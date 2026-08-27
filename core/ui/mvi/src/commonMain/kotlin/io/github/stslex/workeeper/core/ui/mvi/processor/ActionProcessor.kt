package io.github.stslex.workeeper.core.ui.mvi.processor

import androidx.compose.runtime.Immutable
import io.github.stslex.workeeper.core.ui.mvi.Store

@Immutable
class ActionProcessor<in A : Store.Action, TStore : Store<*, A, *>>(
    val store: TStore,
) {

    operator fun invoke(action: A) {
        store.consume(action)
    }
}
