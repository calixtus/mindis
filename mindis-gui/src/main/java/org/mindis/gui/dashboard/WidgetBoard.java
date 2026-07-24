package org.mindis.gui.dashboard;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.Region;
import javafx.scene.paint.Color;

/// A fixed-column grid board of dashboard widgets. Widgets are positioned by
/// grid coordinates (column, row) and span whole cells; the cell width is
/// derived from the board's own width, so cells widen and shrink as the window
/// is resized or the sidebar is collapsed/expanded - the column count stays
/// constant, the cells breathe.
///
/// <p>Widgets can be dragged (press-and-hold the header) to a new cell, resized
/// in whole-cell steps by the bottom-right grip, added via
/// {@link #placeNewWidget}, and removed via each widget's close button. The
/// invisible grid is only drawn - faintly - while a drag or resize is in
/// progress, as a placement guide. Every layout change notifies the supplied
/// callback so the arrangement can be persisted.
final class WidgetBoard extends Region {

    static final int COLUMNS = 12;
    private static final double ROW_HEIGHT = 96;
    private static final double GAP = 12;
    private static final int MIN_COL_SPAN = 2;
    private static final int MIN_ROW_SPAN = 1;

    private final Runnable onLayoutChanged;
    private final Canvas gridOverlay = new Canvas();
    private boolean showGrid;

    WidgetBoard(Runnable onLayoutChanged) {
        this.onLayoutChanged = onLayoutChanged;
        getStyleClass().add("dashboard-board");
        gridOverlay.setManaged(false);
        gridOverlay.setMouseTransparent(true);
        getChildren().add(gridOverlay);
    }

    /// Adds a widget at its stored placement (restoring a persisted layout);
    /// does not notify, since nothing changed from the persisted state.
    void restoreWidget(WidgetContainer widget) {
        wireInteractions(widget);
        getChildren().add(widget);
    }

    /// Adds a freshly-created widget: its own default cell when free, otherwise
    /// a full-width row below everything already placed. Notifies the callback.
    void placeNewWidget(WidgetContainer widget) {
        WidgetPlacement fallback = widget.type().defaultPlacement();
        if (overlapsExisting(fallback.col(), fallback.row(), fallback.colSpan(), fallback.rowSpan())) {
            widget.setGridBounds(0, bottomRow(), Math.min(COLUMNS, fallback.colSpan()), fallback.rowSpan());
        }
        wireInteractions(widget);
        getChildren().add(widget);
        requestLayout();
        onLayoutChanged.run();
    }

    private void removeWidget(WidgetContainer widget) {
        getChildren().remove(widget);
        requestLayout();
        onLayoutChanged.run();
    }

    List<WidgetPlacement> placements() {
        List<WidgetPlacement> result = new ArrayList<>();
        for (WidgetContainer widget : widgets()) {
            result.add(widget.placement());
        }
        return result;
    }

    Set<WidgetType> placedTypes() {
        Set<WidgetType> types = EnumSet.noneOf(WidgetType.class);
        for (WidgetContainer widget : widgets()) {
            types.add(widget.type());
        }
        return types;
    }

    private List<WidgetContainer> widgets() {
        List<WidgetContainer> result = new ArrayList<>();
        for (Node child : getChildren()) {
            if (child instanceof WidgetContainer widget) {
                result.add(widget);
            }
        }
        return result;
    }

    private void wireInteractions(WidgetContainer widget) {
        widget.closeButton().setOnAction(_ -> removeWidget(widget));
        installDrag(widget);
        installResize(widget);
    }

    private void installDrag(WidgetContainer widget) {
        double[] start = new double[2];
        widget.header().addEventHandler(MouseEvent.MOUSE_PRESSED, event -> {
            // Pressing the close button must not begin a drag.
            if (isWithin(widget.closeButton(), event.getTarget())) {
                return;
            }
            start[0] = event.getSceneX();
            start[1] = event.getSceneY();
            widget.toFront();
            setShowGrid(true);
            event.consume();
        });
        widget.header().addEventHandler(MouseEvent.MOUSE_DRAGGED, event -> {
            if (isWithin(widget.closeButton(), event.getTarget())) {
                return;
            }
            widget.setTranslateX(event.getSceneX() - start[0]);
            widget.setTranslateY(event.getSceneY() - start[1]);
            event.consume();
        });
        widget.header().addEventHandler(MouseEvent.MOUSE_RELEASED, event -> {
            if (widget.getTranslateX() == 0 && widget.getTranslateY() == 0) {
                setShowGrid(false);
                return;
            }
            commitDrag(widget);
            event.consume();
        });
    }

    private void commitDrag(WidgetContainer widget) {
        double step = cellWidth() + GAP;
        double rowStep = ROW_HEIGHT + GAP;
        Insets in = getInsets();
        double x = widget.getLayoutX() + widget.getTranslateX() - in.getLeft();
        double y = widget.getLayoutY() + widget.getTranslateY() - in.getTop();
        int col = clamp((int) Math.round(x / step), 0, COLUMNS - widget.colSpan());
        int row = Math.max(0, (int) Math.round(y / rowStep));
        widget.setTranslateX(0);
        widget.setTranslateY(0);
        widget.setGridBounds(col, row, widget.colSpan(), widget.rowSpan());
        setShowGrid(false);
        requestLayout();
        onLayoutChanged.run();
    }

    private void installResize(WidgetContainer widget) {
        int[] startSpan = new int[2];
        double[] startScene = new double[2];
        widget.resizeGrip().addEventHandler(MouseEvent.MOUSE_PRESSED, event -> {
            startSpan[0] = widget.colSpan();
            startSpan[1] = widget.rowSpan();
            startScene[0] = event.getSceneX();
            startScene[1] = event.getSceneY();
            setShowGrid(true);
            event.consume();
        });
        widget.resizeGrip().addEventHandler(MouseEvent.MOUSE_DRAGGED, event -> {
            double step = cellWidth() + GAP;
            if (step <= 0) {
                return;
            }
            int deltaCols = (int) Math.round((event.getSceneX() - startScene[0]) / step);
            int deltaRows = (int) Math.round((event.getSceneY() - startScene[1]) / (ROW_HEIGHT + GAP));
            int colSpan = clamp(startSpan[0] + deltaCols, MIN_COL_SPAN, COLUMNS - widget.col());
            int rowSpan = Math.max(MIN_ROW_SPAN, startSpan[1] + deltaRows);
            if (colSpan != widget.colSpan() || rowSpan != widget.rowSpan()) {
                widget.setGridBounds(widget.col(), widget.row(), colSpan, rowSpan);
                requestLayout();
            }
            event.consume();
        });
        widget.resizeGrip().addEventHandler(MouseEvent.MOUSE_RELEASED, event -> {
            setShowGrid(false);
            onLayoutChanged.run();
            event.consume();
        });
    }

    @Override
    protected void layoutChildren() {
        Insets in = getInsets();
        double cellW = cellWidth();
        double rowStep = ROW_HEIGHT + GAP;
        for (WidgetContainer widget : widgets()) {
            double x = in.getLeft() + widget.col() * (cellW + GAP);
            double y = in.getTop() + widget.row() * rowStep;
            double w = widget.colSpan() * cellW + (widget.colSpan() - 1) * GAP;
            double h = widget.rowSpan() * ROW_HEIGHT + (widget.rowSpan() - 1) * GAP;
            widget.resizeRelocate(x, y, w, h);
        }
        gridOverlay.resizeRelocate(0, 0, getWidth(), getHeight());
        drawGrid(cellW);
    }

    private void drawGrid(double cellW) {
        GraphicsContext g = gridOverlay.getGraphicsContext2D();
        g.clearRect(0, 0, gridOverlay.getWidth(), gridOverlay.getHeight());
        if (!showGrid || cellW <= 0) {
            return;
        }
        Insets in = getInsets();
        double height = gridOverlay.getHeight();
        g.setStroke(Color.gray(0.5, 0.25));
        g.setLineWidth(1);
        for (int c = 0; c <= COLUMNS; c++) {
            double x = Math.round(in.getLeft() + c * (cellW + GAP) - (c == 0 ? 0 : GAP / 2)) + 0.5;
            g.strokeLine(x, in.getTop(), x, height - in.getBottom());
        }
        double rowStep = ROW_HEIGHT + GAP;
        for (double y = in.getTop(); y <= height - in.getBottom(); y += rowStep) {
            double yy = Math.round(y - GAP / 2) + 0.5;
            g.strokeLine(in.getLeft(), yy, getWidth() - in.getRight(), yy);
        }
    }

    private double cellWidth() {
        Insets in = getInsets();
        double avail = getWidth() - in.getLeft() - in.getRight();
        return (avail - GAP * (COLUMNS - 1)) / COLUMNS;
    }

    private int bottomRow() {
        int bottom = 0;
        for (WidgetContainer widget : widgets()) {
            bottom = Math.max(bottom, widget.row() + widget.rowSpan());
        }
        return bottom;
    }

    private boolean overlapsExisting(int col, int row, int colSpan, int rowSpan) {
        for (WidgetContainer widget : widgets()) {
            boolean colsOverlap = col < widget.col() + widget.colSpan() && widget.col() < col + colSpan;
            boolean rowsOverlap = row < widget.row() + widget.rowSpan() && widget.row() < row + rowSpan;
            if (colsOverlap && rowsOverlap) {
                return true;
            }
        }
        return false;
    }

    private void setShowGrid(boolean value) {
        showGrid = value;
        requestLayout();
    }

    @Override
    protected double computePrefHeight(double width) {
        Insets in = getInsets();
        int rows = Math.max(1, bottomRow());
        return in.getTop() + in.getBottom() + rows * ROW_HEIGHT + (rows - 1) * GAP;
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private static boolean isWithin(Node ancestor, Object target) {
        Node node = target instanceof Node n ? n : null;
        while (node != null) {
            if (node == ancestor) {
                return true;
            }
            node = node.getParent();
        }
        return false;
    }
}
