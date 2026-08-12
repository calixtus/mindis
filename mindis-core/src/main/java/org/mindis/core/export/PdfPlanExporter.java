package org.mindis.core.export;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDFont;
import org.apache.pdfbox.pdmodel.font.PDType0Font;

/// Lays the [PlanExportDocument] out as an A4 PDF with Apache PDFBox.
///
/// <p>PDFBox draws text, not documents: this exporter owns the page cursor,
/// the page breaks and the two-column word wrapping itself (see [Layout]).
/// Text is drawn in the bundled DejaVu Sans, embedded as a subset, so server
/// names outside Windows-1252 (the limit of the PDF standard-14 fonts) still
/// render as themselves.
final class PdfPlanExporter implements PlanExporter {

    private static final PDRectangle PAGE_SIZE = PDRectangle.A4;
    private static final float MARGIN = 56;
    private static final float TITLE_SIZE = 16;
    private static final float HEADING_SIZE = 11;
    private static final float BODY_SIZE = 10;
    /// Share of the content width given to the role column; the server name gets the rest.
    private static final float ROLE_COLUMN_SHARE = 1f / 3;

    @Override
    public PlanExportFormat format() {
        return PlanExportFormat.PDF;
    }

    @Override
    public void export(PlanExportDocument document, Path targetFile) {
        try (PDDocument pdf = new PDDocument()) {
            PDFont regular = loadFont(pdf, "font/DejaVuSans.ttf");
            PDFont bold = loadFont(pdf, "font/DejaVuSans-Bold.ttf");

            try (Layout layout = new Layout(pdf, regular, bold)) {
                layout.paragraph(document.title(), bold, TITLE_SIZE);
                layout.paragraph(document.subtitle(), regular, BODY_SIZE);
                layout.gap(BODY_SIZE);

                for (PlanExportDocument.ServiceSection section : document.services()) {
                    layout.paragraph(section.heading(), bold, HEADING_SIZE);
                    for (PlanExportDocument.AssignmentRow assignment : section.assignments()) {
                        layout.row(assignment.role(), assignment.serverName());
                    }
                    layout.gap(BODY_SIZE);
                }

                layout.paragraph(document.summaryHeading(), bold, HEADING_SIZE);
                for (PlanExportDocument.SummaryRow row : document.summary()) {
                    layout.row(row.serverName(), String.valueOf(row.count()));
                }
            }

            try (OutputStream out = Files.newOutputStream(targetFile)) {
                pdf.save(out);
            }
        } catch (IOException e) {
            throw new UncheckedIOException("Could not write PDF: " + targetFile, e);
        }
    }

    private static PDFont loadFont(PDDocument pdf, String resource) throws IOException {
        try (InputStream in = PdfPlanExporter.class.getResourceAsStream(resource)) {
            if (in == null) {
                throw new IllegalStateException("Bundled PDF font is missing: " + resource);
            }
            return PDType0Font.load(pdf, in);
        }
    }

    /// Text cursor over a growing list of pages: it appends lines top-down and
    /// starts a new page whenever the next line would cross the bottom margin.
    private static final class Layout implements AutoCloseable {

        private final PDDocument pdf;
        private final PDFont regular;
        private final PDFont bold;
        private PDPageContentStream stream;
        private float cursorY;

        private Layout(PDDocument pdf, PDFont regular, PDFont bold) throws IOException {
            this.pdf = pdf;
            this.regular = regular;
            this.bold = bold;
            this.stream = newPage();
        }

        /// Draws `text` wrapped across the full content width.
        private void paragraph(String text, PDFont font, float size) throws IOException {
            for (String line : wrap(text, font, size, contentWidth())) {
                drawLine(line, font, size, MARGIN);
                cursorY -= leading(size);
            }
        }

        /// Draws one assignment row: role in the left column, server in the right one.
        private void row(String left, String right) throws IOException {
            float roleWidth = contentWidth() * ROLE_COLUMN_SHARE;
            List<String> leftLines = wrap(left, regular, BODY_SIZE, roleWidth);
            List<String> rightLines = wrap(right, regular, BODY_SIZE, contentWidth() - roleWidth);
            int lineCount = Math.max(leftLines.size(), rightLines.size());

            // Keep the whole row on one page so a role never ends up separated
            // from the server assigned to it.
            requireSpace(lineCount * leading(BODY_SIZE));
            float rowTop = cursorY;
            drawColumn(leftLines, MARGIN, rowTop);
            drawColumn(rightLines, MARGIN + roleWidth, rowTop);
            cursorY = rowTop - lineCount * leading(BODY_SIZE);
        }

        private void drawColumn(List<String> lines, float x, float top) throws IOException {
            float y = top;
            for (String line : lines) {
                cursorY = y;
                drawLine(line, regular, BODY_SIZE, x);
                y -= leading(BODY_SIZE);
            }
        }

        private void gap(float size) {
            cursorY -= leading(size);
        }

        @Override
        public void close() throws IOException {
            stream.close();
        }

        private void drawLine(String text, PDFont font, float size, float x) throws IOException {
            requireSpace(leading(size));
            if (!text.isEmpty()) {
                stream.beginText();
                stream.setFont(font, size);
                stream.newLineAtOffset(x, cursorY);
                stream.showText(text);
                stream.endText();
            }
        }

        /// Starts a new page unless `height` still fits above the bottom margin.
        private void requireSpace(float height) throws IOException {
            if (cursorY - height < MARGIN) {
                stream.close();
                stream = newPage();
            }
        }

        private PDPageContentStream newPage() throws IOException {
            PDPage page = new PDPage(PAGE_SIZE);
            pdf.addPage(page);
            cursorY = PAGE_SIZE.getHeight() - MARGIN;
            return new PDPageContentStream(pdf, page);
        }

        private static float contentWidth() {
            return PAGE_SIZE.getWidth() - 2 * MARGIN;
        }

        private static float leading(float fontSize) {
            return fontSize * 1.35f;
        }

        /// Breaks `text` into lines no wider than `maxWidth`, splitting on
        /// spaces and, for a single word that is too long on its own, between
        /// characters.
        private static List<String> wrap(String text, PDFont font, float size, float maxWidth)
                throws IOException {
            String encodable = encodable(text, font);
            List<String> lines = new ArrayList<>();
            StringBuilder line = new StringBuilder();
            for (String word : encodable.split(" ", -1)) {
                String candidate = line.isEmpty() ? word : line + " " + word;
                if (line.isEmpty() || width(candidate, font, size) <= maxWidth) {
                    line.setLength(0);
                    line.append(candidate);
                } else {
                    lines.add(line.toString());
                    line.setLength(0);
                    line.append(word);
                }
                while (width(line.toString(), font, size) > maxWidth && line.length() > 1) {
                    int fitting = fittingPrefix(line.toString(), font, size, maxWidth);
                    lines.add(line.substring(0, fitting));
                    line.delete(0, fitting);
                }
            }
            lines.add(line.toString());
            return lines;
        }

        private static int fittingPrefix(String text, PDFont font, float size, float maxWidth)
                throws IOException {
            int fitting = 1;
            while (fitting < text.length() && width(text.substring(0, fitting + 1), font, size) <= maxWidth) {
                fitting++;
            }
            return fitting;
        }

        private static float width(String text, PDFont font, float size) throws IOException {
            return font.getStringWidth(text) / 1000 * size;
        }

        /// Replaces characters the font cannot draw - PDFBox rejects those
        /// outright - so one exotic name never fails the whole export.
        private static String encodable(String text, PDFont font) throws IOException {
            String printable = text.replaceAll("\\p{Cntrl}", " ");
            try {
                font.getStringWidth(printable);
                return printable;
            } catch (IllegalArgumentException e) {
                StringBuilder cleaned = new StringBuilder(printable.length());
                printable.codePoints().forEach(codePoint -> {
                    String character = Character.toString(codePoint);
                    try {
                        font.getStringWidth(character);
                        cleaned.append(character);
                    } catch (IOException | IllegalArgumentException unsupported) {
                        cleaned.append('?');
                    }
                });
                return cleaned.toString();
            }
        }
    }
}
