package org.mindis.core.persistence;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.Month;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import org.mindis.core.model.LiturgicalDay;
import org.mindis.core.model.RecurrenceRule;

class RecurrenceCodecTest {

    @Test
    void everyRuleKindSurvivesAFormatParseRoundTrip() {
        List<RecurrenceRule> rules = List.of(
                RecurrenceRule.weekly(DayOfWeek.SUNDAY, DayOfWeek.TUESDAY),
                RecurrenceRule.dayOfMonth(13, -1),
                RecurrenceRule.nthWeekdayOfMonth(3, DayOfWeek.SUNDAY),
                new RecurrenceRule.NthWeekdayOfMonth(Set.of(1, -1), Set.of(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY)),
                RecurrenceRule.inMonths(Month.OCTOBER, Month.DECEMBER),
                RecurrenceRule.fixedMonthDay(Month.DECEMBER, 24),
                new RecurrenceRule.FixedDay(LocalDate.of(2026, 4, 5)),
                new RecurrenceRule.EveryNDays(10, LocalDate.of(2026, 1, 5)),
                new RecurrenceRule.EveryNWeeks(2, LocalDate.of(2026, 1, 5)),
                new RecurrenceRule.EveryNMonths(3, LocalDate.of(2026, 1, 5)),
                RecurrenceRule.allOf(RecurrenceRule.weekly(DayOfWeek.SUNDAY),
                        new RecurrenceRule.EveryNWeeks(2, LocalDate.of(2026, 1, 4))),
                RecurrenceRule.anyOf(RecurrenceRule.dayOfMonth(13),
                        RecurrenceRule.allOf(RecurrenceRule.nthWeekdayOfMonth(-1, DayOfWeek.SATURDAY),
                                RecurrenceRule.inMonths(Month.OCTOBER))),
                RecurrenceRule.not(RecurrenceRule.fixedMonthDay(Month.DECEMBER, 25)),
                RecurrenceRule.feast(LiturgicalDay.EASTER, 0),
                RecurrenceRule.feast(LiturgicalDay.EASTER, -2),
                RecurrenceRule.feast(LiturgicalDay.ADVENT_1, 1),
                RecurrenceRule.NEVER);

        for (RecurrenceRule rule : rules) {
            assertEquals(rule, RecurrenceCodec.parse(RecurrenceCodec.format(rule)),
                    "round trip of " + RecurrenceCodec.format(rule));
        }
    }

    @Test
    void formatIsStableRegardlessOfSetIterationOrder() {
        assertEquals("WEEKDAY:MONDAY,SUNDAY",
                RecurrenceCodec.format(RecurrenceRule.weekly(DayOfWeek.SUNDAY, DayOfWeek.MONDAY)));
        assertEquals("DOM:-1,13", RecurrenceCodec.format(RecurrenceRule.dayOfMonth(13, -1)));
        assertEquals("NTH:3/SUNDAY", RecurrenceCodec.format(RecurrenceRule.nthWeekdayOfMonth(3, DayOfWeek.SUNDAY)));
    }

    @Test
    void aFeastWithoutAnOffsetIsWrittenAndReadWithoutOne() {
        assertEquals("FEAST:EASTER", RecurrenceCodec.format(RecurrenceRule.feast(LiturgicalDay.EASTER, 0)));
        assertEquals("FEAST:EASTER-2", RecurrenceCodec.format(RecurrenceRule.feast(LiturgicalDay.EASTER, -2)));
        assertEquals("FEAST:ADVENT_1+1", RecurrenceCodec.format(RecurrenceRule.feast(LiturgicalDay.ADVENT_1, 1)));
        assertEquals(RecurrenceRule.feast(LiturgicalDay.ADVENT_1, 0), RecurrenceCodec.parse("FEAST:ADVENT_1+0"),
                "a feast name's own digits must not be read as an offset");
    }

    @Test
    void nestedGroupsKeepTheirOwnMembers() {
        RecurrenceRule parsed = RecurrenceCodec.parse("ANY(DOM:13; ALL(NTH:-1/SATURDAY; MONTH:OCTOBER))");

        assertEquals(RecurrenceRule.anyOf(RecurrenceRule.dayOfMonth(13),
                RecurrenceRule.allOf(RecurrenceRule.nthWeekdayOfMonth(-1, DayOfWeek.SATURDAY),
                        RecurrenceRule.inMonths(Month.OCTOBER))), parsed);
    }

    @Test
    void keywordsAndWhitespaceAreForgiving() {
        assertEquals(RecurrenceRule.weekly(DayOfWeek.SUNDAY), RecurrenceCodec.parse("  weekday : sunday "));
        assertEquals(RecurrenceRule.allOf(RecurrenceRule.weekly(DayOfWeek.SUNDAY), RecurrenceRule.dayOfMonth(13)),
                RecurrenceCodec.parse("all( WEEKDAY:SUNDAY ;DOM:13 )"));
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "",
            "   ",
            "WEEKDAY:",
            "WEEKDAY:FUNDAY",
            "SUNDAY",
            "DOM:0",
            "DOM:32",
            "DOM:abc",
            "NTH:3",
            "NTH:6/SUNDAY",
            "MONTH:SMARCH",
            "MONTHDAY:02-30",
            "DATE:2026-13-01",
            "EVERY_WEEKS:0@2026-01-05",
            "EVERY_WEEKS:2",
            "EVERY_WEEKS:2@not-a-date",
            "FEAST:",
            "FEAST:PANCAKE_DAY",
            "FEAST:EASTER+x",
            "ALL()",
            "ALL(WEEKDAY:SUNDAY; NOPE:1)",
            "NOT()"})
    void unreadableRulesParseToNullRatherThanAWrongPattern(String text) {
        assertNull(RecurrenceCodec.parse(text));
    }
}
