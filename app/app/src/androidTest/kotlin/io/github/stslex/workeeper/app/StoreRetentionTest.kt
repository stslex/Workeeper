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
 * Store scoping across navigation — the third of stage 1.1's four oracle classes, written at the
 * pre-1.3 oracle-completion step (the 1.1 spec named it; it never shipped, filed in tech-debt.md).
 *
 * Guards the axis stage 1.3 breaks silently: under Nav3, a missing
 * `rememberViewModelStoreNavEntryDecorator()` makes `viewModel { }` resolve against the Activity's
 * store — nothing crashes, every Store becomes process-scoped. Three assertions, per the spec:
 *
 * 1. **retention** — a Store's non-default state survives a round trip to another destination and
 *    back while its entry stays on the stack;
 * 2. **isolation** — the same destination opened for a different entity arrives at DEFAULT state
 *    (the assertion that separates entry-scoped from Activity-scoped; retention alone stays green
 *    under a broken scope);
 * 3. **disposal** — a Store dies with its entry: pop and re-open arrives at default state.
 *
 * The retention case is also the discriminator the tech-debt entry on `NavigationResultTest`'s
 * plan-editor half demanded ("settle this in `StoreRetentionTest`; do not assume either answer").
 * It settles it: see the test's own KDoc.
 *
 * All journeys reach the app through the semantics tree only — no `androidx.navigation` import,
 * enforced by `:app:app:detektAndroidTestNavigation` — so the class runs unchanged across the swap.
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
     * RETENTION — and the answer to the open question in tech-debt.md's "two oracle classes were
     * never written" entry.
     *
     * The LiveWorkout Store keeps `expandedExerciseUuids` in State precisely so a plan-editor round
     * trip preserves it. A blank session with two inline-added exercises has BOTH cards expanded
     * (the mapper seeds the first, the picker-add inserts each new one). The round trip goes
     * through a CLEAN plan editor dismissed with its back button — `interceptBack` is false, the
     * exit is a plain `popBack()` with NO result — so the only thing that re-runs `loadSession` on
     * return is the re-fired `Action.Common.Init` (`BaseStore.init` re-fires `initialActions` on
     * every composition re-entry).
     *
     * The outcomes are exactly inverted, which is what makes this a discriminator:
     * - Store RETAINED  → `processInit` runs `withExpansionCarriedFrom(previous)` over live State →
     *   both cards still expanded (`LiveExerciseCard_AddSet_<uuid>` exists iff expanded);
     * - Store RECREATED → `previous.exercises` is empty, the carry guard returns the fresh snapshot
     *   → the mapper's first-card seed only → the second card's AddSet node never appears (and a
     *   recreated Store would additionally mint a SECOND blank session, forking the card list).
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

        // Pre-trip state, measured not assumed: both cards expanded.
        awaitExpanded(firstUuid)
        awaitExpanded(secondUuid)

        paths.openPlanEditorForFirstExercise()
        paths.awaitTag(PLAN_EDITOR_GRAPH)
        paths.tap("PlanEditorBack")
        paths.awaitTag(LIVE_WORKOUT_GRAPH)

        // The discriminating assertion: the SECOND card is still expanded. A recreated Store
        // resets expansion to the mapper's first-card seed and this node never returns.
        awaitExpanded(secondUuid)
        awaitExpanded(firstUuid)

        // And the session did not fork: still exactly the same two cards.
        val cardsAfter = paths
            .tagsStartingWith(CARD_SUB_PREFIX, atLeast = 2, useUnmergedTree = true)
            .map { it.removePrefix(CARD_SUB_PREFIX) }
        check(cardsAfter.toSet() == setOf(firstUuid, secondUuid)) {
            "Session forked across the round trip: $cardsAfter vs [$firstUuid, $secondUuid]"
        }
    }

    /**
     * ISOLATION — `ExerciseChart`, the spec's named candidate: the only parameterised destination
     * with no `BackHandler` intercept on the way out and a clean constant default
     * (`ChartPresetUiModel.ALL`).
     *
     * Chart A gets a non-default preset; chart B — a DIFFERENT exercise reached through its own
     * detail screen's record hero — must arrive at the default. Retention alone stays green under
     * an Activity-scoped Store; this is the assertion that cannot.
     *
     * Selection is read from `SemanticsProperties.Selected` on the preset chips
     * (`ChartPresetChip_<NAME>`), which `AppTag` publishes via `Modifier.selectable` — the same
     * semantics contract the bottom bar publishes since the navbar-a11y PR.
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

    /**
     * DISPOSAL — a Store dies when its entry leaves the back stack for good. The chart is popped
     * with its own back button and re-opened from Home; the second visit is a NEW entry and must
     * arrive at the default preset, not the previous visit's.
     */
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

    /**
     * `LiveExerciseCard_AddSet_<uuid>` is composed iff the card body is expanded, and it sits on
     * the merged tree (its `SetBarButton` owns its own click). Waited, not sampled: expansion
     * lands a recomposition after the Store updates.
     */
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

        /** `ChartPresetUiModel.entries` by name — duplicated here because androidTest cannot see a feature module's enum. */
        val PRESET_NAMES = listOf("MONTH_1", "MONTHS_3", "YEAR_1", "ALL")
    }
}
