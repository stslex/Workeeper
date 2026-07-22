// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.core.di

import io.github.stslex.workeeper.core.core.di.DefaultDispatcher
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
import io.github.stslex.workeeper.core.data.dataStore.store.CommonDataStore
import io.github.stslex.workeeper.core.data.database.snapshot.DatabaseSnapshotProvider
import io.github.stslex.workeeper.core.data.exercise.exercise.ExerciseRepository
import io.github.stslex.workeeper.core.data.exercise.personal_record.PersonalRecordRepository
import io.github.stslex.workeeper.core.data.exercise.session.PerformedExerciseRepository
import io.github.stslex.workeeper.core.data.exercise.session.SessionConflictResolver
import io.github.stslex.workeeper.core.data.exercise.session.SessionRepository
import io.github.stslex.workeeper.core.data.exercise.session.SetRepository
import io.github.stslex.workeeper.core.data.exercise.tags.TagRepository
import io.github.stslex.workeeper.core.data.exercise.training.TrainingExerciseRepository
import io.github.stslex.workeeper.core.data.exercise.training.TrainingRepository
import io.github.stslex.workeeper.core.ui.mvi.di.StoreDispatchers
import io.github.stslex.workeeper.core.ui.mvi.holders.AnalyticsHolder
import io.github.stslex.workeeper.core.ui.mvi.holders.LoggerHolder
import io.github.stslex.workeeper.core.ui.navigation.Navigator
import kotlinx.coroutines.CoroutineDispatcher

/**
 * PUBLIC app-scope DI contract — the seam library consumers read the app graph through.
 *
 * Declares the app-scoped accessors that LIBRARY consumers need — `RecoveryActivity`, the feature
 * `*Feature.kt` bridges, and the worker — so they resolve app-scope bindings via
 * [Context.appGraphContract]. app/app's
 * `@DependencyGraph(scope = AppScope::class) internal interface AppGraph : AppGraphContract` extends
 * this interface; Metro treats the inherited `val`s as graph accessors (interface-inheritance proven
 * cross-module) and resolves them from the `@ContributesBinding(AppScope)` contributions.
 * The aggregation site — and `AppScope` — stay in app/app / `core:core-android`; this is a plain
 * interface with no Metro annotation.
 *
 * **Deliberately absent — feature/app-owned types a `core` module cannot name:**
 * the app-dialogs api/impl accessors (`AppDialogObserver` etc., feature-tier) and the concrete
 * `NavigatorEventBus` (app/app-owned) — these stay on `AppGraph` directly.
 */
interface AppGraphContract {

    // ── core:ui:mvi holders + store dispatchers ──
    val analyticsHolder: AnalyticsHolder
    val loggerHolder: LoggerHolder
    val storeDispatchers: StoreDispatchers

    // ── core:core-android qualified dispatchers ──
    @DefaultDispatcher
    val defaultDispatcher: CoroutineDispatcher

    @MainImmediateDispatcher
    val mainImmediateDispatcher: CoroutineDispatcher

    @IODispatcher
    val ioDispatcher: CoroutineDispatcher

    // ── core:core / core:core-android platform + resources + image storage ──
    val resourceWrapper: ResourceWrapper
    val platformInfoProvider: PlatformInfoProvider
    val tempFileProvider: TempFileProvider

    // ImageStorage — a `create()` bound-instance root on AppGraph;
    // exposed as an accessor so the exercise feature resolves it from the graph.
    val imageStorage: ImageStorage

    // SessionConflictResolver — self-bound @SingleIn(AppScope) @Inject
    // graph node; exposed so home + single-training features read it from the graph.
    val sessionConflictResolver: SessionConflictResolver

    // ── core:ui:navigation ──
    val navigator: Navigator

    // ── core:data:backup:api + worker (backup/restore/scheduling) ──
    val backupAuth: BackupAuth
    val backupStorage: BackupStorage
    val snapshotExportRunner: SnapshotExportRunner
    val restoreStateRepository: RestoreStateRepository
    val autoBackupController: AutoBackupController
    val backupPreferencesRepository: BackupPreferencesRepository
    val backupNotificationHelper: BackupNotificationHelper

    // ── feature/recovery (P-REC): impl Metro-owned, contract extracted to core:data:backup:api ──
    val recoveryDiagnosticsExporter: RecoveryDiagnosticsExporter

    // ── core:data:dataStore ──
    val commonDataStore: CommonDataStore

    // ── core:data:database (AppDatabase-derived, DB-safe on resolve; opens lazily) ──
    val databaseSnapshotProvider: DatabaseSnapshotProvider

    // ── core:data:exercise repositories + conflict resolver ──
    val exerciseRepository: ExerciseRepository
    val sessionRepository: SessionRepository
    val setRepository: SetRepository
    val tagRepository: TagRepository
    val personalRecordRepository: PersonalRecordRepository
    val performedExerciseRepository: PerformedExerciseRepository
    val trainingExerciseRepository: TrainingExerciseRepository
    val trainingRepository: TrainingRepository
}
