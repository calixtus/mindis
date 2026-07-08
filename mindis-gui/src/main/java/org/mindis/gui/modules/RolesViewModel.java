package org.mindis.gui.modules;

import java.util.List;

import org.mindis.core.model.Role;
import org.mindis.core.persistence.RoleRepository;

/**
 * ViewModel for {@link RolesModule}: owns every {@link RoleRepository} call and
 * the sort-order assignment for new roles, so the module only constructs UI and
 * binds to this class.
 */
final class RolesViewModel {

    private static final int SORT_ORDER_STEP = 10;

    private final RoleRepository roleRepository;

    RolesViewModel(RoleRepository roleRepository) {
        this.roleRepository = roleRepository;
    }

    List<Role> findAll() {
        return roleRepository.findAll();
    }

    void save(Role role) {
        roleRepository.save(role);
    }

    void delete(Role role) {
        roleRepository.delete(role.id());
    }

    /** A blank role with the next free sort order, for the New action. */
    Role createStub() {
        return new Role(Role.newId(), "", null, null, nextSortOrder());
    }

    private int nextSortOrder() {
        return roleRepository.findAll().stream()
                .mapToInt(Role::sortOrder)
                .max()
                .orElse(-SORT_ORDER_STEP) + SORT_ORDER_STEP;
    }

    List<String> csvHeader() {
        return List.of("id", "name", "minAge", "maxAge", "sortOrder");
    }

    List<String> toCsvRow(Role role) {
        return List.of(
                role.id(),
                role.name(),
                role.minAge() == null ? "" : role.minAge().toString(),
                role.maxAge() == null ? "" : role.maxAge().toString(),
                String.valueOf(role.sortOrder()));
    }

    /** Blank name rows are skipped; a blank id gets a fresh one, matching {@link #createStub()}. */
    Role fromCsvRow(List<String> row) {
        String name = CsvFields.at(row, 1);
        if (name.isEmpty()) {
            return null;
        }
        String id = CsvFields.at(row, 0);
        Integer sortOrder = CsvFields.parseInt(CsvFields.at(row, 4));
        return new Role(
                id.isEmpty() ? Role.newId() : id,
                name,
                CsvFields.parseInt(CsvFields.at(row, 2)),
                CsvFields.parseInt(CsvFields.at(row, 3)),
                sortOrder == null ? nextSortOrder() : sortOrder);
    }
}
