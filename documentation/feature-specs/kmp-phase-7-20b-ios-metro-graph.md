# KMP Phase 7.20B — assemble the Apple Metro graph

Proposed for maintainer review at
[`dev@c2883c2f`](https://github.com/stslex/Workeeper/commit/c2883c2ffbdfd4b627762970bf352a5a4d39cb3c), the
merge of [the iOS Room implementation, #355](https://github.com/stslex/Workeeper/pull/355).

## Outcome

Build one production Metro parent for the Apple targets. The decisive simulator journey opens the real Workeeper
database, inserts a uniquely named active training through its DAO, mounts the shared `App` on Home, navigates
to Trainings and finds that exact name. The parent must supply all thirteen real feature factories, including
root-mounted app-dialogs. A device-target link closes the Native build gate.

This stage delivers an iOS graph that the future host can call. A small simulator host exercises it. Swift/Xcode
packaging, a permanent application entry point, native media/date/installation services, the production
generation coordinator, recovery and actual iOS backup belong to later specifications. No App Store readiness
claim follows from this work.

## Verified starting point

| Source on the discovery ref | What it establishes |
| --- | --- |
| [Phase 7.20A evidence](kmp-phase-7-20a-ios-room-database.md#implementation-evidence) and the [iOS factory](../../core/data/database/src/iosMain/kotlin/io/github/stslex/workeeper/core/data/database/AppDatabaseFactory.ios.kt) | The no-argument factory requires an off-Main caller and returns a caller-owned `AppDatabase`. #355 recorded 956 passing simulator cases across 20 modules and 20 device links. |
| [AppRootDeps](../../app/common/src/commonMain/kotlin/io/github/stslex/workeeper/app/common/di/AppRootDeps.kt), [shared root](../../app/common/src/commonMain/kotlin/io/github/stslex/workeeper/AppRoot.kt) and [7.19B](kmp-phase-7-19b-shared-app-common.md#implementation-evidence) | The root resolves dependencies inside an admitted generation. Its contract exposes `CommonDataStore`, one `NavigatorEventBus` and 13 factories for 12 destinations plus the dialog host. |
| [Native root fixture](../../app/common/src/iosTest/kotlin/io/github/stslex/workeeper/AppRootNativeFixture.kt) and [Settings fixture](../../feature/settings/src/iosTest/kotlin/io/github/stslex/workeeper/feature/settings/SettingsNativeGraphFixture.kt) | The root fixture builds three real extensions and excludes ten factories. Both fixtures explicitly supply a `StoreDispatchers` and a single bare `CoroutineDispatcher`. |
| [Apple dispatcher annotations](../../core/core/src/iosMain/kotlin/io/github/stslex/workeeper/core/core/di), [Android providers](../../core/core/src/androidMain/kotlin/io/github/stslex/workeeper/core/core/di/DispatchersBindingContainer.kt) and [StoreDispatchers](../../core/ui/mvi/src/commonMain/kotlin/io/github/stslex/workeeper/core/ui/mvi/di/StoreDispatchers.kt) | The four Apple actual annotations have binary retention but no qualifier meta-annotation. Android's actuals use `javax.inject.Qualifier`. The graph must distinguish IO from UI and default work on Native. |
| [Android parent](../../app/app/src/main/java/io/github/stslex/workeeper/di/AppGraph.kt) and [Trainings visibility test](../../app/app/src/androidTest/kotlin/io/github/stslex/workeeper/app/AllTrainingsExtensionDbVisibilityTest.kt) | The existing parent also owns Android recovery, backup-worker and Wear contracts. The Android device test already demonstrates that a row inserted above a feature graph reaches its UI. |

## P0 — distinguish the four dispatcher keys on Native

Start with a disposable cross-module Metro probe or an isolated first increment in the proposed module. Use
producers and consumers from separate compiled Kotlin modules, including the real `StoreDispatchers`. Supply
four distinguishable tagged dispatcher instances in the probe. Resolve each qualified key and verify the actual
consumer receives the intended instance. A single shared dispatcher or four getters that all forward to the same
value cannot pass. [Metro 1.3.2 binding keys](https://zacsweers.github.io/metro/1.3.2/bindings/#qualifiers)

First try the native Metro qualifier meta-annotation on the existing `iosMain` actuals. Keep all four names,
package, retention, use sites and Android `javax.inject` semantics. If consumers need the Metro annotation
runtime on their Apple API classpath, allow a narrow change to `core/core/build.gradle.kts` exposing the already
pinned runtime only with a reproducing diagnostic. Keep production providers local to the new Apple parent, so
the two existing test parents can supply their own explicitly qualified values. Port their factory bindings and
affected callers without changing assertions or dropping cases. The former bare binding may remain only for a
demonstrated genuinely unqualified consumer, never as the fallback for a qualified one.

Compile and execute the positive simulator probe, link it for `iosArm64` and preserve the existing Android
dispatcher/extension identity cases. For the DI negative, remove one required qualified provider in the
disposable probe and require Metro's diagnostic to identify that missing key. Restore and rerun. If the
qualified use site or provider is lost across a klib, or if Android would need new annotation types/DI policy or
a version change, **STOP** with the exact target, annotated site and diagnostic. Do not build a graph around a
bare dispatcher to bypass P0.

## Production graph and ownership

Create `:app:ios` in `app/ios` as a sibling above `:app:common`. Use the existing KMP Compose convention and
Metro. Keep production code and dependency edges for the Apple parent in `iosMain`. The convention also
declares Android. Keep the new module out of every shipped Android module's dependency graph. Declare the direct
Apple classpath dependencies needed for the actual graph, including `:app:common` and the real
database/exercise/DataStore modules. Retain the existing Nav3, Room, Kotlin and Metro pins. Do not add an Xcode
project, framework export or a new Gradle convention.

Use an internal `@DependencyGraph(AppScope::class)` parent implementing `AppRootDeps` and a Metro-generated
factory. Expose one small construction entry to future host code returning the existing `AppRootDeps` contract.
Use the following ownership boundaries.

| Owner | Contract |
| --- | --- |
| Caller / future host | Supplies an already constructed `AppDatabase`, one generation's `AppScopeLifetime` and the existing `AppReinitializationHost`, `ImageStorage`, `CameraPermission`, `ResourceWrapper` and `InstallEpochSource` contracts. These are required, with no defaults or silent production stubs. The database and lifetime are borrowed. The caller disposes UI/owners, cancels and joins the lifetime, then closes its database. |
| Apple graph | Use `Dispatchers.Main`, `Dispatchers.Main.immediate`, `Dispatchers.Default` and `Dispatchers.IO` for the corresponding keys. Construct the Apple `AppReinitializer(host)`, `PlatformInfoProvider()` and `AnalyticsHolder` as required. Bind the existing [iOS DataStore path resolver](../../core/data/dataStore/src/iosMain/kotlin/io/github/stslex/workeeper/core/data/dataStore/core/IosDataStorePathResolver.kt). Resolve `StoreDispatchers`, the assisted `DataStoreProviderFactory`, `CommonDataStoreImpl`, DB/DAO bindings, repositories, dialog repository and the scoped navigator from the real contributions. |
| Feature extensions | Resolve every factory through the generated parent. Pass real typed `Screen` arguments at the seven argument-taking factories. Keep one Store/extension per entry. Use the current iOS Settings unavailable-backup binding. No production `excludes`, handwritten factories, fake repositories, nullable ports, service locator or duplicate navigator. |
| Simulator test host | Supplies only the external host contracts and UI generation holder/owner. Recording implementations belong in `iosTest`. Use a stable test installation epoch and a recording restart host. A fixture may reject unused media operations. The repo's test-only `AppRootNativeParent` and `SettingsNativeParent` remain fixtures in their original modules. |

The graph never opens/closes a second database, resolves dependencies from a composable outside admission, or
implements an in-process restart by rebuilding itself. `AppReinitializer` forwards the request to its supplied
host. The [Phase 5 host obligations](kmp-phase-5-startup-processor.md) remain for the separate runtime stage.
Apple media, date formatting and durable installation identity remain explicit host dependencies even though the
graph can now be built and tested. The activated root dialog uses the real repository and preference files. The
existing Settings backup capability stays unavailable.

## Simulator acceptance

Register these seven exact methods in `io.github.stslex.workeeper.app.ios.IosAppGraphTest` before inspecting
output. Each must occur once in fresh Native JUnit XML and pass.

| Method | Required observation |
| --- | --- |
| `qualifiedDispatchersReachProductionConsumers` | Assert each production key resolves to its intended Apple dispatcher, including `StoreDispatchers` and a feature extension with qualified accessors. The P0 tagged graph separately proves key discrimination even if two platform schedulers share machinery. |
| `allThirteenFactoriesConstructRealStores` | Invoke all 13 factory methods with valid route values where required and resolve each extension's actual Store. Record the factory list and clean up all created active Stores. Reading 13 property names is insufficient. |
| `navigatorIsSharedAndRestartReachesHost` | The parent-injected `Navigator` and concrete `NavigatorEventBus` are identical. Without a command collector, `restartApp()` reaches the recording host exactly once. |
| `unadmittedRootSkipsGraphResolution` | Mount the real common `App` with `Transitioning` and a refused generation. The resolver must not run. On admission, resolve once across recomposition and render Home. Keep the existing 7.19B token/recreation tests. |
| `admittedRootShowsTrainingFromOwnedDatabase` | Open the public 7.20A factory off Main. Through its `trainingDao` insert a unique active, non-adhoc training and an archived control. Navigate through the real root from Home to `Screen.BottomBar.AllTrainings`. Await the active name in rendered semantics and verify the archived name is absent. Use the production parent and Paging flow. |
| `preferencesAndDialogsSurviveGraphRecreation` | Update `CommonDataStore` and publish a synthetic dialog through the real repository. A second graph with the same install epoch reads the preference and pending dialog, and dismissal clears it. Check that the expected `common_prefs.preferences_pb` and `app_dialogs_prefs.preferences_pb` files exist under the established `Documents/datastore` location. This proves same-process graph recreation. |
| `generationLifetimesAreIndependent` | Build two graphs with distinct caller-owned lifetimes. Observe a real Store job or generation-owned child complete during teardown of the first. The successor keeps its own navigator, active lifetime and functioning Store. Avoid claims about production replacement or process death. |

Use a fresh test-owned simulator for any default-path writes. Verify the expected simulator UUID against the
running process and sandbox before writing. The [database module](../../core/data/database/build.gradle.kts)
already wires an owned simulator and runs its public-factory smoke with an exclusive directory. Serialize the
graph and database test processes on that simulator, or use two separately provisioned devices. Do not let the
two suites race on `Workeeper/app.db`. Do not touch user simulators or delete pre-existing directories. Keep
[DataStore's process-wide name cache](../../core/data/dataStore/src/commonMain/kotlin/io/github/stslex/workeeper/core/data/dataStore/core/DataStoreProvider.kt)
intact. Never unlink a preference file while a live store owns it. Clean up the newly created simulator after
the test processes exit.

## CI, evidence and stop conditions

Extend the current [macOS job](../../.github/workflows/android_build_unified.yml) from 20 modules to 21. Keep
its stable check name, the existing 20 expectation dictionaries, owned database simulator, freshness markers and
artifacts. Add `:app:ios:iosSimulatorArm64Test`, its marker/XML, `:app:ios:linkDebugTestIosArm64` and a fresh
nonempty device executable. Register the seven tuples in
[the Native oracle](../../.github/scripts/assert_kmp_ios_smoke.py). Update the previous 20-module workflow
assertions in [the topology script](../../.github/scripts/assert_kmp_ui_source_topology.py), retaining proof
for Settings, app/common and the database. Track the new module as the host graph in the topology check.

In separate restorable controls, bind a production dispatcher key to the wrong implementation and require the
consumer test to fail. Independently make the unfiltered active-Trainings query omit the seeded row and require
the root UI case to fail while a raw/DAO read still finds the seed. Both must compile, execute the intended
Native test, and go green after exact restoration. A compile-only negative is appropriate for the missing Metro
key in P0. Reject a missing host workflow row and missing/stale/incomplete host XML with validator fixtures.
Compare all seven database schema files byte-for-byte with the base. Compare resolved release/runtime inputs for
the phone to show the new Apple module is absent.

Run the following gates in Bash on the supported hosts. Provision the owned simulator and create fresh markers
as the workflow does before measuring. Run Detekt separately.

```bash
G=(--rerun-tasks --no-build-cache --no-configuration-cache --console=plain)
./gradlew :app:ios:iosSimulatorArm64Test "${G[@]}"
./gradlew :app:ios:linkDebugTestIosArm64 "${G[@]}"
./gradlew assembleDebug lintDebug testDebugUnitTest :app:common:testAndroidHostTest :core:data:database:testAndroidHostTest "${G[@]}" --continue
./gradlew assembleDebugAndroidTest "${G[@]}" --continue
./gradlew verifyPaparazziDebug :lint-rules:test "${G[@]}" --continue
./gradlew detekt "${G[@]}" --continue
./gradlew connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.annotation=io.github.stslex.workeeper.core.ui.test.annotations.Smoke "${G[@]}" --continue
python3 .github/scripts/assert_mvi_device_identities.py
# Archive Smoke evidence before Regression overwrites the output.
./gradlew connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.annotation=io.github.stslex.workeeper.core.ui.test.annotations.Regression "${G[@]}" --continue
```

Also execute every simulator and device-link task in the updated 21-module workflow, run
`python3 .github/scripts/assert_kmp_ios_smoke.py`, and check all fresh binaries. Run the source topology, MVI
host/source and privacy gates. Compare identities with fresh entry baselines. #355 reported Android Smoke 44
with three known skips, Regression 90 including 35 database cases, and 956 Native identities. Preserve the exact
former cases and skip states. Device links are not device executions.

Allowed edits are `app/ios/**`, its inclusion in `settings.gradle.kts`, the Apple qualifier actuals,
explanatory comments and the bounded annotation classpath fix from P0, the two affected Native test fixtures,
the CI/scripts above, this spec's evidence and focused updates to
`documentation/{architecture,testing,ci-cd}.md`. Additional approval is required for changes to released
Android behavior, the common root or feature production sources, database/schema or DataStore policy,
media/backup/recovery behavior, toolchain pins or a new runtime abstraction.

Implementation begins only after the spec is accepted on `origin/dev` and Ilya gives GO. STOP on failed P0,
unexpected required roots, loss of admission/dispatch correctness, Android runtime drift, new
suppressions/baselines, golden movement or missing execution evidence. Report commands, times, task/test counts,
identities/skips, controls, ownership and remaining device/host work. Use signed Conventional Commits and an
independent final review on the implementation head. Open one implementation PR into `dev`. The maintainer owns
merge.
