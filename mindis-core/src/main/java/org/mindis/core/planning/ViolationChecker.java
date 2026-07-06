package org.mindis.core.planning;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.mindis.core.model.Server;

/**
 * Computes per-assignment hard/medium violations for display, mirroring the
 * hard and medium constraints of {@link MinDisConstraintProvider} in plain
 * Java. Needed because Timefold's {@code SolutionManager.analyze()} is an
 * enterprise-only feature; these checks are trivial to hold in sync (shared
 * name constants, guarded by tests).
 */
public final class ViolationChecker {

    private ViolationChecker() {
    }

    /**
     * @return assignment id to the (localizable full-text) names of violated
     *         constraints; assignments without violations are absent.
     */
    public static Map<String, List<String>> violationsByAssignment(ServicePlan plan) {
        Map<String, List<String>> violations = new HashMap<>();
        List<Assignment> assignments = plan.getAssignments();

        for (Assignment assignment : assignments) {
            Server server = assignment.getServer();
            if (server == null) {
                add(violations, assignment, MinDisConstraintProvider.UNASSIGNED);
                continue;
            }
            if (!server.qualifications().contains(assignment.getRole())) {
                add(violations, assignment, MinDisConstraintProvider.NOT_QUALIFIED);
            }
            if (!server.isAvailableAt(assignment.serviceStart())) {
                add(violations, assignment, MinDisConstraintProvider.UNAVAILABLE);
            }
            if (!server.active()) {
                add(violations, assignment, MinDisConstraintProvider.INACTIVE);
            }
        }

        for (int i = 0; i < assignments.size(); i++) {
            Assignment a = assignments.get(i);
            if (a.getServer() == null) {
                continue;
            }
            for (int j = i + 1; j < assignments.size(); j++) {
                Assignment b = assignments.get(j);
                if (a.getServer().equals(b.getServer()) && overlap(a, b)) {
                    add(violations, a, MinDisConstraintProvider.DOUBLE_BOOKED);
                    add(violations, b, MinDisConstraintProvider.DOUBLE_BOOKED);
                }
            }
        }
        return violations;
    }

    private static boolean overlap(Assignment a, Assignment b) {
        return a.serviceStart().isBefore(b.serviceEnd()) && b.serviceStart().isBefore(a.serviceEnd());
    }

    private static void add(Map<String, List<String>> violations, Assignment assignment, String name) {
        violations.computeIfAbsent(assignment.getId(), key -> new ArrayList<>()).add(name);
    }
}
