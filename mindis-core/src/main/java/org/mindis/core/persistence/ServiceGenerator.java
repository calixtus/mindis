package org.mindis.core.persistence;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.mindis.core.model.LiturgicalService;
import org.mindis.core.model.ServiceTemplate;
import org.mindis.core.model.Slot;

/// Expands recurring templates into concrete services for a date range.
/// Occurrences that collide with an existing service (same date-time and
/// location) are skipped, so re-generating a range is idempotent.
public final class ServiceGenerator {

    private ServiceGenerator() {
    }

    public static List<LiturgicalService> generate(
            List<ServiceTemplate> templates,
            List<LiturgicalService> existingServices,
            LocalDate from,
            LocalDate toInclusive) {
        Set<String> occupied = existingServices.stream()
                .map(service -> occurrenceKey(service.dateTime(), service.location()))
                .collect(Collectors.toSet());

        List<LiturgicalService> generated = new ArrayList<>();
        for (LocalDate date = from; !date.isAfter(toInclusive); date = date.plusDays(1)) {
            for (ServiceTemplate template : templates) {
                if (template.dayOfWeek() != date.getDayOfWeek()) {
                    continue;
                }
                LocalDateTime dateTime = date.atTime(template.time());
                if (!occupied.add(occurrenceKey(dateTime, template.location()))) {
                    continue;
                }
                // Fresh ids, not the template's own RoleSlot rows - a
                // template's slots are a shape, not persisted instances,
                // so there is nothing per-instance to carry over (see
                // Slot#expand).
                generated.add(new LiturgicalService(
                        LiturgicalService.newId(),
                        dateTime,
                        template.durationMinutes(),
                        template.location(),
                        template.type(),
                        Slot.expand(template.slots()),
                        ""));
            }
        }
        return generated;
    }

    private static String occurrenceKey(LocalDateTime dateTime, String location) {
        return dateTime + "|" + location;
    }
}
