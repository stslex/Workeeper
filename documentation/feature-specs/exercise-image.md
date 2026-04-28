# Feature spec — Exercise image attachment (v1.5)

**Status:** Merged in Stage v1.5 (PR #71). For current architecture, see [architecture.md](../architecture.md). This spec is preserved as a historical record of the planning state.

This is the v1.5 spec — a fast follow-up after v1 closure. The first feature outside the v1 bottom-bar shape: Exercise image attachment. References:

- [product.md](../product.md) — `v1.5` feature 14 (Exercise image attachment) + Open questions section ("Image storage strategy")
- [ux-architecture.md](../ux-architecture.md) — Exercise detail (Hero image), Edit exercise (Image picker), Exercises tab (thumbnail row)
- [data-needs.md](../data-needs.md) — `exercise.image_path: String?` reserved field
- [architecture.md](../architecture.md) — Permission handling, Compose launchers, file IO conventions
- [feature-specs/exercises.md](exercises.md) — patterns to mirror in feature/exercise + feature/all-exercises

## Scope

Four deliverables:

- **ImageStorage utility** in a shared core module — handles compress + save + delete + path generation. Single source of truth for the file lifecycle.
- **Picker flow in Edit exercise** — camera capture + gallery selection, with permission handling, optimistic UI updates, and "save on commit" semantics so cancelled edits leave no orphan files.
- **Read-only surfaces in Exercise detail + Exercises tab row** — replace the existing icon placeholder with the actual image when `imagePath != null`, retain the placeholder otherwise.
- **Full-screen image viewer** — tap-to-open from Exercise detail hero and Edit exercise thumbnail. Pinch-to-zoom + pan + double-tap-to-toggle, hosted as a routed screen.

## Out of scope

- Image cropping / rotation / filters in-app — accept what the picker / camera returns, only resize.
- Multiple images per exercise — single hero image only (per product.md).
- Cloud sync / remote storage — local only (per product.md: "Stored locally on the device; no cloud upload.").
- Image attachment on `training`, `session`, or `set` — exercise-only.
- Server-uploaded thumbnails — single source file, downsampled on load by Coil.
- Sharing / exporting an exercise image — v2.
- ExifInterface orientation correction beyond what `ImageDecoder` does for free.
- Migration of `imagePath` for existing exercises — they all stay `null` until the user explicitly attaches; no batch import.
- **Tap-to-fullscreen on Exercises tab row thumbnail** — row tap opens Exercise detail (existing convention). Adding a separate gesture to open the viewer would conflict.
- **Swipe-to-dismiss in viewer** — back gesture / back button only. Vertical-drag dismiss is a v2 nice-to-have.
- **Immersive mode in viewer (hiding system bars)** — viewer renders with normal status/nav bars. Edge-to-edge content via Scaffold + transparent top bar; no `WindowInsetsController` manipulation.
- **Save / share / delete actions inside viewer** — view-only. Delete + edit are reachable from Exercise detail; the viewer doesn't duplicate them.

## Design decisions (locked)

These were "open questions" in product.md. Locking them now.

| Question | Decision | Reasoning |
|---|---|---|
| Format | **JPEG quality 85** | Universal decoder support, ~70% size reduction vs PNG for photo content, quality loss imperceptible at this level. WebP would save ~25% more but complicates debug viewing of files. |
| Max dimensions | **1280×1280**, longest edge | Hero image renders at ~120-180dp on phones; 1280px gives 4-5x oversample for clarity on dense screens. Above this is wasted bytes. |
| Thumbnail strategy | **None — single file**, Coil downsamples on load | Coil 3 has `Size`-aware decoding. Separate thumbnail file = 2x the file management complexity for negligible gain. |
| Storage location | **`context.filesDir/exercise_images/<exerciseUuid>.jpg`** | Internal storage = no external permission needed, no MediaStore visibility leak, auto-cleanup on uninstall. NOT `cacheDir` (system can wipe), NOT `externalFilesDir` (worse for private data). |
| Camera capture | **`ActivityResultContracts.TakePicture`** | Modern API, returns Boolean success, writes to a `FileProvider` URI we control. |
| Gallery picker | **`ActivityResultContracts.PickVisualMedia`** with `ImageOnly`. AndroidX backport handles API < 33 transparently. | No `READ_MEDIA_IMAGES` / `READ_EXTERNAL_STORAGE` permission needed on any supported SDK. |
| Permissions | **`CAMERA` only** (runtime). Gallery needs none. | Photo Picker is the official privacy-preserving path; no permission required on the supported SDK range (28+). |
| Edit commit semantics | **"Save on commit"** — keep `pendingImage` in State, write to disk only on Save. Old file deleted only after new file successfully written. | Eliminates orphan files from cancelled edits. Cost: image bytes held in memory during edit (acceptable — single image, max ~500KB). |
| File replace | **Atomic via temp file + rename** within `saveImage`. | If process is killed mid-write, old file remains intact. |
| Lifecycle | **Delete file only on `deleteItem` (permanent)**. Archive does NOT delete the file. | Per product.md: archive preserves history. If unarchived later, image must still be there. |
| Coil cache invalidation | **Append `?v=<lastModifiedMs>` to the image model** when displaying. | Coil keys cache by URI string. Without versioning, replacing a file at the same path shows the old cached image. |
| Viewer hosting | **Routed screen via `Screen.ExerciseImage(path)`** | Consistent with project nav model (PastSession, AllTrainings are routes). Saves state across config changes for free. NOT bottom sheet (constrained padding/scrim) and NOT in-place overlay (state lost on rotation). |
| Viewer zoom impl | **Self-written via `Modifier.pointerInput` + `detectTransformGestures` + `graphicsLayer`** | ~80 lines, no new dependency. Compose Foundation has no first-party zoomable Modifier yet. Adding a third-party lib for one feature isn't justified. |
| Viewer image quality | **Full file via Coil at native viewport size** | Files are already capped at 1280×1280 by ImageStorage. Most phone viewports ≤ 1280px, so Coil gets full source quality automatically. No extra "high-res" path needed. |
| Viewer source from Exercises row | **NOT added** — row tap opens Exercise detail (existing convention) | Adding a second gesture to the same row hits the gesture-conflict / discoverability tradeoff with no real win. Detail is one tap away. |

If any of these need to change, surface it in PR review — but the spec assumes them.

## Module structure

```
core/
├── core/                          # MODIFIED — new utility added
│   └── src/main/kotlin/.../core/core/
│       └── images/                # NEW package
│           ├── ImageStorage.kt              # interface
│           ├── ImageStorageImpl.kt          # impl: save / delete / paths
│           ├── ImageStorageModule.kt        # Hilt @Module @Binds
│           └── model/
│               ├── ImageSaveResult.kt       # sealed: Success(path) | Failure(reason)
│               └── ImageSaveError.kt        # enum: SourceUnreadable, OutOfSpace, IoFailure
│
└── exercise/                      # MODIFIED — repo hooks file deletion
    └── src/main/kotlin/.../core/exercise/exercise/
        └── ExerciseRepositoryImpl.kt        # +ImageStorage dependency; deleteItem/deleteAllItems remove files

feature/
├── exercise/                      # MODIFIED — picker + state + hero
│   └── src/main/kotlin/.../feature/exercise/
│       ├── domain/
│       │   ├── ExerciseInteractor.kt        # +saveImage(uri), +deleteImage(path) passthroughs
│       │   └── ExerciseInteractorImpl.kt
│       ├── mvi/
│       │   ├── handler/
│       │   │   ├── ClickHandler.kt          # MODIFIED — picker open/source-select/remove
│       │   │   ├── CommonHandler.kt         # MODIFIED — image result events
│       │   │   ├── InputHandler.kt          # MODIFIED — pendingImage state mgmt
│       │   │   └── PlanEditActionHandler.kt # MODIFIED — Save flow commits image
│       │   ├── mapper/
│       │   │   └── ExerciseUiMapper.kt      # MODIFIED — surfaces effective imagePath (committed or pending)
│       │   ├── model/                       # NEW models
│       │   │   ├── PendingImage.kt          # sealed: NewFromUri(uri) | RemoveExisting | Unchanged
│       │   │   └── ImageSourceUiModel.kt    # enum: Camera | Gallery (for source picker dialog)
│       │   └── store/
│       │       └── ExerciseStore.kt         # MODIFIED — +pendingImage, +sourceDialogVisible, +permissionDeniedDialogVisible
│       └── ui/
│           ├── ExerciseEditScreen.kt        # MODIFIED — wires picker launchers + image edit row
│           └── components/
│               ├── ExerciseHero.kt          # MODIFIED — conditional AsyncImage when path != null
│               ├── ImageEditRow.kt          # NEW — current thumbnail + Edit/Remove buttons (in edit screen)
│               ├── ImageSourceDialog.kt     # NEW — Camera / Gallery / Cancel choice
│               └── PermissionDeniedDialog.kt# NEW — fallback when CAMERA permission denied
│
└── all-exercises/                 # MODIFIED — row thumbnail
    └── src/main/kotlin/.../feature/all_exercises/
        ├── mvi/
        │   ├── mapper/
        │   │   └── AllExercisesUiMapper.kt  # MODIFIED — passes imagePath into ExerciseUiModel
        │   └── model/
        │       └── ExerciseUiModel.kt       # MODIFIED — +imagePath: String?
        └── ui/components/
            └── ExerciseRow.kt               # MODIFIED — AsyncImage when path != null, else ExerciseTypeIcon

feature/image-viewer/              # NEW MODULE
└── src/main/kotlin/.../feature/image_viewer/
    ├── di/
    │   ├── ImageViewerFeature.kt
    │   ├── ImageViewerHandlerStore.kt
    │   ├── ImageViewerHandlerStoreImpl.kt
    │   └── ImageViewerModule.kt
    ├── mvi/
    │   ├── handler/
    │   │   ├── ClickHandler.kt              # back, double-tap zoom toggle
    │   │   ├── CommonHandler.kt             # init from Screen route arg
    │   │   ├── ImageViewerComponent.kt
    │   │   └── NavigationHandler.kt
    │   └── store/
    │       ├── ImageViewerStore.kt
    │       └── ImageViewerStoreImpl.kt
    └── ui/
        ├── ImageViewerGraph.kt
        ├── ImageViewerScreen.kt             # full-screen Scaffold + ZoomableImage
        └── components/
            └── ZoomableImage.kt             # the gesture/transform Modifier impl

core/ui/navigation/                # MODIFIED
└── src/main/kotlin/.../core/ui/navigation/
    └── Screen.kt                            # +data class ExerciseImage(path: String)

app/app/                           # MODIFIED
├── src/main/AndroidManifest.xml             # +CAMERA permission, +FileProvider authority
├── src/main/java/.../host/AppNavigationHost.kt    # +imageViewerGraph(...)
├── src/main/java/.../navigation/RootComponentImpl.kt  # +Screen.ExerciseImage branch
├── src/main/res/xml/
│   └── file_provider_paths.xml              # NEW — exposes filesDir/exercise_images for camera capture URI
└── src/main/res/values/strings.xml          # NEW image-related strings (mirrored in values-ru)
```

## Data layer

**No schema migration.** `ExerciseEntity.imagePath: String?`, `ExerciseDataModel.imagePath`, `ExerciseChangeDataModel.imagePath` already in place from earlier stages — verified end-to-end. Pipeline already carries the field; today it's always `null`. v1.5 simply starts populating it.

## Storage layer — `core/core/images`

### `ImageStorage` interface

```kotlin
interface ImageStorage {

    /**
     * Reads the image at [sourceUri], decodes it, downsamples to fit within
     * [MAX_EDGE]x[MAX_EDGE], compresses as JPEG quality [QUALITY], and writes
     * it atomically to filesDir/exercise_images/<exerciseUuid>.jpg.
     *
     * If a file already exists at the destination, it is overwritten (atomic
     * via temp file + rename — old file remains intact if the process dies).
     *
     * Caller is responsible for invoking deleteImage() on any *previous* path
     * that this exercise had — saveImage does not track that.
     *
     * @return ImageSaveResult.Success(absolutePath) on success, or
     *         ImageSaveResult.Failure(error) on failure.
     *         Never throws.
     */
    suspend fun saveImage(sourceUri: Uri, exerciseUuid: String): ImageSaveResult

    /**
     * Returns a temporary file URI suitable for handing to
     * ActivityResultContracts.TakePicture. The caller passes this URI to the
     * camera launcher; on success, the camera writes the captured image there.
     * The caller is then expected to call saveImage(tempUri, exerciseUuid) to
     * downsample + persist to the canonical location.
     *
     * Temp files live in filesDir/exercise_images/.tmp/ and are cleaned up by
     * cleanupTempFiles() on next app start.
     */
    suspend fun createTempCaptureUri(): Uri

    /**
     * Deletes the file at [path]. No-op if absent. Returns true if a file
     * was actually deleted.
     */
    suspend fun deleteImage(path: String): Boolean

    /**
     * Removes any temp capture files left behind by killed processes.
     * Called once at app startup.
     */
    suspend fun cleanupTempFiles()

    companion object {
        const val MAX_EDGE = 1280
        const val QUALITY = 85
        const val DIRECTORY = "exercise_images"
        const val TEMP_SUBDIRECTORY = ".tmp"
    }
}
```

### `ImageStorageImpl` notes

- Decode source: `ImageDecoder.createSource(contentResolver, sourceUri).decodeBitmap { info, _ -> info.setTargetSampleSize(...) }` — built-in downsample at decode time, avoids loading full bitmap into memory.
- Write: `BitmapFactory` for fallback on weird sources; primary path is `ImageDecoder` (API 28+, matches our minSdk).
- Atomic rename: write to `<dest>.tmp`, then `File.renameTo(<dest>)`. On Linux/Android this is atomic for same-filesystem moves.
- Wrap all IO in `withContext(bgDispatcher)`. Use the existing `bgDispatcher` qualifier from core/core DI.
- Return `ImageSaveResult` rather than throwing — caller (UI) needs structured failure to show appropriate error message.
- `cleanupTempFiles` runs at app start. Trigger from `WorkeeperApp.onCreate` via Hilt-injected `AppStartupTask` if such a hook exists; otherwise launch from `WorkeeperApp.onCreate` in a `GlobalScope.launch(Dispatchers.IO)` (one-shot, fire-and-forget; not ideal but acceptable for cleanup).

### Hilt module

```kotlin
@Module
@InstallIn(SingletonComponent::class)
abstract class ImageStorageModule {

    @Binds
    @Singleton
    abstract fun bindImageStorage(impl: ImageStorageImpl): ImageStorage
}
```

`ImageStorageImpl` constructor injects `@ApplicationContext context: Context` and `@BgDispatcher dispatcher: CoroutineDispatcher`.

### `ExerciseRepositoryImpl` hook

Inject `ImageStorage`. Modify two methods:

```kotlin
override suspend fun deleteItem(uuid: String) {
    withContext(bgDispatcher) {
        // Read imagePath BEFORE delete — once row is gone, we lose the path.
        val imagePath = dao.getById(Uuid.parse(uuid))?.imagePath
        dao.permanentDelete(Uuid.parse(uuid))
        imagePath?.let { imageStorage.deleteImage(it) }
    }
}

override suspend fun deleteAllItems(uuids: List<Uuid>) {
    withContext(bgDispatcher) {
        // Snapshot paths before deleting rows.
        val paths = uuids.mapNotNull { dao.getById(it)?.imagePath }
        uuids.forEach { dao.permanentDelete(it) }
        paths.forEach { imageStorage.deleteImage(it) }
    }
}
```

Note: archive flow does NOT touch the file. Verified by inspecting current `archiveItem` / `unarchiveItem` impls — they only flip the `archived` flag. Image survives archive/unarchive cycles, as it should.

## Permission flow

`CAMERA` is the only runtime permission needed. Add to `AndroidManifest.xml`:

```xml
<uses-permission android:name="android.permission.CAMERA" />
<uses-feature android:name="android.hardware.camera" android:required="false" />
```

`required="false"` keeps the app installable on devices without camera hardware (rare, but tablets sometimes).

The flow:

```
User taps "Edit image" in Edit exercise
                ↓
       ImageSourceDialog shown
                ↓
       ┌────────┴────────┐
       │                 │
   Camera             Gallery
       ↓                 │
  Check CAMERA           │
  permission             │
       │                 │
   ┌───┴───┐             │
 granted  denied         │
   │       │             │
   │   request           │
   │   permission        │
   │       │             │
   │   ┌───┴───┐         │
   │ granted denied      │
   │   │     │           │
   │   │  PermissionDeniedDialog
   │   │  (offer Settings deeplink)
   │   │                 │
   ▼   ▼                 ▼
 TakePicture launcher  PickVisualMedia launcher
       │                 │
   tempUri          contentUri
       │                 │
       └────────┬────────┘
                ▼
       Action.Common.ImagePicked(uri)
                ↓
       Update State.pendingImage = NewFromUri(uri)
       (no disk write yet)
                ↓
       Hero re-renders showing the new image
       (Coil reads from URI directly — content://
        works the same as file://)
```

Permission rationale string (for the system dialog, when applicable): "Workeeper uses the camera so you can attach a photo of the exercise machine or movement, helping you identify it later in the gym." Localized string in values + values-ru.

## Compose launcher wiring

Picker launchers live in `ExerciseEditScreen` (the only place that picks). Use `rememberLauncherForActivityResult` for both, plus `rememberLauncherForActivityResult(RequestPermission)` for the camera-permission flow.

Pseudocode in the screen body:

```kotlin
val context = LocalContext.current
val cameraTempUriState = remember { mutableStateOf<Uri?>(null) }

val cameraLauncher = rememberLauncherForActivityResult(
    contract = ActivityResultContracts.TakePicture(),
) { success ->
    val uri = cameraTempUriState.value
    if (success && uri != null) {
        consume(Action.Common.ImagePicked(uri))
    } else {
        consume(Action.Common.ImagePickCancelled)
    }
    cameraTempUriState.value = null
}

val galleryLauncher = rememberLauncherForActivityResult(
    contract = ActivityResultContracts.PickVisualMedia(),
) { uri ->
    if (uri != null) consume(Action.Common.ImagePicked(uri))
    else consume(Action.Common.ImagePickCancelled)
}

val cameraPermissionLauncher = rememberLauncherForActivityResult(
    contract = ActivityResultContracts.RequestPermission(),
) { granted ->
    if (granted) {
        consume(Action.Click.RequestCameraCapture)
    } else {
        consume(Action.Click.OnCameraPermissionDenied)
    }
}

// When ClickHandler emits a "launch camera" event, the screen handles it via:
LaunchedEffect(Unit) {
    events.collect { event ->
        when (event) {
            is Event.LaunchCamera -> {
                val tempUri = event.tempUri
                cameraTempUriState.value = tempUri
                cameraLauncher.launch(tempUri)
            }
            is Event.LaunchGallery -> {
                galleryLauncher.launch(
                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                )
            }
            is Event.RequestCameraPermission -> {
                cameraPermissionLauncher.launch(android.Manifest.permission.CAMERA)
            }
        }
    }
}
```

The `tempUri` for camera capture comes from the Store, which calls `imageStorage.createTempCaptureUri()` and emits `Event.LaunchCamera(tempUri)`. The screen does NOT call ImageStorage directly.

## MVI surface — `feature/exercise` deltas

### State additions

```kotlin
data class State(
    // ... existing fields
    val imagePath: String?,                  // committed value from DB; what's in ExerciseDataModel today
    val pendingImage: PendingImage,           // edit-time staging
    val sourceDialogVisible: Boolean,
    val permissionDeniedDialogVisible: Boolean,
) : Store.State {

    /** What the UI should display right now — pending overrides committed. */
    val effectiveImageDisplay: ImageDisplay
        get() = when (pendingImage) {
            is PendingImage.NewFromUri -> ImageDisplay.FromUri(pendingImage.uri)
            PendingImage.RemoveExisting -> ImageDisplay.None
            PendingImage.Unchanged -> when (imagePath) {
                null -> ImageDisplay.None
                else -> ImageDisplay.FromPath(imagePath, lastModified = imageLastModified)
            }
        }

    val isImageDirty: Boolean get() = pendingImage != PendingImage.Unchanged
    val canSave: Boolean get() = nameValid /* &&  ... existing checks */ // unchanged structurally
}

@Stable
sealed interface ImageDisplay {
    data object None : ImageDisplay
    data class FromPath(val path: String, val lastModified: Long) : ImageDisplay
    data class FromUri(val uri: Uri) : ImageDisplay
}

@Stable
sealed interface PendingImage {
    data object Unchanged : PendingImage
    data class NewFromUri(val uri: Uri) : PendingImage
    data object RemoveExisting : PendingImage
}
```

`imageLastModified` is captured at exercise-load time (from `File(imagePath).lastModified()` if the path exists) and stored in State. Used to bust Coil's cache when a different revision lands.

### Action additions

```kotlin
sealed interface Click : Action {
    // ... existing
    data object OnEditImageClick : Click           // opens source dialog
    data class OnImageSourceSelected(val source: ImageSourceUiModel) : Click
    data object OnRemoveImageClick : Click
    data object OnImageSourceDialogDismiss : Click
    data object OnPermissionDeniedDialogDismiss : Click
    data object OnPermissionDeniedSettingsClick : Click   // open app settings
    data object RequestCameraCapture : Click       // internal: emitted by permission launcher result
    data object OnCameraPermissionDenied : Click   // internal: emitted by permission launcher result
    data object OnImageThumbnailClick : Click      // opens viewer (Edit screen + Detail screen)
}

sealed interface Common : Action {
    // ... existing
    data class ImagePicked(val uri: Uri) : Common
    data object ImagePickCancelled : Common
}
```

### Navigation additions

```kotlin
sealed interface Navigation : Action {
    // ... existing
    data class OpenImageViewer(val model: String) : Navigation
}
```

`NavigationHandler`:

```kotlin
is Action.Navigation.OpenImageViewer ->
    navigator.navTo(Screen.ExerciseImage(action.model))
```

`ClickHandler.OnImageThumbnailClick` derives the viewer model from current state:

```kotlin
Action.Click.OnImageThumbnailClick -> {
    val model = when (val display = state.value.effectiveImageDisplay) {
        is ImageDisplay.FromPath -> display.path
        is ImageDisplay.FromUri -> display.uri.toString()
        ImageDisplay.None -> return  // no-op: thumbnail isn't shown anyway
    }
    consume(Action.Navigation.OpenImageViewer(model))
}
```

### Event additions

```kotlin
sealed interface Event : Store.Event {
    // ... existing
    data class LaunchCamera(val tempUri: Uri) : Event
    data object LaunchGallery : Event
    data object RequestCameraPermission : Event
    data class OpenAppSettings(val packageName: String) : Event
    data class ShowImageError(val errorType: ImageErrorType) : Event
}

enum class ImageErrorType {
    SaveFailed,
    LoadFailed,
    DecodeFailed,
}
```

### Handler responsibilities

- **`ClickHandler`**:
  - `OnEditImageClick` → emit `Event.HapticClick`, `updateState { sourceDialogVisible = true }`.
  - `OnImageSourceSelected(Camera)` → check permission via `context.checkSelfPermission(CAMERA)`. If granted, call `interactor.createTempCaptureUri()` → emit `Event.LaunchCamera(uri)`. If not granted, emit `Event.RequestCameraPermission`.
  - `OnImageSourceSelected(Gallery)` → emit `Event.LaunchGallery`. Close source dialog.
  - `OnRemoveImageClick` → `updateState { pendingImage = PendingImage.RemoveExisting }`. (Source dialog implicitly hidden — show only when no image, but allow remove from row.)
  - `RequestCameraCapture` → re-run the camera path from the source-selected case, now that permission is granted.
  - `OnCameraPermissionDenied` → `updateState { permissionDeniedDialogVisible = true }`.
  - `OnPermissionDeniedSettingsClick` → emit `Event.OpenAppSettings(context.packageName)`.

- **`CommonHandler`**:
  - `ImagePicked(uri)` → `updateState { pendingImage = PendingImage.NewFromUri(uri) }`. Source dialog gets hidden.
  - `ImagePickCancelled` → no state change beyond hiding source dialog if visible.

- **`PlanEditActionHandler`** (or whichever handler owns the Save click):
  - On Save: build `ExerciseChangeDataModel` based on current State + `pendingImage`:
    - If `pendingImage = NewFromUri(uri)`:
      - Capture `oldPath = state.imagePath`.
      - Call `interactor.saveImage(uri, exerciseUuid)`.
      - On `ImageSaveResult.Success(newPath)`: set `imagePath = newPath` in the change model. After successful repo update, fire-and-forget `interactor.deleteImage(oldPath)` if `oldPath != null && oldPath != newPath`.
      - On `ImageSaveResult.Failure(_)`: emit `Event.ShowImageError(SaveFailed)` and ABORT the save (do NOT call repo update). Keep `pendingImage` so user can retry.
    - If `pendingImage = RemoveExisting`:
      - Set `imagePath = null` in change model.
      - After successful repo update, fire-and-forget `interactor.deleteImage(state.imagePath!!)`.
    - If `pendingImage = Unchanged`: don't touch image path in the change model (or pass through the existing value — match how non-image fields work).
  - Sequence is critical: write new file → update DB → only then delete old file. If DB update fails, the new file is orphaned but old file is intact (better outcome than the reverse).

## MVI surface — `feature/all-exercises` deltas

Minimal — just plumbing:

- `ExerciseUiModel` — add `val imagePath: String?`.
- `AllExercisesUiMapper` — pass through from `ExerciseDataModel.imagePath`.
- `ExerciseRow` — leading slot becomes:
  ```kotlin
  if (item.imagePath != null) {
      AsyncImage(
          model = ImageRequest.Builder(LocalContext.current)
              .data("${item.imagePath}?v=${file.lastModified()}")  // cache-bust
              .crossfade(true)
              .build(),
          contentDescription = null,
          modifier = Modifier.size(48.dp).clip(AppUi.shapes.small),
          contentScale = ContentScale.Crop,
      )
  } else {
      ExerciseTypeIcon(type = item.type, modifier = Modifier.size(48.dp))
  }
  ```
  Wrap the `file.lastModified()` call in a remember keyed on path to avoid IO on every recomposition. Or skip cache-busting in the row (image rarely changes in this context — staleness window is acceptable; cache-bust matters most in the edit screen where the user just changed it).

## Compose surface — Edit exercise

`ImageEditRow` placement: between Description field and the Save button area, or as a card at the top of the form (above Name) — match the visual hierarchy from `ux-architecture.md` ("Hero image (v1.5)" listed first under Exercise detail, suggests prominent placement in edit too).

```
┌──────────────────────────────────────┐
│  ┌────────┐                           │
│  │  IMG   │  [Edit image]  [Remove]   │
│  │ thumb  │                           │
│  └────────┘                           │
└──────────────────────────────────────┘
```

When no image: thumb shows the placeholder icon (reuse `ExerciseHero` styled smaller), `[Remove]` button hidden, `[Edit image]` becomes `[Add image]`.

`ImageSourceDialog` — `AppDialog` (existing component) with two list items: "Take photo" + "Choose from gallery", plus Cancel.

`PermissionDeniedDialog` — `AppDialog` explaining the permission was denied, with primary "Open settings" and secondary "Cancel".

## Full-screen image viewer — `feature/image-viewer`

### Trigger surfaces

Two entry points add a tap handler:

- **Exercise detail hero** — `ExerciseHero` (when `imagePath != null`) becomes clickable. Tap → `Action.Click.OnHeroClick` → `Action.Navigation.OpenImageViewer(path)` → `navigator.navTo(Screen.ExerciseImage(path))`.
- **Edit exercise thumbnail** — the thumbnail in `ImageEditRow` becomes clickable when an effective image is displayed (`effectiveImageDisplay != ImageDisplay.None`). Tap on the **image area** opens viewer; tap on **Edit / Remove buttons** does what those buttons do.

The Exercises tab row thumbnail does NOT trigger the viewer (row tap → Exercise detail; explicit out of scope, see top of spec).

### Viewer source-of-truth

Viewer takes a single `path: String` arg from the route — the absolute file path under `filesDir/exercise_images/`. Viewer does NOT load the exercise from the DB; it doesn't need name/type/tags. This keeps the route stateless and the screen reusable for any future image-viewing need (e.g. v2 training cover).

### Edit-screen viewer for pending image

If the user has just picked a new image (`pendingImage = NewFromUri(uri)`) and taps the thumbnail, the viewer needs to display that **content URI**, not a committed file path. Two reasonable approaches:

- (a) Generalize the route arg to accept either path-or-URI. `Screen.ExerciseImage(modelString)` where `modelString` can be `"/data/.../exercise_images/<uuid>.jpg"` (file path Coil handles natively) OR `"content://..."` from the picker.
- (b) Disable viewer tap when there's a pending image, only enable for committed paths.

**Pick (a).** Coil's `data` parameter accepts both `String` paths and `Uri` content URIs through the same loader; passing the URI's `toString()` and reconstructing on the receiving side works. Worth verifying in implementation that the content URI's permission grant survives the navigation — content URIs from `PickVisualMedia` get a transient grant that should persist within the same Activity's lifetime; we navigate within the same Activity, so this should hold. If it doesn't, fall back to (b) and document.

### Route + Navigator

Add to `core/ui/navigation/Screen.kt`:

```kotlin
@Serializable
data class ExerciseImage(
    val model: String,    // file path OR content URI string
) : Screen
```

No new Navigator method required — uses existing `navTo` and `popBack`.

`AppNavigationHost` registers `imageViewerGraph(...)` like other feature graphs. `RootComponentImpl` adds:

```kotlin
is Screen.ExerciseImage -> ImageViewerComponent.create(navigator, screen)
```

### MVI surface

```kotlin
internal abstract class ImageViewerComponent(
    val data: Screen.ExerciseImage,
) : Component

@Stable
data class State(
    val model: String,
    val scale: Float,
    val offsetX: Float,
    val offsetY: Float,
) : Store.State {
    companion object {
        const val MIN_SCALE = 1f
        const val MAX_SCALE = 5f
        const val DOUBLE_TAP_TARGET_SCALE = 2.5f

        fun initial(model: String): State = State(
            model = model,
            scale = MIN_SCALE,
            offsetX = 0f,
            offsetY = 0f,
        )
    }
}

@Stable
sealed interface Action : Store.Action {

    sealed interface Click : Action {
        data object OnBackClick : Click
        data object OnDoubleTap : Click
    }

    sealed interface Common : Action {
        data class TransformChange(val scaleDelta: Float, val panX: Float, val panY: Float) : Common
        data object Init : Common
    }

    sealed interface Navigation : Action {
        data object Back : Navigation
    }
}

@Stable
sealed interface Event : Store.Event {
    data class HapticClick(val type: HapticFeedbackType) : Event
}
```

Handler logic:

- `CommonHandler.Init` → just confirms `state.model` reflects route arg (already set by initial()).
- `CommonHandler.TransformChange(scaleDelta, panX, panY)` → compute `newScale = (state.scale * scaleDelta).coerceIn(MIN_SCALE, MAX_SCALE)`. If `newScale == 1f`, snap offset to 0. Otherwise apply pan with bounds (offsets clamped so image edges don't pull beyond viewport when zoomed; bounds = `(scale - 1) * viewportSize / 2` per axis, computed from the LayoutCoordinates the screen owns and passed in via the action).
- `ClickHandler.OnDoubleTap` → toggle scale: if `scale > MIN_SCALE`, animate to `MIN_SCALE` (and reset offsets); else animate to `DOUBLE_TAP_TARGET_SCALE`. Animation in the UI layer via `Animatable`.
- `ClickHandler.OnBackClick` → `consume(Action.Navigation.Back)`.
- `NavigationHandler.Back` → `navigator.popBack()`.

The transform clamp logic depends on viewport size. Two options:
- (a) Pass viewport dimensions in the action: `TransformChange(scaleDelta, panX, panY, viewportWidth, viewportHeight)`. Store does math.
- (b) Do all gesture math in the Composable, store only the final clamped values.

**Pick (b).** Less data marshalling, gesture math is presentational concern. Store holds final scale/offset; Composable does the clamping using `LayoutCoordinates`. Action becomes simpler:

```kotlin
data class TransformChange(val scale: Float, val offsetX: Float, val offsetY: Float) : Common
```

Composable computes new scale + clamped offsets, sends absolute values.

### Compose surface — `ImageViewerScreen`

```kotlin
@Composable
internal fun ImageViewerScreen(
    state: ImageViewerStore.State,
    consume: (ImageViewerStore.Action) -> Unit,
) {
    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            AppTopAppBar(
                title = { /* empty — no clutter */ },
                navigationIcon = {
                    IconButton(onClick = { consume(Action.Click.OnBackClick) }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = stringResource(R.string.feature_image_viewer_back))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent,
                ),
            )
        },
        containerColor = Color.Black,
    ) { padding ->
        ZoomableImage(
            model = state.model,
            scale = state.scale,
            offsetX = state.offsetX,
            offsetY = state.offsetY,
            onTransform = { newScale, newOffsetX, newOffsetY ->
                consume(Action.Common.TransformChange(newScale, newOffsetX, newOffsetY))
            },
            onDoubleTap = { consume(Action.Click.OnDoubleTap) },
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        )
    }
}
```

`BackHandler { consume(Action.Click.OnBackClick) }` — system-back goes through the same Action path as the icon. Not strictly required (default popBack works), but keeps haptic + analytics consistent if added later.

### `ZoomableImage` composable

```kotlin
@Composable
internal fun ZoomableImage(
    model: String,
    scale: Float,
    offsetX: Float,
    offsetY: Float,
    onTransform: (scale: Float, offsetX: Float, offsetY: Float) -> Unit,
    onDoubleTap: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val animatedScale by animateFloatAsState(scale, label = "scale")
    val animatedOffsetX by animateFloatAsState(offsetX, label = "offsetX")
    val animatedOffsetY by animateFloatAsState(offsetY, label = "offsetY")

    var viewportSize by remember { mutableStateOf(IntSize.Zero) }

    Box(
        modifier = modifier
            .onSizeChanged { viewportSize = it }
            .pointerInput(viewportSize) {
                detectTransformGestures { _, pan, zoom, _ ->
                    val newScale = (scale * zoom).coerceIn(MIN_SCALE, MAX_SCALE)
                    val newOffsetX: Float
                    val newOffsetY: Float
                    if (newScale <= MIN_SCALE) {
                        newOffsetX = 0f
                        newOffsetY = 0f
                    } else {
                        val maxOffsetX = (viewportSize.width * (newScale - 1f)) / 2f
                        val maxOffsetY = (viewportSize.height * (newScale - 1f)) / 2f
                        newOffsetX = (offsetX + pan.x).coerceIn(-maxOffsetX, maxOffsetX)
                        newOffsetY = (offsetY + pan.y).coerceIn(-maxOffsetY, maxOffsetY)
                    }
                    onTransform(newScale, newOffsetX, newOffsetY)
                }
            }
            .pointerInput(Unit) {
                detectTapGestures(onDoubleTap = { onDoubleTap() })
            },
        contentAlignment = Alignment.Center,
    ) {
        AsyncImage(
            model = model,    // Coil handles file path or content URI string
            contentDescription = stringResource(R.string.feature_image_viewer_content_description),
            contentScale = ContentScale.Fit,
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    scaleX = animatedScale
                    scaleY = animatedScale
                    translationX = animatedOffsetX
                    translationY = animatedOffsetY
                },
        )
    }
}
```

Two `pointerInput` blocks because `detectTransformGestures` and `detectTapGestures` can't compose in a single block — Compose runs the first matching detector and ignores others within the same `pointerInput`. Splitting them lets both run.

Note: `detectTapGestures(onDoubleTap = ...)` consumes single taps too — but here that's fine because there's no single-tap action. If a single-tap-to-toggle-bars feature were added later, the second `pointerInput` would need restructuring.

### Edge cases

- **File missing at viewer open** — Coil renders nothing / its error placeholder. Handle by setting `error` and `fallback` on `ImageRequest.Builder` to show a simple "Image unavailable" overlay. State doesn't need to track this; UI-only concern.
- **Rotation during zoomed state** — viewer survives via routed nav; State persists; offsets are absolute pixel values that may not map perfectly to new viewport. Acceptable: on rotation, clamp re-runs on next gesture and snaps to bounds. If the visible glitch is bad, reset to MIN_SCALE on viewport change (`LaunchedEffect(viewportSize) { if (oldSize != newSize) consume(reset) }`). Defer until it's actually a problem.
- **Process death** — ROM-dependent; Compose state is lost, but `Screen.ExerciseImage(model)` route arg survives via SavedStateHandle. Viewer reopens at MIN_SCALE. Acceptable.
- **Content URI permission expired** — if Edit-screen viewer fails to load a content URI because the grant lapsed (theoretical edge case across long viewer sessions / process restart), Coil shows error placeholder. User taps back, picks again. Document but don't prevent.


## Localization

New strings (en + ru, both with proper plurals where applicable):

```xml
<!-- Edit exercise — image -->
<string name="feature_exercise_image_edit_title">Exercise image</string>
<string name="feature_exercise_image_action_add">Add image</string>
<string name="feature_exercise_image_action_edit">Edit image</string>
<string name="feature_exercise_image_action_remove">Remove</string>

<!-- Source dialog -->
<string name="feature_exercise_image_source_dialog_title">Choose source</string>
<string name="feature_exercise_image_source_camera">Take photo</string>
<string name="feature_exercise_image_source_gallery">Choose from gallery</string>

<!-- Permission -->
<string name="feature_exercise_image_camera_rationale">
    Workeeper uses the camera so you can attach a photo of the exercise
    machine or movement, helping you identify it later in the gym.
</string>
<string name="feature_exercise_image_permission_denied_title">Camera permission denied</string>
<string name="feature_exercise_image_permission_denied_body">
    To take a photo, grant camera access in app settings. You can still
    choose an existing photo from your gallery.
</string>
<string name="feature_exercise_image_permission_denied_action_settings">Open settings</string>

<!-- Errors -->
<string name="feature_exercise_image_error_save_failed">Could not save image — try again</string>
<string name="feature_exercise_image_error_load_failed">Could not load image</string>
<string name="feature_exercise_image_error_decode_failed">Image format not supported</string>

<!-- Image viewer -->
<string name="feature_image_viewer_back">Back</string>
<string name="feature_image_viewer_content_description">Exercise image, full size</string>
<string name="feature_image_viewer_unavailable">Image unavailable</string>
```

Russian translations to mirror — pay attention to verb forms ("открыть настройки" / "выбрать из галереи" etc.).

## Testing

### Unit tests

- `ImageStorageImplTest` (JVM with Robolectric for Context, OR androidTest for full integration):
  - `saveImage` happy path — file exists at expected location, dimensions ≤ 1280, decodable.
  - `saveImage` overwrites existing file at same path.
  - `saveImage` returns `Failure(SourceUnreadable)` on bogus URI.
  - `saveImage` is atomic — corrupt the temp file mid-flight (simulate via mock), verify destination unchanged.
  - `deleteImage` removes existing file, no-ops on absent file.
  - `createTempCaptureUri` returns a URI under the temp subdirectory.
  - `cleanupTempFiles` removes everything in temp subdirectory.

- `ExerciseRepositoryImplTest`:
  - `deleteItem` removes both the row AND the file.
  - `deleteAllItems` removes both rows AND all files.
  - `archiveItem` does NOT delete the file (regression test).

- `feature/exercise`:
  - `ClickHandlerTest` — `OnEditImageClick` opens source dialog. `OnImageSourceSelected(Camera)` with permission granted emits `Event.LaunchCamera`. With permission denied emits `Event.RequestCameraPermission`. `OnRemoveImageClick` updates State to `RemoveExisting`. `OnCameraPermissionDenied` shows permission denied dialog.
  - `CommonHandlerTest` — `ImagePicked` sets `pendingImage = NewFromUri`. `ImagePickCancelled` doesn't set pending image.
  - `PlanEditActionHandlerTest` (or whatever owns Save) — Save with `NewFromUri` calls `saveImage`, on success updates DB with new path, on failure shows error and aborts. Save with `RemoveExisting` updates DB with `null` path. Save with `Unchanged` doesn't touch image path or call ImageStorage.
  - `ExerciseUiMapperTest` — `effectiveImageDisplay` returns correct variant per pendingImage state.

- `feature/all-exercises`:
  - `AllExercisesUiMapperTest` — `imagePath` propagates from DataModel to UiModel.

- `feature/image-viewer`:
  - `ClickHandlerTest` — `OnBackClick` emits `Action.Navigation.Back`. `OnDoubleTap` toggles scale: from MIN_SCALE → DOUBLE_TAP_TARGET_SCALE; from any > MIN_SCALE → MIN_SCALE with reset offsets.
  - `CommonHandlerTest` — `TransformChange` updates state with passed values. `Init` no-op (state already from initial()).
  - `NavigationHandlerTest` — `Back` calls `navigator.popBack()`.
  - No mapper test — viewer has no domain → UI mapping; route arg flows through unchanged.

### Compose tests

- `ExerciseEditScreenTest` — when no image, renders Add button + placeholder. When image present (committed), renders thumbnail + Edit + Remove. When `pendingImage = RemoveExisting`, renders placeholder despite committed path (effective display wins). When `pendingImage = NewFromUri`, renders new URI image.
- `ExerciseDetailScreenTest` — hero shows AsyncImage when `imagePath != null`, placeholder otherwise.
- `ExerciseRowTest` — leading slot AsyncImage when imagePath, ExerciseTypeIcon otherwise.
- `ImageSourceDialogTest`, `PermissionDeniedDialogTest` — render + interaction.
- `ImageViewerScreenTest` — renders AsyncImage with passed model; back icon click emits OnBackClick; tapping image area twice in quick succession emits OnDoubleTap; pinch gesture (synthesized via robolectric/compose test injectors) emits TransformChange. Real pinch + bound clamping are best validated in androidTest (see Integration tests).

### Integration tests (`androidTest`)

- Full picker flow: open Edit exercise → tap Edit image → select Gallery → mock returns a URI → State updates → tap Save → verify file written to expected location and DB updated.
- Replace flow: existing exercise with image → edit → pick new → save → old file gone, new file present, DB has new path.
- Remove flow: existing exercise with image → edit → remove → save → file gone, DB null.
- Cancel flow: existing exercise with image → edit → pick new → press back / cancel → no file changes, DB unchanged.
- Delete flow: exercise with image → permanent delete → file gone.
- Archive flow: exercise with image → archive → file present (regression).
- Viewer open from Detail: tap hero with imagePath set → ImageViewerScreen reached → back gesture returns to Detail with same scroll position.
- Viewer open from Edit (committed image): tap thumbnail → viewer opens → back returns to Edit with form state preserved (pendingImage still Unchanged).
- Viewer open from Edit (pending image): pick new image → tap thumbnail → viewer shows the new content URI → back → pending state intact, picker hasn't re-fired.
- Viewer pinch-zoom: programmatic pinch gesture → scale increases → max scale clamped at 5f. Pan only available when scale > 1f. Pan clamped to image bounds.

## Verification gate

Before marking PR ready:

- `./gradlew assembleDebug` passes.
- `./gradlew testDebugUnitTest` passes.
- `./gradlew connectedDebugAndroidTest` passes (integration tests).
- `./gradlew detekt` passes.
- `./gradlew lintDebug` passes.
- Manual on a real device:
  - Add image via gallery → save → verify hero updates → verify Exercises tab row thumbnail updates.
  - Add image via camera (permission granted) → save → verify thumbnail.
  - Deny camera permission → permission dialog shown → "Open settings" deeplinks correctly.
  - Replace image → save → verify hero shows new image (no stale cache).
  - Remove image → save → verify placeholder returns.
  - Cancel during edit → no file artifacts in `filesDir/exercise_images/`.
  - Permanent delete exercise → file gone from disk (verify via Device Explorer / `adb shell run-as`).
  - Archive exercise with image → file still present.
  - Kill app during camera capture (force stop while camera open) → next launch, no temp file left behind.
  - Tap hero on Exercise detail (with image) → viewer opens, image fits viewport, status bar visible.
  - In viewer: pinch to zoom in, pan around — image bounds clamp pan correctly. Pinch out → image snaps back to MIN_SCALE = 1f and centers.
  - In viewer: double-tap zoomed-out image → animates to ~2.5x. Double-tap again → animates back to 1f.
  - Back gesture from viewer → returns to entry screen.
  - Tap thumbnail in Edit screen with a freshly picked (uncommitted) image → viewer shows the picked image (content URI). Back → Edit screen still has `pendingImage = NewFromUri`.
  - In RU locale, all new strings translated.

## Constraints

- **SPDX-License-Identifier: GPL-3.0-only** on every new file.
- ImageStorage is the **only** module that touches filesystem for images. Repos, ViewModels, and UI never call `File()` directly.
- The save sequence is fixed: write new file → update DB → delete old file. Never reorder.
- pendingImage in State ONLY — do not write to disk before Save click.
- No image-related code in `core/database` (it's a pure persistence layer).
- No image-related code in `feature/single-training`, `feature/all-trainings`, `feature/live-workout`, `feature/past-session`, `feature/home` — exercise-only.
