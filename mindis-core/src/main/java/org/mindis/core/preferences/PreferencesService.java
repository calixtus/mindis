package org.mindis.core.preferences;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

import jakarta.inject.Singleton;

import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;
import java.util.function.UnaryOperator;

import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/// Loads and stores {@link MinDisPreferences} as JSON in the user data
/// directory (PLAN.md section 2.6). Writes are atomic (temp file + move).
/// A corrupt or missing file yields defaults, never a crash.
///
/// <p>Change listeners use a plain {@link Consumer} - no JavaFX types in core
/// (PLAN.md section 2.5). UI adapters bridge to observable properties.
@Singleton
public class PreferencesService {

    private static final Logger LOGGER = LoggerFactory.getLogger(PreferencesService.class);

    private final Path preferencesFile;
    private final ObjectMapper objectMapper;
    private final List<Consumer<MinDisPreferences>> listeners = new CopyOnWriteArrayList<>();

    private @Nullable MinDisPreferences current;

    public PreferencesService(DataDirectory dataDirectory) {
        this(dataDirectory.resolve("preferences.json"));
    }

    protected PreferencesService(Path preferencesFile) {
        this.preferencesFile = preferencesFile;
        this.objectMapper = new ObjectMapper()
                .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
                .configure(SerializationFeature.INDENT_OUTPUT, true);
    }

    public synchronized MinDisPreferences get() {
        if (current == null) {
            current = load();
        }
        return current;
    }

    /// Applies the change to the current preferences, persists the result
    /// atomically and notifies listeners.
    public synchronized MinDisPreferences update(UnaryOperator<MinDisPreferences> change) {
        MinDisPreferences before = get();
        MinDisPreferences updated = change.apply(before);
        if (updated.equals(before)) {
            return before;
        }
        current = updated;
        save(updated);
        listeners.forEach(listener -> listener.accept(updated));
        return updated;
    }

    public void addListener(Consumer<MinDisPreferences> listener) {
        listeners.add(listener);
    }

    public void removeListener(Consumer<MinDisPreferences> listener) {
        listeners.remove(listener);
    }

    private MinDisPreferences load() {
        if (!Files.exists(preferencesFile)) {
            return MinDisPreferences.defaults();
        }
        try {
            MinDisPreferences loaded = objectMapper.readValue(preferencesFile.toFile(), MinDisPreferences.class);
            return migrate(loaded);
        } catch (IOException e) {
            LOGGER.warn("Could not read preferences, falling back to defaults: {}", preferencesFile, e);
            return MinDisPreferences.defaults();
        }
    }

    private MinDisPreferences migrate(MinDisPreferences loaded) {
        if (loaded.version() == MinDisPreferences.CURRENT_VERSION) {
            return loaded;
        }
        // v1 -> v2: solverSecondsLimit added; absent field deserializes as 0.
        // v2 -> v3: softConstraintWeights added; the record's compact
        // constructor already fills missing weights with defaults.
        // v3 -> v4: largeSidebarIcons dropped; the unknown field in older JSON
        // is ignored on read (FAIL_ON_UNKNOWN_PROPERTIES is off).
        // v4 -> v5: accentColor/fontFamily/fontSize added; absent fields
        // deserialize as null/0 and the record's compact constructor fills
        // them with defaults.
        // v5 -> v6: followSystemTheme added; absent boolean deserializes as
        // false (do not follow), which is the intended default.
        // v6 -> v7: lastExportDirectory added; absent field deserializes as
        // null, which is the intended default (no remembered directory yet).
        // v7 -> v8: sidebarWidth added; absent field deserializes as null,
        // which is the intended default (the workbench falls back to its own
        // default width).
        // v8 -> v9: lastDocument added (data moved from per-entity files in the
        // data directory into one user-chosen document). Absent field
        // deserializes as null, so a preferences file written by an older
        // version starts with a new untitled document - the old per-entity
        // files are not read any more.
        int solverSeconds = loaded.solverSecondsLimit() > 0
                ? loaded.solverSecondsLimit()
                : MinDisPreferences.DEFAULT_SOLVER_SECONDS;
        return new MinDisPreferences(
                MinDisPreferences.CURRENT_VERSION,
                loaded.languageTag(),
                loaded.theme(),
                loaded.windowBounds(),
                solverSeconds,
                loaded.softConstraintWeights(),
                loaded.accentColor(),
                loaded.fontFamily(),
                loaded.fontSize(),
                loaded.followSystemTheme(),
                loaded.lastExportDirectory(),
                loaded.sidebarWidth(),
                loaded.lastDocument());
    }

    private void save(MinDisPreferences preferences) {
        try {
            Files.createDirectories(preferencesFile.getParent());
            Path tempFile = preferencesFile.resolveSibling(preferencesFile.getFileName() + ".tmp");
            objectMapper.writeValue(tempFile.toFile(), preferences);
            try {
                Files.move(tempFile, preferencesFile,
                        StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(tempFile, preferencesFile, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException e) {
            LOGGER.warn("Could not save preferences: {}", preferencesFile, e);
        }
    }
}
