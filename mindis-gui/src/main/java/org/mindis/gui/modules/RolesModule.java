package org.mindis.gui.modules;

import atlantafx.base.layout.InputGroup;

import java.util.List;
import java.util.function.IntSupplier;

import javafx.beans.property.SimpleStringProperty;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.Spinner;
import javafx.scene.control.SpinnerValueFactory;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TextField;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.util.StringConverter;

import org.jspecify.annotations.Nullable;

import org.mindis.core.l10n.Localization;
import org.mindis.core.model.Role;
import org.mindis.core.persistence.RoleRepository;
import org.mindis.workbench.CrudModule;
import org.mindis.workbench.CsvRowMapper;

/**
 * Liturgical role management module: name plus an optional minimum/maximum
 * age requirement (years). Reference implementation of {@link CrudModule}.
 */
public class RolesModule extends CrudModule<Role> {

    private static final int MIN_AGE = 1;
    private static final int MAX_AGE = 120;

    private final RolesViewModel viewModel;

    public RolesModule(String name, RoleRepository roleRepository) {
        super(name, "mdi2t-tag-multiple");
        this.viewModel = new RolesViewModel(roleRepository);

        TableColumn<Role, String> nameColumn = new TableColumn<>(Localization.lang("Name"));
        nameColumn.setPrefWidth(200);
        nameColumn.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().name()));

        TableColumn<Role, String> ageRangeColumn = new TableColumn<>(Localization.lang("Age range"));
        ageRangeColumn.setPrefWidth(140);
        ageRangeColumn.setCellValueFactory(data -> new SimpleStringProperty(
                ageRange(data.getValue().minAge(), data.getValue().maxAge())));

        table().getColumns().add(nameColumn);
        table().getColumns().add(ageRangeColumn);
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
    protected Role createStub() {
        return viewModel.createStub();
    }

    @Override
    protected List<Role> loadAll() {
        return viewModel.findAll();
    }

    @Override
    protected void persist(Role role) {
        viewModel.save(role);
    }

    @Override
    protected void delete(Role role) {
        viewModel.delete(role);
    }

    @Override
    protected Object identity(Role role) {
        return role.id();
    }

    @Override
    protected CsvRowMapper<Role> csvMapper() {
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
    protected Node buildEditor(Role role) {
        TextField nameField = new TextField(role.name());

        Spinner<Integer> minAgeSpinner = new Spinner<>();
        Spinner<Integer> maxAgeSpinner = new Spinner<>();
        minAgeSpinner.setPrefWidth(90);
        maxAgeSpinner.setPrefWidth(90);
        minAgeSpinner.setEditable(true);
        maxAgeSpinner.setEditable(true);
        minAgeSpinner.setValueFactory(new NullableAgeSpinnerValueFactory(() -> MIN_AGE));
        maxAgeSpinner.setValueFactory(new NullableAgeSpinnerValueFactory(
                () -> minAgeSpinner.getValue() == null ? MIN_AGE : minAgeSpinner.getValue()));
        minAgeSpinner.getValueFactory().setValue(role.minAge());
        maxAgeSpinner.getValueFactory().setValue(role.maxAge());
        // Raising the min age above the max age drags the max age up with it.
        minAgeSpinner.valueProperty().subscribe(newMin -> {
            Integer max = maxAgeSpinner.getValue();
            if (newMin != null && max != null && max < newMin) {
                maxAgeSpinner.getValueFactory().setValue(newMin);
            }
        });

        GridPane grid = new GridPane();
        grid.setHgap(8);
        grid.setVgap(8);
        ColumnConstraints labelColumn = new ColumnConstraints();
        labelColumn.setMinWidth(110);
        ColumnConstraints fieldColumn = new ColumnConstraints();
        fieldColumn.setHgrow(Priority.ALWAYS);
        grid.getColumnConstraints().addAll(labelColumn, fieldColumn);

        grid.add(new Label(Localization.lang("Name")), 0, 0);
        grid.add(nameField, 1, 0);
        grid.add(new Label(Localization.lang("Age range")), 0, 1);
        grid.add(new InputGroup(minAgeSpinner, new Label("–"), maxAgeSpinner), 1, 1);

        Button saveButton = new Button(Localization.lang("Save"));
        saveButton.setDefaultButton(true);
        saveButton.setOnAction(event -> {
            String name = nameField.getText().strip();
            if (name.isEmpty()) {
                return;
            }
            save(new Role(role.id(), name,
                    minAgeSpinner.getValue(), maxAgeSpinner.getValue(), role.sortOrder()));
        });

        VBox content = new VBox(12, grid, new HBox(saveButton));
        content.setPadding(new Insets(12));
        return content;
    }

    /**
     * Formats a role's age range for the table: {@code "min-max"}, or one-sided
     * ({@code "min-"} / {@code "-max"}) when only one bound is set, or empty when
     * neither is. Uses an en dash, the typographic range separator.
     */
    private static String ageRange(@Nullable Integer min, @Nullable Integer max) {
        if (min == null && max == null) {
            return "";
        }
        return ageText(min) + "–" + ageText(max);
    }

    private static String ageText(@Nullable Integer age) {
        return age == null ? "" : String.valueOf(age);
    }

    /**
     * Parses an age field: blank means "no bound"; a non-numeric or negative
     * value is treated as no bound rather than an error (the field is free-form).
     */
    private static @Nullable Integer parseAge(@Nullable String text) {
        String trimmed = text == null ? "" : text.strip();
        if (trimmed.isEmpty()) {
            return null;
        }
        try {
            int value = Integer.parseInt(trimmed);
            return value < MIN_AGE ? null : value;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /**
     * Editable integer spinner factory where a blank editor means {@code null}
     * ("no age bound"). The converter maps blank &harr; null so committing an
     * empty editor keeps it blank; the arrows step up to {@link #MAX_AGE} and
     * collapse to blank below the (dynamic) {@code floor}. A dynamic floor
     * supplier lets the max-age spinner track the current min age.
     */
    private static final class NullableAgeSpinnerValueFactory extends SpinnerValueFactory<Integer> {

        private final IntSupplier floor;

        NullableAgeSpinnerValueFactory(IntSupplier floor) {
            this.floor = floor;
            setConverter(new StringConverter<>() {
                @Override
                public String toString(@Nullable Integer value) {
                    return ageText(value);
                }

                @Override
                public @Nullable Integer fromString(@Nullable String text) {
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

        private @Nullable Integer normalize(int value) {
            return value < floor.getAsInt() ? null : Math.min(MAX_AGE, value);
        }
    }
}
