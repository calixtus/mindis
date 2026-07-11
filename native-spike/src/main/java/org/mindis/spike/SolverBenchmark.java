package org.mindis.spike;

import ai.timefold.solver.core.api.solver.SolverFactory;
import ai.timefold.solver.core.config.solver.EnvironmentMode;
import ai.timefold.solver.core.config.solver.SolverConfig;
import ai.timefold.solver.core.config.solver.termination.TerminationConfig;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.mindis.core.model.LiturgicalService;
import org.mindis.core.model.Role;
import org.mindis.core.model.RoleSlot;
import org.mindis.core.model.Server;
import org.mindis.core.model.ServiceType;
import org.mindis.core.model.Slot;
import org.mindis.core.planning.Assignment;
import org.mindis.core.planning.AssignmentKey;
import org.mindis.core.planning.MinDisConstraintProvider;
import org.mindis.core.planning.ServicePlan;

/// M7 spike benchmark (PLAN.md): solves the realistic month fixture with a
/// fixed time budget and prints the reached score plus wall time. Run once on
/// the JVM (JIT) and once as a GraalVM native image; Timefold's
/// "score calculation speed" log line is the comparison metric.
public final class SolverBenchmark {

    // Role entities keyed by id (roles are configurable; the solver needs the
    // entity for name/age, slots/qualifications reference them by id).
    private static final Map<String, Role> ROLES = Map.of(
            Role.ACOLYTE, new Role(Role.ACOLYTE, "Acolyte", null, null, 0),
            Role.CROSS_BEARER, new Role(Role.CROSS_BEARER, "Cross bearer", null, null, 1),
            Role.THURIFER, new Role(Role.THURIFER, "Thurifer", null, null, 2),
            Role.BOAT_BEARER, new Role(Role.BOAT_BEARER, "Boat bearer", null, null, 3),
            Role.MASTER_OF_CEREMONIES, new Role(Role.MASTER_OF_CEREMONIES, "Master of ceremonies", null, null, 4));

    private SolverBenchmark() {
    }

    public static void main(String[] args) {
        long seconds = args.length > 0 ? Long.parseLong(args[0]) : 10L;

        ServicePlan problem = buildFixture();
        SolverConfig config = new SolverConfig()
                .withSolutionClass(ServicePlan.class)
                .withEntityClasses(Assignment.class)
                .withConstraintProviderClass(MinDisConstraintProvider.class)
                // Fixed mode so JIT and native runs are comparable (the
                // default escalates to PHASE_ASSERT when assertions are on).
                .withEnvironmentMode(EnvironmentMode.NO_ASSERT)
                .withTerminationConfig(new TerminationConfig().withSecondsSpentLimit(seconds));

        long start = System.nanoTime();
        ServicePlan solved = SolverFactory.<ServicePlan>create(config)
                .buildSolver()
                .solve(problem);
        long wallMillis = (System.nanoTime() - start) / 1_000_000;

        long unassigned = solved.getAssignments().stream()
                .filter(assignment -> assignment.getServer() == null)
                .count();
        System.out.println("=== MinDis solver benchmark ===");
        System.out.println("budget-seconds: " + seconds);
        System.out.println("wall-millis:    " + wallMillis);
        System.out.println("score:          " + solved.getScore());
        System.out.println("unassigned:     " + unassigned + "/" + solved.getAssignments().size());
    }

    private static ServicePlan buildFixture() {
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

        List<LiturgicalService> services = new ArrayList<>();
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

        List<Assignment> assignments = new ArrayList<>();
        for (LiturgicalService service : services) {
            for (Slot slot : service.slots()) {
                assignments.add(new Assignment(
                        new AssignmentKey(service.id(), slot.id()).toId(), service, ROLES.get(slot.role())));
            }
        }
        return new ServicePlan(servers, assignments);
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
