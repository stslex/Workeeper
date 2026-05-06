package io.github.stslex.workeeper.host

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.navigation.NavController.OnDestinationChangedListener
import io.github.stslex.workeeper.bottom_app_bar.BottomBarItem
import io.github.stslex.workeeper.core.ui.navigation.NavigatorHolder

@Stable
class BottomBarNavigationListener private constructor(
    val bottomBarDestination: State<BottomBarItem?>,
) {

    companion object {

        @Composable
        fun rememberBottomBarNavigationListener(holder: NavigatorHolder): BottomBarNavigationListener {
            val bottomBarDestination = remember {
                mutableStateOf<BottomBarItem?>(BottomBarItem.HOME)
            }
            DisposableEffect(holder.navController) {
                val listener = OnDestinationChangedListener { _, destination, _ ->
                    bottomBarDestination.value = destination.route?.let(BottomBarItem::getByRoute)
                }
                holder.navController.addOnDestinationChangedListener(listener)
                onDispose {
                    holder.navController.removeOnDestinationChangedListener(listener)
                }
            }

            return remember(holder.navController) {
                BottomBarNavigationListener(bottomBarDestination)
            }
        }
    }
}
