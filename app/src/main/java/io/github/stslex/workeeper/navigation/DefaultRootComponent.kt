package io.github.stslex.workeeper.navigation

import com.arkivanov.decompose.ComponentContext
import com.arkivanov.decompose.DelicateDecomposeApi
import com.arkivanov.decompose.router.slot.ChildSlot
import com.arkivanov.decompose.router.slot.SlotNavigation
import com.arkivanov.decompose.router.slot.activate
import com.arkivanov.decompose.router.slot.childSlot
import com.arkivanov.decompose.router.slot.dismiss
import com.arkivanov.decompose.router.stack.ChildStack
import com.arkivanov.decompose.router.stack.StackNavigation
import com.arkivanov.decompose.router.stack.childStack
import com.arkivanov.decompose.router.stack.navigate
import com.arkivanov.decompose.router.stack.pop
import com.arkivanov.decompose.value.Value
import io.github.stslex.workeeper.core.ui.navigation.Config
import io.github.stslex.workeeper.core.ui.navigation.DialogConfig
import io.github.stslex.workeeper.core.ui.navigation.DialogRouter
import io.github.stslex.workeeper.core.ui.navigation.Router
import io.github.stslex.workeeper.feature.exercise.ui.mvi.handler.ExerciseComponent
import io.github.stslex.workeeper.feature.home.ui.mvi.handler.HomeComponent
import io.github.stslex.workeeper.navigation.RootComponent.Child
import io.github.stslex.workeeper.navigation.RootComponent.Child.Home
import io.github.stslex.workeeper.navigation.RootComponent.DialogChild

class DefaultRootComponent(
    componentContext: ComponentContext
) : RootComponent, ComponentContext by componentContext {

    private val navigation = StackNavigation<Config>()
    private val dialogNavigation = SlotNavigation<DialogConfig>()
    private val _stack = childStack(
        source = navigation,
        serializer = Config.serializer(),
        childFactory = ::child,
        handleBackButton = true,
        initialConfiguration = Config.Home
    )

    override val stack: Value<ChildStack<Config, Child>> = _stack

    override val dialogStack: Value<ChildSlot<DialogConfig, DialogChild>> = childSlot(
        source = dialogNavigation,
        serializer = DialogConfig.serializer(),
        handleBackButton = true,
    ) { config, context ->
        TODO("not implemented")
    }

    override fun onConfigChanged(block: (Config) -> Unit) = stack.subscribe {
        block(it.active.configuration)
    }

    private fun child(
        config: Config,
        context: ComponentContext
    ): Child = when (config) {
        is Config.Home -> Home(HomeComponent.create(context.router))
        is Config.Exercise -> Child.Exercise(ExerciseComponent.create(context.router, config.data))
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

    @OptIn(DelicateDecomposeApi::class)
    private fun navigateTo(config: DialogConfig) {
        dialogNavigation.activate(config)
    }

    private val ComponentContext.dialogRouter: DialogRouter get() = DialogRouterImpl(this)

    private val ComponentContext.router: Router get() = RouterImpl(this)

    private inner class RouterImpl(
        context: ComponentContext
    ) : Router, BaseRouter(context) {

        override fun popBack() {
            navigation.pop()
        }
    }

    private inner class DialogRouterImpl(context: ComponentContext) : DialogRouter, BaseRouter(context) {

        override fun popBack() {
            dialogNavigation.dismiss()
        }
    }

    private abstract inner class BaseRouter(
        context: ComponentContext,
    ) : Router, ComponentContext by context {

        override fun navTo(config: Config) {
            navigateTo(config)
        }

        override fun navTo(config: DialogConfig) {
            navigateTo(config)
        }
    }
}
