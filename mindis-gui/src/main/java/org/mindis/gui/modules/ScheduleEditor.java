package org.mindis.gui.modules;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.Month;
import java.time.format.TextStyle;
import java.time.temporal.WeekFields;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Function;
import java.util.stream.Collectors;

import javafx.beans.property.ObjectProperty;
import javafx.beans.property.ReadOnlyObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.value.ObservableValue;
import javafx.collections.FXCollections;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.RadioButton;
import javafx.scene.control.Spinner;
import javafx.scene.control.TextField;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.util.StringConverter;

import com.dlsc.gemsfx.CalendarPicker;

import org.jspecify.annotations.Nullable;

import org.mindis.core.l10n.EnumDisplay;
import org.mindis.core.l10n.Localization;
import org.mindis.core.l10n.RecurrenceText;
import org.mindis.core.model.LiturgicalDay;
import org.mindis.core.model.RecurrenceRule;
import org.mindis.core.model.ServiceSchedule;
import org.mindis.core.persistence.RecurrenceCodec;
import org.mindis.gui.util.CalendarPickers;

/// Editor for a [ServiceSchedule]: the pattern (a mode picker plus that
/// mode's fields), the window it applies in, the dates dropped from it, a
/// plain-language summary and a preview of the next dates it produces.
///
/// <p>The four guided modes cover what a parish schedules ("every other
/// Sunday", "the third Sunday of the month", "the first Sunday of October",
/// "Easter minus two days"); [Mode#CUSTOM] takes the rule's text form
/// directly and is what any rule the guided modes cannot express falls back
/// to, so nothing a document or a CSV import can contain is uneditable here.
///
/// <p>Reading a rule back into the controls only recognizes the shapes the
/// guided modes themselves build. That is deliberate: rather than trying to
/// normalize arbitrary rule trees into fields, anything else opens in CUSTOM
/// with its exact text - which round-trips losslessly - instead of being
/// silently flattened into something the fields can hold.
final class ScheduleEditor {

    private static final int PREVIEW_COUNT = 5;
    private static final int MAX_INTERVAL = 12;
    private static final int MAX_FEAST_OFFSET_DAYS = 90;
    private static final List<Integer> ORDINALS = List.of(1, 2, 3, 4, 5, -1);

    private final ObjectProperty<ServiceSchedule> schedule =
            new SimpleObjectProperty<>(ServiceSchedule.of(RecurrenceRule.NEVER));
    private final VBox root = new VBox(8);
    private final VBox modeBody = new VBox(8);
    private final Label summary = new Label();
    private final Label preview = new Label();

    private final ComboBox<Mode> modeBox = new ComboBox<>(FXCollections.observableArrayList(Mode.values()));

    private final Map<DayOfWeek, ToggleButton> weekdayToggles = new LinkedHashMap<>();
    private final Spinner<Integer> weeklyInterval = new Spinner<>(1, MAX_INTERVAL, 1);
    private final CalendarPicker weeklyAnchor = CalendarPickers.create();

    private final ToggleGroup monthlyKind = new ToggleGroup();
    private final RadioButton monthlyByDay = new RadioButton(Localization.lang("On day"));
    private final RadioButton monthlyByWeekday = new RadioButton(Localization.lang("On the"));
    private final RadioButton monthlyByLastDay = new RadioButton(Localization.lang("On the last day of the month"));
    private final Spinner<Integer> monthlyDay = new Spinner<>(1, 31, 1);
    private final ComboBox<Integer> monthlyOrdinal = ordinalBox();
    private final ComboBox<DayOfWeek> monthlyWeekday = weekdayBox();
    private final Spinner<Integer> monthlyInterval = new Spinner<>(1, MAX_INTERVAL, 1);
    private final CalendarPicker monthlyAnchor = CalendarPickers.create();

    private final ComboBox<Month> yearlyMonth = new ComboBox<>(FXCollections.observableArrayList(Month.values()));
    private final ToggleGroup yearlyKind = new ToggleGroup();
    private final RadioButton yearlyByDay = new RadioButton(Localization.lang("On day"));
    private final RadioButton yearlyByWeekday = new RadioButton(Localization.lang("On the"));
    private final Spinner<Integer> yearlyDay = new Spinner<>(1, 31, 1);
    private final ComboBox<Integer> yearlyOrdinal = ordinalBox();
    private final ComboBox<DayOfWeek> yearlyWeekday = weekdayBox();

    private final ComboBox<LiturgicalDay> feastBox =
            new ComboBox<>(FXCollections.observableArrayList(LiturgicalDay.values()));
    private final Spinner<Integer> feastOffset =
            new Spinner<>(-MAX_FEAST_OFFSET_DAYS, MAX_FEAST_OFFSET_DAYS, 0);

    private final TextField customText = new TextField();
    private final CheckBox customValid = new CheckBox();

    private final CalendarPicker validFrom = CalendarPickers.create();
    private final CalendarPicker validUntil = CalendarPickers.create();
    private final CalendarPicker skipDatePicker = CalendarPickers.create();
    private final Button addSkipDate = new Button(Localization.lang("Skip this date"));
    private final FlowPane skipDateChips = new FlowPane(6, 6);
    private final Set<LocalDate> skipDates = new TreeSet<>();

    /// Guards every control listener while [#setSchedule] pushes an
    /// externally changed schedule into the fields, so that seeding the
    /// controls cannot rebuild (and thereby round off) the very schedule
    /// being seeded.
    private boolean seeding;

    ScheduleEditor(ServiceSchedule initial) {
        modeBox.setConverter(converter(ScheduleEditor::modeLabel));
        summary.setWrapText(true);
        preview.setWrapText(true);
        preview.getStyleClass().add("text-muted");
        // A read-only marker rather than a control: the custom field's text is
        // either a rule or it is not, and the user needs to see which.
        customValid.setDisable(true);
        customValid.setText(Localization.lang("Valid rule"));

        buildWeeklyControls();
        buildMonthlyControls();
        buildYearlyControls();
        addSkipDate.setOnAction(event -> addSkipDate());
        root.getChildren().addAll(modeBox, modeBody,
                row(new Label(Localization.lang("Valid from")), validFrom,
                        new Label(Localization.lang("until")), validUntil),
                row(skipDatePicker, addSkipDate),
                skipDateChips,
                summary, preview);

        modeBox.getSelectionModel().selectedItemProperty().addListener((obs, old, mode) -> {
            showMode(mode == null ? Mode.WEEKLY : mode);
            rebuild();
        });
        onChange(weeklyInterval.valueProperty(), weeklyAnchor.valueProperty(),
                monthlyKind.selectedToggleProperty(), monthlyDay.valueProperty(), monthlyOrdinal.valueProperty(),
                monthlyWeekday.valueProperty(), monthlyInterval.valueProperty(), monthlyAnchor.valueProperty(),
                yearlyMonth.valueProperty(), yearlyKind.selectedToggleProperty(), yearlyDay.valueProperty(),
                yearlyOrdinal.valueProperty(), yearlyWeekday.valueProperty(),
                feastBox.valueProperty(), feastOffset.valueProperty(), customText.textProperty(),
                validFrom.valueProperty(), validUntil.valueProperty());
        weekdayToggles.values().forEach(toggle -> onChange(toggle.selectedProperty()));

        setSchedule(initial);
    }

    Region node() {
        return root;
    }

    /// The schedule currently described by the controls. Never null; a mode
    /// with nothing selected yet yields [RecurrenceRule#NEVER].
    ServiceSchedule schedule() {
        return schedule.get();
    }

    /// For `CrudModule`'s dirty tracking and for the owning editor's
    /// live push - fires whenever any control changes the schedule.
    ReadOnlyObjectProperty<ServiceSchedule> scheduleProperty() {
        return schedule;
    }

    /// Seeds the controls from `value` - for an `EditorBinding`
    /// refresh, or when the editor is first built.
    void setSchedule(ServiceSchedule value) {
        seeding = true;
        try {
            seedControls(value);
        } finally {
            seeding = false;
        }
        schedule.set(value);
        describe(value);
    }

    // --- schedule <-> controls ---------------------------------------------

    private void rebuild() {
        if (seeding) {
            return;
        }
        ServiceSchedule built = new ServiceSchedule(buildRule(), validFrom.getValue(), validUntil.getValue(),
                Set.copyOf(skipDates));
        schedule.set(built);
        describe(built);
    }

    private RecurrenceRule buildRule() {
        Mode mode = modeBox.getValue();
        return switch (mode == null ? Mode.WEEKLY : mode) {
            case WEEKLY -> weeklyRule();
            case MONTHLY -> withInterval(monthlyBase(), monthlyInterval.getValue(), monthlyAnchor.getValue());
            case YEARLY -> yearlyRule();
            case FEAST -> RecurrenceRule.feast(feastValue(), feastOffset.getValue());
            case CUSTOM -> customRule();
        };
    }

    private RecurrenceRule weeklyRule() {
        Set<DayOfWeek> days = selectedWeekdays();
        if (days.isEmpty()) {
            return RecurrenceRule.NEVER;
        }
        RecurrenceRule weekly = new RecurrenceRule.Weekday(days);
        int interval = weeklyInterval.getValue();
        return interval == 1
                ? weekly
                : RecurrenceRule.allOf(weekly, new RecurrenceRule.EveryNWeeks(interval, anchorOr(weeklyAnchor)));
    }

    private RecurrenceRule monthlyBase() {
        if (monthlyByWeekday.isSelected()) {
            return RecurrenceRule.nthWeekdayOfMonth(ordinalOr(monthlyOrdinal), weekdayOr(monthlyWeekday));
        }
        return monthlyByLastDay.isSelected()
                ? RecurrenceRule.dayOfMonth(-1)
                : RecurrenceRule.dayOfMonth(monthlyDay.getValue());
    }

    private static RecurrenceRule withInterval(RecurrenceRule base, int months, @Nullable LocalDate anchor) {
        return months == 1
                ? base
                : RecurrenceRule.allOf(new RecurrenceRule.EveryNMonths(months, anchor == null ? LocalDate.now() : anchor),
                        base);
    }

    private RecurrenceRule yearlyRule() {
        Month month = yearlyMonth.getValue() == null ? Month.JANUARY : yearlyMonth.getValue();
        if (yearlyByWeekday.isSelected()) {
            return RecurrenceRule.allOf(RecurrenceRule.inMonths(month),
                    RecurrenceRule.nthWeekdayOfMonth(ordinalOr(yearlyOrdinal), weekdayOr(yearlyWeekday)));
        }
        int day = Math.min(yearlyDay.getValue(), month.maxLength());
        return RecurrenceRule.fixedMonthDay(month, day);
    }

    /// The typed rule, or - while the text is half-written - the last rule
    /// that did parse, so that a stray keystroke never wipes a template's
    /// pattern.
    private RecurrenceRule customRule() {
        RecurrenceRule parsed = RecurrenceCodec.parse(customText.getText());
        customValid.setSelected(parsed != null);
        return parsed == null ? schedule.get().rule() : parsed;
    }

    private void seedControls(ServiceSchedule value) {
        // Defaults first, so that the shape-specific seeding below only has to
        // set what it actually knows.
        weeklyInterval.getValueFactory().setValue(1);
        monthlyInterval.getValueFactory().setValue(1);
        monthlyKind.selectToggle(monthlyByDay);
        yearlyKind.selectToggle(yearlyByDay);
        yearlyMonth.setValue(Month.JANUARY);
        feastBox.setValue(LiturgicalDay.EASTER);
        feastOffset.getValueFactory().setValue(0);
        customText.setText(RecurrenceCodec.format(value.rule()));
        customValid.setSelected(true);
        validFrom.setValue(value.validFrom());
        validUntil.setValue(value.validUntil());
        skipDates.clear();
        skipDates.addAll(value.skipDates());
        rebuildSkipDateChips();

        Mode mode = seedGuidedMode(value.rule());
        modeBox.getSelectionModel().select(mode);
        showMode(mode);
    }

    /// Fills the fields of whichever guided mode `rule` corresponds to
    /// and returns that mode, or [Mode#CUSTOM] for a rule none of them
    /// can hold.
    private Mode seedGuidedMode(RecurrenceRule rule) {
        RecurrenceRule base = rule;
        if (rule instanceof RecurrenceRule.AllOf allOf && allOf.rules().size() == 2) {
            RecurrenceRule first = allOf.rules().getFirst();
            RecurrenceRule second = allOf.rules().getLast();
            if (second instanceof RecurrenceRule.EveryNWeeks weeks && first instanceof RecurrenceRule.Weekday) {
                weeklyInterval.getValueFactory().setValue(weeks.n());
                weeklyAnchor.setValue(weeks.anchor());
                base = first;
            } else if (first instanceof RecurrenceRule.EveryNMonths months) {
                monthlyInterval.getValueFactory().setValue(months.n());
                monthlyAnchor.setValue(months.anchor());
                base = second;
            } else if (first instanceof RecurrenceRule.MonthOfYear month && month.months().size() == 1
                    && second instanceof RecurrenceRule.NthWeekdayOfMonth nth && isSimple(nth)) {
                yearlyMonth.setValue(month.months().iterator().next());
                yearlyKind.selectToggle(yearlyByWeekday);
                yearlyOrdinal.setValue(nth.ordinals().iterator().next());
                yearlyWeekday.setValue(nth.days().iterator().next());
                return Mode.YEARLY;
            } else {
                return Mode.CUSTOM;
            }
        }
        return seedBase(base);
    }

    private Mode seedBase(RecurrenceRule base) {
        switch (base) {
            case RecurrenceRule.Weekday weekday -> {
                selectWeekdays(weekday.days());
                return Mode.WEEKLY;
            }
            case RecurrenceRule.DayOfMonth dayOfMonth when dayOfMonth.days().size() == 1 -> {
                int day = dayOfMonth.days().iterator().next();
                monthlyKind.selectToggle(day == -1 ? monthlyByLastDay : monthlyByDay);
                if (day > 0) {
                    monthlyDay.getValueFactory().setValue(day);
                }
                return day < -1 ? Mode.CUSTOM : Mode.MONTHLY;
            }
            case RecurrenceRule.NthWeekdayOfMonth nth when isSimple(nth) -> {
                monthlyKind.selectToggle(monthlyByWeekday);
                monthlyOrdinal.setValue(nth.ordinals().iterator().next());
                monthlyWeekday.setValue(nth.days().iterator().next());
                return Mode.MONTHLY;
            }
            case RecurrenceRule.FixedMonthDay fixed -> {
                yearlyMonth.setValue(fixed.monthDay().getMonth());
                yearlyKind.selectToggle(yearlyByDay);
                yearlyDay.getValueFactory().setValue(fixed.monthDay().getDayOfMonth());
                return Mode.YEARLY;
            }
            case RecurrenceRule.FeastRelative feast -> {
                feastBox.setValue(feast.feast());
                feastOffset.getValueFactory().setValue(feast.offsetDays());
                return Mode.FEAST;
            }
            default -> {
                return Mode.CUSTOM;
            }
        }
    }

    private static boolean isSimple(RecurrenceRule.NthWeekdayOfMonth nth) {
        return nth.ordinals().size() == 1 && nth.days().size() == 1
                && ORDINALS.contains(nth.ordinals().iterator().next());
    }

    private void describe(ServiceSchedule value) {
        summary.setText(RecurrenceText.describe(value));
        List<LocalDate> dates = value.nextOccurrences(LocalDate.now(), PREVIEW_COUNT);
        preview.setText(dates.isEmpty()
                ? Localization.lang("No upcoming dates")
                : Localization.lang("Next dates: %0",
                        dates.stream().map(LocalDate::toString).collect(Collectors.joining(", "))));
    }

    // --- skipped dates -----------------------------------------------------

    private void addSkipDate() {
        LocalDate date = skipDatePicker.getValue();
        if (date == null || !skipDates.add(date)) {
            return;
        }
        rebuildSkipDateChips();
        rebuild();
    }

    /// One removable chip per skipped date - a list this short (a summer
    /// break, a handful of cancellations) reads better inline than in a list
    /// view.
    private void rebuildSkipDateChips() {
        List<Button> chips = new ArrayList<>();
        for (LocalDate date : skipDates) {
            Button chip = new Button(date + "  ×");
            chip.setTooltip(new Tooltip(Localization.lang("Remove this date")));
            chip.setOnAction(event -> {
                skipDates.remove(date);
                rebuildSkipDateChips();
                rebuild();
            });
            chips.add(chip);
        }
        skipDateChips.getChildren().setAll(chips);
    }

    // --- layout ------------------------------------------------------------

    private void buildWeeklyControls() {
        DayOfWeek firstDay = WeekFields.of(Locale.getDefault()).getFirstDayOfWeek();
        for (int i = 0; i < DayOfWeek.values().length; i++) {
            DayOfWeek day = firstDay.plus(i);
            ToggleButton toggle = new ToggleButton(day.getDisplayName(TextStyle.SHORT, Locale.getDefault()));
            toggle.setMinWidth(44);
            weekdayToggles.put(day, toggle);
        }
        weeklyAnchor.setValue(LocalDate.now());
        monthlyAnchor.setValue(LocalDate.now());
    }

    private void buildMonthlyControls() {
        monthlyByDay.setToggleGroup(monthlyKind);
        monthlyByWeekday.setToggleGroup(monthlyKind);
        monthlyByLastDay.setToggleGroup(monthlyKind);
        monthlyKind.selectToggle(monthlyByDay);
        monthlyOrdinal.setValue(1);
        monthlyWeekday.setValue(DayOfWeek.SUNDAY);
    }

    private void buildYearlyControls() {
        yearlyByDay.setToggleGroup(yearlyKind);
        yearlyByWeekday.setToggleGroup(yearlyKind);
        yearlyKind.selectToggle(yearlyByDay);
        yearlyMonth.setConverter(converter(month -> month.getDisplayName(TextStyle.FULL, Locale.getDefault())));
        yearlyMonth.setValue(Month.JANUARY);
        yearlyOrdinal.setValue(1);
        yearlyWeekday.setValue(DayOfWeek.SUNDAY);
        feastBox.setConverter(converter(EnumDisplay::of));
        feastBox.setValue(LiturgicalDay.EASTER);
    }

    private void showMode(Mode mode) {
        modeBody.getChildren().setAll(switch (mode) {
            case WEEKLY -> List.of(
                    row(weekdayToggles.values().toArray(Region[]::new)),
                    row(new Label(Localization.lang("Repeat every")), weeklyInterval,
                            new Label(Localization.lang("weeks, starting")), weeklyAnchor));
            case MONTHLY -> List.of(
                    row(monthlyByDay, monthlyDay),
                    row(monthlyByWeekday, monthlyOrdinal, monthlyWeekday),
                    row(monthlyByLastDay),
                    row(new Label(Localization.lang("Repeat every")), monthlyInterval,
                            new Label(Localization.lang("months, starting")), monthlyAnchor));
            case YEARLY -> List.of(
                    row(new Label(Localization.lang("Month")), yearlyMonth),
                    row(yearlyByDay, yearlyDay),
                    row(yearlyByWeekday, yearlyOrdinal, yearlyWeekday));
            case FEAST -> List.of(
                    row(feastBox),
                    row(new Label(Localization.lang("Shifted by")), feastOffset, new Label(Localization.lang("days"))));
            case CUSTOM -> List.of(
                    row(customText),
                    row(customValid));
        });
    }

    private static HBox row(Region... controls) {
        HBox row = new HBox(8, controls);
        row.setAlignment(Pos.CENTER_LEFT);
        return row;
    }

    // --- control plumbing --------------------------------------------------

    private void onChange(ObservableValue<?>... properties) {
        for (ObservableValue<?> property : properties) {
            property.addListener((obs, old, value) -> rebuild());
        }
    }

    private Set<DayOfWeek> selectedWeekdays() {
        return weekdayToggles.entrySet().stream()
                .filter(entry -> entry.getValue().isSelected())
                .map(Map.Entry::getKey)
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    private void selectWeekdays(Set<DayOfWeek> days) {
        weekdayToggles.forEach((day, toggle) -> toggle.setSelected(days.contains(day)));
    }

    private LiturgicalDay feastValue() {
        return feastBox.getValue() == null ? LiturgicalDay.EASTER : feastBox.getValue();
    }

    private static int ordinalOr(ComboBox<Integer> box) {
        return box.getValue() == null ? 1 : box.getValue();
    }

    private static DayOfWeek weekdayOr(ComboBox<DayOfWeek> box) {
        return box.getValue() == null ? DayOfWeek.SUNDAY : box.getValue();
    }

    private static LocalDate anchorOr(CalendarPicker picker) {
        return picker.getValue() == null ? LocalDate.now() : picker.getValue();
    }

    private static ComboBox<Integer> ordinalBox() {
        ComboBox<Integer> box = new ComboBox<>(FXCollections.observableArrayList(ORDINALS));
        box.setConverter(converter(RecurrenceText::ordinal));
        return box;
    }

    private static ComboBox<DayOfWeek> weekdayBox() {
        ComboBox<DayOfWeek> box = new ComboBox<>(FXCollections.observableArrayList(DayOfWeek.values()));
        box.setConverter(converter(day -> day.getDisplayName(TextStyle.FULL, Locale.getDefault())));
        return box;
    }

    /// One-way display converter - every box here is selection-only, so
    /// parsing typed text back is never needed.
    private static <T> StringConverter<T> converter(Function<T, String> display) {
        return new StringConverter<>() {
            @Override
            public String toString(@Nullable T value) {
                return value == null ? "" : display.apply(value);
            }

            @Override
            public @Nullable T fromString(@Nullable String text) {
                return null;
            }
        };
    }

    /// What kind of pattern the fields below the mode picker describe.
    private enum Mode {
        WEEKLY,
        MONTHLY,
        YEARLY,
        FEAST,
        CUSTOM
    }

    private static String modeLabel(Mode mode) {
        return switch (mode) {
            case WEEKLY -> Localization.lang("Weekly");
            case MONTHLY -> Localization.lang("Monthly");
            case YEARLY -> Localization.lang("Yearly");
            case FEAST -> Localization.lang("Feast day");
            case CUSTOM -> Localization.lang("Custom rule");
        };
    }
}
