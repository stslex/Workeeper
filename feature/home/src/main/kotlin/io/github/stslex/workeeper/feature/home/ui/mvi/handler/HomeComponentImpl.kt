package io.github.stslex.workeeper.feature.home.ui.mvi.handler

import com.arkivanov.decompose.ComponentContext
import io.github.stslex.workeeper.core.ui.navigation.DialogConfig
import io.github.stslex.workeeper.core.ui.navigation.Router
import io.github.stslex.workeeper.feature.home.ui.mvi.store.HomeHandlerStore
import io.github.stslex.workeeper.feature.home.ui.mvi.store.HomeStore.Action.Navigation

internal class HomeComponentImpl(
    private val router: Router,
) : HomeComponent, ComponentContext by router {

    override fun HomeHandlerStore.invoke(action: Navigation) {
        when (action) {
            Navigation.CreateExerciseDialog -> router.navTo(DialogConfig.Exercise)
        }
    }

}