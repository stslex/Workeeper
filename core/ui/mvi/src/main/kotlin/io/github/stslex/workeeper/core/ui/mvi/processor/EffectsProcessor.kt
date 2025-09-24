package io.github.stslex.workeeper.core.ui.mvi.processor

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import io.github.stslex.workeeper.core.ui.mvi.Store

@Immutable
class EffectsProcessor<E : Store.Event, TStore : Store<*, *, E>>(
    val store: TStore,
) {

    @Composable
    operator fun invoke(block: SuspendProcessor<E>) {
        LaunchedEffect(Unit) { store.event.collect { block(it) } }
    }
}
