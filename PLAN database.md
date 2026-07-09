# Core in-memory database + cross-module live state + global Save all/Load

## Context

Trigger: creating a Role (unsaved) should immediately show up in Servers' qualification checklist. Today it can't — `RolesModule`'s unsaved edits live only in its own private `CrudModule.items`; `ServersModule` reads roles via a one-shot `viewModel.findAllRoles()` snapshot at editor-build time, from `RoleRepository` (which only reflects the last disk-flushed state, since repo writes are synchronous-to-disk today).

Target architecture: the "database" (in-memory repos) lives in **core** and is the **single source of truth** — mutated immediately by any edit, so every reader (other modules' viewmodels, `PlanningService`, the CSV mappers, `ServiceGenerator`) sees the same live state. A global **Save all** flushes it to disk; a global **Load** discards unflushed edits and reloads from disk. GUI-layer stores/viewmodels sit on top, adding JavaFX observability and GUI-only business logic (dirty tracking, per-row display state). Views bind to those. The core half stays JavaFX-free (already enforced — `mindis-core`'s `module-info.java` has no `javafx.*` requires), so a later non-JavaFX client reuses it as-is.

**Key design decision — write-through, not stage-in-GUI**: an earlier draft kept unsaved edits in a GUI-side store and only pushed them into the repos inside the Save-all handler. That silently breaks the "single source of truth" goal: `PlanningService.findAll()`, `ServersViewModel.roleName()` (the Qualifications table column), all four CSV mappers (each takes `roleRepository`), and `ServiceGenerator` (fed `templateRepository.findAll()`) would keep seeing only last-saved state. Instead, every live edit **writes through to the repo cache immediately** (cache-only — no disk I/O); the GUI store is an observable mirror plus dirty bookkeeping, never a divergent copy.

Accepted consequence: the solver and other repo readers see staged-but-unflushed edits, including a freshly created blank stub (e.g. a new active Server with empty name can enter a solve before it's filled in). That is the intended meaning of "the solver sees the same live database the GUI does."

Out of scope: `PlanRepository`/`ServicesModule`'s "Save plan" (a separate, already-settled concept) — not folded in, but see §7 for the two required touch points (Services' Save-all must also flush the shared DB; global Load must reset Services' plan state).

## Design

### 1. Core repositories: split "stage" from "flush to disk"

`RoleRepository`, `ServerRepository`, `TemplateRepository`, `ServiceRepository` (`mindis-core/src/main/java/org/mindis/core/persistence/`) each already have a private lazily-loaded mutable cache (`cached()`) backed by `JsonStore<T>`. Today `save()`/`delete()`/`saveAll()` mutate the cache **and** call `store.save(cached())` in the same call. Change: mutate only; disk I/O moves to two new explicit methods.

```java
public synchronized void save(Role role) {           // cache-only now, no store.save(...)
    List<Role> list = cached();
    list.removeIf(existing -> existing.id().equals(role.id()));
    list.add(role);
    sort(list);
}
public synchronized void delete(String id) {          // cache-only now
    cached().removeIf(existing -> existing.id().equals(id));
}
public synchronized void saveAll(List<Role> roles) {   // NEW on Role/Server/Template repos, mirrors ServiceRepository's existing one
    roles.forEach(this::save);
}
public synchronized void flush() {                     // NEW — the only disk write path
    store.save(cached());
}
public synchronized void reload() {                    // NEW — discard unflushed mutations
    roles = null;
    cached();
}
```

`findAll()`/`findById()` unchanged (still read `cached()` — now "current staged state", not "always-flushed state"). Repo methods stay `synchronized` and `findAll()` stays `List.copyOf` — the solver thread reads snapshots safely.

**Fix while touching this**: `RoleRepository.cached()` seeds 5 default roles whenever the loaded list is empty. Add `JsonStore.exists()` (`Files.exists(file)`) and seed only `if (!store.exists())` — otherwise `reload()` on a genuinely-emptied roster would silently reseed the defaults, a latent bug this change would otherwise make easier to trigger. Keep the seed path's existing one-time `store.save(roles)` (it now only ever runs when the file doesn't exist, so it's a first-run bootstrap write, not a violation of "flush is the only write path").

### 2. `AppDatabase` — new core aggregator

```java
// mindis-core/src/main/java/org/mindis/core/persistence/AppDatabase.java
@Singleton
public class AppDatabase {
    private final RoleRepository roles;
    private final ServerRepository servers;
    private final TemplateRepository templates;
    private final ServiceRepository services;
    // constructor injects all 4

    public synchronized void saveAll() { roles.flush(); servers.flush(); templates.flush(); services.flush(); }
    public synchronized void loadAll() { roles.reload(); servers.reload(); templates.reload(); services.reload(); }
}
```
`@Singleton`, avaje-managed like the 4 repos it wraps (`beanScope.get(AppDatabase.class)`). `PlanRepository` deliberately excluded.

### 3. `LiveStore<T>` — shared observable mirror over one repo (mindis-workbench)

`mindis-workbench` is already a JavaFX module sitting between core and gui — the right home. This extracts `CrudModule`'s dirty-tracking algorithm (`items`, `savedSnapshots`, `pendingDeletions`, `dirtyCount`, `isDirty`/`recomputeDirtyCount`, `updateLive`, `mergeLive`, `refresh`) into a standalone class, with one behavioral change: every mutation **writes through to the repo** as it updates the observable list.

```java
public final class LiveStore<T> {
    private final ObservableList<T> items = FXCollections.observableArrayList();
    private final Map<Object, T> savedSnapshots = new HashMap<>();   // baseline = last-FLUSHED state
    private final Map<Object, T> pendingDeletions = new HashMap<>(); // dirty-count + display bookkeeping only
    private final ReadOnlyIntegerWrapper dirtyCount = new ReadOnlyIntegerWrapper(0);
    private final List<Runnable> refreshListeners = new ArrayList<>();
    // constructor takes: Supplier<List<T>> loader,     // repo::findAll
    //                    Consumer<T> stage,            // repo::save   (cache-only now)
    //                    Consumer<T> unstage,          // id -> repo.delete(id) (cache-only now)
    //                    Function<T,Object> identity, BiPredicate<T,T> equivalence

    public ObservableList<T> items() { ... }
    public ReadOnlyIntegerProperty dirtyCountProperty() { ... }
    public @Nullable T savedSnapshot(T item) { ... }
    public void updateLive(T updated) { ... }   // items.set(index, updated) + stage.accept(updated); same index-lookup algorithm as today's CrudModule.updateLive, minus the TableView reselect (view concern)
    public void insertFirst(T stub) { ... }     // items.addFirst + stage.accept(stub) — write-through, so the stub is instantly visible to every repo reader (the trigger scenario)
    public void remove(T item) { ... }          // items.remove + unstage.accept(item) + pendingDeletions bookkeeping (only if it has a saved snapshot — a never-flushed row is just dropped)
    public void mergeLive(List<T> incoming) { ... } // same merge algorithm as today, each merged row staged
    public void refresh() { ... }               // reloads items + savedSnapshots baseline from loader.get(), clears pendingDeletions, recomputes dirty, fires refreshListeners
    public Subscription onRefresh(Runnable listener) { ... } // returns a handle to unsubscribe (see §5 lifecycle)
}
```

Since edits write through immediately, there is no `flushToRepo()` — nothing to push at save time. **Save all** = `appDatabase.saveAll()` then `refresh()` on each store (re-baselines snapshots against the now-flushed state, clears dirty counts and `:crud-new`). **Load** = `appDatabase.loadAll()` then `refresh()` (repo cache reverted, mirror follows).

Dirty semantics unchanged from today's `CrudModule`: a row is dirty when it differs (per `equivalence`) from its snapshot or has no snapshot; `pendingDeletions.size()` adds queued deletions. Only the *baseline* moved: "last persisted" now means "last flushed to disk", refreshed only by `refresh()`.

**Critical property**: one `LiveStore<T>` instance per entity type, constructed **once in `MinDisApp.start()`**, directly over the avaje repos — not per `buildWorkbench()` call, and not wired through module subclass hooks (modules don't exist yet at store-construction time, and are recreated on every locale change). `rebuildUi()` must reuse the same store instances so unsaved cross-module edits and dirty counts survive a language switch. `identity`/`equivalence` come from small static functions passed at construction (`Role::id`, `RoleSlotsEditor.sameSlots`-based service/template equivalence — `MinDisApp` is in mindis-gui, so it can reference them; expose `sameSlots` or move the two `isEquivalent` bodies to static helpers as needed).

### 4. `CrudModule<T>` — thin JavaFX view shell over a `LiveStore<T>`

Constructor gains a `LiveStore<T> store` parameter; the four private state fields move out. Abstract hooks `loadAll()`/`persist()`/`persistAll()`/`delete()`/`identity()`/`isEquivalent()` **are deleted** — the store owns all of that (wired to repos in `MinDisApp`), so the subclass overrides and the corresponding viewmodel pass-throughs (`RolesViewModel.save/delete`, etc.) go too. Remaining subclass hooks: `createStub()` (feeds `store.insertFirst`), `buildEditor(T)`, and a new optional `onActivate()` (see below).

- `table.setItems(store.items())` — was the private `items` list.
- `updateLive`/`mergeLive`: `CrudModule` keeps thin wrappers that set `suppressEditorRebuild`, call the store, and do the reselect-by-identity/index dance (`TableView` treats `set` as remove+add) — selection is a view concern, the algorithm itself lives in the store.
- `savedSnapshot(item)`, `dirtyCountProperty()` delegate to the store.
- The `:crud-new` row factory keys off `store.savedSnapshot(rowItem) == null` instead of the private map.
- CSV import/export unchanged in shape (`exportCsv` iterates `store.items()`, `importCsv` → `store.mergeLive`).

**Critical fix the earlier draft missed — `activate()` must stop calling `refresh()`** (today: `CrudModule.activate()` → `refresh()` on every tab switch). Under a shared store, that would re-baseline `savedSnapshots` against the *staged* repo state on every activation — wiping dirty counts, `:crud-new` styling, and disabling Save all while the repo still differs from disk. With write-through there is also nothing to reload: the table already mirrors the repo. `activate()` returns the (cached) view; `store.refresh()` runs only from global Save all/Load (and Services' own, §7). Replace the `refresh()` call with a new no-op `protected void onActivate() { }` hook so subclasses keep a per-activation entry point.

`ServicesModule` currently piggybacks `rebuildCurrentPlan()` inside its `loadAll()` override (which dies): move it to `onActivate()` — preserving today's "reactivating the tab rebuilds the same plan preserving assignments" behavior — plus a `store.onRefresh(...)` subscription for the Save/Load paths (§7).

### 5. Owning vs. consuming modules

- **Owning** (`RolesModule`, `ServersModule` for `Server`, `TemplatesModule`, `ServicesModule`): constructor takes the relevant `LiveStore<T>`, passes it to `super(...)`. Repo constructor params and viewmodel CRUD pass-throughs are dropped (kept only where still needed: `nextSortOrder` for the Role stub, `familyIds()`, `roleName()`, `generateFromTemplates` — all read the live repo cache and now see staged edits for free).
- **Consuming** (`ServersModule` reading Roles for its qualification checklist; `TemplatesModule`/`ServicesModule` reading Roles for `RoleSlotsEditor`): also receive the shared `LiveStore<Role>` as an extra constructor parameter, for *change notification* — the data itself they can keep reading via `findAllRoles()` (same staged cache).
  - `ServersModule` checklist: point `qualificationsList`'s `ListView` straight at `roleStore.items()` (same instance, not a copy) — list-change events make new/edited/deleted roles appear with no extra listener code. **Bug the earlier draft missed**: the `pushLive` wiring only attaches listeners to the `qualificationSelected` properties that exist at editor-build time; a property lazily created by the cell factory's `computeIfAbsent` (i.e. for a role added *after* the editor opened) has no listener — the new role would render and be checkable, but checking it would never update the server. Fix: attach the listener inside the `computeIfAbsent` mapping function (requires the cell factory to be built after `pushLive` exists, or the same `Runnable[]`-holder trick already used in `ServicesModule`).
  - `ServersModule` table: the Qualifications column resolves names via `viewModel.roleName()` — live data now, but `TableView` cells don't recompute until repainted. Add a `roleStore.items()` listener calling `table().refresh()` so an unsaved role rename shows in the Servers table too. A role deleted while checked leaves its id in `server.qualifications()` — matches today's existing "unknown role id shows raw id" fallback, no new handling.
  - `TemplatesModule`/`ServicesModule`: `RoleSlotsEditor` builds imperative rows, not `ObservableList`-bound — add a `roleStore.items()` listener in each module's constructor that calls `refreshSelectedEditor()` (exists in `ServicesModule`; add the same small helper to `TemplatesModule`) to rebuild the currently-open editor when the roles list changes. Rebuilding drops in-progress spinner focus but preserves values (`collectSlots()` is re-seeded from the live item) — acceptable.

**Listener lifecycle (locale changes)**: `rebuildUi()` recreates every module but the stores (and their `ObservableList`s) live forever. Plain listeners added by a module would keep the old module graph reachable and fire `refreshSelectedEditor()`/`table().refresh()` on detached UI after every language switch. Register all module-added store listeners as `WeakListChangeListener`/weak-`Runnable` equivalents, keeping the strong reference in a module field (standard JavaFX weak-listener pattern), or add a `dispose()` to `WorkbenchModule` that `MinDisApp.rebuildUi()` invokes on the old module set before replacing it. Weak listeners are less machinery; `dispose()` is more deterministic — either is fine, pick one and apply it to *all* cross-object subscriptions added in module constructors (including `ServicesModule`'s `store.onRefresh`). Old `TableView`s bound to `store.items()` detach naturally once the old scene graph is garbage (JavaFX registers its content bindings weakly), so `setItems` needs no special handling.

### 6. Global Save all / Load — `Workbench`'s empty `setTop(...)`

No `Workbench` class changes needed (`setTop` is inherited `BorderPane` API; verified free — sidebar sits in `setLeft`, content in `setCenter`). `MinDisApp` builds the `ToolBar` with two buttons inside `buildWorkbench()` (or a helper called from both `start()` and `rebuildUi()`) — built **fresh each time**, not reused, because the button labels are localized; the stores it binds to are the long-lived ones.

```java
saveAllButton.disableProperty().bind(
        roleStore.dirtyCountProperty().add(serverStore.dirtyCountProperty())
                .add(templateStore.dirtyCountProperty()).add(serviceStore.dirtyCountProperty())
                .isEqualTo(0));
saveAllButton.setOnAction(e -> {
    appDatabase.saveAll();                                  // repos already hold every staged edit (write-through)
    stores.forEach(LiveStore::refresh);                     // re-baseline; fires onRefresh listeners
});
loadAllButton.setOnAction(e -> {
    appDatabase.loadAll();
    stores.forEach(LiveStore::refresh);
});
```

**Remove** the per-module Load/Save-all buttons just added to `RolesModule`, `ServersModule`, `TemplatesModule` — under a shared database, a per-module "Save all" can't honestly mean "just this module" (`AppDatabase.saveAll()` always flushes all 4 files), and a per-module "Load" would silently discard every module's unsaved edits. One global flush point, one global reload point.

### 7. `ServicesModule` / plan coupling

**Keep** `ServicesModule`'s own Load/Save-all — plan-coupled (`onSaveAll()`/`onLoad()` also handle `PlanRepository` state, out of scope to remove). Two adjustments:

- `ServicesModule` gains an `AppDatabase` dependency. Its `onSaveAll()` becomes: `appDatabase.saveAll()` + `store.refresh()` + the existing plan-save half — so clicking Services' Save all still genuinely writes everything to disk (repo `save()` no longer self-flushes), consistent with "Save all" always means "flush the shared database". Its `onLoad()` becomes `appDatabase.loadAll()` + `store.refresh()` + the existing plan-reset half (`currentPlan = null`, `reloadSavedPlanSnapshot()`, `rebuildCurrentPlan()`).
- **Global Load must reset Services' plan state too** — otherwise `currentPlan` keeps `Assignment` objects referencing discarded service/roster edits. This is what `store.onRefresh(...)` is for: `ServicesModule` subscribes on its service store and runs the same plan-reset-or-rebuild it does in `onLoad()` (distinguishing "refresh after save" from "refresh after load" isn't necessary if the handler does `reloadSavedPlanSnapshot()` + `rebuildCurrentPlan()` — after a save that's a cheap no-op rebuild preserving assignments; after a load it correctly rebuilds against reverted data. Only the `currentPlan = null` hard reset stays exclusive to the explicit Load actions).

### 8. Migration note: `PlanningService`

`PlanningService` (`mindis-core/.../planning/PlanningService.java`) reads `serverRepository.findAll()`/`serviceRepository.findAll()`/`roleRepository.findAll()` at solve-build time — API-compatible as-is. With write-through it now genuinely sees staged edits (including blank just-created stubs, see Context) — intended, flagged, no code change.

## Verification

1. Open a Servers editor (qualifications list visible). Without closing it, go to Roles, click New, type a name, don't save. Return to Servers — the new role appears in the checklist immediately; **check it and confirm the server row's Qualifications column updates** (exercises the `computeIfAbsent` listener fix). Edit an existing role's name (unsaved) — checklist label and Servers table column both update live.
2. With a Templates or Services editor open (`RoleSlotsEditor` visible), create a role in Roles (unsaved) — a spinner row for it appears in the open editor without losing already-entered counts for other roles.
3. Make one unsaved edit in each of Roles/Servers/Templates/Services, then **switch tabs away and back on each** — dirty counts, `:crud-new` styling and the global Save-all enablement must survive tab switches (exercises the `activate()`-no-longer-refreshes fix). Then click global Save all: all 4 JSON files on disk reflect the edits, every dirty count clears.
4. Repeat step 3's setup, click global Load instead. Every table reverts to last-flushed content, dirty counts hit 0, a role created+checked live disappears from both Roles' table and Servers' checklist with no orphaned selection, and an open Services editor shows assignments consistent with the reverted data (plan reset via `onRefresh`).
5. Repeat step 1's setup, trigger a language change (Settings). The unsaved role is still visible in Roles' table and in the open Servers checklist after the rebuild (stores built once in `start()`). Switch languages twice more and confirm role edits still refresh exactly one Services/Templates editor (no stale-listener double-rebuilds — exercises the weak-listener/dispose choice).
6. With an unsaved assignment pick and an unsaved service field edit, click Services' own Save all. Both the service record and the plan persist, and other modules' pending edits are flushed too (by design, §7).
7. Run a solve with an unsaved server-qualification edit in place — the solver respects the staged qualification (write-through visible to `PlanningService`).
8. Empty the roles list, global Save all, then global Load — roles stay empty (no default reseed; exercises `JsonStore.exists()` fix).

## Critical files
- `mindis-core/src/main/java/org/mindis/core/persistence/{RoleRepository,ServerRepository,TemplateRepository,ServiceRepository}.java` — split save/delete into stage-only + new `flush()`/`reload()`; add `saveAll()` to the 3 missing it
- `mindis-core/src/main/java/org/mindis/core/persistence/JsonStore.java` — add `exists()`
- `mindis-core/src/main/java/org/mindis/core/persistence/AppDatabase.java` (new)
- `mindis-workbench/src/main/java/org/mindis/workbench/LiveStore.java` (new — write-through observable mirror + dirty tracking + `onRefresh`)
- `mindis-workbench/src/main/java/org/mindis/workbench/CrudModule.java` — constructor takes `LiveStore<T>`; delete `loadAll`/`persist`/`persistAll`/`delete`/`identity`/`isEquivalent` hooks; `activate()` stops refreshing, gains `onActivate()` hook
- `mindis-workbench/src/main/java/org/mindis/workbench/WorkbenchModule.java` — only if the `dispose()` option is chosen for listener cleanup
- `mindis-gui/src/main/java/org/mindis/gui/MinDisApp.java` — builds the 4 `LiveStore`s over the avaje repos + gets `AppDatabase` in `start()`, passes stores into `buildWorkbench()`, builds the localized global toolbar per workbench build; disposes old modules if that option is chosen
- `mindis-gui/src/main/java/org/mindis/gui/modules/{RolesModule,ServersModule,TemplatesModule,ServicesModule}.java` — take `LiveStore` param(s); drop CRUD overrides; `ServersModule` checklist binds to shared role list + `computeIfAbsent` listener fix + table-refresh listener; `TemplatesModule` gains `refreshSelectedEditor`; remove per-module Load/Save-all from Roles/Servers/Templates; `ServicesModule` keeps its own, gains `AppDatabase`, moves plan rebuild to `onActivate()` + `onRefresh`
- `mindis-gui/src/main/java/org/mindis/gui/modules/{RolesViewModel,ServersViewModel,TemplatesViewModel,ServicesViewModel}.java` — drop CRUD pass-throughs; keep the read/compute helpers
