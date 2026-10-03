package org.mindis.gui.modules;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.control.OverrunStyle;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

import org.kordamp.ikonli.javafx.FontIcon;

import org.mindis.core.l10n.EnumDisplay;
import org.mindis.core.model.LiturgicalService;
import org.mindis.core.model.Role;
import org.mindis.core.model.RoleId;
import org.mindis.core.model.Server;
import org.mindis.core.model.ServerId;
import org.mindis.core.model.Slot;

import org.mindis.gui.util.DateTimes;

/// Renders one row of the services table as a tile: when and what on the left,
/// the role-slot summary on the right. Read-only - picks happen in the editor.
final class ServiceTiles {

    private static final double TILE_INFO_WIDTH = 180;
    private static final double ROLE_COLUMN_WIDTH = 100;
    private static final double SLOT_COLUMN_WIDTH = 90;

    private final LiveRoster roster;

    ServiceTiles(LiveRoster roster) {
        this.roster = roster;
    }

    /// The table row's tile: big-font date/time on the left (with an
    /// underfilled warning icon), type/location below it, and the role-slot
    /// grid on the right.
    Node build(LiturgicalService service) {
        Label dateTimeLabel = new Label(DateTimes.dateTime(service.dateTime()));
        dateTimeLabel.getStyleClass().add("service-tile-datetime");
        Label typeLabel = new Label(EnumDisplay.of(service));
        Label locationLabel = new Label(service.location());
        VBox left = new VBox(2, dateTimeLabel, typeLabel, locationLabel);
        left.setMinWidth(TILE_INFO_WIDTH);
        left.setPrefWidth(TILE_INFO_WIDTH);
        left.setMaxWidth(TILE_INFO_WIDTH);
        left.setAlignment(Pos.CENTER_LEFT);
        for (Label label : List.of(dateTimeLabel, typeLabel, locationLabel)) {
            label.setMaxWidth(TILE_INFO_WIDTH);
            label.setTextOverrun(OverrunStyle.ELLIPSIS);
        }

        AssignedCount count = assignedCount(service);
        if (count.underfilled()) {
            FontIcon warningIcon = new FontIcon("mdi2a-alert-circle");
            warningIcon.getStyleClass().add("altar-warning-icon");
            HBox dateRow = new HBox(6, dateTimeLabel, warningIcon);
            dateRow.setAlignment(Pos.CENTER_LEFT);
            left.getChildren().set(0, dateRow);
        }

        GridPane slotGrid = buildRoleSlotGrid(service);
        HBox.setHgrow(slotGrid, Priority.ALWAYS);
        HBox tile = new HBox(20, left, slotGrid);
        tile.setAlignment(Pos.CENTER_LEFT);
        tile.setPadding(new Insets(8, 4, 8, 4));
        return tile;
    }

    /// Per-service role-slot summary, read-only (picks happen in the editor):
    /// role name, then that role's slots showing the assigned server (or "-").
    private GridPane buildRoleSlotGrid(LiturgicalService service) {
        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(2);
        grid.getColumnConstraints().addAll(roleSlotColumn(ROLE_COLUMN_WIDTH), roleSlotColumn(SLOT_COLUMN_WIDTH),
                roleSlotColumn(SLOT_COLUMN_WIDTH));

        Map<ServerId, Server> serversById = roster.serversById();
        Map<RoleId, Role> rolesById = roster.rolesById();

        int gridRow = 0;
        for (Map.Entry<RoleId, List<Slot>> entry : slotsByRole(service.slots()).entrySet()) {
            List<Slot> roleSlots = entry.getValue();
            Role role = rolesById.get(entry.getKey());
            Label roleLabel = new Label(role == null ? entry.getKey().value() : role.name());
            roleLabel.getStyleClass().add("service-tile-role");
            roleLabel.setMaxWidth(ROLE_COLUMN_WIDTH);
            roleLabel.setTextOverrun(OverrunStyle.ELLIPSIS);
            grid.add(roleLabel, 0, gridRow);

            for (int slotIndex = 0; slotIndex < roleSlots.size(); slotIndex++) {
                Slot slot = roleSlots.get(slotIndex);
                Server server = slot.serverId() == null ? null : serversById.get(slot.serverId());
                String text = server == null ? "-" : server.displayName();
                Label slotLabel = new Label(text);
                slotLabel.getStyleClass().add("service-tile-slot");
                slotLabel.setMaxWidth(SLOT_COLUMN_WIDTH);
                slotLabel.setTextOverrun(OverrunStyle.ELLIPSIS);
                int column = 1 + slotIndex % 2;
                grid.add(slotLabel, column, gridRow);
                if (column == 2) {
                    gridRow++;
                }
            }
            if (roleSlots.size() % 2 != 0) {
                gridRow++;
            }
        }
        return grid;
    }

    /// `slots`, grouped by role in first-encountered order.
    private static Map<RoleId, List<Slot>> slotsByRole(List<Slot> slots) {
        Map<RoleId, List<Slot>> byRole = new LinkedHashMap<>();
        for (Slot slot : slots) {
            byRole.computeIfAbsent(slot.role(), roleId -> new ArrayList<>()).add(slot);
        }
        return byRole;
    }

    private static ColumnConstraints roleSlotColumn(double width) {
        ColumnConstraints column = new ColumnConstraints();
        column.setMinWidth(width);
        column.setPrefWidth(width);
        column.setMaxWidth(width);
        column.setHgrow(Priority.NEVER);
        return column;
    }

    /// Filled/total slot counts backing the tile's underfilled warning. Reads
    /// the service's own slots directly - an in-editor slot edit is written
    /// through to the row's record before the tile re-renders, so the record is
    /// always the live source.
    private record AssignedCount(int filled, int total) {
        boolean underfilled() {
            return filled < total;
        }
    }

    private static AssignedCount assignedCount(LiturgicalService service) {
        int filled = (int) service.slots().stream().filter(slot -> slot.serverId() != null).count();
        return new AssignedCount(filled, service.totalSlots());
    }
}
