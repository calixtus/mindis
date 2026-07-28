package org.mindis.core.persistence;

import java.time.DateTimeException;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.Month;
import java.time.MonthDay;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import org.mindis.core.model.LiturgicalDay;
import org.mindis.core.model.RecurrenceRule;

/// Text form of a {@link RecurrenceRule}, used wherever a rule has to fit into
/// a single field a human may edit - currently the CSV {@code recurrence}
/// column of {@link TemplateCsvMapper}. The JSON document uses Jackson's
/// polymorphic form instead; this one trades that verbosity for one readable
/// line:
///
/// ```
/// WEEKDAY:SUNDAY
/// DOM:13,-1
/// NTH:3/SUNDAY
/// MONTH:OCTOBER
/// MONTHDAY:12-24
/// DATE:2026-04-05
/// EVERY_WEEKS:2@2026-01-05
/// FEAST:EASTER
/// FEAST:EASTER-2
/// FEAST:ADVENT_1+0
/// ALL(WEEKDAY:SUNDAY; EVERY_WEEKS:2@2026-01-05)
/// ANY(DOM:13; NTH:-1/SATURDAY)
/// ALL(WEEKDAY:SUNDAY; NOT(MONTHDAY:12-25))
/// NEVER
/// ```
///
/// <p>Parsing is all-or-nothing per rule and returns {@code null} for anything
/// unreadable, including a group with one bad member: a partially understood
/// pattern would generate services on the wrong days, which is worse than the
/// row being skipped the way every other unparsable CSV row is.
@NullMarked
public final class RecurrenceCodec {

    private static final String NEVER = "NEVER";
    private static final String MEMBER_SEPARATOR = "; ";
    /// Bounds recursion on hand-edited or malicious input; far above what any
    /// rule a user can build in the editor needs.
    private static final int MAX_DEPTH = 16;

    private RecurrenceCodec() {
    }

    /// The rule as one line. Set members are emitted in calendar order so that
    /// re-exporting an unchanged document produces an unchanged file.
    public static String format(RecurrenceRule rule) {
        return switch (rule) {
            case RecurrenceRule.Weekday weekday -> "WEEKDAY:" + names(weekday.days(), DayOfWeek::name, DayOfWeek::getValue);
            case RecurrenceRule.DayOfMonth dayOfMonth -> "DOM:" + numbers(dayOfMonth.days());
            case RecurrenceRule.NthWeekdayOfMonth nth ->
                    "NTH:" + numbers(nth.ordinals()) + "/" + names(nth.days(), DayOfWeek::name, DayOfWeek::getValue);
            case RecurrenceRule.MonthOfYear month -> "MONTH:" + names(month.months(), Month::name, Month::getValue);
            case RecurrenceRule.FixedMonthDay fixed -> "MONTHDAY:%02d-%02d"
                    .formatted(fixed.monthDay().getMonthValue(), fixed.monthDay().getDayOfMonth());
            case RecurrenceRule.FixedDay fixed -> "DATE:" + fixed.date();
            case RecurrenceRule.EveryNDays every -> "EVERY_DAYS:" + every.n() + "@" + every.anchor();
            case RecurrenceRule.EveryNWeeks every -> "EVERY_WEEKS:" + every.n() + "@" + every.anchor();
            case RecurrenceRule.EveryNMonths every -> "EVERY_MONTHS:" + every.n() + "@" + every.anchor();
            case RecurrenceRule.FeastRelative feast -> "FEAST:" + feast.feast().name() + offset(feast.offsetDays());
            case RecurrenceRule.AllOf allOf -> "ALL(" + members(allOf.rules()) + ")";
            case RecurrenceRule.AnyOf anyOf -> "ANY(" + members(anyOf.rules()) + ")";
            case RecurrenceRule.Not not -> "NOT(" + format(not.rule()) + ")";
            case RecurrenceRule.Never ignored -> NEVER;
        };
    }

    /// The rule described by {@code text}, or {@code null} if it is blank or
    /// not understood.
    public static @Nullable RecurrenceRule parse(String text) {
        return parse(text, 0);
    }

    private static @Nullable RecurrenceRule parse(String text, int depth) {
        String trimmed = text.strip();
        if (trimmed.isEmpty() || depth > MAX_DEPTH) {
            return null;
        }
        String keyword = keywordOf(trimmed);
        if (NEVER.equals(keyword)) {
            return RecurrenceRule.NEVER;
        }
        int paren = trimmed.indexOf('(');
        if (paren >= 0 && trimmed.endsWith(")")) {
            String body = trimmed.substring(paren + 1, trimmed.length() - 1);
            return switch (keyword) {
                case "ALL" -> group(body, depth, RecurrenceRule.AllOf::new);
                case "ANY" -> group(body, depth, RecurrenceRule.AnyOf::new);
                case "NOT" -> negation(body, depth);
                default -> null;
            };
        }
        int colon = trimmed.indexOf(':');
        if (colon < 0) {
            return null;
        }
        String value = trimmed.substring(colon + 1).strip();
        return switch (keyword) {
            case "WEEKDAY" -> weekdays(value);
            case "DOM" -> dayOfMonth(value);
            case "NTH" -> nthWeekday(value);
            case "MONTH" -> months(value);
            case "MONTHDAY" -> fixedMonthDay(value);
            case "DATE" -> fixedDay(value);
            case "EVERY_DAYS" -> cycle(value, RecurrenceRule.EveryNDays::new);
            case "EVERY_WEEKS" -> cycle(value, RecurrenceRule.EveryNWeeks::new);
            case "EVERY_MONTHS" -> cycle(value, RecurrenceRule.EveryNMonths::new);
            case "FEAST" -> feastRelative(value);
            default -> null;
        };
    }

    /// The leading keyword: everything up to the first {@code :} or {@code (},
    /// upper-cased so that hand-written rules may be lower case.
    private static String keywordOf(String text) {
        int end = text.length();
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == ':' || c == '(') {
                end = i;
                break;
            }
        }
        return text.substring(0, end).strip().toUpperCase(Locale.ROOT);
    }

    private static @Nullable RecurrenceRule group(String body, int depth,
                                                  Function<List<RecurrenceRule>, RecurrenceRule> factory) {
        List<String> parts = splitMembers(body);
        if (parts.isEmpty()) {
            return null;
        }
        List<RecurrenceRule> rules = new ArrayList<>();
        for (String part : parts) {
            RecurrenceRule rule = parse(part, depth + 1);
            if (rule == null) {
                return null;
            }
            rules.add(rule);
        }
        return factory.apply(rules);
    }

    private static @Nullable RecurrenceRule negation(String body, int depth) {
        RecurrenceRule rule = parse(body, depth + 1);
        return rule == null ? null : new RecurrenceRule.Not(rule);
    }

    /// Splits a group body on {@code ;} at nesting depth zero, so that nested
    /// groups keep their own members.
    private static List<String> splitMembers(String body) {
        List<String> parts = new ArrayList<>();
        int depth = 0;
        int start = 0;
        for (int i = 0; i < body.length(); i++) {
            char c = body.charAt(i);
            if (c == '(') {
                depth++;
            } else if (c == ')') {
                depth--;
            } else if (c == ';' && depth == 0) {
                parts.add(body.substring(start, i));
                start = i + 1;
            }
        }
        parts.add(body.substring(start));
        return parts.stream().map(String::strip).filter(part -> !part.isEmpty()).toList();
    }

    private static @Nullable RecurrenceRule weekdays(String value) {
        Set<DayOfWeek> days = enumSet(value, RecurrenceCodec::dayOfWeek);
        return days == null ? null : new RecurrenceRule.Weekday(days);
    }

    private static @Nullable RecurrenceRule months(String value) {
        Set<Month> months = enumSet(value, RecurrenceCodec::month);
        return months == null ? null : new RecurrenceRule.MonthOfYear(months);
    }

    private static @Nullable RecurrenceRule dayOfMonth(String value) {
        Set<Integer> days = intSet(value);
        if (days == null || days.stream().anyMatch(day -> day == 0 || day < -31 || day > 31)) {
            return null;
        }
        return new RecurrenceRule.DayOfMonth(days);
    }

    private static @Nullable RecurrenceRule nthWeekday(String value) {
        int slash = value.indexOf('/');
        if (slash < 0) {
            return null;
        }
        Set<Integer> ordinals = intSet(value.substring(0, slash));
        Set<DayOfWeek> days = enumSet(value.substring(slash + 1), RecurrenceCodec::dayOfWeek);
        if (ordinals == null || days == null
                || ordinals.stream().anyMatch(ordinal -> ordinal == 0 || ordinal < -5 || ordinal > 5)) {
            return null;
        }
        return new RecurrenceRule.NthWeekdayOfMonth(ordinals, days);
    }

    private static @Nullable RecurrenceRule fixedMonthDay(String value) {
        String[] parts = value.split("-");
        if (parts.length != 2) {
            return null;
        }
        Integer month = CsvFields.parseInt(parts[0].strip());
        Integer day = CsvFields.parseInt(parts[1].strip());
        if (month == null || day == null) {
            return null;
        }
        try {
            return new RecurrenceRule.FixedMonthDay(MonthDay.of(month, day));
        } catch (DateTimeException e) {
            return null;
        }
    }

    /// {@code FEAST_NAME}, or {@code FEAST_NAME+n} / {@code FEAST_NAME-n}. The
    /// sign is looked for from the right so that a feast name may contain the
    /// underscore-separated digits of {@code ADVENT_1}.
    private static @Nullable RecurrenceRule feastRelative(String value) {
        int sign = Math.max(value.lastIndexOf('+'), value.lastIndexOf('-'));
        String name = sign < 0 ? value : value.substring(0, sign).strip();
        int offsetDays = 0;
        if (sign >= 0) {
            Integer parsed = CsvFields.parseInt(value.substring(sign).strip().replace("+", ""));
            if (parsed == null) {
                return null;
            }
            offsetDays = parsed;
        }
        LiturgicalDay feast = liturgicalDay(name);
        return feast == null ? null : new RecurrenceRule.FeastRelative(feast, offsetDays);
    }

    private static @Nullable RecurrenceRule fixedDay(String value) {
        LocalDate date = CsvFields.parseDate(value);
        return date == null ? null : new RecurrenceRule.FixedDay(date);
    }

    /// {@code n@anchor}, e.g. {@code 2@2026-01-05}.
    private static @Nullable RecurrenceRule cycle(String value, CycleFactory factory) {
        int at = value.indexOf('@');
        if (at < 0) {
            return null;
        }
        Integer n = CsvFields.parseInt(value.substring(0, at).strip());
        LocalDate anchor = CsvFields.parseDate(value.substring(at + 1).strip());
        if (n == null || n < 1 || anchor == null) {
            return null;
        }
        return factory.create(n, anchor);
    }

    private static <T> @Nullable Set<T> enumSet(String value, Function<String, @Nullable T> parser) {
        Set<T> values = new LinkedHashSet<>();
        for (String part : value.split(",")) {
            String name = part.strip();
            if (name.isEmpty()) {
                continue;
            }
            T parsed = parser.apply(name);
            if (parsed == null) {
                return null;
            }
            values.add(parsed);
        }
        return values.isEmpty() ? null : values;
    }

    private static @Nullable Set<Integer> intSet(String value) {
        Set<Integer> numbers = new LinkedHashSet<>();
        for (String part : value.split(",")) {
            String text = part.strip();
            if (text.isEmpty()) {
                continue;
            }
            Integer parsed = CsvFields.parseInt(text);
            if (parsed == null) {
                return null;
            }
            numbers.add(parsed);
        }
        return numbers.isEmpty() ? null : numbers;
    }

    private static @Nullable DayOfWeek dayOfWeek(String name) {
        return CsvFields.parseDayOfWeek(name);
    }

    private static @Nullable LiturgicalDay liturgicalDay(String name) {
        try {
            return LiturgicalDay.valueOf(name.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private static String offset(int offsetDays) {
        return offsetDays == 0 ? "" : (offsetDays > 0 ? "+" : "") + offsetDays;
    }

    private static @Nullable Month month(String name) {
        try {
            return Month.valueOf(name.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private static <T> String names(Set<T> values, Function<T, String> name, Function<T, Integer> order) {
        return values.stream()
                .sorted(Comparator.comparing(order))
                .map(name)
                .collect(Collectors.joining(","));
    }

    private static String numbers(Set<Integer> values) {
        return values.stream().sorted().map(String::valueOf).collect(Collectors.joining(","));
    }

    private static String members(List<RecurrenceRule> rules) {
        return rules.stream().map(RecurrenceCodec::format).collect(Collectors.joining(MEMBER_SEPARATOR));
    }

    /// Constructor reference shape shared by the three {@code EVERY_*} rules.
    @FunctionalInterface
    private interface CycleFactory {
        RecurrenceRule create(int n, LocalDate anchor);
    }
}
