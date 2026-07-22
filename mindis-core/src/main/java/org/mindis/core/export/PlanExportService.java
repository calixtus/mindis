package org.mindis.core.export;

import jakarta.inject.Singleton;

import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.FormatStyle;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.jspecify.annotations.Nullable;

import org.mindis.core.l10n.EnumDisplay;
import org.mindis.core.l10n.Localization;
import org.mindis.core.model.ArchivedService;
import org.mindis.core.model.LiturgicalService;
import org.mindis.core.model.Role;
import org.mindis.core.model.Server;
import org.mindis.core.model.ServiceType;
import org.mindis.core.model.Slot;
import org.mindis.core.persistence.RoleRepository;
import org.mindis.core.persistence.ServerRepository;

/// Builds a localized, format-agnostic {@link PlanExportDocument} from a set of
/// services and dispatches it to the {@link PlanExporter} registered for the
/// requested {@link PlanExportFormat} (PLAN.md M5).
///
/// <p>Two entry points, one document builder: {@link #exportLive} resolves each
/// slot's role/server against the live roster; {@link #exportArchived} reads
/// the display names straight off the self-contained {@link ArchivedService}
/// snapshot, so a frozen plan still exports faithfully after the servers or
/// roles it referenced are gone.
@Singleton
public class PlanExportService {

    private final ServerRepository serverRepository;
    private final RoleRepository roleRepository;
    private final Map<PlanExportFormat, PlanExporter> exporters = new EnumMap<>(PlanExportFormat.class);

    public PlanExportService(ServerRepository serverRepository, RoleRepository roleRepository) {
        this.serverRepository = serverRepository;
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

    /// Exports the given live services, resolving names against the current roster.
    public void exportLive(List<LiturgicalService> services, Path targetFile, PlanExportFormat format) {
        Map<String, Server> serversById = new LinkedHashMap<>();
        serverRepository.findAll().forEach(server -> serversById.put(server.id(), server));
        Map<String, Role> rolesById = new LinkedHashMap<>();
        roleRepository.findAll().forEach(role -> rolesById.put(role.id(), role));

        List<ServiceView> views = new ArrayList<>();
        for (LiturgicalService service : services) {
            List<RowView> rows = new ArrayList<>();
            for (Slot slot : service.slots()) {
                Role role = rolesById.get(slot.role());
                Server server = slot.serverId() == null ? null : serversById.get(slot.serverId());
                rows.add(new RowView(
                        role == null ? slot.role() : role.name(),
                        server == null ? null : server.displayName()));
            }
            views.add(new ServiceView(service.dateTime(), service.type(), service.location(), rows));
        }
        dispatch(views, targetFile, format);
    }

    /// Exports frozen archived services directly from their self-contained snapshots.
    public void exportArchived(List<ArchivedService> services, Path targetFile, PlanExportFormat format) {
        List<ServiceView> views = new ArrayList<>();
        for (ArchivedService service : services) {
            List<RowView> rows = new ArrayList<>();
            for (ArchivedService.ArchivedSlot slot : service.slots()) {
                rows.add(new RowView(slot.roleName(), slot.serverName()));
            }
            views.add(new ServiceView(service.dateTime(), service.type(), service.location(), rows));
        }
        dispatch(views, targetFile, format);
    }

    private void dispatch(List<ServiceView> views, Path targetFile, PlanExportFormat format) {
        PlanExporter exporter = exporters.get(format);
        if (exporter == null) {
            throw new IllegalArgumentException("No exporter registered for format: " + format);
        }
        exporter.export(buildDocument(views), targetFile);
    }

    private PlanExportDocument buildDocument(List<ServiceView> views) {
        List<ServiceView> sorted = new ArrayList<>(views);
        sorted.sort(Comparator.comparing(ServiceView::dateTime));

        DateTimeFormatter dateFormat = DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM);
        DateTimeFormatter dateTimeFormat = DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM, FormatStyle.SHORT);

        String title = Localization.lang("Altar server plan");
        String subtitle = sorted.isEmpty() ? "" : dateRange(sorted, dateFormat);

        List<PlanExportDocument.ServiceSection> sections = new ArrayList<>();
        Map<String, Long> countByServer = new LinkedHashMap<>();
        for (ServiceView view : sorted) {
            String heading = view.dateTime().format(dateTimeFormat) + "  "
                    + EnumDisplay.of(view.type()) + "  " + view.location();
            List<PlanExportDocument.AssignmentRow> rows = new ArrayList<>();
            for (RowView row : view.rows()) {
                rows.add(new PlanExportDocument.AssignmentRow(
                        row.roleName(), row.serverName() == null ? "-" : row.serverName()));
                if (row.serverName() != null) {
                    countByServer.merge(row.serverName(), 1L, Long::sum);
                }
            }
            sections.add(new PlanExportDocument.ServiceSection(heading, rows));
        }

        List<PlanExportDocument.SummaryRow> summary = countByServer.entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                .map(entry -> new PlanExportDocument.SummaryRow(entry.getKey(), entry.getValue()))
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

    private static String dateRange(List<ServiceView> sorted, DateTimeFormatter dateFormat) {
        LocalDate from = sorted.getFirst().dateTime().toLocalDate();
        LocalDate to = sorted.getLast().dateTime().toLocalDate();
        return from.format(dateFormat) + " - " + to.format(dateFormat);
    }

    /// One service, normalized so live and archived paths feed the same builder.
    private record ServiceView(LocalDateTime dateTime, ServiceType type, String location, List<RowView> rows) {
    }

    private record RowView(String roleName, @Nullable String serverName) {
    }
}
