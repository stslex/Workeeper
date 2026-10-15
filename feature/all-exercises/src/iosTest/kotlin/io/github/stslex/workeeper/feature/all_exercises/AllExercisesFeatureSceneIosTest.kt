// SPDX-License-Identifier: GPL-3.0-only
@file:OptIn(ExperimentalTestApi::class)

package io.github.stslex.workeeper.feature.all_exercises

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
import io.github.stslex.workeeper.core.ui.kit.strings.ComposeStringResolver
import io.github.stslex.workeeper.core.ui.kit.theme.AppTheme
import io.github.stslex.workeeper.feature.all_exercises.domain.model.BulkArchiveResult
import io.github.stslex.workeeper.feature.all_exercises.domain.model.ExerciseDomain
import io.github.stslex.workeeper.feature.all_exercises.domain.model.ExerciseListItemDomain
import io.github.stslex.workeeper.feature.all_exercises.domain.model.ExerciseTypeDomain
import io.github.stslex.workeeper.feature.all_exercises.mvi.mapper.AllExercisesUiMapper.toBlockedArchiveDialog
import io.github.stslex.workeeper.feature.all_exercises.mvi.mapper.AllExercisesUiMapper.toUi
import io.github.stslex.workeeper.feature.all_exercises.mvi.model.TagUiModel
import io.github.stslex.workeeper.feature.all_exercises.mvi.store.AllExercisesStore.Action
import io.github.stslex.workeeper.feature.all_exercises.mvi.store.AllExercisesStore.State
import io.github.stslex.workeeper.feature.all_exercises.mvi.store.AllExercisesStore.State.PendingBulkDelete
import io.github.stslex.workeeper.feature.all_exercises.mvi.store.AllExercisesStore.State.PendingDelete
import io.github.stslex.workeeper.feature.all_exercises.mvi.store.AllExercisesStore.State.SelectionMode
import io.github.stslex.workeeper.feature.all_exercises.resources.feature_all_exercises_blocked_archive_confirm
import io.github.stslex.workeeper.feature.all_exercises.resources.feature_all_exercises_blocked_archive_more
import io.github.stslex.workeeper.feature.all_exercises.resources.feature_all_exercises_blocked_archive_next_step
import io.github.stslex.workeeper.feature.all_exercises.resources.feature_all_exercises_blocked_archive_title
import io.github.stslex.workeeper.feature.all_exercises.resources.feature_all_exercises_blocked_archive_used_in_format
import io.github.stslex.workeeper.feature.all_exercises.resources.feature_all_exercises_bulk_archive
import io.github.stslex.workeeper.feature.all_exercises.resources.feature_all_exercises_bulk_archive_confirm_body
import io.github.stslex.workeeper.feature.all_exercises.resources.feature_all_exercises_bulk_archive_confirm_title
import io.github.stslex.workeeper.feature.all_exercises.resources.feature_all_exercises_bulk_archive_impact
import io.github.stslex.workeeper.feature.all_exercises.resources.feature_all_exercises_bulk_archive_success
import io.github.stslex.workeeper.feature.all_exercises.resources.feature_all_exercises_empty_create
import io.github.stslex.workeeper.feature.all_exercises.resources.feature_all_exercises_empty_headline
import io.github.stslex.workeeper.feature.all_exercises.resources.feature_all_exercises_empty_supporting
import io.github.stslex.workeeper.feature.all_exercises.resources.feature_all_exercises_fab_create
import io.github.stslex.workeeper.feature.all_exercises.resources.feature_all_exercises_filtered_empty_clear
import io.github.stslex.workeeper.feature.all_exercises.resources.feature_all_exercises_filtered_empty_headline
import io.github.stslex.workeeper.feature.all_exercises.resources.feature_all_exercises_footer_separator
import io.github.stslex.workeeper.feature.all_exercises.resources.feature_all_exercises_last_trained_days
import io.github.stslex.workeeper.feature.all_exercises.resources.feature_all_exercises_last_trained_hours
import io.github.stslex.workeeper.feature.all_exercises.resources.feature_all_exercises_last_trained_just_now
import io.github.stslex.workeeper.feature.all_exercises.resources.feature_all_exercises_last_trained_minutes
import io.github.stslex.workeeper.feature.all_exercises.resources.feature_all_exercises_linked_trainings_count
import io.github.stslex.workeeper.feature.all_exercises.resources.feature_all_exercises_paging_error
import io.github.stslex.workeeper.feature.all_exercises.resources.feature_all_exercises_paging_loading
import io.github.stslex.workeeper.feature.all_exercises.resources.feature_all_exercises_paging_retry
import io.github.stslex.workeeper.feature.all_exercises.resources.feature_all_exercises_permanent_delete_body
import io.github.stslex.workeeper.feature.all_exercises.resources.feature_all_exercises_permanent_delete_confirm
import io.github.stslex.workeeper.feature.all_exercises.resources.feature_all_exercises_permanent_delete_impact
import io.github.stslex.workeeper.feature.all_exercises.resources.feature_all_exercises_permanent_delete_success
import io.github.stslex.workeeper.feature.all_exercises.resources.feature_all_exercises_permanent_delete_title
import io.github.stslex.workeeper.feature.all_exercises.resources.feature_all_exercises_refresh_error
import io.github.stslex.workeeper.feature.all_exercises.resources.feature_all_exercises_selected_count
import io.github.stslex.workeeper.feature.all_exercises.resources.feature_all_exercises_selection_close
import io.github.stslex.workeeper.feature.all_exercises.resources.feature_all_exercises_selection_empty_headline
import io.github.stslex.workeeper.feature.all_exercises.resources.feature_all_exercises_selection_empty_supporting
import io.github.stslex.workeeper.feature.all_exercises.resources.feature_all_exercises_session_count
import io.github.stslex.workeeper.feature.all_exercises.resources.feature_all_exercises_title
import io.github.stslex.workeeper.feature.all_exercises.resources.feature_all_exercises_type_weighted
import io.github.stslex.workeeper.feature.all_exercises.resources.feature_all_exercises_type_weightless
import io.github.stslex.workeeper.feature.all_exercises.ui.AllExercisesScreen
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentSetOf
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import io.github.stslex.workeeper.core.ui.kit.resources.Res as KitRes
import io.github.stslex.workeeper.feature.all_exercises.resources.Res as AllExercisesRes

class AllExercisesFeatureSceneIosTest {

    /** Every read outside composition goes through this one real resolver: no fake here. */
    private val resolver = ComposeStringResolver()

    @Test
    fun resourcesPagingBranchesSelectionAndActionsRenderAndDispatch() = runComposeUiTest {
        // The private catalog in file order through the real resolver (D2): as stored, then every
        // placeholder-bearing entry filled — the first execution of getPluralString's delegation.
        assertEquals(EXPECTED_STORED, storedCatalog())
        assertEquals(EXPECTED_FILLED, filledCatalog())

        // Mapper copy (D1) through the production toUi and the real resolver, at a fixed now.
        val domains = listOf(
            exercise(WEIGHTED, "Bench press", ExerciseTypeDomain.WEIGHTED, listOf("Push", "Chest"))
                .copy(sessionCount = 12, linkedTrainingsCount = 3, lastTrainedAt = NOW - 4 * DAY - 3 * HOUR),
            exercise(RECENT, "Pull-up", ExerciseTypeDomain.WEIGHTLESS, listOf("Pull"))
                .copy(sessionCount = 1, linkedTrainingsCount = 1, lastTrainedAt = NOW - 30_000L),
            exercise(HOURS, "Squat", ExerciseTypeDomain.WEIGHTED, emptyList())
                .copy(linkedTrainingsCount = 2, lastTrainedAt = NOW - 2 * HOUR - 15 * MINUTE),
            exercise(MINUTES, "Plank", ExerciseTypeDomain.WEIGHTLESS, listOf("Core"))
                .copy(sessionCount = 5, lastTrainedAt = NOW - 15 * MINUTE - 20_000L),
            exercise(FRESH, "Lunge", ExerciseTypeDomain.WEIGHTED, emptyList()),
        )
        val rows = runBlocking { domains.map { it.toUi(stringResolver = resolver, nowMillis = NOW) } }
        assertEquals(
            listOf(
                "12 sessions · in 3 trainings · last 4d ago",
                "1 session · in 1 training · last just now",
                "in 2 trainings · last 2h ago",
                "5 sessions · last 15m ago",
                "",
            ),
            rows.map { it.footerLabel },
        )

        // Blocked-archive copy (D1) through the production mapper and the real resolver.
        val dialogs = runBlocking {
            listOf(
                blockedResult(1, "Bench", listOf("Push", "Pull", "Legs", "Upper")),
                blockedResult(3, "Row", listOf("Legs")),
                blockedResult(0, "Bench", listOf("Push", "Legs")),
            ).map { it.toBlockedArchiveDialog(resolver) }
        }
        assertEquals(
            listOf("1 exercise archived", "3 exercises archived", null),
            dialogs.map { it.archivedSummary },
        )
        assertEquals(
            listOf("used in Push, Pull +2 more", "used in Legs", "used in Push, Legs"),
            dialogs.map { it.items.single().trainingsLabel },
        )

        val tags = persistentListOf(TagUiModel(TAG_PUSH, "Push"), TagUiModel(TAG_LEGS, "Legs"))
        val coldOpen = State(
            pagingUiState = paging(
                PagingData.empty(sourceLoadStates = settled(refresh = LoadState.Loading)),
            ),
            availableTags = persistentListOf(),
            activeTagFilter = persistentSetOf(),
            pendingPermanentDelete = null,
            selectionMode = SelectionMode.Off,
            pendingBulkDelete = null,
            blockedArchiveDialog = null,
        )
        val state = mutableStateOf(coldOpen)
        val actions = mutableListOf<Action>()

        setContent {
            AppTheme {
                AllExercisesScreen(
                    state = state.value,
                    consume = actions::add,
                )
            }
        }

        // Cold open: the deferred loading verdict, held rather than the first frame of a new list.
        settleUntilTag("AllExercisesColdOpen")
        repeat(STABLE_SETTLE_STEPS) { settleScene() }
        onNodeWithTag("AllExercisesColdOpen", useUnmergedTree = true).assertIsDisplayed()
        onNodeWithText("Loading", useUnmergedTree = true).assertIsDisplayed()

        // The resting bar's title, and the FAB's create description on its production tag.
        onNode(
            hasText("Exercises") and hasAnyAncestor(hasTestTag("AllExercisesTopBar")),
            useUnmergedTree = true,
        ).assertIsDisplayed()
        onNodeWithTag("AllExercisesFab").assertContentDescriptionEquals("Create exercise")

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
        settleUntilTag("AllExercisesColdOpenError")
        onNodeWithTag("AllExercisesColdOpenError", useUnmergedTree = true).assertIsDisplayed()
        onNodeWithText("Couldn’t load the list", useUnmergedTree = true).assertIsDisplayed()
        onNodeWithText("Retry", useUnmergedTree = true).assertIsDisplayed()

        // Populated rows from the mapper's models: each name and meta line, and the tag band.
        val populated = coldOpen.copy(
            pagingUiState = paging(PagingData.from(rows, sourceLoadStates = settled())),
            availableTags = tags,
        )
        state.value = populated
        settleUntilTag("AllExercisesList")
        onNodeWithTag("AllExercisesList", useUnmergedTree = true).assertIsDisplayed()
        EXPECTED_ROWS.forEach { (uuid, name, meta) ->
            onNodeWithTag("AllExercisesItemName_$uuid", useUnmergedTree = true).assertTextEquals(name)
            onNodeWithTag("AllExercisesItemMeta_$uuid", useUnmergedTree = true).assertTextEquals(meta)
        }
        onNodeWithTag("AllExercisesTagFilter", useUnmergedTree = true).assertIsDisplayed()
        onNodeWithTag("AllExercisesFab").assertContentDescriptionEquals("Create exercise")

        onNodeWithTag("AllExercisesItemName_$WEIGHTED", useUnmergedTree = true).performClick()
        assertEquals(Action.Click.OnExerciseClick(WEIGHTED), actions.last())
        onNodeWithTag("AllExercisesItemName_$RECENT", useUnmergedTree = true)
            .performTouchInput { longClick() }
        assertEquals(Action.Click.OnExerciseLongPress(RECENT), actions.last())
        onNodeWithTag("AllExercisesTagFilter_$TAG_PUSH").performClick()
        assertEquals(Action.Click.OnTagFilterToggle(TAG_PUSH), actions.last())
        onNodeWithTag("AllExercisesFab").performClick()
        assertEquals(Action.Click.OnFabClick, actions.last())

        // Both append tails, inside the list: the loading footer, then the error footer and retry.
        state.value = populated.copy(
            pagingUiState = paging(
                PagingData.from(rows, sourceLoadStates = settled(append = LoadState.Loading)),
            ),
        )
        settleUntil(listTail("AllExercisesPagingLoading"))
        onNode(listTail("AllExercisesPagingLoading"), useUnmergedTree = true).assertIsDisplayed()

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
        settleUntil(listTail("AllExercisesPagingError"))
        onNode(listTail("AllExercisesPagingError"), useUnmergedTree = true).assertIsDisplayed()
        onNode(
            hasText("Couldn’t load more") and hasAnyAncestor(hasTestTag("AllExercisesList")),
            useUnmergedTree = true,
        ).assertIsDisplayed()
        onNode(listTail("AllExercisesPagingRetry"), useUnmergedTree = true).assertIsDisplayed()

        // First run: glyph block, headline, sentence and the one CTA, which dispatches.
        val firstRun = coldOpen.copy(
            pagingUiState = paging(PagingData.empty(sourceLoadStates = settled())),
        )
        state.value = firstRun
        settleUntilTag("AllExercisesEmptyState")
        onNodeWithTag("AllExercisesEmptyState", useUnmergedTree = true).assertIsDisplayed()
        onNodeWithText("Your exercises will appear here", useUnmergedTree = true).assertIsDisplayed()
        onNodeWithText(
            "Add the first one — after that you can put it into any training.",
            useUnmergedTree = true,
        ).assertIsDisplayed()
        onNodeWithText("Add exercise").performClick()
        assertEquals(Action.Click.OnEmptyCreate, actions.last())

        // A filter that matches nothing is its own state, and Clear filter dispatches.
        val filtered = firstRun.copy(availableTags = tags, activeTagFilter = persistentSetOf(TAG_PUSH))
        state.value = filtered
        settleUntilTag("AllExercisesFilteredEmpty")
        onNodeWithTag("AllExercisesFilteredEmpty", useUnmergedTree = true).assertIsDisplayed()
        onNodeWithText("Nothing matches these tags", useUnmergedTree = true).assertIsDisplayed()
        onNodeWithText("Clear filter").performClick()
        assertEquals(Action.Click.OnClearTagFilter, actions.last())

        // Selection empty under a filter keeps Clear filter, and the selection bar replaces the title.
        val selection = SelectionMode.On(persistentSetOf(WEIGHTED, RECENT))
        state.value = filtered.copy(selectionMode = selection)
        settleUntilTag("AllExercisesSelectionEmpty")
        onNodeWithTag("AllExercisesSelectionEmpty", useUnmergedTree = true).assertIsDisplayed()
        onNodeWithText("Nothing here, but your selection is kept", useUnmergedTree = true)
            .assertIsDisplayed()
        onNodeWithText("Your selection stays until you leave selection mode.", useUnmergedTree = true)
            .assertIsDisplayed()
        onNodeWithText("Clear filter").assertIsDisplayed()
        onNode(
            hasText("2 selected") and hasAnyAncestor(hasTestTag("AllExercisesSelectionTopBar")),
            useUnmergedTree = true,
        ).assertIsDisplayed()
        onNodeWithTag("AllExercisesSelectionTopBarClose").assertContentDescriptionEquals("Close selection")
        onNodeWithTag("AllExercisesSelectionTopBarArchive").assertContentDescriptionEquals("Archive")
        onNodeWithTag("AllExercisesFab").assertContentDescriptionEquals("Archive")
        onNodeWithTag("AllExercisesTopBar").assertDoesNotExist()

        // Selection empty with no filter active offers no Clear filter at all.
        state.value = firstRun.copy(selectionMode = selection)
        settleScene()
        onNodeWithTag("AllExercisesSelectionEmpty", useUnmergedTree = true).assertIsDisplayed()
        onNodeWithText("Clear filter").assertDoesNotExist()

        // Selected rows carry the check; close, the morphed FAB and the bar's archive dispatch.
        val selectedRows = populated.copy(selectionMode = selection)
        state.value = selectedRows
        settleUntilTag("AllExercisesItemCheck_$WEIGHTED")
        onNodeWithTag("AllExercisesItemCheck_$WEIGHTED", useUnmergedTree = true).assertIsDisplayed()
        onNodeWithTag("AllExercisesItemCheck_$RECENT", useUnmergedTree = true).assertIsDisplayed()
        onAllNodesWithTag("AllExercisesItemCheck_$HOURS", useUnmergedTree = true).assertCountEquals(0)
        onNodeWithTag("AllExercisesSelectionTopBarClose").performClick()
        assertEquals(Action.Click.OnSelectionExit, actions.last())
        onNodeWithTag("AllExercisesFab").performClick()
        assertEquals(Action.Click.OnBulkDelete, actions.last())
        onNodeWithTag("AllExercisesSelectionTopBarArchive").performClick()
        assertEquals(Action.Click.OnBulkDelete, actions.last())

        val cancel = runBlocking { resolver.getString(KitRes.string.core_ui_kit_action_cancel) }

        // The bulk-archive confirm: title, plural body, impact and confirm label; confirm, dismiss.
        state.value = selectedRows.copy(pendingBulkDelete = PendingBulkDelete(count = 2))
        settleScene()
        onNodeWithText("Archive selected?", useUnmergedTree = true).assertIsDisplayed()
        onNodeWithText(
            "2 exercises will move to archive. Restore from Settings → Archive.",
            useUnmergedTree = true,
        ).assertIsDisplayed()
        onNodeWithText("Reversible · history preserved", useUnmergedTree = true).assertIsDisplayed()
        onNodeWithText("Archive", useUnmergedTree = true).assertIsDisplayed()
        onNodeWithText("Archive").performClick()
        assertEquals(Action.Click.OnBulkDeleteConfirm, actions.last())
        onNodeWithText(cancel).performClick()
        assertEquals(Action.Click.OnBulkDeleteDismiss, actions.last())

        // The blocked-archive acknowledgement, from the mapper's first model.
        state.value = populated.copy(blockedArchiveDialog = dialogs.first())
        settleScene()
        onNodeWithText("Some couldn’t be archived", useUnmergedTree = true).assertIsDisplayed()
        onNodeWithText("1 exercise archived", useUnmergedTree = true).assertIsDisplayed()
        onNodeWithText("Bench", useUnmergedTree = true).assertIsDisplayed()
        onNodeWithText("used in Push, Pull +2 more", useUnmergedTree = true).assertIsDisplayed()
        onNodeWithText(
            "These are still in active trainings. Remove them from those trainings first, then archive.",
            useUnmergedTree = true,
        ).assertIsDisplayed()
        onNodeWithText("Got it").performClick()
        assertEquals(Action.Click.OnBlockedArchiveDismiss, actions.last())

        // The permanent-delete dialog, rendered from State (no UI path sets it; SPEC 5.7).
        state.value = populated.copy(pendingPermanentDelete = PendingDelete(uuid = FRESH, name = "Bench"))
        settleScene()
        onNodeWithText("Delete ‘Bench’ permanently?", useUnmergedTree = true).assertIsDisplayed()
        onNodeWithText(
            "This exercise has no history and isn’t used. This cannot be undone.",
            useUnmergedTree = true,
        ).assertIsDisplayed()
        onNodeWithText("No history affected", useUnmergedTree = true).assertIsDisplayed()
        onNodeWithText("Delete").performClick()
        assertEquals(Action.Click.OnConfirmPermanentDelete, actions.last())
        onNodeWithText(cancel).performClick()
        assertEquals(Action.Click.OnCancelPermanentDelete, actions.last())

        assertEquals(
            listOf<Action>(
                Action.Click.OnExerciseClick(WEIGHTED),
                Action.Click.OnExerciseLongPress(RECENT),
                Action.Click.OnTagFilterToggle(TAG_PUSH),
                Action.Click.OnFabClick,
                Action.Click.OnEmptyCreate,
                Action.Click.OnClearTagFilter,
                Action.Click.OnSelectionExit,
                Action.Click.OnBulkDelete,
                Action.Click.OnBulkDelete,
                Action.Click.OnBulkDeleteConfirm,
                Action.Click.OnBulkDeleteDismiss,
                Action.Click.OnBlockedArchiveDismiss,
                Action.Click.OnConfirmPermanentDelete,
                Action.Click.OnCancelPermanentDelete,
            ),
            actions,
        )
    }

    /** A library row with no stats; each fixture adds its own with `copy`. */
    private fun exercise(
        uuid: String,
        name: String,
        type: ExerciseTypeDomain,
        tags: List<String>,
    ): ExerciseListItemDomain = ExerciseListItemDomain(
        exercise = ExerciseDomain(
            uuid = uuid,
            name = name,
            type = type,
            description = null,
            imagePath = null,
            archived = false,
            archivedAt = null,
            timestamp = 0L,
        ),
        tags = tags,
        sessionCount = 0,
        linkedTrainingsCount = 0,
        lastTrainedAt = null,
    )

    private fun blockedResult(
        archivedCount: Int,
        name: String,
        activeTrainings: List<String>,
    ): BulkArchiveResult = BulkArchiveResult(
        archivedCount = archivedCount,
        blocked = listOf(BulkArchiveResult.BlockedExerciseDomain(name, activeTrainings)),
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
        hasTestTag(tag) and hasAnyAncestor(hasTestTag("AllExercisesList"))

    /** The catalog in file order, each read with no format arguments (the template verbatim). */
    private fun storedCatalog(): List<String> = runBlocking {
        listOf(
            resolver.getString(AllExercisesRes.string.feature_all_exercises_title),
            resolver.getString(AllExercisesRes.string.feature_all_exercises_empty_headline),
            resolver.getString(AllExercisesRes.string.feature_all_exercises_empty_supporting),
            resolver.getString(AllExercisesRes.string.feature_all_exercises_empty_create),
            resolver.getString(AllExercisesRes.string.feature_all_exercises_fab_create),
            resolver.getString(AllExercisesRes.string.feature_all_exercises_type_weighted),
            resolver.getString(AllExercisesRes.string.feature_all_exercises_type_weightless),
            resolver.getString(AllExercisesRes.string.feature_all_exercises_paging_loading),
            resolver.getString(AllExercisesRes.string.feature_all_exercises_paging_error),
            resolver.getString(AllExercisesRes.string.feature_all_exercises_paging_retry),
            resolver.getString(AllExercisesRes.string.feature_all_exercises_permanent_delete_title),
            resolver.getString(AllExercisesRes.string.feature_all_exercises_permanent_delete_body),
            resolver.getString(AllExercisesRes.string.feature_all_exercises_permanent_delete_impact),
            resolver.getString(AllExercisesRes.string.feature_all_exercises_permanent_delete_confirm),
            resolver.getString(AllExercisesRes.string.feature_all_exercises_permanent_delete_success),
            resolver.getPluralString(AllExercisesRes.plurals.feature_all_exercises_session_count, 1),
            resolver.getPluralString(AllExercisesRes.plurals.feature_all_exercises_session_count, 2),
            resolver.getPluralString(AllExercisesRes.plurals.feature_all_exercises_linked_trainings_count, 1),
            resolver.getPluralString(AllExercisesRes.plurals.feature_all_exercises_linked_trainings_count, 2),
            resolver.getString(AllExercisesRes.string.feature_all_exercises_last_trained_just_now),
            resolver.getPluralString(AllExercisesRes.plurals.feature_all_exercises_last_trained_minutes, 1),
            resolver.getPluralString(AllExercisesRes.plurals.feature_all_exercises_last_trained_minutes, 2),
            resolver.getPluralString(AllExercisesRes.plurals.feature_all_exercises_last_trained_hours, 1),
            resolver.getPluralString(AllExercisesRes.plurals.feature_all_exercises_last_trained_hours, 2),
            resolver.getPluralString(AllExercisesRes.plurals.feature_all_exercises_last_trained_days, 1),
            resolver.getPluralString(AllExercisesRes.plurals.feature_all_exercises_last_trained_days, 2),
            resolver.getString(AllExercisesRes.string.feature_all_exercises_footer_separator),
            resolver.getString(AllExercisesRes.string.feature_all_exercises_selection_close),
            resolver.getPluralString(AllExercisesRes.plurals.feature_all_exercises_selected_count, 1),
            resolver.getPluralString(AllExercisesRes.plurals.feature_all_exercises_selected_count, 2),
            resolver.getString(AllExercisesRes.string.feature_all_exercises_bulk_archive),
            resolver.getPluralString(AllExercisesRes.plurals.feature_all_exercises_bulk_archive_success, 1),
            resolver.getPluralString(AllExercisesRes.plurals.feature_all_exercises_bulk_archive_success, 2),
            resolver.getString(AllExercisesRes.string.feature_all_exercises_bulk_archive_confirm_title),
            resolver.getPluralString(AllExercisesRes.plurals.feature_all_exercises_bulk_archive_confirm_body, 1),
            resolver.getPluralString(AllExercisesRes.plurals.feature_all_exercises_bulk_archive_confirm_body, 2),
            resolver.getString(AllExercisesRes.string.feature_all_exercises_bulk_archive_impact),
            resolver.getString(AllExercisesRes.string.feature_all_exercises_blocked_archive_title),
            resolver.getString(AllExercisesRes.string.feature_all_exercises_blocked_archive_used_in_format),
            resolver.getPluralString(AllExercisesRes.plurals.feature_all_exercises_blocked_archive_more, 1),
            resolver.getPluralString(AllExercisesRes.plurals.feature_all_exercises_blocked_archive_more, 2),
            resolver.getString(AllExercisesRes.string.feature_all_exercises_blocked_archive_next_step),
            resolver.getString(AllExercisesRes.string.feature_all_exercises_blocked_archive_confirm),
            resolver.getString(AllExercisesRes.string.feature_all_exercises_filtered_empty_headline),
            resolver.getString(AllExercisesRes.string.feature_all_exercises_filtered_empty_clear),
            resolver.getString(AllExercisesRes.string.feature_all_exercises_selection_empty_headline),
            resolver.getString(AllExercisesRes.string.feature_all_exercises_selection_empty_supporting),
            resolver.getString(AllExercisesRes.string.feature_all_exercises_refresh_error),
        )
    }

    /** Every placeholder-bearing entry, filled: the count for plurals, the SPEC's two arguments. */
    private fun filledCatalog(): List<String> = runBlocking {
        listOf(
            resolver.getString(AllExercisesRes.string.feature_all_exercises_permanent_delete_title, "Bench"),
            resolver.getPluralString(AllExercisesRes.plurals.feature_all_exercises_session_count, 1, 1),
            resolver.getPluralString(AllExercisesRes.plurals.feature_all_exercises_session_count, 2, 2),
            resolver.getPluralString(AllExercisesRes.plurals.feature_all_exercises_linked_trainings_count, 1, 1),
            resolver.getPluralString(AllExercisesRes.plurals.feature_all_exercises_linked_trainings_count, 2, 2),
            resolver.getPluralString(AllExercisesRes.plurals.feature_all_exercises_last_trained_minutes, 1, 1),
            resolver.getPluralString(AllExercisesRes.plurals.feature_all_exercises_last_trained_minutes, 2, 2),
            resolver.getPluralString(AllExercisesRes.plurals.feature_all_exercises_last_trained_hours, 1, 1),
            resolver.getPluralString(AllExercisesRes.plurals.feature_all_exercises_last_trained_hours, 2, 2),
            resolver.getPluralString(AllExercisesRes.plurals.feature_all_exercises_last_trained_days, 1, 1),
            resolver.getPluralString(AllExercisesRes.plurals.feature_all_exercises_last_trained_days, 2, 2),
            resolver.getPluralString(AllExercisesRes.plurals.feature_all_exercises_selected_count, 1, 1),
            resolver.getPluralString(AllExercisesRes.plurals.feature_all_exercises_selected_count, 2, 2),
            resolver.getPluralString(AllExercisesRes.plurals.feature_all_exercises_bulk_archive_success, 1, 1),
            resolver.getPluralString(AllExercisesRes.plurals.feature_all_exercises_bulk_archive_success, 2, 2),
            resolver.getPluralString(AllExercisesRes.plurals.feature_all_exercises_bulk_archive_confirm_body, 1, 1),
            resolver.getPluralString(AllExercisesRes.plurals.feature_all_exercises_bulk_archive_confirm_body, 2, 2),
            resolver.getString(
                AllExercisesRes.string.feature_all_exercises_blocked_archive_used_in_format,
                "Push, Pull",
            ),
            resolver.getPluralString(AllExercisesRes.plurals.feature_all_exercises_blocked_archive_more, 1, 1),
            resolver.getPluralString(AllExercisesRes.plurals.feature_all_exercises_blocked_archive_more, 2, 2),
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

        /** Fixed so the scene never depends on the clock. */
        const val NOW = 1_720_000_000_000L

        const val MINUTE = 60_000L
        const val HOUR = 60 * MINUTE
        const val DAY = 24 * HOUR

        const val WEIGHTED = "native-weighted"
        const val RECENT = "native-recent"
        const val HOURS = "native-hours"
        const val MINUTES = "native-minutes"
        const val FRESH = "native-fresh"
        const val TAG_PUSH = "native-tag-push"
        const val TAG_LEGS = "native-tag-legs"

        /** Ten virtual seconds: far beyond the kit's 140 ms deferral and 260 ms minimum hold. */
        const val MAX_SETTLE_STEPS = 10

        /** Settles that a transient first-frame verdict could not survive. */
        const val STABLE_SETTLE_STEPS = 3

        /** uuid, name and the drawn meta line: type, the mapper's footer, then tags. */
        val EXPECTED_ROWS = listOf(
            Triple(WEIGHTED, "Bench press", "with weight · 12 sessions · in 3 trainings · last 4d ago · Push · Chest"),
            Triple(RECENT, "Pull-up", "bodyweight · 1 session · in 1 training · last just now · Pull"),
            Triple(HOURS, "Squat", "with weight · in 2 trainings · last 2h ago"),
            Triple(MINUTES, "Plank", "bodyweight · 5 sessions · last 15m ago · Core"),
            Triple(FRESH, "Lunge", "with weight"),
        )

        /** SPEC 3.4's EN catalog in file order, as stored; each plural as its one, then its other. */
        val EXPECTED_STORED = listOf(
            "Exercises",
            "Your exercises will appear here",
            "Add the first one — after that you can put it into any training.",
            "Add exercise",
            "Create exercise",
            "with weight",
            "bodyweight",
            "Loading",
            "Couldn’t load more",
            "Retry",
            "Delete ‘%1\$s’ permanently?",
            "This exercise has no history and isn’t used. This cannot be undone.",
            "No history affected",
            "Delete",
            "Exercise deleted",
            "%1\$d session",
            "%1\$d sessions",
            "in %1\$d training",
            "in %1\$d trainings",
            "last just now",
            "last %1\$dm ago",
            "last %1\$dm ago",
            "last %1\$dh ago",
            "last %1\$dh ago",
            "last %1\$dd ago",
            "last %1\$dd ago",
            "·",
            "Close selection",
            "%1\$d selected",
            "%1\$d selected",
            "Archive",
            "%1\$d exercise archived",
            "%1\$d exercises archived",
            "Archive selected?",
            "%1\$d exercise will move to archive. Restore from Settings → Archive.",
            "%1\$d exercises will move to archive. Restore from Settings → Archive.",
            "Reversible · history preserved",
            "Some couldn’t be archived",
            "used in %1\$s",
            "+%1\$d more",
            "+%1\$d more",
            "These are still in active trainings. Remove them from those trainings first, then archive.",
            "Got it",
            "Nothing matches these tags",
            "Clear filter",
            "Nothing here, but your selection is kept",
            "Your selection stays until you leave selection mode.",
            "Couldn’t load the list",
        )

        /** Every placeholder-bearing entry of [EXPECTED_STORED], filled by the real formatter. */
        val EXPECTED_FILLED = listOf(
            "Delete ‘Bench’ permanently?",
            "1 session",
            "2 sessions",
            "in 1 training",
            "in 2 trainings",
            "last 1m ago",
            "last 2m ago",
            "last 1h ago",
            "last 2h ago",
            "last 1d ago",
            "last 2d ago",
            "1 selected",
            "2 selected",
            "1 exercise archived",
            "2 exercises archived",
            "1 exercise will move to archive. Restore from Settings → Archive.",
            "2 exercises will move to archive. Restore from Settings → Archive.",
            "used in Push, Pull",
            "+1 more",
            "+2 more",
        )
    }
}
