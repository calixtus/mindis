package org.mindis.core.persistence;

import com.fasterxml.jackson.core.type.TypeReference;

import jakarta.inject.Singleton;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

import org.mindis.core.planning.AcceptedPlan;
import org.mindis.core.preferences.AppDirectories;

/**
 * Stores the one accepted plan as plan.json in the user data directory.
 */
@Singleton
public class PlanRepository {

    private final JsonStore<AcceptedPlan> store;

    public PlanRepository() {
        this(AppDirectories.userDataDir().resolve("plan.json"));
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
}
