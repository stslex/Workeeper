package io.github.stslex.workeeper.navigation

import androidx.navigation.NavHostController
import dagger.hilt.android.scopes.ActivityRetainedScoped
import javax.inject.Inject

@ActivityRetainedScoped
class NavigationHolderImpl @Inject constructor() : NavigationHolderController {

    @Volatile
    private var _navigator: NavHostController? = null

    override val navController: NavHostController
        get() = requireNotNull(_navigator) {
            "NavHostController is not set. Make sure to call rememberNavHostControllerHolder() in a composable scope."
        }

    @Synchronized
    override fun produce(navController: NavHostController) {
        _navigator = navController
    }

    @Synchronized
    override fun clear() {
        _navigator = null
    }
}
