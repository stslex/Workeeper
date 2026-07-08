// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.past_session.di

import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import io.github.stslex.workeeper.core.core.di.IODispatcher
import io.github.stslex.workeeper.core.core.resources.ResourceWrapper
import io.github.stslex.workeeper.core.data.exercise.personal_record.PersonalRecordRepository
import io.github.stslex.workeeper.core.data.exercise.session.SessionRepository
import io.github.stslex.workeeper.core.data.exercise.session.SetRepository
import io.github.stslex.workeeper.core.ui.mvi.di.StoreDispatchers
import io.github.stslex.workeeper.core.ui.mvi.holders.AnalyticsHolder
import io.github.stslex.workeeper.core.ui.mvi.holders.LoggerHolder
import io.github.stslex.workeeper.core.ui.navigation.Navigator
import kotlinx.coroutines.CoroutineDispatcher

/**
 * Hilt→Metro bridge for feature/past-session (KMP C.1 wave 2). Pulls past-session's 9 app-scoped
 * `@Singleton` dependencies out of the Hilt `SingletonComponent` for [PastSessionGraph] as
 * `@Provides` bound instances. Consumed via `EntryPointAccessors.fromApplication` in
 * `PastSessionFeature.processor()`.
 *
 * `@IODispatcher` stays QUALIFIED across the bridge (Metro reads it via `includeJavax`); it is the
 * only dispatcher (no collision). No Context.
 */
@EntryPoint
@InstallIn(SingletonComponent::class)
internal interface PastSessionHiltEntryPoint {

    fun sessionRepository(): SessionRepository

    fun setRepository(): SetRepository

    fun personalRecordRepository(): PersonalRecordRepository

    fun resourceWrapper(): ResourceWrapper

    fun navigator(): Navigator

    fun storeDispatchers(): StoreDispatchers

    fun analyticsHolder(): AnalyticsHolder

    fun loggerHolder(): LoggerHolder

    @IODispatcher
    fun ioDispatcher(): CoroutineDispatcher
}
