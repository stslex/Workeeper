// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.core.ui.mvi

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import io.github.stslex.workeeper.core.ui.mvi.processor.StoreProcessor
import io.github.stslex.workeeper.core.ui.navigation.Screen

/**
 * Composition entry for a feature whose Store is seeded by the route arguments; Metro binds the
 * `Screen` as a `@Provides` instance on the feature's `@GraphExtension.Factory`.
 */
@Immutable
abstract class FeatureAssisted<TProcessor : StoreProcessor<*, *, *>, TScreen : Screen> {

    @Composable
    abstract fun processor(screen: TScreen): TProcessor
}
