package org.mindis.gui.modules;

import atlantafx.base.layout.InputGroup;

import java.util.Objects;
import java.util.function.IntSupplier;
import java.util.function.Supplier;

import javafx.beans.property.SimpleStringProperty;
import javafx.geometry.Insets;
import javafx.geometry.Orientation;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.Separator;
import javafx.scene.control.Spinner;
import javafx.scene.control.SpinnerValueFactory;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TextField;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.util.StringConverter;

import org.jspecify.annotations.Nullable;

import org.mindis.core.l10n.Localization;
import org.mindis.core.model.Role;
import org.mindis.core.persistence.RoleCsvMapper;
import org.mindis.core.persistence.RoleRepository;
import org.mindis.workbench.CrudModule;
import org.mindis.workbench.CsvRowMapper;
import org.mindis.workbench.LiveStore;

/// Liturgical role management module: name plus an optional minimum/maximum
/// age requirement (years). Reference implementation of {@link CrudModule}.
public class RolesModule extends CrudModule<Role> {

    private static final int MIN_AGE = 1;
    private static final int MAX_AGE = 120;

    private final RolesViewModel viewModel;

    public RolesModule(String name, LiveStore<Role> roleStore, RoleRepository roleRepository) {
        super(name, "mdi2t-tag-multiple", roleStore);
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

        Button newButton = new Button(Localization.lang("New"));
        newButton.setOnAction(event -> newItem());
        Button deleteButton = new Button(Localization.lang("Delete"));
        deleteButton.disableProperty().bind(table().getSelectionModel().selectedItemProperty().isNull());
        deleteButton.setOnAction(event -> deleteSelected());

        RoleCsvMapper roleCsvMapper = new RoleCsvMapper(roleRepository);
        CsvRowMapper<Role> csvMapper = CsvRowMapper.of(roleCsvMapper::header, roleCsvMapper::toRow, roleCsvMapper::fromRow);
        Button exportButton = new Button(Localization.lang("Export"));
        exportButton.setOnAction(event -> exportCsv(csvMapper));
        Button importButton = new Button(Localization.lang("Import"));
        importButton.setOnAction(event -> importCsv(csvMapper,
                (imported, total) -> Localization.lang("%0 of %1 rows imported", imported, total)));

        toolbarExtras().addAll(newButton, deleteButton, new Separator(Orientation.VERTICAL), exportButton, importButton);
    }

    @Override
    protected Role createStub() {
        return viewModel.createStub();
    }

    @Override
    protected EditorBinding<Role> buildEditor(Role role) {
        // Compares against the last-flushed value, not role itself - role
        // may already be a live (unsaved) edit from a previous visit to this
        // row, and comparing against itself would always read "unchanged"
        // even though it still differs from disk. A supplier, not a
        // one-time snapshot, so it reflects the post-Save baseline on a
        // later call, not whatever was last flushed when this editor was
        // built (see CrudModule#markDirtyOnChange).
        Supplier<Role> baselineSupplier = () -> Objects.requireNonNullElse(savedSnapshot(role), role);

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

        // Guards every programmatic control set below (the refresh callback,
        // and the min-age listener's own nested max-age bump) against
        // re-firing pushLive - without it, a value push here can trigger a
        // *second*, reentrant items.set() on the shared store list while an
        // outer one (e.g. a sibling row's edit, or this same edit) is still
        // unwinding through its own listener chain, which corrupts
        // JavaFX's internal ListChangeBuilder (observed as an
        // UnmodifiableList.add crash deep in ListChangeBuilder.nextRemove).
        boolean[] suppressPushLive = new boolean[1];
        Runnable pushLive = () -> {
            if (suppressPushLive[0]) {
                return;
            }
            updateLive(new Role(role.id(), nameField.getText().strip(),
                    minAgeSpinner.getValue(), maxAgeSpinner.getValue(), role.sortOrder()));
        };

        // Raising the min age above the max age drags the max age up with it.
        // addListener, not subscribe(): subscribe() fires immediately with
        // the current value at registration - since buildEditor can run
        // synchronously nested inside another mutation of the shared store
        // list (e.g. TableView reselecting a row mid-delete), that immediate
        // call would run pushLive() (via the nested maxAge set below) while
        // the outer list change was still unwinding - the same reentrant
        // items.set() corruption the suppressPushLive guard elsewhere in
        // this file targets, but from construction rather than refresh().
        minAgeSpinner.valueProperty().addListener((obs, oldMin, newMin) -> {
            Integer max = maxAgeSpinner.getValue();
            if (newMin != null && max != null && max < newMin) {
                suppressPushLive[0] = true;
                try {
                    maxAgeSpinner.getValueFactory().setValue(newMin);
                } finally {
                    suppressPushLive[0] = false;
                }
            }
            pushLive.run();
        });
        maxAgeSpinner.valueProperty().addListener((obs, oldValue, newValue) -> pushLive.run());
        nameField.textProperty().addListener((obs, oldValue, newValue) -> pushLive.run());

        GridPane grid = new GridPane();
        grid.setHgap(8);
        grid.setVgap(8);
        ColumnConstraints labelColumn = new ColumnConstraints();
        labelColumn.setMinWidth(110);
        ColumnConstraints fieldColumn = new ColumnConstraints();
        fieldColumn.setHgrow(Priority.ALWAYS);
        grid.getColumnConstraints().addAll(labelColumn, fieldColumn);

        Label nameLabel = new Label(Localization.lang("Name"));
        Label ageRangeLabel = new Label(Localization.lang("Age range"));
        grid.add(nameLabel, 0, 0);
        grid.add(nameField, 1, 0);
        grid.add(ageRangeLabel, 0, 1);
        grid.add(new InputGroup(minAgeSpinner, new Label("–"), maxAgeSpinner), 1, 1);

        VBox content = new VBox(12, grid);
        content.setPadding(new Insets(12));

        markDirtyOnChange(nameField.textProperty(), () -> baselineSupplier.get().name(), nameLabel);
        // One label covers both spinners - dirty if either differs from the
        // baseline, not just the one whose own listener last fired (two
        // independent markDirtyOnChange calls sharing a label would let an
        // unchanged spinner's own recompute clobber the accent set by the
        // other one still differing).
        Runnable recomputeAgeRangeChanged = () -> setFieldChanged(ageRangeLabel,
                !Objects.equals(minAgeSpinner.getValue(), baselineSupplier.get().minAge())
                        || !Objects.equals(maxAgeSpinner.getValue(), baselineSupplier.get().maxAge()));
        minAgeSpinner.valueProperty().addListener((obs, oldValue, newValue) -> recomputeAgeRangeChanged.run());
        maxAgeSpinner.valueProperty().addListener((obs, oldValue, newValue) -> recomputeAgeRangeChanged.run());
        recomputeAgeRangeChanged.run();

        // refresh: the row's value changed externally (e.g. a Load reverted
        // this role, or an unrelated row's edit elsewhere ran CrudModule's
        // generic re-sync - see line141 in CrudModule) - push the new value
        // into every control in place. Suppressed the same way as above:
        // these are programmatic sets, not a user edit, and Spinner's value
        // property compares by reference - not equals() - so even
        // "unchanged" Integer values can still fire the field's own listener.
        return EditorBinding.of(content, updated -> {
            suppressPushLive[0] = true;
            try {
                nameField.setText(updated.name());
                minAgeSpinner.getValueFactory().setValue(updated.minAge());
                maxAgeSpinner.getValueFactory().setValue(updated.maxAge());
            } finally {
                suppressPushLive[0] = false;
            }
            // None of the sets above necessarily changed what a control
            // displays (a Save all moves the baseline, not the live value),
            // so their own listeners may not have fired - recompute
            // explicitly rather than relying on one.
            recomputeFieldChanged(nameField.textProperty(), () -> baselineSupplier.get().name(), nameLabel);
            recomputeAgeRangeChanged.run();
        });
    }

    /// Formats a role's age range for the table: {@code "min-max"}, or one-sided
    /// ({@code "min-"} / {@code "-max"}) when only one bound is set, or empty when
    /// neither is. Uses an en dash, the typographic range separator.
    private static String ageRange(@Nullable Integer min, @Nullable Integer max) {
        if (min == null && max == null) {
            return "";
        }
        return ageText(min) + "–" + ageText(max);
    }

    private static String ageText(@Nullable Integer age) {
        return age == null ? "" : String.valueOf(age);
    }

    /// Parses an age field: blank means "no bound"; a non-numeric or negative
    /// value is treated as no bound rather than an error (the field is free-form).
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

    /// Editable integer spinner factory where a blank editor means {@code null}
    /// ("no age bound"). The converter maps blank &harr; null so committing an
    /// empty editor keeps it blank; the arrows step up to {@link #MAX_AGE} and
    /// collapse to blank below the (dynamic) {@code floor}. A dynamic floor
    /// supplier lets the max-age spinner track the current min age.
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
