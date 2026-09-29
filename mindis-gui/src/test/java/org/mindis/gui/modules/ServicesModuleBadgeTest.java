package org.mindis.gui.modules;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.Test;

import org.mindis.core.model.LiturgicalService;
import org.mindis.core.model.ServiceType;
import org.mindis.core.model.Slot;

/// The number the Services sidebar entry carries: work still waiting, not a
/// tally of everything ever left open.
class ServicesModuleBadgeTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 6, 1, 12, 0);

    private static LiturgicalService service(LocalDateTime when, String... serverIds) {
        List<Slot> slots = java.util.Arrays.stream(serverIds)
                .map(serverId -> new Slot("slot-" + serverId, "Acolyte",
                        "-".equals(serverId) ? null : serverId, false))
                .toList();
        return new LiturgicalService("s-" + when, when, 60, "St. Mary",
                ServiceType.SUNDAY_MASS, "", slots, "");
    }

    @Test
    void countsUnfilledSlotsOfServicesStillToCome() {
        assertEquals(3, ServicesModule.countOpenSlotsAhead(List.of(
                service(NOW.plusDays(1), "-", "server-1", "-"),
                service(NOW.plusDays(9), "-", "server-2")), NOW));
    }

    @Test
    void ignoresServicesThatHaveAlreadyHappened() {
        assertEquals(0, ServicesModule.countOpenSlotsAhead(List.of(
                service(NOW.minusDays(1), "-", "-", "-")), NOW));
    }

    @Test
    void isZeroWhenEverythingAheadIsAssigned() {
        assertEquals(0, ServicesModule.countOpenSlotsAhead(List.of(
                service(NOW.plusDays(2), "server-1", "server-2")), NOW));
    }

    @Test
    void isZeroWithNoServicesAtAll() {
        assertEquals(0, ServicesModule.countOpenSlotsAhead(List.of(), NOW));
    }
}
