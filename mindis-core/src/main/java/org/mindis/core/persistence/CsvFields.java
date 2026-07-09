package org.mindis.core.persistence;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Locale;

import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import org.mindis.core.model.ServiceType;

/// Field access and value parsing shared by every entity's CSV row mapper.
/// Unparsable or absent fields resolve to {@code null} (or a caller-supplied
/// fallback) rather than throwing - CSV import is best-effort per PLAN.md's
/// "free-form field" convention used throughout the editors.
@NullMarked
final class CsvFields {

    private CsvFields() {
    }

    /// The field at {@code index}, or {@code ""} if the row is shorter than expected.
    static String at(List<String> row, int index) {
        return index < row.size() ? row.get(index).strip() : "";
    }

    static @Nullable Integer parseInt(String text) {
        if (text.isEmpty()) {
            return null;
        }
        try {
            return Integer.parseInt(text);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    static @Nullable LocalDate parseDate(String text) {
        if (text.isEmpty()) {
            return null;
        }
        try {
            return LocalDate.parse(text);
        } catch (DateTimeParseException e) {
            return null;
        }
    }

    static @Nullable LocalTime parseTime(String text) {
        if (text.isEmpty()) {
            return null;
        }
        try {
            return LocalTime.parse(text);
        } catch (DateTimeParseException e) {
            return null;
        }
    }

    static @Nullable DayOfWeek parseDayOfWeek(String text) {
        try {
            return DayOfWeek.valueOf(text.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    static ServiceType parseServiceType(String text, ServiceType fallback) {
        try {
            return ServiceType.valueOf(text.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return fallback;
        }
    }
}
