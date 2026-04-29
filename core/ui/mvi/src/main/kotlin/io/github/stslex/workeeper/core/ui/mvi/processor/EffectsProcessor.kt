package io.github.stslex.workeeper.core.ui.mvi.processor

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.rememberLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import io.github.stslex.workeeper.core.core.logger.Log
import io.github.stslex.workeeper.core.ui.mvi.Store

@Immutable
class EffectsProcessor<E : Store.Event, TStore : Store<*, *, E>>(
    val store: TStore,
) {

    @Composable
    operator fun invoke(block: SuspendProcessor<E>) {
        val currentLifecycleOwner = rememberLifecycleOwner()
        LaunchedEffect(currentLifecycleOwner) {
            currentLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                store.event.collect { event ->
                    Log.tag("MVI_STORE_LiveWorkout").i { "Received event collector: $event" }
                    block(event)
                }
            }
        }
    }
}
