package org.mindis.core.persistence;

import com.fasterxml.jackson.core.type.TypeReference;

import jakarta.inject.Singleton;

import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.mindis.core.planning.AcceptedPlan;
import org.mindis.core.preferences.DataDirectory;

/**
 * Stores the one accepted plan as plan.json in the user data directory.
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

    public synchronized Optional<AcceptedPlan> load() {
        return store.load().stream().findFirst();
    }

    public synchronized void save(AcceptedPlan plan) {
        store.save(List.of(plan));
    }

    /**
     * The most recently saved plan that ended before {@code date}, if any -
     * used by {@link org.mindis.core.planning.PlanningService} to seed
     * {@link org.mindis.core.planning.PriorAssignment} facts so the solver
     * can see across a plan boundary. Only the single stored plan exists yet
     * (single-slot store, see class doc); once archiving keeps plan history
     * this should search the full archive for the latest match instead.
     */
    public synchronized Optional<AcceptedPlan> mostRecentBefore(LocalDate date) {
        return load().filter(plan -> plan.toInclusive().isBefore(date));
    }
}
