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
                AppDialogHostContent(current = null, onDismiss = {})
            }
        }
        composeTestRule.onAllNodesWithText("Restore complete").assertCountEquals(0)
        composeTestRule.onAllNodesWithText("Restore failed").assertCountEquals(0)
    }

    @Test
    fun restoreSuccessRendersWithTitle() {
        composeTestRule.setContent {
            AppTheme {
                AppDialogHostContent(
                    current = AppDialog.RestoreSuccess(
                        restoredAtEpochMs = 1_700_000_000_000L,
                        previousVersionAvailable = true,
                    ),
                    onDismiss = {},
                )
            }
        }
        composeTestRule.onNodeWithText("Restore complete").assertIsDisplayed()
        composeTestRule.onNodeWithText("OK").assertIsDisplayed()
    }

    @Test
    fun restoreFailureRendersAndInvokesDismissOnConfirm() {
        val captured = mutableStateOf<AppDialog?>(null)
        val variant = AppDialog.RestoreFailure(reason = BackupErrorCode.MissingMigrationPath)
        composeTestRule.setContent {
            AppTheme {
                AppDialogHostContent(current = variant, onDismiss = { captured.value = it })
            }
        }
        composeTestRule.onNodeWithText("Restore failed").assertIsDisplayed()
        composeTestRule.onNodeWithText("OK").performClick()
        assertEquals(variant, captured.value)
    }

    @Test
    fun undoRestoreConfirmationRendersBothButtons() {
        composeTestRule.setContent {
            AppTheme {
                AppDialogHostContent(
                    current = AppDialog.UndoRestoreConfirmation(
                        originalDataDateEpochMs = 1_700_000_000_000L,
                    ),
                    onDismiss = {},
                )
            }
        }
        composeTestRule.onNodeWithText("Undo last restore?").assertIsDisplayed()
        composeTestRule.onNodeWithText("Undo restore").assertIsDisplayed()
        composeTestRule.onNodeWithText("Cancel").assertIsDisplayed()
    }

    @Test
    fun undoRestoreSuccessRendersTitle() {
        composeTestRule.setContent {
            AppTheme {
                AppDialogHostContent(current = AppDialog.UndoRestoreSuccess, onDismiss = {})
            }
        }
        composeTestRule.onNodeWithText("Reverted").assertIsDisplayed()
    }
}
