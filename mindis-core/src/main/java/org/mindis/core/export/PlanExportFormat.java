package org.mindis.core.export;

import java.util.Locale;

/// File formats [PlanExportService] can render an accepted plan into.
public enum PlanExportFormat {
    PDF("pdf", "PDF"),
    CSV("csv", "CSV"),
    TXT("txt", "TXT"),
    RTF("rtf", "RTF"),
    MARKDOWN("md", "Markdown"),
    /// iCalendar (RFC 5545) - one event per service, for a calendar application.
    ICS("ics", "iCalendar");

    private final String extension;
    private final String label;

    PlanExportFormat(String extension, String label) {
        this.extension = extension;
        this.label = label;
    }

    public String extension() {
        return extension;
    }

    /// Name of the format for a file dialog's filter. Deliberately untranslated:
    /// these are format names, and a user looking for a PDF looks for "PDF".
    public String label() {
        return label;
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
