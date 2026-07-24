package org.mindis.core.persistence;

import jakarta.inject.Singleton;

import java.io.IOException;
import java.nio.file.Path;

import org.jspecify.annotations.Nullable;

import org.mindis.core.model.CollectionMeta;

/// The open document: aggregates the five repositories, whose caches are the
/// single source of truth every reader (GUI stores, solver, CSV mappers) sees
/// live, and owns the file they came from.
///
/// <p>All data lives in one user-chosen JSON file (see {@link MinDisDocument}),
/// not in per-entity files under a fixed data directory - so the document
/// actions <em>are</em> the file actions: {@link #newDocument()},
/// {@link #open(Path)}, {@link #save()}, {@link #saveAs(Path)} and
/// {@link #reload()}. These are the only disk-I/O entry points for entity data;
/// every repository mutation stages in memory until a save. Assignments are
/// part of the service records (see {@link org.mindis.core.model.Slot}) and the
/// archive is part of the document, so one save covers everything.
///
/// <p>A new document has no path ("untitled") until it is saved somewhere:
/// {@link #save()} on such a document is a programming error - the caller must
/// route it to {@link #saveAs(Path)}, which is what the GUI does.
@Singleton
public class AppDatabase {

    private final RoleRepository roles;
    private final ServerRepository servers;
    private final TemplateRepository templates;
    private final ServiceRepository services;
    private final ArchivedServiceRepository archived;
    private final DocumentStore store = new DocumentStore();

    private @Nullable Path documentPath;
    private CollectionMeta meta = CollectionMeta.empty();

    public AppDatabase(RoleRepository roles, ServerRepository servers,
                       TemplateRepository templates, ServiceRepository services,
                       ArchivedServiceRepository archived) {
        this.roles = roles;
        this.servers = servers;
        this.templates = templates;
        this.services = services;
        this.archived = archived;
    }

    /// The file the open document was loaded from or last saved to;
    /// {@code null} for a new, never-saved document.
    public synchronized @Nullable Path documentPath() {
        return documentPath;
    }

    /// The open document's identity (name + logo); never {@code null} - a new
    /// or metadata-less document reports {@link CollectionMeta#empty()}.
    public synchronized CollectionMeta meta() {
        return meta;
    }

    /// Replaces the open document's identity; staged like any other edit until
    /// the next save.
    public synchronized void updateMeta(CollectionMeta newMeta) {
        this.meta = newMeta;
    }

    /// Replaces the open document with an empty, untitled one, seeded with the
    /// built-in default roles so it is usable out of the box.
    public synchronized void newDocument() {
        apply(MinDisDocument.empty(), null);
        roles.replaceAll(RoleRepository.defaults());
    }

    /// Opens {@code file} and replaces the open document with its content.
    /// Staged edits of the previous document are discarded - the caller is
    /// responsible for asking the user first.
    ///
    /// @throws IOException if the file cannot be read or is not a MinDis
    ///         document. Deliberately not swallowed into an empty document: the
    ///         user picked this exact file and must learn that it did not open.
    public synchronized void open(Path file) throws IOException {
        apply(store.read(file), file);
    }

    /// Writes the open document back to its own file.
    ///
    /// @throws IllegalStateException if the document has never been saved (no
    ///         path yet) - use {@link #saveAs(Path)}
    public synchronized void save() throws IOException {
        if (documentPath == null) {
            throw new IllegalStateException("Untitled document: use saveAs(Path)");
        }
        store.write(documentPath, snapshot());
        archived.markSaved();
    }

    /// Writes the open document to {@code file} and makes that its file from now on.
    public synchronized void saveAs(Path file) throws IOException {
        store.write(file, snapshot());
        documentPath = file;
        archived.markSaved();
    }

    /// Discards every staged edit by re-reading the document from disk; an
    /// untitled document resets to a new one.
    public synchronized void reload() throws IOException {
        if (documentPath == null) {
            newDocument();
            return;
        }
        open(documentPath);
    }

    /// The open document's current (staged) state, as it would be written.
    public synchronized MinDisDocument snapshot() {
        return new MinDisDocument(MinDisDocument.CURRENT_VERSION, meta,
                roles.findAll(), servers.findAll(), templates.findAll(),
                services.findAll(), archived.findAll());
    }

    private void apply(MinDisDocument document, @Nullable Path path) {
        roles.replaceAll(document.roles());
        servers.replaceAll(document.servers());
        templates.replaceAll(document.templates());
        services.replaceAll(document.services());
        archived.replaceAll(document.archivedServices());
        meta = document.meta();
        documentPath = path;
    }
}
