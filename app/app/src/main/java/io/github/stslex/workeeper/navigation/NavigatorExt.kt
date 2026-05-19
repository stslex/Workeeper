// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.navigation

import android.app.Activity
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

            is NavCommand.ReplaceTo -> replaceTo(navController, command.screen)
            NavCommand.RestartApp -> restartApp(context)
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

    private fun restartApp(context: Context) {
        val intent = context.packageManager.getLaunchIntentForPackage(context.packageName)
            ?: error("No launch intent for package ${context.packageName}")
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        context.startActivity(intent)
        if (context is Activity) context.finishAffinity()
        Runtime.getRuntime().exit(0)
    }

    private fun openRecovery(context: Context) {
        val intent = Intent(context, RecoveryActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    }
}
