// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.app

import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
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
 * Every destination is reachable through the UI and dismissible back to its origin. A differential
 * oracle for the navigation backend: editing a test here during a swap is itself the bug.
 *
 * @see NavPaths for the journeys.
 * @see NavSeed for the rows.
 */
@OptIn(ExperimentalUuidApi::class)
@Regression
@RunWith(AndroidJUnit4::class)
internal class RouteReachabilityTest {

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

    // Bottom-bar roots: no seed, one click.

    @Test
    fun homeIsTheColdStartDestination() {
        paths.awaitTag("AppRoot")
        paths.awaitTag(HOME_GRAPH)
    }

    @Test
    fun allTrainingsOpensFromTheBottomBarAndHomeReturns() {
        paths.awaitTag(HOME_GRAPH)

        paths.toAllTrainings()
        paths.awaitTag(ALL_TRAININGS_GRAPH)

        paths.toHome()
        paths.awaitTag(HOME_GRAPH)
    }

    @Test
    fun allExercisesOpensFromTheBottomBarAndHomeReturns() {
        paths.awaitTag(HOME_GRAPH)

        paths.toAllExercises()
        paths.awaitTag(ALL_EXERCISES_GRAPH)

        paths.toHome()
        paths.awaitTag(HOME_GRAPH)
    }

    // Reachable with no seeded rows.

    @Test
    fun settingsOpensFromHomeAndHomeReturns() {
        paths.awaitTag(HOME_GRAPH)

        paths.openSettings()
        paths.awaitTag(SETTINGS_GRAPH)

        paths.tap("SettingsBackButton")
        paths.awaitTag(HOME_GRAPH)
    }

    /** Archive's only route is through Settings, so dismissing it returns to Settings, not Home. */
    @Test
    fun archiveOpensFromSettingsAndSettingsReturns() {
        paths.awaitTag(HOME_GRAPH)

        paths.openSettings()
        paths.awaitTag(SETTINGS_GRAPH)

        paths.openArchiveFromSettings()
        paths.awaitTag(ARCHIVE_GRAPH)

        paths.tap("ArchiveBackButton")
        paths.awaitTag(SETTINGS_GRAPH)
    }

    /** Blank start needs no seed; back leaves the session running — discard needs the dialog. */
    @Test
    fun liveWorkoutOpensBlankFromHomeAndHomeReturns() {
        paths.awaitTag(HOME_GRAPH)

        paths.startBlankSession()
        paths.awaitTag(LIVE_WORKOUT_GRAPH)

        paths.tap("LiveWorkoutBackButton")
        paths.awaitTag(HOME_GRAPH)
    }

    /** The banner is the re-entry hop into a session that is already running, not the way in. */
    @Test
    fun liveWorkoutIsReEnteredThroughTheActiveSessionBanner() {
        paths.awaitTag(HOME_GRAPH)

        paths.startBlankSession()
        paths.awaitTag(LIVE_WORKOUT_GRAPH)
        paths.tap("LiveWorkoutBackButton")
        paths.awaitTag(HOME_GRAPH)

        paths.reEnterActiveSession()
        paths.awaitTag(LIVE_WORKOUT_GRAPH)

        paths.tap("LiveWorkoutBackButton")
        paths.awaitTag(HOME_GRAPH)
    }

    @Test
    fun exerciseOpensForCreateFromAllExercisesAndTheListReturns() {
        paths.toAllExercises()
        paths.awaitTag(ALL_EXERCISES_GRAPH)

        paths.createExercise()
        paths.awaitTag(EXERCISE_GRAPH)

        paths.tap("ExerciseEditCloseButton")
        paths.awaitTag(ALL_EXERCISES_GRAPH)
    }

    @Test
    fun singleTrainingOpensForCreateFromAllTrainingsAndTheListReturns() {
        paths.toAllTrainings()
        paths.awaitTag(ALL_TRAININGS_GRAPH)

        paths.createTraining()
        paths.awaitTag(SINGLE_TRAINING_GRAPH)

        paths.tap("TrainingEditCloseButton")
        paths.awaitTag(ALL_TRAININGS_GRAPH)
    }

    /**
     * The only route to the plan editor. A red with the editor's load error rather than a missing
     * tag is a wrong-uuid bug — see documentation/architecture.md.
     */
    @Test
    fun planEditorOpensFromALiveSessionExerciseAndTheSessionReturns() {
        paths.awaitTag(HOME_GRAPH)

        paths.startBlankSession()
        paths.awaitTag(LIVE_WORKOUT_GRAPH)

        paths.addInlineExerciseToSession(INLINE_EXERCISE_NAME)
        paths.openPlanEditorForFirstExercise()
        paths.awaitTag(PLAN_EDITOR_GRAPH)

        paths.tap("PlanEditorSave")
        paths.awaitTag(LIVE_WORKOUT_GRAPH)
    }

    // Reachable only with seeded rows.

    @Test
    fun singleTrainingOpensFromASeededRowAndTheListReturns() {
        seed.training(SEEDED_TRAINING_NAME)

        paths.toAllTrainings()
        paths.awaitTag(ALL_TRAININGS_GRAPH)

        paths.openTraining(SEEDED_TRAINING_NAME)
        paths.awaitTag(SINGLE_TRAINING_GRAPH)

        paths.tap("TrainingDetailBackButton")
        paths.awaitTag(ALL_TRAININGS_GRAPH)
    }

    @Test
    fun exerciseOpensFromASeededRowAndTheListReturns() {
        val exerciseUuid = seed.exercise(SEEDED_EXERCISE_NAME)

        paths.toAllExercises()
        paths.awaitTag(ALL_EXERCISES_GRAPH)

        paths.openExercise(exerciseUuid.toString())
        paths.awaitTag(EXERCISE_GRAPH)

        paths.tap("ExerciseDetailBackButton")
        paths.awaitTag(ALL_EXERCISES_GRAPH)
    }

    @Test
    fun pastSessionOpensFromASeededFinishedSessionAndHomeReturns() {
        val session = seed.finishedSession(
            exerciseName = SEEDED_EXERCISE_NAME,
            trainingName = SEEDED_TRAINING_NAME,
        )

        paths.awaitTag(HOME_GRAPH)

        paths.openPastSession(session.sessionUuid.toString())
        paths.awaitTag(PAST_SESSION_GRAPH)

        paths.tap("PastSessionBackButton")
        paths.awaitTag(HOME_GRAPH)
    }

    /** With no uuid the chart resolves to the most recent exercise, so it needs history. */
    @Test
    fun exerciseChartOpensFromHomeAndHomeReturns() {
        seed.finishedSession(
            exerciseName = SEEDED_EXERCISE_NAME,
            trainingName = SEEDED_TRAINING_NAME,
        )

        paths.awaitTag(HOME_GRAPH)

        paths.openExerciseChart()
        paths.awaitTag(EXERCISE_CHART_GRAPH)

        paths.tap("ExerciseChartBack")
        paths.awaitTag(HOME_GRAPH)
    }

    /** The thumbnail opens the viewer only when the exercise has an image, else it is inert. */
    @Test
    fun imageViewerOpensFromASeededExerciseImageAndTheExerciseReturns() {
        val exerciseUuid = seed.exercise(
            name = SEEDED_EXERCISE_NAME,
            imagePath = SEEDED_IMAGE_PATH,
        )

        paths.toAllExercises()
        paths.awaitTag(ALL_EXERCISES_GRAPH)

        paths.openExercise(exerciseUuid.toString())
        paths.awaitTag(EXERCISE_GRAPH)

        paths.tap("ExerciseDescriptionImage")
        paths.awaitTag(IMAGE_VIEWER_GRAPH)

        paths.tap("ImageViewerBackButton")
        paths.awaitTag(EXERCISE_GRAPH)
    }

    private companion object {

        const val HOME_GRAPH = "HomeGraph"
        const val ALL_TRAININGS_GRAPH = "AllTrainingsGraph"
        const val ALL_EXERCISES_GRAPH = "AllExercisesGraph"
        const val SINGLE_TRAINING_GRAPH = "SingleTrainingGraph"
        const val EXERCISE_GRAPH = "ExerciseGraph"
        const val EXERCISE_CHART_GRAPH = "ExerciseChartGraph"
        const val LIVE_WORKOUT_GRAPH = "LiveWorkoutGraph"
        const val PAST_SESSION_GRAPH = "PastSessionGraph"
        const val IMAGE_VIEWER_GRAPH = "ImageViewerGraph"
        const val ARCHIVE_GRAPH = "ArchiveGraph"
        const val SETTINGS_GRAPH = "SettingsGraph"
        const val PLAN_EDITOR_GRAPH = "PlanEditorGraph"

        const val SEEDED_TRAINING_NAME = "Route Reachability Training"
        const val SEEDED_EXERCISE_NAME = "Route Reachability Exercise"
        const val INLINE_EXERCISE_NAME = "Inline Plan Exercise"
        const val SEEDED_IMAGE_PATH = "/fake-image-storage/route-reachability.jpg"
    }
}
