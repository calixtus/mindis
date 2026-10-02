package org.mindis.gui.theme;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Arrays;
import java.util.Map;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;

import org.mindis.core.preferences.AccentColor;

class ThemeStylerTest {

    /// The light accents get dark text on their fills; the mid-tones keep the light text
    /// the themes were designed with.
    @Test
    void onlyLightAccentsGetDarkTextOnTheirFills() {
        Map<AccentColor, Boolean> darkText = Arrays.stream(AccentColor.values())
                .filter(accent -> accent.baseHex() != null)
                .collect(Collectors.toMap(accent -> accent, accent -> ThemeStyler.needsDarkTextOn(accent.baseHex())));

        assertEquals(Map.of(
                AccentColor.BLUE, false,
                AccentColor.PURPLE, false,
                AccentColor.RED, false,
                AccentColor.GREEN, true,
                AccentColor.ORANGE, true,
                AccentColor.TEAL, true), darkText);
    }
}
