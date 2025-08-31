package io.github.stslex.workeeper.feature.home.ui.mvi.handler

import io.github.stslex.workeeper.core.ui.mvi.handler.Handler
import io.github.stslex.workeeper.core.ui.navigation.Component
import io.github.stslex.workeeper.core.ui.navigation.Router
import io.github.stslex.workeeper.feature.home.ui.mvi.store.HomeHandlerStore
import io.github.stslex.workeeper.feature.home.ui.mvi.store.HomeStore.Action.Navigation

interface HomeComponent : Component, Handler<Navigation, HomeHandlerStore> {

    companion object {

        fun create(router: Router): HomeComponent = HomeComponentImpl(router)
    }
}
