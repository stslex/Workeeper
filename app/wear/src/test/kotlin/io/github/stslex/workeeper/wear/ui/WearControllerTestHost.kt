package io.github.stslex.workeeper.wear.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import io.github.stslex.workeeper.core.core.coroutine.scope.AppScopeLifetime
import io.github.stslex.workeeper.core.ui.mvi.di.StoreDispatchers
import io.github.stslex.workeeper.core.ui.mvi.holders.AnalyticsHolder
import io.github.stslex.workeeper.core.ui.mvi.holders.LoggerHolder
import io.github.stslex.workeeper.core.ui.mvi.processor.StoreProcessor
import io.github.stslex.workeeper.core.ui.mvi.processor.rememberStoreProcessor
import io.github.stslex.workeeper.wear.ambient.WearAmbientState
import io.github.stslex.workeeper.wear.di.WearHandlerStoreImpl
import io.github.stslex.workeeper.wear.domain.WearInteractor
import io.github.stslex.workeeper.wear.mvi.handler.ClickHandler
import io.github.stslex.workeeper.wear.mvi.handler.CommonHandler
import io.github.stslex.workeeper.wear.mvi.handler.InputHandler
import io.github.stslex.workeeper.wear.mvi.handler.NavigationHandler
import io.github.stslex.workeeper.wear.mvi.mapper.WearPresentationMapper
import io.github.stslex.workeeper.wear.mvi.store.WearPresentation
import io.github.stslex.workeeper.wear.mvi.store.WearStore.Action
import io.github.stslex.workeeper.wear.mvi.store.WearStore.Event
import io.github.stslex.workeeper.wear.mvi.store.WearStore.State
import io.github.stslex.workeeper.wear.mvi.store.WearStoreImpl
import io.github.stslex.workeeper.wear.runtime.ControllerAction
import io.github.stslex.workeeper.wear.runtime.WatchRuntimeSnapshot
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow

/** Render fixtures enter the real Store; editor and ambient decisions are production handlers. */
@Composable
internal fun WearControllerScreen(
    state: WearSurfaceState,
    ambient: WearAmbientState = WearAmbientState(),
    ongoingNotice: WearOngoingNotice? = null,
    onEnableNotifications: () -> Unit = {},
    onAction: (ControllerAction) -> Unit,
) {
    val actionCallback = rememberUpdatedState(onAction)
    val lifetime = remember { AppScopeLifetime() }
    val store = remember {
        fixtureStore(
            interactor = object : WearInteractor {
                override val snapshots: Flow<WatchRuntimeSnapshot> = emptyFlow()
                override fun current(): WatchRuntimeSnapshot = WatchRuntimeSnapshot()
                override fun perform(action: ControllerAction) = actionCallback.value(action)
            },
            lifetime = lifetime,
        )
    }
    DisposableEffect(lifetime) { onDispose { lifetime.cancel() } }
    @Suppress("UNCHECKED_CAST")
    val processor = rememberStoreProcessor { store } as StoreProcessor<State, Action, Event>
    LaunchedEffect(state, ambient, ongoingNotice) {
        processor.consume(Action.Common.PresentationChanged(WearPresentation(state, ambient, ongoingNotice)))
    }
    processor.Handle { event ->
        when (event) {
            Event.EnableNotificationsRequested -> onEnableNotifications()
        }
    }
    WearControllerScreen(state = processor.state.value, consume = processor::consume)
}

internal fun fixtureStore(interactor: WearInteractor, lifetime: AppScopeLifetime): WearStoreImpl {
    val handlerStore = WearHandlerStoreImpl()
    val dispatchers = StoreDispatchers(Dispatchers.Main.immediate, Dispatchers.Main.immediate)
    return WearStoreImpl(
        commonHandler = CommonHandler(handlerStore, interactor, WearPresentationMapper(), dispatchers),
        clickHandler = ClickHandler(handlerStore, interactor),
        inputHandler = InputHandler(handlerStore, interactor),
        navigationHandler = NavigationHandler(handlerStore),
        handlerStore = handlerStore,
        storeDispatchers = dispatchers,
        analyticsHolder = AnalyticsHolder(),
        loggerHolder = LoggerHolder(),
        appScopeLifetime = lifetime,
    )
}
