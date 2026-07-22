package org.mindis.gui.dashboard;

import io.avaje.inject.Prototype;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.mindis.core.l10n.EnumDisplay;
import org.mindis.core.l10n.Localization;
import org.mindis.core.model.LiturgicalService;
import org.mindis.core.model.Server;
import org.mindis.core.persistence.ServerRepository;
import org.mindis.core.persistence.ServiceRepository;

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

    public DashboardViewModel(ServiceRepository serviceRepository, ServerRepository serverRepository) {
        this.serviceRepository = serviceRepository;
        this.serverRepository = serverRepository;
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
