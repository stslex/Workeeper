// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.single_training.di

import dev.zacsweers.metro.Binds
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.GraphExtension
import dev.zacsweers.metro.Provides
import io.github.stslex.workeeper.core.core.di.AppScope
import io.github.stslex.workeeper.core.core.di.DefaultDispatcher
import io.github.stslex.workeeper.core.core.di.MainImmediateDispatcher
import io.github.stslex.workeeper.core.data.exercise.session.SessionConflictResolver
import io.github.stslex.workeeper.core.ui.navigation.Screen
import io.github.stslex.workeeper.feature.single_training.domain.SingleTrainingInteractor
import io.github.stslex.workeeper.feature.single_training.domain.SingleTrainingInteractorImpl
import io.github.stslex.workeeper.feature.single_training.mvi.store.SingleTrainingStoreImpl
import kotlinx.coroutines.CoroutineDispatcher

/**
 * feature/single-training's Metro graph as a CONTRIBUTED [GraphExtension] of [SingleTrainingScope]. The
 * factory carries `@ContributesTo(AppScope::class)`, so the extension is merged into the app graph in
 * `:app` and inherits ALL of its app-scoped bindings — the 13 formerly hand-threaded bound-instance
 * `@Provides` are gone. The two `@Binds` (SingleTrainingInteractor, SingleTrainingHandlerStore) stay.
 *
 * ROUTE ARG (shape B): the `Screen.Training` route arg enters as a `@Provides` bound instance on the
 * extension factory rather than as an `@Assisted` store param, so the accessor is the Store itself and
 * the feature carries no assisted machinery — `@AssistedInject`, `@Assisted`, `@AssistedFactory` and
 * the `StoreFactory` supertype are all gone from [SingleTrainingStoreImpl]. One extension is built per
 * navigation entry, parameterised by that entry's arg.
 *
 * The route arg is an ordinary binding in this scope, so it COULD be injected anywhere in the
 * extension; `ScreenInjectionRule` (detekt) forbids that outside the Store's primary constructor —
 * state must flow through the Store, not be read from DI.
 *
 * This feature reaches the session subsystem (`SessionRepository`, `SessionConflictResolver`), which is
 * the deepest app-scoped stack any extension inherits. If any of it static-inits only on-device, the
 * `:app` identity test pins the BOUNDARY rather than dropping the claim — see STANDING RULE 4.
 *
 * The two dispatcher accessors keep the qualifier-distinctness claim OBSERVABLE. They were
 * bridge-observability roots for `SingleTrainingGraphBridgeTest`; they are retained deliberately,
 * because the property they expose got HARDER to verify rather than easier — the pair is now inherited
 * across a graph boundary instead of handed in explicitly, and a silent cross-wire would be invisible
 * from the Store alone. They cost no forced-public surface: `CoroutineDispatcher` is an external type.
 *
 * Interface + factory are `public` because `:app` generates the extension impl and references them;
 * [SingleTrainingScope] stays `internal` (Metro reads the scope KClass at IR level).
 */
@GraphExtension(SingleTrainingScope::class)
interface SingleTrainingGraph {

    /** Root accessor: the retained Store. Its route arg is the factory's bound instance. */
    val singleTrainingStore: SingleTrainingStoreImpl

    @DefaultDispatcher
    val defaultDispatcher: CoroutineDispatcher

    @MainImmediateDispatcher
    val mainImmediateDispatcher: CoroutineDispatcher

    /**
     * Observability root for the deepest thing this extension inherits. Without it the "the session
     * subsystem is inherited, not rebuilt" claim cannot be *made* — only asserted against the parent's
     * own accessor compared to itself, which is vacuously true and tests nothing. Costs no
     * forced-public surface: `SessionConflictResolver` is a `core:data:exercise` type.
     */
    val sessionConflictResolver: SessionConflictResolver

    @Binds
    val SingleTrainingInteractorImpl.bindInteractor: SingleTrainingInteractor

    @Binds
    val SingleTrainingHandlerStoreImpl.bindHandlerStore: SingleTrainingHandlerStore

    /**
     * The creator method name must be UNIQUE across all contributed extension factories: every
     * `@ContributesTo(AppScope::class)` factory is merged into `AppGraph`, so two factories both
     * declaring `create()` collide ("return types are incompatible"). Binding rule for all 13 — see
     * documentation/graph-extension-arc/HANDOFF.md.
     */
    @ContributesTo(AppScope::class)
    @GraphExtension.Factory
    fun interface Factory {
        fun createSingleTrainingGraph(@Provides screen: Screen.Training): SingleTrainingGraph
    }
}
