package org.mindis.gui.modules;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.List;

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

import com.dlsc.gemsfx.CalendarPicker;

import org.mindis.core.l10n.EnumDisplay;
import org.mindis.core.l10n.Localization;
import org.mindis.core.model.LiturgicalService;
import org.mindis.core.model.ServiceType;
import org.mindis.core.persistence.RoleRepository;
import org.jspecify.annotations.Nullable;

import org.mindis.core.persistence.ServiceRepository;
import org.mindis.core.persistence.TemplateRepository;
import org.mindis.gui.util.CalendarPickers;
import org.mindis.workbench.CrudModule;
import org.mindis.workbench.CsvRowMapper;

/**
 * Liturgical services module: individual date/time services plus generation
 * from weekly templates (managed in the Templates module).
 */
public class ServicesModule extends CrudModule<LiturgicalService> {

    private static final DateTimeFormatter DATE_TIME_FORMAT = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm");
    private static final double EDITOR_MIN_HEIGHT = 520;

    private final ServicesViewModel viewModel;

    public ServicesModule(String name, ServiceRepository serviceRepository, TemplateRepository templateRepository,
                          RoleRepository roleRepository) {
        super(name, "mdi2c-church");
        this.viewModel = new ServicesViewModel(serviceRepository, templateRepository, roleRepository);

        TableColumn<LiturgicalService, String> dateColumn = new TableColumn<>(Localization.lang("Date"));
        dateColumn.setPrefWidth(140);
        dateColumn.setCellValueFactory(data -> new SimpleStringProperty(
                data.getValue().dateTime().format(DATE_TIME_FORMAT)));

        TableColumn<LiturgicalService, String> typeColumn = new TableColumn<>(Localization.lang("Type"));
        typeColumn.setPrefWidth(120);
        typeColumn.setCellValueFactory(data -> new SimpleStringProperty(EnumDisplay.of(data.getValue().type())));

        TableColumn<LiturgicalService, String> locationColumn = new TableColumn<>(Localization.lang("Location"));
        locationColumn.setPrefWidth(120);
        locationColumn.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().location()));

        TableColumn<LiturgicalService, String> slotsColumn =
                new TableColumn<>(Localization.lang("Required servers"));
        slotsColumn.setPrefWidth(130);
        slotsColumn.setCellValueFactory(data -> new SimpleStringProperty(
                String.valueOf(data.getValue().totalSlots())));

        table().getColumns().add(dateColumn);
        table().getColumns().add(typeColumn);
        table().getColumns().add(locationColumn);
        table().getColumns().add(slotsColumn);

        CalendarPicker fromPicker = CalendarPickers.create();
        fromPicker.setPromptText(Localization.lang("From"));
        fromPicker.setPrefWidth(130);
        CalendarPicker toPicker = CalendarPickers.create();
        toPicker.setPromptText(Localization.lang("To"));
        toPicker.setPrefWidth(130);
        Button generateButton = new Button(Localization.lang("Generate from templates"));
        generateButton.setOnAction(event -> {
            if (viewModel.generateFromTemplates(fromPicker.getValue(), toPicker.getValue())) {
                refresh();
            }
        });
        toolbarExtras().add(new Label(Localization.lang("From")));
        toolbarExtras().add(fromPicker);
        toolbarExtras().add(new Label(Localization.lang("To")));
        toolbarExtras().add(toPicker);
        toolbarExtras().add(generateButton);
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
    protected LiturgicalService createStub() {
        return viewModel.createStub();
    }

    @Override
    protected List<LiturgicalService> loadAll() {
        return viewModel.findAll();
    }

    @Override
    protected void persist(LiturgicalService service) {
        viewModel.save(service);
    }

    @Override
    protected void delete(LiturgicalService service) {
        viewModel.delete(service);
    }

    @Override
    protected Object identity(LiturgicalService service) {
        return service.id();
    }

    @Override
    protected CsvRowMapper<LiturgicalService> csvMapper() {
        return CsvRowMapper.of(viewModel::csvHeader, viewModel::toCsvRow, viewModel::fromCsvRow);
    }

    @Override
    protected String exportButtonLabel() {
        return Localization.lang("Export");
    }

    @Override
    protected String importButtonLabel() {
        return Localization.lang("Import");
    }

    @Override
    protected String importSummary(int imported, int total) {
        return Localization.lang("%0 of %1 rows imported", imported, total);
    }

    @Override
    protected Node buildEditor(LiturgicalService service) {
        CalendarPicker dateField = CalendarPickers.create();
        dateField.setValue(service.dateTime().toLocalDate());
        TextField timeField = new TextField(service.dateTime().toLocalTime().toString());
        timeField.setPromptText("10:00");

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
        typeBox.getSelectionModel().select(service.type());

        TextField locationField = new TextField(service.location());
        TextField noteField = new TextField(service.note());

        RoleSlotsEditor slotsEditor = new RoleSlotsEditor(viewModel.findAllRoles(), service.slots());

        GridPane grid = new GridPane();
        grid.setHgap(8);
        grid.setVgap(8);
        ColumnConstraints labelColumn = new ColumnConstraints();
        labelColumn.setMinWidth(110);
        ColumnConstraints fieldColumn = new ColumnConstraints();
        fieldColumn.setHgrow(Priority.ALWAYS);
        grid.getColumnConstraints().addAll(labelColumn, fieldColumn);

        int row = 0;
        grid.add(new Label(Localization.lang("Date")), 0, row);
        grid.add(dateField, 1, row++);
        grid.add(new Label(Localization.lang("Time")), 0, row);
        grid.add(timeField, 1, row++);
        grid.add(new Label(Localization.lang("Type")), 0, row);
        grid.add(typeBox, 1, row++);
        grid.add(new Label(Localization.lang("Location")), 0, row);
        grid.add(locationField, 1, row++);
        grid.add(new Label(Localization.lang("Note")), 0, row);
        grid.add(noteField, 1, row++);

        GridPane.setValignment(slotsEditor.label, VPos.TOP);
        grid.add(slotsEditor.label, 0, row);
        GridPane.setVgrow(slotsEditor.list(), Priority.ALWAYS);
        grid.add(slotsEditor.list(), 1, row++);

        Button saveButton = new Button(Localization.lang("Save"));
        saveButton.setDefaultButton(true);
        saveButton.setOnAction(event -> {
            LocalDate date = dateField.getValue();
            LocalTime time = parseTime(timeField.getText());
            if (date == null || time == null) {
                return;
            }
            save(new LiturgicalService(service.id(), date.atTime(time), service.durationMinutes(),
                    locationField.getText().strip(),
                    typeBox.getValue() == null ? ServiceType.OTHER : typeBox.getValue(),
                    slotsEditor.collectSlots(), noteField.getText().strip()));
        });

        VBox content = new VBox(10, grid, new HBox(saveButton));
        content.setPadding(new Insets(12));
        content.setMinHeight(EDITOR_MIN_HEIGHT);
        return content;
    }

    private static @Nullable LocalTime parseTime(String text) {
        try {
            return LocalTime.parse(text.strip(), DateTimeFormatter.ofPattern("H:mm"));
        } catch (DateTimeParseException e) {
            return null;
        }
    }
}
