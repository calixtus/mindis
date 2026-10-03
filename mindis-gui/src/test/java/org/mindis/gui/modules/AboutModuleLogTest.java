package org.mindis.gui.modules;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.List;
import java.util.logging.Level;

import atlantafx.base.theme.NordLight;

import javafx.application.Application;
import javafx.geometry.Orientation;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.ScrollBar;
import javafx.scene.layout.StackPane;

import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.kordamp.ikonli.javafx.FontIcon;

import org.mindis.gui.FxTest;
import org.mindis.gui.logging.LogConsoleModel;
import org.mindis.gui.logging.LogEntry;
import org.mindis.gui.theme.ThemeStyler;

/// The log list on the About screen: long messages are cut off instead of widening the
/// list, and only those (and multi-line ones) offer the chevron that shows them whole.
class AboutModuleLogTest {

    private static final LogEntry SHORT = entry("Saved");
    private static final LogEntry LONG = entry("Could not reach the update server ".repeat(20));
    private static final LogEntry MULTI_LINE = entry("First line\nSecond line");

    /// The app's theme, whose fixed list-cell height the rows are laid out against.
    @BeforeEach
    void installTheme() throws InterruptedException {
        FxTest.runAndWait(() -> Application.setUserAgentStylesheet(
                ThemeStyler.withModenaTokens(new NordLight()).getUserAgentStylesheet()));
    }

    @AfterEach
    void removeTheme() throws InterruptedException {
        FxTest.runAndWait(() -> Application.setUserAgentStylesheet(null));
    }

    private static LogEntry entry(String message) {
        return new LogEntry(Instant.now(), Level.WARNING, "test", message, null);
    }

    /// The list on a laid-out About screen, holding [#SHORT], [#LONG] and [#MULTI_LINE].
    // NullAway: HostServices only opens links, which nothing here does.
    @SuppressWarnings("NullAway")
    private static ListView<LogEntry> logList() {
        LogConsoleModel log = new LogConsoleModel();
        log.entries().setAll(SHORT, LONG, MULTI_LINE);
        Node content = new AboutModule("About", null, log).activate();
        Scene scene = new Scene(new StackPane(content), 700, 900);
        layout(scene.getRoot());
        @SuppressWarnings("unchecked")
        ListView<LogEntry> list = FxTest.find(content, ListView.class);
        return list;
    }

    private static void layout(Node node) {
        node.applyCss();
        if (node instanceof javafx.scene.Parent parent) {
            parent.layout();
        }
        node.applyCss();
        if (node instanceof javafx.scene.Parent parent) {
            parent.layout();
        }
    }

    private static ListCell<?> cellOf(ListView<LogEntry> list, LogEntry entry) {
        return list.lookupAll(".list-cell").stream()
                .map(node -> (ListCell<?>) node)
                .filter(cell -> cell.getItem() == entry)
                .findFirst()
                .orElseThrow(() -> new AssertionError("no cell shows " + entry.message()));
    }

    private static @Nullable Button expandButton(ListCell<?> cell) {
        return FxTest.findAll(cell, Button.class).stream()
                .filter(button -> button.getGraphic() instanceof FontIcon icon
                        && icon.getIconLiteral().startsWith("mdi2c-chevron"))
                .findFirst()
                .orElse(null);
    }

    @Test
    void aLongMessageDoesNotScrollTheListSideways() throws InterruptedException {
        FxTest.runAndWait(() -> {
            ListView<LogEntry> list = logList();

            boolean horizontalBarShown = list.lookupAll(".scroll-bar").stream()
                    .map(node -> (ScrollBar) node)
                    .anyMatch(bar -> bar.getOrientation() == Orientation.HORIZONTAL && bar.isVisible());

            assertFalse(horizontalBarShown);
        });
    }

    @Test
    void onlyCutOffAndMultiLineMessagesOfferTheChevron() throws InterruptedException {
        FxTest.runAndWait(() -> {
            ListView<LogEntry> list = logList();

            assertAll(
                    () -> assertFalse(expandButton(cellOf(list, SHORT)).isVisible(), "short"),
                    () -> assertTrue(expandButton(cellOf(list, LONG)).isVisible(), "long"),
                    () -> assertTrue(expandButton(cellOf(list, MULTI_LINE)).isVisible(), "multi-line"));
        });
    }

    @Test
    void expandingShowsTheWholeMessageWrapped() throws InterruptedException {
        FxTest.runAndWait(() -> {
            ListView<LogEntry> list = logList();
            ListCell<?> cell = cellOf(list, LONG);
            double collapsedHeight = cell.getHeight();
            Button expand = expandButton(cell);
            assertNotNull(expand);

            expand.fire();
            layout(list.getScene().getRoot());
            ListCell<?> expanded = cellOf(list, LONG);
            Label text = FxTest.findAll(expanded, Label.class).getFirst();

            assertAll(
                    () -> assertTrue(text.isWrapText()),
                    () -> assertTrue(text.getText().endsWith(LONG.message())),
                    () -> assertTrue(expanded.getHeight() > collapsedHeight * 2,
                            "a wrapped message spans several lines"));
        });
    }

    @Test
    void collapsedRowsShowLineBreaksAsSpaces() throws InterruptedException {
        FxTest.runAndWait(() -> {
            ListView<LogEntry> list = logList();

            List<Label> labels = FxTest.findAll(cellOf(list, MULTI_LINE), Label.class);

            assertTrue(labels.getFirst().getText().endsWith("First line Second line"));
        });
    }

    private static void mouse(Node target, javafx.event.EventType<javafx.scene.input.MouseEvent> type, int clicks) {
        target.fireEvent(new javafx.scene.input.MouseEvent(type, 0, 0, 0, 0,
                javafx.scene.input.MouseButton.PRIMARY, clicks,
                false, false, false, false, true, false, false, false, false, false, null));
    }

    @Test
    void aDoubleClickExpandsTheRow() throws InterruptedException {
        FxTest.runAndWait(() -> {
            ListView<LogEntry> list = logList();

            mouse(cellOf(list, LONG), javafx.scene.input.MouseEvent.MOUSE_CLICKED, 2);
            layout(list.getScene().getRoot());

            assertTrue(FxTest.findAll(cellOf(list, LONG), Label.class).getFirst().isWrapText());
        });
    }

    @Test
    void selectingARowDoesNotExpandIt() throws InterruptedException {
        FxTest.runAndWait(() -> {
            ListView<LogEntry> list = logList();

            list.getSelectionModel().select(LONG);
            layout(list.getScene().getRoot());

            assertFalse(FxTest.findAll(cellOf(list, LONG), Label.class).getFirst().isWrapText());
        });
    }

    /// The hover buttons are taller than a line of text; the row must not grow to fit them.
    @Test
    void hoveringKeepsTheRowHeight() throws InterruptedException {
        FxTest.runAndWait(() -> {
            ListView<LogEntry> list = logList();
            ListCell<?> cell = cellOf(list, SHORT);
            double before = cell.getHeight();

            mouse(cell, javafx.scene.input.MouseEvent.MOUSE_ENTERED, 0);
            layout(list.getScene().getRoot());

            assertAll(
                    () -> assertEquals(before, cellOf(list, SHORT).getHeight()),
                    () -> assertTrue(before > FxTest.findAll(cell, Label.class).getFirst().getHeight() * 1.5,
                            "a collapsed row keeps the theme's cell height, not the text's"));
        });
    }

    private static Label textOf(ListCell<?> cell) {
        return FxTest.findAll(cell, Label.class).getFirst();
    }

    private static javafx.scene.layout.Region actionsOf(ListCell<?> cell) {
        return (javafx.scene.layout.Region) expandButton(cell).getParent();
    }

    /// The buttons lie over the text instead of taking room from it, so showing them on
    /// hover cannot re-wrap or re-cut the message.
    @Test
    void hoverButtonsOverlayTheTextWithoutNarrowingIt() throws InterruptedException {
        FxTest.runAndWait(() -> {
            ListView<LogEntry> list = logList();
            ListCell<?> cell = cellOf(list, LONG);
            double widthBefore = textOf(cell).getWidth();

            mouse(cell, javafx.scene.input.MouseEvent.MOUSE_ENTERED, 0);
            layout(list.getScene().getRoot());
            javafx.geometry.Bounds text = textOf(cell).localToScene(textOf(cell).getLayoutBounds());
            javafx.geometry.Bounds actions = actionsOf(cell).localToScene(actionsOf(cell).getLayoutBounds());

            assertAll(
                    () -> assertTrue(actionsOf(cell).isVisible()),
                    () -> assertEquals(widthBefore, textOf(cell).getWidth()),
                    () -> assertTrue(actions.getMinX() < text.getMaxX(), "the buttons cover the end of the text"));
        });
    }

    /// The fade behind the buttons ends in the row's own fill, also once the row is selected.
    @Test
    void theFadeEndsInTheRowBackground() throws InterruptedException {
        FxTest.runAndWait(() -> {
            ListView<LogEntry> list = logList();
            list.getSelectionModel().select(LONG);
            layout(list.getScene().getRoot());

            for (LogEntry entry : List.of(SHORT, LONG)) {
                ListCell<?> cell = cellOf(list, entry);
                javafx.scene.paint.Paint fade = actionsOf(cell).getBackground().getFills().getFirst().getFill();
                javafx.scene.paint.Paint rowFill = cell.getBackground().getFills().getLast().getFill();

                assertTrue(fade instanceof javafx.scene.paint.LinearGradient gradient
                                && gradient.getStops().getLast().getColor().equals(rowFill),
                        entry.message() + ": " + fade + " should end in " + rowFill);
            }
        });
    }
}
