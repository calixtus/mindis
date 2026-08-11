package org.mindis.core.preferences;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.jspecify.annotations.Nullable;

import org.mindis.core.l10n.Localization;
import org.mindis.core.planning.MinDisConstraintProvider;

/// All user-facing settings (PLAN.md section 2.6). Immutable; use the wither
/// methods and [PreferencesService#update] for changes.
///
/// <p>New user-facing setting = new component with a default here. Bump
/// [#CURRENT_VERSION] and add an explicit migration in
/// [PreferencesService] when the shape changes incompatibly.
public record MinDisPreferences(
        int version,
        String languageTag,
        Theme theme,
        @Nullable WindowBounds windowBounds,
        int solverSecondsLimit,
        Map<String, Integer> softConstraintWeights,
        AccentColor accentColor,
        String fontFamily,
        int fontSize,
        @Nullable String lastExportDirectory,
        @Nullable Double sidebarWidth,
        @Nullable String lastDocument,
        List<RecentCollection> recentCollections,
        ToolbarButtonDisplay toolbarButtonDisplay,
        @Nullable List<DashboardWidgetLayout> dashboardWidgets) {

    public static final int CURRENT_VERSION = 14;
    /// Most-recent collections kept for the switcher dropdown (UX guidance:
    /// show up to five recents).
    public static final int MAX_RECENT_COLLECTIONS = 5;
    public static final int DEFAULT_SOLVER_SECONDS = 30;
    /// Sentinel meaning "use the theme's default font family" (no override).
    public static final String DEFAULT_FONT_FAMILY = "Default";
    public static final int DEFAULT_FONT_SIZE = 14;
    public static final int MIN_FONT_SIZE = 10;
    public static final int MAX_FONT_SIZE = 24;

    /// Light/dark choice. [#SYSTEM] follows the OS colour scheme live;
    /// the GUI resolves it to a concrete [#LIGHT]/[#DARK] before
    /// styling.
    public enum Theme implements PreferenceEnumValue {
        LIGHT("Light"),
        DARK("Dark"),
        SYSTEM("System");

        private final String l10nKey;

        Theme(String l10nKey) {
            this.l10nKey = l10nKey;
        }

        @Override
        public String displayName() {
            // Looked up lazily (not cached): must reflect the current
            // language, not the language active when this enum was loaded.
            return Localization.lang(l10nKey);
        }
    }

    /// Last main window geometry; `null` until the first shutdown.
    public record WindowBounds(double x, double y, double width, double height, boolean maximized) {
    }

    public MinDisPreferences {
        // Null-tolerant + default-filling: older JSON lacks newer fields.
        Map<String, Integer> weights = new HashMap<>(MinDisConstraintProvider.defaultSoftWeights());
        if (softConstraintWeights != null) {
            weights.putAll(softConstraintWeights);
        }
        softConstraintWeights = Map.copyOf(weights);
        if (accentColor == null) {
            accentColor = AccentColor.DEFAULT;
        }
        if (fontFamily == null || fontFamily.isBlank()) {
            fontFamily = DEFAULT_FONT_FAMILY;
        }
        if (fontSize <= 0) {
            fontSize = DEFAULT_FONT_SIZE;
        }
        // Null-tolerant: older JSON (before the recent list) lacks this field.
        recentCollections = recentCollections == null ? List.of() : List.copyOf(recentCollections);
        if (toolbarButtonDisplay == null) {
            toolbarButtonDisplay = ToolbarButtonDisplay.BOTH;
        }
        // Nullable on purpose: null means "no layout saved yet" (the dashboard
        // falls back to its default arrangement); an empty list is a real,
        // deliberately-cleared board. Copy only when present to keep both.
        dashboardWidgets = dashboardWidgets == null ? null : List.copyOf(dashboardWidgets);
    }

    public static MinDisPreferences defaults() {
        String language = "de".equals(Locale.getDefault().getLanguage()) ? "de" : "en";
        return new MinDisPreferences(CURRENT_VERSION, language, Theme.LIGHT, null,
                DEFAULT_SOLVER_SECONDS, MinDisConstraintProvider.defaultSoftWeights(),
                AccentColor.DEFAULT, DEFAULT_FONT_FAMILY, DEFAULT_FONT_SIZE, null, null, null,
                List.of(), ToolbarButtonDisplay.BOTH, null);
    }

    public Locale locale() {
        return Locale.forLanguageTag(languageTag);
    }

    public MinDisPreferences withLanguageTag(String newLanguageTag) {
        return new MinDisPreferences(version, newLanguageTag, theme, windowBounds,
                solverSecondsLimit, softConstraintWeights, accentColor, fontFamily, fontSize,
                lastExportDirectory, sidebarWidth, lastDocument, recentCollections, toolbarButtonDisplay,
                dashboardWidgets);
    }

    public MinDisPreferences withTheme(Theme newTheme) {
        return new MinDisPreferences(version, languageTag, newTheme, windowBounds,
                solverSecondsLimit, softConstraintWeights, accentColor, fontFamily, fontSize,
                lastExportDirectory, sidebarWidth, lastDocument, recentCollections, toolbarButtonDisplay,
                dashboardWidgets);
    }

    public MinDisPreferences withWindowBounds(WindowBounds newWindowBounds) {
        return new MinDisPreferences(version, languageTag, theme, newWindowBounds,
                solverSecondsLimit, softConstraintWeights, accentColor, fontFamily, fontSize,
                lastExportDirectory, sidebarWidth, lastDocument, recentCollections, toolbarButtonDisplay,
                dashboardWidgets);
    }

    public MinDisPreferences withSolverSecondsLimit(int newSolverSecondsLimit) {
        return new MinDisPreferences(version, languageTag, theme, windowBounds,
                newSolverSecondsLimit, softConstraintWeights, accentColor, fontFamily, fontSize,
                lastExportDirectory, sidebarWidth, lastDocument, recentCollections, toolbarButtonDisplay,
                dashboardWidgets);
    }

    public MinDisPreferences withSoftConstraintWeight(String constraintName, int weight) {
        Map<String, Integer> weights = new HashMap<>(softConstraintWeights);
        weights.put(constraintName, weight);
        return new MinDisPreferences(version, languageTag, theme, windowBounds,
                solverSecondsLimit, weights, accentColor, fontFamily, fontSize,
                lastExportDirectory, sidebarWidth, lastDocument, recentCollections, toolbarButtonDisplay,
                dashboardWidgets);
    }

    public MinDisPreferences withAccentColor(AccentColor newAccentColor) {
        return new MinDisPreferences(version, languageTag, theme, windowBounds,
                solverSecondsLimit, softConstraintWeights, newAccentColor, fontFamily, fontSize,
                lastExportDirectory, sidebarWidth, lastDocument, recentCollections, toolbarButtonDisplay,
                dashboardWidgets);
    }

    public MinDisPreferences withFontFamily(String newFontFamily) {
        return new MinDisPreferences(version, languageTag, theme, windowBounds,
                solverSecondsLimit, softConstraintWeights, accentColor, newFontFamily, fontSize,
                lastExportDirectory, sidebarWidth, lastDocument, recentCollections, toolbarButtonDisplay,
                dashboardWidgets);
    }

    public MinDisPreferences withFontSize(int newFontSize) {
        return new MinDisPreferences(version, languageTag, theme, windowBounds,
                solverSecondsLimit, softConstraintWeights, accentColor, fontFamily, newFontSize,
                lastExportDirectory, sidebarWidth, lastDocument, recentCollections, toolbarButtonDisplay,
                dashboardWidgets);
    }

    /// Directory the plan export `FileChooser` last saved into; `null` until the first export.
    public MinDisPreferences withLastExportDirectory(String newLastExportDirectory) {
        return new MinDisPreferences(version, languageTag, theme, windowBounds,
                solverSecondsLimit, softConstraintWeights, accentColor, fontFamily, fontSize,
                newLastExportDirectory, sidebarWidth, lastDocument, recentCollections, toolbarButtonDisplay,
                dashboardWidgets);
    }

    /// Sidebar width; `null` until the first shutdown (the shell then uses its own default).
    public MinDisPreferences withSidebarWidth(double newSidebarWidth) {
        return new MinDisPreferences(version, languageTag, theme, windowBounds,
                solverSecondsLimit, softConstraintWeights, accentColor, fontFamily, fontSize,
                lastExportDirectory, newSidebarWidth, lastDocument, recentCollections, toolbarButtonDisplay,
                dashboardWidgets);
    }

    /// Path of the document last opened or saved, reopened on the next start;
    /// `null` when no document has been opened yet, or after the user
    /// worked in an untitled one.
    public MinDisPreferences withLastDocument(@Nullable String newLastDocument) {
        return new MinDisPreferences(version, languageTag, theme, windowBounds,
                solverSecondsLimit, softConstraintWeights, accentColor, fontFamily, fontSize,
                lastExportDirectory, sidebarWidth, newLastDocument, recentCollections, toolbarButtonDisplay,
                dashboardWidgets);
    }

    /// Records `recent` as the most-recently-used collection: moved to the
    /// front, any earlier entry for the same path removed (its cached name/logo
    /// refreshed), and the list trimmed to [#MAX_RECENT_COLLECTIONS].
    public MinDisPreferences withRecentCollection(RecentCollection recent) {
        List<RecentCollection> updated = new ArrayList<>();
        updated.add(recent);
        for (RecentCollection existing : recentCollections) {
            if (!existing.path().equals(recent.path())) {
                updated.add(existing);
            }
        }
        List<RecentCollection> trimmed = updated.size() > MAX_RECENT_COLLECTIONS
                ? updated.subList(0, MAX_RECENT_COLLECTIONS)
                : updated;
        return new MinDisPreferences(version, languageTag, theme, windowBounds,
                solverSecondsLimit, softConstraintWeights, accentColor, fontFamily, fontSize,
                lastExportDirectory, sidebarWidth, lastDocument, trimmed,
                toolbarButtonDisplay, dashboardWidgets);
    }

    /// Drops the recent entry for `path` (e.g. a document that has since
    /// vanished from disk); a no-op when none matches.
    public MinDisPreferences withoutRecentCollection(String path) {
        List<RecentCollection> updated = new ArrayList<>();
        for (RecentCollection existing : recentCollections) {
            if (!existing.path().equals(path)) {
                updated.add(existing);
            }
        }
        return new MinDisPreferences(version, languageTag, theme, windowBounds,
                solverSecondsLimit, softConstraintWeights, accentColor, fontFamily, fontSize,
                lastExportDirectory, sidebarWidth, lastDocument, updated,
                toolbarButtonDisplay, dashboardWidgets);
    }

    /// How the module toolbar buttons render (text/icon/both); default
    /// [ToolbarButtonDisplay#BOTH].
    public MinDisPreferences withToolbarButtonDisplay(ToolbarButtonDisplay newToolbarButtonDisplay) {
        return new MinDisPreferences(version, languageTag, theme, windowBounds,
                solverSecondsLimit, softConstraintWeights, accentColor, fontFamily, fontSize,
                lastExportDirectory, sidebarWidth, lastDocument, recentCollections,
                newToolbarButtonDisplay, dashboardWidgets);
    }

    /// Persisted dashboard widget layout (positions and grid spans);
    /// `null` until the user first arranges the board, when the dashboard uses
    /// its default arrangement. An empty list is a deliberately-cleared board.
    public MinDisPreferences withDashboardWidgets(List<DashboardWidgetLayout> newDashboardWidgets) {
        return new MinDisPreferences(version, languageTag, theme, windowBounds,
                solverSecondsLimit, softConstraintWeights, accentColor, fontFamily, fontSize,
                lastExportDirectory, sidebarWidth, lastDocument, recentCollections,
                toolbarButtonDisplay, newDashboardWidgets);
    }
}
