// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.core.data.backup.api

import android.net.Uri
import io.github.stslex.workeeper.core.data.backup.api.restore.RestoreInProgressContext

/**
 * Writes a human-readable recovery diagnostics `.txt` to app cache and returns its content [Uri].
 * The GMS/Room-free seam; the Android-bound file logic lives in `feature/recovery`.
 */
interface RecoveryDiagnosticsExporter {

    /** Restore-time variant. `null` when the write failed, so the caller hides sharing. */
    suspend fun exportRestoreFailure(
        exception: Throwable?,
        context: RestoreInProgressContext?,
        appVersionName: String,
        appVersionCode: Long,
    ): Uri?

    /** Startup-time variant; reads version info itself. `null` when the write failed. */
    suspend fun exportStartupMigrationFailure(): Uri?
}
