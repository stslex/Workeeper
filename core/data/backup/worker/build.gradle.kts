plugins {
    alias(libs.plugins.convention.androidLibrary)
    // App-Scope Collapse Step 3: Metro for @ContributesBinding(AutoBackupController via BackupScheduler)
    // + @SingleIn BackupNotificationHelper. Coexists with the module's Hilt BackupWorker (@HiltWorker stays).
    alias(libs.plugins.metro)
}

metro {
    interop {
        includeJavax()
    }
}

dependencies {
    implementation(project(":core:core"))
    implementation(project(":core:core-android"))
    implementation(project(":core:data:backup:api"))
    implementation(project(":core:data:backup:scheduling"))
    implementation(project(":core:data:database"))
    // P-WORKER: AppGraphContract seam so MetroWorkerFactory reads its 6 deps via appGraphContract()
    // (Hilt-free). Acyclic now that BackupNotificationHelper's contract moved to :core:data:backup:api.
    implementation(project(":core:di"))

    implementation(libs.androidx.work.runtime)
    implementation(libs.androidx.hilt.work)
    ksp(libs.androidx.hilt.compiler)

    testImplementation(libs.androidx.work.testing)
}
