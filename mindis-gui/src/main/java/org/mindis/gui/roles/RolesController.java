package org.mindis.gui.roles;

import io.avaje.inject.Prototype;

import java.util.List;

import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;

import org.mindis.core.model.Role;
import org.mindis.core.persistence.RoleRepository;

/**
 * CRUD for liturgical roles: name plus an optional minimum/maximum age
 * requirement (years). Prototype bean, one controller per FXML load.
 */
@Prototype
public class RolesController {

    private static final int SORT_ORDER_STEP = 10;

    private final RoleRepository roleRepository;
    private final ObservableList<Role> tableItems = FXCollections.observableArrayList();

    @FXML
    private TableView<Role> rolesTable;
    @FXML
    private TableColumn<Role, String> nameColumn;
    @FXML
    private TableColumn<Role, String> minAgeColumn;
    @FXML
    private TableColumn<Role, String> maxAgeColumn;
    @FXML
    private TextField nameField;
    @FXML
    private TextField minAgeField;
    @FXML
    private TextField maxAgeField;

    private Role selected;

    public RolesController(RoleRepository roleRepository) {
        this.roleRepository = roleRepository;
    }

    @FXML
    private void initialize() {
        nameColumn.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().name()));
        minAgeColumn.setCellValueFactory(data -> new SimpleStringProperty(ageText(data.getValue().minAge())));
        maxAgeColumn.setCellValueFactory(data -> new SimpleStringProperty(ageText(data.getValue().maxAge())));

        rolesTable.setItems(tableItems);
        rolesTable.getSelectionModel().selectedItemProperty().subscribe(this::showRole);
        refreshTable(null);
    }

    @FXML
    private void onNew() {
        rolesTable.getSelectionModel().clearSelection();
        showRole(null);
        nameField.requestFocus();
    }

    @FXML
    private void onSave() {
        String name = nameField.getText().strip();
        if (name.isEmpty()) {
            return;
        }
        Role role = new Role(
                selected == null ? Role.newId() : selected.id(),
                name,
                parseAge(minAgeField.getText()),
                parseAge(maxAgeField.getText()),
                selected == null ? nextSortOrder() : selected.sortOrder());
        roleRepository.save(role);
        refreshTable(role.id());
    }

    @FXML
    private void onDelete() {
        if (selected != null) {
            roleRepository.delete(selected.id());
            refreshTable(null);
            showRole(null);
        }
    }

    private int nextSortOrder() {
        return roleRepository.findAll().stream()
                .mapToInt(Role::sortOrder)
                .max()
                .orElse(-SORT_ORDER_STEP) + SORT_ORDER_STEP;
    }

    private void refreshTable(String selectId) {
        List<Role> roles = roleRepository.findAll();
        tableItems.setAll(roles);
        if (selectId != null) {
            roles.stream()
                    .filter(role -> role.id().equals(selectId))
                    .findFirst()
                    .ifPresent(role -> rolesTable.getSelectionModel().select(role));
        }
    }

    private void showRole(Role role) {
        selected = role;
        nameField.setText(role == null ? "" : role.name());
        minAgeField.setText(role == null ? "" : ageText(role.minAge()));
        maxAgeField.setText(role == null ? "" : ageText(role.maxAge()));
    }

    private static String ageText(Integer age) {
        return age == null ? "" : String.valueOf(age);
    }

    /**
     * Parses an age field: blank means "no bound"; a non-numeric or negative
     * value is treated as no bound rather than an error (the field is free-form).
     */
    private static Integer parseAge(String text) {
        String trimmed = text == null ? "" : text.strip();
        if (trimmed.isEmpty()) {
            return null;
        }
        try {
            int value = Integer.parseInt(trimmed);
            return value < 0 ? null : value;
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
