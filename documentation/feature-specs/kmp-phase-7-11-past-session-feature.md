# KMP Phase 7.11 — feature:past-session becomes a shared feature entry

**Status:** SPECIFIED — authorized for implementation by the maintainer's GO of 2026-10-16 (Section 18)

**Discovery baseline:** dev @ 5beefec84f773fd69ceaf96ca2a220699c95c838 (2026-10-16, merge of #334).
`feature/past-session` is unchanged since Phase 7.10's merge (`733377255`, #330). The only commits
since then touch `.github/workflows/android_deploy_prod.yml` and `documentation/release-flow.md`.

**Template:** [Phase 7.10](kmp-phase-7-10-all-exercises-feature.md). This document is a delta. Every
rule of 7.10 that this document does not change applies unchanged, read with the substitutions of
Section 0.3. Through 7.10, the rules of 7.9, 7.8 and 7.7 that it inherits apply too.
[Phase 7.7](kmp-phase-7-7-plan-editor-feature.md) is also the precedent for the route-argument
factory flow (Section 6).

**Measured by:** the Phase 7.11 discovery run of 2026-10-16, which was machine-only. Sections 3.5 and
5.2 cite it.

---

## 0. Authority, decisions, substitutions

### 0.1 Authority order

1. live origin/dev at the implementation base;
2. AGENTS.md, CLAUDE.md, documentation/architecture.md, testing.md, ci-cd.md,
   compose-state-discipline.md;
3. the Phase 7.10 specification, and through it 7.9, 7.8 (including its Section 19 decisions P1, P1a
   and G1–G4) and 7.7 (the shape-B factory flow);
4. kmp-string-resolver.md (PR-R) as merged;
5. the checked-in source, resources, tests, PNGs and CI oracles at the implementation base.

### 0.2 Stable rules

7.10 Section 0.2 applies unchanged. It covers:

- the positive-gate flags `--rerun-tasks --no-build-cache --no-configuration-cache --console=plain`
  after `./gradlew --stop`;
- quoted `N actionable tasks: N executed`;
- evidence from JUnit XML written after the run's recorded start only;
- detekt and tests in separate invocations;
- signed, GitHub-verified commits with `Co-Authored-By` as the only trailer;
- no session identifier in a commit, the PR or a document;
- no merge by the implementer.

Suppressions: **no new suppression of any kind.** The one inherited production suppression,
`@Suppress("UNCHECKED_CAST")` on the processor cast in di/PastSessionFeature.kt, stays
byte-semantically unchanged. The module has no test suppression, and Phase 7.11 needs no Native
test-name suppression.

One session does all the work. 7.10 Section 19 used two independent census measurers and an
independent transcription audit. Here each is a script the implementer writes and runs, with its own
parser. None is a separate agent.

### 0.3 Substitutions against 7.10

| 7.10 | 7.11 |
| --- | --- |
| `feature:all-exercises`, `feature/all-exercises` | `feature:past-session`, `feature/past-session` |
| package `io.github.stslex.workeeper.feature.all_exercises` | `io.github.stslex.workeeper.feature.past_session` |
| resource package `…feature.all_exercises.resources` | `io.github.stslex.workeeper.feature.past_session.resources` |
| `AllExercisesFeature`, `AllExercisesGraph`, `allExercisesGraph` | `PastSessionFeature`, `PastSessionGraph`, `pastSessionGraph` |
| `allExercisesGraphFactory`, `createAllExercisesGraph()`, `allExercisesStore` | `pastSessionGraphFactory`, `createPastSessionGraph(screen)`, `pastSessionStore` |
| `AllExercisesStoreImpl`, `AllExercisesExtensionIdentityTest` | `PastSessionStoreImpl`, `PastSessionExtensionIdentityTest` |
| route `Screen.BottomBar.AllExercises`, shape A (`Feature`) | route `Screen.PastSession(sessionUuid)`, shape B (`FeatureAssisted`) |
| graph tag `AllExercisesGraph` | `PastSessionGraph` |
| `ALL_EXERCISES_ENTRY` | `PAST_SESSION_ENTRY` |
| ten-module Native command | eleven modules (Section 12.1) |
| 7.10's D1: all copy outside composition through `StringResolver` | Section 0.4's D1: the same, plus the grouping separator; the date stays on `ResourceWrapper` (S2) |

### 0.4 Decisions

Locked by the maintainer's GO (2026-10-16):

| Id | Decision |
| --- | --- |
| D1 | Tonnage grouping, option A. Compose resources cannot format `%,d`. So the catalog gains `feature_past_session_tonnage_group_separator` (EN `,`, RU U+00A0), `tonnage_format` becomes `%1$s kg` / `%1$s кг`, and the mapper groups the rounded tonnage itself, with the separator read through `StringResolver`. EN and RU output stays byte-identical (measured, Section 5.2). |
| D2 | The topology gate gains four additive, defaulted `FeatureEntry` fields in the **first** commit of this PR. That commit is proven not to change the three existing entries' behaviour (Section 12.0). |
| D3 | `core:data:database` leaves the module entirely: no main edge and no test edge (measured, Section 3.5). |

Specification decisions, delivered with this document. The maintainer accepts them by starting the
implementation (Section 18):

| Id | Decision |
| --- | --- |
| S1 | The 15 previews are already portable, so they stay verbatim. The gate pins their exact `@Preview` arguments through `preview_inventory` (Section 5.4). |
| S2 | The session date stays on `ResourceWrapper.formatMediumDate`, the way archive kept `formatDayMonth`. The gate allows exactly that one call, through `resource_wrapper_dates` (Section 5.3). |
| S3 | `PastSetRowOverflowGateTest` moves to androidHostTest, as kit's `SetColumnHeaderTest` did. It runs a layoutlib harness, so it is not portable (Section 11.2). |
| S4 | core:ui:kit stays `implementation`, as in feature:plan-editor: no public declaration of the module exposes a kit type (Section 10). |
| S5 | The iOS scene types into the set fields through the `SetText` semantics action (Section 11.4). |

Inherited and unchanged:

- P1: a bare `%d` in a migrated catalog becomes positional;
- P1a: the placeholder rule;
- D4 of 7.10: `.claude/skills/` stays untouched.

## 1. Bounded exit claim

The implementation may prove only this:

- feature:past-session applies `convention.kmpComposeLibrary` and retains Metro and Paparazzi;
- all 29 production Kotlin files compile from commonMain for Android, iosSimulatorArm64 and
  iosArm64;
- the exact private 23-identifier EN/RU catalog lives in commonMain Compose Resources. Its 22 existing
  identifiers change only by the 12 positional placeholders of P1 and D1's `tonnage_format` value,
  and D1 adds the separator key (Section 3.4);
- every visible Android behaviour is unchanged, except the inherited Compose-resources locale
  property, which D1 extends to the tonnage separator (Section 5.6);
- copy outside composition resolves through the injected `StringResolver`, never through CMP's
  top-level `getString` / `getPluralString`, and `ResourceWrapper` keeps exactly one read: the
  session date, in the mapper (Section 5.3);
- 6 portable suites with 40 exact identities execute on Android host and on Native; the one
  overflow-gate identity executes on Android host;
- the golden suite executes as 30 Android-host Paparazzi cases (15 methods × light/dark) against the
  same PNG blobs;
- one deterministic iOS production scene proves the whole catalog through the real
  `ComposeStringResolver`. It also proves the totals copy through the production mapper, including
  grouped tonnage, and renders the three phases, the session sheet and both dialogs. It checks
  action dispatch too;
- the Metro graph is reached through the explicit generation-owned factory with the route argument
  (shape B), never `LocalContext` or `Context.appDeps`; all four extension identities use the
  accessor;
- the topology gate checks the entry through one `FeatureEntry`, after D2's additive
  generalization; `Context.appDeps` readers drop from 8 to 7;
- `core:data:database` is no edge of the module;
- Android remains releasable with every repository PNG blob unchanged.

This is a source-set, dependency, resource, test and composition-root migration. It does not permit:

- redesigning the screen;
- changing disclosure, editing, reorder, delete or PR-explainer behaviour;
- adding a device test;
- changing `core:ui:kit` or `core:data:exercise`;
- adding an iOS app;
- migrating another feature.

## 2. Candidate selection — fresh census

Measured at the discovery baseline with 7.9's method:

- production = `src/main/kotlin`;
- unit = `src/test/kotlin`;
- EN string keys = `<string` elements of `values/strings.xml`;
- PNGs = files under `src/test/snapshots`.

| Feature | Prod files | Prod lines | Unit files | Unit lines | EN string keys | PNGs |
| --- | ---: | ---: | ---: | ---: | ---: | ---: |
| **past-session** | **29** | **2,378** | 8 | 2,067 | **20** | 30 |
| home | 39 | 3,085 | 15 | 1,924 | 35 | 42 |
| exercise-chart | 41 | 3,214 | 10 | 2,314 | 31 | 30 |
| settings | 51 | 3,326 | 12 | 2,638 | 65 | 12 |
| single-training | 39 | 3,635 | 6 | 1,582 | 39 | 12 |
| exercise | 46 | 4,141 | 13 | 2,890 | 53 | 48 |
| live-workout | 59 | 6,755 | 27 | 7,903 | 96 | 60 |

Past-session is smallest in production files, production lines and string keys. Single-training and
home have fewer unit lines. Every row equals 7.10's table except live-workout, which has grown since
(6,666 → 6,755 production lines, 7,611 → 7,903 unit lines).

Every module past-session depends on is already KMP:

- core:core, core:ui:kit, core:ui:mvi, core:ui:navigation, core:ui:plan-editor and
  core:data:exercise;
- core:data:database, which no source file uses and D3 removes.

Every symbol its production code imports from them is declared in commonMain (kit's `Res` accessors
are generated there), with two exceptions:

- `IODispatcher` is a commonMain `expect` with platform actuals;
- `core.ui.mvi.di.appDeps` lives in androidMain and leaves with the factory flow (Section 6).

The golden harness (`core.ui.kit.golden.*`, an Android library) is imported only by the two files
that stay on the Android host (Section 11.2).

## 3. Measured baseline

Static measurements at the discovery baseline. Test names are given as PR-N left them.

### 3.1 Production manifest

All 29 files move from `src/main/kotlin/io/github/stslex/workeeper/feature/past_session/` to
`src/commonMain/kotlin/io/github/stslex/workeeper/feature/past_session/` with unchanged relative
paths:

| File | Lines |
| --- | ---: |
| di/PastSessionFeature.kt | 37 |
| di/PastSessionGraph.kt | 45 |
| di/PastSessionHandlerStore.kt | 9 |
| di/PastSessionHandlerStoreImpl.kt | 15 |
| di/PastSessionScope.kt | 8 |
| domain/PastSessionInteractor.kt | 25 |
| domain/PastSessionInteractorImpl.kt | 74 |
| domain/mapper/PastSessionDomainMapper.kt | 72 |
| domain/model/DetailWithPrs.kt | 7 |
| domain/model/ExerciseTypeDomain.kt | 7 |
| domain/model/SessionDetailDomain.kt | 22 |
| domain/model/SetDomain.kt | 10 |
| domain/model/SetTypeDomain.kt | 9 |
| mvi/handler/ClickHandler.kt | 227 |
| mvi/handler/CommonHandler.kt | 50 |
| mvi/handler/InputHandler.kt | 156 |
| mvi/handler/NavigationHandler.kt | 21 |
| mvi/mapper/PastSessionUiMapper.kt | 207 |
| mvi/model/PastSessionUiModels.kt | 48 |
| mvi/store/DialogState.kt | 31 |
| mvi/store/PastSessionStore.kt | 121 |
| mvi/store/PastSessionStoreImpl.kt | 63 |
| ui/PastSessionGraph.kt | 58 |
| ui/PastSessionScreen.kt | 339 |
| ui/components/DeleteConfirmDialog.kt | 41 |
| ui/components/PastExerciseCard.kt | 355 |
| ui/components/PastSessionHeader.kt | 75 |
| ui/components/PastSessionSheets.kt | 33 |
| ui/components/PastSetEditRow.kt | 213 |
| **29 files** | **2,378** |

The one inherited production suppression, unchanged by this phase:

- `@Suppress("UNCHECKED_CAST")` — di/PastSessionFeature.kt line 27 (the processor cast).

Android coupling in production:

- `R.*` in seven files:
  - six UI files read 16 identifiers at 19 sites through Android `stringResource`. The files are
    `ui/PastSessionGraph.kt`, `ui/PastSessionScreen.kt`, and in `ui/components/`
    `PastSessionHeader.kt`, `PastExerciseCard.kt`, `PastSessionSheets.kt` and
    `DeleteConfirmDialog.kt`. Three identifiers are read in both the screen and the graph:
    `error_not_found`, `error_load_failed` and `save_failed_snackbar`;
  - `mvi/mapper/PastSessionUiMapper.kt` reads the other 6 through `ResourceWrapper`;
- `ResourceWrapper` itself in two files:
  - the mapper, with 6 copy calls (4 `getString`, 2 `getQuantityString`) and 1 `formatMediumDate`;
  - `CommonHandler`, which only passes it to the mapper;
- `androidx.compose.ui.res.stringResource` imported in `ui/components/PastSetEditRow.kt` beside CMP's
  `stringResource`. Every read there is a kit Compose resource, so the Android import resolves
  nothing;
- `LocalContext` with `appDeps` in di/PastSessionFeature.kt;
- `@VisibleForTesting` on `PastSessionStoreImpl.NAME`.

Already portable:

- the 15 previews: `androidx.compose.ui.tooling.preview.Preview` with an explicit
  `AppTheme(themeMode = ThemeMode.…)`, as commonMain code elsewhere already uses;
- `Icons.Filled.DragHandle`, which kit's commonMain already draws through
  `libs.cmp.material.icons.extended`.

None of these occurs: `BackHandler`, `System.currentTimeMillis`, `.format(`, `java.*`, `uiMode`,
`android.content`, `CompositionLocal`.

### 3.2 Portable manifest — 6 suites, 40 identities

Each file moves from `src/test/kotlin/…/feature/past_session/` to
`src/commonTest/kotlin/…/feature/past_session/` with its relative path.

**domain/PastSessionInteractorImplTest.kt — 4**

- observeDetailWithPrs emits null when session is missing
- observeDetailWithPrs combines fresh detail with PR set uuids
- updateSet maps domain SetDomain to data SetsDataModel and forwards
- deleteSession forwards to SessionRepository deleteSession

**mvi/handler/ClickHandlerTest.kt — 11**

- OnDeleteClick opens the confirmation and closes the sheet it came from
- OnSessionMenuClick opens the overflow sheet with a haptic
- OnSheetDismiss hides the sheet
- OnPrTagClick opens the explainer and OnPrExplainerDismiss hides it
- OnDeleteClick while the explainer is shown replaces the dialog
- OnDeleteConfirm deletes the session closes dialog and navigates back with snackbar
- OnRetryLoad consumes Init
- OnSetTypeChange updates the row and persists the changed type
- OnExerciseHeaderClick expands exactly the tapped card and touches nothing else
- OnExerciseHeaderClick collapses the tapped card and only that card
- OnExerciseHeaderClick with an unknown uuid changes nothing

**mvi/handler/CommonHandlerTest.kt — 6**

- Init success loads mapped detail
- Init with missing session switches to SessionNotFound phase
- Init failure switches to LoadFailed phase
- Init flags PR set with isPersonalRecord
- Init seeds the first card open on the first Loaded emission
- a re-emission carries the user's open set instead of reseeding

**mvi/handler/InputHandlerTest.kt — 5**

- OnSetWeightChange marks invalid weight and skips persist
- OnSetRepsChange clears reps error when value becomes valid
- rapid valid inputs debounce to a single persist with the latest value
- invalid follow-up input cancels in-flight persist
- persist failure reverts to the last saved set and emits SaveFailedSnackbar

**mvi/handler/NavigationHandlerTest.kt — 1**

- Back triggers popBack

**mvi/mapper/PastSessionUiMapperTest.kt — 13**

- mapper covers adhoc header skipped rows weighted volume and no-set exercise
- mapper marks the PR-bearing set with isPersonalRecord
- mapper leaves all sets unflagged when prSetUuids is empty
- mapper sums tonnage over weighted sets
- mapper excludes a weightless exercise carrying a residual weight from tonnage
- mapper omits the tonnage term when a weighted session logged no weights
- mapper builds the collapsed summary per exercise type
- mapper excludes unfilled sets from the collapsed summary
- mapper marks a weighted set with no logged weight instead of printing bare reps
- first Loaded state seeds exactly the first card open
- first Loaded state with no exercises seeds nothing
- Loaded to Loaded carries the previous open set pruned to live exercises
- a non-Loaded next phase is returned untouched

Suite distribution: 4/11/6/5/1/13. Every name is Native-legal: none contains `.`, `;`, `[`, `]`,
`/`, `<`, `>`, `:` or `\`, and all are ASCII. One name contains an apostrophe
(`a re-emission carries the user's open set instead of reseeding`).

Today's test doubles:

- JUnit 5 in all six suites;
- a relaxed MockK `PastSessionInteractor` in the click, common and input handler suites;
- a relaxed MockK `Navigator` in NavigationHandlerTest;
- relaxed MockK `SessionRepository`, `SetRepository` and `PersonalRecordRepository` in the domain
  suite, which captures `SetRepository.update`'s set through a slot;
- `logger = mockk(relaxed = true)` inside each of the three handler suites' in-file
  `private class TestStore`.

Those `TestStore`s are already deterministic fakes. They apply state updates, record events and
consumed actions, and run every launch on the test dispatcher.

Thirteen identities run through copy resolution:

- the 9 mapper identities that call `toUi` (every mapper identity except the four
  `withExpansionCarriedFrom` ones);
- the 4 CommonHandlerTest identities whose flow emits a detail: `Init success loads mapped detail`,
  `Init flags PR set with isPersonalRecord`, `Init seeds the first card open on the first Loaded
  emission`, and `a re-emission carries the user's open set instead of reseeding`.

Each of the two suites stubs `ResourceWrapper` with an in-file object today:

- **the mapper suite:** `R`-keyed strings, quantity-aware plurals (`1 exercise` / `2 exercises`),
  `formatMediumDate(90_000L) = "Apr 28"`;
- **the CommonHandler suite:** quantity-blind plurals (`"$quantity exercises"`), no `adhoc_label`,
  `formatMediumDate(60_000L) = "Apr 28"`.

Both build `tonnage_format` as `"${args[0]} kg"`. Every fixture tonnage is under 1,000, so no expected
string depends on grouping:

- the mapper suite: 770, 500 and 735;
- the CommonHandler suite: 900 in the PR identity, and none in the others.

`PastSetRowOverflowGateTest` (`gate/`, one identity, `weightedRowValuesFitTheirSlots`) is not part of
the portable manifest. It drives `OverflowGateSdk`, the golden harness's layoutlib measurement, and
moves to androidHostTest (S3, Section 11.2).

### 3.3 Golden, overflow and device ownership

`golden/PastSessionGoldenTest.kt` has 15 methods parameterized over `GoldenTheme`, in this order:

    screenLoaded, screenError, topbar, sheetSessionMenu, header, headerWithoutTonnage,
    cardWithSets, cardCollapsed, cardDoubleDigitIndex, cardSkippedEmpty, cardSkippedWithSets,
    setRowPlain, setRowPersonalRecord, setRowWeightless, setRowInvalidReps

That is 30 render cases and 30 PNGs, all mode 100644, named
`io.github.stslex.workeeper.feature.past_session.golden_PastSessionGoldenTest_<method>_<dark|light>.png`.

The PNGs hold 28 distinct blobs. `cardSkippedEmpty` and `cardSkippedWithSets` share their dark
(`6fd5057111d0…`) and light (`29fa73ad69a1…`) blobs, as testing.md's "A golden fixture can be
mutation-blind" paragraph explains.

The goldens never reach the mapper or a resolver:

- fixtures build `PastSessionUiModel` and the row models directly, with pre-formatted strings such
  as `5 exercises · 14 sets · 4,820 kg`;
- the golden test imports no `R`;
- the harness renders in EN.

After the move, the goldens pixel-check these composable reads through Compose resources, as in
7.10:

- `loading_title`, `error_not_found` and `action_retry` (`screenError`);
- `finished_at_label` (the header goldens and `screenLoaded`);
- both section labels (`screenLoaded`);
- `skipped_line` (`cardSkippedEmpty`, `cardSkippedWithSets`);
- `menu_delete_session` (`sheetSessionMenu`).

No golden renders `no_sets`, `error_load_failed`, `save_failed_snackbar` or either dialog.
`action_more` is a content description and draws no pixel. The scene asserts all of these
(Section 11.4).

There is no device test. The build declares three `androidTestImplementation` edges and a
`debugImplementation` of the test manifest, but `src/androidTest` holds no file. Those edges are dead
and leave (Section 10).

### 3.4 Exact feature-local resource catalog

Each locale owns 20 strings and 2 plurals at the baseline, 22 identifiers. At exit each owns 21
strings and 2 plurals, 23 identifiers in this file order.

Values are shown as they must be at exit, decoded the way the topology gate compares them (DOM
text). The baseline differs from them in three ways:

- P1 replaces the bare `%d` of 12 plural items (EN 4, RU 8) with `%1$d`;
- D1 changes `tonnage_format` from `%,d kg` / `%,d кг`;
- D1 adds identifier 21.

| # | Identifier | EN | RU |
| ---: | --- | --- | --- |
| 1 | feature_past_session_loading_title | Past session | Прошедшая сессия |
| 2 | feature_past_session_adhoc_label | Ad-hoc workout | Свободная тренировка |
| 3 | feature_past_session_error_not_found | Session not found | Сессия не найдена |
| 4 | feature_past_session_error_load_failed | Could not load session | Не удалось загрузить сессию |
| 5 | feature_past_session_action_retry | Retry | Повторить |
| 6 | feature_past_session_action_delete | Delete | Удалить |
| 7 | feature_past_session_delete_dialog_title | Delete session? | Удалить сессию? |
| 8 | feature_past_session_delete_dialog_body | This permanently removes the session and all its sets. The training template is unchanged. | Сессия и все её подходы будут безвозвратно удалены. Шаблон тренировки не изменится. |
| 9 | feature_past_session_deleted_snackbar | Session deleted | Сессия удалена |
| 10 | feature_past_session_save_failed_snackbar | Could not save change — try again | Не удалось сохранить — попробуйте ещё раз |
| 11 | feature_past_session_skipped_line | skipped | пропущено |
| 12 | feature_past_session_no_sets | No sets logged | Подходы не записаны |
| 13 | feature_past_session_section_logged | Logged | Записано |
| 14 | feature_past_session_section_editable | editable | можно править |
| 15 | feature_past_session_finished_at_label | Finished · %1$s | Завершена · %1$s |
| 16 | feature_past_session_action_more | More | Ещё |
| 17 | feature_past_session_menu_delete_session | Delete session | Удалить сессию |
| 18 | feature_past_session_totals_format | %1$s · %2$s | %1$s · %2$s |
| 19 | feature_past_session_totals_format_with_tonnage | %1$s · %2$s · %3$s | %1$s · %2$s · %3$s |
| 20 | feature_past_session_tonnage_format | %1$s kg | %1$s кг |
| 21 | feature_past_session_tonnage_group_separator | `,` | U+00A0 (`&#160;` in the file) |
| 22 | feature_past_session_exercises_count | plural, below | plural, below |
| 23 | feature_past_session_sets_count | plural, below | plural, below |

| Plural | EN one / other | RU one / few / many / other |
| --- | --- | --- |
| exercises_count | `%1$d exercise` / `%1$d exercises` | `%1$d упражнение` / `%1$d упражнения` / `%1$d упражнений` / `%1$d упражнений` |
| sets_count | `%1$d set` / `%1$d sets` | `%1$d подход` / `%1$d подхода` / `%1$d подходов` / `%1$d подходов` |

(Plural identifiers carry the `feature_past_session_` prefix.)

The XML moves byte-semantically, except P1 and D1:

- neither root carries `xmlns:tools`;
- the RU file has no comment;
- the EN file's comments move unchanged, except the tonnage comment above `tonnage_format`. It
  explains `%,d`, so it is replaced by exactly:

      <!--
          Session tonnage (v3 §11.1). Compose resources format only positional %N$d / %N$s, so the
          mapper groups the figure itself with the separator below: "4,820" in en and "4 820" in ru
          (the mockup's form). The ru separator is a no-break space, so the figure cannot wrap apart.
      -->

The separator lines follow `tonnage_format` directly:

    EN: <string name="feature_past_session_tonnage_group_separator">,</string>
    RU: <string name="feature_past_session_tonnage_group_separator">&#160;</string>

The RU value is the numeric character reference `&#160;`, which the DOM parse and Compose resources
both decode to U+00A0. A literal U+00A0 in the file would be invisible in review, so it is not used.

Nothing outside the module consumes these identifiers, and the generated `Res` stays private.

The baseline's 22 identifiers are all read by production code:

- 16 in composition (Section 3.1);
- 6 outside it, by the mapper;
- none in both.

At exit, the separator is the mapper's seventh.

### 3.5 Declared dependencies and D3

Today's build file declares:

- `convention.composeLibrary`, Metro and Paparazzi, with `includeJavax()`;
- `implementation` edges on `core:core`, `core:ui:kit`, `core:ui:mvi`, `core:ui:navigation`,
  `core:ui:plan-editor`, `core:data:database` (line 28) and `core:data:exercise`;
- `testImplementation` of `kotlin("test")` and the golden harness;
- the dead device edges of Section 3.3.

**D3, measured on 2026-10-16 in a probe worktree at `733377255`, reverted afterwards.** No file
under `feature/past-session/src` mentions `core.data.database`. The only `core:data:database` type
on the repository interfaces past-session uses is `PlanSetDataModel`, inside the data class
`SessionRepository.AddExerciseResult`. That is a return type, which a fail-fast fake overrides as
`Nothing`. `SetRepository` and `PersonalRecordRepository` name no `core:data:database` type.

Three configurations were compiled:

| Configuration | Edge | Probe | Result |
| --- | --- | --- | --- |
| C1 | present | a main anchor naming `PlanSetDataModel`, and test fakes delegating all three repositories (`class X(d: I) : I by d`) | `compileDebugKotlin` and `compileDebugUnitTestKotlin` GREEN; both compile classpaths list `project ':core:data:database'` once |
| C2 | deleted | the same | `compileDebugKotlin` RED with exactly two errors, both in the anchor: `Unresolved reference 'database'` and `Unresolved reference 'PlanSetDataModel'` |
| C3 | deleted | fakes only | `compileDebugKotlin` and `compileDebugUnitTestKotlin` GREEN; neither compile classpath lists `core:data:database` |

Delegation makes the compiler emit an override for every member, so C3 proves that every member
signature of the three repositories resolves without the edge on the Android unit-test classpath.

Native was not measured. The early Native check of Section 12.3 is the first measurement. If the
commonTest Native compile needs the edge, that is a STOP-and-amend toward a commonTest-only edge
(7.10's D5 shape), never a silent addition.

### 3.6 External consumers and root identity

| Surface | Ownership |
| --- | --- |
| Module edges | app/app `implementation` (stays); app/common `implementation` → `api` |
| Navigation registration | app/common `AppNavigationHost` calls `pastSessionGraph`, tag `PastSessionGraph` |
| Graph aggregation | app/app `AppGraph` (gains the accessor) |
| Extension identities | app/app test `PastSessionExtensionIdentityTest`: 4 identities, through `asContribution` today |
| Direct composition | none: no app test composes `pastSessionGraph()` directly |
| Device journey by tag | `RouteReachabilityTest.pastSessionOpensFromASeededFinishedSessionAndHomeReturns` (`@Regression` class): opens the screen from Home's recent row and returns through `PastSessionBackButton` |
| Route | `Screen.PastSession(sessionUuid)`, core:ui:navigation commonMain, unchanged |

No path outside the module reads `io.github.stslex.workeeper.feature.past_session.R` or a
`feature_past_session_` identifier.

### 3.7 Context readers and PNG integrity

Eight executable `Context.appDeps` readers exist (`EXPECTED_APP_DEPS_READERS`): seven navigation
entries and app-dialogs. Removing past-session's leaves seven.

PNG manifests at the discovery baseline use 7.10 Section 3.7's method. They are reproduced at
`5beefec84` with git and with an independent Python reader, and the two agree. The base equals
7.10's merge projection, because nothing since has touched a PNG.

| Manifest | Entries | Hash |
| --- | ---: | --- |
| Paparazzi mode/blob/path | 456, 13 owners | f8792e1efeca575a6b1bfa6bf780c89b52a0475392e3f9fea3f04f90d598443b |
| Paparazzi path list | 456 | 27b831bcc04b22f872dc2221abd310c3e33211f9f4163c42f0226822a4d6d44d |
| All repository PNGs, mode/blob/path | 528 | ce590ad5c3a859541f8249cb95ea33e33104b5ba326699b323289b15c2fb3f36 |
| past-session blob set (30 sorted blob IDs, duplicates kept) | 30 | 85bb492445882305ebcbd415ab54cb0543edc8b4181f76c2c4815ea14446c22c |

If only the 30 paths move to `src/androidHostTest/snapshots/images/`, the projections are:

- cc526e47fd76e92f7427de44de3aa31e91a625a8fec12db7b59b2a09fe85cd00 (Paparazzi mode/blob/path);
- d855238ee9806022020e424953fbb54dd809c175e692921e418377a4f31cacc2 (path list);
- b5ed4b9d415feb88d515bd0de3359f9f2d3b0881df29828ca98f60476911ad08 (all PNGs).

**The normative check is relative, as in 7.10.** At implementation entry:

1. take the base manifests;
2. rewrite exactly the 30 past-session paths from `src/test/` to `src/androidHostTest/`;
3. require the head manifests to equal the rewritten base byte for byte;
4. require the blob-set hash to be unchanged.

### 3.8 Measurement limitation

The discovery ran Gradle only for the baseline host counts (7 suites / 41 cases and 8 / 71,
Section 11.2) and the D3 probes, and it measured D1's grouping on one emulator only (Section 5.2).
It ran no Native task and no device test. The implementation records fresh entry gates
(Section 12) and stops if the environment for any of them is missing.

## 4. Platform/API blockers — settled decisions

### 4.1 Android resources become private Compose resources

7.10 Section 4.2 applies with the package of Section 0.3. No `StringResource`, `PluralStringResource`,
Android id, `ResourceWrapper` or `StringResolver` enters State, Action, Event, UI models, domain
models or repository APIs.

### 4.2 Android APIs leave common

| Today | At exit |
| --- | --- |
| `LocalContext` + `context.appDeps<…>()` | explicit factory with the route argument (Section 6) |
| `androidx.annotation.VisibleForTesting` | removed; `NAME` stays private |
| `R`, `androidx.compose.ui.res.*` | generated `Res`, `org.jetbrains.compose.resources.*` in composition |
| `ResourceWrapper` copy reads | `StringResolver` (D1, Section 5.3) |
| `ResourceWrapper.formatMediumDate` | unchanged (S2, Section 5.3) |
| `%,d` platform grouping | `groupThousands` with the catalog separator (D1, Section 5.2) |

There is no `BackHandler`, clock, paging or `uiMode` to port.

### 4.3 Native test names

None. Every identity of Section 3.2 is Native-legal. No `INVALID_CHARACTERS_NATIVE_ERROR` suppression
is authorized.

## 5. Resource, State, copy and preview architecture

### 5.1 Private generated resource owner

    src/main/res/values/strings.xml    -> src/commonMain/composeResources/values/strings.xml
    src/main/res/values-ru/strings.xml -> src/commonMain/composeResources/values-ru/strings.xml

These are byte-semantic moves, except P1 on the 12 plural items and D1 (Section 3.4). `src/main/res`
holds nothing else.

### 5.2 D1 — what the grouping does today, and why the separator is a catalog value

Today, `tonnage_format` (`%,d kg`) reaches `Resources.getString(int, Object...)` through
`ResourceWrapper`. In the android-34 and android-36 sources, that call is
`String.format(mResourcesImpl.getConfiguration().getLocales().get(0), raw, formatArgs)`. The group
separator follows the configuration's first locale, even where the strings fall back to EN.

The discovery measured `%,d` with a d8-dexed probe on `emulator-5554`:

- API 34, Android 14, ICU 72.1, CLDR 42, `sdk_gphone64_arm64`;
- the host JDK 21.0.11 as an anchor;
- all 13 locale lines byte-identical between the two.

| Locale | Separator | 4820 |
| --- | --- | --- |
| en, en-US, en-GB | U+002C | `4,820` |
| ru, ru-RU, uk-UA, be-BY, kk-KZ | U+00A0 | `4 820` |
| de-DE | U+002E | `4.820` |
| fr-FR | U+202F | `4 820` |
| ar-EG | U+066C, Arabic-Indic digits | `٤٬٨٢٠` |

Through the real catalog, `catalog-en` printed `4,820 kg` (U+002C, then U+0020 before `kg`), and
`catalog-ru` printed `4 820 кг` (U+00A0, then U+0020 before `кг`).

Compose resources cannot carry that format. Two facts from the Compose Multiplatform 1.11.1 sources
decide this:

- `PrepareComposeResources.kt` (`XmlValuesConverterTask.getItemRecord`) stores each `<string>` as
  `handleSpecialCharacters(node.textContent)`, Base64-encoded. That is a DOM decode with no trimming,
  so `&#160;` survives as U+00A0;
- the runtime formats only `%(\d+)\$[ds]`, with each argument's `toString()`
  (`StringResourcesUtils.kt`, `replaceWithArgs`). A `%,d` would ship verbatim, and the placeholder
  rule (P1a) already rejects it.

D1 therefore moves the separator into the catalog: `,` in EN and U+00A0 in RU. The mapper inserts it
every three digits from the right. For every tonnage, EN and RU output is byte-identical to the
measured output. The exit build's prepared catalog is checked once (Section 12.3): the `values` and
`values-ru` `.cvr` records of the separator must decode to U+002C and U+00A0. Any other value is a
STOP.

Coverage limit: one Android runtime. After the move the separator no longer depends on the
platform's CLDR data.

design-system.md pins grouping in numerals to an NBSP (`ChartReadoutMapper.formatGrouped`'s
`GROUP_SEPARATOR`), because the bundled Archivo cut has no NNBSP glyph. D1 does not adopt that pin:
en keeps its comma. The concern behind it does not reach this label. The separator stops coming
from a locale API, the ru one is the same U+00A0, and the totals line is set in IBM Plex Mono
(`AppUi.typography.mono.meta`), whose three bundled weights have glyphs for U+00A0 and U+202F.
Section 9.2 records the difference in design-system.md and architecture.md.

### 5.3 Copy outside composition — D1 and S2

`PastSessionUiMapper` keeps its `internal object` and file. Its entry point becomes:

    suspend fun SessionDetailDomain.toUi(
        resourceWrapper: ResourceWrapper,
        stringResolver: StringResolver,
        prSetUuids: Set<String> = emptySet(),
    ): PastSessionUiModel

Each read keeps its resource, quantity and arguments:

- `adhoc_label` becomes `stringResolver.getString(Res.string.feature_past_session_adhoc_label)`;
- `buildTotalsLabel` becomes `private suspend fun buildTotalsLabel(` and takes
  `stringResolver: StringResolver` in place of `resourceWrapper`. Its two plurals become
  `stringResolver.getPluralString(Res.plurals.…, count, count)`, and its `totals_format` and
  `totals_format_with_tonnage` reads become `stringResolver.getString(Res.string.…, args…)` with the
  same arguments;
- the tonnage term reads the separator first, then formats the grouped figure:

      val separator = stringResolver.getString(
          Res.string.feature_past_session_tonnage_group_separator,
      )
      return stringResolver.getString(
          Res.string.feature_past_session_totals_format_with_tonnage,
          exercises,
          sets,
          stringResolver.getString(
              Res.string.feature_past_session_tonnage_format,
              roundedTonnage.groupThousands(separator),
          ),
      )

- a private helper groups the figure, with `private const val THOUSANDS_GROUP_SIZE = 3` beside
  `WEIGHT_DECIMAL_FACTOR`:

      /**
       * "4820" → "4,820": Compose resources cannot format `%,d`, so the figure is grouped here with
       * the catalog's [separator] — a comma in en, a no-break space in ru.
       */
      private fun Long.groupThousands(separator: String): String = toString()
          .reversed()
          .chunked(THOUSANDS_GROUP_SIZE)
          .map { it.reversed() }
          .reversed()
          .joinToString(separator)

The mapper then holds exactly 5 `stringResolver.getString(` and 2 `stringResolver.getPluralString(`.
The separator is read only when the rounded tonnage is positive.

These are unchanged:

- the rounding (`roundToLong`, then `<= 0L` drops the third figure);
- the tonnage sum, which counts WEIGHTED exercises only;
- the set count (`reps > 0`) and the active-exercise count (`!skipped`);
- `setSummary`, `formatWeight`, `withExpansionCarriedFrom`, both `SetType` mappings and
  `formatElapsedDuration`;
- the KDoc example `"5 exercises · 14 sets · 4,820 kg"`.

**S2, the date.** `val finishedAtLabel = resourceWrapper.formatMediumDate(finishedAt)` stays, and it
is the only `ResourceWrapper` read in the module. It is a locale-aware date format, not catalog copy.
The precedent is archive's `formatDayMonth` (7.8).

`ResourceWrapper`'s only implementation is androidMain's `AndroidResourceWrapper` (`DateFormat`), so
iOS has none. The scene therefore stubs the date and only the date (Section 11.4). A portable date
formatter is out of scope.

`CommonHandler` keeps `ResourceWrapper` for that date and injects the resolver. Constructor order:
`interactor`, `resourceWrapper`, `stringResolver`, `store`.

    private val stringResolver: StringResolver,

The collector calls
`it.detail.toUi(resourceWrapper = resourceWrapper, stringResolver = stringResolver, prSetUuids = it.prSetUuids)`
inside the existing `Flow.launch { result -> … }`, whose `each` is `suspend`. The mapping still
happens before `updateStateImmediate`, never inside its lambda.

**Threading does not change**, for the reason 7.10 Section 5.2 gives. The mapping runs where today's
`ResourceWrapper` call runs: on the store's collection dispatcher. CMP's first catalog read in a
process is a blocking asset read, and later reads are cached. Do not move the work in this phase.

The argument rule of 7.10 Section 5.2 applies. `StringResolver` returns the catalog text verbatim
when a call passes no format arguments. So a plural read that dropped its count would ship
`%1$d exercises`. The strict fakes (Section 11.1) and control 41 exist for this.

The events, the State and the UI models keep their shape:

- `Event.HapticClick(type: HapticFeedbackType)`, `Event.ShowError(errorType: ErrorType)`,
  `Event.DeletedSnackbar`, `Event.SaveFailedSnackbar`;
- `PastSessionUiModel`'s pre-formatted strings.

`ui/PastSessionGraph.kt` keeps its composition-side copy for the snackbars.

This module follows tech-debt.md's "Shared (KMP) string resources" rule, so it adds no debt row and no
`TODO(tech-debt)` marker.

### 5.4 Composable copy and previews (S1)

The six UI files of Section 3.1 resolve copy with `org.jetbrains.compose.resources.stringResource` on
the private `Res`, in the same places and with the same arguments, for example
`stringResource(Res.string.feature_past_session_finished_at_label, detail.finishedAtAbsoluteLabel)`.
`ErrorContent`'s `when` yields a `StringResource`.

`ui/PastSessionScreen.kt` also reads kit's `Res` (`core_ui_kit_action_back`). There, the feature's
class is imported as `import io.github.stslex.workeeper.feature.past_session.resources.Res as
PastSessionRes`, after archive's `ArchiveRes` and plan-editor's `PlanEditorRes`. Every other file
imports the feature's `Res` unaliased.

`ui/components/PastSetEditRow.kt` changes only by losing its Android `stringResource` import.

The two `TODO(tech-debt): UI mapping boundary` markers, in ui/PastSessionGraph.kt and
ui/PastSessionScreen.kt, move unchanged.

**Previews (S1).** The 15 previews are already portable: CMP's `Preview` annotation, an explicit
`AppTheme(themeMode = ThemeMode.…)`, and no `uiMode`. They stay byte-identical, names included:

| File | `@Preview` arguments, in source order |
| --- | --- |
| ui/components/PastSetEditRow.kt | `name = "Weighted Light"`, `name = "Record Dark"`, `name = "Weightless Dark"`, `name = "Error"` |
| ui/components/DeleteConfirmDialog.kt | `name = "Light"`, `name = "Dark"` |
| ui/components/PastSessionHeader.kt | `name = "Light"`, `name = "Dark"` |
| ui/components/PastExerciseCard.kt | `name = "Expanded — Light"`, `name = "Collapsed — Dark"`, `name = "Skipped — Collapsed"` |
| ui/PastSessionScreen.kt | `name = "Loaded — Light"`, `name = "Loaded — Dark"`, `name = "Loading"`, `name = "Error"` |

That makes 15 `@Preview(` in total. Rewriting them into 7.10's Light/Dark subject shape would change
UI files for no behaviour. D2's `preview_inventory` pins them instead (Section 12.0).

### 5.5 Back

The module declares no `BackHandler`, and system back reaches Nav3's default pop, as today. There is
no back contract.

### 5.6 The inherited locale property, extended by D1

7.9 Section 5.7 applies, and from this phase it also covers past-session's copy. The screen follows
CMP's primary-locale choice:

- a device whose locale list is [uk-UA, ru-RU] shows it in Russian today and in English after the
  move;
- counts render with Latin digits.

D1 extends the property to the grouping separator, which now comes from the selected catalog
instead of the configuration's first locale. Against Section 5.2's measurement, at exit:

| Device locale | Today | At exit |
| --- | --- | --- |
| en-* | `4,820 kg` | `4,820 kg` (identical) |
| ru-RU | `4 820 кг` (U+00A0) | identical |
| [uk-UA, ru-RU] | RU strings, U+00A0 | EN strings, `4,820 kg` |
| de-DE | `4.820 kg` | `4,820 kg` |
| fr-FR | `4 820 kg` (U+202F) | `4,820 kg` |
| ar-EG | Arabic-Indic digits, U+066C | Latin digits, `4,820 kg` |

Locales the discovery did not measure follow the same rule. At exit, the selected catalog's
separator applies in groups of three, whatever the platform grouped with before; en-IN, for
instance, was not measured.

`StringResolver` has the same property (its KDoc, and tech-debt.md's 🟡 row). The remedy is the
maintainer's open decision, not part of this phase.

## 6. Explicit generation-owned shape-B factory flow

7.10 Section 6 applies, with the route argument of 7.7 Section 6:

- `AppRootDeps` gains `val pastSessionGraphFactory: PastSessionGraph.Factory` after
  `allExercisesGraphFactory`, with the KDoc
  `/** Generation-owned factory for the past-session graph extension. */`;
- `AppGraph` overrides it, after `allExercisesGraphFactory`;
- `App.kt` passes `pastSessionGraphFactory = deps.pastSessionGraphFactory` after the all-exercises
  line;
- `AppNavigationHost` takes a required `pastSessionGraphFactory: PastSessionGraph.Factory` after
  `allExercisesGraphFactory`. It calls `pastSessionGraph(factory = pastSessionGraphFactory, modifier = …)`,
  and the modifier chain and the tag `PastSessionGraph` are unchanged;
- `pastSessionGraph` takes a required `factory: PastSessionGraph.Factory` as its first parameter. It
  builds `navComponentScreen(PastSessionFeature(factory)) { processor -> … }`: the `FeatureAssisted`
  overload, which hands the typed `Screen.PastSession` key to `processor(screen)`;
- `PastSessionFeature` becomes an `internal class` with `private val factory: PastSessionGraph.Factory`,
  still `FeatureAssisted<PastSessionStoreProcessor, Screen.PastSession>`. Its `processor(screen)`
  returns:

      rememberMetroStoreProcessor<PastSessionStoreImpl> {
          factory
              .createPastSessionGraph(screen)
              .pastSessionStore
      } as PastSessionStoreProcessor

- `createPastSessionGraph(screen)` runs once, inside the retained `rememberMetroStoreProcessor`
  lambda. The route argument stays a `@Provides` bound instance (7.7 Section 6), and
  `PastSessionStoreImpl` still reads `screen.sessionUuid`;
- di/PastSessionGraph.kt is unchanged;
- app/common's edge becomes `api(project(":feature:past-session"))`, placed after
  `api(project(":feature:all-exercises"))`. The comment "The twelve entry providers
  AppNavigationHost composes." stays true.

`LocalContext`, `appDeps`, `asContribution`, nullable fallbacks, registries and pre-admission
resolution are forbidden.

`PastSessionExtensionIdentityTest` gets a new helper:

    private fun AppGraph.pastSession(sessionUuid: String): PastSessionGraph = pastSessionGraphFactory
        .createPastSessionGraph(Screen.PastSession(sessionUuid = sessionUuid))

The `asContribution` import goes. The four names, the call sites (`.pastSession("session-1")` in all
four identities, plus `.pastSession("session-2")` in the fourth) and every assertion stay exact.

No app test composes `pastSessionGraph()` directly, so the app boundary is six paths (Section 9.2).

## 7. In scope

Only these:

- D2's additive gate generalization, as this PR's first commit (Section 12.0);
- the conversion of Sections 4–6;
- the moves of Sections 3.1–3.4, P1 and D1's catalog change;
- D1, S2 and the removal of `@VisibleForTesting`;
- the composable copy and the verbatim previews;
- porting the six suites to kotlin.test with deterministic in-file fakes;
- moving the golden test and the overflow-gate test to androidHostTest;
- removing the dead device edges and `core:data:database` (D3);
- one iosTest scene;
- the six app paths, the three CI paths and the six documentation paths;
- one signed, green PR that the maintainer merges.

## 8. Explicit non-goals

7.10 Section 8 applies. In addition:

- no change to `core:ui:kit`, `core:core` (`ResourceWrapper` gets no iOS implementation) or
  `core:data:exercise`. The `PlanSetDataModel` inside `SessionRepository.AddExerciseResult` stays,
  as 7.10's follow-up;
- no portable date formatter (S2);
- no UI path for `OnSetTypeChange`, because the row's type chip has no click today. Also out: no
  drag-reorder scene and no sheet-dismiss gesture;
- no device test for the module;
- no change to the two `TODO(tech-debt)` markers or to their rows' text. Only the links are
  re-pointed. Row 435's stale `deleteDialogVisible` text stays as it is;
- no edit to `.claude/skills/` (7.10's D4), including add-feature.md line 41;
- no new test identity beyond the scene, no rename of any Section 3.2 identity, and no strengthened
  assertion;
- no change to `graphify-out/`.

## 9. Exact allowed change boundary

### 9.1 Target module — 71 tracked paths at exit

- `build.gradle.kts`;
- 29 production files under `src/commonMain/kotlin/io/github/stslex/workeeper/feature/past_session/`
  (Section 3.1);
- `src/commonMain/composeResources/values/strings.xml` and `values-ru/strings.xml`;
- 6 suites under `src/commonTest/kotlin/io/github/stslex/workeeper/feature/past_session/`
  (Section 3.2 paths);
- `src/androidHostTest/kotlin/io/github/stslex/workeeper/feature/past_session/golden/PastSessionGoldenTest.kt`;
- `src/androidHostTest/kotlin/io/github/stslex/workeeper/feature/past_session/gate/PastSetRowOverflowGateTest.kt`;
- 30 PNGs under `src/androidHostTest/snapshots/images/` (Section 3.3 names);
- `src/iosTest/kotlin/io/github/stslex/workeeper/feature/past_session/PastSessionFeatureSceneIosTest.kt`.

Nothing remains under `src/main`, `src/test` or `src/androidTest`. There is no androidMain, iosMain,
androidDeviceTest, shared test helper, fixture or extra catalog.

### 9.2 Root, CI and documentation — 15 paths

App (6):

    app/app/src/main/java/io/github/stslex/workeeper/di/AppGraph.kt
    app/app/src/test/kotlin/io/github/stslex/workeeper/di/PastSessionExtensionIdentityTest.kt
    app/common/build.gradle.kts
    app/common/src/main/kotlin/io/github/stslex/workeeper/App.kt
    app/common/src/main/kotlin/io/github/stslex/workeeper/app/common/di/AppRootDeps.kt
    app/common/src/main/kotlin/io/github/stslex/workeeper/host/AppNavigationHost.kt

CI (3):

    .github/scripts/assert_kmp_ui_source_topology.py
    .github/scripts/assert_kmp_ios_smoke.py
    .github/workflows/android_build_unified.yml

Documentation (6):

    documentation/architecture.md
    documentation/ci-cd.md
    documentation/design-system.md
    documentation/testing.md
    documentation/tech-debt.md
    documentation/feature-specs/kmp-phase-7-11-past-session-feature.md

What changes in each document. This list includes every place 7.10's five commits touched for
all-exercises, among them the four testing.md places its Section 9.2 missed. Find them with
`git diff 97c03dfe1 29ca1d6fc -- documentation/`. Every module count there reads eleven.
It adds design-system.md, which 7.10 did not touch: D1 bears on its grouping rule (Section 5.2).

- **architecture.md:**
  1. In the shape list, item 2 (`FeatureAssisted`): the sentence that names image-viewer as "the
     bounded factory-resolution exception" names image-viewer, plan-editor and past-session as the
     shape-B factory-resolution exceptions. Their generation-owned root passes the required
     `ImageViewerGraph.Factory` / `PlanEditorGraph.Factory` / `PastSessionGraph.Factory` through
     `AppNavigationHost` and `imageViewerGraph` / `planEditorGraph` / `pastSessionGraph` into
     `ImageViewerFeature` / `PlanEditorFeature` / `PastSessionFeature`. Each invokes it only inside
     the `rememberMetroStoreProcessor` creation lambda. Plan-editor has used this flow since 7.7 and
     was missing from the sentence.
  2. The Context-seam paragraph: past-session joins the list, `AppGraph.pastSessionGraphFactory`
     joins the accessor list, and the sentence reads "Exactly 7 feature/dialog Context readers
     remain."
  3. Localization, "Pluralization": two sentences after "…rejects anything else in a
     `commonMain/composeResources` catalog.": "That rules out `%,d` too, so past-session groups its
     tonnage in the mapper, with a separator from its own catalog
     (`feature_past_session_tonnage_group_separator`), to keep `4,820` in en and `4 820` in ru. The
     numerals of `ChartReadoutMapper.formatGrouped` keep design-system.md's pinned NBSP."
- **ci-cd.md**, the places 7.10 updated for all-exercises:
  - the `android_build_unified.yml` row of the workflows table;
  - the KMP-shaped alias list;
  - the Native job's module description, which adds `:feature:past-session` (all 40 portable cases
    plus the production scene: the catalog and the totals copy through the real `StringResolver`,
    grouped tonnage included, the three phases, the session sheet, both dialogs, and dispatch);
  - the every-module-is-checked list;
  - the validator paragraph, which adds: "The past-session feature validator requires all 40
    portable tuples and
    `io.github.stslex.workeeper.feature.past_session.PastSessionFeatureSceneIosTest.resourcesTotalsPhasesSheetDialogsAndActionsRenderAndDispatch`
    exactly once, for exactly 41 target tuples.";
  - "All eleven result directories";
  - "The same eleven modules'" and `N/11 linked`.
- **design-system.md:** one sentence after "`formatGrouped` feeds the chart readout and all three
  footer statrows.": "Past-session's tonnage is grouped per language instead (`4,820` in en,
  `4 820` in ru), with a separator its mapper reads from the feature's own catalog
  (`feature_past_session_tonnage_group_separator`): that is no locale API either, the ru separator
  is the same NBSP, and the totals line is set in IBM Plex Mono, which has glyphs for both NBSP and
  NNBSP."
- **testing.md:**
  1. The `AppGraph.stringResolver` bullet. It says the accessor still has no production reader, and
     that all-exercises' `ClickHandler` and `PagingHandler` and past-session's `CommonHandler`
     receive the binding by constructor injection through their graph extensions.
  2. A "Shared past-session feature" subsection after "Shared all-exercises feature". It covers:
     - the 40 identities and the overflow gate on Android host;
     - the kept `TestStore`s and the in-file fakes;
     - the strict resolver fakes and the date-only `ResourceWrapper` fakes;
     - D3 (no `core:data:database` edge, not even in tests);
     - the scene, with D1's grouped totals and its date stub;
     - what it does not cover (Section 11.5);
     - the 30 goldens moved byte-identical;
     - the identity test through `appGraph.pastSessionGraphFactory`;
     - the absence of a device test, and the `RouteReachabilityTest` journey.
  3. "Visual gate — Paparazzi screenshot goldens": in both module lists, past-session moves from
     the classic modules (`src/test`) to the KMP modules (`src/androidHostTest`).
  4. The sentence naming the KMP-shaped modules that both alias spellings cover adds
     `:feature:past-session`.
  5. The Native command block under "Running tests" adds `:feature:past-session:iosSimulatorArm64Test`.
  6. The device-link block adds `:feature:past-session:linkDebugTestIosArm64`, and its comment reads
     `11/11`.

  "Shared string resolver" stays as it is: its statement about the first execution concerns
  all-exercises' scene and stays true.
- **tech-debt.md:** the three links into old past-session paths are re-pointed to
  `src/commonMain/kotlin/…`. At the baseline they are the rows at lines 27, 28 and 435. Row 489
  names its location as plain text and stays.

Known stale references after the move, deliberately untouched. They are historical records, already
stale before this phase, or owned by the follow-up documentation PR:

- `documentation/feature-specs/v2.4-design-foundation.md`,
  `documentation/research/domain-boundary-audit-codex.md` and
  `documentation/research/v2.3-hotfix-findings.md`, which link old past-session paths;
- `documentation/kmp-migration-assessment.md`;
- `.claude/skills/*` (7.10's D4), including add-feature.md line 41;
- tech-debt.md row 435's text (Section 8);
- `graphify-out/`.

A need for any other path is a STOP.

## 10. Gradle and public API contract

~~~kotlin
plugins {
    alias(libs.plugins.convention.kmpComposeLibrary)
    alias(libs.plugins.metro)
    alias(libs.plugins.paparazzi)
}

compose.resources {
    packageOfResClass = "io.github.stslex.workeeper.feature.past_session.resources"
}

metro {
    interop {
        includeJavax()
    }
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation(project(":core:core"))

            implementation(project(":core:ui:kit"))
            api(project(":core:ui:mvi"))
            api(project(":core:ui:navigation"))
            api(project(":core:ui:plan-editor"))
            implementation(project(":core:data:exercise"))

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
}

apply(from = "$rootDir/gradle/golden-gate.gradle.kts")
~~~

Why each visibility:

- `api(project(":core:ui:mvi"))`: `PastSessionStore`, `PastSessionHandlerStore` and
  `PastSessionStoreImpl` extend the MVI contracts;
- `api(project(":core:ui:navigation"))`: `pastSessionGraph` extends `NavGraphScope`, and
  `PastSessionGraph.Factory` takes a `Screen.PastSession`;
- `api(project(":core:ui:plan-editor"))`: `PastSetUiModel.type` and
  `Action.Click.OnSetTypeChange.type` are its `SetTypeUiModel`;
- `api(libs.cmp.ui)`: `pastSessionGraph` takes a `Modifier`, and `Event.HapticClick` carries a
  `HapticFeedbackType`;
- `api(libs.coroutines.core)`: `PastSessionInteractor.observeDetailWithPrs` returns a `Flow`, and
  `PastSessionGraph.ioDispatcher` is a `CoroutineDispatcher`;
- `api(libs.kotlinx.collections.immutable)`: the State and the UI models carry `ImmutableSet` /
  `ImmutableList`;
- `implementation(libs.cmp.animation)`: `animateContentSize`, `animateFloatAsState` and `tween` in
  `PastExerciseCard`;
- `implementation(libs.cmp.material.icons.extended)`: `Icons.Filled.DragHandle` in `PastSetEditRow`.
  If the build shows the icon is not in that artifact, STOP and amend; never switch icons;
- core:ui:kit stays `implementation` (S4). No public declaration names a kit type, and
  `StringResolver` is injected only into the internal `CommonHandler`. app/app and app/common
  declare kit themselves, so Metro's extension implementation resolves it there.

`kotlin("test")` appears exactly twice, once in commonTest and once in iosTest.

Removed relative to today:

- the `core:data:database` edge (D3);
- `testImplementation` (replaced by commonTest and `androidHostTestImplementation`);
- every `androidTestImplementation` and the `debugImplementation` of the test manifest. There is no
  device test, so neither the Compose BOM nor test-utils guard applies.

The module's own history comments move with the build file: the Metro and Paparazzi plugin comments
and the `includeJavax` comment. Rationale comments adapted from the bullets above may be added. No
comment may hold a dependency fragment or a forbidden build token.

Verify Gradle metadata rather than copying it blindly (7.8 Section 10). A dependency the measured
source does not need is removed. One it needs is a STOP-and-amend, never a silent addition.

## 11. Test and Native scene contract

### 11.1 Portable suites

The six files move to commonTest. JUnit 5 and MockK give way to `kotlin.test`,
`kotlinx-coroutines-test` and deterministic in-file fakes: named private classes, no shared helper
file. Android host and Native each execute exactly the 40 identities of Section 3.2, with zero
failure, error or skip, in the distribution 4/11/6/5/1/13.

7.10 Section 11.1 and the fake rules its Section 19.7 records apply. In this module that means:

- **The three handler suites keep their `TestStore`.** It already applies state updates, records
  events and consumed actions, and runs each launch on the test dispatcher. Only its `logger`
  changes: the relaxed mock becomes a getter that fails fast
  (`error("logger must not be read by <Handler>")`), as 7.9's and 7.10's store fakes have it. No
  handler of this module reads the logger.
- **Interactor fakes.** Each of the three handler suites declares its own private
  `PastSessionInteractor` (4 members):
  - ClickHandlerTest: `deleteSession` and `updateSet` record their arguments;
    `observeDetailWithPrs` and `reorderSets` fail fast;
  - CommonHandlerTest: `observeDetailWithPrs(SESSION_UUID)` returns the flow the identity sets up
    (`flowOf(…)`, a throwing `flow { }`, or a `MutableSharedFlow`); any other argument and the other
    three members fail fast;
  - InputHandlerTest: `updateSet` records its arguments, and throws `IllegalStateException("boom")`
    in the identity that sets up a failure; the other three members fail fast.
- **NavigationHandlerTest** declares a private `Navigator` (6 members). `popBack()` counts; the
  other five fail fast.
- **The domain suite** declares three repository fakes: `SessionRepository` (26 abstract members),
  `SetRepository` (12) and `PersonalRecordRepository` (4). Only the reached members behave:
  - `getSessionDetail` returns the detail the identity sets up for `"session-1"`, and null for any
    other argument, as the relaxed mock did;
  - `deleteSession` records;
  - `SetRepository.update` records `(performedExerciseUuid, set)` in place of the slot;
  - `observePrSetUuids` returns the identity's flow for its key.

  Every other member fails fast. A fail-fast member may override its return type as `Nothing`, which
  keeps `SessionRepository.AddExerciseResult`'s `PlanSetDataModel` off the source (D3). The fakes
  import data shapes directly, which PR-L's test-source-set exemption allows.
- **MockK verifications become recorded-state assertions**, as in 7.10:
  - `coVerify(exactly = 1) { … }` becomes exactly one recorded call with the same arguments, or one
    that satisfies the same predicate (ClickHandlerTest's `match { … }` conjunction stays one
    conjunction);
  - `coVerify(exactly = 0)` becomes an empty record;
  - `verify(exactly = 1) { navigator.popBack() }` becomes a count of 1;
  - the domain suite's `slot` becomes the recorded set.

**StringResolver fakes (D1).** `PastSessionUiMapperTest` and `CommonHandlerTest` each declare their
own private fake. A fake answers by switching on the generated resource objects, as testing.md's
"Shared string resolver" prescribes, and throws on any other resource. It also checks arguments. A
read throws unless it passes exactly what its production call passes:

- no format argument for `adhoc_label` and `tonnage_group_separator`;
- exactly two for `totals_format`;
- exactly three for `totals_format_with_tonnage`;
- exactly one for `tonnage_format`;
- `formatArgs == [quantity]` for every plural.

The return values reproduce today's stubs:

| Resource | Mapper-suite fake | CommonHandler-suite fake |
| --- | --- | --- |
| `adhoc_label` | `Ad-hoc workout` | throws (not reached) |
| `totals_format` (a, b) | `a · b` | `a · b` |
| `totals_format_with_tonnage` (a, b, c) | `a · b · c` | `a · b · c` |
| `tonnage_format` (a) | `a kg` | `a kg` |
| `tonnage_group_separator` | `,` | `,` |
| `exercises_count` (quantity q) | `q exercise` for q = 1, otherwise `q exercises` | `q exercises` |
| `sets_count` | `q set` for q = 1, otherwise `q sets` | `q sets` |

The separator rows are new, and each returns the EN catalog value. Every fixture tonnage is under
1,000 (Section 3.2), so no expected string changes.

**ResourceWrapper fakes (S2).** Both suites keep their in-file `ResourceWrapper` object, reduced to
the date:

- `formatMediumDate` answers as today: `90_000L → "Apr 28"` in the mapper suite and
  `60_000L → "Apr 28"` in CommonHandlerTest. It throws on any other timestamp;
- `getString`, `getQuantityString`, `getAbbreviatedRelativeTime` and `formatDayMonth` throw;
- the suites' `R` imports go.

**Mechanics only:**

- `toUi` is `suspend`, so the 9 mapper identities that call it become `= runTest { … }`. Each keeps
  its name, inputs and assertions, and passes the date fake and the resolver fake;
- the four `withExpansionCarriedFrom` identities stay as they are;
- every `CommonHandler(…)` construction gains `stringResolver = …`;
- the mapper suite's comment about `"%,d kg"` grouping goes with the `ResourceWrapper` copy stub. Its
  replacement says the mapper groups the figure with the catalog separator, and that every fixture
  here is under 1,000.

No other assertion changes. The inherited casts and `!!` after `kotlin.test` contract assertions
stay, together with their warnings (7.10 Section 19.7).

### 11.2 Android host: goldens and the overflow gate (S3)

7.10 Section 11.2 applies. `PastSessionGoldenTest` moves to androidHostTest byte-identical, as
all-exercises' did. Its 15 method names, light and dark cases, subjects, fixtures and 30 filenames
stay exact. The PNGs move with `git mv`, and `verifyPaparazziDebug` executes all 30 cases.
`recordPaparazziDebug` is forbidden.

`PastSetRowOverflowGateTest` moves byte-identical to
`src/androidHostTest/kotlin/…/feature/past_session/gate/`, beside kit's androidHostTest
`SetColumnHeaderTest`, which drives the same `OverflowGateSdk`. It sits outside `*.golden.*`, so
both host runs execute it:

- `testAndroidHostTest`: 7 suites and 41 cases (the 40 portable cases and the gate);
- `verifyPaparazziDebug`: 8 suites and 71 cases, with the goldens.

Those equal the baseline's 7/41 and 8/71.

### 11.3 Android device

The module has no device test, and its dead device edges leave (Section 10). The device proof of the
factory flow is the app journey
`RouteReachabilityTest.pastSessionOpensFromASeededFinishedSessionAndHomeReturns`, which runs on the
recorded serial (Section 12.3) and in the canonical Regression suite (Section 12.4).

### 11.4 Deterministic iOS production scene

Exactly one class and method, no comma:

    class PastSessionFeatureSceneIosTest
    fun resourcesTotalsPhasesSheetDialogsAndActionsRenderAndDispatch()

The scene's setup:

- it uses `runComposeUiTest` and the production `PastSessionScreen(state, consume)` inside
  `AppTheme`;
- a test-held `State` drives the screen, and `consume` records actions;
- it uses the bounded settle stepping of all-exercises' scene (7.10 Section 19.7);
- every copy read outside composition goes through one `ComposeStringResolver()` instance
  (`resolver`), kit's labels included;
- it declares no fake resolver;
- `ResourceWrapper` has no iOS implementation (Section 5.3). So the scene declares one private stub.
  The stub answers `formatMediumDate` for exactly the five timestamps of step 2 and throws on every
  other call and member. It is the scene's only test double.

In one bounded scene it proves:

1. **Catalog (D1):** through `resolver`, all 21 strings and both EN quantities (1 → one, 2 → other)
   of both plurals resolve to the exact EN values of Section 3.4. Each is read as stored, with no
   format argument, which gives the verbatim template, for example `%1$s kg`, and `,` for the
   separator. Each placeholder-bearing value is also read filled:
   - `finished_at_label` with `"Apr 28"` gives `Finished · Apr 28`;
   - `totals_format` with `"2 exercises"` and `"3 sets"` gives `2 exercises · 3 sets`;
   - `totals_format_with_tonnage` with `"1 exercise"`, `"5 sets"` and `"4,820 kg"` gives
     `1 exercise · 5 sets · 4,820 kg`;
   - `tonnage_format` with `"4,820"` gives `4,820 kg`;
   - each plural is read with its count as the one format argument.
2. **Totals copy (D1, S2):** the production
   `toUi(resourceWrapper = dateStub, stringResolver = resolver, prSetUuids = …)` turns five fixtures
   into exactly these values. Sets are written as reps × weight, and "—" is a null weight. `startedAt`
   is 0, except T5's, which is 1_000:

   | Fixture | Exercises | finishedAt | trainingName | finishedAtAbsoluteLabel | durationLabel | totalsLabel |
   | --- | --- | ---: | --- | --- | --- | --- |
   | T1, ad-hoc | `pe-pull` Pull Up, weightless: 12 × —; `pe-bench` Bench, weighted: `set-bench-1` 5 × 100.0, `set-bench-2` 3 × 90.0 (FAILURE); `pe-fly` Skipped Fly, weighted, skipped, no sets | 90_000 | `Ad-hoc workout` | `Apr 28` | `01:30` | `2 exercises · 3 sets · 770 kg` |
   | T2, `Push Day` | Bench, weighted: four 10 × 100.0, one 8 × 102.5 | 3_725_000 | `Push Day` | `May 2` | `1:02:05` | `1 exercise · 5 sets · 4,820 kg` |
   | T3, `Leg Day` | Squat, weighted: three 1 × 333.2 | 59_000 | `Leg Day` | `Jun 3` | `00:59` | `1 exercise · 3 sets · 1,000 kg` |
   | T4, `Max Day` | Deadlift, weighted: one 1 × 1234567.0 | 600_000 | `Max Day` | `Jul 4` | `10:00` | `1 exercise · 1 set · 1,234,567 kg` |
   | T5, `Core Day` | Plank, weightless: two 15 × 30.0 | 1_000 | `Core Day` | `Aug 5` | `00:00` | `1 exercise · 2 sets` |

   T1 reaches the date stub with 90_000, T2 with 3_725_000, T3 with 59_000, T4 with 600_000 and T5
   with 1_000. What the fixtures cover:
   - T3 crosses a group boundary through rounding (999.6 → 1,000);
   - T4 has three groups;
   - T5's residual weights stay out of the tonnage;
   - T1, with `prSetUuids = {"set-bench-1"}`, also gives the set summaries `12` and `100×5 · 90×3`,
     an empty summary for Skipped Fly, and exactly one set with `isPersonalRecord`.
3. **Phases:**
   - Loading, from `State.create("scene-session")`: the title `Past session`, no `More` button,
     nothing below the bar;
   - Error, for each of the three `ErrorType`s: `Session not found`, `Could not load session` and
     `Could not save change — try again`, each with `Retry` and the title `Past session`, and no
     `More` button;
   - Loaded, from T1's model with `hasResolved = true` and `expandedExerciseUuids = {"pe-bench"}`:
     - the title `Ad-hoc workout` and the `More` button;
     - the header `FINISHED · APR 28` (`AppLabel` uppercases), `01:30` and
       `2 exercises · 3 sets · 770 kg`;
     - the section labels `LOGGED` and `EDITABLE`;
     - the three card names;
     - the collapsed plan lines `12` and `skipped`;
     - the expanded Bench rows, with weight fields `100` and `90`, reps fields `5` and `3`, and
       the `PR` tag on the first row;
   - Loaded again, with `expandedExerciseUuids = {"pe-fly"}`: `No sets logged`.
4. **Sheet:** with `bottomSheetState = SessionMenu`, `Delete session`.
5. **Dialogs:**
   - with `dialogState = DeleteConfirm`: `Delete session?`, its body, `Delete` and kit's `Cancel`;
   - with `dialogState = PrExplainer`: kit's `Personal record`, its body and `OK`.
6. **Dispatch:** one exact list, in this order, containing these 11 entries:

   | Interaction | Action |
   | --- | --- |
   | `Retry` in the first Error phase | `OnRetryLoad` |
   | the back button (`PastSessionBackButton`) | `OnBackClick` |
   | `More` | `OnSessionMenuClick` |
   | the `Pull Up` card header | `OnExerciseHeaderClick("pe-pull")` |
   | `SetText` `102.5` into the first Bench weight field | `OnSetWeightChange("set-bench-1", "102.5")` |
   | `SetText` `6` into the first Bench reps field | `OnSetRepsChange("set-bench-1", "6")` |
   | the `PR` tag | `OnPrTagClick` |
   | the sheet item (`PastSessionMenu_Delete`) | `OnDeleteClick` |
   | the delete dialog's `Delete` | `OnDeleteConfirm` |
   | the delete dialog's `Cancel` | `OnDeleteDismiss` |
   | the PR explainer's `OK` | `OnPrExplainerDismiss` |

   The two text entries use the `SetText` semantics action (`performTextReplacement`) on the
   fields kit labels `weight, kilograms` and `repetitions`, read through `resolver` (S5). No scene
   in the repository types text yet, so the first run is the proof. If the action is unavailable on
   the simulator, STOP with the exception (Section 14).

It may use production test tags, texts, content descriptions, scrolling and deterministic fixtures.
`PR` is kit's literal tag label. It may not add a production test tag, a platform host, a
repository, a database, a fake Store, a fake `StringResolver`, a screenshot baseline or a second
identity.

The module's Native oracle is 41 tuples: the 40 portable identities plus this scene.

### 11.5 What is not covered

- RU values execute in no test. The topology gate pins them statically, and the `.cvr` check of
  Section 12.3 pins the separator once.
- On Android host, the grouping runs only through the strict fake, and every fixture there is under
  1,000. The scene's step 2 is the only executed guard of `groupThousands` (control 26). No executed
  test notices a hardcoded EN separator; the copy contract's count does (control 27).
- The overflow gate runs on Android host only.
- `OnSetReorder`, `OnDragStarted`, `OnSheetDismiss` and `OnSetTypeChange` have no scene path.
  `OnSetTypeChange` has no UI path at all today.

testing.md records these (Section 9.2).

## 12. CI ownership and positive verification

### 12.0 D2 — the gate generalization (commit 1)

`check_feature_entry` assumes four things that past-session breaks:

- a no-argument `create…Graph()`;
- a device test file that every entry has;
- exactly two `Light`/`Dark` previews per subject;
- no `ResourceWrapper.formatMediumDate` call anywhere.

Commit 1 changes only `.github/scripts/assert_kmp_ui_source_topology.py`. It adds four defaulted
fields to `FeatureEntry`, appended after `extra_checks`, and four guarded branches to
`check_feature_entry`. No entry is touched, and with the defaults every existing check runs exactly
as before.

| Field | Default | Semantics |
| --- | --- | --- |
| `create_args: str` | `""` | The retained-factory template becomes `.{create_method}({create_args})`. The invocation-count message reads `{create_method}({create_args}) must be invoked once, inside the Store lambda`. |
| `device_test: str` (existing) | — | An empty `device_test` means the module has no device test, and its file is not read. Empty `device_test` with non-empty `device_fragments` fails: `device fragments [...] need a device test`. |
| `preview_inventory: dict` | `{}` | Keys are files (relative to the package dir); values are the exact `@Preview(...)` argument strings in source order, as `re.findall(r"@Preview\(([^)]*)\)", source)` returns them. The required total of `@Preview(` becomes `2 × len(preview_subjects) + Σ len(values)`. A mismatch fails: `<file> preview inventory must be exactly [...]; actual=[...]`. |
| `resource_wrapper_dates: dict` | `{}` | Keys are files; values map a `ResourceWrapper` method name to its exact call count. In a declared file, a call of a declared method is exempt from the "resolves … copy through ResourceWrapper.…" rule and is counted instead: `<file> must read the ResourceWrapper.<method> date exactly N time(s); found M`. A declared file must also be a declared reader (`… declares a ResourceWrapper date but is not a declared reader`) and must exist (`… declares a ResourceWrapper date but is missing`). |

Reference implementation. It is normative for the messages; formatting may differ.

~~~python
    # in FeatureEntry, after `extra_checks: tuple = ()`:
    # Additive shape fields (kmp-phase-7-11-past-session-feature.md Section 12.0). The defaults
    # keep every earlier entry's checks and failure text byte-identical.
    create_args: str = ""
    preview_inventory: dict = field(default_factory=dict)
    resource_wrapper_dates: dict = field(default_factory=dict)

    # check_feature_entry, ResourceWrapper section:
    dates = {
        common_main / relative: methods
        for relative, methods in entry.resource_wrapper_dates.items()
    }
    # ... inside the existing `for match in re.finditer(...)` loop, first statement:
            if match.group(1) in dates.get(path, {}):
                continue
    # ... after that loop:
    for path, methods in dates.items():
        relative = path.relative_to(root)
        if path not in readers:
            failures.append(
                f"{module}: {relative} declares a ResourceWrapper date but is not a declared reader"
            )
        source = common_sources.get(path)
        if source is None:
            failures.append(f"{module}: {relative} declares a ResourceWrapper date but is missing")
            continue
        for method, expected in methods.items():
            found = len(re.findall(rf"resourceWrapper\s*\.\s*{re.escape(method)}\s*\(", source))
            if found != expected:
                failures.append(
                    f"{module}: {relative} must read the ResourceWrapper.{method} date exactly "
                    f"{expected} time(s); found {found}"
                )

    # previews:
    preview_count = 2 * len(entry.preview_subjects) + sum(
        len(arguments) for arguments in entry.preview_inventory.values()
    )
    # ... after the existing preview_subjects loop:
    for relative, expected_arguments in entry.preview_inventory.items():
        path = common_main / relative
        actual_arguments = re.findall(r"@Preview\(([^)]*)\)", common_sources.get(path, ""))
        if actual_arguments != expected_arguments:
            failures.append(
                f"{module}: {path.name} preview inventory must be exactly {expected_arguments!r}; "
                f"actual={actual_arguments!r}"
            )

    # device:
    if entry.device_test:
        device_source = (root / entry.device_test).read_text(encoding="utf-8")
        for fragment in entry.device_fragments:
            if device_source.count(fragment) != 1:
                failures.append(
                    f"{module}: device placeholder must keep {fragment!r} exactly once"
                )
    elif entry.device_fragments:
        failures.append(
            f"{module}: device fragments {list(entry.device_fragments)!r} need a device test"
        )

    # retained factory:
                .{entry.create_method}({entry.create_args})
    # ... and the count message:
            f"{module}: {entry.create_method}({entry.create_args}) must be invoked once, inside "
            "the Store lambda"
~~~

The `FeatureEntry` docstring adds the new keys to its relative-path sentence and states the
empty-`device_test` rule.

**Proof that commit 1 changes no behaviour.** It runs on the committed tree, with the base's script
saved aside as `old_gate.py`, under both Pythons the host has:

1. GREEN: old and new outputs on the unmodified tree are byte-identical, exit code included.
2. Differential: under each mutation below, old and new outputs are byte-identical, and both RED
   with exactly the named line. Each mutation is applied, scored by both scripts, and restored
   byte-exactly before the next:

   | # | Path exercised | Mutation (one occurrence) | RED, from both scripts |
   | --- | --- | --- | --- |
   | M1 | `create_args` | all-exercises' `AllExercisesFeature.kt`: `.createAllExercisesGraph()` followed by a new line `.also { }` before `.allExercisesStore` | `feature:all-exercises: factory invocation must occur exactly inside retained Store creation` |
   | M2 | `device_test` | delete the `    @Regression` line of `AllExercisesScreenTest.kt` | `feature:all-exercises: device placeholder must keep '@Regression' exactly once` |
   | M3 | `preview_inventory` | in all-exercises' `ExerciseRow.kt`, a second annotation line `@Preview(name = "Extra", showBackground = true)` directly above `@Preview(name = "Dark", showBackground = true)` | `feature:all-exercises: exactly eight portable previews are required; found 9` |
   | M4 | `resource_wrapper_dates` | archive's `ArchivePagingHandler.kt`: `resourceWrapper.formatDayMonth(` → `resourceWrapper.formatMediumDate(` | `feature:archive: …ArchivePagingHandler.kt resolves archive copy through ResourceWrapper.formatMediumDate; use the private Compose resources` and `feature:archive: ResourceWrapper must serve exactly one formatDayMonth date` |

3. The diff of commit 1 touches no `ARCHIVE_ENTRY`, `ALL_TRAININGS_ENTRY`, `ALL_EXERCISES_ENTRY`,
   `MODULES`, contract function or `FEATURE_ENTRIES` line.

A differing byte in step 1 or 2 is a STOP.

### 12.1 Topology gate data (normative)

`MODULES["feature:past-session"]` holds the 70 files under `src` of Section 9.1. It follows the
all-exercises entry and uses its format:

- `kotlin_source_sets`: `commonMain`, `commonTest`, `androidHostTest`, `iosTest`;
- `resource_dirs`: `src/commonMain/composeResources/values` and
  `src/commonMain/composeResources/values-ru`.

`EXPECTED_APP_DEPS_READERS` loses the past-session entry. The script compares it exactly with every
`appDeps<…Factory>(` reader in the tree.

`PAST_SESSION_ENTRY` is appended to `FEATURE_ENTRIES` after `ALL_EXERCISES_ENTRY`. It uses the field
names of the merged schema plus Section 12.0's:

| Field | Value |
| --- | --- |
| module / short_name | `feature:past-session` / `past-session` |
| root / package_dir | `feature/past-session` / `io/github/stslex/workeeper/feature/past_session` |
| build_fragments | every plugin, `packageOfResClass`, `includeJavax()`, dependency and `apply(from = …)` line of Section 10, each exactly once (20), except `implementation(kotlin("test"))`, which kotlin_test_count covers |
| kotlin_test_count | 2 |
| catalog | the 23 rows of Section 3.4, in file order, with the exit values (decoded text; the RU separator is the Python literal `"\u00a0"`) |
| resource_prefix | `feature_past_session_` |
| payload_files | `PastSessionStore.kt`, `DialogState.kt`, `PastSessionUiModels.kt` (plus everything under domain/). The mapper and `CommonHandler` are deliberately absent: they resolve copy |
| store_file / state_fields | `mvi/store/PastSessionStore.kt` / `sessionUuid: String`, `phase: Phase`, `hasResolved: Boolean`, `expandedExerciseUuids: ImmutableSet<String>`, `dialogState: DialogState`, `bottomSheetState: BottomSheetState` |
| resource_wrapper_readers | `mvi/mapper/PastSessionUiMapper.kt`, `mvi/handler/CommonHandler.kt` |
| resource_wrapper_dates | `{"mvi/mapper/PastSessionUiMapper.kt": {"formatMediumDate": 1}}` |
| preview_subjects | empty |
| preview_inventory | the 15 arguments of Section 5.4, per file, in source order |
| suppressions | `{"src/commonMain/kotlin/io/github/stslex/workeeper/feature/past_session/di/PastSessionFeature.kt": ['"UNCHECKED_CAST"']}`; nothing under commonTest |
| test_names | the 40 of Section 3.2 under their commonTest paths; `weightedRowValuesFitTheirSlots` under `src/androidHostTest/…/gate/PastSetRowOverflowGateTest.kt`; the scene name under its iosTest path |
| golden_test / golden_methods | `src/androidHostTest/…/golden/PastSessionGoldenTest.kt` / the 15 of Section 3.3 |
| device_test / device_fragments | `""` / `()` |
| factory names | Section 0.3; create_args `screen`; feature_file `di/PastSessionFeature.kt`, graph_file `ui/PastSessionGraph.kt` |
| identity_test / identity_fragments | `app/app/src/test/…/di/PastSessionExtensionIdentityTest.kt` / the `pastSession(sessionUuid)` helper of Section 6, on one line |
| identity_names / identity_accessor | the four inherited names / `(?<!AppGraph)\.pastSession\("session-1"\)` (each identity calls it exactly once; the fourth's `session-2` call is not counted) |
| extra_root_fragments | none |
| extra_checks | `past_session_copy_contract`, `past_session_dependency_contract` below |

`past_session_copy_contract` checks D1 and S2 (Sections 5.3 and 5.4). Every count is over sources
with Kotlin comments stripped:

1. In `mvi/store/PastSessionStore.kt`, the parameters of `HapticClick` (by 7.9's
   `data_class_parameters`) are exactly `["type: HapticFeedbackType"]`, and those of `ShowError` are
   exactly `["errorType: ErrorType"]`. `data object DeletedSnackbar : Event` and
   `data object SaveFailedSnackbar : Event` each occur exactly once. In
   `mvi/model/PastSessionUiModels.kt`, `PastSessionUiModel`'s parameters are exactly
   `["trainingName: String", "isAdhoc: Boolean", "finishedAtAbsoluteLabel: String",
   "durationLabel: String", "totalsLabel: String", "exercises: ImmutableList<PastExerciseUiModel>"]`.
2. `mvi/mapper/PastSessionUiMapper.kt` holds each of these exactly once:
   - `suspend fun SessionDetailDomain.toUi(`;
   - `private suspend fun buildTotalsLabel(`;
   - `Res.string.feature_past_session_tonnage_group_separator`;
   - `private fun Long.groupThousands(separator: String)`;
   - `roundedTonnage.groupThousands(separator)`;
   - `THOUSANDS_GROUP_SIZE = 3`.

   It holds exactly five `stringResolver.getString(` and exactly two
   `stringResolver.getPluralString(`.
3. `mvi/handler/CommonHandler.kt` holds exactly once each of
   `private val resourceWrapper: ResourceWrapper,`, `private val stringResolver: StringResolver,`
   and `toUi(`, and no `getString(` or `getPluralString(`.
4. No commonMain file imports CMP's top-level functions: the pattern
   `^import org\.jetbrains\.compose\.resources\.(getString|getPluralString)\b` has no match.
5. Under `mvi/`, every `getString(` is `stringResolver.getString(` and every `getPluralString(` is
   `stringResolver.getPluralString(`: the counts are equal per file. No `mvi/` file contains
   `stringResource(` or `pluralStringResource(`.
6. No file under `ui/` contains `getString(` or `getPluralString(`.
7. Both walks, `mvi/` and `ui/`, find at least one file. An existing empty file is counted, not
   skipped (7.9's F1).

`past_session_dependency_contract` (D3) fails if the build file contains `:core:data:database`
anywhere, in any configuration, with the message
`feature:past-session: build must not depend on :core:data:database in any source set (D3)`.

The topology script's change after commit 1 is additive:

- `MODULES`, `EXPECTED_APP_DEPS_READERS`, `PAST_SESSION_ENTRY`, its two checks and the
  `FEATURE_ENTRIES` tuple;
- the only removed lines are the reader entry and the old `FEATURE_ENTRIES` line;
- `check_feature_entry` (as commit 1 left it), the three existing entries and their checks stay
  byte-identical.

`assert_kmp_ios_smoke.py` gains a `feature:past-session` entry, after all-exercises', with the 41
tuples of Section 11.4. The classnames are the suites' packages from Section 3.2. The scene's is
`io.github.stslex.workeeper.feature.past_session.PastSessionFeatureSceneIosTest`.

`android_build_unified.yml`, KMP iOS kit smoke job:

- add `:feature:past-session:iosSimulatorArm64Test` to the simulator test command and
  `:feature:past-session:linkDebugTestIosArm64` to the device-link command, after all-exercises;
- add the directory `feature/past-session` to the binary-assert loop (`/10 linked` → `/11 linked`)
  and `feature/past-session/build/test-results/iosSimulatorArm64Test/` to the upload list;
- the job-header comment names the past-session feature among the features, and the two "ten
  modules" comments say eleven.

The job, step and context names stay. The build job's comment "all 13 golden-holding modules" stays
true.

The eleven modules, for every command below:

1. `:core:ui:kit`
2. `:core:ui:navigation`
3. `:core:ui:mvi`
4. `:core:ui:start-mode`
5. `:core:ui:plan-editor`
6. `:feature:image-viewer`
7. `:feature:plan-editor`
8. `:feature:archive`
9. `:feature:all-trainings`
10. `:feature:all-exercises`
11. `:feature:past-session`

### 12.2 Entry gate before any edit

7.10 Section 12.2 applies, plus:

- `733377255` (Phase 7.10's merge) is an ancestor of the base;
- `git diff 733377255..<base> -- feature/past-session` is empty;
- every commit since `733377255` that touches a Section 9.2 path is listed, with whether it
  conflicts;
- the census of Sections 3.1–3.7 is reproduced on the base;
- the current ten-module Native command and oracle are green.

Baseline commands, with the stable flags:

    ./gradlew :feature:past-session:assembleDebug
    ./gradlew :feature:past-session:testDebugUnitTest      # 7 suites, 41 cases; goldens excluded
    ./gradlew :feature:past-session:verifyPaparazziDebug   # 8 suites, 71 cases: 30 golden + 41
    ./gradlew :app:app:testDebugUnitTest --tests '*PastSessionExtensionIdentityTest*' --tests '*StringResolverIdentityTest*'   # 6 cases

### 12.3 Focused gates after the implementation

After commit 2, on the committed tree:

    python3 .github/scripts/assert_kmp_ui_source_topology.py
    ./gradlew :feature:past-session:assembleDebug            # Android and both iOS klibs
    ./gradlew :feature:past-session:testAndroidHostTest      # 7 suites, 41 cases
    ./gradlew :feature:past-session:verifyPaparazziDebug     # 30 golden + 41
    ./gradlew :feature:past-session:iosSimulatorArm64Test    # 41 tuples
    ./gradlew :app:common:assembleDebug

Then:

- `./gradlew detekt --continue` alone, on every commit's tree;
- the four `PastSessionExtensionIdentityTest` identities and the two `StringResolverIdentityTest`
  identities (fresh XML);
- **the prepared-catalog check of Section 5.2.** It reads the build output of the `assembleDebug`
  above: every `strings.*.cvr` under `feature/past-session/build/` whose directory is `values` or
  `values-ru`. There is at least one of each, and every copy is checked. Each holds exactly one
  `string|feature_past_session_tonnage_group_separator|…` record. Its Base64 payload decodes to
  exactly `2C` under `values` and `C2 A0` under `values-ru`;
- on an explicit API-34 serial with animations disabled, as `ui_tests.yml` does: the whole
  `RouteReachabilityTest` class, which includes
  `pastSessionOpensFromASeededFinishedSessionAndHomeReturns` (fresh XML);
- the relative PNG proof of Section 3.7.

Run `:feature:past-session:iosSimulatorArm64Test` as soon as production and commonTest compile for
Native, before the scene and the app paths. That run is D3's first Native measurement (Section 3.5)
and must give the 40 identities by name.

After commit 3: the eleven-module Native command (`--continue`) and the oracle, the eleven-module
device link (11/11), the topology script, and detekt alone.

### 12.4 Repository and visual gates

7.10 Section 12.4 applies unchanged:

- assemble, lint and unit;
- detekt alone;
- `assembleDebugAndroidTest`;
- `verifyPaparazziDebug`;
- `:lint-rules:test`;
- the personal-data gate;
- the canonical Smoke and Regression device suites;
- diff hygiene;
- the PNG manifest check of Section 3.7;
- the mockup shell gate, where runnable.

### 12.5 Final diff and remote proof

7.10 Section 12.5 applies, with:

- the Section 9 boundary;
- 40 + 1 Native tuples;
- 30 unchanged golden blobs;
- the one production suppression.

Record the KMP iOS kit smoke job's duration on the final head against its 120-minute timeout.

## 13. Mandatory known-negative controls

7.10 Section 13's procedure applies:

- every control runs fresh GREEN → one named observable RED → automatic exact restoration → fresh
  GREEN;
- Gradle- and device-scored controls run through `documentation/mockups/mutation_harness.py` in
  one-shot mode, with `--no-configuration-cache --console=plain` inside `--task`;
- Python-scored controls run against a byte-restoring copy in a detached worktree under
  `.claude/worktrees/`;
- a compile-invalid mutation is never a RED where a compiled test is the detector;
- no mutation is committed.

Every control runs after commit 3, on its committed tree.

**From 7.10, with the substitutions of Section 0.3:**

| # | Mutation | Scorer: expected RED |
| ---: | --- | --- |
| 1 | delete `ui/components/PastSessionSheets.kt` | topology: source manifest mismatch, `missing=[…PastSessionSheets.kt]` |
| 2 | copy `di/PastSessionScope.kt` into `src/main/kotlin/…/di/` | topology: legacy source set `src/main`, manifest `extra=[…]`, unexpected Kotlin-bearing source set |
| 3 | move `ui/components/PastSessionSheets.kt` to `ui/PastSessionSheets.kt` | topology: manifest `missing` and `extra` |
| 4 | `import android.util.Log` in `ui/components/PastSessionSheets.kt` | topology: platform API in commonMain, `'import android.'` |
| 5 | rename the EN key `feature_past_session_action_retry` to `…_renamed` | topology: EN catalog mismatch (`order=drifted`), two ownership-drift lines |
| 6 | append a word to the RU value of `feature_past_session_action_retry` | topology: RU catalog mismatch, `order=exact` |
| 7 | move the EN `feature_past_session_action_retry` into `feature/settings/src/main/res/values/strings.xml` | topology: resource ownership drift, and the EN catalog mismatch |
| 8 | `val titleRes: Int = 0` as State's last field | topology: Store State must stay semantic |
| 9 | the mapper's `adhoc_label` read → `resourceWrapper.getString(0)` | topology: `resolves past-session copy through ResourceWrapper.getString`; copy contract `'stringResolver.getString(' exactly five times; found 4`, and the `mvi/` rule `5 time(s), 4 through stringResolver` |
| 10 | `val label = getString(TITLE)` as the first line of `CommonHandler`'s `updateStateImmediate { current -> … }` | topology: `resolves a resource inside the State lambda: 'getString('`; copy contract on `CommonHandler` |
| 11 | `uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES` added to `PastSessionHeader.kt`'s `@Preview(name = "Dark")` | topology: forbidden `'android.content'` and `'uiMode'`; preview inventory mismatch |
| 12 | `LocalContext.current` and `context.appDeps<PastSessionGraph.Factory>()` in `PastSessionFeature` | topology: forbidden `LocalContext` and `appDeps<`, readers not the exact 7, factory invocation not inside retained creation |
| 13 | delete `val pastSessionGraphFactory: PastSessionGraph.Factory` and its KDoc from `AppRootDeps` | topology: exact root-factory flow requires the accessor |
| 14 | `val graph = factory.createPastSessionGraph(screen)` before `rememberMetroStoreProcessor` | topology: `createPastSessionGraph(screen) must be invoked once, inside the Store lambda` |
| 15 | a second `(context.applicationContext as AppRootDepsHolder).appRootDeps()` inside the admitted `remember(currentPhase.id) { … }` in `App.kt` | device: `UiAdmissionRaceTest`, as in 7.10 |
| 16 | hoist `val deps = remember(currentPhase.id) { …appRootDeps() }` above `if (admission.granted) {` | device: `UiAdmissionRaceTest`, as in 7.10 |
| 17 | identity helper `= asContribution<PastSessionGraph.Factory>()` | topology: `must not bypass AppRootDeps via asContribution`, and the identity-helper fragment |
| 18 | archive-specific; no counterpart | — |
| 19 | `withExpansionCarriedFrom`: `exercises.firstOrNull()` → `exercises.lastOrNull()` | host and Native: `first Loaded state seeds exactly the first card open` and `Init seeds the first card open on the first Loaded emission` |
| 20 | `SUMMARY_TIMES = "×"` → `"x"` | host and Native: `mapper builds the collapsed summary per exercise type` and its siblings; the scene's T1 summaries |
| 21 | the scene's `setContent { AppTheme { PastSessionScreen(…) } }` body → `setContent { }` | Native: the scene, at its first node assertion |
| 22 | production `PastSessionScreen.kt`: the back button's `onClick = { consume(Action.Click.OnBackClick) }` → `onClick = { }` | Native: the scene's dispatch list |
| 23 | rename the scene method to `renamedSceneSubstitute` | Gradle GREEN, then the oracle: `feature:past-session: expected exactly one testcase … found 0` |
| 24 | flip (XOR 0xFF) the last byte of `…golden_PastSessionGoldenTest_header_light.png` | PNG assertion: blob drift, blob-set drift, the all-PNG and Paparazzi manifest drift |
| 25 | a compile-valid overlong assertion line in `commonTest/…/mvi/handler/NavigationHandlerTest.kt` | root detekt, alone: findings on that line only |

**New in 7.11:**

| # | Mutation | Scorer: expected RED |
| ---: | --- | --- |
| 26 | `THOUSANDS_GROUP_SIZE = 3` → `4` | Native: the scene's step 2 (`4,820` → `4820`, `1,000` → `1000`, `1,234,567` → `123,4567`); topology: copy contract `'THOUSANDS_GROUP_SIZE = 3' … found 0`. Record also that host stays GREEN: every host fixture is under 1,000 |
| 27 | the separator read → `val separator = ","` | topology: copy contract `'stringResolver.getString(' exactly five times; found 4` and the separator resource `found 0`. Record also that host, Native and the scene stay GREEN, because the EN catalog holds the same comma. That is why the count exists |
| 28 | RU separator `&#160;` → a plain space | topology: RU catalog mismatch on `feature_past_session_tonnage_group_separator` |
| 29 | EN `tonnage_format` `%1$s kg` → `%,d kg` | placeholder rule: key `feature_past_session_tonnage_format`, token `'%,d'`; EN catalog mismatch |
| 30 | `data class ShowError(val errorType: ErrorType)` → `(val message: String)` | topology: copy contract, `ShowError parameters must be exactly ['errorType: ErrorType']` |
| 31 | the mapper imports CMP's top-level `getString` and calls it for `adhoc_label` | topology: copy contract on the import, the five-call count and the `mvi/` rule |
| 32 | ui/PastSessionGraph.kt shows `getString(Res.string.feature_past_session_deleted_snackbar)` instead of `deletedMessage` | topology: `reads copy outside composition under ui/: 'getString('` |
| 33 | `resourceWrapper.formatMediumDate(0L)` added to `CommonHandler.processInit` | topology: `CommonHandler.kt resolves past-session copy through ResourceWrapper.formatMediumDate` (no allowance there) |
| 34 | the mapper's date → `finishedAt.toString()` | topology: `must read the ResourceWrapper.formatMediumDate date exactly 1 time(s); found 0` |
| 35 | `@Preview(name = "Skipped — Collapsed")` → `@Preview(name = "Skipped")` | topology: `PastExerciseCard.kt preview inventory must be exactly […]` |
| 36 | add `src/androidDeviceTest/kotlin/…/PastSessionScreenTest.kt` (a bare class) | topology: manifest `extra=[…]` and an unexpected Kotlin-bearing source set `src/androidDeviceTest` |
| 37 | add `implementation(project(":core:data:database"))` to commonTest's dependencies | topology: `build must not depend on :core:data:database in any source set (D3)` |
| 38 | `.createPastSessionGraph(screen)` → `.createPastSessionGraph(Screen.PastSession(sessionUuid = screen.sessionUuid))` | topology: `factory invocation must occur exactly inside retained Store creation` |
| 39 | in `PastSessionExtensionIdentityTest`, the third identity's `appGraph.pastSession("session-1")` → `("session-3")` | topology: `all four extension identities must reach the graph through the accessor` |
| 40 | a 41st `@Test` in `NavigationHandlerTest`, `` `a forty-first identity nobody declared` `` | topology: test identities drifted, `unexpected=['a forty-first identity nobody declared']` |
| 41 | the argument rule: the mapper's `exercises_count` read drops its format argument (`getPluralString(…, exerciseCount)`) | host and Native: the 13 resolver-dependent identities, which throw through the strict fake or reach `LoadFailed`; the scene's totals show `%1$d exercises` |

Each Python-scored control was dry-run once against a simulated exit tree while this document was
written, and each produced exactly its named RED, without a traceback. That covers 1–14, 17, the
topology half of 26, 27–40, and M1–M4 of Section 12.0. That dry run is not evidence. The
implementation's fresh runs are.

Scorers:

- **Python, the topology script as CI runs it:** 1–14, 17, 27–40, and 26's topology half;
- **PNG:** 24;
- **Gradle:**
  - `:feature:past-session:testAndroidHostTest :feature:past-session:iosSimulatorArm64Test --continue`
    for 19–22, 26, 41, and 27's GREEN observation;
  - the eleven-module Native command and the oracle for 23;
  - root detekt, alone, for 25;
- **device:** 15 and 16 (`UiAdmissionRaceTest` on the recorded serial, fresh XML).

## 14. STOP conditions

7.10 Section 14 applies, read with Section 0.3. In addition, stop if:

- `733377255` is not an ancestor of the base, or the target or any Section 9.2 path drifted from
  the discovery baseline in a way this document does not describe;
- commit 1's proof (Section 12.0) shows any differing byte;
- D1 needs any of these:
  - a new production file;
  - a resource handle, `ResourceWrapper` or `StringResolver` in a payload file;
  - resource resolution inside a State lambda;
  - a change to an Event, the State or `PastSessionUiModel`;
- the prepared catalog's separator records decode to anything but U+002C and U+00A0
  (Section 12.3);
- any of the 13 resolver-dependent identities needs an assertion change, or a strict fake cannot
  reproduce today's stubs;
- the Native scene's EN catalog differs from Section 3.4 in any value, or step 2's totals differ
  from its table;
- the `SetText` action is unavailable on the simulator (S5). Report the exception; never drop the
  entries silently;
- Native compilation of commonTest needs `core:data:database` (D3, Section 3.5);
- `Icons.Filled.DragHandle` does not resolve from `libs.cmp.material.icons.extended` on Native;
- the KMP iOS kit smoke job times out on any PR head;
- any golden needs recording, or any PNG blob changes;
- detekt reports anything in the moved tests that PR-L does not cover. The fix is never a
  suppression;
- `RouteReachabilityTest` fails on the recorded serial.

A STOP produces a measured note and, where needed, an amendment proposal. It never widens the
implementation silently.

## 15. Signed, bisect-green commit plan

Four English Conventional Commits. Each is signed, GitHub-verified and green on its own tree:

1. **ci(kmp): generalize the shared feature-entry gate** — D2 only (Section 12.0): the four
   additive fields and their branches, and nothing else.
2. **refactor(kmp): share past-session feature entry** — the module (Sections 3–11) and the six app
   paths. It also carries the minimal topology delta that keeps this tree green (precedent 7.8 G1):
   the `MODULES` entry, and the past-session reader removed from `EXPECTED_APP_DEPS_READERS`.
3. **ci(kmp): gate shared past-session feature** — `PAST_SESSION_ENTRY` with its two checks, the
   Native oracle entry, and the workflow lists and comments.
4. **docs(kmp): record Phase 7.11 evidence** — three things:
   - this document's Status line, which becomes
     `**Status:** COMPLETE — implemented by the PR whose merge brought this line to dev.`;
   - Section 19;
   - the documentation edits of Section 9.2.

   Sections 0–18 stay byte-identical.

Each commit is made first. Its gates then run on the committed tree, and so do the controls (after
commit 3) and the repository gates. A fix is a new signed commit inside Section 9, and every gate it
can reach is re-run. Nothing is amended, squashed or reordered.

## 16. Exit criteria

7.10 Section 16 applies, with:

- commit 1's byte-identical proof;
- the 71-path topology and two private catalogs;
- 23 identifiers, changed only by the 12 P1 values and D1's two;
- copy outside composition only through `StringResolver`, and `ResourceWrapper` only for the one
  date (the copy contract and the date allowance);
- the prepared separator records U+002C and U+00A0;
- 40 identities on host and Native, plus the overflow gate on host, and 41 Native tuples;
- 30 golden cases with unchanged blobs and the relative PNG manifest proof;
- one production suppression and no other;
- the explicit shape-B factory flow, with all four identities through the accessor;
- no `core:data:database` edge;
- the topology entry, oracle and workflow at eleven modules;
- readers 8 → 7;
- every control of Section 13 evidenced;
- the Section 9.2 documents updated.

## 17. Remaining ordered roadmap

After 7.11 the frontier is remeasured. By today's sizes the next entries are home, exercise-chart,
settings, single-training, exercise and live-workout. Then come the app-dialog boundary, app/common
and the permanent iOS host. A throwaway iosApp remains forbidden.

These are separate follow-ups:

- moving the files of tech-debt.md's 🟢 resolver row onto `StringResolver`;
- the locale-list remedy, which is a maintainer decision;
- a portable date formatter, which would retire archive's and past-session's `ResourceWrapper`
  reads;
- `core:data:exercise`'s leaky `PlanSetDataModel`.

## 18. Authorization

The maintainer's GO of 2026-10-16 locked D1–D3. Starting the implementation with this document
accepts S1–S5 and authorizes two things:

- landing this document on dev;
- implementing it as one PR into dev, inside Section 9.

Merge stays the maintainer's.

## 19. Implementation evidence

Reserved for the implementation's documentation commit.
