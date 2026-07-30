package org.mindis.gui.dashboard;

import io.avaje.inject.Prototype;

import java.util.List;

import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.control.MenuButton;
import javafx.scene.control.MenuItem;
import javafx.scene.control.ScrollPane;

/// Dashboard board of widgets - upcoming services, unassigned-slot count and
/// per-server load - each a draggable, resizable card on an invisible column
/// grid. The controller builds the board from the persisted layout, fills each
/// widget with content from the [DashboardViewModel] snapshot, and offers
/// an "add widget" menu of the types not yet on the board (each type is unique).
@Prototype
public class DashboardController {

    private final DashboardViewModel viewModel;

    @FXML
    private ScrollPane boardScroll;
    @FXML
    private MenuButton addWidgetButton;

    private WidgetBoard board;
    private DashboardViewModel.Snapshot snapshot;

    // NullAway: @FXML fields are populated by FXMLLoader reflection right
    // after this constructor runs, before initialize() is called; board and
    // snapshot are assigned in initialize() before any handler can run.
    @SuppressWarnings("NullAway.Init")
    public DashboardController(DashboardViewModel viewModel) {
        this.viewModel = viewModel;
    }

    @FXML
    private void initialize() {
        snapshot = viewModel.loadSnapshot();
        board = new WidgetBoard(this::persistLayout);
        for (WidgetPlacement placement : viewModel.loadLayout()) {
            WidgetContainer widget = new WidgetContainer(placement);
            widget.content().getChildren().add(buildContent(placement.type()));
            board.restoreWidget(widget);
        }
        boardScroll.setContent(board);
        boardScroll.setFitToWidth(true);

        addWidgetButton.setOnShowing(_ -> rebuildAddMenu());
        rebuildAddMenu();
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
                Label label = new Label(snapshot.summaryText());
                label.getStyleClass().add("dashboard-summary");
                label.setWrapText(true);
                yield label;
            }
            case NEXT_SERVICES -> listView(snapshot.upcomingServices());
            case SERVER_LOAD -> listView(snapshot.serverLoad());
        };
    }

    private static ListView<String> listView(List<String> items) {
        ListView<String> list = new ListView<>();
        list.setItems(FXCollections.observableArrayList(items));
        return list;
    }
}
