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

/**
 * Roster storage: servers.json in the user data directory. Upsert by id.
 */
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
        store.save(list);
    }

    public synchronized void delete(String id) {
        List<Server> list = cached();
        list.removeIf(existing -> existing.id().equals(id));
        store.save(list);
    }

    /** The live (mutable) cache, loading and sorting it from disk on first access. */
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
