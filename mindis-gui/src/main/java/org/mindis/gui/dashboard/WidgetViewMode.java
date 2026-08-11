package org.mindis.gui.dashboard;

import java.util.Optional;

import org.mindis.core.l10n.Localization;

/// How a widget renders its data: as text, or as one of the diagram kinds. Each
/// [WidgetType] declares which of these it supports (its first one is the
/// default), and the user picks among them from the widget header; the choice is
/// persisted with the layout, so the [#id()] is stable and decoupled from
/// the enum name, exactly as for [WidgetType].
public enum WidgetViewMode {

    /// A row of key figures - only the summary widget renders this way.
    TILES("tiles", "Tiles"),
    LIST("list", "List"),
    BAR("bar", "Bar chart"),
    STACKED_BAR("stacked-bar", "Stacked bar chart"),
    PIE("pie", "Pie chart"),
    DONUT("donut", "Donut chart"),
    LINE("line", "Line chart"),
    AREA("area", "Area chart");

    private final String id;
    private final String titleKey;

    WidgetViewMode(String id, String titleKey) {
        this.id = id;
        this.titleKey = titleKey;
    }

    public String id() {
        return id;
    }

    /// Localized mode name; looked up lazily so it tracks the current language.
    public String displayName() {
        return Localization.lang(titleKey);
    }

    /// The icon shown for this mode in the widget header's mode menu.
    String iconCode() {
        return switch (this) {
            case TILES -> "mdi2v-view-grid-outline";
            case LIST -> "mdi2f-format-list-bulleted";
            case BAR -> "mdi2c-chart-bar";
            case STACKED_BAR -> "mdi2c-chart-bar-stacked";
            case PIE -> "mdi2c-chart-pie";
            case DONUT -> "mdi2c-chart-donut";
            case LINE -> "mdi2c-chart-line";
            case AREA -> "mdi2c-chart-areaspline";
        };
    }

    public static Optional<WidgetViewMode> fromId(String id) {
        for (WidgetViewMode mode : values()) {
            if (mode.id.equals(id)) {
                return Optional.of(mode);
            }
        }
        return Optional.empty();
    }
}
