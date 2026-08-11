package org.mindis.gui.dashboard;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import javafx.scene.Cursor;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.MenuButton;
import javafx.scene.control.MenuItem;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.shape.Rectangle;

import org.jspecify.annotations.Nullable;

import org.kordamp.ikonli.javafx.FontIcon;

/// One widget on the dashboard board: a titled card with a drag handle (the
/// header), a view-mode menu, a close button, a body holding the widget content,
/// and a bottom-right resize grip. It only builds and exposes these parts; all
/// drag, resize, add and remove behaviour lives in [WidgetBoard], which
/// sizes and positions the container on the grid. The current grid geometry and
/// view mode are held here so the board can read and update them in place.
///
/// The mode menu is built only for a [WidgetType] that offers more than
/// one [WidgetViewMode] - a widget with a single rendering shows no
/// chooser at all.
final class WidgetContainer extends StackPane {

    private final WidgetType type;
    private final HBox header;
    private final Button closeButton;
    private final @Nullable MenuButton modeButton;
    private final StackPane content = new StackPane();
    private final Region resizeGrip;

    private int col;
    private int row;
    private int colSpan;
    private int rowSpan;
    private WidgetViewMode mode;

    /// @param onModeChanged called with this container after it has adopted a
    ///        newly picked mode, so the view can refill the content and persist
    ///        the layout
    WidgetContainer(WidgetPlacement placement, Consumer<WidgetContainer> onModeChanged) {
        this.type = placement.type();
        this.col = placement.col();
        this.row = placement.row();
        this.colSpan = placement.colSpan();
        this.rowSpan = placement.rowSpan();
        this.mode = placement.mode();

        getStyleClass().add("dashboard-widget");
        // The board sizes the card from the grid, and the grid is the authority
        // on how small a card may get.
        setMinSize(0, 0);

        Label title = new Label(type.title());
        title.getStyleClass().add("dashboard-widget-title");
        HBox.setHgrow(title, Priority.ALWAYS);
        title.setMaxWidth(Double.MAX_VALUE);

        closeButton = new Button();
        closeButton.setGraphic(new FontIcon("mdi2c-close"));
        closeButton.getStyleClass().add("dashboard-widget-close");

        modeButton = type.modes().size() > 1 ? buildModeButton(onModeChanged) : null;

        header = modeButton == null
                ? new HBox(title, closeButton)
                : new HBox(title, modeButton, closeButton);
        header.getStyleClass().add("dashboard-widget-header");
        header.setCursor(Cursor.MOVE);

        content.getStyleClass().add("dashboard-widget-content");
        VBox.setVgrow(content, Priority.ALWAYS);
        // Nothing a widget shows may set a floor for the card: a StackPane
        // cannot resize a child below the child's own minimum, so content that
        // insists on a size (a row of key figures, a chart) would otherwise
        // push the body out of the card and over the widget below it.
        content.setMinSize(0, 0);
        // Content that cannot shrink far enough (a chart at its minimum, a row
        // of key figures) must stop at the card's edge instead of drawing over
        // the widget below it - JavaFX panes do not clip their children.
        Rectangle contentClip = new Rectangle();
        contentClip.widthProperty().bind(content.widthProperty());
        contentClip.heightProperty().bind(content.heightProperty());
        content.setClip(contentClip);

        VBox body = new VBox(header, content);
        body.getStyleClass().add("dashboard-widget-body");
        body.setMinSize(0, 0);

        resizeGrip = new Region();
        resizeGrip.getStyleClass().add("dashboard-widget-resize-grip");
        resizeGrip.setCursor(Cursor.SE_RESIZE);
        resizeGrip.setPrefSize(16, 16);
        resizeGrip.setMaxSize(16, 16);
        StackPane.setAlignment(resizeGrip, javafx.geometry.Pos.BOTTOM_RIGHT);

        getChildren().addAll(body, resizeGrip);
    }

    private MenuButton buildModeButton(Consumer<WidgetContainer> onModeChanged) {
        MenuButton button = new MenuButton();
        button.getStyleClass().add("dashboard-widget-mode");
        button.setGraphic(new FontIcon(mode.iconCode()));
        for (WidgetViewMode candidate : type.modes()) {
            MenuItem item = new MenuItem(candidate.displayName(), new FontIcon(candidate.iconCode()));
            item.setOnAction(_ -> {
                if (candidate == mode) {
                    return;
                }
                mode = candidate;
                button.setGraphic(new FontIcon(candidate.iconCode()));
                onModeChanged.accept(this);
            });
            button.getItems().add(item);
        }
        return button;
    }

    WidgetType type() {
        return type;
    }

    WidgetViewMode mode() {
        return mode;
    }

    /// The drag handle: pressing and holding here moves the widget.
    HBox header() {
        return header;
    }

    Button closeButton() {
        return closeButton;
    }

    /// The header's own controls - a press on any of them must operate the
    /// control rather than start a drag.
    List<Node> headerControls() {
        List<Node> controls = new ArrayList<>();
        controls.add(closeButton);
        if (modeButton != null) {
            controls.add(modeButton);
        }
        return controls;
    }

    Region resizeGrip() {
        return resizeGrip;
    }

    /// Where widget content is placed by the controller.
    StackPane content() {
        return content;
    }

    int col() {
        return col;
    }

    int row() {
        return row;
    }

    int colSpan() {
        return colSpan;
    }

    int rowSpan() {
        return rowSpan;
    }

    void setGridBounds(int col, int row, int colSpan, int rowSpan) {
        this.col = col;
        this.row = row;
        this.colSpan = colSpan;
        this.rowSpan = rowSpan;
    }

    WidgetPlacement placement() {
        return new WidgetPlacement(type, col, row, colSpan, rowSpan, mode);
    }
}
