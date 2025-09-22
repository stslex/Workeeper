package io.github.stslex.workeeper.feature.exercise.ui.mvi.handler

import io.github.stslex.workeeper.core.ui.navigation.Navigator
import io.github.stslex.workeeper.feature.exercise.ui.mvi.store.ExerciseStore.Action

internal class ExerciseComponentImpl(
    private val navigator: Navigator,
    override val uuid: String?,
    override val trainingUuid: String?
) : ExerciseComponent {

    override fun invoke(
        action: Action.Navigation
    ) {
        when (action) {
            Action.Navigation.Back -> navigator.popBack()
        }
    }
}
