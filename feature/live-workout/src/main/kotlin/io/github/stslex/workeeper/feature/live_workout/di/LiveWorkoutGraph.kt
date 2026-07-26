// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.live_workout.di

import dev.zacsweers.metro.Binds
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.GraphExtension
import dev.zacsweers.metro.Provides
import io.github.stslex.workeeper.core.core.di.AppScope
import io.github.stslex.workeeper.core.core.di.DefaultDispatcher
import io.github.stslex.workeeper.core.data.exercise.session.SessionRepository
import io.github.stslex.workeeper.core.ui.navigation.Screen
import io.github.stslex.workeeper.feature.live_workout.domain.LiveWorkoutInteractor
import io.github.stslex.workeeper.feature.live_workout.domain.LiveWorkoutInteractorImpl
import io.github.stslex.workeeper.feature.live_workout.mvi.store.LiveWorkoutStoreImpl
import kotlinx.coroutines.CoroutineDispatcher

/**
 * feature/live-workout's Metro graph as a CONTRIBUTED [GraphExtension] of [LiveWorkoutScope] — the
 * THIRTEENTH and final feature graph of the arc. The factory carries `@ContributesTo(AppScope::class)`,
 * so the extension is merged into the app graph in `:app` and inherits ALL of its app-scoped bindings —
 * the 13 formerly hand-threaded bound-instance `@Provides` are gone. The two `@Binds`
 * (LiveWorkoutInteractor, LiveWorkoutHandlerStore) stay.
 *
 * ROUTE ARG (shape B): the `Screen.LiveWorkout` route arg enters as a `@Provides` bound instance on the
 * extension factory rather than as an `@Assisted` store param, so the accessor is the Store itself and
 * the feature carries no assisted machinery — `@AssistedInject`, `@Assisted`, `@AssistedFactory` and
 * the `StoreFactory` supertype are all gone from [LiveWorkoutStoreImpl]. With this port
 * `StoreFactory` has NO users left and dies with the closing commits.
 *
 * The arg carries TWO nullable uuids (`sessionUuid` / `trainingUuid`) of which at least one is
 * non-null — the widest route-arg shape in the arc, though still a flat 2-level data class and so the
 * shape `ScreenInjectionRule` is already proven on.
 *
 * The route arg is an ordinary binding in this scope, so it COULD be injected anywhere in the
 * extension; `ScreenInjectionRule` (detekt) forbids that outside the Store's primary constructor —
 * state must flow through the Store, not be read from DI.
 *
 * This feature owns the session write path — start, add-exercise, finish, cancel, discard-adhoc — so
 * it inherits the deepest transactional stack of any extension. Construction is asserted directly by
 * `LiveWorkoutExtensionIdentityTest`; if any of it ever static-inits only on-device, that test pins the
 * BOUNDARY (fail at platform static-init HAVING PASSED THROUGH the real container, both halves) rather
 * than dropping the claim — STANDING RULE 4.
 *
 * [sessionRepository] and [defaultDispatcher] are observability roots, not feature needs: without them
 * the "inherited, not rebuilt" claim can only be asserted parent-against-parent, which is vacuous
 * (adjacent-answer witness 13). They cost no forced-public surface — both types are external.
 *
 * Interface + factory are `public` because `:app` generates the extension impl and references them;
 * [LiveWorkoutScope] stays `internal` (Metro reads the scope KClass at IR level).
 */
@GraphExtension(LiveWorkoutScope::class)
interface LiveWorkoutGraph {

    /** Root accessor: the retained Store. Its route arg is the factory's bound instance. */
    val liveWorkoutStore: LiveWorkoutStoreImpl

    /** Observability root: the session write path this extension inherits rather than rebuilds. */
    val sessionRepository: SessionRepository

    @DefaultDispatcher
    val defaultDispatcher: CoroutineDispatcher

    @Binds
    val LiveWorkoutInteractorImpl.bindInteractor: LiveWorkoutInteractor

    @Binds
    val LiveWorkoutHandlerStoreImpl.bindHandlerStore: LiveWorkoutHandlerStore

    /**
     * The creator method name must be UNIQUE across all contributed extension factories: every
     * `@ContributesTo(AppScope::class)` factory is merged into `AppGraph`, so two factories both
     * declaring `create()` collide ("return types are incompatible"). Binding rule for all 13 — see
     * documentation/graph-extension-arc/HANDOFF.md.
     */
    @ContributesTo(AppScope::class)
    @GraphExtension.Factory
    fun interface Factory {
        fun createLiveWorkoutGraph(@Provides screen: Screen.LiveWorkout): LiveWorkoutGraph
    }
}
