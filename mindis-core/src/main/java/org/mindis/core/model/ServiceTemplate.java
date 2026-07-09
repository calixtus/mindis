package org.mindis.core.model;

import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

/// Recurring weekly service ("every Sunday 10:00 at St. Mary"). Concrete
/// services are generated from templates for a date range
/// (see ServiceGenerator).
public record ServiceTemplate(
        String id,
        DayOfWeek dayOfWeek,
        LocalTime time,
        int durationMinutes,
        String location,
        ServiceType type,
        List<RoleSlot> slots) {

    public ServiceTemplate {
        slots = List.copyOf(slots);
    }

    public static String newId() {
        return UUID.randomUUID().toString();
    }
}
