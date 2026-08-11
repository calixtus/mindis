package org.mindis.gui.dashboard;

import io.avaje.inject.Prototype;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.jspecify.annotations.Nullable;

import org.mindis.core.model.LiturgicalService;
import org.mindis.core.model.Server;
import org.mindis.core.model.ServiceType;
import org.mindis.core.persistence.ServerRepository;
import org.mindis.core.persistence.ServiceRepository;
import org.mindis.core.preferences.DashboardWidgetLayout;
import org.mindis.core.preferences.PreferencesService;

/// ViewModel for the dashboard: owns every repository call and the
/// upcoming-services/server-load aggregation, and hands the view plain data to
/// render. Assignments live on the service slots, so everything is derived
/// straight from the live services - there is no separate plan to read.
@Prototype
public final class DashboardViewModel {

    private static final int MAX_NEXT_SERVICES = 8;

    private final ServiceRepository serviceRepository;
    private final ServerRepository serverRepository;
    private final PreferencesService preferencesService;

    public DashboardViewModel(ServiceRepository serviceRepository, ServerRepository serverRepository,
                              PreferencesService preferencesService) {
        this.serviceRepository = serviceRepository;
        this.serverRepository = serverRepository;
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

    /// What the board shows, as data: no formatted text, no locale, no layout.
    /// Rendering it - dates, separators, "n/m" - is the view's job, so the same
    /// numbers could drive a chart or an export without unpicking a string.
    public record Snapshot(int unassignedSlots, int totalSlots,
                           List<UpcomingService> upcomingServices,
                           List<ServerLoad> serverLoad) {

        public Snapshot {
            upcomingServices = List.copyOf(upcomingServices);
            serverLoad = List.copyOf(serverLoad);
        }

        /// Whether the document holds no plan at all yet (no slots anywhere).
        public boolean isEmpty() {
            return totalSlots == 0;
        }
    }

    /// One entry of the "next services" widget.
    public record UpcomingService(LocalDateTime dateTime, ServiceType type, String location,
                                  int assignedSlots, int totalSlots) {
    }

    /// One entry of the "assignments per server" widget, most-loaded first.
    public record ServerLoad(String serverName, long assignments) {
    }

    public Snapshot loadSnapshot() {
        List<LiturgicalService> services = serviceRepository.findAll();
        int totalSlots = services.stream().mapToInt(service -> service.slots().size()).sum();
        int unassigned = (int) services.stream()
                .flatMap(service -> service.slots().stream())
                .filter(slot -> slot.serverId() == null)
                .count();
        return new Snapshot(unassigned, totalSlots, upcomingServices(services), serverLoad(services));
    }

    private static List<UpcomingService> upcomingServices(List<LiturgicalService> services) {
        return services.stream()
                .filter(service -> service.dateTime().isAfter(LocalDateTime.now()))
                .limit(MAX_NEXT_SERVICES)
                .map(service -> new UpcomingService(
                        service.dateTime(),
                        service.type(),
                        service.location(),
                        (int) service.slots().stream().filter(slot -> slot.serverId() != null).count(),
                        service.slots().size()))
                .toList();
    }

    private List<ServerLoad> serverLoad(List<LiturgicalService> services) {
        Map<String, Server> serversById = new LinkedHashMap<>();
        serverRepository.findAll().forEach(server -> serversById.put(server.id(), server));
        Map<String, Long> countByServer = new LinkedHashMap<>();
        services.stream()
                .flatMap(service -> service.slots().stream())
                .forEach(slot -> {
                    if (slot.serverId() != null) {
                        countByServer.merge(slot.serverId(), 1L, Long::sum);
                    }
                });
        return countByServer.entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                .map(entry -> {
                    Server server = serversById.get(entry.getKey());
                    // An id with no server left (deleted while still assigned)
                    // falls back to the raw id rather than vanishing.
                    return new ServerLoad(server == null ? entry.getKey() : server.displayName(), entry.getValue());
                })
                .toList();
    }
}
