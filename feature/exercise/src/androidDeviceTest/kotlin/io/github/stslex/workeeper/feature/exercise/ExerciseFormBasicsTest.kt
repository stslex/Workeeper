// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.exercise

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.stslex.workeeper.core.ui.kit.theme.AppTheme
import io.github.stslex.workeeper.core.ui.kit.theme.ThemeMode
import io.github.stslex.workeeper.core.ui.test.BaseComposeTest
import io.github.stslex.workeeper.core.ui.test.annotations.Smoke
import io.github.stslex.workeeper.feature.exercise.ui.ExerciseEditScreen
import io.github.stslex.workeeper.feature.exercise.ui.mvi.store.ExerciseStore.Action
import io.github.stslex.workeeper.feature.exercise.ui.mvi.store.ExerciseStore.State
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * F-02 — the create-form interaction wiring: Save is always enabled and its tap dispatches
 * [Action.Click.OnSaveClick]. The DB half lives in `:app`'s `ExerciseCreatePersistenceTest`.
 */
@Smoke
@RunWith(AndroidJUnit4::class)
class ExerciseFormBasicsTest : BaseComposeTest() {

    @get:Rule
    val composeTestRule = createComposeRule()

    private fun createState(name: String = ""): State =
        State.create(uuid = null).copy(isLoading = false, name = name)

    /** The UI half of §26 "Save is never disabled", asserted on both sides of the name. */
    @Test
    fun f02_saveIsEnabledWithAnEmptyName_soTheBlankNameErrorIsReachable() {
        val capture = createActionCapture<Action>()
        // The test plays the Store's role, so empty → non-empty is a real transition.
        var state by mutableStateOf(createState())

        composeTestRule.setContent {
            AppTheme(themeMode = ThemeMode.LIGHT) {
                // The screen reads `LocalAppColors`, so the mount lives inside `AppTheme` as in
                // production.
                ExerciseEditScreen(
                    state = state,
                    consume = { action ->
                        capture(action)
                        if (action is Action.Input.OnNameChange) {
                            state = state.copy(name = action.value)
                        }
                    },
                )
            }
        }

        // Empty name → Save ENABLED, and the tap goes through.
        composeTestRule
            .onNodeWithTag("ExerciseEditSaveButton")
            .assertIsEnabled()
            .performClick()
        capture.assertCapturedExactly(Action.Click.OnSaveClick)

        // Typing a name dispatches OnNameChange (the input wiring the form depends on)...
        composeTestRule
            .onNodeWithTag("ExerciseEditNameField")
            .performTextInput("Bench Press")
        capture.assertCaptured<Action.Input.OnNameChange>()

        // ...and Save is still enabled, because it does not depend on the name at all.
        composeTestRule.waitForIdle()
        composeTestRule
            .onNodeWithTag("ExerciseEditSaveButton")
            .assertIsEnabled()
            .performClick()
        capture.assertCaptured<Action.Click.OnSaveClick>()
    }

    @Test
    fun f02_tappingSaveWithAValidName_dispatchesOnSaveClick() {
        val capture = createActionCapture<Action>()

        composeTestRule.setContent {
            AppTheme(themeMode = ThemeMode.LIGHT) {
                // A non-blank name enables the button without the input round-trip.
                ExerciseEditScreen(state = createState(name = "Bench Press"), consume = capture)
            }
        }

        composeTestRule
            .onNodeWithTag("ExerciseEditSaveButton")
            .assertIsEnabled()
            .performClick()

        capture.assertCapturedExactly(Action.Click.OnSaveClick)
    }
}
