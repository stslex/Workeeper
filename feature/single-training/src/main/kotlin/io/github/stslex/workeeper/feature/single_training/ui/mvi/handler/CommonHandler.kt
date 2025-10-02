package io.github.stslex.workeeper.feature.single_training.ui.mvi.handler

import dagger.hilt.android.scopes.ViewModelScoped
import io.github.stslex.workeeper.core.ui.mvi.handler.Handler
import io.github.stslex.workeeper.feature.single_training.di.TrainingHandlerStore
import io.github.stslex.workeeper.feature.single_training.domain.interactor.SingleTrainingInteractor
import io.github.stslex.workeeper.feature.single_training.ui.model.TrainingDomainUiModelMapper
import io.github.stslex.workeeper.feature.single_training.ui.mvi.store.TrainingStore.Action
import javax.inject.Inject
import kotlin.uuid.Uuid

@ViewModelScoped
internal class CommonHandler @Inject constructor(
    private val interactor: SingleTrainingInteractor,
    private val trainingDomainUiMap: TrainingDomainUiModelMapper,
    store: TrainingHandlerStore,
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
                    training = item.let(trainingDomainUiMap::invoke),
                )
            }
        }
    }
}
