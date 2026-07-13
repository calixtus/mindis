package org.mindis.core.persistence;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.core.type.TypeReference;

import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mindis.core.planning.AcceptedPlan;

class PlanRepositoryTest {

    @TempDir
    java.nio.file.Path tempDir;

    private static AcceptedPlan plan(LocalDate from, LocalDate toInclusive) {
        return new AcceptedPlan(from, toInclusive, List.of(), null, false, null);
    }

    @Test
    void saveStampsSavedAt() {
        PlanRepository repository = new PlanRepository(tempDir.resolve("plan.json"));
        repository.save(plan(LocalDate.of(2026, 7, 1), LocalDate.of(2026, 7, 31)));

        assertNotNull(repository.load().orElseThrow().savedAt());
    }

    @Test
    void loadReturnsTheOpenPlan() {
        PlanRepository repository = new PlanRepository(tempDir.resolve("plan.json"));
        repository.save(plan(LocalDate.of(2026, 7, 1), LocalDate.of(2026, 7, 31)));
        repository.save(plan(LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 31)));

        assertEquals(LocalDate.of(2026, 8, 31), repository.load().orElseThrow().toInclusive());
    }

    @Test
    void savingADifferentPeriodReplacesTheOpenPlanRatherThanArchivingIt() {
        // Plain save() no longer auto-archives the previous period - under
        // the explicit open/archived model, only applyArchiveSplit does.
        PlanRepository repository = new PlanRepository(tempDir.resolve("plan.json"));
        repository.save(plan(LocalDate.of(2026, 7, 1), LocalDate.of(2026, 7, 31)));
        repository.save(plan(LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 31)));

        assertTrue(repository.listArchived().isEmpty());
        assertEquals(LocalDate.of(2026, 8, 31), repository.load().orElseThrow().toInclusive());
    }

    @Test
    void savingSamePeriodAgainReplacesInPlaceRatherThanArchiving() {
        PlanRepository repository = new PlanRepository(tempDir.resolve("plan.json"));
        repository.save(plan(LocalDate.of(2026, 7, 1), LocalDate.of(2026, 7, 31)));
        repository.save(plan(LocalDate.of(2026, 7, 1), LocalDate.of(2026, 7, 31)));

        assertTrue(repository.listArchived().isEmpty());
    }

    @Test
    void applyArchiveSplitFreezesThePortionUpToCutoffAndKeepsTheRemainderOpen() {
        PlanRepository repository = new PlanRepository(tempDir.resolve("plan.json"));
        AcceptedPlan archivedPortion = plan(LocalDate.of(2026, 7, 1), LocalDate.of(2026, 7, 15));
        AcceptedPlan remainder = plan(LocalDate.of(2026, 7, 16), LocalDate.of(2026, 7, 31));

        repository.applyArchiveSplit(archivedPortion, remainder);

        List<AcceptedPlan> archived = repository.listArchived();
        assertEquals(1, archived.size());
        assertEquals(LocalDate.of(2026, 7, 15), archived.getFirst().toInclusive());
        assertTrue(archived.getFirst().archived());
        assertNotNull(archived.getFirst().archivedAt());

        AcceptedPlan open = repository.load().orElseThrow();
        assertEquals(LocalDate.of(2026, 7, 16), open.from());
        assertTrue(!open.archived());
    }

    @Test
    void applyArchiveSplitWithNullRemainderLeavesNoOpenPlan() {
        PlanRepository repository = new PlanRepository(tempDir.resolve("plan.json"));
        AcceptedPlan archivedPortion = plan(LocalDate.of(2026, 7, 1), LocalDate.of(2026, 7, 31));

        repository.applyArchiveSplit(archivedPortion, null);

        assertTrue(repository.load().isEmpty());
        assertEquals(1, repository.listArchived().size());
    }

    @Test
    void allOverlappingFindsBothOpenAndArchivedPlansIntersectingTheWindow() {
        PlanRepository repository = new PlanRepository(tempDir.resolve("plan.json"));
        repository.applyArchiveSplit(
                plan(LocalDate.of(2026, 6, 1), LocalDate.of(2026, 6, 30)),
                plan(LocalDate.of(2026, 7, 1), LocalDate.of(2026, 7, 31)));

        List<AcceptedPlan> overlapping = repository.allOverlapping(
                LocalDate.of(2026, 6, 15), LocalDate.of(2026, 7, 15));

        assertEquals(2, overlapping.size());
    }

    @Test
    void mostRecentBeforeFindsPlanEndingBeforeDate() {
        PlanRepository repository = new PlanRepository(tempDir.resolve("plan.json"));
        repository.save(plan(LocalDate.of(2026, 7, 1), LocalDate.of(2026, 7, 31)));

        assertEquals(LocalDate.of(2026, 7, 31),
                repository.mostRecentBefore(LocalDate.of(2026, 8, 1)).orElseThrow().toInclusive());
    }

    @Test
    void mostRecentBeforeIgnoresPlanEndingOnOrAfterDate() {
        PlanRepository repository = new PlanRepository(tempDir.resolve("plan.json"));
        repository.save(plan(LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 31)));

        // toInclusive itself, and anything still inside the stored plan own
        // range, must not count as "before" it - that would make the
        // current plan its own prior plan when re-solving the same period.
        assertTrue(repository.mostRecentBefore(LocalDate.of(2026, 8, 31)).isEmpty());
        assertTrue(repository.mostRecentBefore(LocalDate.of(2026, 8, 15)).isEmpty());
    }

    @Test
    void mostRecentBeforeSearchesArchivedPlansTooNotJustTheOpenOne() {
        PlanRepository repository = new PlanRepository(tempDir.resolve("plan.json"));
        repository.applyArchiveSplit(
                plan(LocalDate.of(2026, 7, 1), LocalDate.of(2026, 7, 31)),
                plan(LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 31)));

        // Solving August again: the most recent plan strictly before it is
        // July, even though July is archived and August is the open one -
        // the search must not stop at the open plan.
        assertEquals(LocalDate.of(2026, 7, 31),
                repository.mostRecentBefore(LocalDate.of(2026, 8, 1)).orElseThrow().toInclusive());
    }

    @Test
    void mostRecentBeforeEmptyWhenNothingSaved() {
        PlanRepository repository = new PlanRepository(tempDir.resolve("plan.json"));
        assertTrue(repository.mostRecentBefore(LocalDate.of(2026, 8, 1)).isEmpty());
    }

    @Test
    void constructingNormalizesLegacyMultiUnarchivedDataOnce() {
        // Simulate a pre-upgrade plan.json: several plans, all archived=false
        // (the field didn't exist), written straight to disk bypassing save()
        // (which would maintain the one-open invariant).
        java.nio.file.Path file = tempDir.resolve("plan.json");
        JsonStore<AcceptedPlan> raw = new JsonStore<>(file, new TypeReference<>() {
        });
        raw.save(List.of(
                plan(LocalDate.of(2026, 6, 1), LocalDate.of(2026, 6, 30)),
                plan(LocalDate.of(2026, 7, 1), LocalDate.of(2026, 7, 31)),
                plan(LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 31))));

        PlanRepository repository = new PlanRepository(file);

        // The latest period stays open; the two earlier ones are now archived.
        assertEquals(LocalDate.of(2026, 8, 31), repository.load().orElseThrow().toInclusive());
        assertEquals(2, repository.listArchived().size());

        // Idempotent: a fresh repository over the now-normalized file changes nothing.
        PlanRepository again = new PlanRepository(file);
        assertEquals(2, again.listArchived().size());
        assertEquals(LocalDate.of(2026, 8, 31), again.load().orElseThrow().toInclusive());
    }
}
