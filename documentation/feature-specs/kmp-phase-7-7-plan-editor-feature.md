# KMP Phase 7.7 — `feature:plan-editor` becomes a shared feature entry

**Status:** IMPLEMENTED FOR REVIEW — MAINTAINER MERGE REQUIRED

**Target branch:** `dev`

**Specification baseline:** `e2e18db1398ddeb997dbf1a4d66c7838bf6004fa` — verified merge
commit of the Phase 7.7 portable BackHandler convention prerequisite PR #271

**Original measured baseline:** `d52dd740b5e6154cb717c879be3bf59c76d47dac` — verified merge
commit of Phase 7.6 implementation PR #269

**Specification merge:** `8f522da568250f6adfc715b48b7780a78dac5d2d` — verified merge commit of the
documentation-only Phase 7.7 specification PR #270

**Discovery date:** 2026-08-29

**Prerequisite rebaseline date:** 2026-08-31

**Authorized implementation baseline:** `74878e68fb9d029b1661179542a4c9b8d68abb8b` — verified merge
commit of prerequisite rebaseline PR #272 and exact `origin/dev` at implementation entry

**Maintainer implementation GO:** 2026-08-31

---

## 0. Authority, entry gate, and authorization boundary

This document specifies the next bounded Kotlin/Compose Multiplatform increment. The original
documentation-only specification is merged. This prerequisite rebaseline authorizes no production
implementation: its merge records the corrected measured contract, and implementation still
requires a later, explicit maintainer GO.

The authority order for this measurement is:

1. live `origin/dev` at the exact SHA above;
2. `AGENTS.md`, `documentation/architecture.md`, `documentation/testing.md`,
   `documentation/ci-cd.md`, and `documentation/compose-state-discipline.md`;
3. `documentation/feature-specs/kmp-phase-2-probes.md`, the current KMP convention plugins,
   Phase 7.5, completed Phase 7.6, and merged BackHandler prerequisite PR #271;
4. `documentation/graph-extension-arc/HANDOFF.md` for the Metro shape-B contract; and
5. the checked-in topology, Native XML, and workflow gates.

`documentation/kmp-migration-assessment.md` was read as historical assessment evidence only. Its
old module counts and classifications do not override this live census.

### 0.1 Reproduced entry and prerequisite facts

| Claim | Reproduced evidence |
| --- | --- |
| Live target | GitHub branch ref `dev` = `e2e18db1398ddeb997dbf1a4d66c7838bf6004fa` |
| Phase 7.6 delivery | PR #269 is `MERGED`; implementation head `c1fb534afc4c662d3d9345eee6015cf134f3f6f2`; merged at `2026-08-29T13:29:24Z`; merge commit `d52dd740b5e6154cb717c879be3bf59c76d47dac` is the original measured baseline |
| Phase 7.7 specification | PR #270 is `MERGED`; specification head `9cf772d167921c66f07542e1a488dc1f42243391`; merged at `2026-08-29T22:02:22Z`; merge commit `8f522da568250f6adfc715b48b7780a78dac5d2d` |
| Triggering STOP | the first implementation attempt stopped before any production commit when `:feature:plan-editor:assembleDebug` failed only on unresolved `androidx.compose.ui.backhandler` / `BackHandler`; `192 actionable tasks: 192 executed` |
| Prerequisite delivery | PR #271 is `MERGED`; signed and GitHub-Verified head `3685abd808eca83ece26a6e5b0d85cf9cf8efda5`; merged at `2026-08-30T20:03:39Z`; merge commit is the live target SHA |
| Exact post-spec delta | GitHub comparison `8f522da568250f6adfc715b48b7780a78dac5d2d..e2e18db1398ddeb997dbf1a4d66c7838bf6004fa` is ahead by two commits, behind by zero, and changes exactly two files with one insertion each: the `cmp-uiBackhandler` catalog alias and its single `commonMainImplementation` convention edge |
| Target-boundary drift | zero feature, application, workflow, script, test, generated, documentation, ruleset, golden, or repository-setting path changed after the specification merge |
| Conflicting PR | live open-PR inventory is empty |
| Local preservation | prerequisite rebaseline discovery read the exact GitHub refs without modifying a production checkout |
| Native infrastructure | Xcode 26.6 (`17F113`), available iOS Simulator runtimes/devices, and Apple-silicon `iosSimulatorArm64` execution from the original measurement remain the applicable implementation environment |
| Android infrastructure | `emulator-5554`, Pixel 6 API 34, was available for the original focused six-case baseline; implementation must resolve and report the explicit live serial again |

The prerequisite's causal probe proved the missing module was the separate
`org.jetbrains.compose.ui:ui-backhandler` artifact, not ordinary CMP `ui`. With the probe held
byte-identical, Android moved from RED on only that API (`84 actionable tasks: 84 executed`) to
GREEN (`93 actionable tasks: 93 executed`); common metadata moved from the same RED
(`33 actionable tasks: 33 executed`) to GREEN with the same task count; and Native remained GREEN
(`30 actionable tasks: 30 executed`). Dependency resolution selected
`org.jetbrains.compose.ui:ui-backhandler:1.11.1` for common metadata, Android, and
`iosSimulatorArm64`.

PR #271 also reran all six current KMP Compose consumers on Android and Native. Native again
finished `161 actionable tasks: 161 executed`; the checked-in XML oracle reported the unchanged
51 tests, zero skipped, zero failed, and zero errored across kit (1), navigation (1), MVI (14),
start-mode (2), shared plan-editor (20), and image-viewer (13). The full repository, Android-test,
Paparazzi, lint-rules, Detekt, lint, unit, and personal-data gates passed. All three authoritative
GitHub contexts passed at the prerequisite head, including both Mockup Appearance Gate directions;
the local shell-gate directions remain explicitly unmeasured for the documented Python/Chrome host
reasons.

The original STOP is therefore closed at the generic convention owner. The exact Phase 7.7 target,
root, route/result, test, resource, CI, and PNG boundaries remain unchanged. No further prerequisite
is known, but this rebaseline is not implementation authorization.

### 0.2 Live protection and workflow facts

The active repository-wide ruleset is `all` (id `8116593`, condition `~ALL`). It requires signed
commits and the `Build and Unit Tests` and `KMP iOS kit smoke` status contexts. Its strict-status
policy is false. The `dev` ruleset (id `18553518`) is disabled, and the repository has no classic
branch-protection rule. `Mockup Appearance Gate` remains the separate always-on PR workflow for
non-`master` targets and is a required Phase exit gate even though it is not named in ruleset
8116593.

These three stable gate names must not change:

- `Build and Unit Tests`;
- `KMP iOS kit smoke`; and
- `Mockup Appearance Gate`.

The exact live task/script ownership is:

| Job | Current commands owned by the job |
| --- | --- |
| `Build and Unit Tests` | topology Python; `assembleDebug`; `assembleDebugAndroidTest`; `verifyPaparazziDebug`; `:lint-rules:test`; `detekt`; personal-data Python; `lintDebug`; `:core:ui:mvi:testAndroidHostTest` plus its identity oracle; `testDebugUnitTest` |
| `KMP iOS kit smoke` | `:core:ui:kit:iosSimulatorArm64Test`, `:core:ui:navigation:iosSimulatorArm64Test`, `:core:ui:mvi:iosSimulatorArm64Test`, `:core:ui:start-mode:iosSimulatorArm64Test`, `:core:ui:plan-editor:iosSimulatorArm64Test`, `:feature:image-viewer:iosSimulatorArm64Test`, then `assert_kmp_ios_smoke.py` and six result directories |
| `Mockup Appearance Gate` | `shell_gate.py --base <PR merge-base> -v` plus the permanent `--target f52462c7` known negative |

The live lint/rules boundary contains 48 tracked entries under `lint-rules`/`config`, with path-list
SHA-256 `5bffbb3f504684eac75f9872cd7905ca6ec38e37e15a7164169da9d0a1f12842`.
No ruleset, rule, baseline, task filter, or required-context change is authorized. The sole
suppression exception is the two test-file annotations enumerated in Section 11.1; no production
suppression or other suppression change is authorized.

## 1. Decision and bounded exit claim

Proceed with `feature:plan-editor`. It is still the smallest coherent remaining navigation entry,
all seven direct project dependencies already publish Android and `iosSimulatorArm64` variants,
and every Android-only seam has a repository-established common replacement.

The implementation may prove only this exit claim:

- `feature:plan-editor` applies `convention.kmpComposeLibrary` and retains Metro;
- all 23 production Kotlin files compile from `commonMain` for Android and
  `iosSimulatorArm64`;
- the exact 12-key EN/RU feature catalog is privately generated from CMP resources;
- all six existing suites and all 42 existing test identities execute on Android host and Native;
- one deterministic iOS production scene composes the real screen and proves resources, branches,
  and action dispatch;
- the route-bound Metro shape-B graph is reached through one explicit generation-owned root
  factory, not `Context.appDeps`;
- the route, Store, navigation actions, `Screen.PlanEditor` result channel, visible Android copy,
  and Android journeys remain behaviorally unchanged; and
- Android stays releasable with all 456 Paparazzi PNG entries byte/path identical.

This is a dependency and source-set migration, not permission to redesign the editor, global
resources, navigation, runtime generations, or the iOS application host.

## 2. Candidate selection — adversarial comparison

The live census of the 11 remaining navigation entries is:

| Feature | Prod files | Prod lines | Unit files | Unit lines | Device files | EN keys | PNGs |
| --- | ---: | ---: | ---: | ---: | ---: | ---: | ---: |
| `plan-editor` | **23** | **1,315** | 6 | 752 | 0 | **12** | **0** |
| `archive` | 25 | 1,409 | 6 | 503 | 1 | 25 | 14 |
| `all-trainings` | 31 | 1,795 | 10 | 1,041 | 1 | 26 | 50 |
| `all-exercises` | 34 | 2,062 | 11 | 1,471 | 1 | 30 | 52 |
| `past-session` | 29 | 2,378 | 8 | 2,067 | 0 | 20 | 30 |
| `home` | 39 | 3,081 | 15 | 1,924 | 0 | 35 | 42 |
| `exercise-chart` | 41 | 3,214 | 10 | 2,314 | 1 | 31 | 30 |
| `settings` | 51 | 3,324 | 12 | 2,638 | 1 | 65 | 12 |
| `single-training` | 39 | 3,635 | 6 | 1,582 | 1 | 39 | 12 |
| `exercise` | 46 | 4,141 | 13 | 2,890 | 2 | 53 | 48 |
| `live-workout` | 56 | 6,429 | 25 | 6,857 | 1 | 96 | 60 |

`archive`, the next-smallest plausible slice, has 249 fewer unit-test lines, but also brings paging,
one instrumented source set, and 14 owned goldens. `plan-editor` has the smallest production file
and line boundary, the smallest catalog, no owned PNG, and its reusable `core:ui:plan-editor`
dependency was already shared in Phase 7.5. Its larger existing unit suite is an asset because all
42 identities are portable. The target's repositories and UI dependencies are already KMP. It
requires no sibling feature, runtime, recovery, platform-host, version, compiler-policy, or
convention-plugin change.

Changing the candidate would therefore increase the boundary rather than reduce risk. If this
comparison changes before implementation, STOP instead of silently choosing another feature.

## 3. Measured baseline

All measurements in this section are from the exact baseline SHA and were reproduced locally.

### 3.1 Exact target manifest and physical lines

| Current `src/main` production Kotlin file | Lines |
| --- | ---: |
| `di/PlanEditorFeature.kt` | 37 |
| `di/PlanEditorGraph.kt` | 39 |
| `di/PlanEditorHandlerStore.kt` | 9 |
| `di/PlanEditorHandlerStoreImpl.kt` | 14 |
| `di/PlanEditorScope.kt` | 8 |
| `domain/PlanEditorInteractor.kt` | 26 |
| `domain/PlanEditorInteractorImpl.kt` | 70 |
| `domain/mapper/PlanEditorDomainMapper.kt` | 48 |
| `domain/model/ExerciseTypeDomain.kt` | 7 |
| `domain/model/PlanEditorLoadResult.kt` | 13 |
| `domain/model/PlanSetDomain.kt` | 8 |
| `domain/model/SetTypeDomain.kt` | 8 |
| `ui/PlanEditorGraph.kt` | 76 |
| `ui/PlanEditorScreen.kt` | 242 |
| `ui/mapper/PlanEditorMapper.kt` | 48 |
| `ui/mvi/handler/ClickHandler.kt` | 213 |
| `ui/mvi/handler/CommonHandler.kt` | 74 |
| `ui/mvi/handler/EditorHandler.kt` | 29 |
| `ui/mvi/handler/InputHandler.kt` | 36 |
| `ui/mvi/handler/NavigationHandler.kt` | 26 |
| `ui/mvi/store/DialogState.kt` | 34 |
| `ui/mvi/store/PlanEditorStore.kt` | 157 |
| `ui/mvi/store/PlanEditorStoreImpl.kt` | 93 |
| **Production total** | **1,315** |

| Other target ownership | Exact baseline |
| --- | --- |
| Android resource catalogs | `src/main/res/values/strings.xml` and `values-ru/strings.xml`, 17 physical lines each |
| Unit tests | six `src/test` Kotlin files, 752 physical lines total |
| Android manifests | zero module-owned manifest files |
| `androidTest` | zero files |
| Preview declarations | two Android `@Preview` annotations (`Light`, `Dark`) on one function in `PlanEditorScreen.kt` |
| Checked-in generated resources | zero; Android `R` is build-generated only |
| Target PNGs/goldens | zero |
| Other source sets | none; production is only `src/main`, tests only `src/test` |

There are exactly 23 production Kotlin files. The remote pre-census count is confirmed rather than
copied.

| Current `src/test` Kotlin file | Lines |
| --- | ---: |
| `mappers/PlanEditorMapperTest.kt` | 57 |
| `model/SetTypeUiModelTest.kt` | 34 |
| `mvi/handler/ClickHandlerTest.kt` | 403 |
| `mvi/handler/CommonHandlerTest.kt` | 116 |
| `mvi/handler/NavigationHandlerTest.kt` | 30 |
| `ui/mvi/store/PlanEditorStateRouteArgTest.kt` | 112 |
| **Test total** | **752** |

### 3.2 Exact six-suite / 42-case test manifest

A fresh
`:feature:plan-editor:testDebugUnitTest --rerun-tasks --no-build-cache --no-configuration-cache`
run finished `154 actionable tasks: 154 executed`. Fresh JUnit XML reports 42 tests, zero
failures, zero errors, and zero skips. There are no disabled test declarations.
Suite shorthand below is `tests/failures/errors/skips`.

| Exact JVM classname | Tests | Failures | Errors | Skips |
| --- | ---: | ---: | ---: | ---: |
| `io.github.stslex.workeeper.feature.plan_editor.mappers.PlanEditorMapperTest` | 5 | 0 | 0 | 0 |
| `io.github.stslex.workeeper.feature.plan_editor.model.SetTypeUiModelTest` | 3 | 0 | 0 | 0 |
| `io.github.stslex.workeeper.feature.plan_editor.mvi.handler.ClickHandlerTest` | 23 | 0 | 0 | 0 |
| `io.github.stslex.workeeper.feature.plan_editor.mvi.handler.CommonHandlerTest` | 3 | 0 | 0 | 0 |
| `io.github.stslex.workeeper.feature.plan_editor.mvi.handler.NavigationHandlerTest` | 2 | 0 | 0 | 0 |
| `io.github.stslex.workeeper.feature.plan_editor.ui.mvi.store.PlanEditorStateRouteArgTest` | 6 | 0 | 0 | 0 |

**`PlanEditorMapperTest` — 5/0/0/0**

- `formatPlanSummary falls back to reps-only when weight is null()`
- `formatPlanSummary truncates after the fifth row with an ellipsis suffix()`
- `formatPlanSummary keeps decimals for non-integer weights()`
- `formatPlanSummary joins rows with bullet separators and formats integer weights()`
- `formatPlanSummary on empty list yields an empty string()`

**`SetTypeUiModelTest` — 3/0/0/0**

- `toUiKitType maps every variant to the kit's chip enum()`
- `every SetTypeUiModel has a unique labelRes()`
- `DROP labelRes resolves to drop string and not failure()`

**`ClickHandlerTest` — 23/0/0/0**

- `OnConfirmDiscard closes the sheet and navigates back without persisting()`
- `back with the discard sheet open hides it and never navigates()`
- `the discard sheet and the type-change sheet cannot be open at once()`
- `OnAddSet copies reps from previous set when draft has rows()`
- `state is dirty when type differs from initialType even with stable draft()`
- `state is dirty when draft differs from initialDraft()`
- `interceptBack stays armed while the type-change sheet is shown()`
- `OnSetRemove with out-of-bounds index leaves draft unchanged()`
- `OnTypeToggle WEIGHTLESS to WEIGHTED applies new type silently regardless of draft()`
- `OnBackClick with open dialog dismisses dialog before propagating()`
- `OnTypeChangeConfirm wipes weights from draft, applies type, hides dialog()`
- `OnTypeToggle to same type is no-op()`
- `OnDismissDiscard closes the sheet without navigating()`
- `OnTypeToggle WEIGHTED to WEIGHTLESS with weighted draft opens confirm dialog()`
- `OnSetRemove drops the row at the given index()`
- `interceptBack stays enabled when type-change confirm dialog is open()`
- `OnBackClick on dirty state opens discard dialog instead of popping()`
- `OnBackClick on clean state dispatches Navigation Back()`
- `OnTypeChangeDismiss clears pending and hides dialog without changing type()`
- `OnTypeToggle with empty draft applies new type silently without dialog()`
- `OnSetTypeChange updates the type of the row at the given index()`
- `interceptBack stays armed while the discard sheet is shown()`
- `OnAddSet appends a new work set with default reps when draft is empty()`

**`CommonHandlerTest` — 3/0/0/0**

- `NotFound clears isLoading and reports, same reason()`
- `a successful load clears isLoading and hydrates the type the seed guessed wrong()`
- `a load that throws clears isLoading, or the route is composed on nothing forever()`

**`NavigationHandlerTest` — 2/0/0/0**

- `BackAfterSave pops handing true back to the PlanEditor destination()`
- `Back pops the navigation stack with no result attributes()`

**`PlanEditorStateRouteArgTest` — 6/0/0/0**

- `blank trainingUuid falls through to Exercise mode rather than PerformedExercise()`
- `live workout entry maps to PerformedExercise mode()`
- `live workout adhoc entry maps to PerformedExercise mode without training uuid()`
- `null exerciseUuid is rejected because the editor needs an exercise to load against()`
- `exercise default plan entry maps to Exercise mode()`
- `single-training edit entry maps to PerformedExercise mode without performed uuid()`

The exact test files are the same class-name paths under `mappers/`, `model/`,
`mvi/handler/`, and `ui/mvi/store/`. No helper file is required at exit; deterministic fakes and
spies fit inside those six owners.

### 3.3 Exact feature-local resource catalog

Each identifier has exactly one EN owner and one RU owner. No path outside
`feature/plan-editor` consumes any of these 12 exact identifiers.

| Identifier | EN value | RU value |
| --- | --- | --- |
| `core_ui_plan_editor_screen_title_format` | `Edit plan: %1$s` | `План: %1$s` |
| `core_ui_plan_editor_screen_title_default` | `Edit plan` | `План` |
| `core_ui_plan_editor_screen_back` | `Back` | `Назад` |
| `core_ui_plan_editor_screen_save` | `Save` | `Сохранить` |
| `core_ui_plan_editor_screen_cancel` | `Cancel` | `Отмена` |
| `core_ui_plan_editor_error_load` | `Failed to load the plan.` | `Не удалось загрузить план.` |
| `core_ui_plan_editor_error_save` | `Failed to save the plan.` | `Не удалось сохранить план.` |
| `feature_plan_editor_set_type_tooltip` | `Tap to cycle: warmup → work → failure → drop` | `Нажмите, чтобы переключить: разминка → рабочий → отказ → дроп` |
| `feature_plan_editor_type_change_weightless_title` | `Switch to weightless?` | `Переключить на без веса?` |
| `feature_plan_editor_type_change_weightless_body` | `Weight values from this exercise’s plans will be cleared. This cannot be undone.` | `Значения веса из планов этого упражнения будут очищены. Это нельзя отменить.` |
| `feature_plan_editor_type_change_weightless_impact` | `All plan weights cleared` | `Все веса в планах очищены` |
| `feature_plan_editor_type_change_weightless_confirm` | `Switch` | `Переключить` |

The English curly apostrophe and both `%1$s` format placeholders are byte-significant. The
identifiers, values, ordering, formatting, and visible copy must not change.

### 3.4 Declared dependencies and source-set visibility

The current module applies Android `convention.composeLibrary` plus Metro. Its eight direct
declarations all have `implementation` visibility in Android `main`:

| Direct dependency | Used by target | KMP readiness / decision |
| --- | --- | --- |
| `project(":core:core")` | App scope/lifetime, dispatcher, current `ResourceWrapper` | already `convention.kmpLibrary`; retain, but remove only the feature-copy `ResourceWrapper` use |
| `project(":core:ui:kit")` | theme, components, icons, snackbar, kit resources | already `convention.kmpComposeLibrary`; retain |
| `project(":core:ui:mvi")` | Store, handlers, processor, dispatchers | already `convention.kmpComposeLibrary`; retain and expose as API |
| `project(":core:ui:navigation")` | route, navigator, graph entry | already `convention.kmpComposeLibrary`; retain and expose as API |
| `project(":core:ui:plan-editor")` | body, reducer, public UI models | shared in Phase 7.5; retain and expose as API |
| `project(":core:data:database")` | `PlanSetDataModel`, `SetTypeDataModel` | already `convention.kmpLibrary`; retain as implementation |
| `project(":core:data:exercise")` | repositories and exercise types | already `convention.kmpLibrary`; retain as implementation |
| `libs.kotlinx.serialization.json` | no source import or use | artifact is KMP-capable but declaration is unused; remove |

The current Android convention's exact library visibility is:

- `implementationPlatform`: `androidx-compose-bom`;
- `debugImplementation`: `androidx-compose-tooling`;
- `implementation`: the Compose bundle (`androidx-compose-activity`, Material 3, UI, material
  icons core/extended, tooling preview, foundation, paging, animation, animation-graphics,
  runtime, Coil Compose), the lifecycle bundle (`lifecycle-compose`, `lifecycle-viewModel`),
  Material, core-ktx, immutable collections, coroutines-android, and `javax-inject`;
- `coreLibraryDesugaring`: `android-desugarJdkLibs`;
- `testImplementationPlatform`: `junit-bom`; `testRuntimeOnly`: `junit-launcher`;
- `testImplementation`: MockK Android/agent, JUnit Jupiter, Robolectric, AndroidX Test, and
  coroutines-test; and
- unused-by-this-module `androidTestImplementation`: the JUnit BOM plus AndroidX JUnit, Espresso,
  and runner.

The Android-only coordinates and JVM test tools are intentionally not carried into common. Their
needed production capabilities already have KMP counterparts in the KMP Compose convention or the
seven project dependencies; JUnit/MockK/Robolectric are replaced by portable test APIs/fakes. The
KMP Compose convention supplies CMP runtime, foundation, Material 3, UI, the portable BackHandler
artifact, preview tooling, and components-resources to `commonMain`; the KMP library convention
owns Android/Native targets, host/device source sets, lint, compiler defaults, and ordinary task
aliases.

No further dependency version, catalog entry, compiler option, or plugin implementation change is
needed inside Phase 7.7. The separate merged prerequisite already placed the generic artifact at
its convention owner.

### 3.5 External consumers and graph/result boundary

| Surface | Exact external consumers at baseline |
| --- | --- |
| Module dependency | `app/common/build.gradle.kts` and `app/app/build.gradle.kts`, both currently `implementation` |
| `PlanEditorGraph.Factory` / creator | `PlanEditorExtensionIdentityTest` uses `asContribution<PlanEditorGraph.Factory>()`; production lookup is currently internal to `PlanEditorFeature` |
| `planEditorGraph` | `AppNavigationHost.kt` registers the destination |
| `Screen.PlanEditor` route definition | `core:ui:navigation/.../Screen.kt`; `Existing(performedExerciseUuid, exerciseUuid, trainingUuid)` implements `ScreenWithResult<Boolean>` |
| Production route producer | `feature/live-workout/.../NavigationHandler.kt` constructs `Screen.PlanEditor.Existing` |
| Production typed-result consumer | `feature/live-workout/ui/LiveWorkoutGraph.kt` observes `Screen.PlanEditor::class` and sends `PlanResultReceived(saved)` |
| Production typed-result producer | target `NavigationHandler` calls `popBackWithResult(Screen.PlanEditor::class, true)` only for `BackAfterSave` |
| Result transport tests | `app/common/.../NavigatorEventBusTest.kt` and `core/ui/mvi/.../NavigationResultContractTest.kt` |
| Serialization/sample tests | `core/ui/navigation/.../ScreenSampleCatalog.kt` and `ScreenSerializationIosTest.kt` |
| Other external tests | target app identity test, live-workout `NavigationHandlerTest`, and the Android journeys below |
| Feature resource identifiers | zero external consumers |

`core/ui/plan-editor/.../PlanDraftResult.kt` documents the separate
`Screen.PlanEditor.Draft` channel. It is not the full-screen `Existing` result contract and is not
changed here. `ScreenSerialization.kt` registers the existing route subtype and also remains
unchanged.

The exact external test-source manifest for the route/factory/result boundary is:

```text
app/app/src/test/kotlin/io/github/stslex/workeeper/di/PlanEditorExtensionIdentityTest.kt
app/app/src/androidTest/kotlin/io/github/stslex/workeeper/app/NavigationResultTest.kt
app/app/src/androidTest/kotlin/io/github/stslex/workeeper/app/RouteReachabilityTest.kt
app/app/src/androidTest/kotlin/io/github/stslex/workeeper/app/StoreRetentionTest.kt
app/common/src/test/kotlin/io/github/stslex/workeeper/navigation/NavigatorEventBusTest.kt
core/ui/mvi/src/commonTest/kotlin/io/github/stslex/workeeper/core/ui/mvi/NavigationResultContractTest.kt
core/ui/navigation/src/commonTest/kotlin/io/github/stslex/workeeper/core/ui/navigation/ScreenSampleCatalog.kt
core/ui/navigation/src/iosTest/kotlin/io/github/stslex/workeeper/core/ui/navigation/ScreenSerializationIosTest.kt
feature/live-workout/src/test/kotlin/io/github/stslex/workeeper/feature/live_workout/mvi/handler/NavigationHandlerTest.kt
```

### 3.6 Android journeys and root identity tests

There are exactly three current device journeys into the full-screen editor:

| Class and method | Entry/exit claim |
| --- | --- |
| `RouteReachabilityTest.planEditorOpensFromALiveSessionExerciseAndTheSessionReturns` | enters from live workout, saves, and returns |
| `NavigationResultTest.planEditorSaveReachesTheLiveSessionThatOpenedIt` | enters, adds a set, saves, and proves the typed `true` result reloads the caller |
| `StoreRetentionTest.liveWorkoutStoreSurvivesThePlanEditorRoundTrip` | enters, uses clean Back, returns, and proves caller Store identity survives |

No current device case clicks the `PlanEditorCancel` tag or opens/confirms the dirty-discard
modal. Those behaviors are owned by the existing Click-handler identities above; this
specification does not misreport absent device coverage as present.

A fresh focused API-34 run executed those three journeys plus all three
`UiAdmissionRaceTest` cases. XML reported six tests, zero failures/errors/skips, and Gradle reported
`683 actionable tasks: 683 executed`. The admission identities are:

- `admittedGeneration_composesTheRegion_andResolvesItsDependencies` — exactly one root-deps
  resolution;
- `retiredGeneration_composesNothing_andResolvesNothing` — zero; and
- `retirementBetweenPublicationAndFrame_resolvesNothing` — zero.

`PlanEditorExtensionIdentityTest` also ran freshly: its three exact cases passed with
`528 actionable tasks: 528 executed`:

- `extension resolves the store through the parent graph()`;
- `store's app-scoped deps are the SAME instances the parent holds()`; and
- `each extension carries its own route arg into the store state()`.

### 3.7 Context readers and PNG ownership

There are exactly 12 executable production `context.appDeps<...>()` readers before this slice:

1. `feature/all-exercises/.../AllExercisesFeature.kt`
2. `feature/all-trainings/.../AllTrainingsFeature.kt`
3. `feature/app-dialogs/impl/.../AppDialogFeature.kt`
4. `feature/archive/.../ArchiveFeature.kt`
5. `feature/exercise-chart/.../ExerciseChartFeature.kt`
6. `feature/exercise/.../ExerciseFeature.kt`
7. `feature/home/.../HomeFeature.kt`
8. `feature/live-workout/.../LiveWorkoutFeature.kt`
9. `feature/past-session/.../PastSessionFeature.kt`
10. `feature/plan-editor/.../PlanEditorFeature.kt`
11. `feature/settings/.../SettingsFeature.kt`
12. `feature/single-training/.../SingleTrainingFeature.kt`

The projected exit inventory is the same list minus plan-editor: exactly 11 readers. KDoc mentions
in `AppRootViewModel` and `BackupWorkerDeps` are not executable readers and are not counted.

The repository contains 484 tracked PNGs. Exactly 456 are Paparazzi entries under
`snapshots/images`, across 13 owners:

| Owner | PNGs | Owner | PNGs |
| --- | ---: | --- | ---: |
| `core:ui:kit` | 86 | `core:ui:plan-editor` | 18 |
| `core:ui:start-mode` | 2 | `feature:all-exercises` | 52 |
| `feature:all-trainings` | 50 | `feature:archive` | 14 |
| `feature:exercise` | 48 | `feature:exercise-chart` | 30 |
| `feature:home` | 42 | `feature:live-workout` | 60 |
| `feature:past-session` | 30 | `feature:settings` | 12 |
| `feature:single-training` | 12 |  |  |

The other 28 PNGs are 20 app launcher assets and eight Fastlane images. The Paparazzi path-list
SHA-256 is `720a2f7de467d7472b99157952bc189123cfb1549afc8728c0626584965a0bc0`;
the mode/blob/path manifest SHA-256 is
`e5c47e60890fae85a76ccd4e97cd4de7364d7c285b4da19e5e5c55e8f3d3e9ff`.
The complete PNG mode/blob/path manifest hash is
`f2c4a01eed26b232856face73048dd831759fead6a3b173b86e848bedce4721d`.

Phase 7.7 owns no PNG and authorizes no record, mutation, tolerance, harness, or golden-owner
change.

## 4. Platform/API blockers — settled decisions

| Baseline blocker | Required treatment |
| --- | --- |
| `PlanEditorFeature` uses `LocalContext` and `Context.appDeps` | pass `PlanEditorGraph.Factory` explicitly through the admitted root flow and the `PlanEditorFeature(factory)` constructor in Section 6; remove both imports and lookup |
| graph uses Android `stringResource` and activity `BackHandler` | use private CMP resources and `androidx.compose.ui.backhandler.BackHandler` with the required Compose UI experimental opt-in |
| screen uses Android `R`, `Configuration`, and Android preview semantics | use generated private `Res`, portable Compose `Preview`, and explicit `ThemeMode.LIGHT`/`DARK` preview wrappers |
| `ErrorType` carries Android `Int` ids | make it a semantic payload-free enum; the composable maps each entry to a private CMP `StringResource` and resolves it |
| Click handler resolves four static dialog strings through `ResourceWrapper` | remove that constructor dependency; use a semantic `TypeChangeConfirm` state and resolve the four private CMP values in the screen |
| `VisibleForTesting` | remove the Android annotation; keep `NAME` private and unchanged |
| JUnit 5 and MockK | replace with `kotlin.test` and deterministic in-file fakes/spies; neither dependency enters `commonTest` |
| shape-B route argument | retain `@Provides screen: Screen.PlanEditor` on `PlanEditorGraph.Factory.createPlanEditorGraph`; no assisted parameter or route registry |
| unused serialization JSON | remove the declaration; do not add serialization work |
| previews | keep two portable named previews; no deletion and no Android-only preview source is necessary |

Merged prerequisite PR #271 adds the separate
`org.jetbrains.compose.ui:ui-backhandler:1.11.1` artifact directly to
`convention.kmpComposeLibrary` as `commonMainImplementation`. The ordinary CMP `ui` artifact
did not provide the Android compile edge; the prerequisite's byte-identical causal probe proved
that distinction on common metadata, Android, and Native. Do not duplicate the generic dependency
in `feature:plan-editor`.

The portable `androidx.compose.ui.backhandler.BackHandler` API is deprecated in favor of the
broader navigation-event API, but that is not permission to change behavior in this bounded phase.
Preserve the current boolean enablement and `OnBackClick` dispatch exactly.

Common production at exit must contain no Android resource id, Android `R`, Android
`ResourceWrapper` call for feature-local static copy, `Context` lookup, Android annotation,
Java/Javax platform API, placeholder actual, service locator, static factory registry,
CompositionLocal DI, or expect/actual shim.

## 5. Resource, State, Back, and preview architecture

### 5.1 Private generated CMP resource owner

Move the two catalogs byte-semantically to:

- `src/commonMain/composeResources/values/strings.xml`; and
- `src/commonMain/composeResources/values-ru/strings.xml`.

Configure:

```kotlin
compose.resources {
    packageOfResClass = "io.github.stslex.workeeper.feature.plan_editor.resources"
}
```

Do not set `publicResClass = true`. The generated `Res` and its 12 `StringResource` values are
private module implementation details. Same-module common and iOS tests may inspect them; no
external module may import them. Delete the two legacy Android catalogs only after the CMP owners
contain all exact identifiers and values.

The screen resolves title, Back, Save, Cancel, tooltip, and type-change confirmation copy through
`org.jetbrains.compose.resources.stringResource`. `PlanEditorContent` resolves the two error
messages through the same owner before entering the suspend event handler, preserving the current
Compose/Lint lifecycle.

### 5.2 Semantic Store state, not resource handles

`ErrorType` becomes the payload-free enum `LoadFailed`, `SaveFailed`. The composable owns the
exhaustive enum-to-`Res.string` mapping. No `Int`, Android id, string, or generated resource type is
stored in the event.

`DialogState.TypeChangeConfirm` becomes a payload-free semantic object; the pending target remains
in `State.pendingTypeChange`. The screen's matching branch resolves the title, body, impact, and
confirm label directly from the private CMP catalog. This removes `ResourceWrapper` from
`ClickHandler` without redesigning the global wrapper contract or exposing generated resources in
public State.

All handler `updateState` lambdas remain copy/reducer operations only. Resource resolution,
repository calls, navigation, and other work stay outside those lambdas.

### 5.3 Portable Back and previews

Use the convention-provided portable API:

```kotlin
@OptIn(ExperimentalComposeUiApi::class)
BackHandler(enabled = state.interceptBack) {
    processor.consume(Action.Click.OnBackClick)
}
```

The exact `interceptBack` derivation and action are unchanged. Do not introduce a platform wrapper
or migrate this route to `NavigationEventHandler` in Phase 7.7.

Keep the two named previews in `PlanEditorScreen.kt`. Replace the Android `Configuration` constant
with two portable preview entry functions that use the same sample State and call
`AppTheme(themeMode = ThemeMode.LIGHT)` and `AppTheme(themeMode = ThemeMode.DARK)` respectively.
This is the established commonMain pattern in start-mode and shared plan-editor UI. Preview
tooling is already supplied by `convention.kmpComposeLibrary`; no `androidMain` preview source is
needed. If those two previews cannot compile from commonMain, STOP rather than delete or silently
degrade them.

## 6. Explicit generation-owned shape-B factory flow

The required production flow is exact:

```text
AppRootDeps.planEditorGraphFactory
  -> AppGraph.override planEditorGraphFactory
  -> admitted AppGenerationContent(deps)
  -> AppNavigationHost(planEditorGraphFactory = deps.planEditorGraphFactory)
  -> planEditorGraph(factory = planEditorGraphFactory)
  -> PlanEditorFeature(factory).processor(screen)
  -> rememberMetroStoreProcessor<PlanEditorStoreImpl> {
         factory.createPlanEditorGraph(screen).planEditorStore
     }
```

Required invariants:

1. `AppRootDeps` adds only `val planEditorGraphFactory: PlanEditorGraph.Factory`.
2. `AppGraph` explicitly overrides that accessor. It remains the generation-owned app graph; no
   global/static holder is introduced.
3. `App()` still calls `appRootDeps()` once, only after the generation admission grant. Reading two
   factory properties from that one object does not mean resolving root deps twice.
4. Rejected or retired regions still call `appRootDeps()` zero times.
5. `AppNavigationHost` and `planEditorGraph` receive the factory as explicit required parameters.
   `planEditorGraph` supplies it to the required `PlanEditorFeature(factory)` constructor, and
   `PlanEditorFeature` continues to override `processor(screen)` exactly. The processor override
   does not gain a factory parameter. No default, nullable fallback, CompositionLocal, or service
   lookup is permitted.
6. Production invokes `createPlanEditorGraph(screen)` only inside the Store-creation lambda. A
   recomposition that retains the Store must not create another extension.
7. The shape-B creator name and `@Provides Screen.PlanEditor` parameter remain unchanged.
8. Every route entry creates a distinct extension graph/Store. The route argument from entry A
   cannot be cached, reused, or observed by entry B.

`PlanEditorExtensionIdentityTest` must use
`appGraph.planEditorGraphFactory.createPlanEditorGraph(screen)`, never
`asContribution<PlanEditorGraph.Factory>()`, while preserving its three exact identities. This is
the explicit root accessor under test, not a new DI path.

Navigation remains an `Action.Navigation` decision handled by `NavigationHandler`. `Back` still
calls `popBack()`. `BackAfterSave` still calls
`popBackWithResult(Screen.PlanEditor::class, true)`. The destination type and Boolean value are
load-bearing and must not change.

## 7. In scope

Only the following implementation work is authorized after the separate GO:

- convert the target build to `convention.kmpComposeLibrary`, retain Metro, and declare the
  narrow source-set/API edges in Section 10;
- move the exact 23 production files to `commonMain` and make only the portability edits in
  Sections 4–6;
- move the exact two catalogs to private CMP ownership;
- move the six existing test files to `commonTest`, preserve all 42 identities, and replace
  JUnit/MockK with deterministic portable test code;
- add the single exact iOS production-scene file in Section 11;
- add the explicit root factory through the four production root/host files;
- update the one app graph identity test to the explicit root accessor;
- change `app:common`'s existing plan-editor edge from `implementation` to `api`; keep
  `app:app`'s existing `implementation` edge;
- minimally extend the existing topology script, Native invocation, Native upload, and Native XML
  validator; and
- update only canonical documentation made stale by the implementation and append measured
  implementation evidence to this specification.

## 8. Explicit non-goals

Phase 7.7 does not authorize:

- any sibling feature production change, including `feature:live-workout`;
- changes to `Screen.PlanEditor`, route fields, serialization, result destination/value, or
  caller behavior;
- global `ResourceWrapper` redesign or deletion;
- app runtime, admission, replacement, recovery, database schema, repository semantics, or
  navigation-bus redesign;
- `app:common` KMP conversion;
- expect/actual, placeholder implementations, static registries, service locators, or
  CompositionLocal DI;
- dependency/catalog/version/compiler/convention-plugin/ruleset/filter changes;
- CocoaPods, an `iosApp`, XCFramework packaging, Xcode project generation, signing, or permanent
  host work;
- new or changed PNGs, golden recording, tolerance changes, or visual re-baselining;
- preview deletion or an Android-only preview unless the common preview finding is first
  remeasured and this specification is replaced;
- MVI action renames, State/route redesign, unrelated cleanup, or comment history; or
- merge, auto-merge, repository settings, or implementation before the explicit GO.

The permanent iOS host remains later in the roadmap and should prefer direct framework
integration. A CocoaPods convention is not justified unless that later direct-integration work is
measured infeasible.

## 9. Exact allowed change boundary and exit topology

### 9.1 Target module

`feature/plan-editor/build.gradle.kts` may change. Under `src`, the exact final manifest is 32
files: 23 common production, two common resource catalogs, six common tests, and one iOS test.

The 23 `commonMain` Kotlin paths are the current production relative paths, unchanged:

```text
src/commonMain/kotlin/io/github/stslex/workeeper/feature/plan_editor/di/PlanEditorFeature.kt
src/commonMain/kotlin/io/github/stslex/workeeper/feature/plan_editor/di/PlanEditorGraph.kt
src/commonMain/kotlin/io/github/stslex/workeeper/feature/plan_editor/di/PlanEditorHandlerStore.kt
src/commonMain/kotlin/io/github/stslex/workeeper/feature/plan_editor/di/PlanEditorHandlerStoreImpl.kt
src/commonMain/kotlin/io/github/stslex/workeeper/feature/plan_editor/di/PlanEditorScope.kt
src/commonMain/kotlin/io/github/stslex/workeeper/feature/plan_editor/domain/PlanEditorInteractor.kt
src/commonMain/kotlin/io/github/stslex/workeeper/feature/plan_editor/domain/PlanEditorInteractorImpl.kt
src/commonMain/kotlin/io/github/stslex/workeeper/feature/plan_editor/domain/mapper/PlanEditorDomainMapper.kt
src/commonMain/kotlin/io/github/stslex/workeeper/feature/plan_editor/domain/model/ExerciseTypeDomain.kt
src/commonMain/kotlin/io/github/stslex/workeeper/feature/plan_editor/domain/model/PlanEditorLoadResult.kt
src/commonMain/kotlin/io/github/stslex/workeeper/feature/plan_editor/domain/model/PlanSetDomain.kt
src/commonMain/kotlin/io/github/stslex/workeeper/feature/plan_editor/domain/model/SetTypeDomain.kt
src/commonMain/kotlin/io/github/stslex/workeeper/feature/plan_editor/ui/PlanEditorGraph.kt
src/commonMain/kotlin/io/github/stslex/workeeper/feature/plan_editor/ui/PlanEditorScreen.kt
src/commonMain/kotlin/io/github/stslex/workeeper/feature/plan_editor/ui/mapper/PlanEditorMapper.kt
src/commonMain/kotlin/io/github/stslex/workeeper/feature/plan_editor/ui/mvi/handler/ClickHandler.kt
src/commonMain/kotlin/io/github/stslex/workeeper/feature/plan_editor/ui/mvi/handler/CommonHandler.kt
src/commonMain/kotlin/io/github/stslex/workeeper/feature/plan_editor/ui/mvi/handler/EditorHandler.kt
src/commonMain/kotlin/io/github/stslex/workeeper/feature/plan_editor/ui/mvi/handler/InputHandler.kt
src/commonMain/kotlin/io/github/stslex/workeeper/feature/plan_editor/ui/mvi/handler/NavigationHandler.kt
src/commonMain/kotlin/io/github/stslex/workeeper/feature/plan_editor/ui/mvi/store/DialogState.kt
src/commonMain/kotlin/io/github/stslex/workeeper/feature/plan_editor/ui/mvi/store/PlanEditorStore.kt
src/commonMain/kotlin/io/github/stslex/workeeper/feature/plan_editor/ui/mvi/store/PlanEditorStoreImpl.kt
```

Resources:

```text
src/commonMain/composeResources/values/strings.xml
src/commonMain/composeResources/values-ru/strings.xml
```

Portable tests, with the same package/class paths as baseline:

```text
src/commonTest/kotlin/io/github/stslex/workeeper/feature/plan_editor/mappers/PlanEditorMapperTest.kt
src/commonTest/kotlin/io/github/stslex/workeeper/feature/plan_editor/model/SetTypeUiModelTest.kt
src/commonTest/kotlin/io/github/stslex/workeeper/feature/plan_editor/mvi/handler/ClickHandlerTest.kt
src/commonTest/kotlin/io/github/stslex/workeeper/feature/plan_editor/mvi/handler/CommonHandlerTest.kt
src/commonTest/kotlin/io/github/stslex/workeeper/feature/plan_editor/mvi/handler/NavigationHandlerTest.kt
src/commonTest/kotlin/io/github/stslex/workeeper/feature/plan_editor/ui/mvi/store/PlanEditorStateRouteArgTest.kt
```

New Native scene:

```text
src/iosTest/kotlin/io/github/stslex/workeeper/feature/plan_editor/PlanEditorFeatureSceneIosTest.kt
```

There must be no target file under `src/main`, `src/test`, `src/androidMain`, `src/iosMain`,
`src/androidHostTest`, or `src/androidDeviceTest`; no module manifest; no extra catalog; and no
target PNG.

### 9.2 Exact root, CI, and documentation boundary

The only allowed root/consumer production and test files are:

```text
app/common/build.gradle.kts
app/common/src/main/kotlin/io/github/stslex/workeeper/App.kt
app/common/src/main/kotlin/io/github/stslex/workeeper/app/common/di/AppRootDeps.kt
app/common/src/main/kotlin/io/github/stslex/workeeper/host/AppNavigationHost.kt
app/app/src/main/java/io/github/stslex/workeeper/di/AppGraph.kt
app/app/src/test/kotlin/io/github/stslex/workeeper/di/PlanEditorExtensionIdentityTest.kt
```

The only allowed CI/gate files are:

```text
.github/scripts/assert_kmp_ui_source_topology.py
.github/scripts/assert_kmp_ios_smoke.py
.github/workflows/android_build_unified.yml
```

The implementation may update only the Phase 7.7 evidence section and any exact stale facts in
`documentation/architecture.md`, `documentation/testing.md`, or `documentation/ci-cd.md`. No
other source, test, resource, Gradle, workflow, script, generated, rules, golden, or documentation
path is in scope. If another path is required, STOP and amend the specification first.

`UiAdmissionRaceTest`, the three Android editor journeys, `NavigatorEventBusTest`, the MVI result
contract, navigation serialization tests, and live-workout tests are execution-only consumers in
this slice; their source does not need modification.

## 10. Gradle and public API contract

The build applies `convention.kmpComposeLibrary` and Metro with the current `includeJavax()`
interop block. The convention owns `androidLibrary`, `iosSimulatorArm64`, source-set wiring,
compiler defaults, CMP core dependencies, lint, and ordinary CI task aliases. Do not encode those
generic facts in Python or duplicate them in the module.

Declare only the narrow source-set dependencies:

| Configuration | Dependency | Reason |
| --- | --- | --- |
| `commonMain implementation` | `project(":core:core")` | app-scope tokens/lifetime and dispatcher qualifier |
| `commonMain implementation` | `project(":core:ui:kit")` | internal components, theme, snackbar, and kit strings |
| `commonMain api` | `project(":core:ui:mvi")` | public Store/processor/implementation surface |
| `commonMain api` | `project(":core:ui:navigation")` | public factory route parameter and graph entry |
| `commonMain api` | `project(":core:ui:plan-editor")` | public State/action fields use its UI models |
| `commonMain implementation` | `project(":core:data:database")` | internal data mapping |
| `commonMain implementation` | `project(":core:data:exercise")` | internal repository implementation |
| `commonMain api` | `libs.cmp.ui` | public graph `Modifier` and event `HapticFeedbackType` |
| `commonMain api` | `libs.kotlinx.collections.immutable` | `ImmutableList` appears in public State and initializer signatures |
| `commonTest implementation` | `kotlin("test")` | portable assertions and `@Test` |
| `commonTest implementation` | `libs.coroutine-test` | deterministic suspend/launch fake execution |
| `iosTest implementation` | `kotlin("test")`, `libs.cmp.ui.test` | Native production-scene assertions and UI driver |

The KMP Compose convention supplies runtime, foundation, Material 3, UI, the portable BackHandler
artifact, preview tooling, and CMP resources. Do not add Android activity-compose, Android
resources, Android test bundles, JUnit, MockK, Robolectric, Paparazzi, a golden harness,
serialization JSON, a module-local `libs.cmp.uiBackhandler` edge, or any other duplicate generic
CMP dependency.

Change `app:common`'s existing plan-editor dependency to `api`, because public `AppRootDeps` names
`PlanEditorGraph.Factory`. Leave `app:app`'s existing `implementation` dependency unchanged for
Metro contribution aggregation and its explicit accessor.

Preserve every existing route, Store, State, Action, Event, handler, interactor, mapper, graph
scope, creator name, test tag, enum order, default, and visible behavior except the narrowly listed
resource-representation and explicit-factory API changes. If KMP compilation forces a consumer
adapter or callable-surface change not listed here, STOP.

## 11. Test and Native scene contract

### 11.1 Portable suites

Move all six suites and all 42 exact Section 3.2 identities to `commonTest`. Use `kotlin.test` and
handwritten deterministic fakes/spies:

- mapper, set-type, and route-state suites remain pure;
- `ClickHandlerTest` uses a mutable fake handler Store and a recording/fake interactor; it no
  longer needs a resource fake because the handler no longer resolves copy;
- `CommonHandlerTest` uses a deterministic fake interactor and a fake Store whose launch path
  runs through the test scheduler and records errors/events;
- `NavigationHandlerTest` uses a recording `Navigator` and asserts the exact destination/value;
  and
- fake helpers stay in their owning files so the exact six-file topology does not grow.

The sole authorized suppression exception is exactly
`@file:Suppress("INVALID_CHARACTERS_NATIVE_ERROR")` in these two files:

```text
feature/plan-editor/src/commonTest/kotlin/io/github/stslex/workeeper/feature/plan_editor/mvi/handler/ClickHandlerTest.kt
feature/plan-editor/src/commonTest/kotlin/io/github/stslex/workeeper/feature/plan_editor/mvi/handler/CommonHandlerTest.kt
```

The exception exists only to preserve the three inherited comma-bearing identities in Section 3.2
under Kotlin/Native 2.4.10. It authorizes no production suppression, additional diagnostic,
additional file, compiler flag, task filter, baseline, ruleset change, or test-identity change. It
is the only exception to the suppression prohibitions in Sections 0.2 and 12.5 and the suppression
STOP condition in Section 14.

Independent removal experiments on 2026-08-31 used
`documentation/mockups/mutation_harness.py` with the canonical seven-module Native command and all
required cache-defeating flags. The untouched PR #273 head
`4a1efd030f20a60db84d50a323141e54af0561e7` first passed with
`197 actionable tasks: 197 executed`; the Native XML oracle verified all 43 target identities with
zero failures, errors, or skips. Removing the annotation from `ClickHandlerTest.kt` produced
`Name contains illegal characters: ","` for
`OnTypeChangeConfirm wipes weights from draft, applies type, hides dialog`; removing it from
`CommonHandlerTest.kt` produced the same compiler error for
`a load that throws clears isLoading, or the route is composed on nothing forever` and
`NotFound clears isLoading and reports, same reason`. Each mutated run reached
`194 actionable tasks: 194 executed`, was correctly classified `INVALID — DID NOT COMPILE` rather
than RED, produced no accepted mutated XML, and was byte-restored by the harness. With the two
annotations present, Kotlin/Native warns that suppressing this error has unspecified behavior and
will not be preserved.

At that measured PR #273 head, production still contained `@Suppress("MagicNumber")`. This
documentation-only exception neither authorizes nor claims removal of that production suppression,
and it does not claim that the Phase 7.7 implementation has been revalidated.

No identity may be renamed, removed, disabled, or skipped. Android-host XML and Native XML must
both show all six suites and 42 exact method identities, with zero failures/errors/skips.

### 11.2 Android graph and device compatibility

Preserve the three exact `PlanEditorExtensionIdentityTest` cases through the explicit AppGraph
accessor. Preserve and rerun the three exact device journeys in Section 3.6, including the typed
Boolean result and live-workout Store retention. Rerun the three admission cases to prove the root
resolution counts remain admitted = one, retired = zero, and publication/retirement race = zero.

### 11.3 Deterministic iOS production scene

Add exactly:

```text
io.github.stslex.workeeper.feature.plan_editor.PlanEditorFeatureSceneIosTest
  .resourcesBranchesAndActionsRenderAndDispatch
```

The scene must compose the real production `PlanEditorScreen` under `AppTheme`, not a copied
facsimile, blank lambda, graph placeholder, or test-only replacement. It must deterministically:

1. validate all 12 EN generated values, including `%1$s`, the curly apostrophe, and dialog copy;
2. render a loaded weighted Exercise-mode State with a nonblank exercise name and at least one set;
3. prove the formatted title, Back, Save, Cancel, and tooltip/resource-bearing UI;
4. drive the real add-set control and observe exact
   `Action.EditorAction(PlanEditorBodyAction.OnAddSet)` dispatch;
5. drive Cancel/Back and observe exact `Action.Click.OnBackClick` dispatch;
6. recompose the semantic `TypeChangeConfirm` branch, assert its four feature-local strings plus
   the kit Cancel label, and dispatch `OnTypeChangeConfirm`; and
7. exercise the blank-name default-title branch or another equivalently meaningful production
   resource branch.

The scene does not instantiate the Android app graph and does not substitute an iOS host. It is a
production composable proof at the module boundary.

Native XML at exit contains seven target suites and exactly 43 target cases: the 42 portable
identities plus the one scene. Kotlin/Native prefixes classnames with
`iosSimulatorArm64Test.` and suffixes each method with `[iosSimulatorArm64]`. Add every one of
those 43 exact tuples to the target entry in `assert_kmp_ios_smoke.py`; the validator must reject a
missing, duplicate, skipped, failed, errored, or mismatched tuple. Existing module tuples remain
unchanged.

## 12. CI ownership and positive verification

### 12.1 Minimum stable-CI extension

Do not rename or split a required job. Make only these repository-specific additions:

1. Extend `.github/scripts/assert_kmp_ui_source_topology.py` with the exact 32-file target
   manifest, two resource owners and values, forbidden common-platform imports/APIs, two-preview
   contract, explicit root-factory flow through `PlanEditorFeature(factory).processor(screen)`,
   `app:common` API edge, and exact remaining 11-reader inventory.
2. Append `:feature:plan-editor:iosSimulatorArm64Test` to the existing forced Native Gradle
   invocation in `KMP iOS kit smoke`.
3. Append `feature/plan-editor/build/test-results/iosSimulatorArm64Test/` to the existing
   always-uploaded Native artifact.
4. Extend `.github/scripts/assert_kmp_ios_smoke.py` with the exact 43 target identities from
   Section 11 while preserving every existing module/tuple.

Python may enforce these repository facts because Gradle's build model does not prove exact path
ownership, absence of legacy source sets, resource owner/value identity, forbidden lookup/API
regression, root-factory text flow, reader inventory, or JUnit tuple identity. Target creation,
source-set relationships, dependency resolution, and ordinary task aliases remain solely in the
existing convention plugins.

The `Build and Unit Tests` job continues to run the topology oracle, `assembleDebug`,
`assembleDebugAndroidTest`, `verifyPaparazziDebug`, `:lint-rules:test`, `detekt`, the personal-data
gate, `lintDebug`, forced MVI host identities, and `testDebugUnitTest`. The Native job remains the
single macOS owner of its forced invocation, XML assertion, and upload. The mockup workflow and its
known negative remain unchanged.

If a missing generic guarantee requires a convention-plugin change, STOP and propose a separate
build-logic prerequisite PR. Do not put policy work into Phase 7.7.

### 12.2 Entry gate before implementation edits

The implementation agent must repeat, from a clean isolated worktree:

```bash
git fetch origin dev
git rev-parse origin/dev
git status --short
git merge-base --is-ancestor e2e18db1398ddeb997dbf1a4d66c7838bf6004fa origin/dev
gh pr view 270 --repo stslex/Workeeper --json state,mergeCommit,mergedAt
gh pr view 271 --repo stslex/Workeeper --json state,headRefOid,mergeCommit,mergedAt
REBASELINE_MERGE_SHA="$(
  gh pr view 272 --repo stslex/Workeeper \
    --json state,mergeCommit,mergedAt \
    --jq 'select(.state == "MERGED" and .mergeCommit.oid != null) | .mergeCommit.oid'
)"
test -n "$REBASELINE_MERGE_SHA"
git merge-base --is-ancestor "$REBASELINE_MERGE_SHA" origin/dev
gh pr list --repo stslex/Workeeper --state open --json number,headRefName,baseRefName,title
python3 .github/scripts/assert_kmp_ui_source_topology.py
```

The `REBASELINE_MERGE_SHA` resolution and ancestry assertion are the load-bearing proof that
PR #272 is merged and that `origin/dev` contains this rebaseline; checking only PRs #270 and #271
is insufficient. The base must also descend from the exact prerequisite merge and still match the
Section 3 target boundary. PR #271 must remain merged with head
`3685abd808eca83ece26a6e5b0d85cf9cf8efda5` and merge commit
`e2e18db1398ddeb997dbf1a4d66c7838bf6004fa`. Re-run the six-suite baseline and inspect fresh XML.
Any material drift invokes a Section 14 STOP.

### 12.3 Focused implementation gates

Run gates serially. Every load-bearing Gradle invocation uses
`--rerun-tasks --no-build-cache --no-configuration-cache`; configuration-cache use is not permitted
for any evidence command. Run `detekt` in its own Gradle invocation as shown in Section 12.4. Quote
the summary line. A summary containing `from cache` or `up-to-date`, or anything other than
`N actionable tasks: N executed`, is not execution evidence.

```bash
python3 .github/scripts/assert_kmp_ui_source_topology.py

./gradlew :feature:plan-editor:assembleDebug \
  --rerun-tasks --no-build-cache --no-configuration-cache --console=plain

./gradlew :feature:plan-editor:testAndroidHostTest \
  --rerun-tasks --no-build-cache --no-configuration-cache --console=plain

./gradlew :core:ui:kit:iosSimulatorArm64Test \
  :core:ui:navigation:iosSimulatorArm64Test \
  :core:ui:mvi:iosSimulatorArm64Test \
  :core:ui:start-mode:iosSimulatorArm64Test \
  :core:ui:plan-editor:iosSimulatorArm64Test \
  :feature:image-viewer:iosSimulatorArm64Test \
  :feature:plan-editor:iosSimulatorArm64Test \
  --rerun-tasks --no-build-cache --no-configuration-cache --console=plain

python3 .github/scripts/assert_kmp_ios_smoke.py

./gradlew :app:common:assembleDebug \
  --rerun-tasks --no-build-cache --no-configuration-cache --console=plain

./gradlew :app:app:testDebugUnitTest \
  --tests '*PlanEditorExtensionIdentityTest*' \
  --rerun-tasks --no-build-cache --no-configuration-cache --console=plain
```

Structurally parse XML rather than using console totals:

- Android host: six suites, the 42 exact Section 3.2 pairs, 42 tests, zero
  failures/errors/skips;
- Native: those same 42 pairs plus exactly
  `PlanEditorFeatureSceneIosTest.resourcesBranchesAndActionsRenderAndDispatch`, 43 target tests,
  zero failures/errors/skips;
- app JVM: the three exact extension identities, zero failures/errors/skips.

Run the three editor journeys and the three admission identities on the repository portrait API-34
device profile with the exact current task:

```bash
ANDROID_SERIAL=emulator-5554 ./gradlew :app:app:connectedDebugAndroidTest \
  '-Pandroid.testInstrumentationRunnerArguments.class=io.github.stslex.workeeper.app.RouteReachabilityTest#planEditorOpensFromALiveSessionExerciseAndTheSessionReturns,io.github.stslex.workeeper.app.NavigationResultTest#planEditorSaveReachesTheLiveSessionThatOpenedIt,io.github.stslex.workeeper.app.StoreRetentionTest#liveWorkoutStoreSurvivesThePlanEditorRoundTrip,io.github.stslex.workeeper.app.UiAdmissionRaceTest' \
  --rerun-tasks --no-build-cache --no-configuration-cache --console=plain
```

Fresh device XML must show those exact six identities, all executed, with zero
failures/errors/skips. `emulator-5554` is the measured discovery serial; resolve and substitute an
equally explicit repository portrait API-34 serial if it changes. Restore every temporary device
setting afterward.

### 12.4 Repository and visual gates

Run the repository contract without overlap. The initial `clean` is preparation, not evidence.
Every load-bearing Gradle invocation after it uses all three required cache-defeating flags, and
`detekt` runs in its own invocation:

```bash
./gradlew clean
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
python3 documentation/mockups/shell_gate.py \
  --base "$(git merge-base origin/dev HEAD)" -v
```

Also prove the permanent mockup known negative remains red with target `f52462c7`. Execute canonical
Smoke and Regression when the implementation is ready for Phase exit, inspect exact XML
membership, and accept only the documented named skips. Do not turn a filter or missing device
into a green.

Before and after all gates, compare:

```bash
git ls-files -s '*snapshots/images/*.png' | shasum -a 256
git ls-files -s '*.png' | shasum -a 256
```

The Paparazzi manifest must remain 456 entries across 13 owners with hash
`e5c47e60890fae85a76ccd4e97cd4de7364d7c285b4da19e5e5c55e8f3d3e9ff` relative to this
baseline. All 484 repository PNG mode/blob/path entries must remain identical. No image may be
recorded merely to make a migration green.

### 12.5 Final diff and remote proof

At implementation PR head, prove:

- only Section 9 paths changed and no generated/local/secret file is tracked;
- exact target topology, catalog, import/API, preview,
  `PlanEditorFeature(factory).processor(screen)` factory-flow, and reader gates are green;
- all 42 portable identities execute on both platforms and the Native scene identity executes
  once;
- all three graph, three editor-journey, and three admission identities remain green;
- route/result/store behavior and the 456/484 PNG manifests are unchanged;
- rulesets, stable context names, versions, compiler policy, conventions, filters, baselines, and
  repository settings are unchanged, and exactly the two Section 11.1 test-file annotations are
  the only authorized suppression delta;
- every implementation commit is signed and GitHub Verified; and
- every review finding is reproduced/classified, answered, fixed or rebutted with evidence, and
  its thread resolved before completion is claimed.

The implementation PR stays open. The implementation agent must not merge or enable auto-merge.

## 13. Mandatory known-negative controls

Every control is mandatory at the implementation head and must be recorded as:

```text
fresh GREEN -> named observable RED -> exact byte/path restoration -> fresh GREEN
```

Use `documentation/mockups/mutation_harness.py` for one-shot Kotlin/XML mutations whenever the
owning verdict is a Gradle task. For topology-only path/oracle controls, use a scratch byte copy,
an explicit target path, a guaranteed restoration trap, and post-restore byte/path assertions.
Never use `git checkout`, `restore`, reset, clean, or a hand-revert. A compile-invalid mutation,
sandbox-blocked run, `UP-TO-DATE`, or `FROM-CACHE` result is not RED evidence.

| # | Critical claim | Named mutation and required observable RED |
| ---: | --- | --- |
| 1 | Exact source/resource/test topology | remove one exact target path; the shared topology oracle must name the missing path |
| 2 | No legacy residue | copy one production file to `src/main` without changing its common owner; the oracle must name forbidden legacy residue |
| 3 | Same-count path substitutions cannot pass | move one common file to a wrong relative path while keeping total file count 32; the exact manifest must be RED |
| 4 | Common production rejects Android/platform APIs | insert a valid Android import into one common production file; platform rejection must name file/import |
| 5 | Exact resource keys | rename one EN key while keeping 12 entries; resource identity must be RED |
| 6 | Exact resource values | change one RU value without changing its key/count; resource value must be RED |
| 7 | Exact resource owner | move one catalog to a wrong source/resource owner with a same-count substitute; ownership must be RED |
| 8 | Store carries no Android-style id | add a compile-valid `Int` resource-id payload to `ErrorType`; semantic resource-model check must be RED |
| 9 | Handler does not resolve feature copy | reintroduce a `ResourceWrapper` import/call in `ClickHandler`; forbidden feature-copy check must be RED |
| 10 | Back handling is portable and behaviorally wired | replace the portable BackHandler import with `androidx.activity.compose.BackHandler` or suppress its `OnBackClick`; topology or the owning Native/action gate must be RED by name |
| 11 | Root accessors exist | remove either `AppRootDeps.planEditorGraphFactory` or `AppGraph`'s explicit override; root topology must name the missing accessor |
| 12 | Exact root/host/feature flow | bypass one arrow in `AppRootDeps.planEditorGraphFactory` -> `AppGraph` -> admitted `AppGenerationContent(deps)` -> `AppNavigationHost(required factory)` -> `planEditorGraph(required factory)` -> `PlanEditorFeature(factory).processor(screen)` -> `createPlanEditorGraph(screen)`; flow topology must name that broken edge |
| 13 | Admitted root resolution remains one | make the admitted region call `appRootDeps()` twice; `admittedGeneration_composesTheRegion_andResolvesItsDependencies` must fail with observed count 2 |
| 14 | Rejected root resolution remains zero | move root-deps resolution before admission; retired and race identities must fail with a nonzero count |
| 15 | Route arguments are extension-local | replace `initialState = screen.toInitialState()` with a fixed valid route; `each extension carries its own route arg into the store state()` must fail |
| 16 | Handler/reducer behavior is not decorative | change `OnAddSet` to append the wrong set type or reps; its exact Click-handler identity must fail on Android host and Native |
| 17 | Native scene composes production UI | replace the production screen composition with a blank container; the exact scene must fail on missing semantics/text |
| 18 | Native action observation is live | suppress the production add-set dispatch; the exact scene must fail on the missing `EditorAction` |
| 19 | Fresh XML identity is enforced | rename only the Native scene method, run a fresh passing Native task, then require `assert_kmp_ios_smoke.py` to fail on the missing exact tuple |
| 20 | Root Detekt reaches the new source sets | introduce a compile-valid `MaxLineLength` violation in a migrated source/test file; forced root `detekt` must fail and name it |

For controls 5–7, take at least one EN and one RU mutation across the set. For controls 8–10, the
topology gate must reject the forbidden construct before any iOS compiler failure could be
misclassified as the intended RED. For control 19, old XML must be removed by the fresh task; a
validator result over stale XML is void.

## 14. STOP conditions

STOP without production implementation or speculative workaround if, at implementation entry or
during the work:

- live `dev` materially changes the target, root seam, dependencies, tests, workflows, rules,
  goldens, route/result contract, or canonical migration authority;
- any sibling feature production file must change;
- any dependency version, catalog, compiler policy, convention plugin, ruleset, required context,
  workflow filter, or baseline change is required, or any suppression beyond the exact two
  Section 11.1 test-file annotations is required;
- a global `ResourceWrapper` redesign is required;
- expect/actual, a placeholder implementation, service locator, CompositionLocal DI, or static
  factory registry becomes necessary;
- `app:common` itself must become KMP;
- exact route, result, Store, save/back/discard, or typed-result behavior cannot be preserved;
- common previews cannot remain viable without deletion or a new platform policy;
- Native production composition cannot execute on available iOS simulator infrastructure;
- Android appearance requires recording or accepting changed goldens;
- a generic Gradle guarantee is missing from the convention plugin;
- local simulator/device/signing/toolchain infrastructure needed for real evidence is unavailable;
  or
- the exact Section 9 boundary is insufficient.

A STOP produces a prerequisite proposal or amended specification, never an implicit scope
expansion.

## 15. Signed, bisect-green implementation commit plan

After this prerequisite rebaseline PR merges and the maintainer gives a new explicit GO, use three
English Conventional Commits. Every commit is signed, locally verified, pushed, and GitHub
Verified.

1. **`refactor(kmp): share plan editor feature entry`**
   - target KMP build/source/resources/tests/scene;
   - explicit AppRootDeps/AppGraph/App/host/graph/`PlanEditorFeature(factory).processor(screen)`
     factory flow;
   - `app:common` API visibility and explicit-root identity test;
   - focused topology-independent compile, Android-host, Native, graph, device, and PNG proof.
2. **`ci(kmp): gate shared plan editor feature`**
   - exact topology/resource/root/reader contract;
   - Native task, upload path, and all 43 exact target XML tuples;
   - fresh focused and repository gates, including all mandatory controls.
3. **`docs(kmp): record Phase 7.7 evidence`**
   - append actual commands, XML counts/identities, red/green controls, PNG hashes, commit SHAs,
     CI runs, review disposition, and delivery state;
   - update only canonical facts made stale by the completed implementation.

Each commit must be green against its own tree. The first commit may not rely on an uncommitted CI
oracle from the second. The third contains no production fix. Do not squash away the evidence
structure locally; the maintainer owns merge strategy.

## 16. Exit criteria

Phase 7.7 implementation is complete only when all are true:

- the original specification and this prerequisite rebaseline were merged before implementation,
  and the maintainer gave a new explicit GO;
- target production compiles from commonMain for Android and `iosSimulatorArm64`;
- exact 32-file topology and two private catalogs hold, with no legacy/platform residue;
- visible EN/RU copy, format placeholders, previews, test tags, route fields, and behavior are
  unchanged;
- the explicit root factory flows through the admitted generation, the required
  `AppNavigationHost` and `planEditorGraph` parameters, and the `PlanEditorFeature(factory)`
  constructor; `processor(screen)` remains the exact override and invokes the factory only for
  Store creation;
- admitted dependency resolution is exactly one and both rejected cases are zero;
- every route entry owns a distinct graph/Store and the three extension identities pass through
  the explicit AppGraph accessor;
- all 42 existing identities execute on Android host and Native with no skip/failure/error;
- the one real production Native scene executes and all 43 target Native tuples validate exactly;
- the three Android journeys preserve entry/save/back/return, typed result, and caller Store
  identity;
- all positive gates and all 20 known-negative controls have fresh accepted evidence;
- all 456 Paparazzi and all 484 repository PNG entries are mode/blob/path identical;
- Android remains releasable; stable contexts, rulesets, versions, policy, and settings remain
  unchanged;
- implementation commits are signed and GitHub Verified, CI is green, and review threads are
  classified/answered/resolved; and
- the PR remains open for the maintainer to merge.

## 17. Boundary and remaining ordered roadmap

Phase 7.7 removes one of the 11 feature-entry `Context.appDeps` readers and leaves exactly ten
navigation feature entries plus the separate app-dialog reader. It proves another explicit
root-factory slice; it does not authorize a batch migration.

After Phase 7.7 completes, the ordered frontier is:

1. remeasure and specify the next one of the ten remaining navigation entries; the current size
   order begins `archive`, `all-trainings`, `all-exercises`, and `past-session`, but no name is
   pre-authorized and paging/golden/device risk must be re-evaluated;
2. continue the remaining navigation entries one bounded slice at a time, keeping Android
   releasable and solving data/resource/platform seams at their owners;
3. migrate the separate app-dialog entry reader and explicitly settle its recovery/activity
   boundary;
4. convert real `app:common` only after every feature entry, dialog host, recovery branch,
   navigation host, resource owner, and root dependency is portable;
5. add the permanent iOS host through real `app:common`, preferring direct framework integration
   and covering its first window with XCTest/XCUITest; and
6. finish iOS-owned runtime/recovery, database/filesystem, image acquisition, Google auth/Drive,
   observability substitute, signing, CI, TestFlight, and release under separate measured specs.

A throwaway `iosApp` that bypasses `app:common` remains forbidden. CocoaPods remains unneeded unless
later direct integration is measured infeasible. Existing generation replacement, admission,
retirement, persistence, navigation-result, and recovery specifications remain authoritative.

## 18. No implementation authorization

This prerequisite rebaseline is documentation-only. Its merge records that PR #271 closed the
original BackHandler STOP and establishes the corrected measured contract, but it does not
authorize Gradle, Kotlin, resource, workflow, script, test, generated-file, ruleset,
repository-setting, or production migration work. Phase 7.7 implementation may begin only after
this rebaseline merges and the maintainer gives a new, separate, explicit GO.

## 19. Implementation evidence

This section records the bounded implementation authorized by the maintainer GO on 2026-08-31.
Section 18 remains the historical authorization boundary of prerequisite PR #272; the later GO
changed only that authorization state and did not relax any scope, STOP condition, or proof
requirement in this specification.

### 19.1 Entry, commits, and changed boundary

The isolated implementation worktree was created from exact `origin/dev`
`74878e68fb9d029b1661179542a4c9b8d68abb8b`. PRs #270, #271, and #272 were all `MERGED`, PR #272's
merge commit and `origin/dev` were both that exact SHA, the dynamic Section 12.2 merge-SHA ancestry
assertion passed, and the open-PR plus local/remote branch inventories contained no conflicting
implementation. The old stopped Phase 7.7 worktree was classified and preserved rather than
reused or discarded. Local SSH signing was proven before the first commit.

| Commit | SHA | Boundary and verification |
| --- | --- | --- |
| `refactor(kmp): share plan editor feature entry` | `1525a041fd668d6bc5c06e72cad119a88c97aaf9` | exact target/root implementation; locally valid SSH signature; GitHub `verified: true`, `reason: valid`; bisect-green |
| `ci(kmp): gate shared plan editor feature` | `931c387c5a844b303ca1308dc335a05fa0058404` | exactly the three Section 9 CI/gate paths; locally valid SSH signature; GitHub `verified: true`, `reason: valid`; bisect-green |
| `docs(kmp): record Phase 7.7 evidence` | `4a1efd030f20a60db84d50a323141e54af0561e7` | documentation only; locally valid SSH signature; GitHub `verified: true`, `reason: valid`; bisect-green |
| `fix(kmp): remove production preview suppression` | this commit | integrates the merged PR #274 amendment and removes the unauthorized production suppression without changing preview state, names, themes, or behavior |

The implementation diff is confined to the Section 9 manifest: 23 production Kotlin moves to
`commonMain`, two byte-identical EN/RU catalog moves, six portable suite moves to `commonTest`, one
new iOS production scene, the six allowed root/consumer files, the target build file, the three
CI/gate files, this evidence section, and the exact stale facts in `architecture.md`, `testing.md`,
and `ci-cd.md`. No sibling feature production, convention, catalog, version, compiler, ruleset,
baseline, filter, golden, repository setting, or graph-extension handoff changed.

### 19.2 Untouched baseline and focused positive evidence

Before implementation, fresh XML reproduced the six target JVM suites and all 42 Section 3.2
cases, the current six-module Native invocation's 51 cases, the three exact
`PlanEditorExtensionIdentityTest` identities, and the three editor journeys plus three
`UiAdmissionRaceTest` identities on portrait Pixel 6 API 34 `emulator-5554`; every case had zero
failure, error, or skip. The topology oracle passed, and the two PNG manifests matched Section 3.7.

Every load-bearing Gradle invocation below used
`--rerun-tasks --no-build-cache --no-configuration-cache --console=plain`; multi-task repository
commands also used the specified `--continue`. Fresh focused results were:

| Command | Executed result | Structurally parsed result |
| --- | ---: | --- |
| `python3 .github/scripts/assert_kmp_ui_source_topology.py` | GREEN | exact 32 target files, catalogs, semantics, previews, factory flow, API edge, and 11 readers |
| `./gradlew :feature:plan-editor:assembleDebug ...` | `215 actionable tasks: 215 executed` | Android target assembled |
| `./gradlew :feature:plan-editor:testAndroidHostTest ...` | `156 actionable tasks: 156 executed` | six suites; exact Section 3.2 membership; 42 tests; 0/0/0 failure/error/skip |
| seven-module `iosSimulatorArm64Test` invocation from Section 12.3 | `197 actionable tasks: 197 executed` | target 42 portable cases plus the exact production scene = 43; all seven modules accepted; 0/0/0 |
| `./gradlew :app:common:assembleDebug ...` | `297 actionable tasks: 297 executed` | explicit common consumer compiled |
| focused `PlanEditorExtensionIdentityTest` command | `531 actionable tasks: 531 executed` | three exact identities; 0/0/0 |
| focused six-case `connectedDebugAndroidTest` command from Section 12.3 | `686 actionable tasks: 686 executed` | three editor journeys and three admission cases; 0/0/0 |

Android-host XML contained the exact 5/3/23/3/2/6 suite distribution for
`PlanEditorMapperTest`, `SetTypeUiModelTest`, `ClickHandlerTest`, `CommonHandlerTest`,
`NavigationHandlerTest`, and `PlanEditorStateRouteArgTest`. Native contained those same 42
normalized identities once each, plus exactly
`PlanEditorFeatureSceneIosTest.resourcesBranchesAndActionsRenderAndDispatch`. App JVM XML contained
exactly `extension resolves the store through the parent graph()`,
`store's app-scoped deps are the SAME instances the parent holds()`, and
`each extension carries its own route arg into the store state()`.

Device XML contained exactly the focused membership:

- `RouteReachabilityTest.planEditorOpensFromALiveSessionExerciseAndTheSessionReturns`;
- `NavigationResultTest.planEditorSaveReachesTheLiveSessionThatOpenedIt`;
- `StoreRetentionTest.liveWorkoutStoreSurvivesThePlanEditorRoundTrip`;
- `UiAdmissionRaceTest.admittedGeneration_composesTheRegion_andResolvesItsDependencies`;
- `UiAdmissionRaceTest.retiredGeneration_composesNothing_andResolvesNothing`; and
- `UiAdmissionRaceTest.retirementBetweenPublicationAndFrame_resolvesNothing`.

### 19.3 Repository, device, and PNG evidence

After `./gradlew clean`, the Section 12.4 repository commands produced only executed summaries:

| Command | Result |
| --- | ---: |
| `./gradlew assembleDebug lintDebug testDebugUnitTest ... --continue` | `2105 actionable tasks: 2105 executed` |
| `./gradlew detekt ... --continue` | `57 actionable tasks: 57 executed` |
| `./gradlew assembleDebugAndroidTest ... --continue` | `1940 actionable tasks: 1940 executed` |
| `./gradlew verifyPaparazziDebug ...` | `621 actionable tasks: 621 executed` |
| `./gradlew :lint-rules:test ...` | `9 actionable tasks: 9 executed` |
| `python3 documentation/personal_data_gate.py -v` | GREEN |

The canonical Smoke run produced `2013 actionable tasks: 2013 executed`. Its 14 fresh module XML
files contained 44 unique cases: 41 executed and only the three documented skips
`ArchiveScreenTest.pendingFeatureRewrite`, `AllTrainingsScreenTest.pendingFeatureRewrite`, and
`AllExercisesScreenTest.pendingFeatureRewrite`; failures and errors were zero. The two MVI Smoke
oracle identities executed exactly once. A preceding attempt lost the ADB server during
`feature:archive`; it was rejected as transport evidence, all 14 stale result XML files were
removed, and the accepted run was fresh.

The canonical Regression run also produced `2013 actionable tasks: 2013 executed`. Its 14 fresh
module XML owners were `app:app`, `core:data:database`, `core:data:exercise`, `core:ui:kit`,
`core:ui:mvi`, `feature:all-exercises`, `feature:all-trainings`, `feature:app-dialogs:impl`,
`feature:archive`, `feature:exercise`, `feature:exercise-chart`, `feature:live-workout`,
`feature:settings`, and `feature:single-training`. They contained 81 unique executed cases with
zero failure, error, or skip; the per-owner counts were respectively
49/30/1/0/0/1/0/0/0/0/0/0/0/0. The device rotation setting was restored to its original `1/0`
state after testing.

The final Paparazzi manifest remained exactly 456 entries across 13 owners with mode/blob/path
hash `e5c47e60890fae85a76ccd4e97cd4de7364d7c285b4da19e5e5c55e8f3d3e9ff`. The full repository PNG
manifest remained exactly 484 entries with mode/blob/path hash
`f2c4a01eed26b232856face73048dd831759fead6a3b173b86e848bedce4721d`. No golden was recorded or
rewritten.

### 19.4 Mandatory known-negative controls

All controls used the required fresh GREEN -> named observable RED -> exact byte/path restoration
-> fresh GREEN protocol. Kotlin/XML controls used `documentation/mockups/mutation_harness.py`;
path controls used scratch byte copies with automatic restoration and post-restore equality. No
compile-invalid or sandbox-blocked run was accepted as RED.

| # | Observable RED at the mutation | Restored GREEN |
| ---: | --- | --- |
| 1 | missing exact target path named by topology | topology GREEN |
| 2 | copied `commonMain` production file named as forbidden `src/main` residue | topology GREEN |
| 3 | same-count wrong relative path reported as exact missing plus extra paths | topology GREEN |
| 4 | inserted Android import named by common platform-import check | topology GREEN |
| 5 | same-count EN key rename reported as exact catalog mismatch | topology GREEN |
| 6 | RU value edit reported as exact catalog mismatch | topology GREEN |
| 7 | same-count catalog move reported the wrong resource owner | topology GREEN |
| 8 | compile-valid `ErrorType(val msgRes: Int)` reported the payload-free violation | topology GREEN |
| 9 | compile-valid `ResourceWrapper` import/call in `ClickHandler` reported forbidden handler lookup | topology GREEN |
| 10 | Android BackHandler import reported forbidden platform API and missing portable BackHandler | topology GREEN |
| 11 | removed `AppRootDeps` accessor reported the missing exact root accessor | topology GREEN |
| 12 | graph resolution moved outside the Store lambda broke the named factory-placement edge | topology GREEN |
| 13 | double root resolution failed the admitted identity with expected 1, observed 2 | focused device pre/post GREEN, `686/686` |
| 14 | pre-admission root resolution failed both retired/race identities with expected 0, observed 1 | focused device pre/post GREEN, `686/686` |
| 15 | fixed valid route failed the extension-local identity with expected `ex-1`, observed `fixed` | app identity pre/post GREEN, `531/531` |
| 16 | `OnAddSet` appended `FAILURE`; both exact handler identities failed on Android host and Native | combined host/Native pre/post GREEN, `227/227` |
| 17 | blank production scene failed on missing `PlanEditorScreen` semantics | focused scene Native pre/post GREEN, `103/103` |
| 18 | suppressed add dispatch failed with expected `EditorAction(OnAddSet)`, observed `[]` | focused scene Native pre/post GREEN, `103/103` |
| 19 | renamed scene produced 43 passing Native cases, while the XML oracle reported the exact expected tuple count 0 and named the substitute | seven-module pre-GREEN `197/197`; feature restore GREEN `103/103`; oracle accepted exact 43 |
| 20 | compile-valid long assertion failed `feature:plan-editor:detekt` and named `PlanEditorMapperTest.kt:55` / `detekt.MaxLineLength` | root Detekt pre/post GREEN, `57/57` |

### 19.5 Explicit local limitations and remote evidence boundary

- The default macOS `/usr/bin/python3` is 3.9.6, while the unchanged shell gate uses syntax
  supported by the workflow's pinned Python 3.12; the exact local command stopped with a parser
  error before evaluating the mockup.
- The same gate's static checks pass under compatible Homebrew Python. Both installed Chrome and a
  separately extracted supported Chromium hung in macOS headless `--dump-dom` and reached the
  gate's own 90-second timeout. Therefore the positive browser direction and permanent
  `f52462c7` known-negative direction are explicitly **unverified locally**; the unchanged Linux
  `Mockup Appearance Gate` is their required remote authority.
- Three inherited, contractually exact test names contain commas. Kotlin/Native rejects those names
  without `@file:Suppress("INVALID_CHARACTERS_NATIVE_ERROR")`, so the two exact Section 11.1
  test-file annotations preserve the identities but produce the compiler's warning that such names
  have unspecified behavior. They are the only Phase 7.7 suppressions; production contains none.
- The convention-provided portable BackHandler currently emits its upstream deprecation warning;
  no alternate API or module-local dependency was introduced.
- A sandbox-blocked app-common invocation, a sandbox-blocked first mutation-harness attempt, an
  initial pre-clean Detekt failure, and the ADB transport failure above were excluded rather than
  counted as evidence; each owning proof was rerun freshly to an accepted result.
- This documentation commit must exist before its SHA, PR URL, GitHub CI run/job IDs, check
  conclusions, and independent review threads can exist. Those facts, including every finding's
  `correct`, `correct-but-already-decided`, `wrong`, or `correct-and-new` disposition, are recorded
  in the live open PR evidence. They are not predeclared here.

### 19.6 Independent suppression correction

An independent audit classified the production `@Suppress("MagicNumber")` on
`PlanEditorScreenPreview` as `correct-and-new`: the annotation contradicted Sections 0.2, 12.5,
and 14, and made the production-suppression claim in Section 19.5 false. Removing either test-file
annotation independently made Kotlin/Native compilation invalid on the three inherited
comma-bearing identities, so merged amendment PR #274 authorized exactly those two Section 11.1
annotations and no production exception.

The follow-up correction integrates authoritative `dev` merge
`2e0776979e7d4f83ee7e4432399b3c4d034fcc15`, removes the production annotation, and replaces only
the preview fixture's eight numeric literals with named private constants. The two preview names,
LIGHT/DARK modes, sample State, rendered values, action sink, production behavior, route/result
contract, test identities, and PNG ownership remain unchanged. Final remote gate and fresh review
facts are recorded in the live PR evidence because they arise only after this correction commit.
