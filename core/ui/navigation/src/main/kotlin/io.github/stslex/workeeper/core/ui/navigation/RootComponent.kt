// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.core.ui.navigation

import androidx.compose.runtime.staticCompositionLocalOf

interface RootComponent {

    fun createComponent(screen: Screen): Component<*>
}

val LocalRootComponent = staticCompositionLocalOf<RootComponent> {
    error("No RootComponent provided")
}
