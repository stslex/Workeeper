package io.github.stslex.workeeper.spike_metro.topology

import dev.zacsweers.metro.Assisted
import dev.zacsweers.metro.AssistedFactory
import dev.zacsweers.metro.AssistedInject
import dev.zacsweers.metro.DependencyGraph
import dev.zacsweers.metro.GraphExtension
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn

/**
 * Phase B.2 — faithful reproduction of the app's DI topology in Metro, commonMain,
 * compiling for android + iosSimulatorArm64.
 *
 * Maps the Hilt shape 1:1:
 *   Hilt @Singleton (SingletonComponent)      -> AppScope,     app-scoped graph (parent)
 *   Hilt @ViewModelScoped (ViewModelComponent)-> FeatureScope, feature graph (child/extension)
 *   Hilt @HiltViewModel(assistedFactory=..)    -> @AssistedInject store + @AssistedFactory
 *   BaseStore default-arg seams                -> a default-value constructor param
 *
 * The feature graph EXTENDS the app graph, so a per-screen feature scope sees the
 * long-lived app singletons — exactly Hilt's SingletonComponent -> ViewModelComponent
 * parent/child lifecycle.
 */

// --- Scope tokens ---------------------------------------------------------------
abstract class AppScope private constructor()

abstract class FeatureScope private constructor()

// --- App-scoped singleton dep (Hilt @Singleton) ---------------------------------
@Inject
@SingleIn(AppScope::class)
class ExerciseRepository {
    fun load(id: String): String = "exercise:$id"
}

// --- Feature-scoped dep (Hilt @ViewModelScoped), depends on the app singleton ----
@Inject
@SingleIn(FeatureScope::class)
class ChartInteractor(
    private val repository: ExerciseRepository,
) {
    fun chartFor(id: String): String = "chart(${repository.load(id)})"
}

// --- The MVI Store: assisted (screen arg) + injected feature dep + default-arg seam
@AssistedInject
class ChartStore(
    @Assisted val screenId: String,
    private val interactor: ChartInteractor,
    // default-arg constructor seam, as in BaseStore(initialActions = emptyList(), ...)
    private val name: String = DEFAULT_NAME,
) {
    fun render(): String = "$name[$screenId] -> ${interactor.chartFor(screenId)}"

    @AssistedFactory
    fun interface Factory {
        fun create(screenId: String): ChartStore
    }

    companion object {
        const val DEFAULT_NAME: String = "ChartStore"
    }
}

// --- App graph (parent, AppScope) -----------------------------------------------
// Becomes extendable automatically by exposing a @GraphExtension accessor.
@DependencyGraph(scope = AppScope::class)
interface AppGraph {
    val repository: ExerciseRepository

    // Accessor that creates the child feature extension (Hilt: ViewModelComponent
    // built from SingletonComponent). The child inherits AppScope wholesale.
    val featureGraph: FeatureGraph
}

// --- Feature graph (child extension, FeatureScope) -------------------------------
// A @GraphExtension inherits the parent's scopes + full binding set, so ChartInteractor
// resolves the app-scoped ExerciseRepository from the parent. This is the faithful
// Metro equivalent of Hilt's SingletonComponent -> ViewModelComponent hierarchy.
@GraphExtension(scope = FeatureScope::class)
interface FeatureGraph {

    // Expose the assisted Store FACTORY (never ChartStore directly — that is a
    // [Metro/InvalidBinding]). iOS consumes this factory directly; the Android
    // ViewModel wraps it for retention.
    val storeFactory: ChartStore.Factory

    // Feature-scoped interactor; inherited app-scoped repository is visible too.
    val interactor: ChartInteractor
}
