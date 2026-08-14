// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.core.ui.mvi

import androidx.compose.animation.AnimatedContentScope
import androidx.compose.runtime.Composable
import androidx.lifecycle.SavedStateHandle
import io.github.stslex.workeeper.core.ui.mvi.processor.StoreProcessor
import io.github.stslex.workeeper.core.ui.navigation.NavGraphScope
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
    > NavGraphScope.navComponentScreen(
    feature: FeatureAssisted<TProcessor, TScreen>,
    crossinline content: @Composable AnimatedContentScope.(TProcessor) -> Unit,
) {
    navScreen<TScreen> { screen ->
        content(feature.processor(screen))
    }
}

/**
 * Like [navComponentScreen], for a destination that reads a result back from one it opened.
 *
 * The content lambda gets a [NavResults] rather than a raw [SavedStateHandle]: the result is
 * typed off the destination, and the transport stays inside this module. Registered only for
 * the [FeatureAssisted] shape, because both consumers today are assisted and an unused
 * overload is API that 1.3 would have to keep working for no caller.
 */
inline fun <
    TProcessor : StoreProcessor<*, *, *>,
    reified TScreen : Screen,
    > NavGraphScope.navComponentScreenWithResults(
    feature: FeatureAssisted<TProcessor, TScreen>,
    crossinline content: @Composable AnimatedContentScope.(NavResults, TProcessor) -> Unit,
) {
    navScreenWithState<TScreen> { screen, state ->
        content(NavResults(state), feature.processor(screen))
    }
}

inline fun <
    TProcessor : StoreProcessor<*, *, *>,
    reified TScreen : Screen,
    > NavGraphScope.navComponentScreen(
    feature: Feature<TProcessor, TScreen>,
    crossinline content: @Composable AnimatedContentScope.(TProcessor) -> Unit,
) {
    navScreen<TScreen> {
        content(feature.processor())
    }
}
