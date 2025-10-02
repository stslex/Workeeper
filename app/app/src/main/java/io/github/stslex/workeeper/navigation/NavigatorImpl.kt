package io.github.stslex.workeeper.navigation

import android.annotation.SuppressLint
import androidx.compose.runtime.Stable
import io.github.stslex.workeeper.core.core.logger.Log
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

    @SuppressLint("RestrictedApi")
    override fun navTo(screen: Screen) {
        logger.d("navTo $screen")
        try {
            val currentRoute = holder.navigator.currentDestination?.route ?: return
            navController.navigate(screen) {
                if (screen.isSingleTop) {
                    popUpTo(currentRoute) {
                        inclusive = true
                        saveState = true
                    }
                    launchSingleTop = true
                }
            }
        } catch (exception: Exception) {
            logger.e(exception, "screen: $screen")
        }
    }

    override fun popBack() {
        logger.d("popBack")
        navController.popBackStack()
    }

    companion object {

        private const val TAG = "NAVIGATION"

        private val logger = Log.tag(TAG)
    }
}
