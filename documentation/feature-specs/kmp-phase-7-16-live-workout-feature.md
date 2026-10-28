# KMP Phase 7.16 — feature:live-workout. Part A: the launch failure seam (PR-L)

**Status:** COMPLETE — Part A by PR #344, Part B by PR #345, Part C by the PR whose merge brought
this line to dev.

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

---

# Part B — live-workout's copy before the click (PR-P)

**Amendment of 2026-10-25.** Sections B0–B12 are Part B's contract, and Section B13 is filled by its
implementation. Sections 0–14 above are Part A's record and are not rewritten.

**Measured on:** dev @ `c3026c3cebdd0680a79917c2844bbaac3ae17286` (2026-10-25, the merge of #344,
PR-L), statically: git data, greps and scripts. Nothing was compiled or run. The implementation
base is dev after this amendment lands, and Section B6.1 proves it carries this tree on every path
Part B reads.

**Prototype.** Section B3's code and Section B4's tests were written against the measured tree in a
scratch copy, and every census of Sections B2 and B5.3 ran on that copy. The prototype is not
evidence, because nothing in it was compiled. The implementation's fresh runs are.

## B0. Authority, decisions, choices, corrections

### B0.1 Authority order

1. live origin/dev at the implementation base;
2. AGENTS.md, CLAUDE.md, documentation/architecture.md, testing.md, ci-cd.md, lint-rules.md;
3. Part B of this document;
4. Part A of this document (Sections 0–14), and the Phase 7.13–7.15 specifications where Part B
   cites them;
5. the checked-in source, resources, tests, PNGs and CI oracles at the implementation base.

### B0.2 Stable rules

Section 0.2 applies, with these changes and additions:

- **One catalog change.** SP6 removes the two dead keys from `values/strings.xml` and
  `values-ru/strings.xml` (Section B3.4). No other catalog line, build file, workflow, CI script or
  version catalog changes.
- **Suppressions.** No new suppression of any kind, in production or in tests. The module's
  inherited suppressions stay byte-identical (Section B2.6).
- **Goldens.** None is recorded, and no PNG blob changes (Section B2.6).
- **Census method.** Every census reads a call from its name to its closing parenthesis, across
  lines, with comments and string literals blanked; a name split by whitespace before `.` or `(`
  still matches (Part A's deviation 1).
- **Test helpers.** A new helper's name is unique across the module's whole host compilation (Part
  A's deviation 2).
- **Controls.** Every identity this part adds has a known-negative control that turns it RED
  (Section B5).

### B0.3 Decisions

Locked by the maintainer on 2026-10-25 (Section 2), with the discovery's spec-level items SP1–SP8
accepted by starting Part A:

| Id | Decision, as it binds Part B |
| --- | --- |
| D1 | PR-P restructures live-workout in the classic module. Templates are read verbatim through a temporary module-local reader on the app `Context`, which PR-M's move commit deletes. The owner checks the classic build on a device (SP8). |
| D5 | The copy is resolved into State at the load, and kit's pure filler fills the templates synchronously. |
| D6 | Every copy read outside a launch comes from D5's copy. |
| SP1 | The filler lives in kit's commonMain and is not called `.format(`. It has a kit-level parity test against `ComposeStringResolver`; live-workout's host tests add parity against Android's `getString(id, args)` for every template the module fills. |
| SP2 | The reader is internal and module-local, reads `context.getString(id)`, and is injected only where the copy is resolved. |
| SP3 | One copy model in State holds the constants and the templates, kit's reps unit through `StringResolver` among them, so CMP's top-level `getString` leaves `CommonHandler`. Launch bodies keep resolving on use, through `ResourceWrapper`. |
| SP4 | The two resource-id models become semantic, and a `when` maps them. |
| SP5 | The picker seam follows 7.13 D2-A: a public two-member interface, `ExercisePickerHandler` public with an internal constructor, a `@Binds` in the graph. `ClickHandlerTest` and `DialogClickHandlerTest` record the calls on a fake. |
| SP6 | `kotlin.time.Clock` replaces the three `System.currentTimeMillis()` calls; the two dead keys leave both catalogs. |
| SP7 | Identities that pin today's labels keep their expected strings. New identities pin the copy's resolution and the one-write dialogs. Every suite's setup is reconciled here, not during the run. |
| SP8 | The owner's device check of the classic build, after the merge (Section B11.2). |

D7 (`state_mutators`) and D8 are Part C's.

### B0.4 Choices this part makes (CB1–CB11)

| Id | Choice | Why |
| --- | --- | --- |
| CB1 | One `LiveWorkoutCopy` with 31 `String` fields, in `State.liveCopy`, default `LiveWorkoutCopy.EMPTY`. It replaces `State.repsUnitLabel`. | One field keeps `State.create` and every construction site as they are. |
| CB2 | The copy resolves at each load's suspend boundary, in `CommonHandler.toLoadedState()`. That runs on every `Init` and on every saved-plan reload, so a re-fired `Init` resolves again. A resolution failure is a load failure: on `Init` the launch's `onError` abandons the session (today's path); on a reload the launch records it (Part A) and the State stays as it was. | D5 says "at `Init`". The load replaces the State whole (`toState` builds it), so carrying the old copy across a reload would need a carry rule inside `coveredLoad`'s compare-and-set. Reading the copy with the load needs none. |
| CB3 | The pipeline reads the copy from the State it transforms. `StateStatusMapper` loses its constructor parameter, and `LiveSetMutator`'s constructor is unchanged. | SP3 said the pipeline takes the copy in place of the wrapper. Every pipeline function already receives the State, so only `toState` gains a parameter, and no lambda can read a copy other than its own State's. |
| CB4 | `fun String.fillTemplate(vararg formatArgs: Any): String`, on CMP 1.11.1's `replaceWithArgs` rule: `%N$d` and `%N$s` become `formatArgs[N - 1].toString()`, inserted literally, and nothing else changes. A missing argument throws `IndexOutOfBoundsException`, as CMP's does. | Parity by construction with what `StringResolver` formats after the move (verified in CMP's source at tag v1.11.1: `Regex("""%(\d+)\$[ds]""")`, `args[index - 1]`, arguments mapped with `toString()`). |
| CB5 | `LiveWorkoutCopyReader` is unscoped `@Inject`, injected into `CommonHandler` alone. | It holds nothing but the application `Context` that `AppGraph` provides. Its name is outside `MetroScopeRule`'s buckets. |
| CB6 | The two synchronous errors (`InvalidReps`, and `FinishFailed` on a finish with no training) read the copy. `ErrorType.InvalidReps` goes, because nothing produces it any more; its key stays, read into the copy. The 17 launch-body errors keep `ResourceWrapper`. | D6. `FinishFailed` stays, because its launch's `onError` still raises it. `ErrorType.Unknown` had no producer before Part B and stays (out of scope). |
| CB7 | The id mappings live in mappers. `DeleteExerciseCopyMapper` gains `titleRes()`, `bodyRes()`, `confirmRes()` and `toastTemplate(…)`, and a new `ErrorTypeMapper` holds `messageRes()`. | 7.13 S3. `DeleteExerciseCopyMapperTest`'s four identities keep their names. |
| CB8 | The seam is `ExercisePicker`. Its two consumers keep the parameter name `pickerHandler`. | 7.13 D2-A's shape (`ChartLoader`), with no rename churn in the two handlers. |
| CB9 | `Clock.System.now().toEpochMilliseconds()` at all three sites. | SP6. `kotlin.time.Clock` needs no opt-in on the repository's Kotlin, and the repository already uses it so. |
| CB10 | Reconciled identities keep their expected values. A value that came from a wrapper stub on a path that now reads the copy goes into the State as `LiveWorkoutCopy.EMPTY.copy(<field> = <value>)`. New identities use `NAMED_COPY`, a test fixture whose every value is its field's name, with a template's placeholders after it. | SP7. A read of the wrong field shows in the expected string. |
| CB11 | The device gate captures logcat during Smoke and Regression. A `SCREEN_LiveWorkout` error line is a STOP. | The PR-L review: instrumented runs keep `Log.isLogging` true (`TestApplication.isDebugLoggingAllow`), so every launch record prints as `E/SCREEN_<name>`. |

### B0.5 Observable differences

- **OD1.** Labels follow the locale of the latest load. Before, each label resolved when it was
  built. Android recreates the Activity on a locale change and `Init` re-fires, so the screen
  relabels then.
- **OD2.** The filler prints its arguments with `toString()`, where `ResourceWrapper` formatted with
  the device locale. A locale with non-Latin digits would have printed its own digits before; EN
  and RU print Latin digits either way (Section B4.2's parity identities).
- **OD3.** Before the first load, `State.liveCopy` is `EMPTY`, as blank as `State.create`'s title and
  meta line. A watch write that lands in that window derives its labels from it, and the load's
  State replaces them.
- **OD4.** A catalog read that throws fails the load (CB2), not a later click.

### B0.6 Corrections to the discovery and to Part A

- **XB1.** The `ResourceWrapper` reads are 36, not 35. One call in `DialogClickHandler`
  (`processFinishNameChange`) spans two lines.
- **XB2.** Of the 13 State lambdas that reach copy through another file (F1), 12 do it by a call and
  one by the function reference `setMutator::applyExternalWrite` in `coveredLoad`.
- **XB3.** After Part A, live-workout has four launches with the default `onError`, not three: Part A
  turned `PendingUndoOps`' deferred delete into one.
- **XB4.** D7's `state_mutators` must cover `LiveWorkoutMapper` too in Part C: the 13 lambdas run
  `withPresentation` through `StateStatusMapper`.
- **XB5.** SP4's UI-side mapping lives in `DeleteExerciseCopyMapper`, so the mapper suite's four
  identities keep their names (CB7).
- **XB6.** Part A's C8 and Section 8 say "recorded as backlog" and name no file. Commit 4 records
  those items in tech-debt.md (Section B8).

## B1. Bounded exit claim

The implementation may prove only this:

- kit's commonMain gains `fillTemplate`, which fills a verbatim template as CMP 1.11.1's `getString`
  does. On the JVM host it equals `ComposeStringResolver`'s own formatting on kit's EN and RU
  catalogs, and kit's Native compilation and link stay green;
- live-workout reads its 30 outside-composition keys and kit's reps unit once per load, verbatim,
  into `State.liveCopy`;
- every template the module fills from that copy equals Android's `getString(id, args)` on the EN
  and RU catalogs, with the argument types its call sites pass;
- no State lambda of a live-workout handler holds a resource token, references a resolver, or
  reaches a copy-resolving function of any file of the module, by call or by function reference;
- the status pipeline (`LiveSetMutator`, `StateStatusMapper`, `LiveWorkoutMapper`,
  `LiveSetRowsResolver`, `ExerciseDoneRule`) names neither `ResourceWrapper` nor a resolver, and
  holds no resource token;
- the four dialogs and the finish dialog open from the copy in one write, with no launch, and the
  toasts and the two synchronous errors take their text from the copy on the click path;
- `ResourceWrapper` keeps exactly seven reads, all in launch bodies;
- CMP's top-level `getString` leaves the module, and `StringResolver` reads kit's reps unit at the
  load;
- `ClickHandler` and `DialogClickHandler` reach the picker through `ExercisePicker`;
- no `System.currentTimeMillis()` remains in the module;
- every existing identity keeps its name and expected values, with Section B4.3's setups; the module
  gains 14 identities and kit 7;
- every PNG blob, the topology gate's output and every other module's host cases are unchanged;
- the device suites pass as they did, with no `SCREEN_LiveWorkout` error line.

Not claimed: OD1–OD4 on a device (SP8 is the owner's); the release Crashlytics effect of anything.

It does not permit:

- changing any label's text, a dialog's layout or a toast's timing;
- moving the module to KMP, its catalog to Compose resources, or a launch body's reads to
  `StringResolver` (Part C);
- the `state_mutators` gate field (D7, Part C);
- changing the State beyond `liveCopy` replacing `repsUnitLabel`, or any Event, `DialogState`
  variant, `BottomSheetState` variant or UI model;
- changing core:ui:kit beyond the one new file, or any module but kit and live-workout;
- changing the watch-write path's ordering or coverage (wear-live-sync.md §6.4).

## B2. Measured baseline (at `c3026c3ce`)

Paths are relative to `feature/live-workout/src/main/kotlin/io/github/stslex/workeeper/feature/live_workout/`
unless they start with `feature/`, `core/` or `documentation/`.

### B2.1 Copy reads

| Read | Count | Where |
| --- | ---: | --- |
| `resourceWrapper.getString(` | 36 | `ClickHandler` 15, `DialogClickHandler` 5, `ExercisePickerHandler` 4, `LiveWorkoutMapper` 12 |
| … its key expressions | 32 | 30 direct keys, `type.msgRes` (3 calls, the three `sendError`s), `copy.toastRes` (1 call, the delete-exercise toast) |
| CMP's top-level `getString(Res.string.…)` | 1 | `CommonHandler` line 125, kit's `core_ui_kit_plan_editor_unit_reps` |
| composition reads | 50 | 47 Android `stringResource(R.string.…)`, 3 CMP `stringResource(Res.string.…)` in `LiveSetRow`; Part B changes none |

Of the 30 direct keys, 26 move into the copy. The other four stay in launch bodies:
`finish_success` (the finish launch's `onSuccess`), `toast_exercise_added`, `picker_create_format`
and `picker_no_match_format` (the picker's launches). The copy adds the two toast keys
`copy.toastRes` named, the two error keys of the synchronous `sendError`s, and kit's reps unit:

| # | Field | Key (`feature_live_workout_` unless kit's) | Read at the base | EN | RU |
| ---: | --- | --- | --- | --- | --- |
| 1 | `trainingNamePlaceholder` | `training_name_placeholder` | `LiveWorkoutMapper` (presentation, finish stats) | Untitled | Без названия |
| 2 | `statusSkipped` | `status_skipped` | `LiveWorkoutMapper.toStatusLabel` | skipped | пропущено |
| 3 | `statusNoPlan` | `status_no_plan` | `LiveWorkoutMapper.toStatusLabel` | no plan | плана нет |
| 4 | `repsUnit` | kit's `core_ui_kit_plan_editor_unit_reps` | `CommonHandler`, CMP's top-level `getString` | reps | повт |
| 5 | `metaTemplate` | `meta_format` | `LiveWorkoutMapper.toHeaderMetaLabel` | `%1$d of %2$d exercises · %3$d of %4$d sets` | `%1$d из %2$d упражнений · %3$d из %4$d подходов` |
| 6 | `metaSkippedTemplate` | `meta_skipped_format` | the same | `%1$s · %2$d skipped` | `%1$s · пропущено %2$d` |
| 7 | `resetTitle` | `reset_title` | `ClickHandler.processResetSetsAsk`, in its State lambda | Reset sets? | Сбросить подходы? |
| 8 | `resetBody` | `reset_body` | the same | All logged sets for this exercise will be deleted. This cannot be undone. | Все записанные подходы этого упражнения будут удалены. Это нельзя отменить. |
| 9 | `resetConfirm` | `reset_confirm` | the same | Reset | Сбросить |
| 10 | `resetDismiss` | `reset_dismiss` | the same | Cancel | Отмена |
| 11 | `emptyFinishDiscard` | `empty_finish_discard` | `ClickHandler.processFinishClick`, in its State lambda | Discard | Удалить |
| 12 | `emptyFinishContinue` | `empty_finish_continue` | the same | Continue edit | Продолжить |
| 13 | `cancelTitle` | `cancel_title` | `ClickHandler.processCancelClick`, in its State lambda | Cancel session? | Отменить сессию? |
| 14 | `cancelBody` | `cancel_body` | the same | All progress will be deleted. This cannot be undone. | Весь прогресс будет удалён. Это нельзя отменить. |
| 15 | `cancelConfirm` | `cancel_confirm` | the same | Cancel | Отменить |
| 16 | `cancelDismiss` | `cancel_dismiss` | the same | Keep going | Продолжить |
| 17 | `deleteSessionUnnamed` | `delete_session_unnamed` | `ClickHandler.processDeleteSessionMenuClick`, in its State lambda | Workout | Тренировка |
| 18 | `deleteSessionProgressTemplate` | `delete_session_progress_format` | the same | `%1$d of %2$d exercises done · %3$d sets logged` | `%1$d из %2$d упражнений · %3$d подходов записано` |
| 19 | `finishNameLabel` | `finish_name_label` | `LiveWorkoutMapper.toFinishStats` | Workout name | Название тренировки |
| 20 | `finishNameRequired` | `finish_name_required` | `DialogClickHandler`, `processFinishConfirm` and `processFinishNameChange` | Name is required | Введите название |
| 21 | `finishSetsCountTemplate` | `finish_stat_sets_count` | `LiveWorkoutMapper.toFinishStats` | `%1$d` | `%1$d` |
| 22 | `finishExercisesTemplate` | `finish_stat_exercises_format` | `LiveWorkoutMapper.formatExerciseSummary` | `%1$d of %2$d done` | `%1$d из %2$d` |
| 23 | `finishExercisesWithSkippedTemplate` | `finish_stat_exercises_with_skipped_format` | the same | `%1$d of %2$d done · %3$d skipped` | `%1$d из %2$d · %3$d пропущено` |
| 24 | `finishPrWeightedTemplate` | `finish_pr_weighted_format` | `LiveWorkoutMapper.formatPrLabel` | `%1$s × %2$d` | `%1$s × %2$d` |
| 25 | `finishPrWeightlessTemplate` | `finish_pr_weightless_format` | the same | `%1$d reps` | `%1$d повт` |
| 26 | `toastSetAdded` | `toast_set_added` | `ClickHandler.processAddSet` | Set added | Подход добавлен |
| 27 | `toastSetRemoved` | `toast_set_removed` | `ClickHandler.processRemoveLastSet` | Set removed | Подход удалён |
| 28 | `toastExerciseRemovedTemplate` | `toast_exercise_removed` | `DialogClickHandler.processDeleteExerciseConfirm`, through `copy.toastRes` | `“%1$s” removed from plan` | `«%1$s» удалено из плана` |
| 29 | `toastExerciseRemovedFromWorkoutTemplate` | `toast_exercise_removed_from_workout` | the same | `“%1$s” removed from workout` | `«%1$s» удалено из тренировки` |
| 30 | `errorInvalidReps` | `error_invalid_reps` | `ClickHandler.processSetMarkDone`, `sendError(InvalidReps)` | Reps must be greater than 0 | Повторения должны быть больше 0 |
| 31 | `errorFinishFailed` | `error_finish_failed` | `DialogClickHandler.processFinishConfirm`'s missing-training path, `sendError(FinishFailed)` | Could not finish session | Не удалось завершить сессию |

The values are the catalogs' text as Android returns it; none carries an escape, an entity, a `%%`
or a bare `%d`. The ten templates are rows 5, 6, 18 and 21–25, 28 and 29.

### B2.2 State lambdas

Method: the topology gate's own functions, imported from
`.github/scripts/assert_kmp_ui_source_topology.py`: `braced_call_bodies` for every `updateState {` and
`updateStateImmediate {` in `mvi/handler/`, `compact`, `strip_kotlin_comments`, `calls_function`,
`FEATURE_STATE_LAMBDA_SIDE_EFFECTS`, `FEATURE_STATE_LAMBDA_RESOLVERS` and `copy_resolving_functions`.
"Module reach" runs `copy_resolving_functions` over the whole module's comment-stripped text and
counts a lambda that calls one of those functions, or names it as `::name`.

| | Lambdas | Without `.copy(` | Holding tokens (occurrences) | Resolver references | Same-file helper calls | Module reach |
| --- | ---: | ---: | --- | ---: | ---: | ---: |
| at the base | 61 | 11 | 4 (24) | 0 | 0 | 13 |
| at exit (prototype) | 61 | 11 | 0 | 0 | 0 | 0 |

The 13 at the base: `ClickHandler` 7 (`applySetMarked`, `recomputeStatuses`, `applySetUnchecked` × 2,
`applyAddSet`, `applyRemoveLastSet`, `applySkipped`), `CommonHandler` 2 (`applyExternalWrite`, one
by call and one by `::`), `DialogClickHandler` 2 (`recomputeStatuses`, `applyResetSets`),
`ExercisePickerHandler` 1 and `PendingUndoOps` 1 (`recomputeStatuses`). The 11 without `.copy(`
return `LiveSetMutator`'s State by design (F3) and stay so; D7 is Part C's.

### B2.3 Catalogs

Each locale holds 96 strings and 2 plurals. The dead keys:
`feature_live_workout_delete_session` (EN line 100, RU line 91) and
`feature_live_workout_remove_set` (EN line 127, RU line 113), each `tools:ignore="…UnusedResources"`
and referenced nowhere in the repository.

### B2.4 Tests

`:feature:live-workout:testDebugUnitTest` runs 24 suites and 227 cases. The four golden classes
(30 methods, 60 cases, 60 PNGs) run under `verifyPaparazziDebug`, not in that count.

| Suite | Cases | | Suite | Cases |
| --- | ---: | --- | --- | ---: |
| `LiveWorkoutInteractorImplTest` | 29 | | `UnfilledSetTest` | 6 |
| `DialogClickHandlerTest` | 28 | | `LiveWorkoutMapperReloadTest` | 5 |
| `LiveSetMutatorTest` | 27 | | `DeleteExerciseCopyMapperTest` | 4 |
| `CommonHandlerExternalWritesTest` | 24 | | `LiveWorkoutStateRouteArgTest` | 4 |
| `LiveWorkoutMapperTest` | 20 | | `NavigationHandlerTest` | 4 |
| `ClickHandlerTest` | 18 | | `CommonHandlerTest` | 3 |
| `ExercisePickerHandlerTest` | 12 | | `CommonHandlerComposeResourceTest` | 1 |
| `LiveSetDraftBehaviorTest` | 10 | | `CommonHandlerComposeResourceRuTest` | 1 |
| `LiveSetVisibleRowsResolverTest` | 8 | | `InputHandlerTest` | 1 |
| `ExerciseDoneRuleTest` | 7 | | `LiveSetRowOverflowGateTest` | 1 |
| `ExternalWriteCoverageTest` | 6 | | `LiveSetRowSemanticsTest` | 1 |
| `StateStatusMapperTest` | 6 | | `SetColumnAlignmentGateTest` | 1 |

core:ui:kit's androidHostTest (48 files) and iosTest (2 identities, pinned by
`assert_kmp_ios_smoke.py`) are measured at entry (Section B6.1); kit has no commonTest.

### B2.5 Other facts

- `System.currentTimeMillis()`: 3 calls, `CommonHandler` lines 121 and 189 and
  `domain/LiveWorkoutInteractorImpl.kt` line 324.
- `ClickHandler` and `DialogClickHandler` take `pickerHandler: ExercisePickerHandler`; nothing else
  injects it. `LiveWorkoutGraph` (di) has 2 `@Binds`. `AppGraph.Factory.create` provides an
  unqualified `Context` (`@Provides applicationContext: Context`), which the feature's graph
  extension inherits; settings' `BackupClickHandler` injects it the same way.
- `ErrorType` producers: `InvalidReps` 1 (synchronous, `processSetMarkDone`); `FinishFailed` 2 (one
  synchronous, one in the finish launch's `onError`); `Unknown` 0; every other value only in launch
  bodies. `sendError` has 19 calls: those 2 synchronous ones, and 17 in launch bodies
  (`ClickHandler` 9, `DialogClickHandler` 6, `ExercisePickerHandler` 2).
- The CMP 1.11.1 rule (CB4) is what `ComposeStringResolver`'s argument path applies, and its
  no-argument path returns the catalog text verbatim (its KDoc).
- `AndroidResourceWrapper.getString(id, *args)` formats even with no arguments, so a `%1$d` template
  read through it throws `MissingFormatArgumentException`; `Context.getString(id)` does not format.
- No module outside live-workout references `State.repsUnitLabel`, `DeleteExerciseCopyUiModel`'s
  resource ids, the module's `ErrorType`, `StateStatusMapper` or `ExercisePickerHandler`, in any
  source set (app/app's androidTest and test included).
- detekt: `LongMethod` 120 (tests excluded), `LongParameterList` 9 for constructors (data classes
  ignored), `TooManyFunctions` 30, `MaxLineLength` 120 (tests included). Android Lint runs with
  `checkAllWarnings`, `warningsAsErrors` and an empty baseline (`lint-rules/lint-baseline.xml`).

### B2.6 Suppressions and PNGs

Inherited suppressions, which stay byte-identical:

- production, 12 in 10 files: `@Suppress("UNCHECKED_CAST")` (di/LiveWorkoutFeature.kt),
  `@Suppress("TooManyFunctions")` (domain/LiveWorkoutInteractor.kt,
  domain/mapper/LiveWorkoutDomainMapper.kt, mvi/mapper/LiveWorkoutMapper.kt),
  `@Suppress("TooManyFunctions", "LongParameterList")` (domain/LiveWorkoutInteractorImpl.kt),
  `@Suppress("TooManyFunctions", "LongMethod", "LargeClass")` (mvi/handler/ClickHandler.kt),
  `@Suppress("ComplexInterface")` × 2 and `@Suppress("MviActionNamingRule")`
  (mvi/store/LiveWorkoutStore.kt), `@Suppress("LongMethod", "CyclomaticComplexMethod")`
  (ui/LiveWorkoutGraph.kt), `@SuppressLint("ModifierParameter")` (ui/LiveWorkoutScreen.kt) and
  `@Suppress("LongParameterList")` (ui/components/LiveSetRow.kt);
- tests, 7 in 5 files: `@Suppress("MaximumLineLength")` (LiveWorkoutInteractorImplTest),
  `@Suppress("LongMethod")` (SessionScreenGoldenTest) and `@Suppress("UNCHECKED_CAST")` × 2
  (ClickHandlerTest), × 1 (CommonHandlerExternalWritesTest), × 2 (DialogClickHandlerTest).

The module's 60 PNGs have the blob set
`53221eab422589d95e416c6de0b32e3b2d4d0112a8e70a1b2344cf171f17bb67`: the sha256 of
`git ls-tree -r <rev> -- feature/live-workout | grep '\.png$' | awk '{print $3}' | sort`.

## B3. Contract

Normative code, printed at each file's own indentation. KDoc and comment wording is free unless
quoted, but a KDoc must not name a payload token of Section B5.3's X8 in the model files. The
anchors of Section B5 are lines of this section, and each must match exactly once in its file.
Imports follow the repository's layout (lexicographic, `java.`, `javax.` and `kotlin.` last) and
gain or lose only what the code below needs.

### B3.1 The filler (commit 1)

New file `core/ui/kit/src/commonMain/kotlin/io/github/stslex/workeeper/core/ui/kit/strings/StringTemplates.kt`:

```kotlin
// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.core.ui.kit.strings

/**
 * Fills a catalog template read verbatim (a [StringResolver] read with no arguments) as Compose
 * resources' `getString(resource, *formatArgs)` fills it: every positional `%N$d` or `%N$s` becomes
 * `formatArgs[N - 1].toString()`, and nothing else in the template changes (Compose resources 1.11.1,
 * `replaceWithArgs`). A missing argument throws [IndexOutOfBoundsException] there and here.
 *
 * Pure and synchronous, so a State lambda or a click path may call it: copy resolved once into
 * State can still take arguments (kmp-phase-7-16-live-workout-feature.md, D5).
 */
fun String.fillTemplate(vararg formatArgs: Any): String =
    POSITIONAL_PLACEHOLDER.replace(this) { match ->
        // GUARD: the lambda inserts the argument literally; a replacement string would read `$1` or
        // a backslash in a user-entered name as a group reference.
        formatArgs[match.groupValues[1].toInt() - 1].toString()
    }

private val POSITIONAL_PLACEHOLDER = Regex("""%(\d+)\$[ds]""")
```

Nothing else in kit changes.

### B3.2 The picker seam (commit 2)

New file `mvi/handler/ExercisePicker.kt`:

```kotlin
// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.live_workout.mvi.handler

import io.github.stslex.workeeper.core.ui.plan_editor.model.ExercisePickerAction

/**
 * SP5, on Phase 7.13 D2-A's template: what [ClickHandler] and [DialogClickHandler] need from
 * [ExercisePickerHandler], bound in `LiveWorkoutGraph`, so their tests record the calls on a fake.
 */
interface ExercisePicker {

    /** Opens the picker with the library list, minus exercises already in the session. */
    fun open()

    /** Routes one picker sheet action. */
    operator fun invoke(action: ExercisePickerAction)
}
```

`mvi/handler/ExercisePickerHandler.kt`, its declaration and the two entry points; the bodies are
unchanged:

```kotlin
/**
 * Owns the picker sheet's transitions and the add-to-session + lazy PR-fetch flow. Public only
 * because `LiveWorkoutGraph` binds it as [ExercisePicker] (SP5).
 */
@SingleIn(LiveWorkoutScope::class)
class ExercisePickerHandler @Inject internal constructor(
    private val interactor: LiveWorkoutInteractor,
    private val resourceWrapper: ResourceWrapper,
    private val statusMapper: StateStatusMapper,
    store: LiveWorkoutHandlerStore,
) : ExercisePicker, LiveWorkoutHandlerStore by store {
```

```kotlin
    override operator fun invoke(action: ExercisePickerAction) {
```

```kotlin
    override fun open() {
```

`di/LiveWorkoutGraph.kt` gains, after the `bindHandlerStore` binding, with its two imports:

```kotlin
    /**
     * SP5: ClickHandler and DialogClickHandler reach the picker through the seam; the picker
     * handler is the scope's one instance.
     */
    @Binds
    val ExercisePickerHandler.bindExercisePicker: ExercisePicker
```

In `ClickHandler` and `DialogClickHandler` the constructor parameter becomes:

```kotlin
    private val pickerHandler: ExercisePicker,
```

### B3.3 The clock (commit 2)

The three calls become `Clock.System.now().toEpochMilliseconds()`, with `import kotlin.time.Clock`
in each of the two files:

- `CommonHandler.toLoadedState()`: `nowMillis = Clock.System.now().toEpochMilliseconds(),`
- `CommonHandler.startTimer()`: `val now = Clock.System.now().toEpochMilliseconds()`
- `LiveWorkoutInteractorImpl` (the finish): `val finishedAt = Clock.System.now().toEpochMilliseconds()`

### B3.4 The dead keys (commit 2)

Exactly these two lines leave `feature/live-workout/src/main/res/values/strings.xml`:

```xml
    <string name="feature_live_workout_delete_session" tools:ignore="DuplicateStrings,UnusedResources">Delete</string>
    <string name="feature_live_workout_remove_set" tools:ignore="UnusedResources">Remove set</string>
```

and exactly these two leave `values-ru/strings.xml`:

```xml
    <string name="feature_live_workout_delete_session" tools:ignore="DuplicateStrings,UnusedResources">Удалить</string>
    <string name="feature_live_workout_remove_set" tools:ignore="UnusedResources">Удалить подход</string>
```

Each catalog's diff is that one pair of deleted lines (numstat `0 2`); no other byte changes.

### B3.5 The copy model (commit 3)

New file `mvi/model/LiveWorkoutCopy.kt`: a public `@Stable data class LiveWorkoutCopy` (public,
because the public `State` holds it) with the 31 `val … : String` fields of Section B2.1, in that
table's order, and a `companion object` whose `val EMPTY` sets every field to `""`. Its KDoc says
what reads it and that each `…Template` keeps its placeholders for kit's `fillTemplate`. Nothing
else is in the file.

`mvi/store/LiveWorkoutStore.kt`: `State.repsUnitLabel` and its KDoc are replaced, in the same
position, by

```kotlin
        /**
         * D5: the screen's copy outside composition, which the status pipeline, the dialogs, the
         * toasts and the synchronous errors read, resolved at each load's suspend boundary. Empty
         * before the first load, whose State replaces every label derived from it.
         */
        val liveCopy: LiveWorkoutCopy = LiveWorkoutCopy.EMPTY,
```

`State.create` is unchanged.

### B3.6 The reader (commit 3)

New file `mvi/handler/LiveWorkoutCopyReader.kt`:

```kotlin
// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.live_workout.mvi.handler

import android.content.Context
import androidx.annotation.StringRes
import dev.zacsweers.metro.Inject

/**
 * D1-A, temporary: reads a live-workout string verbatim, placeholders included, through the app
 * Context's non-formatting `getString(id)`. `ResourceWrapper` cannot, because it formats even with
 * no arguments, so a `%1$d` template throws there. Phase 7.16 PR-M's move commit deletes this class
 * for `StringResolver`'s no-argument path.
 */
@Inject
internal class LiveWorkoutCopyReader(
    private val context: Context,
) {

    fun read(@StringRes id: Int): String = context.getString(id)
}
```

### B3.7 The load (commit 3)

`CommonHandler`'s constructor takes the reader and the resolver in place of the wrapper:

```kotlin
internal class CommonHandler @Inject constructor(
    private val interactor: LiveWorkoutInteractor,
    private val copyReader: LiveWorkoutCopyReader,
    private val stringResolver: StringResolver,
    private val setMutator: LiveSetMutator,
    private val storeDispatchers: StoreDispatchers,
    store: LiveWorkoutHandlerStore,
) : Handler<Action.Common>, LiveWorkoutHandlerStore by store {
```

`toLoadedState()` resolves the copy, and `resolveCopy()` is new, directly below it:

```kotlin
    private suspend fun SessionSnapshotDomain.toLoadedState(): State = toState(
        nowMillis = Clock.System.now().toEpochMilliseconds(),
        // D5: the copy resolves here, at the load's suspend boundary, so the mapper and every
        // State lambda after it stay pure synchronous transformations.
        liveCopy = resolveCopy(),
    )

    /** D5: each field from its own key, read verbatim, so a template keeps its placeholders. */
    private suspend fun resolveCopy(): LiveWorkoutCopy = LiveWorkoutCopy(
        trainingNamePlaceholder = copyReader.read(R.string.feature_live_workout_training_name_placeholder),
        statusSkipped = copyReader.read(R.string.feature_live_workout_status_skipped),
        statusNoPlan = copyReader.read(R.string.feature_live_workout_status_no_plan),
        repsUnit = stringResolver.getString(Res.string.core_ui_kit_plan_editor_unit_reps),
        metaTemplate = copyReader.read(R.string.feature_live_workout_meta_format),
        metaSkippedTemplate = copyReader.read(R.string.feature_live_workout_meta_skipped_format),
        resetTitle = copyReader.read(R.string.feature_live_workout_reset_title),
        resetBody = copyReader.read(R.string.feature_live_workout_reset_body),
        resetConfirm = copyReader.read(R.string.feature_live_workout_reset_confirm),
        resetDismiss = copyReader.read(R.string.feature_live_workout_reset_dismiss),
        emptyFinishDiscard = copyReader.read(R.string.feature_live_workout_empty_finish_discard),
        emptyFinishContinue = copyReader.read(R.string.feature_live_workout_empty_finish_continue),
        cancelTitle = copyReader.read(R.string.feature_live_workout_cancel_title),
        cancelBody = copyReader.read(R.string.feature_live_workout_cancel_body),
        cancelConfirm = copyReader.read(R.string.feature_live_workout_cancel_confirm),
        cancelDismiss = copyReader.read(R.string.feature_live_workout_cancel_dismiss),
        deleteSessionUnnamed = copyReader.read(R.string.feature_live_workout_delete_session_unnamed),
        deleteSessionProgressTemplate = copyReader.read(R.string.feature_live_workout_delete_session_progress_format),
        finishNameLabel = copyReader.read(R.string.feature_live_workout_finish_name_label),
        finishNameRequired = copyReader.read(R.string.feature_live_workout_finish_name_required),
        finishSetsCountTemplate = copyReader.read(R.string.feature_live_workout_finish_stat_sets_count),
        finishExercisesTemplate = copyReader.read(R.string.feature_live_workout_finish_stat_exercises_format),
        finishExercisesWithSkippedTemplate = copyReader.read(
            R.string.feature_live_workout_finish_stat_exercises_with_skipped_format,
        ),
        finishPrWeightedTemplate = copyReader.read(R.string.feature_live_workout_finish_pr_weighted_format),
        finishPrWeightlessTemplate = copyReader.read(R.string.feature_live_workout_finish_pr_weightless_format),
        toastSetAdded = copyReader.read(R.string.feature_live_workout_toast_set_added),
        toastSetRemoved = copyReader.read(R.string.feature_live_workout_toast_set_removed),
        toastExerciseRemovedTemplate = copyReader.read(R.string.feature_live_workout_toast_exercise_removed),
        toastExerciseRemovedFromWorkoutTemplate = copyReader.read(
            R.string.feature_live_workout_toast_exercise_removed_from_workout,
        ),
        errorInvalidReps = copyReader.read(R.string.feature_live_workout_error_invalid_reps),
        errorFinishFailed = copyReader.read(R.string.feature_live_workout_error_finish_failed),
    )
```

The imports lose `ResourceWrapper` and `org.jetbrains.compose.resources.getString`, and gain kit's
`StringResolver`, the feature's `R` and `LiveWorkoutCopy`. Nothing else in `CommonHandler` changes:
`processInit`'s `onError = { abandonUnloadedSession() }` is CB2's failure path, and `coveredLoad`,
the subscription, the timer and the coverage are untouched.

### B3.8 The pipeline (commit 3)

`mvi/mapper/StateStatusMapper.kt` loses its constructor and its `ResourceWrapper` import:

```kotlin
/** D5: the status pipeline; it reads copy only from the State it transforms. */
@Inject
@SingleIn(LiveWorkoutScope::class)
internal class StateStatusMapper {
```

and `recomputeStatuses` ends `return state.copy(exercises = refreshed).withPresentation()`.

`mvi/mapper/LiveWorkoutMapper.kt` loses `ResourceWrapper` and the feature's `R`, and gains kit's
`fillTemplate` and `LiveWorkoutCopy`. Every function that took `resourceWrapper` takes the copy, or
reads its State's:

| Function | After |
| --- | --- |
| `SessionSnapshotDomain.toState` | parameters `(nowMillis: Long, liveCopy: LiveWorkoutCopy)`; the State gets `liveCopy = liveCopy,` where it had `repsUnitLabel = repsUnitLabel,`, and ends `).withPresentation()` |
| `State.withPresentation()` | no parameter; `statusLabel = exercise.toStatusLabel(withVisible.liveCopy),`; `trainingNameLabel = trainingName.ifBlank { liveCopy.trainingNamePlaceholder },`; `headerMetaLabel = presentedExercises.toHeaderMetaLabel(liveCopy),` |
| `toHeaderMetaLabel(copy: LiveWorkoutCopy)` | `val base = copy.metaTemplate.fillTemplate(` with the same four counts, in order; the skipped line `copy.metaSkippedTemplate.fillTemplate(base, skippedCount)` |
| `State.toFinishStats()` | no parameter; `formatExerciseSummary(copy = liveCopy, …)`; `setsLoggedLabel = liveCopy.finishSetsCountTemplate.fillTemplate(setsLogged),`; `computeNewPersonalRecords()`; `nameLabel = liveCopy.finishNameLabel,`; `namePlaceholder = liveCopy.trainingNamePlaceholder,` |
| `State.computeNewPersonalRecords()` | no parameter; `it.toNewPrEntry(preSessionPrSnapshot, liveCopy)` |
| `toNewPrEntry(snapshot, copy: LiveWorkoutCopy)` | passes `copy = copy` to `formatPrLabel` |
| `formatPrLabel(weight, reps, type, copy: LiveWorkoutCopy)` | WEIGHTED: `copy.finishPrWeightedTemplate.fillTemplate(weightLabel, reps)`; WEIGHTLESS: `copy.finishPrWeightlessTemplate.fillTemplate(reps)` |
| `toStatusLabel(copy: LiveWorkoutCopy)` | the three branches below |
| `formatExerciseSummary(copy: LiveWorkoutCopy, doneCount, totalCount, skippedCount)` | `copy.finishExercisesWithSkippedTemplate.fillTemplate(doneCount, totalCount, skippedCount)`, else `copy.finishExercisesTemplate.fillTemplate(doneCount, totalCount)` |

`toStatusLabel`'s branches:

```kotlin
            status == ExerciseStatusUiModel.SKIPPED -> copy.statusSkipped

            planSets.isEmpty() -> copy.statusNoPlan

            else -> planSets.toPlanSubLabel(exerciseType, copy.repsUnit)
```

`toPlanSubLabel`, `formatPrWeight` and every other function are unchanged. `LiveSetMutator`,
`LiveSetRowsResolver` and `ExerciseDoneRule` do not change.

### B3.9 The click paths (commit 3)

`ClickHandler` (it loses the feature's `R` import and gains `fillTemplate` and
`ErrorTypeMapper.messageRes`):

| Path | After |
| --- | --- |
| `processSetMarkDone`, no reps | `sendEvent(Event.ShowError(message = current.liveCopy.errorInvalidReps))` in place of `sendError(ErrorType.InvalidReps)`, before the `return` |
| `processAddSet` | `message = prior.liveCopy.toastSetAdded,` |
| `processRemoveLastSet` | `message = prior.liveCopy.toastSetRemoved,` |
| `processResetSetsAsk` | the one `updateState`, its lambda reading `it.liveCopy`: the four lines below |
| `processFinishClick`, empty | `confirmLabel = it.liveCopy.emptyFinishDiscard,` and `dismissLabel = it.liveCopy.emptyFinishContinue,` |
| `processFinishClick`, logged | `val stats = current.toFinishStats()` |
| `processCancelClick` | the one `updateState`, its lambda reading `it.liveCopy`: the four lines below |
| `processDeleteSessionMenuClick` | in its one `updateState`: `?: state.liveCopy.deleteSessionUnnamed`, and the progress label below |
| `sendError` | `message = resourceWrapper.getString(type.messageRes()),` |

```kotlin
                    title = it.liveCopy.resetTitle,
                    body = it.liveCopy.resetBody,
                    confirmLabel = it.liveCopy.resetConfirm,
                    dismissLabel = it.liveCopy.resetDismiss,
```

```kotlin
                    title = it.liveCopy.cancelTitle,
                    body = it.liveCopy.cancelBody,
                    confirmLabel = it.liveCopy.cancelConfirm,
                    dismissLabel = it.liveCopy.cancelDismiss,
```

```kotlin
            val progressLabel = state.liveCopy.deleteSessionProgressTemplate.fillTemplate(
                state.doneCount,
                state.totalCount,
                state.setsLogged,
            )
```

A one-line comment naming D6 may sit above each changed write. Every other line of `ClickHandler`
is unchanged; the launches, haptics and undo flows keep their order.

`DialogClickHandler` (it keeps the feature's `R` for `finish_success`, and gains `fillTemplate` and
`ErrorTypeMapper.messageRes`):

| Path | After |
| --- | --- |
| `processDeleteExerciseConfirm` | the toast below |
| `processFinishConfirm`, a required name left blank | `val requiredError = current.liveCopy.finishNameRequired` |
| `processFinishConfirm`, a required name with no training | `sendEvent(Event.ShowError(message = current.liveCopy.errorFinishFailed))` in place of `sendError(ErrorType.FinishFailed)`, before the `return` |
| `processFinishNameChange` | the `requiredError` local goes; inside the one `updateState`: `nameError = latest.liveCopy.finishNameRequired.takeIf { trimmed.isBlank() },` |
| `sendError` | `message = resourceWrapper.getString(type.messageRes()),` |

```kotlin
                message = DeleteExerciseCopyMapper.toastTemplate(copy, prior.liveCopy)
                    .fillTemplate(exercise.exerciseName.truncateForToast()),
```

The finish launch's `onError` keeps `sendError(ErrorType.FinishFailed)`, and its `onSuccess` keeps
`resourceWrapper.getString(R.string.feature_live_workout_finish_success)`.

`ExercisePickerHandler.sendError` reads `resourceWrapper.getString(type.messageRes())`. Its three
other reads are unchanged.

### B3.10 The id models (commit 3)

`mvi/model/ErrorType.kt` becomes a plain enum, `InvalidReps` gone (CB6):

```kotlin
/** SP4: what failed; `ErrorTypeMapper` maps each value to its catalog key. */
internal enum class ErrorType {
    SetSaveFailed,
    SetDeleteFailed,
    ResetFailed,
    SkipFailed,
    FinishMissingSession,
    FinishFailed,
    CancelFailed,
    PlanSaveFailed,
    TrainingNameSaveFailed,
    AddExerciseFailed,
    CreateInlineExerciseFailed,
    DiscardSessionFailed,
    Unknown,
}
```

New file `mvi/mapper/ErrorTypeMapper.kt`: `internal object ErrorTypeMapper` with one member,
`@StringRes fun ErrorType.messageRes(): Int = when (this) { … }`, mapping each of the 13 values to
the key its base constructor argument named (`SetSaveFailed -> R.string.feature_live_workout_error_set_save_failed`,
and so on to `Unknown -> R.string.feature_live_workout_error_unknown`).

`mvi/model/DeleteExerciseCopyUiModel.kt` becomes:

```kotlin
/** SP4: which removal copy the delete sheet and its toast show; `DeleteExerciseCopyMapper` maps it. */
internal enum class DeleteExerciseCopyUiModel {
    ADHOC_WORKOUT,
    TEMPLATE_ADDITION,
    TEMPLATE_PLANNED,
}
```

`mvi/mapper/DeleteExerciseCopyMapper.kt` keeps `map(…)` as it is and gains four members:

```kotlin
    /** SP4: the delete sheet's title, read in composition. */
    @StringRes
    fun DeleteExerciseCopyUiModel.titleRes(): Int = when (this) {
        DeleteExerciseCopyUiModel.ADHOC_WORKOUT -> R.string.feature_live_workout_delete_workout_title
        DeleteExerciseCopyUiModel.TEMPLATE_ADDITION,
        DeleteExerciseCopyUiModel.TEMPLATE_PLANNED,
        -> R.string.feature_live_workout_delete_plan_title
    }

    /** SP4: the delete sheet's body, a template the sheet fills with the exercise name. */
    @StringRes
    fun DeleteExerciseCopyUiModel.bodyRes(): Int = when (this) {
        DeleteExerciseCopyUiModel.ADHOC_WORKOUT -> R.string.feature_live_workout_delete_workout_body
        DeleteExerciseCopyUiModel.TEMPLATE_ADDITION -> R.string.feature_live_workout_delete_plan_body_added
        DeleteExerciseCopyUiModel.TEMPLATE_PLANNED -> R.string.feature_live_workout_delete_plan_body_planned
    }

    /** SP4: the delete sheet's confirm label. */
    @StringRes
    fun DeleteExerciseCopyUiModel.confirmRes(): Int = when (this) {
        DeleteExerciseCopyUiModel.ADHOC_WORKOUT -> R.string.feature_live_workout_delete_workout_confirm
        DeleteExerciseCopyUiModel.TEMPLATE_ADDITION,
        DeleteExerciseCopyUiModel.TEMPLATE_PLANNED,
        -> R.string.feature_live_workout_delete_plan_confirm
    }

    /** SP4 and D6: the removal toast's template, taken from the copy in State. */
    fun toastTemplate(
        copy: DeleteExerciseCopyUiModel,
        liveCopy: LiveWorkoutCopy,
    ): String = when (copy) {
        DeleteExerciseCopyUiModel.ADHOC_WORKOUT -> liveCopy.toastExerciseRemovedFromWorkoutTemplate
        DeleteExerciseCopyUiModel.TEMPLATE_ADDITION,
        DeleteExerciseCopyUiModel.TEMPLATE_PLANNED,
        -> liveCopy.toastExerciseRemovedTemplate
    }
```

The mapping is the base enum's, entry by entry. `ui/components/SessionSheets.kt` imports the three
`…Res` members and calls them: `stringResource(copy.titleRes())`,
`stringResource(copy.bodyRes(), exercise.exerciseName)` and `stringResource(copy.confirmRes())`.
Nothing else in the sheet changes, so no PNG can.

### B3.11 What changes, path by path

| Path | Base | After Part B |
| --- | --- | --- |
| the status pipeline, in the 13 lambdas (F1) | `ResourceWrapper` reads in `LiveWorkoutMapper`, inside the compare-and-set | reads of `State.liveCopy` and `fillTemplate`, pure |
| the four dialogs (reset, empty finish, cancel, delete session) | `ResourceWrapper` reads inside the State lambda | the copy, in the same one write |
| the finish dialog | `toFinishStats(resourceWrapper)` before the write | `toFinishStats()` from the copy, before the same write |
| the set and exercise toasts | `ResourceWrapper` reads on the click path | the copy, filled with the truncated name where it takes one |
| the two synchronous errors | `sendError` → `ResourceWrapper` | `Event.ShowError` with the copy's text |
| the 17 launch-body errors and the 3 other launch reads | `ResourceWrapper` on use | unchanged (`messageRes()` names the key) |
| kit's reps unit | CMP's top-level `getString` at the load | `StringResolver` at the load |
| a load | one catalog read (the reps unit) | 31 reads into `liveCopy` (CB2) |
| the picker | `ExercisePickerHandler` injected into two handlers | `ExercisePicker`, bound to the same scoped instance |
| clock reads | `System.currentTimeMillis()` | `Clock.System.now().toEpochMilliseconds()`, the same value |

## B4. Tests

JUnit 5 throughout, as each module's host tests already are. Kit's new suites live in its
androidHostTest; live-workout's in its `src/test`. Robolectric suites follow the modules' existing
form (`@ExtendWith(RobolectricExtension::class)`, `@Config(sdk = [33])`, a second class with
`qualifiers = "ru"`, because the extension forbids method-level `@Config`), and read the
application through `org.robolectric.RuntimeEnvironment.getApplication()`.

### B4.1 Kit (commit 1)

`core/ui/kit/src/androidHostTest/kotlin/io/github/stslex/workeeper/core/ui/kit/strings/StringTemplatesTest.kt`,
`internal class StringTemplatesTest`, plain JVM:

| # | Name | Asserts |
| --- | --- | --- |
| T1 | `fillsPositionalPlaceholdersInOrder` | `"%1\$d of %2\$d exercises".fillTemplate(2, 3)` is `"2 of 3 exercises"` |
| T2 | `aPlaceholderMayRepeatOrComeOutOfOrder` | `"%2\$s, %1\$s, %2\$s".fillTemplate("a", "b")` is `"b, a, b"` |
| T3 | `aTemplateWithoutPositionalPlaceholdersComesBackVerbatim` | `"Set added".fillTemplate(5)` is `"Set added"`; `"%d sets".fillTemplate(3)` is `"%d sets"` |
| T4 | `argumentsPrintWithToStringAndInsertLiterally` | `"%1\$s × %2\$d".fillTemplate(102.5, 5)` is `"102.5 × 5"`; `"“%1\$s” removed".fillTemplate("\$1 \\ name")` is `"“\$1 \\ name” removed"` |
| T5 | `aMissingArgumentThrowsAsComposeResourcesDoes` | `"%2\$d of %1\$d".fillTemplate(1)` throws `IndexOutOfBoundsException` (`org.junit.jupiter.api.assertThrows`) |

`core/ui/kit/src/androidHostTest/kotlin/io/github/stslex/workeeper/core/ui/kit/strings/StringTemplatesParityTest.kt`,
Robolectric, two classes and one private helper:

| # | Class | Name |
| --- | --- | --- |
| T6 | `StringTemplatesParityTest` (`@Config(sdk = [33])`) | `fillsTheEnglishCatalogAsTheResolverFormats` |
| T7 | `StringTemplatesParityRuTest` (`@Config(sdk = [33], qualifiers = "ru")`) | `fillsTheRussianCatalogAsTheResolverFormats` |

The helper builds a real `ComposeStringResolver()` and, in `runBlocking`, asserts three rows, each
`resolver.getString(res, *args)` equal to `resolver.getString(res).fillTemplate(*args)`:
`core_ui_kit_discard_session_confirm_title_format` with `("Push Day")`;
`core_ui_kit_active_session_conflict_body_format` with `("Push Day", "12 min")`; the same template
with `(3, 4)`.

### B4.2 live-workout's new identities (commits 2 and 3)

Two new test files, used by the suites:

- `mvi/handler/FakeExercisePicker.kt` (commit 2), under `src/test/kotlin/…/feature/live_workout/`, with
  its package and the `ExercisePickerAction` import:

  ```kotlin
  /** SP5: records what a handler asks of the picker; nothing else happens. */
  internal class FakeExercisePicker : ExercisePicker {

      var opens: Int = 0
          private set

      val actions: MutableList<ExercisePickerAction> = mutableListOf()

      override fun open() {
          opens += 1
      }

      override operator fun invoke(action: ExercisePickerAction) {
          actions += action
      }
  }
  ```

- `mvi/model/LiveWorkoutCopyFixtures.kt` (commit 3): `internal val NAMED_COPY = LiveWorkoutCopy(…)`,
  with a KDoc. Each constant field's value is its field's name (`resetTitle = "resetTitle"`). Each
  template's value is its field's name, a space, and the catalog template's placeholders in the
  catalog's order, separated by spaces: `metaTemplate = "metaTemplate %1\$d %2\$d %3\$d %4\$d"`,
  `metaSkippedTemplate = "metaSkippedTemplate %1\$s %2\$d"`,
  `deleteSessionProgressTemplate = "deleteSessionProgressTemplate %1\$d %2\$d %3\$d"`,
  `finishSetsCountTemplate = "finishSetsCountTemplate %1\$d"`,
  `finishExercisesTemplate = "finishExercisesTemplate %1\$d %2\$d"`,
  `finishExercisesWithSkippedTemplate = "finishExercisesWithSkippedTemplate %1\$d %2\$d %3\$d"`,
  `finishPrWeightedTemplate = "finishPrWeightedTemplate %1\$s %2\$d"`,
  `finishPrWeightlessTemplate = "finishPrWeightlessTemplate %1\$d"`,
  `toastExerciseRemovedTemplate = "toastExerciseRemovedTemplate %1\$s"` and
  `toastExerciseRemovedFromWorkoutTemplate = "toastExerciseRemovedFromWorkoutTemplate %1\$s"`.

The identities. `baseState`, `loggedExercise()` and `doneExercise(status)` are each suite's
existing helpers (`loggedExercise()` is `pe-1`, `Bench Press`, WEIGHTED, one done WORK set at
position 0 of 100.0 × 5, no plan; `doneExercise(…)` has no plan and no sets).

| # | Suite | Name | Arrange → act | Assert |
| --- | --- | --- | --- | --- |
| N1 | `ClickHandlerTest` | `OnResetSets opens its confirm from the copy in one write` | `baseState(loggedExercise())` with `NAMED_COPY` → `OnResetSets("pe-1")` | `ResetSets("resetTitle", "resetBody", "resetConfirm", "resetDismiss", exerciseUuid = "pe-1")`; 1 update, 0 launches |
| N2 | `ClickHandlerTest` | `OnCancelSessionClick opens its confirm from the copy in one write` | the same State → `OnCancelSessionClick` | `CancelSession("cancelTitle", "cancelBody", "cancelConfirm", "cancelDismiss")`; 1 update, 0 launches |
| N3 | `ClickHandlerTest` | `OnFinishClick on an empty session opens the empty-finish dialog from the copy in one write` | `baseState(doneExercise(CURRENT))`, `isAdhoc = true`, `NAMED_COPY` → `OnFinishClick` | `EmptyFinish(canDiscard = true, "emptyFinishDiscard", "emptyFinishContinue")`; 1 update, 0 launches |
| N4 | `ClickHandlerTest` | `OnDeleteSessionMenuClick fills the progress template in one write` | `baseState(loggedExercise())`, `trainingNameLabel = ""`, `doneCount = 1`, `totalCount = 2`, `setsLogged = 3`, `NAMED_COPY` → `OnDeleteSessionMenuClick` | `DeleteDialog("deleteSessionUnnamed", "deleteSessionProgressTemplate 1 2 3")`; 1 update, 0 launches |
| N5 | `ClickHandlerTest` | `OnFinishClick builds the finish dialog from the copy in one write` | `baseState(loggedExercise())`, `doneCount = 1`, `totalCount = 2`, `setsLogged = 3`, `NAMED_COPY`, no PR snapshot → `OnFinishClick` | `exercisesSummaryLabel` `"finishExercisesTemplate 1 2"`, `setsLoggedLabel` `"finishSetsCountTemplate 3"`, the PR labels `["finishPrWeightedTemplate 100 5"]`, `nameLabel` `"finishNameLabel"`, `namePlaceholder` `"trainingNamePlaceholder"`; 1 update, 0 launches |
| N6 | `ClickHandlerTest` | `the set toasts read their messages from the copy` | `statusMapper.recomputeStatuses(baseState(loggedExercise()).copy(liveCopy = NAMED_COPY))`, so the visible rows are resolved → `OnAddSet("pe-1")`, then `OnRemoveLastSet("pe-1")` | `pendingUndo?.message` is `"toastSetAdded"` after the first, `"toastSetRemoved"` after the second |
| N8 | `ClickHandlerTest` | `OnSetMarkDone without reps shows the copy's error without a launch` | `baseState(doneExercise(CURRENT))` with `NAMED_COPY` → `OnSetMarkDone("pe-1", position = 0)` | the `ShowError` events are exactly `["errorInvalidReps"]`; 0 launches |
| N9 | `ClickHandlerTest` | `OnAddExerciseClick opens the picker through the seam` | `baseState(loggedExercise())` → `OnAddExerciseClick` | `pickerHandler.opens` is 1 |
| N7 | `DialogClickHandlerTest` | `OnDeleteExerciseConfirm fills its toast with the truncated name` | `baseState(loggedExercise().copy(exerciseName = "Romanian Deadlift With Pause"))` with `NAMED_COPY`, through the suite's `handler(stateFlow)` → `OnDeleteExerciseConfirm("pe-1")` | `pendingUndo?.message` is `"toastExerciseRemovedTemplate Romanian Deadlift With P…"` (24 characters and `…`) |
| M1 | `StateStatusMapperTest` | `recomputeStatuses labels the cards and the header from the State's copy` | `State.create("session-1", "training-1")`, `isLoading = false`, `NAMED_COPY`, three exercises from the suite's `exercise(…)` helper: `pe-1` SKIPPED with a 60.0 × 8 plan; `pe-2` with no plan; `pe-3` WEIGHTLESS with a 12-rep plan (positions 0, 1, 2) → `mapper.recomputeStatuses(state)` | the status labels `["statusSkipped", "statusNoPlan", "12 repsUnit"]`; `headerMetaLabel` `"metaSkippedTemplate metaTemplate 0 2 0 1 1"`; `trainingNameLabel` `"trainingNamePlaceholder"` |
| R1 | `CommonHandlerComposeResourceTest` | `init resolves the whole copy verbatim from the real catalog` | the suite's `loadWeightlessSessionThroughInit()` | `stateFlow.value.liveCopy` equals `ENGLISH_COPY`, a private file-level value: Section B2.1's EN column, field by field |
| R2 | `CommonHandlerComposeResourceRuTest` | `init resolves the whole russian copy verbatim from the real catalog` | the same | `liveCopy` equals `RUSSIAN_COPY`, the RU column |
| P1 | `LiveWorkoutTemplateParityTest` (new, `mvi/mapper/`) | `every filled template matches the platform format in English` | the ten templates and arguments below | each `context.getString(id, *args)` equals `context.getString(id).fillTemplate(*args)`, with `assertAll` over the ten and the resource entry name as each message |
| P2 | `LiveWorkoutTemplateParityRuTest` (same file, `qualifiers = "ru"`) | `every filled template matches the platform format in Russian` | the same | the same |

N5's PR line: `loggedExercise()`'s best set beats an absent baseline (`PrComparator.beats` with
`hasBaseline = false` returns true for an eligible set), and 100.0 prints as `100`.

M1's header: the non-skipped exercises are 2, none done, no visible row done, and one visible row
(`pe-3`'s plan row; `pe-2` has none), with 1 skipped.

P1/P2's ten rows, with the argument types each template's call site passes:
`meta_format` `(2, 3, 5, 9)`; `meta_skipped_format` `("2 of 3 exercises · 5 of 9 sets", 1)`;
`delete_session_progress_format` `(1, 4, 6)`; `finish_stat_sets_count` `(12)`;
`finish_stat_exercises_format` `(3, 4)`; `finish_stat_exercises_with_skipped_format` `(2, 4, 1)`;
`finish_pr_weighted_format` `("102.5", 5)`; `finish_pr_weightless_format` `(18)`;
`toast_exercise_removed` `("Bench Press")`; `toast_exercise_removed_from_workout`
`("Bench Press")`.

N1–N5 and N8 count writes and launches on `ClickHandlerTest`'s own `FakeLiveWorkoutHandlerStore`,
which gains, under a one-line KDoc naming D6:

- `val events: MutableList<Event>`, appended by `sendEvent`;
- `var updates: Int` (`private set`), incremented by `updateState`;
- `var launches: Int` (`private set`), incremented by both `launch` and `launchDefault`.

`updateStateImmediate` and the flow `launch` stay as they are. The suite gains a private
`clickHandler(store)` helper (commit 2, for N9) that builds the handler with the suite's fields.

### B4.3 Reconciliation of the existing suites

Every existing identity keeps its name, its action and its assertions. Only setups change:

| Suite | Commit | Change |
| --- | --- | --- |
| `ClickHandlerTest` | 2 | `pickerHandler = FakeExercisePicker()`; N9 and `clickHandler(store)` |
| `ClickHandlerTest` | 3 | `StateStatusMapper()`; the feature `R` import goes; `undo of a deleted exercise restores it without any DB delete` loses its `every { resourceWrapper.getString(…toast_exercise_removed, "Bench Press") }` stub and builds its State with `liveCopy = LiveWorkoutCopy.EMPTY.copy(toastExerciseRemovedTemplate = "Removed from plan")`; `an adhoc session still removes the plan row on delete` likewise, with `isAdhoc = true` and `toastExerciseRemovedFromWorkoutTemplate = "Removed from workout"`; the store fake's three recorders; N1–N6 and N8 |
| `DialogClickHandlerTest` | 2 | `pickerHandler = FakeExercisePicker()`; `PickerAction delegates to the picker sub-handler` asserts `assertEquals(listOf(picker), pickerHandler.actions)` in place of `verify(exactly = 1) { pickerHandler.invoke(picker) }`; the `io.mockk.verify` import goes |
| `DialogClickHandlerTest` | 3 | `StateStatusMapper()`. The three stubs on paths that now read the copy go into the State: `OnFinishConfirm with blank required name sets nameError and skips finishSession` and `OnFinishNameChange with blank text sets the required error and disables confirm` get `liveCopy = LiveWorkoutCopy.EMPTY.copy(finishNameRequired = "Name is required")`; `OnFinishConfirm with required name and missing trainingUuid surfaces FinishFailed` gets `liveCopy = LiveWorkoutCopy.EMPTY.copy(errorFinishFailed = "Finish failed")`. The stubs on launch-body paths (`error_cancel_failed`, `error_discard_session_failed`, `error_reset_failed`, `finish_success`, `error_finish_missing_session`, and `error_finish_failed` in the launch-failure identity) stay. N7 |
| `ExercisePickerHandlerTest` | 3 | `StateStatusMapper()` |
| `LiveSetDraftBehaviorTest` | 3 | `pickerHandler = FakeExercisePicker()`; `StateStatusMapper()` |
| `LiveSetMutatorTest` | 3 | `StateStatusMapper()`; the unused `resourceWrapper`, its `ResourceWrapper` and `mockk` imports go; the class KDoc's last sentence says the fixtures carry the empty copy |
| `StateStatusMapperTest` | 3 | `StateStatusMapper()`; the unused `resourceWrapper` and its two imports go; M1, with the `NAMED_COPY` and `State` imports |
| `CommonHandlerTest` | 3 | `copyReader = mockk<LiveWorkoutCopyReader>(relaxed = true)` and `stringResolver = mockk<StringResolver>(relaxed = true)` in place of the wrapper; `LiveSetMutator(StateStatusMapper())`; the positional construction becomes `val handler = CommonHandler(interactor, copyReader, stringResolver, setMutator, dispatchers, store)` (the line limit), then `return Triple(stateFlow, handler, store)`; the named construction passes `copyReader = copyReader,` and `stringResolver = stringResolver,` where it passed the wrapper; the `ResourceWrapper` import becomes kit's `StringResolver` |
| `CommonHandlerExternalWritesTest` | 3 | `copyReader = LiveWorkoutCopyReader(RuntimeEnvironment.getApplication())`, `stringResolver = ComposeStringResolver()` (the suite is Robolectric, and its base load already read kit's catalog for real); `LiveSetMutator(StateStatusMapper())`; the `resourceWrapper` field and import go |
| `CommonHandlerComposeResourceTest` (both classes) | 3 | the helper builds `CommonHandler` with the real reader on `RuntimeEnvironment.getApplication()`, `ComposeStringResolver()` and `LiveSetMutator(StateStatusMapper())`, and drops its `ResourceWrapper` mock; the two existing identities assert `stateFlow.value.liveCopy.repsUnit` where they asserted `repsUnitLabel`; the class KDoc says both catalogs are real; R1, R2, `ENGLISH_COPY`, `RUSSIAN_COPY` |
| `LiveWorkoutMapperTest` | 3 | the 10 `toState(nowMillis = …, resourceWrapper = resourceWrapper, repsUnitLabel = X)` calls become `toState(nowMillis = …, liveCopy = LiveWorkoutCopy.EMPTY.copy(repsUnit = X))`; the six `toFinishStats(res)` calls become `toFinishStats()`; the four relaxed `res` mocks and the two stubbed ones go, and the two PR identities set the templates in their State: `toFinishStats emits one NewPrEntry per exercise that beats the snapshot` with `finishPrWeightedTemplate = "%1\$s × %2\$d"` and `finishPrWeightlessTemplate = "%1\$d reps"`, `toFinishStats picks the heaviest set per exercise even with multiple PR sets` with the weighted one; their expected `"110 × 5"`, `"18 reps"` and `"115 × 3"` stay; the class's `resourceWrapper` field and the `ResourceWrapper`, `R`, `every` and `mockk` imports go |
| `DeleteExerciseCopyMapperTest` | 3 | `copy.titleRes` → `copy.titleRes()`, and so for `bodyRes` and `confirmRes`, with the three member imports; `copy.toastRes` → `DeleteExerciseCopyMapper.toastTemplate(copy, NAMED_COPY)`, expecting `"toastExerciseRemovedFromWorkoutTemplate %1\$s"` for ADHOC_WORKOUT and `"toastExerciseRemovedTemplate %1\$s"` for the two TEMPLATE values |

No other suite changes. Every helper and fake keeps its name; the new ones (`FakeExercisePicker`,
`NAMED_COPY`, `ENGLISH_COPY`, `RUSSIAN_COPY`, `TEMPLATES` if the parity file names its rows so, and
the parity helper) are unique in the module's host compilation.

### B4.4 Counts

- `:core:ui:kit:testAndroidHostTest`: the baseline's suites and cases + 3 suites, + 7 cases (T1–T7).
- `:feature:live-workout:testDebugUnitTest`: after commit 2, 24 suites and 228 cases (+N9); after
  commit 3, 26 suites and 241 cases. The new identities are N1–N9, M1, R1, R2, P1 and P2 (14);
  `ClickHandlerTest` 18 → 26, `DialogClickHandlerTest` 28 → 29, `StateStatusMapperTest` 6 → 7, each
  `CommonHandlerComposeResource…Test` class 1 → 2, and the two parity classes are new.
- Every other module's host cases: unchanged, and all pass. No golden, device or Native identity is
  added or changed.

## B5. Known-negative controls and the exit census

### B5.1 Procedure

Section 6's procedure applies: fresh GREEN → one mutation → the required verdict quoted from fresh
XML → byte-exact restoration → `git status --porcelain --untracked-files=no` empty → fresh GREEN.

- The K controls go through `mutation_harness.py` one-shot, with
  `--no-configuration-cache --console=plain` inside `--task`. They run after commit 3, on its
  committed tree.
- A compile-invalid mutation is a failed control, never a RED.
- "Only" means every other identity of the task passes. Score each RED by identity from the fresh
  XML of every result directory the task writes.
- Consecutive controls with a byte-identical task may share a GREEN when HEAD is unchanged, the
  porcelain is empty, and the restored file's sha256 and mode equal HEAD's.

Tasks: TK = `:core:ui:kit:testAndroidHostTest :feature:live-workout:testDebugUnitTest --continue`;
TL = `:feature:live-workout:testDebugUnitTest`; TR =
`:feature:live-workout:testDebugUnitTest --tests *CommonHandlerComposeResource*`, with no quotes:
the harness splits `--task` on whitespace and runs Gradle without a shell.

### B5.2 The K controls

Paths are Section B2's, plus `core/…` for kit.

| Id | File | Mutation (anchor → replacement) | Task | Required verdict |
| --- | --- | --- | --- | --- |
| K1 | kit `StringTemplates.kt` | `formatArgs[match.groupValues[1].toInt() - 1].toString()` → `formatArgs[0].toString()` | TK | RED only: T1, T2, T4, T5, T6, T7; P1, P2, N4, N5, M1, and `LiveWorkoutMapperTest`'s `toFinishStats emits one NewPrEntry per exercise that beats the snapshot` and `toFinishStats picks the heaviest set per exercise even with multiple PR sets` |
| K2 | `mvi/handler/LiveWorkoutCopyReader.kt` | `context.getString(id)` → `context.getString(id, *arrayOf<Any>())` | TR | RED: all four identities of the task (the reader formats, so the first template throws and the load is abandoned) |
| K3 | `mvi/handler/CommonHandler.kt` | `resetTitle = copyReader.read(R.string.feature_live_workout_reset_title),` → `resetTitle = copyReader.read(R.string.feature_live_workout_cancel_title),` | TL | RED only R1 and R2 |
| K4 | `mvi/handler/ClickHandler.kt` | `title = it.liveCopy.resetTitle,` → `title = it.liveCopy.resetBody,` | TL | RED only N1 |
| K5 | `mvi/handler/ClickHandler.kt` | `state.doneCount,` → `state.totalCount,` | TL | RED only N4 |
| K6 | `mvi/mapper/DeleteExerciseCopyMapper.kt` | `DeleteExerciseCopyUiModel.ADHOC_WORKOUT -> liveCopy.toastExerciseRemovedFromWorkoutTemplate` → `DeleteExerciseCopyUiModel.ADHOC_WORKOUT -> liveCopy.toastExerciseRemovedTemplate` | TL | RED only `ClickHandlerTest`'s `an adhoc session still removes the plan row on delete` and `DeleteExerciseCopyMapperTest`'s `ad-hoc workout copy does not claim a plan changes after restoration` |
| K7 | `mvi/handler/DialogClickHandler.kt` | `.fillTemplate(exercise.exerciseName.truncateForToast()),` → `.fillTemplate(exercise.exerciseName),` | TL | RED only N7 |
| K8 | `mvi/mapper/LiveWorkoutMapper.kt` | `status == ExerciseStatusUiModel.SKIPPED -> copy.statusSkipped` → `status == ExerciseStatusUiModel.SKIPPED -> copy.statusNoPlan` | TL | RED only M1 |
| K9 | `mvi/handler/ClickHandler.kt` | `sendEvent(Event.ShowError(message = current.liveCopy.errorInvalidReps))` → `sendEvent(Event.ShowError(message = current.liveCopy.errorFinishFailed))` | TL | RED only N8 |
| K10 | `mvi/handler/ClickHandler.kt` | `pickerHandler.open()` → `// K10` | TL | RED only N9 |
| K11 | `mvi/handler/ClickHandler.kt` | `title = it.liveCopy.cancelTitle,` → `title = it.liveCopy.cancelBody,` | TL | RED only N2 |
| K12 | `mvi/handler/ClickHandler.kt` | `confirmLabel = it.liveCopy.emptyFinishDiscard,` → `confirmLabel = it.liveCopy.emptyFinishContinue,` | TL | RED only N3 |
| K13 | `mvi/handler/ClickHandler.kt` | `message = prior.liveCopy.toastSetAdded,` → `message = prior.liveCopy.toastSetRemoved,` | TL | RED only N6 |
| K14 | kit `StringTemplates.kt` | `private val POSITIONAL_PLACEHOLDER = Regex("""%(\d+)\$[ds]""")` → `private val POSITIONAL_PLACEHOLDER = Regex("""%(\d+)\$[ds]\|%d""")` | TK | RED only T3 (a bare `%d` now matches, and its empty group throws `NumberFormatException`) |

Every new identity has a RED: T1, T2, T4–T7 (K1), T3 (K14), N1 (K4), N2 (K11), N3 (K12), N4 (K1,
K5), N5 (K1), N6 (K13), N7 (K7), N8 (K9), N9 (K10), M1 (K1, K8), R1 and R2 (K2, K3), P1 and P2 (K1).
K14's replacement holds a `|` that this table escapes; the file gets the regex
`%(\d+)\$[ds]|%d`. K2 is scored on the filtered task because the real reader also serves
`CommonHandlerExternalWritesTest`'s loads, which K2 abandons too.

### B5.3 The exit census (X1–X11)

One script, written by the session (Section 0.2), run on the base and on commit 3's tree. Its
method is Section B0.2's; X4 uses Section B2.2's functions. It exits 0 only when every item holds,
prints each item's input count, and must exit 1 on the base.

| Id | Item | At exit | At the base |
| --- | --- | --- | --- |
| X1 | `resourceWrapper.getString(` calls in the module's `src/main` | 7: `ClickHandler` 1 (`sendError`), `DialogClickHandler` 2 (`sendError`, `finish_success`), `ExercisePickerHandler` 4 (`sendError`, `toast_exercise_added`, `picker_create_format`, `picker_no_match_format`) | 36 |
| X2 | files naming `ResourceWrapper` or `resourceWrapper` | exactly those three handlers | 6 (+ `CommonHandler`, `StateStatusMapper`, `LiveWorkoutMapper`) |
| X3 | CMP's top-level `getString(Res.…)` calls and `org.jetbrains.compose.resources.getString` imports; files naming `StringResolver` | 0 and 0; `CommonHandler` only | 1 and 1; none |
| X4 | Section B2.2's lambda census | 61 lambdas, 11 without `.copy(`, 0 holding tokens, 0 resolver references, 0 same-file calls, 0 module reach | 61, 11, 4 (24), 0, 0, 13 |
| X5 | the five pipeline files (B1) hold no `FEATURE_STATE_LAMBDA_SIDE_EFFECTS` token, name no `ResourceWrapper` / `resourceWrapper` / `StringResolver` / `stringResolver`, and import no feature `R` | all five clean | `StateStatusMapper` and `LiveWorkoutMapper` fail |
| X6 | `context.getString(` calls; files importing `android.content.Context` | 1, in `LiveWorkoutCopyReader`; that file only | 0; none |
| X7 | in `CommonHandler`: `copyReader.read(` calls, their keys, kit's read; `copyReader` named in another file | 30 calls, 30 distinct keys equal to Section B2.1's 30, and one `stringResolver.getString(Res.string.core_ui_kit_plan_editor_unit_reps)`; none | — |
| X8 | the gate's `FEATURE_PAYLOAD_TOKENS` in `LiveWorkoutCopy.kt`, `DeleteExerciseCopyUiModel.kt`, `ErrorType.kt`; a feature `R` import in them | none; none | `StringRes` in `DeleteExerciseCopyUiModel.kt`; `R` in both id models |
| X9 | `<string name=` and `<plurals name=` per catalog | 94 and 2 in each, the diff Section B3.4's lines only | 96 and 2 |
| X10 | `currentTimeMillis` in the module; `Clock.System.now().toEpochMilliseconds()` | 0; `CommonHandler` 2, `LiveWorkoutInteractorImpl` 1 | 3; 0 |
| X11 | `ExercisePickerHandler` in `ClickHandler` / `DialogClickHandler` code; `val … : ExercisePicker,` parameters; implementers; `@Binds` in di/LiveWorkoutGraph.kt | 0 / 0; those two handlers; `ExercisePickerHandler`; 3 | 1 / 1; none; none; 2 |

The census controls run on scratchpad copies of a file, restored with `cp` and sha256-checked, each
followed by the restored tree's exit 0:

| Id | Mutation | Required verdict |
| --- | --- | --- |
| G1 | `ClickHandler.kt`: `title = it.liveCopy.resetTitle,` → `title = resourceWrapper.getString(R.string.feature_live_workout_reset_title),` | exit 1: X1 = 8 (`ClickHandler` 2), and X4 one lambda holding `getString(` and `resourceWrapper.` (2 occurrences) |
| G2 | `CommonHandler.kt`: `nowMillis = Clock.System.now().toEpochMilliseconds(),` → `nowMillis = System.currentTimeMillis(),` | exit 1: X10, `currentTimeMillis` 1 and `CommonHandler` 1 |
| G3 | `LiveWorkoutMapper.kt`: `status == ExerciseStatusUiModel.SKIPPED -> copy.statusSkipped` → `status == ExerciseStatusUiModel.SKIPPED -> resourceWrapper.getString(R.string.feature_live_workout_status_skipped)` | exit 1: X1 = 8, X2 adds `LiveWorkoutMapper`, X4 module reach 13, X5 flags `LiveWorkoutMapper` |

The prototype census produced exactly these verdicts on the prototype tree and the base.

## B6. Gates

Section 0.2's flags and XML rules apply to every gate. Repository-wide, Native, link and device
gates add Section 14's memory flags (`-Dorg.gradle.jvmargs=-Xmx4g --max-workers=2`; device suites
testing.md's `-Xmx3g --max-workers=2 --full-stacktrace --continue`).

### B6.1 Entry

- Base = live origin/dev after this amendment's commit. It contains `c3026c3ce`. The latest
  `Android CI/CD - Unified Build and Tests` push run on dev is green (record its id and head;
  `c3026c3ce`'s is `40217032970`, which concluded success). A red run is a STOP.
- Drift: no Section B8 path changed since `c3026c3ce`, except this document.
- A census script reproduces Section B2, each item with its method: the copy reads and their keys
  (B2.1, the 31-row table's keys and EN/RU values against the catalogs), the lambda census (B2.2),
  the catalogs (B2.3), the `@Test` counts by class (B2.4), the facts of B2.5, and the
  suppressions and blob set of B2.6.
- The exit census (Section B5.3) on the base exits 1 with exactly the base column.
- Baselines, with fresh XML:
  - `python3 .github/scripts/assert_kmp_ui_source_topology.py` under every Python on the host:
    byte-identical, exit 0, empty stderr (BASE_GATE_OUTPUT; record its lines and sha256);
  - `python3 .github/scripts/assert_mvi_source_topology.py` (BASE_MVI_TOPOLOGY);
  - `:core:ui:kit:testAndroidHostTest`: suites, cases and names (KIT_BASELINE);
  - `./gradlew assembleDebug lintDebug testDebugUnitTest --continue`: every module/task row's
    suites, cases and names from fresh XML (HOST_BASELINE; live-workout 24 suites and 227 cases),
    and every module's lint findings by id and path from its `lint-results.xml` (LINT_BASELINE);
  - `./gradlew verifyPaparazziDebug`: suites and cases; porcelain empty;
  - the fifteen-module Native command from `android_build_unified.yml` with `--continue`, then
    `assert_kmp_ios_smoke.py` as CI runs it (NATIVE_BASELINE);
  - the fifteen-module `linkDebugTestIosArm64` (15/15 `test.kexe`).

### B6.2 After commit 1

    ./gradlew :core:ui:kit:testAndroidHostTest      # KIT_BASELINE + 3 suites, + 7 cases (T1–T7)
    ./gradlew :core:ui:kit:assembleDebug            # Android and both iOS klibs
    ./gradlew <fifteen-module iosSimulatorArm64Test> --continue
    python3 .github/scripts/assert_kmp_ios_smoke.py # = NATIVE_BASELINE
    ./gradlew <fifteen-module linkDebugTestIosArm64> --continue   # 15/15 test.kexe

Then detekt alone.

### B6.3 After commit 2

    ./gradlew :feature:live-workout:testDebugUnitTest   # 24 suites, 228 cases: the baseline + N9
    ./gradlew :app:dev:assembleDebug                    # Metro compiles the graph with the seam

Then the exit census's X9, X10 and X11, which hold from this commit on, and detekt alone.

### B6.4 After commit 3

    ./gradlew :feature:live-workout:testDebugUnitTest   # 26 suites, 241 cases: Section B4.4's names
    ./gradlew :core:ui:kit:testAndroidHostTest          # = commit 1's run
    ./gradlew :app:dev:assembleDebug                    # Metro provides the reader its Context

Then the exit census (X1–X11) exits 0, and detekt alone.

### B6.5 Repository gates, each in its own invocation, on commit 3's tree

    ./gradlew assembleDebug lintDebug testDebugUnitTest --continue   # = HOST_BASELINE but kit (+3, +7) and live-workout (+2, +14); lint = LINT_BASELINE
    ./gradlew detekt --continue
    ./gradlew assembleDebugAndroidTest
    ./gradlew verifyPaparazziDebug                       # the baseline's counts; porcelain empty; blob set 53221eab…
    ./gradlew :lint-rules:test
    ./gradlew :app:wear:testStoreReleaseUnitTest -Pandroid.onlyEnableUnitTestForTheTestedBuildType=false --tests '*ReleaseRuntimeBoundaryTest*'
    python3 .github/scripts/assert_kmp_ui_source_topology.py   # byte-identical to BASE_GATE_OUTPUT, every Python
    python3 .github/scripts/assert_mvi_source_topology.py      # = BASE_MVI_TOPOLOGY
    python3 documentation/personal_data_gate.py -v
    ./gradlew <fifteen-module iosSimulatorArm64Test> --continue && python3 .github/scripts/assert_kmp_ios_smoke.py   # = NATIVE_BASELINE
    ./gradlew <fifteen-module linkDebugTestIosArm64> --continue   # 15/15

"Lint = LINT_BASELINE" means every module's set of findings (id, path, line text) equals the entry's.
Part A measured 80 dependency-version advisories in each of app:app, app:dev and app:store, and
none elsewhere (Section 14.6). A finding on a Section B8 path, or one the baseline lacks, is a STOP.

### B6.6 Device gates

On one API-34 emulator with an explicit serial and animations off:

- testing.md's canonical Smoke and Regression suites, in `run_smoke_ui_tests.sh`'s and
  `ui_tests.yml`'s form, then `assert_mvi_device_identities.py` as the Smoke script runs it. Compare
  with Part A's record (Section 14.6: Smoke 44 with the 3 documented skips, Regression 88); a new
  failure is a STOP.
- Logcat (CB11), per suite: `adb -s <serial> logcat -c`; start
  `adb -s <serial> logcat -v threadtime` writing to a scratchpad file, and record its PID; run the
  suite; stop the writer by that PID. Report every line whose priority is `E` and whose tag starts
  with `SCREEN_`, grouped by tag with counts and the first stack of each. A `SCREEN_LiveWorkout`
  line is a STOP: report it with its stack and the test that was running.

### B6.7 Diff

Against the PR's base (this amendment's commit): after commit 1, exactly its 3 paths; after
commit 2, 15; after commit 3, 36; after commit 4, the 39 of Section B8. Never a `.png`,
`*.gradle.kts`, workflow, `.github/scripts/` or `gradle/libs.versions.toml` path; the two catalogs
only with Section B3.4's lines.

## B7. Out of scope

- Everything of Part C: the move, the Compose-resources catalog, `StringResolver` in launch bodies,
  D7's `state_mutators`, D8's suites, the previews, the goldens' move, the gate entry.
- The 11 lambdas that return `LiveSetMutator`'s State without `.copy(` (F3, D7's subject).
- `ErrorType.Unknown`, which no path produces (already so at the base).
- Any locale-list or per-app-locale behaviour of `StringResolver` (tech-debt.md's row).
- The release Crashlytics observation of Part A (Section 12.2) and every PR-L backlog item beyond
  writing it down (XB6).
- Wear: the watch's own copy and the transport.

## B8. Exact change boundary — 39 paths

Kit, commit 1 (3, new):

    core/ui/kit/src/commonMain/kotlin/io/github/stslex/workeeper/core/ui/kit/strings/StringTemplates.kt
    core/ui/kit/src/androidHostTest/kotlin/io/github/stslex/workeeper/core/ui/kit/strings/StringTemplatesTest.kt
    core/ui/kit/src/androidHostTest/kotlin/io/github/stslex/workeeper/core/ui/kit/strings/StringTemplatesParityTest.kt

live-workout production, under `feature/live-workout/src/main/` (19):

    kotlin/io/github/stslex/workeeper/feature/live_workout/mvi/handler/ExercisePicker.kt            (new, commit 2)
    kotlin/io/github/stslex/workeeper/feature/live_workout/mvi/handler/ExercisePickerHandler.kt     (commits 2, 3)
    kotlin/io/github/stslex/workeeper/feature/live_workout/di/LiveWorkoutGraph.kt                   (commit 2)
    kotlin/io/github/stslex/workeeper/feature/live_workout/mvi/handler/ClickHandler.kt              (commits 2, 3)
    kotlin/io/github/stslex/workeeper/feature/live_workout/mvi/handler/DialogClickHandler.kt        (commits 2, 3)
    kotlin/io/github/stslex/workeeper/feature/live_workout/mvi/handler/CommonHandler.kt             (commits 2, 3)
    kotlin/io/github/stslex/workeeper/feature/live_workout/domain/LiveWorkoutInteractorImpl.kt      (commit 2)
    res/values/strings.xml                                                                         (commit 2)
    res/values-ru/strings.xml                                                                      (commit 2)
    kotlin/io/github/stslex/workeeper/feature/live_workout/mvi/model/LiveWorkoutCopy.kt             (new, commit 3)
    kotlin/io/github/stslex/workeeper/feature/live_workout/mvi/handler/LiveWorkoutCopyReader.kt     (new, commit 3)
    kotlin/io/github/stslex/workeeper/feature/live_workout/mvi/mapper/ErrorTypeMapper.kt            (new, commit 3)
    kotlin/io/github/stslex/workeeper/feature/live_workout/mvi/model/ErrorType.kt                   (commit 3)
    kotlin/io/github/stslex/workeeper/feature/live_workout/mvi/model/DeleteExerciseCopyUiModel.kt   (commit 3)
    kotlin/io/github/stslex/workeeper/feature/live_workout/mvi/mapper/DeleteExerciseCopyMapper.kt   (commit 3)
    kotlin/io/github/stslex/workeeper/feature/live_workout/mvi/mapper/LiveWorkoutMapper.kt          (commit 3)
    kotlin/io/github/stslex/workeeper/feature/live_workout/mvi/mapper/StateStatusMapper.kt          (commit 3)
    kotlin/io/github/stslex/workeeper/feature/live_workout/mvi/store/LiveWorkoutStore.kt            (commit 3)
    kotlin/io/github/stslex/workeeper/feature/live_workout/ui/components/SessionSheets.kt           (commit 3)

live-workout tests, under `feature/live-workout/src/test/kotlin/io/github/stslex/workeeper/feature/live_workout/` (14):

    mvi/handler/FakeExercisePicker.kt                     (new, commit 2)
    mvi/handler/ClickHandlerTest.kt                       (commits 2, 3)
    mvi/handler/DialogClickHandlerTest.kt                 (commits 2, 3)
    mvi/model/LiveWorkoutCopyFixtures.kt                  (new, commit 3)
    mvi/mapper/LiveWorkoutTemplateParityTest.kt           (new, commit 3)
    mvi/handler/ExercisePickerHandlerTest.kt              (commit 3)
    mvi/handler/LiveSetDraftBehaviorTest.kt               (commit 3)
    mvi/mapper/LiveSetMutatorTest.kt                      (commit 3)
    mvi/mapper/StateStatusMapperTest.kt                   (commit 3)
    mvi/handler/CommonHandlerTest.kt                      (commit 3)
    mvi/handler/CommonHandlerExternalWritesTest.kt        (commit 3)
    mvi/handler/CommonHandlerComposeResourceTest.kt       (commit 3)
    mvi/mapper/LiveWorkoutMapperTest.kt                   (commit 3)
    mvi/mapper/DeleteExerciseCopyMapperTest.kt            (commit 3)

Documentation, commit 4 (3):

    documentation/architecture.md
    documentation/tech-debt.md
    documentation/feature-specs/kmp-phase-7-16-live-workout-feature.md   (the Status line and Section B13 only)

That is 3 + 19 + 14 = 36 code paths, and 39 with the documentation: commit 1 adds 3, commit 2
adds 12 (9 production, 3 tests), commit 3 adds 21 more (10 production, 11 tests) and re-touches 6.

What each document gains:

- **architecture.md, Localization.**
  - "The five production files that predate this rule" becomes "The four production files…".
  - The `R`-string bullet reads: `stringResource(R.string.*)` in composition, `ResourceWrapper`
    outside it, except live-workout's verbatim copy read (below).
  - A new paragraph after the click-path paragraph: live-workout takes another route (Phase 7.16
    D5 and D6, this document's Part B). Its copy, templates included, is read verbatim at each load
    into `State.liveCopy`, and kit's `fillTemplate`, beside `StringResolver`, fills a template
    synchronously, exactly as Compose resources' `getString` fills it: each positional `%N$s` or
    `%N$d` becomes the argument's `toString()`. So its status pipeline runs inside its
    compare-and-set lambdas, and its dialogs, toasts and synchronous errors open in one write, with
    nothing to resolve. Until the feature moves (Part C), the verbatim read of its `R` strings goes
    through the temporary module-local `LiveWorkoutCopyReader` on the app `Context`, because
    `ResourceWrapper.getString` always formats and a template read through it throws
    `MissingFormatArgumentException`.
- **architecture.md, State mutation discipline.** The paragraph on the synchronous gesture path
  (exercise-chart's `readoutCopy`) gains one sentence: live-workout keeps its templates in State
  too, and fills them inside the lambda with kit's pure `fillTemplate`, a string transform of
  State like the exempt transforms above (Phase 7.16 D5).
- **tech-debt.md, "Shared (KMP) string resources".** The first row drops live-workout's
  `CommonHandler` link: "These five production files" becomes "These four", "markers for the other
  three" becomes "markers for the other two", and the row ends with one sentence: live-workout's
  `CommonHandler` left it in Phase 7.16 PR-P, which reads kit's reps unit through `StringResolver`.
- **tech-debt.md, "Shared (KMP) feature entries — follow-ups".**
  - The `ChartLoader` row's last sentence becomes: live-workout's `ClickHandler` and
    `DialogClickHandler` follow it since Phase 7.16 PR-P, reaching `ExercisePickerHandler` through
    `ExercisePicker`.
  - The single-training row's last sentence becomes: live-workout takes another route (Phase 7.16
    D5 and D6): its whole copy, templates included, resolves at each load into `State.liveCopy`,
    and kit's `fillTemplate` fills a template synchronously, so its four confirm dialogs, the
    finish dialog, the toasts and its two synchronous errors open in one write, with no launch.
- **tech-debt.md, a new section** after "✅ RESOLVED — The default flow `onError` is silent …", titled
  `## Store launch contract — what Phase 7.16 PR-L left as it was (2026-10-25)`, with one line of
  context (PR-L changed only what a launch records; this document's C8 and Section 8) and a table
  in the file's form:
  - 🟡, `core/core/…/coroutine/scope/AppCoroutineScopeImpl.kt`: a throw from the flow overload's
    `each` runs downstream of its `catch`, and the `launchIn` coroutine has no
    `CoroutineExceptionHandler`, so on Android it crashes the app (read from the code, not
    measured). Trigger: the next change to the launch contract, or the first such crash;
  - 🟢, the same file: the backstop re-runs `onError` on the work dispatcher, not the `each`
    dispatcher an explicit `onError` otherwise runs on; the `exceptionHandler` parameter has no
    caller; `StoreConsumer` and `HandlerStore` KDoc does not mention the record. Trigger: the next
    change to the launch contract;
  - 🟢, `core/core/…/result/ResultUtils.kt`: `ResultUtils` and `ResultFlow` have no consumer, and
    `fold`'s default `onError` logs (`logger.e(it, it.message)`), so a first consumer that kept the
    default would record an upstream failure twice since PR-L. Trigger: a first consumer, or
    deleting both.

## B9. STOP conditions

Stop, report what was measured, and widen nothing, if:

- the base lacks `c3026c3ce`, the latest dev push run is red, or a Section B8 path drifted;
- the entry census or a baseline differs from Section B2, or the exit census on the base differs
  from Section B5.3's base column;
- the exit census fails on commit 3's tree;
- an existing identity changes its name or outcome, or needs a change beyond Section B4.3; a stub
  that B4.3 keeps goes unused, or one it moves is still needed;
- T6, T7, P1 or P2 fails: record the template, the locale and both strings. Never change the
  filler, a template or an argument type to make it pass;
- Metro rejects the graph: the binding needs a seam or a handler shape other than Section B3.2's,
  or the reader cannot get the app `Context`;
- the reader needs anything but `context.getString(id)`;
- a control of Section B5 does not produce its required verdict, or a mutation does not compile;
- any PNG blob changes under `verifyPaparazziDebug` (never re-record);
- detekt reports anything; lint reports a finding on a Section B8 path or any finding the baseline
  lacks; any suppression becomes necessary;
- the topology gate's output differs from BASE_GATE_OUTPUT, kit's Native identities or link change,
  or a host case outside kit and live-workout changes count or outcome;
- a gate needs a build-file, workflow, CI-script, catalog (beyond Section B3.4) or version change;
- a device suite fails in a way Part A's record does not, or a `SCREEN_LiveWorkout` error line
  appears in the device logcat;
- any path outside Section B8 changes.

## B10. Signed, bisect-green commit plan

1. **feat(kit): fill a verbatim catalog template as Compose resources do.** Section B3.1 and
   Section B4.1: the 3 kit paths.
2. **refactor(live-workout): reach the picker through a seam, use kotlin.time.Clock, drop two dead keys.**
   Sections B3.2–B3.4, and the commit-2 rows of Section B4.3 with N9: 12 paths.
3. **refactor(live-workout): resolve the screen copy at the load and fill it before the click.**
   Sections B3.5–B3.10, and the commit-3 rows of Section B4.3 with N1–N8, M1, R1, R2, P1 and P2:
   21 new paths and 6 re-touched.
4. **docs(kmp): record Phase 7.16 Part B evidence.** The two documents of Section B8, and this
   document's Status line and Section B13. The Status line becomes `**Status:** Part A COMPLETE —
   implemented by PR #344. Part B COMPLETE — implemented by the PR whose merge brought this line to
   dev. Part C (PR-M) is specified by amendment.`, which is true on dev only once the maintainer
   merges. Sections 0–14 and B0–B12 stay byte-identical.

Each commit is made first; its gates then run on the committed tree. A fix is a new signed commit
inside Section B8, and every gate it can reach re-runs. Nothing is amended, squashed or reordered.

## B11. Exit criteria

### B11.1 PR-P

Section B1 is proven and Sections B5 and B6 are evidenced, in one PR into dev with CI green on its
head. `@claude review` is requested, and every finding is classified (correct / already decided /
wrong / new) before any fix. The maintainer merges.

### B11.2 The owner's device check (SP8, after the merge; not a gate)

On the dev build of the merged tree, with the watch paired, and with
`adb logcat -s SCREEN_LiveWorkout` running:

- set marks on a planned exercise, a skip and an unskip; the cards' plan lines and the header's
  meta line update with each;
- add a set and remove the last one: both toasts and their undo;
- reset an exercise's sets, and cancel a session: both confirms' text;
- finish with a new personal record: the summary, the sets count, the PR line and the name field;
- finish an empty session (the empty-finish dialog), and the delete-session dialog's progress line;
- delete an exercise with a long name: the toast's 24-character name and `…`;
- a set marked on the watch during the session, and one during a plan-editor round trip;
- the same screens in Russian, switching the system language with the screen open (OD1: the
  screen relabels on re-entry);
- the logcat shows no `SCREEN_LiveWorkout` error line.

A difference from the build before PR-P, other than OD1–OD4, goes to the maintainer as a finding.

## B12. Part C carry-ins

- **SM0.** The move commit swaps `LiveWorkoutCopyReader` for `StringResolver`'s no-argument path
  and deletes the reader; X6 then finds no `Context` in the module (SP2's control). The seven launch
  reads move to `StringResolver`.
- **XB4.** D7's `state_mutators` names the classes the 11 lambdas return through and the
  functions they reach: `LiveSetMutator`, `StateStatusMapper` and `LiveWorkoutMapper` at least.
- **D8.** `CommonHandlerComposeResourceTest` and `…RuTest` (4 identities after Part B) retire; one
  commonTest identity pins the copy's resolution at the load through a recording resolver. P1 and
  P2 read Android resources and leave with the catalog; kit's T6 and T7 keep the parity on CMP.
- **Payloads.** `LiveWorkoutCopy.kt` joins `payload_files`; X8 shows it clean.
- **The id mappers.** `ErrorTypeMapper` and `DeleteExerciseCopyMapper`'s three `…Res` members map
  to `Res.string` with the catalog.
- The spec misses of Section B0.2 (multi-line census, unique helper names, a control for every
  identity, a lint STOP scoped to findings the PR adds, a named backlog file) and the logcat sweep
  stay in Part C's gates.

## B13. Implementation evidence

Recorded by commit 4. Every Gradle gate ran after `./gradlew --stop` with `--rerun-tasks
--no-build-cache --no-configuration-cache --console=plain`, on a daemon registry private to this
run, and every count below is parsed from JUnit XML written after the gate's recorded start.
Repository-wide, Native and link gates added `-Dorg.gradle.jvmargs=-Xmx4g --max-workers=2`; device
suites used testing.md's canonical `-Dorg.gradle.jvmargs=-Xmx3g --max-workers=2 --full-stacktrace
--continue`. detekt never shared an invocation with tests.

### B13.1 Base and commits

- Base: `972537be30a250dac9a2c5f30e6f70a273a1209a`, this amendment's landing on dev (parent
  `c3026c3ce`). It changed one Markdown file, so no unified run started for it.
- Commit 1: `db875f5fdbb3f5ec8ea741ea39fd6d9d52a3ae6b`, the 3 kit paths; local `gpgsig` header
  present.
- Commit 2: `b285b13510d1d06e55b19e3b90df8c8084c68015`, the 12 paths of Section B10; local `gpgsig`
  header present.
- Commit 3: `01ddfa7577fd650a9857ffcfdcc4127b831fa7df`, 21 new paths and 6 re-touched; local `gpgsig`
  header present.
- Commit 4: this commit.

Against the base: 3 paths after commit 1, 15 after commit 2, 36 after commit 3, and Section B8's 39
after commit 4. No `.png`, `*.gradle.kts`, workflow, `.github/scripts/` or
`gradle/libs.versions.toml` path; each catalog's numstat is `0 2`.

### B13.2 Entry gate (Section B6.1)

- The latest `Android CI/CD - Unified Build and Tests` push run on dev: `40217032970`, head
  `c3026c3ce`, success.
- Drift: since `c3026c3ce`, only this document's landing touched a Section B8 path.
- Census: one script read the base's git objects (59 production Kotlin files of the module, its 27
  test files, both catalogs, kit's catalogs, the topology gate and the other files Section B2
  names). Comments and string literals were blanked first, and every call was read from its name to
  its closing parenthesis, across lines. Section B2.2 imported the topology gate's own functions.
  The output was byte-identical under all five Pythons (sha256 `8a835c452aadd54b…`).

| Item (method) | Measured | Verdict |
| --- | --- | --- |
| `resourceWrapper.getString(` calls, by file | 36: `ClickHandler` 15, `DialogClickHandler` 5, `ExercisePickerHandler` 4, `LiveWorkoutMapper` 12 | equal |
| Their key expressions (first argument, compacted, distinct) | 32: 30 direct keys, `type.msgRes` (3 calls, the three `sendError`s), `copy.toastRes` (1 call, `DialogClickHandler` line 88) | equal |
| CMP's top-level `getString` (not after `.`, not a declaration) and its import | 1, `CommonHandler` line 125, kit's `core_ui_kit_plan_editor_unit_reps`; 1 import | equal |
| Composition reads (every `stringResource(` and `pluralStringResource(`; CMP when the id is `Res.*`) | 50: 47 Android, 3 CMP in `LiveSetRow` | equal, see deviation 1 |
| The 31-row table: each key in both catalogs (kit's in kit's) with the EN and RU values shown | 31 of 31 | equal |
| No value carries an escape, an entity, a `%%` or a bare `%d` (raw XML) | 62 values, none | equal |
| The ten templates (rows with a positional placeholder; fields ending `Template`) | rows 5, 6, 18, 21–25, 28, 29, both ways | equal |
| 26 of the 30 direct keys move; four stay; the copy adds 2 toast keys, 2 error keys, kit's unit | 26; the four present; equal to the table | equal |
| "Read at the base": rows 1–27 in the named file; 28–29 via `copy.toastRes`; 30–31 via `type.msgRes` | 26 direct rows in their files; the 4 enum entries present | equal |
| Section B2.2's lambda census (the gate's `braced_call_bodies`, `compact`, side-effect tokens, resolver regex, same-file and module-wide `copy_resolving_functions`) | 61 lambdas, 11 without `.copy(`, 4 holding tokens (24), 0 resolver references, 0 same-file calls, 13 module reach | equal |
| The 13 by file | `ClickHandler` 7, `CommonHandler` 2 (one by `::`), `DialogClickHandler` 2, `ExercisePickerHandler` 1, `PendingUndoOps` 1, with the functions Section B2.2 names | equal |
| Catalogs: `<string>` and `<plurals>` per locale | 96 and 2, each | equal |
| The dead keys at their lines, each `…UnusedResources`; references outside the catalogs and `documentation/` | EN 100 and 127, RU 91 and 113; none | equal |
| `@Test` counts by innermost enclosing class | 24 suites, 227 cases, each equal to Section B2.4's table | equal |
| The four golden classes (the gate's `GOLDEN_METHODS`) | 30 methods: header 4, screen 1, sheets 7, state 18 | equal |
| `System.currentTimeMillis()` | `CommonHandler` 121 and 189, `LiveWorkoutInteractorImpl` 324 | equal |
| The picker's injection; `@Binds` in `di/LiveWorkoutGraph.kt`; `AppGraph`'s `Context`; settings' injection | `ClickHandler`, `DialogClickHandler` only; 2; `@Provides applicationContext: Context`; `BackupClickHandler` | equal |
| `ErrorType` producers; `sendError` calls, synchronous / in launch bodies | `InvalidReps` 1, `FinishFailed` 2, `Unknown` 0; 19: 2 synchronous (`ClickHandler` 170, `DialogClickHandler` 178), 17 in launch bodies (9, 6, 2) | equal |
| The resolver's two paths; the wrapper always formats | both hold | equal |
| References outside the module (every source set; `documentation/` and `graphify-out/` excluded) | none | equal |
| detekt thresholds and lint settings | as Section B2.5; the lint baseline is empty | equal |
| Inherited suppressions | production 12 in 10 files, tests 7 in 5, each as Section B2.6 lists | equal |
| The PNG blob set | 60 PNGs, `53221eab…` | equal |

The exit census (Section B5.3) on the base exited 1, and all eleven items equalled the base column
(X7, whose base column is "—", measured 0 reads).

Baselines on the base:

| Gate | Executed | Fresh XML |
| --- | --- | --- |
| `assert_kmp_ui_source_topology.py`, five Pythons | — | 833 lines, exit 0, empty stderr, one sha256 `37315e70…` (BASE_GATE_OUTPUT) |
| `assert_mvi_source_topology.py` | — | exit 0, 5 lines (BASE_MVI_TOPOLOGY) |
| `:core:ui:kit:testAndroidHostTest` | 87/87 | 27 suites, 416 cases (KIT_BASELINE) |
| `assembleDebug lintDebug testDebugUnitTest --continue` | 2808/2808 | 34 module/task rows, 474 suites, 3,370 cases, 0 failures (HOST_BASELINE; live-workout 24 suites, 227 cases); lint 80 findings in each of app:app, app:dev and app:store, none in the other 36 reports (LINT_BASELINE); see deviation 2 |
| `verifyPaparazziDebug` | 675/675 | 169 suites, 1,766 cases, 0 failures; porcelain empty |
| fifteen-module `iosSimulatorArm64Test --continue` + oracle | 410/410 | 96 suites, 635 cases; oracle exit 0, kit 2 identities (NATIVE_BASELINE) |
| fifteen-module `linkDebugTestIosArm64 --continue` | 395/395 | 15/15 fresh `test.kexe` |

### B13.3 After commits 1, 2 and 3 (Sections B6.2–B6.4)

| Commit | Gate | Executed | Result |
| --- | --- | --- | --- |
| 1 | `:core:ui:kit:testAndroidHostTest` | 87/87 | 30 suites, 423 cases: KIT_BASELINE plus exactly T1–T7 |
| 1 | `:core:ui:kit:assembleDebug` | 128/128 | the Android AAR and both iOS klibs (`compileKotlinIosArm64`, `compileKotlinIosSimulatorArm64`) |
| 1 | fifteen-module Native + oracle | 410/410 | 96 suites, 635 cases; every row's names and the oracle's output equal to NATIVE_BASELINE |
| 1 | fifteen-module link | 395/395 | 15/15 fresh `test.kexe` |
| 2 | `:feature:live-workout:testDebugUnitTest` | 212/212 | 24 suites, 228 cases: HOST_BASELINE's row plus exactly N9 |
| 2 | `:app:dev:assembleDebug` | 688/688 | built; Metro accepted the `ExercisePicker` binding |
| 2 | exit census X9, X10, X11 | — | pass: 94 and 2 per catalog with Section B3.4's diff only; no `currentTimeMillis`, `Clock` in `CommonHandler` 2 and `LiveWorkoutInteractorImpl` 1; the seam as Section B5.3 states, 3 `@Binds` |
| 3 | `:feature:live-workout:testDebugUnitTest` | 212/212 | 26 suites, 241 cases: HOST_BASELINE's row plus exactly Section B4.4's 14 |
| 3 | `:core:ui:kit:testAndroidHostTest` | 87/87 | equal to commit 1's run |
| 3 | `:app:dev:assembleDebug` | 688/688 | built; Metro provides the reader its `Context` |
| 3 | exit census X1–X11 | — | exit 0 (Section B13.5) |
| each | detekt (alone, on the staged tree before the commit and on the commit after it) | 63/63 | 0 findings in 55 fresh reports (deviation 3 for commit 2's first staged run) |

On commit 3 the module's 19 suppression lines are byte-identical to the base's, and the PNG blob
set is still `53221eab…`.

### B13.4 Known-negative controls (Section B5)

The K controls ran through `mutation_harness.py` one-shot under `python3`, on commit 3's committed
tree. Every RED is scored by identity from the fresh XML of every result directory the task writes,
and every other case of the task passed. Each control restored its file to HEAD's sha256 and mode
with an empty porcelain, and its fresh GREEN afterwards passed (TK 664 cases, TL 241, TR 4). K4–K13
shared the GREEN between consecutive controls of task TL.

| Id | Required | Measured RED, quoted |
| --- | --- | --- |
| K1 | only T1, T2, T4–T7, P1, P2, N4, N5, M1, the two PR identities | exactly those 13. T1: `expected: <2 of 3 exercises> but was: <2 of 2 exercises>`. T5: `Expected java.lang.IndexOutOfBoundsException to be thrown, but nothing was thrown.` P1: `feature_live_workout_meta_format ==> expected: <2 of 3 exercises · 5 of 9 sets> but was: <2 of 2 exercises · 2 of 2 sets>`. M1: `but was: <metaSkippedTemplate metaTemplate 0 0 0 0 metaTemplate 0 0 0 0>` |
| K2 | all four of TR | the four. `init resolves the reps unit from the real catalog into state`: `expected: <reps> but was: <>` (the load was abandoned) |
| K3 | only R1, R2 | R1, R2: the whole-copy comparison expected `resetTitle=Reset sets?` and got `resetTitle=Cancel session?` (RU: `Сбросить подходы?` and `Отменить сессию?`) |
| K4 | only N1 | N1: `but was: <ResetSets(title=resetBody, …` |
| K5 | only N4 | N4: `but was: <DeleteDialog(sessionName=deleteSessionUnnamed, progressLabel=deleteSessionProgressTemplate 2 2 3)>` |
| K6 | only `an adhoc session still removes the plan row on delete` and `ad-hoc workout copy does not claim a plan changes after restoration` | those two: `expected: <Removed from workout> but was: <>`; `expected: <toastExerciseRemovedFromWorkoutTemplate %1$s> but was: <toastExerciseRemovedTemplate %1$s>` |
| K7 | only N7 | N7: `expected: <toastExerciseRemovedTemplate Romanian Deadlift With P…> but was: <toastExerciseRemovedTemplate Romanian Deadlift With Pause>` |
| K8 | only M1 | M1: `expected: <[statusSkipped, statusNoPlan, 12 repsUnit]> but was: <[statusNoPlan, statusNoPlan, 12 repsUnit]>` |
| K9 | only N8 | N8: `expected: <[errorInvalidReps]> but was: <[errorFinishFailed]>` |
| K10 | only N9 | N9: `expected: <1> but was: <0>` |
| K11 | only N2 | N2: `but was: <CancelSession(title=cancelBody, …` |
| K12 | only N3 | N3: `but was: <EmptyFinish(canDiscard=true, confirmLabel=emptyFinishContinue, …` |
| K13 | only N6 | N6: `expected: <toastSetAdded> but was: <toastSetRemoved>` |
| K14 | only T3 | T3: `java.lang.NumberFormatException: For input string: ""` |

| Id | Mutation (scratchpad copy, restored with `cp`) | Measured |
| --- | --- | --- |
| G1 | `ClickHandler.kt`: the reset title read through `resourceWrapper` | exit 1, failing X1 and X4 only: X1 = 8 (`ClickHandler` 2); X4 `(61, 11, 1, 2, 0, 0, 0)`, one lambda holding `getString(` and `resourceWrapper.` |
| G2 | `CommonHandler.kt`: `nowMillis = System.currentTimeMillis(),` | exit 1, failing X10 only: `currentTimeMillis` 1, `Clock` in `CommonHandler` 1 |
| G3 | `LiveWorkoutMapper.kt`: the skipped label read through `resourceWrapper` | exit 1, failing X1, X2, X4 and X5 only: X1 = 8, X2 adds `LiveWorkoutMapper`, X4 module reach 13, X5 flags `LiveWorkoutMapper` |

Each restored file's blob and mode equalled HEAD's, and the restored tree's census exited 0.

### B13.5 Exit census (Section B5.3), on commit 3

The census read commit 3's git objects and exited 0.

| Id | Input | Measured |
| --- | --- | --- |
| X1 | 63 production files | 7: `ClickHandler` 1 (`sendError`), `DialogClickHandler` 2 (`sendError`, `finish_success`), `ExercisePickerHandler` 4 (`sendError`, `toast_exercise_added`, `picker_create_format`, `picker_no_match_format`) |
| X2 | 63 | the three handlers |
| X3 | 63 | 0 and 0; `StringResolver` in `CommonHandler` only |
| X4 | 11 handler files, 69 module copy-resolving functions | 61, 11, 0 (0), 0, 0, 0 |
| X5 | the 5 pipeline files | all clean |
| X6 | 63 | 1, in `LiveWorkoutCopyReader`; that file alone imports `android.content.Context` |
| X7 | `CommonHandler` | 30 `copyReader.read(` calls with 30 distinct keys, equal to Section B2.1's 30, and one `stringResolver.getString(Res.string.core_ui_kit_plan_editor_unit_reps)`; `copyReader` in no other file |
| X8 | the 3 model files | no payload token (KDoc included), no feature `R` import |
| X9 | 2 catalogs | 94 and 2 in each; the diff from the base is Section B3.4's lines only |
| X10 | 94 module Kotlin files, every source set | no `currentTimeMillis`; `Clock.System.now().toEpochMilliseconds()` in `CommonHandler` 2 and `LiveWorkoutInteractorImpl` 1 |
| X11 | 63 | `ExercisePickerHandler` 0 / 0 in the two handlers' code; `val … : ExercisePicker,` in those two; implementer `ExercisePickerHandler`; 3 `@Binds` |

### B13.6 Repository, Native and device gates (Sections B6.5 and B6.6), on commit 3

| Gate | Executed | Result |
| --- | --- | --- |
| `assembleDebug lintDebug testDebugUnitTest --continue` | 2808/2808 | 479 suites, 3,391 cases, 0 failures. 32 of 34 rows name-identical to HOST_BASELINE; core:ui:kit gains 3 suites and 7 cases (T1–T7) and live-workout 2 suites and 14 cases (Section B4.4's names); no name removed and no outcome changed |
| lint, inside that gate | — | every report's findings (id, path, line text) equal to LINT_BASELINE: 80 each in app:app, app:dev and app:store, none in the other 36. No finding names a Section B8 path |
| `detekt --continue` | 63/63 | 0 findings in 55 fresh reports |
| `assembleDebugAndroidTest` | 2171/2171 | built |
| `verifyPaparazziDebug` | 675/675 | 174 suites, 1,787 cases, 0 failures: the baseline's rows, with the same 21 unit identities added (the task runs each module's unit tests); the 60 live-workout golden cases name-identical and passing; porcelain empty; blob set `53221eab…` |
| `:lint-rules:test` | 9/9 | 16 suites, 151 cases |
| `:app:wear:testStoreReleaseUnitTest … --tests '*ReleaseRuntimeBoundaryTest*'` | 107/107 | 3 cases |
| `assert_kmp_ui_source_topology.py`, five Pythons | — | byte-identical to BASE_GATE_OUTPUT (833 lines, sha256 `37315e70…`), exit 0, empty stderr |
| `assert_mvi_source_topology.py` | — | equal to BASE_MVI_TOPOLOGY |
| `personal_data_gate.py -v` | — | PASS |
| fifteen-module Native + oracle | 410/410 | 96 suites, 635 cases; every row's names and the oracle's output equal to NATIVE_BASELINE |
| fifteen-module link | 395/395 | 15/15 fresh `test.kexe` |

On `emulator-5554` (API 34, animations off), each suite ran with `ANDROID_SERIAL` set and its XML
read fresh. Around each suite a logcat writer (`adb -s emulator-5554 logcat -v threadtime`, after
`logcat -c`) ran to a file and was stopped by its PID.

| Suite | Executed | Result |
| --- | --- | --- |
| Smoke, then `assert_mvi_device_identities.py` | 2131/2131 | 44 cases, the 3 documented `pendingFeatureRewrite` skips, 0 failures; 2 device identities |
| Regression | 2131/2131 | 88 cases, 0 failures |

Smoke and Regression equal Part A's record (Section 14.6) name for name and outcome for outcome.
The logcat sweep parsed 31,024 lines around Smoke and 24,104 around Regression and found no line of
priority `E` whose tag starts with `SCREEN_`, so no `SCREEN_LiveWorkout` error line. The same files
hold `SCREEN_` lines at other priorities (121 `I/SCREEN_LiveWorkout` in Regression) and 1,606 and
601 `E` lines of other tags, so the sweep could see one. Before and after every suite the device
state (the three animation scales, rotation, keyguard) and the pre-installed dev app's
`lastUpdateTime` were unchanged, and no other instrumentation ran on the serial.

### B13.7 Environment

- Xcode 27.0 (27A266a) here, with iOS simulator runtimes 18.0, 18.2, 18.6 and 26.5; CI selects
  Xcode 26.6. JDK 21.0.11 (JBR).
- Pythons: 3.9.6 (`/usr/bin/python3`), 3.11.16, 3.13.16, 3.14.8 and 3.15.0 (`python3`). Controls
  ran under `python3` only.
- Gradle ran on a daemon registry private to this run, so another checkout's `--stop` could not
  reach it, and its own `--stop` reached no other daemon. No other Gradle client ran on the host.
- Emulator: `Pixel_6_API_34`, serial `emulator-5554` (API 34, `sdk_gphone64_arm64`), cold-booted
  twice with animations off; no other device was attached.
- Spotlight indexing was enabled on `/`; it was left as it was. Free disk stayed between 86 and
  92 GiB.

### B13.8 Deviations

1. **Section B2.1's "47 Android `stringResource(R.string.…)`".** The counts hold (50 composition
   reads: 47 Android, 3 CMP), but the 47 are 45 `stringResource(` and 2 `pluralStringResource(`
   calls: 40 with a literal `R.string` id, 2 with a conditional `R.string` expression, 3 with
   `DeleteExerciseCopyUiModel`'s ids and 2 with `R.plurals` ids. The census counts calls, as Section
   B0.2 says, and reports this shape rather than a mismatch.
2. **The base's repository gate, first run.** It executed 2808/2808 with every test passing, and
   exited 1 on `:core:data:database:copyAndroidMainLintReports`: `FileAlreadyExistsException` on
   `build/reports/lint-results.xml`, which `lintReportJvm` wrote concurrently (an untouched module).
   A second run on the same base was green, with all 34 rows and every lint finding identical to
   the first; HOST_BASELINE and LINT_BASELINE are that run's. The race did not recur on commit 3.
3. **Commit 2's first staged detekt run.** It reported `NoConsecutiveBlankLines` in
   `FakeExercisePicker.kt`: the script that transcribed Section B4.2's block kept a trailing blank
   line from the Markdown fence. The line was removed before the commit and the run repeated with 0
   findings; no commit carried it.
4. **One extra run before commit 3.** Because a fix must be a new commit, the module's tests also
   ran on commit 3's staged tree (26 suites, 241 cases, the same names); Section B6.4's gates then
   ran on the committed tree.
5. **tech-debt.md's first string-resources row** takes Section B8's quoted wording literally:
   "These five production files" became "These four".

### B13.9 Not verified on this host

- Xcode 26.6, which CI's KMP iOS kit smoke job runs; this host has 27.0.
- The iosArm64 test binaries run on a device: they are linked, never executed.
- OD1–OD4 on a device, which are the owner's check (Section B11.2), and the release Crashlytics
  effect of anything.

GitHub's signature verification, the CI run ids and the review classification go in the PR body
and the report, because they cannot exist before this commit does.

---

# Part C — the move: feature:live-workout becomes a shared feature entry (PR-M)

**Amendment of 2026-10-26.** Sections C0–C16 are Part C's contract, and Section C17 is filled by its
implementation. Sections 0–14 (Part A) and B0–B13 (Part B) above are records and are not rewritten;
Section C0.5 corrects three of their statements.

**Measured on:** dev @ `39be1b4ea2ed9954d332bee5ea8893f9e59dfeac` (2026-10-26, the merge of #345,
PR-P), statically: git data, greps and scripts. Nothing was compiled or run. The implementation
base is dev after this amendment lands, and Section C10.4 proves it carries this tree on every path
Part C reads.

**Prototype.** Every production, test, app, lint-rules, CI and document change of Sections C3–C10
was written against the measured tree in a scratch copy. Sections C2, C10.1, C11 and C12 ran on
that copy and on the measured tree: the topology gate and its controls under Python 3.9, 3.10,
3.11, 3.12, 3.13 and 3.14, byte-identical across them, and the Native oracle on synthetic XML. The
prototype is not evidence, because nothing in it was compiled. The implementation's fresh runs are.

## C0. Authority, decisions, choices, corrections

### C0.1 Authority order

1. The maintainer's decisions: D1–D8 of the Phase 7.16 discovery (GO, 2026-10-25), as Sections 2
   and B0.3 record them, and the rulings of Section C0.3.
2. This Part C: Sections C0–C16.
3. Part B's and Part A's contracts, where Part C does not replace them.
4. The Phase 7.15 specification (`kmp-phase-7-15-exercise-feature.md`), Part B, for every procedure
   this part names as inherited, read with the substitutions of Section C0.3.
5. testing.md, ci-cd.md, architecture.md and AGENTS.md.

A conflict between this part and a lower source is resolved here. A conflict this part does not
resolve is a STOP (Section C13).

### C0.2 Stable rules

Sections 0.2 and B0.2 apply, with these changes and additions:

- **The move is the change.** `feature/live-workout` becomes a KMP module on the shared convention:
  every source set moves, the catalogs become private Compose resources, and the code that cannot
  compile in common changes as Sections C3–C5 say. No behaviour changes beyond Section C0.6.
- **Build files, workflow, CI scripts.** Exactly `feature/live-workout/build.gradle.kts`,
  `app/common/build.gradle.kts`, `.github/workflows/android_build_unified.yml`,
  `.github/scripts/assert_kmp_ui_source_topology.py` and `.github/scripts/assert_kmp_ios_smoke.py`
  change, as Sections C5, C8 and C10 say. `gradle/libs.versions.toml` does not change.
- **Catalogs.** The two catalogs move with `git mv`; P1's twelve plural items (Section C2.4) are the
  only lines that change.
- **Suppressions.** No new suppression of any kind. The eleven production `@Suppress` annotations
  move byte-identical; `@SuppressLint("ModifierParameter")` leaves (CM4); the six test
  suppressions leave with the casts and the long lines the port removes; the host golden's
  `@Suppress("LongMethod")` stays (Section C2.7).
- **Goldens.** None is recorded. The 60 PNGs move with `git mv`, byte-identical, and
  `verifyPaparazziDebug` executes all 60. Any pixel change is a STOP (Section C13).
- **Census method.** Section B0.2's: a call is read from its name to its closing parenthesis, across
  lines, with comments and string literals blanked.
- **Test helpers.** A test class's helper types are nested in it. A top-level test declaration's
  name is unique across the module's whole host compilation (Part A's deviation 2), because
  commonTest and androidHostTest compile into one package set.
- **Controls.** Every identity this part adds has a known-negative control that turns it RED, and
  every new gate rule has one (Section C11).
- **Lint.** Android Lint is compared with LINT_BASELINE: a finding the baseline lacks, or one on a
  Section C7 path, is a STOP.
- **Copying from this document.** A block copied from a fence ends at its last code line: the
  fence's trailing blank line is not part of it (Part B's deviation 3).
- **One more test run before each commit.** Because a fix must be a new commit, the module's tests
  run on the staged tree before each commit, and the commit's gates then run on the committed tree
  (Part B's deviation 4).

### C0.3 Decisions

| Id | Decision, as it binds Part C |
| --- | --- |
| D1 | Part C moves the module to the shared convention. PR-M's move commit swaps Part B's reader for `StringResolver`'s no-argument path and deletes it (SM0). |
| D5, D6 | Part B's copy model, resolution at each load and synchronous filling stay as they are. Every read outside composition goes through the injected `StringResolver`. |
| D7 | The topology gate gains an additive field, `state_mutators`: the pure same-module classes a State lambda may return through in place of a `.copy(`. Its default `()` leaves every earlier entry's output byte-identical (Section C10.0). |
| D8 | The suites move to commonTest: `CommonHandlerExternalWritesTest` among them, ported off Robolectric. `CommonHandlerComposeResourceTest`, `CommonHandlerComposeResourceRuTest`, `LiveWorkoutTemplateParityTest` and `LiveWorkoutTemplateParityRuTest` retire; one commonTest identity pins the copy's resolution at the load through a recording resolver. `LiveSetRowSemanticsTest`'s check goes into the iOS scene. Kit's T6 and T7 keep the filler's parity on CMP. |
| SM0 | `LiveWorkoutCopyReader` is deleted. `resolveCopy` reads each of its 30 feature keys through `stringResolver.getString(LiveWorkoutRes.string.…)` with no format argument, which returns the stored template unfilled. The seven launch reads move from `ResourceWrapper` to `StringResolver`. |
| XB4 | `state_mutators` covers `LiveSetMutator`, `StateStatusMapper` and `LiveWorkoutMapper` at least. Measured, the closure is six classes (CM12). |
| Locale | Whether the copy should re-resolve on a locale change is the maintainer's question (Section C0.5, XC1). Until the maintainer rules otherwise, Part C adds no mechanism and records the property in tech-debt.md (CM16). A ruling before the run that asks for a mechanism is a STOP-and-amend. |

The 7.15 procedures this part inherits read with: exercise → live-workout; `ExerciseFeature` /
`ExerciseGraph` / `createExerciseGraph` / `exerciseGraphFactory` / `exerciseGraph` →
`LiveWorkoutFeature` / `LiveWorkoutGraph` / `createLiveWorkoutGraph` / `liveWorkoutGraphFactory` /
`liveWorkoutGraph`; the fifteen modules → the sixteen of Section C10.3; four Context readers → three
before and two after.

### C0.4 Choices this part makes (CM1–CM17)

| Id | Choice | Why |
| --- | --- | --- |
| CM1 | The generated `Res` stays internal (CMP's default) in `io.github.stslex.workeeper.feature.live_workout.resources`. A file that also reads kit's `Res` imports the feature's as `LiveWorkoutRes`. | 7.10–7.15's private resource owner. Kit's `Res` keeps its name where both are read (`CommonHandler`, `LiveSetRow`, the scene, `CommonHandlerTest`). |
| CM2 | `sendError` in the three launch-reading handlers and `ExercisePickerHandler.derivePickerLabels` become `suspend`. | `StringResolver.getString` suspends. Every caller already runs inside a launch body, an `onError` or an `onSuccess`, or `PendingUndoOps.undoPending`'s `onError`, all of them suspend. |
| CM3 | The added toast resolves into `val addedMessage` before the add's write, not inside `PendingUndo(…)`. | A suspend read inside the undo's construction would put a suspension between the write and its undo push. The gate pins the order (Section C10.1). |
| CM4 | The screen's private `Body` takes `modifier: Modifier = Modifier` as its first optional parameter, applied to its `Box`, and `activeSessionBannerModifier` loses `@SuppressLint("ModifierParameter")`. | `android.annotation.SuppressLint` is Android-only. With the default the tree is unchanged, and the parameter order is what Compose's rule asks for. |
| CM5 | `LiveWorkoutStoreImpl`'s private `NAME` loses `@VisibleForTesting`. | AndroidX annotations are forbidden in common code, and `NAME` is private. |
| CM6 | The click-path suites port their relaxed mocks as relaxed fakes (`RelaxedHandlerStore`, `RecordingInteractor`, `RelaxedStringResolver`, `SilentLogger`); suites that were strict stay strict. | Their identities rely on the relaxed mocks' defaults (an unstubbed read answering `""`, a launch running nothing). A strict fake would change what they observe. |
| CM7 | D8's identity lives in `CommonHandlerTest`, on a strict `CatalogStringResolver` keyed by `NAMED_COPY`. | The fixture already maps each field to a distinct value, so a read of the wrong key shows in the copy. |
| CM8 | The interactor port states what the mock left implicit: three finish identities assert the empty discard list, `cancelSession deletes the session row` seeds the session, and the add-exercise identity asserts the whole call. | A recording fake reads back every argument; the stub matched only those values. No identity's outcome changes. |
| CM9 | `CommonHandlerExternalWritesTest` turns `Log.isLogging` off for each identity and restores it, uses a lock-free deque on `kotlin.concurrent.atomics.AtomicReference`, a 2 ms wall-clock busy-wait where it slept, a blank resolver, and its own `FakeLifecycleOwner` nested in the class. | The plain Android host has no Robolectric, so `android.util.Log` throws; `java.util.concurrent` and `Thread.sleep` are JVM-only; the identities pin structure, not copy (core:ui:mvi's `StoreEventPressureTest` is the precedent for the logging switch). |
| CM10 | `payload_files` holds twelve files: F5's seven and the other five models. | 7.15's precedent: every Store-side payload and UI model. |
| CM11 | Each fake's unreached members fail fast with `Nothing`; the `@Deprecated` member that mirrors a deprecated repository API carries its own message. | 7.10–7.15's fake rules. |
| CM12 | `state_mutators` is the six-class closure `LiveSetMutator`, `StateStatusMapper`, `LiveWorkoutMapper`, `LiveSetRowsResolver`, `LiveWorkoutDomainMapper`, `ExerciseDoneRule`, and D7's contract checks the closure. | A lambda that returns through a listed class is only as pure as everything that class reaches. |
| CM13 | The host golden's inherited `@Suppress("LongMethod")` stays. | It is inherited; the rule forbids new suppressions, not old ones. |
| CM14 | `NavigationResultTest`'s KDoc names the moved key. | It cites `R.string.feature_live_workout_status_no_plan`, which no longer exists. Comment only. |
| CM15 | The iOS scene renders all five sheets and all five dialogs the screen routes, plan-editor's shared picker and kit's delete dialog among them, but not the undo toast. | No Native scene renders those two shared composables today. The toast's five-second `delay` would put a timing-dependent action in the scene's exact list. |
| CM16 | No locale re-resolution mechanism; a tech-debt row records the property (Section C0.3). | The maintainer's open question; the property matches the shared features' `Init`-resolved copy. |
| CM17 | The dead delete-session surface (Section C16) is recorded, not removed. | Removing it changes the Store contract, two handlers, the copy and kit's dialog: out of scope. |

### C0.5 Corrections to Parts A and B

- **XC1. Section B0.5's OD1 names the wrong mechanism.** Android does not recreate the Activity on a
  locale change: `app/app/src/main/AndroidManifest.xml` gives `MainActivity` `configChanges` with
  `locale` and `layoutDirection`, `MainActivity` neither overrides `onConfigurationChanged` nor
  calls `recreate()`, and `Init` re-fires only when the screen re-enters composition
  (`StoreProcessor`'s `DisposableEffect`). So the composition reads relabel at once (by the library
  sources: Android's `stringResource` reads `LocalResources`, and CMP's environment is remembered on
  `Locale.current` under `LocalConfiguration`; not measured on a device), while the copy in State
  keeps the old locale until the next load: re-entry to the screen, or a plan-editor save. PR #345's
  body carries the corrected text.
- **XC2. Section B8's tech-debt wording.** It quoted "These five production files" → "These four",
  and the row now reads "These four call CMP's top-level `getString`…". Part C's docs commit
  restores "These four production files" and splits the row's last sentence (Section C7).
- **XC3. Section B2.1's "47 Android `stringResource(R.string.…)`".** The count holds, but the shape
  is 45 `stringResource(` and 2 `pluralStringResource(` calls (Part B's deviation 1).

### C0.6 Observable differences

- **OC1.** live-workout's strings now resolve through Compose resources, which choose by the primary
  system locale only (Phase 7.9 Section 5.7). On a device whose first locale has no catalog, the
  screen falls back to the default (EN) catalog, where Android resources walked the locale list
  first. EN and RU devices see what they saw.
- **OC2.** None in rendering: the 60 goldens stay byte-identical (Section C9.3).

## C1. Bounded exit claim

At exit:

- `feature:live-workout` is a shared feature entry: production in `commonMain`, compiled for
  Android, `iosArm64` and `iosSimulatorArm64`; private Compose resources; the factory flow of
  Section C5; no `Context`, no `ResourceWrapper`, no reader (Section C12, X1–X3).
- Its 18 portable suites, 226 exact identities, execute in `commonTest` on the Android host and on
  the iOS simulator; the production scene adds one Native identity (Section C9).
- The 30 golden methods render their 60 byte-identical PNGs from `androidHostTest`; the 6 device
  identities run from `androidDeviceTest`; the 7 extension identities reach the graph through
  `AppRootDeps`.
- The topology gate holds the module to `LIVE_WORKOUT_ENTRY` (Section C10.1); D7's field lets its
  eleven delegating State lambdas pass; the Native oracle requires 227 exact tuples among sixteen
  modules; the KMP iOS kit smoke job runs and links sixteen modules.
- The Context readers outside shared entries drop from 3 to 2 (app-dialogs and settings).

Not claimed: anything Section C9.6 lists, the iOS host's own wiring, or a device run of the Native
code.

## C2. Measured baseline (at `39be1b4ea`)

Paths below are relative to `feature/live-workout/src/<set>/kotlin/io/github/stslex/workeeper/feature/live_workout/`
unless they start with `src/`, `app/`, `lint-rules/`, `feature/`, `core/`, `.github/` or
`documentation/`.

### C2.1 Production manifest

`src/main` holds 63 Kotlin files and the two catalogs; at exit `src/commonMain` holds 62 and the
two catalogs. The module's 157 tracked paths become 154 (Section C7).

| Kind | Files |
| --- | --- |
| deleted | `mvi/handler/LiveWorkoutCopyReader.kt` |
| moved, content changed (14) | `di/LiveWorkoutFeature.kt`, `mvi/handler/ClickHandler.kt`, `mvi/handler/CommonHandler.kt`, `mvi/handler/DialogClickHandler.kt`, `mvi/handler/ExercisePickerHandler.kt`, `mvi/mapper/DeleteExerciseCopyMapper.kt`, `mvi/mapper/ErrorTypeMapper.kt`, `mvi/store/LiveWorkoutStoreImpl.kt`, `ui/LiveWorkoutGraph.kt`, `ui/LiveWorkoutScreen.kt`, `ui/components/FinishConfirmDialog.kt`, `ui/components/LiveExerciseCard.kt`, `ui/components/LiveSetRow.kt`, `ui/components/SessionSheets.kt` |
| moved byte-identical (48) | every other Kotlin file |
| moved, P1 only | `res/values/strings.xml` → `composeResources/values/strings.xml`, and `values-ru` likewise |

What the measured tree holds that common code cannot:

| Item | At the base | At exit |
| --- | --- | --- |
| the feature's Android `R` | 11 files import it; 101 `R.string.` / `R.plurals.` references | 0; the same 101 as `Res.` / `LiveWorkoutRes.` in 11 files |
| `android.*` imports | `LiveWorkoutCopyReader.kt` (`android.content.Context`), `ui/LiveWorkoutScreen.kt` (`android.annotation.SuppressLint`) | none |
| `androidx.annotation` | `@StringRes` in `DeleteExerciseCopyMapper.kt` and `ErrorTypeMapper.kt`; the reader; `@VisibleForTesting` in `LiveWorkoutStoreImpl.kt` | none (the mappers return `StringResource`) |
| `androidx.activity` | `ui/LiveWorkoutGraph.kt`'s `BackHandler` | CMP's `androidx.compose.ui.backhandler.BackHandler`, under `@OptIn(ExperimentalComposeUiApi::class)` |
| the app `Context` | `LiveWorkoutFeature`'s `LocalContext.current.appDeps<LiveWorkoutGraph.Factory>()`; the reader's `context.getString(id)` | none (Section C5; SM0) |

Copy reads, with Section C0.2's census method:

| Read | At the base | At exit |
| --- | --- | --- |
| `copyReader.read(` | 30, `CommonHandler` | 0 |
| `resourceWrapper.getString(` | 7: `ClickHandler` 1, `DialogClickHandler` 2, `ExercisePickerHandler` 4 | 0 |
| `stringResolver.getString(` | 1, `CommonHandler` (kit's reps unit) | 38: `CommonHandler` 31, `ClickHandler` 1, `DialogClickHandler` 2, `ExercisePickerHandler` 4 |
| `context.getString(` | 1, the reader | 0 |
| composition reads | 50 in 6 files: 45 `stringResource(` and 2 `pluralStringResource(` on Android resources, and `LiveSetRow`'s 3 CMP `stringResource(` on kit's | the same 50, all CMP |

The State lambdas, with Section B2.2's method, are unchanged by the move: 61 lambdas, 11 without
`.copy(`, no token, no resolver reference, no same-file helper call, no module reach. The eleven
return `LiveSetMutator`'s State through the file's `setMutator` property: `ClickHandler` 8,
`CommonHandler` 2 (one call and one `setMutator::applyExternalWrite` reference) and
`DialogClickHandler` 1.

### C2.2 Portable manifest — 18 suites, 225 identities at the base, 226 at exit

Every suite moves from `src/test` to `src/commonTest` under its path, keeps its class name, and
keeps every identity's name and assertions (Section C9.1 says what the port changes around them).

| Suite | Identities | Port |
| --- | ---: | --- |
| `domain/LiveWorkoutInteractorImplTest` | 29 | MockK → recording repository fakes |
| `mvi/handler/ClickHandlerTest` | 26 | MockK → relaxed fakes |
| `mvi/handler/CommonHandlerExternalWritesTest` | 24 | Robolectric and MockK → in-file fakes, a real `BaseStore` |
| `mvi/handler/CommonHandlerTest` | 3 → 4 | MockK → strict fakes; + D8's identity |
| `mvi/handler/DialogClickHandlerTest` | 29 | MockK → relaxed fakes |
| `mvi/handler/ExercisePickerHandlerTest` | 12 | MockK → relaxed fakes |
| `mvi/handler/ExternalWriteCoverageTest` | 6 | `kotlin.test` |
| `mvi/handler/InputHandlerTest` | 1 | MockK → a strict store fake |
| `mvi/handler/LiveSetDraftBehaviorTest` | 10 | MockK → relaxed fakes |
| `mvi/handler/NavigationHandlerTest` | 4 | MockK → a recording navigator |
| `mvi/mapper/DeleteExerciseCopyMapperTest` | 4 | `kotlin.test`; `R.string` → `Res.string` |
| `mvi/mapper/LiveSetMutatorTest` | 27 | `kotlin.test` |
| `mvi/mapper/LiveSetVisibleRowsResolverTest` | 8 | `kotlin.test` |
| `mvi/mapper/LiveWorkoutMapperReloadTest` | 5 | `kotlin.test` |
| `mvi/mapper/LiveWorkoutMapperTest` | 20 | `kotlin.test` |
| `mvi/mapper/StateStatusMapperTest` | 7 | `kotlin.test` |
| `mvi/model/UnfilledSetTest` | 6 | `kotlin.test` |
| `mvi/store/LiveWorkoutStateRouteArgTest` | 4 | `kotlin.test` |

Part B's two shared test files, `mvi/handler/FakeExercisePicker.kt` and
`mvi/model/LiveWorkoutCopyFixtures.kt` (`NAMED_COPY`), move byte-identical.

Retired, with their files (D8): `CommonHandlerComposeResourceTest` and
`CommonHandlerComposeResourceRuTest` (one file, 4 identities), `LiveWorkoutTemplateParityTest` and
`LiveWorkoutTemplateParityRuTest` (one file, 2), `ui/components/LiveSetRowSemanticsTest` (1). Added:
`CommonHandlerTest`'s `a load resolves every copy field from its own catalog key` (D8, Section
C9.2) and the scene's `resourcesScreenSheetsDialogsAndActionsRenderAndDispatch` (Section C9.5).

Across all of the module's test sources the `@Test` and golden-method names go from 277 (271 in
`src/test`, 6 in `src/androidTest`) to 272: commonTest 226, iosTest 1, androidHostTest 39
(`ExerciseDoneRuleTest` 7, the two gates 2, the golden methods 30) and androidDeviceTest 6. No other
name changes. The 227 names of commonTest and iosTest serialize, as
`"".join(f"{path}\t{name}\n")` over the files sorted by module-relative path (`src/…`) and each
file's names in source order, to sha256
`2d7f45ff65deef68bd5517e737ec33f4f049cdcf5e81fc9d41fb8e6d03e8b1fc`.

### C2.3 Golden and device ownership

| Class | Methods (source order) |
| --- | --- |
| `golden/SessionStateGoldenTest` | `setPlain`, `setDone`, `setPersonalRecord`, `setUnfilled`, `setPersonalRecordDone`, `setTwoDigitReps`, `setBodyweight`, `setFlashPeak`, `exercisePending`, `exerciseActive`, `exerciseSingleRow`, `exerciseFinished`, `exerciseFinishedReopened`, `exerciseSkipped`, `exerciseOneOff`, `exerciseOneOffFinished`, `exerciseTenSets`, `exerciseBodyweightRu` |
| `golden/SessionHeaderGoldenTest` | `topbar`, `sheadDefault`, `sheadSkippedMeta`, `sheadEditing` |
| `golden/SessionScreenGoldenTest` | `sessionScreen` |
| `golden/SessionSheetsGoldenTest` | `sessionMenuSheet`, `exerciseMenuSheet`, `exerciseMenuSheetSkipped`, `deleteExerciseSheetPlanned`, `deleteExerciseSheetAdhoc`, `descriptionSheet`, `undoToast` |

30 methods, each light and dark: 60 PNGs under `src/test/snapshots/images/`, blob set
`53221eab422589d95e416c6de0b32e3b2d4d0112a8e70a1b2344cf171f17bb67` (Section B2.6's definition).
`exerciseBodyweightRu` is the one golden that renders a Russian catalog (`locale = LOCALE_RU`).
Beside them in `src/test`: `gate/LiveSetRowOverflowGateTest` (1), `gate/SetColumnAlignmentGateTest`
(1) and `mvi/mapper/ExerciseDoneRuleTest` (7, on `core:data:database-test`'s
`WorkoutTargetParityFixture`, 7.13 D4-A). The golden gate's plain-run filter excludes only
`*.golden.*`, so these three run in every host test task.

`src/androidTest/…/LiveWorkoutScreenTest.kt` holds 6 `@Smoke` identities:
`screen_renders_with_expected_root_test_tag`, `finish_button_dispatches_OnFinishClick`,
`add_set_button_dispatches_OnAddSet_with_exercise_uuid`,
`type_chip_click_dispatches_OnSetTypeSelect_with_current_chip_type`,
`checkbox_click_on_undone_row_dispatches_OnSetMarkDone` and
`checkbox_click_on_done_row_dispatches_OnSetUncheck`.

### C2.4 Catalog

Each locale holds 94 strings and 2 plurals (143 EN lines, 128 RU lines); the keys and their order
are equal in both. P1: CMP fills only positional placeholders (`%N$d`, `%N$s`; Section B0.4's CB4),
and the gate requires them in every commonMain catalog, so the plurals' bare `%d` become `%1$d`:

| Catalog | Lines | Base → exit |
| --- | --- | --- |
| EN | 83–84, 88–89 | `feature_live_workout_finish_new_pr_title` `one` and `other`, `feature_live_workout_finish_unfilled_discarded` `one` and `other`: `%d` → `%1$d` |
| RU | 71–74, 78–81 | the same two plurals' `one`, `few`, `many` and `other`: `%d` → `%1$d` |

Nothing else changes, `tools:ignore` attributes included (kit, exercise, home and single-training
keep theirs in Compose resources). At exit the EN catalog's sha256 is
`1f4f87cb70965c45434ae197c5f4523192c58d4df973907a301445aa6b4eecee` and the RU catalog's
`4e83b59effd14bd0934c3c03eff832a4381a4b4442acf6fa35a12aab9c97edd4` (the base files are `e201fa09…`
and `f4b9ad01…`).

### C2.5 Declared dependencies

The base build file applies `convention.composeLibrary`, Metro with `includeJavax()` and Paparazzi;
`implementation` on `core:core`, `core:ui:kit`, `core:ui:mvi`, `core:ui:navigation`,
`core:ui:plan-editor`, `core:data:database` and `core:data:exercise`; `testImplementation` on
`kotlin("test")`, `core:data:database-test`, `core:ui:golden-harness` and
`androidx.compose.ui.test.junit4` (for `LiveSetRowSemanticsTest`); `androidTestImplementation` on
`libs.bundles.android.test`, `androidx.compose.ui.test.junit4` and `core:ui:test-utils`, and
`debugImplementation` on `ui-test-manifest`. Section C8 is the exit file.

### C2.6 External consumers and the root flow

- `app/common/build.gradle.kts`: `implementation(project(":feature:live-workout"))`, the only
  feature edge besides settings' that is not `api`.
- `app/common/…/host/AppNavigationHost.kt` calls `liveWorkoutGraph(modifier = …)`; `App.kt` and
  `AppRootDeps.kt` carry no live-workout factory; `app/app/…/di/AppGraph.kt` exposes none.
- `app/app/src/test/…/di/LiveWorkoutExtensionIdentityTest.kt` (7 identities) reaches the graph
  through `asContribution<LiveWorkoutGraph.Factory>()`.
- `app/app/src/androidTest/…/app/NavigationResultTest.kt` inlines `"no plan"` with a KDoc that names
  `R.string.feature_live_workout_status_no_plan`.
- `lint-rules`' `ActiveSurfaceSingleReaderRule.PERMITTED_READERS` and its test name
  `feature/live-workout/src/main/…/ui/components/LiveExerciseCard.kt`.
- The topology gate's `EXPECTED_APP_DEPS_READERS` holds three readers: app-dialogs, live-workout and
  settings.
- The device journeys that reach the screen: `RouteReachabilityTest`'s
  `liveWorkoutOpensBlankFromHomeAndHomeReturns`,
  `liveWorkoutIsReEnteredThroughTheActiveSessionBanner` and
  `planEditorOpensFromALiveSessionExerciseAndTheSessionReturns`,
  `NavigationResultTest.planEditorSaveReachesTheLiveSessionThatOpenedIt` and
  `StoreRetentionTest.liveWorkoutStoreSurvivesThePlanEditorRoundTrip`.
- No other path in the repository composes the graph or names its source sets, except
  `graphify-out/graph.json`, a generated snapshot last regenerated on 2026-09-03 that still names
  exercise's `src/main` paths too; it stays as it is.

### C2.7 Suppressions

- Production, 12 in 10 files (Section B2.6): the eleven `@Suppress` annotations move byte-identical
  under their `src/commonMain/…` paths; `@SuppressLint("ModifierParameter")` in
  `ui/LiveWorkoutScreen.kt` leaves (CM4). At exit: 11 in 9 files.
- Tests, 7 in 5 files: `@Suppress("MaximumLineLength")` (`LiveWorkoutInteractorImplTest`) and
  the five `@Suppress("UNCHECKED_CAST")` casts (`ClickHandlerTest` 2, `DialogClickHandlerTest` 2,
  `CommonHandlerExternalWritesTest` 1) leave with the port; `SessionScreenGoldenTest`'s
  `@Suppress("LongMethod")` stays (CM13). At exit: 1.

### C2.8 Other facts this part relies on

- origin/dev is `39be1b4ea`; its push run of `Android CI/CD - Unified Build and Tests` is
  `40350332440`, which concluded success. The merge's tree equals the PR head's and
  `git merge-tree` of its parents; GitHub verified it; the first-parent diff is Section B8's 39
  paths.
- The topology gate on dev: exit 0, 833 lines, sha256
  `37315e70b6606acdc9cffaa68b5c82bba7cbbf1006f6a2f1e28f7071e3eca235`, byte-identical under
  Python 3.9 through 3.14 (BASE_GATE_OUTPUT). The script's sha256 is
  `679d6e9f1a6bdd2c2e82835acc1818595884984497f6f525a109f70fbe506746`.
- The Native oracle requires fifteen modules; the workflow runs and links fifteen.
- CMP 1.11.1's `getString(resource)` with no format argument returns the stored template unfilled;
  with arguments it applies `%(\d+)\$[ds]` (Section B0.4's CB4). `StringResolver.getString`
  delegates to it.
- Native test names: no live-workout identity holds a character Kotlin/Native rejects (`.` `;` `,`
  `(` `)` `[` `]` `{` `}` `/` `<` `>` `:` `\` `$` `&` `~` `*` `?` `#` `|` `§` `%` `@`), so no
  `INVALID_CHARACTERS_NATIVE_ERROR` suppression is needed. Two names hold `—`: `ClickHandlerTest`'s
  `OnExerciseHeaderClick collapse is pure — sets statuses and siblings untouched` and
  `LiveWorkoutMapperTest`'s `first entry expands exactly the first card — even a completed one`; the
  oracle compares them from the UTF-8 XML.
- On the plain Android host (no Robolectric; `isReturnDefaultValues` unset) `android.util.Log`
  throws, and the real `BaseStore` logs every consumed action.

## C3. Platform and API blockers — settled

### C3.1 Android resources become private Compose resources

- `compose.resources { packageOfResClass = "io.github.stslex.workeeper.feature.live_workout.resources" }`
  (Section C8). The generated `Res` stays internal (CM1), so no other module reads the catalog; an
  app test that needs a string inlines it (CM14).
- `git mv` moves `src/main/res/values/strings.xml` and `src/main/res/values-ru/strings.xml` to
  `src/commonMain/composeResources/values/` and `…/values-ru/`, then P1 (Section C2.4).
- Each read imports the `Res` object and its generated accessor
  (`import io.github.stslex.workeeper.feature.live_workout.resources.feature_live_workout_…`). Where
  a file reads kit's `Res` too, the feature's is imported `… .resources.Res as LiveWorkoutRes`, last
  among the imports, where ktlint's import order puts aliases (exercise's precedent; CM1).

### C3.2 Android and JVM APIs leave common code

| Base | Exit | Where |
| --- | --- | --- |
| `androidx.compose.ui.res.stringResource` / `pluralStringResource` with `R.string.…` / `R.plurals.…` | `org.jetbrains.compose.resources.stringResource` / `pluralStringResource` with `Res.string.…` / `Res.plurals.…`, the same arguments | the 47 Android composition reads (Section C2.1) |
| `@StringRes fun …(): Int` returning `R.string.…` | `fun …(): StringResource` returning `Res.string.…` | `DeleteExerciseCopyMapper`'s `titleRes()`, `bodyRes()`, `confirmRes()`; `ErrorTypeMapper.messageRes()` |
| `androidx.activity.compose.BackHandler` | `androidx.compose.ui.backhandler.BackHandler`, with `@OptIn(ExperimentalComposeUiApi::class)` on `liveWorkoutGraph` | `ui/LiveWorkoutGraph.kt` (7.15's precedent) |
| `context.appDeps<LiveWorkoutGraph.Factory>()` on `LocalContext.current` | the constructor-held factory (Section C5) | `di/LiveWorkoutFeature.kt` |
| `LiveWorkoutCopyReader(context)` | `StringResolver`'s no-argument path (SM0) | `mvi/handler/CommonHandler.kt` |
| `@SuppressLint("ModifierParameter")` | `Body`'s own `modifier` (CM4) | `ui/LiveWorkoutScreen.kt` |
| `@VisibleForTesting` | nothing (CM5) | `mvi/store/LiveWorkoutStoreImpl.kt` |
| `@Preview(uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES / _NO)` pairs | named `ThemeMode` previews (SM2, Section C4.5) | `ui/components/LiveSetRow.kt` |

In the tests: JUnit 5 → `kotlin.test`; MockK → in-file fakes; Robolectric → nothing (Section C9.1);
`java.util.concurrent` and `java.util.Collections` → `kotlin.concurrent.atomics`; `Thread.sleep` →
a `kotlin.time.Clock` busy-wait (CM9). No commonTest file uses `runBlocking`; the iOS scene does, as
every earlier scene does.

### C3.3 Native test names

None of the 227 names holds a character Kotlin/Native rejects (Section C2.8). No
`@file:Suppress("INVALID_CHARACTERS_NATIVE_ERROR")` is added; the gate's suppressions field pins
zero of them.

## C4. Copy, resources and previews

### C4.1 SM0 — the load's copy through `StringResolver`

`CommonHandler` loses its `copyReader: LiveWorkoutCopyReader` parameter; its constructor is
`(interactor, stringResolver, setMutator, storeDispatchers, store)`. `resolveCopy` keeps Part B's
shape and order (Section B3.7) and reads each field through the resolver with no format argument:

~~~kotlin
    /** D5: each field from its own key, read verbatim, so a template keeps its placeholders. */
    private suspend fun resolveCopy(): LiveWorkoutCopy = LiveWorkoutCopy(
        trainingNamePlaceholder = stringResolver.getString(
            LiveWorkoutRes.string.feature_live_workout_training_name_placeholder,
        ),
        statusSkipped = stringResolver.getString(LiveWorkoutRes.string.feature_live_workout_status_skipped),
        statusNoPlan = stringResolver.getString(LiveWorkoutRes.string.feature_live_workout_status_no_plan),
        repsUnit = stringResolver.getString(Res.string.core_ui_kit_plan_editor_unit_reps),
        metaTemplate = stringResolver.getString(LiveWorkoutRes.string.feature_live_workout_meta_format),
        // … each remaining field of Section B2.1's table, in its order, as
        // `<field> = stringResolver.getString(LiveWorkoutRes.string.<its key>),`, wrapped at 120 columns
        errorFinishFailed = stringResolver.getString(LiveWorkoutRes.string.feature_live_workout_error_finish_failed),
    )
~~~

That is 31 `stringResolver.getString(` calls, 30 on the feature's keys and one on kit's reps unit,
each key once, none with an argument. `mvi/handler/LiveWorkoutCopyReader.kt` is deleted; it had no
`@Binds`, so the graph loses nothing else. Nothing else in `CommonHandler` changes: the load, the
reload, the timer, `coveredLoad`'s compare-and-set and the watch writes stay byte-identical apart
from imports.

### C4.2 The seven launch reads

Each launch-reading handler swaps `private val resourceWrapper: ResourceWrapper,` for
`private val stringResolver: StringResolver,` in the same position, and reads on use:

| Handler | Reads at exit |
| --- | --- |
| `ClickHandler` | `sendError`: `stringResolver.getString(type.messageRes())` |
| `DialogClickHandler` | `sendError`, and `onSuccess`'s `stringResolver.getString(Res.string.feature_live_workout_finish_success)` |
| `ExercisePickerHandler` | `sendError`; the added toast (CM3); `derivePickerLabels`' create label and no-match headline, each with the trimmed query |

`private suspend fun sendError(type: ErrorType)` in all three, and
`private suspend fun derivePickerLabels(` (CM2). The added toast:

~~~kotlin
            // «{name}» добавлено — resolved before the write, so no suspension separates the write
            // from its undo window.
            val addedMessage = stringResolver.getString(
                Res.string.feature_live_workout_toast_exercise_added,
                picked.name.truncateForToast(),
            )
            updateState { latest ->
                val nextExercises = (
~~~

and the undo's `message = addedMessage,` replaces the read the base made inside `PendingUndo(…)`;
the comment above `pushUndo(` becomes `// Undo removes the rows the add just wrote.`. Every click
path that reads the copy keeps reading `State.liveCopy` (D6); no click path gains a launch.

### C4.3 Composition reads

The 47 Android composition reads keep their call sites and arguments; only the function's package
and the resource handle change (Section C3.2). `RailMetaRow`'s `when` maps `RailDetail` to
`Res.string.…` handles; `FinishConfirmDialog`'s two plurals read
`pluralStringResource(Res.plurals.…, count, count)`; `LiveExerciseCard` keeps `.uppercase()` on the
one-off badge; `LiveSetRow`'s three reads of kit's catalog are CMP already and stay as they are.

### C4.4 The resource-id mappers

`mvi/mapper/DeleteExerciseCopyMapper.kt` and `mvi/mapper/ErrorTypeMapper.kt` keep every branch and
return `org.jetbrains.compose.resources.StringResource`: `Res.string.<same key>` for each
`R.string.<key>`. `DeleteExerciseCopyUiModel` and `ErrorType` stay semantic payloads (Part B's SP4);
the mappers are UI-side and are not payload files.

### C4.5 Previews

- **SM2.** `LiveSetRow`'s three `uiMode` pairs become six named previews, each a two-line wrapper
  over a private function that takes the theme:

  ~~~kotlin
  @Preview(name = "Pending Light", showBackground = true)
  @Composable
  private fun LiveSetRowPendingLightPreview() {
      LiveSetRowPendingPreview(themeMode = ThemeMode.LIGHT)
  }

  @Preview(name = "Pending Dark", showBackground = true)
  @Composable
  private fun LiveSetRowPendingDarkPreview() {
      LiveSetRowPendingPreview(themeMode = ThemeMode.DARK)
  }

  @Composable
  private fun LiveSetRowPendingPreview(themeMode: ThemeMode) {
      AppTheme(themeMode = themeMode) {
          // the base preview's body, unchanged
      }
  }
  ~~~

  and the same for `Done` and `Weightless`. The six argument lists are the gate's
  `preview_inventory`.
- The 16 bare `@Preview` annotations stay: `ui/LiveWorkoutScreen.kt` 5,
  `ui/components/FinishConfirmDialog.kt` 3, `ui/components/LiveExerciseCard.kt` 5 and
  `ui/components/LiveWorkoutHeader.kt` 3 (7.13's D5). No other commonMain file holds one.

### C4.6 What does not change

The copy model, the filler, the State and its 30 fields, the 61 State lambdas, the Store and its
actions and events, the handlers' click paths, the pipeline, the watch-write path, the layout and
every test tag. Beyond the resolver swap, the only signature and control-flow changes inside the
module are CM2's `suspend` modifiers, CM3's order, CM4's `Body` parameter, the mappers' return type
(Section C4.4), the previews (Section C4.5) and the factory flow (Section C5).

## C5. The generation-owned shape-B factory flow

As 7.15 Section B6, for live-workout:

- `di/LiveWorkoutFeature.kt`:

  ~~~kotlin
  internal class LiveWorkoutFeature(
      private val factory: LiveWorkoutGraph.Factory,
  ) : FeatureAssisted<
      LiveWorkoutStoreProcessor,
      Screen.LiveWorkout,
      >() {

      @Suppress("UNCHECKED_CAST")
      @Composable
      override fun processor(screen: Screen.LiveWorkout): LiveWorkoutStoreProcessor =
          rememberMetroStoreProcessor<LiveWorkoutStoreImpl> {
              factory.createLiveWorkoutGraph(screen).liveWorkoutStore
          } as LiveWorkoutStoreProcessor
  }
  ~~~

  The KDoc gains "The generation-owned [LiveWorkoutGraph.Factory] arrives by constructor from
  `AppRootDeps`." The `LocalContext` and `appDeps` imports leave.
- `ui/LiveWorkoutGraph.kt`: `fun NavGraphScope.liveWorkoutGraph(factory: LiveWorkoutGraph.Factory,
  modifier: Modifier = Modifier)` registers through
  `navComponentScreenWithResults(LiveWorkoutFeature(factory))`; the rest of the body is unchanged.
- `app/common/…/di/AppRootDeps.kt`: after `exerciseGraphFactory`,

  ~~~kotlin
      /** Generation-owned factory for the live-workout graph extension. */
      val liveWorkoutGraphFactory: LiveWorkoutGraph.Factory
  ~~~

- `app/app/…/di/AppGraph.kt`: `override val liveWorkoutGraphFactory: LiveWorkoutGraph.Factory` after
  `exerciseGraphFactory`'s override, and its import.
- `app/common/…/App.kt`: `liveWorkoutGraphFactory = deps.liveWorkoutGraphFactory,` after
  `exerciseGraphFactory = deps.exerciseGraphFactory,`, inside the admitted
  `remember(currentPhase.id) { … }` that reads `AppRootDeps` once (7.10's admission).
- `app/common/…/host/AppNavigationHost.kt`: the parameter
  `liveWorkoutGraphFactory: LiveWorkoutGraph.Factory,` after `exerciseGraphFactory`'s, and
  `factory = liveWorkoutGraphFactory,` as the first argument of `liveWorkoutGraph(`.
- `app/common/build.gradle.kts`: `implementation(project(":feature:live-workout"))` leaves its
  place and `api(project(":feature:live-workout"))` follows `api(project(":feature:exercise"))`.
  `app/app` keeps its direct `implementation` edge.
- `app/app/src/test/…/di/LiveWorkoutExtensionIdentityTest.kt`: the helper becomes

  ~~~kotlin
      private fun AppGraph.liveWorkout(
          sessionUuid: String? = null,
          trainingUuid: String? = null,
      ): LiveWorkoutGraph = liveWorkoutGraphFactory.createLiveWorkoutGraph(
          Screen.LiveWorkout(sessionUuid = sessionUuid, trainingUuid = trainingUuid),
      )
  ~~~

  and the `asContribution` import leaves. The seven identities keep their names and bodies.
- `lint-rules`: `ActiveSurfaceSingleReaderRule.PERMITTED_READERS` names
  `feature/live-workout/src/commonMain/kotlin/io/github/stslex/workeeper/feature/` +
  `live_workout/ui/components/LiveExerciseCard.kt`; its test's `PERMITTED_READER` constant
  likewise, and the feature-test fixture path `src/test` → `src/commonTest`.

`createLiveWorkoutGraph(` then occurs exactly twice in commonMain: the factory's declaration and the
one call inside the retained creation lambda.

## C6. In scope and non-goals

In scope: Sections C3–C5 and C8–C10, the tests of Section C9, and the documents of Section C7.

Not in scope:

- any behaviour change beyond Section C0.6, and any change to the copy model, the filler, the State
  lambdas or the click paths beyond CM2–CM4;
- the locale mechanism (CM16) and the dead delete-session surface (CM17);
- the iOS host's wiring of the screen (no iOS app composes it yet);
- a portable `core:data:database-test` fixture (ExerciseDoneRuleTest stays on the Android host);
- `feature/settings` and `feature/app-dialogs`, the two remaining Context readers;
- recording any golden, or adding a suppression;
- `graphify-out/`, the older feature specs and the research notes, which keep the paths they cite.

## C7. Exact change boundary — 157 module paths become 154, and 17 others

### C7.1 The module, `feature/live-workout/`

| Base path | Exit path |
| --- | --- |
| `build.gradle.kts` | the same, Section C8 |
| `src/main/kotlin/<pkg>/…` | `src/commonMain/kotlin/<pkg>/…` |
| `src/main/res/values/strings.xml`, `src/main/res/values-ru/strings.xml` | `src/commonMain/composeResources/values/strings.xml`, `…/values-ru/strings.xml` |
| `src/test/kotlin/<pkg>/golden/…`, `…/gate/…`, `…/mvi/mapper/ExerciseDoneRuleTest.kt` | `src/androidHostTest/kotlin/<pkg>/…` |
| `src/test/snapshots/images/*.png` | `src/androidHostTest/snapshots/images/*.png` |
| every other `src/test/kotlin/<pkg>/…` file | `src/commonTest/kotlin/<pkg>/…` |
| `src/androidTest/kotlin/<pkg>/LiveWorkoutScreenTest.kt` | `src/androidDeviceTest/kotlin/<pkg>/LiveWorkoutScreenTest.kt` |

`<pkg>` is `io/github/stslex/workeeper/feature/live_workout`. Every move keeps the file's path below
`<pkg>` and its name.

- **118 move byte-identical:** 48 production files (Section C2.1), the 60 PNGs, the seven
  `androidHostTest` Kotlin files, the device class, and Part B's two shared test files.
- **34 move with content changes:** the 14 production files of Section C2.1, the two catalogs (P1's
  twelve lines only) and the 18 suites (Section C9.1).
- **4 are deleted:** `src/main/kotlin/<pkg>/mvi/handler/LiveWorkoutCopyReader.kt`,
  `src/test/kotlin/<pkg>/mvi/handler/CommonHandlerComposeResourceTest.kt`,
  `src/test/kotlin/<pkg>/mvi/mapper/LiveWorkoutTemplateParityTest.kt` and
  `src/test/kotlin/<pkg>/ui/components/LiveSetRowSemanticsTest.kt`.
- **1 is new:** `src/iosTest/kotlin/<pkg>/LiveWorkoutFeatureSceneIosTest.kt` (Section C9.5).

That is 1 + 118 + 34 + 4 = 157 base paths and 1 + 118 + 34 + 1 = 154 exit paths: 62 production
files and two catalogs in `commonMain`, 20 files in `commonTest`, 67 in `androidHostTest` (7 Kotlin,
60 PNGs), one each in `androidDeviceTest` and `iosTest`. No `src/main`, `src/test` or
`src/androidTest` directory remains, and nothing else is added beside the build file.

### C7.2 Outside the module — 17 paths

    .github/scripts/assert_kmp_ui_source_topology.py                                         (commits 1, 2, 3)
    .github/scripts/assert_kmp_ios_smoke.py                                                  (commit 3)
    .github/workflows/android_build_unified.yml                                              (commit 3)
    app/common/build.gradle.kts                                                              (commit 2)
    app/common/src/main/kotlin/io/github/stslex/workeeper/App.kt                             (commit 2)
    app/common/src/main/kotlin/io/github/stslex/workeeper/app/common/di/AppRootDeps.kt       (commit 2)
    app/common/src/main/kotlin/io/github/stslex/workeeper/host/AppNavigationHost.kt          (commit 2)
    app/app/src/main/java/io/github/stslex/workeeper/di/AppGraph.kt                          (commit 2)
    app/app/src/test/kotlin/io/github/stslex/workeeper/di/LiveWorkoutExtensionIdentityTest.kt   (commit 2)
    app/app/src/androidTest/kotlin/io/github/stslex/workeeper/app/NavigationResultTest.kt    (commit 2, KDoc only)
    lint-rules/src/main/kotlin/io/github/stslex/workeeper/lint_rules/ActiveSurfaceSingleReaderRule.kt         (commit 2)
    lint-rules/src/test/kotlin/io/github/stslex/workeeper/lint_rules/ActiveSurfaceSingleReaderRuleTest.kt     (commit 2)
    documentation/architecture.md                                                            (commit 4)
    documentation/ci-cd.md                                                                   (commit 4)
    documentation/tech-debt.md                                                               (commit 4)
    documentation/testing.md                                                                 (commit 4)
    documentation/feature-specs/kmp-phase-7-16-live-workout-feature.md                       (commit 4: Status and C17 only)

The app, lint-rules and CI changes are Sections C5 and C10. `gradle/libs.versions.toml`,
`settings.gradle.kts`, every other build file, `core/`, every other feature and every other
document stay as they are.

### C7.3 Per commit

| Commit | Paths |
| --- | --- |
| 1 | the topology script: Section C10.0's 71 insertions and 1 deletion |
| 2 | the 157 module paths and the new scene (as 156 deletions under `src/`, 153 additions and the build file in `git diff --no-renames`), the 7 app paths, the 2 lint-rules paths, and the topology script's commit-2 delta (Section C10.1, 169 insertions and 1 deletion) |
| 3 | the topology script's entry (833 insertions), the oracle (327 insertions), the workflow (10 insertions, 6 deletions) |
| 4 | the five documents |

### C7.4 What each document gains (commit 4)

- **testing.md.**
  - The `StringResolver` consumer list names live-workout's `CommonHandler`, `ClickHandler`,
    `DialogClickHandler` and `ExercisePickerHandler`.
  - A new section, `#### Shared live-workout feature`, before `#### Shared string resolver`, in
    the shared features' form: the 18 suites and their 226 identities by suite; D8's identity; the
    port's fakes (CM6, CM9, CM11); the dependency facts (Section C10.1's dependency contract); the
    scene and its 42 actions (Section C9.5); what it does not cover (Section C9.6), linking
    tech-debt.md's new section; and the goldens, the device class, the seven extension identities
    and the five app journeys.
  - The `NO_PLAN_LABEL` bullet reads live-workout's internal Compose `Res`.
  - The golden module lists name `:feature:live-workout` among the KMP modules.
  - The iOS commands add `:feature:live-workout:iosSimulatorArm64Test` and
    `:feature:live-workout:linkDebugTestIosArm64`, and say 16 modules.
- **ci-cd.md.** The KMP iOS kit smoke job's table row, the repo-wide spelling list, the job's
  description (live-workout's 226 portable cases plus the scene: its catalog, the load's copy and
  the launch reads through the real `StringResolver`, the production mapper, the screen with its
  five sheets and five dialogs, the set rows' field labels, and dispatch), the verdict list and
  the validator paragraph (226 portable tuples and the scene's, 227 in all) name live-workout;
  "All fifteen result directories" becomes "All sixteen …", "The same fifteen modules'" "The same
  sixteen modules'", and `N/15 linked` `N/16 linked`.
- **architecture.md.**
  - The shape-B factory-resolution paragraph adds live-workout to its lists
    (`LiveWorkoutGraph.Factory`, `liveWorkoutGraph`, `LiveWorkoutFeature`), and "Exercise
    registers through `navComponentScreenWithResults`" becomes "Exercise and live-workout register
    through …".
  - The Context-seam paragraph adds live-workout and `AppGraph.liveWorkoutGraphFactory`, and
    "Exactly 3 feature/dialog Context readers remain." becomes "Exactly 2 …".
  - The results-overload example reads `navComponentScreenWithResults(LiveWorkoutFeature(factory))`.
  - Localization: the `R`-string bullet loses the live-workout exception ("outside it."), and the
    paragraph Part B added keeps the copy model, the load-time resolution and kit's filler, and says
    the verbatim read goes through `StringResolver`'s no-argument path, which returns the stored
    template unfilled; the temporary reader becomes history ("Until the feature moved (Part C), …").
- **tech-debt.md.**
  - "Shared (KMP) string resources": the first row reads "These four production files call CMP's
    top-level …" and ends "live-workout's `CommonHandler` left the row in Phase 7.16 PR-P; it reads
    kit's reps unit through `StringResolver`." (XC2); a new 🟢 row on `CommonHandler`'s
    `resolveCopy`, the locale property (CM16, XC1); the RU-coverage row adds
    `SessionStateGoldenTest.exerciseBodyweightRu`; the `MetroStoreProcessor` row says "eleven
    shared features'" and names live-workout.
  - Every live-workout link to `src/main` points at `src/commonMain`, and the
    `feature/live-workout/src/androidTest/` row at `src/androidDeviceTest/`.
  - A new section before "Dead restart surface in `feature/settings`":
    `` ## Dead delete-session surface in `feature/live-workout` — `Action.Click.OnDeleteSessionMenuClick` has no emitter (Phase 7.16 Part C, 2026-10-26) ``,
    with one 🟢 row (Section C16), and a `---` separator after it.
- **This document.** The Status line and Section C17 only (Section C14).

## C8. The exit build file (normative)

~~~kotlin
plugins {
    alias(libs.plugins.convention.kmpComposeLibrary)
    // Largest feature. Route-arg feature (shape B), single @DefaultDispatcher.
    alias(libs.plugins.metro)
    // Goldens for the session's set and exercise states; harness from core:ui:golden-harness.
    alias(libs.plugins.paparazzi)
}

compose.resources {
    packageOfResClass = "io.github.stslex.workeeper.feature.live_workout.resources"
}

metro {
    interop {
        includeJavax()
    }
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            // No public signature names its types; the graph's AppScope and DefaultDispatcher
            // annotations reach the app through the app's own core:core edge.
            implementation(project(":core:core"))

            // No public signature names a kit type either: StringResolver arrives only through
            // internal constructors.
            implementation(project(":core:ui:kit"))
            // `api`: LiveWorkoutStore, its handler store and Store impl and the public picker
            // handler extend the MVI contracts; liveWorkoutGraph extends NavGraphScope and the
            // graph's Factory takes a Screen.LiveWorkout; the State, the UI models, the picker seam
            // and PendingUndo carry the plan editor's models.
            api(project(":core:ui:mvi"))
            api(project(":core:ui:navigation"))
            api(project(":core:ui:plan-editor"))
            // Production, not a fixture: the domain mapper maps its data models, which no public
            // declaration names (plan-editor's precedent).
            implementation(project(":core:data:database"))
            // `api`: the graph exposes the inherited SessionRepository and ExternalSessionWrites.
            api(project(":core:data:exercise"))

            // `api`: liveWorkoutGraph takes a Modifier and the haptic events carry a
            // HapticFeedbackType; the interactor returns Flows and the graph exposes a
            // CoroutineDispatcher; State and the UI models carry ImmutableList, ImmutableSet and
            // ImmutableMap.
            api(libs.cmp.ui)
            api(libs.coroutines.core)
            api(libs.kotlinx.collections.immutable)
            // The card's animateContentSize and animateColorAsState, and their animation specs.
            implementation(libs.cmp.animation)
        }

        commonTest.dependencies {
            implementation(kotlin("test"))
            implementation(libs.coroutine.test)
        }

        iosTest.dependencies {
            implementation(kotlin("test"))
            implementation(libs.cmp.ui.test)
        }
    }
}

dependencies {
    // ExerciseDoneRuleTest's WorkoutTargetParityFixture (Phase 7.13 D4-A): host only.
    "androidHostTestImplementation"(project(":core:data:database-test"))
    "androidHostTestImplementation"(project(":core:ui:golden-harness"))

    "androidDeviceTestImplementation"(libs.bundles.android.test)
    "androidDeviceTestImplementation"(libs.androidx.compose.ui.test.junit4)
    // GUARD: ui-test-manifest is versionless in the catalog; classic modules resolve it through
    // the convention's compose BOM, which the KMP convention does not add — so add it here.
    "androidDeviceTestImplementation"(platform(libs.androidx.compose.bom))
    "androidDeviceTestImplementation"(libs.androidx.compose.ui.test.manifest)
    // GUARD: carries the @Smoke / @Regression annotations — without this edge androidx.test
    // silently drops ui_tests.yml's filter. Enforced by `verifyInstrumentedSuiteClasspath`.
    "androidDeviceTestImplementation"(project(":core:ui:test-utils"))
}

apply(from = "$rootDir/gradle/golden-gate.gradle.kts")
~~~

Why each visibility (an edge is `api` when a public declaration of this module names one of its
types; annotations do not count, as in every earlier shared feature):

- `core:core`: `AppScopeLifetime` is in the Store's internal constructor, `formatElapsedDuration`
  in internal code, and `AppScope` / `@DefaultDispatcher` are annotations on the graph; app:app and
  app:common declare core:core themselves.
- `core:ui:kit`: kit composables are called inside bodies, and `StringResolver` arrives through
  internal constructors (`ExercisePickerHandler`'s is internal).
- `core:ui:mvi`, `core:ui:navigation`, `core:ui:plan-editor`, `core:data:exercise`, `cmp.ui`,
  `coroutines.core`, `kotlinx.collections.immutable`: the comments above.
- `core:data:database`: `LiveWorkoutDomainMapper` alone reads it, for `PlanSetDataModel` and
  `SetTypeDataModel`.
- `cmp.animation`: `LiveExerciseCard`'s `animateContentSize` and `animateColorAsState`.
- `core:data:database-test`: `ExerciseDoneRuleTest` alone (Section C10.1's dependency contract);
  exercise-chart's build file is the precedent for the host-only edge.

`kotlin("test")` appears exactly twice. Removed relative to the base: `convention.composeLibrary`,
every `testImplementation`, `androidTestImplementation` and `debugImplementation` line, and
`androidx.compose.ui.test.junit4` on the host, which only `LiveSetRowSemanticsTest` used (D8). No
comment may hold a dependency fragment or a forbidden build token (`mockk`, `robolectric`,
`androidMain`, `iosMain`, `paging.testing`, `testImplementation(`, …).

Verify Gradle metadata rather than copying it blindly (7.8 Section 10). A dependency the measured
source does not need is removed; one it needs that this file lacks is a STOP-and-amend, never a
silent addition.

## C9. Tests and the Native scene

### C9.1 The port — 18 suites into commonTest

Every suite of Section C2.2 moves under its path and keeps its class name, every identity's name and
every assertion; Part B's two shared test files move byte-identical. Around the identities the
port changes only this:

- **APIs.** JUnit 5 → `kotlin.test` (`@Test`, `assertEquals`, `assertTrue`, `assertFalse`,
  `assertFailsWith`, …); `kotlinx-coroutines-test` (`runTest`, `StandardTestDispatcher`,
  `runCurrent`, `advanceUntilIdle`) stays; MockK and Robolectric leave; no commonTest file names
  `java.`, `Thread.`, `org.junit`, `io.mockk`, `org.robolectric`, `@Config` or `runBlocking`
  (Section C12, X7).
- **Fakes.** Each suite's fakes are private classes nested in it (Section C0.2). A member no
  identity reaches fails fast with `Nothing` (CM11). The relaxed MockK store of the four click-path
  suites becomes a relaxed fake (CM6); every strict mock becomes a strict fake.

| Suite | Fakes |
| --- | --- |
| `LiveWorkoutInteractorImplTest` (29) | `LiveWorkoutInteractorImpl` on recording fakes of its seven repositories (`RecordingSessionRepository`, `RecordingPerformedExerciseRepository`, `RecordingSetRepository`, `RecordingExerciseRepository`, `RecordingTrainingRepository`, `RecordingTrainingExerciseRepository`, `RecordingPersonalRecordRepository`) and the real `ExternalSessionWrites`. Each answers what the identity seeds, or what the relaxed mock answered where an identity depends on it, and fails fast through the file's `unreached` / `unanswered` on any other member. CM8's three explicit statements. The fakes spell `PlanSetDataModel` and `SetTypeDataModel`, the suite's only `core:data:database` names |
| `ClickHandlerTest` (26), `DialogClickHandlerTest` (29) | `RelaxedHandlerStore` (`state` is the identity's flow and `updateState` applies to it; `launch` is counted and runs nothing; every other member does what the relaxed mock did), `FakeLiveWorkoutHandlerStore` (captures the latest launch for the identity to run, a throw routed to `onError`), `RecordingInteractor` (every call in order as `Call(member, args)`, so a `coVerify` becomes a count over the log; a set-up failure throws from `onCall`), `RelaxedStringResolver` (answers what the identity sets up and `""` otherwise; fails fast on a plural), `SilentLogger`, and Part B's `FakeExercisePicker` |
| `ExercisePickerHandlerTest` (12) | `RelaxedHandlerStore`, `ExecutingLiveWorkoutHandlerStore` (runs every launch on the identity's `runTest` scope, a throw to `onError`), `RecordingInteractor`, `RelaxedStringResolver`, `SilentLogger` |
| `LiveSetDraftBehaviorTest` (10) | `RelaxedHandlerStore`, `RecordingInteractor`, `RelaxedStringResolver`, `SilentLogger`, `FakeExercisePicker` |
| `CommonHandlerTest` (3 → 4) | `FakeCommonHandlerStore` (launches on the identity's scheduler inside `supervisorScope`, then `onSuccess`, a throw to `onError`; `CommonHandler` writes only through `updateState`, so every other member fails fast), `LoadingInteractor` (answers `load` and an empty write flow), `CatalogStringResolver` (CM7, Section C9.2) |
| `CommonHandlerExternalWritesTest` (24) | CM9: a real `BaseStore` with no Robolectric and no MockK; an in-file interactor answering the queued loads and the identity's write flow; `BlankStringResolver` (`""`; the identities pin structure, not copy); `Log.isLogging` off for each identity and restored after it; `ManualDispatcher`, whose queue and the immediate-write log are `ConcurrentDeque`, a lock-free deque on `kotlin.concurrent.atomics.AtomicReference`; a 2 ms `kotlin.time.Clock` busy-wait where the timer identity slept; `FakeLifecycleOwner` nested in the class |
| `InputHandlerTest` (1) | `FakeInputHandlerStore`, strict |
| `NavigationHandlerTest` (4) | `RecordingNavigator` |
| `DeleteExerciseCopyMapperTest` (4) | none; `R.string` → `Res.string` |
| the other seven | none: `kotlin.test` alone |

The six test `@Suppress` annotations of Section C2.7 leave with the casts and the long lines the
fakes remove. A compile error in a moved identity is fixed in its fake, never in its assertions.

### C9.2 D8's identity

`CommonHandlerTest` gains, after `Init launches a load coroutine`:

~~~kotlin
    /** D8: the copy resolves at the load, each field from its own key, and nothing else is read. */
    @Test
    fun `a load resolves every copy field from its own catalog key`() = runTest {
        interactor.load = { snapshot() }
        val resolver = CatalogStringResolver()
        val (stateFlow, handler) = setup(
            State.create(sessionUuid = "session-1", trainingUuid = "training-1"),
            stringResolver = resolver,
        )

        handler.invoke(Action.Common.Init)
        runCurrent()

        assertFalse(stateFlow.value.loadFailed)
        assertEquals(NAMED_COPY, stateFlow.value.liveCopy)
        assertEquals(CATALOG.size, resolver.reads.size, "each key is read once: ${resolver.reads}")
        assertEquals(CATALOG.keys, resolver.reads.toSet())
    }
~~~

with `snapshot()` an in-progress session of training `training-1` named `Push Day` with no
exercises, and:

~~~kotlin
    /**
     * Answers exactly the copy's 31 keys, each with [NAMED_COPY]'s value for the field that key
     * belongs to, verbatim, and records every read; any other read, or a read with arguments, throws.
     */
    private class CatalogStringResolver : StringResolver {

        val reads = mutableListOf<StringResource>()

        override suspend fun getString(resource: StringResource, vararg formatArgs: Any): String {
            check(formatArgs.isEmpty()) { "$resource is read verbatim; got ${formatArgs.toList()}" }
            reads += resource
            return CATALOG[resource] ?: error("$resource is not part of the copy")
        }

        override suspend fun getPluralString(
            resource: PluralStringResource,
            quantity: Int,
            vararg formatArgs: Any,
        ): Nothing = error("the copy reads no plural; got $resource")
    }
~~~

`CATALOG` is a `Map<StringResource, String>` in the class's companion: the 31 keys of Section
C4.1, each to its field's `NAMED_COPY` value (kit's `Res.string.core_ui_kit_plan_editor_unit_reps`
to `repsUnit`). The three existing identities take `CatalogStringResolver()` by default and none
reaches the copy: `resolveCopy` runs only on a found session, and their loads throw, find no session
or never run.

### C9.3 The Android host — androidHostTest

- The four golden classes, `gate/LiveSetRowOverflowGateTest`, `gate/SetColumnAlignmentGateTest` and
  `mvi/mapper/ExerciseDoneRuleTest` move byte-identical (Section C2.3), and the 60 PNGs with them.
- `:feature:live-workout:testAndroidHostTest` runs 21 suites and 235 cases: the 18 portable suites
  (226), `ExerciseDoneRuleTest` (7) and the two gates (2). The golden gate's plain-run filter
  excludes `*.golden.*` only (7.14's D4).
- `:feature:live-workout:verifyPaparazziDebug` runs 25 suites and 295 cases and prints
  `Visual gate live: 60 golden test case(s) executed for 60 golden image(s).`. The goldens now
  render the catalogs through CMP's `stringResource` and `pluralStringResource`; the blob set stays
  `53221eab…` (OC2). Any changed PNG is a STOP (Section C13): record the method, the theme and the
  differing region; never re-record.

### C9.4 The device and the app

- `LiveWorkoutScreenTest` moves byte-identical to `androidDeviceTest`: its 6 `@Smoke` identities
  run through `:feature:live-workout:connectedDebugAndroidTest` (the KMP convention's alias onto
  `connectedAndroidDeviceTest`), and the device placeholder contract pins its seven fragments.
- `LiveWorkoutExtensionIdentityTest`'s 7 identities reach the graph through
  `AppGraph.liveWorkoutGraphFactory` (Section C5); names and bodies unchanged.
- `NavigationResultTest` changes its KDoc only (CM14).
- The factory flow and the result read-back are proven on a device by the app journeys:
  `RouteReachabilityTest`, `NavigationResultTest`, `StoreRetentionTest` and
  `BackStackStateRestorationTest`, 25 cases (15 + 3 + 3 + 4), among them the five of Section C2.6.

### C9.5 The Native scene

`src/iosTest/kotlin/<pkg>/LiveWorkoutFeatureSceneIosTest.kt`: class
`LiveWorkoutFeatureSceneIosTest`, one identity,
`resourcesScreenSheetsDialogsAndActionsRenderAndDispatch`, in `runComposeUiTest`. Every read outside
composition goes through one real `ComposeStringResolver` (kit's); the file declares no fake
resolver and names no `ResourceWrapper`. Kit's `Res` keeps its name as `KitRes`, the feature's is
`LiveWorkoutRes` (CM1). `runBlocking` wraps each suspend read, as in every earlier scene.

| Step | What it proves |
| --- | --- |
| 1 | The private EN catalog in file order, each entry read with no format argument: its 94 strings and each plural's `one` and `other` as stored (98 values, equal to the EN column of Section C10.1's catalog). Then both plurals filled with their count as the one argument: `1 new personal record`, `2 new personal records`, `1 empty set will not be saved`, `2 empty sets will not be saved` |
| 2 | `CommonHandler.resolveCopy`'s 31 reads, repeated in the scene in its order, give a `LiveWorkoutCopy` whose every field is its EN value verbatim (`repsUnit = "reps"`, kit's) |
| 3 | The launch reads with production's arguments: `“Bench press” added`, `Create “Curl”`, `No exercises match “Curl”`, `Session saved`; and the 13 `ErrorType` messages through `messageRes()`, in declaration order |
| 4 | The production mapper on Native: a snapshot of `Push Day` (Bench press weighted with a two-set plan and its first set logged, Pull-up weightless with a two-set plan, Plank skipped and one-off) at 12:34 gives `Push Day`, `12:34`, `0 of 2 exercises · 1 of 4 sets · 1 skipped`, statuses CURRENT / PENDING / SKIPPED and labels `100×5 · 100×5`, `8 reps · 8 reps`, `skipped`; `toFinishStats` gives the exact `DialogState.FinishSession` (`0 of 3 done · 1 skipped`, one record `100 × 5`, one unfilled set); kit's two field labels read `weight, kilograms` and `repetitions` |
| 5 | One content slot, from test-held state, switches between the production `LiveWorkoutScreen` and two `LiveSetRow`s, under `AppTheme`. The loaded screen: the top bar's descriptions, the header, the rail meta `3 EXERCISES · 4 SETS`, the three cards, their sub-labels, the `ONE-OFF` badge, the set rows' fields by their unit labels, the record tag, the type chip, `+ SET` / `− SET`, the add-exercise CTA and `Finish session`; then the screen's 16 dispatching gestures, text entered through the `SetText` semantics action (7.11's S5), the test-held State taking each typed value as the Store would |
| 6 | The empty unnamed session: `Untitled`, no meta line, the empty state and its add action |
| 7 | All five sheets through the screen's own routing: the session menu; the exercise menu planned (no one-off row) and skipped one-off; the delete sheet in its three copies (planned, added this session, ad hoc); the description sheet; and plan-editor's shared picker, under an empty query (one library row, the search hint, no create CTA) and under `Curl` (the no-match headline and the create CTA, then a typed `Curls`) |
| 8 | All five dialogs through the screen's own routing: kit's shared delete dialog (`Delete Push Day?`, its progress line filled from the copy as the click path fills it, `0 of 2 exercises done · 1 sets logged`); the finish dialog named, then unnamed (the name field, its error, the disabled confirm, a typed `Leg Day`); reset sets; cancel session; the empty-finish dialog |
| 9 | The retired `LiveSetRowSemanticsTest`'s two rows: each field announces its unit |
| 10 | Dispatch: production's 42 actions, as one exact ordered list (below) |

    Action.Click.OnBackClick
    Action.Click.OnSessionMenuClick
    Action.Click.OnTrainingNameTap
    Action.Click.OnExerciseMenuClick(PE_BENCH)
    Action.Click.OnShowDescription(PE_BENCH)
    Action.Click.OnExerciseHeaderClick(PE_BENCH)
    Action.Click.OnExerciseHeaderClick(PE_PULL)
    Action.Click.OnSetUncheck(PE_BENCH, 0)
    Action.Click.OnSetMarkDone(PE_BENCH, 1)
    Action.Click.OnSetTypeSelect(PE_BENCH, 1, SetTypeUiModel.WORK)
    Action.Input.OnSetWeightChange(PE_BENCH, 1, 102.5)
    Action.Input.OnSetRepsChange(PE_BENCH, 1, 6)
    Action.Click.OnAddSet(PE_BENCH)
    Action.Click.OnRemoveLastSet(PE_BENCH)
    Action.Click.OnAddExerciseClick
    Action.Click.OnFinishClick
    Action.Click.OnAddExerciseClick
    Action.Click.OnAddExerciseClick
    Action.Click.OnCancelSessionClick
    Action.Click.OnEditPlan(PE_BENCH)
    Action.Click.OnResetSets(PE_BENCH)
    Action.Click.OnSkipExercise(PE_BENCH, skipped = true)
    Action.Click.OnDeleteExerciseClick(PE_BENCH)
    Action.Click.OnToggleOneOff(PE_PLANK)
    Action.Click.OnSkipExercise(PE_PLANK, skipped = false)
    Action.DialogClick.OnDeleteExerciseKeep
    Action.DialogClick.OnDeleteExerciseConfirm(PE_BENCH)
    Action.Click.OnSheetDismiss
    Action.DialogClick.PickerAction(ExercisePickerAction.OnExerciseSelect("ex-9"))
    Action.DialogClick.PickerAction(ExercisePickerAction.OnCreateNewExercise("Curl"))
    Action.DialogClick.PickerAction(ExercisePickerAction.OnQueryChange("Curls"))
    Action.DialogClick.OnDeleteSessionDismiss
    Action.DialogClick.OnDeleteSessionConfirm
    Action.DialogClick.OnFinishDismiss
    Action.DialogClick.OnFinishConfirm
    Action.DialogClick.OnFinishNameChange("Leg Day")
    Action.DialogClick.OnResetSetsConfirm(PE_BENCH)
    Action.DialogClick.OnResetSetsDismiss
    Action.DialogClick.OnCancelSessionConfirm
    Action.DialogClick.OnCancelSessionDismiss
    Action.DialogClick.OnEmptyFinishDiscard
    Action.DialogClick.OnEmptyFinishContinue

`PE_BENCH`, `PE_PULL` and `PE_PLANK` are `pe-1`, `pe-2` and `pe-3`. Settling is bounded: ten steps
of one virtual second each, with `mainClock.autoAdvance` off inside a step. Together with the 226
portable cases, the live-workout Native XML holds exactly 227 passing cases with no skip, failure or
error (19 suites).

### C9.6 What this does not cover

- RU catalog values render only in `SessionStateGoldenTest.exerciseBodyweightRu`; the gate pins the
  RU catalog statically.
- The undo toast's five-second window has no scene path (CM15); the toast itself is
  `SessionSheetsGoldenTest.undoToast`.
- The graph's wiring — the plan editor's result read-back, haptics, snackbars, `BackHandler`, the
  load-failure exit and `AppLoadedContent` — has no scene path; the app journeys of Section C9.4
  cover it on a device.
- No UI dispatches `OnDeleteSessionMenuClick`, so the delete dialog renders from test-held state
  only (Section C16).
- The iosArm64 test binaries are linked, never run.

### C9.7 Counts

| Source set | Names |
| --- | ---: |
| commonTest | 226 (18 suites) |
| iosTest | 1 |
| androidHostTest | 39: `ExerciseDoneRuleTest` 7, the two gates 2, the golden methods 30 |
| androidDeviceTest | 6 |

272 names, from 277 at the base (Section C2.2): −7 retired, +1 D8, +1 scene.

## C10. CI ownership and positive verification

### C10.0 D7 — `state_mutators` (commit 1)

Commit 1 changes only `.github/scripts/assert_kmp_ui_source_topology.py`, in four places: the
field, two patterns and a contract function, the delegation condition of the State-lambda walk, and
the contract's call. No entry, contract, `MODULES` or `FEATURE_ENTRIES` line changes.

Reference implementation, normative for the field and its default, both patterns, every check and
message of the contract, the condition and the call site; formatting may differ:

~~~python
# FeatureEntry, after foreign_resource_prefixes:
    # D7 (kmp-phase-7-16-live-workout-feature.md Section C10.0): the pure same-module classes a
    # State lambda may return through in place of a `.copy(`. Empty by default, so every earlier
    # entry stays byte-identical.
    state_mutators: tuple = ()

# After KOTLIN_TEST_NAME:
# D7 (kmp-phase-7-16-live-workout-feature.md Section C10.0): a `val`/`var` and its declared type.
STATE_MUTATOR_PROPERTY = re.compile(r"\b(?:val|var)\s+(\w+)\s*:\s*(\w+)\b")
# A top-level class or object; a nested one belongs to its file's mutator.
STATE_MUTATOR_DECLARATION = re.compile(
    r"^(?:(?:internal|private|public|data|open)\s+)*(?:class|object)\s+(\w+)", re.MULTILINE
)


def state_mutator_contract(entry: FeatureEntry, common_sources: dict[Path, str]) -> list[str]:
    """D7: each listed mutator is declared once in commonMain, its file is pure (no State lambda
    side effect, no resolver or wrapper), and every mapper class it names is listed too."""
    failures: list[str] = []
    if not entry.state_mutators:
        return failures
    declared: dict[str, list[Path]] = {}
    for path, raw in common_sources.items():
        for name in STATE_MUTATOR_DECLARATION.findall(strip_kotlin_comments(raw)):
            declared.setdefault(name, []).append(path)
    mappers = {
        name
        for name, paths in declared.items()
        if any("/mapper/" in path.as_posix() for path in paths)
    }
    for mutator in entry.state_mutators:
        paths = declared.get(mutator, [])
        if len(paths) != 1:
            failures.append(
                f"{entry.module}: state mutator {mutator} must be declared once in commonMain; "
                f"found {len(paths)}"
            )
            continue
        relative = paths[0].relative_to(entry.root)
        source = strip_kotlin_comments(common_sources[paths[0]])
        for token in FEATURE_STATE_LAMBDA_SIDE_EFFECTS:
            if token in source:
                failures.append(
                    f"{entry.module}: state mutator {mutator} in {relative} holds {token!r}; "
                    "a State lambda returns only through pure code"
                )
        for name in sorted(set(FEATURE_COPY_HELPER_REFERENCES.findall(source))):
            failures.append(f"{entry.module}: state mutator {mutator} in {relative} names {name!r}")
        for type_name in ("ResourceWrapper", "StringResolver"):
            if type_name in source:
                failures.append(
                    f"{entry.module}: state mutator {mutator} in {relative} takes {type_name}"
                )
        for name in sorted(mappers - set(entry.state_mutators)):
            if re.search(rf"\b{re.escape(name)}\b", source):
                failures.append(
                    f"{entry.module}: state mutator {mutator} in {relative} reaches {name}, "
                    "which state_mutators does not list"
                )
    return failures

# check_feature_entry, the State-lambda walk, after `helpers = copy_resolving_functions(…)`:
        # D7: a property of this file whose declared type is a listed mutator, reached with `.`
        # or `::`, stands in for the `.copy(`.
        mutators = {
            name
            for name, declared in STATE_MUTATOR_PROPERTY.findall(strip_kotlin_comments(source))
            if declared in entry.state_mutators
        }
# … and the copy check of each lambda body:
                delegates = any(
                    re.search(rf"(?<![\w.]){re.escape(name)}(?:\.|::)", compact(body))
                    for name in mutators
                )
                if ".copy(" not in compact(body) and not delegates:

# check_feature_entry, directly after the walk's `no updateState lambdas were inspected` check:
    failures.extend(state_mutator_contract(entry, common_sources))
~~~

The diff is 71 insertions and 1 deletion, every added line within 100 columns. With the default
`()`, `mutators` is empty, so `delegates` is `False` and the condition reduces to today's; the
contract returns at once. Every message stays byte-identical. D7 changes only which lambdas pass:
the side-effect, resolver and same-file checks still read every lambda body as before.

**Proof that commit 1 changes no existing behaviour.** 7.15 Section B12.0's procedure: on the
committed tree, with the base's script saved aside as `old_gate.py` (sha256
`679d6e9f1a6bdd2c2e82835acc1818595884984497f6f525a109f70fbe506746` at the measured tree), under
every Python the host has:

1. GREEN: old and new outputs on the unmodified tree are byte-identical, exit code and stderr
   included, and equal to BASE_GATE_OUTPUT (833 lines; sha256
   `37315e70b6606acdc9cffaa68b5c82bba7cbbf1006f6a2f1e28f7071e3eca235` at the measured tree).
2. Differential, one exact occurrence per mutation, scored by both scripts and restored byte-exactly
   (sha256 and mode) before the next, with a fresh GREEN after the last. **M** rows: old and new are
   byte-identical and RED with exactly the named line. **Q** rows: both GREEN.

   `EX` is `feature/exercise/src/commonMain/kotlin/io/github/stslex/workeeper/feature/exercise/ui/mvi/handler/ClickHandler.kt`;
   `ST` is `feature/single-training/src/commonMain/kotlin/io/github/stslex/workeeper/feature/single_training/mvi/handler/ClickHandler.kt`;
   `EXM` is `feature/exercise/src/commonMain/kotlin/io/github/stslex/workeeper/feature/exercise/ui/mvi/mapper/ExerciseUiMapper.kt`.

   | # | Mutation | Result |
   | --- | --- | --- |
   | M1 | `EX`: `updateState { it.copy(type = target) }` → `updateState { interactor.run { it } }` | both RED, identical: `feature:exercise: updateState in <EX> must return a State copy` |
   | M2 | `ST`: `updateState { it.copy(dialogState = DialogState.DetailMenu) }` → `updateState { it.also(interactor::equals) }` | both RED, identical: `feature:single-training: updateState in <ST> must return a State copy` |
   | Q1 | `EXM`: append the line `internal object StateStatusMapper` | both GREEN |

   M1 and M2 pin that an entry with the default lets neither a `.` nor a `::` reference stand in
   for the `.copy(`; Q1 that the contract walks nothing for such an entry, even when a class with a
   listed name exists in it.
3. The diff of commit 1 touches only the topology script, and in it only the four places above.

A differing byte in step 1 or an M row, or a Q row that is not GREEN, is a STOP. The prototype ran
this proof under Python 3.9, 3.10, 3.11, 3.12, 3.13 and 3.14, and every row gave exactly its
result; that run is not evidence.

### C10.1 The topology gate data (normative)

**Commit 2** carries the minimal delta that keeps its tree green (7.8's G1 precedent):

- `MODULES["feature:live-workout"]` after exercise's entry and in its format: `root`
  `Path("feature/live-workout")`; `files`, the 153 exit paths under `src/` of Section C7.1, sorted;
  `kotlin_source_sets` `commonMain`, `commonTest`, `androidHostTest`, `androidDeviceTest`,
  `iosTest`; `resource_dirs` `src/commonMain/composeResources/values` and
  `src/commonMain/composeResources/values-ru`;
- `EXPECTED_APP_DEPS_READERS` loses live-workout's line (3 → 2).

live-workout has no `FEATURE_RESOURCE_OWNERS` row, so nothing else changes: 169 insertions and 1
deletion.

**Commit 3** appends `live_workout_copy_contract`, `live_workout_dependency_contract` and
`LIVE_WORKOUT_ENTRY` after `EXERCISE_ENTRY`, and the line `    LIVE_WORKOUT_ENTRY,` after
`    EXERCISE_ENTRY,` in `FEATURE_ENTRIES`. Nothing else in the script changes: commit 3's topology
diff is purely additive (833 insertions in the prototype).

| Field | Value |
| --- | --- |
| module / short_name | `feature:live-workout` / `live-workout` |
| root / package_dir | `feature/live-workout` / `io/github/stslex/workeeper/feature/live_workout` |
| build_fragments | every plugin, `packageOfResClass`, `includeJavax()`, dependency and `apply(from = …)` line of Section C8, each exactly once (26), except `implementation(kotlin("test"))`, which kotlin_test_count covers |
| kotlin_test_count | 2 |
| catalog | the 96 rows of the exit catalogs (Section C2.4), in file order: `(tag, name, EN, RU)`, decoded text, the typographic characters (`· ’ “ ” « » — − → ×`) as themselves (the catalogs hold no escape sequence); each plural as its items, EN `(one, other)` and RU `(one, few, many, other)`, with P1's `%1$d` |
| resource_prefix | `feature_live_workout_` |
| payload_files | CM10's twelve: `BottomSheetState.kt`, `DeleteExerciseCopyUiModel.kt`, `DialogState.kt`, `ErrorType.kt`, `ExerciseStatusUiModel.kt`, `ExternalWriteUiModel.kt`, `LiveExerciseUiModel.kt`, `LiveSessionStateUiModel.kt`, `LiveSetUiModel.kt`, `LiveWorkoutCopy.kt`, `LiveWorkoutStore.kt`, `PendingUndo.kt` |
| store_file / state_fields | `mvi/store/LiveWorkoutStore.kt` / the 30 fields as the gate's State reading prints them (a default or a generic's comma ends the type text), in order: `sessionUuid` `String?`, `trainingUuid` `String?`, `trainingName` `String`, `trainingNameLabel` `String`, `trainingNameDraft` `String`, `isTrainingNameEditing` `Boolean`, `isAdhoc` `Boolean`, `startedAt` `Long`, `nowMillis` `Long`, `elapsedDurationLabel` `String`, `doneCount` `Int`, `totalCount` `Int`, `setsLogged` `Int`, `progress` `Float`, `headerMetaLabel` `String`, `liveCopy` `LiveWorkoutCopy = LiveWorkoutCopy.EMPTY`, `exercises` `ImmutableList<LiveExerciseUiModel>`, `setDrafts` `ImmutableMap<DraftKey`, `rowCountOverrides` `ImmutableMap<String`, `midSessionAddedUuids` `ImmutableSet<String> = persistentSetOf()`, `pendingUndo` `PendingUndo? = null`, `activeExerciseUuids` `ImmutableSet<String>`, `expandedExerciseUuids` `ImmutableSet<String>`, `preSessionPrSnapshot` `ImmutableMap<String`, `isAddExerciseInFlight` `Boolean`, `isFinishInFlight` `Boolean`, `isLoading` `Boolean`, `loadFailed` `Boolean`, `dialogState` `DialogState`, `bottomSheetState` `BottomSheetState` |
| resource_wrapper_readers / resource_wrapper_dates | empty / `{}` (D5, D6: no `ResourceWrapper` reader is left) |
| preview_subjects / preview_inventory | `{}` / `ui/components/LiveSetRow.kt`: the six argument lists of Section C4.5, `name = "Pending Light", showBackground = true` through `name = "Weightless Dark", showBackground = true` |
| bare_previews | `ui/LiveWorkoutScreen.kt` 5, `ui/components/FinishConfirmDialog.kt` 3, `ui/components/LiveExerciseCard.kt` 5, `ui/components/LiveWorkoutHeader.kt` 3 |
| suppressions | the eleven production annotations of Section C2.7 under their nine `src/commonMain/…` paths, each file's arguments as strings in source order (`mvi/store/LiveWorkoutStore.kt`: `['"ComplexInterface"', '"ComplexInterface"', '"MviActionNamingRule"']`), and `src/androidHostTest/…/golden/SessionScreenGoldenTest.kt`: `['"LongMethod"']`; nothing under commonTest or iosTest. The gate's message counts files: "the nine inherited production" |
| test_names | the 226 names of Section C2.2 under their 18 commonTest paths, and the scene's under its iosTest path: 227, whose serialization is Section C2.2's hash |
| golden_test / golden_methods | `src/androidHostTest/…/golden/SessionStateGoldenTest.kt` / its 18 methods of Section C2.3, in source order |
| extra_golden_tests (7.14's D4) | `…/golden/SessionHeaderGoldenTest.kt`, `…/golden/SessionScreenGoldenTest.kt`, `…/golden/SessionSheetsGoldenTest.kt`, each with its methods of Section C2.3 |
| device_test / device_fragments | `src/androidDeviceTest/…/LiveWorkoutScreenTest.kt` / `@Smoke` and its six `fun <name>()` declarations of Section C2.3, each exactly once |
| factory names | Section C0.3's substitutions; `create_args = "screen"`; feature_file `di/LiveWorkoutFeature.kt`, graph_file `ui/LiveWorkoutGraph.kt`, store_impl `LiveWorkoutStoreImpl`, store_accessor `liveWorkoutStore` |
| screen_registration (7.15's G1) | `navComponentScreenWithResults` |
| identity_test / identity_fragments | `app/app/src/test/…/di/LiveWorkoutExtensionIdentityTest.kt` / the `liveWorkout(…)` helper of Section C5, on one line (the gate compares whitespace-compacted text) |
| identity_names / identity_accessor | `LiveWorkoutExtensionIdentityTest`'s seven names, in source order (unchanged) / below |
| state_mutators (D7) | CM12's six, in this order: `LiveSetMutator`, `StateStatusMapper`, `LiveWorkoutMapper`, `LiveSetRowsResolver`, `LiveWorkoutDomainMapper`, `ExerciseDoneRule` |
| extra_root_fragments / foreign_resource_prefixes | none / `()` |
| extra_checks | `live_workout_copy_contract`, `live_workout_dependency_contract`, below |

`identity_accessor` is the pattern `(?<!AppGraph)\.liveWorkout\(sessionUuid = "s-[19]"\)`. It
matches seven times, once per identity; the sixth identity's `"s-2"` call, the seventh's
training-arm call and the helper's declaration are deliberately not counted.

The two contracts, normative for every fragment, count, order check and message; formatting may
differ. Every count is over comment-stripped source:

~~~python
def live_workout_copy_contract(entry: FeatureEntry, common_sources: dict[Path, str]) -> list[str]:
    # D5, D6, SM0 (kmp-phase-7-16-live-workout-feature.md Sections B3.5–B3.9, C4.1 and C4.2): the
    # copy outside composition resolves at each load into State through the injected StringResolver,
    # each field from its own key; the seven launch reads resolve on use through the same resolver;
    # the Part B reader is gone. Every count is over comment-stripped source.
    failures: list[str] = []
    module = entry.module

    def source_of(relative: str):
        # None only for an absent file: an existing empty file is still checked, and fails.
        path = entry.common_main / relative
        if path not in common_sources:
            failures.append(f"{module}: copy contract cannot read {path.relative_to(entry.root)}")
            return None
        return strip_kotlin_comments(common_sources[path])

    def shown(relative: str) -> Path:
        return (entry.common_main / relative).relative_to(entry.root)

    def require(relative: str, source: str, fragment: str, expected: int) -> None:
        count = source.count(fragment)
        if count != expected:
            times = "once" if expected == 1 else f"{number_word(expected)} times"
            failures.append(
                f"{module}: {shown(relative)} copy contract requires {fragment!r} "
                f"exactly {times}; found {count}"
            )

    # 1. The copy model: 31 String fields in order, each blank in EMPTY.
    copy_fields = (
        "trainingNamePlaceholder",
        "statusSkipped",
        "statusNoPlan",
        "repsUnit",
        "metaTemplate",
        "metaSkippedTemplate",
        "resetTitle",
        "resetBody",
        "resetConfirm",
        "resetDismiss",
        "emptyFinishDiscard",
        "emptyFinishContinue",
        "cancelTitle",
        "cancelBody",
        "cancelConfirm",
        "cancelDismiss",
        "deleteSessionUnnamed",
        "deleteSessionProgressTemplate",
        "finishNameLabel",
        "finishNameRequired",
        "finishSetsCountTemplate",
        "finishExercisesTemplate",
        "finishExercisesWithSkippedTemplate",
        "finishPrWeightedTemplate",
        "finishPrWeightlessTemplate",
        "toastSetAdded",
        "toastSetRemoved",
        "toastExerciseRemovedTemplate",
        "toastExerciseRemovedFromWorkoutTemplate",
        "errorInvalidReps",
        "errorFinishFailed",
    )
    model_relative = "mvi/model/LiveWorkoutCopy.kt"
    model_source = source_of(model_relative)
    if model_source is not None:
        parameters = data_class_parameters(model_source, "LiveWorkoutCopy")
        expected_parameters = [f"{field_name}: String" for field_name in copy_fields]
        if parameters != expected_parameters:
            failures.append(
                f"{module}: {shown(model_relative)} LiveWorkoutCopy parameters must be exactly "
                f"{expected_parameters}; actual={parameters}"
            )
        require(model_relative, model_source, "val EMPTY: LiveWorkoutCopy = LiveWorkoutCopy(", 1)
        for field_name in copy_fields:
            require(model_relative, model_source, f'{field_name} = "",', 1)

    # 2. CommonHandler: the load resolves the copy, 31 reads through the resolver, no reader.
    common_relative = "mvi/handler/CommonHandler.kt"
    common_source = source_of(common_relative)
    if common_source is not None:
        for fragment in (
            "private val stringResolver: StringResolver,",
            "private suspend fun resolveCopy(): LiveWorkoutCopy = LiveWorkoutCopy(",
            "liveCopy = resolveCopy(),",
            "repsUnit = stringResolver.getString(Res.string.core_ui_kit_plan_editor_unit_reps),",
        ):
            require(common_relative, common_source, fragment, 1)
        require(common_relative, common_source, "stringResolver.getString(", 31)
        for fragment in ("copyReader", "LiveWorkoutCopyReader", "System.currentTimeMillis"):
            require(common_relative, common_source, fragment, 0)

    # 3. The launch bodies' seven reads: one, two and four, each through the resolver.
    for relative, reads in (
        ("mvi/handler/ClickHandler.kt", 1),
        ("mvi/handler/DialogClickHandler.kt", 2),
        ("mvi/handler/ExercisePickerHandler.kt", 4),
    ):
        source = source_of(relative)
        if source is None:
            continue
        require(relative, source, "private val stringResolver: StringResolver,", 1)
        require(relative, source, "stringResolver.getString(", reads)
        require(relative, source, "private suspend fun sendError(type: ErrorType)", 1)
    # CM3: the added toast resolves before the add's write, so no suspension separates the write
    # from its undo push; `val nextExercises = (` opens that write's lambda.
    picker_relative = "mvi/handler/ExercisePickerHandler.kt"
    picker_source = source_of(picker_relative)
    if picker_source is not None:
        anchors = (
            "val addedMessage = stringResolver.getString(",
            "val nextExercises = (",
            "message = addedMessage,",
        )
        for anchor in anchors:
            require(picker_relative, picker_source, anchor, 1)
        resolved_at, written_at, used_at = (picker_source.find(anchor) for anchor in anchors)
        if not 0 <= resolved_at < written_at < used_at:
            failures.append(
                f"{module}: {shown(picker_relative)} must resolve the added toast before its write"
            )

    # 4. 7.11's rules 4–7 over this module's layout: no top-level CMP read; mvi/ reads only through
    # the resolver; no ui/ file reads copy outside composition; the reader is gone everywhere.
    top_level_import = re.compile(
        r"^import org\.jetbrains\.compose\.resources\.(getString|getPluralString)\b", re.MULTILINE
    )
    mvi_prefix = (entry.common_main / "mvi").as_posix() + "/"
    ui_prefix = (entry.common_main / "ui").as_posix() + "/"
    mvi_files = ui_files = 0
    for path, raw in common_sources.items():
        source = strip_kotlin_comments(raw)
        relative = path.relative_to(entry.root)
        for match in top_level_import.finditer(source):
            failures.append(
                f"{module}: {relative} imports CMP's top-level {match.group(1)}; read copy "
                "outside composition through the injected StringResolver"
            )
        if "LiveWorkoutCopyReader" in source:
            failures.append(f"{module}: {relative} still names the Part B reader (SM0)")
        posix = path.as_posix()
        if posix.startswith(mvi_prefix):
            mvi_files += 1
            for call in ("getString(", "getPluralString("):
                total = source.count(call)
                through_resolver = source.count(f"stringResolver.{call}")
                if total != through_resolver:
                    failures.append(
                        f"{module}: {relative} reads copy outside the StringResolver: {call!r} "
                        f"{total} time(s), {through_resolver} through stringResolver"
                    )
            for token in ("stringResource(", "pluralStringResource("):
                if token in source:
                    failures.append(
                        f"{module}: {relative} resolves composable copy under mvi/: {token!r}"
                    )
        elif posix.startswith(ui_prefix):
            ui_files += 1
            for call in ("getString(", "getPluralString("):
                if call in source:
                    failures.append(
                        f"{module}: {relative} reads copy outside composition under ui/: {call!r}"
                    )
    if mvi_files == 0 or ui_files == 0:
        failures.append(
            f"{module}: copy contract walked {mvi_files} mvi/ and {ui_files} ui/ file(s); "
            "both must be non-empty"
        )
    return failures


def live_workout_dependency_contract(
    entry: FeatureEntry,
    common_sources: dict[Path, str],
) -> list[str]:
    # SM8 (kmp-phase-7-16-live-workout-feature.md Sections C8 and C10.1): core:data:database stays
    # one production edge, read by the domain mapper alone. The domain suite's repository fakes
    # spell its two set models, so that suite may import PlanSetDataModel and SetTypeDataModel and
    # nothing else from it. core:data:database-test is ExerciseDoneRuleTest's host fixture (7.13
    # D4-A): one androidHostTest edge, one import. Shared code names no java.*.
    failures: list[str] = []
    module = entry.module
    build_source = (entry.root / "build.gradle.kts").read_text(encoding="utf-8")
    fixture_edge = "\"androidHostTestImplementation\"(project(\":core:data:database-test\"))"
    if build_source.count(":core:data:database-test") != 1 or fixture_edge not in build_source:
        failures.append(
            f"{module}: :core:data:database-test must be exactly one "
            "androidHostTestImplementation edge"
        )
    mapper = entry.common_main / "domain/mapper/LiveWorkoutDomainMapper.kt"
    domain_suite = (
        entry.root / "src/commonTest/kotlin" / entry.package_dir
        / "domain/LiveWorkoutInteractorImplTest.kt"
    )
    done_rule_suite = (
        entry.root / "src/androidHostTest/kotlin" / entry.package_dir
        / "mvi/mapper/ExerciseDoneRuleTest.kt"
    )
    readers = {
        domain_suite: [
            "core.data.database.sets.PlanSetDataModel",
            "core.data.database.sets.SetTypeDataModel",
        ],
        done_rule_suite: ["core.data.database.testfixtures.WorkoutTargetParityFixture"],
    }
    if "core.data.database" not in strip_kotlin_comments(common_sources.get(mapper, "")):
        failures.append(
            f"{module}: {mapper.relative_to(entry.root)} "
            "must be the module's one production reader of core:data:database"
        )
    java_reference = re.compile(r"(?<![\w.])java\.")
    shared_sets = ("src/commonMain/", "src/commonTest/", "src/iosTest/")
    for path in sorted((entry.root / "src").rglob("*.kt")):
        source = strip_kotlin_comments(path.read_text(encoding="utf-8"))
        relative = path.relative_to(entry.root)
        references = re.findall(r"core\.data\.database[\w.]*", source)
        if path in readers:
            if references != readers[path]:
                failures.append(
                    f"{module}: {relative} may name core:data:database only as "
                    f"{readers[path]}; found {references}"
                )
        elif path != mapper and references:
            failures.append(
                f"{module}: {relative} must not read core:data:database;"
                " only the domain mapper maps its data models"
            )
        if relative.as_posix().startswith(shared_sets) and java_reference.search(source):
            failures.append(f"{module}: {relative} names java.* in shared code")
    return failures
~~~

The device contract of earlier entries is not needed: `device_fragments` pins the device class.

### C10.2 The Native oracle (commit 3)

`assert_kmp_ios_smoke.py` gains a `feature:live-workout` entry after exercise's, with `results_dir`
`feature/live-workout/build/test-results/iosSimulatorArm64Test`, the known suite prefix, and the 227
tuples of Section C9: the 226 portable identities under their 18 suites' classnames through
`natives(…)`, in Section C2.2's order, and the scene's
(`io.github.stslex.workeeper.feature.live_workout.LiveWorkoutFeatureSceneIosTest`,
`resourcesScreenSheetsDialogsAndActionsRenderAndDispatch`) through `native(…)`. The names are
written as UTF-8 (`ensure_ascii=False`), the two `—` names among them. That is 327 insertions. On a
green run it prints
`feature:live-workout: 227 executed / 0 skipped / 0 failed / 0 errored — verified 227 exact identities`.

### C10.3 The workflow (commit 3)

`android_build_unified.yml`, KMP iOS kit smoke job:

- `:feature:live-workout:iosSimulatorArm64Test` in the simulator test command and
  `:feature:live-workout:linkDebugTestIosArm64` in the device-link command, each after exercise's;
- the binary-assert loop's list gains `feature/live-workout` on a continuation line
  (`feature/exercise-chart feature/single-training feature/exercise \` /
  `feature/live-workout; do`), `$linked/15 linked` becomes `$linked/16 linked`, and
  `feature/live-workout/build/test-results/iosSimulatorArm64Test/` joins the upload list after
  exercise's;
- the job-header comment rewraps within 100 columns to "…, exercise-chart, single-training,
  exercise and live-workout features' Phase-7 Native tests on" / "the iOS simulator, then …", and
  the two "fifteen modules'" comments say "sixteen modules'".

That is 10 insertions and 6 deletions. The job, step and context names stay. The build job's
comment on the golden-holding modules stays true: live-workout already holds goldens.

The sixteen modules, for every Native command below: `:core:ui:kit`, `:core:ui:navigation`,
`:core:ui:mvi`, `:core:ui:start-mode`, `:core:ui:plan-editor`, `:feature:image-viewer`,
`:feature:plan-editor`, `:feature:archive`, `:feature:all-trainings`, `:feature:all-exercises`,
`:feature:past-session`, `:feature:home`, `:feature:exercise-chart`, `:feature:single-training`,
`:feature:exercise`, `:feature:live-workout`. The fifteen are the same list without the last.

**The topology output per commit.** The prototype printed, exit 0 and empty stderr, byte-identical
under Python 3.9 through 3.14:

| Tree | Lines | sha256 (prototype) | What changes against BASE_GATE_OUTPUT |
| --- | ---: | --- | --- |
| base, after commit 1 | 833 | `37315e70…` | nothing |
| after commit 2 | 989 | `32eb44ec3bb0829a1894875e242ab09f71152584cacc331844f53e3eddabcb22` | live-workout's `MODULES` block (154 lines: `  feature:live-workout: 153 exact source/resource/test files` and the 153 paths, after exercise's); `app:common API edges and 2 remaining Context.appDeps readers are exact` (was 3); `compose-resource placeholders: 28 commonMain catalogs walked, …` (was 26), with live-workout's EN (`36 placeholder(s)`) and RU (`40 placeholder(s)`) lines in path order |
| after commit 3 | 990 | `7271a2b1655ca7f294ed45ae2fa188c26dfd634c70071f5b737da8c6adc7f8d4` | and `  live-workout catalogs, semantic State, suppressions, previews, test identities, and explicit factory flow are exact`, after exercise's line |

The implementation's outputs must have exactly these lines and these differences; their hashes are
recorded, and a hash that differs from the prototype's while the lines hold is reported with the
differing lines. The oracle entry's tuples were generated from the prototype tree's names; that run
is not evidence.

### C10.4 Entry gate before any edit

Part B's Section B6.1 applies, with:

- the base is origin/dev after this amendment's commit; it contains
  `39be1b4ea2ed9954d332bee5ea8893f9e59dfeac`, and `git diff 39be1b4ea <base>` touches no path of
  Section C7 except this document;
- the latest `Android CI/CD - Unified Build and Tests` push run on dev is green (record its id and
  head; `39be1b4ea`'s is `40350332440`, which concluded success);
- the census of Section C2 is reproduced on the base, each item with its method: the manifests and
  counts of C2.1 (the 101 `R.` references in 11 files, the platform imports, the copy reads, the 50
  composition reads, the 61 / 11 lambdas and the eleven by file); C2.2's 18 suites and 225
  identities by suite and the retired files' 7; C2.2's name total (277) and, at exit, its hash;
  C2.3's golden methods, the 60 PNGs and their blob set, and the device class's six names; C2.4's
  catalogs (94 strings and 2 plurals each, the base hashes `e201fa09…` and `f4b9ad01…`, P1's twelve
  lines); C2.5's build file; C2.6's consumers; C2.7's 12 + 7 suppressions; C2.8's facts;
- the exit census (Section C12) on the base exits 1 with exactly the base column;
- the Mac's simulator runtime and Xcode first-launch state are recorded (`xcrun simctl list
  runtimes`); a missing runtime or an unfinished first launch is a STOP with the exact command for
  the maintainer.

Baselines, each with fresh XML:

- `python3 .github/scripts/assert_kmp_ui_source_topology.py` under every Python on the host:
  byte-identical, exit 0, empty stderr (BASE_GATE_OUTPUT: 833 lines, `37315e70…`);
- `python3 .github/scripts/assert_mvi_source_topology.py` (BASE_MVI_TOPOLOGY);
- `./gradlew assembleDebug lintDebug testDebugUnitTest --continue`: every module/task row's suites,
  cases and names (HOST_BASELINE; live-workout's `testDebugUnitTest` 26 suites and 241 cases; the
  repository 479 suites and 3,391 cases at the measured tree), and every module's lint findings by
  id, path and line text (LINT_BASELINE; Part A measured 80 dependency-version advisories in each of
  app:app, app:dev and app:store, and none elsewhere);
- `./gradlew verifyPaparazziDebug`: suites and cases (live-workout 30 and 301; the repository 174
  and 1,787), live-workout's `Visual gate live: 60 golden test case(s) executed for 60 golden
  image(s).`, porcelain empty afterwards;
- `./gradlew :lint-rules:test` (16 suites, 151 cases);
- `./gradlew :app:app:testDebugUnitTest --tests '*LiveWorkoutExtensionIdentityTest*'` (7 cases);
- the fifteen-module Native command from `android_build_unified.yml` with `--continue`, then
  `assert_kmp_ios_smoke.py` as CI runs it (NATIVE_BASELINE: 96 suites, 635 cases), and the
  fifteen-module `linkDebugTestIosArm64` (15/15 `test.kexe`);
- on the recorded serial: `:feature:live-workout:connectedDebugAndroidTest` (6 cases) and the four
  app classes (25 cases):

      ANDROID_SERIAL=<serial> ./gradlew :app:app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=io.github.stslex.workeeper.app.RouteReachabilityTest,io.github.stslex.workeeper.app.NavigationResultTest,io.github.stslex.workeeper.app.StoreRetentionTest,io.github.stslex.workeeper.app.BackStackStateRestorationTest --max-workers=2 --continue

### C10.5 Focused gates per commit

After commit 1: Section C10.0's proof, and detekt alone.

**Early Native check (before commit 2).** As soon as production and commonTest compile for Native,
before the scene and the app paths, run `:feature:live-workout:iosSimulatorArm64Test`. It is the
first Native compilation of this module's CMP `BackHandler` opt-in, the results overload, the
injected `StringResolver` reads and `kotlin.concurrent.atomics`, and the first Native run of the
watch-write contract on a real `BaseStore` and of the two `—` names: the fresh XML must hold the 226
identities by name. A failure here is a STOP-and-amend with the compiler or test output.

After commit 2, on the committed tree:

    python3 .github/scripts/assert_kmp_ui_source_topology.py   # 989 lines, every Python
    ./gradlew :feature:live-workout:assembleDebug              # Android and both iOS klibs
    ./gradlew :feature:live-workout:testAndroidHostTest        # 21 suites, 235 cases
    ./gradlew :feature:live-workout:verifyPaparazziDebug       # 25 suites, 295 cases: 60 golden + 235
    ./gradlew :feature:live-workout:iosSimulatorArm64Test      # 19 suites, 227 cases
    ./gradlew :app:common:assembleDebug
    ./gradlew :app:dev:assembleDebug                           # Metro validates the graph
    ./gradlew :app:app:testDebugUnitTest --tests '*LiveWorkoutExtensionIdentityTest*'   # 7 cases
    ./gradlew :lint-rules:test                                 # 16 suites, 151 cases

`verifyPaparazziDebug` must print `Visual gate live: 60 golden test case(s) executed for 60 golden
image(s).` and leave the porcelain empty. Then, on the recorded serial,
`:feature:live-workout:connectedDebugAndroidTest` (6 cases) and the four app classes (25 cases, the
five live-workout journeys among them), with fresh XML; the exit census (Section C12), which exits
0 from this commit on; and detekt alone.

After commit 3: the topology script (990 lines, every Python), the sixteen-module Native command
(`--continue`) and the oracle (16 modules, 115 suites, 862 cases), the sixteen-module device link
(`16/16 linked`), and detekt alone.

### C10.6 Repository, visual and device gates (on commit 3's tree)

Each in its own invocation, with Part B's flags (Section B6):

    ./gradlew assembleDebug lintDebug testDebugUnitTest --continue   # = HOST_BASELINE but live-workout (26 → 21 suites, 241 → 235 cases); lint = LINT_BASELINE
    ./gradlew detekt --continue
    ./gradlew assembleDebugAndroidTest
    ./gradlew verifyPaparazziDebug                       # = the baseline but live-workout (30 → 25, 301 → 295); porcelain empty; blob set 53221eab…
    ./gradlew :lint-rules:test
    ./gradlew :app:wear:testStoreReleaseUnitTest -Pandroid.onlyEnableUnitTestForTheTestedBuildType=false --tests '*ReleaseRuntimeBoundaryTest*'
    python3 .github/scripts/assert_kmp_ui_source_topology.py   # 990 lines, every Python
    python3 .github/scripts/assert_mvi_source_topology.py      # = BASE_MVI_TOPOLOGY
    python3 documentation/personal_data_gate.py -v
    ./gradlew <sixteen-module iosSimulatorArm64Test> --continue && python3 .github/scripts/assert_kmp_ios_smoke.py
    ./gradlew <sixteen-module linkDebugTestIosArm64> --continue   # 16/16

The repository rows are expected at 474 suites and 3,385 cases for the host task and 169 and 1,781
for `verifyPaparazziDebug` (the base's minus live-workout's 5 suites and 6 cases); any other
module's change is a STOP. "Lint = LINT_BASELINE" means every module's set of findings (id, path,
line text) equals the entry's; a finding on a Section C7 path, or one the baseline lacks, is a
STOP. The PNG manifest proof: the 60 blobs at their new paths equal the base's at the old ones,
blob for blob, and the blob set is `53221eab…`.

**Device gates**, on one API-34 emulator with an explicit serial and animations off:

- testing.md's canonical Smoke and Regression suites, in `run_smoke_ui_tests.sh`'s and
  `ui_tests.yml`'s form, then `assert_mvi_device_identities.py` as the Smoke script runs it. Compare
  with Part A's record (Section 14.6: Smoke 44 with the 3 documented skips, Regression 88); a new
  failure is a STOP.
- The logcat sweep of Section B6.6 around each suite; a `SCREEN_LiveWorkout` error line is a STOP.

**Final diff and remote proof.** Against the PR's base: `git diff --no-renames --name-status`
lists exactly Section C7's paths (D for the 156 base paths under `src/`, A for the 153 exit paths,
M for the build file and the 17 others); the 118 byte-identical moves keep their blob ids; the two
catalogs differ from their base blobs in P1's twelve lines only; no `.png` blob, no
`gradle/libs.versions.toml` line and no build file outside Section C7 changes. Record the KMP iOS
kit smoke job's duration on the final head against its 120-minute timeout, next to 7.15's.

## C11. Mandatory known-negative controls

7.15 Section B13's procedure applies: every control runs fresh GREEN → one named observable RED →
exact restoration → fresh GREEN. Gradle- and device-scored controls go through
`documentation/mockups/mutation_harness.py` one-shot, with `--no-configuration-cache
--console=plain` inside `--task`; Python-scored controls run against MAIN's tree with a
byte-restoring scratchpad copy of each mutated file, restored with `cp` and checked by sha256 and
mode (no worktree, Section C0.2). A compile-invalid mutation is never a RED where a compiled test is
the detector; no mutation is committed. Every control runs after commit 3, on its committed tree.
"Only" means no other identity, suite or line is RED; "at least" means the named ones must be RED
and every other RED is recorded. Consecutive controls with a byte-identical task may share a GREEN
when HEAD is unchanged, the porcelain is empty and the restored file's sha256 and mode equal HEAD's.

Paths are relative to `src/commonMain/kotlin/<pkg>/` unless they start with `src/`, `app/`,
`feature/`, `lint-rules/` or `.github/`; `…` abbreviates a module-relative path, which the
implementation writes out. A topology RED names exactly the lines quoted, each prefixed
`feature:live-workout: ` unless it starts with another module or `remaining`.

**From 7.15 (Section B13's rows 1–17, 21–25), with live-workout's names:**

| # | Mutation | Scorer: expected RED |
| ---: | --- | --- |
| K1 | delete `ui/components/LiveWorkoutHeader.kt` | topology: `source manifest mismatch; missing=['…/ui/components/LiveWorkoutHeader.kt'], extra=[]` and `…/ui/components/LiveWorkoutHeader.kt must hold bare @Preview annotations but is missing` |
| K2 | copy `di/LiveWorkoutScope.kt` to `src/main/kotlin/<pkg>/di/LiveWorkoutScope.kt` | topology: manifest `extra=['src/main/…/di/LiveWorkoutScope.kt']`, `legacy source set src/main still contains […]` and `unexpected Kotlin-bearing source set src/main: […]` |
| K3 | move `ui/components/LiveWorkoutHeader.kt` to `ui/LiveWorkoutHeader.kt` | topology: manifest `missing` and `extra`; `…/ui/components/LiveWorkoutHeader.kt must hold bare @Preview annotations but is missing` and `…/ui/LiveWorkoutHeader.kt must hold exactly 0 bare @Preview annotation(s); found 3` |
| K4 | `import android.util.Log` after the package line of `ui/components/SessionSheets.kt` | topology: `…SessionSheets.kt imports a platform API in commonMain: 'import android.'` |
| K5 | EN: `name="feature_live_workout_sheet_close"` → `name="feature_live_workout_sheet_close_renamed"` | topology: the EN catalog mismatch (`unexpected=['feature_live_workout_sheet_close_renamed']`, `order=drifted`) and two ownership-drift lines |
| K6 | RU: `Закрыть` → `Закрыть окно` in `feature_live_workout_sheet_close` | topology: the RU catalog mismatch, `order=exact` |
| K7 | move the EN `feature_live_workout_sheet_close` line into `feature/settings/src/main/res/values/strings.xml`, before `</resources>` | topology: the EN catalog mismatch (`order=drifted`) and `resource ownership drift for feature_live_workout_sheet_close; …` |
| K8 | `val titleRes: Int = 0,` after State's `val bottomSheetState: BottomSheetState,` | topology: `Store State must stay semantic with exactly (…)` |
| K9 | `resetTitle = stringResolver.getString(LiveWorkoutRes.string.feature_live_workout_reset_title),` → `resetTitle = resourceWrapper.getString(0),` | topology: `…CommonHandler.kt resolves live-workout copy through ResourceWrapper.getString; use the private Compose resources`; `…CommonHandler.kt copy contract requires 'stringResolver.getString(' exactly 31 times; found 30`; `…CommonHandler.kt reads copy outside the StringResolver: 'getString(' 31 time(s), 30 through stringResolver` |
| K10 | the load failure's `updateState { it.copy(isLoading = false, loadFailed = true) }` → a block whose first line is `val label = stringResolver.getString(LiveWorkoutRes.string.feature_live_workout_reset_title)` | topology: `updateState in …CommonHandler.kt resolves a resource inside the State lambda: 'getString('` and `… 'Res.'`; `…CommonHandler.kt copy contract requires 'stringResolver.getString(' exactly 31 times; found 32` |
| K11 | the first bare `@Preview` of `ui/components/LiveWorkoutHeader.kt` → `@Preview(uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES)` | topology: forbidden `'android.content'` and `'uiMode'`; `exactly six portable previews are required; found 7`; `…LiveWorkoutHeader.kt must hold exactly 3 bare @Preview annotation(s); found 2` |
| K12 | `LiveWorkoutFeature.processor`'s body → `val context = LocalContext.current`, `val contextFactory = context.appDeps<LiveWorkoutGraph.Factory>()`, and a retained creation that reads `contextFactory.createLiveWorkoutGraph(screen).liveWorkoutStore` | topology: `remaining Context.appDeps readers are not the exact 2 unported entries; …`, forbidden `'LocalContext'` and `'appDeps<'`, `factory invocation must occur exactly inside retained Store creation` |
| K13 | delete `val liveWorkoutGraphFactory: LiveWorkoutGraph.Factory` and its KDoc from `AppRootDeps` | topology: `exact root-factory flow requires 'val liveWorkoutGraphFactory: LiveWorkoutGraph.Factory' once in app/common/…/AppRootDeps.kt` |
| K14 | `val graph = factory.createLiveWorkoutGraph(screen)` before `rememberMetroStoreProcessor`, whose lambda then reads `graph.liveWorkoutStore` | topology: `factory invocation must occur exactly inside retained Store creation` |
| K15 | a second `(context.applicationContext as AppRootDepsHolder).appRootDeps()` inside the admitted `remember(currentPhase.id) { … }` in `App.kt` | device: `UiAdmissionRaceTest`, as in 7.10 |
| K16 | hoist `val deps = remember(currentPhase.id) { …appRootDeps() }` above `if (admission.granted) {` | device: `UiAdmissionRaceTest`, as in 7.10 |
| K17 | the identity helper's `liveWorkoutGraphFactory.createLiveWorkoutGraph(` → `asContribution<LiveWorkoutGraph.Factory>().createLiveWorkoutGraph(` | topology: the identity-helper fragment, and `extension identities must not bypass AppRootDeps via asContribution` |

**D7:**

| # | Mutation | Scorer: expected RED |
| ---: | --- | --- |
| K18 | `LIVE_WORKOUT_ENTRY.state_mutators` loses `"LiveSetMutator"` | topology: exactly eleven `updateState in <file> must return a State copy` lines: `…ClickHandler.kt` 8, `…CommonHandler.kt` 2, `…DialogClickHandler.kt` 1 |
| K19 | it loses `"StateStatusMapper"` | topology: exactly `state mutator LiveSetMutator in …LiveSetMutator.kt reaches StateStatusMapper, which state_mutators does not list` |
| K20 | it loses `"LiveWorkoutMapper"` | topology: exactly the `reaches LiveWorkoutMapper` lines of `LiveSetMutator` and `StateStatusMapper` |
| K21 | it loses `"LiveSetRowsResolver"` | topology: exactly the `reaches LiveSetRowsResolver` lines of `LiveSetMutator` and `LiveWorkoutMapper` |
| K22 | it loses `"LiveWorkoutDomainMapper"` | topology: exactly the `reaches LiveWorkoutDomainMapper` lines of `LiveSetMutator` and `LiveWorkoutMapper` |
| K23 | it loses `"ExerciseDoneRule"` | topology: exactly the `reaches ExerciseDoneRule` lines of `StateStatusMapper` and `LiveWorkoutMapper` |
| K24 | append `private val probe = Res.string.feature_live_workout_status_skipped` to `mvi/mapper/LiveSetMutator.kt` | topology: exactly `state mutator LiveSetMutator in …LiveSetMutator.kt holds 'Res.'; a State lambda returns only through pure code` |
| K25 | append `private val stringResolver: Any? = null` to `mvi/mapper/StateStatusMapper.kt` | topology: exactly `state mutator StateStatusMapper in …StateStatusMapper.kt names 'stringResolver'` |
| K26 | append `internal fun probe(resolver: StringResolver): StringResolver = resolver` to `mvi/mapper/LiveWorkoutMapper.kt` | topology: exactly `state mutator LiveWorkoutMapper in …LiveWorkoutMapper.kt takes StringResolver` |
| K27 | append `private val railProbe = RailMapper` to `mvi/mapper/LiveWorkoutMapper.kt` | topology: exactly `state mutator LiveWorkoutMapper in …LiveWorkoutMapper.kt reaches RailMapper, which state_mutators does not list` |
| K28 | append `internal class LiveSetMutator` to `mvi/handler/LiveWorkoutDraftExt.kt` | topology: exactly `state mutator LiveSetMutator must be declared once in commonMain; found 2` |
| K29 | `ClickHandler`: `updateState { it.copy(bottomSheetState = BottomSheetState.SessionMenu) }` → `updateState { interactor.run { it } }` | topology: exactly one `updateState in …ClickHandler.kt must return a State copy` (a non-mutator property does not delegate) |

**The copy contract (D5, D6, SM0, CM2, CM3):**

| # | Mutation | Scorer: expected RED |
| ---: | --- | --- |
| K30 | `liveCopy = resolveCopy(),` → `liveCopy = LiveWorkoutCopy.EMPTY,` | topology: exactly `…CommonHandler.kt copy contract requires 'liveCopy = resolveCopy(),' exactly once; found 0`; host and Native: at least D8's identity |
| K31 | append `private typealias LiveWorkoutCopyReader = Any` to `mvi/handler/CommonHandler.kt` | topology: exactly `…CommonHandler.kt copy contract requires 'LiveWorkoutCopyReader' exactly zero times; found 1` and `…CommonHandler.kt still names the Part B reader (SM0)` |
| K32 | `import org.jetbrains.compose.resources.getString` after `ClickHandler`'s `StringResolver` import | topology: exactly `…ClickHandler.kt imports CMP's top-level getString; read copy outside composition through the injected StringResolver` |
| K33 | `ui/LiveWorkoutGraph.kt`: `val loadFailedMessage = stringResource(Res.string.feature_live_workout_error_session_load_failed)` → `val loadFailedMessage = runBlocking { getString(Res.string.feature_live_workout_error_session_load_failed) }` | topology: exactly `…ui/LiveWorkoutGraph.kt reads copy outside composition under ui/: 'getString('` |
| K34 | move `ExercisePickerHandler`'s `val addedMessage = stringResolver.getString(…)` block (four lines) from before the add's `updateState { latest ->` to directly before `// Undo removes the rows the add just wrote.` | topology: exactly `…ExercisePickerHandler.kt must resolve the added toast before its write` |
| K35 | `message = stringResolver.getString(Res.string.feature_live_workout_finish_success),` → `message = "",` | topology: exactly `…DialogClickHandler.kt copy contract requires 'stringResolver.getString(' exactly two times; found 1` |
| K36 | `ExercisePickerHandler`: `private suspend fun sendError(type: ErrorType)` → `private fun sendError(type: ErrorType)` | topology: exactly `…ExercisePickerHandler.kt copy contract requires 'private suspend fun sendError(type: ErrorType)' exactly once; found 0` (Python only: the mutation does not compile) |
| K37 | `nowMillis = Clock.System.now().toEpochMilliseconds(),` → `nowMillis = System.currentTimeMillis(),` in `CommonHandler` | topology: exactly `…CommonHandler.kt copy contract requires 'System.currentTimeMillis' exactly zero times; found 1` (Python only) |
| K38 | `LiveWorkoutCopy`: `val errorFinishFailed: String,` → `val errorFinish: String,` and `errorFinishFailed = "",` → `errorFinish = "",` | topology: exactly the `LiveWorkoutCopy parameters must be exactly [...]` line and `…LiveWorkoutCopy.kt copy contract requires 'errorFinishFailed = "",' exactly once; found 0` (Python only) |

**The dependency contract (SM8):**

| # | Mutation | Scorer: expected RED |
| ---: | --- | --- |
| K39 | `import io.github.stslex.workeeper.core.data.database.sets.PlanSetDataModel` as the first import of `domain/LiveWorkoutInteractorImpl.kt` | topology: exactly `…LiveWorkoutInteractorImpl.kt must not read core:data:database; only the domain mapper maps its data models` |
| K40 | `import io.github.stslex.workeeper.core.data.database.exercise.ExerciseEntity` before the `PlanSetDataModel` import of `src/commonTest/…/domain/LiveWorkoutInteractorImplTest.kt` | topology: exactly `…LiveWorkoutInteractorImplTest.kt may name core:data:database only as [...]; found [...]` |
| K41 | `"androidDeviceTestImplementation"(project(":core:data:database-test"))` after the bundle line of the build file | topology: exactly `:core:data:database-test must be exactly one androidHostTestImplementation edge` |
| K42 | `private val probe = java.util.UUID.randomUUID()` after `CommonHandlerTest`'s `private val interactor = LoadingInteractor()` | topology: exactly `src/commonTest/…/CommonHandlerTest.kt names java.* in shared code` |
| K43 | both `import io.github.stslex.workeeper.core.data.database.` prefixes of `domain/mapper/LiveWorkoutDomainMapper.kt` → `import io.github.stslex.workeeper.core.data.legacy.` | topology: exactly `…LiveWorkoutDomainMapper.kt must be the module's one production reader of core:data:database` (Python only) |

**The entry, the identities and the root flow:**

| # | Mutation | Scorer: expected RED |
| ---: | --- | --- |
| K44 | a 227th `@Test`, `` `a two-hundred-and-twenty-seventh identity nobody declared` `` (`= Unit`), before `NavigationHandlerTest`'s first identity | topology: exactly `exact test identities drifted in …NavigationHandlerTest.kt; missing=[], unexpected=['a two-hundred-and-twenty-seventh identity nobody declared']` |
| K45 | the scene's `fun resourcesScreenSheetsDialogsAndActionsRenderAndDispatch()` → `fun renamedSceneSubstitute()` | topology: exactly the drift line for the scene, `missing=['resourcesScreenSheetsDialogsAndActionsRenderAndDispatch'], unexpected=['renamedSceneSubstitute']`; Gradle GREEN, then the oracle (K59) |
| K46 | in `LiveWorkoutExtensionIdentityTest`, `val store = buildAppGraph().liveWorkout(sessionUuid = "s-1").liveWorkoutStore` → `val store = buildAppGraph().liveWorkoutGraphFactory` / `.createLiveWorkoutGraph(Screen.LiveWorkout(sessionUuid = "s-1", trainingUuid = null)).liveWorkoutStore` | topology: exactly `all seven extension identities must reach the graph through the accessor` |
| K47 | `AppNavigationHost`: delete `factory = liveWorkoutGraphFactory,` from the `liveWorkoutGraph(` call | topology: exactly `exact root-factory flow requires 'liveWorkoutGraph(factory = liveWorkoutGraphFactory,' once in app/common/…/AppNavigationHost.kt` |
| K48 | `navComponentScreenWithResults(LiveWorkoutFeature(factory))` → `navComponentScreen(LiveWorkoutFeature(factory))` | topology: exactly `exact root-factory flow requires 'navComponentScreenWithResults(LiveWorkoutFeature(factory))' once in …ui/LiveWorkoutGraph.kt` |
| K49 | `SessionHeaderGoldenTest`'s `fun topbar(theme` → `fun topbarProbe(theme` | topology: exactly `golden methods in src/androidHostTest/…/SessionHeaderGoldenTest.kt must be exactly ['topbar', 'sheadDefault', 'sheadSkippedMeta', 'sheadEditing']; actual=['topbarProbe', …]` |
| K50 | copy `golden/SessionScreenGoldenTest.kt` to `golden/SessionScreenCopyGoldenTest.kt` (androidHostTest) | topology: manifest `extra=[…SessionScreenCopyGoldenTest.kt]`, the suppressions line (`suppressions must be exactly the nine inherited production and zero Native test-name annotations; …`) and `…SessionScreenCopyGoldenTest.kt renders goldens but is neither golden_test nor in extra_golden_tests` |
| K51 | P1: EN `%1$d of %2$d exercises · %3$d of %4$d sets` → `%d of %2$d exercises · %3$d of %4$d sets` | topology: the EN catalog mismatch (`order=exact`) and `compose-resource placeholder is not %N$d or %N$s with N >= 1: …values/strings.xml key feature_live_workout_meta_format token '%d'` |
| K52 | `LiveWorkoutScreenTest`: `fun finish_button_dispatches_OnFinishClick()` → `fun finish_button_dispatches_Finish()` | topology: exactly `device placeholder must keep 'fun finish_button_dispatches_OnFinishClick()' exactly once` |
| K53 | `@Suppress("MagicNumber")` directly above `internal object RailMapper` | topology: exactly the suppressions line |
| K54 | `app/common/build.gradle.kts`: `api(project(":feature:live-workout"))` → `implementation(project(":feature:live-workout"))` | topology: exactly `app:common: live-workout edge must be exactly one api dependency` and `app:common: live-workout edge must not remain implementation` |
| K55 | `val messageRes: StringResource? = null,` after `PendingUndo`'s `val message: String,` | topology: exactly `…PendingUndo.kt carries a resource handle/lookup 'StringResource' in a Store/domain payload` and the same for `'StringRes'` |

**Gradle-, PNG- and detekt-scored:**

| # | Mutation | Scorer: expected RED |
| ---: | --- | --- |
| K56 | D8: `resetTitle = stringResolver.getString(LiveWorkoutRes.string.feature_live_workout_reset_title),` → `…feature_live_workout_cancel_title),` | host and Native: `a load resolves every copy field from its own catalog key`, and only it; topology GREEN (the count holds) |
| K57 | the scene's `setContent { … }` body → `setContent { }` | Native: the scene, at its first node assertion; the 226 portable identities pass |
| K58 | `ui/LiveWorkoutScreen.kt`: the back button's `onClick = { consume(Action.Click.OnBackClick) },` → `onClick = { },` | Native: the scene's step 10 (`Action.Click.OnBackClick` missing from the list), and only it |
| K59 | K45's rename | Gradle GREEN (the renamed case passes), then the oracle: `feature:live-workout: expected exactly one testcase with classname='io.github.stslex.workeeper.feature.live_workout.LiveWorkoutFeatureSceneIosTest' name='resourcesScreenSheetsDialogsAndActionsRenderAndDispatch[iosSimulatorArm64]', found 0; parsed 227 testcase(s): …` |
| K60 | flip (XOR 0xFF) the last byte of `src/androidHostTest/snapshots/images/io.github.stslex.workeeper.feature.live_workout.golden_SessionScreenGoldenTest_sessionScreen_light.png` | PNG assertion: that blob's drift, the blob-set drift, and the manifest drift |
| K61 | a compile-valid overlong assertion line in `src/commonTest/…/mvi/handler/NavigationHandlerTest.kt` | root detekt, alone: findings on that line only |
| K62 | EN `feature_live_workout_finish`: `Finish session` → `Finish session.` | `verifyPaparazziDebug`: `SessionScreenGoldenTest.sessionScreen`, both themes, and only it; Native: the scene's step 1; topology: the EN catalog mismatch |
| K63 | RU `feature_live_workout_setbar_add`: `+ подход` → `+ подход!` | `verifyPaparazziDebug`: `SessionStateGoldenTest.exerciseBodyweightRu`, both themes, and only it; topology: the RU catalog mismatch |
| K64 | lint-rules: `PERMITTED_READERS`' `feature/live-workout/src/commonMain/kotlin/…` → `feature/live-workout/src/main/kotlin/…` | `:lint-rules:test`: at least `the permitted reader passes`; root detekt: a finding on `LiveExerciseCard.kt`'s reader call |
| K65 | delete `CommonHandlerExternalWritesTest`'s `Log.isLogging = false` | host: at least one `CommonHandlerExternalWritesTest` identity RED with `… in android.util.Log not mocked` (record the set); Native: GREEN — CM9's switch is what keeps the plain Android host green |
| K66 | the oracle on copies of the fresh sixteen result directories: delete the scene's `<testcase>` from live-workout's XML copy | the oracle, run from the copies' root: `… found 0; parsed 226 testcase(s): …` |
| K67 | the same copies: `<skipped/>` inside one live-workout `<testcase>`, and the suite's `skipped="1"` | the oracle: `<testsuite name=…> in <file> reports skipped=1, expected 0` |

K34's move keeps the four lines verbatim. K57 and K58 are 7.15's rows 21 and 22; K60 and K61 its
rows 24 and 25.

Every new or changed identity has a RED: D8's (K30, K56), the scene (K45, K57, K58, K59, K62,
K66, K67), the seven extension identities (K17, K46), the device class (K52), the goldens (K49,
K50, K60, K62, K63).

Each of the 55 Python-scored runs (K1–K14, K17–K55, and the topology halves of K62 and K63), and
Section C10.0's M1, M2 and Q1 (on the measured tree), was dry-run against the prototype tree under
Python 3.9, 3.10, 3.11, 3.12, 3.13 and 3.14 while this document was written. Each produced exactly
its named result, byte-identical across interpreters, without a traceback, and the restored tree
gave a fresh GREEN byte-equal to the exit output. That dry run is not evidence. The
implementation's fresh runs are.

Scorers:

- **Python, the topology script as CI runs it:** K1–K14, K17–K55, and the topology halves of K62
  and K63;
- **PNG:** K60;
- **Gradle:**
  - `:feature:live-workout:testAndroidHostTest :feature:live-workout:iosSimulatorArm64Test --continue`
    for K30's and K56's identities, K57, K58, K62's Native half and K65;
  - `:feature:live-workout:verifyPaparazziDebug` for K62's and K63's golden halves;
  - the sixteen-module Native command and the oracle for K59;
  - the oracle alone, on result-directory copies, for K66 and K67;
  - `:lint-rules:test`, then root detekt alone, for K64; root detekt alone for K61;
- **device:** K15 and K16 (`UiAdmissionRaceTest` on the recorded serial, fresh XML).

## C12. The exit census (X1–X12)

One script, written by the session (Section C0.2's method), run on the base and on commit 2's and
commit 3's trees. X5 uses the gate's own functions, imported from
`.github/scripts/assert_kmp_ui_source_topology.py`. It exits 0 only when every item holds, prints
each item's input count, and must exit 1 on the base.

| Id | Item | At exit | At the base |
| --- | --- | --- | --- |
| X1 | files importing `android.content.Context`, and `context.getString(` calls, in every module source set | none; 0 | `…/mvi/handler/LiveWorkoutCopyReader.kt` and `…/mvi/mapper/LiveWorkoutTemplateParityTest.kt`; 3 |
| X2 | Kotlin files in the repository (outside `build/`) naming `LiveWorkoutCopyReader` or `copyReader` | none | 5: the reader, `CommonHandler` and three test files |
| X3 | module files naming `ResourceWrapper` or `resourceWrapper` | none | 7: the three launch-reading handlers and four suites |
| X4 | `stringResolver.getString(` calls per production file | `ClickHandler` 1, `CommonHandler` 31, `DialogClickHandler` 2, `ExercisePickerHandler` 4 | `CommonHandler` 1 |
| X5 | Part B's lambda census (Section B2.2): lambdas, without `.copy(`, holding a token, token hits, resolver references, same-file calls, module reach | 61, 11, 0, 0, 0, 0, 0 | the same |
| X6 | the lambdas without `.copy(` that reach `setMutator` by `.` or `::`, per file | `ClickHandler` 8, `CommonHandler` 2, `DialogClickHandler` 1 | the same, under `src/main` |
| X7 | commonTest and iosTest files; `org.junit`, `io.mockk`, `org.robolectric`, `@Config`, `Thread.`, `java.` occurrences in them; files holding `runBlocking` | 21; none; the scene only | 0 |
| X8 | the feature's `R.string.` / `R.plurals.` / `R.drawable.` / `R.dimen.` references, and `import …feature.live_workout.R` lines | 0, 0 | 128, 14 |
| X9 | `<string name=` and `<plurals name=` per catalog; with the base: each catalog's changed lines | 94 and 2 in each; EN 4 and RU 8 changed lines, every one an `<item quantity=` line whose only change is `%d` → `%1$d` | 94 and 2 |
| X10 | the goldens' PNGs: count and blob-set sha256 (Section B2.6's definition) | 60, `53221eab…` | the same |
| X11 | `src/main`, `src/test`, `src/androidTest` present | none | all three |
| X12 | `liveWorkoutGraphFactory` per app file; `asContribution<LiveWorkoutGraph` in app; `appDeps<LiveWorkoutGraph` in the repository | `App.kt` 2, `AppGraph.kt` 1, `AppNavigationHost.kt` 2, `AppRootDeps.kt` 1, `LiveWorkoutExtensionIdentityTest.kt` 1; 0; 0 | none; 1; 1 |

Calls are read with Section C0.2's method (comments and string literals blanked, a call across
lines is one call). The census controls run on scratchpad copies of a file, restored with `cp` and
sha256-checked, each followed by the restored tree's exit 0:

| Id | Mutation | Required verdict |
| --- | --- | --- |
| Y1 | `CommonHandler.kt`: `import android.content.Context` after the package line | exit 1: X1 = `[…/mvi/handler/CommonHandler.kt]`, 0 |
| Y2 | `ClickHandler.kt`: append `internal suspend fun probe(stringResolver: StringResolver) = stringResolver.getString(Res.string.feature_live_workout_finish)` | exit 1: X4 `ClickHandler` 2 |
| Y3 | `ClickHandler.kt`: K29's mutation | exit 1: X5 = 61, 12, 0, 0, 0, 0, 0; X6 unchanged |

The prototype census produced exactly these verdicts on the prototype tree and the base.

## C13. STOP conditions

Part B's Section B9 applies, read with Section C0.3's substitutions. In addition, stop, report what
was measured and widen nothing, if:

- the base lacks `39be1b4ea`, the latest dev push run is red, or a Section C7 path drifted;
- the entry census or a baseline differs from Sections C2 and C10.4, or the exit census on the base
  differs from Section C12's base column;
- commit 1's proof (Section C10.0) shows any differing byte, or a Q row deviates;
- the move needs anything beyond Sections C3–C5 and C8: a production change outside Section C2.1's
  14 files and the reader's deletion; a click path that suspends for copy; a change to the copy
  model, the filler, a State lambda, the Store contract, an Event, a `DialogState` variant or a UI
  model;
- the copy port needs a resource handle, `ResourceWrapper` or `StringResolver` in a payload file, or
  resolution or a resolver reference inside a State lambda, directly, through a same-file function
  or through a state mutator; or D7's contract fails on the exit tree, or a lambda needs a mutator
  outside CM12's six;
- an identity needs a name or assertion change; a fake cannot reproduce the mock behaviour an
  identity relies on (CM6); or a strict fake would have to answer a member no identity reaches;
- `CommonHandlerExternalWritesTest` fails on either platform: record the identity, the platform and
  the trace. Never relax a wait, an assertion or the dispatcher order;
- **any of the 60 PNG blobs changes** under `verifyPaparazziDebug`: record the method, the theme
  and the differing region. It means CMP renders a catalog, a plural or a font differently from
  Android resources. Never re-record;
- `navComponentScreenWithResults` with the class-based feature, CMP's `BackHandler` under
  `ExperimentalComposeUiApi`, `kotlin.concurrent.atomics`, CMP resources' generated accessors, or
  `implementation(project(":core:data:database"))` does not resolve for the iOS targets in
  commonMain: STOP-and-amend with the compiler output;
- the Native scene's catalog differs from Section C10.1's EN column in any value, or steps 2–4
  differ from Section C9.5;
- `SetText`, a click inside a sheet's or dialog's content, or `performScrollToNode` cannot run on
  the simulator (Section C9.5);
- a Native test name fails to compile, or appears in the XML under another name than its source
  (the two `—` names included). Never rename an identity;
- a test source set needs `core:data:database` beyond the interactor suite's two models, or
  `core:data:database-test` beyond `ExerciseDoneRuleTest`;
- Metro rejects the graph after the move, or the generated extension needs a handler's constructor
  public;
- the module's device class, the four app classes or `UiAdmissionRaceTest` fails on the recorded
  serial;
- a control's verdict differs from its row, or a mutation meant for a compiled detector does not
  compile;
- detekt reports anything; lint reports a finding on a Section C7 path or any finding the baseline
  lacks; any suppression becomes necessary. The fix is never a suppression;
- a host case outside live-workout changes count or outcome; the topology output differs from
  Section C10.3's lines; the fifteen earlier modules' Native or link results change;
- a gate needs a build-file, workflow, CI-script, catalog (beyond P1) or version change other than
  Sections C8 and C10;
- the KMP iOS kit smoke job times out on any PR head;
- a device suite fails in a way Part A's record does not, or a `SCREEN_LiveWorkout` error line
  appears in the device logcat;
- the maintainer rules, before the run, that the copy must re-resolve on a locale change (CM16):
  STOP-and-amend;
- any path outside Section C7 changes.

A STOP produces a measured note and, where needed, an amendment proposal. It never widens the
implementation silently.

## C14. Signed, bisect-green commit plan

Four English Conventional Commits. Each is signed, GitHub-verified and green on its own tree:

1. **ci(kmp): let a State lambda return through the module's pure mutators** — D7 only (Section
   C10.0): the field, the two patterns, the contract, the condition and the call; 1 path.
2. **refactor(kmp): share live-workout feature entry** — the module (Sections C3–C9 and C7.1), the
   seven app paths and the two lint-rules paths (Section C5), and the minimal topology delta of
   Section C10.1 (commit 2).
3. **ci(kmp): gate shared live-workout feature** — `LIVE_WORKOUT_ENTRY` with its two contracts, the
   Native oracle entry, and the workflow lists and comments (Sections C10.1–C10.3).
4. **docs(kmp): record Phase 7.16 Part C evidence** — the four documents of Section C7.4; this
   document's Status line, which becomes
   `**Status:** COMPLETE — Part A by PR #344, Part B by PR #345, Part C by the PR whose merge brought`
   / `this line to dev.`; and Section C17. Sections 0–14, B0–B13 and C0–C16 stay byte-identical.

Each commit is made first. Its gates then run on the committed tree, and so do the controls (after
commit 3) and the repository gates. A fix is a new signed commit inside Section C7, and every gate
it can reach re-runs. Nothing is amended, squashed or reordered.

## C15. Exit criteria

### C15.1 PR-M

Section C1 is proven and Sections C10–C12 are evidenced, in one PR into dev with CI green on its
head:

- commit 1's proof;
- the 154-path module (Section C7.1) and its two private catalogs, 96 identifiers, changed only by
  P1's twelve lines;
- copy outside composition only through `StringResolver`: the load's 31 reads and the seven launch
  reads; no `ResourceWrapper`, no reader and no `Context` in the module (X1–X4);
- 61 State lambdas, the 11 without `.copy(` returning through CM12's six mutators, none resolving
  copy or naming a resolver (X5, X6 and D7's contract);
- 226 identities on the Android host and on Native, and 227 Native tuples, the scene's among them;
- 60 golden cases with unchanged blobs, the four classes pinned;
- 6 device identities from `androidDeviceTest`, the 7 extension identities through the accessor,
  and the 25 app identities of Section C9.4;
- the eleven production suppressions and the host golden's one; none in commonTest or iosTest;
- the explicit shape-B factory flow through `navComponentScreenWithResults`;
- `core:data:database` one production `implementation` edge read by the domain mapper alone, and
  `core:data:database-test` one `androidHostTest` edge read by `ExerciseDoneRuleTest` alone;
- the topology entry, the oracle and the workflow at sixteen modules; Context readers 3 → 2;
- every control of Section C11 and every census item of Section C12 evidenced;
- the Section C7.4 documents updated.

`@claude review` is requested, and every finding is classified (correct / already decided / wrong
/ new) before any fix. The maintainer merges.

### C15.2 The owner's device check (after the merge; not a gate)

On the dev build of the merged tree, with the watch paired and `adb logcat -s SCREEN_LiveWorkout`
running: Part B's Section B11.2 list, except the delete-session dialog, which no UI opens (Section
C16), in English and in Russian. Expected: nothing differs from the build before PR-M; a device
whose first locale has no catalog shows English (OC1). A difference goes to the maintainer as a
finding.

## C16. Carried out of Part C

- **The dead delete-session surface** (CM17). No composable dispatches
  `Action.Click.OnDeleteSessionMenuClick` at `39be1b4ea`:
  `git grep -n OnDeleteSessionMenuClick -- '*.kt'` returns the Store's declaration, `ClickHandler`'s
  branch and two `ClickHandlerTest` identities. With it, the action,
  `processDeleteSessionMenuClick`, `DialogState.DeleteDialog`, the screen's branch,
  `DialogClickHandler`'s confirm and dismiss, the copy's `deleteSessionUnnamed` and
  `deleteSessionProgressTemplate`, and kit's `DiscardSessionConfirmDialog` (whose only caller is
  that branch) have no production path. v2.0 shipped a Delete session item in the session overflow;
  the menu now offers Add exercise and Cancel session only. Part C moves the surface unchanged and
  tech-debt.md records it (Section C7.4). Restoring the item or dropping the surface is the
  maintainer's call.
- **The locale property** (CM16, XC1): the copy in State keeps the latest load's locale until the
  screen is re-entered or a plan-editor save reloads it, while composition reads relabel at once.
  tech-debt.md's new row records it; re-resolving at the boundary is a cross-feature decision.
- **The frontier.** After Part C the two Context readers left are app-dialogs' and settings'
  (Section C1). Settings follows once `feature:app-dialogs:api` is shared (7.15 Section B17); then
  `app/common` and the permanent iOS host. A throwaway iosApp remains forbidden.

## C17. Implementation evidence

Recorded by commit 4. Every Gradle gate ran after `./gradlew --stop` with `--rerun-tasks
--no-build-cache --no-configuration-cache --console=plain`, on a daemon registry private to this
run, and every count below is parsed from JUnit XML written after the gate's recorded start.
Repository-wide, Native and link gates added `-Dorg.gradle.jvmargs=-Xmx4g --max-workers=2`; device
suites used testing.md's canonical `-Dorg.gradle.jvmargs=-Xmx3g --max-workers=2 --full-stacktrace
--continue`. detekt never shared an invocation with tests. The run used one checkout and no
worktree.

### C17.1 Base and commits

- Base: `6d88cedcd19613db5b36dcb5133befda35e33ca0`, this amendment's landing on dev (parent
  `39be1b4ea`). It changed one Markdown file, so no unified run started for it.
- Commit 1: `5d91f0645bd69e98dbb651b0660e2ae3644bb015`, the topology script (71 insertions, 1
  deletion); local `gpgsig` header present.
- Commit 2: `3e3c916a74860d656454017936f5d97ce998a5ff`, 156 deletions under `src/`, 153 additions
  and 11 modified paths in `git diff --no-renames`: the build file, the 7 app paths, the 2
  lint-rules paths and the topology script (169 insertions, 1 deletion); local `gpgsig` header
  present.
- Commit 3: `c4cfb1642338b9d60513f969d5d06bc2676884b6`, the topology script (829 insertions), the
  oracle (327 insertions) and the workflow (10 insertions, 6 deletions); local `gpgsig` header
  present. See deviation 7 for 829.
- Commit 4: `55390757dfaa325128df64c65cf4ecaa9cad5272`, this section, the Status line and four
  other documents (588 insertions, 65 deletions); local `gpgsig` header present.
- Commit 5: this commit, the review fix (deviation 10).

Against the base: 1 path after commit 1, 320 after commit 2, 322 after commit 3, and Section C7's
327 after commit 4 and after commit 5. No `.png` blob changes; `gradle/libs.versions.toml`,
`settings.gradle.kts` and `build-logic/` are unchanged; the only build files are the module's and
`app/common`'s.

### C17.2 Entry gate (Section C10.4)

- The base contains `39be1b4ea`, and `git diff 39be1b4ea 6d88cedcd` touches one path, this
  document.
- The latest `Android CI/CD - Unified Build and Tests` push run on dev: `40350332440`, head
  `39be1b4ea`, success.
- Xcode 27.0 (27A266a), first launch complete (`xcodebuild -checkFirstLaunchStatus` exit 0);
  `xcrun simctl list runtimes` lists iOS 18.0, 18.2, 18.6 and 26.5.
- Census: one script read the base's git objects. Comments and string literals were blanked first,
  and every call was read from its name to its closing parenthesis, across lines; the lambda items
  imported the topology gate's own functions. The output was byte-identical under all five Pythons
  (sha256 `636d756869beaf8d…`): 30 items equal and 3 different, all three in Section C2.6's prose
  (deviation 1).

| Item (method) | Measured | Verdict |
| --- | --- | --- |
| C2.1 module paths (`git ls-tree -r`); `src/main` Kotlin files; catalogs | 157 paths; 63 Kotlin files, the 14 changed, the reader and 48 others; the 2 catalogs | equal |
| C2.1 the feature's `R`: importing files; `R.string.` / `R.plurals.` references (blanked, not after a word character or `.`) | 11 files, 101 references (`CommonHandler` 30, `LiveWorkoutScreen` 17, `ErrorTypeMapper` 13, `SessionSheets` 11, `FinishConfirmDialog` 10, `DeleteExerciseCopyMapper` 7, `LiveExerciseCard` 7, `ExercisePickerHandler` 3, `DialogClickHandler`, `LiveWorkoutGraph` and `LiveSetRow` 1 each) | equal |
| C2.1 `android.*`, `androidx.annotation`, `androidx.activity` imports | `android.content.Context` (the reader), `android.annotation.SuppressLint` (the screen); `StringRes` in the reader, `DeleteExerciseCopyMapper` and `ErrorTypeMapper`, `VisibleForTesting` in `LiveWorkoutStoreImpl`; `androidx.activity.compose.BackHandler` in `ui/LiveWorkoutGraph.kt` | equal |
| C2.1 the app `Context`: `LiveWorkoutFeature`'s `LocalContext` and `appDeps<LiveWorkoutGraph.Factory>()`; `context.getString(` | both present; one call, the reader's `context.getString(id)` | equal |
| C2.1 copy reads (`<receiver>.<member>(` read to its `)`) | `copyReader.read` 30 (`CommonHandler`); `resourceWrapper.getString` 7 (`ClickHandler` 1, `DialogClickHandler` 2, `ExercisePickerHandler` 4); `stringResolver.getString` 1 (`CommonHandler`); `context.getString` 1 (the reader) | equal |
| C2.1 composition reads (`stringResource(` and `pluralStringResource(`; CMP when the first argument is `Res.*`) | 50 in 6 files: 45 `stringResource` and 2 `pluralStringResource` on Android `R`, 3 `stringResource` on kit's `Res` (`LiveSetRow`) | equal |
| C2.1 State lambdas (Section B2.2's method, the gate's `braced_call_bodies`, side-effect tokens, resolver regex and `copy_resolving_functions`) | 11 handler files, 69 module copy-resolving functions: 61 lambdas, 11 without `.copy(`, 0, 0, 0, 0, 0 | equal |
| C2.1 the eleven reach `setMutator` by `.` or `::` | `ClickHandler` 8, `CommonHandler` 2 (one by `::`), `DialogClickHandler` 1; those three declare `val setMutator: LiveSetMutator` | equal |
| C2.2 `@Test` by innermost enclosing class, the 18 portable suites | 225: interactor 29, click handler 26, dialog click handler 29, external writes 24, set mutator 27, mapper 20, picker handler 12, draft behaviour 10, visible-rows resolver 8, status mapper 7, external-write coverage 6, unfilled set 6, mapper reload 5, common handler 3, navigation handler 4, delete-copy mapper 4, route argument 4, input handler 1 | equal |
| C2.2 the retired classes | 7 in three files: `CommonHandlerComposeResourceTest` 2 and `…RuTest` 2, `LiveWorkoutTemplateParityTest` 1 and `…RuTest` 1, `LiveSetRowSemanticsTest` 1 | equal |
| C2.2 / C2.3 the other `@Test` classes; Part B's two shared test files | `ExerciseDoneRuleTest` 7, the two gates 1 each; `FakeExercisePicker.kt` and `LiveWorkoutCopyFixtures.kt` (`NAMED_COPY`), no `@Test` | equal |
| C2.2 the module's names (`@Test` plus `GOLDEN_METHODS` in `src/test`; `@Test` in `src/androidTest`) | 241 + 30 = 271; 6; 277 | equal |
| C2.3 the four golden classes' methods, in source order (the gate's `GOLDEN_METHODS`) | header 4, screen 1, sheets 7, state 18 | equal |
| C2.3 the PNGs (`git ls-tree`); blob set (sha256 of the sorted blob ids, each followed by a newline) | 60, one per method and theme; `53221eab…` | equal |
| C2.3 `ExerciseDoneRuleTest`'s fixture; the golden gate's plain-run filter | `WorkoutTargetParityFixture`; `*.golden.*` only | equal |
| C2.3 the device class | `@Smoke` once; its six names in order | equal |
| C2.4 catalogs (XML elements, lines, name order, blob sha256) | 94 and 2 each; 143 and 128 lines; keys and order equal; `e201fa09…`, `f4b9ad01…` | equal |
| C2.4 P1 (`%(?!\d+\$)` per line, its `<plurals>` / `<item>` context) | EN lines 83, 84, 88, 89 and RU 71–74, 78–81, each an `<item quantity=…>` of `feature_live_workout_finish_new_pr_title` or `…_finish_unfilled_discarded` with `%d`; exit sha256 `1f4f87cb…` and `4e83b59e…` | equal |
| C2.4 / C10.3 the P1-applied catalogs under the gate's own `catalog_values` and `placeholder_tokens` | EN 36 and RU 40 placeholder tokens, none non-positional | equal |
| C2.5 the build file (plugin aliases, `includeJavax`, every line inside `dependencies { }`) | `convention.composeLibrary`, `metro`, `paparazzi`; present; the 15 lines | equal |
| C2.6 `app/common`'s feature edges by configuration; the root flow | `implementation` edges: app-dialogs (`:impl`), live-workout, recovery and settings; `liveWorkoutGraph(modifier = …)`; no factory in `App.kt`, `AppRootDeps.kt` or `AppGraph.kt`; the identity test's 7 through `asContribution` | different: deviation 1 |
| C2.6 lint-rules' `PERMITTED_READERS` and its test; the gate's `EXPECTED_APP_DEPS_READERS` | both name `src/main`; app-dialogs, live-workout and settings | equal |
| C2.6 / C9.4 the five journeys by name; the four app classes' `@Test` counts | the five; 15 + 3 + 3 + 4 = 25 | equal |
| C2.6 paths outside the module and `documentation/` that call `liveWorkoutGraph(` or name `feature/live-workout/src/` (`git grep -l`) | the topology script, `AppNavigationHost.kt`, the two lint-rules files, `graphify-out/graph.json` and `graphify-out/manifest.json` | different: deviation 1 |
| C2.6 `graph.json`'s regeneration date (`built_at_commit` and that commit's author date) | `ab2a1b859`, 2026-09-01 | different: deviation 1 |
| C2.7 suppressions (`@Suppress`, `@SuppressLint`, `@file:Suppress`, blanked) | production 12 in 10 files, tests 7 in 5, the device class none, each as listed | equal |
| C2.8 the merge `39be1b4ea` (`rev-parse ^{tree}`, `merge-tree --write-tree`, first-parent diff, ancestry) | tree equal to `43a12bdd5`'s and to the merge-tree of its parents; 39 paths; an ancestor of the base | equal |
| C2.8 the topology script's blob sha256; BASE_GATE_OUTPUT | `679d6e9f…`; 833 lines, `37315e70…` | equal |
| C2.8 the oracle's modules; the workflow's Native and link lines | 15; 15 and 15, `$linked/15` | equal |
| C2.8 `ComposeStringResolver`'s two delegations; CMP's catalog version | the no-argument path returns `composeGetString(resource)`; 1.11.1 | equal |
| C2.8 portable names against the characters Kotlin/Native rejects | 225 names, none; the two with `—` (`ClickHandlerTest`, `LiveWorkoutMapperTest`) | equal |
| C2.8 `isReturnDefaultValues` in `build-logic/`, the module and `gradle/` | absent | equal |

The exit census (Section C12) on the base read the base's git objects and exited 1 with all twelve
items equal to the base column; its output was byte-identical under all five Pythons.

Baselines on the base:

| Gate | Executed | Fresh XML |
| --- | --- | --- |
| `assert_kmp_ui_source_topology.py`, five Pythons | — | 833 lines, exit 0, empty stderr, one sha256 `37315e70…` (BASE_GATE_OUTPUT) |
| `assert_mvi_source_topology.py` | — | exit 0, 5 lines, `da7863ca…` (BASE_MVI_TOPOLOGY) |
| `assembleDebug lintDebug testDebugUnitTest --continue` | 2808/2808 | 34 module/task rows, 479 suites, 3,391 cases, 0 failures (HOST_BASELINE; live-workout 26 suites, 241 cases); lint 80 findings in each of app:app, app:dev and app:store, none in the other 35 modules (LINT_BASELINE) |
| `verifyPaparazziDebug` | 675/675 | 13 rows, 174 suites, 1,787 cases, 0 failures (live-workout 30 and 301; `Visual gate live: 60 golden test case(s) executed for 60 golden image(s).`); porcelain empty |
| `:lint-rules:test` | 9/9 | 16 suites, 151 cases |
| `:app:app:testDebugUnitTest --tests '*LiveWorkoutExtensionIdentityTest*'` | 595/595 | 7 cases |
| fifteen-module `iosSimulatorArm64Test --continue` + oracle | 410/410 | 96 suites, 635 cases; oracle exit 0 (NATIVE_BASELINE) |
| fifteen-module `linkDebugTestIosArm64 --continue` | 395/395 | 15/15 fresh `test.kexe` |
| `:feature:live-workout:connectedDebugAndroidTest` on `emulator-5554` | 248/248 | 6 cases |
| the four app classes on `emulator-5554` | 756/756 | 4 suites, 25 cases |

### C17.3 Commit 1's proof and the per-commit gates (Sections C10.0 and C10.5)

Commit 1's proof (Section C10.0) ran on its committed tree with the base's script saved aside as
`old_gate.py` (sha256 `679d6e9f…`), under all five Pythons:

- GREEN: old and new outputs byte-identical, exit 0, empty stderr, 833 lines, `37315e70…`, equal to
  BASE_GATE_OUTPUT.
- M1: both RED and byte-identical, exit 1, with exactly `feature:exercise: updateState in
  src/commonMain/kotlin/io/github/stslex/workeeper/feature/exercise/ui/mvi/handler/ClickHandler.kt
  must return a State copy`.
- M2: both RED and byte-identical, exit 1, with exactly `feature:single-training: updateState in
  src/commonMain/kotlin/io/github/stslex/workeeper/feature/single_training/mvi/handler/ClickHandler.kt
  must return a State copy`.
- Q1: both GREEN.
- Each mutated file was restored to HEAD's blob and mode with an empty porcelain, and a fresh
  GREEN after Q1 equalled BASE_GATE_OUTPUT. The commit touches the topology script only.

| Commit | Gate | Executed | Result |
| --- | --- | --- | --- |
| 1 | `:feature:live-workout:testDebugUnitTest` (staged) | 212/212 | 26 suites, 241 cases, equal to HOST_BASELINE's row |
| 1 | detekt (staged; committed) | 63/63; 63/63 | 0 findings in 55 fresh reports, each |
| 2 | early Native check: `:feature:live-workout:iosSimulatorArm64Test`, before the scene (deviation 5) | 126/126 | 18 suites, 226 cases, every one passing; the names equal commonTest's, both `—` names among them |
| 2 | `testAndroidHostTest` and `iosSimulatorArm64Test` of the module (staged) | 303/303 | 21 suites, 235 cases; 19 suites, 227 cases |
| 2 | detekt (staged) | 63/63 | 0 findings in 55 fresh reports, after deviation 4's first run |
| 2 | `assert_kmp_ui_source_topology.py`, five Pythons | — | 989 lines, exit 0, empty stderr, one sha256 `32eb44ec…`, the prototype's |
| 2 | `assert_mvi_source_topology.py` | — | equal to BASE_MVI_TOPOLOGY |
| 2 | `:feature:live-workout:assembleDebug` | 344/344 | the Android AAR and both iOS klibs |
| 2 | `:feature:live-workout:testAndroidHostTest` | 214/214 | 21 suites, 235 cases |
| 2 | `:feature:live-workout:verifyPaparazziDebug` | 215/215 | 25 suites, 295 cases: 60 golden and 235; `Visual gate live: 60 golden test case(s) executed for 60 golden image(s).`; porcelain empty |
| 2 | `:feature:live-workout:iosSimulatorArm64Test` | 126/126 | 19 suites, 227 cases |
| 2 | `:app:common:assembleDebug` | 347/347 | built |
| 2 | `:app:dev:assembleDebug` | 691/691 | built; Metro validated the graph |
| 2 | `:app:app:testDebugUnitTest --tests '*LiveWorkoutExtensionIdentityTest*'` | 598/598 | 7 cases |
| 2 | `:lint-rules:test` | 9/9 | 16 suites, 151 cases |
| 2 | `:feature:live-workout:connectedDebugAndroidTest` on `emulator-5554` | 248/248 | 6 cases |
| 2 | the four app classes on `emulator-5554` | 759/759 | 4 suites, 25 cases, the five journeys among them |
| 2 | exit census (Section C12) | — | exit 0 (Section C17.5) |
| 2 | detekt (committed) | 63/63 | 0 findings in 55 fresh reports |
| 3 | `assert_kmp_ui_source_topology.py` (staged) | — | 990 lines, `7271a2b1…` |
| 3 | sixteen-module Native + oracle (staged) | 434/434 | 115 suites, 862 cases; the oracle's live-workout line exactly `feature:live-workout: 227 executed / 0 skipped / 0 failed / 0 errored — verified 227 exact identities` |
| 3 | sixteen-module link (staged) | 418/418 | 16/16 fresh `test.kexe` |
| 3 | detekt (staged) | 63/63 | 0 findings in 55 fresh reports |
| 3 | `assert_kmp_ui_source_topology.py`, five Pythons | — | 990 lines, exit 0, empty stderr, one sha256 `7271a2b1…`, the prototype's |
| 3 | `assert_mvi_source_topology.py` | — | equal to BASE_MVI_TOPOLOGY |
| 3 | sixteen-module `iosSimulatorArm64Test --continue` + oracle | 434/434 | 115 suites, 862 cases; oracle exit 0, its output NATIVE_BASELINE's plus exactly the live-workout line above |
| 3 | sixteen-module `linkDebugTestIosArm64 --continue` | 418/418 | `16/16 linked` |
| 3 | detekt (committed) | 63/63 | 0 findings in 55 fresh reports |

On commit 2's tree the PNG manifest proof held: the 60 blobs at their `androidHostTest` paths equal
the base's at `src/test`, blob for blob, and the blob set is `53221eab…`.

### C17.4 Known-negative controls (Section C11)

Every control ran on commit 3's committed tree. The Python-scored rows ran the topology script under
`python3` against the working tree, each on a scratchpad copy of its file (or files), restored with
`cp` to HEAD's blob and mode with an empty porcelain and no untracked file left, then a fresh GREEN
byte-identical to commit 3's output (990 lines, `7271a2b1…`). Each RED exited 1 and printed exactly
the lines quoted below on stderr, no more and no fewer. A line is quoted with its
`feature:live-workout: ` prefix dropped, the module's `src/commonMain/kotlin/<pkg>/` written `…/`,
another source set's `src/<set>/kotlin/<pkg>/` written `src/<set>/…/`, the catalogs'
`feature/live-workout/src/commonMain/composeResources/` written `composeResources/`, and a line over
300 characters cut and marked `…`. The mutations are Section C11's rows.

| Id | Measured RED (exit 1), every line quoted |
| --- | --- |
| K1 | `source manifest mismatch; missing=['…/ui/components/LiveWorkoutHeader.kt'], extra=[]`; `…/ui/components/LiveWorkoutHeader.kt must hold bare @Preview annotations but is missing` |
| K2 | `source manifest mismatch; missing=[], extra=['src/main/…/di/LiveWorkoutScope.kt']`; `legacy source set src/main still contains ['src/main/…/di/LiveWorkoutScope.kt']`; `unexpected Kotlin-bearing source set src/main: ['src/main/…/di/LiveWorkoutScope.kt']` |
| K3 | `source manifest mismatch; missing=['…/ui/components/LiveWorkoutHeader.kt'], extra=['…/ui/LiveWorkoutHeader.kt']`; `…/ui/components/LiveWorkoutHeader.kt must hold bare @Preview annotations but is missing`; `…/ui/LiveWorkoutHeader.kt must hold exactly 0 bare @Preview annotation(s); found 3` |
| K4 | `…/ui/components/SessionSheets.kt imports a platform API in commonMain: 'import android.'` |
| K5 | `exact private CMP catalog mismatch in composeResources/values/strings.xml; mismatched=["feature_live_workout_sheet_close: expected='Close', actual=None"], unexpected=['feature_live_workout_sheet_close_renamed'], order=drifted`; `resource ownership drift for feature_live_workout_sheet_close; expected=['composeResources/values-ru/strings.xml', 'composeResources/values/strings.xml'], actual=['composeResources/values-ru/strings.xml']`; `resource ownership drift for feature_live_workout_sheet_close_renamed; expected=['composeResources/values-ru/strings.xml', 'composeResources/values/strings.xml'], actual=['composeResources/values/strings.xml']` |
| K6 | `exact private CMP catalog mismatch in composeResources/values-ru/strings.xml; mismatched=["feature_live_workout_sheet_close: expected='Закрыть', actual=('string', 'feature_live_workout_sheet_close', 'Закрыть окно')"], unexpected=[], order=exact` |
| K7 | `exact private CMP catalog mismatch in composeResources/values/strings.xml; mismatched=["feature_live_workout_sheet_close: expected='Close', actual=None"], unexpected=[], order=drifted`; `resource ownership drift for feature_live_workout_sheet_close; expected=['composeResources/values-ru/strings.xml', 'composeResources/values/strings.xml'], actual=['composeResources/values-ru/strings.xml', 'feature/settings/src/main/res/values/strings.xml']` |
| K8 | `Store State must stay semantic with exactly (('sessionUuid', 'String?'), ('trainingUuid', 'String?'), ('trainingName', 'String'), ('trainingNameLabel', 'String'), ('trainingNameDraft', 'String'), ('isTrainingNameEditing', 'Boolean'), ('isAdhoc', 'Boolean'), ('startedAt', 'Long'), ('nowMillis', …` |
| K9 | `…/mvi/handler/CommonHandler.kt resolves live-workout copy through ResourceWrapper.getString; use the private Compose resources`; `…/mvi/handler/CommonHandler.kt copy contract requires 'stringResolver.getString(' exactly 31 times; found 30`; `…/mvi/handler/CommonHandler.kt reads copy outside the StringResolver: 'getString(' 31 time(s), 30 through stringResolver` |
| K10 | `updateState in …/mvi/handler/CommonHandler.kt resolves a resource inside the State lambda: 'getString('`; `updateState in …/mvi/handler/CommonHandler.kt resolves a resource inside the State lambda: 'Res.'`; `…/mvi/handler/CommonHandler.kt copy contract requires 'stringResolver.getString(' exactly 31 times; found 32` |
| K11 | `…/ui/components/LiveWorkoutHeader.kt contains forbidden platform/lookup API 'android.content'`; `…/ui/components/LiveWorkoutHeader.kt contains forbidden platform/lookup API 'uiMode'`; `exactly six portable previews are required; found 7`; `…/ui/components/LiveWorkoutHeader.kt must hold exactly 3 bare @Preview annotation(s); found 2` |
| K12 | `remaining Context.appDeps readers are not the exact 2 unported entries; expected={PosixPath('feature/app-dialogs/impl/src/main/kotlin/io/github/stslex/workeeper/feature/app_dialogs/impl/di/AppDialogFeature.kt'): ['AppDialogGraph.Factory'], …`; `…/di/LiveWorkoutFeature.kt contains forbidden platform/lookup API 'LocalContext'`; `…/di/LiveWorkoutFeature.kt contains forbidden platform/lookup API 'appDeps<'`; `factory invocation must occur exactly inside retained Store creation` |
| K13 | `exact root-factory flow requires 'val liveWorkoutGraphFactory: LiveWorkoutGraph.Factory' once in app/common/src/main/kotlin/io/github/stslex/workeeper/app/common/di/AppRootDeps.kt` |
| K14 | `factory invocation must occur exactly inside retained Store creation` |
| K17 | `exact root-factory flow requires 'private fun AppGraph.liveWorkout(sessionUuid: String? = null, trainingUuid: String? = null,): LiveWorkoutGraph = liveWorkoutGraphFactory.createLiveWorkoutGraph(Screen.LiveWorkout(sessionUuid = sessionUuid, trainingUuid = trainingUuid),)' once in …`; `extension identities must not bypass AppRootDeps via asContribution` |
| K18 | `updateState in …/mvi/handler/ClickHandler.kt must return a State copy`; `updateState in …/mvi/handler/ClickHandler.kt must return a State copy`; `updateState in …/mvi/handler/ClickHandler.kt must return a State copy`; `updateState in …/mvi/handler/ClickHandler.kt must return a State copy`; `updateState in …/mvi/handler/ClickHandler.kt must return a State copy`; `updateState in …/mvi/handler/ClickHandler.kt must return a State copy`; `updateState in …/mvi/handler/ClickHandler.kt must return a State copy`; `updateState in …/mvi/handler/ClickHandler.kt must return a State copy`; `updateState in …/mvi/handler/CommonHandler.kt must return a State copy`; `updateState in …/mvi/handler/CommonHandler.kt must return a State copy`; `updateState in …/mvi/handler/DialogClickHandler.kt must return a State copy` |
| K19 | `state mutator LiveSetMutator in …/mvi/mapper/LiveSetMutator.kt reaches StateStatusMapper, which state_mutators does not list` |
| K20 | `state mutator LiveSetMutator in …/mvi/mapper/LiveSetMutator.kt reaches LiveWorkoutMapper, which state_mutators does not list`; `state mutator StateStatusMapper in …/mvi/mapper/StateStatusMapper.kt reaches LiveWorkoutMapper, which state_mutators does not list` |
| K21 | `state mutator LiveSetMutator in …/mvi/mapper/LiveSetMutator.kt reaches LiveSetRowsResolver, which state_mutators does not list`; `state mutator LiveWorkoutMapper in …/mvi/mapper/LiveWorkoutMapper.kt reaches LiveSetRowsResolver, which state_mutators does not list` |
| K22 | `state mutator LiveSetMutator in …/mvi/mapper/LiveSetMutator.kt reaches LiveWorkoutDomainMapper, which state_mutators does not list`; `state mutator LiveWorkoutMapper in …/mvi/mapper/LiveWorkoutMapper.kt reaches LiveWorkoutDomainMapper, which state_mutators does not list` |
| K23 | `state mutator StateStatusMapper in …/mvi/mapper/StateStatusMapper.kt reaches ExerciseDoneRule, which state_mutators does not list`; `state mutator LiveWorkoutMapper in …/mvi/mapper/LiveWorkoutMapper.kt reaches ExerciseDoneRule, which state_mutators does not list` |
| K24 | `state mutator LiveSetMutator in …/mvi/mapper/LiveSetMutator.kt holds 'Res.'; a State lambda returns only through pure code` |
| K25 | `state mutator StateStatusMapper in …/mvi/mapper/StateStatusMapper.kt names 'stringResolver'` |
| K26 | `state mutator LiveWorkoutMapper in …/mvi/mapper/LiveWorkoutMapper.kt takes StringResolver` |
| K27 | `state mutator LiveWorkoutMapper in …/mvi/mapper/LiveWorkoutMapper.kt reaches RailMapper, which state_mutators does not list` |
| K28 | `state mutator LiveSetMutator must be declared once in commonMain; found 2` |
| K29 | `updateState in …/mvi/handler/ClickHandler.kt must return a State copy` |
| K30 | `…/mvi/handler/CommonHandler.kt copy contract requires 'liveCopy = resolveCopy(),' exactly once; found 0` |
| K31 | `…/mvi/handler/CommonHandler.kt copy contract requires 'LiveWorkoutCopyReader' exactly zero times; found 1`; `…/mvi/handler/CommonHandler.kt still names the Part B reader (SM0)` |
| K32 | `…/mvi/handler/ClickHandler.kt imports CMP's top-level getString; read copy outside composition through the injected StringResolver` |
| K33 | `…/ui/LiveWorkoutGraph.kt reads copy outside composition under ui/: 'getString('` |
| K34 | `…/mvi/handler/ExercisePickerHandler.kt must resolve the added toast before its write` |
| K35 | `…/mvi/handler/DialogClickHandler.kt copy contract requires 'stringResolver.getString(' exactly two times; found 1` |
| K36 | `…/mvi/handler/ExercisePickerHandler.kt copy contract requires 'private suspend fun sendError(type: ErrorType)' exactly once; found 0` |
| K37 | `…/mvi/handler/CommonHandler.kt copy contract requires 'System.currentTimeMillis' exactly zero times; found 1` |
| K38 | `…/mvi/model/LiveWorkoutCopy.kt LiveWorkoutCopy parameters must be exactly ['trainingNamePlaceholder: String', 'statusSkipped: String', 'statusNoPlan: String', 'repsUnit: String', 'metaTemplate: String', 'metaSkippedTemplate: String', 'resetTitle: String', 'resetBody: String', 'resetConfirm: …`; `…/mvi/model/LiveWorkoutCopy.kt copy contract requires 'errorFinishFailed = "",' exactly once; found 0` |
| K39 | `…/domain/LiveWorkoutInteractorImpl.kt must not read core:data:database; only the domain mapper maps its data models` |
| K40 | `src/commonTest/…/domain/LiveWorkoutInteractorImplTest.kt may name core:data:database only as ['core.data.database.sets.PlanSetDataModel', 'core.data.database.sets.SetTypeDataModel']; found ['core.data.database.exercise.ExerciseEntity', 'core.data.database.sets.PlanSetDataModel', …` |
| K41 | `:core:data:database-test must be exactly one androidHostTestImplementation edge` |
| K42 | `src/commonTest/…/mvi/handler/CommonHandlerTest.kt names java.* in shared code` |
| K43 | `…/domain/mapper/LiveWorkoutDomainMapper.kt must be the module's one production reader of core:data:database` |
| K44 | `exact test identities drifted in src/commonTest/…/mvi/handler/NavigationHandlerTest.kt; missing=[], unexpected=['a two-hundred-and-twenty-seventh identity nobody declared']` |
| K45 | `exact test identities drifted in src/iosTest/…/LiveWorkoutFeatureSceneIosTest.kt; missing=['resourcesScreenSheetsDialogsAndActionsRenderAndDispatch'], unexpected=['renamedSceneSubstitute']` |
| K46 | `all seven extension identities must reach the graph through the accessor` |
| K47 | `exact root-factory flow requires 'liveWorkoutGraph(factory = liveWorkoutGraphFactory,' once in app/common/src/main/kotlin/io/github/stslex/workeeper/host/AppNavigationHost.kt` |
| K48 | `exact root-factory flow requires 'navComponentScreenWithResults(LiveWorkoutFeature(factory))' once in feature/live-workout/…/ui/LiveWorkoutGraph.kt` |
| K49 | `golden methods in src/androidHostTest/…/golden/SessionHeaderGoldenTest.kt must be exactly ['topbar', 'sheadDefault', 'sheadSkippedMeta', 'sheadEditing']; actual=['topbarProbe', 'sheadDefault', 'sheadSkippedMeta', 'sheadEditing']` |
| K50 | `source manifest mismatch; missing=[], extra=['src/androidHostTest/…/golden/SessionScreenCopyGoldenTest.kt']`; `suppressions must be exactly the nine inherited production and zero Native test-name annotations; expected={'src/androidHostTest/…/golden/SessionScreenGoldenTest.kt': ['"LongMethod"'], '…/di/LiveWorkoutFeature.kt': ['"UNCHECKED_CAST"'], '…/domain/LiveWorkoutInteractor.kt': ['"TooManyFunctions"'], …`; `src/androidHostTest/…/golden/SessionScreenCopyGoldenTest.kt renders goldens but is neither golden_test nor in extra_golden_tests` |
| K51 | `exact private CMP catalog mismatch in composeResources/values/strings.xml; mismatched=["feature_live_workout_meta_format: expected='%1$d of %2$d exercises · %3$d of %4$d sets', actual=('string', 'feature_live_workout_meta_format', '%d of %2$d exercises · %3$d of %4$d sets')"], unexpected=[], …`; `compose-resource placeholder is not %N$d or %N$s with N >= 1: composeResources/values/strings.xml key feature_live_workout_meta_format token '%d'` |
| K52 | `device placeholder must keep 'fun finish_button_dispatches_OnFinishClick()' exactly once` |
| K53 | `suppressions must be exactly the nine inherited production and zero Native test-name annotations; expected={'src/androidHostTest/…/golden/SessionScreenGoldenTest.kt': ['"LongMethod"'], '…/di/LiveWorkoutFeature.kt': ['"UNCHECKED_CAST"'], '…/domain/LiveWorkoutInteractor.kt': ['"TooManyFunctions"'], …` |
| K54 | `app:common: live-workout edge must be exactly one api dependency`; `app:common: live-workout edge must not remain implementation` |
| K55 | `…/mvi/store/PendingUndo.kt carries a resource handle/lookup 'StringResource' in a Store/domain payload`; `…/mvi/store/PendingUndo.kt carries a resource handle/lookup 'StringRes' in a Store/domain payload` |
| K62 (topology half) | `exact private CMP catalog mismatch in composeResources/values/strings.xml; mismatched=["feature_live_workout_finish: expected='Finish session', actual=('string', 'feature_live_workout_finish', 'Finish session.')"], unexpected=[], order=exact` |
| K63 (topology half) | `exact private CMP catalog mismatch in composeResources/values-ru/strings.xml; mismatched=["feature_live_workout_setbar_add: expected='+ подход', actual=('string', 'feature_live_workout_setbar_add', '+ подход!')"], unexpected=[], order=exact` |

K56's topology half (the D8 mutation alone, Section C11's "topology GREEN") exited 0 with output
byte-identical to commit 3's.

The Gradle- and device-scored rows ran through `mutation_harness.py` one-shot under `python3`, each
with `--no-configuration-cache --console=plain` inside `--task`: a fresh GREEN, the harness's
mutation, run and restoration, the fresh XML (or report) of every result directory the task writes
copied aside and scored by identity, the restored file's blob and mode equal to HEAD's with an empty
porcelain, then a fresh GREEN that was also the next control's. Every GREEN passed in full: TH
(`testAndroidHostTest` and `iosSimulatorArm64Test` of the module) 303/303 tasks, 235 host and 227
Native cases; TP (`verifyPaparazziDebug`) 215/215, 295 cases with `Visual gate live: 60 …`; the
sixteen-module Native task 434/434, live-workout's 227 cases, the oracle exit 0; `:lint-rules:test`
9/9, 151 cases; root detekt 63/63, 0 findings in 55 fresh reports.

| Id | Task | Measured RED, quoted |
| --- | --- | --- |
| K30 | TH | host and Native: `CommonHandlerTest`'s `a load resolves every copy field from its own catalog key`, and nothing else; the expected `NAMED_COPY` against a copy whose 31 fields are all `""` |
| K56 | TH | host and Native: that identity only; the one differing field is `resetTitle=resetTitle` expected, `resetTitle=cancelTitle` found |
| K57 | TH | Native: the scene only, at its first node assertion, `Failed to assert the following: (ContentDescription = [Back]) … Expected exactly '1' node but could not find any node that satisfies: (TestTag = 'LiveWorkoutBackButton')`; host 235 and the 226 portable Native cases pass |
| K58 | TH | Native: the scene only, at step 10: the expected action list begins `[OnBackClick, OnSessionMenuClick, …`, the dispatched one `[OnSessionMenuClick, …` |
| K62 | TH | Native: the scene only, at step 1: the catalog list differs at `Finish session` → `Finish session.` |
| K62 | TP | `SessionScreenGoldenTest.sessionScreen`, both themes, and only it: `Images differ (by 0.132060%)` against `…_sessionScreen_light.png` and `(by 0.126296%)` against `…_dark.png`; the other 293 cases pass |
| K63 | TP | `SessionStateGoldenTest.exerciseBodyweightRu`, both themes, and only it: `Images differ (by 0.087256%)` (light) and `(by 0.063002%)` (dark); the other 293 cases pass |
| K65 | TH | none: GREEN, host 235/235 and Native 227/227 (deviation 3) |
| K59 | sixteen-module Native, then the oracle | Gradle GREEN, 434/434, live-workout's 227 cases passing with `renamedSceneSubstitute` among them; the oracle exit 1: `feature:live-workout: expected exactly one testcase with classname='io.github.stslex.workeeper.feature.live_workout.LiveWorkoutFeatureSceneIosTest' name='resourcesScreenSheetsDialogsAndActionsRenderAndDispatch[iosSimulatorArm64]', found 0; parsed 227 testcase(s): ['io.github.stslex.workeeper.feature.live_workout.LiveWorkoutFeatureSceneIosTest.renamedSceneSubstitute[iosSimulatorArm64]', …`, the other fifteen modules `ok` |
| K61 | root detekt alone | 6 findings, all on line 20 of `src/commonTest/…/mvi/handler/NavigationHandlerTest.kt`: `MaxLineLength`, `MaximumLineLength` (`Exceeded max line length (120)`) and four `ArgumentListWrapping` |
| K64 | `:lint-rules:test`, then root detekt alone | lint-rules: `the permitted reader passes` (`The active exercise is the one permitted reader. ==> expected: <0> but was: <1>`) and `a second call inside the permitted reader is flagged` (`expected: <1> but was: <2>`); the other 149 cases pass (deviation 8). detekt: one finding, `ActiveSurfaceSingleReaderRule` on `…/ui/components/LiveExerciseCard.kt` line 96 |
| K15 | device | `UiAdmissionRaceTest`: `admittedGeneration_composesTheRegion_andResolvesItsDependencies` only: `an admitted region resolves the generation's app-scope deps expected:<1> but was:<2>` |
| K16 | device | `UiAdmissionRaceTest`: `retiredGeneration_composesNothing_andResolvesNothing` (`a retired generation must resolve NOTHING — not even the app root deps expected:<0> but was:<1>`) and `retirementBetweenPublicationAndFrame_resolvesNothing` (`the stale generation's region must resolve NOTHING expected:<0> but was:<1>`) only |

K15 and K16 ran on `emulator-5554`, cold-booted for them; each GREEN executed 759/759 tasks with the
class's 3 cases passing, and `App.kt` was also put back from its scratchpad copy with `cp`. The
device state was the same before and after.

The PNG and oracle rows:

- **K60.** With the last byte of `…golden_SessionScreenGoldenTest_sessionScreen_light.png` flipped
  on MAIN's tree, the PNG assertion failed with all three named drifts: the blob (HEAD `009f511e…`,
  working `c82bd9cd…`), the blob set (`259825c9…` against `53221eab…`) and the manifest (base
  `009f511e…` at `src/test/snapshots/images/`, working `c82bd9cd…` at
  `src/androidHostTest/snapshots/images/`). Restored with `cp`, the file's blob and mode equalled
  HEAD's, the porcelain was empty, and the proof held again.
- **K66 and K67** ran the oracle from MAIN's `.github/scripts/` with a scratchpad mirror of commit
  3's sixteen fresh result directories as its working directory. Unmutated: exit 0, live-workout's
  line `227 executed / 0 skipped / 0 failed / 0 errored — verified 227 exact identities`. K66 (the
  scene's `<testcase>` deleted and its suite's `tests="0"`; deviation 6): exit 1,
  `feature:live-workout: expected exactly one testcase with classname='io.github.stslex.workeeper.feature.live_workout.LiveWorkoutFeatureSceneIosTest'
  name='resourcesScreenSheetsDialogsAndActionsRenderAndDispatch[iosSimulatorArm64]', found 0; parsed
  226 testcase(s): …`. K67: exit 1, `feature:live-workout: <testsuite
  name='iosSimulatorArm64Test.io.github.stslex.workeeper.feature.live_workout.LiveWorkoutFeatureSceneIosTest'>
  in TEST-iosSimulatorArm64T-R6OAPPG318BDG.io.github.stslex.workeeper.feature.live_workout.LiveWorkoutFeatureSceneIosTest.xml
  reports skipped=1, expected 0`. In both, the other fifteen modules' lines stayed `ok`.

Every new or changed identity has a RED: D8's (K30, K56), the scene (K45, K57, K58, K59, K62,
K66, K67), the seven extension identities (K17, K46), the device class (K52), the goldens (K49,
K50, K60, K62, K63).

### C17.5 Exit census (Section C12)

One script read a revision's git objects (or, for Y1–Y3, the working tree), with Section C0.2's
method; X5 imported the topology gate's own functions. Its output was byte-identical under all five
Pythons on each revision.

- **The base** (`6d88cedcd`): exit 1; all twelve items equal to the base column (X5, X6 and X10 hold
  at exit too).
- **Commit 2** (`3e3c916a7`): exit 0, 12 of 12.
- **Commit 3** (`c4cfb1642`): exit 0, 12 of 12, its output equal to commit 2's line for line except
  the revision (sha256 `0a57597a…` under every Python).

| Id | Input at commit 3 | Measured at commits 2 and 3 | At the base |
| --- | --- | --- | --- |
| X1 | 91 module Kotlin files | none; 0 | `…/mvi/handler/LiveWorkoutCopyReader.kt`, `…/mvi/mapper/LiveWorkoutTemplateParityTest.kt`; 3 |
| X2 | 1,604 tracked Kotlin files (1,607 at the base) | none | 5: the reader, `CommonHandler`, `CommonHandlerComposeResourceTest`, `CommonHandlerExternalWritesTest`, `CommonHandlerTest` |
| X3 | 91 | none | 7: `ClickHandler`, `DialogClickHandler`, `ExercisePickerHandler` and four suites |
| X4 | 62 production files | `ClickHandler` 1, `CommonHandler` 31, `DialogClickHandler` 2, `ExercisePickerHandler` 4 | `CommonHandler` 1 |
| X5 | 10 handler files, 77 module copy-resolving functions | 61, 11, 0, 0, 0, 0, 0 | the same (11 handler files, 69 functions) |
| X6 | 11 lambdas without `.copy(` | `ClickHandler` 8, `CommonHandler` 2, `DialogClickHandler` 1 | the same, under `src/main` |
| X7 | 21 commonTest and iosTest files | 21; none of the six tokens; `runBlocking` in the scene only | 0 |
| X8 | 91 | 0, 0 | 128, 14 |
| X9 | the 2 catalogs | 94 and 2 in each; EN 4 and RU 8 changed lines, every one an `<item quantity=` line whose only change is `%d` → `%1$d` | 94 and 2 |
| X10 | 60 PNGs | 60, `53221eab…` | the same |
| X11 | 154 module paths (157 at the base) | none | all three |
| X12 | 253 app Kotlin files; the repository's | `App.kt` 2, `AppGraph.kt` 1, `AppNavigationHost.kt` 2, `AppRootDeps.kt` 1, `LiveWorkoutExtensionIdentityTest.kt` 1; 0; 0 | none; 1; 1 |

The census controls ran on commit 3's committed tree, each on a scratchpad copy of its file,
restored with `cp`, the restored file's blob and mode equal to HEAD's and the porcelain empty, then
the restored tree's census exited 0:

| Id | Mutation | Measured |
| --- | --- | --- |
| Y1 | `CommonHandler.kt`: `import android.content.Context` after the package line | exit 1, failing X1 only: `(['mvi/handler/CommonHandler.kt'], 0)` |
| Y2 | `ClickHandler.kt`: append the `probe` reading `Res.string.feature_live_workout_finish` | exit 1, failing X4 only: `ClickHandler` 2, the other three unchanged |
| Y3 | `ClickHandler.kt`: K29's mutation | exit 1, failing X5 only: `(61, 12, 0, 0, 0, 0, 0)`; X6 unchanged |

### C17.6 Repository, Native and device gates (Section C10.6), on commit 3

| Gate | Executed | Result |
| --- | --- | --- |
| `assembleDebug lintDebug testDebugUnitTest --continue` | 2841/2841 | 474 suites, 3,385 cases, 0 failures. 33 of 34 module/task rows name-identical to HOST_BASELINE; live-workout's row is now `testAndroidHostTest`, 21 suites and 235 cases where `testDebugUnitTest` had 26 and 241: the seven retired identities (Section C2.2) gone, D8's one added, the other 234 name-identical. No outcome changed |
| lint, inside that gate | — | every module's findings (id, path, line text) equal to LINT_BASELINE: 80 each in app:app, app:dev and app:store, none in the other 35. No finding names a Section C7 path |
| `detekt --continue` | 63/63 | 0 findings in 55 fresh reports |
| `assembleDebugAndroidTest` | 2171/2171 | built |
| `verifyPaparazziDebug` | 659/659 | 169 suites, 1,781 cases, 0 failures: 12 of 13 rows name-identical to the baseline, live-workout's 25 and 295 where it had 30 and 301 (the same seven out, D8's in), its 60 golden cases passing and `Visual gate live: 60 golden test case(s) executed for 60 golden image(s).`; porcelain empty; the PNG manifest proof holds and the blob set is `53221eab…` |
| `:lint-rules:test` | 9/9 | 16 suites, 151 cases |
| `:app:wear:testStoreReleaseUnitTest … --tests '*ReleaseRuntimeBoundaryTest*'` | 107/107 | 3 cases |
| `assert_kmp_ui_source_topology.py`, five Pythons | — | 990 lines, exit 0, empty stderr, one sha256 `7271a2b1…`, equal to Section C17.3's |
| `assert_mvi_source_topology.py` | — | equal to BASE_MVI_TOPOLOGY |
| `personal_data_gate.py -v` | — | PASS |
| sixteen-module Native + oracle | 434/434 | 115 suites, 862 cases; oracle exit 0, NATIVE_BASELINE's fifteen lines plus `feature:live-workout: 227 executed / 0 skipped / 0 failed / 0 errored — verified 227 exact identities` |
| sixteen-module link | 418/418 | `16/16 linked` |

On `emulator-5554` (API 34, animations off, cold-booted for these suites), each suite ran with
`ANDROID_SERIAL` set and its XML read fresh. Around each suite a logcat writer (`adb -s
emulator-5554 logcat -v threadtime`, after `logcat -c`) ran to a file and was stopped by its PID.

| Suite | Executed | Result |
| --- | --- | --- |
| Smoke, then `assert_mvi_device_identities.py` | 2131/2131 | 12 suites, 44 cases, the 3 documented `pendingFeatureRewrite` skips (archive, all-trainings, all-exercises), 0 failures; `core:ui:mvi (androidDeviceTest): 2 executed / 0 skipped / 0 failed / 0 errored — verified 2 exact identities` |
| Regression | 2131/2131 | 27 suites, 88 cases, 0 failures |

Smoke and Regression equal Part B's run (Section B13.6) name for name and outcome for outcome, and
so Part A's record (Section 14.6). The logcat sweep parsed 38,851 lines around Smoke and 24,278
around Regression and found no line of priority `E` whose tag starts with `SCREEN_`, so no
`SCREEN_LiveWorkout` error line. The same files hold `SCREEN_` lines at other priorities (16
`I/SCREEN_AppRootProbe` around Smoke; 1,549 around Regression, 121 of them
`I/SCREEN_LiveWorkout`) and 1,798 and 577 `E` lines of other tags, so the sweep could see one.
Before and after every suite the device state (the three animation scales, rotation, keyguard) and
the pre-installed dev app's `lastUpdateTime` were unchanged, and no other instrumentation ran on
the serial.

### C17.7 Environment

- Xcode 27.0 (27A266a) here, with iOS simulator runtimes 18.0, 18.2, 18.6 and 26.5; CI selects
  Xcode 26.6. JDK 21.0.11 (JBR).
- Pythons: 3.9.6 (`/usr/bin/python3`), 3.11.16, 3.13.16, 3.14.8 and 3.15.0 (`python3`). Controls
  ran under `python3` only.
- Gradle ran on a daemon registry private to this run, so another checkout's `--stop` could not
  reach it, and its own `--stop` reached no other daemon. Every sample of the host's Gradle
  processes, taken before and after each gate (126 in 63 gates), found only this run's.
- Emulator: `Pixel_6_API_34`, serial `emulator-5554` (API 34, `sdk_gphone64_arm64`), cold-booted
  five times with animations off (the entry check, the base's device gates, commit 2's, K15 and
  K16, and Section C17.6's suites), each shut down after its use; no other device was attached.
- Spotlight indexing was enabled on `/`; it was left as it was. Free disk stayed between 89.5
  and 105.7 GiB.
- The host slept: from 2026-10-27 20:35:52Z, during commit 3's committed detekt run, to 2026-10-28
  10:32:21Z it was on battery with the lid closed and entered sleep 67 times, with 64 short dark
  wakes (`pmset -g log`). Work in that window ran in the wake slices, so its durations include
  sleep: commit 3's detekt took 10m 39s, and the first four Python-scored controls 1–3 hours each.
  No verdict depends on a duration.

### C17.8 Deviations

1. **Section C2.6's prose (the maintainer's ruling: proceed and record).** Three entry-census items
   differ from it while every count Part C relies on holds: `app/common`'s `implementation`
   feature edges are four (app-dialogs' `:impl`, live-workout, recovery and settings), so
   live-workout's is not "the only feature edge besides settings' that is not `api`";
   `graphify-out/manifest.json`, as well as `graph.json`, names live-workout's source sets; and
   `graph.json`'s `built_at_commit` is `ab2a1b859`, dated 2026-09-01, not 2026-09-03. Both
   generated files stay as they are.
2. **The scene's steps 4 and 8 (the maintainer's ruling: use the measured values).** With Section
   C9.5's snapshot as described, `toFinishStats().unfilledSetCount` is 0 on Native, not 1 (the
   snapshot holds no visible row with 0 reps), so the finish dialog shows no unfilled-set line; and
   the delete dialog that the click path fills from the loaded state reads `0 of 3 exercises done ·
   1 sets logged` (the total counts the skipped exercise), not `0 of 2`. The scene asserts the
   measured values.
3. **K65 (the maintainer's ruling: record the measured result).** Without `Log.isLogging = false`,
   `CommonHandlerExternalWritesTest` stays GREEN on the plain Android host (235 of 235; Native 227
   of 227), and its 24 cases print the store's `consume: …` lines to stdout. Kermit 2.1.0's
   `LogcatWriter` wraps `android.util.Log` in `try`/`catch` and falls back to a `println` writer
   when the call throws, so the row's `… in android.util.Log not mocked` cannot occur. CM9's switch
   stays, as in core:ui:mvi's `StoreEventPressureTest`: it keeps the suite's output quiet and
   restores the global flag. CM9's stated reason, that `android.util.Log` throws, does not hold.
4. **The previews' arguments, and commit 2's first staged detekt run.** The first run on commit 2's
   staged tree reported 118 weighted issues: 111 `Indentation` (63 in the scene, 48 in the
   interactor suite), 5 `MagicNumber` in `LiveSetRow`'s previews, 1 `NoConsecutiveBlankLines` (the
   scene) and 1 `MemberNameEqualsClassName` (the interactor suite). All were fixed before the
   commit and the run repeated with 0 findings; no commit carried them. SM2 keeps "the base
   preview's body, unchanged", but the six bodies now sit in private functions without `@Preview`,
   which `MagicNumber`'s `ignoreAnnotated` no longer covers, so each `LiveSetUiModel(…)` takes named
   arguments (`ignoreNamedArgument`) rather than a suppression.
5. **The early Native check** ran before the scene, as Section C10.5 says, but after the seven app
   paths and the two lint-rules paths were applied, not before them. It compiles and runs the
   module alone, so they could not affect it.
6. **K66's literal mutation.** Deleting the scene's `<testcase>` alone leaves its suite declaring
   `tests=1` over no case, and the oracle reports that first: `feature:live-workout: inconsistent
   XML — <testsuite name='iosSimulatorArm64Test.io.github.stslex.workeeper.feature.live_workout.LiveWorkoutFeatureSceneIosTest'>
   in TEST-…LiveWorkoutFeatureSceneIosTest.xml declares tests=1 but contains 0 <testcase>
   element(s)`. Both were run: the literal deletion, and the deletion with the suite's `tests="0"`,
   which gives the row's line (Section C17.4).
7. **Commit 3's insertion count.** The topology entry is 829 inserted lines where Section C7.3
   counts the prototype's 833. The entry was generated from Section C10.1's data, every count
   cross-checked against it, and lays its lines out differently; the gate's output is the
   prototype's, 990 lines, `7271a2b1…`, under every Python.
8. **This run's scorers, and one harness label.** Four first verdicts were tooling errors, not gate
   behaviour, and were corrected before being counted. The TH scorer did not strip the `()` that
   JUnit appends to host names, so it missed D8's identity in K30 and K56; both were rescored from
   the fresh XML they had copied aside, without a re-run. The Paparazzi scorer keyed cases by class
   and name, and the golden cases are named `[1] LIGHT` and `[2] DARK` in every method, so it
   counted 243 of 295; the TP group was re-run after the fix. The oracle runner first read a
   missing directory and then ran the oracle with `python3 -I`, which hides its sibling
   `junit_identity` module; K66 and K67 were re-run on commit 3's fresh sixteen-module directories.
   K64's lint-rules half is scored from its fresh XML: `mutation_harness.py` labelled the run
   `INVALID — TASK OUTPUT REUSED`, because its reuse check matches `:lint-rules:test` as a substring
   of `> Task :lint-rules:testClasses UP-TO-DATE`, a lifecycle task with no actions that is up to
   date in every run, the GREENs included; the build executed 9 of 9 tasks, and the XML was written
   after the control's start.
9. **One more test run before each commit**, as Section C0.2 says: the module's tests ran on each
   staged tree, and the commit's gates then ran on the committed tree.
10. **A review fix (commit 5).** The PR's review found that `ExercisePickerHandler`'s add launch
    read the added toast after `interactor.addExerciseToActiveSession(…)`, so a read that throws
    once the add's row is committed sends the launch to `onError`: `AddExerciseFailed`, the sheet
    hidden, and a row in the database that State shows only after a reload. A probe identity, added
    through `mutation_harness.py` and removed by it, reproduced this on commit 4 (`the add wrote its
    row before the toast's read failed ==> expected: <0> but was: <1>`) and passed on commit 5's
    tree. Commit 5 moves the four-line read to the top of the launch body, above the add, and
    rewords its comment; nothing else in the function changes. CM3's order still holds: the toast
    resolves before both the add's write and the State write, and the gate's three anchors keep
    their order. Section C4.2's listing shows the pre-review placement. K34 moves the same four
    lines from their new place to directly before `// Undo removes the rows the add just wrote.`
    and gives its recorded line. The same commit replaces `CommonHandlerExternalWritesTest`'s KDoc
    reason with deviation 3's: the switch keeps the suite's output quiet, and the previous flag is
    restored after each identity. No identity changes: the module's host and Native suites stay at
    21 suites, 235 cases and 19 suites, 227 cases, the same names.

### C17.9 Not verified on this host

- Xcode 26.6, which CI's KMP iOS kit smoke job runs; this host has 27.0.
- The iosArm64 test binaries run on a device: they are linked, never executed.
- Section C15.2's device check, which is the owner's, and the release Crashlytics effect of
  anything.
- XC1's claim that composition reads relabel at once on a locale change, which rests on the library
  sources.

GitHub's signature verification, the CI run ids and the review classification go in the PR body
and the report, because they cannot exist before this commit does.
