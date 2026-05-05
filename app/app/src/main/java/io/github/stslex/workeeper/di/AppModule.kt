// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import io.github.stslex.workeeper.core.ui.navigation.Navigator
import io.github.stslex.workeeper.core.ui.navigation.NavigatorHolder
import io.github.stslex.workeeper.core.ui.navigation.NavigatorStack
import io.github.stslex.workeeper.navigation.NavigationHolderImpl
import io.github.stslex.workeeper.navigation.NavigationHolderProducer
import io.github.stslex.workeeper.navigation.NavigatorImpl
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
internal object AppModule {

    @Provides
    @Singleton
    fun provideNavigatorHolderImpl(): NavigationHolderImpl = NavigationHolderImpl()

    @Provides
    @Singleton
    fun provideNavigatorHolder(impl: NavigationHolderImpl): NavigatorHolder = impl

    @Provides
    @Singleton
    fun provideNavigatorHolderProducer(impl: NavigationHolderImpl): NavigationHolderProducer = impl

    @Provides
    @Singleton
    fun provideNavigatorImpl(holder: NavigatorHolder): NavigatorImpl = NavigatorImpl(holder)

    @Provides
    @Singleton
    fun provideNavigator(impl: NavigatorImpl): Navigator = impl

    @Provides
    @Singleton
    fun provideNavigatorStack(impl: NavigatorImpl): NavigatorStack = impl
}
