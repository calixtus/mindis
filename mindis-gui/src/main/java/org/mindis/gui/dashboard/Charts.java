package org.mindis.gui.dashboard;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.BiFunction;

import javafx.beans.value.ObservableValue;
import javafx.collections.FXCollections;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.chart.AreaChart;
import javafx.scene.chart.BarChart;
import javafx.scene.chart.CategoryAxis;
import javafx.scene.chart.Chart;
import javafx.scene.chart.LineChart;
import javafx.scene.chart.NumberAxis;
import javafx.scene.chart.PieChart;
import javafx.scene.chart.StackedBarChart;
import javafx.scene.chart.XYChart;
import javafx.scene.control.Label;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.util.StringConverter;

import org.jspecify.annotations.Nullable;

import org.mindis.core.l10n.Localization;

/// Builds the diagrams the dashboard widgets render, from plain
/// `(label, value)` data - the widgets hand over numbers, never JavaFX
/// chart types, so the view model stays free of both.
///
/// Every chart is built the same way on purpose: animation off (a widget is
/// rebuilt on each dashboard activation and on every mode switch, so animation
/// would only flash), no legend unless more than one series is plotted, a
/// tooltip per data point (the axes are too small in a widget to read exact
/// values off), and an empty-state label instead of an empty pair of axes.
final class Charts {

    /// Beyond this many slices a pie is unreadable; the caller is expected to
    /// have bucketed the tail into an "Others" slice (see
    /// [#topWithOthers]).
    static final int MAX_PIE_SLICES = 8;

    /// Tick labels for a value axis. Counts are whole numbers, and a small
    /// range auto-ranges into ticks like 0.5, which reads as if half a slot
    /// could be open. The tick unit cannot be pinned - auto-ranging recomputes
    /// it - so the fractional ticks are left unlabelled instead. Stateless, so
    /// every axis shares the one converter.
    private static final StringConverter<Number> WHOLE_NUMBERS = new StringConverter<>() {

        @Override
        public String toString(@Nullable Number value) {
            if (value == null) {
                return "";
            }
            double raw = value.doubleValue();
            return raw == Math.rint(raw) ? number(raw) : "";
        }

        @Override
        public Number fromString(@Nullable String text) {
            // A chart axis renders its tick labels and never parses them back.
            throw new UnsupportedOperationException();
        }
    };

    private Charts() {
    }

    /// One labelled number: a bar, a pie slice, or a point on a line.
    record Slice(String label, double value) {
    }

    /// One named row of values, aligned with the category labels passed
    /// alongside it - what a stacked bar or a multi-line chart is made of.
    record Series(String name, List<Double> values) {
    }

    /// A vertical bar per slice. Best when the labels are short (weeks, months).
    static Node bar(List<Slice> data, String valueAxisLabel) {
        if (data.isEmpty()) {
            return empty();
        }
        List<String> labels = numbered(data.stream().map(Slice::label).toList());
        BarChart<String, Number> chart = new BarChart<>(categoryAxis(labels), valueAxis(valueAxisLabel));
        chart.getData().add(seriesOf(labels, values(data), null));
        return configure(chart, false);
    }

    /// A horizontal bar per slice - the readable choice for long labels
    /// (server and role names).
    static Node horizontalBar(List<Slice> data, String valueAxisLabel) {
        if (data.isEmpty()) {
            return empty();
        }
        // Reversed: a category axis grows upward, so the largest value would
        // otherwise land at the bottom of a most-first list.
        List<String> bottomUp = numbered(data.stream().map(Slice::label).toList()).reversed();
        BarChart<Number, String> chart = new BarChart<>(valueAxis(valueAxisLabel), categoryAxis(bottomUp));
        chart.getData().add(horizontalSeriesOf(bottomUp, values(data.reversed()), null));
        return configure(chart, false);
    }

    /// Horizontal bars grouped per category, one bar per series - for comparing
    /// two figures about the same thing (a role's open slots against the
    /// servers qualified for it).
    static Node horizontalBar(List<String> categoryLabels, List<Series> series, String valueAxisLabel) {
        if (categoryLabels.isEmpty() || series.isEmpty()) {
            return empty();
        }
        List<String> labels = numbered(categoryLabels);
        BarChart<Number, String> chart = new BarChart<>(valueAxis(valueAxisLabel),
                categoryAxis(labels.reversed()));
        for (Series row : series) {
            chart.getData().add(horizontalSeriesOf(labels, row.values(), row.name()));
        }
        return configure(chart, series.size() > 1);
    }

    /// One stacked bar per category, one stack segment per series.
    static Node stackedBar(List<String> categoryLabels, List<Series> series, String valueAxisLabel) {
        return categoryChart(categoryLabels, series, valueAxisLabel, StackedBarChart::new);
    }

    static Node pie(List<Slice> data) {
        return pieChart(data, null);
    }

    /// A pie with a hole carrying a headline figure and its caption. JavaFX has
    /// no donut chart; the hole is a styled overlay on a pie.
    static Node donut(List<Slice> data, String centreValue, String centreCaption) {
        return pieChart(data, new Centre(centreValue, centreCaption));
    }

    /// What is written into a donut's hole.
    private record Centre(String value, String caption) {
    }

    static Node line(List<String> categoryLabels, List<Series> series, String valueAxisLabel) {
        return categoryChart(categoryLabels, series, valueAxisLabel, (categories, values) -> {
            LineChart<String, Number> chart = new LineChart<>(categories, values);
            chart.setCreateSymbols(true);
            return chart;
        });
    }

    static Node area(List<String> categoryLabels, List<Series> series, String valueAxisLabel) {
        return categoryChart(categoryLabels, series, valueAxisLabel, AreaChart::new);
    }

    /// The shape every chart plotting series against a shared category axis
    /// has: the categories numbered, one plotted series per row, a legend as
    /// soon as there is more than one of them. `factory` supplies the chart
    /// type, which is all that separates a stacked bar from a line.
    private static Node categoryChart(List<String> categoryLabels, List<Series> series, String valueAxisLabel,
            BiFunction<CategoryAxis, NumberAxis, XYChart<String, Number>> factory) {
        if (categoryLabels.isEmpty() || series.isEmpty()) {
            return empty();
        }
        List<String> labels = numbered(categoryLabels);
        XYChart<String, Number> chart = factory.apply(categoryAxis(labels), valueAxis(valueAxisLabel));
        for (Series row : series) {
            chart.getData().add(seriesOf(labels, row.values(), row.name()));
        }
        return configure(chart, series.size() > 1);
    }

    /// The widget body when there is nothing to plot - an empty chart frame
    /// would look like a rendering fault.
    static Node empty() {
        Label label = new Label(Localization.lang("Nothing to show"));
        label.getStyleClass().add("dashboard-empty");
        StackPane pane = new StackPane(label);
        pane.setAlignment(Pos.CENTER);
        return pane;
    }

    /// At most `limit` slices: the largest ones, with everything after
    /// them summed into a single trailing "Others" slice. The bucket counts
    /// towards the limit, so the result never exceeds it - a chart that drops
    /// its labels beyond `limit` slices would otherwise lose them in
    /// exactly the case the bucketing was meant to keep readable. `data`
    /// must already be sorted largest-first.
    static List<Slice> topWithOthers(List<Slice> data, int limit) {
        if (data.size() <= limit) {
            return data;
        }
        double others = data.subList(limit - 1, data.size()).stream().mapToDouble(Slice::value).sum();
        List<Slice> top = new ArrayList<>(data.subList(0, limit - 1));
        top.add(new Slice(Localization.lang("Others"), others));
        return List.copyOf(top);
    }

    /// The labels in order, with the second and every further occurrence of one
    /// suffixed by its occurrence number.
    ///
    /// Nothing the dashboard plots is guaranteed unique: two services can fall
    /// on one day, two servers can share a name. A category axis rejects a
    /// repeated category outright ([CategoryAxis#setCategories] throws) and a
    /// pie would draw two legend entries nobody can tell apart, so a repeat is
    /// numbered rather than dropped - both entries stay visible and say which
    /// is which. Numbering an already-unique list leaves it as it is, so the
    /// axis can apply it as well without a caller having to know whether it
    /// already did.
    private static List<String> numbered(List<String> labels) {
        Set<String> used = new HashSet<>();
        List<String> unique = new ArrayList<>();
        for (String label : labels) {
            String candidate = label;
            // A numbered label can collide in turn (a real label reading
            // "Anna (2)"), so it is numbered up until it is free.
            for (int occurrence = 2; !used.add(candidate); occurrence++) {
                candidate = label + " (" + occurrence + ")";
            }
            unique.add(candidate);
        }
        return List.copyOf(unique);
    }

    private static List<Double> values(List<Slice> data) {
        return data.stream().map(Slice::value).toList();
    }

    private static Node pieChart(List<Slice> data, @Nullable Centre centre) {
        if (data.isEmpty()) {
            return empty();
        }
        List<String> labels = numbered(data.stream().map(Slice::label).toList());
        PieChart chart = new PieChart();
        for (int i = 0; i < data.size(); i++) {
            Slice slice = new Slice(labels.get(i), data.get(i).value());
            PieChart.Data point = new PieChart.Data(slice.label(), slice.value());
            chart.getData().add(point);
            installTooltip(point.nodeProperty(), slice);
        }
        chart.setAnimated(false);
        chart.setLabelsVisible(data.size() <= MAX_PIE_SLICES);
        chart.setLegendVisible(true);
        chart.getStyleClass().add("dashboard-chart");
        if (centre == null) {
            return chart;
        }
        Label value = new Label(centre.value());
        value.getStyleClass().add("dashboard-donut-value");
        Label caption = new Label(centre.caption());
        caption.getStyleClass().add("dashboard-donut-caption");
        VBox hole = new VBox(value, caption);
        hole.getStyleClass().add("dashboard-donut-hole");
        hole.setAlignment(Pos.CENTER);
        hole.setMaxSize(VBox.USE_PREF_SIZE, VBox.USE_PREF_SIZE);
        hole.setMouseTransparent(true);
        StackPane pane = new StackPane(chart, hole);
        StackPane.setAlignment(hole, Pos.CENTER);
        return pane;
    }

    /// One plotted series over a category x-axis. `name` is null for the lone
    /// series of a single-series chart, which has no legend to name and whose
    /// tooltips read as the label alone.
    private static XYChart.Series<String, Number> seriesOf(List<String> labels, List<Double> values,
            @Nullable String name) {
        XYChart.Series<String, Number> series = new XYChart.Series<>();
        for (int i = 0; i < labels.size() && i < values.size(); i++) {
            XYChart.Data<String, Number> point = new XYChart.Data<>(labels.get(i), values.get(i));
            series.getData().add(point);
            installTooltip(point.nodeProperty(), tooltipSlice(labels.get(i), values.get(i), name));
        }
        return named(series, name);
    }

    /// As [#seriesOf], for a chart whose category axis is the y-axis.
    private static XYChart.Series<Number, String> horizontalSeriesOf(List<String> labels, List<Double> values,
            @Nullable String name) {
        XYChart.Series<Number, String> series = new XYChart.Series<>();
        for (int i = 0; i < labels.size() && i < values.size(); i++) {
            XYChart.Data<Number, String> point = new XYChart.Data<>(values.get(i), labels.get(i));
            series.getData().add(point);
            installTooltip(point.nodeProperty(), tooltipSlice(labels.get(i), values.get(i), name));
        }
        return named(series, name);
    }

    private static <X, Y> XYChart.Series<X, Y> named(XYChart.Series<X, Y> series, @Nullable String name) {
        if (name != null) {
            series.setName(name);
        }
        return series;
    }

    private static Slice tooltipSlice(String label, double value, @Nullable String seriesName) {
        return new Slice(seriesName == null ? label : seriesName + " - " + label, value);
    }

    /// The node of a data point exists only once the chart has laid itself out,
    /// so the tooltip is installed when it appears rather than right away.
    private static void installTooltip(ObservableValue<? extends Node> nodeProperty, Slice slice) {
        nodeProperty.subscribe(node -> {
            if (node != null) {
                Tooltip.install(node, new Tooltip(slice.label() + ": " + number(slice.value())));
            }
        });
    }

    /// Whole numbers are the usual case here (counts of slots, services,
    /// servers); only a genuinely fractional value keeps a decimal.
    private static String number(double value) {
        return value == Math.rint(value) ? String.valueOf((long) value) : String.valueOf(value);
    }

    /// The axis carrying `labels`, numbered through [#numbered] - the one place
    /// categories reach an axis, so no chart type can hand it a repeat and
    /// crash. A caller that plots against those categories numbers them itself
    /// as well, since its data points have to carry the same labels.
    private static CategoryAxis categoryAxis(List<String> labels) {
        List<String> categories = numbered(labels);
        CategoryAxis axis = new CategoryAxis();
        axis.setCategories(FXCollections.observableArrayList(categories));
        axis.setAnimated(false);
        // Beyond a handful of categories horizontal labels overlap; tilting
        // them keeps every one readable in a widget-sized chart.
        axis.setTickLabelRotation(categories.size() > 6 ? -45 : 0);
        return axis;
    }

    private static NumberAxis valueAxis(String label) {
        NumberAxis axis = new NumberAxis();
        axis.setAnimated(false);
        axis.setLabel(label);
        axis.setMinorTickVisible(false);
        axis.setTickLabelFormatter(WHOLE_NUMBERS);
        return axis;
    }

    private static <X, Y> Node configure(XYChart<X, Y> chart, boolean legend) {
        chart.setAnimated(false);
        chart.setLegendVisible(legend);
        chart.setHorizontalGridLinesVisible(true);
        chart.setVerticalGridLinesVisible(false);
        return styled(chart);
    }

    private static Node styled(Chart chart) {
        chart.getStyleClass().add("dashboard-chart");
        chart.setMinSize(0, 0);
        return chart;
    }
}
