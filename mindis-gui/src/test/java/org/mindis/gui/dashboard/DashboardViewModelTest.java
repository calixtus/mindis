package org.mindis.gui.dashboard;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import org.mindis.core.persistence.ArchivedServiceRepository;
import org.mindis.core.persistence.RoleRepository;
import org.mindis.core.persistence.ServerRepository;
import org.mindis.core.persistence.ServiceRepository;
import org.mindis.core.preferences.DashboardWidgetLayout;
import org.mindis.core.preferences.PreferencesService;
import org.mindis.gui.TestPreferences;
import org.mindis.gui.TestStores;

/// Covers the widget layout the view model persists; the figures it hands the
/// view are [org.mindis.core.overview.PlanOverview]'s and tested in core.
class DashboardViewModelTest {

    @TempDir
    Path tempDir;

    private final ServiceRepository services = new ServiceRepository();
    private final ServerRepository servers = new ServerRepository();
    private final RoleRepository roles = new RoleRepository();
    private final ArchivedServiceRepository archive = new ArchivedServiceRepository();

    private DashboardViewModel newViewModel() {
        return new DashboardViewModel(TestStores.services(services), TestStores.servers(servers),
                TestStores.roles(roles), archive, TestPreferences.at(
                tempDir.resolve("preferences.json")));
    }

    @Test
    void loadLayout_neverArranged_returnsEveryWidgetTypeOnce() {
        List<WidgetPlacement> layout = newViewModel().loadLayout();

        assertEquals(List.of(WidgetType.values()), layout.stream().map(WidgetPlacement::type).toList());
    }

    @Test
    void loadLayout_neverArranged_usesEachTypesDefaultMode() {
        for (WidgetPlacement placement : newViewModel().loadLayout()) {
            assertEquals(placement.type().defaultMode(), placement.mode());
        }
    }

    @Test
    void saveLayout_thenLoadLayout_keepsGeometryAndViewMode() {
        DashboardViewModel viewModel = newViewModel();
        WidgetPlacement saved = new WidgetPlacement(WidgetType.SERVER_LOAD, 3, 2, 4, 5,
                WidgetType.SERVER_LOAD.defaultMode());

        viewModel.saveLayout(List.of(saved));

        assertEquals(List.of(saved), viewModel.loadLayout());
    }

    /// A layout entry whose stored mode this version does not know - written by
    /// a newer version, or by one that offered a mode since dropped - must not
    /// lose the widget; it falls back to the type's default mode.
    @Test
    void loadLayout_unknownOrUnsupportedMode_fallsBackToTheDefault() {
        PreferencesService preferences = TestPreferences.at(tempDir.resolve("preferences.json"));
        preferences.update(p -> p.withDashboardWidgets(List.of(
                new DashboardWidgetLayout(WidgetType.SERVER_LOAD.id(), 0, 0, 6, 3, "sunburst"),
                new DashboardWidgetLayout(WidgetType.NEXT_SERVICES.id(), 0, 3, 6, 3, null))));
        DashboardViewModel viewModel = new DashboardViewModel(TestStores.services(services),
                TestStores.servers(servers),
                TestStores.roles(roles), archive, preferences);

        List<WidgetPlacement> layout = viewModel.loadLayout();

        assertAll(
                () -> assertEquals(2, layout.size()),
                () -> assertEquals(WidgetType.SERVER_LOAD.defaultMode(), layout.getFirst().mode()),
                () -> assertEquals(WidgetType.NEXT_SERVICES.defaultMode(), layout.get(1).mode()));
    }
}
