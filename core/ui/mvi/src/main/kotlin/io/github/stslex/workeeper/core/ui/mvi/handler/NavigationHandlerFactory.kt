package io.github.stslex.workeeper.core.ui.mvi.handler

import io.github.stslex.workeeper.core.ui.navigation.Navigator

abstract class NavigationHandlerFactory<THandler : Handler<*>>(
    private val creator: (Navigator) -> THandler,
) {

    @Volatile
    private var handler: THandler? = null

    fun getOrCreate(navigator: Navigator): THandler = handler ?: creator(navigator).also {
        handler = it
    }
}
