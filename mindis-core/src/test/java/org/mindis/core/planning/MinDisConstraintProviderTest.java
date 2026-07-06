package org.mindis.core.planning;

import ai.timefold.solver.core.api.score.stream.test.ConstraintVerifier;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.mindis.core.model.LiturgicalService;
import org.mindis.core.model.Role;
import org.mindis.core.model.Server;
import org.mindis.core.model.ServiceType;
import org.mindis.core.model.UnavailabilityPeriod;

class MinDisConstraintProviderTest {

    private final ConstraintVerifier<MinDisConstraintProvider, ServicePlan> verifier =
            ConstraintVerifier.build(new MinDisConstraintProvider(), ServicePlan.class, Assignment.class);

    private static Server server(String id, Set<Role> qualifications) {
        return new Server(id, "First-" + id, "Last-" + id, "", null, null, qualifications, List.of(), Set.of(), false, true);
    }

    private static Server siblingServer(String id, String familyId) {
        return new Server(id, "First-" + id, "Last-" + id, "", null, familyId,
                Set.of(Role.ACOLYTE), List.of(), Set.of(), false, true);
    }

    private static LiturgicalService serviceAt(String id, LocalDateTime dateTime) {
        return new LiturgicalService(id, dateTime, 60, "St. Mary", ServiceType.SUNDAY_MASS, List.of(), "");
    }

    private static Assignment assigned(String id, LiturgicalService service, Role role, Server server) {
        Assignment assignment = new Assignment(id, service, role);
        assignment.setServer(server);
        return assignment;
    }

    private static final LocalDateTime SUNDAY_10 = LocalDateTime.of(2026, 7, 12, 10, 0);

    @Test
    void unqualifiedServerPenalized() {
        Server acolyteOnly = server("s1", Set.of(Role.ACOLYTE));
        verifier.verifyThat(MinDisConstraintProvider::serverMustBeQualified)
                .given(assigned("a1", serviceAt("svc1", SUNDAY_10), Role.THURIFER, acolyteOnly))
                .penalizesBy(1);
    }

    @Test
    void qualifiedServerNotPenalized() {
        Server thurifer = server("s1", Set.of(Role.THURIFER));
        verifier.verifyThat(MinDisConstraintProvider::serverMustBeQualified)
                .given(assigned("a1", serviceAt("svc1", SUNDAY_10), Role.THURIFER, thurifer))
                .penalizesBy(0);
    }

    @Test
    void unavailableServerPenalized() {
        Server onVacation = new Server("s1", "A", "B", "", null, null, Set.of(Role.ACOLYTE),
                List.of(new UnavailabilityPeriod(LocalDate.of(2026, 7, 1), LocalDate.of(2026, 7, 31))), Set.of(), false, true);
        verifier.verifyThat(MinDisConstraintProvider::serverMustBeAvailable)
                .given(assigned("a1", serviceAt("svc1", SUNDAY_10), Role.ACOLYTE, onVacation))
                .penalizesBy(1);
    }

    @Test
    void inactiveServerPenalized() {
        Server inactive = new Server("s1", "A", "B", "", null, null, Set.of(Role.ACOLYTE), List.of(), Set.of(), false, false);
        verifier.verifyThat(MinDisConstraintProvider::serverMustBeActive)
                .given(assigned("a1", serviceAt("svc1", SUNDAY_10), Role.ACOLYTE, inactive))
                .penalizesBy(1);
    }

    @Test
    void doubleBookingPenalized() {
        Server one = server("s1", Set.of(Role.ACOLYTE, Role.THURIFER));
        LiturgicalService service = serviceAt("svc1", SUNDAY_10);
        verifier.verifyThat(MinDisConstraintProvider::noOverlappingAssignments)
                .given(
                        assigned("a1", service, Role.ACOLYTE, one),
                        assigned("a2", service, Role.THURIFER, one))
                .penalizesBy(1);
    }

    @Test
    void nonOverlappingServicesNotPenalized() {
        Server one = server("s1", Set.of(Role.ACOLYTE));
        verifier.verifyThat(MinDisConstraintProvider::noOverlappingAssignments)
                .given(
                        assigned("a1", serviceAt("svc1", SUNDAY_10), Role.ACOLYTE, one),
                        assigned("a2", serviceAt("svc2", SUNDAY_10.plusHours(2)), Role.ACOLYTE, one))
                .penalizesBy(0);
    }

    @Test
    void workloadPenaltyGrowsQuadratically() {
        Server busy = server("s1", Set.of(Role.ACOLYTE));
        // Two assignments on one server -> match weight count^2 = 4
        // (penalizesBy counts match weights; the constraint weight is separate).
        verifier.verifyThat(MinDisConstraintProvider::fairWorkloadDistribution)
                .given(
                        assigned("a1", serviceAt("svc1", SUNDAY_10), Role.ACOLYTE, busy),
                        assigned("a2", serviceAt("svc2", SUNDAY_10.plusDays(7)), Role.ACOLYTE, busy))
                .penalizesBy(4);
    }

    @Test
    void siblingsTogetherRewarded() {
        LiturgicalService service = serviceAt("svc1", SUNDAY_10);
        verifier.verifyThat(MinDisConstraintProvider::siblingsServeTogether)
                .given(
                        assigned("a1", service, Role.ACOLYTE, siblingServer("s1", "fam-x")),
                        assigned("a2", service, Role.ACOLYTE, siblingServer("s2", "fam-x")))
                .rewardsWith(1);
    }

    @Test
    void unassignedSlotPenalized() {
        verifier.verifyThat(MinDisConstraintProvider::everySlotAssigned)
                .given(new Assignment("a1", serviceAt("svc1", SUNDAY_10), Role.ACOLYTE))
                .penalizesBy(1);
    }

    @Test
    void preferredTimeRewarded() {
        Server likesTen = new Server("s1", "A", "B", "", null, null, Set.of(Role.ACOLYTE),
                List.of(), Set.of(java.time.LocalTime.of(10, 0)), false, true);
        verifier.verifyThat(MinDisConstraintProvider::preferredServiceTime)
                .given(assigned("a1", serviceAt("svc1", SUNDAY_10), Role.ACOLYTE, likesTen))
                .rewardsWith(1);
    }

    @Test
    void experiencedPresenceRewardedOncePerService() {
        Server experiencedOne = new Server("s1", "A", "B", "", null, null, Set.of(Role.ACOLYTE),
                List.of(), Set.of(), true, true);
        Server experiencedTwo = new Server("s2", "C", "D", "", null, null, Set.of(Role.ACOLYTE),
                List.of(), Set.of(), true, true);
        LiturgicalService service = serviceAt("svc1", SUNDAY_10);
        verifier.verifyThat(MinDisConstraintProvider::experiencedServerPresent)
                .given(
                        assigned("a1", service, Role.ACOLYTE, experiencedOne),
                        assigned("a2", service, Role.ACOLYTE, experiencedTwo))
                .rewardsWith(1);
    }

    @Test
    void consecutiveDayAssignmentsPenalized() {
        Server one = server("s1", Set.of(Role.ACOLYTE));
        verifier.verifyThat(MinDisConstraintProvider::spacingBetweenAssignments)
                .given(
                        assigned("a1", serviceAt("svc1", SUNDAY_10), Role.ACOLYTE, one),
                        assigned("a2", serviceAt("svc2", SUNDAY_10.plusDays(1)), Role.ACOLYTE, one))
                .penalizesBy(1);
    }
}
