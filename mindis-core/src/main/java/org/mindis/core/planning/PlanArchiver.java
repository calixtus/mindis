package org.mindis.core.planning;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;

/// Pure logic for splitting the open plan at an archive cutoff. Dependency-free
/// by design (SOLID/DIP), same shape as {@link PlanMapper} - callers do not
/// need a repository-backed service for plain partitioning.
public final class PlanArchiver {

    private PlanArchiver() {
    }

    /// The two halves of an archive split: the frozen portion (dates on or
    /// before the cutoff) and the still-open remainder (dates after it).
    /// Either half is empty when every assignment in {@code open} lands on
    /// the other side of the cutoff.
    public record Split(Optional<AcceptedPlan> archived, Optional<AcceptedPlan> remainder) {
    }

    /// Partitions {@code open}'s assignments by each assignment's own
    /// service date (resolved via {@code serviceDateOf}, keyed by {@code
    /// serviceId}) against {@code cutoff}: on-or-before goes to the archived
    /// half (bounded {@code [open.from(), cutoff]}), strictly-after goes to
    /// the remainder (bounded {@code [cutoff.plusDays(1), open.toInclusive()]}).
    /// An assignment whose service can no longer be resolved (deleted) stays
    /// on the remainder side - it cannot be dated, so it is left editable
    /// rather than silently frozen by a guess.
    public static Split split(AcceptedPlan open, LocalDate cutoff,
                              Function<String, Optional<LocalDate>> serviceDateOf) {
        List<AcceptedPlan.PlannedAssignment> archivedAssignments = new ArrayList<>();
        List<AcceptedPlan.PlannedAssignment> remainderAssignments = new ArrayList<>();
        for (AcceptedPlan.PlannedAssignment assignment : open.assignments()) {
            Optional<LocalDate> date = serviceDateOf.apply(assignment.serviceId());
            if (date.isPresent() && !date.get().isAfter(cutoff)) {
                archivedAssignments.add(assignment);
            } else {
                remainderAssignments.add(assignment);
            }
        }
        Optional<AcceptedPlan> archived = archivedAssignments.isEmpty()
                ? Optional.empty()
                : Optional.of(new AcceptedPlan(open.from(), cutoff, archivedAssignments, null, true, null));
        Optional<AcceptedPlan> remainder = remainderAssignments.isEmpty()
                ? Optional.empty()
                : Optional.of(new AcceptedPlan(cutoff.plusDays(1), open.toInclusive(), remainderAssignments, null, false, null));
        return new Split(archived, remainder);
    }
}
