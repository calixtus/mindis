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
import java.util.stream.Collectors;

import javafx.application.Platform;
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
import javafx.util.StringConverter;

import org.mindis.core.l10n.Localization;
import org.mindis.core.model.Server;
import org.mindis.core.persistence.PlanRepository;
import org.mindis.core.planning.PlanningService;
import org.mindis.core.planning.ServicePlan;
import org.mindis.core.preferences.PreferencesService;
import org.mindis.gui.util.EnumDisplay;

/**
 * Planning workflow: load assignments for a horizon, solve with live score
 * updates, manually swap/pin servers, re-solve, save the accepted plan.
 */
@Prototype
public class PlanningController {

    private static final DateTimeFormatter DATE_TIME_FORMAT = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm");

    private final PlanningService planningService;
    private final PlanRepository planRepository;
    private final PreferencesService preferencesService;

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

    private ServicePlan currentPlan;
    private UUID jobId;

    public PlanningController(PlanningService planningService,
                              PlanRepository planRepository,
                              PreferencesService preferencesService) {
        this.planningService = planningService;
        this.planRepository = planRepository;
        this.preferencesService = preferencesService;
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
                .ifPresent(saved -> planningService.applyAcceptedPlan(currentPlan, saved));
        setupServerColumn();
        rebuildRows();
        refreshScoreAndViolations();
        saveButton.setDisable(currentPlan.getAssignments().isEmpty());
        statusLabel.setText(Localization.lang("%0 slots loaded",
                currentPlan.getAssignments().size()));
    }

    @FXML
    private void onSolve() {
        if (currentPlan == null || currentPlan.getAssignments().isEmpty()) {
            return;
        }
        setSolving(true);
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
                        statusLabel.setText(Localization.lang("Solving failed: %0", e.getMessage()));
                    } finally {
                        setSolving(false);
                    }
                }),
                error -> Platform.runLater(() -> {
                    setSolving(false);
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
        planRepository.save(planningService.toAcceptedPlan(
                currentPlan, fromPicker.getValue(), toPicker.getValue()));
        statusLabel.setText(Localization.lang("Plan saved"));
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
        List<AssignmentRow> rows = currentPlan.getAssignments().stream()
                .sorted(Comparator.comparing(a -> a.serviceStart()))
                .map(AssignmentRow::new)
                .toList();
        assignmentsTable.setItems(FXCollections.observableArrayList(rows));
    }

    private void refreshScoreAndViolations() {
        if (currentPlan == null || currentPlan.getAssignments().isEmpty()) {
            scoreLabel.setText("");
            return;
        }
        updateScoreLabel(planningService.scoreOf(currentPlan));
        Map<String, List<String>> violations = planningService.violationsByAssignment(currentPlan);
        for (AssignmentRow row : assignmentsTable.getItems()) {
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

    private void setSolving(boolean solving) {
        solveButton.setDisable(solving);
        stopButton.setDisable(!solving);
        saveButton.setDisable(solving);
        fromPicker.setDisable(solving);
        toPicker.setDisable(solving);
    }
}
