package io.github.stslex.workeeper.navigation

import androidx.compose.runtime.Stable
import io.github.stslex.workeeper.core.core.logger.Log
import io.github.stslex.workeeper.core.ui.navigation.Navigator
import io.github.stslex.workeeper.core.ui.navigation.NavigatorHolder
import io.github.stslex.workeeper.core.ui.navigation.Screen

@Stable
class NavigatorImpl(
    private val holder: NavigatorHolder
) : Navigator {

    override fun navTo(screen: Screen) {
        logger.d("navTo $screen")
        try {
            holder.navigator.navigate(screen)
        } catch (exception: Exception) {
            logger.e(exception, "screen: $screen")
        }
    }

    override fun popBack() {
        logger.d("popBack")
        holder.navigator.popBackStack()
    }

    companion object {

        private const val TAG = "NAVIGATION"

        private val logger = Log.tag(TAG)
    }
}