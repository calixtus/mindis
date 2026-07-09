package org.mindis.core.planning;

import java.time.LocalDate;

import org.mindis.core.model.Server;

/// A read-only fact from the plan immediately preceding the one being solved:
/// {@code server} served on {@code date}. Not a {@link Assignment} (not a
/// planning entity, never assigned/moved by the solver) - it exists purely so
/// {@link MinDisConstraintProvider#spacingFromPriorPlan} can see across a plan
/// boundary and keep the same server from being scheduled again the day after
/// a previous plan ended, something a solve confined to the new plan's own
/// date range would otherwise be blind to.
public record PriorAssignment(LocalDate date, Server server) {
}
