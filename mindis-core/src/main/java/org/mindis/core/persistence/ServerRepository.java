package org.mindis.core.persistence;

import jakarta.inject.Singleton;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

import org.mindis.core.model.Server;

/// Roster storage: the servers of the currently open document. Upsert by id.
/// Purely in-memory - this cache is the single source of truth every reader
/// (GUI stores, solver, CSV mappers) sees live; disk I/O happens exclusively in
/// {@link AppDatabase}, which fills this repository when a document is opened
/// and collects it back when one is saved.
@Singleton
public class ServerRepository {

    private final List<Server> servers = new ArrayList<>();

    public synchronized List<Server> findAll() {
        return List.copyOf(servers);
    }

    public synchronized Optional<Server> findById(String id) {
        return servers.stream().filter(server -> server.id().equals(id)).findFirst();
    }

    public synchronized void save(Server server) {
        servers.removeIf(existing -> existing.id().equals(server.id()));
        servers.add(server);
        sort(servers);
    }

    public synchronized void delete(String id) {
        servers.removeIf(existing -> existing.id().equals(id));
    }

    /// Replaces the whole content, e.g. with a freshly opened document's
    /// servers. Only {@link AppDatabase} calls this.
    synchronized void replaceAll(List<Server> items) {
        servers.clear();
        servers.addAll(items);
        sort(servers);
    }

    private static void sort(List<Server> list) {
        list.sort(Comparator.comparing(Server::lastName).thenComparing(Server::firstName));
    }
}
