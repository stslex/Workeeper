plugins {
    alias(libs.plugins.convention.composeLibrary)
    // PLAIN Store, single @DefaultDispatcher. archive is the template every other feature graph
    // follows: a @GraphExtension contributed to AppScope, so it inherits all app-scoped bindings.
    alias(libs.plugins.metro)
}

// Metro reads javax.inject qualifiers (via includeJavax) so the inherited app-scoped dispatcher
// bindings keep their qualifiers — @DefaultDispatcher / @IODispatcher are core:core annotations
// meta-annotated @javax.inject.Qualifier. Without this, two same-typed CoroutineDispatcher bindings
// would silently merge; with it, (type + qualifier) stays the Metro binding key.
metro {
    interop {
        includeJavax()
    }
}

dependencies {
    implementation(project(":core:core"))

    implementation(project(":core:ui:kit"))
    implementation(project(":core:ui:mvi"))
    implementation(project(":core:ui:navigation"))
    implementation(project(":core:data:exercise"))

    testImplementation(kotlin("test"))
    testImplementation(libs.androidx.paging.testing)

    androidTestImplementation(libs.bundles.android.test)
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(project(":core:ui:test-utils"))
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}
