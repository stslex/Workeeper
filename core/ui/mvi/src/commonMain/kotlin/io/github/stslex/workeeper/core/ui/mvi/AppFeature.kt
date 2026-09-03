// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.core.ui.mvi

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import io.github.stslex.workeeper.core.ui.mvi.processor.StoreProcessor

/**
 * Composition entry for a feature whose Store lives at the App root, not in a nav destination.
 * GUARD: mount as a sibling of `NavDisplay`; inside a destination the Store silently rescopes.
 */
@Immutable
abstract class AppFeature<TProcessor : StoreProcessor<*, *, *>> {

    @Composable
    abstract fun processor(): TProcessor
}
