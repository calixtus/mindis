package org.mindis.core.preferences;

import java.util.Locale;

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
        int solverSecondsLimit) {

    public static final int CURRENT_VERSION = 2;
    public static final int DEFAULT_SOLVER_SECONDS = 30;

    public enum Theme {
        LIGHT,
        DARK
    }

    /**
     * Last main window geometry; {@code null} until the first shutdown.
     */
    public record WindowBounds(double x, double y, double width, double height, boolean maximized) {
    }

    public static MinDisPreferences defaults() {
        String language = Locale.getDefault().getLanguage().equals("de") ? "de" : "en";
        return new MinDisPreferences(CURRENT_VERSION, language, Theme.LIGHT, null, DEFAULT_SOLVER_SECONDS);
    }

    public Locale locale() {
        return Locale.forLanguageTag(languageTag);
    }

    public MinDisPreferences withLanguageTag(String newLanguageTag) {
        return new MinDisPreferences(version, newLanguageTag, theme, windowBounds, solverSecondsLimit);
    }

    public MinDisPreferences withTheme(Theme newTheme) {
        return new MinDisPreferences(version, languageTag, newTheme, windowBounds, solverSecondsLimit);
    }

    public MinDisPreferences withWindowBounds(WindowBounds newWindowBounds) {
        return new MinDisPreferences(version, languageTag, theme, newWindowBounds, solverSecondsLimit);
    }

    public MinDisPreferences withSolverSecondsLimit(int newSolverSecondsLimit) {
        return new MinDisPreferences(version, languageTag, theme, windowBounds, newSolverSecondsLimit);
    }
}
