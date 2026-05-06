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
import io.github.stslex.workeeper.navigation.NavigationHolderProducer
import java.lang.ref.WeakReference

@Stable
class NavHostControllerWrapper private constructor(
    val bottomBarDestination: State<BottomBarItem?>,
) {

    companion object {

        private var _navController: WeakReference<NavHostController>? = null

        @Composable
        fun rememberNavHostControllerHolder(producer: NavigationHolderProducer): NavHostControllerWrapper {
            val controller = rememberNavController()
            val bottomBarDestination = remember {
                mutableStateOf<BottomBarItem?>(BottomBarItem.HOME)
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

            _navController = WeakReference(controller)
            return remember(controller) {
                producer.produce(controller)
                NavHostControllerWrapper(bottomBarDestination)
            }
        }
    }
}
