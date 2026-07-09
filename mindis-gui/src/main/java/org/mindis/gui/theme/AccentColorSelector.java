package org.mindis.gui.theme;

import javafx.application.Platform;
import javafx.beans.property.ObjectProperty;
import javafx.geometry.Pos;
import javafx.scene.control.Toggle;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;

import org.mindis.core.preferences.AccentColor;

/// Row of colored swatch buttons for picking the UI accent - one flat, single
/// select {@link ToggleButton} per {@link AccentColor}, styled with that color.
/// Modeled on AtlantaFX sampler's AccentColorSelector. {@link AccentColor#DEFAULT}
/// shows the OS accent color from JavaFX platform preferences and tracks it live.
public final class AccentColorSelector extends HBox {

    public AccentColorSelector(ObjectProperty<AccentColor> accentProperty) {
        getStyleClass().add("accent-color-selector");
        getStylesheets().add(
                AccentColorSelector.class.getResource("accent-selector.css").toExternalForm());
        setSpacing(6);
        setAlignment(Pos.CENTER_LEFT);

        ToggleGroup group = new ToggleGroup();
        for (AccentColor accent : AccentColor.values()) {
            ToggleButton swatch = createSwatch(accent);
            swatch.setToggleGroup(group);
            swatch.setSelected(accent == accentProperty.get());
            getChildren().add(swatch);
        }

        // Keep one swatch selected at all times; write the pick through.
        group.selectedToggleProperty().subscribe((oldToggle, newToggle) -> {
            if (newToggle == null) {
                if (oldToggle != null) {
                    group.selectToggle(oldToggle);
                }
                return;
            }
            accentProperty.set((AccentColor) newToggle.getUserData());
        });

        // External changes (e.g. reset from elsewhere) re-sync the selection.
        accentProperty.subscribe(value -> {
            for (Toggle toggle : group.getToggles()) {
                if (toggle.getUserData() == value) {
                    group.selectToggle(toggle);
                    return;
                }
            }
        });
    }

    private ToggleButton createSwatch(AccentColor accent) {
        ToggleButton swatch = new ToggleButton();
        swatch.setUserData(accent);
        swatch.getStyleClass().add("accent-swatch");
        swatch.setTooltip(new Tooltip(accent.displayName()));
        swatch.setFocusTraversable(false);
        Region fill = new Region();
        fill.getStyleClass().add("swatch-fill");
        if (accent.baseHex() != null) {
            fill.setStyle("-fx-background-color: " + accent.baseHex() + ";");
        } else {
            // DEFAULT: mirror the OS accent color and follow live changes.
            Platform.getPreferences().accentColorProperty().subscribe(
                    color -> fill.setStyle("-fx-background-color: " + ThemeStyler.toWebHex(color) + ";"));
        }
        swatch.setGraphic(fill);
        return swatch;
    }
}
