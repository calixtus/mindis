package org.mindis.gui.logging;

import java.time.Instant;
import java.util.logging.Level;

import org.jspecify.annotations.Nullable;

/// One row in the in-app error console (see {@link LogConsoleModel}).
public record LogEntry(Instant time, Level level, String loggerName, String message, @Nullable String stackTrace) {
}
