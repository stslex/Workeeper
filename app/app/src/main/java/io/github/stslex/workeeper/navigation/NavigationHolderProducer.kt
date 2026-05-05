package io.github.stslex.workeeper.navigation

import androidx.navigation.NavHostController

interface NavigationHolderProducer {

    fun produce(navController: NavHostController)
}
