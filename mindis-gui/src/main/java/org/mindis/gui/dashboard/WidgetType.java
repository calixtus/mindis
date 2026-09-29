package org.mindis.gui.dashboard;

import java.util.List;
import java.util.Optional;

import org.jspecify.annotations.Nullable;

import org.mindis.core.l10n.Localization;

/// The kinds of dashboard widget. Each type is unique on the board (added once,
/// removed via the widget's close button) and carries a stable [#id()]
/// used to persist the layout - decoupled from the enum name so a rename does
/// not invalidate saved layouts - its default grid placement for a fresh board,
/// and the [WidgetViewMode]s it can render its data in (the first one is
/// the default).
public enum WidgetType {

    SUMMARY("summary", 0, 0, 12, 1, WidgetViewMode.TILES, WidgetViewMode.DONUT),
    NEXT_SERVICES("next-services", 0, 1, 6, 3,
            WidgetViewMode.LIST, WidgetViewMode.STACKED_BAR),
    SERVER_LOAD("server-load", 6, 1, 6, 3,
            WidgetViewMode.LIST, WidgetViewMode.BAR, WidgetViewMode.PIE),
    ROLES("roles", 0, 4, 6, 3,
            WidgetViewMode.BAR, WidgetViewMode.PIE, WidgetViewMode.LIST),
    SERVICE_TYPE_MIX("service-type-mix", 6, 4, 6, 3,
            WidgetViewMode.PIE, WidgetViewMode.BAR, WidgetViewMode.LIST),
    COVERAGE_TREND("coverage-trend", 0, 7, 12, 3,
            WidgetViewMode.STACKED_BAR, WidgetViewMode.LINE, WidgetViewMode.AREA, WidgetViewMode.LIST),
    PEOPLE_AHEAD("absences-ahead", 0, 10, 6, 3,
            WidgetViewMode.LIST, WidgetViewMode.BAR),
    PROBLEMS("problems", 6, 10, 6, 3,
            WidgetViewMode.LIST, WidgetViewMode.BAR),
    ARCHIVE_HISTORY("archive-history", 0, 13, 12, 3,
            WidgetViewMode.LINE, WidgetViewMode.BAR, WidgetViewMode.LIST);

    private final String id;
    private final int defaultCol;
    private final int defaultRow;
    private final int defaultColSpan;
    private final int defaultRowSpan;
    private final List<WidgetViewMode> modes;

    WidgetType(String id, int defaultCol, int defaultRow, int defaultColSpan, int defaultRowSpan,
               WidgetViewMode... modes) {
        this.id = id;
        this.defaultCol = defaultCol;
        this.defaultRow = defaultRow;
        this.defaultColSpan = defaultColSpan;
        this.defaultRowSpan = defaultRowSpan;
        this.modes = List.of(modes);
    }

    public String id() {
        return id;
    }

    /// Localized widget title. Looked up per call (not stored) so it tracks the current
    /// language, and written as a literal inside lang(...) so LocalizationConsistencyTest
    /// can find the key.
    public String title() {
        return switch (this) {
            case SUMMARY -> Localization.lang("Summary");
            case NEXT_SERVICES -> Localization.lang("Next services");
            case SERVER_LOAD -> Localization.lang("Assignments per server");
            case ROLES -> Localization.lang("Roles");
            case SERVICE_TYPE_MIX -> Localization.lang("Service types");
            case COVERAGE_TREND -> Localization.lang("Coverage by week");
            case PEOPLE_AHEAD -> Localization.lang("Away and birthdays");
            case PROBLEMS -> Localization.lang("Problems");
            case ARCHIVE_HISTORY -> Localization.lang("Archived services");
        };
    }

    /// This type's placement on a fresh, never-arranged board.
    public WidgetPlacement defaultPlacement() {
        return new WidgetPlacement(this, defaultCol, defaultRow, defaultColSpan, defaultRowSpan, defaultMode());
    }

    /// Every way this widget can render its data, in menu order.
    public List<WidgetViewMode> modes() {
        return modes;
    }

    public WidgetViewMode defaultMode() {
        return modes.getFirst();
    }

    /// `mode` if this type can render it, its default mode otherwise -
    /// so a layout saved by a version that offered more modes still loads.
    public WidgetViewMode resolveMode(@Nullable WidgetViewMode mode) {
        return mode != null && modes.contains(mode) ? mode : defaultMode();
    }

    public static Optional<WidgetType> fromId(String id) {
        for (WidgetType type : values()) {
            if (type.id.equals(id)) {
                return Optional.of(type);
            }
        }
        return Optional.empty();
    }
}
