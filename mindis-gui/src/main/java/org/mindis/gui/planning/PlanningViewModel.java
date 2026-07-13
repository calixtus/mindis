package org.mindis.gui.planning;

import ai.timefold.solver.core.api.score.HardMediumSoftScore;

import java.nio.file.Path;
import java.time.Duration;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;

import javafx.application.Platform;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.ReadOnlyBooleanProperty;
import javafx.beans.property.ReadOnlyBooleanWrapper;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;

import org.jspecify.annotations.Nullable;

import org.mindis.core.export.PlanExportFormat;
import org.mindis.core.export.PlanExportService;
import org.mindis.core.model.LiturgicalService;
import org.mindis.core.model.Server;
import org.mindis.core.persistence.PlanRepository;
import org.mindis.core.planning.AcceptedPlan;
import org.mindis.core.planning.Assignment;
import org.mindis.core.planning.AssignmentKey;
import org.mindis.core.planning.PlanMapper;
import org.mindis.core.planning.PlanningService;
import org.mindis.core.planning.ServicePlan;
import org.mindis.core.preferences.PreferencesService;

/// ViewModel for {@link org.mindis.gui.modules.ServicesModule}'s solve/save/
/// export/archive workflow: owns every call into {@link PlanningService},
/// {@link PlanRepository} and {@link PlanExportService}, the {@link
/// PlanMapper} snapshot/apply plumbing between them, *and* - unlike a plain
/// operation facade - the plan's own live state (the current {@link
/// ServicePlan}, whether it has any assignments, whether it differs from
/// what's on disk, whether a solve is running) plus the logic that mutates
/// that state (rebuild, pick, solve, save). That state used to live on
/// {@code ServicesModule} itself - a View owning state and the logic that
/// changes it is exactly what MVVM puts here instead: the module now only
/// constructs UI, marshals async solver callbacks onto the FX thread (a
/// View-side threading concern - see {@code ServicesModule#onSolveAll}), and
/// binds to this class's properties. Plain-constructed and held as a field
/// for the app's lifetime, like {@code ServicesModule}'s other dependencies -
/// not avaje-managed.
///
/// <p>Deliberately stateless with respect to which horizon (from/toInclusive)
/// is active - every method that needs one takes it as a parameter, the same
/// way {@link #generateProblem}/{@link #rebuildPreservingAssignments} always
/// have. The horizon itself is {@code ServicesModule}'s own view-local state
/// (the From/To pickers); this class only ever operates on whichever horizon
/// its caller currently cares about, never a remembered one - the picker
/// value at listener-fire time is always the live one to use, so remembering
/// a stale copy here would risk it drifting out of sync with the pickers on
/// exactly the range-change path that matters most.
public class PlanningViewModel {

    private final PlanningService planningService;
    private final PlanRepository planRepository;
    private final PreferencesService preferencesService;
    private final PlanExportService planExportService;

    // Plain field, not a property - the plan object itself is an
    // implementation detail (needed for scoreOf()/violationsByAssignment()/
    // PlanMapper, which operate on the whole ServicePlan, not just its
    // assignments). Every read goes through this field directly; every write
    // goes through publishPlan(...), which is also what keeps
    // liveAssignments/planPresent in sync - never assign this field itself.
    private @Nullable ServicePlan currentPlan;
    /// The reactive surface for "what's currently assigned" - the open
    /// editor's Altar-servers panel binds to this list directly.
    /// {@code setAll(...)} always fires a change notification even when the
    /// elements are the exact same object references already held - unlike a
    /// plain {@code ObjectProperty<ServicePlan>}, which would silently
    /// suppress the notification once Timefold's solver started mutating
    /// those objects in place instead of replacing them.
    private final ObservableList<Assignment> liveAssignments = FXCollections.observableArrayList();
    /// Whether a plan object currently exists (regardless of whether it has any assignments yet).
    private final ReadOnlyBooleanWrapper planPresent = new ReadOnlyBooleanWrapper(false);
    private final BooleanProperty hasPlan = new SimpleBooleanProperty(false);
    // Whether the current plan's assignments differ from what's on disk -
    // the plan-side half of "Save all"'s dirty state (the other half is
    // CrudModule's own dirtyCountProperty(), tracking LiturgicalService
    // record edits). Recomputed (not just set true) after every assignment
    // pick and solver run, by diffing against savedPlanSnapshot - so
    // clearing a pick and then setting it back to its previous value reads
    // clean again, the same "diff against last-saved state" rule the
    // CrudModule dirty tracking uses.
    private final BooleanProperty planDirty = new SimpleBooleanProperty(false);
    private final BooleanProperty solving = new SimpleBooleanProperty(false);
    private @Nullable AcceptedPlan savedPlanSnapshot;
    /// Guards {@link #scheduleRebuild} against coalescing two same-pulse
    /// callers (a store refresh-tick and a service-count change, both
    /// observed by {@code ServicesModule}) into a double {@link
    /// #rebuildForRange}.
    private boolean rebuildScheduled;

    public PlanningViewModel(PlanningService planningService,
                             PlanRepository planRepository,
                             PreferencesService preferencesService,
                             PlanExportService planExportService) {
        this.planningService = planningService;
        this.planRepository = planRepository;
        this.preferencesService = preferencesService;
        this.planExportService = planExportService;
    }

    // --- State ---

    /// The live plan, or {@code null} if none is loaded (no valid horizon
    /// picked yet). Read directly for whole-plan operations
    /// (scoreOf/violationsByAssignment/export); per-assignment UI reads
    /// {@link #liveAssignments()} instead.
    public @Nullable ServicePlan currentPlan() {
        return currentPlan;
    }

    public ObservableList<Assignment> liveAssignments() {
        return liveAssignments;
    }

    public ReadOnlyBooleanProperty planPresentProperty() {
        return planPresent.getReadOnlyProperty();
    }

    /// Whether the current plan has any assignments at all - part of "Solve
    /// all"/"Export"'s enablement (nothing to solve or export otherwise).
    public ReadOnlyBooleanProperty hasPlanProperty() {
        return hasPlan;
    }

    /// Whether an assignment pick or a solve run differs from what's on disk - part of the global Save all's enablement.
    public ReadOnlyBooleanProperty planDirtyProperty() {
        return planDirty;
    }

    /// Whether the solver is currently running - the global Save all, and every module-local solve control, must stay disabled while true.
    public ReadOnlyBooleanProperty solvingProperty() {
        return solving;
    }

    // --- Lifecycle ---

    public Optional<AcceptedPlan> loadSavedPlan() {
        return planRepository.load();
    }

    /// Discards the current plan (and its assignments) without rebuilding -
    /// for a range change (about to rebuild for the new horizon anyway) or a
    /// global Load (about to reload the whole database from disk).
    public void discardPlan() {
        publishPlan(null);
    }

    /// Rebuilds the plan for {@code from}/{@code to}: preserves the existing
    /// plan's in-progress (possibly unsaved) assignments if one is already
    /// loaded, otherwise starts fresh (re-applying whatever's saved for this
    /// horizon, if anything). {@code from}/{@code to} missing or reversed
    /// (no valid horizon picked) clears the plan instead.
    public void rebuildForRange(@Nullable LocalDate from, @Nullable LocalDate to) {
        if (from == null || to == null || to.isBefore(from)) {
            publishPlan(null);
            hasPlan.set(false);
            return;
        }
        ServicePlan existing = currentPlan;
        ServicePlan rebuilt = existing == null
                ? generateProblem(from, to)
                : rebuildPreservingAssignments(existing, from, to);
        publishPlan(rebuilt);
        hasPlan.set(!rebuilt.getAssignments().isEmpty());
        recomputeDirty(from, to);
    }

    /// Coalesces same-pulse rebuild requests into a single {@link
    /// #rebuildForRange} on the next pulse, rather than each caller running
    /// it directly and possibly twice in a row (a store re-baseline that also
    /// changes the service count fires both a refresh-tick and a
    /// service-count trigger in {@code ServicesModule} back to back).
    /// {@code reloadSnapshot} runs eagerly, before coalescing, so a second
    /// same-pulse caller that does need it still gets it even when a first
    /// caller already claimed the pending rebuild. {@code afterRebuild} runs
    /// after the (single, deferred) {@link #rebuildForRange} call - the
    /// caller's own presentation refresh (e.g. {@code table().refresh()}).
    public void scheduleRebuild(@Nullable LocalDate from, @Nullable LocalDate to, boolean reloadSnapshot, Runnable afterRebuild) {
        if (reloadSnapshot) {
            reloadSnapshot(from, to);
        }
        if (rebuildScheduled) {
            return;
        }
        rebuildScheduled = true;
        Platform.runLater(() -> {
            rebuildScheduled = false;
            rebuildForRange(from, to);
            afterRebuild.run();
        });
    }

    /// Reloads {@link #savedPlanSnapshot} for {@code from}/{@code to} (or clears it if none is saved for that horizon).
    public void reloadSnapshot(@Nullable LocalDate from, @Nullable LocalDate to) {
        savedPlanSnapshot = loadSavedPlan()
                .filter(saved -> saved.from().equals(from) && saved.toInclusive().equals(to))
                .orElse(null);
    }

    /// Applies a manual combo-box pick: sets the server, resolves the pin
    /// state (see {@link #resolvePinnedAfterManualPick}) and recomputes
    /// dirty state. Does not touch the UI - the caller still rebuilds its own
    /// assignment rows and refreshes the table.
    public void pick(Assignment assignment, @Nullable Server newServer, LocalDate from, LocalDate to) {
        assignment.setServer(newServer);
        assignment.setPinned(resolvePinnedAfterManualPick(assignment, newServer));
        recomputeDirty(from, to);
    }

    /// Persists {@code plan} if non-null and {@link #planDirtyProperty()} -
    /// the plan-side half of the global Save all (the other half, the four
    /// entity stores, is {@code LiveDatabase}'s job).
    public void save(@Nullable ServicePlan plan, LocalDate from, LocalDate to) {
        if (plan == null || !planDirty.get()) {
            return;
        }
        savePlan(plan, from, to);
        savedPlanSnapshot = PlanMapper.toAcceptedPlan(plan, from, to);
        recomputeDirty(from, to);
    }

    // --- Solve orchestration ---
    // Pure plan-state mutation - no UI. Callers (ServicesModule) still own
    // Platform.runLater marshaling around planningService's async callbacks
    // (which fire on the solver thread) and their own presentation refresh
    // (table().refresh(), score logging) after calling into these.

    public void beginSolve() {
        solving.set(true);
    }

    public void applyBestSolution(ServicePlan best) {
        publishPlan(best);
    }

    /// solving is set false *before* publishing the plan, so a listener that
    /// skips updates while solving (see {@code ServicesModule}'s
    /// {@code assignmentsListener}) lets this final update through.
    public void finishSolve(ServicePlan finalBest, LocalDate from, LocalDate to) {
        solving.set(false);
        publishPlan(finalBest);
        recomputeDirty(from, to);
    }

    public void failSolve() {
        solving.set(false);
    }

    /// Pins every assignment not belonging to {@code service}, for the
    /// duration of a scoped auto-fill solve - {@code service}'s own slots are
    /// the only ones left free to move. Returns the pin snapshot {@link
    /// #finishAutoFill} needs to restore every other assignment's original
    /// pin state afterward.
    public Map<String, Boolean> beginAutoFill(LiturgicalService service) {
        ServicePlan plan = currentPlan;
        Map<String, Boolean> pinSnapshot = new HashMap<>();
        if (plan == null) {
            return pinSnapshot;
        }
        for (Assignment assignment : plan.getAssignments()) {
            pinSnapshot.put(assignment.getId(), assignment.isPinned());
            if (!assignment.getService().id().equals(service.id())) {
                assignment.setPinned(true);
            }
        }
        solving.set(true);
        return pinSnapshot;
    }

    /// Restores every non-{@code service} assignment's original pin state
    /// from {@code pinSnapshot} (see {@link #beginAutoFill}); {@code
    /// service}'s own newly-filled slots become pinned instead, the same as
    /// a manual pick, since the planner asked for this fill just as
    /// deliberately - resolved via {@link #resolvePinnedAfterManualPick}, not
    /// a blind "pinned = server != null", so the solver reproducing the exact
    /// server a slot already had saved on disk doesn't force a pin that
    /// wasn't there before.
    public void finishAutoFill(ServicePlan finalBest, LiturgicalService service,
                               Map<String, Boolean> pinSnapshot, LocalDate from, LocalDate to) {
        for (Assignment assignment : finalBest.getAssignments()) {
            if (assignment.getService().id().equals(service.id())) {
                assignment.setPinned(resolvePinnedAfterManualPick(assignment, assignment.getServer()));
            } else {
                Boolean wasPinned = pinSnapshot.get(assignment.getId());
                assignment.setPinned(wasPinned != null && wasPinned);
            }
        }
        solving.set(false);
        publishPlan(finalBest);
        recomputeDirty(from, to);
    }

    /// Snapshot returned by {@link #beginAutofillWindow}: every assignment's
    /// original pin state (for {@link #finishAutofillWindow} to restore on
    /// the ones the solver was not allowed to touch), plus the ids left
    /// eligible for the solver.
    public record AutofillScope(Map<String, Boolean> pinSnapshot, Set<String> eligibleIds) {
    }

    /// Publishes {@code problem} as the live plan, then pins every assignment
    /// outside {@code [from, to]}, every assignment belonging to an archived
    /// service ({@code archivedServiceIds}), and - unless {@code overwrite} -
    /// every already-assigned assignment inside the window too; only
    /// genuinely eligible slots are left free for the solver. Generalizes
    /// {@link #beginAutoFill} from "pin everything except one service" to
    /// this wider window-based scope. Takes the freshly built problem
    /// explicitly (unlike {@link #beginAutoFill}, which reads the already-live
    /// {@code currentPlan}) since an Autofill run builds its own problem over
    /// a possibly wider range than whatever was loaded. Returns the pin
    /// snapshot and eligible-id set {@link #finishAutofillWindow} needs
    /// afterward.
    public AutofillScope beginAutofillWindow(ServicePlan problem, LocalDate from, LocalDate to,
                                             boolean overwrite, Set<String> archivedServiceIds) {
        publishPlan(problem);
        Map<String, Boolean> pinSnapshot = new HashMap<>();
        Set<String> eligible = new HashSet<>();
        for (Assignment assignment : problem.getAssignments()) {
            pinSnapshot.put(assignment.getId(), assignment.isPinned());
            LocalDate date = assignment.getService().dateTime().toLocalDate();
            boolean withinWindow = !date.isBefore(from) && !date.isAfter(to);
            boolean archived = archivedServiceIds.contains(assignment.getService().id());
            boolean isEligible = withinWindow && !archived && (overwrite || assignment.getServer() == null);
            assignment.setPinned(!isEligible);
            if (isEligible) {
                eligible.add(assignment.getId());
            }
        }
        solving.set(true);
        return new AutofillScope(pinSnapshot, eligible);
    }

    /// Restores every non-eligible assignment's original pin state from
    /// {@code scope} (see {@link #beginAutofillWindow}); eligible
    /// assignments become pinned instead via {@link
    /// #resolvePinnedAfterManualPick}, same as a manual pick - the planner
    /// asked for this fill just as deliberately.
    public void finishAutofillWindow(ServicePlan finalBest, AutofillScope scope, LocalDate from, LocalDate to) {
        for (Assignment assignment : finalBest.getAssignments()) {
            if (scope.eligibleIds().contains(assignment.getId())) {
                assignment.setPinned(resolvePinnedAfterManualPick(assignment, assignment.getServer()));
            } else {
                Boolean wasPinned = scope.pinSnapshot().get(assignment.getId());
                assignment.setPinned(wasPinned != null && wasPinned);
            }
        }
        solving.set(false);
        publishPlan(finalBest);
        recomputeDirty(from, to);
    }

    public void stopSolving(UUID id) {
        planningService.stopSolving(id);
    }

    // --- Internals ---

    /// The only path that may assign {@link #currentPlan} - keeps {@link
    /// #liveAssignments}/{@link #planPresent} in lockstep with it.
    private void publishPlan(@Nullable ServicePlan plan) {
        currentPlan = plan;
        liveAssignments.setAll(plan == null ? List.of() : plan.getAssignments());
        planPresent.set(plan != null);
    }

    /// Diffs the current plan against {@link #savedPlanSnapshot}, ignoring
    /// unassigned/unpinned slots on either side (an empty slot on disk and an
    /// empty slot in memory are the same "nothing to save" state regardless
    /// of assignment id bookkeeping) - so clearing a pick and then setting it
    /// back to its previous value reads clean again, not stuck dirty.
    private void recomputeDirty(LocalDate from, LocalDate to) {
        ServicePlan plan = currentPlan;
        if (plan == null) {
            planDirty.set(false);
            return;
        }
        AcceptedPlan current = PlanMapper.toAcceptedPlan(plan, from, to);
        Map<String, AcceptedPlan.PlannedAssignment> currentMeaningful = meaningfulAssignments(current);
        Map<String, AcceptedPlan.PlannedAssignment> savedMeaningful = savedPlanSnapshot == null
                ? Map.of()
                : meaningfulAssignments(savedPlanSnapshot);
        planDirty.set(!currentMeaningful.equals(savedMeaningful));
    }

    private static Map<String, AcceptedPlan.PlannedAssignment> meaningfulAssignments(AcceptedPlan plan) {
        Map<String, AcceptedPlan.PlannedAssignment> byId = new HashMap<>();
        for (AcceptedPlan.PlannedAssignment assignment : plan.assignments()) {
            if (assignment.serverId() != null || assignment.pinned()) {
                byId.put(assignment.assignmentId(), assignment);
            }
        }
        return byId;
    }

    /// The pin state to apply after a manual combo-box pick. A plain {@code
    /// newServer != null} would mark *any* interaction as a manual pin -
    /// including reselecting the exact server this slot was last saved with -
    /// permanently diverging from {@link #savedPlanSnapshot} on the pin flag
    /// alone even though the server itself matches, so "picking the original
    /// value back" could never actually clear the dirty accent. Restoring the
    /// saved pin state specifically when the picked server matches what was
    /// saved makes reverting a pick genuinely equivalent to not having
    /// touched it; picking anything else is still a deliberate manual pin,
    /// same as before.
    private boolean resolvePinnedAfterManualPick(Assignment assignment, @Nullable Server newServer) {
        AcceptedPlan saved = savedPlanSnapshot;
        if (saved != null) {
            String newServerId = newServer == null ? null : newServer.id();
            for (AcceptedPlan.PlannedAssignment planned : saved.assignments()) {
                if (planned.assignmentId().equals(assignment.getId())) {
                    if (Objects.equals(planned.serverId(), newServerId)) {
                        return planned.pinned();
                    }
                    break;
                }
            }
        }
        return newServer != null;
    }

    /// The Altar-servers-panel counterpart of {@link #recomputeDirty}:
    /// whether {@code service}'s own picks differ from what's on disk, scoped
    /// to just this row (unlike {@link #planDirtyProperty()}, which is true
    /// if *any* service in the horizon has an unsaved pick) - a per-row
    /// accent should only light up for the row that's actually different.
    public boolean isAssignmentsDirtyFor(LiturgicalService service, LocalDate from, LocalDate to) {
        ServicePlan plan = currentPlan;
        if (plan == null) {
            return false;
        }
        AcceptedPlan current = PlanMapper.toAcceptedPlan(plan, from, to);
        Map<String, AcceptedPlan.PlannedAssignment> currentForService =
                filterByService(meaningfulAssignments(current), service.id());
        Map<String, AcceptedPlan.PlannedAssignment> savedForService = savedPlanSnapshot == null
                ? Map.of()
                : filterByService(meaningfulAssignments(savedPlanSnapshot), service.id());
        return !currentForService.equals(savedForService);
    }

    private static Map<String, AcceptedPlan.PlannedAssignment> filterByService(
            Map<String, AcceptedPlan.PlannedAssignment> assignments, String serviceId) {
        Map<String, AcceptedPlan.PlannedAssignment> filtered = new HashMap<>();
        assignments.forEach((id, assignment) -> {
            if (AssignmentKey.belongsToService(id, serviceId)) {
                filtered.put(id, assignment);
            }
        });
        return filtered;
    }

    // --- Pre-existing stateless operations ---

    /// Builds a fresh problem for the horizon and, if a saved plan exists for
    /// exactly this horizon, re-applies its assignments/pins onto it.
    public ServicePlan generateProblem(LocalDate from, LocalDate toInclusive) {
        ServicePlan plan = planningService.buildProblem(from, toInclusive);
        planRepository.load()
                .filter(saved -> saved.from().equals(from) && saved.toInclusive().equals(toInclusive))
                .ifPresent(saved -> PlanMapper.applyAcceptedPlan(plan, saved));
        return plan;
    }

    /// Rebuilds the problem for the horizon from fresh repository data (picking
    /// up roster/service edits made elsewhere), re-applying {@code currentPlan}'s
    /// current - possibly unsaved - assignments and pins.
    public ServicePlan rebuildPreservingAssignments(ServicePlan currentPlan, LocalDate from, LocalDate toInclusive) {
        AcceptedPlan snapshot = PlanMapper.toAcceptedPlan(currentPlan, from, toInclusive);
        ServicePlan plan = planningService.buildProblem(from, toInclusive);
        PlanMapper.applyAcceptedPlan(plan, snapshot);
        return plan;
    }

    /// Splits the open plan at {@code cutoff} - see {@link
    /// org.mindis.core.planning.PlanArchiver}. Returns false if there is no
    /// open plan, or the cutoff produces no archived portion at all.
    public boolean archiveOpenPlan(LocalDate cutoff) {
        return planningService.archiveOpenPlan(cutoff);
    }

    /// Resolves both the solve window and the plan range for an Autofill run
    /// from the user's (possibly blank) bounds - see {@link
    /// org.mindis.core.planning.PlanningService#planAutofill}.
    public Optional<org.mindis.core.planning.PlanningService.AutofillPlan> planAutofill(
            @Nullable LocalDate from, @Nullable LocalDate to) {
        return planningService.planAutofill(from, to);
    }

    /// Builds an Autofill problem for {@code [from, toInclusive]}: the stored
    /// plans (open + archived) overlapping the range are re-applied by {@link
    /// org.mindis.core.planning.PlanningService#buildAutofillProblem}, then
    /// the current in-memory plan's own (possibly unsaved, staged) picks are
    /// re-applied on top so a second Autofill run - or one after other staged
    /// edits - preserves them rather than reverting to what is on disk.
    public ServicePlan buildAutofillProblem(LocalDate from, LocalDate toInclusive) {
        ServicePlan problem = planningService.buildAutofillProblem(from, toInclusive);
        if (currentPlan != null) {
            PlanMapper.applyAcceptedPlan(problem, PlanMapper.toAcceptedPlan(currentPlan, from, toInclusive));
        }
        return problem;
    }

    /// Starts solving with the solver time budget from preferences. Returns a job id for {@link #stopSolving}.
    public UUID solveAsync(ServicePlan problem,
                           Consumer<ServicePlan> bestSolutionConsumer,
                           Consumer<ServicePlan> finalSolutionConsumer,
                           Consumer<Throwable> exceptionHandler) {
        int seconds = preferencesService.get().solverSecondsLimit();
        return solveAsync(problem, Duration.ofSeconds(seconds),
                bestSolutionConsumer, finalSolutionConsumer, exceptionHandler);
    }

    /// As {@link #solveAsync(ServicePlan, Consumer, Consumer, Consumer)}, but
    /// with an explicit time budget instead of the preferences-configured one
    /// - for a scoped solve (e.g. one service's open slots, everything else
    /// pinned) where the user's full-plan time budget would be a needlessly
    /// long wait for a problem with far fewer decision variables.
    public UUID solveAsync(ServicePlan problem, Duration timeBudget,
                           Consumer<ServicePlan> bestSolutionConsumer,
                           Consumer<ServicePlan> finalSolutionConsumer,
                           Consumer<Throwable> exceptionHandler) {
        return planningService.solveAsync(problem, timeBudget,
                bestSolutionConsumer, finalSolutionConsumer, exceptionHandler);
    }

    public void savePlan(ServicePlan plan, LocalDate from, LocalDate toInclusive) {
        planRepository.save(PlanMapper.toAcceptedPlan(plan, from, toInclusive));
    }

    public void exportPlan(ServicePlan plan, LocalDate from, LocalDate toInclusive,
                           Path target, PlanExportFormat format) {
        planExportService.export(PlanMapper.toAcceptedPlan(plan, from, toInclusive), target, format);
    }

    /// Every saved plan from a period the planner has since moved past, newest first.
    public List<AcceptedPlan> listArchivedPlans() {
        return planRepository.listArchived();
    }

    /// Exports an already-accepted (typically archived) plan directly - no live {@link ServicePlan} needed.
    public void exportAcceptedPlan(AcceptedPlan plan, Path target, PlanExportFormat format) {
        planExportService.export(plan, target, format);
    }

    public @Nullable HardMediumSoftScore scoreOf(ServicePlan plan) {
        return planningService.scoreOf(plan);
    }

    public Map<String, List<String>> violationsByAssignment(ServicePlan plan) {
        return planningService.violationsByAssignment(plan);
    }

    /// Directory the export {@code FileChooser} last saved into; empty until the first export.
    public Optional<Path> lastExportDirectory() {
        String directory = preferencesService.get().lastExportDirectory();
        return directory == null ? Optional.empty() : Optional.of(Path.of(directory));
    }

    public void rememberExportDirectory(Path directory) {
        preferencesService.update(p -> p.withLastExportDirectory(directory.toString()));
    }

    /// Infers the export format from {@code fileName}'s extension, falling back
    /// to the first of {@code fallbackExtensions} (e.g. a FileChooser filter's
    /// {@code "*.pdf"} style extensions) if the file name has none recognized.
    public static PlanExportFormat resolveFormat(String fileName, List<String> fallbackExtensions) {
        int dot = fileName.lastIndexOf('.');
        if (dot >= 0 && dot < fileName.length() - 1) {
            try {
                return PlanExportFormat.fromExtension(fileName.substring(dot + 1));
            } catch (IllegalArgumentException ignored) {
                // Fall through to the fallback extension.
            }
        }
        return PlanExportFormat.fromExtension(fallbackExtensions.getFirst().substring(2));
    }
}
