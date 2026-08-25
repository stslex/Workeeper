// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.core.ui.mvi

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import io.github.stslex.workeeper.core.ui.mvi.processor.StoreProcessor
import io.github.stslex.workeeper.core.ui.navigation.Screen

/**
 * Composition entry for a feature whose Store needs no route arguments. Use [FeatureAssisted]
 * when the destination carries a `Screen` payload that seeds the initial state.
 */
@Immutable
abstract class Feature<TProcessor : StoreProcessor<*, *, *>, TScreen : Screen> {

    @Composable
    abstract fun processor(): TProcessor
}
