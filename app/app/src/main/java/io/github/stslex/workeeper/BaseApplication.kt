// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import dev.zacsweers.metro.HasMemberInjections
import io.github.stslex.workeeper.core.core.images.ImageStorage
import io.github.stslex.workeeper.core.core.logger.FirebaseCrashlyticsHolder
import io.github.stslex.workeeper.core.core.logger.Log
import io.github.stslex.workeeper.core.data.database.common.DbTransitionRunner
import io.github.stslex.workeeper.core.data.database.exercise.ExerciseDao
import io.github.stslex.workeeper.core.data.database.session.PerformedExerciseDao
import io.github.stslex.workeeper.core.data.database.session.SessionDao
import io.github.stslex.workeeper.core.data.database.session.SetDao
import io.github.stslex.workeeper.core.data.database.tag.ExerciseTagDao
import io.github.stslex.workeeper.core.data.database.tag.TagDao
import io.github.stslex.workeeper.core.data.database.tag.TrainingTagDao
import io.github.stslex.workeeper.core.data.database.training.TrainingDao
import io.github.stslex.workeeper.core.data.database.training.TrainingExerciseDao
import io.github.stslex.workeeper.core.ui.mvi.performance.PerformanceMetricsRecorder
import io.github.stslex.workeeper.core.ui.mvi.performance.RecordAction
import io.github.stslex.workeeper.di.AppGraph
import io.github.stslex.workeeper.di.AppGraphOwner
import io.github.stslex.workeeper.di.buildAppGraph
import io.github.stslex.workeeper.feature.recovery.boot.AppDialogObserverBootstrapEntryPoint
import io.github.stslex.workeeper.feature.recovery.domain.RestoreRecoveryCoordinator
import io.github.stslex.workeeper.feature.recovery.domain.StartupMigrationCoordinator
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import javax.inject.Inject

// Metro (now applied to app/app) scans this module and sees the Hilt `@Inject workerFactory` member
// on this NON-FINAL class; it requires @HasMemberInjections for subclass-propagation safety (the
// flavor subclasses extend it). This is a Metro↔Hilt coexistence acknowledgement only — Hilt still
// performs the actual member injection. This friction generalizes to any Metro-enabled module that
// retains a Hilt-member-injected non-final class; it recurs on the real app flip.
@HasMemberInjections
abstract class BaseApplication : Application(), Configuration.Provider, AppGraphOwner {

    abstract val isDebugLoggingAllow: Boolean

    /**
     * The Metro app-scope graph (KMP C.1 app-collapse Phase 1 — leaf E-proof), held for the whole
     * process ALONGSIDE `@HiltAndroidApp`. `by lazy` so it is created on first access (a feature
     * Store construction, well after `onCreate`), which guarantees `applicationContext` is ready.
     *
     * Exposed via [AppGraphOwner] (NOT a concrete-type cast) so Hilt reads it through the interface —
     * `AppGraphAdoptBackModule.provideAppGraph` resolves the graph from the app context as an
     * `AppGraphOwner`, and instrumented tests can `@TestInstallIn`-replace that provider without a
     * `BaseApplication` (the Hilt test harness swaps in `HiltTestApplication`).
     *
     * `@Suppress(EXPOSED_PROPERTY_TYPE)`: `AppGraph`/`AppGraphOwner` are `internal` to `:app:app` and
     * this override is only ever read through the `internal AppGraphOwner` seam within the module —
     * the public class surface never leaks the internal type to another module (flavor subclasses only
     * call `super`). Keeping the DI types module-internal is deliberate.
     */
    @Suppress("EXPOSED_PROPERTY_TYPE_IN_CONSTRUCTOR_ERROR", "EXPOSED_PROPERTY_TYPE")
    override val appGraph: AppGraph by lazy {
        // App-Scope Collapse Step 3 (C2, bridge-scaffold): the exercise-repo db-cascade substrate (9 DAOs +
        // DbTransitionRunner + ImageStorage) is bridge-READ from Hilt (they stay Hilt-owned) and handed to
        // create(). Reading the Hilt-BOUND ImageStorage (not constructing) keeps @TestInstallIn fakes intact.
        // Cycle-free since C2-h' (DbTransitionRunner/ImageStorage need @IO = a direct Hilt Dispatchers.IO, no
        // appGraph re-entry). Transient — each param retires when its owning tier migrates (db-cascade = Step 5).
        val db = EntryPointAccessors.fromApplication(
            applicationContext,
            DbCascadeBridgeEntryPoint::class.java,
        )
        buildAppGraph(
            applicationContext = applicationContext,
            exerciseDao = db.exerciseDao(),
            exerciseTagDao = db.exerciseTagDao(),
            performedExerciseDao = db.performedExerciseDao(),
            sessionDao = db.sessionDao(),
            setDao = db.setDao(),
            tagDao = db.tagDao(),
            trainingDao = db.trainingDao(),
            trainingExerciseDao = db.trainingExerciseDao(),
            trainingTagDao = db.trainingTagDao(),
            dbTransitionRunner = db.dbTransitionRunner(),
            imageStorage = db.imageStorage(),
        )
    }

    @Inject
    internal lateinit var workerFactory: HiltWorkerFactory

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()

    override fun onCreate() {
        super.onCreate()
        FirebaseCrashlyticsHolder.initialize()
        Log.isLogging = isDebugLoggingAllow
        handleRecoveryPreflightChain()
        cleanupOrphanedImageTempFiles()
        bootstrapAppDialogObserver()
        PerformanceMetricsRecorder.process(RecordAction.AppCreated)
    }

    /**
     * Runs the two recovery pre-flights in the order required by
     * `documentation/feature-specs/backup-recovery.md`:
     *
     * 1. **Scenario 1** (post-restart restore migration). If the
     *    `restore_in_progress` flag is set, the coordinator either publishes
     *    a `RestoreSuccess` dialog and returns `RestoreSucceeded` (continue
     *    to MainActivity), or rolls back the live db and returns
     *    `RestoreRolledBack` (caller restarts — this method never returns).
     *    `NoOp` means there was no restore in progress; fall through.
     * 2. **Scenario 2** (startup migration failure / developer error).
     *    Only runs after Scenario 1 was a no-op. Reads the live db's
     *    schema via a Room-free SQLite peek and decides whether to
     *    `Proceed` (MainActivity opens normally) or `RouteToRecovery`
     *    (MainActivity reads `coordinator.lastDecision` and finishes
     *    itself, launching `RecoveryActivity`).
     *
     * Both checks run under `runBlocking` because the alternative —
     * dispatching on a background coroutine after `setContent` — would
     * briefly show MainActivity content before recovery routing decides.
     * The work is bounded: a DataStore read, one SQLite version peek, and
     * (on failure) one file copy. Steady-state cost on a healthy install
     * is ~one DataStore read and one peek.
     */
    private fun handleRecoveryPreflightChain() {
        val recoveryEntryPoint = EntryPointAccessors.fromApplication(
            this,
            RecoveryEntryPoint::class.java,
        )
        val restoreOutcome = runBlocking {
            recoveryEntryPoint.restoreRecoveryCoordinator().handlePostRestoreLaunch()
        }
        if (restoreOutcome == RestoreRecoveryCoordinator.PreflightOutcome.RestoreRolledBack) {
            recoveryEntryPoint.restoreRecoveryCoordinator().restartApp()
            return
        }
        if (restoreOutcome == RestoreRecoveryCoordinator.PreflightOutcome.RestoreSucceeded) {
            // The Scenario 1 success path leaves `pre_restore_backup.db` on
            // disk for the user's undo slot, and Room will open the
            // freshly-restored db on first DAO access. Scenario 2 has
            // nothing to add — skip.
            return
        }
        // Scenario 1 was a no-op (no restore in progress). Run Scenario 2.
        runBlocking {
            recoveryEntryPoint.startupMigrationCoordinator().checkAndRouteOrProceed()
        }
        // The result is cached on `StartupMigrationCoordinator.lastDecision`;
        // MainActivity reads it on its own onCreate to decide whether to
        // finish + launch RecoveryActivity.
    }

    private fun cleanupOrphanedImageTempFiles() {
        val imageStorage = EntryPointAccessors.fromApplication(
            this,
            ImageStorageEntryPoint::class.java,
        ).imageStorage()
        // Fire-and-forget on a one-shot IO coroutine — clearing temp files left
        // behind by killed camera-capture flows is best-effort.
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            imageStorage.cleanupTempFiles()
        }
    }

    /**
     * Eagerly construct the `@Singleton` cross-feature dialog reactor so its
     * `init { observer.observeUserActions()...launchIn(scope) }` registers a
     * subscriber on the SharedFlow BEFORE MainActivity.onCreate runs. Lazy
     * @Singleton construction would mean the first user dispatch fires on
     * zero subscribers and is lost (same failure class as the rehydrate bug
     * we're explicitly avoiding). The return value is intentionally
     * discarded — the side-effect of construction is what we want.
     *
     * Same EntryPoint pattern as [RecoveryEntryPoint] and
     * [ImageStorageEntryPoint]; see those for the established convention.
     */
    private fun bootstrapAppDialogObserver() {
        EntryPointAccessors.fromApplication(
            this,
            AppDialogObserverBootstrapEntryPoint::class.java,
        ).recoveryBootstrap()
    }

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    internal interface ImageStorageEntryPoint {
        fun imageStorage(): ImageStorage
    }

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    internal interface RecoveryEntryPoint {
        fun restoreRecoveryCoordinator(): RestoreRecoveryCoordinator
        fun startupMigrationCoordinator(): StartupMigrationCoordinator
    }

    /**
     * App-Scope Collapse Step 3 (C2, bridge-scaffold). Pulls the exercise-repo db-cascade substrate (9 Room
     * DAOs + [DbTransitionRunner] + [ImageStorage]) out of Hilt's `SingletonComponent` so [appGraph] can
     * bridge them into `create()` as bound instances. Reading the Hilt-BOUND [ImageStorage] preserves the
     * `@TestInstallIn` fake. Transient — each accessor retires when its owning tier migrates (db-cascade =
     * Step 5, ImageStorage = the C1 carveout).
     */
    @EntryPoint
    @InstallIn(SingletonComponent::class)
    internal interface DbCascadeBridgeEntryPoint {
        fun exerciseDao(): ExerciseDao
        fun exerciseTagDao(): ExerciseTagDao
        fun performedExerciseDao(): PerformedExerciseDao
        fun sessionDao(): SessionDao
        fun setDao(): SetDao
        fun tagDao(): TagDao
        fun trainingDao(): TrainingDao
        fun trainingExerciseDao(): TrainingExerciseDao
        fun trainingTagDao(): TrainingTagDao
        fun dbTransitionRunner(): DbTransitionRunner
        fun imageStorage(): ImageStorage
    }
}
