package io.github.stslex.workeeper.navigation

import androidx.compose.runtime.Stable
import io.github.stslex.workeeper.core.core.logger.Log
import io.github.stslex.workeeper.core.ui.mvi.performance.PerformanceMetricsRecorder
import io.github.stslex.workeeper.core.ui.mvi.performance.RecordAction
import io.github.stslex.workeeper.core.ui.navigation.Navigator
import io.github.stslex.workeeper.core.ui.navigation.NavigatorHolder
import io.github.stslex.workeeper.core.ui.navigation.Screen
import javax.inject.Inject
import javax.inject.Singleton

@Stable
@Singleton
class NavigatorImpl @Inject constructor(
    private val holder: NavigatorHolder,
) : Navigator {

    override val navController get() = holder.navigator

    override fun navTo(screen: Screen) {
        logger.d("navTo $screen")
        try {
            val currentRoute = holder.navigator.currentDestination?.route ?: return
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

    override fun popBack() {
        logger.d("popBack")
        navController.popBackStack()
    }

    override fun replaceTo(screen: Screen) {
        logger.d("replaceTo $screen")
        try {
            val currentRoute = holder.navigator.currentDestination?.route ?: return
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

    companion object {

        private const val TAG = "NAVIGATION"

        private val logger = Log.tag(TAG)
    }
}
