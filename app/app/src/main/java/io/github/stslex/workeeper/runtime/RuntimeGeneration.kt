// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.runtime

import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import io.github.stslex.workeeper.core.core.coroutine.scope.AppScopeLifetime
import io.github.stslex.workeeper.core.data.database.AppDatabase
import io.github.stslex.workeeper.di.AppGraph
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.flow.StateFlow

/**
 * Atomically published database, graph, lifetime, and ViewModel ownership unit.
 *
 * [id] always advances; [dbGeneration] advances only when a file swap creates a new database.
 */
internal class RuntimeGeneration(
    val id: Int,
    val dbGeneration: Int,
    val database: AppDatabase,
    val graph: AppGraph,
    val lifetime: AppScopeLifetime,
    override val viewModelStore: ViewModelStore,
) : ViewModelStoreOwner

/** Runtime publication state. */
internal sealed interface RuntimePhase {

    data class Serving(val generation: RuntimeGeneration) : RuntimePhase

    data object Transitioning : RuntimePhase

    /** Terminal state: no generation may be exposed or published again. */
    data object Fatal : RuntimePhase
}

/** Read-only derived [StateFlow] view over the single published value. */
internal class DerivedStateFlow<T, R>(
    private val source: StateFlow<T>,
    private val transform: (T) -> R,
) : StateFlow<R> {

    override val value: R get() = transform(source.value)

    override val replayCache: List<R> get() = listOf(value)

    override suspend fun collect(collector: FlowCollector<R>): Nothing {
        source.collect { collector.emit(transform(it)) }
    }
}
