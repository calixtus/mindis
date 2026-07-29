package org.mindis.core.model;

import java.time.LocalDate;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

import org.jspecify.annotations.Nullable;

/// When a {@link ServiceTemplate}'s services happen: the {@link RecurrenceRule}
/// pattern, the window it applies in, and the individual dates dropped from it.
///
/// <p>The three are separate on purpose. A rule answers "which days does this
/// pattern name?" and stays a pure, reusable predicate; a window ("this
/// template starts in September", "no services here after the parish merger")
/// and single cancellations ("no mass on 2 August this year") are facts about
/// one template, not about the pattern. Encoding them into the rule instead -
/// as a `Not(FixedDay(...))` per cancellation - would work but would make the
/// pattern unreadable and unrepresentable in the editor's guided modes.
public record ServiceSchedule(
        RecurrenceRule rule,
        @Nullable LocalDate validFrom,
        @Nullable LocalDate validUntil,
        Set<LocalDate> skipDates) {

    public ServiceSchedule {
        // Null-tolerant like the other model records: a hand-edited document
        // reads as "generates nothing" rather than failing the whole open.
        if (rule == null) {
            rule = RecurrenceRule.NEVER;
        }
        // Sorted, so that saving an unchanged document produces an unchanged
        // file - a hash-ordered set would reshuffle between JVM runs.
        skipDates = skipDates == null
                ? Set.of()
                : Collections.unmodifiableSet(new TreeSet<>(skipDates));
    }

    /// A pattern with no window and no cancellations - what a new template gets.
    public static ServiceSchedule of(RecurrenceRule rule) {
        return new ServiceSchedule(rule, null, null, Set.of());
    }

    /// Whether a service happens on {@code date}: the rule matches, the date
    /// lies inside the window (both bounds inclusive), and it is not one of
    /// the skipped dates.
    public boolean occursOn(LocalDate date) {
        if (validFrom != null && date.isBefore(validFrom)) {
            return false;
        }
        if (validUntil != null && date.isAfter(validUntil)) {
            return false;
        }
        return !skipDates.contains(date) && rule.matches(date);
    }

    /// The first {@code limit} dates this schedule produces on or after
    /// {@code from}, for the editor's preview - the window and the skipped
    /// dates included, so the preview shows what would actually be generated.
    public List<LocalDate> nextOccurrences(LocalDate from, int limit) {
        LocalDate start = validFrom == null || validFrom.isBefore(from) ? from : validFrom;
        LocalDate end = start.plusYears(RecurrenceRule.SEARCH_HORIZON_YEARS);
        if (validUntil != null && validUntil.isBefore(end)) {
            end = validUntil.plusDays(1);
        }
        if (!start.isBefore(end)) {
            return List.of();
        }
        return start.datesUntil(end).filter(this::occursOn).limit(limit).toList();
    }

    public ServiceSchedule withRule(RecurrenceRule newRule) {
        return new ServiceSchedule(newRule, validFrom, validUntil, skipDates);
    }

    public ServiceSchedule withWindow(@Nullable LocalDate newValidFrom, @Nullable LocalDate newValidUntil) {
        return new ServiceSchedule(rule, newValidFrom, newValidUntil, skipDates);
    }

    public ServiceSchedule withSkipDates(Set<LocalDate> newSkipDates) {
        return new ServiceSchedule(rule, validFrom, validUntil, newSkipDates);
    }
}
