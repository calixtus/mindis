package org.mindis.core.planning;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;

import org.mindis.core.model.LiturgicalService;
import org.mindis.core.model.Role;
import org.mindis.core.model.Server;
import org.mindis.core.model.ServiceType;

class AutofillTest {

    private static final Role ACOLYTE = new Role("ACOLYTE", "Acolyte", null, null, 0);

    private static LiturgicalService service(String id, LocalDate date) {
        return new LiturgicalService(id, LocalDateTime.of(date, LocalTime.of(10, 0)), 60,
                "St. Mary", ServiceType.SUNDAY_MASS, List.of(), "");
    }

    private static Assignment assignment(LiturgicalService service, String slotId) {
        return new Assignment(new AssignmentKey(service.id(), slotId).toId(), service, ACOLYTE);
    }

    private static Server server() {
        return new Server("srv", "Anna", "B", "", null, null, Set.of(), List.of(), Set.of(), false, true);
    }

    @Test
    void windowLeavesOnlyOpenInWindowSlotsFree() {
        LiturgicalService inWindow = service("in", LocalDate.of(2026, 8, 5));
        LiturgicalService outside = service("out", LocalDate.of(2026, 9, 5));
        Assignment openInWindow = assignment(inWindow, "a");
        Assignment filledInWindow = assignment(inWindow, "b");
        filledInWindow.setServer(server());
        Assignment openOutside = assignment(outside, "c");
        ServicePlan plan = new ServicePlan(List.of(server()),
                List.of(openInWindow, filledInWindow, openOutside));

        Autofill.Scope scope = Autofill.begin(plan,
                Autofill.within(LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 31), false));

        assertEquals(Set.of(openInWindow.getId()), scope.eligibleIds());
        assertFalse(openInWindow.isPinned(), "Eligible slot must be free");
        assertTrue(filledInWindow.isPinned(), "Already-filled slot must be pinned when not overwriting");
        assertTrue(openOutside.isPinned(), "Out-of-window slot must be pinned");
    }

    @Test
    void overwriteAlsoFreesFilledSlots() {
        LiturgicalService svc = service("in", LocalDate.of(2026, 8, 5));
        Assignment filled = assignment(svc, "b");
        filled.setServer(server());
        ServicePlan plan = new ServicePlan(List.of(server()), List.of(filled));

        Autofill.begin(plan, Autofill.within(LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 31), true));

        assertFalse(filled.isPinned(), "Overwrite must free an already-filled slot");
    }

    @Test
    void finishPinsFilledEligibleAndRestoresTheRest() {
        LiturgicalService svc = service("in", LocalDate.of(2026, 8, 5));
        Assignment eligibleFilled = assignment(svc, "a");
        Assignment eligibleEmpty = assignment(svc, "b");
        Assignment pinnedOutside = assignment(service("out", LocalDate.of(2026, 9, 5)), "c");
        pinnedOutside.setServer(server());
        pinnedOutside.setPinned(true);
        ServicePlan plan = new ServicePlan(List.of(server()),
                List.of(eligibleFilled, eligibleEmpty, pinnedOutside));

        Autofill.Scope scope = Autofill.begin(plan,
                Autofill.within(LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 31), false));
        // Solver fills one eligible slot, leaves the other empty.
        eligibleFilled.setServer(server());
        Autofill.finish(plan, scope);

        assertTrue(eligibleFilled.isPinned(), "Filled eligible slot becomes a pin");
        assertFalse(eligibleEmpty.isPinned(), "Empty eligible slot stays unpinned");
        assertTrue(pinnedOutside.isPinned(), "Original pin restored");
    }
}
