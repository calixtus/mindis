package org.mindis.gui.modules;

import org.jspecify.annotations.NullMarked;

import org.mindis.core.model.Role;
import org.mindis.core.persistence.RoleRepository;

/// ViewModel for {@link RolesModule}: owns the {@link RoleRepository} reads the
/// module still needs directly (CRUD goes through the shared
/// {@link org.mindis.workbench.LiveStore}), so the module only constructs UI
/// and binds to this class.
@NullMarked
final class RolesViewModel {

    private final RoleRepository roleRepository;

    RolesViewModel(RoleRepository roleRepository) {
        this.roleRepository = roleRepository;
    }

    /// A blank role with the next free sort order, for the New action.
    Role createStub() {
        return new Role(Role.newId(), "", null, null, roleRepository.nextSortOrder());
    }
}
