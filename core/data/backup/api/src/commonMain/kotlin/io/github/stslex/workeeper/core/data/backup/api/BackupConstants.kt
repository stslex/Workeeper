// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.core.data.backup.api

/**
 * Cross-impl constants shared by backup callers and impl modules. Kept in the api
 * module so the UI layer and the google-drive impl agree on the same values.
 */
object BackupConstants {

    /** Maximum number of backups retained per account. Older backups are pruned by impls. */
    const val MAX_BACKUPS: Int = 3

    /** Prefix applied to every uploaded archive's filename. */
    const val FILE_PREFIX: String = "app_"

    /** Filename suffix of the SQLite payload inside an uploaded backup. */
    const val DB_FILE_SUFFIX: String = ".db"

    /** Filename suffix of the sidecar manifest uploaded alongside the db file. */
    const val MANIFEST_FILE_SUFFIX: String = ".json"
}
