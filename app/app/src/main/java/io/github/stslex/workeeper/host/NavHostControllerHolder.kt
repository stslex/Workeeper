package io.github.stslex.workeeper.host

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.navigation.NavController.OnDestinationChangedListener
import androidx.navigation.NavHostController
import androidx.navigation.compose.rememberNavController
import io.github.stslex.workeeper.bottom_app_bar.BottomBarItem
import io.github.stslex.workeeper.core.ui.navigation.NavigatorHolder

@Stable
class NavHostControllerHolder private constructor(
    override val navigator: NavHostController,
    val bottomBarDestination: State<BottomBarItem?>
) : NavigatorHolder {

    companion object {

        @Composable
        fun rememberNavHostControllerHolder(): NavHostControllerHolder {
            val controller = rememberNavController()
            val bottomBarDestination = remember {
                mutableStateOf<BottomBarItem?>(BottomBarItem.CHARTS)
            }
            DisposableEffect(controller) {
                val listener = OnDestinationChangedListener { _, destination, _ ->
                    bottomBarDestination.value = destination.route?.let(BottomBarItem::getByRoute)
                }
                controller.addOnDestinationChangedListener(listener)
                onDispose {
                    controller.removeOnDestinationChangedListener(listener)
                }
            }
            return remember(controller) {
                NavHostControllerHolder(
                    navigator = controller,
                    bottomBarDestination = bottomBarDestination
                )
            }
        }
    }
}