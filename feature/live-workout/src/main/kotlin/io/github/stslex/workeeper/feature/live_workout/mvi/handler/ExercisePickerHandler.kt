// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.live_workout.mvi.handler

import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import io.github.stslex.workeeper.core.core.resources.ResourceWrapper
import io.github.stslex.workeeper.core.ui.plan_editor.model.ExercisePickerAction
import io.github.stslex.workeeper.core.ui.plan_editor.model.ExercisePickerUiModel
import io.github.stslex.workeeper.core.ui.plan_editor.model.ExerciseTypeUiModel
import io.github.stslex.workeeper.core.ui.plan_editor.model.PlanSetUiModel
import io.github.stslex.workeeper.feature.live_workout.R
import io.github.stslex.workeeper.feature.live_workout.di.LiveWorkoutHandlerStore
import io.github.stslex.workeeper.feature.live_workout.di.LiveWorkoutScope
import io.github.stslex.workeeper.feature.live_workout.domain.LiveWorkoutInteractor
import io.github.stslex.workeeper.feature.live_workout.domain.model.ExercisePickerEntry
import io.github.stslex.workeeper.feature.live_workout.domain.model.PersonalRecordDomain
import io.github.stslex.workeeper.feature.live_workout.mvi.handler.PendingUndoOps.pushUndo
import io.github.stslex.workeeper.feature.live_workout.mvi.mapper.LiveWorkoutMapper.toUi
import io.github.stslex.workeeper.feature.live_workout.mvi.mapper.StateStatusMapper
import io.github.stslex.workeeper.feature.live_workout.mvi.model.ErrorType
import io.github.stslex.workeeper.feature.live_workout.mvi.model.ExerciseStatusUiModel
import io.github.stslex.workeeper.feature.live_workout.mvi.model.LiveExerciseUiModel
import io.github.stslex.workeeper.feature.live_workout.mvi.store.BottomSheetState
import io.github.stslex.workeeper.feature.live_workout.mvi.store.LiveWorkoutStore.Event
import io.github.stslex.workeeper.feature.live_workout.mvi.store.LiveWorkoutStore.State
import io.github.stslex.workeeper.feature.live_workout.mvi.store.PendingUndo
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.ImmutableMap
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.collections.immutable.toImmutableMap
import kotlinx.collections.immutable.toImmutableSet

/** Owns the picker sheet's transitions and the add-to-session + lazy PR-fetch flow. */
@SingleIn(LiveWorkoutScope::class)
internal class ExercisePickerHandler @Inject constructor(
    private val interactor: LiveWorkoutInteractor,
    private val resourceWrapper: ResourceWrapper,
    private val statusMapper: StateStatusMapper,
    store: LiveWorkoutHandlerStore,
) : LiveWorkoutHandlerStore by store {

    /** Routed from `ClickHandler`; [ExercisePickerAction] is kit-local, not a `Store.Action`. */
    operator fun invoke(action: ExercisePickerAction) {
        when (action) {
            is ExercisePickerAction.OnQueryChange -> processQueryChange(action.query)
            is ExercisePickerAction.OnExerciseSelect -> processExerciseSelect(action.exerciseUuid)
            is ExercisePickerAction.OnCreateNewExercise -> processCreate(action.name)
            ExercisePickerAction.OnDismiss -> processDismiss()
        }
    }

    /** Opens the picker with the library list, minus exercises already in the session. */
    fun open() {
        val current = state.value
        val excludedUuids = current.exerciseUuidsInSession()
        val excludedNames = current.exerciseNamesInSession()
        // Optimistic show with empty results; the search lands a tick later.
        updateState { current ->
            current.copy(
                bottomSheetState = BottomSheetState.ExercisePicker(
                    query = "",
                    results = persistentListOf(),
                    noMatchHeadline = null,
                    createCtaLabel = null,

                ),
            )
        }
        loadResults(query = "", excludedUuids = excludedUuids, excludedNames = excludedNames)
    }

    private fun processQueryChange(query: String) {
        val current = state.value
        val visible = current.bottomSheetState as? BottomSheetState.ExercisePicker
            ?: return

        // Optimistic UI: surface the query now; results are recomputed off-Main and merged.
        updateState { latest ->
            latest.copy(
                bottomSheetState = visible.copy(query = query),
            )
        }
        loadResults(
            query = query,
            excludedUuids = current.exerciseUuidsInSession(),
            excludedNames = current.exerciseNamesInSession(),
        )
    }

    private fun processExerciseSelect(exerciseUuid: String) {
        val current = state.value
        if (!current.canAddExercise) return
        val visible = current.bottomSheetState as? BottomSheetState.ExercisePicker
            ?: return
        val picked = visible.results.firstOrNull { it.uuid == exerciseUuid } ?: return
        sendEvent(Event.HapticImpact(HapticFeedbackType.Confirm))
        addExerciseFlow(
            picked = PickedExercise(
                exerciseUuid = picked.uuid,
                name = picked.name,
                type = picked.type,
                fetchPr = true,
            ),
        )
    }

    private fun processCreate(name: String) {
        val current = state.value
        if (!current.canAddExercise) return
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return
        sendEvent(Event.HapticImpact(HapticFeedbackType.Confirm))
        updateState { it.copy(isAddExerciseInFlight = true) }
        launch(
            onSuccess = { result ->
                // `reusedExisting = true` means the name collided with an existing row, which
                // may have history; a fresh inline row has none, so the fetch is skipped.
                addExerciseFlow(
                    picked = PickedExercise(
                        exerciseUuid = result.exerciseUuid,
                        name = result.name,
                        type = result.type.toUi(),
                        fetchPr = result.reusedExisting,
                    ),
                    inFlightAlreadySet = true,
                )
            },
            onError = { _ ->
                updateState { it.copy(isAddExerciseInFlight = false) }
                sendError(ErrorType.CreateInlineExerciseFailed)
            },
        ) {
            interactor.createInlineAdhocExercise(trimmed)
        }
    }

    private fun processDismiss() {
        sendEvent(Event.HapticClick(HapticFeedbackType.ContextClick))
        updateState { it.copy(bottomSheetState = BottomSheetState.Hidden) }
    }

    private fun addExerciseFlow(
        picked: PickedExercise,
        inFlightAlreadySet: Boolean = false,
    ) {
        val current = state.value
        val sessionUuid = current.sessionUuid ?: return
        val trainingUuid = current.trainingUuid ?: return
        if (!inFlightAlreadySet) {
            updateState { it.copy(isAddExerciseInFlight = true) }
        }
        val prior = current
        launch(
            onError = { _ ->
                updateState {
                    it.copy(
                        isAddExerciseInFlight = false,
                        bottomSheetState = BottomSheetState.Hidden,
                    )
                }
                sendError(ErrorType.AddExerciseFailed)
            },
        ) {
            val addResult = interactor.addExerciseToActiveSession(
                sessionUuid = sessionUuid,
                trainingUuid = trainingUuid,
                exerciseUuid = picked.exerciseUuid,
            )
            // Snapshot fetch only for exercises with potential history; inline rows have none.
            val pr: PersonalRecordDomain? = if (picked.fetchPr) {
                runCatching {
                    interactor.fetchPrSnapshotForExercise(exerciseUuid = picked.exerciseUuid)
                }.getOrNull()
            } else {
                null
            }
            // Convert outside `updateState` so the lambda stays a pure state transformation.
            val planUi: ImmutableList<PlanSetUiModel> =
                addResult.planSets?.toUi() ?: persistentListOf()
            updateState { latest ->
                val nextExercises = (
                    latest.exercises + buildPickedExerciseUi(
                        picked = picked,
                        performedExerciseUuid = addResult.performedExerciseUuid,
                        position = latest.exercises.size,
                        planSets = planUi,
                    )
                    ).toImmutableList()
                val activeNext = (
                    latest.activeExerciseUuids + addResult.performedExerciseUuid
                    ).toImmutableSet()
                val expandedNext = (
                    latest.expandedExerciseUuids + addResult.performedExerciseUuid
                    ).toImmutableSet()
                latest.copy(
                    exercises = nextExercises,
                    activeExerciseUuids = activeNext,
                    expandedExerciseUuids = expandedNext,
                    // The one-off toggle appears only on mid-session additions.
                    midSessionAddedUuids = (
                        latest.midSessionAddedUuids + addResult.performedExerciseUuid
                        ).toImmutableSet(),
                    isAddExerciseInFlight = false,
                    bottomSheetState = BottomSheetState.Hidden,
                    preSessionPrSnapshot = latest.preSessionPrSnapshot.mergePr(
                        exerciseUuid = picked.exerciseUuid,
                        type = picked.type,
                        pr = pr,
                    ),
                ).let {
                    statusMapper.recomputeStatuses(it)
                }
            }
            // «{name}» добавлено — undo removes the rows the add just wrote.
            pushUndo(
                interactor,
                PendingUndo(
                    id = PendingUndoOps.nextUndoId(),
                    message = resourceWrapper.getString(
                        R.string.feature_live_workout_toast_exercise_added,
                        picked.name.truncateForToast(),
                    ),
                    restoreExercises = prior.exercises,
                    restoreDrafts = prior.setDrafts,
                    restoreOverrides = prior.rowCountOverrides,
                    undoCompensation = PendingUndo.UndoCompensation.RemoveAddedExercise(
                        performedExerciseUuid = addResult.performedExerciseUuid,
                        exerciseUuid = picked.exerciseUuid,
                        removeFromPlan = addResult.isPlanAttached,
                    ),
                ),
            )
            sendEvent(Event.HapticClick(HapticFeedbackType.ContextClick))
        }
    }

    private fun buildPickedExerciseUi(
        picked: PickedExercise,
        performedExerciseUuid: String,
        position: Int,
        planSets: ImmutableList<PlanSetUiModel>,
    ): LiveExerciseUiModel = LiveExerciseUiModel(
        performedExerciseUuid = performedExerciseUuid,
        exerciseUuid = picked.exerciseUuid,
        exerciseName = picked.name,
        exerciseType = picked.type,
        position = position,
        // New exercises start PENDING; the next status recompute may promote one to CURRENT.
        status = ExerciseStatusUiModel.PENDING,
        statusLabel = "",
        planSets = planSets,
        performedSets = persistentListOf(),
    )

    private fun loadResults(
        query: String,
        excludedUuids: Set<String>,
        excludedNames: Set<String>,
    ) {
        launch {
            val rows = interactor.searchExercisesForPicker(
                query = query,
                excludedUuids = excludedUuids,
            )
            val pickerEntries = rows.toPickerUi()
            val (noMatchHeadline, createCta) = derivePickerLabels(
                query = query,
                results = pickerEntries,
                excludedNames = excludedNames,
            )
            updateState { latest ->
                val visible = latest.bottomSheetState as? BottomSheetState.ExercisePicker
                    ?: return@updateState latest
                // Discard stale results — the latest in-flight load wins.
                if (visible.query != query) return@updateState latest
                latest.copy(
                    bottomSheetState = visible.copy(
                        results = pickerEntries,
                        noMatchHeadline = noMatchHeadline,
                        createCtaLabel = createCta,
                    ),
                )
            }
        }
    }

    private fun List<ExercisePickerEntry>.toPickerUi() = map { entry ->
        ExercisePickerUiModel(
            uuid = entry.uuid,
            name = entry.name,
            type = entry.type.toUi(),
        )
    }.toImmutableList()

    private fun derivePickerLabels(
        query: String,
        results: List<ExercisePickerUiModel>,
        excludedNames: Set<String>,
    ): Pair<String?, String?> {
        val trimmed = query.trim()
        if (trimmed.isBlank()) return null to null
        // On an exact case-insensitive match the DB dedupes instead of inserting, so both
        // the no-match indicator and the Create CTA are suppressed.
        val exactMatchInExcluded = excludedNames.any { it.equals(trimmed, ignoreCase = true) }
        if (exactMatchInExcluded) return null to null
        val exactMatchInResults = results.any { it.name.equals(trimmed, ignoreCase = true) }
        // Hide the Create CTA only on an exact name match; partial matches still allow
        // creating the distinct typed name.
        val createCta = if (!exactMatchInResults) {
            resourceWrapper.getString(
                R.string.feature_live_workout_picker_create_format,
                trimmed,
            )
        } else {
            null
        }
        // The no-match headline applies only when the result list is genuinely empty.
        val headline = if (results.isEmpty()) {
            resourceWrapper.getString(
                R.string.feature_live_workout_picker_no_match_format,
                trimmed,
            )
        } else {
            null
        }
        return headline to createCta
    }

    private fun State.exerciseUuidsInSession(): Set<String> =
        exercises.map { it.exerciseUuid }.toSet()

    private fun State.exerciseNamesInSession(): Set<String> =
        exercises.map { it.exerciseName }.toSet()

    private fun ImmutableMap<String, State.PrSnapshotItem>.mergePr(
        exerciseUuid: String,
        type: ExerciseTypeUiModel,
        pr: PersonalRecordDomain?,
    ): ImmutableMap<String, State.PrSnapshotItem> {
        if (pr == null) return this
        val item = State.PrSnapshotItem(
            weight = pr.weight,
            reps = pr.reps,
            type = type,
        )
        // Map-plus, never replace — parallel fetches converge regardless of resolve order.
        return (this + (exerciseUuid to item)).toImmutableMap()
    }

    private fun sendError(type: ErrorType) {
        sendEvent(Event.ShowError(message = resourceWrapper.getString(type.msgRes)))
    }

    private data class PickedExercise(
        val exerciseUuid: String,
        val name: String,
        val type: ExerciseTypeUiModel,
        val fetchPr: Boolean,
    )
}
