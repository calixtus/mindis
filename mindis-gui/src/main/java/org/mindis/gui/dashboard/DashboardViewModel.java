package org.mindis.gui.dashboard;

import io.avaje.inject.Prototype;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.jspecify.annotations.Nullable;

import org.mindis.core.l10n.EnumDisplay;
import org.mindis.core.l10n.Localization;
import org.mindis.core.model.LiturgicalService;
import org.mindis.core.model.Server;
import org.mindis.core.persistence.ServerRepository;
import org.mindis.core.persistence.ServiceRepository;
import org.mindis.core.preferences.DashboardWidgetLayout;
import org.mindis.core.preferences.PreferencesService;

/// ViewModel for {@link DashboardController}: owns every repository call and
/// the upcoming-services/server-load aggregation, so the controller only
/// constructs UI and binds to this class. Assignments live on the service
/// slots, so everything is derived straight from the live services - there is
/// no separate plan to read.
@Prototype
public class DashboardViewModel {

    private static final DateTimeFormatter DATE_TIME_FORMAT = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm");
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
    /// id (e.g. a removed widget type from a newer version) is skipped.
    public List<WidgetPlacement> loadLayout() {
        @Nullable List<DashboardWidgetLayout> saved = preferencesService.get().dashboardWidgets();
        if (saved == null) {
            return defaultLayout();
        }
        List<WidgetPlacement> layout = new ArrayList<>();
        for (DashboardWidgetLayout entry : saved) {
            WidgetType.fromId(entry.widgetId()).ifPresent(type -> layout.add(
                    new WidgetPlacement(type, entry.col(), entry.row(), entry.colSpan(), entry.rowSpan())));
        }
        return layout;
    }

    /// Persists the current board arrangement (positions and spans).
    public void saveLayout(List<WidgetPlacement> placements) {
        List<DashboardWidgetLayout> saved = new ArrayList<>();
        for (WidgetPlacement placement : placements) {
            saved.add(new DashboardWidgetLayout(placement.type().id(),
                    placement.col(), placement.row(), placement.colSpan(), placement.rowSpan()));
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

    /// Summary text, upcoming services and per-server load, computed off the live services.
    public record Snapshot(String summaryText, List<String> upcomingServices, List<String> serverLoad) {
    }

    public Snapshot loadSnapshot() {
        List<LiturgicalService> services = serviceRepository.findAll();
        return new Snapshot(summaryText(services), upcomingServices(services), serverLoad(services));
    }

    private String summaryText(List<LiturgicalService> services) {
        long totalSlots = services.stream().mapToLong(service -> service.slots().size()).sum();
        if (totalSlots == 0) {
            return Localization.lang("No plan saved yet");
        }
        long unassigned = services.stream()
                .flatMap(service -> service.slots().stream())
                .filter(slot -> slot.serverId() == null)
                .count();
        return Localization.lang("Unassigned slots") + ": " + unassigned;
    }

    private List<String> upcomingServices(List<LiturgicalService> services) {
        return services.stream()
                .filter(service -> service.dateTime().isAfter(LocalDateTime.now()))
                .limit(MAX_NEXT_SERVICES)
                .map(this::describeService)
                .toList();
    }

    private List<String> serverLoad(List<LiturgicalService> services) {
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
                    return (server == null ? entry.getKey() : server.displayName()) + ": " + entry.getValue();
                })
                .toList();
    }

    private String describeService(LiturgicalService service) {
        String base = service.dateTime().format(DATE_TIME_FORMAT) + "  "
                + EnumDisplay.of(service.type())
                + (service.location().isBlank() ? "" : "  " + service.location());
        int total = service.slots().size();
        if (total == 0) {
            return base;
        }
        long assigned = service.slots().stream().filter(slot -> slot.serverId() != null).count();
        return base + "  (" + assigned + "/" + total + ")";
    }
}
