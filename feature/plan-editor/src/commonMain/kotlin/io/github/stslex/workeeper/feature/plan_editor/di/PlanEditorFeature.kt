// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.plan_editor.di

import androidx.compose.runtime.Composable
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
 * Resolves the Store through the Metro graph-extension path, shape B: the route arg is a bound
 * instance on the factory, and the extension is built inside `rememberMetroStoreProcessor`.
 */
internal class PlanEditorFeature(
    private val factory: PlanEditorGraph.Factory,
) : FeatureAssisted<
    PlanEditorStoreProcessor,
    Screen.PlanEditor,
    >() {

    @Suppress("UNCHECKED_CAST")
    @Composable
    override fun processor(screen: Screen.PlanEditor): PlanEditorStoreProcessor {
        return rememberMetroStoreProcessor<PlanEditorStoreImpl> {
            factory.createPlanEditorGraph(screen)
                .planEditorStore
        } as PlanEditorStoreProcessor
    }
}
