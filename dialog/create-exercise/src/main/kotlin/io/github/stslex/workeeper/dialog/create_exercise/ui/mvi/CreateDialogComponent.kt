package io.github.stslex.workeeper.dialog.create_exercise.ui.mvi

import io.github.stslex.workeeper.core.ui.navigation.DialogComponent
import io.github.stslex.workeeper.core.ui.navigation.DialogRouter

interface CreateDialogComponent : DialogComponent {

    companion object {

        fun create(router: DialogRouter): CreateDialogComponent = ExerciseCreateDialogComponentImpl(router)
    }
}

