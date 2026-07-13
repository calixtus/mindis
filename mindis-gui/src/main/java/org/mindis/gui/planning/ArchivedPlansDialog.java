package org.mindis.gui.planning;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;

import javafx.beans.property.SimpleStringProperty;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
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
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.stage.Window;

import com.dlsc.gemsfx.CalendarPicker;

import org.mindis.core.export.PlanExportFormat;
import org.mindis.core.l10n.Localization;
import org.mindis.core.planning.AcceptedPlan;

import org.mindis.gui.util.CalendarPickers;

/// Browser over {@link PlanningViewModel#listArchivedPlans()}: every plan whose
/// period the planner has since moved past and frozen. Selecting a row and
/// exporting reuses the same {@link PlanExportFormat} file-chooser flow as the
/// open plan's own "Export plan" button - archived plans aren't editable, only
/// viewable and exportable. Also hosts the "Archive up to..." action, which
/// freezes the open plan up to a chosen cutoff (splitting off the remainder as
/// the new open plan) - placed here, next to the archives it produces, rather
/// than in the always-visible main toolbar since it's an infrequent action.
public final class ArchivedPlansDialog {

    private static final DateTimeFormatter SAVED_AT_FORMAT =
            DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm").withZone(ZoneId.systemDefault());

    private ArchivedPlansDialog() {
    }

    /// Shows the dialog. {@code archiveAction} performs the freeze at a chosen
    /// cutoff and returns whether anything was archived - it lives on the
    /// Services module because archiving also drops the frozen services from
    /// the live list and rebuilds the open plan, needing the module's own
    /// store and bound caches.
    public static void show(PlanningViewModel viewModel, Window owner,
                            java.util.function.Predicate<LocalDate> archiveAction) {
        TableView<AcceptedPlan> table = new TableView<>();
        table.getItems().setAll(viewModel.listArchivedPlans());
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
        table.setPlaceholder(new Label(Localization.lang("No archived plans yet")));

        Button exportButton = new Button(Localization.lang("Export selected"));
        exportButton.disableProperty().bind(table.getSelectionModel().selectedItemProperty().isNull());
        exportButton.setOnAction(e -> {
            AcceptedPlan selected = table.getSelectionModel().getSelectedItem();
            if (selected != null) {
                exportPlan(viewModel, selected, owner);
            }
        });
        Button deleteButton = new Button(Localization.lang("Delete selected"));
        deleteButton.disableProperty().bind(table.getSelectionModel().selectedItemProperty().isNull());
        deleteButton.setOnAction(e -> {
            AcceptedPlan selected = table.getSelectionModel().getSelectedItem();
            if (selected != null && confirmDelete(selected, owner)) {
                viewModel.deleteArchivedPlan(selected);
                table.getItems().setAll(viewModel.listArchivedPlans());
            }
        });

        CalendarPicker cutoffPicker = CalendarPickers.create();
        cutoffPicker.setPromptText(Localization.lang("Cutoff date"));
        Button archiveButton = new Button(Localization.lang("Archive up to..."));
        archiveButton.disableProperty().bind(cutoffPicker.valueProperty().isNull());
        archiveButton.setOnAction(e -> {
            LocalDate cutoff = cutoffPicker.getValue();
            if (cutoff == null) {
                return;
            }
            if (confirmArchive(cutoff, owner)) {
                if (archiveAction.test(cutoff)) {
                    table.getItems().setAll(viewModel.listArchivedPlans());
                    cutoffPicker.setValue(null);
                } else {
                    Alert none = new Alert(AlertType.INFORMATION);
                    none.setTitle(Localization.lang("Archive up to..."));
                    none.setHeaderText(Localization.lang("Nothing to archive up to that date"));
                    none.initOwner(owner);
                    none.showAndWait();
                }
            }
        });

        Region spacer = new Region();
        HBox.setHgrow(spacer, javafx.scene.layout.Priority.ALWAYS);
        HBox actionRow = new HBox(8, exportButton, deleteButton, spacer, cutoffPicker, archiveButton);
        actionRow.setAlignment(Pos.CENTER_LEFT);

        VBox content = new VBox(8, table, actionRow);
        content.setPadding(new Insets(4));

        Dialog<Void> dialog = new Dialog<>();
        dialog.setTitle(Localization.lang("Archived plans"));
        dialog.initOwner(owner);
        DialogPane pane = dialog.getDialogPane();
        pane.setContent(content);
        pane.getButtonTypes().add(new ButtonType(Localization.lang("Close"), ButtonData.CANCEL_CLOSE));
        dialog.showAndWait();
    }

    private static boolean confirmArchive(LocalDate cutoff, Window owner) {
        Alert confirm = new Alert(AlertType.CONFIRMATION);
        confirm.setTitle(Localization.lang("Archive up to..."));
        confirm.setHeaderText(Localization.lang(
                "Freeze every assignment up to %0? The plan is saved and can't be edited afterward.",
                cutoff.toString()));
        confirm.initOwner(owner);
        Optional<ButtonType> result = confirm.showAndWait();
        return result.isPresent() && result.get().getButtonData() == ButtonData.OK_DONE;
    }

    private static boolean confirmDelete(AcceptedPlan plan, Window owner) {
        Alert confirm = new Alert(AlertType.CONFIRMATION);
        confirm.setTitle(Localization.lang("Delete selected"));
        confirm.setHeaderText(Localization.lang("Permanently delete the archived plan %0?",
                plan.from() + " - " + plan.toInclusive()));
        confirm.initOwner(owner);
        Optional<ButtonType> result = confirm.showAndWait();
        return result.isPresent() && result.get().getButtonData() == ButtonData.OK_DONE;
    }

    private static void exportPlan(PlanningViewModel viewModel, AcceptedPlan plan, Window owner) {
        Optional<PlanExportChooser.Target> target = PlanExportChooser.show(
                owner, viewModel, "MinDis-" + plan.from(), PlanExportFormat.PDF);
        if (target.isEmpty()) {
            return;
        }
        PlanExportFormat format = target.get().format();
        try {
            viewModel.exportAcceptedPlan(plan, target.get().file(), format);
        } catch (RuntimeException ex) {
            Alert alert = new Alert(AlertType.ERROR);
            alert.setTitle(Localization.lang("Export failed"));
            alert.setHeaderText(Localization.lang("%0 export failed: %1", format.name(), ex.getMessage()));
            alert.initOwner(owner);
            alert.showAndWait();
        }
    }
}
