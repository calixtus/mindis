package org.mindis.gui.modules;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.geometry.VPos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.DatePicker;
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
import org.mindis.core.model.LiturgicalService;
import org.mindis.core.model.Role;
import org.mindis.core.model.RoleSlot;
import org.mindis.core.model.ServiceType;
import org.mindis.core.persistence.RoleRepository;
import org.mindis.core.persistence.ServiceGenerator;
import org.mindis.core.persistence.ServiceRepository;
import org.mindis.core.persistence.TemplateRepository;
import org.mindis.workbench.CrudModule;

/**
 * Liturgical services module: individual date/time services plus generation
 * from weekly templates (managed in the Templates module).
 */
public class ServicesModule extends CrudModule<LiturgicalService> {

    private static final DateTimeFormatter DATE_TIME_FORMAT = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm");
    private static final int DEFAULT_DURATION_MINUTES = 60;
    private static final int MAX_SLOT_COUNT = 10;
    private static final double EDITOR_MIN_HEIGHT = 520;

    private final ServiceRepository serviceRepository;
    private final TemplateRepository templateRepository;
    private final RoleRepository roleRepository;

    public ServicesModule(String name, ServiceRepository serviceRepository, TemplateRepository templateRepository,
                          RoleRepository roleRepository) {
        super(name, "mdi2c-church");
        this.serviceRepository = serviceRepository;
        this.templateRepository = templateRepository;
        this.roleRepository = roleRepository;

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

        DatePicker fromPicker = new DatePicker();
        fromPicker.setPromptText(Localization.lang("From"));
        fromPicker.setPrefWidth(130);
        DatePicker toPicker = new DatePicker();
        toPicker.setPromptText(Localization.lang("To"));
        toPicker.setPrefWidth(130);
        Button generateButton = new Button(Localization.lang("Generate from templates"));
        generateButton.setOnAction(event -> {
            LocalDate from = fromPicker.getValue();
            LocalDate to = toPicker.getValue();
            if (from == null || to == null || to.isBefore(from)) {
                return;
            }
            List<LiturgicalService> generated = ServiceGenerator.generate(
                    templateRepository.findAll(), serviceRepository.findAll(), from, to);
            serviceRepository.saveAll(generated);
            refresh();
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
        LocalDateTime nextFullHour = LocalDateTime.now()
                .withMinute(0).withSecond(0).withNano(0).plusHours(1);
        return new LiturgicalService(LiturgicalService.newId(), nextFullHour, DEFAULT_DURATION_MINUTES,
                "", ServiceType.OTHER, List.of(), "");
    }

    @Override
    protected List<LiturgicalService> loadAll() {
        return serviceRepository.findAll();
    }

    @Override
    protected void persist(LiturgicalService service) {
        serviceRepository.save(service);
    }

    @Override
    protected void delete(LiturgicalService service) {
        serviceRepository.delete(service.id());
    }

    @Override
    protected Object identity(LiturgicalService service) {
        return service.id();
    }

    @Override
    protected Node buildEditor(LiturgicalService service) {
        DatePicker dateField = new DatePicker(service.dateTime().toLocalDate());
        TextField timeField = new TextField(service.dateTime().toLocalTime().toString());
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
        typeBox.getSelectionModel().select(service.type());

        TextField locationField = new TextField(service.location());
        TextField noteField = new TextField(service.note());

        // Role id -> its count spinner, in role display order.
        Map<String, Spinner<Integer>> slotSpinners = new LinkedHashMap<>();
        VBox slotsList = new VBox(8);
        for (Role role : roleRepository.findAll()) {
            Spinner<Integer> spinner = new Spinner<>(0, MAX_SLOT_COUNT, slotCount(service, role.id()));
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
            LocalDate date = dateField.getValue();
            LocalTime time = parseTime(timeField.getText());
            if (date == null || time == null) {
                return;
            }
            List<RoleSlot> slots = new ArrayList<>();
            slotSpinners.forEach((roleId, spinner) -> {
                int count = spinner.getValue();
                if (count > 0) {
                    slots.add(new RoleSlot(roleId, count));
                }
            });
            save(new LiturgicalService(service.id(), date.atTime(time), service.durationMinutes(),
                    locationField.getText().strip(),
                    typeBox.getValue() == null ? ServiceType.OTHER : typeBox.getValue(),
                    slots, noteField.getText().strip()));
        });

        VBox content = new VBox(10, grid, new HBox(saveButton));
        content.setPadding(new Insets(12));
        content.setMinHeight(EDITOR_MIN_HEIGHT);
        return content;
    }

    private static int slotCount(LiturgicalService service, String roleId) {
        return service.slots().stream()
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
