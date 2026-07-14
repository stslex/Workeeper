plugins {
    alias(libs.plugins.convention.androidLibrary)
}

// core:di holds the PUBLIC app-scope DI contract (`AppGraphContract`) + the single-point
// `Context.appGraphContract()` accessor. It is the Hilt-free seam the ~15 post-cut LIBRARY
// consumers (RecoveryActivity, the 13 feature bridges, the worker) read the app graph through,
// replacing today's `EntryPointAccessors.fromApplication(..., *HiltEntryPoint)`.
//
// It carries NO Metro plugin: it declares a plain interface (no `@DependencyGraph`, no
// `@ContributesBinding`). The `@DependencyGraph AppGraph : AppGraphContract` aggregation site stays
// in app/app (Metro interface-inheritance proven cross-module; app/app owns the 55 contributions).
//
// Every module below is `api(...)` because its type appears in the PUBLIC `AppGraphContract` surface,
// so consumers of this module must see those types transitively. All are core-tier (no app/feature
// edge — verified acyclic). `RecoveryDiagnosticsExporter` (feature-owned) is deliberately absent —
// its contract extracts to `core:data:backup:api` in P-REC before its accessor is added here.
dependencies {
    api(project(":core:core"))              // PlatformInfoProvider, AppReinitializer, ResourceWrapper (commonMain)
    api(project(":core:core-android"))      // TempFileProvider, dispatcher qualifiers
    api(project(":core:ui:mvi"))            // AnalyticsHolder, LoggerHolder, StoreDispatchers
    api(project(":core:ui:navigation"))     // Navigator
    api(project(":core:data:exercise"))     // the 8 exercise repositories
    api(project(":core:data:backup:api"))   // BackupAuth, BackupStorage, SnapshotExportRunner, RestoreStateRepository, AutoBackupController, BackupPreferencesRepository
    api(project(":core:data:backup:worker")) // BackupNotificationHelper
    api(project(":core:data:database"))     // DatabaseSnapshotProvider, LiveDatabaseLocator
    api(project(":core:data:dataStore"))    // CommonDataStore
}
