# KMP Phase 7.12 — feature:home becomes a shared feature entry

**Status:** SPECIFIED — authorized for implementation by the maintainer's GO of 2026-10-17 (Section 18)

**Discovery baseline:** dev @ 894f2ec6be8cdd17dbb075b13bb9b9c3ff6bc2be (2026-10-17, merge of #337,
Phase 7.11). Its parents are `fe57277c0` (#335) and `8b1e5825e` (the 7.11 PR head). `feature/home`
is unchanged since Phase 7.11's discovery baseline `5beefec84`; no commit on dev follows #337.

**Template:** [Phase 7.11](kmp-phase-7-11-past-session-feature.md). This document is a delta. Every
rule of 7.11 that this document does not change applies unchanged, read with the substitutions of
Section 0.3. Through 7.11, the rules of 7.10, 7.9, 7.8 and 7.7 that it inherits apply too. Home has
no route argument, so [Phase 7.10](kmp-phase-7-10-all-exercises-feature.md) Section 6 is the
precedent for its factory flow (shape A), not 7.11's shape B.

**Measured by:** a static discovery of 2026-10-17 on git data, greps and scripts, with nothing
compiled or run. Its gate change and gate data were prototyped against a simulated exit tree
(Sections 12.0 and 13). That prototype is not evidence; the implementation's fresh runs are.

---

## 0. Authority, decisions, substitutions

### 0.1 Authority order

1. live origin/dev at the implementation base;
2. AGENTS.md, CLAUDE.md, documentation/architecture.md, testing.md, ci-cd.md,
   compose-state-discipline.md;
3. the Phase 7.11 specification, and through it 7.10 (its Section 6 for shape A), 7.9, 7.8
   (including its Section 19 decisions P1, P1a and G1–G4) and 7.7;
4. kmp-string-resolver.md (PR-R) as merged;
5. the checked-in source, resources, tests, PNGs and CI oracles at the implementation base.

### 0.2 Stable rules

7.11 Section 0.2 applies unchanged: the positive-gate flags after `./gradlew --stop`, quoted
`N actionable tasks: N executed`, fresh JUnit XML only, detekt and tests in separate invocations,
signed and GitHub-verified commits with `Co-Authored-By` as the only trailer, no session identifier
in a commit, the PR or a document, and no merge by the implementer.

Suppressions: **no new suppression of any kind.** The three inherited production suppressions stay
byte-semantically unchanged:

- `@Suppress("UNCHECKED_CAST")` on the processor cast in di/HomeFeature.kt;
- `@Suppress("LongParameterList")` on `HomeInteractorImpl`;
- `@Suppress("TooManyFunctions")` on `ClickHandler`.

The module has no test suppression, and Phase 7.12 needs no Native test-name suppression.

One session does all the work. Every census, transcription and audit is a script that session
writes and runs, with its own parser. None is a separate agent.

### 0.3 Substitutions against 7.11

| 7.11 | 7.12 |
| --- | --- |
| `feature:past-session`, `feature/past-session` | `feature:home`, `feature/home` |
| package `io.github.stslex.workeeper.feature.past_session` | `io.github.stslex.workeeper.feature.home` |
| resource package `…feature.past_session.resources` | `io.github.stslex.workeeper.feature.home.resources` |
| `PastSessionFeature`, `PastSessionGraph`, `pastSessionGraph` | `HomeFeature`, `HomeGraph`, `homeGraph` |
| `pastSessionGraphFactory`, `createPastSessionGraph(screen)`, `pastSessionStore` | `homeGraphFactory`, `createHomeGraph()`, `homeStore` |
| `PastSessionStoreImpl`, `PastSessionExtensionIdentityTest` | `HomeStoreImpl`, `HomeExtensionIdentityTest` |
| route `Screen.PastSession(sessionUuid)`, shape B (`FeatureAssisted`, `create_args = "screen"`) | route `Screen.BottomBar.Home`, shape A (`Feature`, `create_args` left at its default `""`), 7.10 Section 6 |
| graph tag `PastSessionGraph` | `HomeGraph` |
| `PAST_SESSION_ENTRY` | `HOME_ENTRY` |
| eleven-module Native command | twelve modules (Section 12.1) |
| 7.11's D2: the additive gate generalization (commit 1) | Section 0.4's D3: the resolver-reference check (commit 1) |
| 7.11's S2: the date on `ResourceWrapper.formatMediumDate`, one call | Section 0.4's S1: the relative time on `ResourceWrapper.getAbbreviatedRelativeTime`, two calls |
| 7.11's S3: the overflow gate on androidHostTest | Section 0.4's S2: the mode-label test on iosTest |

### 0.4 Decisions

Locked by the maintainer's GO (2026-10-17):

| Id | Decision |
| --- | --- |
| D1 | `feature:home` is the next shared entry: the smallest remaining candidate, the only one with no Android or JVM import, and every dependency already KMP (Section 2). |
| D2 | The two use cases get an interface seam on the interactor pattern (option A). `ObserveStartCardReadoutUseCase` and `ObserveWeekReadoutUseCase` become public interfaces, implemented by public `…Impl internal constructor` classes that `HomeGraph` binds. No identity changes; MockK stubs and verifications become fake-recorded values and assertions (Section 5.5). |
| D3 | The topology gate gains a separate check (option A): inside a handler's `updateState { … }` or `updateStateImmediate { … }` body, a `stringResolver` or `resourceWrapper` reference that is not a direct member call (`name.`) is RED. It is separate from `FEATURE_STATE_LAMBDA_SIDE_EFFECTS`, so every failure that list reports stays byte-identical. It is this PR's **first** commit, proven not to change any existing behaviour (Section 12.0). |

Specification decisions, delivered with this document. The maintainer accepts them by starting the
implementation (Section 18):

| Id | Decision |
| --- | --- |
| S1 | The recent rows' and the picker rows' relative time stays on `ResourceWrapper.getAbbreviatedRelativeTime`, as archive kept `formatDayMonth` and past-session `formatMediumDate`. The gate allows exactly those two calls, in the mapper, through `resource_wrapper_dates` (Section 5.3). |
| S2 | `HomeStartCardModeLabelTest` moves to its own iosTest file with kotlin.test. A shared feature's build may not take Robolectric (testing.md, `FEATURE_FORBIDDEN_BUILD_TOKENS`), and the test already drives `androidx.compose.ui.test.v2.runComposeUiTest`. The identity keeps its name and assertions: 70 portable + 1 iOS identity + the scene = 72 Native tuples (Section 11.3). This corrects the discovery note's androidHostTest placement. |
| S3 | The 19 previews: the 17 that are already portable stay as written, pinned by `preview_inventory` (the 11 with an argument list) and by a home contract (the 6 bare `@Preview`, Section 5.4). The RecentSessionRow `uiMode` pair becomes the Light/Dark ThemeMode subject. `@Preview(device = "id:pixel_9")` stays; if commonMain rejects it, STOP. |
| S4 | Five signed commits: the D3 check; D2 and the F1 hoists in `src/main`, before any move; the module move; the gate entry, oracle and workflow; the docs (Section 15). Behaviour-relevant edits stay out of the move commit. |
| S5 | The docs commit records 7.11's three follow-ups in tech-debt.md (runtime RU catalog coverage, a typed `rememberMetroStoreProcessor`, a portable date formatter now also covering home's relative time) and the D3 follow-up (a non-suspend `updateStateImmediate` lambda), marks the 🟡 State Mutation row for home resolved, and re-points the home links of rows 24–26 and 463 (Section 9.2). |
| S6 | The three suites that name their identities with JUnit 5 `@DisplayName` (12 identities) keep the **function name** as the identity on both targets: the display sentence moves verbatim into a one-line KDoc directly above `@Test`, so `@Test` directly precedes `fun` (Section 4.3). |

Inherited and unchanged:

- P1: a bare `%d` in a migrated catalog becomes positional;
- P1a: the placeholder rule;
- D4 of 7.10: `.claude/skills/` stays untouched.

**Required, not a decision — F1.** Two copy-bearing mappings run inside `updateStateImmediate`
today. Both move before their lambdas (Section 5.2). This resolves tech-debt.md's 🟡 State Mutation
row for home, and D3 makes the gate fail the shape from now on.

## 1. Bounded exit claim

The implementation may prove only this:

- feature:home applies `convention.kmpComposeLibrary` and retains Metro and Paparazzi;
- all 41 production Kotlin files (the 39 of Section 3.1 plus D2's two implementation files) compile
  from commonMain for Android, iosSimulatorArm64 and iosArm64;
- the exact private 40-identifier EN/RU catalog lives in commonMain Compose Resources, changed only
  by P1's 18 positional placeholders (Section 3.4);
- every visible Android behaviour is unchanged, except the inherited Compose-resources locale
  property (Section 5.7) and F1's earlier `now` read (Section 5.2);
- copy outside composition resolves through the injected `StringResolver`, never through CMP's
  top-level `getString` / `getPluralString`, and `ResourceWrapper` keeps exactly two reads: the
  relative time, in the mapper (Section 5.3);
- no State lambda of a handler resolves copy, and the gate fails a `stringResolver` or
  `resourceWrapper` reference inside one, in every shared entry (D3, F1);
- 12 portable suites with 70 exact identities execute on Android host and on Native; the one
  mode-label identity executes on Native;
- the golden suite executes as 42 Android-host Paparazzi cases (21 methods × light/dark) against
  the same PNG blobs;
- one deterministic iOS production scene proves the whole catalog through the real
  `ComposeStringResolver`, the readout, row, picker and banner copy through the production mapper,
  the screen's loading, banner, start-card and list states, the empty region, the training picker
  and action dispatch;
- the Metro graph is reached through the explicit generation-owned factory (shape A), never
  `LocalContext` or `Context.appDeps`; both extension identities use the accessor;
- the topology gate checks the entry through one `FeatureEntry` after D3's additive check;
  `Context.appDeps` readers drop from 7 to 6;
- `core:data:database` is no edge of the module;
- Android remains releasable with every repository PNG blob unchanged.

This is a source-set, dependency, resource, test and composition-root migration, plus D2's seam and
F1's two hoists. It does not permit:

- redesigning the screen, the start card, its modes or the picker;
- changing start-card, picker, conflict, paging or active-session behaviour;
- adding a device test;
- changing `core:ui:kit`, `core:ui:start-mode`, `core:core`, `core:data:exercise` or
  `core:data:dataStore`;
- adding an iOS app;
- migrating another feature.

## 2. Candidate selection — fresh census

Measured at the discovery baseline with 7.9's method (production = `src/main/kotlin`, unit =
`src/test/kotlin`, EN string keys = `<string` elements of `values/strings.xml`, PNGs = files under
`src/test/snapshots`). Every row equals 7.11's table: no candidate changed since `5beefec84`.

| Feature | Prod files | Prod lines | Unit files | Unit lines | EN string keys | Plurals | PNGs |
| --- | ---: | ---: | ---: | ---: | ---: | ---: | ---: |
| **home** | 39 | **3,085** | 15 | 1,924 | 35 | 5 | 42 |
| exercise-chart | 41 | 3,214 | 10 | 2,314 | 31 | 2 | 30 |
| settings | 51 | 3,326 | 12 | 2,638 | 65 | 3 | 12 |
| single-training | 39 | 3,635 | 6 | 1,582 | 39 | 1 | 12 |
| exercise | 46 | 4,141 | 13 | 2,890 | 53 | 1 | 48 |
| live-workout | 59 | 6,755 | 27 | 7,903 | 96 | 2 | 60 |

Portability of production code (import lines, summed over files):

| Feature | `android.*` | `java.*` | Blocking dependency |
| --- | ---: | ---: | --- |
| **home** | 0 | 0 | none; every dependency is KMP |
| exercise-chart | 0 | 9 (`java.time`) | tests use the Android-only `core:data:database-test` |
| settings | 11 | 4 | `feature:app-dialogs:api` (Android-only); ActivityResult, IntentSender, DateUtils |
| single-training | 0 | 0 | activity `BackHandler`; four `toUi(` calls inside State lambdas |
| exercise | 10 | 1 | photo picker, permission, Settings intent; tests use `database-test` |
| live-workout | 1 | 0 | largest; tests use `database-test`; five State lambdas reference the wrapper or a mapper |

Every module home depends on is already KMP: core:core, core:ui:kit, core:ui:mvi,
core:ui:navigation, core:ui:start-mode, core:data:exercise, core:data:dataStore, and
kotlinx-datetime. Every symbol its production code imports from them is declared in commonMain,
with two exceptions:

- `DefaultDispatcher` is a commonMain `expect` with platform actuals;
- `core.ui.mvi.di.appDeps` lives in androidMain and leaves with the factory flow (Section 6).

Home is the first shared consumer of core:ui:start-mode and core:data:dataStore, and the first
shared entry with use cases (D2). The golden harness (`core.ui.kit.golden.*`) is imported only by
the golden test, which stays on the Android host (Section 11.2).

`feature:recovery` stays Android-only (kmp-migration-assessment.md), and `feature:wear-bridge` is
phone↔watch only; neither is a candidate.

## 3. Measured baseline

Static measurements at the discovery baseline. Test names are given as the base leaves them.

### 3.1 Production manifest

All 39 files move from `src/main/kotlin/io/github/stslex/workeeper/feature/home/` to
`src/commonMain/kotlin/io/github/stslex/workeeper/feature/home/` with unchanged relative paths. D2
adds two files beside them (Section 5.5).

| File | Lines |
| --- | ---: |
| di/HomeFeature.kt | 34 |
| di/HomeGraph.kt | 34 |
| di/HomeHandlerStore.kt | 9 |
| di/HomeHandlerStoreImpl.kt | 14 |
| di/HomeScope.kt | 8 |
| domain/HomeInteractor.kt | 43 |
| domain/HomeInteractorImpl.kt | 84 |
| domain/mapper/HomeDomainMapper.kt | 68 |
| domain/model/ActiveSessionDomain.kt | 8 |
| domain/model/ActiveSessionWithStatsDomain.kt | 12 |
| domain/model/RecentSessionDomain.kt | 13 |
| domain/model/StartCardModeDomain.kt | 21 |
| domain/model/StartCardReadoutDomain.kt | 42 |
| domain/model/StartSessionConflict.kt | 15 |
| domain/model/TrainingListItemDomain.kt | 9 |
| domain/model/WeekReadoutDomain.kt | 13 |
| domain/usecase/ObserveStartCardReadoutUseCase.kt | 120 |
| domain/usecase/ObserveWeekReadoutUseCase.kt | 44 |
| mvi/handler/ClickHandler.kt | 218 |
| mvi/handler/CommonHandler.kt | 117 |
| mvi/handler/NavigationHandler.kt | 52 |
| mvi/handler/PagingHandler.kt | 42 |
| mvi/mapper/HomeUiMapper.kt | 192 |
| mvi/mapper/StartCardModeMapper.kt | 26 |
| mvi/model/PickerTrainingItem.kt | 16 |
| mvi/model/RecentSessionItem.kt | 18 |
| mvi/model/StartCardBodyUi.kt | 66 |
| mvi/store/BottomSheetState.kt | 28 |
| mvi/store/HomeStore.kt | 136 |
| mvi/store/HomeStoreImpl.kt | 56 |
| ui/HomeGraph.kt | 67 |
| ui/HomeScreen.kt | 342 |
| ui/components/ActiveSessionBanner.kt | 164 |
| ui/components/HomeListSurface.kt | 34 |
| ui/components/HomeStartCard.kt | 540 |
| ui/components/PagingTailKind.kt | 17 |
| ui/components/PagingTails.kt | 42 |
| ui/components/RecentSessionRow.kt | 103 |
| ui/components/TrainingPickerSheet.kt | 218 |
| **39 files** | **3,085** |

The three inherited production suppressions are listed in Section 0.2 (lines 24, 28 and 26 of
HomeFeature.kt, HomeInteractorImpl.kt and ClickHandler.kt).

Android coupling in production:

- `R.*` in seven files:
  - five UI files read 20 identifiers at 21 sites through Android `stringResource`:
    `ui/HomeScreen.kt` (6), and in `ui/components/` `ActiveSessionBanner.kt` (3 sites, 2
    identifiers), `HomeStartCard.kt` (4), `PagingTails.kt` (3) and `TrainingPickerSheet.kt` (5);
  - `mvi/mapper/HomeUiMapper.kt` reads 19 identifiers through `ResourceWrapper`: 12 `getString`
    calls (the seven weekday labels through one call over `WEEK_DAY_LABELS`) and 7
    `getQuantityString` calls;
  - `mvi/handler/ClickHandler.kt` reads 2 through `ResourceWrapper.getString` (the conflict copy);
  - `feature_home_picker_empty` is read both in composition and by the mapper, so the catalog's 40
    identifiers are 20 composition reads and 21 outside reads, with one in both;
- `ResourceWrapper` itself in four files:
  - the mapper, with the 19 copy calls above and 2 `getAbbreviatedRelativeTime`;
  - `ClickHandler`, with its 2 copy calls, and passing the wrapper to `toPickerItems`;
  - `CommonHandler`, which only passes it to two mapper calls;
  - `PagingHandler`, which only passes it to `toRecentItem`;
- `androidx.compose.ui.res.stringResource` imported in the five UI files;
- `LocalContext` with `appDeps` in di/HomeFeature.kt;
- `@VisibleForTesting` on `HomeStoreImpl.NAME`;
- `System.currentTimeMillis()` five times: ClickHandler 1, CommonHandler 3, PagingHandler 1;
- one `uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES` preview pair in
  `RecentSessionRow.kt`.

Already portable:

- 17 of the 19 previews: `androidx.compose.ui.tooling.preview.Preview` with an explicit
  `AppTheme(themeMode = ThemeMode.…)` (Section 5.4);
- `Icons.Filled.Settings`, and `Icons.Outlined.QueryStats`, `Icons.Filled.FitnessCenter` and
  `Icons.Default.AddCircleOutline` through `libs.cmp.material.icons.extended` (archive,
  image-viewer and past-session draw from it in commonMain);
- `LazyPagingItems` and the kit's `collectAsItems` (all-trainings' paging-compose precedent);
- `DateTimeUtil.formatMillis`, `formatElapsedDuration`, `calendarDaysBetween`, `weekWindowOf` and
  `weekdayIndexOf` (core:core commonMain, kotlinx-datetime and `kotlin.time`).

None of these occurs: `BackHandler`, `.format(`, `java.*`, `CompositionLocal`, `Locale`,
`synchronized`, `@Volatile`, `Atomic*`, `Thread`, `TimeUnit`, `@Jvm*`, and the JVM-only collection
members `putIfAbsent`, `getOrDefault`, `computeIfAbsent`, `removeIf`, `toSortedSet` and
`toSortedMap` (the scan 7.11 Section 19.8 asked for).

### 3.2 Portable manifest — 12 suites, 70 identities

Each file moves from `src/test/kotlin/…/feature/home/` to `src/commonTest/kotlin/…/feature/home/`
with its relative path. The helper `mvi/store/HomeTestState.kt` (`emptyPagingState()`, used by the
handler suites) moves with them unchanged.

**domain/HomeInteractorImplTest.kt — 6**

- observeActiveSession maps repository ActiveSessionWithStats to domain
- observeStartCardReadout delegates to the use case with mode and now
- observeStartCardMode maps the stored string to the domain mode
- observeStartCardMode falls back to WEEK for an unknown stored value
- setStartCardMode forwards the persistence encoding to the data store
- the storage encoding and the WEEK default are pinned

**domain/usecase/ObserveStartCardReadoutUseCaseTest.kt — 9**

- WEEK with history wraps the week readout
- WEEK with no session ever is the mode's own empty state even at count zero
- DAYS_SINCE_LAST measures calendar days and anchors on the last session
- DAYS_SINCE_LAST with no session ever is NoSessions
- LAGGING_GROUPS maps idle stats to calendar days in repository order
- LAGGING_GROUPS with no tagged history is NoTaggedHistory
- FORGOTTEN_TRAINING carries days idle and composition
- FORGOTTEN_TRAINING never run keeps daysIdle null — HD1's most forgotten thing
- FORGOTTEN_TRAINING with no templates is NoTemplates

**domain/usecase/ObserveWeekReadoutUseCaseTest.kt — 2**

- queries the repository with the Monday-first week window around now
- counts every session and collapses same-day sessions into one trained day

**mvi/handler/ClickHandlerTest.kt — 16**

- OnSettingsClick consumes OpenSettings navigation and emits HapticClick
- OnActiveSessionClick is a no-op when no active session
- OnActiveSessionClick consumes OpenLiveWorkoutResume with the session uuid
- OnRecentSessionClick consumes OpenPastSession with the session uuid
- OnPickerSeeAllClick hides picker and consumes OpenAllTrainings
- OnModeLabelClick opens the mode sheet with a haptic
- OnModeLabelClick while the training picker is up replaces the sheet
- OnModeSelected hides the sheet and persists the MAPPED mode through the interactor
- OnModeSheetDismiss hides the sheet without a haptic
- OnPickerTrainingSelected hides the picker before resolving conflict
- OnPickerTrainingSelected reports the persisted active session progress
- OnStartActionClick under a Forgotten body starts THAT training no picker
- OnStartActionClick under an Empty body opens the picker instead
- OnStartActionClick before the body arrives opens the picker
- OnConflictDismiss clears pendingConflict
- OnConflictResume consumes OpenLiveWorkoutResume with the active session uuid

**mvi/handler/CommonHandlerTest.kt — 2**

- Init subscribes to the active session and NOT to the paged recent list
- Init builds the start card pipeline from the persisted mode

**mvi/handler/NavigationHandlerTest.kt — 5**

- OpenLiveWorkoutResume navigates to LiveWorkout with the active session uuid
- OpenLiveWorkoutFresh navigates to LiveWorkout with the training uuid
- OpenPastSession navigates to PastSession with the session uuid
- OpenSettings navigates to Settings
- OpenAllTrainings navigates to AllTrainings bottom-bar destination

**mvi/mapper/HomeUiMapperTest.kt — 14**

- recent mapper uses adhoc label and pluralized stats
- recent mapper preserves template name and mixed count labels
- picker mapper handles recently used and never used templates
- week mapper renders count as digits resolves the unit plural fills trained days
- week mapper with no sessions renders zero over seven unfilled pills
- week mapper uses the singular unit for one session
- days-since readout renders digits unit and the name-date anchor
- days-since readout for an adhoc session anchors on the adhoc label
- tag-idle readout scales bars against the longest idle and keeps bare counts
- tag-idle readout with all groups trained today draws empty bars not NaN
- forgotten readout joins days idle and composition
- forgotten readout for a never-run template says so instead of a day count
- each empty readout carries its own mode's copy
- week readout wrapped in the sealed type maps through the week mapper

**mvi/mapper/StartCardModeMapperTest.kt — 3**

- every UI mode maps to its own domain twin
- every domain mode maps to its own UI twin
- the mapping round-trips over the whole catalog

**mvi/store/HomeStartCardSeedTest.kt — 1**

- init seeds no mode and no body

**ui/components/HomeListSurfaceTest.kt — 5** (function name, then today's `@DisplayName`)

- `rowsWin` — rows win over everything, including an unsettled refresh
- `coldOpenIsLoading` — an unsettled refresh with no rows is loading, not empty — B22
- `coldOpenErrorIsItsOwnVerdict` — a failed first page is its own verdict, not loading and not empty
- `settledAndEmpty` — settled with no rows is the empty state
- `theEnumIsNarrowedNotCopied` — four verdicts, and the two the siblings carry are absent on purpose

**ui/components/HomePagingTailKindTest.kt — 3**

- `appending` — an appending list draws the spinner footer
- `failedAppend` — a failed append draws the error footer — the branch goldens cannot reach
- `exhausted` — an exhausted list draws NOTHING, and the absence is the assertion

**ui/components/RecentMetaLineTest.kt — 4**

- `order` — when · how long · how much, joined by the drawn interpunct
- `relativeLabelIsFirst` — the relative label leads — it is what orders the list being scanned
- `statsAreLast` — stats trail — the tail is what disappears, and counts are recoverable
- `emptyTokensAreDropped` — an empty token is dropped, not left as a dangling separator

Suite distribution: 6/9/2/16/2/5/14/3/1/5/3/4. Every identity is Native-legal: none contains `.`,
`;`, `[`, `]`, `/`, `<`, `>`, `:` or `\`. Three contain an apostrophe (the second and eighth
use-case identities, and `each empty readout carries its own mode's copy`) and one contains U+2014
(the eighth use-case identity); both already run on Native in past-session, plan-editor,
all-trainings and all-exercises.

**ui/components/HomeStartCardModeLabelTest.kt — 1, not portable (S2)**

- `theHeadNamesNoModeUntilOneIsKnown`

It runs `androidx.compose.ui.test.v2.runComposeUiTest` under `RobolectricExtension` today, so it
needs a Compose host. It moves to `src/iosTest/…/ui/components/` (Section 11.3).

Today's test doubles:

- JUnit 5 in all 13 suites; Robolectric in `HomeUiMapperTest` (only for the
  `DateUtils.MINUTE_IN_MILLIS` / `HOUR_IN_MILLIS` constants) and in the mode-label test;
- MockK in six suites:
  - the three handler suites mock `HomeHandlerStore` relaxed. `newStoreWithFlow` answers `state`
    and `updateState` from a `MutableStateFlow`, records `sendEvent`, `consume` and
    `consumeOnMain` for verification, and answers `launch` by running its action under
    `runBlocking` only when `executeLaunch = true` (three ClickHandler identities). One identity
    stubs `updateStateImmediate` to apply; every other member is relaxed;
  - `HomeInteractor` relaxed in the click and common suites (with `observeActiveSession()` stubbed
    to `emptyFlow()` in CommonHandlerTest), and `ResourceWrapper` relaxed in both;
  - `Navigator` relaxed in NavigationHandlerTest;
  - `SessionRepository`, `TrainingRepository`, `SessionConflictResolver`, `CommonDataStore` and
    `ObserveStartCardReadoutUseCase` relaxed in the interactor suite;
  - `SessionRepository`, `TagRepository`, `TrainingRepository` and `ObserveWeekReadoutUseCase`
    strict (`mockk<…>()`) in the two use-case suites;
- `HomeUiMapperTest`'s `ResourceWrapper` is already an in-file object: `R`-keyed strings,
  quantity-aware plurals, and the argument-keyed sentinel `rel:$timestamp@$now` for the relative
  time, which pins the argument order.

Abstract members, counted from each type itself: `SessionRepository` 26, `TrainingRepository` 20,
`TagRepository` 5, `CommonDataStore` 8, `HomeInteractor` 9, `HandlerStore` 12, `Navigator` 6,
`ResourceWrapper` 5, `StringResolver` 2, `Logger` 13 (plus three defaulted members).
`SessionConflictResolver` is a final class with one dependency (`SessionRepository`); no identity
calls it.

Fifteen identities run through copy resolution: the 14 mapper identities, and
`OnPickerTrainingSelected reports the persisted active session progress`, which reads
`conflict_progress_format` with `(1, 2)` (its training name is non-blank, so `conflict_unnamed` is
not read). No handler identity collects the active-session, readout or picker flow, so no handler
identity reaches the mapper, and none reaches F1's hoisted code (Section 11.6).

### 3.3 Golden and device ownership

`golden/HomeGoldenTest.kt` has 21 methods parameterized over `GoldenTheme`, in this order:

    recentRow, recentRowRussian, pagingLoadingTail, pagingErrorTail, coldOpenError,
    activeSessionBanner, startCard, startCardDaysSince, startCardTagIdle, startCardForgotten,
    startCardForgottenNeverRun, startCardEmptyWeek, startCardEmptyDaysSince, startCardEmptyTags,
    startCardEmptyTemplates, screenList, screenWithActiveSession, screenEmptyRegion,
    screenColdOpenLoading, screenColdOpenError, screenActiveSessionLoading

That is 42 render cases and 42 PNGs, all mode 100644, named
`io.github.stslex.workeeper.feature.home.golden_HomeGoldenTest_<method>_<dark|light>.png`.

The PNGs hold 40 distinct blobs. `recentRow` and `recentRowRussian` share their dark
(`1533abee88fe…`) and light (`69a965553e21…`) blobs: the four rows carry fixed strings and read no
catalog string, so the RU golden exercises no RU copy (testing.md, "A golden fixture can be
mutation-blind").

The goldens never reach the mapper or a resolver: their fixtures build `RecentSessionItem`,
`StartCardBodyUi` and `State.ActiveSessionInfo` with pre-formatted strings. The golden test reads
one catalog string itself: `coldOpenError` passes
`stringResource(R.string.feature_home_refresh_error)` to `PagingErrorFooter`. After the move it
reads `Res.string.feature_home_refresh_error` through CMP's `stringResource`: the first golden test
that reads its module's private `Res` (Section 11.2).

There is no device test. The build declares three `androidTestImplementation` edges and a
`debugImplementation` of the test manifest, but `src/androidTest` holds no file. Those edges are
dead and leave (Section 10). So does `testImplementation(libs.androidx.paging.testing)`, which no
test imports.

### 3.4 Exact feature-local resource catalog

Each locale owns 35 strings and 5 plurals, 40 identifiers, in this file order. Values are shown as
they must be at exit, decoded the way the topology gate compares them (DOM text). The baseline
differs from them only by P1: the 18 plural items that carry a bare `%d` (EN 6, RU 12) become
`%1$d`.

| # | Identifier (all `feature_home_…`) | EN | RU |
| ---: | --- | --- | --- |
| 1 | title | Home | Главная |
| 2 | active_session_label | Session in progress | Сессия в процессе |
| 3 | active_session_progress_format | %1$d of %2$d done | %1$d из %2$d |
| 4 | empty_headline | Ready when you are | Готовы тренироваться |
| 5 | empty_supporting | Start a training to log your first session | Начните тренировку, чтобы записать первую сессию |
| 6 | settings | Settings | Настройки |
| 7 | charts | Charts | Прогресс |
| 8 | start_action | Start | Начать |
| 9 | start_mode_switch | Choose what to show | Выбрать, что показывать |
| 10 | start_mode_forgotten_label | Longest not done | Дольше всего не делали |
| 11 | start_empty_sessions | No workouts yet | Ещё ни одной тренировки |
| 12 | start_empty_tags | Exercises have no tags yet — tag muscle groups and they will show up here | Упражнения без тегов — отмечайте группы, и они появятся здесь |
| 13 | start_never_run | never yet | ещё ни разу |
| 14 | start_groups_footnote | days since the group’s last workout | дней с последней тренировки группы |
| 15 | start_other_training | Another training | Другая тренировка |
| 16 | start_week_day_mon | Mo | Пн |
| 17 | start_week_day_tue | Tu | Вт |
| 18 | start_week_day_wed | We | Ср |
| 19 | start_week_day_thu | Th | Чт |
| 20 | start_week_day_fri | Fr | Пт |
| 21 | start_week_day_sat | Sa | Сб |
| 22 | start_week_day_sun | Su | Вс |
| 23 | recent_adhoc_label | Ad-hoc workout | Свободная тренировка |
| 24 | recent_stats_format | %1$s · %2$s | %1$s · %2$s |
| 25 | picker_title | Choose a training | Выберите тренировку |
| 26 | picker_see_all | See all templates | Все шаблоны |
| 27 | picker_empty | No templates yet | Пока нет шаблонов |
| 28 | picker_start_blank_title | Start blank workout | Пустая тренировка |
| 29 | picker_start_blank_subtitle | Add exercises as you go | Добавляйте упражнения по ходу |
| 30 | conflict_unnamed | Workout | Тренировка |
| 31 | conflict_progress_format | %1$d of %2$d exercises done | %1$d из %2$d упражнений выполнено |
| 32 | paging_loading | Loading | Загружаю |
| 33 | paging_error | Couldn’t load more | Не удалось загрузить дальше |
| 34 | paging_retry | Retry | Повторить |
| 35 | refresh_error | Couldn’t load the list | Не удалось загрузить список |
| 36 | recent_exercises_count | plural, below | plural, below |
| 37 | recent_sets_count | plural, below | plural, below |
| 38 | start_week_sessions_count | plural, below | plural, below |
| 39 | start_days_count | plural, below | plural, below |
| 40 | start_days_idle_count | plural, below | plural, below |

`’` is U+2019 in rows 14, 33 and 35; `—` is U+2014 in row 12.

| Plural | EN one / other | RU one / few / many / other |
| --- | --- | --- |
| recent_exercises_count | `%1$d exercise` / `%1$d exercises` | `%1$d упражнение` / `%1$d упражнения` / `%1$d упражнений` / `%1$d упражнений` |
| recent_sets_count | `%1$d set` / `%1$d sets` | `%1$d подход` / `%1$d подхода` / `%1$d подходов` / `%1$d подходов` |
| start_week_sessions_count | `workout` / `workouts` | `тренировка` / `тренировки` / `тренировок` / `тренировки` |
| start_days_count | `day` / `days` | `день` / `дня` / `дней` / `дня` |
| start_days_idle_count | `%1$d day` / `%1$d days` | `%1$d день` / `%1$d дня` / `%1$d дней` / `%1$d дня` |

The two unit-word plurals (`start_week_sessions_count`, `start_days_count`) carry no placeholder.
Production reads them with the quantity only, and no format argument (Section 5.3).

The XML moves byte-semantically, except P1:

- both roots keep `xmlns:tools="http://schemas.android.com/tools"`;
- the element-level `tools:ignore` attributes move as they are: `PluralsCandidate` on EN rows 3 and
  31, `ImpliedQuantity` on both unit-word plurals in both locales. Kit's and all-exercises' Compose
  catalogs already carry element-level `tools:ignore`;
- the comments move unchanged: EN's two unit-word comments, RU's two.

Nothing outside the module consumes these identifiers, and the generated `Res` stays private.

### 3.5 Declared dependencies

Today's build file declares:

- `convention.composeLibrary`, Metro (`includeJavax()`) and Paparazzi;
- `implementation` edges on `core:core`, `core:ui:kit`, `core:ui:mvi`, `core:ui:navigation`,
  `core:ui:start-mode`, `core:data:exercise`, `core:data:dataStore` and `libs.kotlinx.datetime`;
- `testImplementation` of `libs.androidx.paging.testing`, `kotlin("test")`, the golden harness and
  `libs.androidx.compose.ui.test.junit4`;
- the dead device edges of Section 3.3.

There is no `core:data:database` edge, and none is expected. The only `core:data:database` type on
the repository interfaces home's fakes implement is `PlanSetDataModel`, and only inside two data
classes that no home fake constructs: `SessionRepository.AddExerciseResult`, a return type, which a
fail-fast member overrides as `Nothing`; and `TrainingRepository.ExercisePlanWrite`, the element
type of one member's parameter, which the fake names in its override but never builds. 7.11
Section 3.5 measured the `SessionRepository` case on the Android unit-test classpath (configuration
C3); the parameter case is new. Native is
measured first by the early Native check of Section 12.3. If either compile needs the edge, that is
a STOP-and-amend toward a commonTest-only edge (7.10's D5 shape), never a silent addition.

### 3.6 External consumers and root identity

| Surface | Ownership |
| --- | --- |
| Module edges | app/app `implementation` (stays); app/common `implementation` → `api` |
| Navigation registration | app/common `AppNavigationHost` calls `homeGraph(modifier = …)`, tag `HomeGraph` |
| Graph aggregation | app/app `AppGraph` (gains the accessor) |
| Extension identities | app/app test `HomeExtensionIdentityTest`, through `asContribution` today: `extension resolves the store through the parent graph` and `store's app-scoped deps are the SAME instances the parent holds` |
| Direct composition | none: no app test composes `homeGraph()` directly |
| Device journeys by tag | `RouteReachabilityTest` (`@Regression`): `homeIsTheColdStartDestination` and every journey that returns to Home; seven more app androidTest classes reach the `HomeGraph` tag |
| Route | `Screen.BottomBar.Home`, core:ui:navigation commonMain, unchanged |

No path outside the module reads `io.github.stslex.workeeper.feature.home.R` or a `feature_home_`
identifier.

### 3.7 Context readers and PNG integrity

Seven executable `Context.appDeps` readers exist (`EXPECTED_APP_DEPS_READERS`): six navigation
entries and app-dialogs. Removing home's leaves six.

PNG manifests at the discovery baseline use 7.10 Section 3.7's method, reproduced at `894f2ec6`
from git objects. The base equals 7.11's merge projection, because nothing since has touched a PNG.

| Manifest | Entries | Hash |
| --- | ---: | --- |
| Paparazzi mode/blob/path | 456, 13 owners | cc526e47fd76e92f7427de44de3aa31e91a625a8fec12db7b59b2a09fe85cd00 |
| Paparazzi path list | 456 | d855238ee9806022020e424953fbb54dd809c175e692921e418377a4f31cacc2 |
| All repository PNGs, mode/blob/path | 528 | b5ed4b9d415feb88d515bd0de3359f9f2d3b0881df29828ca98f60476911ad08 |
| home blob set (42 sorted blob IDs, duplicates kept) | 42 | 8c039f3cd062afe6bf019d35e4768f113aacd17306ef4e81c945076abb715ece |

If only the 42 paths move to `src/androidHostTest/snapshots/images/`, the projections are:

- 2ab60a9cfcf296e515ba73d9c90992ccccbf66d5edb3fca779d67a4230972470 (Paparazzi mode/blob/path);
- df423a622a6cbf18c1d8dee9491261f19b4a5486e3fd9bc0220e57f13e8332f6 (path list);
- 48c6ad0d5000b5f546ff1287d7b80e3e3f6dc3886eba6eda5c5f602211b2680e (all PNGs).

The simulated exit tree's index reproduced all three and the unchanged blob set.

**The normative check is relative, as in 7.10.** At implementation entry: take the base manifests;
rewrite exactly the 42 home paths from `src/test/` to `src/androidHostTest/`; require the head
manifests to equal the rewritten base byte for byte; require the blob-set hash to be unchanged.

### 3.8 Measurement limitation

The discovery ran no Gradle task, no Native task and no device test. The implementation records
fresh entry gates (Section 12) and stops if the environment for any of them is missing.

## 4. Platform/API blockers — settled decisions

### 4.1 Android resources become private Compose resources

7.10 Section 4.2 applies with the package of Section 0.3. No `StringResource`,
`PluralStringResource`, Android id, `ResourceWrapper` or `StringResolver` enters State, Action,
Event, UI models, domain models or repository APIs.

### 4.2 Android APIs leave common

| Today | At exit |
| --- | --- |
| `LocalContext` + `context.appDeps<…>()` | explicit factory, shape A (Section 6) |
| `androidx.annotation.VisibleForTesting` | removed; `NAME` stays private |
| `R`, `androidx.compose.ui.res.*` | generated `Res`, `org.jetbrains.compose.resources.*` in composition |
| `ResourceWrapper` copy reads | `StringResolver` (Section 5.3) |
| `ResourceWrapper.getAbbreviatedRelativeTime` | unchanged (S1, Section 5.3) |
| `System.currentTimeMillis()` | `Clock.System.now().toEpochMilliseconds()` (`kotlin.time.Clock`, the 7.9/7.10 row; the KMP convention opts in to `ExperimentalTime`) |
| `uiMode` preview pair | the ThemeMode subject (S3) |
| final use cases mocked by MockK | D2's interface seam (Section 5.5) |

There is no `BackHandler` to port.

### 4.3 Native test names (S6)

None needs a suppression: every identity of Section 3.2 is Native-legal.

kotlin.test has no display name, so the three `@DisplayName` suites keep the function name as the
identity, on Android host and Native alike. Each `@DisplayName("…")` line goes; its sentence moves
verbatim into a one-line KDoc (`/** … */`) directly above `@Test`, so `@Test` directly precedes
`fun` and the gate's `@Test\s+fun` reader sees each identity. Function names and bodies do not
change.

## 5. Resource, State, copy and preview architecture

### 5.1 Private generated resource owner

7.10 Section 5.1 applies: `compose.resources { packageOfResClass =
"io.github.stslex.workeeper.feature.home.resources" }` and no `publicResClass`, so the generated
`Res` is internal to the module. No home file reads kit's or start-mode's `Res`, so every file
imports the feature's `Res` unaliased.

### 5.2 F1 — the two hoists (required)

`updateStateImmediate(update: suspend (S) -> S)` is `_state.emit(update(state.value))` in
`BaseStore`: a suspend lambda, and not atomic. Once the mapper is `suspend`, a resolver call inside
the lambda compiles, and an `updateState` that lands while it is suspended (the same handler's
one-second tick, a click that opens a sheet) is overwritten by the stale snapshot.
`StringResolver`'s KDoc already says "Never called inside a State lambda", and architecture.md's
State mutation discipline already forbids mapping there. Today two mappings break that rule; both
move before their lambdas, in commit 2, while the module is still classic (S4).

**CommonHandler, the active session.** Today the lambda reads `now` from `current` and maps inside.
At exit (shown with commit 3's resolver and clock; commit 2 has `resourceWrapper` and
`System.currentTimeMillis()` in the same places):

    interactor.observeActiveSession().launch { row ->
        logger.i(…)                                     // unchanged
        // F1: the copy resolves before the State lambda, which only copies State.
        val now = state.value.nowMillis.takeIf { it > 0L }
            ?: Clock.System.now().toEpochMilliseconds()
        val activeSession = row?.toUi(now, stringResolver)
        updateStateImmediate { current ->
            current.copy(
                activeSession = activeSession,
                isActiveLoaded = true,
                // As today: only the first row seeds nowMillis, later rows keep the tick's.
                nowMillis = current.nowMillis.takeIf { it > 0L } ?: now,
            )
        }
    }

`now` is read from `state.value` before the mapping, the form ClickHandler's picker already uses
(`nowMillis` is either 0 or an epoch time, so `takeIf { it > 0L }` equals today's `== 0L` test).
The lambda keeps today's rule for `nowMillis` itself: only the first row seeds it, so a tick that
lands while the mapping is suspended is never overwritten by an older value. The one observable
difference: in that window the new row's `elapsedDurationLabel` is mapped with the `now` read
before the tick, so it trails by one tick until the next tick recomputes it. The tick loop's own
lambda is unchanged: it formats `elapsedDurationLabel` from `current`, a pure transform of State
without copy, which 7.9 and 7.10 kept inside lambdas as an explicit non-goal.

**ClickHandler, the training picker.** The mapping runs before the lambda, unconditionally; the
lambda still copies it in only while the sheet is the picker, so the result is the same:

    interactor.observeRecentTrainings(PICKER_LIMIT).launch { trainings ->
        val now = state.value.nowMillis.takeIf { it > 0L }
            ?: Clock.System.now().toEpochMilliseconds()
        // F1: mapped before the State lambda, and copied only if the picker is still up.
        val templates = trainings.toPickerItems(now, resourceWrapper, stringResolver)
        updateStateImmediate { current ->
            if (current.bottomSheet is BottomSheetState.TrainingPicker) {
                current.copy(
                    bottomSheet = BottomSheetState.TrainingPicker(
                        templates = templates,
                        isLoading = false,
                    ),
                )
            } else {
                current
            }
        }
    }

The comment lines are part of the contract only in meaning; their wording may differ.
CommonHandler's start-card collector already maps before its lambda (`val body = readout.toUi(…)`)
and stays as it is, apart from the resolver.

D3 makes the gate fail both shapes from now on, in every shared entry (Section 12.0).

### 5.3 Copy outside composition — StringResolver, and S1

`HomeUiMapper` keeps its `internal object` and file. Its five functions become `suspend` and take
the resolver; the two row mappers keep the wrapper for the relative time:

    suspend fun StartCardReadoutDomain.toUi(stringResolver: StringResolver): StartCardBodyUi
    suspend fun WeekReadoutDomain.toUi(stringResolver: StringResolver): StartCardBodyUi.Week
    suspend fun ActiveSessionWithStatsDomain.toUi(
        nowMillis: Long,
        stringResolver: StringResolver,
    ): ActiveSessionInfo
    suspend fun RecentSessionDomain.toRecentItem(
        nowMillis: Long,
        resourceWrapper: ResourceWrapper,
        stringResolver: StringResolver,
    ): RecentSessionItem
    suspend fun List<TrainingListItemDomain>.toPickerItems(
        nowMillis: Long,
        resourceWrapper: ResourceWrapper,
        stringResolver: StringResolver,
    ): ImmutableList<PickerTrainingItem>

Each read keeps its resource, quantity and arguments:

- each `resourceWrapper.getString(R.string.x, args…)` becomes
  `stringResolver.getString(Res.string.x, args…)`: 12 in the mapper, the seven weekday labels
  through one call over `WEEK_DAY_LABELS`, which becomes a list of
  `Res.string.feature_home_start_week_day_*`;
- each `resourceWrapper.getQuantityString(R.plurals.x, quantity, args…)` becomes
  `stringResolver.getPluralString(Res.plurals.x, quantity, args…)`: 7 in the mapper. The five reads
  of the three `%1$d` plurals pass the count as the one format argument, as today; the two reads of
  the unit-word plurals (`start_days_count` with `daysSince`, `start_week_sessions_count` with
  `sessionsThisWeek`) pass the quantity and **no** format argument, as today;
- the mapper then holds exactly 12 `stringResolver.getString(` and 7
  `stringResolver.getPluralString(`.

These are unchanged: the mappings themselves (`barFraction`'s `maxIdle > 0` guard, the
`daysIdle?.let { … } ?: never_run` fallback, the shared `picker_empty` key for `NoTemplates`), the
`DateTimeUtil.formatMillis` anchor date, `formatElapsedDuration`, and every KDoc.

**S1, the relative time.** `resourceWrapper.getAbbreviatedRelativeTime(finishedAt, nowMillis)` in
`toRecentItem` and `resourceWrapper.getAbbreviatedRelativeTime(it, nowMillis)` in `toPickerItems`
stay, and they are the only `ResourceWrapper` reads in the module. A relative time is a
locale-aware format, not catalog copy; kmp-string-resolver.md keeps date and time formatting on
`ResourceWrapper`, and archive's `formatDayMonth` and past-session's `formatMediumDate` are the
precedents. This is the first `getAbbreviatedRelativeTime` allowance and the repository's only
production consumer of that member. `ResourceWrapper`'s only implementation is androidMain's
`AndroidResourceWrapper` (`DateUtils`), so iOS has none, and the scene stubs exactly the timestamps
it renders (Section 11.5). A portable date formatter is out of scope (S5 records it).

**The handlers.**

- `ClickHandler` injects the resolver beside the wrapper. Constructor order: `interactor`,
  `resourceWrapper`, `stringResolver`, `store`. Its two conflict reads become
  `stringResolver.getString(Res.string.feature_home_conflict_unnamed)` and
  `stringResolver.getString(Res.string.feature_home_conflict_progress_format, resolution.doneCount,
  resolution.totalCount)`, inside the existing `launch { … }` and before its
  `updateStateImmediate { it.copy(pendingConflict = info) }`, as today. The picker passes both to
  `toPickerItems` (Section 5.2).
- `CommonHandler` swaps the wrapper for the resolver. Constructor order: `interactor`,
  `stringResolver`, `store`. It passes the resolver to `row?.toUi(now, stringResolver)` and
  `readout.toUi(stringResolver)`, and no longer names `ResourceWrapper`.
- `PagingHandler` injects the resolver beside the wrapper. Constructor order: `interactor`,
  `resourceWrapper`, `stringResolver`, `defaultDispatcher`, `store`. The paged map becomes
  `pagingData.map { session -> session.toRecentItem(now, resourceWrapper, stringResolver) }`, with
  its one clock read per generation unchanged. `PagingData.map` takes a suspend transform.

**Threading.** 7.10 Section 5.2's reasoning applies: every mapping runs where today's
`ResourceWrapper` call runs (the store's collection dispatcher, and the paging flow's
`flowOn(defaultDispatcher)`), except F1's two, which leave the State lambda for their collector
bodies, as the State mutation rule wants. CMP's first catalog read in a process is a blocking asset
read and later reads are cached; do not move the work otherwise in this phase.

**The argument rule** (7.10 Section 5.2) applies. `StringResolver` returns the catalog text
verbatim when a call passes no format arguments, so a `%1$d` plural read that dropped its count
would ship `%1$d sets`. The unit-word plurals are the reverse case: they must pass no format
argument. This phase is the first production use of `getPluralString` without format arguments.
Kit's resolver tests cover `getString` only, but the 7.10 and 7.11 scenes read their plurals as
stored, with no format argument, so the path already executes on Native. The strict
fakes (Section 11.1) and controls 43 and 44 pin both directions.

The event, the State and the UI models keep their shape: `Event.HapticClick(type:
HapticFeedbackType)`, `State.ConflictInfo` and `State.ActiveSessionInfo` with their pre-formatted
strings, and the pre-formatted strings of `RecentSessionItem`, `PickerTrainingItem` and
`StartCardBodyUi`.

This module follows tech-debt.md's "Shared (KMP) string resources" rule, so it adds no debt row or
`TODO(tech-debt)` marker for the resolver.

### 5.4 Composable copy and previews (S3)

The five UI files of Section 3.1 resolve copy with `org.jetbrains.compose.resources.stringResource`
on the private `Res`, in the same places and with the same arguments: for example
`stringResource(Res.string.feature_home_active_session_progress_format, info.doneCount,
info.totalCount)` in `ActiveSessionBanner`, and
`stringResource(Res.string.feature_home_start_other_training).uppercase()` in `HomeStartCard`.
`PagingErrorFooter`'s default argument `reason: String = stringResource(…paging_error)` stays a
composable default. Each file loses its Android `stringResource` and `R` imports.

The `TODO(tech-debt): UI mapping boundary` marker in `TrainingPickerSheet.kt` moves unchanged.

**Previews.** 17 of the 19 are already portable and stay byte-identical, names included. 11 carry an
argument list, which `preview_inventory` pins:

| File | `@Preview` arguments, in source order |
| --- | --- |
| ui/components/ActiveSessionBanner.kt | `device = "id:pixel_9"` |
| ui/components/HomeStartCard.kt | `name = "Week — Light"`, `name = "Week — Dark"`, `name = "Days since — Dark"`, `name = "Lagging groups — Dark"`, `name = "Forgotten — Dark"`, `name = "Mode unknown — Dark"`, `name = "Empty — Dark"` |
| ui/components/TrainingPickerSheet.kt | `name = "Loaded — Light"`, `name = "Loading — Dark"`, `name = "Empty — Dark"` |

Six are a bare `@Preview` with no argument list: four in `ui/HomeScreen.kt` and two in
`ui/components/ActiveSessionBanner.kt`. The shared gate counts only `@Preview(`, so it cannot see
them; `home_preview_contract` pins their count per file (Section 12.1). `@Preview(device = …)` is
the first `device` argument in commonMain. CMP's `ui-tooling-preview` declares the multiplatform
annotation, but its parameter list was not verified; if commonMain rejects `device`, STOP.

`RecentSessionRow.kt`'s pair, today one function under `@Preview(name = "Light", showBackground =
true)` and `@Preview(name = "Dark", showBackground = true, uiMode = …UI_MODE_NIGHT_YES)` with a bare
`AppTheme { … }`, becomes 7.9's subject shape (archive's `ArchivedItemRow` is the model):

    @Preview(name = "Light", showBackground = true)
    @Composable
    private fun RecentSessionRowLightPreview() {
        RecentSessionRowPreview(themeMode = ThemeMode.LIGHT)
    }

    @Preview(name = "Dark", showBackground = true)
    @Composable
    private fun RecentSessionRowDarkPreview() {
        RecentSessionRowPreview(themeMode = ThemeMode.DARK)
    }

    @Composable
    private fun RecentSessionRowPreview(themeMode: ThemeMode) {
        AppTheme(themeMode = themeMode) {
            Column { … }                                // the two rows, unchanged
        }
    }

That makes 13 `@Preview(` (2 from the subject and 11 from the inventory) and 6 bare ones.

### 5.5 D2 — the use-case seam

Metro generates the graph extension in `:app` and rejects internal contributions (PR-R K4), so a
`@Binds` in the public `HomeGraph` must name public types. The seam therefore has the
`HomeInteractor` / `HomeInteractorImpl` shape:

- `domain/usecase/ObserveStartCardReadoutUseCase.kt` holds the public interface, with today's KDoc:

      interface ObserveStartCardReadoutUseCase {

          operator fun invoke(
              mode: StartCardModeDomain,
              nowMillis: Long,
              timeZone: TimeZone = TimeZone.currentSystemDefault(),
          ): Flow<StartCardReadoutDomain>
      }

- the new `domain/usecase/ObserveStartCardReadoutUseCaseImpl.kt` holds today's class body, with
  `TAG_IDLE_LIMIT`, as

      /** The production [ObserveStartCardReadoutUseCase], bound in `HomeGraph` (D2). */
      @Inject
      @SingleIn(HomeScope::class)
      class ObserveStartCardReadoutUseCaseImpl internal constructor(
          private val sessionRepository: SessionRepository,
          private val tagRepository: TagRepository,
          private val trainingRepository: TrainingRepository,
          private val observeWeekReadoutUseCase: ObserveWeekReadoutUseCase,
          @DefaultDispatcher private val defaultDispatcher: CoroutineDispatcher,
      ) : ObserveStartCardReadoutUseCase {

          override operator fun invoke(
              mode: StartCardModeDomain,
              nowMillis: Long,
              timeZone: TimeZone,
          ): Flow<StartCardReadoutDomain> = …            // today's body, unchanged

- `ObserveWeekReadoutUseCase` / `ObserveWeekReadoutUseCaseImpl` follow the same shape
  (`invoke(nowMillis: Long, timeZone: TimeZone = TimeZone.currentSystemDefault()):
  Flow<WeekReadoutDomain>`);
- di/HomeGraph.kt gains, after `bindInteractor`:

      @Binds
      val ObserveStartCardReadoutUseCaseImpl.bindStartCardReadout: ObserveStartCardReadoutUseCase

      @Binds
      val ObserveWeekReadoutUseCaseImpl.bindWeekReadout: ObserveWeekReadoutUseCase

`HomeInteractorImpl` keeps its constructor: it already takes `ObserveStartCardReadoutUseCase`, now
the interface. The default `timeZone` stays on the interface, evaluated at the call site as today,
so `kotlinx.datetime.TimeZone` enters the public API (`api(libs.kotlinx.datetime)`, Section 10).
DomainLayerPurityRule and DomainLayerNoUiRule accept the shape: the domain files import repository
abstractions and `*Domain` types only. This is the template for feature:exercise's four use cases.

D2 lands in commit 2, in `src/main`, with the MockK suites: they mock the interfaces as they mocked
the classes, and the two use-case suites construct the `…Impl` classes. No identity, stub or
assertion changes there.

### 5.6 Back

The module declares no `BackHandler`, and system back reaches Nav3's default pop, as today.

### 5.7 The inherited locale property

7.9 Section 5.7 applies, and from this phase it also covers home's copy: the screen follows CMP's
primary-locale choice, so a device whose locale list is [uk-UA, ru-RU] shows Russian today and
English after the move, and counts render with Latin digits. The relative time stays on Android's
`DateUtils` and its configuration locale, as archive's and past-session's dates do; on such a device
a recent row can therefore pair English copy with a Ukrainian relative time. The unit-word plurals
keep their CLDR plural rules per selected catalog. `StringResolver` has the same property (its KDoc,
and tech-debt.md's 🟡 row); the remedy is the maintainer's open decision, not part of this phase.

## 6. Explicit generation-owned shape-A factory flow

7.10 Section 6 applies, with the substitutions of Section 0.3:

- `AppRootDeps` gains `val homeGraphFactory: HomeGraph.Factory` after `pastSessionGraphFactory`,
  with the KDoc `/** Generation-owned factory for the home graph extension. */`;
- `AppGraph` overrides it, after `pastSessionGraphFactory`;
- `App.kt` passes `homeGraphFactory = deps.homeGraphFactory` after the past-session line;
- `AppNavigationHost` takes a required `homeGraphFactory: HomeGraph.Factory` after
  `pastSessionGraphFactory`. It calls `homeGraph(factory = homeGraphFactory, modifier = …)`, and the
  modifier chain is unchanged:
  `bottomBarModifier.reportScreenPlace<Screen.BottomBar.Home>().testTag("HomeGraph")`;
- `homeGraph` takes a required `factory: HomeGraph.Factory` as its first parameter and builds
  `navComponentScreen(HomeFeature(factory)) { processor -> … }`; the rest of the function is
  unchanged;
- `HomeFeature` becomes an `internal class` with `private val factory: HomeGraph.Factory`, still
  `Feature<HomeStoreProcessor, Home>`. Its `processor()` returns:

      rememberMetroStoreProcessor<HomeStoreImpl> {
          factory
              .createHomeGraph()
              .homeStore
      } as HomeStoreProcessor

- `createHomeGraph()` runs once, inside the retained `rememberMetroStoreProcessor` lambda;
- di/HomeGraph.kt changes only by D2's two bindings;
- app/common's edge becomes `api(project(":feature:home"))`, placed after
  `api(project(":feature:past-session"))`. The comment "The twelve entry providers AppNavigationHost
  composes." stays true.

`LocalContext`, `appDeps`, `asContribution`, nullable fallbacks, registries and pre-admission
resolution are forbidden.

`HomeExtensionIdentityTest` gets the helper, after `buildAppGraph()`:

    private fun AppGraph.home(): HomeGraph = homeGraphFactory.createHomeGraph()

The `asContribution` import goes. Both names, the call sites (`buildAppGraph().home().homeStore` and
`appGraph.home().homeStore`, each exactly once) and every assertion stay exact.

No app test composes `homeGraph()` directly, so the app boundary is six paths (Section 9.2).

## 7. In scope

Only these:

- D3's gate check, as this PR's first commit (Section 12.0);
- D2's seam and F1's hoists, in `src/main` (Sections 5.2, 5.5);
- the conversion of Sections 4–6;
- the moves of Sections 3.1–3.4 and P1;
- S1, the clock port and the removal of `@VisibleForTesting`;
- the composable copy, the verbatim previews and the RecentSessionRow subject;
- porting the 12 suites to kotlin.test with deterministic in-file fakes, and the mode-label test to
  iosTest;
- moving the golden test to androidHostTest;
- removing the dead edges;
- one iosTest scene;
- the six app paths, the three CI paths and the five documentation paths;
- one signed, green PR that the maintainer merges.

## 8. Explicit non-goals

7.11 Section 8 applies. In addition:

- no change to `core:ui:kit`, `core:ui:start-mode`, `core:core` (`ResourceWrapper` gets no iOS
  implementation), `core:data:exercise` or `core:data:dataStore`. The `PlanSetDataModel` leak stays
  7.10's follow-up;
- no portable date formatter (S1);
- no non-suspend `updateStateImmediate` lambda in core:ui:mvi. It would make F1 a compile error in
  every feature, but it touches every feature; S5 records it;
- no other State lambda hoist: the tick loop's `formatElapsedDuration`, and all-exercises' and
  all-trainings' pure `toTagUi` mappings, stay where they are;
- no device test for the module, and no UI path for the start-card mode sheet, the conflict dialog
  or the paging tails in the scene (Section 11.6);
- no change to the `TODO(tech-debt)` marker or to the text of tech-debt.md's three 🟢 home rows; only
  their links are re-pointed. Row 491 names its location as plain text and stays;
- no edit to `.claude/skills/` (7.10's D4);
- no new test identity beyond the scene, no rename of any Section 3.2 identity (S6 keeps the
  function names), and no strengthened assertion;
- no change to `graphify-out/`.

## 9. Exact allowed change boundary

### 9.1 Target module — 102 tracked paths at exit

- `build.gradle.kts`;
- 41 production files under `src/commonMain/kotlin/io/github/stslex/workeeper/feature/home/`
  (Section 3.1, plus `domain/usecase/ObserveStartCardReadoutUseCaseImpl.kt` and
  `domain/usecase/ObserveWeekReadoutUseCaseImpl.kt`);
- `src/commonMain/composeResources/values/strings.xml` and `values-ru/strings.xml`;
- 13 files under `src/commonTest/kotlin/io/github/stslex/workeeper/feature/home/`: the 12 suites of
  Section 3.2 and `mvi/store/HomeTestState.kt`;
- `src/androidHostTest/kotlin/io/github/stslex/workeeper/feature/home/golden/HomeGoldenTest.kt`;
- 42 PNGs under `src/androidHostTest/snapshots/images/` (Section 3.3 names);
- `src/iosTest/kotlin/io/github/stslex/workeeper/feature/home/ui/components/HomeStartCardModeLabelTest.kt`;
- `src/iosTest/kotlin/io/github/stslex/workeeper/feature/home/HomeFeatureSceneIosTest.kt`.

That is 101 files under `src` plus the build file. Nothing remains under `src/main`, `src/test` or
`src/androidTest`. There is no androidMain, iosMain, androidDeviceTest, new shared test helper,
fixture or extra catalog.

### 9.2 Root, CI and documentation — 14 paths

App (6):

    app/app/src/main/java/io/github/stslex/workeeper/di/AppGraph.kt
    app/app/src/test/kotlin/io/github/stslex/workeeper/di/HomeExtensionIdentityTest.kt
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
    documentation/feature-specs/kmp-phase-7-12-home-feature.md

What changes in each document. This list covers every place 7.11's commits touched for past-session
(`git diff fe57277c0 894f2ec6b -- documentation/`), except design-system.md, which only D1 of 7.11
concerned. Every module count there reads twelve.

- **architecture.md:**
  1. In the shape list, item 1 (plain `Feature`): the sentence naming the shared archive,
     all-trainings and all-exercises entries as the plain-shape factory-resolution exceptions adds
     home: `ArchiveGraph.Factory` / `AllTrainingsGraph.Factory` / `AllExercisesGraph.Factory` /
     `HomeGraph.Factory` through `AppNavigationHost` and `archiveGraph` / `allTrainingsGraph` /
     `allExercisesGraph` / `homeGraph` into `ArchiveFeature` / `AllTrainingsFeature` /
     `AllExercisesFeature` / `HomeFeature`. The example `Screen.BottomBar.Home` for a plain feature
     without route arguments stays.
  2. The Context-seam paragraph: home joins the list, `AppGraph.homeGraphFactory` joins the accessor
     list, and the sentence reads "Exactly 6 feature/dialog Context readers remain."
  3. "State mutation discipline", after its last paragraph, two sentences: "`updateStateImmediate`'s
     lambda is `suspend` and is not atomic, so a resolver suspending inside it could lose a
     concurrent update; that is one more reason the copy resolves before it. For the shared feature
     entries, `assert_kmp_ui_source_topology.py` fails a `stringResolver` or `resourceWrapper`
     reference inside a handler's `updateState { … }` or `updateStateImmediate { … }` lambda
     (decision D3 of
     [kmp-phase-7-12-home-feature.md](feature-specs/kmp-phase-7-12-home-feature.md)), beside its
     older check for resource reads there."
- **ci-cd.md**, the places 7.11 updated for past-session:
  - the `android_build_unified.yml` row of the workflows table ("…, past-session feature, and home
    feature Native tests …, then links the same twelve modules' …");
  - the KMP-shaped alias list, which adds `:feature:home`;
  - the Native job's module description, which adds `:feature:home` (all 70 portable cases, the
    iOS mode-label case, and the production scene: the catalog and the readout, row, picker and
    banner copy through the real `StringResolver`, the loading, banner, start-card and list states,
    the empty region, the training picker, and dispatch);
  - the every-module-is-checked list;
  - the validator paragraph, which adds: "The home feature validator requires all 70 portable
    tuples,
    `io.github.stslex.workeeper.feature.home.ui.components.HomeStartCardModeLabelTest.theHeadNamesNoModeUntilOneIsKnown`
    and
    `io.github.stslex.workeeper.feature.home.HomeFeatureSceneIosTest.resourcesReadoutsListPickerAndActionsRenderAndDispatch`
    exactly once each, for exactly 72 target tuples.";
  - "All twelve result directories";
  - "The same twelve modules'" and `N/12 linked`.
- **testing.md:**
  1. The `AppGraph.stringResolver` bullet: home's `ClickHandler`, `CommonHandler` and
     `PagingHandler` join the constructor-injected readers.
  2. A "Shared home feature" subsection after "Shared past-session feature". It covers:
     - the 70 identities on both targets, and the mode-label identity on Native only (S2);
     - the store fake that reproduces the relaxed store, its silent logger, and the in-file
       interactor, use-case, repository, data-store and navigator fakes (D2);
     - the strict resolver fakes, including the unit-word rule, and the relative-time-only
       `ResourceWrapper` fakes;
     - the `@DisplayName` rule (S6);
     - no `core:data:database` edge, not even in tests;
     - the scene, with its relative-time stub;
     - what it does not cover (Section 11.6);
     - the golden test on androidHostTest with its one private `Res` read, its 42 byte-identical
       PNGs, and the duplicate RU blobs;
     - the identity test through `appGraph.homeGraphFactory`;
     - the absence of a device test, and the `RouteReachabilityTest` journeys.
  3. "Visual gate — Paparazzi screenshot goldens": in both module lists, home moves from the classic
     modules (`src/test`) to the KMP modules (`src/androidHostTest`).
  4. The sentence naming the KMP-shaped modules that both alias spellings cover adds
     `:feature:home`.
  5. The Native command block under "Running tests" adds `:feature:home:iosSimulatorArm64Test`.
  6. The device-link block adds `:feature:home:linkDebugTestIosArm64`, and its comment reads
     `12/12`.
- **tech-debt.md:**
  1. Rows 24–26 (ActiveSessionBanner, RecentSessionRow, TrainingPickerSheet): the links re-pointed
     to `src/commonMain/kotlin/…`; the text stays.
  2. Row 463 (State Mutation Discipline, home `CommonHandler`): becomes `✅ RESOLVED`, the link
     re-pointed and its stale `:36-39` dropped, the text kept and followed by "Resolved by Phase
     7.12 (F1): the mapping runs before the lambda here and in `ClickHandler`'s picker, and the
     topology gate fails a resolver or wrapper reference inside any shared entry's State lambda
     (D3)." The format follows the table's existing `✅ RESOLVED` rows.
  3. One 🟢 row in the same table: "A non-suspend `updateStateImmediate` lambda in core:ui:mvi would
     make a suspending call inside any State lambda a compile error in every feature; today only
     the shared entries' gate check (D3) catches it." Location: `BaseStore` in core:ui:mvi.
  4. In "Shared (KMP) string resources", three rows (S5):
     - 🟡 runtime RU catalog coverage: no test in archive, all-trainings, all-exercises, past-session
       or home sets a Russian locale or reads `values-ru` at runtime; the topology gate pins the RU
       values statically;
     - 🟢 a typed `rememberMetroStoreProcessor` would drop the inherited
       `@Suppress("UNCHECKED_CAST")` from the seven shared features' `*Feature.kt` (archive,
       all-trainings, all-exercises, image-viewer, plan-editor, past-session, home);
     - 🟢 a portable date formatter would retire archive's `formatDayMonth`, past-session's
       `formatMediumDate` and home's two `getAbbreviatedRelativeTime` reads, the only
       `ResourceWrapper` reads left in shared entries; each iOS scene stubs them.

Known stale references after the move, deliberately untouched. They are historical records, already
stale before this phase, or owned by a follow-up documentation PR:

- `documentation/feature-specs/v2.4-design-foundation.md` line 569,
  `documentation/feature-specs/home-and-past-session.md` line 1039,
  `documentation/feature-specs/live-workout.md` line 1050,
  `documentation/feature-specs/kmp-phase-7-4-start-mode.md` line 120 (that phase's own scope item
  for the mode-label test),
  `documentation/research/v2.3-hotfix-findings.md` lines 1063, 1079 and 1098, and
  `documentation/research/domain-boundary-audit-codex.md` lines 41–44, 81, 108 and 123, which name
  old home paths;
- the fixtures of lint-rules' `ActiveSurfaceSingleReaderRuleTest.kt` (lines 21, 222 and 244): they
  compile synthetic `feature/home/src/main/…` paths, and the rule keys only on its permitted reader
  and exempt paths, so they stay valid;
- `documentation/mockups/mutation_harness.py` line 14: its example's elided path
  `feature/home/src/.../PagingTailKind.kt` and `:feature:home:verifyPaparazziDebug` (a lifecycle
  alias in KMP modules) stay valid;
- `documentation/kmp-migration-assessment.md`;
- `.claude/skills/*` (7.10's D4), including add-feature.md line 37;
- tech-debt.md row 491, which names its location as plain text;
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
    packageOfResClass = "io.github.stslex.workeeper.feature.home.resources"
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
            api(project(":core:ui:start-mode"))
            implementation(project(":core:data:exercise"))
            implementation(project(":core:data:dataStore"))

            api(libs.cmp.ui)
            api(libs.androidx.paging.common)
            api(libs.coroutines.core)
            api(libs.kotlinx.collections.immutable)
            api(libs.kotlinx.datetime)
            implementation(libs.androidx.compose.paging)
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

- `api(project(":core:ui:kit"))`: `HomeStore.State.pagingUiState` is the kit's `PagingUiState`
  (all-trainings' and all-exercises' reason; unlike 7.11's S4, home's public State names a kit
  type);
- `api(project(":core:ui:mvi"))`: `HomeStore`, `HomeHandlerStore`, `HomeHandlerStoreImpl` and
  `HomeStoreImpl` extend the MVI contracts;
- `api(project(":core:ui:navigation"))`: `homeGraph` extends `NavGraphScope`;
- `api(project(":core:ui:start-mode"))`: `State.startCardMode` and
  `Action.Click.OnModeSelected.mode` are its `StartCardModeUi`;
- `implementation` of core:core, core:data:exercise and core:data:dataStore: their types appear only
  in internal constructors and internal code (the dispatcher qualifiers, the repositories,
  `CommonDataStore`, `ResourceWrapper`). app/app and app/common declare them, so Metro's extension
  implementation resolves them there;
- `api(libs.cmp.ui)`: `homeGraph` takes a `Modifier`, and `Event.HapticClick` carries a
  `HapticFeedbackType`;
- `api(libs.androidx.paging.common)`: `HomeInteractor.pagedRecent` returns `PagingData`, and the
  State carries it;
- `api(libs.coroutines.core)`: `HomeInteractor` and the use cases return `Flow`;
- `api(libs.kotlinx.collections.immutable)`: `StartCardBodyUi` and
  `BottomSheetState.TrainingPicker` carry `ImmutableList`;
- `api(libs.kotlinx.datetime)`: the use-case interfaces take a `TimeZone` (D2). kotlinx-datetime is
  `implementation` in core:core, so it does not arrive transitively (the base build file's comment
  says so);
- `implementation(libs.androidx.compose.paging)`: `LazyPagingItems` in `HomeScreen`;
- `implementation(libs.cmp.animation)`: `AnimatedContent`, `fadeIn`/`fadeOut` in `HomeScreen`,
  `animateColorAsState` and `tween` in `HomeStartCard`;
- `implementation(libs.cmp.material.icons.extended)`: `QueryStats`, `FitnessCenter` and
  `AddCircleOutline`. If the build shows an icon is not in that artifact, STOP and amend; never
  switch icons.

`kotlin("test")` appears exactly twice, once in commonTest and once in iosTest. The iOS mode-label
test needs nothing beyond `cmp.ui.test`, which the scene already needs.

Removed relative to today: `testImplementation` (replaced by commonTest, iosTest and
`androidHostTestImplementation`), `libs.androidx.paging.testing` and
`libs.androidx.compose.ui.test.junit4` with it, and every `androidTestImplementation` and the
`debugImplementation` of the test manifest. There is no device test, so neither the Compose BOM nor
the test-utils guard applies.

The module's own history comments move with the build file: the Metro and Paparazzi plugin
comments, the start-mode and data-store comments, and the kotlinx-datetime comment. Rationale
comments adapted from the bullets above may be added. No comment may hold a dependency fragment or a
forbidden build token (`mockk`, `robolectric`, `androidMain`, `iosMain`, `paging.testing`,
`testImplementation(`, …).

Verify Gradle metadata rather than copying it blindly (7.8 Section 10). A dependency the measured
source does not need is removed. One it needs is a STOP-and-amend, never a silent addition.

## 11. Test and Native scene contract

### 11.1 Portable suites

The 12 suites and the helper move to commonTest. JUnit 5, MockK and Robolectric give way to
`kotlin.test`, `kotlinx-coroutines-test` and deterministic in-file fakes: named private classes,
and no shared helper beyond the existing `HomeTestState.kt`. Android host and Native each execute
exactly the 70 identities of Section 3.2, with zero failure, error or skip, in the distribution
6/9/2/16/2/5/14/3/1/5/3/4.

7.10 Section 11.1, 7.11 Section 11.1 and the fake rules of their Sections 19 apply. In this module
that means:

- **The handler store fake** (ClickHandlerTest, CommonHandlerTest). Each suite declares a private
  `HomeHandlerStore` that reproduces today's relaxed MockK store, as all-exercises' click-handler
  fake did:
  - `state` is the identity's `MutableStateFlow`; `updateState` and `updateStateImmediate(update)`
    apply to it (applying the latter always is equivalent: the one identity that reaches it stubbed
    it to apply);
  - `sendEvent`, `consume` and `consumeOnMain` record;
  - each Store-scope `launch` is recorded. Its action runs, to completion before the identity's
    assertions, only in the three identities that ran it under MockK (`executeLaunch = true`):
    `OnModeSelected hides the sheet and persists the MAPPED mode through the interactor`,
    `OnPickerTrainingSelected reports the persisted active session progress` and
    `OnStartActionClick under a Forgotten body starts THAT training no picker`. It runs on the
    identity's `runTest` scheduler (past-session's `TestStore` or all-exercises' `RecordedLaunch`
    shape), never under `runBlocking`, which no commonTest in the repository uses;
  - `Flow.launch` records without collecting, as the relaxed store did: no identity collects a flow;
  - `lastAction`, `launchDefault` and `updateStateImmediate(state)` fail fast.
- **The logger.** `CommonHandler`'s Init calls `logger.i { … }` before it subscribes, so it is the
  first handler of a shared entry whose identity path reads the logger. CommonHandlerTest's store
  fake returns a private strict silent `Logger`: `i(message: () -> String)` does nothing and every
  other abstract member (12) fails fast; the three defaulted members are not overridden.
  ClickHandlerTest's store fake keeps 7.9's fail-fast getter
  (`error("logger must not be read by ClickHandler")`).
- **Interactor fakes** (9 members each), all other members failing fast:
  - ClickHandlerTest: `setStartCardMode` records; `resolveStartConflict` answers `"tpl-1"` with the
    identity's `NeedsUserChoice` and `"tpl-9"` with `ProceedFresh`;
    `getTrainingName("active-training")` answers `"Push Day"`; `observeRecentTrainings` records its
    limit and returns `emptyFlow()` (the picker-opening identities reach it; nothing collects it);
  - CommonHandlerTest: `observeActiveSession()` and `observeStartCardMode()` return `emptyFlow()`
    and count their calls; `pagedRecent()` counts its calls, so
    `verify(exactly = 0) { interactor.pagedRecent() }` becomes an asserted count of 0.
- **The interactor suite** declares `SessionRepository` (26: `observeActiveSessionWithStats()`
  returns the identity's flow), `TrainingRepository` (20, all fail fast), `CommonDataStore` (8:
  `homeStartCardMode` returns the identity's flow, `setHomeStartCardMode` records) and
  `ObserveStartCardReadoutUseCase` (D2: records `(mode, nowMillis, timeZone)` and returns the
  identity's flow). `SessionConflictResolver` is constructed for real over the `SessionRepository`
  fake and never called. MockK's `verify(exactly = 1) { useCase(mode = WEEK, nowMillis = 42L) }`
  evaluated the interface default at verification, so it becomes exactly one recorded call equal to
  `(WEEK, 42L, TimeZone.currentSystemDefault())`.
- **The use-case suites** keep their strict doubles' semantics: `SessionRepository`
  (`observeLastFinishedSession()`, and `observeFinishedTimesBetween(start, end)` for exactly the
  identity's window), `TagRepository` (`observeTagIdleStats(3)`), `TrainingRepository`
  (`observeMostForgottenTemplate()`) and `ObserveWeekReadoutUseCase` (`(now, zone)` only) answer the
  identity's flows; every other member and argument fails fast. They construct the `…Impl` classes
  (Section 5.5).
- **NavigationHandlerTest** declares a private `Navigator` (6 members): `navTo` records, the other
  five fail fast.
- A fail-fast member may override its return type as `Nothing`, which keeps `PlanSetDataModel`
  off the source. The fakes import `core:data` shapes directly, which PR-L's test-source-set
  exemption allows. A fake overriding a deprecated member marks the override `@Deprecated`, as
  7.11's did; that is no suppression.
- **MockK verifications become recorded-state assertions**, as in 7.10 and 7.11:
  `verify(exactly = 1) { … }` becomes exactly one recorded call with the same arguments;
  `verify(exactly = 0) { … }` becomes an empty record (or a count of 0); a `slot` capture becomes
  the recorded value. kotlin.test's `assertEquals` may need type witnesses (`listOf<Event>(…)`), as
  in 7.11 Section 19.8.

**StringResolver fakes.** `HomeUiMapperTest` and `ClickHandlerTest` each declare their own private
strict fake, and `CommonHandlerTest` one that fails fast on both members (no identity reaches copy).
A fake answers by switching on the generated resource objects, as testing.md's "Shared string
resolver" prescribes, and throws on any other resource. It also checks arguments. A read throws
unless it passes exactly what its production call passes:

- no format argument for every plain string without a placeholder;
- exactly two for `recent_stats_format` and `conflict_progress_format`;
- `formatArgs == [quantity]` for the three `%1$d` plurals;
- **no** format argument for the two unit-word plurals.

The return values reproduce today's stubs:

| Resource | Mapper-suite fake | ClickHandler-suite fake |
| --- | --- | --- |
| `recent_adhoc_label` | `Ad-hoc workout` | throws |
| `recent_stats_format` (a, b) | `a · b` | throws |
| `start_week_day_mon` … `_sun` | `Mo`, `Tu`, `We`, `Th`, `Fr`, `Sa`, `Su` | throws |
| `start_empty_sessions` | `No workouts yet` | throws |
| `start_empty_tags` | `No tagged exercises` | throws |
| `picker_empty` | `No templates yet` | throws |
| `start_never_run` | `never yet` | throws |
| `start_groups_footnote` | `days since the group's last workout` (ASCII apostrophe, as today) | throws |
| `conflict_progress_format` (a, b) | throws | `1 of 2 exercises done` for (1, 2) |
| `conflict_unnamed` | throws | throws (not reached) |
| `recent_exercises_count` (quantity q) | `q exercise` for q = 1, otherwise `q exercises` | throws |
| `recent_sets_count` | `q set` / `q sets` | throws |
| `start_week_sessions_count` | `workout` for q = 1, otherwise `workouts` | throws |
| `start_days_count` | `day` / `days` | throws |
| `start_days_idle_count` | `q day` / `q days` | throws |

**ResourceWrapper fakes (S1).** The mapper suite keeps its in-file object, reduced to the relative
time: `getAbbreviatedRelativeTime(timestamp, now)` answers the sentinel `rel:$timestamp@$now`, which
still pins the argument order; `getString`, `getQuantityString`, `formatMediumDate` and
`formatDayMonth` throw. ClickHandlerTest's wrapper throws on every member (no identity reaches it).
`CommonHandler` no longer takes one. The suites' `R` imports go.

**Mechanics only:**

- the mapper functions are `suspend`, so the 14 mapper identities become `= runTest { … }`; each
  keeps its name, inputs and assertions, and passes the resolver fake (and, for rows and picker, the
  wrapper fake);
- `DateUtils.MINUTE_IN_MILLIS` and `HOUR_IN_MILLIS` become `60_000L` and `3_600_000L` (named private
  constants are fine); `@ExtendWith(RobolectricExtension::class)` and `@Config` go;
- every `ClickHandler(…)` construction gains `stringResolver = …`; every `CommonHandler(…)` swaps
  `resourceWrapper` for `stringResolver`;
- S6's KDoc move in the three `@DisplayName` suites.

No other assertion changes. The inherited casts and `!!` after `kotlin.test` contract assertions
stay, together with their warnings (7.10 Section 19.7).

### 11.2 Android host: goldens

7.11 Section 11.2 applies. `HomeGoldenTest` moves to androidHostTest with exactly three changes: the
Android `stringResource` import becomes CMP's; the `R` import becomes the private `Res` and its
`feature_home_refresh_error` accessor; and `coldOpenError`'s
`stringResource(R.string.feature_home_refresh_error)` becomes
`stringResource(Res.string.feature_home_refresh_error)`. Its 21 method names, light and dark cases,
subjects, fixtures and 42 filenames stay exact. The PNGs move with `git mv`, `verifyPaparazziDebug`
executes all 42, and `recordPaparazziDebug` is forbidden.

This is the first golden test that reads its module's `Res`. The androidHostTest compilation is a
friend of commonMain, so the internal `Res` should be visible; if it is not, STOP (never replace the
read with a literal). The read renders the same EN text through Compose resources, so the four
`coldOpenError` / `screenColdOpenError` PNGs must stay byte-identical; any pixel change is a STOP.

Host runs: `testAndroidHostTest` executes 12 suites and 70 cases; `verifyPaparazziDebug` executes 13
suites and 112 cases (42 golden + 70) and prints
`Visual gate live: 42 golden test case(s) executed for 42 golden image(s).` The baseline's 13/71
and 14/113 (Section 12.2) differ by the mode-label identity, which leaves the host (S2).

### 11.3 The iOS mode-label test (S2)

`HomeStartCardModeLabelTest` moves to
`src/iosTest/kotlin/io/github/stslex/workeeper/feature/home/ui/components/HomeStartCardModeLabelTest.kt`
with its package, class, method, KDoc, comments and assertions. Only these change:

- kotlin.test's `@Test` and `assertTrue(actual, message)` replace JUnit's (same argument order);
- `@ExtendWith(RobolectricExtension::class)` and `@Config(sdk = [33])` go;
- `@OptIn(ExperimentalTestApi::class)` and `androidx.compose.ui.test.v2.runComposeUiTest` stay.

Its expected label is still computed in composition from `startCardModeName(DAYS_SINCE_LAST)`, so
it does not depend on the simulator's locale. It is the first non-scene iosTest file in a feature
module; it runs in the same `iosSimulatorArm64Test` invocation as the scene, and its tuple is
`io.github.stslex.workeeper.feature.home.ui.components.HomeStartCardModeLabelTest.theHeadNamesNoModeUntilOneIsKnown[iosSimulatorArm64]`.
If it cannot run on the simulator, STOP with the failure; it never moves back to a host with
Robolectric.

### 11.4 Android device

The module has no device test, and its dead device edges leave (Section 10). The device proof of the
factory flow is the app journeys: the whole `RouteReachabilityTest` class (`@Regression`), which
includes `homeIsTheColdStartDestination` and the journeys that return to Home, on the recorded
serial (Section 12.3) and in the canonical Regression suite (Section 12.4).

### 11.5 Deterministic iOS production scene

Exactly one class and method, no comma:

    class HomeFeatureSceneIosTest
    fun resourcesReadoutsListPickerAndActionsRenderAndDispatch()

The scene's setup:

- it uses `runComposeUiTest` and the production `HomeScreen(state, consume)` inside `AppTheme`, and
  for step 4 the production `TrainingPickerSheet`; it may switch what it composes through test-held
  state inside one `setContent`;
- a test-held `State` drives the screen; `consume` records actions, and the picker's callbacks are
  recorded as `select:<uuid>`, `startBlank` and `seeAll`;
- it uses the bounded settle stepping of all-exercises' scene (7.10 Section 19.7), settling until
  the next state's production tag or text exists;
- paged rows come from `PagingUiState { flowOf(PagingData.from(rows, sourceLoadStates = …)) }` and
  empty states from `PagingData.empty(sourceLoadStates = …)`, with every load state stated, as the
  golden test's `pagingState` helper does;
- every copy read outside composition goes through one `ComposeStringResolver()` instance
  (`resolver`); it declares no fake resolver;
- `ResourceWrapper` has no iOS implementation (S1), so the scene declares one private stub. It
  answers `getAbbreviatedRelativeTime` for exactly the three pairs below and throws on every other
  call and member. It is the scene's only test double.

Fixed values: `NOW = 1_200_000L`. The stub answers `(300_000, NOW)` → `15 min. ago`,
`(1_020_000, NOW)` → `3 min. ago`, and `(900_000, NOW)` → `5 min. ago`.

In one bounded scene it proves:

1. **Catalog:** through `resolver`, all 35 strings and both EN quantities (1 → one, 2 → other) of
   all five plurals resolve to the exact EN values of Section 3.4, each read as stored, with no
   format argument (45 values; for the `%1$d` plurals that is the verbatim template, for the
   unit-word plurals the word). Each placeholder-bearing value is also read filled:
   - `active_session_progress_format` with (2, 5) gives `2 of 5 done`;
   - `recent_stats_format` with `"a"`, `"b"` gives `a · b`;
   - `conflict_progress_format` with (1, 2) gives `1 of 2 exercises done`;
   - each `%1$d` plural with its count as the one format argument, at 1 and 2: `1 exercise`,
     `2 exercises`, `1 set`, `2 sets`, `1 day`, `2 days` (9 values in all).
2. **Copy through the production mapper** (`resolver`, and the stub where a row needs it):

   | Input | Output |
   | --- | --- |
   | R1 `Week(WeekReadoutDomain(3, {0, 2, 4}))` | `Week("3", "workouts", Mo✓ Tu We✓ Th Fr✓ Sa Su)` |
   | R2 `Week(WeekReadoutDomain(1, {6}))` | `Week("1", "workout", …Su✓)` |
   | R3 `DaysSince(4, "Leg Day", lastIsAdhoc = false, ANCHOR)` | `DaysSince("4", "days", "Leg Day · " + DateTimeUtil.formatMillis(ANCHOR))` |
   | R4 `DaysSince(1, "Unused", lastIsAdhoc = true, ANCHOR)` | `DaysSince("1", "day", "Ad-hoc workout · " + DateTimeUtil.formatMillis(ANCHOR))` |
   | R5 `TagIdle([Legs 10, Back 5, Chest 0])` | rows `(Legs, 1.0, "10")`, `(Back, 0.5, "5")`, `(Chest, 0.0, "0")`; footnote `days since the group’s last workout` |
   | R6 `Forgotten("tpl-1", "Push Day", 21, 6)` | `Forgotten("tpl-1", "Push Day", "21 days · 6 exercises")` |
   | R7 `Forgotten("tpl-2", "Pull Day", null, 1)` | metaLabel `never yet · 1 exercise` |
   | R8–R10 `NoSessions`, `NoTaggedHistory`, `NoTemplates` | `Empty` with rows 11, 12 and 27 of Section 3.4 |
   | S1 recent: ad-hoc, `startedAt 0`, `finishedAt 300_000`, 1 exercise, 2 sets | `Ad-hoc workout`, `15 min. ago`, `05:00`, `1 exercise · 2 sets` |
   | S2 recent: `Push Day`, `startedAt 120_000`, `finishedAt 1_020_000`, 3 exercises, 1 set | `Push Day`, `3 min. ago`, `15:00`, `3 exercises · 1 set` |
   | P picker: `tpl-1` `Push Day` 6 exercises last `900_000`; `tpl-2` `Pull Day` 1 exercise never | `(Push Day, 6 exercises, 5 min. ago)`, `(Pull Day, 1 exercise, null)` |
   | A1/A2 active: ad-hoc and `Push Day`, `startedAt 0`, done 2 of 5, `nowMillis 754_000` | `Ad-hoc workout` / `Push Day`, `12:34` |

   `ANCHOR` is any fixed timestamp; the expected date is computed with `DateTimeUtil.formatMillis`
   in the test, because the simulator's time zone decides it. Recent rows and the picker use
   `nowMillis = NOW`.
3. **The screen** (`HomeScreen`), from `State.init(…)` with S1 and S2 as the paged rows:
   - loading (`isActiveLoaded = false`): the title `Home` and the content descriptions `Charts` and
     `Settings`; no `HomeStartCard`, `ActiveSessionBanner` or `HomeList`;
   - the banner (`activeSession` = A2, `isActiveLoaded = true`, `nowMillis = 754_000`): `Push Day`,
     `•12:34` and `Session in progress · 2 of 5 done`; no `HomeStartCard`; the two rows with their
     names and meta lines `15 min. ago · 05:00 · 1 exercise · 2 sets` and
     `3 min. ago · 15:00 · 3 exercises · 1 set`;
   - the start card (`activeSession = null`), each with `Start`:
     - `WEEK` over R1: the head `WEEK`, `3`, `workouts`, and `Mo` … `Su`;
     - `DAYS_SINCE_LAST` over R3: `DAYS WITHOUT TRAINING`, `4`, `days` and R3's anchor;
     - `LAGGING_GROUPS` over R5: `LAGGING GROUPS`, the three names and counts, and the footnote;
     - `FORGOTTEN_TRAINING` over R6: `LONGEST NOT DONE` (home's own catalog), `Push Day`,
       `21 days · 6 exercises` and `ANOTHER TRAINING`;
     - `FORGOTTEN_TRAINING` over R10: `No templates yet`, and no `ANOTHER TRAINING`.

     The first three head labels are core:ui:start-mode's EN names, uppercased by `AppLabel`;
   - the empty region, with no rows: refresh settled → `HomeEmptyState` with `Ready when you are`
     and `Start a training to log your first session`; refresh failed → `HomeColdOpenError` with
     `Couldn’t load the list` and `Retry`.
4. **The training picker** (`TrainingPickerSheet`), over step 2's picker items: the title
   `Choose a training`, `Start blank workout`, `Add exercises as you go`, `Push Day` with
   `6 exercises · 5 min. ago`, `Pull Day` with `1 exercise`, and `See all templates`; then with no
   templates, `No templates yet`.
5. **Dispatch:** the screen's actions as one exact list, in this order:

   | Interaction | Action |
   | --- | --- |
   | `Charts` (`HomeChartsButton`) | `OnChartsClick` |
   | `Settings` (`HomeSettingsButton`) | `OnSettingsClick` |
   | the banner (`ActiveSessionBanner`) | `OnActiveSessionClick` |
   | the second row (`HomeRecentRow_s2`) | `OnRecentSessionClick("s2")` |
   | the head (`HomeStartModeHead`), WEEK card | `OnModeLabelClick` |
   | `Start` (`HomeStartButton`) | `OnStartActionClick` |
   | `ANOTHER TRAINING` (`HomeStartOtherTraining`), R6 card | `OnStartTrainingClick` |

   and the picker's callbacks as one exact list: `select:tpl-1` (the `Push Day` row), `startBlank`
   (`HomePickerStartBlankRow`), `seeAll` (`See all templates`).

It may use production test tags, texts, content descriptions, scrolling and deterministic fixtures.
It may not add a production test tag, a platform host, a repository, a database, a fake Store, a
fake `StringResolver`, a screenshot baseline or a second identity.

The module's Native oracle is 72 tuples: the 70 portable identities, the mode-label identity and
this scene.

### 11.6 What is not covered

- RU values execute in no test; the topology gate pins them statically (S5 records the gap for every
  shared entry).
- F1's hoisted code executes in no unit identity: no handler identity collects the active-session or
  picker flow, before or after this phase. The D3 check pins the shape; the device journeys that
  return to Home with a session in progress execute it.
- The mode sheet (`StartCardModeSheet`, core:ui:start-mode's own scene covers it), the conflict
  dialog (kit's `ActiveSessionConflictDialog`) and the graph's sheet and dialog wiring have no scene
  path; nor do the append tails, which `HomePagingTailKindTest` covers as a selector.
- The relative time runs only through stubs off-device.

testing.md records these (Section 9.2).

## 12. CI ownership and positive verification

### 12.0 D3 — the resolver-reference check (commit 1)

`FEATURE_STATE_LAMBDA_SIDE_EFFECTS` (`getString(`, `getPluralString(`, `stringResource(`,
`pluralStringResource(`, `resourceWrapper.`, `Res.`) sees a resource read written inside a State
lambda, but not a resolver or wrapper **passed** to a mapper there: `toUi(now, resourceWrapper)` and
`toUi(now, stringResolver)` both stay GREEN today, and F1's mechanical port would too.

Commit 1 changes only `.github/scripts/assert_kmp_ui_source_topology.py`. It adds one module
constant and one loop to `check_feature_entry`'s existing State-lambda section. No entry, contract,
`MODULES` or `FEATURE_ENTRIES` line changes.

Reference implementation. It is normative for the pattern, the comment stripping and the message;
formatting may differ.

~~~python
# after FEATURE_STATE_LAMBDA_SIDE_EFFECTS:
# D3 (kmp-phase-7-12-home-feature.md Section 12.0): a resolver or wrapper handed to a call inside a
# State lambda resolves copy there just the same, where no token above sees it. A direct member call
# (`name.`) stays the token list's to report, so every failure that list reports stays
# byte-identical; any other reference is this pattern's.
FEATURE_STATE_LAMBDA_RESOLVERS = re.compile(r"\b(stringResolver|resourceWrapper)\b(?!\.)")

    # check_feature_entry, inside `for body in braced_call_bodies(source, call):`, after the
    # existing side-effect loop:
                referenced = FEATURE_STATE_LAMBDA_RESOLVERS.findall(strip_kotlin_comments(body))
                for name in sorted(set(referenced)):
                    failures.append(
                        f"{module}: {call} in {path.relative_to(root)} references {name!r} "
                        "inside the State lambda; resolve copy before it"
                    )
~~~

What it reports: a bare reference (`toUi(now, stringResolver)`), a named argument, a function
reference (`stringResolver::getString`), and a member call split across lines
(`resourceWrapper\n.formatDayMonth(…)`, which the token list misses). What it leaves to the token
list: `stringResolver.` and `resourceWrapper.` member calls. Comments inside the lambda and longer
identifiers (`stringResolverReady`, `myResourceWrapper`) do not match. Every shared entry's handlers
already map before their lambdas (all-exercises' blocked dialog and past-session's `phase`), so
today's tree stays GREEN.

**Proof that commit 1 changes no existing behaviour.** It runs on the committed tree, with the
base's script saved aside as `old_gate.py`, under every Python the host has:

1. GREEN: old and new outputs on the unmodified tree are byte-identical, exit code and stderr
   included, and equal to BASE_GATE_OUTPUT.
2. Differential: under each M mutation, old and new outputs are byte-identical and RED with exactly
   the named lines. New-only: under each N mutation, the old script is GREEN (byte-equal to
   BASE_GATE_OUTPUT) and the new one is RED with exactly the named lines. Guards: under each G
   mutation, both are GREEN. Each mutation is one exact occurrence, scored by both scripts, and
   restored byte-exactly (sha256 and mode) before the next; a fresh GREEN follows the last.

   | # | Mutation | Result |
   | --- | --- | --- |
   | M1 | past-session `CommonHandler.kt`: a first line `val probe = resourceWrapper.formatMediumDate(0L)` inside `updateStateImmediate { current -> current.copy(phase = phase, hasResolved = true)…` | both RED, identical: `…CommonHandler.kt resolves past-session copy through ResourceWrapper.formatMediumDate; use the private Compose resources` and `…updateStateImmediate in …CommonHandler.kt resolves a resource inside the State lambda: 'resourceWrapper.'` |
   | M2 | all-exercises `ClickHandler.kt`: `blockedArchiveDialog = dialog,` → `blockedArchiveDialog = dialog.copy(archivedSummary = stringResolver.getString(Res.string.feature_all_exercises_permanent_delete_success)),` | both RED, identical: the `'getString('` and `'Res.'` State-lambda lines, and `…ClickHandler.kt copy contract requires 'stringResolver.getString(' exactly once; found 2` |
   | M3 | archive `ArchivePagingHandler.kt`: `exerciseSegmentLabel = label,` → `exerciseSegmentLabel = resourceWrapper.formatDayMonth(0L),` | both RED, identical: `…updateState in …ArchivePagingHandler.kt resolves a resource inside the State lambda: 'resourceWrapper.'` and `feature:archive: ResourceWrapper must serve exactly one formatDayMonth date` |
   | N1 | all-exercises `ClickHandler.kt`: the hoisted `val dialog = with(AllExercisesUiMapper) { result.toBlockedArchiveDialog(stringResolver) }` moves into the lambda as `blockedArchiveDialog = with(AllExercisesUiMapper) { … }` | new only: `feature:all-exercises: updateStateImmediate in src/commonMain/kotlin/io/github/stslex/workeeper/feature/all_exercises/mvi/handler/ClickHandler.kt references 'stringResolver' inside the State lambda; resolve copy before it` |
   | N2 | past-session `CommonHandler.kt`: the `val phase = result?.let { … toUi(resourceWrapper = resourceWrapper, stringResolver = stringResolver, …) … }` block moves into the `updateStateImmediate { current -> … }` lambda | new only, two lines: `… references 'resourceWrapper' …` and `… references 'stringResolver' …` (that file, `updateStateImmediate`) |
   | N3 | past-session `CommonHandler.kt`: `current.copy(phase = phase, hasResolved = true)` → `current.copy(phase = phase, hasResolved = stringResolver::getString != null)` | new only: `… references 'stringResolver' …` |
   | N4 | past-session `CommonHandler.kt`: a first lambda line `val probe = resourceWrapper` + newline + `.formatDayMonth(0L)` | new only: `… references 'resourceWrapper' …` (the token list and the date regex both miss it) |
   | G1 | past-session `CommonHandler.kt`: a first lambda line `// stringResolver and resourceWrapper stay outside this lambda.` | both GREEN |
   | G2 | past-session `CommonHandler.kt`: a first lambda line `val stringResolverReady = myResourceWrapper` | both GREEN |

3. The diff of commit 1 touches only the topology script, and in it only the new constant and the
   new loop.

A differing byte in step 1 or step 2's M rows, an N row that is not RED with exactly its line, or a
G row that is not GREEN is a STOP.

### 12.1 Topology gate data (normative)

`MODULES["feature:home"]` holds the 101 files under `src` of Section 9.1. It follows the
past-session entry and uses its format:

- `kotlin_source_sets`: `commonMain`, `commonTest`, `androidHostTest`, `iosTest`;
- `resource_dirs`: `src/commonMain/composeResources/values` and
  `src/commonMain/composeResources/values-ru`.

`EXPECTED_APP_DEPS_READERS` loses the home entry. The script compares it exactly with every
`appDeps<…Factory>(` reader in the tree.

`HOME_ENTRY` is appended to `FEATURE_ENTRIES` after `PAST_SESSION_ENTRY`:

| Field | Value |
| --- | --- |
| module / short_name | `feature:home` / `home` |
| root / package_dir | `feature/home` / `io/github/stslex/workeeper/feature/home` |
| build_fragments | every plugin, `packageOfResClass`, `includeJavax()`, dependency and `apply(from = …)` line of Section 10, each exactly once (24), except `implementation(kotlin("test"))`, which kotlin_test_count covers |
| kotlin_test_count | 2 |
| catalog | the 40 rows of Section 3.4, in file order, with the exit values (decoded text; `’` and `—` as the characters) |
| resource_prefix | `feature_home_` |
| payload_files | `HomeStore.kt`, `BottomSheetState.kt`, `PickerTrainingItem.kt`, `RecentSessionItem.kt`, `StartCardBodyUi.kt` (plus everything under domain/). The mapper and the three copy-reading handlers are deliberately absent |
| store_file / state_fields | `mvi/store/HomeStore.kt` / `activeSession: ActiveSessionInfo?`, `startCardMode: StartCardModeUi?`, `startCardBody: StartCardBodyUi?`, `pagingUiState: PagingUiState<PagingData<RecentSessionItem>>`, `nowMillis: Long`, `isActiveLoaded: Boolean`, `bottomSheet: BottomSheetState`, `pendingConflict: ConflictInfo?` |
| resource_wrapper_readers | `mvi/mapper/HomeUiMapper.kt`, `mvi/handler/ClickHandler.kt`, `mvi/handler/PagingHandler.kt` |
| resource_wrapper_dates | `{"mvi/mapper/HomeUiMapper.kt": {"getAbbreviatedRelativeTime": 2}}` |
| preview_subjects | `{"ui/components/RecentSessionRow.kt": "RecentSessionRow"}` |
| preview_inventory | the 11 arguments of Section 5.4, per file, in source order |
| suppressions | the three production annotations of Section 0.2 under their `src/commonMain/…` paths, each `['"…"']`; nothing under commonTest |
| test_names | the 70 of Section 3.2 under their commonTest paths; `theHeadNamesNoModeUntilOneIsKnown` under its iosTest path; the scene name under its iosTest path |
| golden_test / golden_methods | `src/androidHostTest/…/golden/HomeGoldenTest.kt` / the 21 of Section 3.3 |
| device_test / device_fragments | `""` / `()` |
| factory names | Section 0.3; create_args left at `""`; feature_file `di/HomeFeature.kt`, graph_file `ui/HomeGraph.kt` |
| identity_test / identity_fragments | `app/app/src/test/…/di/HomeExtensionIdentityTest.kt` / the `home()` helper of Section 6, on one line |
| identity_names / identity_accessor | the two names of Section 3.6 / `(?<!AppGraph)\.home\(\)` |
| extra_root_fragments | none |
| extra_checks | `home_copy_contract`, `home_preview_contract`, `home_dependency_contract` below |

`home_copy_contract` checks Section 5.3. Every count is over sources with Kotlin comments stripped,
and it reuses past-session's helpers' shape (`source_of`, `require`, `require_parameters`):

1. In `mvi/store/HomeStore.kt`, the parameters of `HapticClick` are exactly
   `["type: HapticFeedbackType"]`, and those of `ConflictInfo` exactly
   `["activeSessionUuid: String", "requestedTrainingUuid: String", "activeSessionName: String",
   "progressLabel: String"]`. `RecentSessionItem`'s are exactly `["sessionUuid: String",
   "trainingName: String", "isAdhoc: Boolean", "finishedAtRelativeLabel: String",
   "durationLabel: String", "statsLabel: String"]`, and `PickerTrainingItem`'s exactly
   `["trainingUuid: String", "name: String", "exerciseCountLabel: String",
   "lastSessionRelativeLabel: String?"]`, in their `mvi/model/` files.
2. `mvi/mapper/HomeUiMapper.kt` holds each of these exactly once:
   `suspend fun StartCardReadoutDomain.toUi(stringResolver: StringResolver)`,
   `suspend fun WeekReadoutDomain.toUi(stringResolver: StringResolver)`,
   `suspend fun ActiveSessionWithStatsDomain.toUi(`, `suspend fun RecentSessionDomain.toRecentItem(`
   and `suspend fun List<TrainingListItemDomain>.toPickerItems(`; and exactly twelve
   `stringResolver.getString(` and seven `stringResolver.getPluralString(`.
3. `mvi/handler/ClickHandler.kt` holds exactly once each of `private val resourceWrapper:
   ResourceWrapper,`, `private val stringResolver: StringResolver,` and
   `toPickerItems(now, resourceWrapper, stringResolver)`, exactly two `stringResolver.getString(`,
   and no `getPluralString(`.
4. `mvi/handler/CommonHandler.kt` holds exactly once each of `private val stringResolver:
   StringResolver,`, `toUi(now, stringResolver)` and `readout.toUi(stringResolver)`, and no
   `getString(` or `getPluralString(`. (It names no `ResourceWrapper`: it is no declared reader.)
5. `mvi/handler/PagingHandler.kt` holds exactly once each of `private val resourceWrapper:
   ResourceWrapper,`, `private val stringResolver: StringResolver,` and
   `toRecentItem(now, resourceWrapper, stringResolver)`, and no `getString(` or `getPluralString(`.
6. 7.11's rules 4–7: no commonMain import of CMP's top-level `getString` / `getPluralString`; under
   `mvi/`, every `getString(` / `getPluralString(` is a `stringResolver.` call and no file holds
   `stringResource(` / `pluralStringResource(`; no `ui/` file holds `getString(` /
   `getPluralString(`; both walks find at least one file.

The message shapes are past-session's: `<file> copy contract requires <fragment> exactly <n>;
found <m>`, `<file> <name> parameters must be exactly [...]; actual=[...]`, and so on.

`home_preview_contract` counts bare `@Preview` annotations (`@Preview\b(?!\s*\()` over
comment-stripped source) in every commonMain file: exactly 4 in `ui/HomeScreen.kt`, exactly 2 in
`ui/components/ActiveSessionBanner.kt`, and 0 everywhere else, with the message
`feature:home: <file> must hold exactly <n> bare @Preview annotation(s); found <m>`.

`home_dependency_contract` fails if the build file contains `:core:data:database` anywhere, with the
message `feature:home: build must not depend on :core:data:database in any source set`.

The topology script's change after commit 1 is additive:

- `MODULES`, `EXPECTED_APP_DEPS_READERS`, `HOME_ENTRY`, its three checks and the `FEATURE_ENTRIES`
  tuple;
- the only removed lines are the reader entry and the old `FEATURE_ENTRIES` line;
- `check_feature_entry` (as commit 1 left it), the four existing entries and their checks stay
  byte-identical.

`assert_kmp_ios_smoke.py` gains a `feature:home` entry, after past-session's, with the 72 tuples of
Section 11.5: the 70 under their suites' classnames (Section 3.2's packages), the mode-label tuple,
and the scene's (`io.github.stslex.workeeper.feature.home.HomeFeatureSceneIosTest`).

`android_build_unified.yml`, KMP iOS kit smoke job:

- add `:feature:home:iosSimulatorArm64Test` to the simulator test command and
  `:feature:home:linkDebugTestIosArm64` to the device-link command, after past-session;
- add the directory `feature/home` to the binary-assert loop (`/11 linked` → `/12 linked`) and
  `feature/home/build/test-results/iosSimulatorArm64Test/` to the upload list;
- the job-header comment names the home feature among the features, rewrapped to stay within 100
  columns ("…, all-exercises, past-session and home" / "features' Phase-7 Native tests on the iOS
  simulator, then a checked-in script requires every" / "exact per-module XML identity."), and the
  two "eleven modules" comments say twelve.

The job, step and context names stay. The build job's comment "all 13 golden-holding modules" stays
true.

The twelve modules, for every command below: `:core:ui:kit`, `:core:ui:navigation`, `:core:ui:mvi`,
`:core:ui:start-mode`, `:core:ui:plan-editor`, `:feature:image-viewer`, `:feature:plan-editor`,
`:feature:archive`, `:feature:all-trainings`, `:feature:all-exercises`, `:feature:past-session`,
`:feature:home`.

The prototype run of this data (Section 13) printed, on the simulated exit tree, 556 lines with
exit 0 under four Pythons, byte-identical: the base's 451, plus home's `MODULES` block (102 lines),
`home catalogs, semantic State, suppressions, previews, test identities, and explicit factory flow
are exact`, `app:common API edges and 6 remaining Context.appDeps readers are exact`, and the two
home catalogs among `20 commonMain catalogs walked` (EN 12 placeholders, RU 18). That run is not
evidence.

### 12.2 Entry gate before any edit

7.11 Section 12.2 applies, plus:

- `894f2ec6b` (Phase 7.11's merge) is an ancestor of the base;
- `git diff 894f2ec6b..<base> -- feature/home` is empty;
- every commit since `894f2ec6b` that touches a Section 9.2 path is listed, with whether it
  conflicts;
- the census of Sections 3.1–3.7 is reproduced on the base;
- the current eleven-module Native command and oracle are green.

Baseline commands, with the stable flags:

    ./gradlew :feature:home:assembleDebug
    ./gradlew :feature:home:testDebugUnitTest      # 13 suites, 71 cases; goldens excluded
    ./gradlew :feature:home:verifyPaparazziDebug   # 14 suites, 113 cases: 42 golden + 71
    ./gradlew :app:app:testDebugUnitTest --tests '*HomeExtensionIdentityTest*' --tests '*StringResolverIdentityTest*'   # 4 cases

### 12.3 Focused gates after the implementation

After commit 1: Section 12.0's proof.

After commit 2 (still `src/main`), on the committed tree:

- `:feature:home:testDebugUnitTest` (13 suites, 71 cases, names exactly Section 3.2 plus the
  mode-label identity) and `:feature:home:verifyPaparazziDebug` (14 suites, 113 cases, and
  `Visual gate live: 42 golden test case(s) executed for 42 golden image(s).`);
- the two `HomeExtensionIdentityTest` identities, which build the real graph with D2's bindings;
- detekt alone;
- the topology script, byte-identical to its commit-1 output.

After commit 3, on the committed tree:

    python3 .github/scripts/assert_kmp_ui_source_topology.py
    ./gradlew :feature:home:assembleDebug            # Android and both iOS klibs
    ./gradlew :feature:home:testAndroidHostTest      # 12 suites, 70 cases
    ./gradlew :feature:home:verifyPaparazziDebug     # 42 golden + 70
    ./gradlew :feature:home:iosSimulatorArm64Test    # 72 tuples
    ./gradlew :app:common:assembleDebug

Then:

- `./gradlew detekt --continue` alone, on every commit's tree;
- the two `HomeExtensionIdentityTest` identities and the two `StringResolverIdentityTest`
  identities (fresh XML);
- on an explicit API-34 serial with animations disabled, as `ui_tests.yml` does: the whole
  `RouteReachabilityTest` class (fresh XML);
- the relative PNG proof of Section 3.7.

Run `:feature:home:iosSimulatorArm64Test` as soon as production and commonTest compile for Native,
before the scene, the mode-label test and the app paths. That run is the first Native measurement of
the fakes without a `core:data:database` edge (Section 3.5) and of `@Preview(device = …)` in
commonMain (S3), and it must give the 70 identities by name.

After commit 4: the twelve-module Native command (`--continue`) and the oracle, the twelve-module
device link (12/12), the topology script, and detekt alone.

### 12.4 Repository and visual gates

7.11 Section 12.4 applies unchanged: assemble, lint and unit; detekt alone;
`assembleDebugAndroidTest`; `verifyPaparazziDebug`; `:lint-rules:test`; the personal-data gate; the
canonical Smoke and Regression device suites; diff hygiene; the PNG manifest check of Section 3.7;
the mockup shell gate, where runnable.

### 12.5 Final diff and remote proof

7.11 Section 12.5 applies, with: the Section 9 boundary; 72 Native tuples; 42 unchanged golden
blobs (40 distinct); the three production suppressions. Record the KMP iOS kit smoke job's duration
on the final head against its 120-minute timeout.

## 13. Mandatory known-negative controls

7.11 Section 13's procedure applies: every control runs fresh GREEN → one named observable RED →
automatic exact restoration → fresh GREEN; Gradle- and device-scored controls through
`documentation/mockups/mutation_harness.py` in one-shot mode, with `--no-configuration-cache
--console=plain` inside `--task`; Python-scored controls against a byte-restoring copy in a detached
worktree under `.claude/worktrees/`; a compile-invalid mutation is never a RED where a compiled test
is the detector; no mutation is committed. Every control runs after commit 4, on its committed tree.

**From 7.11, with the substitutions of Section 0.3:**

| # | Mutation | Scorer: expected RED |
| ---: | --- | --- |
| 1 | delete `ui/components/PagingTails.kt` | topology: source manifest mismatch, `missing=[…PagingTails.kt]` |
| 2 | copy `di/HomeScope.kt` into `src/main/kotlin/…/di/` | topology: legacy source set `src/main`, manifest `extra=[…]`, unexpected Kotlin-bearing source set `src/main` |
| 3 | move `ui/components/PagingTails.kt` to `ui/PagingTails.kt` | topology: manifest `missing` and `extra` |
| 4 | `import android.util.Log` in `ui/components/PagingTails.kt` | topology: platform API in commonMain, `'import android.'` |
| 5 | rename the EN key `feature_home_paging_retry` to `…_renamed` | topology: EN catalog mismatch (`order=drifted`), two ownership-drift lines |
| 6 | append a word to the RU value of `feature_home_paging_retry` | topology: RU catalog mismatch, `order=exact` |
| 7 | move the EN `feature_home_paging_retry` into `feature/settings/src/main/res/values/strings.xml` | topology: resource ownership drift, and the EN catalog mismatch |
| 8 | `val titleRes: Int = 0` as State's last field | topology: Store State must stay semantic |
| 9 | `toRecentItem`'s `adhoc_label` read → `resourceWrapper.getString(0)` | topology: `resolves home copy through ResourceWrapper.getString`; copy contract `'stringResolver.getString(' exactly twelve times; found 11`; the `mvi/` rule `12 time(s), 11 through stringResolver` |
| 10 | `val label = stringResolver.getString(Res.string.feature_home_title)` as the first line of `CommonHandler`'s active-session `updateStateImmediate { current -> … }` | topology: `resolves a resource inside the State lambda: 'getString('` and `… 'Res.'`; copy contract `'getString(' exactly zero times; found 1` on `CommonHandler` |
| 11 | `uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES` added to `HomeStartCard.kt`'s `@Preview(name = "Week — Dark")` | topology: forbidden `'android.content'` and `'uiMode'`; preview inventory mismatch |
| 12 | `LocalContext.current` and `context.appDeps<HomeGraph.Factory>()` in `HomeFeature` | topology: readers not the exact 6, forbidden `LocalContext` and `appDeps<`, factory invocation not inside retained creation |
| 13 | delete `val homeGraphFactory: HomeGraph.Factory` and its KDoc from `AppRootDeps` | topology: exact root-factory flow requires the accessor |
| 14 | `val graph = factory.createHomeGraph()` before `rememberMetroStoreProcessor` | topology: `createHomeGraph() must be invoked once, inside the Store lambda` |
| 15 | a second `(context.applicationContext as AppRootDepsHolder).appRootDeps()` inside the admitted `remember(currentPhase.id) { … }` in `App.kt` | device: `UiAdmissionRaceTest`, as in 7.10 |
| 16 | hoist `val deps = remember(currentPhase.id) { …appRootDeps() }` above `if (admission.granted) {` | device: `UiAdmissionRaceTest`, as in 7.10 |
| 17 | identity helper `= asContribution<HomeGraph.Factory>().createHomeGraph()` | topology: `must not bypass AppRootDeps via asContribution`, and the identity-helper fragment |
| 18 | archive-specific; no counterpart | — |
| 19 | `HomeUiMapper`'s `if (maxIdle > 0)` → `if (maxIdle >= 0)` | host and Native: `tag-idle readout with all groups trained today draws empty bars not NaN` (NaN against 0f), and only it |
| 20 | `META_SEPARATOR = " · "` → `" - "` | host and Native: RecentMetaLineTest `order` and `emptyTokensAreDropped`; Native: the scene's step 3 meta lines |
| 21 | the scene's `setContent { … }` body → `setContent { }` | Native: the scene, at its first node assertion |
| 22 | production `HomeScreen.kt`: the settings button's `onClick = { consume(Action.Click.OnSettingsClick) }` → `onClick = { }` | Native: the scene's dispatch list |
| 23 | rename the scene method to `renamedSceneSubstitute` | Gradle GREEN, then the oracle: `feature:home: expected exactly one testcase … found 0` |
| 24 | flip (XOR 0xFF) the last byte of `…golden_HomeGoldenTest_activeSessionBanner_light.png` | PNG assertion: blob drift, blob-set drift, the all-PNG and Paparazzi manifest drift |
| 25 | a compile-valid overlong assertion line in `commonTest/…/mvi/handler/NavigationHandlerTest.kt` | root detekt, alone: findings on that line only |

**New in 7.12:**

| # | Mutation | Scorer: expected RED |
| ---: | --- | --- |
| 26 | `CommonHandler`: the hoisted `val activeSession = row?.toUi(now, stringResolver)` moves back into its lambda as `activeSession = row?.toUi(now, stringResolver),` | topology (D3): `updateStateImmediate in …/mvi/handler/CommonHandler.kt references 'stringResolver' inside the State lambda; resolve copy before it` |
| 27 | `ClickHandler`: the hoisted `val templates = …toPickerItems(…)` moves back into its lambda as `templates = trainings.toPickerItems(now, resourceWrapper, stringResolver),` | topology (D3): two lines, `… references 'resourceWrapper' …` and `… references 'stringResolver' …` |
| 28 | the mapper's recent relative time → `finishedAtRelativeLabel = finishedAt.toString(),` | topology: `…HomeUiMapper.kt must read the ResourceWrapper.getAbbreviatedRelativeTime date exactly 2 time(s); found 1` |
| 29 | `resourceWrapper.getAbbreviatedRelativeTime(0L, 0L)` added to `CommonHandler.processInit` | topology: `…CommonHandler.kt resolves home copy through ResourceWrapper.getAbbreviatedRelativeTime; use the private Compose resources` |
| 30 | delete the bare `@Preview` above `HomeScreenLoadingPreview` | topology: `…ui/HomeScreen.kt must hold exactly 4 bare @Preview annotation(s); found 3` |
| 31 | a bare `@Preview` on `RecentSessionRowPreview(themeMode: ThemeMode)` | topology: `…RecentSessionRow.kt must hold exactly 0 bare @Preview annotation(s); found 1` |
| 32 | `@Preview(device = "id:pixel_9")` → `@Preview` | topology: `exactly thirteen portable previews are required; found 12`, `ActiveSessionBanner.kt preview inventory must be exactly ['device = "id:pixel_9"']; actual=[]`, and `…ActiveSessionBanner.kt must hold exactly 2 bare @Preview annotation(s); found 3` |
| 33 | `RecentSessionRow.kt`: `@Preview(name = "Dark", showBackground = true)` → `@Preview(name = "Dark")` | topology: `RecentSessionRow.kt preview contract requires '@Preview(name = "Dark", showBackground = true)' exactly once` |
| 34 | EN `recent_sets_count` one item `%1$d set` → `%d set` | placeholder rule: key `feature_home_recent_sets_count[one]` token `'%d'`; EN catalog mismatch |
| 35 | add `implementation(project(":core:data:database"))` to commonTest's dependencies | topology: `feature:home: build must not depend on :core:data:database in any source set` |
| 36 | a 71st `@Test`, `` `a seventy-first identity nobody declared` ``, in `NavigationHandlerTest` | topology: test identities drifted, `unexpected=['a seventy-first identity nobody declared']` |
| 37 | `@DisplayName("rows win over everything, including an unsettled refresh")` between `@Test` and `fun rowsWin()` | topology: test identities drifted in `…HomeListSurfaceTest.kt`, `missing=['rowsWin']` (S6's reader) |
| 38 | rename `theHeadNamesNoModeUntilOneIsKnown` to `theHeadNamesNoMode` | topology: test identities drifted in the iosTest file, `missing=[…]`, `unexpected=['theHeadNamesNoMode']` |
| 39 | in `HomeExtensionIdentityTest`, the second identity's `appGraph.home()` → `appGraph.homeGraphFactory.createHomeGraph()` | topology: `both extension identities must reach the graph through the accessor` |
| 40 | `AppNavigationHost`: delete `factory = homeGraphFactory,` from the `homeGraph(` call | topology: `exact root-factory flow requires 'homeGraph(factory = homeGraphFactory,' once in …AppNavigationHost.kt` |
| 41 | `ClickHandler`'s `conflict_unnamed` read → the literal `"Workout"` | topology: copy contract `'stringResolver.getString(' exactly two times; found 1`. Record also that host, Native and the scene stay GREEN (no identity reaches that read); that is why the count exists |
| 42 | `private val resourceWrapper: ResourceWrapper,` added to `CommonHandler`'s constructor | topology: `…CommonHandler.kt must not read ResourceWrapper` |
| 43 | the argument rule: the mapper's `recent_sets_count` read in `toRecentItem` drops its format argument | host and Native: the two recent-mapper identities throw at the strict fake's argument check; Native: the scene's step 2 S1/S2 show `%1$d sets` / `%1$d set` |
| 44 | the unit-word rule: the mapper's `start_days_count` read gains the format argument `daysSince` | host and Native: the two days-since identities throw at the strict fake's argument check. Record also that the scene stays GREEN (the real resolver ignores an argument for a template without placeholder); that is why the strict fake checks it |
| 45 | D2: `HomeInteractorImpl.observeStartCardReadout` passes `nowMillis + 1` | host and Native: `observeStartCardReadout delegates to the use case with mode and now`, and only it |
| 46 | S2: production `HomeStartCard.kt` `.widthIn(min = if (label == null) AppDimension.heightMd else 0.dp)` → `.widthIn(min = 0.dp)` | Native: `theHeadNamesNoModeUntilOneIsKnown` (`label-less head is … wide, under 48.0.dp`). Record also that the host stays GREEN: no host test reaches the head |
| 47 | EN `feature_home_refresh_error` `Couldn’t load the list` → `Couldn’t load the list.` | `verifyPaparazziDebug`: `coldOpenError` and `screenColdOpenError`, both themes (pixel diffs); topology: EN catalog mismatch; Native: the scene's steps 1 and 3 |

Each Python-scored control (1–14, 17, 26–42 and 47's topology half, and M1–M3, N1–N4 and G1–G2 of
Section 12.0) was dry-run once against the simulated exit tree while this document was written, and
each produced exactly its named result, without a traceback. That dry run is not evidence. The
implementation's fresh runs are.

Scorers:

- **Python, the topology script as CI runs it:** 1–14, 17, 26–42, and 47's topology half;
- **PNG:** 24;
- **Gradle:**
  - `:feature:home:testAndroidHostTest :feature:home:iosSimulatorArm64Test --continue` for 19–22,
    43–46, 47's Native half and the GREEN observations of 41, 44 and 46;
  - `:feature:home:verifyPaparazziDebug` for 47's golden half;
  - the twelve-module Native command and the oracle for 23;
  - root detekt, alone, for 25;
- **device:** 15 and 16 (`UiAdmissionRaceTest` on the recorded serial, fresh XML).

## 14. STOP conditions

7.11 Section 14 applies, read with Section 0.3. In addition, stop if:

- `894f2ec6b` is not an ancestor of the base, or the target or any Section 9.2 path drifted from the
  discovery baseline in a way this document does not describe;
- commit 1's proof (Section 12.0) shows any differing byte, or an N or G row deviates;
- D2 or F1 needs anything beyond Sections 5.2 and 5.5: another production file, a changed
  identity, stub or assertion in commit 2, or a `HomeGraph` change beyond the two bindings;
- the copy port needs any of these: a resource handle, `ResourceWrapper` or `StringResolver` in a
  payload file; resource resolution or a resolver reference inside a State lambda; a change to an
  Event, the State, `RecentSessionItem`, `PickerTrainingItem` or `StartCardBodyUi`;
- any of the 15 resolver-dependent identities needs an assertion change, or a strict fake cannot
  reproduce today's stubs;
- the Native scene's EN catalog differs from Section 3.4 in any value, or step 2's outputs differ
  from its table;
- Native or Android compilation of commonTest needs `core:data:database` (Section 3.5);
- commonMain rejects `@Preview(device = "id:pixel_9")` (S3);
- the androidHostTest golden test cannot read the module's internal `Res` (Section 11.2), or any of
  the 42 PNG blobs changes;
- the mode-label test cannot run on the simulator (S2);
- an icon does not resolve from `libs.cmp.material.icons.extended` on Native;
- the KMP iOS kit smoke job times out on any PR head;
- detekt reports anything in the moved tests that PR-L does not cover. The fix is never a
  suppression;
- `RouteReachabilityTest` fails on the recorded serial.

A STOP produces a measured note and, where needed, an amendment proposal. It never widens the
implementation silently.

## 15. Signed, bisect-green commit plan

Five English Conventional Commits (S4). Each is signed, GitHub-verified and green on its own tree:

1. **ci(kmp): fail resolver references inside State lambdas** — D3 only (Section 12.0): the constant
   and the loop, and nothing else.
2. **refactor(home): seam the readout use cases and hoist copy out of State lambdas** — in
   `src/main`, before any move: D2 (Section 5.5: the two interfaces, the two `…Impl` files, the two
   bindings, and the two use-case suites constructing the `…Impl` classes) and F1 (Section 5.2, with
   `resourceWrapper` and `System.currentTimeMillis()` in place). Nothing else changes.
3. **refactor(kmp): share home feature entry** — the module (Sections 3–11) and the six app paths.
   It also carries the minimal topology delta that keeps this tree green (precedent 7.8 G1): the
   `MODULES` entry, and the home reader removed from `EXPECTED_APP_DEPS_READERS`.
4. **ci(kmp): gate shared home feature** — `HOME_ENTRY` with its three checks, the Native oracle
   entry, and the workflow lists and comments.
5. **docs(kmp): record Phase 7.12 evidence** — this document's Status line, which becomes
   `**Status:** COMPLETE — implemented by the PR whose merge brought this line to dev.`; Section 19;
   and the documentation edits of Section 9.2. Sections 0–18 stay byte-identical.

Each commit is made first. Its gates then run on the committed tree, and so do the controls (after
commit 4) and the repository gates. A fix is a new signed commit inside Section 9, and every gate it
can reach is re-run. Nothing is amended, squashed or reordered.

## 16. Exit criteria

7.11 Section 16 applies, with:

- commit 1's proof, and commit 2's green classic module;
- the 102-path topology and two private catalogs;
- 40 identifiers, changed only by P1's 18 values;
- copy outside composition only through `StringResolver`, `ResourceWrapper` only for the two
  relative times (the copy contract and the allowance), and no resolver reference in any shared
  entry's State lambda (D3);
- D2's seam with no identity changed;
- 70 identities on host and Native, the mode-label identity on Native, and 72 Native tuples;
- 42 golden cases with unchanged blobs and the relative PNG manifest proof;
- the three production suppressions and no other;
- the explicit shape-A factory flow, with both identities through the accessor;
- no `core:data:database` edge;
- the topology entry, oracle and workflow at twelve modules;
- readers 7 → 6;
- every control of Section 13 evidenced;
- the Section 9.2 documents updated.

## 17. Remaining ordered roadmap

After 7.12 the frontier is remeasured. By today's scan the next entries are exercise-chart
(`java.time` → kotlinx-datetime; tests on the Android-only `database-test`), single-training (a
database edge, four State-lambda mappings that D3 reports once it is an entry, and the activity
`BackHandler`), exercise (pickers and permissions; four use cases, for which D2 is the template),
live-workout (the largest; five State-lambda sites), and settings, which needs
`feature:app-dialogs:api` in KMP first and so comes after the app-dialog boundary. Then come
app/common and the permanent iOS host. A throwaway iosApp remains forbidden.

Separate follow-ups (S5 records them in tech-debt.md): runtime RU catalog coverage, a typed
`rememberMetroStoreProcessor`, a portable date formatter, a non-suspend `updateStateImmediate`
lambda, the locale-list remedy (a maintainer decision), and `core:data:exercise`'s leaky
`PlanSetDataModel`.

## 18. Authorization

The maintainer's GO of 2026-10-17 locked D1–D3. Starting the implementation with this document
accepts S1–S6 and authorizes two things:

- landing this document on dev;
- implementing it as one PR into dev, inside Section 9.

Merge stays the maintainer's.
