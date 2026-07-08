package org.mindis.core.persistence;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mindis.core.planning.AcceptedPlan;

class PlanRepositoryTest {

    @TempDir
    java.nio.file.Path tempDir;

    @Test
    void mostRecentBeforeFindsPlanEndingBeforeDate() {
        PlanRepository repository = new PlanRepository(tempDir.resolve("plan.json"));
        AcceptedPlan july = new AcceptedPlan(
                LocalDate.of(2026, 7, 1), LocalDate.of(2026, 7, 31), List.of());
        repository.save(july);

        assertEquals(july, repository.mostRecentBefore(LocalDate.of(2026, 8, 1)).orElseThrow());
    }

    @Test
    void mostRecentBeforeIgnoresPlanEndingOnOrAfterDate() {
        PlanRepository repository = new PlanRepository(tempDir.resolve("plan.json"));
        AcceptedPlan august = new AcceptedPlan(
                LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 31), List.of());
        repository.save(august);

        // toInclusive itself, and anything still inside the stored plan's
        // own range, must not count as "before" it - that would make the
        // current plan its own prior plan when re-solving the same period.
        assertTrue(repository.mostRecentBefore(LocalDate.of(2026, 8, 31)).isEmpty());
        assertTrue(repository.mostRecentBefore(LocalDate.of(2026, 8, 15)).isEmpty());
    }

    @Test
    void mostRecentBeforeEmptyWhenNothingSaved() {
        PlanRepository repository = new PlanRepository(tempDir.resolve("plan.json"));
        assertTrue(repository.mostRecentBefore(LocalDate.of(2026, 8, 1)).isEmpty());
    }
}
