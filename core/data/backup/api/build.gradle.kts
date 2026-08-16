plugins {
    alias(libs.plugins.convention.kmpLibrary)
}

// BackupStorage and RecoveryDiagnosticsExporter stay in androidMain and must not be "finished" into
// commonMain: their PUBLIC API is platform-typed (java.io.File, android.net.Uri) and nine modules
// depend on this one, so retyping them is a nine-module API break with no observably-correct target
// shape until a real iOS backup implementation exists. Rationale, including why okio.Path fits File
// but nothing fits a SAF Uri: documentation/feature-specs/kmp-phase-6-data-layer.md -> §4.
kotlin {
    sourceSets {
        commonMain.dependencies {
            // Flow / StateFlow on the repository and controller contracts.
            implementation(libs.coroutines.core)
        }
    }
}
