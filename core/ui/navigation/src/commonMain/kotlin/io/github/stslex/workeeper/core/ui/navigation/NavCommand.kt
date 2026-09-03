// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.core.ui.navigation

sealed interface NavCommand {
    data class NavTo(val screen: Screen) : NavCommand
    data class ReplaceTo(val screen: Screen) : NavCommand
    data object PopBack : NavCommand

    /** Pop, handing [result] back to the destination underneath; [key] is a [NavResultKey]. */
    data class PopBackWithResult(val key: String, val result: Any) : NavCommand

    data object OpenRecovery : NavCommand
}
