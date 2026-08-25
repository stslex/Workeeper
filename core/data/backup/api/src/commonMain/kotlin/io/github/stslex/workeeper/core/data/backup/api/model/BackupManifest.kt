// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.core.data.backup.api.model

/** Sidecar metadata stored with every backup; drives restore compatibility and the list UI. */
data class BackupManifest(
    val appVersion: String,
    val dbSchemaVersion: Int,
    val createdAtEpochMs: Long,
    val dbFileSizeBytes: Long,
    val deviceModel: String?,
)
