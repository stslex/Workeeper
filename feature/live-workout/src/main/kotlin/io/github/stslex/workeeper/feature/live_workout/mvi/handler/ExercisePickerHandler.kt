// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.live_workout.mvi.handler

import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import dagger.hilt.android.scopes.ViewModelScoped
import io.github.stslex.workeeper.core.core.resources.ResourceWrapper
import io.github.stslex.workeeper.core.exercise.personal_record.PersonalRecordDataModel
import io.github.stslex.workeeper.core.ui.plan_editor.model.ExercisePickerAction
import io.github.stslex.workeeper.core.ui.plan_editor.model.ExercisePickerUiModel
import io.github.stslex.workeeper.core.ui.plan_editor.model.ExerciseTypeUiModel
import io.github.stslex.workeeper.core.ui.plan_editor.model.ExerciseTypeUiModel.Companion.toUi
import io.github.stslex.workeeper.feature.live_workout.R
import io.github.stslex.workeeper.feature.live_workout.di.LiveWorkoutHandlerStore
import io.github.stslex.workeeper.feature.live_workout.domain.LiveWorkoutInteractor
import io.github.stslex.workeeper.feature.live_workout.domain.LiveWorkoutInteractor.ExercisePickerEntry
import io.github.stslex.workeeper.feature.live_workout.mvi.model.ErrorType
import io.github.stslex.workeeper.feature.live_workout.mvi.model.ExerciseStatusUiModel
import io.github.stslex.workeeper.feature.live_workout.mvi.model.LiveExerciseUiModel
import io.github.stslex.workeeper.feature.live_workout.mvi.store.LiveWorkoutStore.Event
import io.github.stslex.workeeper.feature.live_workout.mvi.store.LiveWorkoutStore.State
import io.github.stslex.workeeper.feature.live_workout.mvi.store.LiveWorkoutStore.State.ExercisePickerSheetState
import kotlinx.collections.immutable.ImmutableMap
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.collections.immutable.toImmutableMap
import javax.inject.Inject

/**
 * Sub-handler isolated from `ClickHandler` to keep the v2.7 decomposition concern from
 * landing in this PR. Owns every transition of [State.exercisePickerSheet] and the
 * downstream "add the exercise to the active session + lazy PR fetch" flow.
 *
 * Routed via `Action.Click.PickerAction` from the parent click handler — see
 * [ClickHandler] delegation.
 */
@ViewModelScoped
internal class ExercisePickerHandler @Inject constructor(
    private val interactor: LiveWorkoutInteractor,
    private val resourceWrapper: ResourceWrapper,
    store: LiveWorkoutHandlerStore,
) : LiveWorkoutHandlerStore by store {

    /**
     * Routed from `ClickHandler` when `Action.Click.PickerAction(action)` fires. Not a
     * [io.github.stslex.workeeper.core.ui.mvi.handler.Handler] subtype because
     * [ExercisePickerAction] lives in `core/ui/plan-editor` (kit-local) and does not
     * extend `Store.Action` — the parent click handler unwraps the Store-side wrapper
     * before invoking us.
     */
    operator fun invoke(action: ExercisePickerAction) {
        when (action) {
            is ExercisePickerAction.OnQueryChange -> processQueryChange(action.query)
            is ExercisePickerAction.OnExerciseSelect -> processExerciseSelect(action.exerciseUuid)
            is ExercisePickerAction.OnCreateNewExercise -> processCreate(action.name)
            ExercisePickerAction.OnDismiss -> processDismiss()
        }
    }

    /**
     * Opens the picker with the full library list (filtered to exclude exercises already
     * in the active session). Called from the parent `ClickHandler` when
     * `Action.Click.OnAddExerciseClick` fires.
     */
    fun open() {
        val excludedUuids = state.value.exerciseUuidsInSession()
        // Optimistic show with empty results; the search lands a tick later. Keeps the
        // sheet animation snappy.
        updateState { current ->
            current.copy(
                exercisePickerSheet = ExercisePickerSheetState.Visible(
                    query = "",
                    results = persistentListOf(),
                    noMatchHeadline = null,
                    createCtaLabel = null,
                ),
            )
        }
        loadResults(query = "", excludedUuids = excludedUuids)
    }

    private fun processQueryChange(query: String) {
        val current = state.value
        val visible = current.exercisePickerSheet as? ExercisePickerSheetState.Visible
            ?: return
        // Optimistic UI: surface the new query immediately so the keyboard input feels
        // immediate. Results are recomputed off-Main and merged when ready.
        updateState { latest ->
            latest.copy(
                exercisePickerSheet = visible.copy(query = query),
            )
        }
        loadResults(query = query, excludedUuids = current.exerciseUuidsInSession())
    }

    private fun processExerciseSelect(exerciseUuid: String) {
        val current = state.value
        if (!current.canAddExercise) return
        val visible = current.exercisePickerSheet as? ExercisePickerSheetState.Visible
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
                if (result == null) {
                    updateState { it.copy(isAddExerciseInFlight = false) }
                    sendError(ErrorType.CreateInlineExerciseFailed)
                    return@launch
                }
                // Inline-created (`is_adhoc = true`) exercises have no history → no PR fetch.
                addExerciseFlow(
                    picked = PickedExercise(
                        exerciseUuid = result.exerciseUuid,
                        name = result.name,
                        type = result.type.toUi(),
                        fetchPr = false,
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
        updateState { it.copy(exercisePickerSheet = ExercisePickerSheetState.Hidden) }
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
        launch(
            onError = { _ ->
                updateState {
                    it.copy(
                        isAddExerciseInFlight = false,
                        exercisePickerSheet = ExercisePickerSheetState.Hidden,
                    )
                }
                sendError(ErrorType.AddExerciseFailed)
            },
        ) {
            val performedUuid = interactor.addExerciseToActiveSession(
                sessionUuid = sessionUuid,
                trainingUuid = trainingUuid,
                exerciseUuid = picked.exerciseUuid,
            )
            // Q6 + C1 lock: snapshot fetch only for library exercises with potential
            // history. Inline-created (`is_adhoc = true`) entries have no baseline so the
            // map-plus update is skipped, which keeps the in-moment PR badge suppressed.
            val pr: PersonalRecordDataModel? = if (picked.fetchPr) {
                runCatching {
                    interactor.fetchPrSnapshotForExercise(
                        exerciseUuid = picked.exerciseUuid,
                        type = picked.type.toData(),
                    )
                }.getOrNull()
            } else {
                null
            }
            updateState { latest ->
                val nextExercises = (
                    latest.exercises + buildPickedExerciseUi(
                        picked = picked,
                        performedExerciseUuid = performedUuid,
                        position = latest.exercises.size,
                    )
                    ).toImmutableList()
                latest.copy(
                    exercises = nextExercises,
                    isAddExerciseInFlight = false,
                    exercisePickerSheet = ExercisePickerSheetState.Hidden,
                    preSessionPrSnapshot = latest.preSessionPrSnapshot.mergePr(
                        exerciseUuid = picked.exerciseUuid,
                        type = picked.type,
                        pr = pr,
                    ),
                )
            }
            sendEvent(Event.HapticClick(HapticFeedbackType.ContextClick))
        }
    }

    private fun buildPickedExerciseUi(
        picked: PickedExercise,
        performedExerciseUuid: String,
        position: Int,
    ): LiveExerciseUiModel = LiveExerciseUiModel(
        performedExerciseUuid = performedExerciseUuid,
        exerciseUuid = picked.exerciseUuid,
        exerciseName = picked.name,
        exerciseType = picked.type,
        position = position,
        // Newly added exercises start as PENDING; the auto-default in the next status
        // recompute pass will promote one to CURRENT if no explicit active set exists.
        status = ExerciseStatusUiModel.PENDING,
        statusLabel = "",
        planSets = persistentListOf(),
        performedSets = persistentListOf(),
    )

    private fun loadResults(query: String, excludedUuids: Set<String>) {
        launch {
            val rows = interactor.searchExercisesForPicker(
                query = query,
                excludedUuids = excludedUuids,
            )
            val pickerEntries = rows.toPickerUi()
            val (noMatchHeadline, createCta) = derivePickerLabels(query, pickerEntries.size)
            updateState { latest ->
                val visible = latest.exercisePickerSheet as? ExercisePickerSheetState.Visible
                    ?: return@updateState latest
                // Discard stale results when the user has typed a different query in the
                // meantime — the latest in-flight load wins.
                if (visible.query != query) return@updateState latest
                latest.copy(
                    exercisePickerSheet = visible.copy(
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

    private fun derivePickerLabels(query: String, resultCount: Int): Pair<String?, String?> {
        if (query.isBlank() || resultCount > 0) return null to null
        val headline = resourceWrapper.getString(
            R.string.feature_live_workout_picker_no_match_format,
            query,
        )
        val createCta = resourceWrapper.getString(
            R.string.feature_live_workout_picker_create_format,
            query,
        )
        return headline to createCta
    }

    private fun State.exerciseUuidsInSession(): Set<String> =
        exercises.map { it.exerciseUuid }.toSet()

    private fun ImmutableMap<String, State.PrSnapshotItem>.mergePr(
        exerciseUuid: String,
        type: ExerciseTypeUiModel,
        pr: PersonalRecordDataModel?,
    ): ImmutableMap<String, State.PrSnapshotItem> {
        if (pr == null) return this
        val item = State.PrSnapshotItem(
            weight = pr.weight,
            reps = pr.reps,
            type = type,
        )
        // Map-plus, never replace — parallel fetches converge correctly regardless of
        // resolve order (per spec section 6 concurrency contract).
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
