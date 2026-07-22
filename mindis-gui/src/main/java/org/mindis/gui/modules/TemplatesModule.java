package org.mindis.gui.modules;

import java.time.DayOfWeek;
import java.time.LocalTime;
import java.time.format.TextStyle;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.function.Supplier;

import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.geometry.Orientation;
import javafx.geometry.VPos;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.Separator;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TextField;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.util.StringConverter;

import com.dlsc.gemsfx.TimePicker;

import org.mindis.core.l10n.EnumDisplay;
import org.mindis.core.l10n.Localization;
import org.mindis.core.model.Role;
import org.mindis.core.model.RoleSlot;
import org.mindis.core.model.ServiceTemplate;
import org.jspecify.annotations.Nullable;

import org.mindis.core.model.ServiceType;
import org.mindis.core.persistence.RoleRepository;
import org.mindis.core.persistence.TemplateCsvMapper;
import org.mindis.gui.util.TimePickers;
import org.mindis.workbench.CrudModule;
import org.mindis.workbench.CsvRowMapper;
import org.mindis.workbench.LiveStore;

/// Weekly recurring service templates ("every Sunday 10:00 at St. Mary"),
/// expanded into concrete services from the Services module.
///
/// <p>Weekly-only for now; month/year/feast-day template types are a planned
/// extension of this module (see PLAN.md).
public class TemplatesModule extends CrudModule<ServiceTemplate> {

    private static final double EDITOR_MIN_HEIGHT = 420;

    private final TemplatesViewModel viewModel;
    private final LiveStore<Role> roleStore;

    public TemplatesModule(String name, LiveStore<ServiceTemplate> templateStore, LiveStore<Role> roleStore,
                           RoleRepository roleRepository) {
        super(name, "mdi2c-calendar-sync", templateStore);
        this.viewModel = new TemplatesViewModel(roleRepository);
        this.roleStore = roleStore;

        TableColumn<ServiceTemplate, String> dayColumn = new TableColumn<>(Localization.lang("Weekday"));
        dayColumn.setPrefWidth(110);
        dayColumn.setCellValueFactory(data -> new SimpleStringProperty(
                data.getValue().dayOfWeek().getDisplayName(TextStyle.FULL, Locale.getDefault())));

        TableColumn<ServiceTemplate, String> timeColumn = new TableColumn<>(Localization.lang("Time"));
        timeColumn.setPrefWidth(70);
        timeColumn.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().time().toString()));

        TableColumn<ServiceTemplate, String> typeColumn = new TableColumn<>(Localization.lang("Type"));
        typeColumn.setPrefWidth(110);
        typeColumn.setCellValueFactory(data -> new SimpleStringProperty(EnumDisplay.of(data.getValue().type())));

        TableColumn<ServiceTemplate, String> locationColumn = new TableColumn<>(Localization.lang("Location"));
        locationColumn.setPrefWidth(110);
        locationColumn.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().location()));

        table().getColumns().add(dayColumn);
        table().getColumns().add(timeColumn);
        table().getColumns().add(typeColumn);
        table().getColumns().add(locationColumn);

        Button newButton = new Button(Localization.lang("New"));
        newButton.setOnAction(event -> newItem());
        Button deleteButton = new Button(Localization.lang("Delete"));
        deleteButton.disableProperty().bind(table().getSelectionModel().selectedItemProperty().isNull());
        deleteButton.setOnAction(event -> deleteSelected());

        TemplateCsvMapper templateCsvMapper = new TemplateCsvMapper(roleRepository);
        CsvRowMapper<ServiceTemplate> csvMapper =
                CsvRowMapper.of(templateCsvMapper::header, templateCsvMapper::toRow, templateCsvMapper::fromRow);
        Button exportButton = new Button(Localization.lang("Export"));
        exportButton.setOnAction(event -> exportCsv(csvMapper));
        Button importButton = new Button(Localization.lang("Import"));
        importButton.setOnAction(event -> importCsv(csvMapper,
                (imported, total) -> Localization.lang("%0 of %1 rows imported", imported, total)));

        toolbarExtras().addAll(newButton, deleteButton, new Separator(Orientation.VERTICAL), exportButton, importButton);
    }

    @Override
    protected ServiceTemplate createStub() {
        return viewModel.createStub();
    }

    @Override
    protected EditorBinding<ServiceTemplate> buildEditor(ServiceTemplate template) {
        // Compares against the last-flushed value, not template itself - see
        // CrudModule#markDirtyOnChange.
        Supplier<ServiceTemplate> baselineSupplier = () -> Objects.requireNonNullElse(savedSnapshot(template), template);

        ComboBox<DayOfWeek> dayBox = new ComboBox<>(FXCollections.observableArrayList(DayOfWeek.values()));
        dayBox.setConverter(new StringConverter<>() {
            @Override
            public String toString(@Nullable DayOfWeek day) {
                return day == null ? "" : day.getDisplayName(TextStyle.FULL, Locale.getDefault());
            }

            @Override
            public @Nullable DayOfWeek fromString(@Nullable String string) {
                return null;
            }
        });
        dayBox.getSelectionModel().select(template.dayOfWeek());

        TimePicker timeField = TimePickers.create();
        timeField.setTime(template.time());

        ComboBox<ServiceType> typeBox = new ComboBox<>(FXCollections.observableArrayList(ServiceType.values()));
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
        typeBox.getSelectionModel().select(template.type());

        TextField locationField = new TextField(template.location());

        Runnable[] pushLiveHolder = new Runnable[1];
        // Recomputed from baselineSupplier on every call (not captured once)
        // for the same reason baselineSupplier itself is a supplier - it
        // must reflect the post-Save baseline, not whatever was last flushed
        // when this editor was built.
        Function<Map<String, Integer>, Boolean> slotsChanged =
                liveCounts -> !liveCounts.equals(countsByRole(baselineSupplier.get().slots()));
        // SlotCountEditor's onChange callback needs to mark its own label
        // dirty, but that label (slotsEditor.label) doesn't exist until the
        // SlotCountEditor constructor - which is where the callback itself
        // gets built - returns. A one-element array sidesteps the
        // chicken-and-egg: the callback only runs later, in response to a
        // spinner change, well after the array's single slot is filled in
        // right below the constructor call.
        Region[] slotsLabelHolder = new Region[1];
        // Bound directly to the shared live role list - a role added,
        // renamed or removed anywhere shows up in this editor's slot rows on
        // its own, no rebuild call from here needed.
        SlotCountEditor slotsEditor = new SlotCountEditor(roleStore.items(), countsByRole(template.slots()),
                counts -> {
                    setFieldChanged(slotsLabelHolder[0], slotsChanged.apply(counts));
                    pushLiveHolder[0].run();
                });
        slotsLabelHolder[0] = slotsEditor.label;
        setFieldChanged(slotsEditor.label, slotsChanged.apply(slotsEditor.collectCounts()));

        // Guards every control's change listener against firing while the
        // refresh callback below is pushing an externally-changed value into
        // the controls - without it, a refresh's programmatic set can
        // trigger a *second*, reentrant items.set() on the shared store list
        // while an outer one is still unwinding through its own listener
        // chain, corrupting JavaFX's internal ListChangeBuilder (see
        // RolesModule for the same fix).
        boolean[] suppressPushLive = new boolean[1];
        Runnable pushLive = () -> {
            if (suppressPushLive[0]) {
                return;
            }
            DayOfWeek day = dayBox.getValue();
            LocalTime time = timeField.getTime();
            if (day == null || time == null) {
                return;
            }
            updateLive(new ServiceTemplate(template.id(), day, time, template.durationMinutes(),
                    locationField.getText().strip(),
                    typeBox.getValue() == null ? ServiceType.SUNDAY_MASS : typeBox.getValue(),
                    toRoleSlots(slotsEditor.collectCounts())));
        };
        pushLiveHolder[0] = pushLive;
        dayBox.getSelectionModel().selectedItemProperty().addListener((obs, oldValue, newValue) -> pushLive.run());
        timeField.timeProperty().addListener((obs, oldValue, newValue) -> pushLive.run());
        typeBox.getSelectionModel().selectedItemProperty().addListener((obs, oldValue, newValue) -> pushLive.run());
        locationField.textProperty().addListener((obs, oldValue, newValue) -> pushLive.run());

        GridPane grid = new GridPane();
        grid.setHgap(8);
        grid.setVgap(8);
        ColumnConstraints labelColumn = new ColumnConstraints();
        labelColumn.setMinWidth(110);
        ColumnConstraints fieldColumn = new ColumnConstraints();
        fieldColumn.setHgrow(Priority.ALWAYS);
        grid.getColumnConstraints().addAll(labelColumn, fieldColumn);

        Label dayLabel = new Label(Localization.lang("Weekday"));
        Label timeLabel = new Label(Localization.lang("Time"));
        Label typeLabel = new Label(Localization.lang("Type"));
        Label locationLabel = new Label(Localization.lang("Location"));

        int row = 0;
        grid.add(dayLabel, 0, row);
        grid.add(dayBox, 1, row++);
        grid.add(timeLabel, 0, row);
        grid.add(timeField, 1, row++);
        grid.add(typeLabel, 0, row);
        grid.add(typeBox, 1, row++);
        grid.add(locationLabel, 0, row);
        grid.add(locationField, 1, row++);

        GridPane.setValignment(slotsEditor.label, VPos.TOP);
        grid.add(slotsEditor.label, 0, row);
        GridPane.setVgrow(slotsEditor.list(), Priority.ALWAYS);
        grid.add(slotsEditor.list(), 1, row++);

        VBox content = new VBox(10, grid);
        content.setPadding(new Insets(12));
        content.setMinHeight(EDITOR_MIN_HEIGHT);
        markDirtyOnChange(dayBox.getSelectionModel().selectedItemProperty(), () -> baselineSupplier.get().dayOfWeek(), dayLabel);
        markDirtyOnChange(timeField.timeProperty(), () -> baselineSupplier.get().time(), timeLabel);
        markDirtyOnChange(typeBox.getSelectionModel().selectedItemProperty(), () -> baselineSupplier.get().type(), typeLabel);
        markDirtyOnChange(locationField.textProperty(), () -> baselineSupplier.get().location(), locationLabel);

        // refresh: the row's value changed externally (e.g. an Open or a revert reverted
        // this template) - push the new value into every control in place,
        // no rebuild, so the row survives without losing focus/scroll state.
        return new EditorBinding<>(content, updated -> {
            suppressPushLive[0] = true;
            try {
                dayBox.getSelectionModel().select(updated.dayOfWeek());
                timeField.setTime(updated.time());
                typeBox.getSelectionModel().select(updated.type());
                locationField.setText(updated.location());
                slotsEditor.setCounts(countsByRole(updated.slots()));
            } finally {
                suppressPushLive[0] = false;
            }
            // None of the sets above necessarily changed what a control
            // displays (a Save moves the baseline, not the live value),
            // so their own listeners may not have fired - recompute
            // explicitly rather than relying on one.
            recomputeFieldChanged(dayBox.getSelectionModel().selectedItemProperty(), () -> baselineSupplier.get().dayOfWeek(), dayLabel);
            recomputeFieldChanged(timeField.timeProperty(), () -> baselineSupplier.get().time(), timeLabel);
            recomputeFieldChanged(typeBox.getSelectionModel().selectedItemProperty(), () -> baselineSupplier.get().type(), typeLabel);
            recomputeFieldChanged(locationField.textProperty(), () -> baselineSupplier.get().location(), locationLabel);
            setFieldChanged(slotsEditor.label, slotsChanged.apply(countsByRole(updated.slots())));
        }, slotsEditor::dispose);
    }

    private static Map<String, Integer> countsByRole(List<RoleSlot> slots) {
        Map<String, Integer> counts = new LinkedHashMap<>();
        slots.forEach(slot -> counts.put(slot.role(), slot.count()));
        return counts;
    }

    private static List<RoleSlot> toRoleSlots(Map<String, Integer> counts) {
        return counts.entrySet().stream().map(entry -> new RoleSlot(entry.getKey(), entry.getValue())).toList();
    }
}
