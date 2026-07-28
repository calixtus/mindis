package org.mindis.core.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.Month;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class LiturgicalCalendarTest {

    /// Published Easter dates - the whole Easter cycle hangs off this one
    /// computation, so it is checked against a table rather than against
    /// itself. Includes the extremes of the Gregorian range (22 March 1818,
    /// 25 April 2038) and a century year, where the algorithm's leap-century
    /// correction bites.
    @ParameterizedTest
    @CsvSource({
            "1818, 1818-03-22",
            "1900, 1900-04-15",
            "2000, 2000-04-23",
            "2008, 2008-03-23",
            "2020, 2020-04-12",
            "2021, 2021-04-04",
            "2022, 2022-04-17",
            "2023, 2023-04-09",
            "2024, 2024-03-31",
            "2025, 2025-04-20",
            "2026, 2026-04-05",
            "2027, 2027-03-28",
            "2028, 2028-04-16",
            "2029, 2029-04-01",
            "2030, 2030-04-21",
            "2031, 2031-04-13",
            "2032, 2032-03-28",
            "2033, 2033-04-17",
            "2034, 2034-04-09",
            "2035, 2035-03-25",
            "2038, 2038-04-25"})
    void easterMatchesThePublishedDates(int year, LocalDate expected) {
        assertEquals(expected, LiturgicalCalendar.easter(year));
    }

    @Test
    void easterIsAlwaysASundayInMarchOrApril() {
        for (int year = 1900; year <= 2100; year++) {
            LocalDate easter = LiturgicalCalendar.easter(year);
            assertEquals(DayOfWeek.SUNDAY, easter.getDayOfWeek(), "Easter " + year);
            assertTrue(easter.getMonth() == Month.MARCH || easter.getMonth() == Month.APRIL,
                    "Easter " + year + " fell in " + easter.getMonth());
        }
    }

    /// The dates German parishes print in their calendars, all derived from
    /// Easter 2026 (5 April).
    @Test
    void theEasterCycleDerivesTheMovableFeasts() {
        assertEquals(LocalDate.of(2026, 2, 18), LiturgicalDay.ASH_WEDNESDAY.dateIn(2026));
        assertEquals(LocalDate.of(2026, 4, 3), LiturgicalDay.GOOD_FRIDAY.dateIn(2026));
        assertEquals(LocalDate.of(2026, 4, 5), LiturgicalDay.EASTER.dateIn(2026));
        assertEquals(LocalDate.of(2026, 5, 14), LiturgicalDay.ASCENSION.dateIn(2026));
        assertEquals(LocalDate.of(2026, 5, 24), LiturgicalDay.PENTECOST.dateIn(2026));
        assertEquals(LocalDate.of(2026, 6, 4), LiturgicalDay.CORPUS_CHRISTI.dateIn(2026));
    }

    /// Advent 1 moves across a five-week band; the years below cover Christmas
    /// Eve falling on a Sunday (2023, when Advent 4 *is* Christmas Eve) and on
    /// a Saturday (2022, its earliest position).
    @ParameterizedTest
    @CsvSource({
            "2022, 2022-11-27",
            "2023, 2023-12-03",
            "2024, 2024-12-01",
            "2025, 2025-11-30",
            "2026, 2026-11-29"})
    void firstAdventSundayIsThreeWeeksBeforeTheLastSundayOnOrBeforeChristmasEve(int year, LocalDate expected) {
        assertEquals(expected, LiturgicalDay.ADVENT_1.dateIn(year));
    }

    @Test
    void theFourthAdventSundayCanBeChristmasEveItself() {
        assertEquals(LocalDate.of(2023, 12, 24), LiturgicalDay.ADVENT_4.dateIn(2023));
        assertEquals(LocalDate.of(2026, 12, 20), LiturgicalDay.ADVENT_4.dateIn(2026));
    }

    @Test
    void adventSundaysOutsideOneToFourAreRejected() {
        assertThrows(IllegalArgumentException.class, () -> LiturgicalCalendar.adventSunday(2026, 0));
        assertThrows(IllegalArgumentException.class, () -> LiturgicalCalendar.adventSunday(2026, 5));
    }

    @Test
    void germanUsageDaysFollowTheirOwnWeekdayRules() {
        // Erntedank: first Sunday of October.
        assertEquals(LocalDate.of(2026, 10, 4), LiturgicalDay.HARVEST_THANKSGIVING.dateIn(2026));
        // Buss- und Bettag: the Wednesday before 23 November.
        assertEquals(LocalDate.of(2026, 11, 18), LiturgicalDay.REPENTANCE_DAY.dateIn(2026));
        assertEquals(LocalDate.of(2025, 11, 19), LiturgicalDay.REPENTANCE_DAY.dateIn(2025));
        // Totensonntag: the Sunday before Advent 1.
        assertEquals(LocalDate.of(2026, 11, 22), LiturgicalDay.ETERNITY_SUNDAY.dateIn(2026));
    }

    @Test
    void everyDayResolvesForEveryYearOfAPlanningLifetime() {
        for (LiturgicalDay day : LiturgicalDay.values()) {
            for (int year = 2020; year <= 2060; year++) {
                assertEquals(year, day.dateIn(year).getYear(), day + " in " + year);
            }
        }
    }
}
