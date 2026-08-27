# KMP Phase 7.2 — `core:ui:navigation` becomes the shared native navigation contract

**Status:** IMPLEMENTED — verification record in §18; delivered by the Phase-7.2 PR into `dev`

**Target branch:** `dev`

**Documentation baseline:** `9caee21951b44df02ebc54ec7483b3f1085c5c6b` (PR #257 merge)

**Code baseline:** `ba367fe96ac3cf96ccc58a2f419e11b684df27e2` (PR #256 merge)

**Delivery:** one implementation PR to `dev`; no direct push

## 0. Authority and entry condition

This specification is the implementation authority for Phase 7.2. It is derived from the exact
code baseline above and from these canonical repository documents:

- `documentation/feature-specs/kmp-phase-2-probes.md`, especially P0, P6 and the Phase-7 checklist;
- `documentation/feature-specs/kmp-phase-6-data-layer.md`, especially §8;
- `documentation/feature-specs/kmp-phase-7-1-ui-kit.md`, especially §14–§16;
- `documentation/architecture.md` → Navigation;
- `documentation/testing.md` and `documentation/ci-cd.md`.

Upstream facts checked for the pinned dependency line:

- AndroidX Navigation 3 `1.1.6` publishes Kotlin Multiplatform support for the runtime, including
  native Apple targets, while Navigation 3 UI is not available for KMP on that artifact line:
  <https://developer.android.com/jetpack/androidx/releases/navigation3>;
- Kotlin `2.4` defaults JVM interface methods to `JvmDefaultMode.ENABLE`, which also emits
  compatibility bridges and `DefaultImpls`; the classic module's `-Xjvm-default=all` instead maps
  to `JvmDefaultMode.NO_COMPATIBILITY` and must be preserved explicitly:
  <https://kotlinlang.org/docs/gradle-compiler-options.html>;
- the repository remains pinned to Kotlin `2.4.10`, Compose Multiplatform `1.11.1`, AGP `9.3.0`
  and Navigation 3 `1.1.6`; this stage upgrades none of them.

The documentation baseline is PR #257's merge commit. PR #257 changes only the Phase-7.1 status
record, so production/test/build/workflow state remains exactly the code baseline. Before editing,
compare the implementation branch base with both SHAs. Stop and re-run discovery if any production,
test, build-logic, catalog or workflow file changed after the code baseline.

The ruleset API was re-read after PR #257 merged on 2026-08-27. The active repository-wide ruleset
`all` (id `8116593`, matching `~ALL`, `updated_at = 2026-08-27T01:06:29.887+03:00`) requires these
GitHub Actions contexts:

- `Build and Unit Tests`;
- `KMP iOS kit smoke`.

The dedicated `dev` ruleset (id `18553518`) remains disabled. Phase 7.2 preserves both stable
context names, so no repository-settings transition is part of this stage.

## 1. Decision

Phase 7.2 converts **only** `core:ui:navigation` from an Android Compose library to a Kotlin
Multiplatform / Compose Multiplatform library.

The exit claim is deliberately narrow:

> The production navigation contract, typed route hierarchy, result keys and saved-state
> serializer registry compile for Android and the iOS simulator under the pinned Navigation 3
> runtime. Android keeps a reflection-based registry oracle exhaustive for the current direct and
> sealed hierarchy, Kotlin/Native executes a fixed-catalog round-trip oracle, and both native
> Phase-7 tests run inside an already-required CI context.

It does **not** claim that an iOS application, navigation UI, production iOS graph or UIKit window
exists.

### Why this slice is navigation-only

The dependency order is real, but the portability risks are not equal:

- `core:ui:navigation` is 9 production files / 289 physical lines, with zero `android.*` or
  `java.*` imports and no resources, Metro, device tests or goldens;
- it is a direct dependency of `core:ui:mvi` and 15 other modules, so it is the upstream contract
  that every later Store-based UI slice needs;
- `core:ui:mvi` is 29 production files and already crosses Android `Context`/`Activity`, Firebase
  frame metrics, JVM atomics/synchronization, Lifecycle/ViewModel retention and Phase-5 generation
  job ownership;
- combining both modules would mix a mechanical source-set conversion with unresolved runtime,
  telemetry and concurrency decisions.

Phase 7.3 must therefore receive a fresh MVI-specific discovery/spec after this phase merges. A
navigation-only phase is not an `iosApp` shortcut; it is the smallest upstream conversion whose
public contract is already platform-neutral.

## 2. Measured baseline

At the code baseline, `core:ui:navigation` contains:

| Surface | Measured inventory |
| --- | ---: |
| Production Kotlin files | 9 |
| Production physical lines | 289 |
| Concrete `Screen` route leaves | 12 |
| `@Serializable` annotations | 15, including sealed roots |
| Direct `android.*` / `java.*` imports | 0 |
| Android host-test files / source-level tests | 1 / 1 |
| Device tests | 0 |
| Resources / manifests / Paparazzi goldens | 0 / 0 / 0 |
| Direct Gradle consumers | 16 |

The 12 concrete routes are:

1. `Screen.BottomBar.Home`;
2. `Screen.BottomBar.AllExercises`;
3. `Screen.BottomBar.AllTrainings`;
4. `Screen.Training`;
5. `Screen.Exercise`;
6. `Screen.LiveWorkout`;
7. `Screen.Settings`;
8. `Screen.Archive`;
9. `Screen.PastSession`;
10. `Screen.ExerciseChart`;
11. `Screen.ExerciseImage`;
12. `Screen.PlanEditor.Existing`.

The production tree currently lives under the non-standard directory
`src/main/kotlin/io.github/stslex/...` even though the Kotlin package is the normal
`io.github.stslex...`. The conversion corrects the directory shape with a move; it does not change
the package or API.

The single `ScreenSerializationTest` is JVM-specific by design. It uses `kotlin-reflect`,
`KClass.java`, `primaryConstructor` and sealed-subclass reflection to discover all 12 concrete
routes reachable through the current direct/sealed hierarchy without a hand-maintained list, then
round-trips each one through the production
`screenSavedStateConfiguration.serializersModule`. It must remain the current-hierarchy
Android-host oracle.

This reflection is not a global classpath scan. `ScreenWithResult : Screen` is a public non-sealed
marker branch, and the helper intentionally drops abstract/interface nodes. A future route that
implements only `ScreenWithResult` could therefore escape discovery. Phase 7.2 does not redesign
that public marker, but it makes the limitation explicit: any later route must remain directly or
sealed-hierarchy reachable from `Screen`, and route review must update the exact-count baseline.

The classic `convention.composeLibrary` plugin currently applies the Kotlin serialization plugin
implicitly. `convention.kmpComposeLibrary` does not. The converted module must apply serialization
explicitly or generated serializers can disappear while the source-set move appears otherwise
mechanical.

## 3. In scope

The implementation PR must contain all of the following and nothing broader:

1. Apply `convention.kmpComposeLibrary` and the Kotlin serialization plugin to
   `core:ui:navigation`.
2. Move all nine production files to the standard `commonMain` package directory with `git mv`.
3. Preserve the public Navigation 3 runtime edge and declare direct common dependencies for every
   library type exposed or imported by production code.
4. Move the existing current-hierarchy JVM registry test to `androidHostTest`, preserve its
   reflection and round-trip behaviour, sharpen its baseline leaf-count guard from `>= 12` to
   exactly 12, and add an assertion for the pre-conversion no-compatibility JVM interface ABI.
5. Add exactly one Kotlin/Native test that round-trips the fixed catalog of all 12 current concrete
   routes through the production serializer configuration.
6. Extend the existing required `KMP iOS kit smoke` job to execute and identify both the Phase-7.1
   kit scene test and the new navigation native test, without renaming the job/context.
7. Extract the native XML/count/identity oracle from inline workflow code into one checked-in
   script used identically by CI and the known-negative control.
8. Update only documentation made stale by the task/source-set/CI changes and append exact final
   verification evidence to this specification.

## 4. Explicit non-goals

Do not add, convert, redesign or upgrade any of the following:

- `core:ui:mvi`, any specialized UI module, feature module, `app:common` or `app:app`;
- `iosApp`, an Xcode project, an XCFramework, a production framework export, UIKit/SwiftUI host or
  real window;
- `navigation3-ui`, `NavDisplay`, the app-owned back stack or any feature entry provider;
- the `Screen` hierarchy, route payloads, `isSingleTop`, result types or serializer discriminator;
- `Navigator`, `NavigatorReceiver`, `NavResultsSource`, `NavCommand`, `NavResultKey` semantics or
  the Android `NavigatorEventBus` / `NavigatorExt` implementations;
- Decompose, Essenty or any second navigation framework; Navigation 3 remains canonical;
- `iosArm64`, signing, TestFlight, App Store delivery or device-native execution;
- Phase-5 `AppRuntime`, generation admission/retirement, recovery, database replacement, Store
  joining or startup behaviour;
- Kotlin, AGP, Compose, Navigation 3, SavedState, serialization or coroutine version upgrades;
- resources, screenshots, Paparazzi configuration, golden recording or tolerance changes;
- new lint/detekt baselines, broad suppressions or unrelated debt cleanup;
- a repo-wide JVM-default compiler-policy change; this stage preserves the current module ABI
  locally;
- issue #204's classic `androidTest` Detekt gap or issue #205's golden-gate auto-discovery gap.

Navigation 3 UI's missing iOS variant on the pinned AndroidX line is a later `app:common` host
decision. Do not solve it by changing coordinates or adding a parallel navigation stack here.

## 5. Required source-set shape

Use `git mv` for every existing file. Copying a file while its old source remains discoverable is a
failure.

| Current path | Required destination/shape |
| --- | --- |
| `src/main/kotlin/io.github/stslex/workeeper/core/ui/navigation/**` | `src/commonMain/kotlin/io/github/stslex/workeeper/core/ui/navigation/**` |
| `src/test/kotlin/io/github/stslex/workeeper/core/ui/navigation/ScreenSerializationTest.kt` | `src/androidHostTest/kotlin/io/github/stslex/workeeper/core/ui/navigation/ScreenSerializationTest.kt` |
| new native registry test | `src/iosTest/kotlin/io/github/stslex/workeeper/core/ui/navigation/ScreenSerializationIosTest.kt` |

The final module has no production file under `src/main` and needs no production `androidMain` or
`iosMain` actual. All nine production files are common code. The KMP convention may create empty
platform source sets; do not add placeholder files or no-op actuals.

## 6. Production compatibility contract

### 6.1 Navigation 3 stays the only navigation contract

Keep `androidx.navigation3:navigation3-runtime:1.1.6` as an `api` dependency. This is required by
the same public exposures as before:

- `NavGraphScope` names `EntryProviderScope<NavKey>`;
- `NavigatorHolder` names `NavBackStack<NavKey>`;
- `Screen` extends `NavKey`;
- `screenSavedStateConfiguration` exposes the runtime/saved-state serialization contract.

Do not add Navigation 3 UI to this module. It remains an app-host concern and the pinned AndroidX
UI artifact has no iOS target.

### 6.2 Route and saved-state registry

Keep all 12 concrete `Screen` leaves and every current serializer registration byte-for-byte in
meaning. The production registry remains the only registry used by the app and by both tests.

The Android-host oracle remains exhaustive for the current direct/sealed hierarchy: a newly
reachable concrete `Screen` leaf that is not registered fails without anyone updating a test list
first. Phase 7.2 must sharpen its current `>= 12` guard to an explicit
`assertEquals(12, leaves.size, ...)` (or the equivalent exact assertion), and the final verification
record must state the discovered count. A future route-set change must deliberately update that
assertion and its reviewed baseline; the `ScreenWithResult` escape hatch documented in §2 must not
be used to bypass it.

The native oracle uses a fixed test-side list of one non-default sample of every current concrete
route. For nullable constructor parameters, use non-null samples so every field is encoded rather
than omitted. The test must:

- assert that the catalog contains exactly 12 instances and that
  `catalog.map { it::class }.toSet()` equals the exact 12-class set listed in §2; instance equality
  or a class-count alone is insufficient;
- create `Json` from `screenSavedStateConfiguration.serializersModule`;
- encode and decode each route through `PolymorphicSerializer(NavKey::class)`;
- assert equality after every round-trip;
- assert exact keys
  `"nav-result:${Screen.PlanEditor::class.qualifiedName}"` and
  `"nav-result:${Screen.PlanEditor.Existing::class.qualifiedName}"`, then assert they differ. This
  pins both the literal prefix and the destination-class discriminator.

This fixed catalog is not the hierarchy-change detector; the JVM reflection test fills that role
within the limit documented in §2. The native test proves that the exact production registry and
generated serializers execute under Kotlin/Native for all routes that exist at this baseline.

### 6.3 Public API and behaviour

The move must not change a public declaration, visibility, package, generic bound, annotation,
default argument, equality contract or KDoc invariant. In particular:

- `NavCommand.PopBackWithResult` keeps its string key and `Any` payload;
- `NavResultsSource` keeps nullable `StateFlow<Any?>` channels;
- `Navigator.popBackWithResult` remains typed by `ScreenWithResult<R>`;
- `NavResultKey.of` remains keyed by the destination `KClass` and keeps its prefix;
- `NavigatorHolder.currentScreen` keeps the current last-entry cast semantics;
- `navScreen` and `navScreenWithResults` keep their current entry registration behaviour;
- `restartApp` and `openRecovery` remain abstract commands whose Android execution stays outside
  this module.

No `expect`/`actual`, platform singleton, service locator or CompositionLocal is necessary or
allowed in this stage.

## 7. Build and dependency contract

### 7.1 Plugins

`core:ui:navigation` must apply:

- `convention.kmpComposeLibrary` for Android + `iosSimulatorArm64`, Compose runtime annotations,
  lint, Detekt and repository task aliases;
- `org.jetbrains.kotlin.plugin.serialization` explicitly.

The classic convention currently compiles this module with `-Xjvm-default=all`. Because the KMP
convention does not override Kotlin 2.4's compatibility-emitting `ENABLE` default, configure this
module's `KotlinJvmCompile` tasks with the stable `JvmDefaultMode.NO_COMPATIBILITY` property. Do not
copy the legacy flag and do not change the shared convention in this phase. This preserves direct
default getters on both `Screen` and `Screen.BottomBar` without generating either nested
`DefaultImpls` class.

It must not apply Metro, Paparazzi, Room/KSP or an Android application/library convention alongside
the KMP convention.

### 7.2 Source-set dependencies

Use direct dependencies rather than the classic Android convention's former transitive closure:

| Source set | Required dependencies |
| --- | --- |
| `commonMain` | `api` Navigation 3 runtime; `api` SavedState because `SavedStateConfiguration` is public; `api` coroutines core because `StateFlow` is public; `api` kotlinx-serialization core because `SerializersModule` is public and `Screen` serializers are generated |
| `androidHostTest` | kotlinx-serialization JSON and `kotlin("reflect")` |
| `iosTest` | `kotlin("test")` and kotlinx-serialization JSON |

Add catalog aliases for `org.jetbrains.kotlinx:kotlinx-serialization-core` on the already-pinned
serialization version and for `androidx.savedstate:savedstate:1.4.0`. The SavedState pin makes the
already-resolved Navigation 3 API dependency explicit; it is not permission to change the
effective version. Do not bump either version and do not place JSON on the production classpath
solely because tests use it.

`@Stable` remains backed by the Compose runtime dependency that
`convention.kmpComposeLibrary` already adds directly to `commonMain`; do not duplicate that
convention-owned edge in the module.

Confirm with the exact dependency report below that every selected artifact resolves an
`iosSimulatorArm64` variant. A successful Android compile is not evidence of native resolution.

### 7.3 Existing KMP aliases

Use the aliases already registered by `KmpLibraryConventionPlugin`:

- `assembleDebug -> assemble`;
- `testDebugUnitTest ->` Android host `Test` tasks;
- `lintDebug -> lint`;
- `assembleDebugAndroidTest -> assembleAndroidDeviceTest`;
- `connectedDebugAndroidTest -> connectedAndroidDeviceTest`.

Do not add a module-local alias. This module has no device test or Paparazzi task; an empty
device-test source set is not evidence and must not be reported as a navigation test gate.

## 8. Test migration and native proof

### 8.1 Android host

Move `ScreenSerializationTest` to `androidHostTest`. Keep JUnit 5, JVM reflection and its production
registry round-trip. Change only the leaf-count guard as required by §6.2. The focused
post-conversion run must produce current JUnit XML with exactly one executed, zero skipped and zero
failed test named
`every concrete Screen leaf round-trips through the production registry`.

Inside that one test, the discovered concrete-leaf count must be exactly 12 at this baseline. A
future route addition is allowed only by a separately reviewed route change that updates this
specification's recorded baseline; Phase 7.2 adds none.

The same test method must prove the retained JVM interface ABI: both `isSingleTop` getters are Java
default methods, and reflective loading of
`io.github.stslex.workeeper.core.ui.navigation.Screen$DefaultImpls` and
`io.github.stslex.workeeper.core.ui.navigation.Screen$BottomBar$DefaultImpls` throws
`ClassNotFoundException`. Keep these assertions inside the existing test so the host XML contract
remains exactly one executed case.

### 8.2 iOS simulator

Add exactly one test method under `iosTest`, named
`allCurrentRoutesRoundTripThroughProductionRegistry`. It must perform every assertion in §6.2 and
produce exactly one executed / zero skipped / zero failed case in
`iosSimulatorArm64Test` XML.

Do not use a sentinel assertion, an independently constructed serializers module, an Android
Bundle, reflection-based leaf discovery or a Compose scene. The existing kit test already proves a
native Compose scene; this test proves the distinct Navigation 3 serialization contract.

This is a Kotlin/Native simulator test. It does not prove a framework link, Xcode embedding,
`ComposeUIViewController`, `NavDisplay`, UIKit attachment, application launch or state restoration
across an actual iOS process death.

## 9. CI and required-check contract

Keep the workflow job id `kmp-ios-kit-smoke` and stable check name `KMP iOS kit smoke`. The name is
already required by ruleset `8116593`; renaming it would create an avoidable settings transition.
Treat the name as a stable historical context while expanding its payload.

Update the job so one forced Gradle invocation executes both:

```bash
./gradlew \
  :core:ui:kit:iosSimulatorArm64Test \
  :core:ui:navigation:iosSimulatorArm64Test \
  --rerun-tasks --no-build-cache --no-configuration-cache \
  --continue --full-stacktrace --console=plain
```

Extract the current inline assertion to `.github/scripts/assert_kmp_ios_smoke.py` and call that
script from the workflow after Gradle. Parse XML structurally; concatenated substring checks are not
admissible. The script must independently require one exact `<testcase>` tuple from each exact
module result directory:

| Module | Exact result | Required identity |
| --- | --- | --- |
| `core:ui:kit` | 1 executed / 0 skipped / 0 failed | classname `io.github.stslex.workeeper.core.ui.kit.IosKitSceneSmokeTest`; name `sheetLayoutRendersMigratedStringFontAndIcon[iosSimulatorArm64]` |
| `core:ui:navigation` | 1 executed / 0 skipped / 0 failed | classname `io.github.stslex.workeeper.core.ui.navigation.ScreenSerializationIosTest`; name `allCurrentRoutesRoundTripThroughProductionRegistry[iosSimulatorArm64]` |

Fresh checkout plus exact per-module paths and identities are load-bearing. A total of two tests
without per-module checks is insufficient: two kit tests and zero navigation tests would pass it.
Two independent substrings are also insufficient because a classname from one case and a method
from another can form a false identity.

The script must fail on a missing directory/XML/testcase, duplicate testcase, nonzero
failure/error/skip count or mismatched exact tuple, and print both verified identities on success.

Upload both exact Native result directories under `if: always()`:

- `core/ui/kit/build/test-results/iosSimulatorArm64Test/`;
- `core/ui/navigation/build/test-results/iosSimulatorArm64Test/`.

Keep the Xcode 26.6 selection,
simulator-runtime assertion, JDK 21, Kotlin/Native cache and ephemeral signing material exactly as
the Phase-7.1 job already defines them.

The Linux `Build and Unit Tests` job remains unchanged. Its root `assembleDebug` and
`testDebugUnitTest` commands automatically reach the converted module through the established KMP
aliases, but they do not replace the macOS native execution gate.

Before merge, re-read the ruleset API and record that the active `~ALL` ruleset still requires both
stable contexts. No owner settings change is expected or authorized by this stage.

## 10. Compatibility invariants

The implementation PR is rejected if any of these changes:

- the 12-route set, route payload/default values or `isSingleTop` values;
- the serializer registrations or production saved-state configuration;
- `NavResultKey` identity or result-channel semantics;
- public Kotlin package names, signatures or visibility;
- the no-compatibility JVM default-method ABI of `Screen` / `Screen.BottomBar`;
- Android app navigation, back-stack ownership, event-bus behaviour or process-death restoration;
- any consumer's dependency on the public Navigation 3 runtime types;
- the existing kit native test or its 1/0/0 result;
- any of the 456 committed golden PNG bytes or the 13 live visual gates;
- the existing Android Smoke / Regression suite membership;
- the two required GitHub check names.

Metro remains the sole DI framework. This module does not apply it because it owns no binding.

## 11. Verification gates

Every executing build, test or lint Gradle verification below must include:

```text
--rerun-tasks --no-build-cache --no-configuration-cache --full-stacktrace --console=plain
```

Run Detekt separately. Quote every final Gradle summary line. A critical task reported only
`UP-TO-DATE`, `FROM-CACHE`, `NO-SOURCE` or with zero current XML cases is not evidence.
The explicit dependency report and `--dry-run` graph inspections are exempt from the rerun and
build-cache flags because they do not produce execution evidence. They still disable configuration
cache and use plain console output as shown.

### 11.1 Characterization before edits

Record the exact source inventory in §2, the current task result and JUnit XML identity for:

```bash
./gradlew :core:ui:navigation:testDebugUnitTest <forced flags>
```

It must execute the one current host test. Its baseline source currently guards only `>= 12` and
does not print the discovered count, so do not claim that its Gradle/JUnit output proves exact 12;
the pre-edit exact count comes from the static route/registry inventory in §2. The post-conversion
test's new exact assertion supplies the runtime count proof. Also record:

- a sorted `relative path + SHA-256` manifest for all 456 repository golden PNGs;
- the 13 modules/directories applying the visual gate;
- current API-34 Smoke and Regression XML totals if the implementation baseline differs from the
  Phase-7.1 record.

If any source/test/golden count differs from the baseline without an already-merged documented
reason, stop before moving files.

### 11.2 Focused post-conversion gates

```bash
./gradlew :core:ui:navigation:clean \
  --no-configuration-cache --full-stacktrace --console=plain
./gradlew :core:ui:navigation:dependencies \
  --configuration iosSimulatorArm64CompileKlibraries \
  --no-configuration-cache --full-stacktrace --console=plain
./gradlew :core:ui:navigation:dependencies \
  --configuration iosSimulatorArm64TestCompileKlibraries \
  --no-configuration-cache --full-stacktrace --console=plain
./gradlew :core:ui:navigation:compileKotlinIosSimulatorArm64 <forced flags>
./gradlew :core:ui:navigation:assembleDebug <forced flags>
./gradlew :core:ui:navigation:testDebugUnitTest <forced flags>
./gradlew :core:ui:navigation:iosSimulatorArm64Test <forced flags>
./gradlew :core:ui:navigation:lintDebug <forced flags>
./gradlew detekt <forced flags>
```

Also prove the alias graphs without executing a record or device task:

```bash
./gradlew :core:ui:navigation:assembleDebug --dry-run \
  --no-configuration-cache --full-stacktrace --console=plain
./gradlew :core:ui:navigation:testDebugUnitTest --dry-run \
  --no-configuration-cache --full-stacktrace --console=plain
./gradlew :core:ui:navigation:lintDebug --dry-run \
  --no-configuration-cache --full-stacktrace --console=plain
```

The graphs must reach `compileKotlinIosSimulatorArm64`, `testAndroidHostTest` and `lint`
respectively.

### 11.3 Repository gates

Run the unified Android workflow battery in its current order, with forced flags:

```bash
./gradlew assembleDebug <forced flags>
./gradlew assembleDebugAndroidTest <forced flags>
./gradlew verifyPaparazziDebug <forced flags>
./gradlew :lint-rules:test <forced flags>
./gradlew detekt <forced flags>
python3 documentation/personal_data_gate.py -v
./gradlew lintDebug <forced flags>
./gradlew testDebugUnitTest <forced flags>
```

Root `verifyPaparazziDebug` must emit 13 live-gate markers summing to 456 images. The post-change
golden SHA-256 manifest must be identical to the pre-edit manifest, with zero nested snapshot files
and no PNG in the diff. Never execute `recordPaparazzi*`.

On an API-34 emulator, re-establish the repository navigation/UI oracles because all shipping
feature routes consume this contract:

```bash
./gradlew connectedDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.annotation=io.github.stslex.workeeper.core.ui.test.annotations.Smoke \
  <forced flags>
./gradlew connectedDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.annotation=io.github.stslex.workeeper.core.ui.test.annotations.Regression \
  <forced flags>
```

The current canonical totals are Smoke 44 discovered / 41 executed / 3 named skips and Regression
81/81. Re-characterize rather than rewriting the expectation if the pre-edit baseline differs.

### 11.4 Known-negative controls

Run each control only after its corresponding green baseline. Use
`documentation/mockups/mutation_harness.py` for every source mutation; it restores exact bytes in
its `finally`. Then re-run the green gate.

1. Remove one real `subclass(...)` registration, preferably a parameterized route such as
   `Screen.ExerciseImage`. Apply the same mutation in two separate harness invocations: one runs
   only the host registry test and expects `RED`, then the other independently runs only the
   native fixed-catalog test and expects `RED`. Each must fail at runtime for the missing production
   serializer; a compile failure is not the expected verdict. Restore and rerun each corresponding
   green gate. Do not combine both tasks in one harness invocation: a single failure would mask a
   missing second oracle.
2. Add a real `android.os.Build` reference to one production `commonMain` file. The explicit
   `compileKotlinIosSimulatorArm64` gate must fail with an unresolved Android reference. In the
   mutation harness this expected compiler kill is `INVALID`, not `RED`.
3. Immediately before the identity mutation, run the exact two-task Native command from §9 and the
   checked-in `.github/scripts/assert_kmp_ios_smoke.py` green so both module directories contain
   fresh proof. Then use the harness to temporarily rename only the native navigation test method
   and run its navigation Gradle task with expected `GREEN`; include the full forced flags in
   `--task` because the harness adds only `--rerun-tasks --no-build-cache`. The harness verdict is
   merely a staging result: a name change is intentionally behaviour-neutral. After the harness
   restores the source, run the script against that just-produced XML without rerunning Gradle. It
   must fail specifically on the navigation tuple mismatch, not on missing/stale kit XML. Finally,
   rerun the exact two-task Native command and the script green. This proves the required context
   checks identity rather than only the Gradle exit code.
4. Change only `NavResultKey.PREFIX` to a different non-empty literal. Run the native fixed-catalog
   test alone and require `RED` on the exact key assertion, then restore and rerun it green. This
   proves the key-prefix invariant is executable rather than inferred from two unequal keys.
5. Change only the module-local JVM-default mode from `NO_COMPATIBILITY` to `ENABLE`. Run the host
   test alone and require `RED` because at least one forbidden `DefaultImpls` class becomes
   loadable. After restoration, clean the module output before rerunning the host test green so a
   mutation-generated class cannot survive as a stale false failure. A compilation failure is not
   the intended result.

The final worktree must contain none of the mutation artifacts.

## 12. Commit decomposition

Keep the implementation PR bisect-green:

1. **Atomic module conversion:** plugin/dependency wiring, all `git mv` operations and both test
   source-set shapes in one commit. Do not land an intermediate commit where production or the
   registry test disappears from discovery.
2. **Native required gate:** expand the existing macOS job and its exact XML/identity assertion,
   preserving the stable context name.
3. **Evidence and documentation:** update only stale navigation/CI testing text and append exact
   positive/negative results to this specification.

If commit 1 cannot remain green as one atomic conversion, keep it atomic rather than manufacturing
an intermediate false green.

## 13. Documentation and comment budget

This file owns the migration rationale. Preserve production comments whose invariant remains true;
do not add move narration or Phase history to Kotlin files.

Update `documentation/ci-cd.md` so `KMP iOS kit smoke` is described honestly as a stable required
context running the kit scene plus navigation registry tests. Update `documentation/testing.md`
with the second native command, and update `documentation/features.md`'s canonical `Screen.kt`
path from `src/main` to `src/commonMain`. Do not rewrite unrelated architecture sections.

## 14. Exit criteria

Phase 7.2 is complete only when all statements below are true:

- all nine production files compile from `commonMain` for Android and `iosSimulatorArm64`;
- no duplicate production tree remains under `src/main` and no placeholder platform actual exists;
- the Android-host current-hierarchy registry test executes 1/1 and discovers all 12 concrete
  routes;
- the Native fixed-catalog registry test executes 1/1 and round-trips all 12 routes through the
  production configuration;
- the two JVM interface getters remain default methods with no generated `DefaultImpls`, and the
  mode-change mutation reddens that ABI oracle;
- the exact `nav-result` keys are verified and the prefix mutation reddens the Native oracle;
- the missing-registration mutation fails both oracles for the intended runtime reason;
- the Android-import mutation fails the native compile for the intended reason;
- all 16 direct Gradle consumers and the shipping app compile without public API changes;
- the root Android gate battery is green with executed evidence;
- API-34 Smoke and Regression totals are re-established;
- root visual verification reports 13 live gates / 456 images and every PNG hash is unchanged;
- the required `KMP iOS kit smoke` context independently identifies the kit and navigation tests;
- the active ruleset covering `dev` still requires `Build and Unit Tests` and
  `KMP iOS kit smoke`;
- no MVI/feature/app-root/runtime/iosApp conversion or navigation dependency replacement is present;
- no effective dependency version was upgraded or downgraded, and no suppression, baseline,
  schema or generated credential changed;
- this file contains the exact final task summaries, XML identities/counts and negative-control
  evidence.

## 15. Stop conditions

Stop implementation and report instead of improvising if any of these occurs:

- the implementation base contains non-documentation drift after the pinned code baseline;
- Navigation 3 runtime or any direct production dependency lacks an `iosSimulatorArm64` variant on
  the pinned version;
- the production `SavedStateConfiguration` / polymorphic serializer registry cannot execute on
  Kotlin/Native without changing route semantics;
- conversion requires `navigation3-ui`, a new coordinate family, Decompose, a feature change or an
  `app:common` change;
- any public signature, route payload/default, result key or serializer discriminator must change;
- an expected host/native task is `NO-SOURCE`, produces zero/stale XML or passes without naming the
  expected test;
- the stable required CI context would need to be renamed or removed;
- any golden hash, device-suite membership or Android navigation behaviour changes;
- a proposed fix needs a dependency upgrade, new suppression/baseline, `iosArm64`, Xcode host or
  Phase-5 runtime/recovery work.

## 16. Next stage boundary

Only after Phase 7.2 merges should a fresh spec select `core:ui:mvi`. That discovery must decide,
with tests in both directions:

- a common-safe replacement for `AtomicBoolean` and JVM-only `@Synchronized`;
- how each Store receives the **current** `AppScopeLifetime` generation job through Metro/injection
  instead of `Context.appDeps`, without an autonomous job, nullable production fallback, global
  holder or DI CompositionLocal;
- how Android Firebase frame metrics remain real behind a narrow platform seam while native tests
  use a deterministic non-production substitute;
- Lifecycle/ViewModel KMP retention, `onCleared`, disposal and generation-join semantics;
- why `AppDepsHolder` remains Android-only until feature graph factories gain an explicit host/root
  contract.

That later stage must not alter Phase-5 publication/admission/retirement semantics. If it cannot
separate MVI from those semantics, it stops for a dedicated runtime-boundary decision.

The first real UIKit-window claim remains later still: a permanent repository-owned host must call
the production `app:common/App()` path and prove attachment/interaction with XCTest and XCUITest.
A throwaway screen or parallel root is not admissible.

## 17. Implementation-agent handoff

Use this exact scope only after the docs-only specification PR merges:

> Implement `documentation/feature-specs/kmp-phase-7-2-navigation.md` from the current `dev` head.
> Verify that its code/build/workflow state matches code baseline
> `ba367fe96ac3cf96ccc58a2f419e11b684df27e2` before editing. Work on a branch; convert only
> `core:ui:navigation`; preserve every public route/result/navigation invariant; execute every
> required positive and negative gate; append the exact verification record; and open one PR to
> `dev`. Preserve the required `Build and Unit Tests` and `KMP iOS kit smoke` context names. Do not
> implement MVI, features, `app:common`, `iosApp`, UIKit, Navigation 3 UI or Phase-5 runtime work.
> Stop and report if the baseline or a locked invariant has drifted.

## 18. Final verification record (implementation, 2026-08-27)

Implemented on `feature/kmp-phase-7-2-navigation`, forked from `dev` at
`8b7cfea4729a140199cdde3a38f436e9482092d5` (PR #258 merge; verified
`git diff --stat ba367fe96..origin/dev` shows only the two Phase-7 documentation files, so
production/test/build-logic/catalog/workflow state is exactly the code baseline). Commit
decomposition per §12: `612cb1e7e` (atomic module conversion), `db3b77c5e` (native required
gate), plus the evidence/documentation commit that carries this record. Every Gradle
verification below ran with `--rerun-tasks --no-build-cache --no-configuration-cache
--full-stacktrace --console=plain` (dependency reports and `--dry-run` graphs per the §11
exemption); every quoted summary line reads `N actionable tasks: N executed`, and every count
was read from produced JUnit XML, never from an exit code.

### 18.1 Pre-edit characterization (§11.1, at 8b7cfea47)

- Source inventory matches §2 exactly: 9 production Kotlin files / 289 physical lines under
  `src/main/kotlin/io.github/…`, 1 host-test file, 12 concrete routes, 12 `subclass(...)`
  registrations, 15 `@Serializable`, 0 `android.*`/`java.*` imports, 16 direct Gradle consumers.
- `:core:ui:navigation:testDebugUnitTest` (forced): "36 actionable tasks: 36 executed"; XML
  `tests="1" skipped="0" failures="0" errors="0"`, testcase
  `every concrete Screen leaf round-trips through the production registry()` in
  `io.github.stslex.workeeper.core.ui.navigation.ScreenSerializationTest`. (The pre-edit source
  guards only `>= 12`; the exact pre-edit count is the static §2 inventory, per §11.1.)
- Golden baseline: sorted path + SHA-256 manifest of all 456 committed PNGs recorded; 13
  modules apply `golden-gate.gradle.kts` (kit + plan-editor + start-mode + the ten feature
  modules).
- Effective versions before the conversion (`:app:dev` `debugRuntimeClasspath`
  `dependencyInsight`): `kotlinx-serialization-core:1.11.0`,
  `androidx.savedstate:savedstate:1.4.0`; the module's own classpath already resolves
  SavedState `1.4.0` through Navigation 3 `1.1.6`.
- Environment: Xcode 26.6 (17F113) with the iOS 26.5 simulator runtime; `Pixel_6_API_34` AVD.

### 18.2 Focused post-conversion gates (§11.2)

| Gate | Summary line | Evidence |
| --- | --- | --- |
| `:core:ui:navigation:dependencies --configuration iosSimulatorArm64CompileKlibraries` | BUILD SUCCESSFUL | nav3-runtime 1.1.6, savedstate 1.4.0, serialization-core 1.11.0, coroutines-core 1.11.0, Compose runtime 1.11.1 — every selected artifact resolves an `iosSimulatorArm64` variant |
| `…iosSimulatorArm64TestCompileKlibraries` | BUILD SUCCESSFUL | kotlin-test 2.4.10; `kotlinx-serialization-json-iossimulatorarm64:1.11.0` listed |
| `compileKotlinIosSimulatorArm64` | 11 actionable tasks: 11 executed | green |
| `assembleDebug` (module) | 45 actionable tasks: 45 executed | green |
| `testDebugUnitTest` (module) | 35 actionable tasks: 35 executed | XML 1/0/0; the one testcase carries the exact-12 assertion and the ABI assertions (both `isSingleTop` getters `Method.isDefault`, both `DefaultImpls` binary names throw `ClassNotFoundException`) |
| `iosSimulatorArm64Test` (module) | 31 actionable tasks: 31 executed | XML 1/0/0, testcase `allCurrentRoutesRoundTripThroughProductionRegistry[iosSimulatorArm64]` |
| `lintDebug` (module) | 23 actionable tasks: 23 executed | green |
| `detekt` (root, separate run) | 57 actionable tasks: 57 executed | green |
| `--dry-run` alias graphs | — | `assembleDebug` reaches `compileKotlinIosSimulatorArm64`; `testDebugUnitTest` reaches `testAndroidHostTest`; `lintDebug` reaches `lint` |

Encoding note a reviewer should not rediscover: the Kotlin/Native Gradle test task prefixes
every XML classname with its own name — the produced attribute is
`iosSimulatorArm64Test.io.github.stslex.workeeper.core.ui.navigation.ScreenSerializationIosTest`,
and the kit's Phase-7.1 XML has the same shape. `.github/scripts/assert_kmp_ios_smoke.py`
therefore strips exactly that one known prefix and then requires full equality; an `endswith`
would accept a foreign package.

### 18.3 Repository gates (§11.3)

| Gate | Summary line |
| --- | --- |
| `assembleDebug` | 1116 actionable tasks: 1116 executed |
| `assembleDebugAndroidTest` | 1960 actionable tasks: 1960 executed |
| `verifyPaparazziDebug` | 617 actionable tasks: 617 executed — 13 "Visual gate live" lines summing to 456 executed golden cases |
| `:lint-rules:test` | 9 actionable tasks: 9 executed |
| `detekt` | 57 actionable tasks: 57 executed |
| `python3 documentation/personal_data_gate.py -v` | exit 0 |
| `lintDebug` | 1094 actionable tasks: 1094 executed |
| `testDebugUnitTest` | 1128 actionable tasks: 1128 executed |

Golden identity: the post-battery sorted path + SHA-256 manifest of all 456 committed PNGs is
byte-identical to the pre-edit manifest; the diff contains no PNG and no nested snapshot file.
Effective versions after the conversion are unchanged
(`kotlinx-serialization-core:1.11.0`, `androidx.savedstate:savedstate:1.4.0` at `:app:dev`).

### 18.4 Device suites (§11.3, API-34 emulator `Pixel_6_API_34`, animations off, forced flags, `--continue`)

| Suite | Result |
| --- | --- |
| `@Smoke` | "2033 actionable tasks: 2033 executed" — fresh connected XML sums to **44 discovered / 41 executed / 3 skipped / 0 failures** (the three pre-existing named skips in all-exercises, all-trainings, archive) |
| `@Regression` | "2033 actionable tasks: 2033 executed" — fresh connected XML sums to **81 discovered / 81 executed / 0 skipped / 0 failures** |

Counting note: connected XMLs are per-module disk files and a prior day's leftover under a
different variant directory (`connected/debug/` beside the current `connected/androidMain/`)
inflates a naive recursive sum — the Smoke total was read from the current run's files only,
after deleting the one stale kit XML. Same false-total family as the Phase-5 evidence-count
correction.

### 18.5 Known-negative controls (§11.4)

All source mutations ran through `documentation/mockups/mutation_harness.py` (one-shot mode,
which restores exact bytes in its `finally`); every `--task` carried the full §11 flag set on
top of the harness's own `--rerun-tasks --no-build-cache`. Each control ran only after its
green baseline, and each corresponding gate was re-proven green after restoration.

1. **Missing registration, two separate invocations.** Deleting
   `subclass(Screen.ExerciseImage::class)` from the production registry: the host oracle
   invocation → `RED (1 test(s))`, `ScreenSerializationTest > every concrete Screen leaf
   round-trips through the production registry() FAILED`, "35 actionable tasks: 35 executed"
   (a runtime serializer-lookup failure, not a compile failure); the independent native
   invocation → `RED (1 test(s))`,
   `…ScreenSerializationIosTest.allCurrentRoutesRoundTripThroughProductionRegistry[iosSimulatorArm64] FAILED`,
   "31 actionable tasks: 31 executed". Both restored gates re-proven green (59/59 executed,
   XML 1/0/0 each).
2. **`android.os.Build` in `commonMain`.** A fully-qualified `android.os.Build.VERSION.SDK_INT`
   reference in `NavCommand.kt`: `compileKotlinIosSimulatorArm64` failed with
   `Unresolved reference 'android'` at the mutated line — harness verdict
   `INVALID — DID NOT COMPILE` with `--expect INVALID: OK`, as §11.4.2 prescribes. Restored
   compile green (11/11 executed).
3. **Native XML identity.** The exact §9 two-task command plus
   `.github/scripts/assert_kmp_ios_smoke.py` ran green first (59 actionable tasks: 59
   executed; both identities printed). The harness then renamed only the native navigation
   test method (`…Renamed`): its Gradle task stayed green ("31 actionable tasks: 31
   executed", staging verdict GREEN as intended). With the source restored and **no Gradle
   rerun**, the script failed exactly on the navigation tuple —
   `core:ui:navigation: testcase identity mismatch — … got
   name='allCurrentRoutesRoundTripThroughProductionRegistryRenamed[iosSimulatorArm64]'` —
   not on missing/stale kit XML. The two-task command and script then re-ran green. The
   required context checks identity, not the exit code.
4. **`NavResultKey.PREFIX` mutation.** `"nav-result"` → `"mutated-prefix"`: the native oracle
   alone went `RED (1 test(s))` on the exact-key assertion ("31 actionable tasks: 31
   executed"); restored and re-proven green with fresh 1/0/0 XML.
5. **`JvmDefaultMode.NO_COMPATIBILITY` → `ENABLE`.** The host oracle alone went
   `RED (1 test(s))` — the forbidden `DefaultImpls` class became loadable, failing the
   `ClassNotFoundException` assertion ("35 actionable tasks: 35 executed", not a compile
   failure). After restoration the module output was cleaned
   (`:core:ui:navigation:clean`) so no mutation-generated bytecode could survive, then the
   host test re-ran green (35/35 executed, PASSED).

The final worktree contains no mutation artifact: after all controls, `git status` shows only
the intended documentation edits and the golden manifest re-check stayed byte-identical.

### 18.6 CI / ruleset

Recorded at PR-open time in the pull-request thread; the active repository-wide ruleset `all`
(id `8116593`, matching `~ALL`) requires `Build and Unit Tests` and `KMP iOS kit smoke`, as
re-read via the ruleset API before merge. The `KMP iOS kit smoke` payload change (two forced
native tasks + the checked-in identity script) preserves both stable context names; no
repository-settings change was made.
