# KMP Phase 7.15 — feature:exercise. Part A: the media seams (PR-M)

**Status:** Part A COMPLETE — implemented by PR #341. Part B SPECIFIED — Sections B0–B18; the PR
that implements it fills Section B19.

**Discovery baseline:** dev @ 63d511dee5829e5c7b1338aa69c9dc56123f055e (2026-10-20, the merge of
#340, Phase 7.14).

**Decisions:** maintainer GO, 2026-10-20, on D1–D6 of the Phase 7.15 discovery, each on its lean
(Section 2).

---

## 0. Authority and stable rules

### 0.1 Authority order

1. live origin/dev at the implementation base;
2. AGENTS.md, CLAUDE.md, documentation/architecture.md, testing.md, ci-cd.md, lint-rules.md;
3. this document;
4. the checked-in source, resources, tests and CI oracles at the implementation base.

Phase 7.14's specification (`kmp-phase-7-14-single-training-feature.md`, its Section 19 included)
and kmp-string-resolver.md are the precedents this part cites.

### 0.2 Stable rules

- The work happens in the main checkout, on branches. No `git worktree` is created for any step:
  not for the spec, the implementation, a proof or a control (maintainer rule, 2026-10-20). Mutations
  go through `documentation/mockups/mutation_harness.py`, which restores the file; anything the
  harness cannot run uses a scratchpad copy restored with `cp` (AGENTS.md).
- Every load-bearing Gradle invocation runs after `./gradlew --stop` with
  `--rerun-tasks --no-build-cache --no-configuration-cache --console=plain` and quotes its
  `N actionable tasks: N executed` line. detekt and tests run as separate invocations.
- Only JUnit XML written after the run's recorded start is evidence. Every gate reports its input
  count; a walk over zero items is a failure, not a pass.
- Commits are signed and GitHub-verified; the only trailer is `Co-Authored-By`; no session identifier
  appears in a commit, the PR or a document. The implementer never merges.
- No suppression of any kind. No golden is recorded and no PNG blob changes. No catalog
  (`strings.xml`), build file or version catalog changes.

## 1. Why

`feature:exercise` is the next shared entry (D1). Every other part of it has a template from Phases
7.12–7.14; its media flow has none. At the baseline that flow is Android in three layers:

- **Composition** (`ui/ExerciseGraph.kt`): three `rememberLauncherForActivityResult` calls
  (`TakePicture` with a remembered temporary `Uri`, `PickVisualMedia` with
  `PickVisualMediaRequest(ImageOnly)`, `RequestPermission`) and
  `Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)` started on `LocalContext.current`.
- **Handler** (`ui/mvi/handler/ClickHandler.kt`): a bare `android.content.Context` injected for
  `ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA)` and
  `context.packageName`, and `ref.value.toUri()` on the temporary capture reference.
- **Store contract**: `android.net.Uri` in `Action.Common.ImagePicked`, `Event.NavigateLaunchCamera`,
  `PendingImage.NewFromUri` and `ImageDisplay.FromUri`.

The handler logic around it is plain Kotlin; only the permission check, the launchers and the
reference type need a seam. In addition, the load reads the image's modification time with
`java.io.File(path).lastModified()` inside `applyLoaded`, which runs in the load's
`updateStateImmediate` lambda in `onSuccess`, on the main immediate dispatcher
(`AppCoroutineScopeImpl` runs `onSuccess` on `eachDispatcher ?: immediateDispatcher`). The value is
Coil's cache key (`"$path?v=$mtime"`): every save rewrites the same `exercise_images/<uuid>.jpg`.

No automated test exercises the launchers, and no identity drives `OnImageSourceSelected` or
`RequestCameraCapture`. On the plain JVM the camera branch cannot run at all:
`ContextCompat.checkSelfPermission` calls `TextUtils.equals` from the `android.jar` stubs before it
reaches `Context.checkPermission` (androidx-main source, read 2026-10-20), and no module sets
`unitTests.isReturnDefaultValues`; `ClickHandlerTest`'s class-level `checkPermission` stub is never
reached.

PR-M adds the seams, makes the classic module consume them, and pins the branch first. Part B then
moves a module whose handlers and Store contract carry no Android type.

## 2. Decisions

Locked by the maintainer on 2026-10-20 (the discovery's D1–D6, each on its lean):

| Id | Decision |
| --- | --- |
| D1 | `feature:exercise` is Phase 7.15's entry. |
| D2 | The media flow goes behind core seams: a camera-permission check in `core:core` and an image-source launcher in `core:ui:kit` (Section 4). The feature drops `Context` and carries `ImageRef` instead of `Uri`. |
| D3 | `ImageStorage.lastModified(path)`, read in the load's action, replaces `File(path).lastModified()`. |
| D4 | Part B: the two Robolectric suites move to commonTest with recording fakes. |
| D5 | Part B: an exercise-only extra check pins `ExerciseFormBasicsTest`. |
| D6 | One specification, two PRs: PR-M (this part) lands D2 and D3 on the classic module with the pinned identities; the move follows as its own PR. |

Choices this part makes inside those decisions:

| Id | Choice | Why |
| --- | --- | --- |
| M1 | The kit launcher takes and reports the platform reference as a `String` (what `ImageRef.value` holds), not `ImageRef`. | Kit's public API exposes no `core:core` type today: kit reads `core:core` only internally (`AppScope` in annotations, `Log`, `DateTimeUtil`), through an `implementation` edge. The image viewer's route already carries the same reference as a `String` (`Screen.ExerciseImage(model: String)`). The feature wraps the string in `ImageRef` in its graph. |
| M2 | `CameraPermission` lives in `core:core` `platform`, contributed like `AndroidTempFileProvider`. | The check is a platform query read on a click path, beside `TempFileProvider` and `PlatformInfoProvider`; `@ContributesBinding(AppScope)` on a public `@Inject` class is the existing shape. |
| M3 | `ImageStorage.lastModified` is `suspend` and runs on its IO dispatcher. | Every `ImageStorage` member is `suspend` and does its I/O on `ioDispatcher`. |
| M4 | The Android launcher remembers its three contracts. | `rememberLauncherForActivityResult` keys its `DisposableEffect` on `(activityResultRegistry, key, contract)` (androidx-main, read 2026-10-20; the 1.13.0 sources jar was not reachable), so a contract built inline in a composable re-registers on every recomposition, which the baseline graph does. Results are routed the same either way. |
| M5 | The four image-source identities land first, against today's code, with MockK static stubs for `ContextCompat.checkSelfPermission` and `Uri.parse`; commit 4 drops the stubs. | The branch is pinned before the seam changes it, so the same names prove the seam preserved it. |
| M6 | The exercise graph's `appContext: Context` accessor becomes `cameraPermission: CameraPermission`, and its identity is replaced. | After PR-M no exercise node injects `Context`; the accessor's only reader is `ExerciseExtensionIdentityTest`. |
| M7 | Correction to the discovery: the launcher wiring is automatable with a recording `ActivityResultRegistry`. Only the system camera and photo-picker apps are not, and the maintainer checks those on a device (Section 12.2). | Section 5.2. |

## 3. Bounded exit claim

The implementation may prove only this:

- `core:core` declares `CameraPermission` (commonMain) and contributes `AndroidCameraPermission`
  (androidMain) to `AppScope`; on the Android host it reports the granted and the denied camera
  permission;
- `ImageStorage.lastModified` returns a stored file's modification time and 0 for an absent file;
  every implementer compiles;
- `core:ui:kit` declares `ImageSourceLauncher` and `rememberImageSourceLauncher` (commonMain) with an
  Android actual and an inert iOS actual; kit compiles for Android, iosSimulatorArm64 and iosArm64;
- on the Android host, under a recording `ActivityResultRegistry`, the Android actual launches
  `TakePicture` with the target's URI, `PickVisualMedia` with `ImageOnly`, `RequestPermission` with
  `CAMERA`, and starts the app-details settings intent, and routes each result to the right callback;
- `feature:exercise` imports no `android.*` type and no `java.io.File`; its four image-source
  identities pass before and after the seam with the same names; every existing identity keeps its
  name and assertion except those Section 5.3 lists;
- the exercise graph inherits the app-scoped `CameraPermission`;
- every PNG blob is unchanged, and the device suites of Section 7.4 pass.

Not claimed: the system camera and photo-picker apps (Section 12.2); any iOS behaviour (the iOS
actual is compiled, never executed); the move (Part B).

## 4. Contract

Normative signatures and bodies; KDoc wording is free unless quoted.

### 4.1 core:core

`core/core/src/commonMain/kotlin/io/github/stslex/workeeper/core/core/platform/CameraPermission.kt`:

    package io.github.stslex.workeeper.core.core.platform

    /** Whether the app may use the camera right now. Synchronous: a click path reads it. */
    interface CameraPermission {

        fun isGranted(): Boolean
    }

`core/core/src/androidMain/kotlin/io/github/stslex/workeeper/core/core/platform/AndroidCameraPermission.kt`:

    @ContributesBinding(AppScope::class)
    @SingleIn(AppScope::class)
    @Inject
    class AndroidCameraPermission(
        private val context: Context,
    ) : CameraPermission {

        override fun isGranted(): Boolean = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.CAMERA,
        ) == PackageManager.PERMISSION_GRANTED
    }

with `AppScope` from `io.github.stslex.workeeper.core.core.di` (`ContributesBindingScopeRule`), and
a GUARD comment: the class stays public, because an internal contribution does not aggregate into
`AppGraph` (the guard `AndroidTempFileProvider` and `ComposeStringResolver` carry). The expression is
the baseline `ClickHandler.hasCameraPermission()` unchanged.

`ImageStorage` (commonMain) gains, after `deleteImage`:

    /** The file's last-modified time in epoch millis; 0 when [path] names no file. */
    suspend fun lastModified(path: String): Long

`ImageStorageImpl` (androidMain) implements it as
`withContext(ioDispatcher) { File(path).lastModified() }`, which is the baseline expression and
returns 0 for an absent or blank path.

The two other implementers follow in the same commit:

- `FakeImageStorage` (`core/ui/test-utils`): returns `0L`, because the synthetic paths it hands out
  name no file (the baseline `File(path).lastModified()` returns 0 for them), and counts the call in
  a new `lastModifiedCount`, which `Snapshot` and `reset()` carry like the other counters;
- the fail-fast object in `ExerciseRepositoryDuplicateNameDeviceTest`:
  `error("saveItem must not reach ImageStorage.lastModified")`, as its other members do.

The relaxed MockK instances of `ImageStorage` need no change.

### 4.2 core:ui:kit

`core/ui/kit/src/commonMain/kotlin/io/github/stslex/workeeper/core/ui/kit/media/ImageSourceLauncher.kt`:

    package io.github.stslex.workeeper.core.ui.kit.media

    /**
     * The platform's camera, photo picker, camera-permission request and app settings. A reference is
     * the platform's string form of the image's location (`ImageRef.value`; a `content://` URI on
     * Android).
     */
    @Stable
    interface ImageSourceLauncher {

        /** Opens the camera, which writes the photo to [targetRef]. */
        fun launchCamera(targetRef: String)

        /** Opens the photo picker, images only. */
        fun launchGallery()

        fun requestCameraPermission()

        /** Opens this app's page in the system settings. */
        fun openAppSettings()
    }

    /**
     * [onImagePicked] receives the captured or picked reference; [onImagePickCancelled] fires when
     * the camera or the picker returns without one; [onCameraPermissionResult] reports the request's
     * answer.
     */
    @Composable
    expect fun rememberImageSourceLauncher(
        onImagePicked: (ref: String) -> Unit,
        onImagePickCancelled: () -> Unit,
        onCameraPermissionResult: (granted: Boolean) -> Unit,
    ): ImageSourceLauncher

`core/ui/kit/src/androidMain/kotlin/io/github/stslex/workeeper/core/ui/kit/media/ImageSourceLauncher.android.kt`:

    @Composable
    actual fun rememberImageSourceLauncher(
        onImagePicked: (ref: String) -> Unit,
        onImagePickCancelled: () -> Unit,
        onCameraPermissionResult: (granted: Boolean) -> Unit,
    ): ImageSourceLauncher {
        val context = LocalContext.current
        // The camera's result carries no URI, so the target it launched with is kept here.
        // Rotation between launch and result is out of scope, as it was in the graph.
        val pendingCameraTarget = remember { mutableStateOf<String?>(null) }
        // GUARD: remembered — rememberLauncherForActivityResult keys its registration on the
        // contract instance, so a contract built per recomposition re-registers every time.
        val takePicture = remember { ActivityResultContracts.TakePicture() }
        val pickVisualMedia = remember { ActivityResultContracts.PickVisualMedia() }
        val requestPermission = remember { ActivityResultContracts.RequestPermission() }
        val camera = rememberLauncherForActivityResult(takePicture) { success ->
            val target = pendingCameraTarget.value
            if (success && target != null) onImagePicked(target) else onImagePickCancelled()
            pendingCameraTarget.value = null
        }
        val gallery = rememberLauncherForActivityResult(pickVisualMedia) { uri ->
            if (uri != null) onImagePicked(uri.toString()) else onImagePickCancelled()
        }
        val permission = rememberLauncherForActivityResult(requestPermission) { granted ->
            onCameraPermissionResult(granted)
        }
        return remember(context, camera, gallery, permission) {
            object : ImageSourceLauncher {
                override fun launchCamera(targetRef: String) {
                    pendingCameraTarget.value = targetRef
                    camera.launch(targetRef.toUri())
                }

                override fun launchGallery() {
                    gallery.launch(
                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
                    )
                }

                override fun requestCameraPermission() {
                    permission.launch(Manifest.permission.CAMERA)
                }

                override fun openAppSettings() {
                    val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                        data = "package:${context.packageName}".toUri()
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    context.startActivity(intent)
                }
            }
        }
    }

The callbacks, the contracts, the request, the permission string and the intent are the baseline
graph's (`ui/ExerciseGraph.kt` lines 76–161), with three differences: the pending target is a
`String` instead of a `Uri` (the same string the handler converted with `toUri()`), the package name
comes from `LocalContext` instead of the handler's application `Context` (the same package), and the
contracts are remembered (M4).

`core/ui/kit/src/iosMain/kotlin/io/github/stslex/workeeper/core/ui/kit/media/ImageSourceLauncher.ios.kt`:

    /**
     * Inert: no iOS host exists yet, and a throwaway iosApp stays forbidden. The permanent iOS host
     * implements the camera, the photo picker and the settings link here. Nothing calls back, so a
     * Store stays in the state it wrote before asking.
     */
    @Composable
    actual fun rememberImageSourceLauncher(
        onImagePicked: (ref: String) -> Unit,
        onImagePickCancelled: () -> Unit,
        onCameraPermissionResult: (granted: Boolean) -> Unit,
    ): ImageSourceLauncher = InertImageSourceLauncher

    private object InertImageSourceLauncher : ImageSourceLauncher {
        override fun launchCamera(targetRef: String) = Unit
        override fun launchGallery() = Unit
        override fun requestCameraPermission() = Unit
        override fun openAppSettings() = Unit
    }

The iOS precedent for an inert actual with unused parameters is `PlatformWindowChrome` and
`drawPlatformNoiseOrFallback` (kit iosMain). No build file changes: kit's androidMain already has
`activity-compose` and `core-ktx`, and its androidHostTest already has Robolectric,
`ui-test-junit4` and `ui-test-manifest`.

### 4.3 app

`app/app/.../di/AppGraph.kt` gains, directly after `stringResolver`:

    /**
     * The camera-permission check (kmp-phase-7-15-exercise-feature.md Part A). Its only reader here
     * is the graph identity test, `ExerciseExtensionIdentityTest`.
     */
    val cameraPermission: CameraPermission

and its import. `stringResolver` is the precedent for an accessor whose only reader is a graph test
(architecture.md: an accessor stays when a `:app` identity test reads it).

### 4.4 feature:exercise (still classic)

Store contract (`ui/mvi/store/ExerciseStore.kt`, `ui/mvi/model/`):

| Baseline | After PR-M |
| --- | --- |
| `Action.Common.ImagePicked(val uri: Uri)` | `ImagePicked(val ref: ImageRef)` |
| `Event.NavigateLaunchCamera(val tempUri: Uri)` | `NavigateLaunchCamera(val target: ImageRef)` |
| `Event.NavigateOpenAppSettings(val packageName: String)` | `data object NavigateOpenAppSettings` |
| `PendingImage.NewFromUri(val uri: Uri)` | `NewFromUri(val ref: ImageRef)` |
| `ImageDisplay.FromUri(val uri: Uri)` | `FromUri(val ref: ImageRef)` |

The class names stay, so every identity that names them keeps its name; `ImageRef`'s KDoc already
says it holds an Android `Uri` string. `State.effectiveImageDisplay` maps `NewFromUri(ref)` to
`FromUri(ref)`.

`ClickHandler`:

- the constructor's `context: Context` becomes `cameraPermission: CameraPermission`, and the comment
  above it goes;
- the camera branch reads `cameraPermission.isGranted()`; `hasCameraPermission()` and its three
  Android imports go;
- `launchCameraCapture` sends `Event.NavigateLaunchCamera(ref)` (no `toUri()`);
- `processPermissionDeniedSettingsClick` sends `Event.NavigateOpenAppSettings`;
- `commitPendingImage` passes `pending.ref` to `interactor.saveImage` (baseline:
  `ImageRef(pending.uri.toString())`, the same string);
- `processImageThumbnailClick` maps `FromUri` to `display.ref.value` (baseline:
  `display.uri.toString()`, the same string).

`CommonHandler`:

- `processImagePicked` writes `PendingImage.NewFromUri(action.ref)`;
- the load's action awaits the exercise, then reads
  `exercise?.imagePath?.let { interactor.getImageLastModified(it) } ?: 0L` and carries it in
  `LoadResult.imageLastModified`; the comment about Coil's `?v=<mtime>` key moves with it;
- `applyLoaded` reads `result.imageLastModified`; `import java.io.File` goes. Nothing else in
  `applyLoaded` changes; Part B splits it.

`ExerciseInteractor` gains, after `deleteImageFile`:

    /** The stored image's modification time, Coil's cache key; 0 when the file is absent. */
    suspend fun getImageLastModified(path: String): Long

and `ExerciseInteractorImpl` forwards it to `imageStorage.lastModified(path)`. The interface goes
from 20 to 21 abstract members.

`di/ExerciseGraph.kt`: `val appContext: Context` becomes `val cameraPermission: CameraPermission`;
the KDoc's "three accessors below" stays true; `android.content.Context` goes.

`ui/ExerciseGraph.kt`: the three launchers, the pending-URI state, `LocalContext` and the intent are
replaced by

    val imageSources = rememberImageSourceLauncher(
        onImagePicked = { ref -> processor.consume(Action.Common.ImagePicked(ImageRef(ref))) },
        onImagePickCancelled = { processor.consume(Action.Common.ImagePickCancelled) },
        onCameraPermissionResult = { granted ->
            processor.consume(
                if (granted) Action.Click.RequestCameraCapture else Action.Click.OnCameraPermissionDenied,
            )
        },
    )

and the four events become `imageSources.launchCamera(event.target.value)`,
`imageSources.launchGallery()`, `imageSources.requestCameraPermission()` and
`imageSources.openAppSettings()`. The activity `BackHandler` stays until Part B.

`ui/components/ExerciseDescriptionBlock.kt`: `is ImageDisplay.FromUri -> imageDisplay.ref.value`.
Coil receives the reference as a `String`, as the image viewer already does in production for the
same picked or captured reference (`Screen.ExerciseImage(model = display.uri.toString())`).
`ImageRequest.Builder(LocalContext.current)` stays until Part B.

At exit `feature/exercise/src/main` imports no `android.*` (10 at the baseline), no `java.*` (1),
no `androidx.core.*` (3) and no `androidx.activity.result.*` / `rememberLauncherForActivityResult`
(3). What remains for Part B: `LocalContext` in `di/ExerciseFeature.kt` (`appDeps`) and in
`ExerciseDescriptionBlock.kt` (Coil), the activity `BackHandler`, `androidx.compose.ui.res.*`,
`@VisibleForTesting` and the two `System.currentTimeMillis()`.

### 4.5 Behaviour

| Path | Baseline | After PR-M |
| --- | --- | --- |
| Camera, permission granted | `ContextCompat` check, temp ref, `NavigateLaunchCamera(uri)`, graph stores the `Uri`, launches `TakePicture` | the same check through `CameraPermission`, the same temp ref, `NavigateLaunchCamera(ref)`, the launcher stores the string, launches `TakePicture` with `toUri()` of it |
| Camera result | success with a pending URI → `ImagePicked(uri)`, else `ImagePickCancelled` | the same, with the reference string |
| Camera, no permission | `NavigateRequestCameraPermission` → `RequestPermission(CAMERA)` → `RequestCameraCapture` or `OnCameraPermissionDenied` | the same |
| Gallery | `PickVisualMedia(ImageOnly)` → `ImagePicked(uri)` or `ImagePickCancelled` | the same, with `uri.toString()` |
| Settings | intent with `package:<application packageName>` on the graph's `LocalContext` | the same intent with `LocalContext`'s package name (the same package) |
| Save | `saveImage(ImageRef(uri.toString()), uuid)` | `saveImage(ref, uuid)` with the same string |
| Load | `File(path).lastModified()` in the State lambda, main immediate | `ImageStorage.lastModified(path)` in the action, on IO |

## 5. Tests

### 5.1 core:core (Android host)

- `core/core/src/androidHostTest/.../core/core/platform/AndroidCameraPermissionTest.kt`, JUnit 5,
  `@ExtendWith(RobolectricExtension::class)`, `@Config(sdk = [33])`, Robolectric's
  `shadowOf(application)`:
  - `` `isGranted is true once the camera permission is granted` `` (`grantPermissions(CAMERA)`);
  - `` `isGranted is false while the camera permission is denied` `` (`denyPermissions(CAMERA)`).
- `ImageStorageImplTest` gains two, in its style:
  - `` `lastModified returns the stored file's modification time` ``: a file under the root dir
    with `setLastModified(1_700_000_000_000L)`; the result equals `target.lastModified()`, which is
    positive (the comparison absorbs the host file system's granularity);
  - `` `lastModified returns 0 when the file is absent` ``.

### 5.2 core:ui:kit (Android host)

`core/ui/kit/src/androidHostTest/.../core/ui/kit/media/ImageSourceLauncherTest.kt`:
`@ExtendWith(RobolectricExtension::class)`, `@Config(sdk = [33])`,
`@OptIn(ExperimentalTestApi::class)`, exactly one method, `launchesEachContractAndRoutesItsResult`,
and one `runComposeUiTest` composition (kit's GUARD in `AccessibilitySemanticsTest`: a second
composition in the same Robolectric sandbox hangs).

The composition provides a recording `ActivityResultRegistry` through
`LocalActivityResultRegistryOwner` (its `onLaunch` records request code, contract and input) and a
`ContextWrapper` through `LocalContext` whose `startActivity` records the intent. It captures the
launcher and the three callbacks' calls. The steps run in `runOnIdle`, results go back through
`registry.dispatchResult(requestCode, result)`, and the method asserts every row with `assertAll`:

| Step | Launch recorded | Result dispatched | Expected |
| --- | --- | --- | --- |
| `launchCamera("content://test/capture-1.jpg")` | `TakePicture`, input `Uri.parse("content://test/capture-1.jpg")` | `true` | picked `["content://test/capture-1.jpg"]` |
| `launchCamera("content://test/capture-2.jpg")` | `TakePicture` | `false` | one cancel; picked unchanged |
| `launchGallery()` | `PickVisualMedia`, `mediaType == ImageOnly` | `Uri.parse("content://picker/7")` | picked gains `"content://picker/7"` |
| `launchGallery()` | `PickVisualMedia` | `null` | a second cancel |
| `requestCameraPermission()` | `RequestPermission`, input `Manifest.permission.CAMERA` | `true` | permission results `[true]` |
| `requestCameraPermission()` | `RequestPermission` | `false` | `[true, false]` |
| `openAppSettings()` | none | — | one started intent: action `Settings.ACTION_APPLICATION_DETAILS_SETTINGS`, data `Uri.parse("package:" + packageName)`, `FLAG_ACTIVITY_NEW_TASK` set |

and six launches in all, each `dispatchResult` returning `true`. `dispatchResult` calls a callback
only for a launched key (androidx `ActivityResultRegistry.dispatchResult`, read 2026-10-20), so
every row needs its launch.

### 5.3 feature:exercise (JVM, JUnit 5 and MockK, still classic)

Commit 1 adds four identities to `ClickHandlerTest`, against the baseline code:

| Name | Drives | Asserts |
| --- | --- | --- |
| `camera with the permission creates a capture target and launches the camera` | `OnImageSourceSelected(Camera)` from `ImageSourcePicker`, permission granted, launch run synchronously | dialog `Hidden`; events `[Haptic(ContextClick), NavigateLaunchCamera(<the target>)]`; `createTempCaptureRef` once |
| `camera without the permission asks for it and creates no capture target` | the same, permission denied | dialog `Hidden`; events `[Haptic(ContextClick), NavigateRequestCameraPermission]`; `createTempCaptureRef` never |
| `gallery launches the photo picker and reads no permission` | `OnImageSourceSelected(Gallery)` | dialog `Hidden`; events `[Haptic(ContextClick), NavigateLaunchGallery]`; the permission check never called; `createTempCaptureRef` never |
| `a granted permission launches the camera on a fresh capture target` | `RequestCameraCapture`, launch run synchronously | events `[NavigateLaunchCamera(<the target>)]`; `createTempCaptureRef` once |

In commit 1 the permission is `mockkStatic(ContextCompat::class)` with
`ContextCompat.checkSelfPermission(any(), Manifest.permission.CAMERA)` answering
`PERMISSION_GRANTED` or `PERMISSION_DENIED`, and `toUri()` is `mockkStatic(Uri::class)` with
`Uri.parse("content://capture/1.jpg")` answering one relaxed `Uri` mock, which the event must carry;
both static mocks are undone in a `finally` (or `@AfterEach`) so no sibling identity sees them.
`createTempCaptureRef` answers `ImageRef("content://capture/1.jpg")`. Names avoid every character
Kotlin/Native rejects in a test name (7.13 Section 4.3), because Part B moves them to commonTest.

Commit 4 changes, and nothing else:

- `ClickHandlerTest`: the class-level `Context` mock (and its dead `checkPermission` and
  `packageName` stubs) becomes a `CameraPermission` mock answering `true`; the four identities above
  answer through it, drop both static mocks, assert `NavigateLaunchCamera(ImageRef("content://capture/1.jpg"))`,
  and the gallery row verifies `isGranted()` was never called. Their names stay.
  `OnPermissionDeniedSettingsClick emits NavigateOpenAppSettings` keeps its assertion
  (`is Event.NavigateOpenAppSettings`, which now matches the object).
- `CommonHandlerTest`: `ImagePicked sets pendingImage to NewFromUri and hides the source dialog`
  uses `ImageRef("content://picked/1")` instead of a relaxed `Uri` mock; and two new identities:
  - `the load keys the image by its file's modification time`: `getExercise` answers an exercise
    with `imagePath = "/files/bench.jpg"` and `getImageLastModified("/files/bench.jpg")` answers
    `1234L`; after `Init`, `imageLastModified == 1234L` and `effectiveImageDisplay ==
    ImageDisplay.FromPath("/files/bench.jpg", 1234L)`;
  - `an exercise without an image reads no modification time`: `imagePath = null`; after `Init`,
    `imageLastModified == 0L` and `getImageLastModified` was never called.
- `ExerciseDirtyStateTest`: `PendingImage.NewFromUri(ImageRef("content://picked/1"))` instead of a
  relaxed `Uri` mock; its MockK import goes if nothing else uses it.
- `ExerciseInteractorImplTest`: `getImageLastModified forwards to the image storage`
  (`imageStorage.lastModified("/files/x.jpg")` answers `1234L`; the result is `1234L`; one call).
- `ExerciseDeferredDeleteDbTest` and `PermanentDeleteUndoLabelRuTest`: the `ClickHandler`
  constructor's `context = mockk<Context>(relaxed = true)` becomes
  `cameraPermission = mockk(relaxed = true)`; nothing else.

Identity counts (non-golden `testDebugUnitTest` cases, measured at the baseline by Phase 2):
120 → 124 (commit 1) → 127 (commit 4), in 10 suites throughout. The 24 golden methods (48 cases)
do not change.

### 5.4 app

`ExerciseExtensionIdentityTest`: `` `the bare app Context is inherited from the parent's bound
instance` `` is replaced by `` `the camera permission is inherited from the parent graph` ``:
`assertSame(appGraph.cameraPermission, extension.cameraPermission, …)` on one graph. Six identities
before and after; the other five are unchanged.

### 5.5 Device

No device test is added or changed. Section 7.4 runs the module's two classes and the app journeys
that reach the screen.

## 6. Known-negative controls

Each: fresh GREEN → one mutation → the named verdict quoted from fresh output → byte-exact
restoration → `git status --porcelain --untracked-files=no` empty → fresh GREEN. Mutations go through
`documentation/mockups/mutation_harness.py` in one-shot mode, with
`--no-configuration-cache --console=plain` inside `--task` (the harness adds
`--rerun-tasks --no-build-cache`). A compile-invalid mutation is a failed control, never a RED.

| Id | On | Mutation | Task | Required verdict |
| --- | --- | --- | --- | --- |
| K0 | commit 1 | `processImageSourceSelected`: `if (hasCameraPermission())` → `if (!hasCameraPermission())` | `:feature:exercise:testDebugUnitTest --tests *ClickHandlerTest*` | RED: the two camera identities of Section 5.3, and only them |
| K1 | head | `AndroidCameraPermission.isGranted` returns `true` | `:core:core:testAndroidHostTest --tests *AndroidCameraPermissionTest*` | RED: the denied identity only |
| K2 | head | `ImageStorageImpl.lastModified` returns `0L` | `:core:core:testAndroidHostTest --tests *ImageStorageImplTest*` | RED: the stored-file identity only |
| K3 | head | the camera callback's `success && target != null` → `target != null` | `:core:ui:kit:testAndroidHostTest --tests *ImageSourceLauncherTest*` | RED (the failed-capture row) |
| K4 | head | `launchCamera` no longer writes `pendingCameraTarget` | the same | RED (the capture row reports a cancel) |
| K5 | head | `PickVisualMedia.ImageOnly` → `PickVisualMedia.ImageAndVideo` | the same | RED (the gallery row) |
| K6 | head | `Manifest.permission.CAMERA` → `Manifest.permission.READ_MEDIA_IMAGES` in `requestCameraPermission` | the same | RED (the permission row) |
| K7 | head | the settings intent's `data = …` line removed | the same | RED (the settings row) |
| K8 | head | the camera branch reads `!cameraPermission.isGranted()` | `:feature:exercise:testDebugUnitTest --tests *ClickHandlerTest*` | RED: the two camera identities, and only them |
| K9 | head | the gallery branch becomes `if (cameraPermission.isGranted()) sendEvent(NavigateLaunchGallery) else sendEvent(NavigateLaunchGallery)` | the same | RED: the gallery identity only |
| K10 | head | the load carries `imageLastModified = 0L` instead of the read | `:feature:exercise:testDebugUnitTest --tests *CommonHandlerTest*` | RED: `the load keys the image by its file's modification time` only |
| K11 | head | `ExerciseInteractorImpl.getImageLastModified` returns `0L` | `:feature:exercise:testDebugUnitTest --tests *ExerciseInteractorImplTest*` | RED: the forwarding identity only |
| K12 | head | `@SingleIn(AppScope::class)` removed from `AndroidCameraPermission` | `:app:app:testDebugUnitTest --tests *ExerciseExtensionIdentityTest*` | RED: `the camera permission is inherited from the parent graph` only |

The graph's mapping of the permission result (`RequestCameraCapture` / `OnCameraPermissionDenied`)
has no automated reader; it is the maintainer's check (Section 12.2), as at the baseline.

## 7. Gates

### 7.1 Entry

- Base = live origin/dev after the spec commit; it contains `63d511de`. The latest
  `Android CI/CD - Unified Build and Tests` push run on dev is green (record its id); if it is red,
  STOP.
- No Section 9 path changed since `63d511de`, except this document landing.
- Baselines, stable flags, fresh XML: `:feature:exercise:testDebugUnitTest` (10 suites, 120 cases),
  `:feature:exercise:verifyPaparazziDebug` (13 suites, 168 cases,
  `Visual gate live: 48 golden test case(s) executed for 48 golden image(s).`),
  `:core:core:testAndroidHostTest`, `:core:ui:kit:testAndroidHostTest` and
  `:app:app:testDebugUnitTest` (record each case count); the fourteen-module Native command from
  `android_build_unified.yml` with `--continue`, then `python3 .github/scripts/assert_kmp_ios_smoke.py`;
  `python3 .github/scripts/assert_kmp_ui_source_topology.py` (712 lines, exit 0, empty stderr; keep
  the output as BASE_GATE_OUTPUT). The PNG blob set of `feature/exercise/src/test/snapshots`: 48
  files, 48 distinct blobs, and
  `git ls-files -s feature/exercise/src/test/snapshots | awk '{print $2}' | sort | shasum -a 256`
  = `0e4ba8746577ab0198e29fc09e58f7fd145d41dbf6a6cc01489dfcd09b5a6353`, the same at the head.

### 7.2 Per commit

After commit 1:

    ./gradlew :feature:exercise:testDebugUnitTest     # 124 cases; the four names of 5.3 in fresh XML

then K0.

After commit 2:

    ./gradlew :core:core:testAndroidHostTest          # baseline + 4; the four names
    ./gradlew :core:core:assembleDebug                # Android and both iOS klibs
    ./gradlew :core:ui:test-utils:assembleDebug :core:data:exercise:assembleAndroidDeviceTest
    ./gradlew :app:app:testDebugUnitTest              # = baseline (the contribution aggregates; no accessor yet)

After commit 3:

    ./gradlew :core:ui:kit:testAndroidHostTest        # baseline + 1; launchesEachContractAndRoutesItsResult
    ./gradlew :core:ui:kit:assembleDebug              # Android and both iOS klibs
    ./gradlew <fourteen-module iosSimulatorArm64Test> --continue
    python3 .github/scripts/assert_kmp_ios_smoke.py   # = baseline: kit verifies its 2 tuples
    ./gradlew <fourteen-module linkDebugTestIosArm64> --continue   # 14/14 test.kexe

After commit 4:

    ./gradlew :feature:exercise:testDebugUnitTest     # 127 cases; Section 5.3's names
    ./gradlew :feature:exercise:verifyPaparazziDebug  # 175 cases; the 48-golden line; porcelain empty
    ./gradlew :app:app:testDebugUnitTest              # = baseline; ExerciseExtensionIdentityTest 6/6 by name
    ./gradlew :feature:exercise:assembleDebug :app:app:assembleDebug

and the source census of Section 4.4's exit sentence, with its counts.

detekt runs alone after each commit.

### 7.3 Repository gates, each in its own invocation

    ./gradlew assembleDebug lintDebug testDebugUnitTest --continue
    ./gradlew detekt --continue
    ./gradlew assembleDebugAndroidTest
    ./gradlew verifyPaparazziDebug
    ./gradlew :lint-rules:test
    python3 .github/scripts/assert_kmp_ui_source_topology.py   # byte-identical to BASE_GATE_OUTPUT
    python3 documentation/personal_data_gate.py -v

### 7.4 Device gates

On one API-34 emulator with an explicit serial, animations off, each with fresh XML:

- `:feature:exercise:connectedDebugAndroidTest`: 5 cases (`ExerciseScreenTest` 3,
  `ExerciseFormBasicsTest` 2);
- `:app:app:connectedDebugAndroidTest` with
  `-Pandroid.testInstrumentationRunnerArguments.class=` the comma-separated
  `RouteReachabilityTest`, `StoreRetentionTest` and `BackStackStateRestorationTest`
  (`io.github.stslex.workeeper.app.` each): 15 + 3 + 4 cases;
- the canonical Smoke and Regression suites of testing.md.

### 7.4a Native

The fourteen-module Native command and the device link run after commit 3 (Section 7.2) and again
on the head. Kit's oracle tuples do not change: PR-M adds no iosTest.

### 7.5 Diff

Against the PR's base (the spec commit): after commit 4, exactly the 30 code paths of Section 9;
after commit 5, exactly its 34 paths. Never a `.png`, `strings.xml`, `*.gradle.kts` or
`gradle/libs.versions.toml` path.

## 8. Out of scope

- Everything of Part B: the move, `StringResolver`, CMP resources, the shared gate entry, the
  copy and State-lambda work, the `BackHandler` and Coil-context changes, commonTest.
- An iOS implementation of the launcher or of `CameraPermission` (the permanent iOS host).
- Any change to `ImageStorage`'s other members, to `ImageRef`, or to the image viewer.
- The graph's `Event.ShowImageError` mapping (tech-debt's UI Mapping Boundary row stays).
- The historical specs that describe the baseline flow (`feature-specs/exercise-image.md`), the
  manual scenarios (`test-scenarios/exercise.md`, whose behaviour holds), the skills and
  `graphify-out/`.

## 9. Exact change boundary — 34 paths

New (7):

    core/core/src/commonMain/kotlin/io/github/stslex/workeeper/core/core/platform/CameraPermission.kt
    core/core/src/androidMain/kotlin/io/github/stslex/workeeper/core/core/platform/AndroidCameraPermission.kt
    core/core/src/androidHostTest/kotlin/io/github/stslex/workeeper/core/core/platform/AndroidCameraPermissionTest.kt
    core/ui/kit/src/commonMain/kotlin/io/github/stslex/workeeper/core/ui/kit/media/ImageSourceLauncher.kt
    core/ui/kit/src/androidMain/kotlin/io/github/stslex/workeeper/core/ui/kit/media/ImageSourceLauncher.android.kt
    core/ui/kit/src/iosMain/kotlin/io/github/stslex/workeeper/core/ui/kit/media/ImageSourceLauncher.ios.kt
    core/ui/kit/src/androidHostTest/kotlin/io/github/stslex/workeeper/core/ui/kit/media/ImageSourceLauncherTest.kt

Modified, core and app (7):

    core/core/src/commonMain/kotlin/io/github/stslex/workeeper/core/core/images/ImageStorage.kt
    core/core/src/androidMain/kotlin/io/github/stslex/workeeper/core/core/images/ImageStorageImpl.kt
    core/core/src/androidHostTest/kotlin/io/github/stslex/workeeper/core/core/images/ImageStorageImplTest.kt
    core/ui/test-utils/src/main/kotlin/io/github/stslex/workeeper/core/ui/test/fakes/FakeImageStorage.kt
    core/data/exercise/src/androidDeviceTest/kotlin/io/github/stslex/workeeper/core/data/exercise/exercise/ExerciseRepositoryDuplicateNameDeviceTest.kt
    app/app/src/main/java/io/github/stslex/workeeper/di/AppGraph.kt
    app/app/src/test/kotlin/io/github/stslex/workeeper/di/ExerciseExtensionIdentityTest.kt

Modified, feature/exercise (16; `…` = `kotlin/io/github/stslex/workeeper/feature/exercise`):

    feature/exercise/src/main/…/di/ExerciseGraph.kt
    feature/exercise/src/main/…/domain/ExerciseInteractor.kt
    feature/exercise/src/main/…/domain/ExerciseInteractorImpl.kt
    feature/exercise/src/main/…/ui/ExerciseGraph.kt
    feature/exercise/src/main/…/ui/components/ExerciseDescriptionBlock.kt
    feature/exercise/src/main/…/ui/mvi/handler/ClickHandler.kt
    feature/exercise/src/main/…/ui/mvi/handler/CommonHandler.kt
    feature/exercise/src/main/…/ui/mvi/model/ImageDisplay.kt
    feature/exercise/src/main/…/ui/mvi/model/PendingImage.kt
    feature/exercise/src/main/…/ui/mvi/store/ExerciseStore.kt
    feature/exercise/src/test/…/domain/ExerciseInteractorImplTest.kt
    feature/exercise/src/test/…/ui/mvi/handler/ClickHandlerTest.kt
    feature/exercise/src/test/…/ui/mvi/handler/CommonHandlerTest.kt
    feature/exercise/src/test/…/ui/mvi/handler/ExerciseDeferredDeleteDbTest.kt
    feature/exercise/src/test/…/ui/mvi/handler/PermanentDeleteUndoLabelRuTest.kt
    feature/exercise/src/test/…/ui/mvi/store/ExerciseDirtyStateTest.kt

No other file under `feature/exercise` changes.

Documentation (4, commit 5):

    documentation/architecture.md
    documentation/testing.md
    documentation/tech-debt.md
    documentation/feature-specs/kmp-phase-7-15-exercise-feature.md   (Status and Section 14 only)

7 + 7 + 16 = 30 code paths, and 34 with the documentation.

What each document gains:

- **architecture.md** — the `core/core` bullet names `CameraPermission` (commonMain) and
  `AndroidCameraPermission` (androidMain, contributed); the `core/ui/kit` bullet names
  `rememberImageSourceLauncher`: camera, photo picker, camera-permission request and app settings
  behind one platform seam, inert on iOS until the iOS host.
- **testing.md** — "`app:app` graph and runtime host tests" gains a bullet: `AppGraph.cameraPermission`
  has no production reader; `ExerciseExtensionIdentityTest` reads it to prove the exercise
  extension inherits the app-scoped check. "Deliberate coverage boundaries" gains "Image sources":
  the kit host test drives the Android actual under a recording `ActivityResultRegistry`; the
  system camera and picker apps and the graph's permission-result mapping are the maintainer's
  device check; the iOS actual is compiled, never executed.
- **tech-debt.md** — "Shared (KMP) feature entries — follow-ups" gains one 🟢 row:
  `rememberImageSourceLauncher` and `CameraPermission` have no iOS implementation (the launcher is
  inert, the check has no binding); trigger: the permanent iOS host.

## 10. STOP conditions

Stop, report what was measured, and widen nothing, if:

- the base lacks `63d511de`, the latest dev push run is red, or a Section 9 path drifted;
- a Phase 2 baseline differs from Section 7.1;
- commit 1's identities cannot run on the plain JVM with the two static mocks (do not add
  Robolectric to `ClickHandlerTest`, set `isReturnDefaultValues`, or move them);
- the kit host test hangs, or Robolectric cannot supply the composition (do not split it into a
  second composition, change the runner or add configuration);
- `ActivityResultRegistry`, `LocalActivityResultRegistryOwner` or `ActivityOptionsCompat` is missing
  from kit's androidHostTest classpath (a build-file change is a STOP-and-amend);
- Metro rejects the contribution, or `AppGraph` needs more than the accessor;
- any PNG blob changes, or `verifyPaparazziDebug` executes fewer goldens than images;
- detekt or lint reports anything, or any suppression becomes necessary;
- a control of Section 6 does not produce its required verdict;
- a gate needs a build-file, catalog or version change;
- a device suite fails in a way the baseline does not.

## 11. Signed, bisect-green commit plan

1. **test(exercise): pin the image-source branch before its seam** — `ClickHandlerTest` only
   (Section 5.3's four identities on the baseline code).
2. **feat(core): camera permission and image modification time seams** — the three `platform`
   files, `ImageStorage`, `ImageStorageImpl`, `ImageStorageImplTest`, `FakeImageStorage`,
   `ExerciseRepositoryDuplicateNameDeviceTest`.
3. **feat(kit): launch the image sources behind one platform seam** — the four `media` files.
4. **refactor(exercise): consume the media seams** — the 16 feature paths, `AppGraph.kt`,
   `ExerciseExtensionIdentityTest.kt`.
5. **docs: document the media seams and record PR-M's evidence** — the three documents of Section 9
   and this document's Status and Section 14. The Status line becomes
   `**Status:** Part A COMPLETE — implemented by the PR whose merge brought this line to dev. Part B
   is specified by an amendment.`, which is true on dev only once the maintainer merges.

Review fixes are new signed commits inside Section 9. Nothing is amended, squashed or reordered.

## 12. Exit criteria

### 12.1 PR-M

Section 3 proven; Sections 6 and 7 evidenced; one PR into dev, CI green on its head,
`@claude review` requested and every finding classified (correct / already decided / wrong / new)
before any fix; the maintainer merges.

### 12.2 The maintainer's device check (after the merge, before Part B)

On a phone with a fresh install of the merged build:

1. Exercise → Edit → photo → Camera, first use: the system asks; allow; take a photo; the thumbnail
   shows it; Save; reopen the exercise: the photo is there.
2. Revoke the camera permission in settings; Camera again; deny: the permission sheet opens; its
   settings button opens the app's settings page.
3. Gallery: pick a photo; the thumbnail shows it; Save; reopen: it is there.
4. Camera and Gallery, each backed out without a photo: nothing changes, no sheet stays open.

Part B starts after this check passes.

## 13. Part B — the move (decisions; the contract follows by amendment)

Accepted with the discovery and unchanged by Part A: D4-A (the two Robolectric suites to commonTest
with recording fakes; the real delete stays `ExerciseRepositoryImplDbTest`'s and the RU catalog
`ComposeStringResolverRuTest`'s), D5-A (an exercise-only extra check pins `ExerciseFormBasicsTest`),
and the discovery's S2–S8, S10–S12 and S14–S16: 7.14 D2-B for the six synchronous copy paths and
kit's undo label; F2's split of `applyLoaded` (history rows before the write, the tag match and the
snapshot inside, with `.copy(`); both dates on `ResourceWrapper`; P1's six plural items; the 14 bare
previews pinned and the `uiMode` pair as a ThemeMode subject; `kotlin.time.Clock`, CMP `BackHandler`,
CMP resources with `ExerciseRes`, Coil through `LocalPlatformContext`, the dead
`kotlinx.serialization.json` edge dropped and `api(project(":core:core"))`; the four use cases on
7.12 D2-A's seam; the golden classes and PNGs moved unchanged, `sheetPlanInfo` as the runtime gate
for the catalog's `\n\n`; both device classes to androidDeviceTest; the identity test on
`AppRootDeps`' `exerciseGraphFactory`; `FEATURE_RESOURCE_OWNERS`' exercise root; the documents.
Part A already did S1 (`ImageRef` in the contract), S9 (the pinned identities) and S13's content
change (the `Context` identity).

The amendment states Part B's measured baseline on dev after PR-M, the entry, its contracts and
controls, and its commit plan, in 7.14's shape.

## 14. Implementation evidence

Filled by commit 5: base SHA; commits 1–4 with the signed-commits skill's local `gpgsig` check,
and commit 5 as "this commit"; the gate table with executed-task lines and case counts; K0–K12 with
each verdict quoted; the source census of Section 4.4; the PNG blob-set hash; the environment
(Xcode, Pythons, the emulator and its serial); deviations, if any. GitHub's signature verification,
the CI run ids and the review classification go in the PR body and the report, because they cannot
exist before this commit does.

This section records the bounded implementation the maintainer's GO of 2026-10-20 authorized:
Part A exactly (Sections 4 and 5) inside Section 9, in Section 11's five commits. Sections 0–13
remain the specification and are not rewritten here; where the measured tree differs from their
prose, Section 14.7 says so.

Every load-bearing Gradle invocation below ran after `./gradlew --stop` and a sample of every other
Gradle client and daemon on the host (`gradle-wrapper.jar`, `GradleWrapperMain` and `GradleDaemon`,
each with its working directory read through `lsof`), with
`--rerun-tasks --no-build-cache --no-configuration-cache --console=plain` (plus `--continue` where
stated). This run's Gradle used its own daemon registry (`-Dorg.gradle.daemon.registry.base` in
`GRADLE_OPTS`, the pre-commit hook's detekt and `mutation_harness.py` included), so its `--stop`
reached no other daemon. Across the 64 gate invocations (the 13 harness runs of Section 14.3 come on top; each stops this run's daemons itself), the samples taken before each stop
list 63 of this run's daemons (63 distinct PIDs, each with its working directory in this
run's registry), one foreign daemon in every sample (PID 3369, Gradle 9.6.1, the default registry,
idle: 1:03 of CPU, all at its start, before this run's first gate), and no client. Every "Executed"
cell is the logged `N actionable tasks: N executed`. Only JUnit XML written after the run's recorded
start was parsed, and each gate's XML was copied aside at once. detekt never shared an invocation
with tests. Every census, scorer and audit below is a script this one session wrote and ran; no
sub-agent took part, and no git worktree was created.

### 14.1 Entry, drift, census and baselines

This specification landed on `dev` as `cb2da0e456d77d8a814daeb43a79557e220807f9`, one signed commit
that fast-forwarded `63d511dee`; its blob had the input sha256 `4a464dc1…690938`. It was pushed with
the launch prompt's guarded command, which the maintainer authorized in their own message, run as
written (the push printed GitHub's bypass notice for the two required status checks). GitHub reports
the commit `verified: true`, `reason: valid`. A Markdown-only push starts no unified run, and none
started. That SHA is the implementation base.

| Entry check | Result |
| --- | --- |
| latest push run of `Android CI/CD - Unified Build and Tests` on `dev` | run `39396179621` on `63d511dee`, `success` (Build and Unit Tests, Release bundle identity, KMP iOS kit smoke, Unit Test Results, Detailed Unit Test Report) |
| `63d511de` is an ancestor of the base | yes (the base's parent) |
| drift | the one commit since `63d511de` is the SPEC commit, which touches only this document's path; no Section 9 path changed (no conflict) |

The census reads every figure twice, from git objects at the base (`ls-tree`, `cat-file`) and from
the working tree, under both Pythons, with byte-identical output; A equals B and both equal this
specification on every row:

| Figure | Base |
| --- | --- |
| `feature/exercise/src/main`: `import android.` / `import java.` / `import androidx.core.` lines | 10 / 1 / 3 |
| activity-result imports of `ui/ExerciseGraph.kt` | 3 (`rememberLauncherForActivityResult`, `PickVisualMediaRequest`, `ActivityResultContracts`) |
| non-golden identities, by `@Test`, by the topology gate's `KOTLIN_TEST_NAME` and distinct | 120 in 10 suites: 68/11/9/8/6/6/5/4/2/1 |
| golden methods (the gate's `GOLDEN_METHODS`) | 24 in 3 classes (3, 15, 6) |
| PNGs under `src/test/snapshots` | 48, all `100644`, 48 distinct blobs, blob-set sha256 `0e4ba874…6a6353` |
| `ExerciseInteractor` / `ImageStorage` abstract members | 20 / 4 |
| `ImageStorage` implementers | 3: `ImageStorageImpl`, `FakeImageStorage`, the `ExerciseRepositoryDuplicateNameDeviceTest` object |
| `ExerciseExtensionIdentityTest` names | the six of Section 5.4's baseline |
| identities in the module that drive `OnImageSourceSelected` or `RequestCameraCapture` | 0 |

The census also lists two fully qualified `android.` references outside imports: the graph's
`android.Manifest.permission.CAMERA` (gone at commit 4) and the preview `uiMode` in
`ImageSourceSheetContent.kt` (Part B's `uiMode` pair, unchanged).

| Baseline on the base | Executed | Fresh XML |
| --- | ---: | --- |
| topology gate, both Pythons | — | exit 0, 712 lines, empty stderr, byte-identical (BASE_GATE_OUTPUT) |
| `:feature:exercise:testDebugUnitTest` | 212 | 10 suites, 120 cases |
| `:feature:exercise:verifyPaparazziDebug` | 213 | 13 suites, 168 cases; `Visual gate live: 48 golden test case(s) executed for 48 golden image(s).` |
| `:core:core:testAndroidHostTest` | 25 | 8 suites, 42 cases |
| `:core:ui:kit:testAndroidHostTest` | 87 | 26 suites, 415 cases |
| `:app:app:testDebugUnitTest` | 592 | 23 suites, 200 cases; `ExerciseExtensionIdentityTest` 6 |
| fourteen-module `iosSimulatorArm64Test`, `--continue --full-stacktrace` | 386 | 84 suites, 483 cases; then `assert_kmp_ios_smoke.py`, exit 0, 14 modules verified (kit 2 tuples), byte-identical under both Pythons |

### 14.2 Commits and per-commit gates

Each commit was made through the signed-commits procedure; its raw object carries a `gpgsig`
header, and `Co-Authored-By` is its only trailer. detekt ran alone on the staged tree before each
commit and again on the committed tree after it: 63 tasks, 55 fresh reports, 0 findings every time;
the pre-commit hook's detekt passed on every commit.

| Commit | Gate | Executed | Fresh XML |
| --- | --- | ---: | --- |
| 1 `665783561` test(exercise) | `:feature:exercise:testDebugUnitTest` | 212 | 10 suites, 124 cases; the four Section 5.3 names added, none removed |
| 2 `b37b153df` feat(core) | `:core:core:testAndroidHostTest` | 25 | 9 suites, 46 cases; the four Section 5.1 names added |
| | `:core:core:assembleDebug` | 35 | `core.aar`; `compileKotlinIosArm64` and `compileKotlinIosSimulatorArm64` klibs fresh |
| | `:core:ui:test-utils:assembleDebug :core:data:exercise:assembleAndroidDeviceTest` | 244 | — (`compileAndroidDeviceTest` executed) |
| | `:app:app:testDebugUnitTest` | 592 | 23 suites, 200 cases (= base) |
| 3 `555eec09c` feat(kit) | `:core:ui:kit:testAndroidHostTest` | 87 | 27 suites, 416 cases; `launchesEachContractAndRoutesItsResult` added, 2.5 s, no hang |
| | `:core:ui:kit:assembleDebug` | 128 | `kit.aar` and both iOS klibs fresh |
| | fourteen-module Native, `--continue` | 386 | 84 suites, 483 cases; oracle exit 0, output byte-identical to the base and across both Pythons (kit's 2 tuples unchanged) |
| | fourteen-module `linkDebugTestIosArm64`, `--continue` | 372 | 14/14 `test.kexe` written after the run's start |
| 4 `1c7a27f91` refactor(exercise) | `:feature:exercise:testDebugUnitTest` | 212 | 10 suites, 127 cases (72/13/10/8/6/6/5/4/2/1); against commit 1 exactly Section 5.3's three names added, none removed |
| | `:feature:exercise:verifyPaparazziDebug` | 213 | 13 suites, 175 cases; `Visual gate live: 48 golden test case(s) executed for 48 golden image(s).`; porcelain empty; blob-set hash unchanged |
| | `:app:app:testDebugUnitTest` | 592 | 23 suites, 200 cases; `ExerciseExtensionIdentityTest` 6/6: Section 5.4's replacement in, the `Context` identity out, the other five unchanged |
| | `:feature:exercise:assembleDebug :app:app:assembleDebug` | 411 | — |

The four commit-1 identities pass at commit 1 (static stubs) and at commit 4 (the `CameraPermission`
mock), under the same names.

### 14.3 Known-negative controls

Each control ran through `mutation_harness.py` one-shot (`--no-configuration-cache --console=plain`
in `--task`; the harness adds `--rerun-tasks --no-build-cache`) between two fresh GREEN runs of the
same task, all with every task executed. The verdict is scored from the mutated run's fresh XML,
never from the harness's verdict line. After each run the file's sha256 equalled the one before and
`git status --porcelain --untracked-files=no` was empty. K0 ran on commit 1, K1–K12 on commit 4.

| Id | Mutation | RED quoted from the fresh XML | GREEN before / after |
| --- | --- | --- | --- |
| K0 | `if (hasCameraPermission())` → `if (!hasCameraPermission())` | exactly the two camera identities: `expected: <[Haptic(type=ContextClick), NavigateLaunchCamera(tempUri=Uri(#53))]> but was: <[Haptic(type=ContextClick), NavigateRequestCameraPermission]>` and the converse; 2 of 72 | 72 / 72 |
| K1 | `AndroidCameraPermission.isGranted` returns `true` | only `isGranted is false while the camera permission is denied`: `expected: <false> but was: <true>`; 1 of 2 | 2 / 2 |
| K2 | `ImageStorageImpl.lastModified` returns `0L` | only `lastModified returns the stored file's modification time`: `expected: <1700000000000> but was: <0>`; 1 of 7 | 7 / 7 |
| K3 | `success && target != null` → `target != null` | the failed-capture row: `failed capture picked ==> expected: <[content://test/capture-1.jpg]> but was: <[content://test/capture-1.jpg, content://test/capture-2.jpg]>`, `failed capture cancels ==> expected: <1> but was: <0>` (the later rows' running totals follow) | 1 / 1 |
| K4 | `launchCamera` no longer writes `pendingCameraTarget` | the capture row reports a cancel: `capture picked ==> expected: <[content://test/capture-1.jpg]> but was: <[]>`, `capture cancels ==> expected: <0> but was: <1>` | 1 / 1 |
| K5 | `PickVisualMedia.ImageOnly` → `ImageAndVideo` | only `gallery media type` (expected `ImageOnly`) | 1 / 1 |
| K6 | `Manifest.permission.CAMERA` → `READ_MEDIA_IMAGES` | only `permission input ==> expected: <android.permission.CAMERA> but was: <android.permission.READ_MEDIA_IMAGES>` | 1 / 1 |
| K7 | the settings intent's `data = …` line removed | only `settings data ==> expected: <package:io.github.stslex.workeeper.core.ui.kit.test> but was: <null>` | 1 / 1 |
| K8 | the camera branch reads `!cameraPermission.isGranted()` | exactly the two camera identities, e.g. `expected: <[Haptic(type=ContextClick), NavigateRequestCameraPermission]> but was: <[Haptic(type=ContextClick), NavigateLaunchCamera(target=ImageRef(value=))]>`; 2 of 72 | 72 / 72 |
| K9 | the gallery branch reads the permission and sends `NavigateLaunchGallery` either way | only the gallery identity: `Verification failed: call 1 of 1: CameraPermission(#484).isGranted()) should not be called`; 1 of 72 | 72 / 72 |
| K10 | the load carries `0L` instead of the read | only `the load keys the image by its file's modification time`: `expected: <1234> but was: <0>`; 1 of 13 | 13 / 13 |
| K11 | `getImageLastModified` returns `0L` | only `getImageLastModified forwards to the image storage`: `expected: <1234> but was: <0>`; 1 of 10 | 10 / 10 |
| K12 | `@SingleIn(AppScope::class)` removed from `AndroidCameraPermission` | only `the camera permission is inherited from the parent graph`: `The extension's CameraPermission must be the parent graph's app-scoped instance`; 1 of 6 | 6 / 6 |

Executed tasks per run: K0 and K8–K11 212, K1–K2 25, K3–K7 87, K12 592.

### 14.4 Repository and Native gates on commit 4

| Gate | Executed | Result |
| --- | ---: | --- |
| `assembleDebug lintDebug testDebugUnitTest --continue` | 2775 | 472 suites, 3345 cases, 0 failures, errors or skips (7.14's 470 / 3333 plus this PR's 2 suites and 12 cases); lint: 36 modules `Lint found no errors or warnings`, and `app:app`, `app:dev` and `app:store` `Lint found 77 warnings` each, all dependency-version checks (GradleDependency 37, NewerVersionAvailable 34, AndroidGradlePluginVersion 6) in `gradle/libs.versions.toml` and `gradle-wrapper.properties`, none in source — the same as 7.14 Section 19.8 |
| `detekt --continue` | 63 | 55 fresh reports, 0 findings |
| `assembleDebugAndroidTest` | 2171 | — |
| `verifyPaparazziDebug` | 673 | 169 suites, 1751 cases; 13 `Visual gate live` lines, 456 golden cases for 456 images; porcelain empty |
| `:lint-rules:test` | 9 | 16 suites, 151 cases |
| topology gate, both Pythons | — | exit 0, 712 lines, empty stderr, byte-identical to BASE_GATE_OUTPUT |
| `python3 documentation/personal_data_gate.py -v` | — | `[PASS] no personal data in tracked files` |
| fourteen-module Native, `--continue` | 386 | 84 suites, 483 cases; oracle exit 0, output byte-identical to the base and across both Pythons |
| fourteen-module device link, `--continue` | 372 | 14/14 fresh `test.kexe` |

### 14.5 Device gates

On `emulator-5554` (`Pixel_6_API_34`, API 34), booted by this run at 12:05Z (boot completed 12:05:24Z) with the launch
prompt's command (qemu PID 41871) and shut down by `adb -s emulator-5554 emu kill` at 12:24:34Z,
after the last device run. Before and after every run (eight snapshots) the three animation scales
read 0, `accelerometer_rotation` and `user_rotation` 0, the keyguard was down, nothing was
instrumenting, and the one installed workeeper package, the maintainer's
`io.github.stslex.workeeper.dev`, kept its `lastUpdateTime` of 2026-09-07; no test APK stayed
installed. Every run had `ANDROID_SERIAL=emulator-5554`.

| Gate | Executed | Fresh XML |
| --- | ---: | --- |
| `:feature:exercise:connectedDebugAndroidTest` | 248 | 2 suites, 5 cases: `ExerciseScreenTest` 3, `ExerciseFormBasicsTest` 2 |
| `:app:app:connectedDebugAndroidTest` with `-Pandroid.testInstrumentationRunnerArguments.class=` `RouteReachabilityTest`, `StoreRetentionTest`, `BackStackStateRestorationTest`, `--max-workers=2 --continue` | 753 | 3 suites, 22 cases: 15 + 3 + 4, among them the exercise create, seeded-row and image-viewer journeys |
| Smoke suite: `connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.annotation=io.github.stslex.workeeper.core.ui.test.annotations.Smoke -Dorg.gradle.jvmargs=-Xmx3g --max-workers=2 --full-stacktrace --continue` | 2131 | 15 fresh XML owners, 11 with cases, 44 cases: 41 passed and exactly the three documented skips (`ArchiveScreenTest`, `AllTrainingsScreenTest`, `AllExercisesScreenTest` `pendingFeatureRewrite`); 0 failures and errors; exercise's 5 among them. Then `assert_mvi_device_identities.py`: `core:ui:mvi (androidDeviceTest): 2 executed / 0 skipped / 0 failed / 0 errored — verified 2 exact identities` |
| Regression suite: the same with `…annotations.Regression` | 2131 | 88 cases, all executed, 0 skips, failures or errors: `app:app` 49 (`RouteReachabilityTest` 15, `StoreRetentionTest` 3, `UiAdmissionRaceTest` 3, `ApplicationBottomBarTest` 4, `AllTrainingsExtensionDbVisibilityTest` 1, `UiGenerationSwapTest` 1, `AppRuntimeUiHandshakeDeviceTest` 1, and the rest), `core:data:database` 35, `core:data:exercise` 1, `feature:all-exercises` 1, `feature:wear-bridge` 2 |

Against Phase 7.14's record (its Section 19.8): the same 44 Smoke cases with the same three skips and
the same 88 Regression cases in the same modules; no new failure or skip. The Smoke run wrote 15 XML
owners where 7.14 counted 14; the four without cases belong to `app:app`, `core:data:database`,
`core:data:exercise` and `feature:wear-bridge`, which carry no `@Smoke` test.

### 14.6 Exit census and PNG proof

At commit 4, the census in exit mode (both methods, both Pythons, byte-identical) reads, for
`feature/exercise/src/main`: `import android.` 0 (10 at the base), `import java.` 0 (1),
`import androidx.core.` 0 (3), activity-result imports 0 (3). The one fully qualified `android.`
reference left is the preview `uiMode` in `ImageSourceSheetContent.kt`, not an import. What Section
4.4 leaves for Part B is all there: `LocalContext` in `di/ExerciseFeature.kt` and
`ExerciseDescriptionBlock.kt`, the activity `BackHandler` in `ui/ExerciseGraph.kt`,
`androidx.compose.ui.res.*` in seven files, `@VisibleForTesting` in `ExerciseStoreImpl.kt` and the
two `System.currentTimeMillis()` in `ClickHandler.kt`. `ExerciseInteractor` has 21 abstract members,
`ImageStorage` 5 with the same 3 implementers, and four identities drive the image sources.

The PNG set at commit 4 and at this commit's parent: 48 files, all `100644`, 48 distinct blobs,
`git ls-files -s feature/exercise/src/test/snapshots | awk '{print $2}' | sort | shasum -a 256` =
`0e4ba8746577ab0198e29fc09e58f7fd145d41dbf6a6cc01489dfcd09b5a6353`, the base's value. No golden was
recorded and no PNG path is in the diff.

The diff against the base: after commit 4, `git diff -M30% --name-status` lists exactly Section 9's
30 code paths (7 `A`, 23 `M`); after this commit, its 34 (the four documents `M`). No `.png`,
`strings.xml`, `*.gradle.kts` or `gradle/libs.versions.toml` path.

### 14.7 Environment, worktrees, deviations and limits

| Item | Value |
| --- | --- |
| Xcode | 27.0 (27A266a) here, `/Applications/Xcode.app/Contents/Developer`, first launch done; iOS runtimes 18.0, 18.2, 18.6, 26.5. CI's `KMP iOS kit smoke` selects Xcode 26.6 |
| JDK | JBR 21.0.11 |
| Pythons | `/opt/homebrew/bin/python3` 3.15.0 and `/usr/bin/python3` 3.9.6; the topology gate, the oracle and the census ran under both with byte-identical output |
| Gradle | 9.6.1, this run's own daemon registry in the session scratchpad |
| emulator | `Pixel_6_API_34` (API 34) as `emulator-5554`, qemu PID 41871, booted and shut down by this run (Section 14.5) |
| free disk | 101.8 GiB at the first gate, 91.0 GiB at the lowest (after the repository build gate); no disk-full event |

Worktrees: at Phase 0 the repository had none besides the main checkout, so none was removed and
none was kept. This run created none.

Deviations and implementation decisions, none of which widens Section 9:

1. Commit 1's four identities share one private helper in `ClickHandlerTest`,
   `withStaticCameraMocks(granted) { captureUri -> … }`, which installs both static mocks and undoes
   them in a `finally`; commit 4 removes it. Every identity also wires the synchronous launch, the
   gallery one included, so its `createTempCaptureRef` never-call is not vacuous.
2. `ClickHandler`'s `ImageRef` import goes in commit 4: after `saveImage(pending.ref, …)` the class
   names the type nowhere, and an unused import fails detekt.
3. The load names the awaited exercise `loadedExercise`; the six `async` reads are unchanged, and the
   modification time is read after the exercise is awaited and before `LoadResult` is built.
4. The two new `CommonHandlerTest` identities build their exercise with one private
   `exercise(imagePath)` and also assert that the load landed (`name == "Bench"`), so neither can pass
   on a load that never ran.
5. `ExerciseExtensionIdentityTest`'s class KDoc named the bare `Context`; it now names the camera
   permission. `ImageStorageImplTest` gains one comment on the granularity the comparison absorbs.
6. K3's RED also moves the running totals the later rows assert (the failed capture's reference stays
   in `picked`); the failed-capture row is the one Section 6 names.

Not verifiable on this host: GitHub's verification of this commit and the CI runs on the PR (they
are in the PR body and the report); the iOS actuals' behaviour (compiled and linked, never
executed); the system camera and photo-picker apps and the graph's permission-result mapping
(Section 12.2); Xcode 26.6, which only CI runs.

---

# Part B — the move: feature:exercise becomes a shared feature entry

**Amendment of 2026-10-21.** Sections B0–B18 are Part B's contract; Section B19 is filled by its
implementation. Sections 0–14 above are Part A's record and are not rewritten.

**Measured on:** PR #341's head `cf91a98319bd61492ffe8eeffbd21fb79ad9a2ba` (tree
`624401e2ce22e4ba8f564cd801365b63ac8140b5`), static measurement with git data, greps and scripts;
nothing was compiled or run. #341 merged into dev as `509bfba2d7dae7dd20c1e1b7780b2614cd7c3b44`,
whose tree is this one. The implementation base is dev after this amendment lands; Section B12.2
proves it carries this tree on every path Part B reads.

**Template:** [Phase 7.14](kmp-phase-7-14-single-training-feature.md). Part B is a delta on it:
every rule of 7.14 that Part B does not change applies unchanged, read with the substitutions of
Section B0.3, and through 7.14 the rules of 7.13, 7.12, 7.11, 7.10, 7.9, 7.8 and 7.7 it inherits.
Exercise has a route argument, so 7.11 Section 6 is the precedent for its factory flow (shape B).

**Prototype:** Section B12's gate change, gate data and every Python-scored control were run against
a simulated exit tree built from the measured one (Sections B12.0, B12.1, B13). That prototype is
not evidence; the implementation's fresh runs are.

## B0. Authority, decisions, substitutions

### B0.1 Authority order

1. live origin/dev at the implementation base;
2. AGENTS.md, CLAUDE.md, documentation/architecture.md, testing.md, ci-cd.md,
   compose-state-discipline.md;
3. Part B of this document;
4. the Phase 7.14 specification (its Section 19 included), and through it 7.13–7.7;
5. Part A of this document (Sections 0–14) and kmp-string-resolver.md;
6. the checked-in source, resources, tests, PNGs and CI oracles at the implementation base.

### B0.2 Stable rules

Section 0.2 above applies unchanged: the main checkout only, no `git worktree` for anything; the
positive-gate flags after `./gradlew --stop`; quoted `N actionable tasks: N executed`; fresh JUnit
XML only; detekt and tests in separate invocations; signed, GitHub-verified commits with
`Co-Authored-By` as the only trailer; no session identifier in a commit, the PR or a document; no
merge by the implementer. 7.14 Section 13's Python-scored controls ran "against a byte-restoring
copy in a detached worktree"; here they run in the main checkout through `mutation_harness.py`, or
against a byte-restoring scratchpad copy restored with `cp` (Section B13).

Suppressions: **no new suppression of any kind.** The seven inherited production suppressions, in
six files, stay byte-semantically unchanged:

- `@Suppress("UNCHECKED_CAST")` on the processor cast in di/ExerciseFeature.kt;
- `@Suppress("TooManyFunctions")` on the interface in domain/ExerciseInteractor.kt;
- `@Suppress("LongParameterList", "TooManyFunctions")` on domain/ExerciseInteractorImpl.kt;
- `@Suppress("LongMethod", "CyclomaticComplexMethod")` on `exerciseGraph` in ui/ExerciseGraph.kt;
- `@Suppress("TooManyFunctions", "LargeClass")` on ui/mvi/handler/ClickHandler.kt and
  `@Suppress("LongMethod")` on its `processSaveClick`;
- `@Suppress("MviActionNamingRule")` on `OnAdhocPlanEditorAction` in ui/mvi/store/ExerciseStore.kt.

The module has no test suppression, and Part B needs no Native test-name suppression (Section B4.3).

One session does all the work. Every census, transcription and audit is a script that session
writes and runs. None is a separate agent.

### B0.3 Substitutions against 7.14

| 7.14 | Part B |
| --- | --- |
| `feature:single-training`, `feature/single-training` | `feature:exercise`, `feature/exercise` |
| package `io.github.stslex.workeeper.feature.single_training` | `io.github.stslex.workeeper.feature.exercise` |
| resource package `…feature.single_training.resources` | `io.github.stslex.workeeper.feature.exercise.resources` |
| `SingleTrainingFeature`, `SingleTrainingGraph`, `singleTrainingsGraph` | `ExerciseFeature`, `ExerciseGraph` (the Metro graph, di/ExerciseGraph.kt), `exerciseGraph` (the composable, ui/ExerciseGraph.kt) |
| `singleTrainingGraphFactory`, `createSingleTrainingGraph(screen)`, `singleTrainingStore` | `exerciseGraphFactory`, `createExerciseGraph(screen)`, `exerciseStore` |
| `SingleTrainingStoreImpl`, `SingleTrainingExtensionIdentityTest` | `ExerciseStoreImpl`, `ExerciseExtensionIdentityTest` |
| route `Screen.Training(uuid: String?)` | `Screen.Exercise(uuid: String?)`; shape B, `create_args = "screen"`, unchanged |
| graph tag `SingleTrainingGraph` | `ExerciseGraph` |
| MVI under `mvi/` | MVI under `ui/mvi/` (every path below says which) |
| `SINGLE_TRAINING_ENTRY` | `EXERCISE_ENTRY` |
| fourteen-module Native command | fifteen modules (Section B12.1) |
| 7.14's D4 and D5: extra golden classes and same-file copy helpers (commit 1) | G1 and G2: the screen-registration overload and foreign key prefixes (commit 1, Section B12.0) |
| 7.14's D2: four click paths, constants once per Store, the confirm on use | the same model over six click paths (Section B5.2) |
| 7.14's F2: `applyLoaded` and `applySnapshotOrPop` | F2: `applyLoaded` only (Section B5.3) |
| 7.14's S1: `ResourceWrapper.formatMediumDate`, one call | S4: `formatDayMonth` and `formatMediumDate`, one call each, in the UI mapper (Section B5.4) |
| 7.14's S14: four new identities | S17: six new identities (Section B11.1) |
| `cmp.material.icons.extended` | `coil.compose` (the module uses kit's `AppIcons` only) |

### B0.4 Decisions

Locked by the maintainer's GO of 2026-10-20 (the discovery's D1–D6 and its spec-level items),
restated for Part B:

| Id | Decision |
| --- | --- |
| D4 | The two Robolectric suites move to commonTest with recording fakes: the window, the commit outside the Store and Back-after-queue stay asserted; the real delete stays `ExerciseRepositoryImplDbTest`'s (core:data:exercise) and the Russian catalog `ComposeStringResolverRuTest`'s (core:ui:kit). Names unchanged. |
| D5 | An exercise-only extra check pins `ExerciseFormBasicsTest`'s fragments (home's one-off precedent). |
| D6 | One specification, two PRs; this is the second. |
| S2 | Copy on F3's six synchronous click paths follows 7.14's D2: seven catalog constants and kit's undo label resolved once per Store at `Init` into State; the permanent-delete confirm, which formats the name, resolved on use behind a compare-and-set guarded on `BottomSheetState.DetailMenu`; the five reads already inside launches stay on use (Section B5.2). |
| S3 | F2's split of `applyLoaded`: the history rows and the plan before the write; the tag match, the snapshot and the draft carry inside, with `.copy(` (Section B5.3). |
| S4 | The two dates stay on `ResourceWrapper`, read by the UI mapper; `ClickHandler` moves wholly to `StringResolver` (Section B5.4). |
| S5 | P1: the plural's 6 bare `%d` items (EN 2, RU 4) become `%1$d`. |
| S6 | The 14 bare previews are pinned by `bare_previews`; `ImageSourceSheetContent`'s `uiMode` pair becomes a ThemeMode subject (Section B5.5). |
| S7 | The platform rows of Section B4.2: `kotlin.time.Clock`; `@VisibleForTesting` dropped; the Feature takes the factory by constructor; the private `Res`, imported as `ExerciseRes` in the files that also read kit's `Res`; Coil through `LocalPlatformContext` with `implementation(libs.coil.compose)`; CMP's `BackHandler`; the dead `kotlinx.serialization.json` edge dropped; `api(project(":core:core"))`. |
| S8 | The four use cases on 7.12 D2-A's seam: public interfaces, public `…Impl` classes with internal constructors, `@Binds` in the graph; no identity changes for it (Section B5.9). |
| S10 | The suites move to commonTest with private in-file fakes; names unchanged (Section B11.1). |
| S11 | The three golden classes move to androidHostTest unchanged, the 48 PNGs by `git mv` with the blob set unchanged; `sheetPlanInfo` is the runtime gate for the catalog's `\n\n` escape; any PNG change is a STOP (Section B11.2). |
| S12 | Both device classes move to androidDeviceTest; the device gate after the move runs them and the app journeys through the screen (Section B11.3). |
| S13 | The identity test reaches the graph through `AppRootDeps`' `exerciseGraphFactory` instead of `asContribution`; its six identities (Part A's content) keep their names and assertions (Section B6). |
| S14 | The move commit moves `FEATURE_RESOURCE_OWNERS`' exercise root to `src/commonMain/composeResources`. |
| S15 | Five signed commits (Section B15). |
| S16 | The documents (Section B9.2). |

S1 (`ImageRef` in the Store contract), S9 (the image-source identities) and S13's content change
are Part A's and done. A dropped `packageName` (S1's tail) is done too: `Event.NavigateOpenAppSettings`
is a `data object` at the base.

**Required, not decisions — G1 and G2.** The shared gate cannot express this entry as it stands:

- **G1.** `check_feature_entry` requires the literal `navComponentScreen(<Feature>(factory))` in the
  graph file. Exercise registers through `navComponentScreenWithResults(ExerciseFeature(factory))`
  (core:ui:mvi commonMain, the `FeatureAssisted` overload), because it reads the image viewer's
  result back. Commit 1 adds an additive `FeatureEntry` field `screen_registration` (default
  `"navComponentScreen"`) that the fragment reads.
- **G2.** The resource-ownership walk requires every key that starts with `resource_prefix` to live
  in the entry's two catalogs. Exercise's keys share `feature_exercise_` with exercise-chart's 33
  `feature_exercise_chart_…` keys (31 strings, 2 plurals) and with kit's two `feature_exercise_detail_type_…` labels, and no
  narrower prefix covers exercise's own keys. Commit 1 adds an additive field
  `foreign_resource_prefixes` (default `()`) that the walk skips.

Both defaults keep every existing entry's output byte-identical (Section B12.0's proof). The
alternative — renaming kit's or exercise-chart's keys — changes two other modules' catalogs and
goldens for a gate's convenience, and is out of scope.

**Required, not decisions — F2 and F3.** The discovery's findings, settled by S2 and S3:

- F3: six `ClickHandler` paths resolve copy synchronously through `ResourceWrapper` today:
  `processTypeToggle` (four constants into `TypeChangeConfirm`), `processPermanentDeleteMenuClick`
  (four strings, the title formats the name, before the one write that swaps the menu sheet for
  the confirm), `processConfirmPermanentDelete` (the success message, before the main-immediate
  launch, whose action then suspends on CMP's top-level `getString` for kit's undo label),
  `processAdhocPlanEditorAction` (set removed, into the undo event), `processTagToggle` and
  `processTagCreate` (the tag limit, into the event). `StringResolver` is suspend-only.
- F2: `updateStateImmediate { current -> current.applyLoaded(result) }` returns a helper's State
  and, through `applyLoaded`'s history rows, reaches `resourceWrapper.formatDayMonth` inside a
  compare-and-set lambda (7.14's D5 reports it).

### B0.5 Corrections to the discovery

1. S15 named D5 as the first commit. D5 is an entry-local check, so it lands with the entry in
   commit 4; commit 1 carries G1 and G2.
2. S7 named `ClickHandler` among the five files that alias the feature's `Res`. At exit
   `ClickHandler` reads no kit resource (S2 moves kit's undo label to `Init`), and `CommonHandler`
   reads both; the five alias files are ui/ExerciseGraph.kt, ui/ExerciseDetailScreen.kt,
   ui/ExerciseEditScreen.kt, ui/components/PlanInfoSheetContent.kt and
   ui/mvi/handler/CommonHandler.kt.
3. D4 said two assertions "read the resolver fake". Under S2 the deferred delete reads its message
   and undo label from State, so those two assertions read the State's copy (Section B11.1).
4. The discovery's boundary sketch said 50 production files; S2's copy holder is a 51st
   (`ui/mvi/model/ExerciseClickCopy.kt`, one model per file as the module keeps them).

## B1. Bounded exit claim

The implementation may prove only this:

- feature:exercise applies `convention.kmpComposeLibrary` and retains Metro and Paparazzi;
- all 51 production Kotlin files of Section B3.1 compile from commonMain for Android,
  iosSimulatorArm64 and iosArm64;
- the exact private 54-identifier EN/RU catalog (53 strings and 1 plural) lives in commonMain
  Compose Resources, changed only by P1's 6 positional placeholders (Section B3.4);
- every visible Android behaviour is unchanged, except the inherited Compose-resources locale
  property (Section B5.7) and S2's differences (Section B5.2);
- copy outside composition resolves through the injected `StringResolver`, never through CMP's
  top-level `getString` / `getPluralString`; `ResourceWrapper` keeps exactly two reads, the history
  rows' day-month date and the record's medium date, in the UI mapper (S4);
- no click path suspends for copy: the weight-wipe confirm, the set-removed and tag-limit toasts and
  the deferred delete read `State.clickCopy`, which `Init` resolves once per Store, and the
  permanent-delete confirm resolves on use and lands only on the menu sheet it replaces (S2);
- no State lambda of a handler resolves copy, references a resolver, or calls a same-file function
  that does, and every one returns a `.copy(` (S3, 7.14's D5);
- 10 portable suites with 133 exact identities execute on Android host and on Native, the first
  `+` in a Native identity name among them;
- the three golden suites execute as 48 Android-host Paparazzi cases (24 methods × light/dark)
  against the same PNG blobs, rendering the module's EN and RU catalogs through CMP;
- both device classes execute their 5 identities from androidDeviceTest;
- one deterministic iOS production scene proves the whole EN catalog, the handlers' copy through
  the real `ComposeStringResolver`, the read screen, the editor, the three sheets and action
  dispatch, text input included;
- the Metro graph is reached through the explicit generation-owned factory (shape B), registered
  through `navComponentScreenWithResults`, never `LocalContext` or `Context.appDeps`; all six
  extension identities use the accessor;
- the topology gate checks the entry through one `FeatureEntry` after G1's and G2's additive
  change, all three golden classes and both device classes pinned; `Context.appDeps` readers drop
  from 4 to 3;
- `core:data:database` stays one production edge, read by the domain mapper alone;
- Android remains releasable with every repository PNG blob unchanged.

This is a source-set, dependency, resource, test and composition-root migration, plus S2's copy
model, S3's split and S8's seam. It does not permit:

- redesigning the read screen, the editor, the sheets, the history rows, the thumb or any dialog;
- changing save, discard, undo, archive, permanent-delete, Track now, tag, image or loading
  behaviour beyond S2's differences;
- fixing the create-mode dirty check (Section B17) or any other inherited defect;
- changing `core:ui:kit` (beyond Section B9.2's one comment line), `core:ui:plan-editor`,
  `core:ui:navigation`, `core:ui:mvi`, `core:core`, `core:data:exercise` or `core:data:database`;
- adding a device test identity;
- adding an iOS app;
- migrating another feature.

## B2. Entry census at the measured tree

7.14 Section 2's method, applied to `feature/exercise/src/main` as if the module were an entry:

| | Lambdas | Without `.copy(` | Token / resolver findings | Call a same-file copy helper (7.14 D5) |
| --- | ---: | ---: | --- | ---: |
| at the measured tree | 50 | 1 (`applyLoaded`) | 0 | 1 (`applyLoaded`) |
| at exit (prototype) | 51 | 0 | 0 | 0 |

S2's `resolveClickCopy` adds one lambda; the confirm's guarded write replaces the old one. The
copy-resolving same-file functions at exit are `loadExercise`, `observePersonalRecord`,
`processInit` and `resolveClickCopy` in `CommonHandler`, and `processArchiveClick`,
`processPermanentDeleteMenuClick`, `processTrackNowClick`, `processTrackNowDeleteAndStart` and
`startFreshTrackNow` in `ClickHandler`; no State lambda calls one.

Every module exercise depends on is already KMP. Every symbol its production code imports from
them is declared in commonMain or generated by CMP into it, with the notes of 7.14 Section 2
(the two dispatcher qualifiers; `core.ui.mvi.di.appDeps`, which leaves with the factory flow) and
two more: kit's `rememberImageSourceLauncher` (Part A, a public `expect` composable with an Android
and an iOS actual) and `navComponentScreenWithResults` / `NavResults` (core:ui:mvi commonMain).
No `java.` token remains (Part A), and the 7.12 scan list finds nothing; `System.currentTimeMillis()`
(twice) and `@VisibleForTesting` (once) are Section B4.2's rows.

## B3. Measured baseline

Static measurements at the measured tree. Test names are given as the base leaves them, plus S17's
six, marked. The module tracks 112 paths: the build file, 46 production files, 2 catalogs, 13
unit-test files (10 portable suites and 3 golden suites), 48 PNGs and 2 device tests.

### B3.1 Production manifest

The 46 files move from `src/main/kotlin/io/github/stslex/workeeper/feature/exercise/` to
`src/commonMain/kotlin/io/github/stslex/workeeper/feature/exercise/` with unchanged relative paths,
and five files are added: S8's four `domain/usecase/…UseCaseImpl.kt` and S2's
`ui/mvi/model/ExerciseClickCopy.kt`. Production goes from 46 files to 51.

| File | Lines at the base |
| --- | ---: |
| di/ExerciseFeature.kt | 34 |
| di/ExerciseGraph.kt | 49 |
| di/ExerciseHandlerStore.kt | 9 |
| di/ExerciseHandlerStoreImpl.kt | 14 |
| di/ExerciseScope.kt | 10 |
| domain/ExerciseInteractor.kt | 90 |
| domain/ExerciseInteractorImpl.kt | 150 |
| domain/mapper/ExerciseDomainMapper.kt | 129 |
| domain/model/ActiveSessionDomain.kt | 8 |
| domain/model/ArchiveResult.kt | 9 |
| domain/model/ExerciseChangeDomain.kt | 16 |
| domain/model/ExerciseDomain.kt | 14 |
| domain/model/ExerciseTypeDomain.kt | 7 |
| domain/model/HistoryEntryDomain.kt | 16 |
| domain/model/PersonalRecordDomain.kt | 12 |
| domain/model/PlanSetDomain.kt | 8 |
| domain/model/SaveResult.kt | 11 |
| domain/model/SetTypeDomain.kt | 9 |
| domain/model/TagDomain.kt | 7 |
| domain/model/TrackNowConflict.kt | 14 |
| domain/usecase/ArchiveExerciseUseCase.kt | 29 |
| domain/usecase/DeleteSessionUseCase.kt | 35 |
| domain/usecase/ResolveTrackNowConflictUseCase.kt | 34 |
| domain/usecase/StartTrackNowSessionUseCase.kt | 36 |
| ui/ExerciseDetailScreen.kt | 446 |
| ui/ExerciseEditScreen.kt | 387 |
| ui/ExerciseGraph.kt | 276 |
| ui/components/ExerciseDescriptionBlock.kt | 132 |
| ui/components/ExerciseDetailSheets.kt | 60 |
| ui/components/ExerciseHistoryRow.kt | 129 |
| ui/components/ImageSourceSheetContent.kt | 61 |
| ui/components/PlanInfoSheetContent.kt | 66 |
| ui/mvi/handler/ClickHandler.kt | 834 |
| ui/mvi/handler/CommonHandler.kt | 201 |
| ui/mvi/handler/InputHandler.kt | 40 |
| ui/mvi/handler/NavigationHandler.kt | 42 |
| ui/mvi/mapper/ExerciseUiMapper.kt | 106 |
| ui/mvi/model/HistoryUiModel.kt | 15 |
| ui/mvi/model/ImageDisplay.kt | 15 |
| ui/mvi/model/ImageErrorType.kt | 8 |
| ui/mvi/model/ImageSourceUiModel.kt | 7 |
| ui/mvi/model/PendingImage.kt | 15 |
| ui/mvi/model/PersonalRecordUiModel.kt | 16 |
| ui/mvi/store/DialogState.kt | 78 |
| ui/mvi/store/ExerciseStore.kt | 342 |
| ui/mvi/store/ExerciseStoreImpl.kt | 63 |
| **46 files** | **4089** |

The production suppressions of Section B0.2 sit at ExerciseFeature.kt line 24,
ExerciseInteractor.kt 17, ExerciseInteractorImpl.kt 35, ui/ExerciseGraph.kt 47, ClickHandler.kt 50
and 271, and ExerciseStore.kt 230.

Android coupling in production:

- `R.*` at 60 references in eight files, over all 54 of the catalog's identifiers:
  - seven UI files read 38 identifiers (37 strings and the plural) at 43 references, through
    Android's `stringResource` and `pluralStringResource` at 40 call sites:
    `ui/ExerciseDetailScreen.kt` 7 (six strings and the plural), `ui/ExerciseEditScreen.kt` 12 at 11
    calls (its fallback title is a local id picked between two), `ui/ExerciseGraph.kt` 10,
    `ui/components/ExerciseDescriptionBlock.kt` 6 at 4 calls (the thumb's description is a `when`
    over three), `ui/components/ExerciseDetailSheets.kt` 3,
    `ui/components/ImageSourceSheetContent.kt` 3 and `ui/components/PlanInfoSheetContent.kt` 2;
  - `ui/mvi/handler/ClickHandler.kt` reads the other 16 identifiers at 17
    `resourceWrapper.getString` sites: 12 on F3's six synchronous paths and 5 inside launches (the
    archive success with the name, the archive block's `used in` with the joined names, the
    Track-now conflict's unnamed fallback and its progress with two counts, and the default
    training name);
- kit's Compose `Res` read at 12 sites in four UI files (`core_ui_kit_action_back`; `…_action_cancel`
  and `…_action_save`; eight in the graph: the toast undo, the four discard-sheet strings with the
  dismiss twice, and `…_action_cancel` twice; `…_sheet_close`), through CMP's overload, which those
  files import beside Android's; and once outside composition, through CMP's top-level `getString`
  inside the deferred delete's launch (`core_ui_kit_toast_undo`, ClickHandler);
- `ResourceWrapper` in three files: ClickHandler's 17 `getString`; CommonHandler, which passes it
  to the mapper; and `ui/mvi/mapper/ExerciseUiMapper.kt`'s `formatDayMonth` (history rows, called
  inside the load's State lambda today, F2) and `formatMediumDate` (the record, in the record
  flow's `map`);
- `androidx.activity.compose.BackHandler` once, in the graph;
- `LocalContext` with `appDeps` in di/ExerciseFeature.kt, and `LocalContext` for Coil's
  `ImageRequest.Builder` in ExerciseDescriptionBlock.kt;
- `@VisibleForTesting` on `ExerciseStoreImpl.NAME`;
- `System.currentTimeMillis()` twice in ClickHandler: the save's `ExerciseChangeDomain.timestamp`
  and the post-save `imageLastModified`;
- one `uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES` preview pair, in
  ImageSourceSheetContent.kt.

Already portable: the 14 bare `@Preview`s, each over `AppTheme(themeMode = ThemeMode.…)`; every icon,
from kit's `AppIcons`; `kotlin.uuid.Uuid`; Part A's seams (`ImageRef`, `CameraPermission`,
`rememberImageSourceLauncher`); `navigationBarsPadding()` on both docks (CMP-common, 7.14's
precedent); `navComponentScreenWithResults` and `NavResults`.

State: 25 fields at the base (the gate's regex); S2 adds `clickCopy` as the 24th of 26, directly
after `dialogState`. `DialogState` carries pre-resolved strings (`TypeChangeConfirm` and
`PermanentDeleteConfirm`: title, body, impact, confirm label; `ActiveSessionConflict`: session,
name, progress; `ArchiveBlocked`: kit's `BlockedArchiveItem`). No resource handle or id lives in
State, the models or the domain. `launch` runs the action on Default and `onSuccess` / `onError` on
Main.immediate (`AppCoroutineScopeImpl`). No handler injects another handler.

### B3.2 Portable manifest — 10 suites, 127 identities, 133 after commit 2

Each suite moves from `src/test/kotlin/…/feature/exercise/` to `src/commonTest/kotlin/…/feature/exercise/`
with its relative path. **(S17)** marks the six identities commit 2 adds (Section B11.1).

**ui/mvi/handler/ClickHandlerTest.kt — 72, then 76**

- OnTypeToggle to WEIGHTLESS with weighted rows raises the confirm instead of switching
- OnTypeToggle to WEIGHTLESS with no weights switches immediately and asks nothing
- OnTypeChangeConfirm commits the switch and clears the weights it warned about
- OnTypeChangeDismiss leaves both the type and the weights alone
- OnSaveClick with blank name sets nameError without saving — reachable
- OnSaveClick with a name does not set nameError and proceeds
- OnEditClick flips mode to Edit and snapshots current state
- OnDetailMenuClick opens the detail-menu sheet
- OnSheetDismiss hides the detail-menu sheet
- OnPlanInfoClick opens the plan-info sheet
- OnEditClick from the sheet closes it in the same transition
- OnPermanentDeleteMenuClick swaps the sheet for the confirm dialog
- OnArchiveMenuClick closes the sheet before the archive result lands
- confirm permanent delete defers - nothing runs until the commit
- minus set emits the undo toast and the undo restores the row
- a stale set undo after save flipped to read edits nothing
- a stale set undo does not edit a re-entered draft
- an undo restored across a type switch re-enters weightless
- an undo during the save's write edits nothing
- a duplicate-name save re-arms the draft's undos
- a cancel during the save's write raises nothing and reverts nothing
- a back during the save's write raises nothing
- a confirmed discard during the save's write pops nothing
- FlipToReadMode during the save's write flips nothing
- Edit entry resets a stuck isSaving
- createTag resolving to an already-selected tag does not duplicate the chip
- a second create landing on a full draft chips nothing past the cap
- a plan value edit emits no undo toast
- the dashed add chip opens the tag picker sheet
- dismissing the tag picker clears the query with the sheet
- OnTagToggle adds tag when not selected
- OnTagToggle blocks adding when 10 tags already selected
- OnDismissArchiveBlocked is no-op
- OnTrackNowClick emits ContextClick haptic
- OnTrackNowClick reports the persisted active session progress
- OnTrackNowResumeConfirm with no pending conflict is a no-op
- OnTrackNowResumeConfirm consumes OpenLiveWorkout with active session uuid
- OnTrackNowConflictDismiss clears the active session conflict dialog
- OnCancelClick from clean Edit on existing flips to Read mode
- OnCancelClick from clean create mode pops back
- OnCancelClick from dirty Edit on existing shows FLIP_TO_READ discard dialog
- OnConfirmDiscard with POP_SCREEN navigates back
- OnConfirmDiscard with FLIP_TO_READ flips mode without popping
- OnConfirmDiscard with FLIP_TO_READ restores the plan the edit changed
- OnConfirmDiscard with FLIP_TO_READ restores a plan that was absent
- OnBackClick in clean Edit on existing flips to Read mode
- OnPermanentDeleteMenuClick is no-op when not eligible
- OnPermanentDeleteMenuClick surfaces the PermanentDeleteConfirm dialog when eligible
- OnEditImageClick opens the image source picker dialog
- OnRemoveImageClick stages a RemoveExisting pending image
- saving an image removal clears the persisted path before deleting the old file
- a rejected image removal keeps the attachment file and the staged draft
- cancelling an image removal restores the attachment without deleting its file
- OnImageSourceDialogDismiss hides the source dialog
- OnPermissionDeniedDialogDismiss hides the permission dialog
- OnCameraPermissionDenied surfaces the permission denied dialog
- OnPermissionDeniedSettingsClick emits NavigateOpenAppSettings
- camera with the permission creates a capture target and launches the camera
- camera without the permission asks for it and creates no capture target
- gallery launches the photo picker and reads no permission
- a granted permission launches the camera on a fresh capture target
- OnImageThumbnailClick with committed path consumes OpenImageViewer with the path
- Edit mode opens the viewer as editable
- Read mode opens the viewer as NOT editable
- OnImageThumbnailClick with no image is a no-op
- OnAdhocPlanEditorAction OnAddSet appends a default set to the in-memory plan
- OnAdhocPlanEditorAction OnSetRemove on the only row normalizes the plan back to null
- OnAdhocPlanEditorAction OnSetWeightChange routes through the reducer
- OnAdhocPlanEditorAction OnSetRepsChange routes through the reducer
- OnAdhocPlanEditorAction OnSetTypeChange routes through the reducer
- OnCancelClick from create-mode with edited plan surfaces POP_SCREEN discard dialog
- OnBackClick from create-mode with edited plan surfaces POP_SCREEN discard dialog
- a menu dismissed before the confirm's copy lands stays dismissed **(S17)**
- the type-change confirm carries the copy Init resolved **(S17)**
- the set-removed toast carries the copy Init resolved **(S17)**
- both tag-limit toasts carry the copy Init resolved **(S17)**

**ui/mvi/handler/CommonHandlerTest.kt — 13, then 14**

- Init for create mode does not load exercise
- Init for read mode kicks off observe + load
- a load that throws clears isLoading or the route is composed on nothing forever
- a create route never loads so it is never withheld
- a re-fired Init preserves a dirty draft and its staged image removal
- ImagePicked sets pendingImage to NewFromUri and hides the source dialog
- the load keys the image by its file's modification time
- an exercise without an image reads no modification time
- ImagePickCancelled hides the source dialog without staging a pending image
- a REPLACE request reopens the image source picker
- a REMOVE request stages the removal
- an unrecognised request name is dropped rather than acted on
- every declared request verb resolves to an action
- Init resolves the click paths' constant copy into State **(S17)**

**domain/ExerciseInteractorImplTest.kt — 10**

- countSessions forwards to countSessionsUsing
- saveExercise forwards the snapshot uuid to repository on Success
- saveExercise propagates the lastAdhocSets payload
- saveExercise propagates DuplicateName from repository
- archive returns Blocked when active trainings exist
- archive returns Success when no active trainings
- getImageLastModified forwards to the image storage
- canPermanentlyDelete delegates to repository
- permanentlyDelete delegates to repository
- observePersonalRecord forwards to repository and maps to domain

**ui/mvi/store/ExerciseDirtyStateTest.kt — 8**

- a freshly loaded exercise is clean
- ONLY the snapshot term - a renamed exercise with no image and no plan edit
- ONLY the image term - a staged picture the snapshot cannot see
- ONLY the plan term - create mode where there is no snapshot to compare against
- create mode with no plan and nothing typed is clean
- a plan the baseline has seen reads clean
- an inline plan edit the baseline has not seen reads dirty
- an empty plan and a null plan are the same plan

**ui/mvi/handler/NavigationHandlerTest.kt — 6**

- Back triggers popBack
- OpenSession navigates to Screen PastSession with the session uuid
- OpenLiveWorkout navigates to Screen LiveWorkout with the resume session uuid
- OpenImageViewer carries the model arg and the caller's own capability
- OpenImageViewer forwards a non-editable caller as non-editable
- OpenChart navigates to Screen ExerciseChart with the exercise uuid

**ui/mvi/mapper/ExerciseUiMapperTest.kt — 6**

- weighted record splits into trimmed weight and reps with the medium date
- fractional record weight trims trailing zeros
- weightless record carries no weight label
- history entry maps day-month date and the tight summary form
- history summary caps at five sets and appends an ellipsis
- weightless history sets summarize as bare reps

**ui/mvi/handler/ExerciseDeferredDeleteDbTest.kt — 5, then 6**

- confirming deletes nothing while the snackbar lives
- back is consumed only after the snackbar is queued with the real undo label
- the window closing commits the delete
- undo — or a process death inside the window — leaves the row
- the commit outlives the screen that scheduled it
- the queued delete carries the message and the undo label Init resolved **(S17)**

**ui/mvi/handler/InputHandlerTest.kt — 4**

- OnNameChange updates name and clears nameError
- OnNameChange clears nameDuplicateError
- OnDescriptionChange truncates to 2000 chars
- OnTagSearchChange updates tagSearchQuery

**ui/mvi/store/ExerciseStateRouteArgTest.kt — 2**

- null uuid yields create-mode initial state with no loading flag
- non-null uuid yields read-mode initial state and starts loading

**ui/mvi/handler/PermanentDeleteUndoLabelRuTest.kt — 1**

- the queued undo label resolves from the real russian catalog

Suite distribution: 72/13/10/8/6/6/5/4/2/1 at the base, 76/14/10/8/6/6/6/4/2/1 after commit 2.
Counted by `@Test` and by the gate's name regex alike; there is no `@ParameterizedTest`, `@Nested` or
`@DisplayName` outside the goldens, so JUnit cases equal identities.

Today's test doubles:

- JUnit 5 in all thirteen unit suites; assertions are `assertEquals`, `assertTrue`, `assertFalse`,
  `assertNotNull` and `assertNull`, plus JUnit's `org.junit.jupiter.api.assertNull` extension in
  ClickHandlerTest;
- MockK in nine suites (all but ExerciseStateRouteArgTest): relaxed `ExerciseInteractor`,
  `ResourceWrapper`, `CameraPermission`, `Navigator`; relaxed `ExerciseRepository`, `TagRepository`,
  `ImageStorage`, `PersonalRecordRepository` and the four final use cases in
  ExerciseInteractorImplTest; `coVerify`, `coVerifyOrder` (the save writes the row before it deletes
  the old file), `slot`, `verify`;
  - ClickHandlerTest's relaxed store answers `state` from the identity's `MutableStateFlow`,
    applies `updateState`, and answers `launch` with a relaxed `Job` without running anything;
    the identities of Section B11.1's table re-stub `launch` (and `updateStateImmediate`), six
    through the shared `wireSynchronousLaunch` helper;
  - CommonHandlerTest's store applies both State lambdas and answers `launch` by running the
    action under `runBlocking` inside `supervisorScope`, then `onSuccess`, routing a throw to
    `onError`; its `Flow.launch` is the relaxed default, so the two observed flows are never
    collected;
- Robolectric in two suites (D4): `ExerciseDeferredDeleteDbTest` (`RepositoryTestEnv`'s in-memory
  Room from core:data:database-test, a MockK repository that delegates `permanentDelete` to the real
  DAO, the real kit catalog for `Undo`) and `PermanentDeleteUndoLabelRuTest` (`qualifiers = "ru"`,
  the real kit catalog for `Отменить`); both drive the deferred delete through `SnackbarManager`;
- ExerciseDirtyStateTest and ExerciseStateRouteArgTest are pure.

Abstract members, counted from each type itself: `ExerciseInteractor` 21 (`setAdhocPlan` has no
caller), `ExerciseRepository` 34, `TagRepository` 5, `PersonalRecordRepository` 4, `ImageStorage` 5,
`CameraPermission` 1, `HandlerStore` 12, `Navigator` 6, `ResourceWrapper` 5, `StringResolver` 2.

One identity asserts handler copy today: `OnTrackNowClick reports the persisted active session
progress` reads `1 of 2 exercises done` from a `ResourceWrapper` stub keyed by the progress
identifier and its two counts. The deferred-delete pair asserts kit's undo label (`Undo`,
`Отменить`) from the real catalogs. Every other message the suites see is a relaxed mock's `""`.

### B3.3 Golden and device ownership

Three golden classes, each method parameterized over `GoldenTheme`, in source order:

- `golden/ExerciseDetailGoldenTest.kt` — 15, rendering the harness's default (EN):
  `screenLoaded, screenEmpty, screenNoRecord, screenEmptyHistory, screenEmptyPlan,
  screenImageNoDescription, screenWeightless, topbar, sheetDetailMenu, sheetDetailMenuDeletable,
  recordHero, recordHeroWeightless, recordHeroMetaClamped, historyRow, historyRowRecord`;
- `golden/ExerciseDescriptionBlockGoldenTest.kt` — 3, `locale = LOCALE_RU`:
  `readWithImage, readWithPlaceholder, editable`;
- `golden/ExerciseEditGoldenTest.kt` — 6, `locale = LOCALE_RU`:
  `createEmpty, editWeighted, editWeightless, editBlankNameError, editDuplicateNameError, sheetPlanInfo`.

That is 48 render cases and 48 PNGs, all mode 100644 and 48 distinct blobs, named
`io.github.stslex.workeeper.feature.exercise.golden_<Class>_<method>_<dark|light>.png`. The goldens
compose the production screens, sheets and blocks with literal fixture strings, so no handler or
resolver reaches one. After the move the chrome comes through CMP's `stringResource` and
`pluralStringResource`: the Detail class pins the EN catalog — its section heads, `4 SESSIONS`
(the EN plural's `other` form, from `historyCount = 4` in `screenLoaded`, `screenNoRecord` and
`screenEmptyPlan`), the dock and the menu sheet — and the Description and Edit classes pin the RU
catalog, `sheetPlanInfo` the body with its `\n\n` escape among it (S11).

Two device classes under `src/androidTest`, both class-level `@Smoke`, finding nodes by test tag
and reading no copy:

    ExerciseScreenTest: edit_inCreateMode_startsFromAnEmptyInlinePlan,
        edit_inCreateMode_rendersPlanRowsWhenAdhocPlanIsPresent,
        edit_forExistingExercise_editsThePlanInlineToo
    ExerciseFormBasicsTest: f02_saveIsEnabledWithAnEmptyName_soTheBlankNameErrorIsReachable,
        f02_tappingSaveWithAValidName_dispatchesOnSaveClick

Their edges are `androidTestImplementation` of `libs.bundles.android.test`,
`libs.androidx.compose.ui.test.junit4` and `:core:ui:test-utils`, and `debugImplementation` of the
test manifest.

### B3.4 Exact feature-local resource catalog

At exit each locale owns 53 strings and 1 plural, 54 identifiers, in this file order, with the same
keys in the same order in both locales. Values are shown as they must be at exit, decoded the way
the topology gate compares them (DOM text). The baseline differs from them only in P1's 6 plural
items (EN 2, RU 4), which carry a bare `%d` today and become `%1$d` in commit 3.

| # | Identifier (all `feature_exercise_…`) | EN | RU |
| ---: | --- | --- | --- |
| 1 | detail_recent | History | История |
| 2 | detail_track_now | Log now | Записать сейчас |
| 3 | detail_default_plan | Default plan | План по умолчанию |
| 4 | track_now_default_training_name | Track now | Отдельная тренировка |
| 5 | track_now_conflict_unnamed | Workout | Тренировка |
| 6 | track_now_conflict_progress_format | %1$d of %2$d exercises done | %1$d из %2$d упражнений выполнено |
| 7 | detail_edit | Edit | Изменить |
| 8 | detail_action_more | More actions | Ещё |
| 9 | detail_archive | Archive | В архив |
| 10 | detail_archive_success_format | ‘%1$s’ archived | «%1$s» в архиве |
| 11 | detail_archive_blocked_title | Cannot archive | Нельзя архивировать |
| 12 | detail_archive_blocked_used_in_format | used in %1$s | в тренировках: %1$s |
| 13 | detail_archive_blocked_next_step | Remove it from those trainings first, then archive. | Сначала уберите его из этих тренировок, затем архивируйте. |
| 14 | detail_archive_blocked_ok | OK | ОК |
| 15 | detail_archive_undo | Undo | Восстановить |
| 16 | detail_permanent_delete | Delete permanently | Удалить навсегда |
| 17 | detail_permanent_delete_confirm_title | Delete ‘%1$s’ permanently? | Удалить «%1$s» навсегда? |
| 18 | detail_permanent_delete_confirm_body | This exercise has no history. This cannot be undone. | У упражнения нет истории. Действие необратимо. |
| 19 | detail_permanent_delete_confirm_impact | No history affected | История не затронута |
| 20 | detail_permanent_delete_confirm_button | Delete | Удалить |
| 21 | detail_permanent_delete_success | Exercise deleted | Упражнение удалено |
| 22 | edit_title_create | New exercise | Новое упражнение |
| 23 | edit_title_edit | Edit exercise | Изменить упражнение |
| 24 | edit_close_description | Close | Закрыть |
| 25 | edit_label_name | Name | Название |
| 26 | edit_label_tags | Tags | Теги |
| 27 | edit_label_description | Description | Описание |
| 28 | edit_placeholder_description | Optional notes — technique, gym-specific details | Заметки — техника, особенности зала |
| 29 | edit_tag_limit | Maximum 10 tags per exercise | Максимум 10 тегов на упражнение |
| 30 | edit_error_name_required | Name is required | Введите название |
| 31 | edit_error_name_duplicate | Exercise with this name already exists | Упражнение с таким именем уже есть |
| 32 | edit_plan_info_body | This is the plan for when the exercise is performed on its own — from its card, with the “Log now” button, outside any training.\n\nInside a training every exercise has a plan of its own: it is set in the training editor and does not touch this one. If neither plan exists, sets are added right in the session. | Это план на случай, когда упражнение выполняется само по себе — из его карточки, кнопкой «Записать сейчас», без тренировки.\n\nВнутри тренировки план у каждого упражнения свой: он задаётся в редакторе тренировки и этот план не трогает. Если планов нет ни там, ни тут, подходы добавляются прямо в сессии. |
| 33 | edit_plan_info_description | What the default plan is for | Что такое план по умолчанию |
| 34 | edit_plan_set_type_tooltip | Tap to cycle: warmup → work → failure → drop | Нажмите, чтобы переключить: разминка → рабочий → отказ → дроп |
| 35 | edit_plan_type_change_weightless_title | Switch to weightless? | Переключить на без веса? |
| 36 | edit_plan_type_change_weightless_body | Weight values from this exercise’s plans will be cleared. This cannot be undone. | Значения веса из планов этого упражнения будут очищены. Это нельзя отменить. |
| 37 | edit_plan_type_change_weightless_impact | All plan weights cleared | Все веса в планах очищены |
| 38 | edit_plan_type_change_weightless_confirm | Switch | Переключить |
| 39 | edit_plan_set_removed | Set removed | Подход удалён |
| 40 | edit_tags_counter | %1$d of %2$d | %1$d из %2$d |
| 41 | image_thumb_description | Attached exercise photo | Прикреплённое фото упражнения |
| 42 | image_thumb_open_description | Open photo full screen | Открыть фото на весь экран |
| 43 | image_thumb_pick_description | Add a photo | Добавить фото |
| 44 | image_placeholder_description | No exercise photo attached | Фото упражнения не прикреплено |
| 45 | image_source_dialog_title | Choose source | Выберите источник |
| 46 | image_source_camera | Take photo | Сделать фото |
| 47 | image_source_gallery | Choose from gallery | Выбрать из галереи |
| 48 | image_permission_denied_title | Camera permission denied | Доступ к камере запрещён |
| 49 | image_permission_denied_body | To take a photo, grant camera access in app settings. You can still choose an existing photo from your gallery. | Чтобы сделать фото, разрешите доступ к камере в настройках приложения. Вы можете выбрать фото из галереи. |
| 50 | image_permission_denied_action_settings | Open settings | Открыть настройки |
| 51 | image_error_save_failed | Could not save image — try again | Не удалось сохранить фото — попробуйте ещё раз |
| 52 | image_error_load_failed | Could not load image | Не удалось загрузить фото |
| 53 | image_error_decode_failed | Image format not supported | Формат изображения не поддерживается |
| 54 | detail_history_count | plural, below | plural, below |

`‘ ’` are U+2018 and U+2019 in EN rows 10 and 17 (the character references `&#8216;` / `&#8217;`),
and `« »` the RU counterparts; `’` is U+2019 in EN row 36; `“ ”` are U+201C and U+201D in EN row 32;
`—` is U+2014 in rows 28, 32 and 51 of both locales; `→` is U+2192 in row 34. Row 32's `\n\n` is the
two-character escape, twice, in both locales, as the XML holds it: Android resources and CMP 1.11.1's
`handleSpecialCharacters` both render it as two line breaks (S11).

| Plural | EN one / other | RU one / few / many / other |
| --- | --- | --- |
| detail_history_count | `%1$d session` / `%1$d sessions` | `%1$d сессия` / `%1$d сессии` / `%1$d сессий` / `%1$d сессии` |

The XML moves byte-semantically, except P1: the EN root keeps its `xmlns:tools`, rows 4 and 6 keep
their element-level `tools:ignore` (`DuplicateStrings`, `PluralsCandidate`), the RU root keeps its
namespace, every comment and blank line may stay, and the `\n` escapes and character references stay
as they are. Compose Resources ignore `tools:` attributes (7.14 Section 3.4).

At exit, composition reads 38 identifiers (37 strings and the plural) and the two handlers read the
other 16 outside composition (Section B5.4): ClickHandler 9 (rows 4, 5, 6, 10, 12 and 17–20),
CommonHandler 7 (rows 21, 29 and 35–39, S2) plus kit's `core_ui_kit_toast_undo`. Nothing outside the
module reads these identifiers; only `graphify-out/`'s generated graph and historical specs name
some of them as text. `FEATURE_RESOURCE_OWNERS` pins rows 34–39 at the module's resource root (S14).
The generated `Res` stays private.

### B3.5 Declared dependencies

Today's build file declares:

- `convention.composeLibrary`, Metro (`includeJavax()`, which keeps `@DefaultDispatcher` and
  `@MainImmediateDispatcher` distinct as binding keys) and Paparazzi;
- `implementation` edges on `core:core`, `core:ui:kit`, `core:ui:mvi`, `core:ui:navigation`,
  `core:ui:plan-editor`, `core:data:database` and `core:data:exercise`, and
  `implementation(libs.kotlinx.serialization.json)`, which nothing in the module imports;
- `testImplementation` of `kotlin("test")`, `:core:ui:golden-harness` and
  `:core:data:database-test` (D4's witness only);
- the device edges of Section B3.3.

Coil reaches the module through the classic convention's `compose` bundle (`coil-compose`), which
the KMP convention does not add; image-viewer declares `implementation(libs.coil.compose)` in
commonMain (S7). The `core:data:database` edge is production: `ExerciseDomainMapper` maps
`PlanSetDataModel` and `SetTypeDataModel` (plan-editor's and single-training's precedent). After D4
no test reads `core:data:database` or `core:data:database-test`.

### B3.6 External consumers and root identity

| Surface | Ownership |
| --- | --- |
| Module edges | app/app `implementation` (stays); app/common `implementation` → `api` |
| Navigation registration | app/common `AppNavigationHost` calls `exerciseGraph(modifier = …)`, tag `ExerciseGraph` |
| Graph aggregation | app/app `AppGraph` (gains the accessor; its `cameraPermission` accessor, Part A's, stays) |
| Extension identities | app/app test `ExerciseExtensionIdentityTest`, through `asContribution` today, 6 identities (below) |
| Direct composition | none: no app test composes `exerciseGraph()` directly |
| Device journeys by tag | `RouteReachabilityTest` (class-level `@Regression`, 15 identities): `exerciseOpensForCreateFromAllExercisesAndTheListReturns` (a null route argument), `exerciseOpensFromASeededRowAndTheListReturns` (a non-null one) and `imageViewerOpensFromASeededExerciseImageAndTheExerciseReturns`; `NavigationResultTest` (`@Regression`, 3): `imageReplaceRequestReachesTheExerciseThatOpenedTheViewer` and `imageRemoveRequestTurnsTheThumbnailIntoThePhotoPickerEntryPoint`, the device proof of the result read-back (G1); `StoreRetentionTest` (3): its two exercise-chart identities pass the detail; `BackStackStateRestorationTest.editorDraftSurvivesTheImageViewerRoundTrip` (`@Regression`) |
| Route | `Screen.Exercise(uuid: String?)`, core:ui:navigation commonMain, unchanged |

The six extension identities, each of which calls the helper `.exercise("ex-1")` once, the fifth
also `.exercise("ex-2")`, and the sixth `.exercise(null)` instead:

1. extension resolves the store through the parent graph
2. store's app-scoped deps are the SAME instances the parent holds
3. both qualified dispatchers are inherited and stay two distinct keys
4. the camera permission is inherited from the parent graph
5. each extension carries its own route arg into the store state
6. a null route arg survives the binding and reaches the store as null

The third pins `@DefaultDispatcher` and `@MainImmediateDispatcher` as the parent's instances and as
two distinct keys; the fourth reads `ExerciseGraph.cameraPermission` against `AppGraph`'s, which is
why the graph's public surface names a `core:core` type (Section B10).

### B3.7 Context readers and PNG integrity

Four executable `Context.appDeps` readers exist (`EXPECTED_APP_DEPS_READERS`): app-dialogs and the
exercise, live-workout and settings features. Removing exercise's leaves three.

PNG manifests at the measured tree, 7.10 Section 3.7's method, reproduced from git objects. They
equal 7.14's projection (its Section 3.7) exactly: single-training's 12 PNGs moved byte-identically,
and nothing since has touched a PNG.

| Manifest | Entries | Hash |
| --- | ---: | --- |
| Paparazzi mode/blob/path | 456, 13 owners | f903e721e571699b5138ea7363bb9109dce0583400a4057d5daed91c0e540a1f |
| Paparazzi path list | 456 | f19c856d18f8b4d836a5d7bc73571443deaaebb3ac1d41f9f45fa51ca97a9cab |
| All repository PNGs, mode/blob/path | 528 | 7cc28d134cb89a24a189a7b17a05a9d1fba95db0c7b3213f93dccbfe253e2df9 |
| exercise blob set (48 sorted blob IDs, duplicates kept) | 48 | 0e4ba8746577ab0198e29fc09e58f7fd145d41dbf6a6cc01489dfcd09b5a6353 |

If only the 48 paths move from `src/test/snapshots/images/` to `src/androidHostTest/snapshots/images/`,
the projections are:

- 0af65bda8c6cbb233e25c3c186ad600894f642670eeb988d58d8a2d0152b354a (Paparazzi mode/blob/path);
- aa39f858fd2c25147e88bc4c6a73d5ff3a9b0ba0132fd2d0934326e30bc78634 (path list);
- 73df6dd5d4b020aab59d2f28f76d3f9112dcabed9129ce99442c16399ed1242a (all PNGs).

The owner count stays 13. **The normative check is relative, as in 7.10:** at implementation entry,
take the base manifests; rewrite exactly the 48 exercise paths from `src/test/` to
`src/androidHostTest/`; require the head manifests to equal the rewritten base byte for byte; require
the blob-set hash to be unchanged.

### B3.8 Measurement limitation

The measurement ran no Gradle task, no Native task and no device test. The implementation records
fresh entry gates (Section B12) and stops if the environment for any of them is missing.

## B4. Platform/API blockers — settled decisions

### B4.1 Android resources become private Compose resources

7.10 Section 4.2 applies with the package of Section B0.3. No `StringResource`,
`PluralStringResource`, Android id, `ResourceWrapper` or `StringResolver` enters State, Action, Event,
UI models, domain models or repository APIs. S2's `ExerciseClickCopy` holds plain strings.
`ExerciseTypeUiModel.labelRes`, which the read screen passes to `stringResource`, is core:ui:plan-editor's
`StringResource` on kit's catalog, as today.

### B4.2 Android and JVM APIs leave common

| Today | At exit |
| --- | --- |
| `LocalContext` + `context.appDeps<…>()` | explicit factory, shape B (Section B6) |
| Coil's `ImageRequest.Builder(LocalContext.current)` | `ImageRequest.Builder(LocalPlatformContext.current)`, `import coil3.compose.LocalPlatformContext` (image-viewer's form), through `implementation(libs.coil.compose)` |
| `androidx.annotation.VisibleForTesting` on `NAME` | removed; `NAME` stays private |
| `R`, `androidx.compose.ui.res.stringResource` / `pluralStringResource` | generated `Res`, `org.jetbrains.compose.resources.stringResource` / `pluralStringResource` in composition (Section B5.5) |
| `ResourceWrapper.getString` (17 sites) and CMP's top-level `getString` (1) | `StringResolver`: 9 on use in ClickHandler's launches, 8 once per Store in CommonHandler's `Init` (S2, Section B5.4) |
| `ResourceWrapper.formatDayMonth` / `formatMediumDate` | unchanged (S4), the day-month date now before the State lambda (S3) |
| `androidx.activity.compose.BackHandler` | `androidx.compose.ui.backhandler.BackHandler`, `exerciseGraph` opting in to `ExperimentalComposeUiApi` beside `ExperimentalSharedTransitionApi` (7.14's form) |
| `System.currentTimeMillis()` × 2 | `Clock.System.now().toEpochMilliseconds()` (`kotlin.time.Clock`) |
| `uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES` | a ThemeMode subject (S6, Section B5.5) |
| `navComponentScreenWithResults(ExerciseFeature)` on the object | `navComponentScreenWithResults(ExerciseFeature(factory))` (Section B6, G1) |
| four final use cases mocked by MockK | public interfaces bound in the graph (S8, Section B5.9) |
| copy resolved on six synchronous click paths | S2's model (Section B5.2) |
| a State lambda that returns a helper's State and reaches the wrapper | S3's split (Section B5.3) |

There is no `rememberSaveable`, Parcelable, paging or `java.` token to port, and no
material-icons edge to add.

### B4.3 Native test names

None needs a suppression. 7.13 Section 4.3's rejected characters (`.` `;` `,` `(` `)` `[` `]` `{`
`}` `/` `<` `>` `:` `\` `$` `&` `~` `*` `?` `#` `|` `§` `%` `@`) appear in none of Section B3.2's
133 names. Two contain U+2014 (one of them twice) and ten an apostrophe; both have run on Native
before, and U+2014 is the only non-ASCII character among them.

`Init for read mode kicks off observe + load` is the first identity with `+` to run on Native: no
commonTest or iosTest name holds one today, and `+` is not on the rejected list. Its first Native run
is the early check of Section B12.3; a compile error, a mangled name in the XML or an oracle miss is a
STOP, never a rename (Section B14).

## B5. Resource, State, copy and preview architecture

### B5.1 Private generated resource owner

7.10 Section 5.1 applies:
`compose.resources { packageOfResClass = "io.github.stslex.workeeper.feature.exercise.resources" }`
and no `publicResClass`, so the generated `Res` is internal to the module. The five files of Section
B0.5(2) also read kit's `Res`, so they import the feature's as
`import io.github.stslex.workeeper.feature.exercise.resources.Res as ExerciseRes` (archive's,
plan-editor's, past-session's, exercise-chart's and single-training's precedent); every other file
imports the feature's `Res` unaliased, each key's accessor is imported from the resources package,
and ktlint keeps the alias import last. A line the alias pushes past detekt's 120 columns wraps, the
argument on its own line, as 7.14 Section 5.1 shows; nothing else in it changes.

### B5.2 S2 and F3 — copy before the click (required)

F3's six paths run synchronously from `consume` on Main. S2 splits them as 7.14's D2 did: the
paths that show constant copy read it from State, resolved once per Store; the one whose copy takes
an argument resolves it on use and writes it only onto the state it replaces.

**The copy holder.** A new file, `ui/mvi/model/ExerciseClickCopy.kt`:

```kotlin
// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.exercise.ui.mvi.model

import androidx.compose.runtime.Stable

/**
 * D2: the click paths' constant copy — the weight-wipe confirm, the set-removed and tag-limit
 * toasts, and the deferred delete's message and undo label — resolved once per Store by `Init`,
 * so the click paths that show it never suspend.
 */
@Stable
data class ExerciseClickCopy(
    val typeChangeTitle: String,
    val typeChangeBody: String,
    val typeChangeImpact: String,
    val typeChangeConfirm: String,
    val setRemoved: String,
    val tagLimit: String,
    val permanentDeleteSuccess: String,
    val undoLabel: String,
) {

    companion object {

        /** Until `Init`'s resolution lands; each reader needs a draft, a full chip row or a confirm. */
        val EMPTY = ExerciseClickCopy(
            typeChangeTitle = "",
            typeChangeBody = "",
            typeChangeImpact = "",
            typeChangeConfirm = "",
            setRemoved = "",
            tagLimit = "",
            permanentDeleteSuccess = "",
            undoLabel = "",
        )
    }
}
```

`State` gains, directly after `dialogState`,
`/** D2: the click paths' constant copy, resolved once per Store; they read it here. */
val clickCopy: ExerciseClickCopy,`, and `State.create` sets `clickCopy = ExerciseClickCopy.EMPTY,`
after `dialogState = DialogState.Hidden,`. State goes from 25 fields to 26. `clickCopy` takes no part
in `hasChanges`, the snapshot or `withDraftCarriedFrom`.

**The resolution** (`CommonHandler`). `processInit` calls `resolveClickCopy()` after `observeTags()`,
before the create/load branch, so both routes resolve it:

```kotlin
/**
 * D2: the click paths' constant copy, resolved once per Store, so the weight-wipe confirm, the
 * two toasts and the deferred delete read it from State and their click paths never suspend.
 */
private fun resolveClickCopy() {
    launch(
        onSuccess = { copy -> updateState { it.copy(clickCopy = copy) } },
    ) {
        ExerciseClickCopy(
            typeChangeTitle = stringResolver.getString(
                ExerciseRes.string.feature_exercise_edit_plan_type_change_weightless_title,
            ),
            typeChangeBody = stringResolver.getString(
                ExerciseRes.string.feature_exercise_edit_plan_type_change_weightless_body,
            ),
            typeChangeImpact = stringResolver.getString(
                ExerciseRes.string.feature_exercise_edit_plan_type_change_weightless_impact,
            ),
            typeChangeConfirm = stringResolver.getString(
                ExerciseRes.string.feature_exercise_edit_plan_type_change_weightless_confirm,
            ),
            setRemoved = stringResolver.getString(ExerciseRes.string.feature_exercise_edit_plan_set_removed),
            tagLimit = stringResolver.getString(ExerciseRes.string.feature_exercise_edit_tag_limit),
            permanentDeleteSuccess = stringResolver.getString(
                ExerciseRes.string.feature_exercise_detail_permanent_delete_success,
            ),
            undoLabel = stringResolver.getString(Res.string.core_ui_kit_toast_undo),
        )
    }
}
```

`Init` runs once per Store (`initialActions = listOf(Action.Common.Init)`) and again when a retained
Store's route recomposes after the image viewer (the existing `withDraftCarriedFrom` case); a second
resolution writes the same copy.

**The constant paths** (`ClickHandler`) read State, with no other change in their order of writes,
events and haptics:

- `processTypeToggle`, in the weight-wipe branch after its haptic:
  `// D2: the confirm's copy was resolved once per Store, so this click path never suspends.` then
  `val copy = current.clickCopy`, and the `TypeChangeConfirm` takes `title = copy.typeChangeTitle,`,
  `body = copy.typeChangeBody,`, `impactSummary = copy.typeChangeImpact,` and
  `confirmLabel = copy.typeChangeConfirm,`; the old comment
  `// Strings resolved outside \`updateState\` — Rule 1 of compose-state-discipline.` leaves;
- `processAdhocPlanEditorAction`: `message = current.clickCopy.setRemoved,` in
  `Event.ShowSetRemovedUndo` (it already reads `current.draftEpoch`);
- `processTagToggle` and `processTagCreate`: `message = current.clickCopy.tagLimit,` in
  `Event.ShowTagLimitReached`;
- `processConfirmPermanentDelete` reads `val current = state.value` first, takes `uuid` from it, and
  after the dialog's write:
  `// D2: both labels were resolved once per Store, so nothing suspends before the queue.`,
  `val message = current.clickCopy.permanentDeleteSuccess` and
  `val undoLabel = current.clickCopy.undoLabel`; the snackbar model takes `actionLabel = undoLabel,`.
  The `GUARD` comment, the main-immediate `workDispatcher`, `NonCancellable`, the no-op `action`,
  `onDismissed = { interactor.permanentlyDelete(uuid) }` and the Back in `onSuccess` stay.

**The confirm** (`ClickHandler.processPermanentDeleteMenuClick`) at exit:

```kotlin
private fun processPermanentDeleteMenuClick() {
    val current = state.value
    if (!current.canPermanentlyDelete) return
    sendEvent(Event.Haptic(HapticFeedbackType.ContextClick))
    val name = current.name
    // D2: the title takes the name, so the confirm's copy resolves on use, before its write.
    launch(
        onSuccess = { confirm ->
            updateState { latest ->
                // Only the menu it replaces may open it: a dismissal that landed first wins.
                if (latest.bottomSheetState == BottomSheetState.DetailMenu) {
                    latest.copy(dialogState = confirm, bottomSheetState = BottomSheetState.Hidden)
                } else {
                    latest
                }
            }
        },
    ) {
        DialogState.PermanentDeleteConfirm(
            title = stringResolver.getString(
                Res.string.feature_exercise_detail_permanent_delete_confirm_title,
                name,
            ),
            body = stringResolver.getString(
                Res.string.feature_exercise_detail_permanent_delete_confirm_body,
            ),
            impactSummary = stringResolver.getString(
                Res.string.feature_exercise_detail_permanent_delete_confirm_impact,
            ),
            confirmLabel = stringResolver.getString(
                Res.string.feature_exercise_detail_permanent_delete_confirm_button,
            ),
        )
    }
}
```

The confirm still replaces the menu sheet in one write: no intermediate state with neither is ever
written. A second tap while the copy resolves launches again; the first write opens the confirm and
the second finds no menu. `processArchiveClick` closes the menu before its own launch, and
`processEditClick` closes it in its write, so an archive or edit tapped in the window wins too.

**In commit 2** (classic, Section B15) the same shapes read through the wrapper where the exit has
the resolver: `resourceWrapper.getString(R.string.…)` for the seven feature constants in
`resolveClickCopy` and for the confirm's four, with `import io.github.stslex.workeeper.feature.exercise.R`
in CommonHandler. Kit's undo label is a Compose resource that `ResourceWrapper` cannot read, so
commit 2 already injects `StringResolver` into `CommonHandler` and reads it as
`stringResolver.getString(Res.string.core_ui_kit_toast_undo)` (kit's `Res`, unaliased in commit 2);
`StringResolver` is app-scoped and already in `AppGraph`. `ClickHandler`'s constructor does not change
in commit 2. Commit 3 swaps every other read for the resolver.

**S2's observable differences.**

1. The confirm opens one dispatch later: its copy resolves on Default and the write lands on
   Main.immediate. A dismissal of the menu sheet (scrim, drag, back, or its archive or edit item)
   that lands in that window now wins; before, the confirm replaced the sheet synchronously.
2. The constant copy follows the locale at `Init` until the Store ends, as every pre-formatted State
   field already does (7.14's D2 accepted the same). Before, each click re-read it. A toast or confirm
   fired before `Init`'s resolution lands would carry empty text; each needs a typed plan row, a full
   chip row, weighted rows or an opened confirm first, after the screen composes, so the window is
   theoretical and is accepted.
3. The deferred delete's launch no longer suspends before it queues the snackbar: today its action
   awaits CMP's `getString` for the undo label inside `NonCancellable`. The queue, then Back, is the
   order its `GUARD` comment wants; S2 removes the one suspension in front of it.

The archive's two reads, the Track-now conflict's two and the default training name already run
inside launches; they move to the resolver unchanged in place (Section B5.4).

### B5.3 S3 and F2 — the load's State lambda (required)

`CommonHandler.loadExercise`'s `onSuccess` at exit:

```kotlin
onSuccess = { result ->
    // F2: the history rows and the plan read nothing from the State, so they are built
    // before the write; the lambda keeps what reads `current`, and the copy.
    val recentHistory = result.history.map { it.toUi(resourceWrapper) }.toImmutableList()
    val adhocPlan = result.adhocPlan?.map { it.toUi() }?.toImmutableList()
    updateStateImmediate { current ->
        val exercise = result.exercise
            ?: return@updateStateImmediate current.copy(isLoading = false)
        val tags = result.labels
            .map { name ->
                val matched = current.availableTags
                    .firstOrNull { it.name.equals(name, ignoreCase = true) }
                matched ?: AppTagItem(uuid = name, name = name)
            }
            .toImmutableList()
        current.copy(
            name = exercise.name,
            type = exercise.type.toUi(),
            description = exercise.description.orEmpty(),
            tags = tags,
            recentHistory = recentHistory,
            historyCount = result.historyCount,
            isLoading = false,
            canPermanentlyDelete = result.canPermanentlyDelete,
            adhocPlan = adhocPlan,
            imagePath = exercise.imagePath,
            imageLastModified = result.imageLastModified,
            pendingImage = PendingImage.Unchanged,
            originalSnapshot = State.Snapshot(
                name = exercise.name,
                type = exercise.type.toUi(),
                description = exercise.description.orEmpty(),
                tagUuids = tags.map { it.uuid },
                adhocPlan = adhocPlan,
            ),
        ).withDraftCarriedFrom(current)
    }
    if (result.exercise != null) observePersonalRecord(uuid)
},
```

`private fun State.applyLoaded(result: LoadResult)` leaves, with the plain `// TODO: DON'T RETURN A
BIG RESULT …` comment above it (it describes the helper that leaves and names no tech-debt row). The
expressions are its own, in its order; `withDraftCarriedFrom`, `LoadResult`, the action, the
`onError` arm with its comment and `observePersonalRecord` stay as they are.

What changes in behaviour: nothing visible. The history rows and the plan are built once, in
`onSuccess`, where `applyLoaded` ran them; a compare-and-set retry no longer re-runs
`formatDayMonth`. With no exercise, the two lists are built from what the load returned and dropped,
where `applyLoaded` returned before building them. The tag match stays inside the lambda because it
reads `current.availableTags`. `observeTags` keeps its pure mapping before its lambda, and the record
flow keeps its `formatMediumDate` in the flow's `map`.

Both rewrites (S2's and S3's) are commit 2's, in `src/main`; after commit 2 every State lambda of
the module returns a `.copy(` and none calls a same-file function that resolves copy (Section B2).

### B5.4 Copy outside composition — StringResolver, and S4

**ClickHandler** at exit: constructor `interactor`, `stringResolver: StringResolver`,
`cameraPermission`, `@MainImmediateDispatcher mainDispatcher`, `store`. It holds exactly nine
`stringResolver.getString(`, names no `ResourceWrapper`, and imports no CMP top-level read:

- the archive's launch: `feature_exercise_detail_archive_success_format` with the captured name,
  and `feature_exercise_detail_archive_blocked_used_in_format` with
  `result.activeTrainings.joinToString(", ")`;
- the Track-now launch, on `NeedsUserChoice`: `feature_exercise_track_now_conflict_unnamed` when the
  name is null, and `feature_exercise_track_now_conflict_progress_format` with
  `resolution.doneCount` and `resolution.totalCount`;
- `startFreshTrackNow`: `feature_exercise_track_now_default_training_name`;
- the confirm's launch action: its four (Section B5.2).

**CommonHandler** at exit: constructor `interactor`, `resourceWrapper: ResourceWrapper`,
`stringResolver: StringResolver`, `store`. It holds exactly eight `stringResolver.getString(`
(`resolveClickCopy`) and passes the wrapper to the mapper twice (`toUi(resourceWrapper)` for the
history rows, `toUi(resourceWrapper, type.toUi())` for the record).

**ExerciseUiMapper** keeps exactly one `resourceWrapper.formatDayMonth(` and one
`resourceWrapper.formatMediumDate(`, declared through `resource_wrapper_dates`; it and `CommonHandler`
are the module's two `ResourceWrapper` readers. A day-month or medium date is a locale-aware format,
not catalog copy (S4; archive's and exercise-chart's `formatDayMonth`, past-session's and
single-training's `formatMediumDate` are the precedents). A portable date formatter is out of scope
(tech-debt.md's row gains these two reads, Section B9.2).

Each read keeps its resource and arguments: `resourceWrapper.getString(R.string.x, args…)` becomes
`stringResolver.getString(Res.string.x, args…)`. **The argument rule** (7.10 Section 5.2) applies:
the four formatted reads (the confirm's title, the archive success, the `used in` label, the
progress) pass theirs; the strict fakes (Section B11.1) and controls 20 and 53 pin it.

**Threading.** The archive's, the conflict's and the default name's reads run where they run today.
S2 moves two things: the eight constants from click time on Main to `Init`'s launch on Default, and
the confirm's four from the click on Main into a launch on Default. CMP's first catalog read in a
process is a blocking asset read, and later reads are cached; do not move the work otherwise in this
phase.

The events, `DialogState` and the UI models keep their shape and their pre-formatted strings. This
module follows tech-debt.md's "Shared (KMP) string resources" rule, so it adds no debt row or
`TODO(tech-debt)` marker for the resolver.

### B5.5 Composable copy and previews (S6)

The seven UI files of Section B3.1 resolve copy with `org.jetbrains.compose.resources.stringResource`
/ `pluralStringResource` on the private `Res`, in the same places and with the same arguments:
`ExerciseDetailScreen` (through `ExerciseRes`: the More button, the default-plan, description and
history heads, the history head's plural `pluralStringResource(…history_count, count, count)`, the
dock's edit and log-now labels; kit's `core_ui_kit_action_back` and the plan editor's type label
stay), `ExerciseEditScreen` (through `ExerciseRes`: the fallback title, a local
`StringResource` picked between the create and edit titles, the close button, the name label and
its two errors, the set-type tooltip, the plan head and its info button, the tags head and its
`tags_counter` with `state.tags.size` and `State.MAX_TAGS_PER_EXERCISE`, the description head; kit's
cancel and save stay), `ui/ExerciseGraph.kt` (through `ExerciseRes`: the archive undo label, the
three image errors, the archive-blocked dialog's three, the permission sheet's three; kit's eight
stay), `ExerciseDescriptionBlock` (the description field's label and placeholder, the thumb's
`when` over three keys, the image's description), `ExerciseDetailSheets` (the menu's three),
`ImageSourceSheetContent` (the sheet's three) and `PlanInfoSheetContent` (through `ExerciseRes`: the
title and the body; kit's close stays).

Each file loses Android's `stringResource` / `pluralStringResource` and the `R` import. In commit 2
these files do not change.

**S6, the previews.** `ImageSourceSheetContent`'s `uiMode` pair becomes the ThemeMode subject shape
(home's `RecentSessionRow`, 7.14 Section 5.5):

```kotlin
@Preview(name = "Light", showBackground = true)
@Composable
private fun ImageSourceSheetContentLightPreview() {
    ImageSourceSheetContentPreview(themeMode = ThemeMode.LIGHT)
}

@Preview(name = "Dark", showBackground = true)
@Composable
private fun ImageSourceSheetContentDarkPreview() {
    ImageSourceSheetContentPreview(themeMode = ThemeMode.DARK)
}

@Composable
private fun ImageSourceSheetContentPreview(themeMode: ThemeMode) {
    AppTheme(themeMode = themeMode) {
        …                                                  // today's preview body, unchanged
    }
}
```

and the file imports `ThemeMode`. Today's body runs under `AppTheme { … }` (the system theme); the
subject passes the mode explicitly, which is the shape's point. The 14 bare `@Preview`s stay
byte-identical: `preview_subjects` names the one subject (two `@Preview(`), `bare_previews` pins 5 in
`ui/ExerciseDetailScreen.kt`, 3 in `ui/ExerciseEditScreen.kt`, 2 in
`ui/components/ExerciseDescriptionBlock.kt`, 1 in `ui/components/ExerciseDetailSheets.kt`, 2 in
`ui/components/ExerciseHistoryRow.kt` and 1 in `ui/components/PlanInfoSheetContent.kt`, and every
other commonMain file holds none.

### B5.6 Part A's seams in the shared entry

The graph keeps Part A's wiring as it is: one `val imageSources = rememberImageSourceLauncher(…)` whose
three callbacks consume `ImagePicked(ImageRef(ref))`, `ImagePickCancelled` and
`RequestCameraCapture` / `OnCameraPermissionDenied`, and the four event routes
(`launchCamera(event.target.value)`, `launchGallery()`, `requestCameraPermission()`,
`openAppSettings()`). `ClickHandler` keeps its one `cameraPermission.isGranted()` read. On iOS the
launcher is Part A's inert actual; nothing in Part B executes it. The description block's Coil
request becomes the exact chain
`ImageRequest.Builder(LocalPlatformContext.current).data(it).crossfade(true).build()` (image-viewer's,
with the block's own `it`). `exercise_media_contract` pins all of it (Section B12.1).

### B5.7 The inherited locale property

7.9 Section 5.7 applies, and from this phase it also covers exercise's copy: the screens follow
CMP's primary-locale choice, so a device whose locale list is [uk-UA, ru-RU] shows Russian today and
English after the move. The history and record dates stay on `AndroidResourceWrapper` for
`Locale.getDefault()`, so such a device can pair English copy with a Ukrainian date, as
past-session's and single-training's do. `StringResolver` has the same property; the remedy is the
maintainer's open decision, not part of this phase.

### B5.8 Back, the clock and the dispatcher

- `BackHandler(enabled = processor.state.value.interceptBack) { processor.consume(Action.Click.OnBackClick) }`
  keeps its condition and action on CMP's `androidx.compose.ui.backhandler.BackHandler`;
  `exerciseGraph` opts in with
  `@OptIn(ExperimentalSharedTransitionApi::class, ExperimentalComposeUiApi::class)`.
- Both timestamps are `Clock.System.now().toEpochMilliseconds()` with `import kotlin.time.Clock`,
  in commit 2.
- `ClickHandler` keeps `@MainImmediateDispatcher private val mainDispatcher`, its two
  `withContext(mainDispatcher) { consume(Action.Navigation.Back) }` (archive success, create save)
  and the deferred delete's `workDispatcher = mainDispatcher`. The qualifier is commonMain; its
  binding is the parent graph's, and the third extension identity pins it.

### B5.9 S8 — the four use cases on 7.12 D2-A's seam

Each of `ArchiveExerciseUseCase`, `DeleteSessionUseCase`, `ResolveTrackNowConflictUseCase` and
`StartTrackNowSessionUseCase` becomes a public interface in its own file, holding exactly today's
`invoke` signature, with a one-line KDoc:

```kotlin
// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.exercise.domain.usecase

import io.github.stslex.workeeper.feature.exercise.domain.model.ArchiveResult

/** Archives the exercise unless an active training still uses it. */
interface ArchiveExerciseUseCase {

    suspend operator fun invoke(uuid: String): ArchiveResult
}
```

and today's class body moves to `…UseCaseImpl.kt`:
`/** The production [ArchiveExerciseUseCase], bound in \`ExerciseGraph\` (S8). */`, then
`@Inject`, `@SingleIn(ExerciseScope::class)`,
`class ArchiveExerciseUseCaseImpl internal constructor(…) : ArchiveExerciseUseCase {` with
`override suspend operator fun invoke(…)` and the body unchanged. The other three KDocs:
"Cancels a session: an ad-hoc training cascades, a library one is unlinked.", "Whether an active
session blocks Track now, and what it is." and "Starts an ad-hoc session for one exercise and returns
its uuid." `di/ExerciseGraph.kt` gains, after the handler store's `@Binds`, four:

```kotlin
@Binds
val ArchiveExerciseUseCaseImpl.bindArchiveExercise: ArchiveExerciseUseCase

@Binds
val DeleteSessionUseCaseImpl.bindDeleteSession: DeleteSessionUseCase

@Binds
val ResolveTrackNowConflictUseCaseImpl.bindResolveTrackNowConflict: ResolveTrackNowConflictUseCase

@Binds
val StartTrackNowSessionUseCaseImpl.bindStartTrackNowSession: StartTrackNowSessionUseCase
```

`ExerciseInteractorImpl` keeps its constructor text; its four parameters now name the interfaces.
The interfaces are public because a public graph's `@Binds` names them (home's precedent). No
identity changes for S8; `ExerciseInteractorImplTest`'s four use-case mocks become fakes of the
interfaces in commit 3.

## B6. Explicit generation-owned shape-B factory flow

7.11 Section 6 applies, with the substitutions of Section B0.3, exactly as 7.14 Section 6 applied it:

- `AppRootDeps` gains `val exerciseGraphFactory: ExerciseGraph.Factory` after
  `singleTrainingGraphFactory`, with the KDoc
  `/** Generation-owned factory for the exercise graph extension. */`;
- `AppGraph` overrides it, after `singleTrainingGraphFactory`;
- `App.kt` passes `exerciseGraphFactory = deps.exerciseGraphFactory` after the single-training line;
- `AppNavigationHost` takes a required `exerciseGraphFactory: ExerciseGraph.Factory` after
  `singleTrainingGraphFactory`. It calls `exerciseGraph(factory = exerciseGraphFactory, modifier = …)`,
  and the modifier chain is unchanged;
- `exerciseGraph` takes a required `factory: ExerciseGraph.Factory` as its first parameter and builds
  `navComponentScreenWithResults(ExerciseFeature(factory)) { results, processor -> … }`: the
  `FeatureAssisted` overload that hands the typed `Screen.Exercise` key to `processor(screen)` and the
  `NavResults` to the content. The rest — the result read-back, the launcher, the event handling,
  `BackHandler` (Section B5.8), the loaded-content gate, the sheets and dialogs — is unchanged;
- `ExerciseFeature` becomes an `internal class` with `private val factory: ExerciseGraph.Factory`,
  still `FeatureAssisted<ExerciseStoreProcessor, Screen.Exercise>`. Its `processor(screen)` keeps
  `@Suppress("UNCHECKED_CAST")` and `@Composable` and returns:

      rememberMetroStoreProcessor<ExerciseStoreImpl> {
          factory
              .createExerciseGraph(screen)
              .exerciseStore
      } as ExerciseStoreProcessor

- `createExerciseGraph(screen)` runs once, inside the retained `rememberMetroStoreProcessor` lambda.
  The route argument stays a `@Provides` bound instance, and `ExerciseStoreImpl` still reads
  `screen.uuid`;
- di/ExerciseGraph.kt changes only by S8's four `@Binds`: the Store, both qualified dispatchers, the
  inherited `CameraPermission` and the two existing `@Binds` stay;
- app/common's edge becomes `api(project(":feature:exercise"))`, placed after
  `api(project(":feature:single-training"))`, and its `implementation` line goes.

`LocalContext`, `appDeps`, `asContribution`, nullable fallbacks, registries and pre-admission
resolution are forbidden.

`ExerciseExtensionIdentityTest` gets the helper:

    private fun AppGraph.exercise(uuid: String?): ExerciseGraph =
        exerciseGraphFactory.createExerciseGraph(Screen.Exercise(uuid = uuid))

The `asContribution` import goes. The six names, the call sites (`.exercise("ex-1")` in the first five
identities, plus `.exercise("ex-2")` in the fifth, and `.exercise(null)` in the sixth) and every
assertion stay exact; the file stays JUnit 5 and MockK in app/app's `src/test`, and keeps its
`android.content.Context` mock for `AppGraph.Factory.create`.

No app test composes `exerciseGraph()` directly, so the app boundary is six paths (Section B9.2).

## B7. In scope

Only these:

- G1's and G2's gate change, as this PR's first commit (Section B12.0);
- S2, S3, S8, the clock port and S17's six identities, in `src/main` and `src/test` (Sections B5.2,
  B5.3, B5.8, B5.9, B11.1);
- the conversion of Sections B4–B6;
- the moves of Sections B3.1–B3.4 and P1;
- S7's rows: the removal of `@VisibleForTesting`, CMP's `BackHandler`, Coil through
  `LocalPlatformContext`, the build edges;
- the composable copy and S6's previews;
- porting the 10 suites to kotlin.test with deterministic in-file fakes, D4's two among them;
- moving the three golden suites to androidHostTest, and the two device tests to androidDeviceTest;
- one iosTest scene;
- the six app paths, the three CI paths and the five documentation paths;
- one signed, green PR that the maintainer merges.

## B8. Explicit non-goals

7.14 Section 8 applies, read with Section B0.3. In addition:

- no change to `core:ui:kit` (its launcher's code, its `StringResolver`, its catalogs) beyond
  Section B9.2's one comment line, `core:ui:plan-editor`, `core:core` (`ResourceWrapper` gets no
  iOS implementation), `core:data:exercise` or `core:data:database`;
- no portable date formatter (S4);
- no fix of the create-mode dirty check, no removal of `setAdhocPlan` and no change to the two
  `withContext(mainDispatcher)` calls (Section B17);
- no handler seam, and no change to how the Store wires its four handlers;
- no new test identity beyond S17's six and the scene, no rename of any Section B3.2 or B3.3
  identity, and no change to an inherited identity's assertions; the inherited setups that change
  are Section B11.1's list;
- no new device test identity, and no copy read added to a device test;
- no change to `TODO(tech-debt)` markers, or to the text of tech-debt.md's exercise rows beyond
  Section B9.2's edits;
- no edit to `.claude/skills/` (7.10's D4);
- no change to `graphify-out/`.

## B9. Exact allowed change boundary

### B9.1 Target module — 118 tracked paths at exit

- `build.gradle.kts`;
- 51 production files under `src/commonMain/kotlin/io/github/stslex/workeeper/feature/exercise/`
  (Section B3.1's 46 and the five it adds);
- `src/commonMain/composeResources/values/strings.xml` and `values-ru/strings.xml`;
- 10 files under `src/commonTest/kotlin/io/github/stslex/workeeper/feature/exercise/`: the suites of
  Section B3.2, at their relative paths;
- `src/androidHostTest/kotlin/io/github/stslex/workeeper/feature/exercise/golden/ExerciseDetailGoldenTest.kt`,
  `…/golden/ExerciseDescriptionBlockGoldenTest.kt` and `…/golden/ExerciseEditGoldenTest.kt`;
- 48 PNGs under `src/androidHostTest/snapshots/images/` (Section B3.3's names);
- `src/androidDeviceTest/kotlin/io/github/stslex/workeeper/feature/exercise/ExerciseScreenTest.kt` and
  `…/ExerciseFormBasicsTest.kt`;
- `src/iosTest/kotlin/io/github/stslex/workeeper/feature/exercise/ExerciseFeatureSceneIosTest.kt`.

That is 117 files under `src` plus the build file. Nothing remains under `src/main`, `src/test` or
`src/androidTest`. There is no androidMain, iosMain, shared test helper, fixture or extra catalog.

### B9.2 Root, CI, kit comment and documentation — 15 paths

App (6):

    app/app/src/main/java/io/github/stslex/workeeper/di/AppGraph.kt
    app/app/src/test/kotlin/io/github/stslex/workeeper/di/ExerciseExtensionIdentityTest.kt
    app/common/build.gradle.kts
    app/common/src/main/kotlin/io/github/stslex/workeeper/App.kt
    app/common/src/main/kotlin/io/github/stslex/workeeper/app/common/di/AppRootDeps.kt
    app/common/src/main/kotlin/io/github/stslex/workeeper/host/AppNavigationHost.kt

CI (3):

    .github/scripts/assert_kmp_ui_source_topology.py
    .github/scripts/assert_kmp_ios_smoke.py
    .github/workflows/android_build_unified.yml

Kit, one comment (1):

    core/ui/kit/src/androidMain/kotlin/io/github/stslex/workeeper/core/ui/kit/media/ImageSourceLauncher.android.kt

Its line `// Rotation between launch and result is out of scope, as it was in the graph.` is wrong:
Part A's device check (step 5) showed that a rotation with the camera in front keeps the target,
because `MainActivity` handles configuration changes itself. Commit 5 replaces that one line with
the two lines

    // A rotation keeps it (MainActivity handles configuration changes); a process death between
    // launch and result loses it (tech-debt.md, the exercise process-death row).

and changes nothing else in core:ui:kit.

Documentation (5):

    documentation/architecture.md
    documentation/ci-cd.md
    documentation/testing.md
    documentation/tech-debt.md
    documentation/feature-specs/kmp-phase-7-15-exercise-feature.md

What changes in each document. Every module count there reads fifteen.

- **architecture.md:**
  1. The shape list, item 2 (`FeatureAssisted`): the sentence naming the shared shape-B
     factory-resolution exceptions adds exercise: `… / SingleTrainingGraph.Factory /
     ExerciseGraph.Factory` through `AppNavigationHost` and `… / singleTrainingsGraph /
     exerciseGraph` into `… / SingleTrainingFeature / ExerciseFeature`, and one sentence after it:
     "Exercise registers through `navComponentScreenWithResults`, the `FeatureAssisted` overload that
     also hands the content the destination's `NavResults`."
  2. The Context-seam paragraph: exercise joins the entries that no longer use the feature-side
     seam, `AppGraph.exerciseGraphFactory` joins the accessor list, and the sentence reads "Exactly 3
     feature/dialog Context readers remain."
  3. "Localization": "The six production files that predate this rule" reads "The five production
     files …"; the D2 paragraph after the primary-locale paragraph ends "… (decision D2 of
     [kmp-phase-7-14-single-training-feature.md](feature-specs/kmp-phase-7-14-single-training-feature.md):
     single-training's `TrainingEditCopy` and its permanent-delete confirm; exercise's
     `ExerciseClickCopy` and its confirm, [kmp-phase-7-15-exercise-feature.md](feature-specs/kmp-phase-7-15-exercise-feature.md)
     S2)."
  4. The core:ui:kit and core:core module bullets that describe Part A's seams stay; nothing else in
     the document changes.
- **ci-cd.md**, the places 7.14 updated for single-training:
  - the `android_build_unified.yml` row of the workflows table ("…, exercise-chart feature,
    single-training feature, and exercise feature Native tests …, then links the same fifteen
    modules' …");
  - the KMP-shaped alias list, which adds `:feature:exercise`;
  - the Native job's module description, which adds `:feature:exercise` (all 133 portable cases plus
    the production scene: the catalog and the handlers' copy through the real `StringResolver`, the
    read screen, the editor, the three sheets, and dispatch, text input included);
  - the every-module-is-checked list;
  - the validator paragraph, which adds: "The exercise feature validator requires all 133 portable
    tuples and
    `io.github.stslex.workeeper.feature.exercise.ExerciseFeatureSceneIosTest.resourcesDetailEditorSheetsAndActionsRenderAndDispatch`
    exactly once, for exactly 134 target tuples.";
  - "All fifteen result directories";
  - "The same fifteen modules'" and `N/15 linked`.
- **testing.md:**
  1. The `AppGraph.stringResolver` bullet: exercise's `ClickHandler` and `CommonHandler` join the
     constructor-injected readers.
  2. A "Shared exercise feature" subsection after "Shared single-training feature". It covers:
     - the 133 identities on both targets, in their distribution, S17's six among them, and the
       first `+` identity on Native;
     - the store fakes and the per-identity launch behaviour of Section B11.1, the interactor,
       camera-permission and navigator fakes, and the use-case fakes S8 makes possible;
     - D4: the deferred-delete witness and the Russian undo label on recording fakes, the
       `SnackbarManager` drain each needs on Native, and where the real delete and the Russian
       catalog are owned;
     - the strict resolver fakes and the dates-only `ResourceWrapper` fakes;
     - S2's guard and Init-resolved copy as S17 pins them;
     - the module's one production `core:data:database` edge, read by the domain mapper only;
     - the scene (Section B11.4), with no `ResourceWrapper` stub, and what it does not cover
       (Section B11.5);
     - the three golden suites on androidHostTest with their 48 byte-identical PNGs: the Detail
       class pins the EN catalog through CMP, the Description and Edit classes the RU one,
       `sheetPlanInfo` the `\n\n` escape;
     - both device classes on androidDeviceTest, `ExerciseFormBasicsTest` pinned by D5's check;
     - the identity test through `appGraph.exerciseGraphFactory`, and the app journeys of Section
       B3.6, `NavigationResultTest`'s two among them as the device proof of the result read-back.
  3. "Existing UI tests": the exercise row's paths become
     `feature/exercise/src/androidDeviceTest/kotlin/io/github/stslex/workeeper/feature/exercise/ExerciseScreenTest.kt`
     and `…/ExerciseFormBasicsTest.kt`.
  4. "Visual gate — Paparazzi screenshot goldens": in both module lists, exercise moves from the
     classic modules (`src/test`) to the KMP modules (`src/androidHostTest`).
  5. The sentence naming the KMP-shaped modules that both alias spellings cover adds
     `:feature:exercise`.
  6. The Native command block under "Running tests" adds `:feature:exercise:iosSimulatorArm64Test`.
  7. The device-link block adds `:feature:exercise:linkDebugTestIosArm64`, and its comment reads
     `15/15`.
- **tech-debt.md:**
  1. Re-pointed links, the text unchanged: the 🟢 UI Mapping Boundary row for `ui/ExerciseGraph.kt`;
     the 🟢 `withDraftCarriedFrom` row for `ui/mvi/handler/CommonHandler.kt`; the 🟡
     `Event.ShowError` payload row's exercise link (`ui/mvi/store/ExerciseStore.kt`) and, in the same
     row, single-training's link, stale since Phase 7.14; the 🟡 device-test row for
     `ExerciseScreenTest.kt` (to `src/androidDeviceTest/kotlin/…`). Each `src/main/kotlin/…` becomes
     `src/commonMain/kotlin/…`. Rows that name the module in plain text with an elided path stay.
  2. In "Shared (KMP) string resources":
     - the 🟢 top-level-read row loses the exercise `ClickHandler` link and reads "These five
       production files …";
     - the 🟡 runtime RU catalog coverage row adds: "exercise's Description and Edit goldens render
       its RU catalog through CMP as well, the `\n\n` escape included
       ([kmp-phase-7-15-exercise-feature.md](feature-specs/kmp-phase-7-15-exercise-feature.md) S11)";
     - the 🟢 typed `rememberMetroStoreProcessor` row reads "the ten shared features' `*Feature.kt`
       (…, single-training, exercise)";
     - the 🟢 portable date formatter row reads "… archive's, exercise-chart's and exercise's
       `formatDayMonth`, past-session's, single-training's and exercise's `formatMediumDate` …".
  3. In "Shared (KMP) feature entries — follow-ups", the 🟢 D2-template row adds: "exercise's six
     paths follow it (`ExerciseClickCopy` and the menu-sheet-guarded confirm, Phase 7.15 S2)".
  4. In "v2.4 Design foundation — follow-ups":
     - the 🟡 "Exercise create-flow plan persistence — process-death loss" row adds, after "A process
       kill mid-edit loses the in-flight draft.": "A camera capture in flight is lost the same way:
       the launcher keeps the capture target in composition, so a process death while the camera
       is in front drops the photo and the result finds no target (`ImageSourceLauncher.android.kt`);
       a rotation does not, because `MainActivity` handles configuration changes (Phase 7.15 Part A,
       device check step 5).";
     - a new 🟡 row directly after it. Location:
       `[feature/exercise/.../ui/mvi/store/ExerciseStore.kt](../feature/exercise/src/commonMain/kotlin/io/github/stslex/workeeper/feature/exercise/ui/mvi/store/ExerciseStore.kt) \`hasChanges\``.
       Description: "**Create mode ignores typed fields in the dirty check.** `hasChanges` is
       `originalSnapshot?.matches(this) == false || isImageDirty || isAdhocPlanDirty`, and create
       mode has no snapshot, so a typed name, description, type or tag raises no discard sheet: Back
       and Cancel drop them without asking. Test scenario D-01 (documentation/test-scenarios/exercise.md)
       expects the sheet; no test automates D-01, and `ExerciseDirtyStateTest` has no create-mode case
       with a typed field. Present since `6e86f26bf` (2026-04-26); found by Phase 7.15 Part A's device
       check, kept unchanged by Part B's move. **Fix path:** a create-mode baseline for the snapshot
       term (the `State.create` defaults), one dirty-state case per field, and D-01 automated."

Known stale references after the move, deliberately untouched (historical records, or already stale
before this phase):

- `documentation/features.md`'s exercise section (its MVI paths, already stale for every ported
  feature);
- `documentation/feature-specs/exercises.md`'s catalog blocks and `src/main` paths;
- `documentation/test-scenarios/exercise.md` (scenario text; D-01's discrepancy is the new row's);
- `documentation/research/domain-boundary-audit-codex.md` (its `src/main` paths);
- tech-debt.md's ✅ resolved lines that name exercise files in plain text;
- `.claude/skills/*` (7.10's D4);
- `graphify-out/`.

A need for any other path is a STOP.

## B10. Gradle and public API contract

~~~kotlin
plugins {
    alias(libs.plugins.convention.kmpComposeLibrary)
    // Collider — two same-typed dispatchers; the route arg is a bound instance (shape B).
    alias(libs.plugins.metro)
    // Goldens for the exercise-detail surface; the harness comes from core:ui:golden-harness.
    alias(libs.plugins.paparazzi)
}

compose.resources {
    packageOfResClass = "io.github.stslex.workeeper.feature.exercise.resources"
}

// includeJavax keeps @Default and @MainImmediate distinct as binding keys.
metro {
    interop {
        includeJavax()
    }
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            api(project(":core:core"))

            api(project(":core:ui:kit"))
            api(project(":core:ui:mvi"))
            api(project(":core:ui:navigation"))
            api(project(":core:ui:plan-editor"))
            implementation(project(":core:data:database"))
            implementation(project(":core:data:exercise"))

            api(libs.cmp.ui)
            api(libs.coroutines.core)
            api(libs.kotlinx.collections.immutable)
            implementation(libs.cmp.animation)
            implementation(libs.coil.compose)
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
    // GUARD comment, as single-training's
    "androidDeviceTestImplementation"(platform(libs.androidx.compose.bom))
    "androidDeviceTestImplementation"(libs.androidx.compose.ui.test.manifest)
    // GUARD comment, as single-training's
    "androidDeviceTestImplementation"(project(":core:ui:test-utils"))
}

apply(from = "$rootDir/gradle/golden-gate.gradle.kts")
~~~

The Metro plugin's comment today reads "Collider — two same-typed dispatchers + Context; …"; Part A
removed the `Context`, so the moved comment drops "+ Context" and reads as shown. Every other comment
of the module (the Paparazzi plugin's, `includeJavax`'s) moves as it is; the classic-only
"androidTest here is screen-render only" comment and the deferred-delete witness's comment on
`database-test` leave with what they describe.

Why each visibility (an edge is `api` when a public declaration of this module names one of its types):

- `api(project(":core:core"))`: the Store contract carries `ImageRef` (`Action.Common.ImagePicked`,
  `Event.NavigateLaunchCamera`, `PendingImage`, `ImageDisplay`), `ExerciseInteractor` names
  `ImageRef` and `ImageSaveResult`, and `ExerciseGraph` exposes the inherited `CameraPermission`
  (S7);
- `api(project(":core:ui:kit"))`: the State carries `AppTagItem` and `DialogState.ArchiveBlocked`
  kit's `BlockedArchiveItem`;
- `api(project(":core:ui:mvi"))`: the Store, its handler store and Store impl extend the MVI
  contracts;
- `api(project(":core:ui:navigation"))`: `exerciseGraph` extends `NavGraphScope`, and
  `ExerciseGraph.Factory` takes a `Screen.Exercise`;
- `api(project(":core:ui:plan-editor"))`: the State carries `ExerciseTypeUiModel` and
  `PlanSetUiModel`, and the actions `PlanEditorBodyAction`;
- `implementation(project(":core:data:database"))`: production, for `ExerciseDomainMapper`'s
  `PlanSetDataModel` and `SetTypeDataModel`, which no public declaration names;
- `implementation(project(":core:data:exercise"))`: its repositories appear only in internal
  constructors (the interactor's and the use cases');
- `api(libs.cmp.ui)`: `exerciseGraph` takes a `Modifier`, and `Event.Haptic` carries a
  `HapticFeedbackType`;
- `api(libs.coroutines.core)`: the interactor returns `Flow`s, and the graph exposes two
  `CoroutineDispatcher`s;
- `api(libs.kotlinx.collections.immutable)`: the State and the UI models carry `ImmutableList`;
- `implementation(libs.cmp.animation)`: the graph's `ExperimentalSharedTransitionApi` opt-in;
- `implementation(libs.coil.compose)`: the description block's `AsyncImage` and
  `LocalPlatformContext` (image-viewer's edge).

`kotlin("test")` appears exactly twice, once in commonTest and once in iosTest.

Commit 2 changes no build file. Removed at commit 3, relative to today: `convention.composeLibrary`,
`implementation(libs.kotlinx.serialization.json)` (no file imports it), every `testImplementation`
(`:core:data:database-test` with it, D4), every `androidTestImplementation` and the
`debugImplementation` of the test manifest (replaced by the `androidDeviceTestImplementation` edges
and the BOM, single-training's shape).

Rationale comments adapted from the bullets above may be added. No comment may hold a dependency
fragment or a forbidden build token (`mockk`, `robolectric`, `androidMain`, `iosMain`,
`paging.testing`, `testImplementation(`, …).

Verify Gradle metadata rather than copying it blindly (7.8 Section 10). A dependency the measured
source does not need is removed. One it needs is a STOP-and-amend, never a silent addition.

## B11. Test and Native scene contract

### B11.1 Portable suites

The 10 portable suites move to commonTest. JUnit 5, MockK and Robolectric give way to `kotlin.test`,
`kotlinx-coroutines-test` and deterministic in-file fakes: named private classes, and no shared
helper. Android host and Native each execute exactly the 133 identities of Section B3.2, with zero
failure, error or skip, in the distribution 76/14/10/8/6/6/6/4/2/1.

7.10–7.14's Sections 11.1 and the fake rules of their Sections 19 apply. In this module that means:

- **ClickHandlerTest.**
  - A private `ExerciseHandlerStore`: `state` is the identity's `MutableStateFlow`; `updateState`
    and the update-lambda `updateStateImmediate` apply to it; `sendEvent` and `consume` record;
    `launch` follows the table below and returns a `Job`; every other member fails fast,
    `logger`, `lastAction`, `consumeOnMain`, `launchDefault` and `Flow.launch` included (no
    ClickHandler identity reaches them).
  - A private `ExerciseInteractor` (21 members): each answers what an identity sets up today
    (`saveExercise`, `saveImage`, `createTag`, `createTempCaptureRef`, `resolveTrackNowConflict`,
    `deleteImageFile`, …) and otherwise the relaxed mock's default (`Unit`, an empty list, `false`,
    `0`, `null`), and records its calls in order, so `coVerify` becomes a count and `coVerifyOrder`
    an ordered call log.
  - A private `CameraPermission` whose `isGranted()` answers the identity's value (`true` by
    default, as today) and counts its calls.
  - A private strict `StringResolver` (below), and no `ResourceWrapper`: ClickHandler holds none.
  - `ClickHandler(interactor, resolver, cameraPermission, Dispatchers.Unconfined, store)`, the
    dispatcher as today.
- **CommonHandlerTest.**
  - A private `ExerciseHandlerStore`: `state`, `updateState` and the update-lambda
    `updateStateImmediate` as above; `launch` launches on the identity's `runTest` scheduler and
    runs the action inside `supervisorScope`, then `onSuccess`, routing a throw from either to
    `onError`, as today's mock does (its GUARD comment on `supervisorScope` moves with it), and
    returns its `Job`; `Flow.launch` records and returns a `Job` without collecting, which is what
    the relaxed mock did; `consume` records (the request identities read it); every other member
    fails fast.
  - A private interactor: the six load reads and `getImageLastModified` answer what the identity
    sets up (a value or a throw) and otherwise the relaxed defaults; `observeAvailableTags` answers
    `flowOf(emptyList())` and `observePersonalRecord` an empty flow, as today; calls are recorded.
  - A private strict `StringResolver` and a `ResourceWrapper` whose `formatDayMonth` and
    `formatMediumDate` answer `""` and whose other members throw.
  - Every identity runs as `= runTest { … }` and advances the scheduler until idle before its
    assertions; none uses `runBlocking`.
- **ExerciseInteractorImplTest.** Private recording fakes for `ExerciseRepository` (34 members),
  `TagRepository` (5), `ImageStorage` (5), `PersonalRecordRepository` (4) and S8's four use-case
  interfaces, each answering what an identity sets up today and failing fast on any member no
  identity reaches; `ExerciseInteractorImpl(…, defaultDispatcher = Dispatchers.Unconfined)` as today.
  The two archive identities stub and verify the archive use case's fake, as they stub and verify
  the mock today.
- **ExerciseDeferredDeleteDbTest (D4).** The class name stays. A private `ExerciseInteractor` fake
  holds an in-memory set of row uuids; `permanentlyDelete(uuid)` removes one and is recorded; every
  member the deferred delete does not reach fails fast. `seedExercise()` adds a fresh uuid to the
  set; "the row survives" is `uuid in rows`, "deleted" is `uuid !in rows`, and the last identity's
  `getAllActive().isEmpty()` is `rows.isEmpty()`. The store fake runs every launch, the action
  inside `supervisorScope` then `onSuccess`, a throw to `onError`, and records each `consume` with
  `SnackbarManager.pendingModelCount > 0` at that moment, as today. The resolver fake answers the
  confirm's four reads (Section B5.2: `OnPermanentDeleteMenuClick` now launches them; its write finds
  no menu sheet in this suite's State and lands nothing). The State is
  `State.create(uuid).copy(canPermanentlyDelete = true, clickCopy = ExerciseClickCopy.EMPTY.copy(permanentDeleteSuccess = "Exercise deleted", undoLabel = "Undo"))`,
  the EN catalog's own values, so `assertEquals("Undo", pending.actionLabel)` stays byte-identical.
  A `@BeforeTest` drains `SnackbarManager` (`while (pendingModelCount > 0) snackbar.first()`): on
  Native one test binary is one process, and the manager is a process-wide object. The real
  delete is `ExerciseRepositoryImplDbTest`'s (`permanentDelete removes the row and the image file
  when present`, core:data:exercise).
- **PermanentDeleteUndoLabelRuTest (D4).** The class name stays; its drain stays. It keeps one
  identity, now on a recording store fake, a relaxed-default interactor fake and
  `clickCopy = ExerciseClickCopy.EMPTY.copy(undoLabel = "Отменить")`: the assertion that the queued
  label is `Отменить` stays byte-identical, and it pins that the deferred delete queues the label
  the State holds. The Russian catalog itself is `ComposeStringResolverRuTest`'s (core:ui:kit).
- **ExerciseUiMapperTest.** A private `ResourceWrapper` whose `formatDayMonth` answers
  `22 июля` and `formatMediumDate` `12 июля 2026 г.`, as today's stubs do; other members throw.
- **InputHandlerTest.** A private store fake (`state`, `updateState`); other members fail fast.
- **NavigationHandlerTest.** A private `Navigator` (6 members): `navTo` and `popBack` record; the
  other four fail fast.
- **ExerciseDirtyStateTest, ExerciseStateRouteArgTest.** `kotlin.test` assertions only.

**ClickHandlerTest's launches, per identity.** Today the relaxed store's `launch` runs nothing, and
the identities below re-stub it. S2 makes two existing identities run theirs, and S17 adds one that
captures it; every other identity runs no launch, and its fake `launch` records and returns a `Job`
without running.

| Identity | Launch behaviour |
| --- | --- |
| OnPermanentDeleteMenuClick swaps the sheet for the confirm dialog | the action, then `onSuccess` (S2: this setup is new; the assertion is not) |
| OnPermanentDeleteMenuClick surfaces the PermanentDeleteConfirm dialog when eligible | the action, then `onSuccess`, and its State also opens the menu sheet (`bottomSheetState = BottomSheetState.DetailMenu`), where production's item lives (S2: both setups are new; the assertion is not) |
| a duplicate-name save re-arms the draft's undos | from its re-stub on, the action, then `onSuccess` |
| createTag resolving to an already-selected tag does not duplicate the chip | the action, then `onSuccess` |
| a second create landing on a full draft chips nothing past the cap | the action, then `onSuccess` |
| OnTrackNowClick reports the persisted active session progress | the action only |
| saving an image removal clears the persisted path before deleting the old file | the action, then `onSuccess` |
| a rejected image removal keeps the attachment file and the staged draft | the action, then `onSuccess` |
| camera with the permission creates a capture target and launches the camera | the action, then `onSuccess` |
| camera without the permission asks for it and creates no capture target | the action, then `onSuccess` (wired, as today, so its never-call is not vacuous) |
| gallery launches the photo picker and reads no permission | the action, then `onSuccess` (wired, as today) |
| a granted permission launches the camera on a fresh capture target | the action, then `onSuccess` |
| a menu dismissed before the confirm's copy lands stays dismissed **(S17)** | captured; the identity runs it after the dismissal |

These identities run as `= runTest { … }`; the running ones launch on the test scheduler and the
identity advances it until idle, and the captured one runs when the identity completes it. A throw
from a running or captured launch is not routed anywhere: it fails the identity, as it does from
today's `runBlocking` stubs. `confirm permanent delete defers - nothing runs until the commit` keeps
running nothing: its two launches are recorded, and its `verify { store.launch(…) }` becomes "at
least one launch recorded".

**The strict StringResolver fakes.** ClickHandlerTest's answers exactly these reads:
`feature_exercise_track_now_conflict_progress_format` with `[1, 2]` → `1 of 2 exercises done`;
`feature_exercise_detail_permanent_delete_confirm_title` with exactly one argument, the identity's
`State.name` → `""`; and `…_confirm_body`, `…_confirm_impact` and `…_confirm_button` with no
argument → `""`. CommonHandlerTest's answers the eight `resolveClickCopy` reads with no argument:
`…_type_change_weightless_title` → `type-title`, `…_body` → `type-body`, `…_impact` → `type-impact`,
`…_confirm` → `type-confirm`, `feature_exercise_edit_plan_set_removed` → `set-removed`,
`feature_exercise_edit_tag_limit` → `tag-limit`, `feature_exercise_detail_permanent_delete_success`
→ `exercise-deleted`, and kit's `core_ui_kit_toast_undo` → `undo`. Both throw on any other resource
and on any read that does not pass exactly what production passes. A throw inside a CommonHandlerTest
launch is routed to `onError`, as every throw there is, so a missed read shows as
`clickCopy == ExerciseClickCopy.EMPTY` in S17's identity; no other CommonHandlerTest identity reads
`clickCopy`. Every `Init` now also runs `resolveClickCopy`, which reads nothing but the resolver, so
no inherited identity's outcome changes.

**S17 — the six identities commit 2 adds.** In commit 2 they are JUnit 5 and MockK (the store mock's
`launch` captured or run, `resourceWrapper.getString(R.string.…)` and
`stringResolver.getString(Res.string.core_ui_kit_toast_undo)` stubs where the port has the strict
resolver, Robolectric in the deferred-delete one); commit 3 ports them like the rest. Their fixture
is
`ExerciseClickCopy(typeChangeTitle = "type-title", typeChangeBody = "type-body", typeChangeImpact = "type-impact", typeChangeConfirm = "type-confirm", setRemoved = "set-removed", tagLimit = "tag-limit", permanentDeleteSuccess = "exercise-deleted", undoLabel = "undo")`,
distinct per field, so a read of the wrong field fails:

1. `a menu dismissed before the confirm's copy lands stays dismissed` (ClickHandlerTest): State with
   `uuid = "uuid-1"`, `name = "Bench"`, `canPermanentlyDelete = true` and
   `bottomSheetState = BottomSheetState.DetailMenu`; the launch is captured; `OnPermanentDeleteMenuClick`,
   then `bottomSheetState` is still `DetailMenu` and `dialogState` `Hidden`; `OnSheetDismiss`; the
   captured launch runs its action and then `onSuccess`; `dialogState` is `Hidden` and
   `bottomSheetState` `Hidden`;
2. `the type-change confirm carries the copy Init resolved` (ClickHandlerTest): create State, type
   `WEIGHTED`, one `80×8` work row and the fixture as `clickCopy`; `OnTypeToggle(WEIGHTLESS)`; the
   dialog is `TypeChangeConfirm("type-title", "type-body", "type-impact", "type-confirm")`;
3. `the set-removed toast carries the copy Init resolved` (ClickHandlerTest): Edit mode with two
   rows and the fixture; `OnAdhocPlanEditorAction(OnSetRemove(1))`; the one `ShowSetRemovedUndo`
   carries `set-removed`;
4. `both tag-limit toasts carry the copy Init resolved` (ClickHandlerTest): ten selected tags, an
   eleventh available, and the fixture; `OnTagToggle("tag-11")`, then `OnTagCreate("New")`; exactly
   two `ShowTagLimitReached`, both carrying `tag-limit`, and no launch;
5. `Init resolves the click paths' constant copy into State` (CommonHandlerTest): a create route
   (`State.create(uuid = null)`), the eight reads answering the fixture's strings; `Init`;
   `clickCopy` equals the fixture;
6. `the queued delete carries the message and the undo label Init resolved`
   (ExerciseDeferredDeleteDbTest): the suite's State with
   `clickCopy = ExerciseClickCopy.EMPTY.copy(permanentDeleteSuccess = "exercise-deleted", undoLabel = "undo")`;
   `OnConfirmPermanentDelete`; the queued model's `message` is `exercise-deleted` and its
   `actionLabel` `undo`.

They are the first identities these phases add to a D4 suite; every existing identity keeps its
name and assertions. Portable identities go from 127 to 133. Controls 32–37 turn each RED
(Section B13).

**MockK verifications become recorded-state assertions**, as in 7.10–7.14: `verify(exactly = 1)`
becomes exactly one recorded call with the same arguments; `verify(exactly = 0)` an empty record or a
count of 0; a `slot` capture the recorded value; `coVerifyOrder` the order of the recorded call log.
kotlin.test's `assertEquals`, `assertTrue`, `assertFalse`, `assertNull` and `assertNotNull` replace
JUnit's with the same argument order (ClickHandlerTest's `org.junit.jupiter.api.assertNull`
extension included); type witnesses may be needed (7.11 Section 19.8). No suite uses `assertThrows`,
`assertAll` or a delta.

**Mechanics only:** the constructions that swap a parameter (`resourceWrapper` → `resolver` in
ClickHandler; `resolver` added to CommonHandler), the conflict identity's stub becoming the strict
resolver's answer, the two permanent-delete setups above, D4's fakes and fixtures, and
`= runTest { … }` where a launch now runs on a scheduler. No other assertion changes. The inherited
casts and `!!` after `kotlin.test` contract assertions stay, together with their warnings (7.10
Section 19.7).

### B11.2 Android host: the golden suites

7.14 Section 11.2 applies to all three golden classes. `ExerciseDetailGoldenTest`,
`ExerciseDescriptionBlockGoldenTest` and `ExerciseEditGoldenTest` move to androidHostTest as they are,
JUnit 5 and the golden harness, with no change. Their 24 method names, light and dark cases,
subjects, fixture values and 48 filenames stay exact. The PNGs move with `git mv`,
`verifyPaparazziDebug` executes all 48, and `recordPaparazziDebug` is forbidden.

**S11.** The chrome now reads EN (Detail) and RU (Description, Edit) through CMP's `stringResource`
and `pluralStringResource` under Paparazzi: the Detail class's `4 SESSIONS` through CMP's EN plural
rule, and `sheetPlanInfo`'s body through CMP's handling of the catalog's `\n\n` escape — the first
`\n` in any Compose catalog. Every PNG must stay byte-identical; any pixel change is a STOP that names
the method and theme (Section B14).

Host runs at exit: `testAndroidHostTest` executes 10 suites and 133 cases; `verifyPaparazziDebug`
executes 13 suites and 181 cases (48 golden + 133) and prints
`Visual gate live: 48 golden test case(s) executed for 48 golden image(s).` The baseline's 10/127
and 13/175 (Section B12.2) differ by S17's six.

### B11.3 Android device (S12, D5)

`ExerciseScreenTest` and `ExerciseFormBasicsTest` move to androidDeviceTest unchanged. Their 5
identities, `@Smoke`, test tags, clicks, captured actions and assertions stay exact. They read no
copy, and their library test APK carries AGP's default debug certificate. `device_test` pins
`ExerciseScreenTest`; D5's `exercise_device_contract` pins `ExerciseFormBasicsTest`'s `@Smoke` and
its two method signatures, each exactly once (Section B12.1).

The device proof of the factory flow and of the result read-back is the app journeys of Section
B3.6: `RouteReachabilityTest` (whole class, 15), `NavigationResultTest` (whole class, 3),
`StoreRetentionTest` (whole class, 3) and `BackStackStateRestorationTest` (whole class, 4), run with
the command of Section B12.2.

### B11.4 Deterministic iOS production scene

Exactly one class and method, no comma:

    class ExerciseFeatureSceneIosTest
    fun resourcesDetailEditorSheetsAndActionsRenderAndDispatch()

The scene's setup, 7.14 Section 11.4's:

- `runComposeUiTest` and, inside `AppTheme` and one `setContent`, a test-held value switches between
  the production `ExerciseDetailScreen(state, consume)`, `ExerciseEditScreen(state, consume)`,
  `ExerciseDetailMenuSheetContent(canPermanentlyDelete, consume)`, `PlanInfoSheetContent(consume)`
  and `ImageSourceSheetContent(onSourceSelected)`;
- a test-held `State` drives the two screens, and `consume` records actions in one list;
  `onSourceSelected` records `source:Camera` / `source:Gallery` in a second list;
- the bounded settle stepping of all-exercises' scene (7.10 Section 19.7);
- every copy read outside composition goes through one `ComposeStringResolver()` (`resolver`); no
  fake resolver;
- no `ResourceWrapper`: every date it renders is a pre-formatted fixture label. No test double.

Fixtures: tags `AppTagItem("tag-1", "Push")` and `AppTagItem("tag-2", "Chest")`; plan
`[PlanSetUiModel(weight = 60.0, reps = 10, type = WORK)]`; record
`PersonalRecordUiModel("s-1", "80", "5", "Jul 27, 2026")`; history
`HistoryUiModel("s-1", "27 Jul", "80×5")` and `HistoryUiModel("s-2", "20 Jul", "75×5")`.

In one bounded scene it proves:

1. **Catalog:** through `resolver`, all 53 strings and both EN quantities (1 → one, 2 → other) of the
   plural resolve to the exact EN values of Section B3.4, each read as stored, with no format argument
   (55 values; for the plural that is the verbatim template). The plural is also read filled, its
   count as the one format argument, at 1 and 2: `1 session`, `2 sessions`. Row 32's value contains
   two U+000A characters in a row and no backslash: CMP's escape handling on Native.
2. **The handlers' copy** (`resolver`, with production's arguments):

   | Read | Output |
   | --- | --- |
   | E: the eight reads of `resolveClickCopy`, as an `ExerciseClickCopy` | `ExerciseClickCopy("Switch to weightless?", "Weight values from this exercise’s plans will be cleared. This cannot be undone.", "All plan weights cleared", "Switch", "Set removed", "Maximum 10 tags per exercise", "Exercise deleted", "Undo")` |
   | T: `permanent_delete_confirm_title` with `"Bench press"` | `Delete ‘Bench press’ permanently?` |
   | A: `archive_success_format` with `"Bench press"` | `‘Bench press’ archived` |
   | U: `archive_blocked_used_in_format` with `"Push Day, Leg Day"` | `used in Push Day, Leg Day` |
   | P: `track_now_conflict_progress_format` with `1`, `2` | `1 of 2 exercises done` |

3. **The read screen** (`ExerciseDetailScreen`):
   - D1, `State.create(uuid = "ex-1")` with `isLoading = false`, name `Bench press`, both tags, the
     plan, description `Pause at the bottom.`, the record, both history rows and `historyCount = 2`:
     the title `Bench press`; `ExerciseDetailBackButton` with the content description `Back` (kit's
     catalog) and a node with the content description `More actions`; `ExerciseDetailTagMeta`
     `Push · Chest`; `ExerciseDetailRecordHero`; the heads `DEFAULT PLAN` with `WEIGHTED` (`AppLabel`
     uppercases), `DESCRIPTION` and `HISTORY` with `2 SESSIONS`; `ExerciseDetailDefaultPlanCard`;
     `ExerciseDescriptionText` `Pause at the bottom.`; `ExerciseDescriptionImage` with the content
     description `No exercise photo attached` (read mode, no picture: no click action);
     `ExerciseHistoryRow_s-1` holding `27 Jul` and `80×5`, and `ExerciseHistoryRow_s-2`;
     `ExerciseEditButton` `Edit`; `ExerciseTrackNowButton` `Log now`, enabled;
   - D2, D1 with no tag, no plan, an empty description, no record, no history and
     `historyCount = 0`: no `ExerciseDetailTagMeta`, no `ExerciseDetailRecordHero`, and none of
     `DEFAULT PLAN`, `DESCRIPTION` and `HISTORY`;
   - D3, D2 with `imagePath = "native-image-model"` and `imageLastModified = 1`: `DESCRIPTION` is
     back, and `ExerciseDescriptionImage`'s click action carries the label `Open photo full screen`
     (Coil composes `AsyncImage` on Native with a model that loads nothing, image-viewer's
     precedent).
4. **The editor** (`ExerciseEditScreen`):
   - E1, D1's exercise in `Mode.Edit(isCreate = false)` with the tag `Push` only, no description
     and no image: the title `Bench press`; `ExerciseEditCloseButton` with `Close`; the field label
     `Name`; `ExerciseEditNameField` holding `Bench press`; `DEFAULT PLAN` and
     `ExerciseEditPlanInfoButton` with `What the default plan is for`; `TAGS` with `1 OF 10`;
     `AppTagFormRowSelected_tag-1` `Push` and `AppTagFormRowAdd` `+ tag` (kit's); `DESCRIPTION`;
     `ExerciseEditDescriptionField` with the placeholder
     `Optional notes — technique, gym-specific details`; `ExerciseDescriptionImage`'s click action
     labelled `Add a photo`; `ExerciseEditCancelButton` `Cancel` and `ExerciseEditSaveButton` `Save`
     (kit's);
   - E2, `State.create(uuid = null)` with `isLoading = false` and `nameError = true`: the title
     `New exercise`, the error `Name is required` and `0 OF 10`;
   - E3, E1 with an empty name and `nameDuplicateError = true`: the title `Edit exercise` and the
     error `Exercise with this name already exists`.
5. **The sheets:**
   - the menu (`ExerciseDetailMenuSheetContent`): with `canPermanentlyDelete = true`,
     `ExerciseDetailMenu_Edit` `Edit`, `ExerciseDetailMenu_Archive` `Archive` and
     `ExerciseDetailMenu_PermanentDelete` `Delete permanently`; with `false`, no
     `ExerciseDetailMenu_PermanentDelete`;
   - the plan info (`PlanInfoSheetContent`): `ExercisePlanInfoSheet` holding `Default plan` and a
     text node equal to step 1's row-32 value; `ExercisePlanInfoSheetClose` `Close` (kit's);
   - the source sheet (`ImageSourceSheetContent`): `ExerciseImageSourceSheet` holding
     `Choose source`; `ExerciseImageSourceCamera` `Take photo`; `ExerciseImageSourceGallery`
     `Choose from gallery`.
6. **Dispatch:** two exact lists, in this order. Actions:

   | Interaction | Action |
   | --- | --- |
   | D1: `ExerciseDetailBackButton` | `Click.OnBackClick` |
   | D1: the node described `More actions` | `Click.OnDetailMenuClick` |
   | D1: `ExerciseDetailRecordHero` | `Click.OnPrCardClick` |
   | D1: `ExerciseHistoryRow_s-2` | `Click.OnHistoryRowClick("s-2")` |
   | D1: `ExerciseEditButton` | `Click.OnEditClick` |
   | D1: `ExerciseTrackNowButton` | `Click.OnTrackNowClick` |
   | D3: `ExerciseDescriptionImage` | `Click.OnImageThumbnailClick` |
   | E1: `ExerciseEditCloseButton` | `Click.OnCancelClick` |
   | E1: `SetText` `Squat` into `ExerciseEditNameField` | `Input.OnNameChange("Squat")` |
   | E1: `ExerciseEditPlanInfoButton` | `Click.OnPlanInfoClick` |
   | E1: `AppTagFormRowAdd` | `Click.OnTagAddClick` |
   | E1: the `Remove tag` icon inside `AppTagFormRowSelected_tag-1` | `Click.OnTagRemove("tag-1")` |
   | E1: `SetText` `Slow.` into `ExerciseEditDescriptionField` | `Input.OnDescriptionChange("Slow.")` |
   | E1: `ExerciseDescriptionImage` | `Click.OnEditImageClick` |
   | E1: `ExerciseEditCancelButton` | `Click.OnCancelClick` |
   | E1: `ExerciseEditSaveButton` | `Click.OnSaveClick` |
   | menu, `true`: `ExerciseDetailMenu_Edit` | `Click.OnEditClick` |
   | menu, `true`: `ExerciseDetailMenu_Archive` | `Click.OnArchiveMenuClick` |
   | menu, `true`: `ExerciseDetailMenu_PermanentDelete` | `Click.OnPermanentDeleteMenuClick` |
   | plan info: `ExercisePlanInfoSheetClose` | `Click.OnSheetDismiss` |

   Source callbacks: `ExerciseImageSourceCamera` → `source:Camera`; `ExerciseImageSourceGallery` →
   `source:Gallery`.

   `SetText` is past-session's `performTextReplacement`. The test-held State does not change on
   dispatch, so each list records exactly what production dispatches. If `SetText`, a click action's
   label, or a click inside a sheet's content is unavailable on the simulator, STOP with the
   exception (Section B14).

It may use production test tags, texts, content descriptions, semantics action labels, scrolling and
deterministic fixtures. It may not add a production test tag, a platform host, a repository, a
database, a fake Store, a fake `StringResolver`, a `ResourceWrapper`, a screenshot baseline or a
second identity. It does not drive the inline plan editor's rows (core:ui:plan-editor's scene owns
them) and does not launch an image source (Part A's iOS actual is inert).

The module's Native oracle is 134 tuples: the 133 portable identities and this scene.

### B11.5 What is not covered

- RU values execute only in the Description and Edit goldens (Paparazzi, Android host); no unit test
  or scene reads `values-ru`, and the topology gate pins the RU values statically.
- S2's guard races only in commonTest's captured launch, not on a device; no identity measures the
  confirm's one-dispatch delay.
- The graph's wiring — the launcher and its four routes, snackbars, undo actions, `BackHandler`, the
  result read-back, the confirm and conflict sheets — has no scene path; the handler suites cover
  their State, `exercise_media_contract` pins the routes' text, and the app journeys of Section
  B3.6 drive the read-back on a device.
- The system camera and photo picker: Part A's device check (Section 12.2), not repeated here.
- The history and record dates run only through `AndroidResourceWrapper` on Android; no test
  formats one off-device.
- The create-mode dirty check (Section B17) is pinned by no identity, in either direction.

testing.md records these (Section B9.2).

## B12. CI ownership and positive verification

### B12.0 G1 and G2 — the screen-registration overload and foreign key prefixes (commit 1)

Commit 1 changes only `.github/scripts/assert_kmp_ui_source_topology.py`, in three places: the two
fields, the ownership walk's condition and the graph row of `required_root_fragments`. No entry,
contract, `MODULES` or `FEATURE_ENTRIES` line changes.

Reference implementation, normative for the fields, their defaults, the condition and the fragment;
formatting may differ:

~~~python
# FeatureEntry, after extra_golden_tests:
    # G1 (kmp-phase-7-15-exercise-feature.md Section B12.0): the navComponentScreen overload the
    # entry's graph registers through. The default keeps every earlier entry byte-identical.
    screen_registration: str = "navComponentScreen"
    # G2 (same section): keys that share resource_prefix but belong to another owner. The
    # ownership walk skips them; the default skips nothing, so every earlier entry is unchanged.
    foreign_resource_prefixes: tuple = ()

# check_feature_entry, the ownership walk:
            if row[1].startswith(entry.resource_prefix) and not row[1].startswith(
                entry.foreign_resource_prefixes
            ):

# check_feature_entry, required_root_fragments' graph row:
        (
            graph_path,
            [
                f"factory: {graph_factory},",
                f"{entry.screen_registration}({entry.feature_class}(factory))",
            ],
        ),
~~~

The diff is 13 insertions and 2 deletions, every added line within 100 columns.
`str.startswith(())` is `False`, so with the default the walk's condition reduces to today's; with
the default the fragment is today's string. Every message stays byte-identical.

**Proof that commit 1 changes no existing behaviour.** 7.14 Section 12.0's procedure: on the
committed tree, with the base's script saved aside as `old_gate.py` (sha256
`b6cd65ad9497fd5e04f55a33b0603b5ed0990398ae3c4d4d74b66445c2384b44` at the measured tree), under every
Python the host has:

1. GREEN: old and new outputs on the unmodified tree are byte-identical, exit code and stderr
   included, and equal to BASE_GATE_OUTPUT (712 lines at the measured tree).
2. Differential, one exact occurrence per mutation, scored by both scripts and restored byte-exactly
   (sha256 and mode) before the next, with a fresh GREEN after the last. **M** rows: old and new are
   byte-identical and RED with exactly the named lines. **G** rows: both GREEN.

   `ST` is `feature/single-training/src/commonMain/kotlin/io/github/stslex/workeeper/feature/single_training/ui/SingleTrainingGraph.kt`;
   `AR` is `feature/archive/src/commonMain/kotlin/io/github/stslex/workeeper/feature/archive/ui/ArchiveGraph.kt`;
   `SET` is `feature/settings/src/main/res/values/strings.xml`. In the messages `<ST>` and `<AR>`
   stand for those paths; the implementation writes them out.

   | # | Mutation | Result |
   | --- | --- | --- |
   | M1 | `ST`: `navComponentScreen(SingleTrainingFeature(factory))` → `navComponentScreenWithResults(SingleTrainingFeature(factory))` | both RED, identical: `feature:single-training: exact root-factory flow requires 'navComponentScreen(SingleTrainingFeature(factory))' once in <ST>` |
   | M2 | `AR`: append the line `// navComponentScreen(ArchiveFeature(factory))` | both RED, identical: `feature:archive: exact root-factory flow requires 'navComponentScreen(ArchiveFeature(factory))' once in <AR>` |
   | M3 | move single-training's EN `feature_training_picker_cancel` line into `SET`, before `</resources>` | both RED, identical: the EN catalog mismatch (`order=drifted`) and `feature:single-training: resource ownership drift for feature_training_picker_cancel; …` |
   | M4 | move exercise-chart's EN `feature_exercise_chart_empty_no_data_title` line into `SET`, before `</resources>` | both RED, identical: exercise-chart's EN catalog mismatch and its ownership drift for that key |
   | G1 | `ST`: the line `// No navComponentScreenWithResults( here: nothing reads a result back.` directly before `@OptIn(ExperimentalSharedTransitionApi::class, ExperimentalComposeUiApi::class)` | both GREEN |

   M1 and M2 pin that G1's default fragment is today's, counted once; M3 and M4 that G2's default
   walk is today's for a prefix that does and one that does not share a stem with another module;
   G1 that the overload's longer name is not mistaken for the fragment.
3. The diff of commit 1 touches only the topology script, and in it only the three places above.

A differing byte in step 1 or an M row, or a G row that is not GREEN, is a STOP.

The proof was dry-run under Python 3.11, 3.12 and 3.13 against the measured tree with G1 and G2
applied, and every row produced exactly its result. That dry run is not evidence.

### B12.1 Topology gate data (normative)

**Commit 3** carries the minimal delta that keeps its tree green (precedent 7.8 G1):

- `MODULES["feature:exercise"]` holds the 117 files under `src` of Section B9.1, after the
  single-training entry and in its format: `kotlin_source_sets` `commonMain`, `commonTest`,
  `androidHostTest`, `androidDeviceTest`, `iosTest`; `resource_dirs`
  `src/commonMain/composeResources/values` and `src/commonMain/composeResources/values-ru`;
- `EXPECTED_APP_DEPS_READERS` loses the exercise entry (4 → 3);
- S14: `FEATURE_RESOURCE_OWNERS`' line `Path("feature/exercise/src/main/res"): FEATURE_EXERCISE_RESOURCES,`
  becomes `Path("feature/exercise/src/commonMain/composeResources"): FEATURE_EXERCISE_RESOURCES,`
  (the catalog move alone turns the gate RED on this owner; control 47 is that RED).

That diff is 134 insertions and 2 deletions.

**Commit 4** appends `EXERCISE_ENTRY` and its four checks before `FEATURE_ENTRIES`, and the line
`    EXERCISE_ENTRY,` after `    SINGLE_TRAINING_ENTRY,` in that tuple. Nothing else in the script
changes: commit 4's topology diff is purely additive (684 insertions in the prototype).

| Field | Value |
| --- | --- |
| module / short_name | `feature:exercise` / `exercise` |
| root / package_dir | `feature/exercise` / `io/github/stslex/workeeper/feature/exercise` |
| build_fragments | every plugin, `packageOfResClass`, `includeJavax()`, dependency and `apply(from = …)` line of Section B10, each exactly once (26), except `implementation(kotlin("test"))`, which kotlin_test_count covers |
| kotlin_test_count | 2 |
| catalog | the 54 rows of Section B3.4, in file order, with the exit values (decoded text; `‘ ’ “ ” — → «  »` as the characters, row 32's `\n\n` as backslash-n twice); the plural's EN items `(one, other)` and RU items `(one, few, many, other)` |
| resource_prefix / foreign_resource_prefixes (G2) | `feature_exercise_` / `("feature_exercise_chart_", "feature_exercise_detail_type_")` |
| payload_files | `DialogState.kt`, `ExerciseStore.kt`, and the seven files of `ui/mvi/model/` (`ExerciseClickCopy.kt`, `HistoryUiModel.kt`, `ImageDisplay.kt`, `ImageErrorType.kt`, `ImageSourceUiModel.kt`, `PendingImage.kt`, `PersonalRecordUiModel.kt`), plus everything under domain/. The mapper and the two copy-reading handlers are deliberately absent |
| store_file / state_fields | `ui/mvi/store/ExerciseStore.kt` / the 26 fields, in order: `uuid: String?`, `mode: Mode`, `draftEpoch: Int`, `isSaving: Boolean`, `name: String`, `nameError: Boolean`, `nameDuplicateError: Boolean`, `type: ExerciseTypeUiModel`, `description: String`, `tags: ImmutableList<AppTagItem>`, `availableTags: ImmutableList<AppTagItem>`, `tagSearchQuery: String`, `recentHistory: ImmutableList<HistoryUiModel>`, `historyCount: Int`, `originalSnapshot: Snapshot?`, `isLoading: Boolean`, `canPermanentlyDelete: Boolean`, `adhocPlan: ImmutableList<PlanSetUiModel>?`, `pendingTypeChange: ExerciseTypeUiModel?`, `imagePath: String?`, `imageLastModified: Long`, `pendingImage: PendingImage`, `dialogState: DialogState`, `clickCopy: ExerciseClickCopy`, `bottomSheetState: BottomSheetState`, `personalRecord: PersonalRecordUiModel?` |
| resource_wrapper_readers | `ui/mvi/handler/CommonHandler.kt`, `ui/mvi/mapper/ExerciseUiMapper.kt` |
| resource_wrapper_dates | `{"ui/mvi/mapper/ExerciseUiMapper.kt": {"formatDayMonth": 1, "formatMediumDate": 1}}` |
| preview_subjects / preview_inventory | `ui/components/ImageSourceSheetContent.kt` → `ImageSourceSheetContent` / `{}` |
| bare_previews | Section B5.5's six files: 5, 3, 2, 1, 2, 1 |
| suppressions | the seven production annotations of Section B0.2 under their six `src/commonMain/…` paths, each file's arguments as strings in source order (`ClickHandler.kt`: `['"TooManyFunctions", "LargeClass"', '"LongMethod"']`); nothing under any test source set. The gate's message counts files: "the six inherited production" |
| test_names | the 133 of Section B3.2 under their ten commonTest paths; the scene's name under its iosTest path |
| golden_test / golden_methods | `src/androidHostTest/…/golden/ExerciseDetailGoldenTest.kt` / its 15 methods of Section B3.3, in source order |
| extra_golden_tests (7.14's D4) | `…/golden/ExerciseDescriptionBlockGoldenTest.kt`: `readWithImage`, `readWithPlaceholder`, `editable`; `…/golden/ExerciseEditGoldenTest.kt`: `createEmpty`, `editWeighted`, `editWeightless`, `editBlankNameError`, `editDuplicateNameError`, `sheetPlanInfo` |
| device_test / device_fragments | `src/androidDeviceTest/…/ExerciseScreenTest.kt` / `@Smoke`, `fun edit_inCreateMode_startsFromAnEmptyInlinePlan()`, `fun edit_inCreateMode_rendersPlanRowsWhenAdhocPlanIsPresent()`, `fun edit_forExistingExercise_editsThePlanInlineToo()`, each exactly once |
| factory names | Section B0.3; `create_args = "screen"`; feature_file `di/ExerciseFeature.kt`, graph_file `ui/ExerciseGraph.kt` |
| screen_registration (G1) | `navComponentScreenWithResults` |
| identity_test / identity_fragments | `app/app/src/test/…/di/ExerciseExtensionIdentityTest.kt` / the `exercise(uuid)` helper of Section B6, on one line (the gate compares whitespace-compacted text) |
| identity_names / identity_accessor | the six names of Section B3.6 / below |
| extra_root_fragments | none |
| extra_checks | `exercise_copy_contract`, `exercise_media_contract`, `exercise_dependency_contract`, `exercise_device_contract` below |

`identity_accessor` is the pattern `(?<!AppGraph)\.exercise\((?:"ex-1"|null)\)`. It matches six times,
once per identity; the fifth identity's `"ex-2"` call and the helper's declaration are deliberately
not counted.

`exercise_copy_contract` checks Sections B5.2–B5.4. Every count is over sources with Kotlin comments
stripped, and it reuses single-training's helpers' shape (`source_of`, `require`,
`require_parameters`, `data_class_parameters`):

1. Parameters, exactly, in `ui/mvi/store/ExerciseStore.kt`: `Haptic` `["type: HapticFeedbackType"]`;
   `ShowArchiveSuccess` `["uuid: String", "message: String"]`; `ShowTagLimitReached`
   `["message: String"]`; `ShowSetRemovedUndo` `["message: String", "set: PlanSetUiModel",
   "index: Int", "draftEpoch: Int"]`. In `ui/mvi/store/DialogState.kt`: `TypeChangeConfirm` and
   `PermanentDeleteConfirm` `["title: String", "body: String", "impactSummary: String",
   "confirmLabel: String"]`; `ActiveSessionConflict` `["sessionUuid: String",
   "activeSessionName: String", "progressLabel: String"]`. In `ui/mvi/model/ExerciseClickCopy.kt`:
   `ExerciseClickCopy` with its eight `…: String` fields in Section B5.2's order.
2. S2's defaults: `clickCopy = ExerciseClickCopy.EMPTY,` once in the store file;
   `val EMPTY = ExerciseClickCopy(` once, and each of the eight `<field> = "",` once, in the model.
3. `ui/mvi/handler/ClickHandler.kt`: exactly once each of
   `private val stringResolver: StringResolver,`, `val copy = current.clickCopy`,
   `title = copy.typeChangeTitle,`, `body = copy.typeChangeBody,`,
   `impactSummary = copy.typeChangeImpact,`, `confirmLabel = copy.typeChangeConfirm,`,
   `message = current.clickCopy.setRemoved,`, `val message = current.clickCopy.permanentDeleteSuccess`,
   `val undoLabel = current.clickCopy.undoLabel`, `actionLabel = undoLabel,`,
   `if (latest.bottomSheetState == BottomSheetState.DetailMenu) {` and
   `latest.copy(dialogState = confirm, bottomSheetState = BottomSheetState.Hidden)`; exactly twice
   `message = current.clickCopy.tagLimit,`; exactly nine `stringResolver.getString(`; and none of
   `ResourceWrapper`, `resourceWrapper` or `System.currentTimeMillis`.
4. `ui/mvi/handler/CommonHandler.kt`: exactly once each of
   `private val resourceWrapper: ResourceWrapper,`, `private val stringResolver: StringResolver,`,
   `ExerciseClickCopy(`, `onSuccess = { copy -> updateState { it.copy(clickCopy = copy) } },`,
   `undoLabel = stringResolver.getString(Res.string.core_ui_kit_toast_undo),`,
   `val recentHistory = result.history.map { it.toUi(resourceWrapper) }.toImmutableList()`,
   `val adhocPlan = result.adhocPlan?.map { it.toUi() }?.toImmutableList()` and
   `).withDraftCarriedFrom(current)`; exactly eight `stringResolver.getString(`; exactly two
   `resolveClickCopy()` (its declaration and its one call, in `processInit`); and no `applyLoaded`.
5. F2, in that file: the `recentHistory` line occurs before the `adhocPlan` line, and the next
   `updateStateImmediate { current ->` after each is the same one; otherwise
   `feature:exercise: <file> must build the history rows and the plan before the load's State lambda (F2)`.
6. 7.11's rules 4–7 over this module's layout: no commonMain import of CMP's top-level `getString` /
   `getPluralString`; under `ui/mvi/`, every `getString(` / `getPluralString(` is a
   `stringResolver.` call and no file holds `stringResource(` / `pluralStringResource(`; no other
   `ui/` file holds `getString(` / `getPluralString(`; both walks find at least one file.

The message shapes are single-training's: `<file> copy contract requires <fragment> exactly <n>;
found <m>`, `<file> <name> parameters must be exactly [...]; actual=[...]`, and so on.

`exercise_media_contract` (Section B5.6): in `ui/ExerciseGraph.kt` exactly once each of
`val imageSources = rememberImageSourceLauncher(`,
`is Event.NavigateLaunchCamera -> imageSources.launchCamera(event.target.value)`,
`Event.NavigateLaunchGallery -> imageSources.launchGallery()`,
`Event.NavigateRequestCameraPermission -> imageSources.requestCameraPermission()` and
`Event.NavigateOpenAppSettings -> imageSources.openAppSettings()`; in `ui/mvi/handler/ClickHandler.kt`
exactly one `if (cameraPermission.isGranted()) {`; in `ui/components/ExerciseDescriptionBlock.kt`
exactly one `import coil3.compose.LocalPlatformContext` and one `ImageRequest.Builder(`, and exactly
one compacted chain `ImageRequest.Builder(LocalPlatformContext.current).data(it).crossfade(true).build()`.
Messages: `<file> media contract requires <fragment> exactly once; found <n>` and
`feature:exercise: Coil request must use exactly one LocalPlatformContext/data(it)/crossfade(true)/build() chain`.

`exercise_dependency_contract` (Sections B3.5 and B10) is single-training's, with the mapper
`domain/mapper/ExerciseDomainMapper.kt`: no `project(":core:data:database-test")` in the build file;
the mapper names `core.data.database`; no other Kotlin file under `src` does; no Kotlin file under
`src/commonMain`, `src/commonTest` or `src/iosTest` names `java.` (comments stripped;
`(?<![\w.])java\.`).

`exercise_device_contract` (D5): `src/androidDeviceTest/…/ExerciseFormBasicsTest.kt` holds `@Smoke`,
`fun f02_saveIsEnabledWithAnEmptyName_soTheBlankNameErrorIsReachable()` and
`fun f02_tappingSaveWithAValidName_dispatchesOnSaveClick()`, each exactly once; otherwise
`feature:exercise: <path> must keep <fragment> exactly once`. A missing file reads as empty, so every
fragment reports.

`assert_kmp_ios_smoke.py` gains a `feature:exercise` entry, after single-training's, with
`results_dir` `feature/exercise/build/test-results/iosSimulatorArm64Test`, the known suite prefix, and
the 134 tuples of Section B11.4: the 133 portable identities under their suites' classnames
(`io.github.stslex.workeeper.feature.exercise.ui.mvi.handler.ClickHandlerTest`, `….CommonHandlerTest`,
`….ExerciseDeferredDeleteDbTest`, `….InputHandlerTest`, `….NavigationHandlerTest`,
`….PermanentDeleteUndoLabelRuTest`, `io.github.stslex.workeeper.feature.exercise.domain.ExerciseInteractorImplTest`,
`io.github.stslex.workeeper.feature.exercise.ui.mvi.mapper.ExerciseUiMapperTest`,
`io.github.stslex.workeeper.feature.exercise.ui.mvi.store.ExerciseDirtyStateTest` and
`….ExerciseStateRouteArgTest`), through `natives(…)`, and the scene's
(`io.github.stslex.workeeper.feature.exercise.ExerciseFeatureSceneIosTest`) through `native(…)`. The
names are written as UTF-8 (`ensure_ascii=False`). That is 194 insertions.

`android_build_unified.yml`, KMP iOS kit smoke job:

- `:feature:exercise:iosSimulatorArm64Test` in the simulator test command and
  `:feature:exercise:linkDebugTestIosArm64` in the device-link command, each after single-training's;
- the binary-assert loop's last line becomes
  `feature/exercise-chart feature/single-training feature/exercise; do`, `$linked/14 linked` becomes
  `$linked/15 linked`, and `feature/exercise/build/test-results/iosSimulatorArm64Test/` joins the
  upload list after single-training's;
- the job-header comment rewraps within 100 columns to "…, past-session, home," / "exercise-chart,
  single-training and exercise features' Phase-7 Native tests on the iOS" / "simulator, then a
  checked-in script requires every exact per-module XML identity.", and the two "fourteen modules'"
  comments say "fifteen modules'".

That is 9 insertions and 6 deletions. The job, step and context names stay. The build job's comment
"all 13 golden-holding modules" stays true: exercise already holds goldens.

The fifteen modules, for every command below: `:core:ui:kit`, `:core:ui:navigation`, `:core:ui:mvi`,
`:core:ui:start-mode`, `:core:ui:plan-editor`, `:feature:image-viewer`, `:feature:plan-editor`,
`:feature:archive`, `:feature:all-trainings`, `:feature:all-exercises`, `:feature:past-session`,
`:feature:home`, `:feature:exercise-chart`, `:feature:single-training`, `:feature:exercise`.

The prototype run of this data printed, on the simulated tree, with exit 0 and byte-identical under
Python 3.11, 3.12 and 3.13:

| Tree | Lines | What changed |
| --- | ---: | --- |
| after commit 1 | 712 | nothing: equal to the base |
| after commit 2 | 712 | nothing |
| after commit 3 | 832 | exercise's `MODULES` block (118 lines: `117 exact source/resource/test files` and the files); `app:common API edges and 3 remaining Context.appDeps readers are exact`; `26 commonMain catalogs walked`, exercise's EN with 9 placeholders and RU with 11 |
| after commit 4 | 833 | `exercise catalogs, semantic State, suppressions, previews, test identities, and explicit factory flow are exact` |

The oracle entry's tuples were generated from the simulated tree's names; that run is not evidence.

### B12.2 Entry gate before any edit

7.14 Section 12.2 applies, with:

- dev contains `cf91a98319bd61492ffe8eeffbd21fb79ad9a2ba` (PR #341, merged as `509bfba2d`);
  `git diff cf91a9831 <base>` touches no path of Section B9 except this document (the specification
  commit), and names every other path it touches;
- the census of Sections B2 and B3.1–B3.7 is reproduced on the base: 46 production files and 4,089
  lines; the 127 identities (72/13/10/8/6/6/5/4/2/1) by `@Test` and by the gate's name regex; 53
  strings and 1 plural per locale, identical key order; the seven production suppressions in six
  files; the four readers; 50 State lambdas, one without `.copy(`, and 7.14 D5's one hit; the four
  PNG manifests and the blob-set hash;
- the current fourteen-module Native command and oracle are green;
- the Mac's simulator runtime and Xcode first-launch state are recorded (`xcrun simctl list
  runtimes`); a missing runtime or an unfinished first launch is a STOP with the exact command for
  the maintainer.

Baseline commands, with the stable flags:

    ./gradlew :feature:exercise:assembleDebug
    ./gradlew :feature:exercise:testDebugUnitTest      # 10 suites, 127 cases; goldens excluded
    ./gradlew :feature:exercise:verifyPaparazziDebug   # 13 suites, 175 cases: 48 golden + 127
    ./gradlew :app:app:testDebugUnitTest --tests '*ExerciseExtensionIdentityTest*' --tests '*StringResolverIdentityTest*'   # 8 cases
    ./gradlew :feature:exercise:connectedDebugAndroidTest   # on the recorded serial: 5 cases
    ANDROID_SERIAL=<serial> ./gradlew :app:app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=io.github.stslex.workeeper.app.RouteReachabilityTest,io.github.stslex.workeeper.app.NavigationResultTest,io.github.stslex.workeeper.app.StoreRetentionTest,io.github.stslex.workeeper.app.BackStackStateRestorationTest --max-workers=2 --continue   # 25 cases: 15 + 3 + 3 + 4

`verifyPaparazziDebug` must print
`Visual gate live: 48 golden test case(s) executed for 48 golden image(s).`

### B12.3 Focused gates after the implementation

After commit 1: Section B12.0's proof.

After commit 2 (still `src/main`), on the committed tree:

- `:feature:exercise:testDebugUnitTest` (10 suites, 133 cases, names exactly Section B3.2, S17's six
  among them) and `:feature:exercise:verifyPaparazziDebug` (13 suites, 181 cases, and
  `Visual gate live: 48 golden test case(s) executed for 48 golden image(s).`): S2, S3 and S8 on the
  JVM first;
- the six `ExerciseExtensionIdentityTest` identities and the two `StringResolverIdentityTest`
  identities (commit 2 adds `StringResolver` to `CommonHandler`'s constructor, which the real graph
  must now satisfy);
- the module's two device classes on the recorded serial (5 cases);
- detekt alone;
- the topology script, byte-identical to its commit-1 output.

After commit 3, on the committed tree:

    python3 .github/scripts/assert_kmp_ui_source_topology.py   # 832 lines
    ./gradlew :feature:exercise:assembleDebug                  # Android and both iOS klibs
    ./gradlew :feature:exercise:testAndroidHostTest            # 10 suites, 133 cases
    ./gradlew :feature:exercise:verifyPaparazziDebug           # 13 suites, 181 cases: 48 golden + 133
    ./gradlew :feature:exercise:iosSimulatorArm64Test          # 134 tuples
    ./gradlew :app:common:assembleDebug

Then:

- `./gradlew detekt --continue` alone, on every commit's tree;
- the six identity tests, whose real graph now hands both handlers the app-scoped `StringResolver`,
  and the two `StringResolverIdentityTest` identities (fresh XML);
- on an explicit API-34 serial with animations disabled: the module's two device classes through
  `:feature:exercise:connectedDebugAndroidTest` (the KMP convention's alias onto
  `connectedAndroidDeviceTest`, 5 cases), and the four app classes with the command of Section B12.2
  (25 cases, the exercise journeys and the result read-back among them; fresh XML);
- the relative PNG proof of Section B3.7.

**Early Native check.** Run `:feature:exercise:iosSimulatorArm64Test` as soon as production and
commonTest compile for Native, before the scene and the app paths. That run is the first Native
compilation of this module's CMP `BackHandler` opt-in, Coil's `LocalPlatformContext`, the results
overload and kit's launcher call site, and the first Native run of a `+` identity: it must give the
133 identities by name in the fresh XML, `Init for read mode kicks off observe + load` among them.

After commit 4: the fifteen-module Native command (`--continue`) and the oracle, the fifteen-module
device link (`15/15 linked`), the topology script (833 lines), and detekt alone.

### B12.4 Repository and visual gates

7.14 Section 12.4 applies unchanged: assemble, lint and unit; detekt alone; `assembleDebugAndroidTest`;
`verifyPaparazziDebug`; `:lint-rules:test`; the personal-data gate; the canonical Smoke and
Regression device suites (testing.md's form: `-Dorg.gradle.jvmargs=-Xmx3g --max-workers=2
--full-stacktrace --continue`); diff hygiene; the PNG manifest check of Section B3.7; the mockup shell
gate, where runnable. Free-disk samples are taken around every load-bearing gate, and after any
disk-full event `build/test-results/*/binary` is cleared and the run repeated (7.14 Section 19.9).

### B12.5 Final diff and remote proof

7.14 Section 12.5 applies, with: the Section B9 boundary (118 module paths, 15 others); 134 Native
tuples; 48 unchanged golden blobs; the seven production suppressions and no test suppression. Record
the KMP iOS kit smoke job's duration on the final head against its 120-minute timeout, next to 7.14's.

## B13. Mandatory known-negative controls

7.14 Section 13's procedure applies: every control runs fresh GREEN → one named observable RED →
automatic exact restoration → fresh GREEN; Gradle- and device-scored controls through
`documentation/mockups/mutation_harness.py` in one-shot mode, with `--no-configuration-cache
--console=plain` inside `--task`; Python-scored controls against the main checkout's tree with a
byte-restoring copy of the mutated file in the session scratchpad, restored with `cp` and checked by
sha256 and mode (Section B0.2: no worktree); a compile-invalid mutation is never a RED where a
compiled test is the detector; no mutation is committed. Every control runs after commit 4, on its
committed tree. Where a row says "at least", the named identities must be RED and every other RED
identity is recorded. "Only" means no other identity, suite or line is RED.

Paths below are relative to
`src/commonMain/kotlin/io/github/stslex/workeeper/feature/exercise/` unless they start with `src/`,
`app/`, `feature/` or `.github/`; `…` abbreviates the module-relative path, which the implementation
writes out.

**From 7.14, with the substitutions of Section B0.3:**

| # | Mutation | Scorer: expected RED |
| ---: | --- | --- |
| 1 | delete `ui/components/ExerciseDetailSheets.kt` | topology: source manifest mismatch, `missing=[…ExerciseDetailSheets.kt]`, and `…ExerciseDetailSheets.kt must hold bare @Preview annotations but is missing` |
| 2 | copy `di/ExerciseScope.kt` into `src/main/kotlin/…/di/` | topology: manifest `extra=[…]`, legacy source set `src/main`, unexpected Kotlin-bearing source set `src/main` |
| 3 | move `ui/components/ExerciseDetailSheets.kt` to `ui/ExerciseDetailSheets.kt` | topology: manifest `missing` and `extra`; `…ui/components/ExerciseDetailSheets.kt must hold bare @Preview annotations but is missing` and `…ui/ExerciseDetailSheets.kt must hold exactly 0 bare @Preview annotation(s); found 1` |
| 4 | `import android.util.Log` in `ui/components/ExerciseDetailSheets.kt` | topology: platform API in commonMain, `'import android.'` |
| 5 | rename the EN key `feature_exercise_image_source_gallery` to `…_renamed` | topology: EN catalog mismatch (`unexpected=['feature_exercise_image_source_gallery_renamed']`, `order=drifted`), two ownership-drift lines |
| 6 | the RU value of `feature_exercise_image_source_gallery` `Выбрать из галереи` → `Выбрать из альбома` | topology: RU catalog mismatch, `order=exact` |
| 7 | move the EN `feature_exercise_image_source_gallery` into `feature/settings/src/main/res/values/strings.xml` | topology: the EN catalog mismatch, and resource ownership drift |
| 8 | `val titleRes: Int = 0,` as State's last field | topology: Store State must stay semantic |
| 9 | `resolveClickCopy`'s `setRemoved = stringResolver.getString(ExerciseRes.string.feature_exercise_edit_plan_set_removed),` → `setRemoved = resourceWrapper.getString(0),` | topology: `…CommonHandler.kt resolves exercise copy through ResourceWrapper.getString; use the private Compose resources`; `… copy contract requires 'stringResolver.getString(' exactly eight times; found 7`; `… reads copy outside the StringResolver: 'getString(' 8 time(s), 7 through stringResolver` |
| 10 | `val label = stringResolver.getString(ExerciseRes.string.feature_exercise_edit_plan_set_removed)` as the first line of the load's `updateStateImmediate { current -> … }` | topology: `updateStateImmediate in …CommonHandler.kt resolves a resource inside the State lambda: 'getString('` and `… 'Res.'`; `…CommonHandler.kt copy contract requires 'stringResolver.getString(' exactly eight times; found 9` |
| 11 | the bare `@Preview` above `ExerciseDetailMenuSheetContentPreview` → `@Preview(uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES)` | topology: forbidden `'android.content'` and `'uiMode'`; `exactly two portable previews are required; found 3`; `…ExerciseDetailSheets.kt must hold exactly 1 bare @Preview annotation(s); found 0` |
| 12 | `val context = LocalContext.current` and `context.appDeps<ExerciseGraph.Factory>()` in `ExerciseFeature` | topology: readers not the exact 3, forbidden `LocalContext` and `appDeps<`, factory invocation not inside retained creation |
| 13 | delete `val exerciseGraphFactory: ExerciseGraph.Factory` and its KDoc from `AppRootDeps` | topology: exact root-factory flow requires the accessor |
| 14 | `val graph = factory.createExerciseGraph(screen)` before `rememberMetroStoreProcessor`, whose lambda then reads `graph.exerciseStore` | topology: `factory invocation must occur exactly inside retained Store creation` |
| 15 | a second `(context.applicationContext as AppRootDepsHolder).appRootDeps()` inside the admitted `remember(currentPhase.id) { … }` in `App.kt` | device: `UiAdmissionRaceTest`, as in 7.10 |
| 16 | hoist `val deps = remember(currentPhase.id) { …appRootDeps() }` above `if (admission.granted) {` | device: `UiAdmissionRaceTest`, as in 7.10 |
| 17 | identity helper `= asContribution<ExerciseGraph.Factory>().createExerciseGraph(…)` | topology: `extension identities must not bypass AppRootDeps via asContribution`, and the identity-helper fragment |
| 18 | archive-specific; no counterpart | — |
| 19 | `processFlipToReadMode`'s `adhocPlan = snapshot.adhocPlan,` → `adhocPlan = current.adhocPlan,` | host and Native: at least `OnConfirmDiscard with FLIP_TO_READ restores the plan the edit changed` and `OnConfirmDiscard with FLIP_TO_READ restores a plan that was absent` |
| 20 | the argument rule: the Track-now progress read's `resolution.doneCount,` / `resolution.totalCount,` swapped | host and Native: `OnTrackNowClick reports the persisted active session progress`, and only it, at the strict fake |
| 21 | the scene's `setContent { … }` body → `setContent { }` | Native: the scene, at its first node assertion |
| 22 | production `ui/ExerciseDetailScreen.kt`: the back button's `onClick = { consume(Action.Click.OnBackClick) },` → `onClick = { },` | Native: the scene's action list |
| 23 | rename the scene method to `renamedSceneSubstitute` | Gradle GREEN, then the oracle: `feature:exercise: expected exactly one testcase … found 0` |
| 24 | flip (XOR 0xFF) the last byte of `…golden_ExerciseEditGoldenTest_sheetPlanInfo_light.png` | PNG assertion: blob drift, blob-set drift, the all-PNG and Paparazzi manifest drift |
| 25 | a compile-valid overlong assertion line in `src/commonTest/…/ui/mvi/handler/NavigationHandlerTest.kt` | root detekt, alone: findings on that line only |


**New in Part B:**

| # | Mutation | Scorer: expected RED |
| ---: | --- | --- |
| 26 | G1: `navComponentScreenWithResults(ExerciseFeature(factory))` → `navComponentScreen(ExerciseFeature(factory))` | topology: exactly `exact root-factory flow requires 'navComponentScreenWithResults(ExerciseFeature(factory))' once in …ui/ExerciseGraph.kt` |
| 27 | G2: `EXERCISE_ENTRY`'s `foreign_resource_prefixes` loses `"feature_exercise_detail_type_"` | topology: exactly two lines, `resource ownership drift for feature_exercise_detail_type_weighted; expected=[exercise's two catalogs], actual=[kit's two catalogs]` and the same for `…_weightless` |
| 28 | 7.14's D4: `ExerciseEditGoldenTest`'s `fun sheetPlanInfo(` → `fun sheetPlanInfoProbe(` | topology: `golden methods in src/androidHostTest/…/golden/ExerciseEditGoldenTest.kt must be exactly [...]; actual=[…, 'sheetPlanInfoProbe']` |
| 29 | 7.14's D4: copy `golden/ExerciseDescriptionBlockGoldenTest.kt` to `golden/ExerciseDescriptionBlockCopyGoldenTest.kt` (androidHostTest) | topology: manifest `extra=[…ExerciseDescriptionBlockCopyGoldenTest.kt]` and `…ExerciseDescriptionBlockCopyGoldenTest.kt renders goldens but is neither golden_test nor in extra_golden_tests` |
| 30 | S3 over F2: the load lambda's `recentHistory = recentHistory,` → `recentHistory = result.history.map { it.toUi(resourceWrapper) }.toImmutableList(),` | topology: exactly `updateStateImmediate in …CommonHandler.kt references 'resourceWrapper' inside the State lambda; resolve copy before it` |
| 31 | 7.14's D5, transitive: `processInit()` as the first line of the load's `updateStateImmediate { current -> … }` | topology: exactly `updateStateImmediate in …CommonHandler.kt calls 'processInit', a same-file function that resolves copy; resolve it before the lambda` |
| 32 | S2: delete the `resolveClickCopy()` call from `processInit` | topology: `…CommonHandler.kt copy contract requires 'resolveClickCopy()' exactly two times; found 1`; host and Native: `Init resolves the click paths' constant copy into State`, and only it |
| 33 | S2: the weight-wipe confirm's `title = copy.typeChangeTitle,` → `title = copy.typeChangeBody,` | topology: `…ClickHandler.kt copy contract requires 'title = copy.typeChangeTitle,' exactly once; found 0`; host and Native: `the type-change confirm carries the copy Init resolved`, and only it |
| 34 | S2: `message = current.clickCopy.setRemoved,` → `message = current.clickCopy.tagLimit,` | topology: `… requires 'message = current.clickCopy.setRemoved,' exactly once; found 0` and `… requires 'message = current.clickCopy.tagLimit,' exactly two times; found 3`; host and Native: `the set-removed toast carries the copy Init resolved`, and only it |
| 35 | S2: the first `message = current.clickCopy.tagLimit,` (`processTagToggle`'s) → `message = current.clickCopy.setRemoved,` | topology: `… 'message = current.clickCopy.setRemoved,' exactly once; found 2` and `… 'message = current.clickCopy.tagLimit,' exactly two times; found 1`; host and Native: `both tag-limit toasts carry the copy Init resolved`, and only it |
| 36 | S2: the deferred delete's `actionLabel = undoLabel,` → `actionLabel = message,` | topology: `…ClickHandler.kt copy contract requires 'actionLabel = undoLabel,' exactly once; found 0`; host and Native: at least `back is consumed only after the snackbar is queued with the real undo label`, `the queued delete carries the message and the undo label Init resolved` and `the queued undo label resolves from the real russian catalog` |
| 37 | S2: the confirm's guard `if (latest.bottomSheetState == BottomSheetState.DetailMenu) {` → `if (true) {` | topology: `… requires 'if (latest.bottomSheetState == BottomSheetState.DetailMenu) {' exactly once; found 0`; host and Native: `a menu dismissed before the confirm's copy lands stays dismissed`, and only it |
| 38 | S3's order: `val adhocPlan = result.adhocPlan?.map { it.toUi() }?.toImmutableList()` moves from before the load's lambda to directly after its `?: return@updateStateImmediate current.copy(isLoading = false)` line | topology: exactly `…CommonHandler.kt must build the history rows and the plan before the load's State lambda (F2)`. 7.14's D5 stays silent: `toUi` is not a same-file function, so only the order check sees this |
| 39 | S3: the load's lambda → `updateStateImmediate { current -> current.withLoaded(result, recentHistory, adhocPlan) }` | topology: `updateStateImmediate in …CommonHandler.kt must return a State copy` and `… copy contract requires ').withDraftCarriedFrom(current)' exactly once; found 0` |
| 40 | P1: EN `<item quantity="one">%1$d session</item>` → `%d session` | topology: the EN catalog mismatch, and the placeholder rule, `…values/strings.xml key feature_exercise_detail_history_count[one] token '%d'` |
| 41 | `"androidHostTestImplementation"(project(":core:data:database-test"))` after the golden-harness edge | topology: `feature:exercise: build must not depend on :core:data:database-test` |
| 42 | `import io.github.stslex.workeeper.core.data.database.sets.PlanSetDataModel` in `domain/ExerciseInteractorImpl.kt` | topology: `…ExerciseInteractorImpl.kt must not read core:data:database; only the domain mapper maps its data models` |
| 43 | `private val probe = java.util.UUID.randomUUID()` in `CommonHandlerTest` | topology: `…CommonHandlerTest.kt names java.* in shared code` |
| 44 | a 134th `@Test`, `` `a hundred-and-thirty-fourth identity nobody declared` ``, in `NavigationHandlerTest` | topology: test identities drifted, `unexpected=['a hundred-and-thirty-fourth identity nobody declared']` |
| 45 | in `ExerciseExtensionIdentityTest`, `val store = buildAppGraph().exercise("ex-1").exerciseStore` → `val store = buildAppGraph().exerciseGraphFactory.createExerciseGraph(Screen.Exercise(uuid = "ex-1")).exerciseStore` | topology: `all six extension identities must reach the graph through the accessor` |
| 46 | `AppNavigationHost`: delete `factory = exerciseGraphFactory,` from the `exerciseGraph(` call | topology: `exact root-factory flow requires 'exerciseGraph(factory = exerciseGraphFactory,' once in …AppNavigationHost.kt` |
| 47 | S14: `FEATURE_RESOURCE_OWNERS`' exercise root back to `src/main/res` | topology: `resource ownership mismatch for …` for each of the six owned keys |
| 48 | S11, RU: `feature_exercise_edit_label_tags` `Теги` → `Теги!` | `verifyPaparazziDebug`: `createEmpty`, `editWeighted`, `editWeightless`, `editBlankNameError` and `editDuplicateNameError` of `ExerciseEditGoldenTest`, both themes, and only them; topology: the RU catalog mismatch |
| 49 | S11, the escape: RU `feature_exercise_edit_plan_info_body`'s `без тренировки.\n\nВнутри` → `без тренировки.\nВнутри` | `verifyPaparazziDebug`: `sheetPlanInfo`, both themes, and only it; topology: the RU catalog mismatch |
| 50 | S11, the EN plural: `<item quantity="other">%1$d sessions</item>` → `%1$d sessionz` | `verifyPaparazziDebug`: `screenLoaded`, `screenNoRecord` and `screenEmptyPlan` of `ExerciseDetailGoldenTest`, both themes, and only them; Native: the scene's step 1; topology: the EN catalog mismatch |
| 51 | EN `feature_exercise_image_source_camera` `Take photo` → `Take photo.` | Native: the scene's steps 1 and 5; topology: the EN catalog mismatch |
| 52 | the `+` identity renamed to `` `Init for read mode kicks off observe and load` `` | topology: test identities drifted in `…CommonHandlerTest.kt`, `missing=['Init for read mode kicks off observe + load']`, `unexpected=['Init for read mode kicks off observe and load']`; Gradle GREEN, then the oracle: the `+` tuple `found 0` |
| 53 | the argument rule: the confirm's title read drops `name` (`stringResolver.getString(Res.string.feature_exercise_detail_permanent_delete_confirm_title)`) | host and Native: at least `OnPermanentDeleteMenuClick swaps the sheet for the confirm dialog`, `OnPermanentDeleteMenuClick surfaces the PermanentDeleteConfirm dialog when eligible` and `a menu dismissed before the confirm's copy lands stays dismissed`, at the strict fake's argument check |
| 54 | Part A's routes: `Event.NavigateLaunchGallery -> imageSources.launchGallery()` → `Event.NavigateLaunchGallery -> imageSources.openAppSettings()` | topology: exactly `…ui/ExerciseGraph.kt media contract requires 'Event.NavigateLaunchGallery -> imageSources.launchGallery()' exactly once; found 0` |
| 55 | Coil: `.crossfade(true)` → `.crossfade(false)` in `ui/components/ExerciseDescriptionBlock.kt` | topology: exactly `feature:exercise: Coil request must use exactly one LocalPlatformContext/data(it)/crossfade(true)/build() chain` |
| 56 | D5: `fun f02_tappingSaveWithAValidName_dispatchesOnSaveClick()` → `fun f02_tappingSaveWithAValidName_dispatchesSave()` | topology: exactly `…ExerciseFormBasicsTest.kt must keep 'fun f02_tappingSaveWithAValidName_dispatchesOnSaveClick()' exactly once` |
| 57 | S2, kit's label: `undoLabel = stringResolver.getString(Res.string.core_ui_kit_toast_undo),` → `undoLabel = stringResolver.getString(Res.string.core_ui_kit_action_cancel),` | topology: `… requires 'undoLabel = stringResolver.getString(Res.string.core_ui_kit_toast_undo),' exactly once; found 0`; host and Native: `Init resolves the click paths' constant copy into State`, and only it (the strict fake throws, the throw routes to `onError`, `clickCopy` stays `EMPTY`) |
| 58 | the clock: `timestamp = Clock.System.now().toEpochMilliseconds(),` → `timestamp = System.currentTimeMillis(),` | topology: exactly `…ClickHandler.kt copy contract requires 'System.currentTimeMillis' exactly zero times; found 1` (Python only: the mutation does not compile for Native) |

Each of the 47 Python-scored controls (the Python list below), and Section B12.0's M1–M4 and G1, was
dry-run once against the simulated exit tree under Python 3.11,
3.12 and 3.13 while this document was written. Each produced exactly its named result,
byte-identical across interpreters, without a traceback, and the restored tree gave a fresh GREEN
byte-equal to the exit output. That dry run is not evidence. The implementation's fresh runs are.

Scorers:

- **Python, the topology script as CI runs it:** 1–14, 17, 26–31, 38–47, 54–56 and 58, and the
  topology halves of 32–37, 48–52 and 57;
- **PNG:** 24;
- **Gradle:**
  - `:feature:exercise:testAndroidHostTest :feature:exercise:iosSimulatorArm64Test --continue` for
    19–22, the Gradle halves of 32–37, 50's and 51's Native halves, 53 and 57;
  - `:feature:exercise:verifyPaparazziDebug` for 48–50's golden halves;
  - the fifteen-module Native command and the oracle for 23 and 52's oracle half;
  - root detekt, alone, for 25;
- **device:** 15 and 16 (`UiAdmissionRaceTest` on the recorded serial, fresh XML).

## B14. STOP conditions

7.14 Section 14 applies, read with Section B0.3. In addition, stop if:

- #341 is not merged into dev, or the base's tree differs from the measured tree on a path Part B
  reads in a way this document does not describe;
- a census number of Section B12.2 differs from this document;
- commit 1's proof (Section B12.0) shows any differing byte, or a G row deviates;
- S2, S3 or S8 needs anything beyond Sections B5.2, B5.3 and B5.9: another production file than the
  five Section B3.1 adds, a click path that suspends for copy, a confirm write other than Section
  B5.2's compare-and-set, an `ExerciseGraph` change beyond the four `@Binds`, or a changed identity,
  stub or assertion in commit 2 beyond S17's six additions and Section B11.1's two setups;
- the copy port needs any of these: a resource handle, `ResourceWrapper` or `StringResolver` in a
  payload file; resolution or a resolver reference inside a State lambda, directly or through a
  same-file function; a change to an Event, a `DialogState` variant or a UI model beyond S2's
  `clickCopy`;
- any identity needs an assertion change, a strict fake cannot reproduce today's stubs, or a store
  fake would have to run a launch that Section B11.1 does not list;
- D4's fakes cannot keep a deferred-delete identity's assertion byte-identical;
- **S11:** any of the 48 PNG blobs changes under `verifyPaparazziDebug`. Record the method, the theme
  and the differing region: it means CMP renders the EN or RU catalog, the EN plural or the `\n\n`
  escape differently from Android resources. Never re-record;
- **the `+` identity** fails to compile for Native, runs under another name, or is missing from the
  XML or the oracle (Section B4.3). Record the XML bytes of its testcase. Never rename it;
- `navComponentScreenWithResults` with the `FeatureAssisted` overload, CMP's `BackHandler` under
  `ExperimentalComposeUiApi`, Coil's `LocalPlatformContext` / `AsyncImage`, or kit's
  `rememberImageSourceLauncher` does not resolve for iOS in commonMain: STOP-and-amend with the
  compiler output;
- the Native scene's EN catalog differs from Section B3.4 in any value, row 32 does not hold two
  line breaks, or step 2's outputs differ from its table;
- `SetText`, a click action's label, or a click inside a sheet's content cannot run on the simulator
  (Section B11.4);
- `implementation(project(":core:data:database"))` does not resolve for the iOS targets in
  commonMain, or any test source set needs `core:data:database` or `core:data:database-test`;
- Metro rejects the graph after the move, the four use-case `@Binds` need anything but public
  interfaces, or the generated extension needs a handler's constructor public;
- a module device class, `RouteReachabilityTest`, `NavigationResultTest`, `StoreRetentionTest` or
  `BackStackStateRestorationTest` fails on the recorded serial;
- the KMP iOS kit smoke job times out on any PR head;
- detekt reports anything in the moved tests or in new code. The fix is never a suppression.

A STOP produces a measured note and, where needed, an amendment proposal. It never widens the
implementation silently.

## B15. Signed, bisect-green commit plan

Five English Conventional Commits (S15, with Section B0.5(1)). Each is signed, GitHub-verified and
green on its own tree:

1. **ci(kmp): let the shared gate register through the results overload and skip foreign key
   prefixes** — G1 and G2 only (Section B12.0): the two fields, the condition and the fragment, and
   nothing else.
2. **refactor(exercise): resolve click copy before the click, split the load's State lambda and seam
   the use cases** — in `src/main` and `src/test`, before any move: S2 (Section B5.2, with
   `resourceWrapper.getString(R.string.…)` where the exit has the resolver and kit's undo label
   already through the injected resolver), S3 (Section B5.3), S8 (Section B5.9), the clock (Section
   B5.8) and S17's six identities (Section B11.1). Every existing identity, stub and assertion stays,
   except the two setups Section B11.1 lists and the constructor argument `CommonHandlerTest` gains.
   Nothing else changes.
3. **refactor(kmp): share exercise feature entry** — the module (Sections B3–B11) and the six app
   paths, and the minimal topology delta that keeps this tree green (Section B12.1): the `MODULES`
   entry, the reader removed from `EXPECTED_APP_DEPS_READERS`, and S14's owner root.
4. **ci(kmp): gate shared exercise feature** — `EXERCISE_ENTRY` with its four checks (D5's among
   them), the Native oracle entry, and the workflow lists and comments.
5. **docs(kmp): record Phase 7.15 Part B evidence** — this document's Status line, which becomes
   `**Status:** COMPLETE — Part A by PR #341, Part B by the PR whose merge brought this line to dev.`;
   Section B19; the documentation edits of Section B9.2; and the kit comment of Section B9.2.
   Sections 0–14 and B0–B18 stay byte-identical.

Each commit is made first. Its gates then run on the committed tree, and so do the controls (after
commit 4) and the repository gates. A fix is a new signed commit inside Section B9, and every gate it
can reach is re-run. Nothing is amended, squashed or reordered.

## B16. Exit criteria

7.14 Section 16 applies, with:

- commit 1's proof, and commit 2's green classic module with 133 identities;
- the 118-path module and its two private catalogs;
- 54 identifiers, changed only by P1's 6 values;
- copy outside composition only through `StringResolver`, `ResourceWrapper` only for the two dates
  (the copy contract and the allowance), no click path that suspends for copy, and the confirm landing
  only on the menu sheet it replaces (S2);
- no State lambda that resolves copy, references a resolver or calls a same-file copy helper, and
  every one returning a `.copy(` (S3, 7.14's D5);
- 133 identities on host and Native, the `+` one among them, and 134 Native tuples;
- 48 golden cases with unchanged blobs, all three classes pinned, and the relative PNG manifest proof;
- 5 device identities from androidDeviceTest, both classes pinned, and the 25 app identities of
  Section B12.2;
- the seven production suppressions and no test suppression;
- the explicit shape-B factory flow through `navComponentScreenWithResults`, with all six identities
  through the accessor;
- `core:data:database` one production `implementation` edge, read by the domain mapper alone, and no
  `core:data:database-test` edge;
- the topology entry, oracle and workflow at fifteen modules;
- readers 4 → 3;
- every control of Section B13 evidenced;
- the Section B9.2 documents and the kit comment updated.

## B17. Remaining ordered roadmap

After Part B the frontier is remeasured. By today's scan:

- **live-workout** next: the logging seam first (7.14's follow-up), then eleven State lambdas through
  `LiveSetMutator` without `.copy(`, four dialog paths on the D2 template (three constant-only, one
  with live counts), two handler-to-handler injections (7.13's D2 seam), the `@StringRes` model, four
  golden classes on 7.14's D4, and its tests on `database-test`; it also registers through
  `navComponentScreenWithResults`, which G1 now expresses;
- **settings**, after `feature:app-dialogs:api` becomes KMP, with seams for Drive sign-in (Part A's
  launcher pattern fits its `IntentSender`), `Formatter`, `DateUtils`, `java.text.DateFormat` and
  `java.util.UUID`.

Then come app/common and the permanent iOS host. A throwaway iosApp remains forbidden.

Separate follow-ups, outside this phase:

- **The create-mode dirty check** (tech-debt.md's new row, Section B9.2): a small fix after this
  move — a create-mode baseline for the snapshot term, one `ExerciseDirtyStateTest` case per field,
  and scenario D-01 automated. It is a behaviour change, so it is not this move's.
- `ExerciseInteractor.setAdhocPlan` has no caller; `ClickHandler`'s two
  `withContext(mainDispatcher) { consume(Back) }` sit in `onSuccess`, already on Main.immediate (the
  canonical form is `consumeOnMain`). Both are recorded here only, for the next change to this module.
- A camera capture in flight across a process death (the widened tech-debt row): a saveable capture
  target in kit's launcher, with the system's result redelivery, is the fix path; it is kit's, not
  this phase's.

## B18. Authorization

The maintainer's GO of 2026-10-20 locked D1–D6 and accepted the discovery's spec-level items.
Starting Part B's implementation with this document accepts its wording of them, G1, G2, S17 and the
corrections of Section B0.5, and authorizes two things:

- landing this amendment on dev;
- implementing it as one PR into dev, inside Section B9.

Merge stays the maintainer's.

## B19. Implementation evidence

Filled by commit 5, in 7.14 Section 19's shape: the base SHA and the specification commit; commits
1–4 with the signed-commits skill's local `gpgsig` check, and commit 5 as "this commit"; the census
of Section B12.2 reproduced; commit 1's proof; the gate table with executed-task lines and case
counts; every control of Section B13 with its verdict quoted; the PNG proof; the environment (Xcode,
Pythons, JDK, Gradle, the emulator and its serial, free disk); deviations and implementation
decisions, if any; rejected runs, if any. GitHub's signature verification, the CI run ids and the
review classification go in the PR body and the report, because they cannot exist before this commit
does.
