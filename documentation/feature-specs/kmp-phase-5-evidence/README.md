# KMP Phase 5 (R2) — verification evidence

Measured on 2026-08-22, branch `feature/kmp-phase-5-startup-processor` (PR #252).
Device for every connected run: `sdk_gphone64_arm64` emulator (Pixel 6 AVD), API 34, arm64-v8a,
`ANDROID_SERIAL=emulator-5554`. Host: macOS (Darwin 25.6), JDK 21, Room 3.0.0,
`BundledSQLiteDriver`. The `.xml` files beside this README are the raw AGP instrumentation
results of the final Regression run, copied verbatim from
`build/outputs/androidTest-results/connected/`.

## Device gates

| Gate | Command | Result |
|---|---|---|
| §7.1 characterization (RED gate → permanent pin) | `./gradlew :core:data:database:connectedDebugAndroidTest -P…class=…SameInstanceReopenAfterSwapDeviceTest` | Gate-form run: exit 1, both branches `SQLException code 21 "Connection pool is closed"`; committed pin form 2/2 GREEN (swap-real disk truth + inode change + fail-loud + green-flip tripwire). Known-negative (swap bypass): red at the inode assertion, reverted. |
| §11.2 per-generation GREEN gate | `./gradlew :app:app:connectedDebugAndroidTest -P…class=…RuntimeGenerationSwapDeviceTest` | 1/1 GREEN (restore → rollback, three generations, full production factory + real graph + real preflight). Known-negative (graph-only transition instead of swap): red at "restore must install a new inode (336971→336971)", reverted. |

## Forced host battery (`--rerun-tasks --no-build-cache --no-configuration-cache`)

| Command | Exit | Executed | Tests |
|---|---|---|---|
| `./gradlew assembleDebug testDebugUnitTest verifyPaparazziDebug lintDebug assembleDebugAndroidTest --rerun-tasks --no-build-cache --no-configuration-cache --full-stacktrace` | 0 (`BUILD SUCCESSFUL in 7m 48s`) | 4030 task lines; 13/13 golden-holding modules ran `verifyPaparazziDebug` (zero movers — goldens unchanged; the often-cited 14th, `core:ui:golden-harness`, references golden-gate in a comment only and hosts no goldens) | 2461 unit/host tests, 0 failures (271 result files) |
| `./gradlew detekt --rerun-tasks --no-build-cache --no-configuration-cache` | 0 | separate forced run | — |
| `./gradlew :lint-rules:test --rerun-tasks …` | 0 | forced | — |
| `./gradlew :core:core:compileKotlinIosSimulatorArm64 :core:data:dataStore:… :core:data:database:… :core:data:database:kspKotlinIosSimulatorArm64 :core:data:exercise:… :core:data:backup:api:… --rerun-tasks …` | 0 | all five KMP modules' iOS klibs + Room KSP, forced | — |

iOS **link/runtime**: UNVERIFIED — no iOS host exists before Phase 7; compile+KSP evidence only.

## Connected suites (the `ui_tests.yml` invocations)

| Command | Exit | Tests |
|---|---|---|
| `ANDROID_SERIAL=emulator-5554 ./gradlew connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.annotation=io.github.stslex.workeeper.core.ui.test.annotations.Smoke --max-workers=2 --full-stacktrace --continue` | 0 (`BUILD SUCCESSFUL in 3m 7s`) | 43 started, 0 failures |
| same with `…annotations.Regression` | 0 (`BUILD SUCCESSFUL in 3m 9s`) | 77 started, 0 failures (45 `app:app` + 30 `core:data:database` + 2 others — raw XMLs beside this file) |

Standalone earlier runs during development (same emulator): `app:app` Regression 42/42 pre-suite
and 45/45 after the three Phase 5 suites landed; `core:data:database` 30/30;
`:core:data:database:testDebugUnitTest` host 128/128.

## Reproduction

Every command above is copy-paste reproducible from the branch root with a booted API-34
emulator. The two known-negative mutations are documented inline in the respective test KDocs
(§7 protocol) and in the PR history — they are executed-and-reverted, never committed.
