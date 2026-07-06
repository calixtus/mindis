package org.mindis.core.export;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

final class MarkdownPlanExporter implements PlanExporter {

    @Override
    public PlanExportFormat format() {
        return PlanExportFormat.MARKDOWN;
    }

    @Override
    public void export(PlanExportDocument document, Path targetFile) {
        try (Writer writer = Files.newBufferedWriter(targetFile, StandardCharsets.UTF_8)) {
            PlanExportDocument.ColumnHeaders headers = document.headers();

            writer.write("# " + escape(document.title()) + "\n\n");
            writer.write("_" + escape(document.subtitle()) + "_\n\n");

            for (PlanExportDocument.ServiceSection section : document.services()) {
                writer.write("## " + escape(section.heading()) + "\n\n");
                writer.write("| " + headers.role() + " | " + headers.server() + " |\n");
                writer.write("| --- | --- |\n");
                for (PlanExportDocument.AssignmentRow assignment : section.assignments()) {
                    writer.write("| " + escape(assignment.role()) + " | " + escape(assignment.serverName()) + " |\n");
                }
                writer.write("\n");
            }

            writer.write("## " + escape(document.summaryHeading()) + "\n\n");
            writer.write("| " + headers.server() + " | " + headers.count() + " |\n");
            writer.write("| --- | --- |\n");
            for (PlanExportDocument.SummaryRow row : document.summary()) {
                writer.write("| " + escape(row.serverName()) + " | " + row.count() + " |\n");
            }
        } catch (IOException e) {
            throw new UncheckedIOException("Could not write Markdown: " + targetFile, e);
        }
    }

    private static String escape(String value) {
        return value.replace("|", "\\|");
    }
}
