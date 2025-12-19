package io.github.stslex.workeeper.core.ui.navigation

import androidx.compose.runtime.Stable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.navigation.NavHostController

@Stable
interface Navigator {

    val navController: NavHostController

    fun navTo(screen: Screen)

    fun popBack()
}

val LocalNavigator = staticCompositionLocalOf<Navigator> {
    error("No Navigator provided")
}
