package org.mindis.gui.dashboard;

import io.avaje.inject.Prototype;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;

import org.mindis.core.l10n.EnumDisplay;
import org.mindis.core.l10n.Localization;
import org.mindis.core.model.LiturgicalService;
import org.mindis.core.model.Server;
import org.mindis.core.persistence.PlanRepository;
import org.mindis.core.persistence.ServerRepository;
import org.mindis.core.persistence.ServiceRepository;
import org.mindis.core.planning.AcceptedPlan;

/**
 * Overview: upcoming services with their staffing state from the accepted
 * plan, unassigned-slot count, and per-server load.
 */
@Prototype
public class DashboardController {

    private static final DateTimeFormatter DATE_TIME_FORMAT = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm");
    private static final int MAX_NEXT_SERVICES = 8;

    private final ServiceRepository serviceRepository;
    private final ServerRepository serverRepository;
    private final PlanRepository planRepository;

    @FXML
    private Label summaryLabel;
    @FXML
    private ListView<String> nextServicesList;
    @FXML
    private ListView<String> serverLoadList;

    public DashboardController(ServiceRepository serviceRepository,
                               ServerRepository serverRepository,
                               PlanRepository planRepository) {
        this.serviceRepository = serviceRepository;
        this.serverRepository = serverRepository;
        this.planRepository = planRepository;
    }

    @FXML
    private void initialize() {
        Optional<AcceptedPlan> plan = planRepository.load();

        List<String> upcoming = serviceRepository.findAll().stream()
                .filter(service -> service.dateTime().isAfter(LocalDateTime.now()))
                .limit(MAX_NEXT_SERVICES)
                .map(service -> describeService(service, plan))
                .toList();
        nextServicesList.setItems(FXCollections.observableArrayList(upcoming));

        plan.ifPresentOrElse(accepted -> {
            long unassigned = accepted.assignments().stream()
                    .filter(assignment -> assignment.serverId() == null)
                    .count();
            summaryLabel.setText(Localization.lang("Unassigned slots") + ": " + unassigned);

            Map<String, Server> serversById = new LinkedHashMap<>();
            serverRepository.findAll().forEach(server -> serversById.put(server.id(), server));
            Map<String, Long> countByServer = new LinkedHashMap<>();
            accepted.assignments().forEach(assignment -> {
                if (assignment.serverId() != null) {
                    countByServer.merge(assignment.serverId(), 1L, Long::sum);
                }
            });
            List<String> load = countByServer.entrySet().stream()
                    .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                    .map(entry -> {
                        Server server = serversById.get(entry.getKey());
                        return (server == null ? entry.getKey() : server.displayName()) + ": " + entry.getValue();
                    })
                    .toList();
            serverLoadList.setItems(FXCollections.observableArrayList(load));
        }, () -> summaryLabel.setText(Localization.lang("No plan saved yet")));
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
