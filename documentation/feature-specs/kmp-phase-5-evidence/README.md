# KMP Phase 5 (R2) — verification evidence

First measured 2026-08-22; re-measured after each rework — round 2 (spec §20/§21), round 3
(spec §22, the attempt journal + admission/snackbar linearization), and round 4 (spec §23, the
maintainer correction: crash-safe promotion/consumption, terminal classification, legacy owner
isolation, graph-only teardown terminality, replay-safe finalization). The tables below carry
the CURRENT (R4.2) numbers, with earlier figures noted where they differ.
Branch `feature/kmp-phase-5-startup-processor` (PR #252).

**Round-4 red-on-base evidence.** Every mandated round-4 test was proven to FAIL at the
pre-correction head `936ab699`: the new test files were copied onto a worktree at that commit
and the four suites run — **21 failures total (20 named tests + 1 parameterized invocation)**,
all green at the current head. Raw JUnit XMLs: `r4-red-on-base-*.xml` beside this file
(`<system-out>` stripped; testcases and failures verbatim).

**R4.2 red-on-base evidence.** All EIGHT production-shaped pins for the durable-phase
dispatch correction (spec §25) were proven to FAIL at `83793531` via the worktree recipe
(`r42-red-on-base-runtime.xml`) — the one-shot RESTORE proof after the adversarial review's
vacuity fix (§25.1). Known-negatives: the old pre-durable→committed dispatch
restored on the retry path kills the production-shaped pin
(`known-negative-r42-predurable-dispatch.xml`); the unbounded-clear mutant re-executed against
the rewritten QUEUEING liveness test hangs it
(`known-negative-r42-unbounded-clear-queued.xml`). Both reverted with clean diffs. The
zero-suppression claim is verified against the FULL range: `git diff -U0 936ab699..HEAD --
'*.kt'` adds no `@Suppress`.

**R4.1 red-on-base evidence.** The six R4.1 pins (spec §24) were proven to FAIL at `7c82c368`
the same way (`r41-red-on-base-runtime.xml` + `r41-red-on-base-coordinator.xml`); the
liveness pin's discriminator is the executed-and-reverted unbounded-clear mutant
(`known-negative-r41-unbounded-clear.xml` — the machine hangs to the test timeout under the
old `withContext(mainDispatcher)` implementation).

**Round-4 adversarial-review fix evidence.** The six pins added with `e9459025` (spec §23.5)
were each proven RED against the pre-fix production by stashing the production changes and
re-running the suites over the new tests — 6 tests red (`r4-review-red-prefix-runtime.xml` +
`r4-review-red-prefix-settings.xml`), green after the stash pop.
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
| §22.3b generation-deps seam (round 3) | `./gradlew :core:ui:mvi:connectedDebugAndroidTest` | 2/2 GREEN. `storeJobsAreDescendantsOfTheGenerationSuppliedByTheDepsSeam` composes the real `rememberMetroStoreProcessor` under a real `AppDepsHolder`, starts a job through the ordinary `launchDefault` surface with a DB-touching `finally`, and ends the lifetime the holder supplied — `cancelAndJoin` cannot return first. Proof boundary: it pins the job the SEAM supplies; the host `StoreGenerationJoinTest` still owns the `AppCoroutineScopeImpl` plus-order proof. Known-negative R3-D below. |
| §20.2 composed real-handshake gate | `./gradlew :app:app:connectedDebugAndroidTest -P…class=…AppRuntimeUiHandshakeDeviceTest` | 1/1 GREEN (`connected-ui-handshake-app-app.xml`): a REAL `AppRuntime` behind the app shell; graph-only reinitialize awaits the LIVE UI region's disposal, re-keys the root, carries the same DB, recreation restores only the successor. Known-negative (severed dispose callback in `TestApplication`): red with `Aborted("ui region did not dispose in time")`, outgoing generation kept serving — raw XML committed as `known-negative-ui-handshake-severed-dispose.xml`, mutation reverted. |

## Forced host battery (`--rerun-tasks --no-build-cache --no-configuration-cache`)

| Command | Exit | Executed | Tests |
|---|---|---|---|
| `./gradlew assembleDebug testDebugUnitTest verifyPaparazziDebug lintDebug assembleDebugAndroidTest --rerun-tasks --no-build-cache --no-configuration-cache` | 0 (`BUILD SUCCESSFUL in 9m 37s`, R4.2 final; R4.1 32m50s/2720; round-4 15m31s/2712; round-3 8m29s/2688; round-2 9m10s/2648; round-1 9m06s/2609; pre-rework 7m48s/2461) | 3265/3265 tasks executed; `verifyPaparazziDebug` in the graph with zero movers (goldens untouched by every round; 13 golden-holding modules) | **2725 unit/host tests, 0 failures, 0 errors** (2209 `testDebugUnitTest` + 389 `testAndroidHostTest` + 127 `test`, counted from the raw JUnit XMLs across all three host scopes) |
| `./gradlew detekt :lint-rules:test --rerun-tasks --no-build-cache --no-configuration-cache` | 0 (R4.2 forced re-run) | **zero new suppressions** — the full diff (`git diff 936ab699..HEAD`) adds no `@Suppress`, and no detekt config or baseline file is touched (verified by grep, not just the exit code) | 127 custom-rule tests, 0 failures |
| `./gradlew :core:data:backup:api:compileKotlinIosSimulatorArm64 :core:core:compileKotlinIosSimulatorArm64 --rerun-tasks …` | 0 (10/10 executed, R4.2 re-forced) | the KMP modules the reworks touched. The full five-module + Room-KSP sweep stands from round 2 | — |

iOS **link/runtime**: UNVERIFIED — no iOS host exists before Phase 7; compile+KSP evidence only.

## Connected suites (the `ui_tests.yml` invocations)

| Command | Exit | Tests |
|---|---|---|
| `ANDROID_SERIAL=emulator-5554 ./gradlew connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.annotation=io.github.stslex.workeeper.core.ui.test.annotations.Smoke --max-workers=2 --continue` | 0 (R4.2 final 44/0; R4.1 44/0; round-4 3m02s/44; round-3 2m39s/44; round-2 2m41s/43) | **44** started, 0 failures (+1 over round 2: the generation-deps seam pin). Its FIRST round-3 run was RED and load-bearing — see §22.3b: `:core:ui:mvi`'s probe builds a Store with no app graph, and the processor now requires one app-scope binding, so the probe's plain `Application` failed the `appDeps` cast. Fixed by supplying the binding as production does, never by softening the seam |
| same with `…annotations.Regression` | 0 (R4.2 final 81/0; R4.1 81/0; round-4 4m06s/81; round-3 3m21s/81; round-2 3m19s/78) | **81** started, 0 failures (49 `app:app` — the composed handshake test plus round-3's three device pins — + 30 `core:data:database` + 2 others; raw XMLs beside this file) |
| `:core:data:database:connectedDebugAndroidTest -P…class=…SameInstanceReopenAfterSwapDeviceTest` | 0 (R4.2 re-run) | **2/2** — the Room same-instance characterization is UNCHANGED in what it proves |

Standalone runs (same emulator): full unfiltered `:app:app:connectedDebugAndroidTest` 49/0 and
`:core:data:database:connectedDebugAndroidTest` 30/0 (round 3);
`:core:data:database:testDebugUnitTest` host 128/128.

The whole 46 → 49 delta is `UiAdmissionRaceTest` (added in `66cc5cc7`): admission is taken during
COMPOSITION and its three cases prove an admitted region resolves its dependencies, a retired one
resolves nothing, and retirement landing between publication and frame resolves nothing. Its
boundary: it runs against the harness's `retiredIds` set, so it pins the SHELL's use of the gate;
the gate's own atomicity is pinned on the host by `UiAdmissionGateTest` and by known-negative
R3-C, and the abandoned-composition half of the leak-freedom is covered by construction only
(spec §22.4).

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
| R3-C — `awaitRetired` split into a two-step observe-then-retire (the non-atomic form blocker 4 exists to rule out) | `retire is ATOMIC with the zero observation - a grant and a clear verdict are exclusive`, failing at hammer iteration 29 with a token granted for a generation the gate had already reported clear | `known-negative-r3c-two-step-retire.xml` |
| R3-D — `rememberStoreProcessor` resolves `remember { null }` instead of the deps seam's generation job (compiles; un-parents every Store job) | `storeJobsAreDescendantsOfTheGenerationSuppliedByTheDepsSeam` (device), on "cancelAndJoin returned while a Store job's finally had not run". `appFeatureProcessorResolvesAtActivityScope` stays GREEN under it — which is why this gap outlived the first review pass | `known-negative-r3d-unparented-store-jobs.xml` |

Six further mutations were run by the test authors and each was killed by its own new pin:
plus-order reversal in `AppCoroutineScopeImpl` (Store `finally` still pending when
`cancelAndJoin` returned), non-idempotent `dispose()`, a re-stamping `requeue`, an always-admit
`beginResolve`, a non-awaiting `fenceResolves`, and a collapsed resolve counter.

## Round-4 known-negatives (executed and reverted per the §7 protocol)

| Mutation | Red pins | Raw XML |
|---|---|---|
| R4-A — the explicit-path→canonical fallback re-enabled in `selectRollbackOperationSource` (a missing journal-named source silently substitutes another attempt's canonical slot) | `a MISSING journal-named rollback source is a typed rejection - the canonical slot is never substituted` | `known-negative-r4a-canonical-substitution.xml` |
| R4-B — "publish the candidate anyway" restored after a failed graph-only post-PONR teardown | `outgoing ViewModelStore clear failure after PONR is FATAL - no successor is published` AND `unjoinable outgoing lifetime after PONR is FATAL - epoch unchanged, admission refused` | `known-negative-r4b-publish-anyway.xml` |
| R4-C — the invalid safe-retry classification restored (a rejected recovery rollback continues the launch and arms) | `every non-commit rollback outcome requires terminal recovery` (`RejectedBeforeMutation` case) | `known-negative-r4c-safe-retry.xml` |

R3-C is the one that had to be earned twice. The hammer as first written could not observe its
own gap — its racer released whatever token it won before the assertion read the count, so the
two-step mutant passed 3000/3000. The racer now KEEPS its grant and the assertion is stated as
the exclusion it means ("a token exists for an id the gate reported clear"), which is what makes
the mutation die. Two further pins of the round-3 work were likewise non-discriminating when the
adversarial review reached them: the generation-job seam had no test at all (a one-token revert
at `StoreProcessor.kt` restored the defect with the suite green — `BaseStore.init`'s
`generationJob` parameter is now REQUIRED, so that revert is a compile error), and
`commitMutation`'s promote-before-record ordering was unpinned (now order-recorded).

## Reproduction

Every command above is copy-paste reproducible from the branch root with a booted API-34
emulator. The three known-negative mutations (§7.1 swap-real, §11.2 swap bypass, §20.2 severed
dispose callback) are documented inline in the respective test KDocs (§7 protocol) and in the
PR history — they are executed-and-reverted, never committed; the severed-dispose failure's raw
XML is committed beside this file.
