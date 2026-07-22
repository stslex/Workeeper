// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.exercise_chart.di

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import dev.zacsweers.metro.createGraphFactory
import io.github.stslex.workeeper.core.ui.mvi.FeatureAssisted
import io.github.stslex.workeeper.core.ui.mvi.di.StoreCoreDeps
import io.github.stslex.workeeper.core.ui.mvi.di.appDeps
import io.github.stslex.workeeper.core.ui.mvi.processor.StoreProcessor
import io.github.stslex.workeeper.core.ui.mvi.processor.rememberMetroStoreProcessor
import io.github.stslex.workeeper.core.ui.navigation.NavigatorDeps
import io.github.stslex.workeeper.core.ui.navigation.Screen
import io.github.stslex.workeeper.feature.exercise_chart.mvi.store.ExerciseChartStore.Action
import io.github.stslex.workeeper.feature.exercise_chart.mvi.store.ExerciseChartStore.Event
import io.github.stslex.workeeper.feature.exercise_chart.mvi.store.ExerciseChartStore.State
import io.github.stslex.workeeper.feature.exercise_chart.mvi.store.ExerciseChartStoreImpl

internal typealias ExerciseChartStoreProcessor = StoreProcessor<State, Action, Event>

/**
 * feature/exercise-chart resolves its Store through the **Metro** path. ASSISTED
 * Store (`Screen.ExerciseChart` route arg) — the graph exposes the assisted
 * [ExerciseChartStoreImpl.Factory] and this composable calls `storeFactory.create(screen)` inside
 * the `rememberMetroStoreProcessor` lambda. The 8 app-scoped bindings are acquired as the composition of
 * three narrow interfaces ([StoreCoreDeps] + [NavigatorDeps] + [ExerciseChartDeps] — the domain tail: two
 * repos, `resourceWrapper`, qualified `@DefaultDispatcher`) via `context.appDeps<T>()` (the god-object
 * split, mechanism A). Single `@DefaultDispatcher`, no Context.
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
            // Mechanism A (the god-object split): spine four from StoreCoreDeps + NavigatorDeps; the domain
            // tail (repos + resourceWrapper + qualified @DefaultDispatcher) from ExerciseChartDeps.
            val coreDeps = context.appDeps<StoreCoreDeps>()
            val navDeps = context.appDeps<NavigatorDeps>()
            val deps = context.appDeps<ExerciseChartDeps>()
            createGraphFactory<ExerciseChartGraph.Factory>()
                .create(
                    exerciseRepository = deps.exerciseRepository,
                    sessionRepository = deps.sessionRepository,
                    resourceWrapper = deps.resourceWrapper,
                    navigator = navDeps.navigator,
                    storeDispatchers = coreDeps.storeDispatchers,
                    analyticsHolder = coreDeps.analyticsHolder,
                    loggerHolder = coreDeps.loggerHolder,
                    defaultDispatcher = deps.defaultDispatcher,
                )
                .storeFactory
                .create(screen)
        } as ExerciseChartStoreProcessor
    }
}
