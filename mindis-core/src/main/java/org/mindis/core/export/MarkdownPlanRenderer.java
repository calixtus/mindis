package org.mindis.core.export;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/// Writes what the template produced, unchanged - the Markdown export is the
/// template's own output, which is also what every other renderer draws.
final class MarkdownPlanRenderer implements PlanRenderer {

    @Override
    public PlanExportFormat format() {
        return PlanExportFormat.MARKDOWN;
    }

    @Override
    public void render(RenderedPlan plan, Path targetFile) {
        try {
            Files.writeString(targetFile, plan.markdown(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("Could not write Markdown: " + targetFile, e);
        }
    }
}
