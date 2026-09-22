package org.mindis.core.export;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/// Plain text: headings underlined, tables laid out as space-padded columns,
/// images reduced to their alt text.
final class TextPlanRenderer implements PlanRenderer {

    private static final String INDENT = "  ";

    @Override
    public PlanExportFormat format() {
        return PlanExportFormat.TXT;
    }

    @Override
    public void render(RenderedPlan plan, Path targetFile) {
        try (Writer writer = Files.newBufferedWriter(targetFile, StandardCharsets.UTF_8)) {
            for (PlanBlock block : plan.blocks()) {
                switch (block) {
                    case PlanBlock.Heading heading -> writeHeading(writer, heading);
                    case PlanBlock.Paragraph paragraph -> writer.write(text(paragraph.spans()) + "\n\n");
                    case PlanBlock.BulletItem item ->
                            writer.write(INDENT.repeat(item.depth()) + "- " + text(item.spans()) + "\n");
                    case PlanBlock.Table table -> writeTable(writer, table);
                    case PlanBlock.Image image -> writeImage(writer, image);
                    case PlanBlock.Break ignored -> writer.write("\n");
                }
            }
        } catch (IOException e) {
            throw new UncheckedIOException("Could not write TXT: " + targetFile, e);
        }
    }

    private static void writeHeading(Writer writer, PlanBlock.Heading heading) throws IOException {
        String text = text(heading.spans());
        writer.write(text + "\n");
        if (heading.level() <= 2 && !text.isEmpty()) {
            writer.write((heading.level() == 1 ? "=" : "-").repeat(text.length()) + "\n");
        }
        writer.write("\n");
    }

    private static void writeImage(Writer writer, PlanBlock.Image image) throws IOException {
        if (!image.alt().isEmpty()) {
            writer.write(image.alt() + "\n\n");
        }
    }

    /// Columns padded to the widest cell, so a plan stays readable in a
    /// monospaced terminal or a printout.
    private static void writeTable(Writer writer, PlanBlock.Table table) throws IOException {
        List<List<String>> rows = new ArrayList<>();
        if (!table.headers().isEmpty()) {
            rows.add(table.headers());
        }
        rows.addAll(table.rows());
        if (rows.isEmpty()) {
            return;
        }

        int columns = rows.stream().mapToInt(List::size).max().orElse(0);
        int[] widths = new int[columns];
        for (List<String> row : rows) {
            for (int column = 0; column < row.size(); column++) {
                widths[column] = Math.max(widths[column], row.get(column).length());
            }
        }

        for (List<String> row : rows) {
            StringBuilder line = new StringBuilder(INDENT);
            for (int column = 0; column < row.size(); column++) {
                String cell = row.get(column);
                line.append(cell);
                if (column < row.size() - 1) {
                    line.append(" ".repeat(widths[column] - cell.length() + 2));
                }
            }
            writer.write(line.toString().stripTrailing() + "\n");
        }
        writer.write("\n");
    }

    private static String text(List<PlanBlock.Span> spans) {
        StringBuilder text = new StringBuilder();
        spans.forEach(span -> text.append(span.text()));
        return text.toString();
    }
}
