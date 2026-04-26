// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.settings.mvi.handler

import io.github.stslex.workeeper.core.ui.navigation.Component
import io.github.stslex.workeeper.core.ui.navigation.Navigator
import io.github.stslex.workeeper.core.ui.navigation.Screen.Archive

abstract class ArchiveComponent : Component<Archive>(Archive) {

    companion object {

        fun create(
            navigator: Navigator,
        ): ArchiveComponent = ArchiveNavigationHandler(
            navigator = navigator,
        )
    }
}
