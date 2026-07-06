package org.mindis.core.preferences;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

import org.mindis.core.l10n.Localization;
import org.mindis.core.planning.MinDisConstraintProvider;

/**
 * All user-facing settings (PLAN.md section 2.6). Immutable; use the wither
 * methods and {@link PreferencesService#update} for changes.
 *
 * <p>New user-facing setting = new component with a default here. Bump
 * {@link #CURRENT_VERSION} and add an explicit migration in
 * {@link PreferencesService} when the shape changes incompatibly.
 */
public record MinDisPreferences(
        int version,
        String languageTag,
        Theme theme,
        WindowBounds windowBounds,
        int solverSecondsLimit,
        Map<String, Integer> softConstraintWeights) {

    public static final int CURRENT_VERSION = 4;
    public static final int DEFAULT_SOLVER_SECONDS = 30;

    public enum Theme implements PreferenceEnumValue {
        LIGHT("Light"),
        DARK("Dark");

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

    /**
     * Last main window geometry; {@code null} until the first shutdown.
     */
    public record WindowBounds(double x, double y, double width, double height, boolean maximized) {
    }

    public MinDisPreferences {
        // Null-tolerant + default-filling: older JSON lacks newer weights.
        Map<String, Integer> weights = new HashMap<>(MinDisConstraintProvider.defaultSoftWeights());
        if (softConstraintWeights != null) {
            weights.putAll(softConstraintWeights);
        }
        softConstraintWeights = Map.copyOf(weights);
    }

    public static MinDisPreferences defaults() {
        String language = Locale.getDefault().getLanguage().equals("de") ? "de" : "en";
        return new MinDisPreferences(CURRENT_VERSION, language, Theme.LIGHT, null,
                DEFAULT_SOLVER_SECONDS, MinDisConstraintProvider.defaultSoftWeights());
    }

    public Locale locale() {
        return Locale.forLanguageTag(languageTag);
    }

    public MinDisPreferences withLanguageTag(String newLanguageTag) {
        return new MinDisPreferences(version, newLanguageTag, theme, windowBounds,
                solverSecondsLimit, softConstraintWeights);
    }

    public MinDisPreferences withTheme(Theme newTheme) {
        return new MinDisPreferences(version, languageTag, newTheme, windowBounds,
                solverSecondsLimit, softConstraintWeights);
    }

    public MinDisPreferences withWindowBounds(WindowBounds newWindowBounds) {
        return new MinDisPreferences(version, languageTag, theme, newWindowBounds,
                solverSecondsLimit, softConstraintWeights);
    }

    public MinDisPreferences withSolverSecondsLimit(int newSolverSecondsLimit) {
        return new MinDisPreferences(version, languageTag, theme, windowBounds,
                newSolverSecondsLimit, softConstraintWeights);
    }

    public MinDisPreferences withSoftConstraintWeight(String constraintName, int weight) {
        Map<String, Integer> weights = new HashMap<>(softConstraintWeights);
        weights.put(constraintName, weight);
        return new MinDisPreferences(version, languageTag, theme, windowBounds,
                solverSecondsLimit, weights);
    }
}
