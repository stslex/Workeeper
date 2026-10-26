# KMP Phase 7.16 — feature:live-workout. Part A: the launch failure seam (PR-L)

**Status:** Part A COMPLETE — implemented by PR #344. Part B COMPLETE — implemented by the PR whose
merge brought this line to dev. Part C (PR-M) is specified by amendment.

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
