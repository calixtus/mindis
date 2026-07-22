package org.mindis.workbench;

import java.io.File;
import java.io.IOException;
import java.io.Writer;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Supplier;

import javafx.beans.property.ObjectProperty;
import javafx.beans.property.ReadOnlyIntegerProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.value.ObservableValue;
import javafx.collections.FXCollections;
import javafx.collections.ListChangeListener;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.control.Alert;
import javafx.scene.control.Alert.AlertType;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.SplitPane;
import javafx.scene.control.TableView;
import javafx.scene.control.ToolBar;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import javafx.stage.Window;
import javafx.util.Subscription;

import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/// Base {@link WorkbenchModule} for the common "table on the left, editor on
/// the right" CRUD screen (see Roles/Servers/Services/Templates): a toolbar,
/// a table of items, and an editor pane for the selected item. This class has
/// no localized text anywhere (it can't reach {@code Localization} across the
/// workbench/core module boundary) - every button, its label and its wiring
/// belongs to the subclass.
///
/// <p><b>State lives in the {@link LiveStore}</b>, not here: the store is a
/// long-lived shared mirror of one repository's staged in-memory state (see
/// its class docs), passed in at construction; this class is a view shell
/// that binds a {@code TableView} to {@code store.items()} and translates
/// user actions into store calls. Several modules may share one store (an
/// owning module edits it, consuming modules read it), and the store - with
/// all unsaved edits and dirty counts - survives this module being rebuilt
/// (e.g. on a language change).
///
/// <p><b>Wiring a subclass:</b>
/// <ul>
///   <li>{@link #table()} - configure columns; do not call
///       {@code setItems} on it, it is bound to the store's live list.
///   <li>{@link #toolbarExtras()} - build the toolbar's buttons (localized
///       text, own {@code setOnAction}) and push them here, in display order;
///       bind {@link #newItem()}/{@link #deleteSelected()}/
///       {@link #exportCsv(CsvRowMapper)}/{@link #importCsv(CsvRowMapper,
///       BiFunction)} as their actions. Push them in the subclass constructor,
///       since the toolbar is built once on first {@link #activate()}.
///   <li>{@link #editorProperty()} - the editor {@link Node} currently shown,
///       set automatically from {@link #buildEditor(Object)}'s result; the
///       editor container is disabled while no row is selected.
///   <li>{@link #onActivate()} - optional per-activation hook (the module was
///       selected in the sidebar). Do <em>not</em> refresh the store from it:
///       re-baselining on a tab switch would wipe every module's dirty state.
///   <li>{@link #dispose()} - overriders must call {@code super.dispose()}.
/// </ul>
///
/// <p><b>The editor is a facade, not a snapshot render.</b>
/// {@link #buildEditor(Object)} runs once per row selection - it wires each
/// control's change listener to call {@link #updateLive(Object)} (writing
/// through to the repository immediately, visible to every other reader), and
/// returns an {@link EditorBinding} bundling the built {@link Node} with a
/// {@code refresh} callback and a {@code dispose} callback. This class calls
/// {@code refresh} - not {@code buildEditor} again - whenever the selected
/// row's value changes for a reason other than the editor's own edit (e.g. a
/// Save/Open re-baselining the store, or another module editing the same
/// row): the subclass updates its controls' values in place instead of losing
/// focus/cursor position to a full teardown-and-rebuild. {@code dispose} runs
/// when the editor is replaced (a different row selected) or the module itself
/// is discarded - detach any subscription the editor set up on a longer-lived
/// object here (an entity-level equivalent of a control's own listener).
/// Subclasses whose editor depends on external reactive state (another
/// module's live store, a plan object) should subscribe directly to that state
/// inside {@link #buildEditor(Object)} and rebuild just the affected part of
/// the editor when it fires - never require a caller elsewhere to remember to
/// "refresh" this editor; that class of bug is exactly what this contract
/// exists to rule out structurally.
///
/// <p><b>New/live-edit lifecycle:</b> {@link #newItem()} calls
/// {@link #createStub()} and inserts+selects it as a live row - an ordinary
/// row from that point on, displayed exactly like any other (no "unsaved" row
/// styling - the table always just shows the live store's current values).
/// Nothing here writes to disk - flushing belongs to the global Save,
/// which re-baselines the store via {@link LiveStore#refresh()}.
/// {@link #deleteSelected()} likewise stages the removal; {@link
/// #importCsv(CsvRowMapper, BiFunction)} merges parsed rows as dirty live
/// rows via {@link #mergeLive(List)}.
///
/// @param <T> the item type; must have a stable identity (the store's
///            identity function, e.g. a record's {@code id()}), used to
///            re-select a row after the store re-baselines
public abstract class CrudModule<T> extends WorkbenchModule {

    private static final Logger LOGGER = LoggerFactory.getLogger(CrudModule.class);

    private final LiveStore<T> store;
    private final TableView<T> table = new TableView<>();
    private final ObservableList<Node> toolbarExtras = FXCollections.observableArrayList();
    private final ObjectProperty<Node> editor = new SimpleObjectProperty<>();
    private final StackPane editorContainer = new StackPane();
    private final Subscription refreshSubscription;

    private @Nullable Node view;
    private @Nullable Object lastSelectedKey;
    private @Nullable EditorBinding<T> currentBinding;
    private boolean suppressEditorRebuild;

    protected CrudModule(String name, String iconLiteral, LiveStore<T> store) {
        super(name, iconLiteral);
        this.store = store;
        // Remember the selection's identity while one exists (a store
        // re-baseline replaces the whole list, which clears the selection
        // before anything can capture it) so it can be restored afterward.
        table.getSelectionModel().selectedItemProperty().subscribe(selected -> {
            if (selected != null) {
                lastSelectedKey = store.identityOf(selected);
            }
        });
        refreshSubscription = store.refreshTickProperty().subscribe(this::restoreSelection);
        // The one generic mechanism replacing every ad hoc "please remember
        // to refresh the editor" call site: whenever the store's list changes
        // for a reason other than this editor's own edit (Save/Open
        // re-baselining, a cross-module write to the same row), and that
        // change touches the currently open row, push the fresh value into
        // the open editor via refresh() instead of requiring a caller
        // elsewhere to notice and rebuild it.
        store.items().addListener((ListChangeListener<T>) change -> {
            if (suppressEditorRebuild || currentBinding == null || lastSelectedKey == null) {
                return;
            }
            Object key = lastSelectedKey;
            store.items().stream()
                    .filter(candidate -> key.equals(store.identityOf(candidate)))
                    .findFirst()
                    .ifPresent(currentBinding.refresh());
        });
    }

    /// The shared live store this module is a view over.
    protected final LiveStore<T> store() {
        return store;
    }

    /// The table of items; configure columns here. The item list is
    /// {@link #tableItems()} (the store's full list by default).
    protected final TableView<T> table() {
        return table;
    }

    /// The list bound to {@link #table()} - the store's full live list by
    /// default. Override to show a derived view instead, e.g. one page's
    /// worth of a windowed/paginated list (see {@code ServicesModule}) - the
    /// rest of this class (selection restore, {@link #newItem()}, {@link
    /// #mergeLive(List)}) still reads the full list via {@link #store()},
    /// only the table's own rendering is affected.
    protected ObservableList<T> tableItems() {
        return store.items();
    }

    /// An optional node shown directly below {@link #table()} - {@code null}
    /// (nothing added) by default. Override to attach paging controls or
    /// similar (see {@code ServicesModule}).
    protected @Nullable Node belowTable() {
        return null;
    }

    /// The toolbar's buttons (and any separators between them), in display order.
    protected final ObservableList<Node> toolbarExtras() {
        return toolbarExtras;
    }

    /// The editor pane shown for the selected (or newly created) item.
    protected final ObjectProperty<Node> editorProperty() {
        return editor;
    }

    /// A blank/default item for the New action; staged but not yet flushed.
    protected abstract T createStub();

    /// Builds the editor for {@code item}: called once whenever the table
    /// selection changes to a non-null item (including a freshly created
    /// stub) - not on every value change afterward, see {@link EditorBinding}.
    protected abstract EditorBinding<T> buildEditor(T item);

    /// An editor built for one row selection: the {@link Node} shown, a
    /// {@code refresh} callback this class invokes in place of rebuilding
    /// (see class docs) when the row's value changes for a reason other than
    /// the editor's own edit, and a {@code dispose} callback run when the
    /// editor is discarded (row deselected, or the module itself torn down) -
    /// detach any subscription {@code buildEditor} set up on longer-lived
    /// state (another store, a plan property) here.
    public record EditorBinding<T>(Node node, Consumer<T> refresh, Runnable dispose) {

        /// For an editor with nothing to detach on disposal.
        public static <T> EditorBinding<T> of(Node node, Consumer<T> refresh) {
            return new EditorBinding<>(node, refresh, () -> { });
        }
    }

    /// Per-activation hook (module selected in the sidebar); default no-op.
    protected void onActivate() {
    }

    @Override
    public final Node activate() {
        Node content = view;
        if (content == null) {
            content = buildView();
            view = content;
        }
        onActivate();
        return content;
    }

    @Override
    public void dispose() {
        refreshSubscription.unsubscribe();
        if (currentBinding != null) {
            currentBinding.dispose().run();
        }
    }

    /// Number of rows differing from their last-flushed snapshot (see
    /// {@link LiveStore#dirtyCountProperty()}).
    protected final ReadOnlyIntegerProperty dirtyCountProperty() {
        return store.dirtyCountProperty();
    }

    /// The last-flushed value for the row sharing {@code item}'s identity, or
    /// {@code null} if it has none yet (a not-yet-flushed new row). Useful as
    /// an editor's dirty-comparison baseline instead of the (possibly already
    /// live-edited) {@code item} passed into {@link #buildEditor(Object)} -
    /// comparing against {@code item} itself would always read "unchanged"
    /// once a live edit has updated it, even though it still differs from
    /// disk.
    protected final @Nullable T savedSnapshot(T item) {
        return store.savedSnapshot(item);
    }

    /// Left border accent on {@code label} while {@code property}'s current
    /// value differs from {@code original.get()} (the last-flushed value) - a
    /// lightweight "you have unsaved changes here" cue that needs no
    /// field-by-field "was this the one that changed" bookkeeping: each field
    /// just watches its own drift from where it started, and clears itself
    /// the moment the value round-trips back to the original (e.g. undoing a
    /// typo). On the field's own label, not the field itself - keeps the
    /// accent out of the way of a field's own focus/validation styling.
    ///
    /// <p>{@code original} is a supplier, not a fixed value: a Save all moves
    /// "the last-flushed value" without necessarily changing what the control
    /// displays (the row was already showing its own just-saved content), so
    /// no property change fires to re-evaluate the accent on its own - a fixed
    /// snapshot captured once at editor-build time would leave the accent
    /// stuck "dirty" forever after the first save. Re-reading the supplier on
    /// every future control edit keeps the listener correct going forward;
    /// {@link EditorBinding}'s {@code refresh} callback additionally
    /// re-invokes this method's initial check after a Save/Open, since
    /// that path changes no control value and so triggers no listener at all.
    ///
    /// <p>{@code original} is typically {@code () -> savedSnapshot(item)}-
    /// derived (falling back to {@code item} for a not-yet-saved new row) -
    /// see any {@code buildEditor(Object)} override for the pattern.
    protected static <T> void markDirtyOnChange(ObservableValue<T> property, Supplier<T> original, Region label) {
        property.addListener((obs, oldValue, newValue) -> recomputeFieldChanged(property, original, label));
        recomputeFieldChanged(property, original, label);
    }

    /// The comparison {@link #markDirtyOnChange} reruns on every control
    /// change - factored out so an {@link EditorBinding}'s {@code refresh}
    /// callback (a Save/Open, which moves {@code original} without
    /// necessarily changing what the control displays, so no listener fires
    /// on its own) can re-invoke just the comparison without registering a
    /// second listener.
    protected static <T> void recomputeFieldChanged(ObservableValue<T> property, Supplier<T> original, Region label) {
        setFieldChanged(label, !Objects.equals(property.getValue(), original.get()));
    }

    /// Toggles the left-border "unsaved change" accent (see {@code .field-changed} in the app's theme stylesheet) on or off.
    protected static void setFieldChanged(Region label, boolean changed) {
        if (changed) {
            if (!label.getStyleClass().contains("field-changed")) {
                label.getStyleClass().add("field-changed");
            }
        } else {
            label.getStyleClass().remove("field-changed");
        }
    }

    /// Pushes a freshly rebuilt value for the row sharing {@code updated}'s
    /// identity into the live store (which stages it into the repository - no
    /// disk write). Call from every control's change listener in
    /// {@link #buildEditor(Object)}. Does not rebuild the open editor (the
    /// edit originated there).
    protected final void updateLive(T updated) {
        suppressEditorRebuild = true;
        try {
            int index = store.updateLive(updated);
            // TableView's selection model treats a list "set" as a
            // remove+add internally and drops the selection - reselect
            // (still guarded by suppressEditorRebuild, so this doesn't
            // trigger another editor rebuild) so the row stays selected and
            // the editor stays enabled mid-edit. By value, not index: for a
            // windowed table() (see tableItems()) the table's own item
            // indices don't line up with store.items()' indices.
            if (index >= 0 && !updated.equals(table.getSelectionModel().getSelectedItem())) {
                table.getSelectionModel().select(updated);
            }
        } finally {
            suppressEditorRebuild = false;
        }
    }

    /// Merges {@code items} into the live store (see
    /// {@link LiveStore#mergeLive(List)}) - ordinary dirty live rows, same as
    /// a manual edit or {@link #newItem()}, preserving the table selection.
    /// For a subclass's own bulk-generate/import action; does not touch disk.
    protected final void mergeLive(List<T> items) {
        T selected = table.getSelectionModel().getSelectedItem();
        Object selectedKey = selected == null ? null : store.identityOf(selected);
        suppressEditorRebuild = true;
        try {
            store.mergeLive(items);
            if (selectedKey != null) {
                selectByKey(selectedKey);
            }
        } finally {
            suppressEditorRebuild = false;
        }
    }

    private void restoreSelection() {
        if (lastSelectedKey != null) {
            selectByKey(lastSelectedKey);
        }
    }

    private void selectByKey(Object key) {
        T current = table.getSelectionModel().getSelectedItem();
        if (current != null && key.equals(store.identityOf(current))) {
            return;
        }
        store.items().stream()
                .filter(candidate -> key.equals(store.identityOf(candidate)))
                .findFirst()
                .ifPresent(table.getSelectionModel()::select);
    }

    private Node buildView() {
        table.setItems(tableItems());
        // Del key deletes the selected row, same as the Delete button (only
        // when the table itself has focus - not while editing in the side panel).
        table.setOnKeyPressed(event -> {
            if (event.getCode() == KeyCode.DELETE) {
                deleteSelected();
                event.consume();
            }
        });

        table.getSelectionModel().selectedItemProperty().subscribe((previous, current) -> {
            if (suppressEditorRebuild) {
                return;
            }
            if (currentBinding != null) {
                currentBinding.dispose().run();
                currentBinding = null;
            }
            if (current == null) {
                editor.set(null);
            } else {
                EditorBinding<T> binding = buildEditor(current);
                currentBinding = binding;
                editor.set(binding.node());
            }
        });
        editor.subscribe(node -> editorContainer.getChildren().setAll(node == null ? List.of() : List.of(node)));
        editorContainer.disableProperty().bind(table.getSelectionModel().selectedItemProperty().isNull());

        ToolBar toolbar = new ToolBar();
        toolbar.setStyle("-fx-padding: 8 12 8 12; -fx-spacing: 8;");
        toolbar.getItems().addAll(toolbarExtras);

        VBox.setVgrow(table, Priority.ALWAYS);
        VBox tableSide = new VBox(table);
        Node footer = belowTable();
        if (footer != null) {
            tableSide.getChildren().add(footer);
        }
        tableSide.setPadding(new Insets(12));

        ScrollPane editorScroll = new ScrollPane(editorContainer);
        editorScroll.setFitToWidth(true);
        editorScroll.setFitToHeight(true);

        SplitPane split = new SplitPane(tableSide, editorScroll);
        VBox.setVgrow(split, Priority.ALWAYS);
        return new VBox(toolbar, split);
    }

    /// Inserts and selects a new {@link #createStub()} row - a normal live row
    /// from this point on (see class docs), immediately editable and included
    /// in the next flush. Bind to the New button's action.
    protected final void newItem() {
        T stub = createStub();
        store.insertFirst(stub);
        table.getSelectionModel().select(stub);
    }

    /// Removes the selected row from the live store, staging the repository
    /// removal (flushed to disk by the next global Save all; restored by a
    /// Load). Bind to the Delete button's action.
    protected final void deleteSelected() {
        T selected = table.getSelectionModel().getSelectedItem();
        if (selected == null) {
            return;
        }
        int index = table.getItems().indexOf(selected);
        store.remove(selected);
        // Reselect whatever row shifted into the freed slot (or the new last
        // row), so a run of deletions does not dump the user back to an empty
        // editor after every one. store.remove updates the table item list
        // synchronously, so the post-removal size/indices are already current.
        int size = table.getItems().size();
        if (size > 0 && index >= 0) {
            table.getSelectionModel().select(Math.min(index, size - 1));
        }
    }

    /// Prompts for a file and writes every row via {@code mapper}. Bind to the Export button's action.
    protected final void exportCsv(CsvRowMapper<T> mapper) {
        FileChooser chooser = new FileChooser();
        chooser.setTitle(getName());
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("CSV", "*.csv"));
        chooser.setInitialFileName(getName() + ".csv");
        File target = chooser.showSaveDialog(sceneWindow());
        if (target == null) {
            return;
        }
        List<List<String>> rows = new ArrayList<>();
        for (T item : store.items()) {
            rows.add(mapper.toRow(item));
        }
        try (Writer writer = Files.newBufferedWriter(target.toPath())) {
            CsvIO.write(writer, mapper.header(), rows);
        } catch (IOException e) {
            LOGGER.warn("CSV export failed: {}", target, e);
            new Alert(AlertType.ERROR, e.getMessage()).showAndWait();
        }
    }

    /// Prompts for a file and merges every parsed row into the live store (via
    /// {@link #mergeLive(List)} - staged, not written to disk until the next
    /// global Save all), then shows {@code summaryMessage.apply(imported,
    /// total)} (e.g. localized "12 of 14 rows imported" text - this class has
    /// no localized text of its own). Bind to the Import button's action.
    protected final void importCsv(CsvRowMapper<T> mapper, BiFunction<Integer, Integer, String> summaryMessage) {
        FileChooser chooser = new FileChooser();
        chooser.setTitle(getName());
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("CSV", "*.csv"));
        File source = chooser.showOpenDialog(sceneWindow());
        if (source == null) {
            return;
        }
        try {
            List<List<String>> rows = CsvIO.parse(Files.readString(source.toPath()));
            List<List<String>> dataRows = rows.isEmpty() ? List.of() : rows.subList(1, rows.size());
            List<T> imported = new ArrayList<>();
            for (List<String> row : dataRows) {
                T item = mapper.fromRow(row);
                if (item != null) {
                    imported.add(item);
                }
            }
            mergeLive(imported);
            new Alert(AlertType.INFORMATION, summaryMessage.apply(imported.size(), dataRows.size())).showAndWait();
        } catch (IOException e) {
            LOGGER.warn("CSV import failed: {}", source, e);
            new Alert(AlertType.ERROR, e.getMessage()).showAndWait();
        }
    }

    private Window sceneWindow() {
        return table.getScene().getWindow();
    }
}
