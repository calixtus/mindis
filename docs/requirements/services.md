# Services, Templates and Slots

The events to be staffed, the recurring patterns they are generated from, and the individual role
slots that carry the assignments.

## Requirements

### Maintain liturgical services
`req~maintain-services~1`

The user maintains individual liturgical services: date and time, duration, location, service type
(Sunday mass, weekday mass, feast, wedding, funeral, other) and a free-form note.

Covers:
- feat~liturgical-service-planning~1

### Role slots per service
`req~service-role-slots~1`

Each service declares how many servers of each role it needs. Every required server is an
individually identified slot that can be filled, changed or cleared on its own.

Covers:
- feat~liturgical-service-planning~1
- feat~manual-plan-control~1

### Weekly templates
`req~weekly-templates~1`

The user maintains recurring weekly service templates ("every Sunday 10:00 at St. Mary", with role
counts), so regular masses are described once rather than entered per week.

Covers:
- feat~liturgical-service-planning~1

### Generate services from templates
`req~generate-services~1`

The user generates concrete services from the templates for a chosen date range. Re-generating a
range that was generated before adds nothing and changes no existing service or assignment.

Covers:
- feat~liturgical-service-planning~1

### Assignments survive slot-count edits
`req~slot-count-edit-preserves-assignments~1`

Changing a role's required headcount on a service keeps the assignments of the slots that remain;
shrinking a count never discards a filled slot while an empty one of the same role exists.

Covers:
- feat~manual-plan-control~1

### Manual assignment
`req~manual-assignment~1`

The user picks a server for any slot from a dropdown of active servers, and can clear a whole
service's slots in one action. A manual pick is marked as a deliberate decision that the solver
must not move.

Covers:
- feat~manual-plan-control~1

### Under-staffing is visible
`req~underfilled-visible~1`

A service whose slots are not all filled is recognizable in the service list without opening it,
and a filled slot that breaks a rule is flagged in the editor.

Covers:
- feat~manual-plan-control~1

### Large service lists stay usable
`req~service-list-paging~1`

The service list is paged in contiguous chronological ranges, so a year or more of generated
services stays navigable.

Covers:
- feat~liturgical-service-planning~1

## Design

### Service record
`dsn~liturgical-service-record~1`

`org.mindis.core.model.LiturgicalService` is a record of `id`, `dateTime`, `durationMinutes`,
`location`, `type` (`ServiceType`), `slots` and `note`. `withSlots(…)` returns a copy with a new
slot list — the single write-back path used after a solve. The service's own end time
(`dateTime + durationMinutes`) is what the overlap rule in
[planning.md](planning.md) uses.

Covers:
- req~maintain-services~1

### Slot as the unit of assignment
`dsn~slot-carries-assignment~1`

`org.mindis.core.model.Slot(id, role, serverId, pinned)` is one physical seat: individually
identified, carrying its own assignment (`serverId`, `null` = open) and its own pin. There is no
separate plan structure — a service *is* its own plan, so saving a service saves its assignments.
`Slot.open(role)` mints an open slot; `withServer(serverId, pinned)` keeps the id (pinning is
dropped when the server is cleared).

Covers:
- req~service-role-slots~1
- req~manual-assignment~1

### Requirement versus instance
`dsn~roleslot-versus-slot~1`

`RoleSlot(role, count)` is the *aggregate* requirement, as edited on a template (negative counts
rejected). `Slot.expand(roleSlots)` turns it into individually-identified open instances;
`Slot.collapse(slots)` is the inverse (ids and assignments discarded), used where a concrete
service must round-trip through a role/count shape, e.g. CSV.

Covers:
- req~service-role-slots~1
- req~weekly-templates~1

### Slot reconciliation on a count edit
`dsn~slot-reconciliation~1`

`SlotReconciler` (GUI) rewrites a service's slot list for edited per-role counts by removing
*specific slot ids* rather than positions, dropping empty slots before filled ones and appending
fresh open slots when a count grows. Because ids are stable, an already-filled slot is never lost to
an index shift. Unit-tested by `SlotReconcilerTest`. `Slot.sameSlots(a, b)` defines "unchanged" for
dirty tracking as the multiset of `(role, serverId, pinned)` — order- and id-insensitive, so pure id
churn from reconciliation is not an unsaved change, while a count edit or a pick is.

Covers:
- req~slot-count-edit-preserves-assignments~1

### Template record
`dsn~service-template-record~1`

`ServiceTemplate(id, dayOfWeek, time, durationMinutes, location, type, slots)` — weekly recurrence
only; month/year/feast-day recurrence is a planned extension (PLAN.md). `RoleSlot.sameSlots`
supplies the order- and zero-count-insensitive "unchanged" comparison for template dirty tracking.

Covers:
- req~weekly-templates~1

### Idempotent generation
`dsn~service-generator~1`

`ServiceGenerator.generate(templates, existingServices, from, toInclusive)` walks the range day by
day, emits one service per template whose `dayOfWeek` matches, and skips any occurrence whose
`(dateTime, location)` key already exists among the existing services or among the services
generated in the same run. Generated services get fresh ids and freshly expanded open slots; the
Services module only ever appends the unmatched proposals, so an existing service's slots and
assignments are never touched.

Covers:
- req~generate-services~1

### Service editor and assignment panel
`dsn~service-editor~1`

`ServicesModule`'s editor holds the date/time/type/location/note fields plus one server dropdown per
slot, seeded with the slot's current server. The dropdown lists active servers; picking one rewrites
that slot on the live slot list, pins it, and stages the service into the shared `LiveStore` like any
other edit. "Clear" empties every slot of the service (server and pin), a no-op when all are already
empty. Violations shown as a per-row warning icon come from a transient whole-board problem (see
[planning.md](planning.md)) — `Slot unassigned` is suppressed there, being already obvious from the
empty dropdown.

Covers:
- req~manual-assignment~1
- req~underfilled-visible~1

### Service tiles and paging
`dsn~service-tiles-paging~1`

Each table row renders as a tile: large date/time with an underfilled warning icon, type and
location below, and a per-role grid of that role's slots showing the assigned server or `-`. Tiles
re-render off a single subscription to the role and server stores, so a rename elsewhere shows up
without an explicit refresh. The table shows a fixed page size of chronologically sorted services;
the page index clamps down when a deletion leaves it past the last page.

Covers:
- req~underfilled-visible~1
- req~service-list-paging~1
