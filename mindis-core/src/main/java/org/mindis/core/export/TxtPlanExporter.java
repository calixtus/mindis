package org.mindis.core.export;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

final class TxtPlanExporter implements PlanExporter {

    @Override
    public PlanExportFormat format() {
        return PlanExportFormat.TXT;
    }

    @Override
    public void export(PlanExportDocument document, Path targetFile) {
        try (Writer writer = Files.newBufferedWriter(targetFile, StandardCharsets.UTF_8)) {
            writer.write(document.title());
            writer.write("\n");
            writer.write(document.subtitle());
            writer.write("\n\n");

            for (PlanExportDocument.ServiceSection section : document.services()) {
                writer.write(section.heading());
                writer.write("\n");
                for (PlanExportDocument.AssignmentRow assignment : section.assignments()) {
                    writer.write("  " + assignment.role() + ": " + assignment.serverName() + "\n");
                }
                writer.write("\n");
            }

            writer.write(document.summaryHeading());
            writer.write("\n");
            for (PlanExportDocument.SummaryRow row : document.summary()) {
                writer.write("  " + row.serverName() + ": " + row.count() + "\n");
            }
        } catch (IOException e) {
            throw new UncheckedIOException("Could not write TXT: " + targetFile, e);
        }
    }
}
