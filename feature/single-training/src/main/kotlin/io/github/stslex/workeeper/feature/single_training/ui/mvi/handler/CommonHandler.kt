package io.github.stslex.workeeper.feature.single_training.ui.mvi.handler

import io.github.stslex.workeeper.core.exercise.training.TrainingRepository
import io.github.stslex.workeeper.core.ui.mvi.handler.Handler
import io.github.stslex.workeeper.feature.single_training.di.TRAINING_SCOPE_NAME
import io.github.stslex.workeeper.feature.single_training.di.TrainingHandlerStore
import io.github.stslex.workeeper.feature.single_training.ui.model.TrainingModelMap
import io.github.stslex.workeeper.feature.single_training.ui.mvi.store.TrainingStore.Action
import org.koin.core.annotation.Named
import org.koin.core.annotation.Scope
import org.koin.core.annotation.Scoped

@Scoped(binds = [CommonHandler::class])
@Scope(name = TRAINING_SCOPE_NAME)
internal class CommonHandler(
    private val repository: TrainingRepository,
    private val trainingModelMap: TrainingModelMap,
    @Named(TRAINING_SCOPE_NAME) store: TrainingHandlerStore
) : Handler<Action.Common>, TrainingHandlerStore by store {

    override fun invoke(action: Action.Common) {
        when (action) {
            is Action.Common.Init -> initial(action)
        }
    }

    private fun initial(action: Action.Common.Init) {
        val uuid = action.uuid ?: return
        launch(
            onSuccess = { item ->
                updateStateImmediate { it.copy(training = trainingModelMap(item)) }
            }
        ) {
            repository.getTraining(uuid)
        }
    }
}