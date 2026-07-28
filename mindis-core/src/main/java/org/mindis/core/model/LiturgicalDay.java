package org.mindis.core.model;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.Month;
import java.util.function.IntFunction;

/// The feast days a {@link RecurrenceRule.FeastRelative} rule can be anchored
/// on, each resolvable to its date in any year.
///
/// <p>Two groups, both pure functions of the year: the movable feasts of the
/// Easter cycle (offsets from Easter Sunday) and the Christmas cycle, and the
/// fixed-date feasts, which are here so that a template can be written against
/// the feast rather than against a bare 15 August.
///
/// <p>A handful of days are German-usage rather than general-calendar
/// ({@link #REPENTANCE_DAY}, {@link #HARVEST_THANKSGIVING},
/// {@link #ETERNITY_SUNDAY}); they are marked as such below. Dioceses that
/// transfer Ascension or Corpus Christi to the following Sunday are not
/// modelled - a parish doing that writes the transferred rule instead
/// ({@code FEAST:ASCENSION+3}).
///
/// <p>Constant names are part of the persisted formats (JSON and the CSV
/// recurrence column); renaming one breaks existing documents.
public enum LiturgicalDay {

    // --- Easter cycle (movable) ---
    ASH_WEDNESDAY(easterPlus(-46)),
    PALM_SUNDAY(easterPlus(-7)),
    MAUNDY_THURSDAY(easterPlus(-3)),
    GOOD_FRIDAY(easterPlus(-2)),
    HOLY_SATURDAY(easterPlus(-1)),
    EASTER(easterPlus(0)),
    EASTER_MONDAY(easterPlus(1)),
    ASCENSION(easterPlus(39)),
    PENTECOST(easterPlus(49)),
    WHIT_MONDAY(easterPlus(50)),
    TRINITY_SUNDAY(easterPlus(56)),
    CORPUS_CHRISTI(easterPlus(60)),

    // --- Christmas cycle ---
    ADVENT_1(adventSunday(1)),
    ADVENT_2(adventSunday(2)),
    ADVENT_3(adventSunday(3)),
    ADVENT_4(adventSunday(4)),
    /// The Sunday before Advent, which ends the liturgical year.
    CHRIST_THE_KING(year -> LiturgicalCalendar.adventSunday(year, 1).minusWeeks(1)),
    CHRISTMAS_EVE(fixed(Month.DECEMBER, 24)),
    CHRISTMAS(fixed(Month.DECEMBER, 25)),
    ST_STEPHEN(fixed(Month.DECEMBER, 26)),
    NEW_YEAR(fixed(Month.JANUARY, 1)),
    EPIPHANY(fixed(Month.JANUARY, 6)),

    // --- Fixed feasts ---
    CANDLEMAS(fixed(Month.FEBRUARY, 2)),
    ANNUNCIATION(fixed(Month.MARCH, 25)),
    ASSUMPTION(fixed(Month.AUGUST, 15)),
    ALL_SAINTS(fixed(Month.NOVEMBER, 1)),
    ALL_SOULS(fixed(Month.NOVEMBER, 2)),
    IMMACULATE_CONCEPTION(fixed(Month.DECEMBER, 8)),

    // --- German usage ---
    /// Erntedank: first Sunday of October (German usage).
    HARVEST_THANKSGIVING(year -> LiturgicalCalendar.firstInMonth(year, Month.OCTOBER, DayOfWeek.SUNDAY)),
    /// Buss- und Bettag: the Wednesday before 23 November (German usage).
    REPENTANCE_DAY(year -> LiturgicalCalendar.lastOnOrBefore(
            LocalDate.of(year, Month.NOVEMBER, 22), DayOfWeek.WEDNESDAY)),
    /// Totensonntag/Ewigkeitssonntag: the Sunday before Advent (German usage,
    /// Protestant in origin but planned for in shared churches).
    ETERNITY_SUNDAY(year -> LiturgicalCalendar.adventSunday(year, 1).minusWeeks(1));

    private final IntFunction<LocalDate> resolver;

    LiturgicalDay(IntFunction<LocalDate> resolver) {
        this.resolver = resolver;
    }

    /// This day's date in {@code year}. For the Christmas-cycle days that is
    /// the occurrence belonging to that calendar year, not to the liturgical
    /// year that starts with Advent.
    public LocalDate dateIn(int year) {
        return resolver.apply(year);
    }

    private static IntFunction<LocalDate> easterPlus(int offsetDays) {
        return year -> LiturgicalCalendar.easterPlus(year, offsetDays);
    }

    private static IntFunction<LocalDate> adventSunday(int number) {
        return year -> LiturgicalCalendar.adventSunday(year, number);
    }

    private static IntFunction<LocalDate> fixed(Month month, int day) {
        return year -> LocalDate.of(year, month, day);
    }
}
