// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.single_training.di

import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import io.github.stslex.workeeper.core.core.di.DefaultDispatcher
import io.github.stslex.workeeper.core.core.di.MainImmediateDispatcher
import io.github.stslex.workeeper.core.core.resources.ResourceWrapper
import io.github.stslex.workeeper.core.data.exercise.exercise.ExerciseRepository
import io.github.stslex.workeeper.core.data.exercise.session.SessionConflictResolver
import io.github.stslex.workeeper.core.data.exercise.session.SessionRepository
import io.github.stslex.workeeper.core.data.exercise.tags.TagRepository
import io.github.stslex.workeeper.core.data.exercise.training.TrainingExerciseRepository
import io.github.stslex.workeeper.core.data.exercise.training.TrainingRepository
import io.github.stslex.workeeper.core.ui.mvi.di.StoreDispatchers
import io.github.stslex.workeeper.core.ui.mvi.holders.AnalyticsHolder
import io.github.stslex.workeeper.core.ui.mvi.holders.LoggerHolder
import io.github.stslex.workeeper.core.ui.navigation.Navigator
import kotlinx.coroutines.CoroutineDispatcher

/**
 * Hilt→Metro bridge for feature/single-training (KMP C.1 wave 1). Pulls single-training's 13
 * app-scoped `@Singleton` dependencies out of the Hilt `SingletonComponent` for [SingleTrainingGraph]
 * as `@Provides` bound instances. Consumed via `EntryPointAccessors.fromApplication` in
 * `SingleTrainingFeature.processor()`. Aggregates into the app Dagger graph automatically.
 *
 * `@DefaultDispatcher` / `@MainImmediateDispatcher` stay QUALIFIED across the bridge (Metro reads
 * them via `includeJavax`), so the two same-typed `CoroutineDispatcher`s resolve distinctly. No
 * Context — this feature injects none (M2 Part A verified 0 sites).
 */
@EntryPoint
@InstallIn(SingletonComponent::class)
internal interface SingleTrainingHiltEntryPoint {

    fun trainingRepository(): TrainingRepository

    fun trainingExerciseRepository(): TrainingExerciseRepository

    fun exerciseRepository(): ExerciseRepository

    fun tagRepository(): TagRepository

    fun sessionRepository(): SessionRepository

    fun sessionConflictResolver(): SessionConflictResolver

    fun resourceWrapper(): ResourceWrapper

    fun navigator(): Navigator

    fun storeDispatchers(): StoreDispatchers

    fun analyticsHolder(): AnalyticsHolder

    fun loggerHolder(): LoggerHolder

    @DefaultDispatcher
    fun defaultDispatcher(): CoroutineDispatcher

    @MainImmediateDispatcher
    fun mainImmediateDispatcher(): CoroutineDispatcher
}
