package org.mindis.gui.shell;

import java.util.Objects;
import java.util.function.Supplier;

import javafx.beans.value.ObservableValue;
import javafx.scene.layout.Region;

import org.jspecify.annotations.Nullable;

/// The "unsaved change" accent an editor puts on a field's label - the left
/// border `.field-changed` in `shell.css` - and the rules for when it shows.
/// Stateless, so any editor can use it, inside a [CrudModule] or not.
public final class FieldAccents {

    private FieldAccents() {
    }

    /// Left border accent on `label` while `property`'s current
    /// value differs from `original.get()` (the last-flushed value) - a
    /// lightweight "you have unsaved changes here" cue that needs no
    /// field-by-field "was this the one that changed" bookkeeping: each field
    /// just watches its own drift from where it started, and clears itself
    /// the moment the value round-trips back to the original (e.g. undoing a
    /// typo). On the field's own label, not the field itself - keeps the
    /// accent out of the way of a field's own focus/validation styling.
    ///
    /// <p>`original` is a supplier, not a fixed value: a Save all moves
    /// "the last-flushed value" without necessarily changing what the control
    /// displays (the row was already showing its own just-saved content), so
    /// no property change fires to re-evaluate the accent on its own - a fixed
    /// snapshot captured once at editor-build time would leave the accent
    /// stuck "dirty" forever after the first save. Re-reading the supplier on
    /// every future control edit keeps the listener correct going forward;
    /// [CrudModule.EditorBinding]'s `refresh` callback additionally
    /// re-invokes this method's initial check after a Save/Open, since
    /// that path changes no control value and so triggers no listener at all.
    ///
    /// <p>`original` is typically `() -> savedSnapshot(item)`-
    /// derived (falling back to `item` for a not-yet-saved new row) -
    /// see any `buildEditor(Object)` override for the pattern.
    public static <T extends @Nullable Object> void markDirtyOnChange(ObservableValue<? extends T> property, Supplier<T> original, Region label) {
        property.addListener((obs, oldValue, newValue) -> recomputeFieldChanged(property, original, label));
        recomputeFieldChanged(property, original, label);
    }

    /// The comparison [#markDirtyOnChange] reruns on every control
    /// change - factored out so an [CrudModule.EditorBinding]'s `refresh`
    /// callback (a Save/Open, which moves `original` without
    /// necessarily changing what the control displays, so no listener fires
    /// on its own) can re-invoke just the comparison without registering a
    /// second listener.
    public static <T extends @Nullable Object> void recomputeFieldChanged(ObservableValue<? extends T> property, Supplier<T> original, Region label) {
        setFieldChanged(label, !Objects.equals(property.getValue(), original.get()));
    }

    /// Toggles the left-border "unsaved change" accent (see `.field-changed` in `shell.css`).
    public static void setFieldChanged(Region label, boolean changed) {
        if (changed) {
            if (!label.getStyleClass().contains("field-changed")) {
                label.getStyleClass().add("field-changed");
            }
        } else {
            label.getStyleClass().remove("field-changed");
        }
    }
}
