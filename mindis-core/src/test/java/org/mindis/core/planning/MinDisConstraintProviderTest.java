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

    private static final Role ROLE_ACOLYTE = new Role(Role.ACOLYTE, "Acolyte", null, null, 0);
    private static final Role ROLE_THURIFER = new Role(Role.THURIFER, "Thurifer", null, null, 2);

    private static Server server(String id, Set<String> qualifications) {
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
                .given(assigned("a1", serviceAt("svc1", SUNDAY_10), ROLE_THURIFER, acolyteOnly))
                .penalizesBy(1);
    }

    @Test
    void qualifiedServerNotPenalized() {
        Server thurifer = server("s1", Set.of(Role.THURIFER));
        verifier.verifyThat(MinDisConstraintProvider::serverMustBeQualified)
                .given(assigned("a1", serviceAt("svc1", SUNDAY_10), ROLE_THURIFER, thurifer))
                .penalizesBy(0);
    }

    @Test
    void unavailableServerPenalized() {
        Server onVacation = new Server("s1", "A", "B", "", null, null, Set.of(Role.ACOLYTE),
                List.of(new UnavailabilityPeriod(LocalDate.of(2026, 7, 1), LocalDate.of(2026, 7, 31))), Set.of(), false, true);
        verifier.verifyThat(MinDisConstraintProvider::serverMustBeAvailable)
                .given(assigned("a1", serviceAt("svc1", SUNDAY_10), ROLE_ACOLYTE, onVacation))
                .penalizesBy(1);
    }

    @Test
    void inactiveServerPenalized() {
        Server inactive = new Server("s1", "A", "B", "", null, null, Set.of(Role.ACOLYTE), List.of(), Set.of(), false, false);
        verifier.verifyThat(MinDisConstraintProvider::serverMustBeActive)
                .given(assigned("a1", serviceAt("svc1", SUNDAY_10), ROLE_ACOLYTE, inactive))
                .penalizesBy(1);
    }

    @Test
    void doubleBookingPenalized() {
        Server one = server("s1", Set.of(Role.ACOLYTE, Role.THURIFER));
        LiturgicalService service = serviceAt("svc1", SUNDAY_10);
        verifier.verifyThat(MinDisConstraintProvider::noOverlappingAssignments)
                .given(
                        assigned("a1", service, ROLE_ACOLYTE, one),
                        assigned("a2", service, ROLE_THURIFER, one))
                .penalizesBy(1);
    }

    @Test
    void nonOverlappingServicesNotPenalized() {
        Server one = server("s1", Set.of(Role.ACOLYTE));
        verifier.verifyThat(MinDisConstraintProvider::noOverlappingAssignments)
                .given(
                        assigned("a1", serviceAt("svc1", SUNDAY_10), ROLE_ACOLYTE, one),
                        assigned("a2", serviceAt("svc2", SUNDAY_10.plusHours(2)), ROLE_ACOLYTE, one))
                .penalizesBy(0);
    }

    @Test
    void workloadPenaltyGrowsQuadratically() {
        Server busy = server("s1", Set.of(Role.ACOLYTE));
        // Two assignments on one server -> match weight count^2 = 4
        // (penalizesBy counts match weights; the constraint weight is separate).
        verifier.verifyThat(MinDisConstraintProvider::fairWorkloadDistribution)
                .given(
                        assigned("a1", serviceAt("svc1", SUNDAY_10), ROLE_ACOLYTE, busy),
                        assigned("a2", serviceAt("svc2", SUNDAY_10.plusDays(7)), ROLE_ACOLYTE, busy))
                .penalizesBy(4);
    }

    @Test
    void siblingsTogetherRewarded() {
        LiturgicalService service = serviceAt("svc1", SUNDAY_10);
        verifier.verifyThat(MinDisConstraintProvider::siblingsServeTogether)
                .given(
                        assigned("a1", service, ROLE_ACOLYTE, siblingServer("s1", "fam-x")),
                        assigned("a2", service, ROLE_ACOLYTE, siblingServer("s2", "fam-x")))
                .rewardsWith(1);
    }

    @Test
    void unassignedSlotPenalized() {
        verifier.verifyThat(MinDisConstraintProvider::everySlotAssigned)
                .given(new Assignment("a1", serviceAt("svc1", SUNDAY_10), ROLE_ACOLYTE))
                .penalizesBy(1);
    }

    @Test
    void preferredTimeRewarded() {
        Server likesTen = new Server("s1", "A", "B", "", null, null, Set.of(Role.ACOLYTE),
                List.of(), Set.of(java.time.LocalTime.of(10, 0)), false, true);
        verifier.verifyThat(MinDisConstraintProvider::preferredServiceTime)
                .given(assigned("a1", serviceAt("svc1", SUNDAY_10), ROLE_ACOLYTE, likesTen))
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
                        assigned("a1", service, ROLE_ACOLYTE, experiencedOne),
                        assigned("a2", service, ROLE_ACOLYTE, experiencedTwo))
                .rewardsWith(1);
    }

    @Test
    void consecutiveDayAssignmentsPenalized() {
        Server one = server("s1", Set.of(Role.ACOLYTE));
        verifier.verifyThat(MinDisConstraintProvider::spacingBetweenAssignments)
                .given(
                        assigned("a1", serviceAt("svc1", SUNDAY_10), ROLE_ACOLYTE, one),
                        assigned("a2", serviceAt("svc2", SUNDAY_10.plusDays(1)), ROLE_ACOLYTE, one))
                .penalizesBy(1);
    }

    @Test
    void assignmentTooCloseToPriorPlanPenalized() {
        Server one = server("s1", Set.of(Role.ACOLYTE));
        verifier.verifyThat(MinDisConstraintProvider::spacingFromPriorPlan)
                .given(
                        assigned("a1", serviceAt("svc1", SUNDAY_10), ROLE_ACOLYTE, one),
                        new PriorAssignment(SUNDAY_10.toLocalDate().minusDays(1), one))
                .penalizesBy(1);
    }

    @Test
    void assignmentFarFromPriorPlanNotPenalized() {
        Server one = server("s1", Set.of(Role.ACOLYTE));
        verifier.verifyThat(MinDisConstraintProvider::spacingFromPriorPlan)
                .given(
                        assigned("a1", serviceAt("svc1", SUNDAY_10), ROLE_ACOLYTE, one),
                        new PriorAssignment(SUNDAY_10.toLocalDate().minusDays(7), one))
                .penalizesBy(0);
    }

    @Test
    void differentServerAcrossPriorPlanNotPenalized() {
        Server one = server("s1", Set.of(Role.ACOLYTE));
        Server two = server("s2", Set.of(Role.ACOLYTE));
        verifier.verifyThat(MinDisConstraintProvider::spacingFromPriorPlan)
                .given(
                        assigned("a1", serviceAt("svc1", SUNDAY_10), ROLE_ACOLYTE, one),
                        new PriorAssignment(SUNDAY_10.toLocalDate().minusDays(1), two))
                .penalizesBy(0);
    }

    @Test
    void underageServerPenalizedForAgeRestrictedRole() {
        Role thurifer14 = new Role(Role.THURIFER, "Thurifer", 14, null, 2);
        Server child = new Server("s1", "A", "B", "", LocalDate.of(2016, 1, 1), null,
                Set.of(Role.THURIFER), List.of(), Set.of(), false, true);
        verifier.verifyThat(MinDisConstraintProvider::ageWithinRoleRequirement)
                .given(assigned("a1", serviceAt("svc1", SUNDAY_10), thurifer14, child))
                .penalizesBy(1);
    }

    @Test
    void ageAppropriateServerNotPenalized() {
        Role thurifer14 = new Role(Role.THURIFER, "Thurifer", 14, null, 2);
        Server teen = new Server("s2", "C", "D", "", LocalDate.of(2008, 1, 1), null,
                Set.of(Role.THURIFER), List.of(), Set.of(), false, true);
        verifier.verifyThat(MinDisConstraintProvider::ageWithinRoleRequirement)
                .given(assigned("a2", serviceAt("svc1", SUNDAY_10), thurifer14, teen))
                .penalizesBy(0);
    }

    @Test
    void unknownBirthDateNotPenalizedForAgeRole() {
        Role thurifer14 = new Role(Role.THURIFER, "Thurifer", 14, null, 2);
        Server noBirthDate = server("s3", Set.of(Role.THURIFER)); // server(...) leaves birthDate null
        verifier.verifyThat(MinDisConstraintProvider::ageWithinRoleRequirement)
                .given(assigned("a3", serviceAt("svc1", SUNDAY_10), thurifer14, noBirthDate))
                .penalizesBy(0);
    }
}
