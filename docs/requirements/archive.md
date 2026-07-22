# Plan Archive

Freezing services that have happened into immutable, self-contained history.

## Requirements

### Archive past services
`req~archive-past-services~1`

The user freezes every service up to a chosen cutoff date into the archive. The frozen services
leave the live service list, so planning stays focused on what is still ahead.

Covers:
- feat~plan-history~1

### Archived plans are immutable
`req~archive-immutable~1`

An archived service is only ever viewed, exported or deleted — never edited or re-solved.

Covers:
- feat~plan-history~1

### Archived plans survive roster changes
`req~archive-faithful~1`

An archived plan shows and exports exactly what was planned, indefinitely, even after the servers
or roles it referenced are renamed or deleted.

Covers:
- feat~plan-history~1

### Browse and clean up the archive
`req~archive-browsing~1`

The user browses archived services newest first, exports one, and can permanently delete an entry
(retention/cleanup).

Covers:
- feat~plan-history~1
- feat~local-data-ownership~1

## Design

### Archived snapshot record
`dsn~archived-service-record~1`

`ArchivedService` copies the service's `id`, `dateTime`, `durationMinutes`, `location`, `type` and
`note`, plus `archivedAt` and a list of `ArchivedSlot(roleName, serverId, serverName)`. It carries
**no foreign keys for display**: role and server *display names* are resolved and copied in at
archive time, so later roster changes cannot alter what an archived service shows. `serverId` is
kept only so cross-boundary spacing can still match a server by identity
(`dsn~prior-plan-facts~1`); it is never resolved against the live roster for display.
`serverName` is `null` for a slot that was left open.

Covers:
- req~archive-immutable~1
- req~archive-faithful~1

### Archiving is a pure transformation
`dsn~service-archiver~1`

`ServiceArchiver.archive(live, cutoff, archivedAt, roleName, serverName)` is dependency-free: the
caller supplies the name lookups and the timestamp, so it stays a testable transformation with no
repository or clock coupling (`ServiceArchiverTest`). It snapshots every service dated on or before
the cutoff and returns `Result(archived, removedServiceIds)`. A name lookup returning `null` (role
or server already gone) falls back to the stored id, so archiving never loses a slot; services after
the cutoff are untouched.

Covers:
- req~archive-past-services~1
- req~archive-faithful~1

### Archive run
`dsn~archive-run~1`

`PlanningService.archive(cutoff)` resolves the lookups from the role and server repositories,
persists the snapshots immediately, and returns the ids to drop. `ServicesModule` removes those rows
from the live store; the removal is committed by the ordinary global Save all.

Covers:
- req~archive-past-services~1

### Archive storage
`dsn~archived-service-repository~1`

`ArchivedServiceRepository` stores `archived-services.json` in the user data directory. Unlike the
four live repositories it is committed history, not staged state: `addAll` and `delete` write to
disk immediately and are excluded from `AppDatabase.saveAll()`. `findAll()` returns newest first.

Covers:
- req~archive-immutable~1
- req~archive-browsing~1

### Archived plans dialog
`dsn~archived-plans-dialog~1`

`ArchivedPlansDialog` browses the archived services (view, export via
[export.md](export.md), delete) and hosts the "Archive up to…" action itself. The archive action is
passed in from `ServicesModule`, because freezing also removes the services from the live list which
that module owns.

Covers:
- req~archive-browsing~1
- req~archive-past-services~1
