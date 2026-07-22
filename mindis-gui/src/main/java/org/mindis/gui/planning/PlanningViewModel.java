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
import javafx.beans.property.DoubleProperty;
import javafx.beans.property.ReadOnlyBooleanProperty;
import javafx.beans.property.ReadOnlyDoubleProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleDoubleProperty;

import org.jspecify.annotations.Nullable;

import org.mindis.core.export.PlanExportFormat;
import org.mindis.core.export.PlanExportService;
import org.mindis.core.model.ArchivedService;
import org.mindis.core.model.LiturgicalService;
import org.mindis.core.persistence.ArchivedServiceRepository;
import org.mindis.core.planning.Assignment;
import org.mindis.core.planning.Autofill;
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
/// edits, saved by the same global Save as everything else). The only
/// state this class owns is the {@code solving} flag every solve control binds
/// to. Plain-constructed and held as a field for the app's lifetime.
public class PlanningViewModel {

    private final PlanningService planningService;
    private final PreferencesService preferencesService;
    private final PlanExportService planExportService;
    private final ArchivedServiceRepository archivedServiceRepository;

    private final BooleanProperty solving = new SimpleBooleanProperty(false);
    /// Fraction (0..1) of the board's slots that are filled - the value a solve
    /// progress bar binds to. Recomputed from each improved solution.
    private final DoubleProperty solveProgress = new SimpleDoubleProperty(0);

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

    /// Whether the solver is currently running - every solve control and the global Save stay disabled while true.
    public ReadOnlyBooleanProperty solvingProperty() {
        return solving;
    }

    /// Fraction (0..1) of the board's slots currently filled - bind a solve progress bar to this.
    public ReadOnlyDoubleProperty solveProgressProperty() {
        return solveProgress;
    }

    /// Recomputes {@link #solveProgressProperty()} from {@code plan}'s
    /// filled/total slot ratio (empty board reads as 0). Called by the View on
    /// the FX thread for each improved solution and at solve start.
    public void updateProgress(ServicePlan plan) {
        List<Assignment> assignments = plan.getAssignments();
        if (assignments.isEmpty()) {
            solveProgress.set(0);
            return;
        }
        long filled = assignments.stream().filter(assignment -> assignment.getServer() != null).count();
        solveProgress.set((double) filled / assignments.size());
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

    // --- Autofill scoping (which slots a solve may touch) ---

    /// Scopes a windowed Autofill: leaves free only the slots of services in
    /// {@code [from, to]} that are open (or, with {@code overwrite}, already
    /// assigned) and pins the rest. A blank bound means unbounded. The returned
    /// {@link Autofill.Scope} is an opaque handle to pass back to {@link
    /// #finishAutofill} after the solve.
    public Autofill.Scope beginWindowAutofill(ServicePlan problem, @Nullable LocalDate from,
                                              @Nullable LocalDate to, boolean overwrite) {
        LocalDate effFrom = from == null ? LocalDate.MIN : from;
        LocalDate effTo = to == null ? LocalDate.MAX : to;
        return Autofill.begin(problem, Autofill.within(effFrom, effTo, overwrite));
    }

    /// Scopes a single service's auto-fill: leaves only that service's open slots free, pins everything else.
    public Autofill.Scope beginServiceAutofill(ServicePlan problem, String serviceId) {
        return Autofill.begin(problem, Autofill.forService(serviceId, false));
    }

    /// Restores the pins of every slot the solve was not allowed to touch and
    /// pins the freshly filled ones - the counterpart of {@code begin*Autofill}.
    public void finishAutofill(ServicePlan solved, Autofill.Scope scope) {
        Autofill.finish(solved, scope);
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
