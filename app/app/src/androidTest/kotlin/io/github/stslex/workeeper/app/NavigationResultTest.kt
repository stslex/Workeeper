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
 * Nav3 migration, stage 1.1: a destination that produces a result hands it back, and the screen
 * that opened it acts on it.
 *
 * Both result flows in the app are covered — the two that stage 1.2 rewrote end to end:
 *
 *  - `Screen.PlanEditor` → `Boolean`, consumed by `LiveWorkout`.
 *  - `Screen.ExerciseImage` → a request name, consumed by `Exercise`.
 *
 * ## Why this class asserts what it asserts
 *
 * **Every assertion here is a user-visible effect, and none of them names the transport.** That is
 * not stylistic. `AppCoroutineScopeImpl.launch(flow, …)` applies `.catch { onError(it) }`, so a
 * flow error inside an MVI Store is swallowed: if a result stops arriving, nothing throws, no
 * crash is reported, and the screen quietly holds the state it already had. A test written around
 * "the navigation completed" or "no exception was raised" passes in exactly that case.
 *
 * So each test drives the app to a state where the *absence* of the result is visible, performs
 * the action that produces it, and asserts the screen changed:
 *
 *  - the plan editor: an inline-created exercise has no plan, so its card reads "no plan". After a
 *    plan is saved the card must read the plan instead.
 *  - the image viewer: Replace opens the source sheet, while Remove turns the thumbnail into the
 *    add-photo entry point. If either request is lost, its visible effect never appears.
 *
 * ## Load-bearing limitation: only one half discriminates
 *
 * **[imageReplaceRequestReachesTheExerciseThatOpenedTheViewer] is a true regression test** for the
 * whole chain — produced, transported, resolved from name to verb, dispatched. Remove the
 * handler's dispatch and it fails on its observable assertion, and on nothing else.
 *
 * **[planEditorSaveReachesTheLiveSessionThatOpenedIt] is not.** It passes whether or not the
 * result reaches the Store: the session comes back showing the new plan either way, because
 * something other than the result re-runs the one-shot `loadSession` on return. So it is honest
 * regression cover for a user-visible outcome — save a plan, the session shows it — and is **not**
 * evidence that the result transport works.
 *
 * Do not cite it as such, and do not let its green mask a broken `Screen.PlanEditor` result flow:
 * nothing in this suite would catch that today. Why the screen recovers, and what should pin it,
 * are open in `documentation/tech-debt.md`.
 *
 * ## What this class does NOT do
 *
 * It never constructs a `Screen`, never reads a result, and never names `SavedStateHandle`,
 * `NavResults` or `ScreenWithResult`. It reaches the app through the semantics tree and Room, the
 * same two channels as the rest of the suite, so the transport underneath can be replaced without
 * touching it.
 *
 * @see NavPaths for the journeys, and for why arrival waits on an explicit timeout.
 * @see NavSeed for the rows, and for the two schema rules a call site cannot see.
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
     * `Screen.PlanEditor` → `Boolean` → `LiveWorkout` reloads.
     *
     * Seed-free, on the app's only plan-editor-result journey:
     * Home → blank session → add an exercise inline → its kebab → Edit plan → add a set → Save.
     *
     * The card's sub-label is the instrument. An inline-created exercise is not plan-attached, so
     * `toStatusLabel` reports the no-plan string; once a plan exists the same label renders the
     * plan summary. Asserting "the text changed away from no-plan" rather than pinning the exact
     * summary keeps this from breaking when the summary's formatting is tuned — the summary's
     * shape is `LiveWorkoutMapper`'s business and has its own unit tests, whereas what is at risk
     * here is only whether the reload happened at all.
     */
    @Test
    fun planEditorSaveReachesTheLiveSessionThatOpenedIt() {
        paths.awaitTag(HOME_GRAPH)

        paths.startBlankSession()
        paths.awaitTag(LIVE_WORKOUT_GRAPH)

        paths.addInlineExerciseToSession(INLINE_EXERCISE_NAME)

        // The "before" half. Without it a green run proves nothing: if the card read the plan
        // already, the assertion after the save would hold whether or not the result arrived.
        val subTag = paths.tagStartingWith(LIVE_EXERCISE_CARD_SUB_PREFIX, useUnmergedTree = true)
        paths.assertUnmergedText(subTag, NO_PLAN_LABEL)

        paths.openPlanEditorForFirstExercise()
        paths.awaitTag(PLAN_EDITOR_GRAPH)

        paths.tap(PLAN_EDITOR_ADD_SET)
        paths.tap(PLAN_EDITOR_SAVE)

        paths.awaitTag(LIVE_WORKOUT_GRAPH)

        // The result landed, the Store reloaded, and the session on screen shows the new plan.
        // A swallowed failure leaves this reading NO_PLAN_LABEL.
        paths.awaitTextChangedFrom(subTag, NO_PLAN_LABEL)
    }

    /**
     * `Screen.ExerciseImage` → request name → `Exercise` acts on it.
     *
     * Needs a seeded image: the viewer is only reachable from an exercise that has one, and its
     * two verbs are only offered when the caller said it can honour them — `editable` is
     * `mode is Mode.Edit`, so the journey goes through the edit screen rather than the detail one.
     *
     * `Replace` is the verb under test because its effect is unambiguous and immediate: the
     * exercise screen owns the picker machinery, so honouring the request means the image-source
     * sheet appears. Nothing else in this journey opens that sheet, so its presence is entirely
     * attributable to the result arriving.
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

        // The request landed and the editor acted on it. If it did not, the screen returns to the
        // edit form with no sheet and this times out.
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
        /**
         * Edit mode's marker, and NOT `ExerciseEditScreen` — that tag never reaches the tree.
         * Both mode branches receive the graph's `modifier`, which already carries
         * `.testTag("ExerciseGraph")`, and a second `testTag` on the same chain overwrites rather
         * than adds. Measured from a semantics dump: in edit mode the tree carries `ExerciseGraph`
         * plus the action bar's own tag, and no `ExerciseEditScreen`.
         */
        const val EXERCISE_EDIT_ACTION_BAR = "ExerciseEditActionBar"
        const val EXERCISE_DESCRIPTION_IMAGE = "ExerciseDescriptionImage"
        const val IMAGE_VIEWER_MENU_BUTTON = "ImageViewerMenuButton"
        const val IMAGE_VIEWER_REPLACE_ITEM = "ImageViewerReplaceItem"
        const val IMAGE_VIEWER_REMOVE_ITEM = "ImageViewerRemoveItem"
        const val EXERCISE_IMAGE_SOURCE_SHEET = "ExerciseImageSourceSheet"

        /**
         * `R.string.feature_live_workout_status_no_plan`, inlined.
         *
         * The androidTest source set cannot see a feature module's `R`, and adding that dependency
         * to reach one string would give this suite a compile-time edge into a feature it is
         * otherwise independent of. The cost of inlining is that renaming the string silently
         * turns the "before" assertion into a comparison against a stale literal — which fails
         * loudly here rather than passing quietly, so the failure mode is the safe one.
         */
        const val NO_PLAN_LABEL = "no plan"

        const val INLINE_EXERCISE_NAME = "Result Probe Press"
        const val SEEDED_EXERCISE_NAME = "Result Probe Squat"
        const val SEEDED_IMAGE_PATH = "/nav-result-probe/image.jpg"
    }
}
