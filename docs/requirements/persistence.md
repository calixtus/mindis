# Persistence and Data Exchange

Where data lives, how edits are staged and committed, and how data gets in and out as CSV.

Related design decision: [ADR 004 — preferences: own JSON store in core](../adr/004-preferences.md)
(same serializer and directory family as the entity repositories).

## Requirements

### Local file storage
`req~local-json-storage~1`

All data is stored as plain, human-readable JSON files in the per-user data directory of the
operating system. No database, server or account.

Covers:
- feat~local-data-ownership~1

### Never lose data to a crash or a bad file
`req~robust-storage~1`

A write either fully succeeds or leaves the previous file intact. A missing file starts empty, and
a corrupt file is reported and starts empty rather than crashing the application.

Covers:
- feat~local-data-ownership~1

### Explicit save
`req~explicit-save-load~1`

Edits in every screen are staged, not written per keystroke: one global Save all commits everything
to disk, and one global Load discards all staged edits and reloads from disk. Unsaved edits are
visible as such, counted, and shared across screens.

Covers:
- feat~local-data-ownership~1
- feat~manual-plan-control~1

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

A data file written by an older version still loads: fields added since are filled with their
defaults, and fields removed since are ignored.

Covers:
- feat~local-data-ownership~1

## Design

### User data directory
`dsn~app-directories~1`

`AppDirectories.userDataDir()` follows platform convention: `%APPDATA%\MinDis` on Windows
(falling back to `~/AppData/Roaming/MinDis`), `~/Library/Application Support/MinDis` on macOS,
`$XDG_DATA_HOME/MinDis` or `~/.local/share/MinDis` elsewhere. Files: `roles.json`, `servers.json`,
`templates.json`, `services.json`, `archived-services.json`, `preferences.json`.

Covers:
- req~local-json-storage~1

### Atomic, fault-tolerant JSON store
`dsn~json-store~1`

`JsonStore<T>` reads and writes one list per file with Jackson (`JavaTimeModule`, ISO dates, pretty
printed, unknown properties ignored). `save` writes a `.tmp` sibling and moves it over the target
with `ATOMIC_MOVE`, falling back to a plain replace where the filesystem does not support it. `load`
returns an empty list for a missing file and, for an unreadable one, logs a warning and returns
empty instead of throwing. `PreferencesService` uses the same temp-file-and-move discipline.

Covers:
- req~robust-storage~1
- req~local-json-storage~1

### Repositories stage, only flush touches disk
`dsn~staged-repositories~1`

`RoleRepository`, `ServerRepository`, `TemplateRepository` and `ServiceRepository` each hold a
lazily loaded, sorted in-memory cache. `save`/`delete` mutate that cache only; `flush()` is the sole
disk write and `reload()` discards staged mutations and re-reads. All methods are `synchronized`.
Sort orders: roles by `sortOrder` then name, servers by last then first name, services by date-time.

Covers:
- req~explicit-save-load~1
- req~shared-live-state~1

### Two global data actions
`dsn~app-database~1`

`AppDatabase` aggregates the four live repositories and exposes exactly two disk entry points:
`saveAll()` (flush all) and `loadAll()` (reload all, discarding staged edits). Assignments are part
of the service records, so a plan is flushed with everything else — there is no separate plan store.
The archive is deliberately excluded (see [archive.md](archive.md)).

Covers:
- req~explicit-save-load~1

### Observable mirror with dirty tracking
`dsn~live-store~1`

`LiveStore<T>` (workbench) is one long-lived JavaFX-observable mirror per entity type, constructed
once at startup and surviving UI rebuilds. Every mutation (`updateLive`, `insertFirst`, `remove`,
`mergeLive`) is **write-through**: it updates the observable list *and* stages into the repository
cache in the same call, so non-GUI readers (solver, CSV mappers, generators) always see current
state. A row counts as dirty when it differs from its snapshot in the last-*flushed* baseline
according to a per-type `equivalence` predicate, or has no snapshot yet; each removal of a
previously flushed row counts too. The baseline moves only in `refresh()`, which runs after a global
Save all or Load. `LiveDatabase` owns the four stores plus `totalDirtyCount()`, the binding a global
Save all button's disabled state uses. Equivalence is order-insensitive where order is not
semantically significant (`RoleSlot.sameSlots`, `Slot.sameSlots`).

Covers:
- req~shared-live-state~1
- req~explicit-save-load~1

### CSV mappers
`dsn~csv-mappers~1`

`RoleCsvMapper`, `ServerCsvMapper`, `TemplateCsvMapper` and `ServiceCsvMapper` (core) each supply a
header, a to-row and a from-row function, wired into `CrudModule`'s generic `exportCsv`/`importCsv`
actions through `CsvRowMapper`. The mappers that reference roles take the `RoleRepository`, so role
columns can be written and read as names. Slot columns round-trip through the collapsed role/count
representation (`dsn~roleslot-versus-slot~1`).

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
`dsn~tolerant-deserialization~1`

`FAIL_ON_UNKNOWN_PROPERTIES` is off everywhere, so a removed field in an old file is ignored, and
every model record's compact constructor fills absent collections and newer scalars with defaults
(`Server`, `MinDisPreferences`, `Slot`'s `serverId`/`pinned`). Role ids of the seeded defaults are
kept equal to the former enum constant names so pre-existing data resolves without migration.
Preferences additionally carry an explicit `version` with documented migrations
(`dsn~preferences-store~1`). Round-trip tested by `RepositoryRoundTripTest`,
`RoleRepositoryRoundTripTest` and `WriteThroughGeneratorTest`.

Covers:
- req~data-file-evolution~1
