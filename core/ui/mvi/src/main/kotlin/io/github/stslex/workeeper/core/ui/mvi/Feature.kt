package io.github.stslex.workeeper.core.ui.mvi

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import io.github.stslex.workeeper.core.ui.mvi.processor.StoreFactory
import io.github.stslex.workeeper.core.ui.mvi.processor.StoreProcessor
import io.github.stslex.workeeper.core.ui.mvi.processor.rememberStoreProcessor
import io.github.stslex.workeeper.core.ui.navigation.Component
import io.github.stslex.workeeper.core.ui.navigation.Screen

/**
 * Feature is a Koin feature module that provides a StoreProcessor.
 * It is responsible for managing the state and actions related to the feature.
 *
 * @see [StoreProcessor]
 * */
@Immutable
abstract class Feature<TProcessor : StoreProcessor<*, *, *>, TScreen : Screen, TComponent : Component<TScreen>> {

    @Composable
    abstract fun processor(screen: TScreen): TProcessor

    @Suppress("UNCHECKED_CAST")
    @Composable
    inline fun <
        reified TSImpl : BaseStore<*, *, *>,
        reified TFactory : StoreFactory<TComponent, TSImpl>,
        > Feature<TProcessor, TScreen, TComponent>.createProcessor(
        screen: TScreen,
    ): TProcessor = rememberStoreProcessor<TSImpl, TComponent, TFactory>(screen) as TProcessor
}
