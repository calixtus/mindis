package org.mindis.core.persistence;

import com.fasterxml.jackson.core.type.TypeReference;

import jakarta.inject.Singleton;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

import org.jspecify.annotations.Nullable;

import org.mindis.core.model.Server;
import org.mindis.core.preferences.DataDirectory;

/// Roster storage: servers.json in the user data directory. Upsert by id.
/// Mutations stage into the in-memory cache only; disk I/O happens exclusively
/// through {@link #flush()} / {@link #reload()} (see {@link AppDatabase}).
@Singleton
public class ServerRepository {

    private final JsonStore<Server> store;
    private @Nullable List<Server> servers;

    public ServerRepository(DataDirectory dataDirectory) {
        this(dataDirectory.resolve("servers.json"));
    }

    protected ServerRepository(Path file) {
        this.store = new JsonStore<>(file, new TypeReference<>() {
        });
    }

    public synchronized List<Server> findAll() {
        return List.copyOf(cached());
    }

    public synchronized Optional<Server> findById(String id) {
        return findAll().stream().filter(server -> server.id().equals(id)).findFirst();
    }

    public synchronized void save(Server server) {
        List<Server> list = cached();
        list.removeIf(existing -> existing.id().equals(server.id()));
        list.add(server);
        sort(list);
    }

    public synchronized void delete(String id) {
        cached().removeIf(existing -> existing.id().equals(id));
    }

    /// Writes the staged state to disk - the only disk write path.
    public synchronized void flush() {
        store.save(cached());
    }

    /// Discards staged (unflushed) mutations and reloads from disk.
    public synchronized void reload() {
        servers = null;
        cached();
    }

    /// The live (mutable) cache, loading and sorting it from disk on first access.
    private List<Server> cached() {
        if (servers == null) {
            servers = new ArrayList<>(store.load());
            sort(servers);
        }
        return servers;
    }

    private static void sort(List<Server> list) {
        list.sort(Comparator.comparing(Server::lastName).thenComparing(Server::firstName));
    }
}
