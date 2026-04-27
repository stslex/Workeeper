// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.home.mvi.handler

import io.github.stslex.workeeper.core.ui.navigation.Component
import io.github.stslex.workeeper.core.ui.navigation.Navigator
import io.github.stslex.workeeper.core.ui.navigation.Screen.BottomBar.Home

abstract class HomeComponent : Component<Home>(Home) {

    companion object {

        fun create(navigator: Navigator): HomeComponent = NavigationHandler(navigator)
    }
}
