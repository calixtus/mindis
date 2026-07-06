package org.mindis.core.export;

import jakarta.inject.Singleton;

import java.io.FileOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.time.format.DateTimeFormatter;
import java.time.format.FormatStyle;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.mindis.core.l10n.EnumDisplay;
import org.mindis.core.l10n.Localization;
import org.mindis.core.model.LiturgicalService;
import org.mindis.core.model.Server;
import org.mindis.core.persistence.ServerRepository;
import org.mindis.core.persistence.ServiceRepository;
import org.mindis.core.planning.AcceptedPlan;
import org.openpdf.text.Document;
import org.openpdf.text.DocumentException;
import org.openpdf.text.Element;
import org.openpdf.text.Font;
import org.openpdf.text.FontFactory;
import org.openpdf.text.PageSize;
import org.openpdf.text.Paragraph;
import org.openpdf.text.pdf.PdfPCell;
import org.openpdf.text.pdf.PdfPTable;
import org.openpdf.text.pdf.PdfWriter;

/**
 * Renders an {@link AcceptedPlan} as a printable PDF: services in
 * chronological order with their role assignments, followed by a per-server
 * summary. All labels honor the active application language (PLAN.md M5).
 */
@Singleton
public class PlanExportService {

    private final ServerRepository serverRepository;
    private final ServiceRepository serviceRepository;

    public PlanExportService(ServerRepository serverRepository, ServiceRepository serviceRepository) {
        this.serverRepository = serverRepository;
        this.serviceRepository = serviceRepository;
    }

    public void exportPdf(AcceptedPlan plan, Path targetFile) {
        Map<String, Server> serversById = new LinkedHashMap<>();
        serverRepository.findAll().forEach(server -> serversById.put(server.id(), server));
        Map<String, LiturgicalService> servicesById = new LinkedHashMap<>();
        serviceRepository.findAll().forEach(service -> servicesById.put(service.id(), service));

        DateTimeFormatter dateFormat = DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM);
        DateTimeFormatter dateTimeFormat = DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM, FormatStyle.SHORT);

        Font titleFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 16);
        Font headingFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 11);
        Font bodyFont = FontFactory.getFont(FontFactory.HELVETICA, 10);

        Document document = new Document(PageSize.A4);
        try (FileOutputStream out = new FileOutputStream(targetFile.toFile())) {
            PdfWriter.getInstance(document, out);
            document.open();

            document.add(new Paragraph(Localization.lang("Altar server plan"), titleFont));
            document.add(new Paragraph(
                    plan.from().format(dateFormat) + " - " + plan.toInclusive().format(dateFormat),
                    bodyFont));
            document.add(new Paragraph(" "));

            // Section 1: services with their assignments, chronological.
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

            for (Map.Entry<String, List<AcceptedPlan.PlannedAssignment>> entry : byService.entrySet()) {
                LiturgicalService service = servicesById.get(entry.getKey());
                String heading = service == null
                        ? entry.getKey()
                        : service.dateTime().format(dateTimeFormat) + "  "
                                + EnumDisplay.of(service.type()) + "  " + service.location();
                document.add(new Paragraph(heading, headingFont));

                PdfPTable table = new PdfPTable(new float[] {1, 2});
                table.setWidthPercentage(100);
                table.setHorizontalAlignment(Element.ALIGN_LEFT);
                for (AcceptedPlan.PlannedAssignment assignment : entry.getValue()) {
                    table.addCell(plainCell(EnumDisplay.of(assignment.role()), bodyFont));
                    Server server = assignment.serverId() == null ? null : serversById.get(assignment.serverId());
                    table.addCell(plainCell(server == null ? "-" : server.displayName(), bodyFont));
                }
                document.add(table);
                document.add(new Paragraph(" "));
            }

            // Section 2: per-server summary.
            document.add(new Paragraph(Localization.lang("Assignments per server"), headingFont));
            PdfPTable summary = new PdfPTable(new float[] {2, 1});
            summary.setWidthPercentage(100);
            summary.setHorizontalAlignment(Element.ALIGN_LEFT);
            Map<String, Long> countByServer = new LinkedHashMap<>();
            plan.assignments().forEach(assignment -> {
                if (assignment.serverId() != null) {
                    countByServer.merge(assignment.serverId(), 1L, Long::sum);
                }
            });
            countByServer.entrySet().stream()
                    .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                    .forEach(entry -> {
                        Server server = serversById.get(entry.getKey());
                        summary.addCell(plainCell(server == null ? entry.getKey() : server.displayName(), bodyFont));
                        summary.addCell(plainCell(String.valueOf(entry.getValue()), bodyFont));
                    });
            document.add(summary);

            document.close();
        } catch (IOException e) {
            throw new UncheckedIOException("Could not write PDF: " + targetFile, e);
        } catch (DocumentException e) {
            throw new IllegalStateException("Could not render PDF: " + targetFile, e);
        }
    }

    private static PdfPCell plainCell(String text, Font font) {
        PdfPCell cell = new PdfPCell(new Paragraph(text, font));
        cell.setBorder(PdfPCell.NO_BORDER);
        return cell;
    }

}
