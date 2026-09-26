package io.github.stslex.workeeper.wear.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import io.github.stslex.workeeper.core.ui.mvi.processor.StoreProcessor
import io.github.stslex.workeeper.core.ui.mvi.processor.rememberMetroStoreProcessor
import io.github.stslex.workeeper.wear.mvi.store.WearPlatformState
import io.github.stslex.workeeper.wear.mvi.store.WearStore.Action
import io.github.stslex.workeeper.wear.mvi.store.WearStore.Event
import io.github.stslex.workeeper.wear.mvi.store.WearStore.State
import io.github.stslex.workeeper.wear.mvi.store.WearStoreImpl

@Composable
internal fun WearControllerRoute(
    factory: () -> WearStoreImpl,
    platform: WearPlatformState,
    onEnableNotifications: () -> Unit,
) {
    @Suppress("UNCHECKED_CAST")
    val processor = rememberMetroStoreProcessor(factory) as StoreProcessor<State, Action, Event>
    LaunchedEffect(platform) { processor.consume(Action.Common.PlatformChanged(platform)) }
    processor.Handle { event ->
        when (event) {
            Event.EnableNotificationsRequested -> onEnableNotifications()
        }
    }
    WearControllerScreen(state = processor.state.value, consume = processor::consume)
}
