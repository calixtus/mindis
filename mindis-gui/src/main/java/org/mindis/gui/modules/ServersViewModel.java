package org.mindis.gui.modules;

import java.util.List;
import java.util.Set;

import org.jspecify.annotations.NullMarked;

import org.mindis.core.model.Role;
import org.mindis.core.model.Server;
import org.mindis.core.persistence.RoleRepository;
import org.mindis.core.persistence.ServerRepository;

/**
 * ViewModel for {@link ServersModule}: owns the repository reads the module
 * still needs directly (CRUD goes through the shared
 * {@link org.mindis.workbench.LiveStore}), so the module only constructs UI
 * and binds to this class. All reads see the live staged state, unsaved
 * edits included.
 */
@NullMarked
final class ServersViewModel {

    private final ServerRepository serverRepository;
    private final RoleRepository roleRepository;

    ServersViewModel(ServerRepository serverRepository, RoleRepository roleRepository) {
        this.serverRepository = serverRepository;
        this.roleRepository = roleRepository;
    }

    /** A blank, active, inexperienced server, for the New action. */
    Server createStub() {
        return new Server(Server.newId(), "", "", "", null, null,
                Set.of(), List.of(), Set.of(), false, true);
    }

    /** Display name for a role id, falling back to the id if the role was deleted. */
    String roleName(String roleId) {
        return roleRepository.findById(roleId).map(Role::name).orElse(roleId);
    }

    /** Family ids already in use, for the Family field's suggestion popup. */
    List<String> familyIds() {
        return serverRepository.findAll().stream()
                .map(Server::familyId)
                .filter(familyId -> familyId != null && !familyId.isBlank())
                .distinct()
                .sorted()
                .toList();
    }

}
