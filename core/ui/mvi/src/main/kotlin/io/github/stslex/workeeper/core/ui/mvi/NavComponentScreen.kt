package io.github.stslex.workeeper.core.ui.mvi

import androidx.compose.runtime.Composable
import io.github.stslex.workeeper.core.ui.mvi.processor.StoreProcessor
import io.github.stslex.workeeper.core.ui.navigation.Component
import org.koin.compose.module.rememberKoinModules
import org.koin.core.annotation.KoinExperimentalAPI

@OptIn(KoinExperimentalAPI::class)
@Suppress("UNCHECKED_CAST")
@Composable
fun <TProcessor : StoreProcessor<*, *, *>, TComponent : Component> NavComponentScreen(
    feature: Feature<TProcessor, TComponent>,
    component: TComponent,
    content: @Composable (TProcessor) -> Unit,
) {
    feature.loadModule?.let {
        rememberKoinModules(unloadModules = true) { listOf(it) }
    }

    content(feature.processor(component))
}
