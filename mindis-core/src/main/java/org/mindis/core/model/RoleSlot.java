package org.mindis.core.model;

import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * How many servers of a given role a service requires. {@code role} is the
 * {@link Role#id()} (the JSON field name stays {@code role} so pre-existing
 * data written with the former enum names still deserializes).
 */
public record RoleSlot(String role, int count) {

    public RoleSlot {
        if (count < 0) {
            throw new IllegalArgumentException("count must not be negative");
        }
    }

    /**
     * True if {@code a} and {@code b} carry the same (role, count) pairs,
     * ignoring list order and zero-count entries - the "unchanged" notion for
     * dirty-tracking slot lists, where order isn't semantically significant.
     */
    public static boolean sameSlots(List<RoleSlot> a, List<RoleSlot> b) {
        return normalized(a).equals(normalized(b));
    }

    private static Map<String, Integer> normalized(List<RoleSlot> slots) {
        Map<String, Integer> byRole = new TreeMap<>();
        for (RoleSlot slot : slots) {
            if (slot.count() > 0) {
                byRole.put(slot.role(), slot.count());
            }
        }
        return byRole;
    }
}
