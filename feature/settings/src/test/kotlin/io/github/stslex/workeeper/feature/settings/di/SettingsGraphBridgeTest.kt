// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.settings.di

import android.content.Context
import dev.zacsweers.metro.createGraphFactory
import io.github.stslex.workeeper.core.core.platform.PlatformInfoProvider
import io.github.stslex.workeeper.core.core.platform.TempFileProvider
import io.github.stslex.workeeper.core.data.backup.api.BackupAuth
import io.github.stslex.workeeper.core.data.backup.api.BackupStorage
import io.github.stslex.workeeper.core.data.backup.api.SnapshotExportRunner
import io.github.stslex.workeeper.core.data.backup.api.restore.RestoreStateRepository
import io.github.stslex.workeeper.core.data.backup.api.scheduling.AutoBackupController
import io.github.stslex.workeeper.core.data.backup.api.scheduling.BackupPreferencesRepository
import io.github.stslex.workeeper.core.data.dataStore.store.CommonDataStore
import io.github.stslex.workeeper.core.data.database.snapshot.DatabaseSnapshotProvider
import io.github.stslex.workeeper.core.ui.mvi.di.StoreDispatchers
import io.github.stslex.workeeper.core.ui.mvi.holders.AnalyticsHolder
import io.github.stslex.workeeper.core.ui.mvi.holders.LoggerHolder
import io.github.stslex.workeeper.core.ui.navigation.Navigator
import io.github.stslex.workeeper.feature.app_dialogs.api.publisher.AppDialogPublisher
import io.mockk.mockk
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Test

/**
 * KMP C.1 — in-situ integration proof of BOTH bridge mechanisms on the HARDEST feature, using
 * settings' REAL [SettingsGraph] (not a focused test graph):
 *
 *  1. QUALIFIED DISPATCHERS (includeJavax): settings bridges two same-typed `CoroutineDispatcher`s
 *     — `@DefaultDispatcher` + `@IODispatcher`. The real graph must resolve each to its OWN bound
 *     instance under its OWN qualifier, with no cross-wire (the strip-bug the audit warned about).
 *  2. CONTEXT STRIP-ON-HILT: the app `Context` is bound bare into the graph (its `@ApplicationContext`
 *     qualifier stayed on the Hilt side). It must reach the graph as `===` the provided instance,
 *     and the whole graph — including the Context-consuming `BackupClickHandler`, constructed
 *     transitively via `settingsStore` — must resolve.
 *
 * Pure-JVM: `createGraphFactory` is Metro-compiler codegen, no Android runtime.
 */
internal class SettingsGraphBridgeTest {

    private val navigator = mockk<Navigator>(relaxed = true)
    private val platformInfoProvider = mockk<PlatformInfoProvider>(relaxed = true)
    private val commonDataStore = mockk<CommonDataStore>(relaxed = true)
    private val backupAuth = mockk<BackupAuth>(relaxed = true)
    private val backupStorage = mockk<BackupStorage>(relaxed = true)
    private val snapshotExportRunner = mockk<SnapshotExportRunner>(relaxed = true)
    private val databaseSnapshotProvider = mockk<DatabaseSnapshotProvider>(relaxed = true)
    private val restoreStateRepository = mockk<RestoreStateRepository>(relaxed = true)
    private val backupPreferencesRepository = mockk<BackupPreferencesRepository>(relaxed = true)
    private val autoBackupController = mockk<AutoBackupController>(relaxed = true)
    private val appDialogPublisher = mockk<AppDialogPublisher>(relaxed = true)
    private val tempFileProvider = mockk<TempFileProvider>(relaxed = true)
    private val storeDispatchers = StoreDispatchers(
        defaultDispatcher = Dispatchers.Unconfined,
        mainImmediateDispatcher = Dispatchers.Unconfined,
    )
    private val analyticsHolder = AnalyticsHolder()
    private val loggerHolder = LoggerHolder()

    // Two DISTINCT dispatcher instances so === identity distinguishes them unambiguously.
    private val defaultDispatcher: CoroutineDispatcher = Dispatchers.Default
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
    private val appContext = mockk<Context>(relaxed = true)

    private fun buildGraph(): SettingsGraph = createGraphFactory<SettingsGraph.Factory>()
        .create(
            navigator = navigator,
            platformInfoProvider = platformInfoProvider,
            commonDataStore = commonDataStore,
            backupAuth = backupAuth,
            backupStorage = backupStorage,
            snapshotExportRunner = snapshotExportRunner,
            databaseSnapshotProvider = databaseSnapshotProvider,
            restoreStateRepository = restoreStateRepository,
            backupPreferencesRepository = backupPreferencesRepository,
            autoBackupController = autoBackupController,
            appDialogPublisher = appDialogPublisher,
            tempFileProvider = tempFileProvider,
            storeDispatchers = storeDispatchers,
            analyticsHolder = analyticsHolder,
            loggerHolder = loggerHolder,
            defaultDispatcher = defaultDispatcher,
            ioDispatcher = ioDispatcher,
            context = appContext,
        )

    @Test
    fun `two qualified dispatchers resolve to their distinct instances with no cross-wire`() {
        val graph = buildGraph()

        // The crux: @DefaultDispatcher and @IODispatcher are BOTH CoroutineDispatcher, yet the
        // real graph resolves each to its OWN bound instance by qualifier — the strip-bug (both
        // arriving bare → merged) cannot happen here.
        assertSame(
            defaultDispatcher,
            graph.defaultDispatcher,
            "@DefaultDispatcher must resolve to the default instance in settings' real graph",
        )
        assertSame(
            ioDispatcher,
            graph.ioDispatcher,
            "@IODispatcher must resolve to the IO instance — not cross-wired to @DefaultDispatcher",
        )
    }

    @Test
    fun `bare Context reaches the graph as the same application Context instance`() {
        val graph = buildGraph()

        assertSame(
            appContext,
            graph.appContext,
            "The app Context (bridged bare, @ApplicationContext resolved on the Hilt side) must " +
                "reach the graph by identity",
        )
    }

    @Test
    fun `graph constructs the store, transitively wiring the Context-consuming BackupClickHandler`() {
        // settingsStore construction transitively builds BackupClickHandler (which injects the bare
        // Context) and both interactors (which inject the two qualified dispatchers). A successful
        // build proves the whole graph — every bridged dep incl. Context and both dispatchers —
        // resolves in situ.
        val store = buildGraph().settingsStore

        assertNotNull(store, "Metro must construct SettingsStoreImpl by wiring all 18 bridged deps")
    }

    @Test
    fun `bridged app-scoped singletons reach the store by identity not copy`() {
        val store = buildGraph().settingsStore

        assertSame(analyticsHolder, store.analyticsHolder, "AnalyticsHolder must be === the provided instance")
        assertSame(loggerHolder, store.loggerHolder, "LoggerHolder must be === the provided instance")
    }
}
