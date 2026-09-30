package org.mindis.core.export;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import org.mindis.core.model.ServiceType;

/// The iCalendar export, checked against the parts of RFC 5545 that a calendar
/// application will actually reject a file over.
class IcsPlanExporterTest {

    private static final Instant STAMPED_AT = Instant.parse("2026-09-30T08:15:30Z");

    private static PlanTemplateModel.Service service(String id, LocalDateTime when, int minutes,
                                                     String name, String location, String note,
                                                     PlanTemplateModel.Slot... slots) {
        return new PlanTemplateModel.Service(id, when, minutes, ServiceType.SUNDAY_MASS,
                name, note, location, List.of(slots));
    }

    private static PlanTemplateModel.Slot slot(String role, String server) {
        return new PlanTemplateModel.Slot(role.toLowerCase(java.util.Locale.ROOT), role, server);
    }

    private static String export(Path directory, PlanTemplateModel.Service... services) throws IOException {
        Path file = directory.resolve("plan.ics");
        new IcsPlanExporter(() -> STAMPED_AT).export(List.of(services), file);
        return Files.readString(file, StandardCharsets.UTF_8);
    }

    @Test
    void theCalendarIsWellFormedAndIdentifiesItself(@TempDir Path directory) throws IOException {
        String ics = export(directory,
                service("s1", LocalDateTime.of(2026, 6, 14, 10, 0), 60, "", "St. Mary", ""));

        assertAll(
                () -> assertTrue(ics.startsWith("BEGIN:VCALENDAR\r\n")),
                () -> assertTrue(ics.endsWith("END:VCALENDAR\r\n")),
                () -> assertTrue(ics.contains("VERSION:2.0\r\n"), "RFC 5545 requires VERSION"),
                () -> assertTrue(ics.contains("PRODID:-//MinDis//Minister Dispatcher//EN\r\n"),
                        "and PRODID"),
                () -> assertTrue(ics.contains("BEGIN:VEVENT\r\n") && ics.contains("END:VEVENT\r\n")));
    }

    /// Every line ends CRLF, not LF - the one detail a text-file exporter written on
    /// Linux gets wrong, and strict parsers reject the result.
    @Test
    void everyLineEndsWithCrLf(@TempDir Path directory) throws IOException {
        String ics = export(directory,
                service("s1", LocalDateTime.of(2026, 6, 14, 10, 0), 60, "", "St. Mary", ""));

        assertEquals(0, ics.replace("\r\n", "").chars().filter(c -> c == '\n').count(),
                "no bare LF anywhere");
    }

    @Test
    void anEventCarriesItsStartEndAndIdentity(@TempDir Path directory) throws IOException {
        String ics = export(directory,
                service("service-42", LocalDateTime.of(2026, 6, 14, 10, 0), 75, "", "St. Mary", ""));

        assertAll(
                // Floating local time: no trailing Z, no TZID.
                () -> assertTrue(ics.contains("DTSTART:20260614T100000\r\n"), ics),
                () -> assertTrue(ics.contains("DTEND:20260614T111500\r\n"), "start + 75 minutes"),
                () -> assertTrue(ics.contains("UID:service-42@mindis\r\n"),
                        "a stable UID, so a re-import updates instead of duplicating"),
                () -> assertTrue(ics.contains("DTSTAMP:20260930T081530Z\r\n"), "DTSTAMP is UTC"));
    }

    @Test
    void theSummaryFallsBackToTheServiceType(@TempDir Path directory) throws IOException {
        String named = export(directory,
                service("s1", LocalDateTime.of(2026, 6, 14, 10, 0), 60, "Patronsfest", "", ""));
        String unnamed = export(directory,
                service("s2", LocalDateTime.of(2026, 6, 14, 10, 0), 60, "", "", ""));

        assertAll(
                () -> assertTrue(named.contains("SUMMARY:Patronsfest\r\n")),
                () -> assertTrue(unnamed.contains("SUMMARY:Sunday mass\r\n"), unnamed));
    }

    @Test
    void theDescriptionListsWhoIsServing(@TempDir Path directory) throws IOException {
        String ics = export(directory,
                service("s1", LocalDateTime.of(2026, 6, 14, 10, 0), 60, "", "", "Bring the thurible",
                        slot("Acolyte", "Anna"), slot("Cross bearer", null)));

        // Escaped newlines keep it one content line; the note leads, then the roster.
        assertTrue(ics.contains("DESCRIPTION:Bring the thurible\\nAcolyte: Anna\\nCross bearer: -\r\n"),
                ics);
    }

    @Test
    void anEmptyLocationAndNoteAreLeftOutRatherThanWrittenBlank(@TempDir Path directory) throws IOException {
        String ics = export(directory,
                service("s1", LocalDateTime.of(2026, 6, 14, 10, 0), 60, "", "  ", "  "));

        assertAll(
                () -> assertFalse(ics.contains("LOCATION:"), ics),
                () -> assertFalse(ics.contains("DESCRIPTION:"), ics));
    }

    /// RFC 5545 section 3.3.11: backslash, semicolon, comma and newline are escaped in
    /// a TEXT value - a location like "St. Mary, Upper Chapel; side altar" would
    /// otherwise read as extra properties. A colon is *not* escaped, which is why
    /// DTSTART and friends can use one as their own separator.
    @Test
    void textValuesAreEscaped(@TempDir Path directory) throws IOException {
        String ics = export(directory,
                service("s1", LocalDateTime.of(2026, 6, 14, 10, 0), 60,
                        "Mass, sung; with C:\\path", "St. Mary, Upper Chapel; side altar", ""));

        assertAll(
                () -> assertTrue(ics.contains("SUMMARY:Mass\\, sung\\; with C:\\\\path" + "\r\n"), ics),
                () -> assertTrue(ics.contains(
                        "LOCATION:St. Mary\\, Upper Chapel\\; side altar" + "\r\n"), ics));
    }

    /// RFC 5545 section 3.1: no content line exceeds 75 octets, and a continuation
    /// begins with one space.
    @Test
    void longLinesAreFoldedAt75Octets(@TempDir Path directory) throws IOException {
        String longName = "Solemn Pontifical High Mass for the Patronal Feast of the Parish Church";
        String ics = export(directory,
                service("s1", LocalDateTime.of(2026, 6, 14, 10, 0), 60, longName, "", ""));

        List<String> lines = List.of(ics.split("\r\n", -1));
        assertAll(
                () -> lines.forEach(line -> assertTrue(
                        line.getBytes(StandardCharsets.UTF_8).length <= 75,
                        "line over 75 octets: " + line)),
                () -> assertTrue(lines.stream().anyMatch(line -> line.startsWith(" ")),
                        "something must actually have been folded"));
        // Unfolding (drop CRLF + one space) has to give the name back intact.
        assertTrue(ics.replace("\r\n ", "").contains("SUMMARY:" + longName), ics);
    }

    /// Folding counts octets, so a name full of umlauts folds earlier than its length
    /// suggests - and must never be split inside a multi-byte sequence.
    @Test
    void foldingNeverSplitsAMultiByteCharacter(@TempDir Path directory) throws IOException {
        String umlauts = "Ökumenischer Gottesdienst für Sankt Bartholomäus und Sankt Jürgen zu Köln";
        String ics = export(directory,
                service("s1", LocalDateTime.of(2026, 6, 14, 10, 0), 60, umlauts, "", ""));

        assertAll(
                () -> List.of(ics.split("\r\n", -1)).forEach(line -> assertTrue(
                        line.getBytes(StandardCharsets.UTF_8).length <= 75, "line over 75 octets")),
                () -> assertFalse(ics.contains("\uFFFD"), "no replacement character: nothing was split"),
                () -> assertTrue(ics.replace("\r\n ", "").contains("SUMMARY:" + umlauts), ics));
    }

    @Test
    void everyServiceBecomesItsOwnEvent(@TempDir Path directory) throws IOException {
        String ics = export(directory,
                service("s1", LocalDateTime.of(2026, 6, 14, 10, 0), 60, "", "", ""),
                service("s2", LocalDateTime.of(2026, 6, 21, 10, 0), 60, "", "", ""),
                service("s3", LocalDateTime.of(2026, 6, 28, 18, 30), 45, "", "", ""));

        assertEquals(3, ics.split("BEGIN:VEVENT", -1).length - 1);
    }

    @Test
    void anEmptyPlanIsStillAValidCalendar(@TempDir Path directory) throws IOException {
        String ics = export(directory);

        assertAll(
                () -> assertTrue(ics.startsWith("BEGIN:VCALENDAR\r\n")),
                () -> assertTrue(ics.endsWith("END:VCALENDAR\r\n")),
                () -> assertFalse(ics.contains("BEGIN:VEVENT")));
    }
}
