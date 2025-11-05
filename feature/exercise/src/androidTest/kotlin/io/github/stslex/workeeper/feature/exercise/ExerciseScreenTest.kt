package io.github.stslex.workeeper.feature.exercise

import androidx.compose.material3.SnackbarHostState
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.stslex.workeeper.core.ui.kit.components.text_input_field.model.PropertyHolder
import io.github.stslex.workeeper.core.ui.test.BaseComposeTest
import io.github.stslex.workeeper.core.ui.test.annotations.Smoke
import io.github.stslex.workeeper.feature.exercise.ui.ExerciseFeatureWidget
import io.github.stslex.workeeper.feature.exercise.ui.mvi.model.SetUiType
import io.github.stslex.workeeper.feature.exercise.ui.mvi.model.SetsUiModel
import io.github.stslex.workeeper.feature.exercise.ui.mvi.store.DialogState
import io.github.stslex.workeeper.feature.exercise.ui.mvi.store.ExerciseStore
import io.github.stslex.workeeper.feature.exercise.ui.mvi.store.ExerciseStore.Action
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentSetOf
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.uuid.Uuid

/**
 * Comprehensive UI tests for Exercise feature
 * Tests both create and edit modes with various edge cases
 */
@Smoke
@RunWith(AndroidJUnit4::class)
class ExerciseScreenTest : BaseComposeTest() {

    @get:Rule
    val composeTestRule = createComposeRule()

    // ========== Basic Rendering Tests ==========

    @Test
    fun exerciseScreen_createMode_displaysCorrectly() {
        val state = ExerciseStore.State.INITIAL

        composeTestRule.setContent {
            ExerciseFeatureWidget(
                consume = {},
                state = state,
                snackbarHostState = SnackbarHostState(),
            )
        }

        composeTestRule.waitForIdle()

        // Verify buttons are present
        composeTestRule
            .onNodeWithTag("ExerciseCancelButton")
            .assertIsDisplayed()

        composeTestRule
            .onNodeWithTag("ExerciseSaveButton")
            .assertIsDisplayed()

        // Delete button should NOT be visible in create mode
        composeTestRule
            .onNodeWithTag("ExerciseDeleteButton")
            .assertDoesNotExist()
    }

    @Test
    fun exerciseScreen_editMode_displaysCorrectly() {
        val state = ExerciseStore.State(
            uuid = Uuid.random().toString(),
            name = PropertyHolder.StringProperty.new("Test Exercise"),
            sets = persistentListOf(),
            dateProperty = PropertyHolder.DateProperty.new(),
            dialogState = DialogState.Closed,
            isMenuOpen = false,
            menuItems = persistentSetOf(),
            trainingUuid = null,
            labels = persistentListOf(),
            initialHash = 0,
        )

        composeTestRule.setContent {
            ExerciseFeatureWidget(
                consume = {},
                state = state,
                snackbarHostState = SnackbarHostState(),
            )
        }

        composeTestRule.waitForIdle()

        // Delete button SHOULD be visible in edit mode
        composeTestRule
            .onNodeWithTag("ExerciseDeleteButton")
            .assertIsDisplayed()
    }

    @Test
    fun exerciseScreen_withSets_displaysAllSets() {
        val sets = persistentListOf(
            createMockSet(reps = 10, weight = 50.0),
            createMockSet(reps = 12, weight = 55.0),
            createMockSet(reps = 8, weight = 60.0),
        )

        val state = ExerciseStore.State.INITIAL.copy(sets = sets)

        composeTestRule.setContent {
            ExerciseFeatureWidget(
                consume = {},
                state = state,
                snackbarHostState = SnackbarHostState(),
            )
        }

        composeTestRule.waitForIdle()

        // Verify all sets are displayed
        sets.forEach { set ->
            composeTestRule
                .onNodeWithTag("ExerciseSetItem_${set.uuid}")
                .assertExists()
        }
    }

    // ========== Button Click Tests ==========

    @Test
    fun exerciseScreen_cancelButton_triggersAction() {
        val actionCapture = createActionCapture<Action>()
        val state = ExerciseStore.State.INITIAL

        composeTestRule.setContent {
            ExerciseFeatureWidget(
                consume = actionCapture,
                state = state,
                snackbarHostState = SnackbarHostState(),
            )
        }

        composeTestRule.waitForIdle()

        composeTestRule
            .onNodeWithTag("ExerciseCancelButton")
            .performClick()

        actionCapture.assertCapturedOnce<Action.Click.Cancel> {
            "Cancel button click was not captured"
        }
    }

    @Test
    fun exerciseScreen_saveButton_triggersAction() {
        val actionCapture = createActionCapture<Action>()
        val state = ExerciseStore.State.INITIAL

        composeTestRule.setContent {
            ExerciseFeatureWidget(
                consume = actionCapture,
                state = state,
                snackbarHostState = SnackbarHostState(),
            )
        }

        composeTestRule.waitForIdle()

        composeTestRule
            .onNodeWithTag("ExerciseSaveButton")
            .performClick()

        actionCapture.assertCapturedOnce<Action.Click.Save> {
            "Save button click was not captured"
        }
    }

    @Test
    fun exerciseScreen_deleteButton_triggersAction() {
        val actionCapture = createActionCapture<Action>()
        val state = ExerciseStore.State.INITIAL.copy(
            uuid = Uuid.random().toString(),
        )

        composeTestRule.setContent {
            ExerciseFeatureWidget(
                consume = actionCapture,
                state = state,
                snackbarHostState = SnackbarHostState(),
            )
        }

        composeTestRule.waitForIdle()

        composeTestRule
            .onNodeWithTag("ExerciseDeleteButton")
            .performClick()

        actionCapture.assertCapturedOnce<Action.Click.Delete> {
            "Delete button click was not captured"
        }
    }

    // ========== Input Field Tests ==========

    @Test
    fun exerciseScreen_nameInput_triggersAction() {
        val actionCapture = createActionCapture<Action>()
        val state = ExerciseStore.State.INITIAL

        composeTestRule.setContent {
            ExerciseFeatureWidget(
                consume = actionCapture,
                state = state,
                snackbarHostState = SnackbarHostState(),
            )
        }

        composeTestRule.waitForIdle()

        val testName = "Bench Press"
        composeTestRule
            .onNodeWithTag("ExerciseNameField")
            .performTextInput(testName)

        composeTestRule.waitForIdle()

        // Verify input action was captured
        actionCapture.assertCaptured<Action.Input.PropertyName> { "Name input action was not captured" }
    }

    // ========== Dialog Tests ==========

    @Test
    fun exerciseScreen_datePickerDialog_opensCorrectly() {
        val state = ExerciseStore.State.INITIAL.copy(
            dialogState = DialogState.Calendar,
        )

        composeTestRule.setContent {
            ExerciseFeatureWidget(
                consume = {},
                state = state,
                snackbarHostState = SnackbarHostState(),
            )
        }

        composeTestRule.waitForIdle()

        // Verify date picker dialog is displayed
        composeTestRule
            .onNodeWithTag("ExerciseDatePickerDialog")
            .assertIsDisplayed()
    }

    @Test
    fun exerciseScreen_datePickerButton_opensDialog() {
        val actionCapture = createActionCapture<Action>()
        val state = ExerciseStore.State.INITIAL

        composeTestRule.setContent {
            ExerciseFeatureWidget(
                consume = actionCapture,
                state = state,
                snackbarHostState = SnackbarHostState(),
            )
        }

        composeTestRule.waitForIdle()

        composeTestRule
            .onNodeWithTag("ExerciseDatePickerButton")
            .performClick()

        actionCapture.assertCapturedCount<Action.Click.PickDate>(1) {
            "Date picker button click was not captured"
        }
    }

    @Test
    fun exerciseScreen_setsDialog_opensForCreate() {
        val mockSet = createMockSet()
        val state = ExerciseStore.State.INITIAL.copy(
            dialogState = DialogState.Sets(mockSet),
        )

        composeTestRule.setContent {
            ExerciseFeatureWidget(
                consume = {},
                state = state,
                snackbarHostState = SnackbarHostState(),
            )
        }

        composeTestRule.waitForIdle()

        // Verify sets dialog is displayed
        composeTestRule
            .onNodeWithTag("ExerciseSetsDialog")
            .assertIsDisplayed()
    }

    @Test
    fun exerciseScreen_setsDialog_inputFields_work() {
        val actionCapture = createActionCapture<Action>()
        val mockSet = createMockSet()
        val state = ExerciseStore.State.INITIAL.copy(
            dialogState = DialogState.Sets(mockSet),
        )

        composeTestRule.setContent {
            ExerciseFeatureWidget(
                consume = actionCapture,
                state = state,
                snackbarHostState = SnackbarHostState(),
            )
        }

        composeTestRule.waitForIdle()

        // Input weight
        composeTestRule
            .onNodeWithTag("ExerciseSetsDialogWeightField")
            .performTextClearance()

        composeTestRule
            .onNodeWithTag("ExerciseSetsDialogWeightField")
            .performTextInput("75.5")

        composeTestRule.waitForIdle()

        // Input reps
        composeTestRule
            .onNodeWithTag("ExerciseSetsDialogRepsField")
            .performTextClearance()

        composeTestRule
            .onNodeWithTag("ExerciseSetsDialogRepsField")
            .performTextInput("12")

        composeTestRule.waitForIdle()

        actionCapture.assertCaptured<Action.Input.DialogSets.Weight> {
            "Weight input action was not captured"
        }

        actionCapture.assertCaptured<Action.Input.DialogSets.Reps> {
            "Reps input action was not captured"
        }
    }

    @Test
    fun exerciseScreen_setsDialog_saveButton_triggersAction() {
        val actionCapture = createActionCapture<Action>()
        val mockSet = createMockSet()
        val state = ExerciseStore.State.INITIAL.copy(
            dialogState = DialogState.Sets(mockSet),
        )

        composeTestRule.setContent {
            ExerciseFeatureWidget(
                consume = actionCapture,
                state = state,
                snackbarHostState = SnackbarHostState(),
            )
        }

        composeTestRule.waitForIdle()

        composeTestRule
            .onNodeWithTag("ExerciseSetsDialogSaveButton")
            .performClick()

        actionCapture.assertCapturedOnce<Action.Click.DialogSets.SaveButton> {
            "Sets dialog save button click was not captured"
        }
    }

    @Test
    fun exerciseScreen_setsDialog_cancelButton_triggersAction() {
        val actionCapture = createActionCapture<Action>()
        val mockSet = createMockSet()
        val state = ExerciseStore.State.INITIAL.copy(
            dialogState = DialogState.Sets(mockSet),
        )

        composeTestRule.setContent {
            ExerciseFeatureWidget(
                consume = actionCapture,
                state = state,
                snackbarHostState = SnackbarHostState(),
            )
        }

        composeTestRule.waitForIdle()

        composeTestRule
            .onNodeWithTag("ExerciseSetsDialogCancelButton")
            .performClick()

        actionCapture.assertCapturedOnce<Action.Click.DialogSets.CancelButton> {
            "Sets dialog cancel button click was not captured"
        }
    }

    // ========== Edge Case Tests ==========

    @Test
    fun exerciseScreen_emptyName_showsAsEmpty() {
        val state = ExerciseStore.State.INITIAL.copy(
            name = PropertyHolder.StringProperty.new(""),
        )

        composeTestRule.setContent {
            ExerciseFeatureWidget(
                consume = {},
                state = state,
                snackbarHostState = SnackbarHostState(),
            )
        }

        composeTestRule.waitForIdle()

        composeTestRule
            .onNodeWithTag("ExerciseNameField")
            .assertIsDisplayed()
    }

    @Test
    fun exerciseScreen_longName_displaysCorrectly() {
        val longName = "A".repeat(100)
        val state = ExerciseStore.State.INITIAL.copy(
            name = PropertyHolder.StringProperty.new(longName),
        )

        composeTestRule.setContent {
            ExerciseFeatureWidget(
                consume = {},
                state = state,
                snackbarHostState = SnackbarHostState(),
            )
        }

        composeTestRule.waitForIdle()

        composeTestRule
            .onNodeWithTag("ExerciseNameField")
            .assertIsDisplayed()
    }

    @Test
    fun exerciseScreen_manySets_displaysCorrectly() {
        val manySets = persistentListOf(
            *Array(20) { index ->
                createMockSet(reps = index + 1, weight = 50.0 + index)
            },
        )

        val state = ExerciseStore.State.INITIAL.copy(sets = manySets)

        composeTestRule.setContent {
            ExerciseFeatureWidget(
                consume = {},
                state = state,
                snackbarHostState = SnackbarHostState(),
            )
        }

        composeTestRule.waitForIdle()

        // Verify at least some sets are visible (first few should be)
        composeTestRule
            .onNodeWithTag("ExerciseSetItem_${manySets.first().uuid}")
            .assertExists()
    }

    @Test
    fun exerciseScreen_zeroWeightSet_displaysCorrectly() {
        val zeroWeightSet = createMockSet(reps = 10, weight = 0.0)
        val state = ExerciseStore.State.INITIAL.copy(
            sets = persistentListOf(zeroWeightSet),
        )

        composeTestRule.setContent {
            ExerciseFeatureWidget(
                consume = {},
                state = state,
                snackbarHostState = SnackbarHostState(),
            )
        }

        composeTestRule.waitForIdle()

        composeTestRule
            .onNodeWithTag("ExerciseSetItem_${zeroWeightSet.uuid}")
            .assertExists()
    }

    @Test
    fun exerciseScreen_differentSetTypes_displayCorrectly() {
        val sets = persistentListOf(
            createMockSet(type = SetUiType.WARM),
            createMockSet(type = SetUiType.WORK),
            createMockSet(type = SetUiType.FAIL),
            createMockSet(type = SetUiType.DROP),
        )

        val state = ExerciseStore.State.INITIAL.copy(sets = sets)

        composeTestRule.setContent {
            ExerciseFeatureWidget(
                consume = {},
                state = state,
                snackbarHostState = SnackbarHostState(),
            )
        }

        composeTestRule.waitForIdle()

        // Verify all set types are displayed
        sets.forEach { set ->
            composeTestRule
                .onNodeWithTag("ExerciseSetItem_${set.uuid}")
                .assertExists()
        }
    }

    @Test
    fun exerciseScreen_dialogClosed_doesNotDisplayDialog() {
        val state = ExerciseStore.State.INITIAL.copy(
            dialogState = DialogState.Closed,
        )

        composeTestRule.setContent {
            ExerciseFeatureWidget(
                consume = {},
                state = state,
                snackbarHostState = SnackbarHostState(),
            )
        }

        composeTestRule.waitForIdle()

        // Verify no dialogs are displayed
        composeTestRule
            .onNodeWithTag("ExerciseDatePickerDialog")
            .assertDoesNotExist()

        composeTestRule
            .onNodeWithTag("ExerciseSetsDialog")
            .assertDoesNotExist()
    }

    // ========== Helper Functions ==========

    private fun createMockSet(
        reps: Int = 10,
        weight: Double = 50.0,
        type: SetUiType = SetUiType.WORK,
    ): SetsUiModel {
        return SetsUiModel(
            uuid = Uuid.random().toString(),
            reps = PropertyHolder.IntProperty.new(reps),
            weight = PropertyHolder.DoubleProperty.new(weight),
            type = type,
        )
    }
}
