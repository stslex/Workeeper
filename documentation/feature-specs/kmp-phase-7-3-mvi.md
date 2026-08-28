# KMP Phase 7.3 — `core:ui:mvi` becomes the shared Store runtime

**Status:** COMPLETE — PR #262 merged into `dev` on 2026-08-28; implementation and verification
records remain in §20

**Target branch:** `dev`

**Documentation baseline:** `adf15d7274c6c58666f78540ace292ad2ed97cbd` (PR #260 merge)

**Code baseline:** `adf15d7274c6c58666f78540ace292ad2ed97cbd`; its production,
test, build and workflow tree is byte-identical to
`19c2d2cc5f2156ff4c6e86ca23029c20b3d08e2b` because the intervening PR #260 changed
only the Phase-5 and Phase-7.2 delivery records

**Delivery:** PR #262 · head `8469ae5f2f9da761f771ffd00132ed3d910f848c` · merge
`8eacd149085db44aa297961bb19e9e563daea274`; all five PR-head checks passed, all ten review
threads were resolved, and the active `~ALL` ruleset remained unchanged

## 0. Authority and entry condition

This document is the implementation authority for Phase 7.3. It is derived from the exact baseline
above and from:

- `AGENTS.md`;
- `documentation/feature-specs/kmp-phase-2-probes.md`, especially the Phase-7
  source-set, native-test and Android-release rules;
- `documentation/feature-specs/kmp-phase-6-data-layer.md`, especially its KMP/Metro
  findings and negative-control discipline;
- `documentation/feature-specs/kmp-phase-7-1-ui-kit.md`;
- `documentation/feature-specs/kmp-phase-7-2-navigation.md`;
- `documentation/feature-specs/kmp-phase-5-startup-processor.md`;
- `documentation/architecture.md`, `documentation/performance.md`,
  `documentation/testing.md` and `documentation/ci-cd.md`.

The baseline was re-read after PR #260 merged on 2026-08-27:

- `dev` points exactly to `adf15d7274c6c58666f78540ace292ad2ed97cbd`;
- comparing the Phase-7.2 merge
  `19c2d2cc5f2156ff4c6e86ca23029c20b3d08e2b` to `dev` reports only two
  documentation files changed;
- no pull request is open;
- the proposed specification branch did not exist at discovery time.

The repository-wide ruleset `all` (id `8116593`, active for
`~ALL`, last updated `2026-08-27T01:06:29.887+03:00`) still requires signed
commits and these stable GitHub Actions contexts:

- `Build and Unit Tests`;
- `KMP iOS kit smoke`.

The dedicated `dev` ruleset (id `18553518`) remains disabled. Phase 7.3 may
extend the work performed inside the two required contexts but must not rename either context or
change repository settings.

Before implementation, re-fetch `dev` and compare it with this baseline. If production,
test, build-logic, catalog or workflow state has changed, stop and repeat discovery instead of
applying this plan by analogy.

## 1. Decision and exit claim

Phase 7.3 converts `core:ui:mvi` from a classic Android Compose library to a Kotlin
Multiplatform / Compose Multiplatform library targeting Android and
`iosSimulatorArm64`.

Three architectural decisions are binding:

1. Every production `BaseStore` receives the exact current-generation
   `AppScopeLifetime` directly through Metro constructor injection. Store lifetime is not
   recovered through Android `Context`.
2. Performance actions keep a common façade. Android continues to use real Firebase traces and
   frame metrics; iOS uses an explicit no-op backend and makes no telemetry-parity claim.
3. The Store consume guard becomes a stable common `@Volatile` Boolean, and the private
   JVM monitor around `MutableSharedFlow` emission is removed only with a deterministic
   buffer-pressure losslessness oracle.

The exit claim is deliberately bounded:

> The existing MVI/Store contracts, Metro-backed Store retention, lifecycle disposal, typed
> navigation-result bridge and runtime-generation job ancestry compile for Android and the iOS
> simulator. Kotlin/Native executes the production Store/processor path. Android retains its real
> Firebase implementations. All 15 current Android consumers still compile and Android remains
> releasable.

This phase does not claim that an iOS application, iOS dependency graph, UIKit window, production
native feature graph, Firebase-on-iOS integration or platform telemetry parity exists.

## 2. Measured baseline

At the code baseline:

| Surface | Measured inventory |
| --- | ---: |
| Production Kotlin files | 29 |
| Production physical lines | 1,194 |
| Files with direct `android.*` or `java.*` imports | 4 |
| Files with direct Firebase imports | 2 |
| Existing host-test files / tests | 2 / 11 |
| Existing device-test files / tests | 2 / 2 |
| Concrete production `BaseStore` subclasses | 13 |
| Direct Gradle consumers | 15 |
| Production imports from `core:ui:kit` | 0 |
| Resources / manifests / Paparazzi goldens owned by the module | 0 / 0 / 0 |

The four direct platform files are:

- `BaseStore.kt` — JVM `AtomicBoolean` and `@Synchronized`;
- `di/AppDepsHolder.kt` — Android `Context`;
- `performance/FirebaseScreenRenderRecorder.kt` — Activity, Firebase frame metrics and
  `ConcurrentHashMap`;
- `processor/StoreProcessor.kt` — `LocalActivity`,
  `LocalContext` and the Context-backed generation lookup.

`performance/PerformanceRecorder.kt` is Android-only for a different reason: it stores
Firebase `Trace` instances. `RecordAction.kt` is already platform-neutral.

The 15 direct consumers are `app:app`, `app:common` and these 13 feature
modules:

| Feature consumers |
| --- |
| `feature:all-exercises` |
| `feature:all-trainings` |
| `feature:app-dialogs:impl` |
| `feature:archive` |
| `feature:exercise-chart` |
| `feature:exercise` |
| `feature:home` |
| `feature:image-viewer` |
| `feature:live-workout` |
| `feature:past-session` |
| `feature:plan-editor` |
| `feature:settings` |
| `feature:single-training` |

All 15 consumers remain classic Android modules. They consume the Android variant of the converted
MVI module; this phase does not require or permit converting any of them.

The current tests are:

- `NavigationResultContractTest` — seven pure contract cases;
- `StoreGenerationJoinTest` — four cases, including one nullable-generation fallback
  that this specification deliberately removes;
- `AppFeatureScopeTest` plus `AppFeatureProbe` — two
  `@Smoke` device cases.

The latest recorded repository verification baseline from Phase 7.2 is:

| Gate | Recorded baseline |
| --- | ---: |
| `assembleDebug` | 1,116 actionable tasks |
| `assembleDebugAndroidTest` | 1,960 actionable tasks |
| `verifyPaparazziDebug` | 617 actionable tasks |
| `:lint-rules:test` | 9 actionable tasks |
| root `detekt` | 57 actionable tasks |
| root `lintDebug` | 1,094 actionable tasks |
| root `testDebugUnitTest` | 1,128 actionable tasks |
| committed golden PNGs | 456 |
| live golden gates | 13 |
| Smoke device suite | 44 discovered / 41 executed / 3 named skips / 0 failures |
| Regression device suite | 81 executed / 0 skipped / 0 failures |

Actionable-task counts are historical evidence, not equality gates: the KMP target and new tests
must legitimately change the task graph. Golden hashes, golden membership, the two MVI device
cases and the whole device-suite totals must not change.

## 3. In scope

The later implementation PR must contain all of the following:

1. Convert only `core:ui:mvi` to `convention.kmpComposeLibrary` while retaining
   Metro and `includeJavax()`.
2. Move every existing production and test file to an explicit KMP source set with
   `git mv`.
3. Introduce the common/Android/iOS performance seams in §7 without changing Android Firebase
   behaviour.
4. Inject one exact `AppScopeLifetime` into every production Store and remove the
   Context-backed `StoreGenerationDeps` lookup.
5. Make the minimal supporting `core:core` change that removes
   `AppCoroutineScopeImpl`'s nullable/default generation parent.
6. Update the 13 Store constructors and `AppGraph` only as required by the direct
   lifetime contract.
7. Preserve the Store, handler, processor, navigation-result, feature and Android Firebase
   behaviours stated below.
8. Migrate and strengthen host, common, native and device tests.
9. Extend the existing required Native job and add structural host/device identity checks without
   renaming required contexts.
10. Update only documentation made stale by the implemented source-set, lifecycle, performance
    and CI changes, then append exact verification evidence to this specification.

## 4. Explicit non-goals

Do not:

- convert `app:app`, `app:common`, any feature module or
  `core:ui:test-utils`;
- add an `iosApp`, Xcode project, XCFramework, UIKit/SwiftUI host, iOS graph, real
  navigation host or native packaging;
- change feature State, Action, Event, handler, mapper, reducer, rendering or initial-action
  semantics;
- change route types, result keys, navigation ownership or Navigation 3;
- change Phase-5 admission, publication, retirement, database-close, recovery, journal or
  replacement order;
- add a global lifetime holder, DI `CompositionLocal`, autonomous Store job,
  nullable/default production lifetime, or a second runtime dependency aggregate;
- hide lifetime in `StoreDispatchers`;
- move `AppDepsHolder` or Android `Context` into common code;
- replace Android Firebase with a fake/no-op implementation or claim Firebase support on iOS;
- fix the current repeated-`init` initial-action behaviour; it is preserved exactly;
- remove the currently unused `core:ui:kit` dependency as incidental cleanup;
- change Kotlin, AGP, Compose, Lifecycle, coroutine, Metro or Firebase versions;
- add a dependency family, global JVM-default policy, suppression, lint/detekt baseline, golden,
  tolerance or unrelated cleanup;
- add `iosArm64`, physical-device, signing, TestFlight or App Store work;
- rename either required GitHub Actions context;
- narrate migration history inside production comments.

## 5. Required source-set shape

All moves preserve package names and use `git mv`.

### 5.1 Common production

Move the platform-neutral contracts and processors to
`src/commonMain/kotlin/io/github/stslex/workeeper/core/ui/mvi/**`:

- root contracts: `AppFeature`, `Feature`, `FeatureAssisted`,
  `NavComponentScreen`, `NavResults` and `Store`;
- all five `handler/**` files;
- all three `holders/**` files;
- `processor/ActionProcessor`, `EffectsProcessor`,
  `MetroStoreProcessor`, `StoreProcessorImpl` and
  `SuspendProcessor`;
- `store/StoreConsumer`;
- adapted common `BaseStore`, `StoreDispatchers` and
  `StoreProcessor`;
- common `RecordAction` and the source-compatible
  `PerformanceMetricsRecorder` façade.

The production `rememberStoreProcessor` and
`rememberMetroStoreProcessor` functions remain common code. Moving only interfaces to
common while leaving the production retention/lifecycle path on Android does not satisfy this
phase.

### 5.2 Android production

Move or implement under `src/androidMain/kotlin/**`:

- `AppDepsHolder` and its `Context.appDeps` extension;
- the real Firebase `PerformanceRecorder` and performance backend;
- `FirebaseScreenRenderRecorder`, retaining Activity/frame-metric/Firebase behaviour;
- the Android screen-recorder provider that reads `LocalActivity` and delegates to the
  Firebase recorder.

`AppDepsHolder` remains public to Android consumers because the 13 feature graph
factories still resolve `Context.appDeps<FeatureGraph.Factory>()`. It is removed only from
the Store lifetime path.

### 5.3 iOS production

`src/iosMain/kotlin/**` contains only the explicit no-op actual/backend needed by the
common performance and screen-render seams. It must import no Firebase, Android or Java type.

### 5.4 Tests

| Current/new surface | Required destination |
| --- | --- |
| `NavigationResultContractTest` | `src/commonTest/kotlin/**` |
| rewritten `StoreGenerationJoinTest` | `src/commonTest/kotlin/**` |
| deterministic event-pressure test | `src/commonTest/kotlin/**` |
| Android JVM ABI and Firebase-provider tests | `src/androidHostTest/kotlin/**` |
| production processor/retention scene | `src/iosTest/kotlin/**` |
| `AppFeatureScopeTest` and probe | `src/androidDeviceTest/kotlin/**` |

At exit there are zero Kotlin files under:

- `core/ui/mvi/src/main`;
- `core/ui/mvi/src/test`;
- `core/ui/mvi/src/androidTest`.

An old directory that happens not to be compiled is not harmless; the topology oracle in §12 must
reject it.

## 6. Generation lifetime and Metro contract

### 6.1 Direct Store ownership

Add a required `AppScopeLifetime` constructor dependency to `BaseStore`. Its
`init` method then accepts only the current `LifecycleOwner` and creates
`AppCoroutineScopeImpl` with `appScopeLifetime.job`.

The lifetime parameter has:

- no nullable type;
- no default;
- no preview/test production fallback;
- no autonomous `SupervisorJob`;
- no lookup through `Context`, a global holder or a composition local.

Every preview, test and probe that constructs a Store creates and passes an explicit test
`AppScopeLifetime`.

The one supporting `core:core` change makes
`AppCoroutineScopeImpl(generationJob: Job)` required and non-null, with no default. The
only production call site at the baseline is `BaseStore`. Preserve:

```kotlin
lifecycleOwner.lifecycleScope.coroutineContext +
    CoroutineName("FeatureScope") +
    SupervisorJob(generationJob)
```

The child supervisor remains the right operand. A lifecycle-context Job must not replace it and
detach Store work from the generation.

### 6.2 Metro graph identity

Add `AppScopeLifetime` to all 13 production `StoreImpl` constructors and pass it
explicitly to `BaseStore`. Metro resolves that value from the same App/feature graph
generation that resolves the Store.

In `AppGraph`:

- delete the `StoreGenerationDeps` superinterface and import;
- retain `val appScopeLifetime: AppScopeLifetime` as an exact identity and
  graph-compilation oracle, changing `override val` to `val`;
- retain the existing `@Provides appScopeLifetime` factory parameter;
- do not add an extra `@SingleIn` annotation or provider.

Delete `StoreGenerationDeps.kt`. Remove the second
`Context.appDeps<StoreGenerationDeps>()` read from `StoreProcessor`.

`AppRuntime.buildGeneration` already creates one lifetime, passes that exact object to
the AppGraph factory and stores it on the same `RuntimeGeneration`. The implementation
must not change this Phase-5 publication or teardown mechanism. The existing
`AppGraphIdentityTest` remains the root identity oracle; feature compilation plus the
Store tests prove the constructor path.

### 6.3 Lifecycle behaviour preserved

Preserve these behaviours:

- `rememberMetroStoreProcessor` retains the Store in the current
  `LocalViewModelStoreOwner` via the production `viewModel` path;
- composition entry calls `init` and exit calls `dispose`;
- `onCleared` calls `dispose`;
- disposal is idempotent and runs dispose actions at most once;
- leaving one screen cancels that Store but does not cancel the generation;
- generation `cancelAndJoin` waits through Store `finally` blocks;
- the lifecycle observer is added/removed as today;
- re-entry still runs `initialActions` as today.

Do not broaden the claim to “BaseStore is thread-safe.” `_scope`,
`_lastAction` and handler dispatch remain governed by their existing lifecycle/call-site
discipline.

## 7. Performance contract

### 7.1 Common action façade

Keep the public source shape:

```
PerformanceMetricsRecorder.process(action: RecordAction)
```

`RecordAction` and the façade live in `commonMain` because
`app:common` already calls them from `NavigatorExt.kt` and
`AppNavigationHost.kt`. Moving the entire package to `androidMain` merely moves
the next blocker downstream and is forbidden.

The common façade delegates to an internal platform backend:

- Android selects a Firebase backend;
- iOS selects an explicit no-op backend.

The Android backend preserves the current synchronous serialization guarantee around
`process`. The current `@Synchronized` belongs to performance state, not to
`BaseStore` event emission. Keep equivalent Android serialization rather than silently
dropping it while moving the façade.

The Android router preserves every current mapping:

| Action | Android behaviour |
| --- | --- |
| `ActivityCreated` | start `ActivityCreate_MainActivity` with `coldStart` |
| `AppCreated` | start the single-shot `AppCreate_App` trace |
| `Navigation.NavTo / ReplaceTo` | start `TTID_<screen>` with the current `navType` |
| `OnScreenPlaced` | stop matching TTID plus App/Activity create traces |
| `ClearTraces` | clear all three recorder states |

Keep the current abort-on-replacement, single-shot App creation and trace-name semantics.

### 7.2 Screen render seam

Create a narrow internal common `ScreenRenderRecorder` seam used by production
`rememberStoreProcessor`. The common processor starts the recorder on entry and stops it
on disposal.

The default providers are:

- Android: preserve `LocalActivity.current ?: LocalContext.current as? Activity`, retain the
  existing null-Activity diagnostic and delegate to `FirebaseScreenRenderRecorder`;
- iOS: explicit no-op.

Keep `FirebaseScreenRenderRecorder` Android-only and preserve its
`FrameMetricsRecorder`, Firebase `Trace`, frame-counter, map and clear
semantics. Do not reimplement Firebase internals in common code.

An internal, composition-scoped test override may supply a fake recorder. It must:

- be inaccessible as public API;
- avoid a process-global mutable test switch;
- be read by the same production `rememberStoreProcessor` path;
- let the native scene prove one start and one stop for the retained Store.

### 7.3 Android is not allowed to become no-op

Android-host tests must prove both:

1. the platform performance provider is the Firebase backend, not the iOS no-op;
2. the Android action router and screen adapter delegate to deterministic fake trace/frame sinks
   while the production provider remains wired to the real Firebase implementations.

The test must enter the public common façade for action processing. An Android-only test of a
detached helper is insufficient. The injectable implementation detail must stay internal and must
not replace the real production provider.

## 8. Common concurrency adaptation

### 8.1 Consume guard

`BaseStore` currently uses `java.util.concurrent.atomic.AtomicBoolean` only
through `get` and `set`; it performs no compare-and-set or read/modify/write
operation.

Replace it with:

```kotlin
@Volatile
private var allowConsumeAction: Boolean
```

using stable `kotlin.concurrent.Volatile`. Preserve the current transition order:
disabled before init, enabled before initial actions, dispose actions consumed before disabling,
and disabled before the Store scope is discarded.

Do not add experimental common atomics or another dependency for a Boolean that needs only
visibility. Kotlin’s contract is documented at
<https://kotlinlang.org/api/core/kotlin-stdlib/kotlin.concurrent/-volatile/>.

### 8.2 Event emission

Remove the private JVM `@Synchronized` annotation from
`sendEventWithAwait`. Keep the existing algorithm:

1. call `tryEmit`;
2. if it returns false under buffer pressure, launch `emit` in the Store scope.

`MutableSharedFlow` methods are thread-safe on all supported coroutine platforms:
<https://kotlinlang.org/api/kotlinx.coroutines/kotlinx-coroutines-core/kotlinx.coroutines.flow/-mutable-shared-flow/>.

The removed monitor does not await or order the launched fallback emissions. Do not replace it
with a common lock and do not invent an ordering claim that the current code does not provide.

A deterministic common test must install an active, deliberately blocked collector, fill beyond
the 32-event extra capacity, release the collector and prove every submitted event is observed
exactly once. It must not be a probabilistic stress loop. Removing the fallback
`scope.launch { _event.emit(event) }` is the mandatory RED control.

## 9. Build and dependency contract

### 9.1 Plugins and targets

`core:ui:mvi/build.gradle.kts` must:

- replace `convention.composeLibrary` with `convention.kmpComposeLibrary`;
- keep Metro and `metro.interop.includeJavax()`;
- publish Android plus `iosSimulatorArm64` through the existing convention;
- add no `iosArm64` target and change no shared convention.

### 9.2 Variant resolution is a precondition

Before moving source, force resolution of the exact pinned
`iosSimulatorArm64CompileKlibraries` and
`iosSimulatorArm64TestCompileKlibraries` configurations for the proposed dependency
graph. In particular, verify the exact Lifecycle 2.11.0 artifacts used by
`ViewModel`, `LifecycleOwner`, `rememberLifecycleOwner` and
`viewModel`.

Do not guess an artifact coordinate, upgrade a version or substitute a new lifecycle framework.
If any required dependency lacks the simulator variant, stop.

### 9.3 Dependency visibility

Declare direct common dependencies with visibility driven by exposed signatures:

- `api(project(":core:core"))` for logger/lifetime/scope types;
- `api(project(":core:ui:navigation"))` for
  `Screen`/`NavGraphScope`/result types;
- common coroutine core as `api` because public Store/handler APIs expose
  `Flow`, `StateFlow`, `SharedFlow`,
  `CoroutineDispatcher`, `CoroutineScope` and `Job`;
- Compose runtime as `api` where public composable/state signatures require it;
- the pinned Lifecycle runtime/ViewModel/Compose artifacts as `api` where
  `BaseStore` or public processor APIs expose them.

Keep `project(":core:ui:kit")` as an implementation edge for scope stability even though
the measured production import count is zero. Removing it requires a separately reviewed cleanup
with its own graph proof.

Android-only dependencies include Activity Compose, Firebase performance/BOM and any
`javax.inject` visibility required by Metro qualifier metadata. Test dependencies are
source-set-specific:

- `commonTest`: `kotlin("test")` and common coroutine test;
- `androidHostTest`: only the JUnit 5/runtime additions actually required beyond the
  KMP convention;
- `androidDeviceTest`: the existing Android test bundle, Compose UI JUnit4,
  `core:ui:test-utils` and the version source needed by
  `ui-test-manifest`;
- `iosTest`: `kotlin("test")` and the pinned CMP UI test runner.

Firebase dependencies must not enter `commonMain` or `iosMain`.

### 9.4 JVM ABI

The classic Compose convention supplied `-Xjvm-default=all`. Kotlin 2.4’s KMP default is
not assumed equivalent. Configure this module’s `KotlinJvmCompile` tasks with
`JvmDefaultMode.NO_COMPATIBILITY`, locally, as Phase 7.2 does.

Before conversion, compile the baseline module and record the actual binary shape of every public
MVI interface that has default members or default arguments. In particular, characterize
`HandlerStore`, `StoreConsumer`, `StoreProcessor` and their generated
default-argument helpers. Do not assume every `DefaultImpls` class is absent:
default-argument helpers and default interface bodies are different mechanisms.

Add an Android-host reflection/ABI oracle pinned to the observed baseline. It may admit only these
approved public deltas:

- `BaseStore` requires `AppScopeLifetime` and
  `init` no longer accepts a Job;
- `AppCoroutineScopeImpl` requires a non-null generation Job with no default;
- `StoreGenerationDeps` is deleted;
- Android-only `AppDepsHolder` is no longer visible to common/native metadata;
- new internal platform seams do not enter public ABI.

All other public names, generic bounds, method descriptors, default helpers and Android-visible
performance entry points remain compatible. Changing only the module JVM-default mode to
`ENABLE` must make the ABI oracle RED after a clean compile.

## 10. Test contract

### 10.1 Common contract tests

Move all seven `NavigationResultContractTest` cases to `commonTest`, replace
JUnit assertions/annotations with `kotlin.test` and preserve their semantics.

Rewrite `StoreGenerationJoinTest` without MockK, using an explicit fake counter and an
explicit `AppScopeLifetime`. It must prove:

- a `launchDefault` child finishes its `finally` before generation
  `cancelAndJoin` returns;
- ordinary `dispose` cancels the Store while the generation remains active;
- `onCleared` disposes exactly once and a later composition disposal is harmless;
- actions after disposal are rejected;
- the right-hand `SupervisorJob(generationJob)` ancestry is live.

Delete the current “Store with no generation job still works” case. Replace it with an explicit
lifetime case; do not preserve the forbidden fallback for previews/tests.

Add the deterministic buffer-pressure test from §8.2.

These common tests run on both Android host and `iosSimulatorArm64`. A test that compiles
only on Android does not prove the common Store.

### 10.2 Native production processor scene

Add an `iosTest` Compose-scene test that enters the production
`rememberMetroStoreProcessor` → `rememberStoreProcessor` path under explicit
common `LifecycleOwner` and `ViewModelStoreOwner` test owners.

It must prove in one deterministic lifecycle:

1. the factory creates one real `BaseStore` with an explicit lifetime;
2. recomposition retains that exact Store through the production `viewModel` path;
3. the injected fake screen recorder is started once and stopped once through the production
   processor path;
4. clearing the owner’s `ViewModelStore` reaches `onCleared` and disposes the
   Store exactly once;
5. generation `cancelAndJoin` waits for the Store child’s cleanup.

The test may use a headless Skiko `ComposeScene` exactly as Phase 7.1 does. It does not
claim UIKit/window rendering.

If the pinned Lifecycle/CMP stack cannot execute this real ViewModel path on
`iosSimulatorArm64` without changing production API or inventing speculative host
scaffolding, stop. An interface-only fake or a test that bypasses
`rememberStoreProcessor` cannot substitute for it.

### 10.3 Android device tests

Move the two existing files to `androidDeviceTest` and preserve exactly two
`@Smoke` cases:

- App-root processor resolves and retains its Store in the Activity
  `ViewModelStore`; `BaseStore.init` receives the child returned by
  `rememberLifecycleOwner`, which follows the parent Activity lifecycle;
- a Store job is a descendant of the exact `AppScopeLifetime` passed to that Store.

Remove `ProbeGenerationDeps`, the `ContextWrapper` and the fake
`AppDepsHolder` host. The probe Store receives its explicit lifetime directly. The first
test still checks the production ViewModel path; the second checks direct injected identity rather
than the deleted Context seam.

Keep the `@Smoke` annotation and `core:ui:test-utils` classpath edge. The whole
Smoke and Regression membership/totals in §2 remain unchanged.

### 10.4 Android performance and ABI host tests

Add Android-host cases that:

- pin the observed pre-conversion JVM ABI from §9.4;
- prove the common performance façade selects and delegates to the Android Firebase backend;
- prove the Android screen adapter delegates start/stop and carries the current Activity;
- fail if either Android provider is replaced by the iOS no-op.

Tests must not contact a network or depend on wall-clock/frame timing.

## 11. CI contract

### 11.1 Stable required contexts

Keep the job names `Build and Unit Tests` and `KMP iOS kit smoke` exactly.

### 11.2 Forced Android-host identity

Inside `Build and Unit Tests`, run the focused task:

```shell
./gradlew :core:ui:mvi:testAndroidHostTest \
  --rerun-tasks --no-build-cache --no-configuration-cache \
  --full-stacktrace --console=plain
```

Then structurally parse the fresh MVI XML and require exact identities for:

- generation join;
- post-dispose rejection;
- buffer-pressure exact delivery;
- JVM ABI;
- Android Firebase provider/delegation.

Additional passing MVI tests are allowed. Missing directories/XML, zero tests, malformed counts,
skips, failures, errors, duplicate expected identities or stale/renamed expected methods are RED.
Run the assertion after a started-but-red Gradle step, using the same status discipline as the
Native gate.

The root `testDebugUnitTest` invocation remains; the forced task is the liveness oracle,
not a replacement.

### 11.3 Native required job

Extend the existing forced command, with `--continue`, to:

```text
:core:ui:kit:iosSimulatorArm64Test
:core:ui:navigation:iosSimulatorArm64Test
:core:ui:mvi:iosSimulatorArm64Test
```

Extend `.github/scripts/assert_kmp_ios_smoke.py` so:

- the existing kit and navigation tuples remain required exactly once;
- MVI requires exact tuples for generation join, navigation result delivery/clear, buffer-pressure
  delivery and the production processor/retention scene;
- additional passing cases remain allowed;
- every module and every suite is checked even after an earlier failure;
- per-suite non-negative counts, child status tags and exact identities retain the Phase-7.2
  guarantees.

Upload `core/ui/mvi/build/test-results/iosSimulatorArm64Test/` beside the existing two
result directories. Rename neither the job nor its artifact without a separate reason.

### 11.4 Reuse one XML validator

Do not create three subtly different JUnit parsers. Extract or reuse the existing Phase-7.2
per-suite validator so Native, MVI Android-host and MVI device identity checks share the same
structural rules. Re-run the existing 29-case synthetic fixture matrix plus new multi-identity,
host-prefix and Android-device fixtures.

### 11.5 Source topology and device identity

Add a checked-in topology assertion, run in `Build and Unit Tests`, that rejects Kotlin
under the three legacy directories in §5.4 and pins the intended common/Android/iOS/device split.

After the Smoke emulator task in `ui_tests.yml`, structurally parse the fresh MVI
connected-test XML and require the two exact `AppFeatureScopeTest` identities. First
characterize and pin the actual AGP-KMP result directory; never guess it.

This closes the legacy-source false green: assembling a KMP device-test APK can succeed while a
test left in `src/androidTest` executes zero times.

## 12. Compatibility invariants

The implementation must preserve:

- all feature State/Action/Event types and handler routing;
- current `initialActions` and `disposeActions` behaviour;
- StateFlow/SharedFlow public types, replay/capacity configuration and event analytics;
- `NavResults` result typing, clearing and delivery semantics;
- current `AppFeature`/`Feature`/`FeatureAssisted` processor entry
  shapes;
- ViewModel retention and lifecycle-observer behaviour;
- Android Store analytics/crash screen naming;
- every Firebase trace name, attribute and frame-counter behaviour;
- all 15 consumer module builds;
- Phase-5 generation ownership and teardown ordering;
- Android release/build/device/golden behaviour.

The intentional compatibility deltas are only those listed in §9.4.

## 13. Required verification

Use forced, non-replayable flags for focused proof. Record exact task outcomes and XML identities
in this document after implementation.

### 13.1 Pre-change

1. Pin the exact implementation base and prove the worktree clean.
2. Resolve Native compile/test variants from §9.2.
3. Compile the classic MVI Android module and capture the JVM ABI manifest.
4. Record current MVI host/device test identities.
5. Record golden filenames/hashes and current device-suite membership.

If local infrastructure cannot fetch the pinned Gradle distribution or dependencies, that is not
evidence. Use a provisioned environment or CI; do not mark a gate green from source inspection.

### 13.2 Focused positive gates

- `:core:ui:mvi:compileAndroidMain` or the exact AGP-KMP compile task;
- `:core:ui:mvi:testAndroidHostTest`;
- Android-host XML identity assertion;
- `:core:ui:mvi:iosSimulatorArm64Test`;
- three-module Native command with `--continue`;
- Native XML identity assertion;
- `:core:ui:mvi:assembleAndroidDeviceTest`;
- a fresh targeted MVI connected device run;
- device XML identity assertion;
- `:app:app:assembleDebug` and explicit compilation of all 15 consumers;
- `:core:ui:mvi:lint` / `lintDebug` alias;
- `:core:ui:mvi:detekt` and root `detekt`;
- XML-validator synthetic fixtures and Python compile;
- workflow YAML parse;
- `git diff --check`.

### 13.3 Repository gates

Run the repository commands required by `AGENTS.md` and the touched surfaces:

- `assembleDebug`;
- `assembleDebugAndroidTest`;
- `verifyPaparazziDebug`;
- `:lint-rules:test`;
- `detekt`;
- personal-data gate;
- `lintDebug`;
- `testDebugUnitTest`.

Run Smoke and Regression device suites fresh when device infrastructure is available. Both MVI
device cases, 44/41/3 Smoke membership and 81/81 Regression execution remain exact.

### 13.4 Final graph/diff proof

At implementation PR head, prove:

- branch is based on the reviewed spec merge;
- only planned production, consumer-constructor, build, test, CI and documentation files changed;
- all 15 direct consumers still point to `core:ui:mvi`;
- all 13 production Store constructors receive direct lifetime;
- `StoreGenerationDeps` and the processor’s Context lookup are absent;
- no Android/Firebase/Java import exists in common or iOS production;
- no Firebase dependency resolves into common/iOS;
- no Kotlin exists in legacy source directories;
- no golden byte/hash or golden gate changed;
- no version, ruleset or required-context name changed.

## 14. Mandatory known-negative controls

For each mutation: establish fresh GREEN first, mutate one thing, require the named RED, restore
exact bytes in `finally`, and re-run fresh GREEN. Do not retain mutation artifacts.

| Mutation | Required RED oracle |
| --- | --- |
| replace the injected lifetime with an autonomous/null parent | common/native generation join and device descendant identity |
| put `SupervisorJob(generationJob)` left of lifecycle context | generation ancestry/join |
| remove `onCleared() -> dispose()` | host/native disposal |
| remove the consume guard | post-dispose action rejection |
| remove fallback suspended `emit` | deterministic buffer-pressure delivery |
| introduce one Android/Firebase import in common | Native compilation/topology |
| set JVM-default mode to `ENABLE` | clean Android ABI oracle |
| rename one required MVI Native method without rerunning Gradle | Native XML identity script |
| leave/move one device case in legacy `src/androidTest` | topology/device identity |
| replace Android performance provider with no-op | Android Firebase-provider oracle |
| bypass the production processor’s recorder seam | Native production scene |
| add a detekt violation in new common/native test source | module/root detekt |

Also re-run the XML parser’s compensating-suite fixture: one suite over-declares tests while
another under-declares by the same amount. It must remain RED per suite rather than passing on a
module total.

No flaky stress, sleep-based race or test-count substring is acceptable.

## 15. STOP conditions

Stop implementation and report instead of widening scope if:

- any required dependency lacks an `iosSimulatorArm64` compile/test variant;
- the real native Lifecycle/ViewModel/`rememberStoreProcessor` path cannot execute;
- a feature State/Action/Event/handler/rendering semantic must change;
- any consumer module must convert to KMP;
- Phase-5 runtime publication, admission, retirement, recovery, database, replacement or journal
  semantics must change;
- Store construction needs a global, nullable/default or autonomous lifetime fallback;
- Android `Context` or `AppDepsHolder` must enter common code;
- Android Firebase must become no-op/fake or real-provider delegation cannot be proven;
- the pre-conversion JVM ABI cannot be characterized or changes outside the approved list;
- a required test is `NO-SOURCE`, zero, stale, missing, duplicated, skipped or only
  interface-faked;
- MVI device identities or whole Smoke/Regression membership changes;
- any golden file/hash/gate changes;
- a new dependency family/version upgrade, global convention edit, suppression or baseline is
  needed;
- `iosArm64`, UIKit/Xcode-host or signing work becomes necessary;
- a required check must be renamed;
- Kotlin remains in a legacy source directory;
- the implementation needs comments that are migration narration rather than durable invariants.

## 16. Documentation and comment budget

This specification owns the migration rationale.

During implementation:

- update `architecture.md` only where MVI source paths/platform ownership become stale;
- update `performance.md` with the common façade, Android Firebase backend and iOS no-op
  boundary;
- update `testing.md` and `ci-cd.md` with exact new source sets, commands and
  liveness/identity gates;
- append a factual implementation/verification record here and change the status only after every
  gate passes.

Production comments are limited to durable guards. Preserve comments whose invariant remains true.
Correct only false process-lifetime wording (the runtime lifetime is per generation), false
source paths and guards introduced by this work. Do not add “moved in Phase 7.3” narration or
perform a broad comment rewrite.

## 17. Implementation commit plan

The implementation PR is separate from this specification PR and targets `dev`.

Recommended commits:

1. `refactor(kmp): share the MVI Store runtime` — KMP/source-set conversion, direct
   lifetime, common concurrency adaptation, performance backends, 13 Store constructor updates
   and the focused tests needed to keep this commit Android-releasable.
2. `ci(kmp): gate MVI host native and device identities` — shared XML validator,
   topology check and workflow wiring, with every existing kit/navigation oracle preserved.
3. `docs(kmp): record Phase 7.3 verification` — only truthful source-path/platform docs
   and the exact positive/negative evidence.

Every commit must compile and preserve Android release viability. Signing follows the repository
policy for that future PR; this specification PR’s connector exception is not implementation
authorization.

## 18. Exit criteria

Phase 7.3 is complete only when:

- the reviewed spec is merged and a separate explicit implementation GO was received;
- production MVI and the real processor path compile for Android and
  `iosSimulatorArm64`;
- every Store is bound directly to its exact generation lifetime;
- all 15 Android consumers compile with no semantic change;
- Android uses real Firebase and iOS uses an explicit no-op;
- common/native concurrency, lifecycle, retention, result and event-delivery tests are live;
- the two Android MVI device tests execute from `androidDeviceTest`;
- host, Native and device XML identities are structurally asserted from fresh outputs;
- all mandatory negative controls went GREEN → RED → GREEN;
- required GitHub contexts are green under their unchanged names;
- repository/device/golden invariants hold;
- this document contains the exact final evidence and no unproven parity claim.

## 19. Boundary after this specification

Merging this specification authorizes no code change by itself.

The next instruction must be an explicit GO for implementation against the merge commit of this
specification PR. The implementation agent must first re-pin `dev`, repeat the drift and
variant/ABI preflight, then either execute this contract or stop on §15. It must open a PR and
must not merge it.

## 20. Implementation and verification record — 2026-08-27

The implementation GO was executed on `feature/kmp-phase-7-3-mvi` from
`6e746bd0abc7eb81a8e6c6847ed26be2717bbf83`. A fresh fetch confirmed `origin/dev` still named
that exact commit before work resumed. The local implementation and every locally available gate
below are green. GitHub required-context evidence is pending, so this record makes no completion
claim.

### 20.1 Positive evidence

All Gradle verification commands used `--rerun-tasks --no-build-cache`; focused KMP commands also
used `--no-configuration-cache`. The quoted summaries are the Gradle evidence lines.

| Surface | Fresh result |
| --- | --- |
| MVI Android compile | `:core:ui:mvi:compileAndroidMain` — `43 actionable tasks: 43 executed` |
| MVI Android host | `:core:ui:mvi:testAndroidHostTest` — `83 actionable tasks: 83 executed`; 24 executed, 7 exact identities |
| Three-module Native | kit + navigation + MVI `iosSimulatorArm64Test --continue` — `86 actionable tasks: 86 executed`; XML totals 1 / 1 / 14 and exact identities 1 / 1 / 5 |
| MVI device assembly | `:core:ui:mvi:assembleAndroidDeviceTest` — `136 actionable tasks: 136 executed` |
| Focused MVI device | Smoke-filtered `:core:ui:mvi:connectedAndroidDeviceTest` — `137 actionable tasks: 137 executed`; 2 executed and 2 exact identities under `connected/androidMain` |
| Android consumers | all 15 direct consumer compile tasks plus `:app:app:assembleDebug` — `325 actionable tasks: 325 executed` |
| MVI analysis | module Detekt + lint — `103 actionable tasks: 103 executed` |
| Repository build / analysis / host tests | `assembleDebug detekt lintDebug testDebugUnitTest` — `2072 actionable tasks: 2072 executed` |
| Instrumented assembly | `assembleDebugAndroidTest` — `1936 actionable tasks: 1936 executed` |
| Screenshot goldens | `verifyPaparazziDebug` — `617 actionable tasks: 617 executed`; 456 committed golden PNGs unchanged |
| Custom Detekt rules | `:lint-rules:test` — `9 actionable tasks: 9 executed` |
| Personal-data gate | exit 0; one detector pattern, one literal and the three documented named exceptions |
| Smoke device suite | `2009 actionable tasks: 2009 executed`; 14 current-run XML files, 44 discovered / 41 executed / 3 named skips / 0 failures / 0 errors |
| Regression device suite | `2009 actionable tasks: 2009 executed`; 81 discovered / 81 executed / 0 skipped / 0 failures / 0 errors |
| Static CI artifacts | Python compile green; both edited workflow YAML files parse with Ruby Psych; `git diff --check` green |

The three Smoke skips remain exactly
`AllExercisesScreenTest.pendingFeatureRewrite`,
`AllTrainingsScreenTest.pendingFeatureRewrite`, and
`ArchiveScreenTest.pendingFeatureRewrite`. Regression membership remains 49 `app:app`, 30
`core:data:database`, one `core:data:exercise`, and one `feature:all-exercises` case.

### 20.2 Known-negative controls

Every repository-file mutation used `documentation/mockups/mutation_harness.py`, which restored
the exact pre-mutation bytes in `finally`; the legacy-directory negative used an isolated
temporary source-tree copy because no legacy repository file exists to mutate. Each accepted
control established fresh green first and fresh green again after restoration.

| Mutation | Required red observed |
| --- | --- |
| autonomous Store parent | host generation ancestry/join tests and device descendant identity |
| `SupervisorJob(generationJob)` moved left | both host generation ancestry/join tests |
| remove `onCleared() -> dispose()` | host and Native disposal identities plus the production Native processor scene |
| remove consume guard | `actionsAfterDisposalAreRejected` |
| remove suspended event fallback | `everyEventSubmittedUnderBufferPressureIsObservedExactlyOnce` |
| add Android import to common | source-topology gate |
| set `JvmDefaultMode.ENABLE` | clean `MviJvmAbiTest.noMviInterfaceCarriesADefaultImplsHolder` |
| rename required Native method, then parse without another Gradle run | stale XML identity rejection |
| place device test in legacy `src/androidTest` | source-topology rejection on isolated copied tree |
| replace Android performance provider with no-op | `theAndroidPlatformBackendIsTheFirebaseOneAndNotANoOp` |
| bypass processor recorder seam | Native production processor scene |
| add Detekt violation to new common test | `:core:ui:mvi:detekt` |

The shared XML validator fixture matrix matched all 35 expected verdicts: the historical 29 cases
plus multi-identity, Android-host prefix, Android-device identity and compensating-suite cases.
The compensating-suite fixture remained red per suite.

Two early harness launches blocked before their child Gradle process ran and produced no named
test; they are rejected and are not counted above. An initial clean-ABI mutation command was also
rejected because its summary contained one `UP-TO-DATE` task; the accepted replacement used the
clean Android compile/host-test outputs and produced the named ABI red. A guessed non-existent
common dependency configuration and a Gradle `resolvableConfigurations` reporting failure were
diagnostic probes only; the accepted dependency proof uses
`commonMainResolvableDependenciesMetadata`, `iosMainResolvableDependenciesMetadata`, and
`iosSimulatorArm64CompileKlibraries`, all with no Firebase resolution.

### 20.3 Final structural proof and pending evidence

- All 13 concrete production Stores receive `AppScopeLifetime` directly; `StoreGenerationDeps`
  and processor `Context` lookup are absent.
- The exact 15 classic Android consumers still depend directly on `core:ui:mvi`; no consumer was
  converted to KMP.
- Source topology is commonMain 27, androidMain 6, iosMain 2, commonTest 3, androidHostTest 2,
  androidDeviceTest 2 and iosTest 1; legacy directories contain no Kotlin.
- Common/iOS production contains no Android, Java, `javax` or Firebase import. Firebase does not
  resolve into common or iOS; Android host tests prove the platform provider is the real serialized
  Firebase backend and the screen adapter retains its Firebase sink.
- The clean Android ABI oracle is green, the 456 golden paths and bytes are unchanged, and no
  dependency version, ruleset or required-context name changed.
- GitHub CI and required contexts are still pending. Phase 7.3 remains incomplete until that
  external evidence is green under the unchanged names.

### 20.4 Review corrections — 2026-08-28

All six initial review comments reproduced as correct before their fixes. At this point the JVM
oracle still pinned only selected public types, the lifecycle test compared owners only while both
were resumed, the actual-provider test proved its Firebase sink but not its Activity, and topology
enumerated only the source sets already present in its expected map. Those four incomplete claims
are superseded by §20.5. `StoreLifecycle` is private and no longer leaks through an inline
composable. The public performance facade and real Firebase provider remain covered, and the
AppGraph KDoc and §10.3 describe one runtime generation and the remembered child lifecycle owner.

The accepted negative controls made the new named oracle red for an added Compose default helper,
a no-op public performance facade, a no-op actual screen provider, and an unrelated Store
lifecycle owner. Moving `BaseStore.kt` from `commonMain` to `androidMain` in an isolated copy now
fails with both the missing and extra exact paths. An earlier screen-provider mutation that did not
compile was `INVALID` and is not evidence.

Fresh post-review results are: MVI Android host `83 actionable tasks: 83 executed` with 26 tests and
9 exact identities; three-module Native `151 actionable tasks: 151 executed`; focused MVI device
`137 actionable tasks: 137 executed` with 2 exact identities; module Detekt plus lint
`103 actionable tasks: 103 executed`; repository build, analysis and host tests
`2072 actionable tasks: 2072 executed`; instrumented assembly
`1936 actionable tasks: 1936 executed`; screenshot goldens
`617 actionable tasks: 617 executed`; and custom Detekt rules
`9 actionable tasks: 9 executed`. GitHub evidence remains pending until the review-fix commit is
pushed and its required contexts complete.

### 20.5 Follow-up corrections — 2026-08-28

The four follow-up findings reproduced as correct and were fixed without changing production API,
test identities, dependencies, versions or migration scope:

- The JVM ABI oracle now derives a deterministic manifest from every public non-synthetic module
  class file, including named nested classes and file facades, public constructors, fields and
  methods, JVM descriptors, generic signatures and bounds, default/Compose helpers and
  `DefaultImpls`. The checked snapshot is traced to a clean compile of the exact pre-conversion
  base plus the explicit Phase 7.3 allowlist. Exclusions are limited to synthetic/local classes,
  AGP `BuildConfig` and generated Compose resources.
- `appFeatureProcessorResolvesAtActivityScope` still has its original identity and now observes the
  owner retained by the real Store while the host Activity moves `RESUMED -> CREATED -> RESUMED`,
  using scenario lifecycle control and Compose idling. The module still contains exactly the same
  two `@Smoke` device identities.
- `theComposableAndroidScreenProviderKeepsTheFirebaseAdapter` now enters
  `rememberScreenRenderRecorder()`, captures the composition Activity and proves the actual
  `FirebaseScreenRenderAdapter` retains that same instance while deterministic sinks remain in use.
- The topology gate first enumerates every immediate Kotlin-bearing source-set directory and
  rejects unknown names with their paths, then applies the existing exact per-source-set manifests
  and legacy/import/symbol/deleted-seam checks.

Each accepted mutation established fresh green, reached the named oracle, restored exact bytes and
then established fresh green again:

| Mutation | Fresh green -> required red -> restoration green |
| --- | --- |
| add a public method to the existing `NavResults.kt` and clean-compile | ABI host test `84/84` -> `MviJvmAbiTest.publicMviAbiMatchesTheMeasuredManifest` red at `84/84` -> `84/84`; source SHA-256 restored exactly |
| retain an independent lifecycle owner frozen at initial `RESUMED` | focused MVI device `137/137`, 2 identities -> `AppFeatureScopeTest.appFeatureProcessorResolvesAtActivityScope` red at `137/137` -> `137/137`, same 2 identities |
| return `FirebaseScreenRenderAdapter(activity = null)` from the Android actual provider | provider host test `84/84` -> `AndroidPerformanceProviderTest.theComposableAndroidScreenProviderKeepsTheFirebaseAdapter` red at `84/84` -> `84/84` |
| add `src/desktopMain/**/DesktopLeak.kt` in an isolated exact source-tree copy | topology green -> red naming `desktopMain` and its exact unexpected path -> fresh topology green with the approved `27/6/2/3/2/2/1` file counts |

The retained public-facade bypass and no-op actual-provider controls also remain named red. Final
local results are: MVI Android host `84 actionable tasks: 84 executed`, 26 tests and 9 exact
identities; Native kit/navigation/MVI `86 actionable tasks: 86 executed`, XML totals 1 / 1 / 14
and exact identities 1 / 1 / 5; focused MVI device `137 actionable tasks: 137 executed`, exactly 2
tests and identities; all 15 consumers plus app assembly `325 actionable tasks: 325 executed`; MVI
Detekt/lint `103 actionable tasks: 103 executed`; repository build/analysis/host tests
`2073 actionable tasks: 2073 executed`; instrumented assembly `1936 actionable tasks: 1936
executed`; Paparazzi `617 actionable tasks: 617 executed` with all 456 goldens unchanged; custom
rules `9 actionable tasks: 9 executed`; Smoke `2009 actionable tasks: 2009 executed` with exact
44 / 41 / 3 membership; Regression `2009 actionable tasks: 2009 executed` with exact 81 / 81
membership. The 35-case XML fixture matrix, topology, three dependency reports, personal-data
gate, Python compilation, all workflow YAML parsing and `git diff --check` are green.

### 20.6 GitHub delivery closeout — 2026-08-28

PR #262 merged into `dev` at `2026-08-28T11:31:28Z` from final head
`8469ae5f2f9da761f771ffd00132ed3d910f848c`; GitHub created merge commit
`8eacd149085db44aa297961bb19e9e563daea274`.

Both workflow runs at the final PR head completed successfully:

- `Android CI/CD - Unified Build and Tests` run `33165327733`: `KMP iOS kit smoke` and
  `Build and Unit Tests` succeeded. The latter published the successful `Unit Test Results`
  and `Detailed Unit Test Report` checks;
- `v3 Mockup Appearance Gate` run `33165327732`: `Mockup Appearance Gate` succeeded.

All 10 review threads are resolved (0 unresolved). The active repository-wide `all` ruleset
(id `8116593`, `~ALL`) remains unchanged and continues to require signed commits plus
`KMP iOS kit smoke` and `Build and Unit Tests`. No check name or repository setting changed.

With the local evidence in §§20.1–20.5, the final-head GitHub evidence above, and the maintainer
merge, every §18 exit criterion is closed. Phase 7.3 is complete.
