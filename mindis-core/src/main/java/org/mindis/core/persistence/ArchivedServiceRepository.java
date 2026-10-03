package org.mindis.core.persistence;

import jakarta.inject.Singleton;

import java.util.Comparator;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import org.mindis.core.model.ArchivedService;

/// The frozen [ArchivedService] snapshots of the currently open document.
/// Entries are only ever appended, browsed or deleted, never edited - each
/// snapshot is self-contained (see [ArchivedService]) so it stays
/// faithful regardless of later roster changes.
///
/// <p>The archive is part of the document, so - unlike when it lived in its own
/// always-written file - archiving and deleting stage in memory and reach disk
/// with the next save. [#isDirty()] reports whether such a staged change
/// exists, since archive edits are not covered by the GUI's per-row dirty
/// tracking; listeners registered through [#addChangeListener] fire on
/// every mutation so the UI can rebind.
///
/// <p>Storage and order are an [InMemoryRepository]'s, held rather than
/// extended: that class is built to be extended without overriding anything,
/// and the archive has to act on every mutation.
@Singleton
public final class ArchivedServiceRepository {

    private final InMemoryRepository<ArchivedService, String> archived = new InMemoryRepository<>(
            ArchivedService::id, Comparator.comparing(ArchivedService::dateTime).reversed()) {
    };
    private final List<Runnable> listeners = new CopyOnWriteArrayList<>();
    private boolean dirty;

    /// Every archived service, newest first. Locked on the archive, not just the
    /// store, so a reader never sees half of an [#addAll(List)] batch.
    public synchronized List<ArchivedService> findAll() {
        return archived.findAll();
    }

    /// Appends `services`. No-op for an empty list.
    public void addAll(List<ArchivedService> services) {
        synchronized (this) {
            if (services.isEmpty()) {
                return;
            }
            services.forEach(archived::save);
            dirty = true;
        }
        notifyListeners();
    }

    /// Removes the archived service with `id` (retention / cleanup).
    public void delete(String id) {
        synchronized (this) {
            if (archived.findById(id).isEmpty()) {
                return;
            }
            archived.delete(id);
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
    /// clears the dirty flag. Only [AppDatabase] calls this.
    void replaceAll(List<ArchivedService> services) {
        synchronized (this) {
            archived.replaceAll(services);
            dirty = false;
        }
        notifyListeners();
    }

    /// Marks the current content as saved. Only [AppDatabase] calls this.
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
