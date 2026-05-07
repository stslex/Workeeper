// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.navigation

import io.github.stslex.workeeper.core.core.logger.Log
import io.github.stslex.workeeper.core.ui.navigation.Navigator
import io.github.stslex.workeeper.core.ui.navigation.Screen
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class NavigatorEventBus @Inject constructor() : Navigator, NavigatorReceiver {

    private val log = Log.tag(TAG)

    private val _commands = MutableSharedFlow<NavigationCommand>(
        extraBufferCapacity = 64,
    )
    override val commands: SharedFlow<NavigationCommand> = _commands.asSharedFlow()

    override fun navTo(screen: Screen) {
        consume(NavigationCommand.NavTo(screen))
    }

    override fun popBack(vararg previousStackAttr: Pair<String, Any?>) {
        consume(NavigationCommand.PopBack(previousStackAttr.toList()))
    }

    override fun replaceTo(screen: Screen) {
        consume(NavigationCommand.ReplaceTo(screen))
    }

    private fun consume(command: NavigationCommand) {
        log.d { "Processing navigation command: $command" }
        _commands.tryEmit(command).also { emitted ->
            if (emitted.not()) {
                log.w { "Failed to emit navigation command: $command" }
            }
        }
    }

    companion object {

        private const val TAG = "NavigatorEventBus"
    }
}
