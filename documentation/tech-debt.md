# Technical Debt Register

This document tracks known debt that should be addressed after functional delivery. It is a **living ratchet**: entries are added when debt is incurred, removed when paid down, and audited periodically against reality (last full audit: 2026-04-28 via dual-model triangulation; v2.0 stage updates applied 2026-04-28).

Each tracked location should carry a `TODO(tech-debt): <category> — <ref>` marker in code so debt is grep-able during development.

## How to read this document

- **Severity** is informal: 🔴 critical for release, 🟡 medium (polish/cleanup), 🟢 low (architectural hygiene).
- **Status** indicates current state. ACTIVE = work to do; PARKED = intentionally deferred to a known horizon.

---

## UI Mapping Boundary Debt

**Rule:** UI composables and graph files render already mapped, localized, and formatted state. Mapping and localization shaping happen in handler / state-mapper layers. See [architecture.md → UI types vs domain types](architecture.md).

| Severity | Location | Description |
|---|---|---|
| 🟢 | [feature/archive/.../ui/ArchiveGraph.kt](../feature/archive/src/main/kotlin/io/github/stslex/workeeper/feature/archive/ui/ArchiveGraph.kt) | Snackbar templates (`restoredTemplate.format(event.item.name)`) substituted in graph. Should be pre-formatted; event payload should carry the ready string. |
| 🟢 | [feature/exercise/.../ui/ExerciseGraph.kt](../feature/exercise/src/main/kotlin/io/github/stslex/workeeper/feature/exercise/ui/ExerciseGraph.kt) | `Event.ShowImageError` → `when (event.errorType) { ... }` shaping in graph. Move to mapper or carry resolved message in the event itself. |
| 🟢 | [feature/single-training/.../ui/SingleTrainingGraph.kt](../feature/single-training/src/main/kotlin/io/github/stslex/workeeper/feature/single_training/ui/SingleTrainingGraph.kt) | Discard-dialog title/body strings still chosen in graph. Push to state or to event payload. |
| 🟢 | [feature/home/.../ui/components/ActiveSessionBanner.kt](../feature/home/src/main/kotlin/io/github/stslex/workeeper/feature/home/ui/components/ActiveSessionBanner.kt) | Concatenation `stringResource(label) + " · " + stringResource(progress)` in composable. Pre-format full label in `HomeUiMapper`. |
| 🟢 | [feature/home/.../ui/components/RecentSessionRow.kt](../feature/home/src/main/kotlin/io/github/stslex/workeeper/feature/home/ui/components/RecentSessionRow.kt) | String interpolation `"${item.finishedAtRelativeLabel} · ${item.durationLabel}"` in composable. Add a single combined label to `RecentSessionItem`. |
| 🟢 | [feature/home/.../ui/components/TrainingPickerSheet.kt](../feature/home/src/main/kotlin/io/github/stslex/workeeper/feature/home/ui/components/TrainingPickerSheet.kt) | `listOfNotNull(...).joinToString(" · ")` in composable. Same pattern — pre-format in mapper. |
| 🟢 | [feature/past-session/.../ui/PastSessionGraph.kt](../feature/past-session/src/main/kotlin/io/github/stslex/workeeper/feature/past_session/ui/PastSessionGraph.kt) | `Event.ShowError` → `when (event.errorType) { ... }` shaping in graph. Same fix as ExerciseGraph. |
| 🟢 | [feature/past-session/.../ui/PastSessionScreen.kt](../feature/past-session/src/main/kotlin/io/github/stslex/workeeper/feature/past_session/ui/PastSessionScreen.kt) | Error headline `when (errorType) { ... }` in composable. Push message into `Phase.Error` payload. |

---

## Schema Migration Debt

| Severity | Location | Description |
|---|---|---|
| 🟢 PARKED | [core/database/.../di/CoreDatabaseModule.kt](../core/database/src/main/kotlin/io/github/stslex/workeeper/core/database/di/CoreDatabaseModule.kt) | No explicit `Migration(3, 4)` and `Migration(4, 5)` defined. Uses `fallbackToDestructiveMigrationFrom(dropAllTables = true, 2, 3, 4)`. **Pre-release context (2026-04-28):** v5 is the first build going to Play Store review; previous v3/v4 builds were never published. Destructive fallback is acceptable until the first stable release ships. **Trigger to act:** before any version after v5 changes the schema, write proper `Migration(N, N+1)` and remove the fallback for the corresponding versions. |

---

## Reactive Aggregations

| Severity | Location | Description |
|---|---|---|
| ✅ RESOLVED | [feature/exercise-chart](../feature/exercise-chart/) | **Heavy-aggregation re-execution policy** (parked from v2.1). The v2.2 chart consumer chooses one-shot reads over a `Flow` subscription: the screen reads `getHistoryByExercise` once on entry / preset change / picker change and buckets in Kotlin. No persistent subscription means no spurious recomputation when other sessions log sets. The "if a cache is needed, cache at the consumer side" guidance was effectively answered by binding the data to `State` instead. See [feature-specs/v2.2-exercise-charts.md → Architectural notes](feature-specs/v2.2-exercise-charts.md#architectural-notes). |
| 🟡 | [feature/exercise-chart/.../mvi/mapper/ExerciseChartUiMapper.kt](../feature/exercise-chart/src/main/kotlin/io/github/stslex/workeeper/feature/exercise_chart/mvi/mapper/ExerciseChartUiMapper.kt) | **Per-day max-of-day collapse loses information** when the user does two sessions on one calendar date — only the higher set's session is reachable from the tooltip. v2.2 ships max-of-day for simplicity; follow-up is to render two points per day (each session's best set, both anchored to the day's X with a small jitter / vertical marker). **Trigger to act:** user reports that double-session days are surprising. |
| 🟢 | [feature/exercise-chart/.../mvi/handler/CommonHandler.kt](../feature/exercise-chart/src/main/kotlin/io/github/stslex/workeeper/feature/exercise_chart/mvi/handler/CommonHandler.kt) | **Window filtering happens client-side**, not in SQL. The mapper drops sets older than the active preset's start. Acceptable at v2.2 data sizes (~hundreds of rows per exercise); if profiling shows the read is slow for >2 years of dense history (>5000 rows per exercise), add a `:sinceMillis` overload to `SessionDao.getHistoryByExercise` and pass it from the handler. **Trigger to act:** load time exceeds ~150ms on a mid-range device. |
| 🟢 | [core/exercise/.../sets/PrComparator.kt](../core/exercise/src/main/kotlin/io/github/stslex/workeeper/core/exercise/sets/PrComparator.kt) ↔ [SessionDao.observePersonalRecord](../core/database/src/main/kotlin/io/github/stslex/workeeper/core/database/session/SessionDao.kt) | Two parallel implementations of the same comparator (Kotlin object-level and SQL `ORDER BY`). The Kotlin path is needed at session finish where the comparison happens against an immutable in-memory snapshot. If the comparator definition changes (e.g. tiebreak rule), both must be updated together. Acceptable duplication; covered by `PrComparatorTest`. |
| 🟢 | [core/exercise/.../sets/PrComparator.kt](../core/exercise/src/main/kotlin/io/github/stslex/workeeper/core/exercise/sets/PrComparator.kt) ↔ [SessionDao.observePersonalRecordsBatch](../core/database/src/main/kotlin/io/github/stslex/workeeper/core/database/session/SessionDao.kt) | Spec called for a parity test that seeds Room and asserts both `bestOf(...)` and the DAO pick the same set. Not implemented because Room test setup in `core/exercise/test` is cross-module; the test would need to live alongside `androidTest` infrastructure. **Trigger to act:** comparator semantics change (e.g. tiebreak rule). |
| 🟢 | [feature/live-workout/.../domain/LiveWorkoutInteractorImpl.kt:70-86](../feature/live-workout/src/main/kotlin/io/github/stslex/workeeper/feature/live_workout/domain/LiveWorkoutInteractorImpl.kt) | Sequential (not parallel) per-entity queries — `loadSession` does N per-exercise calls (`getAdhocPlan` / `getPlan` / `setRepository.getByPerformedExercise`) in a loop. One-shot at session open, low frequency. Cheapest fix: wrap with `asyncMap` from [`core/core/coroutine/CoroutineExt.kt`](../core/core/src/main/kotlin/io/github/stslex/workeeper/core/core/coroutine/CoroutineExt.kt). Not blocking. |
| 🟢 | [core/exercise/.../session/SessionRepositoryImpl.kt:130-143](../core/exercise/src/main/kotlin/io/github/stslex/workeeper/core/exercise/session/SessionRepositoryImpl.kt) | Sequential per-entity queries — `getSessionDetail` does N `setDao.getByPerformedExercise` calls inside `withTransaction`. One-shot at Past session open, low frequency. Same fix shape (`asyncMap`). Not blocking. |

---

## Dialog State Discipline — follow-ups

Items deferred from the dialog-state-discipline PR (see [compose-state-discipline.md → Rule 4](compose-state-discipline.md) and [architecture.md → State / Action / Event conventions](architecture.md)). Both are intentional cuts to keep the rule migration scoped.

| Severity | Location | Description |
|---|---|---|
| 🟡 | [feature/live-workout/.../mvi/store/LiveWorkoutStore.kt](../feature/live-workout/src/main/kotlin/io/github/stslex/workeeper/feature/live_workout/mvi/store/LiveWorkoutStore.kt), [feature/exercise/.../mvi/store/ExerciseStore.kt](../feature/exercise/src/main/kotlin/io/github/stslex/workeeper/feature/exercise/ui/mvi/store/ExerciseStore.kt), [feature/single-training/.../mvi/store/SingleTrainingStore.kt](../feature/single-training/src/main/kotlin/io/github/stslex/workeeper/feature/single_training/mvi/store/SingleTrainingStore.kt) | **`Event.ShowError(message: String)` payload shape inconsistency.** The architecture doc prescribes `Event.ShowError(type: ErrorType)` with the localized resource resolved in the graph; live-workout, exercise, and single-training instead carry a pre-resolved `String message`. Pre-existing minor inconsistency — out of scope for the dialog/sheet rule migration. **Trigger to act:** next pass that touches `Event.ShowError` in any of these features. |
| 🟡 | (cross-cutting — every feature with a `dialogState`) | **`dialogState` is not round-tripped through `SavedStateHandle`.** Configuration changes survive (same VM-scoped store). Process death does not — a dialog open at the moment Android reclaims the process disappears on resume. The Rule 4 known-limitation note acknowledges this; round-tripping critical dialogs needs a per-feature decision (which dialog payloads are worth `Bundle`-encoding) and a `BaseStore` extension or per-store `SavedStateHandle` wiring. **Trigger to act:** user-visible report of "I had a confirm dialog open, the app got killed, and the action got abandoned." |

---

## State Mutation Discipline

**Rule:** `BaseStore.updateState` and `updateStateImmediate` lambdas should perform pure state transformation only — given `current`, return a copy. Mapping, formatting, and any work involving `ResourceWrapper` or domain-to-UI conversions runs *before* the lambda body. See [architecture.md → State mutation discipline](architecture.md) and the [`compose-state-discipline`](../.claude/skills/compose-state-discipline.md) skill.

| Severity | Location | Description |
|---|---|---|
| 🟡 | [feature/home/.../mvi/handler/CommonHandler.kt:36-39](../feature/home/src/main/kotlin/io/github/stslex/workeeper/feature/home/mvi/handler/CommonHandler.kt) | Mapping inside `updateStateImmediate` lambda — `row?.toUi(now, resourceWrapper)` runs on Main.immediate every active-session emit. Hoist out before the lambda. |
| 🟡 | [feature/all-exercises/.../mvi/handler/PagingHandler.kt:49-52](../feature/all-exercises/src/main/kotlin/io/github/stslex/workeeper/feature/all_exercises/mvi/handler/PagingHandler.kt) | Mapping `tags.map { it.toTagUi() }.toImmutableList()` inside `updateStateImmediate` lambda. Same fix shape as above. |
| 🟡 | [feature/all-trainings/.../mvi/handler/PagingHandler.kt:51-54](../feature/all-trainings/src/main/kotlin/io/github/stslex/workeeper/feature/all_trainings/mvi/handler/PagingHandler.kt) | Same pattern as the home / all-exercises rows. |
| 🟡 | [feature/single-training/.../mvi/handler/CommonHandler.kt:55-56](../feature/single-training/src/main/kotlin/io/github/stslex/workeeper/feature/single_training/mvi/handler/CommonHandler.kt) | Same pattern as the home / all-exercises rows. |

---

## Live workout — release-phase hot-fix follow-ups

| Severity | Location | Description |
|---|---|---|
| 🟡 | [feature/live-workout/.../domain/LiveWorkoutInteractorImpl.kt loadSession](../feature/live-workout/src/main/kotlin/io/github/stslex/workeeper/feature/live_workout/domain/LiveWorkoutInteractorImpl.kt) | Read-time `trainingPlan ?: exerciseRepository.getAdhocPlan(...)` fallback exists because we don't backfill old data via migration in this commit. When the next schema bump lands (with the proper Migration framework now in place — see Migration Policy in [architecture.md](architecture.md) → Room database), include a one-shot backfill: `UPDATE training_exercise_table SET plan_sets = (SELECT plan_sets FROM exercise_table WHERE exercise_table.uuid = training_exercise_table.exercise_uuid) WHERE plan_sets IS NULL`. After that, drop the runtime fallback. |
| ✅ RESOLVED | [feature/live-workout/.../mvi/mapper/ExerciseDoneRule.kt](../feature/live-workout/src/main/kotlin/io/github/stslex/workeeper/feature/live_workout/mvi/mapper/ExerciseDoneRule.kt) | Status derivation logic was duplicated between `LiveWorkoutMapper.toUiList` (initial load) and `StateStatusMapper.recomputeOnly` (post-mutation recompute), with the load path still using the legacy `plan.isEmpty() → performed.any { it.isDone }` shortcut. Both callers now route through `ExerciseDoneRule.isDoneLoad` / `isDoneLive`. The two entry points share an `expectedPositions` union; the live variant additionally folds `visibleSets.indices` so typed-but-unchecked drafts keep the row CURRENT. See [feature-specs/live-workout.md → Load vs live status](feature-specs/live-workout.md#load-vs-live-status--exercisedonerule). |
| ✅ RESOLVED | [feature/live-workout/.../mvi/](../feature/live-workout/src/main/kotlin/io/github/stslex/workeeper/feature/live_workout/mvi/) | **Live-workout draft seed and visible-row merge centralized** (lock-in for the LiveSetRow reset class of bugs). Visible-row resolution (`performed > draft > plan > fallback`) is computed once in the MVI mapper and exposed as `LiveExerciseUiModel.visibleSets`; `LiveExerciseCard` no longer accepts `setDrafts` and no longer imports `Store.State.DraftKey`. Draft seed/update goes through a single helper (`mvi/handler/LiveWorkoutDraftExt.kt`) so type / weight / reps edits all preserve the unrelated fields from the current visible row. Behavior tests covering every field-preservation pair and the resolver priority live in `LiveSetDraftBehaviorTest.kt` and `LiveSetVisibleRowsResolverTest.kt`. See [architecture.md → Source-of-truth merging belongs to mappers](architecture.md) and [feature-specs/live-workout.md → Set draft and visible row architecture](feature-specs/live-workout.md). |
| 🟢 | [feature/live-workout/.../mvi/store/LiveWorkoutStore.kt activeExerciseUuids](../feature/live-workout/src/main/kotlin/io/github/stslex/workeeper/feature/live_workout/mvi/store/LiveWorkoutStore.kt) | Active-set state is ephemeral — resets on app background/restore. If users complain about losing parallel state, persist via a new column on `performed_exercise_table` or session-scoped DataStore. Not blocking. |

---

## v2.4 Design foundation — follow-ups

Items deferred from the v2.4 PR (see `documentation/feature-specs/v2.4-design-foundation.md` Sections 6 / 7). The kit primitives, theme tokens, plan editor screen, list-screen reworks, chart footer fix, and DAO queries (F1, F2) all landed; remaining surface is tracked here for a follow-up PR.

| Severity | Location | Description |
|---|---|---|
| 🟡 | feature/live-workout/.../mvi/store/, ui/components/ | **Live workout drag-to-reorder + snackbar undo** (spec 5.4 partial). The follow-up commits closed the PlanEditor route migration, AppCheckmarkButton, AppTooltip on chip, and three-dots menu offset fix. Drag-to-reorder for exercises and inner sets via the new `ReorderableLazyListState` / `ReorderableColumnState`, plus snackbar-undo for set delete (D5 replace policy), are still deferred. The new kit primitives exist and compile; the wiring is the work. |

| 🟡 | feature/past-session/.../ui/components/PastSetEditRow.kt | **PastSession set-delete with snackbar undo** (spec 5.7 partial). Drag-to-reorder, total-kg removal, the PR badge explainer dialog, stable column widths (PR slot reserves 56dp), removed leading accent stripe, and the explicit drag-handle icon all landed; structural set-delete with snackbar+Undo policy (D5) remains paired with the live-workout work. |
| 🟢 | feature/exercise-chart/.../ui/components/ChartTooltipPopup.kt | **Chart tooltip rewrite from subcompose to coordinate-based draw** (spec 5.6). Footer overflow fix (the user-visible Russian regression) landed; the structural tooltip rewrite is a separate larger refactor. Existing `SubcomposeLayout` tooltip still functions. |
| 🟢 | feature/home/.../ui/components/TrainingPickerSheet.kt | **Templates picker → full-screen route** (spec 5.8 / E8). Not yet landed. Requires a new `Screen.TemplatesPicker` route, a TemplatesPickerScreen with `LargeTopAppBar` + search, and migrating the home tap from `consume(...sheet open)` to `Action.Navigation.OpenTemplatesPicker`. |
| 🟢 | (multiple touched files) | **`AppDimension.Padding` migration sweep** (spec B1). Padding is `@Deprecated` and emits warnings on call sites. Step 13 of v2.4 was opportunistic — touched files migrated as work landed. Remaining call sites continue to compile with deprecation warnings. **Trigger to act:** v2.7 tech-debt ratchet, or earlier if drift detected. |
| 🟢 | core/ui/plan-editor/.../mvi/store/PlanEditorStoreImpl.kt | **Snackbar undo for set-delete** (spec D5). The new PlanEditorScreen currently uses the existing immediate-delete behavior. Replacing with snackbar-undo is grouped with the live-workout snackbar-undo work above so both editor surfaces share the policy. |
| 🟢 | core/ui/plan-editor/.../PlanEditorBody.kt | **Plan editor drag-to-reorder** (spec 5.4 partial). The kit primitives (`reorderableColumnItem` + `reorderableColumnDragHandle` with live displacement preview) exist and ship in `core/ui/kit`; PlanEditorBody still renders a non-reorderable `forEachIndexed` loop. Migration mirrors PastExerciseCard's wiring — pass `dragHandleModifier` through PlanEditorRow with the trailing DragHandle icon. |
| 🟢 | feature/exercise/.../ExerciseEditScreen.kt | **ExerciseEditScreen rework** (v2.4.x deferred — separate spec next round). Inline plan section landed in v1.41 release-blocker fix (renders `PlanEditorBody(scrollable = false)` for `Mode.Edit(isCreate = true)`). Image+name unification and full layout overhaul are still pending. |
| 🟡 | feature/exercise/.../ui/mvi/handler/ClickHandler.kt processAdhocPlanEditorAction | **Exercise create-flow plan persistence — process-death loss.** The inline plan editor used during exercise create-mode mutates `state.adhocPlan` in memory; persistence happens only on Save via `ExerciseChangeDomain.lastAdhocSets`. A process kill mid-edit loses the in-flight draft. Identical semantics to the pre-`ad117f3a` `AppPlanEditor` bottom sheet — not a regression, but a known limitation. **Fix path:** introduce a draft row in `exercise_table` (or a sibling `exercise_draft_table`) keyed by a stable client-generated UUID, restored on screen entry, deleted on Cancel/Save. Requires schema migration, DAO filter audit (every `is_adhoc = 0` query must also filter drafts), `UNIQUE(name)` workaround, and an orphan-cleanup worker. **Trigger to act:** user-reported draft loss after a process death, or when DB-draft work is otherwise prioritized. |
| 🟢 | feature/exercise/.../ExerciseDetailScreen.kt | **TopBar collapsing animation feel on ExerciseDetail** — pending user clarification on whether it is a bug or perceived discomfort. Track here so the question is not lost. |

---

## v2.3 Quick start workout — follow-ups

Items deferred from the v2.3 PR (per spec Section 10). Track here so the v2.7 ratchet pass can pick them up.

| Severity | Location | Description |
|---|---|---|
| 🟢 | feature/exercise/.../ExerciseInteractorImpl, feature/live-workout/.../LiveWorkoutInteractorImpl | **Track Now / Quick start UI unification** (deferred to v2.7). Both flows now share the data layer (`SessionRepository.createAdhocSession`, `discardAdhocSession`) but stay as separate UI flows. UI-layer convergence is its own refactor. |
| 🟢 | feature/live-workout/.../mvi/handler/ | **Live workout feature module decomposition** (deferred to v2.7). `feature/live-workout` accumulated significant complexity through v2.1 (PR detection), v2.2 (chart hook), v2.3 (mid-session add, name edit, empty-finish dialog). `ExercisePickerHandler` was already split off via the `PlanEditAction`-style wrapper to keep ClickHandler from bloating; further decomposition (e.g. NameEditHandler, EmptyFinishHandler) is candidate. |
| 🟡 | feature/live-workout/.../mvi/handler/ExercisePickerHandler.kt `addExerciseFlow` | **PR snapshot fetch failure mode telemetry** (new in v2.3). When `fetchPrSnapshotForExercise` fails for a library pick, the exercise is still added to the session and the in-moment PR badge is suppressed (degraded mode silent failure). If telemetry shows this firing often, the user-facing UX needs revisit. |
| 🟡 | feature/live-workout/src/androidTest/ | **Mid-session add UI in instrumented tests** (deferred to v2.7). Per project policy (UI flow tests as dedicated test-coverage PRs), no androidTest landed in v2.3. The blank-init Quick start flow + picker bottom sheet + empty-finish discard cascade need smoke coverage. |
| 🟢 | core/database/.../exercise/ExerciseDao.kt + ExerciseRepositoryImpl.createInlineAdhocExercise | **`ExerciseEntity.isAdhoc` cleanup of stale graduated rows** (deferred, monitoring). After many cycles of inline create → graduate, the library may accumulate poorly-named single-use exercises. No action in v2.3; revisit if user-facing exercise-list pruning becomes a need. |

---

## Remaining from PR #78

| Severity | Location | Description |
|---|---|---|
| 🟡 | [core/exercise/.../personal_record/PersonalRecordRepository.kt](../core/exercise/src/main/kotlin/io/github/stslex/workeeper/core/exercise/personal_record/PersonalRecordRepository.kt) | `observePersonalRecords(uuidsByType)` is a combine-of-N flow — N separate Room subscriptions. KDoc marks it as one-shot only, but there is no compile-time guard. Callers must use `firstOrNull()` or `getPersonalRecord`. Long-lived subscribers must use `observePersonalRecordsBatch` / `observePrSetUuids`. Consider removing from the public interface or converting to `suspend fun` to make the one-shot contract enforced. |
| 🟢 | [core/exercise/.../session/SessionRepositoryImpl.kt `finishSessionAtomic`](../core/exercise/src/main/kotlin/io/github/stslex/workeeper/core/exercise/session/SessionRepositoryImpl.kt) | Double dispatcher switch: outer `withContext(ioDispatcher)` wraps `transition {}` which already does `withContext(ioDispatcher)`. Redundant context switch; clean up when touching this method next. |
| 🟢 | [core/exercise/.../session/SessionRepositoryImpl.kt `groupBySession()`](../core/exercise/src/main/kotlin/io/github/stslex/workeeper/core/exercise/session/SessionRepositoryImpl.kt) | `sortedByDescending { it.finishedAt }` is a redundant O(N log N) pass — the DAO query already returns `ORDER BY sn.finished_at DESC` and `groupBy` preserves insertion order. Remove the sort. |

---

## Spec-vs-Reality Drift

Items where shipped behaviour diverges from what specs originally asked for. Surfaced by the 2026-04-28 audit.

| Severity | Spec | Item | Reality |
|---|---|---|---|
| 🟡 | exercises.md | "Phantom shims removed" | `TrainingDataModel.labels` and `TrainingDataModel.exerciseUuids` still present and populated by repo. Cleanup. |
| 🟡 | exercises.md | "`pagedActiveByTags(Set<String>)` AND semantics" | Shipped uses `IN (:tagUuids)` (OR semantics). The deprecated AND-semantics query was removed as dead code; OR is intentional and remains the supported behaviour — locked decision in v2.0 spec. |
| ✅ RESOLVED | exercises.md | "Canonical NavigationHandler with `@Inject Navigator`" | Resolved in the navigation-lifecycle PR (PR #143). All feature `NavigationHandler` classes are now `@ViewModelScoped @Inject Navigator` constructor-injected; the old `Component.create(navigator, screen)` factory pattern is gone. Route arguments enter the Store via Dagger assisted injection (`@Assisted screen: Screen.<X>`) instead of through a `Component<Screen>` subclass. The `MviHandlerConstructorRule` literal-name exemption for `NavigationHandler` is now redundant — it remains in the rule source for back-compat but new code does not rely on it. See [architecture.md → Navigation](architecture.md#navigation) for the canonical pattern. |
| 🟡 | exercises.md, trainings.md, live-workout.md | "Haptics emitted for every Click action" | Several dismiss / undo / cancel paths bypass haptic emission. Specifically: `processUndoArchive`, `processCancelPermanentDelete`, `processBulkDeleteDismiss` in all-exercises; `processBulkDeleteDismiss` in all-trainings; dismiss handlers and done-card header expansion in live-workout. |
| 🟡 | trainings.md, live-workout.md | "Composable `@Previews` for every public/internal Composable" | `AllTrainingsScreen`, `TrainingDetailScreen`, `TrainingEditScreen` expose internals without `@Preview`. `TrainingRow` lacks active/inactive permutations. `live-workout` is fully covered (verified). |

---

## androidTest Coverage Gap

Five stub files with `TODO(feature-rewrite-tests)` markers carry an `@Ignore`d placeholder method — skipped via `@Ignore` placeholder, real coverage tracked in #93. (`SettingsScreenTest.kt`, listed below, was filled with real tests in a prior PR; row kept for traceability.) Created during initial Stage rewrites (5.1 / 5.2 / 5.3) under the assumption tests would be filled once the smoke harness stabilised. v2.0 stage scheduled the fill-in work; remaining stubs are tracked in the v2.0 spec and addressed in their own PRs.

| Severity | Location | Stage |
|---|---|---|
| 🟡 | [feature/settings/.../SettingsScreenTest.kt](../feature/settings/src/androidTest/kotlin/io/github/stslex/workeeper/feature/settings/SettingsScreenTest.kt) | 5.1 |
| 🟡 | [feature/archive/.../ArchiveScreenTest.kt](../feature/archive/src/androidTest/kotlin/io/github/stslex/workeeper/feature/archive/ArchiveScreenTest.kt) | 5.1 |
| 🟡 | [feature/all-exercises/.../AllExercisesScreenTest.kt](../feature/all-exercises/src/androidTest/kotlin/io/github/stslex/workeeper/feature/all_exercises/AllExercisesScreenTest.kt) | 5.2 |
| 🟡 | [feature/exercise/.../ExerciseScreenTest.kt](../feature/exercise/src/androidTest/kotlin/io/github/stslex/workeeper/feature/exercise/ExerciseScreenTest.kt) | 5.2 |
| 🟡 | [feature/all-trainings/.../AllTrainingsScreenTest.kt](../feature/all-trainings/src/androidTest/kotlin/io/github/stslex/workeeper/feature/all_trainings/AllTrainingsScreenTest.kt) | 5.3 |
| 🟡 | [feature/single-training/.../SingleTrainingScreenTest.kt](../feature/single-training/src/androidTest/kotlin/io/github/stslex/workeeper/feature/single_training/SingleTrainingScreenTest.kt) | 5.3 |

**Plan:** address as a dedicated test-coverage PR after v2 stabilises. Don't try to fill in feature PRs.

---

## Navigation lifecycle — RESOLVED in PR #143

The "stale `NavController` after activity recreation crashes navigation" class of
bugs that shipped before `master` is closed by the navigation-lifecycle refactor.
The architecture now strictly separates navigation **decisions** (Store/Handler
layer, depends on `Navigator`) from navigation **execution** (App/UI bridge,
operates on the composition-scoped `NavController` from
`rememberNavController()`).

What changed:

- `NavigatorEventBus` (`@Singleton`, controller-free) replaced the old controller-
  backed `NavigatorImpl` / `NavigationHolderController` / `NavigationHolderImpl`
  trio. It exposes only `Navigator` (producer) and `NavigatorReceiver` (consumer)
  interfaces over a `SharedFlow<NavigationCommand>`.
- `NavigatorExt.NavigationEventBusSetup` (composable) collects commands keyed on
  the current `NavController` via `LaunchedEffect(navController)` so the executor
  rebinds on every recomposition / activity recreation. The bus instance survives;
  the executor is per-composition.
- `App.kt` owns `rememberNavController()` and creates the `NavigatorHolder`
  composition-scoped via `remember(navController)`.
- `RootComponentImpl`, `LocalRootComponent`, `LocalNavigator`, and the
  `Component.create(navigator, screen)` factory pattern are all removed. Route
  arguments enter the Store via Dagger assisted injection
  (`@Assisted screen: Screen.<X>`).
- All feature `NavigationHandler`s are `@ViewModelScoped @Inject Navigator`.
- `Screen.PlanEditor.planEditorSavedAttr` flows through
  `navigator.popBack(planEditorSavedAttr.toPairValue(true))` and is consumed in
  the previous screen's graph composable via `navComponentScreenWithState` +
  `stateHandle.getStateFlow(...).collectAsState()`. Consumers reset the flag via
  `stateHandle.setAttrDefaultValue(...)` so re-entry does not retrigger.

Verification requirements (live in test code, not docs):

- `NavigatorEventBusTest` covers `navTo` / `replaceTo` / `popBack` emission shape
  and order on the singleton bus.
- `NavigationLifecycleRegressionTest` covers a stale-bridge → fresh-bridge handover.
  It verifies that the bus remains usable across detach / re-attach: commands
  emitted with no executor attached do not crash or block the bus, and commands
  emitted **after** a fresh executor subscribes are observed by that executor.
  The bus uses `MutableSharedFlow(replay = 0, extraBufferCapacity = 64)` and
  intentionally does not guarantee replay of commands emitted before subscription
  — the production bridge attaches via `LaunchedEffect(navController)` before any
  decision-side emit can happen for that composition, so pre-subscription emits
  are not part of the lifecycle contract.
- Per-feature `NavigationHandlerTest` classes verify each `Action.Navigation.<X>`
  branch dispatches the matching `navigator.*` call, with `Navigator` mocked.
- Per-feature route-arg Store tests (`feature/exercise`, `feature/live-workout`,
  `feature/single-training`) verify the `@Assisted screen` value lands in
  `state.value` initial fields.
- `app/dev/.../NavigationLifecycleRegressionTest.kt` (instrumented `@Regression`)
  recreates `MainActivity` mid-flight and asserts that subsequent bottom-bar
  navigation calls land on the correct destination through the freshly-bound
  bridge.

### Test gaps deferred to a follow-up (instrumentation)

The following scenarios are part of the manual QA checklist below but are NOT
yet automated because the `app/dev` instrumentation harness only navigates
within bottom-bar destinations — it has no helpers for seeding DB rows
(Exercise / Training / PerformedExercise) and no shared fixtures for
detail-screen → PlanEditor flows. Adding them would require new test
infrastructure comparable in size to the rest of this PR. **Trigger to act:**
next PR that adds a real-DB instrumentation fixture (similar to the
`RepositoryTestEnv` approach for unit tests).

| Scenario | Status |
|---|---|
| Exercise detail → PlanEditor save → previous screen reload exactly once | manual |
| SingleTraining → PlanEditor save → previous screen reload exactly once | manual |
| LiveWorkout → PlanEditor save → previous screen reload exactly once | manual |
| LiveWorkout finish session → `replaceTo` lands on PastSession; back does not return to finished LiveWorkout | manual |

Documented at [architecture.md → Navigation](architecture.md#navigation),
[lint-rules.md → HiltScopeRule scope expectations](lint-rules.md#scope-expectations-for-the-navigation-layer),
and the lifecycle-safe navigation refactor section in
[`refactor-with-mvi-rules`](../.claude/skills/refactor-with-mvi-rules.md).

---

## Domain model boundary — RESOLVED

Migrated in the domain-model-migration PR. Every feature now declares
its own `*Domain` types under `feature/<X>/domain/model/`; data → domain
mapping lives in `feature/<X>/domain/mapper/`; domain → ui mapping
lives in `feature/<X>/mvi/mapper/`. Sealed result types are extracted
to standalone files. The `DomainLayerPurityRule` and
`DomainLayerNoUiRule` Detekt rules guard the boundary at error
severity. See [architecture.md → Domain model
layer](architecture.md#domain-model-layer) for the convention.

---

## Backup integrations

| Severity | Location | Description |
|---|---|---|
| 🟢 | [core/data/backup/google-drive/.../auth/DriveAuthTokenProvider.kt](../core/data/backup/google-drive/src/main/kotlin/io/github/stslex/workeeper/core/data/backup/google_drive/auth/DriveAuthTokenProvider.kt) | **Token fetch caching.** Every Drive HTTP call invokes `AuthorizationClient.authorize().await()` to retrieve the access token. GMS likely caches internally but this is undocumented. **Trigger to revisit:** real-device measurement of 2nd+ `authorize().await()` calls. If consistently >200ms, add an in-memory access token cache with TTL ~50 minutes (access tokens live 60 minutes). **Mitigation effort if needed:** ~15 LoC + 1 unit test. |
| 🟡 | [feature/settings/.../domain/BackupInteractorImpl.kt restoreLatest](../feature/settings/src/main/kotlin/io/github/stslex/workeeper/feature/settings/domain/BackupInteractorImpl.kt) | **v1 restore is latest-only.** No picker UI; `restoreLatest()` always picks the first entry from `BackupStorage.listBackups()` (newest). `Action.Backup.RequestRestore` surfaces a single `RestoreConfirmationUi` for the latest. **Trigger to act:** v1.1 spec or first user request to roll back to an older backup. **Fix path:** add a picker bottom sheet driven from `RequestRestore`, list all summaries via a new domain query, and route selection back as `Action.Backup.ConfirmRestoreFor(remoteId)` (split out from `ConfirmRestore`). |
| ✅ RESOLVED | [feature/settings/.../domain/BackupInteractorImpl.kt](../feature/settings/src/main/kotlin/io/github/stslex/workeeper/feature/settings/domain/BackupInteractorImpl.kt), [feature/settings/.../domain/mapper/BackupDomainMapper.kt](../feature/settings/src/main/kotlin/io/github/stslex/workeeper/feature/settings/domain/mapper/BackupDomainMapper.kt) | **`BackupManifest` import workaround.** During PR 4 the `DomainLayerPurityRule` flagged `import core.data.backup.api.model.BackupManifest` in `BackupInteractorImpl`, so manifest construction was routed through a `BackupDomainMapper.buildManifest(...)` factory and the impl relied on type inference to avoid the import. The rule has since been extended to exempt `core.data.<feature>.api.*` submodules (see [lint-rules.md → DomainLayerPurityRule](lint-rules.md#domainlayerpurityrule)); the impl now imports `BackupManifest` directly and the factory has been removed. |
| 🟡 | [core/data/backup/google-drive/.../auth/DriveAuthTokenProvider.kt `refreshTokenFromGms`](../core/data/backup/google-drive/src/main/kotlin/io/github/stslex/workeeper/core/data/backup/google_drive/auth/DriveAuthTokenProvider.kt) | **Diagnostic Part-1 logging carried through Part-2 fix and the appProperties split.** `refreshTokenFromGms()` emits `Log.d("authorize result: hasResolution=…, tokenPresent=…, grantedScopes=…")` on every silent re-auth attempt to make cache-miss / refresh behaviour visible during verification of the token-cache fix and the per-field `appProperties` upload. **Trigger to act:** once upload + restore are confirmed working on a real device against Drive (single round-trip, no resolution loop, restore confirmation populated with date/size), drop the `Log.d` line and keep only the `Log.w("authorize() returned null token …")` warning for the actionable failure mode. |

---

## v2.0 Foundations Stage — closed entries

The v2.0 stage addressed the following items. They are listed here for traceability before they roll into the next audit cleanup.

- ✅ `feature/exercise/.../mvi/handler/ClickHandler.kt:163` Track now CTA stub replaced with a real flow that creates an ad-hoc training and opens Live workout via `SessionConflictResolver`.
- ✅ `feature/exercise/.../ui/ExerciseDetailScreen.kt` now renders `state.adhocPlanSummaryLabel` between the description and history sections.
- ✅ `LiveWorkoutInteractorImpl.finishSession` now delegates to `SessionRepository.finishSessionAtomic`, which wraps plan updates + state transition in a single `database.withTransaction { ... }`. `runCatching` + compensating-rollback removed.
- ✅ DAO unit tests added for `TrainingDao.pagedActiveWithStats`, `pagedActiveWithStatsByTags`, and `SessionDao.observeAnyActiveSession` plus the three new aggregation queries (`getPersonalRecord`, `getBestSessionVolumes`, `pagedHistoryByExercise`).
- ✅ Active session conflict modal (`core/ui/kit/.../ActiveSessionConflictDialog.kt`) shared by Home Start CTA, Training detail Start session, and Exercise detail Track now.
- ✅ Live workout overflow Delete session option + `DiscardSessionConfirmDialog` confirm flow.

---

## Resolved (kept for diff visibility, will be removed in next audit)

These were tracked as debt in earlier versions of this doc. Verified resolved by 2026-04-28 audit.

- ✅ `feature/all-trainings/.../ui/components/RelativeTimeFormatter.kt` — file deleted; logic now lives in `TrainingListItemMapper`.
- ✅ `feature/all-trainings/.../ui/AllTrainingsGraph.kt` blocked-name shaping — moved to `ClickHandler` with `ResourceWrapper`.
- ✅ `feature/all-exercises/.../ui/AllExercisesGraph.kt` blocked-name shaping — moved to `ClickHandler` with `ResourceWrapper`.
- ✅ `feature/exercise/.../ui/ExerciseEditScreen.kt` plan summary — `state.adhocPlanSummaryLabel` pre-formatted.
- ✅ `feature/exercise/.../ui/components/ExerciseHistoryRow.kt` date and sets — pre-formatted via `ExerciseUiMapper`.
- ✅ `feature/single-training/.../ui/components/TrainingHistoryRow.kt` date — pre-formatted via `CommonHandler`.
- ✅ `feature/live-workout/.../ui/components/LiveExerciseCard.kt` status-line — `exercise.statusLabel` pre-formatted in `LiveWorkoutMapper`.
- ✅ `feature/settings/.../ui/ArchiveGraph.kt` timestamp formatting — moved to `ArchiveUiMapper`. (Note: snackbar template substitution remains as a separate, smaller debt — see UI Mapping Boundary table above.)
