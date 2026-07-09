package org.mindis.gui.planning;

import java.io.File;
import java.nio.file.Path;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;

import javafx.beans.property.SimpleStringProperty;
import javafx.geometry.Insets;
import javafx.scene.control.Alert;
import javafx.scene.control.Alert.AlertType;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar.ButtonData;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.control.DialogPane;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import javafx.stage.Window;

import org.mindis.core.export.PlanExportFormat;
import org.mindis.core.l10n.Localization;
import org.mindis.core.planning.AcceptedPlan;

/// Read-only browser over {@link PlanningViewModel#listArchivedPlans()}: every
/// plan whose period the planner has since moved past. Selecting a row and
/// exporting reuses the same {@link PlanExportFormat} file-chooser flow as the
/// active plan's own "Export plan" button - archived plans aren't editable,
/// only viewable and exportable, so there's no re-solve/re-save affordance
/// here.
public final class ArchivedPlansDialog {

    private static final DateTimeFormatter SAVED_AT_FORMAT =
            DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm").withZone(ZoneId.systemDefault());

    private ArchivedPlansDialog() {
    }

    public static void show(PlanningViewModel viewModel, Window owner) {
        List<AcceptedPlan> archived = viewModel.listArchivedPlans();

        TableView<AcceptedPlan> table = new TableView<>();
        table.getItems().setAll(archived);
        table.setPrefWidth(520);
        table.setPrefHeight(320);

        TableColumn<AcceptedPlan, String> periodColumn = new TableColumn<>(Localization.lang("Period"));
        periodColumn.setCellValueFactory(data -> new SimpleStringProperty(
                data.getValue().from() + " - " + data.getValue().toInclusive()));
        periodColumn.setPrefWidth(200);

        TableColumn<AcceptedPlan, String> assignedColumn = new TableColumn<>(Localization.lang("Assigned"));
        assignedColumn.setCellValueFactory(data -> {
            long assigned = data.getValue().assignments().stream()
                    .filter(a -> a.serverId() != null)
                    .count();
            return new SimpleStringProperty(assigned + "/" + data.getValue().assignments().size());
        });
        assignedColumn.setPrefWidth(100);

        TableColumn<AcceptedPlan, String> savedAtColumn = new TableColumn<>(Localization.lang("Saved"));
        savedAtColumn.setCellValueFactory(data -> {
            Instant savedAt = data.getValue().savedAt();
            return new SimpleStringProperty(savedAt == null ? "-" : SAVED_AT_FORMAT.format(savedAt));
        });
        savedAtColumn.setPrefWidth(180);

        table.getColumns().setAll(List.of(periodColumn, assignedColumn, savedAtColumn));

        Label emptyLabel = new Label(Localization.lang("No archived plans yet"));
        table.setPlaceholder(emptyLabel);

        Button exportButton = new Button(Localization.lang("Export selected"));
        exportButton.disableProperty().bind(table.getSelectionModel().selectedItemProperty().isNull());
        exportButton.setOnAction(e -> {
            AcceptedPlan selected = table.getSelectionModel().getSelectedItem();
            if (selected != null) {
                exportPlan(viewModel, selected, owner);
            }
        });

        VBox content = new VBox(8, table, exportButton);
        content.setPadding(new Insets(4));

        Dialog<Void> dialog = new Dialog<>();
        dialog.setTitle(Localization.lang("Archived plans"));
        dialog.initOwner(owner);
        DialogPane pane = dialog.getDialogPane();
        pane.setContent(content);
        pane.getButtonTypes().add(new ButtonType(Localization.lang("Close"), ButtonData.CANCEL_CLOSE));
        dialog.showAndWait();
    }

    private static void exportPlan(PlanningViewModel viewModel, AcceptedPlan plan, Window owner) {
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
        chooser.setInitialFileName("MinDis-" + plan.from() + ".pdf");
        File target = chooser.showSaveDialog(owner);
        if (target == null) {
            return;
        }
        viewModel.rememberExportDirectory(target.getParentFile().toPath());
        PlanExportFormat format = PlanningViewModel.resolveFormat(
                target.getName(), chooser.getSelectedExtensionFilter().getExtensions());
        try {
            viewModel.exportAcceptedPlan(plan, target.toPath(), format);
        } catch (RuntimeException ex) {
            Alert alert = new Alert(AlertType.ERROR);
            alert.setTitle(Localization.lang("Export failed"));
            alert.setHeaderText(Localization.lang("%0 export failed: %1", format.name(), ex.getMessage()));
            alert.initOwner(owner);
            alert.showAndWait();
        }
    }
}
