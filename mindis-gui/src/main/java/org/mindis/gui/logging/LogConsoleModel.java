package org.mindis.gui.logging;

import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;

/// Rolling history of log records for the in-app error console (see {@code
/// AboutModule}) - a bounded, observable list {@link LogConsoleHandler}
/// appends to and the console UI binds to directly. One instance, constructed
/// once in {@code MinDisApp} and shared between the handler (registered on
/// the root JUL logger) and the console view.
public final class LogConsoleModel {

    /// Newest-first cap - older entries are dropped rather than growing unbounded for the life of the process.
    private static final int MAX_ENTRIES = 500;

    private final ObservableList<LogEntry> entries = FXCollections.observableArrayList();

    /// Newest-first. Bind UI to this directly; only ever mutated on the FX thread.
    public ObservableList<LogEntry> entries() {
        return entries;
    }

    /// Safe to call from any thread - log calls can come from anywhere.
    void append(LogEntry entry) {
        Platform.runLater(() -> {
            entries.add(0, entry);
            if (entries.size() > MAX_ENTRIES) {
                entries.remove(entries.size() - 1);
            }
        });
    }
}
