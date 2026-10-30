plugins {
    alias(libs.plugins.convention.kmpLibrary)
}

// The cross-feature dialog catalog's contract: its models, the publisher and the observer, in pure
// Kotlin over backup:api's common restore types (Phase 7.17 S1).
kotlin {
    sourceSets {
        commonMain.dependencies {
            // AppDialog's variants carry RestoreOwnerId, UndoRef and BackupErrorCode.
            implementation(project(":core:data:backup:api"))
            // AppDialogObserver's Flow.
            implementation(libs.coroutines.core)
        }
    }
}
