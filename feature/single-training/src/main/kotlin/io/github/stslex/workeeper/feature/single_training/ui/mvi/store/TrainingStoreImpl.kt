package io.github.stslex.workeeper.feature.single_training.ui.mvi.store

import androidx.annotation.VisibleForTesting
import io.github.stslex.workeeper.core.core.logger.Logger
import io.github.stslex.workeeper.core.ui.mvi.AnalyticsHolder
import io.github.stslex.workeeper.core.ui.mvi.BaseStore
import io.github.stslex.workeeper.core.ui.mvi.StoreAnalytics
import io.github.stslex.workeeper.core.ui.mvi.di.StoreDispatchers
import io.github.stslex.workeeper.feature.single_training.di.TRAINING_SCOPE_NAME
import io.github.stslex.workeeper.feature.single_training.di.TrainingHandlerStoreImpl
import io.github.stslex.workeeper.feature.single_training.ui.mvi.handler.ClickHandler
import io.github.stslex.workeeper.feature.single_training.ui.mvi.handler.CommonHandler
import io.github.stslex.workeeper.feature.single_training.ui.mvi.handler.InputHandler
import io.github.stslex.workeeper.feature.single_training.ui.mvi.handler.NavigationHandler
import io.github.stslex.workeeper.feature.single_training.ui.mvi.store.TrainingStore.Action
import io.github.stslex.workeeper.feature.single_training.ui.mvi.store.TrainingStore.Event
import io.github.stslex.workeeper.feature.single_training.ui.mvi.store.TrainingStore.State
import org.koin.android.annotation.KoinViewModel
import org.koin.core.annotation.InjectedParam
import org.koin.core.annotation.Named
import org.koin.core.annotation.Qualifier
import org.koin.core.annotation.Scope

@KoinViewModel(binds = [BaseStore::class])
@Qualifier(name = TRAINING_SCOPE_NAME)
@Scope(name = TRAINING_SCOPE_NAME)
internal class TrainingStoreImpl(
    @InjectedParam navigationHandler: NavigationHandler,
    commonHandler: CommonHandler,
    inputHandler: InputHandler,
    clickHandler: ClickHandler,
    storeDispatchers: StoreDispatchers,
    @Named(TRAINING_SCOPE_NAME) handlerStore: TrainingHandlerStoreImpl,
    analytics: StoreAnalytics<Action, Event> = AnalyticsHolder.createStore(NAME),
    override val logger: Logger = storeLogger(NAME),
) : BaseStore<State, Action, Event>(
    name = NAME,
    initialState = State.INITIAL,
    handlerCreator = { action ->
        when (action) {
            is Action.Navigation -> navigationHandler
            is Action.Common -> commonHandler
            is Action.Input -> inputHandler
            is Action.Click -> clickHandler
        }
    },
    storeEmitter = handlerStore,
    storeDispatchers = storeDispatchers,
    initialActions = listOf(Action.Common.Init(navigationHandler.uuid)),
    analytics = analytics,
    logger = logger,
) {

    companion object {

        @VisibleForTesting
        private const val NAME = "SingleTraining"
    }
}
