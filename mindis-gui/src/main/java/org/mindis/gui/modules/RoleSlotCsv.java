package org.mindis.gui.modules;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import org.jspecify.annotations.NullMarked;

import org.mindis.core.model.Role;
import org.mindis.core.model.RoleSlot;
import org.mindis.core.persistence.RoleRepository;

/**
 * CSV encoding of a {@link RoleSlot} list ("required servers"), shared by
 * {@link ServicesModule}/{@link ServicesViewModel} and
 * {@link TemplatesModule}/{@link TemplatesViewModel}: {@code "Acolyte:2, Cross bearer:1"}
 * - role name (not id, for readability) and count, comma-separated.
 * Roles are matched by name case-insensitively on import; unmatched or
 * zero/negative counts are dropped, mirroring {@code RoleSlotsEditor}'s
 * zero-count-omitted convention.
 */
@NullMarked
final class RoleSlotCsv {

    private RoleSlotCsv() {
    }

    static String format(List<RoleSlot> slots, RoleRepository roleRepository) {
        return slots.stream()
                .map(slot -> roleName(slot.role(), roleRepository) + ":" + slot.count())
                .collect(Collectors.joining(", "));
    }

    static List<RoleSlot> parse(String text, RoleRepository roleRepository) {
        List<RoleSlot> slots = new ArrayList<>();
        if (text.isEmpty()) {
            return slots;
        }
        List<Role> roles = roleRepository.findAll();
        for (String part : text.split(",")) {
            String trimmed = part.strip();
            int colon = trimmed.lastIndexOf(':');
            if (colon < 0) {
                continue;
            }
            String name = trimmed.substring(0, colon).strip();
            Integer count = CsvFields.parseInt(trimmed.substring(colon + 1).strip());
            if (count == null || count <= 0) {
                continue;
            }
            roles.stream()
                    .filter(role -> role.name().equalsIgnoreCase(name))
                    .findFirst()
                    .ifPresent(role -> slots.add(new RoleSlot(role.id(), count)));
        }
        return slots;
    }

    private static String roleName(String roleId, RoleRepository roleRepository) {
        return roleRepository.findById(roleId).map(Role::name).orElse(roleId);
    }
}
