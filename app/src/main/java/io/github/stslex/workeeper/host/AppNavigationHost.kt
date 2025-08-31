package io.github.stslex.workeeper.host

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.arkivanov.decompose.extensions.compose.stack.Children
import com.arkivanov.decompose.extensions.compose.stack.animation.stackAnimation
import io.github.stslex.workeeper.feature.home.ui.HomeScreen

@Composable
internal fun AppNavigationHost(
    rootComponent: RootComponent,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier.fillMaxSize()) {
        Children(
            stack = rootComponent.stack,
            modifier = modifier.fillMaxSize(),
            animation = stackAnimation(),
        ) { created ->
            when (val instance = created.instance) {
                is RootComponent.Child.Home -> HomeScreen(instance.component)
            }
        }
    }
}
