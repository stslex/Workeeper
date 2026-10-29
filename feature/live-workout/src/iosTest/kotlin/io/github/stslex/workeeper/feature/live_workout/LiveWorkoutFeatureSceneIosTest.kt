// SPDX-License-Identifier: GPL-3.0-only
@file:OptIn(ExperimentalTestApi::class)

package io.github.stslex.workeeper.feature.live_workout

import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasScrollToNodeAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.v2.runComposeUiTest
import io.github.stslex.workeeper.core.ui.kit.resources.core_ui_kit_plan_editor_unit_reps
import io.github.stslex.workeeper.core.ui.kit.resources.core_ui_kit_set_field_a11y_reps
import io.github.stslex.workeeper.core.ui.kit.resources.core_ui_kit_set_field_a11y_weight
import io.github.stslex.workeeper.core.ui.kit.strings.ComposeStringResolver
import io.github.stslex.workeeper.core.ui.kit.strings.fillTemplate
import io.github.stslex.workeeper.core.ui.kit.theme.AppTheme
import io.github.stslex.workeeper.core.ui.plan_editor.model.ExercisePickerAction
import io.github.stslex.workeeper.core.ui.plan_editor.model.ExercisePickerUiModel
import io.github.stslex.workeeper.core.ui.plan_editor.model.ExerciseTypeUiModel
import io.github.stslex.workeeper.core.ui.plan_editor.model.SetTypeUiModel
import io.github.stslex.workeeper.feature.live_workout.domain.model.ExerciseTypeDomain
import io.github.stslex.workeeper.feature.live_workout.domain.model.LiveExerciseDomain
import io.github.stslex.workeeper.feature.live_workout.domain.model.PerformedExerciseDomain
import io.github.stslex.workeeper.feature.live_workout.domain.model.PlanSetDomain
import io.github.stslex.workeeper.feature.live_workout.domain.model.SessionDomain
import io.github.stslex.workeeper.feature.live_workout.domain.model.SessionSnapshotDomain
import io.github.stslex.workeeper.feature.live_workout.domain.model.SessionStateDomain
import io.github.stslex.workeeper.feature.live_workout.domain.model.SetDomain
import io.github.stslex.workeeper.feature.live_workout.domain.model.SetTypeDomain
import io.github.stslex.workeeper.feature.live_workout.mvi.handler.truncateForToast
import io.github.stslex.workeeper.feature.live_workout.mvi.handler.updateSetDraft
import io.github.stslex.workeeper.feature.live_workout.mvi.mapper.ErrorTypeMapper.messageRes
import io.github.stslex.workeeper.feature.live_workout.mvi.mapper.LiveWorkoutMapper.toFinishStats
import io.github.stslex.workeeper.feature.live_workout.mvi.mapper.LiveWorkoutMapper.toState
import io.github.stslex.workeeper.feature.live_workout.mvi.model.ErrorType
import io.github.stslex.workeeper.feature.live_workout.mvi.model.ExerciseStatusUiModel
import io.github.stslex.workeeper.feature.live_workout.mvi.model.LiveSetUiModel
import io.github.stslex.workeeper.feature.live_workout.mvi.model.LiveWorkoutCopy
import io.github.stslex.workeeper.feature.live_workout.mvi.store.BottomSheetState
import io.github.stslex.workeeper.feature.live_workout.mvi.store.DialogState
import io.github.stslex.workeeper.feature.live_workout.mvi.store.LiveWorkoutStore.Action
import io.github.stslex.workeeper.feature.live_workout.mvi.store.LiveWorkoutStore.State
import io.github.stslex.workeeper.feature.live_workout.resources.feature_live_workout_action_delete_exercise
import io.github.stslex.workeeper.feature.live_workout.resources.feature_live_workout_action_edit_plan
import io.github.stslex.workeeper.feature.live_workout.resources.feature_live_workout_action_reset_sets
import io.github.stslex.workeeper.feature.live_workout.resources.feature_live_workout_action_skip
import io.github.stslex.workeeper.feature.live_workout.resources.feature_live_workout_action_unskip
import io.github.stslex.workeeper.feature.live_workout.resources.feature_live_workout_add_exercise_cta
import io.github.stslex.workeeper.feature.live_workout.resources.feature_live_workout_back
import io.github.stslex.workeeper.feature.live_workout.resources.feature_live_workout_cancel_body
import io.github.stslex.workeeper.feature.live_workout.resources.feature_live_workout_cancel_confirm
import io.github.stslex.workeeper.feature.live_workout.resources.feature_live_workout_cancel_dismiss
import io.github.stslex.workeeper.feature.live_workout.resources.feature_live_workout_cancel_title
import io.github.stslex.workeeper.feature.live_workout.resources.feature_live_workout_collapse
import io.github.stslex.workeeper.feature.live_workout.resources.feature_live_workout_delete_plan_body_added
import io.github.stslex.workeeper.feature.live_workout.resources.feature_live_workout_delete_plan_body_planned
import io.github.stslex.workeeper.feature.live_workout.resources.feature_live_workout_delete_plan_confirm
import io.github.stslex.workeeper.feature.live_workout.resources.feature_live_workout_delete_plan_keep
import io.github.stslex.workeeper.feature.live_workout.resources.feature_live_workout_delete_plan_title
import io.github.stslex.workeeper.feature.live_workout.resources.feature_live_workout_delete_session_progress_format
import io.github.stslex.workeeper.feature.live_workout.resources.feature_live_workout_delete_session_unnamed
import io.github.stslex.workeeper.feature.live_workout.resources.feature_live_workout_delete_workout_body
import io.github.stslex.workeeper.feature.live_workout.resources.feature_live_workout_delete_workout_confirm
import io.github.stslex.workeeper.feature.live_workout.resources.feature_live_workout_delete_workout_title
import io.github.stslex.workeeper.feature.live_workout.resources.feature_live_workout_description
import io.github.stslex.workeeper.feature.live_workout.resources.feature_live_workout_empty_finish_body
import io.github.stslex.workeeper.feature.live_workout.resources.feature_live_workout_empty_finish_continue
import io.github.stslex.workeeper.feature.live_workout.resources.feature_live_workout_empty_finish_discard
import io.github.stslex.workeeper.feature.live_workout.resources.feature_live_workout_empty_finish_title
import io.github.stslex.workeeper.feature.live_workout.resources.feature_live_workout_empty_headline
import io.github.stslex.workeeper.feature.live_workout.resources.feature_live_workout_empty_supporting
import io.github.stslex.workeeper.feature.live_workout.resources.feature_live_workout_error_add_exercise_failed
import io.github.stslex.workeeper.feature.live_workout.resources.feature_live_workout_error_cancel_failed
import io.github.stslex.workeeper.feature.live_workout.resources.feature_live_workout_error_create_inline_exercise_failed
import io.github.stslex.workeeper.feature.live_workout.resources.feature_live_workout_error_discard_session_failed
import io.github.stslex.workeeper.feature.live_workout.resources.feature_live_workout_error_finish_failed
import io.github.stslex.workeeper.feature.live_workout.resources.feature_live_workout_error_finish_missing_session
import io.github.stslex.workeeper.feature.live_workout.resources.feature_live_workout_error_invalid_reps
import io.github.stslex.workeeper.feature.live_workout.resources.feature_live_workout_error_plan_save_failed
import io.github.stslex.workeeper.feature.live_workout.resources.feature_live_workout_error_reset_failed
import io.github.stslex.workeeper.feature.live_workout.resources.feature_live_workout_error_session_load_failed
import io.github.stslex.workeeper.feature.live_workout.resources.feature_live_workout_error_set_delete_failed
import io.github.stslex.workeeper.feature.live_workout.resources.feature_live_workout_error_set_save_failed
import io.github.stslex.workeeper.feature.live_workout.resources.feature_live_workout_error_skip_failed
import io.github.stslex.workeeper.feature.live_workout.resources.feature_live_workout_error_training_name_save_failed
import io.github.stslex.workeeper.feature.live_workout.resources.feature_live_workout_error_unknown
import io.github.stslex.workeeper.feature.live_workout.resources.feature_live_workout_expand
import io.github.stslex.workeeper.feature.live_workout.resources.feature_live_workout_finish
import io.github.stslex.workeeper.feature.live_workout.resources.feature_live_workout_finish_body
import io.github.stslex.workeeper.feature.live_workout.resources.feature_live_workout_finish_confirm
import io.github.stslex.workeeper.feature.live_workout.resources.feature_live_workout_finish_keep_going
import io.github.stslex.workeeper.feature.live_workout.resources.feature_live_workout_finish_name_label
import io.github.stslex.workeeper.feature.live_workout.resources.feature_live_workout_finish_name_required
import io.github.stslex.workeeper.feature.live_workout.resources.feature_live_workout_finish_new_pr_row_format
import io.github.stslex.workeeper.feature.live_workout.resources.feature_live_workout_finish_new_pr_title
import io.github.stslex.workeeper.feature.live_workout.resources.feature_live_workout_finish_pr_weighted_format
import io.github.stslex.workeeper.feature.live_workout.resources.feature_live_workout_finish_pr_weightless_format
import io.github.stslex.workeeper.feature.live_workout.resources.feature_live_workout_finish_stat_duration
import io.github.stslex.workeeper.feature.live_workout.resources.feature_live_workout_finish_stat_exercises
import io.github.stslex.workeeper.feature.live_workout.resources.feature_live_workout_finish_stat_exercises_format
import io.github.stslex.workeeper.feature.live_workout.resources.feature_live_workout_finish_stat_exercises_with_skipped_format
import io.github.stslex.workeeper.feature.live_workout.resources.feature_live_workout_finish_stat_sets
import io.github.stslex.workeeper.feature.live_workout.resources.feature_live_workout_finish_stat_sets_count
import io.github.stslex.workeeper.feature.live_workout.resources.feature_live_workout_finish_success
import io.github.stslex.workeeper.feature.live_workout.resources.feature_live_workout_finish_title
import io.github.stslex.workeeper.feature.live_workout.resources.feature_live_workout_finish_unfilled_discarded
import io.github.stslex.workeeper.feature.live_workout.resources.feature_live_workout_meta_format
import io.github.stslex.workeeper.feature.live_workout.resources.feature_live_workout_meta_skipped_format
import io.github.stslex.workeeper.feature.live_workout.resources.feature_live_workout_more
import io.github.stslex.workeeper.feature.live_workout.resources.feature_live_workout_one_off_badge
import io.github.stslex.workeeper.feature.live_workout.resources.feature_live_workout_one_off_row_sub
import io.github.stslex.workeeper.feature.live_workout.resources.feature_live_workout_one_off_row_title
import io.github.stslex.workeeper.feature.live_workout.resources.feature_live_workout_picker_create_format
import io.github.stslex.workeeper.feature.live_workout.resources.feature_live_workout_picker_no_match_format
import io.github.stslex.workeeper.feature.live_workout.resources.feature_live_workout_picker_search_hint
import io.github.stslex.workeeper.feature.live_workout.resources.feature_live_workout_railmeta_counts
import io.github.stslex.workeeper.feature.live_workout.resources.feature_live_workout_railmeta_detail_exercises
import io.github.stslex.workeeper.feature.live_workout.resources.feature_live_workout_railmeta_detail_overall
import io.github.stslex.workeeper.feature.live_workout.resources.feature_live_workout_railmeta_detail_sets
import io.github.stslex.workeeper.feature.live_workout.resources.feature_live_workout_railmeta_one_off_suffix
import io.github.stslex.workeeper.feature.live_workout.resources.feature_live_workout_reset_body
import io.github.stslex.workeeper.feature.live_workout.resources.feature_live_workout_reset_confirm
import io.github.stslex.workeeper.feature.live_workout.resources.feature_live_workout_reset_dismiss
import io.github.stslex.workeeper.feature.live_workout.resources.feature_live_workout_reset_title
import io.github.stslex.workeeper.feature.live_workout.resources.feature_live_workout_session_overflow_cancel
import io.github.stslex.workeeper.feature.live_workout.resources.feature_live_workout_set_type_tooltip
import io.github.stslex.workeeper.feature.live_workout.resources.feature_live_workout_setbar_add
import io.github.stslex.workeeper.feature.live_workout.resources.feature_live_workout_setbar_remove
import io.github.stslex.workeeper.feature.live_workout.resources.feature_live_workout_sheet_close
import io.github.stslex.workeeper.feature.live_workout.resources.feature_live_workout_status_no_plan
import io.github.stslex.workeeper.feature.live_workout.resources.feature_live_workout_status_skipped
import io.github.stslex.workeeper.feature.live_workout.resources.feature_live_workout_toast_exercise_added
import io.github.stslex.workeeper.feature.live_workout.resources.feature_live_workout_toast_exercise_removed
import io.github.stslex.workeeper.feature.live_workout.resources.feature_live_workout_toast_exercise_removed_from_workout
import io.github.stslex.workeeper.feature.live_workout.resources.feature_live_workout_toast_set_added
import io.github.stslex.workeeper.feature.live_workout.resources.feature_live_workout_toast_set_removed
import io.github.stslex.workeeper.feature.live_workout.resources.feature_live_workout_toast_undo
import io.github.stslex.workeeper.feature.live_workout.resources.feature_live_workout_training_name_placeholder
import io.github.stslex.workeeper.feature.live_workout.ui.LiveWorkoutScreen
import io.github.stslex.workeeper.feature.live_workout.ui.components.LiveSetRow
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentSetOf
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import io.github.stslex.workeeper.core.ui.kit.resources.Res as KitRes
import io.github.stslex.workeeper.feature.live_workout.resources.Res as LiveWorkoutRes

class LiveWorkoutFeatureSceneIosTest {

    /** Every read outside composition goes through this one real resolver: no fake here. */
    private val resolver = ComposeStringResolver()

    @Test
    fun resourcesScreenSheetsDialogsAndActionsRenderAndDispatch() = runComposeUiTest {
        // 1. The private EN catalog in file order through the real resolver, each read as stored;
        // the plurals also read filled, the count as the one format argument.
        assertEquals(EXPECTED_STORED, storedCatalog())
        assertEquals(
            listOf(
                "1 new personal record",
                "2 new personal records",
                "1 empty set will not be saved",
                "2 empty sets will not be saved",
            ),
            runBlocking {
                listOf(
                    resolver.getPluralString(LiveWorkoutRes.plurals.feature_live_workout_finish_new_pr_title, 1, 1),
                    resolver.getPluralString(LiveWorkoutRes.plurals.feature_live_workout_finish_new_pr_title, 2, 2),
                    resolver.getPluralString(
                        LiveWorkoutRes.plurals.feature_live_workout_finish_unfilled_discarded,
                        1,
                        1,
                    ),
                    resolver.getPluralString(
                        LiveWorkoutRes.plurals.feature_live_workout_finish_unfilled_discarded,
                        2,
                        2,
                    ),
                )
            },
        )

        // 2. CommonHandler.resolveCopy's 31 reads, in its order: every field is its EN value verbatim.
        val copy = runBlocking {
            LiveWorkoutCopy(
                trainingNamePlaceholder = resolver.getString(
                    LiveWorkoutRes.string.feature_live_workout_training_name_placeholder,
                ),
                statusSkipped = resolver.getString(LiveWorkoutRes.string.feature_live_workout_status_skipped),
                statusNoPlan = resolver.getString(LiveWorkoutRes.string.feature_live_workout_status_no_plan),
                repsUnit = resolver.getString(KitRes.string.core_ui_kit_plan_editor_unit_reps),
                metaTemplate = resolver.getString(LiveWorkoutRes.string.feature_live_workout_meta_format),
                metaSkippedTemplate = resolver.getString(
                    LiveWorkoutRes.string.feature_live_workout_meta_skipped_format,
                ),
                resetTitle = resolver.getString(LiveWorkoutRes.string.feature_live_workout_reset_title),
                resetBody = resolver.getString(LiveWorkoutRes.string.feature_live_workout_reset_body),
                resetConfirm = resolver.getString(LiveWorkoutRes.string.feature_live_workout_reset_confirm),
                resetDismiss = resolver.getString(LiveWorkoutRes.string.feature_live_workout_reset_dismiss),
                emptyFinishDiscard = resolver.getString(
                    LiveWorkoutRes.string.feature_live_workout_empty_finish_discard,
                ),
                emptyFinishContinue = resolver.getString(
                    LiveWorkoutRes.string.feature_live_workout_empty_finish_continue,
                ),
                cancelTitle = resolver.getString(LiveWorkoutRes.string.feature_live_workout_cancel_title),
                cancelBody = resolver.getString(LiveWorkoutRes.string.feature_live_workout_cancel_body),
                cancelConfirm = resolver.getString(LiveWorkoutRes.string.feature_live_workout_cancel_confirm),
                cancelDismiss = resolver.getString(LiveWorkoutRes.string.feature_live_workout_cancel_dismiss),
                deleteSessionUnnamed = resolver.getString(
                    LiveWorkoutRes.string.feature_live_workout_delete_session_unnamed,
                ),
                deleteSessionProgressTemplate = resolver.getString(
                    LiveWorkoutRes.string.feature_live_workout_delete_session_progress_format,
                ),
                finishNameLabel = resolver.getString(LiveWorkoutRes.string.feature_live_workout_finish_name_label),
                finishNameRequired = resolver.getString(
                    LiveWorkoutRes.string.feature_live_workout_finish_name_required,
                ),
                finishSetsCountTemplate = resolver.getString(
                    LiveWorkoutRes.string.feature_live_workout_finish_stat_sets_count,
                ),
                finishExercisesTemplate = resolver.getString(
                    LiveWorkoutRes.string.feature_live_workout_finish_stat_exercises_format,
                ),
                finishExercisesWithSkippedTemplate = resolver.getString(
                    LiveWorkoutRes.string.feature_live_workout_finish_stat_exercises_with_skipped_format,
                ),
                finishPrWeightedTemplate = resolver.getString(
                    LiveWorkoutRes.string.feature_live_workout_finish_pr_weighted_format,
                ),
                finishPrWeightlessTemplate = resolver.getString(
                    LiveWorkoutRes.string.feature_live_workout_finish_pr_weightless_format,
                ),
                toastSetAdded = resolver.getString(LiveWorkoutRes.string.feature_live_workout_toast_set_added),
                toastSetRemoved = resolver.getString(LiveWorkoutRes.string.feature_live_workout_toast_set_removed),
                toastExerciseRemovedTemplate = resolver.getString(
                    LiveWorkoutRes.string.feature_live_workout_toast_exercise_removed,
                ),
                toastExerciseRemovedFromWorkoutTemplate = resolver.getString(
                    LiveWorkoutRes.string.feature_live_workout_toast_exercise_removed_from_workout,
                ),
                errorInvalidReps = resolver.getString(
                    LiveWorkoutRes.string.feature_live_workout_error_invalid_reps,
                ),
                errorFinishFailed = resolver.getString(
                    LiveWorkoutRes.string.feature_live_workout_error_finish_failed,
                ),
            )
        }
        assertEquals(EXPECTED_COPY, copy)

        // 3. The launch reads with production's arguments, and every ErrorType's message in order.
        assertEquals(
            listOf("“Bench press” added", "Create “Curl”", "No exercises match “Curl”", "Session saved"),
            runBlocking {
                listOf(
                    resolver.getString(
                        LiveWorkoutRes.string.feature_live_workout_toast_exercise_added,
                        "Bench press".truncateForToast(),
                    ),
                    resolver.getString(LiveWorkoutRes.string.feature_live_workout_picker_create_format, "Curl"),
                    resolver.getString(LiveWorkoutRes.string.feature_live_workout_picker_no_match_format, "Curl"),
                    resolver.getString(LiveWorkoutRes.string.feature_live_workout_finish_success),
                )
            },
        )
        assertEquals(
            listOf(
                "Could not save set",
                "Could not remove set",
                "Could not reset sets",
                "Could not skip exercise",
                "Session is no longer available",
                "Could not finish session",
                "Could not cancel session",
                "Could not save plan",
                "Could not save workout name",
                "Could not add exercise",
                "Could not create exercise",
                "Could not discard workout",
                "Something went wrong",
            ),
            runBlocking { ErrorType.entries.map { type -> resolver.getString(type.messageRes()) } },
        )

        // 4. The production mapper on Native: Push Day at 12:34 into the session.
        val loaded = pushDay().toState(nowMillis = STARTED_AT + ELAPSED_MILLIS, liveCopy = copy)
        assertEquals("Push Day", loaded.trainingNameLabel)
        assertEquals("12:34", loaded.elapsedDurationLabel)
        assertEquals("0 of 2 exercises · 1 of 4 sets · 1 skipped", loaded.headerMetaLabel)
        assertEquals(
            listOf(ExerciseStatusUiModel.CURRENT, ExerciseStatusUiModel.PENDING, ExerciseStatusUiModel.SKIPPED),
            loaded.exercises.map { it.status },
        )
        assertEquals(
            listOf("100×5 · 100×5", "8 reps · 8 reps", "skipped"),
            loaded.exercises.map { it.statusLabel },
        )
        assertEquals(
            DialogState.FinishSession(
                durationMillis = ELAPSED_MILLIS,
                durationLabel = "12:34",
                exercisesSummaryLabel = "0 of 3 done · 1 skipped",
                setsLoggedLabel = "1",
                newPersonalRecords = persistentListOf(
                    DialogState.FinishSession.NewPrEntry(
                        exerciseUuid = "ex-1",
                        exerciseName = "Bench press",
                        displayLabel = "100 × 5",
                    ),
                ),
                // No visible row of the snapshot is unfilled: every plan row carries its reps.
                unfilledSetCount = 0,
                requiresName = false,
                nameDraft = "Push Day",
                nameLabel = "Workout name",
                namePlaceholder = "Untitled",
                nameError = null,
                confirmEnabled = true,
            ),
            loaded.toFinishStats(),
        )
        assertEquals(
            listOf("weight, kilograms", "repetitions"),
            runBlocking {
                listOf(
                    resolver.getString(KitRes.string.core_ui_kit_set_field_a11y_weight),
                    resolver.getString(KitRes.string.core_ui_kit_set_field_a11y_reps),
                )
            },
        )

        // 5–9. One content slot, from test-held state, switches between the production screen and
        // two set rows; the screen routes its own sheets and dialogs.
        val shown = mutableStateOf<Shown>(Shown.Screen(loaded))
        val actions = mutableListOf<Action>()
        setContent {
            AppTheme {
                when (val current = shown.value) {
                    is Shown.Screen -> LiveWorkoutScreen(state = current.state, consume = actions::add)
                    Shown.Rows -> Column {
                        LiveSetRow(
                            set = row(weight = 100.0, reps = 5),
                            isWeighted = true,
                            onWeightChange = {},
                            onRepsChange = {},
                            onTypeChange = {},
                            onMarkDone = {},
                            onUncheck = {},
                            editable = true,
                        )
                        LiveSetRow(
                            set = row(weight = null, reps = 12),
                            isWeighted = false,
                            onWeightChange = {},
                            onRepsChange = {},
                            onTypeChange = {},
                            onMarkDone = {},
                            onUncheck = {},
                            editable = true,
                        )
                    }
                }
            }
        }

        // 5. The loaded screen.
        settleUntilTag("LiveWorkoutScreen")
        onNodeWithTag("LiveWorkoutBackButton").assertContentDescriptionEquals("Back")
        onNodeWithTag("LiveWorkoutMenuButton").assertContentDescriptionEquals("More")
        onNodeWithTag("LiveWorkoutTrainingNameLabel").assertTextEquals("Push Day")
        onNodeWithTag("LiveWorkoutTimer").assertTextEquals("12:34")
        onNodeWithTag("LiveWorkoutHeaderMeta").assertTextEquals("0 of 2 exercises · 1 of 4 sets · 1 skipped")
        onNode(hasText("3 EXERCISES · 4 SETS"), useUnmergedTree = true).assertExists()
        listOf("Bench press", "Pull-up", "Plank").forEach { name ->
            reveal(hasText(name))
            onNode(hasText(name), useUnmergedTree = true).assertExists()
        }
        onNodeWithTag("LiveExerciseCardSub_$PE_BENCH", useUnmergedTree = true).assertTextEquals("100×5 · 100×5")
        onNodeWithTag("LiveExerciseCardSub_$PE_PULL", useUnmergedTree = true).assertTextEquals("8 reps · 8 reps")
        onNodeWithTag("LiveExerciseCardSub_$PE_PLANK", useUnmergedTree = true).assertTextEquals("skipped")
        onNode(hasText("ONE-OFF"), useUnmergedTree = true).assertExists()
        reveal(hasTestTag("LiveExerciseCard_AddSet_$PE_BENCH"))
        onAllNodesWithContentDescription("weight, kilograms", useUnmergedTree = true).assertCountEquals(2)
        onAllNodesWithContentDescription("repetitions", useUnmergedTree = true).assertCountEquals(2)
        onNodeWithTag("LiveSetRow_${PE_BENCH}_0_PrTag", useUnmergedTree = true).assertExists()
        onNodeWithTag("LiveSetRow_${PE_BENCH}_1_TypeChip", useUnmergedTree = true).assertExists()
        onNode(hasText("+ SET"), useUnmergedTree = true).assertExists()
        onNode(hasText("− SET"), useUnmergedTree = true).assertExists()
        reveal(hasTestTag("LiveWorkoutAddAnotherExerciseCta"))
        onNode(within("LiveWorkoutAddAnotherExerciseCta", "Add exercise"), useUnmergedTree = true).assertExists()
        onNodeWithTag("LiveWorkoutFinishButton").assertTextEquals("Finish session")

        // The screen's 16 dispatching gestures.
        onNodeWithTag("LiveWorkoutBackButton").performClick()
        onNodeWithTag("LiveWorkoutMenuButton").performClick()
        onNodeWithTag("LiveWorkoutTrainingNameLabel").performClick()
        reveal(hasTestTag("LiveExerciseCard_Menu_$PE_BENCH"))
        onNodeWithTag("LiveExerciseCard_Menu_$PE_BENCH").performClick()
        onNode(hasContentDescription("Description")).performClick()
        onNode(hasText("Bench press") and hasClickAction()).performClick()
        reveal(hasText("Pull-up"))
        onNode(hasText("Pull-up") and hasClickAction()).performClick()
        reveal(hasTestTag("LiveSetRow_${PE_BENCH}_0_Checkbox"))
        onNodeWithTag("LiveSetRow_${PE_BENCH}_0_Checkbox").performClick()
        onNodeWithTag("LiveSetRow_${PE_BENCH}_1_Checkbox").performClick()
        onNodeWithTag("LiveSetRow_${PE_BENCH}_1_TypeChip").performClick()
        // 7.11's S5: SetText goes through the semantics action; the test-held State takes each
        // typed value as the Store's InputHandler writes it.
        onAllNodesWithContentDescription("weight, kilograms")[1].performTextReplacement("102.5")
        assertEquals(Action.Input.OnSetWeightChange(PE_BENCH, 1, 102.5), actions.last())
        shown.value = Shown.Screen(loaded.updateSetDraft(PE_BENCH, 1) { it.copy(weight = 102.5) })
        settleScene()
        onAllNodesWithContentDescription("repetitions")[1].performTextReplacement("6")
        assertEquals(Action.Input.OnSetRepsChange(PE_BENCH, 1, 6), actions.last())
        shown.value = Shown.Screen(
            loaded.updateSetDraft(PE_BENCH, 1) { it.copy(weight = 102.5) }
                .updateSetDraft(PE_BENCH, 1) { it.copy(reps = 6) },
        )
        settleScene()
        reveal(hasTestTag("LiveExerciseCard_AddSet_$PE_BENCH"))
        onNodeWithTag("LiveExerciseCard_AddSet_$PE_BENCH").performClick()
        onNodeWithTag("LiveExerciseCard_RemoveSet_$PE_BENCH").performClick()
        reveal(hasTestTag("LiveWorkoutAddAnotherExerciseCta"))
        onNodeWithTag("LiveWorkoutAddAnotherExerciseCta").performClick()
        onNodeWithTag("LiveWorkoutFinishButton").performClick()

        // 6. The empty unnamed session: the placeholder name, no meta line, the empty state.
        val empty = pushDay().copy(trainingName = "", exercises = emptyList())
            .toState(nowMillis = STARTED_AT + ELAPSED_MILLIS, liveCopy = copy)
        shown.value = Shown.Screen(empty)
        settleUntil(hasText("No exercises yet"))
        onNodeWithTag("LiveWorkoutTrainingNameLabel").assertTextEquals("Untitled")
        onNodeWithTag("LiveWorkoutHeaderMeta").assertDoesNotExist()
        onNode(hasText("No exercises yet"), useUnmergedTree = true).assertIsDisplayed()
        onNode(hasText("Add your first exercise to begin logging sets."), useUnmergedTree = true).assertIsDisplayed()
        onNode(hasText("Add exercise")).performClick()

        // 7. All five sheets through the screen's own routing.
        shown.value = Shown.Screen(loaded.copy(bottomSheetState = BottomSheetState.SessionMenu))
        settleUntilTag("SessionMenu_Add")
        onNode(within("SessionMenu_Add", "Add exercise"), useUnmergedTree = true).assertIsDisplayed()
        onNode(within("SessionMenu_Cancel", "Cancel session"), useUnmergedTree = true).assertIsDisplayed()
        onNodeWithTag("SessionMenu_Add").performClick()
        onNodeWithTag("SessionMenu_Cancel").performClick()

        shown.value = Shown.Screen(loaded.copy(bottomSheetState = BottomSheetState.ExerciseMenu(PE_BENCH)))
        settleUntilTag("ExerciseMenu_EditPlan")
        onNodeWithTag("ExerciseMenu_OneOff").assertDoesNotExist()
        onNode(within("ExerciseMenu_EditPlan", "Edit plan"), useUnmergedTree = true).assertIsDisplayed()
        onNode(within("ExerciseMenu_Skip", "Skip exercise"), useUnmergedTree = true).assertIsDisplayed()
        onNode(within("ExerciseMenu_Delete", "Delete exercise"), useUnmergedTree = true).assertIsDisplayed()
        onNodeWithTag("ExerciseMenu_EditPlan").performClick()
        onNode(hasText("Reset sets")).performClick()
        onNodeWithTag("ExerciseMenu_Skip").performClick()
        onNodeWithTag("ExerciseMenu_Delete").performClick()

        shown.value = Shown.Screen(loaded.copy(bottomSheetState = BottomSheetState.ExerciseMenu(PE_PLANK)))
        settleUntilTag("ExerciseMenu_OneOff")
        onNode(within("ExerciseMenu_OneOff", "Only for today"), useUnmergedTree = true).assertIsDisplayed()
        onNode(
            within("ExerciseMenu_OneOff", "stays in this session, but won’t be added to the training plan"),
            useUnmergedTree = true,
        ).assertIsDisplayed()
        onNode(within("ExerciseMenu_Skip", "Return to session"), useUnmergedTree = true).assertIsDisplayed()
        onNodeWithTag("ExerciseMenu_OneOff").performClick()
        onNodeWithTag("ExerciseMenu_Skip").performClick()

        val deleteSheet = loaded.copy(bottomSheetState = BottomSheetState.DeleteExerciseConfirm(PE_BENCH))
        shown.value = Shown.Screen(deleteSheet)
        settleUntilTag("DeleteExercise_Keep")
        onNode(hasText("Remove from the training plan?"), useUnmergedTree = true).assertIsDisplayed()
        onNode(
            hasText(
                "“Bench press” will disappear from the training plan and future sessions. If you just don’t " +
                    "want it today — skipping is the better move.",
            ),
            useUnmergedTree = true,
        ).assertIsDisplayed()
        onNodeWithTag("DeleteExercise_Keep").assertTextEquals("Keep")
        onNodeWithTag("DeleteExercise_Confirm").assertTextEquals("Remove from plan")
        onNodeWithTag("DeleteExercise_Keep").performClick()
        onNodeWithTag("DeleteExercise_Confirm").performClick()
        shown.value = Shown.Screen(deleteSheet.copy(midSessionAddedUuids = persistentSetOf(PE_BENCH)))
        settleUntil(hasText("“Bench press” was added in this session. Its logged sets will be lost."))
        onNode(hasText("Remove from the training plan?"), useUnmergedTree = true).assertIsDisplayed()
        onNodeWithTag("DeleteExercise_Confirm").assertTextEquals("Remove from plan")
        shown.value = Shown.Screen(deleteSheet.copy(isAdhoc = true))
        settleUntil(hasText("Remove from this workout?"))
        onNode(
            hasText("“Bench press” will be removed from this workout. Its logged sets will be lost."),
            useUnmergedTree = true,
        ).assertIsDisplayed()
        onNodeWithTag("DeleteExercise_Confirm").assertTextEquals("Remove from workout")

        shown.value = Shown.Screen(loaded.copy(bottomSheetState = BottomSheetState.ExerciseDescription(PE_BENCH)))
        settleUntil(hasText("Pause at the bottom."))
        onNode(hasText("Pause at the bottom."), useUnmergedTree = true).assertIsDisplayed()
        onNode(hasText("Close")).performClick()

        shown.value = Shown.Screen(
            loaded.copy(
                bottomSheetState = BottomSheetState.ExercisePicker(
                    query = "",
                    results = persistentListOf(
                        ExercisePickerUiModel(uuid = "ex-9", name = "Biceps curl", type = ExerciseTypeUiModel.WEIGHTED),
                    ),
                    noMatchHeadline = null,
                    createCtaLabel = null,
                ),
            ),
        )
        settleUntilTag("ExercisePickerRow_ex-9")
        onNode(within("ExercisePickerRow_ex-9", "Biceps curl"), useUnmergedTree = true).assertIsDisplayed()
        onNode(hasText("Type to filter or create new"), useUnmergedTree = true).assertExists()
        onNodeWithTag("ExercisePickerCreateCta").assertDoesNotExist()
        onNodeWithTag("ExercisePickerRow_ex-9").performClick()
        shown.value = Shown.Screen(
            loaded.copy(
                bottomSheetState = BottomSheetState.ExercisePicker(
                    query = "Curl",
                    results = persistentListOf(),
                    noMatchHeadline = "No exercises match “Curl”",
                    createCtaLabel = "Create “Curl”",
                ),
            ),
        )
        settleUntilTag("ExercisePickerCreateCta")
        onNodeWithTag("ExercisePickerNoMatchHeadline", useUnmergedTree = true)
            .assertTextEquals("No exercises match “Curl”")
        onNode(within("ExercisePickerCreateCta", "Create “Curl”"), useUnmergedTree = true).assertIsDisplayed()
        onNodeWithTag("ExercisePickerCreateCta").performClick()
        onNodeWithTag("ExercisePickerQueryField").performTextReplacement("Curls")

        // 8. All five dialogs through the screen's own routing.
        shown.value = Shown.Screen(
            loaded.copy(
                dialogState = DialogState.DeleteDialog(
                    sessionName = loaded.trainingNameLabel,
                    // As the click path fills it (ClickHandler.processDeleteSessionMenuClick).
                    progressLabel = copy.deleteSessionProgressTemplate.fillTemplate(
                        loaded.doneCount,
                        loaded.totalCount,
                        loaded.setsLogged,
                    ),
                ),
            ),
        )
        settleUntil(hasText("Delete Push Day?"))
        onNode(hasText("0 of 3 exercises done · 1 sets logged"), useUnmergedTree = true).assertIsDisplayed()
        onNode(hasText("This session will be deleted. Cannot be undone."), useUnmergedTree = true).assertIsDisplayed()
        onNode(hasText("Cancel")).performClick()
        onNode(hasText("Delete")).performClick()

        val finish = loaded.toFinishStats()
        shown.value = Shown.Screen(loaded.copy(dialogState = finish))
        settleUntil(hasText("Finish session?"))
        onNode(
            hasText("Your sets will be logged and plans will update with what you actually performed."),
            useUnmergedTree = true,
        ).assertIsDisplayed()
        listOf("Duration", "Exercises", "0 of 3 done · 1 skipped", "Sets logged").forEach { text ->
            onNode(hasText(text), useUnmergedTree = true).assertIsDisplayed()
        }
        // The duration beside the screen's own timer behind the dialog.
        onAllNodes(hasText("12:34"), useUnmergedTree = true).assertCountEquals(2)
        onNode(hasText("1 new personal record"), useUnmergedTree = true).assertIsDisplayed()
        onNode(hasText("Bench press — 100 × 5"), useUnmergedTree = true).assertIsDisplayed()
        onNode(hasText("Workout name"), useUnmergedTree = true).assertDoesNotExist()
        onNode(hasText("Keep going")).performClick()
        onNode(hasText("Finish")).performClick()
        val unnamed = loaded.copy(trainingName = "").toFinishStats().copy(nameError = copy.finishNameRequired)
        shown.value = Shown.Screen(loaded.copy(dialogState = unnamed))
        settleUntil(hasText("Name is required"))
        onNode(hasText("Workout name"), useUnmergedTree = true).assertIsDisplayed()
        onNode(hasText("Name is required"), useUnmergedTree = true).assertIsDisplayed()
        onNode(hasText("Finish")).assertIsNotEnabled()
        onNode(hasContentDescription("Workout name") and hasSetTextAction()).performTextReplacement("Leg Day")

        shown.value = Shown.Screen(
            loaded.copy(
                dialogState = DialogState.ConfirmDialog.ResetSets(
                    title = copy.resetTitle,
                    body = copy.resetBody,
                    confirmLabel = copy.resetConfirm,
                    dismissLabel = copy.resetDismiss,
                    exerciseUuid = PE_BENCH,
                ),
            ),
        )
        settleUntil(hasText("Reset sets?"))
        onNode(
            hasText("All logged sets for this exercise will be deleted. This cannot be undone."),
            useUnmergedTree = true,
        ).assertIsDisplayed()
        onNode(hasText("Reset")).performClick()
        onNode(hasText("Cancel")).performClick()

        shown.value = Shown.Screen(
            loaded.copy(
                dialogState = DialogState.ConfirmDialog.CancelSession(
                    title = copy.cancelTitle,
                    body = copy.cancelBody,
                    confirmLabel = copy.cancelConfirm,
                    dismissLabel = copy.cancelDismiss,
                ),
            ),
        )
        settleUntil(hasText("Cancel session?"))
        onNode(hasText("All progress will be deleted. This cannot be undone."), useUnmergedTree = true)
            .assertIsDisplayed()
        onNode(hasText("Cancel")).performClick()
        onNode(hasText("Keep going")).performClick()

        shown.value = Shown.Screen(
            empty.copy(
                dialogState = DialogState.EmptyFinish(
                    canDiscard = true,
                    confirmLabel = copy.emptyFinishDiscard,
                    dismissLabel = copy.emptyFinishContinue,
                ),
            ),
        )
        settleUntil(hasText("Discard empty workout?"))
        onNode(
            hasText("This workout has no logged sets. It looks like you haven’t started yet."),
            useUnmergedTree = true,
        ).assertIsDisplayed()
        onNode(hasText("Discard")).performClick()
        onNode(hasText("Continue edit")).performClick()

        // 9. The retired LiveSetRowSemanticsTest's two rows: each field announces its unit.
        shown.value = Shown.Rows
        settleUntilAbsent("LiveWorkoutScreen")
        onNodeWithContentDescription("weight, kilograms").assertExists()
        onAllNodesWithContentDescription("repetitions").assertCountEquals(2)

        // 10. Dispatch: production's actions, as one exact ordered list.
        assertEquals(
            listOf(
                Action.Click.OnBackClick,
                Action.Click.OnSessionMenuClick,
                Action.Click.OnTrainingNameTap,
                Action.Click.OnExerciseMenuClick(PE_BENCH),
                Action.Click.OnShowDescription(PE_BENCH),
                Action.Click.OnExerciseHeaderClick(PE_BENCH),
                Action.Click.OnExerciseHeaderClick(PE_PULL),
                Action.Click.OnSetUncheck(PE_BENCH, 0),
                Action.Click.OnSetMarkDone(PE_BENCH, 1),
                Action.Click.OnSetTypeSelect(PE_BENCH, 1, SetTypeUiModel.WORK),
                Action.Input.OnSetWeightChange(PE_BENCH, 1, 102.5),
                Action.Input.OnSetRepsChange(PE_BENCH, 1, 6),
                Action.Click.OnAddSet(PE_BENCH),
                Action.Click.OnRemoveLastSet(PE_BENCH),
                Action.Click.OnAddExerciseClick,
                Action.Click.OnFinishClick,
                Action.Click.OnAddExerciseClick,
                Action.Click.OnAddExerciseClick,
                Action.Click.OnCancelSessionClick,
                Action.Click.OnEditPlan(PE_BENCH),
                Action.Click.OnResetSets(PE_BENCH),
                Action.Click.OnSkipExercise(PE_BENCH, skipped = true),
                Action.Click.OnDeleteExerciseClick(PE_BENCH),
                Action.Click.OnToggleOneOff(PE_PLANK),
                Action.Click.OnSkipExercise(PE_PLANK, skipped = false),
                Action.DialogClick.OnDeleteExerciseKeep,
                Action.DialogClick.OnDeleteExerciseConfirm(PE_BENCH),
                Action.Click.OnSheetDismiss,
                Action.DialogClick.PickerAction(ExercisePickerAction.OnExerciseSelect("ex-9")),
                Action.DialogClick.PickerAction(ExercisePickerAction.OnCreateNewExercise("Curl")),
                Action.DialogClick.PickerAction(ExercisePickerAction.OnQueryChange("Curls")),
                Action.DialogClick.OnDeleteSessionDismiss,
                Action.DialogClick.OnDeleteSessionConfirm,
                Action.DialogClick.OnFinishDismiss,
                Action.DialogClick.OnFinishConfirm,
                Action.DialogClick.OnFinishNameChange("Leg Day"),
                Action.DialogClick.OnResetSetsConfirm(PE_BENCH),
                Action.DialogClick.OnResetSetsDismiss,
                Action.DialogClick.OnCancelSessionConfirm,
                Action.DialogClick.OnCancelSessionDismiss,
                Action.DialogClick.OnEmptyFinishDiscard,
                Action.DialogClick.OnEmptyFinishContinue,
            ),
            actions,
        )
    }

    /** What the one content slot shows; the test holds the state and production renders it. */
    private sealed interface Shown {

        data class Screen(val state: State) : Shown

        data object Rows : Shown
    }

    /** [text] inside the production [tag]. */
    private fun within(tag: String, text: String): SemanticsMatcher =
        hasText(text) and hasAnyAncestor(hasTestTag(tag))

    /** Scrolls the screen's lazy list until a node matching [matcher] is composed. */
    private fun ComposeUiTest.reveal(matcher: SemanticsMatcher) {
        onNode(hasScrollToNodeAction()).performScrollToNode(matcher)
        settleScene()
    }

    /** One set row of the retired semantics test: work, not done. */
    private fun row(weight: Double?, reps: Int): LiveSetUiModel = LiveSetUiModel(
        position = 0,
        weight = weight,
        reps = reps,
        type = SetTypeUiModel.WORK,
        isDone = false,
    )

    private fun ComposeUiTest.settleScene() {
        mainClock.autoAdvance = false
        mainClock.advanceTimeBy(1_000)
        mainClock.autoAdvance = true
        waitForIdle()
    }

    /** Settles in bounded steps until a node matching the branch exists. */
    private fun ComposeUiTest.settleUntil(matcher: SemanticsMatcher) {
        repeat(MAX_SETTLE_STEPS) {
            settleScene()
            if (onAllNodes(matcher, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()) {
                return
            }
        }
    }

    private fun ComposeUiTest.settleUntilTag(tag: String) = settleUntil(hasTestTag(tag))

    /** Settles in bounded steps until no node carries [tag]. */
    private fun ComposeUiTest.settleUntilAbsent(tag: String) {
        repeat(MAX_SETTLE_STEPS) {
            settleScene()
            if (onAllNodes(hasTestTag(tag), useUnmergedTree = true).fetchSemanticsNodes().isEmpty()) {
                return
            }
        }
    }
    /** The catalog in file order, each read with no format argument (the template verbatim). */
    private fun storedCatalog(): List<String> = runBlocking {
        listOf(
            resolver.getString(LiveWorkoutRes.string.feature_live_workout_meta_format),
            resolver.getString(LiveWorkoutRes.string.feature_live_workout_meta_skipped_format),
            resolver.getString(LiveWorkoutRes.string.feature_live_workout_railmeta_detail_sets),
            resolver.getString(LiveWorkoutRes.string.feature_live_workout_railmeta_detail_exercises),
            resolver.getString(LiveWorkoutRes.string.feature_live_workout_railmeta_detail_overall),
            resolver.getString(LiveWorkoutRes.string.feature_live_workout_railmeta_counts),
            resolver.getString(LiveWorkoutRes.string.feature_live_workout_railmeta_one_off_suffix),
            resolver.getString(LiveWorkoutRes.string.feature_live_workout_status_skipped),
            resolver.getString(LiveWorkoutRes.string.feature_live_workout_status_no_plan),
            resolver.getString(LiveWorkoutRes.string.feature_live_workout_one_off_badge),
            resolver.getString(LiveWorkoutRes.string.feature_live_workout_expand),
            resolver.getString(LiveWorkoutRes.string.feature_live_workout_collapse),
            resolver.getString(LiveWorkoutRes.string.feature_live_workout_action_edit_plan),
            resolver.getString(LiveWorkoutRes.string.feature_live_workout_action_reset_sets),
            resolver.getString(LiveWorkoutRes.string.feature_live_workout_action_skip),
            resolver.getString(LiveWorkoutRes.string.feature_live_workout_action_unskip),
            resolver.getString(LiveWorkoutRes.string.feature_live_workout_action_delete_exercise),
            resolver.getString(LiveWorkoutRes.string.feature_live_workout_one_off_row_title),
            resolver.getString(LiveWorkoutRes.string.feature_live_workout_one_off_row_sub),
            resolver.getString(LiveWorkoutRes.string.feature_live_workout_delete_workout_title),
            resolver.getString(LiveWorkoutRes.string.feature_live_workout_delete_workout_body),
            resolver.getString(LiveWorkoutRes.string.feature_live_workout_delete_workout_confirm),
            resolver.getString(LiveWorkoutRes.string.feature_live_workout_delete_plan_title),
            resolver.getString(LiveWorkoutRes.string.feature_live_workout_delete_plan_body_added),
            resolver.getString(LiveWorkoutRes.string.feature_live_workout_delete_plan_body_planned),
            resolver.getString(LiveWorkoutRes.string.feature_live_workout_delete_plan_keep),
            resolver.getString(LiveWorkoutRes.string.feature_live_workout_delete_plan_confirm),
            resolver.getString(LiveWorkoutRes.string.feature_live_workout_sheet_close),
            resolver.getString(LiveWorkoutRes.string.feature_live_workout_toast_set_added),
            resolver.getString(LiveWorkoutRes.string.feature_live_workout_toast_set_removed),
            resolver.getString(LiveWorkoutRes.string.feature_live_workout_toast_exercise_added),
            resolver.getString(LiveWorkoutRes.string.feature_live_workout_toast_exercise_removed),
            resolver.getString(LiveWorkoutRes.string.feature_live_workout_toast_exercise_removed_from_workout),
            resolver.getString(LiveWorkoutRes.string.feature_live_workout_toast_undo),
            resolver.getString(LiveWorkoutRes.string.feature_live_workout_setbar_add),
            resolver.getString(LiveWorkoutRes.string.feature_live_workout_setbar_remove),
            resolver.getString(LiveWorkoutRes.string.feature_live_workout_set_type_tooltip),
            resolver.getString(LiveWorkoutRes.string.feature_live_workout_reset_title),
            resolver.getString(LiveWorkoutRes.string.feature_live_workout_reset_body),
            resolver.getString(LiveWorkoutRes.string.feature_live_workout_reset_confirm),
            resolver.getString(LiveWorkoutRes.string.feature_live_workout_reset_dismiss),
            resolver.getString(LiveWorkoutRes.string.feature_live_workout_finish),
            resolver.getString(LiveWorkoutRes.string.feature_live_workout_finish_title),
            resolver.getString(LiveWorkoutRes.string.feature_live_workout_finish_body),
            resolver.getString(LiveWorkoutRes.string.feature_live_workout_finish_name_label),
            resolver.getString(LiveWorkoutRes.string.feature_live_workout_finish_name_required),
            resolver.getString(LiveWorkoutRes.string.feature_live_workout_finish_stat_duration),
            resolver.getString(LiveWorkoutRes.string.feature_live_workout_finish_stat_exercises),
            resolver.getString(LiveWorkoutRes.string.feature_live_workout_finish_stat_sets),
            resolver.getString(LiveWorkoutRes.string.feature_live_workout_finish_stat_sets_count),
            resolver.getString(LiveWorkoutRes.string.feature_live_workout_finish_stat_exercises_format),
            resolver.getString(LiveWorkoutRes.string.feature_live_workout_finish_stat_exercises_with_skipped_format),
            resolver.getString(LiveWorkoutRes.string.feature_live_workout_finish_keep_going),
            resolver.getString(LiveWorkoutRes.string.feature_live_workout_finish_confirm),
            resolver.getString(LiveWorkoutRes.string.feature_live_workout_finish_success),
            resolver.getPluralString(LiveWorkoutRes.plurals.feature_live_workout_finish_new_pr_title, 1),
            resolver.getPluralString(LiveWorkoutRes.plurals.feature_live_workout_finish_new_pr_title, 2),
            resolver.getString(LiveWorkoutRes.string.feature_live_workout_finish_new_pr_row_format),
            resolver.getPluralString(LiveWorkoutRes.plurals.feature_live_workout_finish_unfilled_discarded, 1),
            resolver.getPluralString(LiveWorkoutRes.plurals.feature_live_workout_finish_unfilled_discarded, 2),
            resolver.getString(LiveWorkoutRes.string.feature_live_workout_finish_pr_weighted_format),
            resolver.getString(LiveWorkoutRes.string.feature_live_workout_finish_pr_weightless_format),
            resolver.getString(LiveWorkoutRes.string.feature_live_workout_session_overflow_cancel),
            resolver.getString(LiveWorkoutRes.string.feature_live_workout_cancel_title),
            resolver.getString(LiveWorkoutRes.string.feature_live_workout_cancel_body),
            resolver.getString(LiveWorkoutRes.string.feature_live_workout_cancel_confirm),
            resolver.getString(LiveWorkoutRes.string.feature_live_workout_cancel_dismiss),
            resolver.getString(LiveWorkoutRes.string.feature_live_workout_delete_session_unnamed),
            resolver.getString(LiveWorkoutRes.string.feature_live_workout_delete_session_progress_format),
            resolver.getString(LiveWorkoutRes.string.feature_live_workout_empty_headline),
            resolver.getString(LiveWorkoutRes.string.feature_live_workout_empty_supporting),
            resolver.getString(LiveWorkoutRes.string.feature_live_workout_training_name_placeholder),
            resolver.getString(LiveWorkoutRes.string.feature_live_workout_picker_search_hint),
            resolver.getString(LiveWorkoutRes.string.feature_live_workout_picker_no_match_format),
            resolver.getString(LiveWorkoutRes.string.feature_live_workout_picker_create_format),
            resolver.getString(LiveWorkoutRes.string.feature_live_workout_add_exercise_cta),
            resolver.getString(LiveWorkoutRes.string.feature_live_workout_empty_finish_title),
            resolver.getString(LiveWorkoutRes.string.feature_live_workout_empty_finish_body),
            resolver.getString(LiveWorkoutRes.string.feature_live_workout_empty_finish_continue),
            resolver.getString(LiveWorkoutRes.string.feature_live_workout_empty_finish_discard),
            resolver.getString(LiveWorkoutRes.string.feature_live_workout_back),
            resolver.getString(LiveWorkoutRes.string.feature_live_workout_more),
            resolver.getString(LiveWorkoutRes.string.feature_live_workout_description),
            resolver.getString(LiveWorkoutRes.string.feature_live_workout_error_invalid_reps),
            resolver.getString(LiveWorkoutRes.string.feature_live_workout_error_set_save_failed),
            resolver.getString(LiveWorkoutRes.string.feature_live_workout_error_set_delete_failed),
            resolver.getString(LiveWorkoutRes.string.feature_live_workout_error_reset_failed),
            resolver.getString(LiveWorkoutRes.string.feature_live_workout_error_skip_failed),
            resolver.getString(LiveWorkoutRes.string.feature_live_workout_error_finish_missing_session),
            resolver.getString(LiveWorkoutRes.string.feature_live_workout_error_finish_failed),
            resolver.getString(LiveWorkoutRes.string.feature_live_workout_error_cancel_failed),
            resolver.getString(LiveWorkoutRes.string.feature_live_workout_error_plan_save_failed),
            resolver.getString(LiveWorkoutRes.string.feature_live_workout_error_training_name_save_failed),
            resolver.getString(LiveWorkoutRes.string.feature_live_workout_error_add_exercise_failed),
            resolver.getString(LiveWorkoutRes.string.feature_live_workout_error_create_inline_exercise_failed),
            resolver.getString(LiveWorkoutRes.string.feature_live_workout_error_discard_session_failed),
            resolver.getString(LiveWorkoutRes.string.feature_live_workout_error_unknown),
            resolver.getString(LiveWorkoutRes.string.feature_live_workout_error_session_load_failed),
        )
    }

    private companion object {

        const val PE_BENCH = "pe-1"
        const val PE_PULL = "pe-2"
        const val PE_PLANK = "pe-3"
        const val STARTED_AT = 1_000L

        /** Ten virtual seconds: far beyond any transition the screen, its sheets and dialogs run. */
        const val MAX_SETTLE_STEPS = 10

        /** 12 minutes 34 seconds. */
        const val ELAPSED_MILLIS = 754_000L

        /**
         * Push Day: Bench press weighted with a two-set plan and its first set logged, Pull-up
         * weightless with a two-set plan, Plank skipped and one-off.
         */
        fun pushDay(): SessionSnapshotDomain = SessionSnapshotDomain(
            session = SessionDomain(
                uuid = "session-1",
                trainingUuid = "training-1",
                state = SessionStateDomain.IN_PROGRESS,
                startedAt = STARTED_AT,
                finishedAt = null,
            ),
            trainingName = "Push Day",
            isAdhoc = false,
            exercises = listOf(
                LiveExerciseDomain(
                    performed = performed(PE_BENCH, "ex-1", 0, skipped = false, name = "Bench press"),
                    exerciseType = ExerciseTypeDomain.WEIGHTED,
                    planSets = List(2) { PlanSetDomain(weight = 100.0, reps = 5, type = SetTypeDomain.WORK) },
                    performedSets = listOf(
                        SetDomain(uuid = "set-1", weight = 100.0, reps = 5, type = SetTypeDomain.WORK, position = 0),
                    ),
                    isPlanAttached = true,
                    description = "Pause at the bottom.",
                ),
                LiveExerciseDomain(
                    performed = performed(PE_PULL, "ex-2", 1, skipped = false, name = "Pull-up"),
                    exerciseType = ExerciseTypeDomain.WEIGHTLESS,
                    planSets = List(2) { PlanSetDomain(weight = null, reps = 8, type = SetTypeDomain.WORK) },
                    performedSets = emptyList(),
                    isPlanAttached = true,
                ),
                LiveExerciseDomain(
                    performed = performed(PE_PLANK, "ex-3", 2, skipped = true, name = "Plank"),
                    exerciseType = ExerciseTypeDomain.WEIGHTLESS,
                    planSets = null,
                    performedSets = emptyList(),
                    isPlanAttached = false,
                ),
            ),
            preSessionPrSnapshot = emptyMap(),
        )

        fun performed(uuid: String, exerciseUuid: String, position: Int, skipped: Boolean, name: String) =
            PerformedExerciseDomain(
                uuid = uuid,
                sessionUuid = "session-1",
                exerciseUuid = exerciseUuid,
                position = position,
                skipped = skipped,
                exerciseName = name,
            )

        /** Step 2: the copy's EN values, field by field, from the moved catalog. */
        val EXPECTED_COPY = LiveWorkoutCopy(
            trainingNamePlaceholder = "Untitled",
            statusSkipped = "skipped",
            statusNoPlan = "no plan",
            repsUnit = "reps",
            metaTemplate = "%1\$d of %2\$d exercises · %3\$d of %4\$d sets",
            metaSkippedTemplate = "%1\$s · %2\$d skipped",
            resetTitle = "Reset sets?",
            resetBody = "All logged sets for this exercise will be deleted. This cannot be undone.",
            resetConfirm = "Reset",
            resetDismiss = "Cancel",
            emptyFinishDiscard = "Discard",
            emptyFinishContinue = "Continue edit",
            cancelTitle = "Cancel session?",
            cancelBody = "All progress will be deleted. This cannot be undone.",
            cancelConfirm = "Cancel",
            cancelDismiss = "Keep going",
            deleteSessionUnnamed = "Workout",
            deleteSessionProgressTemplate = "%1\$d of %2\$d exercises done · %3\$d sets logged",
            finishNameLabel = "Workout name",
            finishNameRequired = "Name is required",
            finishSetsCountTemplate = "%1\$d",
            finishExercisesTemplate = "%1\$d of %2\$d done",
            finishExercisesWithSkippedTemplate = "%1\$d of %2\$d done · %3\$d skipped",
            finishPrWeightedTemplate = "%1\$s × %2\$d",
            finishPrWeightlessTemplate = "%1\$d reps",
            toastSetAdded = "Set added",
            toastSetRemoved = "Set removed",
            toastExerciseRemovedTemplate = "“%1\$s” removed from plan",
            toastExerciseRemovedFromWorkoutTemplate = "“%1\$s” removed from workout",
            errorInvalidReps = "Reps must be greater than 0",
            errorFinishFailed = "Could not finish session",
        )

        /** The moved EN catalog in file order, as stored; each plural as its one, then its other. */
        val EXPECTED_STORED = listOf(
            "%1\$d of %2\$d exercises · %3\$d of %4\$d sets",
            "%1\$s · %2\$d skipped",
            "detail: sets",
            "detail: exercises",
            "detail: overall",
            "%1\$d exercises · %2\$d sets",
            "%1\$s · one-off: %2\$d",
            "skipped",
            "no plan",
            "one-off",
            "Expand",
            "Collapse",
            "Edit plan",
            "Reset sets",
            "Skip exercise",
            "Return to session",
            "Delete exercise",
            "Only for today",
            "stays in this session, but won’t be added to the training plan",
            "Remove from this workout?",
            "“%1\$s” will be removed from this workout. Its logged sets will be lost.",
            "Remove from workout",
            "Remove from the training plan?",
            "“%1\$s” was added in this session. Its logged sets will be lost.",
            "“%1\$s” will disappear from the training plan and future sessions. If you just don’t want it " +
                "today — skipping is the better move.",
            "Keep",
            "Remove from plan",
            "Close",
            "Set added",
            "Set removed",
            "“%1\$s” added",
            "“%1\$s” removed from plan",
            "“%1\$s” removed from workout",
            "Undo",
            "+ set",
            "− set",
            "Tap to cycle: warmup → work → failure → drop",
            "Reset sets?",
            "All logged sets for this exercise will be deleted. This cannot be undone.",
            "Reset",
            "Cancel",
            "Finish session",
            "Finish session?",
            "Your sets will be logged and plans will update with what you actually performed.",
            "Workout name",
            "Name is required",
            "Duration",
            "Exercises",
            "Sets logged",
            "%1\$d",
            "%1\$d of %2\$d done",
            "%1\$d of %2\$d done · %3\$d skipped",
            "Keep going",
            "Finish",
            "Session saved",
            "%1\$d new personal record",
            "%1\$d new personal records",
            "%1\$s — %2\$s",
            "%1\$d empty set will not be saved",
            "%1\$d empty sets will not be saved",
            "%1\$s × %2\$d",
            "%1\$d reps",
            "Cancel session",
            "Cancel session?",
            "All progress will be deleted. This cannot be undone.",
            "Cancel",
            "Keep going",
            "Workout",
            "%1\$d of %2\$d exercises done · %3\$d sets logged",
            "No exercises yet",
            "Add your first exercise to begin logging sets.",
            "Untitled",
            "Type to filter or create new",
            "No exercises match “%1\$s”",
            "Create “%1\$s”",
            "Add exercise",
            "Discard empty workout?",
            "This workout has no logged sets. It looks like you haven’t started yet.",
            "Continue edit",
            "Discard",
            "Back",
            "More",
            "Description",
            "Reps must be greater than 0",
            "Could not save set",
            "Could not remove set",
            "Could not reset sets",
            "Could not skip exercise",
            "Session is no longer available",
            "Could not finish session",
            "Could not cancel session",
            "Could not save plan",
            "Could not save workout name",
            "Could not add exercise",
            "Could not create exercise",
            "Could not discard workout",
            "Something went wrong",
            "Could not open the workout",
        )
    }
}
