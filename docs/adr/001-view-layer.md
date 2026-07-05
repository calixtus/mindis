# ADR 001: View layer — FxmlKit now, FXML/2 parked

Date: 2026-07-06
Status: accepted

## Context

Two candidates for the FXML view layer:

- **FxmlKit** (com.dlsc.fxmlkit:fxmlkit, 1.5.1): standard FXML loaded at runtime via
  `FXMLLoader`; convention-based view/controller wiring; FXML+CSS hot reload; DI hook
  (`DiAdapter`); SceneBuilder-compatible. Actively maintained.
- **FXML/2** (jfxcore fxml-compiler, Gradle plugin `org.jfxcore.fxmlplugin`, 0.14.0):
  own FXML dialect compiled to bytecode at build time; no `FXMLLoader`, no reflection,
  compile-time type safety. Pre-1.0.

## Decision

**FxmlKit is the sole view mechanism.** A pre-1.0 dialect compiler underneath the entire UI
is foundation risk (breaking changes, small community, unproven JavaFX 25+/JPMS edge cases).
FxmlKit wins on maturity, standard FXML, SceneBuilder support and hot reload during the
UI-heavy milestones M1-M5.

Accepted cost: `FXMLLoader` reflection stays in the view layer — one of the three allowed
reflection consumers (PLAN.md 2.2), and extra reachability metadata in M7 (native image).

## Spike result (M0, 2026-07-06)

Hello-world view verified on Windows, JavaFX 26.0.1, Java 25, full module path:

- FxmlKit 1.5.1 loads `HelloView.fxml` by convention (`FxmlView<C>` subclass, fx:controller).
- `%`-resource binding with full-text keys works (`text="%Hello, altar servers!"`,
  bundle set globally via `FxmlKit.setResourceBundle(...)`).
- Controller resolved through `FxmlKit.setDiAdapter(...)` backed by Avaje `BeanScope`;
  constructor injection of a core service (`GreetingService`) works (ADR: PLAN.md 2.4).
- AtlantaFX PrimerLight user-agent stylesheet applied without conflict.

## Revisit triggers (FXML/2)

1. FXML/2 reaches stable 1.x with a compatibility story.
2. M7 native-image FXML reachability metadata churn becomes chronic
   (FXML/2 would eliminate FXMLLoader reflection entirely).
3. FxmlKit abandonment or blocking bugs.
