// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.app_dialogs.api.model

/**
 * The union of buttons that any [AppDialog] variant can present. The Host
 * passes one of these into the `AppDialogStore` as `Action.UserAction(dialog,
 * action)` when the user taps a button. Per-feature reaction to specific
 * `(variant, action)` pairs lives in consumer-side `@Singleton` handlers —
 * never in the Store's own handlers — so that the Store is independent of
 * the producer features it serves.
 *
 * Variant → button → action mapping (initial catalog):
 *
 * | Variant | Buttons → action |
 * |---|---|
 * | [AppDialog.RestoreSuccess] | OK → [Acknowledge]; "Undo restore" → [RequestUndo] |
 * | [AppDialog.RestoreFailure] | OK → [Acknowledge]; "Report issue" → [Report]; |
 * | | "Export diagnostics" → [ExportDiagnostics] |
 * | [AppDialog.UndoRestoreConfirmation] | Confirm → [ConfirmUndo]; Cancel → [Cancel] |
 * | [AppDialog.UndoRestoreSuccess] | OK → [Acknowledge] |
 *
 * Adding a button to a future variant: add the entry here, render the button
 * in the per-variant Composable, dispatch the matching `AppDialogUserChoice`
 * through the Host's `onChoice` lambda. The consumer feature that should
 * react adds a branch in its observer-side handler.
 */
enum class AppDialogUserAction {
    Acknowledge,
    RequestUndo,
    Report,
    ExportDiagnostics,
    ConfirmUndo,
    Cancel,
}
