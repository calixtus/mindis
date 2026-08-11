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

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

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
