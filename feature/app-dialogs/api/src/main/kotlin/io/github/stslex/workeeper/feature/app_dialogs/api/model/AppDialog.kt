// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.app_dialogs.api.model

import io.github.stslex.workeeper.core.data.backup.api.scheduling.BackupErrorCode

/**
 * Cross-feature dialog catalog. Every variant in this sealed type is a modal that:
 *
 * 1. Survives process restart — its state is persisted in DataStore, not memory.
 * 2. Appears regardless of the current navigation destination — `AppDialogHost`
 *    mounts above the `NavHost`.
 * 3. Is published by one feature and rendered by `feature/app-dialogs/impl`
 *    without callbacks back into the producer.
 *
 * Adding a new variant is mechanical — see
 * `.claude/skills/app-dialogs-pattern.md` and the catalog table in
 * `documentation/feature-specs/app-dialogs.md`.
 *
 * The initial catalog (v1) backs `documentation/feature-specs/backup-recovery.md`
 * Scenario 1 and Scenario 3 post-restart dialogs.
 */
sealed interface AppDialog {

    /** Stable identifier used for dedup, diagnostics, and dismiss-by-id. */
    val id: String

    /**
     * Post-restore happy path acknowledgement. Published by the restore-recovery
     * pre-flight after Room successfully opens the migrated database.
     */
    data class RestoreSuccess(
        val restoredAtEpochMs: Long,
        val previousVersionAvailable: Boolean,
    ) : AppDialog {
        override val id: String = ID

        companion object {
            const val ID: String = "restore_success"
        }
    }

    /**
     * Post-restore failure acknowledgement. Published after Scenario 1 rollback
     * has restored the user's pre-restore database; the dialog confirms data is
     * intact and surfaces the failure reason for issue-report context.
     */
    data class RestoreFailure(
        val reason: BackupErrorCode,
    ) : AppDialog {
        override val id: String = ID

        companion object {
            const val ID: String = "restore_failure"
        }
    }

    /**
     * User-initiated revert-last-restore confirmation. Published by the Settings
     * "Revert last restore" row tap; the dialog body shows the date of the data
     * that will be restored on confirm.
     */
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
