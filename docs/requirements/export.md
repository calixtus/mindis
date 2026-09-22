# Plan Export

Turning a plan — live or archived — into a document to hand out or print.

Design decisions: [ADR 008 — permissively licensed dependencies](../adr/008-third-party-licensing.md)
(why PDF export runs on Apache PDFBox with a bundled font),
[ADR 009 — one Markdown template behind every exported document](../adr/009-export-templating.md).

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

### The exported document follows a template
`req~export-template~1`

The user decides what an exported plan says by editing one template, which applies to every document
format at once — PDF, plain text, RTF and Markdown all come out of it. The template receives the
plan as data (dates as dates, an unfilled slot as such) and composes the document itself, with
loops, conditionals, filters and includes; wording, date formats and headings are its decisions, not
the application's. A template that cannot be read or rendered falls back to the built-in one instead
of failing the export.

Covers:
- feat~plan-distribution~1

### The parish logo appears on the plan
`req~export-logo~1`

A plan exported from a collection that has a parish logo can carry that logo, as an image in the
PDF and the RTF and as its alt text in the plain-text formats. Where it sits is the template's
decision.

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
`MARKDOWN`/md, with `fromExtension` for the reverse lookup. CSV is written straight from the
structured document by a `PlanExporter` (`CsvPlanExporter`) — a spreadsheet wants columns, not a
laid-out document. Every other format is a `PlanRenderer` (`PdfPlanRenderer` on Apache PDFBox,
`TextPlanRenderer`, `RtfPlanRenderer`, `MarkdownPlanRenderer`) drawing the templated document. Both
kinds are registered into `EnumMap`s in the service constructor; a format with neither is a
programming error and fails fast.

Covers:
- req~export-formats~1

### One template, every document format
`dsn~plan-template~1`

`PlanTemplate` renders the plan into Markdown through a Pebble template: `templates/plan.md.peb` in
the data directory when the user has one, otherwise `plan.md.peb` bundled next to the class. A user
template that cannot be read, compiled or rendered is logged and the bundled one is used.

The Markdown is then parsed once (commonmark + the GFM tables extension) and flattened by
`PlanBlocks` into `PlanBlock`s — heading, paragraph, bullet, table, image, page break — which every
renderer draws. `MARKDOWN` writes the template's own output unchanged; the others draw the blocks,
so a template change lands in all of them at once. Blank-line runs in the rendered Markdown are
collapsed, which changes no block but keeps the leftovers of `{% if %}` lines out of the `.md`
export.

Covers:
- req~export-template~1
- req~export-formats~1

### What a template gets, and what it may not do
`dsn~plan-template-model~1`

`PlanTemplateModel` is plain maps, lists, strings, numbers and `java.time` values — `services[]`
with `dateTime`/`date`/`time`, `type`, `typeLabel`, `location`, `slotCount`, `openCount` and
`slots[]` (`role`, `roleName`, `serverName`, `assigned`); `servers[]` with duty counts; `range`;
`parish`; `generatedAt`; the `labels` the built-in template uses; and a `lang("...")` function for
any other translation. Nothing is pre-composed or pre-formatted: the template formats dates with
`| date(...)`, decides what an unfilled slot reads as, and writes its own headings.

Plain values are also the sandbox. Pebble expressions reach model attributes and registered filters
only, and no object in the model does anything, so a template cannot be turned into code. Two limits
are the application's: values are Markdown-escaped unless the template writes `{{ value | raw }}`,
and `TemplateDirectoryLoader` resolves `{% include %}` inside the template directory only — a name
that escapes it, or an absolute path, is refused.

Covers:
- req~export-template~1

### Parish logo in an export
`dsn~export-logo~1`

`ParishIdentity` reads the parish name and the Base64 PNG logo off `CollectionMeta` (the stock
`logoIcon` is deliberately ignored — it is an icon-font placeholder for the sidebar, not something to
print). A template places the logo by referencing the `mindis:logo` image destination, which
`PdfPlanRenderer` resolves to an embedded image scaled into a 160×70 pt box and `RtfPlanRenderer` to
a `\pngblip` picture; the text formats fall back to the alt text. A collection without a logo, or an
undecodable one, exports without it.

Covers:
- req~export-logo~1

### PDF page layout
`dsn~pdf-layout~1`

PDFBox draws text, not documents, so `PdfPlanRenderer.Layout` owns the page cursor: it appends the
blocks top-down on A4 with a 56 pt margin, starts a new page when the next line would cross the
bottom margin (or when the template asks for one with `---`), keeps a table row on one page so a
role is never separated from its server, and sizes table columns to their widest cell, scaled down
proportionally when they do not fit. Text is drawn in DejaVu Sans, bundled with the module and
embedded as a subset, because the PDF standard-14 fonts stop at Windows-1252 and would reject names
beyond it; a character the font cannot draw becomes `?` rather than failing the export. Italics are
sheared rather than drawn from an oblique font, which keeps a third font file out of the installer.
The library choice is [ADR 008](../adr/008-third-party-licensing.md).

Covers:
- req~export-formats~1
- req~export-localized~1

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
