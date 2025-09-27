package io.github.stslex.workeeper.feature.exercise.ui.mvi.handler

import io.github.stslex.workeeper.core.exercise.exercise.model.ExerciseDataModel
import io.github.stslex.workeeper.core.ui.kit.components.text_input_field.model.MenuItem
import io.github.stslex.workeeper.core.ui.kit.components.text_input_field.model.PropertyHolder
import io.github.stslex.workeeper.core.ui.kit.components.text_input_field.model.PropertyHolder.Companion.update
import io.github.stslex.workeeper.core.ui.mvi.handler.Handler
import io.github.stslex.workeeper.feature.exercise.di.EXERCISE_SCOPE_NAME
import io.github.stslex.workeeper.feature.exercise.di.ExerciseHandlerStore
import io.github.stslex.workeeper.feature.exercise.domain.ExerciseInteractor
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
    private val interactor: ExerciseInteractor,
    @Named(EXERCISE_SCOPE_NAME) store: ExerciseHandlerStore,
) : Handler<Action.Common>, ExerciseHandlerStore by store {

    override fun invoke(action: Action.Common) {
        when (action) {
            is Action.Common.Init -> processInit(action)
        }
    }

    private fun processInit(action: Action.Common.Init) {
        setInitialData(action)
        processTitleSearch()
    }

    private fun setInitialData(action: Action.Common.Init) {
        val uuid = action.uuid
        if (uuid == null) {
            updateState {
                getEmptyState(action.trainingUuid)
            }
        } else {
            launch(
                onSuccess = { item ->
                    val state = item?.mapToState() ?: getEmptyState(action.trainingUuid)
                    updateStateImmediate(state)
                },
            ) {
                interactor.getExercise(uuid)
            }
        }
    }

    private fun getEmptyState(trainingUuid: String?): State = INITIAL.copy(
        dateProperty = PropertyHolder.DateProperty.now(),
        trainingUuid = trainingUuid,
    ).let {
        it.copy(
            initialHash = it.calculateEqualsHash,
        )
    }

    private fun processTitleSearch() {
        launch(
            onSuccess = { result ->
                updateState { state ->
                    state.copy(
                        menuItems = result
                            .map { item ->
                                MenuItem(
                                    uuid = item.uuid,
                                    text = item.name,
                                    itemModel = item.toUi(),
                                )
                            }
                            .toPersistentSet(),
                    )
                }
            },
        ) {
            interactor.searchItems(state.value.name.value)
        }
    }

    private fun ExerciseDataModel.mapToState(): State {
        val state = INITIAL.copy(
            uuid = uuid,
            name = INITIAL.name.update(name),
            sets = sets.map { it.toUi() }.toImmutableList(),
            dateProperty = INITIAL.dateProperty.update(timestamp),
            trainingUuid = trainingUuid,
            initialHash = 0,
        )
        return state.copy(
            initialHash = state.calculateEqualsHash,
        )
    }
}
