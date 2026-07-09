package org.mindis.core.export;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/// Hand-rolled minimal RTF writer (title/heading bold text plus tab-separated
/// rows) - no external RTF library is pulled in for this.
final class RtfPlanExporter implements PlanExporter {

    @Override
    public PlanExportFormat format() {
        return PlanExportFormat.RTF;
    }

    @Override
    public void export(PlanExportDocument document, Path targetFile) {
        StringBuilder rtf = new StringBuilder();
        rtf.append("{\\rtf1\\ansi\\ansicpg1252\\deff0{\\fonttbl{\\f0\\fswiss Helvetica;}}\\f0\n");

        rtf.append("\\b\\fs32 ");
        appendEscaped(rtf, document.title());
        rtf.append("\\b0\\fs20\\par\n");
        appendEscaped(rtf, document.subtitle());
        rtf.append("\\par\n\\par\n");

        for (PlanExportDocument.ServiceSection section : document.services()) {
            rtf.append("\\b\\fs22 ");
            appendEscaped(rtf, section.heading());
            rtf.append("\\b0\\fs20\\par\n");
            for (PlanExportDocument.AssignmentRow assignment : section.assignments()) {
                appendEscaped(rtf, assignment.role());
                rtf.append("\\tab ");
                appendEscaped(rtf, assignment.serverName());
                rtf.append("\\par\n");
            }
            rtf.append("\\par\n");
        }

        rtf.append("\\b\\fs22 ");
        appendEscaped(rtf, document.summaryHeading());
        rtf.append("\\b0\\fs20\\par\n");
        for (PlanExportDocument.SummaryRow row : document.summary()) {
            appendEscaped(rtf, row.serverName());
            rtf.append("\\tab ");
            rtf.append(row.count());
            rtf.append("\\par\n");
        }

        rtf.append("}");

        try {
            Files.writeString(targetFile, rtf.toString(), StandardCharsets.US_ASCII);
        } catch (IOException e) {
            throw new UncheckedIOException("Could not write RTF: " + targetFile, e);
        }
    }

    private static void appendEscaped(StringBuilder out, String text) {
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '\\' || c == '{' || c == '}') {
                out.append('\\').append(c);
            } else if (c < 128) {
                out.append(c);
            } else {
                out.append("\\u").append((int) c).append('?');
            }
        }
    }
}
