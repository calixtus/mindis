# ADR 005: Workbench shell — bespoke implementation, WorkbenchFX-inspired API

Date: 2026-07-06
Status: accepted

## Context

PLAN.md M1 planned an in-repo fork of WorkbenchFX's core (Apache-2.0, unmaintained since
Jan 2022). The plan's own risk table (section 6) timeboxed the extraction with a fallback:
"a small bespoke shell (tabs + drawer + dialogs on plain JavaFX) — MinDis needs only a
subset anyway."

## Decision

**Fallback executed: bespoke shell** in `org.mindis.workbench`, no WorkbenchFX code imported.

Assessment that triggered it: workbenchfx-core is 36 interlinked source files with custom
Control/Skin pairs, FontAwesomeFX woven through toolbar/tabs/tiles, a large custom CSS layer
to rewrite against AtlantaFX tokens, and four years of JavaFX API drift to fix. MinDis needs
a fraction of it (module lifecycle, home tiles, tabbed modules).

Implementation (~2 files):

- `WorkbenchModule` — same lifecycle contract as WorkbenchFX (`activate`/`deactivate`/
  `destroy` with close-veto), so later code reads like WorkbenchFX code.
- `Workbench` — `BorderPane` + `TabPane`: non-closable home tab with one tile per module,
  closable tab per open module; `Workbench.builder(...).homeTabTitle(...).build()`.
- Styling via AtlantaFX design tokens (`-color-*`) in `workbench.css` — no theme bridge
  needed, light/dark follows the active AtlantaFX theme automatically.
- No FontAwesomeFX, no Ikonli (yet): placeholder modules need no icons; add Ikonli when
  real icons arrive.

License note: API shape inspired by WorkbenchFX; no source code copied, so no NOTICE
obligation. If code is ever lifted from WorkbenchFX later, Apache-2.0 attribution rules from
PLAN.md 4.1 apply.

## Consequences

- Drawer, dialog system, module toolbars: not implemented; added on demand in M2+.
- Full control over shell behavior/styling; maintenance burden is ours but tiny.
- `com.dlsc.workbenchfx` never becomes a dependency (M1 done-when criterion holds).
