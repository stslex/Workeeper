plugins {
    alias(libs.plugins.convention.androidLibrary)
    // App-Scope Collapse Step 3 (CommonDataStore slice): Metro plugin so CommonDataStoreImpl can be
    // contributed to the app-scope AppGraph via @ContributesBinding(AppScope), and so the Metro-native
    // @AssistedInject/@AssistedFactory trio is processed by Metro. Metro coexists with the module's Hilt-KSP
    // (convention-applied) — after the assisted trio converted to dev.zacsweers.metro.*, no dagger.assisted.*
    // remains for Hilt-KSP to process (coexistence verified against the pinned 1.1.1 toolchain).
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

    implementation(libs.androidx.datastore.core)
    implementation(libs.androidx.datastore.preferences)
}