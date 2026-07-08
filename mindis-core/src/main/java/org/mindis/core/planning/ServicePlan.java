package org.mindis.core.planning;

import ai.timefold.solver.core.api.domain.solution.ConstraintWeightOverrides;
import ai.timefold.solver.core.api.domain.solution.PlanningEntityCollectionProperty;
import ai.timefold.solver.core.api.domain.solution.PlanningScore;
import ai.timefold.solver.core.api.domain.solution.PlanningSolution;
import ai.timefold.solver.core.api.domain.solution.ProblemFactCollectionProperty;
import ai.timefold.solver.core.api.domain.valuerange.ValueRangeProvider;
import ai.timefold.solver.core.api.score.HardMediumSoftScore;

import java.util.List;

import org.jspecify.annotations.Nullable;

import org.mindis.core.model.Server;

/**
 * Planning solution: all assignments of a horizon plus the servers available
 * to fill them.
 */
@PlanningSolution
public class ServicePlan {

    @ValueRangeProvider
    @ProblemFactCollectionProperty
    private List<Server> servers;

    @PlanningEntityCollectionProperty
    private List<Assignment> assignments;

    // Detected by field type: user-tunable soft constraint weights.
    private ConstraintWeightOverrides<HardMediumSoftScore> constraintWeightOverrides =
            ConstraintWeightOverrides.none();

    @PlanningScore
    private @Nullable HardMediumSoftScore score;

    @SuppressWarnings("NullAway.Init") // Timefold requires this constructor; it populates servers/assignments by reflection.
    public ServicePlan() {
    }

    public ServicePlan(List<Server> servers, List<Assignment> assignments) {
        this.servers = servers;
        this.assignments = assignments;
    }

    public ConstraintWeightOverrides<HardMediumSoftScore> getConstraintWeightOverrides() {
        return constraintWeightOverrides;
    }

    public void setConstraintWeightOverrides(ConstraintWeightOverrides<HardMediumSoftScore> overrides) {
        this.constraintWeightOverrides = overrides;
    }

    public List<Server> getServers() {
        return servers;
    }

    public List<Assignment> getAssignments() {
        return assignments;
    }

    public @Nullable HardMediumSoftScore getScore() {
        return score;
    }

    public void setScore(@Nullable HardMediumSoftScore score) {
        this.score = score;
    }
}
