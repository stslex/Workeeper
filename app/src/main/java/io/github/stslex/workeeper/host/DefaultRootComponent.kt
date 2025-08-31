package io.github.stslex.workeeper.host

import com.arkivanov.decompose.ComponentContext
import com.arkivanov.decompose.DelicateDecomposeApi
import com.arkivanov.decompose.router.stack.ChildStack
import com.arkivanov.decompose.router.stack.StackNavigation
import com.arkivanov.decompose.router.stack.childStack
import com.arkivanov.decompose.router.stack.navigate
import com.arkivanov.decompose.router.stack.pop
import com.arkivanov.decompose.value.Value
import io.github.stslex.workeeper.core.ui.navigation.Config
import io.github.stslex.workeeper.core.ui.navigation.Router
import io.github.stslex.workeeper.feature.home.ui.mvi.handler.HomeComponent
import io.github.stslex.workeeper.host.RootComponent.Child

class DefaultRootComponent(
    componentContext: ComponentContext
) : RootComponent, ComponentContext by componentContext {

    private val navigation = StackNavigation<Config>()

    private val _stack = childStack(
        source = navigation,
        serializer = Config.serializer(),
        childFactory = ::child,
        handleBackButton = true,
        initialConfiguration = Config.Home
    )

    override val stack: Value<ChildStack<Config, Child>> = _stack

    override fun onConfigChanged(block: (Config) -> Unit) = stack.subscribe {
        block(it.active.configuration)
    }

    private fun child(
        config: Config,
        context: ComponentContext
    ): Child = when (config) {
        is Config.Home -> Child.Home(HomeComponent.create(context.router))
    }

    @OptIn(DelicateDecomposeApi::class)
    private fun navigateTo(config: Config) {
        navigation.navigate { currentStack ->
            if (config.isBackAllow) {
                currentStack + config
            } else {
                listOf(config)
            }
        }
    }

    private fun popBack() {
        navigation.pop()
    }

    private val ComponentContext.router: Router
        get() = object : Router, ComponentContext by this {

            override fun navTo(config: Config) {
                navigateTo(config)
            }

            override fun popBack() {
                this@DefaultRootComponent.popBack()
            }
        }
}
