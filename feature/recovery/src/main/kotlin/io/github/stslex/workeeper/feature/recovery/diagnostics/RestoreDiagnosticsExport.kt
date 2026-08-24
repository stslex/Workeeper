// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.recovery.diagnostics

import android.net.Uri
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import io.github.stslex.workeeper.core.core.di.AppScope
import io.github.stslex.workeeper.core.core.platform.PlatformInfoProvider
import io.github.stslex.workeeper.core.data.backup.api.RecoveryDiagnosticsExporter
import io.github.stslex.workeeper.core.data.backup.api.restore.RestoreStateRepository

/**
 * The one Scenario-1 diagnostics call. Both surfaces that offer "Export diagnostics" for an
 * interrupted restore — the `RestoreFailure` dialog and `RecoveryActivity` — go through it, so
 * they cannot drift into producing different files for the same failure.
 */
interface RestoreDiagnosticsExport {

    /** `null` when the write failed, so the caller hides sharing. */
    suspend fun export(): Uri?
}

@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class)
// Public for cross-module Metro aggregation; obtain through DI.
class RestoreDiagnosticsExportImpl @Inject constructor(
    private val exporter: RecoveryDiagnosticsExporter,
    private val restoreStateRepository: RestoreStateRepository,
    private val platformInfo: PlatformInfoProvider,
) : RestoreDiagnosticsExport {

    override suspend fun export(): Uri? = exporter.exportRestoreFailure(
        exception = null,
        // The journal is unresolved on every route that reaches this call, so the interrupted
        // restore's manifest context is still there to attach.
        context = restoreStateRepository.getAttempt()?.context,
        appVersionName = platformInfo.appVersionName(),
        appVersionCode = platformInfo.appVersionCode(),
    )
}
