package org.mindis.core.l10n;

import org.mindis.core.model.ServiceType;

/// Localized display names for domain enums (never show {@code name()}).
/// Roles are no longer an enum - they carry their own editable {@code name()}.
public final class EnumDisplay {

    private EnumDisplay() {
    }

    public static String of(ServiceType type) {
        return switch (type) {
            case SUNDAY_MASS -> Localization.lang("Sunday mass");
            case WEEKDAY_MASS -> Localization.lang("Weekday mass");
            case FEAST -> Localization.lang("Feast");
            case WEDDING -> Localization.lang("Wedding");
            case FUNERAL -> Localization.lang("Funeral");
            case OTHER -> Localization.lang("Other");
        };
    }
}
