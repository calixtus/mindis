package org.mindis.core.planning;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.Test;

/// Unit tests for the pure archive-split partitioning.
class PlanArchiverTest {

    private static AcceptedPlan.PlannedAssignment assignment(String serviceId) {
        return new AcceptedPlan.PlannedAssignment(serviceId + ":slot", serviceId, "ACOLYTE", "server-1", false);
    }

    private static AcceptedPlan openPlanWith(String... serviceIds) {
        return new AcceptedPlan(LocalDate.of(2026, 7, 1), LocalDate.of(2026, 7, 31),
                List.of(java.util.Arrays.stream(serviceIds).map(PlanArchiverTest::assignment).toArray(
                        AcceptedPlan.PlannedAssignment[]::new)),
                null, false, null);
    }

    @Test
    void partitionsAssignmentsByServiceDateAroundCutoff() {
        Map<String, LocalDate> dates = Map.of(
                "svc-early", LocalDate.of(2026, 7, 5),
                "svc-onCutoff", LocalDate.of(2026, 7, 15),
                "svc-late", LocalDate.of(2026, 7, 25));
        AcceptedPlan open = openPlanWith("svc-early", "svc-onCutoff", "svc-late");

        PlanArchiver.Split split = PlanArchiver.split(open, LocalDate.of(2026, 7, 15),
                id -> Optional.ofNullable(dates.get(id)));

        // On-or-before the cutoff is archived; strictly after is the remainder.
        assertTrue(split.archived().isPresent());
        assertEquals(2, split.archived().get().assignments().size());
        assertTrue(split.archived().get().archived());
        assertEquals(LocalDate.of(2026, 7, 1), split.archived().get().from());
        assertEquals(LocalDate.of(2026, 7, 15), split.archived().get().toInclusive());

        assertTrue(split.remainder().isPresent());
        assertEquals(1, split.remainder().get().assignments().size());
        assertFalse(split.remainder().get().archived());
        assertEquals(LocalDate.of(2026, 7, 16), split.remainder().get().from());
        assertEquals(LocalDate.of(2026, 7, 31), split.remainder().get().toInclusive());
    }

    @Test
    void deletedServiceAssignmentStaysOnTheRemainderSide() {
        Map<String, LocalDate> dates = Map.of("svc-known", LocalDate.of(2026, 7, 5));
        AcceptedPlan open = openPlanWith("svc-known", "svc-deleted");

        PlanArchiver.Split split = PlanArchiver.split(open, LocalDate.of(2026, 7, 31),
                id -> Optional.ofNullable(dates.get(id)));

        // svc-known (dated, before cutoff) archives; svc-deleted (undatable)
        // is left editable on the remainder rather than frozen by a guess.
        assertTrue(split.archived().isPresent());
        assertEquals(1, split.archived().get().assignments().size());
        assertTrue(split.remainder().isPresent());
        assertEquals(1, split.remainder().get().assignments().size());
        assertEquals("svc-deleted", split.remainder().get().assignments().getFirst().serviceId());
    }

    @Test
    void cutoffBeforeEveryServiceLeavesNothingToArchive() {
        Map<String, LocalDate> dates = Map.of("svc", LocalDate.of(2026, 7, 20));
        AcceptedPlan open = openPlanWith("svc");

        PlanArchiver.Split split = PlanArchiver.split(open, LocalDate.of(2026, 7, 1),
                id -> Optional.ofNullable(dates.get(id)));

        assertTrue(split.archived().isEmpty());
        assertTrue(split.remainder().isPresent());
    }
}
