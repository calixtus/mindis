package org.mindis.core.model;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * An altar server (ministrant). Plain record - no JavaFX types, no Timefold
 * annotations (those live on the planning entities, PLAN.md section 3).
 *
 * @param familyId shared marker linking siblings; {@code null} if none
 * @param birthDate {@code null} if unknown
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
        boolean active) {

    public Server {
        qualifications = Set.copyOf(qualifications);
        unavailabilities = List.copyOf(unavailabilities);
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
}
