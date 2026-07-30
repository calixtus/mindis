# ADR 001: View layer — views are built in Java, no FXML

Date: 2026-07-31
Status: accepted

## Context

A JavaFX view layer has three plausible shapes:

- **FXML at runtime** (e.g. FxmlKit, `com.dlsc.fxmlkit:fxmlkit`): markup loaded by `FXMLLoader`,
  convention-based view/controller wiring, `%`-resource binding, FXML+CSS hot reload,
  SceneBuilder-compatible. Costs runtime reflection.
- **FXML compiled at build time** (jfxcore FXML/2, `org.jfxcore.fxmlplugin`): own dialect compiled
  to bytecode, no `FXMLLoader`, compile-time type safety. Pre-1.0.
- **Plain Java**: build the scene graph in constructors.

What MinDis actually builds pushed the answer. Every screen is a `ShellModule` assembling controls
in Java, and the two shared base classes — `CrudModule` (table left, editor right) and `AppShell`
(sidebar plus content) — are parameterised by behavior, not by layout: which columns, which
toolbar buttons, which editor a row gets. Markup expresses none of that. The screens with the most
UI in them, Services and Servers, are also the ones whose structure is computed at runtime from the
data (a tile per service, a slot row per role, a checkbox per live role).

## Decision

**Views are built in Java. There is no FXML in the project and no FXML library on the class path.**

A view is an ordinary JavaFX `Parent` subclass whose constructor takes its collaborators —
`new DashboardView(dashboardViewModel)` — and a module returns one from `activate()`. Wiring is
constructor injection from the composition root, the same as everywhere else; there is no
view-layer DI hook and no service locator.

The view/view-model split stays. A view model owns repository access and aggregation and returns
data (`DashboardViewModel.Snapshot` carries counts and domain types); the view decides how that
data is worded, formatted and laid out. What is gone is only the markup, not the separation.

Consequences:

- No `FXMLLoader`, so no reflection in the view layer at all. PLAN.md §2.2's allowed reflection
  consumers drop from three to two (Jackson, Timefold), and there is no FXML reachability metadata
  to maintain if M7's native image is ever revisited.
- `org.mindis.gui` is a closed JPMS module. One qualified hole remains: the JavaFX launcher
  reflectively instantiates the `Application` subclass, so the root package is opened to
  `javafx.graphics` and nothing else.
- No SceneBuilder, and no hot reload of a view without restarting. Accepted: with one markup file
  in the tree, neither was paying for itself.
- Localization is uniform — every string goes through `Localization.lang(...)`. There is no second
  mechanism (`%key` attributes in markup) to keep the extraction task aware of.
- Layout errors are compile errors rather than load-time exceptions.

## Revisit triggers

1. A designer joins who works in SceneBuilder.
2. Screens appear whose layout is genuinely static and large enough that Java assembly obscures it.
3. FXML/2 reaches a stable 1.x, making compiled markup available without the reflection cost that
   ruled runtime FXML out.
