package org.mindis.core.model;

import java.time.LocalDate;

/**
 * A date range (inclusive on both ends) during which a server cannot be
 * assigned (vacation, exams, ...).
 */
public record UnavailabilityPeriod(LocalDate start, LocalDate end) {

    public UnavailabilityPeriod {
        if (end.isBefore(start)) {
            throw new IllegalArgumentException("end must not be before start");
        }
    }

    public boolean contains(LocalDate date) {
        return !date.isBefore(start) && !date.isAfter(end);
    }
}
