package org.mindis.core.persistence;

import jakarta.inject.Singleton;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import org.mindis.core.model.ArchivedService;

/// The frozen {@link ArchivedService} snapshots of the currently open document.
/// Entries are only ever appended, browsed or deleted, never edited - each
/// snapshot is self-contained (see {@link ArchivedService}) so it stays
/// faithful regardless of later roster changes.
///
/// <p>The archive is part of the document, so - unlike when it lived in its own
/// always-written file - archiving and deleting stage in memory and reach disk
/// with the next save. {@link #isDirty()} reports whether such a staged change
/// exists, since archive edits are not covered by the GUI's per-row dirty
/// tracking; listeners registered through {@link #addChangeListener} fire on
/// every mutation so the UI can rebind.
@Singleton
public class ArchivedServiceRepository {

    private final List<ArchivedService> archived = new ArrayList<>();
    private final List<Runnable> listeners = new CopyOnWriteArrayList<>();
    private boolean dirty;

    /// Every archived service, newest first.
    public synchronized List<ArchivedService> findAll() {
        List<ArchivedService> all = new ArrayList<>(archived);
        all.sort(Comparator.comparing(ArchivedService::dateTime).reversed());
        return all;
    }

    /// Appends {@code services}. No-op for an empty list.
    public void addAll(List<ArchivedService> services) {
        synchronized (this) {
            if (services.isEmpty()) {
                return;
            }
            archived.addAll(services);
            dirty = true;
        }
        notifyListeners();
    }

    /// Removes the archived service with {@code id} (retention / cleanup).
    public void delete(String id) {
        synchronized (this) {
            if (!archived.removeIf(service -> service.id().equals(id))) {
                return;
            }
            dirty = true;
        }
        notifyListeners();
    }

    /// Whether the archive holds changes that have not been saved to the
    /// document yet.
    public synchronized boolean isDirty() {
        return dirty;
    }

    /// Replaces the whole content with a freshly opened document's archive and
    /// clears the dirty flag. Only {@link AppDatabase} calls this.
    void replaceAll(List<ArchivedService> services) {
        synchronized (this) {
            archived.clear();
            archived.addAll(services);
            dirty = false;
        }
        notifyListeners();
    }

    /// Marks the current content as saved. Only {@link AppDatabase} calls this.
    synchronized void markSaved() {
        dirty = false;
    }

    public void addChangeListener(Runnable listener) {
        listeners.add(listener);
    }

    public void removeChangeListener(Runnable listener) {
        listeners.remove(listener);
    }

    private void notifyListeners() {
        listeners.forEach(Runnable::run);
    }
}
