// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.core.data.backup.google_drive.storage

import io.github.stslex.workeeper.core.data.backup.api.model.BackupManifest
import io.github.stslex.workeeper.core.data.backup.api.model.BackupRef
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

internal class RotationPolicyTest {

    @Test
    fun `refsToDelete returns empty when size is below cap`() {
        val refs = listOf(ref("a", 100L), ref("b", 200L))
        assertTrue(RotationPolicy.refsToDelete(refs, max = 3) { it.manifest.createdAtEpochMs }.isEmpty())
    }

    @Test
    fun `refsToDelete returns empty when size equals cap`() {
        val refs = listOf(ref("a", 100L), ref("b", 200L), ref("c", 300L))
        assertTrue(RotationPolicy.refsToDelete(refs, max = 3) { it.manifest.createdAtEpochMs }.isEmpty())
    }

    @Test
    fun `refsToDelete returns oldest when size exceeds cap`() {
        val refs = listOf(
            ref("a", 100L),
            ref("b", 200L),
            ref("c", 300L),
            ref("d", 400L),
        )
        val toDelete = RotationPolicy.refsToDelete(refs, max = 3) { it.manifest.createdAtEpochMs }
        assertEquals(listOf("a"), toDelete.map { it.remoteId })
    }

    @Test
    fun `refsToDelete returns N oldest when size exceeds cap by N`() {
        val refs = listOf(
            ref("a", 100L),
            ref("b", 200L),
            ref("c", 300L),
            ref("d", 400L),
            ref("e", 500L),
        )
        val toDelete = RotationPolicy.refsToDelete(refs, max = 3) { it.manifest.createdAtEpochMs }
        assertEquals(listOf("a", "b"), toDelete.map { it.remoteId })
    }

    private fun ref(id: String, createdAt: Long): BackupRef = BackupRef(
        remoteId = id,
        manifest = BackupManifest(
            appVersion = "test",
            dbSchemaVersion = 1,
            createdAtEpochMs = createdAt,
            dbFileSizeBytes = 0L,
            deviceModel = null,
        ),
    )
}
