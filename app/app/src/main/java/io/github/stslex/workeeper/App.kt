import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.zIndex
import io.github.stslex.workeeper.bottom_app_bar.WorkeeperBottomAppBar
import io.github.stslex.workeeper.core.ui.kit.theme.AppTheme
import io.github.stslex.workeeper.host.AppNavigationHost
import io.github.stslex.workeeper.host.NavHostControllerHolder.Companion.rememberNavHostControllerHolder
import io.github.stslex.workeeper.navigation.NavigatorImpl

@Composable
fun App() {
    AppTheme {
        val navigatorHolder = rememberNavHostControllerHolder()
        val navigator = remember(navigatorHolder) { NavigatorImpl(navigatorHolder) }
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background),
        ) {
            AnimatedVisibility(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .zIndex(1f),
                visible = navigatorHolder.bottomBarDestination.value != null,
            ) {
                WorkeeperBottomAppBar(
                    selectedItem = navigatorHolder.bottomBarDestination,
                ) {
                    navigator.navTo(it.screen)
                }
            }

            AppNavigationHost(
                modifier = Modifier,
                navigator = navigator,
            )
        }
    }
}
