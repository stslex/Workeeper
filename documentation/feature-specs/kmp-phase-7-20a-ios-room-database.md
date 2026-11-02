# KMP Phase 7.20A — open the Workeeper Room database on iOS

**Proposed for maintainer review.** Discovery base: [`dev@c3c51717`](https://github.com/stslex/Workeeper/commit/c3c51717b66ebef8ad504aada35aeac369e4ac7d), the merge of [Phase 7.19B, PR #353](https://github.com/stslex/Workeeper/pull/353).

## Outcome

The future Apple host can construct a file-backed `AppDatabase` using a production iOS entry point in `:core:data:database`. On an arm64 simulator the actual DAOs must write, close and reopen the file, preserve data through both supported upgrade paths, and refuse an unsupported database without losing its contents. On `iosArm64` the same implementation must link into a test binary. Android must remain releasable throughout.

This is the first persistence slice of the iOS host. The next discovery can use the real factory to assemble the Apple Metro graph and connect it to the shared root. This specification authorizes neither that graph nor a Swift/Xcode application. It does not decide App Store scope or change the existing deferral of iOS backup.

## What is already on dev

| Evidence | Consequence |
| --- | --- |
| The [7.19B implementation](kmp-phase-7-19b-shared-app-common.md#implementation-evidence) recorded 948 simulator cases in 19 modules, 19 device-target links and 11 `:app:common` Native cases. Its [Native parent](../../app/common/src/iosTest/kotlin/io/github/stslex/workeeper/AppRootNativeFixture.kt) injects test repositories and supports three of the thirteen feature factories. | Running the shared Home screen does not exercise an on-disk iOS database. Leave those test fixtures in their current source set. |
| [Phase 5](kmp-phase-5-startup-processor.md) hands database creation and filesystem behavior to the Apple host. [Phase 6](kmp-phase-6-data-layer.md) shared the Room model and DAOs. [AppDatabase](../../core/data/database/src/commonMain/kotlin/io/github/stslex/workeeper/core/data/database/AppDatabase.kt) names `app.db`. The [factory](../../core/data/database/src/androidMain/kotlin/io/github/stslex/workeeper/core/data/database/AppDatabaseFactory.kt) is Android-only. | Add only the missing platform construction path. The production module has no `iosMain` factory or `iosTest` suite at the discovery base. |
| The [registry](../../core/data/database/src/commonMain/kotlin/io/github/stslex/workeeper/core/data/database/migration/MigrationsRegistry.kt) sets version 7 and registers `Migration6` (5 → 6) and `Migration7` (6 → 7). The [schema exports](../../core/data/database/schemas/io.github.stslex.workeeper.core.data.database.AppDatabase) include versions 1–7. The v7 Room identity hash is `78d89911aae7f1a3fd5a4bb4d15b84c9`. | Versions 1–4 are historical exports outside the supported migration range. Keep all seven exports byte-identical. |
| The [Room convention](../../build-logic/convention/src/main/kotlin/RoomLibraryConventionPlugin.kt) already configures KSP for each declared Native target. Only `androidMain` gets the bundled driver. The catalog pins Room 3.0.0, SQLite 2.7.0 and KSP 2.3.9. The older [Native probe](../research/kmp-probe2-room-p2c-findings.md) compiled generated code without querying a file. | Use the existing catalog alias in this module's `iosMain`. Execute KSP and verify the generated constructor on each Apple target. |

## Production contract

1. Add a small Apple-only `buildIosAppDatabase(): AppDatabase` entry point callable by a future host. Resolve `NSApplicationSupportDirectory` in the user domain at runtime. Create `Workeeper` as needed and use `AppDatabase.NAME` as the leaf, giving `<Application Support>/Workeeper/app.db`. The path stays stable across opens within the installation. Directory creation is idempotent. Use a file URL/path, propagate failures, and never fall back to Documents, Caches, a temporary path or memory. This path is a proposal for maintainer approval. The existing DataStore location and OS backup policies are outside this change. [Apple file placement](https://developer.apple.com/documentation/foundation/using-the-file-system-effectively)
2. Keep the production factory callable without a path parameter. An internal overload may accept an Application Support root so tests can give each file its own directory and exercise the same path construction, directory creation and Room configuration. Tests may not replace the builder or driver. Perform blocking filesystem/open operations on a non-Main dispatcher. Callers own and close their instance. A future host must run blocking path resolution off Main. Room may defer opening until first access, so every database-open test must force a real DAO/SQL operation.
3. Use `androidx.room3`, the generated `AppDatabaseConstructor`, `BundledSQLiteDriver` and an off-Main query context on iOS. Add the existing `libs.androidx.sqlite.bundled` alias to this module's `iosMain` dependencies. Keep one registration site for every entry in `MIGRATIONS` and the existing `onOpen` call to `reportReconciledTargets()` after a committed migration. A small internal common builder function can carry just these two responsibilities. Keep Android's public `buildAppDatabase(context)`, file location, driver, callback order, query-context default and lifecycle intact. [Room KMP builders](https://developer.android.com/kotlin/multiplatform/room)
4. An unsupported version, denied/unusable directory or migration error must propagate. Close any allocated handle on a failed first query, including in tests. The builder must also release anything it allocated if construction throws. Preserve the original file and rows. No destructive migration, automatic repair, file replacement, schema bump, new telemetry or backup/restore operation. No hand-written Room actual, missing-actual suppression or new toolchain pin.

## Simulator acceptance

Pre-register these eight methods under `io.github.stslex.workeeper.core.data.database.IosAppDatabaseTest`. Use the Native classname prefix and target suffix already enforced by the repository. Each method must execute exactly once and pass.

| Method | Observation required |
| --- | --- |
| `defaultLocationUsesApplicationSupport` | Resolve Apple's actual user-domain Application Support directory and assert the exact child components. In a disposable simulator/test sandbox also exercise the public no-argument entry by writing and reading a unique marker through a DAO. Never delete a pre-existing database. |
| `tagSurvivesCloseAndReopen` | Create an initially absent directory, write a fixed UUID/name, observe the physical file, close, construct a second handle at the same location and read the exact row. A never-written UUID/name stays absent. |
| `upgradesV5ThroughV7` | Seed a valid exported v5 database. Through the production factory force first access and verify a preserved sentinel, v6's `exercise_table.is_adhoc` default for an existing exercise, user version 7 and the v7 Room identity. Reopen successfully. |
| `upgradesV6WithDuplicateTargets` | Seed a valid v6 database with duplicate positions for one performed exercise. Preserve both rows/UUIDs, verify deterministic distinct positions, the v7 unique index and the v7 Room identity. Use the expectations from the [Android migration tests](../../core/data/database/src/androidDeviceTest/kotlin/io/github/stslex/workeeper/core/data/database/AppDatabaseMigrationTest.kt). |
| `unsupportedVersionKeepsOriginalRow` | Create an isolated future-version sentinel fixture, force a Room query and require the expected missing-path/downgrade failure. Reopen with raw SQLite and check its original version and exact sentinel. Catching an unrelated exception does not pass. |
| `unusableDirectoryFailsWithoutFallback` | Put a regular file where a directory is required. The real factory path fails, its original contents survive and no substitute DB is returned. |
| `childWritesRollBackTogether` | Invoke the actual `DbCascadeBindingContainer.provideDbTransitionRunner` on the file. Child writes join one transaction. Catch the injected failure by type/identity, prove neither child row survived and distinguish a precommitted control row. |
| `activeTagFlowObservesCommittedWrite` | Keep the same real `TagDao.observeAll()` collector subscribed before and after a write through the production runner. Await its initial emission, commit and observe the new value with a timeout. A second subscription or a one-shot query cannot prove invalidation. |

If a test cannot establish exclusive ownership of its simulator sandbox, fail setup before touching the default database. Removing an existing application, its container or its files is never test setup. Use a newly provisioned test-only simulator when ownership is uncertain.

Create historical fixtures from the tracked v5/v6 JSON, including the tables, indices, Room master row and correct `user_version`. Prefer the pinned Native Room testing support if it can produce those files. Otherwise generate test-only inputs from the same JSON and ship them in the Native test binary. Record the source blob IDs and show a raw SQLite query observing the version, schema identity and seed before Room opens the fixture. Relabeling an empty or current database does not test either migration. Use only synthetic data and uniquely owned directories. Close readers, Room and SQLite handles before cleaning up those directories or their sidecars. Keep the default-path smoke on a disposable, separately owned simulator container.

## Implementation boundaries

Production edits are limited to `core/data/database/build.gradle.kts`, the new `iosMain` entry, and the minimum common/Android factory wiring above. Put simulator tests and any test-only fixture generation in the database module. No new Gradle module or shared filesystem abstraction. The Android startup/recovery path, DataStore, features, UI, Wear, platform bindings, auth, backup and release configuration are outside the implementation PR. Keep `docs/index.md` and `docs/_config.yml` untouched.

Extend `.github/workflows/android_build_unified.yml` and `.github/scripts/assert_kmp_ios_smoke.py` in the current macOS job. Add `:core:data:database:iosSimulatorArm64Test`, `:core:data:database:linkDebugTestIosArm64`, fresh database XML with a start marker, and the path to the database device executable. Retain the nineteen previous modules, their exact expectations, Settings/root freshness checks, artifacts and stable check name. Update the old 19-module literals in `.github/scripts/assert_kmp_ui_source_topology.py` and check that the database row cannot be omitted. All twenty device binaries must be produced by this run and nonempty. Allow this spec's implementation evidence and the corresponding factual updates to `documentation/architecture.md`, `documentation/testing.md` and `documentation/ci-cd.md`.

## Verification and stop gates

**P0.** Start implementation only after the maintainer accepts the spec on `origin/dev` and issues GO for this scope. Use a clean worktree. Record the entry SHA, Xcode/simulator/runtime, JDK and Android device/API. Capture baseline Android database host/device identities, schema blobs and the existing Native expectations. Verify both Apple KSP tasks execute and produce fresh, nonempty actuals. First demonstrate a real query through the shipping module on the simulator and a device-target link. Stop before broadening scope if either target, driver or generator cannot work with the pinned versions.

**Behavioral negatives.** In an isolated, restorable worktree, omit `Migration7` from registration and require the named v6 upgrade case to fail on first access. Independently open a different file on the second construction and require the reopen case to fail on its sentinel. Each negative must compile and reach the intended assertion/Room failure on the simulator. Capture the exact failing identity and restore GREEN for each. Also prove the oracle rejects a missing database workflow row and missing, stale or incomplete database XML. Use synthetic XML only to test the validator.

**Commands.** Run load-bearing Gradle steps in Bash with the following flags. Prepare fresh result markers and clear/archive only owned build outputs before each measured run.

```bash
G=(--rerun-tasks --no-build-cache --no-configuration-cache --console=plain)
./gradlew :core:data:database:kspKotlinIosSimulatorArm64 :core:data:database:kspKotlinIosArm64 "${G[@]}"
./gradlew :core:data:database:iosSimulatorArm64Test "${G[@]}"
./gradlew :core:data:database:linkDebugTestIosArm64 "${G[@]}"
./gradlew assembleDebug lintDebug testDebugUnitTest :core:data:database:testAndroidHostTest :app:common:testAndroidHostTest "${G[@]}" --continue
./gradlew assembleDebugAndroidTest "${G[@]}" --continue
./gradlew verifyPaparazziDebug :lint-rules:test "${G[@]}" --continue
./gradlew detekt "${G[@]}" --continue
./gradlew connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.annotation=io.github.stslex.workeeper.core.ui.test.annotations.Smoke "${G[@]}" --continue
python3 .github/scripts/assert_mvi_device_identities.py
# Archive the Smoke XML before running Regression.
./gradlew connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.annotation=io.github.stslex.workeeper.core.ui.test.annotations.Regression "${G[@]}" --continue
```

Run both full 20-module Gradle task lists from the updated workflow with the same flags and `--continue`. Run the Native identity oracle after the simulator list and validate all 20 fresh `test.kexe` files after the link list. Execute the KMP UI topology, MVI source/host and personal-data checks. Verify the Android database module's actual instrumented migration, file round-trip, rollback and invalidation cases in fresh XML. Run `:core:data:database:connectedAndroidDeviceTest` with the Regression filter explicitly if the aggregate fails to prove them. Archive before reusing result paths.

Report each command, start time, exit status, executed tasks, test identities/counts/skips and artifact paths. Compare unchanged suites against fresh entry evidence. The 7.19B counts (948 Native cases, Smoke 44 with three known skips, Regression 90) are historical comparators. The database row adds eight required identities. Cached, skipped, empty or stale target results are not evidence. Neither a green topology check nor a linked device binary substitutes for execution. Stop on any file/schema/Android behavior drift, new suppression or baseline, golden movement, failed behavioral control or missing required runtime. Label device links as links and any physical-device/App Store checks as pending.

**Handoff.** Record reproducible evidence and residuals in the implementation PR/spec appendix. Keep the coding-agent prompt outside Git. Use signed Conventional Commits, verify every implementation commit on GitHub and obtain an independent review on the exact head. The implementation PR targets `dev`. The maintainer owns merge and the next scope decision.

## Implementation evidence

Entry is `origin/dev@46df486e4b2417264b128e849f488cedd504fc67`, the actual merge of
[specification PR #354](https://github.com/stslex/Workeeper/pull/354). The merge is reachable
from `origin/dev`; its specification blob and final reviewed head
`bfa31c7feecd14bd4f15e3ab7446513792a74712` both contain
`e3cf3c02b8a4b63c050d6ff283334a079925ef75`. Implementation uses the separate
`codex/kmp-7-20a-ios-room-database` worktree/branch. The implementation PR carries the exact
published head, every commit's GitHub signature verification and independent review provenance.

### Runtime and entry inventory

Local host: Apple silicon arm64, macOS 27.0 (`26A428`), Xcode 27.0 (`27A266a`), JBR 21.0.11.
Android: `Pixel_6_API_34`, `emulator-5554`, API 34, `arm64-v8a`. Apple simulator: newly created
`Workeeper-Phase-7.20A-20261102`, UUID `5EEB2D38-BC98-4945-95F8-B8B8C4F97A1E`, iOS 26.5.
The production default-path smoke runs only in that owned user container. Local gates ran
under a fresh recorded `caffeinate` process; continuation on battery was explicitly authorized.
CI retains its existing Xcode selection and provisions its own disposable database simulator.

`build/kmp-7-20a-evidence/entry.json` records the target, all seven schema blob/SHA-256 pairs
and the nineteen original Native expectation dictionaries. Every original expectation is retained.
The fresh legacy Native inventory was measured on a clean detached entry worktree, archived into
the implementation worktree, then the temporary worktree was removed without force. Android entry
inventories contain 166 database and 40 root host cases, 44 Smoke identities (the three existing
`pendingFeatureRewrite` skips), and 90 Regression identities including all 35 database cases.

### P0 and production boundary

Both Apple KSP tasks executed and emitted fresh, nonempty generated `AppDatabaseConstructor`
actuals (308 bytes each). Before expanding beyond the database module, the real bundled-driver
factory wrote/read through a DAO, closed/reopened its owned file on the simulator, and linked a
fresh iosArm64 debug test executable (9,209,016 bytes; Mach-O platform `IOS`). The initial simulator
attempt was a compile failure in Foundation interop spelling/imports; it is retained separately
and is not a behavioral negative. No pin, driver, schema, convention or additional production
module change was required.

`AppDatabaseBuilder.kt` owns the sole migration registration and post-commit reporting callback.
Android keeps its public entry, location, bundled driver, callback order, unset query-context
override and recovery/generation ownership. The Apple entry uses the actual user-domain
Application Support directory, `Workeeper/app.db`, off-Main filesystem work and an IO query context.
The module-local Apple driver dependency is the existing pinned alias. Source and runtime checks
cover the eight previously registered identities; the factory remains independent of Apple DI,
DataStore, shared UI, the Swift/Xcode host and iOS backup.

### Historical inputs and behavioral coverage

Pinned Native Room 3 `MigrationTestHelper` creates historical files from tracked JSON embedded
byte-for-byte by the test-only module generator. Raw SQLite observes the version, Room identity
and exact sentinel before the production builder opens each file. The archived pre-open copies
contain versions 5, 6 and the isolated future-version 8 refusal fixture, with their JSON and blob
IDs. The future-version fixture is seeded from v6 and then deliberately marked 8 solely for the
unsupported-version case.

| Tracked fixture export | Source Git blob | Pre-open Room identity |
| --- | --- | --- |
| `5.json` | `e767ebd355060e39d44cfc5a7ef8c858d0c1cb8c` | `b602a2bf8888dbd9c8880e7f9697be9f` |
| `6.json` | `1f624c48c88db5e40c284cc11211f3d46fdb9e68` | `9a848458f02ef4c4ace9841b9f2e3d42` |

The v6 fixture pins the Android reconciliation expectation: rowids 10/20 at position 0 and
rowid 30 at position 5 become positions 0/6/5 for their unchanged UUIDs. Row count, values, the
unique index, v7 identity and reopen are checked. The rollback case invokes the production runner,
observes both child writes inside its transaction, catches the same injected exception and retains
only the precommitted control. The invalidation case retains one collector across initial emission,
production-runner commit and the new emission. Public-path setup refuses any pre-existing default
directory and closes handles before removing only its newly owned directory.

### Gate receipts

Each directory below is under ignored `build/kmp-7-20a-evidence/`. Its receipt records the full
command, start/end UTC times, actual status, execution-only Gradle summary, test/skip counts and
XML paths; identities and copied XML are retained beside it. The entry instrumentation XML uses
explicit `device-xml` / `device-identities.json` supplements after the initial collector's host-only
path selection was corrected, before Regression reused the result paths. `clean` was preparation,
with byte-exact evidence backup/restoration, and is not reported as an executed green gate.

| Gate | Executed result | Receipt |
| --- | --- | --- |
| Entry database/root host | `499 actionable tasks: 499 executed`; 206 cases / 0 skips | `entry-host/receipt.json` |
| Entry Smoke | `2117 actionable tasks: 2117 executed`; 44 cases / 3 skips | `entry-smoke/receipt.json` |
| Entry Regression | `2117 actionable tasks: 2117 executed`; 90 cases / 0 skips | `entry-regression/receipt.json` |
| Entry 19-module Native | `554 actionable tasks: 554 executed`; 948 cases / 0 skips | `entry-native/receipt.json` |
| P0 Apple KSP pair | `11 actionable tasks: 11 executed`; 0 cases / 0 skips | `p0-ksp/receipt.json` |
| P0 real file reopen | `43 actionable tasks: 43 executed`; 1 cases / 0 skips | `p0-simulator-r2/receipt.json` |
| P0 device-target link | `42 actionable tasks: 42 executed`; 0 cases / 0 skips | `p0-device-link/receipt.json` |
| Eight database simulator cases | `44 actionable tasks: 44 executed`; 8 cases / 0 skips | `database-eight/receipt.json` |
| Database source analysis | `9 actionable tasks: 9 executed`; 0 cases / 0 skips | `database-detekt-r2/receipt.json` |
| Repository builds / Lint / host | `3061 actionable tasks: 3061 executed`; 3398 cases / 0 skips | `exit-build-lint-host/receipt.json` |

The eight-case Native XML has zero skips/failures/errors and every required tuple exactly once.
Synthetic validator controls accept complete twenty-module input, reject missing/stale/incomplete
database XML and reject a missing database workflow row; restoration passes. These synthetic files
prove the validator only. Logs are under `validator-controls-r2/`. The new CI row retains Settings
and root freshness checks; all twenty device executable paths require fresh, nonempty files.

### Exit validation

| Gate | Executed result | Receipt |
| --- | --- | --- |
| Repository device-test compilation | `2157 actionable tasks: 2157 executed`; 0 cases / 0 skips | `exit-device-compile/receipt.json` |
| Paparazzi verification and lint-rules tests | `638 actionable tasks: 638 executed`; 1938 cases / 0 skips | `exit-paparazzi-lint-rules/receipt.json` |
| Separate repository Detekt | `63 actionable tasks: 63 executed`; 0 cases / 0 skips | `exit-detekt/receipt.json` |
| Exit Smoke | `2117 actionable tasks: 2117 executed`; 44 cases / 3 skips | `exit-smoke/receipt.json` |
| Exit Regression | `2117 actionable tasks: 2117 executed`; 90 cases / 0 skips | `exit-regression/receipt.json` |
| All twenty simulator tasks and Native oracle | `560 actionable tasks: 560 executed`; 956 cases / 0 skips | `exit-native/receipt.json` |
| All twenty device-target links | `540 actionable tasks: 540 executed`; 0 cases / 0 skips | `exit-device-links/receipt.json` |

The 206 database/root host tuples and skip states equal entry exactly. Smoke retains all 44
identities and the same three skips; Regression retains all 90 identities, including every one
of the 35 database cases. Native retains all 948 entry tuples/skip states and adds exactly the
eight registered database cases. All twenty result sets are fresh. Every device executable is
fresh and nonempty; `binaries.json` records path, size, timestamp and SHA-256, and the database
binary's Mach-O platform is `IOS`.

The final source-topology, MVI source/host identity and personal-data gates pass. Their separate
`final-*` receipts record validator execution, with no test execution attributed to those commands.
The earlier parallel static receipts retain an interpretation note for unrelated concurrent XML
captured by the generic collector. The final collector restricts XML capture to test commands.
The mockup shell gate passes all eleven checks; its permanent `f52462c7` negative exits nonzero
and reproduces check 7's exact `width=0px→0px` discriminator. Strict raw logs and actual statuses
are under `mockup-controls-strict/`. No schema, golden, suppression or baseline changed.

### Remaining acceptance

Both independent behavioral mutation/restoration pairs and final independent head review are
pending in this ledger. Their measured outcomes will be recorded before handoff. Device-target
linking is not physical-device execution. A production Apple application/DI runtime, real iOS
backup and physical-device/App Store acceptance remain separate scopes; the maintainer owns merge.
