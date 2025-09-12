package io.github.stslex.workeeper.feature.home.ui.mvi.handler

import androidx.compose.runtime.Stable
import io.github.stslex.workeeper.core.ui.mvi.handler.Handler
import io.github.stslex.workeeper.core.ui.navigation.Component
import io.github.stslex.workeeper.core.ui.navigation.Navigator
import io.github.stslex.workeeper.feature.home.ui.mvi.store.HomeStore.Action.Navigation

@Stable
interface HomeComponent : Component, Handler<Navigation> {

    companion object {

        fun create(navigator: Navigator): HomeComponent = HomeComponentImpl(navigator)
    }
}
