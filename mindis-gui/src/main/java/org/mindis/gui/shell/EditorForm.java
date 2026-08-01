package org.mindis.gui.shell;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

import javafx.beans.value.ObservableValue;
import javafx.geometry.Insets;
import javafx.geometry.VPos;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Priority;

import org.jspecify.annotations.Nullable;

/// The label/control grid a [CrudModule] editor is built from, and the wiring
/// each row needs.
///
/// Every editable field used to be mentioned five times, in five different
/// places in the same method: creating the control, attaching a listener that
/// writes the edit through, adding label and control to the grid, registering
/// the unsaved-change accent, and - in the refresh callback - pushing an
/// external change back into the control plus recomputing that accent. Five
/// chances for a field to be added to four of them, and the editors grew to
/// several hundred lines mostly of that bookkeeping.
///
/// A row is declared once here, with its label text, control, property, where
/// its value comes from and how to write it back:
///
/// ```
/// form.field("First name", firstNameField, firstNameField.textProperty(),
///            Server::firstName, firstNameField::setText);
/// ```
///
/// [#onEdit] is called last, once every row is declared, and attaches the
/// write-through listener to all of them. That ordering is deliberate: the
/// callback rebuilds the whole entity from the controls, so it cannot exist
/// until they all do - which is why editors used to smuggle it forward in a
/// one-element array.
///
/// A field the accent cannot be derived for - one label covering a whole
/// collection, or two controls - declares a [#section] and supplies its own
/// comparison instead.
///
/// @param <T> the edited entity type
public final class EditorForm<T> {

    /// Widest label the grid reserves room for before the field column starts.
    private static final double LABEL_COLUMN_WIDTH = 110;
    /// A list's first row sits a little below its own top edge (border plus
    /// cell padding), so a plainly top-aligned label reads as too high.
    private static final Insets TOP_LABEL_NUDGE = new Insets(4, 0, 0, 0);

    private final GridPane grid = new GridPane();
    private final Supplier<T> baseline;
    private final Consumer<Runnable> withoutLiveUpdates;

    private final List<ObservableValue<?>> editableProperties = new ArrayList<>();
    private final List<Consumer<T>> applyToControls = new ArrayList<>();
    private final List<Runnable> recomputeAccents = new ArrayList<>();

    private int row;
    private @Nullable Runnable pushLive;

    /// @param baseline           the last-flushed value to compare against
    ///                           (see [CrudModule#baseline])
    /// @param withoutLiveUpdates runs a block with write-through suppressed
    ///                           (see [CrudModule#withoutLiveUpdates])
    EditorForm(Supplier<T> baseline, Consumer<Runnable> withoutLiveUpdates) {
        this.baseline = baseline;
        this.withoutLiveUpdates = withoutLiveUpdates;
        grid.setHgap(8);
        grid.setVgap(8);
        ColumnConstraints labelColumn = new ColumnConstraints();
        labelColumn.setMinWidth(LABEL_COLUMN_WIDTH);
        ColumnConstraints fieldColumn = new ColumnConstraints();
        fieldColumn.setHgrow(Priority.ALWAYS);
        grid.getColumnConstraints().addAll(labelColumn, fieldColumn);
    }

    /// One labelled control bound to one property of the entity.
    ///
    /// @param labelText localized label
    /// @param control   the control to place beside it
    /// @param property  the control's value, watched for edits and compared
    ///                  against the baseline
    /// @param value     that same value read off an entity - the accent
    ///                  compares `property` with `value.apply(baseline)`
    /// @param setter    writes a value into the control, for a refresh
    public <V> Row field(String labelText, Node control, ObservableValue<V> property,
                         Function<T, V> value, Consumer<V> setter) {
        Label label = new Label(labelText);
        CrudModule.markDirtyOnChange(property, () -> value.apply(baseline.get()), label);
        editableProperties.add(property);
        applyToControls.add(updated -> setter.accept(value.apply(updated)));
        recomputeAccents.add(() ->
                CrudModule.recomputeFieldChanged(property, () -> value.apply(baseline.get()), label));
        return addRow(label, control);
    }

    /// A row whose unsaved-change accent cannot be derived from one property -
    /// one label covering a whole collection, or spanning two controls. The
    /// caller supplies the comparison and the refresh.
    ///
    /// The control's own edits are not watched here; a section wires its own
    /// listeners, since what counts as an edit is exactly what it cannot
    /// express as a single property.
    ///
    /// @param recomputeChanged sets or clears the accent from current state
    /// @param apply            pushes an externally changed entity into the
    ///                         section's controls
    public Row section(String labelText, Node control, Consumer<Label> recomputeChanged, Consumer<T> apply) {
        return section(new Label(labelText), control, recomputeChanged, apply);
    }

    /// As [#section(String, Node, Consumer, Consumer)], for a control that
    /// brings its own label (see `SlotCountEditor`).
    public Row section(Label label, Node control, Consumer<Label> recomputeChanged, Consumer<T> apply) {
        applyToControls.add(apply);
        recomputeAccents.add(() -> recomputeChanged.accept(label));
        recomputeChanged.accept(label);
        return addRow(label, control);
    }

    /// Attaches `pushLive` to every [#field] declared so far. Call once, after
    /// the last row: `pushLive` reads all the controls, so it cannot be built
    /// before them.
    public void onEdit(Runnable pushLive) {
        this.pushLive = pushLive;
        for (ObservableValue<?> property : editableProperties) {
            property.addListener((obs, oldValue, newValue) -> edited());
        }
    }

    /// Reports an edit a [#section] made itself: writes it through and
    /// refreshes every accent.
    ///
    /// Exists so a section's controls can report edits without holding the
    /// write-through callback directly. That callback reads every control, so
    /// it cannot be built until they all exist - but a control built earlier
    /// may need to call it, and routing through the form breaks the cycle
    /// editors used to bridge with a one-element array.
    public void edited() {
        if (pushLive != null) {
            pushLive.run();
        }
        recomputeAccents.forEach(Runnable::run);
    }

    /// The grid holding every declared row.
    public GridPane grid() {
        return grid;
    }

    /// The last-flushed value to compare against, for a [#section] writing its
    /// own comparison. Re-read on every call rather than captured: a save
    /// moves the baseline without changing any displayed value.
    public Supplier<T> baseline() {
        return baseline;
    }

    /// The [CrudModule.EditorBinding] refresh callback: pushes `updated` into
    /// every control with write-through suppressed, then recomputes every
    /// accent.
    ///
    /// The recompute is not left to the controls' own listeners: a save moves
    /// the baseline without changing any displayed value, so nothing would
    /// fire.
    public Consumer<T> refresh() {
        return updated -> {
            withoutLiveUpdates.accept(() -> applyToControls.forEach(apply -> apply.accept(updated)));
            recomputeAccents.forEach(Runnable::run);
        };
    }

    private Row addRow(Label label, Node control) {
        grid.add(label, 0, row);
        grid.add(control, 1, row);
        row++;
        return new Row(label, control);
    }

    /// A declared row, for the few layout tweaks that differ per field.
    public record Row(Label label, Node control) {

        /// Aligns the label with the top of a tall control instead of centring
        /// it on the row.
        public Row topAligned() {
            GridPane.setValignment(label, VPos.TOP);
            return this;
        }

        /// As [#topAligned()], plus a nudge down: a list's first row sits a
        /// little below its own top edge (border plus cell padding), so a
        /// plainly top-aligned label reads as too high beside one.
        public Row listAligned() {
            topAligned();
            label.setPadding(TOP_LABEL_NUDGE);
            return this;
        }

        /// Lets the control take the editor's spare vertical space.
        public Row growing() {
            GridPane.setVgrow(control, Priority.ALWAYS);
            return this;
        }
    }
}
