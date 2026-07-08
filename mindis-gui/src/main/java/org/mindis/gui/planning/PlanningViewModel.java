package org.mindis.gui.planning;

import ai.timefold.solver.core.api.score.HardMediumSoftScore;

import io.avaje.inject.Prototype;

import java.nio.file.Path;
import java.time.Duration;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;

import org.mindis.core.export.PlanExportFormat;
import org.mindis.core.export.PlanExportService;
import org.mindis.core.persistence.PlanRepository;
import org.mindis.core.planning.AcceptedPlan;
import org.mindis.core.planning.PlanMapper;
import org.mindis.core.planning.PlanningService;
import org.mindis.core.planning.ServicePlan;
import org.mindis.core.preferences.PreferencesService;

/**
 * ViewModel for {@link PlanningController}: owns every call into
 * {@link PlanningService}, {@link PlanRepository} and {@link PlanExportService},
 * plus the {@link PlanMapper} snapshot/apply plumbing between them, so the
 * controller only constructs UI, marshals async callbacks onto the FX thread,
 * and binds to this class.
 */
@Prototype
public class PlanningViewModel {

    private final PlanningService planningService;
    private final PlanRepository planRepository;
    private final PreferencesService preferencesService;
    private final PlanExportService planExportService;

    public PlanningViewModel(PlanningService planningService,
                             PlanRepository planRepository,
                             PreferencesService preferencesService,
                             PlanExportService planExportService) {
        this.planningService = planningService;
        this.planRepository = planRepository;
        this.preferencesService = preferencesService;
        this.planExportService = planExportService;
    }

    public Optional<AcceptedPlan> loadSavedPlan() {
        return planRepository.load();
    }

    /**
     * Builds a fresh problem for the horizon and, if a saved plan exists for
     * exactly this horizon, re-applies its assignments/pins onto it.
     */
    public ServicePlan generateProblem(LocalDate from, LocalDate toInclusive) {
        ServicePlan plan = planningService.buildProblem(from, toInclusive);
        planRepository.load()
                .filter(saved -> saved.from().equals(from) && saved.toInclusive().equals(toInclusive))
                .ifPresent(saved -> PlanMapper.applyAcceptedPlan(plan, saved));
        return plan;
    }

    /**
     * Rebuilds the problem for the horizon from fresh repository data (picking
     * up roster/service edits made elsewhere), re-applying {@code currentPlan}'s
     * current - possibly unsaved - assignments and pins.
     */
    public ServicePlan rebuildPreservingAssignments(ServicePlan currentPlan, LocalDate from, LocalDate toInclusive) {
        AcceptedPlan snapshot = PlanMapper.toAcceptedPlan(currentPlan, from, toInclusive);
        ServicePlan plan = planningService.buildProblem(from, toInclusive);
        PlanMapper.applyAcceptedPlan(plan, snapshot);
        return plan;
    }

    /** Starts solving with the solver time budget from preferences. Returns a job id for {@link #stopSolving}. */
    public UUID solveAsync(ServicePlan problem,
                           Consumer<ServicePlan> bestSolutionConsumer,
                           Consumer<ServicePlan> finalSolutionConsumer,
                           Consumer<Throwable> exceptionHandler) {
        int seconds = preferencesService.get().solverSecondsLimit();
        return planningService.solveAsync(problem, Duration.ofSeconds(seconds),
                bestSolutionConsumer, finalSolutionConsumer, exceptionHandler);
    }

    public void stopSolving(UUID jobId) {
        planningService.stopSolving(jobId);
    }

    public void savePlan(ServicePlan plan, LocalDate from, LocalDate toInclusive) {
        planRepository.save(PlanMapper.toAcceptedPlan(plan, from, toInclusive));
    }

    public void exportPlan(ServicePlan plan, LocalDate from, LocalDate toInclusive,
                           Path target, PlanExportFormat format) {
        planExportService.export(PlanMapper.toAcceptedPlan(plan, from, toInclusive), target, format);
    }

    public HardMediumSoftScore scoreOf(ServicePlan plan) {
        return planningService.scoreOf(plan);
    }

    public Map<String, List<String>> violationsByAssignment(ServicePlan plan) {
        return planningService.violationsByAssignment(plan);
    }

    /** Directory the export {@code FileChooser} last saved into; empty until the first export. */
    public Optional<Path> lastExportDirectory() {
        String directory = preferencesService.get().lastExportDirectory();
        return directory == null ? Optional.empty() : Optional.of(Path.of(directory));
    }

    public void rememberExportDirectory(Path directory) {
        preferencesService.update(p -> p.withLastExportDirectory(directory.toString()));
    }

    /**
     * Infers the export format from {@code fileName}'s extension, falling back
     * to the first of {@code fallbackExtensions} (e.g. a FileChooser filter's
     * {@code "*.pdf"} style extensions) if the file name has none recognized.
     */
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
