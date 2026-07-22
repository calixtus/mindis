package org.mindis.gui.planning;

import java.time.Instant;
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
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.stage.Window;

import com.dlsc.gemsfx.CalendarPicker;

import org.mindis.core.export.PlanExportFormat;
import org.mindis.core.l10n.EnumDisplay;
import org.mindis.core.l10n.Localization;
import org.mindis.core.model.ArchivedService;

import org.mindis.gui.util.CalendarPickers;

/// Browser over {@link PlanningViewModel#listArchived()}: the frozen services
/// the planner has archived. Archived services aren't editable, only viewable,
/// exportable and deletable - each is a self-contained snapshot (its own
/// role/server names), so it still exports faithfully after the roster changes.
/// Also hosts the "Archive up to..." action that freezes past services.
public final class ArchivedPlansDialog {

    private static final DateTimeFormatter DATE_TIME_FORMAT =
            DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm");
    private static final DateTimeFormatter ARCHIVED_AT_FORMAT =
            DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm").withZone(ZoneId.systemDefault());

    private ArchivedPlansDialog() {
    }

    /// Shows the dialog. {@code archiveAction} performs the freeze at a chosen
    /// cutoff and returns whether anything was archived - it lives on the
    /// Services module because archiving also drops the frozen services from
    /// the live list.
    public static void show(PlanningViewModel viewModel, Window owner,
                            java.util.function.Predicate<java.time.LocalDate> archiveAction) {
        TableView<ArchivedService> table = new TableView<>();
        table.getItems().setAll(viewModel.listArchived());
        table.setPrefWidth(560);
        table.setPrefHeight(320);

        TableColumn<ArchivedService, String> whenColumn = new TableColumn<>(Localization.lang("Services"));
        whenColumn.setCellValueFactory(data -> new SimpleStringProperty(
                data.getValue().dateTime().format(DATE_TIME_FORMAT) + "  "
                        + EnumDisplay.of(data.getValue().type()) + "  " + data.getValue().location()));
        whenColumn.setPrefWidth(280);

        TableColumn<ArchivedService, String> assignedColumn = new TableColumn<>(Localization.lang("Assigned"));
        assignedColumn.setCellValueFactory(data -> {
            long assigned = data.getValue().slots().stream()
                    .filter(slot -> slot.serverName() != null)
                    .count();
            return new SimpleStringProperty(assigned + "/" + data.getValue().slots().size());
        });
        assignedColumn.setPrefWidth(90);

        TableColumn<ArchivedService, String> archivedAtColumn = new TableColumn<>(Localization.lang("Saved"));
        archivedAtColumn.setCellValueFactory(data -> {
            Instant archivedAt = data.getValue().archivedAt();
            return new SimpleStringProperty(archivedAt == null ? "-" : ARCHIVED_AT_FORMAT.format(archivedAt));
        });
        archivedAtColumn.setPrefWidth(160);

        table.getColumns().setAll(List.of(whenColumn, assignedColumn, archivedAtColumn));
        table.setPlaceholder(new Label(Localization.lang("No archived plans yet")));

        Button exportButton = new Button(Localization.lang("Export all"));
        exportButton.disableProperty().bind(javafx.beans.binding.Bindings.isEmpty(table.getItems()));
        exportButton.setOnAction(e -> exportArchived(viewModel, List.copyOf(table.getItems()), owner));
        Button deleteButton = new Button(Localization.lang("Delete selected"));
        deleteButton.disableProperty().bind(table.getSelectionModel().selectedItemProperty().isNull());
        deleteButton.setOnAction(e -> {
            ArchivedService selected = table.getSelectionModel().getSelectedItem();
            if (selected != null && confirmDelete(selected, owner)) {
                viewModel.deleteArchived(selected);
                table.getItems().setAll(viewModel.listArchived());
            }
        });

        CalendarPicker cutoffPicker = CalendarPickers.create();
        cutoffPicker.setPromptText(Localization.lang("Cutoff date"));
        Button archiveButton = new Button(Localization.lang("Archive up to..."));
        archiveButton.disableProperty().bind(cutoffPicker.valueProperty().isNull());
        archiveButton.setOnAction(e -> {
            java.time.LocalDate cutoff = cutoffPicker.getValue();
            if (cutoff == null) {
                return;
            }
            if (confirmArchive(cutoff, owner)) {
                if (archiveAction.test(cutoff)) {
                    table.getItems().setAll(viewModel.listArchived());
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
        HBox.setHgrow(spacer, Priority.ALWAYS);
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

    private static boolean confirmArchive(java.time.LocalDate cutoff, Window owner) {
        Alert confirm = new Alert(AlertType.CONFIRMATION);
        confirm.setTitle(Localization.lang("Archive up to..."));
        confirm.setHeaderText(Localization.lang(
                "Freeze every assignment up to %0? The frozen plan can't be edited afterward; save the document to keep it.",
                cutoff.toString()));
        confirm.initOwner(owner);
        Optional<ButtonType> result = confirm.showAndWait();
        return result.isPresent() && result.get().getButtonData() == ButtonData.OK_DONE;
    }

    private static boolean confirmDelete(ArchivedService service, Window owner) {
        Alert confirm = new Alert(AlertType.CONFIRMATION);
        confirm.setTitle(Localization.lang("Delete selected"));
        confirm.setHeaderText(Localization.lang("Permanently delete the archived plan %0?",
                service.dateTime().format(DATE_TIME_FORMAT)));
        confirm.initOwner(owner);
        Optional<ButtonType> result = confirm.showAndWait();
        return result.isPresent() && result.get().getButtonData() == ButtonData.OK_DONE;
    }

    private static void exportArchived(PlanningViewModel viewModel, List<ArchivedService> services, Window owner) {
        Optional<PlanExportChooser.Target> target = PlanExportChooser.show(
                owner, viewModel, "MinDis-archived", PlanExportFormat.PDF);
        if (target.isEmpty()) {
            return;
        }
        PlanExportFormat format = target.get().format();
        try {
            viewModel.exportArchived(services, target.get().file(), format);
        } catch (RuntimeException ex) {
            Alert alert = new Alert(AlertType.ERROR);
            alert.setTitle(Localization.lang("Export failed"));
            alert.setHeaderText(Localization.lang("%0 export failed: %1", format.name(), ex.getMessage()));
            alert.initOwner(owner);
            alert.showAndWait();
        }
    }
}
