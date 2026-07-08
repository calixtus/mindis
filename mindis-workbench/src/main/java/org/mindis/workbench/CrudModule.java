package org.mindis.workbench;

import java.io.File;
import java.io.IOException;
import java.io.Writer;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.css.PseudoClass;
import javafx.geometry.Insets;
import javafx.geometry.Orientation;
import javafx.scene.Node;
import javafx.scene.control.Alert;
import javafx.scene.control.Alert.AlertType;
import javafx.scene.control.Button;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Separator;
import javafx.scene.control.SplitPane;
import javafx.scene.control.TableRow;
import javafx.scene.control.TableView;
import javafx.scene.control.ToolBar;
import javafx.scene.layout.Priority;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import javafx.stage.Window;

import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Base {@link WorkbenchModule} for the common "table on the left, editor on
 * the right" CRUD screen (see Roles/Servers/Services/Templates): a toolbar
 * with New/Delete, a table of items, and an editor pane for the selected item.
 *
 * <p><b>Wiring a subclass:</b>
 * <ul>
 *   <li>{@link #table()} - configure columns; do not call
 *       {@code setItems} on it, {@code CrudModule} owns the item list via
 *       {@link #loadAll()} / {@link #refresh()}.
 *   <li>{@link #toolbarExtras()} - add extra toolbar nodes (buttons,
 *       separators) beyond New/Delete; add them before the module is first
 *       activated (e.g. in the subclass constructor), since the toolbar is
 *       built once on first {@link #activate()}.
 *   <li>{@link #editorProperty()} - set the editor {@link Node} (a
 *       {@code Pane} of field controls) whenever {@link #buildEditor(Object)}
 *       is called; the editor container is automatically disabled while no
 *       row is selected.
 * </ul>
 *
 * <p><b>New/save lifecycle:</b> clicking New calls {@link #createStub()} and
 * inserts+selects it as an unsaved placeholder row (styled with the
 * {@code :crud-new} CSS pseudo-class - grey/italic by default, see
 * {@code workbench.css}). The subclass's editor is expected to call
 * {@link #save(Object)} with the filled-in item when the user confirms (e.g.
 * from a Save button inside the editor {@code Node}). Switching the table
 * selection away from an unsaved placeholder discards it.
 *
 * @param <T> the item type; must have a stable identity accessible via
 *            {@link #identity(Object)} (e.g. a record's {@code id()}), used to
 *            re-select an item after {@link #refresh()} reloads the table from
 *            {@link #loadAll()}.
 */
public abstract class CrudModule<T> extends WorkbenchModule {

    private static final Logger LOGGER = LoggerFactory.getLogger(CrudModule.class);
    private static final PseudoClass CRUD_NEW = PseudoClass.getPseudoClass("crud-new");

    private final TableView<T> table = new TableView<>();
    private final ObservableList<T> items = FXCollections.observableArrayList();
    private final ObservableList<Node> toolbarExtras = FXCollections.observableArrayList();
    private final ObjectProperty<Node> editor = new SimpleObjectProperty<>();
    private final StackPane editorContainer = new StackPane();

    private @Nullable Node view;
    private @Nullable T pendingNew;

    protected CrudModule(String name, String iconLiteral) {
        super(name, iconLiteral);
    }

    /** The table of items; configure columns here. Item list is owned by this class. */
    protected final TableView<T> table() {
        return table;
    }

    /** Extra toolbar nodes appended after New/Delete (behind a separator). */
    protected final ObservableList<Node> toolbarExtras() {
        return toolbarExtras;
    }

    /** The editor pane shown for the selected (or newly created) item. */
    protected final ObjectProperty<Node> editorProperty() {
        return editor;
    }

    /** Text for the New button, e.g. {@code Localization.lang("New")}. */
    protected abstract String newButtonLabel();

    /** Text for the Delete button, e.g. {@code Localization.lang("Delete")}. */
    protected abstract String deleteButtonLabel();

    /** A blank/default item for the New action; not yet persisted. */
    protected abstract T createStub();

    /** Loads every item from the backing repository. */
    protected abstract List<T> loadAll();

    /** Saves (creates or updates) an item in the backing repository. */
    protected abstract void persist(T item);

    /** Removes an item from the backing repository. */
    protected abstract void delete(T item);

    /**
     * A stable key identifying the item across reloads (e.g. {@code item.id()}
     * for a record), used to re-select it after {@link #refresh()}.
     */
    protected abstract Object identity(T item);

    /**
     * Builds (or refreshes and returns) the editor {@code Node} for the given
     * item; called whenever the table selection changes to a non-null item,
     * including a freshly created stub.
     */
    protected abstract Node buildEditor(T item);

    /**
     * CSV mapping for this module's table, or {@code null} (the default) to
     * disable the Export/Import toolbar buttons. Override together with
     * {@link #exportButtonLabel()} and {@link #importButtonLabel()}.
     */
    protected @Nullable CsvRowMapper<T> csvMapper() {
        return null;
    }

    /** Text for the Export button; only read when {@link #csvMapper()} is non-null. */
    protected String exportButtonLabel() {
        return "Export";
    }

    /** Text for the Import button; only read when {@link #csvMapper()} is non-null. */
    protected String importButtonLabel() {
        return "Import";
    }

    /** Title of the Export/Import file chooser dialogs. */
    protected String csvChooserTitle() {
        return getName();
    }

    /** Result summary after an import, e.g. {@code "12 of 14 rows imported"}. */
    protected String importSummary(int imported, int total) {
        return imported + " of " + total + " rows imported";
    }

    @Override
    public final Node activate() {
        Node content = view;
        if (content == null) {
            content = buildView();
            view = content;
        }
        refresh();
        return content;
    }

    @Override
    public void deactivate() {
        if (pendingNew != null) {
            items.remove(pendingNew);
            pendingNew = null;
        }
    }

    /**
     * Persists {@code item}, then reloads the table and re-selects it by
     * {@link #identity(Object)}. Call from the editor's Save action.
     */
    protected final void save(T item) {
        persist(item);
        pendingNew = null;
        refresh();
    }

    /** Reloads the table from {@link #loadAll()}, preserving the selection by identity. */
    protected final void refresh() {
        T selected = table.getSelectionModel().getSelectedItem();
        Object key = selected == null || selected == pendingNew ? null : identity(selected);
        items.setAll(loadAll());
        if (key != null) {
            items.stream()
                    .filter(candidate -> key.equals(identity(candidate)))
                    .findFirst()
                    .ifPresent(table.getSelectionModel()::select);
        }
    }

    private Node buildView() {
        table.setItems(items);
        table.setRowFactory(tableView -> {
            TableRow<T> row = new TableRow<>();
            row.itemProperty().subscribe(rowItem ->
                    row.pseudoClassStateChanged(CRUD_NEW, rowItem != null && rowItem == pendingNew));
            return row;
        });

        table.getSelectionModel().selectedItemProperty().subscribe((previous, current) -> {
            if (previous != null && previous == pendingNew && current != previous) {
                items.remove(previous);
                pendingNew = null;
            }
            editor.set(current == null ? null : buildEditor(current));
        });
        editor.subscribe(node -> editorContainer.getChildren().setAll(node == null ? List.of() : List.of(node)));
        editorContainer.disableProperty().bind(table.getSelectionModel().selectedItemProperty().isNull());

        Button newButton = new Button(newButtonLabel());
        newButton.setOnAction(event -> onNew());
        Button deleteButton = new Button(deleteButtonLabel());
        deleteButton.disableProperty().bind(table.getSelectionModel().selectedItemProperty().isNull());
        deleteButton.setOnAction(event -> onDelete());

        ToolBar toolbar = new ToolBar(newButton, deleteButton);
        toolbar.setStyle("-fx-padding: 8 12 8 12; -fx-spacing: 8;");
        if (!toolbarExtras.isEmpty()) {
            toolbar.getItems().add(new Separator(Orientation.VERTICAL));
            toolbar.getItems().addAll(toolbarExtras);
        }
        CsvRowMapper<T> csvMapper = csvMapper();
        if (csvMapper != null) {
            Button exportButton = new Button(exportButtonLabel());
            exportButton.setOnAction(event -> onExportCsv(csvMapper));
            Button importButton = new Button(importButtonLabel());
            importButton.setOnAction(event -> onImportCsv(csvMapper));
            toolbar.getItems().add(new Separator(Orientation.VERTICAL));
            toolbar.getItems().addAll(exportButton, importButton);
        }

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

    private void onNew() {
        T stub = createStub();
        pendingNew = stub;
        items.addFirst(stub);
        table.getSelectionModel().select(stub);
    }

    private void onDelete() {
        T selected = table.getSelectionModel().getSelectedItem();
        if (selected == null) {
            return;
        }
        if (selected == pendingNew) {
            items.remove(selected);
            pendingNew = null;
        } else {
            delete(selected);
            refresh();
        }
    }

    private void onExportCsv(CsvRowMapper<T> mapper) {
        FileChooser chooser = new FileChooser();
        chooser.setTitle(csvChooserTitle());
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("CSV", "*.csv"));
        chooser.setInitialFileName(getName() + ".csv");
        File target = chooser.showSaveDialog(sceneWindow());
        if (target == null) {
            return;
        }
        List<List<String>> rows = new ArrayList<>();
        for (T item : items) {
            rows.add(mapper.toRow(item));
        }
        try (Writer writer = Files.newBufferedWriter(target.toPath())) {
            CsvIO.write(writer, mapper.header(), rows);
        } catch (IOException e) {
            LOGGER.warn("CSV export failed: {}", target, e);
            new Alert(AlertType.ERROR, e.getMessage()).showAndWait();
        }
    }

    private void onImportCsv(CsvRowMapper<T> mapper) {
        FileChooser chooser = new FileChooser();
        chooser.setTitle(csvChooserTitle());
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("CSV", "*.csv"));
        File source = chooser.showOpenDialog(sceneWindow());
        if (source == null) {
            return;
        }
        try {
            List<List<String>> rows = CsvIO.parse(Files.readString(source.toPath()));
            List<List<String>> dataRows = rows.isEmpty() ? List.of() : rows.subList(1, rows.size());
            int imported = 0;
            for (List<String> row : dataRows) {
                T item = mapper.fromRow(row);
                if (item != null) {
                    persist(item);
                    imported++;
                }
            }
            pendingNew = null;
            refresh();
            new Alert(AlertType.INFORMATION, importSummary(imported, dataRows.size())).showAndWait();
        } catch (IOException e) {
            LOGGER.warn("CSV import failed: {}", source, e);
            new Alert(AlertType.ERROR, e.getMessage()).showAndWait();
        }
    }

    private Window sceneWindow() {
        return table.getScene().getWindow();
    }
}
