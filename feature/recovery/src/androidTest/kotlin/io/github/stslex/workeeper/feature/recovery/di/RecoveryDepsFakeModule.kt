// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.recovery.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import io.github.stslex.workeeper.core.data.backup.api.restore.RestoreStateRepository
import io.github.stslex.workeeper.feature.app_dialogs.api.observer.AppDialogObserver
import io.github.stslex.workeeper.feature.app_dialogs.api.publisher.AppDialogPublisher
import io.mockk.mockk
import javax.inject.Singleton

/**
 * Relaxed-mock providers for the cross-feature interfaces the recovery
 * `@Singleton` graph depends on but whose production implementations are NOT
 * on `feature/recovery`'s androidTest classpath (they live in
 * `feature/app-dialogs/impl` and `core/data/backup/google-drive`).
 *
 * Hilt validates the full SingletonComponent graph including bindings that
 * are only reachable through `EntryPoint`s — and [boot.AppDialogObserverBootstrapEntryPoint]
 * pulls in the observer's constructor parameters transitively. Without these
 * fakes the test apk fails to compile the Hilt graph; the activity under test
 * never reaches its onCreate.
 *
 * The fakes are intentionally do-nothing: the activity does not interact
 * with these interfaces directly, and the observer's `init { ... }` block
 * subscribing to a `mockk` SharedFlow is harmless (relaxed mode returns
 * an empty-ish Flow; no signal is ever delivered, no side-effect fires).
 */
@Module
@InstallIn(SingletonComponent::class)
internal object RecoveryDepsFakeModule {

    @Provides
    @Singleton
    internal fun provideAppDialogObserver(): AppDialogObserver = mockk(relaxed = true)

    @Provides
    @Singleton
    internal fun provideAppDialogPublisher(): AppDialogPublisher = mockk(relaxed = true)

    @Provides
    @Singleton
    internal fun provideRestoreStateRepository(): RestoreStateRepository = mockk(relaxed = true)
}
