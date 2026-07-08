package org.mindis.core.persistence;

import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.List;

import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import org.mindis.core.model.ServiceTemplate;
import org.mindis.core.model.ServiceType;

/**
 * CSV row mapping for {@link ServiceTemplate}, shared by every consumer
 * that offers Templates import/export (currently the GUI's Templates
 * module; PLAN.md's future web module gets the same for free).
 */
@NullMarked
public final class TemplateCsvMapper {

    private static final int DEFAULT_DURATION_MINUTES = 60;

    private final RoleRepository roleRepository;

    public TemplateCsvMapper(RoleRepository roleRepository) {
        this.roleRepository = roleRepository;
    }

    public List<String> header() {
        return List.of("id", "dayOfWeek", "time", "durationMinutes", "location", "type", "slots");
    }

    public List<String> toRow(ServiceTemplate template) {
        return List.of(
                template.id(),
                template.dayOfWeek().name(),
                template.time().toString(),
                String.valueOf(template.durationMinutes()),
                template.location(),
                template.type().name(),
                RoleSlotCsv.format(template.slots(), roleRepository));
    }

    /** Rows with an unparsable weekday/time are skipped; a blank id gets a fresh one. */
    public @Nullable ServiceTemplate fromRow(List<String> row) {
        DayOfWeek day = CsvFields.parseDayOfWeek(CsvFields.at(row, 1));
        LocalTime time = CsvFields.parseTime(CsvFields.at(row, 2));
        if (day == null || time == null) {
            return null;
        }
        String id = CsvFields.at(row, 0);
        Integer duration = CsvFields.parseInt(CsvFields.at(row, 3));
        return new ServiceTemplate(
                id.isEmpty() ? ServiceTemplate.newId() : id,
                day,
                time,
                duration == null ? DEFAULT_DURATION_MINUTES : duration,
                CsvFields.at(row, 4),
                CsvFields.parseServiceType(CsvFields.at(row, 5), ServiceType.SUNDAY_MASS),
                RoleSlotCsv.parse(CsvFields.at(row, 6), roleRepository));
    }
}
