package org.mindis.gui.modules;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;

import org.mindis.core.model.Slot;

/// Unit tests for the actual bug fix behind giving {@link Slot} its own
/// stable id: a role's slot count shrinking must never drop a filled/pinned
/// slot just because it happened to occupy a now out-of-range position - it
/// must prefer dropping an unfilled one. No JavaFX, no {@code ServicesModule}
/// construction needed - {@link SlotReconciler} is pure.
class SlotReconcilerTest {

    private static final String ACOLYTE = "acolyte";
    private static final String THURIFER = "thurifer";

    @Test
    void growingAppendsFreshSlotsAndKeepsExistingIdsUntouched() {
        Slot existingSlot = new Slot("s1", ACOLYTE);

        List<Slot> result = SlotReconciler.reconcile(List.of(existingSlot), Map.of(ACOLYTE, 2), slot -> false);

        assertEquals(2, result.size());
        assertTrue(result.contains(existingSlot), "the existing slot's id must survive a grow, not be replaced");
        assertTrue(result.stream().anyMatch(slot -> slot.role().equals(ACOLYTE) && !slot.id().equals("s1")),
                "a fresh slot must be appended for the new count");
    }

    @Test
    void newRoleWithNoExistingSlotsCreatesTheRequestedCount() {
        List<Slot> result = SlotReconciler.reconcile(List.of(), Map.of(ACOLYTE, 3), slot -> false);

        assertEquals(3, result.size());
        assertTrue(result.stream().allMatch(slot -> slot.role().equals(ACOLYTE)));
        // Every generated id must be distinct.
        assertEquals(3, result.stream().map(Slot::id).collect(Collectors.toSet()).size());
    }

    @Test
    void shrinkingDropsAnUnfilledSlotBeforeAFilledOne() {
        Slot filled = new Slot("filled", ACOLYTE);
        Slot empty = new Slot("empty", ACOLYTE);
        // "empty" sits at index 1, after "filled" at index 0 - index alone
        // would have no reason to prefer dropping it, isFilled must decide.
        List<Slot> existing = List.of(filled, empty);

        List<Slot> result = SlotReconciler.reconcile(existing, Map.of(ACOLYTE, 1),
                slot -> slot.id().equals("filled"));

        assertEquals(List.of(filled), result, "the filled slot must be the one that survives");
    }

    @Test
    void shrinkingToZeroDropsEverySlotRegardlessOfFilled() {
        Slot filled = new Slot("filled", ACOLYTE);
        Slot empty = new Slot("empty", ACOLYTE);

        List<Slot> result = SlotReconciler.reconcile(List.of(filled, empty), Map.of(ACOLYTE, 0),
                slot -> slot.id().equals("filled"));

        assertTrue(result.isEmpty());
    }

    @Test
    void roleOmittedFromCountsShrinksToZero() {
        // SlotCountEditor#collectCounts omits zero-count roles entirely -
        // a role with existing slots but no entry in counts must still
        // shrink to nothing, not be left untouched.
        Slot existingSlot = new Slot("s1", ACOLYTE);

        List<Slot> result = SlotReconciler.reconcile(List.of(existingSlot), Map.of(), slot -> false);

        assertTrue(result.isEmpty());
    }

    @Test
    void unaffectedRolesAndOrderSurviveAMixedEdit() {
        Slot acolyte1 = new Slot("a1", ACOLYTE);
        Slot acolyte2 = new Slot("a2", ACOLYTE);
        Slot thurifer1 = new Slot("t1", THURIFER);
        List<Slot> existing = List.of(acolyte1, acolyte2, thurifer1);

        // Shrink Acolyte 2 -> 1 (unfilled a2 should go), leave Thurifer at 1.
        List<Slot> result = SlotReconciler.reconcile(existing, Map.of(ACOLYTE, 1, THURIFER, 1),
                slot -> slot.id().equals("a1"));

        assertEquals(Set.of(acolyte1, thurifer1), Set.copyOf(result));
    }
}
