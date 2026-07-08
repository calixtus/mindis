package org.mindis.core.persistence;

import com.fasterxml.jackson.core.type.TypeReference;

import jakarta.inject.Singleton;

import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

import org.mindis.core.planning.AcceptedPlan;
import org.mindis.core.preferences.DataDirectory;

/**
 * Stores every accepted plan, one per distinct ({@code from}, {@code
 * toInclusive}) period, as plan.json in the user data directory. Saving a
 * plan for a period that already has one replaces that entry in place - the
 * planner iterating on the same period doesn't create archive noise - but a
 * plan for a new period is kept alongside the others rather than overwriting
 * them, so a finished period stays accessible ("archived") once the planner
 * moves on to the next one. There is no separate archived/active flag: the
 * plan with the latest {@code toInclusive} is the active one ({@link
 * #load()}); every other stored plan is, by definition, archived ({@link
 * #listArchived()}).
 */
@Singleton
public class PlanRepository {

    private final JsonStore<AcceptedPlan> store;

    public PlanRepository(DataDirectory dataDirectory) {
        this(dataDirectory.resolve("plan.json"));
    }

    PlanRepository(Path file) {
        this.store = new JsonStore<>(file, new TypeReference<>() {
        });
    }

    /** The active plan: the stored plan with the latest {@code toInclusive}, if any. */
    public synchronized Optional<AcceptedPlan> load() {
        return store.load().stream().max(Comparator.comparing(AcceptedPlan::toInclusive));
    }

    /**
     * Every stored plan except the active one ({@link #load()}), newest
     * period first - for browsing plans from periods the planner has since
     * moved past.
     */
    public synchronized List<AcceptedPlan> listArchived() {
        List<AcceptedPlan> all = store.load();
        Optional<AcceptedPlan> active = load();
        return all.stream()
                .filter(plan -> active.isEmpty() || !isSamePeriod(plan, active.get()))
                .sorted(Comparator.comparing(AcceptedPlan::toInclusive).reversed())
                .toList();
    }

    /**
     * Saves {@code plan}, stamping {@link AcceptedPlan#savedAt()} with the
     * current time regardless of what {@code plan} already carried there.
     * Replaces any existing entry for the same period; plans for other
     * periods are kept.
     */
    public synchronized void save(AcceptedPlan plan) {
        List<AcceptedPlan> all = new ArrayList<>(store.load());
        all.removeIf(existing -> isSamePeriod(existing, plan));
        all.add(new AcceptedPlan(plan.from(), plan.toInclusive(), plan.assignments(), Instant.now()));
        store.save(all);
    }

    /**
     * The stored plan with the latest {@code toInclusive} strictly before
     * {@code date}, if any - used by {@link
     * org.mindis.core.planning.PlanningService} to seed {@link
     * org.mindis.core.planning.PriorAssignment} facts so the solver can see
     * across a plan boundary, regardless of whether that plan is still the
     * active one or has since been archived.
     */
    public synchronized Optional<AcceptedPlan> mostRecentBefore(LocalDate date) {
        return store.load().stream()
                .filter(plan -> plan.toInclusive().isBefore(date))
                .max(Comparator.comparing(AcceptedPlan::toInclusive));
    }

    private static boolean isSamePeriod(AcceptedPlan a, AcceptedPlan b) {
        return a.from().equals(b.from()) && a.toInclusive().equals(b.toInclusive());
    }
}
