package org.mindis.core.persistence;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.time.DayOfWeek;
import java.time.LocalTime;
import java.time.Month;
import java.util.List;

import org.junit.jupiter.api.Test;

import org.mindis.core.model.RecurrenceRule;
import org.mindis.core.model.ServiceTemplate;
import org.mindis.core.model.ServiceType;

class TemplateCsvMapperTest {

    private final TemplateCsvMapper mapper = new TemplateCsvMapper(new RoleRepository());

    @Test
    void aTemplateSurvivesTheCsvRoundTripIncludingItsRecurrence() {
        ServiceTemplate template = new ServiceTemplate("id-1",
                RecurrenceRule.allOf(RecurrenceRule.nthWeekdayOfMonth(3, DayOfWeek.SUNDAY),
                        RecurrenceRule.not(RecurrenceRule.fixedMonthDay(Month.DECEMBER, 25))),
                LocalTime.of(10, 0), 60, "St. Mary", ServiceType.SUNDAY_MASS, List.of());

        assertEquals(template, mapper.fromRow(mapper.toRow(template)));
    }

    @Test
    void aHandWrittenRecurrenceColumnIsEnoughToImportATemplate() {
        ServiceTemplate template = mapper.fromRow(
                List.of("id-1", "WEEKDAY:SUNDAY", "10:00", "60", "St. Mary", "SUNDAY_MASS", ""));

        assertNotNull(template);
        assertEquals(RecurrenceRule.weekly(DayOfWeek.SUNDAY), template.recurrence());
    }

    @Test
    void aRowWithNoUsableRecurrenceIsSkipped() {
        assertNull(mapper.fromRow(List.of("id-1", "every other blue moon", "10:00", "60", "St. Mary", "SUNDAY_MASS", "")));
    }

    @Test
    void theRecurrenceColumnReplacedTheWeekdayColumnInPlace() {
        assertEquals(List.of("id", "recurrence", "time", "durationMinutes", "location", "type", "slots"),
                mapper.header());
    }
}
