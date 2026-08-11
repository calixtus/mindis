package org.mindis.gui.util;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.FormatStyle;
import java.time.format.TextStyle;
import java.util.Locale;

/// Date and time formatting for anything the user reads on screen.
///
/// One place, because four screens had grown their own
/// `DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm")` - a German pattern
/// hardcoded into an app that also ships English, so an English UI still
/// rendered German-style dates.
///
/// The formatter is derived from the current default locale on every call
/// rather than cached in a `static final`: `Localization.setLocale` moves that
/// locale at runtime, and a field initialized at class-load would keep
/// formatting in whatever language the app happened to start in. Style choice
/// (`MEDIUM` date, `SHORT` time) matches what `PlanExportService` already uses
/// for exported plans, so screen and export read alike.
public final class DateTimes {

    private DateTimes() {
    }

    /// A service's date and time, e.g. "30.07.2026, 10:00" / "Jul 30, 2026, 10:00 AM".
    public static String dateTime(LocalDateTime value) {
        return DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM, FormatStyle.SHORT).format(value);
    }

    /// As [#dateTime(LocalDateTime)], for an instant read in the system zone
    /// (e.g. when a plan was archived).
    public static String dateTime(Instant value) {
        return dateTime(LocalDateTime.ofInstant(value, ZoneId.systemDefault()));
    }

    /// A date on its own, e.g. "30.07.2026" / "Jul 30, 2026".
    public static String date(LocalDate value) {
        return DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).format(value);
    }

    /// A date squeezed for an axis or a chart legend, e.g. "30.07." / "7/30".
    public static String shortDate(LocalDate value) {
        return DateTimeFormatter.ofLocalizedDate(FormatStyle.SHORT).format(value);
    }

    /// The abbreviated weekday name in the current language, e.g. "Do." / "Thu".
    public static String weekday(LocalDate value) {
        return value.getDayOfWeek().getDisplayName(TextStyle.SHORT, Locale.getDefault());
    }

    /// A time of day on its own, e.g. "10:00" / "10:00 AM".
    public static String time(LocalTime value) {
        return DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT).format(value);
    }
}
