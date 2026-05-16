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

## The seven steps

Adding a new variant is mechanical. The catalog table in
`app-dialogs.md` and the priority list update in the same PR — do not split.

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

In `feature/app-dialogs/impl/.../store/AppDialogKeys.kt`:

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

In `feature/app-dialogs/impl/.../store/AppDialogStoreImpl.kt`'s
`currentDialog` resolution, add the new variant **at the right priority
level**. The current order (from spec):

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

### 4. Add the render branch in `AppDialogHost`

In `feature/app-dialogs/impl/.../ui/AppDialogHost.kt`:

```kotlin
when (val dialog = currentDialog) {
    null -> Unit
    // existing branches ...
    is AppDialog.<Name> -> render<Name>(dialog)
}
```

The render function delegates to `AppConfirmationDialog` from `core/ui/kit`
whenever the variant follows the "title + body + 1-2 buttons" shape:

```kotlin
@Composable
private fun render<Name>(dialog: AppDialog.<Name>) {
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
        onConfirm = { store.dismiss(dialog.id) },
        onDismiss = { store.dismiss(dialog.id) },
    )
}
```

Variants with non-standard chrome (e.g. three action buttons in a column,
icon + body, etc.) own their own Composable but still call
`store.dismiss(dialog.id)` on any path that clears the flag.

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

**Never** mutate in-memory state to show or hide a dialog. The only writes
are:

- `AppDialogPublisher.publish(dialog)` → translates the variant into a flag
  set, writes the flag set in one `edit { ... }` block.
- `AppDialogStore.dismiss(variantId)` → clears the flag set for that
  variant in one `edit { ... }` block.

The Composable observes `currentDialog: Flow<AppDialog?>` via
`collectAsStateWithLifecycle`. Every render is a derived projection over
DataStore reads. If you find yourself adding a `MutableStateFlow<AppDialog?>`
inside the store impl, you have left the pattern — go back and rewrite.

The reason this is load-bearing:

- Backup recovery's primary case is "show this dialog after a process
  restart". An in-memory mutation is wiped by the process kill.
- One writer means audit trails are simple — `git grep edit\\s*{` finds every
  flag write in one place.
- Reactive view means no manual "tell the UI to re-render" call from the
  store impl. The flow does it.

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
