package org.mindis.gui.dashboard;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

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
    static Node bar(List<Slice> rawData, String valueAxisLabel) {
        if (rawData.isEmpty()) {
            return empty();
        }
        List<Slice> data = distinctLabels(rawData);
        CategoryAxis categories = categoryAxis(data.stream().map(Slice::label).toList(), data.size());
        NumberAxis values = valueAxis(valueAxisLabel);
        BarChart<String, Number> chart = new BarChart<>(categories, values);
        chart.getData().add(seriesOf(data));
        return configure(chart, false);
    }

    /// A horizontal bar per slice - the readable choice for long labels
    /// (server and role names).
    static Node horizontalBar(List<Slice> rawData, String valueAxisLabel) {
        if (rawData.isEmpty()) {
            return empty();
        }
        // Reversed: a category axis grows upward, so the largest value would
        // otherwise land at the bottom of a most-first list.
        List<Slice> bottomUp = distinctLabels(rawData).reversed();
        NumberAxis values = valueAxis(valueAxisLabel);
        CategoryAxis categories = categoryAxis(bottomUp.stream().map(Slice::label).toList(), bottomUp.size());
        BarChart<Number, String> chart = new BarChart<>(values, categories);
        XYChart.Series<Number, String> series = new XYChart.Series<>();
        for (Slice slice : bottomUp) {
            XYChart.Data<Number, String> point = new XYChart.Data<>(slice.value(), slice.label());
            series.getData().add(point);
            installTooltip(point.nodeProperty(), slice);
        }
        chart.getData().add(series);
        return configure(chart, false);
    }

    /// Horizontal bars grouped per category, one bar per series - for comparing
    /// two figures about the same thing (a role's open slots against the
    /// servers qualified for it).
    static Node horizontalBar(List<String> rawCategoryLabels, List<Series> series, String valueAxisLabel) {
        if (rawCategoryLabels.isEmpty() || series.isEmpty()) {
            return empty();
        }
        List<String> categoryLabels = distinct(rawCategoryLabels);
        List<String> bottomUp = categoryLabels.reversed();
        BarChart<Number, String> chart = new BarChart<>(valueAxis(valueAxisLabel),
                categoryAxis(bottomUp, bottomUp.size()));
        for (Series row : series) {
            XYChart.Series<Number, String> plotted = new XYChart.Series<>();
            plotted.setName(row.name());
            for (int i = 0; i < categoryLabels.size() && i < row.values().size(); i++) {
                String label = categoryLabels.get(i);
                double value = row.values().get(i);
                XYChart.Data<Number, String> point = new XYChart.Data<>(value, label);
                plotted.getData().add(point);
                installTooltip(point.nodeProperty(), new Slice(row.name() + " - " + label, value));
            }
            chart.getData().add(plotted);
        }
        return configure(chart, series.size() > 1);
    }

    /// One stacked bar per category, one stack segment per series.
    static Node stackedBar(List<String> rawCategoryLabels, List<Series> series, String valueAxisLabel) {
        if (rawCategoryLabels.isEmpty() || series.isEmpty()) {
            return empty();
        }
        List<String> categoryLabels = distinct(rawCategoryLabels);
        CategoryAxis categories = categoryAxis(categoryLabels, categoryLabels.size());
        StackedBarChart<String, Number> chart = new StackedBarChart<>(categories, valueAxis(valueAxisLabel));
        for (Series row : series) {
            chart.getData().add(seriesOf(row, categoryLabels));
        }
        return configure(chart, series.size() > 1);
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

    static Node line(List<String> rawCategoryLabels, List<Series> series, String valueAxisLabel) {
        if (rawCategoryLabels.isEmpty() || series.isEmpty()) {
            return empty();
        }
        List<String> categoryLabels = distinct(rawCategoryLabels);
        LineChart<String, Number> chart = new LineChart<>(
                categoryAxis(categoryLabels, categoryLabels.size()), valueAxis(valueAxisLabel));
        for (Series row : series) {
            chart.getData().add(seriesOf(row, categoryLabels));
        }
        chart.setCreateSymbols(true);
        return configure(chart, series.size() > 1);
    }

    static Node area(List<String> rawCategoryLabels, List<Series> series, String valueAxisLabel) {
        if (rawCategoryLabels.isEmpty() || series.isEmpty()) {
            return empty();
        }
        List<String> categoryLabels = distinct(rawCategoryLabels);
        AreaChart<String, Number> chart = new AreaChart<>(
                categoryAxis(categoryLabels, categoryLabels.size()), valueAxis(valueAxisLabel));
        for (Series row : series) {
            chart.getData().add(seriesOf(row, categoryLabels));
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

    /// The same slices with every label made unique. A category axis rejects a
    /// repeated category outright ([CategoryAxis#setCategories] throws), and
    /// nothing the dashboard plots is guaranteed unique: two services can fall
    /// on one day, two servers can share a name. A repeat is numbered rather
    /// than dropped, so both entries stay visible and tell each other apart.
    private static List<Slice> distinctLabels(List<Slice> data) {
        List<String> labels = distinct(data.stream().map(Slice::label).toList());
        List<Slice> distinct = new ArrayList<>();
        for (int i = 0; i < data.size(); i++) {
            distinct.add(new Slice(labels.get(i), data.get(i).value()));
        }
        return List.copyOf(distinct);
    }

    /// See [#distinctLabels]: the labels in order, with the second and every
    /// further occurrence of one suffixed by its occurrence number.
    private static List<String> distinct(List<String> labels) {
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

    private static Node pieChart(List<Slice> data, @Nullable Centre centre) {
        if (data.isEmpty()) {
            return empty();
        }
        PieChart chart = new PieChart();
        for (Slice slice : data) {
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

    private static XYChart.Series<String, Number> seriesOf(List<Slice> data) {
        XYChart.Series<String, Number> series = new XYChart.Series<>();
        for (Slice slice : data) {
            XYChart.Data<String, Number> point = new XYChart.Data<>(slice.label(), slice.value());
            series.getData().add(point);
            installTooltip(point.nodeProperty(), slice);
        }
        return series;
    }

    private static XYChart.Series<String, Number> seriesOf(Series row, List<String> categoryLabels) {
        XYChart.Series<String, Number> series = new XYChart.Series<>();
        series.setName(row.name());
        for (int i = 0; i < categoryLabels.size() && i < row.values().size(); i++) {
            String label = categoryLabels.get(i);
            double value = row.values().get(i);
            XYChart.Data<String, Number> point = new XYChart.Data<>(label, value);
            series.getData().add(point);
            installTooltip(point.nodeProperty(), new Slice(row.name() + " - " + label, value));
        }
        return series;
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

    private static CategoryAxis categoryAxis(List<String> labels, int count) {
        CategoryAxis axis = new CategoryAxis();
        axis.setCategories(FXCollections.observableArrayList(labels));
        axis.setAnimated(false);
        // Beyond a handful of categories horizontal labels overlap; tilting
        // them keeps every one readable in a widget-sized chart.
        axis.setTickLabelRotation(count > 6 ? -45 : 0);
        return axis;
    }

    private static NumberAxis valueAxis(String label) {
        NumberAxis axis = new NumberAxis();
        axis.setAnimated(false);
        axis.setLabel(label);
        axis.setMinorTickVisible(false);
        // Counts are whole numbers, and a small range auto-ranges into ticks
        // like 0.5, which reads as if half a slot could be open. The tick unit
        // cannot be pinned - auto-ranging recomputes it - so the fractional
        // ticks are left unlabelled instead.
        axis.setTickLabelFormatter(new StringConverter<>() {

            @Override
            public String toString(@Nullable Number value) {
                if (value == null) {
                    return "";
                }
                double raw = value.doubleValue();
                return raw == Math.rint(raw) ? String.valueOf((long) raw) : "";
            }

            @Override
            public Number fromString(@Nullable String text) {
                return text == null || text.isBlank() ? 0 : Double.valueOf(text);
            }
        });
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
