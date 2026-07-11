package org.mindis.core.planning;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import ai.timefold.solver.core.api.solver.SolverFactory;
import ai.timefold.solver.core.config.solver.SolverConfig;
import ai.timefold.solver.core.config.solver.termination.TerminationConfig;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.mindis.core.model.LiturgicalService;
import org.mindis.core.model.Role;
import org.mindis.core.model.RoleSlot;
import org.mindis.core.model.Server;
import org.mindis.core.model.ServiceType;
import org.mindis.core.model.Slot;

/// PLAN.md M3 done-when: a realistic month (~20 servers, ~15 services) must
/// yield a feasible plan (0 hard violations) headlessly - proving core solves
/// without any UI module.
class PlanningEndToEndTest {

    private static final Map<String, Role> ROLES = Map.of(
            Role.ACOLYTE, new Role(Role.ACOLYTE, "Acolyte", null, null, 0),
            Role.CROSS_BEARER, new Role(Role.CROSS_BEARER, "Cross bearer", null, null, 1),
            Role.THURIFER, new Role(Role.THURIFER, "Thurifer", null, null, 2),
            Role.BOAT_BEARER, new Role(Role.BOAT_BEARER, "Boat bearer", null, null, 3),
            Role.MASTER_OF_CEREMONIES, new Role(Role.MASTER_OF_CEREMONIES, "Master of ceremonies", null, null, 4));

    @Test
    @Timeout(120)
    // NullAway: ROLES.get(slot.role()) is always present (slots only ever
    // reference the Role constants seeded into ROLES below), and solved's
    // score is always set after SolverFactory.solve() returns.
    @SuppressWarnings("NullAway")
    void realisticMonthYieldsFeasiblePlan() {
        List<Server> servers = new ArrayList<>();
        for (int i = 0; i < 20; i++) {
            Set<String> qualifications = switch (i % 4) {
                case 0 -> Set.of(Role.ACOLYTE, Role.THURIFER, Role.BOAT_BEARER);
                case 1 -> Set.of(Role.ACOLYTE, Role.CROSS_BEARER);
                case 2 -> Set.of(Role.ACOLYTE, Role.MASTER_OF_CEREMONIES);
                default -> Set.of(Role.ACOLYTE);
            };
            servers.add(new Server("server-" + i, "First" + i, "Last" + i, "", null,
                    i % 5 == 0 ? "family-" + (i / 5) : null, qualifications, List.of(), Set.of(), false, true));
        }

        List<Assignment> assignments = new ArrayList<>();
        List<LiturgicalService> services = new ArrayList<>();
        // 4 Sundays x 2 masses + weekday masses = 15 services in July 2026.
        LocalDateTime firstSunday = LocalDateTime.of(2026, 7, 5, 10, 0);
        for (int week = 0; week < 4; week++) {
            services.add(sundayMass("sun-early-" + week, firstSunday.plusWeeks(week).minusHours(2)));
            services.add(sundayMass("sun-main-" + week, firstSunday.plusWeeks(week)));
        }
        for (int i = 0; i < 7; i++) {
            services.add(new LiturgicalService("weekday-" + i,
                    LocalDateTime.of(2026, 7, 7 + i * 3, 18, 30), 45, "St. Mary",
                    ServiceType.WEEKDAY_MASS, Slot.expand(List.of(new RoleSlot(Role.ACOLYTE, 2))), ""));
        }
        for (LiturgicalService service : services) {
            for (Slot slot : service.slots()) {
                assignments.add(new Assignment(
                        new AssignmentKey(service.id(), slot.id()).toId(), service, ROLES.get(slot.role())));
            }
        }

        // Note: bestScoreFeasible would stop at the empty plan (hard=0, all
        // slots unassigned = medium only). Terminate on lack of improvement.
        SolverConfig config = PlanningService.solverConfig()
                .withTerminationConfig(new TerminationConfig()
                        .withUnimprovedSecondsSpentLimit(3L)
                        .withSecondsSpentLimit(60L));
        ServicePlan solved = SolverFactory.<ServicePlan>create(config)
                .buildSolver()
                .solve(new ServicePlan(servers, assignments));

        assertTrue(solved.getScore().isFeasible(),
                "Expected feasible plan, got score " + solved.getScore());
        long unassigned = solved.getAssignments().stream()
                .filter(assignment -> assignment.getServer() == null)
                .count();
        assertEquals(0, unassigned, "Expected all slots assigned, " + unassigned + " unassigned");
    }

    private static LiturgicalService sundayMass(String id, LocalDateTime dateTime) {
        return new LiturgicalService(id, dateTime, 60, "St. Mary", ServiceType.SUNDAY_MASS,
                Slot.expand(List.of(
                        new RoleSlot(Role.ACOLYTE, 2),
                        new RoleSlot(Role.THURIFER, 1),
                        new RoleSlot(Role.CROSS_BEARER, 1))),
                "");
    }
}
