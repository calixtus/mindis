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
    private final java.util.Map<String, ObjectProperty<Integer>> softWeights = new java.util.HashMap<>();

    public UiPreferences(PreferencesService preferencesService) {
        this.preferencesService = preferencesService;
        // Order matters: values first, write-through subscriptions second,
        // the external listener last. Registering listeners in a constructor
        // technically lets 'this' escape; acceptable here because Avaje wires
        // beans single-threaded and the lambdas only touch the (already
        // initialized) property fields.
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

    /**
     * Editable weight of one tunable soft constraint (names =
     * MinDisConstraintProvider constants). Settings is the only writer, so no
     * external re-sync is wired for these.
     */
    public ObjectProperty<Integer> softWeightProperty(String constraintName) {
        return softWeights.computeIfAbsent(constraintName, name -> {
            ObjectProperty<Integer> property = new SimpleObjectProperty<>(
                    preferencesService.get().softConstraintWeights().getOrDefault(name, 1));
            property.subscribe(weight ->
                    preferencesService.update(p -> p.withSoftConstraintWeight(name, weight)));
            return property;
        });
    }
}
