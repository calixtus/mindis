package org.mindis.gui.dashboard;

import io.avaje.inject.Prototype;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.mindis.core.l10n.EnumDisplay;
import org.mindis.core.l10n.Localization;
import org.mindis.core.model.LiturgicalService;
import org.mindis.core.model.Server;
import org.mindis.core.persistence.PlanRepository;
import org.mindis.core.persistence.ServerRepository;
import org.mindis.core.persistence.ServiceRepository;
import org.mindis.core.planning.AcceptedPlan;

/// ViewModel for {@link DashboardController}: owns every repository call and
/// the upcoming-services/server-load aggregation, so the controller only
/// constructs UI and binds to this class.
@Prototype
public class DashboardViewModel {

    private static final DateTimeFormatter DATE_TIME_FORMAT = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm");
    private static final int MAX_NEXT_SERVICES = 8;

    private final ServiceRepository serviceRepository;
    private final ServerRepository serverRepository;
    private final PlanRepository planRepository;

    public DashboardViewModel(ServiceRepository serviceRepository, ServerRepository serverRepository,
                              PlanRepository planRepository) {
        this.serviceRepository = serviceRepository;
        this.serverRepository = serverRepository;
        this.planRepository = planRepository;
    }

    /// Summary text, upcoming services and per-server load, computed off one plan read.
    public record Snapshot(String summaryText, List<String> upcomingServices, List<String> serverLoad) {
    }

    public Snapshot loadSnapshot() {
        Optional<AcceptedPlan> plan = planRepository.load();
        return new Snapshot(summaryText(plan), upcomingServices(plan), serverLoad(plan));
    }

    private String summaryText(Optional<AcceptedPlan> plan) {
        if (plan.isEmpty()) {
            return Localization.lang("No plan saved yet");
        }
        long unassigned = plan.get().assignments().stream()
                .filter(assignment -> assignment.serverId() == null)
                .count();
        return Localization.lang("Unassigned slots") + ": " + unassigned;
    }

    private List<String> upcomingServices(Optional<AcceptedPlan> plan) {
        return serviceRepository.findAll().stream()
                .filter(service -> service.dateTime().isAfter(LocalDateTime.now()))
                .limit(MAX_NEXT_SERVICES)
                .map(service -> describeService(service, plan))
                .toList();
    }

    private List<String> serverLoad(Optional<AcceptedPlan> plan) {
        if (plan.isEmpty()) {
            return List.of();
        }
        Map<String, Server> serversById = new LinkedHashMap<>();
        serverRepository.findAll().forEach(server -> serversById.put(server.id(), server));
        Map<String, Long> countByServer = new LinkedHashMap<>();
        plan.get().assignments().forEach(assignment -> {
            if (assignment.serverId() != null) {
                countByServer.merge(assignment.serverId(), 1L, Long::sum);
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

    private String describeService(LiturgicalService service, Optional<AcceptedPlan> plan) {
        String base = service.dateTime().format(DATE_TIME_FORMAT) + "  "
                + EnumDisplay.of(service.type())
                + (service.location().isBlank() ? "" : "  " + service.location());
        if (plan.isEmpty()) {
            return base;
        }
        long total = plan.get().assignments().stream()
                .filter(assignment -> assignment.serviceId().equals(service.id()))
                .count();
        if (total == 0) {
            return base;
        }
        long assigned = plan.get().assignments().stream()
                .filter(assignment -> assignment.serviceId().equals(service.id()))
                .filter(assignment -> assignment.serverId() != null)
                .count();
        return base + "  (" + assigned + "/" + total + ")";
    }
}
