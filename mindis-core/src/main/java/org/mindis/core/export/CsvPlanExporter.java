package org.mindis.core.export;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

final class CsvPlanExporter implements PlanExporter {

    @Override
    public PlanExportFormat format() {
        return PlanExportFormat.CSV;
    }

    @Override
    public void export(PlanExportDocument document, Path targetFile) {
        try (Writer writer = Files.newBufferedWriter(targetFile, StandardCharsets.UTF_8)) {
            PlanExportDocument.ColumnHeaders headers = document.headers();
            writeRow(writer, headers.service(), headers.role(), headers.server());
            for (PlanExportDocument.ServiceSection section : document.services()) {
                for (PlanExportDocument.AssignmentRow assignment : section.assignments()) {
                    writeRow(writer, section.heading(), assignment.role(), assignment.serverName());
                }
            }
            writer.write("\r\n");
            writeRow(writer, document.summaryHeading(), headers.count());
            for (PlanExportDocument.SummaryRow row : document.summary()) {
                writeRow(writer, row.serverName(), String.valueOf(row.count()));
            }
        } catch (IOException e) {
            throw new UncheckedIOException("Could not write CSV: " + targetFile, e);
        }
    }

    private static void writeRow(Writer writer, String... fields) throws IOException {
        for (int i = 0; i < fields.length; i++) {
            if (i > 0) {
                writer.write(",");
            }
            writer.write(escape(fields[i]));
        }
        writer.write("\r\n");
    }

    private static String escape(String value) {
        if (value.contains(",") || value.contains("\"") || value.contains("\n") || value.contains("\r")) {
            return "\"" + value.replace("\"", "\"\"") + "\"";
        }
        return value;
    }
}
