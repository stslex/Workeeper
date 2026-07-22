// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.core.data.backup.api

import android.net.Uri
import io.github.stslex.workeeper.core.data.backup.api.restore.RestoreInProgressContext

/**
 * Writes a human-readable recovery diagnostics `.txt` to app cache and returns its content [Uri].
 *
 * Extracted to `core:data:backup:api` in App-Scope Collapse Step 6 (P-REC): the impl
 * (`feature/recovery`'s `RecoveryDiagnosticsExporter`) is Metro-owned via
 * `@ContributesBinding(AppScope)` and exposed on the app graph as `recoveryDiagnosticsExporter`, so the
 * post-cut library consumer (`RecoveryActivity`) reads it via the typed holder (`RecoveryDepsHolder`) — but
 * the app-scope dep interfaces cannot name a `feature`-owned type, so the CONTRACT lives here in the api
 * module both the dep interfaces and `feature/recovery` already see. The impl carries the Android-bound
 * file/`Uri`/`Context` logic; this interface is the GMS/Room-free seam.
 */
interface RecoveryDiagnosticsExporter {

    /**
     * Scenario 1 (restore-time) variant. Writes a restore-failure diagnostic and returns its content
     * URI, or `null` if the write failed (caller hides the share action).
     */
    suspend fun exportRestoreFailure(
        exception: Throwable?,
        context: RestoreInProgressContext?,
        appVersionName: String,
        appVersionCode: Long,
    ): Uri?

    /**
     * Scenario 2 (startup-time) variant. Writes a startup-migration-failure diagnostic and returns its
     * content URI, or `null` if the write failed. Reads version + install-source info from the app
     * context directly, so `RecoveryActivity` does not plumb them in.
     */
    suspend fun exportStartupMigrationFailure(): Uri?
}
