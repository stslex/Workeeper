// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.app_dialogs.impl.domain

import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.preferencesOf
import io.github.stslex.workeeper.core.data.backup.api.scheduling.BackupErrorCode
import io.github.stslex.workeeper.feature.app_dialogs.api.model.AppDialog
import io.github.stslex.workeeper.feature.app_dialogs.impl.data.AppDialogKeys
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Test

/** Pins the priority walk in [AppDialogResolver] against hand-built [Preferences] snapshots. */
internal class AppDialogResolverTest {

    @Test
    fun `no flag set resolves to null`() {
        assertNull(AppDialogResolver(preferencesOf()))
    }

    @Test
    fun `RestoreFailure resolves with reason`() {
        val prefs = preferencesOf(
            AppDialogKeys.PENDING_RESTORE_FAILURE to true,
            AppDialogKeys.PENDING_RESTORE_FAILURE_REASON to
                BackupErrorCode.MissingMigrationPath.name,
        )

        val resolved = AppDialogResolver(prefs) as AppDialog.RestoreFailure

        assertEquals(BackupErrorCode.MissingMigrationPath, resolved.reason)
    }

    @Test
    fun `RestoreFailure with missing reason key falls back to Unknown`() {
        val prefs = preferencesOf(AppDialogKeys.PENDING_RESTORE_FAILURE to true)

        val resolved = AppDialogResolver(prefs) as AppDialog.RestoreFailure

        assertEquals(BackupErrorCode.Unknown, resolved.reason)
    }

    @Test
    fun `RestoreFailure with unparseable reason name falls back to Unknown`() {
        val prefs = preferencesOf(
            AppDialogKeys.PENDING_RESTORE_FAILURE to true,
            AppDialogKeys.PENDING_RESTORE_FAILURE_REASON to "NoSuchErrorCode",
        )

        val resolved = AppDialogResolver(prefs) as AppDialog.RestoreFailure

        assertEquals(BackupErrorCode.Unknown, resolved.reason)
    }

    @Test
    fun `RestoreSuccess resolves with payload`() {
        val prefs = preferencesOf(
            AppDialogKeys.PENDING_RESTORE_SUCCESS to true,
            AppDialogKeys.PENDING_RESTORE_SUCCESS_AT_EPOCH_MS to 1_700_000_000_000L,
            AppDialogKeys.PENDING_RESTORE_SUCCESS_HAS_PREVIOUS to true,
        )

        val resolved = AppDialogResolver(prefs) as AppDialog.RestoreSuccess

        assertEquals(1_700_000_000_000L, resolved.restoredAtEpochMs)
        assertEquals(true, resolved.previousVersionAvailable)
    }

    @Test
    fun `RestoreSuccess with missing metadata uses defaults`() {
        val prefs = preferencesOf(AppDialogKeys.PENDING_RESTORE_SUCCESS to true)

        val resolved = AppDialogResolver(prefs) as AppDialog.RestoreSuccess

        assertEquals(0L, resolved.restoredAtEpochMs)
        assertEquals(false, resolved.previousVersionAvailable)
    }

    @Test
    fun `UndoRestoreSuccess resolves to the data object`() {
        val prefs = preferencesOf(AppDialogKeys.PENDING_UNDO_RESTORE_SUCCESS to true)

        assertSame(AppDialog.UndoRestoreSuccess, AppDialogResolver(prefs))
    }

    @Test
    fun `UndoRestoreConfirmation resolves with date`() {
        val prefs = preferencesOf(
            AppDialogKeys.PENDING_UNDO_RESTORE_CONFIRMATION to true,
            AppDialogKeys.PENDING_UNDO_RESTORE_CONFIRMATION_ORIGINAL_AT_EPOCH_MS to 1_650_000_000L,
        )

        val resolved = AppDialogResolver(prefs) as AppDialog.UndoRestoreConfirmation

        assertEquals(1_650_000_000L, resolved.originalDataDateEpochMs)
    }

    @Test
    fun `RestoreFailure outranks every other variant when all flags are set`() {
        val prefs = allFlagsSet()

        val resolved = AppDialogResolver(prefs) as AppDialog.RestoreFailure

        assertEquals(BackupErrorCode.Unknown, resolved.reason)
    }

    @Test
    fun `RestoreSuccess wins when RestoreFailure is absent`() {
        val prefs = allFlagsSet(restoreFailure = false)

        assertEquals(AppDialog.RestoreSuccess::class.java, AppDialogResolver(prefs)!!.javaClass)
    }

    @Test
    fun `UndoRestoreSuccess wins over UndoRestoreConfirmation`() {
        val prefs = allFlagsSet(restoreFailure = false, restoreSuccess = false)

        assertSame(AppDialog.UndoRestoreSuccess, AppDialogResolver(prefs))
    }

    @Test
    fun `UndoRestoreConfirmation is the lowest-priority resolution`() {
        val prefs = allFlagsSet(
            restoreFailure = false,
            restoreSuccess = false,
            undoSuccess = false,
        )

        val resolved = AppDialogResolver(prefs) as AppDialog.UndoRestoreConfirmation

        assertEquals(0L, resolved.originalDataDateEpochMs)
    }

    private fun allFlagsSet(
        restoreFailure: Boolean = true,
        restoreSuccess: Boolean = true,
        undoSuccess: Boolean = true,
        undoConfirmation: Boolean = true,
    ): Preferences {
        val entries = buildList<Preferences.Pair<*>> {
            if (restoreFailure) add(AppDialogKeys.PENDING_RESTORE_FAILURE to true)
            if (restoreSuccess) add(AppDialogKeys.PENDING_RESTORE_SUCCESS to true)
            if (undoSuccess) add(AppDialogKeys.PENDING_UNDO_RESTORE_SUCCESS to true)
            if (undoConfirmation) add(AppDialogKeys.PENDING_UNDO_RESTORE_CONFIRMATION to true)
        }
        return preferencesOf(*entries.toTypedArray())
    }
}
