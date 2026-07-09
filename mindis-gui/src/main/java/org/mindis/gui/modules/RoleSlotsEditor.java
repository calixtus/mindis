package org.mindis.gui.modules;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.control.Spinner;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

import org.mindis.core.l10n.Localization;
import org.mindis.core.model.Role;
import org.mindis.core.model.RoleSlot;

/**
 * "Required servers" role/slot-count editor shared by {@link ServicesModule}
 * and {@link TemplatesModule}: one compact row per role (name left, a small
 * split-arrow count spinner right). Built fresh per {@code buildEditor} call,
 * matching the "rebuild from current roles" refresh pattern used by every
 * {@link org.mindis.workbench.CrudModule} editor - so a role added or removed
 * in the Roles module is always picked up.
 */
final class RoleSlotsEditor {

    private static final int MAX_SLOT_COUNT = 10;

    /** Label for the editor's grid row; height-bound to the first slot row so its text centers. */
    final Label label = new Label(Localization.lang("Required servers"));

    private final Map<String, Spinner<Integer>> spinners = new LinkedHashMap<>();
    private final VBox list = new VBox(8);

    RoleSlotsEditor(List<Role> roles, List<RoleSlot> initialSlots) {
        this(roles, initialSlots, slots -> { });
    }

    /**
     * @param onChange called with {@link #collectSlots()}'s current result
     *                 whenever any spinner's count changes - lets a caller
     *                 (e.g. {@link ServicesModule}'s per-slot assignment
     *                 rows) stay in sync with counts live, before Save.
     */
    RoleSlotsEditor(List<Role> roles, List<RoleSlot> initialSlots, Consumer<List<RoleSlot>> onChange) {
        for (Role role : roles) {
            Spinner<Integer> spinner = new Spinner<>(0, MAX_SLOT_COUNT, slotCount(initialSlots, role.id()));
            spinner.getStyleClass().add(Spinner.STYLE_CLASS_SPLIT_ARROWS_HORIZONTAL);
            // Trim the theme's roomy editor padding (7px 11px) so the field is
            // narrower without collapsing the split arrows. Font-relative (em),
            // not fixed pixels, so it scales with the font size.
            spinner.getEditor().setStyle("-fx-padding: 0.2em 0.4em 0.2em 0.4em;");
            spinner.valueProperty().addListener((obs, oldValue, newValue) -> onChange.accept(collectSlots()));
            spinners.put(role.id(), spinner);

            Label roleName = new Label(role.name());
            roleName.setMaxWidth(Double.MAX_VALUE);
            HBox.setHgrow(roleName, Priority.ALWAYS);
            HBox row = new HBox(8, roleName, spinner);
            row.setAlignment(Pos.CENTER_LEFT);
            list.getChildren().add(row);
        }
        if (!list.getChildren().isEmpty() && list.getChildren().getFirst() instanceof HBox firstRow) {
            label.minHeightProperty().bind(firstRow.heightProperty());
            label.prefHeightProperty().bind(firstRow.heightProperty());
        }
    }

    /** The row list; place in the editor grid's field column, with {@code GridPane.setVgrow(ALWAYS)}. */
    VBox list() {
        return list;
    }

    /** Slot counts entered in the editor (zero-count roles omitted), for saving. */
    List<RoleSlot> collectSlots() {
        List<RoleSlot> slots = new ArrayList<>();
        spinners.forEach((roleId, spinner) -> {
            int count = spinner.getValue();
            if (count > 0) {
                slots.add(new RoleSlot(roleId, count));
            }
        });
        return slots;
    }

    private static int slotCount(List<RoleSlot> slots, String roleId) {
        return slots.stream()
                .filter(slot -> slot.role().equals(roleId))
                .mapToInt(RoleSlot::count)
                .findFirst()
                .orElse(0);
    }
}
