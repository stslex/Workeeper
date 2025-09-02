package io.github.stslex.workeeper.dialog.create_exercise.ui.mvi

import com.arkivanov.decompose.ComponentContext
import io.github.stslex.workeeper.core.ui.navigation.DialogRouter

internal class ExerciseCreateDialogComponentImpl(
    private val router: DialogRouter
) : CreateDialogComponent, ComponentContext by router {

    override fun dismiss() {
        router.popBack()
    }
}