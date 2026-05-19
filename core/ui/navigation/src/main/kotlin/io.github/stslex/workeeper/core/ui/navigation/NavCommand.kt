// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.core.ui.navigation

sealed interface NavCommand {
    data class NavTo(val screen: Screen) : NavCommand
    data class ReplaceTo(val screen: Screen) : NavCommand
    data class PopBack(val attrs: List<Pair<String, Any?>>) : NavCommand
    data object RestartApp : NavCommand
    data object OpenRecovery : NavCommand
}
