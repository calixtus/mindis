package org.mindis.gui;

import java.util.Objects;

import org.mindis.core.model.LiturgicalService;
import org.mindis.core.model.Role;
import org.mindis.core.model.Server;
import org.mindis.core.persistence.RoleRepository;
import org.mindis.core.persistence.ServerRepository;
import org.mindis.core.persistence.ServiceRepository;
import org.mindis.gui.data.LiveStore;

/// A [LiveStore] over a repository the test filled, wired the way `LiveDatabase`
/// wires the real ones. A store mirrors its repository when it is built and on
/// [LiveStore#refresh()], so fill the repository first.
public final class TestStores {

    private TestStores() {
    }

    public static LiveStore<LiturgicalService> services(ServiceRepository repository) {
        return new LiveStore<>(repository::findAll, repository::save,
                service -> repository.delete(service.id()), LiturgicalService::id, Objects::equals);
    }

    public static LiveStore<Server> servers(ServerRepository repository) {
        return new LiveStore<>(repository::findAll, repository::save,
                server -> repository.delete(server.id()), Server::id, Objects::equals);
    }

    public static LiveStore<Role> roles(RoleRepository repository) {
        return new LiveStore<>(repository::findAll, repository::save,
                role -> repository.delete(role.id()), Role::id, Objects::equals);
    }
}
