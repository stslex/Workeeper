// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.settings

import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import dev.zacsweers.metro.DependencyGraph
import dev.zacsweers.metro.Provides
import dev.zacsweers.metro.createGraphFactory
import io.github.stslex.workeeper.core.core.coroutine.scope.AppScopeLifetime
import io.github.stslex.workeeper.core.core.di.AppScope
import io.github.stslex.workeeper.core.core.platform.PlatformInfoProvider
import io.github.stslex.workeeper.core.data.dataStore.store.CommonDataStore
import io.github.stslex.workeeper.core.data.dataStore.store.CommonDataStoreImpl
import io.github.stslex.workeeper.core.data.exercise.exercise.ExerciseRepository
import io.github.stslex.workeeper.core.data.exercise.exercise.ExerciseRepositoryImpl
import io.github.stslex.workeeper.core.data.exercise.training.TrainingRepository
import io.github.stslex.workeeper.core.data.exercise.training.TrainingRepositoryImpl
import io.github.stslex.workeeper.core.ui.mvi.di.StoreDispatchers
import io.github.stslex.workeeper.core.ui.mvi.holders.AnalyticsHolder
import io.github.stslex.workeeper.core.ui.navigation.Navigator
import io.github.stslex.workeeper.feature.settings.di.SettingsGraph
import io.github.stslex.workeeper.feature.settings.fixtures.RecordingNavigator
import io.github.stslex.workeeper.feature.settings.fixtures.SettingsCommonDataStoreFake
import io.github.stslex.workeeper.feature.settings.fixtures.SettingsExerciseRepositoryFake
import io.github.stslex.workeeper.feature.settings.fixtures.SettingsTrainingRepositoryFake
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.test.TestDispatcher

@DependencyGraph(
    scope = AppScope::class,
    excludes = [CommonDataStoreImpl::class, ExerciseRepositoryImpl::class, TrainingRepositoryImpl::class],
)
internal interface SettingsNativeParent {
    val settingsGraphFactory: SettingsGraph.Factory

    @DependencyGraph.Factory
    fun interface Factory {
        fun create(
            @Provides commonDataStore: CommonDataStore,
            @Provides exerciseRepository: ExerciseRepository,
            @Provides trainingRepository: TrainingRepository,
            @Provides platformInfo: PlatformInfoProvider,
            @Provides dispatchers: StoreDispatchers,
            @Provides lifetime: AppScopeLifetime,
            @Provides analytics: AnalyticsHolder,
            @Provides navigator: Navigator,
            @Provides dispatcher: CoroutineDispatcher,
        ): SettingsNativeParent
    }
}

internal class SettingsNativeGraphFixture(dispatcher: TestDispatcher) {
    val lifetime = AppScopeLifetime()
    val preferences = SettingsCommonDataStoreFake()
    val owner = object : LifecycleOwner {
        override val lifecycle: Lifecycle = LifecycleRegistry.createUnsafe(this)
    }
    private val parent = createGraphFactory<SettingsNativeParent.Factory>().create(
        commonDataStore = preferences,
        exerciseRepository = SettingsExerciseRepositoryFake(),
        trainingRepository = SettingsTrainingRepositoryFake(),
        platformInfo = PlatformInfoProvider(),
        dispatchers = StoreDispatchers(dispatcher, dispatcher),
        lifetime = lifetime,
        analytics = AnalyticsHolder(),
        navigator = RecordingNavigator(),
        dispatcher = dispatcher,
    )
    private val graph = parent.settingsGraphFactory.createSettingsGraph()
    val store = graph.settingsStore

    suspend fun close() {
        store.dispose()
        lifetime.cancelAndJoin()
    }
}
