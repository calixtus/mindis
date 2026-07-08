package org.mindis.gui.logging;

import java.time.Instant;
import java.util.logging.Handler;
import java.util.logging.LogRecord;

/**
 * Feeds every log record the root logger receives (down to whatever level
 * {@code LoggingBootstrap} set there - INFO and up today) into a
 * {@link LogConsoleModel}, for the in-app error console. Registered directly
 * on the root logger (like {@link AlertOnErrorHandler}), not through DI - it
 * needs to be listening from the moment logging starts, for the life of the
 * process.
 */
public final class LogConsoleHandler extends Handler {

    private final LogConsoleModel model;

    public LogConsoleHandler(LogConsoleModel model) {
        this.model = model;
    }

    @Override
    public void publish(LogRecord record) {
        if (!isLoggable(record)) {
            return;
        }
        model.append(new LogEntry(
                Instant.ofEpochMilli(record.getMillis()),
                record.getLevel(),
                record.getLoggerName(),
                LogMessages.resolveMessage(record),
                LogMessages.stackTraceOf(record.getThrown())));
    }

    @Override
    public void flush() {
    }

    @Override
    public void close() {
    }
}
