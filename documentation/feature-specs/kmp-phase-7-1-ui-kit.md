# KMP Phase 7.1 — `core:ui:kit` becomes the shared CMP foundation

**Status:** SPEC v1 — discovery complete; implementation has not started  
**Target branch:** `dev`  
**Code baseline:** `0638fc7402627bbc78c5ce7a8a2a7c858f23fe24` (PR #252 merge)  
**Delivery:** one implementation PR to `dev`; no direct push

## 0. Authority and entry condition

This specification is the implementation authority for Phase 7.1. It is derived from the code at
the exact baseline above and from these canonical repository documents:

- `documentation/feature-specs/kmp-phase-2-probes.md`, especially P1, P3, P4, P6 and the Phase-7 checklist;
- `documentation/feature-specs/kmp-phase-6-data-layer.md`, especially §8;
- `documentation/feature-specs/kmp-phase-5-startup-processor.md`, only to delimit runtime work;
- `documentation/testing.md` and `documentation/ci-cd.md`;
- `documentation/architecture.md`.

Upstream facts checked for the pinned toolchain:

- Compose Multiplatform 1.10+ supports the unified `androidx` Preview annotation in `commonMain`:
  <https://kotlinlang.org/docs/multiplatform/whats-new-compose-110.html#unified-preview-annotation>;
- Compose resources support localized XML and TTF fonts from `commonMain/composeResources`:
  <https://www.jetbrains.com/help/kotlin-multiplatform-dev/compose-multiplatform-resources-usage.html>;
- CMP 1.11 provides the v2 Compose UI test API on non-Android targets, including execution through
  `iosSimulatorArm64Test`:
  <https://www.jetbrains.com/help/kotlin-multiplatform-dev/compose-test.html>;
- the current `macos-26` runner image contains Xcode 26.6 and the iOS 26.5 simulator runtime:
  <https://github.com/actions/runner-images/blob/564e58dbe650c507ccba1171f6159c12f26820c8/images/macos/macos-26-arm64-Readme.md>.

The implementation branch may start from the docs-only merge that adds this file. Between the code
baseline above and that branch head, only documentation-only changes are allowed. If any production,
test, build-logic, catalog or workflow file has changed, stop and re-run discovery before editing.

## 1. Decision

Phase 7.1 converts **only** `core:ui:kit` from an Android Compose library to a Kotlin
Multiplatform / Compose Multiplatform library and makes the repository's visual gate genuinely
observe that KMP module.

The exit claim is deliberately narrow:

> `core:ui:kit` is a CMP library with preserved Android production call sites and pixel behaviour, byte-identical
> committed goldens, a live KMP-aware Paparazzi gate, and a compiled/executed iOS-simulator slice.

It does **not** claim that an iOS application or the production iOS runtime exists.

### Why this comes before `iosApp`

The order is forced by measured repository facts:

- the Phase-2 checklist requires the first golden-bearing conversion to ship the KMP visual gate;
- `core:ui:kit` must precede every UI leaf: 20 of the 21 Compose modules depend on it;
- the sole exception, `core:ui:navigation`, owns no goldens and proves none of the first-conversion risks;
- `app:common` is still Android-only and depends on `kit`, 12 Android-only feature entries,
  Android-only dialogs and Android-only recovery;
- creating a visible iOS screen now would therefore either bypass `app:common`, introduce a parallel
  temporary UI root, or turn the first PR into a multi-layer migration.

`core:ui:golden-harness` was already extracted and is already consumed by `kit`. It remains an
Android-only test-support module in this stage.

## 2. Measured baseline

At the code baseline, `core:ui:kit` contains:

| Surface | Measured inventory |
| --- | ---: |
| Production Kotlin files | 100 |
| Android resource files | 9: 7 TTF fonts + default/Russian `strings.xml` |
| String resources | 54 default / 53 Russian; the non-translatable work mark inherits default |
| Android host-test Kotlin files | 45 under `src/test` |
| Source-level host test functions | 195: 145 `@Test` + 50 `@ParameterizedTest` |
| Golden test classes/functions | 20 classes / 43 parameterized functions |
| Kit golden PNGs | 86 |
| Device-test files/functions | 1 file / 5 `@Test` methods; `@Smoke` is class-level |
| Repository golden PNGs | 456 total, including the 86 owned by `kit` |

The Android-coupled production seams are small but load-bearing:

- Android shader/build-version APIs in `components/Noise.kt`;
- `SystemClock.elapsedRealtime()` in `components/paging/LoadingVisibility.kt`;
- Android activity/configuration/window APIs in `theme/AppTheme.kt`;
- Android `View`/IME APIs in `utils/CommonExt.kt`;
- Android `Activity` and JVM `WeakReference` in `utils/activityHolder/**`;
- JVM `AtomicLong` in `snackbar/SnackbarManager.kt`;
- Android `R.string` / `R.font` and Android resource helpers in 14 production files;
- 37 preview files that reference `Configuration.UI_MODE_NIGHT_YES` directly.

## 3. In scope

The implementation PR must contain all of the following and nothing broader:

1. Apply `convention.kmpComposeLibrary` to `core:ui:kit`, retaining Metro and Paparazzi.
2. Move/split production code into `commonMain`, `androidMain` and `iosMain` as specified below.
3. Move strings and fonts to Compose Multiplatform resources and migrate every production/test caller.
4. Move host tests and goldens to the KMP Android-host source-set layout.
5. Move the five instrumented tests to the KMP Android-device source-set layout.
6. Make `gradle/golden-gate.gradle.kts` support both classic Android and KMP modules without
   weakening the classic path.
7. Add both Paparazzi compatibility task aliases used by repository workflows/documentation.
8. Add one non-vacuous iOS-simulator smoke test over a real resource-backed `kit` composition and
   run it in a narrow macOS CI job.
9. Update only the documentation made stale by the task/source-set names in this PR, and append the
   final verification record to this specification.

## 4. Explicit non-goals

Do not add, convert or redesign any of the following:

- `iosApp`, an Xcode project, an XCFramework export or a production app/UIKit shell/window;
- a production framework binary exported from `kit`;
- `core:ui:navigation`, `core:ui:mvi`, specialized UI modules, feature modules or `app:common`;
- a production iOS Metro graph or composition root;
- `iosArm64`; the existing `iosSimulatorArm64` target remains the only Apple target in this stage;
- an iOS Room factory/path, DataStore root, ImageStorage, backup, restore or undo;
- `AppRuntime`, `RuntimeGeneration`, `StartupProcessor`, `RebuildInProcess` or production
  `AppReinitializer` binding;
- recovery journal/finalization, DB-free recovery routing, UI admission, generation ViewModelStore,
  saveable-state reset, Store quiescence or background leases;
- iOS scene/multi-window lifecycle ownership;
- navigation, dependency, Kotlin, Compose, AGP, Paparazzi or Haze upgrades;
- visual redesign, string copy changes, font replacement, golden re-recording or tolerance changes;
- new lint/detekt baselines, broad suppressions or unrelated debt cleanup;
- a repository-wide test source-set cleanup;
- any change under the Room schema directories.

The iOS-specific fallback behaviour introduced inside `kit` is library portability work, not a
production iOS runtime claim.

## 5. Required source-set shape

Use `git mv` for every whole-file move. Split only files that contain a real platform seam.

| Current path | Required destination/shape |
| --- | --- |
| `src/main/kotlin/**` | `src/commonMain/kotlin/**`, except the explicit platform seams below |
| `utils/CommonExt.kt` | `src/androidMain/kotlin/**`; keep `OnKeyboardVisible` Android-only |
| `utils/activityHolder/**` | `src/androidMain/kotlin/**`; keep current package/API for Android callers |
| `components/Noise.kt` | shared public wrappers in `commonMain`; internal Android/iOS platform hook implementations |
| `theme/AppTheme.kt` | shared theme construction in `commonMain`; internal Android/iOS window-effect hook implementations |
| `src/main/res/values/strings.xml` | `src/commonMain/composeResources/values/strings.xml` |
| `src/main/res/values-ru/strings.xml` | `src/commonMain/composeResources/values-ru/strings.xml` |
| `src/main/res/font/*.ttf` | `src/commonMain/composeResources/font/*.ttf` |
| `src/test/kotlin/**` | `src/androidHostTest/kotlin/**` |
| `src/test/snapshots/images/*.png` | `src/androidHostTest/snapshots/images/*.png` |
| `src/androidTest/kotlin/**` | `src/androidDeviceTest/kotlin/**` |
| new native smoke | `src/iosTest/kotlin/**`, executed by `iosSimulatorArm64Test` |

No duplicate production tree may remain under `src/main`. No test may be copied while its old copy
remains discoverable.

## 6. Production portability decisions

### 6.1 Theme and Android window chrome

Keep the public `AppTheme(themeMode, content)` API in `commonMain`.

Extract one internal composable platform hook for the host/window side effect:

- the Android implementation must preserve the current `LocalConfiguration`, `LocalActivity` and
  `WindowCompat` behaviour, including light-status-bar appearance;
- the iOS implementation is a no-op because the future iOS host owns native window chrome;
- dark/light palette selection remains common through `isSystemInDarkTheme()` and explicit
  `ThemeMode` values.

Do not introduce a public platform theme interface, a new CompositionLocal or an iOS host object.

### 6.2 Noise

Keep `NoiseBox`, `NoiseColumn` and `drawNoiseOrFallback` callable from common code. Hide the
platform choice behind one internal hook:

- Android keeps the current API-level check, feature flag, AGSL shader and base-colour fallback;
- iOS returns the base-colour fallback and does not pretend to implement the Android shader;
- direct shader-only helpers stay in `androidMain` and are not part of the shared contract.

The shader implementation's `org.intellij.lang.annotations.Language` and
`androidx.annotation.RequiresApi` usage must remain in `androidMain` with that implementation (or
be removed if no longer needed); neither annotation may leak into the common wrapper.

This preserves the reusable kit surface without inventing an unmeasured iOS shader.

### 6.3 Time

Remove Java/Android clocks from common code:

- loading hold/delay calculations must use a common monotonic time source, never wall-clock time;
- date-picker preview and `DateProperty.now()` may use `kotlin.time.Clock.System` epoch milliseconds;
- keep `loadingStep(...)` and `loadingHoldRemaining(...)` pure and retain their current tests.

The Android timing semantics and configured delays must not change.

### 6.4 Snackbar generation epoch

Replace JVM `AtomicLong` with a Kotlin/Native-safe, linearizable common mechanism. Prefer the
already-present `MutableStateFlow<Long>` plus `update`/`updateAndGet`; do not add an atomic library
for one counter.

Preserve all Phase-5 invariants already pinned by `SnackbarManagerTest`: enqueue stamps the current
epoch, requeue keeps the original epoch, committed handover advances before successor publication,
abort does not advance, stale callbacks never execute and resolve fencing remains linearizable.

Add or sharpen one focused host test if the chosen replacement could pass the existing suite while
losing increments under concurrency. Do not expose epoch state as production API solely for a test.

### 6.5 Resources and typography

Move all nine files byte-for-byte to Compose resources. Keep every resource key and every localized
value unchanged. Replace Android `R` and `androidx.compose.ui.res.stringResource` callers with the
generated Compose Resources accessors and `org.jetbrains.compose.resources` APIs.

Compose-resource `Font(...)` is composable. Refactor typography so that:

- the resource-backed font families are created from the same seven TTF files inside composition;
- `AppTheme` obtains the resulting `AppTypography` without calling a composable from a `remember`
  calculation block;
- the type-scale construction remains a pure function accepting font families;
- `AppTypographyContractTest` tests that pure function with distinct common `FontFamily` values;
- Paparazzi remains the byte-level oracle for the actual bundled faces.

No font bytes, weights, size, tracking, line height or fallback policy may change.

### 6.6 Previews

Keep the unified `androidx.compose.ui.tooling.preview.Preview` annotation in common code; CMP 1.11.1
supports that annotation from `commonMain`. Remove direct references to Android
`Configuration.UI_MODE_NIGHT_YES` by replacing them with
`internal const val PREVIEW_UI_MODE_NIGHT_YES = 0x20`, a preview-only common constant accepted by
the annotation. Do not duplicate 37 preview bodies into `androidMain`.

Preview-only refactoring must not change production composition or golden content.

### 6.7 Android-only utilities

`OnKeyboardVisible` and `utils/activityHolder/**` remain Android-only. Repository search finds no
common consumer that requires an iOS abstraction, so this PR must not invent one or move the
activity holder into the production iOS graph.

## 7. Build and dependency contract

### 7.1 Plugins and source-set dependencies

`core:ui:kit` must keep:

- `convention.kmpComposeLibrary`;
- Metro with `includeJavax()`;
- Paparazzi;
- the shared golden-gate script.

Re-declare dependencies explicitly because conversion removes the Android convention's blanket
dependency closure.

| Source set | Required capabilities |
| --- | --- |
| `commonMain` | `:core:core`, `kotlinx-coroutines-core`, immutable collections, KMP Paging Compose, Haze core/materials, CMP Animation and CMP material icons core/extended |
| `androidMain` | Activity Compose, AndroidX core-ktx and explicit `javax.inject` for the retained window/View APIs and Metro interop |
| `androidHostTest` | Compose UI test JUnit4, `:core:ui:golden-harness`, Robolectric, the Robolectric JUnit5 extension and AndroidX test core |
| `androidDeviceTest` | the current Android test bundle, Compose UI test JUnit4, UI-test manifest and `:core:ui:test-utils` |
| `iosTest` | `kotlin("test")` and CMP UI Test for the native headless-scene gate |

Add the direct catalog alias
`org.jetbrains.compose.animation:animation:${composeGradle}` (1.11.1 at baseline). Add a dedicated
`cmpMaterialIcons = "1.7.3"` version and catalog aliases for
`org.jetbrains.compose.material:material-icons-core:${cmpMaterialIcons}` and
`org.jetbrains.compose.material:material-icons-extended:${cmpMaterialIcons}`, then use those three
aliases from `commonMain`. The CMP icons line is frozen at 1.7.3; do not incorrectly version it from
`composeGradle`. Keep both icon artifacts direct: core supplies `Add`, `Close`, `Search`, `MoreVert`
and auto-mirrored `ArrowBack`; extended supplies `SearchOff` and `DragHandle`.

`KmpComposeLibraryConventionPlugin` does not supply Animation, and the kit directly imports
`androidx.compose.animation.*`; do not rely on Material3's transitive graph. The animation artifact
also supplies the imported `animation.core` APIs; `animation-graphics` is not needed because the
kit has no such import. Do not use the current AndroidX-only animation/icon aliases there or retain
any Android-only artifact merely to make the Android compile green.

Add `org.jetbrains.compose.ui:ui-test:${composeGradle}` as a direct catalog alias and consume it
only from `iosTest`; use `androidx.compose.ui.test.v2.runComposeUiTest`, not the deprecated v1 entry
point and not the Android JUnit4 rule API. Do not leak that 1.11.1 dependency into
`androidHostTest`, whose JUnit4 bundle is pinned separately at 1.11.4.

Do not copy the classic Android convention wholesale. The measured `kit` sources use no
`BuildConfig`, injected local property, context parameter or interface default body that requires
the lost Android-only convention features/compiler flags. Add only the explicit dependencies and
source-set wiring above; a new repo-wide compiler-policy change is outside this stage.

For Robolectric, retain the module-local
`junit.platform.launcher.interceptors.enabled=true` setting. Do not apply the classic Android
convention merely to regain its transitive test dependencies.

Keep `:core:ui:golden-harness` Android-only and consume it only from `androidHostTest`.

### 7.2 Paparazzi task compatibility

When the Paparazzi plugin is present on a KMP Compose module,
`KmpComposeLibraryConventionPlugin` must register lazy compatibility aliases:

- `verifyPaparazziDebug` depends on `verifyPaparazzi`;
- `recordPaparazziDebug` depends on `recordPaparazzi`.

The underlying KMP tasks remain `verifyPaparazziAndroidMain` and
`recordPaparazziAndroidMain`. The aliases must be ordinary lifecycle tasks, not cast or declared as
`Test` tasks.

Both aliases are required: CI invokes `verifyPaparazziDebug`, while `documentation/testing.md`
instructs maintainers to invoke `recordPaparazziDebug`. Omitting either silently drops `kit` from a
repository-wide command.

### 7.3 Golden liveness script

Parameterize `gradle/golden-gate.gradle.kts` by module kind while preserving the classic path:

| Value | Classic Android | Android-KMP |
| --- | --- | --- |
| Images | `src/test/snapshots/images` | `src/androidHostTest/snapshots/images` |
| Test XML | `build/test-results/testDebugUnitTest` | `build/test-results/testAndroidHostTest` |
| Actual `Test` task | `testDebugUnitTest` | `testAndroidHostTest` |

Configure test filtering through `tasks.withType<Test>()`; never cast the KMP
`testDebugUnitTest` alias to `Test`. In a plain unit-test run, exclude golden classes from the actual
host `Test` task and set `isFailOnNoMatchingTests=false` for the filtered-to-zero case. In Paparazzi
mode, keep the actual host test task non-cacheable and never up-to-date so the liveness assertion
cannot read replayed XML.

The liveness assertion must still fail when there are no committed PNGs, no current XML, or fewer
executed non-skipped golden cases than committed PNGs. It must continue to work for all 12 remaining
classic golden modules.

### 7.4 Existing KMP aliases

Use the aliases already provided by `KmpLibraryConventionPlugin`:

- `assembleDebug -> assemble`;
- `testDebugUnitTest ->` Android host `Test` tasks;
- `lintDebug -> lint`;
- `assembleDebugAndroidTest -> assembleAndroidDeviceTest`;
- `connectedDebugAndroidTest -> connectedAndroidDeviceTest`.

Do not register duplicates in `kit`.

## 8. Test and golden migration contract

### 8.1 Host tests

Move all 45 current host-test files to `androidHostTest`. Do not opportunistically sort pure tests
into `commonTest` in this PR: preservation is the first-conversion goal, and the current suite also
contains JVM reflection, Robolectric and Paparazzi.

The post-conversion `testDebugUnitTest` alias must execute a non-zero Android host suite and must not
execute golden classes during a plain unit-test run. The pre-change and post-change JUnit XML counts
must be recorded; the post-change count may not be lower without a named, reviewed explanation.

### 8.2 Device tests

Move `AppConfirmationDialogTest` to `androidDeviceTest` with all five methods and its class-level
`@Smoke` annotation intact. Keep the `:core:ui:test-utils` dependency: without it the AndroidX
runner silently drops the annotation filter.

The KMP device-test APK must assemble through both the underlying task and the repository alias.

### 8.3 Goldens

Move all 86 kit PNGs with `git mv`. Never run a record task as part of the migration and never
accept regenerated PNGs as evidence of parity.

Before and after the move, compare a sorted manifest of `relative filename + SHA-256`. The manifests
must be identical. The repository must still contain exactly 456 committed golden PNGs and `kit`
must still own exactly 86.

Successful verification must report 86 executed kit golden cases for 86 committed images and zero
movers. A higher executed count is acceptable only if a named new golden test was deliberately
added; this stage does not need one.

### 8.4 iOS-simulator smoke

Add exactly one focused Kotlin/Native test under `iosTest`, using
`androidx.compose.ui.test.v2.runComposeUiTest`. Scope the required `ExperimentalTestApi` opt-in to
that test file; do not add a project-wide compiler opt-in. Its `setContent` subject must use:

- `AppTheme`;
- at least one component from `kit`;
- at least one migrated string;
- at least one migrated font and one material icon.

Advance the test clock through at least one frame, wait for idle, and assert the expected Compose
semantics node or migrated text. The resource-backed text must actually be painted with the migrated
typography; merely resolving `Res`, storing an uninvoked composable lambda or compiling an iOS klib
is not evidence.

This gate intentionally exercises the native Skiko raster `ComposeScene` owned by the CMP test
runner. It proves native composition, resource/font/icon loading, one rendered frame and the Compose
semantics tree. It does **not** prove `ComposeUIViewController`, `UIWindow`, Metal/UIKit rendering or
VoiceOver export. Those claims require the permanent product `iosApp` plus XCTest/XCUITest and remain
for a later stage. Do not create a custom `UIApplicationMain` test runner or call this an app-launch
proof.

Do not commit or compare a native raster capture and do not introduce an iOS golden corpus in this
stage. Android Paparazzi remains the only pixel oracle for Phase 7.1.

## 9. CI change

Add a narrow macOS job to the unified build workflow (or a dedicated required workflow with the
same pull-request triggers):

- give the job the stable check name `KMP iOS kit smoke`;
- `runs-on: macos-26` (the current image is Apple Silicon, matching `iosSimulatorArm64`);
- select `/Applications/Xcode_26.6.app/Contents/Developer` explicitly;
- assert `xcodebuild -version` and the available iOS simulator runtime before Gradle;
- set up JDK 21 and the repository Gradle properties;
- generate an ephemeral JKS with JDK `keytool` under the runner's temporary directory and write a
  runner-local `keystore.properties` only to satisfy repository configuration; use no production
  signing secret and commit no credential or generated config;
- run exactly `:core:ui:kit:iosSimulatorArm64Test` with the forced flags in §11;
- publish/upload the Native test XML and require exactly 1 executed, 0 skipped and 0 failed;
- require the test log/XML to identify the native Compose scene test rather than a planted sentinel.

The job must not build an Xcode app, sign an Apple bundle, upload an XCFramework or require App
Store Connect secrets. Keep the existing Linux Android job unchanged except for the KMP visual-gate
support it consumes.

At the pinned baseline, the active repository-wide ruleset requires only the external
`Build project` context, while the `dev` ruleset is disabled. Therefore adding the workflow job does
not by itself make this a protected merge gate. After the first PR run exposes its context and
before merge, the repository owner must add the GitHub Actions context `KMP iOS kit smoke` to an
active ruleset covering `dev`. Until that setting is visible through the GitHub ruleset API, the job
is specification-required evidence but Phase 7.1 is not complete.

## 10. Compatibility invariants

The PR is rejected if any of these changes:

- Android public call sites for shared `kit` components;
- Android status-bar appearance under light/dark/system theme;
- Android noise shader/fallback behaviour;
- loading delay and minimum-hold semantics;
- snackbar FIFO, epoch, requeue, callback or resolve-fence semantics;
- resource keys, default/Russian strings or font bytes;
- the 86 kit images or the 456-repository-image total;
- the five device tests' suite membership;
- classic Android golden-module behaviour;
- the existing Android app graph or startup/recovery runtime.

No new production CompositionLocal, service locator, global iOS holder or DI framework is allowed.
Metro remains the sole DI framework.

## 11. Verification gates

Every Gradle verification below must include:

```text
--rerun-tasks --no-build-cache --no-configuration-cache --full-stacktrace --console=plain
```

Run Detekt separately. Do not combine it with a task that could rewrite or prepare golden inputs.

### 11.1 Characterization before edits

Record the result and test/XML counts for:

```bash
./gradlew :core:ui:kit:testDebugUnitTest <forced flags>
./gradlew :core:ui:kit:verifyPaparazziDebug <forced flags>
./gradlew :core:ui:kit:assembleDebugAndroidTest <forced flags>
```

If an API-34 emulator is available, also record the current 5/5 device result before moving files.

### 11.2 Focused post-conversion gates

```bash
./gradlew :core:ui:kit:assembleDebug <forced flags>
./gradlew :core:ui:kit:testDebugUnitTest <forced flags>
./gradlew :core:ui:kit:verifyPaparazziDebug <forced flags>
./gradlew :core:ui:kit:lintDebug <forced flags>
./gradlew :core:ui:kit:assembleDebugAndroidTest <forced flags>
./gradlew detekt <forced flags>
```

On macOS/Xcode 26.6 and in the new CI job:

```bash
./gradlew :core:ui:kit:iosSimulatorArm64Test <forced flags>
```

With an API-34 emulator:

```bash
./gradlew :core:ui:kit:connectedDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.annotation=io.github.stslex.workeeper.core.ui.test.annotations.Smoke \
  <forced flags>
```

The focused device run must execute all five kit methods.

### 11.3 Repository gates

Run the same gates as the unified Android workflow, with forced flags:

```bash
./gradlew assembleDebug <forced flags>
./gradlew assembleDebugAndroidTest <forced flags>
./gradlew verifyPaparazziDebug <forced flags>
./gradlew :lint-rules:test <forced flags>
./gradlew lintDebug <forced flags>
./gradlew testDebugUnitTest <forced flags>
./gradlew detekt <forced flags>
python3 documentation/personal_data_gate.py -v
```

Before merge, run the repository Smoke and Regression device suites on API 34 because `kit` is a
dependency of almost the entire UI. The last canonical baseline is Smoke 44 discovered / 41
executed / 3 named pre-existing skips, and Regression 81/81. Re-establish those counts on the
implementation SHA. If the pre-edit characterization already differs, stop and explain the drift;
do not silently rewrite the expected count. A green task with zero selected tests is a failure.

```bash
./gradlew connectedDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.annotation=io.github.stslex.workeeper.core.ui.test.annotations.Smoke \
  <forced flags>
./gradlew connectedDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.annotation=io.github.stslex.workeeper.core.ui.test.annotations.Regression \
  <forced flags>
```

### 11.4 Known-negative controls

Execute each mutation only after its corresponding green baseline, then restore the exact bytes/code
and prove green again:

1. Change one pixel in a copied kit PNG while keeping it a valid image:
   `:core:ui:kit:verifyPaparazziDebug` must fail with a visual diff.
2. Add one phantom PNG beside the 86 committed images: liveness must fail with 86 executed versus 87 committed.
3. `--dry-run` both compatibility aliases: verify must reach `verifyPaparazziAndroidMain`; record
   must reach `recordPaparazziAndroidMain`. Do not execute the record alias.
4. Replace the native scene subject with blank content: the Compose semantics assertion and macOS
   job must fail. Restore it and prove exactly one passing, non-skipped test.
5. Put an `android.os.Build` reference into `commonMain`: `compileKotlinIosSimulatorArm64` must
   fail; restore it and prove the native smoke green.
6. Remove the device-test edge to `:core:ui:test-utils`: the instrumented-suite classpath guard
   must fail while device sources are non-zero; restore the edge before rerunning the suite.

The final worktree must contain none of the mutation artifacts and no changed PNG bytes.

## 12. Commit decomposition

Keep the PR reviewable and bisect-green:

1. **Build/gate support:** parameterize the golden script and add lazy KMP Paparazzi aliases while
   all current modules are still classic; prove classic root gates remain green.
2. **Atomic kit conversion:** apply the KMP convention; move/split production code, resources,
   host/device tests and goldens together; add the native smoke. Do not land a commit in which tests
   or goldens disappear from discovery.
3. **CI and verification record:** add the narrow macOS job, update task-path documentation and
   append exact green/red/green evidence to this file.

If commit 2 cannot be bisect-green as one atomic conversion, keep it as one commit rather than
creating an intermediate false green.

## 13. Documentation and comment budget

This file owns the migration rationale. Preserve existing comments when their invariant remains
true, but do not add narration to every moved source file and do not bulk-delete existing comments.

New code comments are allowed only at a non-obvious guard boundary: visual-gate liveness, monotonic
time, native fallback or Android window ownership. Prefer one central explanation over repeated
comments at call sites.

## 14. Exit criteria

Phase 7.1 is complete only when all statements below are true:

- `core:ui:kit` has common, Android and iOS-simulator compilations under the KMP Compose convention;
- the Android app compiles and the complete forced Android gate battery is green;
- all 45 host files and all five device-test methods remain discovered;
- all 86 kit PNGs and all 456 repository PNGs are byte-identical to baseline;
- root `verifyPaparazziDebug` includes the converted module and reports a live, non-zero gate;
- root `recordPaparazziDebug --dry-run` includes the converted module;
- corrupt/phantom visual mutations and the native scene-bypass mutation all fail for the intended reason;
- the native smoke executes 1/1 on Xcode 26.6 in CI;
- the active GitHub ruleset covering `dev` requires the stable `KMP iOS kit smoke` context;
- no `iosApp`, production iOS graph/runtime or unrelated module conversion is present;
- no new suppressions, baselines, re-recorded goldens or broad dependency upgrades are present;
- Room schema blobs are unchanged;
- this file contains the exact final task results, test counts and known-negative evidence.

## 15. Stop conditions

Stop implementation and report instead of improvising if any of these occurs:

- the implementation base contains non-documentation drift from the pinned code baseline;
- either compatibility alias exists but its dry-run graph does not reach the real KMP task;
- any of the 86 PNG hashes changes without a separately approved visual-change specification;
- a claimed host, visual, device or native task is `NO-SOURCE`, produces zero current XML cases or
  is satisfied by replayed evidence;
- the native test cannot render a production resource-backed `kit` subject and assert its Compose
  semantics after a frame;
- the implementation PR's native job has run but its stable context cannot be made required for
  `dev` by the repository owner before merge;
- conversion appears to require converting `golden-harness`, adding `iosArm64`, exporting a
  framework, changing database schemas or widening into another module;
- a proposed fix needs a new suppression/baseline, golden tolerance change or dependency upgrade;
- an Android compatibility invariant fails for a reason not already resolved by this specification.

## 16. Next stage boundary

Only after this PR merges should a fresh discovery/spec choose the next bounded slice. That slice may
port the next UI dependencies and prepare a permanent `iosApp` vertical path, but it must not bypass
`app:common` with a throwaway screen. The first real UIKit-window claim must use that permanent host
and XCTest/XCUITest, not the Phase-7.1 headless test runner. Phase-5 runtime/recovery parity remains a
later, separately specified stage.

## 17. Implementation-agent handoff

Use this exact scope after the docs-only PR is merged:

> Implement `documentation/feature-specs/kmp-phase-7-1-ui-kit.md` from the current `dev` head.
> Verify that its only delta from code baseline `0638fc7402627bbc78c5ce7a8a2a7c858f23fe24`
> before implementation is documentation. Work on a branch, preserve Android behaviour and all
> golden bytes, execute every required positive and negative gate, append the verification record,
> and open one PR to `dev`. Do not implement `iosApp`, `app:common`, navigation, features or runtime
> parity. Stop and report if the baseline or a locked invariant has drifted.

## 18. Final verification record (implementation, 2026-08-26)

Implemented on `feature/kmp-phase-7-1-ui-kit`, forked from `dev` at
`500b4efb243633917aa841207d877fa607ba57b3` (docs-only delta from the code baseline
`0638fc7402627bbc78c5ce7a8a2a7c858f23fe24`, verified before editing). All Gradle evidence below
ran with `--rerun-tasks --no-build-cache --no-configuration-cache --full-stacktrace
--console=plain`; every quoted summary line reads `N actionable tasks: N executed` (no
`from cache`, no `up-to-date`), and every count was read from the produced JUnit XML, never from
an exit code.

### 18.1 Pre-edit characterization (§11.1, at 500b4efb2)

| Gate | Result | Evidence |
| --- | --- | --- |
| `:core:ui:kit:testDebugUnitTest` | green | 23 XML files, 411 test cases, 0 skipped; golden classes excluded |
| `:core:ui:kit:verifyPaparazziDebug` | green | "Visual gate live: 86 golden test case(s) executed for 86 golden image(s)"; 497 total cases in XML |
| `:core:ui:kit:assembleDebugAndroidTest` | green | exit 0 |
| `:core:ui:kit:connectedDebugAndroidTest` (@Smoke, API-34 emulator) | green | `tests="5" failures="0" errors="0" skipped="0"` |
| Golden manifests | recorded | 86 kit PNG SHA-256s; 456 repo-wide PNG SHA-256s |

### 18.2 Post-conversion focused gates (§11.2, HEAD^ of the record commit)

| Gate | Summary line | Counts |
| --- | --- | --- |
| `:core:ui:kit:assembleDebug` | 63 actionable tasks: 63 executed | common+android+iosSimulatorArm64 compile |
| `:core:ui:kit:testDebugUnitTest` | 67 actionable tasks: 67 executed | 23 XML files, 412 cases, 0 skipped (411 baseline + the named §6.4 epoch-concurrency test), goldens excluded |
| `:core:ui:kit:verifyPaparazziDebug` | 68 actionable tasks: 68 executed | 43 XML files, 498 cases; 20 golden suites, 86 executed golden cases; "Visual gate live: 86 golden test case(s) executed for 86 golden image(s)" |
| `:core:ui:kit:lintDebug` | 46 actionable tasks: 46 executed | green |
| `:core:ui:kit:assembleDebugAndroidTest` | 136 actionable tasks: 136 executed | device-test APK assembles via alias → `assembleAndroidDeviceTest` |
| `detekt` (separate run) | 57 actionable tasks: 57 executed | green |
| `:core:ui:kit:iosSimulatorArm64Test` | 38 actionable tasks: 38 executed | XML: `tests="1" skipped="0" failures="0" errors="0"`, case `sheetLayoutRendersMigratedStringFontAndIcon[iosSimulatorArm64]` (Xcode 26.6 local) |

Golden byte identity: the post-move sorted `filename + SHA-256` manifest of
`src/androidHostTest/snapshots/images` is identical to the pre-move manifest of
`src/test/snapshots/images` (86/86), and the repository still holds exactly 456 committed golden
PNGs — zero movers, zero re-records, `git mv` throughout.

### 18.3 Repository gates (§11.3)

All eight §11.3 gates green with forced flags on the same tree as §18.2:

| Gate | Summary line |
| --- | --- |
| `assembleDebug` | 1102 actionable tasks: 1102 executed |
| `assembleDebugAndroidTest` | 1983 actionable tasks: 1983 executed |
| `verifyPaparazziDebug` | 617 actionable tasks: 617 executed — 13 "Visual gate live" lines (12 classic modules + kit's 86), summing to 456 |
| `lintDebug` | 1097 actionable tasks: 1097 executed |
| `testDebugUnitTest` | 1129 actionable tasks: 1129 executed |
| `:lint-rules:test` | 9 actionable tasks: 9 executed |
| `detekt` | 57 actionable tasks: 57 executed |
| `python3 documentation/personal_data_gate.py -v` | exit 0 (only the two pre-excused entries) |

The root `verifyPaparazziDebug` line set proves the repo-wide command reaches the converted KMP
module: kit's alias chain (`verifyPaparazziDebug` → `verifyPaparazzi` →
`verifyPaparazziAndroidMain`) executed 86 golden cases against 86 committed images inside the
same invocation that verified the 12 classic modules.

### 18.4 Known-negative controls (§11.4)

Each mutation ran only after its green baseline above, then the exact bytes/code were restored
and green re-proven. Golden PNG SHA-256s re-checked against the baseline manifest after the
last control: identical, no mutation artifacts in the worktree.

1. **Corrupt pixel.** One pixel byte flipped (XOR 0x80 mid-raster) in
   `…AppButtonGoldenTest_primaryButton_light.png`, still a structurally valid PNG (all chunk
   CRCs recomputed). Forced `:core:ui:kit:verifyPaparazziDebug`: exit 1 — exactly
   `AppButtonGoldenTest > primaryButton [1] LIGHT FAILED` with a delta written under
   `build/paparazzi/failures/`; its DARK sibling passed. Restored bytes
   (`1242c592…` re-verified), re-run green: "Visual gate live: 86 … for 86".
2. **Phantom PNG.** An 87th image (`…PhantomGoldenTest_phantom_light.png`) beside the 86:
   forced verify failed with "Visual gate did not run: 86 golden test case(s) executed but 87
   golden image(s) are committed." Removed; re-run green 86/86.
3. **Dry-run alias graphs.** `:core:ui:kit:verifyPaparazziDebug --dry-run` lists
   `:core:ui:kit:testAndroidHostTest`, `:core:ui:kit:verifyPaparazziAndroidMain`,
   `:core:ui:kit:verifyPaparazzi`, `:core:ui:kit:verifyPaparazziDebug`;
   `:core:ui:kit:recordPaparazziDebug --dry-run` lists
   `:core:ui:kit:recordPaparazziAndroidMain`, `:core:ui:kit:recordPaparazzi`,
   `:core:ui:kit:recordPaparazziDebug`. All SKIPPED — no record task ever executed.
4. **Blank native scene.** Mutation harness (`--expect RED`): subject wrapped in `if (false)`,
   forced `iosSimulatorArm64Test` → `sheetLayoutRendersMigratedStringFontAndIcon FAILED`
   (semantics assertion), "38 actionable tasks: 38 executed". Harness restored the file;
   re-run green with XML `tests="1" skipped="0" failures="0" errors="0"`.
5. **`android.os.Build` in commonMain.** Mutation harness (`--expect INVALID`, the harness's
   verdict for a mutant the compiler kills — which is this control's expected outcome):
   `compileKotlinIosSimulatorArm64` failed with `Unresolved reference 'android'` at the mutated
   line. Restored; native smoke re-proven green 1/0/0.
6. **Dropped `:core:ui:test-utils` device edge.** Mutation harness (`--expect RED`): forced
   `:core:ui:kit:assembleDebugAndroidTest` → `Task :core:ui:kit:verifyInstrumentedSuiteClasspath
   FAILED` while device sources are non-zero. Restored; re-run green
   ("136 actionable tasks: 136 executed").

### 18.5 Notes a reviewer should not have to rediscover

- Paparazzi 2.0.0-alpha05's `PrepareResourcesTask` cannot serialize its KMP asset-dir providers
  into the configuration cache and the store failure fails the whole build; the KMP compose
  convention marks `preparePaparazzi*` `notCompatibleWithConfigurationCache`, which degrades
  those invocations to a no-cc run instead.
- Compose-resource strings under Paparazzi need an Android `Context` handed to
  `setResourceReaderAndroidContext` (the app wires it through a content provider that Paparazzi
  never runs); the golden harness now does this after every `Paparazzi`/`PaparazziSdk` setup.
  Fonts loaded without it (they resolve through composition-local context), which is why only
  string-bearing goldens exposed the hole.
- The host-side `runComposeUiTest` needs `ui-test-manifest` on `androidHostTest` (the classic
  module got it from `debugImplementation`, which AGP-KMP lacks); the device-test copy plus the
  compose BOM platform pins the same artifact for the device APK.
- detekt caches rule-plugin classloaders per daemon keyed by jar path: after editing
  `lint-rules` path anchors, a warm daemon still reports with the old rules — `./gradlew --stop`
  first (the repo's known detekt classloader trap, re-measured on this arc).
- The two non-composable kit-string call sites (`LiveWorkoutMapper`, exercise `ClickHandler`)
  read the suspend CMP accessor behind `runBlocking`; CMP caches the parsed string table after
  the first read. `ClickHandlerTest` stubs that accessor (`mockkStatic`) because the plain-JVM
  suite deliberately has no Android environment.

### 18.6 Device suites (§11.3, API-34 emulator)

API-34 emulator (`Pixel_6_API_34`, animations off), forced flags, `--continue`:

| Suite | Result |
| --- | --- |
| `:core:ui:kit:connectedDebugAndroidTest` `@Smoke` (pre-edit, §11.1) | `tests="5" failures="0" errors="0" skipped="0"` |
| `:core:ui:kit:connectedDebugAndroidTest` `@Smoke` (post-conversion) | "Starting 5 tests … Finished 5 tests", BUILD SUCCESSFUL, 137 actionable tasks: 137 executed — via the alias onto `connectedAndroidDeviceTest` |
| Repository `@Smoke` | 14 module XMLs: **44 discovered / 41 executed / 3 skipped / 0 failures** — the canonical baseline re-established, kit's five among them |
| Repository `@Regression` | 14 module XMLs: **81 discovered / 81 executed / 0 skipped / 0 failures** |

### 18.7 CI / ruleset

The `KMP iOS kit smoke` job is added to `android_build_unified.yml` (`runs-on: macos-26`,
explicit `Xcode_26.6` selection, runtime assertion, ephemeral `keytool` JKS, forced-flag
`:core:ui:kit:iosSimulatorArm64Test`, and a step that fails unless the produced XML shows
exactly 1 executed / 0 skipped / 0 failed and names the scene test). Ruleset state measured at
implementation time via the ruleset API: the active repository-wide ruleset (`all`, id 8116593)
requires only the external `Build project` context; the `dev` ruleset (id 18553518) is disabled
with no checks. **Owner action before merge:** once this PR's first run exposes the
`KMP iOS kit smoke` context, add it to an active ruleset covering `dev`; until the setting is
visible through the ruleset API, Phase 7.1 is not complete (§9, §14).

**First CI runs (PR #256).** Three PR-iteration fixes surfaced by CI, each a stale anchor or a
measured resolver behaviour, none a gate weakening: the mockup gate's check 9 reads
`AppColors.kt` by repository path (moved to `commonMain`; the gate failed file-not-found and
went green on the fix — its known negative still red); the mutation harness's registered kit
case moved to the `commonMain` path and the real `testAndroidHostTest` task (the KMP
`testDebugUnitTest` alias cannot accept `--tests`), re-proven red/restore one-shot; and the
ephemeral smoke keystore is copied into the workspace because the application convention
resolves `storeFile` against the repository root even for absolute paths
(`java.io.File(parent, "/abs")` concatenates). Runs recorded with the exact
SHA each one tested: on `811ade13c` both workflows are green — `v3 Mockup Appearance Gate`
(run 32971371393) and `Android CI/CD - Unified Build and Tests` (run 32971371429), the latter's
`Build and Unit Tests` and `KMP iOS kit smoke` jobs both succeeding on `macos-26`/Xcode 26.6,
the smoke assertion step printing "native gate live: 1 executed Compose-scene test, 0 skipped,
0 failed". The final head of that arc, `04efb61eb` (a documentation-only addendum on top of
`811ade13c`), was then tested by its own green runs — `Android CI/CD - Unified Build and Tests`
run 32972840396 and `v3 Mockup Appearance Gate` run 32972840400. The `KMP iOS kit smoke` check
context is now exposed; the ruleset addition remains the owner's step.

## 19. Review-correction record (follow-up, 2026-08-26)

The first implementation arc left two production `runBlocking { getString(Res.string…) }`
bridges, a public `expect/actual` where §6.2 requires a common public wrapper over an internal
hook, and two record inaccuracies. This follow-up closes all four without widening the phase.

### 19.1 The blocking CMP-resource bridges are gone

- **`feature:exercise` `ClickHandler.processConfirmPermanentDelete`** — the undo label now
  resolves through the handler's own Store-scoped `launch` (the existing owner-scoped coroutine
  boundary): the dialog still closes synchronously, the coroutine starts inside the click's
  call stack (`workDispatcher = mainDispatcher`, main-immediate needs no dispatch from main),
  the snackbar registration — the suspend `getString` plus the enqueue — rides
  `withContext(NonCancellable)` so the pop this flow itself triggers cannot separate the closed
  dialog from the queued undo window, and `Action.Navigation.Back` is consumed only from
  `onSuccess`, strictly after registration. The delete still runs only from the model's
  `onDismissed`, capturing the interactor and never the Store.
- **`feature:live-workout` `LiveWorkoutMapper`** — the mapper no longer performs any resource
  lookup. The localized reps unit resolves at the smallest existing suspend boundary owned by
  the Store — `CommonHandler`'s init/reload `launch` blocks, the same coroutine that loads the
  session — into the new `State.repsUnitLabel`, and `withPresentation`/`toPlanSubLabel` are
  pure synchronous functions over that already-resolved immutable value. This matches the
  established State-carried presentation-string discipline exactly: like `trainingNameLabel`,
  `headerMetaLabel` and every `statusLabel` branch, the unit refreshes on the next mapper pass
  (store re-init / reload); `MainActivity` declares `locale` in `configChanges`, so no
  State-carried string refreshes mid-composition today, and none does after this change.
- No production `runBlocking` remains in either path (repo-wide grep: the only production
  `runBlocking` sites are the pre-existing `app:app` startup/runtime ones, untouched); the only
  production `getString(Res…)` calls sit inside true suspend `launch` blocks.

### 19.2 The Noise seam matches §6.2

`Modifier.drawNoiseOrFallback` is again a public `commonMain` implementation (defaults intact,
Android call sites untouched) delegating to `internal expect fun
Modifier.drawPlatformNoiseOrFallback(…)`; the Android actual keeps the API-33 check, feature
flag, AGSL shader and base-colour fallback, the iOS actual keeps the base-colour fallback, and
the shader-only helpers stay `androidMain`. The wrapper's body uses an explicit `this.`
receiver — the measured shape both androidx Modifier lint rules accept
(`ModifierFactoryExtensionFunction` wants the hook to be an extension;
`ModifierFactoryUnreferencedReceiver` cannot see a receiver through an implicit call to an
`expect` callee).

### 19.3 Focused tests added (no static mock of `getString` anywhere)

- `CommonHandlerComposeResourceTest` / `…RuTest` (live-workout, Robolectric): `Action.Common.Init`
  over a WEIGHTLESS session resolves the unit from the REAL catalog — `"reps"` -> `"12 reps"`
  under the default locale, and the shipped `values-ru` value of
  `core_ui_kit_plan_editor_unit_reps` with its sublabel under `qualifiers = "ru"` — into
  `State.repsUnitLabel` and the rendered sublabel. (Two classes: the Robolectric JUnit5
  extension forbids method-level `@Config`.)
- `LiveWorkoutMapperTest`: new WEIGHTLESS case proves the mapper is a pure synchronous
  transformation over the resolved value (the default and the Russian unit as inputs), plus the eight
  existing `toState` fixtures now pass the resolved unit explicitly.
- `ExerciseDeferredDeleteDbTest` (Robolectric + real in-memory Room): the store mock now runs
  the registration coroutine inline, so all four ED11 cases exercise the real
  `getString`-then-enqueue path, and the new case `back is consumed only after the snackbar is
  queued, with the real undo label` pins the ordering (Back recorded with the queue non-empty)
  and the real `"Undo"` label. `PermanentDeleteUndoLabelRuTest` pins the shipped `values-ru`
  value of `core_ui_kit_toast_undo` under `ru`.
- `ClickHandlerTest`: the `mockkStatic("…StringResourcesKt")` stub is deleted; the plain-JVM
  `confirm permanent delete defers` case now pins the sync half — dialog closed, no delete, and
  `Back` NOT consumed synchronously (a scope cancelled before the coroutine starts deletes
  nothing and pops nothing — cancellation cannot turn the deferred delete into an early one).

### 19.4 Record corrections

- §2's source inventory row misread the baseline: the true counts at `0638fc740`/`500b4efb2`
  are **145 `@Test` + 50 `@ParameterizedTest` = 195** annotated host-test functions (the
  earlier 151/45/196 figure counted a KDoc mention); with the §6.4 epoch-concurrency test the
  kit carried 146 + 50 = 196 before this follow-up. Executed-XML case counts (411 → 412) were
  and remain a separate metric and were correct.
- §18.7 previously attributed run 32971371429 to the arc's final head; it tested `811ade13c`.
  The paragraph now records every run against the exact SHA it tested.

### 19.5 Verification (forced flags, detekt separate)

Every Gradle line below ran with `--rerun-tasks --no-build-cache --no-configuration-cache
--full-stacktrace --console=plain` and reports full execution; counts are from the produced XML.

| Gate | Summary line | Counts |
| --- | --- | --- |
| `:feature:live-workout:testDebugUnitTest` | 171 actionable tasks: 171 executed | 22 XML files, 191 cases, 0 failed — three named new tests (the two real-catalog resource classes and the WEIGHTLESS mapper case) |
| `:feature:exercise:testDebugUnitTest` | 189 actionable tasks: 189 executed | 10 XML files, 120 cases, 0 failed — two named new tests (the ordering/real-label DB case and the Russian undo-label case) |
| `:core:ui:kit:testDebugUnitTest` | 67 actionable tasks: 67 executed | 23 XML files, 412 cases, 0 skipped, goldens excluded — unchanged |
| `verifyPaparazziDebug` (root) | 617 actionable tasks: 617 executed | 13 "Visual gate live" lines summing to 456; kit "86 golden test case(s) executed for 86 golden image(s)" |
| `:core:ui:kit:iosSimulatorArm64Test` | 38 actionable tasks: 38 executed | XML `tests="1" skipped="0" failures="0" errors="0"` |
| `assembleDebug` (root) | 1102 actionable tasks: 1102 executed | every consumer compiles |
| `detekt` (separate run) | 57 actionable tasks: 57 executed | green |
| `:core:ui:kit:connectedDebugAndroidTest` `@Smoke` (API-34 emulator) | 137 actionable tasks: 137 executed | "Starting 5 tests … Finished 5 tests", green |

Golden integrity re-proven after the edits: the 86 kit PNG SHA-256s are identical to the
pre-conversion baseline manifest and the repository still holds exactly 456 committed golden
PNGs; the worktree contains only the intended source/test/doc changes — no mutation artifacts
and no generated credentials. `git grep`-level audit: no production `runBlocking` outside the
pre-existing `app:app` runtime files, and no `getString(Res…)` outside suspend `launch` blocks.

### 19.6 CI on this follow-up

Recorded after this correction's CI completes, in a follow-up commit that is explicitly
documentation-only: the run IDs below (once filled in) tested the SHA of the record commit that
carries the corrected code, not the documentation-only commit that writes them down.
