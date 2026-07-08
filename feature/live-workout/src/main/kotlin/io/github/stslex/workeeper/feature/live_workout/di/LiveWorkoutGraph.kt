// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.live_workout.di

import dev.zacsweers.metro.Binds
import dev.zacsweers.metro.DependencyGraph
import dev.zacsweers.metro.Provides
import io.github.stslex.workeeper.core.core.di.DefaultDispatcher
import io.github.stslex.workeeper.core.core.resources.ResourceWrapper
import io.github.stslex.workeeper.core.data.exercise.exercise.ExerciseRepository
import io.github.stslex.workeeper.core.data.exercise.personal_record.PersonalRecordRepository
import io.github.stslex.workeeper.core.data.exercise.session.PerformedExerciseRepository
import io.github.stslex.workeeper.core.data.exercise.session.SessionRepository
import io.github.stslex.workeeper.core.data.exercise.session.SetRepository
import io.github.stslex.workeeper.core.data.exercise.training.TrainingExerciseRepository
import io.github.stslex.workeeper.core.data.exercise.training.TrainingRepository
import io.github.stslex.workeeper.core.ui.mvi.di.StoreDispatchers
import io.github.stslex.workeeper.core.ui.mvi.holders.AnalyticsHolder
import io.github.stslex.workeeper.core.ui.mvi.holders.LoggerHolder
import io.github.stslex.workeeper.core.ui.navigation.Navigator
import io.github.stslex.workeeper.feature.live_workout.domain.LiveWorkoutInteractor
import io.github.stslex.workeeper.feature.live_workout.domain.LiveWorkoutInteractorImpl
import io.github.stslex.workeeper.feature.live_workout.mvi.store.LiveWorkoutStoreImpl
import kotlinx.coroutines.CoroutineDispatcher

/**
 * The single Metro dependency graph for feature/live-workout (KMP C.1 wave 3) — the largest feature.
 * Scoped to [LiveWorkoutScope]. ASSISTED Store (`Screen.LiveWorkout` route arg): the graph exposes
 * the assisted [LiveWorkoutStoreImpl.Factory] — never the Store. 13 app-scoped `@Provides` bound
 * instances; two `@Binds` migrate from the deleted module. `@DefaultDispatcher` stays QUALIFIED
 * (`includeJavax`). No Context. (The feature-scoped ExercisePickerHandler / LiveSetMutator /
 * StateStatusMapper are Metro-constructed via their own `@SingleIn` `@Inject`.)
 */
@DependencyGraph(scope = LiveWorkoutScope::class)
internal interface LiveWorkoutGraph {

    /** Root accessor: the ASSISTED store factory. `create(screen)` builds the retained Store. */
    val storeFactory: LiveWorkoutStoreImpl.Factory

    @Binds
    val LiveWorkoutInteractorImpl.bindInteractor: LiveWorkoutInteractor

    @Binds
    val LiveWorkoutHandlerStoreImpl.bindHandlerStore: LiveWorkoutHandlerStore

    @DependencyGraph.Factory
    fun interface Factory {
        fun create(
            @Provides exerciseRepository: ExerciseRepository,
            @Provides performedExerciseRepository: PerformedExerciseRepository,
            @Provides personalRecordRepository: PersonalRecordRepository,
            @Provides sessionRepository: SessionRepository,
            @Provides setRepository: SetRepository,
            @Provides trainingExerciseRepository: TrainingExerciseRepository,
            @Provides trainingRepository: TrainingRepository,
            @Provides resourceWrapper: ResourceWrapper,
            @Provides navigator: Navigator,
            @Provides storeDispatchers: StoreDispatchers,
            @Provides analyticsHolder: AnalyticsHolder,
            @Provides loggerHolder: LoggerHolder,
            @Provides @DefaultDispatcher defaultDispatcher: CoroutineDispatcher,
        ): LiveWorkoutGraph
    }
}
