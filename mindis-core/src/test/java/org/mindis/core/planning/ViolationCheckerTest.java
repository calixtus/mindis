package org.mindis.core.planning;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.mindis.core.model.LiturgicalService;
import org.mindis.core.model.Role;
import org.mindis.core.model.Server;
import org.mindis.core.model.ServiceType;

class ViolationCheckerTest {

    private static final Server ACOLYTE_ONLY =
            new Server("s1", "Anna", "Muster", "", null, null, Set.of(Role.ACOLYTE), List.of(), Set.of(), false, true);
    private static final LiturgicalService MASS = new LiturgicalService(
            "svc1", LocalDateTime.of(2026, 8, 2, 10, 0), 60, "St. Mary",
            ServiceType.SUNDAY_MASS, List.of(), "");

    @Test
    void unassignedAndUnqualifiedAndDoubleBookedDetected() {
        Assignment unassigned = new Assignment("a1", MASS, Role.ACOLYTE);
        Assignment unqualified = new Assignment("a2", MASS, Role.THURIFER);
        unqualified.setServer(ACOLYTE_ONLY);
        Assignment doubleBooked = new Assignment("a3", MASS, Role.ACOLYTE);
        doubleBooked.setServer(ACOLYTE_ONLY);
        ServicePlan plan = new ServicePlan(List.of(ACOLYTE_ONLY),
                List.of(unassigned, unqualified, doubleBooked));

        Map<String, List<String>> violations = ViolationChecker.violationsByAssignment(plan);

        assertEquals(List.of(MinDisConstraintProvider.UNASSIGNED), violations.get("a1"));
        assertTrue(violations.get("a2").contains(MinDisConstraintProvider.NOT_QUALIFIED));
        assertTrue(violations.get("a2").contains(MinDisConstraintProvider.DOUBLE_BOOKED));
        assertTrue(violations.get("a3").contains(MinDisConstraintProvider.DOUBLE_BOOKED));
    }

    @Test
    void cleanAssignmentHasNoEntry() {
        Assignment clean = new Assignment("a1", MASS, Role.ACOLYTE);
        clean.setServer(ACOLYTE_ONLY);
        ServicePlan plan = new ServicePlan(List.of(ACOLYTE_ONLY), List.of(clean));

        assertTrue(ViolationChecker.violationsByAssignment(plan).isEmpty());
    }
}
