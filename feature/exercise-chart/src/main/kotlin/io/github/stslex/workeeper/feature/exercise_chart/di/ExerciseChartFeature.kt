// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.exercise_chart.di

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import dev.zacsweers.metro.createGraphFactory
import io.github.stslex.workeeper.core.di.appGraphContract
import io.github.stslex.workeeper.core.ui.mvi.FeatureAssisted
import io.github.stslex.workeeper.core.ui.mvi.processor.StoreProcessor
import io.github.stslex.workeeper.core.ui.mvi.processor.rememberMetroStoreProcessor
import io.github.stslex.workeeper.core.ui.navigation.Screen
import io.github.stslex.workeeper.feature.exercise_chart.mvi.store.ExerciseChartStore.Action
import io.github.stslex.workeeper.feature.exercise_chart.mvi.store.ExerciseChartStore.Event
import io.github.stslex.workeeper.feature.exercise_chart.mvi.store.ExerciseChartStore.State
import io.github.stslex.workeeper.feature.exercise_chart.mvi.store.ExerciseChartStoreImpl

internal typealias ExerciseChartStoreProcessor = StoreProcessor<State, Action, Event>

/**
 * feature/exercise-chart resolves its Store through the **Metro** path (KMP C.1 wave 3). ASSISTED
 * Store (`Screen.ExerciseChart` route arg) — the graph exposes the assisted
 * [ExerciseChartStoreImpl.Factory] and this composable calls `storeFactory.create(screen)` inside
 * the `rememberMetroStoreProcessor` lambda. The 8 app-scoped Hilt singletons are pulled from the
 * `SingletonComponent` via [ExerciseChartHiltEntryPoint]. Single `@DefaultDispatcher`, no Context.
 */
internal object ExerciseChartFeature : FeatureAssisted<
    ExerciseChartStoreProcessor,
    Screen.ExerciseChart,
    >() {

    @Suppress("UNCHECKED_CAST")
    @Composable
    override fun processor(screen: Screen.ExerciseChart): ExerciseChartStoreProcessor {
        val context = LocalContext.current
        return rememberMetroStoreProcessor<ExerciseChartStoreImpl> {
            // P-BRIDGES: app-scope deps read via the Metro AppGraphContract (Hilt-free), replacing
            // EntryPointAccessors + the feature HiltEntryPoint. Same app graph, same bindings; the
            // HiltEntryPoint declaration stays until the cut (dead once this reader repoints).
            val graph = context.appGraphContract()
            createGraphFactory<ExerciseChartGraph.Factory>()
                .create(
                    exerciseRepository = graph.exerciseRepository,
                    sessionRepository = graph.sessionRepository,
                    resourceWrapper = graph.resourceWrapper,
                    navigator = graph.navigator,
                    storeDispatchers = graph.storeDispatchers,
                    analyticsHolder = graph.analyticsHolder,
                    loggerHolder = graph.loggerHolder,
                    defaultDispatcher = graph.defaultDispatcher,
                )
                .storeFactory
                .create(screen)
        } as ExerciseChartStoreProcessor
    }
}
