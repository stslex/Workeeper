package io.github.stslex.workeeper.navigation

import androidx.navigation.NavHostController
import io.github.stslex.workeeper.core.ui.navigation.NavigatorHolder

interface NavigationHolderController : NavigatorHolder {

    fun produce(navController: NavHostController)

    fun clear()
}
