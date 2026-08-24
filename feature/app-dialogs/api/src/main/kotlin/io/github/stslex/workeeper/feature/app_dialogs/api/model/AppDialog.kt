// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.app_dialogs.api.model

import io.github.stslex.workeeper.core.data.backup.api.scheduling.BackupErrorCode

/**
 * Cross-feature catalog of process-survival modals: persisted in DataStore, rendered above the
 * NavHost regardless of destination. See documentation/feature-specs/app-dialogs.md.
 */
sealed interface AppDialog {

    /** Stable identifier used for dedup, diagnostics, and dismiss-by-id. */
    val id: String

    /** Post-restore acknowledgement, published by the restore-recovery pre-flight. */
    data class RestoreSuccess(
        val restoredAtEpochMs: Long,
        val previousVersionAvailable: Boolean,
    ) : AppDialog {
        override val id: String = ID

        companion object {
            const val ID: String = "restore_success"
        }
    }

    /** Post-restore failure acknowledgement, published after Scenario 1 rollback. */
    data class RestoreFailure(
        val reason: BackupErrorCode,
    ) : AppDialog {
        override val id: String = ID

        companion object {
            const val ID: String = "restore_failure"
        }
    }

    /** Revert-last-restore confirmation from the Settings row; the body shows the data's date. */
    data class UndoRestoreConfirmation(
        val originalDataDateEpochMs: Long,
    ) : AppDialog {
        override val id: String = ID

        companion object {
            const val ID: String = "undo_restore_confirmation"
        }
    }

    /** Post-undo-restore happy path acknowledgement. */
    data object UndoRestoreSuccess : AppDialog {
        override val id: String = "undo_restore_success"
    }
}
