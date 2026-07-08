package org.mindis.gui.modules;

import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.List;

import org.jspecify.annotations.NullMarked;

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
}
