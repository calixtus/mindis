package org.mindis.core.planning;

import ai.timefold.solver.core.api.domain.solution.ConstraintWeightOverrides;
import ai.timefold.solver.core.api.score.HardMediumSoftScore;
import ai.timefold.solver.core.api.solver.SolutionManager;
import ai.timefold.solver.core.api.solver.SolverConfigOverride;
import ai.timefold.solver.core.api.solver.SolverManager;
import ai.timefold.solver.core.config.solver.SolverConfig;
import ai.timefold.solver.core.config.solver.termination.TerminationConfig;

import jakarta.inject.Singleton;

import java.time.Duration;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;

import org.jspecify.annotations.Nullable;

import org.mindis.core.model.LiturgicalService;
import org.mindis.core.model.Role;
import org.mindis.core.model.Server;
import org.mindis.core.model.Slot;
import org.mindis.core.persistence.PlanRepository;
import org.mindis.core.persistence.RoleRepository;
import org.mindis.core.persistence.ServerRepository;
import org.mindis.core.persistence.ServiceRepository;
import org.mindis.core.preferences.MinDisPreferences;
import org.mindis.core.preferences.PreferencesService;

/// Builds planning problems from the repositories and solves them
/// asynchronously. UI-agnostic API (PLAN.md section 2.5): callers receive best
/// solutions through a plain {@link Consumer}; the GUI adapts onto the FX
/// thread, a future web module onto HTTP.
@Singleton
public class PlanningService implements AutoCloseable {

    private static final long UNIMPROVED_SECONDS = 5L;

    private final ServerRepository serverRepository;
    private final ServiceRepository serviceRepository;
    private final RoleRepository roleRepository;
    private final PreferencesService preferencesService;
    private final PlanRepository planRepository;
    private final SolverManager<ServicePlan> solverManager;
    private final SolutionManager<ServicePlan, HardMediumSoftScore> solutionManager;

    public PlanningService(ServerRepository serverRepository,
                           ServiceRepository serviceRepository,
                           RoleRepository roleRepository,
                           PreferencesService preferencesService,
                           PlanRepository planRepository) {
        this.serverRepository = serverRepository;
        this.serviceRepository = serviceRepository;
        this.roleRepository = roleRepository;
        this.preferencesService = preferencesService;
        this.planRepository = planRepository;
        this.solverManager = SolverManager.create(solverConfig());
        this.solutionManager = SolutionManager.create(solverManager);
    }

    static SolverConfig solverConfig() {
        return new SolverConfig()
                .withSolutionClass(ServicePlan.class)
                .withEntityClasses(Assignment.class)
                .withConstraintProviderClass(MinDisConstraintProvider.class)
                .withTerminationConfig(new TerminationConfig()
                        .withSecondsSpentLimit((long) MinDisPreferences.DEFAULT_SOLVER_SECONDS)
                        .withUnimprovedSecondsSpentLimit(UNIMPROVED_SECONDS));
    }

    /// Creates the unsolved problem for a horizon: one {@link Assignment} per
    /// required role slot of every service in the range, all active servers as
    /// the value range.
    public ServicePlan buildProblem(LocalDate from, LocalDate toInclusive) {
        List<Server> activeServers = serverRepository.findAll().stream()
                .filter(Server::active)
                .toList();
        Map<String, Role> rolesById = new HashMap<>();
        roleRepository.findAll().forEach(role -> rolesById.put(role.id(), role));
        List<Assignment> assignments = new ArrayList<>();
        for (LiturgicalService service : serviceRepository.findAll()) {
            LocalDate date = service.dateTime().toLocalDate();
            if (date.isBefore(from) || date.isAfter(toInclusive)) {
                continue;
            }
            for (Slot slot : service.slots()) {
                Role role = rolesById.get(slot.role());
                if (role == null) {
                    // Slot references a deleted role; nothing to assign.
                    continue;
                }
                assignments.add(new Assignment(new AssignmentKey(service.id(), slot.id()).toId(), service, role));
            }
        }
        ServicePlan plan = new ServicePlan(activeServers, assignments);
        plan.setPriorAssignments(buildPriorAssignments(from));
        plan.setConstraintWeightOverrides(weightOverridesFromPreferences());
        return plan;
    }

    /// Splits the open plan at {@code cutoff}: the portion dated on or
    /// before it is frozen into a new archived plan, the remainder (if any)
    /// becomes the new open plan. No-op (returns false) if there is no open
    /// plan, or if the split produces no archived portion at all (cutoff
    /// before every known assignment date) - callers should disable the
    /// archive action in that case rather than surface this as an error.
    public boolean archiveOpenPlan(LocalDate cutoff) {
        Optional<AcceptedPlan> openOpt = planRepository.load();
        if (openOpt.isEmpty()) {
            return false;
        }
        AcceptedPlan open = openOpt.get();
        PlanArchiver.Split split = PlanArchiver.split(open, cutoff,
                id -> serviceRepository.findById(id).map(service -> service.dateTime().toLocalDate()));
        if (split.archived().isEmpty()) {
            return false;
        }
        planRepository.applyArchiveSplit(split.archived().get(), split.remainder().orElse(null));
        return true;
    }

    /// One end of an Autofill window, after resolving blank bounds against
    /// the currently known service dates.
    public record DateWindow(LocalDate from, LocalDate toInclusive) {
    }

    /// Resolves an Autofill window: a blank {@code from} defaults to the
    /// earliest known service date ("fill all previous unassigned services
    /// present"), a blank {@code to} defaults to the latest known service
    /// date ("fill all future services"). Empty if there are no services at
    /// all, or if the resolved bounds are reversed.
    public Optional<DateWindow> resolveAutofillWindow(@Nullable LocalDate from, @Nullable LocalDate to) {
        List<LocalDate> dates = serviceRepository.findAll().stream()
                .map(service -> service.dateTime().toLocalDate())
                .toList();
        if (dates.isEmpty()) {
            return Optional.empty();
        }
        LocalDate resolvedFrom = from != null ? from : dates.stream().min(LocalDate::compareTo).orElseThrow();
        LocalDate resolvedTo = to != null ? to : dates.stream().max(LocalDate::compareTo).orElseThrow();
        return resolvedTo.isBefore(resolvedFrom) ? Optional.empty() : Optional.of(new DateWindow(resolvedFrom, resolvedTo));
    }

    /// The two ranges an Autofill run needs: {@code solveWindow} is what the
    /// solver may actually change (eligible slots), {@code planRange} is the
    /// full span the resulting open plan covers and is saved under - always a
    /// superset of the solve window, extended to include the prior open
    /// plan's whole range so nothing already decided there is dropped on save.
    public record AutofillPlan(DateWindow solveWindow, DateWindow planRange) {
    }

    /// Resolves both ranges for an Autofill run from the user's (possibly
    /// blank) bounds. The solve window comes from {@link
    /// #resolveAutofillWindow}, then clamped to start no earlier than the day
    /// after the latest archived period - archived services are frozen and
    /// must never be re-solved or pulled into the open plan, so a blank
    /// {@code from} that resolves into archived territory is raised to just
    /// past it. The plan range is the solve window widened to also cover the
    /// current open plan's full span (so a forward-only fill still saves the
    /// earlier, already-decided part of the open plan rather than truncating
    /// it). Empty if there are no services, the bounds are reversed, or the
    /// whole window falls inside archived territory (nothing left to fill).
    public Optional<AutofillPlan> planAutofill(@Nullable LocalDate from, @Nullable LocalDate to) {
        Optional<DateWindow> resolved = resolveAutofillWindow(from, to);
        if (resolved.isEmpty()) {
            return Optional.empty();
        }
        DateWindow window = resolved.get();
        LocalDate afterArchives = planRepository.listArchived().stream()
                .map(AcceptedPlan::toInclusive)
                .max(Comparator.naturalOrder())
                .map(latest -> latest.plusDays(1))
                .orElse(window.from());
        LocalDate solveFrom = window.from().isBefore(afterArchives) ? afterArchives : window.from();
        if (window.toInclusive().isBefore(solveFrom)) {
            return Optional.empty();
        }
        DateWindow solveWindow = new DateWindow(solveFrom, window.toInclusive());
        Optional<AcceptedPlan> open = planRepository.load();
        LocalDate planFrom = open.map(p -> p.from().isBefore(solveFrom) ? p.from() : solveFrom).orElse(solveFrom);
        LocalDate planTo = open.map(p -> p.toInclusive().isAfter(window.toInclusive()) ? p.toInclusive() : window.toInclusive())
                .orElse(window.toInclusive());
        return Optional.of(new AutofillPlan(solveWindow, new DateWindow(planFrom, planTo)));
    }

    /// Builds an Autofill problem: the usual {@link #buildProblem} for the
    /// window, with every stored plan overlapping it - open or archived - 
    /// re-applied on top, so previously-decided assignments (including
    /// archived ones) come back with their saved values instead of blank,
    /// which is what lets the pin pass leave only the genuinely eligible
    /// slots open to the solver.
    public ServicePlan buildAutofillProblem(LocalDate from, LocalDate toInclusive) {
        ServicePlan plan = buildProblem(from, toInclusive);
        for (AcceptedPlan stored : planRepository.allOverlapping(from, toInclusive)) {
            PlanMapper.applyAcceptedPlan(plan, stored);
        }
        return plan;
    }

    /// {@link PriorAssignment} facts from the plan immediately preceding
    /// {@code from}, if any, trimmed to the {@link
    /// MinDisConstraintProvider#SPACING_THRESHOLD_DAYS}-day tail that {@link
    /// MinDisConstraintProvider#spacingFromPriorPlan} actually looks at -
    /// loading the rest of a possibly month-long prior plan would just be
    /// dead weight in the solver's fact list.
    private List<PriorAssignment> buildPriorAssignments(LocalDate from) {
        return planRepository.mostRecentBefore(from)
                .map(prior -> priorAssignmentsFrom(prior, from))
                .orElse(List.of());
    }

    private List<PriorAssignment> priorAssignmentsFrom(AcceptedPlan prior, LocalDate from) {
        LocalDate cutoff = from.minusDays(MinDisConstraintProvider.SPACING_THRESHOLD_DAYS);
        List<PriorAssignment> result = new ArrayList<>();
        for (AcceptedPlan.PlannedAssignment assignment : prior.assignments()) {
            String serverId = assignment.serverId();
            if (serverId == null) {
                continue;
            }
            LiturgicalService service = serviceRepository.findById(assignment.serviceId()).orElse(null);
            if (service == null) {
                continue;
            }
            LocalDate date = service.dateTime().toLocalDate();
            if (date.isBefore(cutoff)) {
                continue;
            }
            Server server = serverRepository.findById(serverId).orElse(null);
            if (server == null) {
                continue;
            }
            result.add(new PriorAssignment(date, server));
        }
        return result;
    }

    private ConstraintWeightOverrides<HardMediumSoftScore> weightOverridesFromPreferences() {
        Map<String, HardMediumSoftScore> overrides = new HashMap<>();
        preferencesService.get().softConstraintWeights().forEach(
                (name, weight) -> overrides.put(name, HardMediumSoftScore.ofSoft(weight)));
        return ConstraintWeightOverrides.of(overrides);
    }

    /// Solves asynchronously; every improved solution is pushed to
    /// {@code bestSolutionConsumer} (solver thread!). Returns a job id for
    /// {@link #stopSolving}.
    public UUID solveAsync(ServicePlan problem,
                           Duration timeBudget,
                           Consumer<ServicePlan> bestSolutionConsumer,
                           Consumer<ServicePlan> finalSolutionConsumer,
                           Consumer<Throwable> exceptionHandler) {
        UUID jobId = UUID.randomUUID();
        solverManager.solveBuilder()
                .withProblemId(jobId)
                .withProblem(problem)
                .withConfigOverride(new SolverConfigOverride().withTerminationSpentLimit(timeBudget))
                .withBestSolutionEventConsumer(event -> bestSolutionConsumer.accept(event.solution()))
                .withFinalBestSolutionEventConsumer(event -> finalSolutionConsumer.accept(event.solution()))
                .withExceptionHandler((id, throwable) -> exceptionHandler.accept(throwable))
                .run();
        return jobId;
    }

    public void stopSolving(UUID jobId) {
        solverManager.terminateEarly(jobId);
    }

    /// Current score of a (possibly manually edited) plan. Uses
    /// {@code SolutionManager.update} - Timefold's {@code analyze()} is an
    /// enterprise-only feature (see PLAN.md risk table).
    public @Nullable HardMediumSoftScore scoreOf(ServicePlan plan) {
        return solutionManager.update(plan);
    }

    /// Per-assignment violation summary: assignment id to the names of the
    /// violated hard/medium constraints. Constraint names are full-text
    /// localization keys (PLAN.md section 2.3). Computed by
    /// {@link ViolationChecker} - Timefold's {@code analyze()} is enterprise-only.
    public Map<String, List<String>> violationsByAssignment(ServicePlan plan) {
        return ViolationChecker.violationsByAssignment(plan);
    }

    @Override
    public void close() {
        solverManager.close();
    }
}
