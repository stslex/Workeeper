package io.github.stslex.workeeper.feature.single_training.ui.mvi.handler

import dagger.hilt.android.scopes.ViewModelScoped
import io.github.stslex.workeeper.core.ui.kit.components.text_input_field.model.MenuItem
import io.github.stslex.workeeper.core.ui.mvi.handler.Handler
import io.github.stslex.workeeper.feature.single_training.di.TrainingHandlerStore
import io.github.stslex.workeeper.feature.single_training.domain.interactor.SingleTrainingInteractor
import io.github.stslex.workeeper.feature.single_training.ui.model.TrainingDomainUiModelMapper
import io.github.stslex.workeeper.feature.single_training.ui.mvi.store.TrainingStore.Action
import kotlinx.collections.immutable.toImmutableSet
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
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

        scope.launch {
            state
                .map { it.training.name.value }
                .distinctUntilChanged()
                .collectLatest { query ->
                    logger.v { "Search for query: '$query'" }
                    delay(600L) // Debounce

                    val result = interactor.searchTrainings(query)
                        .map { training ->
                            MenuItem(
                                uuid = training.uuid,
                                text = training.name,
                                itemModel = trainingDomainUiMap(training),
                            )
                        }
                        .toImmutableSet()

                    updateStateImmediate {
                        it.copy(
                            training = it.training.copy(menuItems = result),
                        )
                    }
                }
        }
    }
}
