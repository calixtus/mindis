package org.mindis.core.planning;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

import org.jspecify.annotations.Nullable;

import org.mindis.core.model.ArchivedService;
import org.mindis.core.model.LiturgicalService;
import org.mindis.core.model.Slot;

/// Pure logic for freezing live services up to an archive cutoff into
/// self-contained {@link ArchivedService} snapshots. Dependency-free by design
/// (SOLID/DIP): callers supply the role/server name lookups and the current
/// time, so this stays a plain, testable transformation with no repository or
/// clock coupling.
public final class ServiceArchiver {

    private ServiceArchiver() {
    }

    /// What an archive run produces: the frozen snapshots to persist, and the
    /// ids of the live services they replace (to drop from the live list).
    public record Result(List<ArchivedService> archived, List<String> removedServiceIds) {

        public Result {
            archived = List.copyOf(archived);
            removedServiceIds = List.copyOf(removedServiceIds);
        }

        public boolean isEmpty() {
            return archived.isEmpty();
        }
    }

    /// Snapshots every service in {@code live} dated on or before {@code
    /// cutoff}, resolving each slot's role and (if filled) server to the
    /// display names {@code roleName}/{@code serverName} return - copied in
    /// now so the snapshot never needs the live roster again. A name lookup
    /// returning {@code null} (role/server already gone) falls back to the
    /// stored id, so archiving never loses a slot. Services after the cutoff
    /// are left untouched.
    public static Result archive(List<LiturgicalService> live, LocalDate cutoff, Instant archivedAt,
                                 Function<String, @Nullable String> roleName,
                                 Function<String, @Nullable String> serverName) {
        List<ArchivedService> archived = new ArrayList<>();
        List<String> removed = new ArrayList<>();
        for (LiturgicalService service : live) {
            if (service.dateTime().toLocalDate().isAfter(cutoff)) {
                continue;
            }
            List<ArchivedService.ArchivedSlot> slots = new ArrayList<>();
            for (Slot slot : service.slots()) {
                String role = orElse(roleName.apply(slot.role()), slot.role());
                String serverId = slot.serverId();
                String server = serverId == null ? null : orElse(serverName.apply(serverId), serverId);
                slots.add(new ArchivedService.ArchivedSlot(role, serverId, server));
            }
            archived.add(new ArchivedService(service.id(), service.dateTime(), service.durationMinutes(),
                    service.location(), service.type(), service.note(), slots, archivedAt));
            removed.add(service.id());
        }
        return new Result(archived, removed);
    }

    private static String orElse(@Nullable String value, String fallback) {
        return value == null ? fallback : value;
    }
}
