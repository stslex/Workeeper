// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper

import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import dev.zacsweers.metro.DependencyGraph
import dev.zacsweers.metro.Provides
import dev.zacsweers.metro.createGraphFactory
import io.github.stslex.workeeper.app.common.di.AppRootDeps
import io.github.stslex.workeeper.app.common.di.AppUiAdmissionToken
import io.github.stslex.workeeper.app.common.di.AppUiGenerationsHolder
import io.github.stslex.workeeper.app.common.di.AppUiPhase
import io.github.stslex.workeeper.core.core.coroutine.scope.AppScopeLifetime
import io.github.stslex.workeeper.core.core.di.AppScope
import io.github.stslex.workeeper.core.core.di.DefaultDispatcher
import io.github.stslex.workeeper.core.core.di.MainImmediateDispatcher
import io.github.stslex.workeeper.core.core.platform.AppReinitializationHost
import io.github.stslex.workeeper.core.core.platform.AppReinitializer
import io.github.stslex.workeeper.core.core.resources.ResourceWrapper
import io.github.stslex.workeeper.core.data.backup.api.restore.InstallEpoch
import io.github.stslex.workeeper.core.data.backup.api.restore.InstallEpochSource
import io.github.stslex.workeeper.core.data.backup.api.restore.RestoreOwnerId
import io.github.stslex.workeeper.core.data.dataStore.core.DataStorePathResolver
import io.github.stslex.workeeper.core.data.dataStore.core.DataStoreProvider
import io.github.stslex.workeeper.core.data.dataStore.core.DataStoreProviderFactory
import io.github.stslex.workeeper.core.data.dataStore.store.CommonDataStore
import io.github.stslex.workeeper.core.data.dataStore.store.CommonDataStoreImpl
import io.github.stslex.workeeper.core.data.exercise.session.SessionRepository
import io.github.stslex.workeeper.core.data.exercise.session.SessionRepositoryImpl
import io.github.stslex.workeeper.core.data.exercise.tags.TagRepository
import io.github.stslex.workeeper.core.data.exercise.tags.TagRepositoryImpl
import io.github.stslex.workeeper.core.data.exercise.training.TrainingRepository
import io.github.stslex.workeeper.core.data.exercise.training.TrainingRepositoryImpl
import io.github.stslex.workeeper.core.ui.mvi.holders.AnalyticsHolder
import io.github.stslex.workeeper.feature.all_exercises.di.AllExercisesGraph
import io.github.stslex.workeeper.feature.all_trainings.di.AllTrainingsGraph
import io.github.stslex.workeeper.feature.app_dialogs.impl.data.AppDialogRepository
import io.github.stslex.workeeper.feature.app_dialogs.impl.di.AppDialogGraph
import io.github.stslex.workeeper.feature.archive.di.ArchiveGraph
import io.github.stslex.workeeper.feature.exercise.di.ExerciseGraph
import io.github.stslex.workeeper.feature.exercise_chart.di.ExerciseChartGraph
import io.github.stslex.workeeper.feature.home.di.HomeGraph
import io.github.stslex.workeeper.feature.image_viewer.di.ImageViewerGraph
import io.github.stslex.workeeper.feature.live_workout.di.LiveWorkoutGraph
import io.github.stslex.workeeper.feature.past_session.di.PastSessionGraph
import io.github.stslex.workeeper.feature.plan_editor.di.PlanEditorGraph
import io.github.stslex.workeeper.feature.settings.di.SettingsGraph
import io.github.stslex.workeeper.feature.single_training.di.SingleTrainingGraph
import io.github.stslex.workeeper.navigation.NavigatorEventBus
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import okio.Path.Companion.toPath
import platform.Foundation.NSTemporaryDirectory
import kotlin.uuid.Uuid

@DependencyGraph(
    scope = AppScope::class,
    excludes = [
        CommonDataStoreImpl::class,
        SessionRepositoryImpl::class,
        TrainingRepositoryImpl::class,
        TagRepositoryImpl::class,
        SettingsGraph.Factory::class,
        AllTrainingsGraph.Factory::class,
        AllExercisesGraph.Factory::class,
        PastSessionGraph.Factory::class,
        ExerciseChartGraph.Factory::class,
        SingleTrainingGraph.Factory::class,
        ExerciseGraph.Factory::class,
        LiveWorkoutGraph.Factory::class,
        ArchiveGraph.Factory::class,
        PlanEditorGraph.Factory::class,
    ],
)
internal interface AppRootNativeParent {
    val navigatorEventBus: NavigatorEventBus
    val homeGraphFactory: HomeGraph.Factory
    val imageViewerGraphFactory: ImageViewerGraph.Factory
    val appDialogGraphFactory: AppDialogGraph.Factory
    val dialogs: AppDialogRepository

    @DependencyGraph.Factory
    fun interface Factory {
        fun create(
            @Provides preferences: CommonDataStore,
            @Provides sessions: SessionRepository,
            @Provides trainings: TrainingRepository,
            @Provides tags: TagRepository,
            @Provides resources: ResourceWrapper,
            @Provides lifetime: AppScopeLifetime,
            @Provides analytics: AnalyticsHolder,
            @Provides @MainImmediateDispatcher mainImmediateDispatcher: CoroutineDispatcher,
            @Provides @DefaultDispatcher defaultDispatcher: CoroutineDispatcher,
            @Provides reinitializer: AppReinitializer,
            @Provides storeFactory: DataStoreProviderFactory,
            @Provides epoch: InstallEpochSource,
        ): AppRootNativeParent
    }
}

internal class AppRootNativeFixture : AppRootDeps, AppReinitializationHost {
    val lifetime = AppScopeLifetime()
    var restarts = 0
        private set
    override val commonDataStore = RootPreferences()
    private val fixtureId = Uuid.random().toString()
    private val paths = object : DataStorePathResolver {
        override fun resolve(name: String) = "${NSTemporaryDirectory()}$name.preferences_pb".toPath()
    }
    private val stores = object : DataStoreProviderFactory {
        override fun create(name: String) = DataStoreProvider("$fixtureId-$name", paths)
    }
    private val parent = createGraphFactory<AppRootNativeParent.Factory>().create(
        preferences = commonDataStore,
        sessions = RootSessionRepository(),
        trainings = RootTrainingRepository(),
        tags = RootTagRepository(),
        resources = RootResources(),
        lifetime = lifetime,
        analytics = AnalyticsHolder(),
        mainImmediateDispatcher = Dispatchers.Main.immediate,
        defaultDispatcher = Dispatchers.Main,
        reinitializer = AppReinitializer(this),
        storeFactory = stores,
        epoch = object : InstallEpochSource {
            override suspend fun installEpoch() = InstallEpoch(
                RestoreOwnerId("00000000-0000-4000-8000-000000000019"),
            )
        },
    )
    override val navigatorEventBus get() = parent.navigatorEventBus
    override val homeGraphFactory get() = parent.homeGraphFactory
    override val imageViewerGraphFactory get() = parent.imageViewerGraphFactory
    override val appDialogGraphFactory get() = parent.appDialogGraphFactory
    val dialogs get() = parent.dialogs
    override val settingsGraphFactory = SettingsGraph.Factory { error("Unexpected Settings graph") }
    override val allTrainingsGraphFactory = AllTrainingsGraph.Factory { error("Unexpected AllTrainings graph") }
    override val allExercisesGraphFactory = AllExercisesGraph.Factory { error("Unexpected AllExercises graph") }
    override val pastSessionGraphFactory = PastSessionGraph.Factory { error("Unexpected PastSession graph") }
    override val exerciseChartGraphFactory = ExerciseChartGraph.Factory { error("Unexpected ExerciseChart graph") }
    override val singleTrainingGraphFactory = SingleTrainingGraph.Factory { error("Unexpected SingleTraining graph") }
    override val exerciseGraphFactory = ExerciseGraph.Factory { error("Unexpected Exercise graph") }
    override val liveWorkoutGraphFactory = LiveWorkoutGraph.Factory { error("Unexpected LiveWorkout graph") }
    override val archiveGraphFactory = ArchiveGraph.Factory { error("Unexpected Archive graph") }
    override val planEditorGraphFactory = PlanEditorGraph.Factory { error("Unexpected PlanEditor graph") }

    override fun requestReinitialize() { restarts++ }

    suspend fun close() { lifetime.cancelAndJoin() }
}

internal class RootPreferences : CommonDataStore {
    override val homeSelectedStartDate = MutableStateFlow<Long?>(null)
    override val homeSelectedEndDate = MutableStateFlow<Long?>(null)
    override val themePreference = MutableStateFlow("LIGHT")
    override val homeStartCardMode = MutableStateFlow("WEEK")
    override suspend fun setHomeSelectedStartDate(value: Long) { homeSelectedStartDate.value = value }
    override suspend fun setHomeSelectedEndDate(value: Long) { homeSelectedEndDate.value = value }
    override suspend fun setThemePreference(value: String) { themePreference.value = value }
    override suspend fun setHomeStartCardMode(value: String) { homeStartCardMode.value = value }
}

internal class RootOwner : ViewModelStoreOwner {
    override val viewModelStore = ViewModelStore()
}

internal class RootGenerations(owner: RootOwner = RootOwner()) : AppUiGenerationsHolder {
    override val appUiPhases = MutableStateFlow<AppUiPhase>(AppUiPhase.Generation(1, owner))
    var allow = true
    val admitted = mutableListOf<AppUiAdmissionToken>()
    val released = mutableListOf<AppUiAdmissionToken>()
    override fun admitUiGeneration(id: Int): AppUiAdmissionToken? =
        if (allow) object : AppUiAdmissionToken {}.also(admitted::add) else null
    override fun releaseUiGeneration(token: AppUiAdmissionToken) { released += token }
}

private class RootTagRepository : TagRepository {
    override fun observeAll() = flowOf(
        emptyList<io.github.stslex.workeeper.core.data.exercise.tags.model.TagDataModel>(),
    )
    override suspend fun searchByPrefix(prefix: String): Nothing = error("Unexpected tag search")
    override suspend fun findByName(name: String): Nothing = error("Unexpected tag read")
    override suspend fun add(name: String): Nothing = error("Unexpected tag write")
    override fun observeTagIdleStats(limit: Int) = flowOf(emptyList<TagRepository.TagIdleStat>())
}

private class RootResources : ResourceWrapper {
    override fun getString(id: Int, vararg args: Any): Nothing = error("Unexpected Android string")
    override fun getQuantityString(id: Int, quantity: Int, vararg args: Any): Nothing = error(
        "Unexpected Android plural",
    )
    override fun getAbbreviatedRelativeTime(timestamp: Long, now: Long) = "just now"
    override fun formatMediumDate(timestamp: Long) = "Nov 2, 2026"
    override fun formatDayMonth(timestamp: Long) = "Nov 2"
}
