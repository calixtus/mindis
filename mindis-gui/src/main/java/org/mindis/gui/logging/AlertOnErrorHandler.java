package org.mindis.gui.logging;

import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;

import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.scene.control.Alert;
import javafx.scene.control.Alert.AlertType;
import javafx.scene.control.TextArea;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

import org.jspecify.annotations.Nullable;
import org.mindis.core.l10n.Localization;

/// Surfaces every {@link Level#SEVERE} log record from MinDis's own code as an
/// error dialog, in addition to whatever {@link org.mindis.core.logging.LoggingBootstrap}
/// already writes to console/file (every record, not just SEVERE, and not
/// just MinDis's own, still reaches {@link LogConsoleHandler}'s in-app
/// history). Restricted to {@code org.mindis} logger names - third-party code
/// bridged through SLF4J (avaje-inject, etc.) can log at SEVERE for its own
/// internal, often-recoverable reasons; popping a user-facing dialog for
/// those isn't a real error the user caused or needs to act on, just noise.
///
/// <p>Content is a plain, non-editable {@link TextArea} rather than
/// {@link Alert#setContentText}, whose text is a {@code Label} - not
/// selectable, so a user hitting an error dialog can't copy it into a bug
/// report at all.
///
/// <p>Log calls can come from any thread, so the dialog is always shown via
/// {@link Platform#runLater}.
public final class AlertOnErrorHandler extends Handler {

    public AlertOnErrorHandler() {
        setLevel(Level.SEVERE);
    }

    @Override
    public void publish(LogRecord record) {
        if (!isLoggable(record) || !isFromMinDis(record)) {
            return;
        }
        String message = LogMessages.resolveMessage(record);
        @Nullable String stackTrace = LogMessages.stackTraceOf(record.getThrown());
        Platform.runLater(() -> {
            Alert alert = new Alert(AlertType.ERROR);
            alert.setTitle("MinDis");
            alert.setHeaderText(Localization.lang("Unexpected error"));

            TextArea details = new TextArea(stackTrace != null ? message + "\n\n" + stackTrace : message);
            details.setEditable(false);
            details.setWrapText(true);
            details.setPrefRowCount(14);
            details.setPrefColumnCount(70);
            VBox.setVgrow(details, Priority.ALWAYS);

            VBox content = new VBox(details);
            content.setPadding(new Insets(4, 0, 0, 0));
            alert.getDialogPane().setContent(content);
            alert.setResizable(true);
            alert.showAndWait();
        });
    }

    private static boolean isFromMinDis(LogRecord record) {
        String loggerName = record.getLoggerName();
        return loggerName != null && loggerName.startsWith("org.mindis");
    }

    @Override
    public void flush() {
    }

    @Override
    public void close() {
    }
}
