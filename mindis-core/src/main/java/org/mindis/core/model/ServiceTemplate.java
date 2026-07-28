package org.mindis.core.model;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

/// A recurring service ("every Sunday 10:00 at St. Mary", "every third Sunday",
/// "every 13th of the month"). The date pattern lives in a
/// {@link RecurrenceRule}; concrete services are generated from templates for a
/// date range (see ServiceGenerator).
public record ServiceTemplate(
        String id,
        RecurrenceRule recurrence,
        LocalTime time,
        int durationMinutes,
        String location,
        ServiceType type,
        List<RoleSlot> slots) {

    public ServiceTemplate {
        // Null-tolerant like the other model records: a hand-edited document
        // without a recurrence reads as "generates nothing" rather than
        // failing the whole open.
        if (recurrence == null) {
            recurrence = RecurrenceRule.NEVER;
        }
        slots = List.copyOf(slots);
    }

    /// The common weekly case, kept as a factory so callers need not spell out
    /// the rule wrapper.
    public static ServiceTemplate weekly(String id, DayOfWeek dayOfWeek, LocalTime time, int durationMinutes,
                                         String location, ServiceType type, List<RoleSlot> slots) {
        return new ServiceTemplate(id, RecurrenceRule.weekly(dayOfWeek), time, durationMinutes, location, type, slots);
    }

    public static String newId() {
        return UUID.randomUUID().toString();
    }

    /// Whether this template produces a service on {@code date}.
    public boolean occursOn(LocalDate date) {
        return recurrence.matches(date);
    }
}
