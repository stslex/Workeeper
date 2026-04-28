// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.past_session.mvi.handler

import dagger.hilt.android.scopes.ViewModelScoped
import io.github.stslex.workeeper.core.exercise.exercise.model.SetsDataModel
import io.github.stslex.workeeper.core.ui.mvi.handler.Handler
import io.github.stslex.workeeper.feature.past_session.di.PastSessionHandlerStore
import io.github.stslex.workeeper.feature.past_session.domain.PastSessionInteractor
import io.github.stslex.workeeper.feature.past_session.mvi.mapper.toSetsDataType
import io.github.stslex.workeeper.feature.past_session.mvi.model.PastSetUiModel
import io.github.stslex.workeeper.feature.past_session.mvi.store.PastSessionStore.Action
import io.github.stslex.workeeper.feature.past_session.mvi.store.PastSessionStore.Event
import io.github.stslex.workeeper.feature.past_session.mvi.store.PastSessionStore.State
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import javax.inject.Inject

@ViewModelScoped
internal class InputHandler @Inject constructor(
    private val interactor: PastSessionInteractor,
    store: PastSessionHandlerStore,
) : Handler<Action.Input>, PastSessionHandlerStore by store {

    private val persistJobs = mutableMapOf<String, Job>()
    private val originalSets = mutableMapOf<String, PastSetUiModel>()

    override fun invoke(action: Action.Input) {
        when (action) {
            is Action.Input.OnSetWeightChange -> processWeightChange(action)
            is Action.Input.OnSetRepsChange -> processRepsChange(action)
        }
    }

    private fun processWeightChange(action: Action.Input.OnSetWeightChange) {
        cancelPersist(action.setUuid)
        val parsed = action.raw.takeIf { it.isNotBlank() }?.toDoubleOrNull()
        val isValid = action.raw.isBlank() || (parsed != null && parsed >= 0.0)
        val updatedSet = updateSetInState(action.setUuid, rememberOriginal = true) { set ->
            set.copy(weightInput = action.raw, weightError = !isValid)
        } ?: return
        if (isValid) {
            persistIfReady(updatedSet)
        }
    }

    private fun processRepsChange(action: Action.Input.OnSetRepsChange) {
        cancelPersist(action.setUuid)
        val parsed = action.raw.takeIf { it.isNotBlank() }?.toIntOrNull()
        val isValid = parsed != null && parsed > 0
        val updatedSet = updateSetInState(action.setUuid, rememberOriginal = true) { set ->
            set.copy(repsInput = action.raw, repsError = !isValid)
        } ?: return
        if (isValid) {
            persistIfReady(updatedSet)
        }
    }

    private fun updateSetInState(
        setUuid: String,
        rememberOriginal: Boolean = false,
        transform: (PastSetUiModel) -> PastSetUiModel,
    ): PastSetUiModel? {
        val current = state.value.phase as? State.Phase.Loaded ?: return null
        var resultSet: PastSetUiModel? = null
        val updatedDetail = current.detail.copy(
            exercises = current.detail.exercises.map { exercise ->
                if (exercise.sets.none { it.setUuid == setUuid }) {
                    exercise
                } else {
                    exercise.copy(
                        sets = exercise.sets.map { set ->
                            if (set.setUuid == setUuid) {
                                if (rememberOriginal) {
                                    originalSets.putIfAbsent(setUuid, set)
                                }
                                transform(set).also { resultSet = it }
                            } else {
                                set
                            }
                        }.toImmutableList(),
                    )
                }
            }.toImmutableList(),
        )
        updateState {
            it.copy(phase = State.Phase.Loaded(detail = updatedDetail))
        }
        return resultSet
    }

    private fun persistIfReady(set: PastSetUiModel) {
        val reps = set.repsInput.toIntOrNull() ?: return
        if (reps <= 0 || set.repsError) return
        val weight = set.weightInput.takeIf { it.isNotBlank() }?.toDoubleOrNull()
        if (set.weightInput.isNotBlank() && weight == null) return
        if (set.weightError) return

        val originalSet = originalSets[set.setUuid] ?: set
        var persistJob: Job? = null
        persistJob = launch(
            onError = { throwable ->
                if (throwable is CancellationException || persistJobs[set.setUuid] != persistJob) {
                    clearPersistJob(set.setUuid, persistJob)
                    return@launch
                }
                clearPersistJob(set.setUuid, persistJob)
                restoreSetInState(originalSet)
                originalSets.remove(set.setUuid)
                sendEvent(Event.SaveFailedSnackbar)
            },
            onSuccess = {
                if (persistJobs[set.setUuid] != persistJob) {
                    clearPersistJob(set.setUuid, persistJob)
                    return@launch
                }
                clearPersistJob(set.setUuid, persistJob)
                originalSets.remove(set.setUuid)
            },
        ) {
            delay(DEBOUNCE_MILLIS)
            interactor.updateSet(
                performedExerciseUuid = set.performedExerciseUuid,
                position = set.position,
                set = SetsDataModel(
                    uuid = set.setUuid,
                    reps = reps,
                    weight = weight,
                    type = set.type.toSetsDataType(),
                ),
            )
        }
        persistJobs[set.setUuid] = persistJob
    }

    private fun cancelPersist(setUuid: String) {
        persistJobs.remove(setUuid)?.cancel()
    }

    private fun clearPersistJob(setUuid: String, job: Job?) {
        if (persistJobs[setUuid] == job) {
            persistJobs.remove(setUuid)
        }
    }

    private fun restoreSetInState(set: PastSetUiModel) {
        updateSetInState(set.setUuid) { set }
    }

    private companion object {

        const val DEBOUNCE_MILLIS = 300L
    }
}
