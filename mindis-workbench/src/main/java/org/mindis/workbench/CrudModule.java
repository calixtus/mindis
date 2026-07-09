package org.mindis.workbench;

import java.io.File;
import java.io.IOException;
import java.io.Writer;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BiFunction;

import javafx.beans.property.ObjectProperty;
import javafx.beans.property.ReadOnlyIntegerProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.css.PseudoClass;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.control.Alert;
import javafx.scene.control.Alert.AlertType;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.SplitPane;
import javafx.scene.control.TableRow;
import javafx.scene.control.TableView;
import javafx.scene.control.ToolBar;
import javafx.scene.layout.Priority;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import javafx.stage.Window;
import javafx.util.Subscription;

import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Base {@link WorkbenchModule} for the common "table on the left, editor on
 * the right" CRUD screen (see Roles/Servers/Services/Templates): a toolbar,
 * a table of items, and an editor pane for the selected item. This class has
 * no localized text anywhere (it can't reach {@code Localization} across the
 * workbench/core module boundary) - every button, its label and its wiring
 * belongs to the subclass.
 *
 * <p><b>State lives in the {@link LiveStore}</b>, not here: the store is a
 * long-lived shared mirror of one repository's staged in-memory state (see
 * its class docs), passed in at construction; this class is a view shell
 * that binds a {@code TableView} to {@code store.items()} and translates
 * user actions into store calls. Several modules may share one store (an
 * owning module edits it, consuming modules read it), and the store - with
 * all unsaved edits and dirty counts - survives this module being rebuilt
 * (e.g. on a language change).
 *
 * <p><b>Wiring a subclass:</b>
 * <ul>
 *   <li>{@link #table()} - configure columns; do not call
 *       {@code setItems} on it, it is bound to the store's live list.
 *   <li>{@link #toolbarExtras()} - build the toolbar's buttons (localized
 *       text, own {@code setOnAction}) and push them here, in display order;
 *       bind {@link #newItem()}/{@link #deleteSelected()}/
 *       {@link #exportCsv(CsvRowMapper)}/{@link #importCsv(CsvRowMapper,
 *       BiFunction)} as their actions. Push them in the subclass constructor,
 *       since the toolbar is built once on first {@link #activate()}.
 *   <li>{@link #editorProperty()} - set the editor {@link Node} (a
 *       {@code Pane} of field controls) whenever {@link #buildEditor(Object)}
 *       is called; the editor container is automatically disabled while no
 *       row is selected.
 *   <li>{@link #onActivate()} - optional per-activation hook (the module was
 *       selected in the sidebar). Do <em>not</em> refresh the store from it:
 *       re-baselining on a tab switch would wipe every module's dirty state.
 *   <li>{@link #dispose()} - overriders must call {@code super.dispose()}.
 * </ul>
 *
 * <p><b>New/live-edit lifecycle:</b> {@link #newItem()} calls
 * {@link #createStub()} and inserts+selects it as a live row - a normal row
 * from that point on, styled with the {@code :crud-new} CSS pseudo-class
 * (grey/italic by default, see {@code workbench.css}) only until it's first
 * flushed. The subclass's editor is a pure facade over the live store state:
 * every control's change listener should call {@link #updateLive(Object)}
 * with a freshly rebuilt item (there is no per-item Save button). Every edit
 * writes through to the repository cache immediately (visible to all other
 * readers); nothing here writes to disk - flushing belongs to the global
 * Save all, which re-baselines the store via {@link LiveStore#refresh()}.
 * {@link #deleteSelected()} likewise stages the removal; {@link
 * #importCsv(CsvRowMapper, BiFunction)} merges parsed rows as dirty live
 * rows via {@link #mergeLive(List)}.
 *
 * @param <T> the item type; must have a stable identity (the store's
 *            identity function, e.g. a record's {@code id()}), used to
 *            re-select a row after the store re-baselines
 */
public abstract class CrudModule<T> extends WorkbenchModule {

    private static final Logger LOGGER = LoggerFactory.getLogger(CrudModule.class);
    private static final PseudoClass CRUD_NEW = PseudoClass.getPseudoClass("crud-new");

    private final LiveStore<T> store;
    private final TableView<T> table = new TableView<>();
    private final ObservableList<Node> toolbarExtras = FXCollections.observableArrayList();
    private final ObjectProperty<Node> editor = new SimpleObjectProperty<>();
    private final StackPane editorContainer = new StackPane();
    private final Subscription refreshSubscription;

    private @Nullable Node view;
    private @Nullable Object lastSelectedKey;
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
        refreshSubscription = store.onRefresh(this::restoreSelection);
    }

    /** The shared live store this module is a view over. */
    protected final LiveStore<T> store() {
        return store;
    }

    /** The table of items; configure columns here. The item list is the store's. */
    protected final TableView<T> table() {
        return table;
    }

    /** The toolbar's buttons (and any separators between them), in display order. */
    protected final ObservableList<Node> toolbarExtras() {
        return toolbarExtras;
    }

    /** The editor pane shown for the selected (or newly created) item. */
    protected final ObjectProperty<Node> editorProperty() {
        return editor;
    }

    /** A blank/default item for the New action; staged but not yet flushed. */
    protected abstract T createStub();

    /**
     * Builds (or refreshes and returns) the editor {@code Node} for the given
     * item; called whenever the table selection changes to a non-null item,
     * including a freshly created stub.
     */
    protected abstract Node buildEditor(T item);

    /** Per-activation hook (module selected in the sidebar); default no-op. */
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
    }

    /**
     * Number of rows differing from their last-flushed snapshot (see
     * {@link LiveStore#dirtyCountProperty()}).
     */
    protected final ReadOnlyIntegerProperty dirtyCountProperty() {
        return store.dirtyCountProperty();
    }

    /**
     * The last-flushed value for the row sharing {@code item}'s identity, or
     * {@code null} if it has none yet (a not-yet-flushed new row). Useful as
     * an editor's dirty-comparison baseline instead of the (possibly already
     * live-edited) {@code item} passed into {@link #buildEditor(Object)} -
     * comparing against {@code item} itself would always read "unchanged"
     * once a live edit has updated it, even though it still differs from
     * disk.
     */
    protected final @Nullable T savedSnapshot(T item) {
        return store.savedSnapshot(item);
    }

    /**
     * Pushes a freshly rebuilt value for the row sharing {@code updated}'s
     * identity into the live store (which stages it into the repository - no
     * disk write). Call from every control's change listener in
     * {@link #buildEditor(Object)}. Does not rebuild the open editor (the
     * edit originated there).
     */
    protected final void updateLive(T updated) {
        suppressEditorRebuild = true;
        try {
            int index = store.updateLive(updated);
            // TableView's selection model treats a list "set" as a
            // remove+add internally and drops the selection - reselect by
            // index (still guarded by suppressEditorRebuild, so this
            // doesn't trigger another editor rebuild) so the row stays
            // selected and the editor stays enabled mid-edit.
            if (index >= 0 && table.getSelectionModel().getSelectedIndex() != index) {
                table.getSelectionModel().select(index);
            }
        } finally {
            suppressEditorRebuild = false;
        }
    }

    /**
     * Merges {@code items} into the live store (see
     * {@link LiveStore#mergeLive(List)}) - ordinary dirty live rows, same as
     * a manual edit or {@link #newItem()}, preserving the table selection.
     * For a subclass's own bulk-generate/import action; does not touch disk.
     */
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
        table.setItems(store.items());
        table.setRowFactory(tableView -> {
            TableRow<T> row = new TableRow<>();
            row.itemProperty().subscribe(rowItem ->
                    row.pseudoClassStateChanged(CRUD_NEW,
                            rowItem != null && store.savedSnapshot(rowItem) == null));
            return row;
        });

        table.getSelectionModel().selectedItemProperty().subscribe((previous, current) -> {
            if (suppressEditorRebuild) {
                return;
            }
            editor.set(current == null ? null : buildEditor(current));
        });
        editor.subscribe(node -> editorContainer.getChildren().setAll(node == null ? List.of() : List.of(node)));
        editorContainer.disableProperty().bind(table.getSelectionModel().selectedItemProperty().isNull());

        ToolBar toolbar = new ToolBar();
        toolbar.setStyle("-fx-padding: 8 12 8 12; -fx-spacing: 8;");
        toolbar.getItems().addAll(toolbarExtras);

        VBox.setVgrow(table, Priority.ALWAYS);
        VBox tableSide = new VBox(table);
        tableSide.setPadding(new Insets(12));

        ScrollPane editorScroll = new ScrollPane(editorContainer);
        editorScroll.setFitToWidth(true);
        editorScroll.setFitToHeight(true);

        SplitPane split = new SplitPane(tableSide, editorScroll);
        VBox.setVgrow(split, Priority.ALWAYS);
        return new VBox(toolbar, split);
    }

    /**
     * Inserts and selects a new {@link #createStub()} row - a normal live row
     * from this point on (see class docs), immediately editable and included
     * in the next flush. Bind to the New button's action.
     */
    protected final void newItem() {
        T stub = createStub();
        store.insertFirst(stub);
        table.getSelectionModel().select(stub);
    }

    /**
     * Removes the selected row from the live store, staging the repository
     * removal (flushed to disk by the next global Save all; restored by a
     * Load). Bind to the Delete button's action.
     */
    protected final void deleteSelected() {
        T selected = table.getSelectionModel().getSelectedItem();
        if (selected == null) {
            return;
        }
        store.remove(selected);
    }

    /** Prompts for a file and writes every row via {@code mapper}. Bind to the Export button's action. */
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

    /**
     * Prompts for a file and merges every parsed row into the live store (via
     * {@link #mergeLive(List)} - staged, not written to disk until the next
     * global Save all), then shows {@code summaryMessage.apply(imported,
     * total)} (e.g. localized "12 of 14 rows imported" text - this class has
     * no localized text of its own). Bind to the Import button's action.
     */
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
