package org.mindis.gui.dashboard;

import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.layout.FlowPane;

/// The summary widget's row of key figures, laid out to survive any widget
/// size: the tiles wrap onto further lines when the card gets narrow, and when
/// even that does not fit, the whole row is set in a smaller font until it does.
///
/// The font is scaled in `em`, so it shrinks relative to whatever font
/// size the user has configured rather than jumping to a fixed one, and it is
/// only ever made smaller - blowing the figures up in a tall card would look
/// like a different design.
final class KeyFigures extends FlowPane {

    /// Below this the figures stop being readable; the widget is then simply
    /// too small for all of them, and the card's clip cuts the rest.
    private static final double MIN_SCALE = 0.55;
    private static final double SCALE_STEP = 0.05;

    private double appliedScale = 1;

    KeyFigures(Node... tiles) {
        super(12, 8, tiles);
        getStyleClass().add("dashboard-tiles");
        setAlignment(Pos.CENTER_LEFT);
        // Fill the card rather than sitting at its own preferred size: the
        // width is what FlowPane wraps against, and the height is what the
        // font has to fit into.
        setMinSize(0, 0);
        setMaxSize(Double.MAX_VALUE, Double.MAX_VALUE);
    }

    @Override
    protected void layoutChildren() {
        fitFont();
        super.layoutChildren();
    }

    /// Largest scale at which the wrapped rows still fit the current height.
    private void fitFont() {
        double width = getWidth();
        double height = getHeight();
        if (width <= 0 || height <= 0) {
            return;
        }
        for (double scale = 1; scale >= MIN_SCALE; scale -= SCALE_STEP) {
            applyScale(scale);
            if (computePrefHeight(width) <= height) {
                return;
            }
        }
    }

    private void applyScale(double scale) {
        if (scale == appliedScale) {
            return;
        }
        appliedScale = scale;
        setStyle(scale == 1 ? "" : "-fx-font-size: " + scale + "em;");
        // The children's preferred sizes are read right after this, and they
        // follow their font - which only exists once the new style is applied.
        applyCss();
    }
}
