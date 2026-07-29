package org.mindis.core.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.Month;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;

class RecurrenceRuleTest {

    @Test
    void weeklyMatchesEveryOccurrenceOfItsDays() {
        RecurrenceRule rule = RecurrenceRule.weekly(DayOfWeek.SUNDAY, DayOfWeek.TUESDAY);

        assertEquals(List.of(LocalDate.of(2026, 7, 5), LocalDate.of(2026, 7, 7), LocalDate.of(2026, 7, 12)),
                matchesIn(rule, LocalDate.of(2026, 7, 5), LocalDate.of(2026, 7, 12)));
    }

    @Test
    void dayOfMonthCountsFromTheEndWhenNegative() {
        RecurrenceRule rule = RecurrenceRule.dayOfMonth(13, -1);

        assertTrue(rule.matches(LocalDate.of(2026, 7, 13)));
        assertTrue(rule.matches(LocalDate.of(2026, 7, 31)), "-1 is July's 31st");
        assertTrue(rule.matches(LocalDate.of(2026, 2, 28)), "-1 is February's 28th in a common year");
        assertFalse(rule.matches(LocalDate.of(2026, 7, 30)));
    }

    @Test
    void dayOfMonthNeverClampsIntoAShorterMonth() {
        RecurrenceRule rule = RecurrenceRule.dayOfMonth(31);

        assertEquals(List.of(LocalDate.of(2026, 3, 31)),
                matchesIn(rule, LocalDate.of(2026, 2, 1), LocalDate.of(2026, 4, 30)),
                "February and April must produce nothing at all rather than a moved date");
    }

    @Test
    void nthWeekdayCountsFromBothEndsOfTheMonth() {
        RecurrenceRule third = RecurrenceRule.nthWeekdayOfMonth(3, DayOfWeek.SUNDAY);
        RecurrenceRule last = RecurrenceRule.nthWeekdayOfMonth(-1, DayOfWeek.SUNDAY);

        // August 2026 has five Sundays: 2, 9, 16, 23, 30.
        assertEquals(List.of(LocalDate.of(2026, 8, 16)),
                matchesIn(third, LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 31)));
        assertEquals(List.of(LocalDate.of(2026, 8, 30)),
                matchesIn(last, LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 31)));
    }

    @Test
    void nthWeekdayDistinguishesFourthFromLastInAFiveWeekdayMonth() {
        RecurrenceRule fourth = RecurrenceRule.nthWeekdayOfMonth(4, DayOfWeek.SUNDAY);

        assertTrue(fourth.matches(LocalDate.of(2026, 8, 23)));
        assertFalse(fourth.matches(LocalDate.of(2026, 8, 30)), "the fifth Sunday is not the fourth");
    }

    @Test
    void everyOtherSundayIsAWeekdayRuleCombinedWithAWeekCycle() {
        RecurrenceRule rule = RecurrenceRule.allOf(
                RecurrenceRule.weekly(DayOfWeek.SUNDAY),
                new RecurrenceRule.EveryNWeeks(2, LocalDate.of(2026, 7, 5)));

        assertEquals(List.of(LocalDate.of(2026, 7, 5), LocalDate.of(2026, 7, 19), LocalDate.of(2026, 8, 2)),
                matchesIn(rule, LocalDate.of(2026, 7, 5), LocalDate.of(2026, 8, 2)));
    }

    @Test
    void weekCycleIsAnchoredOnIsoWeeksAndRunsBackwardsToo() {
        RecurrenceRule rule = new RecurrenceRule.EveryNWeeks(2, LocalDate.of(2026, 7, 8));

        assertTrue(rule.matches(LocalDate.of(2026, 7, 6)), "same ISO week as the anchor");
        assertFalse(rule.matches(LocalDate.of(2026, 7, 13)));
        assertTrue(rule.matches(LocalDate.of(2026, 6, 24)), "two weeks before the anchor");
    }

    @Test
    void monthCycleIgnoresTheDayWithinTheMonth() {
        RecurrenceRule quarterly = RecurrenceRule.allOf(
                new RecurrenceRule.EveryNMonths(3, LocalDate.of(2026, 1, 31)),
                RecurrenceRule.dayOfMonth(1));

        assertEquals(List.of(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 4, 1), LocalDate.of(2026, 7, 1)),
                matchesIn(quarterly, LocalDate.of(2026, 1, 1), LocalDate.of(2026, 8, 31)));
    }

    @Test
    void monthFilterTurnsAMonthlyRuleIntoAYearlyOne() {
        RecurrenceRule rule = RecurrenceRule.allOf(
                RecurrenceRule.nthWeekdayOfMonth(1, DayOfWeek.SUNDAY),
                RecurrenceRule.inMonths(Month.OCTOBER));

        assertEquals(List.of(LocalDate.of(2026, 10, 4)),
                matchesIn(rule, LocalDate.of(2026, 1, 1), LocalDate.of(2026, 12, 31)));
    }

    @Test
    void fixedMonthDayRepeatsYearlyAndSkipsMissingLeapDays() {
        RecurrenceRule christmas = RecurrenceRule.fixedMonthDay(Month.DECEMBER, 24);
        RecurrenceRule leapDay = RecurrenceRule.fixedMonthDay(Month.FEBRUARY, 29);

        assertTrue(christmas.matches(LocalDate.of(2026, 12, 24)));
        assertTrue(christmas.matches(LocalDate.of(2027, 12, 24)));
        assertTrue(leapDay.matches(LocalDate.of(2028, 2, 29)));
        assertFalse(leapDay.matches(LocalDate.of(2026, 2, 28)));
    }

    @Test
    void notExcludesSingleOccurrencesFromAnotherRule() {
        RecurrenceRule rule = RecurrenceRule.allOf(
                RecurrenceRule.weekly(DayOfWeek.FRIDAY),
                RecurrenceRule.not(RecurrenceRule.fixedMonthDay(Month.DECEMBER, 25)));

        assertFalse(rule.matches(LocalDate.of(2026, 12, 25)));
        assertTrue(rule.matches(LocalDate.of(2026, 12, 18)));
    }

    @Test
    void anyOfUnionsItsMembers() {
        RecurrenceRule rule = RecurrenceRule.anyOf(
                RecurrenceRule.dayOfMonth(13),
                RecurrenceRule.weekly(DayOfWeek.SUNDAY));

        assertTrue(rule.matches(LocalDate.of(2026, 7, 13)), "a Monday, matched by the day-of-month member");
        assertTrue(rule.matches(LocalDate.of(2026, 7, 12)));
        assertFalse(rule.matches(LocalDate.of(2026, 7, 14)));
    }

    @Test
    void aFeastRuleMatchesThatFeastInEveryYear() {
        RecurrenceRule easter = RecurrenceRule.feast(LiturgicalDay.EASTER, 0);

        assertTrue(easter.matches(LocalDate.of(2026, 4, 5)));
        assertTrue(easter.matches(LocalDate.of(2027, 3, 28)));
        assertFalse(easter.matches(LocalDate.of(2026, 4, 12)));
    }

    @Test
    void aFeastOffsetShiftsWholeDaysAndMayCrossTheYearBoundary() {
        assertTrue(RecurrenceRule.feast(LiturgicalDay.EASTER, -2).matches(LocalDate.of(2026, 4, 3)));
        assertTrue(RecurrenceRule.feast(LiturgicalDay.ADVENT_1, -1).matches(LocalDate.of(2026, 11, 28)));
        assertTrue(RecurrenceRule.feast(LiturgicalDay.CHRISTMAS, 8).matches(LocalDate.of(2027, 1, 2)),
                "Christmas 2026 + 8 days lands in the next year");
        assertTrue(RecurrenceRule.feast(LiturgicalDay.NEW_YEAR, -1).matches(LocalDate.of(2026, 12, 31)),
                "New Year 2027 - 1 day lands in the previous year");
    }

    @Test
    void feastRulesCombineWithTheOtherRulesLikeAnyPredicate() {
        RecurrenceRule sundaysExceptEaster = RecurrenceRule.allOf(
                RecurrenceRule.weekly(DayOfWeek.SUNDAY),
                RecurrenceRule.not(RecurrenceRule.feast(LiturgicalDay.EASTER, 0)));

        assertFalse(sundaysExceptEaster.matches(LocalDate.of(2026, 4, 5)));
        assertTrue(sundaysExceptEaster.matches(LocalDate.of(2026, 4, 12)));
    }

    @Test
    void nextOccurrencesPreviewsWhatATemplateWouldGenerate() {
        RecurrenceRule rule = RecurrenceRule.nthWeekdayOfMonth(3, DayOfWeek.SUNDAY);

        assertEquals(List.of(LocalDate.of(2026, 7, 19), LocalDate.of(2026, 8, 16), LocalDate.of(2026, 9, 20)),
                rule.nextOccurrences(LocalDate.of(2026, 7, 1), 3));
    }

    @Test
    void nextOccurrencesTerminatesOnARuleThatMatchesNothing() {
        assertEquals(List.of(), RecurrenceRule.NEVER.nextOccurrences(LocalDate.of(2026, 7, 1), 5));
    }

    @Test
    void neverMatchesNothing() {
        assertFalse(RecurrenceRule.NEVER.matches(LocalDate.of(2026, 7, 5)));
    }

    @Test
    void aFeastAnchoredServiceIsGeneratedOnceAYear() {
        RecurrenceRule easterVigil = RecurrenceRule.feast(LiturgicalDay.HOLY_SATURDAY, 0);

        assertEquals(List.of(LocalDate.of(2026, 4, 4), LocalDate.of(2027, 3, 27)),
                matchesIn(easterVigil, LocalDate.of(2026, 1, 1), LocalDate.of(2027, 12, 31)));
    }

    @Test
    void rulesCompareByValueSoTheEditorCanDetectChanges() {
        assertEquals(RecurrenceRule.weekly(DayOfWeek.SUNDAY), RecurrenceRule.weekly(DayOfWeek.SUNDAY));
        assertEquals(RecurrenceRule.allOf(RecurrenceRule.weekly(DayOfWeek.SUNDAY)),
                RecurrenceRule.allOf(RecurrenceRule.weekly(DayOfWeek.SUNDAY)));
    }

    @Test
    void nonsensicalRulesAreRejectedWhereTheyAreBuilt() {
        assertThrows(IllegalArgumentException.class, () -> new RecurrenceRule.Weekday(Set.of()));
        assertThrows(IllegalArgumentException.class, () -> RecurrenceRule.dayOfMonth(0));
        assertThrows(IllegalArgumentException.class, () -> RecurrenceRule.dayOfMonth(32));
        assertThrows(IllegalArgumentException.class, () -> RecurrenceRule.nthWeekdayOfMonth(6, DayOfWeek.SUNDAY));
        assertThrows(IllegalArgumentException.class,
                () -> new RecurrenceRule.EveryNWeeks(0, LocalDate.of(2026, 1, 1)));
        assertThrows(IllegalArgumentException.class, () -> new RecurrenceRule.AllOf(List.of()));
    }

    private static List<LocalDate> matchesIn(RecurrenceRule rule, LocalDate from, LocalDate toInclusive) {
        return from.datesUntil(toInclusive.plusDays(1)).filter(rule::matches).toList();
    }
}
