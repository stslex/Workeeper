// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.recovery

/**
 * Three-way outcome of [RestoreRecoveryCoordinator.performUndoRestore]. The
 * previous `Boolean` return type collapsed [FileMissing] and [IoFailure]
 * into one false case, which prevented the consumer-side reactor from
 * deciding whether to clear the dialog flag after the call: in [FileMissing]
 * the dialog should dismiss (no further user-driven action is possible), but
 * in [IoFailure] the dialog must stay visible so the user sees the reaction
 * did not complete.
 *
 * The handler in `RestoreDialogChoiceObserver` gates
 * [io.github.stslex.workeeper.feature.app_dialogs.api.observer.AppDialogObserver.acknowledgeReaction]
 * on `Succeeded || FileMissing` — see that class's KDoc for the BLOCKER 2
 * dismiss-after contract and the crash-window guarantees.
 */
internal sealed interface UndoRestoreOutcome {

    /** Atomic rename succeeded; `UndoRestoreSuccess` published; restart the app. */
    data object Succeeded : UndoRestoreOutcome

    /**
     * `pre_restore_backup.db` was absent — either never existed or already
     * consumed by a prior call. The defensive
     * `clearPreRestoreBackupAvailable` ran. No swap happened; further taps
     * cannot accomplish anything new. Safe to acknowledge the dialog.
     */
    data object FileMissing : UndoRestoreOutcome

    /**
     * The file existed but the atomic rename failed (IO error). The file is
     * still at its original location; `pre_restore_backup_available` is
     * intentionally NOT cleared so the user can retry from Settings →
     * "Revert last restore". The consumer must NOT acknowledge the dialog
     * — keeping it visible is what tells the user the reaction did not
     * complete.
     */
    data object IoFailure : UndoRestoreOutcome
}
