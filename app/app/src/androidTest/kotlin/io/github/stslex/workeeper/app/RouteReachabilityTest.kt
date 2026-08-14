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
 * Nav3 migration, stage 1.1: every destination is reachable through the UI and dismissible back to
 * where it came from.
 *
 * One test per destination, each: seed -> open the way a user would -> assert the destination's
 * graph tag -> dismiss -> assert the origin returned. Parameterised destinations are opened by
 * clicking a seeded row, never by constructing a `Screen` — a test that builds its own route asserts
 * that the navigation library works, which is not what is at risk here.
 *
 * **This suite is a differential oracle.** It runs unchanged across stages 1.2 and 1.3; only the
 * implementation beneath it changes. If a test here needs editing during either, it was describing
 * the implementation rather than the behaviour, and the edit is the bug.
 *
 * All twelve destinations are covered, including the three bottom-bar roots that
 * `ApplicationBottomBarTest` nominally covers. That class asserts bottom-bar *selection* state and
 * is red on `dev` for a real production defect (`AppNavBar` builds its items with
 * `Modifier.clickable`, so no `Selected` semantics is published at all). Arrival is asserted here on
 * the graph tag, which is independent of that defect — so the oracle stands on its own rather than
 * leaning on a class that does not pass. See `documentation/tech-debt.md`.
 *
 * ## One test lands red, on a production defect
 *
 * 14 of 15 pass. [archiveOpensFromSettingsAndSettingsReturns] throws
 * `IllegalStateException: There are multiple DataStores active for the same file:
 * .../files/datastore/backup_account_prefs.preferences_pb`. That is a defect in production code,
 * not in this suite, and the test is committed red rather than muted or deleted.
 *
 * `AccountDataStoreImpl` builds its store with a per-instance
 * `by lazy { PreferenceDataStoreFactory.create { ... } }`, bypassing `DataStoreProvider`, whose
 * memoization is a **static** `ConcurrentHashMap` keyed on the file name — process-lifetime, not
 * graph-lifetime. `MetroTestRule` installs a fresh `AppGraph` per test, so the
 * `@SingleIn(AppScope)` instance is rebuilt each time and a second `DataStore` is opened over the
 * same file; DataStore 1.1+ throws instead of sharing. Two tests here reach Settings' backup
 * section, so whichever runs second is the one that throws — in the recorded run
 * [settingsOpensFromHomeAndHomeReturns] ran first and passed, and this one threw through
 * `DriveBackupAuth`'s `observeAccount` collector. The order is JUnit's, so do not read the
 * identity of the red test as fixed; the count (exactly one) is what the mechanism pins.
 *
 * **Unblocked by the next commit**, which routes `AccountDataStoreImpl` through
 * `DataStoreProvider`. Its module already depends on `:core:data:dataStore`, so that fix adds no
 * dependency edge. Three further bypasses remain and each would need one — they are out of this
 * commit's scope and recorded in `documentation/tech-debt.md`.
 *
 * @see NavPaths for the journeys, and for why arrival waits on an explicit timeout.
 * @see NavSeed for the rows, and for the two schema rules a call site cannot see.
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

    // ----- bottom-bar roots: no seed, one click -------------------------------------------------

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

    // ----- reachable with no seeded rows --------------------------------------------------------

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

    /**
     * The blank-start path needs no seeded rows: the session and the training row behind it are
     * created on arrival. Plain back does not discard the session — discard is only reachable
     * through the explicit confirm dialog — so Home returns with the session still running.
     */
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
     * The deepest route in the app, and the only one that reaches the plan editor:
     * Home -> blank session -> add an exercise inline -> its kebab -> Edit plan.
     *
     * Still seed-free. The picker offers to create whatever name has no exact match, which on an
     * empty database is every name.
     *
     * If this ever fails with the plan editor's load error rather than a missing tag, suspect the
     * uuid: `PlanEditor.Existing` carries three, and the editor's load reads `exerciseUuid` — never
     * `performedExerciseUuid`. A wrong one resolves to `NotFound` and surfaces as an error event, not
     * as a screen stuck loading.
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

    // ----- reachable only with seeded rows ------------------------------------------------------

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

    /**
     * The chart resolves to the most recently trained exercise when opened with no uuid, so it needs
     * finished-session history to render as anything but an empty state.
     */
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

    /**
     * The image viewer is reached from inside an exercise, and only when the exercise already has an
     * image: the thumbnail's click handler returns early on `ImageDisplay.None`, so on an image-less
     * exercise the same tag is inert in detail mode and opens the source picker in edit mode.
     */
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
