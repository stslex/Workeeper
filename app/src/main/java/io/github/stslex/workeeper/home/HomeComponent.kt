package io.github.stslex.workeeper.home

import com.arkivanov.decompose.ComponentContext
import io.github.stslex.workeeper.core.ui.navigation.Component
import io.github.stslex.workeeper.core.ui.navigation.Router

interface HomeComponent : Component {

    companion object {

        fun create(router: Router): HomeComponent = HomeComponentImpl(router)
    }
}

internal class HomeComponentImpl(
    private val router: Router,
) : HomeComponent, ComponentContext by router