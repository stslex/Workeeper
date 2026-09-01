// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.plan_editor.di

import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import io.github.stslex.workeeper.core.ui.mvi.handler.BaseHandlerStore
import io.github.stslex.workeeper.feature.plan_editor.ui.mvi.store.PlanEditorStore.Action
import io.github.stslex.workeeper.feature.plan_editor.ui.mvi.store.PlanEditorStore.Event
import io.github.stslex.workeeper.feature.plan_editor.ui.mvi.store.PlanEditorStore.State

@Inject
@SingleIn(PlanEditorScope::class)
class PlanEditorHandlerStoreImpl : PlanEditorHandlerStore,
    BaseHandlerStore<State, Action, Event>()
