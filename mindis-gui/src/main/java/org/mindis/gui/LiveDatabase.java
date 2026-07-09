package org.mindis.gui;

import java.util.List;
import java.util.Objects;

import javafx.beans.binding.IntegerExpression;
import javafx.beans.binding.NumberBinding;

import org.mindis.core.model.LiturgicalService;
import org.mindis.core.model.Role;
import org.mindis.core.model.RoleSlot;
import org.mindis.core.model.Server;
import org.mindis.core.model.ServiceTemplate;
import org.mindis.core.persistence.AppDatabase;
import org.mindis.core.persistence.RoleRepository;
import org.mindis.core.persistence.ServerRepository;
import org.mindis.core.persistence.ServiceRepository;
import org.mindis.core.persistence.TemplateRepository;
import org.mindis.workbench.LiveStore;

/// The GUI's view of the shared in-memory database: one long-lived
/// {@link LiveStore} per entity type (write-through mirrors of the {@link
/// AppDatabase} repositories), plus the two global actions. Constructed once
/// in {@code MinDisApp.start()} and reused across UI rebuilds, so unsaved
/// cross-module edits and dirty counts survive a language switch.
public final class LiveDatabase {

    private final AppDatabase database;
    private final LiveStore<Role> roles;
    private final LiveStore<Server> servers;
    private final LiveStore<ServiceTemplate> templates;
    private final LiveStore<LiturgicalService> services;

    public LiveDatabase(AppDatabase database, RoleRepository roleRepository,
                        ServerRepository serverRepository, TemplateRepository templateRepository,
                        ServiceRepository serviceRepository) {
        this.database = database;
        this.roles = new LiveStore<>(roleRepository::findAll, roleRepository::save,
                role -> roleRepository.delete(role.id()), Role::id, Objects::equals);
        this.servers = new LiveStore<>(serverRepository::findAll, serverRepository::save,
                server -> serverRepository.delete(server.id()), Server::id, Objects::equals);
        // Slot lists compare order-insensitively for dirty tracking - a
        // reordered-but-identical slot list is not an unsaved change.
        this.templates = new LiveStore<>(templateRepository::findAll, templateRepository::save,
                template -> templateRepository.delete(template.id()), ServiceTemplate::id,
                LiveDatabase::sameTemplate);
        this.services = new LiveStore<>(serviceRepository::findAll, serviceRepository::save,
                service -> serviceRepository.delete(service.id()), LiturgicalService::id,
                LiveDatabase::sameService);
    }

    public LiveStore<Role> roles() {
        return roles;
    }

    public LiveStore<Server> servers() {
        return servers;
    }

    public LiveStore<ServiceTemplate> templates() {
        return templates;
    }

    public LiveStore<LiturgicalService> services() {
        return services;
    }

    /// Flushes every staged edit to disk and re-baselines all stores (dirty
    /// counts clear, refresh listeners fire).
    public void saveAll() {
        database.saveAll();
        stores().forEach(LiveStore::refresh);
    }

    /// Discards every staged edit, reloading all repositories and stores from disk.
    public void loadAll() {
        database.loadAll();
        stores().forEach(LiveStore::refresh);
    }

    /// Sum of all stores' dirty counts; bind a global Save-all button's disable to {@code isEqualTo(0)}.
    public NumberBinding totalDirtyCount() {
        return IntegerExpression.integerExpression(roles.dirtyCountProperty())
                .add(servers.dirtyCountProperty())
                .add(templates.dirtyCountProperty())
                .add(services.dirtyCountProperty());
    }

    private List<LiveStore<?>> stores() {
        return List.of(roles, servers, templates, services);
    }

    private static boolean sameTemplate(ServiceTemplate a, ServiceTemplate b) {
        return a.dayOfWeek().equals(b.dayOfWeek())
                && a.time().equals(b.time())
                && a.durationMinutes() == b.durationMinutes()
                && a.location().equals(b.location())
                && a.type() == b.type()
                && RoleSlot.sameSlots(a.slots(), b.slots());
    }

    private static boolean sameService(LiturgicalService a, LiturgicalService b) {
        return a.dateTime().equals(b.dateTime())
                && a.durationMinutes() == b.durationMinutes()
                && a.location().equals(b.location())
                && a.type() == b.type()
                && a.note().equals(b.note())
                && RoleSlot.sameSlots(a.slots(), b.slots());
    }
}
