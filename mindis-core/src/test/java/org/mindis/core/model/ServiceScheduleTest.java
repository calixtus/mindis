package org.mindis.core.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;

class ServiceScheduleTest {

    private static final RecurrenceRule SUNDAYS = RecurrenceRule.weekly(DayOfWeek.SUNDAY);

    @Test
    void aScheduleWithoutAWindowIsJustItsRule() {
        ServiceSchedule schedule = ServiceSchedule.of(SUNDAYS);

        assertTrue(schedule.occursOn(LocalDate.of(2026, 7, 5)));
        assertTrue(schedule.occursOn(LocalDate.of(2020, 1, 5)));
        assertFalse(schedule.occursOn(LocalDate.of(2026, 7, 6)));
    }

    @Test
    void bothWindowBoundsAreInclusive() {
        ServiceSchedule schedule = ServiceSchedule.of(SUNDAYS)
                .withWindow(LocalDate.of(2026, 7, 12), LocalDate.of(2026, 7, 26));

        assertFalse(schedule.occursOn(LocalDate.of(2026, 7, 5)), "before the window");
        assertTrue(schedule.occursOn(LocalDate.of(2026, 7, 12)), "the window's first day");
        assertTrue(schedule.occursOn(LocalDate.of(2026, 7, 26)), "the window's last day");
        assertFalse(schedule.occursOn(LocalDate.of(2026, 8, 2)), "after the window");
    }

    @Test
    void anOpenEndedWindowBoundsOnlyOneSide() {
        assertFalse(ServiceSchedule.of(SUNDAYS).withWindow(LocalDate.of(2026, 7, 12), null)
                .occursOn(LocalDate.of(2026, 7, 5)));
        assertTrue(ServiceSchedule.of(SUNDAYS).withWindow(LocalDate.of(2026, 7, 12), null)
                .occursOn(LocalDate.of(2030, 7, 7)));
        assertTrue(ServiceSchedule.of(SUNDAYS).withWindow(null, LocalDate.of(2026, 7, 12))
                .occursOn(LocalDate.of(2020, 7, 5)));
    }

    @Test
    void skippedDatesDropSingleOccurrencesWithoutTouchingThePattern() {
        ServiceSchedule schedule = ServiceSchedule.of(SUNDAYS)
                .withSkipDates(Set.of(LocalDate.of(2026, 8, 2), LocalDate.of(2026, 8, 9)));

        assertFalse(schedule.occursOn(LocalDate.of(2026, 8, 2)));
        assertFalse(schedule.occursOn(LocalDate.of(2026, 8, 9)));
        assertTrue(schedule.occursOn(LocalDate.of(2026, 8, 16)));
        assertEquals(SUNDAYS, schedule.rule(), "the rule itself stays the plain weekly pattern");
    }

    @Test
    void previewSkipsWhatGenerationWouldSkip() {
        ServiceSchedule schedule = new ServiceSchedule(SUNDAYS,
                LocalDate.of(2026, 7, 10),
                LocalDate.of(2026, 8, 10),
                Set.of(LocalDate.of(2026, 7, 19)));

        assertEquals(List.of(LocalDate.of(2026, 7, 12), LocalDate.of(2026, 7, 26), LocalDate.of(2026, 8, 2),
                        LocalDate.of(2026, 8, 9)),
                schedule.nextOccurrences(LocalDate.of(2026, 7, 1), 10),
                "the window bounds the preview and the skipped Sunday is missing from it");
    }

    @Test
    void previewOfAnExpiredScheduleIsEmpty() {
        ServiceSchedule schedule = ServiceSchedule.of(SUNDAYS).withWindow(null, LocalDate.of(2020, 1, 1));

        assertEquals(List.of(), schedule.nextOccurrences(LocalDate.of(2026, 7, 1), 5));
    }

    @Test
    void skippedDatesAreStoredSortedSoSavingTwiceProducesTheSameFile() {
        ServiceSchedule schedule = ServiceSchedule.of(SUNDAYS).withSkipDates(
                Set.of(LocalDate.of(2026, 8, 9), LocalDate.of(2026, 7, 5), LocalDate.of(2026, 8, 2)));

        assertEquals(List.of(LocalDate.of(2026, 7, 5), LocalDate.of(2026, 8, 2), LocalDate.of(2026, 8, 9)),
                List.copyOf(schedule.skipDates()));
    }

    @Test
    void schedulesCompareByValueRegardlessOfHowTheirSkipDatesWereBuilt() {
        assertEquals(ServiceSchedule.of(SUNDAYS).withSkipDates(Set.of(LocalDate.of(2026, 8, 2))),
                new ServiceSchedule(SUNDAYS, null, null, Set.of(LocalDate.of(2026, 8, 2))));
    }
}
