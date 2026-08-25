// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.app

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
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
 * Store scoping across navigation: retention, isolation, disposal. Entry scoping fails silently —
 * a Store resolved against the Activity just becomes process-scoped. See tech-debt.md.
 */
@OptIn(ExperimentalTestApi::class)
@Regression
@RunWith(AndroidJUnit4::class)
internal class StoreRetentionTest {

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
     * RETENTION: a retained Store carries the expansion set across a plan-editor round trip; a
     * recreated one falls back to the mapper's first-card seed. See documentation/tech-debt.md.
     */
    @Test
    fun liveWorkoutStoreSurvivesThePlanEditorRoundTrip() {
        paths.awaitTag(HOME_GRAPH)
        paths.startBlankSession()
        paths.awaitTag(LIVE_WORKOUT_GRAPH)

        paths.addInlineExerciseToSession("Retention Probe One")
        val firstUuid = paths
            .tagsStartingWith(CARD_SUB_PREFIX, atLeast = 1, useUnmergedTree = true)
            .single()
            .removePrefix(CARD_SUB_PREFIX)

        paths.addInlineExerciseToSession("Retention Probe Two")
        val secondUuid = paths
            .tagsStartingWith(CARD_SUB_PREFIX, atLeast = 2, useUnmergedTree = true)
            .map { it.removePrefix(CARD_SUB_PREFIX) }
            .single { it != firstUuid }

        awaitExpanded(firstUuid)
        awaitExpanded(secondUuid)

        paths.openPlanEditorForFirstExercise()
        paths.awaitTag(PLAN_EDITOR_GRAPH)
        paths.tap("PlanEditorBack")
        paths.awaitTag(LIVE_WORKOUT_GRAPH)

        // Discriminator: a recreated Store resets expansion to the first-card seed.
        awaitExpanded(secondUuid)
        awaitExpanded(firstUuid)

        val cardsAfter = paths
            .tagsStartingWith(CARD_SUB_PREFIX, atLeast = 2, useUnmergedTree = true)
            .map { it.removePrefix(CARD_SUB_PREFIX) }
        check(cardsAfter.toSet() == setOf(firstUuid, secondUuid)) {
            "Session forked across the round trip: $cardsAfter vs [$firstUuid, $secondUuid]"
        }
    }

    /**
     * ISOLATION: a second exercise's chart must arrive at the default preset. Retention alone
     * stays green under an Activity-scoped Store; this assertion cannot.
     */
    @Test
    fun exerciseChartStateDoesNotLeakBetweenEntities() {
        val alpha = seed.finishedSession("Isolation Probe Alpha", "Isolation Training Alpha")
        val beta = seed.finishedSession("Isolation Probe Beta", "Isolation Training Beta")

        paths.awaitTag(HOME_GRAPH)
        paths.toAllExercises()
        paths.awaitTag(ALL_EXERCISES_GRAPH)

        openChartFor(alpha.exerciseUuid.toString())
        assertPresetSelected("ALL")
        paths.tap("ChartPresetChip_MONTH_1")
        assertPresetSelected("MONTH_1")

        paths.tap("ExerciseChartBack")
        paths.awaitTag(EXERCISE_GRAPH)
        paths.tap("ExerciseDetailBackButton")
        paths.awaitTag(ALL_EXERCISES_GRAPH)

        openChartFor(beta.exerciseUuid.toString())
        assertPresetSelected("ALL")
    }

    /** DISPOSAL: a Store dies with its entry — pop and re-open arrives at the default preset. */
    @Test
    fun exerciseChartStoreIsDestroyedWhenItsEntryLeavesTheStack() {
        seed.finishedSession("Disposal Probe", "Disposal Training")

        paths.awaitTag(HOME_GRAPH)
        paths.openExerciseChart()
        paths.awaitTag(EXERCISE_CHART_GRAPH)

        assertPresetSelected("ALL")
        paths.tap("ChartPresetChip_YEAR_1")
        assertPresetSelected("YEAR_1")

        paths.tap("ExerciseChartBack")
        paths.awaitTag(HOME_GRAPH)

        paths.openExerciseChart()
        paths.awaitTag(EXERCISE_CHART_GRAPH)
        assertPresetSelected("ALL")
    }

    /** Open the chart for [exerciseUuid] through its detail screen's record hero. */
    private fun openChartFor(exerciseUuid: String) {
        paths.openExercise(exerciseUuid)
        paths.awaitTag(EXERCISE_GRAPH)
        paths.tap("ExerciseDetailRecordHero")
        paths.awaitTag(EXERCISE_CHART_GRAPH)
    }

    /** `LiveExerciseCard_AddSet_<uuid>` is composed iff the card body is expanded. */
    private fun awaitExpanded(performedExerciseUuid: String) {
        composeRule.waitUntilAtLeastOneExists(
            hasTestTag("LiveExerciseCard_AddSet_$performedExerciseUuid"),
            NavPaths.ARRIVAL_TIMEOUT_MS,
        )
    }

    private fun assertPresetSelected(presetName: String) {
        PRESET_NAMES.forEach { name ->
            composeRule.onNodeWithTag("ChartPresetChip_$name").apply {
                if (name == presetName) assertIsSelected() else assertIsNotSelected()
            }
        }
    }

    private companion object {

        const val HOME_GRAPH = "HomeGraph"
        const val LIVE_WORKOUT_GRAPH = "LiveWorkoutGraph"
        const val PLAN_EDITOR_GRAPH = "PlanEditorGraph"
        const val EXERCISE_GRAPH = "ExerciseGraph"
        const val ALL_EXERCISES_GRAPH = "AllExercisesGraph"
        const val EXERCISE_CHART_GRAPH = "ExerciseChartGraph"

        const val CARD_SUB_PREFIX = "LiveExerciseCardSub_"

        /** `ChartPresetUiModel.entries` by name; androidTest cannot see the feature enum. */
        val PRESET_NAMES = listOf("MONTH_1", "MONTHS_3", "YEAR_1", "ALL")
    }
}
