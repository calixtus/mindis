package org.mindis.gui.modules;

import java.time.DayOfWeek;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.format.TextStyle;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.geometry.VPos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.Spinner;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TextField;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.util.StringConverter;

import org.mindis.core.l10n.EnumDisplay;
import org.mindis.core.l10n.Localization;
import org.mindis.core.model.Role;
import org.mindis.core.model.RoleSlot;
import org.mindis.core.model.ServiceTemplate;
import org.mindis.core.model.ServiceType;
import org.mindis.core.persistence.RoleRepository;
import org.mindis.core.persistence.TemplateRepository;
import org.mindis.workbench.CrudModule;

/**
 * Weekly recurring service templates ("every Sunday 10:00 at St. Mary"),
 * expanded into concrete services from the Services module.
 *
 * <p>Weekly-only for now; month/year/feast-day template types are a planned
 * extension of this module (see PLAN.md).
 */
public class TemplatesModule extends CrudModule<ServiceTemplate> {

    private static final int DEFAULT_DURATION_MINUTES = 60;
    private static final int MAX_SLOT_COUNT = 10;
    private static final double EDITOR_MIN_HEIGHT = 420;

    private final TemplateRepository templateRepository;
    private final RoleRepository roleRepository;

    public TemplatesModule(String name, TemplateRepository templateRepository, RoleRepository roleRepository) {
        super(name, "mdi2c-calendar-sync");
        this.templateRepository = templateRepository;
        this.roleRepository = roleRepository;

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
    }

    @Override
    protected String newButtonLabel() {
        return Localization.lang("New");
    }

    @Override
    protected String deleteButtonLabel() {
        return Localization.lang("Delete");
    }

    @Override
    protected ServiceTemplate createStub() {
        return new ServiceTemplate(ServiceTemplate.newId(), DayOfWeek.SUNDAY, LocalTime.of(10, 0),
                DEFAULT_DURATION_MINUTES, "", ServiceType.SUNDAY_MASS, List.of());
    }

    @Override
    protected List<ServiceTemplate> loadAll() {
        return templateRepository.findAll();
    }

    @Override
    protected void persist(ServiceTemplate template) {
        templateRepository.save(template);
    }

    @Override
    protected void delete(ServiceTemplate template) {
        templateRepository.delete(template.id());
    }

    @Override
    protected Object identity(ServiceTemplate template) {
        return template.id();
    }

    @Override
    protected Node buildEditor(ServiceTemplate template) {
        ComboBox<DayOfWeek> dayBox = new ComboBox<>(FXCollections.observableArrayList(DayOfWeek.values()));
        dayBox.setConverter(new StringConverter<>() {
            @Override
            public String toString(DayOfWeek day) {
                return day == null ? "" : day.getDisplayName(TextStyle.FULL, Locale.getDefault());
            }

            @Override
            public DayOfWeek fromString(String string) {
                return null;
            }
        });
        dayBox.getSelectionModel().select(template.dayOfWeek());

        TextField timeField = new TextField(template.time().toString());
        timeField.setPromptText("10:00");

        ComboBox<ServiceType> typeBox = new ComboBox<>(FXCollections.observableArrayList(ServiceType.values()));
        typeBox.setConverter(new StringConverter<>() {
            @Override
            public String toString(ServiceType type) {
                return type == null ? "" : EnumDisplay.of(type);
            }

            @Override
            public ServiceType fromString(String string) {
                return null;
            }
        });
        typeBox.getSelectionModel().select(template.type());

        TextField locationField = new TextField(template.location());

        // Role id -> its count spinner, in role display order.
        Map<String, Spinner<Integer>> slotSpinners = new LinkedHashMap<>();
        VBox slotsList = new VBox(8);
        for (Role role : roleRepository.findAll()) {
            Spinner<Integer> spinner = new Spinner<>(0, MAX_SLOT_COUNT, slotCount(template, role.id()));
            spinner.getStyleClass().add(Spinner.STYLE_CLASS_SPLIT_ARROWS_HORIZONTAL);
            // Trim the theme's roomy editor padding (7px 11px) so the field is
            // narrower without collapsing the split arrows. Font-relative (em),
            // not fixed pixels, so it scales with the font size.
            spinner.getEditor().setStyle("-fx-padding: 0.2em 0.4em 0.2em 0.4em;");
            slotSpinners.put(role.id(), spinner);

            Label roleName = new Label(role.name());
            roleName.setMaxWidth(Double.MAX_VALUE);
            HBox.setHgrow(roleName, Priority.ALWAYS);
            HBox row = new HBox(8, roleName, spinner);
            row.setAlignment(Pos.CENTER_LEFT);
            slotsList.getChildren().add(row);
        }

        GridPane grid = new GridPane();
        grid.setHgap(8);
        grid.setVgap(8);
        ColumnConstraints labelColumn = new ColumnConstraints();
        labelColumn.setMinWidth(110);
        ColumnConstraints fieldColumn = new ColumnConstraints();
        fieldColumn.setHgrow(Priority.ALWAYS);
        grid.getColumnConstraints().addAll(labelColumn, fieldColumn);

        int row = 0;
        grid.add(new Label(Localization.lang("Weekday")), 0, row);
        grid.add(dayBox, 1, row++);
        grid.add(new Label(Localization.lang("Time")), 0, row);
        grid.add(timeField, 1, row++);
        grid.add(new Label(Localization.lang("Type")), 0, row);
        grid.add(typeBox, 1, row++);
        grid.add(new Label(Localization.lang("Location")), 0, row);
        grid.add(locationField, 1, row++);

        Label slotsLabel = new Label(Localization.lang("Required servers"));
        GridPane.setValignment(slotsLabel, VPos.TOP);
        // Gives the label exactly the height of the first slot row and centers
        // its text in it, so the label baseline matches the first role name's
        // (which is centered against its taller spinner). Layout-driven.
        if (!slotsList.getChildren().isEmpty() && slotsList.getChildren().getFirst() instanceof HBox firstRow) {
            slotsLabel.minHeightProperty().bind(firstRow.heightProperty());
            slotsLabel.prefHeightProperty().bind(firstRow.heightProperty());
        }
        grid.add(slotsLabel, 0, row);
        GridPane.setVgrow(slotsList, Priority.ALWAYS);
        grid.add(slotsList, 1, row++);

        Button saveButton = new Button(Localization.lang("Save"));
        saveButton.setDefaultButton(true);
        saveButton.setOnAction(event -> {
            DayOfWeek day = dayBox.getValue();
            LocalTime time = parseTime(timeField.getText());
            if (day == null || time == null) {
                return;
            }
            List<RoleSlot> slots = new ArrayList<>();
            slotSpinners.forEach((roleId, spinner) -> {
                int count = spinner.getValue();
                if (count > 0) {
                    slots.add(new RoleSlot(roleId, count));
                }
            });
            save(new ServiceTemplate(template.id(), day, time, template.durationMinutes(),
                    locationField.getText().strip(),
                    typeBox.getValue() == null ? ServiceType.SUNDAY_MASS : typeBox.getValue(),
                    slots));
        });

        VBox content = new VBox(10, grid, new HBox(saveButton));
        content.setPadding(new Insets(12));
        content.setMinHeight(EDITOR_MIN_HEIGHT);
        return content;
    }

    private static int slotCount(ServiceTemplate template, String roleId) {
        return template.slots().stream()
                .filter(slot -> slot.role().equals(roleId))
                .mapToInt(RoleSlot::count)
                .findFirst()
                .orElse(0);
    }

    private static LocalTime parseTime(String text) {
        try {
            return LocalTime.parse(text.strip(), DateTimeFormatter.ofPattern("H:mm"));
        } catch (DateTimeParseException e) {
            return null;
        }
    }
}
