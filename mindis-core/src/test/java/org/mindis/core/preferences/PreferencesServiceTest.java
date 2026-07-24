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
    // NullAway: the listener is invoked synchronously by update() above, so
    // seen.get() is always populated by the time it's read.
    @SuppressWarnings("NullAway")
    void listenersAreNotifiedOnUpdate() {
        PreferencesService service = new PreferencesService(preferencesFile());
        AtomicReference<MinDisPreferences> seen = new AtomicReference<>();
        service.addListener(seen::set);

        service.update(p -> p.withTheme(MinDisPreferences.Theme.DARK));

        assertEquals(MinDisPreferences.Theme.DARK, seen.get().theme());
    }

    @Test
    void softWeightsDefaultAndOverrideSurviveRoundTrip() {
        PreferencesService service = new PreferencesService(preferencesFile());
        assertEquals(
                org.mindis.core.planning.MinDisConstraintProvider.defaultSoftWeights(),
                service.get().softConstraintWeights());

        service.update(p -> p.withSoftConstraintWeight(
                org.mindis.core.planning.MinDisConstraintProvider.SIBLINGS_TOGETHER, 9));

        PreferencesService reloaded = new PreferencesService(preferencesFile());
        assertEquals(9, reloaded.get().softConstraintWeights()
                .get(org.mindis.core.planning.MinDisConstraintProvider.SIBLINGS_TOGETHER));
    }

    @Test
    void recentCollectionsRoundTripMostRecentFirst() {
        PreferencesService service = new PreferencesService(preferencesFile());
        service.update(p -> p
                .withRecentCollection(new RecentCollection("/a.json", "Alpha", null, 1L))
                .withRecentCollection(new RecentCollection("/b.json", "Beta", "logo", 2L)));

        PreferencesService reloaded = new PreferencesService(preferencesFile());

        assertEquals(
                java.util.List.of(
                        new RecentCollection("/b.json", "Beta", "logo", 2L),
                        new RecentCollection("/a.json", "Alpha", null, 1L)),
                reloaded.get().recentCollections());
    }

    @Test
    void recentCollectionsDedupByPathAndCapAtFive() {
        MinDisPreferences preferences = MinDisPreferences.defaults()
                .withRecentCollection(new RecentCollection("/1.json", "one", null, 1L))
                .withRecentCollection(new RecentCollection("/2.json", "two", null, 2L))
                .withRecentCollection(new RecentCollection("/3.json", "three", null, 3L))
                .withRecentCollection(new RecentCollection("/4.json", "four", null, 4L))
                .withRecentCollection(new RecentCollection("/5.json", "five", null, 5L))
                .withRecentCollection(new RecentCollection("/6.json", "six", null, 6L))
                // re-opening /2 refreshes its name and moves it to the front
                .withRecentCollection(new RecentCollection("/2.json", "two-again", null, 7L));

        assertEquals(MinDisPreferences.MAX_RECENT_COLLECTIONS, preferences.recentCollections().size());
        assertEquals("/2.json", preferences.recentCollections().get(0).path());
        assertEquals("two-again", preferences.recentCollections().get(0).displayName());
        assertTrue(preferences.recentCollections().stream().noneMatch(r -> r.path().equals("/1.json")),
                "oldest entry must fall off once the cap is exceeded");
    }

    @Test
    void migrationSeedsRecentListFromLastDocument() throws IOException {
        Files.writeString(preferencesFile(), """
                { "version": 9, "languageTag": "en", "theme": "LIGHT", "lastDocument": "/parish.json" }
                """);

        PreferencesService service = new PreferencesService(preferencesFile());
        MinDisPreferences preferences = service.get();

        assertEquals(MinDisPreferences.CURRENT_VERSION, preferences.version());
        assertEquals(1, preferences.recentCollections().size());
        assertEquals("/parish.json", preferences.recentCollections().get(0).path());
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
