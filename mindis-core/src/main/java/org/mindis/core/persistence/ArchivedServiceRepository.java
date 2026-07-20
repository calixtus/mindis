package org.mindis.core.persistence;

import com.fasterxml.jackson.core.type.TypeReference;

import jakarta.inject.Singleton;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import org.mindis.core.model.ArchivedService;

/// Stores the frozen {@link ArchivedService} snapshots produced when the
/// planner archives past services, as archived-services.json in the user data
/// directory. Unlike the four live entity repositories, archived data is
/// committed history: writes persist immediately (no staged cache flushed by
/// {@link AppDatabase}) and entries are only ever appended, browsed or deleted,
/// never edited - each snapshot is self-contained (see {@link ArchivedService})
/// so it stays faithful regardless of later roster changes.
@Singleton
public class ArchivedServiceRepository {

    private final JsonStore<ArchivedService> store;

    public ArchivedServiceRepository(org.mindis.core.preferences.DataDirectory dataDirectory) {
        this(dataDirectory.resolve("archived-services.json"));
    }

    ArchivedServiceRepository(Path file) {
        this.store = new JsonStore<>(file, new TypeReference<>() {
        });
    }

    /// Every archived service, newest first.
    public synchronized List<ArchivedService> findAll() {
        List<ArchivedService> all = new ArrayList<>(store.load());
        all.sort(Comparator.comparing(ArchivedService::dateTime).reversed());
        return all;
    }

    /// Appends {@code services} and persists immediately. No-op for an empty list.
    public synchronized void addAll(List<ArchivedService> services) {
        if (services.isEmpty()) {
            return;
        }
        List<ArchivedService> all = new ArrayList<>(store.load());
        all.addAll(services);
        store.save(all);
    }

    /// Permanently removes the archived service with {@code id} (retention / cleanup).
    public synchronized void delete(String id) {
        List<ArchivedService> all = new ArrayList<>(store.load());
        all.removeIf(service -> service.id().equals(id));
        store.save(all);
    }
}
