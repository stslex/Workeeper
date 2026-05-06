// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.navigation

import androidx.compose.runtime.Stable
import io.github.stslex.workeeper.core.core.logger.Log
import io.github.stslex.workeeper.core.ui.mvi.performance.PerformanceMetricsRecorder
import io.github.stslex.workeeper.core.ui.mvi.performance.RecordAction
import io.github.stslex.workeeper.core.ui.navigation.NavigatorHolder
import io.github.stslex.workeeper.core.ui.navigation.NavigatorStack
import io.github.stslex.workeeper.core.ui.navigation.SaveHandlerAttr
import io.github.stslex.workeeper.core.ui.navigation.Screen
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject
import javax.inject.Singleton

@Stable
@Singleton
class NavigatorImpl @Inject internal constructor(
    private val holder: NavigatorHolder,
) : AppNavigator, NavigatorStack {

    override val navController get() = holder.navigator

    override fun navTo(screen: Screen) {
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

    override fun popBack(vararg previousStackAttr: Pair<String, Any?>) {
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

    override fun replaceTo(screen: Screen) {
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

    override fun setCurrentStack(vararg stackAttr: SaveHandlerAttr<*>) {
        logger.d {
            val attrs = stackAttr.joinToString { "${it.key}=${it.defaultValue}" }
            "clearCurrentStack($attrs)"
        }
        val stateHandle = navController.currentBackStackEntry?.savedStateHandle
        stackAttr.forEach { attr ->
            stateHandle?.set(attr.key, attr.defaultValue)
        }
    }

    override fun <T : Any> subscribeToStackAttr(
        saveHandlerAttr: SaveHandlerAttr<T>,
    ): StateFlow<T?>? = navController.currentBackStackEntry
        ?.savedStateHandle
        ?.getStateFlow(saveHandlerAttr.key, saveHandlerAttr.defaultValue)

    companion object {

        private const val TAG = "NAVIGATION"

        private val logger = Log.tag(TAG)
    }
}
