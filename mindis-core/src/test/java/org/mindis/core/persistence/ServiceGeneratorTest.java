package org.mindis.core.persistence;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

import org.junit.jupiter.api.Test;

import org.mindis.core.model.LiturgicalService;
import org.mindis.core.model.RecurrenceRule;
import org.mindis.core.model.ServiceTemplate;
import org.mindis.core.model.ServiceType;

class ServiceGeneratorTest {

    @Test
    void aWeeklyTemplateProducesOneServicePerMatchingDay() {
        List<LiturgicalService> generated = generate(template(RecurrenceRule.weekly(DayOfWeek.SUNDAY)),
                LocalDate.of(2026, 7, 1), LocalDate.of(2026, 7, 31));

        assertEquals(List.of(
                        LocalDateTime.of(2026, 7, 5, 10, 0),
                        LocalDateTime.of(2026, 7, 12, 10, 0),
                        LocalDateTime.of(2026, 7, 19, 10, 0),
                        LocalDateTime.of(2026, 7, 26, 10, 0)),
                generated.stream().map(LiturgicalService::dateTime).toList());
    }

    @Test
    void aCombinedRuleGeneratesOnlyTheDaysItMatches() {
        ServiceTemplate template = template(RecurrenceRule.anyOf(
                RecurrenceRule.nthWeekdayOfMonth(3, DayOfWeek.SUNDAY),
                RecurrenceRule.dayOfMonth(13)));

        List<LiturgicalService> generated = generate(template, LocalDate.of(2026, 7, 1), LocalDate.of(2026, 7, 31));

        assertEquals(List.of(LocalDateTime.of(2026, 7, 13, 10, 0), LocalDateTime.of(2026, 7, 19, 10, 0)),
                generated.stream().map(LiturgicalService::dateTime).sorted().toList());
    }

    @Test
    void regeneratingTheSameRangeAddsNothing() {
        ServiceTemplate template = template(RecurrenceRule.weekly(DayOfWeek.SUNDAY));
        LocalDate from = LocalDate.of(2026, 7, 1);
        LocalDate to = LocalDate.of(2026, 7, 31);
        List<LiturgicalService> first = generate(template, from, to);

        List<LiturgicalService> second = ServiceGenerator.generate(List.of(template), first, from, to);

        assertTrue(second.isEmpty(), "an already generated occurrence must not be duplicated");
    }

    @Test
    void aTemplateWithoutAUsableRuleGeneratesNothing() {
        List<LiturgicalService> generated = generate(template(RecurrenceRule.NEVER),
                LocalDate.of(2026, 7, 1), LocalDate.of(2026, 7, 31));

        assertTrue(generated.isEmpty());
    }

    private static ServiceTemplate template(RecurrenceRule recurrence) {
        return new ServiceTemplate(ServiceTemplate.newId(), recurrence, LocalTime.of(10, 0), 60,
                "St. Mary", ServiceType.SUNDAY_MASS, List.of());
    }

    private static List<LiturgicalService> generate(ServiceTemplate template, LocalDate from, LocalDate to) {
        return ServiceGenerator.generate(List.of(template), List.of(), from, to);
    }
}
