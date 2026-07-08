package org.mindis.gui.modules;

import java.time.DayOfWeek;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.format.TextStyle;
import java.util.List;
import java.util.Locale;

import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.geometry.VPos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
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

    private static final double EDITOR_MIN_HEIGHT = 420;

    private final TemplatesViewModel viewModel;

    public TemplatesModule(String name, TemplateRepository templateRepository, RoleRepository roleRepository) {
        super(name, "mdi2c-calendar-sync");
        this.viewModel = new TemplatesViewModel(templateRepository, roleRepository);

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
        return viewModel.createStub();
    }

    @Override
    protected List<ServiceTemplate> loadAll() {
        return viewModel.findAll();
    }

    @Override
    protected void persist(ServiceTemplate template) {
        viewModel.save(template);
    }

    @Override
    protected void delete(ServiceTemplate template) {
        viewModel.delete(template);
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

        RoleSlotsEditor slotsEditor = new RoleSlotsEditor(viewModel.findAllRoles(), template.slots());

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

        GridPane.setValignment(slotsEditor.label, VPos.TOP);
        grid.add(slotsEditor.label, 0, row);
        GridPane.setVgrow(slotsEditor.list(), Priority.ALWAYS);
        grid.add(slotsEditor.list(), 1, row++);

        Button saveButton = new Button(Localization.lang("Save"));
        saveButton.setDefaultButton(true);
        saveButton.setOnAction(event -> {
            DayOfWeek day = dayBox.getValue();
            LocalTime time = parseTime(timeField.getText());
            if (day == null || time == null) {
                return;
            }
            save(new ServiceTemplate(template.id(), day, time, template.durationMinutes(),
                    locationField.getText().strip(),
                    typeBox.getValue() == null ? ServiceType.SUNDAY_MASS : typeBox.getValue(),
                    slotsEditor.collectSlots()));
        });

        VBox content = new VBox(10, grid, new HBox(saveButton));
        content.setPadding(new Insets(12));
        content.setMinHeight(EDITOR_MIN_HEIGHT);
        return content;
    }

    private static LocalTime parseTime(String text) {
        try {
            return LocalTime.parse(text.strip(), DateTimeFormatter.ofPattern("H:mm"));
        } catch (DateTimeParseException e) {
            return null;
        }
    }
}
