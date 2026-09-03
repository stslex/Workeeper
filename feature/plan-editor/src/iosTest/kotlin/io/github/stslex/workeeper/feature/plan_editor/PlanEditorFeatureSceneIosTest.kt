// SPDX-License-Identifier: GPL-3.0-only
@file:OptIn(ExperimentalTestApi::class)

package io.github.stslex.workeeper.feature.plan_editor

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.v2.runComposeUiTest
import io.github.stslex.workeeper.core.ui.kit.resources.core_ui_kit_action_cancel
import io.github.stslex.workeeper.core.ui.kit.theme.AppTheme
import io.github.stslex.workeeper.core.ui.plan_editor.model.ExerciseTypeUiModel
import io.github.stslex.workeeper.core.ui.plan_editor.model.PlanEditorBodyAction
import io.github.stslex.workeeper.core.ui.plan_editor.model.PlanSetUiModel
import io.github.stslex.workeeper.core.ui.plan_editor.model.SetTypeUiModel
import io.github.stslex.workeeper.feature.plan_editor.resources.core_ui_plan_editor_error_load
import io.github.stslex.workeeper.feature.plan_editor.resources.core_ui_plan_editor_error_save
import io.github.stslex.workeeper.feature.plan_editor.resources.core_ui_plan_editor_screen_back
import io.github.stslex.workeeper.feature.plan_editor.resources.core_ui_plan_editor_screen_cancel
import io.github.stslex.workeeper.feature.plan_editor.resources.core_ui_plan_editor_screen_save
import io.github.stslex.workeeper.feature.plan_editor.resources.core_ui_plan_editor_screen_title_default
import io.github.stslex.workeeper.feature.plan_editor.resources.core_ui_plan_editor_screen_title_format
import io.github.stslex.workeeper.feature.plan_editor.resources.feature_plan_editor_set_type_tooltip
import io.github.stslex.workeeper.feature.plan_editor.resources.feature_plan_editor_type_change_weightless_body
import io.github.stslex.workeeper.feature.plan_editor.resources.feature_plan_editor_type_change_weightless_confirm
import io.github.stslex.workeeper.feature.plan_editor.resources.feature_plan_editor_type_change_weightless_impact
import io.github.stslex.workeeper.feature.plan_editor.resources.feature_plan_editor_type_change_weightless_title
import io.github.stslex.workeeper.feature.plan_editor.ui.PlanEditorScreen
import io.github.stslex.workeeper.feature.plan_editor.ui.mvi.store.DialogState
import io.github.stslex.workeeper.feature.plan_editor.ui.mvi.store.PlanEditorStore.Action
import io.github.stslex.workeeper.feature.plan_editor.ui.mvi.store.PlanEditorStore.State
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.runBlocking
import org.jetbrains.compose.resources.getString
import kotlin.test.Test
import kotlin.test.assertEquals
import io.github.stslex.workeeper.core.ui.kit.resources.Res as KitRes
import io.github.stslex.workeeper.feature.plan_editor.resources.Res as PlanEditorRes

class PlanEditorFeatureSceneIosTest {

    @Test
    fun resourcesBranchesAndActionsRenderAndDispatch() = runComposeUiTest {
        assertEquals(
            listOf(
                "Edit plan: %1\$s",
                "Edit plan",
                "Back",
                "Save",
                "Cancel",
                "Failed to load the plan.",
                "Failed to save the plan.",
                "Tap to cycle: warmup → work → failure → drop",
                "Switch to weightless?",
                "Weight values from this exercise’s plans will be cleared. " +
                    "This cannot be undone.",
                "All plan weights cleared",
                "Switch",
            ),
            featureCatalog(),
        )

        val plan = persistentListOf(
            PlanSetUiModel(
                weight = 80.0,
                reps = 8,
                type = SetTypeUiModel.WORK,
            ),
        )
        val loadedState = State(
            mode = State.Mode.Exercise(exerciseUuid = "native-exercise"),
            isLoading = false,
            exerciseName = "Bench press",
            type = ExerciseTypeUiModel.WEIGHTED,
            initialDraft = plan,
            draft = plan,
            initialType = ExerciseTypeUiModel.WEIGHTED,
            pendingTypeChange = null,
            isSaving = false,
            dialogState = DialogState.Hidden,
        )
        val state = mutableStateOf(loadedState)
        val actions = mutableListOf<Action>()

        setContent {
            AppTheme {
                PlanEditorScreen(
                    state = state.value,
                    consume = actions::add,
                )
            }
        }

        settleScene()
        val formattedTitle = runBlocking {
            getString(
                PlanEditorRes.string.core_ui_plan_editor_screen_title_format,
                loadedState.exerciseName,
            )
        }
        val back = runBlocking {
            getString(PlanEditorRes.string.core_ui_plan_editor_screen_back)
        }
        val save = runBlocking {
            getString(PlanEditorRes.string.core_ui_plan_editor_screen_save)
        }
        val cancel = runBlocking {
            getString(PlanEditorRes.string.core_ui_plan_editor_screen_cancel)
        }
        val tooltip = runBlocking {
            getString(PlanEditorRes.string.feature_plan_editor_set_type_tooltip)
        }

        onNodeWithTag("PlanEditorScreen", useUnmergedTree = true).assertIsDisplayed()
        onNodeWithText(formattedTitle, useUnmergedTree = true).assertIsDisplayed()
        onNodeWithContentDescription(back, useUnmergedTree = true).assertIsDisplayed()
        onNodeWithText(save, useUnmergedTree = true).assertIsDisplayed()
        onNodeWithText(cancel, useUnmergedTree = true).assertIsDisplayed()
        onNodeWithTag("PlanEditorBodyRow_0", useUnmergedTree = true).assertIsDisplayed()

        onNodeWithTag("AppSetBarAdd", useUnmergedTree = true).performClick()
        assertEquals(
            listOf<Action>(Action.EditorAction(PlanEditorBodyAction.OnAddSet)),
            actions,
        )

        onNodeWithTag("PlanEditorCancel", useUnmergedTree = true).performClick()
        onNodeWithTag("PlanEditorBack", useUnmergedTree = true).performClick()
        assertEquals(
            listOf<Action>(
                Action.EditorAction(PlanEditorBodyAction.OnAddSet),
                Action.Click.OnBackClick,
                Action.Click.OnBackClick,
            ),
            actions,
        )

        onNodeWithTag("PlanEditorBodyRowType_0", useUnmergedTree = true)
            .performTouchInput { longClick() }
        settleScene()
        onNodeWithText(tooltip, useUnmergedTree = true).assertIsDisplayed()
        settleScene()

        state.value = loadedState.copy(
            pendingTypeChange = ExerciseTypeUiModel.WEIGHTLESS,
            dialogState = DialogState.TypeChangeConfirm,
        )
        settleScene()

        val dialogCopy = runBlocking {
            listOf(
                getString(
                    PlanEditorRes.string.feature_plan_editor_type_change_weightless_title,
                ),
                getString(
                    PlanEditorRes.string.feature_plan_editor_type_change_weightless_body,
                ),
                getString(
                    PlanEditorRes.string.feature_plan_editor_type_change_weightless_impact,
                ),
                getString(
                    PlanEditorRes.string.feature_plan_editor_type_change_weightless_confirm,
                ),
            )
        }
        dialogCopy.forEach { copy ->
            onNodeWithText(copy, useUnmergedTree = true).assertIsDisplayed()
        }
        val kitCancel = runBlocking {
            getString(KitRes.string.core_ui_kit_action_cancel)
        }
        onNodeWithTag("AppConfirmSheetDismiss").assertTextEquals(kitCancel)
        onNodeWithTag("AppConfirmSheetConfirm", useUnmergedTree = true).performClick()
        assertEquals(
            listOf<Action>(
                Action.EditorAction(PlanEditorBodyAction.OnAddSet),
                Action.Click.OnBackClick,
                Action.Click.OnBackClick,
                Action.Click.OnTypeChangeConfirm,
            ),
            actions,
        )

        state.value = loadedState.copy(exerciseName = "")
        settleScene()
        val defaultTitle = runBlocking {
            getString(PlanEditorRes.string.core_ui_plan_editor_screen_title_default)
        }
        onNodeWithText(defaultTitle, useUnmergedTree = true).assertIsDisplayed()
    }

    private fun featureCatalog(): List<String> = runBlocking {
        listOf(
            getString(PlanEditorRes.string.core_ui_plan_editor_screen_title_format),
            getString(PlanEditorRes.string.core_ui_plan_editor_screen_title_default),
            getString(PlanEditorRes.string.core_ui_plan_editor_screen_back),
            getString(PlanEditorRes.string.core_ui_plan_editor_screen_save),
            getString(PlanEditorRes.string.core_ui_plan_editor_screen_cancel),
            getString(PlanEditorRes.string.core_ui_plan_editor_error_load),
            getString(PlanEditorRes.string.core_ui_plan_editor_error_save),
            getString(PlanEditorRes.string.feature_plan_editor_set_type_tooltip),
            getString(PlanEditorRes.string.feature_plan_editor_type_change_weightless_title),
            getString(PlanEditorRes.string.feature_plan_editor_type_change_weightless_body),
            getString(PlanEditorRes.string.feature_plan_editor_type_change_weightless_impact),
            getString(PlanEditorRes.string.feature_plan_editor_type_change_weightless_confirm),
        )
    }

    private fun androidx.compose.ui.test.ComposeUiTest.settleScene() {
        mainClock.autoAdvance = false
        mainClock.advanceTimeBy(1_000)
        mainClock.autoAdvance = true
        waitForIdle()
    }
}
