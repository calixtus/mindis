package org.mindis.gui.modules;

import java.time.DayOfWeek;
import java.time.LocalTime;
import java.time.format.TextStyle;
import java.util.List;
import java.util.Locale;

import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import org.mindis.core.model.RecurrenceRule;
import org.mindis.core.model.Role;
import org.mindis.core.model.ServiceTemplate;
import org.mindis.core.model.ServiceType;
import org.mindis.core.persistence.RecurrenceCodec;
import org.mindis.core.persistence.RoleRepository;

/// ViewModel for {@link TemplatesModule}: owns the repository reads the module
/// still needs directly (CRUD goes through the shared
/// {@link org.mindis.workbench.LiveStore}), so the module only constructs UI
/// and binds to this class.
@NullMarked
final class TemplatesViewModel {

    private static final int DEFAULT_DURATION_MINUTES = 60;

    private final RoleRepository roleRepository;

    TemplatesViewModel(RoleRepository roleRepository) {
        this.roleRepository = roleRepository;
    }

    /// A blank Sunday-mass template, for the New action.
    ServiceTemplate createStub() {
        return ServiceTemplate.weekly(ServiceTemplate.newId(), DayOfWeek.SUNDAY, LocalTime.of(10, 0),
                DEFAULT_DURATION_MINUTES, "", ServiceType.SUNDAY_MASS, List.of());
    }

    /// The single weekday a rule stands for, or {@code null} if it is anything
    /// richer than "every &lt;weekday&gt;". The weekday editor can only
    /// represent the simple case; richer rules (imported from CSV or written
    /// by a later milestone's editor) are shown read-only and left untouched.
    static @Nullable DayOfWeek simpleWeekday(RecurrenceRule rule) {
        if (rule instanceof RecurrenceRule.Weekday weekday && weekday.days().size() == 1) {
            return weekday.days().iterator().next();
        }
        return null;
    }

    /// A rule as one line for the table column: the localized weekday for the
    /// simple case, the rule's text form otherwise. A properly worded
    /// description of an arbitrary rule arrives with the full recurrence
    /// editor.
    static String describe(RecurrenceRule rule) {
        DayOfWeek weekday = simpleWeekday(rule);
        return weekday == null
                ? RecurrenceCodec.format(rule)
                : weekday.getDisplayName(TextStyle.FULL, Locale.getDefault());
    }

    /// Roles available for the "required servers" slot editor.
    List<Role> findAllRoles() {
        return roleRepository.findAll();
    }
}
