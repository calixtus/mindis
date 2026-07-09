package org.mindis.gui.modules;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import org.jspecify.annotations.Nullable;
import org.jspecify.annotations.NullMarked;

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
@NullMarked
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

    /** One whole-file rewrite for every dirty row, instead of one rewrite per row. */
    void saveAll(List<LiturgicalService> services) {
        serviceRepository.saveAll(services);
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
     * {@code [from, toInclusive]} - pure computation, nothing is persisted;
     * the caller merges the result into its live table state and it's
     * written to disk on the next "Save all".
     *
     * @param existing services already in the live table (not the
     *                 repository - a not-yet-saved live edit should count
     *                 against duplicate generation just as much as a saved
     *                 one), so generation doesn't re-propose a service that's
     *                 only sitting unsaved in the table
     * @return null if the range is invalid ({@code from}/{@code to} missing
     *         or reversed)
     */
    @Nullable List<LiturgicalService> generateFromTemplates(LocalDate from, LocalDate to,
                                                             List<LiturgicalService> existing) {
        if (from == null || to == null || to.isBefore(from)) {
            return null;
        }
        return ServiceGenerator.generate(templateRepository.findAll(), existing, from, to);
    }
}
