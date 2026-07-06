package org.mindis.core.model;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * An altar server (ministrant). Plain record - no JavaFX types, no Timefold
 * annotations (those live on the planning entities, PLAN.md section 3).
 *
 * @param familyId shared marker linking siblings; {@code null} if none
 * @param birthDate {@code null} if unknown
 * @param preferredTimes service start times this server prefers (soft reward)
 * @param experienced experienced servers are spread across services (soft reward)
 */
public record Server(
        String id,
        String firstName,
        String lastName,
        String contact,
        LocalDate birthDate,
        String familyId,
        Set<Role> qualifications,
        List<UnavailabilityPeriod> unavailabilities,
        Set<LocalTime> preferredTimes,
        boolean experienced,
        boolean active) {

    public Server {
        // Null-tolerant: fields added after v0.6 are absent in older JSON.
        qualifications = qualifications == null ? Set.of() : Set.copyOf(qualifications);
        unavailabilities = unavailabilities == null ? List.of() : List.copyOf(unavailabilities);
        preferredTimes = preferredTimes == null ? Set.of() : Set.copyOf(preferredTimes);
    }

    public static String newId() {
        return UUID.randomUUID().toString();
    }

    public String displayName() {
        return (firstName + " " + lastName).strip();
    }

    public boolean isAvailableAt(LocalDateTime dateTime) {
        LocalDate date = dateTime.toLocalDate();
        return unavailabilities.stream().noneMatch(period -> period.contains(date));
    }

    public boolean prefers(LocalDateTime dateTime) {
        return preferredTimes.contains(dateTime.toLocalTime());
    }
}
