package org.mindis.core.l10n;

import java.time.DayOfWeek;
import java.time.Month;
import java.time.format.TextStyle;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.mindis.core.model.RecurrenceRule;

/// Localized one-line descriptions of a {@link RecurrenceRule} - "Every third
/// Sunday of the month", "Easter Sunday minus 2 days" - for the templates
/// table and the recurrence editor's summary.
///
/// <p>The counterpart of {@code RecurrenceCodec}: that one writes the form a
/// rule is stored in, this one the form it is read in. Composites are
/// described by describing their parts, so an arbitrarily nested rule still
/// produces a sentence rather than falling back to raw syntax.
public final class RecurrenceText {

    private RecurrenceText() {
    }

    public static String describe(RecurrenceRule rule) {
        return switch (rule) {
            case RecurrenceRule.Weekday weekday -> Localization.lang("Every %0", weekdays(weekday.days()));
            case RecurrenceRule.DayOfMonth dayOfMonth -> Localization.lang("Day %0 of the month",
                    join(dayOfMonth.days().stream()
                            .sorted(Comparator.comparingInt(RecurrenceText::calendarOrder))
                            .map(RecurrenceText::dayOfMonth)
                            .toList()));
            case RecurrenceRule.NthWeekdayOfMonth nth -> Localization.lang("The %0 %1 of the month",
                    join(nth.ordinals().stream()
                            .sorted(Comparator.comparingInt(RecurrenceText::calendarOrder))
                            .map(RecurrenceText::ordinal)
                            .toList()),
                    weekdays(nth.days()));
            case RecurrenceRule.MonthOfYear month -> Localization.lang("In %0", months(month.months()));
            case RecurrenceRule.FixedMonthDay fixed -> Localization.lang("Every year on %0 %1",
                    fixed.monthDay().getDayOfMonth(), monthName(fixed.monthDay().getMonth()));
            case RecurrenceRule.FixedDay fixed -> Localization.lang("On %0", fixed.date());
            case RecurrenceRule.EveryNDays every -> every.n() == 1
                    ? Localization.lang("Every day")
                    : Localization.lang("Every %0 days from %1", every.n(), every.anchor());
            case RecurrenceRule.EveryNWeeks every -> switch (every.n()) {
                case 1 -> Localization.lang("Every week");
                case 2 -> Localization.lang("Every other week from %0", every.anchor());
                default -> Localization.lang("Every %0 weeks from %1", every.n(), every.anchor());
            };
            case RecurrenceRule.EveryNMonths every -> every.n() == 1
                    ? Localization.lang("Every month")
                    : Localization.lang("Every %0 months from %1", every.n(), every.anchor());
            case RecurrenceRule.FeastRelative feast -> feast(feast);
            case RecurrenceRule.AllOf allOf -> allOf.rules().stream()
                    .map(RecurrenceText::describe)
                    .collect(Collectors.joining(", "));
            case RecurrenceRule.AnyOf anyOf -> join(anyOf.rules().stream().map(RecurrenceText::describe).toList(),
                    Localization.lang("or"));
            case RecurrenceRule.Not not -> Localization.lang("except %0", describe(not.rule()));
            case RecurrenceRule.Never ignored -> Localization.lang("No dates");
        };
    }

    private static String feast(RecurrenceRule.FeastRelative rule) {
        String name = EnumDisplay.of(rule.feast());
        if (rule.offsetDays() == 0) {
            return name;
        }
        return rule.offsetDays() > 0
                ? Localization.lang("%0 plus %1 days", name, rule.offsetDays())
                : Localization.lang("%0 minus %1 days", name, -rule.offsetDays());
    }

    /// A day number as the editor lets one enter it: a plain number, or the
    /// month's end counted backwards.
    private static String dayOfMonth(int day) {
        if (day > 0) {
            return String.valueOf(day);
        }
        return day == -1
                ? Localization.lang("last day")
                : Localization.lang("%0 days before the month's end", -day - 1);
    }

    /// Sorts day and ordinal numbers the way they fall in a month: the ones
    /// counted from its start ascending, then the ones counted from its end,
    /// rather than -1 sorting ahead of 13 as a plain number would.
    private static int calendarOrder(int day) {
        return day > 0 ? day : 100 - day;
    }

    /// "first", "third", "last" - also what the editor labels its ordinal
    /// choices with, so the two never drift apart.
    public static String ordinal(int ordinal) {
        return switch (ordinal) {
            case 1 -> Localization.lang("first");
            case 2 -> Localization.lang("second");
            case 3 -> Localization.lang("third");
            case 4 -> Localization.lang("fourth");
            case 5 -> Localization.lang("fifth");
            case -1 -> Localization.lang("last");
            case -2 -> Localization.lang("second to last");
            default -> String.valueOf(ordinal);
        };
    }

    private static String weekdays(Set<DayOfWeek> days) {
        return join(sortedNames(days, DayOfWeek::getValue, RecurrenceText::weekdayName));
    }

    private static String months(Set<Month> months) {
        return join(sortedNames(months, Month::getValue, RecurrenceText::monthName));
    }

    private static String weekdayName(DayOfWeek day) {
        return day.getDisplayName(TextStyle.FULL, Locale.getDefault());
    }

    private static String monthName(Month month) {
        return month.getDisplayName(TextStyle.FULL, Locale.getDefault());
    }

    private static <T> List<String> sortedNames(Set<T> values, Function<T, Integer> order, Function<T, String> name) {
        return values.stream().sorted(Comparator.comparing(order)).map(name).toList();
    }

    private static String join(List<String> parts) {
        return join(parts, Localization.lang("and"));
    }

    /// "a", "a and b", "a, b and c" - the last two parts joined by the given
    /// word, the rest by commas.
    private static String join(List<String> parts, String lastSeparator) {
        if (parts.size() < 2) {
            return parts.isEmpty() ? "" : parts.getFirst();
        }
        String head = String.join(", ", parts.subList(0, parts.size() - 1));
        return head + " " + lastSeparator + " " + parts.getLast();
    }
}
