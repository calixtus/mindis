package org.mindis.core.model;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/// A single liturgical service (mass, wedding, funeral, ...) that needs altar
/// servers assigned.
///
/// @param name the planner's own name for this service ("Familiengottesdienst"),
///        shown instead of the localized [ServiceType] label wherever the
///        service is labelled; blank means "just show the type"
/// @param note free-form remark, shown under the service in the plan export
public record LiturgicalService(
        String id,
        LocalDateTime dateTime,
        int durationMinutes,
        String location,
        ServiceType type,
        String name,
        List<Slot> slots,
        String note) {

    public LiturgicalService {
        // Null-tolerant like the roster records: `name` postdates the first
        // releases, so JSON written before it exists deserializes it as null.
        name = name == null ? "" : name;
        slots = List.copyOf(slots);
    }

    public static String newId() {
        return UUID.randomUUID().toString();
    }

    /// This service with `slots` replaced - used to write solver results
    /// (assignments now live on the slots) back onto the record.
    public LiturgicalService withSlots(List<Slot> slots) {
        return new LiturgicalService(id, dateTime, durationMinutes, location, type, name, slots, note);
    }

    public int totalSlots() {
        return slots.size();
    }
}
