package org.mindis.gui.modules;

import atlantafx.base.layout.InputGroup;

import java.util.Objects;
import java.util.function.IntSupplier;

import javafx.beans.property.SimpleStringProperty;
import javafx.geometry.Insets;
import javafx.scene.control.Label;
import javafx.scene.control.Spinner;
import javafx.scene.control.SpinnerValueFactory;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TextField;
import javafx.scene.layout.VBox;
import javafx.util.StringConverter;

import org.jspecify.annotations.Nullable;

import org.mindis.core.l10n.Localization;
import org.mindis.core.model.Role;
import org.mindis.core.persistence.RoleCsvMapper;
import org.mindis.core.persistence.RoleRepository;
import org.mindis.gui.shell.CrudModule;
import org.mindis.gui.shell.EditorForm;
import org.mindis.gui.shell.ShellOverlays;
import org.mindis.gui.data.LiveStore;

/// Liturgical role management module: name plus an optional minimum/maximum
/// age requirement (years). Reference implementation of [CrudModule].
public final class RolesModule extends CrudModule<Role> {

    private static final int MIN_AGE = 1;
    private static final int MAX_AGE = 120;

    private final RolesViewModel viewModel;

    public RolesModule(String name, LiveStore<Role> roleStore, RoleRepository roleRepository,
                       ShellOverlays overlays) {
        super(name, "mdi2t-tag-multiple", roleStore, overlays);
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

        addStandardToolbar(new RoleCsvMapper(roleRepository));
    }

    @Override
    protected Role createStub() {
        return viewModel.createStub();
    }

    @Override
    protected EditorBinding<Role> buildEditor(Role role) {
        EditorForm<Role> form = editorForm(role);

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

        form.field(Localization.lang("Name"), nameField, nameField.textProperty(),
                Role::name, nameField::setText);
        // One label covers both spinners, so the accent is a section: dirty if
        // either differs from the baseline, not just the one whose own
        // listener last fired (two independent field() rows sharing a label
        // would let an unchanged spinner's recompute clobber the accent set by
        // the other one still differing).
        form.section(Localization.lang("Age range"),
                new InputGroup(minAgeSpinner, new Label("–"), maxAgeSpinner),
                label -> {
                    Role saved = form.baseline().get();
                    setFieldChanged(label, !Objects.equals(minAgeSpinner.getValue(), saved.minAge())
                            || !Objects.equals(maxAgeSpinner.getValue(), saved.maxAge()));
                },
                updated -> {
                    minAgeSpinner.getValueFactory().setValue(updated.minAge());
                    maxAgeSpinner.getValueFactory().setValue(updated.maxAge());
                });

        Runnable pushLive = () -> {
            if (isSuppressingLiveUpdates()) {
                return;
            }
            updateLive(new Role(role.id(), nameField.getText().strip(),
                    minAgeSpinner.getValue(), maxAgeSpinner.getValue(), role.sortOrder()));
        };
        form.onEdit(pushLive);

        // The age-range section wires its own listeners (see EditorForm#section):
        // both spinners push live and both recompute the shared accent.
        //
        // Raising the min age above the max age drags the max age up with it.
        // addListener, not subscribe(): subscribe() fires immediately at
        // registration - and buildEditor can run nested inside another
        // mutation of the shared store list (a TableView reselecting a row
        // mid-delete), so that immediate call would push live while the outer
        // list change is still unwinding, the same reentrancy
        // withoutLiveUpdates guards against but from construction.
        minAgeSpinner.valueProperty().addListener((obs, oldMin, newMin) -> {
            Integer max = maxAgeSpinner.getValue();
            if (newMin != null && max != null && max < newMin) {
                withoutLiveUpdates(() -> maxAgeSpinner.getValueFactory().setValue(newMin));
            }
            pushLive.run();
        });
        maxAgeSpinner.valueProperty().addListener((obs, oldValue, newValue) -> pushLive.run());

        VBox content = new VBox(12, form.grid());
        content.setPadding(new Insets(12));
        return EditorBinding.of(content, form.refresh());
    }

    /// Formats a role's age range for the table: `"min-max"`, or one-sided
    /// (`"min-"` / `"-max"`) when only one bound is set, or empty when
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

    /// Editable integer spinner factory where a blank editor means `null`
    /// ("no age bound"). The converter maps blank &harr; null so committing an
    /// empty editor keeps it blank; the arrows step up to [#MAX_AGE] and
    /// collapse to blank below the (dynamic) `floor`. A dynamic floor
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
                    // Through normalize(), not just parseAge(): the spinner is
                    // editable, so a typed value has to clear the same floor
                    // the arrows enforce. Without this, typing a max age below
                    // the min age builds an inverted range, which Role rejects
                    // - from inside a control listener, where the exception
                    // has nowhere useful to go.
                    Integer parsed = parseAge(text);
                    return parsed == null ? null : normalize(parsed);
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
