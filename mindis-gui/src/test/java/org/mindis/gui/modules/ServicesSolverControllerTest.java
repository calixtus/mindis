package org.mindis.gui.modules;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import org.mindis.core.export.PlanExportService;
import org.mindis.core.model.LiturgicalService;
import org.mindis.core.model.Role;
import org.mindis.core.model.Server;
import org.mindis.core.model.ServiceType;
import org.mindis.core.model.Slot;
import org.mindis.core.persistence.ArchivedServiceRepository;
import org.mindis.core.persistence.RoleRepository;
import org.mindis.core.persistence.ServerRepository;
import org.mindis.core.persistence.ServiceRepository;
import org.mindis.core.planning.PlanningService;
import org.mindis.core.preferences.PreferencesService;
import org.mindis.gui.planning.PlanningViewModel;
import org.mindis.gui.shell.ShellOverlays;

/// Covers the guard paths of the solver controller - the branches that decide
/// *not* to start a solve. Those are what collapsing three near-identical
/// solveAsync call sites into one `launch()` could plausibly have broken, and
/// they run without a JavaFX toolkit because they return before any
/// `Platform.runLater` callback is wired.
///
/// A started solve is deliberately out of scope here: it hops onto the FX
/// thread and runs the real Timefold solver, which belongs in an end-to-end
/// test (`PlanningEndToEndTest` in core covers the solving itself).
// NullAway: JUnit injects @TempDir after construction, so the collaborators it
// feeds are built in newController() rather than in a field initializer.
@SuppressWarnings("NullAway.Init")
class ServicesSolverControllerTest {

    @TempDir
    Path tempDir;

    private final ServerRepository servers = new ServerRepository();
    private final ServiceRepository services = new ServiceRepository();
    private final RoleRepository roles = new RoleRepository();
    private final ArchivedServiceRepository archived = new ArchivedServiceRepository();

    private final List<List<LiturgicalService>> applied = new ArrayList<>();
    private PlanningService planningService;
    private PlanningViewModel planningViewModel;

    @AfterEach
    void closeSolver() {
        if (planningService != null) {
            planningService.close();
        }
    }

    private ServicesSolverController newController() {
        PreferencesService preferences = new TestablePreferencesService(tempDir.resolve("preferences.json"));
        planningService = new PlanningService(servers, services, roles, preferences, archived);
        planningViewModel = new PlanningViewModel(planningService, preferences,
                new PlanExportService(servers, roles), archived);
        return new ServicesSolverController(planningViewModel,
                services::findAll,
                applied::add,
                // Only ever resolved by confirmAbort, which these tests do
                // not reach - so no toolkit is needed to build one.
                new ShellOverlays(() -> {
                    throw new AssertionError("no overlay expected in this test");
                }));
    }

    @Test
    void solveAll_noServices_doesNotStartSolving() {
        ServicesSolverController controller = newController();

        controller.solveAll();

        assertAll(
                () -> assertFalse(planningViewModel.solvingProperty().get()),
                () -> assertTrue(applied.isEmpty()));
    }

    /// Services with no slots produce no assignments, so there is nothing to
    /// solve even though the document is not empty.
    @Test
    void solveAll_servicesWithoutSlots_doesNotStartSolving() {
        services.save(service("s1", List.of()));

        ServicesSolverController controller = newController();
        controller.solveAll();

        assertFalse(planningViewModel.solvingProperty().get());
    }

    @Test
    void autofillWindow_windowMatchesNothing_doesNotStartSolving() {
        roles.save(new Role("ACOLYTE", "Acolyte", null, null, 0));
        servers.save(server("srv1"));
        services.save(service("s1", List.of(Slot.open("ACOLYTE"))));

        ServicesSolverController controller = newController();
        // The one service sits a day out; this window is far in the past.
        controller.autofillWindow(LocalDate.now().minusYears(2), LocalDate.now().minusYears(1), false);

        assertAll(
                () -> assertFalse(planningViewModel.solvingProperty().get()),
                () -> assertTrue(applied.isEmpty()));
    }

    /// Without `overwrite`, an already-filled slot is not eligible, so a window
    /// containing only filled slots starts nothing.
    @Test
    void autofillWindow_allSlotsFilledAndNoOverwrite_doesNotStartSolving() {
        roles.save(new Role("ACOLYTE", "Acolyte", null, null, 0));
        servers.save(server("srv1"));
        services.save(service("s1", List.of(new Slot(Slot.newId(), "ACOLYTE", "srv1", false))));

        ServicesSolverController controller = newController();
        controller.autofillWindow(null, null, false);

        assertFalse(planningViewModel.solvingProperty().get());
    }

    // Not covered here: autofillService()'s "already solving" guard. Every
    // synchronously observable value (solving, progress, the applied services)
    // reads the same whether it bails out or starts a second solve, so a test
    // asserting on them passes with the guard deleted - verified by mutation,
    // which is why it is absent rather than green-but-meaningless. Catching it
    // needs a started solve, i.e. an FX-thread end-to-end test.

    @Test
    void refreshScore_emptyPlan_isANoOp() {
        ServicesSolverController controller = newController();

        controller.refreshScore();

        assertFalse(planningViewModel.solvingProperty().get());
    }

    private static LiturgicalService service(String id, List<Slot> slots) {
        return new LiturgicalService(id, LocalDateTime.now().plusDays(1), 60,
                "St. Mary", ServiceType.SUNDAY_MASS, slots, "");
    }

    private static Server server(String id) {
        return new Server(id, "Anna", "Becker", "", null, null,
                Set.of("ACOLYTE"), List.of(), Set.of(), false, true);
    }

    /// Exposes the package-private path constructor, as `UiPreferencesTest`
    /// does - the real one resolves the user's data directory.
    private static final class TestablePreferencesService extends PreferencesService {
        TestablePreferencesService(Path file) {
            super(file);
        }
    }
}
