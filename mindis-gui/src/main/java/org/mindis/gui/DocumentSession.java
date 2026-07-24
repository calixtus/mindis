package org.mindis.gui;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;

import javafx.beans.binding.Bindings;
import javafx.beans.binding.StringBinding;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonBar.ButtonData;
import javafx.scene.control.ButtonType;
import javafx.stage.FileChooser;
import javafx.stage.Window;

import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.mindis.core.l10n.Localization;
import org.mindis.core.model.CollectionMeta;
import org.mindis.core.preferences.PreferencesService;
import org.mindis.core.preferences.RecentCollection;

/// The document actions behind the global toolbar: New, Open, Save and Save
/// as, plus the guard that keeps unsaved work from being dropped silently and
/// the window title that shows which document is open.
///
/// <p>All data lives in one user-chosen JSON file, so these are file actions
/// (see {@link org.mindis.core.persistence.AppDatabase}). This class owns
/// everything that makes them user-facing - file chooser, confirmations, error
/// dialogs and the remembered last document - while {@link LiveDatabase} owns
/// the state they act on. Long-lived like {@code LiveDatabase} itself: a UI
/// rebuild (language change) re-binds the toolbar to the same session.
public final class DocumentSession {

    private static final Logger LOGGER = LoggerFactory.getLogger(DocumentSession.class);
    private static final String DEFAULT_FILE_NAME = "mindis.json";

    private final LiveDatabase liveDatabase;
    private final PreferencesService preferencesService;
    /// The dialog owner, resolved per call: the stage exists before the first
    /// document action, but the scene it owns is rebuilt on a language change.
    private final Supplier<@Nullable Window> owner;

    public DocumentSession(LiveDatabase liveDatabase, PreferencesService preferencesService,
                           Supplier<@Nullable Window> owner) {
        this.liveDatabase = liveDatabase;
        this.preferencesService = preferencesService;
        this.owner = owner;
    }

    /// The window title: application name plus the open collection's display
    /// name - the parish name from its {@link CollectionMeta}, falling back to
    /// the file name (or "Untitled") - marked with an asterisk while there are
    /// unsaved edits.
    public StringBinding titleBinding() {
        return Bindings.createStringBinding(
                () -> Localization.lang("MinDis - Minister Dispatcher") + " - " + collectionDisplayName()
                        + (liveDatabase.isDirty() ? "*" : ""),
                liveDatabase.documentPathProperty(), liveDatabase.metaProperty(),
                liveDatabase.dirtyProperty());
    }

    /// The name to show for the open collection: its display name when set,
    /// otherwise the file name, otherwise "Untitled".
    public String collectionDisplayName() {
        String displayName = liveDatabase.meta().displayName();
        if (displayName != null && !displayName.isBlank()) {
            return displayName;
        }
        return documentName();
    }

    /// Opens the document remembered from the last session, or starts a new
    /// untitled one when there is none, it has since disappeared, or it cannot
    /// be read (reported, then an empty document - a startup must never fail on
    /// a file the user may not even remember choosing).
    public void openLastDocumentOrNew() {
        Path last = rememberedDocument();
        if (last == null || !Files.isReadable(last)) {
            liveDatabase.newDocument();
            return;
        }
        try {
            liveDatabase.open(last);
        } catch (IOException e) {
            LOGGER.warn("Could not open the last document {}", last, e);
            showError(Localization.lang("Could not open %0", last.toString()), e);
            liveDatabase.newDocument();
            rememberDocument(null);
        }
    }

    /// Starts an empty untitled document, after guarding unsaved edits.
    public void onNew() {
        if (!confirmDropUnsavedChanges()) {
            return;
        }
        liveDatabase.newDocument();
        rememberDocument(null);
    }

    /// Prompts for a document and opens it, after guarding unsaved edits. A
    /// failed open leaves the current document untouched.
    public void onOpen() {
        if (!confirmDropUnsavedChanges()) {
            return;
        }
        FileChooser chooser = chooser(Localization.lang("Open document"));
        File selected = chooser.showOpenDialog(owner.get());
        if (selected == null) {
            return;
        }
        Path file = selected.toPath();
        try {
            liveDatabase.open(file);
            rememberDocument(file);
            LOGGER.info(Localization.lang("Opened %0", file.getFileName().toString()));
        } catch (IOException e) {
            LOGGER.error(Localization.lang("Could not open %0", file.toString()), e);
            showError(Localization.lang("Could not open %0", file.toString()), e);
        }
    }

    /// Saves the open document, prompting for a location if it has none yet.
    ///
    /// @return whether the document was actually written (false if the user
    ///         cancelled the location prompt or the write failed) - the close
    ///         and New/Open guards need to know
    public boolean onSave() {
        Optional<Path> file = liveDatabase.documentPath();
        if (file.isEmpty()) {
            return onSaveAs();
        }
        return write(() -> liveDatabase.save(), file.get());
    }

    /// Prompts for a location and saves the open document there, making it the
    /// document's file from now on.
    ///
    /// @return whether the document was actually written
    public boolean onSaveAs() {
        FileChooser chooser = chooser(Localization.lang("Save document as"));
        chooser.setInitialFileName(liveDatabase.documentPath()
                .map(path -> path.getFileName().toString())
                .orElse(DEFAULT_FILE_NAME));
        File selected = chooser.showSaveDialog(owner.get());
        if (selected == null) {
            return false;
        }
        Path file = selected.toPath();
        return write(() -> liveDatabase.saveAs(file), file);
    }

    /// Asks what to do with unsaved edits before they would be dropped (New,
    /// Open, or closing the window).
    ///
    /// @return whether the caller may proceed - true when there was nothing to
    ///         save, the user chose Discard, or the save succeeded
    public boolean confirmDropUnsavedChanges() {
        if (!liveDatabase.isDirty()) {
            return true;
        }
        ButtonType save = new ButtonType(Localization.lang("Save"), ButtonData.YES);
        ButtonType discard = new ButtonType(Localization.lang("Discard"), ButtonData.NO);
        ButtonType cancel = new ButtonType(Localization.lang("Cancel"), ButtonData.CANCEL_CLOSE);
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION,
                Localization.lang("%0 has unsaved changes.", documentName()), save, discard, cancel);
        alert.setTitle(Localization.lang("Unsaved changes"));
        alert.setHeaderText(null);
        alert.initOwner(owner.get());
        ButtonType answer = alert.showAndWait().orElse(cancel);
        if (answer == save) {
            return onSave();
        }
        return answer == discard;
    }

    // --- Recent collections (switcher dropdown) ---

    /// The recently opened or saved collections, most recent first, for the
    /// switcher dropdown. Capped at
    /// {@link org.mindis.core.preferences.MinDisPreferences#MAX_RECENT_COLLECTIONS}.
    public List<RecentCollection> recents() {
        return preferencesService.get().recentCollections();
    }

    /// Switches to a collection from the recent list, after guarding unsaved
    /// edits. A recent whose file has since vanished or fails to open is
    /// reported and dropped from the list, so the switcher self-heals.
    public void switchTo(RecentCollection recent) {
        if (!confirmDropUnsavedChanges()) {
            return;
        }
        Path file;
        try {
            file = Path.of(recent.path());
        } catch (InvalidPathException e) {
            LOGGER.warn("Dropping recent with unusable path {}", recent.path(), e);
            forgetRecent(recent.path());
            return;
        }
        if (!Files.isReadable(file)) {
            showError(Localization.lang("Could not open %0", recent.path()));
            forgetRecent(recent.path());
            return;
        }
        try {
            liveDatabase.open(file);
            rememberDocument(file);
            LOGGER.info(Localization.lang("Opened %0", file.getFileName().toString()));
        } catch (IOException e) {
            LOGGER.error(Localization.lang("Could not open %0", file.toString()), e);
            showError(Localization.lang("Could not open %0", file.toString()), e);
            forgetRecent(recent.path());
        }
    }

    // --- Collection metadata (name + logo) ---

    /// The open collection's identity, for the metadata editor to preload.
    public CollectionMeta currentMeta() {
        return liveDatabase.meta();
    }

    /// Applies an edited identity to the open collection (staged like any edit;
    /// a save writes it and refreshes the collection's recent entry). When the
    /// collection already has a file, its recent entry's cached name/logo are
    /// refreshed immediately so the switcher reflects the change before a save.
    public void updateMetadata(CollectionMeta meta) {
        liveDatabase.updateMeta(meta);
        liveDatabase.documentPath().ifPresent(this::refreshRecentMeta);
    }

    private void refreshRecentMeta(Path file) {
        String path = file.toAbsolutePath().toString();
        CollectionMeta meta = liveDatabase.meta();
        preferencesService.update(preferences -> preferences.withRecentCollection(
                new RecentCollection(path, meta.displayName(), meta.logoPngBase64(),
                        System.currentTimeMillis())));
    }

    private String documentName() {
        return liveDatabase.documentPath()
                .map(path -> path.getFileName().toString())
                .orElseGet(() -> Localization.lang("Untitled"));
    }

    private boolean write(IoAction action, Path file) {
        try {
            action.run();
            rememberDocument(file);
            LOGGER.info(Localization.lang("Saved %0", file.getFileName().toString()));
            return true;
        } catch (IOException e) {
            LOGGER.error(Localization.lang("Could not save %0", file.toString()), e);
            showError(Localization.lang("Could not save %0", file.toString()), e);
            return false;
        }
    }

    private FileChooser chooser(String title) {
        FileChooser chooser = new FileChooser();
        chooser.setTitle(title);
        chooser.getExtensionFilters().add(
                new FileChooser.ExtensionFilter(Localization.lang("MinDis document"), "*.json"));
        liveDatabase.documentPath()
                .map(Path::getParent)
                .map(Path::toFile)
                .filter(File::isDirectory)
                .ifPresent(chooser::setInitialDirectory);
        return chooser;
    }

    private @Nullable Path rememberedDocument() {
        String remembered = preferencesService.get().lastDocument();
        if (remembered == null || remembered.isBlank()) {
            return null;
        }
        try {
            return Path.of(remembered);
        } catch (InvalidPathException e) {
            LOGGER.warn("Ignoring unusable remembered document path {}", remembered, e);
            return null;
        }
    }

    private void rememberDocument(@Nullable Path file) {
        if (file == null) {
            // An untitled document is not a recent collection; just clear the
            // reopen-on-start pointer.
            preferencesService.update(preferences -> preferences.withLastDocument(null));
            return;
        }
        String path = file.toAbsolutePath().toString();
        CollectionMeta meta = liveDatabase.meta();
        RecentCollection recent = new RecentCollection(path, meta.displayName(),
                meta.logoPngBase64(), System.currentTimeMillis());
        preferencesService.update(preferences ->
                preferences.withLastDocument(path).withRecentCollection(recent));
    }

    private void forgetRecent(String path) {
        preferencesService.update(preferences -> preferences.withoutRecentCollection(path));
    }

    private void showError(String message) {
        Alert alert = new Alert(Alert.AlertType.ERROR, message, ButtonType.OK);
        alert.setHeaderText(null);
        alert.initOwner(owner.get());
        alert.showAndWait();
    }

    private void showError(String message, Exception cause) {
        Alert alert = new Alert(Alert.AlertType.ERROR, message + "\n\n" + cause.getMessage(), ButtonType.OK);
        alert.setHeaderText(null);
        alert.initOwner(owner.get());
        alert.showAndWait();
    }

    @FunctionalInterface
    private interface IoAction {
        void run() throws IOException;
    }
}
