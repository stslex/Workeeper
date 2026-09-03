// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.core.data.backup.api.model

/** Reference to one uploaded backup; [remoteId] is opaque, for `BackupStorage` calls only. */
data class BackupRef(
    val remoteId: String,
    val manifest: BackupManifest,
)
