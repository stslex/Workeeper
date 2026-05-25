# Feature spec — Backup Recovery

**Status:** Planned. Depends on the [app-dialogs.md](app-dialogs.md)
infrastructure landing first (or in the same PR series). Builds on top of
the shipped Drive backup feature documented in [backup.md](backup.md); does
not change the upload / list / scheduling surface, only the restore path
and the schema-migration safety net.

Backup Recovery adds three independent recovery flows on top of the v1
Drive backup feature:

1. **Restore-time migration failure recovery (Scenario 1).** If Room cannot
   migrate a freshly-restored database, automatically roll back to the
   user's pre-restore data and surface a typed `RestoreFailure` dialog.
2. **User-initiated undo of last successful restore (Scenario 3).** Preserve
   the pre-restore database for one undo opportunity after every successful
   restore. Settings exposes a "Revert last restore" entry while the
   preserved file exists.
3. **Startup migration failure recovery (Scenario 2).** Separate failure
   mode from Scenario 1 — Room cannot migrate the user's existing database
   on launch (developer-error class). A dedicated DB-free `RecoveryActivity`
   exposes the user's data for export and self-service issue reporting.

Together these three close the "fallback-to-destructive-migration silently
wipes the user's data" failure mode that exists in the current build.

## Status

**Shipped end-to-end.** The pre-restore compatibility checks (PR-B), the
`feature/app-dialogs` infrastructure (PR-C), the restore-time recovery +
user-initiated undo flows (PR-D), and the startup migration failure
recovery (PR-E) are all live. Every migration failure now routes to one of
three deterministic recovery paths — silent data wipe is no longer reachable
in any code path.

- **Scenario 1** (restore-time) — pre-flight in
  [`RestoreRecoveryCoordinator`](../../feature/recovery/src/main/kotlin/io/github/stslex/workeeper/feature/recovery/domain/RestoreRecoveryCoordinator.kt),
  triggered from
  [`BaseApplication.onCreate`](../../app/app/src/main/java/io/github/stslex/workeeper/BaseApplication.kt).
  Automatic rollback to `cache/pre_restore_backup.db` on Room migration
  failure; `AppDialog.RestoreSuccess` / `RestoreFailure` published via
  DataStore so it surfaces after restart on any destination.
- **Scenario 2** (startup) — pre-flight in `StartupMigrationCoordinator`
  (in `feature/recovery`), triggered from
  [`BaseApplication.onCreate`](../../app/app/src/main/java/io/github/stslex/workeeper/BaseApplication.kt)
  when `restore_in_progress` is false. Routes to the Room-free
  `RecoveryActivity` (also in `feature/recovery`) via
  `NavCommand.OpenRecovery` on `APP_DOWNGRADE` / `NO_MIGRATION_PATH` /
  `CANNOT_PEEK_LIVE_DB`.
- **Scenario 3** (user-initiated undo) — the Settings "Revert last restore"
  row in
  [`BackupSection`](../../feature/settings/src/main/kotlin/io/github/stslex/workeeper/feature/settings/ui/components/BackupSection.kt)
  publishes `AppDialog.UndoRestoreConfirmation`. `feature/recovery`'s
  `RestoreDialogChoiceObserver` (`@Singleton`, observes
  `AppDialogObserver.observeUserActions()`) reacts to the user's
  ConfirmUndo choice by calling `RestoreRecoveryCoordinator.performUndoRestore`
  (file swap) and then `RestoreRecoveryCoordinator.restartApp()` directly
  (non-Composable call site — see "Restart contract / OpenRecovery contract").
- The four `AppDialog` variants and the cross-feature publisher live in
  [`feature/app-dialogs`](../../feature/app-dialogs/) — see
  [app-dialogs.md](app-dialogs.md).
- `fallbackToDestructiveMigration*` is intentionally absent from the Room
  builder (verified by PR-B). Every migration failure routes to Scenario 1
  or Scenario 2; silent data wipe is gone.

## Scope

1. Pre-restore schema-compatibility checks (reject before file replace if
   the backup cannot be migrated under the current code's MIGRATIONS).
2. Scenario 1 — restore-time migration failure rollback.
3. Scenario 3 — user-initiated undo of the last successful restore via the
   preserved `pre_restore_backup.db`.
4. Scenario 2 — startup migration failure recovery via a dedicated
   `RecoveryActivity` (Room-free, exposes export / report).
5. Removal of `fallbackToDestructiveMigration*` from the Room builder.
6. Introspectable `MIGRATIONS` list with a `hasMigrationPath(from, to)`
   helper, plus a CI-enforced test that asserts every schema bump has a
   registered migration.

## Out of scope

- **Backup history beyond the single preserved slot.** Only one
  `pre_restore_backup.db` exists at a time. Each new Restore overwrites
  the previous preserved file, consuming the undo opportunity. A multi-slot
  history is a v1.x+ follow-up if user demand surfaces.
- **Manual user-initiated DB export under normal conditions.** Scenario 2's
  RecoveryActivity has Export raw data because the user has no other way
  out; a feature for "export the live DB whenever" is deferred.
- **Server-side backup retention beyond N=3.** Rotation policy unchanged
  from current — see [backup.md → Rotation](backup.md#rotation).
- **Encryption at rest.** Still tracked in
  [tech-debt.md → Backup integrations](../tech-debt.md#backup-integrations).
  Recovery work neither blocks nor enables encryption — both can ship
  independently.
- **Cross-device restore conflicts** (user has restored on device A, then
  triggers undo on device B). Undo is local-only: it consumes the
  on-device `pre_restore_backup.db`. A device with no preserved file shows
  no "Revert last restore" row, regardless of cloud state.
- **A "Restore from older backup" picker.** Latest-only restore unchanged
  from v1 (see [backup.md → Out of scope](backup.md#out-of-scope-and-decisions)).
  The undo flow is a single-step "reverse the most recent restore", not a
  picker over arbitrary backups.

## Two scenarios — distinct flows

The two restore-related failure modes look superficially similar (Room
fails to open the database on launch) but have very different triggers,
user expectations, and recovery shapes. Conflating them into one path is
the failure mode this spec exists to avoid.

| Discriminator | Scenario 1 (restore-time) | Scenario 2 (startup-time) |
|---|---|---|
| What user did just before | Tapped Restore in Settings | Updated the app (Play, sideload) |
| Was a restore in progress? | Yes (`restore_in_progress = true`) | No (`restore_in_progress = false`) |
| Why migration failed | Backup schema needs a migration that this code lacks (rare — pre-restore check should have caught it) OR a previously-untriggered bug in an existing migration | Developer shipped a code-side schema bump without a registered migration |
| Recovery shape | Automatic rollback to pre-restore data, restart, show `RestoreFailure` dialog | RecoveryActivity (DB-free) with Update / Export / Report buttons |
| User-visible side effect | Two restarts in quick succession (acceptable edge case), data intact afterwards | App does not reach main UI until update or remediation |

The `restore_in_progress` DataStore flag is the discriminator. It is set
**only** by the Restore happy path and cleared in both success and rollback
branches.

### Scenario 1 — restore-time migration failure

Trigger: user explicitly tapped Restore, backup file was atomically swapped
in, app restarted, Room migration crashes during subsequent open.

**Implementation status:** shipped. Live in
[`BackupInteractorImpl.restoreLatest`](../../feature/settings/src/main/kotlin/io/github/stslex/workeeper/feature/settings/domain/BackupInteractorImpl.kt)
(pre-restore save + flag) and
`RestoreRecoveryCoordinator.handlePostRestoreLaunch` in `feature/recovery`
(post-restart pre-flight + rollback + publish; restart is dispatched by the
caller via `NavCommand.RestartApp`, not by the coordinator).
[`BaseApplication.onCreate`](../../app/app/src/main/java/io/github/stslex/workeeper/BaseApplication.kt)
invokes the coordinator via a Hilt `EntryPoint` exposed by the
`feature/recovery` graph.

Flow:

1. **Pre-restore** (before file replace).
   [`BackupInteractorImpl.restoreLatest`](../../feature/settings/src/main/kotlin/io/github/stslex/workeeper/feature/settings/domain/BackupInteractorImpl.kt):
   1. Save current `<db>` → `cache/pre_restore_backup.db` via
      [`DatabaseSnapshotProvider.preserveCurrentDb`](../../core/data/database/src/main/kotlin/io/github/stslex/workeeper/core/data/database/snapshot/DatabaseSnapshotProvider.kt).
   2. Write DataStore flag `restore_in_progress = true` plus the manifest
      context (`backupSchemaVersion`, `backupCreatedAtEpochMs`,
      `backupAppVersion`, `startedAtEpochMs`) via
      [`RestoreStateRepository.markRestoreInProgress`](../../core/data/backup/api/src/main/kotlin/io/github/stslex/workeeper/core/data/backup/api/restore/RestoreStateRepository.kt).
   3. Existing `restoreFromSnapshot` (delete WAL/SHM sidecars, atomic
      rename — see
      [backup.md → Restore flow](backup.md#restore-flow)).
2. **App restart** via the existing
   `navigator.restartApp()` command (see
   [architecture.md → Destructive app-restart through the bus](../architecture.md#destructive-app-restart-through-the-bus)).
3. **Application.onCreate** reads `restore_in_progress` flag:
   - `false` (normal launch) → fall through to Scenario 2 pre-flight (next
     section).
   - `true` → enter Scenario 1 pre-flight (this section).
4. **Pre-flight Room open attempt.** Open the database read-only behind a
   `runCatching {}`:
   - **Success.** Clear `restore_in_progress` flag, **keep**
     `pre_restore_backup.db` for Scenario 3 (see below),
     `AppDialogPublisher.publish(RestoreSuccess(restoredAtEpochMs,
     previousVersionAvailable = true))`.
   - **Failure.** Atomic rollback: `<db>` ← `pre_restore_backup.db`. Delete
     the preserved file (consumed). Clear `restore_in_progress` flag.
     `AppDialogPublisher.publish(RestoreFailure(reason =
     BackupErrorCode.<derived>))`. Send Crashlytics non-fatal (see
     [Crashlytics non-fatals](#crashlytics-non-fatals)). Trigger a second
     restart via the single Navigator path —
     `BaseApplication.onCreate` reads the coordinator's
     `PreflightOutcome.RestoreRolledBack` return and dispatches
     `NavCommand.RestartApp` through the navigator (the same path Settings
     uses for `Action.Navigation.RestartApp`). The coordinator does NOT
     restart inline.
5. **Next startup** (after rollback). Normal launch — `restore_in_progress`
   is false, pre-flight passes against the now-rolled-back database. The
   `AppDialogHost` observes its Store's `State.current = RestoreFailure(...)`
   (projected from the repository's persisted flag) and renders the dialog
   on whatever destination the user lands on.

UX consequence on failure: user sees **two** restarts in quick succession.
This is acceptable because (a) it is rare — the pre-restore compatibility
check should reject most incompatible backups before the file replace, (b)
the alternative (silent data wipe) is far worse, and (c) the dialog after
the second restart explains exactly what happened and confirms data is
intact.

### Scenario 2 — startup migration failure (developer error)

Trigger: app updated normally (Play, sideload), no restore in progress,
Room cannot migrate the user's existing database because the code-side
schema bumped without a registered migration. This is a developer error;
the user did nothing wrong.

**Implementation status:** shipped. Pre-flight in
`StartupMigrationCoordinator` (in `feature/recovery`) called from
[`BaseApplication.onCreate`](../../app/app/src/main/java/io/github/stslex/workeeper/BaseApplication.kt)
after the Scenario 1 check returns no-op. Routes to `RecoveryActivity`
(also in `feature/recovery`) via a `MainActivity.onCreate` check on
`StartupMigrationCoordinator.lastDecision`. MainActivity launches
RecoveryActivity directly via Intent because the call site fires before
`setContent { App() }` — `NavCommand.OpenRecovery` is reserved for
in-composition callers per `Navigator.openRecovery()` KDoc.

**Implementation deviates from the literal spec** in two ways, both
documented in the coordinator KDoc:

1. **The pre-flight does not trigger Room migration.** It peeks the live
   `PRAGMA user_version` via the Room-free `SQLiteDatabase.openDatabase`
   and consults the registered `hasMigrationPath`. A migration is trusted
   if it is registered; a registered-but-buggy migration still surfaces
   as a Room exception on first DAO call (narrower failure mode than
   missing-migration, which PR-B's `MigrationsRegistryTest` catches
   pre-merge).
2. **The pre-Room snapshot is written lazily, only on the
   `RouteToRecovery` branch.** The live db is pristine at that point
   (Room never opened it on this launch), so the snapshot captures the
   same bytes the spec asks for, without paying the file-copy cost on
   every normal launch.

Flow:

1. **Application.onCreate** reads `restore_in_progress` flag → `false`
   (the Scenario 1 path runs first and short-circuits here when
   it returns `NoOp`).
2. **Schema peek.** `peekSnapshotSchemaVersion` reads the live db's
   `user_version` via `SQLiteDatabase.openDatabase` — no Room init, no
   migration trigger.
3. **Decide.** Four branches, exhaustive over
   `StartupMigrationFailureReason`:
   - `db == code` → `Proceed`. Delete any stale `pre_migration_backup.db`
     from a previous launch.
   - `db > code` → `RouteToRecovery(APP_DOWNGRADE)`. Preserve snapshot,
     record Crashlytics non-fatal.
   - `db < code` + `hasMigrationPath = true` → `Proceed`. Room handles the
     migration lazily on first DAO access.
   - `db < code` + `hasMigrationPath = false` → `RouteToRecovery(NO_MIGRATION_PATH)`.
     Preserve snapshot, record Crashlytics non-fatal.
   - Peek throws → `RouteToRecovery(CANNOT_PEEK_LIVE_DB)`. Preserve
     snapshot best-effort, record Crashlytics non-fatal.
4. **MainActivity.onCreate** reads `coordinator.lastDecision`. On
   `RouteToRecovery`, launches `RecoveryActivity` directly via Intent
   (bootstrap-context — see "Implementation status" above) and finishes
   itself. The brief MainActivity frame is acceptable for a rare
   developer-error path; explicitly chosen over
   `PackageManager.setComponentEnabledSetting` launcher swaps because the
   latter has known OEM-ROM flakiness.
5. **RecoveryActivity** is Room-free — it injects `DatabaseSnapshotProvider`
   and `RecoveryDiagnosticsExporter` but only calls Room-free methods
   (`getPreMigrationBackupFile`, `availableMigrationsLabel`,
   `exportStartupMigrationFailure`). `Room.databaseBuilder.build()` is
   lazy, so this is safe — no migration fires until a DAO call happens.

### RecoveryActivity location and DB-free invariant

`RecoveryActivity` lives in `feature/recovery` (moved out of `app/app` in
the recovery-boundary refactor). The `<activity>` manifest entry stays in
`app/app/src/main/AndroidManifest.xml` referencing the FQCN
`io.github.stslex.workeeper.feature.recovery.RecoveryActivity` — this is
the standard AGP pattern: manifest entries in the app module may reference
classes in any depended module.

The DB-free invariant — RecoveryActivity must not initialize Room — is a
**must-survive** property of the move. Verification:

- `feature/recovery` declares `:core:data:database` as a dependency for
  `DatabaseSnapshotProvider` + `APP_DATABASE_VERSION`. The `AppDatabase`
  Hilt module that constructs Room lives elsewhere; adding the project
  dependency does NOT auto-instantiate Room.
- `RecoveryActivity` injects only `DatabaseSnapshotProvider` (which itself
  accepts a Room instance lazily — only file-path / `PRAGMA` helpers are
  called here) and `RecoveryDiagnosticsExporter`.
- Phase 1 (recovery module creation) adds an explicit test asserting that
  resolving the Hilt graph for `RecoveryActivity` does not call any DAO,
  to prevent regression if a future contributor wires a Room-dependent
  collaborator into the activity.

It is a single Compose-rendered activity with four actions:

| Action | Behavior |
|---|---|
| Update app | Launches `Intent(ACTION_VIEW, market://details?id=<packageName>)` with a play.google.com fallback. The expected fix path: the developer ships a follow-up release with the missing migration. |
| Export raw data | Shares `cache/pre_migration_backup.db` via `FileProvider`. The user can keep this file and re-import on a working app version. |
| Report issue | Opens `GitHub issue URL` (see below) pre-filled with title and labels. The user attaches the diagnostic export. |
| Export diagnostics | Shares a generated `.txt` file with the diagnostic contents (see [Diagnostic file contents](#diagnostic-file-contents)). |

Strings are localized EN + RU (see [Strings](#strings-en--ru-required)).
RecoveryActivity must not depend on any string resource that lives in a
Room-dependent module — keep its strings either in `app/app/.../res/` or
in a Room-free shared module.

### Scenario 3 — user-initiated undo of last successful restore

**Implementation status:** shipped. The Settings "Revert last restore" row
is rendered in
[`BackupSection.AuthenticatedBlock`](../../feature/settings/src/main/kotlin/io/github/stslex/workeeper/feature/settings/ui/components/BackupSection.kt)
when `state.canRevertLastRestore` is true.
[`BackupClickHandler.observeRestoreState`](../../feature/settings/src/main/kotlin/io/github/stslex/workeeper/feature/settings/mvi/handler/BackupClickHandler.kt)
subscribes to `RestoreStateRepository.observePreRestoreBackupAvailable()`
and pushes the result into state. The tap publishes
`AppDialog.UndoRestoreConfirmation` via `AppDialogPublisher`. When the
user confirms, `AppDialogHost` dispatches `Action.UserAction(dialog,
AppDialogUserAction.ConfirmUndo)` to the Store, which records the choice.
`feature/recovery`'s `RestoreDialogChoiceObserver` (`@Singleton`, observes
`AppDialogObserver.observeUserActions()`) reacts: calls
`RestoreRecoveryCoordinator.performUndoRestore` for the file swap, then
calls `RestoreRecoveryCoordinator.restartApp()` directly (in-class
Intent-launch path; non-Composable call site, same Option Y as the
bootstrap restart in Scenario 1). The post-restart `UndoRestoreSuccess`
dialog survives via DataStore. No code path inside `AppDialogHost` calls
the coordinator directly — the host is generic and the reaction is owned
by `feature/recovery`.

After a successful Scenario 1 happy path, `pre_restore_backup.db` is
preserved. Settings exposes a new row "Revert last restore" while the file
exists. The user can choose to roll back their most recent restore once.

Flow:

1. **Settings load.** `BackupClickHandler` (or a sibling handler) checks for
   the existence of `cache/pre_restore_backup.db` and sets
   `state.canRevertLastRestore: Boolean` on the Settings state. The row
   renders only when this is `true`.
2. **User taps "Revert last restore".** Handler dispatches
   `AppDialogPublisher.publish(UndoRestoreConfirmation(originalDataDateEpochMs))`
   where `originalDataDateEpochMs` is the modification time of the
   preserved `.db` file (the moment of the most recent restore).
3. **`AppDialogHost`** renders the confirmation dialog with the formatted
   original-data date in the body.
4. **User confirms.** The dialog dispatches `Action.UserAction(
   UndoRestoreConfirmation, AppDialogUserAction.ConfirmUndo)` to
   `AppDialogStore`. The Store records the choice via `recordUserChoice`
   on the repository. `feature/recovery`'s `RestoreDialogChoiceObserver`
   (`@Singleton`, observes
   `AppDialogObserver.observeUserActions()`) sees the choice and:
   1. Calls `RestoreRecoveryCoordinator.performUndoRestore()`, which:
      - Swaps `<db>` ← `pre_restore_backup.db`.
      - Deletes the preserved file (consumed).
      - Clears the preserved-backup-available marker.
      - `AppDialogPublisher.publish(UndoRestoreSuccess)`.
   2. On `UndoRestoreOutcome.Succeeded`, calls
      `RestoreRecoveryCoordinator.restartApp()` directly (in-class
      Intent-launch path; non-Composable call site, same Option Y as the
      bootstrap restart in Scenario 1).
5. **Next startup.** Normal launch (Scenario 1 flag was already false; no
   migration drama because we are swapping back to the user's pre-restore
   schema which is by definition the same one we successfully migrated
   from earlier in the original Restore). `AppDialogHost` renders
   `UndoRestoreSuccess` on whatever destination the user lands on.

After undo: `pre_restore_backup.db` is consumed, `canRevertLastRestore`
flips to `false`, the "Revert last restore" row disappears from Settings.

After **next** Restore: previous `pre_restore_backup.db` is overwritten by
the new one. Only one slot at a time — bounded storage cost.

The dismiss/confirm split goes through DataStore-persisted user choices
rather than direct callbacks because the App Dialog mechanism has no typed
return channel by design (see
[app-dialogs.md → Cross-feature observation](app-dialogs.md#cross-feature-observation)).
The producer (Settings click on "Revert last restore") publishes the
confirmation; `feature/recovery`'s `RestoreDialogChoiceObserver` observes
`AppDialogObserver.observeUserActions()` and continues the flow when it
sees `AppDialogUserChoice(dialog = UndoRestoreConfirmation, action = ConfirmUndo)`.

## Recovery feature integration

`feature/recovery` is a Compose-library module (`convention.composeLibrary`)
that owns every coordinator, reporter, diagnostics exporter, and the
`RecoveryActivity`. Files moved out of `app/app/recovery/` in the boundary
refactor (see `documentation/lint-rules.md` and the Phase 1 commits
recovery-extracts itself from app/app).

| Symbol | Module before | Module after |
|---|---|---|
| `RecoveryActivity` | `app/app` | `feature/recovery` (manifest `<activity>` entry stays in app/app pointing at FQCN) |
| `RestoreRecoveryCoordinator` | `app/app` | `feature/recovery/domain/` |
| `StartupMigrationCoordinator` + `StartupCheck` | `app/app` | `feature/recovery/domain/` |
| `RestoreRecoveryReporter` | `app/app` | `feature/recovery/diagnostics/` |
| `StartupMigrationReporter` (+ `StartupMigrationFailure`) | `app/app` | `feature/recovery/diagnostics/` |
| `RecoveryDiagnosticsExporter` | `app/app` | `feature/recovery/diagnostics/` |
| `AppDialogActionsImpl` | `app/app/recovery/` | **DELETED** (the `AppDialogActions` interface in `feature/app-dialogs/api` is also deleted; its responsibilities split as described below) |
| `di/RecoveryModule.kt` | `app/app/di/` | **DELETED** (binding goes with the interface) |
| `recovery_*` + intent-side strings | `app/app/src/main/res/` | `feature/recovery/src/main/res/` |

`AppDialogActions`-shaped concerns are reallocated:

| Old responsibility | New owner |
|---|---|
| `performUndoRestore()` | `feature/recovery/RestoreDialogChoiceObserver.handleUndoConfirmation` reacting to `AppDialogObserver` → `RestoreRecoveryCoordinator.performUndoRestore()` |
| `publishUndoConfirmation()` | Already in `feature/settings/.../BackupClickHandler.requestRevertLastRestore` (the publishing producer is the click site; recovery does not need its own publish entry point) |
| `exportRestoreDiagnostics()` | `feature/recovery/RestoreDialogChoiceObserver.exportRestoreDiagnostics` reacting to `AppDialogObserver` → `RecoveryDiagnosticsExporter.exportRestoreFailure(...)` → `RestoreDialogChoiceObserver.shareDiagnostics(uri)` |
| `restartApp()` | Settings/Scenario-3 producer side dispatches `NavCommand.RestartApp` through `Navigator`. Bootstrap (`BaseApplication.onCreate` after Scenario 1 rollback) and consumer-side (`RestoreDialogChoiceObserver` after `UndoRestoreOutcome.Succeeded`) call `RestoreRecoveryCoordinator.restartApp()` directly — Option Y: the `MutableSharedFlow(replay = 0)` bus drops emissions with no live subscriber, so non-Composable call sites must go direct-Intent. |
| `openReportIssue(context)` free function in host | `feature/recovery/RestoreDialogChoiceObserver.openReportIssue` — wraps `Intent.ACTION_VIEW` with `@ApplicationContext` injected |
| `shareDiagnostics(context, uri)` free function in host | `feature/recovery/RestoreDialogChoiceObserver.shareDiagnostics` — wraps `Intent.ACTION_SEND` chooser with `@ApplicationContext` injected |

`feature/recovery`'s `RestoreDialogChoiceObserver` injects only
`@ApplicationContext` for all side effects: restart goes through
`RestoreRecoveryCoordinator.restartApp()` (in-class Intent-launch);
issue tracker and share chooser are inline private methods. The
`Navigator` interface stays UI-neutral — it does NOT learn about issue
trackers or share chooser intents; those are recovery-specific and live
inside the recovery feature.

The canonical project NavigationHandler pattern is Store-tied (consumes
`Action.Navigation.*` from a feature's MVI Store flow). `feature/recovery`
is intentionally Store-less per Phase 0 — there is no Store to dispatch
through — so the consumer-side reactor is shaped as a SharedFlow Observer
with `@ApplicationContext` injection instead. This is a deliberate
divergence from the canonical pattern, not an oversight.

### NavCommand additions

The navigation surface gains `NavCommand.OpenRecovery` so in-composition
callers can launch `RecoveryActivity` through the bus (the bootstrap path
in `MainActivity.onCreate` still launches directly via Intent — see
Scenario 2 "Implementation status" above for the Option Y rationale):

```kotlin
sealed interface NavCommand {
    data class NavTo(val screen: Screen) : NavCommand
    data class ReplaceTo(val screen: Screen) : NavCommand
    data class PopBack(val attrs: List<Pair<String, Any?>>) : NavCommand
    data object RestartApp : NavCommand
    data object OpenRecovery : NavCommand        // new
}

interface Navigator {
    fun navTo(screen: Screen); fun popBack(...); fun replaceTo(...)
    fun restartApp()
    fun openRecovery()                            // new — symmetric with restartApp()
}
```

`NavigatorExt.processCommand` handles `OpenRecovery` by launching the
RecoveryActivity FQCN; the FQCN lives in `feature/recovery` and is
referenced from `app/app/src/main/AndroidManifest.xml`.

## Storage lifecycle of preserved DB files

| File | Created by | Used by | Deleted by |
|---|---|---|---|
| `cache/pre_restore_backup.db` | `BackupClickHandler.confirmRestore` (before file replace) | Scenario 1 rollback **or** Scenario 3 undo | Next Restore (overwritten) OR Scenario 1 failure path (consumed) OR Scenario 3 undo (consumed) |
| `cache/pre_migration_backup.db` | `Application.onCreate` before Room init (when `restore_in_progress` is false) | Scenario 2 RecoveryActivity export | Successful Room open (the same `Application.onCreate` deletes after success) |

Maximum two extra DB files ever exist simultaneously. At the current
schema each file is ~170 KB; users with heavy histories trend toward a
few MB. Cache cost is acceptable.

> **Note on size growth.** Sizes grow with DB schema complexity and user
> data. The ~170 KB figure reflects v6 schema with light usage; heavy-use
> profiles already trend toward a few MB, and future schema additions
> (exercise-image metadata, multi-set history aggregates, telemetry-side
> tables) will lift the floor. Android's cache eviction policy reclaims
> `cacheDir` when storage pressure warrants, and the recovery flow fails
> closed (the "Revert last restore" row disappears) rather than crashing.
> **Revisit retention or compression if files routinely exceed 10 MB** —
> the cost-benefit of preserving an undo slot drops sharply at that scale,
> and at ~10 MB the cache eviction probability under typical phone storage
> pressure becomes high enough to make the slot unreliable for its primary
> purpose.

The choice of `cacheDir` over `filesDir` is deliberate: the system may
reclaim `cacheDir` under storage pressure, but in practice this only
happens when storage is critically low. If reclaimed, the
`canRevertLastRestore` check fails closed and the row disappears — no
crash, no data loss. The alternative (`filesDir`) would keep the file
through reclaim, but at the cost of pushing the file into the user's
uninstall-survives-backup quota.

## Pre-restore compatibility checks

Before the file replace in `restoreFromSnapshot`, `BackupInteractor.restoreLatest()`
verifies the manifest:

1. `backup.schemaVersion <= currentCodeSchemaVersion`.
   - If `backup > current` → reject with `BackupError.BackupTooNew`.
   - UI: confirmation dialog disabled with body "Update the app to restore
     this backup".
   - **Renamed from `BackupError.SchemaTooNew` shipped in v1** — same
     trigger, same payload, same UI consequence. The rename itself is a
     breaking-change call-site sweep; see
     [Rename step](#rename-step) for the files the implementation PR must
     touch in the same commit as the sealed-class declaration change.
2. `hasMigrationPath(backup.schemaVersion, currentCodeSchemaVersion)`.
   - If `false` → reject with `BackupError.MissingMigrationPath`.
   - UI: confirmation dialog disabled with body "This backup is from an
     older version that this app build cannot migrate".

Both checks happen **before** download. The peek-schema-then-decide
pattern already exists for the v1 `SchemaTooNew` variant (which the
recovery work renames — see [Rename step](#rename-step)); the new
`MissingMigrationPath` check plugs into the same point (after manifest
peek, before download). Pulling the check earlier avoids a wasted
download for backups we cannot use.

## `MIGRATIONS` list — introspectable

The Room builder is currently `Room.databaseBuilder(...)
.fallbackToDestructiveMigration*()`. The recovery work replaces this with:

```kotlin
internal val MIGRATIONS: List<Migration> = listOf(
    MIGRATION_1_2,
    MIGRATION_2_3,
    // ...
)

@Provides
@Singleton
fun provideAppDatabase(@ApplicationContext context: Context): AppDatabase =
    Room.databaseBuilder(context, AppDatabase::class.java, "app.db")
        .addMigrations(*MIGRATIONS.toTypedArray())
        // NO fallbackToDestructiveMigration — migration failure routes to
        // Scenario 1 or 2 recovery flow.
        .build()
```

`hasMigrationPath(from: Int, to: Int): Boolean` lives next to `MIGRATIONS`:

- Builds a directed graph of `(Migration.startVersion → Migration.endVersion)`.
- BFS from `from`. Returns `true` iff `to` is reachable.
- Pure function. Unit-tested at the data-database module level.

Existing destructive-migration users (v3, v4 → v5 — see
[tech-debt.md → Schema Migration Debt](../tech-debt.md#schema-migration-debt))
need real `MIGRATION_X_Y` definitions before this work lands. That is the
trigger condition for the parked tech-debt entry.

### CI-enforced migration test

For every defined schema bump, the test suite asserts:

1. A `Migration` object with matching `startVersion` / `endVersion` is
   present in `MIGRATIONS`.
2. The migration is exercised end-to-end against the previous schema
   snapshot using `MigrationTestHelper` (per
   [`.claude/skills/add-database-migration.md`](../../.claude/skills/add-database-migration.md)).

The enforcement lives in one named test class so the implementation PR
has an unambiguous wiring target:

- **Class:** `MigrationsRegistryTest`
- **Path:** `core/data/database/src/test/kotlin/io/github/stslex/workeeper/core/database/migration/MigrationsRegistryTest.kt`
- **Module:** `:core:data:database` (Gradle path; the test runs under the
  module that owns `AppDatabase` and the `core/data/database/schemas/`
  snapshots).
- **CI command:** `./gradlew :core:data:database:testDebugUnitTest`. This
  target is already on the standard CI matrix; no new workflow plumbing
  is required.

What the class does:

1. Loops over `MIGRATIONS` (the top-level `List<Migration>` introspected
   in the parent section). For each entry it:
   1. Uses Room's `MigrationTestHelper` to create a database at
      `entry.startVersion` from the schema snapshot at
      `core/data/database/schemas/<startVersion>.json`.
   2. Calls `helper.runMigrationsAndValidate(dbName, entry.endVersion,
      validateDroppedTables = true, entry)` to exercise the migration
      against the snapshot of `entry.endVersion`.
   3. Asserts the result is valid (Room's helper throws if the resulting
      schema disagrees with the snapshot).
2. Asserts the **completeness invariant**: for each consecutive pair
   `(n, n + 1)` where `n` ranges from `1` to `currentCodeSchemaVersion - 1`,
   `hasMigrationPath(n, n + 1)` returns `true`. Concretely, for every
   adjacent step in the version range there must exist **either**:

   - a direct `Migration` with `startVersion = n` and `endVersion = n + 1`,
     **or**
   - a multi-step path from `n` to `n + 1` via other `MIGRATIONS` entries
     (e.g. `n → n + 2 → n + 1` is admissible if both edges exist).

   Implementation reuses `hasMigrationPath`'s BFS and walks the
   consecutive-pair list, asserting each pair.

   This is **stronger than "reachable from `1`"**. A directed graph with
   edges `1 → 2, 1 → 3, 1 → 4, 1 → 5` makes every version reachable from
   `1`, but `hasMigrationPath(4, 5)` is still `false` — and that is the
   case that matters when a user with DB version 4 upgrades to code at
   schema 5. The consecutive-pair walk catches the realistic failure mode
   the reachable-from-`1` check misses:

   - Developer adds `Migration(5 → 6)` during a schema bump but forgets
     `Migration(4 → 5)`.
   - `hasMigrationPath(4, 6)` returns `false` because no chain from `4`
     reaches `6`.
   - `MigrationsRegistryTest` fails on the `(4, 5)` pair before the
     destructive-fallback removal can hurt a user shipped on DB version 4.

   This is the regression guard for the
   `fallbackToDestructiveMigration*` removal.

The class is **distinct** from the per-migration test classes that the
[`add-database-migration`](../../.claude/skills/add-database-migration.md)
skill generates. Those per-migration tests stay (focused per-migration
assertions: data shape, null handling, default values). `MigrationsRegistryTest`
adds the array-level integrity that prevents a registered-but-not-wired
migration or a missing-version-range gap.

The test reads schema snapshots from `core/data/database/schemas/`. A
schema bump without a corresponding migration fails this class before the
destructive-fallback removal can hurt any user.

## Room configuration changes

Remove all `fallbackToDestructiveMigration*()` calls from the
`AppDatabase.Builder` chain. After this change, migration failure has only
two possible outcomes — Scenario 1 rollback or Scenario 2 RecoveryActivity.
There is no silent data-wipe path anymore.

The pre-Room snapshot in `Application.onCreate` (see Scenario 2 step 2) is
the safety net that lets us remove destructive fallback without risk: even
if Room blows up at first open, the user's data is in
`cache/pre_migration_backup.db` waiting to be exported.

## Diagnostic file contents

Plain text, UTF-8, shared via `FileProvider`. The same format covers both
restore-time and startup-time diagnostics; the differentiator is the
"Scenario" header and the situation-specific fields at the bottom.

Common fields (every diagnostic):

- App `versionName` + `versionCode`
- Device `Build.MODEL`, `Build.MANUFACTURER`, Android API level
- Current code schema version (`AppDatabase.DB_VERSION` or equivalent)
- Detected DB schema version (read via SQLite `PRAGMA user_version` from
  the preserved file, since Room cannot open it)
- Registered `MIGRATIONS` list as `from→to` pairs
- Exception type + message + stacktrace (first 50 frames, truncated)
- ISO 8601 timestamp of diagnostic generation

Scenario 1 (restore-time) additional fields:

- Backup version (manifest's `db_schema_version`, the version we tried to
  restore from)
- Backup `created_at_epoch_ms` (manifest field)
- Backup `app_version` (manifest field)

Scenario 2 (startup-time) additional fields:

- Install source from `PackageManager.getInstallSourceInfo(...)` (Play vs
  sideload — informs whether "Update app" can resolve via the store)
- Time since last successful startup (read from a `last_successful_startup_at`
  DataStore flag written every time the app reaches the first composition
  successfully)

## Crashlytics non-fatals

`FirebaseCrashlytics.recordException(throwable)` with custom keys so we can
filter the Crashlytics dashboard by scenario:

| Key | Type | Notes |
|---|---|---|
| `migration_from_schema` | `Int` | Detected DB schema version. |
| `migration_to_schema` | `Int` | Current code schema version. |
| `available_migrations` | `String` | `"1→2,2→3,..."` joined from MIGRATIONS. |
| `app_version` | `String` | `BuildConfig.VERSION_NAME`. |
| `triggered_at` | `String` | `"restore"` for Scenario 1, `"startup"` for Scenario 2. |
| `restore_in_progress` | `Boolean` | Always `true` for Scenario 1, `false` for Scenario 2. |
| `backup_version` | `Int` | Scenario 1 only — what the user tried to restore from. |

`FirebaseCrashlytics.setUserId(...)` is **not** added here — the existing
project policy is to not pin a user identifier. Filtering by the keys above
is enough to triage.

## GitHub issue URL

```
https://github.com/stslex/Workeeper/issues/new
  ?title=<urlencoded title>
  &labels=bug,migration
```

The body is a short, localized instruction telling the user to attach the
diagnostic file. Pre-filling the body with the diagnostic content is
deliberately avoided — the file is the artifact, not a URL-encoded blob
that GitHub's body-length cap will truncate.

Issue title template (EN): `Migration failure on app version X.Y.Z`.
Issue title template (RU): `Ошибка миграции в версии X.Y.Z`.

## State / UI changes

`feature/settings`:

- New state field `canRevertLastRestore: Boolean` on `SettingsStore.State`.
  Computed from `cache/pre_restore_backup.db` existence at Authenticated
  state load. Populated by a new handler call (or extended within
  `BackupClickHandler.bootstrapOrRehydrate`).
- New row "Revert last restore" inside `BackupSection`, visible only when
  `canRevertLastRestore && backupAuth is Authenticated`. Tap dispatches
  `Action.Backup.RequestRevertLastRestore`, which goes through to
  `AppDialogPublisher.publish(UndoRestoreConfirmation(...))`.
- Existing `RestoreConfirmationDialog` and `SignOutConfirmationDialog` stay
  in `feature/settings` for this PR. Migration to `AppConfirmationDialog`
  generic is deferred — see [tech-debt.md](../tech-debt.md) (new entry to
  be added in the cross-references commit).

`app/app`:

- `AppDialogHost` mounted as a sibling of `NavHost` in `App.kt` (see
  [app-dialogs.md → AppDialogHost mounting](app-dialogs.md#appdialoghost-mounting)).
  The "sibling" placement is load-bearing: it scopes the
  `@HiltViewModel AppDialogStore` to the host Activity rather than to a
  navigation destination.
- Manifest `<activity>` entry for `RecoveryActivity` stays in
  `app/app/src/main/AndroidManifest.xml` referencing the FQCN
  `io.github.stslex.workeeper.feature.recovery.RecoveryActivity`. Launched
  directly via Intent from `MainActivity.onCreate` when the Scenario 2 path
  triggers (bootstrap-context — see "NavCommand additions" above).

`feature/recovery`:

- New `RecoveryActivity` class in
  `feature/recovery/src/main/kotlin/io/github/stslex/workeeper/feature/recovery/RecoveryActivity.kt`.

`core/data/database` (or wherever `DatabaseSnapshotProvider` lives):

- Extend `DatabaseSnapshotProvider` (or add a sibling helper) with
  pre-flight file-copy operations: `copyToPreRestoreSlot()`,
  `copyToPreMigrationSlot()`, `swapWithPreRestoreSlot()`. Mirror the
  existing `restoreFromSnapshot` shape: WAL/SHM sidecar cleanup, atomic
  rename. Tests live in `DatabaseSnapshotProviderImplTest`.

## Strings (EN + RU required)

Localization keys for the new copy. Russian translations are deferred to
the translator pass — mark "RU pending" if uncertain.

App dialog strings (live in `feature/app-dialogs/impl/src/main/res/`):

- `dialog_restore_success_title`, `dialog_restore_success_body` (with date
  argument), `dialog_restore_success_ok`
- `dialog_restore_failure_title`, `dialog_restore_failure_body` (with
  error-reason argument), `dialog_restore_failure_report_action`,
  `dialog_restore_failure_export_logs_action`, `dialog_restore_failure_ok`
- `dialog_undo_restore_confirmation_title`,
  `dialog_undo_restore_confirmation_body` (with original-data-date
  argument), `dialog_undo_restore_confirm`,
  `dialog_undo_restore_cancel`
- `dialog_undo_restore_success_title`, `dialog_undo_restore_success_body`

RecoveryActivity strings (live in `app/app/src/main/res/`):

- `recovery_title`, `recovery_body`
- `recovery_update_app`, `recovery_export_data`,
  `recovery_report_issue`, `recovery_export_diagnostics`

Settings backup additions (live in `feature/settings/src/main/res/`):

- `feature_settings_backup_revert_last_restore_label`
  ("Revert last restore")

## Error taxonomy additions

The recovery work changes the typed `BackupError` surface in two ways:

- **Rename** `BackupError.SchemaTooNew` → `BackupError.BackupTooNew`. Same
  trigger, same payload (`backupSchema`, `appSchema`), same UI consequence
  as the v1 variant. The rename aligns the error name with the
  user-facing framing ("the backup is too new for your app") instead of
  the implementation detail ("the schema integer doesn't fit"). This is a
  **breaking change** to the typed `BackupError` API — see
  [Rename step](#rename-step) below for the call-sites that must update
  atomically.
- **Add new variant** `BackupError.MissingMigrationPath`. Strictly
  additive. Raised when the backup's schema version is ≤ the current
  code's schema, but `hasMigrationPath` returns false — i.e. the backup
  is older than the oldest reachable schema this code can migrate.
  Distinct from `BackupTooNew` (backup newer than code) and
  `CorruptedBackup` (manifest unreadable / SQLite magic mismatch).

| Variant | Raised when | Worker retry | UI consequence |
|---|---|---|---|
| `BackupError.BackupTooNew` *(renamed from `SchemaTooNew`)* | Pre-restore: `backup.schemaVersion > currentCodeSchemaVersion`. | N/A (terminal) | Restore confirmation dialog disabled + "Update the app to restore this backup" message |
| `BackupError.MissingMigrationPath` *(new variant)* | Pre-restore: `hasMigrationPath(backup.schemaVersion, currentCodeSchemaVersion)` returns false. | N/A (terminal) | Restore confirmation dialog disabled + "Backup is from an older version that this app build cannot migrate" message |

### Rename step

The `SchemaTooNew → BackupTooNew` rename is a **breaking change** to the
typed `BackupError` API. Every consumer either pattern-matches the sealed
variant (exhaustive `when`) or references the name in a string-mapped
table (`BackupErrorCode.from`, `BackupUiMapper.toUi`), so the rename must
land **atomically** — one commit, every call-site updated together, no
deprecate-and-remove staging. A typed-API rename split across two PRs
would leave one of them with a `when` that does not compile.

Call-sites the implementation PR must update in the same commit as the
sealed-class declaration change:

- `core/data/backup/api/.../error/BackupError.kt` — sealed-class
  declaration: `data class SchemaTooNew(...)` → `data class BackupTooNew(...)`.
  Payload fields (`backupSchema: Int`, `appSchema: Int`) carry over
  unchanged.
- `core/data/backup/api/.../scheduling/BackupErrorCode.kt` — flat-enum
  entry and the `BackupErrorCode.from(error)` mapping branch. Whether the
  enum value also renames (`SCHEMA_TOO_NEW` → `BACKUP_TOO_NEW`) is a
  follow-up decision: renaming forces a one-shot DataStore migration for
  the persisted `last_error` value, while keeping the enum string as
  `SCHEMA_TOO_NEW` internally avoids that. **Recommendation:** keep the
  persisted enum string as `SCHEMA_TOO_NEW` (implementation detail, not
  API) and rename only the variant + the `BackupErrorUi` label.
- `core/data/backup/google-drive/.../error/DriveErrorMapper.kt` — any
  branch that constructs the variant updates to the new name.
- `core/data/backup/google-drive/.../DriveBackupStorage.kt` — direct
  raise sites (e.g. inside `restoreLatest`'s peek-and-decide block)
  update to the new name.
- `feature/settings/.../mvi/mapper/BackupUiMapper.kt` (occasionally
  referenced in this spec as `BackupErrorUiMapper`) — the
  `BackupError` → `BackupErrorUi` branch renames. If
  `BackupErrorUi.SCHEMA_TOO_NEW` renames in lock-step (recommended), the
  corresponding `R.string.feature_settings_backup_error_schema_too_new`
  key and the EN+RU string resources rename with it.
- Tests covering the above — at minimum
  `core/data/backup/google-drive/.../error/DriveErrorMapperTest.kt` and
  `feature/settings/.../mvi/mapper/BackupUiMapperTest.kt`. The
  exhaustive `BackupError` → `BackupErrorUi` branch coverage in the
  latter is the test most likely to break first if the rename misses a
  call-site, so running `:feature:settings:testDebugUnitTest` locally is
  the quickest pre-flight check.

A two-step rename (deprecate-alias + later remove) is **not** worth the
cost — the surface is small enough that an atomic rename is reviewable in
one sitting, and a deprecation alias would just be dead code from the
day it lands.

`BackupError.MissingMigrationPath` lands in the same PR (or a sibling
one — either is fine). It reuses the existing plumbing shape
`DriveErrorMapper` → `BackupErrorCode` → `BackupUiMapper` → `BackupErrorUi`
→ localized string with no additional surface changes. The mapping table
in [backup.md → Error taxonomy](backup.md#error-taxonomy) updates the
existing `SchemaTooNew` row to `BackupTooNew` and adds the new
`MissingMigrationPath` row when this lands.

## Out of scope / decisions

Decisions locked for this spec, with rationale.

- **One preserved slot at a time.** Multi-slot history multiplies cache
  cost linearly and forces a picker UI that the v1 surface explicitly
  defers. Single slot covers the load-bearing case ("oops, I just restored
  the wrong backup, undo it") and bounds storage.
- **Two-restart UX on Scenario 1 failure.** Acceptable because (a) it is
  rare given the pre-restore check, (b) a single-restart "try to rollback
  in the same process" path is fragile — the in-process DAO graph after a
  failed `Room.databaseBuilder.build()` is in an undefined state, and
  cold restart is the only safe recovery.
- **Scenario 2 is dev-error only.** A correctly-shipped app cannot reach
  it. The CI-enforced migration test catches the omission before merge;
  RecoveryActivity is the safety net for the case where review and tests
  both miss a bump.
- **`cacheDir` not `filesDir` for preserved files.** System reclaim under
  storage pressure is acceptable — the row disappears, no crash. Storage
  cost survives uninstall otherwise.
- **No cross-device undo.** Undo consumes the local preserved file. There
  is no server-side "last-restore breadcrumb" to coordinate across devices,
  and adding one would push this into a multi-device sync problem.
- **No callback into producer from `UndoRestoreConfirmation`.** Per
  [app-dialogs.md](app-dialogs.md), the dialog publishes a sibling
  flag and the Scenario-3 handler observes it. This trade keeps the
  cross-feature dialog surface decoupled from the producer.
- **Diagnostic file format is plain text, not JSON.** The user is the
  primary consumer (attaching to a GitHub issue). Plain text reads in any
  viewer; JSON is fragile for human consumption.

## Troubleshooting additions

The following entries belong in
[backup.md → Troubleshooting](backup.md#troubleshooting) when this work
lands. They are listed here so the cross-link from `backup.md` resolves
to a real section in the recovery spec until the implementation PR
folds them in.

### Two restarts after Restore tap

Expected on the Scenario 1 failure path. The first restart attempts to
migrate the freshly-restored database; the second restart happens after
the automatic rollback. After the second restart, the user sees a
`RestoreFailure` dialog explaining that the restore could not be applied
and their data is intact. The double-restart is bounded — the user does
not see further restarts.

If the user sees more than two restarts in a row, that is a bug — likely
the rollback path failed to delete `pre_restore_backup.db` or to clear
the `restore_in_progress` flag, so the next launch enters the recovery
flow again with no rollback target.

### "Revert last restore" row appears in Settings

Indicates a prior restore succeeded and `cache/pre_restore_backup.db` is
preserved for one undo opportunity. Tapping a new Restore overwrites the
preserved file — the undo opportunity is consumed by the new Restore.

The row disappears in three cases:

- The user tapped Revert and the undo completed.
- The user triggered a new Restore (preserved slot was overwritten).
- The system reclaimed `cacheDir` under storage pressure.

The third case is acceptable — no data loss, just the loss of the undo
option, which is by definition a non-essential safety net.

### `RecoveryActivity` launches instead of `MainActivity`

Indicates Scenario 2 — Room migration failed at app startup, not during a
restore. Possible causes:

- The developer shipped a schema bump without a registered migration. The
  CI-enforced migration test should have caught this before merge.
- A registered migration crashed at runtime due to user-data shape (e.g.
  a runtime assertion failed on a corner case).

The user remediation is one of: update the app (most cases — the developer
ships a follow-up release), export raw data and re-import on a working
build, or file an issue with the diagnostic export attached.

## Related

- [app-dialogs.md](app-dialogs.md) — dependency. The four `AppDialog`
  variants (`RestoreSuccess`, `RestoreFailure`, `UndoRestoreConfirmation`,
  `UndoRestoreSuccess`) are defined there.
- [backup.md](backup.md) — the v1 backup feature this builds on. The
  upload / list / scheduling surface is unchanged; the restore path is
  extended with pre-restore checks and post-restore recovery.
- [tech-debt.md → Schema Migration Debt](../tech-debt.md#schema-migration-debt)
  — the parked entry whose trigger condition this spec satisfies. The
  destructive-fallback removal lives in the implementation PR.
- [`.claude/skills/add-database-migration.md`](../../.claude/skills/add-database-migration.md)
  — the procedural recipe for writing a `Migration` object and its test.
  Recovery's CI-enforced migration test follows this pattern.
- [architecture.md → Destructive app-restart through the bus](../architecture.md#destructive-app-restart-through-the-bus)
  — the `navigator.restartApp()` mechanism used by Scenarios 1 and 3.
- [architecture.md → Room database](../architecture.md) — Migration policy
  background, schema snapshot location.
