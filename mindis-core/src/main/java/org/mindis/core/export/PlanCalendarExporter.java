package org.mindis.core.export;

import java.nio.file.Path;
import java.util.List;

/// An exporter that needs the services themselves rather than a document made out of
/// them.
///
/// The other two shapes in this package both throw away what a calendar lives on:
/// [PlanExporter] takes a [PlanExportDocument], whose headings are already
/// prose with no date or duration behind them, and [PlanRenderer] takes the
/// template's rendered Markdown. A calendar needs the start, the end and the place as
/// values, so it reads [PlanTemplateModel.Service] directly.
interface PlanCalendarExporter {

    PlanExportFormat format();

    void export(List<PlanTemplateModel.Service> services, Path targetFile);
}
