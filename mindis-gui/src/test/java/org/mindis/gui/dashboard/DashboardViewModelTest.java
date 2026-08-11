package org.mindis.gui.dashboard;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import org.mindis.core.model.LiturgicalService;
import org.mindis.core.model.Role;
import org.mindis.core.model.Server;
import org.mindis.core.model.ServiceType;
import org.mindis.core.model.Slot;
import org.mindis.core.model.UnavailabilityPeriod;
import org.mindis.core.persistence.RoleRepository;
import org.mindis.core.persistence.ServerRepository;
import org.mindis.core.persistence.ServiceRepository;
import org.mindis.core.preferences.DashboardWidgetLayout;
import org.mindis.core.preferences.PreferencesService;

/// Covers the aggregation the dashboard is built from. Possible as a plain unit
/// test - no JavaFX toolkit, no stage - only because the view model returns
/// data rather than rendered text; the wording and formatting it used to own
/// now live in `DashboardView`.
class DashboardViewModelTest {

    @TempDir
    Path tempDir;

    private final ServiceRepository services = new ServiceRepository();
    private final ServerRepository servers = new ServerRepository();
    private final RoleRepository roles = new RoleRepository();

    private DashboardViewModel newViewModel() {
        // Never read in these tests (only loadLayout/saveLayout touch it), but
        // pointed at a temp file so a stray read cannot reach real preferences.
        return new DashboardViewModel(services, servers, roles, new TestablePreferencesService(
                tempDir.resolve("preferences.json")));
    }

    @Test
    void loadSnapshot_noServices_isEmpty() {
        DashboardViewModel.Snapshot snapshot = newViewModel().loadSnapshot();
        assertAll(
                () -> assertTrue(snapshot.isEmpty()),
                () -> assertEquals(0, snapshot.totalSlots()),
                () -> assertEquals(0, snapshot.unassignedSlots()),
                () -> assertTrue(snapshot.upcomingServices().isEmpty()),
                () -> assertTrue(snapshot.serverLoad().isEmpty()));
    }

    @Test
    void loadSnapshot_countsOpenSlotsAcrossAllServices() {
        services.save(service("s1", inDays(1), List.of(filled("ACOLYTE", "srv1"), Slot.open("ACOLYTE"))));
        services.save(service("s2", inDays(2), List.of(Slot.open("THURIFER"))));

        DashboardViewModel.Snapshot snapshot = newViewModel().loadSnapshot();

        assertAll(
                () -> assertEquals(3, snapshot.totalSlots()),
                () -> assertEquals(2, snapshot.unassignedSlots()),
                () -> assertTrue(!snapshot.isEmpty()));
    }

    /// A service in the past is not "upcoming" - it still counts toward the
    /// slot totals, which are about the document as a whole.
    @Test
    void loadSnapshot_upcomingServices_excludesPastOnes() {
        services.save(service("past", inDays(-1), List.of(Slot.open("ACOLYTE"))));
        services.save(service("future", inDays(1), List.of(Slot.open("ACOLYTE"))));

        DashboardViewModel.Snapshot snapshot = newViewModel().loadSnapshot();

        assertAll(
                () -> assertEquals(1, snapshot.upcomingServices().size()),
                () -> assertEquals(2, snapshot.totalSlots()));
    }

    @Test
    void loadSnapshot_upcomingServices_carriesAssignedAndTotalCounts() {
        services.save(service("s1", inDays(1),
                List.of(filled("ACOLYTE", "srv1"), filled("ACOLYTE", "srv2"), Slot.open("THURIFER"))));

        DashboardViewModel.UpcomingService upcoming = newViewModel().loadSnapshot().upcomingServices().getFirst();

        assertAll(
                () -> assertEquals(2, upcoming.assignedSlots()),
                () -> assertEquals(3, upcoming.totalSlots()),
                () -> assertEquals(ServiceType.SUNDAY_MASS, upcoming.type()),
                () -> assertEquals("St. Mary", upcoming.location()));
    }

    @Test
    void loadSnapshot_serverLoad_isMostLoadedFirstAndUsesDisplayNames() {
        servers.save(server("srv1", "Anna", "Becker"));
        servers.save(server("srv2", "Ben", "Meier"));
        services.save(service("s1", inDays(1),
                List.of(filled("ACOLYTE", "srv2"), filled("ACOLYTE", "srv2"), filled("ACOLYTE", "srv1"))));

        List<DashboardViewModel.ServerLoad> load = newViewModel().loadSnapshot().serverLoad();

        assertAll(
                () -> assertEquals(2, load.size()),
                () -> assertEquals(2L, load.getFirst().assignments()),
                () -> assertEquals(1L, load.get(1).assignments()),
                () -> assertTrue(load.getFirst().serverName().contains("Meier"),
                        "expected the display name, was: " + load.getFirst().serverName()));
    }

    /// A server deleted while still assigned leaves its id on the slot. The
    /// entry falls back to that id rather than vanishing, so the numbers still
    /// add up to the assigned-slot count.
    @Test
    void loadSnapshot_serverLoad_unknownServerIdFallsBackToTheId() {
        services.save(service("s1", inDays(1), List.of(filled("ACOLYTE", "ghost"))));

        List<DashboardViewModel.ServerLoad> load = newViewModel().loadSnapshot().serverLoad();

        assertAll(
                () -> assertEquals(1, load.size()),
                () -> assertEquals("ghost", load.getFirst().serverName()));
    }

    /// The figures the summary widget shows: every service still ahead (not
    /// only the ones the "next services" widget lists), the active roster, the
    /// configured roles and the share of slots that have a server.
    @Test
    void loadSnapshot_carriesTheSummaryFigures() {
        servers.save(server("srv1", "Anna", "Becker"));
        servers.save(inactive(server("srv2", "Ben", "Meier")));
        roles.save(new Role("ACOLYTE", "Acolyte", null, null, 0));
        services.save(service("past", inDays(-1), List.of(filled("ACOLYTE", "srv1"))));
        services.save(service("s1", inDays(1), List.of(filled("ACOLYTE", "srv1"), Slot.open("ACOLYTE"))));
        services.save(service("s2", inDays(2), List.of(Slot.open("ACOLYTE"))));

        DashboardViewModel.Snapshot snapshot = newViewModel().loadSnapshot();

        assertAll(
                () -> assertEquals(2, snapshot.upcomingServiceCount()),
                () -> assertEquals(1, snapshot.activeServers()),
                () -> assertEquals(1, snapshot.roles()),
                () -> assertEquals(2, snapshot.assignedSlots()),
                () -> assertEquals(50, snapshot.coveragePercent()));
    }

    /// Nothing planned is not "everything covered": an empty document reports
    /// zero coverage, so the summary cannot read as a finished plan.
    @Test
    void loadSnapshot_emptyDocument_hasZeroCoverage() {
        assertEquals(0, newViewModel().loadSnapshot().coveragePercent());
    }

    /// An active server nobody has been assigned to is exactly what this widget
    /// is for, so it appears with a load of zero rather than being left out.
    @Test
    void loadSnapshot_serverLoad_includesActiveServersWithoutAssignments() {
        servers.save(server("srv1", "Anna", "Becker"));
        servers.save(server("srv2", "Ben", "Meier"));
        servers.save(inactive(server("srv3", "Cara", "Vogt")));
        services.save(service("s1", inDays(1), List.of(filled("ACOLYTE", "srv2"))));

        List<DashboardViewModel.ServerLoad> load = newViewModel().loadSnapshot().serverLoad();

        assertAll(
                () -> assertEquals(2, load.size()),
                () -> assertEquals(1L, load.getFirst().assignments()),
                () -> assertEquals(0L, load.get(1).assignments()),
                () -> assertTrue(load.get(1).serverName().contains("Becker")));
    }

    /// Only services still ahead count here: an open slot in a service that has
    /// already happened cannot be staffed any more.
    @Test
    void loadSnapshot_openSlotsByRole_countsFutureOpenSlotsAndUsesRoleNames() {
        roles.save(new Role("ACOLYTE", "Acolyte", null, null, 0));
        services.save(service("past", inDays(-1), List.of(Slot.open("ACOLYTE"))));
        services.save(service("s1", inDays(1),
                List.of(Slot.open("ACOLYTE"), Slot.open("ACOLYTE"), filled("ACOLYTE", "srv1"))));
        services.save(service("s2", inDays(2), List.of(Slot.open("THURIFER"))));

        List<DashboardViewModel.RoleOpenSlots> open = newViewModel().loadSnapshot().openSlotsByRole();

        assertAll(
                () -> assertEquals(2, open.size()),
                () -> assertEquals("Acolyte", open.getFirst().roleName()),
                () -> assertEquals(2, open.getFirst().openSlots()),
                // No role saved under that id, so the raw id stands in.
                () -> assertEquals("THURIFER", open.get(1).roleName()),
                () -> assertEquals(1, open.get(1).openSlots()));
    }

    @Test
    void loadSnapshot_serviceTypeMix_countsUpcomingServicesPerType() {
        services.save(service("past", inDays(-1), List.of(Slot.open("ACOLYTE"))));
        services.save(service("s1", inDays(1), List.of(Slot.open("ACOLYTE"))));
        services.save(service("s2", inDays(2), List.of(Slot.open("ACOLYTE"))));
        services.save(new LiturgicalService("s3", inDays(3), 60, "St. Mary", ServiceType.WEDDING,
                List.of(Slot.open("ACOLYTE")), ""));

        List<DashboardViewModel.ServiceTypeCount> mix = newViewModel().loadSnapshot().serviceTypeMix();

        assertAll(
                () -> assertEquals(2, mix.size()),
                () -> assertEquals(ServiceType.SUNDAY_MASS, mix.getFirst().type()),
                () -> assertEquals(2, mix.getFirst().count()),
                () -> assertEquals(ServiceType.WEDDING, mix.get(1).type()),
                () -> assertEquals(1, mix.get(1).count()));
    }

    /// A fixed span of weeks starting with the current one, so a week without
    /// services shows up as an empty week rather than being skipped.
    @Test
    void loadSnapshot_coverageTrend_bucketsSlotsIntoWholeWeeks() {
        services.save(service("s1", inDays(1), List.of(filled("ACOLYTE", "srv1"), Slot.open("ACOLYTE"))));

        List<DashboardViewModel.WeekCoverage> trend = newViewModel().loadSnapshot().coverageTrend();
        DashboardViewModel.WeekCoverage weekOfTheService = trend.stream()
                .filter(week -> !week.weekStart().isAfter(inDays(1).toLocalDate())
                        && week.weekStart().plusWeeks(1).isAfter(inDays(1).toLocalDate()))
                .findFirst()
                .orElseThrow();

        assertAll(
                () -> assertEquals(8, trend.size()),
                () -> assertEquals(DayOfWeek.MONDAY, trend.getFirst().weekStart().getDayOfWeek()),
                () -> assertEquals(1, weekOfTheService.assignedSlots()),
                () -> assertEquals(1, weekOfTheService.openSlots()),
                () -> assertEquals(2, trend.stream()
                        .mapToInt(week -> week.assignedSlots() + week.openSlots())
                        .sum()));
    }

    /// The comparison is against the *peak* need - the most slots one service
    /// asks of that role - because that is what has to be covered at once.
    @Test
    void loadSnapshot_qualificationCoverage_comparesQualifiedServersWithThePeakNeed() {
        roles.save(new Role("ACOLYTE", "Acolyte", null, null, 0));
        roles.save(new Role("THURIFER", "Thurifer", null, null, 1));
        servers.save(qualified(server("srv1", "Anna", "Becker"), "ACOLYTE"));
        servers.save(inactive(qualified(server("srv2", "Ben", "Meier"), "ACOLYTE")));
        services.save(service("s1", inDays(1), List.of(Slot.open("ACOLYTE"), Slot.open("ACOLYTE"))));
        services.save(service("s2", inDays(2), List.of(Slot.open("ACOLYTE"))));

        List<DashboardViewModel.RoleQualification> coverage = newViewModel().loadSnapshot()
                .qualificationCoverage();

        assertAll(
                () -> assertEquals(2, coverage.size()),
                // Short roles come first: one active qualified server, two
                // needed at once - the inactive one does not count.
                () -> assertEquals("Acolyte", coverage.getFirst().roleName()),
                () -> assertEquals(1, coverage.getFirst().qualifiedServers()),
                () -> assertEquals(2, coverage.getFirst().peakSlots()),
                () -> assertTrue(coverage.getFirst().isShort()),
                () -> assertTrue(!coverage.get(1).isShort()));
    }

    @Test
    void loadSnapshot_absencesAhead_keepsActiveServersAbsentWithinTheHorizon() {
        servers.save(absent(server("srv1", "Anna", "Becker"), LocalDate.now().plusDays(3),
                LocalDate.now().plusDays(10)));
        servers.save(absent(server("srv2", "Ben", "Meier"), LocalDate.now().plusYears(1),
                LocalDate.now().plusYears(1).plusDays(3)));
        servers.save(absent(server("srv3", "Cara", "Vogt"), LocalDate.now().minusDays(20),
                LocalDate.now().minusDays(10)));
        servers.save(inactive(absent(server("srv4", "Dana", "Roth"), LocalDate.now(), LocalDate.now())));

        List<DashboardViewModel.Absence> absences = newViewModel().loadSnapshot().absencesAhead();

        assertAll(
                () -> assertEquals(1, absences.size()),
                () -> assertTrue(absences.getFirst().serverName().contains("Becker")),
                () -> assertEquals(LocalDate.now().plusDays(3), absences.getFirst().start()));
    }

    @Test
    void loadSnapshot_rosterIssues_reportsEachKind() {
        servers.save(inactive(qualified(server("srv1", "Anna", "Becker"), "ACOLYTE")));
        servers.save(server("srv2", "Ben", "Meier"));
        servers.save(qualified(server("srv3", "Cara", "Vogt"), "ACOLYTE"));
        servers.save(absent(qualified(server("srv4", "Dana", "Roth"), "ACOLYTE"),
                LocalDate.now().plusDays(1), LocalDate.now().plusDays(2)));
        services.save(service("s1", inDays(1),
                List.of(filled("ACOLYTE", "srv1"), filled("ACOLYTE", "srv4"))));

        List<DashboardViewModel.RosterIssue> issues = newViewModel().loadSnapshot().rosterIssues();

        assertAll(
                () -> assertEquals(4, issues.size()),
                () -> assertTrue(issues.stream().anyMatch(issue -> issue.kind()
                        == DashboardViewModel.RosterIssueKind.INACTIVE_BUT_ASSIGNED
                        && issue.serverName().contains("Becker"))),
                () -> assertTrue(issues.stream().anyMatch(issue -> issue.kind()
                        == DashboardViewModel.RosterIssueKind.NO_QUALIFICATIONS
                        && issue.serverName().contains("Meier"))),
                () -> assertTrue(issues.stream().anyMatch(issue -> issue.kind()
                        == DashboardViewModel.RosterIssueKind.NO_UPCOMING_DUTY
                        && issue.serverName().contains("Vogt"))),
                () -> assertTrue(issues.stream().anyMatch(issue -> issue.kind()
                        == DashboardViewModel.RosterIssueKind.ASSIGNED_WHILE_UNAVAILABLE
                        && issue.serverName().contains("Roth"))));
    }

    /// A healthy roster reports nothing at all, so the widget can say so rather
    /// than showing an empty list of unnamed problems.
    @Test
    void loadSnapshot_rosterIssues_areEmptyWhenEverythingIsInOrder() {
        servers.save(qualified(server("srv1", "Anna", "Becker"), "ACOLYTE"));
        services.save(service("s1", inDays(1), List.of(filled("ACOLYTE", "srv1"))));

        assertTrue(newViewModel().loadSnapshot().rosterIssues().isEmpty());
    }

    @Test
    void loadLayout_neverArranged_returnsEveryWidgetTypeOnce() {
        List<WidgetPlacement> layout = newViewModel().loadLayout();

        assertEquals(List.of(WidgetType.values()), layout.stream().map(WidgetPlacement::type).toList());
    }

    @Test
    void loadLayout_neverArranged_usesEachTypesDefaultMode() {
        for (WidgetPlacement placement : newViewModel().loadLayout()) {
            assertEquals(placement.type().defaultMode(), placement.mode());
        }
    }

    @Test
    void saveLayout_thenLoadLayout_keepsGeometryAndViewMode() {
        DashboardViewModel viewModel = newViewModel();
        WidgetPlacement saved = new WidgetPlacement(WidgetType.SERVER_LOAD, 3, 2, 4, 5,
                WidgetType.SERVER_LOAD.defaultMode());

        viewModel.saveLayout(List.of(saved));

        assertEquals(List.of(saved), viewModel.loadLayout());
    }

    /// A layout entry whose stored mode this version does not know - written by
    /// a newer version, or by one that offered a mode since dropped - must not
    /// lose the widget; it falls back to the type's default mode.
    @Test
    void loadLayout_unknownOrUnsupportedMode_fallsBackToTheDefault() {
        PreferencesService preferences = new TestablePreferencesService(tempDir.resolve("preferences.json"));
        preferences.update(p -> p.withDashboardWidgets(List.of(
                new DashboardWidgetLayout(WidgetType.SERVER_LOAD.id(), 0, 0, 6, 3, "sunburst"),
                new DashboardWidgetLayout(WidgetType.NEXT_SERVICES.id(), 0, 3, 6, 3, null))));
        DashboardViewModel viewModel = new DashboardViewModel(services, servers, roles, preferences);

        List<WidgetPlacement> layout = viewModel.loadLayout();

        assertAll(
                () -> assertEquals(2, layout.size()),
                () -> assertEquals(WidgetType.SERVER_LOAD.defaultMode(), layout.getFirst().mode()),
                () -> assertEquals(WidgetType.NEXT_SERVICES.defaultMode(), layout.get(1).mode()));
    }

    private static LocalDateTime inDays(int days) {
        return LocalDateTime.now().plusDays(days);
    }

    private static LiturgicalService service(String id, LocalDateTime dateTime, List<Slot> slots) {
        return new LiturgicalService(id, dateTime, 60, "St. Mary", ServiceType.SUNDAY_MASS, slots, "");
    }

    private static Slot filled(String role, String serverId) {
        return new Slot(Slot.newId(), role, serverId, false);
    }

    private static Server server(String id, String firstName, String lastName) {
        return new Server(id, firstName, lastName, "", null, null, Set.of(), List.of(), Set.of(), false, true);
    }

    private static Server qualified(Server server, String... roleIds) {
        return new Server(server.id(), server.firstName(), server.lastName(), server.contact(), server.birthDate(),
                server.familyId(), Set.of(roleIds), server.unavailabilities(), server.preferredTimes(),
                server.experienced(), server.active());
    }

    private static Server absent(Server server, LocalDate start, LocalDate end) {
        return new Server(server.id(), server.firstName(), server.lastName(), server.contact(), server.birthDate(),
                server.familyId(), server.qualifications(), List.of(new UnavailabilityPeriod(start, end)),
                server.preferredTimes(), server.experienced(), server.active());
    }

    private static Server inactive(Server server) {
        return new Server(server.id(), server.firstName(), server.lastName(), server.contact(), server.birthDate(),
                server.familyId(), server.qualifications(), server.unavailabilities(), server.preferredTimes(),
                server.experienced(), false);
    }

    /// Exposes the package-private path constructor, as `UiPreferencesTest`
    /// does - the real one resolves the user's data directory.
    private static final class TestablePreferencesService extends PreferencesService {
        TestablePreferencesService(Path file) {
            super(file);
        }
    }
}
