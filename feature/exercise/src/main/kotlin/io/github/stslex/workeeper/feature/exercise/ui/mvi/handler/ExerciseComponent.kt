package io.github.stslex.workeeper.feature.exercise.ui.mvi.handler

import io.github.stslex.workeeper.core.ui.mvi.handler.Handler
import io.github.stslex.workeeper.core.ui.navigation.Component
import io.github.stslex.workeeper.core.ui.navigation.Navigator
import io.github.stslex.workeeper.feature.exercise.ui.mvi.store.ExerciseStore.Action

interface ExerciseComponent : Component, Handler<Action.Navigation> {

    val uuid: String?
    val trainingUuid: String?

    companion object {

        fun create(
            navigator: Navigator,
            uuid: String?,
            trainingUuid: String?,
        ): ExerciseComponent = ExerciseComponentImpl(
            navigator = navigator,
            uuid = uuid,
            trainingUuid = trainingUuid,
        )
    }
}
