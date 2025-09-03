package io.github.stslex.workeeper.dialog.exercise.ui.mvi

import com.arkivanov.decompose.ComponentContext
import io.github.stslex.workeeper.core.ui.navigation.DialogRouter

internal class ExerciseDialogComponentImpl(
    private val router: DialogRouter
) : ExerciseDialogComponent, ComponentContext by router {

    override fun dismiss() {
        router.popBack()
    }
}