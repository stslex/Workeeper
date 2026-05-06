// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.android.components.ActivityRetainedComponent
import io.github.stslex.workeeper.core.ui.navigation.Navigator
import io.github.stslex.workeeper.core.ui.navigation.NavigatorHolder
import io.github.stslex.workeeper.core.ui.navigation.NavigatorStack
import io.github.stslex.workeeper.navigation.NavigationHolderController
import io.github.stslex.workeeper.navigation.NavigationHolderImpl
import io.github.stslex.workeeper.navigation.NavigatorImpl

@Module
@InstallIn(ActivityRetainedComponent::class)
internal abstract class ActivityModule {

    @Binds
    abstract fun bindNavigationHolderController(
        impl: NavigationHolderImpl,
    ): NavigationHolderController

    @Binds
    abstract fun bindNavigatorHolder(impl: NavigationHolderImpl): NavigatorHolder

    @Binds
    abstract fun bindNavigator(impl: NavigatorImpl): Navigator

    @Binds
    abstract fun bindNavigatorStack(impl: NavigatorImpl): NavigatorStack
}
