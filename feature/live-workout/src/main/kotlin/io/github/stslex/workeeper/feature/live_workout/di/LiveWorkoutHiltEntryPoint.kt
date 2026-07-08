// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.live_workout.di

import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import io.github.stslex.workeeper.core.core.di.DefaultDispatcher
import io.github.stslex.workeeper.core.core.resources.ResourceWrapper
import io.github.stslex.workeeper.core.data.exercise.exercise.ExerciseRepository
import io.github.stslex.workeeper.core.data.exercise.personal_record.PersonalRecordRepository
import io.github.stslex.workeeper.core.data.exercise.session.PerformedExerciseRepository
import io.github.stslex.workeeper.core.data.exercise.session.SessionRepository
import io.github.stslex.workeeper.core.data.exercise.session.SetRepository
import io.github.stslex.workeeper.core.data.exercise.training.TrainingExerciseRepository
import io.github.stslex.workeeper.core.data.exercise.training.TrainingRepository
import io.github.stslex.workeeper.core.ui.mvi.di.StoreDispatchers
import io.github.stslex.workeeper.core.ui.mvi.holders.AnalyticsHolder
import io.github.stslex.workeeper.core.ui.mvi.holders.LoggerHolder
import io.github.stslex.workeeper.core.ui.navigation.Navigator
import kotlinx.coroutines.CoroutineDispatcher

/**
 * Hilt→Metro bridge for feature/live-workout (KMP C.1 wave 3) — the largest feature. Pulls the 13
 * app-scoped `@Singleton` dependencies out of the Hilt `SingletonComponent` for [LiveWorkoutGraph]
 * as `@Provides` bound instances. `@DefaultDispatcher` stays QUALIFIED (`includeJavax`), the only
 * dispatcher. No Context. (LiveSetMutator / StateStatusMapper / ExercisePickerHandler are
 * feature-scoped `@ViewModelScoped` — Metro-constructed inside the graph, NOT bridged.)
 */
@EntryPoint
@InstallIn(SingletonComponent::class)
internal interface LiveWorkoutHiltEntryPoint {

    fun exerciseRepository(): ExerciseRepository

    fun performedExerciseRepository(): PerformedExerciseRepository

    fun personalRecordRepository(): PersonalRecordRepository

    fun sessionRepository(): SessionRepository

    fun setRepository(): SetRepository

    fun trainingExerciseRepository(): TrainingExerciseRepository

    fun trainingRepository(): TrainingRepository

    fun resourceWrapper(): ResourceWrapper

    fun navigator(): Navigator

    fun storeDispatchers(): StoreDispatchers

    fun analyticsHolder(): AnalyticsHolder

    fun loggerHolder(): LoggerHolder

    @DefaultDispatcher
    fun defaultDispatcher(): CoroutineDispatcher
}
