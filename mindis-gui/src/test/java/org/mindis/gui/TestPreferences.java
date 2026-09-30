package org.mindis.gui;

import java.nio.file.Path;

import org.mindis.core.preferences.PreferencesService;

/// A [PreferencesService] over a file the test chose, instead of the one in the user's
/// real data directory.
///
/// Its own class rather than a helper on [FxTest]: most of the tests that need
/// preferences never start the JavaFX toolkit - `ServicesSolverControllerTest` says so in
/// as many words - and reaching for something called `FxTest` to get them suggested
/// otherwise. Four test classes had each grown a private copy of the same three-line
/// subclass instead.
public final class TestPreferences {

    private TestPreferences() {
    }

    /// @param file where this service reads and writes; typically under a `@TempDir`
    public static PreferencesService at(Path file) {
        return new TestablePreferencesService(file);
    }

    /// Exists only to reach [PreferencesService]'s `protected` path constructor,
    /// which is there for exactly this.
    private static final class TestablePreferencesService extends PreferencesService {
        TestablePreferencesService(Path file) {
            super(file);
        }
    }
}
