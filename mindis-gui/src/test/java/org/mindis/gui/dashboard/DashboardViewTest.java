package org.mindis.gui.dashboard;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.chart.BarChart;
import javafx.scene.chart.StackedBarChart;
import javafx.scene.control.MenuButton;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.Region;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import org.kordamp.ikonli.javafx.FontIcon;

import org.mindis.core.model.LiturgicalService;
import org.mindis.core.model.Server;
import org.mindis.core.model.ServiceType;
import org.mindis.core.model.Slot;
import org.mindis.core.persistence.ArchivedServiceRepository;
import org.mindis.core.persistence.RoleRepository;
import org.mindis.core.persistence.ServerRepository;
import org.mindis.core.persistence.ServiceRepository;
import org.mindis.core.preferences.DashboardWidgetLayout;
import org.mindis.core.preferences.PreferencesService;
import org.mindis.gui.FxTest;

/// Covers the view-mode chooser end to end: which widgets offer one, what
/// picking a diagram puts into the widget body, and that the choice is written
/// back to preferences. Needs the real toolkit (charts and menu buttons are
/// controls), so it runs through [FxTest] and skips where there is none.
class DashboardViewTest {

    @TempDir
    Path tempDir;

    private final ServiceRepository services = new ServiceRepository();
    private final ServerRepository servers = new ServerRepository();
    private final RoleRepository roles = new RoleRepository();
    private final ArchivedServiceRepository archive = new ArchivedServiceRepository();

    @Test
    void onlyWidgetsWithSeveralModesShowAModeChooser() throws InterruptedException {
        givenAssignedService();
        FxTest.runAndWait(() -> {
            DashboardView view = new DashboardView(newViewModel(FxTest.preferencesAt(preferencesFile())));

            List<Node> choosers = withStyleClass(view, "dashboard-widget-mode");

            long multiMode = List.of(WidgetType.values()).stream()
                    .filter(type -> type.modes().size() > 1)
                    .count();
            assertEquals(multiMode, choosers.size());
        });
    }

    @Test
    void pickingBarSwapsTheContentAndPersistsTheMode() throws InterruptedException {
        givenAssignedService();
        PreferencesService preferences = FxTest.preferencesAt(preferencesFile());
        FxTest.runAndWait(() -> {
            DashboardView view = new DashboardView(newViewModel(preferences));
            MenuButton chooser = chooserOf(view, WidgetType.SERVER_LOAD);

            fire(chooser, WidgetType.SERVER_LOAD, WidgetViewMode.BAR);

            assertAll(
                    () -> assertInstanceOf(BarChart.class, contentOf(view, WidgetType.SERVER_LOAD)),
                    () -> assertEquals(WidgetViewMode.BAR.id(), savedMode(preferences, WidgetType.SERVER_LOAD)));
        });
    }

    /// A mode picked in an earlier session comes back with the widget, without
    /// the user touching the chooser again.
    @Test
    void aPersistedModeIsRestored() throws InterruptedException {
        givenAssignedService();
        PreferencesService preferences = FxTest.preferencesAt(preferencesFile());
        preferences.update(p -> p.withDashboardWidgets(List.of(
                new DashboardWidgetLayout(WidgetType.SERVER_LOAD.id(), 0, 0, 6, 3, WidgetViewMode.BAR.id()))));

        FxTest.runAndWait(() -> {
            DashboardView view = new DashboardView(newViewModel(preferences));

            assertInstanceOf(BarChart.class, contentOf(view, WidgetType.SERVER_LOAD));
        });
    }

    /// The summary and the next-services widget each have a diagram of their
    /// own; picking it must reach the same content slot the lists use.
    @Test
    void theSummaryAndNextServicesWidgetsAlsoSwitchToTheirDiagram() throws InterruptedException {
        givenAssignedService();
        PreferencesService preferences = FxTest.preferencesAt(preferencesFile());
        FxTest.runAndWait(() -> {
            DashboardView view = new DashboardView(newViewModel(preferences));

            fire(chooserOf(view, WidgetType.SUMMARY), WidgetType.SUMMARY, WidgetViewMode.DONUT);
            fire(chooserOf(view, WidgetType.NEXT_SERVICES), WidgetType.NEXT_SERVICES, WidgetViewMode.STACKED_BAR);

            assertAll(
                    // A donut is a pie under a hole overlay, so the chart sits
                    // one level down rather than being the content node itself.
                    () -> assertEquals(1, withStyleClass(view, "dashboard-donut-hole").size()),
                    () -> assertInstanceOf(StackedBarChart.class,
                            contentOf(view, WidgetType.NEXT_SERVICES)));
        });
    }

    /// Every mode names an Ikonli glyph, and an unknown icon code only fails
    /// when the icon is built - which, for a mode nobody has picked yet, would
    /// be in front of the user rather than here.
    @Test
    void everyViewModeHasAResolvableIcon() throws InterruptedException {
        FxTest.runAndWait(() -> {
            for (WidgetViewMode mode : WidgetViewMode.values()) {
                assertEquals(mode.iconCode(), new FontIcon(mode.iconCode()).getIconLiteral());
            }
        });
    }

    /// Each widget must render in each of its own modes; a mode that throws
    /// would only be found when a user picks it.
    @Test
    void everyWidgetRendersInEveryModeItOffers() throws InterruptedException {
        givenAssignedService();
        PreferencesService preferences = FxTest.preferencesAt(preferencesFile());
        FxTest.runAndWait(() -> {
            DashboardView view = new DashboardView(newViewModel(preferences));
            for (WidgetType type : WidgetType.values()) {
                if (type.modes().size() < 2) {
                    continue;
                }
                for (WidgetViewMode mode : type.modes()) {
                    fire(chooserOf(view, type), type, mode);
                    assertEquals(1, widgetFor(view, type).content().getChildren().size(),
                            "expected exactly one content node for " + type.id() + " in " + mode.id());
                }
            }
        });
    }

    /// The summary must survive being dragged small: rather than running over
    /// the widget header or past its border, the figures are set smaller until
    /// they fit. Sized stand-ins stand in for the tiles, since text has no
    /// measurable size without a window on screen.
    @Test
    void keyFiguresShrinkTheirFontWhenTheyDoNotFit() throws InterruptedException {
        FxTest.runAndWait(() -> {
            Region tooBig = new Region();
            tooBig.setPrefSize(300, 200);
            KeyFigures figures = new KeyFigures(tooBig);

            figures.resize(120, 40);
            figures.layout();

            assertTrue(figures.getStyle().contains("-fx-font-size"),
                    "expected the figures to be scaled down, style was: '" + figures.getStyle() + "'");
        });
    }

    /// However big the content insists on being, the card stays the size the
    /// board gives it and its body stays inside - otherwise a widget dragged
    /// small draws over its neighbours.
    @Test
    void aWidgetStaysWithinTheSizeTheBoardGivesIt() throws InterruptedException {
        FxTest.runAndWait(() -> {
            WidgetContainer widget = new WidgetContainer(WidgetType.SUMMARY.defaultPlacement(), _ -> { });
            Region stubborn = new Region();
            stubborn.setMinSize(400, 300);
            widget.content().getChildren().add(stubborn);

            widget.resizeRelocate(0, 0, 180, 60);
            widget.layout();

            assertAll(
                    () -> assertEquals(180, widget.getWidth()),
                    () -> assertEquals(60, widget.getHeight()),
                    () -> assertTrue(widget.content().getBoundsInParent().getMaxY() <= 60.5,
                            "the body runs past the card: " + widget.content().getBoundsInParent()));
        });
    }

    @Test
    void keyFiguresKeepTheirFontWhenThereIsRoom() throws InterruptedException {
        FxTest.runAndWait(() -> {
            Region small = new Region();
            small.setPrefSize(40, 20);
            KeyFigures figures = new KeyFigures(small);

            figures.resize(400, 200);
            figures.layout();

            assertEquals("", figures.getStyle());
        });
    }

    @Test
    void anEmptyDocumentRendersAnEmptyStateInsteadOfAChart() throws InterruptedException {
        PreferencesService preferences = FxTest.preferencesAt(preferencesFile());
        preferences.update(p -> p.withDashboardWidgets(List.of(
                new DashboardWidgetLayout(WidgetType.SERVER_LOAD.id(), 0, 0, 6, 3, WidgetViewMode.PIE.id()))));

        FxTest.runAndWait(() -> {
            DashboardView view = new DashboardView(newViewModel(preferences));

            assertTrue(withStyleClass(view, "dashboard-empty").size() == 1,
                    "expected the chart's empty state");
        });
    }

    private DashboardViewModel newViewModel(PreferencesService preferences) {
        return new DashboardViewModel(services, servers, roles, archive, preferences);
    }

    private Path preferencesFile() {
        return tempDir.resolve("preferences.json");
    }

    private void givenAssignedService() {
        servers.save(new Server("srv1", "Anna", "Becker", "", null, null,
                Set.of(), List.of(), Set.of(), false, true));
        services.save(new LiturgicalService("s1", LocalDateTime.now().plusDays(1), 60, "St. Mary",
                ServiceType.SUNDAY_MASS, List.of(new Slot(Slot.newId(), "ACOLYTE", "srv1", false)), ""));
    }

    private static String savedMode(PreferencesService preferences, WidgetType type) {
        List<DashboardWidgetLayout> saved = preferences.get().dashboardWidgets();
        if (saved == null) {
            throw new AssertionError("no layout saved");
        }
        return saved.stream()
                .filter(entry -> entry.widgetId().equals(type.id()))
                .map(DashboardWidgetLayout::viewMode)
                .findFirst()
                .orElseThrow(() -> new AssertionError("no layout entry for " + type.id()));
    }

    /// The single node the given widget currently renders its data with.
    private static Node contentOf(Parent view, WidgetType type) {
        return widgetFor(view, type).content().getChildren().getFirst();
    }

    /// That widget's own view-mode chooser - every multi-mode widget has one,
    /// so picking by position on the board would pick another widget's.
    private static MenuButton chooserOf(Parent view, WidgetType type) {
        return (MenuButton) withStyleClass(widgetFor(view, type), "dashboard-widget-mode").getFirst();
    }

    /// Picks `mode` from a widget's chooser, whose items are in the
    /// widget type's own mode order.
    private static void fire(MenuButton chooser, WidgetType type, WidgetViewMode mode) {
        chooser.getItems().get(type.modes().indexOf(mode)).fire();
    }

    private static WidgetContainer widgetFor(Parent view, WidgetType type) {
        for (Node node : withStyleClass(view, "dashboard-widget")) {
            if (node instanceof WidgetContainer widget && widget.type() == type) {
                return widget;
            }
        }
        throw new AssertionError("no widget for " + type.id());
    }

    private static List<Node> withStyleClass(Parent root, String styleClass) {
        List<Node> found = new ArrayList<>();
        collect(root, styleClass, found);
        return found;
    }

    private static void collect(Node node, String styleClass, List<Node> found) {
        if (node.getStyleClass().contains(styleClass)) {
            found.add(node);
        }
        if (node instanceof ScrollPane scrollPane && scrollPane.getContent() != null) {
            collect(scrollPane.getContent(), styleClass, found);
        }
        if (node instanceof Parent parent) {
            for (Node child : parent.getChildrenUnmodifiable()) {
                collect(child, styleClass, found);
            }
        }
    }
}
