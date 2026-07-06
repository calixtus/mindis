package org.mindis.core.planning;

import java.time.LocalDate;
import java.util.List;

import org.mindis.core.model.Role;

/**
 * A persisted planning result: which server serves which slot, and which of
 * those decisions the planner pinned manually. Stored as JSON and re-applied
 * onto a freshly built problem on restart (ids reference servers/services).
 */
public record AcceptedPlan(
        LocalDate from,
        LocalDate toInclusive,
        List<PlannedAssignment> assignments) {

    public AcceptedPlan {
        assignments = List.copyOf(assignments);
    }

    /**
     * One persisted slot decision. {@code serverId} is {@code null} for an
     * unassigned slot.
     */
    public record PlannedAssignment(
            String assignmentId,
            String serviceId,
            Role role,
            String serverId,
            boolean pinned) {
    }
}
