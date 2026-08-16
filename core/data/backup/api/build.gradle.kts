plugins {
    alias(libs.plugins.convention.kmpLibrary)
}

// Layer 2 of the KMP cascade, and the first link of the forced conversion order
// (backup:api -> database -> exercise): core:data:database depends on this module, so it had to
// move first.
//
// 22 of the 24 files are platform-neutral and live in commonMain — the models, the error/result
// taxonomies, BackupPreferences/BackupSchedule and the restore contracts, which is the surface
// phase 7's settings UI needs to read from shared code.
//
// TWO files stay in androidMain because their PUBLIC API is platform-typed, not because their
// bodies are: BackupStorage takes and returns java.io.File, and RecoveryDiagnosticsExporter returns
// android.net.Uri. Nine modules depend on this one, so replacing those types is a nine-module API
// break; doing it now would mean inventing an abstraction for an iOS backup implementation that
// does not exist and whose shape nobody knows. They move when a real iOS implementation makes the
// right shape observable rather than guessed. (okio.Path is the obvious candidate for File — okio
// is already on the graph via core:data:dataStore — but android.net.Uri has no such candidate: it
// is a SAF handle, and iOS shares documents by a different model entirely.)
kotlin {
    sourceSets {
        commonMain.dependencies {
            // Flow / StateFlow on the repository and controller contracts. This is the module's
            // ONLY external dependency: it declared implementation(project(":core:core")) before
            // the split, which zero of its 24 files ever imported.
            implementation(libs.coroutines.core)
        }
    }
}
