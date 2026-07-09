package org.mindis.gui.modules;

import ai.timefold.solver.core.api.score.HardMediumSoftScore;

import java.io.File;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import javafx.application.Platform;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleStringProperty;
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
import javafx.scene.control.TableColumn;
import javafx.scene.control.TextField;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import javafx.util.StringConverter;

import com.dlsc.gemsfx.CalendarPicker;
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
import org.mindis.core.planning.Assignment;
import org.mindis.core.planning.ServicePlan;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.mindis.gui.planning.ArchivedPlansDialog;
import org.mindis.gui.planning.PlanningViewModel;
import org.mindis.gui.util.CalendarPickers;
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

    private final ServicesViewModel viewModel;
    private final PlanningViewModel planningViewModel;

    private final CalendarPicker fromPicker = CalendarPickers.create();
    private final CalendarPicker toPicker = CalendarPickers.create();
    private final Label scoreLabel = new Label();
    private final Label statusLabel = new Label();
    private final BooleanProperty solving = new SimpleBooleanProperty(false);
    private final BooleanProperty hasPlan = new SimpleBooleanProperty(false);

    private @Nullable ServicePlan currentPlan;
    private @Nullable UUID jobId;

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

        TableColumn<LiturgicalService, String> assignedColumn = new TableColumn<>(Localization.lang("Assigned"));
        assignedColumn.setPrefWidth(90);
        assignedColumn.setCellValueFactory(data -> new SimpleStringProperty(assignedLabel(data.getValue())));

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
        Button stopButton = new Button(Localization.lang("Stop"));
        stopButton.disableProperty().bind(solving.not());
        stopButton.setOnAction(event -> onStop());
        Button savePlanButton = new Button(Localization.lang("Save plan"));
        savePlanButton.disableProperty().bind(solving.or(hasPlan.not()));
        savePlanButton.setOnAction(event -> onSavePlan());
        Button exportPlanButton = new Button(Localization.lang("Export plan"));
        exportPlanButton.disableProperty().bind(solving.or(hasPlan.not()));
        exportPlanButton.setOnAction(event -> onExportPlan());
        Button archiveButton = new Button(Localization.lang("Archived plans"));
        archiveButton.setOnAction(event -> ArchivedPlansDialog.show(planningViewModel, table().getScene().getWindow()));

        toolbarExtras().addAll(newButton, deleteButton, new Separator(Orientation.VERTICAL),
                new Label(Localization.lang("From")), fromPicker,
                new Label(Localization.lang("To")), toPicker, generateButton,
                new Separator(Orientation.VERTICAL), exportButton, importButton,
                new Separator(Orientation.VERTICAL),
                solveAllButton, stopButton, savePlanButton, exportPlanButton, archiveButton,
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
    protected Node buildEditor(LiturgicalService service) {
        CalendarPicker dateField = CalendarPickers.create();
        dateField.setValue(service.dateTime().toLocalDate());
        TextField timeField = new TextField(service.dateTime().toLocalTime().toString());
        timeField.setPromptText("10:00");

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

        VBox assignmentSection = new VBox(6);
        RoleSlotsEditor slotsEditor = new RoleSlotsEditor(viewModel.findAllRoles(), service.slots(),
                liveSlots -> assignmentSection.getChildren().setAll(buildAssignmentRows(service, liveSlots)));
        assignmentSection.getChildren().setAll(buildAssignmentRows(service, slotsEditor.collectSlots()));

        GridPane grid = new GridPane();
        grid.setHgap(8);
        grid.setVgap(8);
        ColumnConstraints labelColumn = new ColumnConstraints();
        labelColumn.setMinWidth(110);
        ColumnConstraints fieldColumn = new ColumnConstraints();
        fieldColumn.setHgrow(Priority.ALWAYS);
        grid.getColumnConstraints().addAll(labelColumn, fieldColumn);

        int row = 0;
        grid.add(new Label(Localization.lang("Date")), 0, row);
        grid.add(dateField, 1, row++);
        grid.add(new Label(Localization.lang("Time")), 0, row);
        grid.add(timeField, 1, row++);
        grid.add(new Label(Localization.lang("Type")), 0, row);
        grid.add(typeBox, 1, row++);
        grid.add(new Label(Localization.lang("Location")), 0, row);
        grid.add(locationField, 1, row++);
        grid.add(new Label(Localization.lang("Note")), 0, row);
        grid.add(noteField, 1, row++);

        GridPane.setValignment(slotsEditor.label, VPos.TOP);
        grid.add(slotsEditor.label, 0, row);
        GridPane.setVgrow(slotsEditor.list(), Priority.ALWAYS);
        grid.add(slotsEditor.list(), 1, row++);

        Button saveButton = new Button(Localization.lang("Save"));
        saveButton.setDefaultButton(true);
        saveButton.setOnAction(event -> {
            LocalDate date = dateField.getValue();
            LocalTime time = parseTime(timeField.getText());
            if (date == null || time == null) {
                return;
            }
            save(new LiturgicalService(service.id(), date.atTime(time), service.durationMinutes(),
                    locationField.getText().strip(),
                    typeBox.getValue() == null ? ServiceType.OTHER : typeBox.getValue(),
                    slotsEditor.collectSlots(), noteField.getText().strip()));
        });

        VBox content = new VBox(10, grid, new HBox(saveButton));
        if (currentPlan != null) {
            Button autoFillButton = new Button(Localization.lang("Auto-fill"));
            autoFillButton.disableProperty().bind(solving);
            autoFillButton.setOnAction(event -> onAutoFillService(service));
            content.getChildren().addAll(
                    new Separator(), new Label(Localization.lang("Altar servers")), assignmentSection, autoFillButton);
        }
        content.setPadding(new Insets(12));
        content.setMinHeight(EDITOR_MIN_HEIGHT);
        return content;
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
     */
    private List<Node> buildAssignmentRows(LiturgicalService service, List<RoleSlot> liveSlots) {
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
                    serverBox.setValue(assignment.getServer());
                    serverBox.valueProperty().addListener((obs, oldServer, newServer) -> {
                        assignment.setServer(newServer);
                        assignment.setPinned(newServer != null);
                        refreshScoreAndStatus();
                        table().refresh();
                    });
                } else {
                    serverBox.setDisable(true);
                    serverBox.setPromptText(Localization.lang("Save to assign"));
                }

                Label roleLabel = new Label(roleName);
                roleLabel.setMinWidth(110);
                HBox row = new HBox(8, roleLabel, serverBox);
                row.setAlignment(Pos.CENTER_LEFT);
                // A fixed fraction of the row's own width (not Hgrow.ALWAYS,
                // which used to let the combo box eat the entire remaining
                // row) - bound rather than a flat pixel value so it still
                // scales with the editor pane's width.
                serverBox.prefWidthProperty().bind(row.widthProperty().multiply(0.6));

                List<String> names = assignment == null
                        ? List.of() : violations.getOrDefault(assignment.getId(), List.of());
                if (!names.isEmpty()) {
                    FontIcon warningIcon = new FontIcon("mdi2a-alert-circle");
                    warningIcon.setStyle("-fx-icon-color: -color-danger-fg;");
                    Tooltip.install(warningIcon, new Tooltip(
                            String.join(", ", names.stream().map(Localization::lang).toList())));
                    row.getChildren().add(1, warningIcon);
                }
                rows.add(row);
            }
        }
        return rows;
    }

    /** Filled/total count shown in the "Assigned" column; falls back to just the total when no plan is loaded. */
    private String assignedLabel(LiturgicalService service) {
        int total = service.totalSlots();
        ServicePlan plan = currentPlan;
        if (plan == null) {
            return String.valueOf(total);
        }
        long filled = plan.getAssignments().stream()
                .filter(a -> a.getService().id().equals(service.id()))
                .filter(a -> a.getServer() != null)
                .count();
        return filled + "/" + total;
    }

    private void onRangeChanged() {
        currentPlan = null;
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
        refreshScoreAndStatus();
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
        jobId = planningViewModel.solveAsync(plan,
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

    private void onSavePlan() {
        if (currentPlan == null) {
            return;
        }
        planningViewModel.savePlan(currentPlan, fromPicker.getValue(), toPicker.getValue());
        statusLabel.setText(Localization.lang("Plan saved"));
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

    private static @Nullable LocalTime parseTime(String text) {
        try {
            return LocalTime.parse(text.strip(), DateTimeFormatter.ofPattern("H:mm"));
        } catch (DateTimeParseException e) {
            return null;
        }
    }
}
