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
import org.mindis.core.persistence.AppDatabase;
import org.mindis.core.persistence.ArchivedServiceRepository;
import org.mindis.core.persistence.RoleRepository;
import org.mindis.core.persistence.ServerRepository;
import org.mindis.core.persistence.ServiceRepository;
import org.mindis.core.preferences.DataDirectory;
import org.mindis.core.preferences.PreferencesService;

/// Non-solving planning behavior: problem building and write-back onto slots.
/// The solver itself is exercised by [PlanningEndToEndTest], archiving by
/// [ArchiveServiceTest].
class PlanningServiceTest {

    @TempDir
    Path tempDir;

    private ServerRepository servers;
    private ServiceRepository services;
    private PlanningService planning;

    @BeforeEach
    void setUp() {
        servers = new ServerRepository();
        services = new ServiceRepository();
        // Roles are document content now, no longer seeded on first access -
        // give this document the built-in defaults the slots below reference.
        RoleRepository roles = new RoleRepository();
        AppDatabase.defaultRoles().forEach(roles::save);
        planning = new PlanningService(servers, services, roles,
                new PreferencesService(new DataDirectory(tempDir)),
                new ArchiveService(roles, servers, services, new ArchivedServiceRepository()));
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
