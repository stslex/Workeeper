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
     * The index the nav pill rests on — latched, never null: `AnimatedVisibility` keeps composing
     * the bar through its exit, and a null would snap the pill back to the first item.
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
            // snapshotFlow replays the current value, so the initial destination arrives too.
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
