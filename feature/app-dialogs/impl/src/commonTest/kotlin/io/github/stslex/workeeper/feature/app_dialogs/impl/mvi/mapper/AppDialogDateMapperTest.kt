// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.app_dialogs.impl.mvi.mapper

import io.github.stslex.workeeper.core.core.resources.ResourceWrapper
import io.github.stslex.workeeper.core.data.backup.api.restore.RestoreOwnerId
import io.github.stslex.workeeper.core.data.backup.api.restore.UndoRef
import io.github.stslex.workeeper.core.data.backup.api.scheduling.BackupErrorCode
import io.github.stslex.workeeper.feature.app_dialogs.api.model.AppDialog
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * D3: which epoch each dialog's body date is formatted from, and that a dialog without a date
 * formats nothing.
 */
internal class AppDialogDateMapperTest {

    private val formatted = mutableListOf<Long>()

    private val resources = object : ResourceWrapper {
        override fun getString(id: Int, vararg args: Any): String =
            error("the date mapper reads no copy through ResourceWrapper; id $id")

        override fun getQuantityString(id: Int, quantity: Int, vararg args: Any): String =
            error("the date mapper reads no plural through ResourceWrapper; id $id")

        override fun getAbbreviatedRelativeTime(timestamp: Long, now: Long): String =
            error("Not used in AppDialogDateMapperTest")

        override fun formatDayMonth(timestamp: Long): String =
            error("Not used in AppDialogDateMapperTest")

        override fun formatMediumDate(timestamp: Long): String {
            formatted += timestamp
            return "medium:$timestamp"
        }
    }

    @Test
    fun `a restore success labels the date its backup was restored`() {
        val dialog = AppDialog.RestoreSuccess(
            restoredAtEpochMs = 1_700_000_000_000L,
            previousVersionAvailable = true,
        )

        assertEquals("medium:1700000000000", dialog.toDateLabel(resources))
        assertEquals(listOf(1_700_000_000_000L), formatted)
    }

    @Test
    fun `an undo confirmation labels the date of the data it brings back`() {
        val dialog = AppDialog.UndoRestoreConfirmation(
            undoRef = UndoRef(RestoreOwnerId("00000000-0000-4000-8000-000000000011")),
            originalDataDateEpochMs = 1_650_000_000_000L,
        )

        assertEquals("medium:1650000000000", dialog.toDateLabel(resources))
        assertEquals(listOf(1_650_000_000_000L), formatted)
    }

    @Test
    fun `a failure or an undo success or no dialog labels nothing and formats nothing`() {
        val dialogs = listOf(
            AppDialog.RestoreFailure(reason = BackupErrorCode.Unknown),
            AppDialog.UndoRestoreSuccess(),
            null,
        )

        assertEquals(listOf("", "", ""), dialogs.map { it.toDateLabel(resources) })
        assertEquals(emptyList<Long>(), formatted)
    }
}
