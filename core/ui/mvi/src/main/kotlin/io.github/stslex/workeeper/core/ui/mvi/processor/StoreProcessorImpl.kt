package io.github.stslex.workeeper.core.ui.mvi.processor

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.State
import io.github.stslex.workeeper.core.ui.mvi.Store

@Immutable
class StoreProcessorImpl<
        S : Store.State,
        A : Store.Action,
        E : Store.Event,
        TStore : Store<S, A, E>,
        >(
    private val actionProcessor: ActionProcessor<A, TStore>,
    private val eventProcessor: EffectsProcessor<E, TStore>,
    override val state: State<S>,
) : StoreProcessor<S, A, E> {

    override fun consume(action: A) {
        actionProcessor(action)
    }

    @Composable
    override fun Handle(block: SuspendProcessor<E>) {
        eventProcessor(block)
    }
}