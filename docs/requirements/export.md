# Plan Export

Turning a plan — live or archived — into a document to hand out or print.

## Requirements

### Export a plan as a document
`req~export-plan~1`

The user exports the plan as a document listing every service with its role slots and the assigned
server, plus a per-server duty count summary.

Covers:
- feat~plan-distribution~1

### Several output formats
`req~export-formats~1`

The plan can be written as PDF, CSV, plain text, RTF or Markdown; the user picks the format when
saving.

Covers:
- feat~plan-distribution~1

### Exports are localized
`req~export-localized~1`

Headings, column headers, dates and service types in the exported document use the application
language and locale conventions.

Covers:
- feat~plan-distribution~1
- feat~multilingual-desktop-app~1

### Archived plans export faithfully
`req~export-archived~1`

An archived plan exports with the names it was archived with, even if those servers or roles no
longer exist.

Covers:
- feat~plan-distribution~1
- feat~plan-history~1

### Export dialog remembers its place
`req~export-dialog~1`

The save dialog opens in the last used export directory, preselects the filter for the format the
user chose, and derives the actual format from the chosen filter and typed file name.

Covers:
- feat~plan-distribution~1

## Design

### Format-agnostic document
`dsn~plan-export-document~1`

`PlanExportDocument` is the fully localized, format-agnostic rendering model: `title`, `subtitle`
(the covered date range), `ColumnHeaders`, `ServiceSection`s (a heading plus `AssignmentRow(role,
serverName)`), a summary heading and `SummaryRow(serverName, count)`s. Each `PlanExporter` only lays
this out — it knows nothing about localization, servers or services.

Covers:
- req~export-plan~1
- req~export-localized~1

### One builder, two sources
`dsn~plan-export-service~1`

`PlanExportService` builds the document once and dispatches it to the exporter registered for the
requested `PlanExportFormat`. `exportLive(services, …)` resolves each slot's role and server against
the current roster (an unresolvable id falls back to the raw id, an open slot renders as `-`);
`exportArchived(services, …)` reads the display names straight off the self-contained snapshot.
Services are sorted chronologically, section headings are
`<localized date-time>  <service type>  <location>`, and the summary lists servers by duty count
descending. Unit-tested by `PlanExportServiceTest`.

Covers:
- req~export-plan~1
- req~export-archived~1

### Exporters
`dsn~plan-exporters~1`

`PlanExportFormat` maps each format to its extension: `PDF`/pdf, `CSV`/csv, `TXT`/txt, `RTF`/rtf,
`MARKDOWN`/md, with `fromExtension` for the reverse lookup. One `PlanExporter` implementation per
value (`PdfPlanExporter` on OpenPDF, `CsvPlanExporter`, `TxtPlanExporter`, `RtfPlanExporter`,
`MarkdownPlanExporter`), all registered into an `EnumMap` in the service constructor; an unregistered
format is a programming error and fails fast.

Covers:
- req~export-formats~1

### Localized date and enum rendering
`dsn~export-localization~1`

Dates use `DateTimeFormatter.ofLocalizedDate(MEDIUM)` and headings
`ofLocalizedDateTime(MEDIUM, SHORT)` under the active locale; service types render through
`EnumDisplay`; all fixed strings go through `Localization.lang` full-text keys (see
[ui.md](ui.md)).

Covers:
- req~export-localized~1

### Shared save dialog
`dsn~plan-export-chooser~1`

`PlanExportChooser` is the single "save plan as…" setup shared by `ServicesModule` and
`ArchivedPlansDialog`. It offers one extension filter per format, preselects the filter matching the
format the user triggered the export with, starts in `MinDisPreferences.lastExportDirectory` when
that still exists, and stores the chosen directory back. The final format is resolved from the typed
file name *and* the selected filter, so a format chosen in the UI is not silently overridden by the
initial filter.

Covers:
- req~export-dialog~1
