package org.mindis.core.planning;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import org.mindis.core.model.LiturgicalService;
import org.mindis.core.model.ServiceType;
import org.mindis.core.model.Slot;
import org.mindis.core.persistence.PlanRepository;
import org.mindis.core.persistence.RoleRepository;
import org.mindis.core.persistence.ServerRepository;
import org.mindis.core.persistence.ServiceRepository;
import org.mindis.core.preferences.DataDirectory;
import org.mindis.core.preferences.PreferencesService;

/// Tests the non-solving Autofill planning arithmetic: window resolution and
/// the solve-window/plan-range split (archive clamp + prior-open union).
class PlanningServiceAutofillTest {

    @TempDir
    java.nio.file.Path tempDir;

    private ServiceRepository services;
    private PlanRepository plans;
    private PlanningService service;

    @BeforeEach
    void setUp() {
        DataDirectory dd = new DataDirectory(tempDir);
        services = new ServiceRepository(dd);
        plans = new PlanRepository(dd);
        service = new PlanningService(new ServerRepository(dd), services,
                new RoleRepository(dd), new PreferencesService(dd), plans);
    }

    @AfterEach
    void closeService() {
        service.close();
    }

    private void addService(String id, LocalDate date) {
        services.save(new LiturgicalService(id, LocalDateTime.of(date, LocalTime.of(10, 0)),
                60, "St. Mary", ServiceType.SUNDAY_MASS, List.<Slot>of(), ""));
    }

    private static AcceptedPlan plan(LocalDate from, LocalDate to) {
        return new AcceptedPlan(from, to, List.of(), null, false, null);
    }

    @Test
    void resolveWindowFillsBlankBoundsFromServiceDateExtent() {
        addService("a", LocalDate.of(2026, 7, 3));
        addService("b", LocalDate.of(2026, 7, 20));

        assertEquals(new PlanningService.DateWindow(LocalDate.of(2026, 7, 3), LocalDate.of(2026, 7, 20)),
                service.resolveAutofillWindow(null, null).orElseThrow());
        assertEquals(LocalDate.of(2026, 7, 20),
                service.resolveAutofillWindow(LocalDate.of(2026, 7, 10), null).orElseThrow().toInclusive());
        assertEquals(LocalDate.of(2026, 7, 3),
                service.resolveAutofillWindow(null, LocalDate.of(2026, 7, 10)).orElseThrow().from());
    }

    @Test
    void resolveWindowEmptyWithoutServicesOrOnReversedBounds() {
        assertTrue(service.resolveAutofillWindow(null, null).isEmpty());
        addService("a", LocalDate.of(2026, 7, 3));
        assertTrue(service.resolveAutofillWindow(LocalDate.of(2026, 8, 1), LocalDate.of(2026, 7, 1)).isEmpty());
    }

    @Test
    void planAutofillClampsSolveWindowToAfterTheLatestArchivedPeriod() {
        // Archive July; leave one stray July service plus August services.
        plans.applyArchiveSplit(plan(LocalDate.of(2026, 7, 1), LocalDate.of(2026, 7, 31)), null);
        addService("jul", LocalDate.of(2026, 7, 20));
        addService("aug1", LocalDate.of(2026, 8, 2));
        addService("aug2", LocalDate.of(2026, 8, 20));

        PlanningService.AutofillPlan autofill = service.planAutofill(null, null).orElseThrow();

        // Blank from would be July 20, but that is inside the archived period -
        // clamp up to the day after it.
        assertEquals(LocalDate.of(2026, 8, 1), autofill.solveWindow().from());
        assertEquals(LocalDate.of(2026, 8, 1), autofill.planRange().from());
        assertEquals(LocalDate.of(2026, 8, 20), autofill.planRange().toInclusive());
    }

    @Test
    void planRangeExtendsBackToCoverTheWholePriorOpenPlan() {
        plans.save(plan(LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 15)));
        addService("sep", LocalDate.of(2026, 9, 6));

        // Forward-only fill from September: the plan range still spans the
        // open plan's earlier August start so it isn't truncated on save.
        PlanningService.AutofillPlan autofill = service.planAutofill(LocalDate.of(2026, 9, 1), null).orElseThrow();

        assertEquals(LocalDate.of(2026, 9, 1), autofill.solveWindow().from());
        assertEquals(LocalDate.of(2026, 8, 1), autofill.planRange().from());
        assertEquals(LocalDate.of(2026, 9, 6), autofill.planRange().toInclusive());
    }

    @Test
    void archiveSnapshotsAndReturnsTheFrozenServices() {
        addService("jul", LocalDate.of(2026, 7, 5));
        addService("aug", LocalDate.of(2026, 8, 5));
        // Open plan covering both, with one assignment per service.
        plans.save(new AcceptedPlan(LocalDate.of(2026, 7, 1), LocalDate.of(2026, 8, 31),
                List.of(new AcceptedPlan.PlannedAssignment("jul:s", "jul", "ACOLYTE", "srv", false),
                        new AcceptedPlan.PlannedAssignment("aug:s", "aug", "ACOLYTE", "srv", false)),
                null, false, null));

        // Archive July: the July service is snapshotted and returned; August
        // stays in the open remainder.
        List<LiturgicalService> archived = service.archiveOpenPlan(LocalDate.of(2026, 7, 31));

        assertEquals(1, archived.size());
        assertEquals("jul", archived.getFirst().id());
        AcceptedPlan frozen = plans.listArchived().getFirst();
        assertEquals(1, frozen.archivedServices().size());
        assertEquals("jul", frozen.archivedServices().getFirst().id());
        assertEquals(LocalDate.of(2026, 8, 1), plans.load().orElseThrow().from());
    }
}
