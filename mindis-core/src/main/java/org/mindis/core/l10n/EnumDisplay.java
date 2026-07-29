package org.mindis.core.l10n;

import org.mindis.core.model.LiturgicalDay;
import org.mindis.core.model.ServiceType;

/// Localized display names for domain enums (never show {@code name()}).
/// Roles are no longer an enum - they carry their own editable {@code name()}.
public final class EnumDisplay {

    private EnumDisplay() {
    }

    public static String of(LiturgicalDay day) {
        return switch (day) {
            case ASH_WEDNESDAY -> Localization.lang("Ash Wednesday");
            case PALM_SUNDAY -> Localization.lang("Palm Sunday");
            case MAUNDY_THURSDAY -> Localization.lang("Maundy Thursday");
            case GOOD_FRIDAY -> Localization.lang("Good Friday");
            case HOLY_SATURDAY -> Localization.lang("Holy Saturday");
            case EASTER -> Localization.lang("Easter Sunday");
            case EASTER_MONDAY -> Localization.lang("Easter Monday");
            case ASCENSION -> Localization.lang("Ascension");
            case PENTECOST -> Localization.lang("Pentecost");
            case WHIT_MONDAY -> Localization.lang("Whit Monday");
            case TRINITY_SUNDAY -> Localization.lang("Trinity Sunday");
            case CORPUS_CHRISTI -> Localization.lang("Corpus Christi");
            case ADVENT_1 -> Localization.lang("First Sunday of Advent");
            case ADVENT_2 -> Localization.lang("Second Sunday of Advent");
            case ADVENT_3 -> Localization.lang("Third Sunday of Advent");
            case ADVENT_4 -> Localization.lang("Fourth Sunday of Advent");
            case CHRIST_THE_KING -> Localization.lang("Christ the King");
            case CHRISTMAS_EVE -> Localization.lang("Christmas Eve");
            case CHRISTMAS -> Localization.lang("Christmas Day");
            case ST_STEPHEN -> Localization.lang("Saint Stephen's Day");
            case NEW_YEAR -> Localization.lang("New Year's Day");
            case EPIPHANY -> Localization.lang("Epiphany");
            case CANDLEMAS -> Localization.lang("Candlemas");
            case ANNUNCIATION -> Localization.lang("Annunciation");
            case ASSUMPTION -> Localization.lang("Assumption of Mary");
            case ALL_SAINTS -> Localization.lang("All Saints' Day");
            case ALL_SOULS -> Localization.lang("All Souls' Day");
            case IMMACULATE_CONCEPTION -> Localization.lang("Immaculate Conception");
            case HARVEST_THANKSGIVING -> Localization.lang("Harvest Thanksgiving");
            case REPENTANCE_DAY -> Localization.lang("Day of Repentance and Prayer");
            case ETERNITY_SUNDAY -> Localization.lang("Sunday of the Dead");
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
