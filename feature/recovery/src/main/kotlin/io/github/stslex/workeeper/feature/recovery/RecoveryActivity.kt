// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.recovery

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.core.content.FileProvider
import androidx.core.net.toUri
import androidx.lifecycle.lifecycleScope
import dagger.hilt.android.AndroidEntryPoint
import io.github.stslex.workeeper.core.data.database.snapshot.DatabaseSnapshotProvider
import io.github.stslex.workeeper.core.ui.kit.components.button.AppButton
import io.github.stslex.workeeper.core.ui.kit.components.button.AppButtonSize
import io.github.stslex.workeeper.core.ui.kit.theme.AppDimension
import io.github.stslex.workeeper.core.ui.kit.theme.AppTheme
import io.github.stslex.workeeper.core.ui.kit.theme.AppUi
import io.github.stslex.workeeper.feature.recovery.diagnostics.RecoveryDiagnosticsExporter
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

/**
 * Room-free fallback launcher for Scenario 2 (startup migration failure).
 * Hosted by `feature/recovery` because it must run without any Hilt graph
 * entry that touches `AppDatabase`.
 *
 * **DB-free invariant**: this activity only calls methods on its injected
 * collaborators that operate on file paths or pure-Kotlin data
 * (`getPreMigrationBackupFile`, `availableMigrationsLabel`,
 * `exportStartupMigrationFailure`). Calling
 * `currentSchemaVersion`/`captureSnapshot`/`restoreFromSnapshot`/
 * `preserveCurrentDb` would force `appDatabase.openHelper.{writable,readable}Database`
 * — which triggers the migration we are trying to avoid. Tests verify
 * the four buttons stay on Room-free paths.
 *
 * Routing entry: `MainActivity.onCreate` reads
 * `StartupMigrationCoordinator`'s persisted result; on
 * `RouteToRecovery` it finishes itself and starts this activity via
 * intent. The activity exposes four actions:
 *
 * | Action | Behavior |
 * |---|---|
 * | Update app | `market://details?id=<package>` with `https://play.google.com/...` fallback. |
 * | Export raw data | Shares `cache/pre_migration_backup.db` via FileProvider. |
 * | Report issue | Opens the GitHub issue URL with `bug,migration` labels. |
 * | Export diagnostics | Writes a `.txt` via `RecoveryDiagnosticsExporter` and launches `ACTION_SEND`. |
 */
@AndroidEntryPoint
class RecoveryActivity : ComponentActivity() {

    @Inject internal lateinit var snapshotProvider: DatabaseSnapshotProvider

    @Inject internal lateinit var diagnosticsExporter: RecoveryDiagnosticsExporter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            AppTheme {
                RecoveryContent(
                    onUpdateApp = ::openPlayStore,
                    onExportRawData = ::exportRawData,
                    onReportIssue = ::openGitHubIssue,
                    onExportDiagnostics = ::exportDiagnostics,
                )
            }
        }
    }

    private fun openPlayStore() {
        val market = Intent(
            Intent.ACTION_VIEW,
            "market://details?id=$packageName".toUri(),
        ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        val fallback = Intent(
            Intent.ACTION_VIEW,
            "https://play.google.com/store/apps/details?id=$packageName".toUri(),
        ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { startActivity(market) }
            .onFailure { startActivity(fallback) }
    }

    private fun exportRawData() {
        val file = snapshotProvider.getPreMigrationBackupFile() ?: return
        val uri = fileProviderUriFor(file)
        shareFile(
            uri = uri,
            mimeType = MIME_RAW_DATA,
            chooserTitleRes = R.string.recovery_export_data_chooser,
        )
    }

    private fun openGitHubIssue() {
        val title = Uri.encode(getString(R.string.recovery_report_title))
        val labels = Uri.encode(GITHUB_ISSUE_LABELS)
        val url = "$GITHUB_ISSUE_BASE_URL?title=$title&labels=$labels"
        runCatching {
            startActivity(
                Intent(Intent.ACTION_VIEW, url.toUri())
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
        }
    }

    private fun exportDiagnostics() {
        lifecycleScope.launch {
            val uri = diagnosticsExporter.exportStartupMigrationFailure() ?: return@launch
            shareFile(
                uri = uri,
                mimeType = MIME_DIAGNOSTICS,
                chooserTitleRes = R.string.recovery_export_diagnostics_chooser,
            )
        }
    }

    private fun fileProviderUriFor(file: File): Uri =
        FileProvider.getUriForFile(this, "$packageName.fileprovider", file)

    private fun shareFile(uri: Uri, mimeType: String, chooserTitleRes: Int) {
        val send = Intent(Intent.ACTION_SEND).apply {
            type = mimeType
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        val chooser = Intent
            .createChooser(send, getString(chooserTitleRes))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { startActivity(chooser) }
            .onFailure { e ->
                // Best-effort — the share intent failure (e.g. no app handles
                // the MIME) is non-fatal; the user can still use the other
                // recovery buttons.
                if (e !is ActivityNotFoundException) throw e
            }
    }

    private companion object {
        const val GITHUB_ISSUE_BASE_URL = "https://github.com/stslex/Workeeper/issues/new"
        const val GITHUB_ISSUE_LABELS = "bug,migration"
        const val MIME_RAW_DATA = "application/octet-stream"
        const val MIME_DIAGNOSTICS = "text/plain"
    }
}

@Composable
internal fun RecoveryContent(
    onUpdateApp: () -> Unit,
    onExportRawData: () -> Unit,
    onReportIssue: () -> Unit,
    onExportDiagnostics: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(AppUi.colors.surfaceTier0)
            .systemBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(AppDimension.Space.lg),
        verticalArrangement = Arrangement.spacedBy(AppDimension.Space.md),
    ) {
        Text(
            text = stringResource(R.string.recovery_title),
            style = AppUi.typography.titleLarge,
            color = AppUi.colors.textPrimary,
        )
        Text(
            text = stringResource(R.string.recovery_body),
            style = AppUi.typography.bodyMedium,
            color = AppUi.colors.textSecondary,
        )
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = AppDimension.Space.md),
            verticalArrangement = Arrangement.spacedBy(AppDimension.Space.sm),
        ) {
            AppButton.Primary(
                text = stringResource(R.string.recovery_update_app),
                onClick = onUpdateApp,
                size = AppButtonSize.LARGE,
                modifier = Modifier.fillMaxWidth(),
            )
            AppButton.Tertiary(
                text = stringResource(R.string.recovery_export_data),
                onClick = onExportRawData,
                size = AppButtonSize.LARGE,
                modifier = Modifier.fillMaxWidth(),
            )
            AppButton.Tertiary(
                text = stringResource(R.string.recovery_report_issue),
                onClick = onReportIssue,
                size = AppButtonSize.LARGE,
                modifier = Modifier.fillMaxWidth(),
            )
            AppButton.Tertiary(
                text = stringResource(R.string.recovery_export_diagnostics),
                onClick = onExportDiagnostics,
                size = AppButtonSize.LARGE,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Preview(name = "Light", showBackground = true)
@Composable
private fun RecoveryContentPreview() {
    AppTheme {
        RecoveryContent(
            onUpdateApp = {},
            onExportRawData = {},
            onReportIssue = {},
            onExportDiagnostics = {},
        )
    }
}
