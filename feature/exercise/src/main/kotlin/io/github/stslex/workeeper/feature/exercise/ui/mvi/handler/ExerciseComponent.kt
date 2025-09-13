package io.github.stslex.workeeper.feature.exercise.ui.mvi.handler

import io.github.stslex.workeeper.core.ui.mvi.handler.Handler
import io.github.stslex.workeeper.core.ui.navigation.Component
import io.github.stslex.workeeper.core.ui.navigation.Navigator
import io.github.stslex.workeeper.core.ui.navigation.Screen.Exercise.Data
import io.github.stslex.workeeper.feature.exercise.ui.mvi.store.ExerciseStore.Action

interface ExerciseComponent : Component, Handler<Action.Navigation> {

    val data: Data?

    companion object {

        fun create(
            navigator: Navigator,
            data: Data?
        ): ExerciseComponent = ExerciseComponentImpl(
            navigator = navigator,
            data = data
        )
    }
}

