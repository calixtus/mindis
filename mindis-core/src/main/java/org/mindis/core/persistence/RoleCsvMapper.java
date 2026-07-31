package org.mindis.core.persistence;

import java.util.List;

import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import org.mindis.core.model.Role;

/// CSV row mapping for [Role], shared by every consumer that offers
/// Roles import/export (currently the GUI's Roles module; PLAN.md's future
/// web module gets the same for free).
@NullMarked
public final class RoleCsvMapper implements CsvRowMapper<Role> {

    private final RoleRepository roleRepository;

    public RoleCsvMapper(RoleRepository roleRepository) {
        this.roleRepository = roleRepository;
    }

    @Override
    public List<String> header() {
        return List.of("id", "name", "minAge", "maxAge", "sortOrder");
    }

    @Override
    public List<String> toRow(Role role) {
        return List.of(
                role.id(),
                role.name(),
                role.minAge() == null ? "" : role.minAge().toString(),
                role.maxAge() == null ? "" : role.maxAge().toString(),
                String.valueOf(role.sortOrder()));
    }

    /// Blank name rows are skipped; a blank id gets a fresh one.
    @Override
    public @Nullable Role fromRow(List<String> row) {
        String name = CsvFields.at(row, 1);
        if (name.isEmpty()) {
            return null;
        }
        String id = CsvFields.at(row, 0);
        Integer sortOrder = CsvFields.parseInt(CsvFields.at(row, 4));
        Integer minAge = CsvFields.parseInt(CsvFields.at(row, 2));
        Integer maxAge = CsvFields.parseInt(CsvFields.at(row, 3));
        // Role rejects an inverted range. A hand-edited file is exactly where
        // one shows up, and this importer is best-effort per row (see
        // CsvFields), so clamp rather than let one bad row abort the import.
        if (minAge != null && maxAge != null && minAge > maxAge) {
            maxAge = minAge;
        }
        return new Role(
                id.isEmpty() ? Role.newId() : id,
                name,
                minAge,
                maxAge,
                sortOrder == null ? roleRepository.nextSortOrder() : sortOrder);
    }
}
