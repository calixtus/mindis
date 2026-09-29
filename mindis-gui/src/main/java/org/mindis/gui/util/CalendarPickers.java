package org.mindis.gui.util;

import com.dlsc.gemsfx.CalendarPicker;
import com.dlsc.gemsfx.CalendarView;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.chrono.IsoChronology;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.time.format.DateTimeParseException;
import java.time.format.FormatStyle;
import java.time.temporal.ChronoField;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javafx.util.StringConverter;

import org.jspecify.annotations.Nullable;

/// ISO (`yyyy-MM-dd`) formatting for GemsFX [CalendarPicker]s,
/// shared by every date field in the app (see ADR: date pickers use GemsFX's
/// calendar popup instead of the stock JavaFX `DatePicker`).
///
/// <p>ISO is what a picker *displays* - one unambiguous format everywhere,
/// whatever the UI language. What it *accepts* is deliberately wider: the rest
/// of the app renders dates in the user's own locale ([DateTimes]), so a
/// German user reading "30.07.2026" on every other screen will type
/// "30.07.2026" into a date field too. That input used to be dropped on the
/// floor - the converter returned `null`, the picker kept no value, and the
/// typed text just sat there looking accepted - so [#parse] tries the locale's
/// own date formats after ISO.
public final class CalendarPickers {

    /// The date format every `CalendarPicker` in the app displays.
    public static final DateTimeFormatter ISO = DateTimeFormatter.ISO_LOCAL_DATE;

    /// GemsFX's bundled CSS (calendar-picker/calendar-view/year-view/
    /// year-month-view) is written against stock Modena: it looks up
    /// `-fx-base`, `-fx-mark-color`, `-fx-text-background-color`
    /// and similar Modena-only tokens that AtlantaFX (a from-scratch
    /// `-color-*` stylesheet, not a Modena derivative) never defines, and
    /// hardcodes a few literals (`rgb(230, 231, 233)`, `#eeeeee`)
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

    /// A new [CalendarPicker] already set to [#ISO] format.
    public static CalendarPicker create() {
        CalendarPicker picker = new CalendarPicker();
        applyIsoFormat(picker);
        return picker;
    }

    /// Applies [#ISO] format to an existing (e.g. FXML-instantiated) picker,
    /// hides its "Today" shortcut button - the app shows plain dates only, no
    /// shortcut text - and attaches [#CALENDAR_THEME_CSS] so the popup
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
                return parse(text);
            }
        });
    }

    /// The date `text` denotes, or `null` if it is blank or none of the
    /// accepted formats match: ISO first, then the current locale's own date
    /// formats (see the class docs).
    public static @Nullable LocalDate parse(@Nullable String text) {
        String trimmed = text == null ? "" : text.strip();
        if (trimmed.isEmpty()) {
            return null;
        }
        for (DateTimeFormatter format : inputFormats()) {
            try {
                return LocalDate.parse(trimmed, format);
            } catch (DateTimeParseException tryTheNextOne) {
                // Expected: the formats are alternatives, not a chain.
            }
        }
        return null;
    }

    /// Every format a typed date may be in, strictest first. Derived from the
    /// current locale on every call rather than cached, for the reason
    /// [DateTimes] spells out: `Localization.setLocale` moves that locale at
    /// runtime.
    ///
    /// <p>Two localized styles, each contributing its own pattern plus a
    /// relaxed variant that also takes a one-digit day or month and either a
    /// two- or a four-digit year ("5.9.26" and "5.9.2026" both parse). The
    /// field *order* always stays the locale's, so "3/4/2026" reads as March
    /// 4th for an English UI and as April 3rd for a German one rather than
    /// being guessed at. SHORT before MEDIUM because the German MEDIUM pattern
    /// (`dd.MM.y`) would otherwise read "30.07.26" as the year 26.
    private static List<DateTimeFormatter> inputFormats() {
        Locale locale = Locale.getDefault(Locale.Category.FORMAT);
        List<FormatStyle> styles = List.of(FormatStyle.SHORT, FormatStyle.MEDIUM);
        List<DateTimeFormatter> formats = new ArrayList<>();
        formats.add(ISO);
        for (FormatStyle style : styles) {
            formats.add(DateTimeFormatter.ofLocalizedDate(style).withLocale(locale));
        }
        for (FormatStyle style : styles) {
            relaxed(localizedPattern(style, locale), locale).ifPresent(formats::add);
        }
        return formats;
    }

    private static String localizedPattern(FormatStyle style, Locale locale) {
        return DateTimeFormatterBuilder.getLocalizedDateTimePattern(
                style, null, IsoChronology.INSTANCE, locale);
    }

    /// `pattern` with its day and month fields widened to accept one digit as
    /// well as two, and its year field replaced by a 2-to-4-digit one based at
    /// 2000 (so "26" is 2026, and "2026" is itself). Empty for a pattern with
    /// no year to widen, which no locale's short or medium date has.
    private static Optional<DateTimeFormatter> relaxed(String pattern, Locale locale) {
        // Single letters only: "MMM" is the month's *name*, which is already
        // as wide as it gets, and narrowing it would break the pattern.
        String widened = pattern.replaceAll("(?<!d)dd(?!d)", "d").replaceAll("(?<!M)MM(?!M)", "M");
        Matcher year = Pattern.compile("y+|u+").matcher(widened);
        if (!year.find()) {
            return Optional.empty();
        }
        DateTimeFormatterBuilder builder = new DateTimeFormatterBuilder();
        appendLiteralPattern(builder, widened.substring(0, year.start()));
        builder.appendValueReduced(ChronoField.YEAR, 2, 4, 2000);
        appendLiteralPattern(builder, widened.substring(year.end()));
        return Optional.of(builder.toFormatter(locale));
    }

    private static void appendLiteralPattern(DateTimeFormatterBuilder builder, String pattern) {
        if (!pattern.isEmpty()) {
            builder.appendPattern(pattern);
        }
    }
}
