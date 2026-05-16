// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.recovery

import android.content.Context
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import dagger.hilt.android.qualifiers.ApplicationContext
import io.github.stslex.workeeper.core.data.backup.api.restore.RestoreStateRepository
import io.github.stslex.workeeper.feature.app_dialogs.api.actions.AppDialogActions
import io.github.stslex.workeeper.feature.app_dialogs.api.model.AppDialog
import io.github.stslex.workeeper.feature.app_dialogs.api.publisher.AppDialogPublisher
import javax.inject.Inject
import javax.inject.Singleton

/**
 * App-graph wiring for [AppDialogActions]. Delegates undo execution to
 * [RestoreRecoveryCoordinator] (which owns the file swap + restart), reads
 * the preserved-data date from [RestoreStateRepository] for the undo
 * confirmation publish, and exports diagnostics via
 * [RecoveryDiagnosticsExporter]. The host calls these methods from its
 * Compose layer via the `AppDialogActionsEntryPoint`.
 *
 * The "performUndoRestore" path restarts the app on success — the call
 * never returns in that case.
 */
@Singleton
internal class AppDialogActionsImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    private val coordinator: RestoreRecoveryCoordinator,
    private val restoreStateRepository: RestoreStateRepository,
    private val appDialogPublisher: AppDialogPublisher,
    private val diagnosticsExporter: RecoveryDiagnosticsExporter,
) : AppDialogActions {

    override suspend fun performUndoRestore() {
        val succeeded = coordinator.performUndoRestore()
        if (succeeded) coordinator.restartApp()
    }

    override suspend fun publishUndoConfirmation() {
        val originalDate = restoreStateRepository.getPreRestoreOriginalDate()
            ?: return
        appDialogPublisher.publish(
            AppDialog.UndoRestoreConfirmation(originalDataDateEpochMs = originalDate),
        )
    }

    override suspend fun exportRestoreDiagnostics(): Uri? {
        val info = readPackageInfo()
        return diagnosticsExporter.exportRestoreFailure(
            exception = null,
            // Best-effort: the in-progress context is cleared after rollback,
            // so post-rollback exports show "(no in-progress context — flag
            // was set but payload missing)" rather than stale data. PR-E will
            // optionally persist a last-failure context to recover the fields
            // for the diagnostic export.
            context = restoreStateRepository.getRestoreInProgressContext(),
            appVersionName = info.versionName.orEmpty(),
            appVersionCode = info.longVersionCode,
        )
    }

    override fun restartApp() {
        coordinator.restartApp()
    }

    private fun readPackageInfo(): PackageInfo =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.packageManager.getPackageInfo(
                context.packageName,
                PackageManager.PackageInfoFlags.of(0),
            )
        } else {
            @Suppress("DEPRECATION")
            context.packageManager.getPackageInfo(context.packageName, 0)
        }
}
