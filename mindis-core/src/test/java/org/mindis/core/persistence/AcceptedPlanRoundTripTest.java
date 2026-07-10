package org.mindis.core.persistence;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

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
import org.mindis.core.planning.AcceptedPlan;
import org.mindis.core.planning.Assignment;
import org.mindis.core.planning.PlanMapper;
import org.mindis.core.planning.ServicePlan;


class AcceptedPlanRoundTripTest {

    @TempDir
    java.nio.file.Path tempDir;

    private static final Role ROLE_ACOLYTE = new Role(Role.ACOLYTE, "Acolyte", null, null, 0);
    private static final Server ANNA =
            new Server("srv-1", "Anna", "Muster", "", null, null, Set.of(Role.ACOLYTE), List.of(), Set.of(), false, true);
    private static final Slot SLOT_0 = new Slot("slot-0", Role.ACOLYTE);
    private static final Slot SLOT_1 = new Slot("slot-1", Role.ACOLYTE);
    private static final LiturgicalService MASS = new LiturgicalService(
            "svc-1", LocalDateTime.of(2026, 8, 2, 10, 0), 60, "St. Mary",
            ServiceType.SUNDAY_MASS, List.of(SLOT_0, SLOT_1), "");

    @Test
    void planSurvivesRoundTripAndReapplies() {
        Assignment assigned = new Assignment("svc-1:slot-0", MASS, ROLE_ACOLYTE);
        assigned.setServer(ANNA);
        assigned.setPinned(true);
        Assignment empty = new Assignment("svc-1:slot-1", MASS, ROLE_ACOLYTE);
        ServicePlan plan = new ServicePlan(List.of(ANNA), List.of(assigned, empty));

        AcceptedPlan accepted = PlanMapper.toAcceptedPlan(
                plan, LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 31));

        PlanRepository repository = new PlanRepository(tempDir.resolve("plan.json"));
        repository.save(accepted);
        AcceptedPlan reloaded = new PlanRepository(tempDir.resolve("plan.json")).load().orElseThrow();

        // save() stamps savedAt, so reloaded isn't identical to accepted
        // (which has savedAt == null) - compare the content, then check the
        // stamp landed.
        assertEquals(accepted.from(), reloaded.from());
        assertEquals(accepted.toInclusive(), reloaded.toInclusive());
        assertEquals(accepted.assignments(), reloaded.assignments());
        assertTrue(reloaded.savedAt() != null, "save() should stamp savedAt");

        // Re-apply onto a fresh problem: server and pin restored by id.
        Assignment freshAssigned = new Assignment("svc-1:slot-0", MASS, ROLE_ACOLYTE);
        Assignment freshEmpty = new Assignment("svc-1:slot-1", MASS, ROLE_ACOLYTE);
        ServicePlan freshProblem = new ServicePlan(List.of(ANNA), List.of(freshAssigned, freshEmpty));
        PlanMapper.applyAcceptedPlan(freshProblem, reloaded);

        assertEquals(ANNA, freshAssigned.getServer());
        assertTrue(freshAssigned.isPinned());
        assertNull(freshEmpty.getServer());
    }
}
