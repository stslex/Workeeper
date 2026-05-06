package io.github.stslex.workeeper.navigation

import androidx.compose.runtime.Stable
import androidx.navigation.NavHostController
import io.github.stslex.workeeper.core.ui.navigation.NavigatorHolder

@Stable
interface NavigationHolderController : NavigatorHolder {

    fun produce(controller: NavHostController)

    fun removeController(controller: NavHostController)
}
