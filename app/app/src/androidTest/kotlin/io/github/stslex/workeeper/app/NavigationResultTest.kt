// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.app

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.stslex.workeeper.MainActivity
import io.github.stslex.workeeper.core.ui.test.annotations.Regression
import io.github.stslex.workeeper.harness.MetroTestRule
import io.github.stslex.workeeper.harness.NavPaths
import io.github.stslex.workeeper.harness.NavSeed
import kotlin.uuid.ExperimentalUuidApi
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Nav3 stage 1.1: a destination that produces a result hands it back and the opener acts on it.
 * Every assertion is a user-visible effect, never the transport, because a Store's flow errors are
 * swallowed. See documentation/tech-debt.md.
 */
@OptIn(ExperimentalUuidApi::class)
@Regression
@RunWith(AndroidJUnit4::class)
internal class NavigationResultTest {

    @get:Rule(order = 0)
    val metroRule = MetroTestRule()

    @get:Rule(order = 1)
    val composeRule = createAndroidComposeRule<MainActivity>()

    private lateinit var paths: NavPaths
    private lateinit var seed: NavSeed

    @Before
    fun setUp() {
        paths = NavPaths(composeRule)
        seed = NavSeed(metroRule.appDatabase)
    }

    /**
     * `Screen.PlanEditor` → `Boolean` → `LiveWorkout` reloads, seed-free. Green here is not
     * evidence that the result transport works; see documentation/tech-debt.md.
     */
    @Test
    fun planEditorSaveReachesTheLiveSessionThatOpenedIt() {
        paths.awaitTag(HOME_GRAPH)

        paths.startBlankSession()
        paths.awaitTag(LIVE_WORKOUT_GRAPH)

        paths.addInlineExerciseToSession(INLINE_EXERCISE_NAME)

        // The "before" half: without it the post-save assertion would hold either way.
        val subTag = paths.tagStartingWith(LIVE_EXERCISE_CARD_SUB_PREFIX, useUnmergedTree = true)
        paths.assertUnmergedText(subTag, NO_PLAN_LABEL)

        paths.openPlanEditorForFirstExercise()
        paths.awaitTag(PLAN_EDITOR_GRAPH)

        paths.tap(PLAN_EDITOR_ADD_SET)
        paths.tap(PLAN_EDITOR_SAVE)

        paths.awaitTag(LIVE_WORKOUT_GRAPH)

        // A swallowed failure leaves this reading NO_PLAN_LABEL.
        paths.awaitTextChangedFrom(subTag, NO_PLAN_LABEL)
    }

    /**
     * `Screen.ExerciseImage` → request name → `Exercise` acts on it. Needs a seeded image and goes
     * through edit mode, since the viewer's verbs are only offered when the caller is `editable`.
     */
    @Test
    fun imageReplaceRequestReachesTheExerciseThatOpenedTheViewer() {
        val exerciseUuid = seed.exercise(
            name = SEEDED_EXERCISE_NAME,
            imagePath = SEEDED_IMAGE_PATH,
        )

        paths.toAllExercises()
        paths.awaitTag(ALL_EXERCISES_GRAPH)

        paths.openExercise(exerciseUuid.toString())
        paths.awaitTag(EXERCISE_GRAPH)

        paths.tap(EXERCISE_EDIT_BUTTON)
        paths.awaitTag(EXERCISE_EDIT_ACTION_BAR)

        paths.tap(EXERCISE_DESCRIPTION_IMAGE)
        paths.awaitTag(IMAGE_VIEWER_GRAPH)

        paths.tap(IMAGE_VIEWER_MENU_BUTTON)
        paths.tap(IMAGE_VIEWER_REPLACE_ITEM)

        paths.awaitTag(EXERCISE_GRAPH)

        // Nothing else in this journey opens the sheet, so it is attributable to the request.
        paths.awaitTag(EXERCISE_IMAGE_SOURCE_SHEET)
        composeRule.onNodeWithTag(EXERCISE_IMAGE_SOURCE_SHEET).assertIsDisplayed()
    }

    @Test
    fun imageRemoveRequestTurnsTheThumbnailIntoThePhotoPickerEntryPoint() {
        val exerciseUuid = seed.exercise(
            name = SEEDED_EXERCISE_NAME,
            imagePath = SEEDED_IMAGE_PATH,
        )

        paths.toAllExercises()
        paths.awaitTag(ALL_EXERCISES_GRAPH)
        paths.openExercise(exerciseUuid.toString())
        paths.awaitTag(EXERCISE_GRAPH)
        paths.tap(EXERCISE_EDIT_BUTTON)
        paths.awaitTag(EXERCISE_EDIT_ACTION_BAR)
        paths.tap(EXERCISE_DESCRIPTION_IMAGE)
        paths.awaitTag(IMAGE_VIEWER_GRAPH)

        paths.tap(IMAGE_VIEWER_MENU_BUTTON)
        paths.tap(IMAGE_VIEWER_REMOVE_ITEM)

        paths.awaitTag(EXERCISE_GRAPH)
        composeRule.waitForIdle()
        paths.tap(EXERCISE_DESCRIPTION_IMAGE)

        paths.awaitTag(EXERCISE_IMAGE_SOURCE_SHEET)
        composeRule.onNodeWithTag(EXERCISE_IMAGE_SOURCE_SHEET).assertIsDisplayed()
    }

    private companion object {

        const val HOME_GRAPH = "HomeGraph"
        const val ALL_EXERCISES_GRAPH = "AllExercisesGraph"
        const val EXERCISE_GRAPH = "ExerciseGraph"
        const val LIVE_WORKOUT_GRAPH = "LiveWorkoutGraph"
        const val PLAN_EDITOR_GRAPH = "PlanEditorGraph"
        const val IMAGE_VIEWER_GRAPH = "ImageViewerGraph"

        const val PLAN_EDITOR_ADD_SET = "AppSetBarAdd"
        const val PLAN_EDITOR_SAVE = "PlanEditorSave"
        const val LIVE_EXERCISE_CARD_SUB_PREFIX = "LiveExerciseCardSub_"

        const val EXERCISE_EDIT_BUTTON = "ExerciseEditButton"
        /** Edit mode's marker: an `ExerciseEditScreen` tag never reaches the tree. */
        const val EXERCISE_EDIT_ACTION_BAR = "ExerciseEditActionBar"
        const val EXERCISE_DESCRIPTION_IMAGE = "ExerciseDescriptionImage"
        const val IMAGE_VIEWER_MENU_BUTTON = "ImageViewerMenuButton"
        const val IMAGE_VIEWER_REPLACE_ITEM = "ImageViewerReplaceItem"
        const val IMAGE_VIEWER_REMOVE_ITEM = "ImageViewerRemoveItem"
        const val EXERCISE_IMAGE_SOURCE_SHEET = "ExerciseImageSourceSheet"

        /**
         * `R.string.feature_live_workout_status_no_plan`, inlined — androidTest cannot see a
         * feature module's `R`. Renaming the string fails this loudly, not quietly.
         */
        const val NO_PLAN_LABEL = "no plan"

        const val INLINE_EXERCISE_NAME = "Result Probe Press"
        const val SEEDED_EXERCISE_NAME = "Result Probe Squat"
        const val SEEDED_IMAGE_PATH = "/nav-result-probe/image.jpg"
    }
}
