package org.mindis.core.model;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.Period;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.jspecify.annotations.Nullable;

/// An altar server (ministrant). Plain record - no JavaFX types, no Timefold
/// annotations (those live on the planning entities, PLAN.md section 3).
///
/// @param familyId shared marker linking siblings; `null` if none
/// @param birthDate `null` if unknown
/// @param preferredTimes service start times this server prefers (soft reward)
/// @param experienced experienced servers are spread across services (soft reward)
/// @param qualifications ids of the [Role]s this server may fill
/// @param incompatibleRoles ids of the [Role]s this server cannot serve
///        alongside: a service staffing one of them is barred for this server
///        entirely (e.g. incense intolerance rules out every service with a
///        thurifer, not just the thurifer slot)
public record Server(
        String id,
        String firstName,
        String lastName,
        String contact,
        @Nullable LocalDate birthDate,
        @Nullable String familyId,
        Set<String> qualifications,
        Set<String> incompatibleRoles,
        List<UnavailabilityPeriod> unavailabilities,
        Set<LocalTime> preferredTimes,
        boolean experienced,
        boolean active) {

    public Server {
        // Null-tolerant: fields added after v0.6 are absent in older JSON.
        qualifications = qualifications == null ? Set.of() : Set.copyOf(qualifications);
        incompatibleRoles = incompatibleRoles == null ? Set.of() : Set.copyOf(incompatibleRoles);
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

    /// True if any slot of `service` asks for a role this server cannot
    /// serve alongside - then the whole service is off limits, whichever slot
    /// the server would fill.
    public boolean isExcludedFrom(LiturgicalService service) {
        return service.slots().stream().anyMatch(slot -> incompatibleRoles.contains(slot.role()));
    }

    public boolean prefers(LocalDateTime dateTime) {
        return preferredTimes.contains(dateTime.toLocalTime());
    }

    /// @return the server's age in whole years on `date`, or `null`
    ///         if the birth date is unknown (age requirements are then not
    ///         enforced).
    public @Nullable Integer ageAt(LocalDate date) {
        return birthDate == null ? null : Period.between(birthDate, date).getYears();
    }
}
