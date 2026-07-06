package org.mindis.core.persistence;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mindis.core.model.LiturgicalService;
import org.mindis.core.model.Role;
import org.mindis.core.model.RoleSlot;
import org.mindis.core.model.Server;
import org.mindis.core.model.ServiceTemplate;
import org.mindis.core.model.ServiceType;
import org.mindis.core.model.UnavailabilityPeriod;

class RepositoryRoundTripTest {

    @TempDir
    Path tempDir;

    @Test
    void serverSurvivesRoundTrip() {
        Server server = new Server(
                Server.newId(), "Anna", "Muster", "anna@example.org",
                LocalDate.of(2012, 5, 14), "muster",
                Set.of(Role.ACOLYTE, Role.THURIFER),
                List.of(new UnavailabilityPeriod(LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 15))),
                true);
        Path file = tempDir.resolve("servers.json");

        new ServerRepository(file).save(server);
        List<Server> reloaded = new ServerRepository(file).findAll();

        assertEquals(List.of(server), reloaded);
    }

    @Test
    void serviceSurvivesRoundTrip() {
        LiturgicalService service = new LiturgicalService(
                LiturgicalService.newId(),
                LocalDateTime.of(2026, 7, 12, 10, 0), 60, "St. Mary",
                ServiceType.SUNDAY_MASS,
                List.of(new RoleSlot(Role.ACOLYTE, 2), new RoleSlot(Role.THURIFER, 1)),
                "First communion");
        Path file = tempDir.resolve("services.json");

        new ServiceRepository(file).save(service);
        List<LiturgicalService> reloaded = new ServiceRepository(file).findAll();

        assertEquals(List.of(service), reloaded);
    }

    @Test
    void upsertReplacesById() {
        Path file = tempDir.resolve("servers.json");
        ServerRepository repository = new ServerRepository(file);
        Server original = new Server("id-1", "Anna", "Muster", "", null, null, Set.of(), List.of(), true);
        repository.save(original);

        repository.save(new Server("id-1", "Anna", "Beispiel", "", null, null, Set.of(), List.of(), true));

        assertEquals(1, repository.findAll().size());
        assertEquals("Beispiel", repository.findAll().getFirst().lastName());
    }

    @Test
    void generatorCreatesWeeklyOccurrencesAndSkipsExisting() {
        ServiceTemplate template = new ServiceTemplate(
                ServiceTemplate.newId(), DayOfWeek.SUNDAY, LocalTime.of(10, 0), 60,
                "St. Mary", ServiceType.SUNDAY_MASS, List.of(new RoleSlot(Role.ACOLYTE, 2)));
        LiturgicalService existing = new LiturgicalService(
                LiturgicalService.newId(), LocalDateTime.of(2026, 7, 12, 10, 0), 60,
                "St. Mary", ServiceType.SUNDAY_MASS, List.of(), "");

        // July 2026 has Sundays on 5, 12, 19, 26; the 12th already exists.
        List<LiturgicalService> generated = ServiceGenerator.generate(
                List.of(template), List.of(existing),
                LocalDate.of(2026, 7, 1), LocalDate.of(2026, 7, 31));

        assertEquals(3, generated.size());
        assertTrue(generated.stream().noneMatch(s -> s.dateTime().equals(existing.dateTime())));
    }
}
