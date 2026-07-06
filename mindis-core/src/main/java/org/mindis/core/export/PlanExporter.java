package org.mindis.core.export;

import java.nio.file.Path;

/**
 * Renders a {@link PlanExportDocument} to a specific file format.
 */
public interface PlanExporter {

    PlanExportFormat format();

    void export(PlanExportDocument document, Path targetFile);
}
