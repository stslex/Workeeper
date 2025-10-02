package io.github.stslex.workeeper.core.ui.mvi

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import io.github.stslex.workeeper.core.ui.mvi.processor.StoreProcessor
import io.github.stslex.workeeper.core.ui.navigation.Component

/**
 * Feature is a Koin feature module that provides a StoreProcessor.
 * It is responsible for managing the state and actions related to the feature.
 *
 * @see [StoreProcessor]
 * */
@Immutable
interface Feature<TProcessor : StoreProcessor<*, *, *>, TComponent : Component> {

    @Composable
    fun processor(component: TComponent): TProcessor
}
