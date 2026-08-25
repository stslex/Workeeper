# Phase 2 probe report — the KMP/CMP toolchain, measured

**Status:** executed 2026-08-15 on branch `probe/kmp-battery` (never merges; head at the
C-validation commit). Companion code: PR #227 (`feature/kmp-convention`). Toolchain under
test: AGP 9.3.0 · Kotlin 2.4.10 · KSP 2.3.9 · CMP 1.11.1 · Paparazzi 2.0.0-alpha05 ·
Metro 1.3.2 · Room 3.0.0 · Gradle 9.6.1 · Xcode 26.6.

**Method.** Every probe ran against a synthetic module with zero repo dependencies
(`probe:ui-probe` for the UI stack, `probe:data-probe` for Room/KSP), with the prediction
written down before the run, a known-positive anchor (the same mechanism on the classic
Android path, where 446 goldens and the full CI suite already prove it), and a
known-negative anchor (a named mutation that must turn the gate red — a probe whose gate
cannot go red is a comment). Counts come from result XML and generated-file listings,
never from exit codes: this repo has two documented false-green precedents (KSP 2.3.6
silent skip; the golden-gate cache replay).

**Reading this in six months:** every claim below is measured on this repo unless marked
*(carried from research)*. Research-sourced claims cite the primary source. Nothing here
requires rerunning a probe to act on.

---

## Verdict table

| # | Question | Answer | Phase-7 consequence |
|---|---|---|---|
| P0 | Does CMP commonMain compile at all on AGP-KMP? | **GREEN** | Foundation holds |
| P1 | Paparazzi on a KMP module? | **GREEN with conditions** | kit's 86 goldens can ride; goldens *move*, tasks *rename* |
| P2 | testFixtures on a KMP module? | **RED — feature absent** | Golden harness must become a module |
| P3 | golden-gate.gradle.kts on a KMP module? | **RED ×3, fix proven** | Ship the parametrized gate with the first golden conversion |
| P4 | Life without debugImplementation? | **Answered** | One mechanical pattern; app tier unaffected |
| P5 | Metro aggregation from KMP androidMain? | **GREEN** | Phase 3 (core collapse) unblocked |
| P6 | CMP × Metro × K/N on the simulator? | **GREEN** | The plan's terminal risk, measured early, holds |
| P7 | KSP2 + Room 3 on AGP-KMP? | **GREEN** (prediction was red) | Phase 6 unblocked on the current pins |

---

## P0. Baseline: CMP commonMain on AGP-KMP compiles

A `@Composable` using material3 + foundation in `commonMain` of a
`com.android.kotlin.multiplatform.library` module compiles for both `android` and
`iosSimulatorArm64` under the base KMP convention. 36 tasks green on the first
configuration. This was never in serious doubt but had never been measured in this repo —
CMP was in the catalog with zero consumers.

## P1. Paparazzi 2.0.0-alpha05 on a KMP module — GREEN with conditions

- **Method:** raw Jupiter-driven `Paparazzi.setup()/snapshot()/teardown()` (the exact kit
  harness mechanism, minus the kit dependency), Pixel-5 device config,
  `maxPercentDifference = 0.0`, subject composable in commonMain.
- **Prediction:** green after resource flags; snapshots in `src/androidHostTest/snapshots`.
- **Result:** record green → verify green → **corrupted golden turns verify red** →
  restore green. Golden is a real 461×1000 RGBA render.
- **Conditions, each measured by hitting the failure first:**
  1. `androidResources.enable = true` on the module (AGP-KMP defaults android resources
     OFF) **and** `isIncludeAndroidResources = true` on the host test. Without them:
     `ClassNotFoundException: <module>.R` at `PaparazziCallback.initResources`.
  2. **`withHostTest` is SINGLE-CALL.** A module calling it after the convention gets
     "Android host tests have already been enabled". Every host-test option must therefore
     live in the convention's one call — this moved `isIncludeAndroidResources` into the
     base convention (PR #227), not the Compose one.
  3. **Task names:** `recordPaparazziAndroidMain` / `verifyPaparazziAndroidMain` plus
     variantless aggregates. **`verifyPaparazziDebug` does not exist** — CI's literal
     command silently skips every converted module. A `verifyPaparazziDebug → verifyPaparazzi`
     alias must ship with the first golden-module conversion (same silent-vanish class as
     the three closed in PR #227).
  4. **Snapshot location:** `src/androidHostTest/snapshots/images`, not
     `src/test/snapshots/images` *(carried from research: Paparazzi's own KMP fixture at
     the 2.0.0-alpha05 tag; confirmed locally by where record wrote)*. Phase 7 therefore
     `git mv`s all 446 goldens — **never re-records** them; `git mv` preserves bytes and
     the C-validation run proved a golden recorded pre-convention verifies byte-identically
     post-convention.
  5. **R-class leak:** an AAR on `androidRuntimeClasspath` (measured with ui-tooling)
     makes Paparazzi demand that AAR's R class on the host-test classpath →
     `ClassNotFoundException: androidx.compose.ui.tooling.R`. Keep tooling artifacts off
     converted library modules; the 15 real `debugImplementation` sites never used
     ui-tooling anyway (see P4).
- *(Carried from research, verified-primary:)* alpha05 is the newest release; KMP-plugin
  support landed in it via cashapp/paparazzi #2115 + #2332; the plugin auto-adds its
  runtime to the androidHostTest compilation and hard-fails if Paparazzi is declared in
  commonTest (#2330).

## P2. testFixtures — RED, feature absent

- **P2a:** `testFixtures { }` in the KMP android DSL → *Unresolved reference* at script
  compile. The AGP 9.3 extension has no such member (confirmed in the gradle-api sources:
  no fixtures wiring anywhere in the KMP plugin).
- **P2b:** `java-test-fixtures` *applies* without error — and wires **Java-only**
  compilation (`compileTestFixturesJava`; no Kotlin task, no android-aware outgoing
  variants). An applied-but-nonfunctional plugin is exactly the false-green shape this
  repo distrusts; measured before trusted.
- **Phase-7 consequence:** `core:ui:kit` publishes the golden harness via testFixtures
  precisely so device config / tolerance / canvas width cannot drift per module. That
  invariant survives the KMP conversion only by moving the harness into a dedicated
  module (working name `core:ui:golden-harness`) consumed as a normal
  `androidHostTestImplementation` dependency. **This move is executable today, Android-only,
  before any conversion** — it is the natural first Phase-7 preparatory PR, and it
  de-risks the kit conversion by shrinking what changes at once.

## P3. golden-gate.gradle.kts — three measured divergences; fix proven both directions

The classic script breaks on a KMP module in three places, each a hardcode of the classic
module shape:

1. `(this as Test)` on the task named `testDebugUnitTest` — on KMP that name belongs to
   the convention's plain alias task → **ClassCastException killing configuration of every
   plain test build** (`DefaultTask_Decorated cannot be cast to Test`, measured verbatim).
2. `goldenImagesDir` hardcodes `src/test/snapshots/images` → liveness fails "No golden
   images" while a real golden sits in `src/androidHostTest/snapshots/images` (measured).
3. `unitTestResultsDir` hardcodes `test-results/testDebugUnitTest` vs the real
   `test-results/testAndroidHostTest` (masked behind 2; covered by the fix).

The fix-shape artifact — `gradle/golden-gate-kmp-fix.gradle.kts` on the probe branch —
parametrizes all three on `pluginManager.hasPlugin("com.android.kotlin.multiplatform.library")`
and keeps classic behavior byte-identical. Measured on the KMP module: plain run green (no
CCE, goldens excluded), verify green with "Visual gate live: 1/1", **phantom-golden red**
("1 executed but 2 committed"), restore green. It is a probe artifact, not a merged change:
it lands with Phase 7's first golden conversion.

**Bonus hazard:** a module whose ONLY host tests are goldens trips the test filter's own
fail-on-no-match under the plain run ("No tests found for given includes") — a mechanism
independent of `failOnNoDiscoveredTests`. The fix sets `isFailOnNoMatchingTests = false`.
Classic golden modules dodge this today only because each happens to keep at least one
non-golden test.

**Post-review addendum (PR #227 round 5).** The KMP convention now keeps Gradle's
`failOnNoDiscoveredTests` default (`true`) instead of the Android convention's `false`:
with `false`, a discovery regression (JUnit Platform wiring dropped) went GREEN over zero
executed tests — reproduced, then closed; the same mutation reds under the default. The
interplay with this section's filter is measured benign: a golden-only KMP module's plain
run stays green, because the filter's `isFailOnNoMatchingTests = false` covers
filtered-to-zero and the task-level check does not fire on filter-excluded tests. The two
mechanisms are independent in both directions. The Android convention still sets `false`
and carries the discovery-regression exposure for classic modules — an open cleanup, out
of Phase 2's scope.

## P4. debugImplementation — one mechanical replacement, app tier untouched

- **The real scope, measured:** 15 build files (the prompt said fourteen) use
  `debugImplementation`, and every single one is the same line:
  `debugImplementation(libs.androidx.compose.ui.test.manifest)`. No module debug-ships
  ui-tooling or anything else.
- **P4a:** `"debugImplementation"(...)` on a KMP module fails loud at configuration:
  `Configuration with name 'debugImplementation' not found`. No silent variant.
- **P4b:** `"androidRuntimeClasspath"(...)` — the AGP-sanctioned single-variant
  replacement *(AGP 9.0.0-beta01+, and JetBrains' own AGP-9 migration guide uses it for
  uiTooling)* — is **declarable and resolvable** under Gradle 9's strict configuration
  roles (ui-tooling 1.11.2 confirmed via dependencyInsight). The research's open question
  on declarability is closed.
- **P4c:** `withDeviceTest {}` composes additively from a module script on top of the
  convention; `androidDeviceTestImplementation` accepts the test-manifest artifact and it
  resolves on `androidDeviceTestRuntimeClasspath`. The instrumented run task is
  `connectedAndroidDeviceTest`. **New finding:** with `org.jetbrains.compose` applied,
  assembling the deviceTest APK fails at configuration
  (`copyAndroidDeviceTestComposeResourcesToAndroidAssets`: "Value not set" on
  `outputDirectory`) unless `androidResources.enable = true` — same root cause as
  CMP-9547, surfacing earlier and louder. The Compose convention sets the flag
  unconditionally.
- **App tier:** `:app:dev` (14 lines) and `:app:store` (11 lines) and `:app:app` contain
  zero `productFlavors`/`flavorDimensions` — unaffected, as the plan assumed (verified).
- **Phase-7 note, same vanish class:** CI's `assembleDebugAndroidTest` will not build KMP
  deviceTest APKs (different task name). When the first instrumented-tested module
  converts, it needs an `assembleDebugAndroidTest → assembleAndroidDeviceTest` alias in
  the convention.

## P5. Metro aggregation from KMP androidMain — GREEN, Phase 3 unblocked

- **Method:** Metro applied to `core:core` (KMP); `DispatchersBindingContainer`
  (`@BindingContainer @ContributesTo(AppScope)`) git-mv'd from `core:core-android` into
  `core:core/src/androidMain`. Oracle: the real graph, `:app:app`'s 54 `di.*` identity
  tests — not compilation, per the standing rule that Metro's compile-time validation has
  missed acquisition seams before.
- **Prediction:** 60/40 green. **Result: 54/54 executed, 0 failures**, including the
  dispatcher-qualifier inheritance assertions (`the extension inherits the Default
  dispatcher key and not the IO one`).
- **Red direction:** deleting the moved container fails `:app:app` compile loudly.
  (`AppGraph` carries no import of the container — the one it had was KDoc-linking only and
  went with the KDoc in the comment trim; `@DependencyGraph(scope = AppScope::class)` carries
  no explicit container list either, so consumption is pure aggregation.)
- **Phase-3 consequence:** the `core:core-android` sibling exists *only* because core:core
  predates Metro-on-KMP. Aggregation from androidMain works, so the collapse
  (`ResourceWrapper` impls, platform providers, both binding containers moving into
  core:core androidMain) is mechanically viable. The interface+DI-binding vs expect/actual
  split remains a *choice*, no longer a *constraint* imposed by module structure.
- **Landmine watch (unchanged):** the three `@SingleIn(AppScope)` DataStore classes that
  bypass `DataStoreProvider` memoization did not bite here (identity tests mock the
  DataStore-adjacent roots), but any Phase-3 test that builds two real graphs still risks
  the swallowed "multiple DataStores" failure. tech-debt.md already tracks it.

## P6. The intersection — CMP × Metro × Kotlin/Native on the simulator — GREEN

The seven-phase plan's structural weakness was that CMP and an iOS target first met in
Phase 7, so the go/no-go signal arrived last. It is now measured, early, green:

- `iosSimulatorArm64Test` executes `ComposeUIViewController { ProbeCard(...) }` **in the
  iOS simulator** (Xcode 26.6, iPhone simulator runtime): 1/1 in 4.5s per the result XML.
  Red direction: a planted `fail()` turns the run red; revert restores green.
- `linkDebugFrameworkIosSimulatorArm64` produces `ProbeUi.framework` (static) from the
  CMP-bearing module — the Phase-7 iosApp consumption shape links.
- **P6b:** Metro on the same module generates a working `createGraph<ProbeGraph>()` on
  Kotlin/Native — 1/1 green on the simulator. First Metro-on-Native evidence in this repo.
- Deliberately out of scope: rendering a frame into a visible window (needs an app shell —
  that IS Phase 7), and device (`iosArm64`) targets.

## P7. KSP2 + Room 3 on AGP-KMP — GREEN, against the stated prediction

- **Prediction (wrong):** KSP 2.3.9 misbehaves with Kotlin 2.4.10, based on KSP 2.3.10's
  release note "Sanitize ':' in internal-name module suffix so KSP works with Kotlin 2.4.0
  default module names".
- **Result:** `kspAndroidMain` and `kspKotlinIosSimulatorArm64` both **executed** and each
  emitted **3 generated files** (Dao_Impl, Database_Impl, the DatabaseConstructor actual)
  — counted per the KSP-2.3.6 false-green rule — and both targets compile.
- **Red direction:** a `@Query` against a nonexistent table fails ksp loudly
  (`SQLITE_ERROR ... no such table`), so the processor validates, not just emits.
- **Wiring facts:** per-target configs (`kspAndroid`, `kspIosSimulatorArm64`) with
  `androidx.room3:room3-compiler`; `RoomExtension` configured **by type**
  (`androidx.room3.gradle.RoomExtension`) — no `room {}` accessor materializes on a module
  applying the plugin via `alias(...)` alongside the KMP convention.
- **Disposition of the KSP pin:** 2.3.9 is *measured working* for Room-on-KMP here. The
  2.3.10/2.3.11 fixes (module-name sanitize, AGP-9 R-class, isolated-projects,
  kspDebugAndroidTest skipping) are advisory upgrades for Phase 6, to be taken through the
  same counted-codegen probe, not assumed.

---

## Findings that reshaped the merged conventions (PR #227)

1. **Three silent CI vanishes closed** for KMP modules, each proven red/green with a named
   mutation: `testDebugUnitTest` alias (M1/M2), `assembleDebug → assemble` — before it, NO
   gate compiled the iOS klibs (M4) — and `com.android.lint` co-apply + `lintDebug → lint`
   — before it, androidMain was never lint-analyzed (M5). Fourth found in passing: the
   pre-commit hook self-disabled in git worktrees (`-d .git`).
2. **`withHostTest` single-call** → all host-test options belong to the base convention.
3. **`ComposePlugin.Dependencies` is wholly deprecated** in CMP 1.11.1 ("Specify
   dependency directly", read from the plugin's own sources) → the CMP convention wires
   plain catalog coordinates; material3 rides a decoupled version line (1.9.0 against
   plugin 1.11.1, measured by resolution). **AtTen's accessor mechanism is not liftable** —
   the one part of its shape the phase plan marked liftable is the part its own toolchain
   has since deprecated.
4. **`androidResources.enable = true`** belongs in the Compose convention (two measured
   failure modes without it), NOT in the base convention (pure-logic KMP modules keep the
   AGP default off).
5. **The JVM_21 pin in `KmpLibraryConventionPlugin` is load-bearing, not cosmetic.**
   `tasks.withType<KotlinJvmCompile>().configureEach { compilerOptions.jvmTarget.set(JvmTarget.JVM_21) }`
   — without it a KMP module inherits the Gradle daemon's JDK as its jvmTarget, and on any JDK
   newer than 21 every Android consumer fails with "Cannot inline bytecode built with JVM target
   <N>" the moment it calls an inline helper from the module. Keep in sync with
   `KotlinAndroid.configureKotlin`.

## Corrections of record

- "Fourteen library modules use debugImplementation" → **15 files**, all the same single
  artifact (ui-test-manifest).
- "accompanist … appcompat sit in the version catalog with zero consumers" → each had
  exactly one consumer: `ComposeAndroid.kt`, which shipped them to **all 21 compose
  modules** with zero imports anywhere. The honest cleanup removed the wiring, not just
  the entries (goldens + full battery verify).
- The stale catalog comment "KmpLibraryConventionPlugin … applied to zero modules until
  C.1" (core:core has applied it since C.1 landed) — fixed in PR #227.
- The AtTen `ComposePlugin.Dependencies` route described in the phase plan as "the only
  way to declare CMP dependencies from inside build-logic" — no longer true on CMP 1.11.1;
  see finding 3.

## The Phase-7 checklist this report buys

Ordered, each item now evidence-backed instead of assumed:

1. **Harness module first** (Android-only, no conversion): move the golden harness out of
   kit's testFixtures into `core:ui:golden-harness` (P2). Mechanical; consumers change one
   dependency line.
2. **Visual-gate convention for KMP** ships with the first golden conversion: the
   parametrized golden-gate (P3 artifact), a `verifyPaparazziDebug` alias, and — when an
   instrumented module converts — an `assembleDebugAndroidTest` alias (P4c).
3. **Golden migration is `git mv`**, never re-record (P1 + C-validation byte-identity).
4. **Per-module conversion recipe:** apply `convention.kmpComposeLibrary`; replace the one
   `debugImplementation` line with deviceTest wiring if the module has instrumented tests
   (P4c); expect goldens to verify unchanged.
5. **kit converts before any UI leaf** (the leaf-first measurement: 20 of 21 compose
   modules depend on kit; the sole exception, core:ui:navigation, has no goldens and
   proves nothing).
6. **Phase 3 proceeds** on P5's evidence; **Phase 6** on P7's, with the KSP bump taken
   through a counted-codegen probe when scheduled.
