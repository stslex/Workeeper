package io.github.stslex.workeeper.host

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.navigation.compose.NavHost
import io.github.stslex.workeeper.core.ui.navigation.Screen
import io.github.stslex.workeeper.feature.exercise.ui.exerciseGraph
import io.github.stslex.workeeper.feature.exercise.ui.exerciseNewGraph
import io.github.stslex.workeeper.feature.home.ui.homeGraph
import io.github.stslex.workeeper.navigation.NavigatorImpl

@Composable
internal fun AppNavigationHost(
    navigatorHolder: NavHostControllerHolder,
    modifier: Modifier = Modifier,
) {
    val navigator = remember(navigatorHolder) {
        NavigatorImpl(navigatorHolder)
    }
    NavHost(
        modifier = modifier,
        navController = navigatorHolder.navigator,
        startDestination = Screen.Home
    ) {
        homeGraph(navigator)
        exerciseGraph(navigator)
        exerciseNewGraph(navigator)
    }
}
