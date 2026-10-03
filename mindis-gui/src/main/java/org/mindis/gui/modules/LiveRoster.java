package org.mindis.gui.modules;

import java.util.List;
import java.util.Map;

import javafx.collections.ObservableList;

import org.mindis.core.model.Indexes;
import org.mindis.core.model.Role;
import org.mindis.core.model.RoleId;
import org.mindis.core.model.Server;
import org.mindis.core.model.ServerId;
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

    Map<RoleId, Role> rolesById() {
        Map<RoleId, Role> byId = Indexes.byKey(roles.items(), Role::id);
        return byId;
    }

    Map<ServerId, Server> serversById() {
        Map<ServerId, Server> byId = Indexes.byKey(servers.items(), Server::id);
        return byId;
    }

    List<Server> activeServers() {
        return servers.items().stream().filter(Server::active).toList();
    }
}
