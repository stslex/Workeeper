# KMP Phase 7.5 — `core:ui:plan-editor` becomes the shared plan-presentation leaf

**Status:** COMPLETE — implementation PR #267 merged into `dev` on 2026-08-29.

**Delivery:** base `a34884ad7154edeaeee3cff2b6df8bb334ce9343` · reviewed head
`9157bd8dc951cfbc270ebddcd3f0f5c8f56fc599` · merge
`479a2960574ff165f4f6b99a24d49ab1c961bbd8`. All final-head workflows succeeded; all three
implementation commits were signed/GitHub-Verified; the review inventory contained zero threads
and zero submitted reviews; the active `~ALL` ruleset and both required contexts remained
unchanged.

**Target branch:** `dev`

**Specification baseline:** `ceae5dee282b182ef0946ba40e59e67c6d1a45e7` — verified merge
commit of Phase 7.4 implementation PR #265

---

## 0. Authority and entry condition

This document is the implementation authority for Phase 7.5 only after it merges and the
maintainer gives a separate, explicit implementation GO. A docs-only specification PR does not
authorize production, test, Gradle, workflow, repository-setting, or generated-artifact changes.

The specification is measured from the exact `dev` commit above and refines the frontier in:

- `AGENTS.md`;
- `documentation/feature-specs/kmp-phase-2-probes.md`;
- `documentation/feature-specs/kmp-phase-6-data-layer.md`;
- `documentation/feature-specs/kmp-phase-7-1-ui-kit.md`;
- `documentation/feature-specs/kmp-phase-7-2-navigation.md`;
- `documentation/feature-specs/kmp-phase-7-3-mvi.md`;
- `documentation/feature-specs/kmp-phase-7-4-start-mode.md`;
- `documentation/architecture.md`, `documentation/testing.md`, and
  `documentation/ci-cd.md`.

At discovery time:

- `dev` pointed exactly to the specification baseline and the local tree was clean;
- Phase 7.4 PR #265 was merged, all five final head checks were successful, and its three commits
  were signed/Verified;
- no pull request was open;
- the active repository ruleset was `all` (id `8116593`, selector `~ALL`), requiring signed
  commits plus `Build and Unit Tests` and `KMP iOS kit smoke`;
- the dedicated `dev` ruleset (id `18553518`) remained disabled; and
- the Phase 7.5 branch did not exist.

Before implementation, re-fetch `dev` and repeat the source, resource, consumer, test, golden,
workflow, and ruleset inventory. If the baseline or dependency frontier changed materially, STOP
and update this specification instead of adapting it by analogy.

## 1. Decision and bounded exit claim

Phase 7.5 converts **only** `core:ui:plan-editor` from the classic Android Compose-library shape
to the repository's KMP Compose-library convention, targeting Android and
`iosSimulatorArm64`.

The module is the next forced dependency-frontier step because:

- its 13 production Kotlin files contain no `android.*`, `java.*`, or `javax.*` API;
- its only production Android dependency is one `R.string` read in `PlanSetCard`;
- its only real project dependency is the already-shared `core:ui:kit`;
- its reducer is already pure Kotlin and has 19 focused tests;
- its three golden classes already protect 18 Android-host images; and
- the remaining blocker is a bounded resource-ownership problem with five known Android feature
  call sites, not an unresolved runtime or DI design.

At exit:

> The exact current plan-editor production surface compiles from `commonMain` for Android and the
> iOS simulator. Its read-only empty copy is a private CMP resource. Android features resolve
> feature-owned presentation copy from their own resource catalogs, with no cross-module Android
> `R` access. The 19 reducer cases execute as common tests, all 18 RU goldens remain byte-identical,
> and one native CMP scene renders both read-only and editable production branches and dispatches
> one real add-set action.

This is not the first feature conversion. Even the small `feature:image-viewer` candidate still
resolves its graph through `LocalContext.appDeps`, builds Coil requests from `LocalContext`, and
owns Android resources. `app:common` still depends on all twelve Android feature entry providers,
`feature:app-dialogs:impl`, and `feature:recovery`. Choosing a feature or host now would therefore
conflate UI sharing with the unresolved feature/root composition contract.

## 2. Measured baseline

All measurements below are from the specification baseline.

| Surface | Baseline |
| --- | --- |
| Gradle shape | `convention.composeLibrary` + serialization inherited indirectly + Paparazzi + shared golden gate |
| Production Kotlin | 13 files, 838 physical lines under `src/main/kotlin` |
| Platform coupling | one `androidx.compose.ui.res.stringResource` import and one module-local Android `R.string` access, both in `PlanSetCard.kt`; no `android.*`, `java.*`, or `javax.*` API |
| Android resources | 14 EN keys + the same 14 RU keys under `src/main/res` |
| Unit tests | `PlanDraftReducerTest`: 19 methods under `src/test/kotlin` |
| Golden tests | 3 classes, 9 parameterized methods, 18 RU dark/light executions/images |
| Direct Gradle consumers | 6: `past-session`, `plan-editor`, `live-workout`, `exercise`, `single-training`, `exercise-chart` |
| Real project dependency | `core:ui:kit`; current `core:core` and `core:data:exercise` edges have zero production imports |
| Cross-module Android resources | 5 Kotlin files, 12 `CoreEditorR.string.*` call sites, 3 owning feature modules |
| Repository visual baseline | 456 PNGs across 13 live golden gates |
| Canonical device baseline | Smoke 44 discovered / 41 executed / 3 named skips / 0 failures; Regression 81/81 |

The production/resource/test anchors are:

| File | Git blob |
| --- | --- |
| `build.gradle.kts` | `66d6f810605664dc0c09223b8be201c7024f991c` |
| `ExercisePickerBottomSheet.kt` | `45ab33220b969e5c271e7ddc5eabf3bc75e891aa` |
| `PlanEditorBody.kt` | `7821b9857620f8a991ff0929b10b945ae876db89` |
| `PlanSetCard.kt` | `9ba76ffa35fe312f0b0ee55bfbb084671ef8bbe7` |
| `TypeToggle.kt` | `8367e91eb63ea81a811954f49816acbeaf5afe40` |
| `domain/PlanDraftReducer.kt` | `4cd0393e5b80805c69fae9b1eb621cda36fe57a9` |
| `model/ExercisePickerAction.kt` | `5483fcb75b44b489c2fc1919241adc190ea8e78f` |
| `model/ExercisePickerUiModel.kt` | `086327415ecb238d460d26cc23de5ebb41bbbc9e` |
| `model/ExerciseTypeUiModel.kt` | `a6fbc07e47bb7e168c9e03ed4ad996c1328026c0` |
| `model/PlanDraftResult.kt` | `e5d83fc5d6b546c8050df3b5bafa142073b126bb` |
| `model/PlanEditorBodyAction.kt` | `f5d5e3e3b2d166d906dc5de4517e8c43452141d4` |
| `model/PlanEditorUIMapper.kt` | `bc035ff8d140b53c009aa941ebe65395a3182eba` |
| `model/PlanSetUiModel.kt` | `d1bcb8898c51e75a9783ca8a02bff79d95de87fe` |
| `model/SetTypeUiModel.kt` | `bde28597db4bfc4d34220bd83468768cc5242769` |
| English `strings.xml` | `72767b51fb09083f93e8a5023f63cf098a217244` |
| Russian `strings.xml` | `a7bfc7f3681d8254577e763a7acaab2de22d3f1f` |
| `PlanDraftReducerTest.kt` | `5ab1bea662ad8e2168313df93e000c6b2bb729d5` |
| `PlanEditorBodyGoldenTest.kt` | `5f1e4808a3ce8e9e040628c1c12f4ef890f8c500` |
| `PlanSetCardReadOnlyGoldenTest.kt` | `606016f81ec81bea7a5909eb6833a593128773ad` |
| `TypeToggleGoldenTest.kt` | `e06b358e8fe883ee1191d12ceb696ab0a04e0dbf` |

The 18 PNGs are compatibility data. `subject` below is the filename suffix after the stable class
identity and before `.png`.

| Subject | Dimensions | Bytes | Git blob | SHA-256 |
| --- | --- | ---: | --- | --- |
| `PlanEditorBody_emptyDraft_dark` | 1078 x 400 RGBA | 17,639 | `b60761f2735c37955a112f1a387cc089110d97eb` | `e2c94134d0edb11188d80c839b0839079b7b9e33aa58977955a11e7b6c4492af` |
| `PlanEditorBody_emptyDraft_light` | 1078 x 400 RGBA | 17,469 | `b74b5e127a353939f41296245c2d48c76fed9239` | `20a613fd49582ea0224d612ea7823f92a4868e86ca3ff9ecef838796873b3336` |
| `PlanEditorBody_weightedDraft_dark` | 1078 x 883 RGBA | 40,677 | `7dd893fb3e68ff83bf64b1e8511ce537fb14962c` | `f568666fef442c051d83e8d782d9dc0d2f80ccfafa30e4a9abf046212fa24cd1` |
| `PlanEditorBody_weightedDraft_light` | 1078 x 883 RGBA | 39,921 | `7edb3aa7280ac301d5cd99b0fdbb00b474aca104` | `13e4f359e02b18eee819df0df4282c9a73b2f80abbfa63415cbe11cbd5ede872` |
| `PlanEditorBody_weightlessDraft_dark` | 1078 x 883 RGBA | 30,255 | `169f1281a4c08acd7cb0de9062bd2f1c5212ad48` | `5f5de6301eac700a33ece2cc16ab647aae98bb9732363b2e193085c08833f62d` |
| `PlanEditorBody_weightlessDraft_light` | 1078 x 883 RGBA | 29,556 | `5f455f5f557b033a40429130decbba1b32373732` | `39f52dd170a7d092009d0bd89f837aa2a03b64b3b2778126347a3488601a936d` |
| `ReadOnly_empty_dark` | 1078 x 207 RGBA | 8,360 | `1995cdbc7e80e59ce3d50a96adda560ea20717fd` | `5872e6b7a0c399bc6f00fafcc540748bdea09200ffa21c0d721820f73b83af5e` |
| `ReadOnly_empty_light` | 1078 x 207 RGBA | 8,187 | `fbde652a4fe80654e01327c3e555b94e44420071` | `ad51992f5de079346a75c5d6ccf766fca2cfdf1bb4628872d0928a45dc0cc11b` |
| `ReadOnly_fiveGlyph_dark` | 1078 x 286 RGBA | 10,789 | `2b05817ee3f1f544afd8304e483e6c89ffe20846` | `db47ca6a2f3f0cb7035ce6fde8f13949b32434f530e832c4b886d236ef83567b` |
| `ReadOnly_fiveGlyph_light` | 1078 x 286 RGBA | 10,413 | `c768cf5facd22246673d91deeb91e5503efea741` | `9dc9afbd0daf505da2a81a66857e10c782e2aa78422ad124e2a5844c3acb9aa6` |
| `ReadOnly_weighted_dark` | 1078 x 748 RGBA | 37,742 | `c8711d786a0a747afae86cc8a46cd6e8e55991ef` | `8437559c9176857ac8d118d9ca189fe01b351c5f34265fb5d90cb18c579aae22` |
| `ReadOnly_weighted_light` | 1078 x 748 RGBA | 36,994 | `d096e9de102c6472a407118147d0017c3877e60c` | `7bbaa228336600ba2b5cac34940d8ffca94d531ed05c8764cc91a7a20e0ce080` |
| `ReadOnly_weightless_dark` | 1078 x 748 RGBA | 27,352 | `7280fd45a7c2262ac3fcac0a8b2fc060d9c36323` | `247bd27a46fdc885edfd420084100a529bd9683295bc6bd07bde4ec9b9cb82ad` |
| `ReadOnly_weightless_light` | 1078 x 748 RGBA | 26,639 | `bd9697453b00426a9a6f478c569242bb1616eba6` | `9412e411ce1ceb1f02c6483ba5edf0d3a16b738e44986b4c9e72d817435c8bc7` |
| `TypeToggle_weighted_dark` | 1078 x 198 RGBA | 7,125 | `29a05f58f57742d69da6b2d0ad82ae6df6fb5c74` | `24204b62fd8729fccbb9bba506a7a08647c18b895b182cf20651ecfd8026f1ff` |
| `TypeToggle_weighted_light` | 1078 x 198 RGBA | 9,066 | `00674d3700c877c6d8b454588418af94ac471bf0` | `864817cc3740511b041ff2e6f9ac887c6b7f1fe00f87694c8cb415cb2e36c6ca` |
| `TypeToggle_weightless_dark` | 1078 x 198 RGBA | 7,097 | `6d93f35e5a9616e464363a4e7125dfcf39c664cc` | `6fd941fa4e55e5da5922d603157c46fc59a8502dddedffe30f3389b20e181161` |
| `TypeToggle_weightless_light` | 1078 x 198 RGBA | 9,024 | `51baf488482cc0862093d613fce05bfbe54b9007` | `d85724bf661a0badae0e31630c69cf4c1ce709a5945e1183b74bbc47336c7522` |

The local specification environment could not execute the Gradle wrapper because Gradle 9.6.1
was not cached and `services.gradle.org` was unreachable. That is not positive evidence. The
implementation entry gate in §11 must therefore establish the fresh focused baseline before any
edit; the successful Phase 7.4 PR-head checks only establish repository health at this baseline.

## 3. Resource-ownership decision

### 3.1 Why the generated plan-editor `Res` is not a public consumer API

Only `core_ui_plan_editor_read_plan_empty` is rendered by plan-editor production itself. It moves
to the module's private CMP catalog and is read with generated `Res` plus
`org.jetbrains.compose.resources.stringResource`. Set
`packageOfResClass = "io.github.stslex.workeeper.core.ui.plan_editor.resources"` and keep the
generated class non-public. Because `PlanSetCard` already imports kit `Res`, alias the new module
class as `PlanEditorRes`; do not rewrite the existing kit resource calls.

The other 13 keys are not plan-editor component resources:

- seven screen/error keys already have exact EN/RU copies in `feature:plan-editor`, and every live
  caller already uses that feature's local `R`;
- tooltip and weighted-to-weightless confirmation copy belongs to the feature screen/store that
  presents it; and
- set-removed toast copy belongs to the feature handler that owns undo state.

Making plan-editor's generated `Res` public would preserve the wrong ownership and would not solve
the handlers' integer `ResourceWrapper` contract. Moving the copy into `core:ui:kit` would pollute
the generic kit with feature/store events. Redesigning `ResourceWrapper` for CMP resources would
turn this leaf conversion into a feature KMP migration. All three are forbidden.

### 3.2 Exact final catalog

The text below is exact compatibility data. Newly feature-owned keys receive feature-unique names
so the final Android resource merge has one owner per identifier.

| Final owner/key | English | Russian |
| --- | --- | --- |
| `core:ui:plan-editor` / `core_ui_plan_editor_read_plan_empty` | `This exercise has no default plan.` | `У упражнения нет плана по умолчанию.` |
| `feature:plan-editor` / `feature_plan_editor_set_type_tooltip` | `Tap to cycle: warmup → work → failure → drop` | `Нажмите, чтобы переключить: разминка → рабочий → отказ → дроп` |
| `feature:plan-editor` / `feature_plan_editor_type_change_weightless_title` | `Switch to weightless?` | `Переключить на без веса?` |
| `feature:plan-editor` / `feature_plan_editor_type_change_weightless_body` | `Weight values from this exercise’s plans will be cleared. This cannot be undone.` | `Значения веса из планов этого упражнения будут очищены. Это нельзя отменить.` |
| `feature:plan-editor` / `feature_plan_editor_type_change_weightless_impact` | `All plan weights cleared` | `Все веса в планах очищены` |
| `feature:plan-editor` / `feature_plan_editor_type_change_weightless_confirm` | `Switch` | `Переключить` |
| `feature:exercise` / `feature_exercise_edit_plan_set_type_tooltip` | `Tap to cycle: warmup → work → failure → drop` | `Нажмите, чтобы переключить: разминка → рабочий → отказ → дроп` |
| `feature:exercise` / `feature_exercise_edit_plan_type_change_weightless_title` | `Switch to weightless?` | `Переключить на без веса?` |
| `feature:exercise` / `feature_exercise_edit_plan_type_change_weightless_body` | `Weight values from this exercise’s plans will be cleared. This cannot be undone.` | `Значения веса из планов этого упражнения будут очищены. Это нельзя отменить.` |
| `feature:exercise` / `feature_exercise_edit_plan_type_change_weightless_impact` | `All plan weights cleared` | `Все веса в планах очищены` |
| `feature:exercise` / `feature_exercise_edit_plan_type_change_weightless_confirm` | `Switch` | `Переключить` |
| `feature:exercise` / `feature_exercise_edit_plan_set_removed` | `Set removed` | `Подход удалён` |
| `feature:single-training` / `feature_training_edit_plan_set_removed` | `Set removed` | `Подход удалён` |

The former cross-module identifiers map exactly as follows:

| Former core key | Feature owner | Replacement key |
| --- | --- | --- |
| `core_ui_plan_editor_set_type_tooltip` | `feature:plan-editor` | `feature_plan_editor_set_type_tooltip` |
| `core_ui_plan_editor_set_type_tooltip` | `feature:exercise` | `feature_exercise_edit_plan_set_type_tooltip` |
| `core_ui_plan_editor_type_change_weightless_title` | `feature:plan-editor` | `feature_plan_editor_type_change_weightless_title` |
| `core_ui_plan_editor_type_change_weightless_title` | `feature:exercise` | `feature_exercise_edit_plan_type_change_weightless_title` |
| `core_ui_plan_editor_type_change_weightless_body` | `feature:plan-editor` | `feature_plan_editor_type_change_weightless_body` |
| `core_ui_plan_editor_type_change_weightless_body` | `feature:exercise` | `feature_exercise_edit_plan_type_change_weightless_body` |
| `core_ui_plan_editor_type_change_weightless_impact` | `feature:plan-editor` | `feature_plan_editor_type_change_weightless_impact` |
| `core_ui_plan_editor_type_change_weightless_impact` | `feature:exercise` | `feature_exercise_edit_plan_type_change_weightless_impact` |
| `core_ui_plan_editor_type_change_weightless_confirm` | `feature:plan-editor` | `feature_plan_editor_type_change_weightless_confirm` |
| `core_ui_plan_editor_type_change_weightless_confirm` | `feature:exercise` | `feature_exercise_edit_plan_type_change_weightless_confirm` |
| `core_ui_plan_editor_toast_set_removed` | `feature:exercise` | `feature_exercise_edit_plan_set_removed` |
| `core_ui_plan_editor_toast_set_removed` | `feature:single-training` | `feature_training_edit_plan_set_removed` |

The seven already-local `feature:plan-editor` identifiers remain unchanged, despite their legacy
`core_ui_plan_editor_*` prefix:

- `screen_title_format`, `screen_title_default`, `screen_back`, `screen_save`, `screen_cancel`;
- `error_load`; and
- `error_save`.

Only their redundant copies disappear from the core catalog. Renaming already-local identifiers
would add unrelated call-site churn and is not authorized.

## 4. In scope

1. Replace `convention.composeLibrary` with `convention.kmpComposeLibrary` in
   `core/ui/plan-editor`, retain Paparazzi and `golden-gate.gradle.kts`, and apply the serialization
   plugin explicitly.
2. Move all 13 production Kotlin files with history to `commonMain`.
3. Create private CMP EN/RU catalogs containing exactly the one core-owned key in §3.2.
4. Remove the other 13 keys from the core module: seven already-local duplicates and six former
   cross-module Android resource contracts.
5. Add the exact feature-owned EN/RU keys in §3.2 to the existing resource files of
   `feature:plan-editor`, `feature:exercise`, and `feature:single-training`.
6. Change exactly the five Kotlin consumers in §7 from `CoreEditorR` to their already-imported
   local feature `R` and the new feature-owned identifiers.
7. Remove the two dead project dependencies and declare only the KMP dependencies required by
   the unchanged public surface.
8. Move `PlanDraftReducerTest` to `commonTest`, convert its three JUnit imports to `kotlin.test`,
   and keep all 19 method identities and assertions.
9. Move all three golden classes and all 18 PNGs with history to `androidHostTest`, preserving
   identities, locale, parameters, filenames, dimensions, metadata, and bytes.
10. Add exactly one `iosTest` production-scene test as specified in §9.3.
11. Extend the shared-UI topology/resource oracle and the existing native XML identity gate.
12. Extend the stable native workflow invocation and artifact paths without renaming its context.
13. Update only documentation made stale by the completed implementation and append exact
    implementation/delivery evidence here.

## 5. Explicit non-goals

This phase does not:

- convert any feature module, `core:ui:test-utils`, `app:common`, or an application host;
- add `iosApp`, Xcode, UIKit, `ComposeUIViewController`, `UIWindow`, XCTest, XCUITest, signing, or
  an iOS golden corpus;
- change plan editing, picker, reducer, serialization, formatting, tooltip, confirmation, undo,
  scrolling, read-only, or empty-state behavior;
- change any public Kotlin declaration, enum entry/order, serial name, callback, default,
  nullability, semantics tag, or preview subject;
- make the plan-editor generated `Res` public or expose a resource-ID/resource-wrapper façade;
- redesign `ResourceWrapper`, handlers, Stores, DI, navigation, persistence, or feature state;
- move the rehomed Android strings to `core:ui:kit`;
- add `androidMain`, `iosMain`, `androidDeviceTest`, expect/actual shims, or placeholder actuals;
- re-record, rename, normalize, optimize, or tolerate any PNG;
- change dependency versions, the version catalog, global convention behavior, compiler policy,
  rulesets, required-context names, baselines, suppressions, or test filters; or
- clean up stale names/comments/APIs unrelated to the source-set and ownership changes above.

The future permanent iOS host's direct framework-integration preference is recorded in §17. It
does not authorize host work, CocoaPods work, or an interim throwaway app in this phase.

## 6. Required module topology

The final module has exactly 38 files below `src`:

| Source set | Exact contents |
| --- | --- |
| `commonMain/kotlin` | the 13 baseline Kotlin files at the same package-relative paths |
| `commonMain/composeResources/values` | `strings.xml`, exactly one key |
| `commonMain/composeResources/values-ru` | `strings.xml`, exactly one key |
| `commonTest/kotlin` | `domain/PlanDraftReducerTest.kt` |
| `androidHostTest/kotlin` | `golden/PlanEditorBodyGoldenTest.kt`, `golden/PlanSetCardReadOnlyGoldenTest.kt`, `golden/TypeToggleGoldenTest.kt` |
| `androidHostTest/snapshots/images` | the exact 18 existing PNG filenames |
| `iosTest/kotlin` | exactly one new `PlanEditorSceneIosTest.kt` |

Use `git mv` for every existing Kotlin, XML, test, and PNG path. Twelve production Kotlin blobs
remain byte-identical. `PlanSetCard.kt` changes only the Android resource import/access to the
module's generated private CMP resource. Update a durable build comment only where its old
`src/test` path becomes false.

No file may remain under legacy `src/main`, `src/test`, or `src/androidTest`; no Android resource
may remain under `src/main/res`; and no Kotlin may appear under `androidMain`, `iosMain`, or an
unlisted source set.

Extend `.github/scripts/assert_kmp_ui_source_topology.py` with a second explicit module manifest.
It must compare exact paths, not counts, and retain the Phase 7.4 start-mode manifest unchanged.

## 7. Exact Android consumer boundary

Only these production Kotlin files may change outside the core module:

| Feature | File | Ownership change |
| --- | --- | --- |
| `feature:plan-editor` | `ui/PlanEditorScreen.kt` | tooltip → local feature `R` |
| `feature:plan-editor` | `ui/mvi/handler/ClickHandler.kt` | four type-change strings → local feature `R` |
| `feature:exercise` | `ui/ExerciseEditScreen.kt` | tooltip → local feature `R` |
| `feature:exercise` | `ui/mvi/handler/ClickHandler.kt` | four type-change strings + set-removed toast → local feature `R` |
| `feature:single-training` | `mvi/handler/ClickHandler.kt` | set-removed toast → local feature `R` |

All five files already import their own feature `R`; remove only the `CoreEditorR` alias and
replace the 12 addressed identifiers. The surrounding state/event/control flow remains
byte-identical. The only allowed external XML changes are the existing EN/RU `strings.xml` files
for those three feature modules.

The topology/resource oracle must additionally prove:

- no source file imports `io.github.stslex.workeeper.core.ui.plan_editor.R`;
- no `CoreEditorR` token remains;
- the core CMP catalogs contain exactly the one key/value pair in §3.2;
- each newly feature-owned identifier exists exactly in its specified EN/RU owner with exact text;
- the former six cross-module `core_ui_plan_editor_*` identifiers are absent from every catalog;
- the seven legacy screen/error identifiers exist only in `feature:plan-editor`, not in core; and
- there is no same-count owner/path substitution.

## 8. Build and public API contract

The KMP build keeps Paparazzi and the shared golden script. Dependencies belong to the narrowest
source set and reflect the unchanged public API:

| Configuration | Dependency | Reason |
| --- | --- | --- |
| plugin | `libs.plugins.serialization` | the classic convention supplied serializer generation indirectly; KMP does not |
| `commonMain` | `api(project(":core:ui:kit"))` | public `SetTypeUiModel.toUiKitType()` returns kit `SetType`; public enum fields also use kit-backed resource types |
| `commonMain` | `api(libs.kotlinx.collections.immutable)` | `ImmutableList` appears in public composable, reducer, and mapper signatures |
| `commonMain` | `api(libs.kotlinx.serialization.core)` | four public serializable types generate public serializer APIs |
| `commonMain` | `implementation(libs.cmp.material.icons.core)` | `ExercisePickerBottomSheet` directly imports `Icons`, `filled.Add`, and `filled.Search`; the KMP convention does not supply this artifact and kit does not export its own icon dependency |
| `commonTest` | `implementation(kotlin("test"))` | common reducer tests |
| `iosTest` | `implementation(kotlin("test"))`, `implementation(libs.cmp.ui.test)` | native production-scene test |
| `androidHostTest` | `implementation(project(":core:ui:golden-harness"))` | unchanged Paparazzi subjects |

Configure Compose resources with the exact package in §3.1 and do not set
`publicResClass = true`. The module's generated resource class is an implementation detail and
remains available to same-module tests.

Remove `core:core` and `core:data:exercise`; the baseline has zero source import from either.
Do not duplicate Compose dependencies supplied by the KMP convention. Do not remove any of the
six consumers' direct `core:ui:kit` edges as incidental dependency cleanup.

Preserve source-compatibly every public declaration, including:

- `ExercisePickerBottomSheet`, `PlanEditorBody`, and `PlanSetCard` signatures/defaults;
- `PlanDraftReducer.reduce` and `PlanEditorUIMapper.formatPlanSummary`;
- `ExercisePickerAction`, `PlanEditorBodyAction`, and every nested variant;
- `ExercisePickerUiModel`, `PlanDraftResult`, and `PlanSetUiModel` fields/types;
- `ExerciseTypeUiModel` and `SetTypeUiModel` entries/order, resource fields, and methods; and
- the serialized shape of all four `@Serializable` types.

All current semantics tags remain exact, including `PlanEditorBodyEmpty`,
`PlanEditorBodyRow_<index>`, weight/reps/type row tags, type-option tags, and kit set-bar tags.
If KMP conversion changes the callable JVM surface, serializer shape, or forces any unlisted
consumer adapter, STOP. Do not add a compiler override speculatively.

## 9. Test contract

### 9.1 Common reducer

Move `PlanDraftReducerTest` to `commonTest`, replace its three JUnit imports with the corresponding
`kotlin.test` imports, and preserve all 19 exact backtick method names and assertions. The test
must execute through both the Android-host aggregate and `iosSimulatorArm64Test`; a copied native
reducer suite is forbidden.

The oracle still covers default WORK-set creation, previous-value copying, safe head/tail/index
removal, every type/weight/reps update, negative-reps clamping, out-of-bounds no-ops, empty no-op,
and lifecycle-action no-ops.

### 9.2 Android-host goldens

Move the three classes without changing their package/class/method identities:

- `PlanEditorBodyGoldenTest`: `weightedDraft`, `weightlessDraft`, `emptyDraft`;
- `PlanSetCardReadOnlyGoldenTest`: `readOnlyWeighted`, `readOnlyWeightless`,
  `readOnlyFiveGlyphWeight`, `readOnlyEmpty`; and
- `TypeToggleGoldenTest`: `typeWeighted`, `typeWeightless`.

Each method still parameterizes both `GoldenTheme` values and uses `LOCALE_RU`, producing exactly
18 executions and the exact 18 PNGs in §2. The module and repository golden gates remain 18/18
and 456 PNGs across 13 modules. No re-record is allowed.

### 9.3 Native production scene

Add exactly one native test with this identity:

`io.github.stslex.workeeper.core.ui.plan_editor.PlanEditorSceneIosTest.readOnlyCopyAndEditableAddRenderAndDispatch`

Use CMP UI test v2 `runComposeUiTest` and production code:

1. set content to `AppTheme` with a state-selected production branch;
2. first render read-only `PlanSetCard` with an empty immutable plan and `onAction = null`;
3. disable auto-advance, advance one frame, re-enable it, and wait for idle;
4. resolve the module's migrated `core_ui_plan_editor_read_plan_empty` through generated `Res` and
   assert that exact production text and `PlanEditorBodyEmpty` are displayed;
5. switch the same composition to production `PlanEditorBody` with an empty draft, an action
   collector, and `scrollable = false`;
6. resolve/assert the kit-owned editable empty hint, assert `AppSetBarAdd` is displayed, and prove
   no action was observed before interaction;
7. click the real `AppSetBarAdd` node; and
8. assert exactly `[PlanEditorBodyAction.OnAddSet]` was observed.

The test proves Kotlin/Native compilation, module and kit CMP resource loading, production
read-only/editable composition, a real native headless frame, semantics, and dispatch. It does not
prove UIKit, Metal, app launch, feature Stores, persistence, or a permanent iOS host.

### 9.4 Consumers and device parity

Freshly assemble and run unit tests for all six direct consumers. The three owners with resource
changes require exact-copy/resource-oracle proof; the other three are clean-compile ABI consumers.
No consumer build dependency may be removed.

Run canonical Smoke and Regression when device infrastructure is available. Membership remains
Smoke 44/41/3 and Regression 81/81. This phase adds no Android device-test identity.

## 10. CI contract

The active required contexts remain exactly:

- `Build and Unit Tests`;
- `KMP iOS kit smoke`.

Under `Build and Unit Tests`, extend the existing shared-UI topology script with the exact
plan-editor manifest and §7 resource-ownership checks. Do not create a parallel script that can
drift from the manifest.

Under the stable native job:

- append `:core:ui:plan-editor:iosSimulatorArm64Test` to the existing forced Gradle invocation;
- upload `core/ui/plan-editor/build/test-results/iosSimulatorArm64Test/` even when the test step
  fails; and
- append the exact §9.3 identity to `.github/scripts/assert_kmp_ios_smoke.py` while retaining all
  kit, navigation, MVI, and start-mode identities.

The XML validator must require exactly one executed, zero skipped, and zero failed matching scene
case and must still inspect the complete result directory. Workflow exit status, aggregate totals,
or substring log matching are not evidence.

## 11. Required verification

Every forced Gradle gate uses `--rerun-tasks --no-build-cache`; add
`--no-configuration-cache` where Paparazzi/native guidance requires it. Test counts come from fresh
JUnit XML, not console totals or cached summaries.

### 11.1 Pre-change entry gate

Before editing:

1. prove rebased/pinned `dev` and a clean tree;
2. repeat the exact source/resource/test/golden/consumer inventory;
3. record all 18 PNG hashes, sizes, and dimensions;
4. run fresh focused assemble, unit, Paparazzi, and lint tasks for plan-editor;
5. prove 19 reducer plus 18 golden executions from XML;
6. prove all 12 external `CoreEditorR` call sites and their five-file boundary; and
7. record current ruleset, required contexts, repository golden count, and device-suite baseline.

Failure to establish this baseline is a STOP, not permission to reuse the documentation
environment's blocked Gradle attempt.

### 11.2 Focused positive gates

After conversion, run fresh:

- `:core:ui:plan-editor:assembleDebug`;
- `:core:ui:plan-editor:testDebugUnitTest`;
- `:core:ui:plan-editor:verifyPaparazziDebug`;
- `:core:ui:plan-editor:lintDebug`;
- `:core:ui:plan-editor:iosSimulatorArm64Test` on macOS/Xcode infrastructure;
- the extended shared-UI topology/resource oracle; and
- assemble/unit tasks for all six direct consumers.

Inspect XML for 19 common reducer cases, 18 Android-host golden cases, and exactly one native scene
case, all with zero skip/failure/error.

### 11.3 Repository gates

Run every repository command required by `AGENTS.md` and the touched surfaces:

- `assembleDebug`;
- `assembleDebugAndroidTest`;
- `verifyPaparazziDebug`;
- `:lint-rules:test`;
- `detekt`;
- the personal-data gate;
- `lintDebug`; and
- `testDebugUnitTest`.

Run Smoke and Regression fresh when device infrastructure is available and retain exact
membership/execution evidence.

### 11.4 Final diff proof

At implementation PR head, prove:

- only the core module, exact §7 feature/resource boundary, shared CI scripts/workflow, and stale
  documentation changed;
- the exact 38-file source manifest holds and all legacy paths are absent;
- common/native production has no Android `R`, Android/Java/Javax import, or platform shim;
- no source imports plan-editor Android `R` and every string owner/value matches §3.2;
- the 12 unaffected production Kotlin blobs and all 18 PNG hashes match baseline;
- all public source signatures, enum order, serializer behavior, test tags, and reducer behavior
  remain compatible;
- all six consumers retain their direct edges and compile/test;
- every pre-existing native XML identity remains in the validator;
- rulesets, required-context names, versions, golden counts, device membership, tolerances,
  suppressions, and baselines are unchanged; and
- implementation commits are signed and the PR has zero unresolved review threads.

## 12. Mandatory known-negative controls

Use the repository mutation harness for UTF-8 in-place anchor replacements. For PNG and
create/delete topology controls, preserve exact scratch copies and restore with `cp` in `finally`;
never use `git checkout` as recovery. Establish fresh GREEN, force the named RED, restore exact
bytes/tree shape, and re-run fresh GREEN.

| Mutation | Required RED oracle |
| --- | --- |
| leave one Kotlin file in `src/main` or add a same-count replacement path | shared-UI topology manifest |
| add Android `R`/`androidx.compose.ui.res` to `commonMain` | topology oracle and native compile |
| restore one `CoreEditorR` import/call | resource-ownership oracle |
| move one feature-owned key to the wrong catalog while preserving totals | resource-ownership oracle |
| mutate one EN/RU feature-owned value | exact resource-ownership oracle |
| mutate the RU read-only empty copy | both `readOnlyEmpty` Paparazzi cases |
| change reducer default reps/type or suppress one safe-index guard | corresponding common reducer identity |
| replace the native scene with blank content | native resource/semantics assertions |
| suppress or duplicate the native add callback | native action-list assertion |
| rename the required native method without fresh XML | native XML identity validator |
| corrupt a decoded pixel in one PNG | exact owning Paparazzi identity |
| add a proven-absent nineteenth PNG | golden liveness/inventory gate |
| add a Detekt violation in common/native test code | module/root Detekt |

A syntax error, missing artifact, stale XML, no-op PNG mutation, or failure in an earlier unrelated
oracle does not prove the named negative control.

## 13. STOP conditions

Stop and report rather than widening scope if:

- `dev` changes the measured module, consumers, resources, tests, goldens, or required checks;
- any public declaration, enum order, serializer shape, test tag, copy, reducer result, or behavior
  must change;
- a sixth production feature file or a fourth feature module must change;
- a consumer needs generated plan-editor `Res`, a resource-ID façade, a `ResourceWrapper` redesign,
  or a CMP conversion to compile;
- either dead dependency has a real unmeasured production use;
- any required dependency lacks an `iosSimulatorArm64` variant;
- a platform Context/resource ID, Java API, expect/actual shim, `androidMain`, or `iosMain` is needed
  in plan-editor production;
- the real read-only/editable scene or real set-bar callback cannot execute under native UI test;
- any PNG must be re-recorded or any current golden identity/count changes;
- a test is `NO-SOURCE`, zero, stale, skipped, duplicated, sentinel-only, or missing XML;
- repository golden/device membership changes;
- a version, dependency family, global convention/compiler policy, suppression, baseline, ruleset,
  or required-context name must change;
- `app:common`, a feature KMP graph, a permanent iOS host, Phase-5 runtime/recovery, or release
  infrastructure enters the diff; or
- production comments would need migration narration instead of durable invariant correction.

## 14. Documentation and comment budget

During implementation:

- update `documentation/architecture.md` so the module map names `core/ui/plan-editor` as shared;
- update `documentation/testing.md` for common reducer, `androidHostTest` goldens, and the native
  command/identity;
- update `documentation/ci-cd.md` so the stable native job's module list includes plan-editor;
- update the relevant editor documentation only if a source/resource ownership statement becomes
  false, without rewriting unchanged behavior; and
- append exact local, CI, review, and merge evidence to this file in implementation/delivery
  commits.

Do not add a parallel migration note, changelog, generated report, or production history comment.

## 15. Implementation commit plan

After an explicit GO, use signed Conventional Commits and keep each commit buildable:

1. `refactor(kmp): share plan editor UI` — atomically convert the module, partition resources,
   update the exact five consumers, move tests/goldens, and add the native scene;
2. `ci(kmp): gate shared plan editor UI` — extend topology/resource and native XML/workflow gates;
3. `docs(kmp): record Phase 7.5 evidence` — update only stale docs and append measured evidence.

If a split makes resources or source sets uncompilable in an intermediate commit, combine the
owning move with the conversion. Never commit a failing intermediate state or re-recorded golden.

## 16. Exit criteria

Phase 7.5 is complete only when all are true:

- the exact 38-file module topology is committed and enforced;
- all 13 production files compile from `commonMain` for Android and iOS simulator;
- private core CMP catalogs contain exactly one EN/RU key and all other copy has one honest owner;
- no plan-editor Android `R` import remains anywhere;
- all public/API/serializer/reducer/semantics behavior is compatible;
- 19 reducer tests execute through intended KMP targets;
- 18 Android-host golden cases execute and every PNG remains byte-identical;
- the exact native scene executes 1/0/0 and proves resources, branches, semantics, and dispatch;
- all six direct consumers compile/test without unlisted production changes;
- stable required jobs enforce the new topology and exact native identity;
- focused, repository, visual, device, and known-negative gates are recorded green/red/green;
- rulesets, contexts, versions, golden/device baselines, and filters are unchanged;
- documentation is current, commits are signed, checks are green, and review threads are resolved;
  and
- the maintainer, not the implementation agent, performs the merge.

## 17. Boundary and remaining migration roadmap

This docs-only specification authorizes no implementation. After it merges, Phase 7.5 still needs
an explicit maintainer GO.

Phase 7.5 closes the current shared-core UI leaf frontier. What remains is ordered but not yet
pre-authorized:

1. specify and prove the first feature/root composition slice; `feature:image-viewer` is a small
   candidate, but its `LocalContext.appDeps` graph resolution, Coil request context, Android
   resources, and platform ownership must be resolved explicitly;
2. migrate feature entry providers incrementally, preserving Android releaseability and solving
   platform integrations at their owning boundaries rather than with placeholder actuals;
3. convert the real `app:common` composition root only after its twelve feature entries,
   app-dialog host, recovery branch, navigation host, and platform dependency graph are portable;
4. add the permanent iOS host through real `app:common`, using direct framework integration as the
   preferred strategy; do not introduce CocoaPods by convention unless direct integration is
   proven insufficient, and cover the first permanent window with XCTest/XCUITest;
5. finish iOS-owned runtime/recovery, database/filesystem, Google auth/Drive, observability/Firebase
   substitute, signing, CI, TestFlight, and release work under their own measured specifications.

A throwaway `iosApp` that bypasses `app:common` remains out of scope. Phase-5 replacement,
publication, admission, retirement, journal, and recovery semantics remain separately authoritative
throughout the remaining roadmap.

## 18. Implementation evidence

### 18.1 Entry gate and measured boundary

On 2026-08-29 the implementation fetched the live repository, proved
`origin/dev = a34884ad7154edeaeee3cff2b6df8bb334ce9343`, verified this specification on that
commit, and created `feature/kmp-phase-7-5-plan-editor` directly from it. PR #266 was live as
merged with its required checks successful. The only pre-existing worktree entries were the
untracked user-owned `KMP_C1_RESULTS.md`, `documentation/regression/`, and `iosApp/`; all remained
untouched and unstaged. A throwaway `git commit-tree -S` probe contained a `gpgsig` header before
the first implementation commit.

The repeated §11.1 inventory reproduced the specified frontier exactly: 13 production Kotlin
files and 838 production lines, one core Android-resource access, 14 EN plus 14 RU core strings,
19 reducer identities, three Paparazzi classes with 18 executions and 18 PNGs, five external
caller files with 12 `CoreEditorR` accesses, six direct consumers, 456 repository PNGs across the
same 13 golden modules, and unchanged Smoke/Regression membership. Every PNG SHA-256, Git blob,
byte size, color model, and dimension matched the §2 table. Fresh pre-change focused gates were:

- `:core:ui:plan-editor:assembleDebug`: `66 actionable tasks: 66 executed`;
- `:core:ui:plan-editor:testDebugUnitTest`: `117 actionable tasks: 117 executed`;
- `:core:ui:plan-editor:verifyPaparazziDebug`: `118 actionable tasks: 118 executed`; and
- `:core:ui:plan-editor:lintDebug`: `130 actionable tasks: 130 executed`.

Fresh pre-change JUnit XML contained the exact 19 reducer and 18 golden cases with zero skipped,
failed, or errored cases. No entry STOP condition was reached.

### 18.2 Implemented topology and ownership

The implementation produces the exact 38-file manifest: all 13 production files are in
`commonMain`; the only private core CMP resource is `core_ui_plan_editor_read_plan_empty` in EN/RU;
the unchanged 19-case reducer is in `commonTest`; the three golden classes and 18 byte-identical
PNGs are in `androidHostTest`; and the single required production scene is in `iosTest`. There is
no plan-editor `androidMain`, `iosMain`, expect/actual, platform shim, Android resource/API access,
Java API, Context access, or legacy production/test file.

Exactly the five authorized production callers and the EN/RU owners in `feature:plan-editor`,
`feature:exercise`, and `feature:single-training` changed. The feature partition is five, six, and
one migrated identifiers respectively; the seven already-local plan-editor identifiers are
unchanged. The first fresh consumer compile established that the authorized plan-editor handler
did not already import its local `R` as the discovery wording stated, so its local import was added
inside that already-authorized file. No sixth caller or fourth feature module was required.

The module uses `convention.kmpComposeLibrary`, applies serialization explicitly, keeps generated
resources private under
`io.github.stslex.workeeper.core.ui.plan_editor.resources`, exposes only the §8 API dependencies,
and declares material-icons-core privately in `commonMain`. The dead `core:core` and
`core:data:exercise` edges are removed; every one of the six consumers retains its direct
`core:ui:kit` edge. The extended topology/resource oracle passes the exact plan-editor manifest and
owner/value maps while retaining the Phase 7.4 start-mode manifest unchanged.

### 18.3 Fresh positive verification

Every forced Gradle gate used `--rerun-tasks --no-build-cache`; KMP, Paparazzi, Native, and final
repository gates also used `--no-configuration-cache` where required. The focused results were:

- plan-editor assemble `93/93`, Android-host unit `88/88`, Paparazzi `89/89`, lint `75/75`, and
  local Xcode `iosSimulatorArm64Test` `56/56` executed;
- all six consumers' assemble/unit coverage `432/432` executed;
- the stable five-module Native invocation `134/134` executed; and
- the topology/resource oracle and Native XML validator both passed.

Fresh structural XML proved exactly 19 common reducer cases, 18 Android-host golden cases, and one
`io.github.stslex.workeeper.core.ui.plan_editor.PlanEditorSceneIosTest.readOnlyCopyAndEditableAddRenderAndDispatch`
case, all with zero skips, failures, or errors. The full Native validator retained kit `1`,
navigation `1`, MVI `14` including all five required identities, start-mode `2`, and plan-editor
`20` including the 19 common cases plus the single scene.

Fresh repository gates were `assembleDebug` `1174/1174`, `assembleDebugAndroidTest` `1938/1938`,
`verifyPaparazziDebug` `621/621`, `:lint-rules:test` `9/9`, `detekt` `57/57`, `lintDebug`
`1091/1091`, and `testDebugUnitTest` `1132/1132`, all executed and successful. The personal-data
gate passed with only its documented exceptions. No suppression, baseline, tolerance, version,
convention, compiler policy, ruleset, required context, or golden was changed.

Local device infrastructure was available. Canonical Smoke ran `2011/2011` Gradle tasks and its
14 fresh XML files proved `44 discovered / 41 executed / 3` exact `pendingFeatureRewrite` skips
with zero failures/errors. Canonical Regression ran `2011/2011` tasks and its 14 fresh XML files
proved `81/81` with zero skips/failures/errors. Two week-old KMP alias reports under
`connected/debug` were identified by timestamp and excluded; only reports freshly written by each
run were counted. The MVI device identity validator retained both exact cases at zero skips,
failures, and errors.

All 18 PNGs retained the §2 hashes, byte sizes, dimensions, and metadata, and Git records each as a
100% rename. Repository membership remains 456 PNGs across the same 13 modules. No image was
re-recorded.

### 18.4 Mandatory known-negative controls

Each control ran as fresh GREEN, the named RED, exact restoration without Git recovery, and fresh
GREEN:

1. a same-count `commonMain` file moved to legacy `src/main` made the exact topology oracle RED;
2. an `androidx.compose.ui.res` import made topology RED and the Native compiler rejected the
   unresolved platform API; the generic mutation harness classifies compiler rejection as
   `INVALID`, but the named platform-boundary compiler oracle itself was the specified RED, not a
   syntax failure, and the `30/30` compile gate was green before and after restoration;
3. a restored `CoreEditorR` call made resource ownership RED;
4. a same-total identifier moved into the wrong feature catalog made ownership RED;
5. a changed RU feature value made exact value ownership RED;
6. changed RU read-only copy made both `readOnlyEmpty` LIGHT/DARK Paparazzi cases RED, followed by
   `89/89` green;
7. default repetitions `5 -> 6` made both owning reducer identities RED, followed by `88/88`
   green;
8. a blank Native composition made the exact production-scene identity RED, followed by `56/56`
   green;
9. suppressed add dispatch made the exact action-list assertion RED, followed by `56/56` green;
10. a renamed Native method plus freshly regenerated XML made the Native identity validator RED,
    followed by `56/56` and validator green;
11. changing decoded RGBA pixel `(539, 103)` from red `239` to `16` made the exact owning LIGHT
    Paparazzi case RED; the original `ad51992f...` SHA-256 was restored and `89/89` was green;
12. a proven-absent nineteenth PNG made `assertGoldenLiveness` RED; count 18 was restored and
    `89/89` was green; and
13. a Native-test `MaxLineLength` violation made root Detekt RED; exact restoration returned
    `57/57` green.

No temporary mutation, report, local configuration, secret, or generated output entered a commit.

### 18.5 Delivery snapshot

Implementation PR #267 targets `dev` and remains open and unmerged. Its implementation head
`f0d5164386f92f19d116dd0a3bed783a0eeb9696` contains GitHub-Verified signed commits
`f16fa5fbeb93870bc1cd4f8a292605d7baf29bb9` (`refactor(kmp): share plan editor UI`) and
`f0d5164386f92f19d116dd0a3bed783a0eeb9696` (`ci(kmp): gate shared plan editor UI`). Codex review
completed on that head with zero submitted reviews and zero review threads. The implementation-head
CI snapshot was entirely terminal-success: run `33217962588` completed `Build and Unit Tests` in
33m04s and `KMP iOS kit smoke` in 21m42s, including both exact-identity validators and the Native
artifact upload; run `33217962576` completed `Mockup Appearance Gate` successfully. The PR was
mergeable/CLEAN at that snapshot. No force-push or merge occurred; the maintainer remains the merge
owner.

### 18.6 GitHub delivery closeout — 2026-08-29

PR #267 merged into `dev` at `2026-08-29T06:19:01Z` from final head
`9157bd8dc951cfbc270ebddcd3f0f5c8f56fc599`; GitHub created merge commit
`479a2960574ff165f4f6b99a24d49ab1c961bbd8`.

The final implementation stack was:

- `f16fa5fbeb93870bc1cd4f8a292605d7baf29bb9` —
  `refactor(kmp): share plan editor UI`;
- `f0d5164386f92f19d116dd0a3bed783a0eeb9696` —
  `ci(kmp): gate shared plan editor UI`; and
- `9157bd8dc951cfbc270ebddcd3f0f5c8f56fc599` —
  `docs(kmp): record Phase 7.5 evidence`.

GitHub verifies all three signatures. Final-head `Android CI/CD - Unified Build and Tests` run
`33220042953` completed successfully: `Build and Unit Tests` in 8m35s and
`KMP iOS kit smoke` in 2m25s. All final-head `v3 Mockup Appearance Gate` executions were also
successful, including runs `33220042623`, `33220076250`, `33220588217`, and `33220657871`.

The PR merged with zero review threads and zero submitted reviews. The repository-wide `all`
ruleset (id `8116593`, `~ALL`) remains active and still requires signed commits plus the unchanged
`Build and Unit Tests` and `KMP iOS kit smoke` contexts; the dedicated `dev` ruleset remains
disabled. With the local evidence in Sections 18.1–18.4, the final-head evidence above, and the
maintainer merge, every Section 16 exit criterion is closed.
