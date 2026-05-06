package io.github.stslex.workeeper.navigation

import androidx.compose.runtime.Stable
import androidx.navigation.NavHostController
import dagger.hilt.android.scopes.ActivityRetainedScoped
import javax.inject.Inject

@ActivityRetainedScoped
@Stable
class NavigationHolderImpl @Inject constructor() : NavigationHolderController {

    @Volatile
    private var _navigator: NavHostController? = null

    override val navController: NavHostController
        get() = requireNotNull(_navigator) {
            "NavHostController is not set. Make sure to call rememberNavHostControllerHolder() in a composable scope."
        }

    @Synchronized
    override fun produce(controller: NavHostController) {
        _navigator = controller
    }

    @Synchronized
    override fun removeController(controller: NavHostController) {
        if (_navigator === controller) {
            _navigator = null
        }
    }
}
