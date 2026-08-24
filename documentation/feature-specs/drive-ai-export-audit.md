# Drive backup audit — basis for an "AI-readable Drive snapshot" enhancement

**Status:** read-only audit. This document describes the *existing* Google Drive backup/restore
implementation as of the current working tree. It designs nothing and changes no code.

**Method:** every finding was read from source and is tagged `CONFIRMED` (read directly in source),
`PARTIAL` (some evidence, gaps noted), or `NOT FOUND`. Symbol locations were found by searching the
tree (files moved during the dialogs refactor), not by trusting doc/memory paths. Where a claim was
gathered by a delegated source-reading pass, the cited file path is still the source of record.

**TL;DR of the three blockers** (full evidence in §6):

1. **An external LLM cannot read the current backup as-is.** It lives in the hidden Drive
   `appDataFolder` (app-private) **and** the payload is a binary SQLite `.db` file. Both are
   disqualifying independently.
2. **Current scope is `drive.appdata`** (+ `userinfo.email`/`userinfo.profile`). Writing to a
   user-visible "My Drive" folder requires a **new** scope — minimally `drive.file`. `drive.appdata`
   cannot write to visible space.
3. **No serializable export model of the workout data exists.** An AI-readable JSON snapshot layer
   (Room → `@Serializable` export DTOs → JSON) would have to be built from scratch.

---

## 1. Backup mechanism (current)

### 1.1 Triggers / entry points — `CONFIRMED`

There are **two** runtime paths that produce a backup, both ending in the identical
`captureSnapshot → BackupManifest → uploadBackup` sequence:

| Path | Trigger | Executes | Citation |
|---|---|---|---|
| **Manual "Create backup"** (inline) | `Action.Backup.CreateBackup` button | Runs **inline** in the settings handler coroutine — NOT via WorkManager | `feature/settings/.../mvi/handler/BackupClickHandler.kt:274–300` → `feature/settings/.../domain/BackupInteractorImpl.kt:54–70` |
| **Auto-backup** (periodic + bootstrap one-time) | First sign-in bootstrap and the periodic schedule | `BackupWorker` (`@HiltWorker CoroutineWorker`) | `core/data/backup/worker/.../BackupWorker.kt:44–87` |

- **Bootstrap.** On the first transition to authenticated, `bootstrapOrRehydrate()` sets the default
  schedule (Daily, Wi-Fi-only), calls `schedulePeriodic(DEFAULT)` and `enqueueOneTime()`, and shows a
  snackbar. Re-entry only re-arms the periodic schedule.
  `feature/settings/.../mvi/handler/BackupClickHandler.kt:162–177`, `:123–145`.
- **Scheduling.** `BackupScheduler` (implements `AutoBackupController`) enqueues a
  `PeriodicWorkRequest<BackupWorker>` under unique name `"auto_backup"` (Daily = 1 day, Weekly = 7
  days; `ManualOnly` cancels it) with `ExistingPeriodicWorkPolicy.UPDATE`, and a
  `OneTimeWorkRequest<BackupWorker>` under unique name `"one_time_backup"`
  (`ExistingWorkPolicy.KEEP`). Constraints: network type (CONNECTED vs UNMETERED depending on
  `allowOnMobileData`) and `setRequiresBatteryNotLow(true)` for the periodic one.
  `core/data/backup/worker/.../scheduler/BackupScheduler.kt:36–101`.
- **Worker is the single executor** for both periodic and one-time work; the two unique names route
  to the same class. `core/data/backup/worker/.../BackupWorker.kt:24–28`.
- Worker failure routing: `AuthRevoked` → cancel periodic + "auth paused" notification +
  `Result.failure()`; `NetworkUnavailable` → `Result.retry()`; `StorageQuotaExceeded` /
  `NotAuthenticated` → `Result.failure()`; everything else → `Result.retry()`.
  `core/data/backup/worker/.../BackupWorker.kt:97–111`.

### 1.2 Drive client library + how it's built — `CONFIRMED`

- **Not** the Google Drive Java client (`com.google.api.services.drive`), and **no**
  `GoogleAccountCredential` / `Drive.Builder`. The implementation calls the Drive **REST v3 API
  directly over Ktor**.
- `DriveApiImpl` uses a single shared `HttpClient(Android)`:
  - List/download/delete → `https://www.googleapis.com/drive/v3/files`
  - Upload → `https://www.googleapis.com/upload/drive/v3/files`
  - `core/data/backup/google-drive/.../network/DriveApiImpl.kt:97–98`.
- The client is built in `NetworkModule.provideHttpClient` with `ContentNegotiation` (kotlinx JSON,
  `ignoreUnknownKeys`/`encodeDefaults`), `Logging(LogLevel.ALL)`, the custom `DriveAuthPlugin`, and a
  `defaultRequest { url("https://www.googleapis.com/") }`. `expectSuccess = true`.
  `core/data/backup/google-drive/.../di/NetworkModule.kt:21–48`.
- Authorization is attached per-request by `DriveAuthPlugin` (a Ktor `createClientPlugin`) as
  `Authorization: Bearer <token>`, and it converts a 401 response into a typed
  `DriveException.AuthRevoked`. `core/data/backup/google-drive/.../network/DriveAuthPlugin.kt:17–35`.

### 1.3 OAuth scope(s) — `CONFIRMED` **[BLOCKER]**

`core/data/backup/google-drive/.../auth/DriveAuthScopes.kt:17–41`:

- **Requested on every `AuthorizationRequest` / `RevokeAccessRequest`:**
  - `https://www.googleapis.com/auth/drive.appdata`
  - `https://www.googleapis.com/auth/userinfo.email`
  - `https://www.googleapis.com/auth/userinfo.profile`
- **Hard-required (gates all storage):** only `drive.appdata` (`REQUIRED`, line 40). The two
  `userinfo` scopes are optional — declining them only degrades the displayed account name/email.
- There is **no** `drive.file` and **no** broad `drive` scope anywhere in the tree.

### 1.4 Destination: appDataFolder vs visible — `CONFIRMED` **[BLOCKER]**

Backups are written to the **hidden, app-private `appDataFolder`** — never to user-visible "My
Drive". Evidence:

- Upload metadata sets `parents = listOf("appDataFolder")`.
  `core/data/backup/google-drive/.../storage/DriveBackupStorage.kt:63`, const at `:151`.
- List query uses `parameter("spaces", "appDataFolder")`.
  `core/data/backup/google-drive/.../network/DriveApiImpl.kt:45`, const at `:99`.
- DTO doc confirms: "`parents` for backup uploads is always `["appDataFolder"]`".
  `core/data/backup/google-drive/.../network/DriveDtos.kt:27–31`.

The `appDataFolder` is only reachable by this app's own OAuth client under the `drive.appdata`
scope; it does not appear in the user's Drive UI and cannot be shared.

### 1.5 Payload format & contents — `CONFIRMED` **[BLOCKER]**

The uploaded payload is a **binary, raw SQLite database file** (the whole Room DB), **not** text/JSON.

- The snapshot is produced by `DatabaseSnapshotProviderImpl.captureSnapshot`: it runs
  `PRAGMA wal_checkpoint(TRUNCATE)` on the live DB then `File.copyTo`s the on-disk `app.db`.
  `core/data/database/.../snapshot/DatabaseSnapshotProviderImpl.kt:29–41`.
- The upload sets `mimeType = "application/x-sqlite3"`.
  `core/data/backup/google-drive/.../storage/DriveBackupStorage.kt:64`, const at `:152`.
- `uploadMultipart` builds a `multipart/related` body = JSON metadata part **+** `content.readBytes()`
  (the raw DB bytes) **+** closing boundary; whole file buffered in memory.
  `core/data/backup/google-drive/.../network/DriveApiImpl.kt:54–77`.
- The only JSON in the request is the Drive **file metadata** part (`name`, `parents`, `mimeType`,
  `appProperties`) — it carries no workout data. `DriveApiImpl.kt:59–65`,
  `DriveDtos.kt:32–38` (`DriveFileMetadataDto`).

### 1.6 File naming scheme — `CONFIRMED`

- Filename: `app_<createdAtEpochMs>.db` — `"${FILE_PREFIX}${createdAtEpochMs}${DB_FILE_SUFFIX}"`.
  `core/data/backup/google-drive/.../storage/DriveBackupStorage.kt:147–148`;
  `FILE_PREFIX = "app_"`, `DB_FILE_SUFFIX = ".db"` at `core/data/backup/api/.../BackupConstants.kt:14,17`.
- The manifest is **not** a sidecar file. It is stored as Drive **`appProperties`** (custom key/value
  pairs on the same file), split one entry per field to stay under Drive's 124-byte-per-pair limit:
  `app_version`, `db_schema_version`, `created_at_epoch_ms`, `db_file_size_bytes`, `device_model`
  (truncated to 100 chars). `core/data/backup/google-drive/.../manifest/ManifestPropertiesMapper.kt:26–45`.
- **Dead constant:** `BackupConstants.MANIFEST_FILE_SUFFIX = ".json"` exists with a comment about a
  "sidecar manifest uploaded alongside the db file", but a tree-wide search shows it is **defined and
  never referenced** — no separate `.json` file is uploaded today.
  `core/data/backup/api/.../BackupConstants.kt:19–20` (only occurrence in non-test source).

### 1.7 Auth / credential flow & token storage — `CONFIRMED`

- **Mechanism:** GMS Identity `AuthorizationClient` (`Identity.getAuthorizationClient(context)`).
  No `CredentialManager`, no legacy `GoogleSignIn` client, no Drive-Java `GoogleAccountCredential`.
  `core/data/backup/google-drive/.../auth/DriveBackupAuth.kt:54–55`;
  provider at `core/data/backup/google-drive/.../di/AuthProvidersModule.kt:18–22`.
- **Sign-in:** `signIn()` builds `AuthorizationRequest.setRequestedScopes(ALL)` and calls
  `authorize()`. If `hasResolution()` it returns `SignInResult.NeedsResolution(intentSender)` for the
  UI to launch; otherwise it checks for missing required scopes, captures the access token, fetches
  userinfo, and stores the account. `DriveBackupAuth.kt:74–88, 157–177`.
- **Completion:** `completeSignIn(intent)` → `getAuthorizationResultFromIntent`, partial-grant guard
  (`MissingRequiredScope`), token capture, userinfo, persist. `DriveBackupAuth.kt:90–121`.
- **Sign-out:** `RevokeAccessRequest` via `revokeAccess` (chosen over the OAuth2 revoke endpoint
  because it also clears the GMS-local token cache), then local clear. `DriveBackupAuth.kt:130–142`.
- **Token storage:** access token + expiry persisted in a **Preferences DataStore** named
  `"backup_account_prefs"`, keys `access_token` / `access_token_expires_at` (alongside `email` /
  `display_name`). `core/data/backup/google-drive/.../auth/AccountDataStoreImpl.kt:49–61, 75–81`.
- **Token TTL:** `TOKEN_TTL_MS = 50L * 60 * 1000` (50 min), defined in
  `core/data/backup/google-drive/.../auth/TokenSnapshot.kt`.
- **Token serving:** `DriveAuthTokenProvider.currentToken()` returns `null` if no account; else
  prefers the cached token while unexpired; else does a silent `authorize()` refresh and re-caches.
  `core/data/backup/google-drive/.../auth/DriveAuthTokenProvider.kt:35–67`.
- **Identity:** email + display name come from a follow-up call to
  `https://www.googleapis.com/oauth2/v3/userinfo` via `UserInfoFetcher` (best-effort; falls back to a
  `drive_account` placeholder). `DriveBackupAuth.kt:48–51, 152–155, 195–201`;
  `core/data/backup/google-drive/.../auth/UserInfoFetcherImpl.kt`.

### 1.8 Existing retention / rotation — `CONFIRMED` (rotation exists)

- `MAX_BACKUPS = 3` per account. `core/data/backup/api/.../BackupConstants.kt:10–11`.
- After each successful upload, `DriveBackupStorage.rotate()` re-lists and deletes the oldest entries
  beyond the cap; rotation is **best-effort** and never fails the upload.
  `core/data/backup/google-drive/.../storage/DriveBackupStorage.kt:56–75, 99–115`.
- Pure selection logic: `RotationPolicy.refsToDelete` sorts by `manifest.createdAtEpochMs` ascending
  and drops `size - max`. `core/data/backup/google-drive/.../storage/RotationPolicy.kt:11–19`.

### 1.9 Upload robustness — `CONFIRMED`

- **Single-shot, fully buffered.** Upload concatenates metadata + entire DB bytes
  (`content.readBytes()`) into one in-memory `ByteArray` and POSTs with `uploadType=multipart`. **Not
  resumable.** The class doc explicitly accepts this trade-off (bounded to `MAX_BACKUPS=3` and
  single-digit-MB DBs). `core/data/backup/google-drive/.../network/DriveApiImpl.kt:22–30, 54–77`.
- **Download** also buffers fully via `bodyAsBytes()` then `writeBytes`; the caller verifies size
  against `manifest.dbFileSizeBytes` with a 16-byte tolerance, else `CorruptedBackup`.
  `DriveApiImpl.kt:79–88`; `DriveBackupStorage.kt:77–87, 131–145`.
- **No temp-name-then-rename on the Drive side** — upload is a direct `files.create`. The 401 path
  invalidates the token and retries the call exactly once (`withTokenRefreshOn401`).
  `DriveBackupStorage.kt:117–129`.
- **Partial-write protection on the local DB side** (restore, not upload): `captureSnapshot` does NOT
  delete the target on failure (caller owns cleanup), but `restoreFromSnapshot` /
  `rollbackToPreRestoreBackup` write to a same-directory `.tmp` and atomically `renameTo` the live
  slot. `core/data/database/.../snapshot/DatabaseSnapshotProviderImpl.kt:164–200, 83–109`.

---

## 2. Restore mechanism (current) — the read contract — `CONFIRMED`

The restore "read contract" is **whole-file SQLite replacement**. There is no field-level
deserialization and no text/JSON read path anywhere.

**Drive → live DB (`BackupInteractorImpl.restoreLatest`,
`feature/settings/.../domain/BackupInteractorImpl.kt:78–142`):**

1. `listBackups()` → newest `BackupRef` (else `CorruptedBackup("no backups available")`).
2. Schema gate: if `backupSchemaVersion > current` → `BackupTooNew`; if `backupSchemaVersion <
   current` and `!hasMigrationPath(...)` → `MissingMigrationPath`.
3. `preserveCurrentDb()` → copies live DB to `cache/pre_restore_backup.db` (WAL-checkpointed) so undo
   / rollback has a source. `DatabaseSnapshotProviderImpl.kt:67–81`.
4. `markRestoreInProgress(RestoreInProgressContext(...))` (DataStore flag + payload).
5. `downloadBackup(ref, tempFile)` → binary DB to a cache temp file.
6. `restoreFromSnapshot(tempFile)`: validate the 16-byte `"SQLite format 3\0"` magic header, peek
   `PRAGMA user_version`, reject if newer than running schema, `appDatabase.close()`, delete
   `-wal`/`-shm`, copy to `.tmp`, atomic `renameTo` the live `app.db`.
   `DatabaseSnapshotProviderImpl.kt:164–214`.
7. On any pre-swap failure → `rollbackPreSwapFailure()` deletes the preserved file + clears the flag.

**Post-restart recovery (`feature/recovery/.../domain/RestoreRecoveryCoordinator.kt`):**

- **Scenario 1 (`handlePostRestoreLaunch`, :71–83, 123–158).** Called from `BaseApplication.onCreate`
  when `restore_in_progress` is set. Peeks `currentSchemaVersion()`; on success clears the flag, marks
  an undo slot, publishes `AppDialog.RestoreSuccess`. On failure rolls back to the preserved snapshot,
  records a Crashlytics non-fatal, publishes `AppDialog.RestoreFailure`, and asks the caller to
  restart.
- **Scenario 3 undo (`performUndoRestore`, :104–121).** `rollbackToPreRestoreBackup()` →
  `AppDialog.UndoRestoreSuccess` → restart.
- UI side: `BackupClickHandler.confirmRestore()` drives the restore then `scheduleAppRestart()` after a
  2 s delay. `feature/settings/.../mvi/handler/BackupClickHandler.kt:344–401`.
- `restartApp()` relaunches the package with `NEW_TASK|CLEAR_TASK` and `Runtime.exit(0)`.
  `RestoreRecoveryCoordinator.kt:179–186`.

**Error taxonomy** (the typed contract both backup and restore speak):
`NotAuthenticated`, `NetworkUnavailable`, `AuthRevoked`, `MissingRequiredScope`,
`StorageQuotaExceeded`, `CorruptedBackup(reason)`, `BackupTooNew(…)`, `MissingMigrationPath(…)`,
`Io(cause)`, `Unknown(cause)`. `core/data/backup/api/.../error/BackupError.kt:10–71`.

---

## 3. Data model

### 3.1 Training-related entities (fields/relations) — `CONFIRMED`

Schema version **6** (`core/data/database/.../migration/MigrationsRegistry.kt:15`,
`APP_DATABASE_VERSION = 6`). DB file name `app.db`
(`core/data/database/.../AppDatabase.kt:58`). `exportSchema = true`. Type converters:
`UuidConverter`, `PlanSetsConverter`. The `@Database` entity list (read directly,
`AppDatabase.kt:28–42`) has **9 entities**:

| Entity | tableName | Key fields / type | Relations | File |
|---|---|---|---|---|
| `TrainingEntity` | `training_table` | PK `uuid: Uuid`; `name`, `description?`, `isAdhoc`, `archived`, `createdAt`, `archivedAt?` | — | `.../database/training/TrainingEntity.kt` |
| `TrainingExerciseEntity` | `training_exercise_table` | composite PK `(training_uuid, exercise_uuid)`; `position: Int`, `planSets: String?` (JSON `List<PlanSetDataModel>`) | FK→`TrainingEntity` (CASCADE), FK→`ExerciseEntity` (RESTRICT) | `.../database/training/TrainingExerciseEntity.kt` |
| `ExerciseEntity` | `exercise_table` | PK `uuid`; `name` (NOCASE), `type: ExerciseTypeEntity`, `description?`, `imagePath?`, `archived`, `createdAt`, `archivedAt?`, `lastAdhocSets: String?` (JSON), `isAdhoc` | — | `.../database/exercise/ExerciseEntity.kt` |
| `SessionEntity` | `session_table` | PK `uuid`; `trainingUuid`, `state: SessionStateEntity`, `startedAt`, `finishedAt?` | FK→`TrainingEntity` (CASCADE) | `.../database/session/SessionEntity.kt` |
| `PerformedExerciseEntity` | `performed_exercise_table` | PK `uuid`; `sessionUuid`, `exerciseUuid`, `position`, `skipped` | FK→`SessionEntity` (CASCADE), FK→`ExerciseEntity` (RESTRICT) | `.../database/session/PerformedExerciseEntity.kt` |
| `SetEntity` | `set_table` | PK `uuid`; `performedExerciseUuid`, `position`, `reps: Int`, `weight: Double?`, `type: SetTypeEntity` | FK→`PerformedExerciseEntity` (CASCADE) | `.../database/session/model/SetEntity.kt` |
| `TagEntity` | `tag_table` | PK `uuid`; `name` (NOCASE, unique) | — | `.../database/tag/TagEntity.kt` |
| `ExerciseTagEntity` | `exercise_tag_table` | composite PK `(exercise_uuid, tag_uuid)` | FK→`ExerciseEntity` (CASCADE), FK→`TagEntity` (CASCADE) | `.../database/tag/ExerciseTagEntity.kt` |
| `TrainingTagEntity` | `training_tag_table` | composite PK `(training_uuid, tag_uuid)` | FK→`TrainingEntity` (CASCADE), FK→`TagEntity` (CASCADE) | `.../database/tag/TrainingTagEntity.kt` |

Enum column types (not entities): `ExerciseTypeEntity {WEIGHTED, WEIGHTLESS}`,
`SessionStateEntity {IN_PROGRESS, FINISHED}`, `SetTypeEntity {WARM, WORK, FAIL, DROP}`.

> Note: the per-field/index/FK specifics in the table above were gathered by a delegated read of each
> entity file (paths cited). The `@Database` entity list, DAO surface, DB name, converters, and
> schema version were verified directly in `AppDatabase.kt` and `MigrationsRegistry.kt`.

### 3.2 DAOs + domain models + layering — `CONFIRMED`

- **9 DAOs**, exposed off `AppDatabase` (`AppDatabase.kt:46–54`): `trainingDao`, `trainingExerciseDao`,
  `exerciseDao`, `sessionDao`, `performedExerciseDao`, `setDao`, `tagDao`, `exerciseTagDao`,
  `trainingTagDao`.
- **Layering** (per CLAUDE.md and confirmed by module layout): Room `*Entity` → `*DataModel`
  (in `core/data/...`) → `*Domain` (in `feature/<x>/domain/model/`). Data→domain mapping in
  `feature/<x>/domain/mapper/`; domain→UI in `feature/<x>/mvi/mapper/`. Two Detekt rules
  (`DomainLayerPurityRule`, `DomainLayerNoUiRule`) guard the boundary (§4.4).

### 3.3 Existing Room→export DTO/mapper? — `NOT FOUND` **[BLOCKER]**

There is **no** serializable export model of the workout data and **no** Room→export mapper. Tree-wide
evidence (source only, tests/build excluded):

- `@Serializable` appears only on: Drive wire DTOs (`DriveDtos.kt`, `UserInfoFetcherImpl.kt`); Room
  **column-storage** models `PlanSetDataModel` + `SetTypeDataModel`
  (`core/data/database/.../sets/`); and **navigation route args** (`core/ui/navigation/Screen.kt`,
  `core/ui/plan-editor/model/*`). None of these is a training/workout data-export model.
- **None of the 9 Room `@Entity` classes is `@Serializable`.**
- No `Gson` / `Moshi` anywhere (`com.google.gson` / `com.squareup.moshi` → 0 hits).
- No symbol resembling `ExportDto` / `ExportModel` / `JsonExport` / `*Export` for training/workout.

Serialization plumbing that *does* exist: `kotlinx.serialization.json` is on the classpath in
`core/data/database`, `core/data/backup/google-drive`, `feature/exercise`, `feature/plan-editor`, and
the navigation/plan-editor UI modules — but only for Room column JSON, Drive wire DTOs, and nav args.

### 3.4 schemaVersion, minSdk — `CONFIRMED`

- Room `schemaVersion` = **6** (`MigrationsRegistry.kt:15`). Registered migrations: `MIGRATIONS =
  arrayOf(Migration6)`; `MIN_SUPPORTED_SCHEMA_VERSION` derived from it (versions 1–4 are
  non-migratable pre-Play-Store history). `MigrationsRegistry.kt:29–44`.
- `minSdk = 28`, `targetSdk = 37`, `compileSdk = 36` (`gradle/libs.versions.toml:8–10`).

---

## 4. Architecture & placement

### 4.1 Current real paths of backup/recovery symbols — `CONFIRMED`

| Concern | Symbol(s) | Module / path |
|---|---|---|
| API contracts | `BackupStorage`, `BackupAuth`, `BackupManifest`, `BackupRef`, `BackupError`, `BackupConstants`, `RestoreStateRepository`, `AutoBackupController`, `BackupPreferencesRepository` | `core/data/backup/api/` |
| Drive impl | `DriveBackupStorage`, `DriveApi(Impl)`, `DriveDtos`, `DriveFileMapper`, `RotationPolicy`, `DriveAuthPlugin`, `DriveBackupAuth`, `DriveAuthTokenProvider`, `AccountDataStore(Impl)`, `DriveAuthScopes`, `UserInfoFetcher(Impl)`, `ManifestPropertiesMapper`, `DriveErrorMapper` | `core/data/backup/google-drive/` |
| Scheduling/state repos | `BackupPreferencesRepositoryImpl`, `RestoreStateRepositoryImpl` | `core/data/backup/scheduling/` |
| Worker | `BackupWorker`, `BackupScheduler`, `BackupNotificationHelper` | `core/data/backup/worker/` |
| Snapshot (DB file ops) | `DatabaseSnapshotProvider(Impl)` | `core/data/database/.../snapshot/` |
| Recovery flows | `RestoreRecoveryCoordinator`, `StartupMigrationCoordinator`, `RecoveryActivity`, `RecoveryBootstrap`, diagnostics exporters | `feature/recovery/` |
| Restore dialogs | `RestoreFailureDialog`, `RestoreSuccessDialog`, `UndoRestoreConfirmationDialog`, `UndoRestoreSuccessDialog` | `feature/app-dialogs/impl/.../ui/` |
| Settings UI / MVI | `BackupClickHandler`, `BackupInteractor(Impl)`, `SettingsGraph`, backup `ui/components/*`, backup `mvi/model/*` | `feature/settings/` |

### 4.2 Module shapes, deps, DB-free status — `CONFIRMED`

- `core/data/backup/google-drive/build.gradle.kts:1–27`: plugins `convention.androidLibrary` +
  `serialization`. Deps: `:core:core`, `:core:data:backup:api`, **`:core:data:database`**,
  `:core:data:dataStore`, kotlinx-serialization-json, Ktor (core/android/logging/content-negotiation/
  serialization-json), **`google.play.services.auth`**, `coroutines.play.services`, datastore.
- `feature/recovery/build.gradle.kts:1–26`: plugin `convention.composeLibrary`. Deps: `:core:core`,
  `:core:ui:kit`, `:core:ui:navigation`, **`:core:data:database`**, `:core:data:backup:api`,
  `:feature:app-dialogs:api`.
- **`feature/recovery` "DB-free" status:** it *depends on the database module* (for
  `DatabaseSnapshotProvider` and `RestoreRecoveryCoordinator`) but is bound by an invariant to **never
  open Room** — enforced by `RecoveryActivityDbFreeTest` with a `FailFastDatabaseModule` tripwire that
  throws on `openHelper` access. `RecoveryActivity` is a plain `@AndroidEntryPoint ComponentActivity`
  with **no MVI Store** (callback-based Compose). `feature/recovery/src/androidTest/.../RecoveryActivityDbFreeTest.kt`,
  `feature/recovery/src/main/.../RecoveryActivity.kt`.
- All backup modules apply Hilt via the convention plugin.

### 4.3 Hilt provisioning of the Drive client — `CONFIRMED`

All DI lives in `core/data/backup/google-drive/.../di/`, everything app-scoped
(**`@SingleIn(AppScope)`**):

- `NetworkModule` (`@Module object`): `@Provides @Singleton provideHttpClient(authTokenProvider)`.
  `di/NetworkModule.kt:21–48`.
- `AuthProvidersModule` (`@Module object`): `@Provides @Singleton provideAuthorizationClient(context)
  = Identity.getAuthorizationClient(context)`. `di/AuthProvidersModule.kt:14–23`.
- `AuthBindingsModule` (`@Module interface`, all `@Binds @Singleton`): `BackupStorage←DriveBackupStorage`,
  `DriveApi←DriveApiImpl`, `BackupAuth←DriveBackupAuth`, `AuthTokenProvider←DriveAuthTokenProvider`,
  `AccountDataStore←AccountDataStoreImpl`, `TokenInvalidator←DriveTokenInvalidator`,
  `UserInfoFetcher←UserInfoFetcherImpl`. `di/AuthBindingsModule.kt:24–55`.
- `DatabaseSnapshotProvider` is bound `@Singleton` in `core/data/database/.../di/CoreDatabaseBindingsModule.kt`.

> Scope note for new code: per the project's `MetroScopeRule` (§4.4), a name-matched
> (`Repository` / `DataStore` / `Database` / `Storage` / `StoreDispatchers` / `Handler` /
> `Interactor` / `Mapper`) constructor-`@Inject` class must declare `@SingleIn(<Scope>::class)` —
> app-scoped concerns (`Storage` / `Repository` / `DataStore` / `Database`) as `@SingleIn(AppScope)`,
> feature concerns (`Handler` / `Interactor` / `Mapper`) as `@SingleIn(<Feature>Scope)` (a `*Handler`
> must not be `@SingleIn(AppScope)`). A Metro `Store` is UNSCOPED (class-level `@Inject`, retained by
> the ViewModelStore via `rememberMetroStoreProcessor`). The existing Drive graph is uniformly
> `@SingleIn(AppScope)`, which matches.

### 4.4 Constraining custom Detekt rules — `CONFIRMED`

Rules live in `lint-rules/src/main/kotlin/io/github/stslex/workeeper/lint_rules/` (rule set registered
in `MviArchitectureRules.kt`). The ones most likely to constrain new export/snapshot code:

- **`MviHandlerConstructorRule`** — every `*Handler` needs a primary constructor with `@Inject` and ≥1
  param and must implement `Handler`; `NavigationHandler` is the documented exception.
- **`MetroScopeRule`** — scope-by-suffix enforcement (see §4.3 note); a name-matched
  (`Repository`/`DataStore`/`Database`/`Storage`/`StoreDispatchers`/`Handler`/`Interactor`/`Mapper`)
  constructor-`@Inject` class must declare `@SingleIn(<Scope>::class)`; a `*Handler` must not be
  `@SingleIn(AppScope)` (feature-scoped only). A Metro `Store` is UNSCOPED. (`javax.inject.@Singleton`
  still resolves under Metro's `includeJavax` but the graph ignores it — the rule flags it.)
- **`DomainLayerPurityRule`** — domain layer may not import `core.data.*` model types (suffixes
  `DataModel`/`Entity`/`Dto`/`DataType`/…) except inside `/domain/mapper/` or `.api.*`.
- **`DomainLayerNoUiRule`** — domain layer may not import Compose / `R` / `mvi` / `ui` types.
- **`UiLayerNoDataRule`** — UI layer may not import `core.data.*` model types.
- MVI structure rules: `MviStateImmutabilityRule`, `MviActionNamingRule`, `MviEventNamingRule`,
  `MviHandlerNamingRule`, `MviStoreExtensionRule`, `MviStoreStateRule`, `ComposableStateRule`.

Implication for an export feature: an `@Serializable` **export DTO** must NOT live in the domain layer
(it would be a `*Dto`/data type) — it belongs in the data layer (e.g. the backup/database module),
mapped to domain `*Domain` types only at the boundary.

### 4.5 Canonical NavigationHandler / MVI reference — `CONFIRMED`

`feature/home` is the canonical template (per `.claude/skills/add-feature.md`):
`NavigationHandler` at `feature/home/.../mvi/handler/NavigationHandler.kt` (`@ViewModelScoped`,
`@Inject constructor(navigator)`, `implements Handler<Action.Navigation>`); `Store`/`State`/`Action`/
`Event` contract in `feature/home/.../mvi/store/HomeStore.kt`; `@HiltViewModel HomeStoreImpl` extends
`BaseStore`. For a settings-resident enhancement, `feature/settings` is the closest in-tree analogue
(its `BackupClickHandler` is the working example of an `@Inject`/`Handler`/`@ViewModelScoped` handler
that consumes the backup API).

---

## 5. Consent / permissions / manifest — `CONFIRMED`

- **Permissions (source manifests):**
  - `android.permission.CAMERA` — `app/app/src/main/AndroidManifest.xml:5`.
  - `android.permission.POST_NOTIFICATIONS` — `core/data/backup/worker/src/main/AndroidManifest.xml:4`
    (for backup notifications).
  - **`android.permission.INTERNET` is NOT declared in any source manifest.** It is contributed
    transitively by a dependency AAR at manifest-merge time — verified present in the final
    application merged manifests (`app/dev`, `app/store` under `build/intermediates/merged_manifest/`)
    and absent from every `src/main` manifest. No Drive-specific permission is needed (GMS handles
    consent).
- **Consent UI flow:** GMS `authorize()` returns a resolution `PendingIntent` →
  `SignInResult.NeedsResolution(intentSender)` → settings handler emits
  `Event.AuthResolutionRequested(intentSender)` → `SettingsGraph` launches it via
  `rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult())`, and feeds
  the result back as `Action.Backup.HandleAuthResult`. `feature/settings/.../ui/SettingsGraph.kt:36–57`;
  `feature/settings/.../mvi/handler/BackupClickHandler.kt:179–242`.
- **Manifest notes:** `RecoveryActivity` is `exported=false` with no launcher intent-filter (only
  reached programmatically). The default `androidx.work.WorkManagerInitializer` is removed via
  `tools:node="remove"` — WorkManager is initialized on-demand through `BaseApplication`'s
  `Configuration.Provider` + `HiltWorkerFactory`. `app/app/src/main/AndroidManifest.xml:33–81`.
- `google-services.json` is present at repo root (686 bytes) — supplies the GMS/OAuth client config;
  its contents were not inspected in this audit.

---

## 6. Decision-blocking answers (with evidence)

### a. Can an external LLM read the current backup from Drive as-is? — **No.**

Two independent disqualifiers:

1. **Location.** Backups live in the hidden `appDataFolder` (`spaces=appDataFolder`,
   `parents=["appDataFolder"]`). This space is private to this app's OAuth client under the
   `drive.appdata` scope — it is not visible in the user's Drive, not shareable, and not reachable by a
   general Drive access token. `DriveApiImpl.kt:45`, `DriveBackupStorage.kt:63`.
2. **Format.** The payload is a **binary SQLite `.db`** (`mimeType application/x-sqlite3`), produced by
   a WAL-checkpoint + raw file copy — not text/JSON an LLM can parse.
   `DriveBackupStorage.kt:64`, `DatabaseSnapshotProviderImpl.kt:29–41`.

To make a backup AI-readable, **both** would have to change (a visible/text destination *and* a text
serialization).

### b. Current scope, and does a visible folder need a new scope? — **`drive.appdata`; yes, needs `drive.file`.**

- Current: `drive.appdata` (hard-required) + `userinfo.email` + `userinfo.profile`.
  `DriveAuthScopes.kt:19–40`.
- `drive.appdata` grants access **only** to the hidden app-data folder; it cannot create or write
  files in user-visible "My Drive". Writing a visible (and shareable) file requires at minimum the
  **`drive.file`** scope (per-file access to files the app creates). The broad `drive` scope is not
  required and is a Google-"restricted" scope requiring app verification — avoid it. Adding a scope
  means updating `DriveAuthScopes.ALL` and keeping sign-in / silent refresh / revoke in lock-step:
  `AuthorizationClient.authorize()` raises a resolution (no silent token) whenever a *requested* scope
  is ungranted, so requesting `drive.file` on the silent path for an appdata-only account would break
  its token refresh. A mismatch on revoke leaves userinfo-derived identity stranded in the GMS cache.

### c. Does a serializable export model exist? — **No; must be built from scratch.**

No `@Serializable` export DTO and no Room→export mapper exists for the workout data (§3.3). The
existing `@Serializable` types are Drive wire DTOs, Room column-storage models
(`PlanSetDataModel`/`SetTypeDataModel`), and navigation route args — none represents an exportable
snapshot of trainings/sessions/sets. An AI-readable JSON snapshot would require a new layer: read the
9 entities via their DAOs → map to new `@Serializable` export DTOs → encode JSON. kotlinx.serialization
is already on the classpath, so no new dependency is needed; the rule constraints in §4.4 dictate that
such DTOs live in the data layer, not the domain layer.

---

## 7. UNKNOWNs / not resolvable from source

- **OAuth client ID / `google-services.json` contents.** The file exists at root but its contents
  (web client id used by `AuthorizationClient`) were not inspected. The `AuthorizationClient` call site
  does not pass an explicit server-client-id in source.
- **R8/ProGuard keep rules** for kotlinx.serialization in release builds were not audited (relevant if
  a new export DTO is added and obfuscated).
- **Per-DAO method signatures** were not exhaustively enumerated — only the DAO set and the entities
  they expose. A from-scratch export would need a read query per entity (or reuse of existing
  list/observe queries; note the `is_adhoc = 0` filter invariant on exercise list queries).
- **Drive `appProperties` query semantics for a future AI agent** are moot, since the file is in
  `appDataFolder` and not externally reachable regardless.
- **Whether any auto-backup currently runs in CI/headless** — not determinable from source; depends on
  device/account state at runtime.
