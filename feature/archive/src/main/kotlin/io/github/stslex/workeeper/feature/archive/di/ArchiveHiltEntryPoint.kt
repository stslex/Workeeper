// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.archive.di

import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import io.github.stslex.workeeper.core.core.di.DefaultDispatcher
import io.github.stslex.workeeper.core.core.resources.ResourceWrapper
import io.github.stslex.workeeper.core.data.exercise.exercise.ExerciseRepository
import io.github.stslex.workeeper.core.data.exercise.training.TrainingRepository
import io.github.stslex.workeeper.core.ui.mvi.di.StoreDispatchers
import io.github.stslex.workeeper.core.ui.mvi.holders.AnalyticsHolder
import io.github.stslex.workeeper.core.ui.mvi.holders.LoggerHolder
import io.github.stslex.workeeper.core.ui.navigation.Navigator
import kotlinx.coroutines.CoroutineDispatcher

/**
 * Hilt→Metro bridge for feature/archive (KMP C.1 M0).
 *
 * Pulls archive's 8 app-scoped `@Singleton` dependencies out of the Hilt
 * `SingletonComponent` so they can be handed to [ArchiveGraph] as `@Provides` bound
 * instances. Consumed via `EntryPointAccessors.fromApplication(...)` at the flip point in
 * `ArchiveFeature.processor()`.
 *
 * **Aggregation.** This `@InstallIn(SingletonComponent::class)` interface, declared in a
 * library module, is merged into the app's single Dagger graph automatically — the exact
 * pattern `feature/recovery`'s `AppDialogObserverBootstrapEntryPoint` uses — so NO app-module
 * change is needed. The instances handed across are the same `===` singletons Hilt owns
 * (identity proven at RUN level in the `probe-hiltmetro` micro-spike).
 *
 * **Qualifier boundary.** `@DefaultDispatcher` is resolved HERE, on the Hilt side, where it
 * is a real `javax.inject.Qualifier` (`CoreModule` provides `@Provides @Singleton
 * @DefaultDispatcher = Dispatchers.Default`). Past the bridge, Metro receives the value
 * UNQUALIFIED — Metro does not read `javax.inject` qualifiers, and archive's graph consumes
 * exactly one `CoroutineDispatcher`, so there is no ambiguity.
 */
@EntryPoint
@InstallIn(SingletonComponent::class)
internal interface ArchiveHiltEntryPoint {

    fun navigator(): Navigator

    fun exerciseRepository(): ExerciseRepository

    fun trainingRepository(): TrainingRepository

    fun resourceWrapper(): ResourceWrapper

    fun storeDispatchers(): StoreDispatchers

    fun analyticsHolder(): AnalyticsHolder

    fun loggerHolder(): LoggerHolder

    @DefaultDispatcher
    fun defaultDispatcher(): CoroutineDispatcher
}
