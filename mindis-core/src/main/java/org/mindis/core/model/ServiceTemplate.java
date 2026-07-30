package org.mindis.core.model;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

/// A recurring service ("every Sunday 10:00 at St. Mary", "every third Sunday",
/// "every 13th of the month"). When its services happen is the
/// [ServiceSchedule]; concrete services are generated from templates for
/// a date range (see ServiceGenerator).
public record ServiceTemplate(
        String id,
        ServiceSchedule schedule,
        LocalTime time,
        int durationMinutes,
        String location,
        ServiceType type,
        List<RoleSlot> slots) {

    public ServiceTemplate {
        // Null-tolerant like the other model records: a hand-edited document
        // without a schedule reads as "generates nothing" rather than failing
        // the whole open.
        if (schedule == null) {
            schedule = ServiceSchedule.of(RecurrenceRule.NEVER);
        }
        slots = List.copyOf(slots);
    }

    /// The common weekly case, kept as a factory so callers need not spell out
    /// the rule and schedule wrappers.
    public static ServiceTemplate weekly(String id, DayOfWeek dayOfWeek, LocalTime time, int durationMinutes,
                                         String location, ServiceType type, List<RoleSlot> slots) {
        return new ServiceTemplate(id, ServiceSchedule.of(RecurrenceRule.weekly(dayOfWeek)), time, durationMinutes,
                location, type, slots);
    }

    public static String newId() {
        return UUID.randomUUID().toString();
    }

    /// Whether this template produces a service on `date`.
    public boolean occursOn(LocalDate date) {
        return schedule.occursOn(date);
    }

    /// This template with `schedule` replaced - the editor rebuilds one
    /// part of the schedule at a time.
    public ServiceTemplate withSchedule(ServiceSchedule schedule) {
        return new ServiceTemplate(id, schedule, time, durationMinutes, location, type, slots);
    }
}
