package org.mindis.gui.modules;

import ai.timefold.solver.core.api.score.HardMediumSoftScore;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import javafx.application.Platform;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.ReadOnlyBooleanProperty;
import javafx.beans.property.ReadOnlyBooleanWrapper;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.value.ObservableValue;
import javafx.collections.FXCollections;
import javafx.collections.ListChangeListener;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Orientation;
import javafx.geometry.Pos;
import javafx.geometry.VPos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.MenuItem;
import javafx.scene.control.OverrunStyle;
import javafx.scene.control.Separator;
import javafx.scene.control.SplitMenuButton;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TextField;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.util.StringConverter;
import javafx.util.Subscription;

import atlantafx.base.theme.Styles;
import com.dlsc.gemsfx.CalendarPicker;
import com.dlsc.gemsfx.TimePicker;
import org.kordamp.ikonli.javafx.FontIcon;

import org.mindis.core.export.PlanExportFormat;
import org.mindis.core.l10n.EnumDisplay;
import org.mindis.core.l10n.Localization;
import org.mindis.core.model.LiturgicalService;
import org.mindis.core.model.Role;
import org.mindis.core.model.RoleSlot;
import org.mindis.core.model.Server;
import org.mindis.core.model.ServiceType;
import org.mindis.core.persistence.RoleRepository;
import org.mindis.core.persistence.ServiceCsvMapper;
import org.mindis.core.persistence.TemplateRepository;
import org.mindis.core.planning.AcceptedPlan;
import org.mindis.core.planning.Assignment;
import org.mindis.core.planning.PlanMapper;
import org.mindis.core.planning.ServicePlan;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.mindis.gui.LiveDatabase;
import org.mindis.gui.planning.ArchivedPlansDialog;
import org.mindis.gui.planning.PlanExportChooser;
import org.mindis.gui.planning.PlanningViewModel;
import org.mindis.gui.util.CalendarPickers;
import org.mindis.gui.util.TimePickers;
import org.mindis.workbench.CrudModule;
import org.mindis.workbench.CsvRowMapper;
import org.mindis.workbench.LiveStore;

/// Liturgical services module: individual date/time services (plus generation
/// from weekly templates), and - folded in from the former standalone
/// Planning tab - filling their role slots, either manually or by running the
/// solver, and the solve/save/export/archive workflow around that. One
/// From/To range now drives both "Generate from templates" and which period's
/// plan is active: changing the range loads (or starts) that period's plan
/// fresh, while saving a service or reactivating the tab rebuilds the same
/// plan preserving in-progress (possibly unsaved) assignments - the same
/// "Load assignments" vs. "refresh preserving assignments" distinction the
/// old Planning controller made, just triggered by different UI events now.
/// There is exactly one Save all/Load action app-wide - the global toolbar
/// built in {@code MinDisApp} - which drives {@link #saveAll()}/
/// {@link #loadAll()} directly (this module keeps no Save all/Load buttons of
/// its own): a second, module-local button computing its own "is there
/// anything to save" independently of the global one is exactly how the
/// global button ended up enabled/disabled out of step with an Altar-servers
/// pick in practice.
///
/// <p>There is deliberately no separate "plan" concept distinct from the
/// services list: {@link #liveAssignments} is a plain {@code ObservableList},
/// the same reactive idiom every {@code LiveStore} already uses - not a
/// bespoke property wrapper. Every place that used to need an explicit
/// "please rebuild the editor" call (a tab reactivation, a range change, a
/// Save all/Load, a solve finishing) now just calls
/// {@link #publishPlan(ServicePlan)}, which replaces {@link
/// #liveAssignments}' contents via {@code setAll(...)}; the open editor's
/// Altar-servers panel listens to that list directly (see {@link
/// #buildEditor}). {@code setAll(...)} always fires a change notification even
/// when the elements are the exact same object references already held -
/// unlike a plain {@code ObjectProperty<ServicePlan>}, which would silently
/// suppress the notification once Timefold's solver started mutating those
/// objects in place instead of replacing them.
public class ServicesModule extends CrudModule<LiturgicalService> {

    private static final Logger LOGGER = LoggerFactory.getLogger(ServicesModule.class);
    private static final DateTimeFormatter DATE_TIME_FORMAT = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm");
    private static final double EDITOR_MIN_HEIGHT = 520;
    // Auto-fill only leaves one service's slots unpinned - a far smaller
    // problem than a whole-plan solve, so it doesn't need the user's
    // full solverSecondsLimit (often 30s+) to converge.
    private static final Duration AUTO_FILL_TIME_BUDGET = Duration.ofSeconds(5);
    // Fixed (not just minimum) widths for the tile row's left info block and
    // role-slot grid columns - every row builds its own independent VBox/
    // GridPane (one TableCell each), so without a shared fixed width each
    // row's columns would auto-size to its own content and the grid would
    // no longer line up from tile to tile.
    private static final double TILE_INFO_WIDTH = 180;
    private static final double ROLE_COLUMN_WIDTH = 100;
    private static final double SLOT_COLUMN_WIDTH = 90;

    private final ServicesViewModel viewModel;
    private final PlanningViewModel planningViewModel;
    private final LiveDatabase liveDatabase;
    private final LiveStore<Role> roleStore;
    private final Subscription storeRefreshSubscription;

    private final CalendarPicker fromPicker = CalendarPickers.create();
    private final CalendarPicker toPicker = CalendarPickers.create();
    private final Label scoreLabel = new Label();
    private final Label statusLabel = new Label();
    private final BooleanProperty solving = new SimpleBooleanProperty(false);
    private final BooleanProperty hasPlan = new SimpleBooleanProperty(false);
    // Whether the current plan's assignments differ from what's on disk -
    // the plan-side half of "Save all"'s dirty state (the other half is
    // CrudModule's own dirtyCountProperty(), tracking LiturgicalService
    // record edits). Recomputed (not just set true) after every assignment
    // pick and solver run, by diffing against savedPlanSnapshot - so
    // clearing a pick and then setting it back to its previous value reads
    // clean again, the same "diff against last-saved state" rule the
    // CrudModule dirty tracking uses.
    private final BooleanProperty planDirty = new SimpleBooleanProperty(false);
    private @Nullable AcceptedPlan savedPlanSnapshot;

    // Plain field, not a property - the plan object itself is an
    // implementation detail (needed for scoreOf()/violationsByAssignment()/
    // PlanMapper, which operate on the whole ServicePlan, not just its
    // assignments). Every read goes through this field directly; every write
    // goes through publishPlan(...), which is also what keeps
    // liveAssignments/planPresent in sync - never assign this field itself.
    private @Nullable ServicePlan currentPlan;
    /// The reactive surface for "what's currently assigned" - the open
    /// editor's Altar-servers panel and the table's "Assigned" column both
    /// ultimately read this (the table indirectly, via {@link #assignedCount}
    /// reading {@link #currentPlan} - {@link #publishPlan} always calls {@code
    /// table().refresh()}'s callers immediately afterward). See class docs for
    /// why this is a plain {@code ObservableList}, not a property.
    private final ObservableList<Assignment> liveAssignments = FXCollections.observableArrayList();
    /// Whether a plan object currently exists (regardless of whether it has any assignments yet).
    private final ReadOnlyBooleanWrapper planPresent = new ReadOnlyBooleanWrapper(false);
    private @Nullable UUID jobId;

    // The editor's own live (possibly unsaved) slot counts for whichever
    // service is currently selected - assignedLabel() needs this so the
    // "Assigned" column's denominator matches a slot just added in the
    // editor, not just what's actually persisted. Self-correcting: opening
    // a different service's editor overwrites these, so a row that isn't
    // the one currently open naturally falls back to its own persisted
    // totalSlots() below.
    private @Nullable String liveSlotsServiceId;
    private List<RoleSlot> liveSlotsForEditor = List.of();

    public ServicesModule(String name, LiveStore<LiturgicalService> serviceStore, LiveStore<Role> roleStore,
                          TemplateRepository templateRepository, RoleRepository roleRepository,
                          PlanningViewModel planningViewModel, LiveDatabase liveDatabase) {
        super(name, "mdi2c-church", serviceStore);
        this.viewModel = new ServicesViewModel(templateRepository, roleRepository);
        this.planningViewModel = planningViewModel;
        this.liveDatabase = liveDatabase;
        this.roleStore = roleStore;
        // A store re-baseline (the global Save all/Load - see saveAll()/
        // loadAll() below) may have changed the services/roster underneath
        // the plan: re-resolve which saved plan applies and rebuild
        // (publishPlan(...) inside rebuildCurrentPlan() reactively updates
        // any open editor on its own).
        storeRefreshSubscription = serviceStore.refreshTickProperty().subscribe(() -> {
            reloadSavedPlanSnapshot();
            rebuildCurrentPlan();
            table().refresh();
        });

        // The table is used as a single-column tile list rather than a
        // classic multi-column grid: each row's cell renders the whole
        // date/type/location + role-slot summary via buildTileNode(...), so
        // there is nothing meaningful left for a column header to label -
        // hidden via the "services-tile-table" style class (see
        // ThemeStyler).
        TableColumn<LiturgicalService, LiturgicalService> tileColumn = new TableColumn<>();
        tileColumn.setSortable(false);
        tileColumn.setReorderable(false);
        tileColumn.setCellValueFactory(data -> new SimpleObjectProperty<>(data.getValue()));
        tileColumn.setCellFactory(column -> new TableCell<>() {
            @Override
            protected void updateItem(LiturgicalService service, boolean empty) {
                super.updateItem(service, empty);
                setText(null);
                setGraphic(empty || service == null ? null : buildTileNode(service));
            }
        });
        // Fills the table's own width minus its vertical scrollbar/insets -
        // a fixed prefWidth would either clip the role-slot grid or leave a
        // dead strip on the right, and TableView gives a single column no
        // other way to track the viewport's width automatically.
        tileColumn.prefWidthProperty().bind(table().widthProperty().subtract(18));
        table().getColumns().add(tileColumn);
        table().setTableMenuButtonVisible(false);
        table().getStyleClass().add("services-tile-table");

        fromPicker.setPromptText(Localization.lang("From"));
        fromPicker.setPrefWidth(130);
        toPicker.setPromptText(Localization.lang("To"));
        toPicker.setPrefWidth(130);
        LocalDate firstOfNextMonth = LocalDate.now().plusMonths(1).withDayOfMonth(1);
        fromPicker.setValue(firstOfNextMonth);
        toPicker.setValue(firstOfNextMonth.plusMonths(1).minusDays(1));
        planningViewModel.loadSavedPlan().ifPresent(saved -> {
            fromPicker.setValue(saved.from());
            toPicker.setValue(saved.toInclusive());
            savedPlanSnapshot = saved;
        });
        // A range edit means the planner picked a different period - start
        // that period's plan fresh (reapplying whatever's saved for it, if
        // anything), not carry the previous period's in-memory edits into it.
        fromPicker.valueProperty().addListener((obs, oldValue, newValue) -> onRangeChanged());
        toPicker.valueProperty().addListener((obs, oldValue, newValue) -> onRangeChanged());

        Button generateButton = new Button(Localization.lang("Generate from templates"));
        generateButton.setOnAction(event -> {
            List<LiturgicalService> generated = viewModel.generateFromTemplates(
                    fromPicker.getValue(), toPicker.getValue(), table().getItems());
            if (generated != null) {
                mergeLive(generated);
            }
        });

        Button newButton = new Button(Localization.lang("New"));
        newButton.setOnAction(event -> newItem());
        Button deleteButton = new Button(Localization.lang("Delete"));
        deleteButton.disableProperty().bind(table().getSelectionModel().selectedItemProperty().isNull());
        deleteButton.setOnAction(event -> deleteSelected());

        ServiceCsvMapper serviceCsvMapper = new ServiceCsvMapper(roleRepository);
        CsvRowMapper<LiturgicalService> csvMapper =
                CsvRowMapper.of(serviceCsvMapper::header, serviceCsvMapper::toRow, serviceCsvMapper::fromRow);
        Button importButton = new Button(Localization.lang("Import"));
        importButton.setOnAction(event -> importCsv(csvMapper,
                (imported, total) -> Localization.lang("%0 of %1 rows imported", imported, total)));

        Button solveAllButton = new Button(Localization.lang("Solve all"));
        solveAllButton.disableProperty().bind(solving.or(hasPlan.not()));
        solveAllButton.setOnAction(event -> onSolveAll());
        // The solver can legitimately run up to the configured time budget
        // (default 30s) with no other visible sign of progress - a spinner
        // here (shared by both "Solve all" and per-service auto-fill, since
        // both flip the same solving flag) makes that wait readable instead
        // of looking like the button silently did nothing.
        ProgressIndicator solvingIndicator = new ProgressIndicator();
        solvingIndicator.setPrefSize(20, 20);
        solvingIndicator.visibleProperty().bind(solving);
        solvingIndicator.managedProperty().bind(solving);
        Button stopButton = new Button(Localization.lang("Stop"));
        stopButton.disableProperty().bind(solving.not());
        stopButton.setOnAction(event -> onStop());
        SplitMenuButton exportPlanButton = new SplitMenuButton();
        exportPlanButton.setText(Localization.lang("Export plan"));
        exportPlanButton.disableProperty().bind(solving.or(hasPlan.not()));
        exportPlanButton.setOnAction(event -> onExportPlan(PlanExportFormat.PDF));
        for (PlanExportFormat format : PlanExportFormat.values()) {
            MenuItem formatItem = new MenuItem(format.name());
            formatItem.setOnAction(event -> onExportPlan(format));
            exportPlanButton.getItems().add(formatItem);
        }
        Button archiveButton = new Button(Localization.lang("Archived plans"));
        archiveButton.setOnAction(event -> ArchivedPlansDialog.show(planningViewModel, table().getScene().getWindow()));

        toolbarExtras().addAll(newButton, deleteButton, new Separator(Orientation.VERTICAL),
                new Label(Localization.lang("From")), fromPicker,
                new Label(Localization.lang("To")), toPicker, generateButton,
                new Separator(Orientation.VERTICAL), importButton,
                new Separator(Orientation.VERTICAL),
                solveAllButton, solvingIndicator, stopButton, exportPlanButton, archiveButton,
                new Separator(Orientation.VERTICAL), scoreLabel, statusLabel);
    }

    @Override
    protected LiturgicalService createStub() {
        return viewModel.createStub();
    }

    @Override
    protected void onActivate() {
        // Reactivating the tab rebuilds the same plan preserving in-progress
        // assignments, picking up service/roster edits made in other modules
        // since the last visit (the former loadAll() side effect).
        // publishPlan(...) inside rebuildCurrentPlan() already reactively
        // refreshes any open editor's Altar-servers panel - no explicit
        // "refresh the editor" call needed here or anywhere else.
        rebuildCurrentPlan();
        table().refresh();
    }

    @Override
    public void dispose() {
        storeRefreshSubscription.unsubscribe();
        super.dispose();
    }

    @Override
    protected EditorBinding<LiturgicalService> buildEditor(LiturgicalService service) {
        // The field-changed accents below compare each control against the
        // last-persisted value, not against service itself - service may
        // already be a live (unsaved) edit pushed in by a previous visit to
        // this row's editor, and comparing against itself would always read
        // "unchanged" even though it still differs from disk. Falls back to
        // service for a not-yet-saved new row (no persisted snapshot exists).
        // A supplier, not a one-time snapshot: savedSnapshot(service) looks
        // the row up by identity, so calling it again after a Save all
        // returns the newly-flushed value - re-evaluating field-changed
        // accents against a fixed baseline captured only at editor-open time
        // was exactly why they used to stay stuck "dirty" after saving.
        Supplier<LiturgicalService> baselineSupplier =
                () -> Objects.requireNonNullElse(savedSnapshot(service), service);

        CalendarPicker dateField = CalendarPickers.create();
        dateField.setValue(service.dateTime().toLocalDate());
        TimePicker timeField = TimePickers.create();
        timeField.setTime(service.dateTime().toLocalTime());

        ComboBox<ServiceType> typeBox = new ComboBox<>(FXCollections.observableArrayList(ServiceType.values()));
        typeBox.setConverter(new StringConverter<>() {
            @Override
            public String toString(@Nullable ServiceType type) {
                return type == null ? "" : EnumDisplay.of(type);
            }

            @Override
            public @Nullable ServiceType fromString(@Nullable String string) {
                return null;
            }
        });
        typeBox.getSelectionModel().select(service.type());

        TextField locationField = new TextField(service.location());
        TextField noteField = new TextField(service.note());

        // The Altar-servers panel is always structurally present - not added
        // conditionally on "is there a plan right now" - and its visibility
        // (plus the assignment rows) tracks planPresent reactively. This is
        // what makes it impossible for the panel to go permanently missing:
        // there is no "was it ever attached" state to get stuck in, only a
        // binding that re-evaluates whenever the property changes. Built
        // before assignmentSection below (not after) - every
        // buildAssignmentRows(...) call, including the first, needs this
        // label to set its own dirty accent.
        Label altarServersTitle = new Label(Localization.lang("Altar servers"));
        Button autoFillButton = new Button(null, new FontIcon("mdi2a-auto-fix"));
        autoFillButton.getStyleClass().addAll(Styles.BUTTON_ICON, Styles.FLAT, Styles.SMALL);
        autoFillButton.disableProperty().bind(solving);
        autoFillButton.setOnAction(event -> onAutoFillService(service));
        Tooltip.install(autoFillButton, new Tooltip(Localization.lang("Auto-fill")));
        // A solve can run for several seconds with the button just greyed out
        // otherwise - no visible sign it's doing anything. Swapped for a
        // small spinner (shared "solving" flag, so this also lights up during
        // a whole-plan "Solve all" run - correct, the solver really is busy
        // either way).
        ProgressIndicator autoFillIndicator = new ProgressIndicator();
        autoFillIndicator.setPrefSize(16, 16);
        solving.addListener((obs, wasSolving, isSolving) ->
                autoFillButton.setGraphic(isSolving ? autoFillIndicator : new FontIcon("mdi2a-auto-fix")));
        Region titleSpacer = new Region();
        HBox.setHgrow(titleSpacer, Priority.ALWAYS);
        HBox altarServersHeader = new HBox(8, altarServersTitle, titleSpacer, autoFillButton);
        altarServersHeader.setAlignment(Pos.CENTER_LEFT);
        Separator altarSeparator = new Separator();

        // RoleSlotsEditor's onChange callback needs to mark its own label
        // dirty, but that label (slotsEditor.label) doesn't exist until the
        // RoleSlotsEditor constructor - which is where the callback itself
        // gets built - returns. A one-element array sidesteps the
        // chicken-and-egg: the callback only runs later, in response to a
        // spinner change, well after the array's single slot is filled in
        // right below the constructor call.
        Region[] slotsListHolder = new Region[1];
        // Recomputed from baselineSupplier on every call (not captured once)
        // for the same reason baselineSupplier itself is a supplier - it must
        // reflect the post-Save baseline, not whatever was last flushed when
        // this editor was built.
        Function<List<RoleSlot>, Boolean> slotsChanged = liveSlots -> {
            Map<String, Integer> baselineCounts = new HashMap<>();
            baselineSupplier.get().slots().forEach(slot -> baselineCounts.put(slot.role(), slot.count()));
            Map<String, Integer> liveCounts = new HashMap<>();
            liveSlots.forEach(slot -> liveCounts.put(slot.role(), slot.count()));
            return !liveCounts.equals(baselineCounts);
        };

        Runnable[] pushLiveHolder = new Runnable[1];
        VBox assignmentSection = new VBox(6);
        // Bound directly to the shared live role list - a role added,
        // renamed or removed anywhere shows up in this editor's slot rows on
        // its own, no rebuild call from here needed.
        RoleSlotsEditor slotsEditor = new RoleSlotsEditor(roleStore.items(), service.slots(),
                liveSlots -> {
                    assignmentSection.getChildren().setAll(
                            buildAssignmentRows(service, liveSlots, assignmentSection, altarServersTitle));
                    // The row-rebuild above already updates liveSlotsForEditor
                    // (assignedLabel()'s source for this row's live total),
                    // but the table's own "Assigned" cell only re-reads it
                    // once the table actually refreshes - a decremented
                    // count otherwise left the column showing the old,
                    // now-stale ratio until something else (a solve, a
                    // manual pick) happened to trigger a refresh.
                    table().refresh();
                    setFieldChanged(slotsListHolder[0], slotsChanged.apply(liveSlots));
                    pushLiveHolder[0].run();
                });
        slotsListHolder[0] = slotsEditor.label;
        setFieldChanged(slotsEditor.label, slotsChanged.apply(slotsEditor.collectSlots()));
        assignmentSection.getChildren().setAll(
                buildAssignmentRows(service, slotsEditor.collectSlots(), assignmentSection, altarServersTitle));

        GridPane grid = new GridPane();
        grid.setHgap(8);
        grid.setVgap(8);
        ColumnConstraints labelColumn = new ColumnConstraints();
        labelColumn.setMinWidth(110);
        ColumnConstraints fieldColumn = new ColumnConstraints();
        fieldColumn.setHgrow(Priority.ALWAYS);
        grid.getColumnConstraints().addAll(labelColumn, fieldColumn);

        Label dateLabel = new Label(Localization.lang("Date"));
        Label timeLabel = new Label(Localization.lang("Time"));
        Label typeLabel = new Label(Localization.lang("Type"));
        Label locationLabel = new Label(Localization.lang("Location"));
        Label noteLabel = new Label(Localization.lang("Note"));

        int row = 0;
        grid.add(dateLabel, 0, row);
        grid.add(dateField, 1, row++);
        grid.add(timeLabel, 0, row);
        grid.add(timeField, 1, row++);
        grid.add(typeLabel, 0, row);
        grid.add(typeBox, 1, row++);
        grid.add(locationLabel, 0, row);
        grid.add(locationField, 1, row++);
        grid.add(noteLabel, 0, row);
        grid.add(noteField, 1, row++);

        GridPane.setValignment(slotsEditor.label, VPos.TOP);
        grid.add(slotsEditor.label, 0, row);
        GridPane.setVgrow(slotsEditor.list(), Priority.ALWAYS);
        grid.add(slotsEditor.list(), 1, row++);

        // Guards every control's change listener against firing while the
        // EditorBinding refresh callback below is pushing an externally-
        // changed value into the controls - without it, a refresh's
        // programmatic set can trigger a *second*, reentrant items.set() on
        // the shared store list while an outer one is still unwinding
        // through its own listener chain, corrupting JavaFX's internal
        // ListChangeBuilder (see RolesModule for the same fix).
        boolean[] suppressPushLive = new boolean[1];
        Runnable pushLive = () -> {
            if (suppressPushLive[0]) {
                return;
            }
            LocalDate date = dateField.getValue();
            LocalTime time = timeField.getTime();
            if (date == null || time == null) {
                return;
            }
            updateLive(new LiturgicalService(service.id(), date.atTime(time), service.durationMinutes(),
                    locationField.getText().strip(),
                    typeBox.getValue() == null ? ServiceType.OTHER : typeBox.getValue(),
                    slotsEditor.collectSlots(), noteField.getText().strip()));
        };
        pushLiveHolder[0] = pushLive;
        dateField.valueProperty().addListener((obs, oldValue, newValue) -> pushLive.run());
        timeField.timeProperty().addListener((obs, oldValue, newValue) -> pushLive.run());
        typeBox.valueProperty().addListener((obs, oldValue, newValue) -> pushLive.run());
        locationField.textProperty().addListener((obs, oldValue, newValue) -> pushLive.run());
        noteField.textProperty().addListener((obs, oldValue, newValue) -> pushLive.run());

        ReadOnlyBooleanProperty hasPlanBinding = planPresent.getReadOnlyProperty();
        altarSeparator.visibleProperty().bind(hasPlanBinding);
        altarSeparator.managedProperty().bind(hasPlanBinding);
        altarServersHeader.visibleProperty().bind(hasPlanBinding);
        altarServersHeader.managedProperty().bind(hasPlanBinding);
        assignmentSection.visibleProperty().bind(hasPlanBinding);
        assignmentSection.managedProperty().bind(hasPlanBinding);

        VBox content = new VBox(10, grid, altarSeparator, altarServersHeader, assignmentSection);
        content.setPadding(new Insets(12));
        content.setMinHeight(EDITOR_MIN_HEIGHT);
        markDirtyOnChange(dateField.valueProperty(), () -> baselineSupplier.get().dateTime().toLocalDate(), dateLabel);
        markDirtyOnChange(timeField.timeProperty(), () -> baselineSupplier.get().dateTime().toLocalTime(), timeLabel);
        markDirtyOnChange(typeBox.valueProperty(), () -> baselineSupplier.get().type(), typeLabel);
        markDirtyOnChange(locationField.textProperty(), () -> baselineSupplier.get().location(), locationLabel);
        markDirtyOnChange(noteField.textProperty(), () -> baselineSupplier.get().note(), noteLabel);

        // Rebuilds the assignment rows on every liveAssignments change for a
        // reason other than this row's own manual picks (a solve finishing, a
        // tab reactivation, a range change, a Save all/Load) - skipped while
        // solving, so the solver's several-times-a-second "best so far" ticks
        // don't tear down combo boxes mid-solve; the finalBest callback
        // always sets solving false *before* publishing the plan, so that
        // last update always gets through. liveAssignments.setAll(...) fires
        // this listener unconditionally, even when publishPlan() passed the
        // exact same (in-place mutated) Assignment instances - see the
        // field's docs for why that matters.
        ListChangeListener<Assignment> assignmentsListener = change -> {
            if (!solving.get()) {
                assignmentSection.getChildren().setAll(
                        buildAssignmentRows(service, slotsEditor.collectSlots(), assignmentSection, altarServersTitle));
                table().refresh();
            }
        };
        liveAssignments.addListener(assignmentsListener);

        return new EditorBinding<>(content, updated -> {
            suppressPushLive[0] = true;
            try {
                dateField.setValue(updated.dateTime().toLocalDate());
                timeField.setTime(updated.dateTime().toLocalTime());
                typeBox.getSelectionModel().select(updated.type());
                locationField.setText(updated.location());
                noteField.setText(updated.note());
                slotsEditor.setSlots(updated.slots());
            } finally {
                suppressPushLive[0] = false;
            }
            // None of the sets above necessarily changed what a control
            // displays (a Save all moves the baseline, not the live value),
            // so their own listeners may not have fired - recompute every
            // accent explicitly rather than relying on one.
            recomputeFieldChanged(dateField.valueProperty(), () -> baselineSupplier.get().dateTime().toLocalDate(), dateLabel);
            recomputeFieldChanged(timeField.timeProperty(), () -> baselineSupplier.get().dateTime().toLocalTime(), timeLabel);
            recomputeFieldChanged(typeBox.valueProperty(), () -> baselineSupplier.get().type(), typeLabel);
            recomputeFieldChanged(locationField.textProperty(), () -> baselineSupplier.get().location(), locationLabel);
            recomputeFieldChanged(noteField.textProperty(), () -> baselineSupplier.get().note(), noteLabel);
            setFieldChanged(slotsEditor.label, slotsChanged.apply(updated.slots()));
            setFieldChanged(altarServersTitle, isAssignmentsDirtyFor(service));
        }, () -> {
            liveAssignments.removeListener(assignmentsListener);
            slotsEditor.dispose();
        });
    }

    /// Left border accent on {@code label} while {@code property}'s current
    /// value differs from {@code original.get()} (the last-flushed value) - a
    /// lightweight "you have unsaved changes here" cue that needs no
    /// field-by-field "was this the one that changed" bookkeeping: each field
    /// just watches its own drift from where it started, and clears itself
    /// the moment the value round-trips back to the original (e.g. undoing a
    /// typo). On the field's own label, not the field itself - keeps the
    /// accent out of the way of a field's own focus/validation styling.
    ///
    /// <p>{@code original} is a supplier, not a fixed value: a Save all moves
    /// "the last-flushed value" without necessarily changing what the control
    /// displays (the row was already showing its own just-saved content), so
    /// no property change fires to re-evaluate the accent on its own - a fixed
    /// snapshot captured once at editor-build time would leave the accent
    /// stuck "dirty" forever after the first save. Re-reading the supplier on
    /// every future control edit keeps the listener correct going forward;
    /// {@link CrudModule.EditorBinding}'s {@code refresh} callback additionally
    /// re-invokes this method's initial check after a Save all/Load, since
    /// that path changes no control value and so triggers no listener at all.
    private static <T> void markDirtyOnChange(ObservableValue<T> property, Supplier<T> original, Region label) {
        property.addListener((obs, oldValue, newValue) -> recomputeFieldChanged(property, original, label));
        recomputeFieldChanged(property, original, label);
    }

    /// The comparison {@link #markDirtyOnChange} reruns on every control
    /// change - factored out so an {@code EditorBinding.refresh} (a Save
    /// all/Load, which moves {@code original} without necessarily changing
    /// what the control displays, so no listener fires on its own) can
    /// re-invoke just the comparison without registering a second listener.
    private static <T> void recomputeFieldChanged(ObservableValue<T> property, Supplier<T> original, Region label) {
        setFieldChanged(label, !Objects.equals(property.getValue(), original.get()));
    }

    /// Toggles the left-border "unsaved change" accent (see {@code .field-changed} in ThemeStyler) on or off.
    private static void setFieldChanged(Region label, boolean changed) {
        if (changed) {
            if (!label.getStyleClass().contains("field-changed")) {
                label.getStyleClass().add("field-changed");
            }
        } else {
            label.getStyleClass().remove("field-changed");
        }
    }

    /// The table row's tile: big-font date/time on the left (with an
    /// underfilled warning icon, same rule the old "Assigned" column used),
    /// type/location below it in normal size, and the role-slot grid on the
    /// right (see {@link #buildRoleSlotGrid}).
    private Node buildTileNode(LiturgicalService service) {
        Label dateTimeLabel = new Label(service.dateTime().format(DATE_TIME_FORMAT));
        dateTimeLabel.getStyleClass().add("service-tile-datetime");
        Label typeLabel = new Label(EnumDisplay.of(service.type()));
        Label locationLabel = new Label(service.location());
        VBox left = new VBox(2, dateTimeLabel, typeLabel, locationLabel);
        // Fixed, not just minimum - see TILE_INFO_WIDTH's docs. Overflowing
        // text (a long location) is clipped with an ellipsis rather than
        // pushing the grid out of alignment with every other tile.
        left.setMinWidth(TILE_INFO_WIDTH);
        left.setPrefWidth(TILE_INFO_WIDTH);
        left.setMaxWidth(TILE_INFO_WIDTH);
        left.setAlignment(Pos.CENTER_LEFT);
        for (Label label : List.of(dateTimeLabel, typeLabel, locationLabel)) {
            label.setMaxWidth(TILE_INFO_WIDTH);
            label.setTextOverrun(OverrunStyle.ELLIPSIS);
        }

        AssignedCount count = assignedCount(service);
        if (count.underfilled()) {
            FontIcon warningIcon = new FontIcon("mdi2a-alert-circle");
            warningIcon.getStyleClass().add("altar-warning-icon");
            HBox dateRow = new HBox(6, dateTimeLabel, warningIcon);
            dateRow.setAlignment(Pos.CENTER_LEFT);
            left.getChildren().set(0, dateRow);
        }

        GridPane slotGrid = buildRoleSlotGrid(service);
        HBox.setHgrow(slotGrid, Priority.ALWAYS);
        HBox tile = new HBox(20, left, slotGrid);
        tile.setAlignment(Pos.CENTER_LEFT);
        tile.setPadding(new Insets(8, 4, 8, 4));
        return tile;
    }

    /// Per-service role-slot summary, read-only (picks happen in the row's
    /// editor, not here): three columns - role name, first slot, second slot -
    /// with a role occupying as many further two-slot rows as its count
    /// needs (role name cell left blank on those continuation rows). Reads
    /// {@link #currentPlan} directly the same way {@link #buildAssignmentRows}
    /// does, so a slot with no backing {@link Assignment} yet (a count bumped
    /// up since the plan was last built) just shows as unfilled rather than
    /// needing its own synthesized placeholder - this view never writes to
    /// the plan.
    private GridPane buildRoleSlotGrid(LiturgicalService service) {
        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(2);
        grid.getColumnConstraints().addAll(roleSlotColumn(ROLE_COLUMN_WIDTH), roleSlotColumn(SLOT_COLUMN_WIDTH),
                roleSlotColumn(SLOT_COLUMN_WIDTH));

        ServicePlan plan = currentPlan;
        Map<String, Assignment> byId = plan == null ? Map.of() : plan.getAssignments().stream()
                .filter(a -> a.getService().id().equals(service.id()))
                .collect(Collectors.toMap(Assignment::getId, a -> a));
        Map<String, Role> rolesById = new HashMap<>();
        viewModel.findAllRoles().forEach(role -> rolesById.put(role.id(), role));

        int gridRow = 0;
        for (RoleSlot slot : service.slots()) {
            if (slot.count() <= 0) {
                continue;
            }
            Role role = rolesById.get(slot.role());
            Label roleLabel = new Label(role == null ? slot.role() : role.name());
            roleLabel.getStyleClass().add("service-tile-role");
            roleLabel.setMaxWidth(ROLE_COLUMN_WIDTH);
            roleLabel.setTextOverrun(OverrunStyle.ELLIPSIS);
            grid.add(roleLabel, 0, gridRow);

            for (int slotIndex = 0; slotIndex < slot.count(); slotIndex++) {
                String assignmentId = service.id() + ":" + slot.role() + ":" + slotIndex;
                Assignment assignment = byId.get(assignmentId);
                String text = assignment != null && assignment.getServer() != null
                        ? assignment.getServer().displayName() : "-";
                Label slotLabel = new Label(text);
                slotLabel.getStyleClass().add("service-tile-slot");
                slotLabel.setMaxWidth(SLOT_COLUMN_WIDTH);
                slotLabel.setTextOverrun(OverrunStyle.ELLIPSIS);
                int column = 1 + slotIndex % 2;
                grid.add(slotLabel, column, gridRow);
                if (column == 2) {
                    gridRow++;
                }
            }
            if (slot.count() % 2 != 0) {
                gridRow++;
            }
        }
        return grid;
    }

    /// A fixed (min == pref == max), non-growing column - see
    /// {@link #TILE_INFO_WIDTH}'s docs for why every tile's grid needs the
    /// exact same column widths rather than each auto-sizing to its own row.
    private static ColumnConstraints roleSlotColumn(double width) {
        ColumnConstraints column = new ColumnConstraints();
        column.setMinWidth(width);
        column.setPrefWidth(width);
        column.setMaxWidth(width);
        column.setHgrow(Priority.NEVER);
        return column;
    }

    /// Open-slot fill rows for {@code service}, driven by {@code liveSlots} -
    /// the role/count editor's current (possibly unsaved) values, not just
    /// whatever's in the current plan - so the list updates the
    /// moment a slot count changes, before Save. A slot instance still backed
    /// by an {@link Assignment} in the current plan (matched by its stable id,
    /// {@code service:role:index}) gets an editable dropdown seeded with its
    /// current server; one that isn't - a count bumped up since the plan was
    /// last built - gets a disabled placeholder, since there's nothing to
    /// write a pick into until Save regenerates the plan for the new count.
    /// Decrementing then incrementing a count back before saving never drops
    /// an assignment: the underlying {@link Assignment} objects in the plan
    /// aren't touched by hiding their row, only by an actual rebuild (Save,
    /// tab reactivation, or a range change), so the previously assigned server
    /// reappears exactly as it was.
    ///
    /// <p>{@code assignmentSection} is threaded through so a manual pick can
    /// rebuild these rows in place: violations (and the warning icon) are
    /// computed fresh on every call, so without this a slot's violation
    /// status would only ever catch up on the next full editor rebuild
    /// (Save, a solve, reselecting the row) rather than the moment the pick
    /// is made. {@code altarServersTitle} gets the same left-border
    /// "unsaved change" accent as the other fields, recomputed on every call -
    /// this is the single choke point every assignment-affecting change
    /// (a pick, a slot count edit, a solve, an external plan rebuild) already
    /// goes through, so there is no separate call site to remember.
    private List<Node> buildAssignmentRows(LiturgicalService service, List<RoleSlot> liveSlots,
                                           VBox assignmentSection, Region altarServersTitle) {
        liveSlotsServiceId = service.id();
        liveSlotsForEditor = liveSlots;
        setFieldChanged(altarServersTitle, isAssignmentsDirtyFor(service));
        ServicePlan plan = currentPlan;
        if (plan == null || liveSlots.isEmpty()) {
            return List.of();
        }
        Map<String, Assignment> byId = plan.getAssignments().stream()
                .filter(a -> a.getService().id().equals(service.id()))
                .collect(Collectors.toMap(Assignment::getId, a -> a));
        Map<String, Role> rolesById = new HashMap<>();
        viewModel.findAllRoles().forEach(role -> rolesById.put(role.id(), role));
        Map<String, List<String>> violations = planningViewModel.violationsByAssignment(plan);

        ObservableList<Server> choices = FXCollections.observableArrayList(plan.getServers());
        choices.addFirst(null);

        List<Node> rows = new ArrayList<>();
        for (RoleSlot slot : liveSlots) {
            Role role = rolesById.get(slot.role());
            String roleName = role == null ? slot.role() : role.name();
            for (int i = 0; i < slot.count(); i++) {
                String assignmentId = service.id() + ":" + slot.role() + ":" + i;
                Assignment assignment = byId.get(assignmentId);
                // A count bumped up since the plan was last built has no
                // backing Assignment yet - synthesize one into the live plan
                // right away (matching PlanningService's own id scheme) so
                // the row is immediately editable, not just a disabled
                // "save first" placeholder. Skipped while solving: the
                // solver thread is actively iterating this same
                // plan.getAssignments() list on a background thread, and
                // mutating it concurrently isn't safe - Save (which is
                // disabled during a solve anyway) still picks the new count
                // up normally once solving finishes.
                if (assignment == null && role != null && !solving.get()) {
                    assignment = new Assignment(assignmentId, service, role);
                    plan.getAssignments().add(assignment);
                    byId.put(assignmentId, assignment);
                }

                ComboBox<Server> serverBox = new ComboBox<>(choices);
                serverBox.setConverter(new StringConverter<>() {
                    @Override
                    public String toString(@Nullable Server server) {
                        return server == null ? "-" : server.displayName();
                    }

                    @Override
                    public @Nullable Server fromString(String string) {
                        return null;
                    }
                });
                if (assignment != null) {
                    Assignment finalAssignment = assignment;
                    serverBox.setValue(assignment.getServer());
                    serverBox.valueProperty().addListener((obs, oldServer, newServer) -> {
                        finalAssignment.setServer(newServer);
                        finalAssignment.setPinned(resolvePinnedAfterManualPick(finalAssignment, newServer));
                        recomputePlanDirty();
                        assignmentSection.getChildren().setAll(
                                buildAssignmentRows(service, liveSlots, assignmentSection, altarServersTitle));
                        refreshScoreAndStatus();
                        table().refresh();
                    });
                } else {
                    serverBox.setDisable(true);
                    serverBox.setPromptText(Localization.lang("Save to assign"));
                }

                Label roleLabel = new Label(roleName);
                roleLabel.setMinWidth(110);
                Region spacer = new Region();
                HBox.setHgrow(spacer, Priority.ALWAYS);
                HBox row = new HBox(8, roleLabel, spacer, serverBox);
                row.setAlignment(Pos.CENTER_LEFT);
                // A fixed fraction of the row's own width (not Hgrow.ALWAYS,
                // which used to let the combo box eat the entire remaining
                // row), right-aligned via the spacer above so every row's
                // combo box lines up at the same right edge regardless of
                // its role label's length.
                serverBox.prefWidthProperty().bind(row.widthProperty().multiply(0.6));

                List<String> names = assignment == null
                        ? List.of() : violations.getOrDefault(assignment.getId(), List.of());
                if (!names.isEmpty()) {
                    FontIcon warningIcon = new FontIcon("mdi2a-alert-circle");
                    // Not setStyle(...): FontIcon applies its own glyph font
                    // via setStyle(...) internally when constructed, and
                    // Node.setStyle(...) replaces the whole inline style
                    // string rather than merging into it - overwriting that
                    // font-family here left the icon a fallback tofu box, no
                    // glyph. A style class routes -fx-icon-color through the
                    // stylesheet cascade instead, which doesn't touch it.
                    warningIcon.getStyleClass().add("altar-warning-icon");
                    // Tooltip.install()'d directly on the FontIcon rendered
                    // the tooltip's own text in the icon's glyph font - a
                    // plain StackPane wrapper as the actual tooltip owner
                    // keeps the tooltip out of whatever font-family scoping
                    // FontIcon applies to itself.
                    StackPane iconSlot = new StackPane(warningIcon);
                    Tooltip.install(iconSlot, new Tooltip(
                            String.join(", ", names.stream().map(Localization::lang).toList())));
                    // Index 2, after the spacer (not 1, right after the
                    // label) - the spacer's Hgrow pushes everything after it
                    // to the right as one unit, so the icon needs to be on
                    // the combo box's side of it to sit directly beside the
                    // combo box rather than floating in the middle of the row.
                    row.getChildren().add(2, iconSlot);
                }
                rows.add(row);
            }
        }
        return rows;
    }

    /// Filled/total counts backing the "Assigned" column; {@code filled} is
    /// {@code -1} when no plan is loaded (nothing to be filled yet - the
    /// label falls back to just the total). For the service currently open
    /// in the editor, {@code total} is the live (possibly unsaved) slot
    /// count - otherwise a filled slot just added in the editor but not yet
    /// saved would show as e.g. "3/2" against the still-persisted total.
    private record AssignedCount(int filled, int total) {
        boolean underfilled() {
            return filled >= 0 && filled < total;
        }

        @Override
        public String toString() {
            return filled < 0 ? String.valueOf(total) : filled + "/" + total;
        }
    }

    private AssignedCount assignedCount(LiturgicalService service) {
        int total = service.id().equals(liveSlotsServiceId)
                ? liveSlotsForEditor.stream().mapToInt(RoleSlot::count).sum()
                : service.totalSlots();
        ServicePlan plan = currentPlan;
        if (plan == null) {
            return new AssignedCount(-1, total);
        }
        long filled = plan.getAssignments().stream()
                .filter(a -> a.getService().id().equals(service.id()))
                .filter(a -> a.getServer() != null)
                .count();
        return new AssignedCount((int) filled, total);
    }

    /// The only path that may assign {@link #currentPlan} - keeps
    /// {@link #liveAssignments} and {@link #planPresent} in lockstep with it.
    /// {@code liveAssignments.setAll(...)} fires a change notification even
    /// when {@code plan}'s assignments are the exact object references already
    /// held (Timefold mutates them in place rather than cloning) - see the
    /// field's own docs for why that guarantee matters here.
    private void publishPlan(@Nullable ServicePlan plan) {
        currentPlan = plan;
        liveAssignments.setAll(plan == null ? List.of() : plan.getAssignments());
        planPresent.set(plan != null);
    }

    private void onRangeChanged() {
        publishPlan(null);
        reloadSavedPlanSnapshot();
        rebuildCurrentPlan();
        table().refresh();
    }

    /// Discards every staged edit in the shared database (all modules, by
    /// design - there is one database) and unsaved assignment picks, reloading
    /// from disk. The plan is hard-reset before the reload so the
    /// store-refresh handler rebuilds it fresh against the reverted data.
    /// There is exactly one Load action app-wide (the global toolbar button in
    /// {@code MinDisApp}) - it calls this directly rather than going through
    /// {@link LiveDatabase} on its own, since the plan (unlike the four entity
    /// stores) isn't something {@code LiveDatabase} knows about.
    public void loadAll() {
        publishPlan(null);
        liveDatabase.loadAll();
    }

    /// Same period, preserving the current plan's in-progress assignments while picking up service/roster edits.
    private void rebuildCurrentPlan() {
        LocalDate from = fromPicker.getValue();
        LocalDate to = toPicker.getValue();
        if (from == null || to == null || to.isBefore(from)) {
            publishPlan(null);
            hasPlan.set(false);
            refreshScoreAndStatus();
            return;
        }
        ServicePlan existing = currentPlan;
        ServicePlan rebuilt = existing == null
                ? planningViewModel.generateProblem(from, to)
                : planningViewModel.rebuildPreservingAssignments(existing, from, to);
        publishPlan(rebuilt);
        hasPlan.set(!rebuilt.getAssignments().isEmpty());
        recomputePlanDirty();
        refreshScoreAndStatus();
    }

    /// Reloads {@link #savedPlanSnapshot} for whatever horizon is currently picked (or clears it if none is saved for it).
    private void reloadSavedPlanSnapshot() {
        LocalDate from = fromPicker.getValue();
        LocalDate to = toPicker.getValue();
        savedPlanSnapshot = planningViewModel.loadSavedPlan()
                .filter(saved -> saved.from().equals(from) && saved.toInclusive().equals(to))
                .orElse(null);
    }

    /// Diffs the current plan against {@link #savedPlanSnapshot}, ignoring
    /// unassigned/unpinned slots on either side (an empty slot on disk and an
    /// empty slot in memory are the same "nothing to save" state regardless of
    /// assignment id bookkeeping) - so clearing a pick and then setting it back
    /// to its previous value reads clean again, not stuck dirty.
    private void recomputePlanDirty() {
        ServicePlan plan = currentPlan;
        if (plan == null) {
            planDirty.set(false);
            return;
        }
        AcceptedPlan current = PlanMapper.toAcceptedPlan(plan, fromPicker.getValue(), toPicker.getValue());
        Map<String, AcceptedPlan.PlannedAssignment> currentMeaningful = meaningfulAssignments(current);
        Map<String, AcceptedPlan.PlannedAssignment> savedMeaningful = savedPlanSnapshot == null
                ? Map.of()
                : meaningfulAssignments(savedPlanSnapshot);
        planDirty.set(!currentMeaningful.equals(savedMeaningful));
    }

    private static Map<String, AcceptedPlan.PlannedAssignment> meaningfulAssignments(AcceptedPlan plan) {
        Map<String, AcceptedPlan.PlannedAssignment> byId = new HashMap<>();
        for (AcceptedPlan.PlannedAssignment assignment : plan.assignments()) {
            if (assignment.serverId() != null || assignment.pinned()) {
                byId.put(assignment.assignmentId(), assignment);
            }
        }
        return byId;
    }

    /// The pin state to apply after a manual combo-box pick. A plain
    /// {@code newServer != null} would mark *any* interaction as a manual pin
    /// - including reselecting the exact server this slot was last saved
    /// with - permanently diverging from {@link #savedPlanSnapshot} on the pin
    /// flag alone even though the server itself matches, so "picking the
    /// original value back" could never actually clear the dirty accent.
    /// Restoring the saved pin state specifically when the picked server
    /// matches what was saved makes reverting a pick genuinely equivalent to
    /// not having touched it; picking anything else is still a deliberate
    /// manual pin, same as before.
    private boolean resolvePinnedAfterManualPick(Assignment assignment, @Nullable Server newServer) {
        AcceptedPlan saved = savedPlanSnapshot;
        if (saved != null) {
            String newServerId = newServer == null ? null : newServer.id();
            for (AcceptedPlan.PlannedAssignment planned : saved.assignments()) {
                if (planned.assignmentId().equals(assignment.getId())) {
                    if (Objects.equals(planned.serverId(), newServerId)) {
                        return planned.pinned();
                    }
                    break;
                }
            }
        }
        return newServer != null;
    }

    /// The Altar-servers-panel counterpart of {@link #recomputePlanDirty()}:
    /// whether {@code service}'s own picks differ from what's on disk, scoped
    /// to just this row (unlike {@link #planDirty}, which is true if *any*
    /// service in the horizon has an unsaved pick) - a per-row accent should
    /// only light up for the row that's actually different.
    private boolean isAssignmentsDirtyFor(LiturgicalService service) {
        ServicePlan plan = currentPlan;
        if (plan == null) {
            return false;
        }
        String prefix = service.id() + ":";
        AcceptedPlan current = PlanMapper.toAcceptedPlan(plan, fromPicker.getValue(), toPicker.getValue());
        Map<String, AcceptedPlan.PlannedAssignment> currentForService = filterByService(meaningfulAssignments(current), prefix);
        Map<String, AcceptedPlan.PlannedAssignment> savedForService = savedPlanSnapshot == null
                ? Map.of()
                : filterByService(meaningfulAssignments(savedPlanSnapshot), prefix);
        return !currentForService.equals(savedForService);
    }

    private static Map<String, AcceptedPlan.PlannedAssignment> filterByService(
            Map<String, AcceptedPlan.PlannedAssignment> assignments, String assignmentIdPrefix) {
        Map<String, AcceptedPlan.PlannedAssignment> filtered = new HashMap<>();
        assignments.forEach((id, assignment) -> {
            if (id.startsWith(assignmentIdPrefix)) {
                filtered.put(id, assignment);
            }
        });
        return filtered;
    }

    private void onSolveAll() {
        // Rebuild against the live (possibly unsaved) database right before
        // solving - preserving in-progress picks - so a roster/service edit
        // made without revisiting this tab (or without Save all) is still
        // reflected. The solver must never require a save first: repositories
        // are already the shared in-memory source of truth.
        rebuildCurrentPlan();
        ServicePlan plan = currentPlan;
        if (plan == null || plan.getAssignments().isEmpty()) {
            return;
        }
        solving.set(true);
        statusLabel.setText(Localization.lang("Solving..."));
        jobId = planningViewModel.solveAsync(plan,
                best -> Platform.runLater(() -> {
                    publishPlan(best);
                    refreshScoreAndStatus();
                    table().refresh();
                }),
                finalBest -> Platform.runLater(() -> {
                    // solving false *before* publishing the plan, so the open
                    // editor's onPlanChange listener (which skips while
                    // solving) lets this final rebuild through.
                    solving.set(false);
                    publishPlan(finalBest);
                    recomputePlanDirty();
                    refreshScoreAndStatus();
                    table().refresh();
                    statusLabel.setText(Localization.lang("Solving finished"));
                }),
                error -> Platform.runLater(() -> {
                    solving.set(false);
                    LOGGER.error("Solving failed", error);
                    statusLabel.setText(Localization.lang("Solving failed: %0", error.getMessage()));
                }));
    }

    /// Solves only {@code service}'s open slots: every other assignment is
    /// pinned for the duration of the solve (so it can't be shifted), then
    /// restored to its original pin state afterward - {@code service}'s own
    /// newly-filled slots become pinned instead, the same as a manual pick,
    /// since the planner asked for this fill just as deliberately.
    private void onAutoFillService(LiturgicalService service) {
        if (solving.get()) {
            return;
        }
        // Rebuild first, same reason as onSolveAll(): a slot count shrunk (or
        // zeroed) in the editor since the plan was last built leaves stale
        // Assignment objects sitting in the old plan - not pruned on a
        // shrink, only on a rebuild, so that growing the count back before
        // saving can still restore the previous pick (see
        // buildAssignmentRows' docs). Solving against the stale plan directly
        // would fill those hidden assignments: the table (which reads the
        // plan directly) would then count them as assigned while this
        // service's own editor - which hides rows for slots that no longer
        // exist - shows nothing, exactly the mismatch a rebuild here avoids.
        rebuildCurrentPlan();
        ServicePlan plan = currentPlan;
        if (plan == null) {
            return;
        }
        Map<String, Boolean> pinSnapshot = new HashMap<>();
        for (Assignment assignment : plan.getAssignments()) {
            pinSnapshot.put(assignment.getId(), assignment.isPinned());
            if (!assignment.getService().id().equals(service.id())) {
                assignment.setPinned(true);
            }
        }
        solving.set(true);
        statusLabel.setText(Localization.lang("Solving..."));
        jobId = planningViewModel.solveAsync(plan, AUTO_FILL_TIME_BUDGET,
                best -> Platform.runLater(() -> publishPlan(best)),
                finalBest -> Platform.runLater(() -> {
                    for (Assignment assignment : finalBest.getAssignments()) {
                        if (assignment.getService().id().equals(service.id())) {
                            // Same resolver as a manual combo-box pick, not a
                            // blind "pinned = server != null": the solver
                            // reproducing the exact server this slot already
                            // had saved on disk must not force a pin that
                            // wasn't there before, or Save all stays
                            // permanently enabled despite nothing actually
                            // differing from disk - the auto-fill equivalent
                            // of picking the original value back by hand.
                            assignment.setPinned(resolvePinnedAfterManualPick(assignment, assignment.getServer()));
                        } else {
                            Boolean wasPinned = pinSnapshot.get(assignment.getId());
                            assignment.setPinned(wasPinned != null && wasPinned);
                        }
                    }
                    // solving false *before* publishing the plan, so the open
                    // editor's onPlanChange listener (which skips while
                    // solving) lets this final rebuild through.
                    solving.set(false);
                    publishPlan(finalBest);
                    recomputePlanDirty();
                    refreshScoreAndStatus();
                    table().refresh();
                    statusLabel.setText(Localization.lang("Solving finished"));
                }),
                error -> Platform.runLater(() -> {
                    solving.set(false);
                    LOGGER.error("Solving failed", error);
                    statusLabel.setText(Localization.lang("Solving failed: %0", error.getMessage()));
                }));
    }

    private void onStop() {
        if (jobId != null) {
            planningViewModel.stopSolving(jobId);
        }
    }

    /// Persists both halves of the live state in one call: if
    /// {@link #planDirty} (an assignment was picked or a solve ran since the
    /// last save), the current plan; then the whole shared database - "Save
    /// all" always means "flush the database", so pending edits from every
    /// module land on disk together, not just this tab's. There is exactly
    /// one Save all action app-wide (the global toolbar button in
    /// {@code MinDisApp}) - it calls this directly rather than going through
    /// {@link LiveDatabase} on its own, since the plan (unlike the four entity
    /// stores) isn't something {@code LiveDatabase} knows about; a separate
    /// per-module button here would mean two places compute "is there
    /// anything to save", which is exactly the divergence (global button
    /// enabled/disabled independently of an Altar-servers pick) that made a
    /// single button necessary in the first place.
    public void saveAll() {
        ServicePlan plan = currentPlan;
        if (plan != null && planDirty.get()) {
            planningViewModel.savePlan(plan, fromPicker.getValue(), toPicker.getValue());
            savedPlanSnapshot = PlanMapper.toAcceptedPlan(plan, fromPicker.getValue(), toPicker.getValue());
            recomputePlanDirty();
        }
        liveDatabase.saveAll();
        statusLabel.setText(Localization.lang("Saved"));
    }

    /// Whether an assignment pick or a solve run differs from what's on disk - part of the global Save all's enablement.
    public ReadOnlyBooleanProperty planDirtyProperty() {
        return planDirty;
    }

    /// Whether the solver is currently running - the global Save all must stay disabled while true, same as this tab's own controls.
    public ReadOnlyBooleanProperty solvingProperty() {
        return solving;
    }

    private void onExportPlan(PlanExportFormat preferredFormat) {
        ServicePlan plan = currentPlan;
        if (plan == null || plan.getAssignments().isEmpty()) {
            return;
        }
        Optional<PlanExportChooser.Target> target = PlanExportChooser.show(
                table().getScene().getWindow(), planningViewModel, "MinDis-" + fromPicker.getValue(), preferredFormat);
        if (target.isEmpty()) {
            return;
        }
        PlanExportFormat format = target.get().format();
        try {
            planningViewModel.exportPlan(plan, fromPicker.getValue(), toPicker.getValue(), target.get().file(), format);
            statusLabel.setText(Localization.lang("%0 saved to %1", format.name(), target.get().file().getFileName()));
        } catch (RuntimeException e) {
            statusLabel.setText(Localization.lang("%0 export failed: %1", format.name(), e.getMessage()));
        }
    }

    private void refreshScoreAndStatus() {
        ServicePlan plan = currentPlan;
        if (plan == null || plan.getAssignments().isEmpty()) {
            scoreLabel.setText("");
            return;
        }
        updateScoreLabel(planningViewModel.scoreOf(plan));
    }

    private void updateScoreLabel(@Nullable HardMediumSoftScore score) {
        if (score == null) {
            scoreLabel.setText("");
            return;
        }
        String feasibility = score.hardScore() == 0 && score.mediumScore() == 0
                ? Localization.lang("Feasible")
                : Localization.lang("Has violations");
        scoreLabel.setText(Localization.lang("Score") + ": " + score + " (" + feasibility + ")");
    }
}
