plugins {
    alias(libs.plugins.convention.composeLibrary)
    // KMP C.1: feature/settings flipped from Hilt to Metro DI (the hardest feature — 18
    // app-scoped bound instances incl. two qualified dispatchers + @ApplicationContext).
    alias(libs.plugins.metro)
}

// Metro reads javax.inject qualifiers so the bridged app-scoped @Singletons keep their
// qualifiers — settings bridges @DefaultDispatcher AND @IODispatcher (both CoroutineDispatcher):
// (type + qualifier) is the Metro binding key, so the two resolve distinctly, never merge.
metro {
    interop {
        includeJavax()
    }
}

dependencies {
    implementation(project(":core:core"))
    implementation(project(":core:di"))
    // TempFileProvider (java.io.File-typed) lives in the Android half of core:core.
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
