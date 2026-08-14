// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.navigation

import android.content.Context
import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.NavController
import io.github.stslex.workeeper.core.core.logger.Log
import io.github.stslex.workeeper.core.ui.mvi.performance.PerformanceMetricsRecorder
import io.github.stslex.workeeper.core.ui.mvi.performance.RecordAction
import io.github.stslex.workeeper.core.ui.navigation.NavCommand
import io.github.stslex.workeeper.core.ui.navigation.NavigatorHolder
import io.github.stslex.workeeper.core.ui.navigation.Screen
import io.github.stslex.workeeper.feature.recovery.RecoveryActivity

object NavigatorExt {

    private const val TAG = "NAVIGATION"

    private val logger = Log.tag(TAG)

    @Composable
    fun NavigationEventBusSetup(
        navigatorHolder: NavigatorHolder,
        navigator: NavigatorReceiver,
    ) {
        val navController = navigatorHolder.navController
        val context = LocalContext.current
        LaunchedEffect(navController) {
            navigator.commands.collect { command ->
                processCommand(
                    navController = navController,
                    command = command,
                    context = context,
                )
            }
        }
    }

    private fun processCommand(
        navController: NavController,
        command: NavCommand,
        context: Context,
    ) {
        logger.i { "Processing navigation command: $command" }
        when (command) {
            is NavCommand.NavTo -> navTo(navController, command.screen)
            is NavCommand.PopBack -> popBack(navController, command.attrs)
            is NavCommand.PopBackWithResult -> popBackWithResult(
                navController = navController,
                key = command.key,
                result = command.result,
            )

            is NavCommand.ReplaceTo -> replaceTo(navController, command.screen)
            NavCommand.OpenRecovery -> openRecovery(context)
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

    /**
     * The Nav2 half of [io.github.stslex.workeeper.core.ui.navigation.ScreenWithResult]:
     * write the result onto the entry underneath, then pop.
     *
     * Order matters and is the same as [popBack]'s — the value has to be on the previous
     * entry's handle *before* the pop, or the consumer recomposes on arrival with nothing
     * there and the result is lost. This is the only place the untyped shape touches the
     * navigation library; both sides of it are typed off the destination.
     */
    private fun popBackWithResult(
        navController: NavController,
        key: String,
        result: Any,
    ) {
        logger.d { "popBackWithResult($key=$result)" }

        navController.previousBackStackEntry
            ?.savedStateHandle
            ?.set(key, result)
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

    private fun openRecovery(context: Context) {
        val intent = Intent(context, RecoveryActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    }
}
