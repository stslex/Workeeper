package io.github.stslex.workeeper.core.ui.mvi

import androidx.compose.runtime.Composable
import io.github.stslex.workeeper.core.ui.mvi.processor.StoreProcessor
import io.github.stslex.workeeper.core.ui.navigation.Component

/**
 * NavComponentScreen is a composable function that provides a StoreProcessor
 * for a given feature and component. It allows you to access the processor
 * within the composable content.
 *
 * @param feature The feature that provides the StoreProcessor.
 * @param component The component associated with the feature.
 * @param content The composable content that receives the StoreProcessor.
 */
@Composable
fun <TProcessor : StoreProcessor<*, *, *>, TComponent : Component> NavComponentScreen(
    feature: Feature<TProcessor, TComponent>,
    component: TComponent,
    content: @Composable (TProcessor) -> Unit,
) {
    content(feature.processor(component))
}
