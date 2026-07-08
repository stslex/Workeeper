// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.all_trainings.di

import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import io.github.stslex.workeeper.core.core.di.DefaultDispatcher
import io.github.stslex.workeeper.core.core.resources.ResourceWrapper
import io.github.stslex.workeeper.core.data.exercise.tags.TagRepository
import io.github.stslex.workeeper.core.data.exercise.training.TrainingRepository
import io.github.stslex.workeeper.core.ui.mvi.di.StoreDispatchers
import io.github.stslex.workeeper.core.ui.mvi.holders.AnalyticsHolder
import io.github.stslex.workeeper.core.ui.mvi.holders.LoggerHolder
import io.github.stslex.workeeper.core.ui.navigation.Navigator
import kotlinx.coroutines.CoroutineDispatcher

/**
 * Hilt→Metro bridge for feature/all-trainings (KMP C.1 wave 3). Pulls the 8 app-scoped `@Singleton`
 * dependencies out of the Hilt `SingletonComponent` for [AllTrainingsGraph] as `@Provides` bound
 * instances. `@DefaultDispatcher` stays QUALIFIED (`includeJavax`), the only dispatcher. No Context.
 */
@EntryPoint
@InstallIn(SingletonComponent::class)
internal interface AllTrainingsHiltEntryPoint {

    fun trainingRepository(): TrainingRepository

    fun tagRepository(): TagRepository

    fun resourceWrapper(): ResourceWrapper

    fun navigator(): Navigator

    fun storeDispatchers(): StoreDispatchers

    fun analyticsHolder(): AnalyticsHolder

    fun loggerHolder(): LoggerHolder

    @DefaultDispatcher
    fun defaultDispatcher(): CoroutineDispatcher
}
