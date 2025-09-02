import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import io.github.stslex.workeeper.host.AppNavigationHost
import io.github.stslex.workeeper.navigation.RootComponent
import io.github.stslex.workeeper.core.ui.kit.theme.AppTheme

@Composable
fun App(rootComponent: RootComponent) {
    AppTheme {
        Scaffold(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background),
        ) { paddingValues ->
            AppNavigationHost(
                modifier = Modifier.padding(
                    bottom = paddingValues.calculateBottomPadding()
                ),
                rootComponent = rootComponent
            )
        }
    }
}