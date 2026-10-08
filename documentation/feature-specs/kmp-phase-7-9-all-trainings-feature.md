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

Reserved for the implementation's documentation commit.
