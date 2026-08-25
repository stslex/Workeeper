// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.recovery.diagnostics

import android.net.Uri
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import io.github.stslex.workeeper.core.core.di.AppScope
import io.github.stslex.workeeper.core.core.platform.PlatformInfoProvider
import io.github.stslex.workeeper.core.data.backup.api.RecoveryDiagnosticsExporter
import io.github.stslex.workeeper.core.data.backup.api.restore.RestoreAttempt
import io.github.stslex.workeeper.core.data.backup.api.restore.RestoreProtocolRead
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

    override suspend fun export(): Uri? {
        val context = when (val protocol = restoreStateRepository.readProtocol()) {
            is RestoreProtocolRead.Current ->
                (protocol.state.attempt as? RestoreAttempt.Restore)?.context

            is RestoreProtocolRead.Legacy -> protocol.state.context
            is RestoreProtocolRead.Corrupt -> null
        }
        return exporter.exportRestoreFailure(
            exception = null,
            context = context,
            appVersionName = platformInfo.appVersionName(),
            appVersionCode = platformInfo.appVersionCode(),
        )
    }
}
