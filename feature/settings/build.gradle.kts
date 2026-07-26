plugins {
    alias(libs.plugins.convention.composeLibrary)
    // The largest feature graph — it inherits the most app-scoped bindings, including two qualified
    // dispatchers and the application Context.
    alias(libs.plugins.metro)
}

// Metro reads javax.inject qualifiers so the inherited app-scoped bindings keep them — settings resolves
// @DefaultDispatcher AND @IODispatcher (both CoroutineDispatcher): (type + qualifier) is the Metro
// binding key, so the two resolve distinctly, never merge.
metro {
    interop {
        includeJavax()
    }
}

dependencies {
    implementation(project(":core:core"))
    // BackupInteractorImpl injects TempFileProvider (java.io.File-typed), which is declared only in the
    // Android-only core:core-android — the KMP core:core has no equivalent.
    implementation(project(":core:core-android"))

    implementation(project(":core:data:dataStore"))
    implementation(project(":core:ui:kit"))
    implementation(project(":core:ui:mvi"))
    implementation(project(":core:ui:navigation"))
    implementation(project(":core:data:exercise"))
    implementation(project(":core:data:backup:api"))
    implementation(project(":core:data:database"))
    implementation(project(":feature:app-dialogs:api"))

    testImplementation(kotlin("test"))
    testImplementation(libs.androidx.paging.testing)

    androidTestImplementation(libs.bundles.android.test)
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(project(":core:ui:test-utils"))
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}
