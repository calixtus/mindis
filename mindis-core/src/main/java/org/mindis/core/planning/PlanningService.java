package org.mindis.core.planning;

import ai.timefold.solver.core.api.solver.SolverManager;
import ai.timefold.solver.core.config.solver.SolverConfig;
import ai.timefold.solver.core.config.solver.termination.TerminationConfig;

import jakarta.inject.Singleton;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;

import org.mindis.core.model.LiturgicalService;
import org.mindis.core.model.RoleSlot;
import org.mindis.core.model.Server;
import org.mindis.core.persistence.ServerRepository;
import org.mindis.core.persistence.ServiceRepository;

/**
 * Builds planning problems from the repositories and solves them
 * asynchronously. UI-agnostic API (PLAN.md section 2.5): callers receive best
 * solutions through a plain {@link Consumer}; the GUI adapts onto the FX
 * thread, a future web module onto HTTP.
 */
@Singleton
public class PlanningService implements AutoCloseable {

    private static final long MAX_SECONDS = 30L;
    private static final long UNIMPROVED_SECONDS = 5L;

    private final ServerRepository serverRepository;
    private final ServiceRepository serviceRepository;
    private final SolverManager<ServicePlan> solverManager;

    public PlanningService(ServerRepository serverRepository, ServiceRepository serviceRepository) {
        this.serverRepository = serverRepository;
        this.serviceRepository = serviceRepository;
        this.solverManager = SolverManager.create(solverConfig());
    }

    static SolverConfig solverConfig() {
        return new SolverConfig()
                .withSolutionClass(ServicePlan.class)
                .withEntityClasses(Assignment.class)
                .withConstraintProviderClass(MinDisConstraintProvider.class)
                .withTerminationConfig(new TerminationConfig()
                        .withSecondsSpentLimit(MAX_SECONDS)
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
        List<Assignment> assignments = new ArrayList<>();
        for (LiturgicalService service : serviceRepository.findAll()) {
            LocalDate date = service.dateTime().toLocalDate();
            if (date.isBefore(from) || date.isAfter(toInclusive)) {
                continue;
            }
            for (RoleSlot slot : service.slots()) {
                for (int i = 0; i < slot.count(); i++) {
                    assignments.add(new Assignment(
                            service.id() + ":" + slot.role() + ":" + i, service, slot.role()));
                }
            }
        }
        return new ServicePlan(activeServers, assignments);
    }

    /**
     * Solves asynchronously; every improved solution is pushed to
     * {@code bestSolutionConsumer} (solver thread!). Returns a job id for
     * {@link #stopSolving}.
     */
    public UUID solveAsync(ServicePlan problem,
                           Consumer<ServicePlan> bestSolutionConsumer,
                           Consumer<Throwable> exceptionHandler) {
        UUID jobId = UUID.randomUUID();
        solverManager.solveBuilder()
                .withProblemId(jobId)
                .withProblem(problem)
                .withBestSolutionEventConsumer(event -> bestSolutionConsumer.accept(event.solution()))
                .withExceptionHandler((id, throwable) -> exceptionHandler.accept(throwable))
                .run();
        return jobId;
    }

    public void stopSolving(UUID jobId) {
        solverManager.terminateEarly(jobId);
    }

    @Override
    public void close() {
        solverManager.close();
    }
}
