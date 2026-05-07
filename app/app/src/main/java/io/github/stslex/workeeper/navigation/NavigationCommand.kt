// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.navigation

import io.github.stslex.workeeper.core.ui.navigation.Screen

sealed interface NavigationCommand {

    data class NavTo(val screen: Screen) : NavigationCommand

    data class PopBack(val previousStackAttr: List<Pair<String, Any?>>) : NavigationCommand

    data class ReplaceTo(val screen: Screen) : NavigationCommand
}
