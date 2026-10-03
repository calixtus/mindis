package org.mindis.core.planning;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

import org.junit.jupiter.api.Test;

import org.mindis.core.model.ArchivedService;
import org.mindis.core.model.LiturgicalService;
import org.mindis.core.model.RoleId;
import org.mindis.core.model.ServerId;
import org.mindis.core.model.ServiceType;
import org.mindis.core.model.Slot;

class ServiceArchiverTest {

    private static LiturgicalService service(String id, LocalDate date, List<Slot> slots) {
        return new LiturgicalService(id, LocalDateTime.of(date, LocalTime.of(10, 0)), 60,
                "St. Mary", ServiceType.SUNDAY_MASS, "", slots, "");
    }

    @Test
    void freezesOnlyServicesOnOrBeforeCutoffAndResolvesNames() {
        LiturgicalService jul = service("jul", LocalDate.of(2026, 7, 5),
                List.of(new Slot("s1", new RoleId("ACOLYTE"), new ServerId("srv"), true), new Slot("s2", new RoleId("ACOLYTE"), null, false)));
        LiturgicalService aug = service("aug", LocalDate.of(2026, 8, 5), List.of());

        ServiceArchiver.Result result = ServiceArchiver.archive(
                List.of(jul, aug), LocalDate.of(2026, 7, 31), Instant.now(),
                roleId -> "Acolyte", serverId -> "Anna B.");

        assertEquals(List.of("jul"), result.removedServiceIds());
        assertEquals(1, result.archived().size());
        ArchivedService frozen = result.archived().getFirst();
        assertEquals("jul", frozen.id());
        assertEquals("Acolyte", frozen.slots().getFirst().roleName());
        assertEquals("Anna B.", frozen.slots().getFirst().serverName());
        assertEquals(new ServerId("srv"), frozen.slots().getFirst().serverId());
        assertNull(frozen.slots().get(1).serverName(), "Open slot must have no server name");
    }

    @Test
    void fallsBackToIdsWhenNamesUnresolvable() {
        LiturgicalService jul = service("jul", LocalDate.of(2026, 7, 5),
                List.of(new Slot("s1", new RoleId("ROLE_X"), new ServerId("srv"), false)));

        ServiceArchiver.Result result = ServiceArchiver.archive(
                List.of(jul), LocalDate.of(2026, 7, 31), Instant.now(),
                roleId -> null, serverId -> null);

        ArchivedService.ArchivedSlot slot = result.archived().getFirst().slots().getFirst();
        assertEquals("ROLE_X", slot.roleName());
        assertEquals("srv", slot.serverName());
    }

    @Test
    void emptyResultWhenCutoffFreezesNothing() {
        LiturgicalService aug = service("aug", LocalDate.of(2026, 8, 5), List.of());
        assertTrue(ServiceArchiver.archive(List.of(aug), LocalDate.of(2026, 7, 31), Instant.now(),
                RoleId::value, ServerId::value).isEmpty());
    }
}
