// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.all_trainings.di

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import dagger.hilt.android.EntryPointAccessors
import dev.zacsweers.metro.createGraphFactory
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
 * feature/all-trainings resolves its Store through the **Metro** path (KMP C.1 wave 3). PLAIN Store
 * (a BottomBar destination) — the graph exposes the Store directly and this composable retains it
 * via `rememberMetroStoreProcessor`. The 8 app-scoped Hilt singletons are pulled from the
 * `SingletonComponent` via [AllTrainingsHiltEntryPoint]. Single `@DefaultDispatcher`, no Context.
 */
internal object AllTrainingsFeature : Feature<AllTrainingsStoreProcessor, AllTrainings>() {

    @Suppress("UNCHECKED_CAST")
    @Composable
    override fun processor(): AllTrainingsStoreProcessor {
        val context = LocalContext.current
        return rememberMetroStoreProcessor<AllTrainingsStoreImpl> {
            val entryPoint = EntryPointAccessors.fromApplication(
                context.applicationContext,
                AllTrainingsHiltEntryPoint::class.java,
            )
            createGraphFactory<AllTrainingsGraph.Factory>()
                .create(
                    trainingRepository = entryPoint.trainingRepository(),
                    tagRepository = entryPoint.tagRepository(),
                    resourceWrapper = entryPoint.resourceWrapper(),
                    navigator = entryPoint.navigator(),
                    storeDispatchers = entryPoint.storeDispatchers(),
                    analyticsHolder = entryPoint.analyticsHolder(),
                    loggerHolder = entryPoint.loggerHolder(),
                    defaultDispatcher = entryPoint.defaultDispatcher(),
                )
                .allTrainingsStore
        } as AllTrainingsStoreProcessor
    }
}
