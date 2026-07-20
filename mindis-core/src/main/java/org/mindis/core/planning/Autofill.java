package org.mindis.core.planning;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;

/// Pure pin-juggling for a scoped solve. "Autofill" never solves the whole
/// board freely: it leaves only the genuinely eligible slots free and pins
/// everything else so the solver cannot disturb decisions outside the scope,
/// then restores the untouched slots' original pin state afterward. Both the
/// single-service auto-fill and the windowed "fill all unassigned" use the same
/// two calls; only the eligibility predicate differs.
public final class Autofill {

    private Autofill() {
    }

    /// The pin state captured by {@link #begin}: every assignment's original
    /// pin (to restore the ones the solver was not allowed to touch) plus the
    /// ids left eligible.
    public record Scope(Map<String, Boolean> pinSnapshot, Set<String> eligibleIds) {
    }

    /// Leaves every assignment matching {@code eligible} free and pins the
    /// rest, returning the snapshot {@link #finish} needs.
    public static Scope begin(ServicePlan plan, Predicate<Assignment> eligible) {
        Map<String, Boolean> pinSnapshot = new HashMap<>();
        Set<String> eligibleIds = new HashSet<>();
        for (Assignment assignment : plan.getAssignments()) {
            pinSnapshot.put(assignment.getId(), assignment.isPinned());
            boolean free = eligible.test(assignment);
            assignment.setPinned(!free);
            if (free) {
                eligibleIds.add(assignment.getId());
            }
        }
        return new Scope(pinSnapshot, eligibleIds);
    }

    /// Eligibility for a windowed "fill all unassigned" run: the service falls
    /// within {@code [from, to]} and the slot is either open or - when {@code
    /// overwrite} - already filled.
    public static Predicate<Assignment> within(LocalDate from, LocalDate to, boolean overwrite) {
        return assignment -> {
            LocalDate date = assignment.getService().dateTime().toLocalDate();
            boolean inWindow = !date.isBefore(from) && !date.isAfter(to);
            return inWindow && (overwrite || assignment.getServer() == null);
        };
    }

    /// Eligibility for a single service's auto-fill: just that service's slots.
    public static Predicate<Assignment> forService(String serviceId, boolean overwrite) {
        return assignment -> assignment.getService().id().equals(serviceId)
                && (overwrite || assignment.getServer() == null);
    }

    /// Restores the pin state of every assignment the solver was not allowed to
    /// move (from {@code scope}); each eligible slot the solver filled becomes
    /// pinned, since the planner asked for this fill just as deliberately as a
    /// manual pick, while an eligible slot left empty stays unpinned.
    public static void finish(ServicePlan solved, Scope scope) {
        for (Assignment assignment : solved.getAssignments()) {
            if (scope.eligibleIds().contains(assignment.getId())) {
                assignment.setPinned(assignment.getServer() != null);
            } else {
                assignment.setPinned(Boolean.TRUE.equals(scope.pinSnapshot().get(assignment.getId())));
            }
        }
    }
}
