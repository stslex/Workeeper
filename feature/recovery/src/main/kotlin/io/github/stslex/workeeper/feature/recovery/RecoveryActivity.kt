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
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.core.content.FileProvider
import androidx.core.net.toUri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import io.github.stslex.workeeper.core.ui.kit.components.button.AppButton
import io.github.stslex.workeeper.core.ui.kit.components.button.AppButtonSize
import io.github.stslex.workeeper.core.ui.kit.components.dialog.AppConfirmationDialog
import io.github.stslex.workeeper.core.ui.kit.theme.AppDimension
import io.github.stslex.workeeper.core.ui.kit.theme.AppTheme
import io.github.stslex.workeeper.core.ui.kit.theme.AppUi
import io.github.stslex.workeeper.feature.recovery.di.RecoveryDeps
import io.github.stslex.workeeper.feature.recovery.di.RecoveryDepsHolder
import io.github.stslex.workeeper.feature.recovery.diagnostics.RestoreDiagnosticsExport
import kotlinx.coroutines.CancellationException
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

    private val diagnosticsExporter get() = deps.recoveryDiagnosticsExporter

    private val restoreDiagnosticsExport: RestoreDiagnosticsExport
        get() = deps.restoreDiagnosticsExport

    private lateinit var model: RecoveryActivityModel

    /** Test-only seam: forces resolution of every lazily-resolved app-graph collaborator. */
    @VisibleForTesting
    fun warmDeps(): List<Any> =
        listOf(
            deps.restoreRecoveryFiles,
            deps.restoreStateRepository,
            deps.interruptedRestoreChecker,
            deps.appReinitializer,
            deps.startupMigrationCoordinator,
            diagnosticsExporter,
            restoreDiagnosticsExport,
        )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val scenario = RecoveryScenario.fromIntent(intent)
        val allowContinue = RecoveryScenario.allowsContinue(intent)
        model = ViewModelProvider(
            this,
            RecoveryActivityModelFactory(
                scenario = scenario,
                allowContinue = allowContinue,
                deps = deps,
            ),
        )[RecoveryActivityModel::class.java]
        setContent {
            val state by model.state.collectAsStateWithLifecycle()
            AppTheme {
                RecoveryContent(
                    scenario = scenario,
                    state = state,
                    onUpdateApp = ::openPlayStore,
                    onExportRawData = ::exportRawData,
                    onReportIssue = { openGitHubIssue(scenario) },
                    onExportDiagnostics = { exportDiagnostics(scenario) },
                    onRequestContinue = ::requestContinue,
                    onConfirmContinue = ::confirmContinue,
                    onDismissContinue = model::dismissContinueConfirmation,
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
        lifecycleScope.launch {
            val prepared = model.prepareRawExport()
            if (prepared !is RawExportPreparation.Ready) return@launch
            val shared = runCatching {
                shareFile(
                    uri = fileProviderUriFor(prepared.file),
                    mimeType = MIME_RAW_DATA,
                    chooserTitleRes = R.string.recovery_export_data_chooser,
                )
            }.getOrDefault(false)
            if (!shared) model.markRawExportShareFailed()
        }
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
            val uri = try {
                when (scenario) {
                    RecoveryScenario.StartupMigration ->
                        diagnosticsExporter.exportStartupMigrationFailure()

                    RecoveryScenario.InterruptedRestore -> restoreDiagnosticsExport.export()
                }
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (_: Exception) {
                null
            }
            if (uri == null) {
                model.markDiagnosticsFailed()
                return@launch
            }
            val shared = runCatching {
                shareFile(
                    uri = uri,
                    mimeType = MIME_DIAGNOSTICS,
                    chooserTitleRes = R.string.recovery_export_diagnostics_chooser,
                )
            }.getOrDefault(false)
            if (shared) model.markDiagnosticsReady() else model.markDiagnosticsFailed()
        }
    }

    private fun requestContinue() {
        lifecycleScope.launch { model.requestContinue() }
    }

    private fun confirmContinue() {
        lifecycleScope.launch { model.confirmContinue() }
    }

    private fun fileProviderUriFor(file: File): Uri =
        FileProvider.getUriForFile(this, "$packageName.fileprovider", file)

    private fun shareFile(uri: Uri, mimeType: String, chooserTitleRes: Int): Boolean {
        val send = Intent(Intent.ACTION_SEND).apply {
            type = mimeType
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        val chooser = Intent
            .createChooser(send, getString(chooserTitleRes))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return try {
            startActivity(chooser)
            true
        } catch (_: ActivityNotFoundException) {
            false
        }
    }

    private companion object {
        const val GITHUB_ISSUE_BASE_URL = "https://github.com/stslex/Workeeper/issues/new"
        const val GITHUB_ISSUE_LABELS = "bug,migration"
        const val MIME_RAW_DATA = "application/octet-stream"
        const val MIME_DIAGNOSTICS = "text/plain"
    }
}

private class RecoveryActivityModelFactory(
    private val scenario: RecoveryScenario,
    private val allowContinue: Boolean,
    private val deps: RecoveryDeps,
) : ViewModelProvider.Factory {

    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        require(modelClass == RecoveryActivityModel::class.java)
        val model = RecoveryActivityModel(
            scenario = scenario,
            allowContinue = allowContinue,
            checker = deps.interruptedRestoreChecker,
            recoveryFiles = deps.restoreRecoveryFiles,
            restoreStateRepository = deps.restoreStateRepository,
            appReinitializer = deps.appReinitializer,
            recoveryExportOutcome = when (scenario) {
                RecoveryScenario.InterruptedRestore ->
                    deps.restoreRecoveryCoordinator.lastRecoveryExportOutcome

                RecoveryScenario.StartupMigration ->
                    deps.startupMigrationCoordinator.lastRecoveryExportOutcome
            },
        )
        return modelClass.cast(model)
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
    state: RecoveryUiState,
    onUpdateApp: () -> Unit,
    onExportRawData: () -> Unit,
    onReportIssue: () -> Unit,
    onExportDiagnostics: () -> Unit,
    onRequestContinue: () -> Unit,
    onConfirmContinue: () -> Unit,
    onDismissContinue: () -> Unit,
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
            } else if (state.continueState != ContinueState.Hidden) {
                val continueEnabled = state.continueState is ContinueState.Ready ||
                    state.continueState is ContinueState.Failed
                val continueLabel = if (state.continueState is ContinueState.Checking) {
                    R.string.recovery_continue_checking
                } else {
                    R.string.recovery_continue
                }
                AppButton.Primary(
                    text = stringResource(continueLabel),
                    onClick = onRequestContinue,
                    enabled = continueEnabled,
                    size = AppButtonSize.LARGE,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            AppButton.Tertiary(
                text = stringResource(R.string.recovery_export_data),
                onClick = onExportRawData,
                enabled = state.rawExportState.canRequestShare(),
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
        rawExportMessageRes(state.rawExportState)?.let { messageRes ->
            RecoveryStatusText(stringResource(messageRes))
        }
        continueMessageRes(state.continueState)?.let { messageRes ->
            RecoveryStatusText(stringResource(messageRes))
        }
        if (state.diagnosticsState == DiagnosticsState.Failed) {
            RecoveryStatusText(stringResource(R.string.recovery_diagnostics_failed))
        }
    }

    when (state.dialogState) {
        DialogState.Hidden -> Unit
        is DialogState.ContinueConfirmation -> AppConfirmationDialog(
            title = stringResource(R.string.recovery_continue_confirmation_title),
            body = stringResource(R.string.recovery_continue_confirmation_body),
            confirmLabel = stringResource(R.string.recovery_continue_confirmation_confirm),
            onConfirm = onConfirmContinue,
            dismissLabel = stringResource(R.string.recovery_continue_confirmation_cancel),
            onDismiss = onDismissContinue,
            isDestructive = true,
        )
    }
}

@Composable
private fun RecoveryStatusText(text: String) {
    Text(
        text = text,
        style = AppUi.typography.bodyMedium,
        color = AppUi.colors.status.error,
    )
}

private fun rawExportMessageRes(state: RawExportState): Int? = when (state) {
    RawExportState.Available -> null
    is RawExportState.Unavailable -> R.string.recovery_export_data_unavailable
    is RawExportState.Failed -> R.string.recovery_export_data_failed
}

private fun RawExportState.canRequestShare(): Boolean = when (this) {
    RawExportState.Available -> true
    is RawExportState.Unavailable -> false
    is RawExportState.Failed -> reason != RawExportState.FailureReason.RecoveryExportCreationFailed
}

private fun continueMessageRes(state: ContinueState): Int? = when (state) {
    ContinueState.Hidden,
    ContinueState.Ready,
    ContinueState.Checking,
    ContinueState.Restarting,
    -> null

    is ContinueState.Failed -> when (state.reason) {
        ContinueState.FailureReason.NoOwnedInterruptedRestore,
        ContinueState.FailureReason.OwnerChanged,
        -> R.string.recovery_continue_unavailable

        ContinueState.FailureReason.LiveDatabaseMissing ->
            R.string.recovery_continue_database_missing

        ContinueState.FailureReason.IntegrityCheckFailed ->
            R.string.recovery_continue_integrity_failed

        ContinueState.FailureReason.UnsupportedSchema ->
            R.string.recovery_continue_schema_unsupported

        ContinueState.FailureReason.CheckFailed ->
            R.string.recovery_continue_check_failed

        ContinueState.FailureReason.AbandonFailed ->
            R.string.recovery_continue_abandon_failed
    }
}

@Preview(name = "Startup migration", showBackground = true)
@Composable
private fun RecoveryContentPreview() {
    AppTheme {
        RecoveryContent(
            scenario = RecoveryScenario.StartupMigration,
            state = previewState(RecoveryScenario.StartupMigration),
            onUpdateApp = {},
            onExportRawData = {},
            onReportIssue = {},
            onExportDiagnostics = {},
            onRequestContinue = {},
            onConfirmContinue = {},
            onDismissContinue = {},
        )
    }
}

@Preview(name = "Interrupted restore", showBackground = true)
@Composable
private fun RecoveryContentRestorePreview() {
    AppTheme {
        RecoveryContent(
            scenario = RecoveryScenario.InterruptedRestore,
            state = previewState(RecoveryScenario.InterruptedRestore),
            onUpdateApp = {},
            onExportRawData = {},
            onReportIssue = {},
            onExportDiagnostics = {},
            onRequestContinue = {},
            onConfirmContinue = {},
            onDismissContinue = {},
        )
    }
}

private fun previewState(scenario: RecoveryScenario): RecoveryUiState = RecoveryUiState(
    rawExportState = RawExportState.Available,
    continueState = if (scenario == RecoveryScenario.InterruptedRestore) {
        ContinueState.Ready
    } else {
        ContinueState.Hidden
    },
)
