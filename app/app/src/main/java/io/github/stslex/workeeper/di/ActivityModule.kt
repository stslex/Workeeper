// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import io.github.stslex.workeeper.core.ui.navigation.Navigator
import io.github.stslex.workeeper.navigation.NavigatorEventBus
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
class ActivityModule {

    @Provides
    @Singleton
    fun provideNavigatorEventBus(): NavigatorEventBus = NavigatorEventBus()

    @Provides
    @Singleton
    fun provideNavigator(impl: NavigatorEventBus): Navigator = impl
}
