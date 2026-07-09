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
        List<RoleSlot> slots,
        String note) {

    public LiturgicalService {
        slots = List.copyOf(slots);
    }

    public static String newId() {
        return UUID.randomUUID().toString();
    }

    public int totalSlots() {
        return slots.stream().mapToInt(RoleSlot::count).sum();
    }
}
