package io.github.stslex.workeeper.feature.exercise.ui.mvi.handler

import io.github.stslex.workeeper.core.exercise.exercise.ExerciseRepository
import io.github.stslex.workeeper.core.exercise.exercise.model.DateProperty
import io.github.stslex.workeeper.core.exercise.exercise.model.ExerciseDataModel
import io.github.stslex.workeeper.core.ui.mvi.handler.Handler
import io.github.stslex.workeeper.core.ui.navigation.Screen
import io.github.stslex.workeeper.feature.exercise.di.EXERCISE_SCOPE_NAME
import io.github.stslex.workeeper.feature.exercise.di.ExerciseHandlerStore
import io.github.stslex.workeeper.feature.exercise.ui.mvi.model.toUi
import io.github.stslex.workeeper.feature.exercise.ui.mvi.store.ExerciseStore.Action
import io.github.stslex.workeeper.feature.exercise.ui.mvi.store.ExerciseStore.State
import io.github.stslex.workeeper.feature.exercise.ui.mvi.store.ExerciseStore.State.Companion.INITIAL
import kotlinx.collections.immutable.toImmutableList
import kotlinx.collections.immutable.toPersistentSet
import org.koin.core.annotation.Named
import org.koin.core.annotation.Scope
import org.koin.core.annotation.Scoped

@Scoped(binds = [CommonHandler::class])
@Scope(name = EXERCISE_SCOPE_NAME)
internal class CommonHandler(
    private val exerciseRepository: ExerciseRepository,
    @Named(EXERCISE_SCOPE_NAME) store: ExerciseHandlerStore
) : Handler<Action.Common>, ExerciseHandlerStore by store {

    override fun invoke(action: Action.Common) {
        when (action) {
            is Action.Common.Init -> processInit(action)
        }
    }

    private fun processInit(action: Action.Common.Init) {
        setInitialData(action.data)
        processTitleSearch()
    }

    private fun setInitialData(data: Screen.Exercise.Data?) {
        if (data == null) {
            updateState { getEmptyState() }
        } else {
            exerciseRepository
                .getExercise(data.uuid)
                .launch { item ->
                    val state = item?.mapToState() ?: getEmptyState()
                    updateStateImmediate(state)
                }
        }
    }

    private fun getEmptyState(): State = INITIAL.copy(
        dateProperty = DateProperty.new(System.currentTimeMillis()),
        initialHash = INITIAL.calculateEqualsHash
    )

    private fun processTitleSearch() {
        launch(
            onSuccess = { result ->
                updateState { state ->
                    state.copy(
                        menuItems = result
                            .map { item -> item.toUi() }
                            .toPersistentSet()
                    )
                }
            }
        ) {
            exerciseRepository.searchItems(state.value.name.value)
        }
    }

    private fun ExerciseDataModel.mapToState(): State {
        val state = INITIAL.copy(
            uuid = uuid,
            name = INITIAL.name.update(name),
            sets = sets.map { it.toUi() }.toImmutableList(),
            dateProperty = DateProperty.new(timestamp),
            initialHash = 0
        )
        return state.copy(
            initialHash = state.calculateEqualsHash
        )
    }
}