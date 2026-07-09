package org.mindis.core.persistence;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import org.mindis.core.model.LiturgicalService;
import org.mindis.core.model.ServiceType;

/// CSV row mapping for {@link LiturgicalService}, shared by every consumer
/// that offers Services import/export (currently the GUI's Services module;
/// PLAN.md's future web module gets the same for free).
@NullMarked
public final class ServiceCsvMapper {

    private static final int DEFAULT_DURATION_MINUTES = 60;

    private final RoleRepository roleRepository;

    public ServiceCsvMapper(RoleRepository roleRepository) {
        this.roleRepository = roleRepository;
    }

    public List<String> header() {
        return List.of("id", "date", "time", "durationMinutes", "location", "type", "slots", "note");
    }

    public List<String> toRow(LiturgicalService service) {
        return List.of(
                service.id(),
                service.dateTime().toLocalDate().toString(),
                service.dateTime().toLocalTime().toString(),
                String.valueOf(service.durationMinutes()),
                service.location(),
                service.type().name(),
                RoleSlotCsv.format(service.slots(), roleRepository),
                service.note());
    }

    /// Rows with an unparsable date/time are skipped; a blank id gets a fresh one.
    public @Nullable LiturgicalService fromRow(List<String> row) {
        LocalDate date = CsvFields.parseDate(CsvFields.at(row, 1));
        LocalTime time = CsvFields.parseTime(CsvFields.at(row, 2));
        if (date == null || time == null) {
            return null;
        }
        String id = CsvFields.at(row, 0);
        Integer duration = CsvFields.parseInt(CsvFields.at(row, 3));
        return new LiturgicalService(
                id.isEmpty() ? LiturgicalService.newId() : id,
                date.atTime(time),
                duration == null ? DEFAULT_DURATION_MINUTES : duration,
                CsvFields.at(row, 4),
                CsvFields.parseServiceType(CsvFields.at(row, 5), ServiceType.OTHER),
                RoleSlotCsv.parse(CsvFields.at(row, 6), roleRepository),
                CsvFields.at(row, 7));
    }
}
