package org.mindis.gui.servers;

import io.avaje.inject.Prototype;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import javafx.beans.binding.Bindings;
import javafx.beans.binding.DoubleBinding;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.control.CheckBox;
import javafx.scene.control.DatePicker;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.SplitPane;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.control.cell.CheckBoxListCell;
import javafx.util.StringConverter;

import org.mindis.core.l10n.Localization;
import org.mindis.core.model.Role;
import org.mindis.core.model.Server;
import org.mindis.core.model.UnavailabilityPeriod;
import org.mindis.core.persistence.RoleRepository;
import org.mindis.core.persistence.ServerRepository;
import org.mindis.gui.preferences.UiPreferences;

/**
 * CRUD for the altar server roster. Prototype bean: a fresh controller per
 * FXML load (UI rebuilds recreate views on language change).
 */
@Prototype
public class ServersController {

    // Checkbox list row height as a multiple of the app font size.
    private static final double CELL_SIZE_FONT_FACTOR = 2.0;

    private final ServerRepository serverRepository;
    private final RoleRepository roleRepository;
    private final UiPreferences uiPreferences;
    // Role id -> whether its qualification checkbox is ticked, shared with the
    // CheckBoxListCell so ticks survive list rebuilds.
    private final Map<String, BooleanProperty> qualificationSelected = new HashMap<>();
    private final Map<String, String> roleNames = new LinkedHashMap<>();
    private final ObservableList<Role> qualificationRoles = FXCollections.observableArrayList();
    private final ObservableList<Server> tableItems = FXCollections.observableArrayList();

    @FXML
    private SplitPane root;
    @FXML
    private TableView<Server> serversTable;
    @FXML
    private TableColumn<Server, String> nameColumn;
    @FXML
    private TableColumn<Server, String> qualificationsColumn;
    @FXML
    private TableColumn<Server, String> activeColumn;
    @FXML
    private TextField firstNameField;
    @FXML
    private TextField lastNameField;
    @FXML
    private TextField contactField;
    @FXML
    private DatePicker birthDatePicker;
    @FXML
    private TextField familyIdField;
    @FXML
    private TextField preferredTimesField;
    @FXML
    private CheckBox experiencedCheck;
    @FXML
    private CheckBox activeCheck;
    @FXML
    private ListView<Role> qualificationsList;
    @FXML
    private ListView<UnavailabilityPeriod> unavailabilityList;
    @FXML
    private DatePicker periodFromPicker;
    @FXML
    private DatePicker periodToPicker;

    private Server selected;

    public ServersController(ServerRepository serverRepository, RoleRepository roleRepository,
                             UiPreferences uiPreferences) {
        this.serverRepository = serverRepository;
        this.roleRepository = roleRepository;
        this.uiPreferences = uiPreferences;
    }

    @FXML
    private void initialize() {
        nameColumn.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().displayName()));
        qualificationsColumn.setCellValueFactory(data -> new SimpleStringProperty(
                data.getValue().qualifications().stream()
                        .map(id -> roleNames.getOrDefault(id, id))
                        .sorted()
                        .collect(Collectors.joining(", "))));
        activeColumn.setCellValueFactory(data -> new SimpleStringProperty(
                data.getValue().active() ? Localization.lang("Yes") : Localization.lang("No")));

        // Row height scales with the app font size (keeps rows compact and
        // legible when the user changes the font in Settings).
        DoubleBinding cellSize = Bindings.createDoubleBinding(
                () -> uiPreferences.fontSizeProperty().get() * CELL_SIZE_FONT_FACTOR,
                uiPreferences.fontSizeProperty());
        qualificationsList.fixedCellSizeProperty().bind(cellSize);
        unavailabilityList.fixedCellSizeProperty().bind(cellSize);

        qualificationsList.setItems(qualificationRoles);
        qualificationsList.setCellFactory(CheckBoxListCell.forListView(
                role -> qualificationSelected.computeIfAbsent(role.id(), id -> new SimpleBooleanProperty()),
                new StringConverter<>() {
                    @Override
                    public String toString(Role role) {
                        return role == null ? "" : role.name();
                    }

                    @Override
                    public Role fromString(String string) {
                        return null;
                    }
                }));
        // Re-read the roles every time the tab is shown so roles added or
        // deleted in the Roles tab are reflected here (the module caches its
        // view, so initialize() alone would go stale).
        root.sceneProperty().subscribe(scene -> {
            if (scene != null) {
                refreshRoles();
            }
        });
        refreshRoles();

        unavailabilityList.setItems(FXCollections.observableArrayList());
        unavailabilityList.setCellFactory(list -> new ListCell<>() {
            @Override
            protected void updateItem(UnavailabilityPeriod period, boolean empty) {
                super.updateItem(period, empty);
                setText(empty || period == null ? null : period.start() + " - " + period.end());
            }
        });

        serversTable.setItems(tableItems);
        serversTable.getSelectionModel().selectedItemProperty().subscribe(this::showServer);
        refreshTable(null);
    }

    @FXML
    private void onNew() {
        serversTable.getSelectionModel().clearSelection();
        showServer(null);
        firstNameField.requestFocus();
    }

    @FXML
    private void onSave() {
        String firstName = firstNameField.getText().strip();
        String lastName = lastNameField.getText().strip();
        if (firstName.isEmpty() && lastName.isEmpty()) {
            return;
        }
        Set<String> qualifications = new HashSet<>();
        for (Role role : qualificationRoles) {
            BooleanProperty ticked = qualificationSelected.get(role.id());
            if (ticked != null && ticked.get()) {
                qualifications.add(role.id());
            }
        }
        String familyId = familyIdField.getText().strip();
        Server server = new Server(
                selected == null ? Server.newId() : selected.id(),
                firstName,
                lastName,
                contactField.getText().strip(),
                birthDatePicker.getValue(),
                familyId.isEmpty() ? null : familyId,
                qualifications,
                new ArrayList<>(unavailabilityList.getItems()),
                parsePreferredTimes(preferredTimesField.getText()),
                experiencedCheck.isSelected(),
                activeCheck.isSelected());
        serverRepository.save(server);
        refreshTable(server.id());
    }

    @FXML
    private void onDelete() {
        if (selected != null) {
            serverRepository.delete(selected.id());
            refreshTable(null);
            showServer(null);
        }
    }

    @FXML
    private void onAddPeriod() {
        LocalDate from = periodFromPicker.getValue();
        LocalDate to = periodToPicker.getValue();
        if (from == null || to == null || to.isBefore(from)) {
            return;
        }
        unavailabilityList.getItems().add(new UnavailabilityPeriod(from, to));
        periodFromPicker.setValue(null);
        periodToPicker.setValue(null);
    }

    @FXML
    private void onRemovePeriod() {
        UnavailabilityPeriod period = unavailabilityList.getSelectionModel().getSelectedItem();
        if (period != null) {
            unavailabilityList.getItems().remove(period);
        }
    }

    private void refreshTable(String selectId) {
        List<Server> servers = serverRepository.findAll();
        tableItems.setAll(servers);
        if (selectId != null) {
            servers.stream()
                    .filter(server -> server.id().equals(selectId))
                    .findFirst()
                    .ifPresent(server -> serversTable.getSelectionModel().select(server));
        }
    }

    private void showServer(Server server) {
        selected = server;
        firstNameField.setText(server == null ? "" : server.firstName());
        lastNameField.setText(server == null ? "" : server.lastName());
        contactField.setText(server == null ? "" : server.contact());
        birthDatePicker.setValue(server == null ? null : server.birthDate());
        familyIdField.setText(server == null || server.familyId() == null ? "" : server.familyId());
        preferredTimesField.setText(server == null ? "" : formatPreferredTimes(server.preferredTimes()));
        experiencedCheck.setSelected(server != null && server.experienced());
        activeCheck.setSelected(server == null || server.active());
        showQualifications(server);
        unavailabilityList.getItems().setAll(server == null ? List.of() : server.unavailabilities());
        periodFromPicker.setValue(null);
        periodToPicker.setValue(null);
    }

    /**
     * Reloads the qualification checkbox list from the current roles (picking up
     * roles added or deleted meanwhile) and re-applies the shown server's ticks.
     */
    private void refreshRoles() {
        List<Role> roles = roleRepository.findAll();
        roleNames.clear();
        roles.forEach(role -> roleNames.put(role.id(), role.name()));
        roles.forEach(role -> qualificationSelected.computeIfAbsent(role.id(), id -> new SimpleBooleanProperty()));
        qualificationRoles.setAll(roles);
        showQualifications(selected);
        serversTable.refresh();
    }

    private void showQualifications(Server server) {
        for (Role role : qualificationRoles) {
            qualificationSelected.get(role.id())
                    .set(server != null && server.qualifications().contains(role.id()));
        }
    }

    /**
     * Parses "10:00, 18:30" style input; unparsable entries are dropped.
     */
    private static Set<LocalTime> parsePreferredTimes(String text) {
        Set<LocalTime> times = new HashSet<>();
        for (String part : text.split(",")) {
            try {
                times.add(LocalTime.parse(part.strip(), DateTimeFormatter.ofPattern("H:mm")));
            } catch (DateTimeParseException e) {
                // Ignore invalid entries; the field is free-form.
            }
        }
        return times;
    }

    private static String formatPreferredTimes(Set<LocalTime> times) {
        return times.stream()
                .sorted()
                .map(LocalTime::toString)
                .collect(Collectors.joining(", "));
    }
}
