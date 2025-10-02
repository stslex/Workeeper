package io.github.stslex.workeeper.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import io.github.stslex.workeeper.core.ui.navigation.Navigator
import io.github.stslex.workeeper.navigation.NavigatorImpl
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
interface NavigationModule {

    @Binds
    @Singleton
    fun bindNavigator(impl: NavigatorImpl): Navigator
}
