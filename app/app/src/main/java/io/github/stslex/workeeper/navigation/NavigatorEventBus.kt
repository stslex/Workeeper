// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.navigation

import io.github.stslex.workeeper.core.core.logger.Log
import io.github.stslex.workeeper.core.ui.navigation.NavCommand
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

    private val _commands = MutableSharedFlow<NavCommand>(
        extraBufferCapacity = 64,
    )
    override val commands: SharedFlow<NavCommand> = _commands.asSharedFlow()

    override fun navTo(screen: Screen) {
        consume(NavCommand.NavTo(screen))
    }

    override fun popBack(vararg previousStackAttr: Pair<String, Any?>) {
        consume(NavCommand.PopBack(previousStackAttr.toList()))
    }

    override fun replaceTo(screen: Screen) {
        consume(NavCommand.ReplaceTo(screen))
    }

    override fun restartApp() {
        consume(NavCommand.RestartApp)
    }

    private fun consume(command: NavCommand) {
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
