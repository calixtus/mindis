package org.mindis.gui.services;

import io.avaje.inject.Prototype;

import java.time.DayOfWeek;
import java.time.LocalDate;
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
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.geometry.Pos;
import javafx.scene.control.ComboBox;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Label;
import javafx.scene.control.Spinner;
import javafx.scene.control.SplitPane;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.util.StringConverter;

import org.mindis.core.model.LiturgicalService;
import org.mindis.core.model.Role;
import org.mindis.core.model.RoleSlot;
import org.mindis.core.model.ServiceTemplate;
import org.mindis.core.model.ServiceType;
import org.mindis.core.persistence.RoleRepository;
import org.mindis.core.persistence.ServiceGenerator;
import org.mindis.core.persistence.ServiceRepository;
import org.mindis.core.persistence.TemplateRepository;
import org.mindis.core.l10n.EnumDisplay;

/**
 * CRUD for liturgical services plus weekly templates and horizon generation.
 * Prototype bean: fresh controller per FXML load.
 */
@Prototype
public class ServicesController {

    private static final DateTimeFormatter DATE_TIME_FORMAT = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm");
    private static final int DEFAULT_DURATION_MINUTES = 60;
    private static final int MAX_SLOT_COUNT = 10;

    private final ServiceRepository serviceRepository;
    private final TemplateRepository templateRepository;
    private final RoleRepository roleRepository;
    // Role id -> its count spinner, in role display order.
    private final Map<String, Spinner<Integer>> slotSpinners = new LinkedHashMap<>();
    private final ObservableList<LiturgicalService> serviceItems = FXCollections.observableArrayList();
    private final ObservableList<ServiceTemplate> templateItems = FXCollections.observableArrayList();

    @FXML
    private TableView<LiturgicalService> servicesTable;
    @FXML
    private TableColumn<LiturgicalService, String> dateColumn;
    @FXML
    private TableColumn<LiturgicalService, String> typeColumn;
    @FXML
    private TableColumn<LiturgicalService, String> locationColumn;
    @FXML
    private TableColumn<LiturgicalService, String> slotsColumn;
    @FXML
    private DatePicker serviceDatePicker;
    @FXML
    private TextField serviceTimeField;
    @FXML
    private ComboBox<ServiceType> serviceTypeBox;
    @FXML
    private TextField serviceLocationField;
    @FXML
    private TextField serviceNoteField;
    @FXML
    private SplitPane root;
    @FXML
    private Label slotsLabel;
    @FXML
    private VBox slotsList;
    @FXML
    private DatePicker generateFromPicker;
    @FXML
    private DatePicker generateToPicker;
    @FXML
    private TableView<ServiceTemplate> templatesTable;
    @FXML
    private TableColumn<ServiceTemplate, String> templateDayColumn;
    @FXML
    private TableColumn<ServiceTemplate, String> templateTimeColumn;
    @FXML
    private TableColumn<ServiceTemplate, String> templateTypeColumn;
    @FXML
    private TableColumn<ServiceTemplate, String> templateLocationColumn;
    @FXML
    private ComboBox<DayOfWeek> templateDayBox;
    @FXML
    private TextField templateTimeField;
    @FXML
    private ComboBox<ServiceType> templateTypeBox;
    @FXML
    private TextField templateLocationField;

    private LiturgicalService selected;

    public ServicesController(ServiceRepository serviceRepository, TemplateRepository templateRepository,
                              RoleRepository roleRepository) {
        this.serviceRepository = serviceRepository;
        this.templateRepository = templateRepository;
        this.roleRepository = roleRepository;
    }

    @FXML
    private void initialize() {
        dateColumn.setCellValueFactory(data -> new SimpleStringProperty(
                data.getValue().dateTime().format(DATE_TIME_FORMAT)));
        typeColumn.setCellValueFactory(data -> new SimpleStringProperty(EnumDisplay.of(data.getValue().type())));
        locationColumn.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().location()));
        slotsColumn.setCellValueFactory(data -> new SimpleStringProperty(
                String.valueOf(data.getValue().totalSlots())));

        setupTypeBox(serviceTypeBox);
        setupTypeBox(templateTypeBox);

        templateDayBox.setItems(FXCollections.observableArrayList(DayOfWeek.values()));
        templateDayBox.setConverter(new StringConverter<>() {
            @Override
            public String toString(DayOfWeek day) {
                return day == null ? "" : day.getDisplayName(TextStyle.FULL, Locale.getDefault());
            }

            @Override
            public DayOfWeek fromString(String string) {
                return null;
            }
        });

        // Rebuild the role slot editor whenever the module is shown, so roles
        // added or removed in the Roles module are picked up (the view is cached,
        // so building once in initialize() would go stale).
        root.sceneProperty().subscribe(scene -> {
            if (scene != null) {
                rebuildSlots();
            }
        });

        templateDayColumn.setCellValueFactory(data -> new SimpleStringProperty(
                data.getValue().dayOfWeek().getDisplayName(TextStyle.FULL, Locale.getDefault())));
        templateTimeColumn.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().time().toString()));
        templateTypeColumn.setCellValueFactory(data -> new SimpleStringProperty(EnumDisplay.of(data.getValue().type())));
        templateLocationColumn.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().location()));

        servicesTable.setItems(serviceItems);
        templatesTable.setItems(templateItems);
        servicesTable.getSelectionModel().selectedItemProperty().subscribe(this::showService);

        refreshServices(null);
        refreshTemplates();
    }

    @FXML
    private void onNewService() {
        servicesTable.getSelectionModel().clearSelection();
        showService(null);
        serviceDatePicker.requestFocus();
    }

    @FXML
    private void onSaveService() {
        LocalDate date = serviceDatePicker.getValue();
        LocalTime time = parseTime(serviceTimeField.getText());
        if (date == null || time == null) {
            return;
        }
        LiturgicalService service = new LiturgicalService(
                selected == null ? LiturgicalService.newId() : selected.id(),
                date.atTime(time),
                selected == null ? DEFAULT_DURATION_MINUTES : selected.durationMinutes(),
                serviceLocationField.getText().strip(),
                serviceTypeBox.getValue() == null ? ServiceType.OTHER : serviceTypeBox.getValue(),
                collectSlots(),
                serviceNoteField.getText().strip());
        serviceRepository.save(service);
        refreshServices(service.id());
    }

    @FXML
    private void onDeleteService() {
        if (selected != null) {
            serviceRepository.delete(selected.id());
            refreshServices(null);
            showService(null);
        }
    }

    @FXML
    private void onGenerate() {
        LocalDate from = generateFromPicker.getValue();
        LocalDate to = generateToPicker.getValue();
        if (from == null || to == null || to.isBefore(from)) {
            return;
        }
        List<LiturgicalService> generated = ServiceGenerator.generate(
                templateRepository.findAll(), serviceRepository.findAll(), from, to);
        serviceRepository.saveAll(generated);
        refreshServices(null);
    }

    @FXML
    private void onAddTemplate() {
        DayOfWeek day = templateDayBox.getValue();
        LocalTime time = parseTime(templateTimeField.getText());
        if (day == null || time == null) {
            return;
        }
        ServiceTemplate template = new ServiceTemplate(
                ServiceTemplate.newId(),
                day,
                time,
                DEFAULT_DURATION_MINUTES,
                templateLocationField.getText().strip(),
                templateTypeBox.getValue() == null ? ServiceType.SUNDAY_MASS : templateTypeBox.getValue(),
                collectSlots());
        templateRepository.save(template);
        refreshTemplates();
    }

    @FXML
    private void onRemoveTemplate() {
        ServiceTemplate template = templatesTable.getSelectionModel().getSelectedItem();
        if (template != null) {
            templateRepository.delete(template.id());
            refreshTemplates();
        }
    }

    private void setupTypeBox(ComboBox<ServiceType> box) {
        box.setItems(FXCollections.observableArrayList(ServiceType.values()));
        box.setConverter(new StringConverter<>() {
            @Override
            public String toString(ServiceType type) {
                return type == null ? "" : EnumDisplay.of(type);
            }

            @Override
            public ServiceType fromString(String string) {
                return null;
            }
        });
        box.getSelectionModel().select(ServiceType.SUNDAY_MASS);
    }

    /**
     * Rebuilds the role slot editor from the current roles: one compact row per
     * role (name on the left, a small split-arrow count spinner on the right),
     * matching the form fields above. Preserves the selected service's counts.
     */
    private void rebuildSlots() {
        slotSpinners.clear();
        slotsList.getChildren().clear();
        for (Role role : roleRepository.findAll()) {
            Spinner<Integer> spinner = new Spinner<>(0, MAX_SLOT_COUNT, 0);
            spinner.getStyleClass().add(Spinner.STYLE_CLASS_SPLIT_ARROWS_HORIZONTAL);
            // Trim the theme's roomy editor padding (7px 11px) so the field is
            // narrower without collapsing the split arrows. Font-relative (em),
            // not fixed pixels, so it scales with the font size.
            spinner.getEditor().setStyle("-fx-padding: 0.2em 0.4em 0.2em 0.4em;");
            slotSpinners.put(role.id(), spinner);

            Label name = new Label(role.name());
            name.setMaxWidth(Double.MAX_VALUE);
            HBox.setHgrow(name, Priority.ALWAYS);
            HBox row = new HBox(8, name, spinner);
            row.setAlignment(Pos.CENTER_LEFT);
            slotsList.getChildren().add(row);
        }
        alignSlotsLabel();
        applySlots(selected);
    }

    /**
     * Gives the "Required servers" label exactly the height of the first slot
     * row and centers its text in it, so the label baseline matches the first
     * role name's (which is centered against its taller spinner). Layout-driven,
     * no hardcoded offsets; rebound on every rebuild since rows are recreated.
     */
    private void alignSlotsLabel() {
        slotsLabel.minHeightProperty().unbind();
        slotsLabel.prefHeightProperty().unbind();
        if (!slotsList.getChildren().isEmpty()
                && slotsList.getChildren().getFirst() instanceof HBox firstRow) {
            slotsLabel.minHeightProperty().bind(firstRow.heightProperty());
            slotsLabel.prefHeightProperty().bind(firstRow.heightProperty());
        }
    }

    /** Sets each slot spinner to the count the service requires for that role. */
    private void applySlots(LiturgicalService service) {
        slotSpinners.forEach((roleId, spinner) -> spinner.getValueFactory().setValue(
                service == null
                        ? 0
                        : service.slots().stream()
                                .filter(slot -> slot.role().equals(roleId))
                                .mapToInt(RoleSlot::count)
                                .findFirst()
                                .orElse(0)));
    }

    private List<RoleSlot> collectSlots() {
        List<RoleSlot> slots = new ArrayList<>();
        slotSpinners.forEach((roleId, spinner) -> {
            int count = spinner.getValue();
            if (count > 0) {
                slots.add(new RoleSlot(roleId, count));
            }
        });
        return slots;
    }

    private void refreshServices(String selectId) {
        List<LiturgicalService> services = serviceRepository.findAll();
        serviceItems.setAll(services);
        if (selectId != null) {
            services.stream()
                    .filter(service -> service.id().equals(selectId))
                    .findFirst()
                    .ifPresent(service -> servicesTable.getSelectionModel().select(service));
        }
    }

    private void refreshTemplates() {
        templateItems.setAll(templateRepository.findAll());
    }

    private void showService(LiturgicalService service) {
        selected = service;
        serviceDatePicker.setValue(service == null ? null : service.dateTime().toLocalDate());
        serviceTimeField.setText(service == null ? "" : service.dateTime().toLocalTime().toString());
        serviceTypeBox.getSelectionModel().select(service == null ? ServiceType.SUNDAY_MASS : service.type());
        serviceLocationField.setText(service == null ? "" : service.location());
        serviceNoteField.setText(service == null ? "" : service.note());
        applySlots(service);
    }

    private static LocalTime parseTime(String text) {
        try {
            return LocalTime.parse(text.strip(), DateTimeFormatter.ofPattern("H:mm"));
        } catch (DateTimeParseException e) {
            return null;
        }
    }
}
