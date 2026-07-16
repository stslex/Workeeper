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
 * feature/plan-editor resolves its Store through the **Metro** path. ASSISTED Store
 * (`Screen.PlanEditor` route arg) — the graph exposes the assisted [PlanEditorStoreImpl.Factory] and
 * this composable calls `storeFactory.create(screen)` inside the `rememberMetroStoreProcessor`
 * lambda. The 8 app-scoped bindings are pulled from the Metro AppGraph via
 * [appGraphContract]. Single `@DefaultDispatcher`, no Context.
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
