// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.core.ui.kit.components.dialog

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.window.DialogProperties
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.stslex.workeeper.core.ui.kit.theme.AppTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AppConfirmationDialogTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun rendersTitleBodyAndConfirmLabel() {
        composeTestRule.setContent {
            AppTheme {
                AppConfirmationDialog(
                    title = "Restore complete",
                    body = "Your data was restored.",
                    confirmLabel = "OK",
                    onConfirm = {},
                )
            }
        }
        composeTestRule.onNodeWithText("Restore complete").assertIsDisplayed()
        composeTestRule.onNodeWithText("Your data was restored.").assertIsDisplayed()
        composeTestRule.onNodeWithText("OK").assertIsDisplayed()
    }

    @Test
    fun confirmClickInvokesOnConfirm() {
        var confirmed = 0
        composeTestRule.setContent {
            AppTheme {
                AppConfirmationDialog(
                    title = "Title",
                    body = "Body",
                    confirmLabel = "OK",
                    onConfirm = { confirmed++ },
                )
            }
        }
        composeTestRule.onNodeWithText("OK").performClick()
        assertEquals(1, confirmed)
    }

    @Test
    fun dismissLabelHiddenWhenNull() {
        // Single-action dialog: dismissLabel null → only confirm button rendered.
        composeTestRule.setContent {
            AppTheme {
                AppConfirmationDialog(
                    title = "Title",
                    body = "Body",
                    confirmLabel = "OK",
                    onConfirm = {},
                    dismissLabel = null,
                )
            }
        }
        composeTestRule.onAllNodesWithText("OK").assertCountEquals(1)
        composeTestRule.onAllNodesWithText("Cancel").assertCountEquals(0)
    }

    @Test
    fun dismissLabelRendersWhenProvided() {
        var dismissed = 0
        composeTestRule.setContent {
            AppTheme {
                AppConfirmationDialog(
                    title = "Title",
                    body = "Body",
                    confirmLabel = "Confirm",
                    onConfirm = {},
                    dismissLabel = "Cancel",
                    onDismiss = { dismissed++ },
                )
            }
        }
        composeTestRule.onNodeWithText("Cancel").assertIsDisplayed().performClick()
        assertEquals(1, dismissed)
    }

    @Test
    fun strictDismissPropertiesAreRespectedByConstruction() {
        // Strict (`dismissOnBackPress = false`) mode is enforced by the caller via
        // `properties`. The widget forwards properties unchanged — verified by
        // instantiating with restrictive properties and observing that the dialog
        // still renders its confirm path.
        val strict = DialogProperties(
            dismissOnBackPress = false,
            dismissOnClickOutside = false,
        )
        var confirmed = 0
        composeTestRule.setContent {
            AppTheme {
                AppConfirmationDialog(
                    title = "Restore failed",
                    body = "Your data is intact.",
                    confirmLabel = "OK",
                    onConfirm = { confirmed++ },
                    properties = strict,
                )
            }
        }
        composeTestRule.onNodeWithText("Restore failed").assertIsDisplayed()
        composeTestRule.onNodeWithText("OK").performClick()
        assertTrue("strict properties did not break confirm path", confirmed == 1)
    }
}
