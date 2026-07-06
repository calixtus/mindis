package org.mindis.core.planning;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.mindis.core.model.Server;

/**
 * Pure conversions between the live {@link ServicePlan} and the persisted
 * {@link AcceptedPlan}. Dependency-free by design (SOLID/DIP): callers do not
 * need a solver-backed service for plain mapping.
 */
public final class PlanMapper {

    private PlanMapper() {
    }

    public static AcceptedPlan toAcceptedPlan(ServicePlan plan, LocalDate from, LocalDate toInclusive) {
        List<AcceptedPlan.PlannedAssignment> planned = plan.getAssignments().stream()
                .map(assignment -> new AcceptedPlan.PlannedAssignment(
                        assignment.getId(),
                        assignment.getService().id(),
                        assignment.getRole().id(),
                        assignment.getServer() == null ? null : assignment.getServer().id(),
                        assignment.isPinned()))
                .toList();
        return new AcceptedPlan(from, toInclusive, planned);
    }

    /**
     * Re-applies a persisted plan onto a freshly built problem: assigned
     * servers and pin flags are restored where the assignment ids still match
     * (deleted servers or services degrade gracefully to empty slots).
     */
    public static void applyAcceptedPlan(ServicePlan problem, AcceptedPlan acceptedPlan) {
        Map<String, Server> serversById = new HashMap<>();
        problem.getServers().forEach(server -> serversById.put(server.id(), server));
        Map<String, AcceptedPlan.PlannedAssignment> plannedById = new HashMap<>();
        acceptedPlan.assignments().forEach(planned -> plannedById.put(planned.assignmentId(), planned));

        for (Assignment assignment : problem.getAssignments()) {
            AcceptedPlan.PlannedAssignment planned = plannedById.get(assignment.getId());
            if (planned == null) {
                continue;
            }
            if (planned.serverId() != null) {
                assignment.setServer(serversById.get(planned.serverId()));
            }
            assignment.setPinned(planned.pinned() && assignment.getServer() != null);
        }
    }
}
