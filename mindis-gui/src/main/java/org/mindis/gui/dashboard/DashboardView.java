package org.mindis.gui.dashboard;

import java.util.List;

import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.control.MenuButton;
import javafx.scene.control.MenuItem;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.StackPane;

import org.kordamp.ikonli.javafx.FontIcon;

import org.mindis.core.l10n.EnumDisplay;
import org.mindis.core.l10n.Localization;
import org.mindis.gui.util.DateTimes;

/// Dashboard board of widgets - upcoming services, unassigned-slot count and
/// per-server load - each a draggable, resizable card on an invisible column
/// grid. Builds the board from the persisted layout, fills each widget from a
/// [DashboardViewModel.Snapshot], and offers an "add widget" menu of the types
/// not yet on the board (each type is unique).
///
/// The board fills the pane; the add button floats over it, pinned top-right,
/// overlapping the widgets rather than sitting in its own toolbar strip.
///
/// The snapshot is read once, at construction. That is enough because
/// `DashboardModule` builds a fresh view on every activation, so switching to
/// the dashboard always shows current numbers.
public final class DashboardView extends StackPane {

    private final DashboardViewModel viewModel;
    private final DashboardViewModel.Snapshot snapshot;
    private final WidgetBoard board;
    private final MenuButton addWidgetButton = new MenuButton(Localization.lang("Add widget"));

    public DashboardView(DashboardViewModel viewModel) {
        this.viewModel = viewModel;
        this.snapshot = viewModel.loadSnapshot();
        this.board = new WidgetBoard(this::persistLayout);

        getStyleClass().add("dashboard");
        getStylesheets().add(DashboardView.class.getResource("dashboard.css").toExternalForm());

        for (WidgetPlacement placement : viewModel.loadLayout()) {
            WidgetContainer widget = new WidgetContainer(placement);
            widget.content().getChildren().add(buildContent(placement.type()));
            board.restoreWidget(widget);
        }

        ScrollPane boardScroll = new ScrollPane(board);
        boardScroll.setFitToWidth(true);
        boardScroll.getStyleClass().add("dashboard-scroll");
        boardScroll.setPadding(new Insets(16));

        addWidgetButton.setGraphic(new FontIcon("mdi2p-plus"));
        addWidgetButton.getStyleClass().add("dashboard-add-button");
        addWidgetButton.setOnShowing(_ -> rebuildAddMenu());
        StackPane.setAlignment(addWidgetButton, Pos.TOP_RIGHT);
        StackPane.setMargin(addWidgetButton, new Insets(12, 24, 0, 0));
        rebuildAddMenu();

        getChildren().addAll(boardScroll, addWidgetButton);
    }

    /// Populates the add menu with the widget types not currently on the board;
    /// disables the button when every type is already placed.
    private void rebuildAddMenu() {
        addWidgetButton.getItems().clear();
        for (WidgetType type : WidgetType.values()) {
            if (board.placedTypes().contains(type)) {
                continue;
            }
            MenuItem item = new MenuItem(type.title());
            item.setOnAction(_ -> addWidget(type));
            addWidgetButton.getItems().add(item);
        }
        addWidgetButton.setDisable(addWidgetButton.getItems().isEmpty());
    }

    private void addWidget(WidgetType type) {
        WidgetContainer widget = new WidgetContainer(type.defaultPlacement());
        widget.content().getChildren().add(buildContent(type));
        board.placeNewWidget(widget);
    }

    private void persistLayout() {
        viewModel.saveLayout(board.placements());
        addWidgetButton.setDisable(board.placedTypes().size() == WidgetType.values().length);
    }

    private Node buildContent(WidgetType type) {
        return switch (type) {
            case SUMMARY -> {
                Label label = new Label(summaryText());
                label.getStyleClass().add("dashboard-summary");
                label.setWrapText(true);
                yield label;
            }
            case NEXT_SERVICES -> listView(snapshot.upcomingServices().stream()
                    .map(DashboardView::describe)
                    .toList());
            case SERVER_LOAD -> listView(snapshot.serverLoad().stream()
                    .map(load -> load.serverName() + ": " + load.assignments())
                    .toList());
        };
    }

    private String summaryText() {
        return snapshot.isEmpty()
                ? Localization.lang("No plan saved yet")
                : Localization.lang("Unassigned slots") + ": " + snapshot.unassignedSlots();
    }

    /// One "next services" row: when, what, where, and how full it is.
    private static String describe(DashboardViewModel.UpcomingService service) {
        String base = DateTimes.dateTime(service.dateTime()) + "  "
                + EnumDisplay.of(service.type())
                + (service.location().isBlank() ? "" : "  " + service.location());
        return service.totalSlots() == 0
                ? base
                : base + "  (" + service.assignedSlots() + "/" + service.totalSlots() + ")";
    }

    private static ListView<String> listView(List<String> items) {
        ListView<String> list = new ListView<>();
        list.setItems(FXCollections.observableArrayList(items));
        return list;
    }
}
