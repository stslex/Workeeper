package io.github.stslex.workeeper.host

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.arkivanov.decompose.extensions.compose.stack.Children
import com.arkivanov.decompose.extensions.compose.stack.animation.stackAnimation
import io.github.stslex.workeeper.home.HomeComponent

@Composable
internal fun AppNavigationHost(
    rootComponent: RootComponent,
    modifier: Modifier = Modifier,
) {
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

@Composable
fun HomeScreen(
    component: HomeComponent
) {

    Text("HOME COMPONENT starts")
}