package io.github.stslex.workeeper.navigation

import androidx.compose.runtime.Stable
import androidx.navigation.NavHostController
import dagger.hilt.android.scopes.ActivityRetainedScoped
import javax.inject.Inject

@ActivityRetainedScoped
@Stable
class NavigationHolderImpl @Inject constructor() : NavigationHolderController {

    @Volatile
    private var _navController: NavHostController? = null

    override val navController: NavHostController
        get() = requireNotNull(_navController) {
            "NavHostController is not set. Make sure to call rememberNavHostControllerHolder() in a composable scope."
        }

    @Synchronized
    override fun produce(controller: NavHostController) {
        _navController = controller
    }

    @Synchronized
    override fun removeController(controller: NavHostController) {
        if (_navController === controller) {
            _navController = null
        }
    }
}
