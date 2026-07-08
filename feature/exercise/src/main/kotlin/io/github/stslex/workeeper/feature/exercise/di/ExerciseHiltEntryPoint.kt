// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.exercise.di

import android.content.Context
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
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
import kotlinx.coroutines.CoroutineDispatcher

/**
 * Hilt→Metro bridge for feature/exercise (KMP C.1 wave 1). Pulls exercise's 14 app-scoped
 * `@Singleton` dependencies out of the Hilt `SingletonComponent` so they can be handed to
 * [ExerciseGraph] as `@Provides` bound instances. Consumed via `EntryPointAccessors.fromApplication`
 * in `ExerciseFeature.processor()`. Aggregates into the app Dagger graph automatically — no
 * app-module change.
 *
 * Qualifier boundary:
 * - `@DefaultDispatcher` / `@MainImmediateDispatcher` stay QUALIFIED across the bridge (Metro reads
 *   them via `includeJavax`), so the two same-typed `CoroutineDispatcher`s resolve distinctly.
 * - `@ApplicationContext` stays on the Hilt side HERE (Hilt resolves the app `Context`); the graph
 *   binds it UNqualified downstream (one `Context` per graph → no ambiguity). Metro never sees it.
 */
@EntryPoint
@InstallIn(SingletonComponent::class)
internal interface ExerciseHiltEntryPoint {

    fun exerciseRepository(): ExerciseRepository

    fun tagRepository(): TagRepository

    fun imageStorage(): ImageStorage

    fun personalRecordRepository(): PersonalRecordRepository

    fun sessionRepository(): SessionRepository

    fun trainingRepository(): TrainingRepository

    fun resourceWrapper(): ResourceWrapper

    fun navigator(): Navigator

    fun storeDispatchers(): StoreDispatchers

    fun analyticsHolder(): AnalyticsHolder

    fun loggerHolder(): LoggerHolder

    @DefaultDispatcher
    fun defaultDispatcher(): CoroutineDispatcher

    @MainImmediateDispatcher
    fun mainImmediateDispatcher(): CoroutineDispatcher

    @ApplicationContext
    fun applicationContext(): Context
}
