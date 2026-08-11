// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.exercise.di

import android.content.Context
import dev.zacsweers.metro.Binds
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.GraphExtension
import dev.zacsweers.metro.Provides
import io.github.stslex.workeeper.core.core.di.AppScope
import io.github.stslex.workeeper.core.core.di.DefaultDispatcher
import io.github.stslex.workeeper.core.core.di.MainImmediateDispatcher
import io.github.stslex.workeeper.core.ui.navigation.Screen
import io.github.stslex.workeeper.feature.exercise.domain.ExerciseInteractor
import io.github.stslex.workeeper.feature.exercise.domain.ExerciseInteractorImpl
import io.github.stslex.workeeper.feature.exercise.ui.mvi.store.ExerciseStoreImpl
import kotlinx.coroutines.CoroutineDispatcher

/**
 * feature/exercise's Metro graph as a CONTRIBUTED [GraphExtension] of [ExerciseScope]. The factory
 * carries `@ContributesTo(AppScope::class)`, so the extension is merged into the app graph in `:app`
 * and inherits ALL of its app-scoped bindings — all **14** formerly hand-threaded bound-instance
 * `@Provides` are gone. The two `@Binds` (ExerciseInteractor, ExerciseHandlerStore) stay.
 *
 * ROUTE ARG (shape B): the `Screen.Exercise` route arg enters as a `@Provides` bound instance on the
 * extension factory rather than as an `@Assisted` store param, so the accessor is the Store itself and
 * the feature carries no assisted machinery — `@AssistedInject`, `@Assisted`, `@AssistedFactory` and
 * the `StoreFactory` supertype are all gone from [ExerciseStoreImpl]. One extension is built per
 * navigation entry, parameterised by that entry's arg.
 *
 * The route arg is an ordinary binding in this scope, so it COULD be injected anywhere in the
 * extension; `ScreenInjectionRule` (detekt) forbids that outside the Store's primary constructor —
 * state must flow through the Store, not be read from DI.
 *
 * Alongside `settings`, this is the widest inheritance claim in the arc, and it is the only OTHER
 * graph carrying all three of the hard categories at once:
 * - **two same-typed qualified dispatchers**, `@DefaultDispatcher` + `@MainImmediateDispatcher`, which
 *   must inherit as two DISTINCT `(CoroutineDispatcher + qualifier)` keys and not cross-wire;
 * - a **bare, unqualified `Context`**, now inherited from AppGraph's `create(applicationContext)` bound
 *   instance instead of being passed as `context.applicationContext` from the composable;
 * - six repositories plus `ImageStorage`, all previously threaded by hand.
 *
 * The three accessors below keep that claim OBSERVABLE. They were bridge-observability roots for
 * `ExerciseGraphBridgeTest`; they are retained deliberately, because the property they expose got
 * HARDER to verify rather than easier — the pair is now inherited across a graph boundary instead of
 * handed in explicitly, and a silent cross-wire would be invisible from the Store alone. Read by
 * `ExerciseExtensionIdentityTest` in `:app`. They cost no forced-public surface: `CoroutineDispatcher`
 * and `Context` are external types.
 *
 * Interface + factory are `public` because `:app` generates the extension impl and references them;
 * [ExerciseScope] stays `internal` (Metro reads the scope KClass at IR level).
 */
@GraphExtension(ExerciseScope::class)
interface ExerciseGraph {

    /** Root accessor: the retained Store. Its route arg is the factory's bound instance. */
    val exerciseStore: ExerciseStoreImpl

    @DefaultDispatcher
    val defaultDispatcher: CoroutineDispatcher

    @MainImmediateDispatcher
    val mainImmediateDispatcher: CoroutineDispatcher

    val appContext: Context

    @Binds
    val ExerciseInteractorImpl.bindInteractor: ExerciseInteractor

    @Binds
    val ExerciseHandlerStoreImpl.bindHandlerStore: ExerciseHandlerStore

    /**
     * The creator method name must be UNIQUE across all contributed extension factories: every
     * `@ContributesTo(AppScope::class)` factory is merged into `AppGraph`, so two factories both
     * declaring `create()` collide ("return types are incompatible"). Binding rule for all 13 — see
     * documentation/graph-extension-arc/HANDOFF.md.
     */
    @ContributesTo(AppScope::class)
    @GraphExtension.Factory
    fun interface Factory {
        fun createExerciseGraph(@Provides screen: Screen.Exercise): ExerciseGraph
    }
}
