package org.mindis.core.persistence;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.Month;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;

import org.mindis.core.model.RecurrenceRule;
import org.mindis.core.model.ServiceSchedule;
import org.mindis.core.model.ServiceTemplate;
import org.mindis.core.model.ServiceType;

class TemplateCsvMapperTest {

    private final TemplateCsvMapper mapper = new TemplateCsvMapper(new RoleRepository());

    @Test
    void aTemplateSurvivesTheCsvRoundTripIncludingItsSchedule() {
        ServiceTemplate template = new ServiceTemplate("id-1",
                new ServiceSchedule(
                        RecurrenceRule.allOf(RecurrenceRule.nthWeekdayOfMonth(3, DayOfWeek.SUNDAY),
                                RecurrenceRule.not(RecurrenceRule.fixedMonthDay(Month.DECEMBER, 25))),
                        LocalDate.of(2026, 9, 1),
                        LocalDate.of(2027, 6, 30),
                        Set.of(LocalDate.of(2026, 12, 20), LocalDate.of(2027, 1, 17))),
                LocalTime.of(10, 0), 60, "St. Mary", ServiceType.SUNDAY_MASS, List.of());

        assertEquals(template, mapper.fromRow(mapper.toRow(template)));
    }

    @Test
    void aHandWrittenRecurrenceColumnIsEnoughToImportATemplate() {
        ServiceTemplate template = mapper.fromRow(
                List.of("id-1", "WEEKDAY:SUNDAY", "10:00", "60", "St. Mary", "SUNDAY_MASS", ""));

        assertNotNull(template);
        assertEquals(ServiceSchedule.of(RecurrenceRule.weekly(DayOfWeek.SUNDAY)), template.schedule(),
                "a row that stops before the schedule columns means no window and no cancellations");
    }

    @Test
    void anUnreadableScheduleDateIsDroppedRatherThanFailingTheRow() {
        ServiceTemplate template = mapper.fromRow(
                List.of("id-1", "WEEKDAY:SUNDAY", "10:00", "60", "St. Mary", "SUNDAY_MASS", "",
                        "2026-09-01", "not a date", "2026-12-20, nonsense"));

        assertNotNull(template);
        assertEquals(LocalDate.of(2026, 9, 1), template.schedule().validFrom());
        assertNull(template.schedule().validUntil());
        assertEquals(Set.of(LocalDate.of(2026, 12, 20)), template.schedule().skipDates());
    }

    @Test
    void aRowWithNoUsableRecurrenceIsSkipped() {
        assertNull(mapper.fromRow(List.of("id-1", "every other blue moon", "10:00", "60", "St. Mary", "SUNDAY_MASS", "")));
    }

    @Test
    void theHeaderCarriesTheRecurrenceAndScheduleColumns() {
        assertEquals(List.of("id", "recurrence", "time", "durationMinutes", "location", "type", "slots",
                        "validFrom", "validUntil", "skipDates"),
                mapper.header());
    }
}
