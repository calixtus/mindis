package org.mindis.gui.preferences;

import java.util.Arrays;

import javafx.beans.property.Property;
import javafx.collections.FXCollections;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Slider;
import javafx.util.StringConverter;

import org.jspecify.annotations.Nullable;

import org.mindis.core.preferences.PreferenceEnumValue;

/// Generic controls for preference values. A choice value renders itself
/// ({@link PreferenceEnumValue#displayName()}) and can exclude itself
/// ({@link PreferenceEnumValue#isSelectable()}), so a settings row reduces to one
/// call (kickstartfx idea, reflection-free).
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

    /// An AtlantaFX-themed {@link Slider} bound to an integer preference.
    /// {@code Slider.valueProperty()} is a primitive {@code DoubleProperty} -
    /// plain JavaFX bidirectional binding needs matching types, so this syncs
    /// both directions by hand instead, rounding on the way into the
    /// (integer) preference.
    public static Slider intSlider(int min, int max, Property<Integer> property) {
        Slider slider = new Slider(min, max, property.getValue());
        slider.valueProperty().addListener((obs, oldValue, newValue) -> {
            int rounded = (int) Math.round(newValue.doubleValue());
            if (!Integer.valueOf(rounded).equals(property.getValue())) {
                property.setValue(rounded);
            }
        });
        property.addListener((obs, oldValue, newValue) -> {
            if (newValue != null && slider.getValue() != newValue.doubleValue()) {
                slider.setValue(newValue);
            }
        });
        return slider;
    }
}
