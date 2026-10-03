package org.mindis.gui.modules;

import java.util.List;
import java.util.Map;

import javafx.collections.ObservableList;

import org.mindis.core.model.Indexes;
import org.mindis.core.model.Role;
import org.mindis.core.model.Server;
import org.mindis.gui.data.LiveStore;

/// The live role and server lists the services screen resolves slot ids
/// against, so the tiles and the editor look names up the same way.
final class LiveRoster {

    private final LiveStore<Role> roles;
    private final LiveStore<Server> servers;

    LiveRoster(LiveStore<Role> roles, LiveStore<Server> servers) {
        this.roles = roles;
        this.servers = servers;
    }

    ObservableList<Role> roles() {
        return roles.items();
    }

    ObservableList<Server> servers() {
        return servers.items();
    }

    Map<String, Role> rolesById() {
        Map<String, Role> byId = Indexes.byKey(roles.items(), Role::id);
        return byId;
    }

    Map<String, Server> serversById() {
        Map<String, Server> byId = Indexes.byKey(servers.items(), Server::id);
        return byId;
    }

    List<Server> activeServers() {
        return servers.items().stream().filter(Server::active).toList();
    }
}
