package org.mindis.core.export;

import jakarta.inject.Singleton;

import java.nio.file.Path;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.FormatStyle;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.mindis.core.l10n.Localization;
import org.mindis.core.model.ArchivedService;
import org.mindis.core.model.Indexes;
import org.mindis.core.model.LiturgicalService;
import org.mindis.core.model.Role;
import org.mindis.core.model.RoleId;
import org.mindis.core.model.Server;
import org.mindis.core.model.ServerId;
import org.mindis.core.model.Slot;
import org.mindis.core.persistence.AppDatabase;
import org.mindis.core.persistence.RoleRepository;
import org.mindis.core.persistence.ServerRepository;
import org.mindis.core.preferences.DataDirectory;

/// Builds a localized, format-agnostic [PlanExportDocument] from a set of
/// services and dispatches it to the [PlanExporter] registered for the
/// requested [PlanExportFormat] (PLAN.md M5).
///
/// <p>Two entry points, one document builder: [#exportLive] resolves each
/// slot's role/server against the live roster; [#exportArchived] reads
/// the display names straight off the self-contained [ArchivedService]
/// snapshot, so a frozen plan still exports faithfully after the servers or
/// roles it referenced are gone.
@Singleton
public final class PlanExportService {

    private final ServerRepository serverRepository;
    private final RoleRepository roleRepository;
    private final AppDatabase database;
    private final PlanTemplate template;
    private final Map<PlanExportFormat, PlanExporter> exporters = new EnumMap<>(PlanExportFormat.class);
    private final Map<PlanExportFormat, PlanRenderer> renderers = new EnumMap<>(PlanExportFormat.class);
    private final Map<PlanExportFormat, PlanCalendarExporter> calendarExporters =
            new EnumMap<>(PlanExportFormat.class);

    /// A format is supported by contributing a bean of one of the three
    /// strategy shapes; this service only dispatches by [PlanExportFormat].
    PlanExportService(ServerRepository serverRepository, RoleRepository roleRepository,
                      AppDatabase database, PlanTemplate template, List<PlanExporter> exporters,
                      List<PlanRenderer> renderers, List<PlanCalendarExporter> calendarExporters) {
        this.serverRepository = serverRepository;
        this.roleRepository = roleRepository;
        this.database = database;
        this.template = template;
        exporters.forEach(exporter -> this.exporters.put(exporter.format(), exporter));
        renderers.forEach(renderer -> this.renderers.put(renderer.format(), renderer));
        calendarExporters.forEach(exporter -> this.calendarExporters.put(exporter.format(), exporter));
    }

    /// Every built-in format, for wiring without the container (tests, a CLI).
    public static PlanExportService withBuiltInFormats(ServerRepository serverRepository,
                                                       RoleRepository roleRepository,
                                                       AppDatabase database, DataDirectory dataDirectory) {
        return new PlanExportService(serverRepository, roleRepository, database,
                new PlanTemplate(dataDirectory),
                List.of(new CsvPlanExporter()),
                List.of(new PdfPlanRenderer(), new TextPlanRenderer(), new RtfPlanRenderer(),
                        new MarkdownPlanRenderer()),
                List.of(new IcsPlanExporter()));
    }

    /// Exports the given live services, resolving names against the current roster.
    public void exportLive(List<LiturgicalService> services, Path targetFile, PlanExportFormat format) {
        Map<ServerId, Server> serversById = Indexes.byKey(serverRepository.findAll(), Server::id);
        Map<RoleId, Role> rolesById = Indexes.byKey(roleRepository.findAll(), Role::id);

        List<PlanTemplateModel.Service> views = new ArrayList<>();
        for (LiturgicalService service : services) {
            List<PlanTemplateModel.Slot> slots = new ArrayList<>();
            for (Slot slot : service.slots()) {
                Role role = rolesById.get(slot.role());
                Server server = slot.serverId() == null ? null : serversById.get(slot.serverId());
                slots.add(new PlanTemplateModel.Slot(
                        slot.role().value(),
                        role == null ? slot.role().value() : role.name(),
                        server == null ? null : server.displayName()));
            }
            views.add(new PlanTemplateModel.Service(
                    service.id(), service.dateTime(), service.durationMinutes(), service.type(),
                    service.name(), service.note(), service.location(), slots));
        }
        dispatch(views, targetFile, format);
    }

    /// Exports frozen archived services directly from their self-contained snapshots.
    public void exportArchived(List<ArchivedService> services, Path targetFile, PlanExportFormat format) {
        List<PlanTemplateModel.Service> views = new ArrayList<>();
        for (ArchivedService service : services) {
            List<PlanTemplateModel.Slot> slots = new ArrayList<>();
            for (ArchivedService.ArchivedSlot slot : service.slots()) {
                // An archived slot kept the names, not the ids it was built from.
                slots.add(new PlanTemplateModel.Slot(
                        slot.roleName(), slot.roleName(), slot.serverName()));
            }
            views.add(new PlanTemplateModel.Service(
                    service.id(), service.dateTime(), service.durationMinutes(), service.type(),
                    service.name(), service.note(), service.location(), slots));
        }
        dispatch(views, targetFile, format);
    }

    /// Three shapes, by what the format actually needs. A calendar reads the services
    /// themselves, because it needs their start, end and place as values. CSV is written
    /// straight from the structured document - a spreadsheet wants columns, not a laid-out
    /// document. Everything else goes through the template, so all of those share one layout.
    private void dispatch(List<PlanTemplateModel.Service> views, Path targetFile, PlanExportFormat format) {
        PlanCalendarExporter calendarExporter = calendarExporters.get(format);
        if (calendarExporter != null) {
            calendarExporter.export(views, targetFile);
            return;
        }
        PlanExportDocument document = buildDocument(views);
        PlanExporter exporter = exporters.get(format);
        if (exporter != null) {
            exporter.export(document, targetFile);
            return;
        }
        PlanRenderer renderer = renderers.get(format);
        if (renderer == null) {
            throw new IllegalArgumentException("No exporter registered for format: " + format);
        }
        ParishIdentity parish = ParishIdentity.of(database.meta());
        String markdown = template.render(views, parish);
        renderer.render(
                new PlanRenderer.RenderedPlan(markdown, PlanBlocks.parse(markdown), parish.logoPng()),
                targetFile);
    }

    private PlanExportDocument buildDocument(List<PlanTemplateModel.Service> views) {
        List<PlanTemplateModel.Service> sorted = new ArrayList<>(views);
        sorted.sort(Comparator.comparing(PlanTemplateModel.Service::dateTime));

        DateTimeFormatter dateFormat = DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM);
        DateTimeFormatter dateTimeFormat = DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM, FormatStyle.SHORT);

        String title = Localization.lang("Altar server plan");
        String subtitle = sorted.isEmpty() ? "" : dateRange(sorted, dateFormat);

        List<PlanExportDocument.ServiceSection> sections = new ArrayList<>();
        Map<String, Long> countByServer = new LinkedHashMap<>();
        for (PlanTemplateModel.Service view : sorted) {
            String heading = view.dateTime().format(dateTimeFormat) + "  "
                    + view.label() + "  " + view.location();
            List<PlanExportDocument.AssignmentRow> rows = new ArrayList<>();
            for (PlanTemplateModel.Slot row : view.slots()) {
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

    private static String dateRange(List<PlanTemplateModel.Service> sorted, DateTimeFormatter dateFormat) {
        LocalDate from = sorted.getFirst().dateTime().toLocalDate();
        LocalDate to = sorted.getLast().dateTime().toLocalDate();
        return from.format(dateFormat) + " - " + to.format(dateFormat);
    }

}
