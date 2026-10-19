// SPDX-License-Identifier: GPL-3.0-only
@file:OptIn(ExperimentalTestApi::class)

package io.github.stslex.workeeper.feature.exercise_chart

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.v2.runComposeUiTest
import io.github.stslex.workeeper.core.core.resources.ResourceWrapper
import io.github.stslex.workeeper.core.ui.kit.strings.ComposeStringResolver
import io.github.stslex.workeeper.core.ui.kit.theme.AppTheme
import io.github.stslex.workeeper.core.ui.plan_editor.model.ExerciseTypeUiModel
import io.github.stslex.workeeper.feature.exercise_chart.domain.model.ChartFooterStatsDomain
import io.github.stslex.workeeper.feature.exercise_chart.domain.model.ChartPointDomain
import io.github.stslex.workeeper.feature.exercise_chart.domain.model.ExerciseTypeDomain
import io.github.stslex.workeeper.feature.exercise_chart.mvi.mapper.ChartReadoutMapper
import io.github.stslex.workeeper.feature.exercise_chart.mvi.mapper.ExerciseChartUiMapper.toUi
import io.github.stslex.workeeper.feature.exercise_chart.mvi.model.ChartFooterStatsUiModel
import io.github.stslex.workeeper.feature.exercise_chart.mvi.model.ChartMetricUiModel
import io.github.stslex.workeeper.feature.exercise_chart.mvi.model.ChartPointUiModel
import io.github.stslex.workeeper.feature.exercise_chart.mvi.model.ChartPresetUiModel
import io.github.stslex.workeeper.feature.exercise_chart.mvi.model.ChartReadoutCopyUiModel
import io.github.stslex.workeeper.feature.exercise_chart.mvi.model.ChartReadoutUiModel
import io.github.stslex.workeeper.feature.exercise_chart.mvi.model.ExercisePickerItemUiModel
import io.github.stslex.workeeper.feature.exercise_chart.mvi.store.ExerciseChartStore.Action
import io.github.stslex.workeeper.feature.exercise_chart.mvi.store.ExerciseChartStore.EmptyReason
import io.github.stslex.workeeper.feature.exercise_chart.mvi.store.ExerciseChartStore.State
import io.github.stslex.workeeper.feature.exercise_chart.resources.Res
import io.github.stslex.workeeper.feature.exercise_chart.resources.feature_exercise_chart_empty_no_data_subtitle
import io.github.stslex.workeeper.feature.exercise_chart.resources.feature_exercise_chart_empty_no_data_title
import io.github.stslex.workeeper.feature.exercise_chart.resources.feature_exercise_chart_empty_no_sessions_cta
import io.github.stslex.workeeper.feature.exercise_chart.resources.feature_exercise_chart_empty_no_sessions_subtitle
import io.github.stslex.workeeper.feature.exercise_chart.resources.feature_exercise_chart_empty_no_sessions_title
import io.github.stslex.workeeper.feature.exercise_chart.resources.feature_exercise_chart_empty_not_found_cta
import io.github.stslex.workeeper.feature.exercise_chart.resources.feature_exercise_chart_empty_not_found_subtitle
import io.github.stslex.workeeper.feature.exercise_chart.resources.feature_exercise_chart_empty_not_found_title
import io.github.stslex.workeeper.feature.exercise_chart.resources.feature_exercise_chart_error_load_cta
import io.github.stslex.workeeper.feature.exercise_chart.resources.feature_exercise_chart_error_load_subtitle
import io.github.stslex.workeeper.feature.exercise_chart.resources.feature_exercise_chart_error_load_title
import io.github.stslex.workeeper.feature.exercise_chart.resources.feature_exercise_chart_footer_last
import io.github.stslex.workeeper.feature.exercise_chart.resources.feature_exercise_chart_footer_max
import io.github.stslex.workeeper.feature.exercise_chart.resources.feature_exercise_chart_footer_min
import io.github.stslex.workeeper.feature.exercise_chart.resources.feature_exercise_chart_metric_heaviest
import io.github.stslex.workeeper.feature.exercise_chart.resources.feature_exercise_chart_metric_name_heaviest
import io.github.stslex.workeeper.feature.exercise_chart.resources.feature_exercise_chart_metric_name_session
import io.github.stslex.workeeper.feature.exercise_chart.resources.feature_exercise_chart_metric_name_volume
import io.github.stslex.workeeper.feature.exercise_chart.resources.feature_exercise_chart_metric_session
import io.github.stslex.workeeper.feature.exercise_chart.resources.feature_exercise_chart_metric_volume
import io.github.stslex.workeeper.feature.exercise_chart.resources.feature_exercise_chart_picker_no_matches
import io.github.stslex.workeeper.feature.exercise_chart.resources.feature_exercise_chart_picker_open
import io.github.stslex.workeeper.feature.exercise_chart.resources.feature_exercise_chart_picker_search
import io.github.stslex.workeeper.feature.exercise_chart.resources.feature_exercise_chart_picker_title
import io.github.stslex.workeeper.feature.exercise_chart.resources.feature_exercise_chart_preset_1m
import io.github.stslex.workeeper.feature.exercise_chart.resources.feature_exercise_chart_preset_1y
import io.github.stslex.workeeper.feature.exercise_chart.resources.feature_exercise_chart_preset_3m
import io.github.stslex.workeeper.feature.exercise_chart.resources.feature_exercise_chart_preset_all
import io.github.stslex.workeeper.feature.exercise_chart.resources.feature_exercise_chart_readout_record
import io.github.stslex.workeeper.feature.exercise_chart.resources.feature_exercise_chart_readout_sets
import io.github.stslex.workeeper.feature.exercise_chart.resources.feature_exercise_chart_unit_kg
import io.github.stslex.workeeper.feature.exercise_chart.resources.feature_exercise_chart_unit_reps
import io.github.stslex.workeeper.feature.exercise_chart.resources.feature_exercise_chart_value_reps
import io.github.stslex.workeeper.feature.exercise_chart.ui.ExerciseChartScreen
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentMapOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ExerciseChartFeatureSceneIosTest {

    /** Every read outside composition goes through this one real resolver: no fake here. */
    private val resolver = ComposeStringResolver()

    /**
     * `ResourceWrapper` has no iOS implementation (S4), so the day-month date is stubbed for
     * exactly the four days the scene renders. It is the scene's only test double.
     */
    private val dates = object : ResourceWrapper {
        override fun formatDayMonth(timestamp: Long): String = when (timestamp) {
            millis(MAY_2) -> "May 2"
            millis(JUN_1) -> "Jun 1"
            millis(JUL_11) -> "Jul 11"
            millis(JUL_23) -> "Jul 23"
            else -> error("no day-month date is stubbed for $timestamp")
        }

        override fun getString(id: Int, vararg args: Any): String =
            error("exercise-chart reads no copy through ResourceWrapper; id $id")

        override fun getQuantityString(id: Int, quantity: Int, vararg args: Any): String =
            error("exercise-chart reads no plural through ResourceWrapper; id $id")

        override fun getAbbreviatedRelativeTime(timestamp: Long, now: Long): String =
            error("exercise-chart formats no relative time")

        override fun formatMediumDate(timestamp: Long): String =
            error("exercise-chart formats no medium date")
    }

    @Test
    fun resourcesReadoutFooterStatesPickerAndActionsRenderAndDispatch() = runComposeUiTest {
        // 1. The private catalog in file order through the real resolver: as stored, then each
        // plural filled with its count as the one format argument.
        assertEquals(EXPECTED_STORED, storedCatalog())
        assertEquals(listOf("1 set", "2 sets", "1 rep", "2 reps"), filledPlurals())

        // 2. Copy through the production mappers: the real resolver, and the stub for the date.
        val c1 = runBlocking { ChartReadoutMapper.toReadoutCopy(P, resolver) }
        assertEquals(
            ChartReadoutCopyUiModel(
                heaviestWeightName = "Heaviest weight",
                volumePerSessionName = "Volume per session",
                volumePerSetName = "Volume per set",
                kgUnit = "kg",
                repsUnit = "reps",
                recordLabel = "record",
                setCountLabels = persistentMapOf(4 to "4 sets", 1 to "1 set"),
            ),
            c1,
        )
        val r1 = ChartReadoutMapper.toReadout(P, 2, ChartMetricUiModel.HEAVIEST_WEIGHT, WEIGHTED, c1, dates)
        assertEquals(
            ChartReadoutUiModel("Heaviest weight", true, "Jul 11 2026 · 4 sets · record", "77", "kg"),
            r1,
        )
        assertEquals(
            ChartReadoutUiModel("Heaviest weight", false, "Jun 1 2026 · 1 set", "63", "kg"),
            ChartReadoutMapper.toReadout(P, 1, ChartMetricUiModel.HEAVIEST_WEIGHT, WEIGHTED, c1, dates),
        )
        assertNull(ChartReadoutMapper.toReadout(P, null, ChartMetricUiModel.HEAVIEST_WEIGHT, WEIGHTED, c1, dates))
        val gCopy = runBlocking { ChartReadoutMapper.toReadoutCopy(G, resolver) }
        assertEquals(
            ChartReadoutUiModel("Volume per session", true, "Jul 23 2026 · 4 sets · record", "4 620", "kg"),
            ChartReadoutMapper.toReadout(G, 1, ChartMetricUiModel.VOLUME_PER_SESSION, WEIGHTED, gCopy, dates),
        )
        val wCopy = runBlocking { ChartReadoutMapper.toReadoutCopy(W, resolver) }
        val r5 = ChartReadoutMapper.toReadout(W, 0, ChartMetricUiModel.VOLUME_PER_SET, WEIGHTLESS, wCopy, dates)
        assertEquals(ChartReadoutUiModel("Volume per set", false, "May 2 2026 · 3 sets", "8", "reps"), r5)
        val f1 = runBlocking { footer(49.0, 77.0, 70.0).toUi(ExerciseTypeDomain.WEIGHTED, resolver) }
        assertEquals(ChartFooterStatsUiModel("Minimum", "49", "Maximum", "77", "Last", "70", "kg"), f1)
        assertEquals(
            ChartFooterStatsUiModel(
                "Minimum",
                "2 940",
                "Maximum",
                "4 620",
                "Last",
                "4 620",
                "kg",
            ),
            runBlocking { footer(2940.0, 4620.4, 4620.4).toUi(ExerciseTypeDomain.WEIGHTED, resolver) },
        )
        val f3 = runBlocking { footer(1.0, 12.0, 8.0).toUi(ExerciseTypeDomain.WEIGHTLESS, resolver) }
        assertEquals(ChartFooterStatsUiModel("Minimum", "1 rep", "Maximum", "12 reps", "Last", "8 reps", null), f3)

        // 3. The screen, 4. the picker and 5. dispatch, through one test-held State.
        val loading = State.create(initialUuid = "ex-1")
        val weighted = loading.copy(
            isLoading = false,
            selectedExercise = BENCH,
            recentExercises = persistentListOf(BENCH, PULL_UP),
            points = P.toImmutableList(),
            metric = ChartMetricUiModel.HEAVIEST_WEIGHT,
            preset = ChartPresetUiModel.ALL,
            activeIndex = 2,
            readout = r1,
            readoutCopy = c1,
            footerStats = f1,
        )
        val state = mutableStateOf(loading)
        val actions = mutableListOf<Action>()
        setContent {
            AppTheme {
                ExerciseChartScreen(state = state.value, consume = actions::add)
            }
        }

        // Loading: the shell and its back button only.
        settleUntilTag("ExerciseChartBack")
        onNodeWithTag("ExerciseChartBack").assertContentDescriptionEquals("Back")
        listOf(
            "ExerciseChartPickerOpen", "ChartMetricTabs", "ChartCanvas", "ChartReadout", "ChartFooterStats",
            NO_SESSIONS, NOT_FOUND, NO_DATA, LOAD_FAILED,
        ).forEach { tag -> onNodeWithTag(tag, useUnmergedTree = true).assertDoesNotExist() }

        // The weighted plot.
        state.value = weighted
        settleUntilTag("ChartCanvas")
        onNodeWithTag("ExerciseChartPickerOpen")
            .assert(clickLabel("Switch exercise"))
        onNode(within("ExerciseChartPickerOpen", "Bench press"), useUnmergedTree = true).assertIsDisplayed()
        listOf("Weight", "Session", "Set", "1M", "3M", "1Y", "All").forEach { label ->
            onNodeWithText(label, useUnmergedTree = true).assertIsDisplayed()
        }
        onNodeWithText("HEAVIEST WEIGHT", useUnmergedTree = true).assertIsDisplayed()
        onNodeWithText("Jul 11 2026 · 4 sets · record", useUnmergedTree = true).assertIsDisplayed()
        onNodeWithTag("ChartReadoutRecordDot", useUnmergedTree = true).assertExists()
        onNodeWithTag("ChartCanvas").assertIsDisplayed()
        listOf("Minimum", "Maximum", "Last", "49", "70").forEach { text ->
            onNode(within("ChartFooterStats", text), useUnmergedTree = true).assertIsDisplayed()
        }
        onAllNodesWithText("77", useUnmergedTree = true).assertCountEquals(2)
        onAllNodesWithText("kg", useUnmergedTree = true).assertCountEquals(4)
        onNodeWithTag("ExerciseChartBack").performClick()
        onNodeWithTag("ExerciseChartPickerOpen").performClick()
        onNodeWithTag("ChartMetricTab_VOLUME_PER_SET").performClick()
        onNodeWithTag("ChartPresetChip_MONTH_1").performClick()
        onNodeWithTag("ChartCanvas").performTouchInput {
            down(Offset(1f, height / 2f))
            up()
        }
        onNodeWithTag("ChartCanvas").performTouchInput {
            down(Offset(width - 1f, height / 2f))
            up()
        }

        // The weightless plot: no metric tabs, rep plurals, no unit word.
        state.value = loading.copy(
            isLoading = false,
            selectedExercise = PULL_UP,
            recentExercises = persistentListOf(BENCH, PULL_UP),
            points = W.toImmutableList(),
            metric = ChartMetricUiModel.VOLUME_PER_SET,
            activeIndex = 0,
            readout = r5,
            readoutCopy = wCopy,
            footerStats = f3,
        )
        settleUntil(hasText("VOLUME PER SET"))
        onNodeWithTag("ChartMetricTabs", useUnmergedTree = true).assertDoesNotExist()
        listOf("1M", "3M", "1Y", "All", "May 2 2026 · 3 sets", "8", "reps", "1 rep", "12 reps", "8 reps")
            .forEach { text -> onNodeWithText(text, useUnmergedTree = true).assertIsDisplayed() }
        onNodeWithTag("ChartReadoutRecordDot", useUnmergedTree = true).assertDoesNotExist()
        onAllNodesWithText("kg", useUnmergedTree = true).assertCountEquals(0)

        // Each empty state, with its catalog rows.
        state.value = loading.copy(isLoading = false, emptyReason = EmptyReason.NO_FINISHED_SESSIONS)
        settleUntilTag(NO_SESSIONS)
        assertEmptyState(
            NO_SESSIONS,
            "No finished workouts yet",
            "Finish a session — your progress will appear here.",
            "Start a workout",
        )
        onNodeWithTag("ChartMetricTabs", useUnmergedTree = true).assertDoesNotExist()
        onNodeWithTag("ChartPresetChip_ALL", useUnmergedTree = true).assertDoesNotExist()
        onNodeWithText("Start a workout").performClick()

        state.value = loading.copy(
            initialUuid = "ex-404",
            isLoading = false,
            recentExercises = persistentListOf(BENCH, PULL_UP),
            emptyReason = EmptyReason.EXERCISE_NOT_FOUND,
        )
        settleUntilTag(NOT_FOUND)
        assertEmptyState(
            NOT_FOUND,
            "Exercise unavailable",
            "It may be archived or have no logged sets. Pick another from the list.",
            "Choose exercise",
        )
        onNodeWithText("Choose exercise").performClick()

        state.value = loading.copy(
            isLoading = false,
            selectedExercise = BENCH,
            recentExercises = persistentListOf(BENCH, PULL_UP),
            points = persistentListOf(P.first()),
            emptyReason = EmptyReason.NO_DATA_FOR_EXERCISE,
        )
        settleUntilTag(NO_DATA)
        assertEmptyState(
            NO_DATA,
            "Nothing to show yet",
            "The chart appears after two recorded sessions with this exercise.",
            null,
        )
        onNodeWithTag("ChartMetricTabs").assertIsDisplayed()
        listOf("1M", "3M", "1Y", "All").forEach { label ->
            onNodeWithText(label, useUnmergedTree = true).assertIsDisplayed()
        }
        onNodeWithTag("ChartCanvas", useUnmergedTree = true).assertDoesNotExist()

        state.value = loading.copy(isLoading = false, emptyReason = EmptyReason.LOAD_FAILED)
        settleUntilTag(LOAD_FAILED)
        assertEmptyState(
            LOAD_FAILED,
            "Couldn’t load the chart",
            "Something went wrong while reading your history. Try again.",
            "Try again",
        )
        onNodeWithText("Try again").performClick()

        // 4. The picker over the weighted plot, then filtered to nothing.
        state.value = weighted.copy(isPickerOpen = true)
        settleUntilTag("ExerciseChartPickerItem_ex-2")
        onNodeWithText("Select exercise", useUnmergedTree = true).assertIsDisplayed()
        onNode(within("ExerciseChartPickerSearch", "Search"), useUnmergedTree = true).assertExists()
        onNode(within("ExerciseChartPickerItem_ex-1", "Bench press"), useUnmergedTree = true).assertIsDisplayed()
        onNode(
            hasTestTag("ExerciseChartPickerCheck") and hasAnyAncestor(hasTestTag("ExerciseChartPickerItem_ex-1")),
            useUnmergedTree = true,
        ).assertExists()
        onNode(within("ExerciseChartPickerItem_ex-2", "Pull-up"), useUnmergedTree = true).assertIsDisplayed()
        onNode(
            hasTestTag("ExerciseChartPickerCheck") and hasAnyAncestor(hasTestTag("ExerciseChartPickerItem_ex-2")),
            useUnmergedTree = true,
        ).assertDoesNotExist()
        onNodeWithTag("ExerciseChartPickerItem_ex-2").performClick()
        onNodeWithTag("ExerciseChartPickerSearch").performTextReplacement("zz")

        state.value = weighted.copy(isPickerOpen = true, pickerQuery = "zz")
        settleUntilTag("ExerciseChartPickerNoMatches")
        onNode(hasTestTag("ExerciseChartPickerNoMatches") and hasText("Nothing found"), useUnmergedTree = true)
            .assertIsDisplayed()
        onNodeWithTag("ExerciseChartPickerItem_ex-1", useUnmergedTree = true).assertDoesNotExist()
        onNodeWithTag("ExerciseChartPickerItem_ex-2", useUnmergedTree = true).assertDoesNotExist()

        // 5. Dispatch: every interaction above, as one exact list.
        assertEquals(
            listOf<Action>(
                Action.Click.OnBack,
                Action.Click.OnPickerOpen,
                Action.Click.OnMetricSelect(ChartMetricUiModel.VOLUME_PER_SET),
                Action.Click.OnPresetSelect(ChartPresetUiModel.MONTH_1),
                Action.Click.OnScrub(0),
                Action.Click.OnScrub(3),
                Action.Click.OnEmptyCtaClick,
                Action.Click.OnPickerOpen,
                Action.Click.OnRetryLoad,
                Action.Click.OnPickerItemSelect("ex-2"),
                Action.Click.OnPickerQueryChange("zz"),
            ),
            actions,
        )
    }

    /** The empty state [tag] with its title and subtitle, and its CTA — or, for null, none. */
    private fun ComposeUiTest.assertEmptyState(tag: String, title: String, subtitle: String, cta: String?) {
        onNode(within(tag, title), useUnmergedTree = true).assertIsDisplayed()
        onNode(within(tag, subtitle), useUnmergedTree = true).assertIsDisplayed()
        if (cta == null) {
            onAllNodes(hasClickAction() and hasAnyAncestor(hasTestTag(tag)), useUnmergedTree = true)
                .assertCountEquals(0)
        } else {
            onNode(within(tag, cta), useUnmergedTree = true).assertIsDisplayed()
        }
    }

    /** [text] inside the production [tag]. */
    private fun within(tag: String, text: String): SemanticsMatcher =
        hasText(text) and hasAnyAncestor(hasTestTag(tag))

    /** The node's click action carries [label] — the header's `onClickLabel`. */
    private fun clickLabel(label: String): SemanticsMatcher =
        SemanticsMatcher("click action labelled '$label'") { node ->
            node.config.getOrNull(SemanticsActions.OnClick)?.label == label
        }

    /** The catalog in file order, each read with no format argument (plurals: the template). */
    private fun storedCatalog(): List<String> = runBlocking {
        listOf(
            resolver.getString(Res.string.feature_exercise_chart_picker_title),
            resolver.getString(Res.string.feature_exercise_chart_picker_open),
            resolver.getString(Res.string.feature_exercise_chart_picker_search),
            resolver.getString(Res.string.feature_exercise_chart_picker_no_matches),
            resolver.getString(Res.string.feature_exercise_chart_metric_heaviest),
            resolver.getString(Res.string.feature_exercise_chart_metric_session),
            resolver.getString(Res.string.feature_exercise_chart_metric_volume),
            resolver.getString(Res.string.feature_exercise_chart_metric_name_heaviest),
            resolver.getString(Res.string.feature_exercise_chart_metric_name_session),
            resolver.getString(Res.string.feature_exercise_chart_metric_name_volume),
            resolver.getString(Res.string.feature_exercise_chart_unit_kg),
            resolver.getString(Res.string.feature_exercise_chart_unit_reps),
            resolver.getString(Res.string.feature_exercise_chart_readout_record),
            resolver.getPluralString(Res.plurals.feature_exercise_chart_readout_sets, 1),
            resolver.getPluralString(Res.plurals.feature_exercise_chart_readout_sets, 2),
            resolver.getString(Res.string.feature_exercise_chart_preset_1m),
            resolver.getString(Res.string.feature_exercise_chart_preset_3m),
            resolver.getString(Res.string.feature_exercise_chart_preset_1y),
            resolver.getString(Res.string.feature_exercise_chart_preset_all),
            resolver.getString(Res.string.feature_exercise_chart_footer_min),
            resolver.getString(Res.string.feature_exercise_chart_footer_max),
            resolver.getString(Res.string.feature_exercise_chart_footer_last),
            resolver.getPluralString(Res.plurals.feature_exercise_chart_value_reps, 1),
            resolver.getPluralString(Res.plurals.feature_exercise_chart_value_reps, 2),
            resolver.getString(Res.string.feature_exercise_chart_empty_no_sessions_title),
            resolver.getString(Res.string.feature_exercise_chart_empty_no_sessions_subtitle),
            resolver.getString(Res.string.feature_exercise_chart_empty_no_sessions_cta),
            resolver.getString(Res.string.feature_exercise_chart_empty_not_found_title),
            resolver.getString(Res.string.feature_exercise_chart_empty_not_found_subtitle),
            resolver.getString(Res.string.feature_exercise_chart_empty_not_found_cta),
            resolver.getString(Res.string.feature_exercise_chart_error_load_title),
            resolver.getString(Res.string.feature_exercise_chart_error_load_subtitle),
            resolver.getString(Res.string.feature_exercise_chart_error_load_cta),
            resolver.getString(Res.string.feature_exercise_chart_empty_no_data_title),
            resolver.getString(Res.string.feature_exercise_chart_empty_no_data_subtitle),
        )
    }

    /** Each plural filled with its count as the one format argument, at 1 and at 2. */
    private fun filledPlurals(): List<String> = runBlocking {
        listOf(
            resolver.getPluralString(Res.plurals.feature_exercise_chart_readout_sets, 1, 1),
            resolver.getPluralString(Res.plurals.feature_exercise_chart_readout_sets, 2, 2),
            resolver.getPluralString(Res.plurals.feature_exercise_chart_value_reps, 1, 1),
            resolver.getPluralString(Res.plurals.feature_exercise_chart_value_reps, 2, 2),
        )
    }

    private fun footer(min: Double, max: Double, last: Double) = ChartFooterStatsDomain(
        min = domainPoint(min),
        max = domainPoint(max),
        last = domainPoint(last),
    )

    private fun domainPoint(value: Double) = ChartPointDomain(
        day = MAY_2,
        dayMillis = millis(MAY_2),
        value = value,
        sessionUuid = "f-$value",
        weight = null,
        reps = 0,
        setCount = 1,
    )

    private fun ComposeUiTest.settleScene() {
        mainClock.autoAdvance = false
        mainClock.advanceTimeBy(1_000)
        mainClock.autoAdvance = true
        waitForIdle()
    }

    /**
     * The canvas animates its points in and the sheet slides up, so one fixed advance is not
     * enough per branch. Settles in bounded steps until a node matching the branch exists.
     */
    private fun ComposeUiTest.settleUntil(matcher: SemanticsMatcher) {
        repeat(MAX_SETTLE_STEPS) {
            settleScene()
            if (onAllNodes(matcher, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()) {
                return
            }
        }
    }

    private fun ComposeUiTest.settleUntilTag(tag: String) = settleUntil(hasTestTag(tag))

    private companion object {

        /** Ten virtual seconds: far beyond every entrance animation of the screen and the sheet. */
        const val MAX_SETTLE_STEPS = 10

        const val NO_SESSIONS = "ExerciseChartNoFinishedSessions"
        const val NOT_FOUND = "ExerciseChartExerciseNotFound"
        const val NO_DATA = "ExerciseChartNoDataForExercise"
        const val LOAD_FAILED = "ExerciseChartLoadFailed"
        const val REPS = 5

        val WEIGHTED = ExerciseTypeUiModel.WEIGHTED
        val WEIGHTLESS = ExerciseTypeUiModel.WEIGHTLESS
        val BENCH = ExercisePickerItemUiModel("ex-1", "Bench press", WEIGHTED)
        val PULL_UP = ExercisePickerItemUiModel("ex-2", "Pull-up", WEIGHTLESS)

        val MAY_2 = LocalDate(2026, 5, 2)
        val JUN_1 = LocalDate(2026, 6, 1)
        val JUL_11 = LocalDate(2026, 7, 11)
        val JUL_23 = LocalDate(2026, 7, 23)

        fun millis(day: LocalDate): Long = day.atStartOfDayIn(TimeZone.UTC).toEpochMilliseconds()

        fun point(day: LocalDate, session: String, value: Double, setCount: Int) =
            ChartPointUiModel(day, millis(day), session, value, setCount, REPS)

        /** P (weighted): the readout, the plot and the footer's source. */
        val P = listOf(
            point(MAY_2, "s0", 49.0, 4),
            point(JUN_1, "s1", 63.0, 1),
            point(JUL_11, "s2", 77.0, 4),
            point(JUL_23, "s3", 70.0, 4),
        )

        /** G (grouping): a session volume past a thousand. */
        val G = listOf(point(JUL_11, "g0", 2940.0, 4), point(JUL_23, "g1", 4620.4, 4))

        /** W (weightless): reps as the value. */
        val W = listOf(point(MAY_2, "w0", 8.0, 3), point(JUN_1, "w1", 10.0, 3))

        /** SPEC 3.4's EN catalog in file order, as stored; each plural as its one, then its other. */
        val EXPECTED_STORED = listOf(
            "Select exercise",
            "Switch exercise",
            "Search",
            "Nothing found",
            "Weight",
            "Session",
            "Set",
            "Heaviest weight",
            "Volume per session",
            "Volume per set",
            "kg",
            "reps",
            "record",
            "%1\$d set",
            "%1\$d sets",
            "1M",
            "3M",
            "1Y",
            "All",
            "Minimum",
            "Maximum",
            "Last",
            "%1\$d rep",
            "%1\$d reps",
            "No finished workouts yet",
            "Finish a session — your progress will appear here.",
            "Start a workout",
            "Exercise unavailable",
            "It may be archived or have no logged sets. Pick another from the list.",
            "Choose exercise",
            "Couldn’t load the chart",
            "Something went wrong while reading your history. Try again.",
            "Try again",
            "Nothing to show yet",
            "The chart appears after two recorded sessions with this exercise.",
        )
    }
}
