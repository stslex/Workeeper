// SPDX-License-Identifier: GPL-3.0-only
@file:OptIn(ExperimentalTestApi::class)

package io.github.stslex.workeeper.feature.app_dialogs.impl

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import io.github.stslex.workeeper.core.core.resources.ResourceWrapper
import io.github.stslex.workeeper.core.data.backup.api.restore.RestoreOwnerId
import io.github.stslex.workeeper.core.data.backup.api.restore.UndoRef
import io.github.stslex.workeeper.core.data.backup.api.scheduling.BackupErrorCode
import io.github.stslex.workeeper.core.ui.kit.strings.ComposeStringResolver
import io.github.stslex.workeeper.core.ui.kit.theme.AppTheme
import io.github.stslex.workeeper.feature.app_dialogs.api.model.AppDialog
import io.github.stslex.workeeper.feature.app_dialogs.api.model.AppDialogUserAction
import io.github.stslex.workeeper.feature.app_dialogs.api.model.AppDialogUserChoice
import io.github.stslex.workeeper.feature.app_dialogs.impl.mvi.mapper.toDateLabel
import io.github.stslex.workeeper.feature.app_dialogs.impl.mvi.store.AppDialogStore.State
import io.github.stslex.workeeper.feature.app_dialogs.impl.resources.Res
import io.github.stslex.workeeper.feature.app_dialogs.impl.resources.app_dialog_restore_failure_body
import io.github.stslex.workeeper.feature.app_dialogs.impl.resources.app_dialog_restore_failure_confirm
import io.github.stslex.workeeper.feature.app_dialogs.impl.resources.app_dialog_restore_failure_export_action
import io.github.stslex.workeeper.feature.app_dialogs.impl.resources.app_dialog_restore_failure_reason_auth_revoked
import io.github.stslex.workeeper.feature.app_dialogs.impl.resources.app_dialog_restore_failure_reason_corrupted_backup
import io.github.stslex.workeeper.feature.app_dialogs.impl.resources.app_dialog_restore_failure_reason_io
import io.github.stslex.workeeper.feature.app_dialogs.impl.resources.app_dialog_restore_failure_reason_missing_migration_path
import io.github.stslex.workeeper.feature.app_dialogs.impl.resources.app_dialog_restore_failure_reason_missing_required_scope
import io.github.stslex.workeeper.feature.app_dialogs.impl.resources.app_dialog_restore_failure_reason_network_unavailable
import io.github.stslex.workeeper.feature.app_dialogs.impl.resources.app_dialog_restore_failure_reason_not_authenticated
import io.github.stslex.workeeper.feature.app_dialogs.impl.resources.app_dialog_restore_failure_reason_schema_too_new
import io.github.stslex.workeeper.feature.app_dialogs.impl.resources.app_dialog_restore_failure_reason_storage_quota_exceeded
import io.github.stslex.workeeper.feature.app_dialogs.impl.resources.app_dialog_restore_failure_reason_unknown
import io.github.stslex.workeeper.feature.app_dialogs.impl.resources.app_dialog_restore_failure_report_action
import io.github.stslex.workeeper.feature.app_dialogs.impl.resources.app_dialog_restore_failure_title
import io.github.stslex.workeeper.feature.app_dialogs.impl.resources.app_dialog_restore_success_body
import io.github.stslex.workeeper.feature.app_dialogs.impl.resources.app_dialog_restore_success_body_no_previous
import io.github.stslex.workeeper.feature.app_dialogs.impl.resources.app_dialog_restore_success_confirm
import io.github.stslex.workeeper.feature.app_dialogs.impl.resources.app_dialog_restore_success_title
import io.github.stslex.workeeper.feature.app_dialogs.impl.resources.app_dialog_restore_success_undo_action
import io.github.stslex.workeeper.feature.app_dialogs.impl.resources.app_dialog_undo_restore_confirmation_body
import io.github.stslex.workeeper.feature.app_dialogs.impl.resources.app_dialog_undo_restore_confirmation_cancel
import io.github.stslex.workeeper.feature.app_dialogs.impl.resources.app_dialog_undo_restore_confirmation_confirm
import io.github.stslex.workeeper.feature.app_dialogs.impl.resources.app_dialog_undo_restore_confirmation_title
import io.github.stslex.workeeper.feature.app_dialogs.impl.resources.app_dialog_undo_restore_success_body
import io.github.stslex.workeeper.feature.app_dialogs.impl.resources.app_dialog_undo_restore_success_confirm
import io.github.stslex.workeeper.feature.app_dialogs.impl.resources.app_dialog_undo_restore_success_title
import io.github.stslex.workeeper.feature.app_dialogs.impl.ui.AppDialogHostContent
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * S8: the dialog catalog on the iOS simulator. The production renderer and date mapper run from
 * test-held State, as `AppDialogHost` feeds them from the Store; every choice is captured.
 */
class AppDialogFeatureSceneIosTest {

    /** Every read outside composition goes through this one real resolver: no fake here. */
    private val resolver = ComposeStringResolver()

    /**
     * ResourceWrapper has no iOS implementation, so the dialog dates are stubbed — the scene's
     * only test double. It answers the two fixture dates and throws on every other call.
     */
    private val dateStub = object : ResourceWrapper {
        override fun getString(id: Int, vararg args: Any): String =
            error("the scene reads no copy through ResourceWrapper")

        override fun getQuantityString(id: Int, quantity: Int, vararg args: Any): String =
            error("the scene reads no plural through ResourceWrapper")

        override fun getAbbreviatedRelativeTime(timestamp: Long, now: Long): String =
            error("the scene reads no relative time")

        override fun formatDayMonth(timestamp: Long): String = error("the scene reads no day-month date")

        override fun formatMediumDate(timestamp: Long): String = when (timestamp) {
            RESTORED_AT -> "Nov 14, 2023"
            ORIGINAL_DATA_AT -> "Apr 15, 2022"
            else -> error("Unexpected timestamp: $timestamp")
        }
    }

    @Test
    fun resourcesDatesVariantsAndChoicesRenderAndDispatch() = runComposeUiTest {
        // 1. The private catalog in file order through the real resolver: as stored, then every
        // placeholder-bearing entry filled.
        assertEquals(EXPECTED_STORED, storedCatalog())
        assertEquals(EXPECTED_FILLED, filledCatalog())

        // 2. D3's date through the production mapper, stubbed date: two dated variants, four not.
        val withPrevious = AppDialog.RestoreSuccess(
            restoredAtEpochMs = RESTORED_AT,
            previousVersionAvailable = true,
        )
        val withoutPrevious = withPrevious.copy(previousVersionAvailable = false)
        val confirmation = AppDialog.UndoRestoreConfirmation(
            undoRef = UndoRef(RestoreOwnerId("00000000-0000-4000-8000-000000000011")),
            originalDataDateEpochMs = ORIGINAL_DATA_AT,
        )
        val undoSuccess = AppDialog.UndoRestoreSuccess()
        val failure = AppDialog.RestoreFailure(reason = BackupErrorCode.Unknown)
        assertEquals(
            listOf("Nov 14, 2023", "Nov 14, 2023", "Apr 15, 2022", "", "", ""),
            listOf(withPrevious, withoutPrevious, confirmation, failure, undoSuccess, null)
                .map { it.toDateLabel(dateStub) },
        )

        val state = mutableStateOf(State.EMPTY)
        val choices = mutableListOf<AppDialogUserChoice>()
        fun show(dialog: AppDialog?) {
            state.value = State(current = dialog, dateLabel = dialog.toDateLabel(dateStub))
        }

        setContent {
            AppTheme {
                AppDialogHostContent(
                    current = state.value.current,
                    dateLabel = state.value.dateLabel,
                    onChoice = choices::add,
                )
            }
        }

        // 3a. No dialog: nothing renders.
        settleScene()
        listOf("Restore complete", "Restore failed", "Undo last restore?", "Reverted").forEach { title ->
            onNodeWithText(title, useUnmergedTree = true).assertDoesNotExist()
        }

        // 3b. A restore with a previous version: the dated body and both buttons.
        show(withPrevious)
        settleUntil(hasText("Restore complete"))
        onNodeWithText(SUCCESS_BODY_FILLED, useUnmergedTree = true).assertIsDisplayed()
        onNodeWithText("Undo restore").performClick()
        onNodeWithText("OK").performClick()

        // 3c. Without one: the short dated body, and OK alone.
        show(withoutPrevious)
        settleUntil(hasText("Your data was restored from a backup dated Nov 14, 2023."))
        onNodeWithText("Undo restore").assertDoesNotExist()
        onNodeWithText("OK").performClick()

        // 3d. A failure for each of the twelve codes, its reason in the body (S4); no two in a row
        // share a reason, so each step renders new text.
        assertEquals(BackupErrorCode.entries.toSet(), REASONS.map { it.first }.toSet())
        REASONS.forEach { (code, reason) ->
            show(AppDialog.RestoreFailure(reason = code))
            val body = "Your data is intact. Reason: $reason. Please report this issue so we can fix it."
            settleUntil(hasText(body))
            onNodeWithText(body, useUnmergedTree = true).assertIsDisplayed()
        }
        onNodeWithText("Restore failed", useUnmergedTree = true).assertIsDisplayed()
        onNodeWithText("Export diagnostics").performClick()
        onNodeWithText("Report").performClick()
        onNodeWithText("OK").performClick()

        // 3e. The undo confirmation: the brought-back data's date, and both buttons.
        show(confirmation)
        settleUntil(hasText("Undo last restore?"))
        onNodeWithText(
            "Your data will revert to the state it was in on Apr 15, 2022. This cannot be undone.",
            useUnmergedTree = true,
        ).assertIsDisplayed()
        onNodeWithText("Undo restore").performClick()
        onNodeWithText("Cancel").performClick()

        // 3f. The undo success: its body and OK.
        show(undoSuccess)
        settleUntil(hasText("Reverted"))
        onNodeWithText("Your data has been restored to the previous state.", useUnmergedTree = true)
            .assertIsDisplayed()
        onNodeWithText("OK").performClick()

        // 4. Dispatch: the exact list, in order.
        assertEquals(
            listOf(
                AppDialogUserChoice(withPrevious, AppDialogUserAction.RequestUndo),
                AppDialogUserChoice(withPrevious, AppDialogUserAction.Acknowledge),
                AppDialogUserChoice(withoutPrevious, AppDialogUserAction.Acknowledge),
                AppDialogUserChoice(failure, AppDialogUserAction.ExportDiagnostics),
                AppDialogUserChoice(failure, AppDialogUserAction.Report),
                AppDialogUserChoice(failure, AppDialogUserAction.Acknowledge),
                AppDialogUserChoice(confirmation, AppDialogUserAction.ConfirmUndo),
                AppDialogUserChoice(confirmation, AppDialogUserAction.Cancel),
                AppDialogUserChoice(undoSuccess, AppDialogUserAction.Acknowledge),
            ),
            choices,
        )
    }

    /** The catalog in file order, each read with no format arguments (the template verbatim). */
    private fun storedCatalog(): List<String> = runBlocking {
        listOf(
            resolver.getString(Res.string.app_dialog_restore_success_title),
            resolver.getString(Res.string.app_dialog_restore_success_body),
            resolver.getString(Res.string.app_dialog_restore_success_body_no_previous),
            resolver.getString(Res.string.app_dialog_restore_success_confirm),
            resolver.getString(Res.string.app_dialog_restore_success_undo_action),
            resolver.getString(Res.string.app_dialog_restore_failure_title),
            resolver.getString(Res.string.app_dialog_restore_failure_body),
            resolver.getString(Res.string.app_dialog_restore_failure_confirm),
            resolver.getString(Res.string.app_dialog_restore_failure_report_action),
            resolver.getString(Res.string.app_dialog_restore_failure_export_action),
            resolver.getString(Res.string.app_dialog_undo_restore_confirmation_title),
            resolver.getString(Res.string.app_dialog_undo_restore_confirmation_body),
            resolver.getString(Res.string.app_dialog_undo_restore_confirmation_confirm),
            resolver.getString(Res.string.app_dialog_undo_restore_confirmation_cancel),
            resolver.getString(Res.string.app_dialog_undo_restore_success_title),
            resolver.getString(Res.string.app_dialog_undo_restore_success_body),
            resolver.getString(Res.string.app_dialog_undo_restore_success_confirm),
            resolver.getString(Res.string.app_dialog_restore_failure_reason_not_authenticated),
            resolver.getString(Res.string.app_dialog_restore_failure_reason_network_unavailable),
            resolver.getString(Res.string.app_dialog_restore_failure_reason_auth_revoked),
            resolver.getString(Res.string.app_dialog_restore_failure_reason_missing_required_scope),
            resolver.getString(Res.string.app_dialog_restore_failure_reason_storage_quota_exceeded),
            resolver.getString(Res.string.app_dialog_restore_failure_reason_corrupted_backup),
            resolver.getString(Res.string.app_dialog_restore_failure_reason_schema_too_new),
            resolver.getString(Res.string.app_dialog_restore_failure_reason_missing_migration_path),
            resolver.getString(Res.string.app_dialog_restore_failure_reason_io),
            resolver.getString(Res.string.app_dialog_restore_failure_reason_unknown),
        )
    }

    /** Every placeholder-bearing entry, filled with the scene's arguments. */
    private fun filledCatalog(): List<String> = runBlocking {
        listOf(
            resolver.getString(Res.string.app_dialog_restore_success_body, "Nov 14, 2023"),
            resolver.getString(Res.string.app_dialog_restore_success_body_no_previous, "Nov 14, 2023"),
            resolver.getString(Res.string.app_dialog_restore_failure_body, "no network"),
            resolver.getString(Res.string.app_dialog_undo_restore_confirmation_body, "Apr 15, 2022"),
        )
    }

    private fun ComposeUiTest.settleScene() {
        mainClock.autoAdvance = false
        mainClock.advanceTimeBy(1_000)
        mainClock.autoAdvance = true
        waitForIdle()
    }

    /** Settles in bounded steps until a node matching the next state exists. */
    private fun ComposeUiTest.settleUntil(matcher: SemanticsMatcher) {
        repeat(MAX_SETTLE_STEPS) {
            settleScene()
            if (onAllNodes(matcher, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()) {
                return
            }
        }
    }

    private companion object {

        const val RESTORED_AT = 1_700_000_000_000L
        const val ORIGINAL_DATA_AT = 1_650_000_000_000L

        /** Ten virtual seconds: far beyond every enter animation of a dialog. */
        const val MAX_SETTLE_STEPS = 10

        const val SUCCESS_BODY_TEMPLATE = "Your data was restored from a backup dated %1\$s. " +
            "You can revert to your previous data in Settings if needed."
        const val SUCCESS_BODY_FILLED = "Your data was restored from a backup dated Nov 14, 2023. " +
            "You can revert to your previous data in Settings if needed."

        /** The twelve codes onto their EN reasons, ordered so no two neighbours share one. */
        val REASONS = listOf(
            BackupErrorCode.NotAuthenticated to "not signed in",
            BackupErrorCode.NetworkUnavailable to "no network",
            BackupErrorCode.AuthRevoked to "Google account access was revoked",
            BackupErrorCode.MissingRequiredScope to "Drive access was not granted",
            BackupErrorCode.StorageQuotaExceeded to "Drive storage is full",
            BackupErrorCode.InsufficientLocalStorage to "a read or write error occurred",
            BackupErrorCode.CorruptedBackup to "the backup file is corrupted",
            BackupErrorCode.StorageCapacityUnavailable to "a read or write error occurred",
            BackupErrorCode.SchemaTooNew to "the backup was created by a newer app version",
            BackupErrorCode.Io to "a read or write error occurred",
            BackupErrorCode.MissingMigrationPath to "no migration path to this app version",
            BackupErrorCode.Unknown to "an unexpected error occurred",
        )

        /** The EN catalog in file order, as stored. */
        val EXPECTED_STORED = listOf(
            "Restore complete",
            SUCCESS_BODY_TEMPLATE,
            "Your data was restored from a backup dated %1\$s.",
            "OK",
            "Undo restore",
            "Restore failed",
            "Your data is intact. Reason: %1\$s. Please report this issue so we can fix it.",
            "OK",
            "Report",
            "Export diagnostics",
            "Undo last restore?",
            "Your data will revert to the state it was in on %1\$s. This cannot be undone.",
            "Undo restore",
            "Cancel",
            "Reverted",
            "Your data has been restored to the previous state.",
            "OK",
            "not signed in",
            "no network",
            "Google account access was revoked",
            "Drive access was not granted",
            "Drive storage is full",
            "the backup file is corrupted",
            "the backup was created by a newer app version",
            "no migration path to this app version",
            "a read or write error occurred",
            "an unexpected error occurred",
        )

        /** The placeholder-bearing entries of [EXPECTED_STORED], filled by the real formatter. */
        val EXPECTED_FILLED = listOf(
            SUCCESS_BODY_FILLED,
            "Your data was restored from a backup dated Nov 14, 2023.",
            "Your data is intact. Reason: no network. Please report this issue so we can fix it.",
            "Your data will revert to the state it was in on Apr 15, 2022. This cannot be undone.",
        )
    }
}
