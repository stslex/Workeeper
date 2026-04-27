// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.exercise.mvi.handler

import dagger.hilt.android.scopes.ViewModelScoped
import io.github.stslex.workeeper.core.exercise.exercise.model.ExerciseDataModel
import io.github.stslex.workeeper.core.exercise.exercise.model.HistoryEntry
import io.github.stslex.workeeper.core.ui.mvi.handler.Handler
import io.github.stslex.workeeper.feature.exercise.di.ExerciseHandlerStore
import io.github.stslex.workeeper.feature.exercise.domain.ExerciseInteractor
import io.github.stslex.workeeper.feature.exercise.mvi.model.TagUiModel
import io.github.stslex.workeeper.feature.exercise.mvi.model.toUi
import io.github.stslex.workeeper.feature.exercise.mvi.store.ExerciseStore.Action
import io.github.stslex.workeeper.feature.exercise.mvi.store.ExerciseStore.State
import kotlinx.collections.immutable.toImmutableList
import javax.inject.Inject

@ViewModelScoped
internal class CommonHandler @Inject constructor(
    private val interactor: ExerciseInteractor,
    store: ExerciseHandlerStore,
) : Handler<Action.Common>, ExerciseHandlerStore by store {

    override fun invoke(action: Action.Common) {
        when (action) {
            Action.Common.Init -> processInit()
        }
    }

    private fun processInit() {
        observeTags()
        val uuid = state.value.uuid ?: return
        loadExercise(uuid)
    }

    private fun observeTags() {
        scope.launch(interactor.observeAvailableTags()) { tags ->
            updateStateImmediate { current ->
                current.copy(
                    availableTags = tags.map { it.toUi() }.toImmutableList(),
                )
            }
        }
    }

    private fun loadExercise(uuid: String) {
        launch(
            onSuccess = { result -> updateStateImmediate { current -> current.applyLoaded(result) } },
        ) {
            val exercise = interactor.getExercise(uuid)
            val history = interactor.getRecentHistory(uuid)
            val canPermanentlyDelete = interactor.canPermanentlyDelete(uuid)
            LoadResult(exercise, history, canPermanentlyDelete)
        }
    }

    private fun State.applyLoaded(result: LoadResult): State {
        val exercise = result.exercise ?: return copy(isLoading = false)
        val tags = exercise.labels
            .map { name ->
                val matched = availableTags.firstOrNull { it.name.equals(name, ignoreCase = true) }
                matched ?: TagUiModel(uuid = name, name = name)
            }
            .toImmutableList()
        return copy(
            name = exercise.name,
            type = exercise.type,
            description = exercise.description.orEmpty(),
            tags = tags,
            recentHistory = result.history.map { it.toUi() }.toImmutableList(),
            isLoading = false,
            canPermanentlyDelete = result.canPermanentlyDelete,
            originalSnapshot = State.Snapshot(
                name = exercise.name,
                type = exercise.type,
                description = exercise.description.orEmpty(),
                tagUuids = tags.map { it.uuid },
            ),
        )
    }

    private data class LoadResult(
        val exercise: ExerciseDataModel?,
        val history: List<HistoryEntry>,
        val canPermanentlyDelete: Boolean,
    )
}
