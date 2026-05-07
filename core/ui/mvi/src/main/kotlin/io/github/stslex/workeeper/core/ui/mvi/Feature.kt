// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.core.ui.mvi

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import io.github.stslex.workeeper.core.ui.mvi.processor.StoreProcessor
import io.github.stslex.workeeper.core.ui.mvi.processor.rememberStoreProcessor
import io.github.stslex.workeeper.core.ui.navigation.Screen

@Immutable
abstract class Feature<TProcessor : StoreProcessor<*, *, *>, TScreen : Screen> {

    @Composable
    abstract fun processor(): TProcessor

    @Suppress("UNCHECKED_CAST")
    @Composable
    inline fun <reified TSImpl : BaseStore<*, *, *>> Feature<TProcessor, TScreen>.createProcessor(): TProcessor =
        rememberStoreProcessor<TSImpl>() as TProcessor
}
