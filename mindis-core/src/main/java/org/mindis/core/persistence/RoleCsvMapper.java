package org.mindis.core.persistence;

import java.util.List;

import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import org.mindis.core.model.Role;

/**
 * CSV row mapping for {@link Role}, shared by every consumer that offers
 * Roles import/export (currently the GUI's Roles module; PLAN.md's future
 * web module gets the same for free).
 */
@NullMarked
public final class RoleCsvMapper {

    private final RoleRepository roleRepository;

    public RoleCsvMapper(RoleRepository roleRepository) {
        this.roleRepository = roleRepository;
    }

    public List<String> header() {
        return List.of("id", "name", "minAge", "maxAge", "sortOrder");
    }

    public List<String> toRow(Role role) {
        return List.of(
                role.id(),
                role.name(),
                role.minAge() == null ? "" : role.minAge().toString(),
                role.maxAge() == null ? "" : role.maxAge().toString(),
                String.valueOf(role.sortOrder()));
    }

    /** Blank name rows are skipped; a blank id gets a fresh one. */
    public @Nullable Role fromRow(List<String> row) {
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
                sortOrder == null ? roleRepository.nextSortOrder() : sortOrder);
    }
}
