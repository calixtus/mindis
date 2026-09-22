# ADR 008: Permissively licensed dependencies only, enforced by the build

Date: 2026-08-12
Status: accepted

## Context

MinDis is distributed under the Apache License 2.0 and shipped as a jpackage installer that
bundles every dependency. Legal compliance is a hard requirement for the application, so the
license of each bundled module is a design constraint, not paperwork done at release time.

The dependency set was not clean. `com.github.librepdf:openpdf`, the PDF export library, is
`MPL-2.0 OR LGPL-2.1+`. Electing MPL-2.0 would have been lawful - file-level copyleft, satisfied by
shipping the jar unmodified with its notice - but it puts a reciprocal license inside a product that
claims to be Apache-2.0, and it obliges anyone repackaging MinDis to keep tracking that obligation.
Nothing else in the shipped set was reciprocal, so one library was carrying all of the risk.

Two kinds of license cannot be avoided in a JavaFX desktop application and are therefore part of the
policy rather than exceptions to it: the bundled Java runtime and the JavaFX modules are GPLv2 **with
the Classpath Exception**, which exists precisely to let independent modules link against them and
ship under their own terms.

## Decision

**Every module that ships with MinDis carries a permissive license, and the build fails if one does
not.**

- **Allowlist.** `config/licenses/allowed-licenses.json` lists the accepted licenses: Apache-2.0,
  MIT, BSD-2-Clause, BSD-3-Clause (which is also what the Eclipse Distribution License normalizes
  to), and CC0/public domain. Individually reviewed exceptions are pinned to a module name: the
  OpenJFX modules and `io.smallrye.classfile:jdk-classfile-backport` under GPLv2+Classpath
  Exception, and `org.junit:junit-bom`, which contributes metadata and no shipped code.
  Multi-licensed modules pass on their permissive half - that is how the dual-licensed
  `jakarta.*`, JAXB and Angus modules are accepted, under BSD-3-Clause.
- **Enforcement.** `org.mindis.gradle.check.licenses` applies the `jk1` license report plugin to
  `:gui`, whose `runtimeClasspath` is exactly what jpackage bundles, normalizes the license names,
  and wires `checkLicense` into `check`. A dependency with a license outside the allowlist fails the
  build.
- **PDF export moves to Apache PDFBox** (`org.apache.pdfbox:pdfbox`, Apache-2.0, as are its
  transitive `fontbox`, `pdfbox-io` and `commons-logging`). OpenPDF is gone.
- **DejaVu Sans is embedded into exported PDFs.** PDFBox draws with the PDF standard-14 fonts
  otherwise, which stop at Windows-1252 and reject anything beyond it; a parish roster contains
  names that do not fit there. The fonts are under the permissive Bitstream Vera license, shipped
  with their license text next to them.
- **Notices are generated, never written by hand.** `generateThirdPartyNotices` builds
  `THIRD-PARTY-NOTICES.md` from the resolved runtime dependencies: a preamble
  (`config/licenses/NOTICE-preamble.md`) covering MinDis's own license, the bundled runtime and the
  fonts, the module list with licenses, and the `META-INF/NOTICE` files of the bundled modules,
  which Apache-2.0 section 4(d) requires to be passed on. The file is packaged into the application
  jar and shown from **About → Third-party licenses**, so it reaches the user with the product.

## Consequences

- PDFBox draws text, not documents: `PdfPlanExporter` owns page breaks, the two-column layout and
  word wrapping itself. That is roughly 100 lines that OpenPDF's `PdfPTable` used to provide.
- The installer grows by ~1.4 MB for the two font files. Exported PDFs embed only a subset of the
  glyphs actually used, so they do not.
- Adding a dependency now also means checking its license: `check` fails on an unlisted one, and the
  allowlist is edited deliberately, with the reason recorded here or in the file's comment.
- Notices cannot go stale, because nothing about them is maintained by hand.

## Alternatives rejected

- **Keep OpenPDF, elect MPL-2.0.** Lawful and free, but leaves reciprocal code in an Apache-2.0
  product and an obligation that every downstream repackager inherits.
- **Flying Saucer or openhtmltopdf** (render HTML/CSS to PDF, which would also have made templated
  layouts easy): both are LGPL-2.1+, with no permissive alternative offered.
- **iText**: AGPL, or a commercial license.
- **Apache FOP** (Apache-2.0, a full XSL-FO layout engine): the only permissive way to get
  CSS-grade page layout, but it drags in Batik and XML Graphics Commons for a two-column list, and
  XSL-FO is not a format the intended template authors could ever edit.
- **A hand-maintained NOTICE file**: cheaper today, wrong within two dependency bumps.
