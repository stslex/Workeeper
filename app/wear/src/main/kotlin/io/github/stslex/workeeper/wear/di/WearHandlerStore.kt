package io.github.stslex.workeeper.wear.di

import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import io.github.stslex.workeeper.core.ui.mvi.handler.BaseHandlerStore
import io.github.stslex.workeeper.core.ui.mvi.handler.HandlerStore
import io.github.stslex.workeeper.wear.mvi.store.WearStore.Action
import io.github.stslex.workeeper.wear.mvi.store.WearStore.Event
import io.github.stslex.workeeper.wear.mvi.store.WearStore.State

internal interface WearHandlerStore : HandlerStore<State, Action, Event>

@Inject
@SingleIn(WearScope::class)
internal class WearHandlerStoreImpl : BaseHandlerStore<State, Action, Event>(), WearHandlerStore
