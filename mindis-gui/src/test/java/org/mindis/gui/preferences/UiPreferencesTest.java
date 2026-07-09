package org.mindis.gui.preferences;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mindis.core.planning.MinDisConstraintProvider;
import org.mindis.core.preferences.AppLanguage;
import org.mindis.core.preferences.MinDisPreferences;
import org.mindis.core.preferences.PreferencesService;

/// Registry behavior; runs headless - javafx.base properties need no toolkit.
class UiPreferencesTest {

    @TempDir
    Path tempDir;

    private PreferencesService service() {
        return new TestablePreferencesService(tempDir.resolve("preferences.json"));
    }

    @Test
    void propertyChangeWritesThrough() {
        PreferencesService service = service();
        UiPreferences uiPreferences = new UiPreferences(service);

        uiPreferences.languageProperty().set(AppLanguage.GERMAN);
        uiPreferences.themeProperty().set(MinDisPreferences.Theme.DARK);
        uiPreferences.softWeightProperty(MinDisConstraintProvider.SIBLINGS_TOGETHER).set(9);

        assertEquals("de", service.get().languageTag());
        assertEquals(MinDisPreferences.Theme.DARK, service.get().theme());
        assertEquals(9, service.get().softConstraintWeights()
                .get(MinDisConstraintProvider.SIBLINGS_TOGETHER));
    }

    @Test
    void externalUpdateRefreshesProperties() {
        PreferencesService service = service();
        UiPreferences uiPreferences = new UiPreferences(service);

        service.update(p -> p.withTheme(MinDisPreferences.Theme.DARK).withLanguageTag("de"));

        assertEquals(MinDisPreferences.Theme.DARK, uiPreferences.themeProperty().get());
        assertEquals(AppLanguage.GERMAN, uiPreferences.languageProperty().get());
    }

    @Test
    void unknownConstraintNameIsRejected() {
        UiPreferences uiPreferences = new UiPreferences(service());

        assertThrows(IllegalArgumentException.class,
                () -> uiPreferences.softWeightProperty("No such constraint"));
    }

    /// Exposes the package-private path constructor for tests outside the
    /// core package.
    private static final class TestablePreferencesService extends PreferencesService {
        TestablePreferencesService(Path file) {
            super(file);
        }
    }
}
