# ADR 007: One user-chosen document file instead of a per-user data directory

Date: 2026-07-23
Status: accepted

Supersedes the storage half of [ADR 004](004-preferences.md) for *entity* data; preferences keep
their own store there.

## Context

Entity data lived in five fixed files under the platform user data directory (`roles.json`,
`servers.json`, `templates.json`, `services.json`, `archived-services.json`), loaded lazily per
repository and flushed by a global "Save all"/"Load" pair. That makes the data invisible and
un-nameable to the user: one implicit parish per machine account, no way to keep two parishes or a
trial plan apart, no obvious thing to back up, copy to another machine or hand to a successor -
which is exactly what a parish volunteer handover needs. It also gave "Load" a meaning ("discard my
edits") no desktop user expects from that word.

## Decision

All entity data is one JSON document the user opens and saves like any other file.

- `MinDisDocument` - record holding roles, servers, templates, services (assignments included, they
  live on the slots) and the archive, plus a `version`. Null-tolerant, so an older or hand-edited
  file still opens.
- `DocumentStore` - Jackson read/write of that record; atomic write (temp file + move, non-atomic
  fallback). A failed read throws instead of yielding an empty document: the user picked this exact
  file and must learn that it did not open.
- Repositories become pure in-memory stores of the open document (no path, no lazy load, no flush).
  `AppDatabase` owns the path and the actions: `newDocument`, `open`, `save`, `saveAs`, `reload`.
  An untitled document has no path; `save()` on it is a programming error routed to `saveAs`.
- Toolbar: **New / Open… / Save / Save as…** replaces Save all / Load. `DocumentSession` (GUI) owns
  the file chooser, the error dialogs, the window title (file name + `*` when dirty) and the guard
  that offers Save / Discard / Cancel before New, Open or window close.
- Startup reopens `MinDisPreferences.lastDocument` (preferences version 9); missing or unreadable
  yields a new untitled document and a reported warning, never a failed start.
- Default roles are seeded into a *new document* rather than on first access of a missing file - an
  opened document whose roster was deliberately emptied stays empty.
- The archive moves into the document too. It therefore stages like every other edit instead of
  writing immediately; since archive entries are not rows of a `LiveStore`,
  `ArchivedServiceRepository` carries its own dirty flag, which `LiveDatabase` folds into the
  application-wide unsaved-changes signal.

Preferences (`preferences.json`) and logs stay in the user data directory: they are per-installation
settings, not parish data, and must be readable before any document is known.

## Consequences

- No migration: data written by earlier versions under the data directory is not read any more. The
  files are left untouched on disk, so a user can still recover them by hand, but the application
  starts empty. Accepted deliberately - the project is pre-release (README: "not yet ready for
  production use") and a migration path would outlive its usefulness.
- Saving is now all-or-nothing per document, including the archive; a crash between archiving and
  saving loses the freeze, which the archive confirmation says.
- Multiple parishes, template documents and plain file backup all come for free.

## Alternatives rejected

- **Keep the data directory, add "Open other folder"** - keeps the invisible default, and a folder
  of five files is still not a thing a user can hand on as one artifact.
- **SQLite / embedded DB** - a single file too, but opaque to the user, un-diffable, and a new
  runtime dependency for a data set that is a few hundred kilobytes of JSON.
- **Auto-save on every edit** - would remove Save, but also the deliberate "try a solve, discard it"
  workflow the staged model gives, and there is no undo to lean on.
- **Migrate the old files into a default document on first start** - considered and dropped with the
  no-migration decision above; it would need a permanent legacy reader for a pre-release format.
