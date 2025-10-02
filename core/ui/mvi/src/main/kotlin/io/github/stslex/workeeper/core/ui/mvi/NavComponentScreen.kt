package io.github.stslex.workeeper.core.ui.mvi

import androidx.compose.runtime.Composable
import io.github.stslex.workeeper.core.ui.mvi.processor.StoreProcessor
import io.github.stslex.workeeper.core.ui.navigation.Component

@Suppress("UNCHECKED_CAST")
@Composable
fun <TProcessor : StoreProcessor<*, *, *>, TComponent : Component> NavComponentScreen(
    feature: Feature<TProcessor, TComponent>,
    component: TComponent,
    content: @Composable (TProcessor) -> Unit,
) {
    content(feature.processor(component))
}
