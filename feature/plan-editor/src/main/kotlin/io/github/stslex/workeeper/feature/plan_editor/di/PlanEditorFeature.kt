// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.plan_editor.di

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import dev.zacsweers.metro.createGraphFactory
import io.github.stslex.workeeper.core.di.appGraphContract
import io.github.stslex.workeeper.core.ui.mvi.FeatureAssisted
import io.github.stslex.workeeper.core.ui.mvi.processor.StoreProcessor
import io.github.stslex.workeeper.core.ui.mvi.processor.rememberMetroStoreProcessor
import io.github.stslex.workeeper.core.ui.navigation.Screen
import io.github.stslex.workeeper.feature.plan_editor.ui.mvi.store.PlanEditorStore.Action
import io.github.stslex.workeeper.feature.plan_editor.ui.mvi.store.PlanEditorStore.Event
import io.github.stslex.workeeper.feature.plan_editor.ui.mvi.store.PlanEditorStore.State
import io.github.stslex.workeeper.feature.plan_editor.ui.mvi.store.PlanEditorStoreImpl

internal typealias PlanEditorStoreProcessor = StoreProcessor<State, Action, Event>

/**
 * feature/plan-editor resolves its Store through the **Metro** path (KMP C.1 wave 3). ASSISTED Store
 * (`Screen.PlanEditor` route arg) — the graph exposes the assisted [PlanEditorStoreImpl.Factory] and
 * this composable calls `storeFactory.create(screen)` inside the `rememberMetroStoreProcessor`
 * lambda. The 8 app-scoped Hilt singletons are pulled from the `SingletonComponent` via
 * [PlanEditorHiltEntryPoint]. Single `@DefaultDispatcher`, no Context.
 */
internal object PlanEditorFeature : FeatureAssisted<
    PlanEditorStoreProcessor,
    Screen.PlanEditor,
    >() {

    @Suppress("UNCHECKED_CAST")
    @Composable
    override fun processor(screen: Screen.PlanEditor): PlanEditorStoreProcessor {
        val context = LocalContext.current
        return rememberMetroStoreProcessor<PlanEditorStoreImpl> {
            // P-BRIDGES: app-scope deps read via the Metro AppGraphContract (Hilt-free), replacing
            // EntryPointAccessors + the feature HiltEntryPoint. Same app graph, same bindings; the
            // HiltEntryPoint declaration stays until the cut (dead once this reader repoints).
            val graph = context.appGraphContract()
            createGraphFactory<PlanEditorGraph.Factory>()
                .create(
                    exerciseRepository = graph.exerciseRepository,
                    trainingExerciseRepository = graph.trainingExerciseRepository,
                    resourceWrapper = graph.resourceWrapper,
                    navigator = graph.navigator,
                    storeDispatchers = graph.storeDispatchers,
                    analyticsHolder = graph.analyticsHolder,
                    loggerHolder = graph.loggerHolder,
                    defaultDispatcher = graph.defaultDispatcher,
                )
                .storeFactory
                .create(screen)
        } as PlanEditorStoreProcessor
    }
}
