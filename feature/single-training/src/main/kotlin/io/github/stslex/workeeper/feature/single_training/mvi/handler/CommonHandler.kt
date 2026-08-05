// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.single_training.mvi.handler

import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import io.github.stslex.workeeper.core.core.resources.ResourceWrapper
import io.github.stslex.workeeper.core.ui.kit.components.tag.AppTagItem
import io.github.stslex.workeeper.core.ui.mvi.handler.Handler
import io.github.stslex.workeeper.core.ui.plan_editor.model.PlanEditorUIMapper.formatPlanSummary
import io.github.stslex.workeeper.feature.single_training.di.SingleTrainingHandlerStore
import io.github.stslex.workeeper.feature.single_training.di.SingleTrainingScope
import io.github.stslex.workeeper.feature.single_training.domain.SingleTrainingInteractor
import io.github.stslex.workeeper.feature.single_training.domain.model.SessionDomain
import io.github.stslex.workeeper.feature.single_training.domain.model.TrainingDomain
import io.github.stslex.workeeper.feature.single_training.domain.model.TrainingExerciseDetail
import io.github.stslex.workeeper.feature.single_training.mvi.mapper.TagUiMapper.toUi
import io.github.stslex.workeeper.feature.single_training.mvi.model.HistorySessionItem
import io.github.stslex.workeeper.feature.single_training.mvi.model.TrainingExerciseItem
import io.github.stslex.workeeper.feature.single_training.mvi.store.SingleTrainingStore.Action
import io.github.stslex.workeeper.feature.single_training.mvi.store.SingleTrainingStore.State
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList

@SingleIn(SingleTrainingScope::class)
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
            // Clearing `isLoading` here is load-bearing, not tidiness. The route does not
            // compose until the load lands (§26; `SingleTrainingGraph`), so a throw that left
            // the flag latched would leave the user on a permanently empty frame with no way
            // back into the screen. `launch` defaults `onError` to `{}` (B17, B21), so this arm
            // must be written out — an empty one is the latched flag.
            onError = { updateStateImmediate { it.copy(isLoading = false) } },
        ) {
            val training = interactor.getTraining(uuid)
            val exercises = interactor.getTrainingExercises(uuid)
            val recent = interactor.getRecentSessions(uuid, HISTORY_LIMIT)
            val historyCount = interactor.countSessions(uuid)
            val canPermanentlyDelete = interactor.canPermanentlyDelete(uuid)
            LoadResult(training, exercises, recent, historyCount, canPermanentlyDelete)
        }
    }

    private fun State.applyLoaded(result: LoadResult): State {
        val training = result.training ?: return copy(isLoading = false)
        val tags = training.labels.map { name ->
            availableTags.firstOrNull { it.name.equals(name, ignoreCase = true) }
                ?: AppTagItem(uuid = name, name = name)
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
        val past = result.recentSessions.toHistoryItems()
        val baseSnapshot = State.Snapshot(
            name = training.name,
            description = training.description.orEmpty(),
            tagUuids = tags.map { it.uuid },
            exercises = exercises,
        )
        return copy(
            uuid = training.uuid,
            name = training.name,
            description = training.description.orEmpty(),
            tags = tags,
            exercises = exercises,
            pastSessions = past,
            historyCount = result.historyCount,
            originalSnapshot = baseSnapshot,
            canPermanentlyDelete = result.canPermanentlyDelete,
            isLoading = false,
        )
    }

    private fun List<SessionDomain>.toHistoryItems(): ImmutableList<HistorySessionItem> =
        mapNotNull { session ->
            val finished = session.finishedAt ?: return@mapNotNull null
            HistorySessionItem(
                sessionUuid = session.uuid,
                dateLabel = resourceWrapper.formatMediumDate(finished),
            )
        }.toImmutableList()

    private data class LoadResult(
        val training: TrainingDomain?,
        val exercises: List<TrainingExerciseDetail>,
        val recentSessions: List<SessionDomain>,
        val historyCount: Int,
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
    exercises = exercises,
)
