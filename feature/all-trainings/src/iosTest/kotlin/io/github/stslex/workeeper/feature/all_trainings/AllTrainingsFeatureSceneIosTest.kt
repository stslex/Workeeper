// SPDX-License-Identifier: GPL-3.0-only
@file:OptIn(ExperimentalTestApi::class)

package io.github.stslex.workeeper.feature.all_trainings

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.paging.LoadState
import androidx.paging.LoadStates
import androidx.paging.PagingData
import io.github.stslex.workeeper.core.ui.kit.components.PagingUiState
import io.github.stslex.workeeper.core.ui.kit.resources.core_ui_kit_action_cancel
import io.github.stslex.workeeper.core.ui.kit.theme.AppTheme
import io.github.stslex.workeeper.feature.all_trainings.domain.model.TrainingListItemDomain
import io.github.stslex.workeeper.feature.all_trainings.mvi.mapper.TrainingListItemMapper.toUi
import io.github.stslex.workeeper.feature.all_trainings.mvi.model.TagUiModel
import io.github.stslex.workeeper.feature.all_trainings.mvi.store.AllTrainingsStore.Action
import io.github.stslex.workeeper.feature.all_trainings.mvi.store.AllTrainingsStore.State
import io.github.stslex.workeeper.feature.all_trainings.mvi.store.AllTrainingsStore.State.PendingBulkDelete
import io.github.stslex.workeeper.feature.all_trainings.mvi.store.AllTrainingsStore.State.SelectionMode
import io.github.stslex.workeeper.feature.all_trainings.resources.feature_all_trainings_bulk_archive
import io.github.stslex.workeeper.feature.all_trainings.resources.feature_all_trainings_bulk_archive_confirm_body
import io.github.stslex.workeeper.feature.all_trainings.resources.feature_all_trainings_bulk_archive_confirm_title
import io.github.stslex.workeeper.feature.all_trainings.resources.feature_all_trainings_bulk_archive_impact
import io.github.stslex.workeeper.feature.all_trainings.resources.feature_all_trainings_bulk_archive_partial_format
import io.github.stslex.workeeper.feature.all_trainings.resources.feature_all_trainings_bulk_archive_success
import io.github.stslex.workeeper.feature.all_trainings.resources.feature_all_trainings_empty_create
import io.github.stslex.workeeper.feature.all_trainings.resources.feature_all_trainings_empty_headline
import io.github.stslex.workeeper.feature.all_trainings.resources.feature_all_trainings_empty_start_blank
import io.github.stslex.workeeper.feature.all_trainings.resources.feature_all_trainings_empty_supporting
import io.github.stslex.workeeper.feature.all_trainings.resources.feature_all_trainings_exercise_count
import io.github.stslex.workeeper.feature.all_trainings.resources.feature_all_trainings_fab_create
import io.github.stslex.workeeper.feature.all_trainings.resources.feature_all_trainings_filtered_empty_clear
import io.github.stslex.workeeper.feature.all_trainings.resources.feature_all_trainings_filtered_empty_headline
import io.github.stslex.workeeper.feature.all_trainings.resources.feature_all_trainings_paging_error
import io.github.stslex.workeeper.feature.all_trainings.resources.feature_all_trainings_paging_loading
import io.github.stslex.workeeper.feature.all_trainings.resources.feature_all_trainings_paging_retry
import io.github.stslex.workeeper.feature.all_trainings.resources.feature_all_trainings_refresh_error
import io.github.stslex.workeeper.feature.all_trainings.resources.feature_all_trainings_relative_days_format
import io.github.stslex.workeeper.feature.all_trainings.resources.feature_all_trainings_relative_hours_format
import io.github.stslex.workeeper.feature.all_trainings.resources.feature_all_trainings_relative_just_now
import io.github.stslex.workeeper.feature.all_trainings.resources.feature_all_trainings_relative_minutes_format
import io.github.stslex.workeeper.feature.all_trainings.resources.feature_all_trainings_selected_count
import io.github.stslex.workeeper.feature.all_trainings.resources.feature_all_trainings_selection_close
import io.github.stslex.workeeper.feature.all_trainings.resources.feature_all_trainings_selection_empty_headline
import io.github.stslex.workeeper.feature.all_trainings.resources.feature_all_trainings_selection_empty_supporting
import io.github.stslex.workeeper.feature.all_trainings.resources.feature_all_trainings_status_in_progress_format
import io.github.stslex.workeeper.feature.all_trainings.resources.feature_all_trainings_status_last_format
import io.github.stslex.workeeper.feature.all_trainings.resources.feature_all_trainings_status_never
import io.github.stslex.workeeper.feature.all_trainings.resources.feature_all_trainings_title
import io.github.stslex.workeeper.feature.all_trainings.ui.AllTrainingsScreen
import io.github.stslex.workeeper.feature.all_trainings.ui.bulkArchiveMessage
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentSetOf
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.jetbrains.compose.resources.getPluralString
import org.jetbrains.compose.resources.getString
import kotlin.test.Test
import kotlin.test.assertEquals
import io.github.stslex.workeeper.core.ui.kit.resources.Res as KitRes
import io.github.stslex.workeeper.feature.all_trainings.resources.Res as AllTrainingsRes

class AllTrainingsFeatureSceneIosTest {

    @Test
    fun resourcesPagingBranchesSelectionAndActionsRenderAndDispatch() = runComposeUiTest {
        // The private catalog in file order: as stored, then with every placeholder filled.
        assertEquals(EXPECTED_CATALOG, featureCatalog())
        assertEquals(EXPECTED_FILLED, filledCatalog())

        // Mapper copy (D2) through the production toUi, at a fixed now.
        val domains = listOf(
            training(
                uuid = ACTIVE,
                name = "Push day",
                tags = listOf("Push", "Chest"),
                exerciseCount = 5,
                activeSessionStartedAt = NOW - 5 * MINUTE - 20_000L,
            ),
            training(
                uuid = RECENT,
                name = "Pull day",
                tags = listOf("Pull"),
                exerciseCount = 1,
                lastSessionAt = NOW - 30_000L,
            ),
            training(
                uuid = HOURS,
                name = "Legs",
                tags = emptyList(),
                exerciseCount = 4,
                lastSessionAt = NOW - 2 * HOUR - 15 * MINUTE,
            ),
            training(
                uuid = DAYS,
                name = "Full body",
                tags = listOf("Legs", "Push"),
                exerciseCount = 6,
                lastSessionAt = NOW - 3 * DAY - 5 * HOUR,
            ),
            training(
                uuid = NEVER,
                name = "Mobility",
                tags = emptyList(),
                exerciseCount = 2,
            ),
        )
        val rows = runBlocking { domains.map { it.toUi(nowMillis = NOW) } }
        assertEquals(
            listOf(
                "in progress · started 5m ago",
                "last: just now",
                "last: 2h",
                "last: 3d",
                "never trained",
            ),
            rows.map { it.statusLabel },
        )

        // Snackbar copy (D1) through bulkArchiveMessage: both quantities, then the blocked list.
        val messages = runBlocking {
            listOf(
                bulkArchiveMessage(archivedCount = 1, blockedNames = emptyList()),
                bulkArchiveMessage(archivedCount = 3, blockedNames = emptyList()),
                bulkArchiveMessage(archivedCount = 2, blockedNames = listOf("Legs", "Push")),
            )
        }
        assertEquals(
            listOf("1 training archived", "3 trainings archived", "Archived 2, blocked: Legs, Push"),
            messages,
        )

        val tags = persistentListOf(TagUiModel(TAG_PUSH, "Push"), TagUiModel(TAG_LEGS, "Legs"))
        val coldOpen = State(
            pagingUiState = paging(
                PagingData.empty(sourceLoadStates = settled(refresh = LoadState.Loading)),
            ),
            availableTags = persistentListOf(),
            activeTagFilter = persistentSetOf(),
            selectionMode = SelectionMode.Off,
            pendingBulkDelete = null,
            hasActiveSession = false,
        )
        val state = mutableStateOf(coldOpen)
        val actions = mutableListOf<Action>()

        setContent {
            AppTheme {
                AllTrainingsScreen(
                    state = state.value,
                    consume = actions::add,
                )
            }
        }

        // Cold open: the deferred loading verdict, held rather than the first frame of a new list.
        settleUntilTag("AllTrainingsColdOpen")
        repeat(STABLE_SETTLE_STEPS) { settleScene() }
        onNodeWithTag("AllTrainingsColdOpen", useUnmergedTree = true).assertIsDisplayed()
        onNodeWithText("Loading", useUnmergedTree = true).assertIsDisplayed()

        // The resting bar's title, and the FAB's create description on its production tag.
        onNode(
            hasText("Trainings") and hasAnyAncestor(hasTestTag("AllTrainingsTopBar")),
            useUnmergedTree = true,
        ).assertIsDisplayed()
        onNodeWithTag("AllTrainingsFab").assertContentDescriptionEquals("Create training")

        // A failed first page is its own verdict, with its own reason and the retry.
        state.value = coldOpen.copy(
            pagingUiState = paging(
                PagingData.empty(
                    sourceLoadStates = settled(
                        refresh = LoadState.Error(IllegalStateException("native refresh")),
                    ),
                ),
            ),
        )
        settleUntilTag("AllTrainingsColdOpenError")
        onNodeWithTag("AllTrainingsColdOpenError", useUnmergedTree = true).assertIsDisplayed()
        onNodeWithText("Couldn’t load the list", useUnmergedTree = true).assertIsDisplayed()
        onNodeWithText("Retry", useUnmergedTree = true).assertIsDisplayed()

        // Populated rows: each name and meta line (status · N exercises · tags), and the tag band.
        val populated = coldOpen.copy(
            pagingUiState = paging(PagingData.from(rows, sourceLoadStates = settled())),
            availableTags = tags,
        )
        state.value = populated
        settleUntilTag("AllTrainingsList")
        onNodeWithTag("AllTrainingsList", useUnmergedTree = true).assertIsDisplayed()
        EXPECTED_ROWS.forEach { (uuid, name, meta) ->
            onNodeWithTag("AllTrainingsItemName_$uuid", useUnmergedTree = true).assertTextEquals(name)
            onNodeWithTag("AllTrainingsItemMeta_$uuid", useUnmergedTree = true).assertTextEquals(meta)
        }
        onNodeWithTag("AllTrainingsTagFilter", useUnmergedTree = true).assertIsDisplayed()
        onNodeWithTag("AllTrainingsFab").assertContentDescriptionEquals("Create training")

        onNodeWithTag("AllTrainingsItemName_$ACTIVE", useUnmergedTree = true).performClick()
        assertEquals(Action.Click.OnTrainingClick(ACTIVE), actions.last())
        onNodeWithTag("AllTrainingsItemName_$RECENT", useUnmergedTree = true)
            .performTouchInput { longClick() }
        assertEquals(Action.Click.OnTrainingLongPress(RECENT), actions.last())
        onNodeWithTag("AllTrainingsTagFilter_$TAG_PUSH").performClick()
        assertEquals(Action.Click.OnTagFilterToggle(TAG_PUSH), actions.last())
        onNodeWithTag("AllTrainingsFab").performClick()
        assertEquals(Action.Click.OnFabClick, actions.last())

        // Both append tails, inside the list: the loading footer, then the error footer and retry.
        state.value = populated.copy(
            pagingUiState = paging(
                PagingData.from(rows, sourceLoadStates = settled(append = LoadState.Loading)),
            ),
        )
        settleUntil(listTail("AllTrainingsPagingLoading"))
        onNode(listTail("AllTrainingsPagingLoading"), useUnmergedTree = true).assertIsDisplayed()

        state.value = populated.copy(
            pagingUiState = paging(
                PagingData.from(
                    rows,
                    sourceLoadStates = settled(
                        append = LoadState.Error(IllegalStateException("native append")),
                    ),
                ),
            ),
        )
        settleUntil(listTail("AllTrainingsPagingError"))
        onNode(listTail("AllTrainingsPagingError"), useUnmergedTree = true).assertIsDisplayed()
        onNode(
            hasText("Couldn’t load more") and hasAnyAncestor(hasTestTag("AllTrainingsList")),
            useUnmergedTree = true,
        ).assertIsDisplayed()
        onNode(listTail("AllTrainingsPagingRetry"), useUnmergedTree = true).assertIsDisplayed()

        // First run with no session running: both CTAs are drawn and dispatch.
        val firstRun = coldOpen.copy(
            pagingUiState = paging(PagingData.empty(sourceLoadStates = settled())),
        )
        state.value = firstRun
        settleUntilTag("AllTrainingsEmptyState")
        onNodeWithTag("AllTrainingsEmptyState", useUnmergedTree = true).assertIsDisplayed()
        onNodeWithText("Your trainings will appear here", useUnmergedTree = true).assertIsDisplayed()
        onNodeWithText(
            "Build a template in advance, or start an empty one and add exercises as you go.",
            useUnmergedTree = true,
        ).assertIsDisplayed()
        onNodeWithText("Create a training").performClick()
        assertEquals(Action.Click.OnEmptyCreate, actions.last())
        onNodeWithText("Start an empty training").performClick()
        assertEquals(Action.Click.OnEmptyStartBlank, actions.last())

        // A running session withdraws the blank-start CTA and keeps create.
        state.value = firstRun.copy(hasActiveSession = true)
        settleScene()
        onNodeWithTag("AllTrainingsEmptyState", useUnmergedTree = true).assertIsDisplayed()
        onNodeWithText("Create a training").assertIsDisplayed()
        onNodeWithText("Start an empty training").assertDoesNotExist()

        // A filter that matches nothing is its own state, and Clear filter dispatches.
        val filtered = firstRun.copy(availableTags = tags, activeTagFilter = persistentSetOf(TAG_PUSH))
        state.value = filtered
        settleUntilTag("AllTrainingsFilteredEmpty")
        onNodeWithTag("AllTrainingsFilteredEmpty", useUnmergedTree = true).assertIsDisplayed()
        onNodeWithText("Nothing matches these tags", useUnmergedTree = true).assertIsDisplayed()
        onNodeWithText("Clear filter").performClick()
        assertEquals(Action.Click.OnClearTagFilter, actions.last())

        // Selection empty under a filter keeps Clear filter, and the selection bar replaces the title.
        val selection = SelectionMode.On(persistentSetOf(ACTIVE, RECENT))
        state.value = filtered.copy(selectionMode = selection)
        settleUntilTag("AllTrainingsSelectionEmpty")
        onNodeWithTag("AllTrainingsSelectionEmpty", useUnmergedTree = true).assertIsDisplayed()
        onNodeWithText("Nothing here, but your selection is kept", useUnmergedTree = true)
            .assertIsDisplayed()
        onNodeWithText("Your selection stays until you leave selection mode.", useUnmergedTree = true)
            .assertIsDisplayed()
        onNodeWithText("Clear filter").assertIsDisplayed()
        onNode(
            hasText("2 selected") and hasAnyAncestor(hasTestTag("AllTrainingsSelectionTopBar")),
            useUnmergedTree = true,
        ).assertIsDisplayed()
        onNodeWithTag("AllTrainingsSelectionTopBarClose").assertContentDescriptionEquals("Close selection")
        onNodeWithTag("AllTrainingsSelectionTopBarArchive").assertContentDescriptionEquals("Archive")
        onNodeWithTag("AllTrainingsFab").assertContentDescriptionEquals("Archive")
        onNodeWithTag("AllTrainingsTopBar").assertDoesNotExist()

        // Selection empty with no filter active offers no Clear filter at all.
        state.value = firstRun.copy(selectionMode = selection)
        settleScene()
        onNodeWithTag("AllTrainingsSelectionEmpty", useUnmergedTree = true).assertIsDisplayed()
        onNodeWithText("Clear filter").assertDoesNotExist()

        // Selected rows carry the check; the bar's close and archive dispatch.
        val selectedRows = populated.copy(selectionMode = selection)
        state.value = selectedRows
        settleUntilTag("AllTrainingsItemCheck_$ACTIVE")
        onNodeWithTag("AllTrainingsItemCheck_$ACTIVE", useUnmergedTree = true).assertIsDisplayed()
        onNodeWithTag("AllTrainingsItemCheck_$RECENT", useUnmergedTree = true).assertIsDisplayed()
        onAllNodesWithTag("AllTrainingsItemCheck_$HOURS", useUnmergedTree = true).assertCountEquals(0)
        onNodeWithTag("AllTrainingsSelectionTopBarClose").performClick()
        assertEquals(Action.Click.OnSelectionExit, actions.last())
        onNodeWithTag("AllTrainingsSelectionTopBarArchive").performClick()
        assertEquals(Action.Click.OnFabClick, actions.last())

        // The confirm dialog: title, plural body, impact and confirm label; confirm, then dismiss.
        state.value = selectedRows.copy(pendingBulkDelete = PendingBulkDelete(count = 2))
        settleScene()
        onNodeWithText("Archive selected?", useUnmergedTree = true).assertIsDisplayed()
        onNodeWithText(
            "2 trainings will move to archive. Restore from Settings → Archive.",
            useUnmergedTree = true,
        ).assertIsDisplayed()
        onNodeWithText("Reversible · history preserved", useUnmergedTree = true).assertIsDisplayed()
        onNodeWithText("Archive", useUnmergedTree = true).assertIsDisplayed()
        onNodeWithText("Archive").performClick()
        assertEquals(Action.Click.OnBulkDeleteConfirm, actions.last())
        onNodeWithText(kitString { getString(KitRes.string.core_ui_kit_action_cancel) }).performClick()
        assertEquals(Action.Click.OnBulkDeleteDismiss, actions.last())

        assertEquals(
            listOf<Action>(
                Action.Click.OnTrainingClick(ACTIVE),
                Action.Click.OnTrainingLongPress(RECENT),
                Action.Click.OnTagFilterToggle(TAG_PUSH),
                Action.Click.OnFabClick,
                Action.Click.OnEmptyCreate,
                Action.Click.OnEmptyStartBlank,
                Action.Click.OnClearTagFilter,
                Action.Click.OnSelectionExit,
                Action.Click.OnFabClick,
                Action.Click.OnBulkDeleteConfirm,
                Action.Click.OnBulkDeleteDismiss,
            ),
            actions,
        )
    }

    private fun training(
        uuid: String,
        name: String,
        tags: List<String>,
        exerciseCount: Int,
        lastSessionAt: Long? = null,
        activeSessionStartedAt: Long? = null,
    ): TrainingListItemDomain = TrainingListItemDomain(
        uuid = uuid,
        name = name,
        tags = tags,
        exerciseCount = exerciseCount,
        lastSessionAt = lastSessionAt,
        isActive = activeSessionStartedAt != null,
        activeSessionUuid = activeSessionStartedAt?.let { "native-session" },
        activeSessionStartedAt = activeSessionStartedAt,
    )

    private fun <T : Any> paging(data: PagingData<T>): PagingUiState<PagingData<T>> =
        PagingUiState { flowOf(data) }

    private fun settled(
        refresh: LoadState = LoadState.NotLoading(endOfPaginationReached = false),
        append: LoadState = LoadState.NotLoading(endOfPaginationReached = true),
    ): LoadStates = LoadStates(
        refresh = refresh,
        prepend = LoadState.NotLoading(endOfPaginationReached = true),
        append = append,
    )

    /** A paging tail's production [tag] inside the list, so the cold-open twin cannot satisfy it. */
    private fun listTail(tag: String): SemanticsMatcher =
        hasTestTag(tag) and hasAnyAncestor(hasTestTag("AllTrainingsList"))

    private fun kitString(read: suspend () -> String): String = runBlocking { read() }

    private fun featureCatalog(): List<String> = runBlocking {
        listOf(
            getString(AllTrainingsRes.string.feature_all_trainings_title),
            getString(AllTrainingsRes.string.feature_all_trainings_empty_headline),
            getString(AllTrainingsRes.string.feature_all_trainings_empty_supporting),
            getString(AllTrainingsRes.string.feature_all_trainings_fab_create),
            getString(AllTrainingsRes.string.feature_all_trainings_status_in_progress_format),
            getString(AllTrainingsRes.string.feature_all_trainings_status_last_format),
            getString(AllTrainingsRes.string.feature_all_trainings_status_never),
            getString(AllTrainingsRes.string.feature_all_trainings_relative_just_now),
            getString(AllTrainingsRes.string.feature_all_trainings_relative_minutes_format),
            getString(AllTrainingsRes.string.feature_all_trainings_relative_hours_format),
            getString(AllTrainingsRes.string.feature_all_trainings_relative_days_format),
            getPluralString(AllTrainingsRes.plurals.feature_all_trainings_exercise_count, 1),
            getPluralString(AllTrainingsRes.plurals.feature_all_trainings_exercise_count, 2),
            getString(AllTrainingsRes.string.feature_all_trainings_selection_close),
            getPluralString(AllTrainingsRes.plurals.feature_all_trainings_selected_count, 1),
            getPluralString(AllTrainingsRes.plurals.feature_all_trainings_selected_count, 2),
            getString(AllTrainingsRes.string.feature_all_trainings_bulk_archive),
            getPluralString(AllTrainingsRes.plurals.feature_all_trainings_bulk_archive_success, 1),
            getPluralString(AllTrainingsRes.plurals.feature_all_trainings_bulk_archive_success, 2),
            getString(AllTrainingsRes.string.feature_all_trainings_bulk_archive_partial_format),
            getString(AllTrainingsRes.string.feature_all_trainings_bulk_archive_confirm_title),
            getPluralString(AllTrainingsRes.plurals.feature_all_trainings_bulk_archive_confirm_body, 1),
            getPluralString(AllTrainingsRes.plurals.feature_all_trainings_bulk_archive_confirm_body, 2),
            getString(AllTrainingsRes.string.feature_all_trainings_bulk_archive_impact),
            getString(AllTrainingsRes.string.feature_all_trainings_paging_loading),
            getString(AllTrainingsRes.string.feature_all_trainings_paging_error),
            getString(AllTrainingsRes.string.feature_all_trainings_paging_retry),
            getString(AllTrainingsRes.string.feature_all_trainings_empty_create),
            getString(AllTrainingsRes.string.feature_all_trainings_empty_start_blank),
            getString(AllTrainingsRes.string.feature_all_trainings_filtered_empty_headline),
            getString(AllTrainingsRes.string.feature_all_trainings_filtered_empty_clear),
            getString(AllTrainingsRes.string.feature_all_trainings_selection_empty_headline),
            getString(AllTrainingsRes.string.feature_all_trainings_selection_empty_supporting),
            getString(AllTrainingsRes.string.feature_all_trainings_refresh_error),
        )
    }

    private fun filledCatalog(): List<String> = runBlocking {
        listOf(
            getString(AllTrainingsRes.string.feature_all_trainings_status_in_progress_format, "5m"),
            getString(AllTrainingsRes.string.feature_all_trainings_status_last_format, "2h"),
            getString(AllTrainingsRes.string.feature_all_trainings_relative_minutes_format, 5),
            getString(AllTrainingsRes.string.feature_all_trainings_relative_hours_format, 2),
            getString(AllTrainingsRes.string.feature_all_trainings_relative_days_format, 3),
            getPluralString(AllTrainingsRes.plurals.feature_all_trainings_exercise_count, 1, 1),
            getPluralString(AllTrainingsRes.plurals.feature_all_trainings_exercise_count, 2, 2),
            getPluralString(AllTrainingsRes.plurals.feature_all_trainings_selected_count, 1, 1),
            getPluralString(AllTrainingsRes.plurals.feature_all_trainings_selected_count, 2, 2),
            getPluralString(AllTrainingsRes.plurals.feature_all_trainings_bulk_archive_success, 1, 1),
            getPluralString(AllTrainingsRes.plurals.feature_all_trainings_bulk_archive_success, 2, 2),
            getString(
                AllTrainingsRes.string.feature_all_trainings_bulk_archive_partial_format,
                2,
                "Legs, Push",
            ),
            getPluralString(AllTrainingsRes.plurals.feature_all_trainings_bulk_archive_confirm_body, 1, 1),
            getPluralString(AllTrainingsRes.plurals.feature_all_trainings_bulk_archive_confirm_body, 2, 2),
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
     * per branch. Settles in bounded steps until a node carrying the branch's production tag exists.
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

        /** Fixed so the scene never depends on the clock. */
        const val NOW = 1_720_000_000_000L

        const val MINUTE = 60_000L
        const val HOUR = 60 * MINUTE
        const val DAY = 24 * HOUR

        const val ACTIVE = "native-active"
        const val RECENT = "native-recent"
        const val HOURS = "native-hours"
        const val DAYS = "native-days"
        const val NEVER = "native-never"
        const val TAG_PUSH = "native-tag-push"
        const val TAG_LEGS = "native-tag-legs"

        /** Ten virtual seconds: far beyond the kit's 140 ms deferral and 260 ms minimum hold. */
        const val MAX_SETTLE_STEPS = 10

        /** Settles that a transient first-frame verdict could not survive. */
        const val STABLE_SETTLE_STEPS = 3

        /** uuid, name and the drawn meta line: status, exercise count, then tags. */
        val EXPECTED_ROWS = listOf(
            Triple(ACTIVE, "Push day", "in progress · started 5m ago · 5 exercises · Push · Chest"),
            Triple(RECENT, "Pull day", "last: just now · 1 exercise · Pull"),
            Triple(HOURS, "Legs", "last: 2h · 4 exercises"),
            Triple(DAYS, "Full body", "last: 3d · 6 exercises · Legs · Push"),
            Triple(NEVER, "Mobility", "never trained · 2 exercises"),
        )

        /** The EN catalog in file order; each plural as its one item, then its other item. */
        val EXPECTED_CATALOG = listOf(
            "Trainings",
            "Your trainings will appear here",
            "Build a template in advance, or start an empty one and add exercises as you go.",
            "Create training",
            "in progress · started %1\$s ago",
            "last: %1\$s",
            "never trained",
            "just now",
            "%1\$dm",
            "%1\$dh",
            "%1\$dd",
            "%1\$d exercise",
            "%1\$d exercises",
            "Close selection",
            "%1\$d selected",
            "%1\$d selected",
            "Archive",
            "%1\$d training archived",
            "%1\$d trainings archived",
            "Archived %1\$d, blocked: %2\$s",
            "Archive selected?",
            "%1\$d training will move to archive. Restore from Settings → Archive.",
            "%1\$d trainings will move to archive. Restore from Settings → Archive.",
            "Reversible · history preserved",
            "Loading",
            "Couldn’t load more",
            "Retry",
            "Create a training",
            "Start an empty training",
            "Nothing matches these tags",
            "Clear filter",
            "Nothing here, but your selection is kept",
            "Your selection stays until you leave selection mode.",
            "Couldn’t load the list",
        )

        /** Every placeholder-bearing entry of [EXPECTED_CATALOG], filled by the formatter. */
        val EXPECTED_FILLED = listOf(
            "in progress · started 5m ago",
            "last: 2h",
            "5m",
            "2h",
            "3d",
            "1 exercise",
            "2 exercises",
            "1 selected",
            "2 selected",
            "1 training archived",
            "2 trainings archived",
            "Archived 2, blocked: Legs, Push",
            "1 training will move to archive. Restore from Settings → Archive.",
            "2 trainings will move to archive. Restore from Settings → Archive.",
        )
    }
}
