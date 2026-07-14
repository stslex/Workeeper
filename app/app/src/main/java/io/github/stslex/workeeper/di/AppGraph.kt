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
import io.github.stslex.workeeper.core.core.platform.AppReinitializer
import io.github.stslex.workeeper.core.core.platform.PlatformInfoProvider
import io.github.stslex.workeeper.core.core.platform.TempFileProvider
import io.github.stslex.workeeper.core.core.resources.ResourceWrapper
import io.github.stslex.workeeper.core.data.backup.api.BackupAuth
import io.github.stslex.workeeper.core.data.backup.api.BackupStorage
import io.github.stslex.workeeper.core.data.backup.api.SnapshotExportRunner
import io.github.stslex.workeeper.core.data.backup.api.restore.RestoreStateRepository
import io.github.stslex.workeeper.core.data.backup.api.scheduling.AutoBackupController
import io.github.stslex.workeeper.core.data.backup.api.scheduling.BackupPreferencesRepository
import io.github.stslex.workeeper.core.data.backup.google_drive.auth.AccountDataStore
import io.github.stslex.workeeper.core.data.backup.worker.notification.BackupNotificationHelper
import io.github.stslex.workeeper.core.data.dataStore.store.CommonDataStore
import io.github.stslex.workeeper.core.data.database.AppDatabase
import io.github.stslex.workeeper.core.data.database.snapshot.DatabaseSnapshotProvider
import io.github.stslex.workeeper.core.data.database.snapshot.LiveDatabaseLocator
import io.github.stslex.workeeper.core.data.exercise.exercise.ExerciseRepository
import io.github.stslex.workeeper.core.data.exercise.personal_record.PersonalRecordRepository
import io.github.stslex.workeeper.core.data.exercise.session.PerformedExerciseRepository
import io.github.stslex.workeeper.core.data.exercise.session.SessionRepository
import io.github.stslex.workeeper.core.data.exercise.session.SetRepository
import io.github.stslex.workeeper.core.data.exercise.stats.StatsRepository
import io.github.stslex.workeeper.core.data.exercise.tags.TagRepository
import io.github.stslex.workeeper.core.data.exercise.training.TrainingExerciseRepository
import io.github.stslex.workeeper.core.data.exercise.training.TrainingRepository
import io.github.stslex.workeeper.core.ui.kit.utils.NumUiUtils
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
     * [DispatchersBindingContainer] (`@BindingContainer @ContributesTo(AppScope)`) — the Metro-side binding
     * that Metro consumers (`StoreDispatchers`, the feature graphs) resolve.
     *
     * These qualified accessors expose the graph's dispatchers for the seam's `===` identity proof. Since the
     * C2-h' back-edge correction, the Hilt-side shims provide `Dispatchers.*` DIRECTLY (not via these
     * accessors) — the dispatchers are stateless kotlinx process-singletons, so the direct Hilt value is the
     * IDENTICAL object these accessors return (the seam asserts `===`), and routing the Hilt shim through
     * `appGraph` would re-introduce the dissolved `@IO`→`appGraph` back-edge.
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
     * App-Scope Collapse Step 3 (PF commit 2C). Metro-owned Navigator subsystem — the one
     * `NavigatorEventBus` (`@SingleIn(AppScope)`) contributes [Navigator] via `@ContributesBinding`
     * (read by the 12 feature `*HiltEntryPoint.navigator()` bridges) AND is exposed here as its concrete
     * type for `AppRootViewModel` (which injects the concrete, then passes it as a `NavigatorReceiver`).
     * One instance backs both; both resolve via the two adopt-back `@Provides`.
     */
    val navigator: Navigator
    val navigatorEventBus: NavigatorEventBus

    /**
     * App-Scope Collapse Step 3 (SB1). Metro-owned [ActivityHolder] + [ActivityHolderProducer] — the same
     * `ActivityHolderImpl` (one `@SingleIn(AppScope)` retained instance) contributes BOTH via repeatable
     * `@ContributesBinding`. The `ActivityHolder` supertype now has no production reader — its former sole
     * consumer `ResourceManagerImpl` was a dead binding (last `.locale` reader removed in e37f74f5) and was
     * DELETED in the L-tail slice, so its adopt-back `@Provides` was removed too. This accessor stays: the
     * seam test uses it to prove both supertypes resolve to the one `ActivityHolderImpl` instance.
     * `ActivityHolderProducer` is still read by `MainActivity` via the adopt-back `@Provides`.
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
     * App-Scope Collapse Step 3 (CommonDataStore slice). Metro-owned [CommonDataStore] — CONTRIBUTED by
     * `@ContributesBinding(AppScope)` on the (now public) `CommonDataStoreImpl` in `core:data:dataStore`;
     * `@DependencyGraph` auto-aggregates it. Its dep is a Metro-native `@AssistedFactory`
     * (`DataStoreProviderFactory`, whose produced `DataStoreProvider` takes a plain `Context` from the
     * `create(applicationContext)` bound instance). This accessor exposes the binding for the adopt-back
     * `@Provides` + identity tests; the 3 still-Hilt readers (`AppRootViewModel`, `SettingsHiltEntryPoint`,
     * `SettingsGraph`) delegate here.
     */
    val commonDataStore: CommonDataStore

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
     * App-Scope Collapse Step 3 (PF.3, google-drive auth-chain). The GMS-clean gd bindings that have a
     * still-Hilt reader, Metro-owned via `@ContributesBinding(AppScope)` on their gd impls (the GMS
     * `AuthorizationClient` + ktor `HttpClient` are held by the gd `@BindingContainer`s but stay INSIDE gd —
     * no accessor here, so app/app never names them). Read cross-module by settings + worker (still Hilt),
     * via their adopt-back shims.
     *
     * (The `snapshotStorage` accessor + shim were RETIRED in Step 5 (5b): its sole still-Hilt reader was
     * `SnapshotExportRunnerImpl`, now Metro-owned and resolving `SnapshotStorage` directly.)
     */
    val backupAuth: BackupAuth
    val backupStorage: BackupStorage

    /**
     * App-Scope Collapse Step 5 (5b). Metro-owned [SnapshotExportRunner] (`@ContributesBinding(AppScope)` on
     * `SnapshotExportRunnerImpl`, all deps graph-resolvable). Exposed for the adopt-back shim + identity seam:
     * read by the still-Hilt `BackupWorker` (via `BackupWorkerHiltEntryPoint`) + settings.
     */
    val snapshotExportRunner: SnapshotExportRunner

    /**
     * App-Scope Collapse Step 3 (C2). The nine exercise repositories, now Metro-owned via
     * `@ContributesBinding(AppScope)` on their (public) impls (their Room-DAO / DbTransitionRunner /
     * ImageStorage ctor deps resolve from the C2 bridge params; `@IO` from the graph). Eight are read
     * cross-module by still-Hilt features (via `*HiltEntryPoint` bridges + pure-Hilt `@Inject`) and resolve
     * through their adopt-back shims; [statsRepository] has zero consumers (dead binding) — exposed for
     * completeness/identity, no shim.
     */
    val exerciseRepository: ExerciseRepository
    val sessionRepository: SessionRepository
    val setRepository: SetRepository
    val tagRepository: TagRepository
    val personalRecordRepository: PersonalRecordRepository
    val performedExerciseRepository: PerformedExerciseRepository
    val trainingExerciseRepository: TrainingExerciseRepository
    val trainingRepository: TrainingRepository
    val statsRepository: StatsRepository

    /**
     * App-Scope Collapse Step 5 (5a). The `AppDatabase`-derived DB-locator interface bindings, now Metro-owned
     * via repeatable `@ContributesBinding(AppScope)` on `DatabaseSnapshotProviderImpl` — [databaseSnapshotProvider]
     * / [liveDatabaseLocator] are the SAME instance (derives from the `appDatabase` `create()` root). Exposed for
     * the adopt-back shims + the `===` identity seam. Read cross-module by the restore path (BackupWorker,
     * RecoveryActivity, the recovery observers, settings) — all still Hilt, via their adopt-back shims.
     *
     * (`DatabaseJsonExporter` is Metro-owned too (5a) but its accessor + shim were RETIRED in 5b: its sole
     * still-Hilt reader was `SnapshotExportRunnerImpl`, now Metro-owned and resolving it directly.)
     */
    val databaseSnapshotProvider: DatabaseSnapshotProvider
    val liveDatabaseLocator: LiveDatabaseLocator

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
        // App-Scope Collapse Step 5 (5a): create() collapsed from 12 params to 3 ROOTS. Each is a
        // test-override boundary the seam swaps directly (§Test-override root); everything else derives.
        //  - applicationContext: the app Context (plain; @ApplicationContext stays Hilt-side).
        //  - appDatabase: the single Room instance (Hilt-constructed root, threaded in). The 9 DAOs +
        //    DbTransitionRunner DERIVE from it graph-internally (DbCascadeBindingContainer), and the 3
        //    AppDatabase-derived interface bindings are @ContributesBinding on their impls. The seam swaps
        //    an in-memory AppDatabase here; prod passes the file-backed Hilt one.
        //  - imageStorage: permanent create() root (5c Option A'; @TestInstallIn fake flows through).
        fun create(
            @Provides applicationContext: Context,
            @Provides appDatabase: AppDatabase,
            @Provides imageStorage: ImageStorage,
        ): AppGraph
    }
}
