# KMP Phase 7.4 — `core:ui:start-mode` becomes the first shared UI leaf

**Status:** IMPLEMENTED LOCALLY; DELIVERY IN PROGRESS. The maintainer supplied the explicit
implementation GO and later authorized the single test-only compatibility correction recorded
below. PR CI/review evidence remains delivery evidence; the maintainer still owns the merge.

---

## 0. Authority and entry condition

This specification is measured from `dev` commit
`19a6191b5189e167e9e95a6cc3912c48197aafaf`, the verified merge commit for Phase 7.3 closeout
PR #263. It refines the sequence in `documentation/kmp-migration-assessment.md` using the
contracts established by Phases 7.1–7.3 and the current repository state.

The implementation agent must re-read `AGENTS.md`, this file, the Phase 7.1 UI-kit specification,
`home-start-card.md`, `testing.md`, and `ci-cd.md` from the implementation baseline before making
changes. If `dev` has moved, rebase first and repeat the baseline measurements below. Any material
change to the dependency frontier is a STOP, not implicit permission to broaden this phase.

The active repository ruleset at specification time is `all` (id `8116593`, selector `~ALL`). It
requires signed commits and the stable contexts `KMP iOS kit smoke` and `Build and Unit Tests`.
Neither context name nor the ruleset may change in this phase.

## 1. Decision and exit claim

Phase 7.4 converts **only** `core:ui:start-mode` from the classic Android Compose-library shape to
the repository's KMP Compose-library convention.

At exit, the module's three production Kotlin files and its localized strings are common code;
Android still renders the same public composables through the unchanged Home and Settings
consumers; Paparazzi still verifies the same two images byte-for-byte; and an iOS-simulator CMP UI
test renders the production sheet content, reads the migrated resources, observes the selected
state, and dispatches one real row click.

This is the next dependency-frontier step because the module:

- depends only on the already-shared `core:ui:kit`;
- has no production `android.*`, `java.*`, or `javax.*` dependency;
- exposes one bounded component used by two Android features;
- already owns a visual parity gate; and
- is blocked from common code only by Android resource access and the classic source-set layout.

The phase intentionally does not combine `core:ui:start-mode` with `core:ui:plan-editor`.
`plan-editor` resource IDs currently cross its module boundary into the Plan Editor, Exercise, and
Single Training feature handlers/screens. Replacing those integer Android resource contracts with
CMP resources requires a separate presentation/resource ownership decision. Bundling it here
would turn a mechanical leaf conversion into a multi-feature API redesign.

## 2. Measured baseline

All measurements in this section are from the pinned `dev` commit above.

| Surface | Baseline |
| --- | --- |
| Gradle shape | `convention.composeLibrary` + Paparazzi + shared golden harness |
| Production Kotlin | 3 files, 181 physical lines under `src/main/kotlin` |
| Platform imports | no `android.*`, `java.*`, or `javax.*`; two files use Android `R`/`androidx.compose.ui.res.stringResource` |
| Resources | 9 English keys + the same 9 Russian keys under `src/main/res` |
| Unit tests | 1 catalog-order test under `src/test/kotlin` |
| Golden tests | 1 parameterized RU test, 2 executed themes/images under `src/test` |
| Direct consumers | `feature:home` and `feature:settings` |
| Production dependency | `core:ui:kit` only |
| Public behavior | nullable selection, four ordered rows, selected-only check, row callback |
| Repository visual baseline | 456 PNGs across 13 live golden gates |
| Canonical device baseline | Smoke 44 discovered / 41 executed / 3 named skips / 0 failures; Regression 81/81 |

The load-bearing source blobs are:

| File | Git blob |
| --- | --- |
| `StartCardModeName.kt` | `c27165ccf65722585bbac99b82316c806878f008` |
| `StartCardModeSheet.kt` | `b98aa250a1ce79b3ee558f3e7595505485b1e985` |
| `StartCardModeUi.kt` | `013fa241a6205c2feee8187d07ca026bf1bffe7e` |
| English `strings.xml` | `6fdbca330771e569006cc9b61529ba571425ec66` |
| Russian `strings.xml` | `64a636b6d23e4e00ef07eaf4aa552d6207924c19` |
| `StartCardModeCatalogTest.kt` | `07756ee072af33385bd7c4a4805557ec6d420cc3` |
| `StartCardModeSheetGoldenTest.kt` | `ce78559f675eb1b084d180e64b6b14e882d8b66d` |
| dark golden | `a0033777c21592f18114f68166c73afde658b0e9` |
| light golden | `f92047018a20475dfaefa86d05fab7087d2796d0` |

Before implementation, record fresh SHA-256 hashes for both PNGs and prove the current focused
unit/golden tasks execute the expected cases. Git blob identity is the specification anchor;
SHA-256 is the implementation move/copy oracle.

The enum order is part of behavior, not incidental declaration order:

1. `WEEK`;
2. `DAYS_SINCE_LAST`;
3. `LAGGING_GROUPS`;
4. `FORGOTTEN_TRAINING`.

`StartCardModeSheetContent` renders `entries` as-is. The existing catalog test therefore remains
the order oracle after it moves to common tests.

## 3. In scope

1. Replace `convention.composeLibrary` with `convention.kmpComposeLibrary` in
   `core/ui/start-mode` while retaining Paparazzi and the shared golden gate.
2. Move all three production Kotlin files with history into `commonMain`.
3. Move the English and Russian XML resources verbatim into CMP `composeResources`.
4. Replace Android `R` and Android `stringResource` calls with the generated CMP `Res` accessors
   and `org.jetbrains.compose.resources.stringResource`.
5. Move the catalog-order test to `commonTest` and use `kotlin.test`.
6. Move the existing golden test and both PNGs to `androidHostTest`, preserving the PNG bytes and
   the two existing test identities.
7. Add exactly one `iosTest` production-scene test for rendering, resources, selection semantics,
   and callback dispatch.
8. Extend the existing required native workflow and its XML identity validator without renaming
   the required context.
9. Add a small reusable shared-UI source-topology oracle, initially pinning this module's exact
   source/resource layout, and run it under `Build and Unit Tests`.
10. Update only documentation made stale by the completed implementation and append measured
    evidence to this file.
11. Update only
    `feature/home/src/test/kotlin/io/github/stslex/workeeper/feature/home/ui/components/HomeStartCardModeLabelTest.kt`
    to remove its legacy dependency on the module-local Android resource class. The test must
    resolve the expected
    `DAYS_SINCE_LAST` label through the existing public `startCardModeName(StartCardModeUi)`
    composable inside its own composition and retain its complete behavioral oracle.

## 4. Explicit non-goals

This phase does not:

- convert `core:ui:plan-editor`, `core:ui:test-utils`, any feature module, or `app:common`;
- introduce an `iosApp`, Xcode project, UIKit controller, `UIWindow`, or iOS golden corpus;
- claim UIKit/Metal rendering, app launch, or a permanent iOS host;
- change Home or Settings state, domain mapping, persistence, navigation, sheet ownership, or copy;
- change the four modes, their order, their labels/descriptions, test tags, or null-selection
  semantics;
- export the module's generated `Res` class as a consumer API;
- add `androidMain`, `iosMain`, or `androidDeviceTest` production/test shims;
- migrate the Android-only `core:ui:test-utils` host/device infrastructure;
- change Phase-5 runtime/recovery publication, admission, retirement, replacement, journal, or
  recovery semantics;
- change dependencies or tool versions, global compiler policy, rulesets, required-context names,
  baselines, or suppressions; or
- clean up unrelated comments, APIs, resources, tests, or build logic.

The Section 3.11 exception is deliberately test-only and file-exact. It exists solely because the
baseline Home integration test imported the Android resource class removed by this migration. It
does not authorize any other Home/Settings test or production change, does not expose generated
CMP resources, and must not be generalized to later migrations. Home and Settings production
remain byte-identical to the pinned implementation baseline.

## 5. Required source-set shape

The final committed module shape is exact:

| Source set | Required files |
| --- | --- |
| `commonMain/kotlin` | `StartCardModeName.kt`, `StartCardModeSheet.kt`, `model/StartCardModeUi.kt` |
| `commonMain/composeResources/values` | English `strings.xml` |
| `commonMain/composeResources/values-ru` | Russian `strings.xml` |
| `commonTest/kotlin` | `model/StartCardModeCatalogTest.kt` |
| `androidHostTest/kotlin` | `golden/StartCardModeSheetGoldenTest.kt` |
| `androidHostTest/snapshots/images` | the existing dark and light PNGs with unchanged names and bytes |
| `iosTest/kotlin` | exactly one new `StartModeSceneIosTest.kt` |

Use `git mv` for every existing source, XML, test, and PNG path. There must be no Kotlin under
legacy `src/main`, `src/test`, or `src/androidTest`; no resources under legacy `src/main/res`; and
no production Kotlin in `androidMain` or `iosMain`. Empty placeholder files or no-op actuals are
forbidden.

The two PNG filenames remain:

- `io.github.stslex.workeeper.core.ui.start_mode.golden_StartCardModeSheetGoldenTest_modeSheet_dark.png`;
- `io.github.stslex.workeeper.core.ui.start_mode.golden_StartCardModeSheetGoldenTest_modeSheet_light.png`.

The topology oracle must use an explicit allowlist rather than counts alone. It must reject legacy
paths, unexpected Kotlin source sets, unexpected production files, Android/Java/Javax imports or
Android `R`/`androidx.compose.ui.res` in `commonMain`, missing CMP resource directories, and any
remaining `src/main/res`. Design it as a manifest that later shared-UI leaves can extend; do not
modify the Phase 7.3 MVI-specific topology oracle.

## 6. Resource and API contract

### 6.1 Resource move

Move the XML files verbatim:

| Before | After |
| --- | --- |
| `src/main/res/values/strings.xml` | `src/commonMain/composeResources/values/strings.xml` |
| `src/main/res/values-ru/strings.xml` | `src/commonMain/composeResources/values-ru/strings.xml` |

All 9 keys and both locales' text are exact compatibility data. Do not normalize punctuation,
spacing, capitalization, or Russian copy. Production composition must call
`org.jetbrains.compose.resources.stringResource` with generated `Res.string.*` accessors; it must
not use blocking `getString`, an Android resource wrapper, or a copied string catalog.

The generated resource class remains an internal implementation detail because no public
`StringResource` value crosses the module boundary. Do not set `publicResClass = true` or make
Home/Settings import this module's `Res` merely to mimic `core:ui:kit`.

The file-exact Home test exception calls the already-public `startCardModeName` composable to
obtain its integration expectation. It may not import `Res`, expose `Res`, introduce a resource-ID
API, or duplicate localized text.

### 6.2 Public Kotlin surface

Preserve, source-compatibly:

- `StartCardModeUi` and its four entries in exact order;
- `startCardModeName(mode: StartCardModeUi): String`;
- `StartCardModeSheet(selected, onSelect, onDismiss, modifier)`;
- `StartCardModeSheetContent(selected, onSelect, modifier)`;
- nullable `selected`: `null` means no check is rendered;
- the `StartCardModeSheet`, `StartCardModeRow_<MODE>`, and
  `StartCardModeCheck_<MODE>` semantics tags;
- the existing preview subjects and theme selections; and
- exactly one callback invocation for one row click.

No consumer adapter, overload, resource-ID façade, expect/actual bridge, or platform default is
allowed. Direct Home and Settings source should require no production change.

## 7. Build and dependency contract

The build file keeps the Paparazzi plugin and `golden-gate.gradle.kts`, but uses the KMP Compose
convention. Dependencies belong to the narrowest source set:

| Configuration | Dependency |
| --- | --- |
| `commonMain` | `implementation(project(":core:ui:kit"))` |
| `commonTest` | `implementation(kotlin("test"))` |
| `iosTest` | `implementation(kotlin("test"))`, `implementation(libs.cmp.ui.test)` |
| `androidHostTest` | `implementation(project(":core:ui:golden-harness"))` |

The convention already supplies CMP runtime/foundation/material3/ui/tooling-preview/resources,
the Android and `iosSimulatorArm64` targets, Android-host JUnit 5, and the classic CI task aliases.
Do not duplicate those dependencies. Do not add `androidMain`, `iosMain`, device-test, or global
convention dependencies.

The module's public Kotlin API does not expose a new dependency type, so the kit edge remains
`implementation`. No JVM-default override or bespoke JVM ABI fixture is required for this
function/enum/composable leaf; unchanged source signatures plus fresh compilation of both direct
consumers are the compatibility proof. If conversion changes the callable JVM surface or forces a
consumer adapter, STOP and specify that change separately.

## 8. Test contract

### 8.1 Common catalog test

Move `StartCardModeCatalogTest` to `commonTest`, replace JUnit imports with `kotlin.test`, and keep
the same exact four-entry/order assertion. It must execute on both the Android-host aggregate and
the iOS simulator through the default KMP hierarchy; a copied iOS-only catalog test is forbidden.

### 8.2 Android-host golden parity

Move `StartCardModeSheetGoldenTest` to `androidHostTest` without changing its subject, locale,
surface, selected mode, theme parameterization, or test method/class identity. The existing two
PNGs move with it and retain their filenames and SHA-256 bytes.

The module's golden gate must report exactly two current executions and exactly two expected
images. The repository-wide gate remains 456 PNGs across 13 live modules. A re-record, tolerance
change, renamed identity, visually equivalent replacement, or altered PNG metadata is not a
mechanical move and is outside scope.

### 8.3 Native production scene

Add exactly one iOS-simulator test with this required identity:

`io.github.stslex.workeeper.core.ui.start_mode.StartModeSceneIosTest.sheetRendersMigratedCatalogAndDispatchesSelection`

Use CMP UI test v2 `runComposeUiTest` and production code:

- set content to `AppTheme { StartCardModeSheetContent(...) }`;
- pass `WEEK` as the selected value;
- disable auto-advance, advance one frame, re-enable it, and wait for idle;
- resolve the production migrated title and all four names from the module's generated resources
  and assert they are displayed;
- assert `StartCardModeCheck_WEEK` exists in the unmerged tree and an unselected mode's check does
  not;
- perform a click on one production `StartCardModeRow_<MODE>` node; and
- assert the callback observed exactly that mode exactly once.

The test proves Kotlin/Native compilation, CMP resource loading, production composition, a real
native headless `ComposeScene` frame, semantics, selection rendering, and event dispatch. It does
not prove `ComposeUIViewController`, UIKit, Metal, app startup, or a permanent iOS host.

Do not add a planted sentinel, duplicate production content in the test, weaken assertions to
existence of the root only, or count a common enum test as the UI scene.

### 8.4 Consumer and device parity

Compile and run the existing focused unit tests for `feature:home` and `feature:settings`. Their
production source and dependency declarations remain unchanged. The only allowed consumer diff is
the Section 3.11 Home test correction, which removes the vanished Android `R` dependency through
the existing public naming composable without weakening its assertions. Existing host/device
coverage that distinguishes `selected = null` from a concrete selection remains authoritative;
do not move those host-owned assertions into this leaf.

Run the canonical Smoke and Regression device suites fresh when device infrastructure is
available. Membership/execution remains exactly Smoke 44/41/3 and Regression 81/81. This phase
adds no device identity.

## 9. CI contract

### 9.1 Stable required contexts

The active ruleset continues to require exactly:

- `Build and Unit Tests`;
- `KMP iOS kit smoke`.

Do not rename either context. The historical native name remains stable even though the job now
also covers navigation, MVI, and start-mode.

### 9.2 Build job topology oracle

Add `.github/scripts/assert_kmp_ui_source_topology.py` and call it from `Build and Unit Tests`
before compilation. Its initial manifest covers `core/ui/start-mode` only and enforces Section 5.
The script must print the checked module/files on success and actionable exact path/import failures
on red. It must be deterministic, use repository paths, and compare the explicit allowlist rather
than only comparing totals. The compensating-count/path mutation in Section 12 proves this.

### 9.3 Native required job

Extend the existing macOS Gradle invocation with:

`:core:ui:start-mode:iosSimulatorArm64Test`

Upload `core/ui/start-mode/build/test-results/iosSimulatorArm64Test/` with the existing native XML
artifact even when the test step fails. Extend `.github/scripts/assert_kmp_ios_smoke.py` with the
exact class/method identity in Section 8.3. The validator must require exactly one executed,
zero skipped, and zero failed matching case for this module, while retaining every existing kit,
navigation, and MVI identity.

Do not validate by workflow exit code, substring-only log matching, or aggregate count. Reuse the
existing per-suite XML parser so compensating over/under-count suites remain red.

## 10. Compatibility invariants

The implementation must preserve all of the following:

- four enum values and their declaration/render order;
- 9 resource keys and exact EN/RU values;
- public function names, parameters, defaults, nullability, and callback types;
- `null` selection renders no check;
- a concrete selection renders exactly its check;
- all current semantics tags;
- Home and Settings production behavior and dependency direction;
- both PNG filenames and bytes;
- 456 repository goldens and 13 live golden gates;
- Smoke 44/41/3 and Regression 81/81;
- active ruleset id/selector and both required-context names; and
- no Android/Java/Javax dependency in common or native production.

Comments may be moved with their code and adjusted only when a path/platform statement becomes
false. Preserve durable guard explanations. Do not add migration history to production code.

## 11. Required verification

Every forced gate uses `--rerun-tasks --no-build-cache`; use `--no-configuration-cache` where the
repository's Paparazzi/native guidance requires it. Record test identities/counts from current XML,
not exit codes or cached console summaries.

### 11.1 Pre-change

Before editing:

1. prove the pinned/rebased `dev` and clean worktree;
2. inventory the exact source/resource/test/PNG paths and both direct consumers;
3. record SHA-256 for both PNGs;
4. run the current focused assemble, unit, and golden tasks fresh;
5. record the catalog and golden identities/counts; and
6. record the repository golden and device-suite baselines.

### 11.2 Focused positive gates

After conversion, run fresh:

- `:core:ui:start-mode:assembleDebug`;
- `:core:ui:start-mode:testDebugUnitTest`;
- `:core:ui:start-mode:verifyPaparazziDebug`;
- `:core:ui:start-mode:lintDebug`;
- `:core:ui:start-mode:iosSimulatorArm64Test` on macOS/Xcode infrastructure;
- the new shared-UI topology oracle; and
- Home and Settings assemble/unit tasks.

Inspect XML to prove the common catalog is not dropped, the Android-host golden executes two
themes, and the native production-scene identity executes exactly once with no skip/failure.

### 11.3 Repository gates

Run the repository commands required by `AGENTS.md` and the touched surfaces:

- `assembleDebug`;
- `assembleDebugAndroidTest`;
- `verifyPaparazziDebug`;
- `:lint-rules:test`;
- `detekt`;
- personal-data gate;
- `lintDebug`;
- `testDebugUnitTest`.

Run Smoke and Regression fresh when device infrastructure is available and retain their exact
membership/execution evidence.

### 11.4 Final diff proof

At implementation PR head, prove:

- the branch is based on the merged specification baseline;
- only the planned module, CI scripts/workflow, stale documentation, and the exact Section 3.11
  Home test changed;
- the exact Section 5 source topology holds and legacy paths are absent;
- there is no Android `R`, `androidx.compose.ui.res`, or Android/Java/Javax import in common/native;
- every resource key/value matches the baseline and no Android resource copy remains;
- Home and Settings production files are unchanged and still directly consume the module;
- the two PNG filenames, sizes, and SHA-256 bytes match pre-change;
- all existing native identities remain in the validator;
- no dependency/version, ruleset, required-context name, golden tolerance, suppression, or
  baseline changed; and
- implementation commits are signed and the PR has zero unresolved review threads.

## 12. Mandatory known-negative controls

Use the repository mutation harness for UTF-8 in-place anchor replacements. It is not byte-safe
and cannot create/delete paths: for PNG or topology create/delete controls, copy every existing
affected file to a scratchpad first and restore it with `cp` in `finally`. For a deliberately new
phantom path, prove it was absent, create it with `cp`, and remove that exact path in `finally`.
Never use `git checkout` for recovery. In every case, establish fresh GREEN, mutate one thing,
require the named RED, restore exact bytes/tree shape, and re-run fresh GREEN. Do not retain
mutation artifacts.

| Mutation | Required RED oracle |
| --- | --- |
| corrupt one pixel/byte in the dark PNG | module/repository Paparazzi verification |
| add a phantom third PNG | golden liveness/inventory gate |
| mutate one Russian sheet string | RU Paparazzi golden |
| reorder two enum entries | common catalog test |
| replace the native production scene with blank content | native semantics/resource assertions |
| suppress the row callback or invoke it twice | native callback assertion |
| rename the required native method without rerunning Gradle | Native XML identity validator |
| leave one Kotlin file in `src/main` or one XML file in `src/main/res` | shared-UI topology oracle |
| add one Android import or `R.string` access to `commonMain` | topology oracle and Native compile |
| swap an allowlisted source path while preserving the file count | shared-UI topology oracle |
| add a detekt violation in common/native test code | module/root detekt |

Each failure must be attributed to the intended oracle. A mutation that fails earlier for syntax,
missing checkout state, or a stale artifact does not prove the named gate.

## 13. STOP conditions

Stop implementation and report instead of widening scope if:

- `dev` changed the measured module, resource ownership, consumer graph, or required checks;
- a current resource key/value, enum order, public signature, test tag, or behavior must change;
- Home or Settings production code needs an adapter or behavioral edit;
- `core:ui:plan-editor`, `app:common`, `core:ui:test-utils`, or any feature file other than the
  exact Section 3.11 Home test must change to compile;
- a required dependency lacks an `iosSimulatorArm64` variant;
- the real production sheet cannot render and dispatch under the native CMP test runner;
- Android `Context`, `Resources`, resource IDs, Java, or an expect/actual platform shim is needed
  in common/native production;
- either existing PNG must be re-recorded or its bytes/identity change;
- any required task is `NO-SOURCE`, zero, stale, missing, duplicated, skipped, or sentinel-only;
- repository golden counts/gates or device-suite membership changes;
- a new dependency family/version, global convention edit, compiler-policy change, suppression, or
  baseline is needed;
- `iosArm64`, UIKit/Xcode-host, app signing, or a permanent `iosApp` becomes necessary;
- a ruleset or required-context name must change;
- Phase-5 runtime/recovery semantics enter the change; or
- production comments would need migration narration rather than durable invariants.

## 14. Documentation and comment budget

This file owns the migration rationale. During implementation:

- update `architecture.md` so the module map names `core/ui/start-mode` as shared KMP UI;
- update `testing.md` so it no longer describes start-mode as a classic `src/test` golden module
  and records its `androidHostTest` and native commands;
- update `ci-cd.md` so the stable native job's module list includes start-mode;
- update `home-start-card.md` only if a source-ownership/path statement becomes stale; do not
  rewrite unchanged behavior; and
- append exact implementation, positive-gate, negative-control, CI, review, and merge evidence to
  this specification in the appropriate delivery/closeout PRs.

Do not add a parallel migration note, changelog prose, generated report, or production history
comment.

### 14.1 Local implementation evidence

The implementation started from required base
`d8e4c3af968a6fc9ebf46c42718bafa85f8ffe19`, with `origin/dev` at that exact commit. The older
specification measurement anchor still had the same load-bearing source, resource, test, golden,
consumer, and required-check topology, so no scope adaptation was needed. Before editing, the
focused module tasks executed 49/49 assemble tasks, 86/86 unit tasks, and 87/87 Paparazzi tasks.
The catalog executed once and both golden identities executed with no skip or failure. The PNG
baseline was:

| Golden | Dimensions | Bytes | SHA-256 before and after |
| --- | --- | ---: | --- |
| dark | 1078 x 772 RGBA | 46,739 | `a58ab7d5b3d59110c878b3a75d668cd901c4f1174f98aefcc0a64dc9cecdf4a9` |
| light | 1078 x 772 RGBA | 46,856 | `a2049e21f916834bccd587bcb749b7daaf9ac9f031a6b307727768ac6651324d` |

The final local module has the exact ten-file Section 5 allowlist. All three production files,
both verbatim nine-entry locale catalogs, the common catalog test, the Android-host golden test,
and both PNGs moved with history; the one native production-scene test is new. Generated CMP
resources remain internal. Repository search finds no former start-mode Android `R` reference and
no Android/Java/Javax dependency in common/native code. All 94 Home and Settings production blobs
match the required base.

The baseline Home integration test was the one discovered compatibility dependency: it imported
the removed Android `R` class and therefore failed compilation after the otherwise complete leaf
migration. Under the maintainer's file-exact authorization, it now obtains the expected
`DAYS_SINCE_LAST` copy through `startCardModeName` in its own production-themed composition. Its
original class/method identity executes 1/0/0/0 and its complete null-label, clickable-head,
minimum-target, suppressed-body, transition, resolved-label, and resolved-body oracle remains.
The first full Detekt run then exposed import ordering in that same file; reordering only those
imports corrected it, the combined Home Detekt/unit rerun executed 186/186 tasks, and full Detekt
then executed 53/53 tasks successfully.

Fresh focused results were:

| Gate | Result |
| --- | --- |
| start-mode assemble / unit / Paparazzi / lint | 93/93, 88/88, 89/89, and 75/75 tasks executed |
| start-mode iOS simulator | 55/55 tasks; catalog 1/0/0/0 and exact scene 1/0/0/0 |
| Home assemble / unit | 109/109 and 182/182 tasks executed; corrected integration identity 1/0/0/0 |
| Settings assemble / unit | 120/120 and 200/200 tasks executed |
| shared-UI topology | exact ten-file allowlist accepted |
| required-job native invocation | 110/110 tasks; kit, navigation, MVI, and start-mode XML validator accepted |

The native identity is
`io.github.stslex.workeeper.core.ui.start_mode.StartModeSceneIosTest.sheetRendersMigratedCatalogAndDispatchesSelection`;
the XML target suffix is `[iosSimulatorArm64]`. The native scene uses the production resources and
sheet, while the common catalog and the two RU Android-host golden cases remain separate resource
mapping and localized-output gates.

Fresh repository gates all succeeded: `assembleDebug` 1157/1157,
`assembleDebugAndroidTest` 1937/1937, `verifyPaparazziDebug` 619/619,
`:lint-rules:test` 9/9, Detekt 53/53, `lintDebug` 1091/1091, and
`testDebugUnitTest` 1130/1130 tasks executed. The personal-data gate passed. Paparazzi retained
456 PNGs across 13 live golden gates. On the API 34 emulator, Smoke executed 2010/2010 Gradle tasks
and produced 44 discovered / 41 executed / the three named pending-feature skips / zero failures
or errors; Regression executed 2010/2010 tasks and produced 81/81 with zero skips, failures, or
errors. Both counts came from the 14 connected-test XML files freshly written by each run.

Every mandatory mutation established GREEN, the named RED, byte/tree restoration, and fresh
GREEN:

- changing a Russian title failed both RU golden identities;
- reordering the enum failed the common catalog test;
- blanking the production native scene failed the native scene identity;
- suppressing its callback failed the native callback assertion;
- changing the current Native XML method identity failed the exact-identity validator while all
  four module result sets were still inspected;
- leaving a legacy path and performing a same-count allowlisted-path substitution each failed the
  exact topology oracle;
- adding an Android import to common code failed the topology oracle (and the supplemental native
  compile rejected the unresolved platform import);
- changing a decoded dark-PNG pixel failed only the dark golden while light remained green;
- adding a proven-absent third PNG failed golden liveness at two cases versus three images; and
- adding a max-line-length violation to the native test failed module Detekt.

An alpha-byte PNG mutation that did not change rendered pixels and a source-only native method
rename checked against deliberately stale XML were rejected as non-observable attempts and were
not counted. The accepted PNG and path controls used scratch copies plus `cp` restoration in
`finally`; UTF-8 anchors used the mutation harness. Final hashes and topology match their exact
pre-control state, and no mutation artifact remains.

## 15. Implementation commit plan

Use signed Conventional Commits and keep every commit buildable:

1. `refactor(kmp): share start mode UI` — atomically convert the build, move production/resources,
   move common/host tests and PNGs, add the native test, and apply the exact Section 3.11 Home test
   compatibility correction;
2. `ci(kmp): gate shared start mode UI` — add the topology oracle and extend native workflow/XML
   validation; and
3. `docs(kmp): record Phase 7.4 evidence` — update only stale docs and append measured evidence.

If a split would leave resources, tests, or source sets uncompiled in an intermediate commit,
combine it with the owning conversion commit. Do not commit a failing intermediate state or
re-recorded golden.

## 16. Exit criteria

Phase 7.4 is complete only when all are true:

- the exact module topology in Section 5 is committed and enforced;
- all three production files compile from `commonMain` for Android and iOS simulator;
- the two locale files are CMP resources with all 9 keys/texts preserved;
- the public/behavior contract in Sections 6 and 10 is unchanged;
- the common catalog test executes on the intended KMP targets;
- both Android-host goldens execute and both PNGs remain byte-identical;
- the exact native production-scene test executes 1/0/0 and proves resources, selection, and click;
- Home and Settings compile/test without production changes;
- the stable native required job covers kit, navigation, MVI, and start-mode with exact XML
  identities;
- all focused, repository, visual, device, and known-negative gates are recorded green/red/green;
- rulesets, required-context names, versions, golden counts, and device membership are unchanged;
- documentation is current;
- implementation commits are signed, required checks are successful, and review threads are
  resolved; and
- the maintainer, not the implementation agent, performs the merge.

## 17. Boundary after this specification

This docs-only specification authorizes no implementation by itself. After it merges, the
maintainer must explicitly approve Phase 7.4 implementation.

Phase 7.4 does not pre-authorize a Phase 7.5 candidate. The next boundary requires fresh discovery
and a separate specification. The discovery must choose, rather than conflate:

- a `core:ui:plan-editor` resource/presentation contract that removes cross-module Android `R`
  ownership; or
- the first feature/root host slice once its graph and platform ownership are genuinely bounded.

The permanent iOS host still must enter through the real `app:common` dependency frontier. Its
first UIKit window must be permanent and covered by XCTest/XCUITest; a throwaway `iosApp` remains
out of scope. Phase-5 runtime/recovery remains separately specified.
