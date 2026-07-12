// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import io.github.stslex.workeeper.core.core.platform.AppReinitializer
import io.github.stslex.workeeper.core.core.platform.PlatformInfoProvider
import io.github.stslex.workeeper.core.core.platform.TempFileProvider
import io.github.stslex.workeeper.core.data.backup.api.restore.RestoreStateRepository
import io.github.stslex.workeeper.core.data.backup.api.scheduling.AutoBackupController
import io.github.stslex.workeeper.core.data.backup.api.scheduling.BackupPreferencesRepository
import io.github.stslex.workeeper.core.data.backup.worker.notification.BackupNotificationHelper
import io.github.stslex.workeeper.core.ui.kit.utils.activityHolder.ActivityHolder
import io.github.stslex.workeeper.core.ui.kit.utils.activityHolder.ActivityHolderProducer
import io.github.stslex.workeeper.core.ui.mvi.di.StoreDispatchers
import io.github.stslex.workeeper.core.ui.mvi.holders.AnalyticsHolder
import io.github.stslex.workeeper.core.ui.mvi.holders.LoggerHolder
import io.github.stslex.workeeper.feature.app_dialogs.api.observer.AppDialogObserver
import io.github.stslex.workeeper.feature.app_dialogs.api.publisher.AppDialogPublisher
import io.github.stslex.workeeper.feature.app_dialogs.impl.data.AppDialogRepository
import io.github.stslex.workeeper.feature.app_dialogs.impl.observer.AppDialogObserverImpl
import javax.inject.Singleton

/**
 * ADOPT-BACK seam (KMP C.1 app-collapse Phase 1 — leaf E-proof). The direction-mirror of the batch
 * bridge: the batch is Metro-reads-Hilt (features pull app-scoped `@Singleton`s via `*HiltEntryPoint`);
 * adopt-back is Hilt-reads-Metro — a still-Hilt-owned consumer resolves a now-Metro-owned binding
 * through a thin Hilt `@Provides` that DELEGATES to the app-graph accessor.
 *
 * The `AppGraph` binding these shims consume is provided by [AppGraphSourceModule] — the SINGLE unit
 * that reaches the [BaseApplication]-held graph (or builds the real one under test). This module holds
 * ONLY the delegating shims and is NEVER `@TestInstallIn`-replaced, so tests exercise the REAL
 * delegation (Phase D2 decouple: previously the whole module was replaced by a hand-copied test double,
 * so the `===` proof exercised the copy, not the production shim).
 *
 * SINGLE-OWNER DISCIPLINE: [provideAnalyticsHolder] returns `appGraph.analyticsHolder` — the SAME
 * instance the Metro graph constructed and retains (`@SingleIn(AppScope)`). It NEVER constructs a
 * parallel Hilt-side `AnalyticsHolder`. `AnalyticsHolder`'s `@Inject`/`@Singleton` were stripped so
 * this is the ONLY Hilt binding for the type — no duplicate binding, no second owner (the
 * double-instance `===`-split class). Every one of the 13 `*HiltEntryPoint.analyticsHolder()`
 * accessors resolves through this provider.
 */
// TooManyFunctions: this object is the App-Scope Collapse shim aggregator — it holds exactly one
// trivial delegating `@Provides` per Metro-owned binding that still has a Hilt-side reader. The
// function count IS the migrated-binding count (it grows one line per flip and collapses to zero
// when Hilt is removed in Step 6), not an extractable smell — the same rationale that exempts the
// per-feature `@EntryPoint` bridges in detekt.yml.
@Suppress("TooManyFunctions")
@Module
@InstallIn(SingletonComponent::class)
internal object AppGraphAdoptBackModule {

    /**
     * Adopt-back for the leaf: delegate to the graph's owned instance. `@Singleton` caches the
     * delegate in `SingletonComponent`, but since the target is a graph singleton the value === the
     * graph's either way.
     */
    @Provides
    @Singleton
    fun provideAnalyticsHolder(
        appGraph: AppGraph,
    ): AnalyticsHolder = appGraph.analyticsHolder

    /**
     * Adopt-back for [LoggerHolder] (App-Scope Collapse Step 3, mvi slice). Returns
     * `appGraph.loggerHolder` — the SAME instance the Metro graph retains (`@SingleIn(AppScope)`),
     * never a parallel Hilt construction (`LoggerHolder`'s Hilt `@Inject`/`@Singleton` were stripped, so
     * this is the ONLY Hilt binding). The 13 `*HiltEntryPoint.loggerHolder()` readers + the `BaseStore`
     * ctor param resolve through this single provider.
     */
    @Provides
    @Singleton
    fun provideLoggerHolder(
        appGraph: AppGraph,
    ): LoggerHolder = appGraph.loggerHolder

    /**
     * Adopt-back for [StoreDispatchers] (App-Scope Collapse Step 3, mvi slice). Same single-owner
     * delegation: returns the graph's retained instance for the 13 `*HiltEntryPoint.storeDispatchers()`
     * readers.
     */
    @Provides
    @Singleton
    fun provideStoreDispatchers(
        appGraph: AppGraph,
    ): StoreDispatchers = appGraph.storeDispatchers

    /**
     * Adopt-back for [BackupPreferencesRepository] (App-Scope Collapse Step 3, backup/scheduling slice).
     * Single-owner delegation: returns `appGraph.backupPreferencesRepository`, the SAME instance the Metro
     * graph retains (`@ContributesBinding` + `@SingleIn(AppScope)`), never a parallel construction. The
     * `SettingsHiltEntryPoint` + `BackupWorkerHiltEntryPoint` readers resolve through this single provider.
     */
    @Provides
    @Singleton
    fun provideBackupPreferencesRepository(
        appGraph: AppGraph,
    ): BackupPreferencesRepository = appGraph.backupPreferencesRepository

    /**
     * Adopt-back for [ActivityHolder] (App-Scope Collapse Step 3, ui-kit slice). Single-owner delegation to
     * the Metro graph's retained `ActivityHolderImpl`. Read by the still-Hilt `ResourceManagerImpl` (L1).
     */
    @Provides
    @Singleton
    fun provideActivityHolder(appGraph: AppGraph): ActivityHolder = appGraph.activityHolder

    /**
     * Adopt-back for [ActivityHolderProducer] — the SAME `ActivityHolderImpl` instance (repeatable
     * `@ContributesBinding` binds both types to one owner). Read by `MainActivity` (@Inject field).
     */
    @Provides
    @Singleton
    fun provideActivityHolderProducer(appGraph: AppGraph): ActivityHolderProducer = appGraph.activityHolderProducer

    /** Adopt-back: PlatformInfoProvider (read by RestoreRecoveryCoordinator + SettingsHiltEntryPoint). */
    @Provides
    @Singleton
    fun providePlatformInfoProvider(appGraph: AppGraph): PlatformInfoProvider = appGraph.platformInfoProvider

    /** Adopt-back: TempFileProvider (read by SettingsHiltEntryPoint). */
    @Provides
    @Singleton
    fun provideTempFileProvider(appGraph: AppGraph): TempFileProvider = appGraph.tempFileProvider

    /** Adopt-back: AppReinitializer (read by NavigatorEventBus + RestoreRecoveryCoordinator). */
    @Provides
    @Singleton
    fun provideAppReinitializer(appGraph: AppGraph): AppReinitializer = appGraph.appReinitializer

    /**
     * Adopt-back: RestoreStateRepository — read by RestoreDialogChoiceObserver, RestoreRecoveryCoordinator,
     * and SettingsHiltEntryPoint (all still-Hilt at this layer).
     */
    @Provides
    @Singleton
    fun provideRestoreStateRepository(
        appGraph: AppGraph,
    ): RestoreStateRepository = appGraph.restoreStateRepository

    /**
     * Adopt-back: AutoBackupController — read by BackupWorkerHiltEntryPoint (Step-2 bridge) and
     * SettingsHiltEntryPoint.
     */
    @Provides
    @Singleton
    fun provideAutoBackupController(
        appGraph: AppGraph,
    ): AutoBackupController = appGraph.autoBackupController

    /**
     * Adopt-back: BackupNotificationHelper — read by BackupWorkerHiltEntryPoint (Step-2 bridge).
     */
    @Provides
    @Singleton
    fun provideBackupNotificationHelper(
        appGraph: AppGraph,
    ): BackupNotificationHelper = appGraph.backupNotificationHelper

    /**
     * Adopt-back: AppDialogRepository (App-Scope Collapse Step 3, app-dialogs slice) — the concrete
     * self-bound singleton, read by the feature's OWN `AppDialogsHiltEntryPoint.appDialogRepository()`
     * that feeds its Metro `AppDialogGraph`. Delegates to the single Metro-owned instance.
     */
    @Provides
    @Singleton
    fun provideAppDialogRepository(
        appGraph: AppGraph,
    ): AppDialogRepository = appGraph.appDialogRepository

    /**
     * Adopt-back: AppDialogObserverImpl (concrete) — read by `AppDialogsHiltEntryPoint.appDialogObserverImpl()`.
     * SAME instance the [provideAppDialogObserver] interface shim returns (one `@SingleIn(AppScope)`).
     */
    @Provides
    @Singleton
    fun provideAppDialogObserverImpl(
        appGraph: AppGraph,
    ): AppDialogObserverImpl = appGraph.appDialogObserverImpl

    /**
     * Adopt-back: AppDialogObserver (api interface) — read cross-module by recovery / archive /
     * `BaseApplication` via their `*HiltEntryPoint`s. Delegates to the Metro-owned `AppDialogObserverImpl`.
     */
    @Provides
    @Singleton
    fun provideAppDialogObserver(
        appGraph: AppGraph,
    ): AppDialogObserver = appGraph.appDialogObserver

    /**
     * Adopt-back: AppDialogPublisher (api interface) — read cross-module by settings
     * (`SettingsHiltEntryPoint.appDialogPublisher()` + `BackupClickHandler`). Delegates to the
     * Metro-owned `AppDialogPublisherImpl`.
     */
    @Provides
    @Singleton
    fun provideAppDialogPublisher(
        appGraph: AppGraph,
    ): AppDialogPublisher = appGraph.appDialogPublisher
}
