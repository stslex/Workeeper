// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.di

import android.content.Context
import dev.zacsweers.metro.DependencyGraph
import dev.zacsweers.metro.Provides
import dev.zacsweers.metro.SingleIn
import io.github.stslex.workeeper.core.core.di.AppScope
import io.github.stslex.workeeper.core.core.di.DefaultDispatcher
import io.github.stslex.workeeper.core.core.di.DispatchersBindingContainer
import io.github.stslex.workeeper.core.core.di.IODispatcher
import io.github.stslex.workeeper.core.core.di.MainImmediateDispatcher
import io.github.stslex.workeeper.core.core.platform.AppReinitializer
import io.github.stslex.workeeper.core.core.platform.PlatformInfoProvider
import io.github.stslex.workeeper.core.core.platform.TempFileProvider
import io.github.stslex.workeeper.core.core.resources.ResourceWrapper
import io.github.stslex.workeeper.core.data.backup.api.restore.RestoreStateRepository
import io.github.stslex.workeeper.core.data.backup.api.scheduling.AutoBackupController
import io.github.stslex.workeeper.core.data.backup.api.scheduling.BackupPreferencesRepository
import io.github.stslex.workeeper.core.data.backup.google_drive.auth.AccountDataStore
import io.github.stslex.workeeper.core.data.backup.worker.notification.BackupNotificationHelper
import io.github.stslex.workeeper.core.ui.kit.utils.NumUiUtils
import io.github.stslex.workeeper.core.ui.kit.utils.activityHolder.ActivityHolder
import io.github.stslex.workeeper.core.ui.kit.utils.activityHolder.ActivityHolderProducer
import io.github.stslex.workeeper.core.ui.mvi.di.StoreDispatchers
import io.github.stslex.workeeper.core.ui.mvi.holders.AnalyticsHolder
import io.github.stslex.workeeper.core.ui.mvi.holders.LoggerHolder
import io.github.stslex.workeeper.feature.app_dialogs.api.observer.AppDialogObserver
import io.github.stslex.workeeper.feature.app_dialogs.api.publisher.AppDialogPublisher
import io.github.stslex.workeeper.feature.app_dialogs.impl.data.AppDialogRepository
import io.github.stslex.workeeper.feature.app_dialogs.impl.observer.AppDialogObserverImpl
import kotlinx.coroutines.CoroutineDispatcher

/**
 * The Metro app-scope dependency graph (KMP C.1 app-collapse Phase 1 — leaf E-proof).
 *
 * Stood up ALONGSIDE `@HiltAndroidApp` (a second dual-path, now at the app-scope tier — the
 * mirror of the feature-tier dual-path shipped 13 times). Held by `BaseApplication` for the whole
 * process. Factory-shaped ([Factory]) per the locked C decision: the app `Context` enters as a
 * `@Provides` bound instance via `create(...)`, so nothing reads Hilt's `@ApplicationContext`
 * through this graph and the graph interface stays small.
 *
 * OWNS exactly one app-scoped binding in this leaf spike: [AnalyticsHolder], constructed and
 * retained by the graph (`@Provides @SingleIn(AppScope)`). `AnalyticsHolder`'s `@Inject`/`@Singleton`
 * were stripped so Hilt no longer auto-binds it (single-owner). Hilt-side readers — the 13
 * `*HiltEntryPoint.analyticsHolder()` accessors — resolve it through a delegating Hilt `@Provides`
 * ([AppGraphAdoptBackModule]) that returns THIS graph's instance, never a parallel one. That
 * delegating read is the adopt-back seam this phase proves identity-preserving.
 */
@DependencyGraph(scope = AppScope::class)
internal interface AppGraph {

    /** Root accessor: the single app-scoped [AnalyticsHolder] the adopt-back `@Provides` delegates to. */
    val analyticsHolder: AnalyticsHolder

    /**
     * App-Scope Collapse Step 3 (SB1). Metro-owned [NumUiUtils] — CONTRIBUTED by
     * `@ContributesBinding(AppScope::class)` on `NumUiUtilsImpl` in its own module (`core:ui:kit`),
     * which `@DependencyGraph(AppScope::class)` auto-aggregates. No `@Provides` here: the impl is
     * `internal` to `core:ui:kit` and app/app cannot reference it, so ownership lives at the impl via
     * contribution (the visibility-respecting Metro mechanic). CLEAN migration: no app-scope Hilt
     * consumer, so no adopt-back `@Provides`; this accessor exposes the binding for identity tests.
     */
    val numUiUtils: NumUiUtils

    /**
     * App-Scope Collapse Step 3 (SB1, core:ui:mvi slice). Metro-owned [LoggerHolder] — a concrete
     * self-bound class (no interface), so it carries `@SingleIn(AppScope)` + `@Inject` (NOT
     * `@ContributesBinding`, which binds to a supertype); THIS accessor pulls it into the graph as a
     * retained singleton. The 13 `*HiltEntryPoint.loggerHolder()` readers + the `BaseStore` ctor param
     * resolve it via the single adopt-back `@Provides` ([AppGraphAdoptBackModule]) delegating here.
     */
    val loggerHolder: LoggerHolder

    /**
     * App-Scope Collapse Step 3 (SB1, core:ui:mvi slice). Metro-owned [StoreDispatchers]. Its two
     * qualified `CoroutineDispatcher` ctor deps now resolve from the graph's own
     * [DispatchersBindingContainer] (App-Scope Collapse Step 3, PF commit 1) — no longer bridged through
     * `create()`. `includeJavax` carries the qualifiers.
     */
    val storeDispatchers: StoreDispatchers

    /**
     * App-Scope Collapse Step 3 (PF commit 1). The four Metro-owned CoroutineDispatchers, CONTRIBUTED by
     * [DispatchersBindingContainer] (`@BindingContainer @ContributesTo(AppScope)`). Exposed as QUALIFIED
     * accessors so the qualified adopt-back `@Provides` in `AppGraphAdoptBackModule` re-provide them into
     * Hilt's `SingletonComponent` for the still-Hilt readers (`@Default` / `@MainImmediate` / `@IO` — the
     * three with consumers; `@Main` is provided for completeness but has no reader, hence no accessor/shim).
     */
    @DefaultDispatcher
    val defaultDispatcher: CoroutineDispatcher

    @MainImmediateDispatcher
    val mainImmediateDispatcher: CoroutineDispatcher

    @IODispatcher
    val ioDispatcher: CoroutineDispatcher

    /**
     * App-Scope Collapse Step 3 (PF commit 2A). Metro-owned [ResourceWrapper] — CONTRIBUTED by
     * `ResourceWrapperBindingContainer` (`@BindingContainer @ContributesTo(AppScope)`, its `Context` from
     * the `create(applicationContext)` bound instance). The ten feature `*HiltEntryPoint.resourceWrapper()`
     * bridges resolve it via the single adopt-back `@Provides`.
     */
    val resourceWrapper: ResourceWrapper

    /**
     * App-Scope Collapse Step 3 (SB1). Metro-owned [ActivityHolder] + [ActivityHolderProducer] — the same
     * `ActivityHolderImpl` (one `@SingleIn(AppScope)` retained instance) contributes BOTH via repeatable
     * `@ContributesBinding`. `ActivityHolder` is read by the still-Hilt `ResourceManagerImpl` (L1) and
     * `ActivityHolderProducer` by `MainActivity`, both via the adopt-back `@Provides`.
     */
    val activityHolder: ActivityHolder
    val activityHolderProducer: ActivityHolderProducer

    /** App-Scope Collapse Step 3 (core-android platform slice). Metro-owned via @ContributesBinding. */
    val platformInfoProvider: PlatformInfoProvider
    val tempFileProvider: TempFileProvider
    val appReinitializer: AppReinitializer

    /** App-Scope Collapse Step 3 (scheduling slice). Metro-owned RestoreStateRepository. */
    val restoreStateRepository: RestoreStateRepository

    /**
     * App-Scope Collapse Step 3 (worker slice). Metro-owned AutoBackupController (BackupScheduler) +
     * BackupNotificationHelper.
     */
    val autoBackupController: AutoBackupController
    val backupNotificationHelper: BackupNotificationHelper

    /**
     * App-Scope Collapse Step 3 (SB1, backup/scheduling slice). Metro-owned [BackupPreferencesRepository]
     * — CONTRIBUTED by `@ContributesBinding(AppScope)` on the (now public) `BackupPreferencesRepositoryImpl`
     * in its own module; `@DependencyGraph` auto-aggregates it. Its `Context` ctor dep resolves from the
     * `create(applicationContext)` bound instance. This accessor exposes the binding for the adopt-back
     * `@Provides` + identity tests; `SettingsHiltEntryPoint` + `BackupWorkerHiltEntryPoint` delegate here.
     */
    val backupPreferencesRepository: BackupPreferencesRepository

    /**
     * App-Scope Collapse Step 3 (app-dialogs slice). The three app-scoped singletons of
     * feature/app-dialogs:impl, now Metro-owned:
     *  - [appDialogRepository] — the self-bound concrete `AppDialogRepository` (`@SingleIn(AppScope)` +
     *    `@Inject`; implements `AppDialogPublisher` but is NOT bound to it). Read only intra-module,
     *    including by the feature's own `AppDialogGraph` via `AppDialogsHiltEntryPoint.appDialogRepository()`
     *    — that read is an adopt-back shim. Its `Context` resolves from the `create(applicationContext)`
     *    bound instance.
     *  - [appDialogObserverImpl] — the concrete `AppDialogObserverImpl` (`@SingleIn(AppScope)`), read by
     *    `AppDialogFeature` via `AppDialogsHiltEntryPoint.appDialogObserverImpl()`. The SAME instance is
     *    contributed as [appDialogObserver] (`@ContributesBinding`) for the cross-module consumers.
     *  - [appDialogObserver] / [appDialogPublisher] — the api interfaces, contributed via
     *    `@ContributesBinding(AppScope)` on the impls, read cross-module (settings / recovery / archive /
     *    `BaseApplication`) through the two api adopt-back shims.
     */
    val appDialogRepository: AppDialogRepository
    val appDialogObserverImpl: AppDialogObserverImpl
    val appDialogObserver: AppDialogObserver
    val appDialogPublisher: AppDialogPublisher

    /**
     * App-Scope Collapse Step 3 (google-drive slice). Metro-owned [AccountDataStore] — CONTRIBUTED by
     * `@ContributesBinding(AppScope)` on the (now public) `AccountDataStoreImpl`. Its `Context` resolves
     * from the `create(applicationContext)` bound instance. The one clean Context-only gd binding (no
     * cross-module reader); its four gd consumers stay Hilt this pass and resolve via the adopt-back shim.
     */
    val accountDataStore: AccountDataStore

    /**
     * Metro CONSTRUCTS and retains the leaf. `@SingleIn(AppScope)` binds it to this graph's
     * lifetime — i.e. the process — the exact lifetime Hilt's `@Singleton` gave. This is the first
     * app-scoped binding Metro *owns* (features only ever ADOPTED Hilt-owned singletons in).
     */
    @Provides
    @SingleIn(AppScope::class)
    fun provideAnalyticsHolder(): AnalyticsHolder = AnalyticsHolder()

    @DependencyGraph.Factory
    fun interface Factory {
        fun create(
            // PLAIN Context bound instance (locked C shape). Unused by the leaf, but fixes the graph
            // shape so the bulk migration adds AppDatabase/etc. as siblings without reshaping create().
            @Provides applicationContext: Context,
        ): AppGraph
    }
}
