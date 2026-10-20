# KMP Phase 7.15 — feature:exercise. Part A: the media seams (PR-M)

**Status:** SPECIFIED — Part A (PR-M) only. Part B (the move) is specified by an amendment of this
file after PR-M merges and the maintainer's device check (Section 12.2) passes.

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
