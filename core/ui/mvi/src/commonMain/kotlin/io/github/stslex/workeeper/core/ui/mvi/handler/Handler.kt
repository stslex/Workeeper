package io.github.stslex.workeeper.core.ui.mvi.handler

import io.github.stslex.workeeper.core.ui.mvi.Store.Action

fun interface Handler<in A : Action> {

    operator fun invoke(action: A)
}
