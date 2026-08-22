// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.di

import android.content.Context
import dev.zacsweers.metro.DependencyGraph
import dev.zacsweers.metro.Provides
import dev.zacsweers.metro.SingleIn
import io.github.stslex.workeeper.app.common.di.AppRootDeps
import io.github.stslex.workeeper.core.core.coroutine.scope.AppScopeLifetime
import io.github.stslex.workeeper.core.core.di.AppScope
import io.github.stslex.workeeper.core.core.di.DefaultDispatcher
import io.github.stslex.workeeper.core.core.di.DispatchersBindingContainer
import io.github.stslex.workeeper.core.core.di.IODispatcher
import io.github.stslex.workeeper.core.core.di.MainImmediateDispatcher
import io.github.stslex.workeeper.core.core.images.ImageStorage
import io.github.stslex.workeeper.core.data.backup.api.BackupAuth
import io.github.stslex.workeeper.core.data.backup.api.BackupStorage
import io.github.stslex.workeeper.core.data.backup.api.RecoveryDiagnosticsExporter
import io.github.stslex.workeeper.core.data.backup.api.SnapshotExportRunner
import io.github.stslex.workeeper.core.data.backup.api.notification.BackupNotificationHelper
import io.github.stslex.workeeper.core.data.backup.api.restore.RestoreStateRepository
import io.github.stslex.workeeper.core.data.backup.api.scheduling.AutoBackupController
import io.github.stslex.workeeper.core.data.backup.api.scheduling.BackupPreferencesRepository
import io.github.stslex.workeeper.core.data.backup.worker.BackupWorkerDeps
import io.github.stslex.workeeper.core.data.dataStore.store.CommonDataStore
import io.github.stslex.workeeper.core.data.database.AppDatabase
import io.github.stslex.workeeper.core.data.database.snapshot.DatabaseSnapshotProvider
import io.github.stslex.workeeper.core.data.exercise.session.SessionConflictResolver
import io.github.stslex.workeeper.core.data.exercise.session.SessionRepository
import io.github.stslex.workeeper.core.ui.kit.utils.activityHolder.ActivityHolderProducer
import io.github.stslex.workeeper.core.ui.mvi.di.StoreDispatchers
import io.github.stslex.workeeper.core.ui.mvi.holders.AnalyticsHolder
import io.github.stslex.workeeper.core.ui.mvi.holders.LoggerHolder
import io.github.stslex.workeeper.core.ui.navigation.Navigator
import io.github.stslex.workeeper.feature.app_dialogs.api.observer.AppDialogObserver
import io.github.stslex.workeeper.feature.app_dialogs.impl.data.AppDialogRepository
import io.github.stslex.workeeper.feature.app_dialogs.impl.observer.AppDialogObserverImpl
import io.github.stslex.workeeper.feature.recovery.boot.RecoveryBootstrap
import io.github.stslex.workeeper.feature.recovery.di.RecoveryDeps
import io.github.stslex.workeeper.feature.recovery.domain.RestoreRecoveryCoordinator
import io.github.stslex.workeeper.feature.recovery.domain.StartupMigrationCoordinator
import io.github.stslex.workeeper.navigation.NavigatorEventBus
import kotlinx.coroutines.CoroutineDispatcher

/**
 * The Metro app-scope dependency graph. Held by `BaseApplication` for the whole
 * process. Factory-shaped ([Factory]): the app `Context` enters as a `@Provides` bound instance
 * via `create(...)`, so the graph interface stays small.
 */
/*
 * SUPERTYPE + ACCESSOR STATE — the measured end state of the graph-extension arc, not a plan.
 *
 *
 * The TWO supertypes below are not feature-graph bridges and stay: RecoveryDeps (read by
 * RecoveryActivity) and BackupWorkerDeps (read by MetroWorkerFactory), each acquired through its own
 * typed holder on BaseApplication because those two readers must not depend on core:ui:mvi.
 *
 * Accessor policy, applied here rather than deferred: an accessor stays only when something reads it —
 * BaseApplication / MainActivity / App.kt, one of the two dep interfaces, or a `:app` identity test.
 * Reader-less accessors were deleted; the four kept without a reader say so in their own KDoc and are
 * there as the compile-time assertion that the binding resolves in AppScope. An accessor is NOT what
 * makes a binding reachable — the extensions inherit every AppScope binding whether or not an accessor
 * names it.
 */
@DependencyGraph(scope = AppScope::class)
internal interface AppGraph :
    RecoveryDeps,
    BackupWorkerDeps,
    AppRootDeps {

    /**
     * Root accessor: the single app-scoped [AnalyticsHolder]. Read by the `:app` extension identity
     * tests, which assert each feature extension resolves the parent's instance.
     */
    val analyticsHolder: AnalyticsHolder

    /**
     * Metro-owned [LoggerHolder] — a concrete self-bound class (no interface), so it carries
     * `@SingleIn(AppScope)` + `@Inject` (NOT `@ContributesBinding`, which binds to a supertype); THIS
     * accessor pulls it into the graph as a retained singleton. Read by the identity tests.
     */
    val loggerHolder: LoggerHolder

    /**
     * Metro-owned [StoreDispatchers]. Its two qualified `CoroutineDispatcher` ctor deps resolve from
     * the graph's own [DispatchersBindingContainer]. `includeJavax` carries the qualifiers.
     *
     * NO READER: every Store takes `StoreDispatchers` as a constructor dep inside its own extension.
     * Kept as the compile-time assertion that the qualified pair still resolves from this graph.
     */
    val storeDispatchers: StoreDispatchers

    /**
     * [DispatchersBindingContainer] (`@BindingContainer @ContributesTo(AppScope)`) contributes FOUR
     * qualified `CoroutineDispatcher`s; the three exposed here are the ones the `:app` identity tests
     * read — `@MainDispatcher` has no accessor. They are stateless kotlinx process-singletons, and Metro
     * consumers (`StoreDispatchers`, the feature extensions) resolve them straight from the graph.
     *
     * Live root: the exercise-chart / exercise / live-workout identity tests read [defaultDispatcher] to
     * assert the extension inherits the parent's `@DefaultDispatcher` key.
     */
    @DefaultDispatcher
    val defaultDispatcher: CoroutineDispatcher

    // Live root: `SingleTrainingExtensionIdentityTest` and `ExerciseExtensionIdentityTest` both read it
    // to assert the qualified pair stays distinct across the extension boundary.
    @MainImmediateDispatcher
    val mainImmediateDispatcher: CoroutineDispatcher

    // Live root: `PastSessionExtensionIdentityTest` (and the exercise-chart / live-workout / settings
    // identity tests) read it to assert the extension inherits THIS key and not `@DefaultDispatcher`.
    @IODispatcher
    val ioDispatcher: CoroutineDispatcher

    /**
     * Metro-owned Navigator subsystem — the one `NavigatorEventBus` (`@SingleIn(AppScope)`) contributes
     * [Navigator] via `@ContributesBinding` AND is exposed here as its concrete type for `AppRootViewModel`
     * (which injects the concrete, then passes it as a `NavigatorReceiver`). One instance backs both.
     *
     * [navigatorEventBus] is declared by [AppRootDeps] and read by `App.kt` in `app:common` — which
     * cannot see this graph, so it reads the contract instead. [NavigatorEventBus] itself lives in
     * `app:common` under the `io.github.stslex.workeeper.navigation` package, which is why the
     * import above names no module. [navigator] has NO READER — features
     * inject `Navigator` as a constructor dep inside their own extension — and is kept as the
     * compile-time assertion that the contributed supertype binding still resolves to that same
     * instance.
     */
    val navigator: Navigator
    override val navigatorEventBus: NavigatorEventBus

    /**
     * Metro-owned [ActivityHolderProducer] — `ActivityHolderImpl` (one `@SingleIn(AppScope)` retained
     * instance) contributes it via `@ContributesBinding`. Read by `MainActivity`, which registers the
     * current Activity on it.
     */
    val activityHolderProducer: ActivityHolderProducer

    /** Metro-owned AutoBackupController (BackupScheduler) + BackupNotificationHelper. */
    override val autoBackupController: AutoBackupController
    override val backupNotificationHelper: BackupNotificationHelper

    /**
     * Metro-owned [BackupPreferencesRepository] — CONTRIBUTED by `@ContributesBinding(AppScope)` on the
     * (public) `BackupPreferencesRepositoryImpl` in its own module; `@DependencyGraph` auto-aggregates it.
     * Its `Context` ctor dep resolves from the `create(applicationContext)` bound instance. Read
     * cross-module by settings + the backup worker via the graph.
     */
    override val backupPreferencesRepository: BackupPreferencesRepository

    /**
     * Metro-owned [CommonDataStore] — CONTRIBUTED by `@ContributesBinding(AppScope)` on the (public)
     * `CommonDataStoreImpl` in `core:data:dataStore`; `@DependencyGraph` auto-aggregates it. Its dep is a
     * Metro-native `@AssistedFactory` (`DataStoreProviderFactory`, whose produced `DataStoreProvider` takes
     * a plain `Context` from the `create(applicationContext)` bound instance). Read by `App.kt`, which
     * hands it to `AppRootViewModel`; settings takes it as a constructor dep inside its own extension.
     */
    override val commonDataStore: CommonDataStore

    /**
     * Three of the app-scoped singletons of feature/app-dialogs:impl, Metro-owned:
     *  - [appDialogRepository] — the self-bound concrete `AppDialogRepository` (`@SingleIn(AppScope)` +
     *    `@Inject`; implements `AppDialogPublisher` but is NOT bound to it). Its `Context` resolves from
     *    the `create(applicationContext)` bound instance. Read by `AppDialogExtensionIdentityTest`,
     *    which asserts the extension inherits this instance instead of building a double.
     *  - [appDialogObserverImpl] — the concrete `AppDialogObserverImpl` (`@SingleIn(AppScope)`), read by
     *    the same identity test. The SAME instance is contributed as [appDialogObserver]
     *    (`@ContributesBinding`) for the cross-module consumers.
     *  - [appDialogObserver] — the api interface. NO READER: `RestoreDialogChoiceObserver` takes it as a
     *    constructor dep. Kept as the compile-time assertion that the contributed supertype binding
     *    resolves to the same singleton.
     *
     * There is no `appDialogPublisher` accessor: its only reader was `BaseApplication`'s override of the
     * `AppDialogPublisherHolder` seam, and that seam is deleted — settings and recovery take
     * `AppDialogPublisher` as a constructor dep resolved inside their own extension.
     */
    val appDialogRepository: AppDialogRepository
    val appDialogObserverImpl: AppDialogObserverImpl
    val appDialogObserver: AppDialogObserver

    /**
     * The google-drive auth-chain bindings, Metro-owned via `@ContributesBinding(AppScope)` on their gd
     * impls (the GMS `AuthorizationClient` + ktor `HttpClient` are held by the gd `@BindingContainer`s but
     * stay INSIDE gd — no accessor here, so app/app never names them).
     *
     * [backupStorage] is declared by [BackupWorkerDeps] and read by `MetroWorkerFactory`. [backupAuth]
     * has NO READER — `BackupInteractorImpl` (settings) and `SnapshotExportRunnerImpl` take it as a
     * constructor dep — and is kept as the compile-time assertion that the gd auth chain still resolves.
     */
    val backupAuth: BackupAuth
    override val backupStorage: BackupStorage

    /**
     * Metro-owned [SnapshotExportRunner] (`@ContributesBinding(AppScope)` on `SnapshotExportRunnerImpl`,
     * all deps graph-resolvable). Read by the `BackupWorker` + settings via the graph.
     */
    override val snapshotExportRunner: SnapshotExportRunner

    /**
     * Metro-owned [RecoveryDiagnosticsExporter] — `@ContributesBinding(AppScope)` on
     * `RecoveryDiagnosticsExporterImpl` (feature/recovery), bound to the api interface. Declared by
     * [RecoveryDeps] and read by `RecoveryActivity` through its typed holder; `RestoreDialogChoiceObserver`
     * takes the same binding as a constructor dep.
     */
    override val recoveryDiagnosticsExporter: RecoveryDiagnosticsExporter

    /**
     * The exercise repositories are Metro-owned via `@ContributesBinding(AppScope)` on their (public)
     * impls (their Room-DAO / DbTransitionRunner / ImageStorage ctor deps resolve graph-internally; `@IO`
     * from the graph), and every feature takes the ones it needs as constructor deps inside its own
     * extension. Only [sessionRepository] keeps an accessor, because `LiveWorkoutExtensionIdentityTest`
     * reads it to assert the extension inherits this instance rather than building a double.
     */
    val sessionRepository: SessionRepository

    /**
     * [SessionConflictResolver] — self-bound `@SingleIn(AppScope)` graph node.
     *
     * Live root: `SingleTrainingExtensionIdentityTest` reads it to assert the extension inherits the
     * parent's instance rather than building its own double.
     */
    val sessionConflictResolver: SessionConflictResolver

    /**
     * [ImageStorage] accessor over the `create()` bound-instance root — read by
     * `BaseApplication.cleanupOrphanedImageTempFiles`. The exercise extension INHERITS the same binding
     * rather than being handed it.
     */
    val imageStorage: ImageStorage

    /**
     * The generation lifetime this graph was built with — the `create()` bound-instance root every
     * scope-owning app-scope singleton derives its `childScope` from (Phase 5, spec §8.2). Read by
     * the runtime/identity tests to assert the root is threaded, and by `BaseApplication`'s startup
     * chores until the runtime host owns them.
     */
    val appScopeLifetime: AppScopeLifetime

    /**
     * The recovery cluster — feature/recovery `@SingleIn(AppScope)` graph nodes read by
     * `BaseApplication`/`MainActivity` (both in app/app, so read the INTERNAL graph directly via
     * `AppGraphOwner`, not through a dep interface). [recoveryBootstrap] is the
     * `RestoreDialogChoiceObserver` (via its `RecoveryBootstrap` supertype, `@ContributesBinding`) —
     * resolving it eagerly arms the observer's `init{}` subscriber (app-dialogs BLOCKER 1).
     */
    val restoreRecoveryCoordinator: RestoreRecoveryCoordinator
    val startupMigrationCoordinator: StartupMigrationCoordinator
    val recoveryBootstrap: RecoveryBootstrap

    /**
     * Metro-owned [RestoreStateRepository] — CONTRIBUTED by `@ContributesBinding(AppScope)` on the
     * (public) `RestoreStateRepositoryImpl` in `core:data:backup:scheduling`. Production consumers
     * take it as a constructor dep inside the recovery cluster above, so this accessor exists for
     * one reason: `AppScopeDataStoreSingletonTest` must READ `restore_state_prefs` through the real
     * app-scope binding from two graphs, and a read routed through a coordinator would assert the
     * coordinator's behaviour instead of the store's identity.
     */
    val restoreStateRepository: RestoreStateRepository

    /**
     * The `AppDatabase`-derived DB-snapshot binding, Metro-owned via `@ContributesBinding(AppScope)` on
     * `DatabaseSnapshotProviderImpl` (derives from the `appDatabase` `create()` root). Read cross-module by
     * the restore path (BackupWorker, RecoveryActivity, the recovery observers, settings) via the graph.
     * (The sibling `LiveDatabaseLocator` binding — the SAME instance — is consumed by `StartupMigration
     * Coordinator` via ctor `@Inject`, not through a graph accessor, so it has no accessor here.)
     */
    override val databaseSnapshotProvider: DatabaseSnapshotProvider

    /**
     * Metro CONSTRUCTS and retains this. `@SingleIn(AppScope)` binds it to this graph's
     * lifetime — i.e. the process.
     */
    @Provides
    @SingleIn(AppScope::class)
    fun provideAnalyticsHolder(): AnalyticsHolder = AnalyticsHolder()

    @DependencyGraph.Factory
    fun interface Factory {
        // create() has 4 ROOTS. Each is a test-override boundary the seam swaps directly; everything
        // else derives.
        //  - applicationContext: the app Context (plain).
        //  - appDatabase: the generation's Room instance (caller-constructed root, threaded in). The 9
        //    DAOs + DbTransitionRunner DERIVE from it graph-internally (DbCascadeBindingContainer), and
        //    the 3 AppDatabase-derived interface bindings are @ContributesBinding on their impls. The
        //    seam swaps an in-memory AppDatabase here; prod passes the file-backed one.
        //  - imageStorage: permanent create() root; tests pass a FakeImageStorage here.
        //  - appScopeLifetime: the generation lifetime (Phase 5, spec §8.2) — decided by the owner that
        //    also decides this graph's lifetime, so it CANNOT be a graph-internal binding. The three
        //    scope-owning singletons (RestoreDialogChoiceObserver, DriveBackupAuth,
        //    SnapshotExportRunnerImpl) derive their scopes from it.
        fun create(
            @Provides applicationContext: Context,
            @Provides appDatabase: AppDatabase,
            @Provides imageStorage: ImageStorage,
            @Provides appScopeLifetime: AppScopeLifetime,
        ): AppGraph
    }
}
