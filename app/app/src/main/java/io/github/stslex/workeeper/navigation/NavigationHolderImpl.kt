package io.github.stslex.workeeper.navigation

import androidx.navigation.NavHostController
import io.github.stslex.workeeper.core.ui.navigation.NavigatorHolder
import java.lang.ref.WeakReference

class NavigationHolderImpl : NavigatorHolder, NavigationHolderProducer {

    override val navigator: NavHostController
        get() = requireNotNull(_navigator?.get()) {
            "NavHostController is not set. Make sure to call rememberNavHostControllerHolder() in a composable scope."
        }

    @Synchronized
    override fun produce(navController: NavHostController) {
        _navigator = WeakReference(navController)
    }

    companion object {

        @Volatile
        private var _navigator: WeakReference<NavHostController>? = null
    }
}
