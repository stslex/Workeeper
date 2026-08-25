// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.settings.di

import android.content.Context
import dev.zacsweers.metro.Binds
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.GraphExtension
import io.github.stslex.workeeper.core.core.di.AppScope
import io.github.stslex.workeeper.core.core.di.DefaultDispatcher
import io.github.stslex.workeeper.core.core.di.IODispatcher
import io.github.stslex.workeeper.feature.settings.domain.BackupInteractor
import io.github.stslex.workeeper.feature.settings.domain.BackupInteractorImpl
import io.github.stslex.workeeper.feature.settings.domain.SettingsInteractor
import io.github.stslex.workeeper.feature.settings.domain.SettingsInteractorImpl
import io.github.stslex.workeeper.feature.settings.mvi.store.SettingsStoreImpl
import kotlinx.coroutines.CoroutineDispatcher

/**
 * feature/settings' Metro graph, a contributed [GraphExtension] of [SettingsScope] merged into the
 * app graph. See documentation/graph-extension-arc/HANDOFF.md.
 */
@GraphExtension(SettingsScope::class)
interface SettingsGraph {

    /** Root accessor: the retained Store. Metro constructs [SettingsStoreImpl], wiring its deps. */
    val settingsStore: SettingsStoreImpl

    // The three accessors below have no production consumer: SettingsExtensionIdentityTest in
    // :app reads them to prove the extension inherits AppGraph's instances without cross-wiring.
    @DefaultDispatcher
    val defaultDispatcher: CoroutineDispatcher

    @IODispatcher
    val ioDispatcher: CoroutineDispatcher

    val appContext: Context

    @Binds
    val SettingsInteractorImpl.bindSettingsInteractor: SettingsInteractor

    @Binds
    val BackupInteractorImpl.bindBackupInteractor: BackupInteractor

    @Binds
    val SettingsHandlerStoreImpl.bindHandlerStore: SettingsHandlerStore

    /**
     * GUARD: the creator method name must be unique across all contributed extension factories —
     * they all merge into `AppGraph`. See documentation/graph-extension-arc/HANDOFF.md.
     */
    @ContributesTo(AppScope::class)
    @GraphExtension.Factory
    fun interface Factory {
        fun createSettingsGraph(): SettingsGraph
    }
}
