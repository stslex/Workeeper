plugins {
    alias(libs.plugins.convention.androidLibrary)
    // App-Scope Collapse Step 3 (SB1): Metro plugin so BackupPreferencesRepositoryImpl can be contributed
    // to the app-scope AppGraph via @ContributesBinding(AppScope). Metro coexists with the module's Hilt
    // @Module (which still binds RestoreStateRepository).
    alias(libs.plugins.metro)
}

metro {
    interop {
        includeJavax()
    }
}

dependencies {
    implementation(project(":core:core"))
    // App-Scope Collapse Step 3: the AppScope DI token lives in the Android-only core:core-android.
    implementation(project(":core:core-android"))
    implementation(project(":core:data:backup:api"))

    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.datastore.core)
}
