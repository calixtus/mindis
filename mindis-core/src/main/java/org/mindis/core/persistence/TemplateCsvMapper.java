package org.mindis.core.persistence;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import org.mindis.core.model.RecurrenceRule;
import org.mindis.core.model.ServiceSchedule;
import org.mindis.core.model.ServiceTemplate;
import org.mindis.core.model.ServiceType;

/// CSV row mapping for [ServiceTemplate], shared by every consumer
/// that offers Templates import/export (currently the GUI's Templates
/// module; PLAN.md's future web module gets the same for free).
@NullMarked
public final class TemplateCsvMapper {

    private static final int DEFAULT_DURATION_MINUTES = 60;

    private final RoleRepository roleRepository;

    public TemplateCsvMapper(RoleRepository roleRepository) {
        this.roleRepository = roleRepository;
    }

    public List<String> header() {
        return List.of("id", "recurrence", "time", "durationMinutes", "location", "type", "slots",
                "validFrom", "validUntil", "skipDates");
    }

    public List<String> toRow(ServiceTemplate template) {
        ServiceSchedule schedule = template.schedule();
        return List.of(
                template.id(),
                RecurrenceCodec.format(schedule.rule()),
                template.time().toString(),
                String.valueOf(template.durationMinutes()),
                template.location(),
                template.type().name(),
                RoleSlotCsv.format(template.slots(), roleRepository),
                date(schedule.validFrom()),
                date(schedule.validUntil()),
                schedule.skipDates().stream().map(LocalDate::toString).collect(Collectors.joining(", ")));
    }

    /// Rows with an unparsable recurrence/time are skipped; a blank id gets a
    /// fresh one. The three schedule columns are optional - a row that stops
    /// after `slots` is a template without a window or cancellations,
    /// and an unreadable date in them is dropped rather than failing the row,
    /// mirroring the per-field tolerance of every other importer.
    public @Nullable ServiceTemplate fromRow(List<String> row) {
        RecurrenceRule recurrence = RecurrenceCodec.parse(CsvFields.at(row, 1));
        LocalTime time = CsvFields.parseTime(CsvFields.at(row, 2));
        if (recurrence == null || time == null) {
            return null;
        }
        String id = CsvFields.at(row, 0);
        Integer duration = CsvFields.parseInt(CsvFields.at(row, 3));
        ServiceSchedule schedule = new ServiceSchedule(recurrence,
                CsvFields.parseDate(CsvFields.at(row, 7)),
                CsvFields.parseDate(CsvFields.at(row, 8)),
                skipDates(CsvFields.at(row, 9)));
        return new ServiceTemplate(
                id.isEmpty() ? ServiceTemplate.newId() : id,
                schedule,
                time,
                duration == null ? DEFAULT_DURATION_MINUTES : duration,
                CsvFields.at(row, 4),
                CsvFields.parseServiceType(CsvFields.at(row, 5), ServiceType.SUNDAY_MASS),
                RoleSlotCsv.parse(CsvFields.at(row, 6), roleRepository));
    }

    private static String date(@Nullable LocalDate date) {
        return date == null ? "" : date.toString();
    }

    private static Set<LocalDate> skipDates(String field) {
        Set<LocalDate> dates = new LinkedHashSet<>();
        for (String part : field.split(",")) {
            LocalDate date = CsvFields.parseDate(part.strip());
            if (date != null) {
                dates.add(date);
            }
        }
        return dates;
    }
}
