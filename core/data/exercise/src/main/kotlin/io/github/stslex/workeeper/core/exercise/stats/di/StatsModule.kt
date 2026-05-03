// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.core.exercise.stats.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import io.github.stslex.workeeper.core.exercise.stats.StatsRepository
import io.github.stslex.workeeper.core.exercise.stats.StatsRepositoryImpl
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
internal interface StatsModule {

    @Binds
    @Singleton
    fun bindStatsRepository(impl: StatsRepositoryImpl): StatsRepository
}
