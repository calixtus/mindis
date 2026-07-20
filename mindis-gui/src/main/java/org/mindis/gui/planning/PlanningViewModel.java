package org.mindis.gui.planning;

import ai.timefold.solver.core.api.score.HardMediumSoftScore;

import java.nio.file.Path;
import java.time.Duration;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;

import javafx.beans.property.BooleanProperty;
import javafx.beans.property.ReadOnlyBooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;

import org.jspecify.annotations.Nullable;

import org.mindis.core.export.PlanExportFormat;
import org.mindis.core.export.PlanExportService;
import org.mindis.core.model.ArchivedService;
import org.mindis.core.model.LiturgicalService;
import org.mindis.core.persistence.ArchivedServiceRepository;
import org.mindis.core.planning.PlanningService;
import org.mindis.core.planning.ServiceArchiver;
import org.mindis.core.planning.ServicePlan;
import org.mindis.core.preferences.PreferencesService;

/// ViewModel for {@link org.mindis.gui.modules.ServicesModule}'s solve/archive/
/// export workflow. Thin by design now that an assignment lives on its {@link
/// org.mindis.core.model.Slot} (see {@link PlanningService}): there is no plan
/// object to hold, no plan-dirty state and no range bookkeeping - a solve
/// builds a transient {@link ServicePlan} from the live services, and its
/// results are written straight back onto those services (ordinary service
/// edits, saved by the same global Save all as everything else). The only
/// state this class owns is the {@code solving} flag every solve control binds
/// to. Plain-constructed and held as a field for the app's lifetime.
public class PlanningViewModel {

    private final PlanningService planningService;
    private final PreferencesService preferencesService;
    private final PlanExportService planExportService;
    private final ArchivedServiceRepository archivedServiceRepository;

    private final BooleanProperty solving = new SimpleBooleanProperty(false);

    public PlanningViewModel(PlanningService planningService,
                             PreferencesService preferencesService,
                             PlanExportService planExportService,
                             ArchivedServiceRepository archivedServiceRepository) {
        this.planningService = planningService;
        this.preferencesService = preferencesService;
        this.planExportService = planExportService;
        this.archivedServiceRepository = archivedServiceRepository;
    }

    // --- Solve state ---

    /// Whether the solver is currently running - every solve control and the global Save all stay disabled while true.
    public ReadOnlyBooleanProperty solvingProperty() {
        return solving;
    }

    public void beginSolve() {
        solving.set(true);
    }

    public void finishSolve() {
        solving.set(false);
    }

    public void failSolve() {
        solving.set(false);
    }

    // --- Problem building / write-back ---

    /// A transient problem over the current live services, each slot's stored assignment pre-applied.
    public ServicePlan buildProblem() {
        return planningService.buildProblem();
    }

    /// Writes a solved plan's assignments back onto {@code services} - new
    /// service records the caller merges into the live store.
    public List<LiturgicalService> writeBack(ServicePlan solved, List<LiturgicalService> services) {
        return planningService.writeBack(solved, services);
    }

    // --- Solve orchestration (callers own Platform.runLater marshaling) ---

    /// Starts solving with the solver time budget from preferences.
    public UUID solveAsync(ServicePlan problem,
                           Consumer<ServicePlan> bestSolutionConsumer,
                           Consumer<ServicePlan> finalSolutionConsumer,
                           Consumer<Throwable> exceptionHandler) {
        int seconds = preferencesService.get().solverSecondsLimit();
        return solveAsync(problem, Duration.ofSeconds(seconds),
                bestSolutionConsumer, finalSolutionConsumer, exceptionHandler);
    }

    /// As above, with an explicit time budget - for a scoped solve (one
    /// service's open slots) where the full-plan budget would be a needless wait.
    public UUID solveAsync(ServicePlan problem, Duration timeBudget,
                           Consumer<ServicePlan> bestSolutionConsumer,
                           Consumer<ServicePlan> finalSolutionConsumer,
                           Consumer<Throwable> exceptionHandler) {
        return planningService.solveAsync(problem, timeBudget,
                bestSolutionConsumer, finalSolutionConsumer, exceptionHandler);
    }

    public void stopSolving(UUID id) {
        planningService.stopSolving(id);
    }

    public @Nullable HardMediumSoftScore scoreOf(ServicePlan plan) {
        return planningService.scoreOf(plan);
    }

    public Map<String, List<String>> violationsByAssignment(ServicePlan plan) {
        return planningService.violationsByAssignment(plan);
    }

    // --- Archive ---

    /// Freezes live services up to {@code cutoff} into self-contained archived
    /// snapshots (persisted immediately) and returns the ids to drop from the
    /// live list - see {@link PlanningService#archive}.
    public ServiceArchiver.Result archive(LocalDate cutoff) {
        return planningService.archive(cutoff);
    }

    /// Every archived service, newest first.
    public List<ArchivedService> listArchived() {
        return archivedServiceRepository.findAll();
    }

    public void deleteArchived(ArchivedService service) {
        archivedServiceRepository.delete(service.id());
    }

    // --- Export ---

    public void exportLive(List<LiturgicalService> services, Path target, PlanExportFormat format) {
        planExportService.exportLive(services, target, format);
    }

    public void exportArchived(List<ArchivedService> services, Path target, PlanExportFormat format) {
        planExportService.exportArchived(services, target, format);
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
    /// to the first of {@code fallbackExtensions} if the name has none recognized.
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
