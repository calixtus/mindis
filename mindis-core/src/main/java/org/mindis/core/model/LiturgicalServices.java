package org.mindis.core.model;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import org.jspecify.annotations.Nullable;

/// Queries over a list of [LiturgicalService]s that more than one front end
/// asks - kept with the model rather than in whichever screen asked first.
public final class LiturgicalServices {

    private LiturgicalServices() {
    }

    /// Slots with nobody in them, over the services that have not happened yet.
    ///
    /// Past services are excluded deliberately: a slot nobody filled last month is
    /// a record of what happened, not work still waiting, and counting it would
    /// leave a count that can never be brought to zero.
    public static int openSlotsAhead(List<LiturgicalService> services, LocalDateTime now) {
        return (int) services.stream()
                .filter(service -> service.dateTime().isAfter(now))
                .flatMap(service -> service.slots().stream())
                .filter(slot -> slot.serverId() == null)
                .count();
    }

    /// The services dated within `[from, to]`, either bound `null` meaning
    /// unbounded on that side.
    public static List<LiturgicalService> inDateRange(List<LiturgicalService> services,
                                                      @Nullable LocalDate from, @Nullable LocalDate to) {
        return services.stream()
                .filter(service -> from == null || !service.dateTime().toLocalDate().isBefore(from))
                .filter(service -> to == null || !service.dateTime().toLocalDate().isAfter(to))
                .toList();
    }
}
