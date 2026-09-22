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
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
import org.apache.pdfbox.util.Matrix;

import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/// Lays the templated plan out as an A4 PDF with Apache PDFBox.
///
/// <p>PDFBox draws text, not documents: this renderer owns the page cursor,
/// the page breaks, the column widths and the word wrapping itself (see
/// [Layout]). Text is drawn in the bundled DejaVu Sans, embedded as a subset,
/// so names outside Windows-1252 (the limit of the PDF standard-14 fonts)
/// still render as themselves. Italics are sheared rather than swapped to an
/// oblique font, which keeps a third font file out of the installer.
final class PdfPlanRenderer implements PlanRenderer {

    private static final PDRectangle PAGE_SIZE = PDRectangle.A4;
    private static final float MARGIN = 56;
    private static final float BODY_SIZE = 10;
    private static final float[] HEADING_SIZES = {16, 11, 10, 10};
    /// Logo box; the image is scaled to fit inside it, keeping its ratio.
    private static final float MAX_IMAGE_WIDTH = 160;
    private static final float MAX_IMAGE_HEIGHT = 70;
    /// Share of the font size that sits above the baseline, near enough for
    /// DejaVu Sans, which reports an ascent of 0.76 em.
    private static final float ASCENT_RATIO = 0.8F;

    private static final Logger LOGGER = LoggerFactory.getLogger(PdfPlanRenderer.class);

    @Override
    public PlanExportFormat format() {
        return PlanExportFormat.PDF;
    }

    @Override
    public void render(RenderedPlan plan, Path targetFile) {
        try (PDDocument pdf = new PDDocument()) {
            PDFont regular = loadFont(pdf, "font/DejaVuSans.ttf");
            PDFont bold = loadFont(pdf, "font/DejaVuSans-Bold.ttf");

            try (Layout layout = new Layout(pdf, regular, bold)) {
                for (PlanBlock block : plan.blocks()) {
                    switch (block) {
                        case PlanBlock.Heading heading -> layout.heading(heading);
                        case PlanBlock.Paragraph paragraph -> layout.paragraph(paragraph.spans());
                        case PlanBlock.BulletItem item -> layout.bullet(item);
                        case PlanBlock.Table table -> layout.table(table);
                        case PlanBlock.Image image -> layout.image(image, plan.logoPng());
                        case PlanBlock.Break ignored -> layout.pageBreak();
                    }
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
        try (InputStream in = PdfPlanRenderer.class.getResourceAsStream(resource)) {
            if (in == null) {
                throw new IllegalStateException("Bundled PDF font is missing: " + resource);
            }
            return PDType0Font.load(pdf, in);
        }
    }

    /// Text cursor over a growing list of pages: it appends blocks top-down and
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

        private void heading(PlanBlock.Heading heading) throws IOException {
            float size = HEADING_SIZES[Math.min(heading.level(), HEADING_SIZES.length) - 1];
            // Air above a heading, but not at the top of a page.
            if (cursorY < PAGE_SIZE.getHeight() - MARGIN) {
                gap(size * 0.5f);
            }
            List<PlanBlock.Span> spans = heading.spans().stream()
                    .map(span -> new PlanBlock.Span(span.text(), true, span.italic()))
                    .toList();
            drawSpans(spans, size, MARGIN, contentWidth());
            gap(size * 0.4f);
        }

        private void paragraph(List<PlanBlock.Span> spans) throws IOException {
            drawSpans(spans, BODY_SIZE, MARGIN, contentWidth());
            gap(BODY_SIZE * 0.4f);
        }

        private void bullet(PlanBlock.BulletItem item) throws IOException {
            float indent = MARGIN + (item.depth() - 1) * BODY_SIZE;
            List<PlanBlock.Span> spans = new ArrayList<>();
            spans.add(PlanBlock.Span.plain("•  "));
            spans.addAll(item.spans());
            drawSpans(spans, BODY_SIZE, indent, contentWidth() - (indent - MARGIN));
        }

        /// Columns are as wide as their widest cell, scaled down proportionally
        /// when the natural widths do not fit the page.
        private void table(PlanBlock.Table table) throws IOException {
            List<List<String>> rows = new ArrayList<>();
            if (!table.headers().isEmpty()) {
                rows.add(table.headers());
            }
            rows.addAll(table.rows());
            if (rows.isEmpty()) {
                return;
            }

            float[] widths = columnWidths(rows);
            boolean firstRowIsHeader = !table.headers().isEmpty();
            for (int index = 0; index < rows.size(); index++) {
                drawRow(rows.get(index), widths, firstRowIsHeader && index == 0);
            }
            gap(BODY_SIZE * 0.4f);
        }

        private float[] columnWidths(List<List<String>> rows) throws IOException {
            int columns = rows.stream().mapToInt(List::size).max().orElse(0);
            float[] widths = new float[columns];
            for (List<String> row : rows) {
                for (int column = 0; column < row.size(); column++) {
                    widths[column] = Math.max(widths[column], width(row.get(column), regular, BODY_SIZE));
                }
            }
            float padding = BODY_SIZE;
            float total = 0;
            for (int column = 0; column < columns; column++) {
                widths[column] += padding;
                total += widths[column];
            }
            if (total > contentWidth() && total > 0) {
                float scale = contentWidth() / total;
                for (int column = 0; column < columns; column++) {
                    widths[column] *= scale;
                }
            }
            return widths;
        }

        private void drawRow(List<String> cells, float[] widths, boolean header) throws IOException {
            List<List<String>> wrapped = new ArrayList<>();
            int lineCount = 1;
            for (int column = 0; column < cells.size(); column++) {
                List<String> lines = wrap(cells.get(column), header ? bold : regular, BODY_SIZE, widths[column]);
                wrapped.add(lines);
                lineCount = Math.max(lineCount, lines.size());
            }

            // Keep a row on one page: a role must not be separated from the
            // server assigned to it.
            requireSpace(lineCount * leading(BODY_SIZE));
            float rowTop = cursorY;
            float x = MARGIN;
            for (int column = 0; column < wrapped.size(); column++) {
                float y = rowTop;
                for (String line : wrapped.get(column)) {
                    cursorY = y;
                    drawText(line, header ? bold : regular, BODY_SIZE, x, false);
                    y -= leading(BODY_SIZE);
                }
                x += widths[column];
            }
            cursorY = rowTop - lineCount * leading(BODY_SIZE);
        }

        private void image(PlanBlock.Image image, byte @Nullable [] logoPng) throws IOException {
            if (!PlanTemplate.LOGO_DESTINATION.equals(image.destination()) || logoPng == null) {
                if (!image.alt().isEmpty()) {
                    paragraph(List.of(PlanBlock.Span.plain(image.alt())));
                }
                return;
            }
            PDImageXObject drawn;
            try {
                drawn = PDImageXObject.createFromByteArray(pdf, logoPng, "logo");
            } catch (IOException e) {
                LOGGER.warn("Collection logo is not a readable image, exporting without it", e);
                return;
            }
            float scale = Math.min(MAX_IMAGE_WIDTH / drawn.getWidth(), MAX_IMAGE_HEIGHT / drawn.getHeight());
            scale = Math.min(scale, 1);
            float width = drawn.getWidth() * scale;
            float height = drawn.getHeight() * scale;

            requireSpace(height);
            cursorY -= height;
            stream.drawImage(drawn, MARGIN, cursorY, width, height);
            gap(BODY_SIZE * 0.6f);
        }

        private void pageBreak() throws IOException {
            stream.close();
            stream = newPage();
        }

        private void gap(float height) {
            cursorY -= height;
        }

        @Override
        public void close() throws IOException {
            stream.close();
        }

        /// Draws a run of spans as wrapped lines, switching font per span and
        /// breaking between words.
        private void drawSpans(List<PlanBlock.Span> spans, float size, float x, float maxWidth)
                throws IOException {
            requireSpace(leading(size));
            float cursorX = x;
            boolean anythingDrawn = false;
            for (PlanBlock.Span span : spans) {
                PDFont font = span.bold() ? bold : regular;
                for (String word : words(encodable(span.text(), font))) {
                    float wordWidth = width(word, font, size);
                    boolean lineStart = cursorX <= x + 0.01f;
                    if (!lineStart && cursorX + wordWidth > x + maxWidth) {
                        cursorY -= leading(size);
                        requireSpace(leading(size));
                        cursorX = x;
                        if (word.isBlank()) {
                            continue;
                        }
                    }
                    if (lineStart && word.isBlank() && !anythingDrawn) {
                        continue;
                    }
                    drawText(word, font, size, cursorX, span.italic());
                    cursorX += wordWidth;
                    anythingDrawn = true;
                }
            }
            cursorY -= leading(size);
        }

        /// Words with their trailing space kept, so spacing survives wrapping.
        private static List<String> words(String text) {
            List<String> words = new ArrayList<>();
            StringBuilder word = new StringBuilder();
            for (int i = 0; i < text.length(); i++) {
                char c = text.charAt(i);
                word.append(c);
                if (c == ' ') {
                    words.add(word.toString());
                    word.setLength(0);
                }
            }
            if (!word.isEmpty()) {
                words.add(word.toString());
            }
            return words;
        }

        private void drawText(String text, PDFont font, float size, float x, boolean italic)
                throws IOException {
            String drawable = encodable(text, font);
            if (drawable.isBlank()) {
                return;
            }
            // cursorY is the top of the line, so the baseline sits one ascent
            // lower; otherwise the first line of a block would climb into
            // whatever is above it - an image, most visibly.
            float baseline = cursorY - size * ASCENT_RATIO;
            stream.beginText();
            stream.setFont(font, size);
            if (italic) {
                // Shear instead of an oblique font file: 0.21 is the slant the
                // standard oblique variants use.
                stream.setTextMatrix(new Matrix(1, 0, 0.21f, 1, x, baseline));
            } else {
                stream.newLineAtOffset(x, baseline);
            }
            stream.showText(drawable);
            stream.endText();
        }

        /// Starts a new page unless `height` still fits above the bottom margin.
        private void requireSpace(float height) throws IOException {
            if (cursorY - height < MARGIN) {
                pageBreak();
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
            String drawable = encodable(text, font);
            List<String> lines = new ArrayList<>();
            StringBuilder line = new StringBuilder();
            for (String word : drawable.split(" ", -1)) {
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
            return font.getStringWidth(encodable(text, font)) / 1000 * size;
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
