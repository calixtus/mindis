package org.mindis.gui.logging;

import java.text.MessageFormat;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;

import javafx.application.Platform;
import javafx.scene.control.Alert;
import javafx.scene.control.Alert.AlertType;

import org.mindis.core.l10n.Localization;

/**
 * Surfaces every {@link Level#SEVERE} log record as an error dialog, in
 * addition to whatever {@link org.mindis.core.logging.LoggingBootstrap}
 * already writes to console/file. Log calls can come from any thread, so the
 * dialog is always shown via {@link Platform#runLater}.
 */
public final class AlertOnErrorHandler extends Handler {

    public AlertOnErrorHandler() {
        setLevel(Level.SEVERE);
    }

    @Override
    public void publish(LogRecord record) {
        if (!isLoggable(record)) {
            return;
        }
        String message = resolveMessage(record);
        Throwable thrown = record.getThrown();
        Platform.runLater(() -> {
            Alert alert = new Alert(AlertType.ERROR);
            alert.setTitle("MinDis");
            alert.setHeaderText(Localization.lang("Unexpected error"));
            alert.setContentText(thrown != null ? message + "\n\n" + thrown : message);
            alert.showAndWait();
        });
    }

    private static String resolveMessage(LogRecord record) {
        String raw = record.getMessage();
        if (raw == null) {
            return record.getLoggerName();
        }
        Object[] params = record.getParameters();
        if (params != null && params.length > 0 && raw.contains("{0}")) {
            return MessageFormat.format(raw, params);
        }
        return raw;
    }

    @Override
    public void flush() {
    }

    @Override
    public void close() {
    }
}
