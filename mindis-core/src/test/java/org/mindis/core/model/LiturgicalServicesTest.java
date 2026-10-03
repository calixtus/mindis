package org.mindis.core.model;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.Test;

class LiturgicalServicesTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 6, 1, 12, 0);

    /// `"-"` marks an unfilled slot.
    private static LiturgicalService service(LocalDateTime when, String... serverIds) {
        List<Slot> slots = Arrays.stream(serverIds)
                .map(serverId -> new Slot("slot-" + serverId, "Acolyte",
                        "-".equals(serverId) ? null : serverId, false))
                .toList();
        return new LiturgicalService("s-" + when, when, 60, "St. Mary",
                ServiceType.SUNDAY_MASS, "", slots, "");
    }

    @Test
    void openSlotsAhead_countsUnfilledSlotsOfServicesStillToCome() {
        assertEquals(3, LiturgicalServices.openSlotsAhead(List.of(
                service(NOW.plusDays(1), "-", "server-1", "-"),
                service(NOW.plusDays(9), "-", "server-2")), NOW));
    }

    @Test
    void openSlotsAhead_ignoresServicesThatHaveAlreadyHappened() {
        assertEquals(0, LiturgicalServices.openSlotsAhead(List.of(
                service(NOW.minusDays(1), "-", "-", "-")), NOW));
    }

    @Test
    void openSlotsAhead_isZeroWhenEverythingAheadIsAssigned() {
        assertEquals(0, LiturgicalServices.openSlotsAhead(List.of(
                service(NOW.plusDays(2), "server-1", "server-2")), NOW));
    }

    @Test
    void openSlotsAhead_isZeroWithNoServicesAtAll() {
        assertEquals(0, LiturgicalServices.openSlotsAhead(List.of(), NOW));
    }

    @Test
    void inDateRange_includesBothBoundaryDays() {
        LiturgicalService first = service(NOW);
        LiturgicalService last = service(NOW.plusDays(2));
        List<LiturgicalService> services = List.of(service(NOW.minusDays(1)), first, last, service(NOW.plusDays(3)));

        assertEquals(List.of(first, last), LiturgicalServices.inDateRange(services,
                NOW.toLocalDate(), NOW.toLocalDate().plusDays(2)));
    }

    @Test
    void inDateRange_nullBoundIsUnbounded() {
        List<LiturgicalService> services = List.of(service(NOW.minusYears(5)), service(NOW.plusYears(5)));
        LocalDate today = NOW.toLocalDate();

        assertEquals(services, LiturgicalServices.inDateRange(services, null, null));
        assertEquals(List.of(services.get(1)), LiturgicalServices.inDateRange(services, today, null));
        assertEquals(List.of(services.getFirst()), LiturgicalServices.inDateRange(services, null, today));
    }
}
