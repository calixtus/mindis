package org.mindis.gui.preferences;

import java.util.function.BiFunction;
import java.util.function.Function;

import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;

import org.mindis.core.preferences.MinDisPreferences;
import org.mindis.core.preferences.PreferencesService;

/// One preference exposed as a JavaFX property: reads its initial value from
/// the persisted record, writes changes through via the record's wither, and
/// can be re-synced when the record changes elsewhere. The registry in
/// {@link UiPreferences} is the only creator.
final class PreferenceValue<T> {

    private final ObjectProperty<T> property;
    private final Function<MinDisPreferences, T> getter;

    PreferenceValue(PreferencesService preferencesService,
                    Function<MinDisPreferences, T> getter,
                    BiFunction<MinDisPreferences, T, MinDisPreferences> wither) {
        this.getter = getter;
        this.property = new SimpleObjectProperty<>(getter.apply(preferencesService.get()));
        this.property.subscribe(value ->
                preferencesService.update(p -> wither.apply(p, value)));
    }

    ObjectProperty<T> property() {
        return property;
    }

    /// Re-reads the value from the given record (no-op if equal, so no update
    /// loops with the write-through subscription).
    void refresh(MinDisPreferences preferences) {
        property.set(getter.apply(preferences));
    }
}
