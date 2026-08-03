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
 * F-02 — the create-form interaction wiring: Save is always enabled, and tapping it dispatches
 * [Action.Click.OnSaveClick].
 *
 * App-Scope Collapse Step 6 (Phase 3.4): de-Hilt'd. The former version booted a real Hilt graph +
 * in-memory `AppDatabase` (via the deleted `TestDatabaseModule`) inside `TestActivity` and asserted the
 * row landed in `exercise_table`. Post-cut the feature graph resolves its app-scope deps through the
 * `appDeps<T>()` acquisition seam (backed by the app graph, only reachable from `:app:app`), and the
 * exercise repositories have `internal` constructors — so a real end-to-end DB round-trip is not
 * constructible in this module. Following the
 * established feature-UI-test idiom (F1 — direct screen render with an `ActionCapture`, cf.
 * [io.github.stslex.workeeper.feature.settings.SettingsScreenTest] and the sibling [ExerciseScreenTest]),
 * this verifies the form's action wiring; the DB half of F-02 (type → Save → row in `exercise_table`)
 * was relocated to `app/app/src/androidTest/.../app/ExerciseCreatePersistenceTest.kt`, which drives the
 * real feature graph over `MetroTestRule`'s in-memory `AppDatabase`.
 */
@Smoke
@RunWith(AndroidJUnit4::class)
class ExerciseFormBasicsTest : BaseComposeTest() {

    @get:Rule
    val composeTestRule = createComposeRule()

    private fun createState(name: String = ""): State =
        State.create(uuid = null).copy(isLoading = false, name = name)

    /**
     * **The UI half of §26 "Save is never disabled", asserted on both sides of the name.**
     *
     * Save is enabled with an empty name and the tap dispatches [Action.Click.OnSaveClick], which
     * is what makes the handler's blank-name branch reachable at all: gate the button on
     * `name.isNotBlank()` and that branch — and the two `ClickHandlerTest` cases that assert it —
     * certify a state production cannot enter (B23's shape). Typing a name changes neither, so the
     * assertion is about the absence of a predicate rather than about one value of it.
     */
    @Test
    fun f02_saveIsEnabledWithAnEmptyName_soTheBlankNameErrorIsReachable() {
        val capture = createActionCapture<Action>()
        // In production the Store folds Action.Input.OnNameChange back into State; here the test plays
        // that role, so the empty → non-empty transition is a real transition rather than one frozen
        // frame — which is what makes "enabled on both sides of it" an assertion and not a coincidence.
        var state by mutableStateOf(createState())

        composeTestRule.setContent {
            AppTheme(themeMode = ThemeMode.LIGHT) {
                // ExerciseEditScreen reads from `LocalAppColors` (AppUi.colors), so the mount lives inside
                // `AppTheme` exactly like the production hierarchy — the same wrap `ExerciseScreenTest` uses.
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

        // Empty name → Save ENABLED, and the tap goes through. This is the reachability the
        // handler's blank-name branch depends on.
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
                // A non-blank name makes the button enabled without depending on the input round-trip
                // (the Store would normally fold OnNameChange back into state; here the screen is stateless).
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
