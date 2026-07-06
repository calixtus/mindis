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
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Loads and stores {@link MinDisPreferences} as JSON in the user data
 * directory (PLAN.md section 2.6). Writes are atomic (temp file + move).
 * A corrupt or missing file yields defaults, never a crash.
 *
 * <p>Change listeners use a plain {@link Consumer} - no JavaFX types in core
 * (PLAN.md section 2.5). UI adapters bridge to observable properties.
 */
@Singleton
public class PreferencesService {

    private static final Logger LOGGER = Logger.getLogger(PreferencesService.class.getName());

    private final Path preferencesFile;
    private final ObjectMapper objectMapper;
    private final List<Consumer<MinDisPreferences>> listeners = new CopyOnWriteArrayList<>();

    private MinDisPreferences current;

    public PreferencesService() {
        this(AppDirectories.userDataDir().resolve("preferences.json"));
    }

    PreferencesService(Path preferencesFile) {
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

    /**
     * Applies the change to the current preferences, persists the result
     * atomically and notifies listeners.
     */
    public synchronized MinDisPreferences update(UnaryOperator<MinDisPreferences> change) {
        MinDisPreferences updated = change.apply(get());
        if (!updated.equals(current)) {
            current = updated;
            save(updated);
            listeners.forEach(listener -> listener.accept(updated));
        }
        return current;
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
            LOGGER.log(Level.WARNING, "Could not read preferences, falling back to defaults: " + preferencesFile, e);
            return MinDisPreferences.defaults();
        }
    }

    private MinDisPreferences migrate(MinDisPreferences loaded) {
        if (loaded.version() == MinDisPreferences.CURRENT_VERSION) {
            return loaded;
        }
        // Version 1 is the first shape; unknown versions are normalized to the
        // current one. Future incompatible changes get explicit steps here.
        return new MinDisPreferences(
                MinDisPreferences.CURRENT_VERSION,
                loaded.languageTag(),
                loaded.theme(),
                loaded.windowBounds());
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
            LOGGER.log(Level.WARNING, "Could not save preferences: " + preferencesFile, e);
        }
    }
}
