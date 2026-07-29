package org.mindis.core.model;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.Month;
import java.time.MonthDay;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Set;

/// The date pattern of a {@link ServiceTemplate}: a pure predicate over
/// calendar dates. It answers "does a service occur on this day?" and nothing
/// else - time, location and role slots stay on the template.
///
/// <p>The vocabulary is deliberately small and combined with {@link AllOf},
/// {@link AnyOf} and {@link Not} rather than grown by special cases:
/// <ul>
///   <li>"every third Sunday" = {@code allOf(nthWeekdayOfMonth(3, SUNDAY))} -
///       the ordinal rule already carries the weekday</li>
///   <li>"every other Sunday" = {@code allOf(weekly(SUNDAY), everyNWeeks(2, anchor))}</li>
///   <li>"the 13th, and every Sunday" = {@code anyOf(dayOfMonth(13), weekly(SUNDAY))}</li>
///   <li>"every Sunday except Christmas" =
///       {@code allOf(weekly(SUNDAY), not(fixedMonthDay(DECEMBER, 25)))}</li>
///   <li>"the Saturday before the first Sunday of Advent" =
///       {@code feast(ADVENT_1, -1)}</li>
/// </ul>
///
/// <p>Feast-relative rules need no calendar to be passed in: every liturgical
/// date is a pure function of the year (see {@link LiturgicalCalendar}), so
/// {@link FeastRelative} stays a date predicate like every other rule.
///
/// <p>Implementations are immutable records and compare by value, which is
/// what the GUI's dirty tracking and the document round trip rely on. The two
/// serialized forms both live in the persistence package rather than here:
/// {@code RecurrenceRuleMixin} for the JSON document, {@code RecurrenceCodec}
/// for the one-line CSV form.
public sealed interface RecurrenceRule {

    /// Matches nothing - the neutral default for a template whose pattern is
    /// missing or unreadable, so that a broken rule generates no services
    /// instead of throwing during generation.
    RecurrenceRule NEVER = new Never();

    /// How far {@link #nextOccurrences} looks ahead before giving up. A rule
    /// may match nothing at all ({@link Never}, or a combination that cancels
    /// itself out), so the search has to be bounded; ten years is far beyond
    /// any planning horizon a preview needs.
    int SEARCH_HORIZON_YEARS = 10;

    /// True if a service occurs on {@code date}. Must be side-effect free and
    /// depend on nothing but the date itself.
    boolean matches(LocalDate date);

    /// The first {@code limit} dates this rule matches on or after
    /// {@code from}, for a preview of what a template will generate. Fewer
    /// than {@code limit} dates - possibly none - if the rule runs out within
    /// {@link #SEARCH_HORIZON_YEARS}.
    default List<LocalDate> nextOccurrences(LocalDate from, int limit) {
        return from.datesUntil(from.plusYears(SEARCH_HORIZON_YEARS))
                .filter(this::matches)
                .limit(limit)
                .toList();
    }

    static RecurrenceRule weekly(DayOfWeek... days) {
        return new Weekday(Set.of(days));
    }

    static RecurrenceRule dayOfMonth(Integer... days) {
        return new DayOfMonth(Set.of(days));
    }

    /// "The n-th {@code day} of the month", n counted from the month's end
    /// when negative ({@code -1} = last).
    static RecurrenceRule nthWeekdayOfMonth(int ordinal, DayOfWeek day) {
        return new NthWeekdayOfMonth(Set.of(ordinal), Set.of(day));
    }

    static RecurrenceRule inMonths(Month... months) {
        return new MonthOfYear(Set.of(months));
    }

    static RecurrenceRule fixedMonthDay(Month month, int day) {
        return new FixedMonthDay(MonthDay.of(month, day));
    }

    /// A feast day itself: {@code feast(EASTER, 0)}, or shifted by whole days:
    /// {@code feast(EASTER, -2)} is Good Friday.
    static RecurrenceRule feast(LiturgicalDay feast, int offsetDays) {
        return new FeastRelative(feast, offsetDays);
    }

    static RecurrenceRule allOf(RecurrenceRule... rules) {
        return new AllOf(List.of(rules));
    }

    static RecurrenceRule anyOf(RecurrenceRule... rules) {
        return new AnyOf(List.of(rules));
    }

    static RecurrenceRule not(RecurrenceRule rule) {
        return new Not(rule);
    }

    /// Every occurrence of the given weekdays: "every Monday and Tuesday".
    record Weekday(Set<DayOfWeek> days) implements RecurrenceRule {

        public Weekday {
            days = Set.copyOf(days);
            if (days.isEmpty()) {
                throw new IllegalArgumentException("weekday rule needs at least one day");
            }
        }

        @Override
        public boolean matches(LocalDate date) {
            return days.contains(date.getDayOfWeek());
        }
    }

    /// Days of the month by number: "the 13th". Negative numbers count from
    /// the month's end, {@code -1} being its last day. A number that a given
    /// month does not have (the 31st of February) simply does not occur -
    /// dates are never clamped into the previous day, because a service
    /// silently moving to the 28th would be worse than none at all.
    record DayOfMonth(Set<Integer> days) implements RecurrenceRule {

        public DayOfMonth {
            days = Set.copyOf(days);
            if (days.isEmpty()) {
                throw new IllegalArgumentException("day-of-month rule needs at least one day");
            }
            for (int day : days) {
                if (day == 0 || day < -31 || day > 31) {
                    throw new IllegalArgumentException("day of month out of range: " + day);
                }
            }
        }

        @Override
        public boolean matches(LocalDate date) {
            int day = date.getDayOfMonth();
            return days.contains(day) || days.contains(day - date.lengthOfMonth() - 1);
        }
    }

    /// "The n-th weekday of the month": third Sunday, last Saturday. Ordinals
    /// are 1..5 from the start of the month and -1..-5 from its end.
    record NthWeekdayOfMonth(Set<Integer> ordinals, Set<DayOfWeek> days) implements RecurrenceRule {

        public NthWeekdayOfMonth {
            ordinals = Set.copyOf(ordinals);
            days = Set.copyOf(days);
            if (ordinals.isEmpty() || days.isEmpty()) {
                throw new IllegalArgumentException("nth-weekday rule needs at least one ordinal and one day");
            }
            for (int ordinal : ordinals) {
                if (ordinal == 0 || ordinal < -5 || ordinal > 5) {
                    throw new IllegalArgumentException("weekday ordinal out of range: " + ordinal);
                }
            }
        }

        @Override
        public boolean matches(LocalDate date) {
            if (!days.contains(date.getDayOfWeek())) {
                return false;
            }
            int fromStart = (date.getDayOfMonth() - 1) / 7 + 1;
            int fromEnd = -((date.lengthOfMonth() - date.getDayOfMonth()) / 7 + 1);
            return ordinals.contains(fromStart) || ordinals.contains(fromEnd);
        }
    }

    /// A month filter, only useful in combination: an {@link AllOf} of this
    /// and a monthly rule is what makes a rule yearly ("third Sunday in
    /// October").
    record MonthOfYear(Set<Month> months) implements RecurrenceRule {

        public MonthOfYear {
            months = Set.copyOf(months);
            if (months.isEmpty()) {
                throw new IllegalArgumentException("month rule needs at least one month");
            }
        }

        @Override
        public boolean matches(LocalDate date) {
            return months.contains(date.getMonth());
        }
    }

    /// The same calendar day every year: 24 December. 29 February occurs in
    /// leap years only, for the same reason {@link DayOfMonth} does not clamp.
    record FixedMonthDay(MonthDay monthDay) implements RecurrenceRule {

        @Override
        public boolean matches(LocalDate date) {
            return monthDay.equals(MonthDay.from(date));
        }
    }

    /// One single date - a one-off service kept as a template because it
    /// carries the same role slots.
    record FixedDay(LocalDate date) implements RecurrenceRule {

        @Override
        public boolean matches(LocalDate date) {
            return this.date.equals(date);
        }
    }

    /// Every {@code n}-th day counted from {@code anchor}, in both directions
    /// (dates before the anchor are on the cycle too, which keeps a rule
    /// stable when the user later moves the anchor's year).
    record EveryNDays(int n, LocalDate anchor) implements RecurrenceRule {

        public EveryNDays {
            requireCycle(n);
        }

        @Override
        public boolean matches(LocalDate date) {
            return Math.floorMod(ChronoUnit.DAYS.between(anchor, date), n) == 0;
        }
    }

    /// Every {@code n}-th week counted from the anchor's week - "every other
    /// week". Weeks are ISO weeks (Monday-based), so the cycle is a property
    /// of the week, not of the weekday the occurrence falls on; combine with
    /// {@link Weekday} to pick the day.
    record EveryNWeeks(int n, LocalDate anchor) implements RecurrenceRule {

        public EveryNWeeks {
            requireCycle(n);
        }

        @Override
        public boolean matches(LocalDate date) {
            long weeks = ChronoUnit.WEEKS.between(weekStart(anchor), weekStart(date));
            return Math.floorMod(weeks, n) == 0;
        }

        private static LocalDate weekStart(LocalDate date) {
            return date.with(DayOfWeek.MONDAY);
        }
    }

    /// Every {@code n}-th month counted from the anchor's month - quarterly,
    /// half-yearly. Combine with {@link DayOfMonth} or
    /// {@link NthWeekdayOfMonth} to pick the day within the month.
    record EveryNMonths(int n, LocalDate anchor) implements RecurrenceRule {

        public EveryNMonths {
            requireCycle(n);
        }

        @Override
        public boolean matches(LocalDate date) {
            long months = ChronoUnit.MONTHS.between(anchor.withDayOfMonth(1), date.withDayOfMonth(1));
            return Math.floorMod(months, n) == 0;
        }
    }

    /// A feast day, optionally shifted: Easter itself, Easter -2 (Good
    /// Friday's own constant exists, but a parish may prefer counting), the
    /// Saturday before the first Sunday of Advent ({@code ADVENT_1 - 1}).
    ///
    /// <p>A shift can carry the date into the neighbouring year (Christmas +8
    /// lands in January), so matching probes the adjacent years too rather
    /// than assuming the feast and its occurrence share a year.
    record FeastRelative(LiturgicalDay feast, int offsetDays) implements RecurrenceRule {

        @Override
        public boolean matches(LocalDate date) {
            int year = date.getYear();
            return occursOn(date, year - 1) || occursOn(date, year) || occursOn(date, year + 1);
        }

        private boolean occursOn(LocalDate date, int feastYear) {
            return feast.dateIn(feastYear).plusDays(offsetDays).equals(date);
        }
    }

    /// Conjunction: every part must match. "Third Sunday in October".
    record AllOf(List<RecurrenceRule> rules) implements RecurrenceRule {

        public AllOf {
            rules = List.copyOf(rules);
            if (rules.isEmpty()) {
                throw new IllegalArgumentException("allOf needs at least one rule");
            }
        }

        @Override
        public boolean matches(LocalDate date) {
            return rules.stream().allMatch(rule -> rule.matches(date));
        }
    }

    /// Disjunction: any part matching is enough. "The 13th, or any Sunday".
    record AnyOf(List<RecurrenceRule> rules) implements RecurrenceRule {

        public AnyOf {
            rules = List.copyOf(rules);
            if (rules.isEmpty()) {
                throw new IllegalArgumentException("anyOf needs at least one rule");
            }
        }

        @Override
        public boolean matches(LocalDate date) {
            return rules.stream().anyMatch(rule -> rule.matches(date));
        }
    }

    /// Negation, used inside an {@link AllOf} as an exception: "every Sunday
    /// but not Easter Sunday".
    record Not(RecurrenceRule rule) implements RecurrenceRule {

        @Override
        public boolean matches(LocalDate date) {
            return !rule.matches(date);
        }
    }

    /// The empty pattern - see {@link #NEVER}.
    record Never() implements RecurrenceRule {

        @Override
        public boolean matches(LocalDate date) {
            return false;
        }
    }

    private static void requireCycle(int n) {
        if (n < 1) {
            throw new IllegalArgumentException("cycle length must be positive: " + n);
        }
    }
}
