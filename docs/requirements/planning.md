# Planning and Solving

The constraint model, the solver runs around it, and the scoping that keeps a solve from disturbing
decisions the planner already made.

Engine: [Timefold Solver](https://timefold.ai/solver) (PLAN.md §2, §3).

## Requirements

### Rules that must hold
`req~hard-rules~1`

An assignment must never place a server who is not qualified for the role, who is unavailable on
that date, who is inactive, or who is already serving an overlapping service (including a second
slot of the same service).

Covers:
- feat~automatic-fair-assignment~1

### Partial plans are allowed
`req~partial-plans~1`

Leaving a slot open is discouraged but permitted: an over-constrained month yields the best partial
plan rather than no plan at all. An open slot always loses against any rule violation.

Covers:
- feat~automatic-fair-assignment~1

### Plan quality preferences
`req~plan-quality~1`

Among feasible plans the solver prefers: an even workload across servers, siblings serving the same
service, no server serving on two near-consecutive days, servers serving at their preferred times,
at least one experienced server per service, and servers within their role's age range.

Covers:
- feat~automatic-fair-assignment~1

### Continuity across plan boundaries
`req~prior-plan-spacing~1`

Spacing is respected across the boundary to the previously planned (now archived) period: a server
who served on the last day before the current services is not scheduled again right against it.

Covers:
- feat~automatic-fair-assignment~1

### Tunable preferences
`req~tunable-weights~1`

The relative importance of every quality preference is user-configurable; the rules that must hold
are not.

Covers:
- feat~automatic-fair-assignment~1

### Bounded solve time
`req~bounded-solve-time~1`

A solve runs at most the user-configured time budget, and stops earlier once it has clearly
converged. A running solve can be aborted by the user, and the fill progress is visible while it
runs.

Covers:
- feat~automatic-fair-assignment~1

### Scoped solving
`req~scoped-solving~1`

The user chooses what a solve may touch: all non-pinned slots of the whole board, only the open
slots of services in a date window (optionally including already-assigned ones), or only one
service's open slots. Slots outside the scope keep their assignments unchanged.

Covers:
- feat~manual-plan-control~1

### Violations are explained per slot
`req~violation-display~1`

For any filled slot the user can see which rule it breaks, named in the application language.

Covers:
- feat~manual-plan-control~1

## Design

### Planning entity and solution
`dsn~planning-model~1`

`Assignment` (`@PlanningEntity`) is one role slot of one service: `@PlanningId` id built from
`AssignmentKey(serviceId, slotId)`, the service and role as facts, a `@PlanningVariable(allowsUnassigned
= true)` `Server`, and a `@PlanningPin` flag. `ServicePlan` (`@PlanningSolution`) holds the active
servers as the value range, the assignments, the read-only `PriorAssignment` facts, the constraint
weight overrides and a `HardMediumSoftScore`. Both are mutable classes by Timefold's requirement —
the only non-record model in core.

Covers:
- req~hard-rules~1
- req~partial-plans~1

### Score levels
`dsn~score-levels~1`

`HardMediumSoftScore`: **hard** = rule violation (never acceptable), **medium** = unassigned slot
(allowed, strongly discouraged), **soft** = plan quality. The three levels are what makes an
over-constrained horizon produce the best *partial* plan instead of a rule-breaking full one.

Covers:
- req~partial-plans~1

### Hard constraints
`dsn~hard-constraints~1`

`MinDisConstraintProvider` penalizes one hard point each for:

| Constraint name (also the l10n key) | Rule |
|---|---|
| `Server not qualified for role` | the server's `qualifications` lack the slot's role id |
| `Server unavailable` | the service's date falls in an unavailability period |
| `Server inactive` | the server is flagged inactive |
| `Server double-booked` | the same server on two overlapping `[start, start+duration)` intervals — identical times overlap, so two slots of one service are covered too |

`Slot unassigned` penalizes one medium point per unassigned slot (the only constraint built on
`forEachIncludingUnassigned`).

Covers:
- req~hard-rules~1
- req~partial-plans~1

### Soft constraints and default weights
`dsn~soft-constraints~1`

| Constraint | Effect | Default weight |
|---|---|---|
| `Unbalanced workload` | penalizes *count²* per server, so load spreads out | 2 |
| `Siblings serve together` | rewards each pair of same-`familyId` servers in one service | 5 |
| `Assignments too close together` | penalizes two assignments of one server on days ≤ `SPACING_THRESHOLD_DAYS` (= 1) apart, different services | 3 |
| `Too close to previous plan` | same rule against archived history — see `dsn~prior-plan-facts~1` | 3 |
| `Preferred service time` | rewards a service starting at one of the server's preferred times | 2 |
| `Experienced server present` | rewards each service having at least one experienced server | 4 |
| `Server age outside role range` | penalizes a server outside the role's min/max age; unknown birth date is never penalized | 4 |

`tunableSoftConstraints()` lists them in display order and `defaultSoftWeights()` supplies the
defaults — one source for both the preferences UI and the solver. Unit-tested by
`MinDisConstraintProviderTest`.

Covers:
- req~plan-quality~1
- req~tunable-weights~1

### Weight overrides from preferences
`dsn~weight-overrides~1`

`PlanningService.buildProblem` reads `MinDisPreferences.softConstraintWeights()` and installs them
as Timefold `ConstraintWeightOverrides` on the solution, per solve. Only soft weights are
overridable; hard and medium weights are fixed.

Covers:
- req~tunable-weights~1

### Prior-plan facts
`dsn~prior-plan-facts~1`

`PriorAssignment(date, server)` is a read-only problem fact, never a planning entity. A solve is
confined to its own services, so without these facts the solver could not be penalized for
scheduling a server the day after a previously planned period ended.
`PlanningService.priorFromArchived(earliest)` loads exactly the `SPACING_THRESHOLD_DAYS` tail before
the earliest live service from the archive, skipping servers that no longer exist. It is a separate
constraint from `Assignments too close together` (a join, not a `forEachUniquePair`), hence its own
id and its own tunable weight.

Covers:
- req~prior-plan-spacing~1

### Problem building and write-back
`dsn~problem-writeback~1`

`PlanningService.buildProblem()` creates one `Assignment` per slot of every live service,
pre-populated from the slot's own stored `serverId`/`pinned` (a pin only survives if a server is
actually set), with all *active* servers as the value range; a slot referencing a deleted role is
skipped. `writeBack(solved, services)` is pure: it returns new `LiturgicalService` records whose
slots carry the solved server and pin, which the caller stages into the live store — persisted by
the ordinary global Save all. Unit-tested by `PlanningServiceTest` and `PlanningEndToEndTest`.

Covers:
- req~scoped-solving~1

### Autofill scoping by pinning
`dsn~autofill-scoping~1`

`Autofill` is pure pin-juggling — the single mechanism behind every scoped solve.
`Autofill.begin(plan, eligible)` snapshots each assignment's pin, leaves the eligible ones free and
pins everything else, returning a `Scope`. `Autofill.finish(solved, scope)` restores the original
pin of every non-eligible assignment, and pins each eligible slot the solver filled (the planner
asked for this fill as deliberately as a manual pick); an eligible slot left empty stays unpinned.
Eligibility predicates: `within(from, to, overwrite)` for the windowed run (blank bound = unbounded)
and `forService(serviceId, overwrite)` for a single service. Unit-tested by `AutofillTest`.

Covers:
- req~scoped-solving~1

### Solve orchestration
`dsn~solve-orchestration~1`

`PlanningService.solveAsync(problem, timeBudget, best, final, exception)` runs on a Timefold
`SolverManager` and returns a job id for `stopSolving(jobId)`. Termination always couples two
limits: the time budget *and* a 5-second unimproved cutoff — built together in `terminationWithin`,
because a `SolverConfigOverride` replaces the whole `TerminationConfig` rather than merging it. The
budget is `MinDisPreferences.solverSecondsLimit` (default 30 s) for a board-wide or windowed solve,
and a fixed 5 s for a single service's auto-fill, which is a far smaller problem. Core stays
UI-agnostic: results arrive on plain `Consumer`s (on the solver thread) and the GUI marshals them
onto the FX thread.

Covers:
- req~bounded-solve-time~1

### Solve controls
`dsn~solve-controls~1`

While a solve runs, the Autofill button is swapped in place for a progress bar bound to
`PlanningViewModel.solveProgressProperty()` — the filled/total slot fraction, recomputed from every
improved solution — which doubles as the abort control; the abort prompt auto-dismisses if the solve
finishes first, so a just-completed solve is never cancelled by a stale click. The global Save all
stays disabled while `solvingProperty()` is true. The solve popup carries the From/To bounds and the
"overwrite already-assigned slots" toggle; "Solve all" ignores the bounds and re-solves every
non-pinned slot.

Covers:
- req~bounded-solve-time~1
- req~scoped-solving~1

### Violation checker
`dsn~violation-checker~1`

`ViolationChecker.violationsByAssignment(plan)` mirrors the hard and medium constraints in plain
Java and returns assignment id → violated constraint names, which are the same full-text
localization keys the constraint provider uses. It exists because Timefold's
`SolutionManager.analyze()` is an enterprise-only feature; `PlanningService.scoreOf` similarly uses
`SolutionManager.update`. Kept in sync via the shared name constants and `ViolationCheckerTest`.

Covers:
- req~violation-display~1
