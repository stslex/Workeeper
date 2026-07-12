// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import io.github.stslex.workeeper.core.core.di.DefaultDispatcher
import io.github.stslex.workeeper.core.core.di.IODispatcher
import io.github.stslex.workeeper.core.core.di.MainImmediateDispatcher
import io.github.stslex.workeeper.core.core.platform.AppReinitializer
import io.github.stslex.workeeper.core.core.platform.PlatformInfoProvider
import io.github.stslex.workeeper.core.core.platform.TempFileProvider
import io.github.stslex.workeeper.core.core.resources.ResourceWrapper
import io.github.stslex.workeeper.core.data.backup.api.BackupAuth
import io.github.stslex.workeeper.core.data.backup.api.BackupStorage
import io.github.stslex.workeeper.core.data.backup.api.SnapshotStorage
import io.github.stslex.workeeper.core.data.backup.api.restore.RestoreStateRepository
import io.github.stslex.workeeper.core.data.backup.api.scheduling.AutoBackupController
import io.github.stslex.workeeper.core.data.backup.api.scheduling.BackupPreferencesRepository
import io.github.stslex.workeeper.core.data.backup.google_drive.auth.AccountDataStore
import io.github.stslex.workeeper.core.data.backup.worker.notification.BackupNotificationHelper
import io.github.stslex.workeeper.core.data.exercise.exercise.ExerciseRepository
import io.github.stslex.workeeper.core.data.exercise.personal_record.PersonalRecordRepository
import io.github.stslex.workeeper.core.data.exercise.session.PerformedExerciseRepository
import io.github.stslex.workeeper.core.data.exercise.session.SessionRepository
import io.github.stslex.workeeper.core.data.exercise.session.SetRepository
import io.github.stslex.workeeper.core.data.exercise.tags.TagRepository
import io.github.stslex.workeeper.core.data.exercise.training.TrainingExerciseRepository
import io.github.stslex.workeeper.core.data.exercise.training.TrainingRepository
import io.github.stslex.workeeper.core.ui.kit.utils.activityHolder.ActivityHolder
import io.github.stslex.workeeper.core.ui.kit.utils.activityHolder.ActivityHolderProducer
import io.github.stslex.workeeper.core.ui.mvi.di.StoreDispatchers
import io.github.stslex.workeeper.core.ui.mvi.holders.AnalyticsHolder
import io.github.stslex.workeeper.core.ui.mvi.holders.LoggerHolder
import io.github.stslex.workeeper.core.ui.navigation.Navigator
import io.github.stslex.workeeper.feature.app_dialogs.api.observer.AppDialogObserver
import io.github.stslex.workeeper.feature.app_dialogs.api.publisher.AppDialogPublisher
import io.github.stslex.workeeper.feature.app_dialogs.impl.data.AppDialogRepository
import io.github.stslex.workeeper.feature.app_dialogs.impl.observer.AppDialogObserverImpl
import io.github.stslex.workeeper.navigation.NavigatorEventBus
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
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

    /**
     * Adopt-back: AccountDataStore (App-Scope Collapse Step 3, google-drive slice) — read by the four
     * still-Hilt gd consumers (DriveAuthTokenProvider / DriveBackupAuth / DriveTokenInvalidator /
     * DriveSnapshotStorage). Delegates to the single Metro-owned `AccountDataStoreImpl`.
     */
    @Provides
    @Singleton
    fun provideAccountDataStore(
        appGraph: AppGraph,
    ): AccountDataStore = appGraph.accountDataStore

    /**
     * Hilt-side app CoroutineDispatchers (App-Scope Collapse Step 3, PF commit 1; back-edge correction C2-h').
     *
     * These provide `Dispatchers.IO/.Default/.Main.immediate` DIRECTLY — NOT via `appGraph`. The dispatchers
     * are STATELESS kotlinx process-singletons, so the adopt-back single-owner discipline (which guards a
     * STATEFUL binding from being double-instantiated) is MOOT: a direct `@Provides` returns the IDENTICAL
     * object the Metro `DispatchersBindingContainer` holds (asserted `===` in the seam). Routing them through
     * `appGraph` (the original PF.1 shim) created a latent back-edge — Hilt `@IO` depended on `appGraph` — and
     * any Hilt binding built INSIDE `appGraph` construction that needs `@IO` (C2's `DbTransitionRunner` /
     * `ImageStorageImpl`; Step-5's db-cascade) re-enters the graph mid-build → `StackOverflowError`. Providing
     * the stateless value directly dissolves the back-edge for C2 and Step 5. The Metro-side
     * `DispatchersBindingContainer` is untouched — Metro consumers still resolve from the graph. `@MainDispatcher`
     * has zero consumers, so it needs no Hilt shim.
     */
    @Provides
    @Singleton
    @DefaultDispatcher
    fun provideDefaultDispatcher(): CoroutineDispatcher = Dispatchers.Default

    @Provides
    @Singleton
    @MainImmediateDispatcher
    fun provideMainImmediateDispatcher(): CoroutineDispatcher = Dispatchers.Main.immediate

    @Provides
    @Singleton
    @IODispatcher
    fun provideIODispatcher(): CoroutineDispatcher = Dispatchers.IO

    /**
     * Adopt-back for [ResourceWrapper] (App-Scope Collapse Step 3, PF commit 2A). Metro-owned via
     * `ResourceWrapperBindingContainer`; the ten feature `*HiltEntryPoint.resourceWrapper()` bridges
     * resolve the singleton through this delegating shim.
     */
    @Provides
    @Singleton
    fun provideResourceWrapper(
        appGraph: AppGraph,
    ): ResourceWrapper = appGraph.resourceWrapper

    /**
     * Adopt-back for the Navigator subsystem (App-Scope Collapse Step 3, PF commit 2C). The one
     * Metro-owned `NavigatorEventBus` is re-provided into Hilt as [Navigator] (for the 12 feature
     * `*HiltEntryPoint.navigator()` bridges) AND as its concrete type (for `AppRootViewModel`, which
     * injects the concrete). Both delegate to the SAME `@SingleIn(AppScope)` instance.
     */
    @Provides
    @Singleton
    fun provideNavigator(
        appGraph: AppGraph,
    ): Navigator = appGraph.navigator

    @Provides
    @Singleton
    fun provideNavigatorEventBus(
        appGraph: AppGraph,
    ): NavigatorEventBus = appGraph.navigatorEventBus

    /**
     * Adopt-back for the google-drive auth-chain facade (App-Scope Collapse Step 3, PF.3). All three are
     * GMS/ktor-CLEAN api interfaces — the GMS `AuthorizationClient` + ktor `HttpClient` stay inside gd's
     * `@BindingContainer`s and are never named here (HOME-A containment).
     *  - [provideBackupAuth] / [provideBackupStorage] — read by still-Hilt settings + worker EntryPoints.
     *  - [provideSnapshotStorage] — read by the still-Hilt `SnapshotExportRunnerImpl` (deferred to Step 5).
     *    TRANSIENT: retired when SnapshotExportRunner migrates with its db-cascade tether.
     */
    @Provides
    @Singleton
    fun provideBackupAuth(
        appGraph: AppGraph,
    ): BackupAuth = appGraph.backupAuth

    @Provides
    @Singleton
    fun provideBackupStorage(
        appGraph: AppGraph,
    ): BackupStorage = appGraph.backupStorage

    @Provides
    @Singleton
    fun provideSnapshotStorage(
        appGraph: AppGraph,
    ): SnapshotStorage = appGraph.snapshotStorage

    /**
     * Adopt-back for the exercise repositories (App-Scope Collapse Step 3, C2). Each is Metro-owned via
     * `@ContributesBinding(AppScope)` on its impl; these single-owner shims re-provide them into Hilt's
     * `SingletonComponent` for the still-Hilt readers — the feature `*HiltEntryPoint` bridges AND every
     * pure-Hilt `@Inject` consumer (interactors / handlers / use cases). One shim per type serves all its
     * consumers. `StatsRepository` has zero consumers (dead) — no shim.
     */
    @Provides
    @Singleton
    fun provideExerciseRepository(appGraph: AppGraph): ExerciseRepository = appGraph.exerciseRepository

    @Provides
    @Singleton
    fun provideSessionRepository(appGraph: AppGraph): SessionRepository = appGraph.sessionRepository

    @Provides
    @Singleton
    fun provideSetRepository(appGraph: AppGraph): SetRepository = appGraph.setRepository

    @Provides
    @Singleton
    fun provideTagRepository(appGraph: AppGraph): TagRepository = appGraph.tagRepository

    @Provides
    @Singleton
    fun providePersonalRecordRepository(
        appGraph: AppGraph,
    ): PersonalRecordRepository = appGraph.personalRecordRepository

    @Provides
    @Singleton
    fun providePerformedExerciseRepository(
        appGraph: AppGraph,
    ): PerformedExerciseRepository = appGraph.performedExerciseRepository

    @Provides
    @Singleton
    fun provideTrainingExerciseRepository(
        appGraph: AppGraph,
    ): TrainingExerciseRepository = appGraph.trainingExerciseRepository

    @Provides
    @Singleton
    fun provideTrainingRepository(appGraph: AppGraph): TrainingRepository = appGraph.trainingRepository
}
