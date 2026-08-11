package org.mindis.core.preferences;

import org.jspecify.annotations.Nullable;

/// One placed dashboard widget's persisted state (PLAN.md dashboard): which
/// widget (`widgetId`, a stable string the GUI maps back to its widget
/// type) sits where on the invisible column grid, how many cells it spans, and
/// which view mode (list, bar chart, ...) it renders in. Held as a list in
/// [MinDisPreferences]; core stays free of the GUI widget enums, so both
/// ids are just strings here.
///
/// <p>`viewMode` is `null` for a layout written before widgets could
/// switch their rendering, and for a widget left on its default mode; the GUI
/// then falls back to the widget type's default.
public record DashboardWidgetLayout(String widgetId, int col, int row, int colSpan, int rowSpan,
                                    @Nullable String viewMode) {
}
