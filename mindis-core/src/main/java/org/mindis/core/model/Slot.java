package org.mindis.core.model;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;

/// One physical role slot of a {@link LiturgicalService} - unlike
/// {@link RoleSlot} (a role/count requirement on a {@link ServiceTemplate}),
/// each {@code Slot} is its own individually-identified instance, one per
/// server that actually needs to be assigned. The id is what lets a saved
/// assignment (see {@code org.mindis.core.planning.Assignment}) survive a
/// slot-count edit: a role's count shrinking removes a specific slot id, not
/// a position, so an already-filled slot is never dropped just because it
/// happened to sit at a now out-of-range index.
public record Slot(String id, String role) {

    public static String newId() {
        return UUID.randomUUID().toString();
    }

    /// Expands a role/count requirement (as edited on a template) into
    /// individually-identified instances, each with a fresh id - the
    /// concrete-instance side of the aggregate/instance split with
    /// {@link RoleSlot}. Used when a template's slots are copied onto a new
    /// {@link LiturgicalService} (see {@code ServiceGenerator}); a
    /// template's own slot counts carry no per-instance ids to preserve, so
    /// there is nothing to reuse.
    public static List<Slot> expand(List<RoleSlot> roleSlots) {
        List<Slot> slots = new ArrayList<>();
        for (RoleSlot roleSlot : roleSlots) {
            for (int i = 0; i < roleSlot.count(); i++) {
                slots.add(new Slot(newId(), roleSlot.role()));
            }
        }
        return slots;
    }

    /// The inverse of {@link #expand} - collapses individual instances back
    /// into a role/count requirement list (ids are discarded; they are
    /// meaningful only per-instance, not as a shape). Used wherever a
    /// concrete service's slots need to round-trip through a role/count
    /// representation, e.g. CSV export (see {@code ServiceCsvMapper}).
    public static List<RoleSlot> collapse(List<Slot> slots) {
        Map<String, Integer> counts = new TreeMap<>();
        for (Slot slot : slots) {
            counts.merge(slot.role(), 1, Integer::sum);
        }
        List<RoleSlot> roleSlots = new ArrayList<>();
        counts.forEach((role, count) -> roleSlots.add(new RoleSlot(role, count)));
        return roleSlots;
    }

    /// True if {@code a} and {@code b} carry the same (role, count)
    /// multiset, ignoring list order and individual slot ids - the
    /// "unchanged" notion for dirty-tracking a service's slots, mirroring
    /// {@link RoleSlot#sameSlots}: reconciling a count edit back into slot
    /// instances (see {@code ServicesModule}) mints/drops ids even when the
    /// resulting shape is identical, and that id churn alone should not read
    /// as an unsaved change.
    public static boolean sameSlots(List<Slot> a, List<Slot> b) {
        return normalized(a).equals(normalized(b));
    }

    private static Map<String, Integer> normalized(List<Slot> slots) {
        Map<String, Integer> byRole = new TreeMap<>();
        for (Slot slot : slots) {
            byRole.merge(slot.role(), 1, Integer::sum);
        }
        return byRole;
    }
}
