package org.mindis.gui;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

import javafx.application.Platform;
import javafx.beans.binding.Bindings;
import javafx.beans.binding.BooleanBinding;
import javafx.beans.binding.IntegerExpression;
import javafx.beans.binding.NumberBinding;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.ReadOnlyBooleanProperty;
import javafx.beans.property.ReadOnlyObjectProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleObjectProperty;

import org.jspecify.annotations.Nullable;

import org.mindis.core.model.CollectionMeta;
import org.mindis.core.model.LiturgicalService;
import org.mindis.core.model.Role;
import org.mindis.core.model.RoleSlot;
import org.mindis.core.model.Server;
import org.mindis.core.model.ServiceTemplate;
import org.mindis.core.model.Slot;
import org.mindis.core.persistence.AppDatabase;
import org.mindis.core.persistence.ArchivedServiceRepository;
import org.mindis.core.persistence.RoleRepository;
import org.mindis.core.persistence.ServerRepository;
import org.mindis.core.persistence.ServiceRepository;
import org.mindis.core.persistence.TemplateRepository;
import org.mindis.workbench.LiveStore;

/// The GUI's view of the open document: one long-lived {@link LiveStore} per
/// entity type (write-through mirrors of the {@link AppDatabase} repositories),
/// plus the document actions. Constructed once in {@code MinDisApp.start()} and
/// reused across UI rebuilds, so unsaved cross-module edits and dirty counts
/// survive a language switch.
///
/// <p>Every document action re-mirrors and re-baselines all stores afterwards,
/// so an open or a new document resets the dirty state and a save clears it.
/// {@link #dirty()} is the one "has unsaved work" signal the toolbar, the
/// window title and the close guard all bind to: the per-row dirty counts of
/// the four live stores plus the archive's own staged-change flag, which no
/// row-level tracking covers.
public final class LiveDatabase {

    private final AppDatabase database;
    private final ArchivedServiceRepository archivedServiceRepository;
    private final LiveStore<Role> roles;
    private final LiveStore<Server> servers;
    private final LiveStore<ServiceTemplate> templates;
    private final LiveStore<LiturgicalService> services;

    private final ObjectProperty<@Nullable Path> documentPath = new SimpleObjectProperty<>();
    private final ObjectProperty<CollectionMeta> meta = new SimpleObjectProperty<>(CollectionMeta.empty());
    private final BooleanProperty archiveDirty = new SimpleBooleanProperty(false);
    // The collection identity (name/logo) is not a row of any LiveStore, so its
    // edits need their own staged-change flag, mirrored into the dirty signal.
    private final BooleanProperty metaDirty = new SimpleBooleanProperty(false);
    private final BooleanBinding dirty;

    public LiveDatabase(AppDatabase database, RoleRepository roleRepository,
                        ServerRepository serverRepository, TemplateRepository templateRepository,
                        ServiceRepository serviceRepository,
                        ArchivedServiceRepository archivedServiceRepository) {
        this.database = database;
        this.archivedServiceRepository = archivedServiceRepository;
        this.roles = new LiveStore<>(roleRepository::findAll, roleRepository::save,
                role -> roleRepository.delete(role.id()), Role::id, Objects::equals);
        this.servers = new LiveStore<>(serverRepository::findAll, serverRepository::save,
                server -> serverRepository.delete(server.id()), Server::id, Objects::equals);
        // Slot lists compare order-insensitively for dirty tracking - a
        // reordered-but-identical slot list is not an unsaved change.
        this.templates = new LiveStore<>(templateRepository::findAll, templateRepository::save,
                template -> templateRepository.delete(template.id()), ServiceTemplate::id,
                LiveDatabase::sameTemplate);
        this.services = new LiveStore<>(serviceRepository::findAll, serviceRepository::save,
                service -> serviceRepository.delete(service.id()), LiturgicalService::id,
                LiveDatabase::sameService);
        // Archiving and deleting an archived plan stage in the document like
        // any other edit, but they are not rows of any LiveStore - so the
        // repository's own flag is mirrored into a property here. The
        // repository notifies from whatever thread archived; hop to the FX
        // thread before touching a property the UI binds to.
        this.archivedServiceRepository.addChangeListener(() -> Platform.runLater(this::syncArchiveDirty));
        this.dirty = Bindings.notEqual(0, totalDirtyCount()).or(archiveDirty).or(metaDirty);
    }

    public LiveStore<Role> roles() {
        return roles;
    }

    public LiveStore<Server> servers() {
        return servers;
    }

    public LiveStore<ServiceTemplate> templates() {
        return templates;
    }

    public LiveStore<LiturgicalService> services() {
        return services;
    }

    // --- Document state ---

    /// The open document's file; empty for a new, never-saved one.
    public ReadOnlyObjectProperty<@Nullable Path> documentPathProperty() {
        return documentPath;
    }

    public Optional<Path> documentPath() {
        return Optional.ofNullable(documentPath.get());
    }

    /// The open collection's identity (name + logo); never {@code null}.
    public ReadOnlyObjectProperty<CollectionMeta> metaProperty() {
        return meta;
    }

    public CollectionMeta meta() {
        return meta.get();
    }

    /// Replaces the open collection's identity, staging it like any other edit
    /// (so the document goes dirty and a save writes it).
    public void updateMeta(CollectionMeta newMeta) {
        database.updateMeta(newMeta);
        meta.set(newMeta);
        metaDirty.set(true);
    }

    /// Whether the open document holds edits that have not been saved yet.
    public BooleanBinding dirtyProperty() {
        return dirty;
    }

    public boolean isDirty() {
        return dirty.get();
    }

    /// Whether the archive holds staged changes - part of {@link #dirtyProperty()}.
    public ReadOnlyBooleanProperty archiveDirtyProperty() {
        return archiveDirty;
    }

    // --- Document actions ---

    /// Replaces the open document with an empty untitled one (default roles seeded).
    public void newDocument() {
        database.newDocument();
        afterDocumentChange();
    }

    /// Opens {@code file}; staged edits of the previous document are discarded.
    public void open(Path file) throws IOException {
        database.open(file);
        afterDocumentChange();
    }

    /// Writes the open document back to its own file. Only valid once it has
    /// one - callers route an untitled document to {@link #saveAs(Path)}.
    public void save() throws IOException {
        database.save();
        afterDocumentChange();
    }

    /// Writes the open document to {@code file}, which becomes its file.
    public void saveAs(Path file) throws IOException {
        database.saveAs(file);
        afterDocumentChange();
    }

    /// Discards every staged edit, re-reading the document from disk.
    public void reload() throws IOException {
        database.reload();
        afterDocumentChange();
    }

    /// Sum of all stores' dirty counts; the row-level half of {@link #dirtyProperty()}.
    public NumberBinding totalDirtyCount() {
        return IntegerExpression.integerExpression(roles.dirtyCountProperty())
                .add(servers.dirtyCountProperty())
                .add(templates.dirtyCountProperty())
                .add(services.dirtyCountProperty());
    }

    private void afterDocumentChange() {
        stores().forEach(LiveStore::refresh);
        documentPath.set(database.documentPath());
        meta.set(database.meta());
        metaDirty.set(false);
        syncArchiveDirty();
    }

    private void syncArchiveDirty() {
        archiveDirty.set(archivedServiceRepository.isDirty());
    }

    private List<LiveStore<?>> stores() {
        return List.of(roles, servers, templates, services);
    }

    private static boolean sameTemplate(ServiceTemplate a, ServiceTemplate b) {
        return a.dayOfWeek().equals(b.dayOfWeek())
                && a.time().equals(b.time())
                && a.durationMinutes() == b.durationMinutes()
                && a.location().equals(b.location())
                && a.type() == b.type()
                && RoleSlot.sameSlots(a.slots(), b.slots());
    }

    private static boolean sameService(LiturgicalService a, LiturgicalService b) {
        return a.dateTime().equals(b.dateTime())
                && a.durationMinutes() == b.durationMinutes()
                && a.location().equals(b.location())
                && a.type() == b.type()
                && a.note().equals(b.note())
                && Slot.sameSlots(a.slots(), b.slots());
    }
}
