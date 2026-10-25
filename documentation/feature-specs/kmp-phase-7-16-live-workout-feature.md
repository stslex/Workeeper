# KMP Phase 7.16 — feature:live-workout. Part A: the launch failure seam (PR-L)

**Status:** Part A COMPLETE — implemented by the PR whose merge brought this line to dev.
Parts B (PR-P) and C (PR-M) are specified by amendment.

**Discovery baseline:** dev @ 1b96184125d0bbb621b38a5485ba00fe558857db (2026-10-25, the merge of
#343, the D-01 fix after Phase 7.15).

**Decisions:** maintainer GO, 2026-10-25, on D1–D8 of the Phase 7.16 discovery, each on its lean
(Section 2).

---

## 0. Authority and stable rules

### 0.1 Authority order

1. live origin/dev at the implementation base;
2. AGENTS.md, CLAUDE.md, documentation/architecture.md, testing.md, ci-cd.md, lint-rules.md;
3. this document;
4. the checked-in source, tests and CI oracles at the implementation base.

Phase 7.15's specification (`kmp-phase-7-15-exercise-feature.md`, its Section B19 included) is the
precedent this part cites. tech-debt.md's section "The default flow `onError` is silent" (with its
2026-10-20 correction) is the defect this part resolves.

### 0.2 Stable rules

- The work happens in the main checkout, on branches. No `git worktree` is created for any step:
  not for the spec, the implementation, a proof or a control (maintainer rule, 2026-10-20).
  Mutations go through `documentation/mockups/mutation_harness.py`, which restores the file;
  anything the harness cannot run uses a scratchpad copy restored with `cp` (AGENTS.md).
- Every load-bearing Gradle invocation runs after `./gradlew --stop` with
  `--rerun-tasks --no-build-cache --no-configuration-cache --console=plain` and quotes its
  `N actionable tasks: N executed` line. detekt and tests run as separate invocations.
- Only JUnit XML written after the run's recorded start is evidence. Every gate reports its input
  count; a walk over zero items is a failure, not a pass.
- Commits are signed and GitHub-verified; the only trailer is `Co-Authored-By`; no session
  identifier appears in a commit, the PR or a document. The implementer never merges.
- No suppression of any kind. No golden is recorded and no PNG blob changes. No catalog
  (`strings.xml`), build file, workflow or version catalog changes.
- One session does all the work. Every census, transcription and audit is a script that session
  writes and runs, with its own parser. None is a separate agent.

## 1. Why

Every Store launch goes through `AppCoroutineScopeImpl` (core:core commonMain), and at the
baseline both of its `launch` overloads hand their main failure to `onError` and record nothing:

- the action overload runs `runCatching { action() }`, then `onSuccess` or `onError`, each inside
  `withContext(eachDispatcher)`;
- the flow overload is `.catch { onError(it) }`;
- `onError` defaults to `{}` on all three declaring interfaces (`AppCoroutineScope`,
  `StoreConsumer`, `HandlerStore`).

So with the default `onError` a throw from an action or a flow leaves no trace: nothing in logcat,
nothing in Crashlytics, no observable in a test. The scope's `CoroutineExceptionHandler` calls
`Log.e(throwable)` on the companion (tag `Workeeper`), but it only sees what escapes the
`runCatching` block: a throw from `onSuccess`, from `onError`, or the failure of an `async` child
that cancels the launch (architecture.md's backstop paragraph).

Census on the base (method: Section 1.1):

| Overload | Calls | Default `onError` |
| --- | ---: | ---: |
| `launch(action)` | 62 | 35 |
| `launchDefault` | 19 | 3 |
| `Flow.launch` | 22 | 21 |

The 35 by module: single-training 10, exercise 9, archive 4, home 3, live-workout 3,
all-exercises 2, settings 2, all-trainings 1, and `BaseStore.sendEventWithAwait`. The 3
`launchDefault` defaults are settings' `BackupClickHandler`. The 22 flow calls are 21 in features
and 1 in app/wear; the one explicit `onError` is past-session's.

Where a failure is recorded today, the call site records it. Fourteen launches do, in ten places:

- nine `logger.e(…)` calls inside an explicit `onError` lambda: app-dialogs'
  `AppDialogRepoHandler` (2) and `ChooseHandler` (1), settings' `BackupClickHandler` (5) and
  live-workout's `PendingUndoOps` (1);
- `BackupClickHandler.emitUnknownErrorAndIdle(e)`, which calls `logger.e(e, …)` and is the whole
  `onError` of five more launches (lines 172, 216, 330, 349 and 377).

The call-site logger is the handler store's, which is the Store's `SCREEN_<name>` logger.

Phase 7.14's review placed this seam before live-workout, whose launches resolve copy and catch
watch writes (7.14 Section 19.8, 7.15 B17).

### 1.1 Census method

A source scan of every production source set (`main`, `commonMain`, `androidMain`, `iosMain`), with
comments and string literals blanked and each call's receiver and named arguments parsed.

- Excluded: kotlinx launches (`lifecycleScope`, `hostScope`, the Wear coordinators, exercise-chart's
  composition scopes, live-workout's external-writes child) and ActivityResult `.launch(…)`.
- `BaseStore`'s and `BaseHandlerStore`'s forwarders are not call sites.
- On `63d511de` the same classification gives 60 / 33 for the action overload, with tech-debt.md's
  per-module split exactly. tech-debt.md's bytecode census came from `javap`.

One count does not reconcile from source: tech-debt.md counts 18 `launchDefault` calls, 14 of them
passing every argument. Source has 19, and 15 pass both `onError` and `onSuccess` (app-dialogs 3,
exercise-chart 2, settings 10). The default count, 3, agrees. The entry census repeats the scan and
reports this row as measured; it is not a STOP.

### 1.2 Facts this part relies on (verified at `1b9618412`)

- **The construction site.** `AppCoroutineScopeImpl` is constructed exactly once in the repository,
  in `BaseStore.init`. No test constructs it.
  - `MviJvmAbiTest.lifetimeConstructorDeltasAreRequiredAndHaveNoDefaults` pins its constructor:
    the generation `Job` is the last parameter, and there is no synthetic default constructor.
  - core:core is outside core:ui:mvi's ABI manifest (`mvi-public-jvm-abi.txt` covers the module
    that compiled `BaseStore`).
- **The Store's logger.** `BaseStore.logger` is `loggerHolder.create(name)`, which is
  `Log.tag("SCREEN_$name")`.
  - `Log.e(throwable, message)` calls `FirebaseCrashlyticsHolder.recordException(throwable, tag)`
    before the `isLogging` gate. The message stays local (`LogTelemetrySinkTest`).
  - On iOS the holder is a no-op.
  - On Android `recordException` debounces on the throwable's message and clears its filter after
    every record, so two records of one failure both reach Crashlytics.
- **Cancellation at the base.**
  - `runCatching` catches a cancelled coroutine's `CancellationException`. `withContext` then checks
    the new context's job before `onError` would run, so `onError` never runs for the coroutine's
    own cancellation.
  - `Flow.catch` rethrows the collector's own cancellation and catches any other
    `CancellationException`.
  - live-workout's session timer (`launch { while (isActive) { …; delay(1000) } }`, default
    `onError`) is cancelled on every screen exit and every `Init`, so a record placed before that
    check would fire on every exit.
- **No expected failure is thrown.**
  - Expected outcomes travel as results: `SaveOutcome`, `BackupResult.Failure`,
    `SignInOutcomeDomain`.
  - Of the 44 explicit `onError` lambdas in feature and app/wear production code, one branches on
    the throwable's type: past-session's `InputHandler`, on `CancellationException`, which
    `withContext` already keeps from reaching it.
  - No Store launch action calls `withTimeout`. The two `withTimeoutOrNull` calls (Wear transport,
    wear-bridge RPC) are not Store launches, and they return null rather than throw.
- **What a record carries.** A record carries the throwable (with its message) and the tag. A scan
  of `throw …(`, `error(`, `require(`, `check(` and their `NotNull` forms in production found 8
  messages that interpolate a value. They carry file paths, the package name, recovery-file names
  and an attempt id, never a user-entered value.
- **The JVM host.** The plain-JVM host tests that drive a real `BaseStore` set
  `Log.isLogging = false` (kermit's Logcat writer fails off-device; testing.md "Off-device
  hazards"). Robolectric suites may keep it on. A plain-JVM test that drove a real Store into a
  launch failure with logging on would newly throw from the record; Section 7.4's whole-repository
  comparison is what finds one.
- **Which gates pin core:ui:mvi.**
  - `assert_mvi_source_topology.py` pins core:ui:mvi's exact Kotlin manifest per source set (at the
    base, commonTest 3 files and androidHostTest 3).
  - `assert_mvi_host_identities.py` requires 10 exact Android-host identities.
  - `assert_kmp_ios_smoke.py` requires 5 core:ui:mvi Native identities.
  - The unified workflow runs `:core:ui:mvi:testAndroidHostTest` forced and
    `:core:ui:mvi:iosSimulatorArm64Test` in its fifteen-module Native command.
  - core:core has no commonTest and no Native test task in CI.
- **Test counts.** core:ui:mvi has 13 commonTest identities (NavigationResultContractTest 7,
  StoreEventPressureTest 1, StoreGenerationJoinTest 5), 15 androidHostTest identities
  (MviJvmAbiTest 6, StoreTelemetryRedactionTest 2, AndroidPerformanceProviderTest 7) and 1 iosTest
  identity. That makes 28 host cases and 14 Native cases.
- **The recording fake's surface.** `Logger` has 13 abstract members and 3 members with default
  bodies.

## 2. Decisions

Locked by the maintainer on 2026-10-25, each on its lean:

| Id | Decision | Part |
| --- | --- | --- |
| D1 | One specification, three PRs. PR-L is the seam (this part). PR-P is live-workout's copy restructure, in the classic module, with a temporary verbatim reader on the app `Context` that PR-M's move commit deletes, plus an owner device check. PR-M is the move. | A, B, C |
| D2 | Every failure that is not the coroutine's own cancellation is recorded in both overloads, through the injected logger, before `onError`. The call-site records go, so each failure is recorded once. | A |
| D3 | `logger: Logger` is a required `AppCoroutineScopeImpl` parameter before `generationJob`. `BaseStore` passes its `SCREEN_<name>` logger, and no StoreImpl changes. | A |
| D4 | Only the collector's own cancellation is exempt. A `CancellationException` raised while the coroutine is active (a timeout) is a failure, in both overloads alike. | A |
| D5 | live-workout's copy resolves once per Store at `Init` into State. Templates are filled synchronously by a pure filler in kit. | B |
| D6 | Every copy read outside a launch comes from D5's copy. | B |
| D7 | An additive `state_mutators` gate field, default empty. | B, C |
| D8 | CommonHandlerExternalWrites goes to commonTest, CommonHandlerComposeResource is retired, and LiveSetRowSemantics goes into the iOS scene. | C |

### 2.1 Choices this part makes inside D2–D4

| Id | Choice | Why |
| --- | --- | --- |
| C1 | The record is `logger.e(<failure>)` with no message. | Crashlytics never receives `e()`'s message (`LogTelemetrySinkTest`), and kermit prints the throwable's own message when none is given. |
| C2 | The action overload's own-cancellation check is `ensureActive()`, first in `onFailure`, before the record. It resolves to the block's own `CoroutineScope` (the innermost implicit receiver), not to the Store scope the class also is. | It is the predicate `withContext` applies today before `onError`, made explicit and moved ahead of the record. Checking the launch rather than the Store matters because a launch is often cancelled alone, as live-workout's timer is on every `Init`; identity 2 cancels one launch while the scope stays active. The check also keeps an `async` child's failure from being recorded twice: the child cancels the launch, so the check rethrows and the backstop records it once. |
| C3 | The flow overload records inside its existing `catch`. | `Flow.catch` already applies D4's rule. |
| C4 | The `CoroutineExceptionHandler` records through the same logger. Its re-run of `onError` on the work dispatcher stays. | One tag per Store, and only the recording changes. |
| C5 | The new suites live in core:ui:mvi: a commonTest suite that builds the scope directly, and an androidHostTest suite that drives a real `BaseStore`. | core:ui:mvi already runs on the JVM host and on Native in CI, and core:core has no commonTest or Native task. Placing them here adds no workflow change. |
| C6 | The three CI scripts that pin core:ui:mvi pin the new suites. | So a missing or renamed identity fails CI on both platforms. |
| C7 | At the call sites only the record goes. A lambda that only logs is removed with its now-redundant `onSuccess = { }`, and an unused throwable parameter is dropped. | D2. |
| C8 | Nothing else in the launch contract changes. That covers the dispatchers, the backstop's path, the flow `each` path (a throw from `each` is downstream of the catch and escapes as uncaught), the `exceptionHandler` parameter (no caller) and the KDoc of `StoreConsumer` / `HandlerStore`. | Out of scope; recorded as backlog. |
| C9 | No logging failure is guarded. | `Log`'s sinks do not throw on a device; on the JVM host the `isLogging` rule already applies (testing.md). |

### 2.2 Corrections to the discovery

- **X1.** The call-site records are 14, in ten places, not nine. `emitUnknownErrorAndIdle` logs on
  behalf of five launches. D2 applies to all 14.
- **X2.** SL1 gains an identity: an `async` child's failure is recorded once, through the backstop
  (C2). The discovery's "an explicit `onError` still runs exactly once" is folded into every
  identity's exact event list.
- **X3.** The boundary names the three CI scripts of C6; the discovery's sketch did not.
- **X4.** testing.md says the MVI host gate requires seven identities. It requires ten at the base
  (drift since Phase 7.3), and commit 3 states the new count.

## 3. Bounded exit claim

The implementation may prove only this:

- the action overload records a failure of `action` once, before `onError` receives it, through
  the logger it was constructed with;
- the flow overload records an upstream failure once, before `onError` receives it;
- the coroutine's own cancellation is neither recorded nor handed to `onError`, in both overloads;
- a `CancellationException` raised inside an active coroutine is recorded and handed to `onError`,
  in both overloads;
- a failure that reaches the backstop (an `async` child's, an `onSuccess` throw) is recorded once,
  and `onError` then runs once on the work dispatcher;
- a launch that succeeds records nothing;
- a real `BaseStore` records a launch failure once, under `SCREEN_<name>`;
- no production `onError` lambda, and no function one calls, logs the failure it handles;
- every host case outside core:ui:mvi keeps its count and passes, and core:ui:mvi gains exactly the
  10 identities of Section 5;
- the device suites of Section 7.5 pass as they did.

Not claimed: Crashlytics volume in release (Section 12.2); iOS crash reporting (the holder is a
no-op there); any change to live-workout beyond one removed record (Parts B and C).

## 4. Contract

Normative code, printed at the file's own indentation (class members at four spaces). KDoc wording
is free unless quoted. The anchors of Section 6 are lines of this section, and each must match
exactly once in its file.

### 4.1 `AppCoroutineScopeImpl` (core:core commonMain)

Imports: `io.github.stslex.workeeper.core.core.logger.Logger` replaces `…logger.Log`, and
`kotlinx.coroutines.ensureActive` is added in import order. Everything not shown is unchanged. The
constructor:

    class AppCoroutineScopeImpl(
        private val lifecycleOwner: LifecycleOwner,
        override val defaultDispatcher: CoroutineDispatcher,
        override val immediateDispatcher: CoroutineDispatcher,
        /** Records every launch failure; see [AppCoroutineScope.launch]. */
        private val logger: Logger,
        /** GUARD: required generation parent; an autonomous Store scope could outlive its database. */
        generationJob: Job,
    ) : AppCoroutineScope, CoroutineScope {

The backstop:

        private fun exceptionHandler(
            eachDispatcher: CoroutineDispatcher?,
            onError: suspend (cause: Throwable) -> Unit = {},
        ) = CoroutineExceptionHandler { _, throwable ->
            logger.e(throwable)
            launch(eachDispatcher ?: defaultDispatcher) {
                onError(throwable)
            }
        }

The action overload:

        override fun <T> launch(
            start: CoroutineStart,
            onError: suspend (Throwable) -> Unit,
            onSuccess: suspend CoroutineScope.(T) -> Unit,
            workDispatcher: CoroutineDispatcher?,
            eachDispatcher: CoroutineDispatcher?,
            exceptionHandler: CoroutineExceptionHandler?,
            action: suspend CoroutineScope.() -> T,
        ): Job {
            val eachDispatcher = eachDispatcher ?: immediateDispatcher
            val workDispatcher = workDispatcher ?: defaultDispatcher
            val exceptionHandler = exceptionHandler ?: exceptionHandler(workDispatcher, onError)
            return launch(
                start = start,
                context = workDispatcher + exceptionHandler,
                block = {
                    runCatching { action() }
                        .onSuccess {
                            withContext(eachDispatcher) { onSuccess(it) }
                        }
                        .onFailure { failure ->
                            // GUARD (D4): a cancelled launch is not a failure. The check reads this block's own scope,
                            // not the Store's, and rethrows before the record, as withContext would before onError.
                            ensureActive()
                            logger.e(failure)
                            withContext(eachDispatcher) { onError(failure) }
                        }
                },
            )
        }

The flow overload:

        override fun <T> launch(
            flow: Flow<T>,
            workDispatcher: CoroutineDispatcher?,
            eachDispatcher: CoroutineDispatcher?,
            onError: suspend (cause: Throwable) -> Unit,
            each: suspend (T) -> Unit,
        ): Job = flow
            // Flow.catch rethrows the collector's own cancellation, so only a failure arrives (D4).
            .catch { cause ->
                logger.e(cause)
                onError(cause)
            }
            .onEach {
                withContext(eachDispatcher ?: immediateDispatcher) { each(it) }
            }
            .flowOn(workDispatcher ?: defaultDispatcher)
            .launchIn(this)

Afterwards, `logger.e(` appears exactly three times in the file and `Log` not at all.

### 4.2 `AppCoroutineScope` (core:core commonMain), KDoc only

The two `launch` KDocs say what Section 3 claims. Suggested:

- flow: "Collects [flow] on this scope: work on [workDispatcher], [each] on [eachDispatcher]. An
  upstream failure is recorded through the scope's logger, then handed to [onError]; the
  collector's own cancellation is neither."
- action: "Launches [action] on this scope. A failure is recorded through the scope's logger, then
  routed to [onError]; the coroutine's own cancellation is neither, while a `CancellationException`
  raised as it runs (a timeout) is a failure."

No signature changes.

### 4.3 `BaseStore.init` (core:ui:mvi commonMain)

            _scope = AppCoroutineScopeImpl(
                lifecycleOwner = currentLifecycleOwner,
                defaultDispatcher = storeDispatchers.defaultDispatcher,
                immediateDispatcher = storeDispatchers.mainImmediateDispatcher,
                logger = logger,
                generationJob = appScopeLifetime.job,
            )

Nothing else in `BaseStore` changes, so its public ABI and `mvi-public-jvm-abi.txt` do not change.

### 4.4 The call sites (commit 2)

The shapes at the base are quoted; `…` is unchanged text.

| File (base line) | Base | After |
| --- | --- | --- |
| `AppDialogRepoHandler` (31–34) | `launchDefault(` / `onError = { e -> logger.e(e, "Failed to publish ${action.dialog.id}") },` / `onSuccess = { },` / `) {` | `launchDefault {` |
| `AppDialogRepoHandler` (40–43) | the same, `"Failed to dismiss …"` | `launchDefault {` |
| `ChooseHandler` (24–29) | `launchDefault(` / `onError = { e ->` / `logger.e(e, "Failed to emit choice …")` / `},` / `onSuccess = { },` / `) {` | `launchDefault {` |
| `BackupClickHandler` (95–98) | `launchDefault(` / `onError = { e -> logger.e(e, "Failed to publish UndoRestoreConfirmation") },` / `onSuccess = { },` / `) {` | `launchDefault {` |
| `BackupClickHandler` (267–268) | `onError = { e ->` / `logger.e(e, "Failed to request drive.file for AI export")` | `onError = {`, the rest of the lambda unchanged |
| `BackupClickHandler` (425–426) | `onError = { e ->` / `logger.e(e, "Unknown error during restore operation")` | `onError = {`, the rest unchanged |
| `BackupClickHandler` (465) | `onError = { e -> logger.e(e, "Failed to load backup list") },` | the line goes; `onSuccess` stays |
| `BackupClickHandler` (523–526) | `launchDefault(` / `onError = { e -> logger.e(e, "Failed to save frequency") },` / `onSuccess = { },` / `) {` | `launchDefault {` |
| `BackupClickHandler` (544–545) | `private suspend fun emitUnknownErrorAndIdle(e: Throwable) {` / `logger.e(e, "Unknown error during backup operation")` | `private suspend fun emitUnknownErrorAndIdle() {`, its other two lines unchanged |
| `BackupClickHandler` (172, 216, 330, 349, 377) | `onError = { emitUnknownErrorAndIdle(it) },` (216: `{ e -> emitUnknownErrorAndIdle(e) }`) | `onError = { emitUnknownErrorAndIdle() },` |
| `PendingUndoOps` (80–83) | `launch(` / `// A failed hard-delete resurfaces on the next reload; logged, not surfaced.` / `onError = { error -> logger.e(error, "deferred exercise delete failed") },` / `) {` | `// A failed hard-delete resurfaces on the next reload: the launch records it, and nothing` / `// is surfaced.` / `launch {` |

`return@launchDefault` labels inside these blocks stay valid. After commit 2 a scan of every
production source set finds no `logger.` or `Log.` call inside an explicit `onError` lambda, and
none in a function that such a lambda calls. The one exception is core:core's `ResultUtils.fold`,
which has no consumer (backlog).

### 4.5 What changes, failure by failure

| Failure | Base | After PR-L |
| --- | --- | --- |
| a throw from `action` | `onError` only | recorded (Store tag), then `onError` |
| an upstream flow throw | `onError` only | recorded, then `onError` |
| the coroutine's own cancellation, either overload | nothing | nothing |
| a `CancellationException` inside an active coroutine (action or flow) | `onError` only | recorded, then `onError` |
| an `async` child's failure | backstop: `Log.e` (tag `Workeeper`), `onError` on the work dispatcher | backstop: recorded (Store tag), `onError` on the work dispatcher |
| a throw from `onSuccess` | the same backstop | the same backstop, recorded under the Store tag |
| a throw from `onError` (action) | the backstop records it (tag `Workeeper`) and runs `onError` again | the action's failure is recorded first; then the backstop records the throw from `onError` (Store tag) and runs `onError` again |
| a throw from a flow's `each` | uncaught: no handler is in the collector's context, so on Android it crashes (by reading) | unchanged |
| the 14 call-site records | recorded by the call site, Store tag, with a logcat message | recorded by the launch, Store tag, once; logcat shows the throwable's own message |

In Crashlytics a record is unchanged in content (the throwable and the `TAG` key). What changes is
which failures produce one, and the backstop's tag (`Workeeper` → `SCREEN_<name>`).

## 5. Tests

### 5.1 `LaunchFailureRecordingTest` (core:ui:mvi commonTest; JVM host and Native)

Path: `core/ui/mvi/src/commonTest/kotlin/io/github/stslex/workeeper/core/ui/mvi/LaunchFailureRecordingTest.kt`.
It uses `kotlin.test` and kotlinx-coroutines-test, with private in-file fakes.

**Harness.**

- `@BeforeTest`: `main = StandardTestDispatcher()`, then `Dispatchers.setMain(main)` before any
  scope exists (lifecycleScope builds on `Main.immediate`, StoreGenerationJoinTest's GUARD). Then
  `work = StandardTestDispatcher(main.scheduler, name = "work")` and
  `each = StandardTestDispatcher(main.scheduler, name = "each")`, so everything runs on one
  scheduler. `@AfterTest` calls `Dispatchers.resetMain()`.
- Each identity builds `AppCoroutineScopeImpl(FakeLifecycleOwner(), work, each, logger, Job())` and
  cancels it at its end.
  - `FakeLifecycleOwner` is a private copy of StoreGenerationJoinTest's
    (`LifecycleRegistry.createUnsafe`).
  - `logger` is a private `RecordingLogger : Logger`. Every member appends one entry to the test's
    ordered `events` list: `"record:<member>:<SimpleName>:<message>"` for the members that take a
    throwable, `"log:<member>"` for the others.
- `onError` and `onSuccess` in the identities append `"onError:<SimpleName>:<message>"` and so on to
  the same list.
- Every identity asserts the exact `events` list after `advanceUntilIdle()`.
- Exceptions are compared by type and message, never by identity: stack-trace recovery on the JVM
  host can copy an exception across a suspension.
- The dispatcher an `onError` runs on is read from
  `currentCoroutineContext()[ContinuationInterceptor]` and compared with `assertSame`.

**Identities.** Names avoid every character Kotlin/Native rejects (7.13 Section 4.3).

| # | Name | Drives | Expected `events` (and more) |
| --- | --- | --- | --- |
| 1 | `anActionFailureIsRecordedOnceBeforeOnErrorReceivesIt` | the action throws `IllegalStateException("action")` | `[record:e:IllegalStateException:action, onError:IllegalStateException:action]`; `onError` ran on `each` |
| 2 | `aCancelledActionRecordsNothingAndSkipsOnError` | the action is `awaitCancellation()`; advance; cancel that launch's job while the scope stays active; advance | `[]`; the job is cancelled and the scope is active |
| 3 | `aTimeoutInsideALiveActionIsRecordedAndReachesOnError` | the action is `withTimeout(1_000) { awaitCancellation() }` | `[record:e:TimeoutCancellationException:<m>, onError:TimeoutCancellationException:<m>]`, the same message twice |
| 4 | `anAsyncChildFailureIsRecordedOnceThroughTheBackstop` | the action is `async { throw IllegalStateException("child") }.await()` in its own scope | `[record:e:IllegalStateException:child, onError:IllegalStateException:child]`; `onError` ran on `work` |
| 5 | `anOnSuccessFailureIsRecordedOnceThroughTheBackstop` | the action returns `1`; `onSuccess` throws `IllegalStateException("success")` | `[record:e:IllegalStateException:success, onError:IllegalStateException:success]`; `onError` ran on `work` |
| 6 | `anUpstreamFlowFailureIsRecordedOnceBeforeOnErrorReceivesIt` | `flow<Int> { throw IllegalStateException("upstream") }` | `[record:e:IllegalStateException:upstream, onError:IllegalStateException:upstream]` |
| 7 | `aCancelledCollectionRecordsNothingAndSkipsOnError` | `flow<Int> { awaitCancellation() }`; advance; cancel that collection's job while the scope stays active; advance | `[]` |
| 8 | `aTimeoutInsideALiveCollectionIsRecordedAndReachesOnError` | `flow<Int> { withTimeout(1_000) { awaitCancellation() } }` | `[record:e:TimeoutCancellationException:<m>, onError:TimeoutCancellationException:<m>]` |
| 9 | `successfulLaunchesRecordNothing` | an action returning `1` with an `onSuccess` that appends `success:1`; advance; then `flowOf(1, 2)` with an `each` that appends `each:<n>`; advance | `[success:1, each:1, each:2]` |

Identities 1–8 pass explicit `onError` lambdas, so "onError runs exactly once" is part of every
list.

### 5.2 `StoreLaunchFailureTelemetryTest` (core:ui:mvi androidHostTest)

Path: `core/ui/mvi/src/androidHostTest/kotlin/io/github/stslex/workeeper/core/ui/mvi/StoreLaunchFailureTelemetryTest.kt`.
JUnit 5 and MockK on the plain JVM, with StoreTelemetryRedactionTest's harness:

- `Log.isLogging = false` and `Dispatchers.setMain(StandardTestDispatcher())` in `@BeforeEach`;
- `mockkObject(FirebaseAnalyticsHolder, FirebaseCrashlyticsHolder)`, with every `log`,
  `setCustomKey` and `recordException` stubbed;
- `recordException(throwable, tag)` captured as `"<tag>|<SimpleName>|<message>"`;
- `@AfterEach` restores all three.

One identity, `aFailingStoreLaunchIsRecordedOnceUnderTheStoreTag`:

1. A private minimal real `BaseStore` named `LaunchProbe` (StoreGenerationJoinTest's
   `ProbeStore` shape: `BaseHandlerStore()`, a no-op handler, `AnalyticsHolder()`,
   `LoggerHolder()`, `AppScopeLifetime()`) is `init`ed on a `FakeLifecycleOwner`.
2. It runs `store.launch<Unit>(action = { throw IllegalStateException("probe") })`, then
   `advanceUntilIdle()`.
3. It asserts the captured records equal exactly
   `["SCREEN_LaunchProbe|IllegalStateException|probe"]`.
4. The store is disposed and the lifetime is cancelled and joined.

### 5.3 `MviJvmAbiTest`

`lifetimeConstructorDeltasAreRequiredAndHaveNoDefaults` keeps both of its scope assertions and gains
one. The scope constructor's parameter type names equal exactly:

    androidx.lifecycle.LifecycleOwner, kotlinx.coroutines.CoroutineDispatcher,
    kotlinx.coroutines.CoroutineDispatcher, io.github.stslex.workeeper.core.core.logger.Logger,
    kotlinx.coroutines.Job

It gets a message naming D3. No other identity of the file changes.

### 5.4 Counts

- `:core:ui:mvi:testAndroidHostTest`: 28 → 38 cases (+9 commonTest, +1 androidHostTest); suites
  6 → 8.
- `:core:ui:mvi:iosSimulatorArm64Test`: 14 → 23.
- Every other module's host cases: unchanged, and all pass.
- No device test is added or changed.

### 5.5 The CI scripts (C6)

- `assert_mvi_source_topology.py`: `EXPECTED_PATHS_BY_SOURCE_SET` gains the two new paths
  (`commonTest` 3 → 4, `androidHostTest` 3 → 4). Nothing else changes.
- `assert_mvi_host_identities.py`: core:ui:mvi gains 10 `host(…)` tuples, the nine of Section 5.1
  and the one of Section 5.2, under one comment that names D2–D4. That is 10 → 20.
- `assert_kmp_ios_smoke.py`: core:ui:mvi gains the nine `native(…)` tuples of Section 5.1, under
  one comment. That is 5 → 14.

## 6. Known-negative controls

Each control runs: fresh GREEN → one mutation → the named verdict quoted from fresh XML →
byte-exact restoration → `git status --porcelain --untracked-files=no` empty → fresh GREEN.

- The K controls go through `mutation_harness.py` one-shot, with
  `--no-configuration-cache --console=plain` inside `--task` (the harness adds
  `--rerun-tasks --no-build-cache`).
- A compile-invalid mutation is a failed control, never a RED.
- Where a row says "only", every other identity of the task passes.
- "Host and Native" means the RED appears in both result directories.

Task T = `:core:ui:mvi:testAndroidHostTest :core:ui:mvi:iosSimulatorArm64Test --continue`.

| Id | File | Mutation (anchor → replacement) | Task | Required verdict |
| --- | --- | --- | --- | --- |
| K1 | AppCoroutineScopeImpl | `logger.e(failure)` → `// K1` | T | RED only 1 and 3 (host and Native), and 10 (host) |
| K2 | AppCoroutineScopeImpl | `ensureActive()` → `// K2` | T | RED only 2 and 4, host and Native |
| K3 | AppCoroutineScopeImpl | `ensureActive()` → `if (failure is kotlinx.coroutines.CancellationException) throw failure` | T | RED on 3, host and Native; every identity of the task other than 3 and 4 passes |
| K4 | AppCoroutineScopeImpl | `logger.e(cause)` → `// K4` | T | RED only 6 and 8, host and Native |
| K5 | AppCoroutineScopeImpl | `logger.e(throwable)` → `// K5` | T | RED only 4 and 5, host and Native |
| K6 | BaseStore | `logger = logger,` → `logger = io.github.stslex.workeeper.core.core.logger.Log,` | `:core:ui:mvi:testAndroidHostTest` | RED only 10 (the tag is `Workeeper`) |
| K7 | AppCoroutineScopeImpl | `logger.e(cause)` → `if (cause !is kotlinx.coroutines.CancellationException) logger.e(cause)` | T | RED only 8, host and Native |
| K8 | AppCoroutineScopeImpl | the two lines `logger.e(failure)` and `withContext(eachDispatcher) { onError(failure) }` swapped (one two-line anchor) | T | RED only 1 and 3, host and Native |

K3 leaves identity 4 unconstrained. Whether `await()` surfaces the child's exception or the parent's
cancellation is an internal ordering of kotlinx.coroutines, and only that answer decides whether
K3's rethrow records the child twice. The report names which it was.

Gate controls, on the GREEN XML of the head, through a scratchpad copy restored with `cp`:

| Id | Mutation | Required verdict |
| --- | --- | --- |
| G1 | `assert_mvi_host_identities.py`: `aFailingStoreLaunchIsRecordedOnceUnderTheStoreTag` → `…TagX` | exit 1, naming that tuple |
| G2 | `assert_kmp_ios_smoke.py`: `successfulLaunchesRecordNothing` → `…NothingX` | exit 1, naming that tuple |
| G3 | `assert_mvi_source_topology.py`: the new commonTest path removed from the manifest | exit 1, `extra=[…LaunchFailureRecordingTest.kt]` |

## 7. Gates

### 7.1 Entry

- Base = live origin/dev after the spec commit. It contains `1b9618412`. The latest
  `Android CI/CD - Unified Build and Tests` push run on dev is green (record its id); if it is red,
  STOP.
- No Section 9 path changed since `1b9618412`, except this document landing.
- A census script reproduces Sections 1 and 1.2 and the base lines of Section 4.4, each item with
  its method.
- Baselines, with stable flags and fresh XML:
  - `python3 .github/scripts/assert_kmp_ui_source_topology.py`, under every Python on the host:
    833 lines, exit 0, empty stderr, byte-identical across Pythons (BASE_GATE_OUTPUT);
  - `python3 .github/scripts/assert_mvi_source_topology.py` (BASE_MVI_TOPOLOGY);
  - forced `:core:ui:mvi:testAndroidHostTest` (6 suites, 28 cases), then
    `assert_mvi_host_identities.py` (10 identities);
  - `:core:core:testAndroidHostTest` (record the cases);
  - the fifteen-module Native command from `android_build_unified.yml` with `--continue`, then
    `assert_kmp_ios_smoke.py` (core:ui:mvi 14 cases, 5 identities);
  - the fifteen-module `linkDebugTestIosArm64` (15/15 `test.kexe`);
  - repository-wide `testDebugUnitTest --continue`: every module's suites and cases from fresh XML
    (HOST_BASELINE).

### 7.2 After commit 1

    ./gradlew :core:ui:mvi:testAndroidHostTest          # 8 suites, 38 cases; Section 5's names
    python3 .github/scripts/assert_mvi_host_identities.py   # 20 identities
    python3 .github/scripts/assert_mvi_source_topology.py   # commonTest=4, androidHostTest=4; else = BASE
    ./gradlew <fifteen-module iosSimulatorArm64Test> --continue
    python3 .github/scripts/assert_kmp_ios_smoke.py     # core:ui:mvi 23 cases, 14 identities; others = BASE
    ./gradlew <fifteen-module linkDebugTestIosArm64> --continue   # 15/15 test.kexe
    ./gradlew :core:core:testAndroidHostTest            # = baseline
    ./gradlew :core:core:assembleDebug :core:ui:mvi:assembleDebug   # Android and both iOS klibs

detekt runs alone.

### 7.3 After commit 2

    ./gradlew :feature:app-dialogs:impl:testDebugUnitTest :feature:settings:testDebugUnitTest \
      :feature:live-workout:testDebugUnitTest --continue   # each = its HOST_BASELINE row

Then Section 4.4's exit scan, with its count, and detekt alone.

### 7.4 Repository gates, each in its own invocation

    ./gradlew assembleDebug lintDebug testDebugUnitTest --continue   # every module = HOST_BASELINE, core:ui:mvi + 10
    ./gradlew detekt --continue
    ./gradlew assembleDebugAndroidTest
    ./gradlew verifyPaparazziDebug                       # no PNG changes; porcelain empty
    ./gradlew :lint-rules:test
    ./gradlew :app:wear:testStoreReleaseUnitTest -Pandroid.onlyEnableUnitTestForTheTestedBuildType=false --tests '*ReleaseRuntimeBoundaryTest*'
    python3 .github/scripts/assert_kmp_ui_source_topology.py   # byte-identical to BASE_GATE_OUTPUT
    python3 .github/scripts/assert_mvi_source_topology.py
    python3 documentation/personal_data_gate.py -v

### 7.5 Device gates

On one API-34 emulator with an explicit serial, animations off, each with fresh XML:

- `:core:ui:mvi:connectedDebugAndroidTest`: 2 cases (`AppFeatureScopeTest`);
- testing.md's canonical Smoke and Regression suites, then `assert_mvi_device_identities.py`.
  Compare with Phase 7.15 Part B's record (Smoke 44 with the 3 documented skips, Regression 88); a
  new failure is a STOP.

### 7.6 Diff

Against the PR's base (the spec commit): after commit 1, exactly its 9 paths; after commit 2, the
13 code paths of Section 9; after commit 3, all 18. Never a `.png`, `strings.xml`, `*.gradle.kts`,
workflow or `gradle/libs.versions.toml` path.

## 8. Out of scope

- Everything of Parts B and C: live-workout's copy, the filler, the gate field, the move.
- The flow overload's `each` path, the backstop's re-run of `onError` on the work dispatcher, the
  unused `exceptionHandler` parameter, and core:core's unconsumed `ResultUtils` / `ResultFlow`
  (backlog).
- An iOS crash reporter, and any `Log` or `FirebaseCrashlyticsHolder` change.
- Any default value, overload or KDoc in `StoreConsumer` / `HandlerStore`.
- Every other explicit `onError`: their state resets and user messages stay.
- The ✅ RESOLVED sections of tech-debt.md that mention the old swallow, the historical specs, the
  skills and `graphify-out/`.

## 9. Exact change boundary — 18 paths

New (2):

    core/ui/mvi/src/commonTest/kotlin/io/github/stslex/workeeper/core/ui/mvi/LaunchFailureRecordingTest.kt
    core/ui/mvi/src/androidHostTest/kotlin/io/github/stslex/workeeper/core/ui/mvi/StoreLaunchFailureTelemetryTest.kt

Modified, core (4):

    core/core/src/commonMain/kotlin/io/github/stslex/workeeper/core/core/coroutine/scope/AppCoroutineScope.kt
    core/core/src/commonMain/kotlin/io/github/stslex/workeeper/core/core/coroutine/scope/AppCoroutineScopeImpl.kt
    core/ui/mvi/src/commonMain/kotlin/io/github/stslex/workeeper/core/ui/mvi/BaseStore.kt
    core/ui/mvi/src/androidHostTest/kotlin/io/github/stslex/workeeper/core/ui/mvi/MviJvmAbiTest.kt

Modified, CI (3):

    .github/scripts/assert_mvi_source_topology.py
    .github/scripts/assert_mvi_host_identities.py
    .github/scripts/assert_kmp_ios_smoke.py

Modified, call sites (4):

    feature/app-dialogs/impl/src/main/kotlin/io/github/stslex/workeeper/feature/app_dialogs/impl/mvi/handler/AppDialogRepoHandler.kt
    feature/app-dialogs/impl/src/main/kotlin/io/github/stslex/workeeper/feature/app_dialogs/impl/mvi/handler/ChooseHandler.kt
    feature/settings/src/main/kotlin/io/github/stslex/workeeper/feature/settings/mvi/handler/BackupClickHandler.kt
    feature/live-workout/src/main/kotlin/io/github/stslex/workeeper/feature/live_workout/mvi/handler/PendingUndoOps.kt

Documentation (5, commit 3):

    documentation/architecture.md
    documentation/testing.md
    documentation/ci-cd.md
    documentation/tech-debt.md
    documentation/feature-specs/kmp-phase-7-16-live-workout-feature.md   (Status and Section 14 only)

That is 2 + 4 + 3 + 4 = 13 code paths, and 18 with the documentation.

What each document gains:

- **tech-debt.md.** The section "The default flow `onError` is silent — 21 of 22 production
  collections report nothing (measured 2026-08-16, KMP phase 6)" becomes resolved, in the file's
  form.
  - The heading starts with `✅ RESOLVED — ` and ends with `; resolved <date>, Phase 7.16 PR-L)`.
  - Directly under it goes a `**Resolution (Phase 7.16 PR-L, <date>).**` paragraph covering:
    - both overloads record every failure through the Store's logger before `onError`;
    - the coroutine's own cancellation is exempt, and a `CancellationException` inside an active
      coroutine is not;
    - the backstop records through the same logger;
    - the 14 call-site records went, so each failure is recorded once;
    - the base census (62/35, 19/3, 22/21);
    - the ten identities and K1–K8.
  - The body below stays as the record.
- **architecture.md.**
  - "Coroutine scope and dispatchers", first bullet: the helpers catch exceptions, record each
    failure through the Store's logger (one non-fatal under `SCREEN_<name>`), then invoke `onError`.
    The coroutine's own cancellation is neither recorded nor handed on, and a timeout is a failure.
    So `onError` never logs: it restores state or tells the user.
  - The backstop bullet: it records through the same logger.
  - In the navigation-results text, the sentence that ends "silently, per the
    `.catch { onError(it) }` swallow below" ends "silently: nothing throws, so nothing is recorded
    either".
  - The "Note for tests" says the flow overload records an upstream failure and hands it to
    `onError` without rethrowing, so a broken result path still surfaces as a screen holding
    default state; the advice to assert the observable effect stays.
- **testing.md.**
  - "Shared MVI runtime": commonTest also holds the launch-failure recording contract, and
    androidHostTest the Store-tag wiring of a launch failure.
  - The identity sentence becomes "twenty exact Android-host identities, fourteen exact MVI Native
    identities, and both Android-device identities" (X4).
  - "Off-device hazards", first bullet, gains one sentence: since Phase 7.16 a failing Store launch
    logs too, so a host test that drives a real `BaseStore` into a failure needs the same gate.
- **ci-cd.md.** In the KMP iOS kit smoke paragraph, `:core:ui:mvi` (lifetime/event/navigation/
  processor contracts) gains "and launch-failure recording".

## 10. STOP conditions

Stop, report what was measured, and widen nothing, if:

- the base lacks `1b9618412`, the latest dev push run is red, or a Section 9 path drifted;
- the entry census or a baseline differs from Sections 1, 1.2, 4.4 or 7.1 (the `launchDefault`
  reconciliation of Section 1.1 is reported, not stopped on);
- a new identity cannot run on Native as specified (do not move it to androidHostTest or change
  the harness);
- the ABI manifest test fails (`BaseStore`'s public ABI must not change);
- any host case outside core:ui:mvi changes its count or outcome;
- the topology gate's output differs from BASE_GATE_OUTPUT;
- a control of Section 6 does not produce its required verdict;
- detekt or lint reports anything, or any suppression becomes necessary;
- a gate needs a build-file, workflow, catalog or version change;
- a device suite fails in a way the baseline does not.

## 11. Signed, bisect-green commit plan

1. **feat(core): record every launch failure through the Store's logger.** The 9 paths: the two
   core:core files, `BaseStore`, `MviJvmAbiTest`, the two new suites, and the three CI scripts.
2. **refactor: drop the call-site logs the launch now records.** The 4 call-site files.
3. **docs: document the launch failure seam and record PR-L's evidence.** The four documents of
   Section 9, and this document's Status line and Section 14. The Status line becomes
   `**Status:** Part A COMPLETE — implemented by the PR whose merge brought this line to dev.
   Parts B (PR-P) and C (PR-M) are specified by amendment.`, which is true on dev only once the
   maintainer merges.

Between commits 1 and 2, the 14 call-site failures are recorded twice; no test observes it.
Review fixes are new signed commits inside Section 9. Nothing is amended, squashed or reordered.

## 12. Exit criteria

### 12.1 PR-L

Section 3 is proven and Sections 6 and 7 are evidenced, in one PR into dev with CI green on its
head. `@claude review` is requested, and every finding is classified (correct / already decided /
wrong / new) before any fix. The maintainer merges.

### 12.2 After the next release (the maintainer's observation, not a gate)

Crashlytics shows launch failures as non-fatals under their Store's `TAG`. The volume compared with
the release before is the first measure of what the default `onError` was hiding. Nothing in this
part predicts it.

## 13. Parts B and C (decisions; the contracts follow by amendment)

The amendment for Part B is specified on dev after PR-L merges, and Part C's after PR-P merges.
Each states its measured baseline, contract, controls and commit plan in 7.15 Part B's shape.

Accepted with the discovery and unchanged by Part A:

- D1's split. PR-P lands F1–F3's restructure in the classic module, reading templates verbatim
  through a temporary module-local reader on the app `Context`. PR-M's move commit swaps that reader
  for `StringResolver`'s no-argument path and deletes it.
- D5–D8, and the discovery's SP1–SP8 and SM0–SM9. Among them:
  - the filler in kit, with a parity test;
  - the copy model at `Init`;
  - the two resource-id models made semantic;
  - the picker seam on 7.13 D2-A;
  - `kotlin.time.Clock`;
  - the dead keys removed;
  - the identity reconciliation;
  - the owner's device check;
  - P1's 12 items;
  - the previews;
  - the table rows;
  - the suites, the goldens (blob set `53221eab…`), the device class and the identity test;
  - the gate entry with `state_mutators`;
  - the documents.
- Parts B and C begin with the discovery's "Carry into the prompt" list (7.15's spec misses and host
  lessons).

## 14. Implementation evidence

Recorded by commit 3. Every Gradle gate ran after `./gradlew --stop` with `--rerun-tasks
--no-build-cache --no-configuration-cache --console=plain`, on its own daemon registry, and every
count below is parsed from JUnit XML written after the gate's recorded start. Repository-wide,
Native and link gates added `-Dorg.gradle.jvmargs=-Xmx4g --max-workers=2`; device suites used
testing.md's canonical `-Dorg.gradle.jvmargs=-Xmx3g --max-workers=2 --full-stacktrace --continue`.

### 14.1 Base and commits

- Base: `85f099bb9a73086bb66e4a3c03214e341d50758c`, this document's landing on dev (parent
  `1b9618412`). It changed one Markdown file, so no unified run started for it.
- Commit 1: `8a9efee19fd0e00896f711fb8f8edd1ffa205d6f`, the 9 paths of Section 11; local `gpgsig`
  header present.
- Commit 2: `bbdf6ffde9aae58cb4752f96ab5a9a754d961d7d`, the 4 call-site paths; local `gpgsig`
  header present.
- Commit 3: this commit.

### 14.2 Entry gate (Section 7.1)

- The latest `Android CI/CD - Unified Build and Tests` push run on dev: `40193083943`, head
  `1b9618412`, success.
- Drift: since `1b9618412`, only this document's landing touched a Section 9 path.
- Census: one script read the base's git objects (1,071 production Kotlin files: `main`,
  `commonMain`, `androidMain`, `iosMain`), blanked comments and strings, and parsed every `launch` /
  `launchDefault` token's receiver and named arguments. Each receiver was classified by its declared
  type: a Store launch inside a handler context, kotlinx, ActivityResult or a forwarder. Zero
  tokens were left unresolved. The output was byte-identical under all five Pythons (sha256
  `eaa5bd1d449bee41…`).

| Item (method) | Measured | Verdict |
| --- | --- | --- |
| Launch table: action, `launchDefault`, `Flow.launch` (calls / default `onError`) | 62/35, 19/3, 22/21 | equal |
| The 35 by module | single-training 10, exercise 9, archive 4, home 3, live-workout 3, all-exercises 2, settings 2, all-trainings 1, `BaseStore.sendEventWithAwait` 1 | equal |
| The 3 `launchDefault` defaults | settings' `BackupClickHandler` | equal |
| `launchDefault` calls passing both `onError` and `onSuccess` (reported, Section 1.1) | 15 of 19: app-dialogs 3, exercise-chart 2, settings 10 | as measured |
| The 22 flow calls; the explicit `onError` | 21 features + 1 app/wear; past-session | equal |
| Explicit `onError` arguments in feature and app/wear code | 44: 43 inline lambdas + `PendingUndoOps.undoPending`'s forwarded parameter, whose one caller passes a lambda | equal |
| Lambdas that branch on the throwable's type (`is` / `as?` test) | 1, past-session's `InputHandler` | equal |
| Call-site records (logger call in the lambda, or in a function it calls) | 9 direct (app-dialogs 3, settings 5, live-workout 1) + 5 through `emitUnknownErrorAndIdle`: 14, in ten places | equal |
| Section 4.4's base lines (exact stripped-line comparison) | 15 rows, all equal | equal |
| `AppCoroutineScopeImpl(` constructions (every source set, tests included) | 1, `BaseStore.init` | equal |
| The backstop calls `Log.e(throwable)`; `onFailure` and the flow `catch` call no logger | yes, no, no | equal |
| `withTimeout(` in production | none; `withTimeoutOrNull(` sits outside Store launches | equal |
| core:ui:mvi `@Test` counts | commonTest 7/1/5, androidHostTest 6/2/7, iosTest 1 | equal |
| CI scripts (AST): host tuples, core:ui:mvi native tuples, manifest sizes | 10, 5, commonTest 3, androidHostTest 3 | equal |
| `Logger` members | 13 abstract, 3 with bodies | equal |
| Section 1.2's static facts (logger chain, `Log.e` order, iOS no-op, Android filter, ABI pins) | all hold | equal |
| Failure messages that interpolate a value (Section 1.2: 8) | 19 in shipped production (9 single-line), none user-entered | deviation 1 |

Outside the table, core:core's `ResultUtils.fold` calls `AppCoroutineScope.launch(flow = …)`
directly: 1 site with an explicit `onError`. It is not a `Flow.launch` call site. The six
`BaseStore` / `BaseHandlerStore` overrides are forwarders.

Baselines on the base:

| Gate | Executed | Fresh XML |
| --- | --- | --- |
| `assert_kmp_ui_source_topology.py`, five Pythons | — | 833 lines, exit 0, empty stderr, one sha256 `37315e70…` (BASE_GATE_OUTPUT) |
| `assert_mvi_source_topology.py` | — | exit 0 (BASE_MVI_TOPOLOGY) |
| `:core:ui:mvi:testAndroidHostTest` + host gate | 62/62 | 6 suites, 28 cases; 10 identities |
| `:core:core:testAndroidHostTest` | 25/25 | 9 suites, 46 cases |
| fifteen-module `iosSimulatorArm64Test --continue` + oracle | 410/410 | 95 suites, 626 cases; core:ui:mvi 14 cases, 5 identities |
| fifteen-module `linkDebugTestIosArm64 --continue` | 395/395 | 15/15 fresh `test.kexe` |
| `testDebugUnitTest --continue` (HOST_BASELINE) | 1290/1290 | 34 module/task rows, 472 suites, 3,360 cases, 0 failures |

### 14.3 After commits 1 and 2 (Sections 7.2 and 7.3)

| Gate | Executed | Result |
| --- | --- | --- |
| `:core:ui:mvi:testAndroidHostTest` + host gate | 62/62 | 8 suites, 38 cases: the 28 baseline names plus exactly Section 5's ten; 20 identities |
| `assert_mvi_source_topology.py` | — | equal to BASE_MVI_TOPOLOGY but the counts line (`commonTest=4`, `androidHostTest=4`) |
| fifteen-module Native + oracle | 410/410 | 635 cases; core:ui:mvi 23 (the 14 plus the nine), 14 identities; every other module's names and oracle line equal to the baseline |
| fifteen-module link | 395/395 | 15/15 |
| `:core:core:testAndroidHostTest` | 25/25 | 46 cases, names equal to the baseline |
| `:core:core:assembleDebug :core:ui:mvi:assembleDebug` | 136/136 | Android plus both iOS klibs of each module |
| detekt (alone, before and after each commit) | 63/63 | 0 findings in 55 fresh reports |
| app-dialogs, settings, live-workout `testDebugUnitTest` | 325/325 | 39, 120, 227 cases: each name set equal to its HOST_BASELINE row |

### 14.4 Known-negative controls (Section 6)

Task T ran through `mutation_harness.py` one-shot. Every control's RED is scored by identity from
the fresh XML of both result directories, and every other case of the task passed. Each control
restored its file to HEAD's sha256 and mode with an empty porcelain. Consecutive controls with a
byte-identical command shared the fresh GREEN between them (38 host and 23 Native cases each; K6's
were the host task alone).

| Id | Required | Measured RED (host / Native), quoted |
| --- | --- | --- |
| K1 | only 1, 3 (both), 10 (host) | 1, 3, 10 / 1, 3. Identity 1: `expected: <[record:e:IllegalStateException:action, onError:IllegalStateException:action]> but was: <[onError:IllegalStateException:action]>`. Identity 10: `but was: <[]>` |
| K2 | only 2, 4 | 2, 4 / 2, 4. Identity 2: `expected: <[]> but was: <[record:e:JobCancellationException:StandaloneCoroutine was cancelled]>`. Identity 4 records the child twice |
| K3 | 3; others but 3, 4 pass | 3, 4 / 3, 4. Identity 3: `but was: <[]>`. Identity 4: `but was: <[record:e:IllegalStateException:child, record:e:IllegalStateException:child, onError:IllegalStateException:child]>` |
| K4 | only 6, 8 | 6, 8 / 6, 8. Identity 6: `but was: <[onError:IllegalStateException:upstream]>` |
| K5 | only 4, 5 | 4, 5 / 4, 5. Identity 5: `but was: <[onError:IllegalStateException:success]>` |
| K6 | only 10 (host task) | 10. `expected: <[SCREEN_LaunchProbe\|IllegalStateException\|probe]> but was: <[Workeeper\|IllegalStateException\|probe]>` |
| K7 | only 8 | 8 / 8: the timeout's record is missing |
| K8 | only 1, 3 | 1, 3 / 1, 3. Identity 1: `but was: <[onError:IllegalStateException:action, record:e:IllegalStateException:action]>` |

K3's identity 4, on both platforms: `await()` surfaced the child's own `IllegalStateException`, not
the parent's cancellation. So K3's type test did not rethrow, `onFailure` recorded the child, and
the backstop recorded it a second time.

| Id | Mutation (scratchpad copy, restored with `cp`) | Measured |
| --- | --- | --- |
| G1 | host tuple → `…TagX` | exit 1: `expected exactly one testcase with classname='…StoreLaunchFailureTelemetryTest' name='aFailingStoreLaunchIsRecordedOnceUnderTheStoreTagX()', found 0` |
| G2 | native tuple → `…NothingX` | exit 1: `… name='successfulLaunchesRecordNothingX[iosSimulatorArm64]', found 0` |
| G3 | commonTest path removed | exit 1: `commonTest Kotlin manifest mismatch; missing=[], extra=['kotlin/io/github/stslex/workeeper/core/ui/mvi/LaunchFailureRecordingTest.kt']` |

Each restored script's sha256 equalled HEAD's, and its GREEN exited 0.

### 14.5 Exit scan (Section 4.4)

The census parser, on commit 2: 1,071 production files, 162 launch tokens, none unresolved. Its
input is 37 explicit inline `onError` lambdas (36 in feature code, plus `ResultUtils.fold`'s) and
the one forwarded parameter, whose caller's lambda is checked too: 38 lambdas. One logs: the
lambda in `ResultUtils.fold`, through its `onError` parameter's default
`{ logger.e(it, it.message) }`, which has no consumer. The known negative holds: on the base and on
commit 1 the same scan fails with exactly the 14 call-site records.

### 14.6 Repository, Native and device gates (Sections 7.4 and 7.5), on commit 2

| Gate | Executed | Result |
| --- | --- | --- |
| `assembleDebug lintDebug testDebugUnitTest --continue` | 2808/2808 | 474 suites, 3,370 cases, 0 failures. Every module/task row's names equal HOST_BASELINE except core:ui:mvi, which gains 2 suites and 10 cases, Section 5's names |
| lint, inside that gate | — | no finding in any module except app:app, app:dev and app:store. Those three each report the same 80 dependency-version advisories (`GradleDependency` 40, `NewerVersionAvailable` 34, `AndroidGradlePluginVersion` 6) on `gradle/libs.versions.toml` and `gradle-wrapper.properties`, and dev's run `40193083943` reports the same on the base. No finding names a changed path |
| `detekt --continue` | 63/63 | 0 findings |
| `assembleDebugAndroidTest` | 2171/2171 | built |
| `verifyPaparazziDebug` | 675/675 | 169 suites, 1,766 cases, 0 failures; porcelain empty, no PNG changed |
| `:lint-rules:test` | 9/9 | 16 suites, 151 cases |
| `:app:wear:testStoreReleaseUnitTest … --tests '*ReleaseRuntimeBoundaryTest*'` | 107/107 | 3 cases |
| `assert_kmp_ui_source_topology.py`, five Pythons | — | byte-identical to BASE_GATE_OUTPUT (833 lines, sha256 `37315e70…`) |
| `assert_mvi_source_topology.py` | — | equal to commit 1's output |
| `personal_data_gate.py -v` | — | PASS |
| fifteen-module Native + oracle | 410/410 | 635 cases; names and oracle equal to commit 1's run |
| fifteen-module link | 395/395 | 15/15 fresh `test.kexe` |

On `emulator-5554` (API 34, animations off), each suite ran with `ANDROID_SERIAL` set and its XML
read fresh:

| Suite | Executed | Result |
| --- | --- | --- |
| `:core:ui:mvi:connectedDebugAndroidTest` | 160/160 | 2 cases, `AppFeatureScopeTest` |
| Smoke, then `assert_mvi_device_identities.py` | 2131/2131 | 44 cases, the 3 documented `pendingFeatureRewrite` skips, 0 failures; 2 device identities |
| Regression | 2131/2131 | 88 cases, 0 failures |

Smoke and Regression equal Phase 7.15 Part B's record name for name, taken from the D-01 run's
lists of the same suites. Before and after every suite, the device state (the three animation
scales, rotation, keyguard) and the pre-installed dev app's `lastUpdateTime` were unchanged, and no
other instrumentation ran on the serial.

### 14.7 Environment

- Xcode 27.0 (27A266a) here, with iOS simulator runtimes 18.0, 18.2, 18.6 and 26.5; CI selects
  Xcode 26.6. JDK 21.0.11 (JBR).
- Pythons: 3.9.6 (`/usr/bin/python3`), 3.11.16, 3.13.16, 3.14.8 and 3.15.0 (`python3`). Controls
  ran under `python3` only.
- Gradle ran on a daemon registry private to this run, so another checkout's `--stop` could not
  reach it, and its own `--stop` reached no other daemon.
- Emulator: `Pixel_6_API_34`, serial `emulator-5554` (API 34, `sdk_gphone64_arm64`), cold-booted
  with animations off. Another device was attached over adb throughout; every adb command and
  device task named the serial.
- Spotlight indexing was enabled on `/`; it was left as it was.

### 14.8 Deviations

1. **Section 1.2's "8 messages that interpolate a value".** The census found 19 in shipped
   production code (9 if the keyword and the literal share a line), plus 7 in build-logic and the
   test-support modules. None interpolates a user-entered value: they carry file, directory and
   DataStore names, the package name, WAL counters, restore-phase enums, attempt and owner ids, the
   plan-editor route's UUIDs and a schema SQL preview. Section 10 makes a census difference a STOP;
   the maintainer ruled on 2026-10-25 to proceed and record the count here and in the PR.
2. **The helper `FakeLifecycleOwner` (Sections 5.1 and 5.2).** commonTest and androidHostTest
   compile into one package, where StoreGenerationJoinTest already declares a private
   `FakeLifecycleOwner`. A second private copy is a compile error, measured:
   `LaunchFailureRecordingTest.kt:257:15 Redeclaration`. The copies follow the module's per-file
   prefix convention instead, as `RecordingLifecycleOwner` and `LaunchProbeLifecycleOwner`. Their
   bodies are StoreGenerationJoinTest's.

### 14.9 Not verified on this host

- Xcode 26.6, which CI's KMP iOS kit smoke job runs; this host has 27.0.
- The iosArm64 test binaries run on a device: they are linked, never executed.
- Crashlytics volume in release (Section 12.2), and iOS crash reporting (the holder is a no-op).

GitHub's signature verification, the CI run ids and the review classification go in the PR body
and the report, because they cannot exist before this commit does.
