package org.mindis.gui.dashboard;

/// A widget's position on the invisible column grid: which [WidgetType]
/// sits at `(col, row)` and how many grid cells it spans. GUI-side mirror
/// of [org.mindis.core.preferences.DashboardWidgetLayout]; the view model
/// maps between the two.
public record WidgetPlacement(WidgetType type, int col, int row, int colSpan, int rowSpan) {
}
