// SPDX-License-Identifier: GPL-3.0-only
@file:OptIn(ExperimentalTestApi::class)

package io.github.stslex.workeeper.feature.home

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.paging.LoadState
import androidx.paging.LoadStates
import androidx.paging.PagingData
import io.github.stslex.workeeper.core.core.resources.ResourceWrapper
import io.github.stslex.workeeper.core.core.utils.DateTimeUtil
import io.github.stslex.workeeper.core.ui.kit.components.PagingUiState
import io.github.stslex.workeeper.core.ui.kit.strings.ComposeStringResolver
import io.github.stslex.workeeper.core.ui.kit.theme.AppTheme
import io.github.stslex.workeeper.core.ui.start_mode.model.StartCardModeUi
import io.github.stslex.workeeper.feature.home.domain.model.ActiveSessionWithStatsDomain
import io.github.stslex.workeeper.feature.home.domain.model.RecentSessionDomain
import io.github.stslex.workeeper.feature.home.domain.model.StartCardReadoutDomain
import io.github.stslex.workeeper.feature.home.domain.model.TrainingListItemDomain
import io.github.stslex.workeeper.feature.home.domain.model.WeekReadoutDomain
import io.github.stslex.workeeper.feature.home.mvi.mapper.HomeUiMapper.toPickerItems
import io.github.stslex.workeeper.feature.home.mvi.mapper.HomeUiMapper.toRecentItem
import io.github.stslex.workeeper.feature.home.mvi.mapper.HomeUiMapper.toUi
import io.github.stslex.workeeper.feature.home.mvi.model.PickerTrainingItem
import io.github.stslex.workeeper.feature.home.mvi.model.RecentSessionItem
import io.github.stslex.workeeper.feature.home.mvi.model.StartCardBodyUi
import io.github.stslex.workeeper.feature.home.mvi.model.TagIdleRowUi
import io.github.stslex.workeeper.feature.home.mvi.model.WeekDayUi
import io.github.stslex.workeeper.feature.home.mvi.store.BottomSheetState
import io.github.stslex.workeeper.feature.home.mvi.store.HomeStore.Action
import io.github.stslex.workeeper.feature.home.mvi.store.HomeStore.State
import io.github.stslex.workeeper.feature.home.resources.Res
import io.github.stslex.workeeper.feature.home.resources.feature_home_active_session_label
import io.github.stslex.workeeper.feature.home.resources.feature_home_active_session_progress_format
import io.github.stslex.workeeper.feature.home.resources.feature_home_charts
import io.github.stslex.workeeper.feature.home.resources.feature_home_conflict_progress_format
import io.github.stslex.workeeper.feature.home.resources.feature_home_conflict_unnamed
import io.github.stslex.workeeper.feature.home.resources.feature_home_empty_headline
import io.github.stslex.workeeper.feature.home.resources.feature_home_empty_supporting
import io.github.stslex.workeeper.feature.home.resources.feature_home_paging_error
import io.github.stslex.workeeper.feature.home.resources.feature_home_paging_loading
import io.github.stslex.workeeper.feature.home.resources.feature_home_paging_retry
import io.github.stslex.workeeper.feature.home.resources.feature_home_picker_empty
import io.github.stslex.workeeper.feature.home.resources.feature_home_picker_see_all
import io.github.stslex.workeeper.feature.home.resources.feature_home_picker_start_blank_subtitle
import io.github.stslex.workeeper.feature.home.resources.feature_home_picker_start_blank_title
import io.github.stslex.workeeper.feature.home.resources.feature_home_picker_title
import io.github.stslex.workeeper.feature.home.resources.feature_home_recent_adhoc_label
import io.github.stslex.workeeper.feature.home.resources.feature_home_recent_exercises_count
import io.github.stslex.workeeper.feature.home.resources.feature_home_recent_sets_count
import io.github.stslex.workeeper.feature.home.resources.feature_home_recent_stats_format
import io.github.stslex.workeeper.feature.home.resources.feature_home_refresh_error
import io.github.stslex.workeeper.feature.home.resources.feature_home_settings
import io.github.stslex.workeeper.feature.home.resources.feature_home_start_action
import io.github.stslex.workeeper.feature.home.resources.feature_home_start_days_count
import io.github.stslex.workeeper.feature.home.resources.feature_home_start_days_idle_count
import io.github.stslex.workeeper.feature.home.resources.feature_home_start_empty_sessions
import io.github.stslex.workeeper.feature.home.resources.feature_home_start_empty_tags
import io.github.stslex.workeeper.feature.home.resources.feature_home_start_groups_footnote
import io.github.stslex.workeeper.feature.home.resources.feature_home_start_mode_forgotten_label
import io.github.stslex.workeeper.feature.home.resources.feature_home_start_mode_switch
import io.github.stslex.workeeper.feature.home.resources.feature_home_start_never_run
import io.github.stslex.workeeper.feature.home.resources.feature_home_start_other_training
import io.github.stslex.workeeper.feature.home.resources.feature_home_start_week_day_fri
import io.github.stslex.workeeper.feature.home.resources.feature_home_start_week_day_mon
import io.github.stslex.workeeper.feature.home.resources.feature_home_start_week_day_sat
import io.github.stslex.workeeper.feature.home.resources.feature_home_start_week_day_sun
import io.github.stslex.workeeper.feature.home.resources.feature_home_start_week_day_thu
import io.github.stslex.workeeper.feature.home.resources.feature_home_start_week_day_tue
import io.github.stslex.workeeper.feature.home.resources.feature_home_start_week_day_wed
import io.github.stslex.workeeper.feature.home.resources.feature_home_start_week_sessions_count
import io.github.stslex.workeeper.feature.home.resources.feature_home_title
import io.github.stslex.workeeper.feature.home.ui.HomeScreen
import io.github.stslex.workeeper.feature.home.ui.components.TrainingPickerSheet
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals

class HomeFeatureSceneIosTest {

    /** Every read outside composition goes through this one real resolver: no fake here. */
    private val resolver = ComposeStringResolver()

    /**
     * `ResourceWrapper` has no iOS implementation (S1), so the relative time is stubbed for exactly
     * the three timestamps the scene renders. It is the scene's only test double.
     */
    private val relativeTime = object : ResourceWrapper {
        override fun getAbbreviatedRelativeTime(timestamp: Long, now: Long): String =
            when (timestamp to now) {
                300_000L to NOW -> "15 min. ago"
                1_020_000L to NOW -> "3 min. ago"
                900_000L to NOW -> "5 min. ago"
                else -> error("no relative time is stubbed for ($timestamp, $now)")
            }

        override fun getString(id: Int, vararg args: Any): String =
            error("home reads no copy through ResourceWrapper; id $id")

        override fun getQuantityString(id: Int, quantity: Int, vararg args: Any): String =
            error("home reads no plural through ResourceWrapper; id $id")

        override fun formatMediumDate(timestamp: Long): String = error("home formats no medium date")

        override fun formatDayMonth(timestamp: Long): String = error("home formats no day-month date")
    }

    @Test
    fun resourcesReadoutsListPickerAndActionsRenderAndDispatch() = runComposeUiTest {
        // 1. The private catalog in file order through the real resolver: as stored, then every
        // placeholder-bearing entry filled.
        assertEquals(EXPECTED_STORED, storedCatalog())
        assertEquals(EXPECTED_FILLED, filledCatalog())

        // 2. Copy through the production mapper: the real resolver, and the stub for the rows.
        val anchorDate = DateTimeUtil.formatMillis(ANCHOR)
        val r1 = runBlocking { week(3, setOf(0, 2, 4)).toUi(resolver) }
        val r3 = runBlocking { daysSince(4, "Leg Day", lastIsAdhoc = false).toUi(resolver) }
        val r5 = runBlocking { tagIdle().toUi(resolver) }
        val r6 = runBlocking { forgotten("tpl-1", "Push Day", 21, 6).toUi(resolver) }
        val r10 = runBlocking { StartCardReadoutDomain.NoTemplates.toUi(resolver) }
        assertEquals(
            StartCardBodyUi.Week("3", "workouts", weekDays(filled = setOf(0, 2, 4))),
            r1,
        )
        assertEquals(
            StartCardBodyUi.Week("1", "workout", weekDays(filled = setOf(6))),
            runBlocking { week(1, setOf(6)).toUi(resolver) },
        )
        assertEquals(StartCardBodyUi.DaysSince("4", "days", "Leg Day · $anchorDate"), r3)
        assertEquals(
            StartCardBodyUi.DaysSince("1", "day", "Ad-hoc workout · $anchorDate"),
            runBlocking { daysSince(1, "Unused", lastIsAdhoc = true).toUi(resolver) },
        )
        assertEquals(
            StartCardBodyUi.TagIdle(
                rows = persistentListOf(
                    TagIdleRowUi(name = "Legs", barFraction = 1f, daysCountLabel = "10"),
                    TagIdleRowUi(name = "Back", barFraction = 0.5f, daysCountLabel = "5"),
                    TagIdleRowUi(name = "Chest", barFraction = 0f, daysCountLabel = "0"),
                ),
                footnoteLabel = "days since the group’s last workout",
            ),
            r5,
        )
        assertEquals(StartCardBodyUi.Forgotten("tpl-1", "Push Day", "21 days · 6 exercises"), r6)
        assertEquals(
            StartCardBodyUi.Forgotten("tpl-2", "Pull Day", "never yet · 1 exercise"),
            runBlocking { forgotten("tpl-2", "Pull Day", null, 1).toUi(resolver) },
        )
        assertEquals(
            listOf(
                StartCardBodyUi.Empty("No workouts yet"),
                StartCardBodyUi.Empty(
                    "Exercises have no tags yet — tag muscle groups and they will show up here",
                ),
                StartCardBodyUi.Empty("No templates yet"),
            ),
            runBlocking {
                listOf(
                    StartCardReadoutDomain.NoSessions.toUi(resolver),
                    StartCardReadoutDomain.NoTaggedHistory.toUi(resolver),
                    r10,
                )
            },
        )
        val rows = runBlocking {
            listOf(
                recent("s1", "Unused", isAdhoc = true, 0L, 300_000L, exercises = 1, sets = 2),
                recent("s2", "Push Day", isAdhoc = false, 120_000L, 1_020_000L, exercises = 3, sets = 1),
            ).map { it.toRecentItem(NOW, relativeTime, resolver) }
        }
        assertEquals(
            listOf(
                RecentSessionItem("s1", "Ad-hoc workout", true, "15 min. ago", "05:00", "1 exercise · 2 sets"),
                RecentSessionItem("s2", "Push Day", false, "3 min. ago", "15:00", "3 exercises · 1 set"),
            ),
            rows,
        )
        val picker = runBlocking {
            listOf(
                TrainingListItemDomain("tpl-1", "Push Day", exerciseCount = 6, lastSessionAt = 900_000L),
                TrainingListItemDomain("tpl-2", "Pull Day", exerciseCount = 1, lastSessionAt = null),
            ).toPickerItems(NOW, relativeTime, resolver)
        }
        assertEquals(
            listOf(
                PickerTrainingItem("tpl-1", "Push Day", "6 exercises", "5 min. ago"),
                PickerTrainingItem("tpl-2", "Pull Day", "1 exercise", null),
            ),
            picker,
        )
        val activeSessions = runBlocking {
            listOf(active(isAdhoc = true), active(isAdhoc = false)).map { it.toUi(ACTIVE_NOW, resolver) }
        }
        assertEquals(listOf("Ad-hoc workout", "Push Day"), activeSessions.map { it.trainingName })
        assertEquals(listOf("12:34", "12:34"), activeSessions.map { it.elapsedDurationLabel })

        // 3. The screen, from State.init with S1 and S2 as the paged rows, and 4. the picker.
        val listed = State.init(
            pagingUiState = paging(PagingData.from(rows, sourceLoadStates = settled())),
        )
        val state = mutableStateOf(listed)
        val pickerState = mutableStateOf<BottomSheetState.TrainingPicker?>(null)
        val actions = mutableListOf<Action>()
        val pickerCalls = mutableListOf<String>()
        setContent {
            AppTheme {
                val sheet = pickerState.value
                if (sheet == null) {
                    HomeScreen(state = state.value, consume = actions::add)
                } else {
                    TrainingPickerSheet(
                        state = sheet,
                        onSelect = { uuid -> pickerCalls.add("select:$uuid") },
                        onStartBlank = { pickerCalls.add("startBlank") },
                        onSeeAll = { pickerCalls.add("seeAll") },
                        onDismiss = {},
                    )
                }
            }
        }

        // Loading: only the active-session half gates the body; the top bar draws regardless.
        settleUntilTag("HomeScreen")
        onNode(hasText("Home"), useUnmergedTree = true).assertIsDisplayed()
        onNodeWithTag("HomeChartsButton").assertContentDescriptionEquals("Charts")
        onNodeWithTag("HomeSettingsButton").assertContentDescriptionEquals("Settings")
        onNodeWithTag("HomeStartCard").assertDoesNotExist()
        onNodeWithTag("ActiveSessionBanner").assertDoesNotExist()
        onNodeWithTag("HomeList").assertDoesNotExist()

        // The banner over the two rows; no start card while a session runs.
        state.value = listed.copy(
            activeSession = activeSessions[1],
            isActiveLoaded = true,
            nowMillis = ACTIVE_NOW,
        )
        settleUntilTag("HomeRecentRow_s2")
        onNodeWithTag("ActiveSessionBanner").assertIsDisplayed()
        onNode(within("ActiveSessionBanner", "Push Day"), useUnmergedTree = true).assertIsDisplayed()
        onNodeWithText("•12:34", useUnmergedTree = true).assertIsDisplayed()
        onNodeWithText("Session in progress · 2 of 5 done", useUnmergedTree = true).assertIsDisplayed()
        onNodeWithTag("HomeStartCard").assertDoesNotExist()
        EXPECTED_ROWS.forEach { (uuid, name, meta) ->
            onNodeWithTag("HomeRecentName_$uuid", useUnmergedTree = true).assertTextEquals(name)
            onNodeWithTag("HomeRecentMeta_$uuid", useUnmergedTree = true).assertTextEquals(meta)
        }
        onNodeWithTag("HomeChartsButton").performClick()
        onNodeWithTag("HomeSettingsButton").performClick()
        onNodeWithTag("ActiveSessionBanner").performClick()
        onNodeWithTag("HomeRecentRow_s2").performClick()

        // The start card, mode by mode, each with its own head and readout.
        val idle = listed.copy(isActiveLoaded = true, nowMillis = NOW)
        state.value = idle.copy(startCardMode = StartCardModeUi.WEEK, startCardBody = r1)
        settleUntil(hasText("WEEK"))
        assertHead("WEEK")
        onNodeWithTag("HomeStartButton").assertIsDisplayed()
        onNodeWithText("Start", useUnmergedTree = true).assertIsDisplayed()
        onNodeWithText("3", useUnmergedTree = true).assertIsDisplayed()
        onNodeWithText("workouts", useUnmergedTree = true).assertIsDisplayed()
        listOf("Mo", "Tu", "We", "Th", "Fr", "Sa", "Su").forEach { day ->
            onNodeWithText(day, useUnmergedTree = true).assertIsDisplayed()
        }
        onNodeWithTag("HomeStartModeHead", useUnmergedTree = true).performClick()
        onNodeWithTag("HomeStartButton").performClick()

        state.value = idle.copy(startCardMode = StartCardModeUi.DAYS_SINCE_LAST, startCardBody = r3)
        settleUntil(hasText("DAYS WITHOUT TRAINING"))
        assertHead("DAYS WITHOUT TRAINING")
        onNodeWithText("Start", useUnmergedTree = true).assertIsDisplayed()
        onNodeWithText("4", useUnmergedTree = true).assertIsDisplayed()
        onNodeWithText("days", useUnmergedTree = true).assertIsDisplayed()
        onNodeWithText("Leg Day · $anchorDate", useUnmergedTree = true).assertIsDisplayed()

        state.value = idle.copy(startCardMode = StartCardModeUi.LAGGING_GROUPS, startCardBody = r5)
        settleUntil(hasText("LAGGING GROUPS"))
        assertHead("LAGGING GROUPS")
        onNodeWithText("Start", useUnmergedTree = true).assertIsDisplayed()
        listOf("Legs" to "10", "Back" to "5", "Chest" to "0").forEach { (name, days) ->
            onNodeWithText(name, useUnmergedTree = true).assertIsDisplayed()
            onNodeWithText(days, useUnmergedTree = true).assertIsDisplayed()
        }
        onNodeWithText("days since the group’s last workout", useUnmergedTree = true).assertIsDisplayed()

        state.value = idle.copy(startCardMode = StartCardModeUi.FORGOTTEN_TRAINING, startCardBody = r6)
        settleUntil(hasText("LONGEST NOT DONE"))
        assertHead("LONGEST NOT DONE")
        onNodeWithText("Start", useUnmergedTree = true).assertIsDisplayed()
        onNode(within("HomeStartCard", "Push Day"), useUnmergedTree = true).assertIsDisplayed()
        onNodeWithText("21 days · 6 exercises", useUnmergedTree = true).assertIsDisplayed()
        onNodeWithText("ANOTHER TRAINING", useUnmergedTree = true).assertIsDisplayed()
        onNodeWithTag("HomeStartOtherTraining").performClick()

        state.value = idle.copy(startCardMode = StartCardModeUi.FORGOTTEN_TRAINING, startCardBody = r10)
        settleUntil(hasText("No templates yet"))
        assertHead("LONGEST NOT DONE")
        onNodeWithText("Start", useUnmergedTree = true).assertIsDisplayed()
        onNodeWithText("No templates yet", useUnmergedTree = true).assertIsDisplayed()
        onNodeWithText("ANOTHER TRAINING", useUnmergedTree = true).assertDoesNotExist()

        // The empty region with no rows: settled, then a failed first page.
        state.value = idle.copy(
            startCardMode = StartCardModeUi.WEEK,
            startCardBody = r1,
            pagingUiState = paging(PagingData.empty(sourceLoadStates = settled())),
        )
        settleUntilTag("HomeEmptyState")
        onNodeWithTag("HomeEmptyState", useUnmergedTree = true).assertIsDisplayed()
        onNodeWithText("Ready when you are", useUnmergedTree = true).assertIsDisplayed()
        onNodeWithText("Start a training to log your first session", useUnmergedTree = true)
            .assertIsDisplayed()

        state.value = idle.copy(
            startCardMode = StartCardModeUi.WEEK,
            startCardBody = r1,
            pagingUiState = paging(
                PagingData.empty(
                    sourceLoadStates = settled(
                        refresh = LoadState.Error(IllegalStateException("native refresh")),
                    ),
                ),
            ),
        )
        settleUntilTag("HomeColdOpenError")
        onNodeWithTag("HomeColdOpenError", useUnmergedTree = true).assertIsDisplayed()
        onNodeWithText("Couldn’t load the list", useUnmergedTree = true).assertIsDisplayed()
        onNodeWithText("Retry", useUnmergedTree = true).assertIsDisplayed()

        // 4. The training picker over the mapper's items, then with no templates.
        pickerState.value = BottomSheetState.TrainingPicker(
            templates = picker.toImmutableList(),
            isLoading = false,
        )
        settleUntilTag("HomePickerStartBlankRow")
        onNodeWithText("Choose a training", useUnmergedTree = true).assertIsDisplayed()
        onNodeWithText("Start blank workout", useUnmergedTree = true).assertIsDisplayed()
        onNodeWithText("Add exercises as you go", useUnmergedTree = true).assertIsDisplayed()
        onNodeWithText("Push Day", useUnmergedTree = true).assertIsDisplayed()
        onNodeWithText("6 exercises · 5 min. ago", useUnmergedTree = true).assertIsDisplayed()
        onNodeWithText("Pull Day", useUnmergedTree = true).assertIsDisplayed()
        onNodeWithText("1 exercise", useUnmergedTree = true).assertIsDisplayed()
        onNodeWithText("See all templates", useUnmergedTree = true).assertIsDisplayed()
        onNodeWithText("Push Day").performClick()
        onNodeWithTag("HomePickerStartBlankRow").performClick()
        onNodeWithText("See all templates").performClick()

        pickerState.value = BottomSheetState.TrainingPicker(templates = persistentListOf(), isLoading = false)
        settleUntil(hasText("No templates yet"))
        onNodeWithText("No templates yet", useUnmergedTree = true).assertIsDisplayed()

        // 5. Dispatch: the screen's actions and the picker's callbacks, each as one exact list.
        assertEquals(
            listOf<Action>(
                Action.Click.OnChartsClick,
                Action.Click.OnSettingsClick,
                Action.Click.OnActiveSessionClick,
                Action.Click.OnRecentSessionClick("s2"),
                Action.Click.OnModeLabelClick,
                Action.Click.OnStartActionClick,
                Action.Click.OnStartTrainingClick,
            ),
            actions,
        )
        assertEquals(listOf("select:tpl-1", "startBlank", "seeAll"), pickerCalls)
    }

    /** [text] inside the production [tag]: the recent row S2 carries the same name. */
    private fun within(tag: String, text: String): SemanticsMatcher =
        hasText(text) and hasAnyAncestor(hasTestTag(tag))

    private fun ComposeUiTest.assertHead(label: String) {
        onNodeWithTag("HomeStartModeLabel", useUnmergedTree = true).assertTextEquals(label)
    }

    private fun week(sessions: Int, trained: Set<Int>) =
        StartCardReadoutDomain.Week(WeekReadoutDomain(sessionsThisWeek = sessions, trainedDayIndexes = trained))

    private fun daysSince(days: Int, name: String, lastIsAdhoc: Boolean) = StartCardReadoutDomain.DaysSince(
        daysSince = days,
        lastTrainingName = name,
        lastIsAdhoc = lastIsAdhoc,
        lastFinishedAt = ANCHOR,
    )

    private fun tagIdle() = StartCardReadoutDomain.TagIdle(
        entries = listOf(
            StartCardReadoutDomain.TagIdle.Entry(name = "Legs", daysIdle = 10),
            StartCardReadoutDomain.TagIdle.Entry(name = "Back", daysIdle = 5),
            StartCardReadoutDomain.TagIdle.Entry(name = "Chest", daysIdle = 0),
        ),
    )

    private fun forgotten(uuid: String, name: String, daysIdle: Int?, exercises: Int) =
        StartCardReadoutDomain.Forgotten(
            trainingUuid = uuid,
            trainingName = name,
            daysIdle = daysIdle,
            exerciseCount = exercises,
        )

    private fun recent(
        uuid: String,
        name: String,
        isAdhoc: Boolean,
        startedAt: Long,
        finishedAt: Long,
        exercises: Int,
        sets: Int,
    ) = RecentSessionDomain(
        sessionUuid = uuid,
        trainingUuid = "training-$uuid",
        trainingName = name,
        isAdhoc = isAdhoc,
        startedAt = startedAt,
        finishedAt = finishedAt,
        exerciseCount = exercises,
        setCount = sets,
    )

    private fun active(isAdhoc: Boolean) = ActiveSessionWithStatsDomain(
        sessionUuid = "active-session",
        trainingUuid = "active-training",
        trainingName = "Push Day",
        isAdhoc = isAdhoc,
        startedAt = 0L,
        totalCount = 5,
        doneCount = 2,
    )

    private fun weekDays(filled: Set<Int>) = listOf("Mo", "Tu", "We", "Th", "Fr", "Sa", "Su")
        .mapIndexed { index, label -> WeekDayUi(label = label, isFilled = index in filled) }
        .toImmutableList()

    private fun <T : Any> paging(data: PagingData<T>): PagingUiState<PagingData<T>> =
        PagingUiState { flowOf(data) }

    private fun settled(
        refresh: LoadState = LoadState.NotLoading(endOfPaginationReached = false),
    ): LoadStates = LoadStates(
        refresh = refresh,
        prepend = LoadState.NotLoading(endOfPaginationReached = true),
        append = LoadState.NotLoading(endOfPaginationReached = true),
    )

    /** The catalog in file order, each read with no format arguments (the template verbatim). */
    private fun storedCatalog(): List<String> = runBlocking {
        listOf(
            resolver.getString(Res.string.feature_home_title),
            resolver.getString(Res.string.feature_home_active_session_label),
            resolver.getString(Res.string.feature_home_active_session_progress_format),
            resolver.getString(Res.string.feature_home_empty_headline),
            resolver.getString(Res.string.feature_home_empty_supporting),
            resolver.getString(Res.string.feature_home_settings),
            resolver.getString(Res.string.feature_home_charts),
            resolver.getString(Res.string.feature_home_start_action),
            resolver.getString(Res.string.feature_home_start_mode_switch),
            resolver.getString(Res.string.feature_home_start_mode_forgotten_label),
            resolver.getString(Res.string.feature_home_start_empty_sessions),
            resolver.getString(Res.string.feature_home_start_empty_tags),
            resolver.getString(Res.string.feature_home_start_never_run),
            resolver.getString(Res.string.feature_home_start_groups_footnote),
            resolver.getString(Res.string.feature_home_start_other_training),
            resolver.getString(Res.string.feature_home_start_week_day_mon),
            resolver.getString(Res.string.feature_home_start_week_day_tue),
            resolver.getString(Res.string.feature_home_start_week_day_wed),
            resolver.getString(Res.string.feature_home_start_week_day_thu),
            resolver.getString(Res.string.feature_home_start_week_day_fri),
            resolver.getString(Res.string.feature_home_start_week_day_sat),
            resolver.getString(Res.string.feature_home_start_week_day_sun),
            resolver.getString(Res.string.feature_home_recent_adhoc_label),
            resolver.getString(Res.string.feature_home_recent_stats_format),
            resolver.getString(Res.string.feature_home_picker_title),
            resolver.getString(Res.string.feature_home_picker_see_all),
            resolver.getString(Res.string.feature_home_picker_empty),
            resolver.getString(Res.string.feature_home_picker_start_blank_title),
            resolver.getString(Res.string.feature_home_picker_start_blank_subtitle),
            resolver.getString(Res.string.feature_home_conflict_unnamed),
            resolver.getString(Res.string.feature_home_conflict_progress_format),
            resolver.getString(Res.string.feature_home_paging_loading),
            resolver.getString(Res.string.feature_home_paging_error),
            resolver.getString(Res.string.feature_home_paging_retry),
            resolver.getString(Res.string.feature_home_refresh_error),
            resolver.getPluralString(Res.plurals.feature_home_recent_exercises_count, 1),
            resolver.getPluralString(Res.plurals.feature_home_recent_exercises_count, 2),
            resolver.getPluralString(Res.plurals.feature_home_recent_sets_count, 1),
            resolver.getPluralString(Res.plurals.feature_home_recent_sets_count, 2),
            resolver.getPluralString(Res.plurals.feature_home_start_week_sessions_count, 1),
            resolver.getPluralString(Res.plurals.feature_home_start_week_sessions_count, 2),
            resolver.getPluralString(Res.plurals.feature_home_start_days_count, 1),
            resolver.getPluralString(Res.plurals.feature_home_start_days_count, 2),
            resolver.getPluralString(Res.plurals.feature_home_start_days_idle_count, 1),
            resolver.getPluralString(Res.plurals.feature_home_start_days_idle_count, 2),
        )
    }

    /** Every placeholder-bearing entry, filled: the SPEC's arguments, and the count for plurals. */
    private fun filledCatalog(): List<String> = runBlocking {
        listOf(
            resolver.getString(Res.string.feature_home_active_session_progress_format, 2, 5),
            resolver.getString(Res.string.feature_home_recent_stats_format, "a", "b"),
            resolver.getString(Res.string.feature_home_conflict_progress_format, 1, 2),
            resolver.getPluralString(Res.plurals.feature_home_recent_exercises_count, 1, 1),
            resolver.getPluralString(Res.plurals.feature_home_recent_exercises_count, 2, 2),
            resolver.getPluralString(Res.plurals.feature_home_recent_sets_count, 1, 1),
            resolver.getPluralString(Res.plurals.feature_home_recent_sets_count, 2, 2),
            resolver.getPluralString(Res.plurals.feature_home_start_days_idle_count, 1, 1),
            resolver.getPluralString(Res.plurals.feature_home_start_days_idle_count, 2, 2),
        )
    }

    private fun ComposeUiTest.settleScene() {
        mainClock.autoAdvance = false
        mainClock.advanceTimeBy(1_000)
        mainClock.autoAdvance = true
        waitForIdle()
    }

    /**
     * Paging hands a new list over on the main dispatcher, and the kit then holds a shown loading
     * treatment for its minimum before the next verdict draws, so one fixed advance is not enough
     * per branch. Settles in bounded steps until a node matching the branch exists.
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

        /** Fixed so the scene never depends on the clock; rows and the picker read it. */
        const val NOW = 1_200_000L

        /** The banner's now: 12:34 after a session that started at 0. */
        const val ACTIVE_NOW = 754_000L

        /** Any fixed timestamp; its date is computed in the test, since the zone decides it. */
        const val ANCHOR = 1_700_000_000_000L

        /** Ten virtual seconds: far beyond the kit's 140 ms deferral and 260 ms minimum hold. */
        const val MAX_SETTLE_STEPS = 10

        /** uuid, name and the drawn meta line: when · how long · how much. */
        val EXPECTED_ROWS = listOf(
            Triple("s1", "Ad-hoc workout", "15 min. ago · 05:00 · 1 exercise · 2 sets"),
            Triple("s2", "Push Day", "3 min. ago · 15:00 · 3 exercises · 1 set"),
        )

        /** SPEC 3.4's EN catalog in file order, as stored; each plural as its one, then its other. */
        val EXPECTED_STORED = listOf(
            "Home",
            "Session in progress",
            "%1\$d of %2\$d done",
            "Ready when you are",
            "Start a training to log your first session",
            "Settings",
            "Charts",
            "Start",
            "Choose what to show",
            "Longest not done",
            "No workouts yet",
            "Exercises have no tags yet — tag muscle groups and they will show up here",
            "never yet",
            "days since the group’s last workout",
            "Another training",
            "Mo",
            "Tu",
            "We",
            "Th",
            "Fr",
            "Sa",
            "Su",
            "Ad-hoc workout",
            "%1\$s · %2\$s",
            "Choose a training",
            "See all templates",
            "No templates yet",
            "Start blank workout",
            "Add exercises as you go",
            "Workout",
            "%1\$d of %2\$d exercises done",
            "Loading",
            "Couldn’t load more",
            "Retry",
            "Couldn’t load the list",
            "%1\$d exercise",
            "%1\$d exercises",
            "%1\$d set",
            "%1\$d sets",
            "workout",
            "workouts",
            "day",
            "days",
            "%1\$d day",
            "%1\$d days",
        )

        /** Every placeholder-bearing entry of [EXPECTED_STORED], filled by the real formatter. */
        val EXPECTED_FILLED = listOf(
            "2 of 5 done",
            "a · b",
            "1 of 2 exercises done",
            "1 exercise",
            "2 exercises",
            "1 set",
            "2 sets",
            "1 day",
            "2 days",
        )
    }
}
