# Application Shell and Preferences

The window the user actually works in: navigation, settings, appearance, language, and error
visibility.

Design decisions: [ADR 001 — view layer](../adr/001-view-layer.md),
[ADR 005 — application shell](../adr/005-shell.md),
[ADR 004 — preferences store](../adr/004-preferences.md),
[ADR 006 — preferences architecture](../adr/006-preferences-architecture.md),
[ADR 002 — packaging](../adr/002-packaging.md),
[ADR 007 — document storage](../adr/007-document-storage.md).

## Requirements

### Module-based main window
`req~app-shell~1`

The application presents its areas — dashboard, servers, roles, templates, services, settings,
about — as modules of one shell window with a sidebar. The sidebar top carries the collection
switcher (below), which owns the application-wide document actions (New, Open, Save, Save as — see
[persistence.md](persistence.md)); there is no separate global toolbar.

Covers:
- feat~multilingual-desktop-app~1

### Collection switcher
`req~collection-switcher~1`

The open document is a collection (one parish). The sidebar top shows the collection's identity — a
display name (the parish name) and a logo (a custom image or, failing that, a stock icon), optionally
on a light or dark backdrop for contrast — with an inline Save action that reflects whether there is
anything to save. When expanded it also shows, below the name, how many servers are active in the
collection. A dropdown switches to one of up to five recently used
collections, opens another document, saves under a new name, edits the current collection's name and
logo, or starts a new collection. Switching away from unsaved work asks first. A recent whose file
has since vanished is reported and dropped from the list. The collection's identity is shown in the
window title and travels inside its own document (see [persistence.md](persistence.md)).

Covers:
- feat~multilingual-desktop-app~1
- feat~local-data-ownership~1

### Overview at a glance
`req~dashboard~1`

The dashboard summarizes the current state on a board of widgets the user arranges: key figures, the
upcoming services, the per-server duty load, per role its open slots and qualified servers, the mix
of service types, the coverage of the weeks ahead, who is away soon and whose birthday is near, the
archived services per month, and the problems — both the conflicts in the current assignments and
what is wrong with the roster. Everything is derived from the live services, servers, roles and the
archive.

Anything counted as "open" or "still to do" covers the services that are still ahead; a slot in a
service that has already happened cannot be filled any more, so it is history rather than work.

Each widget renders its data either as a list or as a diagram; the user picks which from the
widget's header, and the choice is remembered with the layout.

Covers:
- feat~liturgical-service-planning~1

### German and English
`req~language-choice~1`

The user chooses German or English; the whole UI switches immediately, and the choice persists
across restarts. Language names are shown untranslated, so a user can always recognize their own.

Covers:
- feat~multilingual-desktop-app~1

### Appearance settings
`req~appearance-settings~1`

The user chooses a light or dark theme (or follows the operating system's scheme live), an accent
color (default follows the OS accent), a font family and size, and how the module toolbar buttons
render - text only, icons only, or both. Changes apply immediately and persist.

Covers:
- feat~multilingual-desktop-app~1

### Solver settings
`req~solver-settings~1`

The user configures the solver time budget and the weight of each tunable quality preference, and
can reset a settings group to its defaults.

Covers:
- feat~automatic-fair-assignment~1

### Window state persists
`req~window-state~1`

Window position, size, maximized state and sidebar width are restored on the next start, and the
window title names the open collection (its display name, or the file name) and whether it has
unsaved edits.

Covers:
- feat~multilingual-desktop-app~1

### Corrupt or missing settings never break startup
`req~preferences-robustness~1`

A missing, unreadable or outdated preferences file yields the defaults and a log entry — never a
failed start.

Covers:
- feat~multilingual-desktop-app~1

### Errors are visible and copyable
`req~error-visibility~1`

An error in the application's own code is surfaced to the user as a dialog whose text can be
selected and copied, and the in-app log history keeps the recent messages for a bug report.

Covers:
- feat~multilingual-desktop-app~1

### Unsaved work is recognizable
`req~unsaved-indication~1`

Rows and fields with unsaved edits are visually marked, and the collection switcher's Save action
reflects whether there is anything to save.

Covers:
- feat~local-data-ownership~1

## Design

### Shell and modules
`dsn~shell-modules~1`

`org.mindis.gui.shell` holds the bespoke shell (ADR 005). `MinDisApp` builds an `AppShell` from `DashboardModule`, `ServersModule`,
`RolesModule`, `TemplatesModule`, `ServicesModule`, `SettingsModule` and `AboutModule`. The four data
screens extend `CrudModule` (table left, editor right, toolbar on top); `CrudModule` holds no
localized text itself — every button and its wiring belongs to the subclass — and holds no state
either, binding its table to a shared `LiveStore` (`org.mindis.gui.data`, see
[persistence.md](persistence.md)). Toolbar buttons are built through `Toolbars`, which gives each an
icon and the `toolbar-button` style class; a mode class on the shell root (from the
`toolbarButtonDisplay` setting) drives `-fx-content-display` so they show text, icon, or both.
Import always precedes Export in a module's toolbar.

The shell is the content of a GemsFX `PowerPane`, which is the scene root and supplies the overlay
layers above it — modal dialogs, an info center for transient notifications, and a bottom drawer.
`ShellOverlays` reaches them from any node in the scene. A language rebuild swaps only the
`PowerPane`'s content, so those layers survive it. `HiddenSidesPane` (`PowerPane`'s fourth layer) is
deliberately unused for navigation: it overlays rather than pushes content, hides on mouse-exit, and
cannot be resized (ADR 005).

Covers:
- req~app-shell~1

### Collection switcher
`dsn~collection-switcher~1`

`CollectionSwitcher` (GUI) sits in the sidebar-header slot the `AppShell` builder exposes. It binds
its name to `DocumentSession.collectionDisplayName()` and its logo to the open collection's
`CollectionMeta` (a `mdi2c-church` placeholder when there is none), shows a dirty dot bound to
`LiveDatabase.dirtyProperty()`, an active-server count recomputed live off
`LiveDatabase.servers().items()` (shown under the name when expanded), and an inline save `Button`
disabled unless dirty and not solving. The logo sits on a fixed tile sized like a module nav button
(in both states); the expanded button is roughly 1.7x a nav button's height to fit the two text lines,
and on the collapsed rail it shrinks to just that tile.
Its `MenuButton` dropdown is rebuilt on each open (recents change with every save): up to five
recent collections excluding the current one — each switching via `DocumentSession.switchTo` — then
Open other (`onOpen`), Save as (`onSaveAs`, disabled while solving), Edit collection
(`CollectionMetaDialog` → `updateMetadata`) and New collection (`onNew`). It follows the
`AppShell.collapsedProperty()` so the icon-only rail shows just the logo. `CollectionMetaDialog`
edits name, logo and backdrop with a live preview. Logo and icon are one control: clicking the logo
tile opens a popover (a `ContextMenu` of `LogoIcons` glyphs with a "Select custom image" button at
the bottom); picking an icon or an image replaces the other, so there is no separate remove action.
A custom image is a PNG only, size-capped to 512 KB so it stays small inside the document; a custom
image wins over a stock icon, which wins over the default icon. The backdrop is a row of swatches
like the settings accent picker (reusing `accent-selector.css`) - light, dark, or transparent (a
bordered square with a diagonal line) - applied as a rounded inline style shared with the switcher
via `CollectionSwitcher.logoBackgroundStyle`, to lift a low-contrast logo off the sidebar. `MinDisApp` also registers
Ctrl+N/O/S and Ctrl+Shift+S as scene accelerators for the same
actions (the scene survives a language rebuild, so they do too).

Covers:
- req~collection-switcher~1
- req~app-shell~1

### Composition root and DI
`dsn~composition-root~1`

`MinDisApp.start` is the single composition root: it builds one Avaje Inject `BeanScope` for the
application (compile-time wiring, no runtime reflection), constructs `LiveDatabase` exactly once so
stores and their unsaved edits survive a UI rebuild, and hands every module its collaborators by
constructor. There is no view-layer DI hook and no service locator (ADR 001).

Covers:
- req~app-shell~1

### Dashboard view model
`dsn~dashboard-viewmodel~1`

`DashboardViewModel` owns every repository call and every aggregation, computed straight off the
live services, servers, roles and the archive (assignments live on their slots, so there is no plan
to read). It returns one `Snapshot` of plain data — slot counts and coverage, `UpcomingService`,
`ServerLoad`, `RoleStatus`, `ServiceTypeCount`, `WeekCoverage`, `Absence`, `Birthday`,
`ArchiveMonth`, `ProblemCount`, `RosterIssue` — and `DashboardView` decides how to word, format and
draw it; dates go through `DateTimes`, which follows the active language. The view is a plain
JavaFX `StackPane` built in Java, like every other screen (ADR 001).

Nothing a widget shows may set a floor for its card: the card, its body and its content pane all
have a zero minimum, since a `StackPane` cannot resize a child below the child's own minimum and the
grid — not the content — decides how small a card gets. On top of that the body is clipped to the
card, so content that has shrunk as far as it can stops at the edge rather than drawing over the
header or the widget below. The summary's `KeyFigures` handles being squeezed itself: the tiles wrap
onto further lines as the card narrows, and when even that does not fit, the whole row is set in a
smaller font (scaled in `em`, so it follows the user's configured font size).

Everything the aggregations look forward over is bounded by a constant on the view model: the next
services shown, the eight weeks of the coverage trend, the absence horizon, the twelve months of
archive history. The conflict counts come from `ViolationChecker` over a plan built by
`ServicePlans` — a plain factory, not `PlanningService`, so opening the dashboard never creates a
solver — and are skipped above a slot threshold, since the double-booking check is quadratic and
this runs while the board is being built.

`WidgetType` declares each widget's stable id, default grid placement and the `WidgetViewMode`s it
supports (first = default). `WidgetContainer` shows a mode chooser only for a type with more than
one, and `Charts` builds every diagram from plain `(label, value)` data with animation off, a
tooltip per point, and an empty-state label instead of bare axes. Chart colours are AtlantaFX
tokens in `dashboard.css`, so diagrams follow the theme and the user's accent.

Covers:
- req~dashboard~1

### Preferences record
`dsn~preferences-record~1`

`MinDisPreferences` is an immutable record with a `version` (currently 14) plus `languageTag`,
`theme` (`Light`/`Dark`/`System`, where `System` follows the OS colour scheme live), `windowBounds`,
`solverSecondsLimit` (default 30), `softConstraintWeights`, `accentColor`,
`fontFamily`/`fontSize` (default 14, clamped 10–24), `lastExportDirectory`,
`sidebarWidth`, `lastDocument`, `recentCollections` (the switcher's list, capped at five; see
[persistence.md](persistence.md)), `toolbarButtonDisplay` (text / icon / both, default both) and
`dashboardWidgets` (the board layout: per widget its id, grid position, spans and view mode; `null`
until the user first arranges it, an empty list being a deliberately cleared board).
Changes go through wither methods. The compact constructor fills
absent or invalid values with defaults, which is what makes most version steps migration-free. The
v11→v12 step is an explicit migration: the old standalone `followSystemTheme` boolean folds into the
`Theme.SYSTEM` enum value.

Covers:
- req~appearance-settings~1
- req~solver-settings~1
- req~window-state~1

### Preferences store
`dsn~preferences-store~1`

`PreferencesService` (ADR 004) is a hand-rolled `@Singleton` store: lazy load of
`preferences.json`, `update(UnaryOperator)` with an atomic temp-file-and-move write, no-op when the
value is unchanged, and plain `Consumer` listeners — no JavaFX types in core. A missing or corrupt
file logs and falls back to defaults. `migrate` documents every version step explicitly. Unit-tested
by `PreferencesServiceTest`.

Covers:
- req~preferences-robustness~1
- req~window-state~1

### GUI preferences adapter
`dsn~ui-preferences~1`

`UiPreferences` bridges the core store to JavaFX properties, so settings controls bind
bidirectionally and every consumer reacts through subscriptions rather than callbacks
(`UiPreferencesTest`). `SettingsModule` renders one `TitledPane` per group — appearance, then solver
budget and constraint weights — with a "Reset to defaults" button in each header and one AtlantaFX
`Tile` per setting.

Covers:
- req~appearance-settings~1
- req~solver-settings~1

### One user-agent stylesheet
`dsn~theme-styler~1`

`ThemeStyler` composes the base AtlantaFX theme (`@import`) plus the user's accent and font `.root`
overrides into a single `data:` URI installed as the *user-agent* stylesheet — not a scene override
— because popup windows (ComboBox popups etc.) consult only the user-agent stylesheet. Accent tokens
(`-color-accent-fg/emphasis/muted/subtle`) are derived from one base hex per theme mode. It also
defines the legacy Modena tokens GemsFX's bundled control CSS looks up but AtlantaFX never defines.
`MinDisApp` reapplies the whole stylesheet whenever theme, accent, font family
or font size changes, and subscribes to the OS color scheme and OS accent so `AccentColor.DEFAULT`
and the `System` theme track live.

Covers:
- req~appearance-settings~1

### Localization with full-text keys
`dsn~localization~1`

`Localization.lang(englishText, params…)` looks the English text itself up as the key (JabRef style)
in `org/mindis/core/l10n/MinDis[_de|_en].properties`; a missing translation falls back to the key,
so raw keys never reach the UI. Positional parameters are `%0`, `%1`, …. Constraint names double as
localization keys, which is how solver output and violation display get translated for free.
`AppLanguage` is the typed view over the persisted BCP-47 tag (`AppLanguageTest`), and its display
names are intentionally untranslated. The global mutable static is a documented DIP exception
(PLAN.md §8, ADR 003).

Covers:
- req~language-choice~1

### Language change rebuilds the UI
`dsn~language-rebuild~1`

`MinDisApp` sets the locale from preferences before the first scene — and before the startup
document is opened, so a new document's seeded roles get localized names — and a language change
rebuilds the shell (labels are read at construction). `LiveDatabase`, the
`LiveStore`s and the `DocumentSession` are *not* rebuilt, so the open document, its unsaved
cross-module edits and its dirty counts survive the switch; only the title binding is rebuilt, since
its own text is localized.

Covers:
- req~language-choice~1
- req~unsaved-indication~1

### Window geometry
`dsn~window-geometry~1`

The stage is restored from `MinDisPreferences.windowBounds` (position, size, maximized) at startup
and saved on shutdown; the sidebar width is persisted separately. The title is bound to
`DocumentSession.titleBinding()` (application name, the collection display name — its `CollectionMeta`
name, else the file name, else "Untitled" — `*` while dirty), and the window's close request runs
the unsaved-changes guard, consuming the event when the
user cancels. On startup the window is
explicitly brought to the foreground, since launching from a terminal or IDE on Windows can leave it
behind whatever had focus.

Covers:
- req~window-state~1

### Error dialogs and in-app log
`dsn~error-surfacing~1`

`LoggingBootstrap` configures console and file logging. `AlertOnErrorHandler` turns every `SEVERE`
record *from `org.mindis` loggers only* into an error dialog — third-party code bridged through
SLF4J logs SEVERE for its own recoverable reasons, which is noise, not a user-actionable error. The
dialog content is a non-editable `TextArea`, not `Alert.setContentText`, so the text can be selected
and copied into a bug report; it is always shown via `Platform.runLater` because log calls can come
from any thread. `LogConsoleHandler`/`LogConsoleModel` keep the full history (every level, every
logger), rendered severity-colored with per-line copy in the About screen, which also shows a
copyable version-info block.

Covers:
- req~error-visibility~1

### Unsaved-edit indication
`dsn~dirty-indication~1`

Each editor field's label carries a dirty accent computed against the *last-flushed baseline*, not
against the row's current value — a live row may already hold an unsaved edit. Collection-backed
fields (qualifications, preferred times, unavailability periods, slot counts) re-diff the whole
collection against the baseline behind one shared label instead of using the per-property
mechanism. The collection switcher's Save button binds to `LiveDatabase.dirtyProperty()` (row-level
dirty counts, the archive's staged-change flag, and the collection-identity staged flag — editing
the name or logo dirties the document like any other edit) and stays disabled while a solve is
running, as does Save as. Regression-tested by `RolesModuleDirtyFlagTest`.

Covers:
- req~unsaved-indication~1
