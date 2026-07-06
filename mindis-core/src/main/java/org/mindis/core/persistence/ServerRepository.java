package org.mindis.core.persistence;

import com.fasterxml.jackson.core.type.TypeReference;

import jakarta.inject.Singleton;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

import org.mindis.core.model.Server;
import org.mindis.core.preferences.AppDirectories;

/**
 * Roster storage: servers.json in the user data directory. Upsert by id.
 */
@Singleton
public class ServerRepository {

    private final JsonStore<Server> store;
    private List<Server> servers;

    public ServerRepository() {
        this(AppDirectories.userDataDir().resolve("servers.json"));
    }

    ServerRepository(Path file) {
        this.store = new JsonStore<>(file, new TypeReference<>() {
        });
    }

    public synchronized List<Server> findAll() {
        if (servers == null) {
            servers = new ArrayList<>(store.load());
            sort();
        }
        return List.copyOf(servers);
    }

    public synchronized Optional<Server> findById(String id) {
        return findAll().stream().filter(server -> server.id().equals(id)).findFirst();
    }

    public synchronized void save(Server server) {
        findAll();
        servers.removeIf(existing -> existing.id().equals(server.id()));
        servers.add(server);
        sort();
        store.save(servers);
    }

    public synchronized void delete(String id) {
        findAll();
        servers.removeIf(existing -> existing.id().equals(id));
        store.save(servers);
    }

    private void sort() {
        servers.sort(Comparator.comparing(Server::lastName).thenComparing(Server::firstName));
    }
}
