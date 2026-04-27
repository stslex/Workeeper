// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.single_training.mvi.handler

import dagger.hilt.android.scopes.ViewModelScoped
import io.github.stslex.workeeper.core.exercise.session.model.SessionDataModel
import io.github.stslex.workeeper.core.exercise.training.TrainingDataModel
import io.github.stslex.workeeper.core.ui.mvi.handler.Handler
import io.github.stslex.workeeper.core.ui.plan_editor.mappers.formatPlanSummary
import io.github.stslex.workeeper.core.ui.plan_editor.mappers.toUi
import io.github.stslex.workeeper.core.ui.plan_editor.model.ExerciseTypeUiModel.Companion.toUi
import io.github.stslex.workeeper.feature.single_training.di.SingleTrainingHandlerStore
import io.github.stslex.workeeper.feature.single_training.domain.SingleTrainingInteractor
import io.github.stslex.workeeper.feature.single_training.mvi.model.HistorySessionItem
import io.github.stslex.workeeper.feature.single_training.mvi.model.TagUiModel
import io.github.stslex.workeeper.feature.single_training.mvi.model.TrainingExerciseItem
import io.github.stslex.workeeper.feature.single_training.mvi.model.toUi
import io.github.stslex.workeeper.feature.single_training.mvi.store.SingleTrainingStore.Action
import io.github.stslex.workeeper.feature.single_training.mvi.store.SingleTrainingStore.State
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import javax.inject.Inject

private const val HISTORY_LIMIT = 5

@ViewModelScoped
internal class CommonHandler @Inject constructor(
    private val interactor: SingleTrainingInteractor,
    store: SingleTrainingHandlerStore,
) : Handler<Action.Common>, SingleTrainingHandlerStore by store {

    override fun invoke(action: Action.Common) {
        when (action) {
            Action.Common.Init -> processInit()
        }
    }

    private fun processInit() {
        observeTags()
        observeActiveSession()
        val uuid = state.value.uuid ?: run {
            // Create mode: no remote loading needed, just snapshot the empty form.
            updateState { current ->
                current.copy(
                    isLoading = false,
                    originalSnapshot = current.toSnapshot(),
                )
            }
            return
        }
        loadTraining(uuid)
    }

    private fun observeTags() {
        scope.launch(interactor.observeAvailableTags()) { tags ->
            updateStateImmediate { current ->
                current.copy(availableTags = tags.map { it.toUi() }.toImmutableList())
            }
        }
    }

    private fun observeActiveSession() {
        scope.launch(interactor.observeAnyActiveSession()) { session ->
            updateStateImmediate { current -> current.copy(activeSession = session) }
        }
    }

    private fun loadTraining(uuid: String) {
        launch(
            onSuccess = { result -> updateStateImmediate { current -> current.applyLoaded(result) } },
        ) {
            val training = interactor.getTraining(uuid)
            val exercises = interactor.getTrainingExercises(uuid)
            val recent = interactor.getRecentSessions(uuid, HISTORY_LIMIT)
            val canPermanentlyDelete = interactor.canPermanentlyDelete(uuid)
            LoadResult(training, exercises, recent, canPermanentlyDelete)
        }
    }

    private fun State.applyLoaded(result: LoadResult): State {
        val training = result.training ?: return copy(isLoading = false)
        val tags = training.labels.map { name ->
            availableTags.firstOrNull { it.name.equals(name, ignoreCase = true) }
                ?: TagUiModel(uuid = name, name = name)
        }.toImmutableList()
        val exercises = result.exercises
            .sortedBy { it.position }
            .mapIndexed { index, detail ->
                val planSets = detail.planSets
                    ?.map { it.toUi() }
                    ?.toImmutableList()
                TrainingExerciseItem(
                    exerciseUuid = detail.exercise.uuid,
                    exerciseName = detail.exercise.name,
                    exerciseType = detail.exercise.type.toUi(),
                    tags = detail.exercise.labels.toImmutableList(),
                    position = index,
                    planSets = planSets,
                    planSummary = planSets?.formatPlanSummary().orEmpty(),
                )
            }.toImmutableList()
        val past = result.recentSessions.toHistoryItems(training)
        val baseSnapshot = State.Snapshot(
            name = training.name,
            description = training.description.orEmpty(),
            tagUuids = tags.map { it.uuid },
            exerciseSignature = exercises.map {
                State.ExerciseSignature(
                    it.exerciseUuid,
                    it.position,
                )
            },
        )
        return copy(
            uuid = training.uuid,
            name = training.name,
            description = training.description.orEmpty(),
            tags = tags,
            exercises = exercises,
            pastSessions = past,
            originalSnapshot = baseSnapshot,
            canPermanentlyDelete = result.canPermanentlyDelete,
            isLoading = false,
        )
    }

    private fun List<SessionDataModel>.toHistoryItems(
        training: TrainingDataModel,
    ): ImmutableList<HistorySessionItem> = mapNotNull { session ->
        val finished = session.finishedAt ?: return@mapNotNull null
        HistorySessionItem(
            sessionUuid = session.uuid,
            finishedAt = finished,
            trainingName = training.name,
            exerciseCount = 0,
        )
    }.toImmutableList()

    private data class LoadResult(
        val training: TrainingDataModel?,
        val exercises: List<SingleTrainingInteractor.TrainingExerciseDetail>,
        val recentSessions: List<SessionDataModel>,
        val canPermanentlyDelete: Boolean,
    )
}

internal fun State.toSnapshot(): State.Snapshot = State.Snapshot(
    name = name,
    description = description,
    tagUuids = tags.map { it.uuid },
    exerciseSignature = exercises.map { State.ExerciseSignature(it.exerciseUuid, it.position) },
)

@Suppress("unused")
private val EMPTY_HISTORY = persistentListOf<HistorySessionItem>()
