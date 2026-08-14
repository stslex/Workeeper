# Navigation 3 Migration

**Arc:** three stages, three PRs, three CC sessions.
**Module surface:** `:app:app`, `core:ui:navigation`, `core:ui:mvi`, `feature:app-dialogs`.
**Baseline:** `dev @ 216dfce0`.
**Platform:** Android only. No CMP, no iOS, no `app:common` in this arc.

---

## 0. Why three stages

The Nav2 → Nav3 swap is **atomic**: two navigation systems cannot drive one host, so there is no per-screen migration and no bisect inside the swap. Everything that can be moved *out* of the atomic step must be, while Nav2 is still running.

| Stage | PR | Bisectable | Purpose |
|---|---|---|---|
| **1.1** | 1 | yes | Behavioural oracle, written against the current app |
| **1.2** | 2 | yes | API contracts that hide the navigation library — still Nav2 underneath |
| **1.3** | 3 | **no** | Swap the implementation |

By the time 1.3 runs, the diff is the DSL implementation, the host, and the retention wiring. The 12 `navComponentScreen*` call sites have already moved — one by one, each bisectable — during 1.2.

Tests come first because 1.2 is exactly the kind of work whose failures are silent: dropping one of the two result flows or losing a Store scope produces no compile error.

---

## 1. Stage 1.1 — Navigation regression suite

### 1.1.1 Purpose

A differential oracle. The same suite runs unchanged across 1.2 and 1.3; only the implementation beneath it changes. If a test needs editing during 1.2 or 1.3, it was describing the implementation rather than the behaviour.

Scope is deliberately migration-shaped, not exhaustive. Broad coverage buys little here: eleven of the 12 call sites are uniform and the compiler catches them. The risk lives in three places that are neither uniform nor compiler-checked, and that is where the weight goes:

1. Store scoping (silent failure);
2. result transport across `popBack`;
3. back-stack state restoration.

Route reachability stays broad but cheap — all 12 graph tags already exist.

### 1.1.2 Governing constraint

The suite reaches the app through **two channels only**:

1. the semantics tree — `onNodeWithTag`, `assertIsDisplayed`, `performClick`, `performScrollToIndex`;
2. observable persisted state — DAO reads via `metroRule.appDatabase`, as `ExerciseCreatePersistenceTest` already does.

Expressed as a prohibition: **no import from `androidx.navigation*` anywhere under `app/app/src/androidTest`.**

Import-scoped, not identifier-scoped. A name blacklist would survive renames badly and would wrongly catch `SavedStateHandle`, which is `androidx.lifecycle`, is unrelated to the navigation library, and does not disappear under Nav3 — what disappears is `NavBackStackEntry.savedStateHandle` as a result channel.

Enforcement: a `ForbiddenImport` rule scoped to that source set. A custom detekt rule is disproportionate for one directory.

**The plain `detekt` task cannot see `app/app/src/androidTest`.** Its source resolves to `src/main/{java,kotlin}` and `src/test/{java,kotlin}` only — probed on this tree, the task reports **0** files under `src/androidTest`. So a rule added to the existing global block in `lint-rules/detekt.yml` would be a silent no-op on the directory it is meant to police, while firing on every `src/main` file in the repo that imports `androidx.navigation*`. Enforcement therefore needs a **second, purpose-built `Detekt` task** with `source.setFrom("src/androidTest/kotlin")` and its own narrow config, following the shape already used at `core/core/build.gradle.kts:92-99`, wired into the gate alongside the bare `./gradlew detekt`.

### 1.1.3 Existing assets — extend, do not rebuild

| Asset | Location |
|---|---|
| `MetroTestRule` — `@Rule(order = 0)`, fresh `AppGraph` per test, in-memory Room + `FakeImageStorage`, exposes `appDatabase` for seeding and read-back | `harness/MetroTestRule.kt` |
| Compose rule — `androidx.compose.ui.test.junit4.v2.createAndroidComposeRule<MainActivity>()`, `@Rule(order = 1)`. Note the **v2** package | existing tests |
| `@Regression` annotation, selected via `-Pandroid.testInstrumentationRunnerArguments.annotation=…` | `core.ui.test.annotations` |
| 12 graph tags: `HomeGraph`, `AllTrainingsGraph`, `AllExercisesGraph`, `SingleTrainingGraph`, `ExerciseGraph`, `ExerciseChartGraph`, `LiveWorkoutGraph`, `PastSessionGraph`, `ArchiveGraph`, `SettingsGraph`, `PlanEditorGraph`, `ImageViewerGraph` | across features |
| 219 `testTag` call sites | across features |
| `ApplicationBottomBarTest` — bottom-bar switching | `:app:app` androidTest |

### 1.1.4 Entry audit — read-only, report before any edit

For each of the 12 destinations, establish and report as a table:

- the UI path that opens it from a cold start (which screen, which tagged control);
- what seed data that path requires, and whether `MetroTestRule`'s in-memory database can supply it;
- whether stable tags exist for arrival, for the opening control, and for the dismissing control.

Report gaps as a list and **stop**. Adding a missing `testTag` is in scope once approved; renaming an existing tag is not.

Known non-trivial cases, as resolved by the audit:

- **`LiveWorkout`** — its KDoc claimed at least one uuid must be non-null; `AllTrainingsStore` was right and the KDoc was stale. Both uuids may be null (blank-init ad-hoc entry), and that path needs **no seed data at all**, which makes LiveWorkout one of the cheapest destinations to reach rather than one of the most expensive. The suite uses it.
- **`ExerciseImage`** — reached from within `Exercise`, and the thumbnail only navigates when the exercise already has an image; on a no-image exercise the same tag either does nothing (detail) or opens the source-picker dialog (edit).
- **`PlanEditor`** — reached from within `LiveWorkout`, not `Exercise`; see the result-flow note below.
- **`ExerciseChart`** — picker-gated, opened from Home rather than the bottom bar. It is also the **isolation candidate** for `StoreRetentionTest`: the only parameterised destination with no `BackHandler` intercept on the way out and a clean constant default (`ChartPresetUiModel.ALL`).

### 1.1.5 Test classes

All `@Regression`, `internal`, `@RunWith(AndroidJUnit4::class)`, two-rule harness.

**Three arrival tags are gated.** `ExerciseGraph.kt:222`, `SingleTrainingGraph.kt:111` and `PlanEditorGraph.kt:84` each sit behind `if (state.isLoading) return`, so the tagged node is absent until an async DB read resolves. For Exercise and Single-training the gate bites the edit path only (`uuid != null`); for `PlanEditor` every navigation is a load, so its tag is **never** present on the first composed frame. Arrival on those three uses `waitUntil` with an explicit timeout — **not `waitForIdle`**, which can return before the tag arrives when an async load sits behind the gate.

**`RouteReachabilityTest`** — one test per destination, **all twelve**, including the three bottom-bar roots. This clause originally scoped the class to "destinations not already covered by `ApplicationBottomBarTest`", on the premise that that class covers the three roots. Measurement falsified the premise: at `origin/dev` (`bcf70b63`) all four of its tests fail their selection assertion on every run — `AppNavBar` publishes no `Selected` semantics at all (see `documentation/tech-debt.md`) — re-established independently on two machines (Linux x86_64; macOS arm64, 2026-08-14). A clause whose premise has been falsified is a stale record, not authority — the same class of error as the "26 call sites" figure and the two stale `Screen` KDocs this stage already corrected. Arrival here is asserted on the graph tag, which is independent of the selection defect, so the oracle stands alone. Seed, open through the UI as a user would, assert the graph tag, dismiss, assert the origin returns. Parameterised destinations must be reached by clicking a seeded row — never by constructing a `Screen` instance, which would test library mechanics rather than behaviour.

**`StoreRetentionTest`** — highest value, because the failure is silent. Under Nav3, a missing `rememberViewModelStoreNavEntryDecorator()` makes `viewModel { }` resolve against the Activity's store: nothing crashes, every Store becomes process-scoped.

- *retention* — set a distinctive non-default state (filter, search query, selected tab), navigate away, return, assert it survived;
- *isolation* — set state for entity A, go back, open the same destination for entity B, assert **default** state. This is the assertion that separates entry-scoped from Activity-scoped. Retention alone stays green under a broken scope, which is why isolation is mandatory rather than optional;
- *disposal* — leave a destination, assert no stale content bleeds into the next entry.

**`NavigationResultTest`** — both `popBack` flows, asserted through their user-visible effect rather than the transport:

- `planEditorSavedAttr` — save from the plan editor, assert the originating screen reflects it. **Live-workout is the only consumer** (`LiveWorkoutGraph.kt:29`); `Screen.PlanEditor`'s KDoc named Exercise and Single-training as well and was wrong. The test therefore takes the only path the app offers: Home → active-session banner → LiveWorkout → exercise kebab → *Edit plan* → Save. The test's own KDoc says so, in these words: *the only path in the app; if the chain changes, fix the path — do not delete the assertion.*
- `exerciseImageRequestAttr` — complete an image request, assert the requesting screen reflects it. Note the current consumption site is inline in `ExerciseGraph.kt` (`.getStateFlow`), which is a 1.2 concern; the test must not depend on that shape.

These two are the entire result surface.

**`BackStackStateRestorationTest`** — three representative cases rather than full coverage; the mechanism is shared, so a third instance adds cost without information.

- scroll position on a seeded list, across a detail round-trip. **Both list cases point at Archive**, whose `ArchiveScreen.kt:170` / `:225` are the only in-repo `rememberLazyListState()` call sites — `AllExercisesScreen` and `AllTrainingsScreen` rely on `LazyColumn`'s own default. What is under test is entry retention, not the state declaration: `LazyColumn` calls the same `rememberLazyListState()` internally and takes the same `rememberSaveable` path, so Archive is representative and no production change is needed to make the mutation land;
- an unsaved editor draft across navigate-away-and-return;
- selection mode across navigate-away-and-return.

### 1.1.6 Exit criteria

All four required.

**A — every test proven red**, against a named mutation, reverted afterwards. Evidence: mutation, failing test, assertion message.

The scroll mutation is more precise than "recreate list state" suggests: it is `rememberLazyListState()` → `remember { LazyListState() }`, **not** a bare `LazyListState()`. A bare constructor breaks scrolling with no navigation involved at all, so it would go red for the wrong reason. `remember {}` survives recomposition but not entry disposal, which is exactly the failure the test has to be able to see.

| Class | Mutation | Expected failure |
|---|---|---|
| `RouteReachabilityTest` | remove one `Action.Navigation` dispatch from a feature handler | that destination's arrival assertion |
| `StoreRetentionTest` | scope the Store to the Activity instead of the entry | the **isolation** test (retention may stay green — that is the point) |
| `NavigationResultTest` | drop one attr from the `popBack` call | that result test |
| `BackStackStateRestorationTest` | `rememberLazyListState()` → `remember { LazyListState() }` | the scroll test |

**B — CI collects them.** Evidence: job log for `…annotations.Regression` showing the collected count > 0 and the new classes named. An unannotated test is never selected; a suite that exists but is not collected is worth nothing.

**C — no NEW `androidx.navigation*` import** under `app/app/src/androidTest`. Two pre-existing violations are **named path exclusions**, each carrying a comment; the rule is otherwise scoped to the whole source set, so a future test cannot reintroduce the coupling. Evidence: the detekt task running green with the exclusions in place, plus a grep whose only hits are the two named files.

`ExerciseCreatePersistenceTest` and `AllTrainingsExtensionDbVisibilityTest` mount their own `NavHost` inside `setContent` as scaffolding for a DI / persistence test. They are not part of the oracle, and refactoring them onto `MainActivity` is outside this stage's scope fence. **Not a detekt baseline** — baselines rot silently; a named exclusion is visible in the config and carries its own revisit note for stage 1.3, when `NavHost` disappears. Recorded in `documentation/tech-debt.md`.

**D — full gate green**, standard convention: `--rerun-tasks --no-build-cache --no-configuration-cache`, detekt as a separate invocation.

### 1.1.7 Out of scope for 1.1

- Motion and visual continuity — invisible to both this suite and the 446 Paparazzi goldens, which capture statics. Accepted gap; manual review during 1.3.
- Deep links — unused today; Nav3 has no deep-link API yet either.
- Process death and `SavedStateConfiguration` — a 1.3 concern with its own test.
- Any Nav3 code, any API change. This stage adds tests only.

### 1.1.8 CI policy

Compose instrumented tests run on the pre-release regression pass, not on PRs, and `ui_tests.yml` states this in its own header. That stays.

One exception: **stage 1.3 gets a manual `ui_tests.yml` run with `test_suite: regression` on its branch, and the job link goes into the PR.** 1.3 is the one step that cannot be bisected; it is precisely where the suite earns its emulator time.

---

## 2. Stage 1.2 — API contracts (still Nav2 underneath)

Locked decisions:

1. **Own registration DSL.** `navComponentScreen*` currently extends `NavGraphBuilder`; under Nav3 the receiver becomes `EntryProviderBuilder`. Introduce a project-owned builder so the 12 call sites never name either.

   **The count is 12, not 26** — one registration per feature graph, matching the 12 graph tags exactly. The helpers themselves live in `core/ui/mvi/.../NavComponentScreen.kt` (four overloads: `navComponentScreen` / `navComponentScreenWithState`, each for `Feature` and `FeatureAssisted`), and delegate to `navScreen` / `navScreenWithState` in `core/ui/navigation/.../Screen.kt`. Eleven graphs call the wrapper; **`PlanEditorGraph.kt:30` does not use it at all** and calls `navScreen<Screen.PlanEditor.Existing>` directly, resolving its processor by hand — so the 1.2 sweep has eleven uniform sites plus one that needs its own decision.
2. **Typed result contract.** `popBack(vararg previousStackAttr: Pair<String, Any?>)` is `savedStateHandle`'s shape — string keys, `Any?` values — and that transport does not exist in Nav3. Replace with a typed contract independent of transport. `SaveHandlerAttr` is renamed accordingly: the concept changes, not just the name.
3. **`AnimatedContentScope` leaves the content-lambda signature.** Under Nav3 it arrives via `LocalNavAnimatedContentScope`; expose it through an accessor instead of a receiver.
4. **Result consumption moves out of graph composables.** `ExerciseGraph.kt` currently reads the image-request result inline via `.getStateFlow`; it moves behind the new contract.

Documented exception: item 3 introduces a CompositionLocal into the navigation path, which the project's own rule otherwise forbids. The rule targets `Navigator`; the animation scope is not the navigator, and Nav3 delivers it this way by design. Record the exception explicitly in `documentation/architecture.md` so it does not later read as a violation.

Exit criterion: the 1.1 oracle stays green with no edits. That is the proof the contract change did not alter behaviour.

Open before implementation: the concrete shape of the typed result contract.

---

## 3. Stage 1.3 — Swap the implementation

Atomic by nature. Contents:

- `NavHost` → `NavDisplay`; the 1.2 DSL is re-pointed at `entryProvider`;
- Store retention re-hosted on `rememberViewModelStoreNavEntryDecorator()` (`lifecycle-viewmodel-navigation3`);
- route types registered in `SavedStateConfiguration` — without it, process death crashes in production only; needs its own test;
- shared elements re-wired via `LocalNavAnimatedContentScope` with `SharedTransitionLayout` wrapping the `NavDisplay`. Simpler than the Nav2 arrangement — no per-feature threading of the scope;
- `NavigationLifecycleRegressionTest` (both the unit and instrumented variants) is **deleted**, not ported: it guards a Nav2-specific bug class around a singleton-scoped controller-backed navigator, and that class does not exist under Nav3.

Exit criteria: 1.1 oracle green; 446 goldens green; manual `ui_tests.yml` regression run linked in the PR; full gate both directions.

(The golden count appeared as 406 in earlier drafts of this document — a transcription error: a truncated per-module table was summed, dropping exercise-chart's 28 and single-training's 12. The raw count was always 446 across 13 modules, re-measured 446/446 on 2026-08-14 at both `origin/dev` and the stage 1.1 branch.)
