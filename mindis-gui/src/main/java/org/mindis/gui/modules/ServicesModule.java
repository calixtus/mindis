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
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;

import javafx.application.Platform;
import javafx.beans.binding.Bindings;
import javafx.beans.property.ReadOnlyBooleanProperty;
import javafx.beans.property.SimpleObjectProperty;
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

import atlantafx.base.controls.ToggleSwitch;
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
import org.mindis.core.planning.AssignmentKey;
import org.mindis.core.planning.Autofill;
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
/// from weekly templates), filling their role slots either manually or by
/// running the solver, and the solve/export/archive workflow around that.
///
/// <p>An assignment lives directly on its {@link Slot} (see that class and
/// {@link org.mindis.core.planning.PlanningService}), so a service <em>is</em>
/// its own plan: picking a server, auto-filling, or solving just rewrites the
/// service's slots and stages them into the shared {@link LiveStore} like any
/// other service edit - the one global Save all persists them. There is no
/// separate plan object, no plan-dirty state and no date-range bookkeeping.
/// A {@link ServicePlan} is built transiently only when the solver runs (or to
/// compute a score / per-slot violations) and discarded once its results are
/// written back onto the services.
public class ServicesModule extends CrudModule<LiturgicalService> {

    private static final Logger LOGGER = LoggerFactory.getLogger(ServicesModule.class);
    private static final DateTimeFormatter DATE_TIME_FORMAT = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm");
    private static final double EDITOR_MIN_HEIGHT = 520;
    // Auto-fill only leaves one service's slots free - a far smaller problem
    // than a whole-plan solve, so it doesn't need the full solverSecondsLimit.
    private static final Duration AUTO_FILL_TIME_BUDGET = Duration.ofSeconds(5);
    private static final double TILE_INFO_WIDTH = 180;
    private static final double ROLE_COLUMN_WIDTH = 100;
    private static final double SLOT_COLUMN_WIDTH = 90;
    private static final String GENERATE_POPUP_STYLE = """
            -fx-background-color: -color-bg-overlay;
            -fx-border-color: -color-border-default;
            -fx-border-radius: 4;
            -fx-background-radius: 4;
            """;
    /// How many masses {@link #table()} shows at once - paged because this list
    /// can grow into the hundreds once a year or more has been generated.
    private static final int PAGE_SIZE = 20;

    private final ServicesViewModel viewModel;
    private final PlanningViewModel planningViewModel;
    private final LiveDatabase liveDatabase;
    private final LiveStore<Role> roleStore;
    private final LiveStore<Server> serverStore;

    // Chronological, not the store's raw order - a windowed table only reads
    // sensibly page-to-page if each page is a contiguous date range.
    private final SortedList<LiturgicalService> sortedServices =
            new SortedList<>(store().items(), Comparator.comparing(LiturgicalService::dateTime));
    private final ObservableList<LiturgicalService> pageItems = FXCollections.observableArrayList();
    private final PagingControls pagingControls = new PagingControls();

    private final CalendarPicker fromPicker = CalendarPickers.create();
    private final CalendarPicker toPicker = CalendarPickers.create();
    /// The currently running solve job, if any - View-local bookkeeping so the
    /// Stop button knows what to cancel.
    private @Nullable UUID jobId;

    // The editor's own live (possibly unsaved) slot counts for whichever
    // service is currently open - assignedCount() needs this so the tile's
    // ratio denominator matches a slot just added in the editor, not just what
    // is persisted. Self-correcting: opening another service overwrites these.
    private @Nullable String liveSlotsServiceId;
    private List<Slot> liveSlotsForEditor = List.of();

    public ServicesModule(String name, LiveStore<LiturgicalService> serviceStore, LiveStore<Role> roleStore,
                          LiveStore<Server> serverStore, TemplateRepository templateRepository,
                          RoleRepository roleRepository, PlanningViewModel planningViewModel,
                          LiveDatabase liveDatabase) {
        super(name, "mdi2c-church", serviceStore);
        this.viewModel = new ServicesViewModel(templateRepository, roleRepository);
        this.planningViewModel = planningViewModel;
        this.liveDatabase = liveDatabase;
        this.roleStore = roleStore;
        this.serverStore = serverStore;

        // The table is used as a single-column tile list: each row's cell
        // renders the whole date/type/location + role-slot summary.
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

        ReadOnlyBooleanProperty solving = planningViewModel.solvingProperty();
        Button solveAllButton = new Button(Localization.lang("Solve all"));
        solveAllButton.disableProperty().bind(solving.or(Bindings.isEmpty(store().items())));
        solveAllButton.setOnAction(event -> onSolveAll());
        Button autofillButton = new Button(Localization.lang("Autofill..."));
        autofillButton.disableProperty().bind(solving.or(Bindings.isEmpty(store().items())));
        autofillButton.setOnAction(event -> showAutofillPopup(autofillButton));
        // The solver can run up to the configured time budget with no other
        // visible sign of progress - a spinner makes that wait readable.
        ProgressIndicator solvingIndicator = new ProgressIndicator();
        solvingIndicator.setPrefSize(20, 20);
        solvingIndicator.visibleProperty().bind(solving);
        solvingIndicator.managedProperty().bind(solving);
        Button stopButton = new Button(Localization.lang("Stop"));
        stopButton.disableProperty().bind(solving.not());
        stopButton.setOnAction(event -> onStop());
        SplitMenuButton exportPlanButton = new SplitMenuButton();
        exportPlanButton.setText(Localization.lang("Export"));
        exportPlanButton.disableProperty().bind(solving.or(Bindings.isEmpty(store().items())));
        exportPlanButton.setOnAction(event -> onExportPlan(PlanExportFormat.PDF));
        for (PlanExportFormat format : PlanExportFormat.values()) {
            MenuItem formatItem = new MenuItem(format.name());
            formatItem.setOnAction(event -> onExportPlan(format));
            exportPlanButton.getItems().add(formatItem);
        }
        Button archiveButton = new Button(Localization.lang("Archived plans"));
        archiveButton.setOnAction(event ->
                ArchivedPlansDialog.show(planningViewModel, table().getScene().getWindow(), this::performArchive));

        toolbarExtras().addAll(newButton, deleteButton, new Separator(Orientation.VERTICAL),
                generateButton,
                new Separator(Orientation.VERTICAL), importButton, exportPlanButton,
                new Separator(Orientation.VERTICAL),
                autofillButton, solveAllButton, solvingIndicator, stopButton, archiveButton);
    }

    /// Lightweight popup for "Generate from templates", anchored under the
    /// toolbar button. {@code ServiceGenerator} only proposes occurrences not
    /// already present, and {@link #mergeLive} only ever appends unmatched
    /// rows, so this never touches an existing service's slots or assignments.
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

    /// Popup for the Autofill action: From/To bounds (blank From = "from the
    /// earliest service", blank To = "all future services") plus an "Overwrite
    /// already-assigned slots" toggle. Fills every open slot of every service
    /// in the window in one solve.
    private void showAutofillPopup(Node anchor) {
        CalendarPicker popupFrom = CalendarPickers.create();
        popupFrom.setValue(LocalDate.now());
        CalendarPicker popupTo = CalendarPickers.create();

        ToggleSwitch overwriteToggle = new ToggleSwitch(Localization.lang("Overwrite already-assigned slots"));

        GridPane grid = new GridPane();
        grid.setHgap(8);
        grid.setVgap(8);
        grid.add(new Label(Localization.lang("From")), 0, 0);
        grid.add(popupFrom, 1, 0);
        grid.add(new Label(Localization.lang("To")), 0, 1);
        grid.add(popupTo, 1, 1);
        grid.add(overwriteToggle, 0, 2, 2, 1);

        Popup popup = new Popup();
        popup.setAutoHide(true);

        Button okButton = new Button(Localization.lang("Autofill"));
        okButton.setOnAction(event -> {
            popup.hide();
            onAutofill(popupFrom.getValue(), popupTo.getValue(), overwriteToggle.isSelected());
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

    /// Runs a windowed Autofill: builds a problem over every live service (so
    /// spacing/fairness see the whole board), leaves only the eligible slots
    /// free (in {@code [from, to]}, and either open or - if {@code overwrite} -
    /// already assigned), solves, then writes the results back onto the
    /// services. A blank bound is treated as unbounded.
    private void onAutofill(@Nullable LocalDate from, @Nullable LocalDate to, boolean overwrite) {
        if (planningViewModel.solvingProperty().get()) {
            return;
        }
        List<LiturgicalService> services = List.copyOf(store().items());
        ServicePlan problem = planningViewModel.buildProblem();
        LocalDate effFrom = from == null ? LocalDate.MIN : from;
        LocalDate effTo = to == null ? LocalDate.MAX : to;
        Autofill.Scope scope = Autofill.begin(problem, Autofill.within(effFrom, effTo, overwrite));
        if (scope.eligibleIds().isEmpty()) {
            LOGGER.info(Localization.lang("Nothing to autofill"));
            return;
        }
        planningViewModel.beginSolve();
        LOGGER.info(Localization.lang("Solving..."));
        jobId = planningViewModel.solveAsync(problem,
                best -> { },
                finalBest -> Platform.runLater(() -> {
                    Autofill.finish(finalBest, scope);
                    applySolution(finalBest, services);
                    planningViewModel.finishSolve();
                    LOGGER.info(Localization.lang("Solving finished"));
                }),
                error -> Platform.runLater(() -> {
                    planningViewModel.failSolve();
                    LOGGER.error(Localization.lang("Solving failed: %0", error.getMessage()), error);
                }));
    }

    @Override
    protected ObservableList<LiturgicalService> tableItems() {
        return pageItems;
    }

    @Override
    protected Node belowTable() {
        return pagingControls;
    }

    /// Recomputes {@link #pageItems} for the current page, clamping the page
    /// down if a deletion left it past the new last page.
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
        // Pick up service/roster edits made in other modules since the last
        // visit; the open editor refreshes itself via CrudModule's store
        // listener, so nothing else is needed here.
        table().refresh();
    }

    @Override
    protected EditorBinding<LiturgicalService> buildEditor(LiturgicalService service) {
        ServiceEditor editor = new ServiceEditor(service);
        return new EditorBinding<>(editor.node(), editor::refresh, editor::dispose);
    }

    /// One service row's editor: date/time/type/location/note fields plus the
    /// Altar-servers assignment panel (one server combo per role slot).
    private final class ServiceEditor {

        private final LiturgicalService service;
        private final Supplier<LiturgicalService> baselineSupplier;

        private final CalendarPicker dateField = CalendarPickers.create();
        private final TimePicker timeField = TimePickers.create();
        private final ComboBox<ServiceType> typeBox =
                new ComboBox<>(FXCollections.observableArrayList(ServiceType.values()));
        private final TextField locationField;
        private final TextField noteField;

        private final Label dateLabel = new Label(Localization.lang("Date"));
        private final Label timeLabel = new Label(Localization.lang("Time"));
        private final Label typeLabel = new Label(Localization.lang("Type"));
        private final Label locationLabel = new Label(Localization.lang("Location"));
        private final Label noteLabel = new Label(Localization.lang("Note"));

        private final Label altarServersTitle = new Label(Localization.lang("Altar servers"));
        private final VBox assignmentSection = new VBox(6);
        private final SlotCountEditor slotsEditor;
        private final VBox content;

        private boolean suppressPushLive;
        // The concrete slot instances backing the editor - reconciled (not
        // rebuilt) on every count edit so a role's already-assigned slots keep
        // their ids and assignments across a resize.
        private List<Slot> liveSlots;

        ServiceEditor(LiturgicalService service) {
            this.service = service;
            this.baselineSupplier = () -> Objects.requireNonNullElse(savedSnapshot(service), service);
            this.liveSlots = service.slots();

            dateField.setValue(service.dateTime().toLocalDate());
            timeField.setTime(service.dateTime().toLocalTime());
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
            locationField = new TextField(service.location());
            noteField = new TextField(service.note());

            Button autoFillButton = new Button(null, new FontIcon("mdi2a-auto-fix"));
            autoFillButton.getStyleClass().addAll(Styles.BUTTON_ICON, Styles.FLAT, Styles.SMALL);
            autoFillButton.disableProperty().bind(planningViewModel.solvingProperty());
            autoFillButton.setOnAction(event -> onAutoFillService(service));
            Tooltip.install(autoFillButton, new Tooltip(Localization.lang("Auto-fill")));
            ProgressIndicator autoFillIndicator = new ProgressIndicator();
            autoFillIndicator.setPrefSize(16, 16);
            planningViewModel.solvingProperty().addListener((obs, wasSolving, isSolving) ->
                    autoFillButton.setGraphic(isSolving ? autoFillIndicator : new FontIcon("mdi2a-auto-fix")));
            Region titleSpacer = new Region();
            HBox.setHgrow(titleSpacer, Priority.ALWAYS);
            HBox altarServersHeader = new HBox(8, altarServersTitle, titleSpacer, autoFillButton);
            altarServersHeader.setAlignment(Pos.CENTER_LEFT);
            Separator altarSeparator = new Separator();

            // Bound directly to the shared live role list - a role added,
            // renamed or removed anywhere shows up in this editor on its own.
            slotsEditor = new SlotCountEditor(roleStore.items(), countsByRole(service.slots()), this::onSlotCountsChanged);
            setFieldChanged(slotsEditor.label, slotsChanged(slotsEditor.collectCounts()));
            refreshAssignmentSection();

            GridPane grid = new GridPane();
            grid.setHgap(8);
            grid.setVgap(8);
            ColumnConstraints labelColumn = new ColumnConstraints();
            labelColumn.setMinWidth(110);
            ColumnConstraints fieldColumn = new ColumnConstraints();
            fieldColumn.setHgrow(Priority.ALWAYS);
            grid.getColumnConstraints().addAll(labelColumn, fieldColumn);

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

            dateField.valueProperty().addListener((obs, oldValue, newValue) -> pushLive());
            timeField.timeProperty().addListener((obs, oldValue, newValue) -> pushLive());
            typeBox.valueProperty().addListener((obs, oldValue, newValue) -> pushLive());
            locationField.textProperty().addListener((obs, oldValue, newValue) -> pushLive());
            noteField.textProperty().addListener((obs, oldValue, newValue) -> pushLive());

            content = new VBox(10, grid, altarSeparator, altarServersHeader, assignmentSection);
            content.setPadding(new Insets(12));
            content.setMinHeight(EDITOR_MIN_HEIGHT);
            markDirtyOnChange(dateField.valueProperty(), () -> baselineSupplier.get().dateTime().toLocalDate(), dateLabel);
            markDirtyOnChange(timeField.timeProperty(), () -> baselineSupplier.get().dateTime().toLocalTime(), timeLabel);
            markDirtyOnChange(typeBox.valueProperty(), () -> baselineSupplier.get().type(), typeLabel);
            markDirtyOnChange(locationField.textProperty(), () -> baselineSupplier.get().location(), locationLabel);
            markDirtyOnChange(noteField.textProperty(), () -> baselineSupplier.get().note(), noteLabel);
        }

        Node node() {
            return content;
        }

        private boolean slotsChanged(Map<String, Integer> liveCounts) {
            return !liveCounts.equals(countsByRole(baselineSupplier.get().slots()));
        }

        private void onSlotCountsChanged(Map<String, Integer> liveCounts) {
            liveSlots = reconcileSlots(liveSlots, liveCounts);
            refreshAssignmentSection();
            table().refresh();
            setFieldChanged(slotsEditor.label, slotsChanged(liveCounts));
            pushLive();
        }

        private void refreshAssignmentSection() {
            assignmentSection.getChildren().setAll(buildAssignmentRows());
        }

        private void pushLive() {
            if (suppressPushLive) {
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
                    liveSlots, noteField.getText().strip()));
        }

        /// One editable server dropdown per slot of {@link #liveSlots}, seeded
        /// with the slot's current server and its constraint violations (a
        /// warning icon). Because an assignment lives on the slot itself, a
        /// slot just added by the count editor is immediately assignable - no
        /// "save first" placeholder.
        private List<Node> buildAssignmentRows() {
            liveSlotsServiceId = service.id();
            liveSlotsForEditor = liveSlots;
            setFieldChanged(altarServersTitle, assignmentsChanged());
            if (liveSlots.isEmpty()) {
                return List.of();
            }
            Map<String, Server> serversById = serversById();
            Map<String, Role> rolesById = rolesById();
            // Violations come from a transient problem over the whole live
            // board (double-booking spans services), keyed by assignment id.
            ServicePlan plan = planningViewModel.buildProblem();
            Map<String, List<String>> violations = planningViewModel.violationsByAssignment(plan);

            ObservableList<Server> choices = FXCollections.observableArrayList(activeServers());
            choices.addFirst(null);

            List<Node> rows = new ArrayList<>();
            for (Slot slot : liveSlots) {
                Role role = rolesById.get(slot.role());
                String roleName = role == null ? slot.role() : role.name();
                String assignmentId = new AssignmentKey(service.id(), slot.id()).toId();
                Server current = slot.serverId() == null ? null : serversById.get(slot.serverId());

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
                serverBox.setValue(current);
                serverBox.valueProperty().addListener((obs, oldServer, newServer) -> onPickServer(slot, newServer));

                Label roleLabel = new Label(roleName);
                roleLabel.setMinWidth(110);
                Region spacer = new Region();
                HBox.setHgrow(spacer, Priority.ALWAYS);
                HBox row = new HBox(8, roleLabel, spacer, serverBox);
                row.setAlignment(Pos.CENTER_LEFT);
                serverBox.prefWidthProperty().bind(row.widthProperty().multiply(0.6));

                List<String> names = violations.getOrDefault(assignmentId, List.of());
                // "Slot unassigned" is already obvious from the empty dropdown -
                // only flag genuine rule conflicts on a filled slot.
                if (slot.serverId() != null && !names.isEmpty()) {
                    FontIcon warningIcon = new FontIcon("mdi2a-alert-circle");
                    warningIcon.getStyleClass().add("altar-warning-icon");
                    StackPane iconSlot = new StackPane(warningIcon);
                    Tooltip.install(iconSlot, new Tooltip(
                            String.join(", ", names.stream().map(Localization::lang).toList())));
                    row.getChildren().add(2, iconSlot);
                }
                rows.add(row);
            }
            return rows;
        }

        /// Applies a manual pick: rewrites the slot's server on {@link
        /// #liveSlots}, stages the service, and refreshes the rows/score/table.
        private void onPickServer(Slot slot, @Nullable Server newServer) {
            List<Slot> updated = new ArrayList<>(liveSlots.size());
            for (Slot existing : liveSlots) {
                updated.add(existing.id().equals(slot.id())
                        ? existing.withServer(newServer == null ? null : newServer.id(), newServer != null)
                        : existing);
            }
            liveSlots = updated;
            pushLive();
            refreshAssignmentSection();
            refreshScoreAndStatus();
            table().refresh();
        }

        /// Whether {@link #liveSlots}' assignments (server + pin per slot id)
        /// differ from the last-saved baseline - the per-row unsaved accent.
        private boolean assignmentsChanged() {
            Map<String, Slot> baseline = new HashMap<>();
            for (Slot slot : baselineSupplier.get().slots()) {
                baseline.put(slot.id(), slot);
            }
            for (Slot slot : liveSlots) {
                Slot original = baseline.get(slot.id());
                String originalServer = original == null ? null : original.serverId();
                boolean originalPinned = original != null && original.pinned();
                if (!Objects.equals(slot.serverId(), originalServer) || slot.pinned() != originalPinned) {
                    return true;
                }
            }
            return false;
        }

        void refresh(LiturgicalService updated) {
            suppressPushLive = true;
            try {
                dateField.setValue(updated.dateTime().toLocalDate());
                timeField.setTime(updated.dateTime().toLocalTime());
                typeBox.getSelectionModel().select(updated.type());
                locationField.setText(updated.location());
                noteField.setText(updated.note());
                liveSlots = updated.slots();
                slotsEditor.setCounts(countsByRole(updated.slots()));
            } finally {
                suppressPushLive = false;
            }
            recomputeFieldChanged(dateField.valueProperty(), () -> baselineSupplier.get().dateTime().toLocalDate(), dateLabel);
            recomputeFieldChanged(timeField.timeProperty(), () -> baselineSupplier.get().dateTime().toLocalTime(), timeLabel);
            recomputeFieldChanged(typeBox.valueProperty(), () -> baselineSupplier.get().type(), typeLabel);
            recomputeFieldChanged(locationField.textProperty(), () -> baselineSupplier.get().location(), locationLabel);
            recomputeFieldChanged(noteField.textProperty(), () -> baselineSupplier.get().note(), noteLabel);
            setFieldChanged(slotsEditor.label, slotsChanged(countsByRole(updated.slots())));
            refreshAssignmentSection();
        }

        void dispose() {
            slotsEditor.dispose();
        }
    }

    /// The table row's tile: big-font date/time on the left (with an
    /// underfilled warning icon), type/location below it, and the role-slot
    /// grid on the right.
    private Node buildTileNode(LiturgicalService service) {
        Label dateTimeLabel = new Label(service.dateTime().format(DATE_TIME_FORMAT));
        dateTimeLabel.getStyleClass().add("service-tile-datetime");
        Label typeLabel = new Label(EnumDisplay.of(service.type()));
        Label locationLabel = new Label(service.location());
        VBox left = new VBox(2, dateTimeLabel, typeLabel, locationLabel);
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

    /// Per-service role-slot summary, read-only (picks happen in the editor):
    /// role name, then that role's slots showing the assigned server (or "-").
    private GridPane buildRoleSlotGrid(LiturgicalService service) {
        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(2);
        grid.getColumnConstraints().addAll(roleSlotColumn(ROLE_COLUMN_WIDTH), roleSlotColumn(SLOT_COLUMN_WIDTH),
                roleSlotColumn(SLOT_COLUMN_WIDTH));

        Map<String, Server> serversById = serversById();
        Map<String, Role> rolesById = rolesById();

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
                Slot slot = roleSlots.get(slotIndex);
                Server server = slot.serverId() == null ? null : serversById.get(slot.serverId());
                String text = server == null ? "-" : server.displayName();
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

    /// {@code slots}, grouped by role in first-encountered order.
    private static Map<String, List<Slot>> slotsByRole(List<Slot> slots) {
        Map<String, List<Slot>> byRole = new LinkedHashMap<>();
        for (Slot slot : slots) {
            byRole.computeIfAbsent(slot.role(), roleId -> new ArrayList<>()).add(slot);
        }
        return byRole;
    }

    private static ColumnConstraints roleSlotColumn(double width) {
        ColumnConstraints column = new ColumnConstraints();
        column.setMinWidth(width);
        column.setPrefWidth(width);
        column.setMaxWidth(width);
        column.setHgrow(Priority.NEVER);
        return column;
    }

    /// Reconciles a role's slot count edit, keeping a filled/pinned slot as
    /// long as possible - the {@code isFilled} seam is now the slot's own
    /// stored assignment, no plan lookup needed.
    private List<Slot> reconcileSlots(List<Slot> existing, Map<String, Integer> counts) {
        return SlotReconciler.reconcile(existing, counts, slot -> slot.serverId() != null || slot.pinned());
    }

    /// Slot counts per role, for the {@link SlotCountEditor}.
    private static Map<String, Integer> countsByRole(List<Slot> slots) {
        Map<String, Integer> counts = new LinkedHashMap<>();
        for (Slot slot : slots) {
            counts.merge(slot.role(), 1, Integer::sum);
        }
        return counts;
    }

    private Map<String, Server> serversById() {
        Map<String, Server> byId = new HashMap<>();
        serverStore.items().forEach(server -> byId.put(server.id(), server));
        return byId;
    }

    private List<Server> activeServers() {
        return serverStore.items().stream().filter(Server::active).toList();
    }

    private Map<String, Role> rolesById() {
        Map<String, Role> byId = new HashMap<>();
        roleStore.items().forEach(role -> byId.put(role.id(), role));
        return byId;
    }

    /// Filled/total counts backing the tile's underfilled warning. For the
    /// service open in the editor, {@code total} is the live (possibly unsaved)
    /// slot count.
    private record AssignedCount(int filled, int total) {
        boolean underfilled() {
            return filled < total;
        }
    }

    private AssignedCount assignedCount(LiturgicalService service) {
        List<Slot> slots = service.id().equals(liveSlotsServiceId) ? liveSlotsForEditor : service.slots();
        int filled = (int) slots.stream().filter(slot -> slot.serverId() != null).count();
        return new AssignedCount(filled, slots.size());
    }

    /// Discards every staged edit in the shared database (all modules) and
    /// reloads from disk. There is exactly one Load action app-wide.
    public void loadAll() {
        liveDatabase.loadAll();
    }

    private void onSolveAll() {
        List<LiturgicalService> services = List.copyOf(store().items());
        ServicePlan problem = planningViewModel.buildProblem();
        if (problem.getAssignments().isEmpty()) {
            return;
        }
        planningViewModel.beginSolve();
        LOGGER.info(Localization.lang("Solving..."));
        jobId = planningViewModel.solveAsync(problem,
                best -> { },
                finalBest -> Platform.runLater(() -> {
                    applySolution(finalBest, services);
                    planningViewModel.finishSolve();
                    LOGGER.info(Localization.lang("Solving finished"));
                }),
                error -> Platform.runLater(() -> {
                    planningViewModel.failSolve();
                    LOGGER.error(Localization.lang("Solving failed: %0", error.getMessage()), error);
                }));
    }

    /// Solves only {@code service}'s open slots: every other slot is pinned for
    /// the duration of the solve, then restored afterward (see {@link Autofill}).
    private void onAutoFillService(LiturgicalService service) {
        if (planningViewModel.solvingProperty().get()) {
            return;
        }
        ServicePlan problem = planningViewModel.buildProblem();
        Autofill.Scope scope = Autofill.begin(problem, Autofill.forService(service.id(), false));
        if (scope.eligibleIds().isEmpty()) {
            return;
        }
        planningViewModel.beginSolve();
        LOGGER.info(Localization.lang("Solving..."));
        jobId = planningViewModel.solveAsync(problem, AUTO_FILL_TIME_BUDGET,
                best -> { },
                finalBest -> Platform.runLater(() -> {
                    Autofill.finish(finalBest, scope);
                    applySolution(finalBest, List.of(service));
                    planningViewModel.finishSolve();
                    LOGGER.info(Localization.lang("Solving finished"));
                }),
                error -> Platform.runLater(() -> {
                    planningViewModel.failSolve();
                    LOGGER.error(Localization.lang("Solving failed: %0", error.getMessage()), error);
                }));
    }

    /// Writes {@code solved}'s assignments back onto {@code services} and stages
    /// the updated records into the live store (Save all persists them).
    private void applySolution(ServicePlan solved, List<LiturgicalService> services) {
        mergeLive(planningViewModel.writeBack(solved, services));
        refreshScoreAndStatus();
        table().refresh();
    }

    private void onStop() {
        if (jobId != null) {
            planningViewModel.stopSolving(jobId);
        }
    }

    /// Flushes every module's staged edits to disk in one action. There is
    /// exactly one Save all action app-wide (the global toolbar button).
    public void saveAll() {
        liveDatabase.saveAll();
        LOGGER.info(Localization.lang("Saved"));
    }

    /// Whether the solver is currently running - the global Save all stays disabled while true.
    public ReadOnlyBooleanProperty solvingProperty() {
        return planningViewModel.solvingProperty();
    }

    /// Freezes live services up to {@code cutoff} into self-contained archived
    /// snapshots and removes them from the live list (Save all commits the
    /// removal). Returns whether anything was archived. Supplied to the
    /// Archived Plans dialog as its archive action.
    private boolean performArchive(LocalDate cutoff) {
        var result = planningViewModel.archive(cutoff);
        if (result.isEmpty()) {
            return false;
        }
        Map<String, LiturgicalService> byId = new HashMap<>();
        store().items().forEach(service -> byId.put(service.id(), service));
        for (String id : result.removedServiceIds()) {
            LiturgicalService service = byId.get(id);
            if (service != null) {
                store().remove(service);
            }
        }
        table().refresh();
        return true;
    }

    private void onExportPlan(PlanExportFormat preferredFormat) {
        List<LiturgicalService> services = List.copyOf(store().items());
        if (services.isEmpty()) {
            return;
        }
        Optional<PlanExportChooser.Target> target = PlanExportChooser.show(
                table().getScene().getWindow(), planningViewModel, "MinDis", preferredFormat);
        if (target.isEmpty()) {
            return;
        }
        PlanExportFormat format = target.get().format();
        try {
            planningViewModel.exportLive(services, target.get().file(), format);
            LOGGER.info(Localization.lang("%0 saved to %1", format.name(), target.get().file().getFileName()));
        } catch (RuntimeException e) {
            LOGGER.error(Localization.lang("%0 export failed: %1", format.name(), e.getMessage()), e);
        }
    }

    private void refreshScoreAndStatus() {
        ServicePlan plan = planningViewModel.buildProblem();
        if (plan.getAssignments().isEmpty()) {
            return;
        }
        logScore(planningViewModel.scoreOf(plan));
    }

    /// The score is solver-internal detail, logged rather than shown permanently in the toolbar.
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
