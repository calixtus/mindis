package org.mindis.core.preferences;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class PreferencesServiceTest {

    @TempDir
    Path tempDir;

    private Path preferencesFile() {
        return tempDir.resolve("preferences.json");
    }

    @Test
    void missingFileYieldsDefaults() {
        PreferencesService service = new PreferencesService(preferencesFile());

        MinDisPreferences preferences = service.get();

        assertEquals(MinDisPreferences.CURRENT_VERSION, preferences.version());
        assertNull(preferences.windowBounds());
    }

    @Test
    void updatePersistsAndReloads() {
        PreferencesService service = new PreferencesService(preferencesFile());
        service.update(p -> p.withLanguageTag("de").withTheme(MinDisPreferences.Theme.DARK));

        PreferencesService reloaded = new PreferencesService(preferencesFile());

        assertEquals("de", reloaded.get().languageTag());
        assertEquals(MinDisPreferences.Theme.DARK, reloaded.get().theme());
        assertTrue(Files.exists(preferencesFile()));
    }

    @Test
    void corruptFileYieldsDefaults() throws IOException {
        Files.writeString(preferencesFile(), "{ not json ]");

        PreferencesService service = new PreferencesService(preferencesFile());

        assertEquals(MinDisPreferences.defaults().theme(), service.get().theme());
    }

    @Test
    void listenersAreNotifiedOnUpdate() {
        PreferencesService service = new PreferencesService(preferencesFile());
        AtomicReference<MinDisPreferences> seen = new AtomicReference<>();
        service.addListener(seen::set);

        service.update(p -> p.withTheme(MinDisPreferences.Theme.DARK));

        assertEquals(MinDisPreferences.Theme.DARK, seen.get().theme());
    }

    @Test
    void windowBoundsRoundTrip() {
        PreferencesService service = new PreferencesService(preferencesFile());
        MinDisPreferences.WindowBounds bounds = new MinDisPreferences.WindowBounds(10, 20, 800, 600, false);
        service.update(p -> p.withWindowBounds(bounds));

        PreferencesService reloaded = new PreferencesService(preferencesFile());

        assertEquals(bounds, reloaded.get().windowBounds());
    }
}
