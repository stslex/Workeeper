# KMP Phase 7.19B — share the application composition root

Proposed for review. The discovery base is `dev` at
`1d87b60fb190ed74c96b44a56252727860dfcd28`, the merge of
[the 7.19A implementation](https://github.com/stslex/Workeeper/pull/351).
Phase 7.19A removed the direct `:app:common` dependency on `:feature:recovery`
and moved the Android launch into `MainActivity`. Its six targeted device
identities and 90 Regression cases were executed in that phase.
[Accepted boundary and evidence](kmp-phase-7-19a-recovery-launch-boundary.md)

## Outcome

Convert the existing `:app:common` Android library into a KMP Compose library
for Android, `iosSimulatorArm64` and `iosArm64`. Android continues to enter via
`App(onOpenRecovery)`. A Native test host enters the shared root with explicit
generation ownership, deferred dependency resolution and a recovery callback.
The exit requires the real application root, Nav3 display and feature entries to
run on the simulator, plus a linked device-target test binary.

The shipped Android host owns startup, database recovery, runtime generation
replacement, Firebase, manifest and Activity lifecycle. An iOS application,
production Apple runtime/DI, physical-device acceptance and real iOS backup
belong to later work. The 7.18 unavailable Settings binding and the 7.19A
Android recovery behavior are prerequisites for this phase.

## Starting inventory

| Area | At the discovery base |
| --- | --- |
| Application root | 13 production Kotlin files in `app/common/src/main`. `App.kt` looks up the generation holder on the Application and resolves `AppRootDeps` after admission. |
| Navigation | 12 registered screen factories, plus the root-mounted `AppDialogHost`. `AppRootDeps` exposes one concrete `NavigatorEventBus` for navigation, command receipt and results. |
| Platform dependencies | `DisplayCornerShape.kt` reads Android window insets. `NavigatorEventBus` stores result channels in `ConcurrentHashMap`. Bottom-bar labels use Android resource IDs. |
| Resources | Three labels in each EN/RU catalog, in Home, Trainings, Exercises order. |
| Host tests | Four app/common suites, 37 cases. `NavTransitionsTest` 17, `NavLogLabelsTest` 2, `NavigatorEventBusLifecycleTest` 5, `NavigatorEventBusTest` 13. |
| CI | 18 modules in the Native simulator/link job. The UI topology script and the mutation harness reference old root paths. |

Read [AGENTS.md](../../AGENTS.md), the [Phase 2 probes](kmp-phase-2-probes.md),
[conventions](kmp-phase-6-data-layer.md), [Phase 4](kmp-phase-4-app-common.md),
[Phase 5 admission contract](kmp-phase-5-startup-processor.md), [architecture](../architecture.md),
[performance](../performance.md), [testing](../testing.md) and [CI](../ci-cd.md).
The results in this specification are source observations or historical evidence.
The implementation supplies fresh builds, tests and dependency-resolution reports.

## Entry gate P0 — resolve and run the real Nav3 APIs

Before the bulk move, record the Android runtime classpath, baseline host identities
and 7.19A regression identities. Use a disposable local probe with the repository
convention, or an isolated preliminary conversion. Keep the probe out of the PR.

| Item | Probe requirement |
| --- | --- |
| Existing pins | Kotlin 2.4.10, AGP 9.3.0, Compose Multiplatform 1.11.1, Metro 1.3.2, AndroidX Navigation 3 1.1.6 and Lifecycle 2.11.0. Compare resolved Android components as well as catalog declarations. |
| Libraries | Try the existing runtime/UI/decorator coordinates on both Apple targets. `core:ui:navigation` already shares the Nav3 **runtime**. The UI and ViewModel decorator have separate variants. Resolve the real `NavDisplay`, both entry decorators, saved-state configuration, `NavigationEvent.EDGE_*`, predictive pop lambda, `unveilIn`, and lifecycle ViewModel/owner imports used by the root. |
| Bounded fallback | If the existing UI or decorator lacks a suitable variant, try a separate catalog alias from the JetBrains multiplatform group. The CMP 1.11.1 release lists `org.jetbrains.androidx.navigation3:navigation3-ui:1.1.1`. For its Lifecycle decorator try `org.jetbrains.androidx.lifecycle:lifecycle-viewmodel-navigation3` at the existing stable Lifecycle pin if published and compatible. Keep the existing Google runtime. Put Android-specific constraints/edges in `androidMain` only if required to preserve the baseline selected Android artifacts. |
| Evidence | Capture selected module/version/variant for Android, simulator and device. Compile Android, execute a simulator scene with a non-empty stack and both decorators, and link the Apple device test. Check the phone app's Android runtime for duplicate implementations, downgrades and resolution changes. Archive the dependency report, commands and executed output. |

Documentation supplies candidate coordinates. The probe determines what works for
this checkout. If those bounded choices fail, report the failing configuration,
candidate and dependency insight before changing a version or a public API. No
global library/toolchain upgrade, weakened decorator, alternate router or silent
loss of predictive back is authorized.
[Official CMP Navigation 3 setup](https://kotlinlang.org/docs/multiplatform/compose-navigation-3.html) ·
[CMP 1.11.1 release alignment](https://github.com/JetBrains/compose-multiplatform/releases/tag/v1.11.1)

## Shared root and Android adapter

Keep the application package and the existing one-argument Android call surface.
Use distinct Kotlin file names so the JVM file facades do not collide.

| Source set | Responsibility |
| --- | --- |
| `androidMain/.../App.kt` | Retain `@Composable fun App(onOpenRecovery: () -> Unit)` in its original package/file facade. Read the Application from `LocalContext` and pass the generation holder plus a lazy function that obtains `AppRootDeps`. Creating the function must not read the graph. |
| `commonMain/.../AppRoot.kt` | Expose a composable `App(generationsHolder: AppUiGenerationsHolder, resolveDeps: () -> AppRootDeps, onOpenRecovery: () -> Unit)`. Own the existing generation shell, `AppGenerationContent`, navigation, snackbar and dialog host. |
| `commonMain` | Move the root ViewModel, holder contracts, navigation host/bridge, bus, labels, transitions, focus and bottom-bar logic here. Preserve package identities and the 13 factory properties. |
| `androidMain` / `iosMain` | Implement the single local display-corner operation described below. The common root accepts its host inputs directly. |

Within a host composition, the generation holder has stable identity. Preserve
`SaveableStateProvider(id)`, `key(id)`, admission remembered by the published phase,
and the supplied generation's `ViewModelStoreOwner`. Resolve dependencies once per
admitted region, inside `if (admission.granted)` and the existing `remember(currentPhase.id)`.
`Transitioning` and refused generations resolve zero dependencies. Release the exact
admission token on forgotten and abandoned paths. An aborted transition can readmit the
same ID and recover its saved slot. A successor ID disposes the old slot and gets its
own store/stack. The platform owner retains control of actual runtime/DB replacement.

Retain the app-owned `rememberNavBackStack(screenSavedStateConfiguration, Screen.BottomBar.Home)`,
root back guard, saveable then ViewModel decorator order, all 12 registrations,
screen placement tracing, transitions and the unclipped image-viewer modifier.
Keep snackbar requeue/timeout and the root-level app-dialogs mount. Within the holder-keyed
collector, `OpenRecovery` invokes the latest required callback. `MainActivity` still owns
the startup decision, stamped intent and 7.19A callback. Both real Android entry paths
and the tests that call the one-argument root should work without source edits.

## Result channels, display shape and resources

**Result channels.** Replace the JVM map with a small common, thread-safe registry.
One option is a private `MutableStateFlow<Map<String, MutableStateFlow<Any?>>>` holding
an immutable snapshot. Use an atomic update/CAS to install a channel on a miss and
return the winning channel, even when another caller registers the same key. Read hits
do not copy the map. Never publish a mutable map that will later be mutated in place.
An equivalent existing common primitive is acceptable with the same evidence.

A key retains the same `StateFlow` through set, clear and later navigation. Result
publication precedes pop. `navTo`, plain pop and replace clear values without replacing
the channels. `popBackWithResult` preserves other keys. `openRecovery` preserves its
current clear policy. Retain the 64-slot, replay-zero command bus, sole Metro-scoped
navigator and type-only log labels. `restartApp` calls the existing `AppReinitializer`
directly even with no command subscriber. The Apple actual accepts the existing
`AppReinitializationHost`, which can be supplied by the test host. The old implementation
has no cross-key atomic clear guarantee. Keep per-key correctness under concurrent first
access without holding a lock while invoking callbacks or emitting commands.

**Corners.** Use an internal common `@Composable expect fun displayCornerShape(): Shape`.
The Android actual keeps the existing pre-S fallback, per-corner radius conversion,
density/view keys, global-layout listener and disposal. The iOS actual returns
`AbsoluteRoundedCornerShape(AppDimension.Radius.big)` as the explicit fallback for
this stage. Keep the same per-destination clipping exception and inset order.

**Labels.** Move the two XML catalogs byte-for-byte to private Compose resources
under `commonMain/composeResources/values{,-ru}/strings.xml`, using package
`io.github.stslex.workeeper.app.common.resources`. Change `BottomBarItem.titleRes`
from Android `Int` to CMP `StringResource` and resolve it in composition. Check all
callers of this public property. Keep the three keys, EN/RU texts, order, icons,
screen values, haptics, selection and `BottomAppBarItem_*` semantics tags.
Document the existing CMP primary-locale fallback in the same way as Settings.

## Build and change boundary

Use `convention.kmpComposeLibrary`, Metro and the existing qualifier interop.
Put all twelve feature API edges, app-dialogs impl and dataStore in `commonMain`
with their existing public visibility. Declare direct API dependencies for types
exposed by the bus/holder/enum (core, navigation, flow, lifecycle owner and shared
Compose types as needed). Keep UI host implementations internal where they already
are. Use the KMP lifecycle artifacts, not the Android KTX bundle in common code.
Use the convention aliases, JUnit 5/MockK on Android host, Kotlin/coroutine test
in shared tests, and the existing CMP scene support on iOS.

Implementation paths are `app/common/**`, the necessary catalog aliases in
`gradle/libs.versions.toml`, and these existing gate owners:

- `.github/scripts/assert_kmp_ui_source_topology.py`: migrate all root paths and
  legacy literal checks, including the specialized earlier feature checks. Add
  a composition-root module manifest and root-specific assertions. Keep the
  recovery edge/import ban, deferred resolver, required callback, scoped factory
  flow and all previous feature contracts. The root is not a fourteenth feature.
- `.github/scripts/assert_kmp_ios_smoke.py` and `.github/workflows/android_build_unified.yml`:
  add `:app:common:iosSimulatorArm64Test` and `:app:common:linkDebugTestIosArm64`
  to the existing stable job. Add the exact app/common identities, fresh XML check,
  headers/marker and artifact paths, device-binary check and 19-module denominator.
  Preserve the 18 existing rows and the Settings freshness check.
- `documentation/mockups/mutation_harness.py`: point the toast self-test at
  `commonMain/.../AppRoot.kt` and use the concrete host test task where necessary.
  Its existing expected RED/GREEN pair must still exercise the intended mutations.
- This spec's evidence and minimal truthful changes to `documentation/architecture.md`,
  `documentation/testing.md`, `documentation/ci-cd.md` and, for a real carried item,
  `documentation/tech-debt.md`.

The existing Android adapter keeps `:app:app`'s production entry and its device tests
intact. Any necessary out-of-bound production edit, plugin/toolchain change, new module,
core modification, schema change, new suppression/baseline, golden pixel change, or
new iOS host policy requires separate review. Inspect the active scripts for other
hard-coded source paths before moving files and report a concrete conflict.

## Acceptance and evidence

Keep all four existing suites in `androidHostTest` with their 37 original identities
and assertions, including the MockK telemetry capture and predictive-back timings.
Add the following exact identities to the affected host/Native oracles before reading
test output. Test the production bus/bridge and root. Test-only providers may supply
repositories, owners, dispatchers and generated feature factories for actually mounted
routes. Unused factories may fail on unexpected creation. Keep the real Home and
app-dialogs entry when proving the admitted root. Fixtures stay in `:app:common` tests
and do not introduce an iOS application or test switches in production.

| Source and suite | Required identities and observations |
| --- | --- |
| `commonTest / ResultChannelRegistryTest` | `sameKeyHasOneChannelUnderContention`, `concurrentKeysRetainTheirPublishedFlows`, `navigationClearsValuesWithoutReplacingChannels`. Exercise simultaneous first readers/writers on real workers with a shared start signal, compare channel identity and values, and verify separation/clears. Avoid sleep-based and single-virtual-thread race proofs. |
| `iosTest / AppRootIosTest` | `transitioningAndDeniedSkipGraphResolution` with a resolver that fails on invocation. `admittedRootResolvesOnceAndReleasesItsToken` must show the production Home/root, bottom bar with real labels and the root dialog mount, through stable recompositions and disposal. |
| `iosTest / AppRootIosTest` | `abortedGenerationRestoresStackAndSuccessorStartsFresh` drives same-ID readmission then a new owning generation. `savedStackRoundTripsThroughTheProductionRoot` captures and restores the actual Compose/Nav3 saveable state for a nontrivial stack. This is simulated root recreation. |
| `iosTest / AppRootIosTest` | `resultIsPublishedBeforeTheDestinationPops` observes the real bridge ordering with a result-bearing route. `bridgeUsesLatestRecoveryCallback` replaces the callback with the holder still mounted. `restartReachesHostWithoutACommandCollector` uses the actual iOS reinitializer and a recording host. |

These deliver 40 app/common non-golden host cases (37 original + 3 common) and
10 app/common Native cases (3 common + 7 iOS), before explicitly registered extras.
Pin full class/method tuples using the existing Native prefix/suffix convention.
Use a controlled locale for rendered label checks and separately compare both catalog
blob maps. Keep Android recreation, root-back, result, route, admission, recovery
DB-free and 7.19A callback tests. Preserve the existing Smoke/Regression identities,
including already documented skips. Compare fresh results with the actual entry baseline.

Run topology/MVI/privacy checks and the mutation harness self-test. Prove independent
negative controls for an eager graph read during refusal, keys returning a different
channel, lost result-before-pop ordering, stale recovery callback, missing root workflow
row, and missing/stale/incomplete Native XML. At least one behavioral negative must
fail a fresh Native test. Use the safe harness or an isolated fixture with byte/mode
restoration. A compile failure is not a behavioral negative. Verify restored GREEN.

Run the repository-required builds, host tests/lint, separate Detekt, Android Smoke
plus its identity check, and Regression on a supported emulator. Explicitly execute
`:app:common:testAndroidHostTest`, the full 19 simulator test tasks, all 19 device
links and the Native identity oracle. All load-bearing Gradle invocations use
`--rerun-tasks --no-build-cache --no-configuration-cache --console=plain`.
Preserve start times, actual exit codes, executed-only summaries and fresh, nonempty XML.
Archive Smoke before Regression overwrites it. Link evidence is not a physical-device run.

Record the entry/head SHAs, P0 selection, source/API/asset census, identity sets,
negative controls, reviewed limitations and signed implementation commits. Obtain a
separate implementation review and open the implementation PR into `dev`. The maintainer
merges. Stop the dependent work for a failed P0, graph access before admission, loss
of a result or risk to Android recovery behavior. Leave an unexecuted gate pending.
The following discovery can target the iOS host once this shared root has run.
