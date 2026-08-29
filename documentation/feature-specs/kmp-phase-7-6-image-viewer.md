# KMP Phase 7.6 — `feature:image-viewer` becomes the first shared feature entry

**Status:** IMPLEMENTED IN OPEN PR #269; AWAITING MAINTAINER MERGE

**Target branch:** `dev`

**Specification baseline:** `479a2960574ff165f4f6b99a24d49ab1c961bbd8` — verified merge
commit of Phase 7.5 implementation PR #267

**Implementation base:** `5bcecf721caf8ec216b3c50133b45538a027a616` — the specification
baseline plus merged docs-only specification PR #268

---

## 0. Authority and entry condition

This document became the implementation authority for Phase 7.6 after docs-only specification PR
#268 merged and the maintainer gave the separate, explicit implementation GO. That GO authorized
the bounded production, test, Gradle, workflow, documentation, branch, and pull-request work
recorded below; it did not authorize merge or repository-setting changes.

The specification is measured from the exact `dev` commit above and refines the remaining frontier
in:

- `AGENTS.md`;
- `documentation/feature-specs/kmp-phase-2-probes.md`;
- `documentation/feature-specs/kmp-phase-6-data-layer.md`;
- `documentation/feature-specs/kmp-phase-7-1-ui-kit.md` through
  `kmp-phase-7-5-plan-editor.md`;
- `documentation/architecture.md`, `documentation/testing.md`, and
  `documentation/ci-cd.md`; and
- the graph-extension invariants recorded in `documentation/graph-extension-arc/HANDOFF.md`.

At specification time:

- `dev` pointed exactly to the baseline and the isolated discovery worktree was clean;
- Phase 7.5 PR #267 was merged, its three commits were GitHub-Verified, every final-head workflow
  execution succeeded, and its review inventory contained zero submitted reviews and zero threads;
- no pull request was open and the proposed Phase 7.6 branch did not exist;
- the active repository-wide ruleset was `all` (id `8116593`, selector `~ALL`), requiring signed
  commits plus `Build and Unit Tests` and `KMP iOS kit smoke`; and
- the dedicated `dev` ruleset (id `18553518`) remained disabled.

Before implementation, fetch `dev` and repeat the source, resource, dependency, consumer, test,
workflow, ruleset, and device-suite inventory. If the baseline or dependency frontier changed
materially, STOP and update this specification instead of adapting it by analogy.

The specification environment could not execute the Gradle wrapper because Gradle 9.6.1 was not
cached and `services.gradle.org` was unreachable. No local Gradle green is claimed here. The fresh
entry gates in Section 11 are therefore mandatory before any implementation edit.

## 1. Decision and bounded exit claim

Phase 7.6 converts **only** `feature:image-viewer` from a classic Android Compose feature to the
repository's KMP Compose feature shape targeting Android and `iosSimulatorArm64`, and moves this
one entry from Android `Context.appDeps` lookup to an explicit generation-owned graph factory
supplied by the composition root.

This is the first shared **feature**, not another shared UI leaf. It intentionally proves all three
parts of one feature boundary together:

1. the Store, handlers, Metro graph extension, resources, and production UI compile from
   `commonMain`;
2. the Android app graph still creates the same per-entry extension with the same app-scope and
   feature-scope identity; and
3. `app:common` explicitly receives and passes `ImageViewerGraph.Factory`, so the feature no
   longer discovers its root through an Android `Context`.

At exit:

> The exact current image-viewer production surface compiles from `commonMain` for Android and the
> iOS simulator. Its six EN/RU strings are private Compose Multiplatform resources. Coil keeps the
> existing request, crossfade, fit, and error-state behavior through `LocalPlatformContext`. The
> root resolves `AppRootDeps` once per composed admitted generation region and obtains the current
> Android graph factory from it. The feature contains no `LocalContext`, `Context.appDeps`, Android
> `R`, Android annotation, expect/actual, or platform shim. Twelve common handler cases, the
> existing three Android graph
> identity cases, four unchanged Android regression journeys, and one native production scene
> prove the boundary.

The phase does **not** convert `app:common`, create an iOS application, migrate `feature:exercise`,
generalize all feature factories, or solve image acquisition/storage on iOS. Those are separate
frontiers.

## 2. Measured baseline

All measurements below are from the specification baseline.

| Surface | Baseline |
| --- | --- |
| Gradle shape | `convention.composeLibrary` + Metro; Coil, animation, and extended icons arrive implicitly through the Android Compose convention |
| Production Kotlin | 13 files, 721 physical lines under `src/main/kotlin` |
| Platform coupling | `LocalContext` + `Context.appDeps` in `ImageViewerFeature`; `LocalContext` in `ZoomableImage`; Android `R`/`androidx.compose.ui.res.stringResource` in two UI files; `androidx.annotation.VisibleForTesting` in the Store |
| Android/Java API | no direct `android.*`, `java.*`, or `javax.*` import; the blockers are the Android Compose/context/resource and annotation APIs above |
| Android resources | 6 English keys + the same 6 Russian keys under `src/main/res` |
| Unit tests | 3 JUnit/MockK handler classes, 11 cases: Click 8, Common 2, Navigation 1 |
| App graph tests | `ImageViewerExtensionIdentityTest`: 3 cases |
| Android regression coverage | 4 exact image-viewer journeys across `RouteReachabilityTest`, `NavigationResultTest`, and `BackStackStateRestorationTest` |
| Direct Gradle consumers | 2: `app:common` for the entry provider and `app:app` for graph aggregation |
| Root lookup population | 13 feature/dialog `appDeps<Factory>()` readers; image-viewer is one of them |
| Module goldens | none; image decode is intentionally not part of the existing Paparazzi corpus |
| Repository visual baseline | 456 PNGs across 13 live golden gates |
| Canonical device baseline | Smoke 44 discovered / 41 executed / 3 named skips / 0 failures; Regression 81/81 |

The load-bearing anchors are:

| File | Git blob |
| --- | --- |
| `feature/image-viewer/build.gradle.kts` | `874b9b8cafadc2f35a49ef34bbde38a776eb2b44` |
| `di/ImageViewerFeature.kt` | `5a58a9e74d9c9afb2e1c0f37c73d36456b3e6a57` |
| `di/ImageViewerGraph.kt` | `0e389ef1fdc0a8a74bc080afc59d73e1d7e8211a` |
| `di/ImageViewerHandlerStore.kt` | `5bacd8ec12bb1f1c9dfb485bb66078fe83a5ce66` |
| `di/ImageViewerHandlerStoreImpl.kt` | `06048d24d8831b60e5bd4c2dc0ff54220b783c26` |
| `di/ImageViewerScope.kt` | `dc337bd228b09722c0af37ae943f45514968bc51` |
| `mvi/handler/ClickHandler.kt` | `a4cc657d13660e438ccdc1cdaacb8d36a60dbf51` |
| `mvi/handler/CommonHandler.kt` | `800f8a9addf1a5bf9a43214140eb4c8cd9c23d80` |
| `mvi/handler/NavigationHandler.kt` | `5dd07acc56672c72769cfd0de5df599e327f1420` |
| `mvi/store/ImageViewerStore.kt` | `aaace882038eb705aade0df707bd5faa14bd2050` |
| `mvi/store/ImageViewerStoreImpl.kt` | `1039126e7a3845071c6fc7bab80f0e64a16bf31f` |
| `ui/ImageViewerGraph.kt` | `8d9bfb6c02c383594c614be17ada68ef648f8650` |
| `ui/ImageViewerScreen.kt` | `93a7ba115db7fdaeb8297b6b63c4cdb6033a4832` |
| `ui/components/ZoomableImage.kt` | `1c3bda6f8b62543007615275fe671fbcd874532b` |
| English `strings.xml` | `cb87bb1224c4daaa8cd6667e61a02de283430143` |
| Russian `strings.xml` | `85ae37eb973b9c23081f1c3df8b3be122ac4d839` |
| `ClickHandlerTest.kt` | `710fc743c53a819cccec6bffbb97107b92401a11` |
| `CommonHandlerTest.kt` | `bea5a5baa6953ed804e1093dd2048c30976e436a` |
| `NavigationHandlerTest.kt` | `3147bbe4dbd9c18209bde87622c9ace8de3733bc` |
| `app/common/build.gradle.kts` | `1b60530f787010150e577de8f8a2759b6174cf53` |
| `app/common/App.kt` | `cde212be8523c072d8c1bc532589770be3b1a8e9` |
| `app/common/host/AppNavigationHost.kt` | `d80f31b04daab56fa141c431b76fed546d0d05c3` |
| `app/common/di/AppRootDeps.kt` | `03b30815e0d2e89fdd4d3aa2752f3bb7d39275dc` |
| `app/app/di/AppGraph.kt` | `4ec3ba10b53b683b9f0fe260545da9958f00e962` |
| `ImageViewerExtensionIdentityTest.kt` | `78d95a6365f43abfbd0fdc22bed020942bfd3704` |
| `UiAdmissionRaceTest.kt` | `c4b4da1ff5294c6c7743531262138252cfde55c0` |
| `core/ui/navigation/Screen.kt` | `6bb1ec35b241d2eed207beef66bd81388bd5a574` |

The current feature has no `androidTest` source file despite declaring Android-test dependencies.
Those dependencies are dead configuration, not a test surface to migrate.

## 3. Architecture decision — explicit factory injection

### 3.1 The current seam and why it cannot enter common code

`ImageViewerFeature.processor(screen)` currently reads `LocalContext.current`, casts the
application graph through `context.appDeps<ImageViewerGraph.Factory>()`, creates the extension,
and reads `imageViewerStore` inside `rememberMetroStoreProcessor`.

The extension lifetime and Store retention are correct; only discovery is wrong for shared code.
Moving that lookup to `androidMain`, wrapping it in expect/actual, or exposing a nullable global
would preserve an Android service locator as the feature contract. All are forbidden.

### 3.2 Chosen root flow

Use Metro's existing graph-extension-factory accessor shape directly:

1. `AppRootDeps` adds `val imageViewerGraphFactory: ImageViewerGraph.Factory`.
2. `AppGraph` explicitly overrides that accessor. The factory is already contributed to
   `AppScope` through `@ContributesTo(AppScope::class)`; no adapter graph, provider object, or new
   scope is introduced.
3. Inside the already-admitted generation region, `App()` resolves one `AppRootDeps` instance,
   remembers it by generation id, and passes it to `AppGenerationContent(deps)`.
4. `AppGenerationContent` uses the same `deps` for `AppRootViewModel` and passes
   `deps.imageViewerGraphFactory` to `AppNavigationHost`.
5. `AppNavigationHost` takes a required `ImageViewerGraph.Factory` parameter and supplies it to
   `imageViewerGraph`.
6. `imageViewerGraph` constructs `ImageViewerFeature(factory)`. The feature uses that factory only
   inside the existing `rememberMetroStoreProcessor` creation lambda, preserving one extension
   creation per retained Store.

This is the parent-graph accessor shape documented by Metro for graph-extension factories; the
implementation must use the repository-pinned Metro version rather than copy generated APIs or
introduce a hand-written adapter. See the upstream
[dependency-graph documentation](https://zacsweers.github.io/metro/1.3.2/dependency-graphs/).

The dependency resolution must stay **behind** `GenerationAdmission.granted`. A retired or
publication-race generation must still resolve zero app-root dependencies. The root must not call
`appRootDeps()` a second time during recomposition, and the factory must not be stored in a process
singleton or outside the generation-keyed composition.

"Once" means once for that live composition region. An Activity recreation may establish a new
region for the same still-current generation id; do not move the dependency into a process-global
cache merely to turn that legitimate new resolution into a lifetime-wide singleton lookup.

### 3.3 Why this is a bounded root slice

Both existing module edges remain real and no new consumer edge is introduced:

- `app:common -> feature:image-viewer` names the entry and its factory contract; its existing
  dependency changes from `implementation` to `api` because public `AppRootDeps` now exposes
  `ImageViewerGraph.Factory`;
- `app:app -> feature:image-viewer` aggregates the graph extension and exposes its factory.

`app:common` remains an Android Compose library. Its existing top-level `LocalContext` access for
the generation holder and `NavigatorExt` recovery Activity seam remain. `AppDepsHolder` and the
other 12 `appDeps<Factory>()` readers remain. This phase moves one feature from implicit discovery
to explicit composition; it does not claim the root is shared yet.

## 4. Coil and platform ownership decision

Coil 3.5.0 is already pinned and its Compose artifact is multiplatform. Preserve the current
request shape exactly, changing only the platform-context source:

```kotlin
ImageRequest.Builder(LocalPlatformContext.current)
    .data(model)
    .crossfade(true)
    .build()
```

This follows Coil 3's multiplatform Compose request shape and `LocalPlatformContext` contract in
the upstream [Compose documentation](https://coil-kt.github.io/coil/compose/).

Keep `AsyncImage`, `ContentScale.Fit`, the current `onState` error transition, gesture state,
animation, test tags, and unavailable UI unchanged. Do not simplify the request to a raw String,
remove crossfade, introduce a custom loader, or add `coil-network-ktor3`.

`Screen.ExerciseImage.model` remains an opaque String owned by the caller:

- Android currently supplies an app-private file path or `content://` URI;
- image-viewer neither parses nor normalizes the value;
- Coil interprets it through the current target's platform context; and
- a future iOS caller must supply a target-loadable file/URI value at the image-acquisition
  boundary that owns it.

This is honest platform ownership: the viewer displays an opaque model and owns no filesystem,
picker, permission, copy, deletion, or persistence behavior. No expect/actual is needed here.

## 5. Resource ownership

Move both catalogs verbatim to private CMP resources and set:

```kotlin
compose.resources {
    packageOfResClass = "io.github.stslex.workeeper.feature.image_viewer.resources"
}
```

Do not set `publicResClass = true`. No external module reads these strings.

| Key | English | Russian |
| --- | --- | --- |
| `feature_image_viewer_back` | `Back` | `Назад` |
| `feature_image_viewer_content_description` | `Exercise image, full size` | `Полноразмерное фото упражнения` |
| `feature_image_viewer_unavailable` | `Image unavailable` | `Фото недоступно` |
| `feature_image_viewer_menu` | `Image actions` | `Действия с фото` |
| `feature_image_viewer_action_replace` | `Replace photo` | `Заменить фото` |
| `feature_image_viewer_action_remove` | `Remove photo` | `Удалить фото` |

Replace Android `R` and `androidx.compose.ui.res.stringResource` with the generated private `Res`
accessors and `org.jetbrains.compose.resources.stringResource`. The identifiers, values, visible
copy, and content descriptions are compatibility data.

## 6. In scope

1. Convert `feature:image-viewer` to `convention.kmpComposeLibrary` while retaining Metro.
2. Move all 13 production files with history to `commonMain`.
3. Replace the two Android context lookups with explicit root factory injection and
   `LocalPlatformContext` as specified above.
4. Remove the Android-only `VisibleForTesting` annotation without changing the private Store name.
5. Move the exact six-key EN/RU catalogs to private CMP resources.
6. Rewrite the three MockK/JUnit handler tests as deterministic `kotlin.test` common tests with
   small in-module fakes, preserving the 11 existing cases and adding the missing
   `BackWithRequest` navigation branch for 12 total.
7. Add one iOS-simulator CMP scene using production image-viewer UI/resources and dispatching real
   image-viewer actions.
8. Thread the graph factory through the exact root files in Section 3, update the existing
   `ImageViewerExtensionIdentityTest` to use the production `AppRootDeps` accessor, and strengthen
   the admitted-generation dependency assertion in `UiAdmissionRaceTest` from at-least-one to
   exactly one without changing any test identity.
9. Change only `app:common`'s existing image-viewer dependency visibility from `implementation` to
   `api`; retain `app:app`'s direct `implementation` edge for graph aggregation.
10. Clarify the durable opaque-model ownership in `Screen.ExerciseImage` KDoc only; do not change
   its serialized fields, defaults, or registry.
11. Extend the existing shared-UI topology gate and stable native workflow/XML validator for this
    feature.
12. Update only documentation made stale by the implementation and record exact evidence.

## 7. Explicit non-goals

- Converting `app:common`, `app:app`, `feature:exercise`, any sibling feature, app dialogs, or
  recovery to KMP.
- Creating `iosApp`, an XCFramework, UIKit/SwiftUI host, CocoaPods integration, XCTest/XCUITest,
  signing, TestFlight, or release automation.
- Migrating the exercise photo picker, permissions, `ImageStorage`, temp files, replacement, or
  deletion behavior.
- Adding a network-image contract or `coil-network-ktor3`.
- Replacing Metro, changing graph scopes, widening a graph contract, or deleting
  `AppDepsHolder`/`Context.appDeps` while other readers remain.
- A CompositionLocal service locator, static factory registry, nullable production fallback, or
  placeholder actual.
- Visual redesign, gesture redesign, new icons, new copy, golden recording, or tolerance changes.
- Version, convention-plugin, compiler-policy, dependency-catalog, ruleset, required-context,
  baseline, suppression, device-membership, or test-filter changes.
- Public CMP resources or a new shared image-viewer core module.

## 8. Exact source topology

At exit `feature/image-viewer/src` contains exactly these files.

### `commonMain`

```text
src/commonMain/kotlin/io/github/stslex/workeeper/feature/image_viewer/di/ImageViewerFeature.kt
src/commonMain/kotlin/io/github/stslex/workeeper/feature/image_viewer/di/ImageViewerGraph.kt
src/commonMain/kotlin/io/github/stslex/workeeper/feature/image_viewer/di/ImageViewerHandlerStore.kt
src/commonMain/kotlin/io/github/stslex/workeeper/feature/image_viewer/di/ImageViewerHandlerStoreImpl.kt
src/commonMain/kotlin/io/github/stslex/workeeper/feature/image_viewer/di/ImageViewerScope.kt
src/commonMain/kotlin/io/github/stslex/workeeper/feature/image_viewer/mvi/handler/ClickHandler.kt
src/commonMain/kotlin/io/github/stslex/workeeper/feature/image_viewer/mvi/handler/CommonHandler.kt
src/commonMain/kotlin/io/github/stslex/workeeper/feature/image_viewer/mvi/handler/NavigationHandler.kt
src/commonMain/kotlin/io/github/stslex/workeeper/feature/image_viewer/mvi/store/ImageViewerStore.kt
src/commonMain/kotlin/io/github/stslex/workeeper/feature/image_viewer/mvi/store/ImageViewerStoreImpl.kt
src/commonMain/kotlin/io/github/stslex/workeeper/feature/image_viewer/ui/ImageViewerGraph.kt
src/commonMain/kotlin/io/github/stslex/workeeper/feature/image_viewer/ui/ImageViewerScreen.kt
src/commonMain/kotlin/io/github/stslex/workeeper/feature/image_viewer/ui/components/ZoomableImage.kt
src/commonMain/composeResources/values/strings.xml
src/commonMain/composeResources/values-ru/strings.xml
```

### `commonTest`

```text
src/commonTest/kotlin/io/github/stslex/workeeper/feature/image_viewer/mvi/handler/ClickHandlerTest.kt
src/commonTest/kotlin/io/github/stslex/workeeper/feature/image_viewer/mvi/handler/CommonHandlerTest.kt
src/commonTest/kotlin/io/github/stslex/workeeper/feature/image_viewer/mvi/handler/NavigationHandlerTest.kt
```

### `iosTest`

```text
src/iosTest/kotlin/io/github/stslex/workeeper/feature/image_viewer/ImageViewerSceneIosTest.kt
```

There is no production or test file under legacy `src/main`, `src/test`, or `src/androidTest`, and
no Kotlin-bearing `androidMain`, `iosMain`, `androidHostTest`, or `androidDeviceTest`. No Android
resource remains under `src/main/res`.

The topology oracle must enumerate the exact 19-file manifest before applying source-set checks,
reject unknown Kotlin-bearing source sets, reject Android/Java/Javax/AndroidX-annotation APIs in
common/native production, and enforce the exact resource catalog and package.

## 9. Gradle and API contract

The final build applies:

- `convention.kmpComposeLibrary`;
- Metro with the current `includeJavax()` interop block retained; and
- the private CMP resource package in Section 5.

Declare dependencies in the narrowest source set. The required common production edges are:

| Visibility | Dependency | Reason |
| --- | --- | --- |
| `implementation` | `project(":core:core")` | `AppScope` token and `AppScopeLifetime` construction dependency |
| `implementation` | `project(":core:ui:kit")` | sheets, icons, dimensions, typography, and theme |
| `api` | `project(":core:ui:mvi")` | public Store interfaces/implementation and feature entry types |
| `api` | `project(":core:ui:navigation")` | public graph factory route parameter and public `NavGraphScope` entry |
| `api` | `libs.cmp.ui` | public `Modifier` graph parameter and `HapticFeedbackType` event payload |
| `implementation` | `libs.coil.compose` | internal KMP `AsyncImage`, `LocalPlatformContext`, and request path |
| `implementation` | `libs.cmp.animation` | `animateFloatAsState`; the KMP Compose convention does not add Animation |
| `implementation` | `libs.cmp.material.icons.extended` | `Icons.Filled.BrokenImage`; it is not in icons-core |

`commonTest` and `iosTest` add `kotlin("test")`; `iosTest` also adds `libs.cmp.ui.test`.
The KMP Compose convention supplies the core CMP stack. Do not add `coil-ktor`, Android test
bundles, Compose test manifest, Robolectric, MockK, Paparazzi, or the golden harness.

In `app:common`, change the existing `project(":feature:image-viewer")` dependency to `api`: the
public `AppRootDeps` contract names `ImageViewerGraph.Factory`. Keep the direct `app:app`
`implementation` dependency unchanged because that module aggregates the Metro contribution and
explicitly overrides the accessor. No other consumer dependency changes.

The public declaration population remains the existing six forced DI/MVI types plus the graph
entry function. The intentional source API change is only that `imageViewerGraph` receives the
required `ImageViewerGraph.Factory`; no Store/State/Action/Event field, default, enum order,
visibility, graph scope, creator name, or serialized route changes.

## 10. Test and compatibility contract

### 10.1 Common handler tests

Use fakes rather than a multiplatform mocking library. The 12 cases must cover:

| Owner | Cases |
| --- | ---: |
| Click | non-editable menu guard; editable menu open; replace request + sheet close; remove request; back haptic/navigation; double-tap expand; double-tap collapse/reset; double-tap haptic |
| Common | Init no-op; absolute transform persistence |
| Navigation | plain back; `BackWithRequest` destination and exact request name |

The fake `ImageViewerHandlerStore` must implement state update, consumed-action, and emitted-event
recording directly; unused logger/coroutine members fail fast. The fake `Navigator` records both
`popBack()` and the destination/result pair passed to `popBackWithResult(...)`; its unrelated
methods fail fast. Do not weaken assertions to counts that accept the wrong request or destination.

### 10.2 Android graph/root identity

Keep the three existing `ImageViewerExtensionIdentityTest` method identities. Route every factory
read through `appGraph.imageViewerGraphFactory`, not `asContribution` in the test. They continue to
prove:

1. the extension resolves a Store through the parent graph;
2. the Store receives the parent's exact `AnalyticsHolder` and `LoggerHolder` singletons; and
3. separate route arguments create separate Stores with the correct model.

The current one-read rule remains: each created extension's unscoped Store accessor is read exactly
once. `ImageViewerHandlerStoreImpl` remains `@SingleIn(ImageViewerScope::class)`.

Keep all three existing `UiAdmissionRaceTest` method identities. Strengthen only the admitted
generation's dependency-resolution assertion from `>= 1` to exactly `1`; the retired and
publication-race assertions remain exact zero. Together they gate moving `AppRootDeps` resolution
outside the ViewModel factory without duplicating it or crossing the admission boundary.

### 10.3 Android device compatibility

Do not edit the four current regression journeys or their identities:

- `RouteReachabilityTest.imageViewerOpensFromASeededExerciseImageAndTheExerciseReturns`;
- `NavigationResultTest.imageReplaceRequestReachesTheExerciseThatOpenedTheViewer`;
- `NavigationResultTest.imageRemoveRequestTurnsTheThumbnailIntoThePhotoPickerEntryPoint`; and
- `BackStackStateRestorationTest.editorDraftSurvivesTheImageViewerRoundTrip`.

They prove the real `App()` -> `AppNavigationHost` -> factory -> extension -> Store path, both
result verbs, ordinary back, and per-entry retention against the Android app graph.

### 10.4 Native production scene

Add exactly one required identity:

```text
io.github.stslex.workeeper.feature.image_viewer.ImageViewerSceneIosTest.resourcesBranchesAndActionsRenderAndDispatch
```

The CMP v2 test runner must compose the production `ImageViewerScreen` under the production theme
and prove, in one test:

- read-only state renders the canvas/back affordance and omits the menu affordance;
- editable menu state renders the exact migrated menu, replace, and remove copy;
- at least one back action and one menu verb dispatch the exact production `Action.Click` values;
- the same production resource accessors used by the screen resolve on Native; and
- the image request path composes through `LocalPlatformContext` without an Android actual.

This is a headless Skiko Compose scene, not an iOS application/window claim. Do not add an iOS
golden corpus or claim UIKit/Metal/XCTest coverage.

### 10.5 Visual compatibility

The module owns no pre-existing golden. Do not create a black/empty viewer golden that cannot
prove image decode, and do not refactor production solely to inject a screenshot fixture. The
repository `verifyPaparazziDebug` gate must retain all 456 existing PNG paths and bytes across the
same 13 modules.

## 11. Verification plan

Every positive Gradle gate must use `--rerun-tasks --no-build-cache`; KMP/native invocations also
use `--no-configuration-cache`. Read fresh XML rather than trusting task exit or aggregate totals.

### 11.1 Entry gate before edits

1. fetch `dev` and prove the exact baseline;
2. repeat the 13-file/721-line production, 6+6 resource, 11 handler-test, 3 graph-test, 4 device
   journey, 2-consumer, 13-reader, 456-PNG, and device-suite inventory;
3. verify the active ruleset and required context names;
4. run fresh baseline `assembleDebug`, `testDebugUnitTest`, and `lintDebug` for image-viewer;
5. run fresh app-common/app-app assemble/unit and instrumented-test assembly gates; and
6. parse XML for the exact 11 handler and 3 graph identity cases with zero skips/failures/errors.

The classic baseline has no Native task for image-viewer; do not fabricate a pre-conversion native
green.

### 11.2 Focused post-change gates

```text
:feature:image-viewer:assembleDebug
:feature:image-viewer:testDebugUnitTest
:feature:image-viewer:lintDebug
:feature:image-viewer:iosSimulatorArm64Test
:app:common:assembleDebug
:app:common:testDebugUnitTest
:app:app:assembleDebug
:app:app:testDebugUnitTest
:app:app:assembleDebugAndroidTest
```

Also run the extended topology oracle and Native XML validator. Fresh XML must prove 12 common
handler cases on Android host and Native, the three Android graph identity cases, and exactly one
matching native scene, with zero skips/failures/errors.

### 11.3 Stable CI extension

Extend, without renaming required contexts:

- `.github/scripts/assert_kmp_ui_source_topology.py` with the exact image-viewer manifest,
  resources, platform-import rejection, root-factory flow, and remaining-reader inventory;
- `.github/workflows/android_build_unified.yml` so `KMP iOS kit smoke` runs
  `:feature:image-viewer:iosSimulatorArm64Test` and uploads its results; and
- `.github/scripts/assert_kmp_ios_smoke.py` so every existing module is still inspected and the
  exact image-viewer scene identity is required once.

The Native validator may accept additional passing common cases, but it must structurally validate
all suites and reject skips, failures, errors, missing results, duplicate required identities, or
compensating totals.

### 11.4 Repository and device gates

Run the existing repository gates unchanged:

- `assembleDebug`;
- `assembleDebugAndroidTest`;
- `verifyPaparazziDebug`;
- `:lint-rules:test`;
- `detekt`;
- `documentation/personal_data_gate.py -v`;
- `lintDebug`;
- `testDebugUnitTest`;
- canonical Smoke; and
- canonical Regression.

Smoke must retain 44 discovered / 41 executed / the three exact authorized skips. Regression must
retain 81/81. Every one of the 456 PNGs must retain path and bytes.

## 12. Mandatory known-negative controls

Each control is fresh GREEN -> named RED -> exact restoration without Git recovery -> fresh GREEN.
Use the repository mutation harness for text changes and reject syntax-only or non-observable
mutations.

| Mutation | Required oracle |
| --- | --- |
| move one allowlisted common file back to legacy `src/main` while preserving the total | exact topology manifest |
| add an Android `LocalContext`/resource/annotation import to common production | topology platform gate and Native compile boundary |
| replace `LocalPlatformContext` with another context source or remove `.crossfade(true)` | exact Coil source contract in topology gate |
| mutate one EN and one RU image-viewer string | exact CMP catalog/value gate; EN mutation also reaches the native resource assertion |
| remove the non-editable menu guard | owning common ClickHandler identity |
| map REPLACE to REMOVE | owning common ClickHandler identity |
| change the result destination or request name in `BackWithRequest` | new common NavigationHandler identity |
| remove or mis-type `AppGraph.imageViewerGraphFactory` | app graph compile/identity gate |
| bypass the factory parameter in the host/feature flow | root-flow topology gate and exact RouteReachability journey |
| resolve `AppRootDeps` twice behind admission | exact admitted-generation dependency identity |
| resolve `AppRootDeps` before admission | exact retired-generation/race device identities |
| blank the Native production composition | native scene identity |
| suppress one Native action callback | native action assertion |
| rename the Native method and regenerate XML | exact Native XML identity validator |
| add a max-line-length violation to the Native test | root Detekt |

Do not invent a PNG mutation for a module that owns no golden. Repository PNG immutability is a
positive inventory gate here.

## 13. STOP conditions

Stop and amend this specification if any is true:

- `dev` changes the measured production, resource, root, test, workflow, ruleset, or dependency
  boundary materially;
- Coil 3.5.0's common artifact or `LocalPlatformContext` does not compile/link for
  `iosSimulatorArm64` while preserving the existing request behavior;
- Metro 1.3.2 cannot expose the contributed `ImageViewerGraph.Factory` as the inherited app-graph
  accessor without an adapter graph, service locator, global, or scope redesign;
- KMP conversion requires a dependency version, convention-plugin, compiler-policy, catalog, or
  platform-target change;
- the factory cannot remain generation-owned or app-root deps must resolve before admission;
- any change is required in `feature:exercise` production, image storage, picker, permission,
  filesystem, or recovery behavior;
- any sibling feature/root factory must move to make image-viewer compile;
- `app:common` itself, an iOS host, expect/actual, or a placeholder platform implementation becomes
  necessary;
- any existing public Store/route field, serialized shape, tag, copy, gesture, navigation result,
  graph scope, or device journey must change;
- any existing PNG must change or the 456/13 visual inventory moves; or
- local macOS/Xcode or Android device verification is unavailable for the required final evidence.

Do not broaden around a STOP. Report it and return to specification review.

## 14. Documentation and comment budget

During implementation:

- update `documentation/architecture.md` for the first shared feature and explicit root factory;
- update `documentation/testing.md` for common handlers and the native feature scene;
- update `documentation/ci-cd.md` for the sixth module in the stable Native invocation;
- update feature/navigation documentation only where a path or platform-ownership statement is
  stale; and
- append exact local, negative-control, CI, review, and merge evidence here.

Keep production comments only for durable generation ownership, one-read graph, opaque image
model, gesture, or result-request invariants. Do not add migration narration, a parallel handoff,
changelog, generated report, or history comment.

## 15. Implementation commit plan

After an explicit GO, use signed Conventional Commits and keep every commit buildable:

1. `refactor(kmp): share image viewer feature entry` — atomically convert the module, move
   resources/tests, inject the graph factory through the root, update the exact identity test, and
   add the native scene;
2. `ci(kmp): gate shared image viewer feature` — extend topology, Native workflow, and XML
   validation; and
3. `docs(kmp): record Phase 7.6 evidence` — update only stale docs and append measured evidence.

The module conversion and root factory flow are one atomic commit: neither is a valid intermediate
state. Never commit a failing intermediate state, generated build output, or recorded golden.

## 16. Exit criteria

Phase 7.6 is complete only when all are true:

- the exact 19-file feature topology is committed and enforced;
- all 13 production files, the Metro extension, and private 6+6 resource catalog compile from
  `commonMain` for Android and the iOS simulator;
- Coil uses `LocalPlatformContext` while preserving request/crossfade/fit/error behavior;
- image-viewer contains no Android Context lookup, Android resource/API/annotation, Java/Javax,
  expect/actual, platform shim, global factory, or legacy source;
- the generation-owned factory flows exactly from `AppRootDeps`/`AppGraph` through
  `AppNavigationHost` into `ImageViewerFeature` after admission;
- the remaining `Context.appDeps` population is exactly the 12 unported feature/dialog readers;
- 12 common handler cases execute on Android host and Native;
- all three Android extension identity cases execute through the root accessor;
- all four unchanged Android regression journeys and the admission-race identities pass, with
  exactly one app-root dependency resolution for the composed admitted region and zero for both
  rejected paths;
- the exact native scene executes once and proves resources, branches, platform context, and
  actions;
- all 456 repository PNGs and 13 golden modules remain byte/path identical;
- stable required jobs enforce the new topology and exact Native identity without renaming;
- focused, repository, visual, device, and known-negative gates are recorded green/red/green;
- rulesets, contexts, versions, compiler policy, device membership, and filters remain unchanged;
- documentation is current, implementation commits are signed/Verified, checks are green, and
  every review thread is classified and resolved; and
- the maintainer, not the implementation agent, performs the merge.

## 17. Boundary and remaining migration roadmap

Phase 7.6 is implemented in open PR #269 under the maintainer's explicit GO, but it is not complete
until the maintainer merges it. The implementation does not pre-authorize any later roadmap item.

Phase 7.6 proves one reusable feature-entry pattern but does not pre-authorize a batch conversion.
After it completes, the ordered frontier is:

1. migrate the remaining 11 navigation feature entries one measured slice at a time, keeping
   Android releasable and solving each feature's data/resource/platform dependencies at their
   owners;
2. migrate the remaining app-dialog entry reader and explicitly settle the recovery/activity
   boundary;
3. convert real `app:common` only after all of its feature entries, dialog host, recovery branch,
   navigation host, resources, and dependency graph are portable;
4. add the permanent iOS host through real `app:common`, preferring direct framework integration
   and covering its first window with XCTest/XCUITest; and
5. finish iOS-owned runtime/recovery, database/filesystem, image acquisition, Google auth/Drive,
   observability/Firebase substitute, signing, CI, TestFlight, and release under separate measured
   specifications.

A throwaway `iosApp` that bypasses `app:common` remains forbidden. Phase-5 replacement,
publication, admission, retirement, journal, and recovery semantics remain separately authoritative
throughout the remaining roadmap.

## 18. Implementation evidence

### 18.1 Entry gate and measured boundary

On 2026-08-29 the implementation fetched `origin/dev`, proved it exactly equal to the required
base `5bcecf721caf8ec216b3c50133b45538a027a616`, and created
`feature/kmp-phase-7-6-image-viewer` from that commit in a clean isolated worktree. The delta from
the Phase 7.5 merge `479a2960574ff165f4f6b99a24d49ab1c961bbd8` to the required base was only
the merged Phase 7.6 specification. The primary checkout's user-owned untracked
`.claude/settings.local.json` and `cleanup-branches.sh` remained untouched and unstaged.

The repeated Section 11.1 inventory matched the specification: 13 production Kotlin files, 6 EN
plus 6 RU Android values, three handler suites with 11 cases, three app graph identities, four
unchanged Android regression journeys, two direct consumers, 13 `Context.appDeps<Factory>()`
readers, no image-viewer golden, and 456 PNGs across the same 13 golden modules. Fresh baseline
gates executed successfully. Xcode 26.6, an iOS simulator runtime, and API-34 Android devices were
available. The active repository-wide `all` ruleset remained id `8116593` with the exact required
contexts `Build and Unit Tests` and `KMP iOS kit smoke`; the dedicated `dev` ruleset remained
disabled. No entry STOP condition was reached.

### 18.2 Implemented topology and ownership

The implementation produces the exact 19-file feature manifest: 13 production Kotlin files and
two private 6+6 CMP catalogs in `commonMain`, three deterministic handler suites in `commonTest`,
and one production scene in `iosTest`. There is no feature production or test file under legacy
`src/main`, `src/test`, or `src/androidTest`, and no Kotlin-bearing `androidMain`, `iosMain`,
`androidHostTest`, or `androidDeviceTest`. The module uses `convention.kmpComposeLibrary`, retains
Metro and `includeJavax()`, exposes only the specified API dependencies, and has no expect/actual,
platform shim, public resource class, Android Context/resource/annotation import, or network Coil
loader.

`AppRootDeps.imageViewerGraphFactory` is implemented directly by `AppGraph`. After
`GenerationAdmission.granted`, `App()` resolves one dependency instance for the live
generation-keyed region; the same instance supplies `AppRootViewModel` and
`AppNavigationHost`, which passes the required factory through `imageViewerGraph` into
`ImageViewerFeature`. The feature invokes the factory only inside the retained Store creation
lambda. The admitted region resolves app-root dependencies exactly once; retired and
publication-race generations resolve them zero times. The remaining 12 unported feature/dialog
Context readers are unchanged.

Coil uses `ImageRequest.Builder(LocalPlatformContext.current).data(model).crossfade(true).build()`
with the existing `AsyncImage`, `ContentScale.Fit`, error, gesture, animation, tag, and unavailable
UI behavior. `Screen.ExerciseImage.model` remains an opaque caller-owned `String`. No acquisition,
storage, filesystem, replacement, deletion, route, Store, action, event, result-name, scope, or
creator contract changed.

### 18.3 Fresh positive verification

Every cited positive Gradle gate used `--rerun-tasks --no-build-cache`; KMP and Native gates also
used `--no-configuration-cache`. Evidence was accepted only when tasks executed. Focused feature
assemble, Android-host test, lint, and Native execution completed `255/255`; `app:common`
assemble/unit completed `468/468`; and `app:app` assemble/unit/androidTest-assemble completed
`707/707`. The app instrumented-classpath gate scanned 23 source files and 276 entries with zero
missing classes.

Fresh structural XML proved the exact 12 common handler cases on Android host (Click 8, Common 2,
Navigation 2) and 13 image-viewer Native cases: those 12 plus exactly one
`io.github.stslex.workeeper.feature.image_viewer.ImageViewerSceneIosTest.resourcesBranchesAndActionsRenderAndDispatch`.
All had zero skips, failures, or errors. The stable six-module Native invocation executed
`161/161`; its validator proved kit `1`, navigation `1`, MVI `14`, start-mode `2`, plan-editor
`20`, and image-viewer `13`, with every required identity exactly once and no skip, failure, or
error.

All three `ImageViewerExtensionIdentityTest` identities passed through
`appGraph.imageViewerGraphFactory`. All three `UiAdmissionRaceTest` identities passed with exact
one/zero/zero resolution, and the exact route journey passed. Fresh repository gates were
`assembleDebug` `1196/1196`, `assembleDebugAndroidTest` `1939/1939`,
`verifyPaparazziDebug` `621/621`, `:lint-rules:test` `9/9`, `detekt` `57/57`, `lintDebug`
`1091/1091`, and `testDebugUnitTest` `1134/1134`, all executed and successful. The personal-data
gate passed with only its documented exceptions.

Fresh device Smoke executed `2012/2012`; XML proved 44 discovered / 41 executed / exactly three
named `pendingFeatureRewrite` skips / zero failures and errors, and the MVI exact-identity validator
passed. Fresh Regression on the API-34 portrait AVD executed `2012/2012`; XML proved 81/81 with
zero skips, failures, or errors, including all four protected image-viewer journeys. A preliminary
landscape-TV Regression attempt was rejected rather than counted after its coordinate clicks hit
the exercise Save dock; the unchanged identities passed on the repository's portrait device
profile. Both devices were restored to `mStayOn=false` afterward.

The base and implementation heads contain exactly the same 456 PNG Git tree entries — mode, blob,
and path — across the same 13 golden modules. No image was recorded or mutated. No suppression,
baseline, tolerance, version, convention, compiler policy, ruleset, required context, device
membership, or filter changed.

### 18.4 Mandatory known-negative controls

All 15 controls ran as fresh GREEN, named observable RED, exact restoration without Git recovery,
and fresh GREEN. `documentation/mockups/mutation_harness.py` performed text mutations; the
same-count topology move used direct filesystem moves with exact restoration:

1. moving one production file to legacy `src/main` made the exact topology gate RED;
2. an AndroidX annotation import in common production made platform rejection RED;
3. removing Coil crossfade made the exact request contract RED;
4. changing the EN action title made the Native scene RED, and removing the RU value made exact
   resource ownership RED;
5. removing the non-editable menu guard made its Click handler identity RED;
6. mapping REPLACE to REMOVE made the owning Click handler identity RED;
7. changing the `BackWithRequest` request name made the exact navigation identity RED;
8. misqualifying `AppGraph.imageViewerGraphFactory` made all three graph identities RED;
9. bypassing the host factory with a throwing factory made topology and the exact route journey
   RED;
10. resolving dependencies twice behind admission made the admitted identity RED;
11. resolving dependencies before admission made both rejected identities and the admitted
    identity RED;
12. suppressing the production Scaffold made the Native scene RED;
13. suppressing back dispatch made the Native action assertion RED;
14. renaming the Native method produced passing XML that the exact identity validator rejected;
    and
15. a Native-test `MaxLineLength` violation made root Detekt RED.

Restoration greens executed the owning topology (`5/5`), handler (`104/104`), graph (`524/524`),
device (`683/683`), Native (`79/79`, or `80/80` for regenerated identity XML), and Detekt (`57/57`)
gates as applicable. An initially non-observable accessor mutation, syntax-only/non-executed
attempts, and sandbox-blocked verdicts were rejected and are not counted. No compensating total or
PNG mutation was used. No temporary mutation, generated output, report, local configuration, or
secret entered a commit.

### 18.5 Implementation-head delivery checkpoint

The implementation and CI commits are
`40e4677ce772c2c710d52a2b740b8c15e2febc42` (`refactor(kmp): share image viewer feature entry`)
and `fdcc3e4a590737beba07a9e2a40528a855e48cae` (`ci(kmp): gate shared image viewer feature`).
Both are signed and GitHub reports both signatures as Verified.

At implementation head `fdcc3e4a590737beba07a9e2a40528a855e48cae`, unified workflow run
`33252003892` concluded successfully: `Build and Unit Tests` job `99099173133` took 32m18s, and
`KMP iOS kit smoke` job `99099173224` took 26m13s. The Native job selected Xcode 26.6, executed
all six modules, passed the exact-identity validator, and uploaded their test results. Mockup run
`33252003845`, job `99099173023`, concluded successfully in 34s, including its permanent known
negative. Both required contexts and the mockup gate were green.

Codex review completed against that implementation head with no findings. The live inventory had
zero submitted reviews and zero review threads, so there was no finding to reproduce, classify,
answer, fix, or resolve. PR #269 was `OPEN`, non-draft, `MERGEABLE` / `CLEAN`, targeted `dev` at
the exact required base, and had no merge commit. The documentation-only commit carrying this
checkpoint does not change the implementation boundary; its final-head workflows are reported at
delivery, and the PR remains for maintainer-owned merge.
