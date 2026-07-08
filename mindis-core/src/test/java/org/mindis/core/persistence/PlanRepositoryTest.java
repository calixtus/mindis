package org.mindis.core.persistence;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mindis.core.planning.AcceptedPlan;

class PlanRepositoryTest {

    @TempDir
    java.nio.file.Path tempDir;

    private static AcceptedPlan plan(LocalDate from, LocalDate toInclusive) {
        return new AcceptedPlan(from, toInclusive, List.of(), null);
    }

    @Test
    void saveStampsSavedAt() {
        PlanRepository repository = new PlanRepository(tempDir.resolve("plan.json"));
        repository.save(plan(LocalDate.of(2026, 7, 1), LocalDate.of(2026, 7, 31)));

        assertNotNull(repository.load().orElseThrow().savedAt());
    }

    @Test
    void loadReturnsPlanWithLatestPeriodAsActive() {
        PlanRepository repository = new PlanRepository(tempDir.resolve("plan.json"));
        repository.save(plan(LocalDate.of(2026, 7, 1), LocalDate.of(2026, 7, 31)));
        repository.save(plan(LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 31)));

        assertEquals(LocalDate.of(2026, 8, 31), repository.load().orElseThrow().toInclusive());
    }

    @Test
    void savingNewPeriodArchivesThePreviousOneInsteadOfDiscardingIt() {
        PlanRepository repository = new PlanRepository(tempDir.resolve("plan.json"));
        repository.save(plan(LocalDate.of(2026, 7, 1), LocalDate.of(2026, 7, 31)));
        repository.save(plan(LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 31)));

        List<AcceptedPlan> archived = repository.listArchived();
        assertEquals(1, archived.size());
        assertEquals(LocalDate.of(2026, 7, 31), archived.getFirst().toInclusive());
    }

    @Test
    void savingSamePeriodAgainReplacesInPlaceRatherThanArchiving() {
        PlanRepository repository = new PlanRepository(tempDir.resolve("plan.json"));
        repository.save(plan(LocalDate.of(2026, 7, 1), LocalDate.of(2026, 7, 31)));
        repository.save(plan(LocalDate.of(2026, 7, 1), LocalDate.of(2026, 7, 31)));

        assertTrue(repository.listArchived().isEmpty());
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

        // toInclusive itself, and anything still inside the stored plan's
        // own range, must not count as "before" it - that would make the
        // current plan its own prior plan when re-solving the same period.
        assertTrue(repository.mostRecentBefore(LocalDate.of(2026, 8, 31)).isEmpty());
        assertTrue(repository.mostRecentBefore(LocalDate.of(2026, 8, 15)).isEmpty());
    }

    @Test
    void mostRecentBeforeSearchesArchivedPlansTooNotJustTheActiveOne() {
        PlanRepository repository = new PlanRepository(tempDir.resolve("plan.json"));
        repository.save(plan(LocalDate.of(2026, 6, 1), LocalDate.of(2026, 6, 30)));
        repository.save(plan(LocalDate.of(2026, 7, 1), LocalDate.of(2026, 7, 31)));
        repository.save(plan(LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 31)));

        // Solving August again: the most recent plan strictly before it is
        // July, even though July is archived (June is active by then... no,
        // August is active) - the search must not stop at the active plan.
        assertEquals(LocalDate.of(2026, 7, 31),
                repository.mostRecentBefore(LocalDate.of(2026, 8, 1)).orElseThrow().toInclusive());
    }

    @Test
    void mostRecentBeforeEmptyWhenNothingSaved() {
        PlanRepository repository = new PlanRepository(tempDir.resolve("plan.json"));
        assertTrue(repository.mostRecentBefore(LocalDate.of(2026, 8, 1)).isEmpty());
    }
}
