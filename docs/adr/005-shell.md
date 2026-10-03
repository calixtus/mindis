# ADR 005: Bespoke application shell in `mindis-gui`, GemsFX PowerPane for overlays

Date: 2026-07-30
Status: accepted

## Context

MinDis needs a shell: a permanent sidebar with one entry per functional area, a content area for the
active area, and — over both — modal dialogs, transient notifications and a bottom drawer.

Two off-the-shelf options were assessed.

**WorkbenchFX** covers the navigation half. It is unmaintained (last release Jan 2022, Java 11 era,
no `module-info`): 36 interlinked source files with custom Control/Skin pairs, FontAwesomeFX woven
through toolbar/tabs/tiles, a large custom CSS layer to rewrite against AtlantaFX tokens, and four
years of JavaFX API drift to fix. MinDis needs a fraction of that — module lifecycle, a sidebar, and
content switching.

**GemsFX `HiddenSidesPane`** is sometimes proposed for the sidebar. It is a different control with
different semantics:

| | what MinDis needs | `HiddenSidesPane` |
|---|---|---|
| layout | pushes content aside | `StackPane` — overlays content |
| trigger | chevron click / drag handle | mouse within `triggerDistance` (16px) of the edge |
| persistence | always visible | animates away on mouse-exit unless `pinnedSide` is set |
| resize | drag handle, 200–360px, snaps to an icon rail below 120 | none; sides are pinned to `USE_PREF_SIZE` |
| collapsed rail | icon-only 60px mode | none |

Setting `pinnedSide = LEFT` restores "always visible" but disables the only behavior the control
adds, keeps the overlay layout, and still leaves the resize handle, collapse snap, chevron and
collapsed-state property to be written by hand.

**GemsFX `PowerPane`** covers the other half, and only that half: it is a ~150-line composition of
`InfoCenterPane` + `DialogPane` + `DrawerStackPane` + `HiddenSidesPane`, contributing no navigation
or module-lifecycle machinery at all. It is orthogonal to the shell rather than an alternative.

## Decision

**The navigation shell is bespoke, and lives in `mindis-gui`** — no separate Gradle project, since
it has exactly one consumer and no third-party code to isolate.

- `org.mindis.gui.shell` — `AppShell` (`BorderPane`: sidebar left, active module's content right,
  `AppShell.builder(...)` with bottom-pinned entries and a sidebar-header slot), `ShellModule`
  (`activate`/`deactivate`/`dispose` lifecycle), `CrudModule` (the table+editor screen the
  four data areas share), `ShellOverlays`, `shell.css`, `power-pane.css`.
- `org.mindis.gui.data` — `LiveStore`, `CsvIO`, `CsvRowMapper`: the staging layer the modules read
  and edit, independent of the shell.
- Styling via AtlantaFX design tokens (`-color-*`), so light/dark follows the active theme with no
  bridge layer. Icons via Ikonli (`ikonli-javafx` + materialdesign2), no FontAwesomeFX.
- No WorkbenchFX code is used, so there is no Apache-2.0 attribution or NOTICE obligation. If code
  is ever lifted from it, PLAN.md §4.1's attribution rules apply.

**`PowerPane` wraps `AppShell` and supplies the overlay layers.** It is the scene root; the shell is
its content. A language change rebuilds the shell and calls `PowerPane.setContent(...)` rather than
`Scene.setRoot(...)`, so the overlay layers — and anything currently showing in them — survive it.

`ShellOverlays` is the access path: a plain object constructed in the composition root
(`MinDisApp`) around the `PowerPane`, handed to collaborators by constructor. Not static methods
and not a scene-graph lookup — callers declare the dependency, and a test can pass its own
`PowerPane`. `CrudModule` takes one and uses it for CSV import/export feedback: failures through
`dialogs().showError(...)`, the "n of m rows imported" count through `notify(...)`, which needs no
answer and so should not block on an OK button.

`HiddenSidesPane` (the fourth `PowerPane` layer) stays unused for primary navigation. It remains
available for a transient secondary panel — a right-side inspector, a bottom log tray — where
hover-reveal-and-hide is the wanted behavior.

## Consequences

- Full control over shell behavior and styling; the maintenance burden is ours. `AppShell` was
  ~300 lines when this was written and is ~500 now — the growth is the sidebar's own behaviour
  (resizable width model with an icon-only rail, badges, a scrolling module list), which is
  specified in [requirements/ui.md](../requirements/ui.md) as `dsn~sidebar-navigation~1` and
  covered by `AppShellNavigationTest` and `AppShellSidebarTest`. Still a fraction of
  WorkbenchFX's 36 files, and still no third-party shell code to track.
- No third-party shell library is a dependency.
- GemsFX's overlay panes each override `getUserAgentStylesheet()` with their own bundled CSS, which
  looks up Modena tokens AtlantaFX never defines and hardcodes some light-theme literals. The
  unresolved tokens are defined once, in the user-agent layer `ThemeStyler` adds to the theme
  (`-fx-background`, `-fx-control-inner-background-alt`); the literals are overridden in
  `shell/power-pane.css` — the drawer's `#e0e0e0, white` fill and `#3b424c` header buttons, the
  info center's `yellow`/`red`/`green` severity fills — an author stylesheet, so it outranks
  GemsFX's per-pane user-agent CSS outright.
- Dialogs elsewhere in the app still use `javafx.scene.control.Alert`, which opens a separate
  Modena-styled stage. `ShellOverlays.dialogs()` is the in-window replacement to move them to.
- `CrudModule` deliberately holds no localized text, so every button's wording stays with the screen
  that knows what the button does. Nothing prevents it from reaching `Localization` — this is a
  design choice, not a module-boundary limitation.
