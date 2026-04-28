# Feature spec — Exercise image attachment (v1.5)

This is the v1.5 spec — a fast follow-up after v1 closure. The first feature outside the v1 bottom-bar shape: Exercise image attachment. References:

- [product.md](../product.md) — `v1.5` feature 14 (Exercise image attachment) + Open questions section ("Image storage strategy")
- [ux-architecture.md](../ux-architecture.md) — Exercise detail (Hero image), Edit exercise (Image picker), Exercises tab (thumbnail row)
- [data-needs.md](../data-needs.md) — `exercise.image_path: String?` reserved field
- [architecture.md](../architecture.md) — Permission handling, Compose launchers, file IO conventions
- [feature-specs/exercises.md](exercises.md) — patterns to mirror in feature/exercise + feature/all-exercises

## Scope

Three deliverables:

- **ImageStorage utility** in a shared core module — handles compress + save + delete + path generation. Single source of truth for the file lifecycle.
- **Picker flow in Edit exercise** — camera capture + gallery selection, with permission handling, optimistic UI updates, and "save on commit" semantics so cancelled edits leave no orphan files.
- **Read-only surfaces in Exercise detail + Exercises tab row** — replace the existing icon placeholder with the actual image when `imagePath != null`, retain the placeholder otherwise.

## Out of scope

- Image cropping / rotation / filters in-app — accept what the picker / camera returns, only resize.
- Multiple images per exercise — single hero image only (per product.md).
- Cloud sync / remote storage — local only (per product.md: "Stored locally on the device; no cloud upload.").
- Image attachment on `training`, `session`, or `set` — exercise-only.
- Server-uploaded thumbnails — single source file, downsampled on load by Coil.
- Sharing / exporting an exercise image — v2.
- ExifInterface orientation correction beyond what `ImageDecoder` does for free.
- Migration of `imagePath` for existing exercises — they all stay `null` until the user explicitly attaches; no batch import.

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

app/app/                           # MODIFIED
├── src/main/AndroidManifest.xml             # +CAMERA permission, +FileProvider authority
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
}

sealed interface Common : Action {
    // ... existing
    data class ImagePicked(val uri: Uri) : Common
    data object ImagePickCancelled : Common
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

### Compose tests

- `ExerciseEditScreenTest` — when no image, renders Add button + placeholder. When image present (committed), renders thumbnail + Edit + Remove. When `pendingImage = RemoveExisting`, renders placeholder despite committed path (effective display wins). When `pendingImage = NewFromUri`, renders new URI image.
- `ExerciseDetailScreenTest` — hero shows AsyncImage when `imagePath != null`, placeholder otherwise.
- `ExerciseRowTest` — leading slot AsyncImage when imagePath, ExerciseTypeIcon otherwise.
- `ImageSourceDialogTest`, `PermissionDeniedDialogTest` — render + interaction.

### Integration tests (`androidTest`)

- Full picker flow: open Edit exercise → tap Edit image → select Gallery → mock returns a URI → State updates → tap Save → verify file written to expected location and DB updated.
- Replace flow: existing exercise with image → edit → pick new → save → old file gone, new file present, DB has new path.
- Remove flow: existing exercise with image → edit → remove → save → file gone, DB null.
- Cancel flow: existing exercise with image → edit → pick new → press back / cancel → no file changes, DB unchanged.
- Delete flow: exercise with image → permanent delete → file gone.
- Archive flow: exercise with image → archive → file present (regression).

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
  - In RU locale, all new strings translated.

## Constraints

- **SPDX-License-Identifier: GPL-3.0-only** on every new file.
- ImageStorage is the **only** module that touches filesystem for images. Repos, ViewModels, and UI never call `File()` directly.
- The save sequence is fixed: write new file → update DB → delete old file. Never reorder.
- pendingImage in State ONLY — do not write to disk before Save click.
- No image-related code in `core/database` (it's a pure persistence layer).
- No image-related code in `feature/single-training`, `feature/all-trainings`, `feature/live-workout`, `feature/past-session`, `feature/home` — exercise-only.

## Implementation prompt (for coding agent)

Read this entire spec before starting. Key callouts below — but the spec is the contract.

Branch: feat/v1-5-exercise-image
PR title: feat(exercise): image attachment (v1.5)
PR mode: draft (mark ready after verification gate)

Critical constraints:
- Save sequence is fixed: write new file → update DB → delete old file. Never reorder.
- ImageStorage is the only filesystem-touching module for images.
- Edit semantics are "save on commit" — pendingImage held in State, disk write only on Save click.
- Archive does NOT delete file. Permanent delete DOES.
- minSdk 28 — use ImageDecoder, not deprecated BitmapFactory paths.
- Use existing Coil 3.3.0 (already in libs.versions.toml). Do not add a new image lib.
- `READ_MEDIA_IMAGES` permission must NOT appear in AndroidManifest — Photo Picker handles gallery without it.
- `CAMERA` permission is the only runtime permission added.
- Cache-bust Coil with `?v=<lastModifiedMs>` when path changes (most important on the edit screen).

Locked design decisions are in the "Design decisions (locked)" table — do NOT deviate without raising in PR review.

PR body must include:
- Files changed grouped by module.
- Verification gate report (build + tests + detekt + lint + manual scenarios checked off).
- Note: "First feature attached to a real file lifecycle. Future features needing image attachment (training cover, etc — v2) should reuse core/core/images/ImageStorage."
- Disk usage estimate: at JPEG q85, 1280×1280, expect ~150-400 KB per image. 100 exercises with photos ≈ 25 MB.

Stop condition: draft PR opened with verification gate passed, ready for review.
