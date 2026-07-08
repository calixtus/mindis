package org.mindis.gui.logging;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.text.MessageFormat;
import java.util.logging.LogRecord;

import org.jspecify.annotations.Nullable;

/** {@link LogRecord} formatting shared by {@link AlertOnErrorHandler} and {@link LogConsoleHandler}. */
final class LogMessages {

    private LogMessages() {
    }

    static String resolveMessage(LogRecord record) {
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

    static @Nullable String stackTraceOf(@Nullable Throwable thrown) {
        if (thrown == null) {
            return null;
        }
        StringWriter trace = new StringWriter();
        thrown.printStackTrace(new PrintWriter(trace));
        return trace.toString();
    }
}
