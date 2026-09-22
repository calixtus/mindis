package org.mindis.core.export;

import java.nio.file.Path;
import java.util.List;

import org.jspecify.annotations.Nullable;

/// Draws a templated plan into one file format.
///
/// <p>Where [PlanExporter] lays out the structured [PlanExportDocument] - the
/// path CSV takes, because a spreadsheet wants columns, not a document - a
/// renderer draws the [PlanBlock]s the user's template produced, and so shares
/// its layout with every other renderer.
interface PlanRenderer {

    PlanExportFormat format();

    void render(RenderedPlan plan, Path targetFile);

    /// One templated plan: the Markdown as the template produced it, the same
    /// document as blocks, and the collection's logo for the
    /// [PlanTemplate#LOGO_DESTINATION] image (null when the collection has
    /// none, in which case a template that references it renders its alt text).
    record RenderedPlan(String markdown, List<PlanBlock> blocks, byte @Nullable [] logoPng) {

        public RenderedPlan {
            blocks = List.copyOf(blocks);
        }
    }
}
