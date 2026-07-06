package org.mindis.gui.planning;

import ai.timefold.solver.core.api.score.HardMediumSoftScore;

import io.avaje.inject.Prototype;

import java.time.Duration;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

import javafx.application.Platform;
import javafx.beans.binding.Bindings;
import javafx.beans.binding.BooleanBinding;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.cell.CheckBoxTableCell;
import javafx.scene.control.cell.ComboBoxTableCell;
import javafx.stage.FileChooser;
import javafx.util.StringConverter;

import java.io.File;

import org.mindis.core.export.PlanExportFormat;
import org.mindis.core.export.PlanExportService;
import org.mindis.core.l10n.EnumDisplay;
import org.mindis.core.l10n.Localization;
import org.mindis.core.model.Server;
import org.mindis.core.persistence.PlanRepository;
import org.mindis.core.planning.PlanMapper;
import org.mindis.core.planning.PlanningService;
import org.mindis.core.planning.ServicePlan;
import org.mindis.core.preferences.PreferencesService;

/**
 * Planning workflow: load assignments for a horizon, solve with live score
 * updates, manually swap/pin servers, re-solve, save the accepted plan.
 */
@Prototype
public class PlanningController {

    private static final DateTimeFormatter DATE_TIME_FORMAT = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm");
    private static final Logger LOGGER = Logger.getLogger(PlanningController.class.getName());

    private final PlanningService planningService;
    private final PlanRepository planRepository;
    private final PreferencesService preferencesService;
    private final PlanExportService planExportService;

    @FXML
    private DatePicker fromPicker;
    @FXML
    private DatePicker toPicker;
    @FXML
    private Button solveButton;
    @FXML
    private Button stopButton;
    @FXML
    private Button saveButton;
    @FXML
    private Button exportButton;
    @FXML
    private Label scoreLabel;
    @FXML
    private Label statusLabel;
    @FXML
    private TableView<AssignmentRow> assignmentsTable;
    @FXML
    private TableColumn<AssignmentRow, String> dateColumn;
    @FXML
    private TableColumn<AssignmentRow, String> serviceColumn;
    @FXML
    private TableColumn<AssignmentRow, String> roleColumn;
    @FXML
    private TableColumn<AssignmentRow, Server> serverColumn;
    @FXML
    private TableColumn<AssignmentRow, Boolean> pinnedColumn;
    @FXML
    private TableColumn<AssignmentRow, String> violationsColumn;

    private final BooleanProperty solving = new SimpleBooleanProperty(false);
    private final ObservableList<AssignmentRow> rows = FXCollections.observableArrayList();

    private ServicePlan currentPlan;
    private UUID jobId;

    public PlanningController(PlanningService planningService,
                              PlanRepository planRepository,
                              PreferencesService preferencesService,
                              PlanExportService planExportService) {
        this.planningService = planningService;
        this.planRepository = planRepository;
        this.preferencesService = preferencesService;
        this.planExportService = planExportService;
    }

    @FXML
    private void initialize() {
        LocalDate firstOfNextMonth = LocalDate.now().plusMonths(1).withDayOfMonth(1);
        fromPicker.setValue(firstOfNextMonth);
        toPicker.setValue(firstOfNextMonth.plusMonths(1).minusDays(1));

        dateColumn.setCellValueFactory(data -> new SimpleStringProperty(
                data.getValue().assignment().serviceStart().format(DATE_TIME_FORMAT)));
        serviceColumn.setCellValueFactory(data -> new SimpleStringProperty(
                EnumDisplay.of(data.getValue().assignment().getService().type())
                        + " " + data.getValue().assignment().getService().location()));
        roleColumn.setCellValueFactory(data -> new SimpleStringProperty(
                EnumDisplay.of(data.getValue().assignment().getRole())));
        violationsColumn.setCellValueFactory(data -> data.getValue().violationsProperty());

        serverColumn.setCellValueFactory(data -> data.getValue().serverProperty());
        pinnedColumn.setCellValueFactory(data -> data.getValue().pinnedProperty());
        pinnedColumn.setCellFactory(CheckBoxTableCell.forTableColumn(pinnedColumn));

        // All control states derive from two observables: 'solving' and the rows.
        assignmentsTable.setItems(rows);
        BooleanBinding noRows = Bindings.isEmpty(rows);
        solveButton.disableProperty().bind(solving.or(noRows));
        stopButton.disableProperty().bind(solving.not());
        saveButton.disableProperty().bind(solving.or(noRows));
        exportButton.disableProperty().bind(solving.or(noRows));
        fromPicker.disableProperty().bind(solving);
        toPicker.disableProperty().bind(solving);

        planRepository.load().ifPresent(saved -> {
            fromPicker.setValue(saved.from());
            toPicker.setValue(saved.toInclusive());
        });
        onGenerate();
    }

    @FXML
    private void onGenerate() {
        LocalDate from = fromPicker.getValue();
        LocalDate to = toPicker.getValue();
        if (from == null || to == null || to.isBefore(from)) {
            return;
        }
        currentPlan = planningService.buildProblem(from, to);
        planRepository.load()
                .filter(saved -> saved.from().equals(from) && saved.toInclusive().equals(to))
                .ifPresent(saved -> PlanMapper.applyAcceptedPlan(currentPlan, saved));
        setupServerColumn();
        rebuildRows();
        refreshScoreAndViolations();
        statusLabel.setText(Localization.lang("%0 slots loaded",
                currentPlan.getAssignments().size()));
    }

    @FXML
    private void onSolve() {
        if (currentPlan == null || currentPlan.getAssignments().isEmpty()) {
            return;
        }
        solving.set(true);
        statusLabel.setText(Localization.lang("Solving..."));
        int seconds = preferencesService.get().solverSecondsLimit();
        jobId = planningService.solveAsync(
                currentPlan,
                Duration.ofSeconds(seconds),
                best -> Platform.runLater(() -> applySolution(best, false)),
                finalBest -> Platform.runLater(() -> {
                    try {
                        applySolution(finalBest, true);
                        statusLabel.setText(Localization.lang("Solving finished"));
                    } catch (RuntimeException e) {
                        LOGGER.log(Level.SEVERE, "Could not apply the final solution", e);
                        statusLabel.setText(Localization.lang("Solving failed: %0", e.getMessage()));
                    } finally {
                        solving.set(false);
                    }
                }),
                error -> Platform.runLater(() -> {
                    solving.set(false);
                    LOGGER.log(Level.SEVERE, "Solving failed", error);
                    statusLabel.setText(Localization.lang("Solving failed: %0", error.getMessage()));
                }));
    }

    @FXML
    private void onStop() {
        if (jobId != null) {
            planningService.stopSolving(jobId);
        }
    }

    @FXML
    private void onSave() {
        if (currentPlan == null) {
            return;
        }
        planRepository.save(PlanMapper.toAcceptedPlan(
                currentPlan, fromPicker.getValue(), toPicker.getValue()));
        statusLabel.setText(Localization.lang("Plan saved"));
    }

    private static File lastExportDirectory;

    @FXML
    private void onExport() {
        if (currentPlan == null || currentPlan.getAssignments().isEmpty()) {
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
        if (lastExportDirectory != null && lastExportDirectory.isDirectory()) {
            chooser.setInitialDirectory(lastExportDirectory);
        }
        chooser.setInitialFileName("MinDis-" + fromPicker.getValue() + ".pdf");
        File target = chooser.showSaveDialog(assignmentsTable.getScene().getWindow());
        if (target == null) {
            return;
        }
        lastExportDirectory = target.getParentFile();
        PlanExportFormat format = formatOf(target, chooser.getSelectedExtensionFilter());
        try {
            planExportService.export(
                    PlanMapper.toAcceptedPlan(currentPlan, fromPicker.getValue(), toPicker.getValue()),
                    target.toPath(),
                    format);
            statusLabel.setText(Localization.lang("%0 saved to %1", format.name(), target.getName()));
        } catch (RuntimeException e) {
            statusLabel.setText(Localization.lang("%0 export failed: %1", format.name(), e.getMessage()));
        }
    }

    private static PlanExportFormat formatOf(File target, FileChooser.ExtensionFilter selectedFilter) {
        String fileName = target.getName();
        int dot = fileName.lastIndexOf('.');
        if (dot >= 0 && dot < fileName.length() - 1) {
            try {
                return PlanExportFormat.fromExtension(fileName.substring(dot + 1));
            } catch (IllegalArgumentException ignored) {
                // Fall through to the filter the user picked in the chooser.
            }
        }
        return PlanExportFormat.fromExtension(selectedFilter.getExtensions().get(0).substring(2));
    }

    private void applySolution(ServicePlan solution, boolean withViolations) {
        currentPlan = solution;
        rebuildRows();
        if (withViolations) {
            refreshScoreAndViolations();
        } else {
            updateScoreLabel(solution.getScore());
        }
    }

    private void setupServerColumn() {
        ObservableList<Server> choices = FXCollections.observableArrayList(currentPlan.getServers());
        choices.addFirst(null);
        serverColumn.setCellFactory(ComboBoxTableCell.forTableColumn(new StringConverter<>() {
            @Override
            public String toString(Server server) {
                return server == null ? "-" : server.displayName();
            }

            @Override
            public Server fromString(String string) {
                return null;
            }
        }, choices));
        serverColumn.setOnEditCommit(event -> {
            event.getRowValue().setServerManually(event.getNewValue());
            refreshScoreAndViolations();
        });
    }

    private void rebuildRows() {
        rows.setAll(currentPlan.getAssignments().stream()
                .sorted(Comparator.comparing(a -> a.serviceStart()))
                .map(AssignmentRow::new)
                .toList());
    }

    private void refreshScoreAndViolations() {
        if (currentPlan == null || currentPlan.getAssignments().isEmpty()) {
            scoreLabel.setText("");
            return;
        }
        updateScoreLabel(planningService.scoreOf(currentPlan));
        Map<String, List<String>> violations = planningService.violationsByAssignment(currentPlan);
        for (AssignmentRow row : rows) {
            List<String> names = violations.getOrDefault(row.assignment().getId(), List.of());
            row.violationsProperty().set(names.stream()
                    .map(Localization::lang)
                    .distinct()
                    .collect(Collectors.joining(", ")));
        }
    }

    private void updateScoreLabel(HardMediumSoftScore score) {
        if (score == null) {
            scoreLabel.setText("");
            return;
        }
        String feasibility = score.hardScore() == 0 && score.mediumScore() == 0
                ? Localization.lang("Feasible")
                : Localization.lang("Has violations");
        scoreLabel.setText(Localization.lang("Score") + ": " + score + " (" + feasibility + ")");
    }

    /**
     * Re-syncs the plan with the repositories (fresh server/service data)
     * while keeping the current slot decisions and pins by id. Called when
     * the Planning tab is re-activated after edits in other modules.
     */
    public void refreshFromRepositories() {
        if (solving.get()) {
            return;
        }
        if (currentPlan == null) {
            onGenerate();
            return;
        }
        var snapshot = PlanMapper.toAcceptedPlan(
                currentPlan, fromPicker.getValue(), toPicker.getValue());
        currentPlan = planningService.buildProblem(fromPicker.getValue(), toPicker.getValue());
        PlanMapper.applyAcceptedPlan(currentPlan, snapshot);
        setupServerColumn();
        rebuildRows();
        refreshScoreAndViolations();
    }
}
