package io.github.stslex.workeeper.core.ui.mvi

import androidx.compose.animation.AnimatedContentScope
import androidx.compose.runtime.Composable
import androidx.lifecycle.SavedStateHandle
import androidx.navigation.NavGraphBuilder
import io.github.stslex.workeeper.core.ui.mvi.processor.StoreProcessor
import io.github.stslex.workeeper.core.ui.navigation.Component
import io.github.stslex.workeeper.core.ui.navigation.Screen
import io.github.stslex.workeeper.core.ui.navigation.navScreen
import io.github.stslex.workeeper.core.ui.navigation.navScreenWithState

/**
 * NavComponentScreen is a composable function that provides a StoreProcessor
 * for a given feature and screen type within a navigation graph. It allows you to access the processor
 * within the composable content.
 *
 * @param feature The feature that provides the StoreProcessor.
 * @param content The composable content that receives the StoreProcessor.
 */
inline fun <
    TProcessor : StoreProcessor<*, *, *>,
    reified TScreen : Screen,
    TComponent : Component<TScreen>,
    > NavGraphBuilder.navComponentScreen(
    feature: FeatureAssisted<TProcessor, TScreen, TComponent>,
    crossinline content: @Composable AnimatedContentScope.(TProcessor) -> Unit,
) {
    navScreen<TScreen> { screen ->
        content(feature.processor(screen))
    }
}

inline fun <
    TProcessor : StoreProcessor<*, *, *>,
    reified TScreen : Screen,
    TComponent : Component<TScreen>,
    > NavGraphBuilder.navComponentScreenWithState(
    feature: FeatureAssisted<TProcessor, TScreen, TComponent>,
    crossinline content: @Composable AnimatedContentScope.(SavedStateHandle, TProcessor) -> Unit,
) {
    navScreenWithState<TScreen> { screen, state ->
        content(state, feature.processor(screen))
    }
}

inline fun <
    TProcessor : StoreProcessor<*, *, *>,
    reified TScreen : Screen,
    > NavGraphBuilder.navComponentScreen(
    feature: Feature<TProcessor, TScreen>,
    crossinline content: @Composable AnimatedContentScope.(TProcessor) -> Unit,
) {
    navScreen<TScreen> {
        content(feature.processor())
    }
}

inline fun <
    TProcessor : StoreProcessor<*, *, *>,
    reified TScreen : Screen,
    > NavGraphBuilder.navComponentScreenWithState(
    feature: Feature<TProcessor, TScreen>,
    crossinline content: @Composable AnimatedContentScope.(SavedStateHandle, TProcessor) -> Unit,
) {
    navScreenWithState<TScreen> { screen, state ->
        content(state, feature.processor())
    }
}
