# KMP Phase 4 — `app:common`, the composition root leaves `:app:app`

Structural only. Android stays the product: no CMP applied, no iOS target, no behaviour change. This
phase moves code and changes one dependency direction; it does not redesign anything it moves.

Phase 3 (#228, #229) is merged. Phase 5 owns the startup processor and reads §6 of this document as
its input.

---

## 0. Corrections to the numbers this phase was planned with

Every figure below was re-measured against the tree. Three inherited claims were wrong, and one of
them had been used to justify the design.

| Claim carried in | Measured | Consequence |
|---|---|---|
| `:app:app` is "roughly 41 files / 4.3k LOC" | **14 files / 1448 LOC** in `src/main`. 47 files / 5932 LOC only if `androidTest` (17) and `test` (16) are counted with it | The extraction is a third the size it was scoped as |
| "theme MOVES to app:common" | `AppTheme` is in **`core:ui:kit`** and always was; `:app:app` merely calls it | Nothing to move |
| "`appGraphContract()` already exists as the seam" | **It does not exist.** `AppGraphContract`, `AppGraphContractHolder`, `AppGraphContractAccessor` and module `core:di` were all DELETED by the completed god-object split (`documentation/appgraphcontract-split/NEXT.md`) | The preferred option was justified by an artifact that is gone — see §2 |

The `:app:app` launcher resources, the `app_name` string, and every `androidTest` file stay where
they are; only three `bottom_bar_label_*` strings move.

---

## 1. What moves and what stays

**Moves to `app:common` — with its Kotlin package path UNCHANGED.**

| file | package | note |
|---|---|---|
| `App.kt` | `io.github.stslex.workeeper` | reads `AppRootDeps` instead of casting to `AppGraphOwner` |
| `AppRootViewModel.kt` | `io.github.stslex.workeeper` | `internal`, moves with its only caller |
| `host/AppNavigationHost.kt` | `…workeeper.host` | the `NavDisplay` and its twelve entry providers |
| `host/BottomBarNavigationListener.kt` | `…workeeper.host` | |
| `host/ClearFocusOnDestinationChanged.kt` | `…workeeper.host` | |
| `bottom_app_bar/BottomBarItem.kt` | `…workeeper.bottom_app_bar` | only its `R` import changes |
| `navigation/NavigatorEventBus.kt` | `…workeeper.navigation` | + its two unit tests |
| `navigation/NavigatorExt.kt` | `…workeeper.navigation` | carries the one Android-only seam (§5) |
| `bottom_bar_label_{home,trainings,exercises}` | `values/`, `values-ru/` | `app_name` stays |

**Stays in `:app:app`.** `BaseApplication`, `MainActivity`, `di/AppGraph`, `di/AppGraphOwner`,
`di/AppGraphBuilder`, the launcher resources, all 17 `androidTest` files, and the 14
`di/*ExtensionIdentityTest` unit tests.

`NavigatorReceiver` moves to **`core:ui:navigation`** rather than to `app:common`: it is a three-line
interface over `SharedFlow<NavCommand>`, `NavCommand` already lives there, and `core/ui/*` is the
module that owns navigation tooling by the architecture rule.

### 1.1 Why the packages do not change

`ApplicationBottomBarTest` — a `@Regression` oracle test — imports
`io.github.stslex.workeeper.bottom_app_bar.BottomBarItem`. The exit criterion for this phase is that
the navigation oracle is green **and unedited**, so renaming that package would have failed the
phase by definition.

A Kotlin package is not a Gradle namespace. Only the generated `R` class is tied to the namespace, so
moving these files while keeping their `package` declarations leaves every oracle import valid, and
the single `R` import that does change (`app.app.R` → `app.common.R`) sits inside a moved file rather
than inside a test. The split package `io.github.stslex.workeeper` across `:app:app` and `app:common`
is legal on the JVM and on Android; only JPMS forbids it, and this project does not use JPMS.

---

## 2. The decision: a narrow contract `app:common` owns

`@DependencyGraph(AppScope::class)` is `internal` to `:app:app`, and after this phase `:app:app`
depends on `app:common`. `app:common` therefore sits **below** the graph and cannot see it. Two
shapes were considered.

**(a) The composition root takes the graph contract — CHOSEN.** `app:common` declares
`AppRootDeps` naming the two app-scope types `App()` reads, plus an `AppRootDepsHolder` seam.
`AppGraph` implements the first, `BaseApplication` implements the second.

**(b) `app:common` declares its own `@ContributesGraphExtension`, merged by `:app:app` — rejected.**
A graph extension models a scope with its own lifetime; that is what the per-screen feature
extensions are. The composition root is not a scope — it needs two singletons that already live in
`AppScope`. Option (b) would invent a lifetime that models nothing, and would couple `app:common` to
Metro's aggregation machinery, which is the wrong direction for phase 7: an iOS composition root
wires itself.

The plan preferred (a) and cited `appGraphContract()` as the existing seam. That function does not
exist — it was deleted, along with the `AppGraphContract` god-object it belonged to. **The correction
strengthens (a) rather than weakening it**, because the idiom that *replaced* the god-object is a
better fit than the one that was removed: `RecoveryDeps`/`RecoveryDepsHolder` and
`BackupWorkerDeps`/`BackupWorkerDepsHolder` are each a module that must not depend on the graph
declaring its own narrow contract, satisfied by `AppGraph` and handed over by `BaseApplication`
through a typed holder. `app:common` is the same situation and gets the same treatment. Reintroducing
a wide contract here would undo a conclusion this repo paid for once already.

**Did (a) force an awkward contract shape?** No. `AppRootDeps` has two members. The only friction was
that `NavigatorReceiver` was `:app:app`-local, and relocating it to `core:ui:navigation` is a
correction rather than a workaround.

`navigatorEventBus` is exposed as its **concrete** type, matching the accessor `AppGraph` already
declared for `App.kt`. The composition root uses all three of its faces at once — `Navigator` to
dispatch, `NavigatorReceiver` to collect, `NavResultsSource` to read results — and three separate
interface members would permit a future graph to satisfy them with three different objects, which
the result transport cannot survive.

### 2.1 `app:common` contributes to `AppScope` without owning a graph

`NavigatorEventBus` is `@ContributesBinding(AppScope) @SingleIn(AppScope) @Inject` and moves to
`app:common`. Metro aggregates contributions across modules, so the binding still lands in
`:app:app`'s `@DependencyGraph` — the same way `core:ui:mvi` contributes `StoreDispatchers` and
`LoggerHolder` today. Contributing to a scope is not owning the graph, and only the latter would
force `app:common` above `:app:app`.

`:app:app` keeps all twelve feature dependencies after the move even though its main source set no
longer names them: the graph aggregates each feature's `@ContributesGraphExtension`, and the 14
identity tests assert exactly that. `app:common` needs the same twelve for the entry-provider
functions. Both edges are real; neither is redundant.

---

## 3. Module shape

`:app:common` is `convention.composeLibrary` + `metro`, namespace `io.github.stslex.workeeper.app.common`
(derived from the module path by `KotlinAndroid.configureKotlinAndroid`). `:app:dev` and `:app:store`
are untouched — there are **no product flavors** in this project, so the AGP-KMP single-variant
limitation does not reach the app tier, and both shells stay `com.android.application` with their
different `applicationId`s.

---

## 4. KMP readiness — what `app:common` would need to become multiplatform

`app:common` is NOT KMP in this phase. Its dependencies split three ways:

**Already portable.** `core:core` (phase 3 made its seams `expect/actual`), `core:ui:navigation`,
`core:data:dataStore`'s `CommonDataStore` interface, and `NavigatorEventBus` itself — pure Kotlin
plus `AppReinitializer`, which is already an `actual class`.

**Portable once phase 7 swaps the UI stack.** `core:ui:kit`, `core:ui:mvi`, the twelve feature
modules, and `feature:app-dialogs:impl` — all Compose-on-Android today, CMP after phase 7. Nothing
here needs a decision, only the dependency swap.

**Needs the `debugImplementation` treatment.** A KMP module has no debug variant, so
`debugImplementation` does not exist there. Phase 2 measured that all **15** real usages across the
repo are one artifact, `compose.ui.test.manifest`, and that `androidRuntimeClasspath` is the
sanctioned replacement. `app:common` does not declare one today and must not acquire one.

**Needs a real decision — exactly one.** `NavigatorExt.openRecovery` does
`Intent(context, RecoveryActivity::class)`. It is the only Android-only construct in everything that
moved, and it is why `app:common` depends on `feature:recovery`. It moves **verbatim** in this phase.
Phase 7 has to answer it, and the answer is not mechanical: iOS has no Activity, and the sibling
question (`RestoreRecoveryCoordinator.restartApp()`, a process-kill-and-relaunch primitive with no
iOS equivalent) is already logged as a design decision in
[kmp-migration-assessment.md](../kmp-migration-assessment.md) §3. The two should be answered together
— both are "how does a non-Android host leave the normal UI and enter recovery".

---

## 5. Startup was not touched

`BaseApplication.onCreate` is unchanged, by constraint. Its stages, verified against the code rather
than carried from prose:

1. `FirebaseCrashlyticsHolder.initialize()`
2. `Log.isLogging` / `CommonExt.isTraceExecutionEnabled` from `isDebugLoggingAllow`
3. `onCreateGraphBootstrap()` — the overridable seam the androidTest `TestApplication` no-ops
   - `handleRecoveryPreflightChain()` — two `runBlocking`s
   - `cleanupOrphanedImageTempFiles()` — fire-and-forget on `CoroutineScope(SupervisorJob() + Dispatchers.IO)`, an unowned scope
   - `bootstrapAppDialogObserver()` — resolves `appGraph.recoveryBootstrap` for its `init {}` side effect
4. `PerformanceMetricsRecorder.process(RecordAction.AppCreated)`

Both ordering invariants survive the move untouched, because everything they involve stayed in
`:app:app`:

- **Scenario 1 before Scenario 2** is enforced by control flow inside `handleRecoveryPreflightChain`
  (early `return`s), not only by the KDoc that describes it.
- **`bootstrapAppDialogObserver` completes before `MainActivity.onCreate`** holds because it runs in
  `Application.onCreate`, which the platform guarantees precedes any Activity's. The reason it
  matters is that the observer subscribes to a `replay = 0` `SharedFlow`, so a dispatch arriving with
  no subscriber is dropped.

`MainActivity`'s Scenario 2 branch still fires **before** `setContent { App() }`, so moving `App()`
out of the module does not move it across that boundary.

---

## 6. Notes for phase 5 (the startup processor)

Written for the reader whose question is "what is the startup stage inventory, now that the
composition root has moved".

- **The inventory did not change.** The move took nothing out of `onCreate` and put nothing in. The
  four stages above are the complete list, and stage 3 is the only one with internal ordering.
- **`onCreateGraphBootstrap` is already a seam, and it is the wrong shape for a processor.** It is
  `protected open` and overridden to a no-op by the androidTest `TestApplication` so `MetroTestRule`
  can install a per-test graph before any graph read. Any stage-list design has to keep that
  "skip the whole graph-touching half" capability, not just "skip stage N" — the test harness needs
  the graph *untouched*, not merely un-run.
- **Two stages hold ordering constraints that a naive stage list would drop.** Scenario 1 → Scenario 2
  is a data dependency (Scenario 2 runs only if Scenario 1 was a no-op, and Scenario 1 can terminate
  the process). `bootstrapAppDialogObserver` has a *deadline* rather than a predecessor: it must
  finish before the first Activity, which a parallel or lazy stage runner would violate silently —
  the failure mode is a dropped dialog, not an exception.
- **One stage is already a latent defect and should not be ported as-is.**
  `cleanupOrphanedImageTempFiles` launches on `CoroutineScope(SupervisorJob() + Dispatchers.IO)` —
  a scope nothing owns, cancels, or joins. It is fire-and-forget by intent, but it means the process
  has an unstructured coroutine outliving startup. Phase 5 should give it an owner; this phase
  deliberately did not, because redesigning startup here would leave phase 5 with no baseline to
  attribute a regression to.
- **`handleRecoveryPreflightChain` reads `appGraph` on its first statement**, which is what forces
  the `by lazy` graph to build with production roots. Graph construction is therefore *implicitly*
  stage 3.0. A stage inventory that lists the pre-flight without listing graph construction has
  hidden the most expensive startup step.
- **The two `runBlocking`s are load-bearing, not laziness.** The KDoc's reason checks out: dispatching
  them after `setContent` would briefly show `MainActivity` content before recovery routing decides.
  Any processor that makes stages suspend must keep the main thread blocked across these two.

---

## 7. Gates

Recorded in the PR body with counts. The exit criterion for this phase is the navigation oracle green
and unedited, and the pinned instrumented failure list unchanged — see
[tech-debt.md](../tech-debt.md) for the list's current pin and
[kmp-phase-0-instrumented-filter.md](kmp-phase-0-instrumented-filter.md) for why the count it is
pinned at changed in this cycle.
