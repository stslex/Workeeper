// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.core.data.backup.api

/**
 * Constants for the AI-readable JSON snapshot uploaded to a *visible* Drive folder
 * (sibling of the binary backup; see `drive-ai-export.md`). The retention cap is shared
 * with the binary path via [BackupConstants.MAX_BACKUPS] — deliberately NOT a copy.
 */
object SnapshotConstants {

    /** Visible Drive folder the snapshot is written into (created at My Drive root). */
    const val FOLDER_NAME: String = "Workeeper"

    /** Filename prefix of every uploaded snapshot (`workeeper_export_<epochMs>.json`). */
    const val FILE_PREFIX: String = "workeeper_export_"

    /** Filename suffix of every uploaded snapshot. */
    const val FILE_SUFFIX: String = ".json"
}
