// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.app

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTouchInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.stslex.workeeper.MainActivity
import io.github.stslex.workeeper.core.ui.test.annotations.Regression
import io.github.stslex.workeeper.harness.MetroTestRule
import io.github.stslex.workeeper.harness.NavPaths
import io.github.stslex.workeeper.harness.NavSeed
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Per-entry state and back-stack shape across leaving-and-returning. Both regress silently: the
 * list re-opens at the top, a draft vanishes, the stack collapses to its root.
 */
@OptIn(ExperimentalTestApi::class)
@Regression
@RunWith(AndroidJUnit4::class)
internal class BackStackStateRestorationTest {

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

    /** SCROLL: the return from a detail lands where the user left the list, not at the top. */
    @Test
    fun listScrollPositionSurvivesTheDetailRoundTrip() {
        val uuids = (1..SEEDED_ROWS).map { index ->
            seed.exercise("Restoration Probe %02d".format(index)).toString()
        }

        paths.awaitTag(HOME_GRAPH)
        paths.toAllExercises()
        paths.awaitTag(ALL_EXERCISES_GRAPH)

        // The target is a seeded row not composed on arrival, so the scroll is a real displacement.
        val initiallyComposed = paths
            .tagsStartingWith(ROW_PREFIX, atLeast = 1)
            .toSet()
        val topRowTag = initiallyComposed.first()
        val targetTag = uuids.map { ROW_PREFIX + it }.first { it !in initiallyComposed }

        scrollListUntilComposed(targetTag)

        paths.tap(targetTag)
        paths.awaitTag(EXERCISE_GRAPH)
        paths.tap("ExerciseDetailBackButton")
        paths.awaitTag(ALL_EXERCISES_GRAPH)

        // A reset-to-top list inverts both assertions.
        composeRule.waitUntilAtLeastOneExists(hasTestTag(targetTag), NavPaths.ARRIVAL_TIMEOUT_MS)
        composeRule.onNodeWithTag(targetTag).assertIsDisplayed()
        check(
            composeRule.onAllNodesWithTag(topRowTag).fetchSemanticsNodes().isEmpty(),
        ) { "The pre-scroll top row is composed again after the round trip — the list reset to the top." }
    }

    /**
     * DRAFT: the re-fired `Init` after the viewer round trip may refresh the persisted baseline but
     * must carry the dirty editor fields forward.
     */
    @Test
    fun editorDraftSurvivesTheImageViewerRoundTrip() {
        val exerciseUuid = seed.exercise(
            name = "Draft Probe",
            imagePath = "/restoration-probe/image.jpg",
        )

        paths.awaitTag(HOME_GRAPH)
        paths.toAllExercises()
        paths.awaitTag(ALL_EXERCISES_GRAPH)
        paths.openExercise(exerciseUuid.toString())
        paths.awaitTag(EXERCISE_GRAPH)

        paths.tap("ExerciseEditButton")
        paths.awaitTag("ExerciseEditActionBar")
        paths.typeInto("ExerciseEditNameField", DRAFT_SUFFIX)

        // Without this, a broken text input would make the assertion below pass vacuously.
        composeRule
            .onNodeWithTag("ExerciseEditNameField")
            .assertTextContains(value = DRAFT_SUFFIX, substring = true)

        paths.tap("ExerciseDescriptionImage")
        paths.awaitTag(IMAGE_VIEWER_GRAPH)
        paths.tap("ImageViewerBackButton")
        paths.awaitTag(EXERCISE_GRAPH)

        composeRule.waitForIdle()
        composeRule
            .onNodeWithTag("ExerciseEditNameField")
            .assertTextContains(value = DRAFT_SUFFIX, substring = true)
    }

    /**
     * SELECTION MODE: a tab tap pops its root inclusively and nothing restores it, so the return is
     * a fresh entry. A selection that survives is as much a regression as a draft that vanishes.
     */
    @Test
    fun selectionModeArrivesResetAfterABottomBarRoundTrip() {
        val exerciseUuid = seed.exercise("Selection Probe")

        paths.awaitTag(HOME_GRAPH)
        paths.toAllExercises()
        paths.awaitTag(ALL_EXERCISES_GRAPH)

        composeRule.waitUntilAtLeastOneExists(
            hasTestTag(ROW_PREFIX + exerciseUuid),
            NavPaths.ARRIVAL_TIMEOUT_MS,
        )
        composeRule
            .onNodeWithTag(ROW_PREFIX + exerciseUuid)
            .performTouchInput { longClick() }
        paths.awaitTag("AllExercisesSelectionTopBar")

        paths.toHome()
        paths.awaitTag(HOME_GRAPH)
        paths.toAllExercises()
        paths.awaitTag(ALL_EXERCISES_GRAPH)

        paths.awaitTag("AllExercisesTopBar")
        check(
            composeRule.onAllNodesWithTag("AllExercisesSelectionTopBar").fetchSemanticsNodes().isEmpty(),
        ) { "Selection mode survived a bottom-bar round trip — tab taps no longer arrive reset." }
    }

    /** DEPTH: Home → Settings → Archive survives recreation and unwinds back through Settings. */
    @Test
    fun backStackDepthSurvivesActivityRecreation() {
        paths.awaitTag(HOME_GRAPH)
        paths.openSettings()
        paths.awaitTag(SETTINGS_GRAPH)
        paths.openArchiveFromSettings()
        paths.awaitTag(ARCHIVE_GRAPH)

        composeRule.activityRule.scenario.recreate()

        paths.awaitTag(ARCHIVE_GRAPH)
        paths.tap("ArchiveBackButton")
        paths.awaitTag(SETTINGS_GRAPH)
        paths.tap("SettingsBackButton")
        paths.awaitTag(HOME_GRAPH)
    }

    /**
     * GUARD: scroll semantically, never by touch injection — injected flings do not scroll this
     * list on CI's x86_64 emulator profile. See documentation/testing.md.
     */
    private fun scrollListUntilComposed(tag: String) {
        composeRule
            .onNodeWithTag("AllExercisesList")
            .performScrollToNode(hasTestTag(tag))
        composeRule.waitUntilAtLeastOneExists(hasTestTag(tag), NavPaths.ARRIVAL_TIMEOUT_MS)
    }

    private companion object {

        const val HOME_GRAPH = "HomeGraph"
        const val ALL_EXERCISES_GRAPH = "AllExercisesGraph"
        const val EXERCISE_GRAPH = "ExerciseGraph"
        const val IMAGE_VIEWER_GRAPH = "ImageViewerGraph"
        const val SETTINGS_GRAPH = "SettingsGraph"
        const val ARCHIVE_GRAPH = "ArchiveGraph"

        const val ROW_PREFIX = "AllExercisesItem_"
        const val DRAFT_SUFFIX = " Amended"

        /** More rows than any sane viewport composes at once — the scroll is a real displacement. */
        const val SEEDED_ROWS = 14
    }
}
