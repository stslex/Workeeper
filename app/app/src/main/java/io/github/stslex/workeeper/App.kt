import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
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
        Scaffold(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background),
            bottomBar = {
                AnimatedContent(
                    navigatorHolder.bottomBarDestination
                ) { targetItem ->
                    WorkeeperBottomAppBar(
                        selectedItem = targetItem,
                    ) {
                        navigator.navTo(it.screen)
                    }
                }
            }
        ) { paddingValues ->
            AppNavigationHost(
                modifier = Modifier
                    .padding(paddingValues),
                navigator = navigator
            )
        }
    }
}