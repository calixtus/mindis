package org.mindis.gui.update;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Optional;
import java.util.function.BooleanSupplier;

import javafx.application.HostServices;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Hyperlink;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.layout.VBox;

import com.dlsc.gemsfx.DialogPane;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.mindis.core.l10n.Localization;
import org.mindis.core.update.AvailableUpdate;
import org.mindis.core.update.UpdateService;
import org.mindis.gui.shell.ShellOverlays;

/// Drives the update flow the user sees: check, offer, download, install
/// (docs/adr/010-auto-update.md).
///
/// <p>Every network call runs on a virtual thread and hops back with
/// [Platform#runLater] before touching a control, so neither the check on
/// startup nor a download blocks the UI.
///
/// <p>Two entry points, differing only in how quiet they are: the check on
/// startup says nothing unless there is an update ([#checkOnStartup]),
/// the one the user asked for always answers ([#checkNow]).
public final class UpdateCheckController {

    private static final Logger LOGGER = LoggerFactory.getLogger(UpdateCheckController.class);

    private final UpdateService updateService;
    private final ShellOverlays overlays;
    private final HostServices hostServices;
    private final BooleanSupplier confirmQuit;

    /// @param confirmQuit asked before MinDis quits to let the installer run:
    ///        the same "save your unsaved edits?" prompt the window's close
    ///        button goes through. `false` keeps the application open and
    ///        leaves the downloaded installer untouched.
    public UpdateCheckController(UpdateService updateService, ShellOverlays overlays,
                                 HostServices hostServices, BooleanSupplier confirmQuit) {
        this.updateService = updateService;
        this.overlays = overlays;
        this.hostServices = hostServices;
        this.confirmQuit = confirmQuit;
    }

    /// Silent check: a newer release opens the offer, everything else - up to
    /// date, no network, an unreachable server - only reaches the log. A
    /// planner opening MinDis to print a plan must not be greeted by an error
    /// about a check they never asked for.
    public void checkOnStartup() {
        check(false);
    }

    /// The check behind Settings' "Check for updates now": reports being up to
    /// date and reports failures, because the user is waiting for an answer.
    public void checkNow() {
        check(true);
    }

    private void check(boolean reportOutcome) {
        if (!updateService.isSupported()) {
            LOGGER.debug("Update check not supported for this build");
            if (reportOutcome) {
                overlays.dialogs().showInformation(
                        Localization.lang("Check for updates"),
                        Localization.lang("This build does not check for updates."));
            }
            return;
        }
        runAsync(() -> {
            try {
                Optional<AvailableUpdate> update = updateService.check();
                Platform.runLater(() -> {
                    if (update.isPresent()) {
                        offer(update.get());
                    } else if (reportOutcome) {
                        overlays.dialogs().showInformation(
                                Localization.lang("Check for updates"),
                                Localization.lang("MinDis is up to date."));
                    }
                });
            } catch (IOException e) {
                LOGGER.warn("Update check failed", e);
                if (reportOutcome) {
                    Platform.runLater(() -> overlays.dialogs().showError(
                            Localization.lang("Check for updates"),
                            Localization.lang("Could not reach the update server."),
                            e));
                }
            }
        });
    }

    /// "Version X is available" - with the release notes one click away, since
    /// that is what tells the user whether they want it at all.
    private void offer(AvailableUpdate update) {
        VBox content = new VBox(8, new Label(Localization.lang(
                "Version %0 is available. You have %1.",
                update.version().toString(), update.runningVersion().toString())));
        String notes = update.releaseNotesUrl();
        if (notes != null && !notes.isBlank()) {
            Hyperlink notesLink = new Hyperlink(Localization.lang("What's new"));
            notesLink.setOnAction(event -> hostServices.showDocument(notes));
            content.getChildren().add(notesLink);
        }
        content.getChildren().add(new Label(Localization.lang(
                "MinDis downloads the installer and starts it; MinDis then closes.")));
        content.setPadding(new Insets(0, 0, 4, 0));

        ButtonType install = new ButtonType(
                Localization.lang("Download and install"), ButtonBar.ButtonData.OK_DONE);
        ButtonType later = new ButtonType(
                Localization.lang("Later"), ButtonBar.ButtonData.CANCEL_CLOSE);
        DialogPane.Dialog<ButtonType> dialog = overlays.dialogs().showNode(
                DialogPane.Type.INFORMATION,
                Localization.lang("Update available"),
                content,
                java.util.List.of(install, later));
        dialog.onClose(answer -> {
            if (answer == install) {
                download(update);
            }
        });
    }

    /// Downloads with a progress bar, then offers to quit into the installer.
    /// The dialog is not cancellable on purpose: a half-written installer is
    /// worse than waiting, and the file lands in a temporary directory that
    /// the system clears anyway.
    private void download(AvailableUpdate update) {
        ProgressBar progressBar = new ProgressBar();
        progressBar.setPrefWidth(320);
        Label status = new Label(Localization.lang("Downloading %0…", update.installer().fileName()));
        VBox content = new VBox(8, status, progressBar);
        DialogPane.Dialog<ButtonType> progressDialog = overlays.dialogs().showNode(
                DialogPane.Type.BLANK,
                Localization.lang("Update available"),
                content,
                java.util.List.of());

        long total = update.installer().size();
        runAsync(() -> {
            try {
                Path installer = updateService.download(update.installer(), received -> {
                    if (total > 0) {
                        Platform.runLater(() -> progressBar.setProgress((double) received / total));
                    }
                });
                Platform.runLater(() -> {
                    overlays.dialogs().hideDialog(progressDialog);
                    confirmInstall(installer);
                });
            } catch (IOException e) {
                LOGGER.warn("Update download failed", e);
                Platform.runLater(() -> {
                    overlays.dialogs().hideDialog(progressDialog);
                    overlays.dialogs().showError(
                            Localization.lang("Update available"),
                            Localization.lang("Could not download the update."),
                            e);
                });
            }
        });
    }

    /// Last stop before quitting: unsaved edits are asked about first (through
    /// `confirmQuit`), then the installer is started and MinDis exits -
    /// it cannot replace files that are still in use.
    private void confirmInstall(Path installer) {
        ButtonType quit = new ButtonType(
                Localization.lang("Install and quit"), ButtonBar.ButtonData.OK_DONE);
        ButtonType later = new ButtonType(
                Localization.lang("Later"), ButtonBar.ButtonData.CANCEL_CLOSE);
        overlays.dialogs().showNode(
                        DialogPane.Type.CONFIRMATION,
                        Localization.lang("Update available"),
                        new Label(Localization.lang(
                                "The update is ready. MinDis closes and the installer takes over.")),
                        java.util.List.of(quit, later))
                .onClose(answer -> {
                    if (answer == quit) {
                        startInstaller(installer);
                    }
                });
    }

    private void startInstaller(Path installer) {
        if (!confirmQuit.getAsBoolean()) {
            return;
        }
        try {
            updateService.install(installer);
        } catch (IOException e) {
            LOGGER.warn("Could not start the installer", e);
            overlays.dialogs().showError(
                    Localization.lang("Update available"),
                    Localization.lang("Could not start the installer: %0", installer.toString()),
                    e);
            return;
        }
        Platform.exit();
    }

    /// One virtual thread per call: the check happens at most twice a session,
    /// so an executor to own (and shut down) would be more machinery than the
    /// job needs.
    private static void runAsync(Runnable work) {
        Thread.ofVirtual().name("mindis-update-check").start(work);
    }
}
