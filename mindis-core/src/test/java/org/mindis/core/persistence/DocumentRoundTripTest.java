package org.mindis.core.persistence;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import org.mindis.core.model.ArchivedService;
import org.mindis.core.model.LiturgicalService;
import org.mindis.core.model.Role;
import org.mindis.core.model.RoleSlot;
import org.mindis.core.model.Server;
import org.mindis.core.model.ServiceTemplate;
import org.mindis.core.model.ServiceType;
import org.mindis.core.model.Slot;
import org.mindis.core.model.UnavailabilityPeriod;

/// All data lives in one user-chosen document, so a round trip means: mutate
/// the repositories, save, open the file in a second {@link AppDatabase}.
class DocumentRoundTripTest {

    @TempDir
    Path tempDir;

    @Test
    void everyEntityTypeSurvivesTheDocumentRoundTrip() throws IOException {
        Path file = tempDir.resolve("parish.json");
        Fixture original = new Fixture();
        Server server = new Server(
                Server.newId(), "Anna", "Muster", "anna@example.org",
                LocalDate.of(2012, 5, 14), "muster",
                Set.of(Role.ACOLYTE, Role.THURIFER),
                List.of(new UnavailabilityPeriod(LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 15))),
                Set.of(LocalTime.of(10, 0)), true, true);
        Role role = new Role(Role.newId(), "Thurifer", 14, 99, 50);
        ServiceTemplate template = new ServiceTemplate(ServiceTemplate.newId(), DayOfWeek.SUNDAY,
                LocalTime.of(10, 0), 60, "St. Mary", ServiceType.SUNDAY_MASS,
                List.of(new RoleSlot(Role.ACOLYTE, 2)));
        LiturgicalService service = new LiturgicalService(
                LiturgicalService.newId(), LocalDateTime.of(2026, 7, 12, 10, 0), 60, "St. Mary",
                ServiceType.SUNDAY_MASS,
                Slot.expand(List.of(new RoleSlot(Role.ACOLYTE, 2), new RoleSlot(Role.THURIFER, 1))),
                "First communion");
        ArchivedService archived = new ArchivedService(
                LiturgicalService.newId(), LocalDateTime.of(2026, 6, 7, 9, 0), 60, "St. Mary",
                ServiceType.SUNDAY_MASS, "",
                List.of(new ArchivedService.ArchivedSlot("Acolyte", "gone", "Deleted Server")),
                Instant.parse("2026-06-08T10:15:30Z"));

        original.servers.save(server);
        original.roles.save(role);
        original.templates.save(template);
        original.services.save(service);
        original.archived.addAll(List.of(archived));
        original.database.saveAs(file);

        Fixture reopened = new Fixture();
        reopened.database.open(file);

        assertEquals(List.of(server), reopened.servers.findAll());
        assertEquals(List.of(role), reopened.roles.findAll());
        assertEquals(List.of(template), reopened.templates.findAll());
        assertEquals(List.of(service), reopened.services.findAll());
        assertEquals(List.of(archived), reopened.archived.findAll());
        assertEquals(file, reopened.database.documentPath());
    }

    @Test
    void mutationsStageUntilTheDocumentIsSaved() throws IOException {
        Path file = tempDir.resolve("parish.json");
        Fixture fixture = new Fixture();
        Server saved = server("id-1", "Anna");
        fixture.servers.save(saved);
        fixture.database.saveAs(file);

        fixture.servers.save(server("id-2", "Ben"));
        fixture.servers.delete("id-1");

        Fixture onDisk = new Fixture();
        onDisk.database.open(file);
        assertEquals(List.of(saved), onDisk.servers.findAll(), "staged edits must not reach the file on their own");

        fixture.database.reload();

        assertEquals(List.of(saved), fixture.servers.findAll(), "reload must discard staged edits");
    }

    @Test
    void saveWritesBackToTheDocumentsOwnFile() throws IOException {
        Path file = tempDir.resolve("parish.json");
        Fixture fixture = new Fixture();
        fixture.database.saveAs(file);
        fixture.servers.save(server("id-1", "Anna"));

        fixture.database.save();

        Fixture onDisk = new Fixture();
        onDisk.database.open(file);
        assertEquals(1, onDisk.servers.findAll().size());
    }

    @Test
    void untitledDocumentCannotBeSavedWithoutALocation() {
        Fixture fixture = new Fixture();
        fixture.database.newDocument();

        assertThrows(IllegalStateException.class, fixture.database::save);
    }

    @Test
    void newDocumentIsUntitledAndSeedsTheDefaultRoles() {
        Fixture fixture = new Fixture();

        fixture.database.newDocument();

        assertEquals(null, fixture.database.documentPath());
        assertFalse(fixture.roles.findAll().isEmpty(), "expected seeded default roles");
        assertTrue(fixture.roles.findAll().stream().anyMatch(role -> Role.ACOLYTE.equals(role.id())));
    }

    @Test
    void emptiedRoleRosterStaysEmpty() throws IOException {
        Path file = tempDir.resolve("parish.json");
        Fixture fixture = new Fixture();
        fixture.database.newDocument();
        fixture.roles.findAll().forEach(role -> fixture.roles.delete(role.id()));
        fixture.database.saveAs(file);

        fixture.database.reload();

        assertTrue(fixture.roles.findAll().isEmpty(), "a saved-empty roster must stay empty, not reseed defaults");
    }

    @Test
    void archiveIsDirtyUntilTheDocumentIsSaved() throws IOException {
        Path file = tempDir.resolve("parish.json");
        Fixture fixture = new Fixture();
        fixture.database.saveAs(file);
        assertFalse(fixture.archived.isDirty());

        fixture.archived.addAll(List.of(new ArchivedService("id", LocalDateTime.of(2026, 6, 7, 9, 0), 60,
                "St. Mary", ServiceType.SUNDAY_MASS, "", List.of(), Instant.EPOCH)));
        assertTrue(fixture.archived.isDirty(), "archiving stages a change like any other edit");

        fixture.database.save();

        assertFalse(fixture.archived.isDirty(), "saving the document commits the archive too");
    }

    @Test
    void openingAnUnreadableFileFailsInsteadOfEmptyingTheDocument() throws IOException {
        Path file = tempDir.resolve("broken.json");
        Files.writeString(file, "{ this is not a MinDis document");
        Fixture fixture = new Fixture();
        fixture.database.newDocument();

        assertThrows(IOException.class, () -> fixture.database.open(file));

        assertFalse(fixture.roles.findAll().isEmpty(), "a failed open must leave the current document alone");
    }

    private static Server server(String id, String firstName) {
        return new Server(id, firstName, "Muster", "", null, null, Set.of(), List.of(), Set.of(), false, true);
    }

    /// One document's repositories plus the {@link AppDatabase} over them -
    /// what the DI container wires at runtime.
    private static final class Fixture {
        private final RoleRepository roles = new RoleRepository();
        private final ServerRepository servers = new ServerRepository();
        private final TemplateRepository templates = new TemplateRepository();
        private final ServiceRepository services = new ServiceRepository();
        private final ArchivedServiceRepository archived = new ArchivedServiceRepository();
        private final AppDatabase database = new AppDatabase(roles, servers, templates, services, archived);
    }
}
