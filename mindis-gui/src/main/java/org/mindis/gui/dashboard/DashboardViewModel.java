package org.mindis.gui.dashboard;

import io.avaje.inject.Prototype;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import org.jspecify.annotations.Nullable;

import org.mindis.core.overview.PlanOverview;
import org.mindis.core.persistence.ArchivedServiceRepository;
import org.mindis.core.persistence.RoleRepository;
import org.mindis.core.persistence.ServerRepository;
import org.mindis.core.persistence.ServiceRepository;
import org.mindis.core.preferences.DashboardWidgetLayout;
import org.mindis.core.preferences.PreferencesService;

/// ViewModel for the dashboard: feeds the document's current state into
/// [PlanOverview] for the view to render, and persists the widget layout.
@Prototype
public final class DashboardViewModel {

    private final ServiceRepository serviceRepository;
    private final ServerRepository serverRepository;
    private final RoleRepository roleRepository;
    private final ArchivedServiceRepository archivedServiceRepository;
    private final PreferencesService preferencesService;

    public DashboardViewModel(ServiceRepository serviceRepository, ServerRepository serverRepository,
                              RoleRepository roleRepository, ArchivedServiceRepository archivedServiceRepository,
                              PreferencesService preferencesService) {
        this.serviceRepository = serviceRepository;
        this.serverRepository = serverRepository;
        this.roleRepository = roleRepository;
        this.archivedServiceRepository = archivedServiceRepository;
        this.preferencesService = preferencesService;
    }

    /// The persisted widget layout, or - when the user has never arranged the
    /// board (null in preferences) - the default arrangement. An unknown widget
    /// id (e.g. a removed widget type from a newer version) is skipped; an
    /// unknown or no-longer-supported view mode falls back to the widget type's
    /// default, so an older or newer layout still loads.
    public List<WidgetPlacement> loadLayout() {
        @Nullable List<DashboardWidgetLayout> saved = preferencesService.get().dashboardWidgets();
        if (saved == null) {
            return defaultLayout();
        }
        List<WidgetPlacement> layout = new ArrayList<>();
        for (DashboardWidgetLayout entry : saved) {
            WidgetType.fromId(entry.widgetId()).ifPresent(type -> layout.add(
                    new WidgetPlacement(type, entry.col(), entry.row(), entry.colSpan(), entry.rowSpan(),
                            type.resolveMode(modeOf(entry)))));
        }
        return layout;
    }

    private static @Nullable WidgetViewMode modeOf(DashboardWidgetLayout entry) {
        @Nullable String saved = entry.viewMode();
        return saved == null ? null : WidgetViewMode.fromId(saved).orElse(null);
    }

    /// Persists the current board arrangement (positions, spans and view modes).
    public void saveLayout(List<WidgetPlacement> placements) {
        List<DashboardWidgetLayout> saved = new ArrayList<>();
        for (WidgetPlacement placement : placements) {
            saved.add(new DashboardWidgetLayout(placement.type().id(),
                    placement.col(), placement.row(), placement.colSpan(), placement.rowSpan(),
                    placement.mode().id()));
        }
        preferencesService.update(preferences -> preferences.withDashboardWidgets(saved));
    }

    private static List<WidgetPlacement> defaultLayout() {
        List<WidgetPlacement> layout = new ArrayList<>();
        for (WidgetType type : WidgetType.values()) {
            layout.add(type.defaultPlacement());
        }
        return layout;
    }

    public PlanOverview loadOverview() {
        return PlanOverview.of(serviceRepository.findAll(), serverRepository.findAll(), roleRepository.findAll(),
                archivedServiceRepository.findAll(), LocalDateTime.now());
    }
}
