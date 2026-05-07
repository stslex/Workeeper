// SPDX-License-Identifier: GPL-3.0-only
// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.navigation.NavController
import io.github.stslex.workeeper.core.core.logger.Log
import io.github.stslex.workeeper.core.ui.mvi.performance.PerformanceMetricsRecorder
import io.github.stslex.workeeper.core.ui.mvi.performance.RecordAction
import io.github.stslex.workeeper.core.ui.navigation.NavigatorHolder
import io.github.stslex.workeeper.core.ui.navigation.Screen

object NavigatorExt {

    private const val TAG = "NAVIGATION"

    private val logger = Log.tag(TAG)

    @Composable
    fun NavigationEventBusSetup(
        navigatorHolder: NavigatorHolder,
        navigator: NavigatorReceiver,
    ) {
        val navController = navigatorHolder.navController
        LaunchedEffect(navController) {
            navigator.commands.collect { command ->
                processCommand(navController, command)
            }
        }
    }

    private fun processCommand(
        navController: NavController,
        command: NavigationCommand,
    ) {
        logger.i { "Processing navigation command: $command" }
        when (command) {
            is NavigationCommand.NavTo -> navTo(navController, command.screen)
            is NavigationCommand.PopBack -> popBack(
                navController,
                command.previousStackAttr,
            )

            is NavigationCommand.ReplaceTo -> replaceTo(navController, command.screen)
        }
    }

    private fun navTo(
        navController: NavController,
        screen: Screen,
    ) {
        logger.d("navTo $screen")
        try {
            val currentRoute = navController.currentDestination?.route ?: return
            PerformanceMetricsRecorder.process(RecordAction.Navigation.NavTo(screen::class))
            navController.navigate(screen) {
                if (screen.isSingleTop) {
                    popUpTo(currentRoute) {
                        inclusive = true
                        saveState = true
                    }
                    launchSingleTop = true
                }
            }
        } catch (ignore: Exception) {
            logger.e(ignore, "screen: $screen")
        }
    }

    private fun popBack(
        navController: NavController,
        previousStackAttr: List<Pair<String, Any?>>,
    ) {
        logger.d {
            val attrs = previousStackAttr.joinToString { "${it.first}=${it.second}" }
            "popBack($attrs)"
        }

        navController.previousBackStackEntry
            ?.savedStateHandle
            ?.let { saveHandle ->
                previousStackAttr.forEach { (key, value) -> saveHandle[key] = value }
            }
        navController.popBackStack()
    }

    private fun replaceTo(
        navController: NavController,
        screen: Screen,
    ) {
        logger.d("replaceTo $screen")
        try {
            val currentRoute = navController.currentDestination?.route ?: return
            PerformanceMetricsRecorder.process(RecordAction.Navigation.ReplaceTo(screen::class))
            navController.navigate(screen) {
                popUpTo(currentRoute) {
                    inclusive = true
                    saveState = false
                }
                launchSingleTop = true
            }
        } catch (ignore: Exception) {
            logger.e(ignore, "screen: $screen")
        }
    }
}
