package io.github.stslex.workeeper.core.ui.navigation

import androidx.compose.runtime.Stable

@Stable
interface Navigator {

    fun navTo(screen: Screen)

    fun popBack()

}
