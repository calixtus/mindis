package org.mindis.gui.preferences;

import jakarta.inject.Singleton;

import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;

import org.mindis.core.preferences.MinDisPreferences;
import org.mindis.core.preferences.PreferencesService;

/**
 * JavaFX property view over {@link PreferencesService} (PLAN.md section 2.6):
 * UI code binds to these properties; changes write through to the persisted
 * preferences. Core stays free of UI concerns; this adapter is the bridge.
 *
 * <p>No update loops: both the property setters and
 * {@code PreferencesService.update} are no-ops for equal values.
 */
@Singleton
public final class UiPreferences {

    private final PreferencesService preferencesService;
    private final StringProperty languageTag = new SimpleStringProperty();
    private final ObjectProperty<MinDisPreferences.Theme> theme = new SimpleObjectProperty<>();
    private final ObjectProperty<Integer> solverSecondsLimit = new SimpleObjectProperty<>();

    public UiPreferences(PreferencesService preferencesService) {
        this.preferencesService = preferencesService;

        MinDisPreferences current = preferencesService.get();
        languageTag.set(current.languageTag());
        theme.set(current.theme());
        solverSecondsLimit.set(current.solverSecondsLimit());

        languageTag.subscribe(tag -> preferencesService.update(p -> p.withLanguageTag(tag)));
        theme.subscribe(newTheme -> preferencesService.update(p -> p.withTheme(newTheme)));
        solverSecondsLimit.subscribe(seconds ->
                preferencesService.update(p -> p.withSolverSecondsLimit(seconds)));

        preferencesService.addListener(updated -> {
            languageTag.set(updated.languageTag());
            theme.set(updated.theme());
            solverSecondsLimit.set(updated.solverSecondsLimit());
        });
    }

    public StringProperty languageTagProperty() {
        return languageTag;
    }

    public ObjectProperty<MinDisPreferences.Theme> themeProperty() {
        return theme;
    }

    public ObjectProperty<Integer> solverSecondsLimitProperty() {
        return solverSecondsLimit;
    }

    public PreferencesService service() {
        return preferencesService;
    }
}
