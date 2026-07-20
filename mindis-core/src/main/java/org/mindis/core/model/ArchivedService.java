package org.mindis.core.model;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;

import org.jspecify.annotations.Nullable;

/// A frozen, self-contained snapshot of a {@link LiturgicalService} and its
/// assignments, taken when the planner archived it. Immutable by contract:
/// once written it is only ever read, never re-solved or edited.
///
/// <p>Deliberately carries no foreign keys into the live database - the role
/// and server display names are resolved and copied in at archive time (see
/// {@link ArchivedSlot}). Deleting or renaming a {@link Server} or {@link Role}
/// afterward therefore cannot alter what an archived service shows, so historic
/// plans stay faithful and exportable indefinitely regardless of later roster
/// changes (privacy/retention requirement).
public record ArchivedService(
        String id,
        LocalDateTime dateTime,
        int durationMinutes,
        String location,
        ServiceType type,
        String note,
        List<ArchivedSlot> slots,
        Instant archivedAt) {

    public ArchivedService {
        slots = List.copyOf(slots);
    }

    /// One frozen role slot: the role and (if it was filled) the server, both
    /// as the display names captured at archive time. {@code serverName} is
    /// {@code null} for a slot that was left open. {@code serverId} is kept
    /// only so cross-boundary spacing continuity can still match a server by
    /// identity ({@link org.mindis.core.planning.PriorAssignment}); it is never
    /// resolved against the live roster for display.
    public record ArchivedSlot(
            String roleName,
            @Nullable String serverId,
            @Nullable String serverName) {
    }
}
