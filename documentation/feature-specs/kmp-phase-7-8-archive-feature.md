# KMP Phase 7.8 — feature:archive becomes a shared feature entry

**Status:** IMPLEMENTED FOR REVIEW — MAINTAINER MERGE REQUIRED

**Authorized implementation baseline:** b1945ee15ed7a64be811d821d11d4d6e86000375 — exact
origin/dev at implementation entry (Section 19.1)

**Maintainer implementation GO:** 2026-10-01

**Target branch:** dev

**Live dev baseline:** 97b55d7318273e567c58b32dccbdf2919039aba2

**Completed prerequisite:** PR #273 — merged as
bbc650ca2acda43d7e0121bb4309d467284a40a1; signed and GitHub-Verified final
implementation head 84c3894c2822b7cc422308f25fde55f59e70cf3c

**Discovery date:** 2026-09-01

---

## 0. Authority, baseline, and authorization boundary

This document specifies the next bounded Kotlin/Compose Multiplatform increment. It is a
documentation-only proposal targeting dev directly. It does not authorize Gradle, Kotlin,
resource, test, workflow, generated-file, golden, ruleset, repository-setting, release, or
deployment changes.

The authority order for this measurement is:

1. live origin/dev at the exact SHA named above;
2. merged Phase 7.7 PR #273 and its exact final implementation head;
3. AGENTS.md, documentation/architecture.md, documentation/testing.md,
   documentation/ci-cd.md, and documentation/compose-state-discipline.md;
4. documentation/feature-specs/kmp-phase-2-probes.md and the current KMP convention plugins;
5. the completed Phase 7.5, Phase 7.6, and Phase 7.7 specifications and evidence;
6. documentation/graph-extension-arc/HANDOFF.md; and
7. the checked-in source topology, resources, tests, PNGs, and CI oracles at the exact baseline.

Historical KMP assessments are discovery aids only. Their old counts and classifications do not
override the fresh census in this document.

### 0.1 Reproduced baseline facts

| Claim | Reproduced evidence |
| --- | --- |
| Live target | origin/dev = 97b55d7318273e567c58b32dccbdf2919039aba2 |
| Phase 7.7 prerequisite | PR #273 is MERGED; merge commit bbc650ca2acda43d7e0121bb4309d467284a40a1 |
| Final implementation head | 84c3894c2822b7cc422308f25fde55f59e70cf3c |
| Final implementation signature | GitHub verified: true, reason: valid |
| Phase 7.7 correction | production preview suppression removed; only the two expressly authorized inherited Native test-name suppressions remain |
| Fresh prerequisite unified run | run 33484173741, SUCCESS |
| Build and Unit Tests | job 99780301416, SUCCESS |
| KMP iOS kit smoke | job 99780301315, SUCCESS |
| Unit Test Results | check 99782719929, SUCCESS |
| Detailed Unit Test Report | check 99782827371, SUCCESS |
| Fresh prerequisite mockup run | run 33484173781, SUCCESS |
| Mockup Appearance Gate | job 99780301451, SUCCESS |
| Independent prerequisite review | fresh Codex review on 84c3894c28 found no major issues |
| Review threads | zero total, zero unresolved |
| Post-merge dev movement | one later commit, 97b55d73, changes only AppNavigationHost background drawing; archive is unchanged and the archive graph call remains intact |
| Target-module drift | git diff 84c3894c..97b55d73 -- feature/archive is empty |

This specification PR targets dev and may contain only this documentation path. Phase 7.7 is
already merged, so no stacked branch or cascading merge is required. If dev moves before
implementation, the local implementation agent must fetch and reproduce the entry measurements
against the new exact dev SHA rather than treating this discovery SHA as permanent authorization.

### 0.2 Stable repository rules

The stable required contexts remain:

- Build and Unit Tests;
- KMP iOS kit smoke; and
- Mockup Appearance Gate.

Every positive Gradle gate must use:

    --rerun-tasks --no-build-cache --no-configuration-cache

Multi-task repository gates also use --continue where the existing specification requires it.
Executed task summaries must be quoted as N actionable tasks: N executed. Cached, up-to-date,
stale-XML, filtered-to-zero, compile-invalid, or transport-failed runs are not positive evidence.

Every implementation commit must be signed, locally verified, pushed without rewriting protected
history, and reported by GitHub as verified: true. Auto-merge remains disabled. The maintainer,
not the implementation agent, merges the pull request.

The only Phase 7.8 suppression additions authorized by this specification are the three exact
test-file annotations in Section 4.4. The two inherited production suppressions in Section 3.1
remain byte-semantically unchanged. No other suppression, compiler flag, ruleset, baseline,
filter, version, or convention-plugin change is authorized.

## 1. Decision and bounded exit claim

Proceed with feature:archive. It is the smallest coherent remaining navigation entry after
Phase 7.7. Its paging, one documented device placeholder, and fourteen owned goldens add proof
obligations, but the repository already contains KMP-capable Paging 3.5.0 artifacts, a KMP
Paparazzi/golden harness, explicit root-factory examples, private Compose Resources examples, and
Native production-scene examples.

The implementation may prove only this exit claim:

- feature:archive applies convention.kmpComposeLibrary and retains Metro and Paparazzi;
- all 25 production Kotlin files compile from commonMain for Android and iosSimulatorArm64;
- the exact private 27-identifier EN/RU catalog is owned by commonMain Compose Resources;
- the archive paging contract and visible Android behavior remain unchanged;
- all five non-golden suites and all 25 inherited identities execute on Android host and Native;
- the golden suite executes as 14 Android-host Paparazzi cases against the same PNG bytes;
- the one inherited Android device placeholder remains the same documented skip;
- one deterministic iOS production scene composes the real archive screen and proves resources,
  paging branches, segment selection, Back, restore, permanent-delete, and action dispatch;
- the Metro graph is reached through one explicit generation-owned root factory, never
  LocalContext or Context.appDeps;
- all four AppGraph extension identities remain true through the explicit accessor; and
- Android remains releasable with all repository PNG blobs unchanged.

This is a source-set, dependency, resource, test, and composition-root migration. It is not
permission to redesign Archive, change persistence behavior, replace Paging, add an iOS app,
rewrite global resources, or batch-migrate another feature.

## 2. Candidate selection — fresh adversarial comparison

The ten remaining navigation entries at the exact baseline are:

| Feature | Prod files | Prod lines | Unit files | Unit lines | Device files | EN string keys | PNGs |
| --- | ---: | ---: | ---: | ---: | ---: | ---: | ---: |
| archive | **25** | **1,409** | **6** | **503** | 1 | **25** | 14 |
| all-trainings | 31 | 1,795 | 10 | 1,041 | 1 | 26 | 50 |
| all-exercises | 34 | 2,062 | 11 | 1,471 | 1 | 30 | 52 |
| past-session | 29 | 2,378 | 8 | 2,067 | 0 | 20 | 30 |
| home | 39 | 3,081 | 15 | 1,924 | 0 | 35 | 42 |
| exercise-chart | 41 | 3,214 | 10 | 2,314 | 1 | 31 | 30 |
| settings | 51 | 3,324 | 12 | 2,638 | 1 | 65 | 12 |
| single-training | 39 | 3,635 | 6 | 1,582 | 1 | 39 | 12 |
| exercise | 46 | 4,141 | 13 | 2,890 | 2 | 53 | 48 |
| live-workout | 56 | 6,429 | 25 | 6,857 | 1 | 96 | 60 |

The EN column counts string elements only. Archive also owns two plural identifiers, for 27 exact
resource identifiers per locale.

Archive is smallest in production files, production lines, unit files, unit lines, and resource
surface. Its 14 PNGs are fewer than every golden-owning candidate except settings and
single-training, while its overall boundary remains substantially smaller than either. No sibling
feature, navigation serialization, persistence schema, database driver, compiler version, or
generic build-convention change is required.

If a fresh pre-implementation census changes this ordering or reveals a prerequisite outside
Section 9, STOP instead of silently choosing another target or widening the phase.

## 3. Measured baseline

All static measurements below come from exact live dev baseline
97b55d7318273e567c58b32dccbdf2919039aba2. The target module is byte-identical to final Phase 7.7
head 84c3894c2822b7cc422308f25fde55f59e70cf3c.

### 3.1 Exact production manifest and physical lines

| Current src/main Kotlin file | Lines |
| --- | ---: |
| di/ArchiveFeature.kt | 34 |
| di/ArchiveGraph.kt | 46 |
| di/ArchiveHandlerStore.kt | 9 |
| di/ArchiveHandlerStoreImpl.kt | 14 |
| di/ArchiveScope.kt | 8 |
| domain/ArchiveInteractor.kt | 34 |
| domain/ArchiveInteractorImpl.kt | 105 |
| domain/mapper/ArchivedItemDomainMapper.kt | 13 |
| domain/model/ArchivedItem.kt | 26 |
| domain/model/ExerciseTypeDomain.kt | 7 |
| mvi/handler/ArchiveClickHandler.kt | 107 |
| mvi/handler/ArchiveNavigationHandler.kt | 21 |
| mvi/handler/ArchivePagingHandler.kt | 79 |
| mvi/mapper/ArchiveUiMapper.kt | 52 |
| mvi/model/ArchivedItemUi.kt | 30 |
| mvi/store/ArchiveStore.kt | 97 |
| mvi/store/ArchiveStoreImpl.kt | 59 |
| ui/ArchiveGraph.kt | 51 |
| ui/ArchiveScreen.kt | 312 |
| ui/components/ArchiveBody.kt | 17 |
| ui/components/ArchiveListSurface.kt | 34 |
| ui/components/ArchivedItemRow.kt | 156 |
| ui/components/PagingTailKind.kt | 14 |
| ui/components/PagingTails.kt | 40 |
| ui/components/PermanentDeleteDialog.kt | 44 |
| **Production total** | **1,409** |

Other exact target ownership:

| Owner | Baseline |
| --- | --- |
| Build script | feature/archive/build.gradle.kts, 34 physical lines |
| EN catalog | src/main/res/values/strings.xml, 60 physical lines |
| RU catalog | src/main/res/values-ru/strings.xml, 56 physical lines |
| Non-golden tests | five files, 367 physical lines |
| Golden test | one file, 136 physical lines |
| Test total | six files, 503 physical lines |
| Device test | one src/androidTest file, 28 physical lines |
| Module manifest | none |
| Production previews | ArchiveScreen and ArchivedItemRow, each Light and Dark |
| Target goldens | 14 PNGs |
| Current target paths | 49: build file plus 48 files under src |

The exact inherited production suppressions are:

- @Suppress("UNCHECKED_CAST") in di/ArchiveFeature.kt; and
- @Suppress("TooManyFunctions") on domain/ArchiveInteractor.

They are not new Phase 7.8 suppressions. Their diagnostics and locations remain semantically
necessary unless a compile-valid, behavior-preserving removal is proved within the target path.
No replacement or additional production suppression is allowed.

### 3.2 Exact five-suite / 25-case portable manifest

The non-golden source contains exactly 25 test declarations:

**ArchiveClickHandlerTest — 9**

- OnSegmentChange updates selectedSegment and emits SegmentTick haptic
- OnSegmentChange to current segment is no-op
- OnRestoreClick emits ContextClick haptic
- OnUndoRestore emits ContextClick haptic
- OnDeleteDismiss does not emit haptic
- OnDeleteDismiss clears pending delete state
- OnPermanentDeleteClick emits LongPress haptic and stores target
- OnDeleteConfirm emits LongPress haptic and clears target
- OnDeleteConfirm without target does nothing

**ArchivePagingHandlerTest — 1**

- placeholder

**ArchiveMetaLineTest — 7**

- an exercise leads with its kind word
- a training leads with the other kind word
- the kind is first, ahead of the date
- tags come last, after the date
- no tags leaves no dangling separator
- the date is day-and-month, not a relative span
- a missing timestamp degrades to the bare word rather than a wrong date

**ArchiveListSurfaceTest — 4**

- rows win over everything
- an unsettled refresh with no rows is loading, not empty
- a failed first page is its own verdict
- settled with no rows is the empty state

**PagingTailKindTest — 4**

- appending draws the loading footer
- a failed page draws the error footer, not silence
- exhausted draws no footer at all
- idle mid-list draws no footer either

The names above are exact normalized identities. No identity may be renamed, deleted, merged,
disabled, filtered, or replaced with an alternate display name. The placeholder is inherited
contract debt, but deleting or silently upgrading it is outside this migration.

### 3.3 Golden and device ownership

ArchiveGoldenTest has seven parameterized methods over GoldenTheme and therefore fourteen exact
render cases:

- rowExercise — light and dark;
- rowTraining — light and dark;
- rowClamped — light and dark;
- pagingLoading — light and dark;
- pagingError — light and dark;
- screenExercisesNoRows — light and dark; and
- screenTrainingsNoRows — light and dark.

The exact PNG filenames are:

    io.github.stslex.workeeper.feature.archive.golden_ArchiveGoldenTest_pagingError_dark.png
    io.github.stslex.workeeper.feature.archive.golden_ArchiveGoldenTest_pagingError_light.png
    io.github.stslex.workeeper.feature.archive.golden_ArchiveGoldenTest_pagingLoading_dark.png
    io.github.stslex.workeeper.feature.archive.golden_ArchiveGoldenTest_pagingLoading_light.png
    io.github.stslex.workeeper.feature.archive.golden_ArchiveGoldenTest_rowClamped_dark.png
    io.github.stslex.workeeper.feature.archive.golden_ArchiveGoldenTest_rowClamped_light.png
    io.github.stslex.workeeper.feature.archive.golden_ArchiveGoldenTest_rowExercise_dark.png
    io.github.stslex.workeeper.feature.archive.golden_ArchiveGoldenTest_rowExercise_light.png
    io.github.stslex.workeeper.feature.archive.golden_ArchiveGoldenTest_rowTraining_dark.png
    io.github.stslex.workeeper.feature.archive.golden_ArchiveGoldenTest_rowTraining_light.png
    io.github.stslex.workeeper.feature.archive.golden_ArchiveGoldenTest_screenExercisesNoRows_dark.png
    io.github.stslex.workeeper.feature.archive.golden_ArchiveGoldenTest_screenExercisesNoRows_light.png
    io.github.stslex.workeeper.feature.archive.golden_ArchiveGoldenTest_screenTrainingsNoRows_dark.png
    io.github.stslex.workeeper.feature.archive.golden_ArchiveGoldenTest_screenTrainingsNoRows_light.png

The one device identity is
ArchiveScreenTest.pendingFeatureRewrite. It is annotated @Smoke and @Ignore with the exact reason:
Awaiting feature rewrite — see GH issue #93 for coverage scope. It remains one documented skip in
androidDeviceTest. This phase neither counts it as executed nor replaces its scope.

### 3.4 Exact feature-local resource catalog

Each locale owns exactly 25 string identifiers and two plural identifiers. Identifier sets,
ordering, placeholders, apostrophes, punctuation, plural categories, and visible values are
contractual.

| Identifier | EN | RU |
| --- | --- | --- |
| feature_archive_title | Archive | Архив |
| feature_archive_action_more | More | Ещё |
| feature_archive_segment_exercises | Exercises (%1$d) | Упражнения (%1$d) |
| feature_archive_segment_trainings | Trainings (%1$d) | Тренировки (%1$d) |
| feature_archive_action_restore | Restore | Восстановить |
| feature_archive_action_permanent_delete | Delete permanently | Удалить навсегда |
| feature_archive_kind_exercise | exercise | упражнение |
| feature_archive_kind_training | training | тренировка |
| feature_archive_label_archived | archived | в архиве |
| feature_archive_label_archived_since_format | archived since %1$s | в архиве с %1$s |
| feature_archive_meta_separator | · | · |
| feature_archive_empty_headline | Nothing archived | Архив пуст |
| feature_archive_empty_supporting_exercises | Archived exercises appear here for restore or permanent delete. | Здесь будут архивированные упражнения — для восстановления или удаления навсегда. |
| feature_archive_empty_supporting_trainings | Archived trainings appear here for restore or permanent delete. | Здесь будут архивированные тренировки — для восстановления или удаления навсегда. |
| feature_archive_dialog_permanent_delete_title | Delete ‘%1$s’ permanently? | Удалить «%1$s» навсегда? |
| feature_archive_dialog_permanent_delete_body_no_history | This action cannot be undone. | Это действие нельзя отменить. |
| feature_archive_dialog_impact_summary_empty | No session history affected | Сессии истории не затронуты |
| feature_archive_dialog_confirm_delete | Delete | Удалить |
| feature_archive_snackbar_restored_format | %1$s restored | «%1$s» восстановлено |
| feature_archive_snackbar_deleted_format | %1$s permanently deleted | «%1$s» удалено навсегда |
| feature_archive_snackbar_undo | Undo | Отменить |
| feature_archive_paging_loading | Loading | Загружаю |
| feature_archive_paging_error | Couldn’t load more | Не удалось загрузить дальше |
| feature_archive_paging_retry | Retry | Повторить |
| feature_archive_refresh_error | Couldn’t load the archive | Не удалось загрузить архив |

Plural feature_archive_session_count:

| Locale/category | Exact value |
| --- | --- |
| EN one | %d session |
| EN other | %d sessions |
| RU one | %d сессия |
| RU few | %d сессии |
| RU many | %d сессий |
| RU other | %d сессии |

Plural feature_archive_dialog_permanent_delete_body_with_history:

| Locale/category | Exact value |
| --- | --- |
| EN one | %1$d session of history will also be deleted. This action cannot be undone. |
| EN other | %1$d sessions of history will also be deleted. This action cannot be undone. |
| RU one | Также будет удалена %1$d сессия истории. Это действие нельзя отменить. |
| RU few | Также будут удалены %1$d сессии истории. Это действие нельзя отменить. |
| RU many | Также будет удалено %1$d сессий истории. Это действие нельзя отменить. |
| RU other | Также будет удалено %1$d сессии истории. Это действие нельзя отменить. |

No path outside feature:archive consumes these identifiers. The generated Res class remains
private to the module.

### 3.5 Declared dependencies and source visibility

The current module applies convention.composeLibrary, Metro, and Paparazzi. It declares:

| Current declaration | Use and Phase 7.8 decision |
| --- | --- |
| implementation project(:core:core) | app lifetime, dispatcher qualifier, date formatting; retain implementation |
| implementation project(:core:ui:kit) | public PagingUiState and UI components; promote to api |
| implementation project(:core:ui:mvi) | public Store and graph entry; promote to api |
| implementation project(:core:ui:navigation) | public NavGraphScope and navigation; promote to api |
| implementation project(:core:data:exercise) | repository contracts/models; retain implementation |
| testImplementation kotlin(test) | move to commonTest |
| testImplementation androidx paging-testing | unused by the current test source; remove |
| testImplementation core:ui:golden-harness | move to androidHostTest |
| androidTest Android bundle, Compose UI test, test-utils | move to androidDeviceTest |
| debugImplementation UI test manifest | move to androidDeviceTest with the Compose BOM |

Additional explicit KMP visibility required by the source surface:

- api(libs.cmp.ui), because archiveGraph exposes Modifier;
- api(libs.androidx.paging.common), because ArchiveInteractor and ArchiveStore.State expose
  PagingData;
- api(libs.coroutines.core), because ArchiveInteractor exposes Flow;
- implementation(libs.androidx.compose.paging), for LazyPagingItems and collection;
- implementation(libs.cmp.material.icons.extended), for Inventory2 and MoreVert; and
- implementation(libs.kotlinx.collections.immutable), for preview fixtures.

Paging remains version 3.5.0. paging-common, paging-compose, and the repository APIs used by this
module publish the required KMP/Native variants. No catalog or version change is authorized.

### 3.6 External consumers and root identity

| Surface | Exact external ownership |
| --- | --- |
| Direct module dependencies | app/common and app/app |
| Navigation registration | app/common AppNavigationHost calls archiveGraph |
| App graph contribution | app/app AppGraph aggregates ArchiveGraph.Factory |
| Root extension test | app/app ArchiveExtensionIdentityTest |
| Route definition | core:ui:navigation Screen.Archive |
| Route producers | settings navigation flow and its tests |
| Device route proof | app/app RouteReachabilityTest and BackStackStateRestorationTest |
| Feature resources | no external owner |

ArchiveExtensionIdentityTest has exactly four identities:

- extension resolves the store through the parent graph;
- store's app-scoped deps are the SAME instances the parent holds;
- the two handler-store keys resolve to ONE instance; and
- the emitter the Store bound itself into is the one the handlers delegate through.

The test currently obtains the factory through asContribution. At exit it must use
appGraph.archiveGraphFactory while preserving all four names and assertions. No production route
payload or serialization change is required.

### 3.7 Context readers and PNG integrity

Before this slice there are eleven executable Context.appDeps entry readers: ten navigation
features plus feature:app-dialogs. The AppRootViewModel mention is a comment and is not counted as
an executable reader.

Removing archive's reader leaves exactly ten executable readers: nine navigation entries plus the
separate app-dialog reader.

At the exact baseline:

| Manifest | Entries | Hash |
| --- | ---: | --- |
| Paparazzi owners | 456 across 13 owners | mode/blob/path e5c47e60890fae85a76ccd4e97cd4de7364d7c285b4da19e5e5c55e8f3d3e9ff |
| Paparazzi path list | 456 | 720a2f7de467d7472b99157952bc189123cfb1549afc8728c0626584965a0bc0 |
| All repository PNGs | 484 | mode/blob/path f2c4a01eed26b232856face73048dd831759fead6a3b173b86e848bedce4721d |
| Archive PNG blob set | 14 | 19dd79f0964d2b19d301370527b56a37eda937055bf6044f7d014f7a9ed0684b |

If only the fourteen archive paths move from src/test/snapshots/images to
src/androidHostTest/snapshots/images and modes/blobs remain identical, the projected hashes are:

| Manifest | Projected hash |
| --- | --- |
| Paparazzi path list | 4782c53805144b129de6ccf933edadcdddc084f46c16fcc3d33de70d6037e733 |
| Paparazzi mode/blob/path | 616a0ea5dcc85256a984bb710eb08a8b1110f1f41b1ae3a6e488909db8f65446 |
| All repository PNG mode/blob/path | 8b788adde487262635c52cb312f83b7db711812ab07d1ec90d1d948982d0a92a |

The implementation must use git mv semantics and prove all fourteen blob IDs unchanged. No
recordPaparazzi task, accepted golden update, tolerance change, or image rewrite is authorized.

### 3.8 Explicit local measurement limitation

A fresh local baseline invocation used the required flags:

    ./gradlew :feature:archive:assembleDebug :feature:archive:testDebugUnitTest \
      :feature:archive:assembleDebugAndroidTest \
      --rerun-tasks --no-build-cache --no-configuration-cache --continue --console=plain

It stopped before project configuration because Gradle 9.6.1 could not be downloaded from
services.gradle.org in this restricted environment: Network is unreachable. It is not positive or
negative gate evidence and no N/N summary is claimed.

The exact-head remote PR #273 checks in Section 0.1 are green and prove the completed prerequisite.
The later live-dev commit changes only AppNavigationHost background drawing and does not touch
feature:archive. The test declaration counts, file counts, resource catalog, consumers, and PNG
hashes in this document are direct static measurements. Before implementation edits, the local
implementation agent must run the fresh entry gates in Section 12.2 and record their actual N/N and
XML results. If that environment is unavailable, STOP.

## 4. Platform/API blockers — settled decisions

### 4.1 Paging remains common

The target uses PagingData, paging map operators, CombinedLoadStates, LoadState, LazyPagingItems,
and paging-compose collection. These APIs remain in commonMain through Paging 3.5.0. There is no
expect/actual paging facade, list materialization, fake page implementation, task filter, or
dependency upgrade.

The exact behavior remains:

- rows win over refresh/loading/empty alternatives;
- an unsettled refresh with zero rows is loading;
- a failed initial page has the refresh-error verdict;
- a settled empty page shows the empty state;
- append loading draws the loading footer;
- append error draws the error footer with retry; and
- exhausted or idle append draws no footer.

### 4.2 Android resource APIs become private CMP resources

All imports of feature.archive.R, androidx.compose.ui.res.stringResource, and
androidx.compose.ui.res.pluralStringResource leave commonMain. They are replaced by the private
generated feature resource package and org.jetbrains.compose.resources APIs.

The resource package is exactly:

    io.github.stslex.workeeper.feature.archive.resources

publicResClass must be absent or false. No generated StringResource, PluralStringResource, Android
Int resource ID, or resource wrapper payload may enter Store State, Action, Event, domain models,
or repository APIs.

### 4.3 Android root and preview APIs leave common

The following seams are removed from target commonMain:

- LocalContext;
- Context.appDeps;
- android.content.res.Configuration;
- Android uiMode preview parameters;
- androidx.annotation.VisibleForTesting; and
- Android feature R imports.

Both ArchiveScreen and ArchivedItemRow retain explicit Light and Dark previews. Each becomes two
portable preview functions using ThemeMode.LIGHT and ThemeMode.DARK through AppTheme. Preview
deletion, theme collapse, or suppression is forbidden.

ArchiveStoreImpl.NAME remains private. Removing @VisibleForTesting does not widen it.

### 4.4 Kotlin/Native comma-bearing identities

Kotlin/Native 2.4.10 rejects commas in Kotlin test declaration names before a Native test binary
or XML exists. Archive has exactly five inherited comma-bearing identities in three files:

- ArchiveMetaLineTest.kt:
  - the kind is first, ahead of the date;
  - tags come last, after the date;
  - the date is day-and-month, not a relative span;
- ArchiveListSurfaceTest.kt:
  - an unsettled refresh with no rows is loading, not empty; and
- PagingTailKindTest.kt:
  - a failed page draws the error footer, not silence.

Sole suppression exception: Phase 7.8 authorizes exactly
@file:Suppress("INVALID_CHARACTERS_NATIVE_ERROR") at file scope in those three commonTest files,
solely to preserve those five inherited exact identities under Kotlin/Native 2.4.10.

No production suppression, additional diagnostic, additional file, compiler flag, task filter,
baseline, ruleset change, display-name adapter, or identity rename is authorized. Removing each
annotation during implementation evidence is expected to fail compilation naming the affected
declarations. Those experiments are compile-invalid decision evidence, not observable RED
controls, and they must be automatically byte-restored.

If the compiler version or test framework changes before implementation and the suppressions are
no longer independently necessary, STOP and amend the specification rather than retaining an
unnecessary exception.

## 5. Resource, State, paging, and preview architecture

### 5.1 Private generated resource owner

Move the two catalogs byte-semantically:

    src/main/res/values/strings.xml
      -> src/commonMain/composeResources/values/strings.xml
    src/main/res/values-ru/strings.xml
      -> src/commonMain/composeResources/values-ru/strings.xml

All 27 identifiers, values, format placeholders, plural categories, and ordering remain exact.
The Android res owners disappear.

Composable-only copy is resolved with portable stringResource or pluralStringResource directly in
the UI and ArchiveGraph event renderer. Snackbar formatting preserves the current item name,
action label, and undo dispatch.

### 5.2 Preformatted State stays semantic

ArchiveStore.State keeps:

- exerciseSegmentLabel as String;
- trainingSegmentLabel as String; and
- ArchivedItemUi.metaLine as String.

This preserves Store semantics, golden fixtures, and current callers. It is not permission to move
resource handles into State.

ArchivePagingHandler resolves feature-local CMP copy in suspending Flow/Paging work before
updateState. The updateState lambda receives already-resolved plain strings and performs only a
State copy. Resource resolution inside updateState or updateStateImmediate is forbidden.

ResourceWrapper remains only for locale-aware formatDayMonth. It must not call getString or
getQuantityString for archive-owned static copy.

ArchiveUiMapper remains an internal deterministic pure seam. Its mapping functions receive the
already-resolved kind, archived/bare or archived-since text, separator, and formatted date output
needed for one item. The mapper joins:

    kind · archived-since · tags

with kind first, tags last, no dangling separator, and bare archived text for a missing timestamp.
ArchiveMetaLineTest must exercise this seam without Android resources, a composition, MockK, or a
generated resource handle.

### 5.3 Preview equivalence

The four portable preview declarations must be:

- ArchiveScreen Light;
- ArchiveScreen Dark;
- ArchivedItemRow Light; and
- ArchivedItemRow Dark.

They preserve current sample state, row payloads, rendered values, and action sinks. Numeric
fixtures may use named private constants when Detekt requires it; no production suppression is
added.

## 6. Explicit generation-owned shape-A factory flow

Archive has no route argument, so it uses the established explicit shape-A flow:

    admitted AppRootDeps
      -> AppGenerationContent
      -> AppNavigationHost
      -> archiveGraph(factory, modifier)
      -> ArchiveFeature(factory)
      -> rememberMetroStoreProcessor Store-creation lambda
      -> factory.createArchiveGraph().archiveStore

The exact contract is:

1. AppRootDeps adds val archiveGraphFactory: ArchiveGraph.Factory.
2. AppGraph explicitly overrides that accessor.
3. App.kt passes deps.archiveGraphFactory from the already-admitted generation.
4. AppNavigationHost accepts a required, non-null ArchiveGraph.Factory parameter and passes it to
   archiveGraph.
5. archiveGraph requires factory and constructs ArchiveFeature(factory).
6. ArchiveFeature changes from object to class with a required constructor factory.
7. createArchiveGraph is invoked exactly once, inside the retained
   rememberMetroStoreProcessor Store-creation lambda.
8. LocalContext, appDeps, asContribution in the identity test, a service locator, nullable
   fallback, static registry, or pre-admission factory resolution is forbidden.

The factory object may be passed through composition before Store creation; the extension graph
itself must not be created outside the retained Store lambda. A newly admitted entry owns its
extension graph/Store. A rejected or retired generation resolves nothing.

app/common changes its feature:archive edge from implementation to api because AppRootDeps and
AppNavigationHost expose ArchiveGraph.Factory. app/app retains its direct implementation
aggregation edge.

ArchiveExtensionIdentityTest uses appGraph.archiveGraphFactory.createArchiveGraph and preserves
its four exact identities. It must continue to prove:

- Store resolution;
- AnalyticsHolder and LoggerHolder parent identity;
- concrete/interface handler-store key identity; and
- emitter/store binding identity.

## 7. In scope

Only the following work is authorized after a later explicit GO:

- convert feature:archive to convention.kmpComposeLibrary while retaining Metro and Paparazzi;
- move the exact production Kotlin topology to commonMain;
- move the exact EN/RU catalog to commonMain Compose Resources;
- replace Android resource and preview APIs with portable equivalents;
- preserve Paging 3.5.0 behavior in common;
- make archive copy resolution suspend-safe and State-semantic as Section 5 requires;
- remove only @VisibleForTesting while keeping NAME private;
- move five non-golden suites to commonTest and port JUnit/MockK usage to kotlin.test plus
  deterministic in-file fakes;
- add the three exact test-only Native-name suppressions;
- move ArchiveGoldenTest and all fourteen PNGs to androidHostTest without rewriting them;
- move ArchiveScreenTest to androidDeviceTest unchanged;
- add exactly one iosTest production scene;
- add the explicit archive root-factory flow through the six named app/root consumer paths;
- extend exactly the three existing KMP topology/Native CI owners;
- update exactly the four documentation owners in Section 9.2 with actual evidence; and
- open a signed, green implementation PR for maintainer review without merging it.

## 8. Explicit non-goals

Phase 7.8 does not authorize:

- another feature or core module migration;
- an app/common KMP conversion or a permanent iOS application host;
- archive UI redesign, copy change, animation change, test-tag change, or behavior change;
- Paging version changes, page-size changes, eager list materialization, or new paging abstraction;
- repository, database, schema, migration, retention, restore, delete, or undo semantics changes;
- navigation route or serialization changes;
- device-test rewrite or removal of the documented skip;
- deletion, rename, merge, disablement, or replacement of any inherited test identity;
- golden recording, PNG rewriting, tolerance adjustment, or snapshot acceptance;
- public feature Res, resource handles in Store/domain payloads, or ResourceWrapper static copy;
- Android imports, expect/actual shims, reflection, service locators, or static factory registries
  in target common code;
- catalog, dependency version, Kotlin version, compiler flag, convention plugin, ruleset, Detekt
  baseline, lint rule, CI filter, or required-context changes;
- new suppression beyond Section 4.4 or a production suppression;
- auto-merge, merge, release, deployment, branch deletion, or repository-setting changes; or
- claiming the iosTest scene as UIKit, XCTest, a permanent iOS app, or release readiness.

## 9. Exact allowed change boundary and exit topology

### 9.1 Target module

At exit feature/archive contains exactly 50 tracked paths:

- one build file;
- 25 commonMain production Kotlin files;
- two commonMain Compose Resource catalogs;
- five commonTest Kotlin files;
- one androidHostTest golden Kotlin file;
- fourteen androidHostTest PNGs;
- one androidDeviceTest Kotlin file; and
- one iosTest Kotlin file.

The 25 production relative paths are unchanged:

    di/ArchiveFeature.kt
    di/ArchiveGraph.kt
    di/ArchiveHandlerStore.kt
    di/ArchiveHandlerStoreImpl.kt
    di/ArchiveScope.kt
    domain/ArchiveInteractor.kt
    domain/ArchiveInteractorImpl.kt
    domain/mapper/ArchivedItemDomainMapper.kt
    domain/model/ArchivedItem.kt
    domain/model/ExerciseTypeDomain.kt
    mvi/handler/ArchiveClickHandler.kt
    mvi/handler/ArchiveNavigationHandler.kt
    mvi/handler/ArchivePagingHandler.kt
    mvi/mapper/ArchiveUiMapper.kt
    mvi/model/ArchivedItemUi.kt
    mvi/store/ArchiveStore.kt
    mvi/store/ArchiveStoreImpl.kt
    ui/ArchiveGraph.kt
    ui/ArchiveScreen.kt
    ui/components/ArchiveBody.kt
    ui/components/ArchiveListSurface.kt
    ui/components/ArchivedItemRow.kt
    ui/components/PagingTailKind.kt
    ui/components/PagingTails.kt
    ui/components/PermanentDeleteDialog.kt

They move under:

    feature/archive/src/commonMain/kotlin/io/github/stslex/workeeper/feature/archive/

The five commonTest relative paths are:

    mvi/handler/ArchiveClickHandlerTest.kt
    mvi/handler/ArchivePagingHandlerTest.kt
    mvi/mapper/ArchiveMetaLineTest.kt
    ui/components/ArchiveListSurfaceTest.kt
    ui/components/PagingTailKindTest.kt

The golden owner is:

    feature/archive/src/androidHostTest/kotlin/io/github/stslex/workeeper/feature/archive/golden/ArchiveGoldenTest.kt

The fourteen PNGs retain their exact filenames under:

    feature/archive/src/androidHostTest/snapshots/images/

The device owner is:

    feature/archive/src/androidDeviceTest/kotlin/io/github/stslex/workeeper/feature/archive/ArchiveScreenTest.kt

The new Native owner is:

    feature/archive/src/iosTest/kotlin/io/github/stslex/workeeper/feature/archive/ArchiveFeatureSceneIosTest.kt

The catalogs are:

    feature/archive/src/commonMain/composeResources/values/strings.xml
    feature/archive/src/commonMain/composeResources/values-ru/strings.xml

No target file remains under src/main, src/test, or src/androidTest. No unexpected androidMain,
iosMain, commonTest helper, test fixture, manifest, generated owner, or extra resource catalog is
allowed.

### 9.2 Exact root, CI, and documentation boundary

Exactly six app/root consumer paths may change:

    app/app/src/main/java/io/github/stslex/workeeper/di/AppGraph.kt
    app/app/src/test/kotlin/io/github/stslex/workeeper/di/ArchiveExtensionIdentityTest.kt
    app/common/build.gradle.kts
    app/common/src/main/kotlin/io/github/stslex/workeeper/App.kt
    app/common/src/main/kotlin/io/github/stslex/workeeper/app/common/di/AppRootDeps.kt
    app/common/src/main/kotlin/io/github/stslex/workeeper/host/AppNavigationHost.kt

Exactly three CI/gate paths may change:

    .github/scripts/assert_kmp_ui_source_topology.py
    .github/scripts/assert_kmp_ios_smoke.py
    .github/workflows/android_build_unified.yml

Exactly four documentation paths may change:

    documentation/architecture.md
    documentation/ci-cd.md
    documentation/testing.md
    documentation/feature-specs/kmp-phase-7-8-archive-feature.md

Outside feature/archive, those thirteen paths are the complete allowed boundary. A need for any
other path is a STOP.

## 10. Gradle and public API contract

The build shape is:

~~~kotlin
plugins {
    alias(libs.plugins.convention.kmpComposeLibrary)
    alias(libs.plugins.metro)
    alias(libs.plugins.paparazzi)
}

compose.resources {
    packageOfResClass = "io.github.stslex.workeeper.feature.archive.resources"
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
            implementation(libs.androidx.compose.paging)
            implementation(libs.cmp.material.icons.extended)
            implementation(libs.kotlinx.collections.immutable)
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
~~~

The external dependencies block additionally owns:

- androidHostTestImplementation core:ui:golden-harness;
- androidDeviceTestImplementation the Android test bundle;
- androidDeviceTestImplementation Compose UI test JUnit4;
- androidDeviceTestImplementation platform(androidx Compose BOM);
- androidDeviceTestImplementation Compose UI test manifest; and
- androidDeviceTestImplementation core:ui:test-utils.

Apply gradle/golden-gate.gradle.kts unchanged. Add no paging-testing dependency unless a new
portable test actually imports its API; such a new test is outside the exact identity contract and
therefore would first require a specification amendment.

The implementation must verify Gradle metadata rather than blindly copy this sketch. API visibility
is load-bearing where public feature types expose the dependency. An implementation-only
dependency may not leak through public signatures.

## 11. Test and Native scene contract

### 11.1 Portable suites

Move the five non-golden test files to commonTest. Replace JUnit Jupiter and MockK with kotlin.test,
coroutine-test, and deterministic in-file fakes/spies. Do not add shared helper files.

Android host and Native must each contain exactly the 25 normalized identities in Section 3.2,
with zero failure, error, or skip. Suite distribution is exactly 9/1/7/4/4. The three exact
file-scope suppressions in Section 4.4 are required unless the specification is amended.

ArchiveMetaLineTest continues to prove:

- kind token first;
- date token after kind;
- tags last;
- no dangling separator;
- day-and-month formatting; and
- bare archived fallback for missing timestamp.

ArchiveListSurfaceTest and PagingTailKindTest remain pure decision tests. ArchiveClickHandlerTest
continues to prove state transitions and haptic emission. The inherited placeholder remains
visible and executable rather than filtered away.

### 11.2 Android host goldens

Move ArchiveGoldenTest to androidHostTest and port only the runner/parameterization mechanics
needed by the established KMP golden harness. The seven method names, Light/Dark cases, rendered
production subjects, fixtures, and 14 filenames remain exact.

The PNG moves use git mv. Before and after manifests must prove:

- 14 archive entries;
- identical file modes;
- identical blob IDs;
- archive blob-set hash unchanged; and
- the projected repository hashes in Section 3.7.

verifyPaparazziDebug must execute all fourteen archive cases and the shared liveness gate must
accept them. recordPaparazziDebug is forbidden.

### 11.3 Android device compatibility

Move ArchiveScreenTest byte-semantically to androidDeviceTest. Its @Smoke ownership, class,
pendingFeatureRewrite method, @Ignore reason, and one documented skip remain exact.

The canonical device suite must continue to discover it as a skip. A filtered-to-zero module, a
deleted placeholder, or a newly executed empty method is not equivalent.

The existing app route/back-stack journeys that cover Archive remain green. No new device journey
is required by this migration.

### 11.4 Deterministic iOS production scene

Add exactly:

    class ArchiveFeatureSceneIosTest
    method resourcesPagingBranchesAndActionsRenderAndDispatch

The name contains no comma. The scene uses runComposeUiTest and the production ArchiveScreen, not
a duplicated test UI. It supplies deterministic settled PagingData and proves in one bounded
scene:

- the private resource catalog resolves;
- exercise and training segment labels render;
- a settled empty branch renders;
- populated exercise and training rows render;
- initial loading and refresh error can be reached deterministically;
- append loading and append error tails render;
- segment selection dispatches OnSegmentChange;
- Back dispatches Navigation.Back;
- restore dispatches OnRestoreClick;
- permanent delete opens the production dialog path and dispatches confirm/dismiss as applicable;
  and
- action collection observes the exact production actions without suppressing dispatch.

The scene may use deterministic fixtures and semantics tags already owned by production. It may
not invent a platform host, repository, database, fake Store implementation, permanent UIKit
claim, screenshot baseline, or additional test identity.

The exact feature Native oracle is 26 identities: the 25 portable cases plus this one scene.

## 12. CI ownership and positive verification

### 12.1 Minimum stable-CI extension

assert_kmp_ui_source_topology.py adds feature:archive with:

- exact 50-path topology;
- no src/main, src/test, or src/androidTest residue;
- no Android imports or expect/actual shims in common;
- exact private EN/RU catalogs;
- no generated resource handle in Store/domain payloads;
- no ResourceWrapper getString/getQuantityString use for archive copy;
- updateState lambdas free of resource resolution;
- exact preview count/names/theme modes;
- exact three test-only suppressions and no others;
- exact factory flow and one Store-lambda invocation;
- exact AppRootDeps/AppGraph/App/host/graph pass-through;
- app/common api and app/app implementation edges;
- root identity test using the explicit accessor;
- exact reader-count reduction; and
- no forbidden build declaration.

assert_kmp_ios_smoke.py adds the exact five suite/classname memberships and 25 names from
Section 3.2 plus:

    io.github.stslex.workeeper.feature.archive.ArchiveFeatureSceneIosTest
    resourcesPagingBranchesAndActionsRenderAndDispatch

The workflow extends the Native command from seven modules to eight, uploads the Archive Native
XML directory, and retains the stable job/context name KMP iOS kit smoke. The oracle requires
exactly 26 Archive tuples and zero failures/errors/skips.

The aggregate build/test/Paparazzi tasks discover Archive through existing conventions. No other
workflow, script, task filter, or required context changes.

### 12.2 Entry gate before implementation edits

After this documentation PR merges and the maintainer gives a separate GO:

1. fetch all remote refs;
2. resolve the exact authorized implementation baseline on dev;
3. verify this specification is merged and is an ancestor of that baseline;
4. verify PR #273 is merged and no conflicting open implementation PR exists;
5. create an isolated clean worktree;
6. verify commit signing locally before the first commit;
7. reproduce Section 2, topology, resource, reader, and PNG measurements; and
8. run fresh positive baseline gates.

Minimum target baseline commands:

    ./gradlew :feature:archive:assembleDebug \
      --rerun-tasks --no-build-cache --no-configuration-cache --console=plain

    ./gradlew :feature:archive:testDebugUnitTest \
      --rerun-tasks --no-build-cache --no-configuration-cache --console=plain

    ./gradlew :feature:archive:assembleDebugAndroidTest \
      --rerun-tasks --no-build-cache --no-configuration-cache --console=plain

Parse fresh XML. The five non-golden suites must total 25 executed identities with zero
failure/error/skip. The golden cases are separately accepted only by the Paparazzi gate. The
device XML must show the exact documented skip when the canonical device gate runs.

Run the current seven-module Native command and exact oracle before adding Archive. Record the
actual N/N and XML totals; do not copy prior evidence.

### 12.3 Focused implementation gates

After the target/root implementation:

    python3 .github/scripts/assert_kmp_ui_source_topology.py

    ./gradlew :feature:archive:assembleDebug \
      --rerun-tasks --no-build-cache --no-configuration-cache --console=plain

    ./gradlew :feature:archive:testAndroidHostTest \
      --rerun-tasks --no-build-cache --no-configuration-cache --console=plain

    ./gradlew \
      :core:ui:kit:iosSimulatorArm64Test \
      :core:ui:navigation:iosSimulatorArm64Test \
      :core:ui:mvi:iosSimulatorArm64Test \
      :core:ui:start-mode:iosSimulatorArm64Test \
      :core:ui:plan-editor:iosSimulatorArm64Test \
      :feature:image-viewer:iosSimulatorArm64Test \
      :feature:plan-editor:iosSimulatorArm64Test \
      :feature:archive:iosSimulatorArm64Test \
      --rerun-tasks --no-build-cache --no-configuration-cache --console=plain

    python3 .github/scripts/assert_kmp_ios_smoke.py

    ./gradlew :app:common:assembleDebug \
      --rerun-tasks --no-build-cache --no-configuration-cache --console=plain

Run the four exact ArchiveExtensionIdentityTest identities with the repository's supported test
filter and parse fresh XML. Run the focused existing Archive route/back-stack device identities on
an explicit API-34 device serial and parse fresh XML. Every accepted positive invocation reports
N/N executed.

### 12.4 Repository and visual gates

After a real clean build, run:

    ./gradlew assembleDebug lintDebug testDebugUnitTest \
      --rerun-tasks --no-build-cache --no-configuration-cache --continue --console=plain

    ./gradlew detekt \
      --rerun-tasks --no-build-cache --no-configuration-cache --continue --console=plain

    ./gradlew assembleDebugAndroidTest \
      --rerun-tasks --no-build-cache --no-configuration-cache --continue --console=plain

    ./gradlew verifyPaparazziDebug \
      --rerun-tasks --no-build-cache --no-configuration-cache --console=plain

    ./gradlew :lint-rules:test \
      --rerun-tasks --no-build-cache --no-configuration-cache --console=plain

    python3 documentation/personal_data_gate.py -v

Run the canonical Smoke and Regression device suites from documentation/testing.md, using a
resolved explicit serial, required flags, and fresh XML. Preserve every documented skip and reject
transport failures or stale outputs.

Run git diff --check, exact boundary assertions, resource assertions, PNG manifests, and the local
mockup shell gate where the host can execute it. If the browser direction is locally unavailable,
say so exactly and use the authoritative remote Mockup Appearance Gate; do not claim local proof.

### 12.5 Final diff and remote proof

Before delivery:

- compare the exact authorized base to final head;
- assert every changed path belongs to Section 9;
- assert the exact target topology and no legacy residue;
- assert all five portable suites and 25 identities on Android host and Native;
- assert one exact Native scene and 26 total target Native tuples;
- assert the one device skip remains exact;
- assert all 14 archive golden blobs are unchanged;
- assert all repository PNG hashes equal their projected values;
- assert no suppression beyond the two inherited production annotations and three authorized
  test-file annotations;
- assert no golden was recorded or rewritten;
- verify every commit signature locally and on GitHub;
- push fast-forward only;
- keep the PR open and auto-merge disabled;
- wait for every required context;
- request a fresh @codex review on final head;
- classify every finding as correct, correct-but-already-decided, wrong, or correct-and-new;
- answer and resolve every thread only after evidence supports resolution; and
- record exact run/job IDs, final SHA, signatures, reviews, and remaining qualified risks.

## 13. Mandatory known-negative controls

Every applicable control uses fresh GREEN → one named observable RED → automatic exact
restoration → fresh GREEN. Mutations use documentation/mockups/mutation_harness.py or an equally
safe byte-restoring harness. No mutation is committed.

1. Remove one exact target path; topology names it.
2. Reintroduce one src/main production file; topology names legacy residue.
3. Keep the path count but move one file to a wrong relative path; topology names missing/extra.
4. Insert one Android import in commonMain; topology names the platform import.
5. Rename one EN resource key while preserving count; topology names the exact mismatch.
6. Change one RU resource value; topology names the exact mismatch.
7. Move one resource to a wrong owner; topology names resource ownership drift.
8. Add a generated resource handle or Int ID to State; topology names the payload violation.
9. Call ResourceWrapper.getString for archive copy; topology names the forbidden lookup.
10. Resolve a resource inside updateState; topology names the State-lambda violation.
11. Restore Android uiMode to a common preview; topology names the platform preview API.
12. Restore LocalContext or appDeps; topology names the forbidden root reader.
13. Remove archiveGraphFactory from AppRootDeps; topology names the missing accessor.
14. Move createArchiveGraph outside the Store lambda; topology names factory-placement drift.
15. Resolve the admitted AppRootDeps twice; the focused admission identity observes two, not one.
16. Resolve AppRootDeps during a rejected/retired generation; the focused admission identities observe one,
    not zero.
17. Make the identity test use asContribution; topology names the bypass.
18. Swap kind/date order; the exact ArchiveMetaLine identity fails on Android host and Native.
19. Classify unsettled empty as empty; the exact surface identity fails on both targets.
20. Suppress append error; the exact paging-tail identity fails on both targets.
21. Blank the production Native scene; the semantics assertion fails.
22. Suppress one scene action; the exact dispatch assertion fails.
23. Rename the Native scene while keeping 26 passing tests; the XML oracle names the missing exact
    tuple and unexpected substitute.
24. Change one archive PNG byte; the blob/hash assertion fails before any accepted golden update.
25. Add one compile-valid Detekt violation in target/commonTest; root Detekt names the file/rule.

The Section 4.4 suppression-removal experiments are intentionally separate. Kotlin/Native rejects
them before XML exists, so they are compiler decision evidence and must not be reported as RED
controls.

## 14. STOP conditions

Stop before implementation or delivery if:

- the live implementation baseline has not been freshly reproduced;
- PR #273 is not merged before implementation entry;
- the specification PR is not merged or the maintainer has not given a new explicit GO;
- a conflicting implementation PR or unclassified local worktree exists;
- signing cannot be proved;
- fresh target baseline tests do not reproduce 25 identities;
- any Section 4.4 suppression is insufficient or a fourth suppression appears necessary;
- Paging 3.5.0 does not resolve for Android and iosSimulatorArm64;
- feature resources cannot remain private and payload-free;
- ResourceWrapper static-copy calls or resource resolution inside State lambdas becomes necessary;
- the exact paging, Store, route, restore/delete/undo, or haptic behavior cannot be preserved;
- the explicit factory requires a service locator, nullable fallback, static registry, reflection,
  pre-admission graph creation, or another root path;
- the four AppGraph extension identities cannot remain exact;
- common previews require deletion, Android APIs, or production suppression;
- the iosTest production scene cannot run on available iOS simulator infrastructure;
- Android appearance requires recording or accepting changed goldens;
- any PNG blob changes;
- a path outside Section 9 is necessary;
- a generic Gradle guarantee is missing from current convention ownership;
- local simulator/device/toolchain infrastructure required for honest evidence is unavailable; or
- any positive gate, required remote context, or review remains unresolved.

A STOP produces a prerequisite proposal or amended documentation-only specification. It never
silently widens the implementation.

## 15. Signed, bisect-green implementation commit plan

After this specification merges and the maintainer gives a separate GO, use three English
Conventional Commits:

1. **refactor(kmp): share archive feature entry**
   - target build/source/resources/tests/goldens/device move/Native scene;
   - explicit root-factory flow and app/common API edge;
   - focused compile, host, Native, identity, device, and PNG evidence.
2. **ci(kmp): gate shared archive feature**
   - exact topology/resource/root/reader contract;
   - eight-module Native workflow, Archive XML upload, and exact 26-tuple oracle;
   - all mandatory controls and fresh repository gates.
3. **docs(kmp): record Phase 7.8 evidence**
   - actual commands, N/N summaries, XML identities, suppressions, controls, PNG hashes, commit
     SHAs, CI runs, review dispositions, and delivery state;
   - update only canonical architecture/testing/CI facts made stale by implementation.

Each commit is signed, locally valid, GitHub Verified, and green against its own tree. The first
commit may not depend on an uncommitted CI oracle from the second. The documentation commit
contains no production fix. Do not locally squash away this evidence structure.

## 16. Exit criteria

Phase 7.8 is complete only when:

- this specification was merged and a later explicit maintainer GO was recorded;
- target production compiles from commonMain for Android and iosSimulatorArm64;
- exact 50-path topology and two private catalogs hold;
- all 27 EN/RU identifiers, values, placeholders, and plural categories are unchanged;
- Paging behavior, visible copy, previews, tags, State, actions, events, route, haptics,
  restore/delete/undo, and persistence effects are unchanged;
- ResourceWrapper is used only for date formatting and no resource resolution occurs in State
  lambdas;
- exact explicit root-factory flow resolves AppRootDeps once for an admitted generation and zero
  times for rejected generations, while creating the feature graph exactly once per retained Store;
- all four extension identities use archiveGraphFactory and pass;
- all 25 inherited identities execute on Android host and Native with zero skip/failure/error;
- exactly the three test-file suppressions preserve the five comma-bearing names;
- the one production Native scene executes and the exact 26 target tuples validate;
- the Android device placeholder remains one exact documented skip;
- all 14 golden cases execute and every PNG blob is unchanged;
- all positive gates and all mandatory controls have fresh accepted evidence;
- repository assembly, lint, unit, Detekt, Android-test, Paparazzi, lint-rules, Smoke, Regression,
  personal-data, and remote contexts are green;
- no out-of-scope path, rule, version, baseline, filter, setting, release, or deployment changed;
- commits are signed and GitHub Verified;
- fresh final-head review findings are classified, answered, and resolved; and
- the implementation PR remains open, non-draft, mergeable, and auto-merge disabled for the
  maintainer.

## 17. Remaining ordered roadmap

Phase 7.8 removes one more navigation Context.appDeps reader and leaves nine navigation feature
entries plus the app-dialog reader.

After Phase 7.8, the next frontier must be remeasured. The current size order begins
all-trainings, all-exercises, past-session, and home, but no next feature is pre-authorized.
Continue one bounded feature entry at a time, then settle the app-dialog activity/recovery boundary,
convert real app/common only after every entry is portable, and add the permanent iOS host through
real app/common. Runtime/recovery, database/filesystem, image acquisition, authentication/Drive,
observability, signing, TestFlight, and release remain later separately measured phases.

A throwaway iosApp that bypasses app/common remains forbidden.

## 18. No implementation authorization

This document is specification-only. Opening or merging its documentation PR records the measured
contract but does not authorize implementation. Phase 7.8 implementation may begin only after:

1. PR #273 is merged;
2. this specification PR is merged into dev;
3. the exact merged dev tree is remeasured;
4. every entry condition in Section 12.2 passes; and
5. the maintainer gives a new, separate, explicit GO.

Until then, no production, test, Gradle, workflow, script, generated, golden, ruleset, repository,
release, or deployment mutation is permitted.

## 19. Implementation evidence

This section records the bounded implementation authorized by the separate maintainer GO given on
2026-10-01 (Section 18, item 5). That GO authorized exactly Section 7 and nothing in Section 8; it
did not relax any scope, STOP condition, or proof requirement. Section 3.7 remains the historical
baseline measurement and is not rewritten here.

### 19.1 Entry, drift, and authorization baseline

At entry, `origin/dev` was `b1945ee15ed7a64be811d821d11d4d6e86000375`. This specification's blob
was unchanged on it (`6cc56c68d412e115abdc3c88c8e3baf5a419c24d`), its PR #276 was merged and an
ancestor, and PR #273 was `MERGED` with merge commit `bbc650ca2acda43d7e0121bb4309d467284a40a1`
and head `84c3894c2822b7cc422308f25fde55f59e70cf3c`. There was no open PR. The only remote branch
matching `archive` or `7-8`, `feature/kmp-c1-archive-slice`, is a July 2026 Phase C.1 branch with
zero commits ahead of `dev` and no PR; it does not implement this phase. The implementation branch
`feature/kmp-phase-7-8-archive` was created in an isolated worktree from that exact SHA, and a
dangling `commit-tree -S` probe verified `Good` against the configured ED25519 signing key before
the first commit.

`dev` had moved 275 commits (41 first-parent) past the Section 0.1 baseline `97b55d73`: Wear OS and
release work, with no KMP commit after this specification itself. The maintainer's planner had
measured `fd0f9365`; live `dev` was one first-parent merge later (#306, which changes only
`versionName`/`versionCode`). Every row was reproduced at the live SHA:

| Item | Specification | Planner (`fd0f9365`) | Measured (`b1945ee1`) | Verdict |
| --- | --- | --- | --- | --- |
| D1 all-repository PNGs | 484, `f2c4a01e…` | 528 (+44 added, 0 changed, 0 removed) | 528; +44 A (app/wear 20, wear-emulator-acceptance 14, feature-specs 6, metadata-wear 4), 0 M, 0 D | EXPECTED |
| D1 fresh baseline mode/blob/path | — | `fc2dda16…` | `fc2dda1621c889b3c9811ae66feb72bbef0c23db1adf3267e5e5f706996fcde3` | EXPECTED |
| D1 fresh projection after the move | `8b788add…` (superseded) | `06a1f2ad…` | `06a1f2adb9b38b70463d5b9b46db881059c594cf879d98f13acfb2e78cfb4d39` | EXPECTED |
| D2 boundary files changed | — | unified workflow 18, ci-cd 31, AppGraph +`WearBridgeDeps`, testing 1 | identical (18/31/1/1; AppGraph adds only `WearBridgeDeps`) | EXPECTED |
| D3 archive's KMP dependencies | — | 69 files, +3745/−748 (method not stated) | `97b55d73..b1945ee1` and `97b55d73..fd0f9365` alike, module set `core/core`, `core/data/dataStore`, `core/data/database`, `core/data/exercise`, `core/ui/kit`, `core/ui/mvi` (`core/ui/navigation` unchanged): 69 files, +3745/−748 with rename detection on (`-M`) and off (`--no-renames`) alike. Adding kit's new `core/ui/design-tokens`: 84 files, +4313/−748 with `--no-renames`; 75 files, +4127/−562 with `-M`, where nine kit → design-tokens moves pair as renames: seven binary `.ttf` fonts and the two 93-line OFL licence texts (`licenses/OFL-Archivo.txt`, `licenses/OFL-IBMPlex.txt`), which account for the whole 186-line difference. No KMP convention plugin changed | EXPECTED |
| I1 `git diff 97b55d73.. -- feature/archive` | empty | empty | empty (0 files) | HELD |
| I2 archive PNG blob set (14) | `19dd79f0…` | `19dd79f0…` | `19dd79f0964d2b19d301370527b56a37eda937055bf6044f7d014f7a9ed0684b` | HELD |
| I3 Paparazzi manifests (456 / 13 owners) | `e5c47e60…`, `720a2f7d…` | same | same; projections `616a0ea5…`, `4782c538…` valid | HELD |
| I4 catalog | kotlin 2.4.10, paging 3.5.0, composeGradle 1.11.1 | same | same | HELD |
| I5 Section 2 order; Context.appDeps readers | archive smallest; 11 | same | archive row exact; order unchanged; 11 | HELD |
| I6 conflicting PR or branch | none | none | none (see above) | HELD |

The Section 2 census reproduced archive's row exactly (25 / 1,409 / 6 / 503 / 1 / 25 / 14). Three
cells of other features moved with the Wear arc without changing the order: home production lines
3,081 → 3,085, settings production lines 3,324 → 3,326, live-workout unit lines 6,857 → 6,882.
Every Section 3.1–3.7 static measurement (25 per-file line counts, 49 paths, both catalogs, the
25 test names, 14 PNG names, both inherited suppressions, consumers, 11 readers) reproduced.

Section 3.7's all-repository projection `8b788add…` is superseded by `06a1f2ad…` for one reason:
44 non-archive PNGs were added on `dev` after the baseline. The four manifest formats were first
validated by reproducing all four Section 3.7 baseline hashes and both projections exactly.

### 19.2 Commits and changed boundary

| Commit | SHA | Boundary and verification |
| --- | --- | --- |
| `refactor(kmp): share archive feature entry` | `36703afa14b54590188af921f4a22eca1c117196` | target module, six root consumers, minimal topology delta; locally valid SSH signature; GitHub `verified: true`, `reason: valid` |
| `ci(kmp): gate shared archive feature` | `07e8c6815c916ab984ff0ef9acf806450fbc88c5` | exactly the three Section 9.2 CI paths; locally valid SSH signature; GitHub `verified: true`, `reason: valid` |
| `docs(kmp): record Phase 7.8 evidence` | `1bafa081df26cefaa2147c534e626d67475d1feb` | exactly the four Section 9.2 documentation paths (`architecture.md`, `ci-cd.md`, `testing.md` and this specification); locally valid SSH signature; GitHub `verified: true`, `reason: valid` |
| `fix(kmp): give archive's session count a positional placeholder` | `206583ff0a32e1349930228e1fd51e00b38b8431` | decision P1 (Section 19.8): the six catalog items, `PermanentDeleteDialog`, the Native scene's raw-catalog expectation, and the topology gate; locally valid SSH signature; GitHub `verified: true`, `reason: valid` |
| `ci(kmp): cite decision P1 beside the archive catalog table` | `14264931da70e24da762642ab419460e9b105919` | the topology gate's table comment only; locally valid SSH signature; GitHub `verified: true`, `reason: valid` |
| `docs(kmp): complete Phase 7.8 decision records` | `fbf6dc06e5a736998941a97f2881930dfcfc7aa8` | this specification and `architecture.md`'s plural example; locally valid SSH signature; GitHub `verified: true`, `reason: valid` |
| `ci(kmp): reject %% and index-0 placeholders in Compose catalogs` | `040811182baa852c4b04d03c4c3e0273fb79d542` | decision P1a (Section 19.11): the placeholder gate only; locally valid SSH signature |
| `docs(kmp): record the tightened placeholder rule` | this commit | this specification only |

Against the authorized base `b1945ee1`, the eight commits change 111 paths (counted without
rename pairing; the five follow-up commits touch only paths the first three already changed): 98
under `feature/archive` — the 48 legacy `src/main`, `src/test` and `src/androidTest` paths
removed, 49 added (the 48 moved files plus the one Native scene) and the build file modified — and
exactly the 13 Section 9.2 paths outside it. No other path changed.
`feature/archive` tracks exactly 50 paths at exit with no legacy residue. All 14 PNGs, the golden
test and the device test are 100%-similarity renames; the two catalogs were too in `36703afa1`,
before decision P1 changed their six session-count items.

### 19.3 Fresh baseline before any edit

Every load-bearing Gradle invocation in this section and below ran immediately after
`./gradlew --stop` with `--rerun-tasks --no-build-cache --no-configuration-cache --console=plain`
(plus `--continue` where specified); every summary below is `N actionable tasks: N executed`.

| Command (baseline tree) | Executed | Parsed result |
| --- | ---: | --- |
| `:feature:archive:assembleDebug` | 104/104 | target assembled |
| `:feature:archive:testDebugUnitTest` | 173/173 | 5 XML suites, 25 cases 9/1/7/4/4, the 25 Section 3.2 identities, 0/0/0 |
| `:feature:archive:assembleDebugAndroidTest` | 223/223 | device APK assembled |
| current seven-module Native command (`--continue`) | 211/211 | 21 XML suites, 94 cases, 0/0/0; `assert_kmp_ios_smoke.py` green for all seven modules |
| `:feature:archive:connectedDebugAndroidTest` (Smoke) on `emulator-5554` | 224/224 | one case, `ArchiveScreenTest.pendingFeatureRewrite`, skipped |

The device is the repository's Pixel 6 API 34 arm64 AVD, serial `emulator-5554`, the only attached
device for every device gate in this record; rotation was `accelerometer_rotation=1`,
`user_rotation=0` throughout.

### 19.4 Commit 1 focused evidence

| Command | Executed | Parsed result |
| --- | ---: | --- |
| `python3 .github/scripts/assert_kmp_ui_source_topology.py` | GREEN | archive's 49 exact `src` paths; 10 readers |
| `:feature:archive:assembleDebug` | 214/214 | Android and iOS klib targets |
| `:feature:archive:testAndroidHostTest` | 175/175 | 5 suites, 25 cases 9/1/7/4/4, exact identities, 0/0/0 |
| eight-module Native command (`--continue`) | 235/235 | archive: 6 suites, 26 cases (25 + the scene), 0/0/0; the seven existing modules unchanged and green |
| `:app:common:assembleDebug` | 315/315 | explicit consumer compiled |
| `:app:app:testDebugUnitTest --tests '*ArchiveExtensionIdentityTest*'` | 574/574 | the four exact identities, 0/0/0 |
| `:feature:archive:verifyPaparazziDebug` | 176/176 | 14 golden cases executed for 14 images; `Visual gate live: 14 … for 14` |
| focused `:app:app:connectedDebugAndroidTest` | 735/735 | `RouteReachabilityTest.archiveOpensFromSettingsAndSettingsReturns`, `BackStackStateRestorationTest.backStackDepthSurvivesActivityRecreation`, the three `UiAdmissionRaceTest` identities; 0/0/0 |
| `:feature:archive:connectedDebugAndroidTest` (Smoke) | 224/224 | `ArchiveScreenTest.pendingFeatureRewrite` the one skip, now under `connected/androidMain` |

All 14 PNG moves are `git mv` renames at 100% similarity; the index after the move reproduced the
fresh projections exactly: all PNGs 528 `06a1f2ad…`, Paparazzi 456 across 13 owners `616a0ea5…`,
path list `4782c538…`, archive blob set `19dd79f0…`. No `recordPaparazzi` task ran in any
invocation. The 14 blob IDs, unchanged before and after:

| PNG (`feature/archive/src/androidHostTest/snapshots/images/…ArchiveGoldenTest_`) | Blob |
| --- | --- |
| `pagingError_dark` | `ffa97faf929410002cd23e3dafebbc50d53853dc` |
| `pagingError_light` | `ae8b1138180e75b59c3c43ce93e0d47080e61463` |
| `pagingLoading_dark` | `07987ad0b892d01c9e797e08c817624c112a8db0` |
| `pagingLoading_light` | `ec7c9bf274ff725bc70ac06d4e21d1290bdc99fc` |
| `rowClamped_dark` | `5688b842de6864aaebd80bc79318fdb172183e80` |
| `rowClamped_light` | `bbf7065d7a2c05b96bafd5f1799c66472ea05d7f` |
| `rowExercise_dark` | `e913e9b9e59a560644f1ef4d068ff3c5457cf70c` |
| `rowExercise_light` | `33d6d2593752a28d90f89c08582eb22318b8531b` |
| `rowTraining_dark` | `4bfcf69ecf5934e5392e510a796b275674ce6eaf` |
| `rowTraining_light` | `e69b097a076baf2ccd9b50648c67d9b0ed84184b` |
| `screenExercisesNoRows_dark` | `4d2328d669bb931f1e5e482434e79262ef24f64d` |
| `screenExercisesNoRows_light` | `54c875394f074aa102b8599267b92f16be7e0c8b` |
| `screenTrainingsNoRows_dark` | `caa34c60ffb98947570b698afa13aab878001300` |
| `screenTrainingsNoRows_light` | `404cc09cae7905f4c1230adb60faf593ddd2cf8c` |

### 19.5 Section 4.4 compiler experiments

Each file-scope suppression was removed in turn through `mutation_harness.py` against
`:feature:archive:compileTestKotlinIosSimulatorArm64` and byte-restored automatically (restoration
re-verified by blob hash). Each run was compile-invalid, as Section 4.4 predicts, and is decision
evidence only, never a RED control:

| File | Kotlin/Native diagnostic | Declarations named |
| --- | --- | --- |
| `ArchiveMetaLineTest.kt` | `Name contains illegal characters: ","` ×3 (84/84 executed) | `the kind is first, ahead of the date`; `tags come last, after the date`; `the date is day-and-month, not a relative span` |
| `ArchiveListSurfaceTest.kt` | `Name contains illegal characters: ","` (80/80) | `an unsettled refresh with no rows is loading, not empty` |
| `PagingTailKindTest.kt` | `Name contains illegal characters: ","` (80/80) | `a failed page draws the error footer, not silence` |

Exactly the five Section 4.4 identities, and nothing else, depend on the three annotations.

### 19.6 Repository, visual, and device evidence (commit-2 tree)

After `./gradlew clean`, each Section 12.4 command ran as its own invocation, sequentially, so
Detekt never ran beside tests:

| Command | Executed | Parsed result |
| --- | ---: | --- |
| `assembleDebug lintDebug testDebugUnitTest --continue` | 2378/2378 | 432 fresh host XML files across 32 modules, 3,050 tests, 0/0/0 |
| `detekt --continue` | 63/63 | GREEN |
| `assembleDebugAndroidTest --continue` | 2169/2169 | GREEN |
| `verifyPaparazziDebug` | 643/643 | 13 liveness lines, 456 golden cases executed for 456 images (archive 14 for 14) |
| `:lint-rules:test` | 9/9 | GREEN |
| `python3 documentation/personal_data_gate.py -v` | GREEN | only the pre-existing excused entries |
| the exact CI Native command (eight modules, `--continue --full-stacktrace`) | 235/235 | `assert_kmp_ios_smoke.py` GREEN for all eight modules; archive 26 executed, 26 exact identities |
| `assert_kmp_ui_source_topology.py`, `assert_mvi_source_topology.py` | GREEN | archive contract, 10 readers |

The canonical device suites from `documentation/testing.md` ran with the required flags plus
`--continue --max-workers=2` on `emulator-5554`, each after deleting every connected result
directory (zero stale directories remained), with the XML copied out before the next suite:

| Suite | Executed | Parsed result |
| --- | ---: | --- |
| Smoke | 2129/2129 | 15 fresh XML files; 44 unique cases: 41 executed and exactly the three documented skips `ArchiveScreenTest.pendingFeatureRewrite`, `AllTrainingsScreenTest.pendingFeatureRewrite` and `AllExercisesScreenTest.pendingFeatureRewrite`; zero failures and errors. Archive's skip is now reported from `connected/androidMain`. |
| Regression | 2129/2129 | 15 fresh XML owners (Phase 7.7's 14 plus the newer `feature:wear-bridge`); 88 unique cases, all executed, zero skip, failure or error: `app:app` 49, `core:data:database` 35, `core:data:exercise` 1, `feature:all-exercises` 1, `feature:wear-bridge` 2, the other owners 0. |

The device rotation settings were `accelerometer_rotation=1`, `user_rotation=0` before and after
every device run.

The whitespace check (`diff --check`) is clean for every commit. The local mockup shell gate ran
with Homebrew Python 3.14: its static checks 1–6, 9 and 10 pass on this branch, and the permanent
known negative `--target f52462c7` still fails checks 6, 9 and 10 without a browser. The render
checks 7 and 8 are **unverified locally**: no headless browser is on `PATH`, and the installed
Chrome, exposed through an exec wrapper, hung until the gate's own 90-second timeout — the same
macOS behavior Phase 7.7 recorded. The remote `Mockup Appearance Gate` is their authority. This PR
changes neither `documentation/mockups/pass2d.html` nor `AppColors.kt`.

Final PNG manifests (bytes on disk and the committed tree): all PNGs 528 entries
`06a1f2adb9b38b70463d5b9b46db881059c594cf879d98f13acfb2e78cfb4d39`; Paparazzi 456 across 13 owners
`616a0ea5dcc85256a984bb710eb08a8b1110f1f41b1ae3a6e488909db8f65446`, path list
`4782c53805144b129de6ccf933edadcdddc084f46c16fcc3d33de70d6037e733`; archive blob set
`19dd79f0964d2b19d301370527b56a37eda937055bf6044f7d014f7a9ed0684b`. No golden was recorded or
rewritten.


### 19.7 Mandatory known-negative controls

Every control used fresh GREEN → one named observable RED → automatic exact restoration → fresh
GREEN; no mutation was committed. Gradle-gated controls 18–20 used
`documentation/mockups/mutation_harness.py`; the script-, path- and PNG-gated controls and the
Gradle-gated controls 15, 16, 21–23 and 25 used an equally safe scratch harness that snapshots
every touched path, restores bytes (or absence) in `finally` — also on SIGTERM — and proves both
byte equality and an unchanged `git status --porcelain` before the post-GREEN. A RED counted only
when the gate's output contained the named failure text. Gradle GREENs are forced runs; the two
device controls used `--max-workers=2`, as `ui_tests.yml` does (see 19.9).

| # | Mutation | Observable RED (quoted) | GREEN before/after |
| ---: | --- | --- | --- |
| 1 | delete `ui/components/PagingTailKind.kt` | topology: `source manifest mismatch; missing=['src/commonMain/…/ui/components/PagingTailKind.kt'], extra=[]` | topology GREEN |
| 2 | copy `di/ArchiveScope.kt` into `src/main/kotlin/…` | `legacy source set src/main still contains ['src/main/kotlin/…/di/ArchiveScope.kt']` | topology GREEN |
| 3 | move `PagingTails.kt` from `ui/components/` to `ui/` | `missing=[…/ui/components/PagingTails.kt], extra=[…/ui/PagingTails.kt]` | topology GREEN |
| 4 | add `import android.util.Log` to `PagingTailKind.kt` | `…/ui/components/PagingTailKind.kt imports a platform API in commonMain: 'import android.'` | topology GREEN |
| 5 | rename EN key `feature_archive_snackbar_undo` (count kept) | `exact private CMP catalog mismatch in …/values/strings.xml; mismatched=["feature_archive_snackbar_undo: expected='Undo', actual=None"], unexpected=['feature_archive_snackbar_undo_renamed'], order=drifted` | topology GREEN |
| 6 | change the RU value of `feature_archive_paging_retry` | `exact private CMP catalog mismatch in …/values-ru/strings.xml; mismatched=["feature_archive_paging_retry: …"], unexpected=[], order=exact` | topology GREEN |
| 7 | move `feature_archive_snackbar_undo` from archive EN to `feature/settings` EN | `resource ownership drift for feature_archive_snackbar_undo; expected=[both archive catalogs], actual=[archive values-ru, 'feature/settings/src/main/res/values/strings.xml']` | topology GREEN |
| 8 | add `val titleRes: Int = 0` to `State` | `Store State must stay semantic with exactly (…); actual=(…, ('titleRes', 'Int = 0'))` | topology GREEN |
| 9 | `exerciseKind = resourceWrapper.getString(0)` in `ArchivePagingHandler` | `…/ArchivePagingHandler.kt resolves archive copy through ResourceWrapper.getString` | topology GREEN |
| 10 | compile-valid suspend `getString(…)` inside `ArchiveClickHandler`'s `updateStateImmediate` | `updateStateImmediate in …/ArchiveClickHandler.kt resolves a resource inside the State lambda: 'getString('` | topology GREEN |
| 11 | restore `uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES` on the Dark screen preview | `…/ArchiveScreen.kt contains forbidden platform/lookup API 'android.content'` and `… 'uiMode'` | topology GREEN |
| 12 | `LocalContext.current` + `context.appDeps<ArchiveGraph.Factory>()` in `ArchiveFeature` | `… forbidden platform/lookup API 'LocalContext'`, `… 'appDeps<'`, and `remaining Context.appDeps readers are not the exact 10 unported entries` | topology GREEN |
| 13 | delete `archiveGraphFactory` from `AppRootDeps` | `exact root-factory flow requires 'val archiveGraphFactory: ArchiveGraph.Factory' once in …/AppRootDeps.kt` | topology GREEN |
| 14 | create the graph before `rememberMetroStoreProcessor` | `factory invocation must occur exactly inside retained Store creation` | topology GREEN |
| 15 | resolve `appRootDeps()` twice inside the admitted `remember` | device: `an admitted region resolves the generation's app-scope deps expected:<1> but was:<2>` | `UiAdmissionRaceTest` 735/735, all 3 cases |
| 16 | hoist the `remember { appRootDeps() }` above `if (admission.granted)` | `a retired generation must resolve NOTHING — not even the app root deps expected:<0> but was:<1>`; `the stale generation's region must resolve NOTHING expected:<0> but was:<1>` (the admitted case passed) | 735/735 |
| 17 | identity helper via `asContribution<ArchiveGraph.Factory>()` | `extension identities must not bypass AppRootDeps via asContribution` | topology GREEN |
| 18 | `listOf(archivedPhrase, kind)` in `ArchiveUiMapper` | the five order-observing MetaLine identities, including `the kind is first, ahead of the date`, FAILED on Android host and on Native (11 failing cases with the scene) | host + Native 248/248 |
| 19 | refresh `Loading` → `EMPTY` in `archiveListSurface` | `an unsettled refresh with no rows is loading, not empty` FAILED on host and Native (and the scene) | 248/248 |
| 20 | append `Error` → `NONE` in `pagingTailKind` | `a failed page draws the error footer, not silence` FAILED on host and Native (and the scene) | 248/248 |
| 21 | blank the scene's `setContent` body | `Failed to perform isDisplayed check. Reason: Expected exactly '1' node but could not find any node that satisfies: (TestTag = 'ArchiveScreen')` | Native 104/104 |
| 22 | Back button `onClick = { }` in `ArchiveScreen` | `Expected <Back>, actual <OnSegmentChange(segment=TRAININGS)>.` | Native 104/104 |
| 23 | rename the scene method to `renamedSceneSubstitute` | Gradle green with 26 passing cases, then the oracle: `feature:archive: expected exactly one testcase with classname='io.github.stslex.workeeper.feature.archive.ArchiveFeatureSceneIosTest' name='resourcesPagingBranchesAndActionsRenderAndDispatch[iosSimulatorArm64]', found 0; parsed 26 testcase(s): [….renamedSceneSubstitute[iosSimulatorArm64], …]` | Native 104/104 + oracle GREEN |
| 24 | flip the last byte of `…rowExercise_light.png` | `archive blob drift: …rowExercise_light.png expected=33d6d2593752a28d90f89c08582eb22318b8531b actual=9b8490f868a2dd0967359200cfcd9c7beca3ced1` and the all-PNG hash drift | PNG assertion GREEN |
| 25 | compile-valid overlong assertion in `PagingTailKindTest.kt` | root Detekt: `PagingTailKindTest.kt:15:…` `[ArgumentListWrapping]` and `[MaxLineLength]` | root `detekt` 63/63 |

### 19.8 Deviations, spec gaps, and implementation decisions

- **G1 — commit-1 topology delta (precedent #273, `1525a041f`).** The live topology gate pins the
  exact reader set, so commit 1 alone would have been red. Commit 1 therefore carries only the
  minimal script delta: archive's exact 49-path `MODULES` manifest, the archive entry removed
  from `EXPECTED_APP_DEPS_READERS`, and both "11" texts changed to "10". Every archive-specific
  contract check is in commit 2.
- **G2 — review fixes** land as separate signed commits inside Section 9; nothing is amended,
  squashed, or reordered. No review fix exists at the time of this commit; any later fix is
  listed on the PR.
- **P1 — positional placeholders (maintainer decision, 2026-10-01).** Compose Multiplatform
  resources 1.11.1 substitute only `%N$d` and `%N$s` (`Regex("%(\d+)\$[ds]")` in `replaceWithArgs`)
  and render any other placeholder literally, so the byte-exact move of
  `feature_archive_session_count`, whose six EN/RU items used a bare `%d`, would have shown `%d
  sessions` in the permanent-delete dialog. The rendered copy is the contract, not the bytes: commit
  `206583ff0` changes exactly those six items to `%1$d` and no other catalog byte, and
  `PermanentDeleteDialog` resolves the plural as
  `pluralStringResource(Res.plurals.feature_archive_session_count, impactCount, impactCount)`, the
  pre-migration call shape. The topology gate walks every
  `*/src/commonMain/composeResources/values*/strings.xml` and, as tightened by P1a, accepts only
  `%N$d` or `%N$s` with N ≥ 1, naming the file, key and token of anything else. For those six items
  only, P1 supersedes the values in Section 3.4's plural table and the placeholder clauses of
  Sections 5.1 and 16; Sections 0–18 stay the historical specification, and the gate's archive
  table, which cites Section 3.4, carries the amended values. The classic catalogs of all nine
  remaining navigation entries (Section 17) use a bare `%d` in both locales, so P1 is the template
  for those migrations; `feature:recovery`'s catalog has no placeholder and
  `feature:app-dialogs:impl`'s uses only `%1$s`. Past-session's
  `feature_past_session_tonnage_format` uses `%,d`, whose locale digit grouping has no positional
  Compose equivalent, so that migration needs the number pre-formatted, not just a positional
  rewrite. Decision P1a (2026-10-01) corrected the rule's first form, which also accepted `%%`: the
  gate now accepts only `%[1-9]\d*\$[ds]`, because Compose resources 1.11.1 render `%%` literally
  rather than as an escape, and `%0$d` matches their substitution regex but reads `args[-1]` at
  render time. No catalog in the repository, Android or Compose, contains either. Rejected
  alternative, the first resolution in commit `36703afa1`: keep the catalog byte-identical and let
  the dialog fill the bare `%d` itself (`pluralStringResource(...).replace("%d", …)`). It matched
  the visible copy, but it hid a formatter mismatch inside one call site and would have to be
  repeated wherever a migrated catalog keeps a bare placeholder.
- **G3 — Native exactness (Amendment 1, 2026-10-01).** The archive Native oracle requires each of
  the 26 exact tuples exactly once with zero failure, error or skip and, like every module in
  `assert_kmp_ios_smoke.py`, allows additional passing cases. Control 23 is therefore RED by the
  missing exact tuple (`found 0`), and the oracle's parsed-case listing names the substitute
  (`renamedSceneSubstitute`). The topology gate's source-level test-name inventory independently
  pins the 26 names.
- **G4 — timeout contingency (Amendment 1, 2026-10-01): did not trigger.** No job timed out on
  head `1bafa081d` (run `36892368502`): `KMP iOS kit smoke` finished in 10m14s, `Build and Unit
  Tests` in 22m13s and `Release bundle identity` in 11m34s, each against a 60-minute limit. No
  timeout value or other CI setting changed.
- **`commonTest` omits `libs.coroutine.test`.** Section 10 lists it in its sketch but also requires
  verifying rather than copying; no portable archive test imports `kotlinx-coroutines-test`. The
  ported click-handler fake reproduces the old relaxed MockK store exactly — Store-scope `launch`
  work is not run — so each identity proves the same transition and haptic as before.
- **Mapper seam.** `ArchiveMetaCopy` (kinds, bare word, separator) lives in `ArchiveUiMapper.kt`,
  since Section 9.1 allows no new production file. `ArchivePagingHandler` resolves it once per
  `PagingData` emission and each item's `archived since <date>` per item, both inside suspending
  Paging work; the mapper alone chooses kind by payload, orders kind before date before tags, and
  falls back to the bare word for a missing timestamp. The handler now formats a date even for a
  missing timestamp and discards it, which is not visible.
- **Snackbar copy.** JVM `String.format` is unavailable in common code, so the event renderer, a
  suspend block, formats the restored/deleted messages with Compose resources' suspend
  `getString(resource, name)`; the undo label still resolves in composition. The app has no
  per-app locale, so the composition and system resource environments agree.
- **Previews.** Both previews keep `showBackground = true`; only the Android `uiMode` parameter is
  replaced by explicit `ThemeMode.LIGHT`/`ThemeMode.DARK` through `AppTheme`.
- **Specification prose the measured tree contradicts** (recorded by the first implementation
  attempt and re-verified at `b1945ee1`; none changes a gate or an identity):
  - Section 2 calls archive smallest in unit files, but it ties single-training at 6.
  - Section 2 calls archive smallest in resource surface, but past-session has fewer EN string
    keys (20 against 25).
  - Section 3.5 gives `kotlinx.collections.immutable` as being for preview fixtures; production
    `ArchiveScreen` uses `persistentListOf` in its body for the segmented control, and no public
    signature exposes the type, so the edge stays `implementation`.
  - Section 3.3 reads as if `@Smoke` sat on `pendingFeatureRewrite`; it is on the
    `ArchiveScreenTest` class, and only `@Ignore` is on the method.
  - The `@Ignore` reason literal ends with a period: `Awaiting feature rewrite — see GH issue #93
    for coverage scope.`
  - The RU catalog's comment still says the file has no `paging_error`, although it does; Section
    5.1 required a byte-semantic move, so the stale comment moved with it.
- **Native scene settling.** Paging hands each new list over on the main dispatcher and the kit
  holds a shown loading treatment for at least 260 ms, so one fixed clock advance per paging
  branch was not enough. The scene settles each paging branch in at most ten one-second steps until
  that branch's production tag exists, and checks that the cold-open loading verdict is held across
  three further steps rather than being the transient first frame of a new list.

### 19.9 Rejected runs, local limitations, and the remote evidence boundary

Runs that did not measure what they appear to were excluded and their owning proof rerun freshly:

- One control batch invoked the mutation harness through an unsplit zsh variable, so no harness
  ran and nothing was mutated; it was stopped and relaunched. Its interrupted GREEN was discarded.
- The first device pre-GREEN for controls 15/16 lost the device to ddmlib
  `ShellCommandUnresponsiveException` ("No compatible devices connected"). Stopping that batch
  with SIGTERM bypassed the repository harness's `finally`, leaving the control-15 line in
  `App.kt`; it was reversed by hand and proved byte-identical to `HEAD` before anything else ran.
  The scratch harness now converts SIGTERM into a restoring interrupt.
- A second pre-GREEN failed with `Instrumentation run failed due to keyDispatchingTimedOut` while
  the emulator guest was memory-starved after long uptime; the AVD was cold-booted (same serial,
  API 34, animation and rotation settings verified unchanged). One mutation run then lost the
  device to a ddmlib property-fetch timeout ("Unknown API Level"); the harness refused to score
  it. The accepted device controls ran with `--max-workers=2`, as `ui_tests.yml` does.
- The first Smoke attempt lost `feature:all-trainings` to `Instrumentation run failed due to
  failed to attach`, and its XML was lost to a copy step that split file names containing spaces;
  in that rejected run archive's line also read `Starting 0 tests`. The accepted Smoke run in
  Section 19.6 is a fresh full rerun, in which archive's one case was discovered and skipped
  exactly.

Local limitations:

- The mockup gate's browser checks 7 and 8 are unverified locally (Section 19.6); the remote
  `Mockup Appearance Gate` is their authority.
- The positional session count (P1) is proven rendered on Native by the scene. On Android the same
  common code runs, but no host or device test renders the dialog: Paparazzi models one window,
  the device placeholder is ignored by contract, and no app journey opens the delete dialog. The
  Compose-resources formatter is shared common code, so the Native proof covers its behavior.
- Wear device tests are not part of the phone `connectedDebugAndroidTest` suites (flavored tasks,
  no alias), matching `ui_tests.yml`.
- This documentation commit must exist before its SHA, the PR, GitHub signature verification of
  the pushed commits, CI run/job IDs, and review threads can exist. Those facts, and every review
  finding's classification, are recorded in the live PR, not predeclared here.

### 19.10 Remote evidence and Follow-up 1

Head `1bafa081d` (the first three commits) was green on every context. Unified run `36892368502`:
`Build and Unit Tests` job `110470872809`, `KMP iOS kit smoke` job `110470873028`, `Release bundle
identity` job `110470872462`; `Unit Test Results` check `110479076418` (2,891 tests passed) and
`Detailed Unit Test Report` check `110479275680`. Mockup run `36892368466`, `Mockup Appearance Gate`
job `110470871953`. A Codex review requested on that head found no major issues, and the PR had no
review threads.

Commit `206583ff0` implements decision P1. Its gates ran on exactly its tree, each after
`./gradlew --stop` with `--rerun-tasks --no-build-cache --no-configuration-cache --console=plain`:

| Command | Executed | Parsed result |
| --- | ---: | --- |
| `python3 .github/scripts/assert_kmp_ui_source_topology.py` | GREEN | 12 commonMain catalogs walked, 36 placeholders, all `%N$d`, `%N$s` or `%%`; archive's exact table carries the six `%1$d` values |
| `:feature:archive:assembleDebug` | 214/214 | Android and iOS klib targets |
| `:feature:archive:testAndroidHostTest` | 175/175 | 25 portable cases, 0/0/0 |
| eight-module Native command (`--continue`) + `assert_kmp_ios_smoke.py` | 235/235 | oracle GREEN for all eight modules; archive 26 exact tuples; the scene still asserts the rendered `3 sessions` and `1 session` |
| `:feature:archive:verifyPaparazziDebug` | 176/176 | 14 golden cases for 14 images; host XML 39 cases (25 portable + 14 goldens), 0/0/0 |
| `detekt --continue` | 63/63 | GREEN |

The device suites cannot reach this change: the plural is read only by `PermanentDeleteDialog`,
`ArchiveScreenTest.pendingFeatureRewrite` is the ignored placeholder, and no device test or app
journey opens archive's delete dialog. They were not rerun.

Commit `14264931d` changes only the comment above the gate's archive table so it also cites
decision P1; on its tree the topology gate is GREEN under Homebrew Python 3.14 and macOS Python 3.9,
and `assert_kmp_ios_smoke.py` stays GREEN on the Native XML above.

The new check's known negative used the same byte-restoring harness: fresh GREEN → one bare `%d`
reintroduced in the archive EN catalog → RED with
`compose-resource placeholder is not %N$d, %N$s or %%:
feature/archive/src/commonMain/composeResources/values/strings.xml key
feature_archive_session_count[one] token '%d'` → exact restoration and an unchanged `git status`
→ fresh GREEN.

### 19.11 Follow-up 2: the tightened placeholder rule (P1a)

Head `fbf6dc06e` was green on every context. Unified run `36905386586`: `Build and Unit Tests`
job `110514516322`, `KMP iOS kit smoke` job `110514516582`, `Release bundle identity` job
`110514516164`; `Unit Test Results` check `110520683150` and `Detailed Unit Test Report` check
`110520835657`. Mockup run `36905386623`, `Mockup Appearance Gate` job `110514515618`.

Commit `040811182` implements decision P1a in the placeholder gate alone: `POSITIONAL_PLACEHOLDER`
becomes `%[1-9]\d*\$[ds]`, the tokenizer reads `%%` as one token so it is reported whole, a lone
`%` stays rejected. The failure text now reads "compose-resource placeholder is not %N$d or %N$s
with N >= 1", and the docstring and success summary state the same rule positively ("every
placeholder is %N$d or %N$s with N >= 1"). Its gates, each fresh:

| Command | Executed | Parsed result |
| --- | ---: | --- |
| `python3 .github/scripts/assert_kmp_ui_source_topology.py` | GREEN | 12 commonMain catalogs and 36 placeholders walked, every one `%N$d` or `%N$s` with N ≥ 1 (also GREEN under macOS Python 3.9) |
| `./gradlew --stop`, then `detekt --continue` with the spec flags | 63/63 | GREEN |

Nothing else is reachable from this change: it touches one Python CI script that no Gradle task,
Kotlin source or test consumes, and whose only runner is the topology step of `Build and Unit
Tests`.

Both new known negatives used the byte-restoring harness, each fresh GREEN → named RED → exact
restoration with an unchanged `git status` → fresh GREEN:

| Control | Mutation | Observable RED (quoted) |
| --- | --- | --- |
| P1a-1 | `Retry` → `Retry %%` in the archive EN `feature_archive_paging_retry` | `compose-resource placeholder is not %N$d or %N$s with N >= 1: feature/archive/src/commonMain/composeResources/values/strings.xml key feature_archive_paging_retry token '%%'` |
| P1a-2 | `Exercises (%1$d)` → `Exercises (%0$d)` in the archive EN `feature_archive_segment_exercises` | `compose-resource placeholder is not %N$d or %N$s with N >= 1: feature/archive/src/commonMain/composeResources/values/strings.xml key feature_archive_segment_exercises token '%0$d'` |
