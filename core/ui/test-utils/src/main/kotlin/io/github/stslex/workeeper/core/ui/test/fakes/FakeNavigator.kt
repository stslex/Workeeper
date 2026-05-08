// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.core.ui.test.fakes

import io.github.stslex.workeeper.core.ui.navigation.Navigator
import io.github.stslex.workeeper.core.ui.navigation.Screen
import java.util.concurrent.CopyOnWriteArrayList
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Records every [Navigator] command without dispatching it. Production wires
 * `NavigatorEventBus` (lives in `app/app`) which is not on feature-test classpaths;
 * tests at feature scope use this fake instead so the Hilt graph for any feature whose
 * handlers inject [Navigator] resolves cleanly.
 *
 * Tests that need to assert "navigated to X" can read [commands] after the action.
 * F-02 doesn't, since the assertion is on database state.
 */
@Singleton
class FakeNavigator @Inject constructor() : Navigator {

    private val _commands: MutableList<Command> = CopyOnWriteArrayList()
    val commands: List<Command> get() = _commands.toList()

    override fun navTo(screen: Screen) {
        _commands += Command.NavTo(screen)
    }

    override fun popBack(vararg previousStackAttr: Pair<String, Any?>) {
        _commands += Command.PopBack(previousStackAttr.toList())
    }

    override fun replaceTo(screen: Screen) {
        _commands += Command.ReplaceTo(screen)
    }

    fun reset() {
        _commands.clear()
    }

    sealed interface Command {
        data class NavTo(val screen: Screen) : Command
        data class ReplaceTo(val screen: Screen) : Command
        data class PopBack(val attrs: List<Pair<String, Any?>>) : Command
    }
}
