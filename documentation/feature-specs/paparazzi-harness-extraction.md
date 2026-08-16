# Paparazzi harness extraction — core:ui:kit testFixtures → core:ui:golden-harness

Decision record for the §7 parallel track de-risking KMP Phase 7. Independent of the
Phase 3 core collapse; its PR targets `dev` in parallel.

## Why now

P2 measured that `testFixtures` does not exist on the AGP-KMP plugin
([kmp-phase-2-probes.md](kmp-phase-2-probes.md) P2). The golden harness
(`GoldenHarness.kt`, `GoldenTheme.kt`) is published from core:ui:kit's testFixtures
precisely so device config, tolerance (0.0) and canvas width (392.dp) cannot drift
across the 13 modules holding 446 goldens. When core:ui:kit converts in Phase 7, the
fixtures mechanism disappears — the harness must already live in an ordinary module.
That extraction is Android-only work, executable today.

## Measured facts the design rests on

- The harness is exactly **2 files** under
  `core/ui/kit/src/testFixtures/kotlin/io/github/stslex/workeeper/core/ui/kit/golden/`,
  importing `AppTheme` / `AppUi` / `ThemeMode` from core:ui:kit main.
- **12 consumers** use `testImplementation(testFixtures(project(":core:ui:kit")))`;
  core:ui:kit itself consumes its own fixtures implicitly (13th module).
  All 13 apply the paparazzi plugin and `gradle/golden-gate.gradle.kts`.
- **446 goldens across the 13 modules** (verified count), all under
  `<module>/src/test/snapshots/images/`, named from the **consumer's** test-class
  package + class + method + theme suffix. The harness module's name and package
  appear in **zero** golden paths. Moving the harness moves no golden.
- `golden-gate.gradle.kts` is module-agnostic (matches `*.golden.*` suite names) —
  no gate-script change needed.
- The `testFixturesCompileOnly(paparazzi)` contortion in core:ui:kit exists only
  because AGP wires a module's own fixtures onto its androidTest runtime classpath,
  where layoutlib's protobuf-java collided with firebase-perf's protobuf-javalite.
  An ordinary harness module with no androidTest source set escapes that wiring
  entirely and can declare paparazzi as a normal dependency — restoring compile-time
  failure for consumers that forget the plugin (the current design's documented cost).

## Design

- New module `core:ui:golden-harness` (composeLibrary convention +
  `api(project(":core:ui:kit"))` for `AppTheme`/`AppUi`/`ThemeMode`,
  `api(libs.paparazzi.core)` — consumers compile against `Paparazzi`/`TestInfo`
  types through the harness — plus compose ui/foundation and junit-jupiter as
  `implementation`).
- The two files move by `git mv`, **keeping the package**
  `io.github.stslex.workeeper.core.ui.kit.golden` (repo precedent: core-android kept
  `core.core.*` packages for an import-transparent move). Zero import churn in the
  42 consuming test files; zero golden renames.
- The 12 consumers swap the fixtures line for
  `testImplementation(project(":core:ui:golden-harness"))`; core:ui:kit adds the same
  line (its test source set consumed the fixtures implicitly until now) and drops
  `testFixtures.enable` + the fixtures dependency block. kit:test → golden-harness →
  kit:main is configuration-level acyclic (test classpath vs apiElements), the
  standard back-dependency shape.

## Constraint

**Goldens are never re-recorded.** No golden path changes in this extraction (measured
above), so the exit criterion is a pure verify: `verifyPaparazziDebug` green over all
13 modules with `assertGoldenLiveness` reporting executed-testcase count ≥ committed
PNG count per module — 446 total. Harness-liveness mutation (applied, reverted, never
committed): change `SUBJECT_WIDTH`/device config in the **new** module → verify must
go red across consumers, proving they render through the extracted harness and not a
stale copy.

## Out of scope

The second Phase 7 blocker — the visual-gate convention and the `verifyPaparazzi*`
alias for KMP modules (where `verifyPaparazziDebug` does not exist and CI's literal
command would silently skip) — is **not** started here. The extraction makes it
cheaper only marginally: the alias problem lives in the convention/CI layer, not in
the harness. What the extraction does buy Phase 7: core:ui:kit's conversion no longer
has to solve fixtures-on-KMP, and harness consumers are ordinary project deps that
survive the consumer modules' own conversions unchanged.

Known adjacent stales, fixed in the extraction PR since the change sits next to them:
`android_build_unified.yml` comments still attribute `assertGoldenLiveness` and the
cache-hole closure to `core/ui/kit/build.gradle.kts` — both live in the shared
`gradle/golden-gate.gradle.kts` since the gate was generalised.
`ActiveSurfaceSingleReaderRule` hardcodes the kit's golden **test** directory
(`core/ui/kit/src/test/.../golden/`) — those test classes do not move, so the rule
stays valid; noted here so a future kit conversion moves the constant with them.
