package org.mindis.core.export;

import java.util.Locale;

/// File formats {@link PlanExportService} can render an accepted plan into.
public enum PlanExportFormat {
    PDF("pdf"),
    CSV("csv"),
    TXT("txt"),
    RTF("rtf"),
    MARKDOWN("md");

    private final String extension;

    PlanExportFormat(String extension) {
        this.extension = extension;
    }

    public String extension() {
        return extension;
    }

    public static PlanExportFormat fromExtension(String extension) {
        String normalized = extension.toLowerCase(Locale.ROOT);
        for (PlanExportFormat format : values()) {
            if (format.extension.equals(normalized)) {
                return format;
            }
        }
        throw new IllegalArgumentException("Unsupported export extension: " + extension);
    }
}
