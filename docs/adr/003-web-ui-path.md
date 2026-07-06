# ADR 003: Web UI path — deferred, architecture prepared

Date: 2026-07-06
Status: accepted (decision on web stack deferred)

## Context

A browser-based UI may replace or complement the desktop GUI someday. Building it now is out
of scope (M0-M7 are desktop-only), but the module cut must not block it later.

## Decision

Prepare only; build nothing web-related yet:

1. **`org.mindis.core` is UI-agnostic.** No `javafx.*` requires in its `module-info.java`;
   Checkstyle `IllegalImport` bans `javafx` imports in `mindis-core` (build-enforced).
2. Domain model is plain Java/records — no JavaFX properties. The GUI wraps domain objects
   in its own observable view models.
3. Core service APIs use plain types and futures/listeners, never `ObservableValue`.
   GUI adapts onto the FX thread; a web module would adapt onto HTTP/WebSocket.
4. Localization (`Localization.lang`, bundles) lives in core and is UI-independent.
5. Avaje Inject is UI-independent and reusable server-side.
6. A future `mindis-web` becomes a sibling JPMS module (`org.mindis.web`) requiring
   `org.mindis.core` only.

## Open decisions (when web becomes scope)

- Stack: lightweight Java server (Javalin/Helidon SE) + HTMX, REST + SPA, or JPro
  (JavaFX-in-browser bridge reusing mindis-gui).
- Storage: JSON file store is single-user desktop scope; multi-user needs a server DB.
- Auth, deployment, hosting.
- Localization: `Localization` is a global static with one process-wide locale (fine for
  desktop); a web module needs per-request locale resolution (e.g. an interface with a
  request-scoped implementation).
