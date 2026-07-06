package org.mindis.core.l10n;

import org.mindis.core.model.Role;
import org.mindis.core.model.ServiceType;

/**
 * Localized display names for domain enums (never show {@code name()}).
 */
public final class EnumDisplay {

    private EnumDisplay() {
    }

    public static String of(Role role) {
        return switch (role) {
            case ACOLYTE -> Localization.lang("Acolyte");
            case CROSS_BEARER -> Localization.lang("Cross bearer");
            case THURIFER -> Localization.lang("Thurifer");
            case BOAT_BEARER -> Localization.lang("Boat bearer");
            case MASTER_OF_CEREMONIES -> Localization.lang("Master of ceremonies");
        };
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
