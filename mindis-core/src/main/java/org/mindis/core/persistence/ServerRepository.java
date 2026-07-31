package org.mindis.core.persistence;

import jakarta.inject.Singleton;

import java.util.Comparator;

import org.mindis.core.model.Server;

/// Roster storage: the servers of the currently open document, ordered by name
/// (see [InMemoryRepository]).
@Singleton
public final class ServerRepository extends InMemoryRepository<Server> {

    public ServerRepository() {
        super(Server::id, Comparator.comparing(Server::lastName).thenComparing(Server::firstName));
    }
}
