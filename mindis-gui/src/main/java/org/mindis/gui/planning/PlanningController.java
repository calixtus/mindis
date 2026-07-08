package org.mindis.gui.planning;

import ai.timefold.solver.core.api.score.HardMediumSoftScore;

import io.avaje.inject.Prototype;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
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
import com.dlsc.gemsfx.CalendarPicker;

import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.cell.CheckBoxTableCell;
import javafx.scene.control.cell.ComboBoxTableCell;
import javafx.stage.FileChooser;
import javafx.util.StringConverter;

import java.io.File;
import java.nio.file.Path;

import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.mindis.core.export.PlanExportFormat;
import org.mindis.core.l10n.Localization;
import org.mindis.core.model.Server;
import org.mindis.core.planning.ServicePlan;
import org.mindis.gui.util.CalendarPickers;

/**
 * Planning workflow: load assignments for a horizon, solve with live score
 * updates, manually swap/pin servers, re-solve, save the accepted plan.
 */
@Prototype
public class PlanningController {

    private static final DateTimeFormatter DATE_TIME_FORMAT = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm");
    private static final Logger LOGGER = LoggerFactory.getLogger(PlanningController.class);

    private final PlanningViewModel viewModel;

    @FXML
    private CalendarPicker fromPicker;
    @FXML
    private CalendarPicker toPicker;
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

    private @Nullable ServicePlan currentPlan;
    private @Nullable UUID jobId;

    // NullAway: @FXML fields are populated by FXMLLoader reflection right
    // after this constructor runs, before initialize() is called.
    @SuppressWarnings("NullAway.Init")
    public PlanningController(PlanningViewModel viewModel) {
        this.viewModel = viewModel;
    }

    @FXML
    private void initialize() {
        CalendarPickers.applyIsoFormat(fromPicker);
        CalendarPickers.applyIsoFormat(toPicker);

        LocalDate firstOfNextMonth = LocalDate.now().plusMonths(1).withDayOfMonth(1);
        fromPicker.setValue(firstOfNextMonth);
        toPicker.setValue(firstOfNextMonth.plusMonths(1).minusDays(1));

        dateColumn.setCellValueFactory(data -> new SimpleStringProperty(
                data.getValue().serviceStart().format(DATE_TIME_FORMAT)));
        serviceColumn.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().serviceLabel()));
        roleColumn.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().roleName()));
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

        viewModel.loadSavedPlan().ifPresent(saved -> {
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
        currentPlan = viewModel.generateProblem(from, to);
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
        jobId = viewModel.solveAsync(
                currentPlan,
                best -> Platform.runLater(() -> applySolution(best, false)),
                finalBest -> Platform.runLater(() -> {
                    try {
                        applySolution(finalBest, true);
                        statusLabel.setText(Localization.lang("Solving finished"));
                    } catch (RuntimeException e) {
                        LOGGER.error("Could not apply the final solution", e);
                        statusLabel.setText(Localization.lang("Solving failed: %0", e.getMessage()));
                    } finally {
                        solving.set(false);
                    }
                }),
                error -> Platform.runLater(() -> {
                    solving.set(false);
                    LOGGER.error("Solving failed", error);
                    statusLabel.setText(Localization.lang("Solving failed: %0", error.getMessage()));
                }));
    }

    @FXML
    private void onStop() {
        if (jobId != null) {
            viewModel.stopSolving(jobId);
        }
    }

    @FXML
    private void onSave() {
        if (currentPlan == null) {
            return;
        }
        viewModel.savePlan(currentPlan, fromPicker.getValue(), toPicker.getValue());
        statusLabel.setText(Localization.lang("Plan saved"));
    }

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
        viewModel.lastExportDirectory()
                .map(Path::toFile)
                .filter(File::isDirectory)
                .ifPresent(chooser::setInitialDirectory);
        chooser.setInitialFileName("MinDis-" + fromPicker.getValue() + ".pdf");
        File target = chooser.showSaveDialog(assignmentsTable.getScene().getWindow());
        if (target == null) {
            return;
        }
        viewModel.rememberExportDirectory(target.getParentFile().toPath());
        PlanExportFormat format = PlanningViewModel.resolveFormat(
                target.getName(), chooser.getSelectedExtensionFilter().getExtensions());
        try {
            viewModel.exportPlan(currentPlan, fromPicker.getValue(), toPicker.getValue(), target.toPath(), format);
            statusLabel.setText(Localization.lang("%0 saved to %1", format.name(), target.getName()));
        } catch (RuntimeException e) {
            statusLabel.setText(Localization.lang("%0 export failed: %1", format.name(), e.getMessage()));
        }
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

    // NullAway: only called right after currentPlan is (re)assigned in
    // onGenerate()/refreshFromRepositories(), never while it's null.
    @SuppressWarnings("NullAway")
    private void setupServerColumn() {
        ObservableList<Server> choices = FXCollections.observableArrayList(currentPlan.getServers());
        choices.addFirst(null);
        serverColumn.setCellFactory(ComboBoxTableCell.forTableColumn(new StringConverter<>() {
            @Override
            public String toString(@Nullable Server server) {
                return server == null ? "-" : server.displayName();
            }

            @Override
            public @Nullable Server fromString(String string) {
                return null;
            }
        }, choices));
        serverColumn.setOnEditCommit(event -> {
            event.getRowValue().setServerManually(event.getNewValue());
            refreshScoreAndViolations();
        });
    }

    // NullAway: only called from applySolution/onGenerate/refreshFromRepositories,
    // always right after currentPlan is (re)assigned.
    @SuppressWarnings("NullAway")
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
        updateScoreLabel(viewModel.scoreOf(currentPlan));
        Map<String, List<String>> violations = viewModel.violationsByAssignment(currentPlan);
        for (AssignmentRow row : rows) {
            List<String> names = violations.getOrDefault(row.id(), List.of());
            row.violationsProperty().set(names.stream()
                    .map(Localization::lang)
                    .distinct()
                    .collect(Collectors.joining(", ")));
        }
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
        currentPlan = viewModel.rebuildPreservingAssignments(currentPlan, fromPicker.getValue(), toPicker.getValue());
        setupServerColumn();
        rebuildRows();
        refreshScoreAndViolations();
    }
}
