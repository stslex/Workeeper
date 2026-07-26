# Feature spec — App Dialogs

**Status:** Implemented as a layered MVI feature (data / domain / presentation)
mounted at the App root as a sibling of `NavHost`. The catalog backs the
backup-recovery work (see [backup-recovery.md](backup-recovery.md)); future
use cases (app-update available, account warnings, license expiry, etc.) plug
into the same catalog without new infrastructure.

App Dialogs is the cross-feature surface for showing modal confirmations or
acknowledgements that must (a) survive process restart, (b) appear regardless
of which navigation destination the user is on, and (c) be authored
independently by the feature that produces them without coupling to the
feature module that renders them.

It is **deliberately distinct** from the existing per-feature `DialogState`
pattern (see [`.claude/skills/mvi-dialog-state.md`](../../.claude/skills/mvi-dialog-state.md)
and [`compose-state-discipline.md → Rule 4`](../../.claude/skills/compose-state-discipline.md)).
Per-feature dialog state is in-memory, screen-scoped, and dismissed when the
user navigates away. App Dialogs are persisted in DataStore, projected through
an unscoped Metro Store that is Activity-scoped by virtue of being obtained at
the App root, and visible from any destination until the user explicitly
acknowledges them.

## Status

- Shipped. The four initial variants (`RestoreSuccess`, `RestoreFailure`,
  `UndoRestoreConfirmation`, `UndoRestoreSuccess`) support
  [backup-recovery.md](backup-recovery.md)'s post-restart flows.
- Future variants (app-update available, account warnings) added in their
  own PRs with a one-line catalog entry + a render branch — see
  [`.claude/skills/app-dialogs-pattern.md`](../../.claude/skills/app-dialogs-pattern.md).

## Scope

- Cross-feature dialog catalog (`AppDialog` sealed interface).
- Three-layer presentation:
  - **Data** — `AppDialogRepository` (`@SingleIn(AppScope)`) owns DataStore persistence
    (the `pending_*` flag set per variant).
  - **Domain** — `AppDialogResolver` is a pure function that walks the
    persisted flags in fixed priority order and emits the current
    `AppDialog?`.
  - **Presentation** — `AppDialogStore` (unscoped Metro `BaseStore<State,
    Action, Event>`) projects the repository flow into UI state and accepts
    Actions from the Host and from producer features.
- `AppDialogHost` Composable mounted at app root **as a sibling of**
  `NavHost`. Its `LocalViewModelStoreOwner` is the host `ComponentActivity`
  so the Store is Activity-scoped (NOT destination-scoped, NOT
  `@SingleIn(AppScope)`).
- Generic `AppConfirmationDialog` Composable in `core/ui/kit` so per-variant
  Composables don't author dialog chrome.
- `AppDialogPublisher` (`@SingleIn(AppScope)`) — producer-side facade that writes
  through `AppDialogRepository`. Producers depend only on the api module.
- `AppDialogObserver` (`@SingleIn(AppScope)`) — cross-feature observation surface.
  Exposes `observeUserActions(): Flow<AppDialogUserChoice>` for consumers
  (e.g. `feature/recovery`) that need to react to the user's dialog choice
  without taking a dependency on the impl module's Store. The observer is
  backed by the repository's persistence flow, NOT by the Activity-scoped
  Store (a `@SingleIn(AppScope)` cannot inject an Activity-scoped `ViewModel`).

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
- Direct callbacks from dialog action buttons into the producing feature.
  The host dispatches `Action.Choose(dialog, choice)` to the Store; the
  Store records the choice in the persisted flag set and the producing
  feature's own `@SingleIn(AppScope)` handler reacts by observing
  `AppDialogObserver.observeUserActions()`. There is no typed callback
  channel from the host into a producer — that would re-introduce the
  coupling this mechanism exists to avoid (see backup-recovery's earlier
  `AppDialogActions` interface — removed).

## Module layout

| Module | Purpose |
|---|---|
| `feature/app-dialogs/api` | Provider-neutral contracts: `AppDialog` sealed types, `AppDialogUserAction` (enum of choices the user can tap on any variant), `AppDialogUserChoice` (data class pairing a variant with the action the user picked), `AppDialogPublisher`, `AppDialogObserver`. Producer features depend on this module only. |
| `feature/app-dialogs/impl` | Layered MVI implementation. **data/** — `AppDialogRepository[Impl]` + `AppDialogKeys`. **domain/** — `AppDialogResolver`. **mvi/store/** — `AppDialogStore[Impl]` (unscoped Metro Store) + `AppDialogHandlerStore[Impl]`. **mvi/handler/** — `AppDialogRepoHandler` (`Action.RepoAction` sub-tree: Observe/Publish/Dismiss) + `ChooseHandler` (`Action.Choose`). **ui/** — `AppDialogHost`, `AppDialogHostContent`, one Composable file per variant. **publisher/** — `AppDialogPublisherImpl` (`@SingleIn(AppScope)` facade over the repository). **observer/** — `AppDialogObserverImpl` (`@SingleIn(AppScope)` over the repository). Bound at the app graph; consumer features do not depend on impl. |
| `core/ui/kit` | `AppConfirmationDialog` generic Composable. Per-variant composables in `feature/app-dialogs/impl/ui` delegate to it for consistent chrome. |

The api/impl split mirrors the convention used by `core/data/backup/*`: a
producer feature depends on `:feature:app-dialogs:api`; a consumer feature
that needs to react to user choices also depends on `:feature:app-dialogs:api`
(for `AppDialogObserver`); only `app/app` wires `:feature:app-dialogs:impl`
so the Store, Host, Repository, and observer impls are reachable from the
DI graph.

## Single source of truth

There are two stacked sources of truth and the layering matters:

1. **Persistence truth.** Every pending dialog lives **exclusively** in
   DataStore Preferences. There is no in-memory queue, no
   `MutableStateFlow<List<AppDialog>>` outside the Store's projection, and no
   caller-side write to an in-memory field. The one writer of the
   `pending_*` keys is `AppDialogRepositoryImpl`; producers call
   `AppDialogPublisher.publish(...)` which delegates to the repository.
2. **Runtime truth.** `AppDialogStore.State.currentDialog` is the
   application's runtime source of truth — a thin projection of
   `AppDialogResolver(repository.observe())`. The Host renders State; it
   does not read DataStore directly.

The reasoning is:

- **Process-restart survival.** Backup recovery's primary failure mode is
  "Room migration crashes after a Restore". The process dies, the activity
  is recreated, the Store is re-instantiated by Metro against the host
  Activity's `ViewModelStore`, the Repository replays from DataStore, and
  the user sees the `RestoreFailure` dialog. An in-memory queue would be
  wiped; a DataStore-backed flag survives.
- **One writer.** `AppDialogRepositoryImpl` is the only writer for
  `pending_*` keys. Dismiss is the only consumer-driven write; it goes
  through the repository via `Action.RepoAction.Dismiss` → `AppDialogRepoHandler`.
- **Reactive view.** `repository.observe(): Flow<AppDialog?>` combines flag
  reads through `AppDialogResolver` and emits the single highest-priority
  variant. `AppDialogStore` collects this flow inside `Action.RepoAction.Observe` and
  updates `State`. Setting any flag causes the flow to re-emit; clearing
  all flags causes it to emit `null` and the Host composes nothing.

Dismiss = clear flags in DataStore → repository flow re-emits → Store updates
State → Host re-renders without the dialog (or with the next-priority dialog
if multiple are pending).

## Layering — data / domain / presentation

```
+--------------------------------------------------------------------+
| Presentation (Activity-scoped via App() root, unscoped Metro Store)|
|                                                                    |
|   AppDialogStore : Store<State, Action, Event>                     |
|       State.current  : AppDialog?                                  |
|       State.lastUserChoice : AppDialogUserChoice?  (transient)     |
|     Handlers (@SingleIn(AppDialogScope)):                          |
|       AppDialogRepoHandler — Action.RepoAction sub-tree            |
|         Observe  → repository.currentDialog → State.current        |
|         Publish  → repository.publish (currently no dispatcher)    |
|         Dismiss  → repository.dismiss (currently no dispatcher)    |
|       ChooseHandler — Action.Choose(dialog, action)                |
|             → emits choice to observer, does NOT dismiss           |
+--------------------------------------------------------------------+
                              ▲                ▲
                              │                │
+--------------------------------------------------------------------+
| Domain (pure)                                                      |
|                                                                    |
|   AppDialogResolver(Preferences) : AppDialog?                      |
|     Walks priority list (RestoreFailure → RestoreSuccess →         |
|     UndoRestoreSuccess → UndoRestoreConfirmation) and returns the  |
|     highest-priority variant whose flag is set.                    |
+--------------------------------------------------------------------+
                              ▲
                              │
+--------------------------------------------------------------------+
| Data (@SingleIn(AppScope))                                         |
|                                                                    |
|   AppDialogRepository                                              |
|     observe(): Flow<AppDialog?>   — repository.data.map(resolver)  |
|     publish(dialog)                — atomic DataStore.edit write   |
|     dismiss(dialog)                — atomic DataStore.edit clear   |
|     recordUserChoice(choice)       — atomic edit, fed to observer  |
|                                                                    |
|   AppDialogObserver (@SingleIn(AppScope))                          |
|     observeUserActions(): Flow<AppDialogUserChoice>                |
|     — backed by repository, NOT by the Activity-scoped Store, so   |
|     other @SingleIn(AppScope)s (e.g. recovery's observer) can inject|
|     it without scope-mismatch.                                     |
+--------------------------------------------------------------------+
```

The two layers' separation is load-bearing for the
[`AppDialogObserver`](#cross-feature-observation) decision: a `@SingleIn(AppScope)`
in another feature cannot legally inject the Activity-scoped
Metro Store; the observer reads from the repository instead so
its scope matches its consumers'.

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
3. Add the (read flag → build variant) branch in `AppDialogResolver`'s
   priority walk and the (write flags / clear flags) branches in
   `AppDialogRepositoryImpl`.
4. Add a render branch in `AppDialogHostContent` + a per-variant Composable
   file in `feature/app-dialogs/impl/ui/<Name>Dialog.kt`.
5. Add any new `AppDialogUserAction` enum entries needed by the new
   variant's buttons; if a consumer feature reacts to them, that feature
   adds a `@SingleIn(AppScope)` handler observing `AppDialogObserver`.

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

When multiple flags are set simultaneously, `AppDialogResolver` returns the
**single** current dialog by walking a fixed priority list and returning the
first variant whose flag is set:

1. `RestoreFailure` — critical; the user must acknowledge that their restore
   failed and their data is intact.
2. `RestoreSuccess` — informational; positive confirmation of restore.
3. `UndoRestoreSuccess` — informational; positive confirmation of undo.
4. `UndoRestoreConfirmation` — user-initiated; least urgent because the user
   chose to enter this flow themselves.

Resolution is implemented in `AppDialogResolver` as a pure function
`(Preferences) -> AppDialog?`. The repository's `observe()` flow maps
`dataStore.data` through the resolver — one read per emission, followed by
an in-Kotlin priority walk. Tests assert that the read happens once per
emission, not once per priority level.

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

`AppDialogPublisherImpl` is a `@SingleIn(AppScope)` thin facade that delegates to
`AppDialogRepository.publish(dialog)` — that's where the atomic dedup write
lives. The split (Publisher = api, Repository = impl) lets producer features
depend on the api module without pulling DataStore.

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

`AppDialogPublisher` is `@SingleIn(AppScope)`; the impl binding inside
`feature/app-dialogs/impl` binds it to `AppDialogPublisherImpl`, which
delegates to the `@SingleIn(AppScope)` `AppDialogRepository`. Producer and consumer
share the same DataStore writer.

## Cross-feature observation

Consumer features that need to react to the user's dialog choice (e.g.
`feature/recovery`'s `RestoreDialogChoiceObserver`, which handles undo /
export / report inline) observe an `AppDialogObserver` interface:

```kotlin
// feature/app-dialogs/api/.../observer/AppDialogObserver.kt
interface AppDialogObserver {

    /**
     * Stream of user-action choices emitted by `ChooseHandler`. Each
     * emission carries the variant the user was looking at and the action
     * they tapped. Backed by a DataStore-persisted "last choice" record
     * that the host clears after the consumer has had a chance to react,
     * so re-subscribing observers do not replay stale choices.
     */
    fun observeUserActions(): Flow<AppDialogUserChoice>
}
```

`AppDialogUserChoice(dialog: AppDialog, action: AppDialogUserAction)` is a
data class in the api module. `AppDialogUserAction` enumerates the buttons
that any variant can present:

| Variant | Buttons → action emitted |
|---|---|
| `RestoreSuccess` | OK → `Acknowledge`; "Undo restore" → `RequestUndo` |
| `RestoreFailure` | OK → `Acknowledge`; "Report issue" → `Report`; "Export diagnostics" → `ExportDiagnostics` |
| `UndoRestoreConfirmation` | Confirm → `ConfirmUndo`; Cancel → `Cancel` |
| `UndoRestoreSuccess` | OK → `Acknowledge` |

`AppDialogObserverImpl` is `@SingleIn(AppScope)` and is **backed by the repository**,
not by the Activity-scoped Store. The rationale is scope: a `@SingleIn(AppScope)`
in another feature (`feature/recovery`'s `RestoreDialogChoiceObserver`, for example) cannot
inject the Activity-scoped Metro Store. The observer reads from
the repository's persisted user-choice record, which is the same source
the Store's `ChooseHandler` writes to. Single source of truth holds.

Consumers (one per producing feature) inject `AppDialogObserver` and
launch a long-lived collector. They are responsible for branching on
`AppDialogUserChoice.dialog` (e.g. only act when it's `RestoreSuccess`)
and for invoking the correct downstream side-effects (typically through
their own NavigationHandler, never through free Compose functions).

## AppDialogHost mounting

```kotlin
// app/app/.../App.kt — mounting site
@Composable
fun App() {
    AppTheme(themeMode = themeMode) {
        Box(modifier = Modifier.fillMaxSize()) {

            AppNavigationHost(/* NavHost is mounted inside this */)

            // ▼ MUST stay a SIBLING of AppNavigationHost (the NavHost), never
            // inside any destination. LocalViewModelStoreOwner at this depth
            // resolves to the host ComponentActivity, which is what scopes
            // AppDialogStore to the Activity (not to a NavBackStackEntry).
            // Moving this call inside NavHost silently rescopes the Store to
            // a destination — no compile error, behaviour breaks at runtime.
            AppDialogHost()
        }
    }
}
```

The host:

- Resolves `AppDialogStore` via the screen-less composition entry
  `AppDialogFeature.processor()` (a single-instance object that delegates
  to `rememberMetroStoreProcessor<AppDialogStoreImpl>()`). Because the surrounding
  `LocalViewModelStoreOwner` is the host `ComponentActivity` (App root is a
  sibling of NavHost), the resulting Store is **Activity-scoped** — same
  lifetime as the Activity's `ViewModelStore`, NOT the current navigation
  destination, NOT a `@SingleIn(AppScope)`. It survives configuration changes and is
  reused across all destinations.

  `AppDialogFeature` is an `AppFeature<TProcessor>` — a screen-less twin of
  the existing `Feature` / `FeatureAssisted` composition-entry hierarchy in
  `core/ui/mvi`. The screen-less variant exists for app-root mounts; it
  must not be used inside `NavHost` (where `Feature` is the right entry).
  This is the minimal new MVI-composition entry required for the refactor;
  the underlying scope/owner mechanism (`rememberMetroStoreProcessor<T>()` →
  `LocalViewModelStoreOwner`) is reused unchanged from the
  existing `AppRootViewModel` Activity-scoped VM (see
  [`App.kt:63`](../../app/app/src/main/java/io/github/stslex/workeeper/App.kt)).
- Dispatches `Action.RepoAction.Observe` once on mount to subscribe to the
  repository flow and project it into State.
- Reads `state.value.current: AppDialog?`. When `null`, composes nothing —
  there is no scrim, no placeholder. When non-null, dispatches on the
  variant and renders the appropriate Composable file
  (`ui/<Name>Dialog.kt`). Buttons dispatch `Action.Choose(dialog,
  action)` to the Store; the `ChooseHandler` emits the choice to the
  observer; the consumer-side reactor dismisses after its side-effect.
- Holds **no state of its own**. Every render decision is derived from
  `state.value`. No `EntryPointAccessors`. No free `scope.launch { … }`
  with embedded logic. No typed callbacks into producer features.

Mounting as a **sibling of** `NavHost` means:

- The dialog appears regardless of destination — useful for backup-recovery's
  "restart, then show dialog on whatever the user lands on".
- The dialog survives navigation — a user tapping a bottom-bar tab while the
  dialog is open does not dismiss it. Only the dismiss gestures listed in
  the [Dismiss policy](#dismiss-policy) clear the flag.
- Recomposition of `NavHost` (route changes, config changes) does not affect
  the host — it reads its own State and composes independently.
- The Store is **not** re-instantiated per destination. Inside NavHost,
  `LocalViewModelStoreOwner` is the current `NavBackStackEntry`, which
  would rescope the Store to the destination and lose its app-wide
  semantics. This is the load-bearing invariant — see the code comment at
  the mount site.

## Action surface

```kotlin
// feature/app-dialogs/impl/.../mvi/store/AppDialogStore.kt
internal interface AppDialogStore : Store<State, Action, Event> {

    data class State(
        /** Currently-displayed dialog, or null when none is pending. */
        val current: AppDialog?,
    ) : Store.State {
        companion object { val EMPTY = State(current = null) }
    }

    sealed interface Action : Store.Action {

        /** Subscribe to the repository flow; dispatched as an initialAction. */
        data object Observe : Action

        /** Producer-driven publish (used by AppDialogPublisherImpl facade). */
        data class Publish(val dialog: AppDialog) : Action

        /** User tapped a button on `dialog`; records the choice + dismisses. */
        data class UserAction(
            val dialog: AppDialog,
            val action: AppDialogUserAction,
        ) : Action

        /** Implicit dismiss (back press where the policy allows it). */
        data class Dismiss(val dialog: AppDialog) : Action
    }

    /** No events in v1 — every user-visible outcome flows through State. */
    sealed interface Event : Store.Event
}
```

Handler routing (`AppDialogStoreImpl.handlerCreator`):

| Action | Handler |
|---|---|
| `is Action.RepoAction` | `AppDialogRepoHandler` (`@SingleIn(AppDialogScope)`) |
| `is Action.Choose` | `ChooseHandler` (`@SingleIn(AppDialogScope)`) |

Per-feature reaction to user choices is **NOT** in the Store's handler
graph — it lives in the consuming feature's own `@SingleIn(AppScope)` handler that
observes `AppDialogObserver.observeUserActions()`. The Store records the
choice; the consumer reacts to it.

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
| `AppDialogRepository[Impl]` | `@SingleIn(AppScope)` | Single writer of `pending_*` keys. Owns the `DataStore<Preferences>` instance. |
| `AppDialogResolver` | pure (no `@Inject`) | Static priority walk. No state. |
| `AppDialogStore` interface / `AppDialogStoreImpl` | unscoped (class-level `@Inject`) | Activity-scoped via `rememberMetroStoreProcessor<AppDialogStoreImpl>()` at App root; retained by the Activity `ViewModelStore`. A Metro Store carries no scope annotation — [`MetroScopeRule`](../lint-rules.md#metroscoperule) does not require one. **No carve-out** — this is a regular MVI Store. |
| `AppDialogHandlerStoreImpl` | `@SingleIn(AppDialogScope)` | Standard `BaseHandlerStore` bridge. |
| `AppDialogRepoHandler` / `ChooseHandler` | `@SingleIn(AppDialogScope)` | Handler-suffix → feature-scoped `@SingleIn(<Feature>Scope)` per `MetroScopeRule` (a `*Handler` must not be `@SingleIn(AppScope)`). |
| `AppDialogPublisher` / `AppDialogPublisherImpl` | `@SingleIn(AppScope)` (`@ContributesBinding`) | Thin facade over the repository. |
| `AppDialogObserver` / `AppDialogObserverImpl` | `@SingleIn(AppScope)` (`@ContributesBinding`) | Cross-feature observation surface, backed by the repository. |
| `AppDialogHost` | Stateless Composable | Resolves the Store via `rememberMetroStoreProcessor<AppDialogStoreImpl>()`. No `EntryPointAccessors`. |

A Metro Store such as `AppDialogStoreImpl` carries no scope annotation at all.
`MetroScopeRule` does inspect its class-level `@Inject`, but exempts it by name
via `ScopedClassNames.isStoreImpl` before any scope check. Activity-scoping is
achieved at the **mount site** (App root, sibling of NavHost) by
`LocalViewModelStoreOwner` resolving to the host `ComponentActivity`, not by a
DI scope. Note the sibling `AppDialogHandlerStoreImpl` is **not** covered by
that exemption (`isStoreImpl` excludes `*HandlerStoreImpl`), which is why it
carries `@SingleIn(AppDialogsScope::class)`.

This replaces the historical `AppDialogStore` carve-out from the deleted
`ScopeClassType.singletonClasses` list (now `ScopedClassNames`) — see
[lint-rules.md → MetroScopeRule](../lint-rules.md#metroscoperule) for the
deletion rationale. The previous design (a `@Singleton` DataStore wrapper
misnamed `*Store`) is gone; the persistence-only role moved to
`AppDialogRepository`, leaving `AppDialogStore` as a genuine MVI Store
that does not need an exception.

## Cross-references

- [backup-recovery.md](backup-recovery.md) — primary consumer of the initial
  AppDialog catalog. Documents which producer raises which variant under
  which conditions, and which `feature/recovery` observer reacts to each
  user choice via `AppDialogObserver`.
- [`.claude/skills/app-dialogs-pattern.md`](../../.claude/skills/app-dialogs-pattern.md)
  — the procedural recipe for adding a new variant.
- [`.claude/skills/mvi-dialog-state.md`](../../.claude/skills/mvi-dialog-state.md)
  — feature-local dialog state, the other half of the dialog-state surface.
  Use feature-local `DialogState` for screen-scoped modals; use App Dialogs
  for process-survival or destination-independent modals.
- [`.claude/skills/compose-state-discipline.md`](../../.claude/skills/compose-state-discipline.md)
  → Rule 4 (dialogs and bottom sheets are State, not Events). App Dialogs
  is the cross-feature counterpart: the *persistence truth* lives in
  DataStore, the *runtime truth* is the Activity-scoped Store's State,
  the *trigger* is `publish(dialog)`, the *cross-feature reaction* is
  `AppDialogObserver.observeUserActions()`. `Event` is never involved.
- [lint-rules.md → MetroScopeRule](../lint-rules.md#metroscoperule) — explains
  why `AppDialogStore` no longer needs the deleted `ScopeClassType.singletonClasses`
  carve-out after the refactor, and how the replacement `ScopedClassNames.isStoreImpl`
  exemption works.
- [tech-debt.md](../tech-debt.md) — entry for migrating existing
  feature-local confirmation dialogs to `AppConfirmationDialog`.
