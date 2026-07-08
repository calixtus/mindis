package org.mindis.gui.preferences;

import java.util.Arrays;

import javafx.beans.property.Property;
import javafx.collections.FXCollections;
import javafx.scene.control.ComboBox;
import javafx.util.StringConverter;

import org.jspecify.annotations.Nullable;

import org.mindis.core.preferences.PreferenceEnumValue;

/**
 * Generic controls for preference values. A choice value renders itself
 * ({@link PreferenceEnumValue#displayName()}) and can exclude itself
 * ({@link PreferenceEnumValue#isSelectable()}), so a settings row reduces to one
 * call (kickstartfx idea, reflection-free).
 */
public final class PreferenceControls {

    private PreferenceControls() {
    }

    public static <T extends PreferenceEnumValue> ComboBox<T> choiceBox(T[] values, Property<T> property) {
        ComboBox<T> box = new ComboBox<>(FXCollections.observableArrayList(
                Arrays.stream(values).filter(PreferenceEnumValue::isSelectable).toList()));
        box.setConverter(new StringConverter<>() {
            @Override
            public String toString(@Nullable T value) {
                return value == null ? "" : value.displayName();
            }

            @Override
            public @Nullable T fromString(@Nullable String string) {
                return null;
            }
        });
        box.valueProperty().bindBidirectional(property);
        return box;
    }
}
