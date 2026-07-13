package org.mindis.core.planning;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import org.jspecify.annotations.Nullable;

/// A persisted planning result: which server serves which slot, and which of
/// those decisions the planner pinned manually. Stored as JSON and re-applied
/// onto a freshly built problem on restart (ids reference servers/services).
///
/// <p>At most one stored plan is ever "open" ({@code archived == false}) at a
/// time - the one the planner is still actively working on. Every other
/// stored plan is archived: frozen the moment {@link
/// org.mindis.core.persistence.PlanRepository#applyArchiveSplit} sets {@code
/// archived}, never mutated again. {@code archived}/{@code archivedAt} were
/// added after the field-less original release; a {@code plan.json} written
/// before they existed deserializes {@code archived} as {@code false} (every
/// stored plan reads as "open") and {@code archivedAt} as {@code null} -
/// {@link org.mindis.core.persistence.PlanRepository} normalizes that
/// automatically on first read after upgrade (see its class docs) rather than
/// leaving every pre-upgrade period stuck unarchived.
///
/// <p>{@code savedAt} is stamped by {@link org.mindis.core.persistence.PlanRepository#save}
/// - callers building a plan for any other purpose (export, tests, re-solving)
/// pass {@code null}. Nullable rather than required so a {@code plan.json}
/// written before this field existed still deserializes (missing JSON
/// properties become {@code null} on a record, not a hard failure) instead of
/// silently discarding a user's saved plan on upgrade.
public record AcceptedPlan(
        LocalDate from,
        LocalDate toInclusive,
        List<PlannedAssignment> assignments,
        @Nullable Instant savedAt,
        boolean archived,
        @Nullable Instant archivedAt) {

    public AcceptedPlan {
        assignments = List.copyOf(assignments);
    }

    /// One persisted slot decision. {@code role} is the {@link
    /// org.mindis.core.model.Role#id()}; {@code serverId} is {@code null} for an
    /// unassigned slot.
    public record PlannedAssignment(
            String assignmentId,
            String serviceId,
            String role,
            @Nullable String serverId,
            boolean pinned) {
    }
}
