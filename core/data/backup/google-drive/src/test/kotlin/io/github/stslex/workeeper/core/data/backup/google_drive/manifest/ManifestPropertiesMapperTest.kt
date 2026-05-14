// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.core.data.backup.google_drive.manifest

import io.github.stslex.workeeper.core.data.backup.api.error.BackupError
import io.github.stslex.workeeper.core.data.backup.api.model.BackupManifest
import io.github.stslex.workeeper.core.data.backup.api.result.BackupResult
import io.github.stslex.workeeper.core.data.backup.google_drive.manifest.ManifestPropertiesMapper.DEVICE_MODEL_MAX_LEN
import io.github.stslex.workeeper.core.data.backup.google_drive.manifest.ManifestPropertiesMapper.KEY_APP_VERSION
import io.github.stslex.workeeper.core.data.backup.google_drive.manifest.ManifestPropertiesMapper.KEY_CREATED_AT_EPOCH_MS
import io.github.stslex.workeeper.core.data.backup.google_drive.manifest.ManifestPropertiesMapper.KEY_DB_FILE_SIZE_BYTES
import io.github.stslex.workeeper.core.data.backup.google_drive.manifest.ManifestPropertiesMapper.KEY_DB_SCHEMA_VERSION
import io.github.stslex.workeeper.core.data.backup.google_drive.manifest.ManifestPropertiesMapper.KEY_DEVICE_MODEL
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

internal class ManifestPropertiesMapperTest {

    private val sample = BackupManifest(
        appVersion = "1.43.0",
        dbSchemaVersion = 6,
        createdAtEpochMs = 1_715_000_000_000L,
        dbFileSizeBytes = 2_345_678L,
        deviceModel = "Pixel 8",
    )

    @Test
    fun `round-trip preserves all fields`() {
        val props = ManifestPropertiesMapper.toAppProperties(sample)
        val result = ManifestPropertiesMapper.fromAppProperties(props)
        assertTrue(result is BackupResult.Success, "expected Success, got $result")
        assertEquals(sample, (result as BackupResult.Success).data)
    }

    @Test
    fun `round-trip preserves null deviceModel and omits key from map`() {
        val nullModel = sample.copy(deviceModel = null)
        val props = ManifestPropertiesMapper.toAppProperties(nullModel)
        assertTrue(KEY_DEVICE_MODEL !in props, "device_model should be absent when null")
        val result = ManifestPropertiesMapper.fromAppProperties(props)
        assertTrue(result is BackupResult.Success)
        assertEquals(nullModel, (result as BackupResult.Success).data)
        assertNull((result.data).deviceModel)
    }

    @Test
    fun `fromAppProperties returns CorruptedBackup when app_version missing`() {
        val props = ManifestPropertiesMapper.toAppProperties(sample) - KEY_APP_VERSION
        val result = ManifestPropertiesMapper.fromAppProperties(props)
        assertTrue(result is BackupResult.Failure)
        val reason = ((result as BackupResult.Failure).error as BackupError.CorruptedBackup).reason
        assertTrue(
            reason.contains(KEY_APP_VERSION),
            "expected reason to mention $KEY_APP_VERSION, got $reason",
        )
    }

    @Test
    fun `fromAppProperties returns CorruptedBackup when db_schema_version not numeric`() {
        val props = ManifestPropertiesMapper.toAppProperties(sample) + (KEY_DB_SCHEMA_VERSION to "abc")
        val result = ManifestPropertiesMapper.fromAppProperties(props)
        assertTrue(result is BackupResult.Failure)
        val reason = ((result as BackupResult.Failure).error as BackupError.CorruptedBackup).reason
        assertTrue(reason.contains(KEY_DB_SCHEMA_VERSION))
    }

    @Test
    fun `fromAppProperties returns CorruptedBackup when created_at_epoch_ms not numeric`() {
        val props = ManifestPropertiesMapper.toAppProperties(sample) +
            (KEY_CREATED_AT_EPOCH_MS to "notALong")
        val result = ManifestPropertiesMapper.fromAppProperties(props)
        assertTrue(result is BackupResult.Failure)
        val reason = ((result as BackupResult.Failure).error as BackupError.CorruptedBackup).reason
        assertTrue(reason.contains(KEY_CREATED_AT_EPOCH_MS))
    }

    @Test
    fun `fromAppProperties returns CorruptedBackup when db_file_size_bytes missing`() {
        val props = ManifestPropertiesMapper.toAppProperties(sample) - KEY_DB_FILE_SIZE_BYTES
        val result = ManifestPropertiesMapper.fromAppProperties(props)
        assertTrue(result is BackupResult.Failure)
        val reason = ((result as BackupResult.Failure).error as BackupError.CorruptedBackup).reason
        assertTrue(reason.contains(KEY_DB_FILE_SIZE_BYTES))
    }

    @Test
    fun `toAppProperties truncates deviceModel longer than 100 chars`() {
        val longModel = "Some Manufacturer Very Long Marketing Model Name ".repeat(10)
        val withLong = sample.copy(deviceModel = longModel)
        val props = ManifestPropertiesMapper.toAppProperties(withLong)
        assertEquals(DEVICE_MODEL_MAX_LEN, props.getValue(KEY_DEVICE_MODEL).length)
        assertTrue(longModel.startsWith(props.getValue(KEY_DEVICE_MODEL)))
    }

    @Test
    fun `every appProperty pair fits Drive's 124-byte per-pair limit`() {
        // Defensive: real-world manifest fields must never breach Drive's 124-byte
        // key+value cap. Build a worst-case manifest (max-length deviceModel,
        // Long.MAX_VALUE for epoch + size) and check every entry.
        val worstCase = BackupManifest(
            appVersion = "v9.99.99-rc-build-${"x".repeat(50)}",
            dbSchemaVersion = Int.MAX_VALUE,
            createdAtEpochMs = Long.MAX_VALUE,
            dbFileSizeBytes = Long.MAX_VALUE,
            deviceModel = "M".repeat(200),
        )
        val props = ManifestPropertiesMapper.toAppProperties(worstCase)
        props.forEach { (key, value) ->
            val bytes = key.toByteArray(Charsets.UTF_8).size +
                value.toByteArray(Charsets.UTF_8).size
            assertTrue(
                bytes <= MAX_PROPERTY_PAIR_BYTES,
                "pair $key=$value is $bytes bytes, max is $MAX_PROPERTY_PAIR_BYTES",
            )
        }
    }

    private companion object {
        const val MAX_PROPERTY_PAIR_BYTES = 124
    }
}
