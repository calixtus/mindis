package org.mindis.gui.planning;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;

import javafx.application.Platform;
import javafx.embed.swing.JFXPanel;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import org.mindis.core.export.PlanExportService;
import org.mindis.core.model.LiturgicalService;
import org.mindis.core.model.Role;
import org.mindis.core.model.Server;
import org.mindis.core.model.ServiceType;
import org.mindis.core.model.Slot;
import org.mindis.core.persistence.PlanRepository;
import org.mindis.core.persistence.RoleRepository;
import org.mindis.core.persistence.ServerRepository;
import org.mindis.core.persistence.ServiceRepository;
import org.mindis.core.planning.Assignment;
import org.mindis.core.planning.PlanningService;
import org.mindis.core.planning.ServicePlan;
import org.mindis.core.preferences.DataDirectory;
import org.mindis.core.preferences.PreferencesService;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/// Verifies which slots an Autofill run leaves eligible for the solver: only
/// those inside the window, not archived, and either empty or (with overwrite)
/// already assigned. Everything else stays pinned.
class AutofillScopeTest {

    private static final LocalDate WINDOW_FROM = LocalDate.of(2026, 8, 1);
    private static final LocalDate WINDOW_TO = LocalDate.of(2026, 8, 31);
    private static final Role ACOLYTE = new Role(Role.ACOLYTE, "Acolyte", null, null, 0);
    private static final Server SERVER = new Server("s1", "A", "B", "", null, null,
            Set.of(Role.ACOLYTE), List.of(), Set.of(), false, true);

    private static Assignment assignment(String id, LocalDate date) {
        LiturgicalService svc = new LiturgicalService(id, LocalDateTime.of(date, LocalTime.of(10, 0)),
                60, "St. Mary", ServiceType.SUNDAY_MASS, List.<Slot>of(), "");
        return new Assignment(id, svc, ACOLYTE);
    }

    private PlanningViewModel newViewModel(java.nio.file.Path dir) {
        DataDirectory dd = new DataDirectory(dir);
        ServerRepository servers = new ServerRepository(dd);
        ServiceRepository services = new ServiceRepository(dd);
        RoleRepository roles = new RoleRepository(dd);
        PreferencesService prefs = new PreferencesService(dd);
        PlanRepository plans = new PlanRepository(dd);
        PlanningService planning = new PlanningService(servers, services, roles, prefs, plans);
        return new PlanningViewModel(planning, plans, prefs, new PlanExportService(servers, services, roles));
    }

    private void onFxThread(Runnable body) throws Exception {
        new JFXPanel();
        CountDownLatch latch = new CountDownLatch(1);
        Throwable[] error = new Throwable[1];
        Platform.runLater(() -> {
            try {
                body.run();
            } catch (Throwable t) {
                error[0] = t;
            } finally {
                latch.countDown();
            }
        });
        latch.await();
        if (error[0] != null) {
            throw new AssertionError(error[0]);
        }
    }

    @Test
    void leavesOnlyEmptyInWindowNonArchivedSlotsEligibleWhenNotOverwriting(@TempDir java.nio.file.Path dir) throws Exception {
        onFxThread(() -> {
            PlanningViewModel vm = newViewModel(dir);

            Assignment newSlot = assignment("new", LocalDate.of(2026, 8, 10));
            Assignment assignedSlot = assignment("assigned", LocalDate.of(2026, 8, 12));
            assignedSlot.setServer(SERVER);
            Assignment archivedSlot = assignment("archived", LocalDate.of(2026, 8, 5));
            Assignment outsideSlot = assignment("outside", LocalDate.of(2026, 9, 5));
            ServicePlan problem = new ServicePlan(List.of(SERVER),
                    List.of(newSlot, assignedSlot, archivedSlot, outsideSlot));

            PlanningViewModel.AutofillScope scope = vm.beginAutofillWindow(
                    problem, WINDOW_FROM, WINDOW_TO, false, Set.of("archived"));

            // Only the empty, in-window, non-archived slot is eligible.
            assertTrue(scope.eligibleIds().contains("new"));
            assertFalse(scope.eligibleIds().contains("assigned"));
            assertFalse(scope.eligibleIds().contains("archived"));
            assertFalse(scope.eligibleIds().contains("outside"));
            assertFalse(newSlot.isPinned());
            assertTrue(assignedSlot.isPinned());
            assertTrue(archivedSlot.isPinned());
            assertTrue(outsideSlot.isPinned());
        });
    }

    @Test
    void overwriteAlsoFreesAlreadyAssignedInWindowSlots(@TempDir java.nio.file.Path dir) throws Exception {
        onFxThread(() -> {
            PlanningViewModel vm = newViewModel(dir);

            Assignment newSlot = assignment("new", LocalDate.of(2026, 8, 10));
            Assignment assignedSlot = assignment("assigned", LocalDate.of(2026, 8, 12));
            assignedSlot.setServer(SERVER);
            Assignment archivedSlot = assignment("archived", LocalDate.of(2026, 8, 5));
            archivedSlot.setServer(SERVER);
            ServicePlan problem = new ServicePlan(List.of(SERVER),
                    List.of(newSlot, assignedSlot, archivedSlot));

            PlanningViewModel.AutofillScope scope = vm.beginAutofillWindow(
                    problem, WINDOW_FROM, WINDOW_TO, true, Set.of("archived"));

            // Overwrite frees the already-assigned in-window slot, but archived
            // stays pinned regardless of the toggle.
            assertTrue(scope.eligibleIds().contains("new"));
            assertTrue(scope.eligibleIds().contains("assigned"));
            assertFalse(scope.eligibleIds().contains("archived"));
            assertFalse(assignedSlot.isPinned());
            assertTrue(archivedSlot.isPinned());
        });
    }
}
