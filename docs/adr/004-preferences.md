# ADR 004: Preferences — own JSON store in core, no framework

Date: 2026-07-06
Status: accepted

## Context

User-editable settings (locale, theme, window geometry; later solver budget and constraint
weights) need typed, persisted, observable storage usable from `org.mindis.core` (UI-agnostic,
PLAN.md 2.5) without new reflection consumers (PLAN.md 2.2).

## Decision

Hand-rolled store in `org.mindis.core.preferences` (~150 lines):

- `MinDisPreferences` — immutable record, all settings + defaults, `version` field with
  explicit migrations in code.
- `PreferencesService` — Avaje `@Singleton`; lazy load; `update(UnaryOperator)` with atomic
  write (temp file + move, non-atomic fallback); corrupt/missing file ⇒ defaults + log,
  never a crash; plain `Consumer` listeners (no `ObservableValue` in core).
- Storage: `preferences.json` (Jackson, pretty-printed, unknown properties ignored) in the
  user data dir (`AppDirectories`: `%APPDATA%\MinDis`, `~/Library/Application Support/MinDis`,
  XDG) — same serializer and directory family as the M2 repositories.
- GUI applies locale + theme before the first scene; language change rebuilds the UI;
  window geometry saved in `Application.stop()`.

## Alternatives rejected

- **java.util.prefs** — Windows Registry backend: invisible to users, not backup/syncable,
  stringly-typed.
- **PreferencesFX (DLSC)** — settings-dialog framework, JavaFX-coupled (breaks core rule),
  FormsFX dependency, persists via java.util.prefs anyway, stale.
- **avaje-config** — read-oriented app config; no user save-back story.
- **JShepherd** (closest contender) — maintained, JPMS module-infos, smart config merging,
  comment support; but annotation/reflection driven (fourth reflection consumer against
  PLAN.md 2.2), ServiceLoader format modules, `ConfigurablePojo` base type would leak into
  core API, no change-listener API (GUI adapter hand-written either way), single maintainer.
  Revisit if preferences grow into large user-edited config files where comments and smart
  merging pay off.
