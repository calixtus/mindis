package org.mindis.gui.modules;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;

import org.mindis.core.model.Slot;

/// Reconciles a role's slot count edit (from {@link SlotCountEditor}) into a
/// concrete {@code List<Slot>}, preserving each surviving slot's stable id -
/// growing a role appends fresh ids; shrinking removes ids, preferring
/// whichever slots {@code isFilled} says aren't currently backed by a filled
/// or pinned assignment, so a shrink never silently drops an assigned server
/// out from under an unrelated empty slot just because it happened to occupy
/// a now out-of-range position (see {@link Slot}'s class docs on why
/// identity matters here). Pure - no JavaFX, no {@code ServicesModule}/plan
/// dependency, {@code isFilled} is the only seam - so it's unit-testable on
/// its own.
final class SlotReconciler {

    private SlotReconciler() {
    }

    static List<Slot> reconcile(List<Slot> existing, Map<String, Integer> counts, Predicate<Slot> isFilled) {
        Map<String, List<Slot>> byRole = groupByRole(existing);
        // Existing roles keep their current order; a role newly given a
        // count by the editor (previously zero, no existing Slot instances)
        // is appended after. A role with existing slots but *omitted* from
        // counts (SlotCountEditor#collectCounts drops zero-count roles) is
        // still in byRole.keySet(), so it's still visited here - getOrDefault
        // below reads that as "wanted = 0", correctly shrinking it to nothing
        // rather than leaving its old slots untouched.
        Set<String> roleIds = new LinkedHashSet<>(byRole.keySet());
        roleIds.addAll(counts.keySet());

        List<Slot> result = new ArrayList<>();
        for (String roleId : roleIds) {
            int wanted = counts.getOrDefault(roleId, 0);
            List<Slot> current = byRole.getOrDefault(roleId, List.of());
            if (current.size() <= wanted) {
                result.addAll(current);
                for (int i = current.size(); i < wanted; i++) {
                    result.add(Slot.open(roleId));
                }
            } else {
                // Shrinking: keep the first `wanted` slots off a filled-
                // first ordering, so a filled/pinned slot is kept as long as
                // possible and an unfilled one is dropped first. Comparator
                // over a boolean sorts false before true; reversed() puts
                // isFilled() == true first.
                List<Slot> orderedByFilledFirst = current.stream()
                        .sorted(Comparator.comparing(isFilled::test).reversed())
                        .toList();
                result.addAll(orderedByFilledFirst.subList(0, wanted));
            }
        }
        return result;
    }

    private static Map<String, List<Slot>> groupByRole(List<Slot> slots) {
        Map<String, List<Slot>> byRole = new LinkedHashMap<>();
        for (Slot slot : slots) {
            byRole.computeIfAbsent(slot.role(), roleId -> new ArrayList<>()).add(slot);
        }
        return byRole;
    }
}
