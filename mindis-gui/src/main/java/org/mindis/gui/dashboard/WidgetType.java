package org.mindis.gui.dashboard;

import java.util.Optional;

import org.mindis.core.l10n.Localization;

/// The kinds of dashboard widget. Each type is unique on the board (added once,
/// removed via the widget's close button) and carries a stable {@link #id()}
/// used to persist the layout - decoupled from the enum name so a rename does
/// not invalidate saved layouts - plus its default grid placement for a fresh
/// board.
public enum WidgetType {

    SUMMARY("summary", "Summary", 0, 0, 12, 1),
    NEXT_SERVICES("next-services", "Next services", 0, 1, 6, 3),
    SERVER_LOAD("server-load", "Assignments per server", 6, 1, 6, 3);

    private final String id;
    private final String titleKey;
    private final int defaultCol;
    private final int defaultRow;
    private final int defaultColSpan;
    private final int defaultRowSpan;

    WidgetType(String id, String titleKey, int defaultCol, int defaultRow, int defaultColSpan, int defaultRowSpan) {
        this.id = id;
        this.titleKey = titleKey;
        this.defaultCol = defaultCol;
        this.defaultRow = defaultRow;
        this.defaultColSpan = defaultColSpan;
        this.defaultRowSpan = defaultRowSpan;
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
        return new WidgetPlacement(this, defaultCol, defaultRow, defaultColSpan, defaultRowSpan);
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
