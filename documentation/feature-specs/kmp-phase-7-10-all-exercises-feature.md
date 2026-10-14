# KMP Phase 7.10 — feature:all-exercises becomes a shared feature entry

**Status:** COMPLETE — implemented by the PR whose merge brought this line to dev.

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

This section records the bounded implementation authorized by the maintainer's GO of 2026-10-12
(Section 18), plus decision D5 of 2026-10-13 (Section 19.7). The GO authorized exactly Section 7
and nothing in Section 8. Sections 0–18 remain the historical specification and are not rewritten
here; where the measured tree contradicts their prose, Section 19.7 says so.

Every load-bearing Gradle invocation below ran after `./gradlew --stop` and a check that no other
Gradle client on the default daemon registry was running, with `--rerun-tasks --no-build-cache
--no-configuration-cache --console=plain` (plus `--continue` where stated). Every "Executed" cell
is the logged `N actionable tasks: N executed`. Only JUnit XML written after the run's recorded
start was parsed, and each gate's XML was copied aside at once, so no later run could overwrite
it. detekt never ran in the same invocation as tests.

### 19.1 Entry, drift, and the D5 stop

This specification landed on `dev` as `97c03dfe1667b2cbb7867c56843d4fcecacddc4c`, one signed
commit pushed as a fast-forward of `16c41a0c`; its blob in the tree had the input sha256
`998798f6…5317a`, and GitHub reports it `verified: true`, `reason: valid`. A Markdown-only push
starts no unified run (`paths-ignore: ['**/*.md']`), so none started. That SHA is the
implementation base; `16c41a0c` (PR-R) is its parent. The worktrees whose branch tips were already
ancestors of `origin/dev` (PR-R's and two clean session worktrees) were removed with
`git worktree remove`; the main checkout stayed.

| Entry check | Result |
| --- | --- |
| latest push run of `Android CI/CD - Unified Build and Tests` on `dev` | run `38268385065` on `16c41a0c`, `success`: `Release bundle identity`, `KMP iOS kit smoke`, `Build and Unit Tests` and both report jobs green |
| PR-R on the base | `StringResolver.kt` and `ComposeStringResolver.kt` match `kmp-string-resolver.md` Sections 4.1–4.2 (signatures, the three annotations, the aliased imports, `AppScope` from `core.core.di`, both GUARD comments); `AppGraph` declares `val stringResolver: StringResolver`; `StringResolverIdentityTest` green (19.3) |
| drift | `git diff 16c41a0c..97c03dfe -- feature/all-exercises` is empty; the only commit since `16c41a0c` touching a Section 9.2 path is `97c03dfe` itself, which adds this document and conflicts with nothing |
| open PRs touching the module | none open; no remote branch names all-exercises |

Two read-only measurers reproduced Section 2's all-exercises row and Sections 3.1–3.7 on the base
independently, neither reading the other's output. Census A read only the base's git objects
(`ls-tree`, `cat-file`, and a scratch index built with `read-tree` for the PNG manifests). Census B
read the clean worktree with a Kotlin lexer and declaration walker (nested comments, string
templates, backtick names, multi-line annotations) and DOM XML parsing. Both reported MATCH on every
item:

| Section | Specification | Measured (A and B agree) |
| --- | --- | --- |
| 2, all-exercises row | 34 / 2,062 / 11 / 1,471 / 30 / 52 | equal |
| 3.1 files and lines | 34 files, 2,062 lines, per-file table | every row equal, in table order |
| 3.1 suppressions | two production, one test | `AllExercisesFeature.kt:24` `UNCHECKED_CAST`, `ClickHandler.kt:21` `TooManyFunctions`, `AllExercisesUiMapperTest.kt:20` `MagicNumber`; no `SuppressLint`, `SuppressWarnings` or `noinspection` |
| 3.1 Android coupling | as listed | `R` in seven files; `ResourceWrapper` calls mapper 10 (3 + 7) and `ClickHandler` 2 (1 + 1), passed through by `PagingHandler`; `System.currentTimeMillis()` at mapper lines 27 and 51; the activity `BackHandler`, `LocalContext` with `appDeps`, `@VisibleForTesting` on `NAME`, and `uiMode` in the four previews where stated |
| 3.2 identities | 66, 5/24/2/1/11/2/9/4/4/4 | byte-identical to the bullets, in order; MockK in the first five suites, JUnit 5 in all ten, no `paging-testing` import; one U+2014 name, `E2 80 94` at zero-based byte offset 29 |
| 3.2 resolver facts | strict mapper mock, relaxed click mock | equal; the mapper stubs return `"$count sessions"` and siblings from the quantity, and only `used_in_format` reads its argument |
| 3.3 goldens and device | 26 methods; 52 PNGs, 52 blobs, 100644; the device test's two identities | equal |
| 3.4 catalog | 30 + 9 per locale in file order; 48 bare `%d` (EN 16, RU 32); three EN strings with numeric character references | equal after applying P1; `&#8216;` and `&#8217;` occur only in `permanent_delete_title`, `permanent_delete_body` and `blocked_archive_title`, and no other `composeResources` catalog carries one; both roots carry `xmlns:tools` and `tools:ignore="DuplicateStrings"`; 29 identifiers read in composition and 11 outside, all 39 read |
| 3.5, 3.6 | as stated | equal; the four `ALL_EXERCISES_GRAPH` journeys declare the tag themselves and reach the tab through `NavPaths.toAllExercises()`, and `ApplicationBottomBarTest` holds the literal tag and uses `NavPaths` only for its timeout |
| 3.7 readers and PNG manifests | 9 readers; four hashes, three projections | 9 readers equal to `EXPECTED_APP_DEPS_READERS`; all seven hashes equal |

**The D5 stop.** Section 11.1 asks the domain suite for in-file fakes that implement
`ExerciseRepository`. Its `setAdhocPlan(exerciseUuid: String, planSets: List<PlanSetDataModel>?)`
names `core:data:database`'s `PlanSetDataModel`, and `core:data:exercise` declares
`implementation(project(":core:data:database"))`, so the type is not on this module's compile
classpath (`:feature:all-exercises:dependencies --configuration debugUnitTestCompileClasspath` lists
`project ':core:data:exercise'` and no database line). A return type can be overridden as
`Nothing`; a parameter type cannot. Measured in a detached probe worktree at the base, removed
afterwards: a 34-member in-file fake failed with `Unresolved reference 'PlanSetDataModel'` and
`'setAdhocPlan' overrides nothing`, and the same fake with one test-scope edge on
`core:data:database` compiled. Section 10 makes a needed edge a STOP-and-amend, so the
implementation stopped before any edit and the maintainer decided D5 (Section 19.7).

Environment: Gradle 9.6.1 (wrapper), launcher and daemon JBR 21.0.11, Kotlin 2.4.10, Paging 3.5.0,
Xcode 26.6 (17F113). Kotlin/Native's test task used its default simulator with none pre-booted, as
in the workflow. Signing: repository-local SSH signing (`commit.gpgsign=true`); each commit object
carries an SSH `gpgsig` header.

### 19.2 Commits and changed boundary

| Commit | SHA | Boundary |
| --- | --- | --- |
| `refactor(kmp): share all-exercises feature entry` | `df7b36a7fc3086012a7867c4df475c2360c9c6b8` | the module (Sections 3–11), the six app paths, and the minimal topology delta (`MODULES["feature:all-exercises"]`, the reader removed from `EXPECTED_APP_DEPS_READERS`); 113 paths with rename detection, 1,861 insertions, 601 deletions |
| `ci(kmp): gate shared all-exercises feature` | `3cdf723573d0b38f3d9b897b4248e5aae74c6cab` | exactly the three Section 9.2 CI paths: `assert_kmp_ui_source_topology.py` +591/−1, `assert_kmp_ios_smoke.py` +129/−0, `android_build_unified.yml` +10/−7 |
| `docs(kmp): record Phase 7.10 evidence` | this commit | exactly the five Section 9.2 documentation paths |

Against the base, the three commits change 216 paths counted without rename pairing: 202 under
`feature/all-exercises` (the 100 legacy `src/main`, `src/test` and `src/androidTest` paths removed,
101 added — the 100 moved files and the Native scene — and `build.gradle.kts` modified) and exactly
the 14 Section 9.2 paths outside it:

    app/app/src/main/java/io/github/stslex/workeeper/di/AppGraph.kt
    app/app/src/test/kotlin/io/github/stslex/workeeper/di/AllExercisesExtensionIdentityTest.kt
    app/common/build.gradle.kts
    app/common/src/main/kotlin/io/github/stslex/workeeper/App.kt
    app/common/src/main/kotlin/io/github/stslex/workeeper/app/common/di/AppRootDeps.kt
    app/common/src/main/kotlin/io/github/stslex/workeeper/host/AppNavigationHost.kt
    .github/scripts/assert_kmp_ui_source_topology.py
    .github/scripts/assert_kmp_ios_smoke.py
    .github/workflows/android_build_unified.yml
    documentation/architecture.md
    documentation/ci-cd.md
    documentation/testing.md
    documentation/tech-debt.md
    documentation/feature-specs/kmp-phase-7-10-all-exercises-feature.md

`feature/all-exercises` tracks exactly 102 paths at exit: `build.gradle.kts`, `commonMain` 36 (34
Kotlin files and 2 catalogs), `commonTest` 10, `androidHostTest` 53 (the golden test and 52 PNGs),
`androidDeviceTest` 1 and `iosTest` 1, all mode 100644, with nothing tracked or on disk under
`src/main`, `src/test`, `src/androidTest`, `androidMain` or `iosMain`. With git's default rename
detection the module reads 96 renames, 1 modification, and four delete/add pairs plus the new scene
(Section 19.7); at `-M10%` it reads 100 renames, 1 addition and 1 modification. The 52 PNGs, the
golden test and 22 of the 34 production files are 100%-similarity renames. The catalogs differ from
the base only by decision P1's 48 lines (EN 16, RU 32), each an `<item quantity=…>` whose only
change is `%d` → `%1$d`; comments, root attributes, character references and the trailing newline
are byte-identical. `git diff --check` is clean for every commit and for base..head.

### 19.3 Fresh baseline before any edit

| Command (base tree) | Executed | Parsed result |
| --- | ---: | --- |
| `:feature:all-exercises:assembleDebug` | 104/104 | target assembled |
| `:feature:all-exercises:testDebugUnitTest` | 173/173 | 10 XML suites, 66 cases 5/24/2/1/11/2/9/4/4/4, names equal Section 3.2 (JUnit's trailing `()` removed), 0/0/0 |
| `:feature:all-exercises:verifyPaparazziDebug` | 174/174 | 11 suites, 118 cases (52 golden and 66 portable), 0/0/0; `Visual gate live: 52 golden test case(s) executed for 52 golden image(s).` |
| `:feature:all-exercises:assembleDebugAndroidTest` | 223/223 | device APK assembled |
| current nine-module Native command (workflow form, `--continue --full-stacktrace`) | 259/259 | 38 XML files, 171 cases, 0/0/0: kit 2, navigation 1, mvi 14, start-mode 2, core plan-editor 20, image-viewer 13, feature plan-editor 43, archive 26, all-trainings 50 |
| `python3 .github/scripts/assert_kmp_ios_smoke.py` | GREEN | `native gate live:`, every module `… — verified N exact identities` |
| `:app:app:testDebugUnitTest --tests '*AllExercisesExtensionIdentityTest*' --tests '*StringResolverIdentityTest*'` | 577/577 | 2 files, 4 cases, 0/0/0 |

The plain unit run compiled `AllExercisesGoldenTest` but executed no golden case
(`gradle/golden-gate.gradle.kts`). Its identities were read from a second plain run
(173/173) whose XML was archived immediately, because the Paparazzi run that followed the first one
rewrote the same result directory (Section 19.8).

### 19.4 Commit 1 focused evidence

While commit 1 was assembled, unrecorded iteration builds (no forced flags) compiled the tree; they
are not evidence. Two runs on the uncommitted tree are recorded because the procedure asks for them
there:

| Command | Executed | Parsed result |
| --- | ---: | --- |
| early Native check: `:feature:all-exercises:iosSimulatorArm64Test` (production and suites ported; no scene, no app paths yet) | 106/106 | 10 suites, 66 cases 5/24/2/1/11/2/9/4/4/4, 0/0/0; the module's first Native run; names equal Section 3.2 after stripping the classname prefix `iosSimulatorArm64Test.` and the name suffix `[iosSimulatorArm64]` |
| the scene's first run (iteration build) | — | the scene passed on its first execution: the stored and filled EN catalog equal Section 3.4, the three character-reference strings included (Section 14's catalog condition cleared) |

The U+2014 identity in the Native XML, read as raw bytes:

    classname="iosSimulatorArm64Test.io.github.stslex.workeeper.feature.all_exercises.mvi.handler.ClickHandlerTest"
    name="OnBulkDelete fires no haptic — the morph is silent[iosSimulatorArm64]"    dash at zero-based byte offset 29

It occurs once, as `E2 80 94`, in every `ClickHandlerTest` Native XML of this record.

The Section 12.3 gates then ran on the committed tree `df7b36a7f` (porcelain empty):

| # | Command | Executed | Parsed result |
| ---: | --- | ---: | --- |
| 1 | `python3 .github/scripts/assert_kmp_ui_source_topology.py` | GREEN | Homebrew Python 3.14.8 and macOS Python 3.9.6, byte-identical: `feature:all-exercises: 101 exact source/resource/test files`; `app:common API edges and 8 remaining Context.appDeps readers are exact`; `compose-resource placeholders: 16 commonMain catalogs walked, every placeholder is %N$d or %N$s with N >= 1` |
| 2 | `:feature:all-exercises:assembleDebug` | 302/302 | Android AAR and both iOS klibs (`compileAndroidMain`, `compileKotlinIosArm64`, `compileKotlinIosSimulatorArm64`, `compileCommonMainKotlinMetadata` executed) |
| 3 | `:feature:all-exercises:testAndroidHostTest` | 175/175 | 10 suites, 66 cases 5/24/2/1/11/2/9/4/4/4, 0/0/0, names equal Section 3.2 |
| 4 | `:feature:all-exercises:verifyPaparazziDebug` | 176/176 | `Visual gate live: 52 golden test case(s) executed for 52 golden image(s).`; 11 suites, 118 cases (52 golden and the 66 portable, names exact), 0/0/0; no `recordPaparazzi` task |
| 5 | `:feature:all-exercises:iosSimulatorArm64Test` | 106/106 | 11 suites, 67 tuples by name (the 66 and the scene), 0/0/0 |
| 6 | `:app:common:assembleDebug` | 323/323 | explicit consumer compiled |
| 7 | `:app:app:testDebugUnitTest --tests '*AllExercisesExtensionIdentityTest*' --tests '*StringResolverIdentityTest*'` | 580/580 | 2 suites, 4 cases, 0/0/0: `extension resolves the store through the parent graph`, `store's app-scoped deps are the SAME instances the parent holds`, `app graph binds the Compose string resolver`, `two reads give the one app-scoped resolver` |
| 8 | `./gradlew detekt --continue`, alone | 63/63 | 55 fresh reports, 0 findings; `:feature:all-exercises:detekt` and `:detektAndroidTestSuite` executed |
| 9 | device: `AllExercisesScreenTest` — `ANDROID_SERIAL=emulator-5554 ./gradlew :feature:all-exercises:connectedDebugAndroidTest --max-workers=2 --continue` | 224/224 | ran as `connectedAndroidDeviceTest`: `pendingFeatureRewrite` skipped with its documented reason, `blockedArchiveDialogListsBlockingTrainings` passed (decision D3: Compose resources load in the library device APK, in composition and through `ComposeStringResolver`) |
| 10 | device: journey classes — `ANDROID_SERIAL=emulator-5554 ./gradlew :app:app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=<the five Section 3.6 journey classes> --max-workers=2 --continue` | 741/741 | `Starting 29 tests`; fresh XML 29 cases, 0/0/0: `ApplicationBottomBarTest` 4, `BackStackStateRestorationTest` 4, `NavigationResultTest` 3, `RouteReachabilityTest` 15, `StoreRetentionTest` 3 |
| 11 | relative PNG proof | GREEN | below |

The golden JUnit XML names each case only `[1] LIGHT` or `[2] DARK`, so gate 4's method identities
were read from the same run's Gradle HTML class report: 52 cases, 52 distinct (method, theme) pairs,
equal to Section 3.3's 26 methods × {LIGHT, DARK}, all passed.

Gradle metadata (Section 10): every declared edge is used by its source set's imports (commonMain
12 edges; commonTest `kotlin("test")`, `coroutine.test` and D5's `core:data:database`; iosTest
`kotlin("test")` and `cmp.ui.test`; the golden harness; the device bundle, `ui.test.junit4`,
`test-utils`), and every import resolves to a declared edge, to the module itself, to Metro, to the
standard library, to kit's `api` surface (CMP runtime, foundation, material3, components-resources),
or, for the golden test's JUnit 5 annotations, to `KmpLibraryConventionPlugin`'s
`androidHostTestImplementation` of `junit-jupiter`. On the Android compile classpath
`kotlinx-collections-immutable` and `material-icons-core` arrive only through the module's own
edges (`dependencyInsight`).

Relative PNG proof (Section 3.7), at commit 1 and again at commit 2: the base manifests, with
exactly the 52 all-exercises paths rewritten from `src/test/` to `src/androidHostTest/`, equal the
head manifests byte for byte; all 528 working-tree PNGs hash to their index blobs; 13 Paparazzi
owners; 52 all-exercises PNGs under `src/androidHostTest/snapshots/images`, 0 under `src/test`, all
100644.

| Manifest | Base | Base, 52 paths rewritten | Head |
| --- | --- | --- | --- |
| Paparazzi mode/blob/path (456 entries, 13 owners) | `46f2f70f052aeaa500cfaee6834b343a5f21f717a0d684c0ead5a54767d833d5` | `f8792e1efeca575a6b1bfa6bf780c89b52a0475392e3f9fea3f04f90d598443b` | `f8792e1efeca575a6b1bfa6bf780c89b52a0475392e3f9fea3f04f90d598443b` |
| Paparazzi path list (456) | `64859f12035c03fab1d27c83c9aa12abd88286e9fec0dd5ef060e05572a65f2c` | `27b831bcc04b22f872dc2221abd310c3e33211f9f4163c42f0226822a4d6d44d` | `27b831bcc04b22f872dc2221abd310c3e33211f9f4163c42f0226822a4d6d44d` |
| All PNGs mode/blob/path (528) | `8f4705371b62353ed92a33af735cddaba926992c24ca718fc5cd491c77b40513` | `ce590ad5c3a859541f8249cb95ea33e33104b5ba326699b323289b15c2fb3f36` | `ce590ad5c3a859541f8249cb95ea33e33104b5ba326699b323289b15c2fb3f36` |
| all-exercises blob set (52 sorted blob IDs) | `4e71615ea8aacb223a77b5bc996147f23ca899c6f575f679060ec324f3fbe432` | — | `4e71615ea8aacb223a77b5bc996147f23ca899c6f575f679060ec324f3fbe432` |

Every value equals Section 3.7's; the base column reproduces its measured hashes and the rewritten
column its projections.

The device for every device gate in this record is the repository's AVD `Pixel_6_API_34`, serial
`emulator-5554`, the only attached device, started with `-avd Pixel_6_API_34 -port 5554 -no-window
-gpu swiftshader_indirect -noaudio -no-boot-anim -camera-back none -no-snapshot-save`: API 34,
fingerprint `google/sdk_gphone64_arm64/emu64a:14/UE1A.230829.050/12077443:userdebug/dev-keys`.
Window, transition and animator scales were 0, `accelerometer_rotation=1`, `user_rotation=0` and
the keyguard was not showing, recorded before and after every device run and identical each time.
Every device run started with its connected-result directory deleted by literal path.

### 19.5 Commit 2 gates and repository, visual, and device evidence

Commit 2 was made first; every gate below ran on the committed tree `3cdf72357` (porcelain empty).
Before the commit, a dry run of the same content was GREEN on the topology gate under both Pythons,
and the transcription audit (Section 19.7) was EQUAL.

| # | Command | Executed | Parsed result |
| ---: | --- | ---: | --- |
| 1 | `python3 .github/scripts/assert_kmp_ui_source_topology.py` | GREEN | both Pythons, byte-identical; adds `all-exercises catalogs, semantic State, suppressions, previews, test identities, and explicit factory flow are exact` to commit 1's three lines |
| 2 | ten-module Native command, exactly the edited workflow's (`--continue --full-stacktrace`) | 283/283 | 49 fresh XML files, 238 cases, 0/0/0: kit 2, navigation 1, mvi 14, start-mode 2, core plan-editor 20, image-viewer 13, feature plan-editor 43, archive 26, all-trainings 50, all-exercises 67 (11 files: the ten suites 5/24/2/1/11/2/9/4/4/4 and the scene; no tuple missing or unexpected; the U+2014 name as in Section 19.4) |
| 3 | `python3 .github/scripts/assert_kmp_ios_smoke.py` on gate 2's XML | GREEN | both Pythons, byte-identical: `native gate live:` and `feature:all-exercises: 67 executed / 0 skipped / 0 failed / 0 errored — verified 67 exact identities`; the other nine modules unchanged |
| 4 | ten-module `linkDebugTestIosArm64`, exactly the edited workflow's | 273/273 | the workflow's binary-assert step run verbatim: `10/10 linked`, every `test.kexe` fresh |
| 5 | `./gradlew detekt --continue`, alone | 63/63 | 55 fresh reports, 0 findings |
| 6 | workflow static checks | GREEN | YAML parses (Ruby Psych): jobs `build`, `release-bundles`, `kmp-ios-kit-smoke`, job name `KMP iOS kit smoke`; ten simulator tasks, ten link tasks, ten upload paths, `echo "$linked/10 linked"`; actionlint reports only the pre-existing `77:9: shellcheck reported issue in this script: SC2129:style:1:1`, at the base and at commit 2 |
| 7 | boundary, hygiene and PNG (static) | GREEN | `diff --check` clean for base..C1, C1..C2 and base..C2; 211 changed paths without rename pairing (115 with), 202 under the module and exactly the 9 Section 9.2 code paths outside it, commit 1 touching 7 of them (the six app paths and the topology script) and commit 2 the 3 CI paths; the module's 102 tracked paths as in Section 19.2; the PNG proof of Section 19.4 repeated (528/528 on-disk bytes equal their blobs) |

On the committed tree, after `./gradlew clean` (`72 actionable tasks: 45 executed, 27 up-to-date`,
not a positive gate), each Section 12.4 command ran as its own invocation, in sequence, so detekt
never ran beside tests:

| Command | Executed | Parsed result |
| --- | ---: | --- |
| `assembleDebug lintDebug testDebugUnitTest --continue` | 2631/2631 | 453 fresh host XML files across 32 modules, 3,175 test cases, 0/0/0 (2,854 unique module/classname/name triples); all-exercises 10 files, 66 cases, names equal Section 3.2, the U+2014 name byte-exact; lint: 36 modules `Lint found no errors or warnings`, and `app:app`, `app:dev` and `app:store` `Lint found 75 warnings` each, all dependency-version checks (GradleDependency 37, NewerVersionAvailable 32, AndroidGradlePluginVersion 6) in `gradle/libs.versions.toml` and `gradle-wrapper.properties`, none in source, as in 7.9 |
| `detekt --continue` | 63/63 | 55 fresh reports, 0 findings |
| `assembleDebugAndroidTest --continue` | 2169/2169 | GREEN; all-exercises `packageAndroidDeviceTest` and `assembleAndroidDeviceTest` executed |
| `verifyPaparazziDebug` | 647/647 | 13 liveness lines, 456 golden cases executed for 456 images: all-exercises 52/52, all-trainings 50/50, archive 14/14, exercise 48/48, exercise-chart 30/30, home 42/42, kit 86/86, live-workout 60/60, past-session 30/30, core plan-editor 18/18, settings 12/12, single-training 12/12, start-mode 2/2; 169 fresh XML files, 1,730 cases, 0/0/0; all-exercises 118 cases (52 golden and 66 portable); no `recordPaparazzi` task |
| `:lint-rules:test` | 9/9 | 16 suites, 151 tests, 0/0/0 |
| `python3 documentation/personal_data_gate.py -v` | GREEN | `[PASS] no personal data in tracked files`; six excused lines, all pre-existing, in paths this branch does not touch |
| device: Smoke suite — `ANDROID_SERIAL=emulator-5554 ./gradlew connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.annotation=io.github.stslex.workeeper.core.ui.test.annotations.Smoke --continue --max-workers=2` | 2129/2129 | 15 fresh XML files, 44 cases: 41 passed and exactly the three documented skips, `ArchiveScreenTest`, `AllTrainingsScreenTest` and `AllExercisesScreenTest` `pendingFeatureRewrite`; 0 failures and errors. `AllExercisesScreenTest` carries a class-level `@Smoke`, so `blockedArchiveDialogListsBlockingTrainings` passed here too. Then, as `run_smoke_ui_tests.sh` does, `assert_mvi_device_identities.py`: `core:ui:mvi (androidDeviceTest): 2 executed / 0 skipped / 0 failed / 0 errored — verified 2 exact identities` |
| device: Regression suite — the same with `…annotations.Regression` | 2129/2129 | 15 fresh XML owners, 88 cases, all executed, 0 skip, failure or error: app:app 49 (among them `AllTrainingsExtensionDbVisibilityTest` 1, `RouteReachabilityTest` 15, `ApplicationBottomBarTest` 4, `UiGenerationSwapTest` 1, `AppRuntimeUiHandshakeDeviceTest` 1, `UiAdmissionRaceTest` 3), core:data:database 35, core:data:exercise 1, feature:all-exercises 1 (`blockedArchiveDialogListsBlockingTrainings`), feature:wear-bridge 2, the other owners 0, the same membership as 7.9 |
| boundary, hygiene and PNG (static) | GREEN | as gate 7 above |
| mockup shell gate | static GREEN | checks 7 and 8 unverified locally (Section 19.8) |

Every connected result directory was deleted by literal path before each device suite, and the
device state was identical before and after each. Another checkout on this host (a different
repository clone, on its own daemon registry, so neither side's `--stop` reaches the other) built
and tested its own tree while these gates ran. Before the Smoke suite, the gate wrapper waited
until no other checkout's Gradle client ran at all. Before the Regression suite, it waited until
none ran a device-capable task: connected, install, managed-device or device check (Section 19.8
says why the wait changed). Before both, it waited until nothing was instrumenting on
`emulator-5554`. While each suite ran, it sampled every foreign client's command line every 15 s.
During the Smoke suite it took 98 samples: the other checkout's `detekt`, `clean`, and
`assembleDebug lintDebug testDebugUnitTest`, none of them device-capable. During the Regression
suite it took 52 samples, all of them the other checkout's host-only
`:app:wear:testStoreDebugUnitTest` runs, 0 device-capable.

### 19.6 Mandatory known-negative controls

All 37 controls of Section 13 — 1–17, 19–32 and 34–39; 18 and 33 have no counterpart — ran on the
committed tree `3cdf72357`: fresh GREEN → one named observable RED → automatic exact restoration →
fresh GREEN. No mutation was committed.

The Python-scored controls (1–14, 17, 29–32, 34, 38, 39) and the PNG-scored control 24 ran in a
detached worktree at `3cdf72357` (2,607 tracked files, porcelain empty), removed afterwards with
`git worktree remove`, through a scratch harness that refuses to start unless HEAD is that commit
and the porcelain is empty. Per control it takes a fresh GREEN, snapshots every touched path (bytes
and mode, or absence) and every directory the mutation would create, and applies one mutation whose
text anchors occur exactly once. It accepts a RED only with a non-zero exit, the gate's failure
header, no Traceback, and every named fragment present. It restores in `finally` with SIGTERM,
SIGINT and SIGHUP ignored during the restore, proves sha256 and mode equal and created paths absent,
requires an empty porcelain, and takes a fresh GREEN whose output is byte-equal to the pre-GREEN. The
topology scorer ran exactly as CI runs it. The whole pass ran once with macOS Python 3.9.6 and once
with Homebrew Python 3.14.8 as scorer; the two outputs are byte-identical, each ending
`SUMMARY: 23/23 controls RED-as-named and restored; problems=[]` and `END porcelain empty: True`.
Control 24's scorer is a scratch PNG assertion that hashes the on-disk bytes of every PNG and
requires the 52 blobs, the blob-set hash, and the all-PNG and Paparazzi manifests to equal the base
manifests with the 52 paths rewritten.

The Gradle- and device-scored controls (15, 16, 19–23, 25–28 and 35–37), the GREEN observations
Section 13 asks for under 35 and 39, and one extra run beyond Section 13 (control 25's
compile-validity run, below) ran in the branch worktree through
`documentation/mockups/mutation_harness.py` in one-shot mode, driven by a scratch script. Per
control the script requires the target file to equal HEAD's blob and the porcelain to be empty,
passes `--no-configuration-cache --console=plain` inside `--task` (the harness stops the daemon and
adds `--rerun-tasks --no-build-cache` itself), reads the RED at once from fresh XML or console
output, proves the mutated file's sha256 equal to HEAD's blob and the porcelain empty, and takes a
fresh GREEN. A compile error would have scored INVALID; no run did. Scorers:

- one invocation of `:feature:all-exercises:testAndroidHostTest
  :feature:all-exercises:iosSimulatorArm64Test --continue` for 19–22, 26–28 and 36, control 39's
  GREEN observation, and the extra compile-validity run under control 25's mutation (GREEN:
  250/250, host 66/66, Native 67/67);
- the same plus `:core:ui:kit:testAndroidHostTest :core:ui:kit:iosSimulatorArm64Test` for 35
  (GREEN: 277/277; kit host 415/415, kit Native 2/2, all-exercises 66/66 and 67/67);
- the ten-module Native command, then the oracle, for 23 (GREEN: 283/283, oracle GREEN);
- root `detekt --continue`, alone, for 25 (GREEN: 63/63, 55 fresh reports, 0 findings);
- device: `UiAdmissionRaceTest` for 15 and 16, `ANDROID_SERIAL=emulator-5554 ./gradlew
  :app:app:connectedDebugAndroidTest
  -Pandroid.testInstrumentationRunnerArguments.class=io.github.stslex.workeeper.app.UiAdmissionRaceTest
  --continue --max-workers=2` (GREEN: 741/741, 3/3);
- device: `AllExercisesScreenTest` for 37, `ANDROID_SERIAL=emulator-5554 ./gradlew
  :feature:all-exercises:connectedDebugAndroidTest --continue --max-workers=2` (GREEN: 224/224,
  `blockedArchiveDialogListsBlockingTrainings` passed and `pendingFeatureRewrite` skipped).

The connected result directory was deleted before every device run, and the device state was
recorded before and after each device group, identical each time. Within a group, one control's
post-GREEN served as the next one's pre-GREEN, because nothing changed between them (same HEAD,
empty porcelain, the next target byte-equal to HEAD): 19 → 20 → 21 → 22 → 26 → 27 → 28 → 36 →
control 39's observation → the compile-validity run, and 15 → 16. The harness's own verdict line
knows only test failures. It reads `GREEN — GATE HOLE` for 23, whose scorer is the oracle, and for
the two runs whose expected result is GREEN (39's observation and the compile-validity run). It
reads `RED (no named test — check)` for 25, whose scorer is detekt's report.

| # | Mutation | Observable RED (quoted) | GREEN before/after |
| ---: | --- | --- | --- |
| 1 | delete `ui/components/TopBarMode.kt` | `feature:all-exercises: source manifest mismatch; missing=['src/commonMain/kotlin/io/github/stslex/workeeper/feature/all_exercises/ui/components/TopBarMode.kt'], extra=[]` | topology GREEN |
| 2 | copy `di/AllExercisesScope.kt` into `src/main/kotlin/…/di/` | `feature:all-exercises: legacy source set src/main still contains ['src/main/kotlin/io/github/stslex/workeeper/feature/all_exercises/di/AllExercisesScope.kt']` (plus the manifest's `extra=[…]` and `unexpected Kotlin-bearing source set src/main`) | topology GREEN |
| 3 | move `ui/components/TopBarMode.kt` to `ui/TopBarMode.kt` | `feature:all-exercises: source manifest mismatch; missing=['src/commonMain/…/ui/components/TopBarMode.kt'], extra=['src/commonMain/…/ui/TopBarMode.kt']` | topology GREEN |
| 4 | `import android.util.Log` in `ui/components/PagingTailKind.kt` | `feature:all-exercises: src/commonMain/…/ui/components/PagingTailKind.kt imports a platform API in commonMain: 'import android.'` | topology GREEN |
| 5 | rename the EN key `feature_all_exercises_paging_retry` to `…_renamed` | `feature:all-exercises: exact private CMP catalog mismatch in feature/all-exercises/src/commonMain/composeResources/values/strings.xml; mismatched=["feature_all_exercises_paging_retry: expected='Retry', actual=None"], unexpected=['feature_all_exercises_paging_retry_renamed'], order=drifted` (plus two ownership-drift lines, old and new key) | topology GREEN |
| 6 | append a second word to the RU value of `feature_all_exercises_paging_retry` | `feature:all-exercises: exact private CMP catalog mismatch in feature/all-exercises/src/commonMain/composeResources/values-ru/strings.xml; mismatched=["feature_all_exercises_paging_retry: expected=<RU value>, actual=('string', 'feature_all_exercises_paging_retry', <mutated RU value>)"], unexpected=[], order=exact` (the two RU values are named here, not quoted) | topology GREEN |
| 7 | move the EN `feature_all_exercises_paging_retry` into `feature/settings/src/main/res/values/strings.xml` | `feature:all-exercises: resource ownership drift for feature_all_exercises_paging_retry; expected=['feature/all-exercises/src/commonMain/composeResources/values-ru/strings.xml', 'feature/all-exercises/src/commonMain/composeResources/values/strings.xml'], actual=['feature/all-exercises/src/commonMain/composeResources/values-ru/strings.xml', 'feature/settings/src/main/res/values/strings.xml']` (plus the EN catalog mismatch) | topology GREEN |
| 8 | `val titleRes: Int = 0` in `State` | `feature:all-exercises: Store State must stay semantic with exactly ((…the seven Section 12.1 fields…)); actual=(…, ('titleRes', 'Int = 0'))` | topology GREEN |
| 9 | `.copy(footerLabel = resourceWrapper.getString(0))` on `PagingHandler`'s mapped item | `feature:all-exercises: src/commonMain/…/mvi/handler/PagingHandler.kt resolves all-exercises copy through ResourceWrapper.getString; use the private Compose resources` (plus the copy contract's `copy contract requires 'getString(' exactly zero times; found 1` and `reads copy outside the StringResolver: 'getString(' 1 time(s), 0 through stringResolver`) | topology GREEN |
| 10 | `val label = getString(TITLE)` inside `ClickHandler`'s bulk-confirm `updateStateImmediate` | `feature:all-exercises: updateStateImmediate in src/commonMain/…/mvi/handler/ClickHandler.kt resolves a resource inside the State lambda: 'getString('` (plus `reads copy outside the StringResolver: 'getString(' 2 time(s), 1 through stringResolver`) | topology GREEN |
| 11 | `uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES` on `ExerciseRow`'s Dark preview | `…/ui/components/ExerciseRow.kt contains forbidden platform/lookup API 'android.content'` and `… 'uiMode'` (plus `ExerciseRow.kt preview contract requires '@Preview(name = "Dark", showBackground = true)' exactly once`) | topology GREEN |
| 12 | `LocalContext.current` and `context.appDeps<AllExercisesGraph.Factory>()` in `AllExercisesFeature` | `…/di/AllExercisesFeature.kt contains forbidden platform/lookup API 'LocalContext'`, `… 'appDeps<'`, `remaining Context.appDeps readers are not the exact 8 unported entries; expected={…the eight…}, …` and `factory invocation must occur exactly inside retained Store creation` | topology GREEN |
| 13 | delete `val allExercisesGraphFactory: AllExercisesGraph.Factory` and its KDoc from `AppRootDeps` | `feature:all-exercises: exact root-factory flow requires 'val allExercisesGraphFactory: AllExercisesGraph.Factory' once in app/common/src/main/kotlin/io/github/stslex/workeeper/app/common/di/AppRootDeps.kt` | topology GREEN |
| 14 | `val graph = factory.createAllExercisesGraph()` before `rememberMetroStoreProcessor` | `feature:all-exercises: factory invocation must occur exactly inside retained Store creation` | topology GREEN |
| 15 | a second `(context.applicationContext as AppRootDepsHolder).appRootDeps()` inside the admitted `remember(currentPhase.id) { … }` in `App.kt` | device: `UiAdmissionRaceTest`, 741/741: `admittedGeneration_composesTheRegion_andResolvesItsDependencies` `java.lang.AssertionError: an admitted region resolves the generation's app-scope deps expected:<1> but was:<2>`; the two retired cases passed (fresh XML 2 passed, 1 failed) | 741/741, 3/3 |
| 16 | hoist `val deps = remember(currentPhase.id) { …appRootDeps() }` above `if (admission.granted) {` | device: `UiAdmissionRaceTest`, 741/741: `retiredGeneration_composesNothing_andResolvesNothing` `java.lang.AssertionError: a retired generation must resolve NOTHING — not even the app root deps expected:<0> but was:<1>`; `retirementBetweenPublicationAndFrame_resolvesNothing` `java.lang.AssertionError: the stale generation's region must resolve NOTHING expected:<0> but was:<1>`; the admitted case passed | 741/741, 3/3 |
| 17 | identity helper `= asContribution<AllExercisesGraph.Factory>()` | `feature:all-exercises: extension identities must not bypass AppRootDeps via asContribution` (plus the identity-helper root-fragment line) | topology GREEN |
| 18 | archive-specific; no counterpart (Section 13) | — | — |
| 19 | `ListSurface.kt`: refresh `Loading` → `ListSurface.FIRST_RUN` | 250/250: host `ListSurfaceTest > an unsettled refresh with no rows is loading not empty` and `loading outranks selection and the filter`, each `org.opentest4j.AssertionFailedError: expected: <LOADING> but was: <FIRST_RUN>`; Native the same two identities, `kotlin.AssertionError: Expected <LOADING>, actual <FIRST_RUN>.`; and the scene `Failed to perform isDisplayed check. Reason: Expected exactly '1' node but could not find any node that satisfies: (TestTag = 'AllExercisesColdOpen')`; passed/failed: host 64/2, Native 64/3 | 250/250 |
| 20 | `PagingTailKind.kt`: append `Error` → `PagingTailKind.NONE` | 250/250: host `PagingTailKindTest > a failed page draws the error footer not silence` `expected: <ERROR> but was: <NONE>`, Native `Expected <ERROR>, actual <NONE>.`, and the scene `… could not find any node that satisfies: ((TestTag = 'AllExercisesPagingError') && (hasAnyAncestorThat(TestTag = 'AllExercisesList')))`; host 65/1, Native 65/2 | 250/250 |
| 21 | the scene's `setContent { AppTheme { AllExercisesScreen(…) } }` body → `setContent { }` | 250/250: Native `AllExercisesFeatureSceneIosTest.resourcesPagingBranchesSelectionAndActionsRenderAndDispatch[iosSimulatorArm64]` `Failed to perform isDisplayed check. Reason: Expected exactly '1' node but could not find any node that satisfies: (TestTag = 'AllExercisesColdOpen')`; host 66/66 passed, Native 66/1 | 250/250 |
| 22 | production `AllExercisesScreen.kt`: the selection-bar close `onClick = { consume(Action.Click.OnSelectionExit) },` → `onClick = { },` | 250/250: Native scene `kotlin.AssertionError: Expected <OnSelectionExit>, actual <OnClearTagFilter>.`; host 66/66, Native 66/1 | 250/250 |
| 23 | rename the scene method to `renamedSceneSubstitute` | Gradle GREEN (283/283; all-exercises 67 cases passing), then the oracle: `native gate FAILED (every module was checked, so no verdict is hidden):` and `feature:all-exercises: expected exactly one testcase with classname='io.github.stslex.workeeper.feature.all_exercises.AllExercisesFeatureSceneIosTest' name='resourcesPagingBranchesSelectionAndActionsRenderAndDispatch[iosSimulatorArm64]', found 0; parsed 67 testcase(s): ['io.github.stslex.workeeper.feature.all_exercises.AllExercisesFeatureSceneIosTest.renamedSceneSubstitute[iosSimulatorArm64]', …]`; the other nine modules `ok:` | 283/283 and oracle GREEN |
| 24 | flip (XOR 0xFF) the last byte of `…golden_AllExercisesGoldenTest_rowWeighted_light.png` | `all-exercises blob drift: …rowWeighted_light.png expected=9899c2e088928ede769051c95810050fec5a3670 actual=6e5b5b56a33c21857b9d9e0f219dcea1546c3e10`, `blob-set hash drift: expected sha256=4e71615e…e432 entries=52, actual sha256=1a304a70…22fa entries=52`, `all-PNG mode/blob/path manifest drift: expected sha256=ce590ad5…3f36 entries=528, actual sha256=0a477a83…9b12 entries=528`, and the Paparazzi mode/blob/path drift (456 entries); the path list does not drift | PNG assertion GREEN |
| 25 | compile-valid overlong assertion at `commonTest/…/ui/components/PagingTailKindTest.kt:16` (a `kotlin.test` message argument) | root detekt, alone, 63/63: `> Task :feature:all-exercises:detekt FAILED` and `BUILD FAILED`; 55 fresh reports, 8 findings, all on line 16 of that file: `detekt.MaxLineLength` `:16:1` `Line detected, which is longer than the defined maximum line length in the code style.`, `detekt.MaximumLineLength` `:16:1` `Exceeded max line length (120)`, and six `detekt.ArgumentListWrapping` (`Argument should be on a separate line (unless all arguments can fit a single line)` at columns 22, 46, 61 and 81; `Missing newline before ")"` at 78 and 193); its compile validity is shown by the extra run below | 63/63, 55 reports, 0 findings |
| 26 | `lastTrainedRelativeLabel`: the sub-hour delta divided by `HOUR_MS` instead of `MINUTE_MS` | 250/250: host `AllExercisesUiMapperTest > composeFooterLabel minute granularity for sub-hour deltas` `expected: <last 15m ago> but was: <last 0m ago>`, Native the same identity `Expected <last 15m ago>, actual <last 0m ago>.`, and the scene `Expected <[12 sessions · in 3 trainings · last 4d ago, 1 session · in 1 training · last just now, in 2 trainings · last 2h ago, 5 sessions · last 15m ago, ]>, actual <[…, 5 sessions · last 0m ago, ]>.`; host 65/1, Native 65/2 | 250/250 |
| 27 | `toBlockedArchiveDialog`: `archivedCount.takeIf { it > 0 }` → `archivedCount.takeIf { false }`, so `archivedSummary` is always `null` | 250/250: host `AllExercisesUiMapperTest > toBlockedArchiveDialog truncates trainings and sets archived summary on partial block` `expected: <1 archived> but was: <null>` and `ClickHandlerTest > OnBulkDeleteConfirm opens blocked dialog with archived summary on partial block` `actual value is null ==> expected: not <null>`; Native the same two, `Expected <1 archived>, actual <null>.` and `Expected value to be not null.`; and the scene `Expected <[1 exercise archived, 3 exercises archived, null]>, actual <[null, null, null]>.`; host 64/2, Native 64/3 | 250/250 |
| 28 | `MAX_TRAININGS_SHOWN = 2` → `3` | 250/250: host `AllExercisesUiMapperTest > toBlockedArchiveDialog truncates trainings and sets archived summary on partial block` `expected: <used in Push, Pull +2 more> but was: <used in Push, Pull, Legs +1 more>`, Native `Expected <used in Push, Pull +2 more>, actual <used in Push, Pull, Legs +1 more>.`, and the scene `Expected <[used in Push, Pull +2 more, used in Legs, used in Push, Legs]>, actual <[used in Push, Pull, Legs +1 more, used in Legs, used in Push, Legs]>.`; host 65/1, Native 65/2 | 250/250 |
| 29 | `ShowBulkDeleteSuccess(val archivedCount: Int)` | `feature:all-exercises: src/commonMain/…/mvi/store/AllExercisesStore.kt ShowBulkDeleteSuccess parameters must be exactly ['message: String']; actual=['archivedCount: Int']` | topology GREEN |
| 30 | `ClickHandler` imports CMP's top-level `getString` and calls it for `permanent_delete_success` | `feature:all-exercises: src/commonMain/…/mvi/handler/ClickHandler.kt imports CMP's top-level getString; read copy outside composition through the injected StringResolver` (plus `copy contract requires 'stringResolver.getString(' exactly once; found 0` and `reads copy outside the StringResolver: 'getString(' 1 time(s), 0 through stringResolver`) | topology GREEN |
| 31 | `BackHandler(enabled = processor.state.value.interceptBack) {` → `BackHandler {` | `feature:all-exercises: src/commonMain/…/ui/AllExercisesGraph.kt back contract requires 'BackHandler(enabled = processor.state.value.interceptBack)' exactly once; found 0` | topology GREEN |
| 32 | EN `feature_all_exercises_session_count[one]` `%1$d session` → `%d session` | `compose-resource placeholder is not %N$d or %N$s with N >= 1: feature/all-exercises/src/commonMain/composeResources/values/strings.xml key feature_all_exercises_session_count[one] token '%d'` (plus the EN catalog mismatch) | topology GREEN |
| 33 | no counterpart (Section 13) | — | — |
| 34 | a 67th `@Test`, `a sixty-seventh identity nobody declared`, in `TopBarModeTest` | `feature:all-exercises: exact test identities drifted in src/commonTest/…/ui/components/TopBarModeTest.kt; missing=[], unexpected=['a sixty-seventh identity nobody declared']` | topology GREEN |
| 35 | core:ui:kit's `ComposeStringResolver.getPluralString` always calls the no-argument overload: the `if (formatArgs.isEmpty()) … else …` body → `composeGetPluralString(resource, quantity)` | 277/277: only the scene fails, on Native, at its first filled-catalog assertion: `kotlin.AssertionError: Expected <[Delete ‘Bench’ permanently?, 1 session, 2 sessions, in 1 training, in 2 trainings, last 1m ago, …, used in Push, Pull, +1 more, +2 more]>, actual <[Delete ‘Bench’ permanently?, %1$d session, %1$d sessions, in %1$d training, in %1$d trainings, last %1$dm ago, last %1$dm ago, last %1$dh ago, last %1$dh ago, last %1$dd ago, last %1$dd ago, %1$d selected, %1$d selected, %1$d exercise archived, %1$d exercises archived, %1$d exercise will move to archive. Restore from Settings → Archive., %1$d exercises will move to archive. Restore from Settings → Archive., used in Push, Pull, +%1$d more, +%1$d more]>.` All 18 filled plural values show the raw template, and the two filled strings, which `getString` reads, stay correct; kit host 415/415, kit Native 2/2, all-exercises host 66/66, Native 66/1 | 277/277 |
| 36 | the mapper's `session_count` read drops its format argument: `getPluralString(…, count, count)` → `getPluralString(…, count)` | 250/250: on host and Native the four footer identities with sessions, `composeFooterLabel renders all three segments when present`, `composeFooterLabel hides linked-trainings segment when count is zero`, `composeFooterLabel hides last-trained segment when value is null` and `ExerciseListItemDomain toUi propagates non-zero stats into the model`, each `java.lang.IllegalStateException: a plural read passes its quantity as its one format argument; got []` (Native `kotlin.IllegalStateException: …`); and the scene's footers show the raw template: `Expected <[12 sessions · in 3 trainings · last 4d ago, 1 session · in 1 training · last just now, in 2 trainings · last 2h ago, 5 sessions · last 15m ago, ]>, actual <[%1$d sessions · in 3 trainings · last 4d ago, %1$d session · in 1 training · last just now, in 2 trainings · last 2h ago, %1$d sessions · last 15m ago, ]>.`; host 62/4, Native 62/5 | 250/250 |
| 37 | `AllExercisesScreenTest` reads `feature_all_exercises_blocked_archive_title` in place of `_confirm` (an aliased import) | device: `AllExercisesScreenTest`, 224/224, fresh XML: `blockedArchiveDialogListsBlockingTrainings` `java.lang.IllegalArgumentException: Expected action OnBlockedArchiveDismiss not found. Captured: []` (clicking the title does not dismiss); `pendingFeatureRewrite` skipped as always | 224/224, 1 passed and the documented skip |
| 38 | `ShowPermanentDeleteSuccess` branch of ui/AllExercisesGraph.kt shows `getString(Res.string.feature_all_exercises_permanent_delete_success)` instead of `event.message` | `feature:all-exercises: src/commonMain/…/ui/AllExercisesGraph.kt reads copy outside composition under ui/: 'getString('` | topology GREEN |
| 39 | the days segment hardcoded as `"last ${days}d ago"` in place of its `stringResolver.getPluralString(…)` call | `feature:all-exercises: src/commonMain/…/mvi/mapper/AllExercisesUiMapper.kt copy contract requires 'stringResolver.getPluralString(' exactly seven times; found 6` | topology GREEN |

Control 35's observations: under its mutation `:core:ui:kit:testAndroidHostTest` (415/415) and kit's
Native identities (2/2) stayed GREEN, as Section 13 expects, because kit's catalog holds no plural.
The all-exercises host suite stayed GREEN too (66/66): its mapper identities use the strict fake. The
scene is the only executed guard. It stops at the first failed assertion, its filled catalog, so its
later footer assertion is not reached under this mutation. The footers' raw template is shown under
control 36, whose mapper-side mutation reaches the real resolver with no format argument. Control 35
ran twice. The first run's RED (277/277, the same failing test, the same counts) was logged cut at
400 characters, before its actual value, and its post-GREEN overwrote the XML. It was run again, with
its own pre- and post-GREEN (277/277 each), keeping the RED XML, which the row quotes.

One run goes beyond Section 13 and is not one of its expectations: control 25's mutation scored by
the module's compiled tests instead of detekt. It follows 7.9's control-25 compile-validity run,
which covered the Android host only (7.9 Section 19.8). Here it covered host and Native: 250/250,
host 66/66 and Native 67/67, all passing. The overlong assertion compiles on both targets, so
control 25's RED is detekt's finding, not a compile failure.

Control 39's observation: under its mutation, `:feature:all-exercises:testAndroidHostTest
:feature:all-exercises:iosSimulatorArm64Test` stayed GREEN, 250/250, host 66/66 and Native 67/67
with the scene passing, as Section 13 expects. The strict fake and the EN catalog both produce
`last 4d ago`, so only the copy contract's resolver-call count names the mutation (row 39).

### 19.7 Deviations, spec gaps, and implementation decisions

- **D5 — a test-scope `core:data:database` edge (maintainer decision, 2026-10-13).** Section
  11.1's domain fakes must implement `ExerciseRepository`, whose adhoc-plan members
  (`getAdhocPlans`, `getAdhocPlan`, `setAdhocPlan`) name `PlanSetDataModel` from
  `core:data:database`, and `core:data:exercise` keeps that module implementation-scoped, so the
  type is absent from this module's compile classpath and `setAdhocPlan`'s parameter cannot be
  written (Section 19.1, measured). The precedent is `feature:plan-editor`, whose `commonMain`
  declares the same edge and whose gate contract pins it. The maintainer approved exactly one
  line, `implementation(project(":core:data:database"))` in `commonTest.dependencies`, and no
  other build change; `core:data:exercise` is untouched. For this line only, D5 supersedes Section
  10's `commonTest` block, the dependency facts of Section 3.5 and the build_fragments row of
  Section 12.1, and it corrects Section 11.1's member count: `ExerciseRepository` has 34 abstract
  members (33 functions and `val exercises`), not 33. `ALL_EXERCISES_ENTRY.build_fragments` carries
  the line, the build file's D5 comment does not repeat it, and `check_feature_entry` is unchanged.
  Sections 0–18 stay the historical specification. The leaky implementation edge in
  `core:data:exercise` (a public interface naming an implementation-scoped type) is reported as a
  follow-up, not fixed here.
- **Commit-1 topology delta (7.8 G1).** Commit 1 carries only `MODULES["feature:all-exercises"]`
  (the 101 `src` paths, built from Sections 3.1–3.3 and 9.1, equal to the tree) and the reader
  removed from `EXPECTED_APP_DEPS_READERS`; every all-exercises contract check is in commit 2.
- **The `FEATURE_ENTRIES` line.** Section 12.1 lists the tuple among the additive changes; it is
  the topology script's only removed line, replaced by the three-entry tuple. `check_feature_entry`,
  `ARCHIVE_ENTRY`, `ALL_TRAININGS_ENTRY` and both all-trainings checks are byte-identical.
- **Transcription.** `ALL_EXERCISES_ENTRY` and the oracle's 67 tuples were transcribed from this
  specification's tables by a script that reads only this document, never the files they check.
  An independent audit with its own parser of this text, which read nothing under
  `feature/all-exercises` or `app/`, reported `AUDIT VERDICT: EQUAL (12 checks)`. Its 38 synthetic
  probes showed that every copy-contract and back-contract rule fails when broken. A perturbation
  self-check found exactly the two planted differences. Before the commit, the transcribed entry
  was also GREEN against the commit-1 tree.
- **Copy contract details.** Every count runs over comment-stripped source, as Section 12.1 says.
  Fragments must occur exactly the stated number of times, and an existing but empty file is
  counted, not skipped (7.9's F1). One guard goes beyond Section 12.1: the contract fails if its
  `mvi/` or `ui/` walk finds no file. The back contract counts raw source, as
  `all_trainings_back_contract` does, because Section 12.1's comment-stripping sentence covers the
  copy contract only.
- **Rename similarity.** Every move is a `git mv`. Four files fall below git's default 50%
  similarity because the specification rewrites them, so default `git diff -M` and
  `git log --follow` show them as delete/add pairs: `ui/components/ExercisesEmptyState.kt` (the
  Section 5.3 imports and the preview split in a 42-line file), `domain/AllExercisesInteractorImplTest.kt`
  (two in-file repository fakes), `mvi/handler/NavigationHandlerTest.kt` (an in-file navigator)
  and `mvi/handler/PagingHandlerTest.kt` (three in-file fakes). All four pair at `-M10%`. 7.9 shows
  the same pattern.
- **Build file.** With comments removed it equals Section 10's block plus D5's line. The module's
  own history comments (the Metro and Paparazzi plugin comments, the `includeJavax` comment) moved
  verbatim; 7.9's two `api` rationale comments and the Compose-BOM and test-utils GUARD comments
  were added, adapted. None holds a dependency fragment or a forbidden build token.
- **Strict resolver fakes.** Each fake answers by `when (resource)` on the generated
  `Res.string.…` / `Res.plurals.…` objects and throws on any other resource. It checks arguments:
  no format argument for `footer_separator`, `last_trained_just_now` and
  `permanent_delete_success`; exactly one for `blocked_archive_used_in_format`; and
  `formatArgs == [quantity]` for every plural. The return values are Section 11.1's table. The two
  `stubBlockedArchiveStrings()` calls disappeared with the strict mock they configured: the fake
  always answers those three resources.
- **Recorded launches.** `ClickHandlerTest`'s store fake records each `launch` as two closures,
  both built inside the generic `launch<T>` and sharing one `MutableList<T>`. One runs the action
  and the other runs `onSuccess`, so the three bulk-confirm identities keep today's order (action,
  then the `bulkArchive` assertion, then `onSuccess`) with no cast. `OnConfirmPermanentDelete with
  pending…` records its launch and never runs it.
- **Fail-fast members.** Every fake member that no identity reaches throws. That includes the
  interactor's suspend operations, where 7.9 had kept the relaxed defaults: Section 11.1 says
  "members no identity reaches fail fast". `PagingHandlerTest`'s store records `Flow.launch` and
  never runs it; `state` is not read, because `PagingUiState` builds its flow lazily.
- **Inherited assertions under `kotlin.test` contracts.** `assertTrue(x is T)` and `assertNotNull(x)`
  carry contracts, so the inherited `(… as Event.Haptic)`, `(result as ArchiveResult.Blocked)`,
  `pending!!` and `dialog?.…` now draw "No cast needed", "Unnecessary non-null assertion" and
  "Unnecessary safe call" warnings. Section 11.1 allows no assertion change, and a warning is not a
  suppression, so these lines are kept.
- **MockK verifications became recorded-state assertions**, as in 7.9: `verify { sendEvent(capture) }`
  became the last recorded event; `verify(exactly = 0)` became an empty list or a zero count;
  `verify(exactly = 1) { sendEvent(ofType<…>()) }` became a count of that event type; and
  `coVerify` became `assertContains` on the recorded calls.
- **Identity helper shape.** Section 6 writes the `allExercises()` helper on one line; it is split
  over two lines like 7.9's. The gate compares whitespace-compacted source.
- **Native scene decisions** (none changes a gate or an identity). Step 1 reads all 48 catalog
  values as stored (30 strings and both EN quantities of 9 plurals) and the 20
  placeholder-bearing ones filled (18 plural quantities, `permanent_delete_title` with `"Bench"`,
  `blocked_archive_used_in_format` with `"Push, Pull"`); this is a superset of both readings of
  "each is read twice". Its expected values were transcribed mechanically from Section 3.4.
  Step 2's fixtures are Section 11.4's five rows at `NOW = 1_720_000_000_000L`; step 4's rows are
  those models, and the scene asserts every row's name and meta line. The kit's cancel label is read
  through the same resolver. The permanent-delete dialog uses a fixture uuid. The 14 dispatched
  actions follow Section 11.4's table order.
- **Device result path.** The module's device results land under
  `build/outputs/androidTest-results/connected/` (7.9 recorded `connected/androidMain` for
  all-trainings). The run prints `Starting 2 tests` and then `Finished 3 tests` for one ignored and
  one executed method; the fresh XML is authoritative and holds exactly the two cases.
- **Re-pointed tech-debt anchor.** Row 456's old anchor `PagingHandler.kt:49-52` already pointed at
  `invoke`'s `when` at the base. At the new path it reads `:56-60`, the `updateStateImmediate`
  mapping block it describes.
- **Observations.** The compile keeps three inherited "only one @Inject-annotated constructor"
  notes (`ClickHandler`, `NavigationHandler`, `PagingHandler`) and the expected portable-BackHandler
  deprecation ("Use NavigationEventHandler instead").

### 19.8 Rejected runs, local limitations, and the remote evidence boundary

Runs that did not measure what they appear to were excluded, and their proof was rerun fresh:

- The first attempt at the early Native gate died eight seconds in with
  `Gradle build daemon has been stopped: stop command received`. Another checkout on this host had
  run `./gradlew --stop`. The attempt wrote no XML and was rerun at once. The gate wrapper then
  learned to retry such a run. Its foreign-client check had matched the wrapper's main class, which
  never appears on a `java -jar gradle-wrapper.jar` command line; it was corrected to match the jar
  and to ignore clients on their own daemon registry, which neither stop nor are stopped by this
  checkout's `--stop`. No accepted gate was affected.
- Baseline 2's XML was overwritten by baseline 3's Paparazzi run before its names were read.
  Baseline 2 was retaken (173/173, 66 exact, archived at once); every later gate copies its XML aside
  immediately.
- Control 35's first run is described with its row (Section 19.6). Its RED was logged cut off
  before the actual value, so the control was run again with the RED XML kept.
- The first launch of the repository gates was interrupted inside `assembleDebug lintDebug
  testDebugUnitTest` when the session that hosted it ended. Its `./gradlew clean` had completed, but
  no gate result was accepted from it. The gates were rerun from a fresh `./gradlew clean`
  (Section 19.5).
- In the rerun, the device wait first required that no Gradle client of any other checkout run at
  all. Before the Smoke suite it found its gap after 56 polls (about 18 minutes). Before the
  Regression suite it found none in 540 polls (3 hours), because the other checkout ran host-only
  builds and tests back to back, and the wait gave up without starting Gradle. No result came from
  that attempt. The wait was then narrowed to what can reach the device, a device-capable foreign
  task or instrumentation on the serial, and the Regression suite ran alone on the same committed
  tree (Section 19.5).

Local limitations:

- The ten iosArm64 device test binaries are linked, never run, by design: there is no device.
- Section 5.6's inherited locale property is accepted, not tested.
- `ComposeStringResolver.getPluralString` has no Android-host route (D2, Section 11.5). Its
  delegation runs on Android in production and in the app journeys whenever a row has stats, with
  no text assertion; the RU plural forms are pinned statically by the topology gate's catalog check
  only.
- The mockup shell gate (`--base "$(git merge-base origin/dev HEAD)" -v`, base `97c03dfe1`,
  Homebrew Python 3.14.8) passes checks 1–6, 9 and 10. No headless browser is on `PATH`, so its
  precondition row fails and checks 7 and 8 are `UNMEASURED` (`the probe stopped at 'no-browser'`).
  The permanent known negative `--target f52462c7` fails checks 6, 9 and 10, as required. Checks 7
  and 8 are unverified locally; the remote `Mockup Appearance Gate` is their authority. This branch
  changes neither `documentation/mockups/pass2d.html` nor `AppColors.kt`.
- The Gradle-scored controls ran the Native oracle (control 23) under Homebrew Python 3.14.8 only.
  Commit 2's own oracle gate ran it under both Pythons, with byte-identical output (Section 19.5).
- The repository gates shared the host with another checkout's builds. No foreign client was
  device-capable during either device suite (Section 19.5), and every gate passed under that load.

This documentation commit must exist before its SHA, the push, GitHub's signature verification of
the three commits, the required contexts on the final head, the CI run and job IDs, and the review
can exist. Those facts, and every review finding's classification, are recorded in the PR, not
predeclared here.

### 19.9 Documentation boundary

This commit updates the canonical facts the implementation made stale, in the places 7.9's
`3a941f1f` updated for all-trainings and the four testing.md places Section 9.2 names:

- `architecture.md`: all-exercises joins archive and all-trainings as a plain-shape
  factory-resolution exception; `AppGraph.allExercisesGraphFactory` implements its `AppRootDeps`
  accessor; the feature/dialog Context readers fall from 9 to 8.
- `ci-cd.md`: the `KMP iOS kit smoke` row, the KMP-shaped alias list, the Native job's module list,
  the all-exercises validator (66 portable tuples plus the scene, 67 exact), ten uploaded result
  directories, and ten linked device binaries (`N/10 linked`).
- `testing.md`: a "Shared all-exercises feature" subsection (the 66 identities, the strict resolver
  fakes, D5, the scene, the D2 limitation); "Shared string resolver" now says that the plural
  delegation first runs on Native in that scene and that the mapper identities use a fake; the
  `AppGraph.stringResolver` bullet says the accessor still has no production reader and that
  all-exercises' handlers receive the binding by injection; the device-test table row names the one
  existing file under `src/androidDeviceTest`.
- `tech-debt.md`: the two links into old all-exercises paths re-pointed (rows 456 and 539 at the
  base), with the line reference corrected to `:56-60`.

Left untouched on purpose (Section 9.2), each re-located on this tree:

- `documentation/features.md` lines 79, 92 and 97–99;
- `.claude/skills/*` (D4): `add-feature`, `compose-state-discipline`, `mvi-dialog-state` and
  `refactor-with-mvi-rules` teach `R` / `ResourceWrapper` copy, and `write-handler-test` keeps its
  stale all-exercises line;
- `documentation/kmp-migration-assessment.md`, `documentation/research/*` and the historical
  feature specs (`exercises.md`, `all-exercises-delta.md`, `archive-delta.md`, `exercise-image.md`,
  `trainings.md`, `v2.*`, `wear-lifecycle-ui.md`, `kmp-phase-5-evidence/*`);
- `kmp-string-resolver.md`'s R5 sentence (a complete specification; `testing.md` carries the
  correction);
- `tech-debt.md`'s "Resolved" list (line 797);
- `graphify-out/`.

Maintainer decision (2026-10-14): Section 9.2 omitted four `testing.md` places that the move made
stale, so this PR fixes them in a separate documentation commit. It adds all-exercises where 7.9's
`3a941f1f` added all-trainings: the Native command block under "Running tests"
(`:feature:all-exercises:iosSimulatorArm64Test`) and the device-link block
(`:feature:all-exercises:linkDebugTestIosArm64`, whose comment now reads `10/10`). The visual-gate
paragraph's two module lists now name all-exercises among the KMP modules with goldens in
`src/androidHostTest`, no longer among the classic ones in `src/test`. The sentence naming the
KMP-shaped modules that both alias spellings cover now includes `:feature:all-exercises`. Every
module count in those places reads ten. Nothing else changes.
