package org.mindis.core.planning;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import org.mindis.core.model.LiturgicalService;
import org.mindis.core.model.Role;
import org.mindis.core.model.Server;
import org.mindis.core.model.ServiceType;
import org.mindis.core.model.Slot;
import org.mindis.core.persistence.ArchivedServiceRepository;
import org.mindis.core.persistence.RoleRepository;
import org.mindis.core.persistence.ServerRepository;
import org.mindis.core.persistence.ServiceRepository;
import org.mindis.core.preferences.DataDirectory;
import org.mindis.core.preferences.PreferencesService;

/// Non-solving planning behavior: problem building, write-back onto slots, and
/// archiving. The solver itself is exercised by {@link PlanningEndToEndTest}.
class PlanningServiceTest {

    @TempDir
    Path tempDir;

    private ServerRepository servers;
    private ServiceRepository services;
    private ArchivedServiceRepository archived;
    private PlanningService planning;

    @BeforeEach
    void setUp() {
        DataDirectory dd = new DataDirectory(tempDir);
        servers = new ServerRepository(dd);
        services = new ServiceRepository(dd);
        archived = new ArchivedServiceRepository(dd);
        planning = new PlanningService(servers, services, new RoleRepository(dd),
                new PreferencesService(dd), archived);
    }

    @AfterEach
    void tearDown() {
        planning.close();
    }

    private void addServer() {
        servers.save(new Server("srv", "Anna", "B", "", null, null,
                Set.of(Role.ACOLYTE), List.of(), Set.of(), false, true));
    }

    private void addService(String id, LocalDate date, Slot slot) {
        services.save(new LiturgicalService(id, LocalDateTime.of(date, LocalTime.of(10, 0)), 60,
                "St. Mary", ServiceType.SUNDAY_MASS, List.of(slot), ""));
    }

    @Test
    void buildProblemPrePopulatesAssignmentsFromSlots() {
        addServer();
        addService("svc", LocalDate.of(2026, 8, 2), new Slot("s1", Role.ACOLYTE, "srv", true));

        ServicePlan plan = planning.buildProblem();

        assertEquals(1, plan.getAssignments().size());
        Assignment assignment = plan.getAssignments().getFirst();
        assertEquals("srv", assignment.getServer() == null ? null : assignment.getServer().id());
        assertTrue(assignment.isPinned());
    }

    @Test
    void writeBackStoresSolverPicksOntoSlots() {
        addServer();
        addService("svc", LocalDate.of(2026, 8, 2), new Slot("s1", Role.ACOLYTE, null, false));
        ServicePlan plan = planning.buildProblem();
        plan.getAssignments().getFirst().setServer(servers.findById("srv").orElseThrow());

        List<LiturgicalService> updated = planning.writeBack(plan, services.findAll());

        Slot slot = updated.getFirst().slots().getFirst();
        assertEquals("srv", slot.serverId());
    }

    @Test
    void archiveSnapshotsPastServicesAndReturnsRemovedIds() {
        addServer();
        addService("jul", LocalDate.of(2026, 7, 5), new Slot("s1", Role.ACOLYTE, "srv", false));
        addService("aug", LocalDate.of(2026, 8, 5), new Slot("s2", Role.ACOLYTE, null, false));

        ServiceArchiver.Result result = planning.archive(LocalDate.of(2026, 7, 31));

        assertEquals(List.of("jul"), result.removedServiceIds());
        assertEquals(1, archived.findAll().size());
        assertEquals("Anna B", archived.findAll().getFirst().slots().getFirst().serverName());
    }

    @Test
    void priorFromArchivedBridgesSpacingAcrossTheBoundary() {
        addServer();
        // Archive a service the day before the window start.
        addService("prev", LocalDate.of(2026, 7, 31), new Slot("s1", Role.ACOLYTE, "srv", false));
        planning.archive(LocalDate.of(2026, 7, 31));

        List<PriorAssignment> prior = planning.priorFromArchived(LocalDate.of(2026, 8, 1));

        assertEquals(1, prior.size());
        assertEquals("srv", prior.getFirst().server().id());
    }

    @Test
    void priorFromArchivedIgnoresServicesOutsideTheSpacingTail() {
        addServer();
        addService("old", LocalDate.of(2026, 7, 1), new Slot("s1", Role.ACOLYTE, "srv", false));
        planning.archive(LocalDate.of(2026, 7, 1));

        // Aug 1 is well past the 1-day spacing tail of a July 1 service.
        assertTrue(planning.priorFromArchived(LocalDate.of(2026, 8, 1)).isEmpty());
    }

    @Test
    void writeBackClearsSlotWhenAssignmentEmptied() {
        addServer();
        addService("svc", LocalDate.of(2026, 8, 2), new Slot("s1", Role.ACOLYTE, "srv", true));
        ServicePlan plan = planning.buildProblem();
        plan.getAssignments().getFirst().setServer(null);
        plan.getAssignments().getFirst().setPinned(false);

        List<LiturgicalService> updated = planning.writeBack(plan, services.findAll());

        assertFalse(updated.getFirst().slots().getFirst().serverId() != null);
    }
}
