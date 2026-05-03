// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.single_training.mvi.handler

import dagger.hilt.android.scopes.ViewModelScoped
import io.github.stslex.workeeper.core.core.resources.ResourceWrapper
import io.github.stslex.workeeper.core.ui.mvi.handler.Handler
import io.github.stslex.workeeper.core.ui.plan_editor.mappers.formatPlanSummary
import io.github.stslex.workeeper.feature.single_training.di.SingleTrainingHandlerStore
import io.github.stslex.workeeper.feature.single_training.domain.SingleTrainingInteractor
import io.github.stslex.workeeper.feature.single_training.domain.model.SessionDomain
import io.github.stslex.workeeper.feature.single_training.domain.model.TrainingDomain
import io.github.stslex.workeeper.feature.single_training.domain.model.TrainingExerciseDetail
import io.github.stslex.workeeper.feature.single_training.mvi.mapper.toUi
import io.github.stslex.workeeper.feature.single_training.mvi.model.HistorySessionItem
import io.github.stslex.workeeper.feature.single_training.mvi.model.TagUiModel
import io.github.stslex.workeeper.feature.single_training.mvi.model.TrainingExerciseItem
import io.github.stslex.workeeper.feature.single_training.mvi.store.SingleTrainingStore.Action
import io.github.stslex.workeeper.feature.single_training.mvi.store.SingleTrainingStore.State
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList
import javax.inject.Inject

@ViewModelScoped
internal class CommonHandler @Inject constructor(
    private val interactor: SingleTrainingInteractor,
    private val resourceWrapper: ResourceWrapper,
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
        interactor.observeAvailableTags().launch { tags ->
            updateStateImmediate { current ->
                current.copy(availableTags = tags.map { it.toUi() }.toImmutableList())
            }
        }
    }

    private fun observeActiveSession() {
        interactor.observeAnyActiveSession().launch { session ->
            updateStateImmediate { current -> current.copy(activeSession = session) }
        }
    }

    private fun loadTraining(uuid: String) {
        launch(
            onSuccess = { result ->
                updateState { current -> current.applyLoaded(result) }
            },
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
                    tags = detail.labels.toImmutableList(),
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

    private fun List<SessionDomain>.toHistoryItems(
        training: TrainingDomain,
    ): ImmutableList<HistorySessionItem> = mapNotNull { session ->
        val finished = session.finishedAt ?: return@mapNotNull null
        HistorySessionItem(
            sessionUuid = session.uuid,
            dateLabel = resourceWrapper.formatMediumDate(finished),
            trainingName = training.name,
            exerciseCount = 0,
        )
    }.toImmutableList()

    private data class LoadResult(
        val training: TrainingDomain?,
        val exercises: List<TrainingExerciseDetail>,
        val recentSessions: List<SessionDomain>,
        val canPermanentlyDelete: Boolean,
    )

    companion object {
        private const val HISTORY_LIMIT = 5
    }
}

internal fun State.toSnapshot(): State.Snapshot = State.Snapshot(
    name = name,
    description = description,
    tagUuids = tags.map { it.uuid },
    exerciseSignature = exercises.map { State.ExerciseSignature(it.exerciseUuid, it.position) },
)
