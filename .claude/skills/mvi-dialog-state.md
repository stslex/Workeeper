---
name: mvi-dialog-state
description: Model mutually-exclusive modal UI (dialogs, bottom sheets) as a single sealed `dialogState` field on the feature's `Store.State`, never as independent boolean/nullable per-dialog flags. Drills down on Rule 4 of `compose-state-discipline.md`: the **shape** of the sealed type, when one nullable is still acceptable, when the hard rule kicks in, naming, and verification. Use when authoring or reviewing any feature that needs to display more than one modal/dialog/bottom-sheet from the same screen.
---

# MVI dialog state

Drill-down companion to Rule 4 of [`compose-state-discipline`](compose-state-discipline.md)
(dialogs and sheets live in `State`, not `Event`). That rule says *where* dialog visibility
lives. This skill says *what shape* it takes when one screen has more than one possible
modal.

## When to use

- Authoring or reviewing a feature with **two or more** dialogs / bottom sheets / modal
  confirmations on the same screen.
- Adding a second dialog to a screen that already has one (the cut-over moment).
- A code-review comment cites "multiple Boolean flags on State for dialog visibility" /
  "independent nullable confirmation fields".
- Detekt is silent on this — the rule is enforced by skill + doc + tech-debt, not by a
  custom lint rule (intentionally; the shape is too pattern-driven to formalize without
  false positives).

## The hard rule

**Two or more dialogs on one screen ⇒ one sealed `dialogState: DialogState` field on
State.** Independent `*Visible: Boolean` flags and independent `*Confirmation: <X>?`
nullables are forbidden when there are two or more possible modals. They allow invalid
combinations at the type level (two dialogs open simultaneously), force defensive
branching in the UI, and let the wrong dismiss action quietly miss its target.

A screen with **exactly one** dialog *may* use a single nullable or a sealed
`DialogState` with `Hidden` + one variant — both are acceptable. Prefer sealed
`DialogState` if a second dialog is on the roadmap (forward-compatibility). The
moment a second dialog lands the sealed shape is mandatory.

## Wrong

```kotlin
// Two independent fields — what if both are true?
@Stable
data class State(
    /* ... */
    val restoreConfirmation: RestoreConfirmation? = null,
    val signOutConfirmationVisible: Boolean = false,
) : Store.State
```

UI must branch on each independently:

```kotlin
state.restoreConfirmation?.let { RestoreDialog(it) }
if (state.signOutConfirmationVisible) { SignOutDialog() }
```

Failure modes this enables:

- Both fields end up `true` / non-null at once. Two dialogs stack visually; the wrong
  one absorbs the dismiss tap.
- A handler that "opens" one dialog has to remember to "close" the other. Refactors
  drop one of the close-other calls and ship a regression.
- Tests assert on `state.signOutConfirmationVisible` and pass even when
  `state.restoreConfirmation` is *also* non-null in the same fixture.

## Right

Single sealed `DialogState` in `feature/<n>/.../mvi/store/DialogState.kt`. Compile-time
mutual exclusivity. Exhaustive `when` in the screen.

```kotlin
// feature/<n>/.../mvi/store/DialogState.kt
package io.github.stslex.workeeper.feature.<n>.mvi.store

import androidx.compose.runtime.Stable

@Stable
internal sealed interface DialogState {

    @Stable
    data object Hidden : DialogState

    @Stable
    data class RestoreConfirmation(
        val createdAtFormatted: String,
        val sizeFormatted: String,
    ) : DialogState

    @Stable
    data object SignOutConfirmation : DialogState
}
```

```kotlin
// In Store.State
data class State(
    /* ... */
    val dialogState: DialogState = DialogState.Hidden,
) : Store.State
```

```kotlin
// In the screen
when (val dialog = state.dialogState) {
    DialogState.Hidden -> Unit
    is DialogState.RestoreConfirmation -> RestoreConfirmationDialog(
        state = dialog,
        onAction = { consume(it) },
    )
    DialogState.SignOutConfirmation -> SignOutConfirmationDialog(
        onAction = { consume(it) },
    )
}
```

```kotlin
// In the handler — open
updateState { current -> current.copy(dialogState = DialogState.SignOutConfirmation) }

// Close (any dialog)
updateState { current -> current.copy(dialogState = DialogState.Hidden) }
```

## Naming

The project convention across `feature/live-workout`, `feature/exercise`,
`feature/plan-editor`, `feature/single-training`, and `feature/settings`:

- **Type:** `DialogState` (unqualified, in the feature-local `mvi/store/` package).
  Do **not** prefix with the feature name (`<Feature>DialogState` is not used in this
  codebase) — the package scope already disambiguates.
- **Default variant:** `data object Hidden : DialogState`. Use **`Hidden`**, not
  `None` and not `Closed`. Every existing dialog state class in the project uses
  `Hidden`.
- **Variants:** name after the dialog's *intent*, not its widget. `ConfirmDelete`,
  `RestoreConfirmation`, `SignOutConfirmation`, `EmptyFinish`, `FinishSession`. A
  variant that exists only to display a label uses `data object`; a variant that
  carries display payload (titles, formatted strings, IDs) is a `data class`.
- **State field:** `val dialogState: DialogState = DialogState.Hidden` on the
  `Store.State` data class. Same pattern for `bottomSheetState: BottomSheetState` if
  the feature has bottom sheets.
- **Annotations:** mark both the sealed interface and every variant `@Stable`
  (mirrors the existing project files; ensures Compose treats them as stability-safe
  payloads).

## When the hard rule does NOT apply

- **Non-modal UI** — snackbars, persistent banners, inline error rows. Snackbars are
  `Event` (single-shot, `SnackbarManager`-driven). Persistent banners are normal
  State fields, not dialog state.
- **A single dialog** on a screen with no second dialog on the roadmap. A single
  nullable (or `Boolean`) is acceptable here. **The moment a second dialog gets
  added, migrate to sealed `DialogState`** as part of that change — do not add a
  second flag alongside the first.
- **Genuinely co-existing overlays** — e.g. a modal dialog plus a bottom toast that
  must remain visible. Reconsider the UX first; if both must coexist, model the
  non-modal one separately (e.g. snackbar via `Event`), not as a second dialog flag.

## Handler / test conventions

- **Dismiss action.** A single `Action.<Section>.Dismiss<X>` per dialog variant is
  fine; they all dispatch to `updateState { it.copy(dialogState = DialogState.Hidden) }`.
  A unified `DismissDialog` action is also acceptable when no per-dialog side effect
  needs to differ.
- **Open replaces previous.** Setting `dialogState` to a new variant from a non-Hidden
  state is allowed and tested as the "open while another dialog is shown" path —
  the sealed type guarantees only one variant is live, so the previous dialog
  vanishes correctly. Write at least one test asserting this:

  ```kotlin
  @Test
  fun `RequestSignOut while RestoreConfirmation is shown replaces dialog`() = runTest {
      store.stateFlow.value = store.stateFlow.value.copy(
          dialogState = DialogState.RestoreConfirmation(/* ... */),
      )
      handler.invoke(Action.<Section>.RequestSignOut)
      assertEquals(DialogState.SignOutConfirmation, store.stateFlow.value.dialogState)
  }
  ```

- **Display strings.** Pre-resolved in the handler via `ResourceWrapper.getString(...)`
  (or `context.getString(...)` if the feature still injects `@ApplicationContext` for
  legacy reasons), never inside the `updateState` lambda (Rule 1 of
  `compose-state-discipline.md`).
- **Back-gesture intercept.** If the screen exposes an `interceptBack: Boolean` derived
  property, OR `dialogState !is DialogState.Hidden` into it so the system back gesture
  closes the dialog before popping the screen.

## Reference implementations

- `feature/settings/.../mvi/store/DialogState.kt` — three variants (`Hidden`,
  `RestoreConfirmation` with payload, `SignOutConfirmation` data object). UI in
  `feature/settings/.../ui/SettingsScreen.kt` (exhaustive `when`). Tests in
  `feature/settings/.../mvi/handler/BackupClickHandlerTest.kt` (mutual-exclusivity
  asserted).
- `feature/live-workout/.../mvi/store/DialogState.kt` — richer set (`Hidden`,
  `DeleteDialog`, `EmptyFinish`, nested `ConfirmDialog` sealed sub-interface for
  three more confirms, `FinishSession` with payload). UI in
  `feature/live-workout/.../ui/LiveWorkoutScreen.kt`.

## Common pitfalls

- **Adding a second dialog without migrating the first.** A screen with one Boolean
  flag gets a feature request for a second dialog. The author adds a second Boolean
  next to it. Now the screen has the wrong-shape state. Migrate to sealed
  `DialogState` *as part of* the second-dialog change, not in a follow-up PR.
- **Naming the default `Closed` or `None`.** The codebase convention is `Hidden`.
  Five existing features use it; introducing a new spelling fragments grep and
  cross-file refactors.
- **Prefixing the type as `<Feature>DialogState`.** The feature package already
  qualifies the name. Two unqualified `DialogState` types in two feature packages
  never collide at the import level.
- **Holding the dialog payload in a Composable `remember { mutableStateOf(...) }`
  driven by `Event.Show*Dialog`.** That's the Rule-4 violation — see
  [`compose-state-discipline`](compose-state-discipline.md). Dialog payloads belong on
  `Store.State`, not on local Composable state.
- **Trying to use a Detekt rule to enforce this.** We chose not to ship a lint rule
  here — the false-positive profile is bad (any `*Visible: Boolean` on State trips
  it). The enforcement is this skill, the architecture-doc reference, and the
  tech-debt entries for known soft-violations.

## Verification

- The State data class has exactly one `val dialogState: DialogState` field; no
  parallel `*Visible: Boolean` / `*Confirmation: <X>?` fields exist alongside it.
- The screen renders dialogs via `when (val dialog = state.dialogState) { ... }`
  with `DialogState.Hidden -> Unit` (or implicit-exhaustive after handling all
  variants).
- Handlers open by setting a variant; close by setting `Hidden`.
- At least one handler test asserts the "open replaces previous" path.
- `:feature:<n>:detekt` is clean (no new violations from the rename).
- `:feature:<n>:testDebugUnitTest` is green.

## Known limitation

`dialogState` lives in the in-memory `StateFlow` of `BaseStore`. Configuration changes
survive (same VM-scoped store). Process death does not — `dialogState` is not
round-tripped through `SavedStateHandle`. Round-tripping critical dialogs is tracked
separately in [tech-debt.md → Dialog State Discipline — follow-ups](../../documentation/tech-debt.md).
