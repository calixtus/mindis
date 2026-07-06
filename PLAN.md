# MinDis — Minister Dispatcher

Implementation plan for a JavaFX desktop application that plans altar server (ministrant)
assignments for a Catholic parish. This document is written so that a developer **or another
coding agent** can execute it milestone by milestone.

---

## 1. Product Overview

MinDis manages a parish's altar servers and liturgical services, and automatically computes
fair, constraint-respecting serving schedules.

Core use cases:

1. Maintain a roster of altar servers (names, contact, family/siblings, qualifications, availability).
2. Maintain liturgical services (Sunday/weekday masses, feasts, weddings, funerals) with required
   roles and headcounts.
3. Automatically generate an assignment plan for a planning horizon (e.g. one month) using
   Timefold Solver, respecting hard rules and optimizing soft preferences.
4. Manually pin/override assignments and re-solve around them.
5. Export/print the resulting plan (PDF, later e-mail).

The application is **multilingual from the start** (German + English; parish context) — see §2.3.

---

## 2. Technology Stack (pinned versions — verify at implementation time)

| Concern              | Choice                                   | Version (as of 2026-07) | Notes |
|----------------------|------------------------------------------|-------------------------|-------|
| Language / JDK       | Java (LTS)                               | 25                      | Toolchain-managed via Gradle; GraalVM toolchain only in M7 |
| UI toolkit           | JavaFX                                   | 26.0.1                  | Module path; platform jars via `org.openjfx.javafxplugin` (no Gradle metadata upstream) |
| Window/shell UI      | **`mindis-workbench` — in-repo fork of [WorkbenchFX](https://github.com/dlsc-software-consulting-gmbh/WorkbenchFX)** | forked from 11.3.1 | Apache-2.0, attribution kept (see §4.1). No external workbench dependency. |
| Theme                | [AtlantaFX](https://mkpaz.github.io/atlantafx/) `io.github.mkpaz:atlantafx-base` | 2.x | Proper JPMS module `atlantafx.base` |
| View layer           | **[FxmlKit](https://github.com/dlsc-software-consulting-gmbh/FxmlKit)** `com.dlsc.fxmlkit:fxmlkit` | 1.5.1 | Standard FXML at runtime, convention wiring, hot reload. **Sole view mechanism** — see §2.1. |
| Dependency injection | [Avaje Inject](https://avaje.io/inject/) `io.avaje:avaje-inject` (+ `avaje-inject-generator` APT) | 12.6 | Compile-time DI: generated wiring, **zero runtime reflection** (§2.2-safe). Plugged into FxmlKit's DI hook — see §2.4. |
| Planning engine      | [Timefold Solver](https://timefold.ai/solver) `ai.timefold.solver:timefold-solver-core` | 2.x | JPMS-supported since 2.0 |
| Build system         | Gradle (Kotlin DSL) + [GradleX](https://gradlex.org/) plugins | Gradle 9.x | JabRef-style setup (see §5) |
| Native compilation   | GraalVM Native Image via GluonFX Gradle plugin (or `org.graalvm.buildtools.native` + Gluon static JavaFX libs) | latest | **Final milestone (M7) only.** Until then: just don't block it (§2.2) |
| Persistence          | Jackson (JSON files in user data dir)    | 2.x                     | Simple start; DB later if needed |
| Logging              | SLF4J + Logback                          | latest                  | |
| Code style           | JabRef code style, enforced via Checkstyle | —                     | See §8 |
| Testing              | JUnit 5, TestFX (UI), Timefold test API  | latest                  | `org.gradlex.java-module-testing` |

### 2.1 View layer decision: FxmlKit, with FXML/2 parked for later

FxmlKit is the view layer. FXML/2 was considered as a replacement (compile-time views, no
reflection) but **rejected for now because it is pre-1.0** (0.14.0) — a pre-1.0 dialect compiler
under the entire UI is foundation risk: breaking changes, small community, unknown JavaFX 25 /
JPMS / resource-binding edge cases. FxmlKit wins on maturity and ecosystem:

1. **Standard FXML** — SceneBuilder works, every JavaFX FXML resource applies.
2. **Hot reload** of FXML + CSS — fastest UI iteration loop for the UI-heavy milestones M1–M5.
3. **Convention wiring** (view ↔ fxml ↔ controller by name) with optional DI hook — controllers
   are resolved from the Avaje Inject `BeanScope` (§2.4), so views get services via constructor
   injection.
4. Actively maintained (1.5.1, 2026), same vendor as the workbench code we fork.

Accepted cost: runtime `FXMLLoader` = reflection in the view layer (§2.2 consumer #3) →
reachability metadata for every view in M7. Acceptable: tracing agent generates it, and M7 is
optional anyway (jpackage ships in M6).

`docs/adr/001-view-layer.md` (written in M0) records this decision and parks **FXML/2**
(`org.jfxcore.fxmlplugin`) as the future alternative, with revisit triggers:

1. FXML/2 reaches a stable 1.x with a compatibility story.
2. M7 native-image FXML metadata churn becomes chronic (FXML/2 would eliminate it).
3. FxmlKit abandonment or blocking bugs.

**M0 spike (blocking, cheap):** hello-world FxmlKit view — JPMS module path, JavaFX 25,
`%`-resource binding with full-text keys (§2.3), hot reload. Problems → resolve or escalate
before M1.

### 2.2 Don't-block-native rules (coding style only — no native tooling before M7)

Native image work happens **exclusively in M7**, the very last milestone. No metadata files, no
tracing-agent tasks, no native CI jobs before that. During M0–M6 the only obligation is to avoid
patterns that would make M7 hard, because GraalVM Native Image is a closed-world AOT compiler:

1. **No dynamic classloading / `Class.forName` on computed names.** Service lookup via explicit
   registration, not classpath scanning.
2. **Keep reflection confined to three known consumers:** JavaFX `FXMLLoader` (via FxmlKit,
   §2.1), Jackson, and Timefold. Nothing else may reflect — no hand-rolled reflective utilities.
3. **Prefer reflection-free alternatives where cheap:** Jackson with explicit
   `@JsonCreator`/records; Timefold constraint streams (no drools); avoid
   `java.util.ServiceLoader` where a direct call works.
4. **No runtime code generation, no dynamic proxies.** Build-time code generation is allowed
   only via the approved annotation processor (Avaje Inject, §2.4) — it emits plain Java that
   AOT-compiles like hand-written code.

Everything else (reachability metadata, tracing agent, GluonFX wiring, native CI) is M7 scope.
jpackage/JLink is the primary shipping path until native image proves itself there.

### 2.3 Localization: full-text keys, JabRef style

The app is multilingual (initially `en`, `de`). **The translation key is the full English
text**, never an abstract key — both in code and in markup. This follows JabRef's
`Localization.lang(...)` pattern:

```java
// yes
saveButton.setText(Localization.lang("Save plan"));
statusLabel.setText(Localization.lang("%0 of %1 slots assigned", assigned, total));

// no
saveButton.setText(bundle.getString("planning.toolbar.save"));
```

Implementation (in `mindis-core` or a tiny `org.mindis.l10n` package in gui):

- `Localization.lang(String englishText, Object... params)` — looks up the English text in the
  current locale's bundle; **falls back to the English text itself** when no translation exists,
  so the UI never shows raw keys.
- Bundles: `MinDis_en.properties` (identity mapping, generated/checked), `MinDis_de.properties`
  (English text as key — escape spaces/`=`/`:` per properties format, exactly as JabRef does).
- Positional placeholders `%0`, `%1` (JabRef convention) rather than `MessageFormat` quirks.
- A build check (Gradle task, like JabRef's localization tests) verifies: every
  `Localization.lang("...")` literal exists in the bundles, no unused/duplicate entries,
  parameter counts match.
- FXML: standard `%`-resource binding with full-text keys (`text="%Save plan"`) — exactly what
  JabRef does in its FXML files; verify FxmlKit passes the bundle through in the M0 spike (§2.1).
  Fallback: set strings from the controller via `Localization.lang(...)`.
- Rule from M1 on: **no hardcoded user-visible string** outside `Localization.lang(...)` calls.
  Locale switchable in Settings at runtime.
- `Localization` lives in **`mindis-core`** — a future web module (§2.5) reuses it unchanged.

### 2.4 Dependency injection: Avaje Inject, plugged into FxmlKit

[Avaje Inject](https://avaje.io/inject/) is the DI framework from M0 on. Chosen because it is
the only mainstream option that satisfies §2.2: wiring is generated by an annotation processor
at compile time (plain Java source — inspectable, debuggable), **no reflection, no classpath
scanning, no dynamic proxies at runtime**; JPMS-friendly (module `io.avaje.inject`); tiny
runtime footprint; native-image safe by construction.

Usage pattern:

- Services, repositories, `PlanningService`, `Localization`-backed helpers: `@Singleton`
  components with constructor injection. External/config-driven objects via `@Factory` +
  `@Bean` methods.
- One `BeanScope` created in `MinDisApp.start()`; closed on shutdown (`AutoCloseable` beans get
  lifecycle for free).
- **FxmlKit bridge:** FxmlKit's DI hook is a controller-factory callback — implement it as
  `beanScope.get(controllerClass)`, registered once at startup. Controllers are then plain
  `@Singleton`/`@Prototype` beans with constructor injection; FXML wiring stays FxmlKit's job.
- Each JPMS module with beans declares the processor; `module-info` gets
  `requires io.avaje.inject;`. Verify processor-on-module-path setup in the M0 spike.
- Tests: `avaje-inject-test` for scope-per-test with mock overrides (`@InjectTest`).
- Scope discipline: beans live in `mindis-core` (services) and `mindis-gui` (controllers,
  view models). **No JavaFX types in core beans** (§2.5).

### 2.5 Web-ready architecture: UI-agnostic core, future `mindis-web` module

A browser-based UI is a **future option, not current scope**. Nothing web-related is built in
M0–M7 — but the module cut is chosen now so a web module can be added later without touching
`mindis-core`:

1. **`mindis-core` must not depend on JavaFX.** No `javafx.*` import anywhere in core —
   enforced by `module-info` (core simply doesn't `require` any `javafx.*` module) and
   Checkstyle `IllegalImport`. Domain model uses plain Java/records — **no JavaFX properties in
   domain classes**; `mindis-gui` wraps domain objects in its own observable view models.
2. **All business capability lives in core:** domain, validation, repositories, solver
   (`PlanningService`), localization, PDF export. UI modules are thin adapters over core
   services. Litmus test: *a CLI could be written against `mindis-core` alone.*
3. **Async API shape:** core services expose plain types, `CompletableFuture`/callbacks or
   listener interfaces — not JavaFX `ObservableValue`. GUI adapts these onto the FX thread;
   a web module would adapt them onto HTTP/WebSocket.
4. **Future `mindis-web`** (when it comes): own JPMS module next to `mindis-gui`, same
   pattern — `requires org.mindis.core`, Avaje Inject reused server-side (it is a general DI
   container, not FX-bound). Candidate stacks recorded in `docs/adr/003-web-ui-path.md`
   (seeded in M0, decision deferred): lightweight Java server (Javalin/Helidon SE) + HTMX or
   REST+SPA; alternatively JPro (JavaFX-in-browser) as low-effort bridge reusing `mindis-gui`.
5. Persistence note: JSON-file store is single-user desktop scope. ADR-003 must revisit storage
   (server DB, multi-user, auth) — another reason repositories stay behind core interfaces.

### 2.6 Preferences: own JSON store in core (no framework)

User-editable settings (locale, theme, data directory, solver time budget, constraint weights,
window geometry) are handled by a small core-owned mechanism — **no preferences framework**:

- `org.mindis.core.preferences`: immutable record `MinDisPreferences` (all settings, sensible
  defaults) + `PreferencesService` (Avaje `@Singleton`): load on first access, `update(...)`
  with atomic write (temp file + move), change listeners via plain core listener interface
  (§2.5 — no `ObservableValue` in core).
- Storage: `preferences.json` via Jackson in the user data dir (`%APPDATA%/MinDis` / XDG) —
  same serializer, same directory as the M2 repositories. Corrupt/missing file ⇒ defaults +
  warning, never a crash.
- GUI: thin adapter wraps `PreferencesService` into JavaFX properties for bindings; Settings
  workbench module edits them. Locale + theme are applied at startup before the first scene.
- Versioning: record carries a `version` field; migrations are explicit code, no magic.

Rejected: `java.util.prefs` (Windows Registry backend — invisible, no backup, stringly-typed),
PreferencesFX (JavaFX-coupled + FormsFX baggage + stores via java.util.prefs anyway),
avaje-config (read-oriented app config, no user save-back),
[JShepherd](https://github.com/bsommerfeld/jshepherd) (closest contender: maintained, JPMS
module-infos, smart config merging, comment support — but annotation/reflection driven, which
would add a fourth reflection consumer against §2.2, uses ServiceLoader for format modules,
leaks a `ConfigurablePojo` base type into core, has no change-listener API so the GUI adapter
must be hand-written anyway, and is single-maintainer; its merge feature mainly solves a
problem our small versioned record does not have). Revisit JShepherd if preferences grow into
large user-edited config files where comments and smart merging pay off. Recorded in
`docs/adr/004-preferences.md` (written in M1).

---

## 3. Domain Model (Timefold)

Package: `org.mindis.core.model` (plain domain) and `org.mindis.core.planning` (solver types).
Plain Java/records only — no JavaFX property types (§2.5); GUI wraps domain in observable view
models on its side.

### Problem facts (immutable during solving)

- **Server** — id, first/last name, contact, birth date, `familyId` (siblings link),
  `Set<Role> qualifications`, `List<UnavailabilityPeriod>` (vacations, blocked weekdays),
  preferences (preferred mass times), active flag.
- **Role** — enum or configurable entity: e.g. `ACOLYTE`, `CROSS_BEARER`, `THURIFER`,
  `BOAT_BEARER`, `MC`. Start as enum; make configurable later. Display names via
  `Localization.lang(...)` (§2.3), never `name()`.
- **LiturgicalService** — id, date/time, duration, location (church), type
  (`SUNDAY_MASS`, `WEEKDAY_MASS`, `FEAST`, `WEDDING`, `FUNERAL`, …), list of required
  role slots (role + count), notes.
- **PlanningHorizon** — date range being solved.

### Planning entity

```java
@PlanningEntity
public class Assignment {
    @PlanningId String id;
    LiturgicalService service;   // fixed
    Role role;                   // fixed
    @PlanningVariable Server server;  // assigned by solver (nullable = unassigned allowed)
    boolean pinned;              // manual override support (@PlanningPin)
}
```

One `Assignment` per required role slot per service. Solution class `ServicePlan` with
`@PlanningEntityCollectionProperty`, `@ValueRangeProvider` over active servers, and
`HardSoftScore`.

### Constraints (`ConstraintProvider`)

Hard:
1. Server must be qualified for the assigned role.
2. Server must be available (no unavailability overlap with service time).
3. No double-booking: one server, one assignment per overlapping service time.
4. Server is active.

Soft (weights tunable in settings):
1. **Fairness** — balance assignment count per server over the horizon (load balancing).
2. **Siblings together** — prefer same-family servers assigned to the same service.
3. **Spacing** — penalize assignments on consecutive services/days for the same server.
4. **Preference match** — reward preferred mass times.
5. **Experience mix** — prefer pairing experienced with new servers per service.

Each constraint gets a `ConstraintVerifier` unit test before UI integration.

---

## 4. Project / Module Layout (JPMS)

Multi-project Gradle build, JabRef-style:

```
mindis/
├── settings.gradle.kts
├── build.gradle.kts                  # minimal root; real logic in build-logic
├── build-logic/                      # included build: convention plugins
│   ├── settings.gradle.kts
│   ├── build.gradle.kts
│   └── src/main/kotlin/
│       ├── org.mindis.gradle.base.repositories.gradle.kts
│       ├── org.mindis.gradle.feature.compile.gradle.kts    # toolchain, javac flags, checkstyle
│       ├── org.mindis.gradle.feature.test.gradle.kts       # JUnit 5, module testing
│       ├── org.mindis.gradle.feature.native.gradle.kts     # native-image wiring (created in M7)
│       └── org.mindis.gradle.module.gradle.kts             # gradlex module plugins wiring
├── gradle/
│   ├── modules.properties            # JPMS module name -> Maven GA mappings
│   └── wrapper/
├── versions/                         # java-platform project: ALL dependency versions (§5)
├── config/
│   └── checkstyle/checkstyle.xml     # JabRef-derived rules (§8)
├── docs/adr/                         # architecture decision records
│   ├── 001-view-layer.md             # FxmlKit primary, FXML/2 parked (§2.1)
│   └── 003-web-ui-path.md            # future web module options, decision deferred (§2.5)
├── mindis-workbench/                 # module: org.mindis.workbench (WorkbenchFX fork, §4.1)
│   └── src/main/java/module-info.java
├── mindis-core/                      # module: org.mindis.core — UI-AGNOSTIC (§2.5)
│   └── src/main/java/module-info.java
│       # exports model, planning, persistence API, localization, preferences
│       # requires ai.timefold.solver.core, com.fasterxml.jackson.databind, io.avaje.inject
│       # NO javafx.* requires — enforced (§2.5)
│       # opens org.mindis.core.model, .planning to timefold + jackson
├── mindis-gui/                       # module: org.mindis.gui  (main application)
│   └── src/main/java/module-info.java
│       # requires org.mindis.core, org.mindis.workbench, javafx.controls, javafx.fxml,
│       #          atlantafx.base, com.dlsc.fxmlkit, io.avaje.inject
│       # opens view/controller packages to javafx.fxml + com.dlsc.fxmlkit
│
└── (future, NOT created now: mindis-web — org.mindis.web, requires org.mindis.core; §2.5)
```

### 4.1 `mindis-workbench` — in-repo WorkbenchFX fork
> **As built (M1):** bespoke shell instead of source fork — see ADR-005. The section below is
> kept as the original intent; its constraints (own module, AtlantaFX tokens, no
> FontAwesomeFX, minimal surface) all hold for the bespoke implementation too.

WorkbenchFX is unmaintained (last release Jan 2022, Java 11 era, no `module-info`). Instead of
depending on the jar and patching it, its useful core is **forked into this repo** as a proper
JPMS module we own. License: Apache-2.0 — keep the original license text and a NOTICE entry
("contains code derived from WorkbenchFX, © DLSC Software & Consulting GmbH, Apache-2.0"), and
retain per-file copyright headers on derived files.

Fork scope — take only what MinDis needs:

- `Workbench` container + builder, `WorkbenchModule` lifecycle (`init/activate/deactivate/destroy`)
- Tab bar, add-module ("+") tile page, navigation drawer, toolbar
- Dialog + drawer system

Deliberate changes vs. upstream:

- Real `module-info.java` (`org.mindis.workbench`), compiled against JavaFX 25.
- **Drop FontAwesomeFX** (unmaintained, JPMS-hostile) → replace icons with Ikonli
  (`org.kordamp.ikonli` — modular, actively maintained) or plain SVG paths.
- Rewrite CSS against AtlantaFX design tokens (CSS variables) instead of WorkbenchFX's own
  palette — kills the theme-clash problem at the root; light/dark follows AtlantaFX theme.
- All user-visible strings through `Localization.lang(...)` (§2.3).
- Delete unused upstream features aggressively (keep the fork small — it is now our maintenance
  burden).
- No reflection, no resource-bundle magic (keeps §2.2 rules trivially satisfied).

---

## 5. Build Setup (Gradle Kotlin DSL + GradleX, JabRef-style)

Key elements copied from the JabRef approach:

1. **Versions platform** (JabRef pattern, no version catalog): all dependency versions live in
   `versions/build.gradle.kts` (`java-platform` project — GAV constraints + BOM imports),
   consumed everywhere via `jvmDependencyConflicts { consistentResolution { platform(":versions") } }`.
   JavaFX version is the `javafxVersion` Gradle property (gradle.properties), read by both the
   platform and `feature.javafx`.
2. **`build-logic` included build** with convention plugins (`org.mindis.gradle.*`) applied by
   subprojects; root `build.gradle.kts` stays nearly empty.
3. **GradleX plugins** (applied in convention plugins):
   - `org.gradlex.java-module-dependencies` — derive Gradle dependencies from `module-info.java`
     (`requires` ⇒ dependency); custom module-name→GA mappings in `gradle/modules.properties`.
     **As built (M0): project-plugin mode** — project names are `core`/`gui`/`workbench`
     (dirs `mindis-*`) so `group + name = module name` holds.
   - `org.gradlex.jvm-dependency-conflict-resolution` — wires the `:versions` platform into all
     resolution (`consistentResolution`), applied in the module convention plugin.
   - `org.gradlex.extra-java-module-info` — patch remaining non-modular jars (fewer now that
     WorkbenchFX is forked; still likely needed for transitive bits).
   - `org.gradlex.java-module-testing` — whitebox module testing with JUnit 5.
   - `org.gradlex.jvm-dependency-conflict-resolution` — sane conflict handling.
   - `org.gradlex.java-module-packaging` — jpackage-based platform installers (primary
     shipping path through M6).
4. **FxmlKit** — regular dependency of `mindis-gui` (`com.dlsc.fxmlkit`); FXML files live next
   to their views per FxmlKit convention; hot reload active in dev runs.
4a. **Avaje Inject** — `avaje-inject` as dependency, `avaje-inject-generator` on
   `annotationProcessor` path of `mindis-core` and `mindis-gui` (wired in a convention plugin;
   mind processor-with-JPMS setup). `avaje-inject-test` for tests.
5. **Checkstyle** — JabRef-derived `config/checkstyle/checkstyle.xml`, wired into
   `feature.compile` convention plugin; build fails on violation (§8). Includes `IllegalImport`
   rule banning `javafx.*` in `mindis-core` (§2.5).
6. **Localization check task** — verifies `Localization.lang` literals ↔ bundle entries (§2.3);
   part of `check`.
7. **Java toolchain** — `java.toolchain.languageVersion = 25` + `org.gradle.toolchains.foojay-resolver-convention` in settings.
8. **Native image** (`org.mindis.gradle.feature.native`) — **does not exist before M7.** Created
   then with: GraalVM toolchain (Liberica NIK or Gluon GraalVM with JavaFX static libs), GluonFX
   Gradle plugin (`com.gluonhq.gluonfx-gradle-plugin`) as the established JavaFX→native path,
   tracing-agent metadata task, VS Build Tools requirement documented in `docs/dev-setup.md`.
9. **Run task** — standard `application` plugin with module path
   (`mainModule = "org.mindis.gui"`, `mainClass = "org.mindis.gui.MinDisApp"`).

---

## 6. Known Risks & Mitigations

| Risk | Impact | Mitigation |
|------|--------|-----------|
| FxmlKit single-vendor dependency (DLSC) | Abandonment would strand view layer | Standard FXML underneath — controllers/FXML survive a swap to plain `FXMLLoader` or FXML/2 (ADR-001 revisit triggers); keep FxmlKit-specific API surface thin. |
| Workbench fork = own maintenance burden | Bug fixes on us forever | Keep fork minimal (§4.1); delete unused features; TestFX coverage on shell behavior. |
| Fork effort underestimated (WorkbenchFX internals tangled) | M1 overruns | Timebox: if extraction exceeds ~1 week, fall back to writing a small bespoke shell (tabs + drawer + dialogs on plain JavaFX) — MinDis needs only a subset anyway. |
| **GraalVM (all M7):** JavaFX native fragility, Timefold AOT (Quarkus-tested, plain-Java less trodden), FXMLLoader + Jackson reflection | M7 fails or slips | Whole risk deferred to M7 by design — **jpackage (M6) is the shipping path and stays regardless**, so native is pure upside. In M7: GluonFX plugin, tracing-agent metadata over every view, headless solver spike first; if FXML metadata churn is chronic, revisit FXML/2 per ADR-001. Rules §2.2 keep M0–M6 code from making it worse. |
| Full-text keys clash with properties format (spaces, `=`, `:` need escaping) | Messy bundle files | Exactly JabRef's trade-off — proven workable; localization check task (§5) catches drift; consider JabRef's tooling for bundle maintenance. |
| Timefold under JPMS | Reflection failures at runtime | `opens org.mindis.core.model, org.mindis.core.planning to ai.timefold.solver.core;` — covered by solver smoke test in CI. |
| Avaje annotation processor + JPMS friction (processor on module path, generated sources in module) | M0 setup pain | Known-workable combo (Avaje documents JPMS use); part of M0 spike — DI-injected controller must work before M1. Fallback: manual composition root (Avaje removal is mechanical — constructor injection stays). |
| Web-readiness discipline erodes (JavaFX types leak into core) | Future web module blocked | `module-info` (core requires no `javafx.*`) + Checkstyle `IllegalImport` fail the build on first leak (§2.5). |

---

## 7. Milestones (execution order for a coding agent)

### M0 — Build scaffold (no UI)
1. `settings.gradle.kts`, root `build.gradle.kts`, wrapper (Gradle 9.x),
   `gradle/libs.versions.toml`, `.gitignore`, `build-logic` with convention plugins (§5),
   JabRef-derived Checkstyle config. No native tooling of any kind.
2. Create `mindis-workbench`, `mindis-core`, `mindis-gui` with `module-info.java` stubs; wire
   `java-module-dependencies` mapping for all libraries.
3. **FxmlKit + Avaje spike (blocking, §2.1/§2.4):** hello-world view in `mindis-gui` — JavaFX
   25, module path, `%` full-text resource keys, hot reload, controller resolved from
   `BeanScope` via FxmlKit's DI hook with one injected `@Singleton` service from `mindis-core`.
   Record outcome in `docs/adr/001-view-layer.md`.
4. `Localization` class (in `mindis-core`) + `MinDis_en/de.properties` + localization check
   task (§2.3).
5. Seed `docs/adr/003-web-ui-path.md`: web UI deferred; §2.5 rules active from now; candidate
   stacks listed, decision postponed.
6. **Done when:** `./gradlew build run` launches a stage showing one localized FxmlKit view
   with DI-injected controller from the module path; `check` runs Checkstyle (incl. core
   `javafx.*` import ban) + localization task.

### M1 — Workbench fork + theme
1. Fork WorkbenchFX core into `mindis-workbench` (§4.1): import sources, add license headers +
   NOTICE, strip to needed feature set, replace FontAwesomeFX with Ikonli, add `module-info.java`,
   compile against JavaFX 25. **Timebox — bespoke-shell fallback per §6.**
   **As built: fallback executed** — bespoke shell with WorkbenchFX-inspired API
   (`WorkbenchModule` lifecycle, builder, home tiles + tabs), AtlantaFX-token CSS, no
   WorkbenchFX code imported. Rationale + consequences: `docs/adr/005-workbench-shell.md`.
   Drawer/dialogs deferred to demand (M2+); Ikonli deferred until real icons needed.
2. Rewrite workbench CSS against AtlantaFX tokens; apply `PrimerLight` user-agent stylesheet;
   light/dark toggle.
3. `MinDisApp`: build `Workbench` with placeholder modules: *Dashboard*, *Servers*, *Services*,
   *Planning*, *Settings*. All strings via `Localization.lang(...)`; language switch in Settings.
4. Preferences (§2.6): `MinDisPreferences` record + `PreferencesService` in core (Jackson,
   atomic write); gui adapter; locale, theme and window geometry persisted and applied at
   startup. Write `docs/adr/004-preferences.md`.
5. TestFX smoke tests for shell (open/close modules, drawer, dialog).
   **As built: deferred** — TestFX needs a headless-toolkit harness (Monocle) that fights
   JPMS; preferences covered by unit tests instead. TestFX harness revisited in M2 when the
   first real views land.
6. **Done when:** app starts, five modules open/close, theme + language switch (en↔de) work
   **and survive restart**, `mindis-workbench` has no dependency on `com.dlsc.workbenchfx`
   artifacts.

### M2 — Domain model + persistence + first real views
1. Implement `mindis-core` model (§3, without Timefold annotations yet) + Jackson JSON
   repository storing under user data dir (`%APPDATA%/MinDis` / XDG equivalent), records +
   explicit creators (§2.2 rule 3), unit tests.
2. Build *Servers* module UI with FxmlKit (`FxmlView` + controller convention, controllers as
   Avaje beans): table, CRUD form (qualifications, family, availability editor). Observable
   view models in gui wrap plain core domain (§2.5).
3. Build *Services* module UI: service list per horizon, CRUD form, role-slot editor,
   recurring-service templates (e.g. every Sunday 10:00).
4. **Done when:** roster + services survive app restart (JSON round-trip), UI CRUD complete,
   both views fully localized.

### M3 — Timefold integration
1. Add Timefold annotations to planning types, `ServicePlan` solution, `ConstraintProvider`
   with all constraints (§3), `SolverConfig` (termination ~10s or best-score-unimproved).
2. `ConstraintVerifier` tests per constraint; one end-to-end solver test with fixture data.
3. `PlanningService` in core (Avaje `@Singleton`): build problem from repositories for a
   horizon, solve async (`SolverManager`), expose progress + best solution via UI-agnostic
   listener/`CompletableFuture` API (§2.5 rule 3) — GUI adapts onto FX thread.
4. **Done when:** headless test produces feasible plan (0 hard violations) for realistic
   month fixture (≈20 servers, ≈15 services) — proving core runs without any UI module.

### M4 — Planning UI
1. *Planning* module: pick horizon → generate assignments → live solving view (score, progress,
   assignment grid: services × role slots). Solver time budget + constraint weights come from
   `PreferencesService` (§2.6), editable in Settings.
2. Manual edit: swap server via combo, pin assignments (`pinned` → `@PlanningPin`), re-solve.
3. Violation display: per-assignment indictment list (Timefold `ScoreAnalysis`), messages
   localized.
4. Persist accepted plan as JSON next to roster data.
5. **Done when:** full loop works — edit roster → solve → pin → re-solve → save → restart → plan restored.

### M5 — Export & polish
1. PDF export of accepted plan (grouped by service; per-server view), via OpenPDF or similar;
   export honors the app language.
2. Dashboard module: next services, unassigned slots, per-server load stats.
3. Localization pass: complete `de` bundle, review `en` texts.
4. **Done when:** printable monthly plan PDF generated from the app in both languages.

### M6 — Packaging & CI (jpackage)
1. `org.gradlex.java-module-packaging`: jpackage installers for Windows (primary), macOS, Linux.
2. GitHub Actions: build + test on push; package on tag.
3. **Done when:** installable Windows build runs on a clean machine. **App is shippable here —
   M7 is optional optimization.**

### M7 — GraalVM Native Image (last, self-contained)
All native work lives here; nothing before M6 depends on it, and failure leaves M6 as the
shipping path.
1. Create `org.mindis.gradle.feature.native` convention plugin: GraalVM toolchain
   (Liberica NIK / Gluon GraalVM with JavaFX static libs), GluonFX Gradle plugin.
   Windows: VS Build Tools (document in `docs/dev-setup.md`).
2. **Spike first:** headless native build of `mindis-core` + solver fixture run (Timefold AOT
   is the biggest unknown). Blocked → stop, record in `docs/adr/002-packaging.md`, stay on jpackage.
3. Generate reachability metadata with the tracing agent
   (`-agentlib:native-image-agent=config-merge-dir=...`) over a scripted flow covering every
   view + one solve + JSON round-trip; commit under
   `mindis-gui/src/main/resources/META-INF/native-image/org.mindis/`. Expect metadata for
   FXMLLoader (every view), Jackson, Timefold, JavaFX internals. If FXML metadata proves
   unmanageable, revisit FXML/2 per ADR-001.
4. Full-app native build; smoke test: launch, open all modules, solve, export PDF.
5. CI: native job on tag (Windows first).
6. **Done when:** native Windows binary passes the smoke test — or ADR-002 documents why
   jpackage stays primary.

### Future (explicitly out of scope for M0–M7): `mindis-web`

Browser UI as additional module per §2.5. Prerequisites already in place by then: UI-free core,
async service API, core-hosted localization, Avaje DI reusable server-side. Open decisions for
ADR-003 when the time comes: stack (Javalin/Helidon + HTMX, REST+SPA, or JPro bridge),
multi-user storage, auth, deployment.

---

## 8. Conventions for Contributors / Agents

### Commit messages — [joelparkerhenderson/git-commit-message](https://github.com/joelparkerhenderson/git-commit-message)

- Subject: imperative mood, capitalized, ≤ 50 chars, no trailing period
  (`Add availability editor to server form`, not `added editor.` / `feat: editor`).
- Blank line, then body wrapped at 72 chars explaining **why** (motivation, contrast with
  previous behavior), not restating the diff.
- Prefer verbs: `Add`, `Fix`, `Refactor`, `Remove`, `Rename`, `Update`, `Document`, `Optimize`.
- One logical change per commit.

### Code style — JabRef

Adopt JabRef's code style (see JabRef `CONTRIBUTING.md` / devdocs), enforced by the
Checkstyle config in `config/checkstyle/checkstyle.xml` (derived from JabRef's):

- 4-space indent; braces always, even for single-statement `if`.
- No wildcard imports; import order per config.
- No abbreviations in identifiers; descriptive names over comments.
- Prefer `Optional<T>` over `null` returns; `final` where it clarifies.
- JavaDoc for public API in `mindis-core`; comments explain *why*, not *what*.
- Modern Java: records, sealed types, pattern matching, `var` where type is obvious.
- Test naming: `methodUnderTest_condition_expectedResult` style, JUnit 5, AssertJ-style
  assertions if adopted repo-wide.

### Localization

- Every user-visible string: `Localization.lang("Full English sentence")` — full text as key,
  never abstract keys (§2.3). Applies to code, FXML (`%` keys), dialogs, PDF export.
- New strings land in `MinDis_en.properties` in the same PR; `de` translation may follow, the
  English fallback keeps the UI intact.
- Localization check task must pass in `check`.

### General

- One ADR per significant decision in `docs/adr/NNN-title.md`. Seeded: `001-view-layer`
  (FxmlKit vs FXML/2), `002-packaging` (native vs jpackage, written in M7), `003-web-ui-path`
  (web module deferred, §2.5), `004-preferences` (own JSON store, §2.6, written in M1).
- **New user-facing setting = new field in `MinDisPreferences`** with default value; bump the
  record's `version` and add explicit migration when shape changes. No ad-hoc config files,
  no `java.util.prefs`.
- **DI via Avaje Inject (§2.4):** constructor injection only (no field injection); services in
  core, controllers/view models in gui; one `BeanScope` per app. No other DI mechanism.
- **Web-readiness (§2.5):** no `javafx.*` in `mindis-core` (build-enforced); core service APIs
  UI-agnostic (plain types, futures/listeners — no `ObservableValue` in signatures).
- All versions only in `versions/build.gradle.kts` (plus `javafxVersion` in gradle.properties);
  module-name ↔ coordinate mappings in `gradle/modules.properties`. No version catalog.
- Don't-block-native rules (§2.2) apply to every PR: new reflection outside
  FXMLLoader/Jackson/Timefold needs justification. No native tooling before M7.
- Derived WorkbenchFX code keeps upstream copyright headers; NOTICE file maintained.
- Every Timefold constraint has a `ConstraintVerifier` test before it ships.
