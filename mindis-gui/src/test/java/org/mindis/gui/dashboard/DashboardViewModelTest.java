package org.mindis.gui.dashboard;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import org.mindis.core.model.LiturgicalService;
import org.mindis.core.model.Server;
import org.mindis.core.model.ServiceType;
import org.mindis.core.model.Slot;
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

    private DashboardViewModel newViewModel() {
        // Never read in these tests (only loadLayout/saveLayout touch it), but
        // pointed at a temp file so a stray read cannot reach real preferences.
        return new DashboardViewModel(services, servers, new TestablePreferencesService(
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
        DashboardViewModel viewModel = new DashboardViewModel(services, servers, preferences);

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

    /// Exposes the package-private path constructor, as `UiPreferencesTest`
    /// does - the real one resolves the user's data directory.
    private static final class TestablePreferencesService extends PreferencesService {
        TestablePreferencesService(Path file) {
            super(file);
        }
    }
}
