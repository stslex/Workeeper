package io.github.stslex.workeeper.dialog.exercise.ui.mvi

import io.github.stslex.workeeper.core.ui.navigation.DialogComponent
import io.github.stslex.workeeper.core.ui.navigation.DialogRouter

interface ExerciseDialogComponent : DialogComponent {

    companion object {

        fun create(router: DialogRouter): ExerciseDialogComponent = ExerciseDialogComponentImpl(router)
    }
}

