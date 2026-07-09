package org.mindis.gui.util;

import com.dlsc.gemsfx.TimePicker;

import java.nio.charset.StandardCharsets;
import java.time.LocalTime;
import java.util.Base64;

/**
 * {@code HOURS_MINUTES}-only {@link TimePicker}s, shared by every time field
 * in the app.
 */
public final class TimePickers {

    /**
     * Same problem as {@link CalendarPickers}, different token set: GemsFX's
     * bundled {@code time-picker.css} is written against stock Modena
     * ({@code -fx-outer-border}, {@code -fx-body-color}, {@code -fx-focus-color},
     * {@code -fx-faint-focus-color}, {@code -fx-mark-color}, ...), tokens
     * AtlantaFX never defines - unresolved, JavaFX logs a ClassCastException/
     * "could not resolve" the moment the field is focused. Author origin
     * (attached directly below), same reasoning as {@code CalendarPickers}:
     * always outranks gemsfx's own stylesheet regardless of specificity, no
     * cascade tie to fight.
     *
     * <p>The clock-face popup ({@code TimePickerPopup}) needs the same
     * treatment for its own rules, including the ones gemsfx writes as
     * hardcoded literals (white/gray/lightgray/black, not token lookups) for
     * the idle/hover/selected list cells - a direct rule override of those
     * previously failed when attached to the app's global scene-level UA
     * stylesheet ({@link org.mindis.gui.theme.ThemeStyler}), since the popup
     * is a separate {@code PopupControl} window with its own node-scoped UA
     * stylesheet, and two same-origin (UA) rules of equal specificity resolve
     * by declaration order, not simply "app always wins". Attached here
     * instead, directly on the {@code TimePicker} itself (author origin, like
     * {@code CalendarPickers} does via {@code getCalendarView()}) - the
     * popup's {@code PopupControl} declares the {@code TimePicker} as its
     * {@code getStyleableParent()}, and author-origin rules do cross that
     * boundary, unlike the UA-origin ones tried before.
     */
    private static final String TIME_PICKER_THEME_CSS = """
            .time-picker {
              -fx-outer-border: -color-border-default;
              -fx-inner-border: -color-border-default;
              -fx-body-color: -color-bg-default;
              -fx-focus-color: -color-accent-emphasis;
              -fx-faint-focus-color: -color-accent-subtle;
              -fx-mark-color: -color-fg-default;
              -fx-control-inner-background: -color-bg-default;
            }
            .time-picker-popup {
              -fx-background-color: -color-border-default, -color-bg-default;
            }
            .time-picker-popup .time-list-view .list-cell,
            .time-picker-popup .time-list-view .list-cell:focus-within,
            .time-picker-popup .time-list-view .list-cell:selected,
            .time-picker-popup .time-list-view .list-cell:focused {
              -fx-background-color: -color-bg-default;
            }
            .time-picker-popup .time-list-view .list-cell .time-label {
              -fx-background-color: -color-bg-subtle;
              -fx-text-fill: -color-fg-default;
            }
            .time-picker-popup .time-list-view .list-cell:hover .time-label {
              -fx-background-color: -color-accent-subtle;
              -fx-text-fill: -color-fg-default;
            }
            .time-picker-popup .time-list-view .list-cell:selected .time-label {
              -fx-background-color: -color-accent-emphasis;
              -fx-text-fill: white;
            }
            """;

    private static final String TIME_PICKER_THEME_STYLESHEET = "data:text/css;base64,"
            + Base64.getEncoder().encodeToString(TIME_PICKER_THEME_CSS.getBytes(StandardCharsets.UTF_8));

    private TimePickers() {
    }

    /**
     * A new {@link TimePicker} restricted to hours and minutes, themed
     * (including its clock popup - see the class javadoc), with the clock
     * icon trigger button shown - a time can be picked without touching the
     * keyboard; typing the hour:minute fields directly still works too.
     */
    public static TimePicker create() {
        return create(true);
    }

    /**
     * As {@link #create()}, but lets the trigger button be left off - for a
     * picker that's pill-joined with an adjacent button (e.g. Servers'
     * "Preferred times" input group), where the button adds width the seam
     * wasn't sized for and its own border reads as a stray inset gap against
     * the neighboring pill button. Enter/F4 still opens the popup either way
     * (unchanged gemsfx behavior), so this only removes the pointer-driven
     * path, not the picker's own keyboard one.
     */
    public static TimePicker create(boolean showTriggerButton) {
        TimePicker picker = new TimePicker();
        picker.setFormat(TimePicker.Format.HOURS_MINUTES);
        picker.setShowPopupTriggerButton(showTriggerButton);
        picker.getStylesheets().add(TIME_PICKER_THEME_STYLESHEET);
        picker.setTime(LocalTime.of(10, 0));
        return picker;
    }
}
