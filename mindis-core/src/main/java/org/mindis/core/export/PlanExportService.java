package org.mindis.core.export;

import jakarta.inject.Singleton;

import java.nio.file.Path;
import java.time.format.DateTimeFormatter;
import java.time.format.FormatStyle;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.mindis.core.l10n.EnumDisplay;
import org.mindis.core.l10n.Localization;
import org.mindis.core.model.LiturgicalService;
import org.mindis.core.model.Role;
import org.mindis.core.model.Server;
import org.mindis.core.persistence.RoleRepository;
import org.mindis.core.persistence.ServerRepository;
import org.mindis.core.persistence.ServiceRepository;
import org.mindis.core.planning.AcceptedPlan;

/// Builds a localized, format-agnostic {@link PlanExportDocument} from an
/// {@link AcceptedPlan} and dispatches it to the {@link PlanExporter}
/// registered for the requested {@link PlanExportFormat} (PLAN.md M5).
@Singleton
public class PlanExportService {

    private final ServerRepository serverRepository;
    private final ServiceRepository serviceRepository;
    private final RoleRepository roleRepository;
    private final Map<PlanExportFormat, PlanExporter> exporters = new EnumMap<>(PlanExportFormat.class);

    public PlanExportService(ServerRepository serverRepository, ServiceRepository serviceRepository,
                             RoleRepository roleRepository) {
        this.serverRepository = serverRepository;
        this.serviceRepository = serviceRepository;
        this.roleRepository = roleRepository;
        register(new PdfPlanExporter());
        register(new CsvPlanExporter());
        register(new TxtPlanExporter());
        register(new RtfPlanExporter());
        register(new MarkdownPlanExporter());
    }

    private void register(PlanExporter exporter) {
        exporters.put(exporter.format(), exporter);
    }

    public void export(AcceptedPlan plan, Path targetFile, PlanExportFormat format) {
        PlanExporter exporter = exporters.get(format);
        if (exporter == null) {
            throw new IllegalArgumentException("No exporter registered for format: " + format);
        }
        exporter.export(buildDocument(plan), targetFile);
    }

    private PlanExportDocument buildDocument(AcceptedPlan plan) {
        Map<String, Server> serversById = new LinkedHashMap<>();
        serverRepository.findAll().forEach(server -> serversById.put(server.id(), server));
        Map<String, LiturgicalService> servicesById = new LinkedHashMap<>();
        serviceRepository.findAll().forEach(service -> servicesById.put(service.id(), service));
        Map<String, Role> rolesById = new LinkedHashMap<>();
        roleRepository.findAll().forEach(role -> rolesById.put(role.id(), role));

        DateTimeFormatter dateFormat = DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM);
        DateTimeFormatter dateTimeFormat = DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM, FormatStyle.SHORT);

        String title = Localization.lang("Altar server plan");
        String subtitle = plan.from().format(dateFormat) + " - " + plan.toInclusive().format(dateFormat);

        Map<String, List<AcceptedPlan.PlannedAssignment>> byService = new LinkedHashMap<>();
        plan.assignments().stream()
                .sorted((a, b) -> {
                    LiturgicalService serviceA = servicesById.get(a.serviceId());
                    LiturgicalService serviceB = servicesById.get(b.serviceId());
                    if (serviceA == null || serviceB == null) {
                        return 0;
                    }
                    return serviceA.dateTime().compareTo(serviceB.dateTime());
                })
                .forEach(assignment -> byService
                        .computeIfAbsent(assignment.serviceId(), key -> new ArrayList<>())
                        .add(assignment));

        List<PlanExportDocument.ServiceSection> sections = new ArrayList<>();
        for (Map.Entry<String, List<AcceptedPlan.PlannedAssignment>> entry : byService.entrySet()) {
            LiturgicalService service = servicesById.get(entry.getKey());
            String heading = service == null
                    ? entry.getKey()
                    : service.dateTime().format(dateTimeFormat) + "  "
                            + EnumDisplay.of(service.type()) + "  " + service.location();

            List<PlanExportDocument.AssignmentRow> rows = new ArrayList<>();
            for (AcceptedPlan.PlannedAssignment assignment : entry.getValue()) {
                Server server = assignment.serverId() == null ? null : serversById.get(assignment.serverId());
                Role role = rolesById.get(assignment.role());
                rows.add(new PlanExportDocument.AssignmentRow(
                        role == null ? assignment.role() : role.name(),
                        server == null ? "-" : server.displayName()));
            }
            sections.add(new PlanExportDocument.ServiceSection(heading, rows));
        }

        Map<String, Long> countByServer = new LinkedHashMap<>();
        plan.assignments().forEach(assignment -> {
            if (assignment.serverId() != null) {
                countByServer.merge(assignment.serverId(), 1L, Long::sum);
            }
        });
        List<PlanExportDocument.SummaryRow> summary = countByServer.entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                .map(entry -> {
                    Server server = serversById.get(entry.getKey());
                    return new PlanExportDocument.SummaryRow(
                            server == null ? entry.getKey() : server.displayName(),
                            entry.getValue());
                })
                .toList();

        PlanExportDocument.ColumnHeaders headers = new PlanExportDocument.ColumnHeaders(
                Localization.lang("Services"),
                Localization.lang("Role"),
                Localization.lang("Server"),
                Localization.lang("Count"));

        return new PlanExportDocument(
                title,
                subtitle,
                headers,
                sections,
                Localization.lang("Assignments per server"),
                summary);
    }
}
