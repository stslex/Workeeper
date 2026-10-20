# KMP Phase 7.14 — feature:single-training becomes a shared feature entry

**Status:** COMPLETE — implemented by the PR whose merge brought this line to dev.

**Discovery baseline:** dev @ 53605e1265a4e34e75b2faa38058e5566bb9f0db (2026-10-19, merge of #339,
Phase 7.13). Its parents are `231739859` (the 7.13 spec commit) and `d8a00f331` (the 7.13 PR head).
`feature/single-training` last changed in `b0045adfa` (2026-10-08, PR-N's Native-legal test names);
no commit on dev follows #339.

**Template:** [Phase 7.13](kmp-phase-7-13-exercise-chart-feature.md). This document is a delta.
Every rule of 7.13 that this document does not change applies unchanged, read with the substitutions
of Section 0.3. Through 7.13, the rules of 7.12, 7.11, 7.10, 7.9, 7.8 and 7.7 that it inherits apply
too. Single-training has a route argument, so [Phase 7.11](kmp-phase-7-11-past-session-feature.md)
Section 6 is the precedent for its factory flow (shape B), as it was for 7.13.

**Measured by:** a static discovery of 2026-10-19 on git data, greps and scripts, with nothing
compiled or run. Its gate change, gate data and Python-scored controls were prototyped against a
simulated exit tree (Sections 12.0, 12.1 and 13). That prototype is not evidence; the
implementation's fresh runs are.

---

## 0. Authority, decisions, substitutions

### 0.1 Authority order

1. live origin/dev at the implementation base;
2. AGENTS.md, CLAUDE.md, documentation/architecture.md, testing.md, ci-cd.md,
   compose-state-discipline.md;
3. the Phase 7.13 specification (its Section 19 included), and through it 7.12 (its D3), 7.11 (its
   Section 6 for shape B), 7.10, 7.9, 7.8 (including its Section 19 decisions P1, P1a and G1–G4)
   and 7.7;
4. kmp-string-resolver.md (PR-R) as merged;
5. the checked-in source, resources, tests, PNGs and CI oracles at the implementation base.

### 0.2 Stable rules

7.13 Section 0.2 applies unchanged: the positive-gate flags after `./gradlew --stop`, quoted
`N actionable tasks: N executed`, fresh JUnit XML only, detekt and tests in separate invocations,
signed and GitHub-verified commits with `Co-Authored-By` as the only trailer, no session identifier
in a commit, the PR or a document, and no merge by the implementer.

Suppressions: **no new suppression of any kind.** The eight inherited production suppressions, in
seven files, stay byte-semantically unchanged:

- `@Suppress("UNCHECKED_CAST")` on the processor cast in di/SingleTrainingFeature.kt;
- `@Suppress("TooManyFunctions")` on the interface in domain/SingleTrainingInteractor.kt;
- `@Suppress("TooManyFunctions", "LongParameterList")` on domain/SingleTrainingInteractorImpl.kt;
- `@Suppress("TooManyFunctions", "LongMethod", "LargeClass")` on mvi/handler/ClickHandler.kt;
- `@Suppress("ComplexInterface")` and `@Suppress("MviActionNamingRule")` in
  mvi/store/SingleTrainingStore.kt;
- `@Suppress("LongMethod", "CyclomaticComplexMethod")` on `singleTrainingsGraph` in
  ui/SingleTrainingGraph.kt;
- `@Suppress("LongParameterList")` on `ExercisePickerSheet` in ui/components/ExercisePickerSheet.kt.

One inherited suppression is dead and leaves in commit 2 (S5): `@Suppress("unused")` on
`val acknowledged = picker` in mvi/handler/InputHandler.kt, a local kept only to silence the
warning its own binding causes. Inherited production suppressions go from 9 to 8. The module has
no test suppression, and Phase 7.14 needs no Native test-name suppression (Section 4.3).

One session does all the work. Every census, transcription and audit is a script that session
writes and runs, with its own parser. None is a separate agent.

### 0.3 Substitutions against 7.13

| 7.13 | 7.14 |
| --- | --- |
| `feature:exercise-chart`, `feature/exercise-chart` | `feature:single-training`, `feature/single-training` |
| package `io.github.stslex.workeeper.feature.exercise_chart` | `io.github.stslex.workeeper.feature.single_training` |
| resource package `…feature.exercise_chart.resources` | `io.github.stslex.workeeper.feature.single_training.resources` |
| `ExerciseChartFeature`, `ExerciseChartGraph`, `exerciseChartGraph` | `SingleTrainingFeature`, `SingleTrainingGraph`, `singleTrainingsGraph` (the plural name stays) |
| `exerciseChartGraphFactory`, `createExerciseChartGraph(screen)`, `exerciseChartStore` | `singleTrainingGraphFactory`, `createSingleTrainingGraph(screen)`, `singleTrainingStore` |
| `ExerciseChartStoreImpl`, `ExerciseChartExtensionIdentityTest` | `SingleTrainingStoreImpl`, `SingleTrainingExtensionIdentityTest` |
| route `Screen.ExerciseChart(exerciseUuid: String?)` | `Screen.Training(uuid: String?)`; shape B, `create_args = "screen"`, unchanged |
| graph tag `ExerciseChartGraph` | `SingleTrainingGraph` |
| `EXERCISE_CHART_ENTRY` | `SINGLE_TRAINING_ENTRY` |
| thirteen-module Native command | fourteen modules (Section 12.1) |
| 7.13's D5: bare previews and `formatDayMonth` in the generic gate (commit 1) | D4 and D5: extra golden classes, and same-file copy helpers (commit 1, Section 12.0) |
| 7.13's F1 and D3: a gesture's copy, resolved once per load | F1 and D2: four click paths' copy, constants once per Store, arguments on use (Section 5.2) |
| 7.13's D2: the `ChartLoader` handler seam | none needed: no handler injects another (Section 5.4) |
| 7.13's S4: `ResourceWrapper.formatDayMonth`, one call | S1: `ResourceWrapper.formatMediumDate`, one call |
| 7.13's D4: a parity suite on androidHostTest only | none: every unit suite is portable |
| 7.13's S1: the `java.time` port | none: the module names no `java.` at the base |

### 0.4 Decisions

Locked by the maintainer's GO (2026-10-19):

| Id | Decision |
| --- | --- |
| D1 | `feature:single-training` is the next shared entry: no Android or Java import, every dependency already KMP, its one production `core:data:database` edge has plan-editor's precedent, and it is 7.13's roadmap order (Section 2). |
| D2 | Copy on the four click paths (option B, Section 5.2): `Init` resolves the editor's constant copy — the no-exercises error and the two undo toasts — once per Store into `State.editCopy`, and the save, exercise-remove and set-remove paths read it synchronously. The permanent-delete confirm, whose title takes the training's name, resolves on use in a launch, and its write is a compare-and-set `updateState` that replaces `DialogState.DetailMenu` only. |
| D3 | F2's two State lambdas are rewritten (option A, Section 5.3): `applySnapshotOrPop` is inlined into the discard's lambda, and `applyLoaded` is split as F2 requires. No gate change for them. |
| D4 | The topology gate gains, as this PR's **first** commit, an additive `extra_golden_tests` field (path → exact methods, default empty) checked like `golden_test`, and a completeness walk: every androidHostTest file that renders goldens is `golden_test` or declared (Section 12.0). |
| D5 | The same commit adds a check that a State lambda in an entry's handler file calls no function of its own file whose text, directly or through other same-file functions, holds a State-lambda side-effect token or names the resolver or the wrapper (Section 12.0). D4 and D5 are proven not to change any existing behaviour. |

Specification decisions, delivered with the discovery note and accepted by the GO. The maintainer
also accepts this document's wording of them, and S14, by starting the implementation (Section 18):

| Id | Decision |
| --- | --- |
| S1 | The history rows' date stays on `ResourceWrapper.formatMediumDate` (past-session's precedent): exactly one call, in `CommonHandler`'s `toHistoryItems`, declared through `resource_wrapper_dates`. `CommonHandler` is the module's only `ResourceWrapper` reader; `ClickHandler` moves wholly to `StringResolver` (Section 5.4). |
| S2 | P1: the plural carries a bare `%d`, so its 6 items (EN 2, RU 4) become `%1$d` (Section 3.4). |
| S3 | The three `uiMode` preview pairs become ThemeMode subjects, home's `RecentSessionRow` shape; the two bare previews stay as written and are pinned by 7.13's `bare_previews` (Section 5.5). |
| S4 | The platform rows of Section 4.2: CMP's `BackHandler` under `ExperimentalComposeUiApi`; `kotlin.time.Clock`; `@VisibleForTesting` dropped; the Feature takes the factory by constructor; the private `Res`, imported as `SingleTrainingRes` in the two files that also read kit's `Res`; `cmp.material.icons.extended` for `Search` and `DragHandle`. |
| S5 | Dead code leaves in commit 2: InputHandler's `@Suppress("unused") val acknowledged = picker` (an `is` check replaces the binding) and the two catalog keys nothing reads, in both locales (39 → 37 strings). `SingleTrainingInteractor.getLabels`, which has no caller, stays (Section 17). |
| S6 | The four suites move to commonTest on `kotlin.test` with private in-file fakes (the 7.12 and 7.13 pattern). The store fakes run a launch only in the identities that run one today and in those S14 adds, as Section 11.1 lists per identity. |
| S7 | Both golden classes move to androidHostTest as they are, and their 12 PNGs by `git mv` with the blob set unchanged. Every golden renders `LOCALE_RU`, so after the move they are the runtime gate on CMP's RU strings and the first RU CMP plural in a golden: any PNG change is a STOP (Section 11.2). |
| S8 | The device test moves to androidDeviceTest. The device gate after the move runs it (3 cases) and the whole `RouteReachabilityTest` class (15 cases), its two single-training journeys among them (Section 11.3). |
| S9 | The identity test reaches the graph through `AppRootDeps`' `singleTrainingGraphFactory` instead of `asContribution`; its 6 identities keep their names and assertions (Section 6). |
| S10 | The move commit moves `FEATURE_RESOURCE_OWNERS`' single-training root from `src/main/res` to `src/commonMain/composeResources`, so every commit stays green (Section 12.1). |
| S11 | Five signed commits: D4 and D5 with their proof; the port in `src/main` (D2, D3 with F2's split, S5, the clock, S14) with the JUnit suites green on the JVM first; the module move; the gate entry, oracle and workflow at 14 modules; the docs (Section 15). |
| S12 | One iOS scene: the whole private EN catalog and the handlers' formatted reads through the real `ComposeStringResolver`; the read screen, the editor (text input via `SetText`, 7.11's S5), the exercise picker and the menu content from test-held state; dispatch as exact ordered lists; no reorder drag (Section 11.4). |
| S13 | The docs commit re-points tech-debt.md's single-training rows, records D2's template and the inherited tag race, and updates architecture.md, ci-cd.md and testing.md (Section 9.2). |
| S14 | Commit 2 adds four identities that pin D2's new behaviour, which no existing identity observes (Section 11.1): three in ClickHandlerTest, one in CommonHandlerTest. They are the first identities these phases add to a ported suite; every existing identity keeps its name and assertions. Portable identities go from 58 to 62. |

Inherited and unchanged:

- P1: a bare `%d` in a migrated catalog becomes positional;
- P1a: the placeholder rule;
- D4 of 7.10: `.claude/skills/` stays untouched;
- 7.12's D3: the State-lambda resolver-reference check, which now also runs over this entry;
- 7.13's D5: the `bare_previews` field, which this entry declares.

**Required, not a decision — F1.** Four `ClickHandler` paths resolve copy synchronously through
`ResourceWrapper` today:

- `processPermanentDeleteMenu` resolves four strings (the title formats the training's name) before
  one `updateState` that replaces `DetailMenu` with `PermanentDeleteConfirm`;
- `processSaveClick` resolves the no-exercises error into `Event.ShowSaveError`;
- `processExerciseRemove` and `processExercisePlanAction` write the draft, then send their undo
  toast with a resolved message; the latter reads `draftEpoch` after its write.

`StringResolver` is suspend-only, so a mechanical port would put a launch into each path: the toast
or dialog would land one dispatch later, anything read inside the launch (`draftEpoch`) could belong
to a newer draft, and a menu dismissed in that window would be reopened by the dialog's write. D2
settles all four (Section 5.2).

**Required, not a decision — F2.** Two State lambdas return a helper's State: `ClickHandler`'s
discard (`updateState { latest -> latest.applySnapshotOrPop() }`) and `CommonHandler`'s load
(`updateState { current -> current.applyLoaded(result) }`). Both helpers return `copy(…)`, but the
gate wants `.copy(` in the lambda body, so both fail as an entry. `applyLoaded` also builds the
history rows through `toHistoryItems()`, which calls `resourceWrapper.formatMediumDate`: a wrapper
call inside a compare-and-set lambda that re-runs on contention, which neither the token list nor
7.12's D3 pattern sees (D5 now does). The exercise items and the history rows are built before the
write, in `onSuccess`, where they run today; the lambda keeps only what reads `current` — the tag
match against `availableTags` and the snapshot built from it — and its `.copy(`.
`applySnapshotOrPop` is pure and inlines. Behaviour is unchanged, except that the date no longer
re-runs on a retry (Section 5.3).

## 1. Bounded exit claim

The implementation may prove only this:

- feature:single-training applies `convention.kmpComposeLibrary` and retains Metro and Paparazzi;
- all 39 production Kotlin files of Section 3.1 compile from commonMain for Android,
  iosSimulatorArm64 and iosArm64; D2's copy holder lives in an existing file;
- the exact private 38-identifier EN/RU catalog (37 strings and 1 plural) lives in commonMain
  Compose Resources, changed only by S5's two removed keys and P1's 6 positional placeholders
  (Section 3.4);
- every visible Android behaviour is unchanged, except the inherited Compose-resources locale
  property (Section 5.7) and D2's two differences (Section 5.2);
- copy outside composition resolves through the injected `StringResolver`, never through CMP's
  top-level `getString` / `getPluralString`, and `ResourceWrapper` keeps exactly one read: the
  history rows' medium date, in `CommonHandler` (S1);
- no click path suspends for copy: the save error and both undo toasts read `State.editCopy`,
  which `Init` resolves once per Store, and the permanent-delete confirm resolves on use and lands
  only on the menu it replaces (D2);
- no State lambda of a handler resolves copy, references a resolver, or calls a same-file function
  that does (7.12's D3 check and this phase's D5), and every one returns a `.copy(` (D3, F2);
- 4 portable suites with 62 exact identities execute on Android host and on Native, the first
  Cyrillic identity on Native among them;
- the two golden suites execute as 12 Android-host Paparazzi cases (6 methods × light/dark)
  against the same PNG blobs, rendering the module's RU catalog through CMP;
- the device suite executes its 3 identities from androidDeviceTest;
- one deterministic iOS production scene proves the whole catalog and the handlers' formatted reads
  through the real `ComposeStringResolver`, the read screen, the editor, the exercise picker, the
  menu content and action dispatch, text input included;
- the Metro graph is reached through the explicit generation-owned factory (shape B), never
  `LocalContext` or `Context.appDeps`; all six extension identities use the accessor;
- the topology gate checks the entry through one `FeatureEntry` after D4's and D5's additive
  change, both golden classes pinned; `Context.appDeps` readers drop from 5 to 4;
- `core:data:database` stays one production edge, read by the domain mapper alone;
- Android remains releasable with every repository PNG blob unchanged.

This is a source-set, dependency, resource, test and composition-root migration, plus D2's copy
model and F2's split. It does not permit:

- redesigning the read screen, the editor, the cards, the history list, the picker, the menu or any
  sheet;
- changing save, discard, undo, reorder, picker, tag, archive, permanent-delete, start-session or
  loading behaviour beyond D2's two differences;
- closing the inherited tag race of Section 17;
- changing `core:ui:kit`, `core:ui:plan-editor`, `core:ui:navigation`, `core:ui:mvi`, `core:core`,
  `core:data:exercise` or `core:data:database`;
- adding a device test identity;
- adding an iOS app;
- migrating another feature.

## 2. Candidate selection — fresh census

Measured at the discovery baseline with 7.9's method (production = `src/main/kotlin`, unit =
`src/test/kotlin`, EN string keys = `<string` elements of `values/strings.xml`, PNGs = files under
`src/test/snapshots`). No unported module changed since `5de43879a`, so every row equals 7.13's.

| Feature | Prod files | Prod lines | Unit files | Unit lines | EN string keys | Plurals | PNGs | `android.*` | `java.*` | Golden classes |
| --- | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: |
| **single-training** | 39 | **3,635** | 6 | 1,582 | 39 | 1 | 12 | 0 | 0 | 2 |
| exercise | 46 | 4,141 | 13 | 2,890 | 53 | 1 | 48 | 10 | 1 | 3 |
| live-workout | 59 | 6,755 | 27 | 7,903 | 96 | 2 | 60 | 1 | 0 | 4 |
| settings | 51 | 3,326 | 12 | 2,638 | 65 | 3 | 12 | 11 | 4 | 1 |

single-training's three fully-qualified `android.content.res.Configuration` uses are preview
`uiMode`s (S3). Settings stays blocked: `feature:app-dialogs:api` is `convention.androidLibrary`,
and Drive sign-in, `Formatter`, `DateUtils` and `java.text.DateFormat` need seams.

State lambdas, scored by the gate's own `braced_call_bodies`, `.copy(` rule, token list and 7.12's
D3 resolver pattern applied to `src/main` as if each module were an entry, plus D5's same-file
helper check as Section 12.0 specifies it:

| Feature | Lambdas | Without `.copy(` | Token / resolver findings | Call a same-file copy helper (D5) |
| --- | ---: | ---: | --- | ---: |
| **single-training** | 43 | 2 | 0 | 1 (`applyLoaded`) |
| exercise | 50 | 1 | 0 | 1 (`applyLoaded`) |
| live-workout | 61 | 11 | 4 lambdas resolve copy | 0 |
| settings | 43 | 0 | 0 | 0 |

D5's check finds nothing in the six shared entries at the base. After commit 2, single-training has
44 lambdas (D2's `resolveEditCopy` adds one), none without `.copy(`, and no finding of any rule.

Every module single-training depends on is already KMP: core:core, core:ui:kit, core:ui:mvi,
core:ui:navigation, core:ui:plan-editor, core:data:exercise and core:data:database. Every symbol its
production code imports from them is declared in commonMain or generated by CMP into it, with two
notes:

- `DefaultDispatcher` and `MainImmediateDispatcher` are commonMain qualifiers with platform
  actuals; core:ui:mvi's `StoreDispatchers` already reads the second in common code;
- `core.ui.mvi.di.appDeps` lives in androidMain and leaves with the factory flow (Section 6).

The `core:data:database` edge is production code, not a test fixture: `SingleTrainingDomainMapper`
maps `PlanSetDataModel` and `SetTypeDataModel`, as feature:plan-editor's `PlanEditorDomainMapper`
does under its commonMain `implementation` edge. No test reads `core:data:database` or
`core:data:database-test`.

Every androidx.compose import of the module is Compose-Multiplatform common, except the four rows of
Section 4.2: `androidx.activity.compose.BackHandler`, `androidx.compose.ui.res.stringResource` (six
files) and `pluralStringResource` (one), and the material icons `Icons.Default.Search` and
`Icons.Filled.DragHandle`, which come from CMP's icons-extended artifact as past-session's and
home's do. `navigationBarsPadding()` on the detail dock is CMP-common; it is the first in a shared
feature, a sibling of image-viewer's `systemBarsPadding()`. There is no `rememberSaveable`,
Parcelable or `nativeCanvas`. The 7.12 scan list (`.format(`, `Locale`, `synchronized`, `@Volatile`,
`Atomic*`, `putIfAbsent`, `getOrDefault`, `computeIfAbsent`, `removeIf`, `toSortedSet`,
`toSortedMap`, `@Jvm*`, `Thread`, `TimeUnit`) finds nothing; `System.currentTimeMillis()` (once) and
`@VisibleForTesting` (once) are Section 4.2's rows.

## 3. Measured baseline

Static measurements at the discovery baseline. Test names are given as the base leaves them, plus
S14's four, marked. The module tracks 61 paths: the build file, 39 production files, 2 catalogs, 6
unit-test files (4 portable suites and 2 golden suites), 12 PNGs and 1 device test.

### 3.1 Production manifest

All 39 files move from `src/main/kotlin/io/github/stslex/workeeper/feature/single_training/` to
`src/commonMain/kotlin/io/github/stslex/workeeper/feature/single_training/` with unchanged relative
paths. No file is added: D2's `TrainingEditCopy` lives in `mvi/model/SingleTrainingUiModels.kt`.

| File | Lines |
| --- | ---: |
| di/SingleTrainingFeature.kt | 37 |
| di/SingleTrainingGraph.kt | 49 |
| di/SingleTrainingHandlerStore.kt | 9 |
| di/SingleTrainingHandlerStoreImpl.kt | 14 |
| di/SingleTrainingScope.kt | 9 |
| domain/SingleTrainingInteractor.kt | 61 |
| domain/SingleTrainingInteractorImpl.kt | 180 |
| domain/mapper/SingleTrainingDomainMapper.kt | 128 |
| domain/model/ActiveSessionDomain.kt | 8 |
| domain/model/ArchiveResult.kt | 9 |
| domain/model/ExerciseDomain.kt | 10 |
| domain/model/ExercisePlanDomain.kt | 8 |
| domain/model/ExerciseTypeDomain.kt | 7 |
| domain/model/PickerExercise.kt | 7 |
| domain/model/PlanSetDomain.kt | 8 |
| domain/model/SessionDomain.kt | 10 |
| domain/model/SessionStateDomain.kt | 7 |
| domain/model/SetTypeDomain.kt | 9 |
| domain/model/StartSessionConflict.kt | 15 |
| domain/model/TagDomain.kt | 7 |
| domain/model/TrainingChangeDomain.kt | 13 |
| domain/model/TrainingDomain.kt | 14 |
| domain/model/TrainingExerciseDetail.kt | 9 |
| mvi/handler/ClickHandler.kt | 774 |
| mvi/handler/CommonHandler.kt | 157 |
| mvi/handler/InputHandler.kt | 77 |
| mvi/handler/NavigationHandler.kt | 34 |
| mvi/mapper/TagUiMapper.kt | 52 |
| mvi/model/SingleTrainingUiModels.kt | 38 |
| mvi/store/DialogState.kt | 40 |
| mvi/store/SingleTrainingStore.kt | 320 |
| mvi/store/SingleTrainingStoreImpl.kt | 63 |
| ui/SingleTrainingGraph.kt | 194 |
| ui/TrainingDetailScreen.kt | 383 |
| ui/TrainingEditScreen.kt | 252 |
| ui/components/ExercisePickerSheet.kt | 209 |
| ui/components/TrainingDetailMenuSheetContent.kt | 55 |
| ui/components/TrainingExerciseCard.kt | 274 |
| ui/components/TrainingHistoryRow.kt | 85 |
| **39 files** | **3,635** |

The production suppressions of Section 0.2 sit at SingleTrainingFeature.kt line 27,
SingleTrainingInteractor.kt 16, SingleTrainingInteractorImpl.kt 32, ClickHandler.kt 41,
SingleTrainingStore.kt 157 and 232, ui/SingleTrainingGraph.kt 38 and ExercisePickerSheet.kt 44;
InputHandler.kt line 74 leaves (S5).

Android coupling in production:

- `R.*` at 39 references in seven files, over 38 of the catalog's 40 identifiers; the other two have
  no reader anywhere in the repository since 2026-04-27, hidden from Android Lint by the EN root's
  `tools:ignore="UnusedResources"` (S5):
  - five UI files read 27 identifiers (26 strings and the plural) at 28 references, through
    Android's `stringResource` and `pluralStringResource`: `ui/TrainingDetailScreen.kt` 8 (seven
    strings and the plural), `ui/TrainingEditScreen.kt` 10, `ui/components/ExercisePickerSheet.kt`
    5, `ui/components/TrainingDetailMenuSheetContent.kt` 2 and
    `ui/components/TrainingExerciseCard.kt`
    3. `feature_training_edit_label_description` is read by both screens, and two calls pick between
    two identifiers (the editor's create/edit title, the dock's start/resume label), so the 28
    references sit at 26 call sites;
  - `mvi/handler/ClickHandler.kt` reads 11 identifiers at 11 `resourceWrapper.getString` sites:
    7 on the four synchronous paths of F1 (the permanent-delete confirm's four, the no-exercises
    error, the two undo toasts) and 4 inside launches (the archive success with the name, the
    archive block, the conflict's unnamed fallback and its progress with two counts);
- `ResourceWrapper` in two files: ClickHandler's 11 `getString`, and CommonHandler's one
  `formatMediumDate`, which runs inside the load's State lambda today (F2);
- `androidx.compose.ui.res.stringResource` imported in six files. The five UI files above hold 28
  `stringResource(` calls (TrainingDetailScreen 7, TrainingEditScreen 11, ExercisePickerSheet 5,
  TrainingDetailMenuSheetContent 2, TrainingExerciseCard 3) and TrainingDetailScreen one
  `pluralStringResource(`. Three of the 28 read kit's Compose `Res` through CMP's overload, which
  both screens import beside Android's (`core_ui_kit_action_back`; `core_ui_kit_action_cancel` and
  `core_ui_kit_action_save`). `ui/SingleTrainingGraph.kt` imports Android's `stringResource` but
  calls only CMP's, six times, all on kit's `Res`. Kit's `Res` is read at 9 sites over 8 keys;
- `androidx.activity.compose.BackHandler` once, in the graph;
- `LocalContext` with `appDeps` in di/SingleTrainingFeature.kt;
- `@VisibleForTesting` on `SingleTrainingStoreImpl.NAME`;
- `System.currentTimeMillis()` once, the timestamp of `processSaveClick`'s `TrainingChangeDomain`;
- three `uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES` preview pairs:
  `TrainingHistoryRowPreview`, `ExercisePickerSheetPreview` and `TrainingExerciseCardPreview`;
- `Icons.Default.Search` (ExercisePickerSheet) and `Icons.Filled.DragHandle` (TrainingExerciseCard).

Already portable:

- the two bare `@Preview`s, `TrainingDetailScreenPreview` and
  `TrainingDetailMenuSheetContentPreview`, each over `AppTheme(themeMode = ThemeMode.DARK)`;
- every other icon, from kit's `AppIcons`;
- `kotlin.uuid.Uuid.random()`, kit's reorderable column and plan-editor's `PlanEditorBody`;
- `ExercisePickerSheet` and the menu sheet's `AppBottomSheet`, which home's scene already composes
  on the simulator (home's `TrainingPickerSheet`).

State: 21 fields at the base (the gate's own regex); D2 adds `editCopy` as the 21st of 22, before
`isLoading`. `DialogState` carries pre-resolved strings (`PermanentDeleteConfirm`: title, body,
impact, confirm label; `ActiveSessionConflict`: session, name, progress). No resource handle or id
lives in State, the models or the domain. `PickerExerciseItem.tagsLabel` keeps its
`TODO(tech-debt-localization)` getter. `launch` runs the action on Default and `onSuccess` /
`onError` on Main.immediate (`AppCoroutineScopeImpl`). No handler injects another handler.

### 3.2 Portable manifest — 4 suites, 58 identities, 62 after commit 2

Each suite moves from `src/test/kotlin/…/feature/single_training/` to
`src/commonTest/kotlin/…/feature/single_training/` with its relative path. **(S14)** marks the four
identities commit 2 adds (Section 11.1).

**mvi/handler/ClickHandlerTest.kt — 47, then 50**

- OnSaveClick with blank name flips nameError — reachable
- OnSaveClick with empty exercises emits ShowSaveError — reachable
- OnSaveClick with a name and exercises raises neither error
- OnEditClick flips into Edit mode
- OnPickerConfirm opens the first inserted card only
- OnExerciseCardToggle expands the tapped card
- OnExerciseCardToggle on the open card collapses it
- cards expand independently
- OnExercisePlanAction adds a set to the addressed exercise and refreshes its summary
- OnExercisePlanAction removing the last set normalizes to null
- a plan edit flips hasChanges through the snapshot signature
- OnExerciseRemove removes the exercise and reindexes positions
- OnExerciseReorder swaps positions on a non-trivial move
- OnExerciseReorder with from equal to to is a no-op
- OnAddExerciseClick emits haptic
- OnConflictDismiss clears the active session conflict dialog
- OnStartSessionClick reports the persisted active session progress
- OnConflictResume consumes OpenLiveWorkout with active session uuid
- OnBackClick closes the dialog before propagating to navigation
- entering the editor after a save collapses every card
- a refreshed exercise name is not an unsaved change
- OnConfirmDiscard restores an exercise the edit removed
- OnConfirmDiscard restores the positions a reorder changed
- OnConfirmDiscard restores the plan a card edited
- remove emits the undo toast and the undo restores item position and expansion
- minus set in a card emits the undo toast and the undo restores the row
- a stale set undo after save flipped to read edits nothing
- a stale set undo does not edit a re-entered draft
- a stale exercise undo after save flipped to read edits nothing
- a stale exercise undo does not edit a re-entered draft
- an undo during the save's write edits nothing
- a failed save re-arms the draft's undos
- a cancel during the save's write raises nothing and reverts nothing
- a confirmed discard during the save's write reverts nothing
- Edit entry resets a stuck isSaving
- createTag resolving to an already-selected tag does not duplicate the chip
- a set undo whose card was already removed waits for the card
- stashed set restores die with the draft
- a picker re-add discards the stash for the returned card
- an exercise undo that finds its card already back discards the stash
- a late picker resolution does not duplicate a card the undo restored
- the dashed add chip opens the tag picker sheet
- dismissing the tag picker clears the query with the sheet
- the topbar menu opens as the DetailMenu sheet
- dismissing the menu hides it
- archive closes the menu before its result lands
- permanent delete replaces the menu with its confirm in one write
- a menu dismissed before the confirm's copy lands stays dismissed **(S14)**
- the save error carries the copy Init resolved **(S14)**
- the undo toasts carry the copy Init resolved **(S14)**

**mvi/handler/CommonHandlerTest.kt — 4, then 5**

- a load that throws clears isLoading or the route is composed on nothing forever
- a training that is not there clears isLoading too
- the История count is the total not the visible page
- a create route never loads so it is never withheld and it opens clean
- Init resolves the editor's constant copy into State **(S14)**

**mvi/handler/NavigationHandlerTest.kt — 5**

- Back triggers popBack
- OpenExerciseDetail navigates to Screen Exercise
- OpenSession navigates to Screen PastSession
- OpenLiveWorkout navigates to Screen LiveWorkout with both uuids
- OpenLiveWorkout passes a null sessionUuid to LiveWorkout when blank

**mvi/store/SingleTrainingStateRouteArgTest.kt — 2**

- null uuid yields create-mode initial state with no loading
- non-null uuid yields read-mode initial state and starts loading

Suite distribution: 47/4/5/2 at the base, 50/5/5/2 after commit 2. Counted by `@Test` and by the
gate's name regex alike; there is no `@ParameterizedTest`, `@Nested` or `@DisplayName` outside the
goldens, so JUnit cases equal identities.

Today's test doubles:

- JUnit 5 in all six unit suites; assertions are `assertEquals`, `assertTrue`, `assertFalse`,
  `assertNotNull` and `assertNull` only;
- MockK in three suites:
  - ClickHandlerTest mocks a relaxed `SingleTrainingInteractor` (stubbed with `coEvery` where an
    identity needs an answer) and a relaxed `ResourceWrapper`; its relaxed store answers `state`
    from the identity's `MutableStateFlow`, applies `updateState` and the update-lambda
    `updateStateImmediate`, records `sendEvent` and `consume`, and answers `launch` with a relaxed
    `Job` without running anything. Seven identities re-stub `launch` (listed in Section 11.1);
  - CommonHandlerTest mocks a relaxed interactor whose two flows are stubbed, and a relaxed
    `ResourceWrapper`; its store applies both State lambdas and answers `launch` by running the
    action under `runBlocking` inside `supervisorScope`, then `onSuccess`, routing any throw to
    `onError`. Its `Flow.launch` is the relaxed default, so the two observed flows are never
    collected. One identity answers `getTraining` with a relaxed `mockk()` of `TrainingDomain`, a
    data class;
  - NavigationHandlerTest mocks `Navigator` relaxed and verifies `popBack()` in one identity and
    `navTo(…)` in the other four;
- SingleTrainingStateRouteArgTest is pure.

Abstract members, counted from each type itself: `SingleTrainingInteractor` 16 (`getLabels` has no
caller), `HandlerStore` 12, `Navigator` 6, `ResourceWrapper` 5, `StringResolver` 2. There is no
interactor suite, so no repository fake and no `core:data:database` edge is needed in any test set.

One identity asserts copy: `OnStartSessionClick reports the persisted active session progress`
reads `1 of 2 exercises done` from a `ResourceWrapper` stub keyed by the progress identifier and its
two counts; every other message the suites see is the relaxed mock's `""`.

After the port, 8 identities reach `StringResolver`: in ClickHandlerTest the conflict progress
identity (one read with two arguments) and the two permanent-delete identities (four reads each, the
title with the name); in CommonHandlerTest all five, through `Init`'s three edit-copy reads. No
identity reaches `formatMediumDate`: no load in any suite returns a finished session.

### 3.3 Golden and device ownership

`golden/TrainingDetailGoldenTest.kt` has 3 methods parameterized over `GoldenTheme`, in this order:

    detailWithPlans, detailNoPlanExercise, detailEmptyHistory

`golden/TrainingEditGoldenTest.kt` has 3:

    editCollapsed, editExpanded, editEmpty

That is 12 render cases and 12 PNGs, all mode 100644 and 12 distinct blobs, named
`io.github.stslex.workeeper.feature.single_training.golden_<Class>_<method>_<dark|light>.png`.

Every method renders `locale = LOCALE_RU`, and the goldens compose the production screens, so the
PNGs pin the module's RU copy: the read screen's heads (`УПРАЖНЕНИЯ`, `ОПИСАНИЕ`, `ИСТОРИЯ`), its
dock (`Изменить`, `Начать сессию`) and the history head's `2 СЕССИИ` — the plural's `few` form, from
`historyCount = 2` in two of the three detail methods — and the editor's labels, counts, `плана пока
нет` line and title fallback (`Изменить тренировку` in `editEmpty`). The fixtures carry their names,
tags, plan summaries and dates as literal strings, so no handler or resolver reaches a golden. After
the move the same text comes through CMP's `stringResource` and `pluralStringResource`. Kit's and
start-mode's goldens already render their CMP catalogs in RU under Paparazzi (kit's `+ тег`), but no
golden has rendered a CMP plural in RU yet (S7).

`SingleTrainingScreenTest` (`src/androidTest`) has 3 identities under a class-level `@Smoke`:

    cardHead_tapTogglesExpansion, cardHead_removeIsNotSwallowedByTheHeadTap,
    expandedCard_showsThePlanBodyWithItsSetbar

It finds nodes by test tag only and reads no copy. Its edges are `androidTestImplementation` of
`libs.bundles.android.test`, `libs.androidx.compose.ui.test.junit4` and `:core:ui:test-utils`, and
`debugImplementation` of the test manifest.

### 3.4 Exact feature-local resource catalog

At exit each locale owns 37 strings and 1 plural, 38 identifiers, in this file order, with the same
keys in the same order in both locales. Values are shown as they must be at exit, decoded the way
the topology gate compares them (DOM text). The baseline differs from them in two ways only: S5's
two dead strings (`feature_training_detail_other_session_active_format` between rows 14 and 15,
and `feature_training_detail_active_pill` after row 16, in both locales) leave in commit 2, and P1's
6 plural items that carry a bare `%d` (EN 2, RU 4) become `%1$d` in commit 3.

| # | Identifier (all `feature_training_…`) | EN | RU |
| ---: | --- | --- | --- |
| 1 | detail_more | More | Ещё |
| 2 | detail_exercises | Exercises | Упражнения |
| 3 | detail_past_sessions | Past sessions | История |
| 4 | detail_start_session | Start session | Начать сессию |
| 5 | detail_resume_session | Resume session | Продолжить сессию |
| 6 | detail_edit | Edit | Изменить |
| 7 | detail_archive | Archive | В архив |
| 8 | detail_archive_blocked | Cannot archive — finish active session first | Нельзя архивировать — завершите активную сессию |
| 9 | detail_archive_success_format | ‘%1$s’ archived | «%1$s» в архиве |
| 10 | detail_permanent_delete | Delete permanently | Удалить навсегда |
| 11 | detail_permanent_delete_title | Delete ‘%1$s’ permanently? | Удалить «%1$s» навсегда? |
| 12 | detail_permanent_delete_body | This training has no history. This cannot be undone. | У тренировки нет истории. Это нельзя отменить. |
| 13 | detail_permanent_delete_impact | No history affected | История не пострадает |
| 14 | detail_permanent_delete_confirm | Delete | Удалить |
| 15 | detail_conflict_unnamed | Workout | Тренировка |
| 16 | detail_conflict_progress_format | %1$d of %2$d exercises done | %1$d из %2$d упражнений выполнено |
| 17 | detail_session_count | plural, below | plural, below |
| 18 | edit_close | Close | Закрыть |
| 19 | edit_title_create | New training | Новая тренировка |
| 20 | edit_title_edit | Edit training | Изменить тренировку |
| 21 | edit_label_name | Name | Название |
| 22 | edit_label_description | Description | Описание |
| 23 | edit_placeholder_description | Optional notes — focus, week, etc. | Заметки — фокус, неделя и т.д. |
| 24 | edit_label_tags | Tags | Теги |
| 25 | edit_label_exercises_format | Exercises (%1$d) | Упражнения (%1$d) |
| 26 | edit_add_exercise | Add exercise | Добавить упражнение |
| 27 | edit_exercise_removed | Exercise removed from training | Упражнение убрано из тренировки |
| 28 | edit_no_plan | no plan yet | плана пока нет |
| 29 | edit_plan_set_removed | Set removed | Подход удалён |
| 30 | edit_remove_exercise | Remove exercise | Удалить упражнение |
| 31 | edit_drag_handle | Reorder | Переместить |
| 32 | edit_error_name_required | Name is required | Введите название |
| 33 | edit_error_no_exercises | Add at least one exercise | Добавьте хотя бы одно упражнение |
| 34 | picker_title | Add exercises | Добавить упражнения |
| 35 | picker_search_placeholder | Search exercises… | Поиск упражнений… |
| 36 | picker_add_format | Add (%1$d) | Добавить (%1$d) |
| 37 | picker_cancel | Cancel | Отмена |
| 38 | picker_empty | No exercises match | Ничего не найдено |

`‘ ’` are U+2018 and U+2019 in EN rows 9 and 11, and `« »` the RU counterparts; `—` is U+2014 in
rows 8 and 23, and `…` U+2026 in row 35, in both locales.

| Plural | EN one / other | RU one / few / many / other |
| --- | --- | --- |
| detail_session_count | `%1$d session` / `%1$d sessions` | `%1$d сессия` / `%1$d сессии` / `%1$d сессий` / `%1$d сессии` |

The XML moves byte-semantically, except S5 and P1: the EN root keeps its `xmlns:tools` and
`tools:ignore="UnusedResources"`, row 16 keeps its element-level `tools:ignore="PluralsCandidate"`
in EN, the RU root keeps its namespace, every comment and blank line may stay, and there is no
escape. Compose Resources ignore `tools:` attributes, as all-exercises' and home's catalogs already
show.

At exit, composition reads 27 identifiers (26 strings and the plural) and the two handlers read the
other 11 outside composition (Section 5.4): ClickHandler 8 (rows 8, 9, 11–16), CommonHandler 3
(rows 27, 29 and 33, D2). Nothing outside the module reads these identifiers; only `graphify-out/`'s
generated graph and historical specs name some of them as text.
`FEATURE_RESOURCE_OWNERS` pins row 29 (`feature_training_edit_plan_set_removed`) at the module's
resource root (S10). The generated `Res` stays private.

### 3.5 Declared dependencies

Today's build file declares:

- `convention.composeLibrary`, Metro (`includeJavax()`, which keeps `@DefaultDispatcher` and
  `@MainImmediateDispatcher` distinct as binding keys) and Paparazzi;
- `implementation` edges on `core:core`, `core:ui:kit`, `core:ui:mvi`, `core:ui:navigation`,
  `core:ui:plan-editor`, `core:data:database` and `core:data:exercise`;
- `testImplementation` of `kotlin("test")` and `:core:ui:golden-harness`;
- the device edges of Section 3.3.

The `core:data:database` edge is production (Section 2) and stays, in commonMain. No test needs a
`core:data:database` or `core:data:database-test` edge.

### 3.6 External consumers and root identity

| Surface | Ownership |
| --- | --- |
| Module edges | app/app `implementation` (stays); app/common `implementation` → `api` |
| Navigation registration | app/common `AppNavigationHost` calls `singleTrainingsGraph(modifier = …)`, tag `SingleTrainingGraph` |
| Graph aggregation | app/app `AppGraph` (gains the accessor) |
| Extension identities | app/app test `SingleTrainingExtensionIdentityTest`, through `asContribution` today, 6 identities (below) |
| Direct composition | none: no app test composes `singleTrainingsGraph()` directly |
| Device journeys by tag | `RouteReachabilityTest.singleTrainingOpensForCreateFromAllTrainingsAndTheListReturns` (the all-trainings FAB, a null route argument) and `singleTrainingOpensFromASeededRowAndTheListReturns` (a seeded row, a non-null one), both in the class-level `@Regression` `RouteReachabilityTest` (15 identities); they await the `SingleTrainingGraph` tag and leave through `TrainingEditCloseButton` and `TrainingDetailBackButton` |
| Route | `Screen.Training(uuid: String?)`, core:ui:navigation commonMain, unchanged |

The six extension identities, each of which calls the helper `.singleTraining("tr-1")` once, the
fifth also `.singleTraining("tr-2")`, and the sixth `.singleTraining(null)` instead:

1. extension resolves the store through the parent graph
2. store's app-scoped deps are the SAME instances the parent holds
3. the session subsystem is inherited from the parent, not rebuilt
4. both qualified dispatchers are inherited and stay two distinct keys
5. each extension carries its own route arg into the store state
6. a null route arg survives the binding and reaches the store as null

The fourth pins `@DefaultDispatcher` and `@MainImmediateDispatcher` as the parent's instances and as
two distinct keys; ClickHandler is the first shared-feature handler to inject
`@MainImmediateDispatcher` (Section 5.8). The third reads
`SingleTrainingGraph.sessionConflictResolver`, which is why the graph's public surface names a
`core:data:exercise` type (Section 10).

### 3.7 Context readers and PNG integrity

Five executable `Context.appDeps` readers exist (`EXPECTED_APP_DEPS_READERS`): app-dialogs and the
exercise, live-workout, settings and single-training features. Removing single-training's leaves
four.

PNG manifests at the discovery baseline use 7.10 Section 3.7's method
(`git ls-files -s … | LC_ALL=C sort -k4 | shasum -a 256`), reproduced at `53605e126` from git
objects. They equal 7.13's projection (its Section 3.7): exercise-chart's 30 PNGs moved
byte-identically, and nothing since has touched a PNG.

| Manifest | Entries | Hash |
| --- | ---: | --- |
| Paparazzi mode/blob/path | 456, 13 owners | 731d6ed7449bab5cc13c7c4e16074f6014d86b9959f3639fe0bcf626efe5b2e4 |
| Paparazzi path list | 456 | f26a3dddf485de5e871b6f9cd64401bc8a82178a284b9ac20fdcfec5c3ffedf0 |
| All repository PNGs, mode/blob/path | 528 | d172abf0962d33f632c4751adbb8d25fd39633a214497ca4e45d5c36c5b60aad |
| single-training blob set (12 sorted blob IDs, duplicates kept) | 12 | e116929ed89cc466ba537bf4600975ab0b35cac88a50bc5e939c90fe6677161b |

If only the 12 paths move from `src/test/snapshots/images/` to
`src/androidHostTest/snapshots/images/`, the projections are:

- f903e721e571699b5138ea7363bb9109dce0583400a4057d5daed91c0e540a1f (Paparazzi mode/blob/path);
- f19c856d18f8b4d836a5d7bc73571443deaaebb3ac1d41f9f45fa51ca97a9cab (path list);
- 7cc28d134cb89a24a189a7b17a05a9d1fba95db0c7b3213f93dccbfe253e2df9 (all PNGs).

The owner count stays 13: single-training already owns goldens. The simulated exit tree's index
reproduced all three and the unchanged blob set.

**The normative check is relative, as in 7.10.** At implementation entry: take the base manifests;
rewrite exactly the 12 single-training paths from `src/test/` to `src/androidHostTest/`; require the
head manifests to equal the rewritten base byte for byte; require the blob-set hash to be unchanged.

### 3.8 Measurement limitation

The discovery ran no Gradle task, no Native task and no device test. The implementation records
fresh entry gates (Section 12) and stops if the environment for any of them is missing.

## 4. Platform/API blockers — settled decisions

### 4.1 Android resources become private Compose resources

7.10 Section 4.2 applies with the package of Section 0.3. No `StringResource`,
`PluralStringResource`, Android id, `ResourceWrapper` or `StringResolver` enters State, Action,
Event, UI models, domain models or repository APIs. D2's `TrainingEditCopy` holds plain strings.

### 4.2 Android and JVM APIs leave common

| Today | At exit |
| --- | --- |
| `LocalContext` + `context.appDeps<…>()` | explicit factory, shape B (Section 6) |
| `androidx.annotation.VisibleForTesting` on `NAME` | removed; `NAME` stays private |
| `R`, `androidx.compose.ui.res.stringResource` / `pluralStringResource` | generated `Res`, `org.jetbrains.compose.resources.stringResource` / `pluralStringResource` in composition (Section 5.5) |
| `ResourceWrapper.getString` (11 sites) | `StringResolver`: 8 on use in ClickHandler's launches, 3 once per Store in CommonHandler's `Init` (D2, Section 5.4) |
| `ResourceWrapper.formatMediumDate` | unchanged (S1), but before the State lambda (F2) |
| `androidx.activity.compose.BackHandler` | `androidx.compose.ui.backhandler.BackHandler`, the graph function opting in to `ExperimentalComposeUiApi` (all-trainings' precedent; the KMP convention adds `ui-backhandler`) |
| `System.currentTimeMillis()` | `Clock.System.now().toEpochMilliseconds()` (`kotlin.time.Clock`, stable since Kotlin 2.3; the shared features' row) |
| `uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES` | `ThemeMode` subjects (S3, Section 5.5) |
| `Icons.Default.Search`, `Icons.Filled.DragHandle` | the same imports, through `implementation(libs.cmp.material.icons.extended)` (past-session's and home's precedent, Section 10) |
| copy resolved on four synchronous click paths | D2's model (Section 5.2) |
| two State lambdas that return a helper's State, one resolving a date | D3 with F2's split (Section 5.3) |

There is no `rememberSaveable`, Parcelable, paging or `java.` token to port.

### 4.3 Native test names

None needs a suppression. Kotlin/Native's `INVALID_CHARACTERS_NATIVE_ERROR` rejects `.` `;` `,`
`(` `)` `[` `]` `{` `}` `/` `<` `>` `:` `\` `$` `&` `~` `*` `?` `#` `|` `§` `%` `@` (7.13
Section 4.3), and no identity of Section 3.2 contains any of them; PR-N (`b0045adfa`) already ran
over this module. Two contain U+2014 (the two `— reachable` save identities) and six an apostrophe
(four inherited, two of S14's); both have run on Native before, in past-session, plan-editor, home
and exercise-chart.

`the История count is the total not the visible page` is the first identity with Cyrillic letters
to run on Native: the only non-ASCII character in any commonTest or iosTest name today is U+2014.
Kotlin/Native accepts Unicode letters in identifiers, and the oracle compares the name from UTF-8
XML. Its first Native run is the early check of Section 12.3; a compile error, a mangled name in
the XML or an oracle miss is a STOP, never a rename (Section 14).

## 5. Resource, State, copy and preview architecture

### 5.1 Private generated resource owner

7.10 Section 5.1 applies:
`compose.resources { packageOfResClass = "io.github.stslex.workeeper.feature.single_training.resources" }`
and no `publicResClass`, so the generated `Res` is internal to the module.
`ui/TrainingDetailScreen.kt` and `ui/TrainingEditScreen.kt` already read kit's `Res`, so they import
the feature's as
`import io.github.stslex.workeeper.feature.single_training.resources.Res as SingleTrainingRes`
(archive's, plan-editor's, past-session's and exercise-chart's precedent); every other file imports
the feature's `Res` unaliased, and ktlint keeps the alias import last.

The alias lengthens three lines of `TrainingEditScreen` past detekt's 120 columns: the two
`FormSection(label = stringResource(SingleTrainingRes.string.…)) { fieldLabel ->` heads (name and
description) and the description field's `placeholder = stringResource(…)`. They wrap, the argument
on its own line, and nothing else in them changes:

    FormSection(
        label = stringResource(SingleTrainingRes.string.feature_training_edit_label_name),
    ) { fieldLabel ->

### 5.2 D2 and F1 — copy before the click (required)

F1's four paths run synchronously from `consume` on Main. D2 splits them by what their copy needs:
the three that show constant copy read it from State, resolved once per Store; the one whose copy
takes an argument resolves it on use, and writes it only onto the state it replaces.

**The copy holder.** `mvi/model/SingleTrainingUiModels.kt` gains, before `HistorySessionItem`:

    /**
     * D2: the editor's constant copy — the no-exercises error and the two undo toasts — resolved once
     * per Store by `Init`, so the click paths that show it never suspend.
     */
    @Stable
    data class TrainingEditCopy(
        val noExercisesError: String,
        val exerciseRemoved: String,
        val setRemoved: String,
    ) {

        companion object {

            /** Until `Init`'s resolution lands; a toast needs a draft exercise or a typed name first. */
            val EMPTY = TrainingEditCopy(noExercisesError = "", exerciseRemoved = "", setRemoved = "")
        }
    }

`State` gains, directly after `dialogState`,
`/** D2: the editor's constant copy, resolved once per Store; its click paths read it here. */
val editCopy: TrainingEditCopy,`, and `State.create` sets `editCopy = TrainingEditCopy.EMPTY`.
State goes from 21 fields to 22. `editCopy` takes no part in `hasChanges` or the snapshot.

**The resolution** (`CommonHandler`). `processInit` calls `resolveEditCopy()` after
`observeActiveSession()`, before the create/load branch, so both routes resolve it:

    /**
     * D2: the editor's constant copy, resolved once per Store, so the save error and the two undo
     * toasts read it from State and their click paths never suspend.
     */
    private fun resolveEditCopy() {
        launch(
            onSuccess = { copy -> updateState { it.copy(editCopy = copy) } },
        ) {
            TrainingEditCopy(
                noExercisesError = stringResolver.getString(
                    Res.string.feature_training_edit_error_no_exercises,
                ),
                exerciseRemoved = stringResolver.getString(
                    Res.string.feature_training_edit_exercise_removed,
                ),
                setRemoved = stringResolver.getString(Res.string.feature_training_edit_plan_set_removed),
            )
        }
    }

`Init` runs once per Store (`initialActions = listOf(Action.Common.Init)`), so the copy resolves
once per navigation entry.

**The three constant paths** (`ClickHandler`) read State, with no other change:

- `processSaveClick`: `sendEvent(Event.ShowSaveError(message = current.editCopy.noExercisesError))`,
  under the comment
  `// D2: the copy was resolved once per Store, so this click path never suspends.`;
- `processExerciseRemove`: `message = current.editCopy.exerciseRemoved,` in
  `Event.ShowExerciseRemovedUndo`;
- `processExercisePlanAction`: `message = state.value.editCopy.setRemoved,` in
  `Event.ShowSetRemovedUndo`, which keeps reading `draftEpoch` from `state.value` after the write.

The order of every write, event and haptic stays as today, so the undo toasts still carry the
`draftEpoch` of the draft they belong to.

**The confirm** (`ClickHandler.processPermanentDeleteMenu`) at exit:

    private fun processPermanentDeleteMenu() {
        val current = state.value
        if (!current.canPermanentlyDelete) return
        sendEvent(Event.HapticClick(HapticFeedbackType.ContextClick))
        val name = current.name
        // D2: the title takes the name, so the confirm's copy resolves on use, before its write.
        launch(
            onSuccess = { confirm ->
                updateState { latest ->
                    // Only the menu it replaces may open it: a dismissal that landed first wins.
                    if (latest.dialogState == DialogState.DetailMenu) {
                        latest.copy(dialogState = confirm)
                    } else {
                        latest
                    }
                }
            },
        ) {
            DialogState.PermanentDeleteConfirm(
                title = stringResolver.getString(
                    Res.string.feature_training_detail_permanent_delete_title,
                    name,
                ),
                body = stringResolver.getString(Res.string.feature_training_detail_permanent_delete_body),
                impactSummary = stringResolver.getString(
                    Res.string.feature_training_detail_permanent_delete_impact,
                ),
                confirmLabel = stringResolver.getString(
                    Res.string.feature_training_detail_permanent_delete_confirm,
                ),
            )
        }
    }

The name is captured before the launch, as every other launch in this handler captures its inputs.
The dialog still replaces the menu in one write: no intermediate `Hidden` is ever written, which the
inherited identity `permanent delete replaces the menu with its confirm in one write` keeps pinning.
A second tap while the copy resolves launches again; the first write opens the confirm and the
second finds no menu. `processArchiveClick` closes the menu before its own launch, so an archive
tapped in the window also wins.

In commit 2 (classic, Section 15) the same shape reads through the wrapper:
`resourceWrapper.getString(R.string.…)` inside `resolveEditCopy`'s and the confirm's launch actions,
with `import io.github.stslex.workeeper.feature.single_training.R` in CommonHandler. Commit 3 swaps
the reads for the resolver.

**D2's two observable differences.**

1. The confirm opens one dispatch later: its copy resolves on Default and the write lands on
   Main.immediate, where `onSuccess` runs. A dismissal of the menu (scrim, back, or the archive
   item) that lands in that window now wins; before D2 the confirm replaced the menu synchronously,
   so there was no window.
2. The editor's constant copy follows the locale at `Init` until the Store ends, as every
   pre-formatted State field already does (the history dates, the plan summaries; 7.13's D3
   accepted the same for its readout). Before D2 each click re-read it. A toast fired before
   `Init`'s resolution lands would carry an empty message; it needs a draft exercise to remove or a
   typed name with no exercise, after the screen composes, so the window is theoretical and is
   accepted.

The archive's two reads (in its `onSuccess`) and the conflict's two (in the start-session launch's
action) already run inside launches; they move to the resolver unchanged in place (Section 5.4).

### 5.3 D3 and F2 — the two State lambdas (required)

**The discard** (`ClickHandler.applyDiscard`), the non-create branch at exit:

    } else {
        // D3: the snapshot's restore is written in the lambda itself, so the lambda is a copy.
        updateState { latest ->
            val snapshot = latest.originalSnapshot ?: return@updateState latest.copy(mode = Mode.Read)
            latest.copy(
                mode = Mode.Read,
                expandedExerciseUuids = persistentSetOf(),
                pendingSetRestores = persistentListOf(),
                name = snapshot.name,
                nameError = false,
                description = snapshot.description,
                tags = latest.availableTags
                    .filter { tag -> tag.uuid in snapshot.tagUuids }
                    .toImmutableList(),
                tagSearchQuery = "",
                // The whole snapshot list, never the current one filtered and re-sorted:
                // filtering can only remove, and re-sorting would leave `position` stale.
                exercises = snapshot.exercises,
            )
        }
    }

`private fun State.applySnapshotOrPop()` leaves. The expressions are its own, in its order; its
comment moves with `exercises`.

**The load** (`CommonHandler.loadTraining`'s `onSuccess`) at exit:

    onSuccess = { result ->
        // F2: the rows read nothing from the State, so they are built before the write;
        // the lambda keeps only the tag match against the live availableTags, and the copy.
        val exercises = result.exercises.toExerciseItems()
        val pastSessions = result.recentSessions.toHistoryItems()
        updateState { current ->
            val training = result.training ?: return@updateState current.copy(isLoading = false)
            val tags = training.labels.map { name ->
                current.availableTags.firstOrNull { it.name.equals(name, ignoreCase = true) }
                    ?: AppTagItem(uuid = name, name = name)
            }.toImmutableList()
            current.copy(
                uuid = training.uuid,
                name = training.name,
                description = training.description.orEmpty(),
                tags = tags,
                exercises = exercises,
                pastSessions = pastSessions,
                historyCount = result.historyCount,
                originalSnapshot = State.Snapshot(
                    name = training.name,
                    description = training.description.orEmpty(),
                    tagUuids = tags.map { it.uuid },
                    exercises = exercises,
                ),
                canPermanentlyDelete = result.canPermanentlyDelete,
                isLoading = false,
            )
        }
    },

`private fun State.applyLoaded(result: LoadResult)` leaves, and its exercise mapping becomes

    private fun List<TrainingExerciseDetail>.toExerciseItems(): ImmutableList<TrainingExerciseItem> =
        sortedBy { it.position }
            .mapIndexed { index, detail -> … }                 // applyLoaded's body, unchanged
            .toImmutableList()

`toHistoryItems()`, `LoadResult`, the action and the `onError` arm with its comment stay as they
are.

What changes in behaviour: nothing visible. The exercise items and the history rows are built once,
in `onSuccess`, where `applyLoaded` ran them; a compare-and-set retry no longer re-runs
`formatPlanSummary` or `formatMediumDate`. With no training, the two lists are built from what the
load returned and dropped, where `applyLoaded` returned before building them. The tag match stays
inside the lambda because it reads `current.availableTags`; Section 17 records the inherited race
that keeps.

`observeTags` keeps its pure `tags.map { it.toUi() }` inside its lambda: tech-debt.md's 🟡 row for it
is re-pointed, not paid down, as 7.9–7.13 left their siblings (Section 9.2).

Both rewrites are commit 2's, in `src/main`; after commit 2 every State lambda of the module returns
a `.copy(` and none calls a same-file function that resolves copy (Section 2), so D5 passes on this
entry with no exception.

### 5.4 Copy outside composition — StringResolver, and S1

**ClickHandler** at exit: constructor `interactor`, `stringResolver: StringResolver`,
`@MainImmediateDispatcher mainDispatcher`, `store`. It holds exactly eight
`stringResolver.getString(` and names no `ResourceWrapper`:

- the archive's `onSuccess`: `feature_training_detail_archive_success_format` with the captured
  name, and `feature_training_detail_archive_blocked`;
- the start-session launch's action, on `NeedsUserChoice`:
  `feature_training_detail_conflict_unnamed` when the name is blank, and
  `feature_training_detail_conflict_progress_format` with `resolution.doneCount` and
  `resolution.totalCount`;
- the confirm's launch action: its four (Section 5.2).

**CommonHandler** at exit: constructor `interactor`, `resourceWrapper: ResourceWrapper`,
`stringResolver: StringResolver`, `store`. It holds exactly three `stringResolver.getString(`
(`resolveEditCopy`) and one `resourceWrapper.formatMediumDate(` (`toHistoryItems`).

Each read keeps its resource and arguments: `resourceWrapper.getString(R.string.x, args…)` becomes
`stringResolver.getString(Res.string.x, args…)`. **The argument rule** (7.10 Section 5.2) applies:
`StringResolver` returns the catalog text verbatim when a call passes no format argument, so a
formatted read that dropped its arguments would ship `Delete ‘%1$s’ permanently?`. The three
formatted reads (the title, the archive success, the progress) pass theirs; the strict fakes
(Section 11.1) and controls 20 and 51 pin it.

**S1, the date.** `resourceWrapper.formatMediumDate(finished)` in `toHistoryItems` stays, and it is
the module's only `ResourceWrapper` read: a medium date is a locale-aware format, not catalog copy
(kmp-string-resolver.md; archive's `formatDayMonth`, past-session's `formatMediumDate` and home's
`getAbbreviatedRelativeTime` are the precedents). It now runs before the State lambda (F2).
`ResourceWrapper` has no iOS implementation; the scene renders pre-formatted State and needs no stub
(Section 11.4). A portable date formatter is out of scope (tech-debt.md's row gains this read,
Section 9.2).

**Threading.** The archive's and the conflict's reads run where they run today. D2 moves two things:
the three constants from click time on Main to `Init`'s launch on Default, and the confirm's four
from the click on Main into a launch on Default. CMP's first catalog read in a process is a blocking
asset read, and later reads are cached; do not move the work otherwise in this phase.

The events, `DialogState` and the UI models keep their shape and their pre-formatted strings. This
module follows tech-debt.md's "Shared (KMP) string resources" rule, so it adds no debt row or
`TODO(tech-debt)` marker for the resolver.

### 5.5 Composable copy and previews (S3)

The five UI files of Section 3.1 resolve copy with `org.jetbrains.compose.resources.stringResource`
/ `pluralStringResource` on the private `Res`, in the same places and with the same arguments:

- `TrainingDetailScreen`, through `SingleTrainingRes`: the menu button's description, the three
  section heads, the history head's plural (`pluralStringResource(…session_count, count, count)`),
  the dock's start/resume label and its edit label; kit's `core_ui_kit_action_back` stays;
- `TrainingEditScreen`, through `SingleTrainingRes`: the create/edit title, the close button's
  description, the two field labels, the name error, the description placeholder, the
  `label_exercises_format` count with `state.exercises.size`, the add button and the tags label;
  kit's `core_ui_kit_action_cancel` and `core_ui_kit_action_save` stay;
- `ExercisePickerSheet`: the title, the search placeholder, the empty text, the cancel label and
  `picker_add_format` with `selectedUuids.size`;
- `TrainingDetailMenuSheetContent`: the archive and permanent-delete items;
- `TrainingExerciseCard`: the drag handle's and the remove button's descriptions, and the no-plan
  line.

Each file loses Android's `stringResource` / `pluralStringResource` and the `R` import; the graph
loses its unused Android `stringResource` import and keeps CMP's six kit reads. In commit 2 these
files do not change.

**S3, the previews.** The three `uiMode` pairs become home's `RecentSessionRow` shape, named after
their subject:

    @Preview(name = "Light", showBackground = true)
    @Composable
    private fun TrainingHistoryRowLightPreview() {
        TrainingHistoryRowPreview(themeMode = ThemeMode.LIGHT)
    }

    @Preview(name = "Dark", showBackground = true)
    @Composable
    private fun TrainingHistoryRowDarkPreview() {
        TrainingHistoryRowPreview(themeMode = ThemeMode.DARK)
    }

    @Composable
    private fun TrainingHistoryRowPreview(themeMode: ThemeMode) {
        AppTheme(themeMode = themeMode) {
            …                                                  // today's preview body, unchanged
        }
    }

and the same for `ExercisePickerSheet` and `TrainingExerciseCard`. Each file imports `ThemeMode`.
The two bare `@Preview`s stay byte-identical. `preview_subjects` names the three subjects (six
`@Preview(`), `bare_previews` pins 1 in `ui/TrainingDetailScreen.kt` and 1 in
`ui/components/TrainingDetailMenuSheetContent.kt`, and every other commonMain file holds none.

### 5.6 S5 — dead code

`InputHandler.processPickerSearch` at exit begins:

    val current = state.value
    if (current.pickerState !is PickerState.Open) return

and the three trailing lines (`// suppress unused: …`, `@Suppress("unused")`,
`val acknowledged = picker`) leave. The binding was read only by that local, so behaviour is
identical. The two dead keys leave both catalogs (Section 3.4); the EN root's `tools:ignore` stays.
Both are commit 2's.

### 5.7 The inherited locale property

7.9 Section 5.7 applies, and from this phase it also covers single-training's copy: the screens
follow CMP's primary-locale choice, so a device whose locale list is [uk-UA, ru-RU] shows Russian
today and English after the move. The history dates stay on `AndroidResourceWrapper`'s medium date
for `Locale.getDefault()`, so such a device can pair English copy with a Ukrainian date, as
past-session's does. `StringResolver` has the same property; the remedy is the maintainer's open
decision, not part of this phase.

### 5.8 Back, the clock and the dispatcher

- `BackHandler(enabled = processor.state.value.interceptBack) { processor.consume(Action.Click.OnBackClick) }`
  keeps its condition and action on CMP's `androidx.compose.ui.backhandler.BackHandler`;
  `singleTrainingsGraph` opts in with
  `@OptIn(ExperimentalSharedTransitionApi::class, ExperimentalComposeUiApi::class)`, all-trainings'
  form. On Android it registers with the activity's back dispatcher, as the AndroidX one did.
- `processSaveClick`'s timestamp is `Clock.System.now().toEpochMilliseconds()` with
  `import kotlin.time.Clock`, in commit 2 (7.13 Section 4.2: no opt-in at Kotlin 2.4.10).
- `ClickHandler` keeps `@MainImmediateDispatcher private val mainDispatcher` and its three
  `withContext(mainDispatcher) { consume(Action.Navigation.Back) }` (archive success, permanent
  delete, create save). The qualifier is commonMain; its binding is the parent graph's, and the
  fourth extension identity pins it. Those calls sit in `onSuccess`, already on Main.immediate;
  dropping them is a backlog item (Section 17), not this phase's.

## 6. Explicit generation-owned shape-B factory flow

7.11 Section 6 applies, with the substitutions of Section 0.3, exactly as 7.13 Section 6 applied it:

- `AppRootDeps` gains `val singleTrainingGraphFactory: SingleTrainingGraph.Factory` after
  `exerciseChartGraphFactory`, with the KDoc
  `/** Generation-owned factory for the single-training graph extension. */`;
- `AppGraph` overrides it, after `exerciseChartGraphFactory`;
- `App.kt` passes `singleTrainingGraphFactory = deps.singleTrainingGraphFactory` after the
  exercise-chart line;
- `AppNavigationHost` takes a required `singleTrainingGraphFactory: SingleTrainingGraph.Factory`
  after `exerciseChartGraphFactory`. It calls
  `singleTrainingsGraph(factory = singleTrainingGraphFactory, modifier = …)`, and the modifier chain
  is unchanged:
  `standardModifier.reportScreenPlace<Screen.Training>().testTag("SingleTrainingGraph")`;
- `singleTrainingsGraph` takes a required `factory: SingleTrainingGraph.Factory` as its first
  parameter and builds `navComponentScreen(SingleTrainingFeature(factory)) { processor -> … }`: the
  `FeatureAssisted` overload, which hands the typed `Screen.Training` key to `processor(screen)`.
  The rest of the function — the event handling, `BackHandler` (Section 5.8), the loaded-content
  gate, the sheets and dialogs — is unchanged;
- `SingleTrainingFeature` becomes an `internal class` with
  `private val factory: SingleTrainingGraph.Factory`, still
  `FeatureAssisted<SingleTrainingStoreProcessor, Screen.Training>`. Its `processor(screen)` keeps
  `@Suppress("UNCHECKED_CAST")` and returns:

      rememberMetroStoreProcessor<SingleTrainingStoreImpl> {
          factory
              .createSingleTrainingGraph(screen)
              .singleTrainingStore
      } as SingleTrainingStoreProcessor

- `createSingleTrainingGraph(screen)` runs once, inside the retained `rememberMetroStoreProcessor`
  lambda. The route argument stays a `@Provides` bound instance (7.7 Section 6), and
  `SingleTrainingStoreImpl` still reads `screen.uuid`;
- di/SingleTrainingGraph.kt does not change: the Store, both qualified dispatchers, the inherited
  `SessionConflictResolver` and the two `@Binds`;
- app/common's edge becomes `api(project(":feature:single-training"))`, placed after
  `api(project(":feature:exercise-chart"))`, and its `implementation` line goes. The comment
  "The twelve entry providers AppNavigationHost composes." stays true.

`LocalContext`, `appDeps`, `asContribution`, nullable fallbacks, registries and pre-admission
resolution are forbidden.

`SingleTrainingExtensionIdentityTest` gets the helper:

    private fun AppGraph.singleTraining(uuid: String?): SingleTrainingGraph =
        singleTrainingGraphFactory.createSingleTrainingGraph(Screen.Training(uuid = uuid))

The `asContribution` import goes. The six names, the call sites (`.singleTraining("tr-1")` in the
first five identities, plus `.singleTraining("tr-2")` in the fifth, and `.singleTraining(null)` in
the sixth) and every assertion stay exact; the file stays JUnit 5 and MockK in app/app's `src/test`.

No app test composes `singleTrainingsGraph()` directly, so the app boundary is six paths
(Section 9.2).

## 7. In scope

Only these:

- D4's and D5's gate change, as this PR's first commit (Section 12.0);
- D2, D3 with F2's split, S5, the clock port and S14's four identities, in `src/main` and `src/test`
  (Sections 5.2, 5.3, 5.6, 5.8, 11.1);
- the conversion of Sections 4–6;
- the moves of Sections 3.1–3.4 and P1;
- S4: the removal of `@VisibleForTesting`, CMP's `BackHandler` and the icons edge;
- the composable copy and S3's previews;
- porting the 4 suites to kotlin.test with deterministic in-file fakes;
- moving both golden suites to androidHostTest, and the device test to androidDeviceTest;
- one iosTest scene;
- the six app paths, the three CI paths and the five documentation paths;
- one signed, green PR that the maintainer merges.

## 8. Explicit non-goals

7.13 Section 8 applies, read with Section 0.3. In addition:

- no change to `core:ui:kit`, `core:ui:plan-editor`, `core:core` (`ResourceWrapper` gets no iOS
  implementation), `core:data:exercise` or `core:data:database`;
- no portable date formatter (S1);
- no closing of the inherited tag race, no removal of `getLabels` and no change to the three
  `withContext(mainDispatcher)` calls (Section 17);
- no handler seam, and no change to how the Store wires its four handlers;
- no new test identity beyond S14's four and the scene, no rename of any Section 3.2 or 3.3
  identity, and no change to an inherited identity's assertions; the one inherited setup that
  changes is Section 11.1's launch for `permanent delete replaces the menu with its confirm in one
  write`;
- no new device test identity, and no copy read added to the device test;
- no change to `TODO(tech-debt)` markers, to `PickerExerciseItem.tagsLabel`, or to the text of
  tech-debt.md's single-training rows beyond Section 9.2's re-pointing and the one label suffix;
- no edit to `.claude/skills/` (7.10's D4);
- no change to `graphify-out/`.

## 9. Exact allowed change boundary

### 9.1 Target module — 62 tracked paths at exit

- `build.gradle.kts`;
- 39 production files under
  `src/commonMain/kotlin/io/github/stslex/workeeper/feature/single_training/` (Section 3.1);
- `src/commonMain/composeResources/values/strings.xml` and `values-ru/strings.xml`;
- 4 files under `src/commonTest/kotlin/io/github/stslex/workeeper/feature/single_training/`: the
  suites of Section 3.2;
- `src/androidHostTest/kotlin/io/github/stslex/workeeper/feature/single_training/golden/TrainingDetailGoldenTest.kt`
  and `…/golden/TrainingEditGoldenTest.kt`;
- 12 PNGs under `src/androidHostTest/snapshots/images/` (Section 3.3 names);
- `src/androidDeviceTest/kotlin/io/github/stslex/workeeper/feature/single_training/SingleTrainingScreenTest.kt`;
- `src/iosTest/kotlin/io/github/stslex/workeeper/feature/single_training/SingleTrainingFeatureSceneIosTest.kt`.

That is 61 files under `src` plus the build file. Nothing remains under `src/main`, `src/test` or
`src/androidTest`. There is no androidMain, iosMain, new production file, shared test helper,
fixture or extra catalog.

### 9.2 Root, CI and documentation — 14 paths

App (6):

    app/app/src/main/java/io/github/stslex/workeeper/di/AppGraph.kt
    app/app/src/test/kotlin/io/github/stslex/workeeper/di/SingleTrainingExtensionIdentityTest.kt
    app/common/build.gradle.kts
    app/common/src/main/kotlin/io/github/stslex/workeeper/App.kt
    app/common/src/main/kotlin/io/github/stslex/workeeper/app/common/di/AppRootDeps.kt
    app/common/src/main/kotlin/io/github/stslex/workeeper/host/AppNavigationHost.kt

CI (3):

    .github/scripts/assert_kmp_ui_source_topology.py
    .github/scripts/assert_kmp_ios_smoke.py
    .github/workflows/android_build_unified.yml

Documentation (5):

    documentation/architecture.md
    documentation/ci-cd.md
    documentation/testing.md
    documentation/tech-debt.md
    documentation/feature-specs/kmp-phase-7-14-single-training-feature.md

What changes in each document. This list covers every place 7.13's commits touched for
exercise-chart (`git diff 231739859 53605e126 -- documentation/`). Every module count there reads
fourteen.

- **architecture.md:**
  1. In the shape list, item 2 (`FeatureAssisted`): the sentence naming the shared image-viewer,
     plan-editor, past-session and exercise-chart entries as the shape-B factory-resolution
     exceptions adds single-training: `… / ExerciseChartGraph.Factory / SingleTrainingGraph.Factory`
     through `AppNavigationHost` and `… / exerciseChartGraph / singleTrainingsGraph` into
     `… / ExerciseChartFeature / SingleTrainingFeature`.
  2. The Context-seam paragraph: single-training joins the list of entries that no longer use the
     feature-side seam, `AppGraph.singleTrainingGraphFactory` joins the accessor list, and the
     sentence reads "Exactly 4 feature/dialog Context readers remain."
  3. "State mutation discipline", after 7.13's paragraph, one paragraph: "A State lambda also calls
     no function of its own file that resolves copy: for the shared feature entries the topology
     gate follows same-file calls from each lambda, transitively (decision D5 of
     [kmp-phase-7-14-single-training-feature.md](feature-specs/kmp-phase-7-14-single-training-feature.md))."
  4. "Localization", under "Shared (KMP) modules and which reader a string takes", after the
     primary-locale paragraph: "A click path cannot suspend, so it never calls `StringResolver`.
     Copy it shows that takes no argument is resolved once per Store, at `Init`, and read from
     State; copy that takes an argument resolves on use, in a launch whose write is a
     compare-and-set `updateState` that replaces only the state it was opened from (decision D2 of
     [kmp-phase-7-14-single-training-feature.md](feature-specs/kmp-phase-7-14-single-training-feature.md):
     single-training's `TrainingEditCopy` and its permanent-delete confirm)."
  5. "Per-feature MVI layout", Notes: the first bullet's stale claim that single-training keeps MVI
     under `ui/mvi/` (it is `mvi/`) reads: "`feature/exercise` keeps MVI under a `ui/mvi/` package
     while `feature/single-training` and the simpler `feature/all-trainings`,
     `feature/all-exercises`, and `feature/home` keep it directly under `mvi/`." The rest of the
     bullet stays.
- **ci-cd.md**, the places 7.13 updated for exercise-chart:
  - the `android_build_unified.yml` row of the workflows table ("…, home feature, exercise-chart
    feature, and single-training feature Native tests …, then links the same fourteen modules' …");
  - the KMP-shaped alias list, which adds `:feature:single-training`;
  - the Native job's module description, which adds `:feature:single-training` (all 62 portable
    cases plus the production scene: the catalog and the handlers' formatted copy through the real
    `StringResolver`, the read screen, the editor, the exercise picker, the menu, and dispatch, text
    input included);
  - the every-module-is-checked list;
  - the validator paragraph, which adds: "The single-training feature validator requires all 62
    portable tuples and
    `io.github.stslex.workeeper.feature.single_training.SingleTrainingFeatureSceneIosTest.resourcesDetailEditorPickerAndMenuRenderAndDispatch`
    exactly once, for exactly 63 target tuples.";
  - "All fourteen result directories";
  - "The same fourteen modules'" and `N/14 linked`.
- **testing.md:**
  1. The `AppGraph.stringResolver` bullet: single-training's `ClickHandler` and `CommonHandler` join
     the constructor-injected readers.
  2. A "Shared single-training feature" subsection after "Shared exercise-chart feature". It covers:
     - the 62 identities on both targets, in their distribution, S14's four among them, and the
       first Cyrillic identity on Native;
     - the store fakes and the per-identity launch behaviour of Section 11.1, the interactor and
       navigator fakes;
     - the strict resolver fakes and the `formatMediumDate`-only `ResourceWrapper` fake;
     - D2's guard and Init-resolved copy as the four identities pin them;
     - the module's one production `core:data:database` edge, read by the domain mapper only;
     - the scene (Section 11.4), with no `ResourceWrapper` stub;
     - what it does not cover (Section 11.5);
     - both golden suites on androidHostTest with their 12 byte-identical PNGs, the first goldens to
       render an entry's own RU copy and a RU plural through CMP; the device test on
       androidDeviceTest;
     - the identity test through `appGraph.singleTrainingGraphFactory`, and the two `@Regression`
       journeys of Section 3.6.
  3. "Existing UI tests": the single-training row's path becomes
     `feature/single-training/src/androidDeviceTest/kotlin/io/github/stslex/workeeper/feature/single_training/SingleTrainingScreenTest.kt`.
  4. "Visual gate — Paparazzi screenshot goldens": in both module lists, single-training moves from
     the classic modules (`src/test`) to the KMP modules (`src/androidHostTest`).
  5. The sentence naming the KMP-shaped modules that both alias spellings cover adds
     `:feature:single-training`.
  6. The Native command block under "Running tests" adds
     `:feature:single-training:iosSimulatorArm64Test`.
  7. The device-link block adds `:feature:single-training:linkDebugTestIosArm64`, and its comment
     reads `14/14`.
- **tech-debt.md:**
  1. Re-pointed links, the text unchanged: the 🟢 UI Mapping Boundary row for
     `ui/SingleTrainingGraph.kt` (to `src/commonMain/kotlin/…`); the 🟡 State Mutation Discipline row
     for `mvi/handler/CommonHandler.kt` (to `src/commonMain/kotlin/…`; its label drops the stale
     `:55-56` and its description ends "…rows, in `observeTags`."); the 🟡 device-test row for
     `SingleTrainingScreenTest.kt` (to `src/androidDeviceTest/kotlin/…`).
  2. In "Shared (KMP) string resources":
     - the 🟡 runtime RU catalog coverage row adds: "single-training's goldens are the exception:
       they render its RU catalog through CMP in Paparazzi, the plural's `few` form included
       ([kmp-phase-7-14-single-training-feature.md](feature-specs/kmp-phase-7-14-single-training-feature.md)
       S7)";
     - the 🟢 typed `rememberMetroStoreProcessor` row reads "the nine shared features' `*Feature.kt`
       (archive, all-trainings, all-exercises, image-viewer, plan-editor, past-session, home,
       exercise-chart, single-training)";
     - the 🟢 portable date formatter row reads "… past-session's and single-training's
       `formatMediumDate` …".
  3. In "State Mutation Discipline", one 🟢 row: "A load that lands before the first tags emission
     chips the training's tags as `AppTagItem(uuid = name)`; the tag picker then shows them
     unselected, and selecting one adds a second chip of the same tag. Phase 7.14's F2 keeps the
     match inside the load's State lambda, so the behaviour is unchanged (a static reading, not
     reproduced)." Location: single-training's `CommonHandler.kt` in `src/commonMain`.
  4. In "Shared (KMP) feature entries — follow-ups", the intro also cites this specification's
     Section 17, and one 🟢 row: "Copy a click path shows is resolved before the click: constant copy
     once per Store at `Init`, into State; copy that takes an argument on use, in a launch whose
     write is a compare-and-set `updateState` guarded on the state it replaces (single-training's
     `TrainingEditCopy` and permanent-delete confirm, Phase 7.14 D2). live-workout's four dialog
     paths follow this template when that feature is shared: three take constants only, the
     delete-session one formats live counts." Location: single-training's `ClickHandler.kt`.

Known stale references after the move, deliberately untouched. They are historical records,
already stale before this phase, or owned by a follow-up documentation PR:

- `documentation/features.md` lines 131–148 (single-training's MVI surface, already stale: it names
  `ui/mvi/store/TrainingStore.kt`; every ported feature's section there is equally stale);
- `documentation/feature-specs/trainings.md` line 904 (the catalog's `src/main/res` path);
- `documentation/research/domain-boundary-audit-codex.md` (its `src/main` paths);
- tech-debt.md's ✅ resolved line for `TrainingHistoryRow.kt` (plain text with an elided path, still
  true);
- `.claude/skills/*` (7.10's D4);
- `graphify-out/`.

A need for any other path is a STOP.

## 10. Gradle and public API contract

~~~kotlin
plugins {
    alias(libs.plugins.convention.kmpComposeLibrary)
    // (the module's Metro comment, moved as it is)
    alias(libs.plugins.metro)
    // (the module's Paparazzi comment, moved as it is)
    alias(libs.plugins.paparazzi)
}

compose.resources {
    packageOfResClass = "io.github.stslex.workeeper.feature.single_training.resources"
}

// (the module's includeJavax comment, moved as it is)
metro {
    interop {
        includeJavax()
    }
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation(project(":core:core"))

            api(project(":core:ui:kit"))
            api(project(":core:ui:mvi"))
            api(project(":core:ui:navigation"))
            api(project(":core:ui:plan-editor"))
            implementation(project(":core:data:database"))
            api(project(":core:data:exercise"))

            api(libs.cmp.ui)
            api(libs.coroutines.core)
            api(libs.kotlinx.collections.immutable)
            implementation(libs.cmp.animation)
            implementation(libs.cmp.material.icons.extended)
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
    "androidHostTestImplementation"(project(":core:ui:golden-harness"))

    "androidDeviceTestImplementation"(libs.bundles.android.test)
    "androidDeviceTestImplementation"(libs.androidx.compose.ui.test.junit4)
    // GUARD comment, as exercise-chart's
    "androidDeviceTestImplementation"(platform(libs.androidx.compose.bom))
    "androidDeviceTestImplementation"(libs.androidx.compose.ui.test.manifest)
    // GUARD comment, as exercise-chart's
    "androidDeviceTestImplementation"(project(":core:ui:test-utils"))
}

apply(from = "$rootDir/gradle/golden-gate.gradle.kts")
~~~

Why each visibility (the rule past-session's build file states: an edge is `api` when a public
declaration of this module names one of its types):

- `implementation(project(":core:core"))`: its types appear only in internal constructors and
  internal code (`ResourceWrapper` in `CommonHandler`). The graph's two dispatcher qualifiers are
  annotations on public members, which need no `api` edge, as in 7.13;
- `api(project(":core:ui:kit"))`: `SingleTrainingStore.State` carries the kit's `AppTagItem`
  (`tags`, `availableTags`). Unlike exercise-chart, the State names a kit type; all-trainings',
  all-exercises' and home's States make their kit edges `api` for the same reason;
- `api(project(":core:ui:mvi"))`: `SingleTrainingStore`, `SingleTrainingHandlerStore`,
  `SingleTrainingHandlerStoreImpl` and `SingleTrainingStoreImpl` extend the MVI contracts;
- `api(project(":core:ui:navigation"))`: `singleTrainingsGraph` extends `NavGraphScope`, and
  `SingleTrainingGraph.Factory` takes a `Screen.Training`;
- `api(project(":core:ui:plan-editor"))`: `TrainingExerciseItem`, `PickerExerciseItem` and the
  events carry `ExerciseTypeUiModel` and `PlanSetUiModel`;
- `implementation(project(":core:data:database"))`: production, for `SingleTrainingDomainMapper`'s
  `PlanSetDataModel` and `SetTypeDataModel`, which no public declaration names (plan-editor's
  precedent);
- `api(project(":core:data:exercise"))`: `SingleTrainingGraph` exposes the inherited
  `SessionConflictResolver`, which the third extension identity reads. This is the one edge that
  differs from the shared siblings, whose graphs expose no `core:data:exercise` type;
- `api(libs.cmp.ui)`: `singleTrainingsGraph` takes a `Modifier`, and `Event.HapticClick` carries a
  `HapticFeedbackType`;
- `api(libs.coroutines.core)`: the interactor returns `Flow`s, and the graph exposes two
  `CoroutineDispatcher`s;
- `api(libs.kotlinx.collections.immutable)`: the State and the UI models carry `ImmutableList` and
  `ImmutableSet`;
- `implementation(libs.cmp.animation)`: `AnimatedVisibility` on the card and the graph's
  `ExperimentalSharedTransitionApi` opt-in;
- `implementation(libs.cmp.material.icons.extended)`: `Icons.Default.Search` and
  `Icons.Filled.DragHandle`.

`kotlin("test")` appears exactly twice, once in commonTest and once in iosTest.

Commit 2 changes no build file. Removed at commit 3, relative to today: `convention.composeLibrary`,
every `testImplementation` (replaced by commonTest, iosTest and `androidHostTestImplementation`),
every `androidTestImplementation` and the `debugImplementation` of the test manifest (replaced by
the `androidDeviceTestImplementation` edges and the BOM, exercise-chart's shape).

The module's own comments move with the build file: the Metro and Paparazzi plugin comments and the
`includeJavax` comment. Rationale comments adapted from the bullets above may be added. No comment
may hold a dependency fragment or a forbidden build token (`mockk`, `robolectric`, `androidMain`,
`iosMain`, `paging.testing`, `testImplementation(`, …).

Verify Gradle metadata rather than copying it blindly (7.8 Section 10). A dependency the measured
source does not need is removed. One it needs is a STOP-and-amend, never a silent addition.

## 11. Test and Native scene contract

### 11.1 Portable suites

The 4 portable suites move to commonTest. JUnit 5 and MockK give way to `kotlin.test`,
`kotlinx-coroutines-test` and deterministic in-file fakes: named private classes, and no shared
helper. Android host and Native each execute exactly the 62 identities of Section 3.2, with zero
failure, error or skip, in the distribution 50/5/5/2.

7.10–7.13 Sections 11.1 and the fake rules of their Sections 19 apply. In this module that means:

- **ClickHandlerTest.**
  - A private `SingleTrainingHandlerStore`: `state` is the identity's `MutableStateFlow`;
    `updateState` and the update-lambda `updateStateImmediate` apply to it; `sendEvent` and
    `consume` record; `launch` follows the table below and returns a `Job`; every other member fails
    fast, `logger`, `lastAction`, `consumeOnMain`, `launchDefault` and `Flow.launch` included (no
    ClickHandler identity reaches them).
  - A private `SingleTrainingInteractor` (16 members): each answers what an identity sets up today
    (`resolveExercises`, `resolveStartSessionConflict`, `createTag`, the save) and otherwise the
    relaxed mock's default (`Unit`, an empty list, `false`, `0`, `null`), and counts its calls.
  - A private strict `StringResolver` (below), and no `ResourceWrapper`: ClickHandler holds none.
  - `ClickHandler(interactor, resolver, Dispatchers.Unconfined, store)`, the dispatcher as today.
- **CommonHandlerTest.**
  - A private `SingleTrainingHandlerStore`: `state`, `updateState` and the update-lambda
    `updateStateImmediate` as above; `launch` launches on the identity's `runTest` scheduler and
    runs the action inside `supervisorScope`, then `onSuccess`, routing a throw from either to
    `onError`, as today's mock does, and returns its `Job`; `Flow.launch` records and returns a
    `Job` without collecting, which is what the relaxed mock did; every other member fails fast.
  - A private interactor: `getTraining`, `getTrainingExercises`, `getRecentSessions`,
    `countSessions` and `canPermanentlyDelete` answer what the identity sets up (a value or a throw)
    and otherwise the relaxed defaults; the History identity's relaxed `mockk()` of `TrainingDomain`
    becomes a real `TrainingDomain` fixture.
  - A private strict `StringResolver` and a `ResourceWrapper` whose `formatMediumDate` answers `""`
    and whose other members throw.
  - Every identity runs as `= runTest { … }` and advances the scheduler until idle before its
    assertions; none uses `runBlocking`, which no commonTest in the repository uses.
- **NavigationHandlerTest.** A private `Navigator` (6 members): `navTo` and `popBack` record; the
  other four fail fast.
- **SingleTrainingStateRouteArgTest.** `kotlin.test` assertions only.

**ClickHandlerTest's launches, per identity.** Today the relaxed store's `launch` runs nothing, and
seven identities re-stub it. D2 makes an eighth, existing identity run its launch, and S14 adds a
ninth; every other identity runs no launch, and its fake `launch` records and returns a `Job`
without running.

| Identity | Launch behaviour |
| --- | --- |
| OnPickerConfirm opens the first inserted card only | the action, then `onSuccess` |
| OnStartSessionClick reports the persisted active session progress | the action only |
| entering the editor after a save collapses every card | the action, then `onSuccess` |
| a failed save re-arms the draft's undos | `onError(RuntimeException("db full"))` only |
| createTag resolving to an already-selected tag does not duplicate the chip | the action, then `onSuccess` |
| a picker re-add discards the stash for the returned card | the action, then `onSuccess` |
| a late picker resolution does not duplicate a card the undo restored | from its re-stub on, captured; the identity later runs the action, then `onSuccess` |
| permanent delete replaces the menu with its confirm in one write | the action, then `onSuccess` (D2: this setup is new; the assertion is not) |
| a menu dismissed before the confirm's copy lands stays dismissed **(S14)** | captured; the identity runs it after the dismissal |

These identities run as `= runTest { … }`; the running ones launch on the test scheduler and the
identity advances it until idle, and the captured ones run when the identity completes them. A
throw from a running or captured launch is not routed anywhere: it fails the identity, as it does
from today's `runBlocking` stubs. Only the failed-save identity calls `onError`, itself.

**The strict StringResolver fakes.** ClickHandlerTest's answers exactly five reads:
`feature_training_detail_conflict_progress_format` with `[1, 2]` → `1 of 2 exercises done`;
`feature_training_detail_permanent_delete_title` with `[<the identity's name>]` → `""`; and
`feature_training_detail_permanent_delete_body`, `…_impact` and `…_confirm` with no argument → `""`.
CommonHandlerTest's answers the three edit-copy identifiers with no argument:
`feature_training_edit_error_no_exercises` → `no-exercises`,
`feature_training_edit_exercise_removed` → `exercise-removed`,
`feature_training_edit_plan_set_removed` → `set-removed`. Both throw on any other resource and on
any read that does not pass exactly what production passes. A throw inside a CommonHandlerTest
launch is routed to `onError`, as every throw there is, so a missed read shows as
`editCopy == TrainingEditCopy.EMPTY` in S14's identity; the other four do not read `editCopy`.

**S14 — the four identities commit 2 adds.** In commit 2 they are JUnit 5 and MockK, with
`resourceWrapper.getString(R.string.…)` stubs where the port has the strict resolver; commit 3 ports
them like the rest. Their fixture is
`TrainingEditCopy(noExercisesError = "no-exercises", exerciseRemoved = "exercise-removed", setRemoved = "set-removed")`:

1. `a menu dismissed before the confirm's copy lands stays dismissed`: State with
   `uuid = "training-1"`, `canPermanentlyDelete = true` and `dialogState = DetailMenu`; the launch
   is captured; `OnPermanentDeleteClick`, then `dialogState` is still `DetailMenu`;
   `OnDetailMenuDismiss`; the captured launch runs its action and then `onSuccess`; `dialogState` is
   `Hidden`;
2. `the save error carries the copy Init resolved`: State with `name = "Push Day"`, no exercise and
   the fixture as `editCopy`; `OnSaveClick`; the one `ShowSaveError` carries `no-exercises`;
3. `the undo toasts carry the copy Init resolved`: Edit mode with `ex-1` (one `60×10` work set) and
   `ex-2`, the fixture as `editCopy`; `OnExercisePlanAction("ex-1", OnSetRemove(0))`, then
   `OnExerciseRemove("ex-2")`; the one `ShowSetRemovedUndo` carries `set-removed` and the one
   `ShowExerciseRemovedUndo` carries `exercise-removed`;
4. `Init resolves the editor's constant copy into State` (CommonHandlerTest): a create route
   (`State.create(uuid = null)`), the three reads answering the fixture's strings; `Init`;
   `editCopy` equals the fixture.

Distinct strings per field make a read of the wrong field fail. Controls 30–33 turn each RED
(Section 13).

**MockK verifications become recorded-state assertions**, as in 7.10–7.13: `verify(exactly = 1)`
becomes exactly one recorded call with the same arguments; `verify(exactly = 0)` an empty record or
a count of 0; a `slot` capture the recorded value. kotlin.test's `assertEquals`, `assertTrue`,
`assertFalse`, `assertNull` and `assertNotNull` replace JUnit's with the same argument order; type
witnesses may be needed (7.11 Section 19.8). No suite uses `assertThrows`, `assertAll` or a delta.

**Mechanics only:** the constructions that swap a parameter (`resourceWrapper` → `resolver`),
the conflict identity's stub becoming the strict resolver's answer, the History identity's
`TrainingDomain` fixture, and `= runTest { … }` where a launch now runs on a scheduler. No other
assertion changes. The inherited casts and `!!` after `kotlin.test` contract assertions stay,
together with their warnings (7.10 Section 19.7).

### 11.2 Android host: the golden suites

7.13 Section 11.2 applies to both golden classes. `TrainingDetailGoldenTest` and
`TrainingEditGoldenTest` move to androidHostTest as they are, JUnit 5 and the golden harness, with
no change. Their 6 method names, light and dark cases, subjects, fixture values and 12 filenames
stay exact. The PNGs move with `git mv`, `verifyPaparazziDebug` executes all 12, and
`recordPaparazziDebug` is forbidden.

**S7.** The chrome now reads RU through CMP's `stringResource` and `pluralStringResource` under
Paparazzi, the history head's `2 СЕССИИ` through CMP's RU plural rule. Every PNG must stay
byte-identical; any pixel change is a STOP that names the method and theme (Section 14).

Host runs at exit: `testAndroidHostTest` executes 4 suites and 62 cases; `verifyPaparazziDebug`
executes 6 suites and 74 cases (12 golden + 62) and prints
`Visual gate live: 12 golden test case(s) executed for 12 golden image(s).` The baseline's 4/58 and
6/70 (Section 12.2) differ by S14's four.

### 11.3 Android device (S8)

`SingleTrainingScreenTest` moves to androidDeviceTest unchanged. Its 3 identities, `@Smoke`, test
tags, clicks, captured actions and assertions stay exact. It reads no copy, and its library test APK
carries AGP's default debug certificate. It runs on the recorded serial (Section 12.3) and in the
canonical Smoke suite (Section 12.4).

The device proof of the factory flow is the two `@Regression` journeys of Section 3.6, one with a
null route argument and one with a non-null one, run as part of the whole `RouteReachabilityTest`
class.

### 11.4 Deterministic iOS production scene

Exactly one class and method, no comma:

    class SingleTrainingFeatureSceneIosTest
    fun resourcesDetailEditorPickerAndMenuRenderAndDispatch()

The scene's setup:

- it uses `runComposeUiTest` and, inside `AppTheme` and one `setContent`, switches through
  test-held state between the production `TrainingDetailScreen(state, consume)`,
  `TrainingEditScreen(state, consume)`, `ExercisePickerSheet(…)` and
  `TrainingDetailMenuSheetContent(canPermanentlyDelete, consume)` (home's scene switches to its
  picker sheet the same way);
- a test-held `State` drives the two screens, and `consume` records actions in one list; the
  picker's four callbacks record `search:<text>`, `toggle:<uuid>`, `confirm` and `dismiss` in a
  second list;
- it uses the bounded settle stepping of all-exercises' scene (7.10 Section 19.7);
- every copy read outside composition goes through one `ComposeStringResolver()` instance
  (`resolver`); it declares no fake resolver;
- it needs no `ResourceWrapper`: every date it renders is a pre-formatted `HistorySessionItem`
  label. It declares no test double.

Fixtures:

- `bench`: `TrainingExerciseItem("ex-1", "Bench press", WEIGHTED, tags = ["Push"], position = 0,
  planSets = [PlanSetUiModel(weight = 60.0, reps = 10, type = WORK)], planSummary = "60×10")`;
- `pullUp`: `TrainingExerciseItem("ex-2", "Pull-up", WEIGHTLESS, tags = [], position = 1,
  planSets = null, planSummary = "")`;
- tags `AppTagItem("tag-1", "Push")` and `AppTagItem("tag-2", "Chest")`;
- history `HistorySessionItem("s-1", "Jul 27, 2026")` and
  `HistorySessionItem("s-2", "Jul 20, 2026")`;
- picker rows `PickerExerciseItem("ex-1", "Bench press", WEIGHTED, ["Push", "Chest"])` and
  `PickerExerciseItem("ex-2", "Pull-up", WEIGHTLESS, [])`.

In one bounded scene it proves:

1. **Catalog:** through `resolver`, all 37 strings and both EN quantities (1 → one, 2 → other) of
   the plural resolve to the exact EN values of Section 3.4, each read as stored, with no format
   argument (39 values; for the plural that is the verbatim template). The plural is also read
   filled, with its count as the one format argument, at 1 and 2: `1 session`, `2 sessions`.
2. **The handlers' copy** (`resolver`, with production's arguments):

   | Read | Output |
   | --- | --- |
   | E: the three reads of `resolveEditCopy`, as a `TrainingEditCopy` | `TrainingEditCopy("Add at least one exercise", "Exercise removed from training", "Set removed")` |
   | T: `permanent_delete_title` with `"Push Day"` | `Delete ‘Push Day’ permanently?` |
   | A: `archive_success_format` with `"Push Day"` | `‘Push Day’ archived` |
   | P: `conflict_progress_format` with `1`, `2` | `1 of 2 exercises done` |

3. **The read screen** (`TrainingDetailScreen`):
   - D1, `State.create(uuid = "t-1")` with `isLoading = false`, name `Push Day`, both tags,
     `[bench, pullUp]`, description `Heavy week.`, both history rows and `historyCount = 2`: the
     title `Push Day`; `TrainingDetailBackButton` with the content description `Back` (kit's
     catalog) and `TrainingDetailMenuButton` with `More`; `TrainingDetailTagMeta` `Push · Chest`;
     the head `EXERCISES` (`AppLabel` uppercases) with `2`; `TrainingExerciseReadCard_ex-1` holding
     `1.`, `Bench press` and `60×10`, and `TrainingExerciseReadCard_ex-2` holding `2.`, `Pull-up`
     and `no plan yet`; `DESCRIPTION` over `TrainingDescriptionText` `Heavy week.`; `PAST SESSIONS`
     with `2 SESSIONS`; `TrainingHistoryRow_s-1` `Jul 27, 2026` and `TrainingHistoryRow_s-2`
     `Jul 20, 2026`; `TrainingDetailEditButton` `Edit`; `TrainingStartSessionButton`
     `Start session`, enabled;
   - D2, D1 with `activeSession = ActiveSessionDomain(sessionUuid = "a-1", trainingUuid = "t-1",
     startedAt = 0L)`: `TrainingStartSessionButton` reads `Resume session`;
   - D3, D1 with no tag, no exercise, an empty description, only `s-1` and `historyCount = 1`: no
     `TrainingDetailTagMeta`; `EXERCISES` with `0`; no `DESCRIPTION` and no
     `TrainingDescriptionText`; `PAST SESSIONS` with `1 SESSION`; `TrainingStartSessionButton` not
     enabled.
4. **The editor** (`TrainingEditScreen`):
   - E1, D1's training in `Mode.Edit(isCreate = false)` with the tag `Push` only and no description:
     the title `Push Day`; `TrainingEditCloseButton` with `Close`; the labels `Name` and
     `Description`; `TrainingEditNameField` holding `Push Day`; `Exercises (2)`;
     `TrainingExerciseCard_ex-1` with `1.`, `Bench press` and `TrainingExerciseCardPlanLine_ex-1`
     `60×10`, its `TrainingExerciseCardRemove_ex-1` with `Remove exercise` and its
     `TrainingExerciseCardDrag_ex-1` holding `Reorder`; `TrainingExerciseCardPlanLine_ex-2`
     `no plan yet`; `TrainingEditAddExerciseButton` `Add exercise`; `TAGS`;
     `AppTagFormRowSelected_tag-1` `Push` and `AppTagFormRowAdd` `+ tag` (kit's); the description
     placeholder `Optional notes — focus, week, etc.`; `TrainingEditCancelButton` `Cancel` and
     `TrainingEditSaveButton` `Save` (kit's); no `PlanEditorBodyEmpty`;
   - E2, E1 with `expandedExerciseUuids = ["ex-2"]`: `PlanEditorBodyEmpty`, after scrolling to it;
   - E3, `State.create(uuid = null)` with `nameError = true`: the title `New training`, the error
     `Name is required` and `Exercises (0)`;
   - E4, E1 with an empty name: the title `Edit training`.
5. **The picker** (`ExercisePickerSheet`):
   - P1, query `""`, both rows, `ex-1` selected: `Add exercises`; `ExercisePickerSearch` with the
     placeholder `Search exercises…`; `ExercisePickerRow_ex-1` with `Bench press` and
     `Push · Chest`; `ExercisePickerRow_ex-2` with `Pull-up` and no tag line; `ExercisePickerCancel`
     `Cancel`; `ExercisePickerConfirm` `Add (1)`, enabled;
   - P2, no row and nothing selected: `ExercisePickerEmpty` `No exercises match`, neither row, and
     `ExercisePickerConfirm` `Add (0)`, not enabled.
6. **The menu content** (`TrainingDetailMenuSheetContent`): with `canPermanentlyDelete = true`,
   `TrainingDetailArchiveMenuItem` `Archive` and `TrainingDetailPermanentDeleteMenuItem`
   `Delete permanently`; with `false`, the archive item only.
7. **Dispatch:** two exact lists, in this order. Actions:

   | Interaction | Action |
   | --- | --- |
   | D1: `TrainingDetailBackButton` | `Click.OnBackClick` |
   | D1: `TrainingDetailMenuButton` | `Click.OnDetailMenuClick` |
   | D1: `TrainingExerciseReadCard_ex-1` | `Click.OnExerciseRowClick("ex-1")` |
   | D1: `TrainingHistoryRow_s-2` | `Click.OnPastSessionClick("s-2")` |
   | D1: `TrainingDetailEditButton` | `Click.OnEditClick` |
   | D1: `TrainingStartSessionButton` | `Click.OnStartSessionClick` |
   | E1: `TrainingEditCloseButton` | `Click.OnCancelClick` |
   | E1: `SetText` `Leg Day` into `TrainingEditNameField` | `Input.OnNameChange("Leg Day")` |
   | E1: `TrainingExerciseCardHead_ex-1` | `Click.OnExerciseCardToggle("ex-1")` |
   | E1: `TrainingExerciseCardRemove_ex-2` | `Click.OnExerciseRemove("ex-2")` |
   | E1: `TrainingEditAddExerciseButton` | `Click.OnAddExerciseClick` |
   | E1: `AppTagFormRowAdd` | `Click.OnTagAddClick` |
   | E1: the `Remove tag` icon inside `AppTagFormRowSelected_tag-1` | `Click.OnTagRemove("tag-1")` |
   | E1: `SetText` `Light week.` into `TrainingEditDescriptionField` | `Input.OnDescriptionChange("Light week.")` |
   | E1: `TrainingEditCancelButton` | `Click.OnCancelClick` |
   | E1: `TrainingEditSaveButton` | `Click.OnSaveClick` |
   | menu, `true`: `TrainingDetailArchiveMenuItem` | `Click.OnArchiveClick` |
   | menu, `true`: `TrainingDetailPermanentDeleteMenuItem` | `Click.OnPermanentDeleteClick` |

   Picker callbacks, P1: `SetText` `ben` into `ExercisePickerSearch` → `search:ben`;
   `ExercisePickerRow_ex-2` → `toggle:ex-2`; `ExercisePickerConfirm` → `confirm`;
   `ExercisePickerCancel` → `dismiss`.

   `SetText` is past-session's `performTextReplacement`. The test-held State does not change on
   dispatch, so each list records exactly what production dispatches. If `SetText` or a click inside
   the picker's sheet is unavailable on the simulator, STOP with the exception (Section 14).

It may use production test tags, texts, content descriptions, semantics action labels, scrolling
and deterministic fixtures. It may not add a production test tag, a platform host, a repository, a
database, a fake Store, a fake `StringResolver`, a `ResourceWrapper`, a screenshot baseline or a
second identity. It does not drag a card: the reorder gesture has no scene path.

The module's Native oracle is 63 tuples: the 62 portable identities and this scene.

### 11.5 What is not covered

- RU values execute only in the 12 goldens (Paparazzi, Android host); no unit test or scene reads
  `values-ru`, and the topology gate pins the RU values statically.
- D2's guard races only in commonTest's captured launch, not on a device; no identity measures the
  confirm's one-dispatch delay.
- The graph's wiring — snackbars, undo actions, `BackHandler`, the discard and conflict sheets and
  their dismissal gestures — has no scene path; ClickHandlerTest covers the handlers' State.
- The reorder drag has no scene path; the device test and the goldens cover the card.
- The history rows' medium date runs only through `AndroidResourceWrapper` on Android; no test
  formats one off-device.
- The inherited tag race of Section 17 is raced by no identity.

testing.md records these (Section 9.2).

## 12. CI ownership and positive verification

### 12.0 D4 and D5 — extra golden classes and same-file copy helpers in the generic gate (commit 1)

Two blind spots of the shared gate, both measured at the base:

- **D4.** `FeatureEntry` pins one golden class: `golden_test` with its exact `golden_methods`. Every
  existing entry has one. Single-training has two, exercise three and live-workout four. As an
  entry, `TrainingEditGoldenTest`'s three methods would be pinned by no gate rule, and nothing
  requires that no other androidHostTest file renders goldens (exercise-chart's
  `ChartFolderPrRuleParityTest` is such a file today, without goldens).
- **D5.** 7.12's D3 check and the token list read the State lambda's own text. A lambda that calls a
  same-file function that resolves copy passes once it carries `.copy(`: today's `applyLoaded`
  reaches `resourceWrapper.formatMediumDate` through `toHistoryItems()` inside a compare-and-set
  lambda. The six shared entries have no such call at the base (Section 2); exercise's `applyLoaded`
  is the next.

Commit 1 changes only `.github/scripts/assert_kmp_ui_source_topology.py`, in six places: the field,
three patterns, two functions, the `helpers` line and the per-body check in the State-lambda loop,
and the D4 checks after the `golden_test` check. No other entry, contract, `MODULES` or
`FEATURE_ENTRIES` line changes.

Reference implementation. It is normative for the field, the patterns, the function boundaries, the
`invoke` exclusion, the comment stripping and the messages; formatting may differ.

~~~python
# FeatureEntry, after bare_previews:
    # D4 (kmp-phase-7-14-single-training-feature.md Section 12.0): every golden class beside
    # golden_test, path relative to root -> its exact methods. Empty by default, so every earlier
    # entry stays byte-identical.
    extra_golden_tests: dict = field(default_factory=dict)

# After FEATURE_STATE_LAMBDA_RESOLVERS:
# D5 (kmp-phase-7-14-single-training-feature.md Section 12.0): what a State lambda may not hold,
# it may not reach through a function of its own file either. A function resolves copy when its
# text holds a side-effect token above or names the wrapper or the resolver at all. `invoke` is
# the handler's entry point, and `.invoke(` on a function value would collide with it.
FEATURE_COPY_HELPER_REFERENCES = re.compile(r"\b(?:stringResolver|resourceWrapper)\b")
SAME_FILE_FUNCTION = re.compile(
    r"^(?P<indent>[ ]*)(?:(?:private|internal|protected|public|override|open|suspend|inline|"
    r"tailrec|operator|infix)\s+)*fun\s+(?:<[^>]*>\s*)?(?:[\w.<>?, ]+\.)?(?P<name>\w+)\s*\(",
    re.MULTILINE,
)

# D4: golden_test's pattern, for the extra classes and the completeness walk.
GOLDEN_METHODS = re.compile(r"@EnumSource\(GoldenTheme::class\)\s+fun\s+(\w+)\(")

# Before read_catalog:
def calls_function(source: str, name: str) -> bool:
    return re.search(rf"(?<!\w){re.escape(name)}\s*\(", source) is not None


def copy_resolving_functions(source: str) -> set:
    """D5: the functions of a comment-free file that resolve copy, directly or through another
    of them. A function runs from its line to the next line indented at most as deep that is not
    its own closing bracket."""
    lines = source.splitlines(keepends=True)
    texts: dict[str, list[str]] = {}
    for match in SAME_FILE_FUNCTION.finditer(source):
        if match.group("name") == "invoke":
            continue
        indent = len(match.group("indent"))
        first = source.count("\n", 0, match.start())
        last = first + 1
        while last < len(lines):
            text = lines[last].strip()
            depth = len(lines[last]) - len(lines[last].lstrip(" "))
            if text and (depth < indent or (depth == indent and text[0] not in ")]}")):
                break
            last += 1
        texts.setdefault(match.group("name"), []).append("".join(lines[first:last]))
    resolving = {
        name
        for name, bodies in texts.items()
        for body in bodies
        if FEATURE_COPY_HELPER_REFERENCES.search(body)
        or any(token in body for token in FEATURE_STATE_LAMBDA_SIDE_EFFECTS)
    }
    grown = True
    while grown:
        grown = False
        for name, bodies in texts.items():
            if name not in resolving and any(
                calls_function(body, helper) for body in bodies for helper in resolving
            ):
                resolving.add(name)
                grown = True
    return resolving

# check_feature_entry, the State-lambda loop, directly after the /mvi/handler/ filter:
        helpers = copy_resolving_functions(strip_kotlin_comments(source))

# check_feature_entry, inside the per-body loop, after the resolver-reference check:
                for helper in sorted(helpers):
                    if calls_function(strip_kotlin_comments(body), helper):
                        failures.append(
                            f"{module}: {call} in {path.relative_to(root)} calls {helper!r}, "
                            "a same-file function that resolves copy; resolve it before the lambda"
                        )

# check_feature_entry, directly after the golden_test check:
    # D4: every extra golden class is pinned like golden_test, and no other androidHostTest file
    # renders goldens.
    for relative, expected_methods in entry.extra_golden_tests.items():
        path = root / relative
        methods = GOLDEN_METHODS.findall(path.read_text(encoding="utf-8") if path.is_file() else "")
        if methods != expected_methods:
            failures.append(
                f"{module}: golden methods in {relative} must be exactly {expected_methods}; "
                f"actual={methods}"
            )
    declared_goldens = {entry.golden_test, *entry.extra_golden_tests}
    for path in sorted((root / "src/androidHostTest").rglob("*.kt")):
        relative = path.relative_to(root).as_posix()
        if relative not in declared_goldens and GOLDEN_METHODS.search(
            path.read_text(encoding="utf-8")
        ):
            failures.append(
                f"{module}: {relative} renders goldens but is neither golden_test nor "
                "in extra_golden_tests"
            )
~~~

The diff is 87 insertions and 0 deletions, every added line within 100 columns. The existing
`golden_test` check, its message and its regex stay byte-identical; `GOLDEN_METHODS` repeats that
regex. D5 adds a line only where a State lambda calls a copy-resolving function, so every message
the token list or 7.12's D3 check reports stays byte-identical, and an entry with no such call
prints nothing new.

**Proof that commit 1 changes no existing behaviour.** 7.13 Section 12.0's procedure, unchanged: on
the committed tree, with the base's script saved aside as `old_gate.py` (sha256
`a823d575c8f0bc1c783ddf4547217d6a953989a5ab0e23bf268a676622634dec` at the discovery baseline), under
every Python the host has:

1. GREEN: old and new outputs on the unmodified tree are byte-identical, exit code and stderr
   included, and equal to BASE_GATE_OUTPUT (647 lines at the discovery baseline).
2. Differential, one exact occurrence per mutation, scored by both scripts and restored
   byte-exactly (sha256 and mode) before the next, with a fresh GREEN after the last. **M** rows:
   old and new are byte-identical and RED with exactly the named lines. **N** rows: the old script
   is GREEN (byte-equal to BASE_GATE_OUTPUT) and the new one RED with exactly the named lines.
   **X** rows: both RED; the new output is the old one plus exactly the named lines. **G** rows:
   both GREEN.

   Paths: `ARCHIVE` is
   `feature/archive/src/commonMain/kotlin/io/github/stslex/workeeper/feature/archive/mvi/handler/ArchivePagingHandler.kt`;
   `CHART` is
   `feature/exercise-chart/src/commonMain/kotlin/io/github/stslex/workeeper/feature/exercise_chart/mvi/handler/CommonHandler.kt`;
   `PARITY` is
   `feature/exercise-chart/src/androidHostTest/kotlin/io/github/stslex/workeeper/feature/exercise_chart/domain/ChartFolderPrRuleParityTest.kt`.
   "ARCHIVE's lambda" is the insertion point directly before the two lines
   `                it.copy(` / `                    exerciseCount = count,`; "CHART's lambda" is
   directly before `            current.copy(` /
   `                emptyReason = EmptyReason.LOAD_FAILED,`; "PARITY's end" is directly before the
   file's final `}`. In the messages, `<A>`, `<C>` and `<P>` stand for the three paths relative to
   their module root (they start with `src/`); the implementation writes them out.

   | # | Mutation | Result |
   | --- | --- | --- |
   | M1 | archive `ArchiveGoldenTest.kt`: `fun screenTrainingsNoRows(` → `fun screenTrainingsNoRowsProbe(` | both RED, identical: `feature:archive: golden methods must be exactly ['rowExercise', 'rowTraining', 'rowClamped', 'pagingLoading', 'pagingError', 'screenExercisesNoRows', 'screenTrainingsNoRows']; actual=['rowExercise', 'rowTraining', 'rowClamped', 'pagingLoading', 'pagingError', 'screenExercisesNoRows', 'screenTrainingsNoRowsProbe']` |
   | M2 | ARCHIVE's lambda: `                val probe = getString(Res.string.feature_archive_meta_separator)` | both RED, identical: `feature:archive: updateState in <A> resolves a resource inside the State lambda: 'getString('` and `feature:archive: updateState in <A> resolves a resource inside the State lambda: 'Res.'` |
   | M3 | CHART's lambda: `            val probe = stringResolver` | both RED, identical: `feature:exercise-chart: updateStateImmediate in <C> references 'stringResolver' inside the State lambda; resolve copy before it` |
   | N1 | PARITY's end: a blank line, then `    @ParameterizedTest`, `    @EnumSource(GoldenTheme::class)`, `    fun probe(theme: GoldenTheme) = Unit` | new only: `feature:exercise-chart: <P> renders goldens but is neither golden_test nor in extra_golden_tests` |
   | N2 | ARCHIVE's lambda: `                val probe = metaCopy()` | new only: `feature:archive: updateState in <A> calls 'metaCopy', a same-file function that resolves copy; resolve it before the lambda` |
   | N3 | CHART's lambda: `            val probe = processInit()` | new only: `feature:exercise-chart: updateStateImmediate in <C> calls 'processInit', a same-file function that resolves copy; resolve it before the lambda` |
   | X1 | ARCHIVE's lambda: `                val probe = metaCopy() to resourceWrapper` | old: `feature:archive: updateState in <A> references 'resourceWrapper' inside the State lambda; resolve copy before it`; new adds exactly N2's line |
   | X2 | PARITY's end: a blank line, `    @Test`, `    fun probeIdentity() = Unit`, then N1's method | old: `feature:exercise-chart: exact test identities drifted in <P>; missing=[], unexpected=['probeIdentity']`; new adds exactly N1's line |
   | G1 | ARCHIVE's lambda: `                // metaCopy() resolves before this lambda, never inside it.` | both GREEN |
   | G2 | ARCHIVE: `            val probe = metaCopy()` directly after `            val label = getString(Res.string.feature_archive_segment_exercises, count)` | both GREEN |
   | G3 | CHART's lambda: `            val probe = current.requestOf()` | both GREEN |
   | G4 | PARITY's end: `    // @EnumSource(GoldenTheme::class) belongs to ExerciseChartGoldenTest alone.` | both GREEN |

   M1 pins that D4's walk does not report `golden_test` itself; M2 and M3 that D5 adds nothing where
   the lambda resolves copy in its own text; N3 that D5 is transitive (`processInit` calls
   `loadChart`, which names the resolver); X1 and X2 that the new lines are appended, never
   reordered. G1 pins that D5 reads comment-stripped text, G2 that a helper call outside a State
   lambda is not D5's, G3 that a same-file extension that resolves nothing passes, and G4 that a
   comment naming the annotation renders no golden.
3. The diff of commit 1 touches only the topology script, and in it only the six places above.

A differing byte in step 1 or step 2's M rows, an N or X row that is not RED with exactly its lines,
or a G row that is not GREEN is a STOP.

The proof was dry-run under Python 3.9, 3.11, 3.12, 3.13 and 3.14 against the discovery tree with
D4 and D5 applied, and every row produced exactly its result, byte-identical across interpreters.
That dry run is not evidence.

### 12.1 Topology gate data (normative)

**Commit 3** carries the minimal delta that keeps its tree green (precedent 7.8 G1):

- `MODULES["feature:single-training"]` holds the 61 files under `src` of Section 9.1, after the
  exercise-chart entry and in its format: `kotlin_source_sets` `commonMain`, `commonTest`,
  `androidHostTest`, `androidDeviceTest`, `iosTest`; `resource_dirs`
  `src/commonMain/composeResources/values` and `src/commonMain/composeResources/values-ru`;
- `EXPECTED_APP_DEPS_READERS` loses the single-training entry (5 → 4). The script compares it
  exactly with every `appDeps<…Factory>(` reader in the tree;
- S10: `FEATURE_RESOURCE_OWNERS`' line
  `Path("feature/single-training/src/main/res"): FEATURE_SINGLE_TRAINING_RESOURCES,` becomes
  `Path("feature/single-training/src/commonMain/composeResources"): FEATURE_SINGLE_TRAINING_RESOURCES,`.
  It is the first move to edit gate data outside its own entry: the catalog move alone turns the
  gate RED on this owner (control 46 is that RED).

That diff is 78 insertions and 2 deletions.

**Commit 4** appends `SINGLE_TRAINING_ENTRY` and its two checks before `FEATURE_ENTRIES`, and the
line `    SINGLE_TRAINING_ENTRY,` after `    EXERCISE_CHART_ENTRY,` in that multi-line tuple.
Nothing else in the script changes: commit 4's topology diff is purely additive (518 insertions in
the prototype).

| Field | Value |
| --- | --- |
| module / short_name | `feature:single-training` / `single-training` |
| root / package_dir | `feature/single-training` / `io/github/stslex/workeeper/feature/single_training` |
| build_fragments | every plugin, `packageOfResClass`, `includeJavax()`, dependency and `apply(from = …)` line of Section 10, each exactly once (26), except `implementation(kotlin("test"))`, which kotlin_test_count covers |
| kotlin_test_count | 2 |
| catalog | the 38 rows of Section 3.4, in file order, with the exit values (decoded text; `‘`, `’`, `—` and `…` as the characters); the plural's EN items `(one, other)` and RU items `(one, few, many, other)` |
| resource_prefix | `feature_training_` |
| payload_files | `DialogState.kt`, `SingleTrainingStore.kt`, `SingleTrainingUiModels.kt` (plus everything under domain/). The tag mapper and the two copy-reading handlers are deliberately absent |
| store_file / state_fields | `mvi/store/SingleTrainingStore.kt` / the 22 fields, in order: `uuid: String?`, `mode: Mode`, `draftEpoch: Int`, `isSaving: Boolean`, `name: String`, `nameError: Boolean`, `description: String`, `tags: ImmutableList<AppTagItem>`, `availableTags: ImmutableList<AppTagItem>`, `tagSearchQuery: String`, `exercises: ImmutableList<TrainingExerciseItem>`, `expandedExerciseUuids: ImmutableSet<String>`, `pendingSetRestores: ImmutableList<PendingSetRestore>`, `pastSessions: ImmutableList<HistorySessionItem>`, `historyCount: Int`, `activeSession: ActiveSessionDomain?`, `canPermanentlyDelete: Boolean`, `originalSnapshot: Snapshot?`, `pickerState: PickerState`, `dialogState: DialogState`, `editCopy: TrainingEditCopy`, `isLoading: Boolean` |
| resource_wrapper_readers | `mvi/handler/CommonHandler.kt` |
| resource_wrapper_dates | `{"mvi/handler/CommonHandler.kt": {"formatMediumDate": 1}}` |
| preview_subjects / preview_inventory | `ui/components/ExercisePickerSheet.kt` → `ExercisePickerSheet`, `ui/components/TrainingExerciseCard.kt` → `TrainingExerciseCard`, `ui/components/TrainingHistoryRow.kt` → `TrainingHistoryRow` / `{}` |
| bare_previews (7.13's D5) | `ui/TrainingDetailScreen.kt` 1, `ui/components/TrainingDetailMenuSheetContent.kt` 1 |
| suppressions | the eight production annotations of Section 0.2 under their seven `src/commonMain/…` paths, each file's arguments as one string (`['"TooManyFunctions", "LongParameterList"']`, …); nothing under any test source set. The gate's message counts files: "the seven inherited production" |
| test_names | the 62 of Section 3.2 under their four commonTest paths; the scene's name under its iosTest path |
| golden_test / golden_methods | `src/androidHostTest/…/golden/TrainingDetailGoldenTest.kt` / `detailWithPlans`, `detailNoPlanExercise`, `detailEmptyHistory` |
| extra_golden_tests (D4) | `{"src/androidHostTest/…/golden/TrainingEditGoldenTest.kt": ["editCollapsed", "editExpanded", "editEmpty"]}` |
| device_test / device_fragments | `src/androidDeviceTest/…/SingleTrainingScreenTest.kt` / `@Smoke`, `fun cardHead_tapTogglesExpansion()`, `fun cardHead_removeIsNotSwallowedByTheHeadTap()`, `fun expandedCard_showsThePlanBodyWithItsSetbar()`, each exactly once |
| factory names | Section 0.3; `create_args = "screen"`; feature_file `di/SingleTrainingFeature.kt`, graph_file `ui/SingleTrainingGraph.kt` |
| identity_test / identity_fragments | `app/app/src/test/…/di/SingleTrainingExtensionIdentityTest.kt` / the `singleTraining(uuid)` helper of Section 6, on one line (the gate compares whitespace-compacted text) |
| identity_names / identity_accessor | the six names of Section 3.6 / below |
| extra_root_fragments | none |
| extra_checks | `single_training_copy_contract`, `single_training_dependency_contract` below |

`identity_accessor` is the pattern `(?<!AppGraph)\.singleTraining\((?:"tr-1"|null)\)`. It matches
six times, once per identity; the fifth identity's `"tr-2"` call and the helper's declaration are
deliberately not counted.

`single_training_copy_contract` checks Sections 5.2–5.4. Every count is over sources with Kotlin
comments stripped, and it reuses past-session's helpers' shape (`source_of`, `require`,
`require_parameters`):

1. Parameters, exactly, in `mvi/store/SingleTrainingStore.kt`: `HapticClick`
   `["type: HapticFeedbackType"]`; `ShowArchiveSuccess`, `ShowArchiveBlocked` and `ShowSaveError`
   `["message: String"]`; `ShowSetRemovedUndo` `["message: String", "exerciseUuid: String",
   "set: PlanSetUiModel", "index: Int", "draftEpoch: Int"]`; `ShowExerciseRemovedUndo`
   `["message: String", "item: TrainingExerciseItem", "wasExpanded: Boolean", "draftEpoch: Int"]`.
   In `mvi/store/DialogState.kt`: `PermanentDeleteConfirm` `["title: String", "body: String",
   "impactSummary: String", "confirmLabel: String"]`; `ActiveSessionConflict`
   `["sessionUuid: String", "activeSessionName: String", "progressLabel: String"]`. In
   `mvi/model/SingleTrainingUiModels.kt`: `TrainingEditCopy` `["noExercisesError: String",
   "exerciseRemoved: String", "setRemoved: String"]`.
2. D2's defaults, exactly once each:
   `val EMPTY = TrainingEditCopy(noExercisesError = "", exerciseRemoved = "", setRemoved = "")` in
   `mvi/model/SingleTrainingUiModels.kt`, and `editCopy = TrainingEditCopy.EMPTY,` in
   `mvi/store/SingleTrainingStore.kt`.
3. `mvi/handler/ClickHandler.kt`: exactly once each of
   `private val stringResolver: StringResolver,`, `current.editCopy.noExercisesError`,
   `current.editCopy.exerciseRemoved`, `state.value.editCopy.setRemoved`,
   `if (latest.dialogState == DialogState.DetailMenu) {` and `latest.copy(dialogState = confirm)`;
   exactly eight `stringResolver.getString(`; and none of `ResourceWrapper`, `resourceWrapper` or
   `applySnapshotOrPop`.
4. `mvi/handler/CommonHandler.kt`: exactly once each of
   `private val resourceWrapper: ResourceWrapper,`, `private val stringResolver: StringResolver,`,
   `TrainingEditCopy(`,
   `onSuccess = { copy -> updateState { it.copy(editCopy = copy) } },`,
   `val exercises = result.exercises.toExerciseItems()` and
   `val pastSessions = result.recentSessions.toHistoryItems()`; exactly three
   `stringResolver.getString(`; exactly two `resolveEditCopy()` (its declaration and its one call,
   in `processInit`); and no `applyLoaded`.
5. F2, in that file: `val exercises = result.exercises.toExerciseItems()` occurs before
   `val pastSessions = result.recentSessions.toHistoryItems()`, which occurs before the next
   `updateState { current ->`; otherwise
   `feature:single-training: <file> must build the exercise items and the history rows before the load's State lambda (F2)`.
6. 7.11's rules 4–7: no commonMain import of CMP's top-level `getString` / `getPluralString`; under
   `mvi/`, every `getString(` / `getPluralString(` is a `stringResolver.` call and no file holds
   `stringResource(` / `pluralStringResource(`; no `ui/` file holds `getString(` /
   `getPluralString(`; both walks find at least one file.

The message shapes are past-session's: `<file> copy contract requires <fragment> exactly <n>;
found <m>`, `<file> <name> parameters must be exactly [...]; actual=[...]`, and so on.

`single_training_dependency_contract` (Sections 2, 3.5 and 10):

- the build file contains no `project(":core:data:database-test")`, with the message
  `feature:single-training: build must not depend on :core:data:database-test`;
- `domain/mapper/SingleTrainingDomainMapper.kt` names `core.data.database` (comments stripped),
  otherwise `<file> must be the module's one reader of core:data:database`;
- no other Kotlin file under `src` names `core.data.database`, with the message
  `<file> must not read core:data:database; only the domain mapper maps its data models`;
- no Kotlin file under `src/commonMain`, `src/commonTest` or `src/iosTest` names `java.`, imported
  or fully qualified (comments stripped; `(?<![\w.])java\.`), with the message
  `<file> names java.* in shared code`.

The production `implementation(project(":core:data:database"))` edge itself is a build fragment.

`assert_kmp_ios_smoke.py` gains a `feature:single-training` entry, after exercise-chart's, with
`results_dir` `feature/single-training/build/test-results/iosSimulatorArm64Test`, the known suite
prefix, and the 63 tuples of Section 11.4: the 62 portable identities under their suites'
classnames (`io.github.stslex.workeeper.feature.single_training.mvi.handler.ClickHandlerTest`,
`….CommonHandlerTest`, `….NavigationHandlerTest` and
`io.github.stslex.workeeper.feature.single_training.mvi.store.SingleTrainingStateRouteArgTest`),
through `natives(…)`, and the scene's
(`io.github.stslex.workeeper.feature.single_training.SingleTrainingFeatureSceneIosTest`) through
`native(…)`. The names are written as UTF-8 (`ensure_ascii=False`): the Cyrillic identity appears
as its letters.

`android_build_unified.yml`, KMP iOS kit smoke job:

- `:feature:single-training:iosSimulatorArm64Test` in the simulator test command and
  `:feature:single-training:linkDebugTestIosArm64` in the device-link command, each after
  exercise-chart's;
- the binary-assert loop's last line becomes `feature/exercise-chart feature/single-training; do`,
  `$linked/13 linked` becomes `$linked/14 linked`, and
  `feature/single-training/build/test-results/iosSimulatorArm64Test/` joins the upload list after
  exercise-chart's;
- the job-header comment rewraps within 100 columns to "…, past-session, home," / "exercise-chart
  and single-training features' Phase-7 Native tests on the iOS simulator, then a" / "checked-in
  script requires every exact per-module XML identity.", and the two "thirteen modules'" comments
  say "fourteen modules'".

That is 10 insertions and 7 deletions. The job, step and context names stay. The build job's
comment "all 13 golden-holding modules" stays true: single-training already holds goldens.

The fourteen modules, for every command below: `:core:ui:kit`, `:core:ui:navigation`,
`:core:ui:mvi`, `:core:ui:start-mode`, `:core:ui:plan-editor`, `:feature:image-viewer`,
`:feature:plan-editor`, `:feature:archive`, `:feature:all-trainings`, `:feature:all-exercises`,
`:feature:past-session`, `:feature:home`, `:feature:exercise-chart`, `:feature:single-training`.

The prototype run of this data (Section 13) printed, on the simulated tree, byte-identical under
Python 3.9, 3.11, 3.12, 3.13 and 3.14 and with exit 0:

| Tree | Lines | What changed |
| --- | ---: | --- |
| after commit 1 | 647 | nothing: equal to the base |
| after commit 2 | 647 | nothing |
| after commit 3 | 711 | single-training's `MODULES` block (62 lines: `61 exact source/resource/test files` and the files); `app:common API edges and 4 remaining Context.appDeps readers are exact`; `24 commonMain catalogs walked`, single-training's EN with 8 placeholders and RU with 10 |
| after commit 4 | 712 | `single-training catalogs, semantic State, suppressions, previews, test identities, and explicit factory flow are exact` |

The oracle entry verified 63 exact identities against synthetic UTF-8 XML, and a Cyrillic tuple
missing from that XML gave `found 0`. That run is not evidence.

### 12.2 Entry gate before any edit

7.13 Section 12.2 applies, plus:

- `53605e126` (Phase 7.13's merge) is an ancestor of the base;
- `git diff 53605e126..<base> -- feature/single-training` is empty;
- every commit since `53605e126` that touches a Section 9.2 path is listed, with whether it
  conflicts;
- the census of Sections 2 and 3.1–3.7 is reproduced on the base: 39 production files and 3,635
  lines; the 58 identities (47 / 4 / 5 / 2) by `@Test` and by the gate's name regex; 39 strings and
  1 plural per locale, identical key order; the nine production suppressions in eight files; the
  five readers; 43 State lambdas, two without `.copy(`, and D5's census (Section 2) with the
  session's own script implementing Section 12.0's function; the four PNG manifests and the blob-set
  hash;
- the current thirteen-module Native command and oracle are green;
- the Mac's simulator runtime and Xcode first-launch state are recorded (`xcrun simctl list
  runtimes`); a missing runtime or an unfinished first launch is a STOP with the exact command for
  the maintainer.

Baseline commands, with the stable flags:

    ./gradlew :feature:single-training:assembleDebug
    ./gradlew :feature:single-training:testDebugUnitTest      # 4 suites, 58 cases; goldens excluded
    ./gradlew :feature:single-training:verifyPaparazziDebug   # 6 suites, 70 cases: 12 golden + 58
    ./gradlew :app:app:testDebugUnitTest --tests '*SingleTrainingExtensionIdentityTest*' --tests '*StringResolverIdentityTest*'   # 8 cases
    ./gradlew :feature:single-training:connectedDebugAndroidTest   # on the recorded serial: 3 cases
    ANDROID_SERIAL=<serial> ./gradlew :app:app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=io.github.stslex.workeeper.app.RouteReachabilityTest --max-workers=2 --continue   # 15 cases

`verifyPaparazziDebug` must print
`Visual gate live: 12 golden test case(s) executed for 12 golden image(s).`

### 12.3 Focused gates after the implementation

After commit 1: Section 12.0's proof.

After commit 2 (still `src/main`), on the committed tree:

- `:feature:single-training:testDebugUnitTest` (4 suites, 62 cases, names exactly Section 3.2,
  S14's four among them) and `:feature:single-training:verifyPaparazziDebug` (6 suites, 74 cases,
  and `Visual gate live: 12 golden test case(s) executed for 12 golden image(s).`): D2 and F2 on the
  JVM first;
- the six `SingleTrainingExtensionIdentityTest` identities and the two `StringResolverIdentityTest`
  identities (commit 2 changes no constructor: both handlers still read `ResourceWrapper`);
- the module's device test on the recorded serial (3 cases);
- detekt alone;
- the topology script, byte-identical to its commit-1 output.

After commit 3, on the committed tree:

    python3 .github/scripts/assert_kmp_ui_source_topology.py   # 711 lines
    ./gradlew :feature:single-training:assembleDebug            # Android and both iOS klibs
    ./gradlew :feature:single-training:testAndroidHostTest      # 4 suites, 62 cases
    ./gradlew :feature:single-training:verifyPaparazziDebug     # 6 suites, 74 cases: 12 golden + 62
    ./gradlew :feature:single-training:iosSimulatorArm64Test    # 63 tuples
    ./gradlew :app:common:assembleDebug

Then:

- `./gradlew detekt --continue` alone, on every commit's tree;
- the six identity tests, whose real graph now hands both handlers the app-scoped `StringResolver`,
  and the two `StringResolverIdentityTest` identities (fresh XML);
- on an explicit API-34 serial with animations disabled, as `ui_tests.yml` does: the module's device
  test through `:feature:single-training:connectedDebugAndroidTest` (the KMP convention's alias onto
  `connectedAndroidDeviceTest`, 3 cases), and the whole `RouteReachabilityTest` class with the
  command of Section 12.2 (15 cases, the two single-training journeys among them; fresh XML);
- the relative PNG proof of Section 3.7.

**Early Native check.** Run `:feature:single-training:iosSimulatorArm64Test` as soon as production
and commonTest compile for Native, before the scene and the app paths. That run is the first Native
compilation of this module's CMP `BackHandler` opt-in and `navigationBarsPadding()`, and the first
Native run of a Cyrillic identity: it must give the 62 identities by name in the fresh XML,
`the История count is the total not the visible page` among them, read as UTF-8.

After commit 4: the fourteen-module Native command (`--continue`) and the oracle, the
fourteen-module device link (`14/14 linked`), the topology script (712 lines), and detekt alone.

### 12.4 Repository and visual gates

7.13 Section 12.4 applies unchanged: assemble, lint and unit; detekt alone;
`assembleDebugAndroidTest`; `verifyPaparazziDebug`; `:lint-rules:test`; the personal-data gate; the
canonical Smoke and Regression device suites; diff hygiene; the PNG manifest check of Section 3.7;
the mockup shell gate, where runnable.

### 12.5 Final diff and remote proof

7.13 Section 12.5 applies, with: the Section 9 boundary (62 module paths, 14 others); 63 Native
tuples; 12 unchanged golden blobs; the eight production suppressions and no test suppression.
Record the KMP iOS kit smoke job's duration on the final head against its 120-minute timeout, next
to 7.13's.

## 13. Mandatory known-negative controls

7.13 Section 13's procedure applies: every control runs fresh GREEN → one named observable RED →
automatic exact restoration → fresh GREEN; Gradle- and device-scored controls through
`documentation/mockups/mutation_harness.py` in one-shot mode, with `--no-configuration-cache
--console=plain` inside `--task`; Python-scored controls against a byte-restoring copy in a detached
worktree under `.claude/worktrees/`; a compile-invalid mutation is never a RED where a compiled test
is the detector; no mutation is committed. Every control runs after commit 4, on its committed tree.
Where a row says "at least", the named identities must be RED and every other RED identity is
recorded. "Only" means no other identity, suite or line is RED.

Paths below are relative to
`src/commonMain/kotlin/io/github/stslex/workeeper/feature/single_training/` unless they start with
`src/`, `app/` or `feature/`; `…` abbreviates the module-relative path, which the implementation
writes out.

**From 7.13, with the substitutions of Section 0.3:**

| # | Mutation | Scorer: expected RED |
| ---: | --- | --- |
| 1 | delete `ui/components/TrainingDetailMenuSheetContent.kt` | topology: source manifest mismatch, `missing=[…TrainingDetailMenuSheetContent.kt]`, and `…TrainingDetailMenuSheetContent.kt must hold bare @Preview annotations but is missing` |
| 2 | copy `di/SingleTrainingScope.kt` into `src/main/kotlin/…/di/` | topology: manifest `extra=[…]`, legacy source set `src/main`, unexpected Kotlin-bearing source set `src/main` |
| 3 | move `ui/components/TrainingDetailMenuSheetContent.kt` to `ui/TrainingDetailMenuSheetContent.kt` | topology: manifest `missing` and `extra`; `…ui/components/TrainingDetailMenuSheetContent.kt must hold bare @Preview annotations but is missing` and `…ui/TrainingDetailMenuSheetContent.kt must hold exactly 0 bare @Preview annotation(s); found 1` |
| 4 | `import android.util.Log` in `ui/components/TrainingDetailMenuSheetContent.kt` | topology: platform API in commonMain, `'import android.'` |
| 5 | rename the EN key `feature_training_picker_cancel` to `…_renamed` | topology: EN catalog mismatch (`order=drifted`), two ownership-drift lines |
| 6 | the RU value of `feature_training_picker_cancel` `Отмена` → `Отменить выбор` | topology: RU catalog mismatch, `order=exact` |
| 7 | move the EN `feature_training_picker_cancel` into `feature/settings/src/main/res/values/strings.xml` | topology: the EN catalog mismatch, and resource ownership drift |
| 8 | `val titleRes: Int = 0` as State's last field | topology: Store State must stay semantic |
| 9 | `resolveEditCopy`'s `setRemoved = stringResolver.getString(Res.string.feature_training_edit_plan_set_removed),` → `setRemoved = resourceWrapper.getString(0),` | topology: `…CommonHandler.kt resolves single-training copy through ResourceWrapper.getString; use the private Compose resources`; the copy contract `'stringResolver.getString(' exactly three times; found 2`; the `mvi/` rule `'getString(' 3 time(s), 2 through stringResolver` |
| 10 | `val label = stringResolver.getString(Res.string.feature_training_edit_plan_set_removed)` as the first line of the load's `updateState { current -> … }` | topology: `resolves a resource inside the State lambda: 'getString('` and `… 'Res.'`; the copy contract `'stringResolver.getString(' exactly three times; found 4` |
| 11 | the bare `@Preview` above `TrainingDetailMenuSheetContentPreview` → `@Preview(uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES)` | topology: forbidden `'android.content'` and `'uiMode'`; `exactly six portable previews are required; found 7`; `…TrainingDetailMenuSheetContent.kt must hold exactly 1 bare @Preview annotation(s); found 0` |
| 12 | `val context = LocalContext.current` and `context.appDeps<SingleTrainingGraph.Factory>()` in `SingleTrainingFeature` | topology: readers not the exact 4, forbidden `LocalContext` and `appDeps<`, factory invocation not inside retained creation |
| 13 | delete `val singleTrainingGraphFactory: SingleTrainingGraph.Factory` and its KDoc from `AppRootDeps` | topology: exact root-factory flow requires the accessor |
| 14 | `val graph = factory.createSingleTrainingGraph(screen)` before `rememberMetroStoreProcessor`, whose lambda then reads `graph.singleTrainingStore` | topology: `factory invocation must occur exactly inside retained Store creation` |
| 15 | a second `(context.applicationContext as AppRootDepsHolder).appRootDeps()` inside the admitted `remember(currentPhase.id) { … }` in `App.kt` | device: `UiAdmissionRaceTest`, as in 7.10 |
| 16 | hoist `val deps = remember(currentPhase.id) { …appRootDeps() }` above `if (admission.granted) {` | device: `UiAdmissionRaceTest`, as in 7.10 |
| 17 | identity helper `= asContribution<SingleTrainingGraph.Factory>().createSingleTrainingGraph(…)` | topology: `extension identities must not bypass AppRootDeps via asContribution`, and the identity-helper fragment |
| 18 | archive-specific; no counterpart | — |
| 19 | the discard's `exercises = snapshot.exercises,` → `exercises = latest.exercises,` | host and Native: `OnConfirmDiscard restores an exercise the edit removed`, `OnConfirmDiscard restores the positions a reorder changed` and `OnConfirmDiscard restores the plan a card edited`, and only them |
| 20 | the argument rule: the conflict progress read's `resolution.doneCount,` / `resolution.totalCount,` swapped | host and Native: `OnStartSessionClick reports the persisted active session progress`, and only it, at the strict fake |
| 21 | the scene's `setContent { … }` body → `setContent { }` | Native: the scene, at its first node assertion |
| 22 | production `ui/TrainingDetailScreen.kt`: the back button's `onClick = { consume(Action.Click.OnBackClick) },` → `onClick = { },` | Native: the scene's action list |
| 23 | rename the scene method to `renamedSceneSubstitute` | Gradle GREEN, then the oracle: `feature:single-training: expected exactly one testcase … found 0` |
| 24 | flip (XOR 0xFF) the last byte of `…golden_TrainingEditGoldenTest_editEmpty_light.png` | PNG assertion: blob drift, blob-set drift, the all-PNG and Paparazzi manifest drift |
| 25 | a compile-valid overlong assertion line in `src/commonTest/…/mvi/handler/NavigationHandlerTest.kt` | root detekt, alone: findings on that line only |

**New in 7.14:**

| # | Mutation | Scorer: expected RED |
| ---: | --- | --- |
| 26 | D4: `TrainingEditGoldenTest`'s `fun editEmpty(` → `fun editEmptyProbe(` | topology: `golden methods in src/androidHostTest/…/golden/TrainingEditGoldenTest.kt must be exactly ['editCollapsed', 'editExpanded', 'editEmpty']; actual=['editCollapsed', 'editExpanded', 'editEmptyProbe']` |
| 27 | D4: copy `golden/TrainingEditGoldenTest.kt` to `golden/TrainingEditCopyGoldenTest.kt` (androidHostTest) | topology: manifest `extra=[…TrainingEditCopyGoldenTest.kt]` and `…TrainingEditCopyGoldenTest.kt renders goldens but is neither golden_test nor in extra_golden_tests` |
| 28 | D5 over F2: the load lambda's `pastSessions = pastSessions,` → `pastSessions = result.recentSessions.toHistoryItems(),` | topology: `updateState in …CommonHandler.kt calls 'toHistoryItems', a same-file function that resolves copy; resolve it before the lambda` |
| 29 | D5, transitive: `processInit()` as the first line of the load's `updateState { current -> … }` | topology: `updateState in …CommonHandler.kt calls 'processInit', a same-file function that resolves copy; resolve it before the lambda` |
| 30 | D2: delete the `resolveEditCopy()` call from `processInit` | topology: `…CommonHandler.kt copy contract requires 'resolveEditCopy()' exactly two times; found 1`; host and Native: `Init resolves the editor's constant copy into State`, and only it |
| 31 | D2: the save error's `message = current.editCopy.noExercisesError` → `message = ""` | topology: `…ClickHandler.kt copy contract requires 'current.editCopy.noExercisesError' exactly once; found 0`; host and Native: `the save error carries the copy Init resolved`, and only it |
| 32 | D2: the exercise toast's `message = current.editCopy.exerciseRemoved,` → `message = current.editCopy.setRemoved,` | topology: `… requires 'current.editCopy.exerciseRemoved' exactly once; found 0`; host and Native: `the undo toasts carry the copy Init resolved`, and only it |
| 33 | D2: the confirm's guard `if (latest.dialogState == DialogState.DetailMenu) {` → `if (true) {` | topology: `… requires 'if (latest.dialogState == DialogState.DetailMenu) {' exactly once; found 0`; host and Native: `a menu dismissed before the confirm's copy lands stays dismissed`, and only it |
| 34 | F2's pure half: `val exercises = result.exercises.toExerciseItems()` moves from before the load's lambda to directly after its `val training = …` line | topology: exactly `…CommonHandler.kt must build the exercise items and the history rows before the load's State lambda (F2)`. D5 stays silent: `toExerciseItems` resolves no copy, so only F2's order check sees this |
| 35 | D3: the discard's inlined lambda → `updateState { latest -> latest.withSnapshotRestored() }` | topology: `updateState in …ClickHandler.kt must return a State copy` |
| 36 | F2 and D5: `val pastSessions = result.recentSessions.toHistoryItems()` moves into the load's lambda, after its `val training = …` line | topology: `… calls 'toHistoryItems', a same-file function that resolves copy; resolve it before the lambda`, and the F2 order message |
| 37 | S5: InputHandler's `if (current.pickerState !is PickerState.Open) return` → `val picker = current.pickerState as? PickerState.Open ?: return` / `@Suppress("unused")` / `val acknowledged = picker` | topology: `suppressions must be exactly the seven inherited production and zero Native test-name annotations; expected=…` |
| 38 | S5: the dead `feature_training_detail_active_pill` (`Active`) back into the EN catalog, before `feature_training_detail_conflict_progress_format` | topology: EN catalog mismatch, `unexpected=['feature_training_detail_active_pill']`, `order=drifted`; its ownership drift |
| 39 | P1: EN `<item quantity="one">%1$d session</item>` → `%d session` | topology: the placeholder rule, `…values/strings.xml key feature_training_detail_session_count[one] token '%d'`; the EN catalog mismatch |
| 40 | `"androidHostTestImplementation"(project(":core:data:database-test"))` after the golden-harness edge | topology: `feature:single-training: build must not depend on :core:data:database-test` |
| 41 | `import io.github.stslex.workeeper.core.data.database.sets.PlanSetDataModel` in `domain/SingleTrainingInteractorImpl.kt` | topology: `…SingleTrainingInteractorImpl.kt must not read core:data:database; only the domain mapper maps its data models` |
| 42 | `private val probe = java.util.UUID.randomUUID()` in `CommonHandlerTest` | topology: `…CommonHandlerTest.kt names java.* in shared code` |
| 43 | a 63rd `@Test`, `` `a sixty-third identity nobody declared` ``, in `NavigationHandlerTest` | topology: test identities drifted, `unexpected=['a sixty-third identity nobody declared']` |
| 44 | in `SingleTrainingExtensionIdentityTest`, `val store = appGraph.singleTraining("tr-1").singleTrainingStore` → `val store = appGraph.singleTrainingGraphFactory.createSingleTrainingGraph(Screen.Training(uuid = "tr-1")).singleTrainingStore` | topology: `all six extension identities must reach the graph through the accessor` |
| 45 | `AppNavigationHost`: delete `factory = singleTrainingGraphFactory,` from the `singleTrainingsGraph(` call | topology: `exact root-factory flow requires 'singleTrainingsGraph(factory = singleTrainingGraphFactory,' once in …AppNavigationHost.kt` |
| 46 | S10: `FEATURE_RESOURCE_OWNERS`' single-training root back to `src/main/res` | topology: `resource ownership mismatch for feature_training_edit_plan_set_removed; expected={…src/main/res/values/strings.xml…}` |
| 47 | S7: RU `feature_training_detail_exercises` `Упражнения` → `Упражнения!` | `verifyPaparazziDebug`: the three `TrainingDetailGoldenTest` methods, both themes (pixel diffs), and only them; topology: the RU catalog mismatch |
| 48 | EN `feature_training_picker_title` `Add exercises` → `Add exercises.` | Native: the scene's steps 1 and 5; topology: the EN catalog mismatch |
| 49 | S7, the RU plural: `<item quantity="few">%1$d сессии</item>` → `%1$d сессиии` | `verifyPaparazziDebug`: `detailWithPlans` and `detailNoPlanExercise`, both themes, and only them (`2 СЕССИИ`); topology: the RU catalog mismatch |
| 50 | the Cyrillic identity renamed to `` `the history count is the total not the visible page` `` | topology: test identities drifted in `…CommonHandlerTest.kt`, `missing=['the История count is the total not the visible page']`, `unexpected=['the history count is the total not the visible page']`; Gradle GREEN, then the oracle: the Cyrillic tuple `found 0` |
| 51 | the argument rule: the confirm's title read drops `name` (`stringResolver.getString(Res.string.feature_training_detail_permanent_delete_title)`) | host and Native: `permanent delete replaces the menu with its confirm in one write` and `a menu dismissed before the confirm's copy lands stays dismissed`, and only them, at the strict fake's argument check |

Each Python-scored control (1–14, 17, 26–46, the topology halves of 30–33 and 47–50, and M1–M3,
N1–N3, X1–X2 and G1–G4 of Section 12.0) was dry-run once against the simulated exit tree under
Python 3.9, 3.11, 3.12, 3.13 and 3.14 while this document was written. Each produced exactly its
named result, byte-identical across interpreters, without a traceback, and the restored tree gave a
fresh GREEN byte-equal to the exit output. That dry run is not evidence. The implementation's fresh
runs are.

Scorers:

- **Python, the topology script as CI runs it:** 1–14, 17, 26–46, and the topology halves of 30–33
  and 47–50;
- **PNG:** 24;
- **Gradle:**
  - `:feature:single-training:testAndroidHostTest :feature:single-training:iosSimulatorArm64Test
    --continue` for 19–22, the Gradle halves of 30–33, 48's Native half and 51;
  - `:feature:single-training:verifyPaparazziDebug` for 47's and 49's golden halves;
  - the fourteen-module Native command and the oracle for 23 and 50's oracle half;
  - root detekt, alone, for 25;
- **device:** 15 and 16 (`UiAdmissionRaceTest` on the recorded serial, fresh XML).

## 14. STOP conditions

7.13 Section 14 applies, read with Section 0.3. In addition, stop if:

- `53605e126` is not an ancestor of the base, or the target or any Section 9.2 path drifted from the
  discovery baseline in a way this document does not describe;
- a census number of Section 12.2 differs from this document;
- commit 1's proof (Section 12.0) shows any differing byte, or an N, X or G row deviates;
- D2, D3 or F2 needs anything beyond Sections 5.2 and 5.3: another production file, a click path
  that suspends for copy, a confirm write other than Section 5.2's compare-and-set, a
  `SingleTrainingGraph` change, or a changed identity, stub or assertion in commit 2 beyond S14's
  four additions;
- the copy port needs any of these: a resource handle, `ResourceWrapper` or `StringResolver` in a
  payload file; resolution or a resolver reference inside a State lambda, directly or through a
  same-file function; a change to an Event, a `DialogState` variant or a UI model beyond
  `TrainingEditCopy`;
- any identity needs an assertion change, a strict fake cannot reproduce today's stubs, or a store
  fake would have to run a launch that Section 11.1 does not list;
- **S7:** any of the 12 PNG blobs changes under `verifyPaparazziDebug`. Record the method, the theme
  and the differing region: it means CMP renders the RU catalog or the RU plural differently from
  Android resources. Never re-record;
- **the Cyrillic identity** fails to compile for Native, runs under another name, or is missing from
  the XML or the oracle (Section 4.3). Record the XML bytes of its testcase. Never rename it;
- `navigationBarsPadding()`, CMP's `BackHandler` under `ExperimentalComposeUiApi`, or
  `cmp.material.icons.extended`'s `Search` / `DragHandle` does not resolve for iOS in commonMain:
  STOP-and-amend with the compiler output;
- the Native scene's EN catalog differs from Section 3.4 in any value, or step 2's outputs differ
  from its table;
- `SetText` or a click inside the picker's sheet cannot run on the simulator (Section 11.4);
- `implementation(project(":core:data:database"))` does not resolve for the iOS targets in
  commonMain, or any test source set needs `core:data:database` or `core:data:database-test`;
- Metro rejects the graph after the move, or the generated extension needs a handler's constructor
  public;
- the module's device test, `RouteReachabilityTest` or `StoreRetentionTest` fails on the recorded
  serial;
- the KMP iOS kit smoke job times out on any PR head;
- detekt reports anything in the moved tests or in new code. The fix is never a suppression.

A STOP produces a measured note and, where needed, an amendment proposal. It never widens the
implementation silently.

## 15. Signed, bisect-green commit plan

Five English Conventional Commits (S11). Each is signed, GitHub-verified and green on its own tree:

1. **ci(kmp): pin every golden class and copy-resolving helpers in the shared gate** — D4 and D5
   only (Section 12.0): the field, the patterns, the two functions and the two checks, and nothing
   else.
2. **refactor(single-training): resolve editor copy before the click and split the load's State
   lambdas** — in `src/main` and `src/test`, before any move: D2 (Section 5.2, with
   `resourceWrapper.getString(R.string.…)` where the exit has the resolver), D3 with F2's split
   (Section 5.3), S5 (Section 5.6), the clock (Section 5.8) and S14's four identities
   (Section 11.1). Every existing identity, stub and assertion stays, except the one setup D2 adds
   to the in-one-write identity. Nothing else changes.
3. **refactor(kmp): share single-training feature entry** — the module (Sections 3–11) and the six
   app paths, and the minimal topology delta that keeps this tree green (Section 12.1): the
   `MODULES` entry, the reader removed from `EXPECTED_APP_DEPS_READERS`, and S10's owner root.
4. **ci(kmp): gate shared single-training feature** — `SINGLE_TRAINING_ENTRY` with its two checks,
   the Native oracle entry, and the workflow lists and comments.
5. **docs(kmp): record Phase 7.14 evidence** — this document's Status line, which becomes
   `**Status:** COMPLETE — implemented by the PR whose merge brought this line to dev.`; Section
   19; and the documentation edits of Section 9.2. Sections 0–18 stay byte-identical.

Each commit is made first. Its gates then run on the committed tree, and so do the controls (after
commit 4) and the repository gates. A fix is a new signed commit inside Section 9, and every gate it
can reach is re-run. Nothing is amended, squashed or reordered.

## 16. Exit criteria

7.13 Section 16 applies, with:

- commit 1's proof, and commit 2's green classic module with 62 identities;
- the 62-path module and its two private catalogs;
- 38 identifiers, changed only by S5's two removals and P1's 6 values;
- copy outside composition only through `StringResolver`, `ResourceWrapper` only for the one medium
  date (the copy contract and the allowance), no click path that suspends for copy, and the confirm
  landing only on the menu it replaces (D2);
- no State lambda that resolves copy, references a resolver or calls a same-file copy helper, and
  every one returning a `.copy(` (D3, D5, F2);
- 62 identities on host and Native, the Cyrillic one among them, and 63 Native tuples;
- 12 golden cases with unchanged blobs, both classes pinned (D4), and the relative PNG manifest
  proof;
- 3 device identities from androidDeviceTest and the 15 `RouteReachabilityTest` identities;
- the eight production suppressions and no test suppression;
- the explicit shape-B factory flow, with all six identities through the accessor;
- `core:data:database` one production `implementation` edge, read by the domain mapper alone, and no
  `core:data:database-test` edge;
- the topology entry, oracle and workflow at fourteen modules;
- readers 5 → 4;
- every control of Section 13 evidenced;
- the Section 9.2 documents updated.

## 17. Remaining ordered roadmap

After 7.14 the frontier is remeasured. By today's scan:

- **exercise** next: four mocked use cases, for which 7.12's D2 is the template; three golden
  classes on D4; its `applyLoaded`, which D5 now reports and F2's split fixes; the photo picker, the
  camera permission and the Settings intent (ten `android.*` imports) need seams; its tests read
  `core:data:database-test`;
- **live-workout**: the largest; eleven State lambdas through `LiveSetMutator` without `.copy(`
  (D3's question across files), four dialog paths that resolve copy (D2's split is their template:
  three constant-only, one with live counts), two handler-to-handler injections (7.13's D2 seam),
  and four golden classes on D4;
- **settings**, after `feature:app-dialogs:api` becomes KMP (a small prerequisite PR), with seams
  for Drive sign-in, `Formatter`, `DateUtils` and `java.text.DateFormat`.

Then come app/common and the permanent iOS host. A throwaway iosApp remains forbidden.

Separate follow-ups. S13 records three in tech-debt.md (Section 9.2): the inherited tag race — a
load that lands before the first tags emission chips the training's tags as
`AppTagItem(uuid = name)`, the picker then shows them unselected, and selecting one adds a second
chip of the same tag (static reading, not reproduced; F2's split keeps the match inside the lambda,
so behaviour is unchanged); D2's template for live-workout; and the history rows' medium date in
the portable date formatter row. `observeTags`' pure in-lambda mapping keeps its re-pointed row (the
7.9–7.13 non-goal). Two are recorded here only, for the next change to this module:
`SingleTrainingInteractor.getLabels`, which has no caller (S5 keeps it), and ClickHandler's three
`withContext(mainDispatcher) { consume(Back) }` inside `onSuccess`, which already runs on
Main.immediate (the canonical form is `consumeOnMain`, and dropping them would drop the handler's
`@MainImmediateDispatcher`).

## 18. Authorization

The maintainer's GO of 2026-10-19 locked D1–D5 and accepted S1–S13. Starting the implementation
with this document accepts its wording of them, and S14, and authorizes two things:

- landing this document on dev;
- implementing it as one PR into dev, inside Section 9.

Merge stays the maintainer's.

## 19. Implementation evidence

This section records the bounded implementation authorized by the maintainer's GO of 2026-10-19
(Section 18). The GO authorized exactly Section 7 and nothing in Section 8. Sections 0–18 remain the
specification and are not rewritten here; where the measured tree differs from their prose, Section
19.8 says so.

Every load-bearing Gradle invocation below ran after `./gradlew --stop` and a sample of every other
Gradle client and daemon on the host (`gradle-wrapper.jar`, `GradleWrapperMain` and `GradleDaemon`,
each with its working directory read through `lsof`), with
`--rerun-tasks --no-build-cache --no-configuration-cache --console=plain` (plus `--continue` where
stated). This run's Gradle used its own daemon registry (`-Dorg.gradle.daemon.registry.base` in
`GRADLE_OPTS`, the pre-commit hook's detekt and `mutation_harness.py` included), so its `--stop`
reached no other checkout's daemon. Across the 67 gates, the samples taken before each stop list 65
daemons with 65 distinct PIDs, every one with its working directory in this run's registry, and no
client. So no other checkout's Gradle was alive at any gate, and no daemon survived from one gate
to the next. The sampler's own label reads `foreign` for these daemons: it matched the registry
path on the command line, which a daemon does not carry. The working directory is what identifies
them. Every "Executed" cell is the logged `N actionable tasks: N executed`. Only JUnit XML written
after the run's recorded start was parsed, and each gate's XML was copied aside at once. detekt
never ran in the same invocation as tests. Every census, transcription, audit and scorer below is a
script this one session wrote and ran, each with its own parser; no sub-agent took part.

### 19.1 Entry, drift, and the two censuses

This specification landed on `dev` as `c00d5703d0e8ac730279971938f26e00a8c10ef3`, one signed commit
that fast-forwarded `53605e126`; its blob had the input sha256 `76d97b9a…0f5`. It was pushed with
the launch prompt's guarded command, which the maintainer authorized in their own message, run as
written (the push printed GitHub's bypass notice for the two required status checks). GitHub reports
the commit `verified: true`, `reason: valid`. A Markdown-only push starts no unified run, and none
started. That SHA is the implementation base.

| Entry check | Result |
| --- | --- |
| latest push run of `Android CI/CD - Unified Build and Tests` on `dev` | run `39231437445` on `53605e126`, `success` (all seven jobs) |
| `53605e126` and the SPEC commit are ancestors of the base | yes (the base is the SPEC commit) |
| PR-R on the base | `StringResolver.kt` and `ComposeStringResolver.kt` match `kmp-string-resolver.md` Sections 4.1–4.2; `AppGraph` exposes `val stringResolver: StringResolver` |
| drift | `git diff 53605e126..c00d5703d -- feature/single-training` is empty; the one commit since `53605e126` is the SPEC commit, which touches only this document's path (no conflict) |
| open PRs touching the module | none (no open PR at all) |
| merged worktrees | `zen-edison-689e16` (#335 merged, clean, no process inside) removed; `pr-332-crash-794d3d` kept (its branch has no PR) |

Two read-only censuses reproduced Sections 2 and 3.1–3.7 on the base independently. Census A read
only git objects (`ls-tree`, `cat-file`, and a scratch index built with `read-tree` for the PNG
manifests) with line and regex parsing. Census B read the clean worktree from disk with its own
Kotlin lexer (comments blanked, string, character and backtick literals masked), its own
literal-aware bracket matcher, `minidom` XML parsing, and git blob IDs computed in Python from the
file bytes; it ran no git command. Both implement Section 12.0's `copy_resolving_functions`
independently for D5's census. They agree on all 86 keys. A third script parsed the expected values
out of this document (the 0.2, 2, 3.1, 3.2, 3.3, 3.4, 3.6 and 3.7 tables and lists, and every prose
count, each checked to occur verbatim) and compared: 71 of 72 checks equal.

The one difference is a word in Section 3.1: "`R.*` at 39 references in seven files". Both censuses
count six files — the five UI files (8, 10, 5, 2 and 3 references) and ClickHandler (11) — which is
also the itemization Section 3.1 gives itself (28 + 11 = 39); no test file and no other production
file names `R`. The count is not one of Section 12.2's census numbers, but the launch prompt made
any census difference a STOP, so the run stopped and asked; the maintainer ruled to proceed and
record it here (Section 19.8).

| Section | Specification | Measured (A and B agree) |
| --- | --- | --- |
| 2 row | 39 / 3,635 / 6 / 1,582 / 39 / 1 / 12 / 0 / 0 / 2 | equal |
| 2 State lambdas | 43; 2 without `.copy(`; 0 findings; D5 one hit (`applyLoaded`); none in the six shared entries | equal: the two are the discard's `applySnapshotOrPop` and the load's `applyLoaded`; D5 finds `CommonHandler.kt` `updateState` → `applyLoaded` only |
| 3.1 files and lines | 39 files, 3,635 lines, per-file table | every row equal; the module tracks 61 paths (build 1, `src/main` 41, `src/test` 18, `src/androidTest` 1) |
| 3.1 suppressions | nine production in eight files, at their lines | equal, line by line; none in `src/test` or `src/androidTest` |
| 3.1 coupling | as listed | 39 `R.*` references over 38 identifiers in six files; UI 27 identifiers (26 strings, 1 plural) at 28 references, 26 call sites; ClickHandler 11 identifiers at 11 `getString` sites, 7 on F1's four paths (`processPermanentDeleteMenu` 4, `processSaveClick`, `processExerciseRemove`, `processExercisePlanAction` 1 each), 4 inside launches; `ResourceWrapper` in ClickHandler (11 `getString`) and CommonHandler (1 `formatMediumDate`); 28 `stringResource(` (7/11/5/2/3) plus the graph's six, one `pluralStringResource(`, three of the 28 on kit's `Res`; kit's `Res` at 9 sites over 8 keys; Android's `stringResource` imported in six files; `BackHandler`, `LocalContext` + `appDeps`, `@VisibleForTesting` and `System.currentTimeMillis()` once each; no `java.`; three FQ `android.content.res.Configuration`; State 21 fields |
| 3.2 identities | 58, 47/4/5/2 | equal by name and order, by `@Test` and by the gate's regex; no `@ParameterizedTest`, `@Nested` or `@DisplayName` outside the goldens |
| 3.2 member counts | 16 / 12 / 6 / 5 / 2 | equal, counted from each type |
| 3.3 goldens and device | 6 methods; 12 PNGs; 3 identities | equal; every method `LOCALE_RU`; all 100644, 12 distinct blobs, names match the methods; class-level `@Smoke`; the device test reads no copy |
| 3.4 catalog | 39 + 1 per locale; exit table | the base rows equal Section 3.4's exit table once S5's two keys are removed and P1 reversed; the dead keys sit between rows 14 and 15 and after row 16 in both locales and have no reader anywhere; bare `%d` items EN 2, RU 4; EN root `tools:ignore="UnusedResources"` and one element-level `PluralsCandidate` |
| 5.5 previews | three `uiMode` pairs, two bare | equal |
| 3.5 build | plugins and edges | equal |
| 3.6 | six names; two journeys | equal; the two journeys reach `SINGLE_TRAINING_GRAPH`; `RouteReachabilityTest` is class-level `@Regression` with 15 identities |
| 3.7 readers and PNG manifests | 5 readers; four hashes, three projections | 5 readers equal to `EXPECTED_APP_DEPS_READERS`; all seven hashes equal |

Environment: Gradle 9.6.1 (wrapper), launcher and daemon JBR 21.0.11, Kotlin 2.4.10, Metro 1.3.2,
Paparazzi 2.0.0-alpha05. Xcode 27.0 (27A266a) at `/Applications/Xcode.app`, first launch complete;
iOS simulator runtimes 18.0, 18.2, 18.6 and 26.5. CI selects Xcode 26.6 on `macos-26`. Python: five
interpreters on six paths, deduplicated by `sys.version` and the executable's real path —
`/usr/bin/python3` 3.9.6, `/opt/homebrew/bin/python3` (the same binary as `python3.15`) 3.15.0,
`~/.local/bin/python3.11` 3.11.16, `python3.13` 3.13.16 and `python3.14` 3.14.8; there is no 3.12
on this host. The worktree linked `local.properties` and both `google-services.json` from the main
checkout and nothing else; it had its own throwaway `keystore.ephemeral.jks` /
`keystore.properties` pair, made with the iOS smoke job's `keytool` line and proven ignored. The
main checkout's keystore was neither linked nor copied. The 16 GB host swapped heavily throughout
(up to 13 of 14 GB of swap in use, alongside the maintainer's own Android Studio), so wall-clock
times below are not comparable across runs, and its disk once filled (Section 19.9).

### 19.2 Fresh baselines before any edit

All on the base `c00d5703d`, device runs on `emulator-5554` (`Pixel_6_API_34`, API 34, booted by
this run, animations off, rotation locked, keyguard dismissed).

| Gate | Executed | Fresh XML |
| --- | ---: | --- |
| topology gate, five interpreters | — | exit 0, 647 lines, empty stderr, byte-identical (BASE_GATE_OUTPUT) |
| `:feature:single-training:assembleDebug` | 119 | — |
| `:feature:single-training:testDebugUnitTest` | 194 | 4 suites, 58 cases, names exactly Section 3.2's base |
| `:feature:single-training:verifyPaparazziDebug` | 195 | 6 suites, 70 cases; `Visual gate live: 12 golden test case(s) executed for 12 golden image(s).` |
| `:app:app:testDebugUnitTest` (both identity classes) | 589 | 2 suites, 8 cases |
| thirteen-module Native command, `--continue` | 362 | 79 suites, 420 cases; oracle exit 0, 13 modules verified |
| `:feature:single-training:connectedDebugAndroidTest` | 248 | 1 suite, 3 cases |
| `RouteReachabilityTest` class | 750 | 15 cases, both single-training journeys passing |

The development app installed on the emulator before this run kept its `lastUpdateTime`
(2026-09-07) through every device run of this section and of Sections 19.4–19.8; no test APK was
left installed.

### 19.3 Commit 1: extra golden classes and copy helpers in the generic gate (Section 12.0)

`4d261b0e7` changes only `.github/scripts/assert_kmp_ui_source_topology.py`: 87 insertions and 0
deletions (81 code and comment lines and 6 blank), every added line within 100 columns — the
reference implementation's count. The base's script was saved aside first; its sha256 is
`a823d575…4dec`, Section 12.0's. The proof ran on the committed tree in a detached worktree of
`4d261b0e7` under `.claude/worktrees/`, under all five interpreters, and was then removed:

| Row | Result |
| --- | --- |
| GREEN | old and new byte-identical, exit 0, empty stderr, both equal to BASE_GATE_OUTPUT (647 lines), before the first and after the last mutation |
| M1–M3 | old and new byte-identical and RED with exactly the named lines (M1 the archive golden list, M2 `'getString('` and `'Res.'`, M3 `'stringResolver'`) |
| N1–N3 | old GREEN and byte-equal to BASE_GATE_OUTPUT; new RED with exactly N1's "renders goldens but is neither golden_test nor in extra_golden_tests", N2's `calls 'metaCopy'` and N3's transitive `calls 'processInit'` |
| X1–X2 | both RED; the new output is the old one plus exactly N2's line (X1) and N1's line (X2) |
| G1–G4 | both GREEN |
| restoration | every mutated file restored byte-exactly (sha256 and mode) before the next; porcelain empty at the end |

97 of 97 rows passed, every row byte-identical across the five interpreters. detekt alone, on the
staged and on the committed tree: 63 executed, no finding.

### 19.4 Commit 2: D2, D3 with F2's split, S5, the clock and S14, still classic

`29cd45f28` changes nine files in `src/main` and `src/test` (240 insertions, 88 deletions): D2's
`TrainingEditCopy` and `State.editCopy`, `resolveEditCopy()` from `processInit`, the three constant
paths reading State and the permanent-delete confirm resolving in a launch with the compare-and-set
write; the discard's lambda inlined and the load split as Section 5.3 prints them; S5's `is` check
and the two catalog keys removed; `Clock.System.now()`; and S14's four identities with the one D2
setup (the in-one-write identity's launch now runs the action, then `onSuccess`). No other identity,
stub or assertion changed. The pre-commit hook's detekt passed, and so did detekt alone on the
staged tree.

| Gate on `29cd45f28` | Executed | Fresh XML |
| --- | ---: | --- |
| `:feature:single-training:testDebugUnitTest` | 194 | 4 suites, 62 cases, 0/0/0; names exactly Section 3.2 (50/5/5/2), S14's four among them |
| `:feature:single-training:verifyPaparazziDebug` | 195 | 6 suites, 74 cases; 12 golden cases executed for 12 golden images; porcelain empty |
| both identity classes | 589 | 8 cases |
| `:feature:single-training:connectedDebugAndroidTest` | 248 | 3 cases |
| detekt | 63 | no finding |
| topology gate, five interpreters | — | 647 lines, byte-identical to commit 1's output |

### 19.5 Commit 3: the move, the early Native check, the scene, and the focused gates

`36ce1ba7c`: 70 files. 59 of them are renames under `-M30%`: 39 production files, 2 catalogs, 3
suites (`ClickHandlerTest` at 75 %, `NavigationHandlerTest` at 42 %, the route-argument suite), the
2 golden classes, the 12 PNGs and the device test. `CommonHandlerTest`, rewritten for kotlin.test,
falls below 30 % and shows as a deleted and an added path; the scene is the other added path. The
catalogs changed only by P1's six items (EN 2, RU 4). The build file is Section 10's, with the
module's three comments moved and prose-only rationale comments added.

Gradle metadata, verified by an import audit: every declared edge has an importer in a source set
that sees it (`core:data:database` in one file, the domain mapper); no import lacks an edge or a
convention; there is no `core:data:database-test` edge.

**Early Native check.** With production and `commonTest` in place and before the scene and the app
paths, `:feature:single-training:iosSimulatorArm64Test` compiled production for Native on the first
run (CMP `BackHandler` under `ExperimentalComposeUiApi`, `navigationBarsPadding()` and
icons-extended's `Search` / `DragHandle` all resolved). Its first run failed to compile three
`commonTest` assertions: kotlin.test cannot infer `T` for `assertEquals(listOf(set), …planSets)`
against the nullable `ImmutableList`. They took the type witness
`assertEquals<List<PlanSetUiModel>?>` (7.11 Section 19.8; Section 11.1 allows it), with the
assertions unchanged. The re-run: 124 executed, 4 suites, 62 cases, 0/0/0, names exactly Section
3.2. The Cyrillic identity appears in the UTF-8 XML as `<testcase name="the История count is the
total not the visible page[iosSimulatorArm64]"
classname="iosSimulatorArm64Test.io.github.stslex.workeeper.feature.single_training.mvi.handler.CommonHandlerTest"
…/>` — the letters' bytes `d0 98 d1 81 d1 82 d0 be d1 80 d0 b8 d1 8f` — and the oracle compares it
from there.

**The scene.** Its first draft did not compile: the picker selection was a `List` where
`ExercisePickerSheet` takes an `ImmutableList`. The next run failed only at its last assertion.
Every catalog value, every step-2 output and every rendered node matched, and `SetText` and the
clicks inside the picker's sheet ran on the simulator. The dispatched-action list held two extra
entries, `Input.OnNameChange("Push Day")` and `Input.OnDescriptionChange("")`. They come from
`BasicTextField`'s `String` overload, which re-reports its held text when focus leaves a field whose
typed value the State never took. Past-session's scene, whose `performTextReplacement` Section 11.4
names for `SetText`, answers exactly this (7.11 S5): after asserting each dispatched input, the test
writes the typed value into its held State, as the Store would. Applied after the three `SetText`s,
the scene passed. The two exact lists remain Section 11.4's 18 actions and 4 picker callbacks
(Section 19.8). The scene's expected EN catalog (39 values) and its step-2 outputs
were then compared mechanically with Section 3.4's and 11.4's tables: equal.

| Gate on `36ce1ba7c` | Executed | Fresh XML |
| --- | ---: | --- |
| topology gate, five interpreters | — | exit 0, 711 lines, byte-identical |
| `:feature:single-training:assembleDebug` | 344 | Android AAR and both iOS klibs (`iosArm64MainKlibrary`, `iosSimulatorArm64MainKlibrary`) |
| `:feature:single-training:testAndroidHostTest` | 196 | 4 suites, 62 cases, 0/0/0; names exactly Section 3.2 |
| `:feature:single-training:verifyPaparazziDebug` | 197 | 6 suites, 74 cases; 12 golden cases executed for 12 golden images; porcelain empty — every PNG byte-identical under CMP's RU catalog and RU plural (S7) |
| `:feature:single-training:iosSimulatorArm64Test` | 124 | 5 suites, 63 tuples, names exactly Sections 3.2 and 11.4 |
| `:app:common:assembleDebug` | 339 | — |
| both identity classes | 592 | 8 cases, through `appGraph.singleTrainingGraphFactory` and the real app-scoped `StringResolver` |
| detekt | 63 | no finding |
| `:feature:single-training:connectedDebugAndroidTest` | 248 | 3 cases (`emulator-5554`, second boot) |
| `RouteReachabilityTest` class | 753 | 15 cases, both single-training journeys passing |

The relative PNG proof (Section 3.7): exactly the 12 single-training paths rewritten from
`src/test/` to `src/androidHostTest/`; the head manifests equal the rewritten base byte for byte —
`f903e721…0a1f` (456 entries, 13 owners), `f19c856d…ca97` and `7cc28d13…2df9` (528), Section 3.7's
projections — and the blob set is `e116929e…161b`, unchanged.

### 19.6 Commit 4: the transcription, the oracle, and the fourteen-module gates

`1476c3cd8`. `SINGLE_TRAINING_ENTRY` and its two checks were generated by a script that reads only
this document (Sections 0.2, 0.3, 3.2, 3.4, 3.6, 6, 10, 11.4 and 12.1), never the files they check;
the contract code is a template whose fragments, counts, parameter lists and messages it parses out
of Section 12.1. A second script with its own parser (line and cell splitting, no shared regex)
rebuilt every field and compared it with the entry the committed gate defines: `TRANSCRIPTION EQUAL
(40 fields)`. Through 43 synthetic probes on a scratch copy of the module it showed that every rule
of both contracts fails when broken — each parameter list, both D2 defaults, every exactly-once and
count fragment, F2's order, all four 7.11 rules and all four dependency bullets — and that the
unbroken copy passes. Commit 4's topology diff is 535 insertions and 0 deletions (the prototype's
518; the difference is line wrapping and comments); commits 3 and 4 together remove exactly the
reader line and the old owner-root line, and `check_feature_entry` and the six earlier entries are
byte-identical. The oracle gained the 63-tuple entry after exercise-chart's, names in UTF-8 (93
insertions); the workflow gained the two commands, the loop line, `/14 linked`, the upload line and
the comments (10 insertions, 7 deletions).

| Gate on `1476c3cd8` | Executed | Result |
| --- | ---: | --- |
| topology gate, five interpreters | — | exit 0, 712 lines, byte-identical |
| fourteen-module Native command, `--continue` | 386 | 84 suites, 483 cases, 0/0/0; oracle exit 0 under all five interpreters, byte-identical, 14 modules verified, single-training 63 |
| fourteen-module device link | 372 | `14/14 linked` |
| detekt | 63 | no finding |
| workflow static checks | — | the YAML parses; actionlint 1.7.12 prints the same pre-existing SC2129 note at the base and at head, nothing new |

### 19.7 Mandatory known-negative controls

All 50 applicable controls of Section 13 (1–17 and 19–51; 18 has no counterpart) ran on the
committed tree `1476c3cd8`. Each went fresh GREEN → one named observable RED → automatic exact
restoration → fresh GREEN. No mutation was committed.

The Python-scored controls (1–14, 17, 26–46, and the topology halves of 30–33 and 47–50: 40
mutations) and the PNG-scored control 24 ran in a detached worktree at `1476c3cd8` under
`.claude/worktrees/` (porcelain empty), removed afterwards with `git worktree remove`. The scratch
harness refused to start unless HEAD was that commit and the porcelain empty. For each control it:

- took a fresh GREEN, byte-equal to commit 4's gate output;
- snapshotted every touched path (bytes and mode, or absence);
- applied one mutation whose text anchors each occur exactly once;
- ran the topology gate as CI runs it under all five interpreters;
- accepted a RED only when all of these held: a non-zero exit, the gate's failure header, no
  Traceback, every named fragment present (compared as the gate's `repr` writes it), every other
  line accounted for, and the five outputs byte-identical;
- restored in `finally` and proved sha256 and mode equal and created paths absent;
- took a fresh GREEN byte-equal to the first.

The pass ended `82/82 PASS`: the first GREEN, 40 REDs, 40 GREENs after them, and an empty porcelain.
Control 24's scorer hashes the on-disk bytes of every tracked PNG in Python. It requires the
single-training blob set, the target's blob, and the all-PNG and Paparazzi manifests to equal the
committed tree's index.

The Gradle- and device-scored controls ran in the branch worktree through
`documentation/mockups/mutation_harness.py` in one-shot mode, driven by a scratch script. For each
control the script:

- required the target file to equal HEAD's blob and the porcelain to be empty;
- passed `--no-configuration-cache --console=plain` inside `--task` (the harness stops the daemon
  and adds `--rerun-tasks --no-build-cache` itself);
- copied the run's fresh XML aside at once;
- proved the target file equal to HEAD's blob and the porcelain empty again;
- took a fresh forced GREEN through the gate runner.

Within a group, one control's post-GREEN served as the next one's pre-GREEN. The groups were:

- 19 → 20 → 21 → 22 → 30 → 31 → 32 → 33 → 48 (Native) → 51 on
  `:feature:single-training:testAndroidHostTest :feature:single-training:iosSimulatorArm64Test
  --continue` (every GREEN 283/283 executed, 62 host and 63 Native cases passing);
- 47 (golden) → 49 (golden) on `:feature:single-training:verifyPaparazziDebug` (every GREEN 197/197,
  74 cases passing, 12 golden cases live);
- 23 → 50 (oracle) on the fourteen-module Native command (every GREEN 386/386 with 84 suites and 483
  cases passing, and the oracle on its XML GREEN, 14 modules verified);
- 25 on `detekt --continue` (every GREEN 63/63);
- 15 → 16 on the device: `:app:app:connectedDebugAndroidTest
  -Pandroid.testInstrumentationRunnerArguments.class=io.github.stslex.workeeper.app.UiAdmissionRaceTest
  --max-workers=2 --continue` (every GREEN 753/753 with 3 cases passing; the device state, recorded
  around all five runs, was the same each time: animations off, rotation locked, keyguard dismissed,
  nothing instrumenting, and the development app's `lastUpdateTime` 2026-09-07).

Every RED was scored from the run's copied JUnit XML, never from the harness's verdict line. The
exceptions are 23 and 50's oracle half, scored from the oracle's output, and 25, scored from
detekt's fresh reports. "Only" was checked against every case of the run: 62 host and 63 Native for
the shared groups.

In the table, `…/` stands for `io/github/stslex/workeeper/feature/single_training/`, and every
topology line carries the gate's `feature:single-training: ` prefix unless it is quoted without one.

| # | Mutation | Observable RED (quoted) |
| ---: | --- | --- |
| 1 | delete `ui/components/TrainingDetailMenuSheetContent.kt` | `source manifest mismatch; missing=['src/commonMain/kotlin/…/ui/components/TrainingDetailMenuSheetContent.kt'], extra=[]` and `src/commonMain/kotlin/…/ui/components/TrainingDetailMenuSheetContent.kt must hold bare @Preview annotations but is missing` |
| 2 | copy `di/SingleTrainingScope.kt` into `src/main/kotlin/…/di/` | the manifest's `extra=['src/main/kotlin/…/di/SingleTrainingScope.kt']`, `legacy source set src/main still contains ['src/main/kotlin/…/di/SingleTrainingScope.kt']` and `unexpected Kotlin-bearing source set src/main: […]` |
| 3 | move `ui/components/TrainingDetailMenuSheetContent.kt` to `ui/TrainingDetailMenuSheetContent.kt` | `missing=['src/commonMain/kotlin/…/ui/components/TrainingDetailMenuSheetContent.kt'], extra=['src/commonMain/kotlin/…/ui/TrainingDetailMenuSheetContent.kt']`, `…/ui/components/TrainingDetailMenuSheetContent.kt must hold bare @Preview annotations but is missing` and `…/ui/TrainingDetailMenuSheetContent.kt must hold exactly 0 bare @Preview annotation(s); found 1` |
| 4 | `import android.util.Log` in `ui/components/TrainingDetailMenuSheetContent.kt` | `…/ui/components/TrainingDetailMenuSheetContent.kt imports a platform API in commonMain: 'import android.'` |
| 5 | rename the EN key `feature_training_picker_cancel` to `…_renamed` | `exact private CMP catalog mismatch in feature/single-training/src/commonMain/composeResources/values/strings.xml; mismatched=["feature_training_picker_cancel: expected='Cancel', actual=None"], unexpected=['feature_training_picker_cancel_renamed'], order=drifted`, plus the two ownership-drift lines |
| 6 | the RU value `Отмена` → `Отменить выбор` | `… values-ru/strings.xml; mismatched=["feature_training_picker_cancel: expected='Отмена', actual=('string', 'feature_training_picker_cancel', 'Отменить выбор')"], unexpected=[], order=exact` |
| 7 | move the EN `feature_training_picker_cancel` into `feature/settings/src/main/res/values/strings.xml` | the EN catalog mismatch (`actual=None`, `order=drifted`) and `resource ownership drift for feature_training_picker_cancel; expected=[…single-training values-ru…, …single-training values…], actual=['feature/settings/src/main/res/values/strings.xml', …single-training values-ru…]` |
| 8 | `val titleRes: Int = 0` as State's last field | `Store State must stay semantic with exactly ((…the 22 Section 12.1 fields…)); actual=(…, ('editCopy', 'TrainingEditCopy'), ('isLoading', 'Boolean'), ('titleRes', 'Int = 0'))` |
| 9 | `resolveEditCopy`'s `setRemoved = stringResolver.getString(…)` → `setRemoved = resourceWrapper.getString(0),` | `…/mvi/handler/CommonHandler.kt resolves single-training copy through ResourceWrapper.getString; use the private Compose resources`, `… copy contract requires 'stringResolver.getString(' exactly three times; found 2` and `… reads copy outside the StringResolver: 'getString(' 3 time(s), 2 through stringResolver` |
| 10 | `val label = stringResolver.getString(Res.string.feature_training_edit_plan_set_removed)` as the first line of the load's lambda | `updateState in …/mvi/handler/CommonHandler.kt resolves a resource inside the State lambda: 'getString('` and `… 'Res.'`, and `… copy contract requires 'stringResolver.getString(' exactly three times; found 4` |
| 11 | the bare `@Preview` above `TrainingDetailMenuSheetContentPreview` → `@Preview(uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES)` | `…/ui/components/TrainingDetailMenuSheetContent.kt contains forbidden platform/lookup API 'android.content'` and `… 'uiMode'`, `exactly six portable previews are required; found 7`, `… must hold exactly 1 bare @Preview annotation(s); found 0` |
| 12 | `val context = LocalContext.current` and `context.appDeps<SingleTrainingGraph.Factory>()` in `SingleTrainingFeature` | `remaining Context.appDeps readers are not the exact 4 unported entries; …` (the actual set adds `…/di/SingleTrainingFeature.kt`), `…/di/SingleTrainingFeature.kt contains forbidden platform/lookup API 'LocalContext'` and `… 'appDeps<'`, `factory invocation must occur exactly inside retained Store creation` |
| 13 | delete `val singleTrainingGraphFactory: SingleTrainingGraph.Factory` and its KDoc from `AppRootDeps` | `exact root-factory flow requires 'val singleTrainingGraphFactory: SingleTrainingGraph.Factory' once in app/common/src/main/kotlin/io/github/stslex/workeeper/app/common/di/AppRootDeps.kt` |
| 14 | `val graph = factory.createSingleTrainingGraph(screen)` before `rememberMetroStoreProcessor`, whose lambda then reads `graph.singleTrainingStore` | `factory invocation must occur exactly inside retained Store creation` |
| 15 | a second `(context.applicationContext as AppRootDepsHolder).appRootDeps()` inside the admitted `remember(currentPhase.id) { … }` in `App.kt` | device: `UiAdmissionRaceTest`, 753/753: `admittedGeneration_composesTheRegion_andResolvesItsDependencies` `java.lang.AssertionError: an admitted region resolves the generation's app-scope deps expected:<1> but was:<2>`; the two retired cases passed; fresh XML 3 cases, 1 failure |
| 16 | hoist `val deps = remember(currentPhase.id) { …appRootDeps() }` above `if (admission.granted) {` | device: `UiAdmissionRaceTest`, 753/753: `retiredGeneration_composesNothing_andResolvesNothing` `java.lang.AssertionError: a retired generation must resolve NOTHING — not even the app root deps expected:<0> but was:<1>` and `retirementBetweenPublicationAndFrame_resolvesNothing` `… the stale generation's region must resolve NOTHING expected:<0> but was:<1>`; the admitted case passed |
| 17 | identity helper `= asContribution<SingleTrainingGraph.Factory>().createSingleTrainingGraph(…)` | `extension identities must not bypass AppRootDeps via asContribution`, plus `exact root-factory flow requires 'private fun AppGraph.singleTraining(uuid: String?): SingleTrainingGraph = singleTrainingGraphFactory.createSingleTrainingGraph(Screen.Training(uuid = uuid))' once in app/app/src/test/kotlin/io/github/stslex/workeeper/di/SingleTrainingExtensionIdentityTest.kt` |
| 18 | archive-specific; no counterpart | — |
| 19 | the discard's `exercises = snapshot.exercises,` → `exercises = latest.exercises,` | host and Native: `OnConfirmDiscard restores an exercise the edit removed` (`expected: <[ex-1, ex-2]> but was: <[ex-2]>`), `OnConfirmDiscard restores the positions a reorder changed` (`expected: <[ex-1, ex-2]> but was: <[ex-2, ex-1]>`) and `OnConfirmDiscard restores the plan a card edited` (`expected: <null> but was: <[PlanSetUiModel(weight=null, reps=5, type=WORK)]>`), the Native messages the same in Kotlin's words; nothing else red (6 of 125) |
| 20 | the conflict progress read's `resolution.doneCount,` / `resolution.totalCount,` swapped | host and Native: `OnStartSessionClick reports the persisted active session progress`, at the strict fake: `the progress passes done, then total; got [2, 1]`; nothing else red (2 of 125) |
| 21 | the scene's `setContent { … }` body → `setContent { }` | Native, the scene only, at its first node assertion: `Failed to perform isDisplayed check.` `Reason: Expected exactly '1' node but could not find any node that satisfies: ((Text + InputText + EditableText contains 'Push Day' (ignoreCase: false)) && (hasAnyAncestorThat(TestTag = 'TrainingDetailTopBar')))`; host 62/62 and the other 62 Native cases green (1 of 125) |
| 22 | `ui/TrainingDetailScreen.kt`: the back button's `onClick = { consume(Action.Click.OnBackClick) },` → `onClick = { },` | Native, the scene's action list: `Expected <[OnBackClick, OnDetailMenuClick, OnExerciseRowClick(exerciseUuid=ex-1), …, OnPermanentDeleteClick]>, actual <[OnDetailMenuClick, OnExerciseRowClick(exerciseUuid=ex-1), …, OnPermanentDeleteClick]>` (18 expected, 17 actual, only `OnBackClick` missing); host 62/62 green (1 of 125) |
| 23 | rename the scene method to `renamedSceneSubstitute` | Gradle GREEN (386/386, 84 suites, 483 cases passing, single-training 63), then the oracle on that run's XML, byte-identical under the five Pythons: `native gate FAILED (every module was checked, so no verdict is hidden):` and `feature:single-training: expected exactly one testcase with classname='io.github.stslex.workeeper.feature.single_training.SingleTrainingFeatureSceneIosTest' name='resourcesDetailEditorPickerAndMenuRenderAndDispatch[iosSimulatorArm64]', found 0; parsed 63 testcase(s): ['io.github.stslex.workeeper.feature.single_training.SingleTrainingFeatureSceneIosTest.renamedSceneSubstitute[iosSimulatorArm64]', …]`, the other thirteen modules `ok`; the oracle on the pre- and post-GREEN runs' XML GREEN |
| 24 | flip (XOR 0xFF) the last byte of `…golden_TrainingEditGoldenTest_editEmpty_light.png` | the target's blob (`1c58fd4e5ec748d5…` → `bec100ce227eccb2…`), the single-training blob set (`e116929e…` → `fc0624c3…`), the all-PNG manifest (`7cc28d13…` → `e65d0429…`) and the Paparazzi manifest (`f903e721…` → `11e633fc…`) drift, and nothing else; restored (sha256 and mode `100644`), porcelain empty, GREEN after |
| 25 | a compile-valid overlong assertion line in `src/commonTest/…/mvi/handler/NavigationHandlerTest.kt` | root detekt, alone (63/63): six findings in the 55 fresh reports, all on that file's line 26 (`MaxLineLength`, `MaximumLineLength`, `Wrapping`, `ArgumentListWrapping` ×3); the post-GREEN detekt (63/63) wrote 55 fresh reports with 0 findings |
| 26 | D4: `TrainingEditGoldenTest`'s `fun editEmpty(` → `fun editEmptyProbe(` | `golden methods in src/androidHostTest/kotlin/…/golden/TrainingEditGoldenTest.kt must be exactly ['editCollapsed', 'editExpanded', 'editEmpty']; actual=['editCollapsed', 'editExpanded', 'editEmptyProbe']` |
| 27 | D4: copy `golden/TrainingEditGoldenTest.kt` to `golden/TrainingEditCopyGoldenTest.kt` | `source manifest mismatch; missing=[], extra=['src/androidHostTest/kotlin/…/golden/TrainingEditCopyGoldenTest.kt']` and `src/androidHostTest/kotlin/…/golden/TrainingEditCopyGoldenTest.kt renders goldens but is neither golden_test nor in extra_golden_tests` |
| 28 | D5 over F2: the load lambda's `pastSessions = pastSessions,` → `pastSessions = result.recentSessions.toHistoryItems(),` | `updateState in src/commonMain/kotlin/…/mvi/handler/CommonHandler.kt calls 'toHistoryItems', a same-file function that resolves copy; resolve it before the lambda` |
| 29 | D5, transitive: `processInit()` as the first line of the load's lambda | `updateState in …/mvi/handler/CommonHandler.kt calls 'processInit', a same-file function that resolves copy; resolve it before the lambda` |
| 30 | D2: delete the `resolveEditCopy()` call from `processInit` | topology: `…/mvi/handler/CommonHandler.kt copy contract requires 'resolveEditCopy()' exactly two times; found 1`; host and Native: `Init resolves the editor's constant copy into State` (`expected: <TrainingEditCopy(noExercisesError=no-exercises, exerciseRemoved=exercise-removed, setRemoved=set-removed)> but was: <TrainingEditCopy(noExercisesError=, exerciseRemoved=, setRemoved=)>`), and only it (2 of 125) |
| 31 | D2: the save error's `message = current.editCopy.noExercisesError` → `message = ""` | topology: `…/mvi/handler/ClickHandler.kt copy contract requires 'current.editCopy.noExercisesError' exactly once; found 0`; host and Native: `the save error carries the copy Init resolved` (`expected: <no-exercises> but was: <>`), and only it (2 of 125) |
| 32 | D2: the exercise toast's `message = current.editCopy.exerciseRemoved,` → `message = current.editCopy.setRemoved,` | topology: `… requires 'current.editCopy.exerciseRemoved' exactly once; found 0`; host and Native: `the undo toasts carry the copy Init resolved` (`expected: <exercise-removed> but was: <set-removed>`), and only it (2 of 125) |
| 33 | D2: the confirm's guard → `if (true) {` | topology: `… requires 'if (latest.dialogState == DialogState.DetailMenu) {' exactly once; found 0`; host and Native: `a menu dismissed before the confirm's copy lands stays dismissed` (`expected: <Hidden> but was: <PermanentDeleteConfirm(title=, body=, impactSummary=, confirmLabel=)>`), and only it (2 of 125) |
| 34 | F2's pure half: `val exercises = result.exercises.toExerciseItems()` moves to directly after the lambda's `val training = …` line | exactly `…/mvi/handler/CommonHandler.kt must build the exercise items and the history rows before the load's State lambda (F2)`; D5 stays silent |
| 35 | D3: the discard's lambda → `updateState { latest -> latest.withSnapshotRestored() }` | `updateState in …/mvi/handler/ClickHandler.kt must return a State copy` |
| 36 | F2 and D5: `val pastSessions = result.recentSessions.toHistoryItems()` moves into the lambda, after `val training = …` | `… calls 'toHistoryItems', a same-file function that resolves copy; resolve it before the lambda` and the F2 order message |
| 37 | S5: InputHandler's `is` check → the suppressed `as?` binding | `suppressions must be exactly the seven inherited production and zero Native test-name annotations; expected={…}, actual={…}`, the actual set adding `…/mvi/handler/InputHandler.kt: ['"unused"']` |
| 38 | S5: `feature_training_detail_active_pill` (`Active`) back into the EN catalog | `… values/strings.xml; mismatched=[], unexpected=['feature_training_detail_active_pill'], order=drifted` and `resource ownership drift for feature_training_detail_active_pill; …` |
| 39 | P1: EN `<item quantity="one">%1$d session</item>` → `%d session` | `compose-resource placeholder is not %N$d or %N$s with N >= 1: feature/single-training/src/commonMain/composeResources/values/strings.xml key feature_training_detail_session_count[one] token '%d'`, plus the EN catalog mismatch `mismatched=["feature_training_detail_session_count: expected=(('one', '%1$d session'), ('other', '%1$d sessions')), actual=('plurals', …, (('one', '%d session'), ('other', '%1$d sessions')))"]` |
| 40 | `"androidHostTestImplementation"(project(":core:data:database-test"))` after the golden-harness edge | `build must not depend on :core:data:database-test` |
| 41 | `import …core.data.database.sets.PlanSetDataModel` in `domain/SingleTrainingInteractorImpl.kt` | `…/domain/SingleTrainingInteractorImpl.kt must not read core:data:database; only the domain mapper maps its data models` |
| 42 | `private val probe = java.util.UUID.randomUUID()` in `CommonHandlerTest` | `src/commonTest/kotlin/…/mvi/handler/CommonHandlerTest.kt names java.* in shared code` |
| 43 | a 63rd `@Test` in `NavigationHandlerTest` | `exact test identities drifted in src/commonTest/kotlin/…/mvi/handler/NavigationHandlerTest.kt; missing=[], unexpected=['a sixty-third identity nobody declared']` |
| 44 | the identity test's `appGraph.singleTraining("tr-1")` → `appGraph.singleTrainingGraphFactory.createSingleTrainingGraph(…)` | `all six extension identities must reach the graph through the accessor` |
| 45 | delete `factory = singleTrainingGraphFactory,` from the `singleTrainingsGraph(` call | `exact root-factory flow requires 'singleTrainingsGraph(factory = singleTrainingGraphFactory,' once in app/common/src/main/kotlin/io/github/stslex/workeeper/host/AppNavigationHost.kt` |
| 46 | S10: `FEATURE_RESOURCE_OWNERS`' single-training root back to `src/main/res` | `resource ownership mismatch for feature_training_edit_plan_set_removed; expected={PosixPath('feature/single-training/src/main/res/values/strings.xml'): 'Set removed', …}, actual=[(PosixPath('feature/single-training/src/commonMain/composeResources/values/strings.xml'), 'Set removed'), …]` (no prefix) |
| 47 | S7: RU `feature_training_detail_exercises` `Упражнения` → `Упражнения!` | topology: `… values-ru/strings.xml; mismatched=["feature_training_detail_exercises: expected='Упражнения', actual=('string', 'feature_training_detail_exercises', 'Упражнения!')"], unexpected=[], order=exact`; `verifyPaparazziDebug` (196/196 on the RED run): `detailWithPlans`, `detailEmptyHistory` and `detailNoPlanExercise`, both themes, `Images differ (by 0.001030%)` light and `(by 0.000947%)` dark, and only them — 6 failed of the 12 golden cases and 74 module cases (JUnit names the parameterized cases `[1] LIGHT` and `[2] DARK`; the delta files name `…_detailWithPlans_light.png` and the rest) |
| 48 | EN `feature_training_picker_title` `Add exercises` → `Add exercises.` | topology: `… values/strings.xml; mismatched=["feature_training_picker_title: expected='Add exercises', actual=('string', 'feature_training_picker_title', 'Add exercises.')"], unexpected=[], order=exact`; Native: the scene at step 1, the stored-catalog list (`Expected <[…, Add at least one exercise, Add exercises, Search exercises…, …]>, actual <[…, Add at least one exercise, Add exercises., Search exercises…, …]>`), before step 5, and nothing else (1 of 125) |
| 49 | S7, the RU plural's `few` item → `%1$d сессиии` | topology: `… values-ru/strings.xml; mismatched=["feature_training_detail_session_count: expected=(('one', '%1$d сессия'), ('few', '%1$d сессии'), …), actual=('plurals', …, (('one', '%1$d сессия'), ('few', '%1$d сессиии'), …))"], unexpected=[], order=exact`; `verifyPaparazziDebug` (196/196 on the RED run): `detailWithPlans` and `detailNoPlanExercise`, both themes, `Images differ (by 0.017057%)` light and `(by 0.015577%)` dark, and only them — 4 of 74 (`2 СЕССИИ`) |
| 50 | the Cyrillic identity renamed to `` `the history count is the total not the visible page` `` | topology: `exact test identities drifted in src/commonTest/kotlin/…/mvi/handler/CommonHandlerTest.kt; missing=['the История count is the total not the visible page'], unexpected=['the history count is the total not the visible page']`; Gradle GREEN (386/386, 84 suites, 483 cases passing), then the oracle on that run's XML, byte-identical under the five Pythons: `feature:single-training: expected exactly one testcase with classname='io.github.stslex.workeeper.feature.single_training.mvi.handler.CommonHandlerTest' name='the История count is the total not the visible page[iosSimulatorArm64]', found 0; parsed 63 testcase(s): […]`, the other thirteen modules `ok` |
| 51 | the confirm's title read drops `name` | host and Native: `permanent delete replaces the menu with its confirm in one write` and `a menu dismissed before the confirm's copy lands stays dismissed`, both at the strict fake: `the title passes the training's name; got []`; nothing else red (4 of 125) |

### 19.8 Repository and visual gates, deviations, and implementation decisions

**Repository and visual gates (Section 12.4)**, each its own invocation on the committed tree
`1476c3cd8`, in sequence, so detekt never ran beside tests:

| Command | Executed | Parsed result |
| --- | ---: | --- |
| `assembleDebug lintDebug testDebugUnitTest --continue` (the re-run, Section 19.9) | 2775/2775 | 470 fresh host XML files across 32 modules, 3,333 test cases, 0/0/0 (2,973 unique module/classname/name triples); single-training 4 suites, 62 cases, names exactly Section 3.2; lint: 36 modules `Lint found no errors or warnings`, and `app:app`, `app:dev` and `app:store` `Lint found 77 warnings` each, all dependency-version checks (GradleDependency 37, NewerVersionAvailable 34, AndroidGradlePluginVersion 6) in `gradle/libs.versions.toml` and `gradle-wrapper.properties`, none in source (7.13 read 75, with 32 NewerVersionAvailable); 86 GiB free before, 83 after |
| `detekt --continue` | 63/63 | 55 fresh reports, 0 findings |
| `assembleDebugAndroidTest --continue` | 2171/2171 | GREEN |
| `verifyPaparazziDebug` (the re-run, Section 19.9) | 673/673 | 13 liveness lines, 456 golden cases executed for 456 images: all-exercises 52/52, all-trainings 50/50, archive 14/14, exercise 48/48, exercise-chart 30/30, home 42/42, kit 86/86, live-workout 60/60, past-session 30/30, core plan-editor 18/18, settings 12/12, single-training 12/12, start-mode 2/2; 168 fresh XML files, 1,743 cases, 0/0/0; single-training 74 cases (12 golden and 62); no record task; porcelain empty |
| `:lint-rules:test` | 9/9 | 16 suites, 151 tests, 0/0/0 |
| `python3 documentation/personal_data_gate.py -v` | GREEN | `[PASS] no personal data in tracked files`; six excused lines, all pre-existing, in paths this branch does not touch |
| device: Smoke suite — `ANDROID_SERIAL=emulator-5554 ./gradlew connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.annotation=io.github.stslex.workeeper.core.ui.test.annotations.Smoke -Dorg.gradle.jvmargs=-Xmx3g --max-workers=2 --full-stacktrace --continue` | 2131/2131 | 14 fresh XML owners, 11 of them with cases, 44 cases: 41 passed and exactly the three documented skips, `ArchiveScreenTest`, `AllTrainingsScreenTest` and `AllExercisesScreenTest` `pendingFeatureRewrite`; 0 failures and errors; single-training's 3 and exercise-chart's 7 from `connected/androidMain`, the KMP device result directory. Then, as `run_smoke_ui_tests.sh` does, `assert_mvi_device_identities.py`: `core:ui:mvi (androidDeviceTest): 2 executed / 0 skipped / 0 failed / 0 errored — verified 2 exact identities` |
| device: Regression suite — the same with `…annotations.Regression` | 2131/2131 | 5 fresh XML owners, 88 cases, all executed, 0 skip, failure or error: app:app 49 (among them `RouteReachabilityTest` 15 with both single-training journeys, `StoreRetentionTest` 3, `UiAdmissionRaceTest` 3, `ApplicationBottomBarTest` 4, `AllTrainingsExtensionDbVisibilityTest` 1, `UiGenerationSwapTest` 1, `AppRuntimeUiHandshakeDeviceTest` 1), core:data:database 35, core:data:exercise 1, feature:all-exercises 1, feature:wear-bridge 2, the same membership as 7.13 |
| PNG on disk, after the Paparazzi gate | GREEN | 528/528 tracked PNGs hash to their index blobs; the all-PNG manifest `7cc28d13…`, the Paparazzi manifest `f903e721…` (456 entries) and the single-training blob set `e116929e…` equal Section 3.7's projections |
| boundary and hygiene (static) | GREEN | `git diff --check` clean for every commit and for base..head; the boundary of Section 19.10, checked on the staged tree before commit 5 and on the committed range after it |
| mockup shell gate | static GREEN | checks 1–6, 9 and 10 PASS; 7 and 8 unmeasured locally (Section 19.9) |

**Deviations and implementation decisions.** None changes a Section 0–18 decision.

- **"Seven files" (Section 3.1).** Both censuses count `R.*`'s 39 references in six files, which
  matches Section 3.1's own itemization. The launch prompt makes any census difference a STOP, so
  the run stopped before any edit. The maintainer ruled to proceed and record it here. No count of
  Section 12.2 differs.
- **Five interpreters.** Section 13's dry run named Python 3.9, 3.11, 3.12, 3.13 and 3.14. This
  host has no 3.12 and has 3.15 (Section 19.1). Every topology, oracle, audit and proof run used
  the host's five, and their outputs were byte-identical each time.
- **Three type witnesses.** kotlin.test could not infer `T` for three `commonTest` assertions on
  the nullable plan list. They took `assertEquals<List<PlanSetUiModel>?>`, which Section 11.1
  allows; no assertion changed (Section 19.5).
- **The scene's text input.** After each `SetText`, the scene asserts the dispatched input, then
  sets the test-held State to the typed value, as past-session's scene does (7.11 S5). Without that
  write, `BasicTextField` re-reported the held text on blur, and the action list gained two
  entries. Dispatch still only records, so Section 11.4's "the test-held State does not change on
  dispatch" holds, and its two lists are unchanged (Section 19.5).
- **Rename detection.** `CommonHandlerTest` was rewritten below 30 % similarity, so `git diff -M30%`
  shows it as a deleted and an added path. `NavigationHandlerTest` reads as `R042`, and the other 58
  renames at 62 % or more.
- **Commit 4's size.** The topology diff is 535 insertions against the prototype's 518. The
  difference is line wrapping and comments; the audit of Section 19.6 compared the fields, not the
  line count.
- **Scratch-script defects**, all fixed before their recorded verdicts, none in a committed file:
  - census A first read past the `dependencies` block, and census B counted a helper's own
    declaration as a call;
  - the comparison's regex missed wrapped bullets;
  - the gate runner's optional-flags array was unbound under bash 3.2;
  - the commit-1 proof's porcelain check passed outside a git worktree;
  - the transcription generator had one malformed capture, and two of the audit's 43 probes
    targeted the wrong text.
- **One process lapse.** Commit 3 staged its module with one `git add -A --
  feature/single-training`. The pathspec limited it to the module, whose manifest the topology gate
  pins file by file (Section 19.5). The maintainer's working rules forbid `git add -A`; every other
  staging named its paths.
- **Rotation.** The emulator's `accelerometer_rotation` flipped from 0 to 1 during device runs. It
  was set back to 0 before every device run.
- **Three emulator boots.** The 16 GB host could not hold the emulator beside Native compilation,
  so this run shut its emulator down for the host phases:
  - the first boot served Phase 2's baselines and commit 2;
  - the second served commits 3 and 4's device gates;
  - the third served controls 15 and 16 and the canonical suites.

  Each was booted by this run with the same command line and torn down with `adb emu kill`.
- **Control 48's Native half** fails the scene at step 1, the stored-catalog assertion. A
  single-method scene stops at its first failure, so that run never reaches step 5's rendering of
  the same value, as in 7.12 and 7.13.
- **Control 37** reads `the seven inherited production` suppressions. That is the gate's wording
  for the eight production annotations in seven files after S5; the row's fragment matches.
- **tech-debt.md beyond Section 9.2's four items**, by the maintainer's ruling of 2026-10-20 on
  review findings 1b and 2 of #340. Those findings were two D2 launches that fail silently, and the
  ruling deferred them with no code change. The section "The default flow `onError` is silent — 21
  of 22 production collections report nothing" is corrected rather than given a new row:
  - its Mechanism paragraph had called the action overload of `launch` not silent, which was
    wrong;
  - a dated paragraph adds the measurement, the count and the single-training instances;
  - "Why not now" adds that the fix should land before live-workout adopts D2's template.

### 19.9 Rejected runs, local limitations, and the remote evidence boundary

- **Two load-bearing runs were rejected, both from one disk-full event.** The first was the
  repository build gate. It ran on `1476c3cd8` from 18:19Z to 18:45Z with 2775/2775 executed, and
  the volume filled while it ran. The log records 44 `java.io.IOException: No space left on
  device`. Two tasks failed on writes, in modules this branch does not touch:
  - `:feature:exercise:testDebugUnitTest` failed on its binary results (`Index 99 out of bounds for
    length 3`, after a Kryo flush failed);
  - `:app:wear:testDevDebugUnitTest` failed on Robolectric's `TempDirectory` (`FileSystemException`)
    and an `EOFException`.

  The space came back on its own: 91 GiB were free a few minutes later, and the worktree was clean
  at HEAD.

  The second was the repository Paparazzi gate, three gates later (622/622 executed, `BUILD FAILED`,
  with 85 GiB free as it started). Its `:feature:exercise:testDebugUnitTest` failed again with
  `Index 99 out of bounds for length 3` before any test ran. The cause was the first run's leftover
  `results-generic.bin`, truncated during the disk-full window: Gradle reads a Test task's previous
  binary results when it starts, and `--rerun-tasks` does not skip that read. Every
  `build/test-results/*/binary` directory was listed and deleted (gitignored build output). Then
  both gates ran again whole as fresh invocations, recorded in Section 19.8 with free-disk samples
  around them. Detekt, `assembleDebugAndroidTest`, `:lint-rules:test` and the personal-data gate ran
  between the two failures; none of them reads those files, and each is recorded as it ran.
- **Three development runs** failed before any recorded gate, and none was recorded as evidence:
  - the early Native check's first compile (the three type witnesses);
  - the scene's first draft (it did not compile);
  - the scene's second run (the two extra actions).
- **Mockup shell gate.** It ran against the merge base `c00d5703d`. Checks 1–6, 9 and 10 PASS.
  This host has no headless Chromium, so the precondition row `headless browser available` FAILs
  and checks 7 and 8 report `UNMEASURED — the probe stopped at 'no-browser'`; the script exits 1.
  The branch touches neither `documentation/mockups/pass2d.html` nor `AppColors.kt`. CI's
  `Mockup Appearance Gate` measures 7 and 8.
- **Xcode.** Every Native run here used Xcode 27.0. CI pins Xcode 26.6, so the CI run on the PR is
  the Xcode 26.6 measurement.
- **Device suites** ran testing.md's canonical selection in CI's exact form
  (`run_smoke_ui_tests.sh` and `ui_tests.yml`: `-Dorg.gradle.jvmargs=-Xmx3g --max-workers=2
  --full-stacktrace --continue`), plus the stable flags. 7.10–7.13 left out the `-Xmx3g` memory
  flag; this host's memory pressure is why it is kept here. Neither flag changes which tests run.
- **iosArm64** test binaries are compiled and linked, never run: this host has no iOS device.
- **Not covered by any test** (Section 11.5):
  - RU values at runtime, outside the goldens (the topology gate pins them statically);
  - D2's guard on a device, and the confirm's one-dispatch delay;
  - the graph's wiring in the scene: snackbars, undo actions, `BackHandler`, the discard and
    conflict sheets, and the reorder drag;
  - the history rows' medium date off Android;
  - the inherited tag race.
- **The remote boundary.** This commit cannot record CI on its own head. The PR records three
  things: its CI run, the KMP iOS kit smoke job's duration against its 120-minute timeout (Section
  12.5, next to 7.13's), and the review.

### 19.10 Documentation boundary

At exit the module tracks exactly Section 9.1's 62 paths (the build file and 61 files under `src`),
against 61 at the base, and the commits change every one of them. Against the base, `git diff
-M30%` reads the build file as modified, 59 paths as renames, and two as added: the commonTest
`CommonHandlerTest` and the scene. It reads one as deleted, the classic `CommonHandlerTest`.
Outside the module the commits change exactly 14 paths, the 14 of Section 9.2: six app paths,
three CI paths and five documentation paths. Nothing remains under `src/main`, `src/test` or
`src/androidTest`.

The documentation edits are Section 9.2's:

- **architecture.md:** the shape-list sentence (the single-training factory flow beside
  image-viewer's, plan-editor's, past-session's and exercise-chart's), the Context-seam paragraph
  (`Exactly 4 feature/dialog Context readers remain.`), D5's State-mutation paragraph, D2's
  Localization paragraph, and the Per-feature MVI layout note.
- **ci-cd.md:** the workflows-table row, the alias list, the Native module description, the
  every-module list, the validator paragraph (`exactly 63 target tuples`),
  `All fourteen result directories`, `The same fourteen modules'` and `N/14 linked`.
- **testing.md:** the `AppGraph.stringResolver` bullet, the `Shared single-training feature`
  subsection, the Existing UI tests row, both golden module lists, the alias sentence, and the
  Native and device-link command blocks (`14/14`).
- **tech-debt.md:** three re-pointed links (`ui/SingleTrainingGraph.kt`,
  `mvi/handler/CommonHandler.kt` and `SingleTrainingScreenTest.kt`), the three widened rows in
  Shared (KMP) string resources, the tag-race row in State Mutation Discipline, and D2's template
  row with the intro's citation in Shared (KMP) feature entries — follow-ups. Beyond Section 9.2,
  the silent-`onError` section is corrected by the ruling recorded in Section 19.8.

Deliberately untouched, as Section 9.2 lists them:

- `documentation/features.md` lines 131–148;
- `documentation/feature-specs/trainings.md` line 904;
- `documentation/research/domain-boundary-audit-codex.md`;
- tech-debt.md's resolved line for `TrainingHistoryRow.kt`;
- `.claude/skills/*`;
- `graphify-out/`.
