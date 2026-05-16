# Feature spec — Drive backup and restore

**Status:** Landed across PRs introducing `core/data/backup/api`,
`core/data/backup/google-drive`, `core/data/backup/scheduling`,
`core/data/backup/worker`, and the `feature/settings` integration. For current
architecture, see [architecture.md](../architecture.md). This spec captures the
shipped surface and the load-bearing decisions behind it.

Drive backup runs from `feature/settings` and persists a full SQLite snapshot of
the user's workout database to the user's own Google Drive `drive.appdata`
folder. Sign-in is one-shot via GMS `AuthorizationClient`; after that, backups
fire automatically on a user-configurable schedule (Weekly by default) plus an
immediate one-time backup after the first successful sign-in. Restore replaces
the live database with a downloaded snapshot and restarts the app. The api
layer (`core/data/backup/api`) is provider-neutral; the only concrete impl
today is Google Drive, but a future self-hosted backend can land alongside it
without touching the feature module.

## Status

- Sign in / sign out / create backup / list backups / restore latest backup are
  shipped, with full localization (EN + RU) and Compose previews.
- Auto-backup scheduling (Daily / Weekly / ManualOnly + allow-on-mobile-data
  toggle) and the first-sign-in bootstrap (immediate one-time backup + snackbar
  + Weekly periodic) are shipped.
- **Upcoming**: schema-migration safety net and user-initiated undo of last
  restore land via the recovery work — see
  [backup-recovery.md](backup-recovery.md). The v1 restore path described here
  remains the foundation; recovery extends it with pre-restore migration-path
  checks, automatic rollback on migration crash, and a preserved-`.db`-backed
  undo slot.
- `BackupError.AuthRevoked` cancels the periodic work, raises a persistent
  low-importance notification, and reflects an "Auto-backup paused" banner in
  Settings until re-sign-in.
- v1 surfaces only the latest backup for restore — there is no picker for older
  versions. See [Out of scope](#out-of-scope-and-decisions).
- Backups are unencrypted at rest in Drive `appdata` (Drive's own at-rest
  encryption + HTTPS-in-transit). No client-side encryption layer.

## Module layout

| Module | Purpose |
|---|---|
| `core/data/backup/api` | Provider-neutral contracts and data types: `BackupAuth`, `BackupStorage`, `BackupResult<T>`, `BackupError`, plus the `scheduling/` subpackage (`AutoBackupController`, `BackupPreferencesRepository`, `BackupSchedule`, `BackupErrorCode`, `BackupPreferences`, `AutoBackupWorkInfo`). |
| `core/data/backup/google-drive` | Drive REST impl: `DriveBackupAuth`, `DriveBackupStorage`, `DriveApi[Impl]`, `DriveAuthPlugin`, `DriveAuthTokenProvider`, `AccountDataStore[Impl]`, `UserInfoFetcher`, `RotationPolicy`, `ManifestPropertiesMapper`, `DriveErrorMapper`. |
| `core/data/backup/scheduling` | DataStore-backed `BackupPreferencesRepositoryImpl` (single `@Singleton` writer of the persisted schedule + last-error + bootstrap-flag tuple). |
| `core/data/backup/worker` | `BackupWorker` (HiltWorker), `BackupScheduler` (`AutoBackupController` impl over WorkManager), `BackupNotificationHelper` (auth-paused notification channel + lifecycle). |
| `core/data/database/snapshot` | `DatabaseSnapshotProvider[Impl]` — WAL checkpoint + file copy for capture; SQLite-magic verification + atomic file replace for restore. Lives in the database module because it is intrusive on the live Room instance. |
| `feature/settings` | UI integration: `BackupSection`, `FrequencyPickerBottomSheet`, `AutoBackupRow`, `AuthPausedBanner`, `RestoreConfirmationDialog`, `SignOutConfirmationDialog`, and the `BackupClickHandler` that orchestrates all four flows (sign-in, manual backup, restore, scheduling). |

The feature module depends only on `core/data/backup/api`; concrete impls are
bound at the app graph via `:core:data:backup:google-drive`,
`:core:data:backup:scheduling`, and `:core:data:backup:worker`, all declared as
`implementation` from `app/app/build.gradle.kts`. This means a future
non-Drive provider can be dropped in as a sibling module without touching
feature code.

## Key types

| Type | File |
|---|---|
| `BackupAuth` | `core/data/backup/api/.../BackupAuth.kt` |
| `BackupStorage` | `core/data/backup/api/.../BackupStorage.kt` |
| `BackupResult<T>` | `core/data/backup/api/.../result/BackupResult.kt` |
| `BackupError` | `core/data/backup/api/.../error/BackupError.kt` |
| `BackupManifest` | `core/data/backup/api/.../model/BackupManifest.kt` |
| `BackupRef` | `core/data/backup/api/.../model/BackupRef.kt` |
| `AuthState` | `core/data/backup/api/.../model/AuthState.kt` |
| `Account` | `core/data/backup/api/.../model/Account.kt` |
| `SignInResult` | `core/data/backup/api/.../model/SignInResult.kt` |
| `BackupConstants` | `core/data/backup/api/.../BackupConstants.kt` |
| `AutoBackupController` | `core/data/backup/api/scheduling/AutoBackupController.kt` |
| `BackupPreferencesRepository` | `core/data/backup/api/scheduling/BackupPreferencesRepository.kt` |
| `BackupSchedule` | `core/data/backup/api/scheduling/BackupSchedule.kt` |
| `BackupErrorCode` | `core/data/backup/api/scheduling/BackupErrorCode.kt` |
| `BackupPreferences` | `core/data/backup/api/scheduling/BackupPreferences.kt` |
| `AutoBackupWorkInfo` | `core/data/backup/api/scheduling/AutoBackupController.kt` |
| `DatabaseSnapshotProvider` | `core/data/database/.../snapshot/DatabaseSnapshotProvider.kt` |

## Architectural rules

- **api-vs-impl split.** The feature module depends only on the
  `core/data/backup/api` module. Impl modules (`google-drive`, `scheduling`,
  `worker`) are wired at the app graph. The
  [`DomainLayerPurityRule`](../lint-rules.md#domainlayerpurityrule) explicitly
  exempts `core.data.<feature>.api.*` so the settings domain layer can import
  `BackupResult`, `BackupError`, etc. directly.
- **MVI dialog state shape.** Every modal surface in `feature/settings` lives on
  `SettingsStore.State.dialogState: DialogState` — see
  [`.claude/skills/mvi-dialog-state.md`](../../.claude/skills/mvi-dialog-state.md).
- **HiltScopeRule compliance.** Class naming matters: `BackupStorage` /
  `BackupAuth` / `*DataStore*` / `*Repository*` are `@Singleton`; handlers
  (`BackupClickHandler`) are `@ViewModelScoped`; `BackupWorker` is constructed
  by Hilt's `HiltWorkerFactory` via `@AssistedInject` and so falls outside the
  rule. `BackupScheduler` is `@Singleton` explicitly via class annotation +
  `@Binds`. See [lint-rules.md → HiltScopeRule](../lint-rules.md#hiltscoperule).

## Authentication

### Library choice

Sign-in flows through GMS `com.google.android.gms.auth.api.identity.AuthorizationClient`
(NOT the legacy `GoogleSignIn` API and NOT a direct OAuth2 HTTP flow). The
official Android guidance is
[developer.android.com/identity/authorization](https://developer.android.com/identity/authorization).
The contract lives in `core/data/backup/api/BackupAuth.kt` and the impl is
`DriveBackupAuth` (`core/data/backup/google-drive/.../auth/DriveBackupAuth.kt`).

### Why `AuthorizationClient` and not Credential Manager

- Credential Manager (`androidx.credentials`) is the modern *authentication*
  surface — "who is this user, log them into the app". We do not log the user
  into the app; the local database is the entire identity surface and works
  without Google.
- `AuthorizationClient` is the modern *authorization* surface — "grant this app
  scoped access (Drive `appdata`)". This is exactly what backup needs: a
  resumable access token bound to a specific scope.
- The Drive-side identity we *do* surface (email + display name in the
  Settings header) is a UX nicety. It is fetched from
  `https://www.googleapis.com/oauth2/v3/userinfo` via `UserInfoFetcherImpl`
  using the same access token we already hold for Drive, NOT from an
  authentication library. `AuthorizationResult.account` / `email` are `null` by
  design on this code path.

### Scopes

All requests use the exact same scope set, declared in `DriveAuthScopes`
(`core/data/backup/google-drive/.../auth/DriveAuthScopes.kt`):

- `drive.appdata` — file-level scope for the private appdata folder.
- `userinfo.email`, `userinfo.profile` — for the userinfo fetch only.

Keeping the three scopes identical on `signIn`, silent refresh, and
`revokeAccess` is load-bearing. A scope mismatch on refresh makes GMS treat the
request as new and re-prompts the user; a mismatch on revoke leaves
userinfo-derived identity stranded in the GMS cache.

### Sign-in flow

1. User taps **Sign in to Google Drive** in `BackupSection`
   (`feature/settings/.../ui/components/BackupSection.kt`). The handler
   dispatches `Action.Backup.SignIn`.
2. `BackupClickHandler.signIn()` calls `interactor.signIn()` → `BackupAuth.signIn()`.
3. `DriveBackupAuth.signIn()` builds an `AuthorizationRequest` with
   `setRequestedScopes(DriveAuthScopes.ALL)` and awaits
   `authorizationClient.authorize(request)`.
4. The result is one of:
   - **Silent success** (`hasResolution() == false`): the user previously
     granted the scope and GMS returns a fresh `accessToken` directly. The
     token is cached in `AccountDataStore` with a 50-minute TTL.
   - **Resolution required** (`hasResolution() == true`): the impl returns
     `SignInResult.NeedsResolution(intentSender)`. The handler emits
     `Event.AuthResolutionRequested` which the graph (`SettingsGraph.kt`)
     consumes via `rememberLauncherForActivityResult(StartIntentSenderForResult)`.
     After the user consents, the result intent flows back through
     `Action.Backup.HandleAuthResult` → `interactor.completeSignIn(intent)` →
     `AuthorizationClient.getAuthorizationResultFromIntent(...)`. The captured
     `accessToken` lands in `AccountDataStore` the same way as the silent path.
5. With a valid `accessToken`, `UserInfoFetcherImpl.fetch(token)` calls
   `/oauth2/v3/userinfo` over Ktor and writes the resolved `Account(email,
   displayName)` to `AccountDataStore`. `BackupAuth.state: StateFlow<AuthState>`
   flips to `SignedIn(account)`.

The full sequence is verifiable via `DriveBackupAuthTest` in
`core/data/backup/google-drive/src/test/`.

### Sign-out flow

`BackupClickHandler.confirmSignOut()` calls
`autoBackupController.cancelPeriodic()` *before* `interactor.signOut()` so the
periodic work is gone before the session is. The impl then:

1. Builds a `RevokeAccessRequest` with the same `DriveAuthScopes.ALL`.
2. Calls `authorizationClient.revokeAccess(revokeRequest).await()`.
3. Clears the token and account from `AccountDataStore` regardless of whether
   the remote revoke succeeded (local-clear is unconditional so users can
   "sign out" even when offline).

**Critical gotcha**: revoke MUST go through `AuthorizationClient.revokeAccess`,
NOT through `https://oauth2.googleapis.com/revoke?token=...`. The HTTP endpoint
revokes the OAuth grant server-side but leaves the GMS-local token cache
populated. On the next silent `signIn`, GMS happily returns the now-stale token
and Drive then rejects it with a 401. This was a real bug during development;
the SDK path clears both caches atomically and is the only correct path. See
the inline KDoc on `DriveBackupAuth.signOut`.

### Token caching

The Ktor `HttpClient` used by `DriveApi` is configured (see
`network/DriveAuthPluginConfig.kt`) to attach an `Authorization: Bearer
<token>` header via the `DriveAuthPlugin` interceptor. The token comes from
`DriveAuthTokenProvider.currentToken()`:

1. If no account is in `AccountDataStore` → return `null`. The network plugin
   maps `null` to `BackupError.NotAuthenticated`.
2. If `AccountDataStore.token()` returns a snapshot whose
   `expiresAtEpochMs > now` → return the cached token. This skips the GMS round
   trip on every Drive HTTP call.
3. Otherwise → `authorize()` silently with the same scope set, cache the new
   token with a 50-minute TTL, and return it.

`DriveBackupStorage.withTokenRefreshOn401 { ... }` wraps every Drive HTTP call.
On a typed `DriveException.AuthRevoked` from the auth plugin, it invalidates
both caches via `DriveTokenInvalidator` and retries the call once. A second
401 propagates as `BackupError.AuthRevoked`. See `DriveTokenInvalidatorTest`.

### Common authentication failures

- **Silent re-auth not appearing.** Symptom: signing in immediately fails with
  401 even though the user just consented. Cause: stale GMS cache from a prior
  `signOut` that used the HTTP revoke endpoint instead of the SDK path. Fix:
  revoke through `AuthorizationClient.revokeAccess` only, and verify by
  checking that the next `signIn` re-prompts.
- **Drive 401 `invalid_token`.** Symptom: every Drive request fails with 401,
  not just the first. Cause: the access token's audience does not match the
  Drive API's enabled OAuth client — usually because the OAuth client was
  created in a different Google Cloud project than the one where Drive API is
  enabled. See [Cloud Console setup](#cloud-console-setup).
- **`AuthorizationResult.account` / `email` returns `null`.** Not a bug. The
  `AuthorizationClient` surface is authorization-only; identity is the
  userinfo endpoint's job. Fall back to the `GoogleSignInAccount` derived from
  the result only when userinfo fetch fails; if that also fails, persist the
  placeholder `"drive_account"` string. See
  `DriveBackupAuth.toAccount(userInfo)`.
- **Partial grant (user unchecked `drive.appdata` on the consent screen).**
  `AuthorizationResult` comes back with a non-null `accessToken` but a
  `grantedScopes` set that omits a required scope. `DriveBackupAuth` checks
  `grantedScopes` against `DriveAuthScopes.REQUIRED` **before** caching the
  token in `AccountDataStore` or calling `setAccount`, so the auth state
  never flips to `Authenticated` in this branch. Recovery: the impl calls
  `AuthorizationClient.clearToken(...)` on the just-issued bad token so the
  next `signIn` re-shows the consent screen instead of silently reusing the
  partial grant, then returns `SignInResult.PartialGrant` (silent path) or
  `BackupResult.Failure(BackupError.MissingRequiredScope)` (resolution path).
  The UI shows an explicit "Drive access wasn't granted — sign in again" 
  snackbar via `BackupErrorUi.MISSING_REQUIRED_SCOPE`. `userinfo.email` and
  `userinfo.profile` are NOT in `REQUIRED` — declining them only forces the
  placeholder display fallback in `toAccount`.

## Backup storage

### Format

The backup payload is the **raw SQLite `.db` file** — not a JSON export, not a
zip, not a custom container. Schema version is embedded in the file itself via
SQLite's built-in `PRAGMA user_version`. Choosing the raw file means:

- Bit-for-bit roundtrip on restore. No serialization layer to drift between app
  versions.
- The same Room migration framework that handles in-process upgrades handles
  cross-version restore (when `backupSchema <= appSchema`; see error taxonomy).
- File size is dominated by indexes and free pages, not the schema; we accept
  this in exchange for the simplicity.

Before each capture, `DatabaseSnapshotProviderImpl.captureSnapshot()` runs
`PRAGMA wal_checkpoint(TRUNCATE)` to flush any in-flight WAL pages into the
main `.db` file. This ensures the copied file is durable on its own; a
caller restoring it on a fresh device will see every committed write up to
the moment of capture.

### Drive layout

Backups live in Drive's **`appDataFolder`** — a per-app, per-user private
folder that the user cannot see in their Drive UI. The `drive.appdata` scope
is classified as non-sensitive by Google (no app verification required to ship
to consumer accounts). The folder survives reinstall, so a user reinstalling
the app, signing back in, and tapping **Restore from backup** gets every
backup they previously made.

File naming: `app_<epochMs>.db` (e.g. `app_1715000000000.db`). The prefix and
suffix come from `BackupConstants.FILE_PREFIX` / `BackupConstants.DB_FILE_SUFFIX`.

Upload uses Drive v3 multipart (`POST /upload/drive/v3/files?uploadType=multipart`)
with the SQLite payload as the binary part and a JSON metadata block carrying
the file name, parent (`appDataFolder`), MIME type, and the manifest as
`appProperties`. The impl lives in `DriveApiImpl.uploadMultipart`.

### Manifest

The manifest is stored as **per-field `appProperties` entries** on the Drive
file metadata — NOT as a sidecar JSON file and NOT as a single JSON-serialized
property. See `ManifestPropertiesMapper`.

Drive's `appProperties` map enforces two independent limits:

- **Per individual key+value pair: 124 bytes UTF-8.**
- **Total across all properties: 30 KB.**

The single-key JSON form (`appProperties.manifest = "{...}"`) clipped the
per-pair limit because the serialized JSON came out to roughly 130 bytes. The
fix was to split into per-field entries; every pair now stays well under the
124-byte ceiling. The keys are:

| Key | Type | Notes |
|---|---|---|
| `app_version` | `String` | `BuildConfig.VERSION_NAME` at capture time. |
| `db_schema_version` | `Int` (as String) | From `DatabaseSnapshotProvider.currentSchemaVersion()`. |
| `created_at_epoch_ms` | `Long` (as String) | `System.currentTimeMillis()` at capture. |
| `db_file_size_bytes` | `Long` (as String) | The SQLite file's actual size. |
| `device_model` | `String` (≤ 100 chars) | `Build.MODEL`, defensively truncated to keep the pair under 124 bytes. |

`ManifestPropertiesMapper.fromAppProperties` parses on restore and collapses
any missing/invalid field to `BackupError.CorruptedBackup(reason = "manifest
field X missing or invalid")` so the UI can surface a typed error rather than
a generic failure.

### Rotation

`BackupConstants.MAX_BACKUPS = 3`. After each successful upload,
`DriveBackupStorage.rotate()` lists current backups, sorts by
`manifest.createdAtEpochMs`, and deletes the oldest entries until at most 3
remain. The decision logic lives in `RotationPolicy.refsToDelete(refs, max)`
— a pure stateless function with its own tests (`RotationPolicyTest`).

Rotation failures are intentionally best-effort: a `delete` that returns an
error logs and moves on. A user is never blocked from creating a backup
because a rotation cleanup failed.

### Restore flow

1. The user taps **Restore from backup** in Settings. The handler dispatches
   `Action.Backup.RequestRestore`.
2. `BackupClickHandler.requestRestore()` calls `interactor.listLatestBackup()`,
   which lists the user's backups and returns the newest as a
   `BackupSummaryDomain`. The UI shows `DialogState.RestoreConfirmation` with
   the formatted timestamp and file size.
3. On confirm, `Action.Backup.ConfirmRestore` → `interactor.restoreLatest()`.
   The interactor:
   - Re-lists backups (sources of truth in case of concurrent uploads) and
     picks the newest.
   - Calls `DatabaseSnapshotProvider.peekSnapshotSchemaVersion(file)` to read
     the backup's `user_version` *before* taking any destructive action.
   - If `backupSchema > appSchema` → `BackupError.SchemaTooNew(...)`. The UI
     surfaces a "Backup needs a newer app version" snackbar.
4. `DatabaseSnapshotProviderImpl.restoreFromSnapshot(source)`:
   - Verifies the SQLite magic bytes (`"SQLite format 3 "`); a wrong
     header collapses to `CorruptedBackup`.
   - Closes the live `AppDatabase`.
   - **Deletes the `<db>-wal` and `<db>-shm` sidecars.** This is load-bearing —
     leftover WAL would replay on the next open and partially override the
     restored data with the now-stale pre-restore writes. Skipping this step
     causes silent data corruption that surfaces minutes after restore.
   - Copies the downloaded file to `<db>.tmp`, then atomically renames it to
     the live database path. On rename failure the temp is deleted; on success
     the live file is now the restored snapshot.
5. The UI flips `RestoreProgressUi` from `Restoring` to `Completed`, holds for
   a brief delay, and emits `Event.AppRestartRequested`. The graph consumes
   that event via `restartApp(context)` (`feature/settings/.../ui/AppRestartHelper.kt`),
   which clears the task stack and calls `Runtime.getRuntime().exit(0)`.
   The process termination is the load-bearing step — only a cold start
   rebuilds the Room graph with the restored file.

## Scheduling

### WorkManager setup

WorkManager is initialized **on-demand** via `Configuration.Provider`
implemented on `BaseApplication`. The standard `WorkManagerInitializer`
manifest provider is suppressed via the `androidx.startup` tombstone in both
`app/dev/AndroidManifest.xml` and `app/store/AndroidManifest.xml`. The
`HiltWorkerFactory` is `@Inject`-ed into `BaseApplication` and supplied to
the WorkManager configuration so `@HiltWorker`-annotated workers can use
`@AssistedInject` constructor injection.

Reviewers occasionally flag the manifest tombstone as a missing initializer;
it is **intentional**. Restoring the default `WorkManagerInitializer` would
create a WorkManager instance without Hilt's worker factory, which would in
turn break `@AssistedInject` construction of `BackupWorker` at enqueue time.
The flavor manifests carry a comment in-place explaining the choice; see
also Google's docs on Hilt + WorkManager integration.

Two unique work names live in parallel and are intentionally independent:

| Name | Type | Trigger |
|---|---|---|
| `auto_backup` | `PeriodicWorkRequest` | `BackupScheduler.schedulePeriodic(prefs)` on first sign-in + on every preferences change. Replaces via `ExistingPeriodicWorkPolicy.UPDATE`. |
| `manual_backup` | `OneTimeWorkRequest` | `BackupScheduler.enqueueOneTime()` on the **Backup now** tap and on the first-sign-in bootstrap immediate-backup. Dedupes rapid double-taps via `ExistingWorkPolicy.KEEP`. |

Cancelling periodic does NOT cancel an in-flight one-time backup, and vice
versa. The contract lives in `core/data/backup/api/scheduling/AutoBackupController.kt`;
the impl is `BackupScheduler` in `core/data/backup/worker/scheduler/`.

### Constraints

Constraints apply to the periodic work only. The one-time work runs with no
constraints — the user explicitly tapped, so we honor that immediately.

- **`NetworkType`:** `UNMETERED` when `BackupPreferences.allowOnMobileData ==
  false`, `CONNECTED` otherwise.
- **`setRequiresBatteryNotLow(true)`** unconditionally — the snapshot capture
  and HTTP upload are small (~170 KB for a typical user) but not so trivial
  that we want to run them on a phone at 5 % battery.
- No `requiresCharging`, no `requiresDeviceIdle` — overkill given the
  payload size and complete in well under 30 s on a typical network.

The retry backoff is `BackoffPolicy.EXPONENTIAL` starting at 1 hour, which
WorkManager handles for any `Result.retry()` return from the worker.

### Preferences

Persisted in DataStore Preferences via
`BackupPreferencesRepositoryImpl`. Schema:

| Key | Type | Default |
|---|---|---|
| `schedule` | `String` (`BackupSchedule.name`) | `Weekly` |
| `allow_on_mobile_data` | `Boolean` | `false` |
| `last_attempt_at` | `Long` (epoch ms) | `0` |
| `last_success_at` | `Long` (epoch ms) | `0` |
| `last_error` | `String` (`BackupErrorCode.name`) | absent |
| `auto_backup_bootstrapped` | `Boolean` | `false` |

`BackupErrorCode` is a flat enum mirror of `BackupError` — variants that carry
payloads (`CorruptedBackup.reason`, `SchemaTooNew.versions`, `Io.cause`,
`Unknown.cause`) collapse to the discriminator only, which is all the UI
surface needs (banner / notification / settings badge).

### First-sign-in bootstrap

On the auth flow's transition from `NotAuthenticated` to `Authenticated`,
`BackupClickHandler.bootstrapOrRehydrate()` reads
`BackupPreferences.autoBackupBootstrapped`:

- **First run** (`bootstrapped == false`):
  1. Write defaults: `schedule = Weekly`, `allowOnMobileData = false`.
  2. Flip `autoBackupBootstrapped = true`.
  3. `schedulePeriodic(BackupPreferences.DEFAULT)`.
  4. `enqueueOneTime()` — the immediate first backup.
  5. Emit `Event.ShowAutoBackupEnabledSnackbarRequested`. The graph renders a
     snackbar with the action label **Change**, which dispatches
     `Action.Backup.OpenFrequencyPicker` on tap.
- **Subsequent runs** (`bootstrapped == true`, schedule ≠ `ManualOnly`):
  Re-call `schedulePeriodic(currentPrefs)` so the work is re-enqueued on the
  current device after a fresh install / app data wipe.
- **In all cases**: if `lastError == AuthRevoked`, clear it now — the user
  has re-signed-in, so the paused state is over.

The `autoBackupBootstrapped` flag is set **before** the WorkManager calls
land. If `schedulePeriodic` or `enqueueOneTime` fails, the next launch will
skip the bootstrap and the user will never see the snackbar again. The
trade-off is intentional — retrying forever would re-fire the snackbar on
every re-sign-in. See [tech-debt.md](../tech-debt.md) for the v1.1 follow-up.

### Auth-revoked handling

`BackupWorker.handleFailure(BackupError.AuthRevoked)`:

1. `preferences.setLastError(AuthRevoked)`.
2. `autoBackupController.cancelPeriodic()` — terminal, no retry.
3. `notificationHelper.showAuthPaused()` — see [State / UI](#state-ui-notifications).
4. Return `Result.failure()`.

`feature/settings` derives `BackupPreferencesUi.isAuthPaused` from
`preferences.lastError == AuthRevoked` and renders the
`AuthPausedBanner` inside `AuthenticatedBlock`. The user re-signs-in via the
banner's **Sign in** button, which dispatches `Action.Backup.SignIn`. On
success, `bootstrapOrRehydrate()` clears the error code, the banner
disappears, and `BackupNotificationHelper.cancelAuthPaused()` runs on the
worker's next success path.

## State / UI

### Dialog state

Backup uses the project's sealed `DialogState` pattern. See
[.claude/skills/mvi-dialog-state.md](../../.claude/skills/mvi-dialog-state.md)
for the canonical convention. The variants are in
`feature/settings/.../mvi/store/DialogState.kt`:

| Variant | Trigger | Composable |
|---|---|---|
| `Hidden` | default, dismiss any other variant | (nothing rendered) |
| `RestoreConfirmation(createdAtFormatted, sizeFormatted)` | `RequestRestore` success | `RestoreConfirmationDialog` |
| `SignOutConfirmation` | `RequestSignOut` | `SignOutConfirmationDialog` |
| `FrequencyPicker(selectedSchedule, allowOnMobileData)` | `OpenFrequencyPicker` | `FrequencyPickerBottomSheet` |

The compose-state-discipline rule that says "dialogs and bottom sheets are
State, not Events" applies here — see
[.claude/skills/compose-state-discipline.md](../../.claude/skills/compose-state-discipline.md)
Rule 4.

### Settings state fields

`SettingsStore.State` in `feature/settings/.../mvi/store/SettingsStore.kt` carries
the backup-related fields:

| Field | Purpose |
|---|---|
| `backupAuth: BackupAuthUi` | `NotAuthenticated` vs `Authenticated(email, displayName)`. Drives which sub-section of `BackupSection` renders. |
| `backupOperation: BackupOperationUi` | `Idle`, `SigningIn`, `SigningOut`, `CreatingBackup`, `FetchingBackups`, `Restoring`. Drives the per-button spinner and `enabled` flag. |
| `dialogState: DialogState` | See above. |
| `backupInfo: BackupInfoUi?` | Pre-formatted "Last backup: 2h ago" + "3 backups stored" strings. Null until the first `listBackups` succeeds. |
| `backupPreferences: BackupPreferencesUi?` | Schedule + allow-on-mobile-data + `nextBackupText` + `isAuthPaused`. Null until `ObservePreferences` lands its first emission. |
| `restoreProgress: RestoreProgressUi` | `Idle`, `Restoring`, `Completed`. Drives the `RestoreProgressOverlay`. |

### Strings and localization

All user-facing text lives in resources, EN + RU. Backup strings are grouped
by purpose:

- `feature_settings_backup_*` — Settings UI: button labels, dialog titles,
  banner copy. In `feature/settings/src/main/res/values/strings.xml` and the
  `values-ru/` counterpart.
- `feature_settings_backup_error_*` — typed error messages keyed by
  `BackupErrorUi` enum. The graph maps each error code to a localized string
  in `backupErrorMessages()` in `SettingsGraph.kt`.
- `core_backup_worker_notification_*` — notification channel + paused
  title + body strings. In `core/data/backup/worker/src/main/res/values/`
  (and `values-ru/`) so the worker module owns its own copy.

Relative timestamps ("Last backup: 2 hours ago", "Next backup: in 5 days") go
through `DateUtils.getRelativeTimeSpanString` — locale-aware and handles
plurals without manual branching.

### Notifications

The auth-paused notification is owned by `BackupNotificationHelper`:

- Channel ID `backup_paused`, importance LOW (no sound, no vibration; the
  shade is enough).
- Title + body from
  `R.string.core_backup_worker_notification_paused_{title,body}`.
- Tap action: `getLaunchIntentForPackage(packageName)` — opens the app at the
  main entry. There is no deep-link to Settings yet; the persistent banner
  inside Settings is the user's actionable surface. The Settings deep-link is
  a v1.1 follow-up; see [tech-debt.md](../tech-debt.md) under "Backup
  integrations".
- The helper guards `notify(...)` behind
  `NotificationManagerCompat.areNotificationsEnabled()` so a user that denied
  `POST_NOTIFICATIONS` on Android 13+ silently falls back to the in-app
  banner only.

## Cloud Console setup

The Drive backup flow requires a Google Cloud project with:

- **OAuth 2.0 Client ID of type Android.** Configure the package name and the
  SHA-1 signing certificate fingerprint for *every* signing key that will run
  the app:
  - Debug keystore SHA-1 (per developer machine).
  - Release keystore SHA-1.
  - Play App Signing SHA-1 (separate from the upload key).
  Each variant uses the same package name (`io.github.stslex.workeeper.dev`
  for dev, `io.github.stslex.workeeper` for store), so multiple Android OAuth
  clients in the same project — one per SHA-1 — is the supported shape.
- **Drive API enabled in the SAME project as the OAuth client.** A common
  failure mode is the OAuth client in project A and Drive API enabled in
  project B — silent 401 `invalid_token` on every Drive request, no useful
  error surface. Verify under "APIs & Services → Enabled APIs".
- **`drive.appdata` listed in Data Access**, classified as **Non-sensitive**.
  This is the default Google assigns; no app verification is required to ship
  to consumer accounts.
- **OAuth Consent Screen** with the app name, support email, and developer
  contact. For test rollout the Publishing status can stay "Testing" with
  explicit test accounts; for production it must be Published.

The corresponding scope strings the app uses are in `DriveAuthScopes`.

### Common pitfalls

- **OAuth client and Drive API in different projects.** No client-side
  indicator; manifests itself only on the wire as a 401 from Drive. Resolve
  by enabling Drive API in the same project that owns the OAuth client.
- **Missing SHA-1.** Symptom: silent re-auth never completes — the user is
  prompted on every signIn. Resolve by adding the missing fingerprint to the
  same Android OAuth client (or a sibling client in the same project).
- **App not published, user not on test list.** Symptom: the consent screen
  shows "App not verified" and explicitly blocks non-test accounts. Resolve
  by adding the user to the consent screen's Test Users list, or publishing
  the app.

## Error taxonomy

Every fallible call in `BackupAuth` / `BackupStorage` returns
`BackupResult<T>` with the failure side carrying a typed `BackupError`. The
worker maps each variant to a `Result.{success,retry,failure}` decision; the
UI surfaces each variant via `BackupErrorUi` + a localized string resource.

| Variant | Raised when | Worker | UI |
|---|---|---|---|
| `NotAuthenticated` | `DriveAuthTokenProvider.currentToken()` returns `null` | `Result.failure()` (terminal — periodic stays cancelled) | snackbar via `BackupErrorUi.NOT_AUTHENTICATED` |
| `NetworkUnavailable` | `IOException` from Ktor, no connectivity | `Result.retry()` (exponential backoff) | snackbar via `BackupErrorUi.NETWORK_UNAVAILABLE` |
| `AuthRevoked` | Drive 401 after the token-refresh retry; or Drive 403 with `Forbidden` | `Result.failure()` + `cancelPeriodic` + `showAuthPaused` | banner + persistent notification |
| `MissingRequiredScope` | Consent screen returned a token but `grantedScopes` excludes `drive.appdata`; detected at the data boundary before any token is cached | n/a (sign-in path only — periodic never reaches this) | snackbar via `BackupErrorUi.MISSING_REQUIRED_SCOPE` with explicit retry copy |
| `StorageQuotaExceeded` | Drive 403 with `quotaExceeded` / `userRateLimitExceeded` reason in the body | `Result.failure()` (non-retryable without user action) | snackbar via `BackupErrorUi.STORAGE_QUOTA_EXCEEDED` |
| `CorruptedBackup(reason)` | SQLite magic header mismatch on restore; manifest parse failure | `Result.retry()` (other paths) | snackbar via `BackupErrorUi.CORRUPTED_BACKUP` |
| `BackupTooNew(backupSchema, appSchema)` | Pre-restore in [`BackupInteractorImpl.restoreLatest`](../../feature/settings/src/main/kotlin/io/github/stslex/workeeper/feature/settings/domain/BackupInteractorImpl.kt): `backup.schemaVersion > currentCodeSchemaVersion`. Defence-in-depth check on the downloaded file in [`DatabaseSnapshotProviderImpl.restoreFromSnapshot`](../../core/data/database/src/main/kotlin/io/github/stslex/workeeper/core/data/database/snapshot/DatabaseSnapshotProviderImpl.kt). Renamed from `SchemaTooNew` in the recovery PR for consistency with the user-facing framing. | n/a (restore path) | snackbar via `BackupErrorUi.BACKUP_TOO_NEW` ("Update the app to restore this backup"). |
| `MissingMigrationPath(backupSchema, appSchema)` | Pre-restore in [`BackupInteractorImpl.restoreLatest`](../../feature/settings/src/main/kotlin/io/github/stslex/workeeper/feature/settings/domain/BackupInteractorImpl.kt): backup schema strictly older than the current code and [`hasMigrationPath`](../../core/data/database/src/main/kotlin/io/github/stslex/workeeper/core/data/database/migration/MigrationGraph.kt) returns false. Distinct from `BackupTooNew` (backup newer than code) and `CorruptedBackup` (manifest unreadable). | n/a (restore path) | snackbar via `BackupErrorUi.MISSING_MIGRATION_PATH` ("Backup is from an older version that this app build cannot migrate"). |
| `Io(cause)` | Snapshot capture `IOException`; Drive 5xx | `Result.retry()` | snackbar via `BackupErrorUi.IO_ERROR` |
| `Unknown(cause)` | Any uncategorized `Throwable` | `Result.retry()` | snackbar via `BackupErrorUi.UNKNOWN` |

The mapping is implemented in three places that must stay in sync:

- `DriveErrorMapper` (`core/data/backup/google-drive/.../error/`) — `Throwable`
  → `BackupError`.
- `BackupErrorCode.from(error)` (`core/data/backup/api/.../scheduling/`) —
  `BackupError` → flat enum for DataStore persistence.
- `BackupUiMapper.toUi()` (`feature/settings/.../mvi/mapper/`) — `BackupError`
  → `BackupErrorUi` → string resource.

`BackupUiMapperTest` covers the exhaustive `BackupError` → `BackupErrorUi`
branch.

## Out of scope and decisions

Decisions locked in for v1, with rationale.

- **No client-side encryption at rest.** Drive's at-rest encryption + HTTPS in
  transit deemed sufficient for v1. A client-side encryption layer (Tink
  envelope encryption, key in Android Keystore) is planned for a later phase
  and is tracked in [tech-debt.md](../tech-debt.md).
- **No refresh tokens.** Short-lived access tokens only; silent
  re-`authorize()` via the GMS SDK handles renewal automatically. Refresh
  tokens are an OAuth2 web-flow concept and require a backend to exchange
  them — Workeeper has no backend.
- **No backup history beyond N=3.** A user-facing list of all backups +
  picker to restore an older version is deferred. Rotation runs after every
  successful upload, so the user always has the most recent three.
- **Latest-only restore.** v1 surfaces only the newest backup in the restore
  confirmation dialog; tapping restore picks the absolute newest at the
  moment of confirm (a second list call inside `restoreLatest()` so a
  concurrent upload on another device wins). Picker UI is the v1.1
  follow-up — tracked in [tech-debt.md](../tech-debt.md) → "Backup
  integrations". A complementary safety net — pre-restore migration-path
  checks, automatic rollback on Room migration failure, and user-initiated
  undo of the last successful restore — is the scope of
  [backup-recovery.md](backup-recovery.md) and ships independently of the
  picker work.
- **Drive only.** No self-hosted backend, no Dropbox, no iCloud (Workeeper
  is Android-only). The api/impl split inside `core/data/backup/` keeps the
  door open for additional providers in their own modules without touching
  the feature module.
- **No foreground service.** The worker runs as a normal CoroutineWorker.
  Payload size (~170 KB for a heavy user) and runtime (<30 s on slow
  networks) make foreground-service hoisting unnecessary, and the
  notification cost would outweigh the user benefit.
- **No retry on `AuthRevoked`.** Terminal until the user re-signs-in.
  Retrying would burn battery and never succeed; the persistent notification
  + banner exist to actually fix the cause.
- **Restore deep-link on auth-paused notification.** The notification taps
  to the main app entry, not to Settings. The persistent banner inside
  Settings is the source of truth for action; the notification's job is
  to alert. Deep-linking is a v1.1 follow-up.
- **No backup option to disable notifications.** Auth-paused is load-bearing
  user-facing information — the user MUST know auto-backup stopped.
  Dismissible, but always shown on `AuthRevoked`.

## Troubleshooting

Concrete diagnostic procedures for known failure modes.

### Drive returns 401 `invalid_token`

1. Confirm the `Authorization: Bearer <token>` header is attached by checking
   `KtorLogger` output (the dev variant logs HTTP requests). If the header
   is missing, `DriveAuthTokenProvider.currentToken()` returned `null` — see
   the next section.
2. While the token is fresh, run
   `curl "https://oauth2.googleapis.com/tokeninfo?access_token=<token>"`.
   Verify `scope` includes `https://www.googleapis.com/auth/drive.appdata`
   and `aud` matches the expected OAuth client ID. If `aud` is unexpected,
   the OAuth client is wrong — see [Cloud Console setup](#cloud-console-setup).
3. Check Cloud Console: the OAuth client and the Drive API enablement live in
   the **same** project.
4. Stale GMS cache: revoke the grant via
   `myaccount.google.com/permissions`, then re-sign-in from the app.
   `DriveBackupAuth.signOut` should clear this automatically, but a manual
   revoke is the last-resort confirmation.

### `DriveAuthTokenProvider.currentToken()` returns null

Two paths lead here:

- `AccountDataStore.observeAccount().first()` returned `null` — no account
  persisted. Expected when the user has not signed in.
- The cache is empty/expired and `authorize()` returned no `accessToken` —
  either `hasResolution()` is true (the user has not consented yet on this
  device) or the grant was revoked server-side. The
  `KtorLogger.TAG`-tagged log line in `refreshTokenFromGms()` surfaces
  which case it is.

### Drive returns 403 `propertyLengthLimitExceeded`

The 124-byte-per-pair limit on `appProperties` is being violated. Check
`ManifestPropertiesMapper` — every field is wrapped to keep pairs under the
limit, and `device_model` is truncated to 100 chars. If a new manifest field
is added, verify its pair length before merging.

### Silent re-auth shows the consent screen unexpectedly

Expected behavior IF the user has not yet granted the scope on this device.
A bug only when it happens after a known prior grant — symptoms are
"my dev built worked and now the user always sees consent". Most common
cause: the SHA-1 of the signing key changed (e.g. local debug keystore
regenerated, or Play App Signing's SHA-1 vs the upload keystore's SHA-1
diverged). Add the missing SHA-1 to the OAuth client in Cloud Console.

### Auto-backup not firing on schedule

1. `adb shell dumpsys jobscheduler | grep io.github.stslex.workeeper` — look
   for the periodic job and confirm its state (`READY`, `WAITING`,
   `PENDING`).
2. Confirm the network constraint is satisfied: if `allowOnMobileData ==
   false`, the device must be on Wi-Fi. If it is and the job still does not
   run, confirm `BatteryNotLow` is satisfied (battery > 15 %).
3. Verify `BackupPreferences.schedule != ManualOnly`. The handler's
   `saveFrequency(ManualOnly, _)` calls `cancelPeriodic`; switching back
   to Daily / Weekly re-schedules.
4. If `BackupPreferences.lastError == AuthRevoked`, the worker has
   deliberately cancelled the periodic. Re-sign-in via the banner.

### Restore appears to succeed but old data reappears

The pre-restore WAL was not cleared. The fix lives in
`DatabaseSnapshotProviderImpl.restoreFromSnapshot` (delete `<db>-wal` and
`<db>-shm` before the atomic file replace). If this regresses, restored
data will be silently overlaid by the stale pre-restore writes on next
open. Tests in `DatabaseSnapshotProviderImplTest` cover this.

## Testing

| Class | Module | Covers |
|---|---|---|
| `DriveBackupAuthTest` | `core/data/backup/google-drive` | signIn happy / resolution / scope set / token caching / revoke / signOut local-clear semantics |
| `DriveAuthTokenProviderTest` | `core/data/backup/google-drive` | cache hit / miss / silent refresh / null-on-no-account |
| `DriveTokenInvalidatorTest` | `core/data/backup/google-drive` | both DataStore and GMS caches cleared |
| `DriveBackupStorageTest` | `core/data/backup/google-drive` | list / upload / download / rotation / token-refresh-on-401 |
| `RotationPolicyTest` | `core/data/backup/google-drive` | refsToDelete pure logic across boundary cases |
| `ManifestPropertiesMapperTest` | `core/data/backup/google-drive` | per-field round-trip, missing-field collapse to `CorruptedBackup` |
| `DriveErrorMapperTest` | `core/data/backup/google-drive` | each `Throwable` shape → typed `BackupError` |
| `UserInfoFetcherImplTest` | `core/data/backup/google-drive` | userinfo HTTP success / failure mapping |
| `BackupPreferencesRepositoryImplTest` | `core/data/backup/scheduling` | defaults, persistence, observe, error code round-trip |
| `BackupSchedulerTest` | `core/data/backup/worker` | constraints by schedule + allowOnMobileData, UPDATE policy, KEEP policy, independence of work names |
| `BackupWorkerTest` | `core/data/backup/worker` | success / AuthRevoked / Network / Quota / Io paths with the appropriate Result + side effects |
| `BackupClickHandlerTest` | `feature/settings` | every `Action.Backup.*` branch, dialog-state transitions, first-sign-in bootstrap, re-sign-in rehydrate, AuthRevoked clearing, ConfirmSignOut cancels periodic |
| `BackupUiMapperTest` | `feature/settings` | exhaustive `BackupError` → `BackupErrorUi` mapping |
| `BackupDateMapperTest` | `feature/settings` | "Last backup: …" / count plurals formatting |
| `DatabaseSnapshotProviderImplTest` | `core/data/database` | capture WAL-checkpoint, restore atomic replace + sidecar cleanup, schema peek, magic-byte rejection |

Run all backup-related tests:

```bash
./gradlew \
  :core:data:backup:google-drive:testDebugUnitTest \
  :core:data:backup:scheduling:testDebugUnitTest \
  :core:data:backup:worker:testDebugUnitTest \
  :feature:settings:testDebugUnitTest \
  :core:data:database:testDebugUnitTest
```

## Related

- [.claude/skills/mvi-dialog-state.md](../../.claude/skills/mvi-dialog-state.md)
  — dialog state shape convention. The `FrequencyPicker` variant added here is
  the canonical "second-dialog landing on a screen with an existing
  `DialogState`" case.
- [.claude/skills/compose-state-discipline.md](../../.claude/skills/compose-state-discipline.md)
  — Rule 4 (dialogs/sheets are State, not Events) and Rule 1 (no computation
  inside `updateState`).
- [documentation/architecture.md](../architecture.md) — module map, MVI
  contract, DI scopes.
- [documentation/lint-rules.md → HiltScopeRule](../lint-rules.md#hiltscoperule)
  — `BackupPreferencesRepositoryImpl` / `BackupSchedule[r]` naming
  decisions.
- [documentation/tech-debt.md](../tech-debt.md) — active backup-related debt
  (notification deep-link, `nextBackupText` fallback, older-backup picker,
  encryption at rest).
