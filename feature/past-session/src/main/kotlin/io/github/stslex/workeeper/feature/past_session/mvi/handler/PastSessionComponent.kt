// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.past_session.mvi.handler

import io.github.stslex.workeeper.core.ui.navigation.Component
import io.github.stslex.workeeper.core.ui.navigation.Navigator
import io.github.stslex.workeeper.core.ui.navigation.Screen

abstract class PastSessionComponent(
    data: Screen.PastSession,
) : Component<Screen.PastSession>(data) {

    companion object {

        fun create(
            navigator: Navigator,
            screen: Screen.PastSession,
        ): PastSessionComponent = NavigationHandler(navigator = navigator, data = screen)
    }
}
