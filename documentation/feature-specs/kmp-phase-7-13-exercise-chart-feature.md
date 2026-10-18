# KMP Phase 7.13 — feature:exercise-chart becomes a shared feature entry

**Status:** SPECIFIED — authorized for implementation by the maintainer's GO of 2026-10-18 (Section 18)

**Discovery baseline:** dev @ 5de43879aefe80cd584973e291b93581880ed6dc (2026-10-18, merge of #338,
Phase 7.12). Its parents are `6f72bc371` (the 7.12 spec commit) and `57e88db23` (the 7.12 PR head).
`feature/exercise-chart` last changed in `b0045adfa` (2026-10-08, PR-N's Native-legal test names);
no commit on dev follows #338.

**Template:** [Phase 7.12](kmp-phase-7-12-home-feature.md). This document is a delta. Every rule of
7.12 that this document does not change applies unchanged, read with the substitutions of Section
0.3. Through 7.12, the rules of 7.11, 7.10, 7.9, 7.8 and 7.7 that it inherits apply too.
Exercise-chart has a route argument, so [Phase 7.11](kmp-phase-7-11-past-session-feature.md)
Section 6 is the precedent for its factory flow (shape B), not 7.12's shape A.

**Measured by:** a static discovery of 2026-10-18 on git data, greps and scripts, with nothing
compiled or run. Its gate change and gate data were prototyped against a simulated exit tree
(Sections 12.0, 12.1 and 13). That prototype is not evidence; the implementation's fresh runs are.

---

## 0. Authority, decisions, substitutions

### 0.1 Authority order

1. live origin/dev at the implementation base;
2. AGENTS.md, CLAUDE.md, documentation/architecture.md, testing.md, ci-cd.md,
   compose-state-discipline.md;
3. the Phase 7.12 specification, and through it 7.11 (its Section 6 for shape B), 7.10, 7.9, 7.8
   (including its Section 19 decisions P1, P1a and G1–G4) and 7.7;
4. kmp-string-resolver.md (PR-R) as merged;
5. the checked-in source, resources, tests, PNGs and CI oracles at the implementation base.

### 0.2 Stable rules

7.12 Section 0.2 applies unchanged: the positive-gate flags after `./gradlew --stop`, quoted
`N actionable tasks: N executed`, fresh JUnit XML only, detekt and tests in separate invocations,
signed and GitHub-verified commits with `Co-Authored-By` as the only trailer, no session identifier
in a commit, the PR or a document, and no merge by the implementer.

Suppressions: **no new suppression of any kind.** The three inherited production suppressions stay
byte-semantically unchanged:

- `@Suppress("UNCHECKED_CAST")` on the processor cast in di/ExerciseChartFeature.kt;
- `@Suppress("MagicNumber")` on `ExerciseChartScreenPopulatedPreview` in ui/ExerciseChartScreen.kt;
- `@Suppress("MagicNumber")` on `previewPoints()` in ui/components/ChartCanvas.kt.

Two inherited suppressions are dead and leave in commit 2 (S10):

- `@Suppress("unused")` on `private fun State.placeholder(): State = this` in
  mvi/handler/CommonHandler.kt, a function nothing has called since the module's scaffold
  (`50b0d32af`, 2026-05-01); the function leaves with it;
- the class-level `@Suppress("MagicNumber")` on the device test `ExerciseChartScreenTest`. detekt's
  `MagicNumber` excludes `**/androidTest/**` and `**/androidDeviceTest/**` (lint-rules/detekt.yml),
  and the instrumented-suite gate builds on no default config, so no tool reads it there. The
  discovery note missed this test suppression; S10 covers it (Section 0.4).

The module has no other test suppression, and Phase 7.13 needs no Native test-name suppression
(Section 4.3).

One session does all the work. Every census, transcription and audit is a script that session
writes and runs, with its own parser. None is a separate agent.

### 0.3 Substitutions against 7.12

| 7.12 | 7.13 |
| --- | --- |
| `feature:home`, `feature/home` | `feature:exercise-chart`, `feature/exercise-chart` |
| package `io.github.stslex.workeeper.feature.home` | `io.github.stslex.workeeper.feature.exercise_chart` |
| resource package `…feature.home.resources` | `io.github.stslex.workeeper.feature.exercise_chart.resources` |
| `HomeFeature`, `HomeGraph`, `homeGraph` | `ExerciseChartFeature`, `ExerciseChartGraph`, `exerciseChartGraph` |
| `homeGraphFactory`, `createHomeGraph()`, `homeStore` | `exerciseChartGraphFactory`, `createExerciseChartGraph(screen)`, `exerciseChartStore` |
| `HomeStoreImpl`, `HomeExtensionIdentityTest` | `ExerciseChartStoreImpl`, `ExerciseChartExtensionIdentityTest` |
| route `Screen.BottomBar.Home`, shape A (`Feature`, `create_args` left at `""`) | route `Screen.ExerciseChart(exerciseUuid: String?)`, shape B (`FeatureAssisted`, `create_args = "screen"`), 7.11 Section 6 |
| graph tag `HomeGraph` | `ExerciseChartGraph` |
| `HOME_ENTRY` | `EXERCISE_CHART_ENTRY` |
| twelve-module Native command | thirteen modules (Section 12.1) |
| 7.12's D3: the resolver-reference check (commit 1) | Section 0.4's D5: bare previews and `formatDayMonth` in the generic gate (commit 1) |
| 7.12's D2: the use-case seam | D2: the `ChartLoader` handler seam (Section 5.5) |
| 7.12's F1: two hoists out of State lambdas | F1: the scrub's copy, resolved once per load (D3, Section 5.2) |
| 7.12's S1: `ResourceWrapper.getAbbreviatedRelativeTime`, two calls | S4: `ResourceWrapper.formatDayMonth`, one call |
| 7.12's S2: the mode-label test on iosTest | D4: the PR-rule parity suite on androidHostTest |

### 0.4 Decisions

Locked by the maintainer's GO (2026-10-18):

| Id | Decision |
| --- | --- |
| D1 | `feature:exercise-chart` is the next shared entry: the smallest remaining candidate, no Android import, every dependency already KMP, and all 11 of its State lambdas clean under the gate's own rules (Section 2). |
| D2 | `ClickHandler` reaches `CommonHandler` through an interface seam (option A): a public one-member `ChartLoader`, `CommonHandler` public with an internal constructor, and `@Binds val CommonHandler.bindChartLoader: ChartLoader` in `ExerciseChartGraph`. The suites record calls on a fake; no identity changes (Section 5.5). |
| D3 | The readout's copy resolves once per load (option A): all three metric names, both units, the record word, and the set plural for each distinct set count among the points, kept in State as `readoutCopy`. The scrub assembles its readout synchronously from it. Dedup, the haptic and the readout/cursor atomicity are unchanged, and the scrub identities keep their assertions (Section 5.2). |
| D4 | `ChartFolderPrRuleParityTest` stays on the Android host (option A): `"androidHostTestImplementation"(project(":core:data:database-test"))` serves its `PrRuleFixture`, so its 4 identities run host-only, while ChartFolder's own 23 run on both targets (Section 11.2). |
| D5 | The topology gate gains, as this PR's **first** commit, an additive `bare_previews` field (default empty; home's own contract untouched) and `formatDayMonth` in the generic `ResourceWrapper` regex, with `ARCHIVE_ENTRY` declaring its one call. It is proven not to change any existing behaviour (Section 12.0). |

Specification decisions, delivered with the discovery note and accepted by the GO. The maintainer
also accepts this document's wording of them by starting the implementation (Section 18):

| Id | Decision |
| --- | --- |
| S1 | The `java.time` port to kotlinx-datetime 0.8.0 and `kotlin.time.Instant` (Section 5.6): `fromEpochMilliseconds(x).toLocalDateTime(tz).date`, `atStartOfDayIn(tz)`, `minus(days, DateTimeUnit.DAY, tz)`, `TimeZone.currentSystemDefault()`, `LocalDate(y, m, d)`; the `ZoneId` parameters become `timeZone: TimeZone`. |
| S2 | The two `@ParameterizedTest` functions of ChartFolderTest each become one `@Test` that loops over `ChartMetricDomain.entries` and names the metric in every assertion message. The function names stay the identities; their JUnit cases go from 6 to 2 (Section 11.1). |
| S3 | `ChartMetricUiModel` and `ChartPresetUiModel` lose their resource ids. MetricTabs and PresetChipsRow map each entry to its `Res.string` with a `when`; the readout mapper maps the metric to its name (Section 5.4). Both model files join `payload_files`. |
| S4 | `formatDayMonth` stays on `ResourceWrapper` (archive's precedent, kmp-string-resolver.md): exactly one call, in `ChartReadoutMapper`, declared through `resource_wrapper_dates`. The readers are the mapper and the two handlers that pass the wrapper on (Section 5.3). |
| S5 | P1: both plurals carry a bare `%d`, so their 12 items (EN 4, RU 8) become `%1$d` (Section 3.4). |
| S6 | The 23 bare `@Preview`s stay as written, pinned per file by D5's field (Section 5.4). |
| S7 | MetricTabsMotionTest's two `@DisplayName`s follow 7.12's S6: the function name is the identity on both targets, and each display sentence moves verbatim into a one-line KDoc directly above `@Test` (Section 4.3). |
| S8 | The device test moves to androidDeviceTest (all-exercises' precedent) with its `LocalDate` fixtures ported (Section 11.3). |
| S9 | `System.currentTimeMillis()` becomes `Clock.System.now().toEpochMilliseconds()`; `@VisibleForTesting` leaves; the Feature takes the factory by constructor (Sections 4.2 and 6). |
| S10 | The two dead suppressions of Section 0.2 leave in commit 2, with the dead `State.placeholder()`. Inherited production suppressions go from 4 to 3, test suppressions from 1 to 0. The discovery's S10 named the production one; the device test's was found while this document was written. |
| S11 | Five signed commits: D5 with its proof; the port in `src/main` (S1, D2, D3 with F1's order, S3, S10), the existing JUnit suites, DST cases included, green on the JVM first; the module move; the gate entry, oracle and workflow at 13 modules; the docs (Section 15). |
| S12 | One iOS scene: the catalog; the readout, footer and grouping copy through the production mappers and the real `ComposeStringResolver` (EN), with only `formatDayMonth` stubbed; the screen's states and the picker; and dispatch, including two presses on the canvas (Section 11.4). |
| S13 | The docs commit re-points tech-debt.md's two exercise-chart `src/main` links and records the follow-ups: D4's option B, the handler-seam template for live-workout, and F1's pre-existing window (Section 9.2). |

Inherited and unchanged:

- P1: a bare `%d` in a migrated catalog becomes positional;
- P1a: the placeholder rule;
- D4 of 7.10: `.claude/skills/` stays untouched;
- 7.12's D3: the State-lambda resolver-reference check, which now also runs over this entry.

**Required, not a decision — F1.** `ClickHandler.processScrub` resolves the readout's copy
(`ResourceWrapper`: one date, one plural and three strings) on a synchronous gesture path today.
`StringResolver` is suspend-only, so a mechanical port would put a launch into the gesture. D3
moves the copy to the load; in `loadChart`'s `onSuccess` the copy then resolves **before** the
staleness guard, so the guard, `prior = state.value`, the index choice and the write still run with
no suspension point between them (Section 5.2). The window that already exists there today — a
scrub landing between `prior` and the write — is recorded as a follow-up, not closed (S13).

## 1. Bounded exit claim

The implementation may prove only this:

- feature:exercise-chart applies `convention.kmpComposeLibrary` and retains Metro and Paparazzi;
- all 43 production Kotlin files (the 41 of Section 3.1, D2's `ChartLoader.kt` and D3's
  `ChartReadoutCopyUiModel.kt`) compile from commonMain for Android, iosSimulatorArm64 and iosArm64;
- the exact private 33-identifier EN/RU catalog lives in commonMain Compose Resources, changed only
  by P1's 12 positional placeholders (Section 3.4);
- every visible Android behaviour is unchanged, except the inherited Compose-resources locale
  property (Section 5.7) and D3's load-time copy (Section 5.2);
- copy outside composition resolves through the injected `StringResolver`, never through CMP's
  top-level `getString` / `getPluralString`, and `ResourceWrapper` keeps exactly one read: the
  readout's day-month date, in the mapper (Section 5.3);
- the scrub resolves no copy: it assembles the readout from `State.readoutCopy` with no suspension
  point, and `loadChart` resolves that copy before its staleness guard (D3, F1);
- no State lambda of a handler resolves copy or references a resolver (7.12's D3 check, now over
  this entry);
- ChartFolder's date arithmetic runs on kotlinx-datetime, and its two DST identities
  (`America/New_York`, 2026) pass on Android host and on Native;
- 8 portable suites with 68 exact identities execute on Android host and on Native; the 4 PR-rule
  parity identities execute on Android host (D4);
- the golden suite executes as 30 Android-host Paparazzi cases (15 methods × light/dark) against
  the same PNG blobs;
- the device suite executes its 7 identities from androidDeviceTest;
- one deterministic iOS production scene proves the whole catalog through the real
  `ComposeStringResolver`, the readout, footer and grouping copy through the production mappers,
  the screen's loading, plot, weightless and four empty states, the picker, and action dispatch,
  canvas presses included;
- the Metro graph is reached through the explicit generation-owned factory (shape B), never
  `LocalContext` or `Context.appDeps`; all five extension identities use the accessor;
- the topology gate checks the entry through one `FeatureEntry` after D5's additive change;
  `Context.appDeps` readers drop from 6 to 5;
- `core:data:database` is no direct edge of the module, and only the host parity suite reads
  `core:data:database-test`;
- Android remains releasable with every repository PNG blob unchanged.

This is a source-set, dependency, resource, test and composition-root migration, plus S1's date
port, D2's seam and D3's copy. It does not permit:

- redesigning the screen, the canvas, the readout, the footer, the tabs, the chips or the picker;
- changing chart folding, record selection, scrub, picker, preset, metric, empty-state or loading
  behaviour;
- closing F1's pre-existing window (a follow-up, S13);
- changing `core:ui:kit`, `core:ui:plan-editor`, `core:ui:navigation`, `core:ui:mvi`,
  `core:core`, `core:data:exercise` or `core:data:database-test`;
- adding a device test identity;
- adding an iOS app;
- migrating another feature.

## 2. Candidate selection — fresh census

Measured at the discovery baseline with 7.9's method (production = `src/main/kotlin`, unit =
`src/test/kotlin`, EN string keys = `<string` elements of `values/strings.xml`, PNGs = files under
`src/test/snapshots`). Every row equals 7.12's table: no candidate changed since `894f2ec6b`.

| Feature | Prod files | Prod lines | Unit files | Unit lines | EN string keys | Plurals | PNGs | `android.*` | `java.*` |
| --- | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: |
| **exercise-chart** | 41 | **3,214** | 10 | 2,314 | 31 | 2 | 30 | 0 | 9 |
| settings | 51 | 3,326 | 12 | 2,638 | 65 | 3 | 12 | 11 | 4 |
| single-training | 39 | 3,635 | 6 | 1,582 | 39 | 1 | 12 | 0 | 0 |
| exercise | 46 | 4,141 | 13 | 2,890 | 53 | 1 | 48 | 10 | 1 |
| live-workout | 59 | 6,755 | 27 | 7,903 | 96 | 2 | 60 | 1 | 0 |

State lambdas, scored by the gate's own `braced_call_bodies`, `.copy(` rule, token list and 7.12's
D3 resolver pattern applied to `src/main` as if each module were an entry (the last column is a
heuristic, not a gate rule):

| Feature | Lambdas | Gate findings | Mapping inside a lambda |
| --- | ---: | --- | ---: |
| **exercise-chart** | 11 | 0 | 0 |
| single-training | 43 | 2 without `.copy(` | 6 |
| exercise | 50 | 1 without `.copy(` | 2 |
| live-workout | 61 | 11 without `.copy(`; 4 that resolve copy | 3 |
| settings | 43 | 0 | 0 |

Every module exercise-chart depends on is already KMP: core:core, core:ui:kit, core:ui:mvi,
core:ui:navigation, core:ui:plan-editor and core:data:exercise, plus kotlinx-datetime, which the
port adds (Section 10). Every symbol its production code imports from them is declared in
commonMain, with two exceptions:

- `DefaultDispatcher` is a commonMain `expect` with platform actuals;
- `core.ui.mvi.di.appDeps` lives in androidMain and leaves with the factory flow (Section 6).

Every androidx.compose import of the module is Compose-Multiplatform common: material3 `Text`,
`Icon` and `HorizontalDivider`; animation core; graphics `Path`, `PathEffect` and `Stroke`;
`pointerInput`; `LocalWindowInfo`, `LocalDensity` and `LocalHapticFeedback`. Its icons come from
kit's `AppIcons`, so it needs no icons artifact. It has no `rememberSaveable`, Parcelable,
`BackHandler` or `nativeCanvas`. The 7.12 scan list (`.format(`, `Locale`, `synchronized`,
`@Volatile`, `Atomic*`, `putIfAbsent`, `getOrDefault`, `computeIfAbsent`, `removeIf`,
`toSortedSet`, `toSortedMap`, `@Jvm*`, `Thread`, `TimeUnit`) finds nothing.

`feature:recovery` stays Android-only (kmp-migration-assessment.md), and `feature:wear-bridge` is
phone↔watch only; neither is a candidate.

## 3. Measured baseline

Static measurements at the discovery baseline. Test names are given as the base leaves them. The
module tracks 85 paths: the build file, 41 production files, 2 catalogs, 10 unit-test files (8
portable suites, the parity suite and the golden suite), 30 PNGs and 1 device test.

### 3.1 Production manifest

All 41 files move from `src/main/kotlin/io/github/stslex/workeeper/feature/exercise_chart/` to
`src/commonMain/kotlin/io/github/stslex/workeeper/feature/exercise_chart/` with unchanged relative
paths. D2 and D3 add one file each beside them (Sections 5.2 and 5.5).

| File | Lines |
| --- | ---: |
| di/ExerciseChartFeature.kt | 38 |
| di/ExerciseChartGraph.kt | 45 |
| di/ExerciseChartHandlerStore.kt | 9 |
| di/ExerciseChartHandlerStoreImpl.kt | 15 |
| di/ExerciseChartScope.kt | 8 |
| domain/ChartFolder.kt | 191 |
| domain/ExerciseChartInteractor.kt | 28 |
| domain/ExerciseChartInteractorImpl.kt | 50 |
| domain/mapper/ExerciseChartDomainMapper.kt | 31 |
| domain/model/ChartFoldDomain.kt | 11 |
| domain/model/ChartFooterStatsDomain.kt | 8 |
| domain/model/ChartMetricDomain.kt | 15 |
| domain/model/ChartPointDomain.kt | 20 |
| domain/model/ChartPresetDomain.kt | 27 |
| domain/model/ExerciseTypeDomain.kt | 7 |
| domain/model/HistoryEntryDomain.kt | 13 |
| domain/model/RecentExerciseDomain.kt | 9 |
| mvi/handler/ClickHandler.kt | 125 |
| mvi/handler/CommonHandler.kt | 191 |
| mvi/handler/NavigationHandler.kt | 23 |
| mvi/mapper/ChartReadoutMapper.kt | 82 |
| mvi/mapper/ExerciseChartUiMapper.kt | 113 |
| mvi/model/ChartFooterStatsUiModel.kt | 21 |
| mvi/model/ChartMetricUiModel.kt | 23 |
| mvi/model/ChartPointUiModel.kt | 22 |
| mvi/model/ChartPresetUiModel.kt | 18 |
| mvi/model/ChartReadoutUiModel.kt | 16 |
| mvi/model/ExercisePickerItemUiModel.kt | 12 |
| mvi/store/ExerciseChartStore.kt | 138 |
| mvi/store/ExerciseChartStoreImpl.kt | 60 |
| ui/ExerciseChartGraph.kt | 34 |
| ui/ExerciseChartScreen.kt | 368 |
| ui/components/ChartCanvas.kt | 288 |
| ui/components/ChartEmptyState.kt | 103 |
| ui/components/ChartFooterStats.kt | 146 |
| ui/components/ChartPointsAnimation.kt | 137 |
| ui/components/ChartReadout.kt | 133 |
| ui/components/ExerciseHeader.kt | 114 |
| ui/components/ExercisePickerSheet.kt | 242 |
| ui/components/MetricTabs.kt | 196 |
| ui/components/PresetChipsRow.kt | 84 |
| **41 files** | **3,214** |

The production suppressions are listed in Section 0.2 (lines 28, 275 and 282 of
ExerciseChartFeature.kt, ExerciseChartScreen.kt and ChartCanvas.kt stay; line 170 of
CommonHandler.kt leaves).

Android coupling in production:

- `R.*` at 34 sites in six files, over all 33 catalog identifiers:
  - two UI files read 15 identifiers at 15 sites through Android `stringResource`:
    `ui/ExerciseChartScreen.kt` (12: the picker affordance and the eleven empty- and error-state
    strings) and `ui/components/ExercisePickerSheet.kt` (3);
  - the two model enums hold 10 ids: `ChartMetricUiModel(labelRes, nameRes)` (the three tab
    labels and the three metric names) and `ChartPresetUiModel(labelRes)` (the four chips).
    `MetricTabs` and `PresetChipsRow` read the labels through `stringResource(metric.labelRes)` and
    `stringResource(preset.labelRes)`; `ChartReadoutMapper` reads the names through
    `resourceWrapper.getString(metric.nameRes)`;
  - `mvi/mapper/ChartReadoutMapper.kt` names 4 identifiers (`readout_sets`, `readout_record`,
    `unit_kg`, `unit_reps`);
  - `mvi/mapper/ExerciseChartUiMapper.kt` names 5 (`value_reps`, `footer_min`, `footer_max`,
    `footer_last`, `unit_kg`); `unit_kg` is the one identifier read at two sites;
- `ResourceWrapper` itself in four files:
  - `ChartReadoutMapper`: 3 `getString` (the metric name, the record word, the unit), 1
    `getQuantityString` (the set count) and 1 `formatDayMonth` (the date);
  - `ExerciseChartUiMapper`: 4 `getString` (the three footer titles and the kg unit) and 1
    `getQuantityString` (a weightless value);
  - `ClickHandler` and `CommonHandler`, which only pass it to the mappers;
- `androidx.compose.ui.res.stringResource` imported in four UI files, with 18 `stringResource(`
  calls: 13 in `ExerciseChartScreen`, 3 in `ExercisePickerSheet`, 1 each in `MetricTabs` and
  `PresetChipsRow`. One of the 13 already reads kit's Compose `Res.string.core_ui_kit_action_back`
  through CMP's overload, which the screen imports beside Android's;
- `LocalContext` with `appDeps` in di/ExerciseChartFeature.kt;
- `@VisibleForTesting` on `ExerciseChartStoreImpl.NAME`;
- `System.currentTimeMillis()` once, in `CommonHandler.loadChart`;
- `java.time`: 9 imports in 6 files:
  - `domain/ChartFolder.kt`: `Instant`, `LocalDate`, `ZoneId`: the day of each entry
    (`Instant.ofEpochMilli(x).atZone(zoneId).toLocalDate()`), its start
    (`day.atStartOfDay(zoneId).toInstant().toEpochMilli()`), and the parameter
    `zoneId: ZoneId = ZoneId.systemDefault()`;
  - `domain/model/ChartPresetDomain.kt`: `Instant`, `ZoneId`: `windowStartMillis(now, zone: ZoneId)`
    subtracts calendar days (`atZone(zone).minusDays(days)`), so a DST change cannot move the
    window's boundary (kmp-migration-assessment.md, decision record A.4);
  - `LocalDate` only in `domain/model/ChartPointDomain.kt`, `mvi/model/ChartPointUiModel.kt` and
    the preview fixtures of `ui/ExerciseChartScreen.kt` and `ui/components/ChartCanvas.kt`.

Already portable:

- all 23 previews: a bare `@Preview` over an explicit `AppTheme(themeMode = ThemeMode.…)`
  (Section 5.4);
- the icons, from kit's `AppIcons` (`ChevronLeft`, `ChevronDown`, `Check`, `ChartLine`);
- the canvas: `Canvas`, `drawLine` / `drawPath` / `drawCircle`, `PathEffect.dashPathEffect`, and the
  scrub's `pointerInput { awaitEachGesture { awaitFirstDown(); drag(…) } }`.

### 3.2 Portable manifest — 8 suites, 68 identities, plus 4 host-only

Each portable suite moves from `src/test/kotlin/…/feature/exercise_chart/` to
`src/commonTest/kotlin/…/feature/exercise_chart/` with its relative path. The parity suite moves to
`src/androidHostTest/kotlin/…/feature/exercise_chart/domain/` (D4).

**domain/ChartFolderTest.kt — 23** (27 JUnit cases today: the two marked ×3 are
`@ParameterizedTest @EnumSource(ChartMetricDomain::class)`)

- empty history returns empty points and null footer
- single set produces single point with min equal max equal last
- multiple sessions are sorted by completion time and use each session maximum
- two same-day sessions remain separate for every metric (×3)
- two different-day sessions remain chronological for every metric (×3)
- setCount counts sets the chart cannot plot
- setCount keeps the readout accurate when only one set is plottable
- equal same-day values keep both sessions in completion order
- weightless exercise plots reps regardless of metric
- preset window filters out sets older than start
- MONTH_1 filter tracks calendar days across a DST spring-forward
- MONTH_1 filter tracks calendar days across a DST fall-back
- ALL preset includes very old sets
- volume metric multiplies weight by reps
- volume tie across sessions preserves both in completion order
- volume tie inside one session keeps the earlier position
- session volume sums every eligible set of the session
- session volume excludes ineligible sets from the sum but not from setCount
- same-day session volume retains each session total
- session total tie preserves both sessions in completion order
- weightless session volume sums reps
- session volume footer tracks session totals across days
- single point case still produces footer with min equal max

**mvi/handler/ClickHandlerTest.kt — 14**

- OnPresetSelect to current preset is no-op
- OnPresetSelect changes state KEEPS emptyReason and delegates load
- OnMetricSelect changes metric KEEPS emptyReason and delegates load
- OnPickerOpen sets isPickerOpen true
- OnPickerQueryChange stores the query and the window resets it both ways
- OnPickerDismiss sets isPickerOpen false
- OnPickerItemSelect to same exercise just dismisses picker
- OnPickerItemSelect to different exercise clears emptyReason and triggers load
- OnScrub to a new index moves the readout and ticks the haptic
- OnScrub can read both sessions completed on the same day
- OnScrub to the current index is a no-op — the haptic is a tick per CROSSED point
- OnScrub outside the point range is ignored
- OnEmptyCtaClick consumes OpenHome navigation
- OnBack consumes PopBack navigation

**mvi/handler/CommonHandlerTest.kt — 13**

- Init with null and no recents emits NO_FINISHED_SESSIONS and skips loadChart
- Init with null and last-trained available loads its chart
- Init with explicit uuid not in recents emits EXERCISE_NOT_FOUND and skips loadChart
- Init with explicit uuid and empty recents prefers NO_FINISHED_SESSIONS
- Init with explicit valid uuid wins over getLastTrainedExerciseUuid
- a response whose metric no longer matches the live state is dropped
- a response that still matches the live state is applied
- loadChart with empty result sets NO_DATA_FOR_EXERCISE
- loadChart with a single point is sub-threshold — empty state no readout
- loadChart with two same-day sessions plots and scrubs the later session
- default state preset is ALL
- a throwing recents read resolves to LOAD_FAILED instead of loading forever
- a throwing chart read resolves to LOAD_FAILED

**mvi/handler/NavigationHandlerTest.kt — 2**

- PopBack triggers popBack
- OpenHome navigates to BottomBar Home

**mvi/mapper/ExerciseChartUiMapperTest.kt — 9**

- domain point keeps session identity in the UI model
- readout for the record point carries the flag and the grouped whole-number value
- readout off the record point has two caption parts and no flag
- volume record indices keep the earliest point on a value tie
- weight record index follows the PR reps tiebreak across sessions
- weight record index keeps the earliest session when weight and reps tie
- readout is null without an active index
- weighted footer values are the readout's grouped form with one shared unit
- weightless footer is a reps plural over the value with no separable unit

**mvi/store/ChartContentResolutionTest.kt — 4**

- one-point exercise never resolves to Plot across metric and preset switches
- zero-point exercise never resolves to Plot across metric and preset switches
- two-point exercise does resolve to Plot
- switching a resolved empty state never passes through Loading

**ui/components/ChartPointsAnimationTest.kt — 1**

- same-day sessions keep distinct animation targets and scrub positions

**ui/components/MetricTabsMotionTest.kt — 2** (function name, then today's `@DisplayName`)

- `indicatorUsesTravel` — the indicator travels on `travel`, and on the scale's own instance
- `durationIsUnchanged` — the duration stays on the scale — only the curve moved

Suite distribution: 23/14/13/2/9/4/1/2. Today's JUnit cases: 72 functions, 76 cases with the
parity suite.

**domain/ChartFolderPrRuleParityTest.kt — 4, host-only (D4)**

- the record-holding set remains represented by its session point
- an ineligible set never becomes the session point
- volume metric keeps the shared eligibility even though it ranks differently
- session metric keeps the shared eligibility in its sum

It reads `PrRuleFixture` (`@TestOnly`, plain data, seven scenarios) from `core:data:database-test`,
an Android-only `convention.androidLibrary` module that also exposes Room and both SQLite drivers
as `api`. `core:data:exercise`'s `PrRuleParityTest` is its other consumer, through the same
`"androidHostTestImplementation"(project(":core:data:database-test"))` edge in a KMP module.

Today's test doubles:

- JUnit 5 in all ten unit suites; the two `@ParameterizedTest` functions above; `@DisplayName`
  twice in MetricTabsMotionTest, between `@Test` and `fun`;
- MockK in four suites:
  - ClickHandlerTest mocks the final `CommonHandler` relaxed, and verifies `loadChart` in five
    identities (exactly 0 or 1 calls, once with an argument). Its relaxed `ResourceWrapper` answers
    `getString` → `label`, `getQuantityString` → `plural` and `formatMediumDate` → `date`; every
    other member, `formatDayMonth` included, answers the relaxed default `""`. Its relaxed store
    answers `state` from the identity's `MutableStateFlow`, applies `updateState`, and records
    `sendEvent` and `consume` for verification;
  - CommonHandlerTest stubs a strict `ExerciseChartInteractor` with `coEvery`, uses a relaxed
    `ResourceWrapper`, and answers the store's `launch` and `launchDefault` by running the action
    under `runBlocking` inside `supervisorScope` (processInit fans out through two `async`
    children), then `onSuccess`, and routes any throw from either to `onError`. `updateState` and
    the update-lambda `updateStateImmediate` apply;
  - NavigationHandlerTest mocks `Navigator` relaxed and verifies `popBack()` and
    `navTo(Screen.BottomBar.Home)`;
  - ChartContentResolutionTest drives the real `CommonHandler` and `ClickHandler` over a strict
    interactor, a relaxed `ResourceWrapper`, and a store that records every State it writes
    (`updateState` and the update-lambda `updateStateImmediate`) and answers `launchDefault` by
    running the action and then `onSuccess` under `runBlocking`, routing a throw to `onError`
    (without `supervisorScope`: its drive has no failing child);
- ExerciseChartUiMapperTest already declares a strict in-file `ResourceWrapper` object:
  `getString` → `string(<id>;<args>)`, `getQuantityString` → `plural(<id>;<quantity>;<args>)`,
  `formatDayMonth` → `dm(<timestamp>)`, `formatMediumDate` → `date(<timestamp>)`, and
  `getAbbreviatedRelativeTime` fails;
- `java.time` fixtures in eight suites: 10 import lines in seven files (ChartFolderTest 3, the
  parity suite 1, the golden suite 2, ClickHandlerTest, ExerciseChartUiMapperTest,
  ChartContentResolutionTest and ChartPointsAnimationTest 1 each) and five fully-qualified
  `java.time.LocalDate` uses in CommonHandlerTest; the device test imports `LocalDate` once.

Abstract members, counted from each type itself: `ExerciseChartInteractor` 3, `HandlerStore` 12,
`Navigator` 6, `ResourceWrapper` 5, `StringResolver` 2. The interactor has no unit test, so no
repository fake and no `core:data:database` edge is needed in any test set.

After the port, 16 identities run through `StringResolver`: the mapper suite's three readout and two
footer identities; the seven CommonHandlerTest identities whose load reaches `onSuccess` (the
second and fifth Init identities, the two staleness identities and the three `loadChart`
identities); and the four ChartContentResolutionTest identities. D3 resolves the readout copy
before the staleness guard, so the "dropped" identity reaches the resolver too, which it did not
before. `formatDayMonth` is reached by 7: two mapper readouts, the two ClickHandler scrubs that move
the readout, the two CommonHandler loads that plot, and the two-point content resolution.

### 3.3 Golden and device ownership

`golden/ExerciseChartGoldenTest.kt` has 15 methods parameterized over `GoldenTheme`, in this order:

    screenPopulated, screenRecordActive, screenWeightless, screenEmptyNoData, screenEmptyNotFound,
    screenEmptyNoSessions, screenLoadFailed, canvasMidScrub, canvasRecordActive,
    pickerSheetContent, readoutPlain, readoutRecord, tabsIndicatorFirst, tabsIndicatorSecond,
    footerStats

That is 30 render cases and 30 PNGs, all mode 100644 and 30 distinct blobs, named
`io.github.stslex.workeeper.feature.exercise_chart.golden_ExerciseChartGoldenTest_<method>_<dark|light>.png`.

The goldens never reach a mapper or a resolver: their fixtures build `ChartReadoutUiModel` and
`ChartFooterStatsUiModel` with literal RU copy (`Максимальный вес`, `11 июля 2026 · 4 подхода`,
`кг`, …), and `ChartPointUiModel`s whose day millis come from `java.time` (`LocalDate.of(…)`,
`atStartOfDay(ZoneOffset.UTC)`). The chrome they compose — tabs, chips, empty states, the picker —
reads the catalog in composition through the production components, so after the move it reads EN
through CMP's `stringResource`, the path archive's, all-exercises' and home's goldens already take.
The golden test itself reads no catalog string.

`ExerciseChartScreenTest` (`src/androidTest`) has 7 identities under a class-level `@Smoke`:

    chart_rendersReadout_whenReadoutIsPresent, chart_rendersRecordDot_whenReadoutIsRecord,
    chart_pressNearLeftEdge_dispatchesOnScrubToFirstIndex,
    chart_pressNearRightEdge_dispatchesOnScrubToLastIndex,
    chart_sameDaySessions_dispatchesBothScrubIndices,
    chart_tappingExerciseHeader_dispatchesOnPickerOpen, chart_singlePoint_neverReachesTheCanvas

It finds nodes by test tag only and reads no copy. It constructs `ChartPointUiModel`s with
`java.time.LocalDate.of(…)` and carries the dead `@Suppress("MagicNumber")` of Section 0.2. Its
edges are `androidTestImplementation` of `libs.bundles.android.test`,
`libs.androidx.compose.ui.test.junit4` and `:core:ui:test-utils`, and `debugImplementation` of the
test manifest.

### 3.4 Exact feature-local resource catalog

Each locale owns 31 strings and 2 plurals, 33 identifiers, in this file order, with the same keys in
the same order in both locales. Values are shown as they must be at exit, decoded the way the
topology gate compares them (DOM text). The baseline differs from them only by P1: the 12 plural
items that carry a bare `%d` (EN 4, RU 8) become `%1$d`.

| # | Identifier (all `feature_exercise_chart_…`) | EN | RU |
| ---: | --- | --- | --- |
| 1 | picker_title | Select exercise | Выбрать упражнение |
| 2 | picker_open | Switch exercise | Сменить упражнение |
| 3 | picker_search | Search | Поиск |
| 4 | picker_no_matches | Nothing found | Ничего не найдено |
| 5 | metric_heaviest | Weight | Вес |
| 6 | metric_session | Session | Сессия |
| 7 | metric_volume | Set | Подход |
| 8 | metric_name_heaviest | Heaviest weight | Максимальный вес |
| 9 | metric_name_session | Volume per session | Объём за сессию |
| 10 | metric_name_volume | Volume per set | Объём за подход |
| 11 | unit_kg | kg | кг |
| 12 | unit_reps | reps | повт. |
| 13 | readout_record | record | рекорд |
| 14 | readout_sets | plural, below | plural, below |
| 15 | preset_1m | 1M | 1М |
| 16 | preset_3m | 3M | 3М |
| 17 | preset_1y | 1Y | 1Г |
| 18 | preset_all | All | Всё |
| 19 | footer_min | Minimum | Минимум |
| 20 | footer_max | Maximum | Максимум |
| 21 | footer_last | Last | Последний |
| 22 | value_reps | plural, below | plural, below |
| 23 | empty_no_sessions_title | No finished workouts yet | Нет завершённых тренировок |
| 24 | empty_no_sessions_subtitle | Finish a session — your progress will appear here. | Завершите тренировку — здесь появится прогресс. |
| 25 | empty_no_sessions_cta | Start a workout | Начать тренировку |
| 26 | empty_not_found_title | Exercise unavailable | Упражнение недоступно |
| 27 | empty_not_found_subtitle | It may be archived or have no logged sets. Pick another from the list. | Возможно, оно архивировано или подходов не было. Выберите другое из списка. |
| 28 | empty_not_found_cta | Choose exercise | Выберите упражнение |
| 29 | error_load_title | Couldn’t load the chart | Не удалось загрузить график |
| 30 | error_load_subtitle | Something went wrong while reading your history. Try again. | Что-то пошло не так при чтении истории. Попробуйте ещё раз. |
| 31 | error_load_cta | Try again | Повторить |
| 32 | empty_no_data_title | Nothing to show yet | Пока нечего показать |
| 33 | empty_no_data_subtitle | The chart appears after two recorded sessions with this exercise. | График появится после двух записанных сессий с этим упражнением. |

`’` is U+2019 in EN row 29; `—` is U+2014 in row 24, in both locales. The RU preset labels use
Cyrillic `М` and `Г`.

| Plural | EN one / other | RU one / few / many / other |
| --- | --- | --- |
| readout_sets | `%1$d set` / `%1$d sets` | `%1$d подход` / `%1$d подхода` / `%1$d подходов` / `%1$d подходов` |
| value_reps | `%1$d rep` / `%1$d reps` | `%1$d повторение` / `%1$d повторения` / `%1$d повторений` / `%1$d повторений` |

The XML moves byte-semantically, except P1: both roots are a plain `<resources>` with no
namespace, there is no `tools:ignore` and no comment, and the blank lines between groups may stay.

At exit, composition reads 22 identifiers (the 15 of the two UI files, the 3 tab labels and the 4
chip labels) and the two mappers read the other 11 outside composition (Section 5.3), `unit_kg`
twice. Nothing outside the module reads these identifiers; only `graphify-out/`'s generated graph
and v2.2-exercise-charts.md name some of them as text. The generated `Res` stays private.

### 3.5 Declared dependencies

Today's build file declares:

- `convention.composeLibrary`, Metro (`includeJavax()`) and Paparazzi;
- `implementation` edges on `core:core`, `core:ui:kit`, `core:ui:mvi`, `core:ui:navigation`,
  `core:ui:plan-editor` and `core:data:exercise`;
- `testImplementation` of `kotlin("test")`, `:core:data:database-test` (for `PrRuleFixture` only,
  with a comment saying so) and `:core:ui:golden-harness`;
- the device edges of Section 3.3.

There is no `core:data:database` edge, and none is needed: no test fakes a repository, and the
production code names no `core:data:database` type. kotlinx-datetime is not declared: core:core
keeps it `implementation`, so the port adds the edge (Section 10).

### 3.6 External consumers and root identity

| Surface | Ownership |
| --- | --- |
| Module edges | app/app `implementation` (stays); app/common `implementation` → `api` |
| Navigation registration | app/common `AppNavigationHost` calls `exerciseChartGraph(modifier = …)`, tag `ExerciseChartGraph` |
| Graph aggregation | app/app `AppGraph` (gains the accessor) |
| Extension identities | app/app test `ExerciseChartExtensionIdentityTest`, through `asContribution` today, 5 identities (below) |
| Direct composition | none: no app test composes `exerciseChartGraph()` directly |
| Device journeys by tag | `RouteReachabilityTest.exerciseChartOpensFromHomeAndHomeReturns`, and `StoreRetentionTest.exerciseChartStateDoesNotLeakBetweenEntities` and `exerciseChartStoreIsDestroyedWhenItsEntryLeavesTheStack`, all `@Regression`; they reach `ExerciseChartGraph` through `NavPaths.openExerciseChart()` (`HomeChartsButton`, a null route argument) and the exercise detail's `ExerciseDetailRecordHero` (a non-null one), and assert the chips `ChartPresetChip_<NAME>` |
| Route | `Screen.ExerciseChart(exerciseUuid: String?)`, core:ui:navigation commonMain, unchanged |

The five extension identities, each of which calls the helper `.exerciseChart("ex-1")` once, the
fourth also `.exerciseChart("ex-2")`, and the fifth `.exerciseChart(null)` instead:

1. extension resolves the store through the parent graph
2. store's app-scoped deps are the SAME instances the parent holds
3. the extension inherits the Default dispatcher key and not the IO one
4. each extension carries its own route arg into the store state
5. a null route arg survives the binding and reaches the store as null

### 3.7 Context readers and PNG integrity

Six executable `Context.appDeps` readers exist (`EXPECTED_APP_DEPS_READERS`): five navigation
entries and app-dialogs. Removing exercise-chart's leaves five.

PNG manifests at the discovery baseline use 7.10 Section 3.7's method
(`git ls-files -s … | LC_ALL=C sort -k4 | shasum -a 256`), reproduced at `5de43879` from git
objects. They equal 7.12's projection (its Section 3.7): home's 42 PNGs moved byte-identically, and
nothing since has touched a PNG.

| Manifest | Entries | Hash |
| --- | ---: | --- |
| Paparazzi mode/blob/path | 456, 13 owners | 2ab60a9cfcf296e515ba73d9c90992ccccbf66d5edb3fca779d67a4230972470 |
| Paparazzi path list | 456 | df423a622a6cbf18c1d8dee9491261f19b4a5486e3fd9bc0220e57f13e8332f6 |
| All repository PNGs, mode/blob/path | 528 | 48c6ad0d5000b5f546ff1287d7b80e3e3f6dc3886eba6eda5c5f602211b2680e |
| exercise-chart blob set (30 sorted blob IDs, duplicates kept) | 30 | aeb9eee0a8671faef28e4f59b5dc8213476bc865bd36d1ef564f086a794de3e2 |

If only the 30 paths move to `src/androidHostTest/snapshots/images/`, the projections are:

- 731d6ed7449bab5cc13c7c4e16074f6014d86b9959f3639fe0bcf626efe5b2e4 (Paparazzi mode/blob/path);
- f26a3dddf485de5e871b6f9cd64401bc8a82178a284b9ac20fdcfec5c3ffedf0 (path list);
- d172abf0962d33f632c4751adbb8d25fd39633a214497ca4e45d5c36c5b60aad (all PNGs).

The simulated exit tree's index reproduced all three and the unchanged blob set.

**The normative check is relative, as in 7.10.** At implementation entry: take the base manifests;
rewrite exactly the 30 exercise-chart paths from `src/test/` to `src/androidHostTest/`; require the
head manifests to equal the rewritten base byte for byte; require the blob-set hash to be unchanged.

### 3.8 Measurement limitation

The discovery ran no Gradle task, no Native task and no device test. The implementation records
fresh entry gates (Section 12) and stops if the environment for any of them is missing.

## 4. Platform/API blockers — settled decisions

### 4.1 Android resources become private Compose resources

7.10 Section 4.2 applies with the package of Section 0.3. No `StringResource`,
`PluralStringResource`, Android id, `ResourceWrapper` or `StringResolver` enters State, Action,
Event, UI models, domain models or repository APIs. S3 takes the ten ids out of the two State enums.

### 4.2 Android and JVM APIs leave common

| Today | At exit |
| --- | --- |
| `LocalContext` + `context.appDeps<…>()` | explicit factory, shape B (Section 6) |
| `androidx.annotation.VisibleForTesting` | removed; `NAME` stays private |
| `R`, `androidx.compose.ui.res.stringResource` | generated `Res`, `org.jetbrains.compose.resources.stringResource` in composition |
| `ResourceWrapper.getString` / `getQuantityString` | `StringResolver` (Section 5.3) |
| `ResourceWrapper.formatDayMonth` | unchanged (S4, Section 5.3) |
| the `Int` resource ids on `ChartMetricUiModel` and `ChartPresetUiModel` | removed; a `when` where each is read (S3, Section 5.4) |
| `java.time.*` | kotlinx-datetime 0.8.0 and `kotlin.time.Instant` (S1, Section 5.6) |
| `System.currentTimeMillis()` | `Clock.System.now().toEpochMilliseconds()` (`kotlin.time.Clock`, the 7.9–7.12 row) |
| the final `CommonHandler` mocked by MockK | D2's `ChartLoader` seam (Section 5.5) |
| copy resolved inside the scrub gesture | D3's per-load copy (Section 5.2) |

`kotlin.time.Instant` and `kotlin.time.Clock` are stable since Kotlin 2.3 (`@SinceKotlin("2.3")
@WasExperimental(ExperimentalTime::class)` in the stdlib at v2.4.10), and the repository builds at
Kotlin 2.4.10 with no `apiVersion` pin. So commit 2's classic module needs no opt-in for them, even
though only the KMP convention passes `-opt-in=kotlin.time.ExperimentalTime`.

There is no `BackHandler`, `uiMode`, paging, `rememberSaveable` or Parcelable to port.

### 4.3 Native test names (S7)

None needs a suppression. Kotlin/Native's `INVALID_CHARACTERS_NATIVE_ERROR` (Kotlin 2.4.10,
`FirNativeIdentifierChecker`, also run in metadata compilation) rejects `.` `;` `,` `(` `)` `[` `]`
`{` `}` `/` `<` `>` `:` `\` `$` `&` `~` `*` `?` `#` `|` `§` `%` `@`, and no identity of Section
3.2 contains any of them; PR-N (`b0045adfa`) already ran over this module. Two contain U+2014 (the
ClickHandlerTest no-op scrub and the CommonHandlerTest single point) and one an apostrophe
(`weighted footer values are the readout's grouped form with one shared unit`); both have run on
Native in past-session, plan-editor and home.

kotlin.test has no display name, so MetricTabsMotionTest keeps the function names
`indicatorUsesTravel` and `durationIsUnchanged` as its identities, on Android host and Native
alike. Each `@DisplayName("…")` line goes; its sentence moves verbatim into a one-line KDoc
(`/** … */`) directly above `@Test`, so `@Test` directly precedes `fun` and the gate's `@Test\s+fun`
reader sees each identity. Function names and bodies do not change.

## 5. Resource, State, copy and preview architecture

### 5.1 Private generated resource owner

7.10 Section 5.1 applies: `compose.resources { packageOfResClass =
"io.github.stslex.workeeper.feature.exercise_chart.resources" }` and no `publicResClass`, so the
generated `Res` is internal to the module. `ui/ExerciseChartScreen.kt` already reads kit's `Res`
(`core_ui_kit_action_back`), so it imports the feature's as
`import io.github.stslex.workeeper.feature.exercise_chart.resources.Res as ExerciseChartRes`
(archive's and plan-editor's precedent); every other file imports the feature's `Res` unaliased.

### 5.2 D3 and F1 — the scrub's copy resolves once per load (required)

`processScrub` runs synchronously from `consume` on Main: it dedups against `state.activeIndex`,
ignores an index outside the points, ticks the haptic, maps the readout through `ResourceWrapper`
(one date, one plural and three strings), and writes `activeIndex` and `readout` in one
`updateState`. `StringResolver` is suspend-only. A launch inside the gesture would move
`activeIndex` only after the copy resolved, so the dedup would read a stale index (a second haptic
tick on jitter) and the readouts of superseded indices could land. D3 removes the copy from the
gesture instead.

**The copy model.** The new `mvi/model/ChartReadoutCopyUiModel.kt`:

    /** D3: the readout's catalog copy, resolved once per load so the scrub never suspends. */
    @Stable
    data class ChartReadoutCopyUiModel(
        val heaviestWeightName: String,
        val volumePerSessionName: String,
        val volumePerSetName: String,
        val kgUnit: String,
        val repsUnit: String,
        val recordLabel: String,
        val setCountLabels: ImmutableMap<Int, String>,
    )

`State` gains, directly after `readout`,
`/** D3: the readout's copy, resolved with the load so the scrub never suspends. */
val readoutCopy: ChartReadoutCopyUiModel?`, and `State.create` sets it to `null`.

**The mapper** (`mvi/mapper/ChartReadoutMapper.kt`) splits the copy from the assembly. At exit:

    /**
     * D3: every catalog read [toReadout] needs, resolved once per load, so the scrub never
     * suspends: all three metric names, both units, the record word, and the set plural for each
     * distinct set count among [points].
     */
    suspend fun toReadoutCopy(
        points: List<ChartPointUiModel>,
        stringResolver: StringResolver,
    ): ChartReadoutCopyUiModel = ChartReadoutCopyUiModel(
        heaviestWeightName = stringResolver.getString(Res.string.feature_exercise_chart_metric_name_heaviest),
        volumePerSessionName = stringResolver.getString(Res.string.feature_exercise_chart_metric_name_session),
        volumePerSetName = stringResolver.getString(Res.string.feature_exercise_chart_metric_name_volume),
        kgUnit = stringResolver.getString(Res.string.feature_exercise_chart_unit_kg),
        repsUnit = stringResolver.getString(Res.string.feature_exercise_chart_unit_reps),
        recordLabel = stringResolver.getString(Res.string.feature_exercise_chart_readout_record),
        setCountLabels = points.map(ChartPointUiModel::setCount).distinct().associateWith { count ->
            stringResolver.getPluralString(Res.plurals.feature_exercise_chart_readout_sets, count, count)
        }.toImmutableMap(),
    )

    fun toReadout(
        points: List<ChartPointUiModel>,
        activeIndex: Int?,
        metric: ChartMetricUiModel,
        type: ExerciseTypeUiModel,
        copy: ChartReadoutCopyUiModel,
        resourceWrapper: ResourceWrapper,
    ): ChartReadoutUiModel?

`toReadout` stays non-suspend and keeps its body, except where its copy comes from: the caption
is still `"${resourceWrapper.formatDayMonth(point.dayMillis)} ${point.day.year}"`, then
`copy.setCountLabels.getValue(point.setCount)`, then `copy.recordLabel` on the record point,
joined with `" · "`; `metricName` is a `when (metric)` over the three names; `unit` a
`when (type)` over `kgUnit` and `repsUnit`. `getValue` cannot miss: `points` and `readoutCopy` are
written by the same State update (below). `recordIndex` and `formatGrouped` are unchanged.

In commit 2 (classic, Section 15) the same shape reads through the wrapper:
`fun toReadoutCopy(points, resourceWrapper: ResourceWrapper)`, non-suspend, with
`resourceWrapper.getString(R.string.…)` and
`resourceWrapper.getQuantityString(R.plurals.feature_exercise_chart_readout_sets, count, count)`.
Commit 3 swaps the reads for the resolver and makes it `suspend`.

**The scrub** (`ClickHandler.processScrub`) at exit:

    private fun processScrub(action: Action.Click.OnScrub) {
        val current = state.value
        if (action.index == current.activeIndex) return
        if (action.index !in current.points.indices) return
        // D3: the load resolved the copy, so the scrub assembles the readout without suspending.
        val copy = current.readoutCopy ?: return
        sendEvent(Event.HapticClick(HapticFeedbackType.SegmentTick))
        // Rule 1 (compose-state-discipline): the readout is mapped BEFORE the lambda.
        val readout = ChartReadoutMapper.toReadout(
            points = current.points,
            activeIndex = action.index,
            metric = current.metric,
            type = current.selectedExercise?.type ?: ExerciseTypeUiModel.WEIGHTED,
            copy = copy,
            resourceWrapper = resourceWrapper,
        )
        updateState { it.copy(activeIndex = action.index, readout = readout) }
    }

The only addition is the `readoutCopy` guard, placed before the haptic. It never returns in
production: a load writes `points` and `readoutCopy` together, so a scrub with an index inside the
points always finds the copy. The dedup, the range check, the haptic, the one `updateState` and
the existing "Rule 1" comment stay.

**The load** (`CommonHandler.loadChart`'s `onSuccess`) at exit:

    onSuccess = { result ->
        // F1: the copy resolves first; the guard, `prior`, the mapping and the write
        // below then run with no suspension point between them.
        val newPoints = result.toUiPoints()
        val readoutCopy = ChartReadoutMapper.toReadoutCopy(newPoints, stringResolver)
        val footerStats = result.footer?.toUi(type, stringResolver)
        if (state.value.requestOf() != request) return@launchDefault
        val prior = state.value
        …                                               // subThreshold, sameSessions, activeIndex: unchanged
        val readout = ChartReadoutMapper.toReadout(
            points = newPoints,
            activeIndex = activeIndex,
            metric = current.metric,
            type = exercise.type,
            copy = readoutCopy,
            resourceWrapper = resourceWrapper,
        )
        updateStateImmediate {
            it.copy(
                points = newPoints,
                footerStats = footerStats,
                activeIndex = activeIndex,
                readout = readout,
                readoutCopy = readoutCopy,
                emptyReason = …,                         // unchanged
                isLoading = false,
            )
        }
    },

Today the guard runs first and `newPoints` and `footerStats` are computed after `prior`; at exit
`newPoints`, the copy and the footer come before the guard, and nothing between the guard and the
write suspends. In commit 2 the two reads take `resourceWrapper` instead of `stringResolver`, and
the order is already this one. The comment lines are part of the contract only in meaning; their
wording may differ.

**What stays as today.** `onSuccess` runs on the Default dispatcher (`launchDefault` passes the
store's default dispatcher as `eachDispatcher`), in parallel with Main's scrubs. So a scrub on the
previous points that lands between `prior = state.value` and the write is overwritten by an
`activeIndex` computed from `prior`. That window exists today, has no suspension point today, and
F1 keeps it so. Closing it (the write on Main, or the index choice inside a CAS `updateState`)
changes today's behaviour; S13 records it as a follow-up. Cancellation covers request changes
(every preset, metric and exercise change goes through `loadChart`, which cancels `loadJob`), not
scrubs.

**D3's observable difference.** The readout's copy now follows the copy resolved at the last load.
After a configuration change that keeps the Store (a locale switch), the readout and the footer
both follow the new locale from the next load; before D3 the next scrub re-read the readout's copy
through `ResourceWrapper`. Every other visible behaviour, the readout's text included, is
unchanged.

The 7.12 D3 gate check already fails a resolver reference inside a State lambda; this entry's copy
contract adds the order check and the scrub's shape (Section 12.1).

### 5.3 Copy outside composition — StringResolver, and S4

The two mappers keep their `internal object`s and files.

- `ChartReadoutMapper.toReadoutCopy` (Section 5.2) holds exactly six
  `stringResolver.getString(` and one `stringResolver.getPluralString(`, which runs once per
  distinct set count. `toReadout` reads no copy itself.
- `ExerciseChartUiMapper`'s footer becomes

      suspend fun ChartFooterStatsDomain.toUi(
          type: ExerciseTypeDomain,
          stringResolver: StringResolver,
      ): ChartFooterStatsUiModel

  Its format lambda becomes a local `suspend fun format(point: ChartPointDomain): String`, with the
  same two branches (`ChartReadoutMapper.formatGrouped(point.value)` for WEIGHTED, the
  `value_reps` plural of `point.value.toInt()` for WEIGHTLESS). It holds exactly four
  `stringResolver.getString(` (the three titles and the kg unit) and one
  `stringResolver.getPluralString(`.

Each read keeps its resource, quantity and arguments: each `resourceWrapper.getString(R.string.x)`
becomes `stringResolver.getString(Res.string.x)` with no format argument, and each
`resourceWrapper.getQuantityString(R.plurals.x, n, n)` becomes
`stringResolver.getPluralString(Res.plurals.x, n, n)`, the count passed as the one format argument.
The mappings themselves, `formatGrouped` with its NBSP, and every KDoc are unchanged.

**S4, the date.** `resourceWrapper.formatDayMonth(point.dayMillis)` in `toReadout` stays, and it is
the only `ResourceWrapper` read in the module. A day-month date is a locale-aware format, not
catalog copy; kmp-string-resolver.md keeps date and time formatting on `ResourceWrapper`, and
archive's `formatDayMonth`, past-session's `formatMediumDate` and home's
`getAbbreviatedRelativeTime` are the precedents. `ResourceWrapper`'s only implementation is
androidMain's `AndroidResourceWrapper`, so iOS has none, and the scene stubs exactly the
timestamps it renders (Section 11.4). A portable date formatter is out of scope (S13 widens 7.12's
row).

**The handlers.**

- `ClickHandler`: constructor `chartLoader: ChartLoader`, `resourceWrapper: ResourceWrapper`,
  `store` (Section 5.5). It reads no copy: the scrub passes `copy` and the wrapper to `toReadout`.
  It names no `StringResolver` and launches nothing.
- `CommonHandler`: constructor `interactor`, `resourceWrapper`, `stringResolver`, `store`. It passes
  the resolver to `toReadoutCopy` and `toUi`, and the wrapper to `toReadout`. It reads no copy
  itself.

**Threading.** Every mapping runs in `loadChart`'s `onSuccess`, on the Default dispatcher, where
today's footer and readout mapping already run. D3 moves one thing: the readout's copy for a scrub,
from Main at gesture time to Default at load time. CMP's first catalog read in a process is a
blocking asset read and later reads are cached; do not move the work otherwise in this phase.

**The argument rule** (7.10 Section 5.2) applies: `StringResolver` returns the catalog text
verbatim when a call passes no format arguments, so a `%1$d` plural read that dropped its count
would ship `%1$d sets`. Both plurals of this module carry `%1$d` at exit, so both pass the count.
The strict fakes (Section 11.1) and controls 36 and 37 pin it.

The event, the State's other fields and the UI models keep their shape: `Event.HapticClick(type:
HapticFeedbackType)`, `ChartReadoutUiModel`, `ChartFooterStatsUiModel` with their pre-formatted
strings.

This module follows tech-debt.md's "Shared (KMP) string resources" rule, so it adds no debt row or
`TODO(tech-debt)` marker for the resolver.

### 5.4 Composable copy and previews (S3, S6)

The four UI files of Section 3.1 resolve copy with `org.jetbrains.compose.resources.stringResource`
on the private `Res`, in the same places and with the same arguments:

- `ExerciseChartScreen`: the picker affordance and the eleven empty- and error-state strings,
  through `ExerciseChartRes.string.…`; kit's `Res.string.core_ui_kit_action_back` stays;
- `ExercisePickerSheet`: `picker_title`, `picker_search` and `picker_no_matches`;
- `MetricTabs`: `stringResource(metric.labelRes())`, over

      private fun ChartMetricUiModel.labelRes(): StringResource = when (this) {
          ChartMetricUiModel.HEAVIEST_WEIGHT -> Res.string.feature_exercise_chart_metric_heaviest
          ChartMetricUiModel.VOLUME_PER_SESSION -> Res.string.feature_exercise_chart_metric_session
          ChartMetricUiModel.VOLUME_PER_SET -> Res.string.feature_exercise_chart_metric_volume
      }

- `PresetChipsRow`: `stringResource(preset.labelRes())`, over the same shape for `MONTH_1` →
  `preset_1m`, `MONTHS_3` → `preset_3m`, `YEAR_1` → `preset_1y`, `ALL` → `preset_all`.

Each file loses its Android `stringResource` and `R` imports. In commit 2 the two `labelRes()`
functions return the `R.string.…` `Int` and are read by Android's `stringResource`; commit
3 swaps them for `StringResource` and `Res`.

**S3, the enums.** At exit:

    enum class ChartMetricUiModel {
        HEAVIEST_WEIGHT,
        VOLUME_PER_SESSION,
        VOLUME_PER_SET,
    }

    enum class ChartPresetUiModel {
        MONTH_1,
        MONTHS_3,
        YEAR_1,
        ALL,
    }

Declaration order is unchanged; it is the tab and chip order. Each KDoc keeps its first sentence
and says where the copy is now mapped (MetricTabs and the readout mapper; PresetChipsRow). The
metric's name moves into `toReadoutCopy`'s three reads and `toReadout`'s `when`. The two files join
`payload_files` (Section 12.1), so a resource handle there is RED from now on.

**S6, the previews.** All 23 are a bare `@Preview` over an explicit
`AppTheme(themeMode = ThemeMode.…)`, so they are portable as written and stay byte-identical, names
included, except S1's fixture changes (`LocalDate.of(…)` → `LocalDate(…)`) in the
`ExerciseChartScreen` and `ChartCanvas` preview data. Per file: `ExerciseChartScreen` 6,
`ChartEmptyState` 3, and 2 each in `ChartCanvas`, `ChartFooterStats`, `ChartReadout`,
`ExerciseHeader`, `ExercisePickerSheet`, `MetricTabs` and `PresetChipsRow`. None carries an
argument list, so `preview_subjects` and `preview_inventory` stay empty and D5's `bare_previews`
pins all 23, and zero in every other commonMain file.

### 5.5 D2 — the handler seam

`ClickHandler` takes `commonHandler: CommonHandler` today and calls `commonHandler.loadChart(…)`
three times (preset, metric and picker changes). `CommonHandler` is an internal final class, and
ClickHandlerTest mocks it relaxed. MockK cannot enter a shared build at all (`mockk` is a forbidden
build token), and Metro generates the graph extension in `:app` and rejects internal contributions
(PR-R K4), so a `@Binds` in the public `ExerciseChartGraph` must name public types. The seam
therefore has the interactor's shape:

- the new `mvi/handler/ChartLoader.kt`:

      /** D2: what [ClickHandler] needs from [CommonHandler] — a reload — bound in `ExerciseChartGraph`. */
      interface ChartLoader {

          /** Re-fetch and bucket the chart for [exercise]; [ClickHandler] calls it on every change. */
          fun loadChart(exercise: ExercisePickerItemUiModel)
      }

- `CommonHandler` becomes public with an internal constructor and implements it:

      @SingleIn(ExerciseChartScope::class)
      class CommonHandler @Inject internal constructor(
          private val interactor: ExerciseChartInteractor,
          private val resourceWrapper: ResourceWrapper,
          private val stringResolver: StringResolver,
          store: ExerciseChartHandlerStore,
      ) : Handler<Action.Common>, ChartLoader, ExerciseChartHandlerStore by store {
          …
          override fun loadChart(exercise: ExercisePickerItemUiModel) { … }  // today's body, Section 5.2
      }

  (commit 2 has no `stringResolver` parameter yet). `loadChart` becomes an `override` and keeps its
  KDoc, which `ChartLoader`'s member repeats; the class gains a one-line KDoc saying it is public
  only because `ExerciseChartGraph` binds it as `ChartLoader` (D2);
- di/ExerciseChartGraph.kt gains, after `bindHandlerStore`:

      /** D2: ClickHandler reloads through the seam; CommonHandler is the scope's one instance. */
      @Binds
      val CommonHandler.bindChartLoader: ChartLoader

- `ClickHandler` takes `private val chartLoader: ChartLoader` in `commonHandler`'s place and calls
  `chartLoader.loadChart(…)` at the same three sites.

`CommonHandler` is `@SingleIn(ExerciseChartScope::class)`, so the binding and the Store's own
`commonHandler` parameter resolve the same instance in each graph. `ExerciseChartInteractorImpl`
already has this public-class, internal-constructor shape, and `MviHandlerConstructorRule` only
requires the `@Inject` constructor. No other handler, model or graph member changes.

This is the template for live-workout, whose `ClickHandler` and `DialogClickHandler` inject
`ExercisePickerHandler` (S13 records it).

D2 lands in commit 2, in `src/main`, with the MockK suites: ClickHandlerTest mocks `ChartLoader`
relaxed where it mocked `CommonHandler`, and ChartContentResolutionTest passes its real
`CommonHandler` as the `chartLoader`. No identity, stub or assertion changes there.

### 5.6 S1 — the `java.time` port

| Today (`java.time`) | At exit (kotlinx-datetime 0.8.0, `kotlin.time.Instant`) |
| --- | --- |
| `import java.time.LocalDate` | `import kotlinx.datetime.LocalDate` |
| `LocalDate.of(y, m, d)` | `LocalDate(y, m, d)` |
| `Instant.ofEpochMilli(x).atZone(zoneId).toLocalDate()` | `Instant.fromEpochMilliseconds(x).toLocalDateTime(timeZone).date` |
| `day.atStartOfDay(zoneId).toInstant().toEpochMilli()` | `day.atStartOfDayIn(timeZone).toEpochMilliseconds()` |
| `Instant.ofEpochMilli(now).atZone(zone).minusDays(days).toInstant().toEpochMilli()` | `Instant.fromEpochMilliseconds(now).minus(days, DateTimeUnit.DAY, timeZone).toEpochMilliseconds()` |
| `zoneId: ZoneId = ZoneId.systemDefault()` | `timeZone: TimeZone = TimeZone.currentSystemDefault()` |
| `windowStartMillis(now: Long, zone: ZoneId)` | `windowStartMillis(now: Long, timeZone: TimeZone)` |
| tests: `ZoneOffset.UTC`, `ZoneId.of("UTC")` | `TimeZone.UTC` |
| tests: `ZoneId.of("America/New_York")` | `TimeZone.of("America/New_York")` |
| tests: `LocalDate.of(y, m, d).atTime(h, mi).toInstant(ZoneOffset.UTC).toEpochMilli()` | `LocalDateTime(y, m, d, h, mi).toInstant(TimeZone.UTC).toEpochMilliseconds()` |
| tests: `….atTime(h, mi).atZone(zone).toInstant().toEpochMilli()` | `LocalDateTime(y, m, d, h, mi).toInstant(zone).toEpochMilliseconds()` |

`Instant` is `kotlin.time.Instant`; the extensions are imported from `kotlinx.datetime`
(`toLocalDateTime`, `atStartOfDayIn`, `minus`, `toInstant`). Checked against kotlinx-datetime
v0.8.0's source: `Instant.minus(value: Long, unit: DateTimeUnit, timeZone: TimeZone)` is a common
extension on `kotlin.time.Instant`; for a date-based unit it moves the local date-time and resolves
it with the original offset preferred, which is `ZonedDateTime.minusDays`' rule in a gap or an
overlap. The two parameter renames carry their KDoc (`[zone]` → `[timeZone]`); the interactor's call
of `bucketAndFold` passes no zone and is unchanged, and tests pass `timeZone = zone`.

The port is commit 2's, over production, the eight `src/test` suites that use `java.time` (the
parity and golden suites among them) and the device test: after it, the module holds no `java.`
token in any source set (Section 12.3). On Android
kotlinx-datetime's JVM implementation reads java.time's zone rules, and on iOS it reads the
system's time-zone database, so the arithmetic is the same and only the data source differs. The
two DST identities pin a DST-observing zone at its real 2026 transitions on both targets, as
kmp-migration-assessment.md requires for this port; they are the first DST identities on Native.

### 5.7 The inherited locale property

7.9 Section 5.7 applies, and from this phase it also covers exercise-chart's copy: the screen
follows CMP's primary-locale choice, so a device whose locale list is [uk-UA, ru-RU] shows Russian
today and English after the move. The day-month date stays on `AndroidResourceWrapper`'s
`SimpleDateFormat` with the best `d MMMM` pattern for `Locale.getDefault()`, as archive's does; on
such a device a readout can therefore pair English copy with a Ukrainian date. `StringResolver` has
the same property (its KDoc, and tech-debt.md's 🟡 row); the remedy is the maintainer's open
decision, not part of this phase.

### 5.8 Back

The module declares no `BackHandler`. The top bar's back button dispatches `OnBack`, and system back
reaches Nav3's default pop, as today.

## 6. Explicit generation-owned shape-B factory flow

7.11 Section 6 applies, with the substitutions of Section 0.3:

- `AppRootDeps` gains `val exerciseChartGraphFactory: ExerciseChartGraph.Factory` after
  `homeGraphFactory`, with the KDoc
  `/** Generation-owned factory for the exercise-chart graph extension. */`;
- `AppGraph` overrides it, after `homeGraphFactory`;
- `App.kt` passes `exerciseChartGraphFactory = deps.exerciseChartGraphFactory` after the home line;
- `AppNavigationHost` takes a required `exerciseChartGraphFactory: ExerciseChartGraph.Factory`
  after `homeGraphFactory`. It calls
  `exerciseChartGraph(factory = exerciseChartGraphFactory, modifier = …)`, and the modifier chain is
  unchanged:
  `standardModifier.reportScreenPlace<Screen.ExerciseChart>().testTag("ExerciseChartGraph")`;
- `exerciseChartGraph` takes a required `factory: ExerciseChartGraph.Factory` as its first
  parameter and builds `navComponentScreen(ExerciseChartFeature(factory)) { processor -> … }`: the
  `FeatureAssisted` overload, which hands the typed `Screen.ExerciseChart` key to
  `processor(screen)`. The rest of the function, its GUARD comment included, is unchanged;
- `ExerciseChartFeature` becomes an `internal class` with
  `private val factory: ExerciseChartGraph.Factory`, still
  `FeatureAssisted<ExerciseChartStoreProcessor, Screen.ExerciseChart>`. Its `processor(screen)`
  keeps `@Suppress("UNCHECKED_CAST")` and returns:

      rememberMetroStoreProcessor<ExerciseChartStoreImpl> {
          factory
              .createExerciseChartGraph(screen)
              .exerciseChartStore
      } as ExerciseChartStoreProcessor

- `createExerciseChartGraph(screen)` runs once, inside the retained `rememberMetroStoreProcessor`
  lambda. The route argument stays a `@Provides` bound instance (7.7 Section 6), and
  `ExerciseChartStoreImpl` still reads `screen.exerciseUuid`;
- di/ExerciseChartGraph.kt changes only by D2's binding;
- app/common's edge becomes `api(project(":feature:exercise-chart"))`, placed after
  `api(project(":feature:home"))`, and its `implementation` line goes. The comment "The twelve entry
  providers AppNavigationHost composes." stays true.

`LocalContext`, `appDeps`, `asContribution`, nullable fallbacks, registries and pre-admission
resolution are forbidden.

`ExerciseChartExtensionIdentityTest` gets the helper:

    private fun AppGraph.exerciseChart(exerciseUuid: String?): ExerciseChartGraph =
        exerciseChartGraphFactory
            .createExerciseChartGraph(Screen.ExerciseChart(exerciseUuid = exerciseUuid))

The `asContribution` import goes. The five names, the call sites (`.exerciseChart("ex-1")` in the
first four identities, plus `.exerciseChart("ex-2")` in the fourth, and `.exerciseChart(null)` in
the fifth) and every assertion stay exact.

No app test composes `exerciseChartGraph()` directly, so the app boundary is six paths (Section
9.2).

## 7. In scope

Only these:

- D5's gate change, as this PR's first commit (Section 12.0);
- S1's port, D2's seam, D3's copy with F1's order, S3 and S10, in `src/main` (Sections 5.2, 5.5,
  5.6);
- the conversion of Sections 4–6;
- the moves of Sections 3.1–3.4 and P1;
- S9: the clock port and the removal of `@VisibleForTesting`;
- the composable copy and the verbatim previews;
- porting the 8 portable suites to kotlin.test with deterministic in-file fakes, S2 and S7;
- moving the parity suite and the golden suite to androidHostTest, and the device test to
  androidDeviceTest;
- one iosTest scene;
- the six app paths, the three CI paths and the five documentation paths;
- one signed, green PR that the maintainer merges.

## 8. Explicit non-goals

7.12 Section 8 applies, read with Section 0.3. In addition:

- no change to `core:ui:kit`, `core:ui:plan-editor`, `core:core` (`ResourceWrapper` gets no iOS
  implementation), `core:data:exercise` or `core:data:database-test`; `PrRuleFixture` does not
  move (D4's option B is a follow-up);
- no portable date formatter (S4);
- no closing of F1's pre-existing window (Section 5.2);
- no other handler seam, and no change to how the Store wires its three handlers;
- no new test identity beyond the scene, no rename of any Section 3.2 or 3.3 identity, and no
  strengthened assertion; S2's loops and S7's KDoc keep every name;
- no new device test identity, and no copy read added to the device test;
- no change to the `TODO(tech-debt)` markers or to the text of tech-debt.md's exercise-chart rows;
  only their links are re-pointed. Row 420 links the module directory and row 494 names its
  location as plain text; both stay;
- no edit to `.claude/skills/` (7.10's D4), including add-feature.md line 42;
- no change to `graphify-out/`.

## 9. Exact allowed change boundary

### 9.1 Target module — 88 tracked paths at exit

- `build.gradle.kts`;
- 43 production files under
  `src/commonMain/kotlin/io/github/stslex/workeeper/feature/exercise_chart/` (Section 3.1, plus
  `mvi/handler/ChartLoader.kt` and `mvi/model/ChartReadoutCopyUiModel.kt`);
- `src/commonMain/composeResources/values/strings.xml` and `values-ru/strings.xml`;
- 8 files under `src/commonTest/kotlin/io/github/stslex/workeeper/feature/exercise_chart/`: the
  suites of Section 3.2;
- `src/androidHostTest/kotlin/io/github/stslex/workeeper/feature/exercise_chart/domain/ChartFolderPrRuleParityTest.kt`;
- `src/androidHostTest/kotlin/io/github/stslex/workeeper/feature/exercise_chart/golden/ExerciseChartGoldenTest.kt`;
- 30 PNGs under `src/androidHostTest/snapshots/images/` (Section 3.3 names);
- `src/androidDeviceTest/kotlin/io/github/stslex/workeeper/feature/exercise_chart/ExerciseChartScreenTest.kt`;
- `src/iosTest/kotlin/io/github/stslex/workeeper/feature/exercise_chart/ExerciseChartFeatureSceneIosTest.kt`.

That is 87 files under `src` plus the build file. Nothing remains under `src/main`, `src/test` or
`src/androidTest`. There is no androidMain, iosMain, new shared test helper, fixture or extra
catalog.

### 9.2 Root, CI and documentation — 14 paths

App (6):

    app/app/src/main/java/io/github/stslex/workeeper/di/AppGraph.kt
    app/app/src/test/kotlin/io/github/stslex/workeeper/di/ExerciseChartExtensionIdentityTest.kt
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
    documentation/feature-specs/kmp-phase-7-13-exercise-chart-feature.md

What changes in each document. This list covers every place 7.12's commits touched for home
(`git diff 6f72bc37 5de43879 -- documentation/`). Every module count there reads thirteen.

- **architecture.md:**
  1. In the shape list, item 2 (`FeatureAssisted`): the sentence naming the shared image-viewer,
     plan-editor and past-session entries as the shape-B factory-resolution exceptions adds
     exercise-chart: `ImageViewerGraph.Factory` / `PlanEditorGraph.Factory` /
     `PastSessionGraph.Factory` / `ExerciseChartGraph.Factory` through `AppNavigationHost` and
     `imageViewerGraph` / `planEditorGraph` / `pastSessionGraph` / `exerciseChartGraph` into
     `ImageViewerFeature` / `PlanEditorFeature` / `PastSessionFeature` / `ExerciseChartFeature`.
  2. The Context-seam paragraph: exercise-chart joins the list, `AppGraph.exerciseChartGraphFactory`
     joins the accessor list, and the sentence reads "Exactly 5 feature/dialog Context readers
     remain."
  3. "State mutation discipline", after 7.12's paragraph, one sentence: "A synchronous gesture
     path cannot suspend, so the copy it needs is resolved earlier, with the load, and kept in
     State: exercise-chart's scrub assembles its readout from `readoutCopy` (decision D3 of
     [kmp-phase-7-13-exercise-chart-feature.md](feature-specs/kmp-phase-7-13-exercise-chart-feature.md))."
- **ci-cd.md**, the places 7.12 updated for home:
  - the `android_build_unified.yml` row of the workflows table ("…, home feature, and
    exercise-chart feature Native tests …, then links the same thirteen modules' …");
  - the KMP-shaped alias list, which adds `:feature:exercise-chart`;
  - the Native job's module description, which adds `:feature:exercise-chart` (all 68 portable
    cases plus the production scene: the catalog and the readout, footer and grouping copy through
    the real `StringResolver`, the loading, plot, weightless and four empty states, the picker,
    and dispatch, canvas presses included);
  - the every-module-is-checked list;
  - the validator paragraph, which adds: "The exercise-chart feature validator requires all 68
    portable tuples and
    `io.github.stslex.workeeper.feature.exercise_chart.ExerciseChartFeatureSceneIosTest.resourcesReadoutFooterStatesPickerAndActionsRenderAndDispatch`
    exactly once, for exactly 69 target tuples.";
  - "All thirteen result directories";
  - "The same thirteen modules'" and `N/13 linked`.
- **testing.md:**
  1. The `AppGraph.stringResolver` bullet: exercise-chart's `CommonHandler` joins the
     constructor-injected readers.
  2. A "Shared exercise-chart feature" subsection after "Shared home feature". It covers:
     - the 68 identities on both targets, in their distribution, and the 4 parity identities on
       the Android host only, through `core:data:database-test` (D4);
     - the store fakes that reproduce the relaxed MockK stores, the recording `ChartLoader` (D2),
       and the in-file interactor and navigator fakes;
     - the strict resolver fakes and the `formatDayMonth`-only `ResourceWrapper` fakes;
     - S2's loops and S7's KDoc;
     - the two DST identities, the first on Native;
     - no direct `core:data:database` edge;
     - the scene, with its date stub and its canvas presses;
     - what it does not cover (Section 11.5);
     - the golden test on androidHostTest with its 30 byte-identical PNGs, and the device test on
       androidDeviceTest;
     - the identity test through `appGraph.exerciseChartGraphFactory`, and the three
       `@Regression` journeys of Section 3.6.
  3. "Shared PR-rule fixture": one sentence saying exercise-chart's `ChartFolderPrRuleParityTest`
     reads the fixture from androidHostTest only, because `core:data:database-test` is
     Android-only (D4), while `ChartFolderTest` runs on both targets.
  4. "Visual gate — Paparazzi screenshot goldens": in both module lists, exercise-chart moves from
     the classic modules (`src/test`) to the KMP modules (`src/androidHostTest`).
  5. The sentence naming the KMP-shaped modules that both alias spellings cover adds
     `:feature:exercise-chart`.
  6. The Native command block under "Running tests" adds
     `:feature:exercise-chart:iosSimulatorArm64Test`.
  7. The device-link block adds `:feature:exercise-chart:linkDebugTestIosArm64`, and its comment
     reads `13/13`.
- **tech-debt.md:**
  1. Rows 421 (`ChartFolder.kt`) and 422 (`CommonHandler.kt`): the links re-pointed to
     `src/commonMain/kotlin/…`; the text stays.
  2. In "Shared (KMP) string resources" (S13):
     - the 🟡 runtime RU catalog coverage row adds exercise-chart to its list of shared entries;
     - the 🟢 typed `rememberMetroStoreProcessor` row reads "the eight shared features'
       `*Feature.kt` (archive, all-trainings, all-exercises, image-viewer, plan-editor,
       past-session, home, exercise-chart)";
     - the 🟢 portable date formatter row adds exercise-chart's one `formatDayMonth` read beside
       archive's.
  3. In "State Mutation Discipline", one 🟢 row (F1): "`loadChart`'s `onSuccess` runs on Default,
     so a scrub on the previous points that lands between `prior = state.value` and the write is
     overwritten by an index computed from `prior`. The window has no suspension point (Phase 7.13
     F1); closing it — the write on Main, or the index choice in a CAS `updateState` — changes
     behaviour." Location: exercise-chart's `CommonHandler.kt` in `src/commonMain`.
  4. A new section "Shared (KMP) feature entries — follow-ups", after "Shared (KMP) string
     resources", with the table format of its neighbours and two 🟢 rows:
     - D4's option B: "`PrRuleFixture` lives in the Android-only `core:data:database-test`, so
       exercise-chart's `ChartFolderPrRuleParityTest` runs on the Android host only. A KMP fixture
       module consumed by `core:data:exercise` and the feature would put the parity on Native;
       worth it once a second shared consumer appears." Location: the parity suite;
     - the handler seam: "A handler that another handler calls is reached through an interface
       bound in the graph (exercise-chart's `ChartLoader`, Phase 7.13 D2), because commonTest
       cannot mock a final handler and Metro's public graph cannot bind an internal one.
       live-workout's `ClickHandler` and `DialogClickHandler` inject `ExercisePickerHandler` and
       follow this template when that feature is shared." Location: `ChartLoader.kt`.

Known stale references after the move, deliberately untouched. They are historical records,
already stale before this phase, or owned by a follow-up documentation PR:

- `documentation/feature-specs/v2.2-exercise-charts.md` lines 184–246 (its module tree) and 827
  (the catalog's `src/main` path);
- `documentation/feature-specs/v2.4-design-foundation.md` line 496;
- `documentation/research/domain-boundary-audit-codex.md` lines 32, 66–68, 79, 105 and 121;
- `documentation/kmp-migration-assessment.md` (its `java.time` findings are now history);
- `.claude/skills/*` (7.10's D4), including add-feature.md line 42, a directory reference that
  stays valid;
- tech-debt.md rows 420 (a directory link) and 494 (plain text);
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
    packageOfResClass = "io.github.stslex.workeeper.feature.exercise_chart.resources"
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
            api(libs.kotlinx.datetime)
            implementation(libs.cmp.animation)
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
    // (the module's PrRuleFixture comment, moved as it is; D4)
    "androidHostTestImplementation"(project(":core:data:database-test"))

    "androidDeviceTestImplementation"(libs.bundles.android.test)
    "androidDeviceTestImplementation"(libs.androidx.compose.ui.test.junit4)
    // GUARD comment, as all-exercises'
    "androidDeviceTestImplementation"(platform(libs.androidx.compose.bom))
    "androidDeviceTestImplementation"(libs.androidx.compose.ui.test.manifest)
    // GUARD comment, as all-exercises'
    "androidDeviceTestImplementation"(project(":core:ui:test-utils"))
}

apply(from = "$rootDir/gradle/golden-gate.gradle.kts")
~~~

Why each visibility:

- `implementation(project(":core:ui:kit"))`: no public declaration of this module names a kit type.
  `StringResolver` appears only in `CommonHandler`'s internal constructor and the internal mapper;
  kit's `Res`, `AppIcons` and components only in internal code. Unlike home, the State carries no
  kit type. app/app and app/common declare the kit, so Metro's extension implementation resolves
  it there;
- `api(project(":core:ui:mvi"))`: `ExerciseChartStore`, `ExerciseChartHandlerStore`,
  `ExerciseChartHandlerStoreImpl`, `ExerciseChartStoreImpl` and now the public `CommonHandler`
  extend the MVI contracts;
- `api(project(":core:ui:navigation"))`: `exerciseChartGraph` extends `NavGraphScope`, and
  `ExerciseChartGraph.Factory` takes a `Screen.ExerciseChart`;
- `api(project(":core:ui:plan-editor"))`: `ExercisePickerItemUiModel`, and so the State, carry its
  `ExerciseTypeUiModel`;
- `implementation` of core:core and core:data:exercise: their types appear only in internal
  constructors and internal code (the repository, `ResourceWrapper`). The graph's
  `@DefaultDispatcher` is an annotation on a public member, which needs no `api` edge; the classic
  module already declared both `implementation`;
- `api(libs.cmp.ui)`: `exerciseChartGraph` takes a `Modifier`, and `Event.HapticClick` carries a
  `HapticFeedbackType`;
- `api(libs.coroutines.core)`: `ExerciseChartGraph` exposes its `@DefaultDispatcher`
  `CoroutineDispatcher`;
- `api(libs.kotlinx.collections.immutable)`: the State and the UI models carry `ImmutableList`, and
  `ChartReadoutCopyUiModel` an `ImmutableMap`;
- `api(libs.kotlinx.datetime)`: `ChartPointUiModel`, `ChartPointDomain` and `ChartPresetDomain`
  carry `LocalDate` and `TimeZone` (S1). kotlinx-datetime is `implementation` in core:core, so it
  does not arrive transitively;
- `implementation(libs.cmp.animation)`: `animateDpAsState`, `animateColorAsState`, `Animatable`,
  `TweenSpec` and `tween` in the tabs, the header, the canvas and its points animation;
- `"androidHostTestImplementation"(project(":core:data:database-test"))`: `PrRuleFixture`, for the
  parity suite only (D4). `core:data:exercise` declares the same edge in a KMP module.

`kotlin("test")` appears exactly twice, once in commonTest and once in iosTest.

Commit 2's only build change is in the classic file: `implementation(libs.kotlinx.datetime)` with a
one-line comment (S1). Removed at commit 3, relative to today: `convention.composeLibrary`, every
`testImplementation` (replaced by commonTest, iosTest and `androidHostTestImplementation`), every
`androidTestImplementation` and the `debugImplementation` of the test manifest (replaced by the
`androidDeviceTestImplementation` edges and the BOM, all-exercises' shape).

The module's own history comments move with the build file: the Metro and Paparazzi plugin
comments and the `PrRuleFixture` comment. Rationale comments adapted from the bullets above may be
added. No comment may hold a dependency fragment or a forbidden build token (`mockk`,
`robolectric`, `androidMain`, `iosMain`, `paging.testing`, `testImplementation(`, …).

Verify Gradle metadata rather than copying it blindly (7.8 Section 10). A dependency the measured
source does not need is removed. One it needs is a STOP-and-amend, never a silent addition.

## 11. Test and Native scene contract

### 11.1 Portable suites

The 8 portable suites move to commonTest. JUnit 5 and MockK give way to `kotlin.test`,
`kotlinx-coroutines-test` and deterministic in-file fakes: named private classes, and no shared
helper. Android host and Native each execute exactly the 68 identities of Section 3.2, with zero
failure, error or skip, in the distribution 23/14/13/2/9/4/1/2.

7.10 Section 11.1, 7.11 Section 11.1, 7.12 Section 11.1 and the fake rules of their Sections 19
apply. In this module that means:

- **ClickHandlerTest.**
  - A private `ExerciseChartHandlerStore`: `state` is the identity's `MutableStateFlow`;
    `updateState` applies to it; `sendEvent` and `consume` record; every other member fails fast,
    `logger` included.
  - A private recording `ChartLoader` (D2): `loadChart` records its argument.
  - A private `ResourceWrapper`: `formatDayMonth` answers `""`, the relaxed mock's default; every
    other member throws. No identity reaches copy: the scrub's copy comes from State.
  - The two scrub fixtures that move the readout (`stateWithPoints` and the same-day identity's
    state) gain `readoutCopy`, a literal `ChartReadoutCopyUiModel` that reproduces today's stubs:
    `label` for the six strings, and `plural` for each set count among their points. Production
    always writes `readoutCopy` with the points (Section 5.2), so this is the State the scrub now
    reads, not a new assertion.
  - Every `ClickHandler(…)` construction passes the recording loader where it passed the mock.
- **CommonHandlerTest.**
  - A private `ExerciseChartHandlerStore`: `state` is the identity's `MutableStateFlow`;
    `updateState` and the update-lambda `updateStateImmediate` apply; `launch` and `launchDefault`
    each launch on the identity's `runTest` scheduler and run the action inside
    `supervisorScope`, then `onSuccess`, and route a throw from either to `onError`, as today's
    mock does (processInit fans out through two `async` children); each returns its `Job`. Every
    other member fails fast, `logger` included. Every identity that invokes the handler runs as
    `= runTest { … }` and advances the scheduler until idle before its assertions; none uses
    `runBlocking`, which no commonTest in the repository uses.
  - A private `ExerciseChartInteractor` (3 members): each answers what the identity sets up — a
    value, a throw, or, for the dropped-response identity, the metric change written during
    `loadChartData` — and counts its calls, so `coVerify(exactly = 0)` and `coVerify(exactly = 1)`
    become asserted counts.
  - A private strict `StringResolver` (below) and a `ResourceWrapper` whose `formatDayMonth`
    answers `""` and whose other members throw.
- **NavigationHandlerTest.** A private `Navigator` (6 members): `navTo` and `popBack` record; the
  other four fail fast.
- **ExerciseChartUiMapperTest.**
  - A private strict `StringResolver` that answers in today's sentinel shapes, keyed by the
    resource's own name: `string(<name>;<args>)` and `plural(<name>;<quantity>;<args>)`, where
    `<name>` comes from the fake's own `when` over the generated resource objects and any other
    resource throws.
  - Its `ResourceWrapper` object keeps only `formatDayMonth` → `dm(<timestamp>)`; every other
    member throws, `formatMediumDate` included.
  - The three readout identities build their copy with the production
    `ChartReadoutMapper.toReadoutCopy(points, resolver)` and pass it to `toReadout`; the record
    identity's expected last caption part becomes
    `resolver.getString(Res.string.feature_exercise_chart_readout_record)` in place of
    `resources.getString(R.string.…)`. The two footer identities call
    `toUi(type = …, stringResolver = resolver)`. These five identities become `= runTest { … }`.
- **ChartContentResolutionTest.**
  - A private store that records every State the handlers write (`updateState` and the
    update-lambda `updateStateImmediate` apply and append), runs `launchDefault`'s action and then
    `onSuccess` on the identity's scheduler and routes a throw to `onError`; `sendEvent` records
    (the click handler's haptics); every other member fails fast.
  - The drive keeps today's order: Init, then the metric switch, then the preset switch, each run to
    completion before the next (`advanceUntilIdle()` after each step), so the recorded emissions
    are today's sequence.
  - The real `ClickHandler` takes the real `CommonHandler` as its `chartLoader`.
  - Interactor, resolver and wrapper fakes as CommonHandlerTest's.
- **ChartFolderTest.** S1's helpers (Section 5.6): `zone = TimeZone.UTC`,
  `nyZone = TimeZone.of("America/New_York")`, `utcMillis` and `zonedMillis` over `LocalDateTime`,
  `LocalDate(…)` in every day assertion, and `timeZone = …` in every `bucketAndFold` call. S2: each
  of the two parameterized functions becomes one `@Test`, without parameters, whose body loops
  `ChartMetricDomain.entries.forEach { metric -> … }` over today's body and passes `metric.name`
  (or a message naming it) to every assertion. The loop covers exactly today's three cases.
- **ChartPointsAnimationTest.** `LocalDate(2026, 4, 28)`.
- **MetricTabsMotionTest.** S7 (Section 4.3).

**The strict StringResolver fakes.** CommonHandlerTest and ChartContentResolutionTest each declare
one that answers `""` (the relaxed mock's default) and the mapper suite's answers in sentinels. All
three throw on any resource their suite does not reach, and on any read that does not pass exactly
what production passes:

- no format argument for the six readout strings and the four footer strings;
- `formatArgs == [quantity]` for `readout_sets` and `value_reps`.

**MockK verifications become recorded-state assertions**, as in 7.10–7.12:
`verify(exactly = 1) { … }` becomes exactly one recorded call with the same arguments;
`verify(exactly = 0) { … }` becomes an empty record (or a count of 0); a `slot` capture becomes the
recorded value. kotlin.test's `assertEquals`, `assertTrue`, `assertFalse`, `assertNull`,
`assertNotNull`, `assertSame` and `assertNotSame` replace JUnit's with the same argument order;
type witnesses may be needed (7.11 Section 19.8). No suite uses `assertThrows`, `assertAll` or a
delta.

**Mechanics only:** the fixtures' `java.time` (S1, commit 2), the constructions that gain or swap a
parameter, S2, S7, and `= runTest { … }` where a suspend call now runs. No other assertion changes.
The inherited casts and `!!` after `kotlin.test` contract assertions stay, together with their
warnings (7.10 Section 19.7).

In commit 2 the suites are still JUnit 5 and MockK in `src/test`: they take S1's fixture port, D2's
`ChartLoader` mock and D3's `readoutCopy` fixtures there, the mapper suite building each readout's
copy with commit 2's `toReadoutCopy(points, resources)`, and keep their parameterized tests and
`@DisplayName`s; the kotlin.test port, S2 and S7 are commit 3's.

### 11.2 Android host: goldens and the parity suite

7.12 Section 11.2 applies. `ExerciseChartGoldenTest` moves to androidHostTest. Its only change is
S1's fixture port, made in commit 2: kotlinx-datetime's `LocalDate`, `TimeZone` and
`atStartOfDayIn` replace the `java.time` imports, `LocalDate.of(` becomes `LocalDate(`, and
`day.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()` becomes
`day.atStartOfDayIn(TimeZone.UTC).toEpochMilliseconds()`, which gives the same millis. Its 15
method names, light and dark cases, subjects, fixture values and 30 filenames stay exact. The PNGs
move with `git mv`, `verifyPaparazziDebug` executes all 30, and `recordPaparazziDebug` is
forbidden. The chrome now reads EN through CMP's `stringResource`, so every PNG must stay
byte-identical; any pixel change is a STOP.

`ChartFolderPrRuleParityTest` moves to androidHostTest with JUnit 5, the host's platform, and S1's
zone port (`private val zone: TimeZone = TimeZone.UTC`, `timeZone = zone`), and nothing else. Its 4
names stay exact.

Host runs: `testAndroidHostTest` executes 9 suites and 72 cases (68 portable + 4 parity);
`verifyPaparazziDebug` executes 10 suites and 102 cases (30 golden + 72) and prints
`Visual gate live: 30 golden test case(s) executed for 30 golden image(s).` The baseline's 9/76
and 10/106 (Section 12.2) differ by S2's four parameterized cases.

### 11.3 Android device (S8)

`ExerciseChartScreenTest` moves to androidDeviceTest. In commit 2 it takes S1's fixture port
(`kotlinx.datetime.LocalDate`, `LocalDate(…)`) and loses the dead `@Suppress("MagicNumber")`
(S10); commit 3 moves it. Its 7 identities, `@Smoke`, test tags, gestures, captured actions and
assertions stay exact. It reads no copy, so no resource behaviour is new on the device, and its
library test APK carries AGP's default debug certificate. It runs on the recorded serial (Section
12.3) and in the canonical Smoke suite (Section 12.4).

The device proof of the factory flow is the three `@Regression` app journeys of Section 3.6, with a
null route argument (`RouteReachabilityTest`, the disposal journey) and a non-null one (the
isolation journey).

### 11.4 Deterministic iOS production scene

Exactly one class and method, no comma:

    class ExerciseChartFeatureSceneIosTest
    fun resourcesReadoutFooterStatesPickerAndActionsRenderAndDispatch()

The scene's setup:

- it uses `runComposeUiTest` and the production `ExerciseChartScreen(state, consume)` inside
  `AppTheme`; it may switch what it composes through test-held state inside one `setContent`;
- a test-held `State` drives the screen, and `consume` records actions;
- it uses the bounded settle stepping of all-exercises' scene (7.10 Section 19.7);
- every copy read outside composition goes through one `ComposeStringResolver()` instance
  (`resolver`); it declares no fake resolver;
- `ResourceWrapper` has no iOS implementation (S4), so the scene declares one private stub. It
  answers `formatDayMonth` for exactly the four day timestamps below and throws on every other call
  and member. It is the scene's only test double.

Fixtures. Every `dayMillis` is `day.atStartOfDayIn(TimeZone.UTC).toEpochMilliseconds()`; the stub
answers 2026-05-02 → `May 2`, 2026-06-01 → `Jun 1`, 2026-07-11 → `Jul 11` and 2026-07-23 →
`Jul 23`. `reps` is any fixed value.

| Set | Points: day, `sessionUuid`, value, `setCount` |
| --- | --- |
| P (weighted) | 2026-05-02 `s0` 49.0 4; 2026-06-01 `s1` 63.0 1; 2026-07-11 `s2` 77.0 4; 2026-07-23 `s3` 70.0 4 |
| G (grouping) | 2026-07-11 `g0` 2940.0 4; 2026-07-23 `g1` 4620.4 4 |
| W (weightless) | 2026-05-02 `w0` 8.0 3; 2026-06-01 `w1` 10.0 3 |

Exercises: `Bench press` (`ex-1`, WEIGHTED) and `Pull-up` (`ex-2`, WEIGHTLESS).

In one bounded scene it proves:

1. **Catalog:** through `resolver`, all 31 strings and both EN quantities (1 → one, 2 → other) of
   both plurals resolve to the exact EN values of Section 3.4, each read as stored, with no format
   argument (35 values; for the plurals that is the verbatim template). Each plural is also read
   filled, with its count as the one format argument, at 1 and 2: `1 set`, `2 sets`, `1 rep`,
   `2 reps`.
2. **Copy through the production mappers** (`resolver`, and the stub for the date):

   | Input | Output |
   | --- | --- |
   | C1 `toReadoutCopy(P, resolver)` | `ChartReadoutCopyUiModel("Heaviest weight", "Volume per session", "Volume per set", "kg", "reps", "record", {4: "4 sets", 1: "1 set"})` |
   | R1 `toReadout(P, 2, HEAVIEST_WEIGHT, WEIGHTED, C1, stub)` | `ChartReadoutUiModel("Heaviest weight", isRecord = true, "Jul 11 2026 · 4 sets · record", "77", "kg")` |
   | R2 `toReadout(P, 1, HEAVIEST_WEIGHT, WEIGHTED, C1, stub)` | `("Heaviest weight", false, "Jun 1 2026 · 1 set", "63", "kg")` |
   | R3 `toReadout(P, null, …)` | `null` |
   | R4 `toReadout(G, 1, VOLUME_PER_SESSION, WEIGHTED, toReadoutCopy(G, resolver), stub)` | `("Volume per session", true, "Jul 23 2026 · 4 sets · record", "4\u00A0620", "kg")` |
   | R5 `toReadout(W, 0, VOLUME_PER_SET, WEIGHTLESS, toReadoutCopy(W, resolver), stub)` | `("Volume per set", false, "May 2 2026 · 3 sets", "8", "reps")` |
   | F1 footer, WEIGHTED, min/max/last = P's 49.0 / 77.0 / 70.0 | `ChartFooterStatsUiModel("Minimum", "49", "Maximum", "77", "Last", "70", "kg")` |
   | F2 footer, WEIGHTED, min/max/last = G's 2940.0 / 4620.4 / 4620.4 | values `2\u00A0940`, `4\u00A0620`, `4\u00A0620`, unit `kg` |
   | F3 footer, WEIGHTLESS, min/max/last = 1.0 / 12.0 / 8.0 | `("Minimum", "1 rep", "Maximum", "12 reps", "Last", "8 reps", null)` |

   The footer inputs are `ChartPointDomain`s with those values.
3. **The screen** (`ExerciseChartScreen`):
   - loading, from `State.create(initialUuid = "ex-1")`: the back button `ExerciseChartBack` with
     the content description `Back` (kit's catalog); no `ExerciseChartPickerOpen`,
     `ChartMetricTabs`, `ChartCanvas`, `ChartReadout`, `ChartFooterStats` or empty-state tag;
   - plot, weighted: `isLoading = false`, `Bench press` selected, both exercises recent, P, metric
     `HEAVIEST_WEIGHT`, preset `ALL`, `activeIndex = 2`, R1, C1 and F1: the header `Bench press`
     whose click action is labelled `Switch exercise`; the tabs `Weight`, `Session` and `Set`; the
     chips `1M`, `3M`, `1Y` and `All`; the readout `HEAVIEST WEIGHT` (`AppLabel` uppercases),
     `Jul 11 2026 · 4 sets · record` and `ChartReadoutRecordDot`; `ChartCanvas`; the footer
     `Minimum`, `Maximum`, `Last`, `49` and `70`; `77` exactly twice (the readout's value and the
     footer's maximum); and `kg` exactly four times (the readout's unit and the three footer
     rows);
   - plot, weightless: `Pull-up` selected, W, metric `VOLUME_PER_SET`, `activeIndex = 0`, R5 and F3:
     no `ChartMetricTabs`, the four chips, the readout `VOLUME PER SET`, `May 2 2026 · 3 sets`, `8`
     and `reps`, no `ChartReadoutRecordDot`, and the footer `1 rep`, `12 reps` and `8 reps` with no
     `kg`;
   - each empty state, with `isLoading = false`:
     - `NO_FINISHED_SESSIONS`, nothing selected: `ExerciseChartNoFinishedSessions` with rows 23–25
       of Section 3.4; no tabs and no chips;
     - `EXERCISE_NOT_FOUND`, nothing selected: `ExerciseChartExerciseNotFound` with rows 26–28;
     - `NO_DATA_FOR_EXERCISE`, `Bench press` with P's first point only:
       `ExerciseChartNoDataForExercise` with rows 32–33, no button, the tabs and the chips, and no
       `ChartCanvas`;
     - `LOAD_FAILED`: `ExerciseChartLoadFailed` with rows 29–31.
4. **The picker:** the weighted plot with `isPickerOpen = true`: `Select exercise`, the field
   `ExerciseChartPickerSearch` with the placeholder `Search`, `ExerciseChartPickerItem_ex-1`
   (`Bench press`, with `ExerciseChartPickerCheck`) and `ExerciseChartPickerItem_ex-2` (`Pull-up`,
   without it); then with `pickerQuery = "zz"`: `ExerciseChartPickerNoMatches` with
   `Nothing found`, and neither row.
5. **Dispatch:** one exact list, in this order:

   | Interaction | Action |
   | --- | --- |
   | the back button (`ExerciseChartBack`), weighted plot | `OnBack` |
   | the header (`ExerciseChartPickerOpen`) | `OnPickerOpen` |
   | the tab `ChartMetricTab_VOLUME_PER_SET` | `OnMetricSelect(VOLUME_PER_SET)` |
   | the chip `ChartPresetChip_MONTH_1` | `OnPresetSelect(MONTH_1)` |
   | a press and release one pixel inside the canvas's left edge, at half its height | `OnScrub(0)` |
   | the same inside its right edge | `OnScrub(3)` |
   | `Start a workout`, `NO_FINISHED_SESSIONS` | `OnEmptyCtaClick` |
   | `Choose exercise`, `EXERCISE_NOT_FOUND` | `OnPickerOpen` |
   | `Try again`, `LOAD_FAILED` | `OnRetryLoad` |
   | the row `ExerciseChartPickerItem_ex-2`, picker open | `OnPickerItemSelect("ex-2")` |
   | `SetText` `zz` into `ExerciseChartPickerSearch` | `OnPickerQueryChange("zz")` |

   The canvas presses are the device test's (`down(Offset(1f, height / 2f))`, `up()`, and
   `width - 1f`). feature:plan-editor's scene already presses with `down(center)` / `up()` and
   all-trainings' and all-exercises' long-click; this is the first scene whose press lands at a
   position read by a custom `awaitEachGesture` detector. `SetText` is past-session's
   `performTextReplacement`. If either is unavailable on the simulator, STOP with the exception
   (Section 14).

It may use production test tags, texts, content descriptions, semantics action labels, scrolling
and deterministic fixtures. It may not add a production test tag, a platform host, a repository, a
database, a fake Store, a fake `StringResolver`, a screenshot baseline or a second identity.

The module's Native oracle is 69 tuples: the 68 portable identities and this scene.

### 11.5 What is not covered

- RU values execute in no test; the topology gate pins them statically (7.12's S5 row, which S13
  widens to this entry).
- F1's pre-existing window: no identity races a scrub against a load, before or after this phase
  (S13 records it).
- The haptic's delivery (the graph's `processor.Handle`) and `OnPickerDismiss`'s sheet gestures
  (scrim, swipe) have no scene path; ClickHandlerTest covers both handlers' State.
- The 4 parity identities run on the Android host only (D4).
- The day-month date runs only through stubs off-device.
- The DST identities cover America/New_York at its 2026 transitions only.

testing.md records these (Section 9.2).

## 12. CI ownership and positive verification

### 12.0 D5 — bare previews and `formatDayMonth` in the generic gate (commit 1)

Two blind spots of the shared gate, both measured at the base:

- The preview census counts `@Preview(` only. All 23 exercise-chart previews are bare, so as an
  entry the module would pass with zero pinned previews. Home's 6 bare ones are pinned by
  `home_preview_contract`, a home-only check. Every remaining candidate has bare previews
  (single-training 2, exercise 14, live-workout 16, settings 6).
- The generic `ResourceWrapper` regex lists `getString`, `getQuantityString`,
  `getAbbreviatedRelativeTime` and `formatMediumDate`. `formatDayMonth` is checked only by
  archive's own contract: a declared allowance counts its file exactly, but an undeclared
  `formatDayMonth` in another reader file is invisible. feature:exercise (`ExerciseUiMapper`) is
  its third reader.

Commit 1 changes only `.github/scripts/assert_kmp_ui_source_topology.py`, in four places. No other
entry, contract, `MODULES` or `FEATURE_ENTRIES` line changes.

Reference implementation. It is normative for the field, the patterns, the comment stripping and
the messages; formatting may differ.

~~~python
# FeatureEntry, after resource_wrapper_dates:
    # D5 (kmp-phase-7-13-exercise-chart-feature.md Section 12.0): the exact bare `@Preview`
    # count per commonMain file, keyed like preview_inventory. Empty skips the check, so every
    # earlier entry stays byte-identical; home keeps home_preview_contract.
    bare_previews: dict = field(default_factory=dict)

# check_feature_entry, the generic ResourceWrapper regex:
            r"resourceWrapper\s*\.\s*(getString|getQuantityString|getAbbreviatedRelativeTime|"
            r"formatMediumDate|formatDayMonth)\s*\(",

# check_feature_entry, directly after the preview_inventory loop:
    # D5: the count above sees only `@Preview(`. An entry that declares bare_previews pins every
    # bare `@Preview` per file, and every other commonMain file holds none.
    if entry.bare_previews:
        bare_preview = re.compile(r"@Preview\b(?!\s*\()")
        expected_bare = {
            common_main / relative: count for relative, count in entry.bare_previews.items()
        }
        for path in sorted(set(expected_bare) - set(common_sources)):
            failures.append(
                f"{module}: {path.relative_to(root)} must hold bare @Preview annotations "
                "but is missing"
            )
        for path, raw in common_sources.items():
            expected = expected_bare.get(path, 0)
            found = len(bare_preview.findall(strip_kotlin_comments(raw)))
            if found != expected:
                failures.append(
                    f"{module}: {path.relative_to(root)} must hold exactly {expected} bare "
                    f"@Preview annotation(s); found {found}"
                )

# ARCHIVE_ENTRY, after extra_checks:
    # D5: the generic regex now sees formatDayMonth, so archive declares its one date read.
    resource_wrapper_dates={"mvi/handler/ArchivePagingHandler.kt": {"formatDayMonth": 1}},
~~~

The diff is 27 insertions and 1 deletion, every added line within 100 columns. Home keeps
`home_preview_contract` and gets no `bare_previews`, so its messages stay byte-identical; the new
messages have home's shape. Archive's own `archive_day_month_contract` stays: it still asserts the
one date read, and its message is part of the old script's output that the proof compares.

**Proof that commit 1 changes no existing behaviour.** It runs on the committed tree, with the
base's script saved aside as `old_gate.py`, under every Python the host has:

1. GREEN: old and new outputs on the unmodified tree are byte-identical, exit code and stderr
   included, and equal to BASE_GATE_OUTPUT.
2. Differential, one exact occurrence per mutation, scored by both scripts and restored
   byte-exactly (sha256 and mode) before the next, with a fresh GREEN after the last:
   - **M** rows: old and new outputs are byte-identical and RED with exactly the named lines;
   - **N** rows: the old script is GREEN (byte-equal to BASE_GATE_OUTPUT) and the new one is RED
     with exactly the named lines;
   - **X** rows: both are RED; the new output is the old one plus exactly the named lines;
   - **G** rows: both are GREEN.

   | # | Mutation | Result |
   | --- | --- | --- |
   | M1 | archive `ArchivePagingHandler.kt`: insert `        resourceWrapper.getString(0),` before the line `        resourceWrapper.formatDayMonth(item.archivedAt),` | both RED, identical: `feature:archive: src/commonMain/kotlin/io/github/stslex/workeeper/feature/archive/mvi/handler/ArchivePagingHandler.kt resolves archive copy through ResourceWrapper.getString; use the private Compose resources` |
   | M2 | home `HomeScreen.kt`: remove the bare `@Preview` above `HomeScreenEmptyLightPreview` | both RED, identical: `feature:home: src/commonMain/kotlin/io/github/stslex/workeeper/feature/home/ui/HomeScreen.kt must hold exactly 4 bare @Preview annotation(s); found 3` |
   | M3 | past-session `PastSessionUiMapper.kt`: a second `val probeLabel = resourceWrapper.formatMediumDate(finishedAt)` after `val finishedAtLabel = …` | both RED, identical: `…PastSessionUiMapper.kt must read the ResourceWrapper.formatMediumDate date exactly 1 time(s); found 2` |
   | N1 | past-session `CommonHandler.kt`: `        val probe = resourceWrapper.formatDayMonth(0L)` after `        val sessionUuid = state.value.sessionUuid` | new only: `feature:past-session: src/commonMain/kotlin/io/github/stslex/workeeper/feature/past_session/mvi/handler/CommonHandler.kt resolves past-session copy through ResourceWrapper.formatDayMonth; use the private Compose resources` |
   | N2 | home `ClickHandler.kt`: the same probe line before the `OpenSettings` haptic and `consume` pair | new only: `feature:home: src/commonMain/kotlin/io/github/stslex/workeeper/feature/home/mvi/handler/ClickHandler.kt resolves home copy through ResourceWrapper.formatDayMonth; use the private Compose resources` |
   | X1 | archive `ArchivePagingHandler.kt`: the `formatDayMonth` line twice | old: `feature:archive: ResourceWrapper must serve exactly one formatDayMonth date`; new adds exactly `…ArchivePagingHandler.kt must read the ResourceWrapper.formatDayMonth date exactly 1 time(s); found 2` |
   | X2 | archive `ArchivePagingHandler.kt`: that line → `        item.archivedAt.toString(),` | new adds exactly `…ArchivePagingHandler.kt must read the ResourceWrapper.formatDayMonth date exactly 1 time(s); found 0` |
   | G1 | archive `ArchivePagingHandler.kt`: the comment `    // resourceWrapper.formatDayMonth is this handler's one date read.` before the KDoc of the date's mapping | both GREEN |
   | G2 | past-session `CommonHandler.kt`: `        val formatDayMonthProbe = 0` after `val sessionUuid = …` | both GREEN |

   `…` abbreviates the module-relative path, written out in full in M1, N1 and N2. G1 pins that
   a comment naming the method stays GREEN: the regex reads raw source, as today, but a name not
   followed by `(` is no call.
3. The diff of commit 1 touches only the topology script, and in it only the four places above.

A differing byte in step 1 or step 2's M rows, an N or X row that is not RED with exactly its lines,
or a G row that is not GREEN is a STOP.

The proof was dry-run under Python 3.9, 3.11, 3.12, 3.13 and 3.14 against the discovery tree with
D5 applied, and every row produced exactly its result. That dry run is not evidence.

### 12.1 Topology gate data (normative)

`MODULES["feature:exercise-chart"]` holds the 87 files under `src` of Section 9.1. It follows the
home entry and uses its format:

- `kotlin_source_sets`: `commonMain`, `commonTest`, `androidHostTest`, `androidDeviceTest`,
  `iosTest`;
- `resource_dirs`: `src/commonMain/composeResources/values` and
  `src/commonMain/composeResources/values-ru`.

`EXPECTED_APP_DEPS_READERS` loses the exercise-chart entry. The script compares it exactly with
every `appDeps<…Factory>(` reader in the tree.

`EXERCISE_CHART_ENTRY` is appended to `FEATURE_ENTRIES` after `HOME_ENTRY`:

| Field | Value |
| --- | --- |
| module / short_name | `feature:exercise-chart` / `exercise-chart` |
| root / package_dir | `feature/exercise-chart` / `io/github/stslex/workeeper/feature/exercise_chart` |
| build_fragments | every plugin, `packageOfResClass`, `includeJavax()`, dependency and `apply(from = …)` line of Section 10, each exactly once (26), except `implementation(kotlin("test"))`, which kotlin_test_count covers |
| kotlin_test_count | 2 |
| catalog | the 33 rows of Section 3.4, in file order, with the exit values (decoded text; `’` and `—` as the characters) |
| resource_prefix | `feature_exercise_chart_` |
| payload_files | `ExerciseChartStore.kt`, `ChartFooterStatsUiModel.kt`, `ChartMetricUiModel.kt`, `ChartPointUiModel.kt`, `ChartPresetUiModel.kt`, `ChartReadoutCopyUiModel.kt`, `ChartReadoutUiModel.kt`, `ExercisePickerItemUiModel.kt` (plus everything under domain/). The two mappers and the two copy-reading handlers are deliberately absent |
| store_file / state_fields | `mvi/store/ExerciseChartStore.kt` / `isLoading: Boolean`, `initialUuid: String?`, `selectedExercise: ExercisePickerItemUiModel?`, `recentExercises: ImmutableList<ExercisePickerItemUiModel>`, `preset: ChartPresetUiModel`, `metric: ChartMetricUiModel`, `points: ImmutableList<ChartPointUiModel>`, `footerStats: ChartFooterStatsUiModel?`, `activeIndex: Int?`, `readout: ChartReadoutUiModel?`, `readoutCopy: ChartReadoutCopyUiModel?`, `isPickerOpen: Boolean`, `pickerQuery: String`, `emptyReason: EmptyReason?` |
| resource_wrapper_readers | `mvi/mapper/ChartReadoutMapper.kt`, `mvi/handler/CommonHandler.kt`, `mvi/handler/ClickHandler.kt` |
| resource_wrapper_dates | `{"mvi/mapper/ChartReadoutMapper.kt": {"formatDayMonth": 1}}` |
| preview_subjects / preview_inventory | `{}` / `{}` |
| bare_previews (D5) | `ui/ExerciseChartScreen.kt` 6, `ui/components/ChartEmptyState.kt` 3, and 2 each for `ui/components/ChartCanvas.kt`, `ChartFooterStats.kt`, `ChartReadout.kt`, `ExerciseHeader.kt`, `ExercisePickerSheet.kt`, `MetricTabs.kt`, `PresetChipsRow.kt` |
| suppressions | the three production annotations of Section 0.2 under their `src/commonMain/…` paths, each `['"…"']`; nothing under any test source set |
| test_names | the 68 of Section 3.2 under their commonTest paths; the 4 parity names under their androidHostTest path; the scene name under its iosTest path |
| golden_test / golden_methods | `src/androidHostTest/…/golden/ExerciseChartGoldenTest.kt` / the 15 of Section 3.3 |
| device_test / device_fragments | `src/androidDeviceTest/…/ExerciseChartScreenTest.kt` / `@Smoke` and the seven `fun <identity>()` signatures of Section 3.3, each exactly once |
| factory names | Section 0.3; `create_args = "screen"`; feature_file `di/ExerciseChartFeature.kt`, graph_file `ui/ExerciseChartGraph.kt` |
| identity_test / identity_fragments | `app/app/src/test/…/di/ExerciseChartExtensionIdentityTest.kt` / the `exerciseChart(exerciseUuid)` helper of Section 6, on one line |
| identity_names / identity_accessor | the five names of Section 3.6 / below |
| extra_root_fragments | none |
| extra_checks | `exercise_chart_copy_contract`, `exercise_chart_dependency_contract` below |

`identity_accessor` is the pattern `(?<!AppGraph)\.exerciseChart\((?:"ex-1"|null)\)`. It
matches five times, once per identity; the fourth identity's `"ex-2"` call is deliberately not
counted.

`exercise_chart_copy_contract` checks Sections 5.2–5.5. Every count is over sources with Kotlin
comments stripped, and it reuses past-session's helpers' shape (`source_of`, `require`,
`require_parameters`):

1. Parameters, exactly:
   - `HapticClick` in `mvi/store/ExerciseChartStore.kt`: `["type: HapticFeedbackType"]`;
   - `ChartReadoutUiModel`: `["metricName: String", "isRecord: Boolean", "caption: String",
     "value: String", "unit: String"]`;
   - `ChartReadoutCopyUiModel`: `["heaviestWeightName: String", "volumePerSessionName: String",
     "volumePerSetName: String", "kgUnit: String", "repsUnit: String", "recordLabel: String",
     "setCountLabels: ImmutableMap<Int, String>"]`;
   - `ChartFooterStatsUiModel`: `["minTitle: String", "minValue: String", "maxTitle: String",
     "maxValue: String", "lastTitle: String", "lastValue: String", "unit: String?"]`,
   each in its `mvi/model/` file.
2. S3: `mvi/model/ChartMetricUiModel.kt` holds `enum class ChartMetricUiModel {` and
   `mvi/model/ChartPresetUiModel.kt` holds `enum class ChartPresetUiModel {`, each exactly once.
3. `mvi/mapper/ChartReadoutMapper.kt` holds exactly once each of `suspend fun toReadoutCopy(`,
   `fun toReadout(` and `copy: ChartReadoutCopyUiModel,`; no `suspend fun toReadout(`; exactly six
   `stringResolver.getString(` and one `stringResolver.getPluralString(`.
4. `mvi/mapper/ExerciseChartUiMapper.kt` holds exactly once
   `suspend fun ChartFooterStatsDomain.toUi(`, exactly four `stringResolver.getString(` and one
   `stringResolver.getPluralString(`.
5. D2 and D3, `mvi/handler/ClickHandler.kt`: exactly once each of
   `private val chartLoader: ChartLoader,`, `private val resourceWrapper: ResourceWrapper,`,
   `val copy = current.readoutCopy ?: return` and `ChartReadoutMapper.toReadout(`; exactly three
   `chartLoader.loadChart(`; and none of `CommonHandler`, `StringResolver`, `stringResolver` or
   `launch`.
6. `mvi/handler/CommonHandler.kt`: exactly once each of
   `class CommonHandler @Inject internal constructor(`,
   `override fun loadChart(exercise: ExercisePickerItemUiModel)`,
   `private val resourceWrapper: ResourceWrapper,`, `private val stringResolver: StringResolver,`,
   `ChartReadoutMapper.toReadoutCopy(newPoints, stringResolver)`, `toUi(type, stringResolver)` and
   `ChartReadoutMapper.toReadout(`; and no `getString(` or `getPluralString(`.
7. F1, in that file: `ChartReadoutMapper.toReadoutCopy(newPoints, stringResolver)` occurs before
   `if (state.value.requestOf() != request) return@launchDefault`, which occurs before
   `val prior = state.value`; otherwise
   ``feature:exercise-chart: <file> must resolve the readout copy before the staleness guard and
   the read of `prior` (F1)``.
8. `mvi/handler/ChartLoader.kt` holds `interface ChartLoader {` and
   `fun loadChart(exercise: ExercisePickerItemUiModel)`, and `di/ExerciseChartGraph.kt` holds
   `val CommonHandler.bindChartLoader: ChartLoader`, each exactly once.
9. 7.11's rules 4–7: no commonMain import of CMP's top-level `getString` / `getPluralString`; under
   `mvi/`, every `getString(` / `getPluralString(` is a `stringResolver.` call and no file holds
   `stringResource(` / `pluralStringResource(`; no `ui/` file holds `getString(` /
   `getPluralString(`; both walks find at least one file.

The message shapes are past-session's: `<file> copy contract requires <fragment> exactly <n>;
found <m>`, `<file> <name> parameters must be exactly [...]; actual=[...]`, and so on.

`exercise_chart_dependency_contract`:

- the build file contains no `project(":core:data:database")`, with the message
  `feature:exercise-chart: build must not depend on :core:data:database in any source set`;
- no Kotlin file under `src` names `core.data.database` (comments stripped) except
  `src/androidHostTest/…/domain/ChartFolderPrRuleParityTest.kt`, with the message
  `<file> must not read core:data:database or its fixtures; only the host parity suite reads PrRuleFixture (D4)`;
  and that file exists;
- no Kotlin file under `src/commonMain`, `src/commonTest` or `src/iosTest` names `java.`,
  imported or fully qualified (comments stripped; `(?<![\w.])java\.`), with the message
  `<file> names java.* in shared code; use kotlinx-datetime (S1)`.

The topology script's change after commit 1 is additive:

- `MODULES`, `EXPECTED_APP_DEPS_READERS`, `EXERCISE_CHART_ENTRY`, its two checks and the
  `FEATURE_ENTRIES` tuple;
- the only removed lines are the reader entry and the old `FEATURE_ENTRIES` line;
- `check_feature_entry` (as commit 1 left it), the five existing entries and their checks stay
  byte-identical.

`assert_kmp_ios_smoke.py` gains a `feature:exercise-chart` entry, after home's, with the 69 tuples
of Section 11.4: the 68 under their suites' classnames (Section 3.2's packages) and the scene's
(`io.github.stslex.workeeper.feature.exercise_chart.ExerciseChartFeatureSceneIosTest`). The parity
suite is not a Native tuple.

`android_build_unified.yml`, KMP iOS kit smoke job:

- add `:feature:exercise-chart:iosSimulatorArm64Test` to the simulator test command and
  `:feature:exercise-chart:linkDebugTestIosArm64` to the device-link command, after home;
- add the directory `feature/exercise-chart` to the binary-assert loop, on a new continuation line
  (`/12 linked` → `/13 linked`), and
  `feature/exercise-chart/build/test-results/iosSimulatorArm64Test/` to the upload list;
- the job-header comment names the exercise-chart feature among the features, rewrapped to stay
  within 100 columns ("…, past-session, home and" / "exercise-chart features' Phase-7 Native tests
  on the iOS simulator, then a checked-in script" / "requires every exact per-module XML
  identity."), and the two "twelve modules" comments say thirteen (the first rewrapped so its
  trailing "a" moves to the next line).

The job, step and context names stay. The build job's comment "all 13 golden-holding modules" stays
true.

The thirteen modules, for every command below: `:core:ui:kit`, `:core:ui:navigation`,
`:core:ui:mvi`, `:core:ui:start-mode`, `:core:ui:plan-editor`, `:feature:image-viewer`,
`:feature:plan-editor`, `:feature:archive`, `:feature:all-trainings`, `:feature:all-exercises`,
`:feature:past-session`, `:feature:home`, `:feature:exercise-chart`.

The prototype run of this data (Section 13) printed, on the simulated exit tree, 647 lines with exit
0 under Python 3.9, 3.11, 3.12, 3.13 and 3.14, byte-identical: the base's 556; exercise-chart's
`MODULES` block (88 lines); `exercise-chart catalogs, semantic State, suppressions, previews, test
identities, and explicit factory flow are exact`; `app:common API edges and 5 remaining
Context.appDeps readers are exact`; and the two exercise-chart catalogs among `22 commonMain
catalogs walked` (EN 4 placeholders, RU 8). The oracle entry verified 69 exact identities against
synthetic XML. That run is not evidence.

### 12.2 Entry gate before any edit

7.12 Section 12.2 applies, plus:

- `5de43879a` (Phase 7.12's merge) is an ancestor of the base;
- `git diff 5de43879a..<base> -- feature/exercise-chart` is empty;
- every commit since `5de43879a` that touches a Section 9.2 path is listed, with whether it
  conflicts;
- the census of Sections 3.1–3.7 is reproduced on the base. It expands parameterized cases (72
  functions, 76 JUnit cases with the parity suite) and counts fully-qualified `java.` uses, not
  only imports;
- the current twelve-module Native command and oracle are green.

Baseline commands, with the stable flags:

    ./gradlew :feature:exercise-chart:assembleDebug
    ./gradlew :feature:exercise-chart:testDebugUnitTest      # 9 suites, 76 cases; goldens excluded
    ./gradlew :feature:exercise-chart:verifyPaparazziDebug   # 10 suites, 106 cases: 30 golden + 76
    ./gradlew :app:app:testDebugUnitTest --tests '*ExerciseChartExtensionIdentityTest*' --tests '*StringResolverIdentityTest*'   # 7 cases
    ./gradlew :feature:exercise-chart:connectedDebugAndroidTest   # on the recorded serial: 7 cases

### 12.3 Focused gates after the implementation

After commit 1: Section 12.0's proof.

After commit 2 (still `src/main`), on the committed tree:

- `:feature:exercise-chart:testDebugUnitTest` (9 suites, 76 cases, names exactly Section 3.2, the
  two DST identities among them) and `:feature:exercise-chart:verifyPaparazziDebug` (10 suites, 106
  cases, and `Visual gate live: 30 golden test case(s) executed for 30 golden image(s).`): the
  port's first, JVM-only run;
- the five `ExerciseChartExtensionIdentityTest` identities, which build the real graph with D2's
  binding;
- the module's device test on the recorded serial (7 cases);
- `git grep -n 'java\.' -- feature/exercise-chart` prints nothing;
- detekt alone;
- the topology script, byte-identical to its commit-1 output.

After commit 3, on the committed tree:

    python3 .github/scripts/assert_kmp_ui_source_topology.py
    ./gradlew :feature:exercise-chart:assembleDebug            # Android and both iOS klibs
    ./gradlew :feature:exercise-chart:testAndroidHostTest      # 9 suites, 72 cases
    ./gradlew :feature:exercise-chart:verifyPaparazziDebug     # 30 golden + 72
    ./gradlew :feature:exercise-chart:iosSimulatorArm64Test    # 69 tuples
    ./gradlew :app:common:assembleDebug

Then:

- `./gradlew detekt --continue` alone, on every commit's tree;
- the five `ExerciseChartExtensionIdentityTest` identities and the two
  `StringResolverIdentityTest` identities (fresh XML);
- on an explicit API-34 serial with animations disabled, as `ui_tests.yml` does: the module's device
  test through `:feature:exercise-chart:connectedDebugAndroidTest` (the KMP convention's alias onto
  `connectedAndroidDeviceTest`), and the three journeys of Section 3.6 (fresh XML);
- the relative PNG proof of Section 3.7.

Run `:feature:exercise-chart:iosSimulatorArm64Test` as soon as production and commonTest compile for
Native, before the scene and the app paths. That run is the first Native measurement of the
kotlinx-datetime port and of the two DST identities, and it must give the 68 identities by name.

After commit 4: the thirteen-module Native command (`--continue`) and the oracle, the
thirteen-module device link (13/13), the topology script, and detekt alone.

### 12.4 Repository and visual gates

7.12 Section 12.4 applies unchanged: assemble, lint and unit; detekt alone;
`assembleDebugAndroidTest`; `verifyPaparazziDebug`; `:lint-rules:test`; the personal-data gate; the
canonical Smoke and Regression device suites; diff hygiene; the PNG manifest check of Section 3.7;
the mockup shell gate, where runnable.

### 12.5 Final diff and remote proof

7.12 Section 12.5 applies, with: the Section 9 boundary; 69 Native tuples; 30 unchanged golden
blobs; the three production suppressions and no test suppression. Record the KMP iOS kit smoke
job's duration on the final head against its 120-minute timeout.

## 13. Mandatory known-negative controls

7.12 Section 13's procedure applies: every control runs fresh GREEN → one named observable RED →
automatic exact restoration → fresh GREEN; Gradle- and device-scored controls through
`documentation/mockups/mutation_harness.py` in one-shot mode, with `--no-configuration-cache
--console=plain` inside `--task`; Python-scored controls against a byte-restoring copy in a detached
worktree under `.claude/worktrees/`; a compile-invalid mutation is never a RED where a compiled test
is the detector; no mutation is committed. Every control runs after commit 4, on its committed tree.
Where a row says "at least", the named identities must be RED and every other RED identity is
recorded.

**From 7.12, with the substitutions of Section 0.3:**

| # | Mutation | Scorer: expected RED |
| ---: | --- | --- |
| 1 | delete `ui/components/PresetChipsRow.kt` | topology: source manifest mismatch, `missing=[…PresetChipsRow.kt]`, and D5's `…PresetChipsRow.kt must hold bare @Preview annotations but is missing` |
| 2 | copy `di/ExerciseChartScope.kt` into `src/main/kotlin/…/di/` | topology: legacy source set `src/main`, manifest `extra=[…]`, unexpected Kotlin-bearing source set `src/main` |
| 3 | move `ui/components/PresetChipsRow.kt` to `ui/PresetChipsRow.kt` | topology: manifest `missing` and `extra`; D5's `…ui/components/PresetChipsRow.kt must hold bare @Preview annotations but is missing` and `…ui/PresetChipsRow.kt must hold exactly 0 bare @Preview annotation(s); found 2` |
| 4 | `import android.util.Log` in `ui/components/PresetChipsRow.kt` | topology: platform API in commonMain, `'import android.'` |
| 5 | rename the EN key `feature_exercise_chart_picker_search` to `…_renamed` | topology: EN catalog mismatch (`order=drifted`), two ownership-drift lines |
| 6 | the RU value of `feature_exercise_chart_picker_search` `Поиск` → `Поиск по списку` | topology: RU catalog mismatch, `order=exact` |
| 7 | move the EN `feature_exercise_chart_picker_search` into `feature/settings/src/main/res/values/strings.xml` | topology: resource ownership drift, and the EN catalog mismatch |
| 8 | `val titleRes: Int = 0` as State's last field | topology: Store State must stay semantic |
| 9 | `toReadout`'s `add(copy.recordLabel)` → `add(resourceWrapper.getString(0))` | topology: `…ChartReadoutMapper.kt resolves exercise-chart copy through ResourceWrapper.getString`; the `mvi/` rule `'getString(' 7 time(s), 6 through stringResolver` |
| 10 | `val label = stringResolver.getString(Res.string.feature_exercise_chart_unit_kg)` as the first line of `loadChart`'s `updateStateImmediate { … }` | topology: `resolves a resource inside the State lambda: 'getString('` and `… 'Res.'`; copy contract `'getString(' exactly zero times; found 1` on `CommonHandler` |
| 11 | the bare `@Preview` above `MetricTabsDarkPreview` → `@Preview(uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES)` | topology: forbidden `'android.content'` and `'uiMode'`; `exactly zero portable previews are required; found 1`; `…MetricTabs.kt must hold exactly 2 bare @Preview annotation(s); found 1` |
| 12 | `val context = LocalContext.current` and `context.appDeps<ExerciseChartGraph.Factory>()` in `ExerciseChartFeature` | topology: readers not the exact 5, forbidden `LocalContext` and `appDeps<`, factory invocation not inside retained creation |
| 13 | delete `val exerciseChartGraphFactory: ExerciseChartGraph.Factory` and its KDoc from `AppRootDeps` | topology: exact root-factory flow requires the accessor |
| 14 | `val graph = factory.createExerciseChartGraph(screen)` before `rememberMetroStoreProcessor`, whose lambda then reads `graph.exerciseChartStore` | topology: `factory invocation must occur exactly inside retained Store creation` |
| 15 | a second `(context.applicationContext as AppRootDepsHolder).appRootDeps()` inside the admitted `remember(currentPhase.id) { … }` in `App.kt` | device: `UiAdmissionRaceTest`, as in 7.10 |
| 16 | hoist `val deps = remember(currentPhase.id) { …appRootDeps() }` above `if (admission.granted) {` | device: `UiAdmissionRaceTest`, as in 7.10 |
| 17 | identity helper `= asContribution<ExerciseChartGraph.Factory>().createExerciseChartGraph(…)` | topology: `must not bypass AppRootDeps via asContribution`, and the identity-helper fragment |
| 18 | archive-specific; no counterpart | — |
| 19 | S1: `windowStartMillis`'s body → `windowDays?.let { days -> now - days * 86_400_000L }` | host and Native: `MONTH_1 filter tracks calendar days across a DST spring-forward` and `MONTH_1 filter tracks calendar days across a DST fall-back`, and only them |
| 20 | `toReadout`'s caption separator `" · "` → `" - "` | host and Native: `readout for the record point carries the flag and the grouped whole-number value` and `readout off the record point has two caption parts and no flag`; Native: the scene's step 2 captions |
| 21 | the scene's `setContent { … }` body → `setContent { }` | Native: the scene, at its first node assertion |
| 22 | production `ExerciseChartScreen.kt`: the back button's `onClick = { consume(Action.Click.OnBack) }` → `onClick = { }` | Native: the scene's dispatch list |
| 23 | rename the scene method to `renamedSceneSubstitute` | Gradle GREEN, then the oracle: `feature:exercise-chart: expected exactly one testcase … found 0` |
| 24 | flip (XOR 0xFF) the last byte of `…golden_ExerciseChartGoldenTest_readoutRecord_light.png` | PNG assertion: blob drift, blob-set drift, the all-PNG and Paparazzi manifest drift |
| 25 | a compile-valid overlong assertion line in `commonTest/…/mvi/handler/NavigationHandlerTest.kt` | root detekt, alone: findings on that line only |

**New in 7.13:**

| # | Mutation | Scorer: expected RED |
| ---: | --- | --- |
| 26 | D5: delete the bare `@Preview` above `ChartEmptyStateDarkPreview` | topology: `…ChartEmptyState.kt must hold exactly 3 bare @Preview annotation(s); found 2` |
| 27 | D5: a bare `@Preview` above `fun NavGraphScope.exerciseChartGraph(` | topology: `…ui/ExerciseChartGraph.kt must hold exactly 0 bare @Preview annotation(s); found 1` |
| 28 | S4: the caption's `resourceWrapper.formatDayMonth(point.dayMillis)` → `point.dayMillis` | topology: `…ChartReadoutMapper.kt must read the ResourceWrapper.formatDayMonth date exactly 1 time(s); found 0` |
| 29 | D5: `val probe = resourceWrapper.formatDayMonth(0L)` after the scrub's `readoutCopy` guard | topology: `…ClickHandler.kt resolves exercise-chart copy through ResourceWrapper.formatDayMonth; use the private Compose resources` |
| 30 | 7.12's D3 over this entry: `loadChart`'s `readout = readout,` → `readout = ChartReadoutMapper.toReadout(…, copy = readoutCopy, resourceWrapper = resourceWrapper),` inside the lambda | topology: `updateStateImmediate in …CommonHandler.kt references 'resourceWrapper' inside the State lambda; resolve copy before it`, and the copy contract `'ChartReadoutMapper.toReadout(' exactly once; found 2` |
| 31 | F1: `val readoutCopy = ChartReadoutMapper.toReadoutCopy(newPoints, stringResolver)` moves to directly after `val prior = state.value` | topology: ``…CommonHandler.kt must resolve the readout copy before the staleness guard and the read of `prior` (F1)`` |
| 32 | D3: `launch { }` as the first line of `processScrub` | topology: `…ClickHandler.kt copy contract requires 'launch' exactly zero times; found 1` |
| 33 | D2: `private val chartLoader: ChartLoader,` → `private val chartLoader: CommonHandler,` | topology: `… requires 'private val chartLoader: ChartLoader,' exactly once; found 0` and `… requires 'CommonHandler' exactly zero times; found 1` |
| 34 | D2: delete `bindChartLoader` and its KDoc from `di/ExerciseChartGraph.kt` | topology: `…di/ExerciseChartGraph.kt copy contract requires 'val CommonHandler.bindChartLoader: ChartLoader' exactly once; found 0` |
| 35 | D2: delete `chartLoader.loadChart(selected)` from `processPresetSelect` | topology: `… requires 'chartLoader.loadChart(' exactly three times; found 2`; host and Native: `OnPresetSelect changes state KEEPS emptyReason and delegates load`, and only it |
| 36 | the argument rule: `toReadoutCopy`'s set plural drops its format argument (`getPluralString(Res.plurals.feature_exercise_chart_readout_sets, count)`) | host and Native: at least the mapper suite's three readout identities throw at the strict fake's argument check; Native: the scene's C1 holds `%1$d sets` |
| 37 | the argument rule: the footer's `value_reps` read drops its format argument | host and Native: `weightless footer is a reps plural over the value with no separable unit`, and only it; Native: the scene's F3 holds `%1$d rep` |
| 38 | P1: EN `readout_sets` one item `%1$d set` → `%d set` | placeholder rule: key `feature_exercise_chart_readout_sets[one]` token `'%d'`; EN catalog mismatch |
| 39 | S3: `import org.jetbrains.compose.resources.StringResource` in `mvi/model/ChartMetricUiModel.kt` | topology: `…ChartMetricUiModel.kt carries a resource handle/lookup 'StringResource' in a Store/domain payload`, and the same for `'.resources.'` and `'StringRes'` |
| 40 | `implementation(project(":core:data:database"))` in commonTest's dependencies | topology: `feature:exercise-chart: build must not depend on :core:data:database in any source set` |
| 41 | S1: `private val probe = java.time.LocalDate.MIN` in `ChartPointsAnimationTest` | topology: `…ChartPointsAnimationTest.kt names java.* in shared code; use kotlinx-datetime (S1)` |
| 42 | D4: `git mv` the parity suite to `src/commonTest/kotlin/…/domain/` | topology: manifest `missing` and `extra`; test identities drifted in the androidHostTest path (`missing=` the four names); `… contains forbidden test API 'org.junit'`; `… must not read core:data:database or its fixtures; only the host parity suite reads PrRuleFixture (D4)`; and `the host parity suite … is missing` |
| 43 | a 69th `@Test`, `` `a sixty-ninth identity nobody declared` ``, in `NavigationHandlerTest` | topology: test identities drifted, `unexpected=['a sixty-ninth identity nobody declared']` |
| 44 | S7: ``@DisplayName("the indicator travels on `travel`, and on the scale's own instance")`` between `@Test` and `fun indicatorUsesTravel()` | topology: test identities drifted in `…MetricTabsMotionTest.kt`, `missing=['indicatorUsesTravel']` |
| 45 | in `ExerciseChartExtensionIdentityTest`, the second identity's `appGraph.exerciseChart("ex-1")` → `appGraph.exerciseChartGraphFactory.createExerciseChartGraph(Screen.ExerciseChart(exerciseUuid = "ex-1"))` | topology: `all five extension identities must reach the graph through the accessor` |
| 46 | `AppNavigationHost`: delete `factory = exerciseChartGraphFactory,` from the `exerciseChartGraph(` call | topology: `exact root-factory flow requires 'exerciseChartGraph(factory = exerciseChartGraphFactory,' once in …AppNavigationHost.kt` |
| 47 | EN `feature_exercise_chart_error_load_cta` `Try again` → `Try again.` | `verifyPaparazziDebug`: `screenLoadFailed`, both themes (pixel diffs), and only it; topology: EN catalog mismatch; Native: the scene's steps 1 and 3 |

Each Python-scored control (1–14, 17, 26–34, 35's topology half, 38–46 and 47's topology half, and
M1–M3, N1–N2, X1–X2 and G1–G2 of Section 12.0) was dry-run once against the simulated exit tree
while this document was written, and each produced exactly its named result, without a traceback.
That dry run is not evidence. The implementation's fresh runs are.

Scorers:

- **Python, the topology script as CI runs it:** 1–14, 17, 26–34, 35's topology half, 38–46, and
  47's topology half;
- **PNG:** 24;
- **Gradle:**
  - `:feature:exercise-chart:testAndroidHostTest :feature:exercise-chart:iosSimulatorArm64Test
    --continue` for 19–22, 35's Gradle half, 36, 37 and 47's Native half;
  - `:feature:exercise-chart:verifyPaparazziDebug` for 47's golden half;
  - the thirteen-module Native command and the oracle for 23;
  - root detekt, alone, for 25;
- **device:** 15 and 16 (`UiAdmissionRaceTest` on the recorded serial, fresh XML).

## 14. STOP conditions

7.12 Section 14 applies, read with Section 0.3. In addition, stop if:

- `5de43879a` is not an ancestor of the base, or the target or any Section 9.2 path drifted from
  the discovery baseline in a way this document does not describe;
- commit 1's proof (Section 12.0) shows any differing byte, or an N, X or G row deviates;
- D2, D3 or F1 needs anything beyond Sections 5.2 and 5.5: another production file, a changed
  identity, stub or assertion in commit 2, an `ExerciseChartGraph` change beyond the one binding,
  or a suspension point between the staleness guard and the write;
- the copy port needs any of these: a resource handle, `ResourceWrapper` or `StringResolver` in a
  payload file; resource resolution or a resolver reference inside a State lambda; a launch or a
  suspension in the scrub; a change to an Event, `ChartReadoutUiModel` or
  `ChartFooterStatsUiModel`;
- any of the 16 resolver-dependent identities needs an assertion change, or a strict fake cannot
  reproduce today's stubs;
- S1's port changes a value that any identity asserts in commit 2, or an API of Section 5.6 does
  not resolve against kotlinx-datetime 0.8.0 and the Kotlin 2.4.10 stdlib, in commit 2's classic
  module or in commonMain;
- either DST identity fails on Native: record the zone, `now`, the computed boundary and the
  entry's millis on both targets;
- the Native scene's EN catalog differs from Section 3.4 in any value, or step 2's outputs differ
  from its table;
- the canvas presses or `SetText` cannot run on the simulator (Section 11.4);
- Native or Android compilation of commonTest needs `core:data:database`, or
  `"androidHostTestImplementation"(project(":core:data:database-test"))` does not resolve or the
  parity suite cannot reach `PrRuleFixture` (D4);
- the androidHostTest golden test changes any of the 30 PNG blobs;
- Metro rejects D2's binding, or the generated extension needs `CommonHandler`'s constructor
  public;
- the module's device test fails on the recorded serial;
- the KMP iOS kit smoke job times out on any PR head;
- detekt reports anything in the moved tests that PR-L does not cover. The fix is never a
  suppression;
- `RouteReachabilityTest` or `StoreRetentionTest` fails on the recorded serial.

A STOP produces a measured note and, where needed, an amendment proposal. It never widens the
implementation silently.

## 15. Signed, bisect-green commit plan

Five English Conventional Commits (S11). Each is signed, GitHub-verified and green on its own tree:

1. **ci(kmp): count bare previews and formatDayMonth in the shared gate** — D5 only (Section 12.0):
   the field, the regex, the check and archive's allowance, and nothing else.
2. **refactor(exercise-chart): port dates to kotlinx-datetime and resolve the readout copy per
   load** — in `src/main`, `src/test` and `src/androidTest`, before any move: S1 (Section 5.6:
   production, the eight `src/test` suites that use `java.time`, the device test, and the classic
   build's `implementation(libs.kotlinx.datetime)`), D2 (Section 5.5), D3 with F1's order
   (Section 5.2, with `resourceWrapper` in the resolver's places and `System.currentTimeMillis()`
   in place), S3 (with `R.string` ints) and S10. The suites take S1's fixtures, D2's mock and D3's
   fixtures; no identity, stub or assertion changes. Nothing else changes.
3. **refactor(kmp): share exercise-chart feature entry** — the module (Sections 3–11) and the six
   app paths. It also carries the minimal topology delta that keeps this tree green (precedent 7.8
   G1): the `MODULES` entry, and the exercise-chart reader removed from
   `EXPECTED_APP_DEPS_READERS`.
4. **ci(kmp): gate shared exercise-chart feature** — `EXERCISE_CHART_ENTRY` with its two checks, the
   Native oracle entry, and the workflow lists and comments.
5. **docs(kmp): record Phase 7.13 evidence** — this document's Status line, which becomes
   `**Status:** COMPLETE — implemented by the PR whose merge brought this line to dev.`; Section
   19; and the documentation edits of Section 9.2. Sections 0–18 stay byte-identical.

Each commit is made first. Its gates then run on the committed tree, and so do the controls (after
commit 4) and the repository gates. A fix is a new signed commit inside Section 9, and every gate it
can reach is re-run. Nothing is amended, squashed or reordered.

## 16. Exit criteria

7.12 Section 16 applies, with:

- commit 1's proof, and commit 2's green classic module with no `java.` token;
- the 88-path topology and two private catalogs;
- 33 identifiers, changed only by P1's 12 values;
- copy outside composition only through `StringResolver`, `ResourceWrapper` only for the one
  day-month date (the copy contract and the allowance), a scrub that resolves no copy, and F1's
  order;
- D2's seam with no identity changed;
- 68 identities on host and Native, 4 parity identities on host, 69 Native tuples, and both DST
  identities green on both targets;
- 30 golden cases with unchanged blobs and the relative PNG manifest proof;
- 7 device identities from androidDeviceTest;
- the three production suppressions and no test suppression;
- the explicit shape-B factory flow, with all five identities through the accessor;
- no direct `core:data:database` edge, and `core:data:database-test` read by the host parity suite
  only;
- the topology entry, oracle and workflow at thirteen modules;
- readers 6 → 5;
- every control of Section 13 evidenced;
- the Section 9.2 documents updated.

## 17. Remaining ordered roadmap

After 7.13 the frontier is remeasured. By today's scan the next entries are single-training (two
State lambdas without `.copy(`, four synchronous copy paths in click handlers, the activity
`BackHandler`, and a production `core:data:database` edge), exercise (photo picker and permissions;
four use cases, for which 7.12's D2 is the template), live-workout (the largest; eleven lambdas
without `.copy(`, four that resolve copy, and two handler-to-handler injections, for which this
phase's D2 is the template), and settings, which needs `feature:app-dialogs:api` in KMP first and
so comes after the app-dialog boundary. Then come app/common and the permanent iOS host. A
throwaway iosApp remains forbidden.

Separate follow-ups (S13 records the new ones in tech-debt.md): runtime RU catalog coverage, a typed
`rememberMetroStoreProcessor`, a portable date formatter, a non-suspend `updateStateImmediate`
lambda, the locale-list remedy (a maintainer decision), `core:data:exercise`'s leaky
`PlanSetDataModel`, F1's pre-existing window, D4's option B, and the handler-seam template.

## 18. Authorization

The maintainer's GO of 2026-10-18 locked D1–D5 and accepted S1–S13. Starting the implementation
with this document accepts its wording of them, S10's widening included, and authorizes two things:

- landing this document on dev;
- implementing it as one PR into dev, inside Section 9.

Merge stays the maintainer's.
