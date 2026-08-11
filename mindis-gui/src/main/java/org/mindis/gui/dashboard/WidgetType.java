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

    SUMMARY("summary", "Summary", 0, 0, 12, 1, WidgetViewMode.TILES),
    NEXT_SERVICES("next-services", "Next services", 0, 1, 6, 3, WidgetViewMode.LIST),
    SERVER_LOAD("server-load", "Assignments per server", 6, 1, 6, 3, WidgetViewMode.LIST);

    private final String id;
    private final String titleKey;
    private final int defaultCol;
    private final int defaultRow;
    private final int defaultColSpan;
    private final int defaultRowSpan;
    private final List<WidgetViewMode> modes;

    WidgetType(String id, String titleKey, int defaultCol, int defaultRow, int defaultColSpan, int defaultRowSpan,
               WidgetViewMode... modes) {
        this.id = id;
        this.titleKey = titleKey;
        this.defaultCol = defaultCol;
        this.defaultRow = defaultRow;
        this.defaultColSpan = defaultColSpan;
        this.defaultRowSpan = defaultRowSpan;
        this.modes = List.of(modes);
    }

    public String id() {
        return id;
    }

    /// Localized widget title; looked up lazily so it tracks the current language.
    public String title() {
        return Localization.lang(titleKey);
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
