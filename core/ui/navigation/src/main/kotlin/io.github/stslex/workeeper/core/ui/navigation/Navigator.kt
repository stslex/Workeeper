package io.github.stslex.workeeper.core.ui.navigation

import androidx.compose.runtime.Stable
import androidx.navigation.NavHostController

@Stable
interface Navigator {

    val navController: NavHostController

    fun navTo(screen: Screen)

    fun popBack()
}
