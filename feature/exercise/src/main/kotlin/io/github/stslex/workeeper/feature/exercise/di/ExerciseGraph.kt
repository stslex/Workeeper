// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.exercise.di

import android.content.Context
import dev.zacsweers.metro.Binds
import dev.zacsweers.metro.DependencyGraph
import dev.zacsweers.metro.Provides
import io.github.stslex.workeeper.core.core.di.DefaultDispatcher
import io.github.stslex.workeeper.core.core.di.MainImmediateDispatcher
import io.github.stslex.workeeper.core.core.images.ImageStorage
import io.github.stslex.workeeper.core.core.resources.ResourceWrapper
import io.github.stslex.workeeper.core.data.exercise.exercise.ExerciseRepository
import io.github.stslex.workeeper.core.data.exercise.personal_record.PersonalRecordRepository
import io.github.stslex.workeeper.core.data.exercise.session.SessionRepository
import io.github.stslex.workeeper.core.data.exercise.tags.TagRepository
import io.github.stslex.workeeper.core.data.exercise.training.TrainingRepository
import io.github.stslex.workeeper.core.ui.mvi.di.StoreDispatchers
import io.github.stslex.workeeper.core.ui.mvi.holders.AnalyticsHolder
import io.github.stslex.workeeper.core.ui.mvi.holders.LoggerHolder
import io.github.stslex.workeeper.core.ui.navigation.Navigator
import io.github.stslex.workeeper.feature.exercise.domain.ExerciseInteractor
import io.github.stslex.workeeper.feature.exercise.domain.ExerciseInteractorImpl
import io.github.stslex.workeeper.feature.exercise.ui.mvi.store.ExerciseStoreImpl
import kotlinx.coroutines.CoroutineDispatcher

/**
 * The single Metro dependency graph for feature/exercise. Scoped to [ExerciseScope].
 *
 * ASSISTED Store: `ExerciseStoreImpl` takes the `Screen.Exercise` route arg via `@Assisted`, so the
 * graph exposes the assisted [ExerciseStoreImpl.Factory] as its root — NEVER the Store directly
 * (exposing an assisted type is a `[Metro/InvalidBinding]`). `ExerciseFeature` calls
 * `storeFactory.create(screen)` inside the `rememberMetroStoreProcessor` lambda.
 *
 * The 14 app-scoped deps are `@SingleIn(AppScope)` bindings from the app graph, handed in as
 * `@Provides` bound instances via [Factory]. The two `@Binds` (ExerciseInteractor,
 * ExerciseHandlerStore) are the feature's own bindings.
 *
 * Bridge specifics:
 * - `@DefaultDispatcher` + `@MainImmediateDispatcher` factory params stay QUALIFIED
 *   → two distinct `(CoroutineDispatcher + qualifier)` binding keys, no collision.
 * - `context` is a PLAIN `Context` param.
 */
@DependencyGraph(scope = ExerciseScope::class)
internal interface ExerciseGraph {

    /** Root accessor: the ASSISTED store factory. `create(screen)` builds the retained Store. */
    val storeFactory: ExerciseStoreImpl.Factory

    // Bridge-observability accessors (inert roots): expose the two qualified dispatchers and the app
    // Context as the graph resolves them, so the real graph is self-verifying. Consumed by
    // ExerciseGraphBridgeTest; no runtime cost unless read.
    @DefaultDispatcher
    val defaultDispatcher: CoroutineDispatcher

    @MainImmediateDispatcher
    val mainImmediateDispatcher: CoroutineDispatcher

    val appContext: Context

    @Binds
    val ExerciseInteractorImpl.bindInteractor: ExerciseInteractor

    @Binds
    val ExerciseHandlerStoreImpl.bindHandlerStore: ExerciseHandlerStore

    @DependencyGraph.Factory
    fun interface Factory {
        fun create(
            @Provides exerciseRepository: ExerciseRepository,
            @Provides tagRepository: TagRepository,
            @Provides imageStorage: ImageStorage,
            @Provides personalRecordRepository: PersonalRecordRepository,
            @Provides sessionRepository: SessionRepository,
            @Provides trainingRepository: TrainingRepository,
            @Provides resourceWrapper: ResourceWrapper,
            @Provides navigator: Navigator,
            @Provides storeDispatchers: StoreDispatchers,
            @Provides analyticsHolder: AnalyticsHolder,
            @Provides loggerHolder: LoggerHolder,
            @Provides @DefaultDispatcher defaultDispatcher: CoroutineDispatcher,
            @Provides @MainImmediateDispatcher mainImmediateDispatcher: CoroutineDispatcher,
            // PLAIN Context.
            @Provides context: Context,
        ): ExerciseGraph
    }
}
