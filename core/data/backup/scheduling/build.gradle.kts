plugins {
    alias(libs.plugins.convention.androidLibrary)
    // Metro owns both repositories in this module — BackupPreferencesRepositoryImpl and
    // RestoreStateRepositoryImpl are @ContributesBinding(AppScope) @SingleIn(AppScope), aggregated
    // into the app-scope AppGraph. No other DI processor runs here.
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

    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.datastore.core)
}
