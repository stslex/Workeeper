# KMP Phase 7.9 — feature:all-trainings becomes a shared feature entry

**Status:** SPECIFIED — IMPLEMENTATION REQUIRES A SEPARATE MAINTAINER GO (Section 18)

**Discovery baseline:** dev @ 38982c2e8c076c0a7578dec6a63ec233efd643f7 (2026-10-07, merge of #319).
Re-checked at be771fefe (2026-10-07): the only change since is
`documentation/feature-specs/wear-live-sync.md`, outside this phase.

**Template:** [Phase 7.8](kmp-phase-7-8-archive-feature.md). This document is a delta. Every rule of
7.8 that this document does not change applies unchanged, read with the substitutions of
Section 0.3.

**Prerequisites (all three merged into dev before implementation entry):**

- **PR-N** — Native-legal test names in the nine remaining navigation-entry features (decision D3).
- **PR-G** — the data-driven feature-entry contract in
  `.github/scripts/assert_kmp_ui_source_topology.py` (decision D4).
- **PR-L** — `DomainLayerPurityRule`, `UiLayerNoDataRule` and detekt's `TooManyFunctions` treat the
  KMP test source sets as tests (decision D5).

**Discovery date:** 2026-10-07

---

## 0. Authority, decisions, substitutions

### 0.1 Authority order

1. live origin/dev at the implementation base;
2. PR-N, PR-G and PR-L as merged;
3. AGENTS.md, documentation/architecture.md, testing.md, ci-cd.md, compose-state-discipline.md;
4. the Phase 7.8 specification, including its Section 19 decisions P1, P1a and G1–G4;
5. the Phase 7.7 specification for the portable BackHandler (its Sections 4 and 5.3);
6. the checked-in source, resources, tests, PNGs and CI oracles at the implementation base.

### 0.2 Stable rules

7.8 Section 0.2 applies: the positive-gate flags
`--rerun-tasks --no-build-cache --no-configuration-cache`, quoted `N actionable tasks: N executed`,
signed and GitHub-verified commits, no merge by the implementer. The required contexts are whatever
the ruleset lists on the final head (7.8's list predates `Release bundle identity`); record them.

Suppressions: **no new suppression of any kind.** The four inherited production suppressions of
Section 3.1 stay byte-semantically unchanged. Phase 7.9 needs no Native test-name suppression,
because PR-N removed every Native-illegal name from the target's tests.

### 0.3 Substitutions against 7.8

| 7.8 | 7.9 |
| --- | --- |
| `feature:archive`, `feature/archive` | `feature:all-trainings`, `feature/all-trainings` |
| package `io.github.stslex.workeeper.feature.archive` | `io.github.stslex.workeeper.feature.all_trainings` |
| resource package `…feature.archive.resources` | `io.github.stslex.workeeper.feature.all_trainings.resources` |
| `ArchiveFeature`, `ArchiveGraph`, `archiveGraph` | `AllTrainingsFeature`, `AllTrainingsGraph`, `allTrainingsGraph` |
| `archiveGraphFactory`, `createArchiveGraph`, `archiveStore` | `allTrainingsGraphFactory`, `createAllTrainingsGraph`, `allTrainingsStore` |
| `ArchiveStoreImpl`, `ArchiveExtensionIdentityTest` | `AllTrainingsStoreImpl`, `AllTrainingsExtensionIdentityTest` |
| route `Screen.Archive` | `Screen.BottomBar.AllTrainings` |
| bespoke `check_archive_feature_contract` | one `FeatureEntry` record (PR-G) — Section 12.1 |
| eight-module Native command | nine modules (Sections 12.1, 12.3) |

### 0.4 Decisions locked by the maintainer

| Id | Decision |
| --- | --- |
| D1 | The bulk-archive snackbar copy is resolved in the graph from a semantic event (Section 5.3). |
| D2 | Status and relative-time copy is resolved by the mapper through Compose resources inside the existing `PagingData.map` (Section 5.2). |
| D3 | Native-illegal test names are renamed once, before this phase, in a test-only PR (PR-N). |
| D4 | The topology checker becomes data-driven first (PR-G); this phase adds all-trainings as data. |
| D5 | Lint rules learn the KMP test source sets first (PR-L): without it `DomainLayerPurityRule` judges the ported `domain/AllTrainingsInteractorImplTest.kt` as production domain code (its exemption is `/src/test/` or `/src/androidTest/` only). Proposed 2026-10-08 after review; carried by the maintainer's GO on the pickup. |

## 1. Bounded exit claim

The implementation may prove only this:

- feature:all-trainings applies `convention.kmpComposeLibrary` and retains Metro and Paparazzi;
- all 31 production Kotlin files compile from commonMain for Android, iosSimulatorArm64 and
  iosArm64;
- the exact private 30-identifier EN/RU catalog lives in commonMain Compose Resources, changed only
  by the 24 positional placeholders of decision P1 (Section 5.1);
- the paging contract and every visible Android behaviour are unchanged, except the inherited
  Compose-resources locale property of Section 5.7;
- `ResourceWrapper` leaves the feature entirely (Sections 5.2, 5.3);
- 9 portable suites with 49 exact identities execute on Android host and on Native;
- the golden suite executes as 50 Android-host Paparazzi cases (25 methods × light/dark) against
  the same PNG blobs;
- the one inherited Android device placeholder stays the same documented skip;
- one deterministic iOS production scene proves resources, mapper copy, snackbar copy, every list
  surface, selection, the confirm dialog and action dispatch;
- the Metro graph is reached through the explicit generation-owned factory, never `LocalContext`
  or `Context.appDeps`; both extension identities use the accessor; the one app device test that
  composes the graph directly passes the factory;
- the topology gate checks the entry through one `FeatureEntry`; `Context.appDeps` readers drop
  from 10 to 9;
- Android remains releasable with every repository PNG blob unchanged.

This is a source-set, dependency, resource, test and composition-root migration. It is not
permission to redesign the screen, change selection, filtering, archive or paging behaviour, add an
iOS app, or migrate another feature.

## 2. Candidate selection — fresh census

| Feature | Prod files | Prod lines | Unit files | Unit lines | EN string keys | PNGs |
| --- | ---: | ---: | ---: | ---: | ---: | ---: |
| **all-trainings** | 31 | **1,795** | 10 | **1,041** | 26 | 50 |
| all-exercises | 34 | 2,062 | 11 | 1,471 | 30 | 52 |
| past-session | 29 | 2,378 | 8 | 2,067 | 20 | 30 |
| home | 39 | 3,085 | 15 | 1,924 | 35 | 42 |
| exercise-chart | 41 | 3,214 | 10 | 2,314 | 31 | 30 |
| settings | 51 | 3,326 | 12 | 2,638 | 65 | 12 |
| single-training | 39 | 3,635 | 6 | 1,582 | 39 | 12 |
| exercise | 46 | 4,141 | 13 | 2,890 | 53 | 48 |
| live-workout | 59 | 6,666 | 27 | 7,611 | 96 | 60 |

All-trainings is smallest in production lines (1,795) and unit lines (1,041). Past-session has fewer
production files (29), unit files (8) and string keys (20), and single-training fewer unit files (6),
but each is larger by lines: 1.3× and 2.0× in production, 2.0× and 1.5× in unit tests. Every module
all-trainings depends on (core:core, core:ui:kit, core:ui:mvi, core:ui:navigation,
core:data:exercise) is already KMP, and every data-layer and kit symbol it uses is declared in
commonMain. The module last changed on 2026-08-27 (`8f2c2aec`).

## 3. Measured baseline

Static measurements at the discovery baseline. Test names are given as PR-N leaves them.

### 3.1 Production manifest

All 31 files move from `src/main/kotlin/io/github/stslex/workeeper/feature/all_trainings/` to
`src/commonMain/kotlin/io/github/stslex/workeeper/feature/all_trainings/` with unchanged relative
paths:

| File | Lines |
| --- | ---: |
| di/AllTrainingsFeature.kt | 34 |
| di/AllTrainingsGraph.kt | 37 |
| di/AllTrainingsHandlerStore.kt | 9 |
| di/AllTrainingsHandlerStoreImpl.kt | 14 |
| di/AllTrainingsScope.kt | 8 |
| domain/AllTrainingsInteractor.kt | 27 |
| domain/AllTrainingsInteractorImpl.kt | 67 |
| domain/mapper/AllTrainingsDomainMapper.kt | 30 |
| domain/model/BulkArchiveResult.kt | 7 |
| domain/model/TagDomain.kt | 7 |
| domain/model/TrainingListItemDomain.kt | 13 |
| mvi/handler/ClickHandler.kt | 167 |
| mvi/handler/NavigationHandler.kt | 26 |
| mvi/handler/PagingHandler.kt | 74 |
| mvi/mapper/TagUiMapper.kt | 10 |
| mvi/mapper/TrainingListItemMapper.kt | 74 |
| mvi/model/TagUiModel.kt | 10 |
| mvi/model/TrainingListItemUi.kt | 15 |
| mvi/store/AllTrainingsStore.kt | 138 |
| mvi/store/AllTrainingsStoreImpl.kt | 58 |
| ui/AllTrainingsGraph.kt | 43 |
| ui/AllTrainingsScreen.kt | 363 |
| ui/components/ArrivedEmptyStates.kt | 113 |
| ui/components/ListSurface.kt | 58 |
| ui/components/PagingTailKind.kt | 20 |
| ui/components/PagingTails.kt | 42 |
| ui/components/TagFilterRow.kt | 78 |
| ui/components/TopBarMode.kt | 16 |
| ui/components/TrailingSlotKind.kt | 21 |
| ui/components/TrainingRow.kt | 169 |
| ui/components/TrainingsEmptyState.kt | 47 |
| **31 files** | **1,795** |

The four inherited production suppressions, unchanged by this phase:

- `@Suppress("UNCHECKED_CAST")` — di/AllTrainingsFeature.kt (the processor cast);
- `@Suppress("TooManyFunctions")` — mvi/handler/ClickHandler.kt;
- `@Suppress("LongMethod")` — ui/AllTrainingsGraph.kt;
- `@Suppress("UNCHECKED_CAST")` — ui/AllTrainingsScreen.kt (the `LazyPagingItems` cast).

Android coupling in production: `R.*` in seven files — five UI files through Android
`stringResource`/`pluralStringResource`, and `ClickHandler` and `TrainingListItemMapper` through
`ResourceWrapper.getString/getQuantityString` for feature copy in
`TrainingListItemMapper` and `ClickHandler`; `System.currentTimeMillis()` in
`TrainingListItemMapper.toUi`; `androidx.activity.compose.BackHandler` in ui/AllTrainingsGraph.kt;
`LocalContext` with `appDeps` in di/AllTrainingsFeature.kt; `@VisibleForTesting` on
`AllTrainingsStoreImpl.NAME`; `uiMode` in four previews.

### 3.2 Portable manifest — 9 suites, 49 identities

Each file moves from `src/test/kotlin/…/feature/all_trainings/` to
`src/commonTest/kotlin/…/feature/all_trainings/` with its relative path.

**domain/AllTrainingsInteractorImplTest.kt — 3**

- archiveTrainings delegates to repository bulkArchive
- deleteTrainings returns target count and delegates
- canPermanentlyDelete delegates to repository

**mvi/handler/ClickHandlerTest.kt — 18**

- OnTrainingClick emits haptic and navigates to OpenDetail
- OnFabClick emits haptic and navigates to OpenCreate
- OnFabClick with selection fires no haptic and sets pendingBulkDelete
- OnTagFilterToggle adds tag when not selected
- OnTagFilterToggle removes tag when already selected
- OnSelectionExit clears selection mode
- OnBulkDeleteConfirm calls archiveTrainings and clears selection on success
- OnBulkDeleteDismiss clears pending delete
- entering selection by long press fires LongPress
- toggling an item inside selection fires ContextClick not LongPress
- untoggling an item inside selection fires ContextClick
- long press inside selection fires ContextClick not a second LongPress
- toggling a tag filter fires no haptic
- confirmed bulk archive fires Confirm
- OnClearTagFilter empties the whole filter in one act
- OnClearTagFilter fires no haptic
- OnClearTagFilter on an already-empty filter changes nothing
- OnEmptyCreate opens create and fires no haptic

**mvi/handler/NavigationHandlerTest.kt — 2**

- OpenDetail navigates to Screen Training with uuid
- OpenCreate navigates to Screen Training with null uuid

**mvi/store/StartBlankGateTest.kt — 3**

- no workout running — the drawn pair is whole
- a workout is running — the blank-start CTA withdraws
- before the first emission the CTA is withheld not offered

**ui/AllTrainingsClearanceTest.kt — 2**

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

Suite distribution: 3/18/2/3/2/9/4/4/4. MockK is used by the first three suites; JUnit 5 by all
nine. No test uses `paging-testing`.

Two StartBlankGateTest identities contain U+2014 (`—`). Kotlin/Native's identifier checker allows
it, but no non-ASCII test name has run on Native in this repository before (no `commonTest` or
`iosTest` name carries one at the baseline), so the first Native run measures that the XML reports
both names byte-exactly (Section 14).

### 3.3 Golden and device ownership

`golden/AllTrainingsGoldenTest.kt` has 25 methods parameterized over `GoldenTheme`, in this order:

    rowPlain, rowLongName, rowClamped, rowActive, rowSelected, rowActiveSelected, tagFilterBand,
    emptyState, rowUnselectedInSelection, pagingLoading, pagingError, confirmDialogContent,
    screenList, screenSelection, screenFirstRunEmpty, filteredEmpty, selectionEmptyFiltered,
    selectionEmptyUnfiltered, coldOpenLoading, coldOpenError, screenColdOpen, screenRefreshError,
    screenFilteredEmpty, screenSelectionEmpty, screenSelectionEmptyUnfiltered

That is 50 render cases and 50 PNGs named
`io.github.stslex.workeeper.feature.all_trainings.golden_AllTrainingsGoldenTest_<method>_<dark|light>.png`,
50 distinct blobs, all mode 100644. Fixtures build `TrainingListItemUi` directly with fixed
`statusLabel` strings, so neither D1 nor D2 reaches a golden. The harness renders in EN.

The device identity is `AllTrainingsScreenTest.pendingFeatureRewrite`: `@Smoke` on the class,
`@Ignore("Awaiting feature rewrite — see GH issue #93 for coverage scope.")` on the method. It
remains one documented skip.

### 3.4 Exact feature-local resource catalog

Each locale owns 26 strings and 4 plurals, 30 identifiers in this file order. Values are shown as
they must be at exit: decision P1 replaces the bare `%d` of all 24 plural items with `%1$d` and
changes nothing else. Strings already use positional placeholders.

| # | Identifier | EN | RU |
| ---: | --- | --- | --- |
| 1 | feature_all_trainings_title | Trainings | Тренировки |
| 2 | feature_all_trainings_empty_headline | Your trainings will appear here | Здесь появятся тренировки |
| 3 | feature_all_trainings_empty_supporting | Build a template in advance, or start an empty one and add exercises as you go. | Собери шаблон заранее или начни пустую и добавляй упражнения по ходу. |
| 4 | feature_all_trainings_fab_create | Create training | Создать тренировку |
| 5 | feature_all_trainings_status_in_progress_format | in progress · started %1$s ago | в процессе · началась %1$s назад |
| 6 | feature_all_trainings_status_last_format | last: %1$s | последняя: %1$s |
| 7 | feature_all_trainings_status_never | never trained | ещё не было |
| 8 | feature_all_trainings_relative_just_now | just now | только что |
| 9 | feature_all_trainings_relative_minutes_format | %1$dm | %1$d мин |
| 10 | feature_all_trainings_relative_hours_format | %1$dh | %1$d ч |
| 11 | feature_all_trainings_relative_days_format | %1$dd | %1$d дн |
| 12 | feature_all_trainings_exercise_count | plural, below | plural, below |
| 13 | feature_all_trainings_selection_close | Close selection | Закрыть выбор |
| 14 | feature_all_trainings_selected_count | plural, below | plural, below |
| 15 | feature_all_trainings_bulk_archive | Archive | В архив |
| 16 | feature_all_trainings_bulk_archive_success | plural, below | plural, below |
| 17 | feature_all_trainings_bulk_archive_partial_format | Archived %1$d, blocked: %2$s | В архиве: %1$d, не получилось: %2$s |
| 18 | feature_all_trainings_bulk_archive_confirm_title | Archive selected? | Архивировать выбранные? |
| 19 | feature_all_trainings_bulk_archive_confirm_body | plural, below | plural, below |
| 20 | feature_all_trainings_bulk_archive_impact | Reversible · history preserved | Обратимо · история сохранится |
| 21 | feature_all_trainings_paging_loading | Loading | Загружаю |
| 22 | feature_all_trainings_paging_error | Couldn’t load more | Не удалось загрузить дальше |
| 23 | feature_all_trainings_paging_retry | Retry | Повторить |
| 24 | feature_all_trainings_empty_create | Create a training | Создать тренировку |
| 25 | feature_all_trainings_empty_start_blank | Start an empty training | Начать пустую тренировку |
| 26 | feature_all_trainings_filtered_empty_headline | Nothing matches these tags | По этим тегам ничего нет |
| 27 | feature_all_trainings_filtered_empty_clear | Clear filter | Сбросить фильтр |
| 28 | feature_all_trainings_selection_empty_headline | Nothing here, but your selection is kept | Здесь пусто, но выбор цел |
| 29 | feature_all_trainings_selection_empty_supporting | Your selection stays until you leave selection mode. | Отметки останутся, пока не выйдешь из режима. |
| 30 | feature_all_trainings_refresh_error | Couldn’t load the list | Не удалось загрузить список |

| Plural | EN one / other | RU one / few / many / other |
| --- | --- | --- |
| exercise_count | `%1$d exercise` / `%1$d exercises` | `%1$d упражнение` / `%1$d упражнения` / `%1$d упражнений` / `%1$d упражнения` |
| selected_count | `%1$d selected` / `%1$d selected` | `выбрана %1$d` / `выбрано %1$d` / `выбрано %1$d` / `выбрано %1$d` |
| bulk_archive_success | `%1$d training archived` / `%1$d trainings archived` | `%1$d тренировка в архиве` / `%1$d тренировки в архиве` / `%1$d тренировок в архиве` / `%1$d тренировки в архиве` |
| bulk_archive_confirm_body | `%1$d training will move to archive. Restore from Settings → Archive.` / `%1$d trainings will move to archive. Restore from Settings → Archive.` | `%1$d тренировка будет перенесена в архив. Восстановить можно в Настройках → Архив.` / `%1$d тренировки будут перенесены в архив. Восстановить можно в Настройках → Архив.` / `%1$d тренировок будут перенесены в архив. Восстановить можно в Настройках → Архив.` / `%1$d тренировки будут перенесены в архив. Восстановить можно в Настройках → Архив.` |

(Plural identifiers carry the `feature_all_trainings_` prefix.) The XML comments of both files and
the RU root's `xmlns:tools` / `tools:ignore="DuplicateStrings"` move unchanged. That root attribute
is inert in Compose resources: at CMP 1.11.1 `PrepareComposeResources.kt` parses with a
non-namespace-aware `DocumentBuilder` and reads only the children of `<resources>` that carry
attributes, by `name` (and `quantity` on plural items); root attributes and comments are never read.
core:ui:kit's catalogs already carry `tools:ignore` on elements; root placement is new and is
covered by this reading. No path outside the module consumes these identifiers; the generated
`Res` stays private.

Goldens render `exercise_count` (rows) and `selected_count` (selection bar), so the P1 change is
pixel-checked for those two; `bulk_archive_success` and `bulk_archive_confirm_body` reach no
golden and are asserted by the Native scene (Section 11.4).

### 3.5 Declared dependencies

| Today (`convention.composeLibrary`) | At exit |
| --- | --- |
| implementation core:core | commonMain implementation (dispatcher qualifier, `AppScope`, `AppScopeLifetime`; no public signature exposes them, as in archive) |
| implementation core:ui:kit | commonMain **api** — State carries the kit's `PagingUiState` |
| implementation core:ui:mvi | commonMain **api** — the Store contracts are public |
| implementation core:ui:navigation | commonMain **api** — `allTrainingsGraph` extends `NavGraphScope` |
| implementation core:data:exercise | commonMain implementation |
| testImplementation paging-testing | removed — no test imports it |
| testImplementation kotlin(test) | commonTest and iosTest |
| testImplementation golden-harness | androidHostTestImplementation |
| androidTest bundle, Compose UI test JUnit4, test-utils; debugImplementation ui-test-manifest | androidDeviceTestImplementation, with the Compose BOM (7.8 Section 10) |

Visibility the source surface requires, added explicitly: `api(libs.cmp.ui)` (Modifier,
HapticFeedbackType in Event), `api(libs.androidx.paging.common)` (PagingData in the public
interactor and State), `api(libs.coroutines.core)` (Flow in the public interactor),
`api(libs.kotlinx.collections.immutable)` (ImmutableList/ImmutableSet in public State and UI
models — unlike archive, where no public signature carried them),
`implementation(libs.androidx.compose.paging)`, `implementation(libs.cmp.animation)`
(AnimatedContent, fade specs; declared explicitly like image-viewer and kit, since no KMP module in
the repository yet proves the transitive edge), `implementation(libs.cmp.material.icons.core)`
(`Icons.Default.Close` only). commonTest adds `libs.coroutine.test` (`runTest` in two suites);
iosTest adds `libs.cmp.ui.test`. No catalog or version change.

### 3.6 External consumers and root identity

| Surface | Ownership |
| --- | --- |
| Module edges | app/app `implementation` (stays); app/common `implementation` → `api` |
| Navigation registration | app/common `AppNavigationHost` calls `allTrainingsGraph`, tag `AllTrainingsGraph` |
| Graph aggregation | app/app `AppGraph` (gains the accessor) |
| Extension identities | app/app test `AllTrainingsExtensionIdentityTest`: 2 identities, through `asContribution` today |
| Direct composition | app/app androidTest `AllTrainingsExtensionDbVisibilityTest` (@Regression) composes `allTrainingsGraph()` inside `TestSingleScreenHost` |
| Device journeys by tag | `RouteReachabilityTest`, `ApplicationBottomBarTest`, `UiGenerationSwapTest`, `AppRuntimeUiHandshakeDeviceTest` |
| Route | `Screen.BottomBar.AllTrainings`, core:ui:navigation commonMain, unchanged |

architecture.md names `feature/all-trainings/ui/AllTrainingsGraph.kt` and
`mvi/handler/NavigationHandler.kt` as the canonical navigation reference; both stay the reference
after the move.

### 3.7 Context readers and PNG integrity

Ten executable `Context.appDeps` readers exist: nine navigation entries and app-dialogs. Removing
all-trainings' leaves nine.

PNG manifests at the discovery baseline, for reference (method of 7.7/7.8,
`git ls-files -s … | shasum -a 256`):

| Manifest | Entries | Hash |
| --- | ---: | --- |
| Paparazzi mode/blob/path | 456, 13 owners | 616a0ea5dcc85256a984bb710eb08a8b1110f1f41b1ae3a6e488909db8f65446 |
| Paparazzi path list | 456 | 4782c53805144b129de6ccf933edadcdddc084f46c16fcc3d33de70d6037e733 |
| All repository PNGs, mode/blob/path | 528 | 06a1f2adb9b38b70463d5b9b46db881059c594cf879d98f13acfb2e78cfb4d39 |
| all-trainings blob set (sorted blob IDs) | 50 | 0d5f7c0ef86b9dc9052433f1e9f908fb8ebdc071cf63673b26388955e81dd3b0 |

If only the 50 paths move to `src/androidHostTest/snapshots/images/` the projections are
46f2f70f052aeaa500cfaee6834b343a5f21f717a0d684c0ead5a54767d833d5 (Paparazzi mode/blob/path),
64859f12035c03fab1d27c83c9aa12abd88286e9fec0dd5ef060e05572a65f2c (path list) and
8f4705371b62353ed92a33af735cddaba926992c24ca718fc5cd491c77b40513 (all PNGs).

**The normative check is relative, not these hashes:** at implementation entry, take the base
manifests, rewrite exactly the 50 all-trainings paths from `src/test/` to `src/androidHostTest/`,
and require the head manifests to equal the rewritten base byte for byte; the blob-set hash must be
unchanged. Other work (Wear Phase 2 regenerates store screenshots) may move the repository totals
before entry; that is not drift of this phase.

### 3.8 Measurement limitation

Discovery was static: no Gradle invocation, simulator or device. The implementation records fresh
entry gates (Section 12) and stops if the environment for any of them is missing.

## 4. Platform/API blockers — settled decisions

### 4.1 Paging remains common

7.8 Section 4.1 applies. The exact verdicts all-trainings keeps:

- `listSurface`: rows → CONTENT; refresh Loading → LOADING; refresh Error → REFRESH_ERROR;
  selecting → SELECTION_EMPTY; filter active → FILTERED_EMPTY; otherwise FIRST_RUN — in this order;
- `crossfades`: true for REFRESH_ERROR, FIRST_RUN, FILTERED_EMPTY, SELECTION_EMPTY only;
- `pagingTailKind`: append Loading → loading footer, Error → error footer with retry, NotLoading →
  no footer.

### 4.2 Android resources become private Compose resources

7.8 Section 4.2 applies with the package of Section 0.3. No `StringResource`,
`PluralStringResource`, Android id or `ResourceWrapper` enters State, Action, Event, UI models,
domain models or repository APIs.

### 4.3 Android APIs leave common

| Today | At exit |
| --- | --- |
| `LocalContext` + `context.appDeps<…>()` | explicit factory (Section 6) |
| `androidx.activity.compose.BackHandler` | `androidx.compose.ui.backhandler.BackHandler` from the convention (#271), `@OptIn(ExperimentalComposeUiApi::class)` (Section 5.6) |
| `System.currentTimeMillis()` | `Clock.System.now().toEpochMilliseconds()` (`kotlin.time`; the opt-in is convention-wide) |
| `androidx.annotation.VisibleForTesting` | removed; `NAME` stays private |
| `uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES` | portable `ThemeMode` previews (Section 5.5) |
| `R`, `androidx.compose.ui.res.*` | generated `Res`, `org.jetbrains.compose.resources.*` |

The portable BackHandler's Android actual at CMP 1.11.1 is
`androidx.activity.compose.BackHandler(enabled, onBack)`, so Android behaviour, including the
predictive-back preview while selection is off, is unchanged. Its upstream deprecation warning
stays, as in 7.7; no `NavigationEventHandler` migration here.

### 4.4 Native test names

None. PR-N renamed the twelve comma-bearing identities of this module; Section 3.2 lists the
results. No `INVALID_CHARACTERS_NATIVE_ERROR` suppression is authorized.

## 5. Resource, State, copy and preview architecture

### 5.1 Private generated resource owner

    src/main/res/values/strings.xml    -> src/commonMain/composeResources/values/strings.xml
    src/main/res/values-ru/strings.xml -> src/commonMain/composeResources/values-ru/strings.xml

Byte-semantic moves, except decision P1: the 24 plural items of Section 3.4 change `%d` to `%1$d`.
Every composable call keeps its current shape, for example
`pluralStringResource(Res.plurals.feature_all_trainings_exercise_count, exerciseCount, exerciseCount)`.

### 5.2 D2 — status and relative-time copy in the mapper

`TrainingListItemMapper` keeps its object and file. Its `toUi` becomes

    internal suspend fun TrainingListItemDomain.toUi(
        nowMillis: Long = Clock.System.now().toEpochMilliseconds(),
    ): TrainingListItemUi

and resolves the status and relative-time strings with `org.jetbrains.compose.resources.getString`
on the private catalog. The branches, thresholds and arguments are unchanged: in progress (needs
`isActive` and a start time) → `status_in_progress_format(relative)`; else a last session →
`status_last_format(relative)`; else `status_never`; relative: under a minute → `just_now`, under an
hour → minutes, under a day → hours, else days, all from `(now − t).coerceAtLeast(0)`.
`PagingHandler` calls it inside the existing `pagingData.map { … }` (paging 3.5.0's `map` takes a
suspend transform) and loses its `ResourceWrapper` parameter. `TrainingListItemUi.statusLabel`
stays a plain `String`.

Threading does not change: the per-item transform runs where today's `ResourceWrapper` call runs,
when `collectAsLazyPagingItems` (via the kit's `collectAsItems`) collects the page events on the
main thread; the outer `flowOn(defaultDispatcher)` never covered it. CMP's first read of the catalog
per process is a blocking asset read there and later reads are cached (`AsyncCache` in
`StringResourcesUtils.kt`). This is recorded as no behaviour change; do not move the work to
"fix" it in this phase.

Consequence, accepted: no Android host test reaches this copy (there is no mapper test, and goldens
use fixtures). On Android, `AllTrainingsExtensionDbVisibilityTest` (@Regression) composes a real row
through the real paging flow and this mapper, so a crash or a missing resource fails it, but it
asserts the name, not the status text. The text is proved on Native by the scene (Section 11.4).

### 5.3 D1 — bulk-archive snackbar copy in the graph

`Event.ShowBulkDeleteSuccess` becomes

    data class ShowBulkDeleteSuccess(
        val archivedCount: Int,
        val blockedNames: ImmutableList<String>,
    ) : Event

`ClickHandler` sends it from the same `onSuccess` with `result.archivedCount` and
`result.blockedNames.toImmutableList()`, and loses its `ResourceWrapper` parameter. ui/AllTrainingsGraph.kt
gains

    // TODO(tech-debt): UI mapping boundary — see documentation/tech-debt.md
    internal suspend fun bulkArchiveMessage(archivedCount: Int, blockedNames: List<String>): String

which returns `getPluralString(bulk_archive_success, archivedCount, archivedCount)` when
`blockedNames` is empty and otherwise
`getString(bulk_archive_partial_format, archivedCount, blockedNames.joinToString(", "))` — the
current branch and separator. The graph's suspend event block shows
`bulkArchiveMessage(event.archivedCount, event.blockedNames)` through `SnackbarManager`, as today.
The function stays in that file, so the production file set is unchanged and iosTest can call it.
documentation/tech-debt.md gains one 🟢 UI Mapping Boundary row for it. The row says why the
shaping returns to the graph that tech-debt.md line 784 records as resolved: `ResourceWrapper`'s
`Int` ids cannot address Compose resources, and a resource handle may not ride in the event.

### 5.4 Composable copy

The five UI files resolve copy with `org.jetbrains.compose.resources.stringResource` /
`pluralStringResource` on the private `Res`, in the same places and with the same arguments.

### 5.5 Previews

The four preview subjects keep their sample content and become eight portable previews:

| File | Subject |
| --- | --- |
| ui/components/TagFilterRow.kt | TagFilterRow |
| ui/components/TrainingRow.kt | TrainingRow |
| ui/components/ArrivedEmptyStates.kt | ArrivedEmptyStates |
| ui/components/TrainingsEmptyState.kt | TrainingsEmptyState |

In each file, exactly once each: `@Preview(name = "Light", showBackground = true)` on
`<Subject>LightPreview()` calling `<Subject>Preview(themeMode = ThemeMode.LIGHT)`;
`@Preview(name = "Dark", showBackground = true)` on `<Subject>DarkPreview()` calling
`<Subject>Preview(themeMode = ThemeMode.DARK)`; and one private `<Subject>Preview(themeMode)` whose
body is today's preview body inside `AppTheme(themeMode = themeMode)`. Eight `@Preview(` in total.
No preview is deleted.

### 5.6 Back

    @OptIn(ExperimentalComposeUiApi::class)        // joins the existing ExperimentalSharedTransitionApi opt-in
    BackHandler(enabled = processor.state.value.interceptBack) {
        processor.consume(Action.Click.OnSelectionExit)
    }

The enablement (`interceptBack` = selection on) and the action are unchanged.

### 5.7 Inherited locale property of Compose resources

Compose resources choose a string by the primary locale only (`Locale.getDefault()` off
composition, `Locale.current` in it; exact language+region, then language, then the default
catalog — `ResourceEnvironment.kt` / `ResourceEnvironment.android.kt` at 1.11.1), and fill
placeholders with `toString()`. Android `res`, which `ResourceWrapper` and Android
`stringResource` use today, walks the whole locale list and formats with the configuration locale.
Two visible differences follow, both already true of every module migrated since Phase 7.1 (the kit
included), and both accepted here rather than fixed:

- a device whose list is, for example, [uk-UA, ru-RU] shows this screen in Russian today and in
  English after the move, as it already shows kit and archive copy;
- a locale with non-Latin default digits shows Latin digits in counts and relative times.

The app-wide remedy (a locale-list-aware resource environment, or a per-app locale) is a separate
decision for the maintainer; it is not part of this phase.

## 6. Explicit generation-owned shape-A factory flow

7.8 Section 6 applies with the substitutions of Section 0.3: `AppRootDeps` gains
`val allTrainingsGraphFactory: AllTrainingsGraph.Factory`; `AppGraph` overrides it; `App.kt`
passes `allTrainingsGraphFactory = deps.allTrainingsGraphFactory`; `AppNavigationHost` takes a
required `allTrainingsGraphFactory: AllTrainingsGraph.Factory` and calls
`allTrainingsGraph(factory = allTrainingsGraphFactory, modifier = …)`; `allTrainingsGraph` takes a
required factory and builds `AllTrainingsFeature(factory)`; `AllTrainingsFeature` becomes an
`internal class` with `private val factory: AllTrainingsGraph.Factory`; `createAllTrainingsGraph()`
runs once, inside the retained `rememberMetroStoreProcessor` lambda. app/common's edge becomes
`api`. `LocalContext`, `appDeps`, `asContribution`, nullable fallbacks, registries and
pre-admission resolution are forbidden. `AllTrainingsFeature`'s KDoc drops its `appDeps<T>()`
sentence for archive's wording: the topology scan reads comments too.

`AllTrainingsExtensionIdentityTest` gains
`private fun AppGraph.allTrainings(): AllTrainingsGraph = allTrainingsGraphFactory.createAllTrainingsGraph()`,
both identities reach the store through `.allTrainings()`, the `asContribution` import goes, and
the two names and assertions stay exact.

The seventh app path: `AllTrainingsExtensionDbVisibilityTest` reads
`val factory = MetroTestGraphHolder.graph.allTrainingsGraphFactory` after `MetroTestRule` installed
the graph and before `setContent`, and composes `allTrainingsGraph(factory = factory)`. Its
@Regression annotation, seeding, wait and assertion are unchanged.

## 7. In scope

Only, after the separate GO: the conversion of Sections 4–6; the moves of Sections 3.1–3.4; the
P1 catalog change; D1 and D2; removal of `@VisibleForTesting`; the portable BackHandler and
previews; porting the nine suites to kotlin.test with deterministic in-file fakes; the golden move;
the device move; one iosTest scene; the seven app paths; the three CI paths; the mutation
harness's registered case; the five documentation paths; one signed, green PR that the maintainer
merges.

## 8. Explicit non-goals

7.8 Section 8 applies. In addition: no `NavigationEventHandler`; no change to the inherited
`updateStateImmediate { … tags.map { … } … }` mapping in `PagingHandler` (pre-existing, outside
this phase); no new mapper test beyond the scene; no rename of any Section 3.2 identity; no change
to `graphify-out/`.

## 9. Exact allowed change boundary

### 9.1 Target module — 96 tracked paths at exit

- `build.gradle.kts`;
- 31 production files under `src/commonMain/kotlin/io/github/stslex/workeeper/feature/all_trainings/`
  (Section 3.1);
- `src/commonMain/composeResources/values/strings.xml` and `values-ru/strings.xml`;
- 9 suites under `src/commonTest/kotlin/io/github/stslex/workeeper/feature/all_trainings/`
  (Section 3.2 paths);
- `src/androidHostTest/kotlin/io/github/stslex/workeeper/feature/all_trainings/golden/AllTrainingsGoldenTest.kt`;
- 50 PNGs under `src/androidHostTest/snapshots/images/` (Section 3.3 names);
- `src/androidDeviceTest/kotlin/io/github/stslex/workeeper/feature/all_trainings/AllTrainingsScreenTest.kt`;
- `src/iosTest/kotlin/io/github/stslex/workeeper/feature/all_trainings/AllTrainingsFeatureSceneIosTest.kt`.

Nothing remains under `src/main`, `src/test` or `src/androidTest`; no androidMain, iosMain, shared
test helper, fixture or extra catalog.

### 9.2 Root, CI, tooling and documentation — 16 paths

App (7):

    app/app/src/main/java/io/github/stslex/workeeper/di/AppGraph.kt
    app/app/src/test/kotlin/io/github/stslex/workeeper/di/AllTrainingsExtensionIdentityTest.kt
    app/app/src/androidTest/kotlin/io/github/stslex/workeeper/app/AllTrainingsExtensionDbVisibilityTest.kt
    app/common/build.gradle.kts
    app/common/src/main/kotlin/io/github/stslex/workeeper/App.kt
    app/common/src/main/kotlin/io/github/stslex/workeeper/app/common/di/AppRootDeps.kt
    app/common/src/main/kotlin/io/github/stslex/workeeper/host/AppNavigationHost.kt

CI (3):

    .github/scripts/assert_kmp_ui_source_topology.py
    .github/scripts/assert_kmp_ios_smoke.py
    .github/workflows/android_build_unified.yml

Gate tooling (1):

    documentation/mockups/mutation_harness.py

Its registered case "blank-start CTA stops withdrawing while a session runs (B27's guard)" names
`feature/all-trainings/src/main/…/mvi/store/AllTrainingsStore.kt` and
`:feature:all-trainings:testDebugUnitTest --tests *StartBlankGateTest*`. After the move the path
must be the commonMain one and the task `:feature:all-trainings:testAndroidHostTest --tests
*StartBlankGateTest*`, because on a KMP module `testDebugUnitTest` is a lifecycle alias that rejects
`--tests` (precedent `6ee79846`, the kit case after Phase 7.1). Nothing else in the file changes.

Documentation (5):

    documentation/architecture.md
    documentation/ci-cd.md
    documentation/testing.md
    documentation/tech-debt.md
    documentation/feature-specs/kmp-phase-7-9-all-trainings-feature.md

tech-debt.md: the Section 5.3 row, and its three links into the old all-trainings paths (rows at
lines 288, 445 and 529 at the baseline) re-pointed to the new paths.

Known stale references after the move, deliberately untouched (historical records, or already
stale before this phase and owned by a separate refresh): `documentation/features.md` lines 36–52,
`.claude/skills/write-ui-test.md` line 24, `documentation/research/*`,
`documentation/feature-specs/trainings.md`, archive's own stale tech-debt links (lines 20 and 526),
`graphify-out/`.

A need for any other path is a STOP.

## 10. Gradle and public API contract

~~~kotlin
plugins {
    alias(libs.plugins.convention.kmpComposeLibrary)
    alias(libs.plugins.metro)
    alias(libs.plugins.paparazzi)
}

compose.resources {
    packageOfResClass = "io.github.stslex.workeeper.feature.all_trainings.resources"
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

The comments that carry the build's history move with it. Verify Gradle metadata rather than copy
blindly (7.8 Section 10): a dependency the measured source does not need is removed, one it needs
is a STOP-and-amend, never a silent addition.

## 11. Test and Native scene contract

### 11.1 Portable suites

The nine files move to commonTest. JUnit 5 and MockK give way to `kotlin.test`,
`kotlinx-coroutines-test` and deterministic in-file fakes and spies (named private classes; no
shared helper file). Android host and Native each execute exactly the 49 identities of Section 3.2,
zero failure, error or skip, distribution 3/18/2/3/2/9/4/4/4.

The fakes reproduce today's relaxed-MockK behaviour exactly: the handler-store fake applies
`updateState`/`updateStateImmediate` to a `MutableStateFlow`, records `sendEvent` and `consume`,
and records each `launch` without running it, so the bulk-confirm identity still runs `action` and
`onSuccess` itself while every other identity never does. Record a launch as a closure built inside
the generic `launch<T>` — for example `suspend (CoroutineScope) -> Unit` that runs `action` and hands
its result to `onSuccess` — so no unchecked cast (and so no suppression) is needed. One identity is
strengthened, because D1 changed its contract: **OnBulkDeleteConfirm calls archiveTrainings and
clears selection on success** also asserts that the last event is
`ShowBulkDeleteSuccess(archivedCount = 2, blockedNames = empty)`. No other assertion changes.

Repository fakes in the domain suite import data shapes (`BulkArchiveOutcome`, `TagDataModel`,
`ActiveSessionWithStats`, …); PR-L is what lets them live under commonTest without
`DomainLayerPurityRule` reading them as production domain code. Never import them by fully
qualified name to dodge the rule.

### 11.2 Android host goldens

7.8 Section 11.2 applies: `AllTrainingsGoldenTest` moves to androidHostTest with only the
runner mechanics the KMP harness needs; the 25 method names, light/dark cases, subjects, fixtures
and 50 filenames stay exact; PNGs move with `git mv`; `verifyPaparazziDebug` executes all 50
cases; `recordPaparazziDebug` is forbidden.

### 11.3 Android device compatibility

`AllTrainingsScreenTest` moves byte-semantically to androidDeviceTest and stays the one documented
skip. The app journeys of Section 3.6 and `AllTrainingsExtensionDbVisibilityTest` stay green.

### 11.4 Deterministic iOS production scene

Exactly one class and method, no comma:

    class AllTrainingsFeatureSceneIosTest
    fun resourcesPagingBranchesSelectionAndActionsRenderAndDispatch()

It uses `runComposeUiTest`, the production `AllTrainingsScreen` inside `AppTheme`, deterministic
settled `PagingData`, a fixed `nowMillis`, and the settle-until-tag stepping of 7.8 Section 19.8.
In one bounded scene it proves:

1. the catalog: all 26 strings and both quantities of the 4 plurals resolve to the exact EN values
   of Section 3.4;
2. mapper copy (D2), through the production `toUi`: `in progress · started 5m ago`,
   `last: just now`, `last: 2h`, `last: 3d`, `never trained`;
3. snackbar copy (D1), through `bulkArchiveMessage`: `1 training archived`,
   `3 trainings archived`, `Archived 2, blocked: Legs, Push`;
4. surfaces: cold-open loading held across three further settles (`AllTrainingsColdOpen`), refresh
   error with its reason and Retry (`AllTrainingsColdOpenError`), first-run empty with both CTAs
   when no session runs and without the blank-start CTA when one does, filtered empty with Clear
   filter, selection empty with and without Clear filter, populated rows with name and meta line
   (`status · N exercises · tags`), append loading and append error tails;
5. bars: resting title `Trainings`; selection title `2 selected`; close and archive content
   descriptions; the FAB description `Create training` at rest and `Archive` in selection. In
   selection two nodes carry `Archive` (the FAB and the bar action), so these are asserted on the
   production tags `AllTrainingsFab` and `AllTrainingsSelectionTopBarArchive`, not by description
   lookup;
6. the confirm dialog: title, `2 trainings will move to archive. Restore from Settings → Archive.`,
   impact, confirm label;
7. dispatch, collected and asserted as one exact list in the scene's own order, containing: row
   click `OnTrainingClick`, row long press `OnTrainingLongPress`, tag chip `OnTagFilterToggle`, FAB
   `OnFabClick`, empty create `OnEmptyCreate`, blank start `OnEmptyStartBlank`, clear filter
   `OnClearTagFilter`, selection close `OnSelectionExit`, selection archive `OnFabClick`, dialog
   confirm `OnBulkDeleteConfirm`, dialog dismiss `OnBulkDeleteDismiss`.

It may use production test tags and deterministic fixtures. It may not add a platform host,
repository, database, fake Store, screenshot baseline or second identity.

The module's Native oracle is 50 tuples: the 49 portable identities plus this scene.

## 12. CI ownership and positive verification

### 12.1 Topology gate data (normative)

`MODULES["feature:all-trainings"]`: the 95 files under `src` of Section 9.1; Kotlin source sets
`commonMain, commonTest, androidHostTest, androidDeviceTest, iosTest`; resource dirs `values`,
`values-ru`. `EXPECTED_APP_DEPS_READERS` loses the all-trainings entry.

`ALL_TRAININGS_ENTRY`, appended to `FEATURE_ENTRIES`, with the field names of the merged PR-G
schema (if the merged schema spells a field differently, keep the data and map it one to one; a
field with no counterpart is a STOP):

| Field | Value |
| --- | --- |
| module / short_name | `feature:all-trainings` / `all-trainings` |
| root / package_dir | `feature/all-trainings` / `io/github/stslex/workeeper/feature/all_trainings` |
| build_fragments | every plugin, `packageOfResClass`, `includeJavax()`, dependency and `apply(from = …)` line of Section 10, each exactly once — except `implementation(kotlin("test"))`, which kotlin_test_count covers |
| kotlin_test_count | 2 |
| catalog | the 30 rows of Section 3.4, in file order, with the P1 values |
| resource_prefix | `feature_all_trainings_` |
| payload_files | `AllTrainingsStore.kt`, `TrainingListItemUi.kt`, `TagUiModel.kt` (plus everything under domain/). `TrainingListItemMapper.kt` is deliberately absent: under D2 it is where copy resolves |
| store_file / state_fields | `mvi/store/AllTrainingsStore.kt` / `pagingUiState: PagingUiState<PagingData<TrainingListItemUi>>`, `availableTags: ImmutableList<TagUiModel>`, `activeTagFilter: ImmutableSet<String>`, `selectionMode: SelectionMode`, `pendingBulkDelete: PendingBulkDelete?`, `hasActiveSession: Boolean` |
| resource_wrapper_readers | empty |
| preview_subjects | the four of Section 5.5 |
| suppressions | the four of Section 3.1; nothing under commonTest |
| test_names | the 49 of Section 3.2, plus the scene name under the iosTest path |
| golden_test / golden_methods | `src/androidHostTest/…/golden/AllTrainingsGoldenTest.kt` / the 25 of Section 3.3 |
| device_test / device_fragments | `src/androidDeviceTest/…/AllTrainingsScreenTest.kt` / archive's three |
| factory names | Section 0.3; feature_file `di/AllTrainingsFeature.kt`, graph_file `ui/AllTrainingsGraph.kt` |
| identity_test / identity_fragments | `app/app/src/test/…/di/AllTrainingsExtensionIdentityTest.kt` / the `allTrainings()` helper of Section 6 |
| identity_names / identity_accessor | the two inherited names / `(?<!AppGraph)\.allTrainings\(\)` |
| extra_root_fragments | DbVisibility test: `MetroTestGraphHolder.graph.allTrainingsGraphFactory` and `allTrainingsGraph(factory = factory)` |
| extra_checks | `all_trainings_copy_contract`, `all_trainings_back_contract` below |

`all_trainings_copy_contract`:

- in `mvi/store/AllTrainingsStore.kt`, the `ShowBulkDeleteSuccess` parameters are exactly
  `archivedCount: Int` and `blockedNames: ImmutableList<String>`;
- `ui/AllTrainingsGraph.kt` holds exactly one `internal suspend fun bulkArchiveMessage(`, exactly
  one `bulkArchiveMessage(event.archivedCount, event.blockedNames)` and exactly one
  `// TODO(tech-debt): UI mapping boundary — see documentation/tech-debt.md`;
- `mvi/mapper/TrainingListItemMapper.kt` holds exactly one
  `internal suspend fun TrainingListItemDomain.toUi(` and one
  `Clock.System.now().toEpochMilliseconds()`;
- no file under `mvi/handler/` contains `getString(`, `getPluralString(`, `stringResource(`,
  `pluralStringResource(` or `Res.`.

`all_trainings_back_contract`: `ui/AllTrainingsGraph.kt` holds exactly once each
`import androidx.compose.ui.backhandler.BackHandler`, `ExperimentalComposeUiApi::class`,
`BackHandler(enabled = processor.state.value.interceptBack)` and
`processor.consume(Action.Click.OnSelectionExit)`.

`assert_kmp_ios_smoke.py` gains a `feature:all-trainings` entry with the 50 tuples of Section 11.4
(classnames are the suites' packages from Section 3.2, the scene's is
`io.github.stslex.workeeper.feature.all_trainings.AllTrainingsFeatureSceneIosTest`).

`android_build_unified.yml`, KMP iOS kit smoke job: add `:feature:all-trainings:iosSimulatorArm64Test`
to the simulator test command and `:feature:all-trainings:linkDebugTestIosArm64` to the device-link
command; add the directory `feature/all-trainings` to the binary-assert loop (its `/8 linked` becomes
`/9 linked`) and `feature/all-trainings/build/test-results/iosSimulatorArm64Test/` to the upload
list; the two "eight modules" comments say nine. The job and context names stay.

The nine modules, for every command below: `:core:ui:kit`, `:core:ui:navigation`, `:core:ui:mvi`,
`:core:ui:start-mode`, `:core:ui:plan-editor`, `:feature:image-viewer`, `:feature:plan-editor`,
`:feature:archive`, `:feature:all-trainings`.

### 12.2 Entry gate before any edit

7.8 Section 12.2 applies, plus: PR-N, PR-G and PR-L are merged and are ancestors of the base; `git diff
38982c2e8..<base> -- feature/all-trainings` shows only PR-N's twelve renames (anything else is a
STOP and a remeasure); the census of Sections 3.1–3.7 is reproduced on the base; the current
eight-module Native command and oracle are green. Baseline commands, with the stable flags:

    ./gradlew :feature:all-trainings:assembleDebug
    ./gradlew :feature:all-trainings:testDebugUnitTest         # 49 portable cases; goldens excluded
    ./gradlew :feature:all-trainings:verifyPaparazziDebug      # 50 golden + 49 portable host cases
    ./gradlew :feature:all-trainings:assembleDebugAndroidTest

`gradle/golden-gate.gradle.kts` excludes `*.golden.*` from every host test run unless a Paparazzi
task is on the command line, so a plain unit run reports 49 cases and only the Paparazzi gate runs
the 50 golden cases (7.8 Sections 12.2, 19.3 and 19.10 record the same split for archive: 25 and
39).

### 12.3 Focused gates after the implementation

    python3 .github/scripts/assert_kmp_ui_source_topology.py
    ./gradlew :feature:all-trainings:assembleDebug
    ./gradlew :feature:all-trainings:testAndroidHostTest       # 49 portable cases
    ./gradlew :feature:all-trainings:verifyPaparazziDebug      # 50 golden + 49 portable
    ./gradlew <iosSimulatorArm64Test of the nine modules of Section 12.1> --continue
    python3 .github/scripts/assert_kmp_ios_smoke.py            # covers all-trainings from commit 2
    ./gradlew <linkDebugTestIosArm64 of the nine modules> --continue
    ./gradlew :app:common:assembleDebug

Then `./gradlew detekt --continue` alone (it runs on every commit's tree, not only on commit 2's);
the two `AllTrainingsExtensionIdentityTest` identities (fresh XML); the registered mutation
harness (`python3 documentation/mockups/mutation_harness.py`), whose B27 case must still score RED
and restore; and on an explicit API-34 serial with animations disabled as `ui_tests.yml` does:
`AllTrainingsExtensionDbVisibilityTest` and the four journey classes of Section 3.6 (fresh XML).

### 12.4 Repository and visual gates

7.8 Section 12.4 applies unchanged (assemble/lint/unit, detekt alone, assembleDebugAndroidTest,
verifyPaparazziDebug, :lint-rules:test, personal-data gate, canonical Smoke and Regression device
suites, diff hygiene, the PNG manifest check of Section 3.7, the mockup shell gate where runnable).

### 12.5 Final diff and remote proof

7.8 Section 12.5 applies with the Section 9 boundary, 49 + 1 Native tuples, 50 unchanged golden
blobs and the four production suppressions.

## 13. Mandatory known-negative controls

Fresh GREEN → one named observable RED → automatic exact restoration → fresh GREEN, with a
byte-restoring harness (7.8 Section 13). From 7.8, with Section 0.3 substitutions: controls 1–17
and 19–25 (15 and 16 unchanged: App.kt still resolves AppRootDeps once per admitted generation).
Control 18 is archive-specific and is replaced by 26. Additionally:

26. Swap the minute and hour thresholds in `TrainingListItemMapper` → the scene's mapper-copy
    assertion fails on Native.
27. Send `archivedCount = 0` from `ClickHandler` → the strengthened bulk-confirm identity fails on
    Android host and Native.
28. Make `bulkArchiveMessage` ignore `blockedNames` → the scene's snackbar assertion fails.
29. Change `ShowBulkDeleteSuccess`'s parameters to `message: String` in AllTrainingsStore.kt →
    `all_trainings_copy_contract` names it.
30. Call `getString(` in `ClickHandler` → `all_trainings_copy_contract` names it.
31. Drop `enabled = processor.state.value.interceptBack` from the BackHandler → the back contract
    names it.
32. Reintroduce one bare `%d` in the EN catalog → the placeholder rule names key and token.
33. Remove `MetroTestGraphHolder.graph.allTrainingsGraphFactory` from the DbVisibility test → the
    root-fragment check names it.
34. Add a 50th portable identity under a new name → the topology test-name inventory names the
    unexpected identity.

Controls 1–14, 17, 29–34 are scored by the Python gates (topology and placeholder rule) and need not
compile; each is one logical mutation (control 3 relocates a file and control 7 moves one key
between two catalogs, as in 7.8). Controls 1 and 3 target a file the contract does not open by name
— `ui/components/TopBarMode.kt` is one — so the gate names it instead of crashing on a missing read.
Control 33's RED is the root-fragment failure line PR-G defines for `extra_root_fragments`, quoted.
Compile-invalid experiments are never scored as RED where a compiled test is the detector (7.8
Section 13).

## 14. STOP conditions

7.8 Section 14 applies, read with Section 0.3. In addition, stop if:

- PR-N, PR-G or PR-L is not merged, or the merged FeatureEntry cannot carry Section 12.1;
- the target or any Section 9.2 path drifted from the discovery baseline in a way this document
  does not describe;
- any identity of Section 3.2 contains a Native-illegal character, or a suppression of any kind
  becomes necessary;
- any golden needs recording or any PNG blob changes;
- the animation, icons or immutable-collections edges of Section 3.5 turn out wrong in either
  direction;
- the portable BackHandler changes Android back behaviour in any device journey;
- D1 or D2 cannot be implemented without a new production file, a resource handle in a payload, or
  resource resolution inside a State lambda (an `updateState` / `updateStateImmediate` body, as the
  topology gate reads it; the `PagingUiState { … }` flow lambda is not one);
- a Section 3.2 identity compiles but the Native XML reports it under any other name (the two
  `—` names are the first non-ASCII test names on Native here);
- detekt reports anything in the moved tests that PR-L does not cover (PR-L makes
  `DomainLayerPurityRule`, `UiLayerNoDataRule` and `TooManyFunctions` treat commonTest/iosTest as
  tests; any other rule firing there is a new finding, and the fix is never a suppression);

A STOP produces a measured note and, where needed, an amendment proposal. It never widens the
implementation silently.

## 15. Signed, bisect-green commit plan

Three English Conventional Commits, each signed, GitHub-verified and green on its own tree:

1. **refactor(kmp): share all-trainings feature entry** — the module (Sections 3–11), the seven app
   paths, the mutation harness's registered case (Section 9.2), and the minimal topology delta that
   keeps this tree green (the `MODULES` entry and the all-trainings reader removed from
   `EXPECTED_APP_DEPS_READERS`; precedent 7.8 G1).
2. **ci(kmp): gate shared all-trainings feature** — `ALL_TRAININGS_ENTRY` and its two extra checks,
   the Native oracle entry, the workflow lists; all controls and the repository gates.
3. **docs(kmp): record Phase 7.9 evidence** — Section 19 of this document and the canonical facts
   the implementation made stale in architecture.md, ci-cd.md and testing.md (the places 7.8's
   commits `1bafa081` and `fbf6dc06` updated for archive), plus tech-debt.md (the Section 5.3 row
   and the three re-pointed links).

Review fixes are separate signed commits inside Section 9. Nothing is amended, squashed or
reordered.

## 16. Exit criteria

7.8 Section 16 applies with: 96-path topology and two private catalogs; 30 identifiers with the 24
P1 values and nothing else changed; `ResourceWrapper` absent from the feature; 49 identities on
host and Native and 50 Native tuples; 50 golden cases with unchanged blobs and the relative PNG
manifest proof; four production suppressions and no other; the explicit factory flow, both
identities through the accessor, the DbVisibility test through the factory; the topology entry,
oracle and workflow at nine modules; readers 10 → 9; the mutation harness's B27 case RED on the new
path; all controls of Section 13 evidenced.

## 17. Remaining ordered roadmap

After 7.9 the frontier is remeasured. By today's sizes the next entry is all-exercises (it shares
all-trainings' list-surface, selection and paging-tail structure). Then past-session, home,
exercise-chart, settings, single-training, exercise, live-workout; then the app-dialog boundary,
app/common, and the permanent iOS host. A throwaway iosApp remains forbidden.

## 18. No implementation authorization

Landing this document on dev authorizes nothing. Implementation may begin only after PR-N, PR-G
and PR-L are merged, the merged dev is remeasured (Section 12.2), and the maintainer gives a new,
explicit GO.

## 19. Implementation evidence

This section records the bounded implementation authorized by the maintainer's GO of 2026-10-09
("PR-N, PR-G and PR-L merged — GO 7.9", Section 18). That GO authorized exactly Section 7 and
nothing in Section 8; it did not relax any scope, STOP condition, or proof requirement. Sections
0–18 remain the historical specification and are not rewritten here; where the measured tree
contradicts their prose, Section 19.7 says so.

Every load-bearing Gradle invocation below ran immediately after `./gradlew --stop` and a bounded
check that no other Gradle client was running, with `--rerun-tasks --no-build-cache
--no-configuration-cache --console=plain` (plus `--continue` where stated). Every "Executed" cell
is the logged `N actionable tasks: N executed`. Only JUnit XML written after the run's recorded
start was parsed; no gate in this record parsed a stale file.

### 19.1 Entry, drift, and authorization baseline

At entry, `origin/dev` was `fd5dc41509483a5e2c9b6c0458527fbfdaf69355`, the merge of #321. This
specification's blob on it had sha256 `6f81ed29aa274eb24e3312d12b57e09c48a2a483d8f9cc345b57cc1c7e4fbe88`,
the value the pickup step landed. The branch `feature/kmp-phase-7-9-all-trainings` was created in
an isolated worktree from that SHA.

| Prerequisite | Decision | Merge commit on `dev` | PR head | Content proof at the base |
| --- | --- | --- | --- | --- |
| PR-N, #321 | D3 | `fd5dc41509483a5e2c9b6c0458527fbfdaf69355` | `b0045adfa` | PR-N's census script reports all-trainings 0, every module 0, TOTAL 0; its positive control on `38982c2e8` reports TOTAL 60 in 34 files, all-trainings 12 |
| PR-L, #322 | D5 | `138f8b0a95d387937cddc1ad02d729d186d55847` | `964cd9a62` | `DomainLayerPurityRule.kt:32` and `UiLayerNoDataRule.kt:31` return on `TestSourceSets.isTestFile(filePath)` (the directory after the last `src` is `test`, `test<Upper>…`, or contains `Test`), so `commonTest`, `iosTest`, `androidHostTest` and `androidDeviceTest` are exempt and `commonMain` is not; `lint-rules/detekt.yml`'s `TooManyFunctions` excludes `**/commonTest/**` and `**/iosTest/**` |
| PR-G, #323 | D4 | `ad71fd8607016c3cfb696ad275788cafd6d8bf31` | `aaaf911bd` | `@dataclass(frozen=True) class FeatureEntry` with 34 fields (line 1066), `ARCHIVE_ENTRY = FeatureEntry(…)` (line 1614), `FEATURE_ENTRIES = (ARCHIVE_ENTRY,)` (line 1864), and `main()` runs `check_feature_entry` for every entry (line 2006) |

All three are ancestors of the base (`merge-base --is-ancestor`). Every field name Section 12.1
spells is a schema field verbatim; its "factory names" row maps one to one onto `feature_class`,
`graph_interface`, `graph_function`, `factory_accessor`, `create_method`, `store_impl` and
`store_accessor`, beside the literal `feature_file` and `graph_file`. No mapping was needed and no
field lacked a counterpart.

Drift: `git diff 38982c2e8..fd5dc4150 -- feature/all-trainings` changes 7 files, 12 insertions and
12 deletions, all from PR-N's `b0045adfa`: the twelve renamed identities. Re-deriving the discovery
names with PR-N's comma deletion gives exactly the base names. Re-measured from the git objects for
this record: of the Section 9.2 paths, only two changed since `38982c2e8`,
`.github/scripts/assert_kmp_ui_source_topology.py` (PR-G's `aaaf911bd`, the contract this phase
extends as data) and this specification (`cf25663fe`). Neither conflicts.

Two read-only measurers reproduced Section 2's all-trainings row and Sections 3.1–3.7 on the base
independently, neither reading the other's output. Census A read only the base's git objects
(`ls-tree -r -z`, `cat-file blob`; a scratch index built with `read-tree` for the PNG manifests).
Census B read the clean worktree with a Kotlin lexer and declaration walker (nested block comments,
string templates, backtick names, multi-line annotations) and DOM XML parsing. Both reported MATCH
on every item:

| Section | Specification | Measured (A and B agree) | Methods |
| --- | --- | --- | --- |
| 2, all-trainings row | 31 / 1,795 / 10 / 1,041 / 26 / 50 | equal; 10 unit files with 1,041 lines (A) | newline counts of the base blobs |
| 3.1 files and lines | 31 files, 1,795 lines, per-file table | every row equal; every file ends in LF | A: newline count of each blob (equal to `wc -l`); B: `wc -l` and awk `NR`, diffed against the table |
| 3.1 suppressions | four | `AllTrainingsFeature.kt:24` `UNCHECKED_CAST`, `ClickHandler.kt:20` `TooManyFunctions`, `ui/AllTrainingsGraph.kt:16` `LongMethod`, `AllTrainingsScreen.kt:240` `UNCHECKED_CAST`; none in `src/test` or `src/androidTest` | A: the gate's `@(?:file:)?Suppress\(([^)]*)\)` over every module `.kt`; B: multi-line `rg` for `@Suppress`, `@file:Suppress`, `SuppressLint`, `SuppressWarnings`, plus case-insensitive `suppress` and `noinspection` |
| 3.1 Android coupling | as listed | `R` in seven files; `ResourceWrapper.getString/getQuantityString` in `ClickHandler` and `TrainingListItemMapper`, passed through by `PagingHandler`; `System.currentTimeMillis()` as `toUi`'s default (`TrainingListItemMapper.kt:18`); the activity `BackHandler` only in `ui/AllTrainingsGraph.kt`; `LocalContext` and `appDeps` only in `di/AllTrainingsFeature.kt`; `@VisibleForTesting` only on `AllTrainingsStoreImpl`'s private `NAME`; `uiMode` in the four preview files | import and token scans |
| 3.2 identities | 49, 3/18/2/3/2/9/4/4/4 | 49 names byte-identical to the bullets, in order | A: `@Test` names from the base blobs; B: the declaration walker, byte comparison |
| 3.2 frameworks | MockK in the first three suites, JUnit 5 in all nine, no `paging-testing` | equal; `runTest` in two suites; the build still declares `testImplementation(libs.androidx.paging.testing)` and no test imports it | import scans |
| 3.2 U+2014 | two `StartBlankGateTest` names | zero-based offsets 19 and 21, bytes `E2 80 94`; the repository's 28 `commonTest`/`iosTest` files carry no non-ASCII test name, here and at `38982c2e8` | byte scans |
| 3.3 goldens and device | 25 methods in order; 50 PNGs, 50 blobs, 100644; `@Smoke` class with the exact `@Ignore` | equal; the 50 paths are the 25 methods × {dark, light}; the `@Ignore` literal byte-equal | declaration walker; `ls-files -s` |
| 3.4 catalog | 26 strings and 4 plurals per locale in file order; 24 bare `%d` items | equal in both locales; bare `%d` EN 8 + RU 16 = 24; strings already positional; EN quantities one/other, RU one/few/many/other; EN one XML comment, RU two; the RU root carries `xmlns:tools` and `tools:ignore="DuplicateStrings"`; no code outside the module names a `feature_all_trainings_` identifier | A: `minidom`; B: DOM parsing and a format-token lexer, `rg` cross-check 8/16 |
| 3.5 dependencies | the left column | the 12 declarations equal; every Section 10 catalog alias exists; the only material icon is `Icons.Default.Close` | build-file read |
| 3.6 consumers | the table | app/app line 48 and app/common line 32 `implementation`; one `allTrainingsGraph(…testTag("AllTrainingsGraph"))` call; no all-trainings accessor in `AppGraph` or `AppRootDeps`; the identity test's two names through `asContribution`; the `@Regression` DbVisibility test through `TestSingleScreenHost`; the tag in exactly the four journey classes; `Screen.BottomBar.AllTrainings` in core:ui:navigation `commonMain` | `rg` and the declaration walker |
| 3.7 readers | 10 | 10 executable `context.appDeps<…>()` readers, equal to `EXPECTED_APP_DEPS_READERS` (nine navigation entries and app-dialogs); 9 without all-trainings' | A: the gate's regex; B: a lexer token scan of every tracked `.kt` and `.kts` |
| 3.7 PNG manifests | four hashes, three projections | all seven equal (Section 19.4) | B: the 7.7 Section 12.4 pipelines verbatim; A: the same over the scratch index, whose method control reproduced archive's 7.8 blob set `19dd79f0…` |

Two notes that are not mismatches: the golden fixtures carry fixed Russian-language `statusLabel`
strings (Section 3.3 calls them fixed strings; the harness renders the EN copy around them), and
`ARCHIVE_ENTRY` leaves `extra_root_fragments` at its default.

Environment: Gradle 9.6.1 (wrapper); launcher and daemon JBR 21.0.11+10-b1163.116; Kotlin 2.4.10;
Xcode 26.6 (17F113), the version CI selects. Kotlin/Native's test task chose, with no
configuration, an iPhone 17 Pro on iOS 26.5 through `simctl spawn --standalone`; no simulator was
pre-booted, as in the workflow. Before baseline gate 1 a Gradle client from another local checkout
of the repository was running; the gate waited 421 s (15 polls of 30 s) until it was gone. Every
later gate found no foreign client at its first poll.

Signing: the `signed-commits` skill ran before each commit (`commit.gpgsign=true`,
`gpg.format=ssh`). Both commit objects carry `gpgsig -----BEGIN SSH SIGNATURE-----`, and
`verify-commit` against a scratch allowed-signers file built from the configured public key reports
a good ED25519 signature (key `SHA256:G9uJsrl0uvY8+GQvRogNO5PG4TLHvUEq2id4rSi0nCg`) for both. The
worktree configures no `gpg.ssh.allowedSignersFile`, so `%G?` alone prints `N`; the skill does not
use it as the gate.

The evidence ledger does not record the open-PR check of the procedure's entry gate. Measured for
this record on 2026-10-09: one open PR, #315 (`AGENTS.md` and `CLAUDE.md` only), and no remote
branch whose name contains `all-trainings`.

### 19.2 Commits and changed boundary

| Commit | SHA | Boundary and verification |
| --- | --- | --- |
| `refactor(kmp): share all-trainings feature entry` | `3f5d129df61fa662a39420e78e973eb639cea19b` | the module (Sections 3–11), the seven app paths, the mutation harness's B27 case, and the minimal topology delta (`MODULES["feature:all-trainings"]`, the reader removed from `EXPECTED_APP_DEPS_READERS`); 109 paths with rename detection, 1,488 insertions, 429 deletions; SSH signature verified locally |
| `ci(kmp): gate shared all-trainings feature` | `5343fda451201e1f2942bd26c64b14233d068f53` | exactly the three Section 9.2 CI paths: `assert_kmp_ios_smoke.py` +107/−0, `assert_kmp_ui_source_topology.py` +479/−1, `android_build_unified.yml` +8/−4; SSH signature verified locally |
| `docs(kmp): record Phase 7.9 evidence` | this commit | exactly the five Section 9.2 documentation paths |

Against the base `fd5dc4150`, the three commits change 206 paths counted without rename pairing:
190 under `feature/all-trainings` — the 94 legacy `src/main`, `src/test` and `src/androidTest`
paths removed, 95 added (the 94 moved files and the Native scene) and `build.gradle.kts` modified —
and exactly the 16 Section 9.2 paths outside it:

    app/app/src/main/java/io/github/stslex/workeeper/di/AppGraph.kt
    app/app/src/test/kotlin/io/github/stslex/workeeper/di/AllTrainingsExtensionIdentityTest.kt
    app/app/src/androidTest/kotlin/io/github/stslex/workeeper/app/AllTrainingsExtensionDbVisibilityTest.kt
    app/common/build.gradle.kts
    app/common/src/main/kotlin/io/github/stslex/workeeper/App.kt
    app/common/src/main/kotlin/io/github/stslex/workeeper/app/common/di/AppRootDeps.kt
    app/common/src/main/kotlin/io/github/stslex/workeeper/host/AppNavigationHost.kt
    .github/scripts/assert_kmp_ui_source_topology.py
    .github/scripts/assert_kmp_ios_smoke.py
    .github/workflows/android_build_unified.yml
    documentation/mockups/mutation_harness.py
    documentation/architecture.md
    documentation/ci-cd.md
    documentation/testing.md
    documentation/tech-debt.md
    documentation/feature-specs/kmp-phase-7-9-all-trainings-feature.md

With git's default rename detection the same diff has 116 rows: the 16 outside paths and 100 in
the module (90 renames, 4 deletions and 5 additions — the four low-similarity moves of Section 19.7
and the scene — and the modified build file); at `-M10%` the module reads 94 renames, 1 addition
and 1 modification. `feature/all-trainings` tracks exactly 96 paths at exit: `build.gradle.kts`,
`commonMain` 33 (31 Kotlin files and 2 catalogs), `commonTest` 9, `androidHostTest` 51 (the golden
test and 50 PNGs), `androidDeviceTest` 1 and `iosTest` 1, all mode 100644, with nothing tracked or
on disk under `src/main`, `src/test`, `src/androidTest`, `androidMain` or `iosMain`. The 50 PNGs,
the golden test and the device test are 100%-similarity renames. The catalogs differ from the base
only by decision P1's 24 lines (EN 8, RU 16), each an `<item quantity=…>` whose only change is `%d`
→ `%1$d`; comments, the RU root attributes and the trailing newline are byte-identical.

### 19.3 Fresh baseline before any edit

| Command (base tree) | Executed | Parsed result |
| --- | ---: | --- |
| `:feature:all-trainings:assembleDebug` | 104/104 | target assembled |
| `:feature:all-trainings:testDebugUnitTest` | 173/173 | 9 XML suites, 49 cases 3/18/2/3/2/9/4/4/4, the 49 Section 3.2 identities (JUnit's trailing `()` removed), 0/0/0 |
| `:feature:all-trainings:verifyPaparazziDebug` | 174/174 | 10 suites, 99 cases (50 golden and 49 portable), 0/0/0; `Visual gate live: 50 golden test case(s) executed for 50 golden image(s).` |
| `:feature:all-trainings:assembleDebugAndroidTest` | 223/223 | device APK assembled (98,466,669 bytes) |
| current eight-module Native command (workflow form, `--continue --full-stacktrace`) | 235/235 | 27 XML suites, 120 cases, 0/0/0: kit 1, navigation 1, mvi 14, start-mode 2, core plan-editor 20, image-viewer 13, feature plan-editor 43, archive 26 |
| `python3 .github/scripts/assert_kmp_ios_smoke.py` | GREEN | `native gate live:`; `feature:archive: 26 executed / 0 skipped / 0 failed / 0 errored — verified 26 exact identities`, and every other module likewise |

The plain unit run compiled `AllTrainingsGoldenTest` but executed no golden case:
`gradle/golden-gate.gradle.kts` excludes `*.golden.*` unless a Paparazzi task is on the command
line. The golden JUnit XML names each case only by its display name (`[1] LIGHT` or `[2] DARK`), so
in every golden gate of this record the method identity was read from the same run's Gradle HTML
report and binary results: 50 cases, 50 distinct (method, theme) pairs, equal to Section 3.3's 25
methods × {LIGHT, DARK}, all passed. Both `StartBlankGateTest` names carry `E2 80 94` in the JVM
XML. Deviations from CI, recorded and not fixed: the checked-in `gradle.properties` and a copied
local keystore stood in for CI's copied CI properties and ephemeral keystore, because the entry
gate allows no repository write; the device link was not part of this baseline, whose procedure
lists only the simulator command and its oracle.

### 19.4 Commit 1 focused evidence

Gates run while commit 1 was assembled, each on the uncommitted tree of that moment:

| Command | Executed | Parsed result |
| --- | ---: | --- |
| `:feature:all-trainings:testAndroidHostTest` (suites ported; no scene, no app paths yet) | 175/175 | 9 suites, 49 cases, 0/0/0, names equal Section 3.2 |
| `:feature:all-trainings:iosSimulatorArm64Test` (same tree) | 105/105 | 9 suites, 49 cases, 0/0/0; the module's first Native run, both U+2014 names byte-exact |
| `:feature:all-trainings:verifyPaparazziDebug` (same tree) | 176/176 | 50 golden and 49 portable cases, 0/0/0 |
| `:feature:all-trainings:iosSimulatorArm64Test` (scene added) | 105/105 | 10 suites, 50 cases, 0/0/0; the 50 tuples byte-exact |
| the same, with one scene expectation changed (dialog body `2 trainings …` → `3 trainings …`) as a non-vacuity probe | 105/105, `BUILD FAILED` | `50 tests completed, 1 failed`: the scene, `Failed to perform isDisplayed check. Reason: Expected exactly '1' node but could not find any node that satisfies: (Text + InputText + EditableText contains '3 trainings will move to archive. Restore from Settings → Archive.' (ignoreCase: false))`; restored by sha256 |
| the same after restoration | 105/105 | 10 suites, 50 cases, 0/0/0 |
| `:app:common:assembleDebug :app:app:assembleDebug :app:app:assembleDebugAndroidTest :app:app:testDebugUnitTest --tests '*AllTrainingsExtensionIdentityTest*'` (app paths added) | 773/773 | the two identities, 0/0/0 |

CMP 1.11.1's `hasText` with `substring = false` compares with `equals`; "contains" is only its
description text. Because the probed assertion is the scene's last rendered stage, every earlier
assertion executed and passed in that run.

The Section 12.3 gates then ran on the complete commit-1 tree, which was committed unchanged as
`3f5d129df` (status, worktree diff and index diff byte-identical at the commit):

| # | Command | Executed | Parsed result |
| ---: | --- | ---: | --- |
| 1 | `python3 .github/scripts/assert_kmp_ui_source_topology.py` | GREEN | macOS Python 3.9.6 and Homebrew Python 3.14.8, byte-identical: `feature:all-trainings: 95 exact source/resource/test files`; `app:common API edges and 9 remaining Context.appDeps readers are exact`; `compose-resource placeholders: 14 commonMain catalogs walked, every placeholder is %N$d or %N$s with N >= 1` |
| 2 | `:feature:all-trainings:assembleDebug` | 302/302 | Android AAR and both iOS klibs (`compileAndroidMain`, `compileKotlinIosArm64`, `compileKotlinIosSimulatorArm64`, `compileCommonMainKotlinMetadata` executed) |
| 3 | `:feature:all-trainings:testAndroidHostTest` | 175/175 | 9 suites, 49 cases 3/18/2/3/2/9/4/4/4, 0/0/0, names equal Section 3.2 |
| 4 | `:feature:all-trainings:verifyPaparazziDebug` | 176/176 | `Visual gate live: 50 golden test case(s) executed for 50 golden image(s).`; 10 suites, 99 cases, 0/0/0; 50 distinct (method, theme) pairs equal to Section 3.3; no PNG written |
| 5 | nine-module `iosSimulatorArm64Test` (Section 12.1 list, `--continue --full-stacktrace`) | 259/259 | all-trainings: 10 suites, 50 cases, 0/0/0, the 50 tuples byte-exact; the other eight modules' (classname, name, status) multisets equal the baseline's |
| 5b | `python3 .github/scripts/assert_kmp_ios_smoke.py` (unchanged in commit 1) | GREEN | byte-identical to the baseline oracle output (eight modules; all-trainings joins in commit 2) |
| 6 | nine-module `linkDebugTestIosArm64` (`--continue --full-stacktrace`) | 250/250 | the workflow's binary loop over the nine directories: `9/9 linked`, every `test.kexe` fresh |
| 7 | `:app:common:assembleDebug` | 319/319 | explicit consumer compiled |
| 8 | `./gradlew detekt --continue`, alone | 63/63 | 55 fresh reports, 0 findings; `:feature:all-trainings:detekt` and `:detektAndroidTestSuite` executed |
| 8b | no-suppression proof (base to tree, renames on) | GREEN | no added or removed line matches `Suppress`, `SuppressLint`, baseline, `freeCompilerArgs`, `compilerOptions` or `allWarningsAsErrors`; no baseline, `detekt.yml`, lint, `gradle.properties`, catalog or build-logic path changed; exactly the four inherited production suppressions |
| 9 | `:app:app:testDebugUnitTest --tests '*AllTrainingsExtensionIdentityTest*'` | 577/577 | 1 suite, 2 cases, 0/0/0: `extension resolves the store through the parent graph` and `store's app-scoped deps are the SAME instances the parent holds` |
| 10 | `python3 documentation/mockups/mutation_harness.py` (the harness's own flags) | 87/87, 175/175 | `blank-start CTA stops withdrawing while a session runs (B27's guard) -> RED (2 test(s))`: `StartBlankGateTest > before the first emission the CTA is withheld not offered() FAILED` and `StartBlankGateTest > a workout is running — the blank-start CTA withdraws() FAILED`; the other registered case also RED; restored with status, diff, index and `AllTrainingsStore.kt` byte-identical |
| 11a | `ANDROID_SERIAL=emulator-5554 ./gradlew :app:app:connectedDebugAndroidTest` for `AllTrainingsExtensionDbVisibilityTest` and the four journey classes (`--max-workers=2 --continue`) | 738/738 | `Starting 22 tests`; fresh XML 22 cases, 0/0/0: DbVisibility 1, `RouteReachabilityTest` 15, `ApplicationBottomBarTest` 4, `UiGenerationSwapTest` 1, `AppRuntimeUiHandshakeDeviceTest` 1, the `@Test` count of the five sources |
| 11b | `ANDROID_SERIAL=emulator-5554 ./gradlew :feature:all-trainings:connectedDebugAndroidTest` (`--max-workers=2 --continue`) | 224/224 | ran as `connectedAndroidDeviceTest`; one case, `AllTrainingsScreenTest.pendingFeatureRewrite`, skipped, under `connected/androidMain` |
| 12 | relative PNG proof | GREEN | below |

The two U+2014 identities in the Native XML, read as raw bytes:

    classname="iosSimulatorArm64Test.io.github.stslex.workeeper.feature.all_trainings.mvi.store.StartBlankGateTest"
    name="no workout running — the drawn pair is whole[iosSimulatorArm64]"            dash at zero-based byte offset 19
    name="a workout is running — the blank-start CTA withdraws[iosSimulatorArm64]"    dash at zero-based byte offset 21

Each occurs exactly once, as `E2 80 94`, in every `StartBlankGateTest` Native XML of this record.
The Native names compared equal to Section 3.2 after stripping the classname prefix
`iosSimulatorArm64Test.io.github.stslex.workeeper.feature.all_trainings.` and the name suffix
`[iosSimulatorArm64]`, with no decoding step.

Relative PNG proof (Section 3.7): the base manifests, with exactly the 50 all-trainings paths
rewritten from `src/test/` to `src/androidHostTest/`, equal the head manifests byte for byte, at
commit 1 and again on the committed commit-2 tree; all 528 working-tree PNGs hash to their index
blobs; 13 Paparazzi owners; 50 all-trainings PNGs under `src/androidHostTest/snapshots/images`, 0
under `src/test`, all 100644 stage 0, all R100. No `recordPaparazzi` task ran in any invocation.

| Manifest | Base | Base, 50 paths rewritten | Head |
| --- | --- | --- | --- |
| Paparazzi mode/blob/path (456 entries, 13 owners) | `616a0ea5dcc85256a984bb710eb08a8b1110f1f41b1ae3a6e488909db8f65446` | `46f2f70f052aeaa500cfaee6834b343a5f21f717a0d684c0ead5a54767d833d5` | `46f2f70f052aeaa500cfaee6834b343a5f21f717a0d684c0ead5a54767d833d5` |
| Paparazzi path list (456) | `4782c53805144b129de6ccf933edadcdddc084f46c16fcc3d33de70d6037e733` | `64859f12035c03fab1d27c83c9aa12abd88286e9fec0dd5ef060e05572a65f2c` | `64859f12035c03fab1d27c83c9aa12abd88286e9fec0dd5ef060e05572a65f2c` |
| All PNGs mode/blob/path (528) | `06a1f2adb9b38b70463d5b9b46db881059c594cf879d98f13acfb2e78cfb4d39` | `8f4705371b62353ed92a33af735cddaba926992c24ca718fc5cd491c77b40513` | `8f4705371b62353ed92a33af735cddaba926992c24ca718fc5cd491c77b40513` |
| all-trainings blob set (50 sorted blob IDs) | `0d5f7c0ef86b9dc9052433f1e9f908fb8ebdc071cf63673b26388955e81dd3b0` | — | `0d5f7c0ef86b9dc9052433f1e9f908fb8ebdc071cf63673b26388955e81dd3b0` |

The 50 blob IDs, unchanged before and after (paths
`feature/all-trainings/src/androidHostTest/snapshots/images/io.github.stslex.workeeper.feature.all_trainings.golden_AllTrainingsGoldenTest_<method>_<light|dark>.png`):

| Method | Light | Dark |
| --- | --- | --- |
| `rowPlain` | `6ca814b9b662bec77c79e2e9277635b353454acf` | `b406b0c8c1c822e48c2113837c859ac15238d62c` |
| `rowLongName` | `d0cc0c98bce367fe82f0d4dd97bbec041f7a8b88` | `bd83eb2fae6f1d210efb8fdb8fb4015b2eff5c52` |
| `rowClamped` | `31371758d1c5e0adf998962a697b7362529d4b72` | `22a9de5dfcda6b431a407be3f8fb878705d6e7c0` |
| `rowActive` | `33cb69cc3844cddd9ae95fa54ccda02b5fde620b` | `66af226d936378604a9307eb4273366f2c4f29d5` |
| `rowSelected` | `d0ef8294765374ad016bd12f73616da11372d93f` | `ff5914eddd47eb41ab088127da07217a53c0bf8d` |
| `rowActiveSelected` | `1b1a00d720dcd93c7b50312b0cd181b90de2a740` | `1ded1384fe8fa2dd04e208c22ee0d49183f1262e` |
| `tagFilterBand` | `e3fce4e31f4997bf55b328bff62f1ff95d15ff1c` | `0264f277dd4ed156eb4ab95904847238656e6dc5` |
| `emptyState` | `3cb23c89fb6810d68b068297075ea57f28ab1945` | `cac5632eff66c82d79a389fd8ac44f9ed74a63a9` |
| `rowUnselectedInSelection` | `33b4e389672b978eef73bf3f5d6e936022bb70fa` | `c47be1ee58710e2bbeac317e60a1cb4b23c5f764` |
| `pagingLoading` | `ec7c9bf274ff725bc70ac06d4e21d1290bdc99fc` | `07987ad0b892d01c9e797e08c817624c112a8db0` |
| `pagingError` | `ae8b1138180e75b59c3c43ce93e0d47080e61463` | `ffa97faf929410002cd23e3dafebbc50d53853dc` |
| `confirmDialogContent` | `8f2edc55cbfc68fae19fa38ee57ee783436840d3` | `319282d654e5bf6ca4a33095c45595027f8da4f3` |
| `screenList` | `18ac3cf55c856ef537bd05c5cab7b83ec704b5db` | `b6d6fe3377e1c46958df5e76f24dc488595035e4` |
| `screenSelection` | `c3f31b31b0b027c781207fb98d909782a7a54151` | `8b4c68f1977008d362db9b604e5052f90ac7ce12` |
| `screenFirstRunEmpty` | `1ebe917f3926c5b90b67a463e75a6c95d5e4fe4d` | `9671ea6a87e0374f326e6de860f5752a7a8a48b0` |
| `filteredEmpty` | `308d8f44564346680570a3a966542a00ef0bfd7d` | `6b2a179f3504d0f51f79fe9730c5a370697f8e64` |
| `selectionEmptyFiltered` | `7d8ca7b165d0b0f7b7d1f30d497a849023ead847` | `206fdba9efcdf8a4f7027e0dde3ff8139f2c4a8e` |
| `selectionEmptyUnfiltered` | `6a9733ee8d124050681c589d705d7b52649ff7a9` | `23e5cd495aadb109e24ae410848c188d4f0da7d8` |
| `coldOpenLoading` | `72baf76ee8ebfbeeb2667286ae18340dd23a1229` | `8a6ca670f0dff592ba6759aa0f19c1791b614ec8` |
| `coldOpenError` | `70ecdbdf75f64c8250bb4e4b4d45f7b0a0dd4307` | `fc2a7b5c97608515df24afb2076fdbbe1829187d` |
| `screenColdOpen` | `c40abb8c286192e68e40b8cd896a2a74cd32bb21` | `ea3ed96cb33afe88fc0f9d69fec95b17f17e4a75` |
| `screenRefreshError` | `03a6ed9ba35cf34cd242485e091a0041bdc316d4` | `6b186e5ea89b85b68413089bdb56478f6ab9bb3d` |
| `screenFilteredEmpty` | `d429abe94e2f61ad6cd936bb5a8a330ff0276879` | `ebf8a5a9fdd1bf4a0ab68405464c9cace549469a` |
| `screenSelectionEmpty` | `75a0e245ddddc57c5ace0603c4de47f51358ca29` | `9e1b92e003dcdfc53574a5fa87301b3601732d30` |
| `screenSelectionEmptyUnfiltered` | `abde95315a90d9062dc76ccaee6c420448d80f2b` | `56e85e6ba38269ce8d6ca71300e6d1e1a4d79014` |

The device for every device gate in this record is the repository's AVD `Pixel_6_API_34`, serial
`emulator-5554`, the only attached device, started with `-avd Pixel_6_API_34 -port 5554 -no-window
-gpu swiftshader_indirect -noaudio -no-boot-anim -camera-back none -no-snapshot-save`: API 34,
fingerprint `google/sdk_gphone64_arm64/emu64a:14/UE1A.230829.050/12077443:userdebug/dev-keys`.
Window, transition and animator scales were 0.0, `accelerometer_rotation=1`, `user_rotation=0`
and the keyguard was not showing, recorded before and after every device run and identical each
time. Every device run started with no connected-result directory present (any left by an
earlier run was deleted by literal path first; Section 19.5 gives the Regression case), and no
run lost the device.

### 19.5 Commit 2 gates and repository, visual, and device evidence

Commit 2's content was gated before it was committed, then folded with the cross-check findings
(Section 19.7, F1 and F3, topology script only), committed, and only then were the official
controls (Section 19.6) and the Section 12.4 gates run on the committed tree `5343fda45`.

Before the commit, on the commit-2 working tree:

| Command | Executed | Parsed result |
| --- | ---: | --- |
| `python3 .github/scripts/assert_kmp_ui_source_topology.py` | GREEN | both Pythons, byte-identical; adds `all-trainings catalogs, semantic State, suppressions, previews, test identities, and explicit factory flow are exact`; all-trainings EN 15 and RU 23 placeholders, each `%1$d`, `%1$s` or `%2$s` |
| nine-module Native command, exactly the edited workflow's | 259/259 | every module fresh; all-trainings 10 files, 50 tests, 0/0/0 |
| `python3 .github/scripts/assert_kmp_ios_smoke.py` | GREEN | `feature:all-trainings: 50 executed / 0 skipped / 0 failed / 0 errored — verified 50 exact identities`; the other eight modules unchanged |
| nine-module `linkDebugTestIosArm64`, exactly the edited workflow's | 250/250 | the workflow's binary-assert step run verbatim: `9/9 linked`, every `test.kexe` fresh |
| workflow static checks | GREEN | YAML parses (Ruby Psych): jobs `build`, `release-bundles`, `kmp-ios-kit-smoke`, job name `KMP iOS kit smoke`, nine simulator tasks, nine link tasks, nine upload paths, `echo "$linked/9 linked"`; actionlint reports only the pre-existing `77:9: shellcheck reported issue in this script: SC2129:style:1:1`, before and after |

The fold changed only the topology script, which no Gradle task reads. After it, the topology gate
was GREEN under both Pythons with output byte-identical to the pre-fold run, the transcription audit
(Section 19.7) was still EQUAL, and the oracle re-read the fresh Native XML above with byte-identical
output. On the committed tree, control 23's pre- and post-GREEN (Section 19.6) ran the nine-module
Native command again: 259/259 each, oracle GREEN under both Pythons with the same all-trainings
line.

On the committed tree, after `./gradlew clean` (`74 actionable tasks: 51 executed, 23 up-to-date`,
not a positive gate), each Section 12.4 command ran as its own invocation, sequentially, so detekt
never ran beside tests:

| Command | Executed | Parsed result |
| --- | ---: | --- |
| `assembleDebug lintDebug testDebugUnitTest --continue` | 2598/2598 | 450 fresh host XML files across 32 modules, 3,171 test cases, 0/0/0 (2,850 unique module/classname/name triples; app:wear repeats its 87 suites under both flavors, 318 cases, and exercise-chart repeats 3 parameterized names); all-trainings 9 files, 49 cases; lint: 36 modules `Lint found no errors or warnings`, `app:app`, `app:dev` and `app:store` `Lint found 75 warnings` each, all dependency-version checks (GradleDependency 37, NewerVersionAvailable 32, AndroidGradlePluginVersion 6) in `gradle/libs.versions.toml` and `gradle-wrapper.properties`, none in source |
| `detekt --continue` | 63/63 | 55 fresh reports, 0 findings |
| `assembleDebugAndroidTest --continue` | 2169/2169 | GREEN; all-trainings `androidDeviceTest` packaging executed |
| `verifyPaparazziDebug` | 645/645 | 13 liveness lines, 456 golden cases executed for 456 images: all-trainings 50/50, all-exercises 52/52, archive 14/14, exercise 48/48, exercise-chart 30/30, home 42/42, kit 86/86, live-workout 60/60, past-session 30/30, core plan-editor 18/18, settings 12/12, single-training 12/12, start-mode 2/2; 167 fresh XML files, 1,728 cases, 0/0/0; all-trainings 99 cases (50 golden and 49 portable); no `recordPaparazzi` task |
| `:lint-rules:test` | 9/9 | 16 suites, 151 tests, 0/0/0 |
| `python3 documentation/personal_data_gate.py -v` | GREEN | `[PASS] no personal data in tracked files`; six excused lines, all pre-existing, in paths this branch does not touch |
| Smoke: `ANDROID_SERIAL=emulator-5554 ./gradlew connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.annotation=io.github.stslex.workeeper.core.ui.test.annotations.Smoke --continue --max-workers=2` | 2129/2129 | 15 fresh XML files, 44 unique cases: 41 passed and exactly the three documented skips `AllTrainingsScreenTest.pendingFeatureRewrite` (now `connected/androidMain`), `ArchiveScreenTest.pendingFeatureRewrite` (`connected/androidMain`) and `AllExercisesScreenTest.pendingFeatureRewrite` (`connected/debug`); 0 failures and errors. Then, as `run_smoke_ui_tests.sh` does, `assert_mvi_device_identities.py`: `core:ui:mvi (androidDeviceTest): 2 executed / 0 skipped / 0 failed / 0 errored — verified 2 exact identities` |
| Regression: the same with `…annotations.Regression` | 2129/2129 | 15 fresh XML owners, 88 cases, all executed, 0 skip, failure or error: app:app 49 (among them DbVisibility 1, `RouteReachabilityTest` 15, `ApplicationBottomBarTest` 4, `UiGenerationSwapTest` 1, `AppRuntimeUiHandshakeDeviceTest` 1, `UiAdmissionRaceTest` 3), core:data:database 35, core:data:exercise 1, feature:all-exercises 1, feature:wear-bridge 2, the other owners 0 — 7.8's membership |
| boundary, hygiene and PNG (static) | GREEN | `diff --check` clean for base..C1, C1..C2 and base..C2; at commit 2, 111 changed paths with renames (201 without), outside the module exactly the 11 code paths of Section 9.2, commit 1 touching 9 of them and commit 2 the 3 CI paths; the 96-path module set equal to the one constructed from Sections 3.1–3.3 and 9.1; the PNG proof of Section 19.4 repeated on the committed tree |
| mockup shell gate | static GREEN | checks 7 and 8 unverified locally (Section 19.8) |

The topology gate on the final tree was GREEN under both Pythons, byte-identical to the control
passes' ending GREEN. Before the Regression run, the 15 Smoke result directories (XML copied out
first) and 22 empty connected directories the Smoke run created were deleted by literal path.

### 19.6 Mandatory known-negative controls

All 33 controls of Section 13 — 1–17 and 19–34, 18 replaced by 26 — ran on the committed tree
`5343fda45`: fresh GREEN → one named observable RED → automatic exact restoration → fresh GREEN. No
mutation was committed.

The script- and PNG-scored controls (1–14, 17, 24, 29–34) ran in a detached worktree at
`5343fda45` (2,595 tracked files, porcelain empty) through a scratch harness that refuses to start
unless HEAD and the on-disk topology script equal the commit. Per control it requires an empty
porcelain, takes a fresh GREEN, snapshots every touched path (bytes and mode, or absence) and every
directory the mutation would create, applies one mutation whose text anchors occur exactly once,
and accepts a RED only with a non-zero exit, the failure header, no Traceback, every named fragment
present and every failure line naming the mutated thing. It restores in `finally` with SIGTERM,
SIGINT and SIGHUP blocked during the restore, proves sha256 and mode equal and created paths
absent, requires an empty porcelain, and takes a fresh GREEN whose output is byte-equal to the
pre-GREEN. The topology scorer ran exactly as CI runs it. The whole pass ran once with macOS Python
3.9.6 and once with Homebrew Python 3.14.8 as scorer: every RED and exit code (1) byte-identical
between the two and to the reviewed dry run, and all 50 restored path records equal to their HEAD
blobs. Control 24's scorer is a scratch PNG assertion that hashes the on-disk bytes of every PNG
and requires the 50 blobs, the blob-set hash, and the all-PNG and Paparazzi manifests to equal the
base manifests with the 50 paths rewritten; it checks its own rewrite against Section 3.7's
projections.

The Gradle- and device-scored controls (15, 16, 19–23, 25–28) ran in the branch worktree through a
second scratch harness with the same restore discipline; a live Gradle client child is terminated
before restoring. Every RED is quoted from fresh XML or console output, and a compile error would
have scored INVALID (no run printed an `e:` line). Scorers: one invocation of
`:feature:all-trainings:testAndroidHostTest :feature:all-trainings:iosSimulatorArm64Test
--continue` for 19–22 and 26–28 (GREEN: 249/249, host 49/49, Native 50/50); the nine-module Native
command and then the oracle under both Pythons for 23; root `detekt --continue`, alone, for 25; and
`ANDROID_SERIAL=emulator-5554 ./gradlew :app:app:connectedDebugAndroidTest
-Pandroid.testInstrumentationRunnerArguments.class=io.github.stslex.workeeper.app.UiAdmissionRaceTest
--continue --max-workers=2` for 15 and 16, with the connected result directory deleted before every
device run. Where nothing changed between two controls (same HEAD, empty porcelain, the next
control's touched file byte-equal to HEAD), one control's post-GREEN served as the next one's
pre-GREEN: 19 → 20 → 27 → 21 → 22 → 26 → 28 and 15 → 16.

| # | Mutation | Observable RED (quoted) | GREEN before/after |
| ---: | --- | --- | --- |
| 1 | delete `ui/components/TopBarMode.kt` | `feature:all-trainings: source manifest mismatch; missing=['src/commonMain/kotlin/io/github/stslex/workeeper/feature/all_trainings/ui/components/TopBarMode.kt'], extra=[]` | topology GREEN |
| 2 | copy `di/AllTrainingsScope.kt` into `src/main/kotlin/…/di/` | `feature:all-trainings: legacy source set src/main still contains ['src/main/kotlin/io/github/stslex/workeeper/feature/all_trainings/di/AllTrainingsScope.kt']` (plus the manifest's `extra=[…]` and `unexpected Kotlin-bearing source set src/main`) | topology GREEN |
| 3 | move `ui/components/TopBarMode.kt` to `ui/TopBarMode.kt` | `feature:all-trainings: source manifest mismatch; missing=['src/commonMain/…/ui/components/TopBarMode.kt'], extra=['src/commonMain/…/ui/TopBarMode.kt']` | topology GREEN |
| 4 | `import android.util.Log` in `ui/components/PagingTailKind.kt` | `feature:all-trainings: src/commonMain/…/ui/components/PagingTailKind.kt imports a platform API in commonMain: 'import android.'` | topology GREEN |
| 5 | rename the EN key `feature_all_trainings_paging_retry` to `…_renamed` (count kept) | `feature:all-trainings: exact private CMP catalog mismatch in feature/all-trainings/src/commonMain/composeResources/values/strings.xml; mismatched=["feature_all_trainings_paging_retry: expected='Retry', actual=None"], unexpected=['feature_all_trainings_paging_retry_renamed'], order=drifted` (plus two ownership-drift lines, old and new key) | topology GREEN |
| 6 | append a second word to the RU value of `feature_all_trainings_paging_retry` | `feature:all-trainings: exact private CMP catalog mismatch in feature/all-trainings/src/commonMain/composeResources/values-ru/strings.xml; mismatched=["feature_all_trainings_paging_retry: expected=<RU value>, actual=('string', 'feature_all_trainings_paging_retry', <mutated RU value>)"], unexpected=[], order=exact` (the two RU values are named here, not quoted) | topology GREEN |
| 7 | move the EN `feature_all_trainings_paging_retry` into `feature/settings/src/main/res/values/strings.xml` | `feature:all-trainings: resource ownership drift for feature_all_trainings_paging_retry; expected=['feature/all-trainings/src/commonMain/composeResources/values-ru/strings.xml', 'feature/all-trainings/src/commonMain/composeResources/values/strings.xml'], actual=['feature/all-trainings/src/commonMain/composeResources/values-ru/strings.xml', 'feature/settings/src/main/res/values/strings.xml']` (plus the EN catalog mismatch) | topology GREEN |
| 8 | `val titleRes: Int = 0` in `State` | `feature:all-trainings: Store State must stay semantic with exactly ((…the six Section 12.1 fields…)); actual=(…, ('titleRes', 'Int = 0'))` | topology GREEN |
| 9 | `statusLabel = resourceWrapper.getString(0)` in `PagingHandler`'s item map | `feature:all-trainings: src/commonMain/…/mvi/handler/PagingHandler.kt resolves all-trainings copy through ResourceWrapper.getString; use the private Compose resources` (plus the copy contract's `resolves copy in a handler: 'getString('`) | topology GREEN |
| 10 | `getString(TITLE)` inside `ClickHandler`'s bulk-confirm `updateStateImmediate` | `feature:all-trainings: updateStateImmediate in src/commonMain/…/mvi/handler/ClickHandler.kt resolves a resource inside the State lambda: 'getString('` (plus the copy-contract line) | topology GREEN |
| 11 | `uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES` on `TrainingRow`'s Dark preview | `…/ui/components/TrainingRow.kt contains forbidden platform/lookup API 'android.content'` and `… 'uiMode'` (plus `TrainingRow.kt preview contract requires '@Preview(name = "Dark", showBackground = true)' exactly once`) | topology GREEN |
| 12 | `LocalContext.current` and `context.appDeps<AllTrainingsGraph.Factory>()` in `AllTrainingsFeature` | `…/di/AllTrainingsFeature.kt contains forbidden platform/lookup API 'LocalContext'`, `… 'appDeps<'`, and `remaining Context.appDeps readers are not the exact 9 unported entries; expected={…the nine…}, actual={…, …/di/AllTrainingsFeature.kt: ['AllTrainingsGraph.Factory'], …}` | topology GREEN |
| 13 | delete `val allTrainingsGraphFactory: AllTrainingsGraph.Factory` and its KDoc from `AppRootDeps` | `feature:all-trainings: exact root-factory flow requires 'val allTrainingsGraphFactory: AllTrainingsGraph.Factory' once in app/common/src/main/kotlin/io/github/stslex/workeeper/app/common/di/AppRootDeps.kt` | topology GREEN |
| 14 | `factory.createAllTrainingsGraph()` before `rememberMetroStoreProcessor` | `feature:all-trainings: factory invocation must occur exactly inside retained Store creation` | topology GREEN |
| 15 | a second `(context.applicationContext as AppRootDepsHolder).appRootDeps()` inside the admitted `remember(currentPhase.id) { … }` in `App.kt` | device, `BUILD FAILED`, 738/738: `UiAdmissionRaceTest.admittedGeneration_composesTheRegion_andResolvesItsDependencies` `java.lang.AssertionError: an admitted region resolves the generation's app-scope deps expected:<1> but was:<2>`; the two retired cases passed | 738/738, 3/3 |
| 16 | hoist `val deps = remember(currentPhase.id) { …appRootDeps() }` above `if (admission.granted) {` | device, 738/738: `retiredGeneration_composesNothing_andResolvesNothing` `a retired generation must resolve NOTHING — not even the app root deps expected:<0> but was:<1>`; `retirementBetweenPublicationAndFrame_resolvesNothing` `the stale generation's region must resolve NOTHING expected:<0> but was:<1>`; the admitted case passed | 738/738, 3/3 |
| 17 | identity helper `= asContribution<AllTrainingsGraph.Factory>()` | `feature:all-trainings: extension identities must not bypass AppRootDeps via asContribution` (plus the identity-helper root-fragment line) | topology GREEN |
| 18 | archive-specific; replaced by 26 (Section 13) | — | — |
| 19 | `ListSurface.kt`: refresh `Loading` → `ListSurface.FIRST_RUN` | 249/249: host `ListSurfaceTest > an unsettled refresh with no rows is loading not empty` `org.opentest4j.AssertionFailedError: expected: <LOADING> but was: <FIRST_RUN>`, Native the same identity `kotlin.AssertionError: Expected <LOADING>, actual <FIRST_RUN>.`; also `loading outranks selection and the filter`, and on Native the scene `… could not find any node that satisfies: (TestTag = 'AllTrainingsColdOpen')`; passed/failed: host 47/2, Native 47/3 | 249/249 |
| 20 | `PagingTailKind.kt`: append `Error` → `PagingTailKind.NONE` | 249/249: host `PagingTailKindTest > a failed page draws the error footer not silence` `expected: <ERROR> but was: <NONE>`, Native `Expected <ERROR>, actual <NONE>.`, and the scene `… ((TestTag = 'AllTrainingsPagingError') && (hasAnyAncestorThat(TestTag = 'AllTrainingsList')))`; host 48/1, Native 48/2 | 249/249 |
| 21 | the scene's `setContent { AppTheme { AllTrainingsScreen(…) } }` body → `setContent { }` | 249/249: Native `AllTrainingsFeatureSceneIosTest.resourcesPagingBranchesSelectionAndActionsRenderAndDispatch[iosSimulatorArm64]` `Failed to perform isDisplayed check. Reason: Expected exactly '1' node but could not find any node that satisfies: (TestTag = 'AllTrainingsColdOpen')`; host 49/49 passed | 249/249 |
| 22 | production `AllTrainingsScreen.kt`: the selection-bar close `onClick = { consume(Action.Click.OnSelectionExit) },` → `onClick = { },` | 249/249: Native scene `kotlin.AssertionError: Expected <OnSelectionExit>, actual <OnClearTagFilter>.`; host 49/49 | 249/249 |
| 23 | rename the scene method to `renamedSceneSubstitute` | Gradle GREEN (259/259; all-trainings 50 cases passing), then the oracle under both Pythons: `native gate FAILED (every module was checked, so no verdict is hidden):` and `feature:all-trainings: expected exactly one testcase with classname='io.github.stslex.workeeper.feature.all_trainings.AllTrainingsFeatureSceneIosTest' name='resourcesPagingBranchesSelectionAndActionsRenderAndDispatch[iosSimulatorArm64]', found 0; parsed 50 testcase(s): ['io.github.stslex.workeeper.feature.all_trainings.AllTrainingsFeatureSceneIosTest.renamedSceneSubstitute[iosSimulatorArm64]', …]`; the other eight modules `ok:` | 259/259 and oracle GREEN |
| 24 | flip (XOR 0xFF) the last byte of `…golden_AllTrainingsGoldenTest_rowPlain_light.png` | `all-trainings blob drift: …rowPlain_light.png expected=6ca814b9b662bec77c79e2e9277635b353454acf actual=93ca7c642304cfe077eab0382fd757ebf329ea13`, `all-trainings blob-set hash drift: expected=0d5f7c0e…dd3b0 actual=6435154d…bf32`, `all-PNG mode/blob/path manifest drift: expected sha256=8f470537…0b513 entries=528, actual sha256=15355d92…9e5c entries=528`, and the Paparazzi mode/blob/path drift; the path list does not drift | PNG assertion GREEN |
| 25 | compile-valid overlong assertion at `commonTest/…/ui/components/PagingTailKindTest.kt:16` (a `kotlin.test` message argument) | root detekt, `BUILD FAILED`, 63/63: `> Task :feature:all-trainings:detekt FAILED`, `Analysis failed with 8 weighted issues.`, `…/PagingTailKindTest.kt:16:1: Line detected, which is longer than the defined maximum line length in the code style. [MaxLineLength]`, `…:16:1: Exceeded max line length (120) [MaximumLineLength]`, `…:16:22: Argument should be on a separate line (unless all arguments can fit a single line) [ArgumentListWrapping]`; all 8 findings in that file. Under the same mutation `:feature:all-trainings:testAndroidHostTest` 175/175, 49/49 | 63/63, 0 findings |
| 26 | `TrainingListItemMapper.kt`: `MINUTE_MS` and `HOUR_MS` exchanged in `relativeAgo`'s first two conditions | 249/249: Native scene `Expected <[in progress · started 5m ago, last: just now, last: 2h, last: 3d, never trained]>, actual <[in progress · started just now ago, last: just now, last: 2h, last: 3d, never trained]>.`; host 49/49 | 249/249 |
| 27 | `ClickHandler.kt`: `archivedCount = result.archivedCount,` → `archivedCount = 0,` | 249/249: host `ClickHandlerTest > OnBulkDeleteConfirm calls archiveTrainings and clears selection on success` `expected: <ShowBulkDeleteSuccess(archivedCount=2, blockedNames=[])> but was: <ShowBulkDeleteSuccess(archivedCount=0, blockedNames=[])>`; Native the same identity `Expected <ShowBulkDeleteSuccess(archivedCount=2, blockedNames=[])>, actual <ShowBulkDeleteSuccess(archivedCount=0, blockedNames=[])>.`; host 48/1, Native 49/1 | 249/249 |
| 28 | `bulkArchiveMessage`: `if (blockedNames.isEmpty()) {` → `if (true) {` | 249/249: Native scene `Expected <[1 training archived, 3 trainings archived, Archived 2, blocked: Legs, Push]>, actual <[1 training archived, 3 trainings archived, 2 trainings archived]>.`; host 49/49 | 249/249 |
| 29 | `ShowBulkDeleteSuccess(val message: String)` | `feature:all-trainings: src/commonMain/…/mvi/store/AllTrainingsStore.kt ShowBulkDeleteSuccess parameters must be exactly ['archivedCount: Int', 'blockedNames: ImmutableList<String>']; actual=['message: String']` | topology GREEN |
| 30 | `val label = getString(BULK_ARCHIVE)` in `ClickHandler.processBulkDeleteDismiss`, outside any State lambda | `feature:all-trainings: src/commonMain/…/mvi/handler/ClickHandler.kt resolves copy in a handler: 'getString('` (the copy contract alone) | topology GREEN |
| 31 | `BackHandler(enabled = processor.state.value.interceptBack) {` → `BackHandler {` | `feature:all-trainings: src/commonMain/…/ui/AllTrainingsGraph.kt back contract requires 'BackHandler(enabled = processor.state.value.interceptBack)' exactly once; found 0` | topology GREEN |
| 32 | EN `feature_all_trainings_exercise_count[one]` `%1$d exercise` → `%d exercise` | `compose-resource placeholder is not %N$d or %N$s with N >= 1: feature/all-trainings/src/commonMain/composeResources/values/strings.xml key feature_all_trainings_exercise_count[one] token '%d'` (plus the EN catalog mismatch) | topology GREEN |
| 33 | `MetroTestGraphHolder.graph.allTrainingsGraphFactory` → `MetroTestGraphHolder.graph` in the DbVisibility test | `feature:all-trainings: exact root-factory flow requires 'MetroTestGraphHolder.graph.allTrainingsGraphFactory' once in app/app/src/androidTest/kotlin/io/github/stslex/workeeper/app/AllTrainingsExtensionDbVisibilityTest.kt`, the line PR-G defines for `extra_root_fragments` (`{module}: exact root-factory flow requires {fragment!r} once in {path}`) | topology GREEN |
| 34 | a 50th `@Test`, `a fiftieth identity nobody declared`, in `TopBarModeTest` | `feature:all-trainings: exact test identities drifted in src/commonTest/…/ui/components/TopBarModeTest.kt; missing=[], unexpected=['a fiftieth identity nobody declared']` | topology GREEN |

For 15 and 16, GREEN is 738/738 with all three `UiAdmissionRaceTest` cases passing and the
exit-code file 0. For 23, the pre- and post-GREEN were 259/259 with the oracle GREEN under both
Pythons (`feature:all-trainings: 50 executed / 0 skipped / 0 failed / 0 errored — verified 50 exact
identities`). For 25, the pre- and post-GREEN were 63/63 with 55 fresh reports and 0 findings.

Three known negatives beyond Section 13 prove the two cross-check fixes (Section 19.7). They ran
with the dry-run harness under both Pythons on the commit-2 working tree after the fold, whose diff
is byte-identical to commit 2's (sha256 `59fcf5a2…8302`); each was GREEN before its fix:

| Addition | Mutation | Observable RED (quoted) |
| --- | --- | --- |
| F1a | truncate `mvi/mapper/TrainingListItemMapper.kt` to 0 bytes | `feature:all-trainings: src/commonMain/…/mvi/mapper/TrainingListItemMapper.kt copy contract requires 'internal suspend fun TrainingListItemDomain.toUi(' exactly once; found 0` and `… requires 'Clock.System.now().toEpochMilliseconds()' exactly once; found 0` |
| F1b | truncate `mvi/store/AllTrainingsStore.kt` to 0 bytes | `… AllTrainingsStore.kt ShowBulkDeleteSuccess parameters must be exactly ['archivedCount: Int', 'blockedNames: ImmutableList<String>']; actual=None` |
| F3 | `val archivedCount: Int = 0,` in `ShowBulkDeleteSuccess` | `… ShowBulkDeleteSuccess parameters must be exactly ['archivedCount: Int', 'blockedNames: ImmutableList<String>']; actual=['archivedCount: Int = 0', 'blockedNames: ImmutableList<String>']` |

That dry run reported `SUMMARY: 25/25 controls RED-as-named and restored; problems=[]` and `END
porcelain equals start: True`.

### 19.7 Deviations, spec gaps, and implementation decisions

- **Commit-1 topology delta (7.8 G1).** Commit 1 carries only `MODULES["feature:all-trainings"]`
  (the 95 `src` paths) and the all-trainings reader removed from `EXPECTED_APP_DEPS_READERS`; every
  all-trainings contract check is in commit 2. 7.8 G1's "11" → "10" text edit has no counterpart:
  PR-G computes both texts from `len(EXPECTED_APP_DEPS_READERS)`.
- **Rename similarity.** Every move is a `git mv`, but four files fall below git's default 50%
  similarity because this specification rewrites them, so default `git diff -M` and `git log
  --follow` show them as delete/add pairs: `mvi/mapper/TrainingListItemMapper.kt` R039 (D2 rewrites
  every resource line and adds nine accessor imports), `ui/components/TrainingsEmptyState.kt` R045
  (Section 5.4 imports and the 5.5 preview split in a 47-line file),
  `domain/AllTrainingsInteractorImplTest.kt` R016 (three repository fakes grow it from 53 to 226
  lines) and `mvi/handler/NavigationHandlerTest.kt` R035 (an in-file navigator replaces MockK).
  All four pair at `-M10%`. A pure-move commit would not compile, which Section 15's bisect-green
  rule forbids, and the code was not reshaped around the heuristic; 7.8's `36703afa1` shows the
  same pattern.
- **Build file.** With comments and blank lines removed it equals Section 10's block (48 lines).
  Besides the verbatim history comments (the metro and paparazzi plugin comments), the archive
  template's comments were added: two `api` rationale comments and the Compose-BOM and test-utils
  GUARD comments. None holds a dependency fragment or a forbidden build token. Gradle metadata:
  every declared edge is used by the source and no import lacks one; on Android
  `kotlinx-collections-immutable` and `material-icons-core` arrive only through the module's own
  edges.
- **Section 3.5's animation rationale is stale.** `org.jetbrains.compose.animation:animation:1.11.1`
  is also reachable through foundation on `androidCompileClasspath`. The explicit edge stays because
  `commonMain` imports `androidx.compose.animation` directly (`TrainingRow.kt`,
  `AllTrainingsScreen.kt`, `ui/AllTrainingsGraph.kt`); this is not Section 14's "edge wrong"
  condition.
- **Observation, not a change.** The public `AllTrainingsInteractorImpl` constructor names
  `core:data:exercise` repositories under an `implementation` edge, exactly as archive's
  `ArchiveInteractorImpl` does. Section 3.5 fixes `implementation`, and the only constructor caller
  (Metro in app/app) has its own edge.
- **Fakes fail fast on members no identity reaches** (Section 11.1, "exactly"). Every member an
  identity reaches behaves as the relaxed mock did: state applied; events, consumed actions and
  launches recorded; launches not run; the same default returns (`bulkPermanentDelete` accepts,
  the interactor fake's `deleteTrainings` returns 0 and `canPermanentlyDelete` false). Members no
  identity reaches (the store's `lastAction`, logger, `consumeOnMain`, `updateStateImmediate(state)`,
  `launchDefault` and `Flow.launch`; the interactor's three flows; the navigator's other members;
  every repository member but the three bulk operations) throw instead of returning relaxed
  defaults, as archive's fake does (7.8 Section 19.8). The fake `launch` returns `Job()`, which
  nothing reads. None of this is observable with the current production code.
- **Data-shape imports** (Section 11.1). `TagDataModel` and `ActiveSessionWithStats`, the
  specification's examples, are not imported, because the unreached repository members fail fast.
  The fakes import `BulkArchiveOutcome`, `ExercisePlanWrite`, `TrainingChangeDataModel` and
  `PlanUpdate` directly, never by qualified name, so PR-L's test-source-set exemption is still
  load-bearing.
- **Bulk-confirm order.** The old identity ran action → `coVerify` → `onSuccess`; the ported one
  runs the single recorded launch closure (action, then `onSuccess`) and then asserts the
  `archiveTrainings` argument, the state and the new event. A failure in either step still fails
  the identity.
- **"No cast needed".** `kotlin.test`'s `assertTrue` carries a contract, so the inherited
  `(event as Event.HapticClick).type` cast at `ClickHandlerTest.kt:139:39` is now smart-cast
  redundant and the compiler warns `No cast needed`. Section 11.1 allows no other assertion change,
  archive's identical helper gives the same warning (`ArchiveClickHandlerTest.kt:145:39`), and a
  warning is not a suppression; the line is kept.
- **Identity helper shape.** Section 6 writes the `allTrainings()` helper on one line; it is split
  over two lines like archive's (`ArchiveExtensionIdentityTest.kt:44-45`). The gate compares
  whitespace-compacted source, so the one-line fragment matches; the one-line form would be 111
  characters, within detekt's 120.
- **Golden test.** It moved byte-identically (R100); the KMP `androidHostTest` still runs JUnit 5
  `@ParameterizedTest`/`@EnumSource` through golden-harness, so no runner change was needed.
- **Native XML form.** Classnames carry the task prefix `iosSimulatorArm64Test.` and names the
  suffix `[iosSimulatorArm64]`; result files carry a hashed prefix (`TEST-iosSimulatorArm64Te-…`),
  as for archive. The two U+2014 names (Section 19.4) are the first non-ASCII test names run on
  Native in this repository.
- **Native scene decisions** (none changes a gate or an identity). Item 1 is asserted twice: the
  raw catalog as stored (34 values: 26 strings and both quantities of 4 plurals, P1's `%1$d`
  included) and the 14 placeholder-bearing entries filled through the formatter, a superset of
  both readings of "exact EN values". `settleUntil(matcher)` generalizes archive's settle-until-tag
  (still at most ten one-second steps) because the cold-open loading and error treatments reuse
  the `AllTrainingsPagingLoading` and `AllTrainingsPagingError` tags; each append tail is therefore
  required as that tag inside `AllTrainingsList`. State changes that are not paging branches
  (session flag, filter, selection over the same empty list) keep the same `PagingUiState` instance
  through `copy` and settle in one step; every new paging branch gets a new instance and settles
  until its tag. Beyond the minimum, the scene also asserts the two selected rows' check marks
  (none on an unselected row) and the resting bar's absence in selection. The mapper fixtures use
  non-round deltas (5m20s, 30s, 2h15m, 3d5h, none) at a fixed `NOW = 1_720_000_000_000L`, which
  pins flooring and makes control 26 observable in either reading of "swap".
- **AVD boot.** Every device stage (Sections 19.4 to 19.6) cold-booted the AVD. Its emulator log
  shows the `default_boot` load attempted and abandoned (`Change of GLES renderer detected`,
  `Failed to load snapshot 'default_boot'`, `The emulator is starting from scratch. Reason:
  different renderer configured`), then `Boot completed in 16396 ms`, `17093 ms` and `17013 ms`
  for the commit-1 gates, the device controls and the Section 12.4 suites. At shutdown
  `-no-snapshot-save` kept the snapshot from being written (`Snapshots have been disabled by the
  user, save request is ignored.`). No device run resumed from a snapshot; no gate is affected.
- **Accidental intent-to-add, measured as a no-op.** While rename similarity was measured in the
  first implementation stage, `git add -N .` ran once by mistake. Measured effect: `diff --cached
  --diff-filter=A` 0 entries and `ls-files --others --exclude-standard` 0 (the six local signing and
  google-services files are ignored); the index held only the `git mv` renames. Recorded because
  the procedure forbids whole-tree adds.
- **Commit 2 before its controls.** Commit 2 was created after the script-scored dry run but
  before the Gradle- and device-scored controls and the Section 12.4 gates, which the procedure
  lists before "Commit, signed". Every official control and repository gate then ran on the
  committed tree (7.8 Section 19.6 precedent), and none forced a fix.
- **Cross-check fixes in commit 2 (F1, F3).** An independent adversarial read of the uncommitted
  commit-2 script, mutating a scratch copy of the tree, found two gaps in the new extra checks,
  both fixed before the commit. F1: an existing but empty file passed `all_trainings_copy_contract`
  vacuously (a 0-byte mapper was GREEN); now only an absent file is the "cannot read" failure, and
  an existing file, empty or not, is counted. F3: `data_class_parameters` stripped defaults, so a
  defaulted `archivedCount: Int = 0` passed; it now keeps them, matching PR-G's `state_fields` parse
  and the strict reading of Section 12.1. The additions F1a, F1b and F3 (Section 19.6) prove both.
- **Decided, not changed (F2, F4).** Fragments are counted in raw source, comments included (F2):
  Section 12.1 says "holds exactly once each", PR-G's checks count raw source, Section 6 notes that
  "the topology scan reads comments too", and the TODO fragment is itself a comment. The pre-7.8
  comment at `android_build_unified.yml` lines 557–559, which names six modules before "the same
  nine", stays (F4): Section 12.1 changes only the two "eight modules" comments.
- **identity_names and device_fragments.** Section 12.1 writes "the two inherited names"; the entry
  reads them from the base's `AllTrainingsExtensionIdentityTest.kt`. "Archive's three" device
  fragments were built from Section 3.3.
- **Transcription.** `ALL_TRAININGS_ENTRY` was transcribed from this specification's tables, never
  generated from the files it checks, then audited by two independent parsers of this text:
  `COMPARE VERDICT: EQUAL (59 checks)` and, under both Pythons, `fields=34 checks=133 / COMPARE
  VERDICT: EQUAL`, whose perturbation self-check reports 3 differences. `resource_wrapper_readers`
  is an empty frozenset, so PR-G's generic check rejects any reader, and every field's container
  types equal `ARCHIVE_ENTRY`'s.
- **Control mutations.** Mutations 9, 10, 30 and 33 are not compile-valid (no `getString` import
  and no `TITLE` or `BULK_ARCHIVE` symbol in `ClickHandler`; no `resourceWrapper` left in the
  feature; a type mismatch in 33). Section 13 allows that for script-scored controls, whose scorer
  reads source; 7.8's control 10 had used a compile-valid suspend `getString`. Controls 9 and 10
  are detected twice (PR-G's generic clause and the new handler-token clause), 30 by the copy
  contract alone, as Section 13 requires. Interpretations: 19 → `FIRST_RUN`, because all-trainings
  has no `EMPTY` verdict (7.8 used `EMPTY`); 21 → the scene's own `setContent` body blanked, as in
  7.8; 22 → `OnSelectionExit` made a no-op in production; 26 → `MINUTE_MS` and `HOUR_MS` exchanged
  in `relativeAgo`'s first two conditions; 28 → `if (true)`, so the branch no longer reads
  `blockedNames`.
- **Scoring tools.** `documentation/mockups/mutation_harness.py`, 7.8's tool for controls 18–20,
  scored no control: it has no SIGTERM/SIGINT/SIGHUP conversion (7.8 Section 19.9), its flags lack
  `--no-configuration-cache` and `--console=plain`, and it keeps neither log nor XML (it prints at
  most eight failure lines), so a RED could not be quoted from fresh XML. Two scratch
  byte-restoring harnesses scored the controls instead, as 7.8 Section 13 allows. The repository
  harness itself ran as a focused gate (Section 19.4, row 10).
- **Control additions, not substitutions.** Control 23 was scored with the full nine-module Native
  command (workflow form, `--full-stacktrace`), so the oracle read fresh XML for all nine modules;
  control 25 also had a host compile-validity run under the same mutation; and controls 21, 22, 26
  and 28 ran in the same host-plus-Native invocation as 19, 20 and 27, so each shows the host
  staying 49/49: no Android host test reaches the scene, the mapper copy or the snackbar copy —
  Section 5.2's accepted consequence, measured.
- **Control 24 in commit 2's message.** The message lists control 24 among the "script-scored"
  controls; it was scored by the PNG assertion, as 7.8's control 24 was. The commit is not amended.
- **Re-pointed tech-debt anchors.** Two of the three links Section 9.2 re-points also carry line
  anchors into the moved files, re-measured at the new paths: `TrainingRow.kt` 62 → 64 (the name
  tag) and the row text's 60 → 62 (`combinedClickable`); `PagingHandler.kt` 51-54 → 53-56 (the
  `updateStateImmediate` mapping, which sat at 55-58 at the base).
- **Observations.** The module's device run prints `Starting 1 tests` and then `Finished 2 tests`
  for its single ignored method; the fresh XML is authoritative and holds exactly one skipped case.
  The compile keeps three inherited "only one @Inject-annotated constructor" notes (`ClickHandler`,
  `NavigationHandler`, `PagingHandler`) and the expected portable-BackHandler deprecation ("Use
  NavigationEventHandler instead", Section 4.3).

### 19.8 Rejected runs, local limitations, and the remote evidence boundary

Runs that did not measure what they appear to were excluded and their proof rerun freshly:

- The first launch of the host-plus-Native control group refused its own pre-GREEN for control 19
  although the run was green (`BUILD SUCCESSFUL in 1m 9s`, 249/249, host 49/49, Native 50/50): its
  freshness guard counted 29 non-actionable lifecycle tasks that print `UP-TO-DATE` under
  `--rerun-tasks` (`androidPreBuild`, `preAndroidMainBuild`, `generate*Assets`) as reuse. No mutation
  had been applied and the porcelain was empty. The guard was narrowed to test, compile, link,
  detekt and connected tasks (the `N actionable tasks: N executed` line stays the global proof) and
  the group relaunched from a fresh pre-GREEN.
- One attempt to record the device settings before commit 1's device gates passed `adb` through an
  unsplit zsh variable and executed nothing on the device; it was redone through a script. No gate
  was affected.
- The first agent of the commit-2 stage died on an infrastructure error (API 403) after launching
  the script-scored dry-run harness, which ran to completion unattended and exited by itself. A
  completion stage proved the working diff byte-identical to the pre-harness snapshot (sha256
  `ef8bd413…4c2d`) and read the log (`SUMMARY: 22/22 controls RED-as-named and restored;
  problems=[]`) without re-running anything. Dry runs are not the official control evidence;
  Section 19.6's passes are.
- Control 25's archived detekt XML copies are not the all-trainings reports: the harness's copy
  filter matched `all-trainings` in every report path, because the worktree is named
  `kmp-phase-7-9-all-trainings`, so all 55 reports were copied onto two names and the last, an
  empty report of another module, survived. The RED stands on the fresh console output quoted in
  Section 19.6 and on the 8 findings the harness parsed from the live report at run time.

Local limitations:

- The mockup shell gate (`--base "$(git merge-base origin/dev HEAD)" -v`, Homebrew Python 3.14.8)
  passes checks 1–6, 9 and 10. No headless browser is on `PATH`, so checks 7 and 8 are
  `UNMEASURED`; with the installed Chrome 154.0.8037.95 exposed as `chrome` through an exec wrapper,
  the gate stopped at its own timeout (`the browser did not return within 90s`). The permanent
  known negative `--target f52462c7` fails checks 6, 9 and 10, as required. Checks 7 and 8 are
  unverified locally; the remote `Mockup Appearance Gate` is their authority. This branch changes
  neither `documentation/mockups/pass2d.html` nor `AppColors.kt`.
- The nine iosArm64 device test binaries are linked, never run, by design: there is no device.
- Section 5.7's inherited locale property is accepted, not tested.
- Control 25's compile-validity run covered the Android host only. The three-argument
  `kotlin.test.assertEquals` is common API, so the Native compile is not in doubt, but it was not
  run.
- The device link did not run again on the committed commit-2 tree: that tree differs from the
  linked one only in the topology script, which no Gradle task reads.
- Host resources: swap use peaked at 8.48 GB during the repository gates (5.93 GB during commit 1's
  device link), and free disk fell to 10 GiB during the repository gates and stood at 13 GiB after
  them; no gate was interrupted or rerun.

This documentation commit must exist before its SHA, the push, GitHub's signature verification of
the three commits, the required contexts on the final head, the CI run and job IDs, and review
threads can exist. Those facts, and every review finding's classification, are recorded in the PR,
not predeclared here.

### 19.9 Documentation boundary

This commit updates the canonical facts the implementation made stale, in the places 7.8's
`1bafa081d` and `fbf6dc06e` updated for archive:

- `architecture.md`: all-trainings joins archive as a plain-shape factory-resolution exception;
  `AppGraph.allTrainingsGraphFactory` implements its `AppRootDeps` accessor; the feature/dialog
  Context readers fall from 10 to 9; the navigation reference implementation points at the
  `commonMain` paths.
- `ci-cd.md`: the `KMP iOS kit smoke` row, the KMP-shaped alias list, the Native job's module list,
  the all-trainings validator (50 target tuples), nine uploaded result directories, and nine linked
  device binaries (`N/9 linked`).
- `testing.md`: a shared all-trainings feature subsection; the device-test table row at
  `androidDeviceTest`; KMP-owned goldens in the visual-gate paragraph; the nine-module Native and
  device-link commands (`9/9`).
- `tech-debt.md`: the Section 5.3 UI Mapping Boundary row and the three re-pointed links.

Left untouched on purpose (Section 9.2: historical records, or stale before this phase and owned
by a separate refresh), each re-located on this tree:

- `documentation/features.md` lines 36 and 52 (the old `src/main` contract path and the
  `src/androidTest` device path);
- `.claude/skills/write-ui-test.md` line 24 (the `src/androidTest` device path);
- `documentation/research/*` (`domain-boundary-audit-codex.md`,
  `v2.3-quick-start-codebase-research.md`);
- `documentation/feature-specs/trainings.md` (line 827, the old `src/main/res` catalog path);
- archive's own stale tech-debt links, its UI Mapping Boundary row and its androidTest Coverage
  Gap row (lines 20 and 526 at the base);
- `graphify-out/` (`manifest.json`, `graph.json`).

Also untouched, because Section 9.2 does not name them: `testing.md`'s sentence "Both spellings
cover the KMP-shaped `:core:ui:kit`, `:core:ui:plan-editor`, and `:core:ui:start-mode` too", which
omitted archive before this phase and now omits all-trainings as well; and `tech-debt.md`'s
Resolved entry that records the graph's blocked-name shaping as moved to `ClickHandler`, which the
new row explains.
