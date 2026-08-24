// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.host

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableIntState
import androidx.compose.runtime.Stable
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import io.github.stslex.workeeper.bottom_app_bar.BottomBarItem
import io.github.stslex.workeeper.core.ui.navigation.NavigatorHolder

@Stable
class BottomBarNavigationListener private constructor(
    val bottomBarDestination: State<BottomBarItem?>,
    /**
     * The index the nav pill rests on — **latched, never null.**
     *
     * [bottomBarDestination] goes null the moment a non-bottom-bar screen is pushed, and the bar is
     * hidden on that signal. But `AnimatedVisibility` keeps composing its content for the whole
     * exit animation, so a bar reading its selection off the nullable state would see `null`,
     * resolve it to "no index", and slide the pill back to the first item **while the bar is
     * animating away** — a visible snap on every push off a bottom-bar destination, and one no
     * golden could catch (§27: a golden gates one static frame).
     *
     * Latching it in the collector rather than in `App.kt` keeps the fix out of the composition
     * phase entirely: both states are written from the same `snapshotFlow` collector that replaced
     * the Nav2 `OnDestinationChangedListener`, so nothing writes snapshot state while composing.
     */
    val selectedIndex: State<Int>,
) {

    companion object {

        @Composable
        fun rememberBottomBarNavigationListener(holder: NavigatorHolder): BottomBarNavigationListener {
            val bottomBarDestination = remember {
                mutableStateOf<BottomBarItem?>(BottomBarItem.HOME)
            }
            val selectedIndex: MutableIntState = remember {
                mutableIntStateOf(BottomBarItem.entries.indexOf(BottomBarItem.HOME))
            }
            // snapshotFlow emits the CURRENT value on first collection — the same "fires for the
            // initial destination too" semantic the Nav2 listener had from the controller
            // replaying the current destination on registration.
            LaunchedEffect(holder) {
                snapshotFlow { holder.currentScreen }
                    .collect { screen ->
                        val item = screen?.let(BottomBarItem::getByScreen)
                        bottomBarDestination.value = item
                        if (item != null) {
                            selectedIndex.intValue = BottomBarItem.entries.indexOf(item)
                        }
                    }
            }

            return remember(holder) {
                BottomBarNavigationListener(bottomBarDestination, selectedIndex)
            }
        }
    }
}
