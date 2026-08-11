// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.plan_editor.di

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import io.github.stslex.workeeper.core.ui.mvi.FeatureAssisted
import io.github.stslex.workeeper.core.ui.mvi.di.appDeps
import io.github.stslex.workeeper.core.ui.mvi.processor.StoreProcessor
import io.github.stslex.workeeper.core.ui.mvi.processor.rememberMetroStoreProcessor
import io.github.stslex.workeeper.core.ui.navigation.Screen
import io.github.stslex.workeeper.feature.plan_editor.ui.mvi.store.PlanEditorStore.Action
import io.github.stslex.workeeper.feature.plan_editor.ui.mvi.store.PlanEditorStore.Event
import io.github.stslex.workeeper.feature.plan_editor.ui.mvi.store.PlanEditorStore.State
import io.github.stslex.workeeper.feature.plan_editor.ui.mvi.store.PlanEditorStoreImpl

internal typealias PlanEditorStoreProcessor = StoreProcessor<State, Action, Event>

/**
 * feature/plan-editor resolves its Store through the Metro **graph-extension** path, shape B.
 *
 * The app-scope graph (returned as `Any` by the `AppDepsHolder` seam) IS the parent graph, and once
 * `:app` is compiled it implements the contributed [PlanEditorGraph.Factory]. `appDeps<T>()` re-narrows
 * it with its `as T` cast — the same acquisition seam as before, now targeting the contributed factory
 * instead of the three `XxxDeps` interfaces. All 8 formerly hand-threaded app-scoped deps are inherited
 * from the parent, so the creator's ONLY argument is the route arg.
 *
 * [FeatureAssisted] stays: the composition seam still takes `processor(screen)`, because navigation still
 * hands the arg in per destination. What changed is where the arg goes — it is now a `@Provides` bound
 * instance on `createPlanEditorGraph(screen)` rather than an `@Assisted` param on the Store, so one
 * extension is built per navigation entry, parameterised by that entry's arg. `FeatureAssisted` names the
 * COMPOSITION shape, not the DI mechanism; the feature contains no assisted machinery at all now.
 *
 * The extension is created INSIDE the `rememberMetroStoreProcessor` factory lambda, so it is built at
 * most once per retained [PlanEditorStoreImpl] (per `NavBackStackEntry` `ViewModelStore`) — binding the
 * extension and its `@SingleIn(PlanEditorScope)` nodes to exactly the Store's lifetime.
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
            context.appDeps<PlanEditorGraph.Factory>()
                .createPlanEditorGraph(screen)
                .planEditorStore
        } as PlanEditorStoreProcessor
    }
}
