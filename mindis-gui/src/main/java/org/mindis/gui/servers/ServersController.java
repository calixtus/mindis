package org.mindis.gui.servers;

import io.avaje.inject.Prototype;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.scene.control.CheckBox;
import javafx.scene.control.DatePicker;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.layout.VBox;

import org.mindis.core.l10n.Localization;
import org.mindis.core.model.Role;
import org.mindis.core.model.Server;
import org.mindis.core.model.UnavailabilityPeriod;
import org.mindis.core.persistence.ServerRepository;
import org.mindis.gui.util.EnumDisplay;

/**
 * CRUD for the altar server roster. Prototype bean: a fresh controller per
 * FXML load (UI rebuilds recreate views on language change).
 */
@Prototype
public class ServersController {

    private final ServerRepository serverRepository;
    private final Map<Role, CheckBox> qualificationChecks = new EnumMap<>(Role.class);

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
    private CheckBox activeCheck;
    @FXML
    private VBox qualificationsBox;
    @FXML
    private ListView<UnavailabilityPeriod> unavailabilityList;
    @FXML
    private DatePicker periodFromPicker;
    @FXML
    private DatePicker periodToPicker;

    private Server selected;

    public ServersController(ServerRepository serverRepository) {
        this.serverRepository = serverRepository;
    }

    @FXML
    private void initialize() {
        nameColumn.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().displayName()));
        qualificationsColumn.setCellValueFactory(data -> new SimpleStringProperty(
                data.getValue().qualifications().stream()
                        .map(EnumDisplay::of)
                        .sorted()
                        .collect(Collectors.joining(", "))));
        activeColumn.setCellValueFactory(data -> new SimpleStringProperty(
                data.getValue().active() ? Localization.lang("Yes") : Localization.lang("No")));

        for (Role role : Role.values()) {
            CheckBox check = new CheckBox(EnumDisplay.of(role));
            qualificationChecks.put(role, check);
            qualificationsBox.getChildren().add(check);
        }

        unavailabilityList.setItems(FXCollections.observableArrayList());
        unavailabilityList.setCellFactory(list -> new ListCell<>() {
            @Override
            protected void updateItem(UnavailabilityPeriod period, boolean empty) {
                super.updateItem(period, empty);
                setText(empty || period == null ? null : period.start() + " - " + period.end());
            }
        });

        serversTable.getSelectionModel().selectedItemProperty()
                .addListener((obs, oldServer, newServer) -> showServer(newServer));
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
        Set<Role> qualifications = new HashSet<>();
        qualificationChecks.forEach((role, check) -> {
            if (check.isSelected()) {
                qualifications.add(role);
            }
        });
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
        serversTable.setItems(FXCollections.observableArrayList(servers));
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
        activeCheck.setSelected(server == null || server.active());
        qualificationChecks.forEach((role, check) ->
                check.setSelected(server != null && server.qualifications().contains(role)));
        unavailabilityList.getItems().setAll(server == null ? List.of() : server.unavailabilities());
        periodFromPicker.setValue(null);
        periodToPicker.setValue(null);
    }
}
