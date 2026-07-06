# ADR 006: Preferences architecture — registry + self-describing choice values

Date: 2026-07-06
Status: accepted

## Context

The original preferences stack was JabRef-style "anemic": immutable record + service +
withers (core), a hand-written property adapter (`UiPreferences`) and manually wired
settings rows (gui). Sound principles, but every setting existed in four places (record
field, wither, adapter property, UI row) and enum display/behavior logic was smeared
across switches in multiple classes.

Instead a preferences registry with self-describing values and observable translation
and platform gating is proposed.

## Decision

Adopt the two transferable ideas, keep our persistence and DI model:

1. **`PreferenceEnumValue`** (core): `displayName()` + `isSelectable()`. `Theme` localizes
   its name via the full-text key; `AppLanguage` (new typed view over the persisted BCP-47
   `languageTag`) deliberately never translates language names. Gui renders any such value
   with the generic `PrefsControls.choiceBox(values, property)` — one line per settings row,
   display switches deleted.
2. **`PreferenceValue<T>` registry** (gui): `UiPreferences` defines each setting exactly once 
   via `register(getter, wither)`; initial load, write-through and external re-sync are wired
   generically (one service listener refreshes all registered properties). Soft-constraint
   weights use the same mechanism (ordered by
   `MinDisConstraintProvider.tunableSoftConstraints()`, the single source for UI order and
   defaults). A new setting = record field + wither + one `register` line.

## Deferred (stage C)

- Generating the settings UI entirely from registry metadata (label key + editor type).
- Observable translations (`toTranslatedString`) instead of rebuild-on-language-change.

Revisit when the settings surface grows beyond one page.
