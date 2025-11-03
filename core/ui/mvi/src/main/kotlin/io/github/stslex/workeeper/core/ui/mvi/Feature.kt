package io.github.stslex.workeeper.core.ui.mvi

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import io.github.stslex.workeeper.core.ui.mvi.processor.StoreFactory
import io.github.stslex.workeeper.core.ui.mvi.processor.StoreProcessor
import io.github.stslex.workeeper.core.ui.mvi.processor.rememberStoreProcessor
import io.github.stslex.workeeper.core.ui.navigation.Component

/**
 * Feature is a Koin feature module that provides a StoreProcessor.
 * It is responsible for managing the state and actions related to the feature.
 *
 * @see [StoreProcessor]
 * */
@Immutable
abstract class Feature<TProcessor : StoreProcessor<*, *, *>, TComponent : Component> {

    @Composable
    abstract fun processor(component: TComponent): TProcessor

    @Suppress("UNCHECKED_CAST")
    @Composable
    inline fun <
        reified TStoreImpl : BaseStore<*, *, *>,
        reified TFactory : StoreFactory<TComponent, TStoreImpl>,
        > Feature<TProcessor, TComponent>.createProcessor(
        component: TComponent,
    ): TProcessor = rememberStoreProcessor<TStoreImpl, TComponent, TFactory>(
        component,
    ) as TProcessor
}
