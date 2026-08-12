package org.mindis.core.logging;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.logging.ConsoleHandler;
import java.util.logging.FileHandler;
import java.util.logging.Formatter;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

import org.mindis.core.preferences.AppDirectories;

/// Wires the root [Logger] to log to both the console and a rotating
/// file under the per-user data directory. Call once, as early as possible in
/// application startup (PLAN.md litmus test: a future CLI against
/// `mindis-core` alone gets the same console+file logging for free).
public final class LoggingBootstrap {

    private static final int MAX_FILE_BYTES = 5 * 1024 * 1024;
    private static final int MAX_FILE_COUNT = 5;

    private LoggingBootstrap() {
    }

    public static void configure() {
        Logger root = Logger.getLogger("");
        for (Handler handler : root.getHandlers()) {
            root.removeHandler(handler);
        }
        root.setLevel(Level.INFO);

        Formatter formatter = new LineFormatter();

        ConsoleHandler console = new ConsoleHandler();
        console.setFormatter(formatter);
        console.setLevel(Level.INFO);
        root.addHandler(console);

        try {
            Path logDir = AppDirectories.userDataDir().resolve("logs");
            Files.createDirectories(logDir);
            FileHandler file = new FileHandler(
                    logDir.resolve("mindis-%g.log").toString(), MAX_FILE_BYTES, MAX_FILE_COUNT, true);
            file.setFormatter(formatter);
            file.setLevel(Level.INFO);
            root.addHandler(file);
        } catch (IOException e) {
            // Logging setup must never block the app from starting.
            root.log(Level.WARNING, "Could not set up log file, continuing with console logging only", e);
        }

        silenceKnownHarmlessLoggers();
    }

    /// PDFBox tries to reach `jdk.internal.ref.Cleaner` through
    /// `java.nio` internals to unmap memory-mapped buffers, which no module on
    /// a module path is allowed to do, and reports the refusal at SEVERE the
    /// first time a document is created. mindis only ever writes PDFs, so
    /// nothing is actually degraded - but an untouched SEVERE with a stack
    /// trace lands in the log file and the in-app log console, where it reads
    /// like a real failure. Opening the JDK internals to it instead would buy
    /// a capability we do not use with access that the next JDK may withdraw.
    private static void silenceKnownHarmlessLoggers() {
        Logger.getLogger("org.apache.pdfbox.io.IOUtils").setLevel(Level.OFF);
    }

    private static final class LineFormatter extends Formatter {
        @Override
        public String format(LogRecord record) {
            StringBuilder line = new StringBuilder()
                    .append(Instant.ofEpochMilli(record.getMillis()))
                    .append(" [").append(record.getLevel()).append("] ")
                    .append(record.getLoggerName()).append(" - ")
                    .append(formatMessage(record))
                    .append(System.lineSeparator());
            if (record.getThrown() != null) {
                StringWriter trace = new StringWriter();
                record.getThrown().printStackTrace(new PrintWriter(trace));
                line.append(trace);
            }
            return line.toString();
        }
    }
}
