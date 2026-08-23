# KMP Phase 5 (R2) — verification evidence

First measured 2026-08-22; re-measured 2026-08-23 after the REQUEST_CHANGES rework (spec §20) —
the tables below carry the CURRENT (2026-08-23) numbers, with the pre-rework figures noted where
they differ. Branch `feature/kmp-phase-5-startup-processor` (PR #252).
Device for every connected run: `sdk_gphone64_arm64` emulator (Pixel 6 AVD), API 34, arm64-v8a,
`ANDROID_SERIAL=emulator-5554`. Host: macOS (Darwin 25.6), JDK 21, Room 3.0.0,
`BundledSQLiteDriver`. The `.xml` files beside this README are the raw AGP instrumentation
results of the final Regression run, copied verbatim from
`build/outputs/androidTest-results/connected/`.

## Device gates

| Gate | Command | Result |
|---|---|---|
| §7.1 characterization (RED gate → permanent pin) | `./gradlew :core:data:database:connectedDebugAndroidTest -P…class=…SameInstanceReopenAfterSwapDeviceTest` | Gate-form run: exit 1, both branches `SQLException code 21 "Connection pool is closed"`; committed pin form 2/2 GREEN (swap-real disk truth + inode change + fail-loud + green-flip tripwire). Known-negative (swap bypass): red at the inode assertion, reverted. |
| §11.2 per-generation GREEN gate | `./gradlew :app:app:connectedDebugAndroidTest -P…class=…RuntimeGenerationSwapDeviceTest` | 1/1 GREEN (restore → rollback, three generations, full production factory + real graph + real preflight). Known-negative (graph-only transition instead of swap): red at "restore must install a new inode (336971→336971)", reverted. Claim boundary (§20.2): inode/close/fresh-Room proof over EMPTY quiesce populations — the UI handshake is the next row's claim. |
| §20.2 composed real-handshake gate | `./gradlew :app:app:connectedDebugAndroidTest -P…class=…AppRuntimeUiHandshakeDeviceTest` | 1/1 GREEN (`connected-ui-handshake-app-app.xml`): a REAL `AppRuntime` behind the app shell; graph-only reinitialize awaits the LIVE UI region's disposal, re-keys the root, carries the same DB, recreation restores only the successor. Known-negative (severed dispose callback in `TestApplication`): red with `Aborted("ui region did not dispose in time")`, outgoing generation kept serving — raw XML committed as `known-negative-ui-handshake-severed-dispose.xml`, mutation reverted. |

## Forced host battery (`--rerun-tasks --no-build-cache --no-configuration-cache`)

| Command | Exit | Executed | Tests |
|---|---|---|---|
| `./gradlew assembleDebug testDebugUnitTest verifyPaparazziDebug lintDebug assembleDebugAndroidTest --rerun-tasks --no-build-cache --no-configuration-cache` | 0 (`BUILD SUCCESSFUL in 9m 10s`, 2026-08-23 round-2 final; round-1 9m06s/2609; pre-rework 7m48s/2461) | 3265/3265 tasks executed; `verifyPaparazziDebug` in the graph with zero movers (goldens untouched by the rework; 13 golden-holding modules per the 08-22 run) | **2648 unit/host tests, 0 failures** |
| `./gradlew detekt --rerun-tasks --no-build-cache --no-configuration-cache` | 0 | separate forced run (re-run 2026-08-23; zero new suppressions) | — |
| `./gradlew :lint-rules:test --rerun-tasks …` | 0 | forced (re-run 2026-08-23) | — |
| `./gradlew :core:core:compileKotlinIosSimulatorArm64 :core:data:dataStore:… :core:data:database:… :core:data:database:kspKotlinIosSimulatorArm64 :core:data:exercise:… :core:data:backup:api:… --rerun-tasks …` | 0 | all five KMP modules' iOS klibs + Room KSP, forced; `:core:data:backup:api` (the one KMP module the rework touched — androidMain seam) re-forced 2026-08-23, exit 0 | — |

iOS **link/runtime**: UNVERIFIED — no iOS host exists before Phase 7; compile+KSP evidence only.

## Connected suites (the `ui_tests.yml` invocations)

| Command | Exit | Tests |
|---|---|---|
| `ANDROID_SERIAL=emulator-5554 ./gradlew connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.annotation=io.github.stslex.workeeper.core.ui.test.annotations.Smoke --max-workers=2 --continue` | 0 (`BUILD SUCCESSFUL in 2m 41s`, 2026-08-23 round-2 final) | 43 started, 0 failures |
| same with `…annotations.Regression` | 0 (`BUILD SUCCESSFUL in 3m 19s`, 2026-08-23 round-2 final, re-run on the hardened protocol) | **78** started, 0 failures (46 `app:app` — the composed handshake test included — + 30 `core:data:database` + 2 others; raw XMLs beside this file) |

Standalone runs (same emulator): full unfiltered `:app:app:connectedDebugAndroidTest` 46/0 and
`:core:data:database:connectedDebugAndroidTest` 30/0 (2026-08-23);
`:core:data:database:testDebugUnitTest` host 128/128.

## Round-2 known-negatives (2026-08-23, executed and reverted per the §7 protocol)

| Mutation | Red pins | Raw XML |
|---|---|---|
| N1 — `completedOrRecovered` forced to always return `Completed` (result-truth lie) | 4 tests: both `RecoveredByRollback, never Completed` pins, the inline-S1-rollback outer-result pin, and the composed integration gate's "no success lie" | `known-negative-n1-result-truth.xml` |
| N2 — outgoing close-throw handled as round-1 `RejectedBeforeMutation` (cleanup-safe rejection) instead of Fatal | 5 tests: the close-throw-Fatal pin plus every downstream Fatal-terminality pin (A-Fatal-while-B-queued, replace-after-Fatal, reinitialize-after-Fatal, Fatal-holder rejection) | `known-negative-n2-close-throw-fatal.xml` |
| N3 — the snackbar delivery epoch filter disabled (stale callbacks delivered into N+1) | 2 kit tests: discarded-at-delivery and exactly-once re-enqueue | `known-negative-n3-epoch-filter.xml` |

Each mutation was applied to the MAIN source, the pinned suites ran RED (XMLs beside this
file), and the mutation was reverted; the reverted tree re-ran green.

## Round-3 known-negatives (2026-08-23, executed and reverted per the §7 protocol)

| Mutation | Red pins | Raw XML |
|---|---|---|
| R3-A — the cold-start branch ignores the attempt PHASE, so a `Prepared` attempt takes the schema-peek path | 7 coordinator tests, headed by `a PREPARED attempt never peeks the schema and never claims success` failing on `currentSchemaVersion() should not be called` — i.e. the false `RestoreSuccess` reproduced on demand | `known-negative-r3a-prepared-bypass.xml` |
| R3-B — the successor is published BEFORE the snackbar epoch advances | `the snackbar epoch advances BEFORE the successor is published` | `known-negative-r3b-publish-before-epoch.xml` |

Six further mutations were run by the test authors and each was killed by its own new pin:
plus-order reversal in `AppCoroutineScopeImpl` (Store `finally` still pending when
`cancelAndJoin` returned), non-idempotent `dispose()`, a re-stamping `requeue`, an always-admit
`beginResolve`, a non-awaiting `fenceResolves`, and a collapsed resolve counter.

## Reproduction

Every command above is copy-paste reproducible from the branch root with a booted API-34
emulator. The three known-negative mutations (§7.1 swap-real, §11.2 swap bypass, §20.2 severed
dispose callback) are documented inline in the respective test KDocs (§7 protocol) and in the
PR history — they are executed-and-reverted, never committed; the severed-dispose failure's raw
XML is committed beside this file.
