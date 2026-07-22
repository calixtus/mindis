package org.mindis.gui.modules;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.ToIntFunction;

import javafx.collections.ListChangeListener;
import javafx.collections.ObservableList;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.control.Spinner;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

import org.mindis.core.l10n.Localization;
import org.mindis.core.model.Role;

/// "Required servers" role/slot-count editor shared by {@link ServicesModule}
/// and {@link TemplatesModule}: one compact row per role (name left, a small
/// split-arrow count spinner right). Exchanges plain role-id-to-count maps,
/// not either caller's own slot type ({@code RoleSlot} for a template's
/// shape-only requirement, {@code Slot} for a service's individually-
/// identified instances) - a spinner only ever edits a count, so this class
/// has no business knowing which of the two a caller actually persists;
/// each caller converts at its own boundary (see {@code ServicesModule}'s
/// {@code reconcileSlots}, which is also where a count decrease's "which
/// specific slot instance to drop" decision belongs, not here).
///
/// <p>Bound directly to the live (shared) {@code ObservableList<Role>}
/// rather than a caller-supplied snapshot: a role added, renamed or removed
/// anywhere - even unsaved - updates this editor's rows on its own via an
/// internal listener, with no rebuild call needed from outside. Only the
/// affected rows are rebuilt; already-entered counts for roles unaffected by
/// the change survive. Call {@link #dispose()} when the owning editor is
/// discarded (see {@code CrudModule.EditorBinding}) to detach the listener
/// from the shared list.
final class SlotCountEditor {

    private static final int MAX_SLOT_COUNT = 10;

    /// Label for the editor's grid row; height-bound to the first slot row so its text centers.
    final Label label = new Label(Localization.lang("Required servers"));

    private final ObservableList<Role> roles;
    private final Consumer<Map<String, Integer>> onChange;
    private final Map<String, Spinner<Integer>> spinners = new LinkedHashMap<>();
    private final VBox list = new VBox(8);
    private final ListChangeListener<Role> rolesListener = change -> rebuildRows(this::currentOrZero);

    SlotCountEditor(ObservableList<Role> roles, Map<String, Integer> initialCounts) {
        this(roles, initialCounts, counts -> { });
    }

    /// @param onChange called with {@link #collectCounts()}'s current result
    ///                 whenever any spinner's count changes - lets a caller
    ///                 (e.g. {@link ServicesModule}'s per-slot assignment
    ///                 rows) stay in sync with counts live, before Save.
    SlotCountEditor(ObservableList<Role> roles, Map<String, Integer> initialCounts, Consumer<Map<String, Integer>> onChange) {
        this.roles = roles;
        this.onChange = onChange;
        rebuildRows(roleId -> initialCounts.getOrDefault(roleId, 0));
        roles.addListener(rolesListener);
        if (!list.getChildren().isEmpty() && list.getChildren().getFirst() instanceof HBox firstRow) {
            label.minHeightProperty().bind(firstRow.heightProperty());
            label.prefHeightProperty().bind(firstRow.heightProperty());
        }
    }

    /// Detaches this editor's listener from the shared role list; call when the editor is discarded.
    void dispose() {
        roles.removeListener(rolesListener);
    }

    /// The row list; place in the editor grid's field column, with {@code GridPane.setVgrow(ALWAYS)}.
    VBox list() {
        return list;
    }

    /// Reseeds every spinner from {@code counts} - for an
    /// {@code EditorBinding} refresh (the owning item's slots changed
    /// externally, e.g. a Save/Open reverting an unflushed count), not
    /// for a role-list change (handled internally). Does not itself call
    /// {@code onChange}.
    void setCounts(Map<String, Integer> counts) {
        rebuildRows(roleId -> counts.getOrDefault(roleId, 0));
    }

    /// Counts entered in the editor (zero-count roles omitted), for saving.
    Map<String, Integer> collectCounts() {
        Map<String, Integer> counts = new LinkedHashMap<>();
        spinners.forEach((roleId, spinner) -> {
            int count = spinner.getValue();
            if (count > 0) {
                counts.put(roleId, count);
            }
        });
        return counts;
    }

    /// This role's currently entered count, or 0 if it has no row yet (a role just added elsewhere).
    private int currentOrZero(String roleId) {
        Spinner<Integer> spinner = spinners.get(roleId);
        return spinner == null ? 0 : spinner.getValue();
    }

    /// Rebuilds every row from the current {@link #roles}; {@code seed}
    /// supplies each row's initial count (the constructor seeds from the
    /// item's persisted counts, a later role-list change seeds from whatever
    /// is already entered, so mid-edit counts survive a role being added or
    /// removed elsewhere).
    private void rebuildRows(ToIntFunction<String> seed) {
        spinners.clear();
        List<HBox> rows = new ArrayList<>();
        for (Role role : roles) {
            Spinner<Integer> spinner = new Spinner<>(0, MAX_SLOT_COUNT, seed.applyAsInt(role.id()));
            spinner.getStyleClass().add(Spinner.STYLE_CLASS_SPLIT_ARROWS_HORIZONTAL);
            // Trim the theme's roomy editor padding (7px 11px) so the field is
            // narrower without collapsing the split arrows. Font-relative (em),
            // not fixed pixels, so it scales with the font size.
            spinner.getEditor().setStyle("-fx-padding: 0.2em 0.4em 0.2em 0.4em;");
            spinner.valueProperty().addListener((obs, oldValue, newValue) -> onChange.accept(collectCounts()));
            spinners.put(role.id(), spinner);

            Label roleName = new Label(role.name());
            roleName.setMaxWidth(Double.MAX_VALUE);
            HBox.setHgrow(roleName, Priority.ALWAYS);
            HBox row = new HBox(8, roleName, spinner);
            row.setAlignment(Pos.CENTER_LEFT);
            rows.add(row);
        }
        list.getChildren().setAll(rows);
    }
}
