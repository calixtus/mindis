# ADR 009: The export template owns the document; the application only supplies values

Date: 2026-08-13
Status: accepted

## Context

Parishes want the handed-out plan to look like theirs: their own heading, their own wording, their
logo, their date format, their idea of what an unfilled slot should say. That is a template - and a
template is only worth having if the person editing it can actually decide those things. An
application that hands the template a finished sentence has not given control away, it has just
moved the string.

PDF makes this harder than it looks. A text template engine cannot produce a PDF; something has to
lay the document out. The usual answer - an HTML template rendered by an HTML-to-PDF engine - is
closed off by [ADR 008](008-third-party-licensing.md): Flying Saucer and openhtmltopdf are both
LGPL, and the only permissive full layout engine, Apache FOP, consumes XSL-FO, which no parish
volunteer will ever edit.

## Decision

**The application supplies data and control structures. The template writes the document.**

```
services + parish → Pebble (plan.md.peb) → Markdown
                  → commonmark AST → PlanBlock list
                    ├─ MARKDOWN : the template's own output, unchanged
                    ├─ TXT      : headings underlined, tables as padded columns
                    ├─ RTF      : bold runs, tab-separated rows, \pngblip images
                    └─ PDF      : PDFBox page painter (PdfPlanRenderer.Layout)
```

- **Values, not sentences.** `PlanTemplateModel` exposes `java.time` values, not formatted dates;
  `slot.assigned = false`, not a `-`; `service.location` and `service.typeLabel` as separate fields,
  not a composed heading. The template formats
  (`{{ service.dateTime | date("EEEE, d. MMMM") }}`), composes and words the document. The only
  wording the application contributes is under `labels`, and the `lang("...")` function that reaches
  any other translation - both there so a template *can* stay multilingual, not because it must.
- **Pebble** (BSD-3) for the template: `{% for %}`, `{% if %}/{% elseif %}/{% else %}`, `{% set %}`,
  filters, macros and `{% include %}`. Chosen over a logic-less engine because loops and
  conditionals are the point, and over FreeMarker and Thymeleaf because its expressions reach model
  attributes and registered filters and nothing else - the model is plain maps, lists, strings,
  numbers and `java.time` values, so there is no object in reach that does anything. Thymeleaf's
  OGNL, by contrast, reaches static methods, which turns a shared template file into code execution.
- **Markdown as the intermediate.** Readable and editable by the people who will edit it, already
  one of the output formats, and its element set - headings, paragraphs, bullets, tables, images,
  thematic breaks - is exactly what a text writer and a page painter can both express.
  **commonmark-java** (BSD-2) parses it; `PlanBlocks` flattens the tree into a small sealed
  `PlanBlock` list, so no renderer knows Markdown.
- **Two things stay the application's call.** Values are Markdown-escaped by default, because a
  server called `A|B` must not silently break out of a table cell; `{{ value | raw }}` opts out per
  value. And `{% include %}` resolves inside the template directory only
  (`TemplateDirectoryLoader`): a document layout is not a licence to read whatever file the process
  can reach.
- **The parish logo** is addressed as the image destination `mindis:logo`, which the PDF and RTF
  renderers resolve to the collection's own PNG. No other destination is fetched.
- **CSV stays outside the pipeline.** It is a data dump for a spreadsheet, not a document.
- **The bundled template wins on failure.** A user template that will not read, compile or render is
  reported and the built-in one is used - half a plan beats no plan on the morning it is needed.

## Consequences

- Everything the template says, it says to all four document formats at once, and there is one
  escaping rule instead of four.
- What Markdown cannot express, no format gets: column widths, margins, fonts, per-page headers. The
  PDF is a plain layout. The escape hatch, when someone needs it, is front matter at the top of the
  template plus renderer support - not a second template language.
- Rendered output has its blank-line runs collapsed. Markdown treats one blank line and three the
  same, so no document decision is taken away; it keeps the `.md` export, which is a file users hand
  out, from carrying the leftovers of `{% if %}` lines.
- Templates are files under `templates/plan.md.peb` in the data directory, with any includes beside
  them. There is no in-app editor or "reset to default" button yet; a user has to place the file.
- Two dependencies (Pebble + unbescape) are automatic modules and need `extraJavaModuleInfo`
  patches; commonmark is a real module.

## Alternatives rejected

- **A logic-less engine (Mustache).** Safe and tiny, and what this was built on first. Sections give
  loops and truthiness, but no conditionals worth the name, no filters, no formatting - so the
  application had to pre-compose headings and pre-format dates, which is exactly the control this
  decision hands back.
- **Thymeleaf.** Apache-2.0 and capable, but it renders HTML, which does not help the PDF at all
  without an HTML layout engine we are not allowed to ship; it is four more automatic modules; and
  its OGNL expressions reach static methods, so a shared template becomes remote code execution.
- **FreeMarker.** Permissive, single jar, more powerful than Pebble. Its `new` and `?api` builtins
  need locking down before a template file can be trusted, for capability the model does not need.
- **One template per format.** Four files to keep in sync, four escapers, and the PDF one would have
  to be a layout language rather than a document.
- **HTML template + HTML-to-PDF renderer.** The natural fit, and unavailable: LGPL (ADR 008).
- **XSL-FO + Apache FOP.** Permissive and print-grade, but unauthorable by the intended audience, and
  it drags Batik into the installer for a two-column list.
