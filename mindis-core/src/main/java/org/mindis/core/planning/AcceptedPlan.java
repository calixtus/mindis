package org.mindis.core.planning;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import org.jspecify.annotations.Nullable;

/**
 * A persisted planning result: which server serves which slot, and which of
 * those decisions the planner pinned manually. Stored as JSON and re-applied
 * onto a freshly built problem on restart (ids reference servers/services).
 *
 * <p>{@code savedAt} is stamped by {@link org.mindis.core.persistence.PlanRepository#save}
 * - callers building a plan for any other purpose (export, tests, re-solving)
 * pass {@code null}. Nullable rather than required so a {@code plan.json}
 * written before this field existed still deserializes (missing JSON
 * properties become {@code null} on a record, not a hard failure) instead of
 * silently discarding a user's saved plan on upgrade.
 */
public record AcceptedPlan(
        LocalDate from,
        LocalDate toInclusive,
        List<PlannedAssignment> assignments,
        @Nullable Instant savedAt) {

    public AcceptedPlan {
        assignments = List.copyOf(assignments);
    }

    /**
     * One persisted slot decision. {@code role} is the {@link
     * org.mindis.core.model.Role#id()}; {@code serverId} is {@code null} for an
     * unassigned slot.
     */
    public record PlannedAssignment(
            String assignmentId,
            String serviceId,
            String role,
            @Nullable String serverId,
            boolean pinned) {
    }
}
