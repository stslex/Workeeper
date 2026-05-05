package io.github.stslex.workeeper.navigation

import android.annotation.SuppressLint
import androidx.navigation.NavHostController
import io.github.stslex.workeeper.core.ui.navigation.NavigatorHolder

class NavigationHolderImpl : NavigatorHolder, NavigationHolderProducer {

    override val navigator: NavHostController
        get() = requireNotNull(_navigator) {
            "NavHostController is not set. Make sure to call rememberNavHostControllerHolder() in a composable scope."
        }

    @Synchronized
    override fun produce(navController: NavHostController) {
        _navigator = navController
    }

    companion object {

        /**
         * Need to be static to avoid memory leaks, as NavHostController holds a reference to the Activity context.
         * Using @Volatile to ensure visibility of changes across threads,
         * as NavHostController can be accessed from different threads.
         * Suppressing the lint warning for static field leak,
         * as we are managing the lifecycle of NavHostController properly,
         * and it will be set and cleared in a composable scope.
         **/
        @SuppressLint("StaticFieldLeak")
        @Volatile
        private var _navigator: NavHostController? = null
    }
}
