package org.mindis.gui.modules;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

import org.mindis.core.model.LiturgicalService;
import org.mindis.core.model.Role;
import org.mindis.core.model.ServiceType;
import org.mindis.core.persistence.RoleRepository;
import org.mindis.core.persistence.ServiceGenerator;
import org.mindis.core.persistence.ServiceRepository;
import org.mindis.core.persistence.TemplateRepository;

/**
 * ViewModel for {@link ServicesModule}: owns every repository call and the
 * template-generation logic, so the module only constructs UI and binds to
 * this class.
 */
final class ServicesViewModel {

    private static final int DEFAULT_DURATION_MINUTES = 60;

    private final ServiceRepository serviceRepository;
    private final TemplateRepository templateRepository;
    private final RoleRepository roleRepository;

    ServicesViewModel(ServiceRepository serviceRepository, TemplateRepository templateRepository,
                      RoleRepository roleRepository) {
        this.serviceRepository = serviceRepository;
        this.templateRepository = templateRepository;
        this.roleRepository = roleRepository;
    }

    List<LiturgicalService> findAll() {
        return serviceRepository.findAll();
    }

    void save(LiturgicalService service) {
        serviceRepository.save(service);
    }

    void delete(LiturgicalService service) {
        serviceRepository.delete(service.id());
    }

    /** A blank service at the next full hour, for the New action. */
    LiturgicalService createStub() {
        LocalDateTime nextFullHour = LocalDateTime.now()
                .withMinute(0).withSecond(0).withNano(0).plusHours(1);
        return new LiturgicalService(LiturgicalService.newId(), nextFullHour, DEFAULT_DURATION_MINUTES,
                "", ServiceType.OTHER, List.of(), "");
    }

    /** Roles available for the "required servers" slot editor. */
    List<Role> findAllRoles() {
        return roleRepository.findAll();
    }

    /**
     * Expands every weekly template into concrete services over
     * {@code [from, toInclusive]} and persists them.
     *
     * @return false if the range is invalid ({@code from}/{@code to} missing
     *         or reversed) and nothing was generated
     */
    boolean generateFromTemplates(LocalDate from, LocalDate to) {
        if (from == null || to == null || to.isBefore(from)) {
            return false;
        }
        List<LiturgicalService> generated = ServiceGenerator.generate(
                templateRepository.findAll(), serviceRepository.findAll(), from, to);
        serviceRepository.saveAll(generated);
        return true;
    }

    List<String> csvHeader() {
        return List.of("id", "date", "time", "durationMinutes", "location", "type", "slots", "note");
    }

    List<String> toCsvRow(LiturgicalService service) {
        return List.of(
                service.id(),
                service.dateTime().toLocalDate().toString(),
                service.dateTime().toLocalTime().toString(),
                String.valueOf(service.durationMinutes()),
                service.location(),
                service.type().name(),
                RoleSlotCsv.format(service.slots(), roleRepository),
                service.note());
    }

    /** Rows with an unparsable date/time are skipped; a blank id gets a fresh one. */
    LiturgicalService fromCsvRow(List<String> row) {
        LocalDate date = CsvFields.parseDate(CsvFields.at(row, 1));
        LocalTime time = CsvFields.parseTime(CsvFields.at(row, 2));
        if (date == null || time == null) {
            return null;
        }
        String id = CsvFields.at(row, 0);
        Integer duration = CsvFields.parseInt(CsvFields.at(row, 3));
        return new LiturgicalService(
                id.isEmpty() ? LiturgicalService.newId() : id,
                date.atTime(time),
                duration == null ? DEFAULT_DURATION_MINUTES : duration,
                CsvFields.at(row, 4),
                CsvFields.parseServiceType(CsvFields.at(row, 5), ServiceType.OTHER),
                RoleSlotCsv.parse(CsvFields.at(row, 6), roleRepository),
                CsvFields.at(row, 7));
    }
}
