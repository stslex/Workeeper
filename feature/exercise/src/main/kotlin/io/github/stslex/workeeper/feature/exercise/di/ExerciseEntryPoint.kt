package io.github.stslex.workeeper.feature.exercise.di

import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.components.ActivityComponent
import io.github.stslex.workeeper.feature.exercise.ui.mvi.store.ExerciseStoreImpl

@EntryPoint
@InstallIn(ActivityComponent::class)
interface ExerciseEntryPoint {
    fun exerciseStoreFactory(): ExerciseStoreImpl.Factory
}
