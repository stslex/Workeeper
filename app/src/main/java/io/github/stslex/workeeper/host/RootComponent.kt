package io.github.stslex.workeeper.host

import com.arkivanov.decompose.Cancellation
import com.arkivanov.decompose.router.stack.ChildStack
import com.arkivanov.decompose.value.Value
import io.github.stslex.workeeper.core.ui.navigation.Config
import io.github.stslex.workeeper.feature.home.ui.mvi.handler.HomeComponent

interface RootComponent {

    val stack: Value<ChildStack<Config, Child>>

    fun onConfigChanged(block: (Config) -> Unit): Cancellation

    sealed interface Child {

        data class Home(val component: HomeComponent) : Child

    }

}
