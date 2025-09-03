package io.github.stslex.workeeper.feature.exercise.ui.mvi.handler

import io.github.stslex.workeeper.core.ui.mvi.handler.Handler
import io.github.stslex.workeeper.core.ui.navigation.Component
import io.github.stslex.workeeper.core.ui.navigation.Config.Exercise.Data
import io.github.stslex.workeeper.core.ui.navigation.Router
import io.github.stslex.workeeper.feature.exercise.ui.mvi.store.ExerciseStore.Action
import io.github.stslex.workeeper.feature.exercise.ui.mvi.store.ExerciseHandlerStore

interface ExerciseComponent : Component, Handler<Action.Navigation, ExerciseHandlerStore> {

    val data: Data

    companion object {

        fun create(router: Router, data: Data): ExerciseComponent = ExerciseComponentImpl(
            router = router,
            data = data
        )
    }
}

