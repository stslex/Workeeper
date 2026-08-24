// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.core.data.backup.api.model

/**
 * Reference to a single uploaded backup. [remoteId] is opaque to api consumers — its
 * format depends on the storage provider (e.g. a Drive file id) and must only be
 * passed back to
 * [io.github.stslex.workeeper.core.data.backup.api.BackupStorage.downloadBackup] or
 * [io.github.stslex.workeeper.core.data.backup.api.BackupStorage.deleteBackup].
 *
 * [manifest] mirrors the sidecar uploaded with the backup so listings can render
 * without an extra round trip.
 */
data class BackupRef(
    val remoteId: String,
    val manifest: BackupManifest,
)
