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
import java.util.List;
import java.util.Objects;

import org.mindis.core.model.Role;

/**
 * Scoring rules for altar server planning (PLAN.md section 3).
 *
 * <p>Score levels: hard = rule violations (never acceptable), medium =
 * unassigned slots (allowed but strongly discouraged, so an over-constrained
 * month still yields the best partial plan), soft = plan quality.
 *
 * <p>Soft constraint weights are tunable via preferences
 * ({@link #defaultSoftWeights()} names the knobs); overrides are applied per
 * solve through {@code ConstraintWeightOverrides} on the solution.
 */
public class MinDisConstraintProvider implements ConstraintProvider {

    static final int FAIRNESS_WEIGHT = 2;
    static final int SIBLINGS_REWARD = 5;
    static final int SPACING_PENALTY = 3;
    static final int PRIOR_SPACING_PENALTY = 3;

    static final int PREFERRED_TIME_REWARD = 2;
    static final int EXPERIENCE_REWARD = 4;
    static final int AGE_REQUIREMENT_PENALTY = 4;

    // Shared with PlanningService, which uses it to decide how much of a
    // preceding plan's tail is even worth loading as PriorAssignment facts -
    // one number so the "how close counts as too close" definition can't
    // drift between the two.
    public static final long SPACING_THRESHOLD_DAYS = 1;

    // Constraint names double as full-text localization keys (PLAN.md 2.3)
    // and are reused by ViolationChecker for the per-assignment display.
    public static final String NOT_QUALIFIED = "Server not qualified for role";
    public static final String UNAVAILABLE = "Server unavailable";
    public static final String INACTIVE = "Server inactive";
    public static final String DOUBLE_BOOKED = "Server double-booked";
    public static final String UNASSIGNED = "Slot unassigned";
    public static final String UNBALANCED_WORKLOAD = "Unbalanced workload";
    public static final String SIBLINGS_TOGETHER = "Siblings serve together";
    public static final String TOO_CLOSE = "Assignments too close together";
    public static final String TOO_CLOSE_TO_PRIOR_PLAN = "Too close to previous plan";
    public static final String PREFERRED_TIME = "Preferred service time";
    public static final String EXPERIENCED_PRESENT = "Experienced server present";
    public static final String AGE_REQUIREMENT = "Server age outside role range";

    /**
     * The tunable soft constraints in display order - single source for the
     * preferences UI and the default weight map.
     */
    public static java.util.List<String> tunableSoftConstraints() {
        return List.of(
                UNBALANCED_WORKLOAD,
                SIBLINGS_TOGETHER,
                TOO_CLOSE,
                TOO_CLOSE_TO_PRIOR_PLAN,
                PREFERRED_TIME,
                EXPERIENCED_PRESENT,
                AGE_REQUIREMENT);
    }

    /**
     * Default weights of the tunable soft constraints, keyed by constraint
     * name; the preferences UI edits these (PLAN.md M4/M5 deferred item).
     */
    public static java.util.Map<String, Integer> defaultSoftWeights() {
        return java.util.Map.of(
                UNBALANCED_WORKLOAD, FAIRNESS_WEIGHT,
                SIBLINGS_TOGETHER, SIBLINGS_REWARD,
                TOO_CLOSE, SPACING_PENALTY,
                TOO_CLOSE_TO_PRIOR_PLAN, PRIOR_SPACING_PENALTY,
                PREFERRED_TIME, PREFERRED_TIME_REWARD,
                EXPERIENCED_PRESENT, EXPERIENCE_REWARD,
                AGE_REQUIREMENT, AGE_REQUIREMENT_PENALTY);
    }

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
                spacingBetweenAssignments(factory),
                spacingFromPriorPlan(factory),
                preferredServiceTime(factory),
                experiencedServerPresent(factory),
                ageWithinRoleRequirement(factory)
        };
    }

    // NullAway: factory.forEach(Assignment.class) below (unlike
    // forEachIncludingUnassigned, used only in everySlotAssigned) yields only
    // entities with a non-null planning variable, so getServer() is safe here
    // - an invariant NullAway can't see through Timefold's API.

    @SuppressWarnings("NullAway")
    Constraint serverMustBeQualified(ConstraintFactory factory) {
        return factory.forEach(Assignment.class)
                .filter(assignment -> !assignment.getServer().qualifications().contains(assignment.getRole().id()))
                .penalize(HardMediumSoftScore.ONE_HARD)
                .asConstraint(NOT_QUALIFIED);
    }

    @SuppressWarnings("NullAway")
    Constraint serverMustBeAvailable(ConstraintFactory factory) {
        return factory.forEach(Assignment.class)
                .filter(assignment -> !assignment.getServer().isAvailableAt(assignment.serviceStart()))
                .penalize(HardMediumSoftScore.ONE_HARD)
                .asConstraint(UNAVAILABLE);
    }

    @SuppressWarnings("NullAway")
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
                .asConstraint(UNBALANCED_WORKLOAD);
    }

    @SuppressWarnings("NullAway")
    Constraint siblingsServeTogether(ConstraintFactory factory) {
        return factory.forEachUniquePair(Assignment.class,
                        equal(assignment -> assignment.getService().id()),
                        filtering((a, b) -> !a.getServer().equals(b.getServer())
                                && a.getServer().familyId() != null
                                && Objects.equals(a.getServer().familyId(), b.getServer().familyId())))
                .reward(HardMediumSoftScore.ofSoft(SIBLINGS_REWARD))
                .asConstraint(SIBLINGS_TOGETHER);
    }

    Constraint spacingBetweenAssignments(ConstraintFactory factory) {
        return factory.forEachUniquePair(Assignment.class,
                        equal(Assignment::getServer),
                        filtering((a, b) -> !a.getService().id().equals(b.getService().id())
                                && Math.abs(ChronoUnit.DAYS.between(
                                        a.serviceStart().toLocalDate(),
                                        b.serviceStart().toLocalDate())) <= SPACING_THRESHOLD_DAYS))
                .penalize(HardMediumSoftScore.ofSoft(SPACING_PENALTY))
                .asConstraint(TOO_CLOSE);
    }

    /**
     * The same "not too close together" intent as {@link
     * #spacingBetweenAssignments}, but across a plan boundary: penalizes a
     * server assigned within {@link #SPACING_THRESHOLD_DAYS} of a day they
     * already served in the immediately preceding plan. A solve is confined
     * to its own date range (PlanningService#buildProblem), so without this
     * the solver has no way to know - and no way to be penalized for -
     * scheduling the same server again the day after a previous plan ended.
     * A separate constraint (not folded into spacingBetweenAssignments)
     * because {@link PriorAssignment} isn't an {@link Assignment} - it's a
     * read-only fact, never a planning entity - so it needs its own {@code
     * join} rather than a {@code forEachUniquePair}; that in turn means its
     * own constraint id, since Timefold requires those unique, which is why
     * it has its own (separately tunable) weight rather than sharing {@link
     * #SPACING_PENALTY}.
     */
    @SuppressWarnings("NullAway")
    Constraint spacingFromPriorPlan(ConstraintFactory factory) {
        return factory.forEach(Assignment.class)
                .join(PriorAssignment.class, equal(Assignment::getServer, PriorAssignment::server))
                .filter((assignment, prior) -> Math.abs(ChronoUnit.DAYS.between(
                        assignment.serviceStart().toLocalDate(), prior.date())) <= SPACING_THRESHOLD_DAYS)
                .penalize(HardMediumSoftScore.ofSoft(PRIOR_SPACING_PENALTY))
                .asConstraint(TOO_CLOSE_TO_PRIOR_PLAN);
    }

    @SuppressWarnings("NullAway")
    Constraint preferredServiceTime(ConstraintFactory factory) {
        return factory.forEach(Assignment.class)
                .filter(assignment -> assignment.getServer().prefers(assignment.serviceStart()))
                .reward(HardMediumSoftScore.ofSoft(PREFERRED_TIME_REWARD))
                .asConstraint(PREFERRED_TIME);
    }

    @SuppressWarnings("NullAway")
    Constraint experiencedServerPresent(ConstraintFactory factory) {
        // One reward per service that has at least one experienced server.
        return factory.forEach(Assignment.class)
                .filter(assignment -> assignment.getServer().experienced())
                .groupBy(assignment -> assignment.getService().id())
                .reward(HardMediumSoftScore.ofSoft(EXPERIENCE_REWARD))
                .asConstraint(EXPERIENCED_PRESENT);
    }

    Constraint ageWithinRoleRequirement(ConstraintFactory factory) {
        // Soft: a server outside the role's age range is discouraged, not
        // forbidden. Unknown birth date -> not enforced (see outsideAgeRange).
        return factory.forEach(Assignment.class)
                .filter(MinDisConstraintProvider::outsideAgeRange)
                .penalize(HardMediumSoftScore.ofSoft(AGE_REQUIREMENT_PENALTY))
                .asConstraint(AGE_REQUIREMENT);
    }

    // See the NullAway note above ageWithinRoleRequirement's forEach: only
    // assigned entities reach here, so getServer() is safe.
    @SuppressWarnings("NullAway")
    private static boolean outsideAgeRange(Assignment assignment) {
        Role role = assignment.getRole();
        if (role.minAge() == null && role.maxAge() == null) {
            return false;
        }
        Integer age = assignment.getServer().ageAt(assignment.serviceStart().toLocalDate());
        if (age == null) {
            return false;
        }
        return (role.minAge() != null && age < role.minAge())
                || (role.maxAge() != null && age > role.maxAge());
    }
}
