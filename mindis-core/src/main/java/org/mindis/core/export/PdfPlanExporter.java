package org.mindis.core.export;

import java.io.IOException;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;

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

final class PdfPlanExporter implements PlanExporter {

    @Override
    public PlanExportFormat format() {
        return PlanExportFormat.PDF;
    }

    @Override
    public void export(PlanExportDocument document, Path targetFile) {
        Font titleFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 16);
        Font headingFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 11);
        Font bodyFont = FontFactory.getFont(FontFactory.HELVETICA, 10);

        Document pdf = new Document(PageSize.A4);
        try (OutputStream out = Files.newOutputStream(targetFile)) {
            PdfWriter.getInstance(pdf, out);
            pdf.open();

            pdf.add(new Paragraph(document.title(), titleFont));
            pdf.add(new Paragraph(document.subtitle(), bodyFont));
            pdf.add(new Paragraph(" "));

            for (PlanExportDocument.ServiceSection section : document.services()) {
                pdf.add(new Paragraph(section.heading(), headingFont));

                PdfPTable table = new PdfPTable(new float[] {1, 2});
                table.setWidthPercentage(100);
                table.setHorizontalAlignment(Element.ALIGN_LEFT);
                for (PlanExportDocument.AssignmentRow assignment : section.assignments()) {
                    table.addCell(plainCell(assignment.role(), bodyFont));
                    table.addCell(plainCell(assignment.serverName(), bodyFont));
                }
                pdf.add(table);
                pdf.add(new Paragraph(" "));
            }

            pdf.add(new Paragraph(document.summaryHeading(), headingFont));
            PdfPTable summary = new PdfPTable(new float[] {2, 1});
            summary.setWidthPercentage(100);
            summary.setHorizontalAlignment(Element.ALIGN_LEFT);
            for (PlanExportDocument.SummaryRow row : document.summary()) {
                summary.addCell(plainCell(row.serverName(), bodyFont));
                summary.addCell(plainCell(String.valueOf(row.count()), bodyFont));
            }
            pdf.add(summary);

            pdf.close();
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
