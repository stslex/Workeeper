import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import io.github.stslex.workeeper.core.ui.kit.theme.AppTheme
import io.github.stslex.workeeper.host.AppNavigationHost
import io.github.stslex.workeeper.host.NavHostControllerHolder.Companion.rememberNavHostControllerHolder

@Composable
fun App() {
    AppTheme {
        val navigatorHolder = rememberNavHostControllerHolder()
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background),
        ) {
            AppNavigationHost(
                navigatorHolder = navigatorHolder
            )
        }
    }
}