package org.mindis.core.planning;

import ai.timefold.solver.core.api.domain.solution.PlanningEntityCollectionProperty;
import ai.timefold.solver.core.api.domain.solution.PlanningScore;
import ai.timefold.solver.core.api.domain.solution.PlanningSolution;
import ai.timefold.solver.core.api.domain.solution.ProblemFactCollectionProperty;
import ai.timefold.solver.core.api.domain.valuerange.ValueRangeProvider;
import ai.timefold.solver.core.api.score.HardMediumSoftScore;

import java.util.List;

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

    @PlanningScore
    private HardMediumSoftScore score;

    public ServicePlan() {
        // Required by Timefold.
    }

    public ServicePlan(List<Server> servers, List<Assignment> assignments) {
        this.servers = servers;
        this.assignments = assignments;
    }

    public List<Server> getServers() {
        return servers;
    }

    public List<Assignment> getAssignments() {
        return assignments;
    }

    public HardMediumSoftScore getScore() {
        return score;
    }

    public void setScore(HardMediumSoftScore score) {
        this.score = score;
    }
}
