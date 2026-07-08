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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

import org.jspecify.annotations.Nullable;

import org.mindis.core.model.LiturgicalService;
import org.mindis.core.model.Role;
import org.mindis.core.model.RoleSlot;
import org.mindis.core.model.Server;
import org.mindis.core.persistence.PlanRepository;
import org.mindis.core.persistence.RoleRepository;
import org.mindis.core.persistence.ServerRepository;
import org.mindis.core.persistence.ServiceRepository;
import org.mindis.core.preferences.MinDisPreferences;
import org.mindis.core.preferences.PreferencesService;

/**
 * Builds planning problems from the repositories and solves them
 * asynchronously. UI-agnostic API (PLAN.md section 2.5): callers receive best
 * solutions through a plain {@link Consumer}; the GUI adapts onto the FX
 * thread, a future web module onto HTTP.
 */
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

    /**
     * Creates the unsolved problem for a horizon: one {@link Assignment} per
     * required role slot of every service in the range, all active servers as
     * the value range.
     */
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
            for (RoleSlot slot : service.slots()) {
                Role role = rolesById.get(slot.role());
                if (role == null) {
                    // Slot references a deleted role; nothing to assign.
                    continue;
                }
                for (int i = 0; i < slot.count(); i++) {
                    assignments.add(new Assignment(
                            service.id() + ":" + slot.role() + ":" + i, service, role));
                }
            }
        }
        ServicePlan plan = new ServicePlan(activeServers, assignments);
        plan.setPriorAssignments(buildPriorAssignments(from));
        plan.setConstraintWeightOverrides(weightOverridesFromPreferences());
        return plan;
    }

    /**
     * {@link PriorAssignment} facts from the plan immediately preceding
     * {@code from}, if any, trimmed to the {@link
     * MinDisConstraintProvider#SPACING_THRESHOLD_DAYS}-day tail that {@link
     * MinDisConstraintProvider#spacingFromPriorPlan} actually looks at -
     * loading the rest of a possibly month-long prior plan would just be
     * dead weight in the solver's fact list.
     */
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

    /**
     * Solves asynchronously; every improved solution is pushed to
     * {@code bestSolutionConsumer} (solver thread!). Returns a job id for
     * {@link #stopSolving}.
     */
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

    /**
     * Current score of a (possibly manually edited) plan. Uses
     * {@code SolutionManager.update} - Timefold's {@code analyze()} is an
     * enterprise-only feature (see PLAN.md risk table).
     */
    public @Nullable HardMediumSoftScore scoreOf(ServicePlan plan) {
        return solutionManager.update(plan);
    }

    /**
     * Per-assignment violation summary: assignment id to the names of the
     * violated hard/medium constraints. Constraint names are full-text
     * localization keys (PLAN.md section 2.3). Computed by
     * {@link ViolationChecker} - Timefold's {@code analyze()} is enterprise-only.
     */
    public Map<String, List<String>> violationsByAssignment(ServicePlan plan) {
        return ViolationChecker.violationsByAssignment(plan);
    }

    @Override
    public void close() {
        solverManager.close();
    }
}
