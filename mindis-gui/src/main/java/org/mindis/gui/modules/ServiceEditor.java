package org.mindis.gui.modules;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Supplier;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.geometry.VPos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.Separator;
import javafx.scene.control.TextField;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.util.StringConverter;

import atlantafx.base.theme.Styles;
import com.dlsc.gemsfx.CalendarPicker;
import com.dlsc.gemsfx.TimePicker;
import org.kordamp.ikonli.javafx.FontIcon;

import org.mindis.core.l10n.EnumDisplay;
import org.mindis.core.l10n.Localization;
import org.mindis.core.model.LiturgicalService;
import org.mindis.core.model.Role;
import org.mindis.core.model.Server;
import org.mindis.core.model.ServiceType;
import org.mindis.core.model.Slot;
import org.mindis.core.planning.AssignmentKey;
import org.mindis.core.planning.ServicePlan;
import org.jspecify.annotations.Nullable;

import org.mindis.gui.planning.PlanningViewModel;
import org.mindis.gui.util.CalendarPickers;
import org.mindis.gui.util.TimePickers;
import org.mindis.gui.shell.CrudModule;

/// One service row's editor: date/time/type/location/note fields plus the
/// Altar-servers assignment panel (one server combo per role slot).
final class ServiceEditor {

    private static final double EDITOR_MIN_HEIGHT = 520;

    private final LiturgicalService service;
    private final Supplier<LiturgicalService> baselineSupplier;
    private final Consumer<LiturgicalService> updateLive;
    private final LiveRoster roster;
    private final PlanningViewModel planningViewModel;
    private final ServicesSolverController solver;

    private final CalendarPicker dateField = CalendarPickers.create();
    private final TimePicker timeField = TimePickers.create();
    private final ComboBox<ServiceType> typeBox =
            new ComboBox<>(FXCollections.observableArrayList(ServiceType.values()));
    private final TextField nameField;
    private final TextField locationField;
    private final TextField noteField;

    private final Label dateLabel = new Label(Localization.lang("Date"));
    private final Label timeLabel = new Label(Localization.lang("Time"));
    private final Label typeLabel = new Label(Localization.lang("Type"));
    private final Label nameLabel = new Label(Localization.lang("Name"));
    private final Label locationLabel = new Label(Localization.lang("Location"));
    private final Label noteLabel = new Label(Localization.lang("Note"));

    private final Label altarServersTitle = new Label(Localization.lang("Altar servers"));
    private final VBox assignmentSection = new VBox(6);
    private final SlotCountEditor slotsEditor;
    private final VBox content;

    private boolean suppressPushLive;
    // The concrete slot instances backing the editor - reconciled (not
    // rebuilt) on every count edit so a role's already-assigned slots keep
    // their ids and assignments across a resize.
    private List<Slot> liveSlots;

    /// @param baseline   the row's last-saved value, re-read on every check
    ///                   (see [CrudModule#baseline(Object)])
    /// @param updateLive stages an edited value (see [CrudModule#updateLive(Object)])
    ServiceEditor(LiturgicalService service, Supplier<LiturgicalService> baseline,
                  Consumer<LiturgicalService> updateLive, LiveRoster roster,
                  PlanningViewModel planningViewModel, ServicesSolverController solver) {
        this.service = service;
        this.baselineSupplier = baseline;
        this.updateLive = updateLive;
        this.roster = roster;
        this.planningViewModel = planningViewModel;
        this.solver = solver;
        this.liveSlots = service.slots();

        dateField.setValue(service.dateTime().toLocalDate());
        timeField.setTime(service.dateTime().toLocalTime());
        typeBox.setConverter(new StringConverter<>() {
            @Override
            public String toString(@Nullable ServiceType type) {
                return type == null ? "" : EnumDisplay.of(type);
            }

            @Override
            public @Nullable ServiceType fromString(@Nullable String string) {
                return null;
            }
        });
        typeBox.getSelectionModel().select(service.type());
        nameField = new TextField(service.name());
        // Blank is the normal case: then the service shows its type.
        nameField.setPromptText(EnumDisplay.of(service.type()));
        typeBox.valueProperty().addListener((obs, oldType, newType) ->
                nameField.setPromptText(newType == null ? "" : EnumDisplay.of(newType)));
        locationField = new TextField(service.location());
        noteField = new TextField(service.note());
        noteField.setPromptText(Localization.lang("Shown with the service in the plan export"));

        Button clearButton = new Button(null, new FontIcon("mdi2b-broom"));
        clearButton.getStyleClass().addAll(Styles.BUTTON_ICON, Styles.FLAT, Styles.SMALL);
        clearButton.disableProperty().bind(planningViewModel.solvingProperty());
        clearButton.setOnAction(event -> onClearSlots());
        Tooltip.install(clearButton, new Tooltip(Localization.lang("Clear assignments")));

        Button autoFillButton = new Button(null, new FontIcon("mdi2a-auto-fix"));
        autoFillButton.getStyleClass().addAll(Styles.BUTTON_ICON, Styles.FLAT, Styles.SMALL);
        autoFillButton.disableProperty().bind(planningViewModel.solvingProperty());
        autoFillButton.setOnAction(event -> solver.autofillService(service));
        Tooltip.install(autoFillButton, new Tooltip(Localization.lang("Auto-fill")));
        ProgressIndicator autoFillIndicator = new ProgressIndicator();
        autoFillIndicator.setPrefSize(16, 16);
        planningViewModel.solvingProperty().addListener((obs, wasSolving, isSolving) ->
                autoFillButton.setGraphic(isSolving ? autoFillIndicator : new FontIcon("mdi2a-auto-fix")));
        Region titleSpacer = new Region();
        HBox.setHgrow(titleSpacer, Priority.ALWAYS);
        HBox altarServersHeader = new HBox(8, altarServersTitle, titleSpacer, clearButton, autoFillButton);
        altarServersHeader.setAlignment(Pos.CENTER_LEFT);
        Separator altarSeparator = new Separator();

        // Bound directly to the shared live role list - a role added,
        // renamed or removed anywhere shows up in this editor on its own.
        slotsEditor = new SlotCountEditor(roster.roles(), countsByRole(service.slots()), this::onSlotCountsChanged);
        CrudModule.setFieldChanged(slotsEditor.label, slotsChanged(slotsEditor.collectCounts()));
        refreshAssignmentSection();

        GridPane grid = new GridPane();
        grid.setHgap(8);
        grid.setVgap(8);
        ColumnConstraints labelColumn = new ColumnConstraints();
        labelColumn.setMinWidth(110);
        ColumnConstraints fieldColumn = new ColumnConstraints();
        fieldColumn.setHgrow(Priority.ALWAYS);
        grid.getColumnConstraints().addAll(labelColumn, fieldColumn);

        int row = 0;
        grid.add(dateLabel, 0, row);
        grid.add(dateField, 1, row++);
        grid.add(timeLabel, 0, row);
        grid.add(timeField, 1, row++);
        grid.add(typeLabel, 0, row);
        grid.add(typeBox, 1, row++);
        grid.add(nameLabel, 0, row);
        grid.add(nameField, 1, row++);
        grid.add(locationLabel, 0, row);
        grid.add(locationField, 1, row++);
        grid.add(noteLabel, 0, row);
        grid.add(noteField, 1, row++);

        GridPane.setValignment(slotsEditor.label, VPos.TOP);
        grid.add(slotsEditor.label, 0, row);
        GridPane.setVgrow(slotsEditor.list(), Priority.ALWAYS);
        grid.add(slotsEditor.list(), 1, row++);

        dateField.valueProperty().addListener((obs, oldValue, newValue) -> pushLive());
        timeField.timeProperty().addListener((obs, oldValue, newValue) -> pushLive());
        typeBox.valueProperty().addListener((obs, oldValue, newValue) -> pushLive());
        nameField.textProperty().addListener((obs, oldValue, newValue) -> pushLive());
        locationField.textProperty().addListener((obs, oldValue, newValue) -> pushLive());
        noteField.textProperty().addListener((obs, oldValue, newValue) -> pushLive());

        content = new VBox(10, grid, altarSeparator, altarServersHeader, assignmentSection);
        content.setPadding(new Insets(12));
        content.setMinHeight(EDITOR_MIN_HEIGHT);
        content.getStylesheets().add(ServicesModule.STYLESHEET);
        CrudModule.markDirtyOnChange(dateField.valueProperty(), () -> baselineSupplier.get().dateTime().toLocalDate(), dateLabel);
        CrudModule.markDirtyOnChange(timeField.timeProperty(), () -> baselineSupplier.get().dateTime().toLocalTime(), timeLabel);
        CrudModule.markDirtyOnChange(typeBox.valueProperty(), () -> baselineSupplier.get().type(), typeLabel);
        CrudModule.markDirtyOnChange(nameField.textProperty(), () -> baselineSupplier.get().name(), nameLabel);
        CrudModule.markDirtyOnChange(locationField.textProperty(), () -> baselineSupplier.get().location(), locationLabel);
        CrudModule.markDirtyOnChange(noteField.textProperty(), () -> baselineSupplier.get().note(), noteLabel);
    }

    Node node() {
        return content;
    }

    private boolean slotsChanged(Map<String, Integer> liveCounts) {
        return !liveCounts.equals(countsByRole(baselineSupplier.get().slots()));
    }

    private void onSlotCountsChanged(Map<String, Integer> liveCounts) {
        liveSlots = reconcileSlots(liveSlots, liveCounts);
        CrudModule.setFieldChanged(slotsEditor.label, slotsChanged(liveCounts));
        // pushLive replaces this service's row item, which re-renders its
        // tile on its own; refreshAssignmentSection redraws the open editor.
        pushLive();
        refreshAssignmentSection();
    }

    private void refreshAssignmentSection() {
        assignmentSection.getChildren().setAll(buildAssignmentRows());
    }

    private void pushLive() {
        if (suppressPushLive) {
            return;
        }
        LocalDate date = dateField.getValue();
        LocalTime time = timeField.getTime();
        if (date == null || time == null) {
            return;
        }
        updateLive.accept(new LiturgicalService(service.id(), date.atTime(time), service.durationMinutes(),
                locationField.getText().strip(),
                typeBox.getValue() == null ? ServiceType.OTHER : typeBox.getValue(),
                nameField.getText().strip(),
                liveSlots, noteField.getText().strip()));
    }

    /// One editable server dropdown per slot of [#liveSlots], seeded
    /// with the slot's current server and its constraint violations (a
    /// warning icon). Because an assignment lives on the slot itself, a
    /// slot just added by the count editor is immediately assignable - no
    /// "save first" placeholder.
    private List<Node> buildAssignmentRows() {
        CrudModule.setFieldChanged(altarServersTitle, assignmentsChanged());
        if (liveSlots.isEmpty()) {
            return List.of();
        }
        Map<String, Server> serversById = roster.serversById();
        Map<String, Role> rolesById = roster.rolesById();
        // Violations come from a transient problem over the whole live
        // board (double-booking spans services), keyed by assignment id.
        ServicePlan plan = planningViewModel.buildProblem();
        Map<String, List<String>> violations = planningViewModel.violationsByAssignment(plan);

        ObservableList<Server> choices = FXCollections.observableArrayList(roster.activeServers());
        choices.addFirst(null);

        List<Node> rows = new ArrayList<>();
        for (Slot slot : liveSlots) {
            Role role = rolesById.get(slot.role());
            String roleName = role == null ? slot.role() : role.name();
            String assignmentId = new AssignmentKey(service.id(), slot.id()).toId();
            Server current = slot.serverId() == null ? null : serversById.get(slot.serverId());

            ComboBox<Server> serverBox = new ComboBox<>(choices);
            serverBox.setConverter(new StringConverter<>() {
                @Override
                public String toString(@Nullable Server server) {
                    return server == null ? "-" : server.displayName();
                }

                @Override
                public @Nullable Server fromString(String string) {
                    return null;
                }
            });
            serverBox.setValue(current);
            serverBox.valueProperty().addListener((obs, oldServer, newServer) -> onPickServer(slot, newServer));

            Label roleLabel = new Label(roleName);
            roleLabel.setMinWidth(110);
            Region spacer = new Region();
            HBox.setHgrow(spacer, Priority.ALWAYS);
            HBox row = new HBox(8, roleLabel, spacer, serverBox);
            row.setAlignment(Pos.CENTER_LEFT);
            serverBox.prefWidthProperty().bind(row.widthProperty().multiply(0.6));

            List<String> names = violations.getOrDefault(assignmentId, List.of());
            // "Slot unassigned" is already obvious from the empty dropdown -
            // only flag genuine rule conflicts on a filled slot.
            if (slot.serverId() != null && !names.isEmpty()) {
                FontIcon warningIcon = new FontIcon("mdi2a-alert-circle");
                warningIcon.getStyleClass().add("altar-warning-icon");
                StackPane iconSlot = new StackPane(warningIcon);
                Tooltip.install(iconSlot, new Tooltip(
                        String.join(", ", names.stream().map(Localization::lang).toList())));
                row.getChildren().add(2, iconSlot);
            }
            rows.add(row);
        }
        return rows;
    }

    /// Applies a manual pick: rewrites the slot's server on [#liveSlots], stages the
    /// service, and refreshes the rows/score/table.
    private void onPickServer(Slot slot, @Nullable Server newServer) {
        List<Slot> updated = new ArrayList<>(liveSlots.size());
        for (Slot existing : liveSlots) {
            updated.add(existing.id().equals(slot.id())
                    ? existing.withServer(newServer == null ? null : newServer.id(), newServer != null)
                    : existing);
        }
        liveSlots = updated;
        pushLive();
        refreshAssignmentSection();
        solver.refreshScore();
    }

    /// Clears every slot of this service - empties the server and drops the
    /// pin on each, then stages and refreshes, same as clearing them one by
    /// one by hand. A no-op (no unsaved change) if they are all empty already.
    private void onClearSlots() {
        List<Slot> cleared = new ArrayList<>(liveSlots.size());
        for (Slot slot : liveSlots) {
            cleared.add(slot.withServer(null, false));
        }
        liveSlots = cleared;
        pushLive();
        refreshAssignmentSection();
        solver.refreshScore();
    }

    /// Whether [#liveSlots]' assignments (server + pin per slot id)
    /// differ from the last-saved baseline - the per-row unsaved accent.
    private boolean assignmentsChanged() {
        Map<String, Slot> baseline = new HashMap<>();
        for (Slot slot : baselineSupplier.get().slots()) {
            baseline.put(slot.id(), slot);
        }
        for (Slot slot : liveSlots) {
            Slot original = baseline.get(slot.id());
            String originalServer = original == null ? null : original.serverId();
            boolean originalPinned = original != null && original.pinned();
            if (!Objects.equals(slot.serverId(), originalServer) || slot.pinned() != originalPinned) {
                return true;
            }
        }
        return false;
    }

    void refresh(LiturgicalService updated) {
        suppressPushLive = true;
        try {
            dateField.setValue(updated.dateTime().toLocalDate());
            timeField.setTime(updated.dateTime().toLocalTime());
            typeBox.getSelectionModel().select(updated.type());
            locationField.setText(updated.location());
            noteField.setText(updated.note());
            liveSlots = updated.slots();
            slotsEditor.setCounts(countsByRole(updated.slots()));
        } finally {
            suppressPushLive = false;
        }
        CrudModule.recomputeFieldChanged(dateField.valueProperty(), () -> baselineSupplier.get().dateTime().toLocalDate(), dateLabel);
        CrudModule.recomputeFieldChanged(timeField.timeProperty(), () -> baselineSupplier.get().dateTime().toLocalTime(), timeLabel);
        CrudModule.recomputeFieldChanged(typeBox.valueProperty(), () -> baselineSupplier.get().type(), typeLabel);
        CrudModule.recomputeFieldChanged(locationField.textProperty(), () -> baselineSupplier.get().location(), locationLabel);
        CrudModule.recomputeFieldChanged(noteField.textProperty(), () -> baselineSupplier.get().note(), noteLabel);
        CrudModule.setFieldChanged(slotsEditor.label, slotsChanged(countsByRole(updated.slots())));
        refreshAssignmentSection();
    }

    void dispose() {
        slotsEditor.dispose();
    }

    /// Reconciles a role's slot count edit, keeping a filled/pinned slot as
    /// long as possible - the `isFilled` seam is now the slot's own
    /// stored assignment, no plan lookup needed.
    private static List<Slot> reconcileSlots(List<Slot> existing, Map<String, Integer> counts) {
        return SlotReconciler.reconcile(existing, counts, slot -> slot.serverId() != null || slot.pinned());
    }

    /// Slot counts per role, for the [SlotCountEditor].
    private static Map<String, Integer> countsByRole(List<Slot> slots) {
        Map<String, Integer> counts = new LinkedHashMap<>();
        for (Slot slot : slots) {
            counts.merge(slot.role(), 1, Integer::sum);
        }
        return counts;
    }
}
