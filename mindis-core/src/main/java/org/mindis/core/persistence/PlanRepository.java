package org.mindis.core.persistence;

import com.fasterxml.jackson.core.type.TypeReference;

import jakarta.inject.Singleton;

import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

import org.jspecify.annotations.Nullable;

import org.mindis.core.planning.AcceptedPlan;
import org.mindis.core.preferences.DataDirectory;

/// Stores every accepted plan, one per distinct ({@code from}, {@code
/// toInclusive}) period, as plan.json in the user data directory. At most one
/// stored plan is "open" ({@link AcceptedPlan#archived()} {@code == false})
/// at a time - the period the planner is still actively working on; every
/// other stored plan is archived - frozen the moment {@link
/// #applyArchiveSplit} sets the flag, from then on only ever read, never
/// replaced by {@link #save}. Saving the open plan again (e.g. after an
/// Autofill run extends its bounds) replaces whichever plan is currently
/// open, even if its {@code (from, toInclusive)} differs from the previous
/// save, or an archived entry for the exact same period (kept for parity
/// with the historical "save into a past period" behavior); every other
/// stored plan is kept untouched.
///
/// <p><b>Upgrade note:</b> {@code archived}/{@code archivedAt} did not exist
/// before this class derived "active"/"archived" purely from {@code
/// toInclusive} ordering (the newest period was active, everything else was
/// archived by definition). A {@code plan.json} written before the fields
/// existed deserializes every entry as {@code archived == false} (see {@link
/// AcceptedPlan} docs), which would otherwise silently drop every
/// pre-upgrade period except the newest one from {@link #listArchived()}. To
/// avoid orphaning that history, {@link #normalizeArchivedFlags} runs once at
/// construction: if more than one entry reads unarchived, every one but the
/// latest-{@code toInclusive} is flipped to archived and persisted. Running
/// it in the constructor (not on every read) keeps {@link #load},
/// {@link #listArchived} and {@link #allOverlapping} pure reads, off the hot
/// path of the Services tab's table refresh. This is a deliberate, narrow
/// exception to the usual "just add a nullable field" schema convention used
/// everywhere else in this codebase - it earns its keep by keeping old
/// periods browsable across the upgrade rather than requiring a real
/// migration engine for everything else too.
@Singleton
public class PlanRepository {

    private final JsonStore<AcceptedPlan> store;

    public PlanRepository(DataDirectory dataDirectory) {
        this(dataDirectory.resolve("plan.json"));
    }

    PlanRepository(Path file) {
        this.store = new JsonStore<>(file, new TypeReference<>() {
        });
        normalizeArchivedFlags();
    }

    /// The open plan (not archived), if any - the period the planner is
    /// still actively working on. The model intends at most one; the
    /// latest-{@code toInclusive} tie-break is defensive rather than load-bearing.
    public synchronized Optional<AcceptedPlan> load() {
        return store.load().stream()
                .filter(plan -> !plan.archived())
                .max(Comparator.comparing(AcceptedPlan::toInclusive));
    }

    /// Every archived stored plan, newest period first - for browsing plans
    /// from periods the planner has since moved past and frozen via {@link
    /// #applyArchiveSplit}.
    public synchronized List<AcceptedPlan> listArchived() {
        return store.load().stream()
                .filter(AcceptedPlan::archived)
                .sorted(Comparator.comparing(AcceptedPlan::toInclusive).reversed())
                .toList();
    }

    /// Saves {@code plan} as the open plan, stamping {@link
    /// AcceptedPlan#savedAt()} with the current time regardless of what
    /// {@code plan} already carried there, and forcing {@code archived =
    /// false} regardless of what the caller passed (this method only ever
    /// writes the open plan - see {@link #applyArchiveSplit} for freezing
    /// one).
    public synchronized void save(AcceptedPlan plan) {
        List<AcceptedPlan> all = new ArrayList<>(store.load());
        all.removeIf(existing -> isSamePeriod(existing, plan) || !existing.archived());
        all.add(new AcceptedPlan(plan.from(), plan.toInclusive(), plan.assignments(), Instant.now(), false, null));
        store.save(all);
    }

    /// Atomically freezes the open plan up to (and including) {@code
    /// archivedPortion} toInclusive: removes whichever plan is currently
    /// open, adds {@code archivedPortion} stamped {@code archived = true}/
    /// {@code archivedAt = now}, and adds {@code remainder} (if non-null) as
    /// the new open plan. One {@link JsonStore#save} call, not two, so a
    /// crash mid-operation can never leave zero or two open plans on disk -
    /// callers (see {@code PlanArchiver#split}) are expected to have already
    /// partitioned the open plan assignments between the two.
    public synchronized void applyArchiveSplit(AcceptedPlan archivedPortion, @Nullable AcceptedPlan remainder) {
        List<AcceptedPlan> all = new ArrayList<>(store.load());
        all.removeIf(existing -> !existing.archived());
        Instant now = Instant.now();
        all.add(new AcceptedPlan(archivedPortion.from(), archivedPortion.toInclusive(),
                archivedPortion.assignments(), now, true, now));
        if (remainder != null) {
            all.add(new AcceptedPlan(remainder.from(), remainder.toInclusive(),
                    remainder.assignments(), null, false, null));
        }
        store.save(all);
    }

    /// The stored plan with the latest {@code toInclusive} strictly before
    /// {@code date}, if any - used by {@link
    /// org.mindis.core.planning.PlanningService} to seed {@link
    /// org.mindis.core.planning.PriorAssignment} facts so the solver can see
    /// across a plan boundary. Deliberately ignores {@link
    /// AcceptedPlan#archived()} - archiving only freezes a period against
    /// further edits, it is not a solver-visibility filter, so spacing
    /// continuity must keep working across the archive boundary the same way
    /// it always has.
    public synchronized Optional<AcceptedPlan> mostRecentBefore(LocalDate date) {
        return store.load().stream()
                .filter(plan -> plan.toInclusive().isBefore(date))
                .max(Comparator.comparing(AcceptedPlan::toInclusive));
    }

    /// Every stored plan (open or archived) whose period intersects {@code
    /// [from, toInclusive]} - used by an Autofill run to re-apply every
    /// previously-decided value (including archived ones) onto a freshly
    /// built problem before pinning, since the resolved autofill window can
    /// span both the open plan and one or more archived plans.
    public synchronized List<AcceptedPlan> allOverlapping(LocalDate from, LocalDate toInclusive) {
        return store.load().stream()
                .filter(plan -> !plan.toInclusive().isBefore(from) && !plan.from().isAfter(toInclusive))
                .toList();
    }

    /// One-time, idempotent fix-up for {@code plan.json} files written
    /// before {@code archived}/{@code archivedAt} existed (see class docs):
    /// if more than one stored plan reads {@code archived == false}, every
    /// one but the latest-{@code toInclusive} is flipped to archived (stamped
    /// {@code archivedAt = savedAt}) and persisted once. A no-op once at most
    /// one unarchived entry remains, which every later save/archive
    /// maintains - so this only ever does real work on the first launch
    /// after upgrade.
    private void normalizeArchivedFlags() {
        List<AcceptedPlan> all = store.load();
        List<AcceptedPlan> unarchived = all.stream().filter(plan -> !plan.archived()).toList();
        if (unarchived.size() <= 1) {
            return;
        }
        AcceptedPlan stillOpen = unarchived.stream()
                .max(Comparator.comparing(AcceptedPlan::toInclusive))
                .orElseThrow();
        List<AcceptedPlan> normalized = all.stream()
                .map(plan -> plan == stillOpen || plan.archived()
                        ? plan
                        : new AcceptedPlan(plan.from(), plan.toInclusive(), plan.assignments(),
                                plan.savedAt(), true, plan.savedAt()))
                .toList();
        store.save(normalized);
    }

    private static boolean isSamePeriod(AcceptedPlan a, AcceptedPlan b) {
        return a.from().equals(b.from()) && a.toInclusive().equals(b.toInclusive());
    }
}
