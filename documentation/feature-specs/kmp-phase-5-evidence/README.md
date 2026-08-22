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
| `./gradlew assembleDebug testDebugUnitTest verifyPaparazziDebug lintDebug assembleDebugAndroidTest --rerun-tasks --no-build-cache --no-configuration-cache` | 0 (`BUILD SUCCESSFUL in 9m 6s`, 2026-08-23 post-rework; pre-rework 7m48s) | 3265/3265 tasks executed; `verifyPaparazziDebug` in the graph with zero movers (goldens untouched by the rework; 13 golden-holding modules per the 08-22 run) | **2609 unit/host tests, 0 failures** (pre-rework: 2461 — the delta is the rework's new/rewritten suites) |
| `./gradlew detekt --rerun-tasks --no-build-cache --no-configuration-cache` | 0 | separate forced run (re-run 2026-08-23; zero new suppressions) | — |
| `./gradlew :lint-rules:test --rerun-tasks …` | 0 | forced (re-run 2026-08-23) | — |
| `./gradlew :core:core:compileKotlinIosSimulatorArm64 :core:data:dataStore:… :core:data:database:… :core:data:database:kspKotlinIosSimulatorArm64 :core:data:exercise:… :core:data:backup:api:… --rerun-tasks …` | 0 | all five KMP modules' iOS klibs + Room KSP, forced; `:core:data:backup:api` (the one KMP module the rework touched — androidMain seam) re-forced 2026-08-23, exit 0 | — |

iOS **link/runtime**: UNVERIFIED — no iOS host exists before Phase 7; compile+KSP evidence only.

## Connected suites (the `ui_tests.yml` invocations)

| Command | Exit | Tests |
|---|---|---|
| `ANDROID_SERIAL=emulator-5554 ./gradlew connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.annotation=io.github.stslex.workeeper.core.ui.test.annotations.Smoke --max-workers=2 --continue` | 0 (`BUILD SUCCESSFUL in 2m 38s`, 2026-08-23) | 43 started, 0 failures |
| same with `…annotations.Regression` | 0 (`BUILD SUCCESSFUL in 3m 18s`, 2026-08-23) | **78** started, 0 failures (46 `app:app` — the composed handshake test joined the suite — + 30 `core:data:database` + 2 others; raw XMLs beside this file. Pre-rework: 77) |

Standalone runs (same emulator): full unfiltered `:app:app:connectedDebugAndroidTest` 46/0 and
`:core:data:database:connectedDebugAndroidTest` 30/0 (2026-08-23);
`:core:data:database:testDebugUnitTest` host 128/128.

## Reproduction

Every command above is copy-paste reproducible from the branch root with a booted API-34
emulator. The three known-negative mutations (§7.1 swap-real, §11.2 swap bypass, §20.2 severed
dispose callback) are documented inline in the respective test KDocs (§7 protocol) and in the
PR history — they are executed-and-reverted, never committed; the severed-dispose failure's raw
XML is committed beside this file.
