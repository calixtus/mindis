package org.mindis.core.export;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/// Writes the plan as an iCalendar file (RFC 5545), one `VEVENT` per service, so a
/// parish schedule can be subscribed to in whatever calendar the servers already use.
///
/// Times are written as **floating** local time - no `Z`, no `TZID` - which is what a
/// wall-clock parish schedule means: a 10:00 mass is at 10:00 where the parish is, and
/// nothing about it should shift if someone opens the file in another time zone. The
/// alternative, a full `VTIMEZONE` block, would carry a claim about zone identity and
/// historical offsets that the document behind this export does not make.
@Singleton
final class IcsPlanExporter implements PlanCalendarExporter {

    /// Per RFC 5545 section 3.1: lines are folded at 75 **octets**, continuation lines
    /// beginning with one space.
    private static final int FOLD_OCTETS = 75;
    private static final String CRLF = "\r\n";

    private static final DateTimeFormatter LOCAL = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss");
    private static final DateTimeFormatter UTC = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'");

    /// Marks an unfilled slot, the same character the bundled template uses, so the
    /// calendar and the printed plan describe an open slot the same way.
    private static final String UNASSIGNED = "-";

    private final Supplier<Instant> now;

    @Inject
    IcsPlanExporter() {
        this(Instant::now);
    }

    /// @param now stamps every event's `DTSTAMP`; injectable so a test can pin it
    IcsPlanExporter(Supplier<Instant> now) {
        this.now = now;
    }

    @Override
    public PlanExportFormat format() {
        return PlanExportFormat.ICS;
    }

    @Override
    public void export(List<PlanTemplateModel.Service> services, Path targetFile) {
        List<String> lines = new ArrayList<>();
        lines.add("BEGIN:VCALENDAR");
        lines.add("VERSION:2.0");
        lines.add("PRODID:-//MinDis//Minister Dispatcher//EN");
        lines.add("CALSCALE:GREGORIAN");
        // Not a subscription feed that anyone republishes, so PUBLISH is the honest method.
        lines.add("METHOD:PUBLISH");
        String stamp = UTC.format(now.get().atOffset(ZoneOffset.UTC));
        for (PlanTemplateModel.Service service : services) {
            addEvent(lines, service, stamp);
        }
        lines.add("END:VCALENDAR");

        StringBuilder out = new StringBuilder();
        for (String line : lines) {
            out.append(fold(line)).append(CRLF);
        }
        try {
            Files.writeString(targetFile, out.toString(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("Could not write iCalendar: " + targetFile, e);
        }
    }

    private static void addEvent(List<String> lines, PlanTemplateModel.Service service, String stamp) {
        LocalDateTime start = service.dateTime();
        lines.add("BEGIN:VEVENT");
        // The service's own id, so re-importing an updated plan replaces the events
        // instead of adding a second copy of every one of them.
        lines.add("UID:" + service.id() + "@mindis");
        lines.add("DTSTAMP:" + stamp);
        lines.add("DTSTART:" + LOCAL.format(start));
        lines.add("DTEND:" + LOCAL.format(start.plusMinutes(service.durationMinutes())));
        lines.add("SUMMARY:" + escape(service.label()));
        if (!service.location().isBlank()) {
            lines.add("LOCATION:" + escape(service.location()));
        }
        String description = describe(service);
        if (!description.isEmpty()) {
            lines.add("DESCRIPTION:" + escape(description));
        }
        lines.add("END:VEVENT");
    }

    /// Who is serving, one `role: server` per line, with the service's note above them
    /// when it has one. This is the only place the assignments appear - a calendar entry
    /// with no roster in it would be a reminder, not a plan.
    private static String describe(PlanTemplateModel.Service service) {
        List<String> parts = new ArrayList<>();
        if (!service.note().isBlank()) {
            parts.add(service.note());
        }
        for (PlanTemplateModel.Slot slot : service.slots()) {
            String serverName = slot.serverName();
            parts.add(slot.roleName() + ": " + (serverName == null ? UNASSIGNED : serverName));
        }
        return String.join("\n", parts);
    }

    /// RFC 5545 section 3.3.11: in a TEXT value a backslash, semicolon, comma and
    /// newline are escaped. The backslash goes first, or it would escape the escapes
    /// added after it.
    private static String escape(String value) {
        return value.replace("\\", "\\\\")
                    .replace(";", "\\;")
                    .replace(",", "\\,")
                    .replace("\r\n", "\\n")
                    .replace("\n", "\\n")
                    .replace("\r", "\\n");
    }

    /// Folds one content line to [#FOLD_OCTETS] octets per line.
    ///
    /// Counted in octets rather than characters, and split only between whole UTF-8
    /// sequences: a parish roster is full of umlauts, and splitting one mid-sequence
    /// would produce a file no calendar can read.
    private static String fold(String line) {
        byte[] bytes = line.getBytes(StandardCharsets.UTF_8);
        if (bytes.length <= FOLD_OCTETS) {
            return line;
        }
        StringBuilder folded = new StringBuilder();
        int octets = 0;
        int limit = FOLD_OCTETS;
        for (int i = 0; i < line.length(); ) {
            int codePoint = line.codePointAt(i);
            int width = new String(Character.toChars(codePoint)).getBytes(StandardCharsets.UTF_8).length;
            if (octets + width > limit) {
                folded.append(CRLF).append(' ');
                octets = 1;
                // A continuation line's leading space counts toward its own 75.
                limit = FOLD_OCTETS;
            }
            folded.appendCodePoint(codePoint);
            octets += width;
            i += Character.charCount(codePoint);
        }
        return folded.toString();
    }
}
