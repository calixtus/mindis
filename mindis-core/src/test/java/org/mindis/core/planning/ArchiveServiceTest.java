package org.mindis.core.planning;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.mindis.core.model.LiturgicalService;
import org.mindis.core.model.Role;
import org.mindis.core.model.Server;
import org.mindis.core.model.ServiceType;
import org.mindis.core.model.Slot;
import org.mindis.core.persistence.ArchivedServiceRepository;
import org.mindis.core.persistence.RoleRepository;
import org.mindis.core.persistence.ServerRepository;
import org.mindis.core.persistence.ServiceRepository;

/// Freezing past services into snapshots, and reading them back as solver
/// facts. Split out of [PlanningServiceTest] along with the code: no solver is
/// constructed here, so these run without one.
class ArchiveServiceTest {

    private final ServerRepository servers = new ServerRepository();
    private final ServiceRepository services = new ServiceRepository();
    private final RoleRepository roles = new RoleRepository();
    private final ArchivedServiceRepository archived = new ArchivedServiceRepository();

    private ArchiveService archiveService;

    @BeforeEach
    void setUp() {
        // Roles are document content, not seeded on first access - give this
        // document the built-in defaults the slots below reference.
        org.mindis.core.persistence.AppDatabase.defaultRoles().forEach(roles::save);
        archiveService = new ArchiveService(roles, servers, services, archived);
    }

    @Test
    void archive_pastServices_snapshotsThemAndReturnsRemovedIds() {
        addServer();
        addService("jul", LocalDate.of(2026, 7, 5), new Slot("s1", Role.ACOLYTE, "srv", false));
        addService("aug", LocalDate.of(2026, 8, 5), new Slot("s2", Role.ACOLYTE, null, false));

        ServiceArchiver.Result result = archiveService.archive(LocalDate.of(2026, 7, 31));

        assertEquals(List.of("jul"), result.removedServiceIds());
        assertEquals(1, archived.findAll().size());
        // Names are resolved at archive time, so the snapshot survives the
        // server being renamed or deleted later.
        assertEquals("Anna B", archived.findAll().getFirst().slots().getFirst().serverName());
    }

    @Test
    void archive_nothingBeforeCutoff_returnsEmpty() {
        addServer();
        addService("aug", LocalDate.of(2026, 8, 5), new Slot("s1", Role.ACOLYTE, "srv", false));

        ServiceArchiver.Result result = archiveService.archive(LocalDate.of(2026, 7, 31));

        assertAllEmpty(result);
    }

    @Test
    void priorFromArchived_serviceInTheSpacingTail_bridgesAcrossTheBoundary() {
        addServer();
        // Archive a service the day before the window start.
        addService("prev", LocalDate.of(2026, 7, 31), new Slot("s1", Role.ACOLYTE, "srv", false));
        archiveService.archive(LocalDate.of(2026, 7, 31));

        List<PriorAssignment> prior = archiveService.priorFromArchived(LocalDate.of(2026, 8, 1));

        assertEquals(1, prior.size());
        assertEquals("srv", prior.getFirst().server().id());
    }

    @Test
    void priorFromArchived_serviceOutsideTheTail_isIgnored() {
        addServer();
        addService("old", LocalDate.of(2026, 7, 1), new Slot("s1", Role.ACOLYTE, "srv", false));
        archiveService.archive(LocalDate.of(2026, 7, 1));

        // Aug 1 is well past the spacing tail of a July 1 service.
        assertTrue(archiveService.priorFromArchived(LocalDate.of(2026, 8, 1)).isEmpty());
    }

    /// No live services to place means no window to bridge into.
    @Test
    void priorFromArchived_noEarliestDate_isEmpty() {
        assertTrue(archiveService.priorFromArchived(null).isEmpty());
    }

    @Test
    void deleteArchived_removesTheSnapshot() {
        addServer();
        addService("jul", LocalDate.of(2026, 7, 5), new Slot("s1", Role.ACOLYTE, "srv", false));
        archiveService.archive(LocalDate.of(2026, 7, 31));

        archiveService.deleteArchived(archiveService.listArchived().getFirst().id());

        assertTrue(archiveService.listArchived().isEmpty());
    }

    private static void assertAllEmpty(ServiceArchiver.Result result) {
        assertTrue(result.removedServiceIds().isEmpty());
        assertTrue(result.archived().isEmpty());
    }

    private void addServer() {
        servers.save(new Server("srv", "Anna", "B", "", null, null,
                Set.of(Role.ACOLYTE), List.of(), Set.of(), false, true));
    }

    private void addService(String id, LocalDate date, Slot slot) {
        services.save(new LiturgicalService(id, LocalDateTime.of(date, LocalTime.of(10, 0)), 60,
                "St. Mary", ServiceType.SUNDAY_MASS, List.of(slot), ""));
    }
}
