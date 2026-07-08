// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.exercise.di

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import dagger.hilt.android.EntryPointAccessors
import dev.zacsweers.metro.createGraphFactory
import io.github.stslex.workeeper.core.ui.mvi.FeatureAssisted
import io.github.stslex.workeeper.core.ui.mvi.processor.StoreProcessor
import io.github.stslex.workeeper.core.ui.mvi.processor.rememberMetroStoreProcessor
import io.github.stslex.workeeper.core.ui.navigation.Screen
import io.github.stslex.workeeper.feature.exercise.ui.mvi.store.ExerciseStore.Action
import io.github.stslex.workeeper.feature.exercise.ui.mvi.store.ExerciseStore.Event
import io.github.stslex.workeeper.feature.exercise.ui.mvi.store.ExerciseStore.State
import io.github.stslex.workeeper.feature.exercise.ui.mvi.store.ExerciseStoreImpl

internal typealias ExerciseStoreProcessor = StoreProcessor<State, Action, Event>

/**
 * feature/exercise resolves its Store through the **Metro** path (KMP C.1 wave 1). The Store is
 * ASSISTED — it takes the `Screen.Exercise` route arg — so the graph exposes the assisted
 * [ExerciseStoreImpl.Factory] and this composable calls `storeFactory.create(screen)` inside the
 * `rememberMetroStoreProcessor` lambda (once per retained Store, per `NavBackStackEntry`).
 *
 * The 14 app-scoped Hilt singletons are pulled from the `SingletonComponent` via
 * [ExerciseHiltEntryPoint]. The two dispatchers cross the bridge QUALIFIED (`includeJavax`); the app
 * `Context` is resolved on the Hilt side and handed to the graph as a plain `Context`.
 */
internal object ExerciseFeature : FeatureAssisted<ExerciseStoreProcessor, Screen.Exercise>() {

    @Suppress("UNCHECKED_CAST")
    @Composable
    override fun processor(screen: Screen.Exercise): ExerciseStoreProcessor {
        val context = LocalContext.current
        return rememberMetroStoreProcessor<ExerciseStoreImpl> {
            val entryPoint = EntryPointAccessors.fromApplication(
                context.applicationContext,
                ExerciseHiltEntryPoint::class.java,
            )
            createGraphFactory<ExerciseGraph.Factory>()
                .create(
                    exerciseRepository = entryPoint.exerciseRepository(),
                    tagRepository = entryPoint.tagRepository(),
                    imageStorage = entryPoint.imageStorage(),
                    personalRecordRepository = entryPoint.personalRecordRepository(),
                    sessionRepository = entryPoint.sessionRepository(),
                    trainingRepository = entryPoint.trainingRepository(),
                    resourceWrapper = entryPoint.resourceWrapper(),
                    navigator = entryPoint.navigator(),
                    storeDispatchers = entryPoint.storeDispatchers(),
                    analyticsHolder = entryPoint.analyticsHolder(),
                    loggerHolder = entryPoint.loggerHolder(),
                    defaultDispatcher = entryPoint.defaultDispatcher(),
                    mainImmediateDispatcher = entryPoint.mainImmediateDispatcher(),
                    context = entryPoint.applicationContext(),
                )
                .storeFactory
                .create(screen)
        } as ExerciseStoreProcessor
    }
}
