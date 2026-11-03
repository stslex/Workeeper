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

## Implementation evidence

Entry is `origin/dev@d30d0f2f862915a13b3c8034d1842513949da984`, the actual merge of
[specification PR #356](https://github.com/stslex/Workeeper/pull/356). Its accepted specification
blob `9bcc3edd384792aa3d29df790ff7293bfd9bdb78` equals the blob on final reviewed head
`930c097a92c6522bfb21a591d292eeccc5eeb441`; the merge is reachable from `origin/dev`.
Implementation uses the separate `codex/kmp-7-20b-ios-metro-graph` branch/worktree. The PR records
the exact published head, GitHub verification of every implementation commit and independent
review provenance.

### Host and ownership inventory

Local host: Apple silicon arm64, macOS 27.0 (`26A428`), Xcode 27.0 (`27A266a`), JBR 21.0.11.
The existing Kotlin 2.4.10 and Metro 1.3.2 pins are unchanged. Android execution uses
`Pixel_6_API_34`, `emulator-5554`, API 34, `arm64-v8a`. Local long gates ran on AC under
`caffeinate`; the later closed-lid state was checked with an online external main display,
`AppleClamshellCausesSleep=No` and `IOPMUserIsActive=Yes`. No power policy was changed.

The task created its own iOS 26.5 simulators. Entry/P0 used
`7D203404-E9C0-44FE-9E67-7D7BABED2572`; after the initial UI teardown experiments, the final suite
and independent controls used `CAA5D7AA-88BE-42C9-917A-1210CB5274FF`
(`Workeeper-Phase-7.20B-20261102-r3`). Tests compare the expected UUID with `SIMULATOR_UDID`,
`NSHomeDirectory()` and the user-domain support path before the public database factory writes.
Database and graph tasks execute serially on that device. Only the newly created default database
directory is removed after handles close; DataStore retains ownership of its preference files
until process exit. Task-owned simulators are removed only after all test processes exit.

`build/kmp-7-20b-evidence/entry.json` retains the seven schema blob/SHA-256 pairs, the twenty
original Native expectation dictionaries and the seven graph identities registered before output.
`feature-factories.json` records all thirteen source declarations. The graph requires exactly the
two borrowed roots and five host ports in the accepted ownership table. Real contributions supply
Room repositories, DataStore, dialog persistence, the scoped navigator and all thirteen feature
extensions. No extra root, bare dispatcher, production stub, Android DI change or API-classpath fix
was needed.

### Dispatcher P0

A disposable three-module probe separates producer, consumer and test host. Four tagged dispatcher
instances cross compiled module boundaries into qualified consumers and the real `StoreDispatchers`.
Both Native fixtures now supply qualified inputs while retaining all original test assertions.
The positive probe also links its fresh iosArm64 executable.

The unchanged canonical mutation harness then removes only the probe's IO provider. The exact
command is `:probe:dispatcher-host:compileTestKotlinIosSimulatorArm64 -I
build/kmp-7-20b-evidence/probe.init.gradle` with the four forced-execution flags below. It exits 1
with `[Metro/MissingBinding] No binding found for @IODispatcher CoroutineDispatcher`, reporting
`35 actionable tasks: 35 executed`. This is an intended compile-only negative, classified
`INVALID` by the behavioral harness. Its actual command/log/status and exact byte/mode restoration
are retained in `p0-missing-key-r2/`. Restored simulator cases, device links and Android's existing
59 dispatcher/extension identity cases pass. Probe modules remain outside Git.

### Entry and P0 receipts

All receipt paths below are relative to ignored `build/kmp-7-20b-evidence/`. Each receipt records
the full command, actual exit status, start/end UTC timestamps and execution-only Gradle summary.
Copied fresh XML and exact identities/skip states accompany test commands. Times are on
2026-11-02 UTC; durations are rounded seconds. A validator row claims no test execution.
Every Gradle gate uses `--rerun-tasks --no-build-cache --no-configuration-cache --console=plain`.
The frozen local gate/control manifest is `local-gate-manifest-sha256.json`, SHA-256
`b7a05913eb247f880a1206fa47470f093e3455b37e13f61de37587b1317a0c54`.
Later publication metadata is recorded separately.

| Gate | Executed result | Start UTC; duration | Receipt |
| --- | --- | --- | --- |
| 20-module Native entry | `560 actionable tasks: 560 executed`; 956 cases / 0 skips | 17:24:37; 328s | `entry-native/receipt.json` |
| Android DI / database / root host entry | `623 actionable tasks: 623 executed`; 265 cases / 0 skips | 17:30:12; 169s | `entry-host/receipt.json` |
| Smoke entry | `2117 actionable tasks: 2117 executed`; 44 cases / 3 skips | 17:33:15; 338s | `entry-smoke/receipt.json` |
| Regression entry | `2117 actionable tasks: 2117 executed`; 90 cases / 0 skips | 17:38:55; 348s | `entry-regression/receipt.json` |
| Cross-module dispatcher probe + device link | `85 actionable tasks: 85 executed`; 1 case / 0 skips | 17:45:28; 72s | `p0-positive/receipt.json` |
| Restored probe / two Native fixtures + links | `545 actionable tasks: 545 executed`; 43 cases / 0 skips | 17:48:24; 146s | `p0-restored-native/receipt.json` |
| Final Native fixtures / Android DI identities | `823 actionable tasks: 823 executed`; 101 cases / 0 skips | 17:51:57; 144s | `p0-fixtures-and-android/receipt.json` |

### Exit receipts

The Bash commands in the accepted gate section ran unchanged; the full Native task lists match
the updated workflow. Freshness markers were created before simulator execution and device
linking. `exit-native.sh` also uses `--no-parallel`; the graph task is ordered after the database
task. Smoke XML was copied before Regression overwrote generated output.

| Gate | Executed result | Start UTC; duration | Receipt |
| --- | --- | --- | --- |
| Seven focused graph simulator cases | `350 actionable tasks: 350 executed`; 7 cases / 0 skips | 18:15:07; 100s | `graph-native-focused-r3/receipt.json` |
| Focused graph device link | `349 actionable tasks: 349 executed`; 0 cases / 0 skips | 18:19:16; 80s | `focused-device-link/receipt.json` |
| All 21 simulator tasks + Native oracle | `584 actionable tasks: 584 executed`; 963 cases / 0 skips | 18:20:41; 302s | `exit-native/receipt.json` |
| All 21 device-target links | `563 actionable tasks: 563 executed`; 0 cases / 0 skips | 18:25:46; 134s | `exit-device-links/receipt.json` |
| Android build / Lint / host tests | `3149 actionable tasks: 3149 executed`; 3398 cases / 0 skips | 18:28:05; 832s | `exit-android-build-host/receipt.json` |
| Device-test compilation | `2210 actionable tasks: 2210 executed`; 0 cases / 0 skips | 18:42:01; 353s | `exit-device-compile/receipt.json` |
| Paparazzi verification / lint-rules tests | `638 actionable tasks: 638 executed`; 1938 cases / 0 skips | 18:47:58; 103s | `exit-paparazzi-lint-rules/receipt.json` |
| Separate Detekt | `64 actionable tasks: 64 executed`; 0 cases / 0 skips | 18:49:45; 39s | `exit-detekt/receipt.json` |
| Smoke | `2171 actionable tasks: 2171 executed`; 44 cases / 3 skips | 18:50:28; 778s | `exit-smoke/receipt.json` |
| Smoke identity oracle | `validator exit 0`; 0 cases / 0 skips | 19:03:30; 0s | `exit-smoke-oracle/receipt.json` |
| Regression | `2171 actionable tasks: 2171 executed`; 90 cases / 0 skips | 19:03:30; 814s | `exit-regression/receipt.json` |
| Dev release runtime report | `5 actionable tasks: 5 executed`; 0 cases / 0 skips | 19:17:09; 11s | `exit-runtime-dev/receipt.json` |
| Store release runtime report | `5 actionable tasks: 5 executed`; 0 cases / 0 skips | 19:17:24; 8s | `exit-runtime-store/receipt.json` |

All 956 previous Native tuples and skip states are retained, with exactly the seven preregistered
graph tuples added: 963 total, no failures/errors/skips. The original twenty expectation
dictionaries are unchanged. Android retains all 265 entry DI/database/root host tuples.
Smoke's exact 44 identities retain the three existing
`pendingFeatureRewrite` skips in archive, all-trainings and all-exercises. Regression retains all
90 identities, including all 35 database cases, with no skips. `exit-audit.json` verifies those
multisets, fresh XML timestamps, all source digests and unchanged dev/store release runtime trees;
`:app:ios` is absent from both shipping trees.

All 21 device executables are fresh and nonempty. `exit-binaries.json` records path, size,
timestamp, SHA-256 and Mach-O platform `IOS`; copies are retained in `exit-binaries/`. The new
graph executable is 95,318,256 bytes. P0's three executable copies are in `p0-binaries/`.
Device linking is distinct from simulator execution and does not prove physical-device execution.

Source topology, MVI source/host identity and privacy gates pass (`source-topology/`,
`mvi-source-topology/`, `mvi-host-identities/`, `privacy/`). `source-audit/` and
`golden-byte-audit.json` retain exact equality for all seven schema files, 456 golden PNGs,
toolchain pins, Android qualifier actuals, privacy documents and existing Native assertion files.
No suppression, baseline or golden was changed. The mockup shell gate passes all eleven checks;
the known `f52462c7` negative exits 1 and reproduces check 7's `width=0px→0px` discriminator.
Optional actionlint reports the same existing SC2129 finding on entry and implementation;
normalized diagnostics are identical and this check is not reported as green.

Synthetic validator fixtures accept complete 21-module XML, reject missing/stale/incomplete host
XML and reject either missing host simulator or device-link CI row. Source is unchanged by these
fixtures. `validator-controls-run-r2/` and `validator-fixtures/` prove validator discrimination,
not Native execution.

### Independent behavioral controls

Two separate clean worktrees start from signed implementation snapshot
`071e0a290359f737ccd35e8d951df67a5cc14e95`. The unchanged canonical `mutation_harness.py`
owns each one-shot mutation and finally-restoration. An observation-only subprocess wrapper
retains original stdout/stderr, actual process results, commands and timing without replacing
the harness's verdict. Each positive, mutant and restored run compiles, links and executes exactly
one intended simulator identity, with fresh XML and an archived `IOSSIMULATOR` executable.

| Mutation | Measured sequence | Evidence |
| --- | --- | --- |
| Default provider returns Main.immediate | positive: exit 0, `350 actionable tasks: 350 executed`; mutant: exit 1, `350 actionable tasks: 350 executed`; restored: exit 0, `350 actionable tasks: 350 executed` | `behavior-controls/behavior-dispatcher/` |
| Active Paging query adds `AND t.uuid IS NULL` | positive: exit 0, `350 actionable tasks: 350 executed`; mutant: exit 1, `350 actionable tasks: 350 executed`; restored: exit 0, `350 actionable tasks: 350 executed` | `behavior-controls/behavior-query/` |

The dispatcher mutation fails `qualifiedDispatchersReachProductionConsumers[iosSimulatorArm64]`
at the real `StoreDispatchers` Default identity assertion, with an `AssertionError` showing the
expected `DarwinGlobalQueueDispatcher` and actual `Dispatchers.Main.immediate`. The query mutation fails
`admittedRootShowsTrainingFromOwnedDatabase[iosSimulatorArm64]` with `ComposeTimeoutException`
while awaiting the active sentinel. Before mounting the root, the unchanged DAO `getById`
assertions still find both seeded rows. Only `pagedActiveWithStats` is mutated. The archived
mutant source, exact failure type/message, normalized Native class/target identity, source hashes
and restored file modes are retained. Each restoration returns the same case to one pass with no
skip/error/failure, and the tracked control tree is clean. Evidence is copied and hash-verified
before either temporary control worktree is removed without force.

Initial setup failures (a duplicated Gradle option and evidence-directory collisions) and the
first two UI teardown experiments are retained separately and excluded from green evidence.
The first dispatcher observer also rejected Kotlin/Native's concrete Default rendering after the
intended RED; its incomplete receipt is retained separately. The corrected observer verifies that
concrete expected dispatcher, actual Main.immediate and the exact consumer assertion site.
The first teardown blocked inside nested `runBlocking`; the second timed out with a manually
driven lifecycle fixture. The final test observes a real generation-owned child finish, then
keeps the successor mounted through the actual shared App/Store path. Its suspend teardown
disposes composition and owners before joining lifetimes and closing the database.

### Review and remaining platform work

The implementation PR carries independent review against the exact published head, final-head
CI status and GitHub verification for every new signed commit. The maintainer owns merge.
The public graph is ready for a separately specified host. Swift/Xcode packaging, native media
and date services, durable installation identity, production generation replacement/recovery,
iOS backup and physical-device/App Store acceptance remain separate work. Same-process
preferences/dialog recreation does not establish process-death or production runtime replacement.

### Native CI timeout follow-up

The follow-up starts from published head `12d2de8a98fc69e79634a2d48bf9ed74cf65d2a3`.
The first Native CI attempt never acquired a hosted macOS arm64 runner. Attempt 2 executed
`584 actionable tasks: 584 executed`: 962 of 963 cases passed, including all seven new graph
cases. The only failure was the unchanged
`LiveWorkoutFeatureSceneIosTest.resourcesScreenSheetsDialogsAndActionsRenderAndDispatch`:
`UncompletedCoroutinesError: After waiting for 1m, the test body did not run to completion`.
Its XML reports 76.582 seconds and stdout contains `waitForIdle has not finished`.
All 21 device links passed separately (`563 actionable tasks: 563 executed`). The artifact ZIP
SHA-256 was checked against GitHub before extraction. Raw log, XML and binary are retained under
`build/kmp-7-20b-evidence/ci-attempt-2/`.

The isolated scene passed with both Native standalone modes. A complete LiveWorkout/Exercise
parallel run with graph linking also passed. A controlled Main dispatcher alone did not reproduce
the timeout. These attempts do not establish an OS, standalone-mode or cross-process cause.

The pinned CMP 1.11.1 source separates the v2 test-body scheduler from its composition scheduler.
The old settle helper advanced only the latter and synchronously called `waitForIdle`, without
yielding the test body. A deterministic regression queues the existing final transition to the
two-row scene on the test body's coroutine context. With the original helper all ten settle steps
return without running that producer, and the new completion assertion fails. This compiled
control reports `131 actionable tasks: 131 executed` and the intended `AssertionError`; the
canonical mutation harness then restores exact source bytes and mode. This reproduces the
unserviced scheduler work, not the exact CI wall-clock timeout or its platform/load trigger.

The helper and its callers now suspend, yield to queued producers, advance the same bounded
composition clock, and use `awaitIdle`. The queued transition and completion assertion remain
in the scene test. All original catalog, plural, mapper, rendering and 42 ordered-action assertions
remain. The original default 60-second budget, ten settle steps, Native identities, suppressions,
baselines and goldens are unchanged.

A separate preflight issue was visible as an Xcode `Broken pipe` exception: `grep -q` could close
the pipe while `xcodebuild` was still writing. The preflight now captures each command's complete
output before checking it. It retains Xcode 26.6, simulator-runtime checks and failure propagation.
Synthetic shell controls accept valid output and reject a wrong version, absent runtime and a
failing runtime command; they are not Native execution evidence.

Follow-up receipts are under ignored `build/kmp-7-20b-timeout-evidence/`. They retain full commands,
actual exit codes, UTC start/end timestamps and fresh XML. Before `clean`, all earlier evidence,
Native binaries and database fixtures were moved aside; 2541 files were byte-verified after
restoration. Clean is preparation, not an executed test gate. The mandatory validations below
all report execution-only summaries.

| Gate | Executed result | Duration | Receipt |
| --- | --- | --- | --- |
| Focused scene with queued-state regression | `131 actionable tasks: 131 executed`; 1 cases / 0 skips | 55s | `fixed-single/receipt.json` |
| 21-module Native suite and oracle | `584 actionable tasks: 584 executed`; 963 cases / 0 skips | 134s | `validation-native/receipt.json` |
| 21 device-target links | `563 actionable tasks: 563 executed`; 0 cases / 0 skips | 130s | `validation-device-links/receipt.json` |
| Android build / Lint / host tests | `3149 actionable tasks: 3149 executed`; 3398 cases / 0 skips | 734s | `validation-android/receipt.json` |
| Separate Detekt | `64 actionable tasks: 64 executed`; 0 cases / 0 skips | 32s | `validation-detekt/receipt.json` |

The final audit retains all 963 Native and 3398 Android-host tuples and skip states, seven schema
files and 456 golden PNGs. Source topology and privacy pass. Fresh device binaries and hashes
are archived in `validation-binaries/` and `validation-binaries.json`; device linking remains
distinct from device execution. Final-head CI, signatures and review are recorded in PR #357.

### Additional CI diagnostics and bounded scene settling

Run 956 on signed head `ef3135a85ad05518395ce8c525026627ee137790` executed all 963 Native
identities again. The same LiveWorkout scene failed after 66.779 seconds with the default
one-minute `UncompletedCoroutinesError`; all other cases, including the seven graph cases,
passed. The queued-producer helper repair alone therefore did not resolve the hosted failure.
The stdout still contained only the synchronous `waitForIdle` warning, without a phase receipt.
Artifact `13220832773` was downloaded and verified against SHA-256
`ea293808847f4a2de625968c8868c3598945059d88c6fc8460389fd36c13c9b6`.
Its full log, XML and failure stack are in ignored `ci-956-native-failure/` under the follow-up
evidence directory. The already-red run was cancelled during device linking to finalize logs;
this run provides no green device-link claim.

The Android job separately failed `WearDisabledPressGateTest` on `XL_ROUND/1.24`: the disabled
hold changed 5653 pixels where zero are required. The unchanged focused case, full 360-case
store suite and 48 profile exposures within one scene all passed locally; each invocation
reported `106 actionable tasks: 106 executed`. The repeated exposures share a process and
composition, and their PNG names are overwritten, so they are not 48 independent executions.
The failing hosted PNGs were not uploaded. Independent source review found no reproduced Wear
defect justifying a behavior, threshold or timeout change. No such change is made.

The workflow now uploads failing host XML and Wear before/held captures, and uploads Native
failure XML and freshness markers before device linking. The final Native artifact remains.
Existing test commands, module coverage and job timeouts are unchanged. Native phase logging
records monotonic elapsed time and the main-thread flag, including mount, input, sheet/dialog,
settle and completion boundaries.

Pinned CMP's Skiko render loop renders every 16 ms of composition-clock progress. The helper's
unconditional 1000 ms advance rounds to 1008 ms, about 63 frames even for a quiet scene. A local
traced Native debug scene reached 39040 ms of virtual clock. The helper now yields, advances one frame, and
cooperatively awaits current idleness with auto-advance still enabled. Pending snapshots,
layout and finite animations continue to run; a fixed second of idle rendering is unnecessary.
The ten-round bound, 60-second budget, exact identities and all original assertions remain.
Future timer events are outside this helper: the scene transitions are test-held State.
This removes unconditional idle clock progress; its relationship to the hosted deadline still requires
final-head CI evidence.

Further local validation of the one-frame helper, Native debug test binaries on the same
Apple-silicon/Xcode 27.0/iOS 26.5 host:

| Gate | Executed result | Receipt |
| --- | --- | --- |
| 21-module Native suite and identity oracle | `584 actionable tasks: 584 executed`; 963 cases, 0 skips | `optimized-native/receipt.json` |
| 21 fresh device-target links | `563 actionable tasks: 563 executed`; 0 cases, 0 skips | `optimized-device-links/receipt.json` |
| Android build, Lint and host tests | `3149 actionable tasks: 3149 executed`; 3398 cases, 0 skips | `optimized-android/receipt.json` |
| Separate Detekt | `64 actionable tasks: 64 executed`; 0 cases, 0 skips | `optimized-detekt/receipt.json` |

All 963 Native and 3398 Android identities, including their module/task and skip states, match
the preceding receipts. The 7 schema files and 456 PNGs remain byte-identical. All 21 fresh IOS
executables are archived and hash-verified in `optimized-binaries/`; APFS clones preserve each
file independently without duplicating unchanged data blocks. Source/MVI topology, MVI host
identities and privacy pass. Workflow test commands and timeouts match the preceding head;
actionlint retains only its unchanged pre-existing SC2129 diagnostic and is not claimed green.
The local scene's final clock moves from 39040 to 15056 ms with all assertions retained; the
individual XML times are receipts, not a controlled CPU benchmark or shipping-performance claim.

The first local diagnostic full run is excluded: 948 cases passed but the owned simulator was
Shutdown, so the database and app:ios cases could not spawn. Its failure receipt remains. The
owned UUID/name were verified and only that simulator was booted before the successful repeat.
The initial new artifact declaration also duplicated explicit freshness-marker lines rejected by
the source-topology oracle. Using a diagnostic marker glob preserves the original explicit final
artifact rows and returns the unchanged oracle to GREEN. These setup/declaration failures are
separate from the hosted LiveWorkout timeout.
