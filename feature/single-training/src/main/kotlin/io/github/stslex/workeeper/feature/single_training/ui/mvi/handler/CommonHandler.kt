package io.github.stslex.workeeper.feature.single_training.ui.mvi.handler

import io.github.stslex.workeeper.core.ui.mvi.handler.Handler
import io.github.stslex.workeeper.feature.single_training.di.TRAINING_SCOPE_NAME
import io.github.stslex.workeeper.feature.single_training.di.TrainingHandlerStore
import io.github.stslex.workeeper.feature.single_training.domain.interactor.SingleTrainingInteractor
import io.github.stslex.workeeper.feature.single_training.ui.model.TrainingDomainUiModelMapper
import io.github.stslex.workeeper.feature.single_training.ui.mvi.store.TrainingStore.Action
import org.koin.core.annotation.Named
import org.koin.core.annotation.Scope
import org.koin.core.annotation.Scoped
import kotlin.uuid.Uuid

@Scoped(binds = [CommonHandler::class])
@Scope(name = TRAINING_SCOPE_NAME)
internal class CommonHandler(
    private val interactor: SingleTrainingInteractor,
    private val trainingDomainUiMap: TrainingDomainUiModelMapper,
    @Named(TRAINING_SCOPE_NAME) store: TrainingHandlerStore
) : Handler<Action.Common>, TrainingHandlerStore by store {

    override fun invoke(action: Action.Common) {
        when (action) {
            is Action.Common.Init -> initial(action)
        }
    }

    private fun initial(action: Action.Common.Init) {
        val uuid = action.uuid
            .orEmpty()
            .ifBlank { state.value.pendingForCreateUuid }
            .ifBlank {
                val uuid = Uuid.random().toString()
                updateState { it.copy(pendingForCreateUuid = uuid) }
                uuid
            }

        scope.launch(
            interactor.subscribeForTraining(uuid),
        ) { item ->
            updateStateImmediate { state ->
                state.copy(
                    training = item.let(trainingDomainUiMap::invoke)
                )
            }
        }
    }
}