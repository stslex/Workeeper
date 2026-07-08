// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.image_viewer.di

import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import io.github.stslex.workeeper.core.ui.mvi.di.StoreDispatchers
import io.github.stslex.workeeper.core.ui.mvi.holders.AnalyticsHolder
import io.github.stslex.workeeper.core.ui.mvi.holders.LoggerHolder
import io.github.stslex.workeeper.core.ui.navigation.Navigator

/**
 * Hilt→Metro bridge for feature/image-viewer (KMP C.1 wave 2). Pulls image-viewer's 4 app-scoped
 * `@Singleton` dependencies out of the Hilt `SingletonComponent` for [ImageViewerGraph] as
 * `@Provides` bound instances. Consumed via `EntryPointAccessors.fromApplication` in
 * `ImageViewerFeature.processor()`. No dispatcher, no Context — the minimal bridge.
 */
@EntryPoint
@InstallIn(SingletonComponent::class)
internal interface ImageViewerHiltEntryPoint {

    fun navigator(): Navigator

    fun storeDispatchers(): StoreDispatchers

    fun analyticsHolder(): AnalyticsHolder

    fun loggerHolder(): LoggerHolder
}
