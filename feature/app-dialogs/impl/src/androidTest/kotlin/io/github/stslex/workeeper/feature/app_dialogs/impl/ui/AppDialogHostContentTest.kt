// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.app_dialogs.impl.ui

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.stslex.workeeper.core.data.backup.api.scheduling.BackupErrorCode
import io.github.stslex.workeeper.core.ui.kit.theme.AppTheme
import io.github.stslex.workeeper.feature.app_dialogs.api.model.AppDialog
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AppDialogHostContentTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun nullCurrentRendersNothing() {
        composeTestRule.setContent {
            AppTheme {
                AppDialogHostContent(
                    current = null,
                    onDismiss = {},
                    onUndoRestoreRequest = {},
                    onConfirmUndo = {},
                    onReport = {},
                    onExportDiagnostics = {},
                )
            }
        }
        composeTestRule.onAllNodesWithText("Restore complete").assertCountEquals(0)
        composeTestRule.onAllNodesWithText("Restore failed").assertCountEquals(0)
    }

    @Test
    fun restoreSuccessRendersWithTitleAndUndoActionWhenPreviousAvailable() {
        composeTestRule.setContent {
            AppTheme {
                AppDialogHostContent(
                    current = AppDialog.RestoreSuccess(
                        restoredAtEpochMs = 1_700_000_000_000L,
                        previousVersionAvailable = true,
                    ),
                    onDismiss = {},
                    onUndoRestoreRequest = {},
                    onConfirmUndo = {},
                    onReport = {},
                    onExportDiagnostics = {},
                )
            }
        }
        composeTestRule.onNodeWithText("Restore complete").assertIsDisplayed()
        composeTestRule.onNodeWithText("OK").assertIsDisplayed()
        composeTestRule.onNodeWithText("Undo restore").assertIsDisplayed()
    }

    @Test
    fun restoreSuccessHidesUndoActionWhenNoPreviousAvailable() {
        composeTestRule.setContent {
            AppTheme {
                AppDialogHostContent(
                    current = AppDialog.RestoreSuccess(
                        restoredAtEpochMs = 1_700_000_000_000L,
                        previousVersionAvailable = false,
                    ),
                    onDismiss = {},
                    onUndoRestoreRequest = {},
                    onConfirmUndo = {},
                    onReport = {},
                    onExportDiagnostics = {},
                )
            }
        }
        composeTestRule.onAllNodesWithText("Undo restore").assertCountEquals(0)
    }

    @Test
    fun restoreSuccessUndoTapInvokesOnUndoRestoreRequest() {
        val captured = mutableStateOf<AppDialog.RestoreSuccess?>(null)
        val variant = AppDialog.RestoreSuccess(
            restoredAtEpochMs = 1_700_000_000_000L,
            previousVersionAvailable = true,
        )
        composeTestRule.setContent {
            AppTheme {
                AppDialogHostContent(
                    current = variant,
                    onDismiss = {},
                    onUndoRestoreRequest = { captured.value = it },
                    onConfirmUndo = {},
                    onReport = {},
                    onExportDiagnostics = {},
                )
            }
        }
        composeTestRule.onNodeWithText("Undo restore").performClick()
        assertEquals(variant, captured.value)
    }

    @Test
    fun restoreFailureRendersThreeButtonsAndInvokesDismissOnConfirm() {
        val capturedDismiss = mutableStateOf<AppDialog?>(null)
        val variant = AppDialog.RestoreFailure(reason = BackupErrorCode.MissingMigrationPath)
        composeTestRule.setContent {
            AppTheme {
                AppDialogHostContent(
                    current = variant,
                    onDismiss = { capturedDismiss.value = it },
                    onUndoRestoreRequest = {},
                    onConfirmUndo = {},
                    onReport = {},
                    onExportDiagnostics = {},
                )
            }
        }
        composeTestRule.onNodeWithText("Restore failed").assertIsDisplayed()
        composeTestRule.onNodeWithText("Report").assertIsDisplayed()
        composeTestRule.onNodeWithText("Export diagnostics").assertIsDisplayed()
        composeTestRule.onNodeWithText("OK").assertIsDisplayed().performClick()
        assertEquals(variant, capturedDismiss.value)
    }

    @Test
    fun restoreFailureReportTapInvokesOnReport() {
        val captured = mutableStateOf<AppDialog.RestoreFailure?>(null)
        val variant = AppDialog.RestoreFailure(reason = BackupErrorCode.Unknown)
        composeTestRule.setContent {
            AppTheme {
                AppDialogHostContent(
                    current = variant,
                    onDismiss = {},
                    onUndoRestoreRequest = {},
                    onConfirmUndo = {},
                    onReport = { captured.value = it },
                    onExportDiagnostics = {},
                )
            }
        }
        composeTestRule.onNodeWithText("Report").performClick()
        assertEquals(variant, captured.value)
    }

    @Test
    fun restoreFailureExportTapInvokesOnExportDiagnostics() {
        val captured = mutableStateOf<AppDialog.RestoreFailure?>(null)
        val variant = AppDialog.RestoreFailure(reason = BackupErrorCode.Unknown)
        composeTestRule.setContent {
            AppTheme {
                AppDialogHostContent(
                    current = variant,
                    onDismiss = {},
                    onUndoRestoreRequest = {},
                    onConfirmUndo = {},
                    onReport = {},
                    onExportDiagnostics = { captured.value = it },
                )
            }
        }
        composeTestRule.onNodeWithText("Export diagnostics").performClick()
        assertEquals(variant, captured.value)
    }

    @Test
    fun undoRestoreConfirmationRendersBothButtonsAndConfirmInvokesCallback() {
        val captured = mutableStateOf<AppDialog.UndoRestoreConfirmation?>(null)
        val variant = AppDialog.UndoRestoreConfirmation(originalDataDateEpochMs = 1_700_000_000_000L)
        composeTestRule.setContent {
            AppTheme {
                AppDialogHostContent(
                    current = variant,
                    onDismiss = {},
                    onUndoRestoreRequest = {},
                    onConfirmUndo = { captured.value = it },
                    onReport = {},
                    onExportDiagnostics = {},
                )
            }
        }
        composeTestRule.onNodeWithText("Undo last restore?").assertIsDisplayed()
        composeTestRule.onNodeWithText("Cancel").assertIsDisplayed()
        composeTestRule.onNodeWithText("Undo restore").assertIsDisplayed().performClick()
        assertEquals(variant, captured.value)
    }

    @Test
    fun undoRestoreSuccessRendersTitle() {
        composeTestRule.setContent {
            AppTheme {
                AppDialogHostContent(
                    current = AppDialog.UndoRestoreSuccess,
                    onDismiss = {},
                    onUndoRestoreRequest = {},
                    onConfirmUndo = {},
                    onReport = {},
                    onExportDiagnostics = {},
                )
            }
        }
        composeTestRule.onNodeWithText("Reverted").assertIsDisplayed()
    }
}
