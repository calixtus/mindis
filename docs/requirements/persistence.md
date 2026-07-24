# Persistence and Data Exchange

Where data lives, how it is opened and saved, how edits are staged, and how data gets in and out as
CSV.

Design decisions: [ADR 007 — one user-chosen document file](../adr/007-document-storage.md),
[ADR 004 — preferences: own JSON store in core](../adr/004-preferences.md) (preferences keep their
own file in the user data directory; only *entity* data moved into the document).

## Requirements

### One document file
`req~single-document-file~1`

All planning data of a parish — roles, servers, templates, services with their assignments, and the
archive — plus the collection's own identity (a display name, a logo — a custom image or a stock
icon — and its backdrop) lives in a single,
plain, human-readable JSON file that the user chooses, names and can copy, back up or hand on like
any other file. The identity travels inside the file, so a collection is self-contained. No database,
server, account, or hidden storage location.

Covers:
- feat~local-data-ownership~1

### Document actions
`req~document-actions~1`

The user starts a new document, opens an existing one, saves the open one, saves it under a new name,
and edits the open collection's name and logo. A document that has never been saved has no file yet;
saving it asks where to put it. The application remembers up to five recently used documents for
quick reopening.

Covers:
- feat~local-data-ownership~1

### Reopen the last document
`req~reopen-last-document~1`

Starting the application reopens the document that was open last. If there is none, or it has since
been moved or cannot be read, the application starts with a new empty document and says so — a
startup never fails because of a file the user may not even remember choosing.

Covers:
- feat~local-data-ownership~1

### Explicit save
`req~staged-edits~1`

Edits in every screen are staged, not written per keystroke: one Save writes the whole document to
disk. Unsaved edits are visible as such, counted, and shared across screens; the open document's
name and its unsaved state are visible in the window title.

Covers:
- feat~local-data-ownership~1
- feat~manual-plan-control~1

### Unsaved work is never dropped silently
`req~unsaved-changes-guard~1`

Starting a new document, opening another one, or closing the window while there are unsaved edits
asks first, offering to save, to discard, or to cancel the action.

Covers:
- feat~local-data-ownership~1

### Never lose data to a crash or a bad file
`req~robust-storage~2`

A save either fully succeeds or leaves the previous file intact. A document that cannot be read is
reported to the user, and the document currently open stays untouched — a failed open never
silently presents an empty parish.

Covers:
- feat~local-data-ownership~1

### One shared source of truth
`req~shared-live-state~1`

An edit made in one screen — even before saving — is immediately visible everywhere else that shows
the same data, including in the solver's view of the world.

Covers:
- feat~local-data-ownership~1

### CSV import and export
`req~csv-exchange~1`

Roles, servers, templates and services can be exported to and imported from CSV, so a roster can be
prepared or bulk-edited in a spreadsheet.

Covers:
- feat~local-data-ownership~1

### Forward-compatible data files
`req~data-file-evolution~1`

A file written by an older version still loads: fields added since are filled with their defaults,
and fields removed since are ignored.

Covers:
- feat~local-data-ownership~1

## Design

### Document record
`dsn~document-record~1`

`MinDisDocument(version, roles, servers, templates, services, archivedServices)` is the whole unit
the user opens and saves; `CURRENT_VERSION` is 1 and `empty()` is the starting point of a new
document. Null-tolerant like the model records: a list absent from an older or hand-edited file
reads as empty rather than failing the whole open.

Covers:
- req~single-document-file~1

### Atomic document store
`dsn~document-store~1`

`DocumentStore` reads and writes the document with Jackson (`JavaTimeModule`, ISO dates, pretty
printed, unknown properties ignored). `write` goes to a `.tmp` sibling and is moved over the target
with `ATOMIC_MOVE`, falling back to a plain replace where the filesystem does not support it.
Unlike the per-entity store this replaced, `read` does **not** swallow a failure into an empty
result: the file is one the user picked explicitly, so the caller must be able to report it.
`PreferencesService` keeps its own file and the same temp-file-and-move discipline.

Covers:
- req~robust-storage~2

### In-memory repositories
`dsn~in-memory-repositories~1`

`RoleRepository`, `ServerRepository`, `TemplateRepository`, `ServiceRepository` and
`ArchivedServiceRepository` hold the open document's content in memory and nothing else — no file
path, no lazy load, no flush. `save`/`delete` upsert by id and stage; `replaceAll` (package-private,
`AppDatabase` only) swaps in a freshly opened document. All methods are `synchronized`. Sort orders:
roles by `sortOrder` then name, servers by last then first name, services by date-time, templates by
day then time, archive newest first.

Covers:
- req~shared-live-state~1
- req~staged-edits~1

### Document actions
`dsn~document-actions~1`

`AppDatabase` aggregates the five repositories and owns the open document's path (`null` =
untitled) and its `CollectionMeta` identity. It is the only disk-I/O entry point for entity data:
`newDocument()` (empty, untitled, empty identity, seeded with `RoleRepository.defaults()`),
`open(Path)`, `save()`, `saveAs(Path)` and `reload()` (re-read, discarding staged edits; an untitled
document resets to a new one), plus `meta()`/`updateMeta()` for the identity. `save()` on an
untitled document throws `IllegalStateException` — the caller must route it to `saveAs`. The
document is a `MinDisDocument` (version 2 since `CollectionMeta` was added; a v1 file simply lacks
the field and reads back as an empty identity). Assignments are part of the service records and the
archive is part of the document, so one save covers everything. Unit-tested by
`DocumentRoundTripTest`.

Covers:
- req~document-actions~1
- req~single-document-file~1
- req~staged-edits~1

### Observable mirror with dirty tracking
`dsn~live-store~2`

`LiveStore<T>` (workbench) is one long-lived JavaFX-observable mirror per entity type, constructed
once at startup and surviving UI rebuilds. Every mutation (`updateLive`, `insertFirst`, `remove`,
`mergeLive`) is **write-through**: it updates the observable list *and* the repository in the same
call, so non-GUI readers (solver, CSV mappers, generators) always see current state. A row counts as
dirty when it differs from its snapshot in the last-*saved* baseline according to a per-type
`equivalence` predicate, or has no snapshot yet; each removal of a previously saved row counts too.
The baseline moves only in `refresh()`, which `LiveDatabase` runs after every document action.
Equivalence is order-insensitive where order is not semantically significant (`RoleSlot.sameSlots`,
`Slot.sameSlots`).

`LiveDatabase` owns the four stores, mirrors `AppDatabase`'s document path and `CollectionMeta` into
properties, and exposes `dirtyProperty()` — the row-level dirty counts **plus** the archive's own
staged-change flag (see [archive.md](archive.md)) **plus** a collection-identity staged flag
(`updateMeta` dirties the document like any other edit), neither of which row-level tracking covers.
That one binding drives the collection switcher's Save button, the window title and the close guard.

Covers:
- req~staged-edits~1
- req~shared-live-state~1

### Document session
`dsn~document-session~1`

`DocumentSession` (GUI) turns the document actions into user-facing ones: the file chooser (one
`*.json` filter, starting in the current document's directory, default name `mindis.json`), the
error dialogs for a failed open or save, the window title (application name, the collection display
name — its `CollectionMeta` name, else the file name, else "Untitled" — plus `*` while dirty), and
the remembered documents. `confirmDropUnsavedChanges()` is the shared guard for New, Open, switching
collection and window close: Save (which may itself route to a location prompt), Discard, or
Cancel — the answer decides whether the caller proceeds. `openLastDocumentOrNew()` runs at startup,
after the locale is applied, so a new document's seeded roles get localized names. It also serves the
collection switcher: `recents()`, `switchTo()` (guarded open of a recent, self-healing — a vanished
or unreadable recent is reported and dropped), `currentMeta()`/`updateMetadata()` and
`collectionDisplayName()`. Every action records the document, with a snapshot of its identity, at the
front of the recent list.

Covers:
- req~document-actions~1
- req~reopen-last-document~1
- req~unsaved-changes-guard~1
- req~collection-switcher~1

### Remembered documents
`dsn~last-document-preference~1`

`MinDisPreferences.lastDocument` (added in preferences version 9) holds the absolute path of the
document last opened or saved, and is cleared when the user starts an untitled document;
`recentCollections` (version 10, capped at `MAX_RECENT_COLLECTIONS` = 5) is the switcher's list —
each entry a path plus a cached name and logo, most recent first, dedup by path, refreshed on every
open or save. The v9→v10 migration seeds the list from `lastDocument`. Both live in
`preferences.json` in the user data directory (`AppDirectories.userDataDir()`: `%APPDATA%\MinDis`,
`~/Library/Application Support/MinDis`, XDG), which also holds the log directory — the only data the
application still keeps outside the user's own document.

Covers:
- req~reopen-last-document~1
- req~collection-switcher~1

### CSV mappers
`dsn~csv-mappers~1`

`RoleCsvMapper`, `ServerCsvMapper`, `TemplateCsvMapper` and `ServiceCsvMapper` (core) each supply a
header, a to-row and a from-row function, wired into `CrudModule`'s generic `exportCsv`/`importCsv`
actions through `CsvRowMapper`. The mappers that reference roles take the `RoleRepository`, so role
columns can be written and read as names. Slot columns round-trip through the collapsed role/count
representation (`dsn~roleslot-versus-slot~1`). An import stages like any other edit and reaches disk
with the next save.

Covers:
- req~csv-exchange~1

### Best-effort CSV parsing
`dsn~csv-lenient-parsing~1`

`CsvFields` reads a field as `""` when the row is short and parses ints, dates, times, days of week
and service types leniently: an unparsable value yields `null` (or a caller-supplied fallback)
rather than throwing, matching the free-form-field convention of the editors. Import never aborts a
whole file over one bad cell.

Covers:
- req~csv-exchange~1

### Tolerant deserialization
`dsn~tolerant-deserialization~2`

`FAIL_ON_UNKNOWN_PROPERTIES` is off everywhere, so a removed field in an old file is ignored, and
every record's compact constructor fills absent collections and newer scalars with defaults
(`MinDisDocument`, `Server`, `MinDisPreferences`, `Slot`'s `serverId`/`pinned`). The seeded default
roles keep the ids of the former `Role` enum constants, so data referencing those names resolves
without migration. Preferences carry an explicit `version` with documented migrations
(`dsn~preferences-store~1`); the document carries its own `version` for the same purpose.
Round-trip tested by `DocumentRoundTripTest` and `WriteThroughGeneratorTest`.

Covers:
- req~data-file-evolution~1
