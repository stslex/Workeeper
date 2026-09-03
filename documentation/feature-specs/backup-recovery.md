# Feature spec — Backup Recovery

**Status:** Implemented. Builds on the shipped Drive backup feature documented
in [backup.md](backup.md); the upload, list, and scheduling surfaces are
unchanged. The current restore protocol is installation-scoped and
attempt-owned; released positional state is supported only by the explicit
rollout table below.

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

The pre-restore compatibility checks, `feature/app-dialogs` infrastructure,
restore-time recovery, user undo, and startup-migration recovery are wired
end-to-end. The durability correction replaces the released positional
`C/R/S` protocol with `RestoreProtocolState(installEpoch, attempt,
activeUndo, terminalOutbox)` and exact opaque references.

- **Scenario 1** (restore-time) — pre-flight in
  [`RestoreRecoveryCoordinator`](../../feature/recovery/src/main/kotlin/io/github/stslex/workeeper/feature/recovery/domain/RestoreRecoveryCoordinator.kt),
  triggered from
  [`BaseApplication.onCreate`](../../app/app/src/main/java/io/github/stslex/workeeper/BaseApplication.kt).
  Every restore owns an immutable `UndoRef` below
  `noBackupFilesDir/restore-recovery`. A failed restore compensates from that
  exact ref; verified finalization publishes `RestoreSuccess` / `RestoreFailure`
  through a replayable terminal outbox.
- **Scenario 2** (startup) — pre-flight in `StartupMigrationCoordinator`
  (in `feature/recovery`), triggered from
  [`BaseApplication.onCreate`](../../app/app/src/main/java/io/github/stslex/workeeper/BaseApplication.kt)
  after restore-state epoch reconciliation finds no unresolved restore
  attempt. It routes `APP_DOWNGRADE` / `NO_MIGRATION_PATH` /
  `CANNOT_PEEK_LIVE_DB` / `LIVE_DB_OPEN_FAILED` to the Room-free
  `RecoveryActivity`.
- **Scenario 3** (user-initiated undo) — the Settings "Revert last restore"
  row in
  [`BackupSection`](../../feature/settings/src/main/kotlin/io/github/stslex/workeeper/feature/settings/ui/components/BackupSection.kt)
  publishes `AppDialog.UndoRestoreConfirmation`. `feature/recovery`'s
  `RestoreDialogChoiceObserver` (`@SingleIn(AppScope)`, observes
  `AppDialogObserver.observeUserActions()`) reacts to the user's
  ConfirmUndo choice by applying the dialog's exact `UndoRef`. The rollback
  is journalled under its own owner and clears the active pointer only if it
  still names the applied ref.
- Android backup rules exclude
  `datastore/restore_state_prefs.preferences_pb` from legacy backup, API 31+
  cloud backup, and API 31+ device transfer. Runtime epoch reconciliation is
  still mandatory defence in depth.
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
   exact immutable `activeUndo.ref`.
4. Scenario 2 — startup migration failure recovery via a dedicated
   `RecoveryActivity` (Room-free, exposes export / report).
5. Removal of `fallbackToDestructiveMigration*` from the Room builder.
6. Introspectable `MIGRATIONS` list with a `hasMigrationPath(from, to)`
   helper, plus a CI-enforced test that asserts every schema bump has a
   registered migration.

## Out of scope

- **Backup history beyond one advertised undo.** Each attempt has a unique
  immutable file, but only `activeUndo` is user-visible. A verified new
  restore atomically replaces or clears that pointer; older unreferenced files
  are garbage-collected rather than exposed as history.
- **Manual user-initiated DB export under normal conditions.** Scenario 2's
  RecoveryActivity has Export raw data because the user has no other way
  out; a feature for "export the live DB whenever" is deferred.
- **Server-side backup retention beyond N=3.** Rotation policy unchanged
  from current — see [backup.md → Rotation](backup.md#rotation).
- **Encryption at rest.** Still tracked in
  [tech-debt.md → Backup integrations](../tech-debt.md#backup-integrations).
  Recovery work neither blocks nor enables encryption — both can ship
  independently.
- **Cross-device undo.** An `UndoRef` is local to its installation epoch and
  never transferred as an arbitrary path. A device with no same-install
  active ref shows no "Revert last restore" row, regardless of cloud state.
- **A "Restore from older backup" picker.** Latest-only restore unchanged
  from v1 (see [backup.md → Out of scope](backup.md#out-of-scope-and-decisions)).
  The undo flow is a single-step "reverse the most recent restore", not a
  picker over arbitrary backups.
- **Changing Android backup eligibility for Room or disabling platform
  backup.** This correction excludes only restore protocol metadata.
  `allowBackup` and the Room database's backup policy remain unchanged; any
  change to either is a separate maintainer/product decision.

## Two scenarios — distinct flows

The two restore-related failure modes look superficially similar (Room
fails to open the database on launch) but have very different triggers,
user expectations, and recovery shapes. Conflating them into one path is
the failure mode this spec exists to avoid.

| Discriminator | Scenario 1 (restore-time) | Scenario 2 (startup-time) |
|---|---|---|
| What user did just before | Tapped Restore in Settings | Updated the app (Play, sideload) |
| Persisted discriminator | Same-epoch `RestoreAttempt.Restore` is unresolved | Epoch-reconciled protocol has no unresolved restore attempt |
| Why migration failed | Backup schema needs a migration that this code lacks (rare — pre-restore check should have caught it) OR a previously-untriggered bug in an existing migration | Developer shipped a code-side schema bump without a registered migration |
| Recovery shape | Exact-ref compensation where possible; otherwise DB-free recovery. Only the integrity-gated legacy missing-ref case offers Continue | RecoveryActivity (DB-free) with Update / Export / Report; no Continue |
| User-visible side effect | Two restarts in quick succession (acceptable edge case), data intact afterwards | App does not reach main UI until update or remediation |

The released `restore_in_progress` flag is read only by the rollout table.
After migration, the sealed attempt type, owner, phase, and installation epoch
are the discriminator. A foreign epoch is cleared before any callback, file
lookup, live swap, pointer observation, or recovery-surface decision.

### Scenario 1 — restore-time migration failure

Trigger: user explicitly tapped Restore, backup file was atomically swapped
in, app restarted, Room migration crashes during subsequent open.

**Implementation status:** current. `RestoreLatestBackupUseCase` submits a
uniquely owned transaction to `DatabaseReplacement`;
`RestoreRecoveryCoordinator` performs verified finalization from cold-start or
candidate preflight. The runtime, not a positional filename, owns mutation
ordering and source lifetime.

Flow:

1. **Download and transfer ownership.** The caller may download into cache,
   but submission mints a unique `RestoreOwnerId` and publishes
   `staged_restore_<owner>.db` below
   `noBackupFilesDir/restore-recovery` before suspension, journal claim, or
   PONR. Publication copies into a unique `<final>.<nonce>.creating` file,
   syncs it and the root, then holds the permanent cross-process publication
   lock across a no-follow absence check and same-directory atomic move.
   Failure to create or write the recovery root rejects while the existing
   generation is still serving.
2. **Validate and admit capacity.** While Room and the outgoing generation
   still serve, validate the staged source, checkpoint the live WAL, and query
   `StorageManager.getAllocatableBytes()`. The restore requires the
   post-checkpoint live size for its undo, the staged-source size for
   `<db>.tmp`, and a 16 MiB margin. Equality passes. Query failure, negative
   values, arithmetic overflow, or insufficient bytes returns a typed
   `RejectedBeforeMutation`; it creates no undo, claims no journal, closes no
   database, and swaps no file. The check is advisory and does not call
   `allocateBytes()`; later writes must still handle ENOSPC.
3. **Prepare N.** Publish immutable `undo_<owner>.db` through a unique
   `undo_<owner>.db.<nonce>.creating` partial and the same locked atomic move;
   never overwrite an existing immutable undo. Reversibly quiesce UI and
   DB-bound work, then atomically persist `Restore(N, Prepared, context,
   undoRef=N, sourceRef=N)` while leaving the previous active pointer P
   unchanged. Prepared is never claimed for a quiesce rejection.
4. **Commit the live file.** Close only after the pre-PONR gates, replace the
   live file through `<db>.tmp`, then owner-check the transition to
   `Committed(N)`. `Committed(N), active=P` is an internal recovery state;
   UI and DB-bound admission stay closed while it exists.
5. **Verify and finalize once.** `RestartProcess` invokes the shared finalizer
   from cold-start preflight; `RebuildInProcess` invokes it from candidate
   preflight before publication. A successful verification performs one
   owner-checked restore-state edit that replaces `activeUndo` with N, writes
   `RestoreTerminal.RestoreSucceeded`, and removes N's attempt. If verified N
   is missing, the restored generation is still proven: the same edit clears
   `activeUndo` and records `previousVersionAvailable=false`. P is never
   advertised as undo of N.
6. **Replay the terminal handoff.** Publish the terminal through
   `AppDialogPublisher`, then clear the outbox only after publication returns
   successfully. A finalization-write failure leaves `Committed(N)`, P, and N
   intact. A later app-dialog write failure leaves the finalized outbox
   pending and also returns `FinalizationPending`; cold startup routes to the
   sealed recovery surface before chores, and a candidate is not published.
   A committed rollback does not report `RecoveryCompleted` until that
   mandatory publication succeeds. Its exact source becomes collectible after
   durable rollback finalization, independently of terminal publication. Once
   the app-dialog write is durable, restore-state acknowledgement failure is
   replay cleanup: the outbox remains and deduplicated publication repeats on
   the next launch.
   For `RebuildInProcess`, a newly finalized success stays in the outbox until
   candidate chores and the dialog observer arm. If arming throws, the runtime
   owner-checks the exact persisted success owner and N-or-null pointer,
   releases the candidate, and retries once without compensation. An
   unreadable or mismatched proof is Fatal; it cannot authorize rolling a
   verified restore back.
7. **Collect only unowned files.** After state is durable, sweep strict
   protocol filenames from persisted ownership. P becomes collectible only
   after N is active or the pointer is cleared. Delete failure is retryable
   garbage, never permission to resolve protocol state.

The Prepared claim attempt is the PONR boundary and is marked before the
DataStore call, because persistence may succeed before the call throws. An
ambiguous claim therefore seals the runtime; `RestartProcess` restarts into
cold recovery. Validation, capacity and reversible-quiesce rejection remain
before that boundary and keep the old generation serving. Restart-terminal
state is set under transition serialization, so a queued second transaction
cannot run against or republish the outgoing generation. Restart callback
failure is a `Fatal` result, not a completed restore.

If verification fails, compensation applies N's exact `UndoRef` and journals a
separate `Rollback` owner with `origin=ScenarioOneRecovery`. Rollback
finalization uses `clearActiveUndoIf(N)`, so compensation from N cannot clear
an unrelated P. The source is deleted only after durable committed-rollback
finalization and terminal handoff. A same-epoch missing or corrupt exact ref is
`RecoveryRequired`; it is never treated as foreign or silently ignored.

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
`setContent { App() }`. `NavCommand.OpenRecovery` is processed only by the
composable-mounted `NavigationEventBusSetup`, and the bus is a
`MutableSharedFlow(extraBufferCapacity = 64)` with `replay = 0`: an emission
with no attached subscriber is dropped, and dropped silently in the strongest
sense — the buffer makes `tryEmit` return `true`, so not even the failed-emit
warning fires. `Navigator.openRecovery()` is therefore for in-composition
callers only; the caller should `finish()` after dispatching, and the fresh
task replaces the current one via `FLAG_ACTIVITY_NEW_TASK`.

**Implementation deviates from the literal spec** in two ways:

1. **The pre-flight does not trigger Room migration.** It peeks the live
   `PRAGMA user_version` via the Room-free `SQLiteDatabase.openDatabase`
   and consults the registered `hasMigrationPath`. A migration is trusted
   if it is registered — `hasMigrationPath` answers *registered*, never
   *succeeds* — so a registered-but-buggy migration peeks as `Proceed` and
   throws later, at this process's first Room open. That residual case is
   caught and routed in step 4 below rather than left to escape; before that
   guard existed it killed the process inside `Application.onCreate`, where
   no Activity yet exists to route from. Missing-migration is the separate,
   wider failure mode that PR-B's `MigrationsRegistryTest` catches pre-merge.
2. **The pre-Room snapshot is written lazily, only on the
   `RouteToRecovery` branch.** The live db is pristine at that point
   (Room never opened it on this launch), so the snapshot captures the
   same bytes the spec asks for, without paying the file-copy cost on
   every normal launch.

Flow:

1. **Application startup reconciles restore ownership first.** A matching
   installation epoch is required before decoding any attempt, pointer,
   outbox, or ref. A foreign new-format epoch atomically clears all protocol
   keys without callbacks, path dereference, file deletion by persisted path,
   or live swap; startup then continues through this ordinary schema preflight.
   Same-epoch unresolved state short-circuits into Scenario 1 instead.
2. **Schema peek.** `peekSnapshotSchemaVersion` reads the live db's
   `user_version` via `SQLiteDatabase.openDatabase` — no Room init, no
   migration trigger.
3. **Decide.** Five branches over the peek's result, covering three of the four
   `StartupMigrationFailureReason` members; the fourth is decided in step 4:
   - `db == code` → `Proceed`. Delete any stale durable recovery export from
     a previous resolved recovery route.
   - `db > code` → `RouteToRecovery(APP_DOWNGRADE)`. Preserve snapshot,
     record Crashlytics non-fatal.
   - `db < code` + `hasMigrationPath = true` → `Proceed`. Room handles the
     migration lazily on first DAO access.
   - `db < code` + `hasMigrationPath = false` → `RouteToRecovery(NO_MIGRATION_PATH)`.
     Preserve snapshot, record Crashlytics non-fatal.
   - Peek throws → `RouteToRecovery(CANNOT_PEEK_LIVE_DB)`. Preserve
     snapshot best-effort, record Crashlytics non-fatal.
4. **First Room open, guarded.** The peek cannot see a migration that is
   registered and fails, so `StartupProcessor` wraps this process's first Room
   open — `prepareWearSyncStorage`, the last synchronous boundary before
   graph-owned listeners may touch Room — and routes any throw, deliberately
   unnarrowed, into `StartupMigrationCoordinator.recordLiveDatabaseOpenFailure`.
   That writes `RouteToRecovery(LIVE_DB_OPEN_FAILED)` into `lastDecision`,
   preserves the live file for the recovery export, and carries the throwable on
   the Crashlytics non-fatal.

   Recording is exactly as load-bearing as catching. `MainActivity` routes on
   `lastDecision` and on nothing else, so a guard that merely caught would leave
   a `Proceed` verdict over a database this launch had just proved unopenable —
   a silently broken app rather than a crash. A cold start that routes here also
   seals DB-bound worker admission, as any recovery route does; the in-process
   candidate preflight never seals, because its abort leaves a healthy
   generation serving. `CancellationException` is re-thrown rather than
   reported: a cancelled candidate transition has proven nothing about the file.
5. **Preserve the raw export durably.** A recovery route checkpoints the live
   file directly and publishes `recovery_export.db` below the no-backup root
   through `recovery_export.db.creating`. Export failure is visible but does
   not change the recovery decision.
6. **MainActivity launches the DB-free surface.** `RecoveryActivity` is opened
   directly by Intent and MainActivity finishes. The Intent's Continue flag
   defaults to false; startup-migration routes never opt in.

### RecoveryActivity location and DB-free invariant

`RecoveryActivity` lives in `feature/recovery` (moved out of `app/app` in
the recovery-boundary refactor). The `<activity>` manifest entry stays in
`app/app/src/main/AndroidManifest.xml` referencing the FQCN
`io.github.stslex.workeeper.feature.recovery.RecoveryActivity` — this is
the standard AGP pattern: manifest entries in the app module may reference
classes in any depended module.

The DB-free invariant — launching and composing RecoveryActivity must not open
Room or framework SQLite — is a **must-survive** property. Verification:

- Composition reads only Intent values, retained UI state, file-presence
  helpers, restore protocol state, reinitialization, and diagnostics/export
  collaborators. It performs no integrity query on launch.
- The fail-fast `RecoveryActivityDbFreeTest` launches startup migration,
  interrupted restore without Continue, and interrupted restore with Continue
  enabled, then resolves every lazy dependency while any SQLite connection is
  configured to fail.
- Framework `SQLiteDatabase` is confined to the explicit Continue checker on
  IO after the user taps Continue. It is never reached by activity launch,
  composition, raw-export lookup, or dependency warm-up.

It is a single Compose-rendered activity with scenario-gated actions:

| Action | Behavior |
|---|---|
| Update app | Startup-migration only. Launches `Intent(ACTION_VIEW, market://details?id=<packageName>)` with a play.google.com fallback. |
| Continue | Genuine `InterruptedRestore` only. Runs the two-step integrity and owner flow below; hidden for `RecoveryRequired`, `FinalizationPending`, and startup migration. |
| Export raw data | Looks up durable `recovery_export.db`. On explicit share, copies it to `cache/recovery_share/workeeper_recovery_export.db` and exposes only that copy through `FileProvider`. |
| Report issue | Opens `GitHub issue URL` (see below) pre-filled with title and labels. The user attaches the diagnostic export. |
| Export diagnostics | Shares a generated `.txt` file with the diagnostic contents (see [Diagnostic file contents](#diagnostic-file-contents)). |

Raw export is typed UI state: `Available`, `Unavailable(reason)`, or
`Failed(reason)`. Missing durable data, root lookup failure, share-copy
failure, and share-Intent failure are not silent. Export success is never a
prerequisite for Continue. `file_provider_paths.xml` exposes
`cache/recovery_share/` (and the unrelated existing `exercise_images/` root),
not `noBackupFilesDir` or a broad files root.

#### InterruptedRestore Continue escape

Continue exists only for the synthetic same-install rollout attempt shaped as
`Restore(Prepared, undoRef=null, sourceRef=null)` after released
`restore_in_progress=true`, missing/unusable C, and a header-compatible live
database. `MainActivity` opts the Intent in only when the coordinator's exact
outcome is `PreflightOutcome.InterruptedRestore`; the default-false Intent and
the model both enforce that boundary.

1. The user explicitly taps Continue.
2. On IO, framework `SQLiteDatabase` opens the live file read-only, consumes
   every row of `PRAGMA integrity_check`, and accepts only a non-empty result
   where every row is exactly `ok`. It then reads `PRAGMA user_version` and
   requires the current schema or a registered migration path.
3. A second State-backed confirmation explains that the app cannot know
   whether the interrupted restore completed.
4. Confirmation rereads the epoch-reconciled protocol, requires the same
   owner and eligible shape, atomically calls
   `abandonInterruptedAttempt(owner)`, updates UI state, then restarts last.
   Worker admission remains sealed until that restart.

No other same-install missing ref is accepted: Prepared attempts with owned
refs, committed restores, and rollbacks route to `RecoveryRequired` when their
exact required file is absent or unusable.

### Scenario 3 — user-initiated undo of last successful restore

**Implementation status:** current. Settings observes the epoch-filtered
`RestoreStateRepository.observeActiveUndo()`. A row is visible only when a
same-install `ActiveUndo(ref, originalDataDateEpochMs)` exists; the UI never
derives availability from a positional filename.

Flow:

1. **Settings observes the pointer.** Epoch reconciliation happens before the
   flow emits. Foreign or absent state emits no active undo; a same-epoch
   missing file remains a protocol failure if the exact ref is later applied.
2. **User taps "Revert last restore".** The producer publishes
   `UndoRestoreConfirmation(undoRef=active.ref,
   originalDataDateEpochMs=active.originalDataDateEpochMs)`. App-dialog
   persistence stores the validated owner with the date. Dedup and dismiss are
   owner-aware, so an old dialog cannot block or dismiss a newer owned
   confirmation.
3. **`AppDialogHost` renders generic State.** It has no recovery coordinator
   dependency and emits the exact dialog/ref in the transient user choice.
4. **User confirms.** The dialog dispatches `Action.UserAction(
   UndoRestoreConfirmation, AppDialogUserAction.ConfirmUndo)` to
   `AppDialogStore`. `RestoreDialogChoiceObserver` passes
   `dialog.undoRef` unchanged to `performUndoRestore(ref)`.
5. **Rollback admits before mutation.** It validates that the current pointer
   still equals the requested ref, validates that exact immutable source, and
   checks allocatable capacity for the source-sized `<db>.tmp` plus the 16 MiB
   margin. A rejection leaves the generation, journal, pointer, source, and
   dialog intact. `NotCurrent` acknowledges only the obsolete owner.
6. **Rollback commits under a new owner.** Persist
   `Rollback(id, Prepared, sourceRef=appliedRef, origin=UserUndo)`, swap from
   that exact ref, then record `Committed`. Startup/candidate verification
   atomically applies `ClearIf(appliedRef)`, writes `UndoSucceeded` to the
   terminal outbox, and removes the rollback attempt. Therefore user undo
   clears its own pointer, while compensation from N cannot clear unrelated P.
7. **Handoff and cleanup.** Outbox publication produces
   `UndoRestoreSuccess`; only then is the terminal acknowledged, and
   `RecoveryCompleted` requires that publication to have succeeded. A later
   acknowledgement failure retains replay cleanup but does not retract the
   durable dialog. The source is deleted after durable rollback finalization,
   independently of this UI handoff. A committed rollback can still finalize
   from descriptor identity if best-effort source deletion already occurred.
   Delete failure remains retryable garbage.

After the next successful restore, active undo changes only during the atomic
verified finalization: P remains protected while `Committed(N), active=P` is
internal, then the pointer becomes N or absent before UI admission reopens.
There is no overwriteable slot and no window where P is advertised as undo of
the newly visible generation.

The dismiss/confirm split goes through transient user choices while the dialog
itself remains DataStore-persisted because App Dialogs has no typed return
channel by design (see
[app-dialogs.md → Cross-feature observation](app-dialogs.md#cross-feature-observation)).
The confirmation is not cleared before a destructive reaction. Durable
rollback finalization and terminal-outbox publication own the success handoff;
`NotCurrent` and Cancel dismiss only the matching confirmation owner.

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
| `restartApp()` | `RestoreRecoveryCoordinator.restartApp()`, a one-line delegation to the injected `AppReinitializer` seam (`core/core/.../platform/`). Bootstrap (`BaseApplication.onCreateGraphBootstrap`, when `StartupProcessor.coldStart` returns `RestartRequired`) and consumer-side (`RestoreDialogChoiceObserver`, after an undo resolves `Succeeded` **or** `RecoveryRequired`) call it directly — Option Y: the `MutableSharedFlow(replay = 0)` bus drops emissions with no live subscriber, so non-Composable call sites must not route through it. The **Settings restore path does not call it at all**; that restart is runtime-owned (below). |
| `openReportIssue(context)` free function in host | `feature/recovery/RestoreDialogChoiceObserver.openReportIssue` — wraps `Intent.ACTION_VIEW` with the injected app-scoped `Context` |
| `shareDiagnostics(context, uri)` free function in host | `feature/recovery/RestoreDialogChoiceObserver.shareDiagnostics` — wraps `Intent.ACTION_SEND` chooser with the same injected app-scoped `Context` |

`feature/recovery`'s `RestoreDialogChoiceObserver` constructor-injects a plain,
**unqualified** `Context`, used only by its issue-tracker and share-chooser
intents, which are inline private methods. There is no `@ApplicationContext`
qualifier — that annotation is a Hilt idiom and no production source names it
(the historical DI sample later in this document still shows it). The
application-lifetime guarantee comes from scope instead: the class is
`@SingleIn(AppScope::class) @ContributesBinding(AppScope::class)`, and the
app-scope graph supplies the application `Context`. It must stay that way — an
`Activity` `Context` bound here would outlive its host and leak.

Restart is **not** one of those side effects: it goes through
`RestoreRecoveryCoordinator.restartApp()`, which is exactly
`appReinitializer.reinitialize()` — the coordinator builds no `Intent` and
imports no `android.*`. The `Navigator` interface stays UI-neutral — it does
NOT learn about issue trackers or share chooser intents; those are
recovery-specific and live inside the recovery feature.

**Runtime-owned restart (the Settings restore path).** Since Phase 5 the
restore restart is not dispatched by any feature at all. `AppRuntime` runs the
swap under `ReplacementPolicy.RestartProcess`: it publishes `Transitioning`,
quiesces UI / worker / snackbar admission through `GenerationQuiescer.quiesce`
(each wait bounded by a timeout, and a failure unwinds to `Serving` before the
point of no return), claims the mutation durably, then closes and replaces the
live file in `runRestartProcessSwap` without building a candidate generation.
After **every** post-PONR outcome — `Completed` and `FailedAfterMutation`
alike — `restartAfterPonr` invokes the host-owned
`RuntimeTransitionPolicy.restartProcess`, wired in `BaseApplication` to
`AppReinitializer(applicationContext).reinitialize()`. There is no delay hop
and no feature coroutine to outlive the swap; a restart hook that throws makes
the delivered outcome `Fatal`. See `architecture.md` → "Destructive app-restart
through the `AppReinitializer` seam" and
`kmp-phase-5-startup-processor.md` §8.4.

The canonical project NavigationHandler pattern is Store-tied (consumes
`Action.Navigation.*` from a feature's MVI Store flow). `feature/recovery`
is intentionally Store-less per Phase 0 — there is no Store to dispatch
through — so the consumer-side reactor is shaped as a SharedFlow Observer
holding the app-scoped `Context` instead. This is a deliberate divergence from
the canonical pattern, not an oversight.

### NavCommand additions

The navigation surface gains `NavCommand.OpenRecovery` so in-composition
callers can launch `RecoveryActivity` through the bus (the bootstrap path
in `MainActivity.onCreate` still launches directly via Intent — see
Scenario 2 "Implementation status" above for the Option Y rationale):

```kotlin
sealed interface NavCommand {
    data class NavTo(val screen: Screen) : NavCommand
    data class ReplaceTo(val screen: Screen) : NavCommand
    data object PopBack : NavCommand
    data class PopBackWithResult(val key: String, val result: Any) : NavCommand
    data object OpenRecovery : NavCommand        // new
}

interface Navigator {
    fun navTo(screen: Screen); fun popBack(); fun replaceTo(...)
    fun <S, R : Any> popBackWithResult(destination: KClass<S>, result: R)
    fun restartApp()
    fun openRecovery()                            // symmetric with restartApp()
}
```

There is **no** `NavCommand.RestartApp`: restart never travels over the bus, so
it has no command variant to drop. `Navigator.restartApp()` survives on the
interface and still resolves the `AppReinitializer` seam directly, but nothing
reaches it any more — its one call site, the `Action.Navigation.RestartApp`
branch in `SettingsNavigationHandler`, is unreachable because no producer emits
that action. The recovery paths call the coordinator; the restore path is
runtime-owned.

`NavigatorExt.processCommand` handles `OpenRecovery` by launching the
RecoveryActivity FQCN; the FQCN lives in `feature/recovery` and is
referenced from `app/app/src/main/AndroidManifest.xml`.

## Storage lifecycle of recovery assets

Every authoritative asset lives below
`context.noBackupFilesDir/restore-recovery`. The downloaded caller temp may
start in cache, but it is consumed after the runtime publishes the staged
source; cache deletion cannot remove same-install protocol truth.

| Name | Role | Lifetime owner |
|---|---|---|
| `install_epoch` | Stable random installation identity, atomically published from `install_epoch.<nonce>.creating`. | Always preserved. |
| `.publication.lock` | Permanent cross-process serialization inode for immutable final-name publication; process death releases its kernel lock. | Always preserved; never swept. |
| `staged_restore_<owner>.db` | Runtime-owned restore source after caller ownership transfer. | Unresolved Restore attempt. |
| `undo_<owner>.db` | Immutable pre-image for that exact restore or rollback source. | Unresolved attempt, `activeUndo`, or pending terminal that still requires it. |
| `recovery_export.db` | Durable raw/pre-migration recovery export. | Recovery UI until explicitly resolved/removed. |
| `<final>.<nonce>.creating` | Unique partial publication; the final name is visible only after a complete synced copy and locked atomic move. | In-flight serialized publication; orphaned partials are sweepable after a crash. |
| `cache/recovery_share/<name>` | On-demand share copy exposed through the narrow FileProvider root. | Non-authoritative cache only. |
| `cache/pre_restore_backup.db` | Released positional C. | Rollout migration only; never selected by the new protocol. |

Immutable undo and staged-source creation never overwrite a final file. Each
publication gets a unique nonce so two processes never copy into the same
partial inode. The root rejects symlinks/non-directories and derives every
filename from validated lower-case UUIDs; no absolute path is persisted or
followed. Complete immutable files and their parent directory entries are
synced before success. Mutable export/share/live replacement syncs its
temporary and parent directory around atomic rename.

The successful restore peak remains approximately five DB-sized files because
live replacement still copies through `<db>.tmp`. Attempt-owned immutable undo
reduces overwrite/full-file-write crash states; it does not reduce the
worst-case peak from five to four. The capacity gate accounts conservatively
for the additional undo and temp writes and remains advisory.

Owner-aware garbage collection runs under startup/transition serialization.
It preserves the install token, all refs from the unresolved attempt, active
undo, durable export, and files required by pending finalization/outbox. It
deletes only unreferenced strict names (`undo_<uuid>.db`,
`staged_restore_<uuid>.db`, their legacy exact `.creating` partials, and their
unique `.<nonce>.creating` partials) below the root.
Deletion failure is reported for retry and never changes attempt, pointer, or
terminal truth.

WAL flush mechanics in `DatabaseSnapshotProviderImpl` remain load-bearing:

- `preserveDbBeforeMigration()` checkpoints through direct framework
  `SQLiteDatabase` without Room, then publishes the complete raw file durably.
  A checkpoint or copy failure produces a typed unavailable/failed export; it
  is not silently treated as success.
- Room-owned create-undo/capture paths execute
  `PRAGMA wal_checkpoint(TRUNCATE)`, consume its result row, and require
  `busy == 0` with all reported log frames checkpointed before copying. A
  missing row, busy reader, incomplete frame count, or prepared-but-unstepped
  statement cannot publish an immutable image.
- Live replacement removes old `-wal` and `-shm` sidecars before publishing
  `<db>.tmp`. A failed sidecar deletion is a typed failure and preserves the old
  main file; it is never ignored while replacement continues.

### Installation epoch and restore-state DataStore

`RestoreStateRepositoryImpl` stores the protocol in
`files/datastore/restore_state_prefs.preferences_pb`. New-format keys are
grouped under `restore_protocol_*`: installation epoch; attempt epoch/id/type/
phase and Restore/Rollback refs; attempt context; active-undo epoch/ref/date;
and terminal-outbox epoch/owner/type/payload. `UndoRef` and
`RestoreSourceRef` persist only validated owner IDs, never paths.

Before reading or mutating an attempt, pointer, availability flow, source ref,
or terminal, the repository compares the stored protocol and record epochs
with the stable no-backup `install_epoch`:

- Match: decode same-install state. A missing required exact file is a local
  recovery failure, never an ignore rule.
- New-format mismatch: atomically clear attempt, pointer, outbox, released and
  obsolete protocol keys, install the local epoch, invoke no owner callback,
  dereference no persisted path, and leave the live database untouched.
- Missing protocol epoch plus released keys: enter the explicit rollout table;
  absence alone is not proof of foreign state.
- Same-install malformed protocol: return `RestoreProtocolRead.Corrupt` and
  route conservatively without path dereference.

Static Android backup rules also exclude the exact DataStore file from legacy
`backup_rules.xml`, API 31+ `<cloud-backup>`, and API 31+
`<device-transfer>`. `noBackupFilesDir` is already platform-ineligible. This
does not set `allowBackup=false` and does not change Room database eligibility.

### Released-state rollout table

The released wire inputs are `restore_in_progress` plus its context,
`pre_restore_backup_available`, its original date, and positional
`cache/pre_restore_backup.db` (C). Every boundary is replay-safe:

| Released state | Migration/result |
|---|---|
| In progress + valid C | Copy C immutably to the synthetic interrupted-attempt ref, persist `Restore(Prepared, undoRef=<synthetic>, sourceRef=null, activeUndo=null)`, then delete C. Stale availability is not interpreted separately. |
| In progress + missing/unusable C + healthy compatible live DB | Persist the synthetic `Restore(Prepared, undoRef=null, sourceRef=null)` and route to the integrity-gated `InterruptedRestore` Continue flow. |
| In progress + invalid live DB + valid C | Persist the owned attempt and recover from the migrated exact C ref. |
| In progress + neither live DB nor C usable | Persist the synthetic missing-ref attempt and route to `RecoveryRequired`. |
| No in-progress marker + availability/date + valid C | Copy C to the synthetic active-undo ref, persist the pointer with the original date, then delete C. |
| Availability + missing/unusable C | Install empty current state and clear stale availability; no undo is advertised. |

If a crash occurs after immutable copy but before state installation, replay
syncs the existing synthetic final file and recovery-root directory before
validating and reusing it. A failed copy or sync installs no new protocol state
and preserves C, even when the same process can still read the unsynced final.
C is deleted only after the new attempt or pointer is durable. Obsolete
path-bearing intermediate keys are cleared without interpreting their values.

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
`noBackupFilesDir/restore-recovery/recovery_export.db` waiting for an
explicit on-demand share copy. Export creation failure is visible in the
RecoveryActivity and never weakens the database-admission decision.

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
| `startup_failure_reason` | `String` | Scenario 2 only — `StartupMigrationFailureReason.name`. |
| `install_source` | `String` | Scenario 2 only — Play vs sideload, from `PackageManager.getInstallSourceInfo(...)`. |

The Scenario 2 pre-flight detects unrecoverable state by pure file inspection,
so there is no Room exception to forward. `recordStartupMigrationFailure`
therefore records a synthesized `StartupMigrationFailure(fromSchema, toSchema,
reason)` when `exception` is null — Crashlytics needs *some* `Throwable` to group
non-fatals by, and without one the failure mode would not surface on the
dashboard at all. The class is declared at file scope rather than nested inside
`StartupMigrationReporter` so Crashlytics groups by a clean class name without
dashboard noise.

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

- `canRevertLastRestore` and its original-data date are projected from the
  epoch-filtered `ActiveUndo?` flow, not file existence.
- "Revert last restore" is visible only for authenticated state with an
  active pointer. Tap publishes `UndoRestoreConfirmation` with that exact
  `UndoRef` and date.
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
- The manifest `<activity>` entry for `RecoveryActivity` stays in
  `app/app/src/main/AndroidManifest.xml` referencing the FQCN
  `io.github.stslex.workeeper.feature.recovery.RecoveryActivity`.
- `MainActivity` launches it for startup-migration recovery and restore
  outcomes requiring a sealed recovery surface. It sets a non-DB Intent
  boolean true only for exact `InterruptedRestore`; the default is false.

`feature/recovery`:

- `RecoveryActivity` owns typed export/Continue State and the two-step
  confirmation. Its framework SQLite checker is invoked only by explicit
  Continue.

`core/data/database`:

- `DatabaseSnapshotProvider` exposes only exact-ref restore/rollback methods,
  durable recovery export, capacity admission, and owner-aware sweep.
  `RestoreRecoveryFiles` alone derives protocol paths below the no-backup root.

## Strings (EN + RU required)

All recovery copy is localized in EN + RU.

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

RecoveryActivity strings (live in `feature/recovery/src/main/res/`):

- Scenario 2: `recovery_title`, `recovery_body`, `recovery_update_app`,
  `recovery_report_title`
- Scenario 1: `recovery_restore_title`, `recovery_restore_body`,
  `recovery_restore_report_title` — selected by the `RecoveryScenario` extra the
  launcher stamps on the Intent. "Update app" is not rendered on this route.
- Shared: `recovery_export_data`, `recovery_report_issue`,
  `recovery_export_diagnostics`, typed export failure/unavailable copy, and
  Continue checking, integrity/schema failures, second confirmation, and
  abandon-failure copy.

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

- **One advertised undo at a time.** Immutable attempt files may briefly
  coexist, but `activeUndo` is a single pointer and no history picker exists.
  Persisted ownership bounds retention and makes old files collectible.
- **Two-restart UX on Scenario 1 failure.** Acceptable because (a) it is
  rare given the pre-restore check, (b) a single-restart "try to rollback
  in the same process" path is fragile — the in-process DAO graph after a
  failed `Room.databaseBuilder.build()` is in an undefined state, and
  cold restart is the only safe recovery.
- **Scenario 2 is dev-error only.** A correctly-shipped app cannot reach
  it. The CI-enforced migration test catches the omission before merge;
  RecoveryActivity is the safety net for the case where review and tests
  both miss a bump.
- **`noBackupFilesDir` for authoritative recovery files.** Cache eviction is
  not an acceptable durability event. Uninstall removes the no-backup root;
  Android backup/transfer does not recreate it.
- **No cross-device undo.** Undo consumes the local preserved file. There
  is no server-side "last-restore breadcrumb" to coordinate across devices,
  and adding one would push this into a multi-device sync problem.
- **No callback into producer from `UndoRestoreConfirmation`.** Per
  [app-dialogs.md](app-dialogs.md), the exact owned dialog emits a transient
  choice and the Scenario-3 observer reacts. Durable finalization/outbox owns
  the terminal handoff, keeping the generic dialog host decoupled.
- **Room and platform backup policy unchanged.** Only restore protocol
  metadata is explicitly excluded. Changing Room eligibility or
  `allowBackup` is a separate maintainer/product decision.
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

If the user sees more than two restarts in a row, inspect the same-install
attempt and terminal outbox. Atomic rollback-finalizer failure keeps the
committed attempt unresolved. Later terminal-publication failure instead keeps
the finalized pointer/outbox recovery-routed; it must not be reported
`RecoveryCompleted`. Source collection after durable rollback finalization is
valid, but clearing the outbox or a legacy flag is not a repair.

### "Revert last restore" row appears in Settings

Indicates a same-install `activeUndo` pointer exists for one exact immutable
file. A new restore does not overwrite it: P stays protected until N verifies,
then atomic finalization replaces P with N or clears the pointer if N's undo is
unavailable. User undo clears only the matching applied ref.

Cache deletion must not affect this row. If the exact same-install file is
missing or corrupt, the pointer is not silently healed away; applying or
recovering it routes to `RecoveryRequired`.

### `RecoveryActivity` launches instead of `MainActivity`

The title/body identify whether this is startup migration or restore recovery.
Possible causes include:

- The developer shipped a schema bump without a registered migration. The
  CI-enforced migration test should have caught this before merge.
- A registered migration crashed at runtime due to user-data shape (e.g.
  a runtime assertion failed on a corner case).
- A same-install restore/rollback attempt is unresolved, an exact required ref
  is missing/corrupt, or verified finalization is still pending.

Update is shown only for startup migration. Continue is shown only for the
genuine integrity-gated legacy `InterruptedRestore` outcome and still requires
two explicit user actions. Raw export and diagnostics remain available
independently when their typed state permits sharing.

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
- [architecture.md → Destructive app-restart through the `AppReinitializer` seam](../architecture.md#destructive-app-restart-through-the-appreinitializer-seam)
  — the runtime-owned restart on the restore path, and the direct
  `AppReinitializer` calls Scenarios 1 and 3 make through
  `RestoreRecoveryCoordinator.restartApp()`.
- [architecture.md → Room database](../architecture.md) — Migration policy
  background, schema snapshot location.
