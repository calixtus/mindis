# ADR 009: One Markdown template behind every exported document

Date: 2026-08-12
Status: accepted

## Context

Parishes want the handed-out plan to look like theirs: their own heading, their own wording, their
logo on top. That means a user-editable template. The obvious shape - one template per output format
- multiplies the work it takes to change anything: a parish that wants the logo moved would have to
edit the PDF template *and* the RTF one *and* the Markdown one, and each would need its own escaping
rules.

PDF makes the single-template idea harder than it looks. A text template engine cannot produce a PDF;
something has to lay the document out. The usual answer - write an HTML template and render it with
an HTML-to-PDF engine - is closed off by [ADR 008](008-third-party-licensing.md): Flying Saucer and
openhtmltopdf are both LGPL, and the only permissive full layout engine, Apache FOP, consumes XSL-FO,
which no parish volunteer will ever edit.

## Decision

**The template does not produce a file format. It produces a document, in Markdown, and every format
is drawn from that.**

```
PlanExportDocument → JMustache (plan.md.mustache) → Markdown
                   → commonmark AST → PlanBlock list
                     ├─ MARKDOWN : the template's own output, unchanged
                     ├─ TXT      : headings underlined, tables as padded columns
                     ├─ RTF      : bold runs, tab-separated rows, \pngblip images
                     └─ PDF      : PDFBox page painter (PdfPlanRenderer.Layout)
```

- **Markdown as the intermediate.** It is readable and editable by the people who will edit it, it
  is already one of the output formats, and its element set - headings, paragraphs, bullets, tables,
  images, thematic breaks - is exactly what a text writer and a page painter can both express.
- **JMustache** (BSD-3) for the template. Logic-less: a template cannot execute code, cannot reach
  the filesystem, cannot throw anything but a parse error. The export model is already fully
  localized and formatted, so a template needs placeholders and loops, nothing more.
- **commonmark-java** (BSD-2) to parse. `PlanBlocks` flattens its tree into a small sealed
  `PlanBlock` list, so no renderer knows Markdown and each is a switch over six cases.
- **The parish logo** is addressed by the template as the image destination `mindis:logo`, which the
  PDF and RTF renderers resolve to the collection's own PNG. Templates cannot reference arbitrary
  files or URLs: an export must not become a way to make the application read a path or open a
  connection someone else chose.
- **CSV stays outside the pipeline.** It is a data dump for a spreadsheet, not a document; running it
  through a document template would only make it worse.
- **The bundled template wins on failure.** A user template that will not read, compile or render is
  reported and the built-in one is used - half a plan beats no plan on the morning it is needed.

## Consequences

- Everything the template can say, it says to all four formats at once, and there is one escaping
  rule instead of four.
- What Markdown cannot express, no format gets: column widths, margins, fonts, per-page headers. The
  PDF is the plain layout it was. A future escape hatch is front matter at the top of the template,
  parsed by hand; not built until someone asks.
- Templates are files on disk under `templates/plan.md.mustache` in the data directory. There is no
  in-app editor or "reset to default" button yet; a user has to place the file themselves.
- Two new dependencies, both permissive and both real JPMS modules, so no module patching.

## Alternatives rejected

- **One template per format.** Four files to keep in sync, four escapers, and the PDF one would have
  to be a layout language rather than a document.
- **HTML template + HTML-to-PDF renderer.** The natural fit, and unavailable: LGPL (see ADR 008).
- **XSL-FO + Apache FOP.** Permissive and powerful, but unauthorable by the intended audience, and it
  drags Batik into the installer for a two-column list.
- **FreeMarker or Pebble** instead of JMustache. Both permissive and both more capable - conditionals,
  filters, locale-aware formatting. Not needed against a model that is already localized and
  formatted, and both give a template far more room to fail.
- **Template the internal records directly** rather than a map model. Would tie the template contract
  to class and accessor names and force reflective access into the module.
