// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.core.ui.plan_editor.di

import dagger.hilt.android.scopes.ViewModelScoped
import io.github.stslex.workeeper.core.ui.mvi.handler.BaseHandlerStore
import io.github.stslex.workeeper.core.ui.plan_editor.mvi.store.PlanEditorStore.Action
import io.github.stslex.workeeper.core.ui.plan_editor.mvi.store.PlanEditorStore.Event
import io.github.stslex.workeeper.core.ui.plan_editor.mvi.store.PlanEditorStore.State
import javax.inject.Inject

@ViewModelScoped
internal class PlanEditorHandlerStoreImpl @Inject constructor() : PlanEditorHandlerStore,
    BaseHandlerStore<State, Action, Event>()
