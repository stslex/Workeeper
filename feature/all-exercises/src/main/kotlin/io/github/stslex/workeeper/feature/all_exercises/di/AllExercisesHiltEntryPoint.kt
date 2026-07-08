// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.all_exercises.di

import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import io.github.stslex.workeeper.core.core.di.DefaultDispatcher
import io.github.stslex.workeeper.core.core.resources.ResourceWrapper
import io.github.stslex.workeeper.core.data.exercise.exercise.ExerciseRepository
import io.github.stslex.workeeper.core.data.exercise.tags.TagRepository
import io.github.stslex.workeeper.core.ui.mvi.di.StoreDispatchers
import io.github.stslex.workeeper.core.ui.mvi.holders.AnalyticsHolder
import io.github.stslex.workeeper.core.ui.mvi.holders.LoggerHolder
import io.github.stslex.workeeper.core.ui.navigation.Navigator
import kotlinx.coroutines.CoroutineDispatcher

/**
 * Hilt→Metro bridge for feature/all-exercises (KMP C.1 wave 2). Pulls all-exercises' 8 app-scoped
 * `@Singleton` dependencies out of the Hilt `SingletonComponent` for [AllExercisesGraph] as
 * `@Provides` bound instances. Consumed via `EntryPointAccessors.fromApplication` in
 * `AllExercisesFeature.processor()`.
 *
 * `@DefaultDispatcher` stays QUALIFIED across the bridge (Metro reads it via `includeJavax`); it is
 * the only dispatcher (no collision). No Context.
 */
@EntryPoint
@InstallIn(SingletonComponent::class)
internal interface AllExercisesHiltEntryPoint {

    fun exerciseRepository(): ExerciseRepository

    fun tagRepository(): TagRepository

    fun resourceWrapper(): ResourceWrapper

    fun navigator(): Navigator

    fun storeDispatchers(): StoreDispatchers

    fun analyticsHolder(): AnalyticsHolder

    fun loggerHolder(): LoggerHolder

    @DefaultDispatcher
    fun defaultDispatcher(): CoroutineDispatcher
}
