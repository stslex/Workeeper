// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.single_training.di

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import io.github.stslex.workeeper.core.ui.mvi.FeatureAssisted
import io.github.stslex.workeeper.core.ui.mvi.di.appDeps
import io.github.stslex.workeeper.core.ui.mvi.processor.StoreProcessor
import io.github.stslex.workeeper.core.ui.mvi.processor.rememberMetroStoreProcessor
import io.github.stslex.workeeper.core.ui.navigation.Screen
import io.github.stslex.workeeper.feature.single_training.mvi.store.SingleTrainingStore.Action
import io.github.stslex.workeeper.feature.single_training.mvi.store.SingleTrainingStore.Event
import io.github.stslex.workeeper.feature.single_training.mvi.store.SingleTrainingStore.State
import io.github.stslex.workeeper.feature.single_training.mvi.store.SingleTrainingStoreImpl

internal typealias SingleTrainingStoreProcessor = StoreProcessor<State, Action, Event>

/**
 * Resolves the Store through the Metro graph-extension path. The extension is created inside the
 * `rememberMetroStoreProcessor` lambda, so it lives exactly as long as the retained Store.
 */
internal object SingleTrainingFeature : FeatureAssisted<
    SingleTrainingStoreProcessor,
    Screen.Training,
    >() {

    @Suppress("UNCHECKED_CAST")
    @Composable
    override fun processor(screen: Screen.Training): SingleTrainingStoreProcessor {
        val context = LocalContext.current
        return rememberMetroStoreProcessor<SingleTrainingStoreImpl> {
            context.appDeps<SingleTrainingGraph.Factory>()
                .createSingleTrainingGraph(screen)
                .singleTrainingStore
        } as SingleTrainingStoreProcessor
    }
}
