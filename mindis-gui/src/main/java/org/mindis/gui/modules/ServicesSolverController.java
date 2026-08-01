package org.mindis.gui.modules;

import java.time.Duration;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.Supplier;

import ai.timefold.solver.core.api.score.HardMediumSoftScore;

import com.dlsc.gemsfx.DialogPane.Dialog;

import javafx.application.Platform;
import javafx.beans.value.ChangeListener;
import javafx.scene.control.ButtonBar.ButtonData;
import javafx.scene.control.ButtonType;

import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.mindis.core.l10n.Localization;
import org.mindis.core.model.LiturgicalService;
import org.mindis.core.planning.Autofill;
import org.mindis.core.planning.ServicePlan;
import org.mindis.gui.planning.PlanningViewModel;
import org.mindis.gui.shell.ShellOverlays;

/// Drives the solver on behalf of [ServicesModule]: solve everything, autofill
/// a date window, autofill one service, abort a running solve, and report the
/// resulting score.
///
/// Split out of the module because none of it is about presenting a table of
/// services - it owns the running solver job and the write-back of a solution,
/// which is a separate reason to change from "how the Services screen looks".
/// The module keeps the buttons; this keeps what they do.
///
/// The three entry points differ only in how the problem is scoped and how long
/// it may run, so they all funnel into one [#launch] - previously three
/// near-identical copies of the same solveAsync call.
///
/// Everything here runs on the FX thread: the solver's callbacks arrive on its
/// own threads and are hopped over with `Platform.runLater` before they touch
/// any of the state below.
final class ServicesSolverController {

    private static final Logger LOGGER = LoggerFactory.getLogger(ServicesSolverController.class);

    /// Per-service autofill is an interactive, one-row action, so it gets a
    /// short fixed budget rather than the configured full-solve one.
    private static final Duration AUTO_FILL_TIME_BUDGET = Duration.ofSeconds(5);

    private final PlanningViewModel planningViewModel;
    private final Supplier<List<LiturgicalService>> liveServices;
    private final Consumer<List<LiturgicalService>> applySolved;
    private final ShellOverlays overlays;

    private @Nullable UUID jobId;

    /// @param liveServices the services currently in the module's store
    /// @param applySolved  stages solved services back into that store
    /// @param overlays     the window's dialog layer, for the abort prompt
    ServicesSolverController(PlanningViewModel planningViewModel,
                             Supplier<List<LiturgicalService>> liveServices,
                             Consumer<List<LiturgicalService>> applySolved,
                             ShellOverlays overlays) {
        this.planningViewModel = planningViewModel;
        this.liveServices = liveServices;
        this.applySolved = applySolved;
        this.overlays = overlays;
    }

    /// Solves every open slot in the document.
    void solveAll() {
        List<LiturgicalService> services = List.copyOf(liveServices.get());
        ServicePlan problem = planningViewModel.buildProblem();
        if (problem.getAssignments().isEmpty()) {
            return;
        }
        launch(problem, null, null, services);
    }

    /// Solves the slots falling in `[from, to]`, either bound `null` meaning
    /// unbounded on that side. `overwrite` also re-assigns slots that are
    /// already filled.
    void autofillWindow(@Nullable LocalDate from, @Nullable LocalDate to, boolean overwrite) {
        List<LiturgicalService> services = List.copyOf(liveServices.get());
        ServicePlan problem = planningViewModel.buildProblem();
        Autofill.Scope scope = planningViewModel.beginWindowAutofill(problem, from, to, overwrite);
        if (scope.eligibleIds().isEmpty()) {
            LOGGER.info(Localization.lang("Nothing to autofill"));
            return;
        }
        launch(problem, scope, null, services);
    }

    /// Solves only `service`'s open slots: every other slot is pinned for the
    /// duration of the solve, then restored afterward (see
    /// [PlanningViewModel#beginServiceAutofill]).
    void autofillService(LiturgicalService service) {
        if (isSolving()) {
            return;
        }
        ServicePlan problem = planningViewModel.buildProblem();
        Autofill.Scope scope = planningViewModel.beginServiceAutofill(problem, service.id());
        if (scope.eligibleIds().isEmpty()) {
            return;
        }
        launch(problem, scope, AUTO_FILL_TIME_BUDGET, List.of(service));
    }

    /// Starts the solver and wires its three callbacks. A non-null `scope`
    /// means the problem was narrowed by an autofill and has to be unpinned
    /// again when it finishes; a non-null `timeBudget` overrides the configured
    /// one.
    private void launch(ServicePlan problem, Autofill.@Nullable Scope scope,
                        @Nullable Duration timeBudget, List<LiturgicalService> services) {
        planningViewModel.updateProgress(problem);
        planningViewModel.beginSolve();
        LOGGER.info(Localization.lang("Solving..."));
        Consumer<ServicePlan> onBest = best -> Platform.runLater(() -> planningViewModel.updateProgress(best));
        Consumer<ServicePlan> onFinal = finalBest -> Platform.runLater(() -> {
            if (scope != null) {
                planningViewModel.finishAutofill(finalBest, scope);
            }
            applySolution(finalBest, services);
            planningViewModel.finishSolve();
            LOGGER.info(Localization.lang("Solving finished"));
        });
        Consumer<Throwable> onError = error -> Platform.runLater(() -> {
            planningViewModel.failSolve();
            LOGGER.error(Localization.lang("Solving failed: %0", error.getMessage()), error);
        });
        jobId = timeBudget == null
                ? planningViewModel.solveAsync(problem, onBest, onFinal, onError)
                : planningViewModel.solveAsync(problem, timeBudget, onBest, onFinal, onError);
    }

    /// Writes `solved`'s assignments back onto `services` and stages the
    /// updated records into the live store (saving the document persists them).
    private void applySolution(ServicePlan solved, List<LiturgicalService> services) {
        applySolved.accept(planningViewModel.writeBack(solved, services));
        refreshScore();
    }

    private boolean isSolving() {
        return planningViewModel.solvingProperty().get();
    }

    private void stop() {
        if (jobId != null) {
            planningViewModel.stopSolving(jobId);
        }
    }

    /// Confirms aborting the running solve. The prompt auto-dismisses if the
    /// solve finishes on its own before the user answers (the toolbar returns
    /// to its Autofill button on its own via the `solving` bindings), so a
    /// just-completed solve is never cancelled by a stale click.
    ///
    /// Non-blocking, unlike the `Alert` it replaces: the in-window dialog
    /// answers through a callback, so the decision is applied there rather than
    /// after a `showAndWait` returns. The auto-dismiss listener is detached on
    /// both exits - the user answering, and the solve finishing first.
    void confirmAbort() {
        if (!isSolving()) {
            return;
        }
        Dialog<ButtonType> confirm = overlays.dialogs().showConfirmation(
                Localization.lang("Abort autofill"),
                Localization.lang("Really abort the running autofill?"));
        ChangeListener<Boolean> autoDismiss = (obs, wasSolving, stillSolving) -> {
            if (!stillSolving) {
                confirm.cancel();
            }
        };
        planningViewModel.solvingProperty().addListener(autoDismiss);
        confirm.onClose(answer -> {
            planningViewModel.solvingProperty().removeListener(autoDismiss);
            // Re-checked rather than trusted: the solve may have finished
            // between the click and this callback.
            if (answer != null && answer.getButtonData() == ButtonData.OK_DONE && isSolving()) {
                stop();
            }
        });
    }

    /// Recomputes and logs the current plan's score. Called after a solve and
    /// after a manual assignment edit.
    void refreshScore() {
        ServicePlan plan = planningViewModel.buildProblem();
        if (plan.getAssignments().isEmpty()) {
            return;
        }
        logScore(planningViewModel.scoreOf(plan));
    }

    /// The score is solver-internal detail, logged rather than shown
    /// permanently in the toolbar.
    private static void logScore(@Nullable HardMediumSoftScore score) {
        if (score == null) {
            return;
        }
        String feasibility = score.hardScore() == 0 && score.mediumScore() == 0
                ? Localization.lang("Feasible")
                : Localization.lang("Has violations");
        LOGGER.info("{}: {} ({})", Localization.lang("Score"), score, feasibility);
    }
}
