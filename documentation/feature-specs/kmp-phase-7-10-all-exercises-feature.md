# KMP Phase 7.10 — feature:all-exercises becomes a shared feature entry

**Status:** SPECIFIED — authorized for implementation by the maintainer's GO of 2026-10-12 (Section 18)

**Discovery baseline:** dev @ 16c41a0cdb56ccc19b2b27227dc63bb53effc701 (2026-10-12, merge of #329, PR-R).
`feature/all-exercises` is unchanged since the Phase 7.9 census except PR-N's renames (`b0045adf`).

**Template:** [Phase 7.9](kmp-phase-7-9-all-trainings-feature.md). This document is a delta. Every rule
of 7.9 that this document does not change applies unchanged, read with the substitutions of
Section 0.3; through 7.9, the rules of 7.8 and 7.7 it inherits apply too.

**First consumer of:** [`StringResolver`](kmp-string-resolver.md) (PR-R, merged in `16c41a0c`).

---

## 0. Authority, decisions, substitutions

### 0.1 Authority order

1. live origin/dev at the implementation base;
2. PR-R as merged (`kmp-string-resolver.md`, `core/ui/kit/.../strings/`);
3. AGENTS.md, CLAUDE.md, documentation/architecture.md, testing.md, ci-cd.md,
   compose-state-discipline.md;
4. the Phase 7.9 specification, and through it 7.8 (including its Section 19 decisions P1, P1a and
   G1–G4) and 7.7 (the portable BackHandler);
5. the checked-in source, resources, tests, PNGs and CI oracles at the implementation base.

### 0.2 Stable rules

7.9 Section 0.2 applies: the positive-gate flags
`--rerun-tasks --no-build-cache --no-configuration-cache --console=plain` after `./gradlew --stop`,
quoted `N actionable tasks: N executed`, only JUnit XML written after the run's recorded start as
evidence, detekt and tests in separate invocations, signed and GitHub-verified commits with
`Co-Authored-By` as the only trailer, no session identifier in a commit, the PR or a document, no
merge by the implementer. The required contexts are whatever the ruleset lists on the final head;
record them.

Suppressions: **no new suppression of any kind.** The two inherited production suppressions of
Section 3.1 stay byte-semantically unchanged. The one inherited test suppression,
`@Suppress("MagicNumber")` on `AllExercisesUiMapperTest`, is removed in the port (Section 11.1): it
is inert today (detekt's `MagicNumber` excludes every test source set, `lint-rules/detekt.yml`
lines 485–488), and the generic FeatureEntry suppression check accepts no commonTest suppression
other than the file-scoped Native test-name annotation. Phase 7.10 needs no Native test-name
suppression.

### 0.3 Substitutions against 7.9

| 7.9 | 7.10 |
| --- | --- |
| `feature:all-trainings`, `feature/all-trainings` | `feature:all-exercises`, `feature/all-exercises` |
| package `io.github.stslex.workeeper.feature.all_trainings` | `io.github.stslex.workeeper.feature.all_exercises` |
| resource package `…feature.all_trainings.resources` | `io.github.stslex.workeeper.feature.all_exercises.resources` |
| `AllTrainingsFeature`, `AllTrainingsGraph`, `allTrainingsGraph` | `AllExercisesFeature`, `AllExercisesGraph`, `allExercisesGraph` |
| `allTrainingsGraphFactory`, `createAllTrainingsGraph`, `allTrainingsStore` | `allExercisesGraphFactory`, `createAllExercisesGraph`, `allExercisesStore` |
| `AllTrainingsStoreImpl`, `AllTrainingsExtensionIdentityTest` | `AllExercisesStoreImpl`, `AllExercisesExtensionIdentityTest` |
| route `Screen.BottomBar.AllTrainings`, graph tag `AllTrainingsGraph` | `Screen.BottomBar.AllExercises`, `AllExercisesGraph` |
| `ALL_TRAININGS_ENTRY` | `ALL_EXERCISES_ENTRY` |
| D1 (semantic event, copy in the graph) and D2 (mapper through CMP's top-level functions) | D1 of Section 0.4 (copy through the injected `StringResolver`) |
| nine-module Native command | ten modules (Section 12.1) |

### 0.4 Decisions locked by the maintainer (2026-10-12)

| Id | Decision |
| --- | --- |
| D1 | Copy outside composition resolves through the injected `StringResolver`. `AllExercisesUiMapper`'s functions become `suspend` and take it; `ClickHandler` and `PagingHandler` inject it instead of `ResourceWrapper`. Events keep `message: String` and `BlockedArchiveDialog` stays pre-formatted. Strict in-file fakes return today's stub strings, so the 14 resolver-dependent identities keep their assertions (Sections 5.2, 11.1). |
| D2 | The real plural delegation of `ComposeStringResolver` (PR-R's R5, an exit obligation of this phase) first executes in this phase's iOS scene, in EN, through the real resolver. Android host has no route; that is recorded as a limitation (Section 11.5). |
| D3 | The `@Regression` device identity reads its confirm label through `ComposeStringResolver` (Section 11.3). |
| D4 | The `.claude/skills/` files stay untouched; the follow-up documentation PR owns them (Section 8). |

Inherited and unchanged: P1 (bare `%d` in Android `res` becomes positional), P1a (the placeholder
rule), D3–D5 of 7.9 (PR-N, PR-G and PR-L are merged and cover this module).

## 1. Bounded exit claim

The implementation may prove only this:

- feature:all-exercises applies `convention.kmpComposeLibrary` and retains Metro and Paparazzi;
- all 34 production Kotlin files compile from commonMain for Android, iosSimulatorArm64 and
  iosArm64;
- the exact private 39-identifier EN/RU catalog lives in commonMain Compose Resources, changed only
  by the 48 positional placeholders of decision P1 (Section 5.1);
- the paging contract and every visible Android behaviour are unchanged, except the inherited
  Compose-resources locale property (Section 5.6);
- `ResourceWrapper` leaves the feature entirely; every string read outside composition goes through
  the injected `StringResolver`, none through CMP's top-level `getString` / `getPluralString`
  (Section 5.2);
- 10 portable suites with 66 exact identities execute on Android host and on Native;
- the golden suite executes as 52 Android-host Paparazzi cases (26 methods × light/dark) against
  the same PNG blobs;
- the two inherited device identities stay: the documented skip, and the `@Regression`
  blocked-archive identity, which reads its label through the real resolver (Section 11.3);
- one deterministic iOS production scene proves the whole catalog through the real
  `ComposeStringResolver` (the first execution of its plural delegation), the mapper and
  blocked-archive copy through the production mapper, every list surface, selection, the three
  dialogs and action dispatch;
- the Metro graph is reached through the explicit generation-owned factory, never `LocalContext`
  or `Context.appDeps`; both extension identities use the accessor;
- the topology gate checks the entry through one `FeatureEntry`; `Context.appDeps` readers drop
  from 9 to 8;
- Android remains releasable with every repository PNG blob unchanged.

This is a source-set, dependency, resource, test and composition-root migration. It is not
permission to redesign the screen, change selection, filtering, archive or paging behaviour, revive
or remove the permanent-delete path (Section 5.7), change `core:ui:kit`, add an iOS app, or migrate
another feature.

## 2. Candidate selection — fresh census

Measured at the discovery baseline with 7.9's method (production = `src/main/kotlin`, unit =
`src/test/kotlin`, EN string keys = `<string` elements of `values/strings.xml`, PNGs under
`src/test/snapshots`). The figures equal 7.9's table row for row.

| Feature | Prod files | Prod lines | Unit files | Unit lines | EN string keys | PNGs |
| --- | ---: | ---: | ---: | ---: | ---: | ---: |
| **all-exercises** | 34 | **2,062** | 11 | **1,471** | 30 | 52 |
| past-session | 29 | 2,378 | 8 | 2,067 | 20 | 30 |
| home | 39 | 3,085 | 15 | 1,924 | 35 | 42 |
| exercise-chart | 41 | 3,214 | 10 | 2,314 | 31 | 30 |
| settings | 51 | 3,326 | 12 | 2,638 | 65 | 12 |
| single-training | 39 | 3,635 | 6 | 1,582 | 39 | 12 |
| exercise | 46 | 4,141 | 13 | 2,890 | 53 | 48 |
| live-workout | 59 | 6,666 | 27 | 7,611 | 96 | 60 |

All-exercises is smallest in production lines and unit lines. Past-session has fewer files, unit
files and keys but is 1.15× larger in production lines and 1.4× in unit lines. Every module
all-exercises depends on (core:core, core:ui:kit, core:ui:mvi, core:ui:navigation,
core:data:exercise) is already KMP, and every symbol it imports from them is declared in
commonMain, except `core.ui.mvi.di.appDeps` (androidMain), which leaves with the factory flow
(Section 6). It shares all-trainings' list-surface, selection and paging-tail structure.

## 3. Measured baseline

Static measurements at the discovery baseline. Test names are given as PR-N left them.

### 3.1 Production manifest

All 34 files move from `src/main/kotlin/io/github/stslex/workeeper/feature/all_exercises/` to
`src/commonMain/kotlin/io/github/stslex/workeeper/feature/all_exercises/` with unchanged relative
paths:

| File | Lines |
| --- | ---: |
| di/AllExercisesFeature.kt | 34 |
| di/AllExercisesGraph.kt | 37 |
| di/AllExercisesHandlerStore.kt | 9 |
| di/AllExercisesHandlerStoreImpl.kt | 14 |
| di/AllExercisesScope.kt | 8 |
| domain/AllExercisesInteractor.kt | 45 |
| domain/AllExercisesInteractorImpl.kt | 108 |
| domain/mapper/AllExercisesDomainMapper.kt | 53 |
| domain/model/ArchiveResult.kt | 9 |
| domain/model/BulkArchiveResult.kt | 18 |
| domain/model/ExerciseDomain.kt | 13 |
| domain/model/ExerciseListItemDomain.kt | 14 |
| domain/model/ExerciseTypeDomain.kt | 7 |
| domain/model/TagDomain.kt | 7 |
| mvi/handler/ClickHandler.kt | 204 |
| mvi/handler/NavigationHandler.kt | 23 |
| mvi/handler/PagingHandler.kt | 63 |
| mvi/mapper/AllExercisesUiMapper.kt | 166 |
| mvi/model/ExerciseTypeUiModel.kt | 7 |
| mvi/model/ExerciseUiModel.kt | 18 |
| mvi/model/TagUiModel.kt | 10 |
| mvi/store/AllExercisesStore.kt | 144 |
| mvi/store/AllExercisesStoreImpl.kt | 54 |
| ui/AllExercisesGraph.kt | 42 |
| ui/AllExercisesScreen.kt | 377 |
| ui/components/ArrivedEmptyStates.kt | 116 |
| ui/components/ExerciseRow.kt | 180 |
| ui/components/ExercisesEmptyState.kt | 42 |
| ui/components/ListSurface.kt | 61 |
| ui/components/PagingTailKind.kt | 17 |
| ui/components/PagingTails.kt | 45 |
| ui/components/TagFilterRow.kt | 80 |
| ui/components/TopBarMode.kt | 16 |
| ui/components/TrailingSlotKind.kt | 21 |
| **34 files** | **2,062** |

The two inherited production suppressions, unchanged by this phase:

- `@Suppress("UNCHECKED_CAST")` — di/AllExercisesFeature.kt (the processor cast);
- `@Suppress("TooManyFunctions")` — mvi/handler/ClickHandler.kt.

Android coupling in production:

- `R.*` in seven files: five UI files through Android `stringResource` / `pluralStringResource`
  (`ui/AllExercisesScreen.kt`, `ui/components/ArrivedEmptyStates.kt`, `ExerciseRow.kt`,
  `ExercisesEmptyState.kt`, `PagingTails.kt`), and `mvi/mapper/AllExercisesUiMapper.kt` and
  `mvi/handler/ClickHandler.kt` through `ResourceWrapper`;
- `ResourceWrapper` itself in three files: the mapper (10 calls: 3 `getString`, 7
  `getQuantityString`), `ClickHandler` (2 calls: 1 `getString`, 1 `getQuantityString`) and
  `PagingHandler`, which only passes it to the mapper;
- `System.currentTimeMillis()` as the `nowMillis` default of the mapper's `toUi` and
  `composeFooterLabel`;
- `androidx.activity.compose.BackHandler` in ui/AllExercisesGraph.kt;
- `LocalContext` with `appDeps` in di/AllExercisesFeature.kt;
- `@VisibleForTesting` on `AllExercisesStoreImpl.NAME`;
- `uiMode` in four previews.

### 3.2 Portable manifest — 10 suites, 66 identities

Each file moves from `src/test/kotlin/…/feature/all_exercises/` to
`src/commonTest/kotlin/…/feature/all_exercises/` with its relative path.

**domain/AllExercisesInteractorImplTest.kt — 5**

- archiveExercise returns Success when no active trainings
- archiveExercise returns Blocked when active trainings exist
- restoreExercise delegates to repository
- canPermanentlyDelete delegates to repository
- permanentlyDelete delegates to repository

**mvi/handler/ClickHandlerTest.kt — 24**

- OnExerciseClick emits haptic and navigates to OpenDetail
- OnFabClick emits haptic and navigates to OpenCreate
- OnTagFilterToggle adds tag when not selected
- OnTagFilterToggle removes tag when already selected
- OnCancelPermanentDelete clears pending delete
- OnConfirmPermanentDelete with no pending is no-op
- OnConfirmPermanentDelete with pending clears state and emits Confirm haptic
- OnBulkDelete fires no haptic — the morph is silent
- OnTagFilterToggle fires no haptic
- OnBulkDeleteConfirm emits Confirm haptic
- OnBulkDelete with selection opens confirm dialog
- OnBulkDeleteConfirm calls bulkArchive and clears selection on success
- OnBulkDeleteConfirm opens blocked dialog and no snackbar when all blocked
- OnBulkDeleteConfirm opens blocked dialog with archived summary on partial block
- OnBlockedArchiveDismiss clears the blocked dialog
- OnBulkDeleteDismiss clears pending
- OnClearTagFilter empties the whole filter in one act
- OnClearTagFilter fires no haptic
- OnClearTagFilter on an already-empty filter changes nothing
- entering selection by long press fires LongPress
- long press inside selection fires ContextClick not a second LongPress
- selection toggle fires ContextClick
- selection exit fires ContextClick
- OnEmptyCreate opens create and fires no haptic

**mvi/handler/NavigationHandlerTest.kt — 2**

- OpenDetail navigates to Screen Exercise with uuid
- OpenCreate navigates to Screen Exercise with null uuid

**mvi/handler/PagingHandlerTest.kt — 1**

- Init invokes observeAvailableTags via scope launch

**mvi/mapper/AllExercisesUiMapperTest.kt — 11**

- composeFooterLabel hides every segment when all values are zero or null
- composeFooterLabel renders all three segments when present
- composeFooterLabel hides session segment when count is zero
- composeFooterLabel hides linked-trainings segment when count is zero
- composeFooterLabel hides last-trained segment when value is null
- composeFooterLabel just-now segment when delta below one minute
- composeFooterLabel minute granularity for sub-hour deltas
- ExerciseListItemDomain toUi propagates non-zero stats into the model
- ExerciseListItemDomain toUi yields empty footer when stats are absent
- toBlockedArchiveDialog truncates trainings and sets archived summary on partial block
- toBlockedArchiveDialog omits archived summary and truncation when nothing archived

**ui/AllExercisesClearanceTest.kt — 2**

- list bottom clearance is the drawn 88 not the 72 it shipped with
- each drawn part is the value the mockup gives it

**ui/components/ListSurfaceTest.kt — 9**

- rows win over everything
- an unsettled refresh with no rows is loading not empty
- loading outranks selection and the filter
- a failed first page is its own verdict
- no rows nothing done is the first-run empty
- a filter that matches nothing is its own state not the first-run empty
- selection outranks the filter because the selection block carries the filter recovery
- the crossfade covers the drawn blocks and neither non-block verdict
- selection empty and filtered empty are both in the crossfade so the pair transits

**ui/components/PagingTailKindTest.kt — 4**

- appending draws the loading footer
- a failed page draws the error footer not silence
- exhausted draws no footer at all
- idle mid-list draws no footer either

**ui/components/TopBarModeTest.kt — 4**

- off is the resting bar
- on is the selection bar
- different selections are one mode so the count cannot drive the crossfade
- an empty selection is still the selection bar

**ui/components/TrailingSlotKindTest.kt — 4**

- at rest the slot promises a destination
- an unselected row in selection mode draws nothing and keeps its slot
- a selected row draws the check
- selected outranks selecting so the mark never blanks

Suite distribution: 5/24/2/1/11/2/9/4/4/4. MockK is used by the first five suites; JUnit 5 by all
ten. No test imports `paging-testing`, although the build declares it (Section 3.5).

One ClickHandlerTest identity contains U+2014 (`—`). 7.9 measured that the Native XML reports such
names byte-exactly (7.9 Section 19), so this is a re-check, not a first.

Fourteen identities run through copy resolution: the 11 mapper identities, and the three
bulk-confirm identities of ClickHandlerTest (`calls bulkArchive and clears selection on success`,
`opens blocked dialog and no snackbar when all blocked`, `opens blocked dialog with archived summary
on partial block`). PagingHandlerTest constructs the handler with the dependency and never reaches
it. Today the mapper suite stubs `ResourceWrapper` (a strict MockK mock) with strings built from
the quantity, never from a plural's format arguments (`"$count sessions"`); only the
`used_in_format` stub reads its argument. ClickHandlerTest uses a relaxed mock, which returns `""`.

### 3.3 Golden and device ownership

`golden/AllExercisesGoldenTest.kt` has 26 methods parameterized over `GoldenTheme`, in this order:

    rowWeighted, rowWeightless, rowTypeIsolated, rowLongName, rowClamped, rowSelected,
    rowUnselectedInSelection, tagFilterBand, emptyState, pagingLoading, pagingError,
    confirmDialogContent, blockedArchiveDialogContent, screenList, screenSelection,
    screenFirstRunEmpty, filteredEmpty, selectionEmptyFiltered, selectionEmptyUnfiltered,
    coldOpenLoading, coldOpenError, screenColdOpen, screenRefreshError, screenFilteredEmpty,
    screenSelectionEmpty, screenSelectionEmptyUnfiltered

That is 52 render cases and 52 PNGs named
`io.github.stslex.workeeper.feature.all_exercises.golden_AllExercisesGoldenTest_<method>_<dark|light>.png`,
52 distinct blobs, all mode 100644. Fixtures build `ExerciseUiModel`, `BlockedArchiveItem` and the
dialog contents directly with fixed strings, so neither the mapper nor `StringResolver` reaches a
golden. The harness renders in EN. The screen goldens render `selected_count` (selection bar) and the
row strings (`type_weighted`, `type_weightless`, `footer_separator`), so the P1 change is
pixel-checked for `selected_count`; the other eight plurals reach no golden and are asserted by the
Native scene (Section 11.4).

`AllExercisesScreenTest` (`@Smoke` on the class) holds two identities:

- `pendingFeatureRewrite`, the documented skip, annotated exactly
  `@Ignore("Awaiting feature rewrite — see GH issue #93 for coverage scope.")`;
- `blockedArchiveDialogListsBlockingTrainings`: `@Regression`. It renders `AllExercisesScreen` with a
  pre-formatted `BlockedArchiveDialog`, asserts the item's name and label, reads the confirm label as
  `targetContext.getString(R.string.feature_all_exercises_blocked_archive_confirm)`, clicks it and
  asserts the one captured `OnBlockedArchiveDismiss`. After the move that `R` no longer exists
  (D3, Section 11.3).

### 3.4 Exact feature-local resource catalog

Each locale owns 30 strings and 9 plurals, 39 identifiers in this file order. Values are shown as
they must be at exit, as the topology gate compares them (decoded XML text): decision P1 replaces
the bare `%d` of 48 plural items (EN 16, RU 32) with `%1$d` and changes nothing else.
`blocked_archive_more` and the two placeholder strings are already positional.

| # | Identifier | EN | RU |
| ---: | --- | --- | --- |
| 1 | feature_all_exercises_title | Exercises | Упражнения |
| 2 | feature_all_exercises_empty_headline | Your exercises will appear here | Здесь появятся твои упражнения |
| 3 | feature_all_exercises_empty_supporting | Add the first one — after that you can put it into any training. | Добавь первое — дальше его можно будет класть в любую тренировку. |
| 4 | feature_all_exercises_empty_create | Add exercise | Добавить упражнение |
| 5 | feature_all_exercises_fab_create | Create exercise | Создать упражнение |
| 6 | feature_all_exercises_type_weighted | with weight | с весом |
| 7 | feature_all_exercises_type_weightless | bodyweight | без веса |
| 8 | feature_all_exercises_paging_loading | Loading | Загружаю |
| 9 | feature_all_exercises_paging_error | Couldn’t load more | Не удалось загрузить дальше |
| 10 | feature_all_exercises_paging_retry | Retry | Повторить |
| 11 | feature_all_exercises_permanent_delete_title | Delete ‘%1$s’ permanently? | Удалить «%1$s» навсегда? |
| 12 | feature_all_exercises_permanent_delete_body | This exercise has no history and isn’t used. This cannot be undone. | Упражнение не использовалось и не имеет истории. Это действие необратимо. |
| 13 | feature_all_exercises_permanent_delete_impact | No history affected | История не пострадает |
| 14 | feature_all_exercises_permanent_delete_confirm | Delete | Удалить |
| 15 | feature_all_exercises_permanent_delete_success | Exercise deleted | Упражнение удалено |
| 16 | feature_all_exercises_session_count | plural, below | plural, below |
| 17 | feature_all_exercises_linked_trainings_count | plural, below | plural, below |
| 18 | feature_all_exercises_last_trained_just_now | last just now | только что |
| 19 | feature_all_exercises_last_trained_minutes | plural, below | plural, below |
| 20 | feature_all_exercises_last_trained_hours | plural, below | plural, below |
| 21 | feature_all_exercises_last_trained_days | plural, below | plural, below |
| 22 | feature_all_exercises_footer_separator | · | · |
| 23 | feature_all_exercises_selection_close | Close selection | Закрыть выбор |
| 24 | feature_all_exercises_selected_count | plural, below | plural, below |
| 25 | feature_all_exercises_bulk_archive | Archive | В архив |
| 26 | feature_all_exercises_bulk_archive_success | plural, below | plural, below |
| 27 | feature_all_exercises_bulk_archive_confirm_title | Archive selected? | Архивировать выбранные? |
| 28 | feature_all_exercises_bulk_archive_confirm_body | plural, below | plural, below |
| 29 | feature_all_exercises_bulk_archive_impact | Reversible · history preserved | Обратимо · история сохранится |
| 30 | feature_all_exercises_blocked_archive_title | Some couldn’t be archived | Не удалось архивировать |
| 31 | feature_all_exercises_blocked_archive_used_in_format | used in %1$s | в тренировках: %1$s |
| 32 | feature_all_exercises_blocked_archive_more | plural, below | plural, below |
| 33 | feature_all_exercises_blocked_archive_next_step | These are still in active trainings. Remove them from those trainings first, then archive. | Они используются в активных тренировках. Сначала уберите их оттуда, затем архивируйте. |
| 34 | feature_all_exercises_blocked_archive_confirm | Got it | Понятно |
| 35 | feature_all_exercises_filtered_empty_headline | Nothing matches these tags | По этим тегам ничего нет |
| 36 | feature_all_exercises_filtered_empty_clear | Clear filter | Сбросить фильтр |
| 37 | feature_all_exercises_selection_empty_headline | Nothing here, but your selection is kept | Здесь пусто, но выбор цел |
| 38 | feature_all_exercises_selection_empty_supporting | Your selection stays until you leave selection mode. | Отметки останутся, пока не выйдешь из режима. |
| 39 | feature_all_exercises_refresh_error | Couldn’t load the list | Не удалось загрузить список |

| Plural | EN one / other | RU one / few / many / other |
| --- | --- | --- |
| session_count | `%1$d session` / `%1$d sessions` | `%1$d сессия` / `%1$d сессии` / `%1$d сессий` / `%1$d сессии` |
| linked_trainings_count | `in %1$d training` / `in %1$d trainings` | `в %1$d тренировке` / `в %1$d тренировках` / `в %1$d тренировках` / `в %1$d тренировках` |
| last_trained_minutes | `last %1$dm ago` / `last %1$dm ago` | `%1$d мин назад` / `%1$d мин назад` / `%1$d мин назад` / `%1$d мин назад` |
| last_trained_hours | `last %1$dh ago` / `last %1$dh ago` | `%1$d ч назад` / `%1$d ч назад` / `%1$d ч назад` / `%1$d ч назад` |
| last_trained_days | `last %1$dd ago` / `last %1$dd ago` | `%1$d д назад` / `%1$d д назад` / `%1$d д назад` / `%1$d д назад` |
| selected_count | `%1$d selected` / `%1$d selected` | `выбрано %1$d` / `выбрано %1$d` / `выбрано %1$d` / `выбрано %1$d` |
| bulk_archive_success | `%1$d exercise archived` / `%1$d exercises archived` | `%1$d упражнение в архиве` / `%1$d упражнения в архиве` / `%1$d упражнений в архиве` / `%1$d упражнения в архиве` |
| bulk_archive_confirm_body | `%1$d exercise will move to archive. Restore from Settings → Archive.` / `%1$d exercises will move to archive. Restore from Settings → Archive.` | `%1$d упражнение будет перенесено в архив. Восстановить можно в Настройках → Архив.` / `%1$d упражнения будут перенесены в архив. Восстановить можно в Настройках → Архив.` / `%1$d упражнений будут перенесены в архив. Восстановить можно в Настройках → Архив.` / `%1$d упражнения будут перенесены в архив. Восстановить можно в Настройках → Архив.` |
| blocked_archive_more | `+%1$d more` / `+%1$d more` | `и ещё %1$d` / `и ещё %1$d` / `и ещё %1$d` / `и ещё %1$d` |

(Plural identifiers carry the `feature_all_exercises_` prefix.)

The XML moves byte-semantically except P1. That includes:

- the XML comments of both files;
- both roots' `xmlns:tools` / `tools:ignore="DuplicateStrings"`, inert in Compose resources as 7.9
  Section 3.4 records;
- the numeric character references `&#8216;` and `&#8217;` of three EN strings
  (`permanent_delete_title`, `permanent_delete_body`, `blocked_archive_title`), shown decoded
  above (‘ ’).

No Compose catalog in the repository carries a numeric character reference yet; kit's only entity
is `&amp;`. The parse 7.9 Section 3.4 records is a DOM parse, which decodes character references,
so the values should equal the decoded text above. That is **unverified at discovery**: the Native
scene's exact-EN catalog check proves it (Section 11.4), and any difference is a STOP.

No path outside the module consumes these identifiers; the generated `Res` stays private. All 39
identifiers are read by production code: 29 in composition and 11 outside it, the mapper's 10 and
ClickHandler's 2 with `bulk_archive_success` in both. `footer_separator` is read in both places.

### 3.5 Declared dependencies

7.9 Section 3.5 applies row for row: the same five project edges, `paging-testing` removed (no
test imports it), `kotlin("test")` in commonTest and iosTest, the golden harness as
`androidHostTestImplementation`, the device bundle with the Compose BOM as
`androidDeviceTestImplementation`, and the same visibility edges for the same reasons —
`api(libs.cmp.ui)` (Modifier; `HapticFeedbackType` in `Event.Haptic`),
`api(libs.androidx.paging.common)` and `api(libs.coroutines.core)` (`PagingData` and `Flow` in the
public interactor and State), `api(libs.kotlinx.collections.immutable)` (State and UI models),
`implementation(libs.androidx.compose.paging)` (`LazyPagingItems`), `implementation(libs.cmp.animation)`
(`AnimatedContent` in the screen and the row), `implementation(libs.cmp.material.icons.core)`
(`Icons.Default.Close` only). commonTest adds `libs.coroutine.test` (`runTest`); iosTest adds
`libs.cmp.ui.test`. `StringResolver` needs no new edge: core:ui:kit is already `api`. No catalog or
version change.

### 3.6 External consumers and root identity

| Surface | Ownership |
| --- | --- |
| Module edges | app/app `implementation` (stays); app/common `implementation` → `api` |
| Navigation registration | app/common `AppNavigationHost` calls `allExercisesGraph`, tag `AllExercisesGraph` |
| Graph aggregation | app/app `AppGraph` (gains the accessor) |
| Extension identities | app/app test `AllExercisesExtensionIdentityTest`: 2 identities, through `asContribution` today |
| Direct composition | none: no app test composes `allExercisesGraph()` directly |
| Device journeys by tag | `ApplicationBottomBarTest`, `BackStackStateRestorationTest`, `NavigationResultTest`, `RouteReachabilityTest`, `StoreRetentionTest` (through `harness/NavPaths.kt`) |
| Route | `Screen.BottomBar.AllExercises`, core:ui:navigation commonMain, unchanged |

### 3.7 Context readers and PNG integrity

Nine executable `Context.appDeps` readers exist (`EXPECTED_APP_DEPS_READERS`): eight navigation
entries and app-dialogs. Removing all-exercises' leaves eight.

PNG manifests at the discovery baseline (7.9's method, reproduced: it gives 7.9's projected values
on 7.9's merge tree). Paparazzi PNGs are the tracked PNGs under `*/src/test/snapshots/images/` and
`*/src/androidHostTest/snapshots/images/`. A mode/blob/path hash is
`git ls-files -s -- <files> | LC_ALL=C sort -k4 | shasum -a 256`; a path list hash is
`git ls-files -- <files> | LC_ALL=C sort | shasum -a 256`; the blob set hash is the sorted blob IDs,
one per line.

| Manifest | Entries | Hash |
| --- | ---: | --- |
| Paparazzi mode/blob/path | 456, 13 owners | 46f2f70f052aeaa500cfaee6834b343a5f21f717a0d684c0ead5a54767d833d5 |
| Paparazzi path list | 456 | 64859f12035c03fab1d27c83c9aa12abd88286e9fec0dd5ef060e05572a65f2c |
| All repository PNGs, mode/blob/path | 528 | 8f4705371b62353ed92a33af735cddaba926992c24ca718fc5cd491c77b40513 |
| all-exercises blob set | 52 | 4e71615ea8aacb223a77b5bc996147f23ca899c6f575f679060ec324f3fbe432 |

If only the 52 paths move to `src/androidHostTest/snapshots/images/`, the projections are
f8792e1efeca575a6b1bfa6bf780c89b52a0475392e3f9fea3f04f90d598443b (Paparazzi mode/blob/path),
27b831bcc04b22f872dc2221abd310c3e33211f9f4163c42f0226822a4d6d44d (path list) and
ce590ad5c3a859541f8249cb95ea33e33104b5ba326699b323289b15c2fb3f36 (all PNGs).

**The normative check is relative, as in 7.9:** at implementation entry, take the base manifests,
rewrite exactly the 52 all-exercises paths from `src/test/` to `src/androidHostTest/`, and require
the head manifests to equal the rewritten base byte for byte; the blob-set hash must be unchanged.

### 3.8 Measurement limitation

Discovery was static: no Gradle invocation, simulator or device. The implementation records fresh
entry gates (Section 12) and stops if the environment for any of them is missing.

## 4. Platform/API blockers — settled decisions

### 4.1 Paging remains common

7.9 Section 4.1 applies. All-exercises' verdicts are the same, in the same order:

- `listSurface`: rows → CONTENT; refresh Loading → LOADING; refresh Error → REFRESH_ERROR;
  selecting → SELECTION_EMPTY; filter active → FILTERED_EMPTY; otherwise FIRST_RUN;
- `crossfades`: true for REFRESH_ERROR, FIRST_RUN, FILTERED_EMPTY, SELECTION_EMPTY only;
- `pagingTailKind`: append Loading → LOADING, Error → ERROR (with retry), NotLoading → NONE.

### 4.2 Android resources become private Compose resources

7.9 Section 4.2 applies with the package of Section 0.3. No `StringResource`,
`PluralStringResource`, Android id, `ResourceWrapper` or `StringResolver` enters State, Action,
Event, UI models, domain models or repository APIs.

### 4.3 Android APIs leave common

7.9 Section 4.3 applies, row for row:

| Today | At exit |
| --- | --- |
| `LocalContext` + `context.appDeps<…>()` | explicit factory (Section 6) |
| `androidx.activity.compose.BackHandler` | `androidx.compose.ui.backhandler.BackHandler`, `@OptIn(ExperimentalComposeUiApi::class)` (Section 5.5) |
| `System.currentTimeMillis()` | `Clock.System.now().toEpochMilliseconds()` (`kotlin.time`) |
| `androidx.annotation.VisibleForTesting` | removed; `NAME` stays private |
| `uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES` | portable `ThemeMode` previews (Section 5.4) |
| `R`, `androidx.compose.ui.res.*` | generated `Res`, `org.jetbrains.compose.resources.*` in composition |
| `ResourceWrapper` | `StringResolver` (D1, Section 5.2) |

### 4.4 Native test names

None. Every identity of Section 3.2 is Native-legal; PR-N already ran over this module. No
`INVALID_CHARACTERS_NATIVE_ERROR` suppression is authorized.

## 5. Resource, State, copy and preview architecture

### 5.1 Private generated resource owner

    src/main/res/values/strings.xml    -> src/commonMain/composeResources/values/strings.xml
    src/main/res/values-ru/strings.xml -> src/commonMain/composeResources/values-ru/strings.xml

Byte-semantic moves, except decision P1 on the 48 plural items of Section 3.4. Every composable
call keeps its current shape, for example
`pluralStringResource(Res.plurals.feature_all_exercises_selected_count, count, count)`.

### 5.2 D1 — copy outside composition through `StringResolver`

`AllExercisesUiMapper` keeps its `internal object` and file. Its three entry points become:

    suspend fun ExerciseListItemDomain.toUi(
        stringResolver: StringResolver,
        nowMillis: Long = Clock.System.now().toEpochMilliseconds(),
    ): ExerciseUiModel

    suspend fun composeFooterLabel(
        stringResolver: StringResolver,
        sessionCount: Int,
        linkedTrainingsCount: Int,
        lastTrainedAt: Long?,
        nowMillis: Long = Clock.System.now().toEpochMilliseconds(),
    ): String

    suspend fun BulkArchiveResult.toBlockedArchiveDialog(
        stringResolver: StringResolver,
    ): AllExercisesStore.State.BlockedArchiveDialog

The three private helpers (`lastTrainedRelativeLabel`, `toBlockedArchiveItem`, `formatTrainings`)
become `suspend` and take `stringResolver` instead of `resourceWrapper`. `ExerciseTypeDomain.toUi()`
and `TagDomain.toUi()` are unchanged.

Every read keeps its resource, quantity and arguments:

- each `resourceWrapper.getString(R.string.x, args…)` becomes
  `stringResolver.getString(Res.string.x, args…)`;
- each `resourceWrapper.getQuantityString(R.plurals.x, count, count)` becomes
  `stringResolver.getPluralString(Res.plurals.x, count, count)`.

The mapper then holds exactly 3 `stringResolver.getString(` and 7 `stringResolver.getPluralString(`
calls. The branches and thresholds are unchanged: just now under a minute, else minutes, else hours,
else days, from `(now − t).coerceAtLeast(0)`. So are the separator (`" $separator "`) and the
truncation: at most `MAX_TRAININGS_SHOWN = 2` names joined by `", "`, then `"$shown $overflow"`.

The argument rule is load-bearing. `StringResolver` returns the catalog text verbatim when a call
passes no format arguments (kmp-string-resolver.md R2; PR-R's O1 showed the vararg path differs).
So a plural call that drops its count would ship the raw template, for example "%1$d sessions".
The strict fakes (Section 11.1) and control 36 exist for this.

`PagingHandler` takes `private val stringResolver: StringResolver,` in place of `resourceWrapper`.
It calls `item.toUi(stringResolver = stringResolver)` inside the existing `pagingData.map { … }`,
whose transform is `suspend` (paging 3.5.0).

Threading does not change, for the reason 7.9 Section 5.2 records. The per-item transform runs where
today's `ResourceWrapper` call runs: on collection, on the main thread. CMP's first catalog read in
a process is a blocking asset read, and later reads are cached. Do not move the work in this phase.

`ClickHandler` takes `private val stringResolver: StringResolver,` in place of `resourceWrapper`
(constructor order: `interactor`, `stringResolver`, `store`):

- `Event.ShowPermanentDeleteSuccess(message = stringResolver.getString(Res.string.feature_all_exercises_permanent_delete_success))`
  is sent inside the existing `launch { … }`, after `permanentlyDelete`;
- `Event.ShowBulkDeleteSuccess(message = stringResolver.getPluralString(Res.plurals.feature_all_exercises_bulk_archive_success, result.archivedCount, result.archivedCount))`
  is sent inside the existing `suspend` `onSuccess`;
- `result.toBlockedArchiveDialog(stringResolver)` is built inside the same `onSuccess`, before
  `updateStateImmediate`. Mapping happens before the State lambda, as today.

The events and the State keep their shape: `ShowPermanentDeleteSuccess(message: String)`,
`ShowBulkDeleteSuccess(message: String)` and
`BlockedArchiveDialog(archivedSummary: String?, items: ImmutableList<BlockedArchiveItem>)`.
ui/AllExercisesGraph.kt shows `event.message` through `SnackbarManager`, as today.

This module follows tech-debt.md's "Shared (KMP) string resources" rule, so it adds no debt row and
no `TODO(tech-debt)` marker. all-trainings keeps 7.9's semantic event and graph copy; that row's 🟢
entry already records the difference, so the two patterns coexist until that follow-up.

### 5.3 Composable copy

The five UI files of Section 3.1 resolve copy with `org.jetbrains.compose.resources.stringResource`
/ `pluralStringResource` on the private `Res`, in the same places and with the same arguments.

### 5.4 Previews

The four preview subjects keep their sample content and become eight portable previews, by 7.9
Section 5.5's rule (one `<Subject>LightPreview`, one `<Subject>DarkPreview`, one private
`<Subject>Preview(themeMode)` wrapping today's body in `AppTheme(themeMode = themeMode)`):

| File | Subject |
| --- | --- |
| ui/components/TagFilterRow.kt | TagFilterRow |
| ui/components/ExerciseRow.kt | ExerciseRow |
| ui/components/ArrivedEmptyStates.kt | ArrivedEmptyStates |
| ui/components/ExercisesEmptyState.kt | ExercisesEmptyState |

Eight `@Preview(` in total. No preview is deleted.

### 5.5 Back

    @OptIn(ExperimentalSharedTransitionApi::class, ExperimentalComposeUiApi::class)
    …
    BackHandler(enabled = processor.state.value.interceptBack) {
        processor.consume(Action.Click.OnSelectionExit)
    }

The enablement (`interceptBack` = selection on) and the action are unchanged. 7.9 Section 4.3's
note on the portable BackHandler's Android actual applies.

### 5.6 Inherited locale property of Compose resources

7.9 Section 5.7 applies, and from this phase it also covers all-exercises' copy. A device whose
locale list is [uk-UA, ru-RU] shows this screen in Russian today and in English after the move.
A locale with non-Latin default digits shows Latin digits in counts and relative times.
`StringResolver` has the same property (its KDoc and tech-debt.md's 🟡 row). The remedy is the
maintainer's open decision, not part of this phase.

### 5.7 The unreachable permanent-delete path

No production code sets `pendingPermanentDelete` to a non-null value. At the baseline the only
writes are `= null` in `ClickHandler` and `State.init`. So the permanent-delete dialog and
`ShowPermanentDeleteSuccess` are unreachable from the UI.

These parts migrate unchanged:

- the State field and the two actions;
- the dialog and the event;
- the interactor's `canPermanentlyDelete` / `permanentlyDelete`;
- the five `permanent_delete_*` keys.

Removing or reviving the path is outside this phase. The Native scene renders the dialog from State
(Section 11.4); that is the only place its copy is asserted. Its title is the only composable string
with a placeholder (`%1$s`), and its title and body are two of the three strings that carry character
references.

## 6. Explicit generation-owned shape-A factory flow

7.9 Section 6 applies with the substitutions of Section 0.3:

- `AppRootDeps` gains `val allExercisesGraphFactory: AllExercisesGraph.Factory`, after
  `allTrainingsGraphFactory`, with the same KDoc shape;
- `AppGraph` overrides it;
- `App.kt` passes `allExercisesGraphFactory = deps.allExercisesGraphFactory`;
- `AppNavigationHost` takes a required `allExercisesGraphFactory: AllExercisesGraph.Factory` and
  calls `allExercisesGraph(factory = allExercisesGraphFactory, modifier = …)`;
- `allExercisesGraph` takes a required factory and builds `AllExercisesFeature(factory)`;
- `AllExercisesFeature` becomes an `internal class` with `private val factory: AllExercisesGraph.Factory`;
- `createAllExercisesGraph()` runs once, inside the retained `rememberMetroStoreProcessor` lambda;
- app/common's edge becomes `api`.

`LocalContext`, `appDeps`, `asContribution`, nullable fallbacks, registries and pre-admission
resolution are forbidden.

`AllExercisesExtensionIdentityTest` gains
`private fun AppGraph.allExercises(): AllExercisesGraph = allExercisesGraphFactory.createAllExercisesGraph()`.
Both identities reach the store through `.allExercises()`, the `asContribution` import goes, and the
two names and assertions stay exact.

7.9's seventh app path (the DbVisibility device test) has no counterpart here. No app test composes
`allExercisesGraph()` directly, so the app boundary is six paths (Section 9.2).

## 7. In scope

Only these:

- the conversion of Sections 4–6;
- the moves of Sections 3.1–3.4 and the P1 catalog change;
- D1 and D3, and the removal of `@VisibleForTesting`;
- the portable BackHandler and previews;
- porting the ten suites to kotlin.test with deterministic in-file fakes, removing the inert test
  suppression;
- the golden move and the device move;
- one iosTest scene;
- the six app paths, the three CI paths and the five documentation paths;
- one signed, green PR that the maintainer merges.

## 8. Explicit non-goals

7.9 Section 8 applies. In addition:

- no change to `core:ui:kit`; `StringResolver` is consumed as merged;
- no move of archive, all-trainings or the other files of tech-debt.md's 🟢 resolver row onto the
  resolver;
- no edit to `.claude/skills/` (D4). The follow-up documentation PR owns the skills that teach
  `R` / `ResourceWrapper` copy (add-feature, compose-state-discipline, mvi-dialog-state,
  refactor-with-mvi-rules) and write-handler-test's stale all-exercises line;
- no change to the inherited `updateStateImmediate { … tags.map { it.toTagUi() } … }` mapping in
  `PagingHandler` (tech-debt.md row 456);
- no removal or revival of the permanent-delete path (Section 5.7);
- no new test identity beyond the scene, no rename of any Section 3.2 identity, and no strengthened
  assertion;
- no change to `graphify-out/`.

## 9. Exact allowed change boundary

### 9.1 Target module — 102 tracked paths at exit

- `build.gradle.kts`;
- 34 production files under `src/commonMain/kotlin/io/github/stslex/workeeper/feature/all_exercises/`
  (Section 3.1);
- `src/commonMain/composeResources/values/strings.xml` and `values-ru/strings.xml`;
- 10 suites under `src/commonTest/kotlin/io/github/stslex/workeeper/feature/all_exercises/`
  (Section 3.2 paths);
- `src/androidHostTest/kotlin/io/github/stslex/workeeper/feature/all_exercises/golden/AllExercisesGoldenTest.kt`;
- 52 PNGs under `src/androidHostTest/snapshots/images/` (Section 3.3 names);
- `src/androidDeviceTest/kotlin/io/github/stslex/workeeper/feature/all_exercises/AllExercisesScreenTest.kt`;
- `src/iosTest/kotlin/io/github/stslex/workeeper/feature/all_exercises/AllExercisesFeatureSceneIosTest.kt`.

Nothing remains under `src/main`, `src/test` or `src/androidTest`. There is no androidMain,
iosMain, shared test helper, fixture or extra catalog.

### 9.2 Root, CI and documentation — 14 paths

App (6):

    app/app/src/main/java/io/github/stslex/workeeper/di/AppGraph.kt
    app/app/src/test/kotlin/io/github/stslex/workeeper/di/AllExercisesExtensionIdentityTest.kt
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
    documentation/feature-specs/kmp-phase-7-10-all-exercises-feature.md

What changes in each document:

- **architecture.md and ci-cd.md:** the places 7.9's commit `3a941f1f` updated for all-trainings.
  Find them with `git show 3a941f1f -- documentation/`. All-exercises then appears wherever
  all-trainings does as a shared entry (the factory-resolution exception, the Context-seam
  paragraph with its reader count 9 → 8, the KMP iOS kit smoke section). Every module count there
  reads ten, and the all-exercises validator is described (66 portable tuples plus the scene,
  67 exact).
- **testing.md:**
  - a "Shared all-exercises feature" subsection after "Shared all-trainings feature". It covers the
    66 identities, the strict resolver fakes, the scene and the D2 limitation.
  - "Shared string resolver" (lines 472–473 at the baseline) says the plural delegation first runs
    on Native in this module's scene, with Android host having no route. The current sentence,
    "its first execution is Phase 7.10's mapper identities", is inaccurate, because those
    identities use a fake. The paragraph also keeps the fake rule.
  - the `AppGraph.stringResolver` bullet (lines 316–319) says the accessor still has no production
    reader, and that all-exercises' handlers receive the binding by injection through their graph
    extension.
  - the device-test table row for `feature/all-exercises` (line 952) names the one existing file
    under `src/androidDeviceTest`. The two other files it lists do not exist.
- **tech-debt.md:** the two links into old all-exercises paths (rows at lines 456 and 539 at the
  baseline) are re-pointed to the new paths, with correct line references at exit.

Known stale references after the move, deliberately untouched. They are historical records, already
stale before this phase, or owned by the follow-up documentation PR:

- `documentation/features.md` lines 79, 92 and 97–99;
- `.claude/skills/*` (D4);
- `documentation/kmp-migration-assessment.md`, `documentation/research/*` and the historical
  feature specs (`exercises.md`, `all-exercises-delta.md`, `archive-delta.md`, `exercise-image.md`,
  `trainings.md`, `v2.*`, `wear-lifecycle-ui.md`, `kmp-phase-5-evidence/*`);
- `kmp-string-resolver.md`'s R5 sentence (a COMPLETE specification; testing.md carries the
  correction);
- tech-debt.md's "Resolved" list (line 797);
- `graphify-out/`.

A need for any other path is a STOP.

## 10. Gradle and public API contract

7.9 Section 10 applies with the substitutions of Section 0.3:

~~~kotlin
plugins {
    alias(libs.plugins.convention.kmpComposeLibrary)
    alias(libs.plugins.metro)
    alias(libs.plugins.paparazzi)
}

compose.resources {
    packageOfResClass = "io.github.stslex.workeeper.feature.all_exercises.resources"
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
            api(project(":core:ui:kit"))
            api(project(":core:ui:mvi"))
            api(project(":core:ui:navigation"))
            implementation(project(":core:data:exercise"))

            api(libs.cmp.ui)
            api(libs.androidx.paging.common)
            api(libs.coroutines.core)
            api(libs.kotlinx.collections.immutable)
            implementation(libs.androidx.compose.paging)
            implementation(libs.cmp.animation)
            implementation(libs.cmp.material.icons.core)
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
    "androidDeviceTestImplementation"(platform(libs.androidx.compose.bom))
    "androidDeviceTestImplementation"(libs.androidx.compose.ui.test.manifest)
    "androidDeviceTestImplementation"(project(":core:ui:test-utils"))
}

apply(from = "$rootDir/gradle/golden-gate.gradle.kts")
~~~

The comments that carry the build's history move with it. 7.9's build-file comments (the two `api`
rationale comments, the Compose-BOM and test-utils GUARD comments) may be carried over, adapted. No
comment may hold a dependency fragment or a forbidden build token. Verify Gradle metadata rather
than copy blindly (7.8 Section 10). A dependency the measured source does not need is removed; one
it needs is a STOP-and-amend, never a silent addition.

## 11. Test and Native scene contract

### 11.1 Portable suites

The ten files move to commonTest. JUnit 5 and MockK give way to `kotlin.test`,
`kotlinx-coroutines-test` and deterministic in-file fakes and spies: named private classes, no
shared helper file. Android host and Native each execute exactly the 66 identities of Section 3.2,
with zero failure, error or skip, distribution 5/24/2/1/11/2/9/4/4/4.

7.9 Section 11.1 and the fake rules its Section 19.7 records apply:

- the handler-store fake applies `updateState` / `updateStateImmediate` to a `MutableStateFlow`;
- it records `sendEvent`, `consume` and each `launch` without running it;
- a launch is recorded as a closure built inside the generic `launch<T>`, so no cast or suppression
  is needed;
- members no identity reaches fail fast.

Two differences from 7.9:

- **PagingHandlerTest reaches `Flow<T>.launch`.** `Init` collects `observeAvailableTags()` through
  it, so this suite's store fake records that call without running it. It does not throw.
- **ClickHandlerTest's launch identities.** The three bulk-confirm identities run their recorded
  launch's action and then its `onSuccess`, in today's order. `OnConfirmPermanentDelete with pending
  clears state and emits Confirm haptic` records its launch and never runs it, as the relaxed mock
  did.

**StringResolver fakes (D1).** `AllExercisesUiMapperTest` and `ClickHandlerTest` each declare their
own private fake. PagingHandlerTest passes one that fails fast on every call.

A fake answers by switching on the generated resource objects (`Res.string.…`, `Res.plurals.…`), as
testing.md → "Shared string resolver" prescribes, and throws on any other resource. Its arguments
are checked, not ignored. A read throws unless it passes exactly what its production call passes:

- no format arguments for `footer_separator`, `last_trained_just_now` and
  `permanent_delete_success`;
- exactly one for `blocked_archive_used_in_format`;
- `formatArgs == [quantity]` for every plural.

The return values reproduce today's stubs exactly:

| Resource | Mapper-suite fake | ClickHandler-suite fake |
| --- | --- | --- |
| `footer_separator` | `·` | throws (not reached) |
| `last_trained_just_now` | `last just now` | throws |
| `blocked_archive_used_in_format` (argument `a`) | `used in a` | `""` |
| `session_count` (quantity `q`) | `q sessions` | throws |
| `linked_trainings_count` | `in q trainings` | throws |
| `last_trained_minutes` | `last qm ago` | throws |
| `last_trained_hours` | `last qh ago` | throws |
| `last_trained_days` | `last qd ago` | throws |
| `bulk_archive_success` | `q archived` | `""` |
| `blocked_archive_more` | `+q more` | `""` |
| `permanent_delete_success` | throws | `""` |

The mapper-suite values are the strings `AllExercisesUiMapperTest` stubs today, including the
quantity-blind `1 sessions`. The ClickHandler-suite `""` is the relaxed mock's default.

**Mechanics only:**

- the mapper's functions are `suspend`, so each mapper identity becomes `= runTest { … }`, keeping
  its name, inputs and assertions;
- `@Suppress("MagicNumber")` leaves `AllExercisesUiMapperTest` (Section 0.2).

No other assertion changes.

The domain suite's repository fakes implement `ExerciseRepository` (33 members) and `TagRepository`
(5). Only the members the five identities reach behave; every other member fails fast. They import
data shapes directly, never by qualified name: PR-L's test-source-set exemption is what keeps them
out of the production-domain check.

### 11.2 Android host goldens

7.9 Section 11.2 applies. `AllExercisesGoldenTest` moves to androidHostTest with only the runner
mechanics the KMP harness needs. The 26 method names, light/dark cases, subjects, fixtures and
52 filenames stay exact. PNGs move with `git mv`, and `verifyPaparazziDebug` executes all 52 cases.
`recordPaparazziDebug` is forbidden.

### 11.3 Android device compatibility (D3)

`AllExercisesScreenTest` moves to androidDeviceTest. `pendingFeatureRewrite` stays the one
documented skip. `blockedArchiveDialogListsBlockingTrainings` changes exactly its label read:

    val confirmLabel = runBlocking {
        ComposeStringResolver().getString(Res.string.feature_all_exercises_blocked_archive_confirm)
    }

This replaces `InstrumentationRegistry.getInstrumentation().targetContext.getString(R.string.…)`
at the same place. The imports follow: `ComposeStringResolver`, the module's `Res` and its accessor,
and `kotlinx.coroutines.runBlocking` come in, and `InstrumentationRegistry` and the feature `R` go.
Everything else stays byte-semantic: the annotations, the State, the assertions and the captured
action.

This is unverified at discovery, for two reasons:

- no device test in the repository reads a Compose resource itself;
- no library device test has rendered a shared feature's Compose resources. 7.8's and 7.9's device
  files are placeholders, and the app journeys render archive's and all-trainings' copy from the
  app APK, not from a library test APK.

The first run on the recorded serial is the proof. If the screen's resources or the resolver read
fail on the device, STOP with the exception. Never fall back to a hardcoded label or Android `R`.

The app journeys of Section 3.6 stay green.

### 11.4 Deterministic iOS production scene

Exactly one class and method, no comma:

    class AllExercisesFeatureSceneIosTest
    fun resourcesPagingBranchesSelectionAndActionsRenderAndDispatch()

The scene's setup:

- it uses `runComposeUiTest`, the production `AllExercisesScreen` inside `AppTheme`,
  deterministic settled `PagingData`, a fixed `NOW`, and the bounded settle stepping of
  all-trainings' scene (`settleScene`, `settleUntil(matcher)`, `settleUntilTag` in
  `AllTrainingsFeatureSceneIosTest.kt`; 7.9 Section 19.7);
- every read outside composition goes through one `ComposeStringResolver()` instance (`resolver`).
  The scene declares no fake resolver.

In one bounded scene it proves:

1. **Catalog (D2):** through `resolver`, all 30 strings and both EN quantities (1 → one, 2 → other)
   of all 9 plurals resolve to the exact EN values of Section 3.4. Each is read twice: as stored,
   with no format arguments, which gives the verbatim template; and filled, with arguments — the
   count for plurals, `"Bench"` for `permanent_delete_title`, `"Push, Pull"` for
   `blocked_archive_used_in_format`. This is the first execution of `getPluralString`'s
   delegation anywhere.
2. **Mapper copy (D1):** through the production `toUi(stringResolver = resolver, nowMillis = NOW)`,
   five rows give exactly these footers:

   | sessionCount | linkedTrainingsCount | lastTrainedAt | footerLabel |
   | ---: | ---: | --- | --- |
   | 12 | 3 | NOW − 4 d − 3 h | `12 sessions · in 3 trainings · last 4d ago` |
   | 1 | 1 | NOW − 30 s | `1 session · in 1 training · last just now` |
   | 0 | 2 | NOW − 2 h − 15 min | `in 2 trainings · last 2h ago` |
   | 5 | 0 | NOW − 15 min − 20 s | `5 sessions · last 15m ago` |
   | 0 | 0 | null | `` (empty) |

3. **Blocked-archive copy (D1):** through the production `toBlockedArchiveDialog(resolver)`:
   - `archivedCount = 1`, one exercise `Bench` in `Push, Pull, Legs, Upper` gives
     `1 exercise archived` and `used in Push, Pull +2 more`;
   - `archivedCount = 3`, `Row` in `Legs` gives `3 exercises archived` and `used in Legs`;
   - `archivedCount = 0`, `Bench` in `Push, Legs` gives summary `null` and `used in Push, Legs`.
4. **Surfaces:**
   - cold-open loading, held across three further settles (`AllExercisesColdOpen`);
   - refresh error with its reason and Retry (`AllExercisesColdOpenError`);
   - the first-run empty state with its headline, supporting text and `Add exercise`
     (`AllExercisesEmptyState`);
   - filtered empty with `Clear filter` (`AllExercisesFilteredEmpty`);
   - selection empty with and without `Clear filter` (`AllExercisesSelectionEmpty`);
   - populated rows built from step 2's models, with name and meta line, for example
     `with weight · 12 sessions · in 3 trainings · last 4d ago · Push · Chest`;
   - the append loading and append error tails (`AllExercisesPagingLoading`,
     `AllExercisesPagingError`, `AllExercisesPagingRetry`).
5. **Bars:**
   - the resting title `Exercises` (under `AllExercisesTopBar`);
   - the FAB description `Create exercise` at rest;
   - in selection, `2 selected`, `Close selection`, and `Archive` asserted on the production tags
     `AllExercisesFab` and `AllExercisesSelectionTopBarArchive`, because two nodes carry it.
6. **The three dialogs:**
   - bulk-archive confirm, from `PendingBulkDelete(count = 2)`: `Archive selected?`,
     `2 exercises will move to archive. Restore from Settings → Archive.`,
     `Reversible · history preserved`, `Archive`;
   - blocked archive, from step 3's first model: `Some couldn’t be archived`, `1 exercise archived`,
     `Bench`, `used in Push, Pull +2 more`, the next-step text, `Got it`;
   - permanent delete, from `PendingDelete(uuid, name = "Bench")`: `Delete ‘Bench’ permanently?`,
     `This exercise has no history and isn’t used. This cannot be undone.`,
     `No history affected`, `Delete`.

   The ‘ and ’ characters in these texts are the catalog's decoded character references
   (Section 3.4).
7. **Dispatch:** one exact list, in the scene's own order, containing these 14 entries:

   | Interaction | Action |
   | --- | --- |
   | row click | `OnExerciseClick` |
   | row long press | `OnExerciseLongPress` |
   | tag chip | `OnTagFilterToggle` |
   | FAB at rest | `OnFabClick` |
   | empty create | `OnEmptyCreate` |
   | clear filter | `OnClearTagFilter` |
   | selection close | `OnSelectionExit` |
   | FAB in selection | `OnBulkDelete` |
   | selection-bar archive | `OnBulkDelete` |
   | bulk dialog confirm | `OnBulkDeleteConfirm` |
   | bulk dialog dismiss | `OnBulkDeleteDismiss` |
   | blocked dialog confirm | `OnBlockedArchiveDismiss` |
   | permanent dialog confirm | `OnConfirmPermanentDelete` |
   | permanent dialog dismiss | `OnCancelPermanentDelete` |

It may use production test tags and deterministic fixtures. It may not add a platform host,
repository, database, fake Store, fake `StringResolver`, screenshot baseline or second identity.

The module's Native oracle is 67 tuples: the 66 portable identities plus this scene.

### 11.5 D2 — where the plural delegation runs, and what is not covered

Steps 1–3 of Section 11.4 discharge kmp-string-resolver.md's R5: `ComposeStringResolver
.getPluralString` runs for the first time and its output is asserted, on Native, in EN.

On Android host it does not run, and this phase records that rather than routes around it:

- `FEATURE_FORBIDDEN_BUILD_TOKENS` rejects `robolectric` in a shared feature's build;
- the generated `Res` is internal (`publicResClass` is rejected too), so neither kit nor app can
  reach this catalog;
- kit's own catalog has no plural.

On Android the delegation runs in production and in the app journeys of Section 3.6 whenever a row
has stats, with no text assertion. RU plural forms are pinned statically by the topology gate's
catalog check only.

Control 35 shows the scene is the only executed guard of the delegation. testing.md records this
(Section 9.2).

## 12. CI ownership and positive verification

### 12.1 Topology gate data (normative)

`MODULES["feature:all-exercises"]` holds the 101 files under `src` of Section 9.1. Its
`kotlin_source_sets` are `commonMain, commonTest, androidHostTest, androidDeviceTest, iosTest`; its
`resource_dirs` are `src/commonMain/composeResources/values` and
`src/commonMain/composeResources/values-ru`, in all-trainings' format.
`EXPECTED_APP_DEPS_READERS` loses the all-exercises entry: the script compares it exactly with every
`appDeps<…Factory>(` reader in the tree.

`ALL_EXERCISES_ENTRY`, appended to `FEATURE_ENTRIES` after `ALL_TRAININGS_ENTRY`, uses the field
names of the merged schema:

| Field | Value |
| --- | --- |
| module / short_name | `feature:all-exercises` / `all-exercises` |
| root / package_dir | `feature/all-exercises` / `io/github/stslex/workeeper/feature/all_exercises` |
| build_fragments | every plugin, `packageOfResClass`, `includeJavax()`, dependency and `apply(from = …)` line of Section 10, each exactly once — except `implementation(kotlin("test"))`, which kotlin_test_count covers |
| kotlin_test_count | 2 |
| catalog | the 39 rows of Section 3.4, in file order, with the P1 values (decoded text) |
| resource_prefix | `feature_all_exercises_` |
| payload_files | `AllExercisesStore.kt`, `ExerciseUiModel.kt`, `ExerciseTypeUiModel.kt`, `TagUiModel.kt` (plus everything under domain/). `AllExercisesUiMapper.kt`, `ClickHandler.kt` and `PagingHandler.kt` are deliberately absent: under D1 they resolve copy |
| store_file / state_fields | `mvi/store/AllExercisesStore.kt` / `pagingUiState: PagingUiState<PagingData<ExerciseUiModel>>`, `availableTags: ImmutableList<TagUiModel>`, `activeTagFilter: ImmutableSet<String>`, `pendingPermanentDelete: PendingDelete?`, `selectionMode: SelectionMode`, `pendingBulkDelete: PendingBulkDelete?`, `blockedArchiveDialog: BlockedArchiveDialog?` |
| resource_wrapper_readers | empty |
| preview_subjects | the four of Section 5.4 |
| suppressions | the two of Section 3.1; nothing under commonTest |
| test_names | the 66 of Section 3.2, plus the scene name under the iosTest path |
| golden_test / golden_methods | `src/androidHostTest/…/golden/AllExercisesGoldenTest.kt` / the 26 of Section 3.3 |
| device_test / device_fragments | `src/androidDeviceTest/…/AllExercisesScreenTest.kt` / archive's three (`@Smoke`, the `@Ignore` text of Section 3.3, `fun pendingFeatureRewrite()`), plus `@Regression`, `fun blockedArchiveDialogListsBlockingTrainings()` and `ComposeStringResolver().getString(Res.string.feature_all_exercises_blocked_archive_confirm)` |
| factory names | Section 0.3; feature_file `di/AllExercisesFeature.kt`, graph_file `ui/AllExercisesGraph.kt` |
| identity_test / identity_fragments | `app/app/src/test/…/di/AllExercisesExtensionIdentityTest.kt` / the `allExercises()` helper of Section 6 |
| identity_names / identity_accessor | the two inherited names / `(?<!AppGraph)\.allExercises\(\)` |
| extra_root_fragments | none |
| extra_checks | `all_exercises_copy_contract`, `all_exercises_back_contract` below |

`all_exercises_copy_contract` checks D1 (Sections 5.2, 5.3). Every count is over sources with Kotlin
comments stripped:

1. In `mvi/store/AllExercisesStore.kt`, the parameters (by 7.9's `data_class_parameters`) of
   `ShowPermanentDeleteSuccess` and of `ShowBulkDeleteSuccess` are exactly `["message: String"]`.
   `BlockedArchiveDialog`'s are exactly
   `["archivedSummary: String?", "items: ImmutableList<BlockedArchiveItem>"]`.
2. `mvi/mapper/AllExercisesUiMapper.kt` holds exactly once each of
   `suspend fun ExerciseListItemDomain.toUi(`, `suspend fun composeFooterLabel(` and
   `suspend fun BulkArchiveResult.toBlockedArchiveDialog(`. It holds exactly two
   `Clock.System.now().toEpochMilliseconds()`, three `stringResolver.getString(` and seven
   `stringResolver.getPluralString(`.
3. `mvi/handler/ClickHandler.kt` holds exactly one `private val stringResolver: StringResolver,`,
   one `stringResolver.getString(` and one `stringResolver.getPluralString(`.
   `mvi/handler/PagingHandler.kt` holds exactly one `private val stringResolver: StringResolver,`
   and no `getString(` or `getPluralString(`.
4. No commonMain file imports CMP's top-level functions. The pattern
   `^import org\.jetbrains\.compose\.resources\.(getString|getPluralString)\b` has no match.
5. Under `mvi/`, every `getString(` is `stringResolver.getString(` and every `getPluralString(` is
   `stringResolver.getPluralString(`: the counts are equal per file. No `mvi/` file contains
   `stringResource(` or `pluralStringResource(`.
6. No file under `ui/` contains `getString(` or `getPluralString(`. Copy outside composition does
   not move into the graph.

`all_exercises_back_contract`: `ui/AllExercisesGraph.kt` (the entry's `graph_file`) holds exactly
once each of:

- `import androidx.compose.ui.backhandler.BackHandler`;
- `ExperimentalComposeUiApi::class`;
- `BackHandler(enabled = processor.state.value.interceptBack)`;
- `processor.consume(Action.Click.OnSelectionExit)`.

The topology script's change is additive:

- `MODULES`, `EXPECTED_APP_DEPS_READERS`, `ALL_EXERCISES_ENTRY`, its two checks and the
  `FEATURE_ENTRIES` tuple;
- `check_feature_entry`, `ARCHIVE_ENTRY`, `ALL_TRAININGS_ENTRY` and their checks stay
  byte-identical.

`assert_kmp_ios_smoke.py` gains a `feature:all-exercises` entry, after all-trainings', with the 67
tuples of Section 11.4. The classnames are the suites' packages from Section 3.2; the scene's is
`io.github.stslex.workeeper.feature.all_exercises.AllExercisesFeatureSceneIosTest`.

`android_build_unified.yml`, KMP iOS kit smoke job:

- add `:feature:all-exercises:iosSimulatorArm64Test` to the simulator test command and
  `:feature:all-exercises:linkDebugTestIosArm64` to the device-link command;
- add the directory `feature/all-exercises` to the binary-assert loop (`/9 linked` → `/10 linked`)
  and `feature/all-exercises/build/test-results/iosSimulatorArm64Test/` to the upload list;
- the two "nine modules" comments say ten;
- the job-header comment that still names six modules ("the kit, navigation, MVI, start-mode,
  plan-editor and image-viewer Phase-7 Native tests") names all ten.

The job, step and context names stay.

The ten modules, for every command below:

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

### 12.2 Entry gate before any edit

7.9 Section 12.2 applies, plus:

- `16c41a0c` (PR-R) is an ancestor of the base;
- `StringResolver` and `ComposeStringResolver` on the base match kmp-string-resolver.md §4.1–4.2,
  and `AppGraph` binds them (`StringResolverIdentityTest` green);
- `git diff 16c41a0c..<base> -- feature/all-exercises` is empty;
- every commit since `16c41a0c` touching a Section 9.2 path is listed, with whether it conflicts;
- the census of Sections 3.1–3.7 is reproduced on the base;
- the current nine-module Native command and oracle are green.

Baseline commands, with the stable flags:

    ./gradlew :feature:all-exercises:assembleDebug
    ./gradlew :feature:all-exercises:testDebugUnitTest         # 66 portable cases; goldens excluded
    ./gradlew :feature:all-exercises:verifyPaparazziDebug      # 52 golden + 66 portable host cases
    ./gradlew :feature:all-exercises:assembleDebugAndroidTest

`gradle/golden-gate.gradle.kts` excludes `*.golden.*` from plain unit runs, as 7.9 Section 12.2
records.

### 12.3 Focused gates after the implementation

    python3 .github/scripts/assert_kmp_ui_source_topology.py
    ./gradlew :feature:all-exercises:assembleDebug
    ./gradlew :feature:all-exercises:testAndroidHostTest       # 66 portable cases
    ./gradlew :feature:all-exercises:verifyPaparazziDebug      # 52 golden + 66 portable
    ./gradlew <iosSimulatorArm64Test of the ten modules of Section 12.1> --continue
    python3 .github/scripts/assert_kmp_ios_smoke.py            # covers all-exercises from commit 2
    ./gradlew <linkDebugTestIosArm64 of the ten modules> --continue
    ./gradlew :app:common:assembleDebug

Then:

- `./gradlew detekt --continue` alone, on every commit's tree;
- the two `AllExercisesExtensionIdentityTest` identities (fresh XML);
- `StringResolverIdentityTest` (fresh XML);
- on an explicit API-34 serial with animations disabled, as `ui_tests.yml` does: both
  `AllExercisesScreenTest` identities through the module's `connectedDebugAndroidTest` (the
  convention's alias of `connectedAndroidDeviceTest`), and the five journey classes of Section 3.6
  (fresh XML each).

Run `:feature:all-exercises:iosSimulatorArm64Test` as soon as production and commonTest compile for
Native, before the scene and the app paths. Its XML must name the U+2014 identity byte-exactly.

### 12.4 Repository and visual gates

7.9 Section 12.4 applies unchanged: assemble/lint/unit, detekt alone, assembleDebugAndroidTest,
verifyPaparazziDebug, `:lint-rules:test`, the personal-data gate, the canonical Smoke and Regression
device suites, diff hygiene, the PNG manifest check of Section 3.7, and the mockup shell gate where
runnable.

### 12.5 Final diff and remote proof

7.9 Section 12.5 applies with:

- the Section 9 boundary;
- 66 + 1 Native tuples;
- 52 unchanged golden blobs;
- the two production suppressions.

Record the KMP iOS kit smoke job's duration on the final head against its 120-minute timeout.

## 13. Mandatory known-negative controls

Every control runs fresh GREEN → one named observable RED → automatic exact restoration → fresh
GREEN, through `documentation/mockups/mutation_harness.py` in one-shot mode (AGENTS.md, "Mutating
the tree: use the harness"). Pass `--no-configuration-cache --console=plain` inside `--task`, as
PR-R did. A Python-scored control may instead use a byte-restoring copy in a detached worktree or
the scratchpad, restored with `cp`. The harness scores a compile failure as `INVALID`; a
compile-invalid mutation is never a RED where a compiled test is the detector. No mutation is
committed.

**From 7.8, through 7.9, with the substitutions of Section 0.3:** controls 1–17 and 19–25. Control 9
names all-exercises copy through `ResourceWrapper.getString`. Controls 15 and 16 are unchanged:
App.kt still resolves AppRootDeps once per admitted generation. Control 18 (archive-specific) has no
counterpart.

**From 7.9, adapted:**

26. In `lastTrainedRelativeLabel`, divide the sub-hour delta by `HOUR_MS` instead of `MINUTE_MS`.
    `composeFooterLabel minute granularity for sub-hour deltas` fails on Android host and Native,
    and the scene's `5 sessions · last 15m ago` row fails.
27. In `toBlockedArchiveDialog`, make `archivedSummary` always `null`. These fail on Android host
    and Native: `OnBulkDeleteConfirm opens blocked dialog with archived summary on partial block`
    and `toBlockedArchiveDialog truncates trainings and sets archived summary on partial block`.
28. Set `MAX_TRAININGS_SHOWN = 3`. The truncation identity fails on Android host and Native, and
    the scene's `used in Push, Pull +2 more` assertion fails.
29. Change `ShowBulkDeleteSuccess`'s parameter to `archivedCount: Int` in AllExercisesStore.kt.
    `all_exercises_copy_contract` names it.
30. In `ClickHandler`, import CMP's top-level `getString` and use it for `permanent_delete_success`.
    The copy contract names the import and the unqualified call.
31. Drop `enabled = processor.state.value.interceptBack` from the BackHandler. The back contract
    names it.
32. Reintroduce one bare `%d` in the EN catalog. The placeholder rule names the key and the token.
33. No counterpart: no app device test composes the graph directly.
34. Add a 67th portable identity under a new name. The topology test-name inventory names the
    unexpected identity.

**New:**

35. **D2.** In core:ui:kit's `ComposeStringResolver.getPluralString`, always call the
    no-argument overload. The scene fails on Native: the filled plural values and the mapper
    footers show raw templates.
    Record also that, under the same mutation, `:core:ui:kit:testAndroidHostTest` and kit's Native
    identity stay GREEN. They hold no plural, which is why the scene is the only executed guard.
36. **The argument rule.** Drop the count format argument from the mapper's `session_count` call,
    leaving `getPluralString(…, count)`. The strict fake throws, so every footer identity with
    sessions fails on Android host and Native, and the scene's footers show `%1$d sessions`.
37. **D3.** In `AllExercisesScreenTest`, read `feature_all_exercises_blocked_archive_title` instead
    of `_confirm`. `blockedArchiveDialogListsBlockingTrainings` fails on the recorded serial
    (fresh XML): clicking the title does not dismiss.
38. In ui/AllExercisesGraph.kt, show
    `getString(Res.string.feature_all_exercises_permanent_delete_success)` instead of
    `event.message`. The copy contract names the out-of-composition read under `ui/`.
39. Hardcode the days segment as `"last ${days}d ago"` in place of its `stringResolver` call. Only
    the copy contract's resolver-call count names it.
    Record also that host, Native and the scene stay GREEN under the same mutation: the fake and
    the EN catalog produce the same text. That is the reason the count exists.

Controls 1–14, 17, 29–32, 34, 38 and 39 are scored by the Python gates (topology and placeholder
rule) and need not compile. Each is one logical mutation; control 3 relocates a file and control 7
moves one key between two catalogs, as in 7.8.

Controls 1 and 3 target a file the contract does not open by name, so the gate names it instead of
crashing on a missing read. `ui/components/TopBarMode.kt` is one such file. Device controls (37) are
scored only with fresh XML from the recorded serial.

## 14. STOP conditions

7.9 Section 14 applies, read with Section 0.3. In addition, stop if:

- `16c41a0c` is not an ancestor of the base, or `StringResolver` / `ComposeStringResolver` differ
  from kmp-string-resolver.md §4.1–4.2;
- the target or any Section 9.2 path drifted from the discovery baseline in a way this document does
  not describe;
- D1 needs any of these: a new production file; a resource handle, `ResourceWrapper` or
  `StringResolver` in a payload file; resource resolution inside a State lambda (an `updateState` /
  `updateStateImmediate` body, as the topology gate reads it); or a change to an Event or to State;
- any of the 14 resolver-dependent identities needs an assertion change, or a strict fake cannot
  reproduce today's stub strings;
- the Native scene's EN catalog differs from Section 3.4 in any value, including the decoded
  character references of its three strings;
- Compose resources cannot be read in the module's device test, in composition or through the
  resolver. Report the exception; never fall back (Section 11.3);
- the Native XML reports the U+2014 identity under any other name;
- the KMP iOS kit smoke job times out on any PR head;
- any golden needs recording or any PNG blob changes;
- detekt reports anything in the moved tests that PR-L does not cover. The fix is never a
  suppression;
- the portable BackHandler changes Android back behaviour in any device journey.

A STOP produces a measured note and, where needed, an amendment proposal. It never widens the
implementation silently.

## 15. Signed, bisect-green commit plan

Three English Conventional Commits, each signed, GitHub-verified and green on its own tree:

1. **refactor(kmp): share all-exercises feature entry** — the module (Sections 3–11) and the six
   app paths. It also carries the minimal topology delta that keeps this tree green (precedent
   7.8 G1): the `MODULES` entry, and the all-exercises reader removed from
   `EXPECTED_APP_DEPS_READERS`.
2. **ci(kmp): gate shared all-exercises feature** — `ALL_EXERCISES_ENTRY` with its two checks, the
   Native oracle entry, and the workflow lists and comments.
3. **docs(kmp): record Phase 7.10 evidence** — this document's Status line, which becomes
   `**Status:** COMPLETE — implemented by the PR whose merge brought this line to dev.` (PR-R's
   precedent), Section 19, and the documentation edits of Section 9.2. Sections 0–18 stay
   byte-identical.

Each commit is made first. Its gates, the controls (after commit 2) and the repository gates then
run on the committed tree. 7.8 and 7.9 already ran this way; this document makes it the rule. A
fix is a new signed commit inside Section 9, and every gate it can reach is re-run. Nothing is
amended, squashed or reordered.

## 16. Exit criteria

7.9 Section 16 applies with:

- the 102-path topology and two private catalogs;
- 39 identifiers, with the 48 P1 values and nothing else changed;
- `ResourceWrapper` absent from the feature, and all copy outside composition through
  `StringResolver` (the copy contract);
- 66 identities on host and Native, and 67 Native tuples;
- 52 golden cases with unchanged blobs and the relative PNG manifest proof;
- two production suppressions and no other;
- the explicit factory flow, with both identities through the accessor;
- the device skip, and the `@Regression` identity green through the resolver read;
- the topology entry, oracle and workflow at ten modules;
- readers 9 → 8;
- all controls of Section 13 evidenced;
- the Section 9.2 documents updated, including the D2 limitation.

## 17. Remaining ordered roadmap

After 7.10 the frontier is remeasured. By today's sizes the next entry is past-session. Then come
home, exercise-chart, settings, single-training, exercise and live-workout; then the app-dialog
boundary, app/common, and the permanent iOS host. A throwaway iosApp remains forbidden.

Moving the files of tech-debt.md's 🟢 resolver row onto `StringResolver` is a separate follow-up.
So is the locale-list remedy, a maintainer decision.

## 18. Authorization

The maintainer's GO of 2026-10-12 locked D1–D4 and covers two things:

- landing this document on dev;
- implementing it as one PR into dev, inside Section 9.

Merge stays the maintainer's.

## 19. Implementation evidence

Reserved for the implementation's documentation commit.
