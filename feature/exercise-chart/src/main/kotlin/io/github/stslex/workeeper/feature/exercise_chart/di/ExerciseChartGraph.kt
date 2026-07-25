// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.exercise_chart.di

import dev.zacsweers.metro.Binds
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.GraphExtension
import dev.zacsweers.metro.Provides
import io.github.stslex.workeeper.core.core.di.AppScope
import io.github.stslex.workeeper.core.core.di.DefaultDispatcher
import io.github.stslex.workeeper.core.ui.navigation.Screen
import io.github.stslex.workeeper.feature.exercise_chart.domain.ExerciseChartInteractor
import io.github.stslex.workeeper.feature.exercise_chart.domain.ExerciseChartInteractorImpl
import io.github.stslex.workeeper.feature.exercise_chart.mvi.store.ExerciseChartStoreImpl
import kotlinx.coroutines.CoroutineDispatcher

/**
 * feature/exercise-chart's Metro graph as a CONTRIBUTED [GraphExtension] of [ExerciseChartScope]. The
 * factory carries `@ContributesTo(AppScope::class)`, so the extension is merged into the app graph in
 * `:app` and inherits ALL of its app-scoped bindings — the 8 formerly hand-threaded bound-instance
 * `@Provides` are gone. The two `@Binds` (ExerciseChartInteractor, ExerciseChartHandlerStore) stay.
 *
 * ROUTE ARG (shape B): the `Screen.ExerciseChart` route arg enters as a `@Provides` bound instance on
 * the extension factory rather than as an `@Assisted` store param, so the accessor is the Store itself
 * and the feature carries no assisted machinery — `@AssistedInject`, `@Assisted`, `@AssistedFactory`
 * and the `StoreFactory` supertype are all gone from [ExerciseChartStoreImpl]. One extension is built
 * per navigation entry, parameterised by that entry's arg.
 *
 * Note the arg is NULLABLE (`Screen.ExerciseChart.exerciseUuid: String?`): "open the chart with no
 * exercise pre-selected" is a real destination, and it reaches `State.initialUuid` unchanged.
 *
 * The route arg is an ordinary binding in this scope, so it COULD be injected anywhere in the
 * extension; `ScreenInjectionRule` (detekt) forbids that outside the Store's primary constructor —
 * state must flow through the Store, not be read from DI.
 *
 * [defaultDispatcher] is an observability accessor, not a feature need: exercise-chart consumes exactly
 * ONE dispatcher (`@DefaultDispatcher`), and an `assertSame` against the parent's cannot by itself
 * distinguish "inherited the Default key" from "the parent collapsed Default and IO into one instance".
 * The identity test in `:app` reads this accessor and asserts it is the parent's `@DefaultDispatcher`
 * AND *not* the parent's `@IODispatcher`. It costs no forced-public surface — `CoroutineDispatcher` is
 * an external type.
 *
 * Interface + factory are `public` because `:app` generates the extension impl and references them;
 * [ExerciseChartScope] stays `internal` (Metro reads the scope KClass at IR level).
 */
@GraphExtension(ExerciseChartScope::class)
interface ExerciseChartGraph {

    /** Root accessor: the retained Store. Its route arg is the factory's bound instance. */
    val exerciseChartStore: ExerciseChartStoreImpl

    /** Observability root for the qualifier-distinctness claim — see the class doc. */
    @DefaultDispatcher
    val defaultDispatcher: CoroutineDispatcher

    @Binds
    val ExerciseChartInteractorImpl.bindInteractor: ExerciseChartInteractor

    @Binds
    val ExerciseChartHandlerStoreImpl.bindHandlerStore: ExerciseChartHandlerStore

    /**
     * The creator method name must be UNIQUE across all contributed extension factories: every
     * `@ContributesTo(AppScope::class)` factory is merged into `AppGraph`, so two factories both
     * declaring `create()` collide ("return types are incompatible"). Binding rule for all 13 — see
     * documentation/graph-extension-arc/HANDOFF.md.
     */
    @ContributesTo(AppScope::class)
    @GraphExtension.Factory
    fun interface Factory {
        fun createExerciseChartGraph(@Provides screen: Screen.ExerciseChart): ExerciseChartGraph
    }
}
