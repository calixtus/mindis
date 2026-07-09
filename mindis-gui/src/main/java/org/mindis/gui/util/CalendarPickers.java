package org.mindis.gui.util;

import com.dlsc.gemsfx.CalendarPicker;
import com.dlsc.gemsfx.CalendarView;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Base64;

import javafx.util.StringConverter;

import org.jspecify.annotations.Nullable;

/// ISO ({@code yyyy-MM-dd}) formatting for GemsFX {@link CalendarPicker}s,
/// shared by every date field in the app (see ADR: date pickers use GemsFX's
/// calendar popup instead of the stock JavaFX {@code DatePicker}).
public final class CalendarPickers {

    /// The date format every {@code CalendarPicker} in the app displays and parses.
    public static final DateTimeFormatter ISO = DateTimeFormatter.ISO_LOCAL_DATE;

    /// GemsFX's bundled CSS (calendar-picker/calendar-view/year-view/
    /// year-month-view) is written against stock Modena: it looks up
    /// {@code -fx-base}, {@code -fx-mark-color}, {@code -fx-text-background-color}
    /// and similar Modena-only tokens that AtlantaFX (a from-scratch
    /// {@code -color-*} stylesheet, not a Modena derivative) never defines, and
    /// hardcodes a few literals ({@code rgb(230, 231, 233)}, {@code #eeeeee})
    /// that ignore the theme entirely. Both cause visible bugs: unresolved
    /// lookups make gemsfx's rule fail to convert and fall back to its own
    /// defaults (ClassCastException/"could not resolve" in the javafx.css log),
    /// and the literals render as a bright patch even in dark mode.
    ///
    /// <p>This stylesheet is attached directly to the picker/calendar-view nodes
    /// below (author origin) rather than folded into the app's user-agent
    /// stylesheet - author origin always outranks gemsfx's own default
    /// stylesheet (user-agent origin) regardless of selector specificity, so
    /// there's no cascade tie to fight and no need to mirror gemsfx's full
    /// ancestor chain in the selectors below - a descendant selector on the
    /// distinctive leaf class is enough. Variable definitions fix the
    /// unresolved lookups at the source (gemsfx's own rule ends up painting
    /// with a real color); the final block directly overrides the hardcoded
    /// literals gemsfx never routes through a lookup, and adds a hover state
    /// for ordinary (current-month) date cells - gemsfx defines none at all.
    private static final String CALENDAR_THEME_CSS = """
            .calendar-picker {
              -fx-outer-border: -color-border-default;
              -fx-inner-border: -color-border-default;
              -fx-body-color: -color-bg-default;
              -fx-shadow-highlight-color: transparent;
              -fx-mark-color: -color-fg-default;
              -fx-mark-highlight-color: transparent;
              -fx-focus-color: -color-accent-emphasis;
              -fx-faint-focus-color: -color-accent-subtle;
            }
            .calendar-view {
              -fx-box-border: -color-border-default;
              -fx-control-inner-background: -color-bg-default;
              -fx-control-inner-background-alt: -color-bg-subtle;
              -fx-mark-color: -color-fg-default;
              -fx-mark-highlight-color: transparent;
              -fx-base: -color-fg-default;
              -fx-text-background-color: -color-fg-default;
              -fx-accent: -color-accent-emphasis;
            }
            .year-view {
              -fx-control-inner-background-alt: -color-bg-subtle;
              -fx-text-background-color: -color-fg-default;
              -fx-base: -color-fg-default;
              -fx-accent: -color-accent-emphasis;
            }
            .year-month-view {
              -fx-control-inner-background: -color-bg-default;
              -fx-mark-highlight-color: transparent;
              -fx-base: -color-fg-default;
              -fx-shadow-highlight-color: transparent;
              -fx-text-background-color: -color-fg-default;
            }

            .calendar-view .date-cell.previous-month,
            .calendar-view .date-cell.next-month,
            .calendar-view .date-label.dropdown:hover,
            .calendar-view .arrow-button:hover,
            .calendar-view .decrement-year-button:hover,
            .calendar-view .increment-year-button:hover,
            .year-view .arrow-button:hover,
            .year-month-view .arrow-button:hover {
              -fx-background-color: -color-bg-subtle;
            }

            /* Plain date cells already default to -color-bg-subtle (mapped from
               -fx-control-inner-background-alt above), so a hover rule using the
               same color would be invisible - use the accent tint instead. The
               selected cell (already -color-accent-emphasis) gets its own
               darker hover shade below rather than this tint, which would look
               like a de-selection. JavaFX CSS has no :not(), so this can't be
               scoped to "not selected" directly - instead .date-cell.selected:hover
               below has one more class than this selector, so it naturally
               wins by specificity regardless of declaration order. */
            .calendar-view .date-cell:hover {
              -fx-background-color: -color-accent-subtle;
            }
            .calendar-view .date-cell.selected:hover {
              -fx-background-color: derive(-color-accent-emphasis, -20%);
            }

            /* Same issue TimePickers fixes for its own edit-button: gemsfx's
               arrow-button paints its own 3-layer background (outer-border/
               inner-border/body-color, inset from the button's own bounds)
               to fake a miniature button border, independent of and inset
               from the picker's real outer border - visible as a doubled/
               inset line around the calendar icon. Flattened to a single
               flat background with a plain 1px left divider. Also, unlike
               TimePicker's edit-button, gemsfx never gives this button its
               own -fx-cursor: arrow at all - it fell through to the
               surrounding text field's I-beam cursor; set explicitly here. */
            .calendar-picker > .box > .arrow-button,
            .calendar-picker:focused > .box > .arrow-button {
              -fx-background-color: -fx-body-color;
              -fx-background-insets: 0;
              -fx-background-radius: 0;
              -fx-border-color: -fx-outer-border;
              -fx-border-width: 0 0 0 1;
              -fx-border-insets: 0;
              -fx-cursor: arrow;
            }
            .calendar-picker > .box > .arrow-button:hover {
              -fx-background-color: -color-bg-subtle;
            }
            """;

    private static final String CALENDAR_THEME_STYLESHEET = "data:text/css;base64,"
            + Base64.getEncoder().encodeToString(CALENDAR_THEME_CSS.getBytes(StandardCharsets.UTF_8));

    private CalendarPickers() {
    }

    /// A new {@link CalendarPicker} already set to {@link #ISO} format.
    public static CalendarPicker create() {
        CalendarPicker picker = new CalendarPicker();
        applyIsoFormat(picker);
        return picker;
    }

    /// Applies {@link #ISO} format to an existing (e.g. FXML-instantiated) picker,
    /// hides its "Today" shortcut button - the app shows plain dates only, no
    /// shortcut text - and attaches {@link #CALENDAR_THEME_CSS} so the popup
    /// calendar actually follows the app's AtlantaFX theme.
    public static void applyIsoFormat(CalendarPicker picker) {
        CalendarView calendarView = picker.getCalendarView();
        calendarView.setShowTodayButton(false);
        picker.getStylesheets().add(CALENDAR_THEME_STYLESHEET);
        calendarView.getStylesheets().add(CALENDAR_THEME_STYLESHEET);
        picker.setConverter(new StringConverter<>() {
            @Override
            public String toString(@Nullable LocalDate date) {
                return date == null ? "" : ISO.format(date);
            }

            @Override
            public @Nullable LocalDate fromString(@Nullable String text) {
                String trimmed = text == null ? "" : text.strip();
                if (trimmed.isEmpty()) {
                    return null;
                }
                try {
                    return LocalDate.parse(trimmed, ISO);
                } catch (DateTimeParseException e) {
                    return null;
                }
            }
        });
    }
}
