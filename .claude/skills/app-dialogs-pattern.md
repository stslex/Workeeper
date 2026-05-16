---
name: app-dialogs-pattern
description: Add a new variant to the cross-feature `AppDialog` catalog. Covers the seven mechanical steps (sealed variant, DataStore keys, priority slot, render branch, dismiss policy, strings, catalog table). Use when extending the catalog defined in `documentation/feature-specs/app-dialogs.md` — not for feature-local screen-scoped dialogs (those use `mvi-dialog-state.md`).
---

# App Dialogs pattern

Procedural recipe for adding a new variant to the `AppDialog` catalog
introduced by [`documentation/feature-specs/app-dialogs.md`](../../documentation/feature-specs/app-dialogs.md).

The App Dialogs surface is the cross-feature, process-survival,
destination-independent dialog mechanism backed by DataStore Preferences.
It is **not** the screen-scoped per-feature dialog state covered by
[`mvi-dialog-state.md`](mvi-dialog-state.md).

## When to use

- Adding a new dialog that must (a) survive a process restart, (b) appear
  regardless of navigation destination, or (c) be produced by feature A and
  rendered without feature A owning UI code for it.
- Examples of fits: post-restart confirmations, app-update-available prompts,
  account-warning acknowledgements, legal-disclosure modals.
- Examples of **not** fits: per-screen confirm-delete confirmations, picker
  bottom sheets tied to one screen, transient progress overlays — those
  belong on the feature's `Store.State.dialogState` (see
  [`mvi-dialog-state.md`](mvi-dialog-state.md)).

## When NOT to use

- Anything dismissible by tapping outside without consequence — use a
  snackbar via `SnackbarManager` instead.
- Dialogs that need a return value piped to a specific feature handler. The
  App Dialogs surface has no callback channel; user actions either clear
  flags or set sibling flags. If you need a typed return into feature A,
  feature A should own the dialog and use the screen-scoped pattern.

## The eight steps

Adding a new variant is mechanical. The catalog table in
`app-dialogs.md` and the priority list update in the same PR — do not split.

The layering is:

- **data/** — `AppDialogRepository[Impl]` (DataStore writes/reads) +
  `AppDialogKeys`.
- **domain/** — `AppDialogResolver` (pure priority walk).
- **mvi/store/** — `AppDialogStore[Impl]` (`@HiltViewModel BaseStore`) +
  `AppDialogHandlerStore[Impl]`.
- **mvi/handler/** — `Observe`/`Publish`/`Dismiss`/`UserActionHandler`
  (`@ViewModelScoped`).
- **ui/** — one Composable file per variant; `AppDialogHost` +
  `AppDialogHostContent` dispatch the right render branch.
- **publisher/** — `AppDialogPublisherImpl` (`@Singleton` facade over
  Repository).
- **observer/** — `AppDialogObserverImpl` (`@Singleton`) for cross-feature
  user-choice reaction.

Adding a variant touches data + domain + mvi (handlers branch on the new
flag) + ui (new Composable file) + the api (if you add a new user action).

### 1. Define the sealed variant

In `feature/app-dialogs/api/.../model/AppDialog.kt`:

```kotlin
sealed interface AppDialog {
    val id: String

    // existing variants ...

    data class <Name>(
        val <metadataField1>: <Type1>,
        val <metadataField2>: <Type2>,
    ) : AppDialog {
        override val id: String = "<lower_snake_case_id>"
    }
}
```

- The `id` constant feeds telemetry, diagnostics, and dismiss-by-id calls. Use
  lower_snake_case matching the DataStore key prefix.
- Use `data class` if the variant carries metadata; `data object` if it does
  not. Single-button OK dialogs are usually `data object` (e.g.
  `UndoRestoreSuccess`).
- Keep metadata fields primitive / serializable. They must round-trip through
  DataStore Preferences without a custom Serializer.

### 2. Define DataStore Preference keys

In `feature/app-dialogs/impl/.../data/AppDialogKeys.kt`:

```kotlin
internal object AppDialogKeys {
    // existing keys ...

    val pendingNameFlag = booleanPreferencesKey("pending_<name>")
    val pendingNameField1 = <typed>PreferencesKey("pending_<name>_<field1>")
    val pendingNameField2 = <typed>PreferencesKey("pending_<name>_<field2>")
}
```

Convention: `pending_<dialog_id>` for the primary boolean flag,
`pending_<dialog_id>_<field>` for each metadata field. Use the typed
preferences key matching the field type
(`booleanPreferencesKey` / `longPreferencesKey` / `stringPreferencesKey` /
`intPreferencesKey`).

### 3. Insert into the priority list

In `feature/app-dialogs/impl/.../domain/AppDialogResolver.kt`'s priority
walk, add the new variant **at the right priority level**. Also add the
write-flags / clear-flags / is-already-pending branches in
`AppDialogRepositoryImpl`. The current order (from spec):

1. `RestoreFailure` — critical
2. `RestoreSuccess` — informational
3. `UndoRestoreSuccess` — informational
4. `UndoRestoreConfirmation` — user-initiated

Pick the slot from this guideline:

| Slot | Use for |
|---|---|
| Top (critical / failure) | Anything the user must acknowledge before continuing safely. |
| Middle (informational) | Positive completion of an operation the user initiated earlier (success acks). |
| Bottom (user-initiated) | Confirmations the user explicitly opened a flow to reach. |

The shape of the resolution is a fixed `when` chain over flag reads — do
**not** sort by an enum ordinal or by a `priority: Int` field on the
variant. The literal chain is the same line count and surfaces the choice in
code review.

### 4. Add a per-variant Composable file + render branch

Create `feature/app-dialogs/impl/.../ui/<Name>Dialog.kt`:

```kotlin
@Composable
internal fun <Name>Dialog(
    dialog: AppDialog.<Name>,
    dispatch: (AppDialogStore.Action) -> Unit,
) {
    AppConfirmationDialog(
        title = stringResource(R.string.dialog_<name>_title),
        body = stringResource(R.string.dialog_<name>_body, dialog.<field>),
        confirmLabel = stringResource(R.string.dialog_<name>_ok),
        dismissLabel = null,  // or a label if the dialog has two buttons
        isDestructive = false,
        properties = DialogProperties(
            dismissOnBackPress = <true|false>,
            dismissOnClickOutside = false,  // always false in this catalog
        ),
        onConfirm = {
            dispatch(AppDialogStore.Action.UserAction(dialog, AppDialogUserAction.Acknowledge))
        },
        onDismiss = {
            dispatch(AppDialogStore.Action.Dismiss(dialog))
        },
    )
}
```

Add a `@Preview` per visual state (Light/Dark, payload variations) in the
same file — follow the `RestoreSuccessDialog` /
`UndoRestoreConfirmationDialog` precedent.

Then add the dispatch branch in
`feature/app-dialogs/impl/.../ui/AppDialogHostContent.kt`:

```kotlin
when (val current = state.current) {
    null -> Unit
    // existing branches ...
    is AppDialog.<Name> -> <Name>Dialog(dialog = current, dispatch = dispatch)
}
```

The dispatch lambda is `(AppDialogStore.Action) -> Unit`. The
Composable does **not** receive typed callbacks like `onUndoRequested` —
every user gesture goes through `Action.UserAction(dialog, action)` (or
`Action.Dismiss(dialog)` for implicit back-press dismiss). Variants with
non-standard chrome (e.g. three action buttons in a column, icon + body)
own their own Composable but still dispatch via `Action.UserAction`.

### 4.5. Add new `AppDialogUserAction` variants if needed

If the new variant introduces a button shape not covered by the existing
`Acknowledge` / `RequestUndo` / `ConfirmUndo` / `Cancel` / `Report` /
`ExportDiagnostics` enum entries, add one in
`feature/app-dialogs/api/.../model/AppDialogUserAction.kt`. The consuming
feature (the one that reacts to the user's choice) then observes
`AppDialogObserver.observeUserActions()` and adds a `when`-branch on the
new `AppDialogUserChoice(dialog, action)` shape. The consumer's `@Singleton`
handler is the reactor; the host stays generic.

### 5. Declare the dismiss policy

Add a row to the [Dismiss policy](../../documentation/feature-specs/app-dialogs.md#dismiss-policy)
table in `app-dialogs.md` with the variant's gestures. The columns to fill:

- **Back press** — `clears flag` or `blocked`.
- **Click outside** — almost always `blocked` (the host always passes
  `dismissOnClickOutside = false`).
- **Action button** — what the visible buttons clear.
- **Implicit dismiss** — natural-language summary ("back = OK", "must tap
  OK", etc.).

If back-press is blocked, the variant must have an explicit confirm button
on the dialog itself — the user cannot become stuck. Failure-level dialogs
typically block back; informational dialogs typically allow back.

### 6. Add string resources

Add EN + RU entries for every visible string in the variant. Naming
convention:

- `dialog_<dialog_id>_title`
- `dialog_<dialog_id>_body`  (with positional / named arguments for
  metadata interpolation)
- `dialog_<dialog_id>_<button>_action` (one per button label)

Files:

- `feature/app-dialogs/impl/src/main/res/values/strings.xml` (EN)
- `feature/app-dialogs/impl/src/main/res/values-ru/strings.xml` (RU)

If RU wording is uncertain, mark "RU pending" in the PR description and
file a follow-up; do not block the PR on translation polish.

### 7. Update the catalog in `app-dialogs.md`

Add the variant to the [AppDialog catalog](../../documentation/feature-specs/app-dialogs.md#appdialog-catalog-initial)
section's code block and to the [DataStore keys](../../documentation/feature-specs/app-dialogs.md#datastore-keys-per-dialog-type)
table. Cross-link the producing feature spec.

## Single source of truth — the invariant

Two stacked sources of truth:

- **Persistence truth.** Every pending dialog lives in DataStore Preferences.
  The only writer is `AppDialogRepositoryImpl`. There are exactly three
  write entry points, each inside its own `edit { … }` block:
  - `publish(dialog)` — translates the variant into a flag set.
  - `dismiss(dialog)` — clears the flag set.
  - `recordUserChoice(choice)` — appends the user's action to a transient
    record consumed by `AppDialogObserver`.
- **Runtime truth.** `AppDialogStore.State.current` is the Activity-scoped
  projection of the repository flow. The Host renders `state.value`, not
  DataStore directly.

If you find yourself adding a `MutableStateFlow<AppDialog?>` inside the
Store, you have left the pattern — go back and rewrite. The Store derives
its State from the repository via `Action.Observe`; there is no parallel
in-memory queue.

The reason this is load-bearing:

- Backup recovery's primary case is "show this dialog after a process
  restart". An in-memory mutation is wiped by the process kill; the
  DataStore record survives, the Store re-projects on re-launch.
- One writer means audit trails are simple — `git grep "edit\\s*{"`
  inside `data/` finds every flag write in one place.
- Reactive view means no manual "tell the UI to re-render" call from the
  Store. The Repository flow does it.
- Cross-feature consumers (`feature/recovery`, future features) read
  `AppDialogObserver.observeUserActions()` — backed by the same repository
  record, NOT by the Activity-scoped Store. A `@Singleton` cannot inject
  an Activity-scoped `@HiltViewModel`, so the observer is the right scope-
  matching surface.

## Host mount-site invariant

`AppDialogHost` must be composed **as a sibling of** `NavHost` in `App.kt`,
not inside any NavHost destination. The Store is resolved via
`AppDialogFeature.processor()` (a screen-less `AppFeature` in
`core/ui/mvi`) which calls `rememberStoreProcessor<AppDialogStoreImpl>()`
→ `hiltViewModel<AppDialogStoreImpl>()`. `LocalViewModelStoreOwner` at the
sibling-of-NavHost depth resolves to the host `ComponentActivity`, which
scopes the Store to the Activity (NOT to a `NavBackStackEntry`).

Moving the `AppDialogHost()` call inside `NavHost` silently rescopes the
Store to the current destination — no compile error, behaviour breaks at
runtime (a dialog published from another destination would not be picked
up by the destination-scoped Store). The mount site carries an in-code
comment explaining this; add a similar comment if you introduce another
app-root mount.

## Where to read next

- [`documentation/feature-specs/app-dialogs.md`](../../documentation/feature-specs/app-dialogs.md)
  — full architecture, dismiss policy table, module layout, DI scope notes.
- [`documentation/feature-specs/backup-recovery.md`](../../documentation/feature-specs/backup-recovery.md)
  — the first consumer of the catalog. Worked example of every step above
  for the four initial variants.
- [`mvi-dialog-state.md`](mvi-dialog-state.md) — the other dialog pattern;
  use it for screen-scoped, in-memory, recomposition-only modals.
- [`compose-state-discipline.md → Rule 4`](compose-state-discipline.md) —
  the project-wide "dialogs are State, not Events" rule. App Dialogs is the
  cross-feature counterpart: State lives in DataStore.
