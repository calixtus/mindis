# Requirements

Requirement specifications for MinDis, distilled from the implemented code and from
[PLAN.md](../../PLAN.md). Written in the [OpenFastTrace](https://github.com/itsallcode/openfasttrace)
Markdown format (`feat~…` → `req~…` → `dsn~…`), but **not yet machine-traced**: there is no
`traceRequirements` task and no `[impl->dsn~…~1]` tags in the source. `Needs:` lines are therefore
omitted; the chain is documented, not enforced. See "Adopting tracing" below.

## Structure

| File | Content |
|------|---------|
| [features.md](features.md) | Vision-level features (`feat~…`) distilled from the [README](../../README.md) and PLAN.md §1 |
| [roster.md](roster.md) | Altar servers and liturgical roles: data, qualifications, availability (`req~` + `dsn~`) |
| [services.md](services.md) | Liturgical services, weekly templates, service generation, role slots (`req~` + `dsn~`) |
| [planning.md](planning.md) | Solver: constraints, scoring, autofill scoping, violations (`req~` + `dsn~`) |
| [archive.md](archive.md) | Freezing past services into immutable snapshots (`req~` + `dsn~`) |
| [persistence.md](persistence.md) | The document file, staged edits, New/Open/Save, CSV import/export (`req~` + `dsn~`) |
| [export.md](export.md) | Plan export to PDF/CSV/TXT/RTF/Markdown (`req~` + `dsn~`) |
| [ui.md](ui.md) | Workbench shell, preferences, theming, localization, logging (`req~` + `dsn~`) |

Architecture decisions live in [../adr](../adr); requirement documents link the decision that
constrains them rather than repeating its rationale.

## Conventions

* Spec item names are kebab-case and stable; increase the revision on a semantic change and update
  every `Covers:` reference in the same commit.
* A `req` item is a user-visible statement of behaviour. A `dsn` item names the class or mechanism
  that realizes it and states the rule precisely enough to test.
* Where a rule is already unit-tested, the test class is named in the `dsn` item body — the
  informal stand-in for `utest` coverage until tracing is wired up.

## Adopting tracing

To turn this into a traced chain: add the OpenFastTrace Gradle plugin, add `Needs:` lines to each
item, and tag the covering code (`[impl->dsn~…~1]`) — each `Needs: impl` added in the same commit
as its tags, so the trace stays green at every commit.
