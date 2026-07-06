# ADR 002: Packaging — jpackage installer; GraalVM native image rejected

Date: 2026-07-06
Status: accepted

## Context

PLAN.md M7 planned an optional portable native binary (GraalVM) next to the jpackage
installer from M6, gated on two spike criteria: (a) Timefold solves under AOT at all,
(b) native solver throughput vs. JIT is acceptable.

## Spike results (native-spike, GitHub windows runner, GraalVM for JDK 25)

Same fixture (20 servers, 46 slots), same 10s budget, `NO_ASSERT` mode, single thread:

| | JIT (Temurin 25) | GraalVM native |
|---|---|---|
| move evaluation speed | 90,242/sec | 39,488/sec (**44%**) |
| best score | 0hard/0medium/-220soft | 0hard/0medium/-220soft |
| unassigned slots | 0/46 | 0/46 |

- (a) **passed**: Timefold 2.2 compiles with native-image and solves correctly. Needed:
  checked-in `reflect-config.json` covering the planning classes (`ServicePlan`,
  `Assignment`, `MinDisConstraintProvider`, score class) — the tracing agent proved
  fragile (metadataCopy path issues), the hand-written config is deterministic and small.
- (b) **failed**: 2.3x slower solving. The identical final score here only means the small
  fixture plateaus within 10s; realistic parish months pay the throughput loss as worse
  plans per time budget. Solving is the product's core feature.

## Decision

**jpackage (M6) is the only shipping path.** No native production artifact:

1. Solver throughput: JIT beats AOT 2.3x on the hot path that defines product quality.
2. A native binary still has no installer UX (Start menu, uninstall, upgrades) — packaging
   effort would come on top.
3. The spike covered headless core only; full-app native would additionally need the
   JavaFX/Gluon static toolchain (major extra effort, PLAN.md risk table).

## Kept in the repo

`native-spike/` project + `.github/workflows/native-spike.yml` (manual dispatch) stay as a
one-click re-evaluation harness. Revisit triggers: GraalVM PGO/ML profile-guided builds
closing the throughput gap, a genuine no-install deployment requirement, or Timefold
shipping first-class native support with published benchmarks.
