package org.mindis.core.model;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/// A single liturgical service (mass, wedding, funeral, ...) that needs altar
/// servers assigned.
public record LiturgicalService(
        String id,
        LocalDateTime dateTime,
        int durationMinutes,
        String location,
        ServiceType type,
        List<Slot> slots,
        String note) {

    public LiturgicalService {
        slots = List.copyOf(slots);
    }

    public static String newId() {
        return UUID.randomUUID().toString();
    }

    /// This service with {@code slots} replaced - used to write solver results
    /// (assignments now live on the slots) back onto the record.
    public LiturgicalService withSlots(List<Slot> slots) {
        return new LiturgicalService(id, dateTime, durationMinutes, location, type, slots, note);
    }

    public int totalSlots() {
        return slots.size();
    }
}
