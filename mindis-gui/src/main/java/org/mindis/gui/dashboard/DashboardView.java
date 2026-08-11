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
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;

import org.kordamp.ikonli.javafx.FontIcon;

import org.mindis.core.l10n.Localization;
import org.mindis.gui.util.DateTimes;

/// Dashboard board of widgets - key figures, upcoming services and per-server
/// load - each a draggable, resizable card on an invisible column grid. Builds
/// the board from the persisted layout, fills each widget from a
/// [DashboardViewModel.Snapshot] in the widget's own [WidgetViewMode]
/// (a list or one of the diagram kinds), and offers an "add widget" menu of the
/// types not yet on the board (each type is unique).
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
            board.restoreWidget(newWidget(placement));
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
        board.placeNewWidget(newWidget(type.defaultPlacement()));
    }

    /// A container for `placement`, filled and wired so that picking
    /// another view mode refills just this widget and persists the choice.
    private WidgetContainer newWidget(WidgetPlacement placement) {
        WidgetContainer widget = new WidgetContainer(placement, changed -> {
            fillContent(changed);
            persistLayout();
        });
        fillContent(widget);
        return widget;
    }

    private void fillContent(WidgetContainer widget) {
        widget.content().getChildren().setAll(buildContent(widget.type(), widget.mode()));
    }

    private void persistLayout() {
        viewModel.saveLayout(board.placements());
        addWidgetButton.setDisable(board.placedTypes().size() == WidgetType.values().length);
    }

    private Node buildContent(WidgetType type, WidgetViewMode mode) {
        return switch (type) {
            case SUMMARY -> summaryContent(mode);
            case NEXT_SERVICES -> upcomingContent(mode);
            case SERVER_LOAD -> serverLoadContent(mode);
        };
    }

    private Node summaryContent(WidgetViewMode mode) {
        if (snapshot.isEmpty()) {
            Label label = new Label(Localization.lang("No plan saved yet"));
            label.getStyleClass().add("dashboard-summary");
            label.setWrapText(true);
            return label;
        }
        if (mode == WidgetViewMode.DONUT) {
            return Charts.donut(
                    List.of(new Charts.Slice(Localization.lang("Assigned"), snapshot.assignedSlots()),
                            new Charts.Slice(Localization.lang("Open"), snapshot.unassignedSlots())),
                    snapshot.coveragePercent() + "%", Localization.lang("Slots assigned"));
        }
        FlowPane tiles = new FlowPane(12, 12,
                tile(String.valueOf(snapshot.upcomingServiceCount()), Localization.lang("Upcoming services"), ""),
                tile(String.valueOf(snapshot.unassignedSlots()), Localization.lang("Open slots"),
                        snapshot.unassignedSlots() == 0 ? "dashboard-tile-good" : "dashboard-tile-warn"),
                tile(snapshot.coveragePercent() + "%", Localization.lang("Slots assigned"), ""),
                tile(String.valueOf(snapshot.activeServers()), Localization.lang("Active servers"), ""),
                tile(String.valueOf(snapshot.roles()), Localization.lang("Roles"), ""));
        tiles.getStyleClass().add("dashboard-tiles");
        return tiles;
    }

    /// One key figure: the number big, its meaning small underneath.
    private static Node tile(String value, String caption, String extraStyleClass) {
        Label number = new Label(value);
        number.getStyleClass().add("dashboard-tile-value");
        Label label = new Label(caption);
        label.getStyleClass().add("dashboard-tile-caption");
        VBox tile = new VBox(number, label);
        tile.getStyleClass().add("dashboard-tile");
        if (!extraStyleClass.isBlank()) {
            tile.getStyleClass().add(extraStyleClass);
        }
        return tile;
    }

    private Node upcomingContent(WidgetViewMode mode) {
        List<DashboardViewModel.UpcomingService> upcoming = snapshot.upcomingServices();
        if (mode == WidgetViewMode.STACKED_BAR) {
            List<String> labels = upcoming.stream()
                    .map(service -> DateTimes.shortDate(service.dateTime().toLocalDate()))
                    .toList();
            return Charts.stackedBar(labels,
                    List.of(new Charts.Series(Localization.lang("Assigned"), upcoming.stream()
                                    .map(service -> (double) service.assignedSlots()).toList()),
                            new Charts.Series(Localization.lang("Open"), upcoming.stream()
                                    .map(service -> (double) (service.totalSlots() - service.assignedSlots()))
                                    .toList())),
                    Localization.lang("Slots"));
        }
        ListView<DashboardViewModel.UpcomingService> list = new ListView<>();
        list.setItems(FXCollections.observableArrayList(upcoming));
        list.setCellFactory(_ -> new UpcomingServiceCell());
        list.setPlaceholder(new Label(Localization.lang("Nothing to show")));
        return list;
    }

    private Node serverLoadContent(WidgetViewMode mode) {
        List<DashboardViewModel.ServerLoad> load = snapshot.serverLoad();
        return switch (mode) {
            case BAR -> Charts.horizontalBar(slices(load), Localization.lang("Assignments"));
            // A pie of "who did how much" only reads with a handful of slices,
            // and a server with no assignment has no slice at all - so the tail
            // is bucketed rather than drawn.
            case PIE -> Charts.pie(Charts.topWithOthers(
                    slices(load).stream().filter(slice -> slice.value() > 0).toList(), Charts.MAX_PIE_SLICES));
            default -> listView(load.stream()
                    .map(entry -> entry.serverName() + ": " + entry.assignments())
                    .toList());
        };
    }

    private static List<Charts.Slice> slices(List<DashboardViewModel.ServerLoad> load) {
        return load.stream().map(entry -> new Charts.Slice(entry.serverName(), entry.assignments())).toList();
    }

    private static ListView<String> listView(List<String> items) {
        ListView<String> list = new ListView<>();
        list.setItems(FXCollections.observableArrayList(items));
        return list;
    }
}
