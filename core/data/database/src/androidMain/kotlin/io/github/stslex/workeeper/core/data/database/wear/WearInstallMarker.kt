// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.core.data.database.wear

import android.content.Context
import io.github.stslex.workeeper.core.core.logger.Log
import io.github.stslex.workeeper.core.data.database.AppDatabase
import java.io.File

/**
 * Records that THIS install has prepared the Wear storage of the database file it holds.
 *
 * Android Auto Backup and device transfer copy `databases/` verbatim, `wear_database_metadata`
 * included, so a restored install starts life already holding the source install's epoch — and
 * `INSERT OR IGNORE` over a seeded row is a no-op. Two installs then claim one epoch while
 * holding each other's receipts. `RestoreRecoveryCoordinator` cannot see that path at all: the
 * platform copied the file, the app did not.
 *
 * `noBackupFilesDir` is the seam, because Auto Backup never captures it by construction. A marker
 * that is absent means this install has not prepared this file: a fresh install, or a
 * platform-restored one. Excluding `databases/` from the backup rules would close the same hole
 * by also refusing to carry the workout database to a new phone, which is the feature users rely
 * on.
 */
class WearInstallMarker(private val directory: File) {

    private val markerFile: File get() = File(directory, MARKER_NAME)

    /** `false` on a fresh install and on a platform-restored one — both of which must rotate. */
    fun isInstallRecorded(): Boolean = markerFile.isFile

    /**
     * Best-effort, and deliberately not fsynced: a lost marker costs one extra epoch rotation on
     * the next launch, which forces a fresh watch handshake and loses nothing. Failing to write it
     * must never fail the launch it belongs to.
     */
    fun recordInstall() {
        runCatching {
            directory.mkdirs()
            markerFile.createNewFile()
        }.onFailure { error ->
            Log.tag(TAG).w { "wear install marker could not be written: $error" }
        }
    }

    private companion object {
        const val TAG = "WearInstallMarker"
        const val MARKER_NAME = "wear-install-marker"
    }
}

/** The production marker: the one app directory the platform's backup never captures. */
fun wearInstallMarker(context: Context): WearInstallMarker =
    WearInstallMarker(context.noBackupFilesDir)

/**
 * [prepareWearSyncStorage] for a launch, treating a fresh-or-platform-restored install as a
 * restore.
 *
 * Reuses the one rotation path rather than adding a second: a missing marker simply forces
 * `rotateDatabaseEpoch`, so the epoch changes and every receipt is cleared exactly as an in-app
 * restore does. The marker is written only after that commits, so a process that dies in between
 * rotates again on the next launch instead of adopting a foreign epoch.
 */
suspend fun prepareWearSyncStorageForLaunch(
    database: AppDatabase,
    installMarker: WearInstallMarker,
    rotateDatabaseEpoch: Boolean,
): String {
    val freshInstall = !installMarker.isInstallRecorded()
    val epoch = prepareWearSyncStorage(database, rotateDatabaseEpoch || freshInstall)
    if (freshInstall) installMarker.recordInstall()
    return epoch
}
