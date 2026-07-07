package org.mindis.gui.roles;

import io.avaje.inject.Prototype;

import java.util.List;
import java.util.function.IntSupplier;

import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.control.Spinner;
import javafx.scene.control.SpinnerValueFactory;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.util.StringConverter;

import org.mindis.core.model.Role;
import org.mindis.core.persistence.RoleRepository;

/**
 * CRUD for liturgical roles: name plus an optional minimum/maximum age
 * requirement (years). Prototype bean, one controller per FXML load.
 */
@Prototype
public class RolesController {

    private static final int SORT_ORDER_STEP = 10;
    private static final int MIN_AGE = 1;
    private static final int MAX_AGE = 120;

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
    private Spinner<Integer> minAgeSpinner;
    @FXML
    private Spinner<Integer> maxAgeSpinner;

    private Role selected;

    public RolesController(RoleRepository roleRepository) {
        this.roleRepository = roleRepository;
    }

    @FXML
    private void initialize() {
        nameColumn.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().name()));
        minAgeColumn.setCellValueFactory(data -> new SimpleStringProperty(ageText(data.getValue().minAge())));
        maxAgeColumn.setCellValueFactory(data -> new SimpleStringProperty(ageText(data.getValue().maxAge())));

        // Min: floor is the absolute minimum age; blank below it.
        minAgeSpinner.setValueFactory(
                new NullableAgeSpinnerValueFactory(() -> MIN_AGE));
        // Max: coupled to min. Floor is the current min (blank below it, so
        // spinning below the min age clears the upper bound); spinning up from
        // blank starts one above the min age.
        maxAgeSpinner.setValueFactory(
                new NullableAgeSpinnerValueFactory(this::minAgeOrFloor));
        // Raising the min age above the max age drags the max age up with it.
        minAgeSpinner.valueProperty().subscribe(newMin -> {
            Integer max = maxAgeSpinner.getValue();
            if (newMin != null && max != null && max < newMin) {
                maxAgeSpinner.getValueFactory().setValue(newMin);
            }
        });

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
                parseAge(minAgeSpinner.getEditor().getText()),
                parseAge(maxAgeSpinner.getEditor().getText()),
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
        // setValue drives the editor via the converter; null -> blank ("no bound").
        minAgeSpinner.getValueFactory().setValue(role == null ? null : role.minAge());
        maxAgeSpinner.getValueFactory().setValue(role == null ? null : role.maxAge());
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
            // 0 (or below the minimum real age) means "no bound".
            return value < MIN_AGE ? null : value;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private Integer minAgeOrFloor() {
        Integer min = minAgeSpinner.getValue();
        return min == null ? MIN_AGE : min;
    }

    private Integer minAgePlusOne() {
        Integer min = minAgeSpinner.getValue();
        return min == null ? MIN_AGE : min + 1;
    }

    /**
     * Editable integer spinner factory where a blank editor means {@code null}
     * ("no age bound"). The converter maps blank &harr; null so committing an
     * empty editor keeps it blank; the arrows step up to {@link #MAX_AGE} and
     * collapse to blank below the (dynamic) {@code floor}. From a blank value an
     * up-step jumps to {@code blankUpTarget}; a down-step stays blank. Dynamic
     * suppliers let the max-age spinner track the current min age.
     */
    private static final class NullableAgeSpinnerValueFactory extends SpinnerValueFactory<Integer> {

        private final IntSupplier floor;

        NullableAgeSpinnerValueFactory(IntSupplier floor) {
            this.floor = floor;
            setConverter(new StringConverter<>() {
                @Override
                public String toString(Integer value) {
                    return ageText(value);
                }

                @Override
                public Integer fromString(String text) {
                    return parseAge(text);
                }
            });
        }

        @Override
        public void increment(int steps) {
            Integer value = getValue();
            int next = value == null ? floor.getAsInt() : value + steps;
            setValue(normalize(next));
        }

        @Override
        public void decrement(int steps) {
            Integer value = getValue();
            if (value != null) {
                setValue(normalize(value - steps));
            }
        }

        /** Below the floor collapses to {@code null} (blank / no bound). */
        private Integer normalize(int value) {
            return value < floor.getAsInt() ? null : Math.min(MAX_AGE, value);
        }
    }
}
