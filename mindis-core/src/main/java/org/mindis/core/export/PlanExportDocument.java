package org.mindis.core.export;

import java.util.List;

/// Format-agnostic, fully-localized rendering of an {@link
/// org.mindis.core.planning.AcceptedPlan}. {@link PlanExportService} builds
/// this once from the plan and repositories; each {@link PlanExporter} only
/// has to lay it out, without knowing about localization, servers or
/// services.
public record PlanExportDocument(
        String title,
        String subtitle,
        ColumnHeaders headers,
        List<ServiceSection> services,
        String summaryHeading,
        List<SummaryRow> summary) {

    public PlanExportDocument {
        services = List.copyOf(services);
        summary = List.copyOf(summary);
    }

    public record ColumnHeaders(
            String service,
            String role,
            String server,
            String count) {
    }

    public record ServiceSection(String heading, List<AssignmentRow> assignments) {
        public ServiceSection {
            assignments = List.copyOf(assignments);
        }
    }

    public record AssignmentRow(String role, String serverName) {
    }

    public record SummaryRow(String serverName, long count) {
    }
}
