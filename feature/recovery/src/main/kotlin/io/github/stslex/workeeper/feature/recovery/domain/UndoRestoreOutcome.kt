// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.recovery.domain

/**
 * Outcome of [RestoreRecoveryCoordinator.performUndoRestore]: the consumer acknowledges the
 * dialog on `Succeeded || FileMissing || SourceUnusable`, and leaves it visible on [IoFailure].
 */
internal sealed interface UndoRestoreOutcome {

    /** Atomic rename succeeded; `UndoRestoreSuccess` published; restart the app. */
    data object Succeeded : UndoRestoreOutcome

    /** `pre_restore_backup.db` was absent; no swap happened and further taps cannot help. */
    data object FileMissing : UndoRestoreOutcome

    /**
     * The undo image exists but is not a usable database, so nothing was swapped and availability
     * was cleared. The file is kept: a validator false negative must not destroy a real image.
     */
    data object SourceUnusable : UndoRestoreOutcome

    /** The atomic rename failed; availability stays set so the user can retry from Settings. */
    data object IoFailure : UndoRestoreOutcome
}
