package org.mindis.core.model;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;
import java.util.UUID;

import org.jspecify.annotations.Nullable;

/// One physical role slot of a {@link LiturgicalService} - unlike
/// {@link RoleSlot} (a role/count requirement on a {@link ServiceTemplate}),
/// each {@code Slot} is its own individually-identified instance, one per
/// server that actually needs to be assigned. The id is what lets a saved
/// assignment survive a slot-count edit: a role's count shrinking removes a
/// specific slot id, not a position, so an already-filled slot is never
/// dropped just because it happened to sit at a now out-of-range index.
///
/// <p>The assignment lives directly on the slot: {@code serverId} is the
/// {@link Server#id()} filling it ({@code null} = still open), {@code pinned}
/// marks a manual decision the solver must not move. This is the whole plan -
/// there is no separate range-keyed plan structure; a service simply carries
/// its own assignments, so saving a service saves its plan. {@code serverId}/
/// {@code pinned} were added after the field-less original release; a slot
/// written before they existed deserializes {@code serverId} as {@code null}
/// (open) and {@code pinned} as {@code false}.
public record Slot(String id, String role, @Nullable String serverId, boolean pinned) {

    public static String newId() {
        return UUID.randomUUID().toString();
    }

    /// An open slot for {@code role} (no server, not pinned) with a fresh id.
    public static Slot open(String role) {
        return new Slot(newId(), role, null, false);
    }

    /// This slot filled by {@code serverId} (or cleared when {@code null}),
    /// carrying {@code pinned} - keeps the same id so any UI/lookup keyed on it
    /// stays stable across a pick.
    public Slot withServer(@Nullable String serverId, boolean pinned) {
        return new Slot(id, role, serverId, pinned && serverId != null);
    }

    /// Expands a role/count requirement (as edited on a template) into
    /// individually-identified open instances, each with a fresh id - the
    /// concrete-instance side of the aggregate/instance split with
    /// {@link RoleSlot}. Used when a template's slots are copied onto a new
    /// {@link LiturgicalService} (see {@code ServiceGenerator}); a template's
    /// own slot counts carry no per-instance ids or assignments to preserve.
    public static List<Slot> expand(List<RoleSlot> roleSlots) {
        List<Slot> slots = new ArrayList<>();
        for (RoleSlot roleSlot : roleSlots) {
            for (int i = 0; i < roleSlot.count(); i++) {
                slots.add(Slot.open(roleSlot.role()));
            }
        }
        return slots;
    }

    /// The inverse of {@link #expand} - collapses individual instances back
    /// into a role/count requirement list (ids and assignments are discarded;
    /// they are meaningful only per-instance, not as a shape). Used wherever a
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

    /// True if {@code a} and {@code b} carry the same assignments, ignoring
    /// list order and individual slot ids - the "unchanged" notion for
    /// dirty-tracking a service's slots. Compares the multiset of
    /// (role, serverId, pinned), so both a role-count edit and an assignment
    /// pick read as a change, while pure id churn (reconciling a count edit
    /// back into fresh slot instances) does not.
    public static boolean sameSlots(List<Slot> a, List<Slot> b) {
        return normalized(a).equals(normalized(b));
    }

    private static Map<Assignment, Integer> normalized(List<Slot> slots) {
        Map<Assignment, Integer> counts = new TreeMap<>();
        for (Slot slot : slots) {
            counts.merge(new Assignment(slot.role(), slot.serverId(), slot.pinned()), 1, Integer::sum);
        }
        return counts;
    }

    /// Order/id-independent key for {@link #sameSlots}: what a slot means for
    /// dirty tracking, minus its instance id.
    private record Assignment(String role, @Nullable String serverId, boolean pinned)
            implements Comparable<Assignment> {
        @Override
        public int compareTo(Assignment other) {
            int byRole = role.compareTo(other.role);
            if (byRole != 0) {
                return byRole;
            }
            int byServer = Objects.compare(serverId, other.serverId,
                    java.util.Comparator.nullsFirst(String::compareTo));
            if (byServer != 0) {
                return byServer;
            }
            return Boolean.compare(pinned, other.pinned);
        }
    }
}
