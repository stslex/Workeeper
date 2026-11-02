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
