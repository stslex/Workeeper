// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.core.ui.plan_editor.di

import androidx.compose.runtime.Composable
import io.github.stslex.workeeper.core.ui.mvi.Feature
import io.github.stslex.workeeper.core.ui.mvi.processor.StoreProcessor
import io.github.stslex.workeeper.core.ui.navigation.Screen
import io.github.stslex.workeeper.core.ui.plan_editor.mvi.handler.PlanEditorComponent
import io.github.stslex.workeeper.core.ui.plan_editor.mvi.store.PlanEditorStore.Action
import io.github.stslex.workeeper.core.ui.plan_editor.mvi.store.PlanEditorStore.Event
import io.github.stslex.workeeper.core.ui.plan_editor.mvi.store.PlanEditorStore.State
import io.github.stslex.workeeper.core.ui.plan_editor.mvi.store.PlanEditorStoreImpl

internal typealias PlanEditorStoreProcessor = StoreProcessor<State, Action, Event>

internal object PlanEditorFeature :
    Feature<PlanEditorStoreProcessor, Screen.PlanEditor, PlanEditorComponent>() {

    @Composable
    override fun processor(
        screen: Screen.PlanEditor,
    ): PlanEditorStoreProcessor =
        createProcessor<PlanEditorStoreImpl, PlanEditorStoreImpl.Factory>(screen)
}
