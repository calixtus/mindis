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
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

import org.jspecify.annotations.Nullable;

import org.mindis.core.model.ArchivedService;
import org.mindis.core.model.LiturgicalService;
import org.mindis.core.model.Role;
import org.mindis.core.model.Server;
import org.mindis.core.model.Slot;
import org.mindis.core.persistence.ArchivedServiceRepository;
import org.mindis.core.persistence.RoleRepository;
import org.mindis.core.persistence.ServerRepository;
import org.mindis.core.persistence.ServiceRepository;
import org.mindis.core.preferences.MinDisPreferences;
import org.mindis.core.preferences.PreferencesService;

/// Turns the live services into solver problems and writes the results back
/// onto them, and freezes past services into the archive. UI-agnostic API
/// (PLAN.md 2.5): callers receive best solutions through a plain {@link
/// Consumer}; the GUI adapts onto the FX thread.
///
/// <p>There is no separate range-keyed plan structure any more: an assignment
/// lives on its {@link Slot} (see that class), so a {@link ServicePlan} is a
/// transient view built on demand from a set of services and discarded once its
/// results are written back. Scoping a solve to only some slots (one service,
/// or an unassigned-only window) is done purely by pinning the rest - {@link
/// Autofill}.
@Singleton
public class PlanningService implements AutoCloseable {

    private static final long UNIMPROVED_SECONDS = 5L;

    private final ServerRepository serverRepository;
    private final ServiceRepository serviceRepository;
    private final RoleRepository roleRepository;
    private final PreferencesService preferencesService;
    private final ArchivedServiceRepository archivedServiceRepository;
    private final SolverManager<ServicePlan> solverManager;
    private final SolutionManager<ServicePlan, HardMediumSoftScore> solutionManager;

    public PlanningService(ServerRepository serverRepository,
                           ServiceRepository serviceRepository,
                           RoleRepository roleRepository,
                           PreferencesService preferencesService,
                           ArchivedServiceRepository archivedServiceRepository) {
        this.serverRepository = serverRepository;
        this.serviceRepository = serviceRepository;
        this.roleRepository = roleRepository;
        this.preferencesService = preferencesService;
        this.archivedServiceRepository = archivedServiceRepository;
        this.solverManager = SolverManager.create(solverConfig());
        this.solutionManager = SolutionManager.create(solverManager);
    }

    static SolverConfig solverConfig() {
        return new SolverConfig()
                .withSolutionClass(ServicePlan.class)
                .withEntityClasses(Assignment.class)
                .withConstraintProviderClass(MinDisConstraintProvider.class)
                .withTerminationConfig(terminationWithin(
                        Duration.ofSeconds(MinDisPreferences.DEFAULT_SOLVER_SECONDS)));
    }

    /// Termination for a solve capped at {@code timeBudget}: stop at the budget,
    /// <em>or</em> early once no improved solution has been found for {@link
    /// #UNIMPROVED_SECONDS} - the "it's clearly converged, don't burn the rest
    /// of the clock" cutoff. Both limits always travel together: a {@link
    /// SolverConfigOverride} replaces the whole {@link TerminationConfig} rather
    /// than merging, so building the spent limit alone (as the per-solve
    /// override used to) would silently drop the unimproved cutoff and make
    /// every solve run the full budget.
    static TerminationConfig terminationWithin(Duration timeBudget) {
        return new TerminationConfig()
                .withSpentLimit(timeBudget)
                .withUnimprovedSecondsSpentLimit(UNIMPROVED_SECONDS);
    }

    /// Builds a problem from the current live services: one {@link Assignment}
    /// per role slot, each pre-populated from the slot's own stored assignment
    /// (server + pin), all active servers as the value range, plus
    /// cross-boundary spacing facts from the archive.
    public ServicePlan buildProblem() {
        List<LiturgicalService> services = serviceRepository.findAll();
        return buildProblem(services, priorFromArchived(earliestDate(services)));
    }

    /// Builds a problem from an explicit service set with explicit prior facts
    /// - the testable core of {@link #buildProblem()}.
    public ServicePlan buildProblem(List<LiturgicalService> services, List<PriorAssignment> priorAssignments) {
        List<Server> activeServers = serverRepository.findAll().stream()
                .filter(Server::active)
                .toList();
        Map<String, Server> serversById = new HashMap<>();
        serverRepository.findAll().forEach(server -> serversById.put(server.id(), server));
        Map<String, Role> rolesById = new HashMap<>();
        roleRepository.findAll().forEach(role -> rolesById.put(role.id(), role));

        List<Assignment> assignments = new ArrayList<>();
        for (LiturgicalService service : services) {
            for (Slot slot : service.slots()) {
                Role role = rolesById.get(slot.role());
                if (role == null) {
                    // Slot references a deleted role; nothing to assign.
                    continue;
                }
                Assignment assignment = new Assignment(
                        new AssignmentKey(service.id(), slot.id()).toId(), service, role);
                if (slot.serverId() != null) {
                    assignment.setServer(serversById.get(slot.serverId()));
                }
                assignment.setPinned(slot.pinned() && assignment.getServer() != null);
                assignments.add(assignment);
            }
        }
        ServicePlan plan = new ServicePlan(activeServers, assignments);
        plan.setPriorAssignments(priorAssignments);
        plan.setConstraintWeightOverrides(weightOverridesFromPreferences());
        return plan;
    }

    /// Writes {@code solved}'s assignments back onto {@code services}: each
    /// slot's {@code serverId}/{@code pinned} is updated from its assignment.
    /// Pure - returns new service records, mutates nothing; the caller stages
    /// them into the live store, and a Save all persists them like any other
    /// service edit.
    public List<LiturgicalService> writeBack(ServicePlan solved, List<LiturgicalService> services) {
        Map<String, Assignment> byId = new HashMap<>();
        solved.getAssignments().forEach(assignment -> byId.put(assignment.getId(), assignment));
        List<LiturgicalService> result = new ArrayList<>();
        for (LiturgicalService service : services) {
            List<Slot> newSlots = new ArrayList<>();
            for (Slot slot : service.slots()) {
                Assignment assignment = byId.get(new AssignmentKey(service.id(), slot.id()).toId());
                if (assignment == null) {
                    newSlots.add(slot);
                    continue;
                }
                Server server = assignment.getServer();
                newSlots.add(slot.withServer(server == null ? null : server.id(), assignment.isPinned()));
            }
            result.add(service.withSlots(newSlots));
        }
        return result;
    }

    /// {@link PriorAssignment} facts drawn from the archive: any archived slot
    /// whose service date lies in the {@link
    /// MinDisConstraintProvider#SPACING_THRESHOLD_DAYS}-day tail immediately
    /// before {@code earliest} and whose server still exists, so the solver is
    /// penalized for scheduling that server again right up against the frozen
    /// history. Empty when there are no live services to place.
    public List<PriorAssignment> priorFromArchived(@Nullable LocalDate earliest) {
        if (earliest == null) {
            return List.of();
        }
        LocalDate cutoff = earliest.minusDays(MinDisConstraintProvider.SPACING_THRESHOLD_DAYS);
        Map<String, Server> serversById = new HashMap<>();
        serverRepository.findAll().forEach(server -> serversById.put(server.id(), server));
        List<PriorAssignment> result = new ArrayList<>();
        for (ArchivedService archived : archivedServiceRepository.findAll()) {
            LocalDate date = archived.dateTime().toLocalDate();
            if (date.isBefore(cutoff) || !date.isBefore(earliest)) {
                continue;
            }
            for (ArchivedService.ArchivedSlot slot : archived.slots()) {
                if (slot.serverId() == null) {
                    continue;
                }
                Server server = serversById.get(slot.serverId());
                if (server != null) {
                    result.add(new PriorAssignment(date, server));
                }
            }
        }
        return result;
    }

    /// Freezes every live service dated on or before {@code cutoff} into a
    /// self-contained {@link ArchivedService} snapshot (role/server names
    /// resolved now), persists the snapshots immediately, and returns the ids
    /// of the live services to drop. The caller removes those from the live
    /// list and Save-alls to commit the removal. Empty result if the cutoff
    /// freezes nothing.
    public ServiceArchiver.Result archive(LocalDate cutoff) {
        Map<String, Role> rolesById = new HashMap<>();
        roleRepository.findAll().forEach(role -> rolesById.put(role.id(), role));
        Map<String, Server> serversById = new HashMap<>();
        serverRepository.findAll().forEach(server -> serversById.put(server.id(), server));
        ServiceArchiver.Result result = ServiceArchiver.archive(
                serviceRepository.findAll(), cutoff, Instant.now(),
                roleId -> rolesById.containsKey(roleId) ? rolesById.get(roleId).name() : null,
                serverId -> serversById.containsKey(serverId) ? serversById.get(serverId).displayName() : null);
        archivedServiceRepository.addAll(result.archived());
        return result;
    }

    private static @Nullable LocalDate earliestDate(List<LiturgicalService> services) {
        return services.stream()
                .map(service -> service.dateTime().toLocalDate())
                .min(LocalDate::compareTo)
                .orElse(null);
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
                .withConfigOverride(new SolverConfigOverride().withTerminationConfig(terminationWithin(timeBudget)))
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
    /// violated hard/medium constraints. Computed by {@link ViolationChecker}.
    public Map<String, List<String>> violationsByAssignment(ServicePlan plan) {
        return ViolationChecker.violationsByAssignment(plan);
    }

    @Override
    public void close() {
        solverManager.close();
    }
}
