package org.mindis.gui.dashboard;

import javafx.scene.Cursor;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;

import org.kordamp.ikonli.javafx.FontIcon;

/// One widget on the dashboard board: a titled card with a drag handle (the
/// header), a close button, a body holding the widget content, and a
/// bottom-right resize grip. It only builds and exposes these parts; all drag,
/// resize, add and remove behaviour lives in {@link WidgetBoard}, which sizes
/// and positions the container on the grid. The current grid geometry is held
/// here so the board can read and update it in place.
final class WidgetContainer extends StackPane {

    private final WidgetType type;
    private final HBox header;
    private final Button closeButton;
    private final StackPane content = new StackPane();
    private final Region resizeGrip;

    private int col;
    private int row;
    private int colSpan;
    private int rowSpan;

    WidgetContainer(WidgetPlacement placement) {
        this.type = placement.type();
        this.col = placement.col();
        this.row = placement.row();
        this.colSpan = placement.colSpan();
        this.rowSpan = placement.rowSpan();

        getStyleClass().add("dashboard-widget");

        Label title = new Label(type.title());
        title.getStyleClass().add("dashboard-widget-title");
        HBox.setHgrow(title, Priority.ALWAYS);
        title.setMaxWidth(Double.MAX_VALUE);

        closeButton = new Button();
        closeButton.setGraphic(new FontIcon("mdi2c-close"));
        closeButton.getStyleClass().add("dashboard-widget-close");

        header = new HBox(title, closeButton);
        header.getStyleClass().add("dashboard-widget-header");
        header.setCursor(Cursor.MOVE);

        content.getStyleClass().add("dashboard-widget-content");
        VBox.setVgrow(content, Priority.ALWAYS);

        VBox body = new VBox(header, content);
        body.getStyleClass().add("dashboard-widget-body");

        resizeGrip = new Region();
        resizeGrip.getStyleClass().add("dashboard-widget-resize-grip");
        resizeGrip.setCursor(Cursor.SE_RESIZE);
        resizeGrip.setPrefSize(16, 16);
        resizeGrip.setMaxSize(16, 16);
        StackPane.setAlignment(resizeGrip, javafx.geometry.Pos.BOTTOM_RIGHT);

        getChildren().addAll(body, resizeGrip);
    }

    WidgetType type() {
        return type;
    }

    /// The drag handle: pressing and holding here moves the widget.
    HBox header() {
        return header;
    }

    Button closeButton() {
        return closeButton;
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
        return new WidgetPlacement(type, col, row, colSpan, rowSpan);
    }
}
