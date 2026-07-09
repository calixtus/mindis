package org.mindis.gui.modules;

import ai.timefold.solver.core.api.score.HardMediumSoftScore;

import java.io.File;
import java.nio.file.Path;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;

import javafx.application.Platform;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.value.ObservableValue;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Orientation;
import javafx.geometry.Pos;
import javafx.geometry.VPos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.Separator;
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
import javafx.stage.FileChooser;
import javafx.util.StringConverter;

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
import org.mindis.core.persistence.ServiceRepository;
import org.mindis.core.persistence.TemplateRepository;
import org.mindis.core.planning.AcceptedPlan;
import org.mindis.core.planning.Assignment;
import org.mindis.core.planning.PlanMapper;
import org.mindis.core.planning.ServicePlan;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.mindis.gui.planning.ArchivedPlansDialog;
import org.mindis.gui.planning.PlanningViewModel;
import org.mindis.gui.util.CalendarPickers;
import org.mindis.gui.util.TimePickers;
import org.mindis.workbench.CrudModule;
import org.mindis.workbench.CsvRowMapper;

/**
 * Liturgical services module: individual date/time services (plus generation
 * from weekly templates), and - folded in from the former standalone
 * Planning tab - filling their role slots, either manually or by running the
 * solver, and the solve/save/export/archive workflow around that. One
 * From/To range now drives both "Generate from templates" and which period's
 * plan is active: changing the range loads (or starts) that period's plan
 * fresh, while saving a service or reactivating the tab rebuilds the same
 * plan preserving in-progress (possibly unsaved) assignments - the same
 * "Load assignments" vs. "refresh preserving assignments" distinction the
 * old Planning controller made, just triggered by different UI events now.
 */
public class ServicesModule extends CrudModule<LiturgicalService> {

    private static final Logger LOGGER = LoggerFactory.getLogger(ServicesModule.class);
    private static final DateTimeFormatter DATE_TIME_FORMAT = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm");
    private static final double EDITOR_MIN_HEIGHT = 520;
    // Auto-fill only leaves one service's slots unpinned - a far smaller
    // problem than a whole-plan solve, so it doesn't need the user's
    // full solverSecondsLimit (often 30s+) to converge.
    private static final Duration AUTO_FILL_TIME_BUDGET = Duration.ofSeconds(5);

    private final ServicesViewModel viewModel;
    private final PlanningViewModel planningViewModel;

    private final CalendarPicker fromPicker = CalendarPickers.create();
    private final CalendarPicker toPicker = CalendarPickers.create();
    private final Label scoreLabel = new Label();
    private final Label statusLabel = new Label();
    private final BooleanProperty solving = new SimpleBooleanProperty(false);
    private final BooleanProperty hasPlan = new SimpleBooleanProperty(false);
    // Whether currentPlan's assignments differ from what's on disk - the
    // plan-side half of "Save all"'s dirty state (the other half is
    // CrudModule's own dirtyCountProperty(), tracking LiturgicalService
    // record edits). Recomputed (not just set true) after every assignment
    // pick and solver run, by diffing against savedPlanSnapshot - so
    // clearing a pick and then setting it back to its previous value reads
    // clean again, the same "diff against last-saved state" rule the
    // CrudModule dirty tracking uses.
    private final BooleanProperty planDirty = new SimpleBooleanProperty(false);
    private @Nullable AcceptedPlan savedPlanSnapshot;

    private @Nullable ServicePlan currentPlan;
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

    public ServicesModule(String name, ServiceRepository serviceRepository, TemplateRepository templateRepository,
                          RoleRepository roleRepository, PlanningViewModel planningViewModel) {
        super(name, "mdi2c-church");
        this.viewModel = new ServicesViewModel(serviceRepository, templateRepository, roleRepository);
        this.planningViewModel = planningViewModel;

        TableColumn<LiturgicalService, String> dateColumn = new TableColumn<>(Localization.lang("Date"));
        dateColumn.setPrefWidth(140);
        dateColumn.setCellValueFactory(data -> new SimpleStringProperty(
                data.getValue().dateTime().format(DATE_TIME_FORMAT)));

        TableColumn<LiturgicalService, String> typeColumn = new TableColumn<>(Localization.lang("Type"));
        typeColumn.setPrefWidth(120);
        typeColumn.setCellValueFactory(data -> new SimpleStringProperty(EnumDisplay.of(data.getValue().type())));

        TableColumn<LiturgicalService, String> locationColumn = new TableColumn<>(Localization.lang("Location"));
        locationColumn.setPrefWidth(120);
        locationColumn.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().location()));

        TableColumn<LiturgicalService, LiturgicalService> assignedColumn =
                new TableColumn<>(Localization.lang("Assigned"));
        assignedColumn.setPrefWidth(90);
        assignedColumn.setCellValueFactory(data -> new SimpleObjectProperty<>(data.getValue()));
        assignedColumn.setCellFactory(column -> new TableCell<>() {
            @Override
            protected void updateItem(LiturgicalService service, boolean empty) {
                super.updateItem(service, empty);
                if (empty || service == null) {
                    setText(null);
                    setGraphic(null);
                    return;
                }
                AssignedCount count = assignedCount(service);
                setText(count.toString());
                if (count.underfilled()) {
                    FontIcon warningIcon = new FontIcon("mdi2a-alert-circle");
                    warningIcon.getStyleClass().add("altar-warning-icon");
                    setGraphic(warningIcon);
                } else {
                    setGraphic(null);
                }
            }
        });

        table().getColumns().add(dateColumn);
        table().getColumns().add(typeColumn);
        table().getColumns().add(locationColumn);
        table().getColumns().add(assignedColumn);

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
            if (viewModel.generateFromTemplates(fromPicker.getValue(), toPicker.getValue())) {
                refresh();
            }
        });

        Button newButton = new Button(Localization.lang("New"));
        newButton.setOnAction(event -> newItem());
        Button deleteButton = new Button(Localization.lang("Delete"));
        deleteButton.disableProperty().bind(table().getSelectionModel().selectedItemProperty().isNull());
        deleteButton.setOnAction(event -> deleteSelected());
        Button saveAllButton = new Button(Localization.lang("Save all"));
        saveAllButton.disableProperty().bind(dirtyCountProperty().isEqualTo(0).and(planDirty.not()).or(solving));
        saveAllButton.setOnAction(event -> onSaveAll());

        ServiceCsvMapper serviceCsvMapper = new ServiceCsvMapper(roleRepository);
        CsvRowMapper<LiturgicalService> csvMapper =
                CsvRowMapper.of(serviceCsvMapper::header, serviceCsvMapper::toRow, serviceCsvMapper::fromRow);
        Button exportButton = new Button(Localization.lang("Export"));
        exportButton.setOnAction(event -> exportCsv(csvMapper));
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
        Button exportPlanButton = new Button(Localization.lang("Export plan"));
        exportPlanButton.disableProperty().bind(solving.or(hasPlan.not()));
        exportPlanButton.setOnAction(event -> onExportPlan());
        Button archiveButton = new Button(Localization.lang("Archived plans"));
        archiveButton.setOnAction(event -> ArchivedPlansDialog.show(planningViewModel, table().getScene().getWindow()));

        toolbarExtras().addAll(newButton, deleteButton, saveAllButton, new Separator(Orientation.VERTICAL),
                new Label(Localization.lang("From")), fromPicker,
                new Label(Localization.lang("To")), toPicker, generateButton,
                new Separator(Orientation.VERTICAL), exportButton, importButton,
                new Separator(Orientation.VERTICAL),
                solveAllButton, solvingIndicator, stopButton, exportPlanButton, archiveButton,
                new Separator(Orientation.VERTICAL), scoreLabel, statusLabel);
    }

    @Override
    protected LiturgicalService createStub() {
        return viewModel.createStub();
    }

    @Override
    protected List<LiturgicalService> loadAll() {
        List<LiturgicalService> services = viewModel.findAll();
        rebuildCurrentPlan();
        return services;
    }

    @Override
    protected void persist(LiturgicalService service) {
        viewModel.save(service);
    }

    @Override
    protected void delete(LiturgicalService service) {
        viewModel.delete(service);
    }

    @Override
    protected Object identity(LiturgicalService service) {
        return service.id();
    }

    @Override
    protected boolean isEquivalent(LiturgicalService a, LiturgicalService b) {
        return a.dateTime().equals(b.dateTime())
                && a.durationMinutes() == b.durationMinutes()
                && a.location().equals(b.location())
                && a.type() == b.type()
                && a.note().equals(b.note())
                && RoleSlotsEditor.sameSlots(a.slots(), b.slots());
    }

    @Override
    protected Node buildEditor(LiturgicalService service) {
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

        Map<String, Integer> originalSlotCounts = new HashMap<>();
        service.slots().forEach(slot -> originalSlotCounts.put(slot.role(), slot.count()));
        // RoleSlotsEditor's onChange callback needs to mark its own label
        // dirty, but that label (slotsEditor.label) doesn't exist until the
        // RoleSlotsEditor constructor - which is where the callback itself
        // gets built - returns. A one-element array sidesteps the
        // chicken-and-egg: the callback only runs later, in response to a
        // spinner change, well after the array's single slot is filled in
        // right below the constructor call.
        Region[] slotsListHolder = new Region[1];

        Runnable[] pushLiveHolder = new Runnable[1];
        VBox assignmentSection = new VBox(6);
        RoleSlotsEditor slotsEditor = new RoleSlotsEditor(viewModel.findAllRoles(), service.slots(),
                liveSlots -> {
                    assignmentSection.getChildren().setAll(buildAssignmentRows(service, liveSlots, assignmentSection));
                    // The row-rebuild above already updates liveSlotsForEditor
                    // (assignedLabel()'s source for this row's live total),
                    // but the table's own "Assigned" cell only re-reads it
                    // once the table actually refreshes - a decremented
                    // count otherwise left the column showing the old,
                    // now-stale ratio until something else (a solve, a
                    // manual pick) happened to trigger a refresh.
                    table().refresh();
                    Map<String, Integer> liveCounts = new HashMap<>();
                    liveSlots.forEach(slot -> liveCounts.put(slot.role(), slot.count()));
                    setFieldChanged(slotsListHolder[0], !liveCounts.equals(originalSlotCounts));
                    pushLiveHolder[0].run();
                });
        slotsListHolder[0] = slotsEditor.label;
        assignmentSection.getChildren().setAll(buildAssignmentRows(service, slotsEditor.collectSlots(), assignmentSection));

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

        Runnable pushLive = () -> {
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

        VBox content = new VBox(10, grid);
        if (currentPlan != null) {
            Label altarServersTitle = new Label(Localization.lang("Altar servers"));
            Button autoFillButton = new Button(null, new FontIcon("mdi2a-auto-fix"));
            autoFillButton.getStyleClass().addAll(Styles.BUTTON_ICON, Styles.FLAT, Styles.SMALL);
            autoFillButton.disableProperty().bind(solving);
            autoFillButton.setOnAction(event -> onAutoFillService(service));
            Tooltip.install(autoFillButton, new Tooltip(Localization.lang("Auto-fill")));
            // A solve can run for several seconds with the button just
            // greyed out otherwise - no visible sign it's doing anything.
            // Swapped for a small spinner (shared "solving" flag, so this
            // also lights up during a whole-plan "Solve all" run - correct,
            // the solver really is busy either way).
            ProgressIndicator autoFillIndicator = new ProgressIndicator();
            autoFillIndicator.setPrefSize(16, 16);
            solving.addListener((obs, wasSolving, isSolving) ->
                    autoFillButton.setGraphic(isSolving ? autoFillIndicator : new FontIcon("mdi2a-auto-fix")));
            Region titleSpacer = new Region();
            HBox.setHgrow(titleSpacer, Priority.ALWAYS);
            HBox altarServersHeader = new HBox(8, altarServersTitle, titleSpacer, autoFillButton);
            altarServersHeader.setAlignment(Pos.CENTER_LEFT);

            content.getChildren().addAll(new Separator(), altarServersHeader, assignmentSection);
        }
        content.setPadding(new Insets(12));
        content.setMinHeight(EDITOR_MIN_HEIGHT);
        markDirtyOnChange(dateField.valueProperty(), service.dateTime().toLocalDate(), dateLabel);
        markDirtyOnChange(timeField.timeProperty(), service.dateTime().toLocalTime(), timeLabel);
        markDirtyOnChange(typeBox.valueProperty(), service.type(), typeLabel);
        markDirtyOnChange(locationField.textProperty(), service.location(), locationLabel);
        markDirtyOnChange(noteField.textProperty(), service.note(), noteLabel);
        return content;
    }

    /**
     * Left border accent on {@code label} while {@code property}'s current
     * value differs from {@code original} (the just-loaded, on-disk value) -
     * a lightweight "you have unsaved changes here" cue that needs no
     * field-by-field "was this the one that changed" bookkeeping: each field
     * just watches its own drift from where it started, and clears itself
     * the moment the value round-trips back to the original (e.g. undoing a
     * typo). On the field's own label, not the field itself - keeps the
     * accent out of the way of a field's own focus/validation styling.
     * Saving rebuilds the whole editor against the freshly-saved service as
     * the new baseline, so every field naturally reports clean again with no
     * extra reset step.
     */
    private static <T> void markDirtyOnChange(ObservableValue<T> property, T original, Region label) {
        property.addListener((obs, oldValue, newValue) -> setFieldChanged(label, !Objects.equals(newValue, original)));
        setFieldChanged(label, !Objects.equals(property.getValue(), original));
    }

    /** Toggles the left-border "unsaved change" accent (see {@code .field-changed} in ThemeStyler) on or off. */
    private static void setFieldChanged(Region label, boolean changed) {
        if (changed) {
            if (!label.getStyleClass().contains("field-changed")) {
                label.getStyleClass().add("field-changed");
            }
        } else {
            label.getStyleClass().remove("field-changed");
        }
    }

    /**
     * Open-slot fill rows for {@code service}, driven by {@code liveSlots} -
     * the role/count editor's current (possibly unsaved) values, not just
     * whatever's in {@link #currentPlan} - so the list updates the moment a
     * slot count changes, before Save. A slot instance still backed by an
     * {@link Assignment} in {@link #currentPlan} (matched by its stable id,
     * {@code service:role:index}) gets an editable dropdown seeded with its
     * current server; one that isn't - a count bumped up since the plan was
     * last built - gets a disabled placeholder, since there's nothing to
     * write a pick into until Save regenerates the plan for the new count.
     * Decrementing then incrementing a count back before saving never drops
     * an assignment: the underlying {@link Assignment} objects in {@code
     * currentPlan} aren't touched by hiding their row, only by an actual
     * rebuild (Save, tab reactivation, or a range change), so the previously
     * assigned server reappears exactly as it was.
     *
     * <p>{@code assignmentSection} is threaded through so a manual pick can
     * rebuild these rows in place: violations (and the warning icon) are
     * computed fresh on every call, so without this a slot's violation
     * status would only ever catch up on the next full editor rebuild
     * (Save, a solve, reselecting the row) rather than the moment the pick
     * is made.
     */
    private List<Node> buildAssignmentRows(LiturgicalService service, List<RoleSlot> liveSlots, VBox assignmentSection) {
        liveSlotsServiceId = service.id();
        liveSlotsForEditor = liveSlots;
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
                        finalAssignment.setPinned(newServer != null);
                        recomputePlanDirty();
                        assignmentSection.getChildren().setAll(buildAssignmentRows(service, liveSlots, assignmentSection));
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

    /**
     * Filled/total counts backing the "Assigned" column; {@code filled} is
     * {@code -1} when no plan is loaded (nothing to be filled yet - the
     * label falls back to just the total). For the service currently open
     * in the editor, {@code total} is the live (possibly unsaved) slot
     * count - otherwise a filled slot just added in the editor but not yet
     * saved would show as e.g. "3/2" against the still-persisted total.
     */
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

    private void onRangeChanged() {
        currentPlan = null;
        reloadSavedPlanSnapshot();
        rebuildCurrentPlan();
        table().refresh();
    }

    /** Same period, preserving {@link #currentPlan}'s in-progress assignments while picking up service/roster edits. */
    private void rebuildCurrentPlan() {
        LocalDate from = fromPicker.getValue();
        LocalDate to = toPicker.getValue();
        if (from == null || to == null || to.isBefore(from)) {
            currentPlan = null;
            hasPlan.set(false);
            refreshScoreAndStatus();
            return;
        }
        currentPlan = currentPlan == null
                ? planningViewModel.generateProblem(from, to)
                : planningViewModel.rebuildPreservingAssignments(currentPlan, from, to);
        hasPlan.set(!currentPlan.getAssignments().isEmpty());
        recomputePlanDirty();
        refreshScoreAndStatus();
    }

    /** Reloads {@link #savedPlanSnapshot} for whatever horizon is currently picked (or clears it if none is saved for it). */
    private void reloadSavedPlanSnapshot() {
        LocalDate from = fromPicker.getValue();
        LocalDate to = toPicker.getValue();
        savedPlanSnapshot = planningViewModel.loadSavedPlan()
                .filter(saved -> saved.from().equals(from) && saved.toInclusive().equals(to))
                .orElse(null);
    }

    /**
     * Diffs {@link #currentPlan} against {@link #savedPlanSnapshot}, ignoring
     * unassigned/unpinned slots on either side (an empty slot on disk and an
     * empty slot in memory are the same "nothing to save" state regardless of
     * assignment id bookkeeping) - so clearing a pick and then setting it back
     * to its previous value reads clean again, not stuck dirty.
     */
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

    private void onSolveAll() {
        ServicePlan plan = currentPlan;
        if (plan == null || plan.getAssignments().isEmpty()) {
            return;
        }
        solving.set(true);
        statusLabel.setText(Localization.lang("Solving..."));
        jobId = planningViewModel.solveAsync(plan,
                best -> Platform.runLater(() -> {
                    currentPlan = best;
                    refreshScoreAndStatus();
                    table().refresh();
                }),
                finalBest -> Platform.runLater(() -> {
                    currentPlan = finalBest;
                    solving.set(false);
                    recomputePlanDirty();
                    refreshScoreAndStatus();
                    refreshSelectedEditor();
                    table().refresh();
                    statusLabel.setText(Localization.lang("Solving finished"));
                }),
                error -> Platform.runLater(() -> {
                    solving.set(false);
                    LOGGER.error("Solving failed", error);
                    statusLabel.setText(Localization.lang("Solving failed: %0", error.getMessage()));
                }));
    }

    /**
     * Solves only {@code service}'s open slots: every other assignment is
     * pinned for the duration of the solve (so it can't be shifted), then
     * restored to its original pin state afterward - {@code service}'s own
     * newly-filled slots become pinned instead, the same as a manual pick,
     * since the planner asked for this fill just as deliberately.
     */
    private void onAutoFillService(LiturgicalService service) {
        ServicePlan plan = currentPlan;
        if (plan == null || solving.get()) {
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
                best -> Platform.runLater(() -> currentPlan = best),
                finalBest -> Platform.runLater(() -> {
                    currentPlan = finalBest;
                    for (Assignment assignment : finalBest.getAssignments()) {
                        if (assignment.getService().id().equals(service.id())) {
                            assignment.setPinned(assignment.getServer() != null);
                        } else {
                            Boolean wasPinned = pinSnapshot.get(assignment.getId());
                            assignment.setPinned(wasPinned != null && wasPinned);
                        }
                    }
                    solving.set(false);
                    recomputePlanDirty();
                    refreshScoreAndStatus();
                    refreshSelectedEditor();
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

    /**
     * "Save all" persists both halves of this module's live state in one
     * click: dirty {@link LiturgicalService} records (via the inherited
     * {@link #saveAll()}) and, if {@link #planDirty} (an assignment was
     * picked or a solve ran since the last save), the current plan.
     */
    private void onSaveAll() {
        saveAll();
        ServicePlan plan = currentPlan;
        if (plan != null && planDirty.get()) {
            planningViewModel.savePlan(plan, fromPicker.getValue(), toPicker.getValue());
            savedPlanSnapshot = PlanMapper.toAcceptedPlan(plan, fromPicker.getValue(), toPicker.getValue());
            recomputePlanDirty();
        }
        statusLabel.setText(Localization.lang("Saved"));
    }

    private void onExportPlan() {
        ServicePlan plan = currentPlan;
        if (plan == null || plan.getAssignments().isEmpty()) {
            return;
        }
        FileChooser chooser = new FileChooser();
        chooser.setTitle(Localization.lang("Export plan"));
        chooser.getExtensionFilters().addAll(
                new FileChooser.ExtensionFilter("PDF", "*.pdf"),
                new FileChooser.ExtensionFilter("CSV", "*.csv"),
                new FileChooser.ExtensionFilter("TXT", "*.txt"),
                new FileChooser.ExtensionFilter("RTF", "*.rtf"),
                new FileChooser.ExtensionFilter("Markdown", "*.md"));
        planningViewModel.lastExportDirectory()
                .map(Path::toFile)
                .filter(File::isDirectory)
                .ifPresent(chooser::setInitialDirectory);
        chooser.setInitialFileName("MinDis-" + fromPicker.getValue() + ".pdf");
        File target = chooser.showSaveDialog(table().getScene().getWindow());
        if (target == null) {
            return;
        }
        planningViewModel.rememberExportDirectory(target.getParentFile().toPath());
        PlanExportFormat format = PlanningViewModel.resolveFormat(
                target.getName(), chooser.getSelectedExtensionFilter().getExtensions());
        try {
            planningViewModel.exportPlan(plan, fromPicker.getValue(), toPicker.getValue(), target.toPath(), format);
            statusLabel.setText(Localization.lang("%0 saved to %1", format.name(), target.getName()));
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

    /** Rebuilds the editor for the currently selected row, if any - assignment objects may be stale after a solve. */
    private void refreshSelectedEditor() {
        LiturgicalService selected = table().getSelectionModel().getSelectedItem();
        if (selected != null) {
            editorProperty().set(buildEditor(selected));
        }
    }
}
