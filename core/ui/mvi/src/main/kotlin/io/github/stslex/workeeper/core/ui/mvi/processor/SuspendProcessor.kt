package io.github.stslex.workeeper.core.ui.mvi.processor

import io.github.stslex.workeeper.core.ui.mvi.Store

fun interface SuspendProcessor<E : Store.Event> {

    suspend operator fun invoke(eff: E)
}