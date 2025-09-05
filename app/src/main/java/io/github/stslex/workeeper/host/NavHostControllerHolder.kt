package io.github.stslex.workeeper.host

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.remember
import androidx.navigation.NavHostController
import androidx.navigation.compose.rememberNavController
import io.github.stslex.workeeper.core.ui.navigation.NavigatorHolder

@Stable
class NavHostControllerHolder private constructor(
    override val navigator: NavHostController,
) : NavigatorHolder {

    companion object {

        @Composable
        fun rememberNavHostControllerHolder(): NavHostControllerHolder {
            val controller = rememberNavController()
            return remember(controller) {
                NavHostControllerHolder(
                    navigator = controller,
                )
            }
        }
    }
}