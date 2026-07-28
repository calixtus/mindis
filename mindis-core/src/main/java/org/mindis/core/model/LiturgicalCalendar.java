package org.mindis.core.model;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.Month;
import java.time.temporal.TemporalAdjusters;

/// The date arithmetic behind {@link LiturgicalDay}: Easter, the Advent
/// Sundays and the weekday rules a few regional feasts are defined by.
///
/// <p>Every liturgical date is a pure function of the year, which is what lets
/// a {@link RecurrenceRule} stay a plain date predicate with no calendar to
/// pass around. Results are cheap enough (integer arithmetic plus a handful of
/// {@link LocalDate} operations) that generation recomputes them per day
/// rather than caching.
///
/// <p>Western/Gregorian reckoning only - the Orthodox (Julian) Easter is a
/// different computation and is not what this application's parishes plan
/// against.
public final class LiturgicalCalendar {

    private LiturgicalCalendar() {
    }

    /// Easter Sunday, by the anonymous Gregorian algorithm (Meeus/Jones/
    /// Butcher). Valid for every year of the Gregorian calendar.
    public static LocalDate easter(int year) {
        int a = year % 19;
        int b = year / 100;
        int c = year % 100;
        int d = b / 4;
        int e = b % 4;
        int f = (b + 8) / 25;
        int g = (b - f + 1) / 3;
        int h = (19 * a + b - d - g + 15) % 30;
        int i = c / 4;
        int k = c % 4;
        int l = (32 + 2 * e + 2 * i - h - k) % 7;
        int m = (a + 11 * h + 22 * l) / 451;
        int month = (h + l - 7 * m + 114) / 31;
        int day = (h + l - 7 * m + 114) % 31 + 1;
        return LocalDate.of(year, month, day);
    }

    /// Easter Sunday of {@code year} shifted by {@code offsetDays} - how every
    /// movable feast of the Easter cycle is defined (Ash Wednesday -46,
    /// Ascension +39, Corpus Christi +60, ...).
    public static LocalDate easterPlus(int year, int offsetDays) {
        return easter(year).plusDays(offsetDays);
    }

    /// The {@code number}-th Sunday of Advent (1-4) in {@code year}. The
    /// fourth is the last Sunday on or before 24 December - which is 24
    /// December itself when Christmas Eve falls on a Sunday - and the earlier
    /// ones follow a week apart.
    public static LocalDate adventSunday(int year, int number) {
        if (number < 1 || number > 4) {
            throw new IllegalArgumentException("Advent Sunday out of range: " + number);
        }
        LocalDate fourth = lastOnOrBefore(LocalDate.of(year, Month.DECEMBER, 24), DayOfWeek.SUNDAY);
        return fourth.minusWeeks(4L - number);
    }

    /// The last {@code day} on or before {@code date}.
    public static LocalDate lastOnOrBefore(LocalDate date, DayOfWeek day) {
        return date.with(TemporalAdjusters.previousOrSame(day));
    }

    /// The first {@code day} of the given month, e.g. Harvest Thanksgiving's
    /// first Sunday of October.
    public static LocalDate firstInMonth(int year, Month month, DayOfWeek day) {
        return LocalDate.of(year, month, 1).with(TemporalAdjusters.firstInMonth(day));
    }
}
