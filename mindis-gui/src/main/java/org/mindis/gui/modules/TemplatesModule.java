package org.mindis.gui.modules;

import java.time.LocalTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.scene.control.ComboBox;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TextField;
import javafx.scene.layout.VBox;
import javafx.util.StringConverter;

import com.dlsc.gemsfx.TimePicker;

import org.mindis.core.l10n.EnumDisplay;
import org.mindis.core.l10n.Localization;
import org.mindis.core.l10n.RecurrenceText;
import org.mindis.core.model.Role;
import org.mindis.core.model.RoleSlot;
import org.mindis.core.model.ServiceTemplate;
import org.jspecify.annotations.Nullable;

import org.mindis.core.model.ServiceType;
import org.mindis.core.persistence.RoleRepository;
import org.mindis.core.persistence.TemplateCsvMapper;
import org.mindis.gui.util.TimePickers;
import org.mindis.gui.shell.CrudModule;
import org.mindis.gui.shell.EditorForm;
import org.mindis.gui.shell.ShellOverlays;
import org.mindis.gui.data.LiveStore;

/// Recurring service templates ("every Sunday 10:00 at St. Mary"), expanded
/// into concrete services from the Services module.
///
/// <p>A template's date pattern is a full
/// [org.mindis.core.model.ServiceSchedule], edited through
/// [ScheduleEditor] (weekly, monthly, yearly, feast-day or a rule
/// written out in text) and shown in the table as localized prose.
public final class TemplatesModule extends CrudModule<ServiceTemplate> {

    private static final double EDITOR_MIN_HEIGHT = 420;

    private final TemplatesViewModel viewModel;
    private final LiveStore<Role> roleStore;

    public TemplatesModule(String name, LiveStore<ServiceTemplate> templateStore, LiveStore<Role> roleStore,
                           RoleRepository roleRepository, ShellOverlays overlays) {
        super(name, "mdi2c-calendar-sync", templateStore, overlays);
        this.viewModel = new TemplatesViewModel(roleRepository);
        this.roleStore = roleStore;

        TableColumn<ServiceTemplate, String> dayColumn = new TableColumn<>(Localization.lang("Recurrence"));
        dayColumn.setPrefWidth(160);
        dayColumn.setCellValueFactory(data -> new SimpleStringProperty(
                RecurrenceText.describe(data.getValue().schedule())));

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

        addStandardToolbar(new TemplateCsvMapper(roleRepository));
    }

    @Override
    protected ServiceTemplate createStub() {
        return viewModel.createStub();
    }

    @Override
    protected EditorBinding<ServiceTemplate> buildEditor(ServiceTemplate template) {
        // Compares against the last-flushed value, not template itself - see
        // CrudModule#markDirtyOnChange.
        EditorForm<ServiceTemplate> form = editorForm(template);

        ScheduleEditor scheduleEditor = new ScheduleEditor(template.schedule());

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

        // Bound directly to the shared live role list - a role added, renamed
        // or removed anywhere shows up in this editor's slot rows on its own,
        // no rebuild call from here needed. Its spinners report through
        // form.edited(), which reaches the write-through callback built below.
        SlotCountEditor slotsEditor = new SlotCountEditor(roleStore.items(), countsByRole(template.slots()),
                counts -> form.edited());

        form.field(Localization.lang("Recurrence"), scheduleEditor.node(), scheduleEditor.scheduleProperty(),
                ServiceTemplate::schedule, scheduleEditor::setSchedule).topAligned();
        form.field(Localization.lang("Time"), timeField, timeField.timeProperty(),
                ServiceTemplate::time, timeField::setTime);
        form.field(Localization.lang("Type"), typeBox, typeBox.getSelectionModel().selectedItemProperty(),
                ServiceTemplate::type, typeBox.getSelectionModel()::select);
        form.field(Localization.lang("Location"), locationField, locationField.textProperty(),
                ServiceTemplate::location, locationField::setText);
        // One label spans the whole role/count list, so its accent re-diffs
        // every count against the baseline rather than watching one property.
        form.section(slotsEditor.label, slotsEditor.list(),
                label -> setFieldChanged(label,
                        !slotsEditor.collectCounts().equals(countsByRole(form.baseline().get().slots()))),
                updated -> slotsEditor.setCounts(countsByRole(updated.slots())))
                .topAligned().growing();

        form.onEdit(() -> {
            if (isSuppressingLiveUpdates()) {
                return;
            }
            LocalTime time = timeField.getTime();
            if (time == null) {
                return;
            }
            updateLive(new ServiceTemplate(template.id(), scheduleEditor.schedule(), time, template.durationMinutes(),
                    locationField.getText().strip(),
                    typeBox.getValue() == null ? ServiceType.SUNDAY_MASS : typeBox.getValue(),
                    toRoleSlots(slotsEditor.collectCounts())));
        });

        VBox content = new VBox(10, form.grid());
        content.setPadding(new Insets(12));
        content.setMinHeight(EDITOR_MIN_HEIGHT);
        return new EditorBinding<>(content, form.refresh(), slotsEditor::dispose);
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
