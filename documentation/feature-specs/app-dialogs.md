# Feature spec — App Dialogs

**Status:** Planned. Initial implementation lands alongside the backup-recovery
work (see [backup-recovery.md](backup-recovery.md)); this spec captures the
shared cross-feature dialog mechanism. Future use cases (app-update available,
account warnings, license expiry, etc.) plug into the same catalog without new
infrastructure.

App Dialogs is the cross-feature surface for showing modal confirmations or
acknowledgements that must (a) survive process restart, (b) appear regardless
of which navigation destination the user is on, and (c) be authored
independently by the feature that produces them without coupling to the
feature module that renders them.

It is **deliberately distinct** from the existing per-feature `DialogState`
pattern (see [`.claude/skills/mvi-dialog-state.md`](../../.claude/skills/mvi-dialog-state.md)
and [`compose-state-discipline.md → Rule 4`](../../.claude/skills/compose-state-discipline.md)).
Per-feature dialog state is in-memory, screen-scoped, and dismissed when the
user navigates away. App Dialogs are persisted in DataStore, scoped to the
app process, and visible from any destination until the user explicitly
acknowledges them.

## Status

- Planned. No code shipped.
- Tracks alongside [backup-recovery.md](backup-recovery.md) — the initial
  catalog (`RestoreSuccess`, `RestoreFailure`, `UndoRestoreConfirmation`,
  `UndoRestoreSuccess`) exists to support that flow's post-restart dialogs.
- Future variants (app-update available, account warnings) added in their
  own PRs with a one-line catalog entry + a render branch.

## Scope

- Cross-feature dialog catalog (`AppDialog` sealed interface).
- Singleton `AppDialogStore` whose state is a derived view over DataStore
  Preferences flags — not an in-memory queue.
- `AppDialogHost` Composable mounted at app root, outside `NavHost`, that
  renders the current dialog (if any) above all destinations.
- Generic `AppConfirmationDialog` Composable in `core/ui/kit` so feature
  code does not have to author dialog chrome per variant.
- `AppDialogPublisher` interface for producers — they call `publish(...)`
  and never touch UI.

## Out of scope

- A general "snackbar publisher" — `SnackbarManager` already handles that
  surface and lives at a different lifetime tier (transient, not persisted).
- Dialog stacking. The host renders **one** dialog at a time, selected via a
  priority rule; if two flags are set simultaneously, the higher-priority
  variant wins and the lower-priority one stays pending until dismiss.
- Per-user opt-out / "do not show again" infrastructure. Every dialog in the
  catalog is shown unconditionally because every variant is shipped only
  when load-bearing. If a future variant needs opt-out, it owns the flag in
  its own DataStore namespace, not in `pending_*`.
- Internationalization-aware ordering: priority is a property of the variant,
  not of the user locale.
- Direct callbacks from dialog action buttons into the producing feature. The
  feature publishes the dialog and walks away; user actions on the dialog
  either clear the flag (no follow-up) or set a *different* flag that the
  producing feature observes through its own DataStore subscription. Cross-
  feature callback wiring is intentionally absent — it would re-introduce the
  coupling this mechanism exists to avoid.

## Module layout

| Module | Purpose |
|---|---|
| `feature/app-dialogs/api` | Provider-neutral contracts: `AppDialog` sealed types, `AppDialogPublisher` interface. Imported by any feature that needs to surface a dialog. |
| `feature/app-dialogs/impl` | `AppDialogStore[Impl]` (singleton, DataStore-backed), `AppDialogHost` Composable, the per-variant render branches, internal `AppDialogKeys`. Bound at the app graph; feature modules do not depend on this. |
| `core/ui/kit` | `AppConfirmationDialog` generic Composable (title, body, confirm/dismiss buttons, optional `isDestructive` flag for red emphasis). Variant-specific composables in `feature/app-dialogs/impl` delegate to this for consistent chrome. |

The api/impl split mirrors the convention used by `core/data/backup/*`: any
feature can depend on `:feature:app-dialogs:api` for `AppDialogPublisher` and
the sealed type, and only `app/app` wires `:feature:app-dialogs:impl` so the
singleton store and host are reachable.

## Single source of truth

The state of every pending dialog lives **exclusively** in DataStore
Preferences. There is no in-memory queue, no `MutableStateFlow<List<AppDialog>>`
inside `AppDialogStore`, and no caller-side write to an in-memory field.

The reasoning is:

- **Process-restart survival.** Backup recovery's primary failure mode is
  "Room migration crashes after a Restore". The process dies, the activity
  is recreated, the user must still see the `RestoreFailure` dialog. An
  in-memory queue would be wiped; a DataStore-backed flag survives.
- **One writer.** `AppDialogStore` is the only writer for `pending_*` keys.
  Producers go through `publish(dialog)` which translates the variant into
  the flag set. Dismiss is the only consumer-driven write, and it goes
  through the same store.
- **Reactive view.** `currentDialog: Flow<AppDialog?>` combines all flag
  reads and resolves to the single highest-priority variant. UI collects this
  flow with `collectAsStateWithLifecycle`. Setting any flag causes the flow
  to re-emit; clearing all flags causes it to emit `null` and the host
  composes nothing.

Dismiss = clear flags in DataStore → flow re-emits → UI re-renders without
the dialog (or with the next-priority dialog if multiple are pending).

## DataStore keys (per dialog type)

Naming convention: `pending_<dialog_type>_<field>`. Primary flag is always a
`Boolean`; metadata fields are typed by their payload. Keys live in a single
`AppDialogKeys` object inside the impl module so the catalog stays grep-able.

Initial catalog (backup-recovery v1):

| Key | Type | Notes |
|---|---|---|
| `pending_restore_success` | `Boolean` | Primary flag for `RestoreSuccess`. |
| `pending_restore_success_at_epoch_ms` | `Long` | Time of restore completion (for "Restored on …" body line). |
| `pending_restore_failure` | `Boolean` | Primary flag for `RestoreFailure`. |
| `pending_restore_failure_reason` | `String` | `BackupErrorCode.name`. |
| `pending_undo_restore_confirmation` | `Boolean` | Primary flag for `UndoRestoreConfirmation` (user-initiated). |
| `pending_undo_restore_confirmation_original_date_epoch_ms` | `Long` | Date of the data that will be restored on confirm. |
| `pending_undo_restore_success` | `Boolean` | Primary flag for `UndoRestoreSuccess`. |

Each `AppDialog` variant maps to one boolean primary key plus zero or more
typed metadata keys. Adding a new dialog type =

1. Define one new boolean key + N metadata keys.
2. Add a new variant to `AppDialog` (api module).
3. Add the (read flag → build variant) branch in `AppDialogStore`'s
   `currentDialog` resolution.
4. Add a render branch in `AppDialogHost`.

The pattern is documented in
[`.claude/skills/app-dialogs-pattern.md`](../../.claude/skills/app-dialogs-pattern.md).

### Key naming stability

`AppDialogKeys` names are wire format: they live on user devices in
`app_dialogs_prefs.preferences_pb` and survive every app update. Adding new
keys for new variants is safe (existing users simply have no value for the
new key, which decodes as `null`/`false` — the correct "no pending dialog"
default). **Renaming an existing key requires the deprecation path
described in `AppDialogKeys.kt`'s class KDoc** — drop a rename and any user
mid-flow loses their pending dialog on update.

## Priority ordering

When multiple flags are set simultaneously, `AppDialogStore` resolves the
**single** current dialog by walking a fixed priority list and returning the
first variant whose flag is set:

1. `RestoreFailure` — critical; the user must acknowledge that their restore
   failed and their data is intact.
2. `RestoreSuccess` — informational; positive confirmation of restore.
3. `UndoRestoreSuccess` — informational; positive confirmation of undo.
4. `UndoRestoreConfirmation` — user-initiated; least urgent because the user
   chose to enter this flow themselves.

Resolution is implemented as a single atomic DataStore read (one
`preferencesDataStore.data.first()` per emission) followed by an in-Kotlin
priority walk. Tests assert that the read happens once per emission, not
once per priority level.

The fixed-list shape is deliberate. Sorting by enum ordinal or by a `priority:
Int` field on the variant would let a future PR change priority by mistake.
A literal `when`-style chain is the same line count and surfaces the choice
in code review.

## Dismiss policy

Each variant declares which gestures clear which flag. The host wires
`androidx.compose.ui.window.DialogProperties(dismissOnBackPress,
dismissOnClickOutside)` to match:

| Dialog | Back press | Click outside | Action button | Implicit dismiss |
|---|---|---|---|---|
| `RestoreFailure` | blocked | blocked | clears flag | none — must tap OK |
| `RestoreSuccess` | clears flag | blocked | clears flag | back = OK |
| `UndoRestoreSuccess` | clears flag | blocked | clears flag | back = OK |
| `UndoRestoreConfirmation` | clears flag | blocked | confirm clears flag and sets next-step flag / cancel clears flag | back = Cancel |

`dismissOnClickOutside = false` everywhere is intentional: these dialogs are
load-bearing, not casual. A taps-outside dismiss would invite the user to
swipe one away without reading it, which defeats the whole purpose of a
persistent app-level dialog.

Failure variants block back-press to force explicit acknowledgement. The
trade-off is acceptable because there is exactly one button and the dialog
is bounded — the user is one tap away from continuing.

## AppDialog catalog (initial)

```kotlin
// feature/app-dialogs/api/.../model/AppDialog.kt
sealed interface AppDialog {
    val id: String  // for dedup / diagnostics / telemetry

    data class RestoreSuccess(
        val restoredAtEpochMs: Long,
        val previousVersionAvailable: Boolean,
    ) : AppDialog {
        override val id: String = "restore_success"
    }

    data class RestoreFailure(
        val reason: BackupErrorCode,
    ) : AppDialog {
        override val id: String = "restore_failure"
    }

    data class UndoRestoreConfirmation(
        val originalDataDateEpochMs: Long,
    ) : AppDialog {
        override val id: String = "undo_restore_confirmation"
    }

    data object UndoRestoreSuccess : AppDialog {
        override val id: String = "undo_restore_success"
    }
}
```

Adding a new variant is a PR; the catalog table above and the priority list
update in the same change.

## AppDialogPublisher contract

```kotlin
// feature/app-dialogs/api/.../publisher/AppDialogPublisher.kt
interface AppDialogPublisher {

    /**
     * Persist the dialog to DataStore so it surfaces on the next composition
     * of `AppDialogHost` (which may be after process restart).
     *
     * `publish()` is **dedup-aware**: if the variant's primary flag is
     * already set in DataStore, the call is a no-op. The implementation
     * reads the current state inside the same `edit { ... }` transaction it
     * would otherwise use to write, so the check-then-write is atomic
     * against concurrent producers. Dialogs do not stack.
     */
    suspend fun publish(dialog: AppDialog)
}
```

There is intentionally **no** `cancel(...)` / `clear(...)` API. Dismiss is
user-driven only. A producer that needs to "withdraw" a dialog has a bug:
either the dialog should not have been published, or the producing flow
should set the dismiss-flag itself through its own DataStore subscription
(not through `AppDialogPublisher`).

### Dedup semantics

`publish(dialog)` exists to prevent duplicate-display from double-trigger
code paths. Concrete failure modes that the dedup guarantee catches:

- A retry loop that fires both an immediate and a delayed publish for the
  same upstream failure (e.g. Scenario 1 rollback retried before the first
  publish lands).
- Two observers in different layers translating the same upstream event
  into a publish (e.g. one in the `BackupClickHandler` and one in
  `Application.onCreate` pre-flight).
- A `BackupWorker` retry that re-publishes the same `RestoreFailure` after
  WorkManager re-enqueues the job.

The implementation:

1. Inside `dataStore.edit { prefs -> ... }`, reads the variant's primary
   flag (`prefs[AppDialogKeys.pending<Name>Flag]`).
2. If the flag is already `true` → returns without modifying any key in
   the same `edit` block.
3. If the flag is `false` → writes the primary flag and every metadata
   key in the same `edit` block (atomic write-set).

The trade-off this locks in: if two different producers publish the same
variant with different payloads, the **first** payload wins and the
second is silently dropped. This is intentional. The realistic failure
mode for repeat publishes is "same upstream cause, same payload"; the
alternative (overwrite-on-write) would let a second call mutate the
dialog body under a user mid-read, which is worse UX than dropping the
duplicate.

Dedup is **per-variant**, not global. `RestoreFailure` already pending
does **not** block a `RestoreSuccess` publish; only the same variant's
primary flag short-circuits. Across-variant prioritization is handled by
the [priority ordering](#priority-ordering) read path, not by publish.

`AppDialogPublisher` is a `@Singleton`; the impl binding inside
`feature/app-dialogs/impl` simply re-exposes `AppDialogStore` as a
`Publisher` via `@Binds` so producer and consumer share the same DataStore
writer.

## AppDialogHost mounting

```kotlin
// app/app/.../App.kt — mounting site
@Composable
fun App() {
    AppTheme(themeMode = themeMode) {
        val navController = rememberNavController()
        val holder = remember(navController) { NavigatorHolder(navController) }
        NavigationEventBusSetup(holder, viewModel.navigatorEventBus)

        // Above NavHost so it renders on every destination
        AppDialogHost()

        NavHost(navController = navController, /* ... */)
    }
}
```

The host:

- Reads `currentDialog: Flow<AppDialog?>` via `collectAsStateWithLifecycle`.
- When `null`, composes nothing — there is no scrim, no placeholder.
- When non-null, dispatches on the variant and renders the appropriate
  Composable. Variants with confirm/dismiss semantics delegate to
  `AppConfirmationDialog` from `core/ui/kit`; variants with action lists
  (e.g. a future `RestoreFailure` that wants Report / Export / OK) render
  their own Composable but share `AppDialog`'s chrome.
- Holds **no state of its own**. Every render decision is derived from
  `currentDialog`. Dismiss buttons call `AppDialogStore.dismiss(variantId)`
  which clears the matching flag set.

Mounting **above** `NavHost` (not inside a route) means:

- The dialog appears regardless of destination — useful for backup-recovery's
  "restart, then show dialog on whatever the user lands on".
- The dialog survives navigation — a user tapping a bottom-bar tab while the
  dialog is open does not dismiss it. Only the dismiss gestures listed in
  the [Dismiss policy](#dismiss-policy) clear the flag.
- Recomposition of `NavHost` (route changes, config changes) does not affect
  the host — it reads its own `Flow` and composes independently.

## AppConfirmationDialog (generic)

```kotlin
// core/ui/kit/.../components/AppConfirmationDialog.kt
@Composable
fun AppConfirmationDialog(
    title: String,
    body: String,
    confirmLabel: String,
    dismissLabel: String? = null,           // null → single-action OK dialog
    isDestructive: Boolean = false,         // confirm button rendered in error container colors
    properties: DialogProperties = DialogProperties(),
    onConfirm: () -> Unit,
    onDismiss: () -> Unit = onConfirm,
)
```

Used by `AppDialogHost` to render every catalog variant that follows the
standard "title + body + 1-2 buttons" shape. Variants needing a custom
layout (e.g. a future `RestoreFailure` with Report / Export / OK buttons)
provide their own Composable but reuse `AppConfirmationDialog`'s scrim,
container shape, and typography tokens.

`AppConfirmationDialog` is also the migration target for the existing
feature-local dialogs (`SignOutConfirmationDialog`,
`RestoreConfirmationDialog` in `feature/settings`). That migration is
deferred to a follow-up PR — see
[tech-debt.md](../tech-debt.md).

## DI

| Class | Scope | Notes |
|---|---|---|
| `AppDialogStore[Impl]` | `@Singleton` (matches `Store` suffix in [`HiltScopeRule`](../lint-rules.md#hiltscoperule)). | DataStore-backed; one writer of every `pending_*` key. |
| `AppDialogPublisher` | `@Singleton` (`@Binds` from `AppDialogStoreImpl`). | Producer-side interface. |
| `AppDialogHost` | Stateless Composable. | No DI; reads `AppDialogStore` via `hiltViewModel` / `@Inject` in a thin VM if needed. |

The `Store` suffix on `AppDialogStore` matches the existing `HiltScopeRule`
predicate (`Store`) and is intentionally `@Singleton` rather than the more
common `@ViewModelScoped`. This is the only `Store` in the codebase that
must live at application scope; the rule allows it because the impl module
declares `@Singleton` explicitly and the class is not a feature MVI store.
The rule documentation in [lint-rules.md](../lint-rules.md) will be updated
when the impl lands.

## Cross-references

- [backup-recovery.md](backup-recovery.md) — primary consumer of the initial
  AppDialog catalog. Documents which producer raises which variant under
  which conditions.
- [`.claude/skills/app-dialogs-pattern.md`](../../.claude/skills/app-dialogs-pattern.md)
  — the procedural recipe for adding a new variant.
- [`.claude/skills/mvi-dialog-state.md`](../../.claude/skills/mvi-dialog-state.md)
  — feature-local dialog state, the other half of the dialog-state surface.
  Use feature-local `DialogState` for screen-scoped modals; use App Dialogs
  for process-survival or destination-independent modals.
- [`.claude/skills/compose-state-discipline.md`](../../.claude/skills/compose-state-discipline.md)
  → Rule 4 (dialogs and bottom sheets are State, not Events). App Dialogs
  is the cross-feature counterpart of that rule: the *state* lives in
  DataStore, the *trigger* is `publish(dialog)`, and `Event` is never
  involved.
- [tech-debt.md](../tech-debt.md) — entry for migrating existing
  feature-local confirmation dialogs to `AppConfirmationDialog`.
