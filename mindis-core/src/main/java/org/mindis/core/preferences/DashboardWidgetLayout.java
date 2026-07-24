package org.mindis.core.preferences;

/// One placed dashboard widget's persisted grid geometry (PLAN.md dashboard):
/// which widget ({@code widgetId}, a stable string the GUI maps back to its
/// widget type) sits where on the invisible column grid and how many cells it
/// spans. Held as a list in {@link MinDisPreferences}; core stays free of the
/// GUI widget enum, so the id is just a string here.
public record DashboardWidgetLayout(String widgetId, int col, int row, int colSpan, int rowSpan) {
}
