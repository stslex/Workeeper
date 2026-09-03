// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.all_trainings.di

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import io.github.stslex.workeeper.core.ui.mvi.Feature
import io.github.stslex.workeeper.core.ui.mvi.di.appDeps
import io.github.stslex.workeeper.core.ui.mvi.processor.StoreProcessor
import io.github.stslex.workeeper.core.ui.mvi.processor.rememberMetroStoreProcessor
import io.github.stslex.workeeper.core.ui.navigation.Screen.BottomBar.AllTrainings
import io.github.stslex.workeeper.feature.all_trainings.mvi.store.AllTrainingsStore.Action
import io.github.stslex.workeeper.feature.all_trainings.mvi.store.AllTrainingsStore.Event
import io.github.stslex.workeeper.feature.all_trainings.mvi.store.AllTrainingsStore.State
import io.github.stslex.workeeper.feature.all_trainings.mvi.store.AllTrainingsStoreImpl

internal typealias AllTrainingsStoreProcessor = StoreProcessor<State, Action, Event>

/**
 * Resolves the plain Store through the Metro graph-extension path: `appDeps<T>()` narrows the
 * app-scope graph to the contributed [AllTrainingsGraph.Factory]. See architecture.md.
 */
internal object AllTrainingsFeature : Feature<AllTrainingsStoreProcessor, AllTrainings>() {

    @Suppress("UNCHECKED_CAST")
    @Composable
    override fun processor(): AllTrainingsStoreProcessor {
        val context = LocalContext.current
        return rememberMetroStoreProcessor<AllTrainingsStoreImpl> {
            context.appDeps<AllTrainingsGraph.Factory>()
                .createAllTrainingsGraph()
                .allTrainingsStore
        } as AllTrainingsStoreProcessor
    }
}
