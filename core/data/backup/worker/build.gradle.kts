plugins {
    alias(libs.plugins.convention.androidLibrary)
    // Metro for @ContributesBinding(AutoBackupController via BackupScheduler) + @SingleIn
    // BackupNotificationHelperImpl. BackupWorker itself is a plain CoroutineWorker constructed by
    // MetroWorkerFactory, which reads its 6 app-scope deps through BackupWorkerDepsHolder.
    alias(libs.plugins.metro)
}

metro {
    interop {
        includeJavax()
    }
}

dependencies {
    implementation(project(":core:core"))
    implementation(project(":core:data:backup:api"))
    implementation(project(":core:data:backup:scheduling"))
    implementation(project(":core:data:database"))

    implementation(libs.androidx.work.runtime)

    testImplementation(libs.androidx.work.testing)
}
