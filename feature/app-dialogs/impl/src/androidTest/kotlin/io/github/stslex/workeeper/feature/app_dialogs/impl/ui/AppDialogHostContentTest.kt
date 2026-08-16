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
import io.github.stslex.workeeper.core.ui.test.annotations.Smoke
import io.github.stslex.workeeper.feature.app_dialogs.api.model.AppDialog
import io.github.stslex.workeeper.feature.app_dialogs.api.model.AppDialogUserAction
import io.github.stslex.workeeper.feature.app_dialogs.api.model.AppDialogUserChoice
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Pins the per-variant button → `AppDialogUserChoice` mapping. After the
 * BLOCKER 2 transient-signal refactor the host content takes a single
 * `onChoice: (AppDialogUserChoice) -> Unit` lambda; tests assert that each
 * button on each variant dispatches the correct `(dialog, action)` pair.
 *
 * `@Smoke` by the taxonomy: `createComposeRule` with state passed straight into the content, no
 * DI container, no database, no Activity (`documentation/testing.md` → "Categorization with
 * `@Smoke` and `@Regression`"). Two things must hold together for this annotation to select
 * anything: it is declared here, and `:core:ui:test-utils` stays on this module's androidTest
 * classpath — androidx.test silently DROPS ui_tests.yml's filter when it cannot load the
 * annotation class it names, which runs every test here in both suites. `detektAndroidTestSuite`
 * and `verifyInstrumentedSuiteClasspath` gate the two halves
 * (`documentation/feature-specs/kmp-phase-0-instrumented-filter.md` → "The gate").
 */
@Smoke
@RunWith(AndroidJUnit4::class)
class AppDialogHostContentTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun nullCurrentRendersNothing() {
        composeTestRule.setContent {
            AppTheme {
                AppDialogHostContent(current = null, onChoice = {})
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
                    onChoice = {},
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
                    onChoice = {},
                )
            }
        }
        composeTestRule.onAllNodesWithText("Undo restore").assertCountEquals(0)
    }

    @Test
    fun restoreSuccessUndoTapDispatchesRequestUndoChoice() {
        val captured = mutableStateOf<AppDialogUserChoice?>(null)
        val variant = AppDialog.RestoreSuccess(
            restoredAtEpochMs = 1_700_000_000_000L,
            previousVersionAvailable = true,
        )
        composeTestRule.setContent {
            AppTheme {
                AppDialogHostContent(
                    current = variant,
                    onChoice = { captured.value = it },
                )
            }
        }
        composeTestRule.onNodeWithText("Undo restore").performClick()
        assertEquals(AppDialogUserChoice(variant, AppDialogUserAction.RequestUndo), captured.value)
    }

    @Test
    fun restoreFailureRendersThreeButtonsAndOkDispatchesAcknowledge() {
        val captured = mutableStateOf<AppDialogUserChoice?>(null)
        val variant = AppDialog.RestoreFailure(reason = BackupErrorCode.MissingMigrationPath)
        composeTestRule.setContent {
            AppTheme {
                AppDialogHostContent(
                    current = variant,
                    onChoice = { captured.value = it },
                )
            }
        }
        composeTestRule.onNodeWithText("Restore failed").assertIsDisplayed()
        composeTestRule.onNodeWithText("Report").assertIsDisplayed()
        composeTestRule.onNodeWithText("Export diagnostics").assertIsDisplayed()
        composeTestRule.onNodeWithText("OK").assertIsDisplayed().performClick()
        assertEquals(AppDialogUserChoice(variant, AppDialogUserAction.Acknowledge), captured.value)
    }

    @Test
    fun restoreFailureReportTapDispatchesReport() {
        val captured = mutableStateOf<AppDialogUserChoice?>(null)
        val variant = AppDialog.RestoreFailure(reason = BackupErrorCode.Unknown)
        composeTestRule.setContent {
            AppTheme {
                AppDialogHostContent(
                    current = variant,
                    onChoice = { captured.value = it },
                )
            }
        }
        composeTestRule.onNodeWithText("Report").performClick()
        assertEquals(AppDialogUserChoice(variant, AppDialogUserAction.Report), captured.value)
    }

    @Test
    fun restoreFailureExportTapDispatchesExportDiagnostics() {
        val captured = mutableStateOf<AppDialogUserChoice?>(null)
        val variant = AppDialog.RestoreFailure(reason = BackupErrorCode.Unknown)
        composeTestRule.setContent {
            AppTheme {
                AppDialogHostContent(
                    current = variant,
                    onChoice = { captured.value = it },
                )
            }
        }
        composeTestRule.onNodeWithText("Export diagnostics").performClick()
        assertEquals(
            AppDialogUserChoice(variant, AppDialogUserAction.ExportDiagnostics),
            captured.value,
        )
    }

    @Test
    fun undoRestoreConfirmationConfirmDispatchesConfirmUndo() {
        val captured = mutableStateOf<AppDialogUserChoice?>(null)
        val variant = AppDialog.UndoRestoreConfirmation(originalDataDateEpochMs = 1_700_000_000_000L)
        composeTestRule.setContent {
            AppTheme {
                AppDialogHostContent(
                    current = variant,
                    onChoice = { captured.value = it },
                )
            }
        }
        composeTestRule.onNodeWithText("Undo last restore?").assertIsDisplayed()
        composeTestRule.onNodeWithText("Cancel").assertIsDisplayed()
        composeTestRule.onNodeWithText("Undo restore").assertIsDisplayed().performClick()
        assertEquals(AppDialogUserChoice(variant, AppDialogUserAction.ConfirmUndo), captured.value)
    }

    @Test
    fun undoRestoreConfirmationCancelDispatchesCancel() {
        val captured = mutableStateOf<AppDialogUserChoice?>(null)
        val variant = AppDialog.UndoRestoreConfirmation(originalDataDateEpochMs = 1_700_000_000_000L)
        composeTestRule.setContent {
            AppTheme {
                AppDialogHostContent(
                    current = variant,
                    onChoice = { captured.value = it },
                )
            }
        }
        composeTestRule.onNodeWithText("Cancel").performClick()
        assertEquals(AppDialogUserChoice(variant, AppDialogUserAction.Cancel), captured.value)
    }

    @Test
    fun undoRestoreSuccessRendersTitleAndOkDispatchesAcknowledge() {
        val captured = mutableStateOf<AppDialogUserChoice?>(null)
        composeTestRule.setContent {
            AppTheme {
                AppDialogHostContent(
                    current = AppDialog.UndoRestoreSuccess,
                    onChoice = { captured.value = it },
                )
            }
        }
        composeTestRule.onNodeWithText("Reverted").assertIsDisplayed()
        composeTestRule.onNodeWithText("OK").assertIsDisplayed().performClick()
        assertEquals(
            AppDialogUserChoice(AppDialog.UndoRestoreSuccess, AppDialogUserAction.Acknowledge),
            captured.value,
        )
    }
}
