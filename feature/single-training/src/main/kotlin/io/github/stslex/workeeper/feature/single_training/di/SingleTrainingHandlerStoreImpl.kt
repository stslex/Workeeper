// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.single_training.di

import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import io.github.stslex.workeeper.core.ui.mvi.handler.BaseHandlerStore
import io.github.stslex.workeeper.feature.single_training.mvi.store.SingleTrainingStore.Action
import io.github.stslex.workeeper.feature.single_training.mvi.store.SingleTrainingStore.Event
import io.github.stslex.workeeper.feature.single_training.mvi.store.SingleTrainingStore.State

@Inject
@SingleIn(SingleTrainingScope::class)
class SingleTrainingHandlerStoreImpl : SingleTrainingHandlerStore,
    BaseHandlerStore<State, Action, Event>()
