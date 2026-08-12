package org.mindis.gui.dashboard;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;

import javafx.scene.Node;
import javafx.scene.chart.BarChart;
import javafx.scene.chart.CategoryAxis;
import javafx.scene.chart.StackedBarChart;

import org.junit.jupiter.api.Test;

import org.mindis.gui.FxTest;

/// Covers what the charts have to survive rather than how they look: repeated
/// labels, which a category axis rejects outright, and the bucketing that keeps
/// a pie readable. Needs the real toolkit, so it runs through [FxTest].
class ChartsTest {

    /// Two services on one day, two servers with the same name: nothing the
    /// dashboard plots is unique. A repeated category makes
    /// `CategoryAxis.setCategories` throw, which would take the whole
    /// dashboard down rather than just one widget.
    @Test
    void bar_repeatedLabels_areNumberedInsteadOfThrowing() throws InterruptedException {
        FxTest.runAndWait(() -> {
            Node node = Charts.bar(List.of(new Charts.Slice("Anna Meier", 3),
                    new Charts.Slice("Anna Meier", 1)), "Assignments");

            BarChart<?, ?> chart = assertInstanceOf(BarChart.class, node);
            List<String> categories = ((CategoryAxis) chart.getXAxis()).getCategories();
            assertAll(
                    () -> assertEquals(List.of("Anna Meier", "Anna Meier (2)"), categories),
                    () -> assertEquals(2, chart.getData().getFirst().getData().size()));
        });
    }

    @Test
    void horizontalBar_repeatedLabels_areNumberedInsteadOfThrowing() throws InterruptedException {
        FxTest.runAndWait(() -> {
            Node node = Charts.horizontalBar(List.of(new Charts.Slice("Anna Meier", 3),
                    new Charts.Slice("Anna Meier", 1)), "Assignments");

            BarChart<?, ?> chart = assertInstanceOf(BarChart.class, node);
            // Reversed for the axis, so the numbered repeat comes first.
            assertEquals(List.of("Anna Meier (2)", "Anna Meier"),
                    ((CategoryAxis) chart.getYAxis()).getCategories());
        });
    }

    @Test
    void stackedBar_repeatedCategoryLabels_areNumberedInsteadOfThrowing() throws InterruptedException {
        FxTest.runAndWait(() -> {
            Node node = Charts.stackedBar(List.of("Sun 12", "Sun 12"),
                    List.of(new Charts.Series("Assigned", List.of(2.0, 1.0))), "Slots");

            StackedBarChart<?, ?> chart = assertInstanceOf(StackedBarChart.class, node);
            assertEquals(List.of("Sun 12", "Sun 12 (2)"),
                    ((CategoryAxis) chart.getXAxis()).getCategories());
        });
    }

    /// A label that already looks numbered must not collide with the number the
    /// repeat is given.
    @Test
    void bar_labelThatLooksLikeARepeat_stillEndsUpUnique() throws InterruptedException {
        FxTest.runAndWait(() -> {
            Node node = Charts.bar(List.of(new Charts.Slice("Anna", 1),
                    new Charts.Slice("Anna (2)", 1), new Charts.Slice("Anna", 1)), "Assignments");

            List<String> categories = ((CategoryAxis) ((BarChart<?, ?>) node).getXAxis()).getCategories();
            assertAll(
                    () -> assertEquals(3, categories.size()),
                    () -> assertEquals(3, categories.stream().distinct().count()));
        });
    }

    /// The "Others" bucket counts towards the limit: a pie of exactly
    /// [Charts#MAX_PIE_SLICES] slices is the most the chart still labels, so
    /// bucketing must not push it one slice over that and drop every label.
    @Test
    void topWithOthers_neverExceedsTheLimit() {
        List<Charts.Slice> data = new ArrayList<>();
        for (int i = 20; i > 0; i--) {
            data.add(new Charts.Slice("s" + i, i));
        }

        List<Charts.Slice> bucketed = Charts.topWithOthers(data, Charts.MAX_PIE_SLICES);

        assertAll(
                () -> assertEquals(Charts.MAX_PIE_SLICES, bucketed.size()),
                () -> assertTrue(bucketed.getLast().value() > 0, "the tail is summed into the last slice"),
                // Nothing is lost: the bucket carries what the top slices do not.
                () -> assertEquals(data.stream().mapToDouble(Charts.Slice::value).sum(),
                        bucketed.stream().mapToDouble(Charts.Slice::value).sum()));
    }

    @Test
    void topWithOthers_shortEnoughData_isLeftAlone() {
        List<Charts.Slice> data = List.of(new Charts.Slice("a", 2), new Charts.Slice("b", 1));

        assertEquals(data, Charts.topWithOthers(data, Charts.MAX_PIE_SLICES));
    }
}
