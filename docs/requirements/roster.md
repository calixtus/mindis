# Roster: Servers and Roles

The people to be scheduled and the liturgical roles they can fill.

Related design decision: [ADR 006 — preferences architecture](../adr/006-preferences-architecture.md)
(shared "core holds no JavaFX types" rule that keeps these models plain records).

## Requirements

### Maintain altar servers
`req~maintain-servers~1`

The user maintains a roster of altar servers: first and last name, a free-form contact, an optional
birth date, and an active flag. An inactive server stays in the roster (history and data are kept)
but is never scheduled.

Covers:
- feat~altar-server-roster~1

### Role qualifications
`req~server-qualifications~1`

Each server carries the set of roles they may fill. A server is never assigned a role they are not
qualified for.

Covers:
- feat~altar-server-roster~1

### Unavailability periods
`req~server-unavailability~1`

Each server carries any number of inclusive date ranges (vacation, exams, …) during which they
cannot be assigned.

Covers:
- feat~altar-server-roster~1

### Siblings and preferences
`req~server-preferences~1`

A server may be linked to siblings through a shared family marker, may declare preferred service
start times, and may be flagged as experienced. All three influence plan quality only, never
feasibility.

Covers:
- feat~altar-server-roster~1

### Maintain liturgical roles
`req~maintain-roles~1`

Liturgical roles are user-defined data, not a fixed list: the user creates, renames, reorders and
deletes roles. A role may carry an optional minimum and/or maximum age (in years).

Covers:
- feat~altar-server-roster~1

### Usable out of the box
`req~default-roles~1`

On first run the roster of roles is pre-populated with the common parish roles (acolyte, cross
bearer, thurifer, boat bearer, master of ceremonies) in the application language, so the user can
start planning without configuring roles first.

Covers:
- feat~altar-server-roster~1

## Design

### Server record
`dsn~server-record~1`

`org.mindis.core.model.Server` is an immutable record: `id`, `firstName`, `lastName`, `contact`,
nullable `birthDate`, nullable `familyId`, `qualifications` (a set of `Role.id()`),
`unavailabilities`, `preferredTimes`, `experienced`, `active`. Ids are random UUIDs
(`Server.newId()`). The compact constructor is null-tolerant and defensively copies every
collection, so JSON written before a field existed still deserializes (absent collection → empty).
Derived helpers: `displayName()`, `isAvailableAt(dateTime)`, `prefers(dateTime)`, and
`ageAt(date)` which returns `null` when the birth date is unknown.

Covers:
- req~maintain-servers~1
- req~server-qualifications~1
- req~server-preferences~1

### Unavailability period
`dsn~unavailability-period~1`

`UnavailabilityPeriod(start, end)` is inclusive on both ends and rejects `end` before `start` at
construction. `Server.isAvailableAt` is false iff any period contains the service's date.

Covers:
- req~server-unavailability~1

### Role record and age range
`dsn~role-record~1`

`org.mindis.core.model.Role` is a record of `id`, `name`, nullable `minAge`, nullable `maxAge` and
`sortOrder`. Roles are referenced everywhere by id, so a rename never breaks existing services or
qualifications. `RoleRepository.nextSortOrder()` hands out the next free order in steps of 10.
An unknown birth date disables the age check entirely rather than failing it
(`MinDisConstraintProvider.outsideAgeRange`).

Covers:
- req~maintain-roles~1

### Seeded default roles
`dsn~seeded-default-roles~1`

`RoleRepository` seeds the five built-in roles the first time `roles.json` does not exist — keyed on
file existence, not on an empty list, so reloading a deliberately emptied role list never
resurrects them. Their ids are the former enum constants (`ACOLYTE`, `CROSS_BEARER`, `THURIFER`,
`BOAT_BEARER`, `MASTER_OF_CEREMONIES`), so data written against the pre-configurable-roles version
still resolves; their names are localized at seed time and remain user-editable afterwards.

Covers:
- req~default-roles~1

### Roster editors
`dsn~roster-editors~1`

`ServersModule` and `RolesModule` are `CrudModule` screens (table left, editor right). The server
editor's qualification checklist binds directly to the shared live role list, so a role added or
renamed in the Roles module — even unsaved — appears immediately. Raising a role's minimum age above
its maximum drags the maximum up with it.

Covers:
- req~maintain-servers~1
- req~server-qualifications~1
- req~server-unavailability~1
- req~maintain-roles~1
