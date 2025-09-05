package io.github.stslex.workeeper.core.ui.navigation

import androidx.compose.runtime.Stable
import androidx.navigation.NavHostController

@Stable
interface NavigatorHolder {

    val navigator: NavHostController
}