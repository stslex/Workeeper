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
import io.github.stslex.workeeper.core.core.images.ImageStorage
import io.github.stslex.workeeper.core.core.platform.PlatformInfoProvider
import io.github.stslex.workeeper.core.core.platform.TempFileProvider
import io.github.stslex.workeeper.core.core.resources.ResourceWrapper
import io.github.stslex.workeeper.core.data.backup.api.BackupAuth
import io.github.stslex.workeeper.core.data.backup.api.BackupStorage
import io.github.stslex.workeeper.core.data.backup.api.RecoveryDiagnosticsExporter
import io.github.stslex.workeeper.core.data.backup.api.SnapshotExportRunner
import io.github.stslex.workeeper.core.data.backup.api.notification.BackupNotificationHelper
import io.github.stslex.workeeper.core.data.backup.api.restore.RestoreStateRepository
import io.github.stslex.workeeper.core.data.backup.api.scheduling.AutoBackupController
import io.github.stslex.workeeper.core.data.backup.api.scheduling.BackupPreferencesRepository
import io.github.stslex.workeeper.core.data.backup.google_drive.auth.AccountDataStore
import io.github.stslex.workeeper.core.data.backup.worker.BackupWorkerDeps
import io.github.stslex.workeeper.core.data.dataStore.store.CommonDataStore
import io.github.stslex.workeeper.core.data.database.AppDatabase
import io.github.stslex.workeeper.core.data.database.snapshot.DatabaseSnapshotProvider
import io.github.stslex.workeeper.core.data.exercise.exercise.ExerciseRepository
import io.github.stslex.workeeper.core.data.exercise.personal_record.PersonalRecordRepository
import io.github.stslex.workeeper.core.data.exercise.session.PerformedExerciseRepository
import io.github.stslex.workeeper.core.data.exercise.session.SessionConflictResolver
import io.github.stslex.workeeper.core.data.exercise.session.SessionRepository
import io.github.stslex.workeeper.core.data.exercise.session.SetRepository
import io.github.stslex.workeeper.core.data.exercise.stats.StatsRepository
import io.github.stslex.workeeper.core.data.exercise.tags.TagRepository
import io.github.stslex.workeeper.core.data.exercise.training.TrainingExerciseRepository
import io.github.stslex.workeeper.core.data.exercise.training.TrainingRepository
import io.github.stslex.workeeper.core.ui.kit.utils.NumUiUtils
import io.github.stslex.workeeper.core.ui.kit.utils.activityHolder.ActivityHolder
import io.github.stslex.workeeper.core.ui.kit.utils.activityHolder.ActivityHolderProducer
import io.github.stslex.workeeper.core.ui.mvi.di.StoreCoreDeps
import io.github.stslex.workeeper.core.ui.mvi.di.StoreDispatchers
import io.github.stslex.workeeper.core.ui.mvi.holders.AnalyticsHolder
import io.github.stslex.workeeper.core.ui.mvi.holders.LoggerHolder
import io.github.stslex.workeeper.core.ui.navigation.Navigator
import io.github.stslex.workeeper.core.ui.navigation.NavigatorDeps
import io.github.stslex.workeeper.feature.app_dialogs.api.observer.AppDialogObserver
import io.github.stslex.workeeper.feature.app_dialogs.api.publisher.AppDialogPublisher
import io.github.stslex.workeeper.feature.app_dialogs.impl.data.AppDialogRepository
import io.github.stslex.workeeper.feature.app_dialogs.impl.observer.AppDialogObserverImpl
import io.github.stslex.workeeper.feature.exercise.di.ExerciseDeps
import io.github.stslex.workeeper.feature.live_workout.di.LiveWorkoutDeps
import io.github.stslex.workeeper.feature.recovery.boot.RecoveryBootstrap
import io.github.stslex.workeeper.feature.recovery.di.RecoveryDeps
import io.github.stslex.workeeper.feature.recovery.domain.RestoreRecoveryCoordinator
import io.github.stslex.workeeper.feature.recovery.domain.StartupMigrationCoordinator
import io.github.stslex.workeeper.feature.single_training.di.SingleTrainingDeps
import io.github.stslex.workeeper.navigation.NavigatorEventBus
import kotlinx.coroutines.CoroutineDispatcher

/**
 * The Metro app-scope dependency graph. Held by `BaseApplication` for the whole
 * process. Factory-shaped ([Factory]): the app `Context` enters as a `@Provides` bound instance
 * via `create(...)`, so the graph interface stays small.
 */
@DependencyGraph(scope = AppScope::class)
internal interface AppGraph :
    StoreCoreDeps,
    NavigatorDeps,
    ExerciseDeps,
    SingleTrainingDeps,
    LiveWorkoutDeps,
    RecoveryDeps,
    BackupWorkerDeps {

    /** Root accessor: the single app-scoped [AnalyticsHolder]. */
    override val analyticsHolder: AnalyticsHolder

    /**
     * Metro-owned [NumUiUtils] — CONTRIBUTED by `@ContributesBinding(AppScope::class)` on
     * `NumUiUtilsImpl` in its own module (`core:ui:kit`), which `@DependencyGraph(AppScope::class)`
     * auto-aggregates. No `@Provides` here: the impl is `internal` to `core:ui:kit` and app/app cannot
     * reference it, so ownership lives at the impl via contribution (the visibility-respecting Metro
     * mechanic). This accessor exposes the binding for identity tests.
     */
    val numUiUtils: NumUiUtils

    /**
     * Metro-owned [LoggerHolder] — a concrete self-bound class (no interface), so it carries
     * `@SingleIn(AppScope)` + `@Inject` (NOT `@ContributesBinding`, which binds to a supertype); THIS
     * accessor pulls it into the graph as a retained singleton.
     */
    override val loggerHolder: LoggerHolder

    /**
     * Metro-owned [StoreDispatchers]. Its two qualified `CoroutineDispatcher` ctor deps resolve from
     * the graph's own [DispatchersBindingContainer]. `includeJavax` carries the qualifiers.
     */
    override val storeDispatchers: StoreDispatchers

    /**
     * The four Metro-owned CoroutineDispatchers, CONTRIBUTED by [DispatchersBindingContainer]
     * (`@BindingContainer @ContributesTo(AppScope)`) — the binding that Metro consumers
     * (`StoreDispatchers`, the feature graphs) resolve. The dispatchers are stateless kotlinx
     * process-singletons.
     */
    @DefaultDispatcher
    override val defaultDispatcher: CoroutineDispatcher

    @MainImmediateDispatcher
    override val mainImmediateDispatcher: CoroutineDispatcher

    // ORPHANED by the past-session port: `PastSessionDeps` was the last bridge interface still
    // declaring `@IODispatcher`, so this accessor now overrides nothing. It is kept (as a plain `val`,
    // no `override`) rather than deleted — accessor cleanup is deferred to the final feature and lands
    // in bulk, per the orphaned-accessor ledger in the arc HANDOFF. Still read by
    // `PastSessionExtensionIdentityTest`, which asserts the extension inherits THIS key and not
    // `@DefaultDispatcher`.
    @IODispatcher
    val ioDispatcher: CoroutineDispatcher

    /**
     * Metro-owned [ResourceWrapper] — CONTRIBUTED by `ResourceWrapperBindingContainer`
     * (`@BindingContainer @ContributesTo(AppScope)`, its `Context` from the `create(applicationContext)`
     * bound instance).
     */
    override val resourceWrapper: ResourceWrapper

    /**
     * Metro-owned Navigator subsystem — the one `NavigatorEventBus` (`@SingleIn(AppScope)`) contributes
     * [Navigator] via `@ContributesBinding` AND is exposed here as its concrete type for `AppRootViewModel`
     * (which injects the concrete, then passes it as a `NavigatorReceiver`). One instance backs both.
     */
    override val navigator: Navigator
    val navigatorEventBus: NavigatorEventBus

    /**
     * Metro-owned [ActivityHolder] + [ActivityHolderProducer] — the same `ActivityHolderImpl` (one
     * `@SingleIn(AppScope)` retained instance) contributes BOTH via repeatable `@ContributesBinding`.
     * The `ActivityHolder` supertype now has no production reader (its former sole consumer
     * `ResourceManagerImpl` was a dead binding, last `.locale` reader removed in e37f74f5, and was
     * DELETED in the L-tail slice). This accessor stays: the seam test uses it to prove both supertypes
     * resolve to the one `ActivityHolderImpl` instance. `ActivityHolderProducer` is read by `MainActivity`.
     */
    val activityHolder: ActivityHolder
    val activityHolderProducer: ActivityHolderProducer

    /** Metro-owned via @ContributesBinding. */
    val platformInfoProvider: PlatformInfoProvider
    val tempFileProvider: TempFileProvider

    /** Metro-owned RestoreStateRepository. */
    val restoreStateRepository: RestoreStateRepository

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
     * a plain `Context` from the `create(applicationContext)` bound instance). Read by `AppRootViewModel`
     * and settings (`SettingsGraph`) via the graph.
     */
    val commonDataStore: CommonDataStore

    /**
     * The three app-scoped singletons of feature/app-dialogs:impl, Metro-owned:
     *  - [appDialogRepository] — the self-bound concrete `AppDialogRepository` (`@SingleIn(AppScope)` +
     *    `@Inject`; implements `AppDialogPublisher` but is NOT bound to it). Read only intra-module,
     *    including by the feature's own `AppDialogGraph`. Its `Context` resolves from the
     *    `create(applicationContext)` bound instance.
     *  - [appDialogObserverImpl] — the concrete `AppDialogObserverImpl` (`@SingleIn(AppScope)`), read by
     *    `AppDialogFeature`. The SAME instance is contributed as [appDialogObserver]
     *    (`@ContributesBinding`) for the cross-module consumers.
     *  - [appDialogObserver] / [appDialogPublisher] — the api interfaces, contributed via
     *    `@ContributesBinding(AppScope)` on the impls, read cross-module (settings / recovery / archive /
     *    `BaseApplication`).
     */
    val appDialogRepository: AppDialogRepository
    val appDialogObserverImpl: AppDialogObserverImpl
    val appDialogObserver: AppDialogObserver
    val appDialogPublisher: AppDialogPublisher

    /**
     * Metro-owned [AccountDataStore] — CONTRIBUTED by `@ContributesBinding(AppScope)` on the (public)
     * `AccountDataStoreImpl`. Its `Context` resolves from the `create(applicationContext)` bound instance.
     * The one Context-only gd binding; read by its four gd consumers via the graph.
     */
    val accountDataStore: AccountDataStore

    /**
     * The google-drive auth-chain bindings, Metro-owned via `@ContributesBinding(AppScope)` on their gd
     * impls (the GMS `AuthorizationClient` + ktor `HttpClient` are held by the gd `@BindingContainer`s but
     * stay INSIDE gd — no accessor here, so app/app never names them). Read cross-module by settings + the
     * backup worker via the graph.
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
     * `RecoveryDiagnosticsExporterImpl` (feature/recovery), bound to the api interface. Exposed via
     * `RecoveryDeps`: read by `RecoveryActivity` + `RestoreDialogChoiceObserver` through the graph.
     */
    override val recoveryDiagnosticsExporter: RecoveryDiagnosticsExporter

    /**
     * The nine exercise repositories, Metro-owned via `@ContributesBinding(AppScope)` on their (public)
     * impls (their Room-DAO / DbTransitionRunner / ImageStorage ctor deps resolve graph-internally; `@IO`
     * from the graph). Eight are read cross-module by features via the graph; [statsRepository] has zero
     * consumers (dead binding) — exposed for completeness/identity.
     */
    override val exerciseRepository: ExerciseRepository
    override val sessionRepository: SessionRepository
    override val setRepository: SetRepository
    override val tagRepository: TagRepository
    override val personalRecordRepository: PersonalRecordRepository
    override val performedExerciseRepository: PerformedExerciseRepository
    override val trainingExerciseRepository: TrainingExerciseRepository
    override val trainingRepository: TrainingRepository
    val statsRepository: StatsRepository

    /**
     * [SessionConflictResolver] — self-bound `@SingleIn(AppScope)` graph node; read by home +
     * single-training via their `XDeps` (`HomeDeps` / `SingleTrainingDeps`).
     */
    override val sessionConflictResolver: SessionConflictResolver

    /**
     * [ImageStorage] accessor over the `create()` bound-instance root — read by the exercise feature
     * (+ `BaseApplication.cleanupOrphanedImageTempFiles`) via the graph.
     */
    override val imageStorage: ImageStorage

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
        // create() has 3 ROOTS. Each is a test-override boundary the seam swaps directly; everything
        // else derives.
        //  - applicationContext: the app Context (plain).
        //  - appDatabase: the single Room instance (caller-constructed root, threaded in). The 9 DAOs +
        //    DbTransitionRunner DERIVE from it graph-internally (DbCascadeBindingContainer), and the 3
        //    AppDatabase-derived interface bindings are @ContributesBinding on their impls. The seam swaps
        //    an in-memory AppDatabase here; prod passes the file-backed one.
        //  - imageStorage: permanent create() root; tests pass a FakeImageStorage here.
        fun create(
            @Provides applicationContext: Context,
            @Provides appDatabase: AppDatabase,
            @Provides imageStorage: ImageStorage,
        ): AppGraph
    }
}
