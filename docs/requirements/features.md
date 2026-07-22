# Features

Vision-level features, distilled from the [README](../../README.md) and the product overview in
[PLAN.md](../../PLAN.md) §1.

## Altar server roster
`feat~altar-server-roster~1`

The parish maintains a roster of altar servers: personal details, family ties between siblings,
the liturgical roles each server is qualified for, and the periods they are unavailable.

## Liturgical service planning
`feat~liturgical-service-planning~1`

The parish maintains its liturgical services (Sunday and weekday masses, feasts, weddings,
funerals) with the roles and headcounts each service requires, and generates recurring services
from weekly templates instead of entering every mass by hand.

## Automatic fair assignment
`feat~automatic-fair-assignment~1`

The application computes assignments of servers to role slots automatically, respecting rules that
must never be broken (qualification, availability, no double-booking) and optimizing preferences
(fair workload, siblings together, spacing between duties, experience per service).

## Manual control over the plan
`feat~manual-plan-control~1`

The planner stays in control: any slot can be filled, changed or cleared by hand, a manual decision
is never overwritten by a later solve, and a solve can be scoped to a date window or a single
service.

## Plan distribution
`feat~plan-distribution~1`

A finished plan can be exported as a document to hand out or print (PDF and plain-text formats).

## Plan history
`feat~plan-history~1`

Past services are frozen into an archive that stays faithful and exportable indefinitely, even
after the servers or roles they referenced are renamed or deleted.

## Local, file-based data ownership
`feat~local-data-ownership~1`

All data is the user's: plain JSON files in the user's own data directory, importable and
exportable as CSV, with no server, account or network dependency.

## Multilingual desktop application
`feat~multilingual-desktop-app~1`

The application is a cross-platform desktop application, German and English from the start, with
user-configurable appearance (theme, accent, font) and no installation-wide state.
