package org.mindis.core.persistence;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

import org.junit.jupiter.api.Test;

import org.mindis.core.model.LiturgicalService;
import org.mindis.core.model.ServiceTemplate;
import org.mindis.core.model.ServiceType;

class WriteThroughGeneratorTest {

    @Test
    void unsavedTemplateEditIsVisibleToGenerateFromTemplates() {
        TemplateRepository repository = new TemplateRepository();

        ServiceTemplate original = new ServiceTemplate(ServiceTemplate.newId(), DayOfWeek.SUNDAY,
                LocalTime.of(10, 0), 60, "St. Mary", ServiceType.SUNDAY_MASS, List.of());
        repository.save(original);

        // Edit staged only - never saved to a document - same as an unsaved
        // live edit in the GUI.
        ServiceTemplate edited = new ServiceTemplate(original.id(), original.dayOfWeek(), original.time(),
                original.durationMinutes(), original.location(), ServiceType.FEAST, original.slots());
        repository.save(edited);

        List<LiturgicalService> generated = ServiceGenerator.generate(
                repository.findAll(), List.of(), LocalDate.of(2026, 7, 5), LocalDate.of(2026, 7, 5));

        assertEquals(1, generated.size());
        assertEquals(ServiceType.FEAST, generated.getFirst().type(), "generator must see the unflushed template edit");
    }
}
