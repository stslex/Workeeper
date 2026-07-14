// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.di

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import dagger.Module
import dagger.Provides
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.components.SingletonComponent
import dagger.hilt.testing.TestInstallIn
import dev.zacsweers.metro.createGraphFactory
import io.github.stslex.workeeper.core.core.platform.AppReinitializer
import io.github.stslex.workeeper.core.core.platform.PlatformInfoProvider
import io.github.stslex.workeeper.core.core.platform.TempFileProvider
import io.github.stslex.workeeper.core.data.backup.api.restore.RestoreStateRepository
import io.github.stslex.workeeper.core.data.backup.api.scheduling.AutoBackupController
import io.github.stslex.workeeper.core.data.backup.api.scheduling.BackupPreferencesRepository
import io.github.stslex.workeeper.core.data.dataStore.store.CommonDataStore
import io.github.stslex.workeeper.core.data.backup.google_drive.auth.AccountDataStore
import io.github.stslex.workeeper.core.data.backup.worker.notification.BackupNotificationHelper
import io.github.stslex.workeeper.core.ui.kit.utils.activityHolder.ActivityHolderProducer
import io.github.stslex.workeeper.core.ui.mvi.di.StoreDispatchers
import io.github.stslex.workeeper.core.ui.mvi.holders.AnalyticsHolder
import io.github.stslex.workeeper.core.ui.mvi.holders.LoggerHolder
import io.github.stslex.workeeper.feature.app_dialogs.api.observer.AppDialogObserver
import io.github.stslex.workeeper.feature.app_dialogs.api.publisher.AppDialogPublisher
import io.github.stslex.workeeper.feature.app_dialogs.impl.data.AppDialogRepository
import io.github.stslex.workeeper.feature.app_dialogs.impl.observer.AppDialogObserverImpl
import io.github.stslex.workeeper.core.core.di.DefaultDispatcher
import io.github.stslex.workeeper.core.core.di.IODispatcher
import io.github.stslex.workeeper.core.core.di.MainImmediateDispatcher
import io.github.stslex.workeeper.core.core.resources.ResourceWrapper
import io.github.stslex.workeeper.core.data.backup.api.BackupAuth
import io.github.stslex.workeeper.core.data.backup.api.BackupStorage
import io.github.stslex.workeeper.core.data.backup.api.SnapshotExportRunner
import io.github.stslex.workeeper.core.data.database.snapshot.DatabaseSnapshotProvider
import io.github.stslex.workeeper.core.data.database.snapshot.LiveDatabaseLocator
import io.github.stslex.workeeper.core.data.database_test.InMemoryDatabaseProvider
import io.github.stslex.workeeper.core.data.exercise.exercise.ExerciseRepository
import io.github.stslex.workeeper.core.data.exercise.session.SessionRepository
import io.github.stslex.workeeper.core.ui.test.fakes.FakeImageStorage
import io.github.stslex.workeeper.core.ui.navigation.Navigator
import io.github.stslex.workeeper.navigation.NavigatorEventBus
import io.github.stslex.workeeper.core.ui.test.annotations.Regression
import kotlinx.coroutines.CoroutineDispatcher
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertSame
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import javax.inject.Singleton

/**
 * KMP C.1 app-collapse Phase 1 (leaf E-proof) — the CROSS-SIDE instrumented half. This is the gate.
 *
 * Proves the ADOPT-BACK seam preserves singleton identity on-device with the REAL Hilt graph live.
 * `AnalyticsHolder` is Metro-OWNED (its `@Inject`/`@Singleton` were stripped); Hilt resolves it only
 * through `AppGraphAdoptBackModule.provideAnalyticsHolder`, which delegates to the Hilt-provided
 * [AppGraph] (`provideAppGraph`).
 *
 * The Hilt test harness swaps in `HiltTestApplication` (no `BaseApplication`), so [TestAppGraphModule]
 * `@TestInstallIn`-REPLACES `provideAppGraph` with a test-built graph — the legitimate test-infra
 * substitute for the prod `BaseApplication.appGraph`. Both asserted access paths then read THAT graph:
 *  - **Path M (Metro-direct):** `testAppGraph.analyticsHolder` — the owner.
 *  - **Path H (Hilt-via-adopt-back):** resolved from Hilt's `SingletonComponent` through
 *    [TestAnalyticsEntryPoint] → `provideAnalyticsHolder` → `provideAppGraph` (the replaced one) →
 *    `appGraph.analyticsHolder`. This is the IDENTICAL Hilt-side resolution the 13 production
 *    `*HiltEntryPoint.analyticsHolder()` accessors perform. Those are `internal` to their feature
 *    modules, so this test declares an equivalent one for the same binding.
 *
 * Negative-control note: the earlier `context as BaseApplication` form threw `ClassCastException`
 * here (HiltTestApplication ≠ BaseApplication) — proving this test genuinely exercises the app swap.
 * If M !== H, the adopt-back constructed a parallel instance (single-owner violation). M === H is E.
 */
@Regression
@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class AppGraphAdoptBackSeamTest {

    @get:Rule
    val hiltRule = HiltAndroidRule(this)

    @Before
    fun setup() {
        hiltRule.inject()
    }

    private fun hiltResolved(): AnalyticsHolder =
        EntryPointAccessors
            .fromApplication(
                ApplicationProvider.getApplicationContext(),
                TestAnalyticsEntryPoint::class.java,
            )
            .analyticsHolder()

    private fun metroDirect(): AnalyticsHolder = TestAppGraphModule.testAppGraph.analyticsHolder

    @Test
    fun metroDirectAndHiltAdoptBackResolveTheSameInstance() {
        val metro = metroDirect()
        val hilt = hiltResolved()

        assertNotNull(metro)
        assertNotNull(hilt)
        // THE GATE: the Hilt-side resolution (through the delegating @Provides) returns the SAME
        // object the Metro graph owns. Identity survives the app-tier adopt-back seam.
        assertSame(
            "Hilt adopt-back @Provides must return the Metro-owned AnalyticsHolder (===), not a copy",
            metro,
            hilt,
        )
    }

    @Test
    fun hiltSideResolutionIsStableAcrossReads() {
        // The delegating @Provides is @Singleton; every Hilt read (all 13 bridges) is the same object.
        assertSame(
            "Repeated Hilt resolutions must be identical — a single owner behind the seam",
            hiltResolved(),
            hiltResolved(),
        )
    }

    @Test
    fun metroSideResolutionIsStableAcrossReads() {
        // @SingleIn(AppScope): the Metro owner retains one instance for the process.
        assertSame(
            "Repeated Metro-direct reads must be identical — @SingleIn(AppScope) retention",
            metroDirect(),
            metroDirect(),
        )
    }

    // ========================================================================================
    // App-Scope Collapse Step 3 (mvi slice) — the FIRST adopt-back SHIM + COLLIDER seam proof.
    // NumUiUtils (clean) never exercised the shim; these tests do.
    // ========================================================================================

    private fun loggerHiltResolved(): LoggerHolder =
        EntryPointAccessors
            .fromApplication(
                ApplicationProvider.getApplicationContext(),
                TestLoggerEntryPoint::class.java,
            )
            .loggerHolder()

    // Metro-side consumer path: the graph's own accessor IS the Metro-owned instance BaseStore
    // ctor-injects (BaseStore is feature/Metro-constructed and receives loggerHolder from the graph).
    private fun loggerMetroDirect(): LoggerHolder = TestAppGraphModule.testAppGraph.loggerHolder

    @Test
    fun loggerHolder_hiltAdoptBackAndMetroResolveTheSameInstance() {
        val metro = loggerMetroDirect()
        val hilt = loggerHiltResolved()

        assertNotNull(metro)
        assertNotNull(hilt)
        // POSITIVE seam proof across BOTH sides (not two Hilt reads): the Hilt EntryPoint resolution
        // (through the adopt-back delegating @Provides) returns the SAME object the Metro graph owns and
        // hands to Metro consumers. === identity survives the shim.
        assertSame(
            "LoggerHolder Hilt adopt-back must return the SAME instance the Metro graph owns (===), not a copy",
            metro,
            hilt,
        )
    }

    @Test
    fun storeDispatchers_collidersResolveWithCorrectQualifiersPostMigration() {
        // COLLIDER: StoreDispatchers is Metro-constructed from the two qualified CoroutineDispatcher
        // bound instances; the qualifiers survived includeJavax into the graph. Both dispatchers resolve
        // (non-null, distinct fields) and the Hilt adopt-back returns the SAME Metro-owned instance.
        val metro = TestAppGraphModule.testAppGraph.storeDispatchers
        val hilt = EntryPointAccessors
            .fromApplication(
                ApplicationProvider.getApplicationContext<Context>(),
                TestStoreDispatchersEntryPoint::class.java,
            )
            .storeDispatchers()

        assertNotNull(metro.defaultDispatcher)
        assertNotNull(metro.mainImmediateDispatcher)
        assertSame(
            "StoreDispatchers Hilt adopt-back must return the SAME Metro-owned instance (===)",
            metro,
            hilt,
        )
    }

    @Test
    fun dispatchers_hiltAdoptBackAndMetroResolveTheSameQualifiedInstances() {
        // App-Scope Collapse Step 3 (PF commit 1): the FIRST provides-factory (@BindingContainer) migration.
        // The 3 dispatchers with Hilt readers are Metro-owned; each qualified adopt-back @Provides returns
        // the SAME instance the graph's DispatchersBindingContainer produced. Qualifiers survive includeJavax.
        val ep = EntryPointAccessors.fromApplication(
            ApplicationProvider.getApplicationContext<Context>(), TestDispatchersEntryPoint::class.java)
        assertSame("@DefaultDispatcher ===", TestAppGraphModule.testAppGraph.defaultDispatcher, ep.defaultDispatcher())
        assertSame("@MainImmediateDispatcher ===", TestAppGraphModule.testAppGraph.mainImmediateDispatcher, ep.mainImmediateDispatcher())
        assertSame("@IODispatcher ===", TestAppGraphModule.testAppGraph.ioDispatcher, ep.ioDispatcher())
    }

    @Test
    fun resourceWrapper_hiltAdoptBackAndMetroResolveTheSameInstance() {
        // App-Scope Collapse Step 3 (PF commit 2A): ResourceWrapper via ResourceWrapperBindingContainer.
        // The ten feature *HiltEntryPoint.resourceWrapper() bridges resolve the SAME Metro-owned instance.
        val ep = EntryPointAccessors.fromApplication(
            ApplicationProvider.getApplicationContext<Context>(), TestResourceWrapperEntryPoint::class.java)
        assertSame(
            "ResourceWrapper ===",
            TestAppGraphModule.testAppGraph.resourceWrapper,
            ep.resourceWrapper(),
        )
    }

    @Test
    fun navigator_hiltAdoptBackConcreteAndInterfaceResolveTheSameInstance() {
        // App-Scope Collapse Step 3 (PF commit 2C): NavigatorEventBus contributes Navigator AND is exposed
        // as its concrete type (AppRootViewModel injects the concrete). One @SingleIn(AppScope) instance:
        // the interface binding, the concrete accessor, and both Hilt adopt-backs are all ===.
        val ep = EntryPointAccessors.fromApplication(
            ApplicationProvider.getApplicationContext<Context>(), TestNavigatorEntryPoint::class.java)
        val ifaceMetro = TestAppGraphModule.testAppGraph.navigator
        val concreteMetro = TestAppGraphModule.testAppGraph.navigatorEventBus
        assertSame("Navigator interface === concrete NavigatorEventBus", ifaceMetro as Any, concreteMetro as Any)
        assertSame("Navigator Hilt adopt-back === Metro-owned", ifaceMetro, ep.navigator())
        assertSame("NavigatorEventBus Hilt adopt-back === Metro-owned", concreteMetro, ep.navigatorEventBus())
    }

    @Test
    fun exerciseRepositories_hiltAdoptBackAndMetroResolveTheSameInstances() {
        // App-Scope Collapse Step 3 (C2): the exercise repos are Metro-owned (@ContributesBinding); their
        // still-Hilt readers (feature *HiltEntryPoint + pure-Hilt @Inject) resolve === the Metro-owned instance
        // through the adopt-back shims. Two representative repos (Exercise + Session — the two heaviest).
        val ep = EntryPointAccessors.fromApplication(
            ApplicationProvider.getApplicationContext<Context>(), TestExerciseReposEntryPoint::class.java)
        assertSame(
            "ExerciseRepository Hilt adopt-back === Metro-owned",
            TestAppGraphModule.testAppGraph.exerciseRepository,
            ep.exerciseRepository(),
        )
        assertSame(
            "SessionRepository Hilt adopt-back === Metro-owned",
            TestAppGraphModule.testAppGraph.sessionRepository,
            ep.sessionRepository(),
        )
    }

    @Test
    fun dbCascade_appDatabaseResolvesAsOneInstance_andHiltAdoptBackMatchesMetro() {
        // App-Scope Collapse Step 5 (5a). THE cascade invariant: the 3 AppDatabase-derived bindings are
        // Metro-owned (@ContributesBinding) and all derive from the ONE appDatabase create() root.
        //  (a) DatabaseSnapshotProvider === LiveDatabaseLocator: repeatable @ContributesBinding binds BOTH
        //      interfaces to the SAME DatabaseSnapshotProviderImpl, which holds the ONE AppDatabase. If this
        //      split into two instances (or two AppDatabase handles), swap/rollback would operate on
        //      divergent Room handles — the exact double-handle corruption the atomicity rule prevents.
        //  (b) each Hilt adopt-back shim === the Metro-owned instance (the still-Hilt restore-path readers
        //      resolve the same object).
        val ep = EntryPointAccessors.fromApplication(
            ApplicationProvider.getApplicationContext<Context>(), TestDbCascadeEntryPoint::class.java)
        val snapshotMetro = TestAppGraphModule.testAppGraph.databaseSnapshotProvider
        val locatorMetro = TestAppGraphModule.testAppGraph.liveDatabaseLocator

        // (a) ONE instance behind both DB-locator interfaces (one AppDatabase, one impl).
        assertSame(
            "DatabaseSnapshotProvider === LiveDatabaseLocator (one impl, one AppDatabase root)",
            snapshotMetro as Any,
            locatorMetro as Any,
        )
        // (b) Hilt adopt-back === Metro-owned, for all three DB-bindings.
        assertSame(
            "DatabaseSnapshotProvider Hilt adopt-back === Metro-owned",
            snapshotMetro,
            ep.databaseSnapshotProvider(),
        )
        assertSame(
            "LiveDatabaseLocator Hilt adopt-back === Metro-owned",
            locatorMetro,
            ep.liveDatabaseLocator(),
        )
        // (DatabaseJsonExporter's === leg was removed in 5b: it no longer has a Hilt adopt-back shim — its sole
        // still-Hilt reader, SnapshotExportRunnerImpl, is now Metro-owned. Its Metro identity is exercised
        // transitively via the SnapshotExportRunner leg in googleDriveAuthChain_....)
    }

    @Test
    fun negativeControl_hiltTestApplicationIsNotAnAppGraphOwner() {
        // NEGATIVE CONTROL (retargeted, Phase B3/V.1): assert the INVARIANT the decoupled
        // AppGraphSourceModule fallback actually relies on — that the Hilt test harness's Application is
        // NOT an AppGraphOwner, so `provideAppGraph` takes the else/fallback branch (build the real graph)
        // rather than `application.appGraph`. If HiltTestApplication WERE an owner, the fallback would
        // never run and the whole cross-module test path would be exercising an untested branch — a
        // false-green. This replaces the vestigial `context as BaseApplication` assertion (the prod code
        // no longer casts; it does this `is` check).
        val app = ApplicationProvider.getApplicationContext<Context>().applicationContext
        assertFalse(
            "HiltTestApplication must NOT be an AppGraphOwner — the fallback branch that builds the real " +
                "graph depends on it; if it were an owner, that branch would never run (false-green).",
            app is AppGraphOwner,
        )
    }

    @Test
    fun backupPreferencesRepository_hiltAdoptBackAndMetroResolveTheSameInstance() {
        // App-Scope Collapse Step 3 backup/scheduling slice: the FIRST @ContributesBinding (interface-bound,
        // public impl) WITH an adopt-back shim. POSITIVE seam proof across both sides.
        val metro = TestAppGraphModule.testAppGraph.backupPreferencesRepository
        // Leg 1 — SettingsHiltEntryPoint-equivalent (a Metro feature's Hilt EntryPoint reader).
        val hiltSettings = EntryPointAccessors
            .fromApplication(
                ApplicationProvider.getApplicationContext<Context>(),
                TestBackupPrefsSettingsEntryPoint::class.java,
            )
            .backupPreferencesRepository()
        // Leg 2 — BackupWorkerHiltEntryPoint-equivalent. NOTE plain Hilt EntryPoint (the Step-2
        // MetroWorkerFactory is dormant) — a 2nd Hilt reader, NOT a live Metro-bridge cross-check.
        val hiltWorker = EntryPointAccessors
            .fromApplication(
                ApplicationProvider.getApplicationContext<Context>(),
                TestBackupPrefsWorkerEntryPoint::class.java,
            )
            .backupPreferencesRepository()

        assertNotNull(metro)
        // Both Hilt-side readers (through the SINGLE adopt-back @Provides) return the SAME Metro-owned
        // instance — === not ==.
        assertSame("Settings EntryPoint must resolve the Metro-owned BackupPreferencesRepository (===)", metro, hiltSettings)
        assertSame("Worker EntryPoint must resolve the SAME instance (===)", metro, hiltWorker)
    }

    @Test
    fun commonDataStore_hiltAdoptBackAndMetroResolveTheSameInstance() {
        // App-Scope Collapse Step 3 CommonDataStore slice: the FIRST assisted-DI flip (Metro-native
        // @AssistedInject/@AssistedFactory, interface-bound public impl) WITH an adopt-back shim.
        // CommonDataStore is a STATEFUL singleton — one DataStore over `common_prefs`. If the Hilt reader
        // resolved a SECOND CommonDataStoreImpl, two DataStore instances would write the same file =>
        // corruption/race. Static gates are blind to this; the === identity is the whole point of the seam.
        val metro = TestAppGraphModule.testAppGraph.commonDataStore
        // Hilt-side reader through the single adopt-back @Provides — mirrors AppRootViewModel (@HiltViewModel),
        // SettingsHiltEntryPoint.commonDataStore(), and SettingsGraph's @Provides bridge-read, all of which
        // resolve CommonDataStore from Hilt via that one provider.
        val hilt = EntryPointAccessors
            .fromApplication(
                ApplicationProvider.getApplicationContext<Context>(),
                TestCommonDataStoreEntryPoint::class.java,
            )
            .commonDataStore()

        assertNotNull(metro)
        // The Hilt-side reader returns the SAME Metro-owned instance — === not == (single DataStore owner).
        assertSame("Hilt EntryPoint must resolve the Metro-owned CommonDataStore (===)", metro, hilt)
    }

    @Test
    fun activityHolder_bothTypesAndProducerHiltAdoptBackResolveTheSameSingleInstance() {
        // App-Scope Collapse Step 3 ui-kit slice: ActivityHolderImpl backs TWO interfaces via repeatable
        // @ContributesBinding. Both bound Metro types are the same retained instance. The ActivityHolder
        // adopt-back @Provides was removed in the L-tail slice (its sole Hilt reader ResourceManagerImpl was
        // a dead binding and was DELETED), so only the ActivityHolderProducer leg still has a Hilt adopt-back.
        val holderMetro = TestAppGraphModule.testAppGraph.activityHolder
        val producerMetro = TestAppGraphModule.testAppGraph.activityHolderProducer
        val producerHilt = EntryPointAccessors
            .fromApplication(ApplicationProvider.getApplicationContext<Context>(), TestActivityHolderEntryPoint::class.java)
            .activityHolderProducer()

        assertNotNull(holderMetro)
        // one @SingleIn(AppScope) ActivityHolderImpl → both bound types === the same object.
        assertSame("ActivityHolder and ActivityHolderProducer are the SAME ActivityHolderImpl (===)", holderMetro as Any, producerMetro as Any)
        assertSame("Hilt adopt-back ActivityHolderProducer === Metro-owned", producerMetro, producerHilt)
    }

    @Test
    fun platformBindings_hiltAdoptBackAndMetroResolveTheSameInstance() {
        // App-Scope Collapse Step 3 core-android platform slice: 3 interface-bound @ContributesBinding
        // impls (PlatformInfoProvider/TempFileProvider/AppReinitializer). Each Hilt adopt-back === Metro.
        val ep = EntryPointAccessors.fromApplication(
            ApplicationProvider.getApplicationContext<Context>(), TestPlatformEntryPoint::class.java)
        assertSame("PlatformInfoProvider ===", TestAppGraphModule.testAppGraph.platformInfoProvider, ep.platformInfoProvider())
        assertSame("TempFileProvider ===", TestAppGraphModule.testAppGraph.tempFileProvider, ep.tempFileProvider())
        assertSame("AppReinitializer ===", TestAppGraphModule.testAppGraph.appReinitializer, ep.appReinitializer())
    }

    @Test
    fun restoreStateRepository_hiltAdoptBackAndMetroResolveTheSameInstance() {
        val ep = EntryPointAccessors.fromApplication(
            ApplicationProvider.getApplicationContext<Context>(), TestRestoreStateEntryPoint::class.java)
        assertSame("RestoreStateRepository ===", TestAppGraphModule.testAppGraph.restoreStateRepository, ep.restoreStateRepository())
    }

    @Test
    fun workerBindings_hiltAdoptBackAndMetroResolveTheSameInstance() {
        // Only BackupNotificationHelper is asserted here. AutoBackupController (BackupScheduler) eagerly calls
        // WorkManager.getInstance(context) in its init — which throws under HiltTestApplication (no
        // Configuration.Provider). It was never constructed in tests before (lazy @Singleton, no trigger);
        // the seam test must NOT force-construct it. Its adopt-back is validated by the app:dev flavor
        // Regression (a real Configuration.Provider app) instead.
        val ep = EntryPointAccessors.fromApplication(
            ApplicationProvider.getApplicationContext<Context>(), TestWorkerEntryPoint::class.java)
        assertSame(
            "BackupNotificationHelper ===",
            TestAppGraphModule.testAppGraph.backupNotificationHelper,
            ep.backupNotificationHelper(),
        )
    }

    @Test
    fun appDialogBindings_hiltAdoptBackAndMetroResolveTheSameInstances() {
        // App-Scope Collapse Step 3 app-dialogs slice: 3 impls, 4 shims. The observer is contributed as
        // its interface AND exposed as its concrete type — one @SingleIn(AppScope) instance backs both.
        val ep = EntryPointAccessors.fromApplication(
            ApplicationProvider.getApplicationContext<Context>(), TestAppDialogsEntryPoint::class.java)

        val repoMetro = TestAppGraphModule.testAppGraph.appDialogRepository
        val observerImplMetro = TestAppGraphModule.testAppGraph.appDialogObserverImpl
        val observerMetro = TestAppGraphModule.testAppGraph.appDialogObserver
        val publisherMetro = TestAppGraphModule.testAppGraph.appDialogPublisher

        assertNotNull(repoMetro)
        // Self-bound concrete Repository: read by the feature's OWN Hilt EntryPoint (feeds AppDialogGraph).
        assertSame("AppDialogRepository Hilt adopt-back === Metro-owned", repoMetro, ep.appDialogRepository())
        // ObserverImpl concrete (read by AppDialogFeature) === the interface binding (cross-module readers):
        // one @SingleIn instance, contributed + self-exposed.
        assertSame("AppDialogObserver interface === concrete AppDialogObserverImpl", observerImplMetro as Any, observerMetro as Any)
        assertSame("AppDialogObserverImpl Hilt adopt-back === Metro-owned", observerImplMetro, ep.appDialogObserverImpl())
        assertSame("AppDialogObserver Hilt adopt-back === Metro-owned", observerMetro, ep.appDialogObserver())
        // Publisher interface (read cross-module by settings).
        assertNotNull(publisherMetro)
        assertSame("AppDialogPublisher Hilt adopt-back === Metro-owned", publisherMetro, ep.appDialogPublisher())
    }

    @Test
    fun accountDataStore_hiltAdoptBackAndMetroResolveTheSameInstance() {
        // App-Scope Collapse Step 3 google-drive slice: the one Context-only gd binding (no cross-module
        // reader). The four gd consumers stay Hilt and resolve through the adopt-back shim === Metro-owned.
        val ep = EntryPointAccessors.fromApplication(
            ApplicationProvider.getApplicationContext<Context>(), TestAccountDataStoreEntryPoint::class.java)
        assertSame(
            "AccountDataStore ===",
            TestAppGraphModule.testAppGraph.accountDataStore,
            ep.accountDataStore(),
        )
    }

    @Test
    fun googleDriveAuthChain_facadeAndInternalCrossReadResolveTheSameMetroInstances() {
        // App-Scope Collapse Step 3 (PF.3): the gd auth-chain atomic unit. GMS/ktor stay inside gd's
        // @BindingContainers; app/app names only these clean facade/inner interfaces.
        val ep = EntryPointAccessors.fromApplication(
            ApplicationProvider.getApplicationContext<Context>(), TestGoogleDriveEntryPoint::class.java)

        // POSITIVE (facade): the still-Hilt settings/worker EntryPoints resolve === the Metro-owned instances.
        assertSame(
            "BackupAuth Hilt adopt-back === Metro-owned",
            TestAppGraphModule.testAppGraph.backupAuth,
            ep.backupAuth(),
        )
        assertSame(
            "BackupStorage Hilt adopt-back === Metro-owned",
            TestAppGraphModule.testAppGraph.backupStorage,
            ep.backupStorage(),
        )
        // POSITIVE (App-Scope Collapse Step 5, 5b): SnapshotExportRunner is now Metro-owned
        // (@ContributesBinding). Its still-Hilt readers — BackupWorker (via BackupWorkerHiltEntryPoint) +
        // settings (SettingsHiltEntryPoint) — resolve === the Metro-owned instance through the adopt-back shim.
        // This replaces the transient SnapshotStorage cross-read leg (that shim retired in 5b: its sole Hilt
        // reader, SnapshotExportRunnerImpl, is now Metro and resolves SnapshotStorage/DatabaseJsonExporter direct).
        assertSame(
            "SnapshotExportRunner Hilt adopt-back === Metro-owned",
            TestAppGraphModule.testAppGraph.snapshotExportRunner,
            ep.snapshotExportRunner(),
        )
    }

    /**
     * Equivalent to a production `*HiltEntryPoint.analyticsHolder()`: reads `AnalyticsHolder` from
     * Hilt's `SingletonComponent`, now served exclusively by the adopt-back delegating `@Provides`.
     * Declared here because the real feature EntryPoints are module-`internal`. (An `@EntryPoint`
     * MAY be nested in a `@HiltAndroidTest` class; only `@TestInstallIn` modules may not.)
     */
    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface TestAnalyticsEntryPoint {
        fun analyticsHolder(): AnalyticsHolder
    }

    /** Mirrors the 13 production `*HiltEntryPoint.loggerHolder()` accessors (module-`internal`). */
    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface TestLoggerEntryPoint {
        fun loggerHolder(): LoggerHolder
    }

    /** Mirrors the 13 production `*HiltEntryPoint.storeDispatchers()` accessors. */
    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface TestStoreDispatchersEntryPoint {
        fun storeDispatchers(): StoreDispatchers
    }

    /** Mirrors SettingsHiltEntryPoint.backupPreferencesRepository() (module-`internal`). */
    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface TestBackupPrefsSettingsEntryPoint {
        fun backupPreferencesRepository(): BackupPreferencesRepository
    }

    /** Mirrors BackupWorkerHiltEntryPoint.backupPreferencesRepository() (the Step-2 worker bridge, dormant). */
    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface TestBackupPrefsWorkerEntryPoint {
        fun backupPreferencesRepository(): BackupPreferencesRepository
    }

    /** Mirrors the 3 Hilt-side CommonDataStore readers (AppRootViewModel @HiltViewModel,
     * SettingsHiltEntryPoint.commonDataStore(), SettingsGraph @Provides) — all resolve via the one adopt-back. */
    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface TestCommonDataStoreEntryPoint {
        fun commonDataStore(): CommonDataStore
    }

    /** Mirrors MainActivity's ActivityHolderProducer read (the ActivityHolder adopt-back was removed in the
     * L-tail slice once ResourceManagerImpl, its sole Hilt reader, was deleted as a dead binding). */
    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface TestActivityHolderEntryPoint {
        fun activityHolderProducer(): ActivityHolderProducer
    }

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface TestPlatformEntryPoint {
        fun platformInfoProvider(): PlatformInfoProvider
        fun tempFileProvider(): TempFileProvider
        fun appReinitializer(): AppReinitializer
    }

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface TestRestoreStateEntryPoint {
        fun restoreStateRepository(): RestoreStateRepository
    }

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface TestWorkerEntryPoint {
        fun autoBackupController(): AutoBackupController
        fun backupNotificationHelper(): BackupNotificationHelper
    }

    /**
     * Mirrors app-dialogs' readers: the feature's own `AppDialogsHiltEntryPoint` (repository + observerImpl
     * concrete, feeding its Metro `AppDialogGraph`) plus the cross-module api-interface readers in settings
     * (`AppDialogPublisher`) and recovery/archive/`BaseApplication` (`AppDialogObserver`).
     */
    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface TestAppDialogsEntryPoint {
        fun appDialogRepository(): AppDialogRepository
        fun appDialogObserverImpl(): AppDialogObserverImpl
        fun appDialogObserver(): AppDialogObserver
        fun appDialogPublisher(): AppDialogPublisher
    }

    /** Mirrors the four gd readers of AccountDataStore (DriveAuthTokenProvider et al., module-`internal`). */
    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface TestAccountDataStoreEntryPoint {
        fun accountDataStore(): AccountDataStore
    }

    /**
     * Mirrors the gd auth-chain facade readers: settings + worker read `BackupAuth` / `BackupStorage`; the
     * still-Hilt (Step-5-deferred) `SnapshotExportRunnerImpl` reads `SnapshotStorage`. All three are GMS/ktor-clean
     * — the GMS `AuthorizationClient` + ktor `HttpClient` never appear here (HOME-A containment).
     */
    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface TestGoogleDriveEntryPoint {
        fun backupAuth(): BackupAuth
        fun backupStorage(): BackupStorage
        fun snapshotExportRunner(): SnapshotExportRunner
    }

    /** Mirrors the feature `*HiltEntryPoint` / pure-Hilt readers of the (now Metro-owned) exercise repos. */
    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface TestExerciseReposEntryPoint {
        fun exerciseRepository(): ExerciseRepository
        fun sessionRepository(): SessionRepository
    }

    /**
     * Mirrors the still-Hilt readers of the (now Metro-owned) DB-cascade interface bindings (App-Scope
     * Collapse Step 5, 5a): the restore path (BackupWorker / RecoveryActivity / recovery observers /
     * settings) reads these from Hilt's `SingletonComponent` through the adopt-back shims.
     */
    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface TestDbCascadeEntryPoint {
        fun databaseSnapshotProvider(): DatabaseSnapshotProvider
        fun liveDatabaseLocator(): LiveDatabaseLocator
    }

    /**
     * Mirrors the still-Hilt dispatcher readers (feature `*HiltEntryPoint` bridges + pure-Hilt `@Inject`
     * interactors / handlers / `core:data` repositories) — each resolves the qualified dispatcher from
     * Hilt's `SingletonComponent`, now served by the adopt-back shims delegating to the Metro graph.
     */
    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface TestDispatchersEntryPoint {
        @DefaultDispatcher
        fun defaultDispatcher(): CoroutineDispatcher

        @MainImmediateDispatcher
        fun mainImmediateDispatcher(): CoroutineDispatcher

        @IODispatcher
        fun ioDispatcher(): CoroutineDispatcher
    }

    /** Mirrors the ten feature `*HiltEntryPoint.resourceWrapper()` bridges. */
    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface TestResourceWrapperEntryPoint {
        fun resourceWrapper(): ResourceWrapper
    }

    /** Mirrors the 12 feature `*HiltEntryPoint.navigator()` bridges + `AppRootViewModel`'s concrete inject. */
    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface TestNavigatorEntryPoint {
        fun navigator(): Navigator
        fun navigatorEventBus(): NavigatorEventBus
    }
}

/**
 * `@TestInstallIn` REPLACES ONLY [AppGraphSourceModule] (Phase D2 decouple) — the single unit that
 * reaches the `BaseApplication`-held graph, absent under `HiltTestApplication`. It does NOT replace
 * [AppGraphAdoptBackModule]: the REAL adopt-back shims (`provideAnalyticsHolder`, etc.) stay live and
 * consume the test-built `AppGraph` this module provides, so the `===` proof exercises the PRODUCTION
 * delegation, not a hand-copied double (the honesty gap this phase fixes). TOP-LEVEL, not nested in the
 * `@HiltAndroidTest` class — Hilt forbids nesting `@TestInstallIn` modules in test classes.
 *
 * NOTE the prod [AppGraphSourceModule] now ALSO builds the real graph from `applicationContext` when the
 * Application is not an `AppGraphOwner` — so the flavor (`app:dev`/`app:store`) tests need no replacement
 * at all. This module remains for `app:app`'s own seam test to expose `testAppGraph` for the Metro-direct
 * assertion path.
 */
@Module
@TestInstallIn(
    components = [SingletonComponent::class],
    replaces = [AppGraphSourceModule::class],
)
internal object TestAppGraphModule {

    // A single process-wide test graph, mirroring the prod `by lazy` app-owned singleton. App-Scope
    // Collapse Step 3 (PF commit 1): the dispatchers are Metro-owned (DispatchersBindingContainer), so
    // create() takes only applicationContext — the same shrunk signature the prod BaseApplication uses.
    val testAppGraph: AppGraph by lazy {
        createGraphFactory<AppGraph.Factory>()
            .create(
                applicationContext = ApplicationProvider.getApplicationContext<Context>(),
                // App-Scope Collapse Step 5 (5a): create() collapsed to 3 roots. The AppDatabase root is an
                // in-memory Room instance (the test-override swap for the file-backed prod DB); the 9 DAOs +
                // DbTransitionRunner + the 3 DB-bindings all derive from it graph-internally. ImageStorage uses
                // the real FakeImageStorage, mirroring the @TestInstallIn fake the prod bridge reads. This is
                // the AppDatabase-resolves-as-ONE-instance invariant that makes it a cascade, not 11 flips.
                appDatabase = InMemoryDatabaseProvider.create(ApplicationProvider.getApplicationContext()),
                imageStorage = FakeImageStorage(),
            )
    }

    // Phase D2 decouple: TestAppGraphModule replaces ONLY AppGraphSourceModule, so it provides ONLY the
    // graph source. The real adopt-back shims (provideAnalyticsHolder / provideLoggerHolder /
    // provideStoreDispatchers) in AppGraphAdoptBackModule stay live and consume this graph — the === proof
    // exercises the PRODUCTION shims, not test copies.
    @Provides
    @Singleton
    fun provideAppGraph(): AppGraph = testAppGraph
}
