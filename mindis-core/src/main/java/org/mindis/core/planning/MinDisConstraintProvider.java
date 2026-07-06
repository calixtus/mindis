package org.mindis.core.planning;

import static ai.timefold.solver.core.api.score.stream.Joiners.equal;
import static ai.timefold.solver.core.api.score.stream.Joiners.filtering;
import static ai.timefold.solver.core.api.score.stream.Joiners.overlapping;

import ai.timefold.solver.core.api.score.HardMediumSoftScore;
import ai.timefold.solver.core.api.score.stream.Constraint;
import ai.timefold.solver.core.api.score.stream.ConstraintCollectors;
import ai.timefold.solver.core.api.score.stream.ConstraintFactory;
import ai.timefold.solver.core.api.score.stream.ConstraintProvider;

import java.time.temporal.ChronoUnit;
import java.util.Objects;

/**
 * Scoring rules for altar server planning (PLAN.md section 3).
 *
 * <p>Score levels: hard = rule violations (never acceptable), medium =
 * unassigned slots (allowed but strongly discouraged, so an over-constrained
 * month still yields the best partial plan), soft = plan quality.
 *
 * <p>Deferred until the model carries the data: preferred-mass-times reward and
 * experienced/new pairing (no preference or experience fields yet - see PLAN.md M3 note).
 */
public class MinDisConstraintProvider implements ConstraintProvider {

    static final int FAIRNESS_WEIGHT = 2;
    static final int SIBLINGS_REWARD = 5;
    static final int SPACING_PENALTY = 3;

    // Constraint names double as full-text localization keys (PLAN.md 2.3)
    // and are reused by ViolationChecker for the per-assignment display.
    public static final String NOT_QUALIFIED = "Server not qualified for role";
    public static final String UNAVAILABLE = "Server unavailable";
    public static final String INACTIVE = "Server inactive";
    public static final String DOUBLE_BOOKED = "Server double-booked";
    public static final String UNASSIGNED = "Slot unassigned";

    @Override
    public Constraint[] defineConstraints(ConstraintFactory factory) {
        return new Constraint[] {
                serverMustBeQualified(factory),
                serverMustBeAvailable(factory),
                serverMustBeActive(factory),
                noOverlappingAssignments(factory),
                everySlotAssigned(factory),
                fairWorkloadDistribution(factory),
                siblingsServeTogether(factory),
                spacingBetweenAssignments(factory)
        };
    }

    Constraint serverMustBeQualified(ConstraintFactory factory) {
        return factory.forEach(Assignment.class)
                .filter(assignment -> !assignment.getServer().qualifications().contains(assignment.getRole()))
                .penalize(HardMediumSoftScore.ONE_HARD)
                .asConstraint(NOT_QUALIFIED);
    }

    Constraint serverMustBeAvailable(ConstraintFactory factory) {
        return factory.forEach(Assignment.class)
                .filter(assignment -> !assignment.getServer().isAvailableAt(assignment.serviceStart()))
                .penalize(HardMediumSoftScore.ONE_HARD)
                .asConstraint(UNAVAILABLE);
    }

    Constraint serverMustBeActive(ConstraintFactory factory) {
        return factory.forEach(Assignment.class)
                .filter(assignment -> !assignment.getServer().active())
                .penalize(HardMediumSoftScore.ONE_HARD)
                .asConstraint(INACTIVE);
    }

    Constraint noOverlappingAssignments(ConstraintFactory factory) {
        // Also covers two slots of the same service: identical times overlap.
        return factory.forEachUniquePair(Assignment.class,
                        equal(Assignment::getServer),
                        overlapping(Assignment::serviceStart, Assignment::serviceEnd))
                .penalize(HardMediumSoftScore.ONE_HARD)
                .asConstraint(DOUBLE_BOOKED);
    }

    Constraint everySlotAssigned(ConstraintFactory factory) {
        return factory.forEachIncludingUnassigned(Assignment.class)
                .filter(assignment -> assignment.getServer() == null)
                .penalize(HardMediumSoftScore.ONE_MEDIUM)
                .asConstraint(UNASSIGNED);
    }

    Constraint fairWorkloadDistribution(ConstraintFactory factory) {
        return factory.forEach(Assignment.class)
                .groupBy(Assignment::getServer, ConstraintCollectors.count())
                .penalize(HardMediumSoftScore.ofSoft(FAIRNESS_WEIGHT), (server, count) -> count * count)
                .asConstraint("Unbalanced workload");
    }

    Constraint siblingsServeTogether(ConstraintFactory factory) {
        return factory.forEachUniquePair(Assignment.class,
                        equal(assignment -> assignment.getService().id()),
                        filtering((a, b) -> !a.getServer().equals(b.getServer())
                                && a.getServer().familyId() != null
                                && Objects.equals(a.getServer().familyId(), b.getServer().familyId())))
                .reward(HardMediumSoftScore.ofSoft(SIBLINGS_REWARD))
                .asConstraint("Siblings serve together");
    }

    Constraint spacingBetweenAssignments(ConstraintFactory factory) {
        return factory.forEachUniquePair(Assignment.class,
                        equal(Assignment::getServer),
                        filtering((a, b) -> !a.getService().id().equals(b.getService().id())
                                && Math.abs(ChronoUnit.DAYS.between(
                                        a.serviceStart().toLocalDate(),
                                        b.serviceStart().toLocalDate())) <= 1))
                .penalize(HardMediumSoftScore.ofSoft(SPACING_PENALTY))
                .asConstraint("Assignments too close together");
    }
}
