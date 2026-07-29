package org.mindis.core.l10n;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.Month;
import java.util.Locale;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import org.mindis.core.model.LiturgicalDay;
import org.mindis.core.model.RecurrenceRule;

/// Pins the English wording; the point is that every rule shape produces a
/// sentence rather than syntax, including nested composites.
class RecurrenceTextTest {

    private static Locale originalLocale;

    @BeforeAll
    static void useEnglish() {
        originalLocale = Locale.getDefault();
        Localization.setLocale(Locale.ENGLISH);
    }

    @AfterAll
    static void restoreLocale() {
        Localization.setLocale(originalLocale);
    }

    @Test
    void weekdaysAreListedInCalendarOrder() {
        assertEquals("Every Sunday", RecurrenceText.describe(RecurrenceRule.weekly(DayOfWeek.SUNDAY)));
        assertEquals("Every Monday and Sunday",
                RecurrenceText.describe(RecurrenceRule.weekly(DayOfWeek.SUNDAY, DayOfWeek.MONDAY)));
    }

    @Test
    void monthDaysCountFromTheEndWhenNegative() {
        assertEquals("Day 13 of the month", RecurrenceText.describe(RecurrenceRule.dayOfMonth(13)));
        assertEquals("Day 13 and last day of the month", RecurrenceText.describe(RecurrenceRule.dayOfMonth(13, -1)));
    }

    @Test
    void ordinalsAreWords() {
        assertEquals("The third Sunday of the month",
                RecurrenceText.describe(RecurrenceRule.nthWeekdayOfMonth(3, DayOfWeek.SUNDAY)));
        assertEquals("The last Saturday of the month",
                RecurrenceText.describe(RecurrenceRule.nthWeekdayOfMonth(-1, DayOfWeek.SATURDAY)));
    }

    @Test
    void cyclesReadAsIntervalsAndNameTheirAnchor() {
        assertEquals("Every week", RecurrenceText.describe(new RecurrenceRule.EveryNWeeks(1, LocalDate.of(2026, 1, 5))));
        assertEquals("Every other week from 2026-01-05",
                RecurrenceText.describe(new RecurrenceRule.EveryNWeeks(2, LocalDate.of(2026, 1, 5))));
        assertEquals("Every 3 months from 2026-01-05",
                RecurrenceText.describe(new RecurrenceRule.EveryNMonths(3, LocalDate.of(2026, 1, 5))));
    }

    @Test
    void feastsUseTheirLocalizedNameAndSignedOffset() {
        assertEquals("Easter Sunday", RecurrenceText.describe(RecurrenceRule.feast(LiturgicalDay.EASTER, 0)));
        assertEquals("Easter Sunday minus 2 days",
                RecurrenceText.describe(RecurrenceRule.feast(LiturgicalDay.EASTER, -2)));
        assertEquals("First Sunday of Advent plus 1 days",
                RecurrenceText.describe(RecurrenceRule.feast(LiturgicalDay.ADVENT_1, 1)));
    }

    @Test
    void compositesAreDescribedThroughTheirParts() {
        assertEquals("Every Sunday, Every other week from 2026-01-05",
                RecurrenceText.describe(RecurrenceRule.allOf(RecurrenceRule.weekly(DayOfWeek.SUNDAY),
                        new RecurrenceRule.EveryNWeeks(2, LocalDate.of(2026, 1, 5)))));
        assertEquals("Day 13 of the month or Every Sunday",
                RecurrenceText.describe(RecurrenceRule.anyOf(RecurrenceRule.dayOfMonth(13),
                        RecurrenceRule.weekly(DayOfWeek.SUNDAY))));
        assertEquals("Every Sunday, except Easter Sunday",
                RecurrenceText.describe(RecurrenceRule.allOf(RecurrenceRule.weekly(DayOfWeek.SUNDAY),
                        RecurrenceRule.not(RecurrenceRule.feast(LiturgicalDay.EASTER, 0)))));
    }

    @Test
    void yearlyAndOneOffDatesReadAsDates() {
        assertEquals("Every year on 24 December",
                RecurrenceText.describe(RecurrenceRule.fixedMonthDay(Month.DECEMBER, 24)));
        assertEquals("On 2026-04-05", RecurrenceText.describe(new RecurrenceRule.FixedDay(LocalDate.of(2026, 4, 5))));
        assertEquals("In October", RecurrenceText.describe(RecurrenceRule.inMonths(Month.OCTOBER)));
    }

    @Test
    void anEmptyRuleSaysSoInsteadOfShowingNothing() {
        assertEquals("No dates", RecurrenceText.describe(RecurrenceRule.NEVER));
    }
}
