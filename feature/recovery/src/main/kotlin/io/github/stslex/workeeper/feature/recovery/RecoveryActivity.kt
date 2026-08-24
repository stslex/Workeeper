// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.recovery

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.annotation.VisibleForTesting
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
import io.github.stslex.workeeper.core.ui.kit.components.button.AppButton
import io.github.stslex.workeeper.core.ui.kit.components.button.AppButtonSize
import io.github.stslex.workeeper.core.ui.kit.theme.AppDimension
import io.github.stslex.workeeper.core.ui.kit.theme.AppTheme
import io.github.stslex.workeeper.core.ui.kit.theme.AppUi
import io.github.stslex.workeeper.feature.recovery.di.RecoveryDeps
import io.github.stslex.workeeper.feature.recovery.di.RecoveryDepsHolder
import io.github.stslex.workeeper.feature.recovery.diagnostics.RestoreDiagnosticsExport
import kotlinx.coroutines.launch
import java.io.File

/**
 * Room-free fallback launcher for Scenario 2 (startup migration failure) and for a Scenario-1
 * restore attempt whose outcome is not provable. [RecoveryScenario] — stamped on the launching
 * Intent — selects the copy and the diagnostics format; the two failures need different both.
 * See documentation/feature-specs/backup-recovery.md.
 */
// GUARD: call only file-path / pure-Kotlin collaborator methods here; opening a SQLiteConnection
// triggers the very migration this screen exists to avoid.
class RecoveryActivity : ComponentActivity() {

    private val deps: RecoveryDeps by lazy {
        (applicationContext as RecoveryDepsHolder).recoveryDeps()
    }

    private val snapshotProvider get() = deps.databaseSnapshotProvider

    private val diagnosticsExporter get() = deps.recoveryDiagnosticsExporter

    private val restoreDiagnosticsExport: RestoreDiagnosticsExport
        get() = deps.restoreDiagnosticsExport

    /** Test-only seam: forces resolution of every lazily-resolved app-graph collaborator. */
    @VisibleForTesting
    fun warmDeps(): List<Any> =
        listOf(snapshotProvider, diagnosticsExporter, restoreDiagnosticsExport)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val scenario = RecoveryScenario.fromIntent(intent)
        setContent {
            AppTheme {
                RecoveryContent(
                    scenario = scenario,
                    onUpdateApp = ::openPlayStore,
                    onExportRawData = ::exportRawData,
                    onReportIssue = { openGitHubIssue(scenario) },
                    onExportDiagnostics = { exportDiagnostics(scenario) },
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

    private fun openGitHubIssue(scenario: RecoveryScenario) {
        val title = Uri.encode(getString(scenario.reportTitleRes))
        val labels = Uri.encode(GITHUB_ISSUE_LABELS)
        val url = "$GITHUB_ISSUE_BASE_URL?title=$title&labels=$labels"
        runCatching {
            startActivity(
                Intent(Intent.ACTION_VIEW, url.toUri())
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
        }
    }

    /**
     * The Scenario-1 export carries the interrupted restore's journalled manifest context; the
     * Scenario-2 one reads its own version + install source. Sharing the wrong one hands the
     * user a file with none of the facts their failure needs.
     */
    private fun exportDiagnostics(scenario: RecoveryScenario) {
        lifecycleScope.launch {
            val uri = when (scenario) {
                RecoveryScenario.StartupMigration ->
                    diagnosticsExporter.exportStartupMigrationFailure()

                RecoveryScenario.InterruptedRestore -> restoreDiagnosticsExport.export()
            } ?: return@launch
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
                // Non-fatal — no installed app handles the MIME type.
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

/** Title/body/report-title copy per scenario; the enum keeps the three in step. */
internal val RecoveryScenario.titleRes: Int
    get() = when (this) {
        RecoveryScenario.StartupMigration -> R.string.recovery_title
        RecoveryScenario.InterruptedRestore -> R.string.recovery_restore_title
    }

internal val RecoveryScenario.bodyRes: Int
    get() = when (this) {
        RecoveryScenario.StartupMigration -> R.string.recovery_body
        RecoveryScenario.InterruptedRestore -> R.string.recovery_restore_body
    }

internal val RecoveryScenario.reportTitleRes: Int
    get() = when (this) {
        RecoveryScenario.StartupMigration -> R.string.recovery_report_title
        RecoveryScenario.InterruptedRestore -> R.string.recovery_restore_report_title
    }

@Composable
internal fun RecoveryContent(
    scenario: RecoveryScenario,
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
            text = stringResource(scenario.titleRes),
            style = AppUi.typography.titleLarge,
            color = AppUi.colors.textPrimary,
        )
        Text(
            text = stringResource(scenario.bodyRes),
            style = AppUi.typography.bodyMedium,
            color = AppUi.colors.textSecondary,
        )
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = AppDimension.Space.md),
            verticalArrangement = Arrangement.spacedBy(AppDimension.Space.sm),
        ) {
            // GUARD: no "Update app" on an interrupted restore — no update exists and none
            // would help; the Play Store is a dead end for that failure.
            if (scenario == RecoveryScenario.StartupMigration) {
                AppButton.Primary(
                    text = stringResource(R.string.recovery_update_app),
                    onClick = onUpdateApp,
                    size = AppButtonSize.LARGE,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
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

@Preview(name = "Startup migration", showBackground = true)
@Composable
private fun RecoveryContentPreview() {
    AppTheme {
        RecoveryContent(
            scenario = RecoveryScenario.StartupMigration,
            onUpdateApp = {},
            onExportRawData = {},
            onReportIssue = {},
            onExportDiagnostics = {},
        )
    }
}

@Preview(name = "Interrupted restore", showBackground = true)
@Composable
private fun RecoveryContentRestorePreview() {
    AppTheme {
        RecoveryContent(
            scenario = RecoveryScenario.InterruptedRestore,
            onUpdateApp = {},
            onExportRawData = {},
            onReportIssue = {},
            onExportDiagnostics = {},
        )
    }
}
