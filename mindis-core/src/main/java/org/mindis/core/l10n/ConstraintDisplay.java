package org.mindis.core.l10n;

import org.mindis.core.planning.MinDisConstraintProvider;

/// Localized names for the solver's constraints, the counterpart of [EnumDisplay] for
/// the one identity that is a string rather than an enum: Timefold names a constraint,
/// the preferences persist soft weights keyed by that name, and it must never change
/// just because the wording does.
///
/// <p>Each name is mapped to a key written out in full here, so the localization test can
/// see it. The mapping is exhaustive over [MinDisConstraintProvider]'s names; an
/// unknown name is shown as it is rather than hidden, since a constraint the solver
/// reports is worth seeing even untranslated.
public final class ConstraintDisplay {

    private ConstraintDisplay() {
    }

    public static String of(String constraintName) {
        return switch (constraintName) {
            case MinDisConstraintProvider.NOT_QUALIFIED -> Localization.lang("Server not qualified for role");
            case MinDisConstraintProvider.UNAVAILABLE -> Localization.lang("Server unavailable");
            case MinDisConstraintProvider.INACTIVE -> Localization.lang("Server inactive");
            case MinDisConstraintProvider.DOUBLE_BOOKED -> Localization.lang("Server double-booked");
            case MinDisConstraintProvider.INCOMPATIBLE_ROLE -> Localization.lang("Incompatible role in service");
            case MinDisConstraintProvider.UNASSIGNED -> Localization.lang("Slot unassigned");
            case MinDisConstraintProvider.UNBALANCED_WORKLOAD -> Localization.lang("Unbalanced workload");
            case MinDisConstraintProvider.SIBLINGS_TOGETHER -> Localization.lang("Siblings serve together");
            case MinDisConstraintProvider.TOO_CLOSE -> Localization.lang("Assignments too close together");
            case MinDisConstraintProvider.TOO_CLOSE_TO_PRIOR_PLAN -> Localization.lang("Too close to previous plan");
            case MinDisConstraintProvider.PREFERRED_TIME -> Localization.lang("Preferred service time");
            case MinDisConstraintProvider.EXPERIENCED_PRESENT -> Localization.lang("Experienced server present");
            case MinDisConstraintProvider.AGE_REQUIREMENT -> Localization.lang("Server age outside role range");
            default -> constraintName;
        };
    }
}
