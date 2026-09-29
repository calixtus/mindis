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
    TILES("tiles"),
    LIST("list"),
    BAR("bar"),
    STACKED_BAR("stacked-bar"),
    PIE("pie"),
    DONUT("donut"),
    LINE("line"),
    AREA("area");

    private final String id;

    WidgetViewMode(String id) {
        this.id = id;
    }

    public String id() {
        return id;
    }

    /// Localized mode name. Looked up per call (not stored) so it tracks the current
    /// language, and written as a literal inside lang(...) so LocalizationConsistencyTest
    /// can find the key.
    public String displayName() {
        return switch (this) {
            case TILES -> Localization.lang("Tiles");
            case LIST -> Localization.lang("List");
            case BAR -> Localization.lang("Bar chart");
            case STACKED_BAR -> Localization.lang("Stacked bar chart");
            case PIE -> Localization.lang("Pie chart");
            case DONUT -> Localization.lang("Donut chart");
            case LINE -> Localization.lang("Line chart");
            case AREA -> Localization.lang("Area chart");
        };
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
