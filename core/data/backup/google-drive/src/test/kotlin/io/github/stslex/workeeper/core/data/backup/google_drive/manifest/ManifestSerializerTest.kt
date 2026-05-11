// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.core.data.backup.google_drive.manifest

import io.github.stslex.workeeper.core.data.backup.api.error.BackupError
import io.github.stslex.workeeper.core.data.backup.api.model.BackupManifest
import io.github.stslex.workeeper.core.data.backup.api.result.BackupResult
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

internal class ManifestSerializerTest {

    private val sample = BackupManifest(
        appVersion = "1.43.0",
        dbSchemaVersion = 6,
        createdAtEpochMs = 1_715_000_000_000L,
        dbFileSizeBytes = 2_345_678L,
        deviceModel = "Pixel 8",
    )

    @Test
    fun `round-trip preserves all fields`() {
        val raw = ManifestSerializer.serialize(sample)
        val result = ManifestSerializer.deserialize(raw)
        assertTrue(result is BackupResult.Success, "expected Success, got $result")
        assertEquals(sample, (result as BackupResult.Success).data)
    }

    @Test
    fun `round-trip preserves null deviceModel`() {
        val nullModel = sample.copy(deviceModel = null)
        val raw = ManifestSerializer.serialize(nullModel)
        val result = ManifestSerializer.deserialize(raw)
        assertTrue(result is BackupResult.Success)
        assertEquals(nullModel, (result as BackupResult.Success).data)
    }

    @Test
    fun `deserialize ignores unknown keys`() {
        val raw = """
            {
              "appVersion": "1.43.0",
              "dbSchemaVersion": 6,
              "createdAtEpochMs": 1715000000000,
              "dbFileSizeBytes": 2345678,
              "deviceModel": "Pixel 8",
              "futureField": "ignored"
            }
        """.trimIndent()
        val result = ManifestSerializer.deserialize(raw)
        assertTrue(result is BackupResult.Success)
    }

    @Test
    fun `deserialize returns CorruptedBackup on malformed JSON`() {
        val result = ManifestSerializer.deserialize("not a json")
        assertTrue(result is BackupResult.Failure)
        assertTrue((result as BackupResult.Failure).error is BackupError.CorruptedBackup)
    }

    @Test
    fun `deserialize returns CorruptedBackup on JSON missing required fields`() {
        val raw = """{ "appVersion": "1.0" }"""
        val result = ManifestSerializer.deserialize(raw)
        assertTrue(result is BackupResult.Failure)
        assertTrue((result as BackupResult.Failure).error is BackupError.CorruptedBackup)
    }
}
