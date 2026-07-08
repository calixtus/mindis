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
     * <p>The clock-face popup ({@code TimePickerPopup}, a plain {@code HBox}
     * shown by {@code CustomComboBox}'s standard popup mechanism - not a
     * separate {@code CustomPopupControl} scene the way SearchField's popup
     * is) crashed the same way: its {@code -fx-background-color: -fx-box-border, white}
     * left {@code -fx-box-border} unresolved. Fixed at the source in
     * {@link org.mindis.gui.theme.ThemeStyler} - the same token, same value,
     * {@code CalendarPickers} already defines for {@code .calendar-view}, just
     * global here since {@code TimePicker} exposes no popup-content accessor
     * to attach an author-origin stylesheet to directly the way
     * {@code CalendarPickers} does via {@code getCalendarView()}. The popup's
     * other rules use hardcoded literals (white/gray/black), not lookups, so
     * they don't crash - just don't follow the theme, and can't be fixed the
     * same way: see {@link org.mindis.gui.theme.ThemeStyler}'s javadoc for why
     * a direct-rule override attempt didn't work either.
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
            """;

    private static final String TIME_PICKER_THEME_STYLESHEET = "data:text/css;base64,"
            + Base64.getEncoder().encodeToString(TIME_PICKER_THEME_CSS.getBytes(StandardCharsets.UTF_8));

    private TimePickers() {
    }

    /**
     * A new {@link TimePicker} restricted to hours and minutes, themed. The
     * clock icon trigger button is hidden - typing the hour:minute fields
     * directly is the primary path everywhere this is used, and dropping it
     * keeps the control compact for a future touch/mobile client too - but
     * the popup itself stays reachable via Enter/F4 (unchanged gemsfx
     * behavior) since it no longer crashes, even though its idle/hover rows
     * still show gemsfx's own unthemed colors (see the class javadoc).
     */
    public static TimePicker create() {
        TimePicker picker = new TimePicker();
        picker.setFormat(TimePicker.Format.HOURS_MINUTES);
        picker.setShowPopupTriggerButton(false);
        picker.getStylesheets().add(TIME_PICKER_THEME_STYLESHEET);
        picker.setTime(LocalTime.of(10, 0));
        return picker;
    }
}
