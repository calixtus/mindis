package org.mindis.gui.preferences;

import jakarta.inject.Singleton;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiFunction;
import java.util.function.Function;

import javafx.beans.property.ObjectProperty;

import org.mindis.core.planning.MinDisConstraintProvider;
import org.mindis.core.preferences.AppLanguage;
import org.mindis.core.preferences.MinDisPreferences;
import org.mindis.core.preferences.PreferencesService;

/**
 * JavaFX property view over {@link PreferencesService} (PLAN.md section 2.6):
 * UI code binds to these properties; changes write through to the persisted
 * preferences. Core stays free of UI concerns; this adapter is the bridge.
 *
 * <p>Registry style (docs/adr/006-preferences-architecture.md): each setting
 * is defined exactly once via {@link #register}, which wires initial load,
 * write-through and external re-sync generically. No update loops: property
 * setters and {@code PreferencesService.update} are no-ops for equal values.
 */
@Singleton
public final class UiPreferences {

    private final PreferencesService preferencesService;
    private final List<PreferenceValue<?>> registry = new ArrayList<>();

    private final PreferenceValue<AppLanguage> language;
    private final PreferenceValue<MinDisPreferences.Theme> theme;
    private final PreferenceValue<Integer> solverSecondsLimit;
    private final Map<String, PreferenceValue<Integer>> softWeights = new LinkedHashMap<>();

    public UiPreferences(PreferencesService preferencesService) {
        this.preferencesService = preferencesService;

        language = register(
                p -> AppLanguage.fromTag(p.languageTag()),
                (p, value) -> p.withLanguageTag(value.tag()));
        theme = register(
                MinDisPreferences::theme,
                MinDisPreferences::withTheme);
        solverSecondsLimit = register(
                MinDisPreferences::solverSecondsLimit,
                MinDisPreferences::withSolverSecondsLimit);
        for (String constraintName : MinDisConstraintProvider.tunableSoftConstraints()) {
            softWeights.put(constraintName, register(
                    p -> p.softConstraintWeights().get(constraintName),
                    (p, weight) -> p.withSoftConstraintWeight(constraintName, weight)));
        }

        // Single external re-sync point: whenever the record changes (from
        // any writer), every registered property re-reads its value.
        preferencesService.addListener(updated ->
                registry.forEach(preferenceValue -> preferenceValue.refresh(updated)));
    }

    private <T> PreferenceValue<T> register(Function<MinDisPreferences, T> getter,
                                            BiFunction<MinDisPreferences, T, MinDisPreferences> wither) {
        PreferenceValue<T> preferenceValue = new PreferenceValue<>(preferencesService, getter, wither);
        registry.add(preferenceValue);
        return preferenceValue;
    }

    public ObjectProperty<AppLanguage> languageProperty() {
        return language.property();
    }

    public ObjectProperty<MinDisPreferences.Theme> themeProperty() {
        return theme.property();
    }

    public ObjectProperty<Integer> solverSecondsLimitProperty() {
        return solverSecondsLimit.property();
    }

    /**
     * Editable weight of one tunable soft constraint
     * ({@link MinDisConstraintProvider#tunableSoftConstraints()}).
     */
    public ObjectProperty<Integer> softWeightProperty(String constraintName) {
        PreferenceValue<Integer> preferenceValue = softWeights.get(constraintName);
        if (preferenceValue == null) {
            throw new IllegalArgumentException("Not a tunable constraint: " + constraintName);
        }
        return preferenceValue.property();
    }
}
