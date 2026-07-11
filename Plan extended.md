# Extended findings from SOLID / Effective Java / good-practices audit

Source: full-codebase audit (SOLID, Effective Java 3rd ed., general engineering
hygiene) run across `mindis-core`, `mindis-gui`, `mindis-workbench`,
`native-spike`. Four findings selected for follow-up; ranked by the order
they'll be worked.

## 1. `ServicesModule` violates MVVM — state and business logic sit in the View

**Status: done.**

`PlanningViewModel` (`mindis-gui/src/main/java/org/mindis/gui/planning/PlanningViewModel.java`)
already exists and already owns every call into `PlanningService`/
`PlanRepository`/`PlanExportService` - but it's stateless: every method takes
a `ServicePlan` in and returns one out. Meanwhile `ServicesModule` (the View)
holds all the actual plan state (`currentPlan`, `hasPlan`, `planDirty`,
`savedPlanSnapshot`, `solving`, `liveAssignments`) *and* the logic that
mutates it (`rebuildCurrentPlan`, `recomputePlanDirty`,
`resolvePinnedAfterManualPick`, `scheduleRebuild`, `onSolveAll`/
`onAutoFillService`'s orchestration, `saveAll`'s plan half,
`isAssignmentsDirtyFor`/`meaningfulAssignments`/`filterByService`). That's a
View owning ViewModel responsibilities - this codebase's own established
convention (`ServicesViewModel`, `PlanningViewModel`, `RolesViewModel`,
`ServersViewModel`, `TemplatesViewModel`, `DashboardViewModel` all already
exist, one per module) makes the correct home obvious: move the state onto
`PlanningViewModel` as JavaFX properties, and the logic onto its methods.
`ServicesModule` keeps only widget construction, tile/assignment-row
rendering (reading the ViewModel's properties), and button-to-ViewModel-method
wiring.

Target property/method surface on `PlanningViewModel`:
- `ObjectProperty<@Nullable ServicePlan> currentPlanProperty()`
- `ReadOnlyBooleanProperty hasPlanProperty()`
- `ReadOnlyBooleanProperty planDirtyProperty()`
- `BooleanProperty solvingProperty()`
- `ObservableList<Assignment> liveAssignments()`
- `rebuildForRange(LocalDate from, LocalDate to)`
- `recomputeDirty()`
- `pick(Assignment, @Nullable Server)` (wraps `resolvePinnedAfterManualPick` + dirty recompute)
- `solveAll(...)` / `autoFill(service, ...)` (orchestration; solver callbacks still fire off the FX thread, so `Platform.runLater` marshaling stays a View-side concern)
- `save()` (plan half of `ServicesModule.saveAll()`)
- `scheduleRebuild(boolean reloadSnapshot)`

`savedPlanSnapshot` stays a private field on the ViewModel (implementation detail, no View needs it directly).

## 2. Stringly-typed assignment id (Effective Java Item 62)

**Status: done.**

`service.id() + ":" + slot.id()` is hand-built independently in 4 places
(`PlanningService.java:97`, `ServicesModule.java:843,937,1074`) and parsed by
prefix elsewhere (`filterByService`'s `id.startsWith(service.id()+":")`). No
shared constant for the separator, no parse helper - four hand-written
constructions of one format. A colon appearing in a future id scheme, or one
call site drifting to a different separator, silently breaks assignment
matching with no compiler signal.

Fix: a small `record AssignmentKey(String serviceId, String slotId)` with
`toId()`/`parse(String)`, used at all four call sites (and inside
`filterByService`/`isAssignmentsDirtyFor` for the prefix check, via
`AssignmentKey.serviceId()` comparison instead of string prefix matching).
Medium cost.

## 3. No test coverage for the shared engine or the recent slot-reconciliation fix

**Status: done** (LiveStore + the reconciliation algorithm; PlanningViewModel's
solve/save orchestration still untested - it needs a real `PlanningService`,
which spins up a Timefold `SolverManager` in its constructor, so testing it
means either a fake/seam for `PlanningService` or accepting integration-only
coverage; deferred, not attempted here).

`mindis-gui/src/test` has 3 files (locale/preferences only); `mindis-workbench`
has none. `LiveStore<T>` is the shared write-through/dirty-tracking engine
behind all four CRUD modules - a bug there silently corrupts every screen.
`ServicesModule.reconcileSlots()` (the "drop the unfilled slot first" fix)
and `scheduleRebuild()` (the rebuild-coalescing fix) are both subtle-invariant
logic that regresses silently without a test pinning the behavior.

Fix: focused unit tests for `LiveStore` (mindis-workbench, plain data, no
JavaFX toolkit needed) and for `reconcileSlots`/`scheduleRebuild`'s observable
effects (mindis-gui, may need TestFX or can test the pure
`reconcileSlots`/`isSlotFilled` logic in isolation if extracted to not require
a live `ServicesModule` instance). Medium cost.

## 4. Raw single-element arrays for lambda-captured mutable state

**Status: done** (only the one problem site - `List<Slot>[] liveSlotsHolder`,
which needed `@SuppressWarnings("unchecked")` - was converted to `Mutable<T>`.
The non-generic holders (`Region[]`, `Runnable[]`, `boolean[]`, 8 sites across
Roles/Servers/Templates/Services) were left as-is: they hit no compiler
warning and are an established, harmless idiom repeated consistently: not
worth the churn of converting call sites that have no actual problem.)

`Region[] slotsListHolder`, `Runnable[] pushLiveHolder`,
`List<Slot>[] liveSlotsHolder` (needs `@SuppressWarnings("unchecked")`),
`boolean[] suppressPushLive` - four in `ServicesModule.buildEditor` alone,
echoed in Roles/Servers/Templates modules. Deliberate, documented workaround
(avoids `AtomicReference.get()`'s JSpecify `@Nullable` return tripping the
project's zero-warning NullAway baseline), legitimate given the constraint -
but `List<Slot>[]` specifically needs a cast suppression the non-generic
holders don't.

Fix: a tiny package-private `Mutable<T>` value class (one field, no array, no
generics-in-array issue) in `org.mindis.workbench` or a `gui.util` package,
used wherever a lambda needs to capture and reassign a value. Removes the one
unchecked-cast suppression and reads more clearly than a 1-element array.
Small cost, lowest priority of the four.
