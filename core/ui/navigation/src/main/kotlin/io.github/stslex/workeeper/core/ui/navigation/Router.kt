package io.github.stslex.workeeper.core.ui.navigation

import com.arkivanov.decompose.ComponentContext

interface Router : ComponentContext {

    fun navTo(config: Config)

    fun navTo(config: DialogConfig)

    fun popBack()

}

