// SPDX-License-Identifier: GPL-3.0-only
@file:OptIn(ExperimentalTestApi::class)

package io.github.stslex.workeeper.feature.past_session

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onChild
import androidx.compose.ui.test.onChildren
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.v2.runComposeUiTest
import io.github.stslex.workeeper.core.core.resources.ResourceWrapper
import io.github.stslex.workeeper.core.ui.kit.resources.core_ui_kit_action_cancel
import io.github.stslex.workeeper.core.ui.kit.resources.core_ui_kit_pr_explainer_body
import io.github.stslex.workeeper.core.ui.kit.resources.core_ui_kit_pr_explainer_confirm
import io.github.stslex.workeeper.core.ui.kit.resources.core_ui_kit_pr_explainer_title
import io.github.stslex.workeeper.core.ui.kit.resources.core_ui_kit_set_field_a11y_reps
import io.github.stslex.workeeper.core.ui.kit.resources.core_ui_kit_set_field_a11y_weight
import io.github.stslex.workeeper.core.ui.kit.strings.ComposeStringResolver
import io.github.stslex.workeeper.core.ui.kit.theme.AppTheme
import io.github.stslex.workeeper.feature.past_session.domain.model.ExerciseTypeDomain
import io.github.stslex.workeeper.feature.past_session.domain.model.PerformedExerciseDetailDomain
import io.github.stslex.workeeper.feature.past_session.domain.model.SessionDetailDomain
import io.github.stslex.workeeper.feature.past_session.domain.model.SetDomain
import io.github.stslex.workeeper.feature.past_session.domain.model.SetTypeDomain
import io.github.stslex.workeeper.feature.past_session.mvi.mapper.PastSessionUiMapper.toUi
import io.github.stslex.workeeper.feature.past_session.mvi.model.ErrorType
import io.github.stslex.workeeper.feature.past_session.mvi.model.PastSetUiModel
import io.github.stslex.workeeper.feature.past_session.mvi.store.BottomSheetState
import io.github.stslex.workeeper.feature.past_session.mvi.store.DialogState
import io.github.stslex.workeeper.feature.past_session.mvi.store.PastSessionStore.Action
import io.github.stslex.workeeper.feature.past_session.mvi.store.PastSessionStore.State
import io.github.stslex.workeeper.feature.past_session.resources.feature_past_session_action_delete
import io.github.stslex.workeeper.feature.past_session.resources.feature_past_session_action_more
import io.github.stslex.workeeper.feature.past_session.resources.feature_past_session_action_retry
import io.github.stslex.workeeper.feature.past_session.resources.feature_past_session_adhoc_label
import io.github.stslex.workeeper.feature.past_session.resources.feature_past_session_delete_dialog_body
import io.github.stslex.workeeper.feature.past_session.resources.feature_past_session_delete_dialog_title
import io.github.stslex.workeeper.feature.past_session.resources.feature_past_session_deleted_snackbar
import io.github.stslex.workeeper.feature.past_session.resources.feature_past_session_error_load_failed
import io.github.stslex.workeeper.feature.past_session.resources.feature_past_session_error_not_found
import io.github.stslex.workeeper.feature.past_session.resources.feature_past_session_exercises_count
import io.github.stslex.workeeper.feature.past_session.resources.feature_past_session_finished_at_label
import io.github.stslex.workeeper.feature.past_session.resources.feature_past_session_loading_title
import io.github.stslex.workeeper.feature.past_session.resources.feature_past_session_menu_delete_session
import io.github.stslex.workeeper.feature.past_session.resources.feature_past_session_no_sets
import io.github.stslex.workeeper.feature.past_session.resources.feature_past_session_save_failed_snackbar
import io.github.stslex.workeeper.feature.past_session.resources.feature_past_session_section_editable
import io.github.stslex.workeeper.feature.past_session.resources.feature_past_session_section_logged
import io.github.stslex.workeeper.feature.past_session.resources.feature_past_session_sets_count
import io.github.stslex.workeeper.feature.past_session.resources.feature_past_session_skipped_line
import io.github.stslex.workeeper.feature.past_session.resources.feature_past_session_tonnage_format
import io.github.stslex.workeeper.feature.past_session.resources.feature_past_session_tonnage_group_separator
import io.github.stslex.workeeper.feature.past_session.resources.feature_past_session_totals_format
import io.github.stslex.workeeper.feature.past_session.resources.feature_past_session_totals_format_with_tonnage
import io.github.stslex.workeeper.feature.past_session.ui.PastSessionScreen
import kotlinx.collections.immutable.persistentSetOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import io.github.stslex.workeeper.core.ui.kit.resources.Res as KitRes
import io.github.stslex.workeeper.feature.past_session.resources.Res as PastSessionRes

class PastSessionFeatureSceneIosTest {

    /** Every read outside composition goes through this one real resolver: no fake here. */
    private val resolver = ComposeStringResolver()

    /**
     * ResourceWrapper has no iOS implementation (S2), so the session date is stubbed — the scene's
     * only test double. It answers the five fixture dates and throws on every other call.
     */
    private val dateStub = object : ResourceWrapper {
        override fun getString(id: Int, vararg args: Any): String =
            error("the scene reads no copy through ResourceWrapper")

        override fun getQuantityString(id: Int, quantity: Int, vararg args: Any): String =
            error("the scene reads no plural through ResourceWrapper")

        override fun getAbbreviatedRelativeTime(timestamp: Long, now: Long): String =
            error("the scene reads no relative time")

        override fun formatDayMonth(timestamp: Long): String = error("the scene reads no day-month date")

        override fun formatMediumDate(timestamp: Long): String = when (timestamp) {
            90_000L -> "Apr 28"
            3_725_000L -> "May 2"
            59_000L -> "Jun 3"
            600_000L -> "Jul 4"
            1_000L -> "Aug 5"
            else -> error("Unexpected timestamp: $timestamp")
        }
    }

    @Test
    fun resourcesTotalsPhasesSheetDialogsAndActionsRenderAndDispatch() = runComposeUiTest {
        // 1. The private catalog in file order through the real resolver (D1): as stored, then every
        // placeholder-bearing entry filled.
        assertEquals(EXPECTED_STORED, storedCatalog())
        assertEquals(EXPECTED_FILLED, filledCatalog())

        // 2. Totals copy (D1, S2) through the production toUi: real resolver, stubbed date.
        val models = runBlocking {
            listOf(
                adhocSession().toUi(
                    resourceWrapper = dateStub,
                    stringResolver = resolver,
                    prSetUuids = setOf(SET_BENCH_1),
                ),
                session("Push Day", 0L, 3_725_000L, push()).toUi(dateStub, resolver),
                session("Leg Day", 0L, 59_000L, legs()).toUi(dateStub, resolver),
                session("Max Day", 0L, 600_000L, max()).toUi(dateStub, resolver),
                session("Core Day", 1_000L, 1_000L, core()).toUi(dateStub, resolver),
            )
        }
        assertEquals(
            listOf(
                listOf("Ad-hoc workout", "Apr 28", "01:30", "2 exercises · 3 sets · 770 kg"),
                listOf("Push Day", "May 2", "1:02:05", "1 exercise · 5 sets · 4,820 kg"),
                listOf("Leg Day", "Jun 3", "00:59", "1 exercise · 3 sets · 1,000 kg"),
                listOf("Max Day", "Jul 4", "10:00", "1 exercise · 1 set · 1,234,567 kg"),
                listOf("Core Day", "Aug 5", "00:00", "1 exercise · 2 sets"),
            ),
            models.map {
                listOf(it.trainingName, it.finishedAtAbsoluteLabel, it.durationLabel, it.totalsLabel)
            },
        )
        val adhoc = models.first()
        assertEquals(listOf("12", "100×5 · 90×3", ""), adhoc.exercises.map { it.setSummary })
        assertEquals(
            listOf(SET_BENCH_1),
            adhoc.exercises.flatMap { it.sets }.filter { it.isPersonalRecord }.map { it.setUuid },
        )

        val weightLabel = runBlocking { resolver.getString(KitRes.string.core_ui_kit_set_field_a11y_weight) }
        val repsLabel = runBlocking { resolver.getString(KitRes.string.core_ui_kit_set_field_a11y_reps) }
        val cancel = runBlocking { resolver.getString(KitRes.string.core_ui_kit_action_cancel) }
        val prTitle = runBlocking { resolver.getString(KitRes.string.core_ui_kit_pr_explainer_title) }
        val prBody = runBlocking { resolver.getString(KitRes.string.core_ui_kit_pr_explainer_body) }
        val prConfirm = runBlocking { resolver.getString(KitRes.string.core_ui_kit_pr_explainer_confirm) }

        val loading = State.create(sessionUuid = "scene-session")
        val state = mutableStateOf(loading)
        val actions = mutableListOf<Action>()

        setContent {
            AppTheme {
                PastSessionScreen(
                    state = state.value,
                    consume = actions::add,
                )
            }
        }

        // 3a. Loading: the fallback title, no More, and nothing below the bar.
        settleUntil(hasTestTag("PastSessionTopBar"))
        assertTitle("Past session")
        onNodeWithContentDescription("More").assertDoesNotExist()
        onNodeWithTag("PastSessionScreen", useUnmergedTree = true).onChildren().assertCountEquals(1)
        onNodeWithTag("PastSessionScreen", useUnmergedTree = true)
            .onChild()
            .assert(hasTestTag("PastSessionTopBar"))

        // 3b. Error, for each ErrorType: its headline, Retry, the fallback title, no More.
        listOf(
            ErrorType.SessionNotFound to "Session not found",
            ErrorType.LoadFailed to "Could not load session",
            ErrorType.SaveFailed to "Could not save change — try again",
        ).forEachIndexed { index, (errorType, headline) ->
            state.value = loading.copy(phase = State.Phase.Error(errorType), hasResolved = true)
            settleUntil(hasText(headline))
            onNodeWithText(headline, useUnmergedTree = true).assertIsDisplayed()
            onNodeWithText("Retry").assertIsDisplayed()
            assertTitle("Past session")
            onNodeWithContentDescription("More").assertDoesNotExist()
            if (index == 0) {
                onNodeWithText("Retry").performClick()
                assertEquals(Action.Click.OnRetryLoad, actions.last())
                onNodeWithTag("PastSessionBackButton").performClick()
                assertEquals(Action.Click.OnBackClick, actions.last())
            }
        }

        // 3c. Loaded, from the ad-hoc model with Bench open.
        val loaded = loading.copy(
            phase = State.Phase.Loaded(detail = adhoc),
            hasResolved = true,
            expandedExerciseUuids = persistentSetOf(PE_BENCH),
        )
        state.value = loaded
        settleUntil(hasText("LOGGED"))
        assertTitle("Ad-hoc workout")
        onNodeWithContentDescription("More").assertIsDisplayed()
        onNodeWithText("FINISHED · APR 28", useUnmergedTree = true).assertIsDisplayed()
        onNodeWithText("01:30", useUnmergedTree = true).assertIsDisplayed()
        onNodeWithText("2 exercises · 3 sets · 770 kg", useUnmergedTree = true).assertIsDisplayed()
        onNodeWithText("LOGGED", useUnmergedTree = true).assertIsDisplayed()
        onNodeWithText("EDITABLE", useUnmergedTree = true).assertIsDisplayed()
        listOf("Pull Up", "Bench", "Skipped Fly").forEach { name ->
            onNodeWithText(name, useUnmergedTree = true).assertIsDisplayed()
        }
        onNodeWithText("12", useUnmergedTree = true).assertIsDisplayed()
        onNodeWithText("skipped", useUnmergedTree = true).assertIsDisplayed()
        val weights = onAllNodesWithContentDescription(weightLabel, useUnmergedTree = true)
        weights.assertCountEquals(2)
        weights[0].assertTextEquals("100")
        weights[1].assertTextEquals("90")
        val reps = onAllNodesWithContentDescription(repsLabel, useUnmergedTree = true)
        reps.assertCountEquals(2)
        reps[0].assertTextEquals("5")
        reps[1].assertTextEquals("3")
        onNodeWithText("PR").assertIsDisplayed()

        onNodeWithContentDescription("More").performClick()
        assertEquals(Action.Click.OnSessionMenuClick, actions.last())
        onNodeWithText("Pull Up").performClick()
        assertEquals(Action.Click.OnExerciseHeaderClick(PE_PULL), actions.last())
        // S5: the two text entries go through the SetText semantics action. The fields are
        // controlled, so the test-held State takes each typed value, as the Store would;
        // otherwise a field reports its old text again when focus leaves it.
        weights[0].performTextReplacement("102.5")
        assertEquals(Action.Input.OnSetWeightChange(SET_BENCH_1, "102.5"), actions.last())
        state.value = state.value.withFirstBenchSet { it.copy(weightInput = "102.5") }
        settleScene()
        reps[0].performTextReplacement("6")
        assertEquals(Action.Input.OnSetRepsChange(SET_BENCH_1, "6"), actions.last())
        state.value = state.value.withFirstBenchSet { it.copy(repsInput = "6") }
        settleScene()
        onNodeWithText("PR").performClick()
        assertEquals(Action.Click.OnPrTagClick, actions.last())

        // 3d. Loaded again with the skipped card open: it logged no sets.
        state.value = loaded.copy(expandedExerciseUuids = persistentSetOf(PE_FLY))
        settleUntil(hasText("No sets logged"))
        onNodeWithText("No sets logged", useUnmergedTree = true).assertIsDisplayed()

        // 4. The session sheet and its one item.
        state.value = loaded.copy(bottomSheetState = BottomSheetState.SessionMenu)
        settleUntil(hasTestTag("PastSessionMenu_Delete"))
        onNodeWithText("Delete session", useUnmergedTree = true).assertIsDisplayed()
        onNodeWithTag("PastSessionMenu_Delete").performClick()
        assertEquals(Action.Click.OnDeleteClick, actions.last())

        // 5. Both dialogs.
        state.value = loaded.copy(dialogState = DialogState.DeleteConfirm)
        settleUntil(hasText("Delete session?"))
        onNodeWithText("Delete session?", useUnmergedTree = true).assertIsDisplayed()
        onNodeWithText(
            "This permanently removes the session and all its sets. The training template is unchanged.",
            useUnmergedTree = true,
        ).assertIsDisplayed()
        onNodeWithText("Delete").assertIsDisplayed()
        onNodeWithText(cancel).assertIsDisplayed()
        onNodeWithText("Delete").performClick()
        assertEquals(Action.Click.OnDeleteConfirm, actions.last())
        onNodeWithText(cancel).performClick()
        assertEquals(Action.Click.OnDeleteDismiss, actions.last())

        state.value = loaded.copy(dialogState = DialogState.PrExplainer)
        settleUntil(hasText(prTitle))
        onNodeWithText(prTitle, useUnmergedTree = true).assertIsDisplayed()
        onNodeWithText(prBody, useUnmergedTree = true).assertIsDisplayed()
        onNodeWithText(prConfirm).assertIsDisplayed()
        onNodeWithText(prConfirm).performClick()
        assertEquals(Action.Click.OnPrExplainerDismiss, actions.last())

        // 6. Dispatch: the exact list, in order.
        assertEquals(
            listOf<Action>(
                Action.Click.OnRetryLoad,
                Action.Click.OnBackClick,
                Action.Click.OnSessionMenuClick,
                Action.Click.OnExerciseHeaderClick(PE_PULL),
                Action.Input.OnSetWeightChange(SET_BENCH_1, "102.5"),
                Action.Input.OnSetRepsChange(SET_BENCH_1, "6"),
                Action.Click.OnPrTagClick,
                Action.Click.OnDeleteClick,
                Action.Click.OnDeleteConfirm,
                Action.Click.OnDeleteDismiss,
                Action.Click.OnPrExplainerDismiss,
            ),
            actions,
        )
    }

    /** T1: ad-hoc, a weightless row, a weighted pair with the PR, and a skipped weighted card. */
    private fun adhocSession(): SessionDetailDomain = session(
        trainingName = "Unused ad-hoc name",
        startedAt = 0L,
        finishedAt = 90_000L,
        exercises = listOf(
            exercise(PE_PULL, "Pull Up", ExerciseTypeDomain.WEIGHTLESS, 0, listOf(set("set-pull-1", 12, null, 0))),
            exercise(
                PE_BENCH,
                "Bench",
                ExerciseTypeDomain.WEIGHTED,
                1,
                listOf(
                    set(SET_BENCH_1, 5, 100.0, 0),
                    set(SET_BENCH_2, 3, 90.0, 1, SetTypeDomain.FAILURE),
                ),
            ),
            exercise(PE_FLY, "Skipped Fly", ExerciseTypeDomain.WEIGHTED, 2, emptyList(), skipped = true),
        ),
        isAdhoc = true,
    )

    /** T2: four 10 × 100.0 and one 8 × 102.5 — 4,820 kg. */
    private fun push() = listOf(
        exercise(
            "pe-push",
            "Bench",
            ExerciseTypeDomain.WEIGHTED,
            0,
            List(4) { set("set-push-$it", 10, 100.0, it) } + set("set-push-4", 8, 102.5, 4),
        ),
    )

    /** T3: three 1 × 333.2 — 999.6 rounds across the group boundary to 1,000 kg. */
    private fun legs() = listOf(
        exercise("pe-squat", "Squat", ExerciseTypeDomain.WEIGHTED, 0, List(3) { set("set-squat-$it", 1, 333.2, it) }),
    )

    /** T4: one 1 × 1234567.0 — three groups. */
    private fun max() = listOf(
        exercise(
            "pe-deadlift",
            "Deadlift",
            ExerciseTypeDomain.WEIGHTED,
            0,
            listOf(set("set-deadlift-0", 1, 1234567.0, 0)),
        ),
    )

    /** T5: weightless with residual weights, which stay out of the tonnage. */
    private fun core() = listOf(
        exercise("pe-plank", "Plank", ExerciseTypeDomain.WEIGHTLESS, 0, List(2) { set("set-plank-$it", 15, 30.0, it) }),
    )

    private fun session(
        trainingName: String,
        startedAt: Long,
        finishedAt: Long,
        exercises: List<PerformedExerciseDetailDomain>,
        isAdhoc: Boolean = false,
    ): SessionDetailDomain = SessionDetailDomain(
        sessionUuid = "scene-$trainingName",
        trainingUuid = "scene-training",
        trainingName = trainingName,
        isAdhoc = isAdhoc,
        startedAt = startedAt,
        finishedAt = finishedAt,
        exercises = exercises,
    )

    private fun exercise(
        uuid: String,
        name: String,
        type: ExerciseTypeDomain,
        position: Int,
        sets: List<SetDomain>,
        skipped: Boolean = false,
    ): PerformedExerciseDetailDomain = PerformedExerciseDetailDomain(
        performedExerciseUuid = uuid,
        exerciseUuid = "exercise-$uuid",
        exerciseName = name,
        exerciseType = type,
        position = position,
        skipped = skipped,
        sets = sets,
    )

    private fun set(
        uuid: String,
        reps: Int,
        weight: Double?,
        position: Int,
        type: SetTypeDomain = SetTypeDomain.WORK,
    ): SetDomain = SetDomain(uuid = uuid, reps = reps, weight = weight, position = position, type = type)

    /** [SET_BENCH_1] rewritten by [update]; everything else in the Loaded phase is unchanged. */
    private fun State.withFirstBenchSet(update: (PastSetUiModel) -> PastSetUiModel): State {
        val detail = (phase as State.Phase.Loaded).detail
        return copy(
            phase = State.Phase.Loaded(
                detail = detail.copy(
                    exercises = detail.exercises.map { exercise ->
                        exercise.copy(
                            sets = exercise.sets
                                .map { set -> if (set.setUuid == SET_BENCH_1) update(set) else set }
                                .toImmutableList(),
                        )
                    }.toImmutableList(),
                ),
            ),
        )
    }

    private fun ComposeUiTest.assertTitle(title: String) {
        onNode(
            hasText(title) and hasAnyAncestor(hasTestTag("PastSessionTopBar")),
            useUnmergedTree = true,
        ).assertIsDisplayed()
    }

    /** The catalog in file order, each read with no format arguments (the template verbatim). */
    private fun storedCatalog(): List<String> = runBlocking {
        listOf(
            resolver.getString(PastSessionRes.string.feature_past_session_loading_title),
            resolver.getString(PastSessionRes.string.feature_past_session_adhoc_label),
            resolver.getString(PastSessionRes.string.feature_past_session_error_not_found),
            resolver.getString(PastSessionRes.string.feature_past_session_error_load_failed),
            resolver.getString(PastSessionRes.string.feature_past_session_action_retry),
            resolver.getString(PastSessionRes.string.feature_past_session_action_delete),
            resolver.getString(PastSessionRes.string.feature_past_session_delete_dialog_title),
            resolver.getString(PastSessionRes.string.feature_past_session_delete_dialog_body),
            resolver.getString(PastSessionRes.string.feature_past_session_deleted_snackbar),
            resolver.getString(PastSessionRes.string.feature_past_session_save_failed_snackbar),
            resolver.getString(PastSessionRes.string.feature_past_session_skipped_line),
            resolver.getString(PastSessionRes.string.feature_past_session_no_sets),
            resolver.getString(PastSessionRes.string.feature_past_session_section_logged),
            resolver.getString(PastSessionRes.string.feature_past_session_section_editable),
            resolver.getString(PastSessionRes.string.feature_past_session_finished_at_label),
            resolver.getString(PastSessionRes.string.feature_past_session_action_more),
            resolver.getString(PastSessionRes.string.feature_past_session_menu_delete_session),
            resolver.getString(PastSessionRes.string.feature_past_session_totals_format),
            resolver.getString(PastSessionRes.string.feature_past_session_totals_format_with_tonnage),
            resolver.getString(PastSessionRes.string.feature_past_session_tonnage_format),
            resolver.getString(PastSessionRes.string.feature_past_session_tonnage_group_separator),
            resolver.getPluralString(PastSessionRes.plurals.feature_past_session_exercises_count, 1),
            resolver.getPluralString(PastSessionRes.plurals.feature_past_session_exercises_count, 2),
            resolver.getPluralString(PastSessionRes.plurals.feature_past_session_sets_count, 1),
            resolver.getPluralString(PastSessionRes.plurals.feature_past_session_sets_count, 2),
        )
    }

    /** Every placeholder-bearing entry, filled: the SPEC's arguments, and the count for plurals. */
    private fun filledCatalog(): List<String> = runBlocking {
        listOf(
            resolver.getString(PastSessionRes.string.feature_past_session_finished_at_label, "Apr 28"),
            resolver.getString(PastSessionRes.string.feature_past_session_totals_format, "2 exercises", "3 sets"),
            resolver.getString(
                PastSessionRes.string.feature_past_session_totals_format_with_tonnage,
                "1 exercise",
                "5 sets",
                "4,820 kg",
            ),
            resolver.getString(PastSessionRes.string.feature_past_session_tonnage_format, "4,820"),
            resolver.getPluralString(PastSessionRes.plurals.feature_past_session_exercises_count, 1, 1),
            resolver.getPluralString(PastSessionRes.plurals.feature_past_session_exercises_count, 2, 2),
            resolver.getPluralString(PastSessionRes.plurals.feature_past_session_sets_count, 1, 1),
            resolver.getPluralString(PastSessionRes.plurals.feature_past_session_sets_count, 2, 2),
        )
    }

    private fun ComposeUiTest.settleScene() {
        mainClock.autoAdvance = false
        mainClock.advanceTimeBy(1_000)
        mainClock.autoAdvance = true
        waitForIdle()
    }

    /** Settles in bounded steps until a node matching the next state exists. */
    private fun ComposeUiTest.settleUntil(matcher: SemanticsMatcher) {
        repeat(MAX_SETTLE_STEPS) {
            settleScene()
            if (onAllNodes(matcher, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()) {
                return
            }
        }
    }

    private companion object {

        const val PE_PULL = "pe-pull"
        const val PE_BENCH = "pe-bench"
        const val PE_FLY = "pe-fly"
        const val SET_BENCH_1 = "set-bench-1"
        const val SET_BENCH_2 = "set-bench-2"

        /** Ten virtual seconds: far beyond every enter animation on this screen. */
        const val MAX_SETTLE_STEPS = 10

        /** SPEC 3.4's EN catalog in file order, as stored; each plural as its one, then its other. */
        val EXPECTED_STORED = listOf(
            "Past session",
            "Ad-hoc workout",
            "Session not found",
            "Could not load session",
            "Retry",
            "Delete",
            "Delete session?",
            "This permanently removes the session and all its sets. The training template is unchanged.",
            "Session deleted",
            "Could not save change — try again",
            "skipped",
            "No sets logged",
            "Logged",
            "editable",
            "Finished · %1\$s",
            "More",
            "Delete session",
            "%1\$s · %2\$s",
            "%1\$s · %2\$s · %3\$s",
            "%1\$s kg",
            ",",
            "%1\$d exercise",
            "%1\$d exercises",
            "%1\$d set",
            "%1\$d sets",
        )

        /** The placeholder-bearing entries of [EXPECTED_STORED], filled by the real formatter. */
        val EXPECTED_FILLED = listOf(
            "Finished · Apr 28",
            "2 exercises · 3 sets",
            "1 exercise · 5 sets · 4,820 kg",
            "4,820 kg",
            "1 exercise",
            "2 exercises",
            "1 set",
            "2 sets",
        )
    }
}
