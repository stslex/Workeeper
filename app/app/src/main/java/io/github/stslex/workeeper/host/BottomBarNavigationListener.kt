// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.host

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.MutableIntState
import androidx.compose.runtime.Stable
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.navigation.NavController.OnDestinationChangedListener
import io.github.stslex.workeeper.bottom_app_bar.BottomBarItem
import io.github.stslex.workeeper.core.ui.navigation.NavigatorHolder

@Stable
class BottomBarNavigationListener private constructor(
    val bottomBarDestination: State<BottomBarItem?>,
    /**
     * The index the nav pill rests on — **latched, never null.**
     *
     * [bottomBarDestination] goes null the moment a non-bottom-bar route is pushed, and the bar is
     * hidden on that signal. But `AnimatedVisibility` keeps composing its content for the whole
     * exit animation, so a bar reading its selection off the nullable state would see `null`,
     * resolve it to "no index", and slide the pill back to the first item **while the bar is
     * animating away** — a visible snap on every push off a bottom-bar destination, and one no
     * golden could catch (§27: a golden gates one static frame).
     *
     * Latching it here rather than in `App.kt` keeps the fix out of the composition phase
     * entirely: it is written from the same `OnDestinationChangedListener` callback that writes
     * [bottomBarDestination], so nothing writes snapshot state while composing.
     */
    val selectedIndex: State<Int>,
) {

    companion object {

        @Composable
        fun rememberBottomBarNavigationListener(holder: NavigatorHolder): BottomBarNavigationListener {
            val navController = holder.navController
            val bottomBarDestination = remember {
                mutableStateOf<BottomBarItem?>(BottomBarItem.HOME)
            }
            val selectedIndex: MutableIntState = remember {
                mutableIntStateOf(BottomBarItem.entries.indexOf(BottomBarItem.HOME))
            }
            DisposableEffect(navController) {
                val listener = OnDestinationChangedListener { _, destination, _ ->
                    val item = destination.route?.let(BottomBarItem::getByRoute)
                    bottomBarDestination.value = item
                    if (item != null) {
                        selectedIndex.intValue = BottomBarItem.entries.indexOf(item)
                    }
                }
                navController.addOnDestinationChangedListener(listener)
                onDispose {
                    navController.removeOnDestinationChangedListener(listener)
                }
            }

            return remember(navController) {
                BottomBarNavigationListener(bottomBarDestination, selectedIndex)
            }
        }
    }
}
