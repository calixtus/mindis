package org.mindis.gui.modules;

import ai.timefold.solver.core.api.score.HardMediumSoftScore;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import javafx.application.Platform;
import javafx.beans.property.ReadOnlyBooleanProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.value.ObservableValue;
import javafx.collections.FXCollections;
import javafx.collections.ListChangeListener;
import javafx.collections.ObservableList;
import javafx.collections.transformation.SortedList;
import javafx.geometry.Bounds;
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
import javafx.stage.Popup;
import javafx.util.StringConverter;
import javafx.util.Subscription;

import atlantafx.base.theme.Styles;
import com.dlsc.gemsfx.CalendarPicker;
import com.dlsc.gemsfx.TimePicker;
import com.dlsc.gemsfx.paging.PagingControls;
import org.kordamp.ikonli.javafx.FontIcon;

import org.mindis.core.export.PlanExportFormat;
import org.mindis.core.l10n.EnumDisplay;
import org.mindis.core.l10n.Localization;
import org.mindis.core.model.LiturgicalService;
import org.mindis.core.model.Role;
import org.mindis.core.model.Server;
import org.mindis.core.model.ServiceType;
import org.mindis.core.model.Slot;
import org.mindis.core.persistence.RoleRepository;
import org.mindis.core.persistence.ServiceCsvMapper;
import org.mindis.core.persistence.TemplateRepository;
import org.mindis.core.planning.Assignment;
import org.mindis.core.planning.AssignmentKey;
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
/// <p>The plan's own state (the live {@code ServicePlan}, whether it has
/// assignments, whether it's dirty, whether a solve is running) and the logic
/// that mutates that state (rebuild, pick, solve, save) live on {@link
/// PlanningViewModel}, not here - MVVM: this module only constructs UI,
/// marshals async solver callbacks onto the FX thread (see {@link
/// #onSolveAll}), and binds to the ViewModel's properties. Every place that
/// used to need an explicit "please rebuild the editor" call (a tab
/// reactivation, a range change, a Save all/Load, a solve finishing) still
/// doesn't: {@link PlanningViewModel#liveAssignments()} is a plain
/// {@code ObservableList} (the same reactive idiom every {@code LiveStore}
/// already uses, not a bespoke property wrapper) that the ViewModel replaces
/// via {@code setAll(...)} on every plan change, and the open editor's
/// Altar-servers panel listens to it directly (see {@link #buildEditor}).
/// {@code setAll(...)} always fires a change notification even when the
/// elements are the exact same object references already held - unlike a
/// plain {@code ObjectProperty<ServicePlan>}, which would silently suppress
/// the notification once Timefold's solver started mutating those objects in
/// place instead of replacing them.
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
    private static final String GENERATE_POPUP_STYLE = """
            -fx-background-color: -color-bg-overlay;
            -fx-border-color: -color-border-default;
            -fx-border-radius: 4;
            -fx-background-radius: 4;
            """;
    /// How many masses {@link #table()} shows at once - the whole point of
    /// paging this list (unlike Roles/Servers/Templates) is that it can grow
    /// into the hundreds once a year or more has been generated.
    private static final int PAGE_SIZE = 20;

    private final ServicesViewModel viewModel;
    private final PlanningViewModel planningViewModel;
    private final LiveDatabase liveDatabase;
    private final LiveStore<Role> roleStore;
    private final Subscription storeRefreshSubscription;

    // Chronological, not the store's raw (insertion/import) order - a
    // windowed table only reads sensibly page-to-page if each page is a
    // contiguous date range. SortedList wraps store().items() directly (no
    // copy), so it - and every page sliced from it - stays live as services
    // are added, edited or removed.
    private final SortedList<LiturgicalService> sortedServices =
            new SortedList<>(store().items(), Comparator.comparing(LiturgicalService::dateTime));
    /// The current page's slice of {@link #sortedServices} - what
    /// {@link #tableItems()} actually hands {@link #table()}. Recomputed by
    /// {@link #refreshPageItems()} whenever {@link #sortedServices} changes
    /// or {@link #pagingControls}'s page/page size changes.
    private final ObservableList<LiturgicalService> pageItems = FXCollections.observableArrayList();
    private final PagingControls pagingControls = new PagingControls();

    private final CalendarPicker fromPicker = CalendarPickers.create();
    private final CalendarPicker toPicker = CalendarPickers.create();
    /// The currently running solve job, if any - View-local bookkeeping so
    /// the Stop button knows what to cancel; every other bit of plan state
    /// lives on {@link #planningViewModel} now (see its class docs on why -
    /// MVVM: a View shouldn't own state or the logic that mutates it).
    private @Nullable UUID jobId;

    // The editor's own live (possibly unsaved) slot counts for whichever
    // service is currently selected - assignedLabel() needs this so the
    // "Assigned" column's denominator matches a slot just added in the
    // editor, not just what's actually persisted. Self-correcting: opening
    // a different service's editor overwrites these, so a row that isn't
    // the one currently open naturally falls back to its own persisted
    // totalSlots() below.
    private @Nullable String liveSlotsServiceId;
    private List<Slot> liveSlotsForEditor = List.of();

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
        // (PlanningViewModel#publishPlan reactively updates any open
        // editor's Altar-servers panel on its own, via liveAssignments()).
        // Routed through scheduleRebuild(...), not called directly:
        // LiveStore#refresh() calls items.setAll(...) (which the size
        // listener below also observes) *before* bumping refreshTick, so a
        // Save all/Load that also changes the item count would otherwise
        // fire both this and the size listener back to back -
        // scheduleRebuild(...) coalesces same-pulse callers into one
        // rebuild instead of running it twice.
        storeRefreshSubscription = serviceStore.refreshTickProperty().subscribe(() ->
                planningViewModel.scheduleRebuild(fromPicker.getValue(), toPicker.getValue(), true, () -> table().refresh()));
        // A service being added or removed (Generate from templates, CSV
        // import, New, Delete) changes whether there is anything to solve,
        // but none of those actions bump refreshTickProperty() above (that's
        // reserved for an actual Save all/Load re-baseline) - without this,
        // hasPlan/the plan only caught up on the next tab reactivation or
        // range change, leaving "Solve all" wrongly disabled right after a
        // generate. Keyed on list *size*, not every change: an ordinary
        // field edit (typing in a location field) also mutates this same
        // list, via LiveStore#updateLive's in-place items.set(i, ...) - that
        // must not re-rebuild the plan (and re-thrash the open editor's
        // assignment combo boxes) on every keystroke.
        int[] lastServiceCount = {serviceStore.items().size()};
        serviceStore.items().addListener((ListChangeListener<LiturgicalService>) change -> {
            int count = serviceStore.items().size();
            if (count != lastServiceCount[0]) {
                lastServiceCount[0] = count;
                planningViewModel.scheduleRebuild(fromPicker.getValue(), toPicker.getValue(), false, () -> table().refresh());
            }
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

        pagingControls.setPageSize(PAGE_SIZE);
        pagingControls.setShowPageSizeSelector(false);
        pagingControls.pageProperty().addListener((obs, oldPage, newPage) -> refreshPageItems());
        sortedServices.addListener((ListChangeListener<LiturgicalService>) change -> refreshPageItems());
        refreshPageItems();

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
        });
        planningViewModel.reloadSnapshot(fromPicker.getValue(), toPicker.getValue());
        // A range edit means the planner picked a different period - start
        // that period's plan fresh (reapplying whatever's saved for it, if
        // anything), not carry the previous period's in-memory edits into it.
        fromPicker.valueProperty().addListener((obs, oldValue, newValue) -> onRangeChanged());
        toPicker.valueProperty().addListener((obs, oldValue, newValue) -> onRangeChanged());

        Button generateButton = new Button(Localization.lang("Generate from templates"));
        generateButton.setOnAction(event -> showGenerateFromTemplatesPopup(generateButton));

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
        solveAllButton.disableProperty().bind(planningViewModel.solvingProperty().or(planningViewModel.hasPlanProperty().not()));
        solveAllButton.setOnAction(event -> onSolveAll());
        // The solver can legitimately run up to the configured time budget
        // (default 30s) with no other visible sign of progress - a spinner
        // here (shared by both "Solve all" and per-service auto-fill, since
        // both flip the same solving flag) makes that wait readable instead
        // of looking like the button silently did nothing.
        ProgressIndicator solvingIndicator = new ProgressIndicator();
        solvingIndicator.setPrefSize(20, 20);
        solvingIndicator.visibleProperty().bind(planningViewModel.solvingProperty());
        solvingIndicator.managedProperty().bind(planningViewModel.solvingProperty());
        Button stopButton = new Button(Localization.lang("Stop"));
        stopButton.disableProperty().bind(planningViewModel.solvingProperty().not());
        stopButton.setOnAction(event -> onStop());
        SplitMenuButton exportPlanButton = new SplitMenuButton();
        exportPlanButton.setText(Localization.lang("Export"));
        exportPlanButton.disableProperty().bind(planningViewModel.solvingProperty().or(planningViewModel.hasPlanProperty().not()));
        exportPlanButton.setOnAction(event -> onExportPlan(PlanExportFormat.PDF));
        for (PlanExportFormat format : PlanExportFormat.values()) {
            MenuItem formatItem = new MenuItem(format.name());
            formatItem.setOnAction(event -> onExportPlan(format));
            exportPlanButton.getItems().add(formatItem);
        }
        Button archiveButton = new Button(Localization.lang("Archived plans"));
        archiveButton.setOnAction(event -> ArchivedPlansDialog.show(planningViewModel, table().getScene().getWindow()));

        toolbarExtras().addAll(newButton, deleteButton, new Separator(Orientation.VERTICAL),
                generateButton,
                new Separator(Orientation.VERTICAL), importButton, exportPlanButton,
                new Separator(Orientation.VERTICAL),
                solveAllButton, solvingIndicator, stopButton, archiveButton);
    }

    /// Lightweight popup for "Generate from templates", anchored under
    /// {@code anchor} (the toolbar button) rather than a separate modal
    /// dialog window - its own From/To pickers (seeded from the current
    /// period range) replace what used to be a permanent From/To/Generate
    /// group sitting in the toolbar. Deliberately does *not* touch
    /// {@link #fromPicker}/{@link #toPicker} - the generate range is
    /// independent of whichever period's plan is currently active, and
    /// pushing it into those pickers would fire {@link #onRangeChanged()},
    /// which discards {@link PlanningViewModel#currentPlan()} and rebuilds it from scratch
    /// (wiping any not-yet-saved altar-server picks on already-existing
    /// masses in the process). {@code ServiceGenerator} already only
    /// proposes occurrences that aren't in {@code store().items()} yet
    /// (matched by date-time + location), so {@link #mergeLive} - which
    /// appends unmatched rows rather than ever removing one - only ever adds
    /// missing masses; it never touches an existing row's slots or
    /// assignments. Dismisses on Ok or on a click outside (auto-hide), same
    /// as any other transient popup.
    private void showGenerateFromTemplatesPopup(Node anchor) {
        CalendarPicker popupFrom = CalendarPickers.create();
        popupFrom.setValue(fromPicker.getValue());
        CalendarPicker popupTo = CalendarPickers.create();
        popupTo.setValue(toPicker.getValue());

        GridPane grid = new GridPane();
        grid.setHgap(8);
        grid.setVgap(8);
        grid.add(new Label(Localization.lang("From")), 0, 0);
        grid.add(popupFrom, 1, 0);
        grid.add(new Label(Localization.lang("To")), 0, 1);
        grid.add(popupTo, 1, 1);

        Popup popup = new Popup();
        popup.setAutoHide(true);

        Button okButton = new Button(Localization.lang("OK"));
        okButton.setOnAction(event -> {
            List<LiturgicalService> generated = viewModel.generateFromTemplates(
                    popupFrom.getValue(), popupTo.getValue(), store().items());
            if (generated != null) {
                mergeLive(generated);
            }
            popup.hide();
        });
        HBox buttonRow = new HBox(okButton);
        buttonRow.setAlignment(Pos.CENTER_RIGHT);

        VBox content = new VBox(10, grid, buttonRow);
        content.setPadding(new Insets(12));
        content.setStyle(GENERATE_POPUP_STYLE);
        popup.getContent().add(content);

        Bounds anchorBounds = anchor.localToScreen(anchor.getBoundsInLocal());
        popup.show(anchor, anchorBounds.getMinX(), anchorBounds.getMaxY() + 4);
    }

    @Override
    protected ObservableList<LiturgicalService> tableItems() {
        return pageItems;
    }

    @Override
    protected Node belowTable() {
        return pagingControls;
    }

    /// Recomputes {@link #pageItems} from {@link #sortedServices} for
    /// whichever page {@link #pagingControls} is currently on, clamping the
    /// page down if a deletion (or a period/store refresh shrinking the
    /// list) left it past the new last page. The clamp re-invokes this
    /// method once more via {@link #pagingControls}'s own page listener;
    /// that second call finds the already-clamped page in range and returns
    /// without clamping again, so this never recurses further than one level
    /// deep.
    private void refreshPageItems() {
        int total = sortedServices.size();
        pagingControls.setTotalItemCount(total);
        int pageSize = pagingControls.getPageSize();
        int lastPage = pageSize <= 0 ? 0 : Math.max(0, (total - 1) / pageSize);
        if (pagingControls.getPage() > lastPage) {
            pagingControls.setPage(lastPage);
            return;
        }
        int from = Math.min(pagingControls.getPage() * pageSize, total);
        int to = Math.min(from + pageSize, total);
        pageItems.setAll(sortedServices.subList(from, to));
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
        // PlanningViewModel#publishPlan already reactively refreshes any open
        // editor's Altar-servers panel (via liveAssignments()) - no explicit
        // "refresh the editor" call needed here or anywhere else.
        rebuildPlanForCurrentRange();
        table().refresh();
    }

    /// Rebuilds the plan for whichever horizon {@link #fromPicker}/{@link
    /// #toPicker} currently hold, then logs the score - the pairing every
    /// call site that used to call {@code rebuildCurrentPlan()} directly
    /// needs, now that both halves live on {@link #planningViewModel}.
    private void rebuildPlanForCurrentRange() {
        planningViewModel.rebuildForRange(fromPicker.getValue(), toPicker.getValue());
        refreshScoreAndStatus();
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
        // (plus the assignment rows) tracks PlanningViewModel#planPresentProperty() reactively. This is
        // what makes it impossible for the panel to go permanently missing:
        // there is no "was it ever attached" state to get stuck in, only a
        // binding that re-evaluates whenever the property changes. Built
        // before assignmentSection below (not after) - every
        // buildAssignmentRows(...) call, including the first, needs this
        // label to set its own dirty accent.
        Label altarServersTitle = new Label(Localization.lang("Altar servers"));
        Button autoFillButton = new Button(null, new FontIcon("mdi2a-auto-fix"));
        autoFillButton.getStyleClass().addAll(Styles.BUTTON_ICON, Styles.FLAT, Styles.SMALL);
        autoFillButton.disableProperty().bind(planningViewModel.solvingProperty());
        autoFillButton.setOnAction(event -> onAutoFillService(service));
        Tooltip.install(autoFillButton, new Tooltip(Localization.lang("Auto-fill")));
        // A solve can run for several seconds with the button just greyed out
        // otherwise - no visible sign it's doing anything. Swapped for a
        // small spinner (shared "solving" flag, so this also lights up during
        // a whole-plan "Solve all" run - correct, the solver really is busy
        // either way).
        ProgressIndicator autoFillIndicator = new ProgressIndicator();
        autoFillIndicator.setPrefSize(16, 16);
        planningViewModel.solvingProperty().addListener((obs, wasSolving, isSolving) ->
                autoFillButton.setGraphic(isSolving ? autoFillIndicator : new FontIcon("mdi2a-auto-fix")));
        Region titleSpacer = new Region();
        HBox.setHgrow(titleSpacer, Priority.ALWAYS);
        HBox altarServersHeader = new HBox(8, altarServersTitle, titleSpacer, autoFillButton);
        altarServersHeader.setAlignment(Pos.CENTER_LEFT);
        Separator altarSeparator = new Separator();

        // SlotCountEditor's onChange callback needs to mark its own label
        // dirty, but that label (slotsEditor.label) doesn't exist until the
        // SlotCountEditor constructor - which is where the callback itself
        // gets built - returns. A one-element array sidesteps the
        // chicken-and-egg: the callback only runs later, in response to a
        // spinner change, well after the array's single slot is filled in
        // right below the constructor call.
        Region[] slotsListHolder = new Region[1];
        // Recomputed from baselineSupplier on every call (not captured once)
        // for the same reason baselineSupplier itself is a supplier - it must
        // reflect the post-Save baseline, not whatever was last flushed when
        // this editor was built.
        Function<Map<String, Integer>, Boolean> slotsChanged =
                liveCounts -> !liveCounts.equals(countsByRole(baselineSupplier.get().slots()));

        // The concrete slot instances backing the editor's counts -
        // reconciled (see reconcileSlots), not rebuilt from scratch, on
        // every count edit: a role's already-filled slots keep their ids
        // (and therefore their assignments) across a resize, and a genuine
        // shrink below the filled count prefers dropping an empty slot
        // first (see reconcileSlots' docs) rather than whichever slot
        // happens to sit at a now out-of-range position.
        @SuppressWarnings("unchecked")
        List<Slot>[] liveSlotsHolder = new List[1];
        liveSlotsHolder[0] = service.slots();

        Runnable[] pushLiveHolder = new Runnable[1];
        VBox assignmentSection = new VBox(6);
        // Bound directly to the shared live role list - a role added,
        // renamed or removed anywhere shows up in this editor's slot rows on
        // its own, no rebuild call from here needed.
        SlotCountEditor slotsEditor = new SlotCountEditor(roleStore.items(), countsByRole(service.slots()),
                liveCounts -> {
                    List<Slot> liveSlots = reconcileSlots(service, liveSlotsHolder[0], liveCounts);
                    liveSlotsHolder[0] = liveSlots;
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
                    setFieldChanged(slotsListHolder[0], slotsChanged.apply(liveCounts));
                    pushLiveHolder[0].run();
                });
        slotsListHolder[0] = slotsEditor.label;
        setFieldChanged(slotsEditor.label, slotsChanged.apply(slotsEditor.collectCounts()));
        assignmentSection.getChildren().setAll(
                buildAssignmentRows(service, liveSlotsHolder[0], assignmentSection, altarServersTitle));

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
                    liveSlotsHolder[0], noteField.getText().strip()));
        };
        pushLiveHolder[0] = pushLive;
        dateField.valueProperty().addListener((obs, oldValue, newValue) -> pushLive.run());
        timeField.timeProperty().addListener((obs, oldValue, newValue) -> pushLive.run());
        typeBox.valueProperty().addListener((obs, oldValue, newValue) -> pushLive.run());
        locationField.textProperty().addListener((obs, oldValue, newValue) -> pushLive.run());
        noteField.textProperty().addListener((obs, oldValue, newValue) -> pushLive.run());

        ReadOnlyBooleanProperty hasPlanBinding = planningViewModel.planPresentProperty();
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
            if (!planningViewModel.solvingProperty().get()) {
                assignmentSection.getChildren().setAll(
                        buildAssignmentRows(service, liveSlotsHolder[0], assignmentSection, altarServersTitle));
                table().refresh();
            }
        };
        planningViewModel.liveAssignments().addListener(assignmentsListener);

        return new EditorBinding<>(content, updated -> {
            suppressPushLive[0] = true;
            try {
                dateField.setValue(updated.dateTime().toLocalDate());
                timeField.setTime(updated.dateTime().toLocalTime());
                typeBox.getSelectionModel().select(updated.type());
                locationField.setText(updated.location());
                noteField.setText(updated.note());
                liveSlotsHolder[0] = updated.slots();
                slotsEditor.setCounts(countsByRole(updated.slots()));
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
            setFieldChanged(slotsEditor.label, slotsChanged.apply(countsByRole(updated.slots())));
            setFieldChanged(altarServersTitle,
                    planningViewModel.isAssignmentsDirtyFor(service, fromPicker.getValue(), toPicker.getValue()));
        }, () -> {
            planningViewModel.liveAssignments().removeListener(assignmentsListener);
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
    /// {@link PlanningViewModel#currentPlan()} directly the same way {@link
    /// #buildAssignmentRows} does, so a slot with no backing {@link
    /// Assignment} yet (a count bumped up since the plan was last built) just
    /// shows as unfilled rather than needing its own synthesized placeholder
    /// - this view never writes to the plan.
    private GridPane buildRoleSlotGrid(LiturgicalService service) {
        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(2);
        grid.getColumnConstraints().addAll(roleSlotColumn(ROLE_COLUMN_WIDTH), roleSlotColumn(SLOT_COLUMN_WIDTH),
                roleSlotColumn(SLOT_COLUMN_WIDTH));

        ServicePlan plan = planningViewModel.currentPlan();
        Map<String, Assignment> byId = plan == null ? Map.of() : plan.getAssignments().stream()
                .filter(a -> a.getService().id().equals(service.id()))
                .collect(Collectors.toMap(Assignment::getId, a -> a));
        Map<String, Role> rolesById = new HashMap<>();
        viewModel.findAllRoles().forEach(role -> rolesById.put(role.id(), role));

        int gridRow = 0;
        for (Map.Entry<String, List<Slot>> entry : slotsByRole(service.slots()).entrySet()) {
            List<Slot> roleSlots = entry.getValue();
            Role role = rolesById.get(entry.getKey());
            Label roleLabel = new Label(role == null ? entry.getKey() : role.name());
            roleLabel.getStyleClass().add("service-tile-role");
            roleLabel.setMaxWidth(ROLE_COLUMN_WIDTH);
            roleLabel.setTextOverrun(OverrunStyle.ELLIPSIS);
            grid.add(roleLabel, 0, gridRow);

            for (int slotIndex = 0; slotIndex < roleSlots.size(); slotIndex++) {
                String assignmentId = new AssignmentKey(service.id(), roleSlots.get(slotIndex).id()).toId();
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
            if (roleSlots.size() % 2 != 0) {
                gridRow++;
            }
        }
        return grid;
    }

    /// {@code service.slots()}, grouped by role in first-encountered order -
    /// shared by {@link #buildRoleSlotGrid} (one row per role followed by
    /// that role's own slot instances) and {@link #reconcileSlots} (which
    /// slot instances a role currently has, before applying a count edit).
    private static Map<String, List<Slot>> slotsByRole(List<Slot> slots) {
        Map<String, List<Slot>> byRole = new LinkedHashMap<>();
        for (Slot slot : slots) {
            byRole.computeIfAbsent(slot.role(), roleId -> new ArrayList<>()).add(slot);
        }
        return byRole;
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
    /// the slot editor's current (possibly unsaved) instances, not just
    /// whatever's in the current plan - so the list updates the moment a
    /// slot count changes, before Save. A slot instance still backed by an
    /// {@link Assignment} in the current plan (matched by its stable id,
    /// {@code service:slot-id}) gets an editable dropdown seeded with its
    /// current server; one that isn't - a slot just added by the editor -
    /// gets a disabled placeholder, since there's nothing to write a pick
    /// into until Save regenerates the plan for the new slot. Decrementing
    /// then incrementing a count back before saving never drops an
    /// assignment: {@link #reconcileSlots} preserves a role's existing slot
    /// ids across a resize, and the underlying {@link Assignment} objects in
    /// the plan aren't touched by hiding a row, only by an actual rebuild
    /// (Save, tab reactivation, or a range change), so the previously
    /// assigned server reappears exactly as it was.
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
    private List<Node> buildAssignmentRows(LiturgicalService service, List<Slot> liveSlots,
                                           VBox assignmentSection, Region altarServersTitle) {
        liveSlotsServiceId = service.id();
        liveSlotsForEditor = liveSlots;
        setFieldChanged(altarServersTitle,
                planningViewModel.isAssignmentsDirtyFor(service, fromPicker.getValue(), toPicker.getValue()));
        ServicePlan plan = planningViewModel.currentPlan();
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
        for (Slot slot : liveSlots) {
            Role role = rolesById.get(slot.role());
            String roleName = role == null ? slot.role() : role.name();
            String assignmentId = new AssignmentKey(service.id(), slot.id()).toId();
            Assignment assignment = byId.get(assignmentId);
            // A slot just added by the editor has no backing Assignment yet -
            // synthesize one into the live plan right away (matching
            // PlanningService's own id scheme) so the row is immediately
            // editable, not just a disabled "save first" placeholder.
            // Skipped while solving: the solver thread is actively iterating
            // this same plan.getAssignments() list on a background thread,
            // and mutating it concurrently isn't safe - Save (which is
            // disabled during a solve anyway) still picks the new slot up
            // normally once solving finishes.
            if (assignment == null && role != null && !planningViewModel.solvingProperty().get()) {
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
                    planningViewModel.pick(finalAssignment, newServer, fromPicker.getValue(), toPicker.getValue());
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
        return rows;
    }

    /// Reconciles a role's slot count edit (from {@link SlotCountEditor})
    /// into a concrete {@code List<Slot>}, preserving each surviving slot's
    /// stable id - growing a role appends fresh ids; shrinking removes ids,
    /// preferring whichever slots aren't currently backed by a filled or
    /// pinned {@link Assignment} in {@link PlanningViewModel#currentPlan()}, so a shrink never
    /// silently drops an assigned server out from under an unrelated empty
    /// slot just because it happened to occupy a now out-of-range position
    /// (see {@link Slot}'s class docs on why identity matters here).
    private List<Slot> reconcileSlots(LiturgicalService service, List<Slot> existing, Map<String, Integer> counts) {
        Map<String, List<Slot>> byRole = slotsByRole(existing);
        // Existing roles keep their current order; a role newly given a
        // count by the editor (previously zero, no existing Slot instances)
        // is appended after.
        Set<String> roleIds = new LinkedHashSet<>(byRole.keySet());
        roleIds.addAll(counts.keySet());

        List<Slot> result = new ArrayList<>();
        for (String roleId : roleIds) {
            int wanted = counts.getOrDefault(roleId, 0);
            List<Slot> current = byRole.getOrDefault(roleId, List.of());
            if (current.size() <= wanted) {
                result.addAll(current);
                for (int i = current.size(); i < wanted; i++) {
                    result.add(new Slot(Slot.newId(), roleId));
                }
            } else {
                // Shrinking: unfilled slots first (removed first), filled/
                // pinned ones last (kept as long as possible).
                List<Slot> orderedByEmptyFirst = current.stream()
                        .sorted(Comparator.comparing(slot -> isSlotFilled(service, slot)))
                        .toList();
                result.addAll(orderedByEmptyFirst.subList(0, wanted));
            }
        }
        return result;
    }

    /// Whether {@code slot} is currently backed by an {@link Assignment}
    /// with a server picked or a manual pin - the "don't drop this one"
    /// signal {@link #reconcileSlots} uses to choose which slot a shrinking
    /// role loses first.
    private boolean isSlotFilled(LiturgicalService service, Slot slot) {
        ServicePlan plan = planningViewModel.currentPlan();
        if (plan == null) {
            return false;
        }
        String assignmentId = new AssignmentKey(service.id(), slot.id()).toId();
        return plan.getAssignments().stream()
                .anyMatch(a -> a.getId().equals(assignmentId) && (a.getServer() != null || a.isPinned()));
    }

    /// Slot counts per role, for seeding/comparing against a
    /// {@link SlotCountEditor} - the editor only ever deals in counts, never
    /// individual slot ids (see {@link #reconcileSlots}).
    private static Map<String, Integer> countsByRole(List<Slot> slots) {
        Map<String, Integer> counts = new LinkedHashMap<>();
        for (Slot slot : slots) {
            counts.merge(slot.role(), 1, Integer::sum);
        }
        return counts;
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
                ? liveSlotsForEditor.size()
                : service.totalSlots();
        ServicePlan plan = planningViewModel.currentPlan();
        if (plan == null) {
            return new AssignedCount(-1, total);
        }
        long filled = plan.getAssignments().stream()
                .filter(a -> a.getService().id().equals(service.id()))
                .filter(a -> a.getServer() != null)
                .count();
        return new AssignedCount((int) filled, total);
    }

    private void onRangeChanged() {
        planningViewModel.discardPlan();
        planningViewModel.reloadSnapshot(fromPicker.getValue(), toPicker.getValue());
        rebuildPlanForCurrentRange();
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
        planningViewModel.discardPlan();
        liveDatabase.loadAll();
    }

    private void onSolveAll() {
        // Rebuild against the live (possibly unsaved) database right before
        // solving - preserving in-progress picks - so a roster/service edit
        // made without revisiting this tab (or without Save all) is still
        // reflected. The solver must never require a save first: repositories
        // are already the shared in-memory source of truth.
        rebuildPlanForCurrentRange();
        ServicePlan plan = planningViewModel.currentPlan();
        if (plan == null || plan.getAssignments().isEmpty()) {
            return;
        }
        planningViewModel.beginSolve();
        LOGGER.info(Localization.lang("Solving..."));
        jobId = planningViewModel.solveAsync(plan,
                best -> Platform.runLater(() -> {
                    planningViewModel.applyBestSolution(best);
                    refreshScoreAndStatus();
                    table().refresh();
                }),
                finalBest -> Platform.runLater(() -> {
                    planningViewModel.finishSolve(finalBest, fromPicker.getValue(), toPicker.getValue());
                    refreshScoreAndStatus();
                    table().refresh();
                    LOGGER.info(Localization.lang("Solving finished"));
                }),
                error -> Platform.runLater(() -> {
                    planningViewModel.failSolve();
                    LOGGER.error(Localization.lang("Solving failed: %0", error.getMessage()), error);
                }));
    }

    /// Solves only {@code service}'s open slots: every other assignment is
    /// pinned for the duration of the solve (so it can't be shifted), then
    /// restored to its original pin state afterward - see {@link
    /// PlanningViewModel#beginAutoFill}/{@link PlanningViewModel#finishAutoFill}
    /// for the actual pin-juggling.
    private void onAutoFillService(LiturgicalService service) {
        if (planningViewModel.solvingProperty().get()) {
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
        rebuildPlanForCurrentRange();
        ServicePlan plan = planningViewModel.currentPlan();
        if (plan == null) {
            return;
        }
        Map<String, Boolean> pinSnapshot = planningViewModel.beginAutoFill(service);
        LOGGER.info(Localization.lang("Solving..."));
        jobId = planningViewModel.solveAsync(plan, AUTO_FILL_TIME_BUDGET,
                best -> Platform.runLater(() -> planningViewModel.applyBestSolution(best)),
                finalBest -> Platform.runLater(() -> {
                    planningViewModel.finishAutoFill(finalBest, service, pinSnapshot,
                            fromPicker.getValue(), toPicker.getValue());
                    refreshScoreAndStatus();
                    table().refresh();
                    LOGGER.info(Localization.lang("Solving finished"));
                }),
                error -> Platform.runLater(() -> {
                    planningViewModel.failSolve();
                    LOGGER.error(Localization.lang("Solving failed: %0", error.getMessage()), error);
                }));
    }

    private void onStop() {
        if (jobId != null) {
            planningViewModel.stopSolving(jobId);
        }
    }

    /// Persists both halves of the live state in one call: the plan (if
    /// {@link PlanningViewModel#planDirtyProperty()}), then the whole shared
    /// database - "Save all" always means "flush the database", so pending
    /// edits from every module land on disk together, not just this tab's.
    /// There is exactly one Save all action app-wide (the global toolbar
    /// button in {@code MinDisApp}) - it calls this directly rather than
    /// going through {@link LiveDatabase} on its own, since the plan (unlike
    /// the four entity stores) isn't something {@code LiveDatabase} knows
    /// about; a separate per-module button here would mean two places
    /// compute "is there anything to save", which is exactly the divergence
    /// (global button enabled/disabled independently of an Altar-servers
    /// pick) that made a single button necessary in the first place.
    public void saveAll() {
        planningViewModel.save(planningViewModel.currentPlan(), fromPicker.getValue(), toPicker.getValue());
        liveDatabase.saveAll();
        LOGGER.info(Localization.lang("Saved"));
    }

    /// Whether an assignment pick or a solve run differs from what's on disk - part of the global Save all's enablement.
    public ReadOnlyBooleanProperty planDirtyProperty() {
        return planningViewModel.planDirtyProperty();
    }

    /// Whether the solver is currently running - the global Save all must stay disabled while true, same as this tab's own controls.
    public ReadOnlyBooleanProperty solvingProperty() {
        return planningViewModel.solvingProperty();
    }

    private void onExportPlan(PlanExportFormat preferredFormat) {
        ServicePlan plan = planningViewModel.currentPlan();
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
            LOGGER.info(Localization.lang("%0 saved to %1", format.name(), target.get().file().getFileName()));
        } catch (RuntimeException e) {
            LOGGER.error(Localization.lang("%0 export failed: %1", format.name(), e.getMessage()), e);
        }
    }

    private void refreshScoreAndStatus() {
        ServicePlan plan = planningViewModel.currentPlan();
        if (plan == null || plan.getAssignments().isEmpty()) {
            return;
        }
        logScore(planningViewModel.scoreOf(plan));
    }

    /// The score is solver-internal detail, not something an average user
    /// (planning altar-server rosters, not tuning constraint weights) needs
    /// permanently visible in the toolbar - logged instead, so it still
    /// shows up in the in-app error/log console for anyone who does want it.
    private void logScore(@Nullable HardMediumSoftScore score) {
        if (score == null) {
            return;
        }
        String feasibility = score.hardScore() == 0 && score.mediumScore() == 0
                ? Localization.lang("Feasible")
                : Localization.lang("Has violations");
        LOGGER.info("{}: {} ({})", Localization.lang("Score"), score, feasibility);
    }
}
