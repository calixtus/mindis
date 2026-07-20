package org.mindis.core.persistence;

import jakarta.inject.Singleton;

/// The application's shared in-memory database: aggregates the four entity
/// repositories, whose caches are the single source of truth every reader
/// (GUI stores, solver, CSV mappers) sees live. {@link #saveAll()} flushes all
/// staged state to disk in one action; {@link #loadAll()} discards staged
/// mutations and reloads from disk - the only two disk-I/O entry points for
/// entity data.
///
/// <p>Assignments are part of the service records (see {@link
/// org.mindis.core.model.Slot}), so they are flushed here with everything else;
/// there is no separate plan store. The immutable archive
/// ({@link ArchivedServiceRepository}) is excluded - it persists on archive,
/// not through Save all.
@Singleton
public class AppDatabase {

    private final RoleRepository roles;
    private final ServerRepository servers;
    private final TemplateRepository templates;
    private final ServiceRepository services;

    public AppDatabase(RoleRepository roles, ServerRepository servers,
                       TemplateRepository templates, ServiceRepository services) {
        this.roles = roles;
        this.servers = servers;
        this.templates = templates;
        this.services = services;
    }

    /// Flushes every repository's staged state to disk.
    public synchronized void saveAll() {
        roles.flush();
        servers.flush();
        templates.flush();
        services.flush();
    }

    /// Discards every repository's staged (unflushed) mutations and reloads from disk.
    public synchronized void loadAll() {
        roles.reload();
        servers.reload();
        templates.reload();
        services.reload();
    }
}
