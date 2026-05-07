// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.navigation

import io.github.stslex.workeeper.core.ui.navigation.Navigator
import io.github.stslex.workeeper.core.ui.navigation.Screen
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class NavigatorEventBus @Inject constructor() : Navigator, NavigatorReceiver {

    private val _commands = MutableSharedFlow<NavigationCommand>(
        extraBufferCapacity = 64,
    )
    override val commands: SharedFlow<NavigationCommand> = _commands.asSharedFlow()

    override fun navTo(screen: Screen) {
        _commands.tryEmit(NavigationCommand.NavTo(screen))
    }

    override fun popBack(vararg previousStackAttr: Pair<String, Any?>) {
        _commands.tryEmit(NavigationCommand.PopBack(previousStackAttr.toList()))
    }

    override fun replaceTo(screen: Screen) {
        _commands.tryEmit(NavigationCommand.ReplaceTo(screen))
    }
}
