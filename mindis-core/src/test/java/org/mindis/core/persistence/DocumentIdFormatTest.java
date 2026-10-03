package org.mindis.core.persistence;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import org.mindis.core.model.ArchivedService;
import org.mindis.core.model.CollectionMeta;
import org.mindis.core.model.LiturgicalService;
import org.mindis.core.model.Role;
import org.mindis.core.model.RoleId;
import org.mindis.core.model.RoleSlot;
import org.mindis.core.model.Server;
import org.mindis.core.model.ServerId;
import org.mindis.core.model.ServiceTemplate;
import org.mindis.core.model.ServiceType;
import org.mindis.core.model.Slot;

/// Role and server ids are typed in memory but stay bare strings on disk, so
/// documents written before they had types still open and new ones look the
/// same to an older version.
class DocumentIdFormatTest {

    private static final RoleId ACOLYTE = Role.ACOLYTE;
    private static final ServerId ANNA = new ServerId("srv-anna");

    @TempDir
    Path tempDir;

    private final DocumentStore store = new DocumentStore();

    private static MinDisDocument document() {
        return new MinDisDocument(MinDisDocument.CURRENT_VERSION, CollectionMeta.empty(),
                List.of(new Role(ACOLYTE, "Acolyte", null, null, 0)),
                List.of(Server.named(ANNA, "Anna", "Becker")
                        .withQualifications(Set.of(ACOLYTE))
                        .withIncompatibleRoles(Set.of(Role.THURIFER))),
                List.of(ServiceTemplate.weekly("tpl", DayOfWeek.SUNDAY, LocalTime.of(10, 0), 60, "St. Mary",
                        ServiceType.SUNDAY_MASS, List.of(new RoleSlot(ACOLYTE, 2)))),
                List.of(new LiturgicalService("svc", LocalDateTime.of(2026, 8, 2, 10, 0), 60, "St. Mary",
                        ServiceType.SUNDAY_MASS, "", List.of(new Slot("slot", ACOLYTE, ANNA, true)), "")),
                List.of(new ArchivedService("old", LocalDateTime.of(2026, 7, 5, 10, 0), 60, "St. Mary",
                        ServiceType.SUNDAY_MASS, "", "",
                        List.of(new ArchivedService.ArchivedSlot("Acolyte", ANNA, "Anna Becker")),
                        Instant.parse("2026-07-06T00:00:00Z"))));
    }

    @Test
    void idsAreWrittenAsBareStrings() throws IOException {
        Path file = tempDir.resolve("parish.mindis");
        store.write(file, document());

        JsonNode root = new ObjectMapper().readTree(file.toFile());

        assertAll(
                () -> assertEquals("ACOLYTE", root.at("/roles/0/id").asText()),
                () -> assertEquals("srv-anna", root.at("/servers/0/id").asText()),
                () -> assertEquals("ACOLYTE", root.at("/servers/0/qualifications/0").asText()),
                () -> assertEquals("THURIFER", root.at("/servers/0/incompatibleRoles/0").asText()),
                () -> assertEquals("ACOLYTE", root.at("/templates/0/slots/0/role").asText()),
                () -> assertEquals("ACOLYTE", root.at("/services/0/slots/0/role").asText()),
                () -> assertEquals("srv-anna", root.at("/services/0/slots/0/serverId").asText()),
                () -> assertEquals("srv-anna", root.at("/archivedServices/0/slots/0/serverId").asText()),
                () -> assertTrue(root.at("/services/0/slots/0/role").isTextual(), "role id must not be an object"));
    }

    @Test
    void aDocumentWrittenBeforeTheIdsHadTypesStillReads() throws IOException {
        Path file = tempDir.resolve("parish.mindis");
        Files.writeString(file, """
                {
                  "version": 2,
                  "roles": [{"id": "ACOLYTE", "name": "Acolyte", "sortOrder": 0}],
                  "servers": [{"id": "srv-anna", "firstName": "Anna", "lastName": "Becker", "contact": "",
                               "qualifications": ["ACOLYTE"], "incompatibleRoles": ["THURIFER"],
                               "experienced": false, "active": true}],
                  "services": [{"id": "svc", "dateTime": "2026-08-02T10:00:00", "durationMinutes": 60,
                                "location": "St. Mary", "type": "SUNDAY_MASS", "name": "", "note": "",
                                "slots": [{"id": "slot", "role": "ACOLYTE", "serverId": "srv-anna", "pinned": true}]}]
                }
                """);

        MinDisDocument read = store.read(file);

        Server anna = read.servers().getFirst();
        Slot slot = read.services().getFirst().slots().getFirst();
        assertAll(
                () -> assertEquals(ACOLYTE, read.roles().getFirst().id()),
                () -> assertEquals(ANNA, anna.id()),
                () -> assertEquals(Set.of(ACOLYTE), anna.qualifications()),
                () -> assertEquals(Set.of(Role.THURIFER), anna.incompatibleRoles()),
                () -> assertEquals(ACOLYTE, slot.role()),
                () -> assertEquals(ANNA, slot.serverId()));
    }

    @Test
    void typedIdsSurviveTheRoundTrip() throws IOException {
        Path file = tempDir.resolve("parish.mindis");
        store.write(file, document());

        assertEquals(document(), store.read(file));
    }
}
