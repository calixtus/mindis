package org.mindis.gui.modules;

import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.List;

import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import org.mindis.core.model.Role;
import org.mindis.core.model.ServiceTemplate;
import org.mindis.core.model.ServiceType;
import org.mindis.core.persistence.RoleRepository;
import org.mindis.core.persistence.TemplateRepository;

/**
 * ViewModel for {@link TemplatesModule}: owns every repository call, so the
 * module only constructs UI and binds to this class.
 */
@NullMarked
final class TemplatesViewModel {

    private static final int DEFAULT_DURATION_MINUTES = 60;

    private final TemplateRepository templateRepository;
    private final RoleRepository roleRepository;

    TemplatesViewModel(TemplateRepository templateRepository, RoleRepository roleRepository) {
        this.templateRepository = templateRepository;
        this.roleRepository = roleRepository;
    }

    List<ServiceTemplate> findAll() {
        return templateRepository.findAll();
    }

    void save(ServiceTemplate template) {
        templateRepository.save(template);
    }

    void delete(ServiceTemplate template) {
        templateRepository.delete(template.id());
    }

    /** A blank Sunday-mass template, for the New action. */
    ServiceTemplate createStub() {
        return new ServiceTemplate(ServiceTemplate.newId(), DayOfWeek.SUNDAY, LocalTime.of(10, 0),
                DEFAULT_DURATION_MINUTES, "", ServiceType.SUNDAY_MASS, List.of());
    }

    /** Roles available for the "required servers" slot editor. */
    List<Role> findAllRoles() {
        return roleRepository.findAll();
    }

    List<String> csvHeader() {
        return List.of("id", "dayOfWeek", "time", "durationMinutes", "location", "type", "slots");
    }

    List<String> toCsvRow(ServiceTemplate template) {
        return List.of(
                template.id(),
                template.dayOfWeek().name(),
                template.time().toString(),
                String.valueOf(template.durationMinutes()),
                template.location(),
                template.type().name(),
                RoleSlotCsv.format(template.slots(), roleRepository));
    }

    /** Rows with an unparsable weekday/time are skipped; a blank id gets a fresh one. */
    @Nullable ServiceTemplate fromCsvRow(List<String> row) {
        DayOfWeek day = CsvFields.parseDayOfWeek(CsvFields.at(row, 1));
        LocalTime time = CsvFields.parseTime(CsvFields.at(row, 2));
        if (day == null || time == null) {
            return null;
        }
        String id = CsvFields.at(row, 0);
        Integer duration = CsvFields.parseInt(CsvFields.at(row, 3));
        return new ServiceTemplate(
                id.isEmpty() ? ServiceTemplate.newId() : id,
                day,
                time,
                duration == null ? DEFAULT_DURATION_MINUTES : duration,
                CsvFields.at(row, 4),
                CsvFields.parseServiceType(CsvFields.at(row, 5), ServiceType.SUNDAY_MASS),
                RoleSlotCsv.parse(CsvFields.at(row, 6), roleRepository));
    }
}
