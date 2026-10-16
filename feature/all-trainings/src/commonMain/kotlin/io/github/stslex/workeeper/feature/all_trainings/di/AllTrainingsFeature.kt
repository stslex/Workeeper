// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.all_trainings.di

import androidx.compose.runtime.Composable
import io.github.stslex.workeeper.core.ui.mvi.Feature
import io.github.stslex.workeeper.core.ui.mvi.processor.StoreProcessor
import io.github.stslex.workeeper.core.ui.mvi.processor.rememberMetroStoreProcessor
import io.github.stslex.workeeper.core.ui.navigation.Screen.BottomBar.AllTrainings
import io.github.stslex.workeeper.feature.all_trainings.mvi.store.AllTrainingsStore.Action
import io.github.stslex.workeeper.feature.all_trainings.mvi.store.AllTrainingsStore.Event
import io.github.stslex.workeeper.feature.all_trainings.mvi.store.AllTrainingsStore.State
import io.github.stslex.workeeper.feature.all_trainings.mvi.store.AllTrainingsStoreImpl

internal typealias AllTrainingsStoreProcessor = StoreProcessor<State, Action, Event>

/**
 * Resolves the Store through the Metro graph-extension path. The extension is created inside the
 * `rememberMetroStoreProcessor` lambda, so its scope is exactly the retained Store's lifetime.
 */
internal class AllTrainingsFeature(
    private val factory: AllTrainingsGraph.Factory,
) : Feature<AllTrainingsStoreProcessor, AllTrainings>() {

    @Suppress("UNCHECKED_CAST")
    @Composable
    override fun processor(): AllTrainingsStoreProcessor {
        return rememberMetroStoreProcessor<AllTrainingsStoreImpl> {
            factory
                .createAllTrainingsGraph()
                .allTrainingsStore
        } as AllTrainingsStoreProcessor
    }
}
