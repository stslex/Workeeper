// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.core.ui.mvi

import androidx.compose.runtime.Composable
import io.github.stslex.workeeper.core.ui.mvi.processor.StoreProcessor
import io.github.stslex.workeeper.core.ui.navigation.NavGraphScope
import io.github.stslex.workeeper.core.ui.navigation.Screen
import io.github.stslex.workeeper.core.ui.navigation.navScreen
import io.github.stslex.workeeper.core.ui.navigation.navScreenWithResults

/** Registers [feature]'s destination and hands its [StoreProcessor] to [content]. */
inline fun <
    TProcessor : StoreProcessor<*, *, *>,
    reified TScreen : Screen,
    > NavGraphScope.navComponentScreen(
    feature: FeatureAssisted<TProcessor, TScreen>,
    crossinline content: @Composable (TProcessor) -> Unit,
) {
    navScreen<TScreen> { screen ->
        content(feature.processor(screen))
    }
}

/**
 * Like [navComponentScreen], for a destination that reads a result back from one it opened.
 * Registered only for the [FeatureAssisted] shape — both consumers are assisted.
 */
inline fun <
    TProcessor : StoreProcessor<*, *, *>,
    reified TScreen : Screen,
    > NavGraphScope.navComponentScreenWithResults(
    feature: FeatureAssisted<TProcessor, TScreen>,
    crossinline content: @Composable (NavResults, TProcessor) -> Unit,
) {
    navScreenWithResults<TScreen> { screen, source ->
        content(NavResults(source), feature.processor(screen))
    }
}

inline fun <
    TProcessor : StoreProcessor<*, *, *>,
    reified TScreen : Screen,
    > NavGraphScope.navComponentScreen(
    feature: Feature<TProcessor, TScreen>,
    crossinline content: @Composable (TProcessor) -> Unit,
) {
    navScreen<TScreen> {
        content(feature.processor())
    }
}
