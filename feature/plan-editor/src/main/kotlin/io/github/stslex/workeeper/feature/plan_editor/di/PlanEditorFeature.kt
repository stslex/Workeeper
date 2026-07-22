// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.plan_editor.di

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
import io.github.stslex.workeeper.feature.plan_editor.ui.mvi.store.PlanEditorStore.Action
import io.github.stslex.workeeper.feature.plan_editor.ui.mvi.store.PlanEditorStore.Event
import io.github.stslex.workeeper.feature.plan_editor.ui.mvi.store.PlanEditorStore.State
import io.github.stslex.workeeper.feature.plan_editor.ui.mvi.store.PlanEditorStoreImpl

internal typealias PlanEditorStoreProcessor = StoreProcessor<State, Action, Event>

/**
 * feature/plan-editor resolves its Store through the **Metro** path. ASSISTED Store
 * (`Screen.PlanEditor` route arg) — the graph exposes the assisted [PlanEditorStoreImpl.Factory] and
 * this composable calls `storeFactory.create(screen)` inside the `rememberMetroStoreProcessor`
 * lambda. The 8 app-scoped bindings are acquired as the composition of three narrow interfaces
 * ([StoreCoreDeps] + [NavigatorDeps] + [PlanEditorDeps] — the domain tail: two repos, `resourceWrapper`,
 * qualified `@DefaultDispatcher`) via `context.appDeps<T>()` (the god-object split, mechanism A). Single
 * `@DefaultDispatcher`, no Context.
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
            // Mechanism A (the god-object split): spine four from StoreCoreDeps + NavigatorDeps; the domain
            // tail (repos + resourceWrapper + qualified @DefaultDispatcher) from PlanEditorDeps.
            val coreDeps = context.appDeps<StoreCoreDeps>()
            val navDeps = context.appDeps<NavigatorDeps>()
            val deps = context.appDeps<PlanEditorDeps>()
            createGraphFactory<PlanEditorGraph.Factory>()
                .create(
                    exerciseRepository = deps.exerciseRepository,
                    trainingExerciseRepository = deps.trainingExerciseRepository,
                    resourceWrapper = deps.resourceWrapper,
                    navigator = navDeps.navigator,
                    storeDispatchers = coreDeps.storeDispatchers,
                    analyticsHolder = coreDeps.analyticsHolder,
                    loggerHolder = coreDeps.loggerHolder,
                    defaultDispatcher = deps.defaultDispatcher,
                )
                .storeFactory
                .create(screen)
        } as PlanEditorStoreProcessor
    }
}
